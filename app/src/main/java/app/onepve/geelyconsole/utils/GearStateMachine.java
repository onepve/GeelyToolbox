package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import app.onepve.geelyconsole.services.VehicleAutomationService;

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
    private long bootInitTimeMs = System.currentTimeMillis(); // 开机/重置时间戳，用于冷启动自检毛刺吸收
    private Runnable pendingGearTask = null;
    private int pendingTargetGear = -1; // 防抖期目标挡位：同目标心跳不重置计时器
    private static final long GEAR_DEBOUNCE_MS = 0; // 目标挡位(D/R/P/S) 0ms 瞬发响应，硬件跳变即刻播报
    private static final long GEAR_N_TRANSIT_DEBOUNCE_MS = 350; // 空挡(N)过渡滤波窗口 (快速划过跳过，驻留才播)

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
        bootInitTimeMs = System.currentTimeMillis();
        if (changed) {
            AppLogger.i("挡位状态", "熄火休眠: 换挡状态机重置归零 (armed=0, lastGear=-1)");
        }
    }

    public synchronized void updateGear(int rawGear, final boolean voiceMasterSwitch, final SharedPreferences prefs) {
        if (rawGear <= 0) return;
        final int gear = normalizeGear(rawGear);
        // 严格挡位合法性检查：仅允许 2(D)、3(N)、4(R)、5(P)、6/7(S) 真实物理挡位，拦截 1 等非标诊断脏数据
        if (gear < 2 || gear > 7) return;

        // 1. 开机首包基准处理：车辆熄火后硬件强制锁 P 挡 (5)
        if (lastGearPos == -1) {
            // 开机冷启动：无论首包收到的是物理 P 挡还是当前静止状态，统一静默确立基准，开机绝对不误播任何挡位声音！
            lastGearPos = (gear >= 2 && gear <= 7) ? gear : 5;
            isGearVoiceArmed = (lastGearPos == 5) ? 0 : 1;
            AppLogger.i("挡位状态", "开机基准初始化: 确立当前基准为【" + getGearName(lastGearPos) + "】(静默建立基准，开机绝不误播，armed=" + isGearVoiceArmed + ")");
            if (listener != null) {
                listener.onGearChanged(lastGearPos);
            }
            return;
        }

        // 物理跳变回落防抖铁律：
        // 若当前有正在倒计时的防抖任务（例如毛刺 5->2），若信号瞬间又跳回当前稳定挡位 (5)，
        // 必须立即撤销该未到期的毛刺防抖任务并清空 pendingTargetGear，彻底吸收瞬态毛刺，杜绝误播！
        if (gear == lastGearPos) {
            if (pendingGearTask != null) {
                mainHandler.removeCallbacks(pendingGearTask);
                pendingGearTask = null;
                pendingTargetGear = -1;
                AppLogger.i("挡位状态", "瞬态自检毛刺已吸收: 挡位已恢复为当前稳定挡位 " + getGearName(lastGearPos) + "，撤销未到期防抖任务");
            }
            return;
        }

        // 核心铁律：一旦物理挡位发生跃变，第一毫秒立即掐灭上一段旧语音！
        // 彻底解决从倒挡切回空挡时，倒挡声音仍在播放或排队延迟播报的问题，手拨即静、立竿见影
        if (voicePlayer != null) {
            voicePlayer.stopCurrentVoice();
        }

        // 防抖核心铁律：同目标挡位的 CAN 心跳直接忽略，绝不重置计时器
        // 否则心跳间隔<160ms 时防抖任务永远无法到期，首次换挡永久无声
        if (gear == pendingTargetGear && pendingGearTask != null) {
            return; // 同目标，让已投递的防抖任务自然到期
        }
        // 目标挡位变了（如快速连切 P->N->D），才取消旧任务重新计时
        if (pendingGearTask != null) {
            mainHandler.removeCallbacks(pendingGearTask);
            pendingGearTask = null;
        }
        pendingTargetGear = gear;

        final int prevGear = lastGearPos;
        pendingGearTask = new Runnable() {
            @Override
            public void run() {
                synchronized (GearStateMachine.this) {
                    pendingGearTask = null;
                    pendingTargetGear = -1;
                    if (gear == lastGearPos) return;

                    AppLogger.i("挡位状态", "挡位确认跃变: " + getGearName(lastGearPos) + " -> " + getGearName(gear) + ", armed=" + isGearVoiceArmed);

                    // 1. 换出 P 挡真正进入行车/运行挡 (D挡2, N挡3, R挡4, S挡6/7)，才允许武装状态机
                    if (gear == 2 || gear == 3 || gear == 4 || gear == 6 || gear == 7) {
                        isGearVoiceArmed = 1;
                    }

                    // 2. 播报判定 (干脆利落短句，强制瞬发顶掉任何旧语音，绝不排队延时)
                    if (voiceMasterSwitch) {
                        if (voicePlayer != null) {
                            voicePlayer.stopCurrentVoice();
                        }
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
                            // 核心铁律：只要是从非 P 挡切换回 P 挡（prevGear != 5），100% 灵敏播报驻车挡！
                            // 彻底消除按 P 键偶尔哑巴无声的顽疾！
                            if (prevGear != 5) {
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

        // 动态防抖策略：
        // 核心铁律：所有目标挡位(D/R/P/S/N) 严格 0ms 瞬发执行，手落声起！
        // 换挡响应设计：所有挡位(D/R/P/S/N) 严格 0ms 瞬发执行，保证操作即时播报。严禁对 N 挡增加 350ms 延迟防误以为滞后排队。
        long debounceTime = GEAR_DEBOUNCE_MS;
        if (debounceTime <= 0) {
            pendingGearTask.run();
        } else {
            mainHandler.postDelayed(pendingGearTask, debounceTime);
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
