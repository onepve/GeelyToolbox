package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

/**
 * 电动尾门专属监听与状态机 (TrunkStateManager)
 * 纯粹聚焦于电动尾门/后备箱硬件物理状态，与四门完全解耦独立！
 * 
 * 权威输入源:
 * 1. SerialControl_v2_0: 91 02 01 00 04 00 [b6] [b7] ... (b7 为尾门状态位图)
 *    - (b7 & 0x01) == 0: 尾门解锁升起打开 (如 0xF4)
 *    - (b7 & 0x01) == 1: 尾门锁死闭合关好 (如 0xF5)
 * 2. MCULog: TRUNK_DOOR_STS / TAILGATE_DOOR_STS / BACK_DOOR_STS Open/Close 容灾
 */
public class TrunkStateManager {
    private static final String TAG = "TrunkStateManager";

    public interface TrunkStateListener {
        void onTrunkStateChanged(int trunk);
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private VehicleVoicePlayer voicePlayer;
    private TrunkStateListener listener;

    // 当前物理状态 (0=关, 1=开, -1=未初始化)
    private int currentTrunk = -1;
    private long lastTriggerTrunk = 0;

    public TrunkStateManager(Context context, VehicleVoicePlayer voicePlayer) {
        this.context = context.getApplicationContext();
        this.voicePlayer = voicePlayer;
    }

    public void setListener(TrunkStateListener listener) {
        this.listener = listener;
    }

    public void setVoicePlayer(VehicleVoicePlayer voicePlayer) {
        this.voicePlayer = voicePlayer;
    }

    public int getTrunk() { return currentTrunk == -1 ? 0 : currentTrunk; }

    /**
     * 更新电动尾门物理状态
     */
    public synchronized void updateTrunk(int trunk, boolean voiceMasterSwitch, SharedPreferences prefs) {
        if (trunk == -1) return;
        long now = System.currentTimeMillis();

        if (currentTrunk == -1) {
            currentTrunk = trunk;
            AppLogger.i("车门状态", "物理基准初始化: 尾门状态=" + (trunk == 1 ? "开" : "关"));
            if (listener != null) {
                listener.onTrunkStateChanged(currentTrunk);
            }
            return;
        }

        if (trunk != currentTrunk) {
            boolean enableOpen = prefs.getBoolean("voice_enable_trunk_open", true) && prefs.getBoolean("enable_trunk_open", true);
            boolean enableClose = prefs.getBoolean("voice_enable_trunk_close", true) && prefs.getBoolean("enable_trunk_close", true);

            if (trunk == 1) {
                AppLogger.i("车门状态", "捕获物理跃变: 后备箱打开 -> voiceMaster=" + voiceMasterSwitch + ", enableOpen=" + enableOpen);
                if (voiceMasterSwitch && enableOpen && (now - lastTriggerTrunk > 300)) {
                    lastTriggerTrunk = now;
                    mainHandler.postDelayed(() -> {
                        if (voiceMasterSwitch && currentTrunk == 1 && voicePlayer != null) {
                            voicePlayer.play("trunk_open.mp3", "后备箱已打开");
                        }
                    }, 120);
                }
            } else {
                AppLogger.i("车门状态", "捕获物理跃变: 后备箱关闭 -> voiceMaster=" + voiceMasterSwitch + ", enableClose=" + enableClose);
                if (voiceMasterSwitch && enableClose && (now - lastTriggerTrunk > 300)) {
                    lastTriggerTrunk = now;
                    if (voicePlayer != null) {
                        voicePlayer.play("trunk_close.mp3", "后备箱已关闭");
                    }
                }
            }

            currentTrunk = trunk;
            if (listener != null) {
                listener.onTrunkStateChanged(currentTrunk);
            }
        }
    }
}
