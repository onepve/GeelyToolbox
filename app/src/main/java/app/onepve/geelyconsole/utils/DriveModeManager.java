package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

/**
 * 驾驶模式独立判定器 (DriveModeManager)
 * 模式枚举: MODE_COMFORT=1, MODE_SPORT=2, MODE_ECO=3, MODE_SMART=4
 * 
 * 权威输入源:
 * 1. AdaptAPI 9 位常量 (100% 绝对权威，零错位):
 *    - 570491138 -> 舒适模式 (MODE_COMFORT)
 *    - 570491139 -> 运动模式 (MODE_SPORT)
 *    - 570491137 -> 经济模式 (MODE_ECO)
 *    - 570491158 -> 智能模式 (MODE_SMART)
 * 2. ComfortModule 上报: DM_FUNC_DRIVE_MODE_SELECT value=X
 * 3. MCU SwitchMode & TargetMode
 */
public class DriveModeManager {
    private static final String TAG = "DriveModeManager";

    public static final int MODE_COMFORT = 1;
    public static final int MODE_SPORT = 2;
    public static final int MODE_ECO = 3;
    public static final int MODE_SMART = 4;

    public interface DriveModeListener {
        void onDriveModeChanged(int mode);
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private VehicleVoicePlayer voicePlayer;
    private DriveModeListener listener;

    private int lastDriveMode = -1; // 初始未定态 (-1)，开机首包静默确立基准
    private boolean isSmartModeArmed = false; // 智能模式闭锁标志：默认锁定(false)，切到其他模式时武装(true)，切回智能并播报一次后立即恢复锁定值(false)，彻底杜绝驻车循环播报
    private Runnable pendingModeTask = null;
    private int pendingTargetMode = -1; // 防抖期目标模式：同目标信号不重置计时器
    private static final long MODE_DEBOUNCE_MS = 160; // 模式切换防抖滤波窗口 (160ms 滤除旋钮极速滑动过渡态)

    public DriveModeManager(Context context, VehicleVoicePlayer voicePlayer) {
        this.context = context.getApplicationContext();
        this.voicePlayer = voicePlayer;
    }

    public void setListener(DriveModeListener listener) {
        this.listener = listener;
    }

    public void setVoicePlayer(VehicleVoicePlayer voicePlayer) {
        this.voicePlayer = voicePlayer;
    }

    public int getDriveMode() { return lastDriveMode == -1 ? MODE_SMART : lastDriveMode; }

    /**
     * 车辆熄火/断电/休眠复位：驾驶模式状态机重置归零，下一次点火绝对静默
     * ⚠️ 幂等静默：仅在状态真正发生变化时才写日志，严禁被 MCU 心跳无限刷屏。
     */
    public synchronized void resetState() {
        if (pendingModeTask != null) {
            mainHandler.removeCallbacks(pendingModeTask);
            pendingModeTask = null;
        }
        boolean changed = (lastDriveMode != -1 || isSmartModeArmed);
        lastDriveMode = -1;
        isSmartModeArmed = false;
        if (changed) {
            AppLogger.i("驾驶模式", "熄火休眠复位: 重置归位基准 (lastMode=-1, smartArmed=false)");
        }
    }

