package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

/**
 * 四门物理门控独立状态机 (DoorStateManager)
 * 纯粹聚焦于四门硬件物理状态，与尾门、换挡、驾驶模式彻底物理隔离解耦！
 * 
 * 权威输入源:
 * 1. SerialControl_v2_0: 91 02 01 00 04 00 [b6] [b7] ... (b6 为四门状态位图)
 *    - bit0-1: FL主驾 (00b=开, 01b=关)
 *    - bit2-3: FR副驾 (00b=开, 01b=关)
 *    - bit4-5: RL左后 (00b=开, 01b=关)
 *    - bit6-7: RR右后 (00b=开, 01b=关)
 * 2. MCULog: FL_DOOR_STS / FR_DOOR_STS / RL_DOOR_STS / RR_DOOR_STS Open/Close 容灾
 */
public class DoorStateManager {
    private static final String TAG = "DoorStateManager";

    public interface DoorStateListener {
        void onDoorStateChanged(int fl, int fr, int rl, int rr);
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private VehicleVoicePlayer voicePlayer;
    private DoorStateListener listener;

    // 当前物理状态 (0=关, 1=开, -1=未初始化)
    private int currentFL = -1;
    private int currentFR = -1;
    private int currentRL = -1;
    private int currentRR = -1;

    // 防连击时间戳
    private long lastTriggerFL = 0;
    private long lastTriggerFR = 0;
    private long lastTriggerRL = 0;
    private long lastTriggerRR = 0;

    public DoorStateManager(Context context, VehicleVoicePlayer voicePlayer) {
        this.context = context.getApplicationContext();
        this.voicePlayer = voicePlayer;
    }

    public void setListener(DoorStateListener listener) {
        this.listener = listener;
    }

    public void setVoicePlayer(VehicleVoicePlayer voicePlayer) {
        this.voicePlayer = voicePlayer;
    }

    public int getFL() { return currentFL == -1 ? 0 : currentFL; }
    public int getFR() { return currentFR == -1 ? 0 : currentFR; }
    public int getRL() { return currentRL == -1 ? 0 : currentRL; }
    public int getRR() { return currentRR == -1 ? 0 : currentRR; }

