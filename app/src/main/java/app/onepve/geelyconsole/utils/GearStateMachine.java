package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

/**
 * 车辆换挡独立状态机 (GearStateMachine)
 * 纯粹聚焦于 TCU / MCU 物理挡位信号，内置有人感知状态机（P挡静默，换出激活，换回静默）
 * 
 * 权威输入源:
 * 1. MCULog:GearPosition: x (2=D, 3=N, 4=R, 5=P, 6/7=S) 毫秒直出
 * 2. VehId=Vehicle_Gear value=0x0x 容灾
 */
public class GearStateMachine {
    private static final String TAG = "GearStateMachine";

    public interface GearStateListener {
        void onGearChanged(int gear);
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private VehicleVoicePlayer voicePlayer;
    private GearStateListener listener;

    private int lastGearPos = -1;
    private int isGearVoiceArmed = 0; // 0=P挡静默休眠态, 1=车主激活态

    public static int normalizeGear(int rawGear) {
        switch (rawGear) {
            case 2:
            case 17: // 0x11 ECARX Vehicle_Gear D挡
                return 2;
            case 3:
            case 18: // 0x12 ECARX Vehicle_Gear N挡
                return 3;
            case 4:
            case 19: // 0x13 ECARX Vehicle_Gear R挡
                return 4;
            case 5:
            case 20: // 0x14 ECARX Vehicle_Gear P挡
                return 5;
            case 6:
            case 7:
            case 21: // 0x15 ECARX Vehicle_Gear S/M挡
            case 22: // 0x16 ECARX Vehicle_Gear S/B挡
                return 6;
            default:
                return rawGear;
        }
    }

    public GearStateMachine(Context context, VehicleVoicePlayer voicePlayer) {
        this.context = context.getApplicationContext();
        this.voicePlayer = voicePlayer;
    }

    public void setListener(GearStateListener listener) {
        this.listener = listener;
    }

    public void setVoicePlayer(VehicleVoicePlayer voicePlayer) {
        this.voicePlayer = voicePlayer;
    }

    public int getGear() { return lastGearPos == -1 ? 5 : lastGearPos; }

    /**
     * 车辆熄火/断电/休眠复位：状态机重置归零，下一次点火绝对静默
     */
    public synchronized void resetState() {
        lastGearPos = -1;
        isGearVoiceArmed = 0;
        AppLogger.i("换挡状态", "熄火休眠: 换挡状态机重置归零 (armed=0, lastGear=-1)");
    }

    public synchronized void updateGear(int rawGear, boolean voiceMasterSwitch, SharedPreferences prefs) {
        if (rawGear <= 0) return;
        final int gear = normalizeGear(rawGear);

        if (lastGearPos == -1) {
            lastGearPos = gear;
            isGearVoiceArmed = 0; // 严格铁律：开机基准初始化 100% 保持休眠态 (armed=0)，绝不主动发声
            AppLogger.i("换挡状态", "基准初始化: 当前挡位=" + getGearName(gear) + ", armed=0 (静默休眠)");
            if (listener != null) {
                listener.onGearChanged(lastGearPos);
            }
            return;
        }

        if (gear == lastGearPos) return;

        AppLogger.i("换挡状态", "挡位跃变: " + getGearName(lastGearPos) + " -> " + getGearName(gear) + ", armed=" + isGearVoiceArmed);

        // 1. 从 P 挡换出 -> 激活状态机
        if (lastGearPos == 5 && gear != 5) {
            isGearVoiceArmed = 1;
        }

        // 2. 播报判定 (默认全开启，即使开机处于初始态也按需激活)
        if (voiceMasterSwitch) {
            boolean enableD = prefs.getBoolean("voice_enable_gear_d", true) && prefs.getBoolean("enable_gear_d", true);
            boolean enableR = prefs.getBoolean("voice_enable_gear_r", true) && prefs.getBoolean("enable_gear_r", true);
            boolean enableN = prefs.getBoolean("voice_enable_gear_n", true) && prefs.getBoolean("enable_gear_n", true);
            boolean enableS = prefs.getBoolean("voice_enable_gear_s", true) && prefs.getBoolean("enable_gear_s", true);
            boolean enableP = prefs.getBoolean("voice_enable_gear_p", true) && prefs.getBoolean("enable_gear_p", true);

            if (gear == 2) { // D 挡
                if (enableD && voicePlayer != null) {
                    voicePlayer.play("gear_d.mp3", "已挂入前进挡，系好安全带，祝你一路平安");
                }
            } else if (gear == 4) { // R 挡 (直接播报抗衰减倒车语音)
                if (enableR && voicePlayer != null) {
                    voicePlayer.play("gear_r.mp3", "已挂入倒车挡，请注意观察后方安全");
                }
            } else if (gear == 3) { // N 挡
                if (enableN && voicePlayer != null) {
                    voicePlayer.play("gear_n.mp3", "已挂入空挡");
                }
            } else if (gear == 6 || gear == 7) { // S 挡
                if (enableS && voicePlayer != null) {
                    voicePlayer.play("gear_s.mp3", "已挂入运动挡，动力充沛");
                }
            } else if (gear == 5) { // P 挡
                // 必须在车主主动换出过 P 挡（isGearVoiceArmed == 1）后挂回 P 挡才播报，杜绝开机/熄火点火伪跳变误报
                if (isGearVoiceArmed == 1) {
                    if (enableP && voicePlayer != null) {
                        voicePlayer.play("gear_p.mp3", "已挂入驻车挡");
                    }
                }
                isGearVoiceArmed = 0; // 归零！进入休眠态
            }
        }

        lastGearPos = gear;
        if (listener != null) {
            listener.onGearChanged(lastGearPos);
        }
    }

    private String getGearName(int gear) {
        switch (normalizeGear(gear)) {
            case 2: return "D挡(前进)";
            case 3: return "N挡(空挡)";
            case 4: return "R挡(倒车)";
            case 5: return "P挡(驻车)";
            case 6:
            case 7: return "S挡(运动)";
            default: return "挡位(" + gear + ")";
        }
    }
}
