package app.onepve.geelyconsole.utils;

/**
 * 两项测试语音传感器 (EPB 电子手刹 / 方向盘转角) 纯 Java 回归测试 (零 Android 依赖，直跑 JVM)。
 *
 * 覆盖核心安全铁律：
 *  A. 未知枚举静默: EPB 只有 0/1 已知，其他一律 unknown 绝不告警
 *  B. 信号新鲜度: 过期一律静默
 *  C. 一次触发/去抖: EPB 释放持续 1200ms 才确认，每点火行程一次；拉起=正常完全静默
 *  D. 方向盘角度: 0/超量程 unknown 静默；|角度|>60 才提醒一次
 *  E. 行程复位: 熄火下电后一次触发锁全部恢复
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
        testEpbUnknownEnumSilent();
        testEpbReleasedAlarmFreshOnly();
        testStaleSignalSilent();
        testParkSteerSnapshotWindow();
        testSteerAngleZeroAndOutOfRangeSilent();
        testSteerAngleThresholdOncePerTrip();
        testCustomSteerThreshold();
        testTripResetRestoresOnceLocks();
        testExtractLow16();
        testDecodeSignedSteerAngle();

        System.out.println();
        System.out.println("==== RESULT: pass=" + passes + " fail=" + failures + " ====");
        if (failures > 0) System.exit(1);
    }

    // ---------------- A. 未知枚举静默 ----------------

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

    // ---------------- B. 信号新鲜度 ----------------

    static void testStaleSignalSilent() {
        FakeClock clock = new FakeClock();
        SafetySensorStateMachine m = new SafetySensorStateMachine(clock);
        m.feedSteerAngleDegrees(90, clock.now());
        clock.advance(SafetySensorStateMachine.SIGNAL_FRESHNESS_MS + 1);
        check(m.checkSteerAngleNotCentered(clock.now()) == SafetySensorStateMachine.RESULT_SILENT,
                "stale: 过期转角信号静默");
        m.feedEpbState(SafetySensorStateMachine.EPB_RELEASED, clock.now());
        clock.advance(SafetySensorStateMachine.SIGNAL_FRESHNESS_MS + 1);
        check(m.checkEpbNotEngagedOnPDoorOpen(clock.now()) == SafetySensorStateMachine.RESULT_SILENT,
                "stale: 过期EPB信号静默");
    }

    // ---------------- C. P挡方向盘位置快照 ----------------

    static void testParkSteerSnapshotWindow() {
        FakeClock clock = new FakeClock();
        SafetySensorStateMachine m = new SafetySensorStateMachine(clock);
        m.feedSteerAngleDegrees(90, clock.now());
        // 方向盘停在偏角后 6 秒再挂 P：实时窗口过期，但挂P位置快照必须可用。
        clock.advance(SafetySensorStateMachine.SIGNAL_FRESHNESS_MS + 1000);
        check(m.checkSteerAngleNotCentered(clock.now()) == SafetySensorStateMachine.RESULT_SILENT,
                "steer-live-stale: 6秒前转角不作为实时信号提醒");
        check(m.checkSteerAngleNotCentered(clock.now(), SafetySensorStateMachine.STEER_ANGLE_THRESHOLD_DEG,
                        SafetySensorStateMachine.PARK_STEER_SNAPSHOT_FRESHNESS_MS)
                        == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED,
                "steer-park-snapshot: 6秒前偏角挂P仍必须提醒");

        m.resetTrip(clock.now());
        m.feedSteerAngleDegrees(90, clock.now());
        clock.advance(SafetySensorStateMachine.PARK_STEER_SNAPSHOT_FRESHNESS_MS + 1);
        check(m.checkSteerAngleNotCentered(clock.now(), SafetySensorStateMachine.STEER_ANGLE_THRESHOLD_DEG,
                        SafetySensorStateMachine.PARK_STEER_SNAPSHOT_FRESHNESS_MS)
                        == SafetySensorStateMachine.RESULT_SILENT,
                "steer-park-expired: 超30秒旧快照必须静默");
    }

    // ---------------- D. 方向盘角度 ----------------

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

    static void testCustomSteerThreshold() {
        FakeClock clock = new FakeClock();
        SafetySensorStateMachine m = new SafetySensorStateMachine(clock);
        m.feedSteerAngleDegrees(45, clock.now());
        check(m.checkSteerAngleNotCentered(clock.now(), 45) == SafetySensorStateMachine.RESULT_SILENT,
                "steer-custom: 恰好用户设定 45° 不提醒 (需严格超过)");
        check(m.checkSteerAngleNotCentered(clock.now(), 44) == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED,
                "steer-custom: 45° 超过用户设定 44° 必须提醒");

        m.resetTrip(clock.now());
        m.feedSteerAngleDegrees(-46, clock.now());
        check(m.checkSteerAngleNotCentered(clock.now(), 45) == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED,
                "steer-custom: 左打 -46° 也按用户设定 45° 提醒");
        check(SafetySensorStateMachine.clampSteerAngleThreshold(1) == SafetySensorStateMachine.STEER_ANGLE_THRESHOLD_MIN_DEG,
                "steer-custom: 低于下限自动收敛");
        check(SafetySensorStateMachine.clampSteerAngleThreshold(999) == SafetySensorStateMachine.STEER_ANGLE_THRESHOLD_MAX_DEG,
                "steer-custom: 高于上限自动收敛");
    }

    // ---------------- D. 行程复位 ----------------

    static void testTripResetRestoresOnceLocks() {
        FakeClock clock = new FakeClock();
        SafetySensorStateMachine m = new SafetySensorStateMachine(clock);
        m.feedSteerAngleDegrees(90, clock.now());
        check(m.checkSteerAngleNotCentered(clock.now()) == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED,
                "reset: trip内首次");
        m.resetTrip(clock.now());
        m.feedSteerAngleDegrees(90, clock.now());
        check(m.checkSteerAngleNotCentered(clock.now()) == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED,
                "reset: 复位后新行程再次允许一次");
    }

    // ---------------- 数值提取 ----------------

    static void testExtractLow16() {
        check(SafetySensorStateMachine.extractLow16(0x00300000) == 0,
                "extract: 基址打包 0x00300000 -> 0 (低16位为有效数值)");
        check(SafetySensorStateMachine.extractLow16(0x00201202) == 0x1202,
                "extract: 安全带 0x00201202 低16位非0 (未系带告警候选)");
        check(SafetySensorStateMachine.extractLow16(0x00201201) == 0x1201,
                "extract: 安全带 0x00201201 低16位非0 (值1=已系带, 消息级语义由调用方判)");
        check(SafetySensorStateMachine.extractLow16(0x0000002c) == 44,
                "extract: 方向盘 0x0000002c -> 44°");
    }

    static void testDecodeSignedSteerAngle() {
        check(SafetySensorStateMachine.decodeSignedSteerAngle(0x0000002c) == 44,
                "steer-decode: 右打 0x002c -> +44°");
        check(SafetySensorStateMachine.decodeSignedSteerAngle(0x0000ffa6) == -90,
                "steer-decode: 左打补码 0xffa6 -> -90°");

        FakeClock clock = new FakeClock();
        SafetySensorStateMachine m = new SafetySensorStateMachine(clock);
        m.feedSteerAngleDegrees(SafetySensorStateMachine.decodeSignedSteerAngle(0x0000ffa6), clock.now());
        check(m.checkSteerAngleNotCentered(clock.now()) == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED,
                "steer-left: 左打 90° 挂P必须触发回正提醒");
    }
}
