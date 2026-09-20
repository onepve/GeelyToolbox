package app.onepve.geelyconsole.utils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 两项测试语音传感器的纯 Java安全判定状态机 (零 Android 依赖，可直跑 JVM 回归)。
 *
 * 证据与铁律 (来源: 固件逆向 + 实车日志 + 用户安全决策)：
 * 1. AdaptAPI funValue 采用 「funId基址 | 数值」打包编码：
 *      - 安全带 DRV funId 0x00201200：funValue(0x00201201)=已系带(值1)，funValue(0x00201202)=未系(值2)；
 *      - 方向盘转角采样 funValue(0x0000002c)=44° → 数值在低 16 位。
 *    因此数值提取统一取 funValue & 0xFFFF (低16位)。
 * 2. EPB / 角度的 "未知值必须静默"：任何未在白名单内的枚举值一律判 unknown，
 *    坚决不播报、不误警 (证据不足安全静默)。
 * 3. 信号新鲜度：每个传感器记录最后收到时间戳 (单调时钟注入)，过期 (默认 5s) 一律视为
 *    unknown，绝不以陈旧值报警。
 * 4. 一次触发/去抖：同一告警需要持续时间 (默认 1200ms 去抖窗口) 才确认，且每个点火
 *    行程 (trip) 内同类告警仅播报一次，杜绝心跳复读。
 * 5. 角度比例未实车验证：偏角判定阈值 60° 为测试值，超过正负最大量程(±540°)判 unknown。
 * 6. EPB 语义: 0x00/0=释放, 0x01/1=拉起 (实车采样值)。其余值 unknown。
 * 7. 告警确认后回调播报由调用方执行；本类只做判定，绝不谎称已播放 ——
 *    onAlarmConfirmed 返回后调用方自行记录是否真实送入仲裁器。
 */
public final class SafetySensorStateMachine {

    /** 传感器种类 */
    public static final int SENSOR_STEER_ANGLE = 0;   // 方向盘转角 (P3 关怀)
    public static final int SENSOR_EPB = 1;           // 电子手刹 (P0 高危)

    /** EPB 已知枚举 (实车采样: 0x01=拉起 / 0x00=释放) */
    public static final int EPB_RELEASED = 0;
    public static final int EPB_ENGAGED = 1;

    /** 方向盘未回正测试阈值 (度，测试值未实车标定) */
    public static final int STEER_ANGLE_THRESHOLD_DEG = 60;
    /** 方向盘角度合理量程上限 (度) */
    public static final int STEER_ANGLE_MAX_DEG = 540;
    /** 信号新鲜度窗口 (ms): 超过视为过期 unknown */
    public static final long SIGNAL_FRESHNESS_MS = 5000L;
    /** 告警确认去抖窗口 (ms): 持续非零值超过该时长才确认 */
    public static final long ALARM_DEBOUNCE_MS = 1200L;

    /** 判定结果 */
    public static final int RESULT_SILENT = 0;        // 静默 (正常/未知/过期/未达阈值)
    public static final int RESULT_ALARM_CONFIRMED = 1; // 告警确认 (本次行程首次，应播报)
    public static final int RESULT_ALARM_ONGOING = 2;   // 告警持续中 (已播报过，不重复)

    /** 可注入单调时钟 (测试可控，免疫墙钟回拨) */
    public interface Clock {
        long now();
    }

    /** 一次触发锁: 每个点火行程每类告警仅确认一次 */
    private final boolean[] trippedThisCycle = new boolean[2];
    /** 告警开始时间戳 (0=当前无告警候选) */
    private final long[] alarmCandidateSince = new long[2];
    /** 各传感器最后收到有效 (已知枚举) 信号的时间戳 */
    private final long[] lastSignalAt = new long[2];
    /** 各传感器当前已知值; unknown 用 NaN / -1 表示 */
    private final double[] currentKnownValue = new double[2];

    private final Clock clock;
    private long tripStartAt = 0L;

    public SafetySensorStateMachine(Clock clock) {
        this.clock = clock != null ? clock : new Clock() {
            @Override
            public long now() { return System.currentTimeMillis(); }
        };
        resetTrip(this.clock.now());
    }

    /** 新点火行程开始: 重置一次触发锁与告警候选 */
    public synchronized void resetTrip(long now) {
        tripStartAt = now;
        for (int i = 0; i < 2; i++) {
            trippedThisCycle[i] = false;
            alarmCandidateSince[i] = 0L;
            lastSignalAt[i] = 0L;
            currentKnownValue[i] = Double.NaN;
        }
    }

    /** 熄火下电: 与 resetTrip 等价 (行程结束) */
    public synchronized void powerOff(long now) {
        resetTrip(now);
    }

    // ---------------- 数值提取 (AdaptAPI funValue 编码) ----------------

    /**
     * 从 funValue 原始 32 位值提取有效数值 (低 16 位)。
     * 证据: 安全带 funValue(0x00201201)=值1 / 0x00201202=值2；转角 0x0000002c=44°；
     * 机油正常 0x00300000 低16位=0。数值永远在低 16 位。
     */
    public static int extractLow16(int funValueRaw) {
        return funValueRaw & 0xFFFF;
    }

    // ---------------- 各传感器喂入 ----------------

