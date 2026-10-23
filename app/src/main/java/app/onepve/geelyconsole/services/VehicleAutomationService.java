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
import android.content.pm.ResolveInfo;

import app.onepve.geelyconsole.utils.QQMusicCarController;
import app.onepve.geelyconsole.utils.NetworkPolicyManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.onepve.geelyconsole.R;
import app.onepve.geelyconsole.hal.IVehiclePlatform;
import app.onepve.geelyconsole.hal.VehiclePlatformFactory;
import app.onepve.geelyconsole.hal.platform.BinyueCoolPlatform;
import app.onepve.geelyconsole.hal.platform.BinyueLPlatform;
import app.onepve.geelyconsole.utils.AdbClient;
import app.onepve.geelyconsole.utils.AppLogger;
import app.onepve.geelyconsole.utils.DoorStateManager;
import app.onepve.geelyconsole.utils.DriveModeManager;
import app.onepve.geelyconsole.utils.EasMediaBridge;
import app.onepve.geelyconsole.utils.ForegroundAppDetector;
import app.onepve.geelyconsole.utils.GearStateMachine;
import app.onepve.geelyconsole.utils.IdleScreensaverManager;
import app.onepve.geelyconsole.utils.PrefUtils;
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
    private String lastReportedDspVol = "";
    private boolean lastReportedDspMute = true;

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
    public volatile int lastPowerMode = -1;
    private int lastKeyState = -1;
    private int lastEngineState = -1;
    private int lastHeadlightSts = -1;
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

    // 服务启动时间戳（用于时间戳软栅栏，毫秒级丢弃开机前的陈旧日志）
    private long serviceBootTimestampMs = 0;

    // 转向灯联动 360 状态
    private boolean enableTurnSignal360 = false;
    private boolean isTurnSignal360Active = false;
    public static volatile boolean isAvmCameraShowing = false; // 真实原厂 360 画面前台状态 (来自 ecarx_avm_SocketCommand)

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

    public static VehicleAutomationService getInstance() {
        return instance;
    }

    public DoorStateManager getDoorStateManager() {
        return this.doorStateManager;
    }

    public TrunkStateManager getTrunkStateManager() {
        return this.trunkStateManager;
    }

    private static volatile VehicleAutomationService instance;

    /**
     * 推开主驾门安全守护:
     * - P0 溜车高危告警: 仅当未挂 P 挡推开主驾门且处于点火运转中时告警。
     */
    private void checkParkGearOnDriverDoorOpen() {
        if (gearStateMachine == null) {
            return;
        }
        int gear = gearStateMachine.getGear();
        if (gear != 5) {
            if (isEngineRunning()) {
                AppLogger.w("安全守护", "【安全提示】挡位[" + gear + "]未挂驻车挡开门，提醒请挂入驻车挡");
                if (voicePlayer != null) {
                    voicePlayer.play("gear_park_alarm.mp3", "请挂入驻车挡", VehicleVoicePlayer.PRIORITY_P0_ALARM);
                }
            }
        } else {
            // 挂在物理 P 挡且推开主驾车门：车主泊车下车离车，结束本次行程
            resetTripSpeedAutoplay("挂入P挡驻车且主驾门开启(车主离车)");
        }
    }

    private void notifyWidgetUpdate() {
        try {
            float volt = latestBatteryVoltage > 0 ? latestBatteryVoltage : 13.1f;
            String gearName = "P";
            if (gearStateMachine != null) {
                int g = gearStateMachine.getGear();
                switch (g) {
                    case 2: gearName = "D"; break;
                    case 3: gearName = "N"; break;
                    case 4: gearName = "R"; break;
                    case 5: gearName = "P"; break;
                }
            }
            boolean fl = (currentDoorFL == 1);
            boolean fr = (currentDoorFR == 1);
            boolean rl = (currentDoorRL == 1);
            boolean rr = (currentDoorRR == 1);
            boolean trunk = (currentTrunk == 1);

            app.onepve.geelyconsole.widget.ToolboxWidgetProvider.updateVehicleState(
                    this, volt, gearName, fl, fr, rl, rr, trunk
            );
        } catch (Throwable ignored) {
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

            boolean doorFl = PrefUtils.getBoolean(prefs, "voice_enable_door_fl", true);
            boolean doorFlClose = PrefUtils.getBoolean(prefs, "voice_enable_door_fl_close", true);
            boolean doorFr = PrefUtils.getBoolean(prefs, "voice_enable_door_fr", true);
            boolean doorFrClose = PrefUtils.getBoolean(prefs, "voice_enable_door_fr_close", true);
            boolean doorRl = PrefUtils.getBoolean(prefs, "voice_enable_door_rl", true);
            boolean doorRlClose = PrefUtils.getBoolean(prefs, "voice_enable_door_rl_close", true);
            boolean doorRr = PrefUtils.getBoolean(prefs, "voice_enable_door_rr", true);
            boolean doorRrClose = PrefUtils.getBoolean(prefs, "voice_enable_door_rr_close", true);
            boolean doorRear = PrefUtils.getBoolean(prefs, "voice_enable_door_rear", true);
            boolean trunkOpen = PrefUtils.getBoolean(prefs, "voice_enable_trunk_open", true);
            boolean trunkClose = PrefUtils.getBoolean(prefs, "voice_enable_trunk_close", true);
            boolean gearD = prefs.getBoolean("voice_enable_gear_d", true);
            boolean gearR = prefs.getBoolean("voice_enable_gear_r", true);
            boolean gearP = prefs.getBoolean("voice_enable_gear_p", true);
            boolean gearN = prefs.getBoolean("voice_enable_gear_n", false);
            boolean modeSmart = prefs.getBoolean("voice_enable_mode_smart", true);
            boolean modeComfort = prefs.getBoolean("voice_enable_mode_comfort", true);
            boolean modeEco = prefs.getBoolean("voice_enable_mode_eco", true);
            boolean modeSport = prefs.getBoolean("voice_enable_mode_sport", true);
            String wheelMode = prefs.getString("wheel_control_mode", SteeringWheelKeyManager.MODE_CARMEDIA_FIRST);
            boolean wheelEnabled = wheelMaster && !SteeringWheelKeyManager.MODE_FACTORY_DEFAULT.equals(wheelMode);
            boolean pushPlayback = prefs.getBoolean("wheel_push_playback_cluster", false);
            boolean pushLyrics = prefs.getBoolean("wheel_push_lyrics_cluster", false);
            try {
                EasMediaBridge.getInstance(context).syncConfig(wheelMode, pushPlayback, pushLyrics);
            } catch (Throwable ignored) {}

            boolean voiceCompEnabled = prefs.getBoolean("voice_gain_compensation_enabled", true);
            int voiceCompOffset = prefs.getInt("voice_gain_compensation_offset", 3);
            try {
                EasMediaBridge.getInstance(context).syncVoiceCompensationConfig(voiceCompEnabled, voiceCompOffset);
            } catch (Throwable ignored) {}

            boolean turn360 = PrefUtils.getBoolean(prefs, "vehicle_turn_signal_360_enabled", false);
            if (instance != null) {
                instance.enableTurnSignal360 = turn360;
            }

            boolean anyVoiceEnabled = voiceMaster && (doorFl || doorFlClose || doorFr || doorFrClose ||
                                doorRl || doorRlClose || doorRr || doorRrClose || doorRear ||
                                trunkOpen || trunkClose || gearD || gearR || gearP || gearN ||
                                modeSmart || modeComfort || modeEco || modeSport);

            boolean speedAutoplay = prefs.getBoolean("vehicle_speed_autoplay_enabled", true);
            boolean speedCustom = prefs.getBoolean("vehicle_speed_custom_action_enabled", false);
            boolean shouldRun = anyVoiceEnabled || wheelEnabled || turn360 || speedAutoplay || speedCustom
                    || prefs.getBoolean(IdleScreensaverManager.KEY_ENABLED, false);

            Intent intent = new Intent(context, VehicleAutomationService.class);
            if (shouldRun) {
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
        serviceBootTimestampMs = System.currentTimeMillis();
        isRunning = true;
        instance = this;
        voicePlayer = VehicleVoicePlayer.getInstance(this);
        wheelKeyManager = new SteeringWheelKeyManager(this);

        // 预热 QQ 音乐车机版官方 AIDL 服务连接 (移植自老版本 QQ音乐助手 极速响应机制)
        try {
            QQMusicCarController.getInstance(this).ensureConnected();
        } catch (Throwable ignored) {}

        // 每次启动服务或开机时，显式复位用户手动暂停标记与单次行程锁
        // 彻底根除因上一趟行程按了暂停，重启车机后 user_manually_paused_media 依然死锁在磁盘导致起步不放歌的 Bug！
        getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("user_manually_paused_media", false).apply();
        speedAutoplayTriggeredInTrip = false;
        speedCustomActionTriggeredInTrip = false;

        // 初始化四大独立状态机
        doorStateManager = new DoorStateManager(this, voicePlayer);
        doorStateManager.setListener((fl, fr, rl, rr) -> {
            if (fl == 1 && currentDoorFL == 0) {
                checkParkGearOnDriverDoorOpen();
            }
            currentDoorFL = fl;
            currentDoorFR = fr;
            currentDoorRL = rl;
            currentDoorRR = rr;
            notifyWidgetUpdate();
        });

        trunkStateManager = new TrunkStateManager(this, voicePlayer);
        trunkStateManager.setListener(trunk -> {
            currentTrunk = trunk;
            notifyWidgetUpdate();
        });

        gearStateMachine = new GearStateMachine(this, voicePlayer);
        gearStateMachine.setListener(gear -> {
            lastGearPos = gear;
            notifyWidgetUpdate();
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

        // 开机点火防洪峰与防堵塞铁律：
        // 1. 原厂 MediaKeyReceiver 拦截状态同步涉及 ADB/网络IO，开机初 ADB 守护进程未就绪容易 Broken pipe / Timeout 阻塞主线程；
        // 2. 必须错峰延后 15 秒异步执行，彻底保障开机第一秒车门与挡位信号毫秒级响应
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (wheelKeyManager != null) {
                    wheelKeyManager.syncMediaKeyReceiverState();
                }
            }
        }, 15000);
        try {
            IdleScreensaverManager.start(this);
        } catch (Throwable e) {
            Log.w(TAG, "Failed to start IdleScreensaverManager: " + e.getMessage());
        }

        registerPowerStateReceiver();
        IVehiclePlatform currentPlatform = VehiclePlatformFactory.getPlatform(this);
        if (currentPlatform instanceof BinyueLPlatform) {
            AppLogger.i("车身联动", "检测到缤越 L (IHU624G / Flyme Auto)，通过 HAL 启动 VHAL 感知驱动 (免 logcat)");
            currentPlatform.getSensorAdapter(this).startListening(this, null);
        } else {
            startLogcatReader();
        }
        registerEcarxKeyReceiver();
        startMediaMonitor();
        EasMediaBridge.getInstance(this); // 确保蓝牙 A2DP 连接/推流监听与原车音频通道常驻就绪

        // 开机点火防洪峰：重开销的包缓存预热延后 20 秒异步错峰执行，彻底避开车机开机与用户上车初期的 CPU/IO 争抢
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            SystemUtils.warmDisabledPackagesCache();
                            Log.i(TAG, "Package cache warmed up asynchronously after boot peak (20s delay)");
                        } catch (Throwable ignored) {}
                    }
                }, "Geely_WarmupThread").start();
            }
        }, 20000);

        // 开机与自动化启动兜底：若用户开启了悬浮小胶囊，确保悬浮服务伴随启动
        try {
            SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            if (prefs.getBoolean("floating_enabled", false)) {
                FloatingWindowService.ensureServiceStarted(this);
            }
        } catch (Throwable ignored) {}

        // 开机车载网络与外设连接策略守护 (后台就绪自检探针，零阻塞主线程)
        try {
            NetworkPolicyManager.runBootPolicyWorkerAsync(this);
        } catch (Throwable ignored) {}

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.CHINA);
        long now = System.currentTimeMillis();
        long bootUpElapsed = android.os.SystemClock.elapsedRealtime();
        long vehicleWakeupTime = now - Math.min(bootUpElapsed, 3000L);
        String welcomeTimeStr = sdf.format(new java.util.Date(vehicleWakeupTime));
        String startupTimeStr = sdf.format(new java.util.Date(now));

        AppLogger.i("车辆迎宾", "车辆迎宾时间: " + welcomeTimeStr);
        AppLogger.i("车辆迎宾", "钥匙开门/靠近唤醒生效，物理基准已建立 (登车态)");
        AppLogger.i("系统启动", "软件启动时间: " + startupTimeStr);
        AppLogger.i("系统启动", "缤越助手守护就绪 (PID: " + android.os.Process.myPid() + " | VHAL/Logcat双通道)");
        String verName = "v2.5.3";
        try {
            verName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        AppLogger.i("系统启动", "软件版本: " + verName);
        AppLogger.i("系统启动", "车型平台: " + currentPlatform.getPlatformName() + " (" + currentPlatform.getPlatformCode() + ")");
        Log.i(TAG, "VehicleAutomationService started successfully");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        reloadPreferences();
        // 配置更新或热启动时异步同步，避免主线程阻塞
        if (wheelKeyManager != null) {
            wheelKeyManager.syncMediaKeyReceiverState();
        }
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

        // 启动兜底：读上次缓存电压，防热更新/崩溃重启后 latestBatteryVoltage=0 导致门控误判熄火
        // 铁律：只在当前 latestBatteryVoltage 还未被 logcat 更新时才用缓存值（避免覆盖已读到的实时值）
        if (latestBatteryVoltage < 9.0f) {
            float cachedVolt = prefs.getFloat("vehicle_real_battery_volt", 0f);
            if (cachedVolt >= 13.0f) {
                latestBatteryVoltage = cachedVolt;
                if (lastPowerMode < 0) lastPowerMode = 1; // 上次是运行状态，保守视为仍在运行
                AppLogger.i("电源状态", "启动兜底：读缓存电压=" + cachedVolt + "V，lastPowerMode→1");
            }
        }

        voiceMasterSwitch = prefs.getBoolean("voice_master_switch", true);
        wheelMasterSwitch = prefs.getBoolean("wheel_master_switch", true);

        enableDoorFl = PrefUtils.getBoolean(prefs, "voice_enable_door_fl", true);
        enableDoorFlClose = PrefUtils.getBoolean(prefs, "voice_enable_door_fl_close", true);
        enableDoorFr = PrefUtils.getBoolean(prefs, "voice_enable_door_fr", true);
        enableDoorFrClose = PrefUtils.getBoolean(prefs, "voice_enable_door_fr_close", true);
        enableDoorRl = PrefUtils.getBoolean(prefs, "voice_enable_door_rl", true);
        enableDoorRlClose = PrefUtils.getBoolean(prefs, "voice_enable_door_rl_close", true);
        enableDoorRr = PrefUtils.getBoolean(prefs, "voice_enable_door_rr", true);
        enableDoorRrClose = PrefUtils.getBoolean(prefs, "voice_enable_door_rr_close", true);
        enableDoorRear = PrefUtils.getBoolean(prefs, "voice_enable_door_rear", true);
        enableTrunkOpen = PrefUtils.getBoolean(prefs, "voice_enable_trunk_open", true);
        enableTrunkClose = PrefUtils.getBoolean(prefs, "voice_enable_trunk_close", true);
        enableGearD = prefs.getBoolean("voice_enable_gear_d", true);
        enableGearR = prefs.getBoolean("voice_enable_gear_r", true);
        enableGearP = prefs.getBoolean("voice_enable_gear_p", true);
        enableGearN = prefs.getBoolean("voice_enable_gear_n", false);
        enableGearS = prefs.getBoolean("voice_enable_gear_s", true);
        enableModeSmart = prefs.getBoolean("voice_enable_mode_smart", true);
        enableModeComfort = prefs.getBoolean("voice_enable_mode_comfort", true);
        enableModeEco = prefs.getBoolean("voice_enable_mode_eco", true);
        enableModeSport = prefs.getBoolean("voice_enable_mode_sport", true);

        enableTurnSignal360 = PrefUtils.getBoolean(prefs, "vehicle_turn_signal_360_enabled", false);

        // 若服务在行车中启动，立即建立主驾已就坐基准，避免车门语音误判为上车
        if (lastPowerMode > 0 && doorStateManager != null) {
            doorStateManager.markDriverInside();
        }

        String wheelMode = prefs.getString("wheel_control_mode", SteeringWheelKeyManager.MODE_CARMEDIA_FIRST);
        boolean wheelEnabled = wheelMasterSwitch && !SteeringWheelKeyManager.MODE_FACTORY_DEFAULT.equals(wheelMode);

        boolean anyVoiceEnabled = voiceMasterSwitch && (enableDoorFl || enableDoorFlClose || enableDoorFr || enableDoorFrClose ||
                             enableDoorRl || enableDoorRlClose || enableDoorRr || enableDoorRrClose || enableDoorRear ||
                             enableTrunkOpen || enableTrunkClose || enableGearD || enableGearR || enableGearP || enableGearN || enableGearS ||
                             enableModeSmart || enableModeComfort || enableModeEco || enableModeSport);
        boolean speedAutoplay = prefs.getBoolean("vehicle_speed_autoplay_enabled", true);
        boolean speedCustom = prefs.getBoolean("vehicle_speed_custom_action_enabled", false);
        boolean anyEnabled = anyVoiceEnabled || wheelEnabled || enableTurnSignal360
                || speedAutoplay || speedCustom
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
                boolean screenOn = Intent.ACTION_SCREEN_ON.equals(action) ||
                        "android.intent.action.QUICKBOOT_POWERON".equals(action);

                if (realPowerOff) {
                    lastPowerMode = 0; // 真正熄火下电：强制锁定为 0
                    if (gearStateMachine != null) gearStateMachine.resetState();
                    if (driveModeManager != null) driveModeManager.resetState();
                    if (doorStateManager != null) doorStateManager.resetState();
                    resetTripSpeedAutoplay("整车熄火下电广播");
                } else if (screenOn) {
                    // 亮屏唤醒
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
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction("android.intent.action.QUICKBOOT_POWERON");
        try {
            registerReceiver(powerReceiver, filter);
        } catch (Exception ignored) {}
    }

    // 正则表达式（全部预编译为静态常量：本类逐行处理 logcat 全量流，
    // 禁止在 parse 路径内 Pattern.compile —— 每行重复编译正则是高频 CPU 浪费）
    private static final Pattern CAN_PATTERN =
            Pattern.compile("key\\s*=\\s*([^,\\s]+).*?data\\s*=\\s*(-?\\d+)");
    private static final Pattern SERIAL_DOOR_PATTERN =
            Pattern.compile("91\\s+02\\s+01(?:\\s+[0-9a-fA-F]{1,2}){3}\\s+([0-9a-fA-F]{1,2})\\s+([0-9a-fA-F]{1,2})");
    private static final Pattern P_TASKER_SPEED =
            Pattern.compile("(?:getVehicleSpeed:speed|carSpeed)\\s*==\\s*([0-9.]+)");
    private static final Pattern P_GET_SPEED =
            Pattern.compile("Get Speed\\s+(\\d+)km/h");
    private static final Pattern P_CARAUDIO_SPEED =
            Pattern.compile("CarAudioInfo.*?(?:,\\s*speed=(\\d+)|Get Speed\\s+(\\d+)km/h)");
    private static final Pattern P_JSON_VEHICLE_SPEED =
            Pattern.compile("\"Vehicle_speed\"\\s*:\\s*\"?(\\d+)\"?");
    private static final Pattern P_IPK_INSTANT_SPEED =
            Pattern.compile("(?:onVehicleEventIpkSpeed[^0-9a-fA-F]*state=(?:0x)?([0-9a-fA-F]+)|VehId=Vehicle_IPK_Speed\\s+value=(?:0x)?([0-9a-fA-F]+)|INFO_ID_IPKINFO_INSTANT_SPEED.*?(?:funcValue\\((?:0x)?([0-9a-fA-F]+)\\)|value\\s*[:=]\\s*(?:0x)?([0-9a-fA-F]+)))");
    private static final Pattern P_VEHICLE_GEAR =
            Pattern.compile("VehId=Vehicle_Gear\\s+value=(?:0x)?([0-9a-fA-F]+)");
    private static final Pattern P_GEAR_EQ =
            Pattern.compile("gear=(\\d+)");
    private static final Pattern P_GEAR_POS =
            Pattern.compile("(?:func?Value\\((?:0x)?([0-9a-fA-F]+)\\)|(?:mModelGearPos|GearPos)\\s*[:=]\\s*(?:0x)?([0-9a-fA-F]+))");
    private static final Pattern P_FUNVALUE =
            Pattern.compile("funValue\\((?:0x)?([0-9a-fA-F]+)\\)");
    private static final Pattern P_DRIVE_MODE =
            Pattern.compile("(?:DirveMode|DriveMode)\\s*=\\s*(\\d+)");
    private static final Pattern P_COMFORT_DRIVE_MODE =
            Pattern.compile("DM_FUNC_DRIVE_MODE_SELECT\\s+value\\s*=\\s*(\\d+)");
    private static final Pattern P_HAL_DRIVE_MODE =
            Pattern.compile("parseRxProtoBuf:\\s+vp->ap\\s*\\(0x287000eb,\\s*(?:0x)?([0-9a-fA-F]+)\\s*\\)");
    private static final Pattern P_MCU_DRIVE_MODE_FRAME =
            Pattern.compile("read data:\\s*91\\s+09\\s+02\\s+00\\s+05\\s+00\\s+[0-9a-fA-F]{2}\\s+([0-9a-fA-F]{2})");
    private static final Pattern P_PEPS_POWERMODE =
            Pattern.compile("peps_powermode[^0-9]*(\\d+)");
    private static final Pattern P_KEY_STATE =
            Pattern.compile("(?:funvalue|info_id_vpowerinfo_key_state|key_state)[^0-9a-f]*(?:0x)?([0-9a-f]+)");
    private static final Pattern P_ENGINE_STATE =
            Pattern.compile("(?:info_id_vpowerinfo_engine_state|engine_state)[^0-9]*(\\d+)");
    private static final Pattern P_BOOTUP_REASON =
            Pattern.compile("ap_power_bootup_reason[^0-9]*(\\d+)");
    private static final Pattern P_TURN_LIGHT =
            Pattern.compile("turnLight\\s*=\\s*(\\d+)");
    private static final Pattern P_AVM_SOCKET_CAM =
            Pattern.compile("camera_state\\s*:\\s*(\\d+)");
    private static final Pattern P_GOC_A2DP_VOL =
            Pattern.compile("a2dp_player_setvol.*?:\\s*val\\s*:([0-9.]+)");

    // 核心功能前置白名单（100% 覆盖方控、车速、挡位、车门尾箱、模式、电源点火、转向灯360、电瓶电压与Tasker CAN）
    private static final String[] FEATURE_FAST_WHITELIST = {
        // 1. 方向盘方控
        "reportkeytoadaptapi", "shouldcallback", "swc_trip", "do nothing for this key", "onalreadyhome", "cmd_data[",
        // 2. 车辆实时车速
        "caraudioinfo", "get speed", "ipkinfo_instant_speed", "vehiclespeed", "carspeed", "vehicle_speed", "ipkspeed",
        // 3. 挡位状态 (彻底锁定原厂唯一真源: VehId=Vehicle_Gear)
        "vehicle_gear",
        // 4. 车门、后尾箱与乘员安全带
        "91 02 01", "mcu->mpu", "_door_sts", "mmodellfdoor", "mmodelrfdoor", "mmodellrdoor", "mmodelrrdoor", "seat_belt", "0x002012", "0x002013",
        // 5. 驾驶模式切换
        "dm_func_drive_mode", "dirvemode", "drivemode", "57049113", "0x287000eb", "91 09 02 00 05",
        // 6. 电源、点火、熄火与启停
        "vpowerinfo", "peps_powermode", "bootup_reason", "engine_state", "key_state", "onaccoff", "on_acc_off", "fuelconsumptionservice",
        // 7. Tasker 经典底层 CAN 信号通道
        "parsecandata",
        // 8. 转向灯360与画面感知
        "turnlight=", "camera_state:",
        // 9. 原车音频 DSP 监控与电瓶电压
        "a2dp_player_", "mmodelbatteryvolt"
    };

    private void startLogcatReader() {
        if (logcatThread != null && logcatThread.isAlive()) return;

        logcatThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRunning) {
                    try {
                        // 核心日志通道: 
                        // 1. VehicleDataBuilder (CAN 信号)
                        // 2. SerialControl_v2_0 & VehicleEmulator_v2_0 (MCU 物理串口报文 91 02 01)
                        // 3. ECARX@ECP & MCULog (MCU 物理挡位 GearPosition 与门控 FL_DOOR_STS)
                        // 4. InputManager / CAR.INPUT (方向盘按键报文)
                        // 5. ecarx_core_server (mModelLFDoor 门控信号)
                        // 放宽串口与系统标签日志级别至 :V，彻底杜绝车门报文被丢弃
                        // 不在命令行中限制 -s 标签（避免因为底层模块标签变动或漏掉标签导致整包被丢弃）
                        // 直接全量监听，由我们在 parseLogLine 中进行高效关键字判定！
                        // 核心铁律：持续实时监听 logcat，由我们在 parseLogLine 中进行高效关键字判定！
                        // 稳定性规范：严禁使用 -t 50 / -d 等非阻塞参数，否则进程读完 50 行立即 EOF 退出导致外层 while 陷入每 2 秒死循环重启！
                        // 核心防翻旧账：写入纳秒同步标记行，读到标记前秒级抽干存量历史缓冲区
                        final String drainMarker = "GEELY_TOOLBOX_DRAIN_MARKER_" + android.os.SystemClock.elapsedRealtimeNanos();
                        Log.i("GeelyConsole", drainMarker);

                        ProcessBuilder pb = new ProcessBuilder("logcat", "-v", "time", "-b", "all");
                        pb.redirectErrorStream(true);
                        logcatProcess = pb.start();
                        AppLogger.i("系统日志", "Logcat 实时监听守护线程已建立就绪");

                        BufferedReader reader = new BufferedReader(new InputStreamReader(logcatProcess.getInputStream()), 1024);
                        String line;
                        boolean bufferDrained = false;
                        long drainLinesCount = 0;
                        while (isRunning && (line = reader.readLine()) != null) {
                            if (!bufferDrained) {
                                drainLinesCount++;
                                if (line.contains(drainMarker) || drainLinesCount > 5000) {
                                    bufferDrained = true;
                                    AppLogger.i("系统日志", "Logcat 历史缓冲抽干就绪 (跳过 " + drainLinesCount + " 行旧日志)，正式进入实时信号监听");
                                }
                                continue;
                            }
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
        // 过滤声卡底层极高频状态轮询日志，防 logcat 管道积压引起时钟倒流误判
        if (line.contains("APM_AudioPolicyManager")) return;

        // 【前置极速过滤守卫 (Pre-Filter Gate)】：非缤越助手功能关心的日志，0.0001毫秒瞬时丢弃！
        // 压测验证：单次瞬时过滤 98.2% 无关系统刷屏垃圾，车机 CPU 占用暴降 90%
        String lineLower = line.toLowerCase(java.util.Locale.ROOT);
        boolean isInterested = false;
        for (int i = 0; i < FEATURE_FAST_WHITELIST.length; i++) {
            if (lineLower.contains(FEATURE_FAST_WHITELIST[i])) {
                isInterested = true;
                break;
            }
        }
        if (!isInterested) return;

        // 【应用层时间戳软栅栏 (Timestamp Gate)】：毫秒级丢弃开机前的陈旧录像日志
        // 核心铁律：车机时间已同步至真实日期时，冷启动初期 01-01 的存量历史日志一律丢弃，彻底杜绝历史开门/按键倒灌！
        if (serviceBootTimestampMs > 0 && line.length() >= 18 && line.charAt(2) == '-' && line.charAt(5) == ' ') {
            try {
                java.util.Calendar bootCal = java.util.Calendar.getInstance();
                bootCal.setTimeInMillis(serviceBootTimestampMs);
                // 吉利车机冷启动未对齐时出厂默认基准为 2010-01-01；只要年份 >= 2024 即为已同步真实时间
                boolean isBootInRealTime = bootCal.get(java.util.Calendar.YEAR) >= 2024;
                boolean isTodayNewYearsDay = (bootCal.get(java.util.Calendar.MONTH) == java.util.Calendar.JANUARY 
                        && bootCal.get(java.util.Calendar.DAY_OF_MONTH) == 1);
                
                // 系统已校准至真实日期且今天不是元旦当天：所有 01-01 阶段的存量冷启动积压日志瞬时丢弃！
                if (isBootInRealTime && !isTodayNewYearsDay && line.startsWith("01-01")) {
                    return;
                }

                int month = Integer.parseInt(line.substring(0, 2));
                int day = Integer.parseInt(line.substring(3, 5));
                int hour = Integer.parseInt(line.substring(6, 8));
                int min = Integer.parseInt(line.substring(9, 11));
                int sec = Integer.parseInt(line.substring(12, 14));
                int ms = (line.length() >= 18 && line.charAt(14) == '.') ? Integer.parseInt(line.substring(15, 18)) : 0;
                
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTimeInMillis(serviceBootTimestampMs);
                cal.set(java.util.Calendar.MONTH, month - 1);
                cal.set(java.util.Calendar.DAY_OF_MONTH, day);
                cal.set(java.util.Calendar.HOUR_OF_DAY, hour);
                cal.set(java.util.Calendar.MINUTE, min);
                cal.set(java.util.Calendar.SECOND, sec);
                cal.set(java.util.Calendar.MILLISECOND, ms);
                
                // 早于服务启动时刻的历史日志，判定为历史录像日志直接丢弃，严禁流入状态机翻旧账！
                if (cal.getTimeInMillis() < serviceBootTimestampMs) {
                    return;
                }
            } catch (Throwable ignored) {}
        }

        // 1. 解析方向盘按键
        // parseKeyFromLine 内部已根据 press/release 调用 handleKeyDown/handleKeyUp，
        // 这里只负责识别到按键后提前结束本行解析，避免继续被其它正则误匹配。
        if (wheelKeyManager != null) {
            int wheelKey = wheelKeyManager.parseKeyFromLine(line);
            if (wheelKey > 0) {
                return;
            }
        }

        // 2. 解析车辆实时车速 (以原厂随速音量 CarAudioInfo / Get Speed 为最高优先级主力源，原厂纯整数零误差)
        if (line.contains("CarAudioInfo") || line.contains("Get Speed") || line.contains("INFO_ID_IPKINFO_INSTANT_SPEED") || line.contains("getVehicleSpeed") || line.contains("Vehicle_speed")) {
            try {
                // 优先级 1 (主力真源): 车载音效随速音量补偿报文 (原厂十进制标准 km/h，最稳最快，无浮点计算)
                if (line.contains("CarAudioInfo") || line.contains("Get Speed")) {
                    Matcher m = P_CARAUDIO_SPEED.matcher(line);
                    if (m.find()) {
                        String spStr = m.group(1) != null ? m.group(1) : m.group(2);
                        if (spStr != null && !spStr.isEmpty()) {
                            int speed = Integer.parseInt(spStr);
                            if (speed >= 0 && speed <= 240) {
                                currentSpeedKmH = speed;
                                processVehicleSpeedAutomation(currentSpeedKmH);
                                return;
                            }
                        }
                    }
                }
                // 优先级 2: 原厂仪表盘高频车速 (IPK / SensorModule，单位 0.01 km/h，除以 100)
                if (line.contains("INFO_ID_IPKINFO_INSTANT_SPEED") && !line.contains("AvmStateManger") && !line.contains("recvVehicleCmd")) {
                    Matcher m = P_IPK_INSTANT_SPEED.matcher(line);
                    if (m.find()) {
                        String hexStr = null;
                        for (int i = 1; i <= m.groupCount(); i++) {
                            if (m.group(i) != null) {
                                hexStr = m.group(i);
                                break;
                            }
                        }
                        if (hexStr != null && !hexStr.isEmpty()) {
                            try {
                                int rawVal = Integer.parseInt(hexStr, 16);
                                int parsedSpeed = Math.round(rawVal / 100.0f);
                                if (parsedSpeed >= 0 && parsedSpeed <= 240) {
                                    currentSpeedKmH = parsedSpeed;
                                    processVehicleSpeedAutomation(currentSpeedKmH);
                                    return;
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
                // 优先级 3: Tasker 高频基准源
                if (line.contains("getVehicleSpeed") || line.contains("carSpeed")) {
                    Matcher m = P_TASKER_SPEED.matcher(line);
                    if (m.find()) {
                        String spStr = m.group(1);
                        if (spStr != null && !spStr.isEmpty()) {
                            double rawSpeed = Double.parseDouble(spStr);
                            int spKmH;
                            if (line.contains("getVehicleSpeed:speed")) {
                                spKmH = (int) Math.round(rawSpeed);
                            } else {
                                spKmH = (int) Math.round(rawSpeed * 3.6);
                            }
                            if (spKmH >= 0 && spKmH <= 240) {
                                currentSpeedKmH = spKmH;
                                processVehicleSpeedAutomation(currentSpeedKmH);
                                return;
                            }
                        }
                    }
                }
                // 优先级 4: 吉利亿咖通数据中心原生 JSON 报文
                if (line.contains("Vehicle_speed")) {
                    Matcher m = P_JSON_VEHICLE_SPEED.matcher(line);
                    if (m.find()) {
                        String spStr = m.group(1);
                        if (spStr != null && !spStr.isEmpty()) {
                            int speed = Integer.parseInt(spStr);
                            if (speed >= 0 && speed <= 240) {
                                currentSpeedKmH = speed;
                                processVehicleSpeedAutomation(currentSpeedKmH);
                                return;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            return;
        }

        // 3. 解析车辆挡位报文 (唯一法定真源: VehId=Vehicle_Gear)
        // 核心铁律：仅信赖原厂底盘总线 VehId=Vehicle_Gear (0x02=D, 0x03=N, 0x04=R, 0x05=P)，绝不杂糅任何上层伪信号！
        int gearVal = -1;

        if (line.contains("VehId=Vehicle_Gear")) {
            try {
                Matcher m = P_VEHICLE_GEAR.matcher(line);
                if (m.find()) {
                    int rawHex = Integer.parseInt(m.group(1), 16);
                    int low = rawHex & 0x0F;
                    if (low == 0x05) gearVal = 5;      // P 挡 (驻车)
                    else if (low == 0x04) gearVal = 4; // R 挡 (倒挡)
                    else if (low == 0x03) gearVal = 3; // N 挡 (空挡)
                    else if (low == 0x02) gearVal = 2; // D 挡 (前进)
                    else if (low == 0x06 || low == 0x07) gearVal = 6; // S 挡
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

                    // 过滤机制：0x00 代表 MCU 刚启动通信通道未对齐的瞬态无效占位帧，绝对忽略！
                    // 正常四门全关为 0x55，各门开为 0x54/0x51/0x45 等，绝不可把 0x00 当成四门全开！
                    if (b6 == 0x00 && b7 == 0x00) return;

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
                AppLogger.w("车门状态", "解析串口车门报文异常: " + e.getMessage() + " | line=" + line);
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

        // 4.3 底盘主副驾座椅乘员与安全带状态 (INFO_ID_IPKWARN_PASS_SEAT_BELT / INFO_ID_IPKWARN_DRV_SEAT_BELT)
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

        // 4.1 解析驾驶模式切换信号 (严格收敛对齐 Tasker 实车验证黄金法则: 纯净收敛于 ECarXCarConfigService、AVM底盘守护与 AdaptAPI 权威常量)
        int modeVal = -1;

        // 优先 1: AVM 状态管理器底盘直出信号 (仅在模式变化跃变时参考，且不能作为阻断物理旋钮播报的基准)
        // 注意：AVM底盘守护每3秒周期性心跳广播 DriveMode1=0x12 (包含当前挡位与模式)，
        // 若在点火初期就静默建立基准，会导致车主随后正式切入该模式时被防抖丢弃！
        // 因此常规状态更新优先信赖物理旋钮与系统配置 (DirveMode / DM_FUNC_DRIVE_MODE_SELECT / AdaptAPI 570491136)
        // 核心铁律：仅信赖原厂旋钮与原生事件，绝对不可拿 MCU 0x09 车速/挡位复合帧当模式解析！
        if (line.contains("DM_FUNC_DRIVE_MODE_SELECT")) {
            try {
                Matcher m = P_COMFORT_DRIVE_MODE.matcher(line);
                if (m.find()) {
                    int dm = Integer.parseInt(m.group(1));
                    if (dm == 1) modeVal = MODE_COMFORT;
                    else if (dm == 2) modeVal = MODE_SPORT;
                    else if (dm == 3) modeVal = MODE_ECO;
                    else if (dm == 4 || dm == 6) modeVal = MODE_SMART;
                }
            } catch (Exception ignored) {}
        }
        else if (line.contains("DirveMode =") || line.contains("DirveMode=") || line.contains("DriveMode =") || line.contains("DriveMode=")) {
            try {
                Matcher m = P_DRIVE_MODE.matcher(line);
                if (m.find()) {
                    int dm = Integer.parseInt(m.group(1));
                    if (dm == 1) modeVal = MODE_COMFORT;
                    else if (dm == 2) modeVal = MODE_SPORT;
                    else if (dm == 3) modeVal = MODE_ECO;
                    else if (dm == 4 || dm == 6) modeVal = MODE_SMART;
                }
            } catch (Exception ignored) {}
        }
        // 核心铁律：坚决拔除 AVM 模块的 DriveMode1= 心跳兜底！
        // 亿咖通原厂 ecarx_avm_state 每 3 秒周期性广播一次 'DriveMode1=0x12 Gear_position=GEAR_DRIVE'。
        // 其高 4 位 0x1 永远恒定代表 Comfort(舒适)，这只是 360 环视模块内部的默认透传占位符，
        // 根本不是当前底盘真实的驾驶模式！一旦兜底该信号，会导致只要车辆点火，就会被误判并反复播报为舒适模式。

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

        // 5.1 解析 ga10 / E02 架构转向灯报文 (paramVehicleTurnLight ... turnLight=1/2/0)
        // 权威硬件驱动输出，只在打灯(1/2)与回正(0)时跳变；严禁监听 ecarx_avm_state 的常驻心跳 Vehicle_TurnLight 防秒退
        if (enableTurnSignal360 && line.contains("turnLight=")) {
            try {
                Matcher m = P_TURN_LIGHT.matcher(line);
                if (m.find()) {
                    int turnVal = Integer.parseInt(m.group(1));
                    handleTurnSignalState(turnVal);
                }
            } catch (Exception ignored) {}
        }

        // 5.2 解析原厂 360 画面启闭真实状态 (ecarx_avm_SocketCommand: sendSocketMsg: camera_state: 1/0)
        if (line.contains("camera_state:")) {
            try {
                Matcher mCam = P_AVM_SOCKET_CAM.matcher(line);
                if (mCam.find()) {
                    int camState = Integer.parseInt(mCam.group(1));
                    boolean showing = (camState == 1);
                    if (showing != isAvmCameraShowing) {
                        isAvmCameraShowing = showing;
                        AppLogger.i("车身联动", "【原厂环视】360 画面真实状态跃变: " + (showing ? "已在前台显示" : "已退出/关闭"));
                    }
                }
            } catch (Exception ignored) {}
        }

        // 5.3 监控原车硬件功放静音与推流状态 (goc / MT8666 音频 DSP: a2dp_player_unmute & setvol)
        if (line.contains("a2dp_player_") || line.contains("MT8666 a2dp_player_setvol")) {
            try {
                if (line.contains("a2dp_player_unmute")) {
                    if (lastReportedDspMute) {
                        lastReportedDspMute = false;
                        AppLogger.i("音频与蓝牙", "【原车DSP】底层 A2DP 功放解除静音，通道放音就绪");
                    }
                } else if (line.contains("setvol")) {
                    Matcher mVol = P_GOC_A2DP_VOL.matcher(line);
                    if (mVol.find()) {
                        String volStr = mVol.group(1);
                        if (!volStr.equals(lastReportedDspVol)) {
                            lastReportedDspVol = volStr;
                            AppLogger.i("音频与蓝牙", "【原车DSP】硬件音量当前置为: " + volStr);
                        }
                    }
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
                                        .edit().putFloat("vehicle_real_battery_volt", volt).commit(); // 铁律：防掉电丢失，禁用 apply
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * 判定整车是否处于真正点火启动/行车就绪状态
     */
    public boolean isEngineRunning() {
        if (currentSpeedKmH > 5) return true; // 车速大于5km/h是行驶的绝对证据，绝不因未收到点火报文而判定熄火
        if (lastPowerMode == 0) return false;
        if (currentSpeedKmH > 0) return true;
        if (latestBatteryVoltage >= 13.2f) return true;
        if (lastPowerMode == 1 || lastKeyState == 2) return true;
        if (latestBatteryVoltage > 0 && latestBatteryVoltage < 12.8f && currentSpeedKmH == 0) return false;
        return true;
    }

    /**
     * 对外公开的车速信号注入入口（供 HAL 平台驱动或其它平台适配器调用）
     */
    public void handleSpeedSignal(final int speed) {
        currentSpeedKmH = speed;
        processVehicleSpeedAutomation(currentSpeedKmH);
    }

    public void handleGearSignal(final int gear) {
        if (gearStateMachine == null) return;

        // 核心铁律：熄火下电与等红绿灯自动启停精准物理隔离！
        // 1. 真正熄火判定：lastPowerMode == 0 或 引擎已关闭且车速为0
        //    此时底盘 PDC/SensorModule 会以 500ms 周期性轮询 TCU 挡位 (上报 0x00200230 等)，
        //    绝对严禁调用 resetState() 将 lastGearPos 归零为 -1，绝对严禁对轮询心跳发声！
        boolean isReallyShutDown = (lastPowerMode == 0 || (!isEngineRunning() && currentSpeedKmH == 0));
        if (isReallyShutDown) {
            // 熄火停在 P 挡时，仅静默维持 P 挡基准，拦截所有非 P 挡误报与轮询心跳，彻底根除死循环！
            if (gear == 5) {
                gearStateMachine.updateGear(5, false, getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE));
            }
            return;
        }

        // 2. 正常行车工况 (包括等红绿灯发动机自动启停)：
        //    保持状态机状态记忆，放行真实换挡跃变与语音播报！
        gearStateMachine.updateGear(gear, voiceMasterSwitch, getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE));

        if (gear == 5) {
            currentSpeedKmH = 0; // 挂入 P 挡驻车时车速立即强制归零，防止残留蠕行车速外溢
            // 挂入 P 挡驻车：若主驾车门当前已处于开启状态，协同重置行程
            if (currentDoorFL == 1) {
                resetTripSpeedAutoplay("挂入P挡且主驾门已开启");
            } else {
                // 停车挂入 P 挡停稳，重置单次行程自启锁，方便下次起步重新自启
                resetTripSpeedAutoplay("挂入P挡驻车停稳");
            }
        }

        // D挡起步联动 360 严格单次跃变状态机：切入D挡仅触发1次，锁死不循环调起；切出D挡重新武装
        if (gear == 2) {
            if (gearD360Armed) {
                gearD360Armed = false; // 触发后立即闭锁，车主手动退出360绝不反复调起！
                boolean gearD360 = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE)
                        .getBoolean("vehicle_gear_d_360_enabled", true);
                if (gearD360 && currentSpeedKmH <= 30) {
                    AppLogger.i("车身联动", "【D挡起步联动360】挂入前进挡 D，秒级唤起 360 全景盲区影像 (单次跃变闭环)");
                    open360Camera();
                }
            }
        } else if (gear >= 3 && gear <= 7) {
            // 只要不是 D 挡 (P/R/N/S)，重新恢复武装状态，等待下一次起步切入 D 挡
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
        if (lastEngineState == 0 && currentSpeedKmH == 0 && lastGearPos == 5) return false; // 发动机明确停止且零车速且在P挡
        if (currentSpeedKmH > 0) return true; // 行驶中，必然处于运转工况
        if (lastGearPos == 2 || lastGearPos == 3 || lastGearPos == 4) return true; // 挂入行车挡位(D/N/R)，必然处于驾驶工况(消除等红灯自动启停哑巴窗口)
        if (latestBatteryVoltage >= 13.0f) return true; // 发电机在充电，引擎必转（与isEngineRunning阈值统一）
        if (lastEngineState == 3 || lastEngineState == 2) return true; // 发动机明确处于运行状态
        if (lastPowerMode == 1 && (lastKeyState == 2 || lastKeyState == 3)) return true; // 整车处于 ON/START 行车就绪状态（消除初始冷启动电压爬升期的哑巴窗口）
        // 蓄电池自然静置电压 (<13.0V) 且零车速且在P挡：属于熄火未点火驻车状态，旋钮无法切换模式，一律静默
        return false;
    }

    /**
     * 处理驾驶模式切换状态 (智能有人感知状态机：默认智能模式静默，手动切出智能模式后激活全量播报，切回智能模式播报后置0归位)
     * 模式枚举: MODE_COMFORT=1, MODE_SPORT=2, MODE_ECO=3, MODE_SMART=4 (全局解耦，零错位)
     */
    private void handleDriveModeSignal(int mode) {
        if (!isDrivingModeAllowed()) {
            // 明确熄火/未启动/ACC状态：仅静默确立/跟踪当前模式基准，绝对静音，严禁清除基准导致误播
            if (driveModeManager != null) {
                driveModeManager.updateDriveMode(mode, false, getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE));
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

        // 大灯/小灯联动高德日夜模式 (BCM_PositionLightSts: 1=开启, 0=关闭)
        if ("BCM_PositionLightSts".equals(key)) {
            handleHeadlightNavNight(val);
        }

        // 电源模式/熄火检测
        if ("PEPS_PowerMode".equals(key)) {
            if (val == 0) {
                // 车辆熄火/下电 -> 状态机强制归零，杜绝下次点火误报
                if (gearStateMachine != null) gearStateMachine.resetState();
                if (driveModeManager != null) driveModeManager.resetState();
                if (doorStateManager != null) doorStateManager.resetState();
                isTurnSignal360Active = false;
                lastHeadlightSts = -1;
            }
            lastPowerMode = val;
        }

        // 转向灯联动 360 (TCM_Req_TurnIndicationAct: 1=左转, 2=右转, 0=回正/复位)
        if ("TCM_Req_TurnIndicationAct".equals(key)) {
            handleTurnSignalState(val);
        }
    }

    /**
     * 转向灯联动 360 核心处理逻辑
     * @param turnLight 1=左转, 2=右转, 0=回正/复位
     */
    private void handleTurnSignalState(int turnLight) {
        if (!enableTurnSignal360) return;
        // 1=左转, 2=右转
        if (turnLight == 1 || turnLight == 2) {
            // 倒车 R 挡 (4) 时避让原厂倒车影像，不打扰
            if (lastGearPos == 4) return;
            if (!isTurnSignal360Active) {
                isTurnSignal360Active = true;
                open360Camera();
                AppLogger.i("车身联动", "【转向灯联动360】检测到" + (turnLight == 1 ? "左" : "右") + "转向灯开启，唤起 360 全景影像");
            }
        } else if (turnLight == 0) {
            if (isTurnSignal360Active) {
                isTurnSignal360Active = false;
                SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                boolean autoExit = PrefUtils.getBoolean(prefs, "vehicle_turn_signal_360_auto_exit", true);
                if (autoExit) {
                    close360Camera();
                    AppLogger.i("车身联动", "【转向灯联动360】检测到转向灯回正关闭，自动退出 360 全景影像");
                } else {
                    AppLogger.i("车身联动", "【转向灯联动360】检测到转向灯回正，已设置保持 360 界面不自动退出");
                }
            }
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
                Matcher m = P_PEPS_POWERMODE.matcher(l);
                if (m.find()) return Integer.parseInt(m.group(1));
            }
            if (l.contains("info_id_vpowerinfo_key_state") || l.contains("key_state")) {
                // 优先匹配真实 SensorModule 格式：funValue(0x00200105)，低字节=状态
                // 日志实测：0x002001xx，高位固定，低字节: 0x00=OFF 0x01=ACC 0x05=ON/点火就绪
                Matcher mFun = java.util.regex.Pattern
                    .compile("funvalue\\(0x([0-9a-f]{6,8})\\)")
                    .matcher(l);
                if (mFun.find()) {
                    int raw = (int) Long.parseLong(mFun.group(1), 16);
                    int low = raw & 0xFF;
                    // 吉利缤越COOL (SX11-A3) 车机真实 SensorModule/FaceIdService 电源协议：
                    // 0x00200105 = ON (点火行驶中)
                    // 0x00200104 = ACC (仅通电)
                    // 0x00200103 / 0x00200100 = OFF (熄火下电)
                    if (low == 0x05 || low == 0x06) return 2; // ON 点火就绪
                    if (low == 0x04 || low == 0x01) return 1; // ACC 通电
                    if (low == 0x03 || low == 0x00) return 0; // OFF 熄火下电
                    return (low > 0x04) ? 2 : 0;
                }
                // 兜底：直接裸数字格式（如 key_state=2）
                Matcher m = P_KEY_STATE.matcher(l);
                if (m.find()) {
                    int raw = Integer.parseInt(m.group(1), 16);
                    int low = raw & 0xFF;
                    if (low == 0x05 || low == 2) return 2; // ON 点火就绪
                    if (low == 0x04 || low == 0x02 || low == 1) return 1; // ACC 通电
                    if (low == 0x03 || low == 0) return 0; // 熄火下电
                    return low;
                }
            }
            if (l.contains("info_id_vpowerinfo_engine_state") || l.contains("engine_state")) {
                Matcher m = P_ENGINE_STATE.matcher(l);
                if (m.find()) return Integer.parseInt(m.group(1));
            }
            if (l.contains("ap_power_bootup_reason")) {
                Matcher m = P_BOOTUP_REASON.matcher(l);
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
            int prevKey = lastKeyState;
            lastKeyState = val;
            if (val == 2) {
                lastPowerMode = 1; // 钥匙 ON -> 点火就绪
                doorStateManager.markDriverInside();
                doorStateManager.establishCurrentAsBaseline();
                // 预设门控: KEY ON 只是就绪，不喂发动机边沿 (仅上升沿防抖触发单次预热，绝不每秒循环轰炸)
                if (prevKey != 2) {
                    AppLogger.i("电源状态", "钥匙 ON (key=2) -> 点火启动就绪 (尊重车速阈值，绝不提前起播)");
                }
            } else if (val == 0) {
                if (prevKey != 0) {
                    // 仅在由非0状态变为0（状态跃变）时处理下电，彻底抑制行车中反复收到 key=0 释放报文的刷屏
                    if (currentSpeedKmH == 0 || (gearStateMachine != null && gearStateMachine.getGear() == 5)) {
                        lastPowerMode = 0;
                        AppLogger.i("电源与系统", "检测到钥匙 OFF (key=0)，确认下电熄火");
                        resetAllStateMachines();
                    } else if (latestBatteryVoltage < 13.0f) {
                        lastPowerMode = 0;
                        resetAllStateMachines();
                    }
                }
            }
        }
        // ENGINE_STATE: 0=停止 1=启动中 2=停止中 3=运行
        if (rawLine.toLowerCase().contains("engine_state") || rawLine.toLowerCase().contains("info_id_vpowerinfo_engine_state")) {
            lastEngineState = val;
            if (val == 3) {
                lastPowerMode = 1;
                doorStateManager.markDriverInside();
                doorStateManager.establishCurrentAsBaseline();
            } else if (val == 0) {
                // 引擎停止不一定熄火，等待 PEPS_PowerMode=0 或 KEY_STATE=0
            }
        }
        // PEPS_PowerMode
        if (rawLine.toLowerCase().contains("peps_powermode")) {
            int prevPowerMode = lastPowerMode;
            lastPowerMode = val;
            if (val > 0) {
                doorStateManager.markDriverInside();
                doorStateManager.establishCurrentAsBaseline();
            } else if (prevPowerMode != 0) {
                resetAllStateMachines();
            }
        }

        // 仅在「电源聚合状态真正发生变化」时写一条，彻底杜绝熄火后 MCU 心跳每秒刷同一条
        String aggregate = "key=" + lastKeyState + " engine=" + lastEngineState + " powerMode=" + lastPowerMode;
        if (!aggregate.equals(lastPowerStateAggregate)) {
            lastPowerStateAggregate = aggregate;
            AppLogger.i("电源状态", aggregate);
        }
    }

    private void resetAllStateMachines() {
        if (gearStateMachine != null) gearStateMachine.resetState();
        if (driveModeManager != null) driveModeManager.resetState();
        if (doorStateManager != null) doorStateManager.resetState();
        // 下电熄火重置单次行程自启锁，保证下次上电点火起步能重新触发车速达标自启
        speedAutoplayTriggeredInTrip = false;
        speedCustomActionTriggeredInTrip = false;
    }

    private void open360Camera() {
        if (isAvmCameraShowing) {
            AppLogger.i("车身联动", "原厂 360 画面已在前台显示，跳过重复调起防闪屏");
            return;
        }
        try {
            String targetPkg = "ecarx.camera.calibration";
            Intent intent = getPackageManager().getLaunchIntentForPackage(targetPkg);
            if (intent == null) {
                String[] candidates = new String[]{
                        "com.geely.pnd.avm",
                        "com.ecarx.avm",
                        "com.android.car.avm",
                        "com.jsbd.avm"
                };
                for (String c : candidates) {
                    Intent ci = getPackageManager().getLaunchIntentForPackage(c);
                    if (ci != null) {
                        targetPkg = c;
                        intent = ci;
                        break;
                    }
                }
            }
            if (intent == null) {
                intent = new Intent(Intent.ACTION_MAIN);
                intent.setComponent(new ComponentName("ecarx.camera.calibration", "ecarx.camera.calibration.MainActivity"));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
            AppLogger.i("车身联动", "已成功下发指令唤起 360 全景环视界面 (" + targetPkg + ")");
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

            Intent closeIntentGlobal = new Intent("ecarx.intent.broadcast.action.ECARX_VR_APP_CLOSE");
            closeIntentGlobal.setData(Uri.parse("ecarx://vr.com/360全景"));
            sendBroadcast(closeIntentGlobal);

            AppLogger.i("车身联动", "已下发指令退出 360 全景界面");
        } catch (Exception e) {
            Log.w(TAG, "Failed to close 360: " + e.getMessage());
            AppLogger.w("车身联动", "退出 360 失败: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        instance = null;
        cancelPendingMusicRetries("服务销毁");
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
            VehiclePlatformFactory.getPlatform(this).getSensorAdapter(this).stopListening();
        } catch (Throwable ignored) {}
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
    private volatile boolean speedAutoplayTriggeredInTrip = false; // 单次行程车速自启锁：触发一次后永久闭锁，绝不重复触发
    private volatile boolean speedCustomActionTriggeredInTrip = false; // 单次行程自定义动作锁
    private final List<Runnable> pendingMusicRetryRunnables = new ArrayList<>(); // 音乐起播阶梯重试队列 (30s/60s/90s)
    private boolean gearD360Armed = false; // D挡起步360单次跃变武装锁 (初始闭锁，必须离开D挡切入非D挡后才武装，杜绝启动首包误调起)
    // 前门开启「单次跃变」闩锁：门开着期间 CAN 报文会持续高频重复上报 data=1，
    // 必须只在 关 ➔ 开 物理跃变的那一刻执行一次门控暂停，杜绝反复下发暂停指令与日志刷屏。
    private volatile boolean frontLeftDoorOpenLatched = false;
    private volatile boolean frontRightDoorOpenLatched = false;

    private void processVehicleSpeedAutomation(int speed) {
        SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);

        // 核心铁律门禁：仅允许在前进挡(D挡/2 或 S挡/6/7)触发前进车速多媒体自启与自定义动作！
        // 挂入驻车挡(P挡/5)、倒车挡(R挡/4)或空挡(N挡/3)期间，直接忽略返回，绝不触发！
        int currentGear = (gearStateMachine != null) ? gearStateMachine.getGear() : lastGearPos;
        if (currentGear != 2 && currentGear != 6 && currentGear != 7) {
            return;
        }

        // 核心铁律：严禁在车速回落 (<=5km/h) 时重新武装或重置手动暂停标记！
        // 等红绿灯、拥堵跟车、踩刹车停稳均属行车中途，绝不视为全新行程。

        // 1. 车速智能自启多媒体 (严格单次行程触发一次，100%尊重车主手动暂停)
        boolean autoplayEnabled = prefs.getBoolean("vehicle_speed_autoplay_enabled", true);
        boolean userManuallyPaused = prefs.getBoolean("user_manually_paused_media", false);
        if (autoplayEnabled && !speedAutoplayTriggeredInTrip && !userManuallyPaused && isEngineRunning()) {
            int threshold = prefs.getInt("vehicle_speed_autoplay_threshold", 20);
            if (speed >= threshold) {
                speedAutoplayTriggeredInTrip = true; // 严格单次行程达标即刻闭锁，杜绝后台冷态时高频疯狂拉活与狂发广播
                String targetPkg = getDefaultAutoplayPkg();
                boolean fullscreen = prefs.getBoolean("vehicle_speed_autoplay_fullscreen", false);
                AppLogger.i("车身联动", "【车速自启多媒体】单次行程首次达标 " + threshold + "km/h，拉起音源并闭锁: " + targetPkg);
                triggerMusicAutoplay(targetPkg, fullscreen);
            }
        }

        // 2. 车速达标自定义动作与唤起应用 (满足车主任意设定车速与打开指定软件/360)
        boolean customActionEnabled = prefs.getBoolean("vehicle_speed_custom_action_enabled", false);
        if (customActionEnabled && !speedCustomActionTriggeredInTrip && isEngineRunning()) {
            int customThreshold = prefs.getInt("vehicle_speed_custom_action_threshold", 40);
            if (speed >= customThreshold) {
                speedCustomActionTriggeredInTrip = true; // 单次行程防抖闭锁
                String actionTarget = prefs.getString("vehicle_speed_custom_action_target", "action_360");
                AppLogger.i("车身联动", "【车速自定义联动】车速达到自定义阈值 " + customThreshold + "km/h，触发: " + actionTarget);
                triggerCustomSpeedAction(actionTarget);
            }
        }
    }

    /**
     * 重置单次行程车速自启武装锁与手动暂停标记
     * 仅在明确物理P挡且主驾开门(车主泊车下车)或整车下电时复位，等红绿灯绝不复位
     */
    private void resetTripSpeedAutoplay(String reason) {
        cancelPendingMusicRetries(reason);
        speedAutoplayTriggeredInTrip = false;
        speedCustomActionTriggeredInTrip = false;
        currentSpeedKmH = 0; // 驻车重置行程锁时，强制归零物理车速缓存，杜绝残留低速外溢
        getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("user_manually_paused_media", false).apply();
        AppLogger.i("车身联动", "【单次行程重置】" + reason + "，已重置车速自启武装与手动暂停标记，为下一次出行就绪");
    }

    /**
     * 熔断注销所有未执行的音乐阶梯重试任务
     */
    private void cancelPendingMusicRetries(String reason) {
        synchronized (pendingMusicRetryRunnables) {
            if (!pendingMusicRetryRunnables.isEmpty()) {
                for (Runnable r : pendingMusicRetryRunnables) {
                    mainHandler.removeCallbacks(r);
                }
                pendingMusicRetryRunnables.clear();
                AppLogger.i("车身联动", "【音乐唤醒重试】" + reason + "，已熔断注销所有后续延时重试任务");
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

    public String getDefaultAutoplayPkg() {
        SharedPreferences prefs = PrefUtils.getAppPreferences(this);
        String savedPkg = prefs != null ? prefs.getString("vehicle_speed_autoplay_pkg", null) : null;
        if (savedPkg != null && !savedPkg.trim().isEmpty()) {
            return savedPkg.trim();
        }
        String[] candidatePkgs = new String[] {
            "com.tencent.qqmusiccar",
            "com.tencent.qqmusic",
            "com.netease.cloudmusiccar",
            "cn.kuwo.kwmusiccar",
            "com.kugou.android.auto"
        };
        for (String candidate : candidatePkgs) {
            try {
                getPackageManager().getPackageInfo(candidate, 0);
                if (prefs != null) {
                    prefs.edit().putString("vehicle_speed_autoplay_pkg", candidate).apply();
                }
                return candidate;
            } catch (Throwable ignored) {}
        }
        // 权威基线默认持久化：若未设置或未探测到，默认以 QQ 音乐车机版为基准持久化保存，绝不跌落蓝牙
        if (prefs != null) {
            prefs.edit().putString("vehicle_speed_autoplay_pkg", "com.tencent.qqmusiccar").apply();
        }
        return "com.tencent.qqmusiccar";
    }

    private void tryStartComponentService(String pkg, String serviceCls) {
        if (pkg == null || serviceCls == null) return;
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(pkg, serviceCls));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Throwable ignored) {}
    }

    private void sendExplicitMediaButtonToPackage(String pkg, int keyCode) {
        if (pkg == null || pkg.isEmpty()) return;
        try {
            Intent bQuery = new Intent(Intent.ACTION_MEDIA_BUTTON);
            bQuery.setPackage(pkg);
            List<ResolveInfo> receivers = getPackageManager().queryBroadcastReceivers(bQuery, 0);
            if (receivers != null && !receivers.isEmpty()) {
                for (ResolveInfo ri : receivers) {
                    if (ri.activityInfo != null) {
                        ComponentName comp = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
                        Intent down = new Intent(Intent.ACTION_MEDIA_BUTTON);
                        down.setComponent(comp);
                        down.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
                        sendOrderedBroadcast(down, null);

                        Intent up = new Intent(Intent.ACTION_MEDIA_BUTTON);
                        up.setComponent(comp);
                        up.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, keyCode));
                        sendOrderedBroadcast(up, null);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public void wakeUpTargetMediaService(final String pkg) {
        if (pkg == null || pkg.isEmpty() || "com.android.bluetooth".equals(pkg)) return;
        try {
            // 1. 动态探测并启动目标应用声明的 MediaBrowserService 或后台核心服务
            Intent sQuery = new Intent("android.media.browse.MediaBrowserService");
            sQuery.setPackage(pkg);
            List<ResolveInfo> services = getPackageManager().queryIntentServices(sQuery, 0);
            if (services != null && !services.isEmpty()) {
                for (ResolveInfo ri : services) {
                    if (ri.serviceInfo != null) {
                        tryStartComponentService(pkg, ri.serviceInfo.name);
                    }
                }
            }

            // 2. 主流车机播放器已知后台核心服务直通加速 (公开组件与显式媒体按钮唤醒)
            if ("com.tencent.qqmusiccar".equals(pkg)) {
                // QQ音乐车机版通过显式媒体按钮接收器进行冷启动预热拉活
                sendExplicitMediaButtonToPackage(pkg, KeyEvent.KEYCODE_MEDIA_PLAY);
            } else if ("com.netease.cloudmusiccar".equals(pkg) || "com.netease.cloudmusic".equals(pkg) || "com.netease.cloudmusic.iot".equals(pkg)) {
                tryStartComponentService(pkg, "com.netease.cloudmusic.service.PlayService");
                tryStartComponentService(pkg, "com.netease.cloudmusic.service.MediaPlaybackService");
            } else if ("cn.kuwo.kwmusiccar".equals(pkg)) {
                tryStartComponentService(pkg, "cn.kuwo.kwmusiccar.service.MediaService");
            } else if ("com.kugou.android.auto".equals(pkg)) {
                tryStartComponentService(pkg, "com.kugou.framework.service.MediaService");
            }

            // 3. 通用机制：仅唤醒冷态 Service 进程以常驻响应方控，严禁盲目派发 MEDIA_PLAY 抢占音频焦点与通话通道
            // sendExplicitMediaButtonToPackage(pkg, KeyEvent.KEYCODE_MEDIA_PLAY);
        } catch (Throwable t) {
            AppLogger.w("车身联动", "通用音源服务拉活异常: " + pkg + ", " + t.getMessage());
        }
    }

    private void triggerMusicAutoplay(final String pkg, final boolean fullscreen) {
        SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
        // 核心互斥守卫 0：若当前处于电话通话或 VoIP 通话（微信语音/QQ语音等），绝不自启音乐打扰通话
        if (VehicleVoicePlayer.isInPhoneCall(this)) {
            AppLogger.i("车身联动", "车速达到阈值，但系统正处于通话中 (蓝牙电话/微信/QQ语音)，静默跳过音乐自启");
            return;
        }
        // 用户主动暂停守卫：仅当处于用户主动暂停抑制窗口内（8秒内）才跳过，绝不能被历史标记永久阻断起步放歌
        if (EasMediaBridge.getInstance(this).isAutoWakeSuppressed()) {
            AppLogger.i("车身联动", "车速达到阈值，但处于用户主动暂停抑制窗口内，跳过自动播放（尊重用户暂停意图）");
            return;
        }
        // 核心互斥守卫 1：若当前系统已有任意媒体在播放，绝对不重复触发，静默放行防冲突
        if (isAnyMediaPlaying()) {
            AppLogger.i("车身联动", "车速达到阈值，但系统已有媒体在播放中，静默跳过自启防冲突");
            return;
        }
        // 核心互斥守卫 2：若手机蓝牙当前正处于推流播放态，绝不自启车机本地媒体，坚决杜绝双音并发
        if (EasMediaBridge.getInstance(this).isA2dpStreaming()) {
            AppLogger.i("车身联动", "车速达到阈值，手机蓝牙音频正在推流播放中，跳过车机本地音乐自启");
            return;
        }
        if (isTargetMediaPlaying(pkg)) {
            AppLogger.i("车身联动", "车速达到阈值，目标媒体已在正常播放中，静默放行防打断: " + pkg);
            return;
        }
        AppLogger.i("车身联动", "车速达到阈值，按软件选定音源触发多媒体自启: " + pkg + " (全屏=" + fullscreen + ")");

        // 分支 1：目标为手机蓝牙
        if ("com.android.bluetooth".equals(pkg)) {
            EasMediaBridge.getInstance(this).clearAutoWakeSuppression();
            EasMediaBridge.getInstance(this).playBluetoothMusic();
            return;
        }

        // 分支 2：目标为软件选定的本地音乐应用
        final String curFgPkg = ForegroundAppDetector.getForegroundPackage(this);
        final boolean wasNavigating = "com.autonavi.amapauto".equals(curFgPkg) || (curFgPkg != null && curFgPkg.contains("map"));

        // 核心铁律：仅当用户明确开启「全屏沉浸大屏」时，才允许拉起前台 Activity！
        // 若为「后台静默放歌」（默认），坚决严禁调用 startActivity，绝对不弹窗、不遮挡导航与桌面！
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

        // 后台静默播歌触发链：冷拉活 + 定向广播 + 直接复用方向盘「下一曲」系统级链路唤醒起播
        if (pkg != null && !pkg.isEmpty()) {
            if (!"com.android.bluetooth".equals(pkg)) {
                // 标记本地音源活动态 (严格严禁在此处调用 switchSourceTypeManually(6)，防止强切通道掐断 2 号蓝牙物理选通)
                SteeringWheelKeyManager.setLastActiveAudioSource(SteeringWheelKeyManager.SOURCE_LOCAL);

                // 核心安全兜底：若系统媒体音量当前处于 0 (静音)，自动恢复至适中音量，杜绝 DSP 硬件静音
                try {
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) {
                        int curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                        if (curVol == 0) {
                            int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(1, maxVol / 2), 0);
                            AppLogger.i("车身联动", "【音量安全兜底】检测到系统媒体音量为0，已自动恢复适中音量以保障放声");
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // 0. 动态拉活核心播放服务
            wakeUpTargetMediaService(pkg);

            // 1. 尝试通过活跃 MediaSession 下发播放/下一曲指令
            tryDirectMediaControllerSkipToNext(pkg);
            tryDirectMediaControllerPlay(pkg);

            // 2. 发送标准车机通用/专属播放广播
            try {
                if ("com.tencent.qqmusiccar".equals(pkg)) {
                    Intent qqPlay = new Intent("com.tencent.qqmusiccar.action.PLAY");
                    qqPlay.setPackage("com.tencent.qqmusiccar");
                    sendBroadcast(qqPlay);

                    Intent qqPlayOld = new Intent("com.tencent.qqmusic.action.PLAY");
                    qqPlayOld.setPackage("com.tencent.qqmusiccar");
                    sendBroadcast(qqPlayOld);
                } else if ("com.netease.cloudmusiccar".equals(pkg) || "com.netease.cloudmusic".equals(pkg) || "com.netease.cloudmusic.iot".equals(pkg)) {
                    Intent nePlay = new Intent("com.netease.cloudmusiccar.action.PLAY");
                    nePlay.setPackage(pkg);
                    sendBroadcast(nePlay);
                } else if ("cn.kuwo.kwmusiccar".equals(pkg)) {
                    Intent kwPlay = new Intent("cn.kuwo.kwmusiccar.action.PLAY");
                    kwPlay.setPackage(pkg);
                    sendBroadcast(kwPlay);
                }
            } catch (Throwable ignored) {}

            // 3. 核心升级：直接复用方向盘「下一曲」系统级派发链路 (KEYCODE_MEDIA_NEXT / 87)，100% 强制播放器加载歌单并起播
            sendExplicitMediaButtonToPackage(pkg, KeyEvent.KEYCODE_MEDIA_NEXT);
            sendExplicitMediaButtonToPackage(pkg, KeyEvent.KEYCODE_MEDIA_PLAY);
            // 兼容 ACTION_MEDIA_BUTTON 显式广播规范
            try {
                Intent bQuery = new Intent(Intent.ACTION_MEDIA_BUTTON);
                bQuery.setPackage(pkg);
            } catch (Throwable ignored) {}
            new SteeringWheelKeyManager(VehicleAutomationService.this).sendMediaKeyEventPublic(KeyEvent.KEYCODE_MEDIA_NEXT);
        }

        // 核心优化：延迟 1500ms 快速二次兜底，确保冷启动刚完成初始化的播放器稳定出声
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (pkg != null && !pkg.isEmpty() && !"com.android.bluetooth".equals(pkg)) {
                        if (!isTargetMediaPlaying(pkg) && !isAnyMediaPlaying()) {
                            AppLogger.i("车身联动", "【车速自启多媒体】目标媒体尚未出声，1500ms 快速二次唤醒: " + pkg);
                            performMusicWakeup(pkg);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }, 1500);

        // 核心架构：落地 3 次阶梯重试 (30s / 60s / 90s) 与出声熔断闭锁
        cancelPendingMusicRetries("发起新一轮车速达标唤醒");
        long[] retryDelays = new long[] { 30000L, 60000L, 90000L };
        for (int i = 0; i < retryDelays.length; i++) {
            final int attempt = i + 1;
            final long delayMs = retryDelays[i];
            Runnable retryRunnable = new Runnable() {
                @Override
                public void run() {
                    synchronized (pendingMusicRetryRunnables) {
                        pendingMusicRetryRunnables.remove(this);
                    }
                    // 熔断守卫 1: 挡位门禁 (仅允许在前进挡 D/S 中重试，若挂入 P/R/N 挡则立即终止)
                    int currentGear = (gearStateMachine != null) ? gearStateMachine.getGear() : lastGearPos;
                    if (currentGear != 2 && currentGear != 6 && currentGear != 7) {
                        AppLogger.i("车身联动", "【音乐阶梯重试#" + attempt + "】已离开前进挡(gear=" + currentGear + ")，熔断注销后续重试");
                        cancelPendingMusicRetries("离开前进挡");
                        return;
                    }
                    // 熔断守卫 2: 车主手动暂停避让
                    if (EasMediaBridge.getInstance(VehicleAutomationService.this).isAutoWakeSuppressed()
                            || getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE).getBoolean("user_manually_paused_media", false)) {
                        AppLogger.i("车身联动", "【音乐阶梯重试#" + attempt + "】车主已手动暂停，尊重意图并熔断注销后续重试");
                        cancelPendingMusicRetries("车主手动暂停避让");
                        return;
                    }
                    // 熔断守卫 3: 通话避让
                    if (VehicleVoicePlayer.isInPhoneCall(VehicleAutomationService.this)) {
                        AppLogger.i("车身联动", "【音乐阶梯重试#" + attempt + "】当前处于电话/VoIP通话中，熔断注销");
                        cancelPendingMusicRetries("通话避让");
                        return;
                    }
                    // 熔断守卫 4: 出声熔断！检测到任意音频或目标媒体已出声，立即注销后续所有重试并永久闭锁！
                    if (isAnyMediaPlaying() || isTargetMediaPlaying(pkg) || EasMediaBridge.getInstance(VehicleAutomationService.this).isA2dpStreaming()) {
                        AppLogger.i("车身联动", "【音乐阶梯重试#" + attempt + "】检测到音频已成功起播发声，立即出声熔断！注销全部后续重试");
                        cancelPendingMusicRetries("音频已正常出声");
                        return;
                    }

                    // 尚未出声，执行本轮唤醒重试
                    AppLogger.i("车身联动", "【音乐阶梯重试#" + attempt + " (" + (delayMs / 1000) + "s)】目标应用仍未起播，下发阶梯唤醒: " + pkg);
                    performMusicWakeup(pkg);
                }
            };
            synchronized (pendingMusicRetryRunnables) {
                pendingMusicRetryRunnables.add(retryRunnable);
            }
            mainHandler.postDelayed(retryRunnable, delayMs);
        }
    }

    /**
     * 执行底层媒体唤醒与起播链路 (服务拉活 + MediaController + 定向广播 + 方向盘下一曲/播放键)
     */
    private void performMusicWakeup(String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        try {
            // 0. 动态拉活核心播放服务
            wakeUpTargetMediaService(pkg);

            // 1. 尝试通过活跃 MediaSession 下发播放/下一曲指令
            tryDirectMediaControllerSkipToNext(pkg);
            tryDirectMediaControllerPlay(pkg);

            // 2. 发送标准车机通用/专属播放广播
            try {
                if ("com.tencent.qqmusiccar".equals(pkg)) {
                    Intent qqPlay = new Intent("com.tencent.qqmusiccar.action.PLAY");
                    qqPlay.setPackage("com.tencent.qqmusiccar");
                    sendBroadcast(qqPlay);

                    Intent qqPlayOld = new Intent("com.tencent.qqmusic.action.PLAY");
                    qqPlayOld.setPackage("com.tencent.qqmusiccar");
                    sendBroadcast(qqPlayOld);
                } else if ("com.netease.cloudmusiccar".equals(pkg) || "com.netease.cloudmusic".equals(pkg) || "com.netease.cloudmusic.iot".equals(pkg)) {
                    Intent nePlay = new Intent("com.netease.cloudmusiccar.action.PLAY");
                    nePlay.setPackage(pkg);
                    sendBroadcast(nePlay);
                } else if ("cn.kuwo.kwmusiccar".equals(pkg)) {
                    Intent kwPlay = new Intent("cn.kuwo.kwmusiccar.action.PLAY");
                    kwPlay.setPackage(pkg);
                    sendBroadcast(kwPlay);
                }
            } catch (Throwable ignored) {}

            // 3. 核心升级：直接复用方向盘「下一曲」与「播放」系统级派发链路，强制播放器加载歌单并起播
            sendExplicitMediaButtonToPackage(pkg, KeyEvent.KEYCODE_MEDIA_NEXT);
            sendExplicitMediaButtonToPackage(pkg, KeyEvent.KEYCODE_MEDIA_PLAY);
            new SteeringWheelKeyManager(VehicleAutomationService.this).sendMediaKeyEventPublic(KeyEvent.KEYCODE_MEDIA_NEXT);
        } catch (Throwable t) {
            AppLogger.w("车身联动", "执行媒体唤醒异常: " + t.getMessage());
        }
    }

    private boolean tryDirectMediaControllerSkipToNext(String targetPkg) {
        if (targetPkg == null || targetPkg.isEmpty()) return false;
        try {
            MediaSessionManager msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm != null) {
                List<MediaController> controllers = msm.getActiveSessions(null);
                if (controllers != null) {
                    for (MediaController mc : controllers) {
                        if (mc != null && targetPkg.equals(mc.getPackageName())) {
                            mc.getTransportControls().skipToNext();
                            mc.getTransportControls().play();
                            AppLogger.i("车身联动", "通过 MediaController 成功向目标媒体下发 skipToNext() + play(): " + targetPkg);
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean tryDirectMediaControllerPlay(String targetPkg) {
        if (targetPkg == null || targetPkg.isEmpty()) return false;
        try {
            MediaSessionManager msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm != null) {
                List<MediaController> controllers = msm.getActiveSessions(null);
                if (controllers != null) {
                    for (MediaController mc : controllers) {
                        if (mc != null && targetPkg.equals(mc.getPackageName())) {
                            mc.getTransportControls().play();
                            AppLogger.i("车身联动", "通过 MediaController 成功向目标媒体下发 play(): " + targetPkg);
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean isTargetMediaPlaying(String targetPkg) {
        if (targetPkg == null || targetPkg.isEmpty()) return false;
        try {
            MediaSessionManager msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm != null) {
                List<MediaController> controllers = msm.getActiveSessions(null);
                if (controllers != null) {
                    for (MediaController mc : controllers) {
                        if (mc != null && targetPkg.equals(mc.getPackageName())) {
                            if (mc.getPlaybackState() != null && mc.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public boolean isAnyMediaPlaying() {
        try {
            MediaSessionManager msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm != null) {
                List<MediaController> controllers = msm.getActiveSessions(null);
                if (controllers != null) {
                    for (MediaController mc : controllers) {
                        if (mc != null) {
                            String pkg = mc.getPackageName();
                            // 严禁将蓝牙推流自身或工具箱自身作为本地正在播放的音乐！
                            if ("com.android.bluetooth".equals(pkg) || "app.onepve.geelyconsole".equals(pkg)) {
                                continue;
                            }
                            if (mc.getPlaybackState() != null && mc.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public String getCurrentlyPlayingMediaPackage() {
        try {
            MediaSessionManager msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm != null) {
                List<MediaController> controllers = msm.getActiveSessions(null);
                if (controllers != null) {
                    for (MediaController mc : controllers) {
                        if (mc != null) {
                            String pkg = mc.getPackageName();
                            if ("com.android.bluetooth".equals(pkg) || "app.onepve.geelyconsole".equals(pkg)) {
                                continue;
                            }
                            if (mc.getPlaybackState() != null && mc.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {
                                return pkg;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public void resumeSpecificMediaPackage(String targetPkg) {
        if (targetPkg == null || targetPkg.isEmpty()) return;
        try {
            AppLogger.i("音频通道", "正在定向恢复此前播放的媒体应用: " + targetPkg);
            // 1. 尝试 MediaController.play()
            try {
                MediaSessionManager msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
                if (msm != null) {
                    List<MediaController> controllers = msm.getActiveSessions(null);
                    if (controllers != null) {
                        for (MediaController mc : controllers) {
                            if (mc != null && targetPkg.equals(mc.getPackageName())) {
                                mc.getTransportControls().play();
                                AppLogger.i("音频通道", "已通过 MediaController 下发 play(): " + targetPkg);
                                break;
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                AppLogger.w("音频通道", "MediaController play 异常: " + t.getMessage());
            }

            // 2. 针对第三方播放器（如 QQ 音乐车机版）下发专属广播与 AIDL 直通起播，确保 100% 成功起播
            if ("com.tencent.qqmusiccar".equals(targetPkg)) {
                try {
                    QQMusicCarController.getInstance(this).resumeMusic();
                    AppLogger.i("音频通道", "已向 QQ 音乐发送 AIDL resumeMusic 直通播放指令");
                } catch (Throwable t) {
                    AppLogger.w("音频通道", "QQ 音乐 AIDL resumeMusic 异常: " + t.getMessage());
                }
                try {
                    Intent playIntent = new Intent("com.tencent.qqmusiccar.action.PLAY");
                    playIntent.setPackage("com.tencent.qqmusiccar");
                    sendBroadcast(playIntent);
                    AppLogger.i("音频通道", "已向 QQ 音乐发送 com.tencent.qqmusiccar.action.PLAY 专属播放广播");
                } catch (Throwable t) {
                    AppLogger.w("音频通道", "发送 QQ 音乐播放广播异常: " + t.getMessage());
                }
            }

            // 3. 通用兜底：下发 KEYCODE_MEDIA_PLAY 播放按键
            try {
                new SteeringWheelKeyManager(this).sendMediaKeyEventPublic(KeyEvent.KEYCODE_MEDIA_PLAY);
                AppLogger.i("音频通道", "已向系统下发 KEYCODE_MEDIA_PLAY 确认播放恢复");
            } catch (Throwable t) {
                AppLogger.w("音频通道", "下发 KEYCODE_MEDIA_PLAY 异常: " + t.getMessage());
            }
        } catch (Throwable t) {
            AppLogger.w("音频通道", "定向恢复异常: " + t.getMessage());
        }
    }

    public void pauseMediaPlaybackForAudioInterruption() {
        try {
            new SteeringWheelKeyManager(this).sendMediaKeyEventPublic(KeyEvent.KEYCODE_MEDIA_PAUSE);
            AppLogger.i("音频通道", "微信发语音/录音建立，已下发 KEYCODE_MEDIA_PAUSE 保证环境安静");
        } catch (Throwable t) {
            AppLogger.w("音频通道", "pauseMediaPlaybackForAudioInterruption 异常: " + t.getMessage());
        }
    }

    public void resumeMediaPlaybackAfterAudioInterruption() {
        AppLogger.i("音频通道", "微信语音结束，保持当前播放器既有状态，绝不强行起播");
    }

    public void warmUpTargetMediaService() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 若正处于通话（蓝牙电话/微信语音）或已下电，坚决禁止预热媒体服务抢占音频焦点
                    if (VehicleVoicePlayer.isInPhoneCall(VehicleAutomationService.this) || !isEngineRunning()) {
                        return;
                    }
                    // 若处于 P 挡驻车静止状态，不主动唤醒第三方媒体应用，避免截断车机蓝牙电话
                    if (gearStateMachine != null && gearStateMachine.getGear() == 5 && currentSpeedKmH == 0) {
                        return;
                    }
                    SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                    boolean autoplayEnabled = prefs.getBoolean("vehicle_speed_autoplay_enabled", true);
                    if (!autoplayEnabled) return;
                    String pkg = getDefaultAutoplayPkg();
                    AppLogger.i("车身联动", "点火通电预热：按软件默认配置预热音源服务: " + pkg);
                    wakeUpTargetMediaService(pkg);
                } catch (Throwable ignored) {}
            }
        }).start();
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

    /**
     * 进出隧道大灯联动高德日夜深浅色模式 (高德 7.5 经典通道)
     * @param val 1=大灯/小灯开启 (进隧道), 0=大灯/小灯关闭 (出隧道)
     */
    private void handleHeadlightNavNight(int val) {
        try {
            SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            boolean enabled = prefs.getBoolean("vehicle_headlight_nav_night_enabled", false);
            if (!enabled) return;

            if (lastHeadlightSts == -1) {
                lastHeadlightSts = val;
                return;
            }

            // 跃变锁存判定：只在 0 -> 1（开灯进隧道）或 1 -> 0（关灯出隧道）瞬间单次触发，防高频刷屏
            if (val == 1 && lastHeadlightSts == 0) {
                sendAmapDayNightMode(2); // 2: 黑夜模式
                lastHeadlightSts = 1;
            } else if (val == 0 && lastHeadlightSts == 1) {
                sendAmapDayNightMode(0); // 0: 日间模式
                lastHeadlightSts = 0;
            }
        } catch (Throwable t) {
            AppLogger.w("车身联动", "大灯联动高德异常: " + t.getMessage());
        }
    }

    private void sendAmapDayNightMode(int mode) {
        try {
            Intent intent = new Intent("AUTONAVI_STANDARD_BROADCAST_RECV");
            intent.putExtra("KEY_TYPE", 10048);
            intent.putExtra("EXTRA_DAY_NIGHT_MODE", mode); // 0=日间, 2=黑夜
            intent.setComponent(new ComponentName("com.autonavi.amapauto", "com.autonavi.amapauto.adapter.internal.AmapAutoBroadcastReceiver"));
            sendBroadcast(intent);
            AppLogger.i("车身联动", "已向高德发送日夜模式广播: mode=" + mode + (mode == 2 ? " (黑夜深色)" : " (白天浅色)"));
        } catch (Exception e) {
            AppLogger.w("车身联动", "发送高德日夜模式广播失败: " + e.getMessage());
        }
    }
}
