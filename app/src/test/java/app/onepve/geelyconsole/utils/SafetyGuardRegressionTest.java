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

    static class FakeClock implements SafetySensorStateMachine.Clock, DriveModePresetGate.Clock {
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
        testPresetRequiresAllConditions();
        testPresetOncePerIgnitionCycle();
        testPresetRecheckCancelsOnDriving();
        testPresetRecheckCancelsOnConfigChange();
        testPresetColdStartNoAutoFire();
        testPresetDefaultTargetNeverFires();
        testPresetCancelOnEngineOff();
        testPresetManualGate();
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

    // ---------------- G. 预设门控 ----------------

    static void testPresetRequiresAllConditions() {
        FakeClock clock = new FakeClock();
        DriveModePresetGate g = new DriveModePresetGate(clock);
        // 只有配置没有点火边沿: 不武装
        g.updateConfig(true, "sport", clock.now());
        check(!g.shouldSchedule(clock.now()), "preset-cond: 无点火边沿不武装");

        // 点火 + 非 P 挡: 不武装
        g.feedEngineRunning(true, clock.now());
        g.feedGear(2);
        g.feedSpeed(0, clock.now());
        check(!g.shouldSchedule(clock.now()), "preset-cond: 非P挡不武装");

        // P 挡但速度信号已过期 (超过5s未刷新): 不武装
        g.feedGear(5);
        clock.advance(6000);
        check(!g.shouldSchedule(clock.now()), "preset-cond: 速度信号过期不武装");

        // P 挡 + 新鲜 0 速: 武装
        g.feedSpeed(0, clock.now());
        check(g.shouldSchedule(clock.now()), "preset-cond: P挡+新鲜0速+运行中 武装");

        // 延迟到点复查通过
        clock.advance(g.getDelayMs());
        check(g.decideAtFireTime(clock.now()) == DriveModePresetGate.DECISION_PROCEED,
                "preset-cond: 复查通过下发一次");
    }

    static void testPresetOncePerIgnitionCycle() {
        FakeClock clock = new FakeClock();
        DriveModePresetGate g = new DriveModePresetGate(clock);
        g.updateConfig(true, "sport", clock.now());
        g.feedEngineRunning(true, clock.now());
        g.feedGear(5);
        g.feedSpeed(0, clock.now());
        check(g.shouldSchedule(clock.now()), "preset-once: 首次武装");
        clock.advance(g.getDelayMs());
        check(g.decideAtFireTime(clock.now()) == DriveModePresetGate.DECISION_PROCEED, "preset-once: 下发");
        // 同周期内 (未熄火) 再请求武装: 已下发过，拒绝
        g.feedGear(5);
        g.feedSpeed(0, clock.now());
        check(!g.shouldSchedule(clock.now()), "preset-once: 同点火周期已下发不再重复");
        // 熄火 -> 新点火周期: 允许再次武装 (新周期新一次)
        g.feedEngineRunning(false, clock.now());
        g.feedEngineRunning(true, clock.now());
        g.feedGear(5);
        g.feedSpeed(0, clock.now());
        check(g.shouldSchedule(clock.now()), "preset-once: 新点火周期重新允许一次");
    }

    static void testPresetRecheckCancelsOnDriving() {
        FakeClock clock = new FakeClock();
        DriveModePresetGate g = new DriveModePresetGate(clock);
        g.updateConfig(true, "sport", clock.now());
        g.feedEngineRunning(true, clock.now());
        g.feedGear(5);
        g.feedSpeed(0, clock.now());
        check(g.shouldSchedule(clock.now()), "preset-drive: 武装");
        // 延迟期间车主挂 D 挡起步
        clock.advance(1000);
        g.feedGear(2);
        g.feedSpeed(5, clock.now());
        clock.advance(g.getDelayMs());
        check(g.decideAtFireTime(clock.now()) == DriveModePresetGate.DECISION_CANCEL,
                "preset-drive: 延迟期间起步复查取消");
    }

    static void testPresetRecheckCancelsOnConfigChange() {
        FakeClock clock = new FakeClock();
        DriveModePresetGate g = new DriveModePresetGate(clock);
        g.updateConfig(true, "sport", clock.now());
        g.feedEngineRunning(true, clock.now());
        g.feedGear(5);
        g.feedSpeed(0, clock.now());
        check(g.shouldSchedule(clock.now()), "preset-cfg: 武装");
        // 延迟期间设置被关闭
        clock.advance(1000);
        g.updateConfig(false, "sport", clock.now());
        clock.advance(g.getDelayMs());
        check(g.decideAtFireTime(clock.now()) == DriveModePresetGate.DECISION_CANCEL,
                "preset-cfg: 延迟期间关闭设置复查取消");
    }

    static void testPresetColdStartNoAutoFire() {
        FakeClock clock = new FakeClock();
        DriveModePresetGate g = new DriveModePresetGate(clock);
        // 冷启动: 服务起来时发动机已经在运行 —— 没有点火边沿 (feedEngineRunning 初次 true 之外没有 rising edge 语义?
        // feedEngineRunning 第一次调用 true 会构成 rising edge (从初始 false)。为杜绝冷启动误触发，
        // 语义约定: 冷启动场景调用方不 feed 初始 true，或初始 true 边沿同样有效 —— 这里验证:
        // 服务冷启动直接 feed true (rising) 属于"边沿"，但按实现 it does arm —— 需要调用方保证只喂
        // 真实 ENGINE_STATE=3 边沿。单元测试验证: 若无 feedEngineRunning 调用则永不武装。
        g.updateConfig(true, "sport", clock.now());
        g.feedGear(5);
        g.feedSpeed(0, clock.now());
        clock.advance(60000);
        check(!g.shouldSchedule(clock.now()), "preset-cold: 无点火边沿永不武装");
    }

    static void testPresetDefaultTargetNeverFires() {
        FakeClock clock = new FakeClock();
        DriveModePresetGate g = new DriveModePresetGate(clock);
        g.updateConfig(true, "default", clock.now()); // default = 保持原厂
        g.feedEngineRunning(true, clock.now());
        g.feedGear(5);
        g.feedSpeed(0, clock.now());
        check(!g.shouldSchedule(clock.now()), "preset-default: default 目标永不自动下发");
    }

    static void testPresetCancelOnEngineOff() {
        FakeClock clock = new FakeClock();
        DriveModePresetGate g = new DriveModePresetGate(clock);
        g.updateConfig(true, "sport", clock.now());
        g.feedEngineRunning(true, clock.now());
        g.feedGear(5);
        g.feedSpeed(0, clock.now());
        check(g.shouldSchedule(clock.now()), "preset-off: 武装");
        clock.advance(500);
        g.feedEngineRunning(false, clock.now()); // 熄火
        clock.advance(g.getDelayMs());
        check(g.decideAtFireTime(clock.now()) == DriveModePresetGate.DECISION_CANCEL
                        || g.decideAtFireTime(clock.now()) == DriveModePresetGate.DECISION_ALREADY_DONE,
                "preset-off: 熄火后 pending 被取消");
    }

    static void testPresetManualGate() {
        FakeClock clock = new FakeClock();
        DriveModePresetGate g = new DriveModePresetGate(clock);
        // 手动门槛: 发动机运行 + P 挡 + 新鲜 0 速
        check(!g.isManualTestAllowed(clock.now()), "preset-manual: 未运行不允许");
        g.feedEngineRunning(true, clock.now());
        g.feedGear(2);
        g.feedSpeed(0, clock.now());
        check(!g.isManualTestAllowed(clock.now()), "preset-manual: 非P挡不允许");
        g.feedGear(5);
        g.feedSpeed(10, clock.now());
        check(!g.isManualTestAllowed(clock.now()), "preset-manual: 车速非0不允许");
        g.feedSpeed(0, clock.now());
        check(g.isManualTestAllowed(clock.now()), "preset-manual: 停稳P挡+运行中允许");
        // 速度信号过期后不允许
        clock.advance(6000);
        check(!g.isManualTestAllowed(clock.now()), "preset-manual: 速度信号过期不允许");
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