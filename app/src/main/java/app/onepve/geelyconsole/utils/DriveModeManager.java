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
    private int isDriveModeVoiceArmed = 0; // 0=静默休眠态, 1=车主激活态
    private Runnable pendingModeTask = null;
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
        boolean changed = (lastDriveMode != -1 || isDriveModeVoiceArmed != 0);
        lastDriveMode = -1;
        isDriveModeVoiceArmed = 0;
        if (changed) {
            AppLogger.i("驾驶模式", "熄火休眠复位: 重置归位基准 (lastMode=-1, armed=0)");
        }
    }

    public synchronized void updateDriveMode(int mode, final boolean voiceMasterSwitch, final SharedPreferences prefs) {
        if (mode <= 0) return;

        // 1. 开机首包基准静默建立：绝不盲目发声！
        if (lastDriveMode == -1) {
            lastDriveMode = mode;
            isDriveModeVoiceArmed = 0;
            AppLogger.i("驾驶模式", "基准初始化: 当前模式=" + getModeName(mode) + ", armed=0 (静默休眠)");
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

        // 取消上一次正在防抖中的模式任务 (滤除旋钮快速连切的瞬态，如快速划过经济直接切入舒适)
        if (pendingModeTask != null) {
            mainHandler.removeCallbacks(pendingModeTask);
            pendingModeTask = null;
        }

        final int targetMode = mode;
        pendingModeTask = new Runnable() {
            @Override
            public void run() {
                synchronized (DriveModeManager.this) {
                    pendingModeTask = null;
                    if (targetMode == lastDriveMode) return;

                    AppLogger.i("驾驶模式", "模式确认切换: " + getModeName(lastDriveMode) + " -> " + getModeName(targetMode) + ", armed=" + isDriveModeVoiceArmed);

                    // 1. 从默认智能模式切出 -> 激活状态机
                    if (lastDriveMode == MODE_SMART && targetMode != MODE_SMART) {
                        isDriveModeVoiceArmed = 1;
                    }

                    // 2. 播报判定 (默认全开，支持双别名兼容)
                    if (voiceMasterSwitch) {
                        boolean enableComfort = prefs.contains("voice_enable_mode_comfort") ? prefs.getBoolean("voice_enable_mode_comfort", true) : prefs.getBoolean("enable_mode_comfort", true);
                        boolean enableSport = prefs.contains("voice_enable_mode_sport") ? prefs.getBoolean("voice_enable_mode_sport", true) : prefs.getBoolean("enable_mode_sport", true);
                        boolean enableEco = prefs.contains("voice_enable_mode_eco") ? prefs.getBoolean("voice_enable_mode_eco", true) : prefs.getBoolean("enable_mode_eco", true);
                        boolean enableSmart = prefs.contains("voice_enable_mode_smart") ? prefs.getBoolean("voice_enable_mode_smart", true) : prefs.getBoolean("enable_mode_smart", true);

                        switch (targetMode) {
                            case MODE_COMFORT:
                                if (enableComfort && voicePlayer != null) {
                                    voicePlayer.play("mode_comfort.mp3", "舒适模式");
                                }
                                break;
                            case MODE_SPORT:
                                if (enableSport && voicePlayer != null) {
                                    voicePlayer.play("mode_sport.mp3", "运动模式");
                                }
                                break;
                            case MODE_ECO:
                                if (enableEco && voicePlayer != null) {
                                    voicePlayer.play("mode_eco.mp3", "经济模式");
                                }
                                break;
                            case MODE_SMART:
                                if (isDriveModeVoiceArmed == 1) {
                                    if (enableSmart && voicePlayer != null) {
                                        voicePlayer.play("mode_smart.mp3", "智能模式");
                                    }
                                }
                                isDriveModeVoiceArmed = 0; // 归零！进入静默态
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
