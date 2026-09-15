package app.onepve.geelyconsole.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.onepve.geelyconsole.R;
import app.onepve.geelyconsole.utils.AdbClient;
import app.onepve.geelyconsole.utils.AppLogger;
import app.onepve.geelyconsole.utils.DoorStateManager;
import app.onepve.geelyconsole.utils.DriveModeManager;
import app.onepve.geelyconsole.utils.EasMediaBridge;
import app.onepve.geelyconsole.utils.GearStateMachine;
import app.onepve.geelyconsole.utils.IdleScreensaverManager;
import app.onepve.geelyconsole.utils.PrefsCompat;
import app.onepve.geelyconsole.utils.SafetySensorStateMachine;
import app.onepve.geelyconsole.utils.SteeringWheelKeyManager;
import app.onepve.geelyconsole.utils.SystemUtils;
import app.onepve.geelyconsole.utils.TrunkStateManager;
import app.onepve.geelyconsole.utils.VehicleVoicePlayer;

/**
 * 吉利座舱自动化与智能联动常驻守护服务（替代第三方 TSK / Tasker）
 * 1. 物理状态翻转机：四门及尾门纯物理边沿状态触发，彻底废弃时间防抖锁，开门未完关门毫秒打断
 * 2. MCU 物理报文锁定：SerialControl_v2_0: 91 02 01 b6(四门) 与 b7(尾门)，阻断 AVM 伪信号
 * 3. 挡位安全播报：MCULog:GearPosition (2=D 前进挡, 4=R 倒车挡)，天然避开 P 挡蓝牙靠近点亮零误报
 * 4. 方向盘方控拦截：Mode键/静音短按转360，集成米小江方控兼容模式单选
 * 5. 蓝牙音频自动接管：监听 A2DP 连接与推流，免点原厂多媒体直接出声
 */
public class VehicleAutomationService extends Service {

    private static final String TAG = "VehicleAutomationService";
    private static final String CHANNEL_ID = "geely_toolbox_daemon";
    private static final int NOTIF_ID = 1002;

    public static volatile boolean isRunning = false;
    public static volatile float latestBatteryVoltage = 0.0f;
    public static volatile boolean voiceMasterSwitch = true;
    public static volatile boolean wheelMasterSwitch = true;
    private float lastSavedBatteryVoltage = -1.0f;

    // 功能开关
    private boolean enableDoorFl = false;
    private boolean enableDoorFlClose = false;
    private boolean enableDoorFr = false;
    private boolean enableDoorFrClose = false;
    private boolean enableDoorRl = false;
    private boolean enableDoorRlClose = false;
    private boolean enableDoorRr = false;
    private boolean enableDoorRrClose = false;
    private boolean enableDoorRear = false;
    private boolean enableTrunkOpen = false;
    private boolean enableTrunkClose = false;
    private boolean enableGearD = false;
    private boolean enableGearR = false;
    private boolean enableGearP = false;
    private boolean enableGearN = false;
    private boolean enableGearS = false;
    private boolean enableModeSmart = true;
    private boolean enableModeComfort = true;
    private boolean enableModeEco = true;
    private boolean enableModeSport = true;
    private boolean enableLightNav = false;
    private boolean enableFlameoutVoice = false;
    // 车况安全守护四项测试开关聚合 (shouldRun 门控)
    private boolean enableSafetyGuards = true;

    // 驾驶模式标准解耦枚举 (100% 根绝底层各协议数值冲突)
    public static final int MODE_COMFORT = 1; // 舒适模式
    public static final int MODE_SPORT   = 2; // 运动模式
    public static final int MODE_ECO     = 3; // 经济模式
    public static final int MODE_SMART   = 4; // 智能模式

    // 状态记录变量 (初始化为 -1，首包仅校准基准绝不误播)
    public static volatile int currentDoorFL = -1;
    public static volatile int currentDoorFR = -1;
    public static volatile int currentDoorRL = -1;
    public static volatile int currentDoorRR = -1;
    public static volatile int currentTrunk = -1;

    public static volatile int lastGearPos = -1;
    public static volatile int lastDriveMode = -1;
    private int lastLightSts = -1;
    private int lastLightStsForDim = -1; // 背光微调专用跃变记录（与高德日夜模式判定解耦，避免互相干扰）
    private int lastPowerMode = -1;
    private int lastKeyState = -1;
    private int lastEngineState = -1;
    public static volatile int currentSpeedKmH = 0;

    // 同状态接触微抖动过滤 (毫秒)
    private long lastTriggerFL = 0;
    private long lastTriggerFR = 0;
    private long lastTriggerRL = 0;
    private long lastTriggerRR = 0;
    private long lastTriggerTrunk = 0;
    private long lastTriggerGear = 0;
    /** 电源聚合状态字符串（仅变化时才写日志，杜绝心跳刷屏） */
    private String lastPowerStateAggregate = "";

    private Thread logcatThread;
    private Process logcatProcess;
    private VehicleVoicePlayer voicePlayer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SteeringWheelKeyManager wheelKeyManager;
    private BroadcastReceiver powerReceiver;
    private BroadcastReceiver ecarxKeyReceiver;

    // 四大独立解耦状态管理器
    private DoorStateManager doorStateManager;
    private TrunkStateManager trunkStateManager;
    private GearStateMachine gearStateMachine;
    private DriveModeManager driveModeManager;

    // 车况感知与安全守护状态变量 (判定逻辑收敛进 SafetySensorStateMachine 纯 Java 状态机)
    private final app.onepve.geelyconsole.utils.SafetySensorStateMachine safetySensors =
            new SafetySensorStateMachine(null);
    /** 信号时间戳 (单调 SystemClock) */
    private long steerAngleSignalAt = 0L;
    private long epbSignalAt = 0L;
    private long remainOdoSignalAt = 0L;
    private long tcuTempSignalAt = 0L;
    private long oilPressureSignalAt = 0L;
    /** 桥接只读快照 (供 MainActivity 同步读取，不加锁只写 volatile) */
    private volatile boolean lastEngineRunningSnapshot = false;

    /** 桥接辅助: MainActivity 同步读取发动机运行判定 */
    public static boolean isEngineRunningForBridge() {
        VehicleAutomationService svc = instance;
        return svc != null && svc.isEngineRunning();
    }

    /** 桥接辅助: 停稳 P 挡 + 新鲜零车速判定 (手动实测门槛) */
    public static boolean isParkedStillForBridge() {
        VehicleAutomationService svc = instance;
        if (svc == null) return false;
        return svc.gearStateMachine != null && svc.gearStateMachine.getGear() == 5
                && VehicleAutomationService.currentSpeedKmH == 0;
    }

    private static volatile VehicleAutomationService instance;

    /**
     * P挡开门安全守护 (证据不足安全静默):
     * - EPB: 仅当 EPB 信号在新鲜窗口内且确知为「释放」才告警 (P0)；
     * - 方向盘: 仅当角度信号新鲜且 |角度|>60° 才提醒 (P3)；
     * - 任何 unknown/过期信号一律静默，绝不误警。
     */
    private void checkEpbAndSteerAngleOnDriverDoorOpen() {
        if (gearStateMachine == null) {
            return;
        }

        int gear = gearStateMachine.getGear();

        // 【最高优先级 P0】挡位与电子手刹双维度驻车安全守护：
        //   停车状态下推开车门前，必须确认「已挂 P 挡」且「电子手刹已拉起」，
        //   任一项不满足车辆都可能溜车（P 挡与手刹既关联又独立，两者都要检测）。
        if (gear != 5) {
            // 未挂驻车挡(P挡)就推开车门 → 溜车高危，最高优先级警告。
            // 仅当车辆点火运行时才告警；熄火下电或 ACC 只开车机时证据不足安全静默。
            if (isEngineRunning()) {
                AppLogger.w("安全守护", "【P0报警】挡位[" + gear + "]未挂驻车挡就推开车门，车辆可能溜车！");
                if (voicePlayer != null) {
                    voicePlayer.play("gear_park_alarm.mp3", "请挂入驻车挡", VehicleVoicePlayer.PRIORITY_P0_ALARM);
                }
            }
            return; // 非驻车挡：不再检查手刹与方向盘
        }

        SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
        long now = android.os.SystemClock.elapsedRealtime();

        // 1. 电子手刹未拉溜车报警 (P0 优先级)
        boolean epbGuard = prefs.getBoolean("voice_enable_epb_guard", true);
        if (epbGuard && epbSignalAt > 0 && now - epbSignalAt <= SafetySensorStateMachine.SIGNAL_FRESHNESS_MS) {
            int r = safetySensors.checkEpbNotEngagedOnPDoorOpen(now);
            if (r == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED) {
                AppLogger.w("安全守护", "【P0报警】挂P挡推开主驾门，检测到电子手刹未拉起！(已确认，本行程不再重复)");
                if (voicePlayer != null) {
                    voicePlayer.play("epb_alarm.mp3", "警告，电子手刹未拉起", VehicleVoicePlayer.PRIORITY_P0_ALARM);
                }
                return; // P0 报警触发后不再报次要提醒
            }
        }

        // 2. 方向盘未回正提醒 (P3 优先级; 阈值 60° 为测试值)
        boolean steerGuard = prefs.getBoolean("voice_enable_steer_angle_guard", true);
        if (steerGuard && steerAngleSignalAt > 0
                && now - steerAngleSignalAt <= SafetySensorStateMachine.SIGNAL_FRESHNESS_MS) {
            int r = safetySensors.checkSteerAngleNotCentered(now);
            if (r == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED) {
                double deg = safetySensors.getKnownValue(SafetySensorStateMachine.SENSOR_STEER_ANGLE);
                AppLogger.i("安全守护", "【P3关怀】挂P挡推开主驾门，方向盘未回正 (偏角=" + (int) deg + "°，测试阈值60°)");
                if (voicePlayer != null) {
                    voicePlayer.play("steer_angle_guard.mp3", "请注意回正方向盘", VehicleVoicePlayer.PRIORITY_P3_ADVISORY);
                }
            }
        }
    }