    /**
     * 方向盘转角 (度)。超出量程或无有效数值返回后内部记 unknown。
     * @param rawFunValue AdaptAPI funValue 原始值 (低16位为角度)
     * @return 本次喂入是否为已知有效值
     */
    public synchronized boolean feedSteerAngle(int rawFunValue, long now) {
        int deg = extractLow16(rawFunValue);
        // 采样为 0x0000002c=44°；0 视为刚通电无效值 (unknown 静默)
        if (deg <= 0 || deg > STEER_ANGLE_MAX_DEG) {
            currentKnownValue[SENSOR_STEER_ANGLE] = Double.NaN;
            return false;
        }
        currentKnownValue[SENSOR_STEER_ANGLE] = deg;
        lastSignalAt[SENSOR_STEER_ANGLE] = now;
        return true;
    }

    /** 方向盘转角直接喂入 (度，可为负)。0 未知静默。 */
    public synchronized void feedSteerAngleDegrees(int degrees, long now) {
        if (Math.abs(degrees) == 0) {
            currentKnownValue[SENSOR_STEER_ANGLE] = Double.NaN;
            return;
        }
        if (Math.abs(degrees) > STEER_ANGLE_MAX_DEG) {
            currentKnownValue[SENSOR_STEER_ANGLE] = Double.NaN;
            return;
        }
        currentKnownValue[SENSOR_STEER_ANGLE] = degrees;
        lastSignalAt[SENSOR_STEER_ANGLE] = now;
    }

    /**
     * EPB 状态。已知枚举: 0=释放, 1=拉起；其余 unknown 静默。
     * 注意 EPB 告警条件与数值大小无关: 释放(0)才是告警候选，拉起(1)=正常。
     */
    public synchronized void feedEpbState(int state, long now) {
        if (state != EPB_RELEASED && state != EPB_ENGAGED) {
            currentKnownValue[SENSOR_EPB] = Double.NaN;
            alarmCandidateSince[SENSOR_EPB] = 0L;
            return;
        }
        currentKnownValue[SENSOR_EPB] = state;
        lastSignalAt[SENSOR_EPB] = now;
        // 释放=告警候选开始计时；拉起=恢复正常清候选
        if (state == EPB_RELEASED) {
            if (alarmCandidateSince[SENSOR_EPB] == 0L && !trippedThisCycle[SENSOR_EPB]) {
                alarmCandidateSince[SENSOR_EPB] = now;
            }
        } else {
            alarmCandidateSince[SENSOR_EPB] = 0L;
        }
    }

    // ---------------- 判定 (证据不足安全静默) ----------------

    /**
     * EPB 未拉起判定: 仅在 P 挡开门场景由调用方触发调用。
     * 信号过期或 unknown → SILENT。释放(0)=告警，拉起(1)=正常。
     */
    public synchronized int checkEpbNotEngagedOnPDoorOpen(long now) {
        if (!isFresh(SENSOR_EPB, now)) {
            return RESULT_SILENT;
        }
        double state = currentKnownValue[SENSOR_EPB];
        if (Double.isNaN(state) || state != EPB_RELEASED) {
            return RESULT_SILENT;
        }
        if (trippedThisCycle[SENSOR_EPB]) {
            return RESULT_ALARM_ONGOING;
        }
        // 去抖: 释放状态持续 ALARM_DEBOUNCE_MS 才确认 (由 feed 计时)
        if (alarmCandidateSince[SENSOR_EPB] == 0L
                || now - alarmCandidateSince[SENSOR_EPB] < ALARM_DEBOUNCE_MS) {
            return RESULT_SILENT;
        }
        trippedThisCycle[SENSOR_EPB] = true;
        alarmCandidateSince[SENSOR_EPB] = 0L;
        return RESULT_ALARM_CONFIRMED;
    }

    /** 方向盘未回正判定: |角度| > 60° 且信号新鲜 */
    public synchronized int checkSteerAngleNotCentered(long now) {
        if (!isFresh(SENSOR_STEER_ANGLE, now)) {
            return RESULT_SILENT;
        }
        double deg = currentKnownValue[SENSOR_STEER_ANGLE];
        if (Double.isNaN(deg) || Math.abs(deg) <= STEER_ANGLE_THRESHOLD_DEG) {
            return RESULT_SILENT;
        }
        // P3 关怀无需持续去抖 (开门瞬间的瞬时快照即可)，但保留一次触发锁
        if (trippedThisCycle[SENSOR_STEER_ANGLE]) {
            return RESULT_ALARM_ONGOING;
        }
        trippedThisCycle[SENSOR_STEER_ANGLE] = true;
        return RESULT_ALARM_CONFIRMED;
    }

    // ---------------- 内部 ----------------

    private boolean isFresh(int sensor, long now) {
        long at = lastSignalAt[sensor];
        return at > 0 && (now - at) <= SIGNAL_FRESHNESS_MS;
    }

    /** 诊断: 当前已知值 (NaN=unknown)，仅测试与状态回显用 */
    public synchronized double getKnownValue(int sensor) {
        return currentKnownValue[sensor];
    }

    public synchronized boolean isTripped(int sensor) {
        return trippedThisCycle[sensor];
    }

    public synchronized long getTripStartAt() {
        return tripStartAt;
    }
}