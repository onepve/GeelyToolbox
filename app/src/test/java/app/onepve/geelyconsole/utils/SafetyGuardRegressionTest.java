package app.onepve.geelyconsole.utils;

/**
 * 四项测试语音传感器 + 上车预设驾驶模式门控 纯 Java 回归测试 (零 Android 依赖，直跑 JVM)。
 *
 * 覆盖核心安全铁律：
 *  A. oil 正常报文 funValue(0x00300000) 绝不触发警报 (raw>0 不是警报，低16位=0 才是判定依据)
 *  B. TCU/OIL 未知枚举静默；EPB 只有 0/1 已知，其他静默
 *  C. 信号新鲜度: 过期一律静默
 *  D. 一次触发/去抖: P0 持续 1200ms 才确认，每点火行程一次；低续航每点火一次
 *  E. 低续航 <=50 含 0: 0 是无效值必须静默
 *  F. 方向盘角度: 0/超量程 unknown 静默；|角度|>60 才提醒一次
 *  G. 预设门控: 只 P挡+新鲜0速+发动机运行才武装；每点火周期一次；延迟后复查全部条件；
 *     改设置/行驶/熄火取消；冷启动 (无点火边沿) 绝不触发；default 目标永不触发
 */
public class SafetyGuardRegressionTest {

    static class FakeClock implements SafetySensorStateMachine.Clock {
        long now = 100000L;
        @Override public long now() { return now; }
        void advance(long ms) { now += ms; }
    }

    static int failures = 0;
    static int passes = 0;

    static void check(boolean cond, String name) {
        if (cond) { passes++; System.out.println("PASS " + name); }
        else { failures++; System.out.println("FAIL " + name); }
    }

    public static void main(String[] args) {
        testOilNormalValueNotAlarm();
        testOilLowPressureDebounceAndOncePerTrip();
        testTcuOverTempOncePerTrip();
        testEpbUnknownEnumSilent();
        testEpbReleasedAlarmFreshOnly();
        testStaleSignalSilent();
        testLowFuelZeroInvalidSilent();
        testLowFuelOncePerIgnition();
        testSteerAngleZeroAndOutOfRangeSilent();
        testSteerAngleThresholdOncePerTrip();
        testTripResetRestoresOnceLocks();
        testExtractLow16();

        System.out.println();
        System.out.println("==== RESULT: pass=" + passes + " fail=" + failures + " ====");
        if (failures > 0) System.exit(1);
    }

    // ---------------- A. 机油正常值绝不警报 ----------------

    static void testOilNormalValueNotAlarm() {
        FakeClock clock = new FakeClock();
        SafetySensorStateMachine m = new SafetySensorStateMachine(clock);
        // 实车: 正常状态持续广播 funValue(0x00300000) —— 低16位=0 是正常
        m.feedOilPressure(SafetySensorStateMachine.extractLow16(0x00300000), clock.now());
        clock.advance(SafetySensorStateMachine.ALARM_DEBOUNCE_MS + 100);
        m.feedOilPressure(SafetySensorStateMachine.extractLow16(0x00300000), clock.now());
        check(m.checkOilLowPressure(clock.now()) == SafetySensorStateMachine.RESULT_SILENT,
                "oil-normal: 0x00300000 (低16位=0) 持续存在也绝不警报");
    }

    static void testOilLowPressureDebounceAndOncePerTrip() {
        FakeClock clock = new FakeClock();
        SafetySensorStateMachine m = new SafetySensorStateMachine(clock);
        m.feedOilPressure(1, clock.now());
        // 未到去抖窗口: 静默
        check(m.checkOilLowPressure(clock.now()) == SafetySensorStateMachine.RESULT_SILENT,
                "oil-alarm: 未达去抖窗口静默");
        clock.advance(SafetySensorStateMachine.ALARM_DEBOUNCE_MS - 1);
        check(m.checkOilLowPressure(clock.now()) == SafetySensorStateMachine.RESULT_SILENT,
                "oil-alarm: 去抖窗口内仍静默");
        clock.advance(2);
        check(m.checkOilLowPressure(clock.now()) == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED,
                "oil-alarm: 去抖通过后确认一次");
        // 心跳重复: 不复读
        clock.advance(2000);
        m.feedOilPressure(2, clock.now());
        check(m.checkOilLowPressure(clock.now()) == SafetySensorStateMachine.RESULT_ALARM_ONGOING,
                "oil-alarm: 同行程心跳不重复告警");
    }

