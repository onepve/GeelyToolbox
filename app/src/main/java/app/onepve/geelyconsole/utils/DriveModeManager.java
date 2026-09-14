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
    private boolean isSmartModeArmed = false; // 智能模式闭锁标志：默认锁定(false)，切到其他模式时武装(true)，切回智能并播报一次后立即恢复锁定值(false)，彻底杜绝驻车循环播报
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
        boolean changed = (lastDriveMode != -1 || isSmartModeArmed);
        lastDriveMode = -1;
        isSmartModeArmed = false;
        if (changed) {
            AppLogger.i("驾驶模式", "熄火休眠复位: 重置归位基准 (lastMode=-1, smartArmed=false)");
        }
    }

    public synchronized void updateDriveMode(int mode, final boolean voiceMasterSwitch, final SharedPreferences prefs) {
        if (mode <= 0) return;

        // 1. 开机首包基准处理：车辆熄火后硬件固定归位为智能模式 (MODE_SMART)
        if (lastDriveMode == -1) {
            if (mode == MODE_SMART) {
                // 车主点火后未旋转旋钮，保持原厂默认智能模式：静默确立基准，开机绝不误播
                lastDriveMode = MODE_SMART;
                isSmartModeArmed = false;
                AppLogger.i("驾驶模式", "开机基准初始化: 当前保持出厂默认【智能模式】(静默建立基准，开机绝不误播，智能模式锁定)");
                if (listener != null) {
                    listener.onDriveModeChanged(lastDriveMode);
                }
                return;
            } else {
                // 首包即为非智能模式（舒适/运动/经济）：说明车主点火后已手动拨动旋钮！
                // 必须以原厂默认智能模式为前置基准，正常触发模式切换与语音播报，绝不吞掉车主的主动操作
                AppLogger.i("驾驶模式", "开机检测到手动切挡: 车主已手动拨动旋钮从【智能模式】->【" + getModeName(mode) + "】，立即执行模式切换播报！");
                lastDriveMode = MODE_SMART; // 前置基准确立为出厂智能模式
                // 继续往下执行常规切换播报与武装逻辑
            }
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

                    AppLogger.i("驾驶模式", "模式确认切换: " + getModeName(lastDriveMode) + " -> " + getModeName(targetMode) + ", smartArmed=" + isSmartModeArmed);

                    // 播报判定 (默认全开，支持双别名兼容)
                    if (voiceMasterSwitch) {
                        boolean enableComfort = prefs.contains("voice_enable_mode_comfort") ? prefs.getBoolean("voice_enable_mode_comfort", true) : prefs.getBoolean("enable_mode_comfort", true);
                        boolean enableSport = prefs.contains("voice_enable_mode_sport") ? prefs.getBoolean("voice_enable_mode_sport", true) : prefs.getBoolean("enable_mode_sport", true);
                        boolean enableEco = prefs.contains("voice_enable_mode_eco") ? prefs.getBoolean("voice_enable_mode_eco", true) : prefs.getBoolean("enable_mode_eco", true);
                        boolean enableSmart = prefs.contains("voice_enable_mode_smart") ? prefs.getBoolean("voice_enable_mode_smart", true) : prefs.getBoolean("enable_mode_smart", true);

                        switch (targetMode) {
                            case MODE_COMFORT:
                                if (enableComfort && voicePlayer != null) {
                                    voicePlayer.play("mode_comfort.mp3", "舒适");
                                }
                                isSmartModeArmed = true; // 主动旋到舒适：武装智能模式，允许后续切回智能时播报一次
                                break;
                            case MODE_SPORT:
                                if (enableSport && voicePlayer != null) {
                                    voicePlayer.play("mode_sport.mp3", "运动");
                                }
                                isSmartModeArmed = true; // 主动旋到运动：武装智能模式，允许后续切回智能时播报一次
                                break;
                            case MODE_ECO:
                                if (enableEco && voicePlayer != null) {
                                    voicePlayer.play("mode_eco.mp3", "经济");
                                }
                                isSmartModeArmed = true; // 主动旋到经济：武装智能模式，允许后续切回智能时播报一次
                                break;
                            case MODE_SMART:
                                if (isSmartModeArmed) {
                                    if (enableSmart && voicePlayer != null) {
                                        voicePlayer.play("mode_smart.mp3", "智能");
                                    }
                                    AppLogger.i("驾驶模式", "切回智能模式并播报一次，立即闭锁为锁定值(false)，防止驻车循环误报");
                                } else {
                                    AppLogger.i("驾驶模式", "智能模式处于锁定状态 (未从其他模式主动切回)，保持绝对静默");
                                }
                                isSmartModeArmed = false; // 立即重置为锁定值！在再次切到其他模式前绝不重复播报
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

    /**
     * 主动请求切换驾驶模式 (测试通道) —— 仅是「下发请求」，绝不等于切换成功。
     *
     * 原厂源码证据 (CarService_java ECarXCarConfigService.java)：
     * - 底层唯一确认的控制入口是原厂 ECarXCarConfigService 监听 HAL 属性
     *   INFO_ID_VDRIVEINFO_SWITCH_DRIVER_MODE (678428908)，在模式值跃变时才
     *   startService(new Intent("ecarx.settings.ACTION_DRIVE_MODE").setPackage("ecarx.settings")
     *   .putExtra("intentKey", converDriveMode(status)))。
     * - converDriveMode 官方映射 (1=舒适 570491138 / 2=运动 570491139 / 3=经济 570491137 /
     *   4=越野 570491140 / 5=雪地 570491145)；570491158(ADAPTIVE) 仅出现在 LocalConfig
     *   getDriveMode() 支持列表中，不在 converDriveMode 输出内 —— smart 下发代码不存在。
     * - 原厂发送的是 startService (显式 Intent + setPackage)，不是 broadcast。
     *
     * 工具箱作为第三方应用无法写 HAL vendor 属性 (CAR_VENDOR_EXTENSION signature|privileged)，
     * 只能尝试以显式 startService 复刻原厂入口；接收端 ecarx.settings 在固件内无源码，
     * 是否放行只能实车验证。因此：
     *   1. status="sent"    —— 请求已成功送出 (仅表示请求下发，不代表模式已切换)；
     *   2. status="blocked" —— 底层拒绝 (SecurityException/目标服务不存在)；
     *   3. status="unsupported" —— 目标模式在原厂映射表中不存在 (smart/unknown)；
     *   4. 绝不发伪造状态的 DRIVE_MODE_CHANGED 全局广播 (旧版通道 2 已删除)；
     *      那是原厂系统内部的「模式已变化」通知，第三方伪造会造成状态错乱。
     */
    public static org.json.JSONObject switchDriveModeWithResult(Context context, String modeStr) {
        org.json.JSONObject result = new org.json.JSONObject();
        try {
            result.put("status", "error");
            result.put("message", "");
        } catch (Exception ignored) {}
        if (context == null || modeStr == null) {
            return put(result, "error", "上下文或模式为空");
        }
        String mode = modeStr.trim().toLowerCase();
        int modeCode;
        int targetInt;
        switch (mode) {
            case "comfort":
                modeCode = 570491138; // 原厂 converDriveMode(1)
                targetInt = MODE_COMFORT;
                break;
            case "sport":
                modeCode = 570491139; // 原厂 converDriveMode(2)
                targetInt = MODE_SPORT;
                break;
            case "eco":
                modeCode = 570491137; // 原厂 converDriveMode(3)
                targetInt = MODE_ECO;
                break;
            case "default":
            case "":
                // default = 保持原厂智能模式: 语义上就没有「切换」可言，静默拒绝
                return put(result, "unsupported",
                        "当前预设为「保持原厂」，不存在需要下发的切换请求；请先在上方选择 智能/舒适/经济/运动 之外的目标模式");
            default:
                // smart 不在原厂 converDriveMode 映射表内 (其值 570491158 不是该函数输出)，
                // 无法构造合法 intentKey → 证据不足，返回 unsupported 静默，绝不猜 ID。
                return put(result, "unsupported",
                        "目标模式 [" + modeStr + "] 无原厂下发映射 (仅支持 comfort/sport/eco)，已静默拒绝");
        }

        try {
            AppLogger.i(TAG, "【测试切换驾驶模式】目标模式: " + mode + ", intentKey=" + modeCode
                    + " (仅下发请求，成功与否以仪表跃变为准)");
            // 唯一通道: 复刻原厂 ECarXCarConfigService 的显式 startService 入口
            android.content.Intent intent = new android.content.Intent("ecarx.settings.ACTION_DRIVE_MODE");
            intent.setPackage("ecarx.settings");
            intent.putExtra("intentKey", modeCode);
            intent.putExtra("driveMode", targetInt);
            context.startService(intent);
            return put(result, "sent",
                    "切换请求已下发 (intentKey=" + modeCode + ")，请观察仪表盘模式图标是否跟随跃变；"
                    + "未跃变即代表底层网关未放行软切换，工具箱绝不伪造成功");
        } catch (SecurityException se) {
            AppLogger.w(TAG, "【测试切换驾驶模式】底层权限拒绝: " + se.getMessage());
            return put(result, "blocked",
                    "底层网关拒绝下发 (目标服务未导出或权限不足): " + se.getMessage());
        } catch (Throwable t) {
            AppLogger.e(TAG, "切换驾驶模式异常: " + t.getMessage());
            return put(result, "error", "底层执行出错: " + t.getMessage());
        }
    }

    private static org.json.JSONObject put(org.json.JSONObject obj, String status, String message) {
        try {
            obj.put("status", status);
            obj.put("message", message);
        } catch (Exception ignored) {}
        return obj;
    }
}
