package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.content.SharedPreferences;

import app.onepve.geelyconsole.services.VehicleAutomationService;

/**
 * 前引擎盖独立状态机 (HoodStateManager)
 *
 * 与四门、尾门完全解耦，独立维护前引擎盖的物理开合状态。
 *
 * 权威输入源（实车取证 2026-11-11，日志包 911453，beta.11 全量抓包）：
 *   SerialControl_v2_0 / VehicleEmulator_v2_0: 91 02 01 [序 3] [b6] [b7] ...
 *     b6 = 四门状态位图（1=关 0=开），沿用 DoorStateManager 既有解析
 *     b7 = 尾门 + 引擎盖状态位图：
 *       bit0 = 尾门   (1=锁死闭合 0=升起打开)
 *       bit2 = 引擎盖 (1=闭合     0=开启)   ← 本次新增
 *
 * 实证过程（车主操作：开副驾门 → 关副驾门 → 开引擎盖 → 关引擎盖 → 关副驾门）：
 *   12:59:06.018  b6=55 b7=f5  副驾关闭
 *   12:59:07.918  b6=51 b7=f5  副驾打开
 *   12:59:17.029  b6=51 b7=f1  ← bit2 由 1 变 0，引擎盖打开
 *   12:59:32.528  b6=51 b7=f5  ← bit2 由 0 变 1，引擎盖关闭（历时 15.5 秒）
 *   12:59:42.018  b6=55 b7=f5  副驾关闭
 *   同一时段 b6 只有副驾位在动，bit0 全程恒为 1（尾门没动），
 *   因此 b7 除 bit0 外的变化只能来自引擎盖——归属唯一，无歧义。
 *
 * 铁律：
 *   1. 本类只做状态判定与回调，不碰车门/尾门任何逻辑，互不影响。
 *   2. 800ms 硬件机械防抖（与车门一致），滤除微动开关反弹。
 *   3. 首帧只建立基线不播报，避免上车瞬间误触发。
 */
public class HoodStateManager {
    private static final String TAG = "HoodStateManager";

    /** 0=闭合, 1=开启 */
    private static final int HOOD_CLOSED = 0;
    private static final int HOOD_OPEN = 1;

    public interface HoodStateListener {
        void onHoodStateChanged(int state);
    }

    // 800ms 硬件机械防抖，与 DoorStateManager 保持一致
    private static final long HOOD_PHYSICAL_DEBOUNCE_MS = 800;

    private static volatile int currentHood = HOOD_CLOSED;
    private static volatile boolean baselineEstablished = false;
    private static volatile long lastChangeAtMs = 0L;
    private static volatile HoodStateListener listener;
    /** 语音播报依赖（由 VehicleAutomationService 注入，与车门/尾门同一套） */
    private static volatile VehicleVoicePlayer voicePlayer;
    private static volatile boolean voiceMasterSwitch = false;
    /** 读语音开关偏好用；与 DoorStateManager 共用同一个 toolbox_settings */
    private static volatile SharedPreferences prefs;
    private static volatile long frameCount = 0L;
    private static volatile long lastStateRawAtMs = 0L;

    public static void setListener(HoodStateListener l) {
        listener = l;
    }

    /**
     * 由 VehicleAutomationService 在初始化时注入，与车门/尾门共用同一个播放器实例。
     * Context 只用来取 toolbox_settings 偏好，不持有 Activity 引用。
     */
    public static void init(Context ctx, VehicleVoicePlayer p) {
        voicePlayer = p;
        if (ctx != null) {
            prefs = ctx.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
        }
    }

    /** 当前引擎盖状态：0=闭合 1=开启 */
    public static int getState() {
        return currentHood;
    }

    /** 累计收到 91 02 01 帧数（用于判断通道是否还活着） */
    public static long getFrameCount() {
        return frameCount;
    }

    /**
     * 由 VehicleAutomationService 在解析 91 02 01 时调用。
     *
     * @param b7 该帧的 b7 字节（尾门 + 引擎盖位图）
     * @param voiceMaster 语音总开关当前状态（与其他状态机同源）
     */
    public static void updateFromB7(int b7, boolean voiceMaster) {
        voiceMasterSwitch = voiceMaster;
        frameCount++;
        lastStateRawAtMs = System.currentTimeMillis();
        // bit2: 1=闭合, 0=开启
        int hood = ((b7 >> 2) & 0x01) == 1 ? HOOD_CLOSED : HOOD_OPEN;

        if (!baselineEstablished) {
            currentHood = hood;
            baselineEstablished = true;
            AppLogger.diag("引擎盖", "引擎盖基线建立: " + (hood == HOOD_OPEN ? "开启" : "闭合")
                    + " (b7=0x" + String.format("%02x", b7) + ")");
            return;
        }

        if (hood == currentHood) return;

        // 防抖：距上次真实变化不足 800ms 视为机械抖动，忽略
        long now = System.currentTimeMillis();
        if (now - lastChangeAtMs < HOOD_PHYSICAL_DEBOUNCE_MS) {
            return;
        }
        lastChangeAtMs = now;
        currentHood = hood;

        AppLogger.diag("引擎盖", "引擎盖状态变化: " + (hood == HOOD_OPEN ? "开启" : "闭合")
                + " (b7=0x" + String.format("%02x", b7) + ")");
        KeySignalRecorder.record(KeySignalRecorder.CH_HOOD,
                hood == HOOD_OPEN ? "引擎盖=开" : "引擎盖=关");

        // 语音播报（v1.8.32.13）：文案固定为「引擎盖已打开 / 已关闭」，
        // 与 hood_open.mp3 / hood_close.mp3 台词逐字一致。
        // 开关读取顺序与车门/尾门保持一致：先看新键 voice_enable_hood_*，
        // 再兼容旧键 enable_hood_*，默认开启。
        if (voiceMasterSwitch && voicePlayer != null) {
            try {
                boolean enabled;
                if (hood == HOOD_OPEN) {
                    enabled = PrefUtils.getBoolean(prefs, "voice_enable_hood_open", true);
                } else {
                    enabled = PrefUtils.getBoolean(prefs, "voice_enable_hood_close", true);
                }
                if (enabled) {
                    voicePlayer.play(hood == HOOD_OPEN ? "hood_open.mp3" : "hood_close.mp3",
                            hood == HOOD_OPEN ? "引擎盖已打开" : "引擎盖已关闭");
                }
            } catch (Throwable ignored) {
            }
        }

        HoodStateListener l = listener;
        if (l != null) {
            try {
                l.onHoodStateChanged(hood);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 采集健康快照（写进 summary.json，一眼看出通道通不通） */
    public static String getHealthSnapshot() {
        long agoMs = lastStateRawAtMs == 0 ? -1 : (System.currentTimeMillis() - lastStateRawAtMs);
        return "引擎盖通道=91 02 01 b7.bit2 累计" + frameCount + "帧"
                + " 当前=" + (currentHood == HOOD_OPEN ? "开启" : "闭合")
                + " 最后距今=" + (agoMs < 0 ? "无" : (agoMs / 1000 + "秒"));
    }
}
