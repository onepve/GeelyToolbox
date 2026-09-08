package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.content.SharedPreferences;

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
    private VehicleVoicePlayer voicePlayer;
    private DriveModeListener listener;

    private int lastDriveMode = -1;
    private int isDriveModeVoiceArmed = 0; // 0=智能模式静默态, 1=车主激活态

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

    public synchronized void updateDriveMode(int mode, boolean voiceMasterSwitch, SharedPreferences prefs) {
        if (mode <= 0) return;

        if (lastDriveMode == -1) {
            lastDriveMode = mode;
            AppLogger.i("驾驶模式", "基准初始化: 当前模式=" + getModeName(mode));
            if (mode != MODE_SMART) {
                isDriveModeVoiceArmed = 1;
            }
            if (listener != null) {
                listener.onDriveModeChanged(lastDriveMode);
            }
            return;
        }

        if (mode == lastDriveMode) return;

        AppLogger.i("驾驶模式", "模式切换: " + getModeName(lastDriveMode) + " -> " + getModeName(mode) + ", armed=" + isDriveModeVoiceArmed);

        // 1. 从默认智能模式切出 -> 激活状态机
        if (lastDriveMode == MODE_SMART && mode != MODE_SMART) {
            isDriveModeVoiceArmed = 1;
        }

        // 2. 播报判定
        if (voiceMasterSwitch && isDriveModeVoiceArmed == 1) {
            boolean enableComfort = prefs.getBoolean("enable_mode_comfort", true);
            boolean enableSport = prefs.getBoolean("enable_mode_sport", true);
            boolean enableEco = prefs.getBoolean("enable_mode_eco", true);
            boolean enableSmart = prefs.getBoolean("enable_mode_smart", true);

            switch (mode) {
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
                    if (enableSmart && voicePlayer != null) {
                        voicePlayer.play("mode_smart.mp3", "智能模式");
                    }
                    isDriveModeVoiceArmed = 0; // 归零！进入静默态
                    break;
            }
        }

        lastDriveMode = mode;
        if (listener != null) {
            listener.onDriveModeChanged(lastDriveMode);
        }
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