    /**
     * 更新四门物理状态 (由串口 b6 整体更新，或 MCULog 单门更新)
     */
    public synchronized void updateDoors(int fl, int fr, int rl, int rr, boolean voiceMasterSwitch, SharedPreferences prefs) {
        long now = System.currentTimeMillis();

        // 首次初始化仅校准物理基准，绝不盲目播报
        if (currentFL == -1) {
            currentFL = fl;
            currentFR = fr;
            currentRL = rl;
            currentRR = rr;
            AppLogger.i("四门门控", "物理基准初始化: FL=" + fl + ", FR=" + fr + ", RL=" + rl + ", RR=" + rr);
            if (listener != null) {
                listener.onDoorStateChanged(currentFL, currentFR, currentRL, currentRR);
            }
            return;
        }

        boolean changed = false;

        // 1. 主驾门 FL
        if (fl != -1 && fl != currentFL) {
            changed = true;
            boolean enableOpen = prefs.getBoolean("voice_enable_door_fl", true) && prefs.getBoolean("enable_door_fl", true);
            boolean enableClose = prefs.getBoolean("voice_enable_door_fl_close", true) && prefs.getBoolean("enable_door_fl_close", true);
            if (fl == 1) {
                AppLogger.i("四门门控", "主驾门打开 -> voiceMaster=" + voiceMasterSwitch + ", enableOpen=" + enableOpen);
                if (voiceMasterSwitch && enableOpen && (now - lastTriggerFL > 300)) {
                    lastTriggerFL = now;
                    mainHandler.postDelayed(() -> {
                        if (voiceMasterSwitch && currentFL == 1 && voicePlayer != null) {
                            voicePlayer.play("door_fl.mp3", "主驾车门打开，请注意后方来车");
                        }
                    }, 120);
                }
            } else {
                AppLogger.i("四门门控", "主驾门关好 -> voiceMaster=" + voiceMasterSwitch + ", enableClose=" + enableClose);
                if (voiceMasterSwitch && enableClose && (now - lastTriggerFL > 300)) {
                    lastTriggerFL = now;
                    if (voicePlayer != null) {
                        voicePlayer.play("door_fl_close.mp3", "主驾车门已关好");
                    }
                }
            }
            currentFL = fl;
        }

        // 2. 副驾门 FR
        if (fr != -1 && fr != currentFR) {
            changed = true;
            boolean enableOpen = prefs.getBoolean("voice_enable_door_fr", true) && prefs.getBoolean("enable_door_fr", true);
            boolean enableClose = prefs.getBoolean("voice_enable_door_fr_close", true) && prefs.getBoolean("enable_door_fr_close", true);
            if (fr == 1) {
                AppLogger.i("四门门控", "副驾门打开 -> voiceMaster=" + voiceMasterSwitch + ", enableOpen=" + enableOpen);
                if (voiceMasterSwitch && enableOpen && (now - lastTriggerFR > 300)) {
                    lastTriggerFR = now;
                    mainHandler.postDelayed(() -> {
                        if (voiceMasterSwitch && currentFR == 1 && voicePlayer != null) {
                            voicePlayer.play("door_fr.mp3", "欢迎乘车，请注意安全");
                        }
                    }, 120);
                }
            } else {
                AppLogger.i("四门门控", "副驾门关好 -> voiceMaster=" + voiceMasterSwitch + ", enableClose=" + enableClose);
                if (voiceMasterSwitch && enableClose && (now - lastTriggerFR > 300)) {
                    lastTriggerFR = now;
                    if (voicePlayer != null) {
                        voicePlayer.play("door_fr_close.mp3", "副驾已就坐，请系好安全带");
                    }
                }
            }
            currentFR = fr;
        }

        // 3. 左后门 RL
        if (rl != -1 && rl != currentRL) {
            changed = true;
            boolean enableOpen = (prefs.getBoolean("voice_enable_door_rl", true) && prefs.getBoolean("enable_door_rl", true)) || prefs.getBoolean("voice_enable_door_rear", true);
            boolean enableClose = prefs.getBoolean("voice_enable_door_rl_close", true) && prefs.getBoolean("enable_door_rl_close", true);
            if (rl == 1) {
                AppLogger.i("四门门控", "左后门打开 -> voiceMaster=" + voiceMasterSwitch + ", enableOpen=" + enableOpen);
                if (voiceMasterSwitch && enableOpen && (now - lastTriggerRL > 300)) {
                    lastTriggerRL = now;
                    mainHandler.postDelayed(() -> {
                        if (voiceMasterSwitch && currentRL == 1 && voicePlayer != null) {
                            voicePlayer.play("door_rl.mp3", "左后门打开，请注意车外环境");
                        }
                    }, 120);
                }
            } else {
                AppLogger.i("四门门控", "左后门关好 -> voiceMaster=" + voiceMasterSwitch + ", enableClose=" + enableClose);
                if (voiceMasterSwitch && enableClose && (now - lastTriggerRL > 300)) {
                    lastTriggerRL = now;
                    if (voicePlayer != null) {
                        voicePlayer.play("door_rl_close.mp3", "左后车门已关好");
                    }
                }
            }
            currentRL = rl;
        }

        // 4. 右后门 RR
        if (rr != -1 && rr != currentRR) {
            changed = true;
            boolean enableOpen = (prefs.getBoolean("voice_enable_door_rr", true) && prefs.getBoolean("enable_door_rr", true)) || prefs.getBoolean("voice_enable_door_rear", true);
            boolean enableClose = prefs.getBoolean("voice_enable_door_rr_close", true) && prefs.getBoolean("enable_door_rr_close", true);
            if (rr == 1) {
                AppLogger.i("四门门控", "右后门打开 -> voiceMaster=" + voiceMasterSwitch + ", enableOpen=" + enableOpen);
                if (voiceMasterSwitch && enableOpen && (now - lastTriggerRR > 300)) {
                    lastTriggerRR = now;
                    mainHandler.postDelayed(() -> {
                        if (voiceMasterSwitch && currentRR == 1 && voicePlayer != null) {
                            voicePlayer.play("door_rr.mp3", "右后门打开，请注意上下车安全");
                        }
                    }, 120);
                }
            } else {
                AppLogger.i("四门门控", "右后门关好 -> voiceMaster=" + voiceMasterSwitch + ", enableClose=" + enableClose);
                if (voiceMasterSwitch && enableClose && (now - lastTriggerRR > 300)) {
                    lastTriggerRR = now;
                    if (voicePlayer != null) {
                        voicePlayer.play("door_rr_close.mp3", "右后车门已关好");
                    }
                }
            }
            currentRR = rr;
        }

        if (changed && listener != null) {
            listener.onDoorStateChanged(currentFL, currentFR, currentRL, currentRR);
        }
    }
}