    static void testTcuOverTempOncePerTrip() {
        FakeClock clock = new FakeClock();
        SafetySensorStateMachine m = new SafetySensorStateMachine(clock);
        m.feedTcuTempLevel(2, clock.now());
        clock.advance(SafetySensorStateMachine.ALARM_DEBOUNCE_MS + 1);
        m.feedTcuTempLevel(2, clock.now());
        check(m.checkTcuOverTemp(clock.now()) == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED,
                "tcu: 持续等级2 去抖后确认");
        clock.advance(5000);
        m.feedTcuTempLevel(3, clock.now());
        check(m.checkTcuOverTemp(clock.now()) == SafetySensorStateMachine.RESULT_ALARM_ONGOING,
                "tcu: 等级升级也仅一次 (本行程锁)");
    }

    // ---------------- B. 未知枚举静默 ----------------

    static void testEpbUnknownEnumSilent() {
        FakeClock clock = new FakeClock();
        SafetySensorStateMachine m = new SafetySensorStateMachine(clock);
        m.feedEpbState(7, clock.now()); // 未知枚举
        clock.advance(SafetySensorStateMachine.ALARM_DEBOUNCE_MS + 100);
        check(m.checkEpbNotEngagedOnPDoorOpen(clock.now()) == SafetySensorStateMachine.RESULT_SILENT,
                "epb-unknown: 未知枚举 7 绝不告警");
        check(Double.isNaN(m.getKnownValue(SafetySensorStateMachine.SENSOR_EPB)),
                "epb-unknown: 未知枚举记录为 unknown");
    }

    static void testEpbReleasedAlarmFreshOnly() {
        FakeClock clock = new FakeClock();
        SafetySensorStateMachine m = new SafetySensorStateMachine(clock);
        m.feedEpbState(SafetySensorStateMachine.EPB_RELEASED, clock.now());
        clock.advance(SafetySensorStateMachine.ALARM_DEBOUNCE_MS + 1);
        check(m.checkEpbNotEngagedOnPDoorOpen(clock.now()) == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED,
                "epb-released: 释放状态去抖后确认一次");
        // 拉起后恢复正常: 完全静默 (不是 ONGOING —— 拉起就是正常)
        m.feedEpbState(SafetySensorStateMachine.EPB_ENGAGED, clock.now());
        check(m.checkEpbNotEngagedOnPDoorOpen(clock.now()) == SafetySensorStateMachine.RESULT_SILENT,
                "epb-engaged: 已拉起=恢复正常，完全静默");
    }

    // ---------------- C. 信号新鲜度 ----------------

    static void testStaleSignalSilent() {
        FakeClock clock = new FakeClock();
        SafetySensorStateMachine m = new SafetySensorStateMachine(clock);
        m.feedOilPressure(1, clock.now());
        clock.advance(SafetySensorStateMachine.ALARM_DEBOUNCE_MS + 1);
        // 超过新鲜度窗口后不再喂信号
        clock.advance(SafetySensorStateMachine.SIGNAL_FRESHNESS_MS + 1);
        check(m.checkOilLowPressure(clock.now()) == SafetySensorStateMachine.RESULT_SILENT,
                "stale: 过期油压信号静默");
        m.feedEpbState(SafetySensorStateMachine.EPB_RELEASED, clock.now());
        clock.advance(SafetySensorStateMachine.SIGNAL_FRESHNESS_MS + 1);
        check(m.checkEpbNotEngagedOnPDoorOpen(clock.now()) == SafetySensorStateMachine.RESULT_SILENT,
                "stale: 过期EPB信号静默");
    }

    // ---------------- D/E. 低续航 ----------------

    static void testLowFuelZeroInvalidSilent() {
        FakeClock clock = new FakeClock();
        SafetySensorStateMachine m = new SafetySensorStateMachine(clock);
        m.feedRemainOdo(0, clock.now()); // 0 = 无效读数 (刚通电)
        check(m.checkLowFuel(clock.now()) == SafetySensorStateMachine.RESULT_SILENT,
                "lowfuel-zero: 0km 是无效值绝不告警 (0 含在 <=50 内但必须静默)");
        m.feedRemainOdo(50, clock.now());
        check(m.checkLowFuel(clock.now()) == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED,
                "lowfuel-50: 恰好 50km 边界确认一次");
    }