    public synchronized void updateDriveMode(int mode, final boolean voiceMasterSwitch, final SharedPreferences prefs) {
        if (mode <= 0) return;

        // 1. 开机首包基准处理：一律静默确立基准，开机/熄火复位后绝不误播任何语音！
        if (lastDriveMode == -1) {
            lastDriveMode = mode;
            isSmartModeArmed = false;
            AppLogger.i("驾驶模式", "首包基准初始化: 当前模式【" + getModeName(mode) + "】(静默建立基准，开机绝不误播，智能模式闭锁)");
            if (listener != null) {
                listener.onDriveModeChanged(lastDriveMode);
            }
            return;
        }

        if (mode == lastDriveMode) {
            // 同模式信号重复接收，静默放行
            return;
        }

        // 模式切换瞬间，第一毫秒立刻掐灭上一句旧声音
        if (voicePlayer != null) {
            voicePlayer.stopCurrentVoice();
        }

        // 防抖核心铁律：同目标模式信号直接忽略，不重置计时器
        if (mode == pendingTargetMode && pendingModeTask != null) {
            return;
        }
        if (pendingModeTask != null) {
            mainHandler.removeCallbacks(pendingModeTask);
            pendingModeTask = null;
        }
        pendingTargetMode = mode;

        final int targetMode = mode;
        pendingModeTask = new Runnable() {
            @Override
            public void run() {
                synchronized (DriveModeManager.this) {
                    pendingModeTask = null;
                    pendingTargetMode = -1;
                    if (targetMode == lastDriveMode) return;

                    AppLogger.i("驾驶模式", "模式确认切换: " + getModeName(lastDriveMode) + " -> " + getModeName(targetMode) + ", smartArmed=" + isSmartModeArmed);

                    // 播报判定 (默认全开，支持双别名兼容)
                    if (voiceMasterSwitch) {
                        boolean enableComfort = prefs.contains("voice_enable_mode_comfort") ? prefs.getBoolean("voice_enable_mode_comfort", true) : prefs.getBoolean("enable_mode_comfort", true);
                        boolean enableSport = prefs.contains("voice_enable_mode_sport") ? prefs.getBoolean("voice_enable_mode_sport", true) : prefs.getBoolean("enable_mode_sport", true);
                        boolean enableEco = prefs.contains("voice_enable_mode_eco") ? prefs.getBoolean("voice_enable_mode_eco", true) : prefs.getBoolean("enable_mode_eco", true);
                        boolean enableSmart = prefs.contains("voice_enable_mode_smart") ? prefs.getBoolean("voice_enable_mode_smart", true) : prefs.getBoolean("enable_mode_smart", true);

                        switch (targetMode) {
                            case MODE_COMFORT:
                                if (enableComfort && voicePlayer != null) {
                                    voicePlayer.play("mode_comfort.mp3", "舒适");
                                }
                                isSmartModeArmed = true; // 主动旋到舒适：武装智能模式，允许后续切回智能时播报一次
                                break;
                            case MODE_SPORT:
                                if (enableSport && voicePlayer != null) {
                                    voicePlayer.play("mode_sport.mp3", "运动");
                                }
                                isSmartModeArmed = true; // 主动旋到运动：武装智能模式，允许后续切回智能时播报一次
                                break;
                            case MODE_ECO:
                                if (enableEco && voicePlayer != null) {
                                    voicePlayer.play("mode_eco.mp3", "经济");
                                }
                                isSmartModeArmed = true; // 主动旋到经济：武装智能模式，允许后续切回智能时播报一次
                                break;
                            case MODE_SMART:
                                if (isSmartModeArmed) {
                                    if (enableSmart && voicePlayer != null) {
                                        voicePlayer.play("mode_smart.mp3", "智能");
                                    }
                                    AppLogger.i("驾驶模式", "切回智能模式并播报一次，立即闭锁为锁定值(false)，防止驻车循环误报");
                                } else {
                                    AppLogger.i("驾驶模式", "智能模式处于锁定状态 (未从其他模式主动切回)，保持绝对静默");
                                }
                                isSmartModeArmed = false; // 立即重置为锁定值！在再次切到其他模式前绝不重复播报
                                break;
                        }
                    }

                    lastDriveMode = targetMode;
                    if (listener != null) {
                        listener.onDriveModeChanged(lastDriveMode);
                    }
                }
            }
        };

        // 立即投递 160ms 防抖确认，确保旋钮旋转到位后干脆播报，绝不被中间态掐断
        mainHandler.postDelayed(pendingModeTask, MODE_DEBOUNCE_MS);
    }

    private String getModeName(int mode) {
        switch (mode) {
            case MODE_COMFORT: return "舒适模式";
            case MODE_SPORT: return "运动模式";
            case MODE_ECO: return "经济模式";
            case MODE_SMART: return "智能模式";
            default: return "模式(" + mode + ")";
        }
    }
}
