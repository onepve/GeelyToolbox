package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.Map;
import java.util.HashMap;

import app.onepve.geelyconsole.services.VehicleAutomationService;

/**
 * 四门物理门控独立状态机 (DoorStateManager)
 * 彻底解耦四门逻辑，独立维护每扇门的物理状态与乘员上下车感知状态机！
 * 
 * 权威输入源:
 * 1. SerialControl_v2_0: 91 02 01 00 04 00 [b6] [b7] ... (b6 为四门状态位图)
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

    // 四门独立物理状态 (0=关, 1=开, -1=未初始化基准)
    private int currentFL = -1;
    private int currentFR = -1;
    private int currentRL = -1;
    private int currentRR = -1;

    // 四门独立硬件防抖时间戳 (800ms 硬件机械防抖滤波，彻底滤除微动开关反弹与颠簸抖动)
    private static final long DOOR_PHYSICAL_DEBOUNCE_MS = 800;
    private long lastTimeFL = 0;
    private long lastTimeFR = 0;
    private long lastTimeRL = 0;
    private long lastTimeRR = 0;

    // 兼容保留接口，防止外部调用编译异常
    public void updateDriverBeltState(boolean buckled) {}
    public void updatePassengerOccupancy(boolean seated) {}

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
     * 熄火休眠/下电复位：重置所有车门乘员状态机
     * ⚠️ 幂等静默：仅在状态真正发生变化时才写日志，严禁被 MCU 心跳无限刷屏。
     */
    public synchronized void resetState() {
        boolean changed = (isDriverInside || isFRInside || isRLInside || isRRInside || !doorActionIntent.isEmpty());
        isDriverInside = false;
        isFRInside = false;
        isRLInside = false;
        isRRInside = false;
        doorActionIntent.clear();
        if (changed) {
            AppLogger.i("车门状态", "熄火休眠复位: 四门乘员感知状态机重置归位");
        }
    }

    /** 车辆点火/上电/运行中：主驾一定在车内，避免"车门已打开"被误判为上车 */
    public synchronized void markDriverInside() {
        if (!isDriverInside) {
            isDriverInside = true;
            doorActionIntent.put("FL", INTENT_NONE);
            AppLogger.i("车门状态", "点火/上电确认 -> 主驾已就坐基准建立");
        }
    }

    /** 冷启动/解锁唤醒：主驾可能即将登车，把主驾座椅先置为'车外'，这样开门会播报"车门已打开" */
    public synchronized void markDriverMayEnter() {
        isDriverInside = false;
        doorActionIntent.put("FL", INTENT_NONE);
        AppLogger.i("车门状态", "冷启动/解锁 -> 主驾待登车基准建立");
    }

    /**
     * 更新四门物理状态 (由串口 b6 整体更新，或 MCULog 单门更新)
     */
    public synchronized void updateDoors(int fl, int fr, int rl, int rr, boolean voiceMasterSwitch, SharedPreferences prefs) {
        long now = System.currentTimeMillis();

        // 1. 首次开机物理基准建立 (绝不盲目播报，精准校准主驾初始状态)
        if (currentFL == -1) {
            currentFL = (fl >= 0) ? fl : 0;
            currentFR = (fr >= 0) ? fr : 0;
            currentRL = (rl >= 0) ? rl : 0;
            currentRR = (rr >= 0) ? rr : 0;

            // 乘员就坐状态不由车门初始位置决定，统一等待电源/点火状态机通知。
            // 默认值保持 false，点火后 markDriverInside() 会立刻把主驾置为在车内。
            isDriverInside = false;

            AppLogger.i("车门状态", "四门物理基准初始化: FL=" + currentFL + ", FR=" + currentFR + ", RL=" + currentRL + ", RR=" + currentRR + " (主驾就坐=" + isDriverInside + ")");
            if (listener != null) {
                listener.onDoorStateChanged(currentFL, currentFR, currentRL, currentRR);
            }
            return;
        }

        boolean universalMode = prefs.getBoolean("voice_door_mode_universal", false);
        boolean changed = false;

        // 2. 主驾驶门 (FL) 独立状态判定
        if (fl >= 0 && fl != currentFL && (now - lastTimeFL > DOOR_PHYSICAL_DEBOUNCE_MS)) {
            lastTimeFL = now;
            changed = true;
            handleDoorTransition("FL", "主驾", fl, currentFL, universalMode, voiceMasterSwitch, prefs);
            currentFL = fl;
        }

        // 3. 副驾驶门 (FR) 独立状态判定
        if (fr >= 0 && fr != currentFR && (now - lastTimeFR > DOOR_PHYSICAL_DEBOUNCE_MS)) {
            lastTimeFR = now;
            changed = true;
            handleDoorTransition("FR", "副驾", fr, currentFR, universalMode, voiceMasterSwitch, prefs);
            currentFR = fr;
        }

        // 4. 左后座门 (RL) 独立状态判定
        if (rl >= 0 && rl != currentRL && (now - lastTimeRL > DOOR_PHYSICAL_DEBOUNCE_MS)) {
            lastTimeRL = now;
            changed = true;
            handleDoorTransition("RL", "左后", rl, currentRL, universalMode, voiceMasterSwitch, prefs);
            currentRL = rl;
        }

        // 5. 右后座门 (RR) 独立状态判定
        if (rr >= 0 && rr != currentRR && (now - lastTimeRR > DOOR_PHYSICAL_DEBOUNCE_MS)) {
            lastTimeRR = now;
            changed = true;
            handleDoorTransition("RR", "右后", rr, currentRR, universalMode, voiceMasterSwitch, prefs);
            currentRR = rr;
        }

        if (changed && listener != null) {
            listener.onDoorStateChanged(currentFL, currentFR, currentRL, currentRR);
        }
    }

    /**
     * 单门状态跃变处理 (0ms 瞬发响应，打断旧声音)
     */
    // 同门同动作语音防抖 (2000ms)，防止多源报文串扰导致重复播报
    private final Map<String, Long> lastVoiceTime = new HashMap<>();
    private static final long DOOR_VOICE_DEBOUNCE_MS = 1500;

    private void handleDoorTransition(String doorCode, String doorName, int newSts, int oldSts, boolean universalMode, boolean voiceMasterSwitch, SharedPreferences prefs) {
        // 瞬间打断当前旧声音，实现干脆立断秒响应
        if (voicePlayer != null) {
            voicePlayer.stopCurrentVoice();
        }

        String voiceKey = doorCode + "_" + newSts;
        long now = System.currentTimeMillis();
        Long last = lastVoiceTime.get(voiceKey);
        if (last != null && (now - last) < DOOR_VOICE_DEBOUNCE_MS) {
            AppLogger.i("车门状态", "同门同动作 2s 内重复触发，已抑制: " + doorName + (newSts == 1 ? "开" : "关"));
            return;
        }
        lastVoiceTime.put(voiceKey, now);

        // 1. 行车中门开判定 (挂入前进挡、倒车挡或车速大于0) -> 最高优先级紧急报警
        boolean isDriving = (VehicleAutomationService.lastGearPos == 2 || VehicleAutomationService.lastGearPos == 4 || VehicleAutomationService.currentSpeedKmH > 0);

        if (newSts == 1) { // ============ 【物理开门动作】 ============
            if (isDriving) {
                AppLogger.w("车门状态", "【危险警报】行车中 " + doorName + "门 打开！触发最高优先级警报！");
                if (voiceMasterSwitch && voicePlayer != null) {
                    voicePlayer.play("door_open.mp3", "警告！车门未关好！");
                }
                return;
            }

            AppLogger.i("车门状态", "【开门播报】" + doorName + "门 打开");
            if (universalMode) {
                boolean enableOpen = prefs.getBoolean("voice_enable_door_universal_open", true);
                if (voiceMasterSwitch && enableOpen && voicePlayer != null) {
                    voicePlayer.play("door_open.mp3", "车门已打开");
                }
            } else {
                String openKey = "voice_enable_door_" + doorCode.toLowerCase();
                String enterKey = "voice_enable_door_" + doorCode.toLowerCase() + "_enter";
                boolean enableOpen = prefs.getBoolean(openKey, prefs.getBoolean(enterKey, true)) && prefs.getBoolean("enable_door_" + doorCode.toLowerCase(), true);
                if (voiceMasterSwitch && enableOpen && voicePlayer != null) {
                    String soundFile = "door_fl.mp3";
                    String text = doorName + "车门已打开";
                    if ("FL".equals(doorCode)) {
                        soundFile = "door_fl.mp3";
                        text = "主驾车门已打开";
                    } else if ("FR".equals(doorCode)) {
                        String role = prefs.getString("passenger_voice_role", "female");
                        if ("queen".equals(role)) {
                            soundFile = "door_fr_queen_enter.mp3";
                            text = "恭迎女王殿下";
                        } else if ("princess".equals(role)) {
                            soundFile = "door_fr_princess_enter.mp3";
                            text = "欢迎公主上车";
                        } else {
                            soundFile = "door_fr_enter.mp3";
                            text = "欢迎乘车";
                        }
                    } else if ("RL".equals(doorCode)) {
                        soundFile = "door_rl.mp3";
                        text = "左后车门已打开";
                    } else if ("RR".equals(doorCode)) {
                        soundFile = "door_rr.mp3";
                        text = "右后车门已打开";
                    }
                    voicePlayer.play(soundFile, text);
                }
            }
        } else { // ============ 【物理关门动作】 ============
            AppLogger.i("车门状态", "【关门播报】" + doorName + "门 关好");
            if (universalMode) {
                boolean enableClose = prefs.getBoolean("voice_enable_door_universal_close", true);
                if (voiceMasterSwitch && enableClose && voicePlayer != null) {
                    voicePlayer.play("door_close.mp3", "车门已关好");
                }
            } else {
                String closeKey = "voice_enable_door_" + doorCode.toLowerCase() + "_close";
                boolean enableClose = prefs.getBoolean(closeKey, true) && prefs.getBoolean("enable_door_" + doorCode.toLowerCase() + "_close", true);
                if (voiceMasterSwitch && enableClose && voicePlayer != null) {
                    String soundFile = "door_fl_close.mp3";
                    String text = doorName + "车门已关好";

                    if ("FL".equals(doorCode)) {
                        soundFile = "door_fl_close.mp3";
                        text = "主驾车门已关好";
                    } else if ("FR".equals(doorCode)) {
                        String role = prefs.getString("passenger_voice_role", "female");
                        if ("queen".equals(role)) {
                            soundFile = "door_fr_queen_close.mp3";
                            text = "副驾车门已关好";
                        } else if ("princess".equals(role)) {
                            soundFile = "door_fr_princess_close.mp3";
                            text = "副驾车门已关好";
                        } else {
                            soundFile = "door_fr_close.mp3";
                            text = "副驾车门已关好";
                        }
                    } else if ("RL".equals(doorCode)) {
                        soundFile = "door_rl_close.mp3";
                        text = "左后车门已关好";
                    } else if ("RR".equals(doorCode)) {
                        soundFile = "door_rr_close.mp3";
                        text = "右后车门已关好";
                    }
                    voicePlayer.play(soundFile, text);
                }
            }
        }
    }
}
