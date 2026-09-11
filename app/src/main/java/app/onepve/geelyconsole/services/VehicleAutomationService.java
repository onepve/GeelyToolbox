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
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.onepve.geelyconsole.R;
import app.onepve.geelyconsole.utils.AdbClient;
import app.onepve.geelyconsole.utils.AppLogger;
import app.onepve.geelyconsole.utils.DoorStateManager;
import app.onepve.geelyconsole.utils.DriveModeManager;
import app.onepve.geelyconsole.utils.GearStateMachine;
import app.onepve.geelyconsole.utils.IdleScreensaverManager;
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
    private boolean enableTurn360 = false;
    private boolean enableLightNav = false;
    private boolean enableFlameoutVoice = false;

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
    private int lastPowerMode = -1;
    private int lastKeyState = -1;
    private int lastEngineState = -1;
    public static volatile int currentSpeedKmH = 0;
    private boolean is360OpenedByTurn = false;

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
            boolean gearN = prefs.getBoolean("voice_enable_gear_n", true);
            boolean modeSmart = prefs.getBoolean("voice_enable_mode_smart", true);
            boolean modeComfort = prefs.getBoolean("voice_enable_mode_comfort", true);
            boolean modeEco = prefs.getBoolean("voice_enable_mode_eco", true);
            boolean modeSport = prefs.getBoolean("voice_enable_mode_sport", true);
            boolean turn360 = prefs.getBoolean("vehicle_turn_360_enabled", false);
            boolean lightNav = prefs.getBoolean("vehicle_light_nav_enabled", false);
            boolean flameout = prefs.getBoolean("vehicle_flameout_voice_enabled", false);
            String wheelMode = prefs.getString("wheel_control_mode", SteeringWheelKeyManager.MODE_CARMEDIA_FIRST);
            boolean wheelEnabled = wheelMaster && !SteeringWheelKeyManager.MODE_FACTORY_DEFAULT.equals(wheelMode);

            boolean anyVoiceEnabled = voiceMaster && (doorFl || doorFlClose || doorFr || doorFrClose ||
                                doorRl || doorRlClose || doorRr || doorRrClose || doorRear ||
                                trunkOpen || trunkClose || gearD || gearR || gearP || gearN ||
                                modeSmart || modeComfort || modeEco || modeSport || turn360 ||
                                lightNav || flameout);

            boolean shouldRun = anyVoiceEnabled || wheelEnabled
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
        voicePlayer = VehicleVoicePlayer.getInstance(this);
        wheelKeyManager = new SteeringWheelKeyManager(this);

        // 初始化四大独立状态机
        doorStateManager = new DoorStateManager(this, voicePlayer);
        doorStateManager.setListener((fl, fr, rl, rr) -> {
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
        enableGearN = prefs.getBoolean("voice_enable_gear_n", true);
        enableGearS = prefs.getBoolean("voice_enable_gear_s", true);
        enableModeSmart = prefs.getBoolean("voice_enable_mode_smart", true);
        enableModeComfort = prefs.getBoolean("voice_enable_mode_comfort", true);
        enableModeEco = prefs.getBoolean("voice_enable_mode_eco", true);
        enableModeSport = prefs.getBoolean("voice_enable_mode_sport", true);
        enableTurn360 = prefs.getBoolean("vehicle_turn_360_enabled", false);
        enableLightNav = prefs.getBoolean("vehicle_light_nav_enabled", false);
        enableFlameoutVoice = prefs.getBoolean("vehicle_flameout_voice_enabled", false);

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
                             enableTurn360 || enableLightNav || enableFlameoutVoice);

        boolean anyEnabled = anyVoiceEnabled || wheelEnabled;

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
        if (line.contains("getVehicleSpeed") || line.contains("speed ==") || line.contains("speed=")) {
            int idx = line.indexOf("speed ==");
            if (idx == -1) idx = line.indexOf("speed=");
            if (idx != -1) {
                try {
                    String sub = line.substring(idx + (line.contains("speed ==") ? 8 : 6)).trim();
                    StringBuilder num = new StringBuilder();
                    for (int i = 0; i < sub.length(); i++) {
                        char c = sub.charAt(i);
                        if (Character.isDigit(c)) num.append(c);
                        else if (num.length() > 0) break;
                    }
                    if (num.length() > 0) {
                        currentSpeedKmH = Integer.parseInt(num.toString());
                    }
                } catch (Exception ignored) {}
            }
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
                        if (rawHex == 0x05 || rawHex == 0x14) gearVal = 5;
                        else if (rawHex == 0x04 || rawHex == 0x13) gearVal = 4;
                        else if (rawHex == 0x03 || rawHex == 0x12) gearVal = 3;
                        else if (rawHex == 0x02 || rawHex == 0x11) gearVal = 2;
                        else if (rawHex == 0x06 || rawHex == 0x15 || rawHex == 0x16) gearVal = 6;
                        else gearVal = rawHex;
                    }
                } catch (Exception ignored) {}
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
        if (gearVal <= 0 && (line.contains("VDRIVEINFO_GEAR_POSITION") || line.contains("mModelGearPos") || line.contains("GearPos ="))) {
            try {
                Matcher gm = Pattern.compile("(?:funValue|mModelGearPos|VDRIVEINFO_GEAR_POSITION|GearPos)[^0-9a-fA-F]*(?:0x)?([0-9a-fA-F]+)").matcher(line);
                if (gm.find()) {
                    int raw = Integer.parseInt(gm.group(1), 16);
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

        // 4.1 解析驾驶模式切换信号 (涵盖 AdaptAPI 9位常量、ComfortModule、MCULog 及 CarSettingLogs)
        int modeVal = -1;

        // 优先 1: AdaptAPI 9位全局权威常量 (100% 准确、零歧义)
        if (line.contains("570491138")) {
            modeVal = MODE_COMFORT; // 舒适模式 (DRIVE_MODE_SELECTION_COMFORT = 570491138)
        } else if (line.contains("570491139")) {
            modeVal = MODE_SPORT;   // 运动模式 (DRIVE_MODE_SELECTION_DYNAMIC = 570491139)
        } else if (line.contains("570491137")) {
            modeVal = MODE_ECO;     // 经济模式 (DRIVE_MODE_SELECTION_ECO = 570491137)
        } else if (line.contains("570491158")) {
            modeVal = MODE_SMART;   // 智能模式 (DRIVE_MODE_SELECTION_ADAPTIVE = 570491158)
        }
        // 优先 2: ComfortModule 上报 (DM_FUNC_DRIVE_MODE_SELECT value=X)
        else if (line.contains("DM_FUNC_DRIVE_MODE_SELECT")) {
            try {
                Matcher m = Pattern.compile("DM_FUNC_DRIVE_MODE_SELECT[\\s:]+value[=:\\s]+(\\d+)").matcher(line);
                if (m.find()) {
                    int val = Integer.parseInt(m.group(1));
                    if (val == 1) modeVal = MODE_COMFORT;
                    else if (val == 2) modeVal = MODE_SPORT;
                    else if (val == 3) modeVal = MODE_ECO;
                    else if (val == 6) modeVal = MODE_SMART;
                }
            } catch (Exception ignored) {}
        }
        // 优先 3: MCU 底盘按键上报 (MCU Report SwitchMode: X)
        // 吉利缤越 COOL SX11-A3 实体按键映射真实对应:
        // 0 -> 经济模式 (MODE_ECO)
        // 1 -> 舒适模式 (MODE_COMFORT)
        // 2 -> 运动模式 (MODE_SPORT)
        // 6 -> 智能模式 (MODE_SMART)
        else if (line.contains("SwitchMode:")) {
            try {
                Matcher m = Pattern.compile("SwitchMode:\\s*(\\d+)").matcher(line);
                if (m.find()) {
                    int sm = Integer.parseInt(m.group(1));
                    if (sm == 1) modeVal = MODE_COMFORT;
                    else if (sm == 0) modeVal = MODE_ECO;
                    else if (sm == 2) modeVal = MODE_SPORT;
                    else if (sm == 6) modeVal = MODE_SMART;
                }
            } catch (Exception ignored) {}
        }
        // 优先 4: MCU TargetMode 与 CarConfig DirveMode
        else if (line.contains("TargetMode:") || line.contains("DirveMode =")) {
            try {
                Matcher m = Pattern.compile("(?:TargetMode:|DirveMode\\s*=)\\s*(\\d+)").matcher(line);
                if (m.find()) {
                    int tm = Integer.parseInt(m.group(1));
                    if (tm == 1 || tm == 3) modeVal = MODE_COMFORT;
                    else if (tm == 2 || tm == 0) modeVal = MODE_ECO;
                    else if (tm == 6) modeVal = MODE_SMART;
                    else if (tm == 4 || tm == 5) modeVal = MODE_SPORT;
                }
            } catch (Exception ignored) {}
        }
        // 优先 5: 原厂核心服务 ecarx_core_server 与 SensorModule (mModelDriverMode / mModelDriveMode / VDRIVEINFO_DRIVER_MODE)
        else if (line.contains("mModelDriverMode") || line.contains("mModelDriveMode") || line.contains("VDRIVEINFO_DRIVER_MODE") || line.contains("DriverMode =") || line.contains("DriveMode =")) {
            try {
                Matcher dm = Pattern.compile("(?:funValue|mModelDriverMode|mModelDriveMode|VDRIVEINFO_DRIVER_MODE|DriverMode|DriveMode)[^0-9a-fA-F]*(?:0x)?([0-9a-fA-F]+)").matcher(line);
                if (dm.find()) {
                    int raw = Integer.parseInt(dm.group(1), 16);
                    int dVal = raw & 0xFF;
                    if (dVal == 1) modeVal = MODE_COMFORT;
                    else if (dVal == 2) modeVal = MODE_SPORT;
                    else if (dVal == 3) modeVal = MODE_ECO;
                    else if (dVal == 4 || dVal == 6) modeVal = MODE_SMART;
                }
            } catch (Exception ignored) {}
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

                        // 只有在 9.0V ~ 16.5V 车规安全范围内才更新并物理落地，坚决杜绝 4.2V 等假信号污染
                        if (volt >= 9.0f && volt <= 16.5f) {
                            latestBatteryVoltage = volt;
                            SharedPreferences sp = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                            sp.edit().putFloat("vehicle_real_battery_volt", volt).commit();
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
        // 2. 发电机高压充电权威信号：若电压稳稳 ≥13.2V，发电机必然在转，100% 确认运行
        if (latestBatteryVoltage >= 13.2f) return true;
        // 3. 车速非零：车辆行驶移动中，100% 确认运行
        if (currentSpeedKmH > 0) return true;
        // 4. 明确检测到电源点火处于就绪状态 (lastPowerMode > 0)
        if (lastPowerMode > 0) return true;
        // 5. 初始未定态 (lastPowerMode == -1)：除极度亏电浅休眠 (9.0V~11.5V) 且零车速外，默认放行
        //    内置有人感知状态机（P挡静眠/智能模式静眠）天然具备开机防误播防线，绝不漏报点火换挡与模式切换
        if (latestBatteryVoltage >= 9.0f && latestBatteryVoltage < 11.5f && currentSpeedKmH == 0) {
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
    }

    /**
     * 处理驾驶模式切换状态 (智能有人感知状态机：默认智能模式静默，手动切出智能模式后激活全量播报，切回智能模式播报后置0归位)
     * 模式枚举: MODE_COMFORT=1, MODE_SPORT=2, MODE_ECO=3, MODE_SMART=4 (全局解耦，零错位)
     */
    private void handleDriveModeSignal(int mode) {
        if (!isEngineRunning()) {
            // 熄火断电/浅待机：强制重置状态机，绝对静音！
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
        // 转向灯联动 360 全景影像 (严格车速过滤 <=30km/h)
        if (enableTurn360 && "TCM_Req_TurnIndicationAct".equals(key)) {
            if (val == 1 || val == 2) { // 1 左转, 2 右转
                if (currentSpeedKmH <= 30) {
                    open360Camera();
                    is360OpenedByTurn = true;
                } else {
                    Log.d(TAG, "车速 " + currentSpeedKmH + " > 30km/h，已自动静默抑制 360 唤起以保护导航画面");
                }
            } else if (val == 0) { // 转向灯回正复位
                if (is360OpenedByTurn) {
                    close360Camera();
                    is360OpenedByTurn = false;
                }
            }
        }

        // 大灯联动高德日夜模式
        if (enableLightNav && "BCM_PositionLightSts".equals(key)) {
            if (lastLightSts == -1) {
                lastLightSts = val;
                return;
            }
            if (val == 1 && lastLightSts == 0) {
                sendAmapDayNightMode(2); // 黑夜模式
                lastLightSts = 1;
            } else if (val == 0 && lastLightSts == 1) {
                sendAmapDayNightMode(0); // 日间模式
                lastLightSts = 0;
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
                lastPowerMode = 1; // 明确标记上电点火就绪
            }
            return;
        }

        // KEY_STATE: 0=关 1=ACC 2=ON (严格限定为电源总线信号，避免普通硬件按键释放报文污染)
        if (rawLine.toLowerCase().contains("info_id_vpowerinfo_key_state") || (rawLine.toLowerCase().contains("key_state") && (rawLine.toLowerCase().contains("power") || rawLine.toLowerCase().contains("peps")))) {
            lastKeyState = val;
            if (val == 2) {
                lastPowerMode = 1; // ON 视为点火就绪
                doorStateManager.markDriverInside();
                AppLogger.i("电源状态", "钥匙 ON -> 主驾已就坐基准建立");
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
        if (flameout && enableFlameoutVoice && voicePlayer != null) {
            voicePlayer.play("flameout.mp3", "车辆已熄火，请带好随身物品");
        }
    }

    private void open360Camera() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("ecarx.camera.calibration");
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(intent);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to launch 360: " + e.getMessage());
        }
    }

    private void close360Camera() {
        try {
            Intent closeIntent = new Intent("ecarx.intent.broadcast.action.ECARX_VR_APP_CLOSE");
            closeIntent.setData(Uri.parse("ecarx://vr.com/360全景"));
            closeIntent.setPackage("ecarx.camera.calibration");
            sendBroadcast(closeIntent);
        } catch (Exception e) {
            Log.w(TAG, "Failed to close 360: " + e.getMessage());
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
        Log.i(TAG, "VehicleAutomationService stopped");
    }
}