    public static void syncState(Context context) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            boolean voiceMaster = prefs.getBoolean("voice_master_switch", true);
            boolean wheelMaster = prefs.getBoolean("wheel_master_switch", true);
            voiceMasterSwitch = voiceMaster;
            wheelMasterSwitch = wheelMaster;

            boolean doorFl = prefs.getBoolean("voice_enable_door_fl", true);
            boolean doorFlClose = prefs.getBoolean("voice_enable_door_fl_close", true);
            boolean doorFr = prefs.getBoolean("voice_enable_door_fr", true);
            boolean doorFrClose = prefs.getBoolean("voice_enable_door_fr_close", true);
            boolean doorRl = prefs.getBoolean("voice_enable_door_rl", true);
            boolean doorRlClose = prefs.getBoolean("voice_enable_door_rl_close", true);
            boolean doorRr = prefs.getBoolean("voice_enable_door_rr", true);
            boolean doorRrClose = prefs.getBoolean("voice_enable_door_rr_close", true);
            boolean doorRear = prefs.getBoolean("voice_enable_door_rear", true);
            boolean trunkOpen = prefs.getBoolean("voice_enable_trunk_open", true);
            boolean trunkClose = prefs.getBoolean("voice_enable_trunk_close", true);
            boolean gearD = prefs.getBoolean("voice_enable_gear_d", true);
            boolean gearR = prefs.getBoolean("voice_enable_gear_r", true);
            boolean gearP = prefs.getBoolean("voice_enable_gear_p", true);
            boolean gearN = prefs.getBoolean("voice_enable_gear_n", false);
            boolean modeSmart = prefs.getBoolean("voice_enable_mode_smart", true);
            boolean modeComfort = prefs.getBoolean("voice_enable_mode_comfort", true);
            boolean modeEco = prefs.getBoolean("voice_enable_mode_eco", true);
            boolean modeSport = prefs.getBoolean("voice_enable_mode_sport", true);
            boolean lightNav = prefs.getBoolean("vehicle_light_nav_enabled", false);
            boolean flameout = prefs.getBoolean("vehicle_flameout_voice_enabled", false);
            String wheelMode = prefs.getString("wheel_control_mode", SteeringWheelKeyManager.MODE_CARMEDIA_FIRST);
            boolean wheelEnabled = wheelMaster && !SteeringWheelKeyManager.MODE_FACTORY_DEFAULT.equals(wheelMode);
            boolean pushPlayback = prefs.getBoolean("wheel_push_playback_cluster", false);
            boolean pushLyrics = prefs.getBoolean("wheel_push_lyrics_cluster", false);
            try {
                if (wheelMaster && "toolbox_alone".equals(wheelMode)) {
                    EasMediaBridge.getInstance(context).syncConfig(wheelMode, pushPlayback, pushLyrics);
                } else {
                    EasMediaBridge.getInstance(context).releaseEasRegistration();
                }
            } catch (Throwable ignored) {}

            boolean guardSteer = prefs.getBoolean("voice_enable_steer_angle_guard", true);
            boolean guardEpb = prefs.getBoolean("voice_enable_epb_guard", true);
            boolean guardLowFuel = prefs.getBoolean("voice_enable_low_fuel_guard", true);
            boolean guardPowertrain = prefs.getBoolean("voice_enable_powertrain_guard", true);
            boolean anySafetyGuardEnabled = voiceMaster && (guardSteer || guardEpb || guardLowFuel || guardPowertrain);

            boolean anyVoiceEnabled = voiceMaster && (doorFl || doorFlClose || doorFr || doorFrClose ||
                                doorRl || doorRlClose || doorRr || doorRrClose || doorRear ||
                                trunkOpen || trunkClose || gearD || gearR || gearP || gearN ||
                                modeSmart || modeComfort || modeEco || modeSport ||
                                flameout || anySafetyGuardEnabled);
            boolean anyVehicleAutoEnabled = lightNav;

            boolean shouldRun = anyVoiceEnabled || anyVehicleAutoEnabled || wheelEnabled
                    || prefs.getBoolean(IdleScreensaverManager.KEY_ENABLED, false);

