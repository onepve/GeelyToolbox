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
    private Runnable pendingGearTask = null;
    private static final long GEAR_DEBOUNCE_MS = 160; // 换挡防抖滤波窗口 (160ms 滤除快切瞬态，保证最终挡位干脆秒出)

    public static int normalizeGear(int rawGear) {
        int low = rawGear & 0x0F;
        if (rawGear > 0x0F && low >= 2 && low <= 7) {
            // 吉利 E02 复合高位报文 (如 0x12 舒适模式D挡 -> low=2, 0x15 P挡 -> low=5)
            if (low == 6 || low == 7) return 6;
            return low;
        }
        switch (rawGear) {
            case 2:
            case 17: // 0x11 D挡
            case 18: // 0x12 D挡 (低4位=2, 舒适模式D挡)
                return 2;
            case 3:
            case 19: // 0x13 N挡 (低4位=3)
                return 3;
            case 4:
            case 20: // 0x14 R挡 (低4位=4)
                return 4;
            case 5:
            case 21: // 0x15 P挡 (低4位=5)
                return 5;
            case 6:
            case 7:
            case 22: // 0x16 S挡
                return 6;
            default:
                if (low >= 2 && low <= 7) return (low == 7 ? 6 : low);
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
     * ⚠️ 幂等静默铁律：本方法被 MCU 心跳周期性触发，仅在「状态真正发生变化」时才写一条日志，
     * 严禁熄火后每个心跳都刷同一条 —— 否则守护日志会被单条消息无限刷屏（车主明确要求）。
     */
    public synchronized void resetState() {
        if (pendingGearTask != null) {
            mainHandler.removeCallbacks(pendingGearTask);
            pendingGearTask = null;
        }
        boolean changed = (lastGearPos != -1 || isGearVoiceArmed != 0);
        lastGearPos = -1;
        isGearVoiceArmed = 0;
        if (changed) {
            AppLogger.i("挡位状态", "熄火休眠: 换挡状态机重置归零 (armed=0, lastGear=-1)");
        }
    }

    public synchronized void updateGear(int rawGear, final boolean voiceMasterSwitch, final SharedPreferences prefs) {
        if (rawGear <= 0) return;
        final int gear = normalizeGear(rawGear);

        if (lastGearPos == -1) {
            lastGearPos = gear;
            isGearVoiceArmed = 0; // 严格铁律：开机基准初始化 100% 保持休眠态 (armed=0)，绝不主动发声
            AppLogger.i("挡位状态", "基准初始化: 当前挡位=" + getGearName(gear) + ", armed=0 (静默休眠)");
            if (listener != null) {
                listener.onGearChanged(lastGearPos);
            }
            return;
        }

        if (gear == lastGearPos) return;

        // 取消上一次正在防抖中的换挡任务 (滤除极速连切的中间过渡态，如快速从P划过N进入D)
        if (pendingGearTask != null) {
            mainHandler.removeCallbacks(pendingGearTask);
            pendingGearTask = null;
        }

        final int prevGear = lastGearPos;
        pendingGearTask = new Runnable() {
            @Override
            public void run() {
                synchronized (GearStateMachine.this) {
                    pendingGearTask = null;
                    if (gear == lastGearPos) return;

                    AppLogger.i("挡位状态", "挡位确认跃变: " + getGearName(lastGearPos) + " -> " + getGearName(gear) + ", armed=" + isGearVoiceArmed);

                    // 1. 换出 P 挡进入行车挡 (D挡2, R挡4, S挡6/7 或从P切出)，立即武装状态机
                    if (gear == 2 || gear == 4 || gear == 6 || gear == 7 || (lastGearPos == 5 && gear != 5)) {
                        isGearVoiceArmed = 1;
                    }

                    // 2. 播报判定 (干脆利落短句，强制瞬发打断旧语音)
                    if (voiceMasterSwitch) {
                        boolean enableD = prefs.contains("voice_enable_gear_d") ? prefs.getBoolean("voice_enable_gear_d", true) : prefs.getBoolean("enable_gear_d", true);
                        boolean enableR = prefs.contains("voice_enable_gear_r") ? prefs.getBoolean("voice_enable_gear_r", true) : prefs.getBoolean("enable_gear_r", true);
                        boolean enableN = prefs.contains("voice_enable_gear_n") ? prefs.getBoolean("voice_enable_gear_n", true) : prefs.getBoolean("enable_gear_n", true);
                        boolean enableS = prefs.contains("voice_enable_gear_s") ? prefs.getBoolean("voice_enable_gear_s", true) : prefs.getBoolean("enable_gear_s", true);
                        boolean enableP = prefs.contains("voice_enable_gear_p") ? prefs.getBoolean("voice_enable_gear_p", true) : prefs.getBoolean("enable_gear_p", true);

                        if (gear == 2) { // D 挡
                            if (enableD && voicePlayer != null) {
                                voicePlayer.play("gear_d.mp3", "已挂入前进挡");
                            }
                        } else if (gear == 4) { // R 挡
                            if (enableR && voicePlayer != null) {
                                voicePlayer.play("gear_r.mp3", "已挂入倒车挡");
                            }
                        } else if (gear == 3) { // N 挡
                            if (enableN && voicePlayer != null) {
                                voicePlayer.play("gear_n.mp3", "已挂入空挡");
                            }
                        } else if (gear == 6 || gear == 7) { // S 挡
                            if (enableS && voicePlayer != null) {
                                voicePlayer.play("gear_s.mp3", "已挂入运动挡");
                            }
                        } else if (gear == 5) { // P 挡
                            // 只要曾切出过 P 挡（isGearVoiceArmed == 1 或 prevGear != 5），挂回 P 挡即刻播报
                            if (isGearVoiceArmed == 1 || prevGear != 5) {
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
            }
        };

        // 立即投递 160ms 防抖确认
        mainHandler.postDelayed(pendingGearTask, GEAR_DEBOUNCE_MS);
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