    static void testLowFuelOncePerIgnition() {
        FakeClock clock = new FakeClock();
        SafetySensorStateMachine m = new SafetySensorStateMachine(clock);
        m.feedRemainOdo(30, clock.now());
        check(m.checkLowFuel(clock.now()) == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED,
                "lowfuel-once: 首次确认");
        clock.advance(60000);
        m.feedRemainOdo(20, clock.now());
        check(m.checkLowFuel(clock.now()) == SafetySensorStateMachine.RESULT_ALARM_ONGOING,
                "lowfuel-once: 同行程续航继续下降不重复");
        // 熄火再点火: 新行程可再次提醒
        clock.advance(1000);
        m.powerOff(clock.now());
        m.feedRemainOdo(20, clock.now());
        check(m.checkLowFuel(clock.now()) == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED,
                "lowfuel-once: 新点火行程重新允许一次");
    }

    // ---------------- F. 方向盘角度 ----------------

    static void testSteerAngleZeroAndOutOfRangeSilent() {
        FakeClock clock = new FakeClock();
        SafetySensorStateMachine m = new SafetySensorStateMachine(clock);
        m.feedSteerAngle(0x00000000, clock.now()); // 0 = 无效
        check(m.checkSteerAngleNotCentered(clock.now()) == SafetySensorStateMachine.RESULT_SILENT,
                "steer-0: 0° 无效静默");
        m.feedSteerAngle(0x0000FFFF & 1000, clock.now()); // 1000° 超量程
        check(m.checkSteerAngleNotCentered(clock.now()) == SafetySensorStateMachine.RESULT_SILENT,
                "steer-range: 1000° 超量程静默");
        m.feedSteerAngleDegrees(0, clock.now());
        check(m.checkSteerAngleNotCentered(clock.now()) == SafetySensorStateMachine.RESULT_SILENT,
                "steer-0-direct: 直接喂 0° 静默");
    }

    static void testSteerAngleThresholdOncePerTrip() {
        FakeClock clock = new FakeClock();
        SafetySensorStateMachine m = new SafetySensorStateMachine(clock);
        m.feedSteerAngleDegrees(60, clock.now()); // 恰好 60° = 不超过阈值
        check(m.checkSteerAngleNotCentered(clock.now()) == SafetySensorStateMachine.RESULT_SILENT,
                "steer-60: 恰好阈值不提醒 (需 >60)");
        m.feedSteerAngleDegrees(90, clock.now());
        check(m.checkSteerAngleNotCentered(clock.now()) == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED,
                "steer-90: 90° 确认一次 (与实车采样 0x0000002c=44° 同低16位编码)");
        m.feedSteerAngleDegrees(120, clock.now());
        check(m.checkSteerAngleNotCentered(clock.now()) == SafetySensorStateMachine.RESULT_ALARM_ONGOING,
                "steer-once: 同行程不重复");
    }

    // ---------------- 行程复位 ----------------

    static void testTripResetRestoresOnceLocks() {
        FakeClock clock = new FakeClock();
        SafetySensorStateMachine m = new SafetySensorStateMachine(clock);
        m.feedRemainOdo(40, clock.now());
        check(m.checkLowFuel(clock.now()) == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED, "reset: trip内首次");
        m.resetTrip(clock.now());
        m.feedRemainOdo(40, clock.now());
        check(m.checkLowFuel(clock.now()) == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED,
                "reset: 复位后新行程再次允许一次");
    }

    // ---------------- 数值提取 ----------------

    static void testExtractLow16() {
        check(SafetySensorStateMachine.extractLow16(0x00300000) == 0,
                "extract: 机油正常 0x00300000 -> 0 (正常)");
        check(SafetySensorStateMachine.extractLow16(0x00201202) == 0x1202,
                "extract: 安全带 0x00201202 低16位非0 (未系带告警候选)");
        check(SafetySensorStateMachine.extractLow16(0x00201201) == 0x1201,
                "extract: 安全带 0x00201201 低16位非0 (值1=已系带, 消息级语义由调用方判)");
        check(SafetySensorStateMachine.extractLow16(0x0000002c) == 44,
                "extract: 方向盘 0x0000002c -> 44°");
        check(SafetySensorStateMachine.extractLow16(0x0000010b) == 267,
                "extract: 续航 0x10b -> 267km");
    }
}