            Intent intent = new Intent(context, VehicleAutomationService.class);
            if (shouldRun) {
                SystemUtils.grantOverlayPermissionViaShell(context);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent);
                } else {
                    context.startService(intent);
                }
            } else {
                if (isRunning) {
                    context.stopService(intent);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to sync VehicleAutomationService state: " + e.getMessage());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        instance = this;
        voicePlayer = VehicleVoicePlayer.getInstance(this);
        wheelKeyManager = new SteeringWheelKeyManager(this);

        // 初始化四大独立状态机
        doorStateManager = new DoorStateManager(this, voicePlayer);
        doorStateManager.setListener((fl, fr, rl, rr) -> {
            if (fl == 1 && currentDoorFL == 0) {
                checkEpbAndSteerAngleOnDriverDoorOpen();
            }
            currentDoorFL = fl;
            currentDoorFR = fr;
            currentDoorRL = rl;
            currentDoorRR = rr;
        });

        trunkStateManager = new TrunkStateManager(this, voicePlayer);
        trunkStateManager.setListener(trunk -> {
            currentTrunk = trunk;
        });

        gearStateMachine = new GearStateMachine(this, voicePlayer);
        gearStateMachine.setListener(gear -> {
            lastGearPos = gear;
        });

        driveModeManager = new DriveModeManager(this, voicePlayer);
        driveModeManager.setListener(mode -> {
            lastDriveMode = mode;
        });

        createNotificationChannel();
        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        Notification notification = builder
                .setContentTitle("吉利控制台 · 智能座舱联动中")
                .setContentText("原生监听物理信号与方控分发，保障全车安全与便捷")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        startForeground(NOTIF_ID, notification);

        reloadPreferences();
        wheelKeyManager.syncMediaKeyReceiverState();

        // 闲置自动屏保：跟随本常驻服务全程运行（无需打开工具箱界面，开机自启即生效）
        try {
            IdleScreensaverManager.start(this);
        } catch (Throwable e) {
            Log.w(TAG, "Failed to start IdleScreensaverManager: " + e.getMessage());
        }

        registerPowerStateReceiver();
        startLogcatReader();
        registerEcarxKeyReceiver();
        startMediaMonitor();
        SystemUtils.warmDisabledPackagesCache();
        AppLogger.i("系统日志", "车辆启动自动运行守护服务已启动 -> 开启底层门控、挡位与方控全量监听");
        Log.i(TAG, "VehicleAutomationService started successfully");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        reloadPreferences();
        wheelKeyManager.syncMediaKeyReceiverState();
        // 每次服务被拉起/配置变更后，按最新配置重新装载闲置屏保计时器
        try {
            IdleScreensaverManager.restart(this);
        } catch (Throwable e) {
            Log.w(TAG, "Failed to restart IdleScreensaverManager: " + e.getMessage());
        }
        return START_STICKY;
    }

    private void reloadPreferences() {
        SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
        voiceMasterSwitch = prefs.getBoolean("voice_master_switch", true);
        wheelMasterSwitch = prefs.getBoolean("wheel_master_switch", true);

        enableDoorFl = prefs.getBoolean("voice_enable_door_fl", true);
        enableDoorFlClose = prefs.getBoolean("voice_enable_door_fl_close", true);
        enableDoorFr = prefs.getBoolean("voice_enable_door_fr", true);
        enableDoorFrClose = prefs.getBoolean("voice_enable_door_fr_close", true);
        enableDoorRl = prefs.getBoolean("voice_enable_door_rl", true);
        enableDoorRlClose = prefs.getBoolean("voice_enable_door_rl_close", true);
        enableDoorRr = prefs.getBoolean("voice_enable_door_rr", true);
        enableDoorRrClose = prefs.getBoolean("voice_enable_door_rr_close", true);
        enableDoorRear = prefs.getBoolean("voice_enable_door_rear", true);
        enableTrunkOpen = prefs.getBoolean("voice_enable_trunk_open", true);
        enableTrunkClose = prefs.getBoolean("voice_enable_trunk_close", true);
        enableGearD = prefs.getBoolean("voice_enable_gear_d", true);
        enableGearR = prefs.getBoolean("voice_enable_gear_r", true);
        enableGearP = prefs.getBoolean("voice_enable_gear_p", true);
        enableGearN = prefs.getBoolean("voice_enable_gear_n", false);
        enableGearS = prefs.getBoolean("voice_enable_gear_s", true);
        enableModeSmart = prefs.getBoolean("voice_enable_mode_smart", true);
        enableModeComfort = prefs.getBoolean("voice_enable_mode_comfort", true);
        enableModeEco = prefs.getBoolean("voice_enable_mode_eco", true);
        enableModeSport = prefs.getBoolean("voice_enable_mode_sport", true);
        enableLightNav = prefs.getBoolean("vehicle_light_nav_enabled", false);
        enableFlameoutVoice = prefs.getBoolean("vehicle_flameout_voice_enabled", false);

        // 车况安全守护四项测试开关 (同步进 shouldRun 门控)
        enableSafetyGuards = voiceMasterSwitch && (
                prefs.getBoolean("voice_enable_steer_angle_guard", true)
                        || prefs.getBoolean("voice_enable_epb_guard", true)
                        || prefs.getBoolean("voice_enable_low_fuel_guard", true)
                        || prefs.getBoolean("voice_enable_powertrain_guard", true));

        // 若服务在行车中启动，立即建立主驾已就坐基准，避免车门语音误判为上车
        if (lastPowerMode > 0 && doorStateManager != null) {
            doorStateManager.markDriverInside();
        }

        String wheelMode = prefs.getString("wheel_control_mode", SteeringWheelKeyManager.MODE_CARMEDIA_FIRST);
        boolean wheelEnabled = wheelMasterSwitch && !SteeringWheelKeyManager.MODE_FACTORY_DEFAULT.equals(wheelMode);

        boolean anyVoiceEnabled = voiceMasterSwitch && (enableDoorFl || enableDoorFlClose || enableDoorFr || enableDoorFrClose ||
                             enableDoorRl || enableDoorRlClose || enableDoorRr || enableDoorRrClose || enableDoorRear ||
                             enableTrunkOpen || enableTrunkClose || enableGearD || enableGearR || enableGearP || enableGearN || enableGearS ||
                             enableModeSmart || enableModeComfort || enableModeEco || enableModeSport ||
                             enableFlameoutVoice || enableSafetyGuards);
        boolean anyVehicleAutoEnabled = enableLightNav;

        boolean anyEnabled = anyVoiceEnabled || anyVehicleAutoEnabled || wheelEnabled
                || prefs.getBoolean(IdleScreensaverManager.KEY_ENABLED, false);

        if (!anyEnabled) {
            stopSelf();
        }
    }

    /**
     * 注册亿咖通原厂方控广播监听 (ECARX_KEY_*)，作为 logcat/HAL 双通道的容灾备份。
     * 这些广播由 CarInputService / 多媒体框架在解析 HW_KEY_INPUT 后发出，
     * 对于电话、静音、切歌等键更稳定可靠。
     */
    private void registerEcarxKeyReceiver() {
        if (ecarxKeyReceiver != null) return;
        ecarxKeyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                if (action == null) return;
                int actionType = intent.getIntExtra("ecarx.extra.ECARX_KEY_ACTION_TYPE", -1);
                if (actionType != 0) return; // 当前只处理短按；长按由 HAL/logcat 手势层处理

                int mappedKey = -1;
                switch (action) {
                    case "ecarx.intent.action.ECARX_KEY_MUTE_EVENT":
                        mappedKey = SteeringWheelKeyManager.KEY_MUTE;
                        break;
                    case "ecarx.intent.action.ECARX_KEY_BLELEFTMOVE_EVENT":
                        mappedKey = SteeringWheelKeyManager.KEY_PREV;
                        break;
                    case "ecarx.intent.action.ECARX_KEY_BLERIGHTMOVE_EVENT":
                        mappedKey = SteeringWheelKeyManager.KEY_NEXT;
                        break;
                    // ⚠️ 以下事件刻意「不映射」：
                    //  ECARX_KEY_RCALL_EVENT（电话键）、ECARX_KEY_RMULTIFUNCTION_EVENT、
                    //  ECARX_KEY_BLEPLAYPAUSE_EVENT（蓝牙遥控播放键）
                    // 它们此前被错误地塞成 KEY_OK，会误触发「编号2 滚轮下按」上配置的动作，
                    // 从而抢占原厂「高德飞屏到仪表盘」功能。现一律放行给原厂总线，工具箱零接触。
                }
                if (mappedKey > 0 && wheelKeyManager != null) {
                    AppLogger.i("方控按键", "ECARX广播触发: " + action + " -> key=" + mappedKey);
                    wheelKeyManager.handleKeyDown(mappedKey);
                    wheelKeyManager.handleKeyUp(mappedKey);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("ecarx.intent.action.ECARX_KEY_RCALL_EVENT");
        filter.addAction("ecarx.intent.action.ECARX_KEY_MUTE_EVENT");
        filter.addAction("ecarx.intent.action.ECARX_KEY_RMULTIFUNCTION_EVENT");
        filter.addAction("ecarx.intent.action.ECARX_KEY_BLELEFTMOVE_EVENT");
        filter.addAction("ecarx.intent.action.ECARX_KEY_BLEPLAYPAUSE_EVENT");
        filter.addAction("ecarx.intent.action.ECARX_KEY_BLERIGHTMOVE_EVENT");
        try {
            registerReceiver(ecarxKeyReceiver, filter);
            AppLogger.i("方控按键", "ECARX_KEY_* 广播监听器已注册");
        } catch (Exception e) {
            Log.w(TAG, "Failed to register ECARX key receiver: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "座舱后台常驻服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("用于常驻监听车身物理信号、方控与蓝牙音频路由");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void registerPowerStateReceiver() {
        if (powerReceiver != null) return;
        powerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                boolean realPowerOff = Intent.ACTION_SHUTDOWN.equals(action) ||
                        "android.intent.action.QUICKBOOT_POWEROFF".equals(action) ||
                        "com.ecarx.intent.action.ECARX_SHUTDOWN".equals(action);
                boolean screenOff = Intent.ACTION_SCREEN_OFF.equals(action);

                if (realPowerOff) {
                    lastPowerMode = 0; // 真正熄火下电：强制锁定为 0
                    if (gearStateMachine != null) gearStateMachine.resetState();
                    if (driveModeManager != null) driveModeManager.resetState();
                    if (doorStateManager != null) doorStateManager.resetState();
                    if (enableFlameoutVoice && voicePlayer != null) {
                        voicePlayer.play("flameout.mp3", "车辆已熄火，请带好随身物品");
                    }
                } else if (screenOff) {
                    // 息屏 ≠ 熄火：车机息屏待机时发动机可能仍在运行（发电机充电电压仍 ≥13.2V）。
                    // 此处坚决不碰 lastPowerMode，交由电压权威判定兜底，杜绝「息屏误判锁死后永久静音」。
                    Log.i(TAG, "Screen off detected (not a real power-off), keep power mode unchanged");
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SHUTDOWN);
        filter.addAction("android.intent.action.QUICKBOOT_POWEROFF");
        filter.addAction("com.ecarx.intent.action.ECARX_SHUTDOWN");
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        try {
            registerReceiver(powerReceiver, filter);
        } catch (Exception ignored) {}
    }

    // 正则表达式
    private static final Pattern CAN_PATTERN =
            Pattern.compile("key\\s*=\\s*([^,\\s]+).*?data\\s*=\\s*(-?\\d+)");
    private static final Pattern SERIAL_DOOR_PATTERN =
            Pattern.compile("91\\s+02\\s+01(?:\\s+[0-9a-fA-F]{1,2}){3}\\s+([0-9a-fA-F]{1,2})\\s+([0-9a-fA-F]{1,2})");

    private void startLogcatReader() {
        if (logcatThread != null && logcatThread.isAlive()) return;

        logcatThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRunning) {
                    try {
                        try {
                            AdbClient.execute(VehicleAutomationService.this, "pm grant " + getPackageName() + " android.permission.READ_LOGS");
                        } catch (Exception ignored) {}

                        // 核心日志通道: 
                        // 1. VehicleDataBuilder (CAN 信号)
                        // 2. SerialControl_v2_0 & VehicleEmulator_v2_0 (MCU 物理串口报文 91 02 01)
                        // 3. ECARX@ECP & MCULog (MCU 物理挡位 GearPosition 与门控 FL_DOOR_STS)
                        // 4. InputManager / CAR.INPUT (方向盘按键报文)
                        // 5. ecarx_core_server (mModelLFDoor 门控信号)
                        // 放宽串口与系统标签日志级别至 :V，彻底杜绝车门报文被丢弃
                        // 不在命令行中限制 -s 标签（避免因为底层模块标签变动或漏掉标签导致整包被丢弃）
                        // 直接全量监听，由我们在 parseLogLine 中进行高效关键字判定！
                        ProcessBuilder pb = new ProcessBuilder("logcat", "-T", "1", "-b", "all", "-v", "brief");
                        pb.redirectErrorStream(true);
                        logcatProcess = pb.start();
                        AppLogger.i("系统日志", "Logcat 实时监听守护线程已建立就绪");

                        BufferedReader reader = new BufferedReader(new InputStreamReader(logcatProcess.getInputStream()), 1024);
                        String line;
                        while (isRunning && (line = reader.readLine()) != null) {
                            parseLogLine(line);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Logcat stream interrupted: " + e.getMessage());
                    }

                    if (isRunning) {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ignored) {
                            break;
                        }
                    }
                }
            }
        }, "VehicleLogcatReader");
        logcatThread.start();
    }

    private void parseLogLine(String line) {
        if (line == null || line.isEmpty()) return;
        // 过滤本应用自身日志，避免 AppLogger 写 logcat 又被 logcat 守护线程读回形成风暴
        if (line.contains("GeelyToolbox_Logger") || line.contains("GeelyConsole")) return;

        // 1. 解析方向盘按键
        // parseKeyFromLine 内部已根据 press/release 调用 handleKeyDown/handleKeyUp，
        // 这里只负责识别到按键后提前结束本行解析，避免继续被其它正则误匹配。
        if (wheelKeyManager != null) {
            int wheelKey = wheelKeyManager.parseKeyFromLine(line);
            if (wheelKey > 0) {
                return;
            }
        }

        // 2. 解析车辆实时车速
        if (line.contains("Get Speed ") || line.contains("getVehicleSpeed") || line.contains("speed ==") || line.contains("speed=")) {
            try {
                if (line.contains("Get Speed ")) {
                    Matcher m = Pattern.compile("Get Speed\\s+(\\d+)km/h").matcher(line);
                    if (m.find()) {
                        currentSpeedKmH = Integer.parseInt(m.group(1));
                        processVehicleSpeedAutomation(currentSpeedKmH);
                        return;
                    }
                }
                int idx = line.indexOf("speed ==");
                if (idx == -1) idx = line.indexOf("speed=");
                if (idx != -1) {
                    String sub = line.substring(idx + (line.contains("speed ==") ? 8 : 6)).trim();
                    StringBuilder num = new StringBuilder();
                    for (int i = 0; i < sub.length(); i++) {
                        char c = sub.charAt(i);
                        if (Character.isDigit(c)) num.append(c);
                        else if (num.length() > 0) break;
                    }
                    if (num.length() > 0) {
                        currentSpeedKmH = Integer.parseInt(num.toString());
                        processVehicleSpeedAutomation(currentSpeedKmH);
                    }
                }
            } catch (Exception ignored) {}
            return;
        }

        // 3. 解析车辆挡位报文 (四大独立权威源融合)
        int gearVal = -1;

        // 3.1 吉利原厂 360 环视核心状态机 (AvmStateManger / onVehicleEventGear / state[GEAR_*]) 与 VehId=Vehicle_Gear
        if (line.contains("onVehicleEventGear") || line.contains("state[GEAR_") || line.contains("Vehicle_Gear")) {
            if (line.contains("GEAR_DRIVE") || line.contains("state[GEAR_DRIVE]")) gearVal = 2;
            else if (line.contains("GEAR_REVERSE") || line.contains("state[GEAR_REVERSE]")) gearVal = 4;
            else if (line.contains("GEAR_NEUTRAL") || line.contains("state[GEAR_NEUTRAL]")) gearVal = 3;
            else if (line.contains("GEAR_PARK") || line.contains("state[GEAR_PARK]")) gearVal = 5;
            else if (line.contains("GEAR_SPORT") || line.contains("state[GEAR_SPORT]")) gearVal = 6;
            else if (line.contains("VehId=Vehicle_Gear")) {
                try {
                    Matcher m = Pattern.compile("VehId=Vehicle_Gear\\s+value=(?:0x)?([0-9a-fA-F]+)").matcher(line);
                    if (m.find()) {
                        int rawHex = Integer.parseInt(m.group(1), 16);
                        // 吉利 E02 复合报文：高4位为驾驶模式 (如 0x1=舒适), 低4位为物理挡位 (0x2=D挡, 0x3=N挡, 0x4=R挡, 0x5=P挡)
                        int low = rawHex & 0x0F;
                        if (low == 0x05) gearVal = 5;
                        else if (low == 0x04) gearVal = 4;
                        else if (low == 0x03) gearVal = 3;
                        else if (low == 0x02) gearVal = 2;
                        else if (low == 0x06 || low == 0x07) gearVal = 6;
                        else gearVal = low;
                    }
                } catch (Exception ignored) {}
            }
        }

        // 3.15 吉利 VehicleManager 框架标准状态: getDrivingMode: gear=X mode=Y (Tasker实车权威验证源)
        if (gearVal <= 0 && line.contains("getDrivingMode") && line.contains("gear=")) {
            try {
                Matcher m = Pattern.compile("gear=(\\d+)").matcher(line);
                if (m.find()) {
                    long gNum = Long.parseLong(m.group(1));
                    long low = gNum & 0xFF;
                    if (low == 0x20 || gNum == 2097696L) gearVal = 2; // D挡 (0x200220)
                    else if (low == 0x40 || gNum == 2097728L) gearVal = 4; // R挡 (0x200240)
                    else if (low == 0x10 || gNum == 2097680L) gearVal = 3; // N挡 (0x200210)
                    else if (low == 0x30 || gNum == 2097712L) gearVal = 5; // P挡 (0x200230)
                }
            } catch (Exception ignored) {}
        }

        // 3.16 ecarx_core_server 电子手刹/驻车: mModelEPB = 0 -> P 挡 (Tasker 模式.prj 黄金源)
        if (gearVal <= 0 && line.contains("mModelEPB")) {
            if (line.contains("mModelEPB = 0") || line.contains("mModelEPB=0")) {
                gearVal = 5; // P 挡 (驻车挡)
            }
        }

        // 3.2 MCU 串口硬件报文: MCULog:GearPosition: x (2=D, 3=N, 4=R, 5=P)
        if (gearVal <= 0 && line.contains("GearPosition:")) {
            try {
                int idx = line.indexOf("GearPosition:");
                if (idx != -1) {
                    String sub = line.substring(idx + 13).trim();
                    gearVal = Integer.parseInt(sub.substring(0, 1));
                }
            } catch (Exception ignored) {}
        }

        // 3.3 AudioPolicyManager 硬件倒车静音标志 (isGearReverse 1 -> 确认挂入倒车挡)
        if (gearVal <= 0 && line.contains("isGearReverse 1")) {
            gearVal = 4; // R挡
        }

        // 3.4 原厂核心服务与底层传感器属性: INFO_ID_VDRIVEINFO_GEAR_POSITION / mModelGearPos
        if (gearVal <= 0 && (line.contains("VDRIVEINFO_GEAR_POSITION") || line.contains("mModelGearPos") || line.contains("GearPos =") || line.contains("GearPos:"))) {
            try {
                Matcher gm = Pattern.compile("(?:funValue\\((?:0x)?([0-9a-fA-F]+)\\)|(?:mModelGearPos|GearPos)\\s*[:=]\\s*(?:0x)?([0-9a-fA-F]+))").matcher(line);
                if (gm.find()) {
                    String valStr = gm.group(1) != null ? gm.group(1) : gm.group(2);
                    int raw = Integer.parseInt(valStr, 16);
                    int low = raw & 0xFF;
                    if (low == 0x30 || low == 0x05) gearVal = 5; // P
                    else if (low == 0x31 || low == 0x04) gearVal = 4; // R
                    else if (low == 0x32 || low == 0x03) gearVal = 3; // N
                    else if (low == 0x33 || low == 0x02) gearVal = 2; // D
                    else if (low >= 2 && low <= 7) gearVal = low;
                }
            } catch (Exception ignored) {}
        }

        if (gearVal > 0) {
            handleGearSignal(gearVal);
            return;
        }

        // 4.1 解析底层 MCU 串口车身报文: 91 02 01 ... b6(四门) 与 b7(尾门) (最权威物理硬件通道！)
        if (line.contains("91 02 01") || (line.contains("91") && line.contains("02 01")) || (line.contains("mcu->mpu") && line.contains("91"))) {
            try {
                Matcher sm = SERIAL_DOOR_PATTERN.matcher(line);
                if (sm.find()) {
                    int b6 = Integer.parseInt(sm.group(1).trim(), 16);
                    int b7 = Integer.parseInt(sm.group(2).trim(), 16);

                    // b6 解析四门:
                    // bit0-1: FL主驾 (00b=开, 01b=关)
                    // bit2-3: FR副驾 (00b=开, 01b=关)
                    // bit4-5: RL左后 (00b=开, 01b=关)
                    // bit6-7: RR右后 (00b=开, 01b=关)
                    int fl = ((b6 & 0x03) == 0x00) ? 1 : 0;
                    int fr = (((b6 >> 2) & 0x03) == 0x00) ? 1 : 0;
                    int rl = (((b6 >> 4) & 0x03) == 0x00) ? 1 : 0;
                    int rr = (((b6 >> 6) & 0x03) == 0x00) ? 1 : 0;

                    // 分发给四门独立状态机
                    if (doorStateManager != null) {
                        doorStateManager.updateDoors(fl, fr, rl, rr, voiceMasterSwitch, getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE));
                    }

                    // b7 解析尾门: bit0=0 开, bit0=1 关
                    int trunk = ((b7 & 0x01) == 0) ? 1 : 0;

                    // 分发给电动尾门独立状态机
                    if (trunkStateManager != null) {
                        trunkStateManager.updateTrunk(trunk, voiceMasterSwitch, getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE));
                    }
                    return;
                }
            } catch (Exception e) {
                AppLogger.w("车门状态", "解析串口车门报文异常: " + e.getMessage());
            }
        }

        // 4.2 解析原厂 MCU/CAN 独立单门状态容灾 (MCULog:FL_DOOR_STS Open / Close 等)
        if (line.contains("_DOOR_STS") && (line.contains("MCULog") || line.contains("ECARX@ECP"))) {
            boolean isOpen = line.contains("Open") || line.contains(":1") || line.contains("=1");
            int val = isOpen ? 1 : 0;
            SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);

            if (line.contains("TRUNK_DOOR_STS") || line.contains("TAILGATE_DOOR_STS") || line.contains("BACK_DOOR_STS")) {
                if (trunkStateManager != null) {
                    trunkStateManager.updateTrunk(val, voiceMasterSwitch, prefs);
                }
            } else if (doorStateManager != null) {
                int curFL = doorStateManager.getFL();
                int curFR = doorStateManager.getFR();
                int curRL = doorStateManager.getRL();
                int curRR = doorStateManager.getRR();

                if (line.contains("FL_DOOR_STS")) curFL = val;
                else if (line.contains("FR_DOOR_STS")) curFR = val;
                else if (line.contains("RL_DOOR_STS")) curRL = val;
                else if (line.contains("RR_DOOR_STS")) curRR = val;

                doorStateManager.updateDoors(curFL, curFR, curRL, curRR, voiceMasterSwitch, prefs);
            }
            return;
        }

        // 4.3 解析原厂 core_server 单门状态容灾 (vehicledata----callbacks---mModelLFDoor = 1/0)
        if (line.contains("vehicledata----callbacks---mModel") && line.contains("Door =")) {
            SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            if (doorStateManager != null) {
                int curFL = doorStateManager.getFL();
                int curFR = doorStateManager.getFR();
                int curRL = doorStateManager.getRL();
                int curRR = doorStateManager.getRR();

                if (line.contains("mModelLFDoor =")) curFL = (line.contains("= 1") || line.contains("=1")) ? 1 : 0;
                else if (line.contains("mModelRFDoor =")) curFR = (line.contains("= 1") || line.contains("=1")) ? 1 : 0;
                else if (line.contains("mModelLRDoor =")) curRL = (line.contains("= 1") || line.contains("=1")) ? 1 : 0;
                else if (line.contains("mModelRRDoor =")) curRR = (line.contains("= 1") || line.contains("=1")) ? 1 : 0;

                doorStateManager.updateDoors(curFL, curFR, curRL, curRR, voiceMasterSwitch, prefs);
            }
            return;
        }

        // 4.4 解析底盘主副驾座椅乘员与安全带状态 (INFO_ID_IPKWARN_PASS_SEAT_BELT / INFO_ID_IPKWARN_DRV_SEAT_BELT)
        if (line.contains("INFO_ID_IPKWARN_PASS_SEAT_BELT") || line.contains("0x00201300")) {
            if (line.contains("funValue(0x00201202)") || line.contains("funValue(2)")) {
                if (doorStateManager != null) doorStateManager.updatePassengerOccupancy(true);
            } else if (line.contains("funValue(0x00201201)") || line.contains("funValue(1)")) {
                if (doorStateManager != null) doorStateManager.updatePassengerOccupancy(false);
            }
        }
        if (line.contains("INFO_ID_IPKWARN_DRV_SEAT_BELT") || line.contains("0x00201200")) {
            if (line.contains("funValue(0x00201201)") || line.contains("funValue(1)")) {
                if (doorStateManager != null) doorStateManager.updateDriverBeltState(true);
            } else if (line.contains("funValue(0x00201202)") || line.contains("funValue(2)")) {
                if (doorStateManager != null) doorStateManager.updateDriverBeltState(false);
            }
        }

        // 4.5 解析方向盘物理转角 (INFO_ID_VSTEERWHEELINFO_ANGLE_VALUE)
        // 强匹配: 必须同时含属性名与 funValue；数值在低16位 (实车采样 0x0000002c=44°)；
        // 0 或超量程 (±540°) 一律 unknown 静默。
        if (line.contains("INFO_ID_VSTEERWHEELINFO_ANGLE_VALUE")) {
            try {
                Matcher m = Pattern.compile("funValue\\((?:0x)?([0-9a-fA-F]+)\\)").matcher(line);
                if (m.find()) {
                    int raw = (int) Long.parseLong(m.group(1), 16);
                    int low16 = raw & 0xFFFF;
                    // 低16位按有符号解释 (左负右正, 16位补码)
                    if (low16 > 32767) low16 -= 65536;
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (safetySensors.feedSteerAngle(raw, now)) {
                        steerAngleSignalAt = now;
                    } else {
                        steerAngleSignalAt = 0L; // unknown: 失去新鲜度资格
                    }
                }
            } catch (Exception ignored) {}
        }

        // 4.6 解析电子手刹 EPB 状态 (INFO_ID_VDRIVEINFO_EPB_STATE)
        // 强匹配 + 已知枚举 (0=释放 1=拉起, 实车采样)；其余值 unknown 静默。
        if (line.contains("INFO_ID_VDRIVEINFO_EPB_STATE")) {
            try {
                Matcher m = Pattern.compile("funValue\\((?:0x)?([0-9a-fA-F]+)\\)").matcher(line);
                if (m.find()) {
                    int raw = (int) Long.parseLong(m.group(1), 16);
                    int state = raw & 0xFFFF;
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (state == SafetySensorStateMachine.EPB_RELEASED
                            || state == SafetySensorStateMachine.EPB_ENGAGED) {
                        safetySensors.feedEpbState(state, now);
                        epbSignalAt = now;
                    } else {
                        // 未知枚举: 证据不足安全静默，标记信号不新鲜
                        epbSignalAt = 0L;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 4.7 解析剩余里程与低油量关怀 (INFO_ID_IPKINFO_REMAIN_ODO)
        // 强匹配: 0 = 无效读数 (刚通电/仪表未就绪) 绝不告警；0 < km <= 50 每点火一次。
        if (line.contains("INFO_ID_IPKINFO_REMAIN_ODO")) {
            try {
                Matcher m = Pattern.compile("(?:funValue|funcValue)\\((?:0x)?([0-9a-fA-F]+)\\)").matcher(line);
                if (m.find()) {
                    int raw = (int) Long.parseLong(m.group(1), 16);
                    int odo = raw & 0xFFFF; // 低16位为公里数 (实车采样 0x10b=267km)
                    long now = android.os.SystemClock.elapsedRealtime();
                    safetySensors.feedRemainOdo(odo, now);
                    if (odo > 0) remainOdoSignalAt = now;
                    if (odo > 0 && isEngineRunning()) {
                        SharedPreferences p = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        if (p.getBoolean("voice_enable_low_fuel_guard", true)) {
                            int r = safetySensors.checkLowFuel(now);
                            if (r == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED) {
                                AppLogger.w("安全守护", "【P3关怀】剩余续航不足 50km (当前=" + odo + "km)，单次低油量温馨播报 (本点火行程不再重复)");
                                if (voicePlayer != null) {
                                    voicePlayer.play("low_fuel.mp3", "燃油即将耗尽，请及时加油", VehicleVoicePlayer.PRIORITY_P3_ADVISORY);
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // 4.8 解析动力总成高危 (INFO_ID_IPKWARN_TCU_OVER_TEMP_LEVEL / INFO_ID_IPKWARN_OIL_LOW_PRESSURE)
        // 铁律: funValue 为 funId基址|数值 打包。机油正常报文 funValue(0x00300000) 低16位=0 ——
        // raw > 0 绝不能当警报！必须提取低16位，仅数值非0且持续去抖后才确认一次。
        if (line.contains("INFO_ID_IPKWARN_TCU_OVER_TEMP_LEVEL")) {
            try {
                Matcher m = Pattern.compile("funValue\\((?:0x)?([0-9a-fA-F]+)\\)").matcher(line);
                if (m.find()) {
                    int raw = (int) Long.parseLong(m.group(1), 16);
                    int level = raw & 0xFFFF; // 提取有效等级数值
                    long now = android.os.SystemClock.elapsedRealtime();
                    safetySensors.feedTcuTempLevel(level, now);
                    if (level >= 0) tcuTempSignalAt = now;
                    if (isEngineRunning()) {
                        SharedPreferences p = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        if (p.getBoolean("voice_enable_powertrain_guard", true)) {
                            int r = safetySensors.checkTcuOverTemp(now);
                            if (r == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED) {
                                AppLogger.e("安全守护", "【P0报警】变速箱油温过高告警确认 (Level=" + level + "，去抖1200ms通过，本行程仅一次)");
                                if (voicePlayer != null) {
                                    voicePlayer.play("tcu_alarm.mp3", "警告，变速箱油温过高，请靠边停车怠速散热", VehicleVoicePlayer.PRIORITY_P0_ALARM);
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        if (line.contains("INFO_ID_IPKWARN_OIL_LOW_PRESSURE")) {
            try {
                Matcher m = Pattern.compile("funValue\\((?:0x)?([0-9a-fA-F]+)\\)").matcher(line);
                if (m.find()) {
                    int raw = (int) Long.parseLong(m.group(1), 16);
                    int level = raw & 0xFFFF; // 正常 0x00300000 → 0 = 正常，绝非警报!
                    long now = android.os.SystemClock.elapsedRealtime();
                    safetySensors.feedOilPressure(level, now);
                    if (level >= 0) oilPressureSignalAt = now;
                    if (isEngineRunning()) {
                        SharedPreferences p = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        if (p.getBoolean("voice_enable_powertrain_guard", true)) {
                            int r = safetySensors.checkOilLowPressure(now);
                            if (r == SafetySensorStateMachine.RESULT_ALARM_CONFIRMED) {
                                AppLogger.e("安全守护", "【P0报警】机油压力过低告警确认 (值=" + level + "，去抖1200ms通过，本行程仅一次)");
                                if (voicePlayer != null) {
                                    voicePlayer.play("oil_alarm.mp3", "警告，机油压力过低，请检查发动机", VehicleVoicePlayer.PRIORITY_P0_ALARM);
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // 4.1 解析驾驶模式切换信号 (严格收敛对齐 Tasker 实车验证黄金法则: 纯净收敛于 ECarXCarConfigService 与 AdaptAPI 权威常量)
        int modeVal = -1;

        // 优先 1: ECarXCarConfigService 官方系统级权威上报 (DirveMode = X / DriveMode = X)
        // 缤越 COOL Tasker 验证黄金源：旋钮切挡与系统配置全局同步，彻底杜绝杂波
        // 1 -> 舒适模式 (MODE_COMFORT)
        // 2 -> 运动模式 (MODE_SPORT)
        // 3 -> 经济模式 (MODE_ECO)
        // 4 / 6 -> 智能模式 (MODE_SMART)
        if (line.contains("DirveMode =") || line.contains("DirveMode=") || line.contains("DriveMode =") || line.contains("DriveMode=")) {
            try {
                Matcher m = Pattern.compile("(?:DirveMode|DriveMode)\\s*=\\s*(\\d+)").matcher(line);
                if (m.find()) {
                    int dm = Integer.parseInt(m.group(1));
                    if (dm == 1) modeVal = MODE_COMFORT;
                    else if (dm == 2) modeVal = MODE_SPORT;
                    else if (dm == 3) modeVal = MODE_ECO;
                    else if (dm == 4 || dm == 6) modeVal = MODE_SMART;
                }
            } catch (Exception ignored) {}
        }
        // 优先 2: AdaptAPI 9位全局权威常量容灾 (必须携带 DM_FUNC_DRIVE_MODE_SELECT 或 mModelDriveMode 上下文，严禁裸数字误配)
        else if (line.contains("DM_FUNC_DRIVE_MODE_SELECT") || line.contains("mModelDriveMode")) {
            if (line.contains("570491138")) {
                modeVal = MODE_COMFORT; // 舒适模式 (DRIVE_MODE_SELECTION_COMFORT = 570491138)
            } else if (line.contains("570491139")) {
                modeVal = MODE_SPORT;   // 运动模式 (DRIVE_MODE_SELECTION_DYNAMIC = 570491139)
            } else if (line.contains("570491137")) {
                modeVal = MODE_ECO;     // 经济模式 (DRIVE_MODE_SELECTION_ECO = 570491137)
            } else if (line.contains("570491158")) {
                modeVal = MODE_SMART;   // 智能模式 (DRIVE_MODE_SELECTION_ADAPTIVE = 570491158)
            }
        }

        if (modeVal > 0) {
            handleDriveModeSignal(modeVal);
            return;
        }

        // 4.9 解析点火/电源状态 (PEPS_PowerMode / KEY_STATE / ENGINE_STATE / AP_POWER_BOOTUP_REASON)
        int keyVal = parsePowerStateLine(line);
        if (keyVal >= 0) {
            handlePowerState(keyVal, line);
            return;
        }

        // 5. 解析 CAN 数据: parseCanData
        if (line.contains("parseCanData")) {
            try {
                Matcher m = CAN_PATTERN.matcher(line);
                if (m.find()) {
                    String key = m.group(1).trim();
                    int val = Integer.parseInt(m.group(2).trim());
                    handleCanSignal(key, val);
                }
            } catch (Exception ignored) {}
        }

        // 6. 解析 12V 蓄电池物理电压报文 (ecarx_core_server: vehicledata----callbacks---mModelBatteryVolt = 125)
        if (line.contains("mModelBatteryVolt =")) {
            try {
                int idx = line.indexOf("mModelBatteryVolt =");
                if (idx != -1) {
                    String sub = line.substring(idx + 19).trim();
                    StringBuilder num = new StringBuilder();
                    for (int i = 0; i < sub.length(); i++) {
                        char c = sub.charAt(i);
                        if (Character.isDigit(c)) num.append(c);
                        else if (num.length() > 0) break;
                    }
                    if (num.length() > 0) {
                        int rawVolt = Integer.parseInt(num.toString());
                        // 12V 蓄电池车规真实数值判定 (有效区间 9.0V ~ 16.5V):
                        // ecarx_core_server 原始为十进制 10 倍 (如 124 -> 12.4V, 136 -> 13.6V)
                        // 若为毫伏级 (如 12400mV -> 12.4V)
                        float volt = -1.0f;
                        if (rawVolt >= 90 && rawVolt <= 165) {
                            volt = rawVolt / 10.0f;
                        } else if (rawVolt >= 900 && rawVolt <= 1650) {
                            volt = rawVolt / 100.0f;
                        } else if (rawVolt >= 9000 && rawVolt <= 16500) {
                            volt = rawVolt / 1000.0f;
                        }

                        // 只有在 9.0V ~ 16.5V 车规安全范围内才更新内存（纯内存流转，零高频磁盘擦写）
                        if (volt >= 9.0f && volt <= 16.5f) {
                            latestBatteryVoltage = volt;
                            if (Math.abs(volt - lastSavedBatteryVoltage) >= 0.2f) {
                                lastSavedBatteryVoltage = volt;
                                getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE)
                                        .edit().putFloat("vehicle_real_battery_volt", volt).apply();
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * 判定整车是否处于真正点火启动/行车就绪状态
     * 熄火下电或蓝牙唤醒浅待机时，发电机未转动，TCU处于休眠或诊断回环，坚决静默不发声
     */
    public boolean isEngineRunning() {
        // 1. 明确检测到熄火下电（SHUTDOWN / QUICKBOOT_POWEROFF / ECARX_SHUTDOWN 广播锁定，或 PEPS/KEY=0）→ 绝对静音
        if (lastPowerMode == 0) return false;
        // 2. 车速非零：车辆行驶移动中，100% 确认运行
        if (currentSpeedKmH > 0) return true;
        // 3. 发电机高压充电权威信号：若电压稳稳 ≥13.2V，发电机必然在转，100% 确认运行
        if (latestBatteryVoltage >= 13.2f) return true;
        // 4. 明确检测到电源点火处于就绪状态 (lastPowerMode == 1 或钥匙 ON 信号)
        if (lastPowerMode == 1 || lastKeyState == 2) return true;
        // 5. 其余静止无充电状态（蓄电池自然静置电压 9.0V~13.0V 且零车速）：判定为熄火未启动状态，绝对静默！
        if (latestBatteryVoltage >= 9.0f && latestBatteryVoltage < 13.0f && currentSpeedKmH == 0) {
            return false;
        }
        return true;
    }

    private void handleGearSignal(final int gear) {
        if (!isEngineRunning()) {
            // 熄火断电/浅待机：TCU 信号处于心跳诊断期，强制重置状态机，绝对静音！
            if (gearStateMachine != null) {
                gearStateMachine.resetState();
            }
            return;
        }
        if (gearStateMachine != null) {
            gearStateMachine.updateGear(gear, voiceMasterSwitch, getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE));
        }

        // D挡起步联动 360 严格单次跃变状态机：切入D挡仅触发1次，锁死不循环调起；切出D挡重新武装
        if (gear == 2) {
            if (gearD360Armed) {
                gearD360Armed = false; // 触发后立即闭锁，车主手动退出360绝不反复调起！
                boolean gearD360 = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE)
                        .getBoolean("vehicle_gear_d_360_enabled", false);
                if (gearD360 && currentSpeedKmH <= 30) {
                    AppLogger.i("车身联动", "【D挡起步联动360】挂入前进挡 D，秒级唤起 360 全景盲区影像 (单次跃变闭环)");
                    open360Camera();
                }
            }
        } else {
            // 只要不是 D 挡 (P/R/N)，重新恢复武装状态，等待下一次起步切入 D 挡
            gearD360Armed = true;
        }
    }

    /**
     * 判定整车当前是否处于允许切换驾驶模式的工况
     * 熄火下电、钥匙关/ACC(熄火只开车机听歌)、驻车未启动等状态下，物理旋钮被吉利底层锁定，任何日志均为假信号，绝对静默！
     */
    private boolean isDrivingModeAllowed() {
        if (lastPowerMode == 0) return false;
        if (lastKeyState == 0 || lastKeyState == 1) return false; // 0=关, 1=ACC (熄火只开车机听歌)
        if (lastEngineState == 0 && currentSpeedKmH == 0) return false; // 发动机明确停止且零车速
        if (currentSpeedKmH > 0) return true; // 行驶中，必然处于运转工况
        if (latestBatteryVoltage >= 13.0f) return true; // 发电机发电中，发动机必然运转中
        if (lastEngineState == 3) return true; // 发动机明确处于运行状态
        // 蓄电池自然静置电压 (9.0V~12.9V) 且零车速：属于熄火未点火驻车状态，旋钮无法切换模式
        if (latestBatteryVoltage >= 9.0f && latestBatteryVoltage < 12.9f && currentSpeedKmH == 0) {
            return false;
        }
        return true;
    }

    /**
     * 处理驾驶模式切换状态 (智能有人感知状态机：默认智能模式静默，手动切出智能模式后激活全量播报，切回智能模式播报后置0归位)
     * 模式枚举: MODE_COMFORT=1, MODE_SPORT=2, MODE_ECO=3, MODE_SMART=4 (全局解耦，零错位)
     */
    private void handleDriveModeSignal(int mode) {
        if (!isDrivingModeAllowed()) {
            // 明确熄火/未启动/ACC状态：强制重置状态机，绝对静音
            if (driveModeManager != null) {
                driveModeManager.resetState();
            }
            return;
        }
        if (driveModeManager != null) {
            driveModeManager.updateDriveMode(mode, voiceMasterSwitch, getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE));
        }
    }

    private void handleCanSignal(String key, int val) {
        // 四门状态监听 (Tasker 核心源: BCM_*DoorAjarStatus, data=1 开 / 0 关)
        if ("BCM_FrontLeftDoorAjarStatus".equals(key)) {
            if (doorStateManager != null) {
                doorStateManager.updateDoors(val, -1, -1, -1, voiceMasterSwitch, getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE));
            }
            // 关 ➔ 开 跃变才触发一次；门持续开着期间一律静默 (CAN 心跳复读不再刷屏、不再重复发暂停)
            if (val == 1) {
                if (!frontLeftDoorOpenLatched) {
                    frontLeftDoorOpenLatched = true;
                    checkFrontDoorPauseMusic();
                }
            } else if (val == 0) {
                frontLeftDoorOpenLatched = false;
            }
        } else if ("BCM_FrontRightDoorAjarStatus".equals(key)) {
            if (doorStateManager != null) {
                doorStateManager.updateDoors(-1, val, -1, -1, voiceMasterSwitch, getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE));
            }
            if (val == 1) {
                if (!frontRightDoorOpenLatched) {
                    frontRightDoorOpenLatched = true;
                    checkFrontDoorPauseMusic();
                }
            } else if (val == 0) {
                frontRightDoorOpenLatched = false;
            }
        } else if ("BCM_RearLeftDoorAjarStatus".equals(key)) {
            if (doorStateManager != null) {
                doorStateManager.updateDoors(-1, -1, val, -1, voiceMasterSwitch, getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE));
            }
        } else if ("BCM_RearRightDoorAjarStatus".equals(key)) {
            if (doorStateManager != null) {
                doorStateManager.updateDoors(-1, -1, -1, val, voiceMasterSwitch, getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE));
            }
        }

        // 大灯联动高德日夜模式与背光微调
        if ("BCM_PositionLightSts".equals(key)) {
            if (enableLightNav) {
                if (lastLightSts == -1) {
                    lastLightSts = val;
                } else if (val == 1 && lastLightSts == 0) {
                    sendAmapDayNightMode(2); // 黑夜模式
                    lastLightSts = 1;
                } else if (val == 0 && lastLightSts == 1) {
                    sendAmapDayNightMode(0); // 日间模式
                    lastLightSts = 0;
                }
            }
            // 背光微调必须同样走「跃变」判定：BCM_PositionLightSts 会被底层反复上报，若无跃变
            // 闩锁，每次信号都要 spawn 一次 ADB shell 改亮度并打日志（实车即每秒一次 shell 调用）。
            if (val != lastLightStsForDim) {
                lastLightStsForDim = val;
                SharedPreferences p = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                if (p.getBoolean("vehicle_light_brightness_dim_enabled", false)) {
                    adjustScreenBrightness(val == 1);
                }
            }
        }

        // 电源模式/熄火检测
        if ("PEPS_PowerMode".equals(key)) {
            if (val == 0) {
                // 车辆熄火/下电 -> 状态机强制归零，杜绝下次点火误报
                if (gearStateMachine != null) gearStateMachine.resetState();
                if (driveModeManager != null) driveModeManager.resetState();
                if (doorStateManager != null) doorStateManager.resetState();
                if (enableFlameoutVoice && lastPowerMode > 0 && voicePlayer != null) {
                    voicePlayer.play("flameout.mp3", "车辆已熄火，请带好随身物品");
                }
            }
            lastPowerMode = val;
        }
    }

    /**
     * 从 logcat 行解析电源/点火状态。
     * 返回：-1 = 未匹配；0 = 关/熄火；1 = ACC；2 = ON；3 = 引擎运行；
     *      100 + bootReason = AP_POWER_BOOTUP_REASON。
     */
    private int parsePowerStateLine(String line) {
        if (line == null) return -1;
        String l = line.toLowerCase();
        try {
            if (l.contains("onaccoff") || l.contains("on_acc_off") || (l.contains("fuelconsumptionservice") && l.contains("accoff"))) {
                return 0; // Tasker实车验证: FuelConsumptionServiceImpl onAccOff -> 车辆熄火下电
            }
            if (l.contains("peps_powermode")) {
                Matcher m = Pattern.compile("peps_powermode[^0-9]*(\\d+)").matcher(l);
                if (m.find()) return Integer.parseInt(m.group(1));
            }
            if (l.contains("info_id_vpowerinfo_key_state") || l.contains("key_state")) {
                Matcher m = Pattern.compile("(?:funvalue|info_id_vpowerinfo_key_state|key_state)[^0-9a-f]*(?:0x)?([0-9a-f]+)").matcher(l);
                if (m.find()) {
                    int raw = Integer.parseInt(m.group(1), 16);
                    int low = raw & 0xFF;
                    if (low == 0x05 || low == 2) return 2; // ON 点火就绪
                    if (low == 0x02 || low == 1) return 1; // ACC 通电
                    if (low == 0) return 0; // 熄火下电
                    return low;
                }
            }
            if (l.contains("info_id_vpowerinfo_engine_state") || l.contains("engine_state")) {
                Matcher m = Pattern.compile("(?:info_id_vpowerinfo_engine_state|engine_state)[^0-9]*(\\d+)").matcher(l);
                if (m.find()) return Integer.parseInt(m.group(1));
            }
            if (l.contains("ap_power_bootup_reason")) {
                Matcher m = Pattern.compile("ap_power_bootup_reason[^0-9]*(\\d+)").matcher(l);
                if (m.find()) return 100 + Integer.parseInt(m.group(1));
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private void handlePowerState(int val, String rawLine) {
        if (val >= 100) {
            int bootReason = val - 100;
            AppLogger.i("电源状态", "AP 冷启动原因=" + bootReason + " | " + rawLine.trim());
            if (bootReason == 0 || bootReason == 1) {
                // 用户上电或解锁 -> 默认认为主驾将登车，座椅状态机准备
                doorStateManager.markDriverMayEnter();
                // QQ 音乐解锁即预载同款：解锁/上电瞬间提前预热 TTS，点火后首条语音秒出
                if (voicePlayer != null) voicePlayer.ensureTtsReady();
                // 铁律：蓝牙靠近唤醒/解锁绝不等于发动机点火，绝对严禁标记 lastPowerMode = 1，防止车外空响！
            }
            return;
        }

        // KEY_STATE: 0=关 1=ACC 2=ON (原厂funValue=0x00200105末尾0x05解析为2，表示点火就绪)
        if (rawLine.toLowerCase().contains("info_id_vpowerinfo_key_state") || (rawLine.toLowerCase().contains("key_state") && (rawLine.toLowerCase().contains("power") || rawLine.toLowerCase().contains("peps")))) {
            lastKeyState = val;
            if (val == 2) {
                lastPowerMode = 1; // 钥匙 ON -> 点火就绪
                doorStateManager.markDriverInside();
                // 预设门控: KEY ON 只是就绪，不喂发动机边沿 (KEY ON/ACC 绝不算点火运行)
                AppLogger.i("电源状态", "钥匙 ON (key=2) -> 点火启动就绪");
            } else if (val == 0) {
                // 若发电机正在以 >=13.2V 充电，或车速非零，绝不可因偶发性按键释放日志误置熄火
                if (latestBatteryVoltage < 13.2f && currentSpeedKmH == 0) {
                    lastPowerMode = 0;
                } else {
                    AppLogger.i("电源状态", "忽略疑似按键释放噪音 key=0 (当前电压=" + latestBatteryVoltage + "V)");
                }
            }
        }
        // ENGINE_STATE: 0=停止 1=启动中 2=停止中 3=运行
        if (rawLine.toLowerCase().contains("engine_state") || rawLine.toLowerCase().contains("info_id_vpowerinfo_engine_state")) {
            lastEngineState = val;
            if (val == 3) {
                lastPowerMode = 1;
                doorStateManager.markDriverInside();
            } else if (val == 0) {
                // 引擎停止不一定熄火，等待 PEPS_PowerMode=0 或 KEY_STATE=0
            }
        }
        // PEPS_PowerMode
        if (rawLine.toLowerCase().contains("peps_powermode")) {
            lastPowerMode = val;
            if (val > 0) {
                doorStateManager.markDriverInside();
            } else {
                resetAllStateMachines(true);
            }
        }

        // 仅在「电源聚合状态真正发生变化」时写一条，彻底杜绝熄火后 MCU 心跳每秒刷同一条
        String aggregate = "key=" + lastKeyState + " engine=" + lastEngineState + " powerMode=" + lastPowerMode;
        if (!aggregate.equals(lastPowerStateAggregate)) {
            lastPowerStateAggregate = aggregate;
            AppLogger.i("电源状态", aggregate);
        }
    }

    private void resetAllStateMachines(boolean flameout) {
        if (gearStateMachine != null) gearStateMachine.resetState();
        if (driveModeManager != null) driveModeManager.resetState();
        if (doorStateManager != null) doorStateManager.resetState();
        // 熄火下电: 安全传感器一次触发锁复位
        if (safetySensors != null) {
            safetySensors.powerOff(android.os.SystemClock.elapsedRealtime());
        }
        if (flameout && enableFlameoutVoice && voicePlayer != null) {
            voicePlayer.play("flameout.mp3", "车辆已熄火，请带好随身物品", VehicleVoicePlayer.PRIORITY_P3_ADVISORY);
        }
    }

    private void open360Camera() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("ecarx.camera.calibration");
            if (intent == null) {
                intent = new Intent(Intent.ACTION_MAIN);
                intent.setComponent(new ComponentName("ecarx.camera.calibration", "ecarx.camera.calibration.MainActivity"));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
            AppLogger.i("车身联动", "已成功下发指令唤起 360 全景环视界面");
        } catch (Exception e) {
            Log.w(TAG, "Failed to launch 360: " + e.getMessage());
            AppLogger.w("车身联动", "唤起 360 失败: " + e.getMessage());
        }
    }

    private void close360Camera() {
        try {
            Intent closeIntent = new Intent("ecarx.intent.broadcast.action.ECARX_VR_APP_CLOSE");
            closeIntent.setData(Uri.parse("ecarx://vr.com/360全景"));
            closeIntent.setPackage("ecarx.camera.calibration");
            sendBroadcast(closeIntent);
            AppLogger.i("车身联动", "已下发指令退出 360 全景界面");
        } catch (Exception e) {
            Log.w(TAG, "Failed to close 360: " + e.getMessage());
            AppLogger.w("车身联动", "退出 360 失败: " + e.getMessage());
        }
    }

    private void sendAmapDayNightMode(int mode) {
        try {
            Intent intent = new Intent("AUTONAVI_STANDARD_BROADCAST_RECV");
            intent.putExtra("KEY_TYPE", 10048);
            intent.putExtra("EXTRA_DAY_NIGHT_MODE", mode);
            intent.setComponent(new ComponentName("com.autonavi.amapauto", "com.autonavi.amapauto.adapter.internal.AmapAutoBroadcastReceiver"));
            sendBroadcast(intent);
        } catch (Exception e) {
            Log.w(TAG, "Failed to send Amap broadcast: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        instance = null;
        if (powerReceiver != null) {
            try {
                unregisterReceiver(powerReceiver);
            } catch (Exception ignored) {}
            powerReceiver = null;
        }
        if (logcatProcess != null) {
            try {
                logcatProcess.destroy();
            } catch (Exception ignored) {}
            logcatProcess = null;
        }
        if (logcatThread != null) {
            logcatThread.interrupt();
            logcatThread = null;
        }
        if (ecarxKeyReceiver != null) {
            try {
                unregisterReceiver(ecarxKeyReceiver);
            } catch (Exception ignored) {}
            ecarxKeyReceiver = null;
        }
        try {
            IdleScreensaverManager.stop();
        } catch (Throwable ignored) {}
        stopMediaMonitor();
        try {
            EasMediaBridge.getInstance(this).releaseEasRegistration();
        } catch (Throwable ignored) {}
        Log.i(TAG, "VehicleAutomationService stopped");
    }

    private final Runnable mediaMonitorRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                pollActiveMediaSession();
            } catch (Throwable ignored) {}
            if (isRunning) {
                mainHandler.postDelayed(this, 3000);
            }
        }
    };

    private void startMediaMonitor() {
        mainHandler.removeCallbacks(mediaMonitorRunnable);
        mainHandler.postDelayed(mediaMonitorRunnable, 3000);
    }

    private void stopMediaMonitor() {
        mainHandler.removeCallbacks(mediaMonitorRunnable);
    }

    private void pollActiveMediaSession() {
        SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
        boolean pushPb = prefs.getBoolean("wheel_push_playback_cluster", false);
        if (!pushPb) return;
        String wheelMode = prefs.getString("wheel_control_mode", SteeringWheelKeyManager.MODE_CARMEDIA_FIRST);
        if (!"toolbox_alone".equals(wheelMode)) return;

        MediaSessionManager msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (msm == null) return;
        List<MediaController> controllers = null;
        try {
            controllers = msm.getActiveSessions(null);
        } catch (Throwable ignored) {}
        if (controllers == null || controllers.isEmpty()) return;

        for (MediaController mc : controllers) {
            if (mc == null) continue;
            PlaybackState state = mc.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                MediaMetadata md = mc.getMetadata();
                if (md != null) {
                    String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
                    String artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
                    String album = md.getString(MediaMetadata.METADATA_KEY_ALBUM);
                    long duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
                    EasMediaBridge.getInstance(this).pushPlaybackInfoIfEnabled(title, artist, album, duration, 1);
                    return;
                }
            }
        }
    }

    // ==========================================
    // 车速与门控自动化中枢 (单次行程防抖闭环)
    // ==========================================
    private boolean speedAutoplayArmed = true; // 车速自启武装锁：初始已武装，车速回落近停后重新武装
    private boolean speedCustomActionArmed = true; // 车速自定义联动武装锁
    private boolean gearD360Armed = true; // D挡起步360单次跃变武装锁 (离开D挡才复位，彻底根治手动退出后循环调起)
    // 前门开启「单次跃变」闩锁：门开着期间 CAN 报文会持续高频重复上报 data=1，
    // 必须只在 关 ➔ 开 物理跃变的那一刻执行一次门控暂停，杜绝反复下发暂停指令与日志刷屏。
    private volatile boolean frontLeftDoorOpenLatched = false;
    private volatile boolean frontRightDoorOpenLatched = false;

    private void processVehicleSpeedAutomation(int speed) {
        SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);

        // 车速回落接近停车 (<=5km/h) 才重新武装：彻底根治「挡位跃变驱动武装」导致 beta 失效 ——
        // 原实现把武装锁挂在挡位跃变上，一旦日志丢失挡位报文或长时间驻车，武装锁永远不解锁，
        // 车速自启与自定义动作整段失效。改为以车速自身回落为权威信号，无需依赖挡位日志。
        if (speed <= 5) {
            speedAutoplayArmed = true;
            speedCustomActionArmed = true;
        }

        // 1. 车速智能自启多媒体
        boolean autoplayEnabled = prefs.getBoolean("vehicle_speed_autoplay_enabled", false);
        if (autoplayEnabled && speedAutoplayArmed && isEngineRunning()) {
            int threshold = prefs.getInt("vehicle_speed_autoplay_threshold", 20);
            if (speed >= threshold) {
                speedAutoplayArmed = false; // 触发一次即锁定，等红绿灯不重复触发
                String targetPkg = prefs.getString("vehicle_speed_autoplay_pkg", "com.luna.music");
                boolean fullscreen = prefs.getBoolean("vehicle_speed_autoplay_fullscreen", false);
                triggerMusicAutoplay(targetPkg, fullscreen);
            }
        }

        // 2. 车速达标自定义动作与唤起应用 (满足车主任意设定车速与打开指定软件/360)
        boolean customActionEnabled = prefs.getBoolean("vehicle_speed_custom_action_enabled", false);
        if (customActionEnabled && speedCustomActionArmed && isEngineRunning()) {
            int customThreshold = prefs.getInt("vehicle_speed_custom_action_threshold", 40);
            if (speed >= customThreshold) {
                speedCustomActionArmed = false; // 触发一次即锁定，等红绿灯不重复弹，单次行程防抖
                String actionTarget = prefs.getString("vehicle_speed_custom_action_target", "action_360");
                triggerCustomSpeedAction(actionTarget);
            }
        }
    }

    private void triggerCustomSpeedAction(String target) {
        if (target == null || target.isEmpty()) return;
        AppLogger.i("车身联动", "车速达到自定义阈值，触发自定义联动动作: " + target);
        if ("action_360".equals(target)) {
            open360Camera();
        } else {
            String pkg = target.startsWith("pkg:") ? target.substring(4) : target;
            try {
                android.content.Intent launchIntent = getPackageManager().getLaunchIntentForPackage(pkg);
                if (launchIntent != null) {
                    launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launchIntent);
                }
            } catch (Exception ignored) {}
        }
    }

    private void triggerMusicAutoplay(final String pkg, boolean fullscreen) {
        if (isAnyMediaPlaying()) {
            AppLogger.i("车身联动", "车速达到阈值，但检测到当前已有媒体在播放，静默放行防打断");
            return;
        }
        AppLogger.i("车身联动", "车速达到阈值，触发智能多媒体自启: " + pkg + " (全屏=" + fullscreen + ")");
        if (fullscreen && pkg != null && !pkg.isEmpty()) {
            try {
                Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launch);
                }
            } catch (Throwable t) {
                AppLogger.w("车身联动", "唤起前台媒体应用失败: " + t.getMessage());
            }
        }
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    new SteeringWheelKeyManager(VehicleAutomationService.this).sendMediaKeyEventPublic(KeyEvent.KEYCODE_MEDIA_PLAY);
                } catch (Throwable ignored) {}
            }
        }, 800);
    }

    private boolean isAnyMediaPlaying() {
        try {
            MediaSessionManager msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm != null) {
                List<MediaController> controllers = msm.getActiveSessions(null);
                if (controllers != null) {
                    for (MediaController mc : controllers) {
                        if (mc != null && mc.getPlaybackState() != null && mc.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {
                            return true;
                        }
                    }
                }
            }
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null && am.isMusicActive()) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void checkFrontDoorPauseMusic() {
        try {
            SharedPreferences p = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            if (p.getBoolean("vehicle_door_pause_music_enabled", false)) {
                if (gearStateMachine != null && gearStateMachine.getGear() == 5) {
                    new SteeringWheelKeyManager(this).sendMediaKeyEventPublic(KeyEvent.KEYCODE_MEDIA_PAUSE);
                    AppLogger.i("车身联动", "检测到P挡且前门开启，已自动发送媒体暂停指令");
                }
            }
        } catch (Throwable ignored) {}
    }

    private int originalBrightness = -1;
    private void adjustScreenBrightness(final boolean dim) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                    int dimPercent = prefs.getInt("vehicle_light_dim_level", 35);
                    if (dim) {
                        try {
                            originalBrightness = android.provider.Settings.System.getInt(getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS, 180);
                        } catch (Throwable ignored) {}
                        int targetVal = (int) (dimPercent * 255.0f / 100.0f);
                        if (targetVal < 20) targetVal = 20;
                        AdbClient.execute(VehicleAutomationService.this, "settings put system screen_brightness " + targetVal);
                        AppLogger.i("车身联动", "进隧道/夜间开大灯，已平滑微调屏幕背光至护眼亮度 (" + dimPercent + "%)");
                    } else {
                        int restoreVal = originalBrightness > 0 ? originalBrightness : 180;
                        AdbClient.execute(VehicleAutomationService.this, "settings put system screen_brightness " + restoreVal);
                        AppLogger.i("车身联动", "出隧道/白天关大灯，已恢复屏幕原始亮度 (" + restoreVal + ")");
                    }
                } catch (Throwable ignored) {}
            }
        }).start();
    }
}
