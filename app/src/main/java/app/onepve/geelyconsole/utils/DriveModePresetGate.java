package app.onepve.geelyconsole.utils;

/**
 * 上车预设驾驶模式自动触发的纯 Java 安全门控状态机 (零 Android 依赖)。
 *
 * 铁律 (用户安全决策 + 原厂源码证据)：
 * 1. 只在 「发动机真正运行 (非 KEY ON/ACC 待机) + P 挡 + 新鲜 0 速」 下才允许自动下发；
 * 2. 每个点火周期 (ignition cycle) 最多触发一次 —— 即使电源状态报文抖动也绝不重复下发；
 * 3. 延迟 (默认 2.5s 避开马达点火压降) 到点后必须复查全部条件 (设置可能被改、
 *    可能已挂挡起步、可能已熄火)，任一不满足则静默取消；
 * 4. 改设置 / 行驶 / 熄火 均取消 pending 触发；
 * 5. 服务冷启动中途 (服务在点火之后才起来) 不得自动补发 —— 只认 「点火边沿」
 *    之后的窗口，冷启动没有边沿则本周期永不自动触发；
 * 6. 下发是 「请求」而非 「成功」: 状态机返回 REQUESTED，真实成功必须等待
 *    仪表反馈 (原厂 DRIVE_MODE_CHANGED 广播)，绝不伪造成功。
 */
public final class DriveModePresetGate {

    /** 触发决策结果 */
    public static final int DECISION_WAIT = 0;        // 条件未满足，继续等待 (保持 pending)
    public static final int DECISION_PROCEED = 1;     // 复查通过，可下发一次请求
    public static final int DECISION_CANCEL = 2;       // 任一条件失守，静默取消
    public static final int DECISION_ALREADY_DONE = 3; // 本周期已触发过，静默

    /** 默认延迟 (ms): 避开马达点火压降与仪表自检 */
    public static final long DEFAULT_DELAY_MS = 2500L;

    private boolean enabled = false;
    private String targetMode = "default"; // default=保持原厂
    private boolean engineRunning = false;
    private int gear = -1;             // -1=未知
    private int speedKmh = -1;         // -1=未知 (不新鲜)
    private long speedUpdatedAt = 0L;
    private long ignitionAt = 0L;      // 本次点火边沿时刻 (0=无)
    private boolean armedThisCycle = false;   // 本点火周期是否已武装 (已见到点火边沿)
    private boolean firedThisCycle = false;   // 本点火周期是否已下发过
    private boolean pendingScheduled = false; // 延迟触发是否已排队
    private long scheduledAt = 0L;
    private long delayMs = DEFAULT_DELAY_MS;

    public interface Clock { long now(); }
    private final Clock clock;

    public DriveModePresetGate(Clock clock) {
        this.clock = clock != null ? clock : new Clock() {
            @Override public long now() { return System.currentTimeMillis(); }
        };
    }

    // ---------------- 配置 (改设置取消 pending) ----------------

    public synchronized void updateConfig(boolean enabled, String targetMode, long now) {
        boolean changed = (this.enabled != enabled)
                || (this.targetMode == null ? targetMode != null : !this.targetMode.equals(targetMode));
        this.enabled = enabled;
        this.targetMode = targetMode != null ? targetMode : "default";
        if (changed && pendingScheduled) {
            // 改设置即静默取消本次 pending (决定位保留: decideAtFireTime 返回 CANCEL)
            cancelPending("config-changed");
        }
    }

    // ---------------- 车辆状态喂入 ----------------

    /** 发动机运行状态喂入; rising edge (false->true) 为点火边沿 */
    public synchronized void feedEngineRunning(boolean running, long now) {
        boolean rising = running && !this.engineRunning;
        this.engineRunning = running;
        if (!running) {
            // 熄火: 周期结束，取消 pending，复位周期锁
            cancelPending("engine-off");
            armedThisCycle = false;
            firedThisCycle = false;
            ignitionAt = 0L;
            return;
        }
        if (rising) {
            // 点火边沿: 武装本周期
            ignitionAt = now;
            armedThisCycle = true;
            pendingScheduled = false; // 新周期
            scheduledAt = 0L;
        }
    }

    public synchronized void feedGear(int gear) {
        this.gear = gear;
    }

    public synchronized void feedSpeed(int speedKmh, long now) {
        this.speedKmh = speedKmh;
        this.speedUpdatedAt = now;
    }

    // ---------------- 判定 ----------------

    /** 调度时刻判定: 是否应该安排一次延迟触发。返回 true 表示应排延迟任务。 */
    public synchronized boolean shouldSchedule(long now) {
        if (!enabled || !armedThisCycle || firedThisCycle || pendingScheduled) {
            return false;
        }
        if ("default".equalsIgnoreCase(targetMode)) {
            return false; // 保持原厂: 永不自动下发
        }
        if (!engineRunning || !isParkedAndStill(now)) {
            return false; // 未运行或未停稳P挡0速: 不武装自动路径 (留给车主手动)
        }
        pendingScheduled = true;
        scheduledAt = now;
        return true;
    }

    /** 延迟到点后的复查判定: 决定是否真的下发一次请求 */
    public synchronized int decideAtFireTime(long now) {
        if (!pendingScheduled) {
            // 从未武装 或 已被取消 (改设置/熄火): 静默
            return DECISION_CANCEL;
        }
        pendingScheduled = false;
        scheduledAt = 0L;
        if (!enabled || firedThisCycle || !armedThisCycle) {
            return DECISION_CANCEL;
        }
        if ("default".equalsIgnoreCase(targetMode)) {
            return DECISION_CANCEL; // 延迟期间被改回保持原厂
        }
        if (!engineRunning) {
            return DECISION_CANCEL; // 延迟期间熄火
        }
        if (!isParkedAndStill(now)) {
            return DECISION_CANCEL; // 延迟期间挂挡/起步
        }
        firedThisCycle = true;
        return DECISION_PROCEED;
    }

    /** 手动测试按钮门槛: 停稳 P 挡 + 发动机运行 (手动路径独立于自动周期锁) */
    public synchronized boolean isManualTestAllowed(long now) {
        return engineRunning && isParkedAndStill(now);
    }

    /** 目标模式 (归一化小写; default=保持原厂) */
    public synchronized String getTargetMode() {
        return targetMode == null ? "default" : targetMode.toLowerCase();
    }

    public synchronized boolean isFiredThisCycle() { return firedThisCycle; }
    public synchronized boolean isPendingScheduled() { return pendingScheduled; }

    public synchronized void setDelayMs(long delayMs) {
        this.delayMs = delayMs > 0 ? delayMs : DEFAULT_DELAY_MS;
    }
    public synchronized long getDelayMs() { return delayMs; }

    // ---------------- 内部 ----------------

    /** P 挡 + 新鲜 0 速 (速度信号 5s 内)。速度不新鲜视为不可判定 → 不放行。 */
    private boolean isParkedAndStill(long now) {
        if (gear != 5) return false;
        if (speedKmh < 0) return false;
        if (speedUpdatedAt <= 0 || now - speedUpdatedAt > 5000L) return false;
        return speedKmh == 0;
    }

    private void cancelPending(String reason) {
        pendingScheduled = false;
        scheduledAt = 0L;
    }
}