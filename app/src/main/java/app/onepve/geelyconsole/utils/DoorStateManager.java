package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

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

    // 四门独立硬件防抖时间戳 (500ms 硬件机械防抖滤波，彻底滤除串口多重重发)
    private long lastTimeFL = 0;
    private long lastTimeFR = 0;
    private long lastTimeRL = 0;
    private long lastTimeRR = 0;

    // 四门独立乘员上下车感知状态机 (true=该座位乘员已就坐/车内态, false=未就坐/车外态)
    private boolean isDriverInside = true;  // 主驾驶员 (点火默认已就坐)
    private boolean isFRInside = false;     // 副驾驶乘员
    private boolean isRLInside = false;     // 左后座乘员
    private boolean isRRInside = false;     // 右后座乘员

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
     */
    public synchronized void resetState() {
        isDriverInside = false;
        isFRInside = false;
        isRLInside = false;
        isRRInside = false;
        AppLogger.i("四门门控", "熄火休眠复位: 四门乘员感知状态机重置归位");
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

            // 若开机时主驾门是关着的，说明车主已登车点火就绪；若主驾门开着，说明车主刚探身点火门尚未闭合
            isDriverInside = (currentFL == 0);

            AppLogger.i("四门门控", "四门物理基准初始化: FL=" + currentFL + ", FR=" + currentFR + ", RL=" + currentRL + ", RR=" + currentRR + " (主驾就坐=" + isDriverInside + ")");
            if (listener != null) {
                listener.onDoorStateChanged(currentFL, currentFR, currentRL, currentRR);
            }
            return;
        }

        boolean universalMode = prefs.getBoolean("voice_door_mode_universal", true);
        boolean changed = false;

        // 2. 主驾驶门 (FL) 独立状态判定
        if (fl >= 0 && fl != currentFL && (now - lastTimeFL > 500)) {
            lastTimeFL = now;
            changed = true;
            handleDoorTransition("FL", "主驾", fl, currentFL, universalMode, voiceMasterSwitch, prefs);
            currentFL = fl;
        }

        // 3. 副驾驶门 (FR) 独立状态判定
        if (fr >= 0 && fr != currentFR && (now - lastTimeFR > 500)) {
            lastTimeFR = now;
            changed = true;
            handleDoorTransition("FR", "副驾", fr, currentFR, universalMode, voiceMasterSwitch, prefs);
            currentFR = fr;
        }

        // 4. 左后座门 (RL) 独立状态判定
        if (rl >= 0 && rl != currentRL && (now - lastTimeRL > 500)) {
            lastTimeRL = now;
            changed = true;
            handleDoorTransition("RL", "左后", rl, currentRL, universalMode, voiceMasterSwitch, prefs);
            currentRL = rl;
        }

        // 5. 右后座门 (RR) 独立状态判定
        if (rr >= 0 && rr != currentRR && (now - lastTimeRR > 500)) {
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
    private void handleDoorTransition(String doorCode, String doorName, int newSts, int oldSts, boolean universalMode, boolean voiceMasterSwitch, SharedPreferences prefs) {
        // 瞬间打断当前旧声音，实现干脆立断秒响应
        if (voicePlayer != null) {
            voicePlayer.stopCurrentVoice();
        }

        // 1. 行车中门开判定 (挂入前进挡、倒车挡或车速大于0) -> 最高优先级紧急报警
        boolean isDriving = (VehicleAutomationService.lastGearPos == 2 || VehicleAutomationService.lastGearPos == 4 || VehicleAutomationService.currentSpeedKmH > 0);

        if (newSts == 1) { // ============ 【开门动作】 ============
            if (isDriving) {
                AppLogger.w("四门门控", "【危险警报】行车中 " + doorName + "门 打开！触发最高优先级警报！");
                if (voiceMasterSwitch && voicePlayer != null) {
                    voicePlayer.play("door_open.mp3", "警告！车门未关好！");
                }
                return;
            }

            if (universalMode) {
                // 通用模式：根据该座位的乘员状态自适应分流
                boolean isSeated = getSeatState(doorCode);
                boolean enableOpen = prefs.getBoolean("voice_enable_door_universal_open", true);

                if (isSeated) {
                    // 之前人在车内，停车推门 -> 判定为【准备下车离车】
                    AppLogger.i("四门门控", "【下车感知】" + doorName + "门 打开 -> 人员准备下车离去");
                    if (voiceMasterSwitch && enableOpen && voicePlayer != null) {
                        voicePlayer.play("door_open.mp3", "请注意后方来车，带好随身物品");
                    }
                    setSeatState(doorCode, false); // 状态翻转为离车/空座
                } else {
                    // 原本在车外，拉门准备登车 -> 判定为【上车开门】
                    AppLogger.i("四门门控", "【登车感知】" + doorName + "门 打开 -> 准备登车入座");
                    if (voiceMasterSwitch && enableOpen && voicePlayer != null) {
                        voicePlayer.play("door_open.mp3", "车门已打开");
                    }
                }
            } else {
                // 独立分门模式 (车友自定义台词与音效)
                String openKey = "voice_enable_door_" + doorCode.toLowerCase();
                boolean enableOpen = prefs.getBoolean(openKey, true) && prefs.getBoolean("enable_door_" + doorCode.toLowerCase(), true);
                AppLogger.i("四门门控", "独立分门: " + doorName + "门 打开");
                if (voiceMasterSwitch && enableOpen && voicePlayer != null) {
                    String soundFile = "door_fl.mp3";
                    if ("FR".equals(doorCode)) soundFile = "door_fr.mp3";
                    else if ("RL".equals(doorCode)) soundFile = "door_rl.mp3";
                    else if ("RR".equals(doorCode)) soundFile = "door_rr.mp3";
                    String text = doorName + "车门打开，请注意后方来车";
                    voicePlayer.play(soundFile, text);
                }
            }
        } else { // ============ 【关门动作】 ============
            if (universalMode) {
                boolean isSeated = getSeatState(doorCode);
                boolean enableClose = prefs.getBoolean("voice_enable_door_universal_close", true);

                if (!isSeated) {
                    // 刚才从车外拉门进来，现在关好车门 -> 判定为【就坐就绪，准备出发】
                    AppLogger.i("四门门控", "【就坐就绪】" + doorName + "门 关好 -> 乘员已在车内就位");
                    if (voiceMasterSwitch && enableClose && voicePlayer != null) {
                        voicePlayer.play("door_close.mp3", "车门已关好");
                    }
                    setSeatState(doorCode, true); // 翻转为在座
                } else {
                    AppLogger.i("四门门控", doorName + "门 关好");
                    if (voiceMasterSwitch && enableClose && voicePlayer != null) {
                        voicePlayer.play("door_close.mp3", "车门已关好");
                    }
                }
            } else {
                // 独立分门模式
                String closeKey = "voice_enable_door_" + doorCode.toLowerCase() + "_close";
                boolean enableClose = prefs.getBoolean(closeKey, true) && prefs.getBoolean("enable_door_" + doorCode.toLowerCase() + "_close", true);
                AppLogger.i("四门门控", "独立分门: " + doorName + "门 关好");
                if (voiceMasterSwitch && enableClose && voicePlayer != null) {
                    String soundFile = "door_fl_close.mp3";
                    if ("FR".equals(doorCode)) soundFile = "door_fr_close.mp3";
                    else if ("RL".equals(doorCode)) soundFile = "door_rl_close.mp3";
                    else if ("RR".equals(doorCode)) soundFile = "door_rr_close.mp3";
                    String text = doorName + "车门已关好";
                    if ("FR".equals(doorCode)) text = "副驾已就坐，请系好安全带";
                    voicePlayer.play(soundFile, text);
                }
            }
        }
    }

    private boolean getSeatState(String doorCode) {
        switch (doorCode) {
            case "FL": return isDriverInside;
            case "FR": return isFRInside;
            case "RL": return isRLInside;
            case "RR": return isRRInside;
            default: return false;
        }
    }

    private void setSeatState(String doorCode, boolean seated) {
        switch (doorCode) {
            case "FL": isDriverInside = seated; break;
            case "FR": isFRInside = seated; break;
            case "RL": isRLInside = seated; break;
            case "RR": isRRInside = seated; break;
        }
    }
}
