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
 * 基于 v1.7.47 稳定基线打造，支持默认女声、公主专属、女王专属三套完整副驾车门音效。
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

    // 四门独立乘员上下车感知状态机 (true=该座位乘员已就坐/车内态, false=未就坐/车外态)
    private boolean isDriverInside = true;  // 主驾驶员 (点火默认已就坐)
    private boolean isFRInside = false;     // 副驾驶乘员
    private boolean isRLInside = false;     // 左后座乘员
    private boolean isRRInside = false;     // 右后座乘员

    // 四门正在进行的动作意图 (1=登车中 ENTERING, 2=离车中 EXITING)
    private final Map<String, Integer> doorActionIntent = new HashMap<>();
    private static final int INTENT_NONE = 0;

    // 同门同动作语音防抖 (1500ms)，防止多源报文串扰导致重复播报
    private final Map<String, Long> lastVoiceTime = new HashMap<>();
    private static final long DOOR_VOICE_DEBOUNCE_MS = 1500;

    // 底盘硬件传感器多重融合 (兼容保留接口，防止外部调用编译异常)
    private boolean hardwareDriverBeltBuckled = false;
    private boolean hardwarePassengerSeated = false;
    private boolean hasDriverBeltSensor = false;
    private boolean hasPassengerSensor = false;

    public void updateDriverBeltState(boolean buckled) {
        this.hardwareDriverBeltBuckled = buckled;
        this.hasDriverBeltSensor = true;
        if (buckled) {
            this.isDriverInside = true;
            this.doorActionIntent.put("FL", INTENT_NONE);
        }
    }

    public void updatePassengerOccupancy(boolean seated) {
        this.hardwarePassengerSeated = seated;
        this.hasPassengerSensor = true;
        if (seated) {
            this.isFRInside = true;
            this.doorActionIntent.put("FR", INTENT_NONE);
        }
    }

    public DoorStateManager(Context context, VehicleVoicePlayer voicePlayer) {
        this.context = context.getApplicationContext();
        this.voicePlayer = voicePlayer;
        if (this.context != null) {
            SharedPreferences prefs = this.context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            PrefUtils.sanitizeDoorPreferences(prefs);
        }
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
     * 幂等静默：仅在状态真正发生变化时才写日志，严禁被 MCU 心跳无限刷屏。
     */
    public synchronized void resetState() {
        boolean changed = (isDriverInside || isFRInside || isRLInside || isRRInside || !doorActionIntent.isEmpty());
        isDriverInside = false;
        isFRInside = false;
        isRLInside = false;
        isRRInside = false;
        doorActionIntent.clear();
        // 铁律：绝对不清除 lastVoiceTime 和 lastTimeFL/FR/RL/RR 硬件物理防抖！
        // 彻底杜绝点火熄火电源模式跳变时防抖被清空导致连续连报！
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
        boolean universalMode = PrefUtils.getBoolean(prefs, "voice_door_mode_universal", false);

        // 1. 首次开机物理基准建立 (智能识别迎宾登车意图)
        if (currentFL == -1) {
            PrefUtils.sanitizeDoorPreferences(prefs);
            currentFL = (fl >= 0) ? fl : 0;
            currentFR = (fr >= 0) ? fr : 0;
            currentRL = (rl >= 0) ? rl : 0;
            currentRR = (rr >= 0) ? rr : 0;

            // 行车途中重启/更新守护：若车速大于0，证明车辆处于行驶状态，主驾必然就坐且四门全关，杜绝行车误报
            if (VehicleAutomationService.currentSpeedKmH > 0) {
                isDriverInside = true;
                currentFL = 0;
                currentFR = 0;
                currentRL = 0;
                currentRR = 0;
                AppLogger.i("车门状态", "行车中开机/重启基准建立 (车速=" + VehicleAutomationService.currentSpeedKmH + "km/h) -> 判定车内就坐并全关，跳过所有开机播报");
                if (listener != null) {
                    listener.onDoorStateChanged(0, 0, 0, 0);
                }
                return;
            }

            // 乘员就坐状态统一置为车外态，等待上车或电源通知
            isDriverInside = false;

            AppLogger.i("车门状态", "四门物理基准初始化: FL=" + currentFL + ", FR=" + currentFR + ", RL=" + currentRL + ", RR=" + currentRR + " (主驾就坐=" + isDriverInside + ")");
            if (listener != null) {
                listener.onDoorStateChanged(currentFL, currentFR, currentRL, currentRR);
            }

            // 核心迎宾与时序闭环：
            // 若开机首包即为关门态 (0)，说明车主在车机启动就绪前就已关门就坐，仅静默建立基准，绝不倒灌补报关门！
            // 仅当首包即为开门态 (1) 时，才触发迎宾开门播报
            if (currentFL == 1) {
                String welcomeTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.CHINA).format(new java.util.Date(now));
                AppLogger.i("车辆迎宾", "车辆迎宾时间: " + welcomeTime + " (检测到主驾开门首包，即刻触发迎宾播报)");
                lastTimeFL = now;
                handleDoorTransition("FL", "主驾", 1, 0, universalMode, voiceMasterSwitch, prefs);
            } else if (currentFR == 1) {
                lastTimeFR = now;
                handleDoorTransition("FR", "副驾", 1, 0, universalMode, voiceMasterSwitch, prefs);
            } else if (currentRL == 1) {
                lastTimeRL = now;
                handleDoorTransition("RL", "左后", 1, 0, universalMode, voiceMasterSwitch, prefs);
            } else if (currentRR == 1) {
                lastTimeRR = now;
                handleDoorTransition("RR", "右后", 1, 0, universalMode, voiceMasterSwitch, prefs);
            } else {
                AppLogger.i("车门状态", "开机首包检测到四门全关，静默对齐当前物理基准 (0 误播报)");
            }
            return;
        }

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
    private void handleDoorTransition(String doorCode, String doorName, int newSts, int oldSts, boolean universalMode, boolean voiceMasterSwitch, SharedPreferences prefs) {
        long now = System.currentTimeMillis();

        // 瞬间打断当前旧声音，实现干脆立断秒响应
        if (voicePlayer != null) {
            voicePlayer.stopCurrentVoice();
        }

        String voiceKey = doorCode + "_" + newSts;
        Long last = lastVoiceTime.get(voiceKey);
        if (last != null && (now - last) < DOOR_VOICE_DEBOUNCE_MS) {
            AppLogger.i("车门状态", "同门同动作 1.5s 内重复触发，已抑制: " + doorName + (newSts == 1 ? "开" : "关"));
            return;
        }
        lastVoiceTime.put(voiceKey, now);

        // 1. 行车中门开判定 (挂入前进挡、倒车挡或车速大于0) -> 最高优先级紧急报警
        boolean isDriving = (VehicleAutomationService.lastGearPos == 2 || VehicleAutomationService.lastGearPos == 4 || VehicleAutomationService.currentSpeedKmH > 0);

        if (newSts == 1) { // ============ 【开门动作】 ============
            if (isDriving) {
                AppLogger.w("车门状态", "【危险警报】行车中 " + doorName + "门 打开！触发最高优先级警报！");
                if (voiceMasterSwitch && voicePlayer != null) {
                    voicePlayer.play("door_open.mp3", "警告！车门未关好！");
                }
                return;
            }

            AppLogger.i("车门状态", doorName + "门 打开");

            if (universalMode) {
                boolean enableOpen = PrefUtils.getBoolean(prefs, "voice_enable_door_universal_open", true);
                if (voiceMasterSwitch && enableOpen && voicePlayer != null) {
                    voicePlayer.play("door_open.mp3", "车门已打开");
                }
            } else {
                String openKey = "voice_enable_door_" + doorCode.toLowerCase();
                boolean enableOpen = PrefUtils.getBoolean(prefs, openKey, true) && PrefUtils.getBoolean(prefs, "enable_door_" + doorCode.toLowerCase(), true);
                if (voiceMasterSwitch && enableOpen && voicePlayer != null) {
                    String soundFile = "door_fl.mp3";
                    String text = doorName + "车门已打开";
                    if ("FR".equals(doorCode)) {
                        soundFile = "door_fr.mp3";
                        text = "欢迎乘车";
                    } else if ("RL".equals(doorCode)) {
                        soundFile = "door_rl.mp3";
                    } else if ("RR".equals(doorCode)) {
                        soundFile = "door_rr.mp3";
                    }
                    voicePlayer.play(soundFile, text);
                }
            }
        } else { // ============ 【关门动作】 ============
            AppLogger.i("车门状态", doorName + "门 关好");

            if (universalMode) {
                boolean enableClose = PrefUtils.getBoolean(prefs, "voice_enable_door_universal_close", true);
                if (voiceMasterSwitch && enableClose && voicePlayer != null) {
                    voicePlayer.play("door_close.mp3", "车门已关好");
                }
            } else {
                // 独立分门模式
                String closeKey = "voice_enable_door_" + doorCode.toLowerCase() + "_close";
                boolean enableClose = PrefUtils.getBoolean(prefs, closeKey, true) && PrefUtils.getBoolean(prefs, "enable_door_" + doorCode.toLowerCase() + "_close", true);
                if (voiceMasterSwitch && enableClose && voicePlayer != null) {
                    String soundFile = "door_fl_close.mp3";
                    String text = doorName + "车门已关好";

                    if ("FR".equals(doorCode)) {
                        soundFile = "door_fr_close.mp3";
                        text = "副驾车门已关好";
                    } else if ("RL".equals(doorCode)) {
                        soundFile = "door_rl_close.mp3";
                    } else if ("RR".equals(doorCode)) {
                        soundFile = "door_rr_close.mp3";
                    }
                    voicePlayer.play(soundFile, text);
                }
            }
        }
    }

    public int getCurrentFL() { return currentFL; }
    public int getCurrentFR() { return currentFR; }
    public int getCurrentRL() { return currentRL; }
    public int getCurrentRR() { return currentRR; }
    public boolean isAnyDoorOpen() {
        return currentFL == 1 || currentFR == 1 || currentRL == 1 || currentRR == 1;
    }
}
