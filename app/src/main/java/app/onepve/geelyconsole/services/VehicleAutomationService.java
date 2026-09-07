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
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.onepve.geelyconsole.R;
import app.onepve.geelyconsole.utils.AdbClient;
import app.onepve.geelyconsole.utils.BluetoothAudioRouter;
import app.onepve.geelyconsole.utils.SteeringWheelKeyManager;
import app.onepve.geelyconsole.utils.SystemUtils;
import app.onepve.geelyconsole.utils.UsbMediaManager;
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
    private boolean enableBluetoothRouter = false;
    private boolean enableUsbMedia = false;

    // 驾驶模式标准解耦枚举 (100% 根绝底层各协议数值冲突)
    public static final int MODE_COMFORT = 1; // 舒适模式
    public static final int MODE_SPORT   = 2; // 运动模式
    public static final int MODE_ECO     = 3; // 经济模式
    public static final int MODE_SMART   = 4; // 智能模式

    // 状态记录变量 (初始化为 -1，首包仅校准基准绝不误播)
    private int lastDoorFL = -1;
    private int lastDoorFR = -1;
    private int lastDoorRL = -1;
    private int lastDoorRR = -1;
    private int lastTrunk = -1;
    private int lastGearPos = -1;
    private int isGearVoiceArmed = 0; // 0=未激活(P挡初始/静默), 1=已手动换出P挡(有人操控，激活播报)
    private int lastDriveMode = -1;
    private int isDriveModeVoiceArmed = 0; // 0=未激活(智能模式初始/静默), 1=已手动切出智能模式(有人操控，激活播报)
    private int lastLightSts = -1;
    private int lastPowerMode = -1;
    private int currentSpeedKmH = 0;
    private boolean is360OpenedByTurn = false;

    // 同状态接触微抖动过滤 (毫秒)
    private long lastTriggerFL = 0;
    private long lastTriggerFR = 0;
    private long lastTriggerRL = 0;
    private long lastTriggerRR = 0;
    private long lastTriggerTrunk = 0;
    private long lastTriggerGear = 0;

    private Thread logcatThread;
    private Process logcatProcess;
    private VehicleVoicePlayer voicePlayer;
    private SteeringWheelKeyManager wheelKeyManager;
    private BluetoothAudioRouter btAudioRouter;
    private UsbMediaManager usbMediaManager;
    private BroadcastReceiver powerReceiver;

    public static void syncState(Context context) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            boolean doorFl = prefs.getBoolean("voice_enable_door_fl", true);
            boolean doorFlClose = prefs.getBoolean("voice_enable_door_fl_close", true);
            boolean doorFr = prefs.getBoolean("voice_enable_door_fr", false);
            boolean doorFrClose = prefs.getBoolean("voice_enable_door_fr_close", false);
            boolean doorRl = prefs.getBoolean("voice_enable_door_rl", false);
            boolean doorRlClose = prefs.getBoolean("voice_enable_door_rl_close", false);
            boolean doorRr = prefs.getBoolean("voice_enable_door_rr", false);
            boolean doorRrClose = prefs.getBoolean("voice_enable_door_rr_close", false);
            boolean doorRear = prefs.getBoolean("voice_enable_door_rear", false);
            boolean trunkOpen = prefs.getBoolean("voice_enable_trunk_open", false);
            boolean trunkClose = prefs.getBoolean("voice_enable_trunk_close", false);
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
            boolean btRouter = prefs.getBoolean("bt_audio_auto_route", true);
            boolean usbMedia = prefs.getBoolean("usb_media_auto_detect", true);
            String wheelMode = prefs.getString("wheel_control_mode", SteeringWheelKeyManager.MODE_CARMEDIA_FIRST);
            boolean wheelEnabled = !SteeringWheelKeyManager.MODE_FACTORY_DEFAULT.equals(wheelMode);

            boolean shouldRun = doorFl || doorFlClose || doorFr || doorFrClose ||
                                doorRl || doorRlClose || doorRr || doorRrClose || doorRear ||
                                trunkOpen || trunkClose || gearD || gearR || gearP || gearN ||
                                modeSmart || modeComfort || modeEco || modeSport || turn360 ||
                                lightNav || flameout || btRouter || usbMedia || wheelEnabled;

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
        btAudioRouter = new BluetoothAudioRouter(this);
        usbMediaManager = new UsbMediaManager(this);

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
        if (enableBluetoothRouter) {
            btAudioRouter.start();
        }
        if (enableUsbMedia) {
            usbMediaManager.start();
        }

        registerPowerStateReceiver();
        startLogcatReader();
        Log.i(TAG, "VehicleAutomationService started successfully");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        reloadPreferences();
        wheelKeyManager.syncMediaKeyReceiverState();
        if (enableBluetoothRouter) {
            btAudioRouter.start();
        } else {
            btAudioRouter.stop();
        }
        if (enableUsbMedia) {
            usbMediaManager.start();
        } else {
            usbMediaManager.stop();
        }
        return START_STICKY;
    }

    private void reloadPreferences() {
        SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
        enableDoorFl = prefs.getBoolean("voice_enable_door_fl", true);
        enableDoorFlClose = prefs.getBoolean("voice_enable_door_fl_close", true);
        enableDoorFr = prefs.getBoolean("voice_enable_door_fr", false);
        enableDoorFrClose = prefs.getBoolean("voice_enable_door_fr_close", false);
        enableDoorRl = prefs.getBoolean("voice_enable_door_rl", false);
        enableDoorRlClose = prefs.getBoolean("voice_enable_door_rl_close", false);
        enableDoorRr = prefs.getBoolean("voice_enable_door_rr", false);
        enableDoorRrClose = prefs.getBoolean("voice_enable_door_rr_close", false);
        enableDoorRear = prefs.getBoolean("voice_enable_door_rear", false);
        enableTrunkOpen = prefs.getBoolean("voice_enable_trunk_open", false);
        enableTrunkClose = prefs.getBoolean("voice_enable_trunk_close", false);
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
        enableBluetoothRouter = prefs.getBoolean("bt_audio_auto_route", true);
        enableUsbMedia = prefs.getBoolean("usb_media_auto_detect", true);

        String wheelMode = prefs.getString("wheel_control_mode", SteeringWheelKeyManager.MODE_CARMEDIA_FIRST);
        boolean wheelEnabled = !SteeringWheelKeyManager.MODE_FACTORY_DEFAULT.equals(wheelMode);

        boolean anyEnabled = enableDoorFl || enableDoorFlClose || enableDoorFr || enableDoorFrClose ||
                             enableDoorRl || enableDoorRlClose || enableDoorRr || enableDoorRrClose || enableDoorRear ||
                             enableTrunkOpen || enableTrunkClose || enableGearD || enableGearR || enableGearP || enableGearN ||
                             enableModeSmart || enableModeComfort || enableModeEco || enableModeSport ||
                             enableTurn360 || enableLightNav || enableFlameoutVoice || enableBluetoothRouter || enableUsbMedia || wheelEnabled;

        if (!anyEnabled) {
            stopSelf();
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
                if (Intent.ACTION_SHUTDOWN.equals(action) ||
                        "android.intent.action.QUICKBOOT_POWEROFF".equals(action) ||
                        "com.ecarx.intent.action.ECARX_SHUTDOWN".equals(action)) {
                    if (enableFlameoutVoice && voicePlayer != null) {
                        voicePlayer.play("flameout.mp3", "车辆已熄火，请带好随身物品");
                    }
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
            Pattern.compile("91\\s+02\\s+01\\s+[0-9a-fA-F]{2}\\s+[0-9a-fA-F]{2}\\s+[0-9a-fA-F]{2}\\s+([0-9a-fA-F]{2})\\s+([0-9a-fA-F]{2})");

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
                        // 2. SerialControl_v2_0 (MCU 物理串口报文 91 02 01)
                        // 3. ECARX@ECP (MCU 物理挡位报文 GearPosition)
                        // 4. InputManager / CAR.INPUT (方向盘按键报文)
                        // -T 1 强制仅从当前最新开始实时监听，绝不回放旧日志
                        ProcessBuilder pb = new ProcessBuilder("logcat", "-T", "1", "-b", "all", "-v", "brief",
                                "-s", "VehicleDataBuilder:D", "SerialControl_v2_0:W", "ECARX@ECP:D", "InputManager:V", "CAR.INPUT:V", "ecarx_core:D", "ecarx_core_server:D", "e:D");
                        pb.redirectErrorStream(true);
                        logcatProcess = pb.start();

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

        // 1. 解析方向盘按键
        if (wheelKeyManager != null) {
            int wheelKey = wheelKeyManager.parseKeyFromLine(line);
            if (wheelKey > 0) {
                wheelKeyManager.handleWheelKey(wheelKey);
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

        // 3. 解析车辆挡位报文: MCULog:GearPosition: x (2=D, 3=N, 4=R, 5=P) 或 VehId=Vehicle_Gear value=0x0x
        if (line.contains("GearPosition:") || line.contains("VehId=Vehicle_Gear value=0x")) {
            int gearVal = -1;
            int idx = line.indexOf("GearPosition:");
            if (idx != -1) {
                try {
                    String sub = line.substring(idx + 13).trim();
                    gearVal = Integer.parseInt(sub.substring(0, 1));
                } catch (Exception ignored) {}
            } else {
                int gidx = line.indexOf("VehId=Vehicle_Gear value=0x");
                if (gidx != -1) {
                    try {
                        String hex = line.substring(gidx + 27, gidx + 29).trim();
                        gearVal = Integer.parseInt(hex, 16);
                    } catch (Exception ignored) {}
                }
            }
            if (gearVal > 0) {
                handleGearSignal(gearVal);
                return;
            }
        }

        // 4. 解析底层 MCU 串口车身报文: 91 02 01 ... b6(四门) 与 b7(尾门)
        if (line.contains("91 02 01")) {
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

                    // b7 解析尾门: bit0=0 开, bit0=1 关
                    int trunk = ((b7 & 0x01) == 0) ? 1 : 0;

                    handleDoorPhysicalState(fl, fr, rl, rr, trunk);
                    return;
                }
            } catch (Exception ignored) {}
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
        else if (line.contains("SwitchMode:")) {
            try {
                Matcher m = Pattern.compile("SwitchMode:\\s*(\\d+)").matcher(line);
                if (m.find()) {
                    int sm = Integer.parseInt(m.group(1));
                    if (sm == 1) modeVal = MODE_COMFORT;
                    else if (sm == 0) modeVal = MODE_SPORT;
                    else if (sm == 3) modeVal = MODE_ECO;
                    else if (sm == 2 || sm == 6) modeVal = MODE_SMART;
                }
            } catch (Exception ignored) {}
        }
        // 优先 4: MCU TargetMode 与 CarConfig DirveMode (注意: 在此层 6 对应 SPORT 运动模式!)
        else if (line.contains("TargetMode:") || line.contains("DirveMode =")) {
            try {
                Matcher m = Pattern.compile("(?:TargetMode:|DirveMode\\s*=)\\s*(\\d+)").matcher(line);
                if (m.find()) {
                    int tm = Integer.parseInt(m.group(1));
                    if (tm == 1 || tm == 3) modeVal = MODE_COMFORT;
                    else if (tm == 2) modeVal = MODE_ECO;
                    else if (tm == 6) modeVal = MODE_SPORT; // 核心！在 MCU/DirveMode 层 6 为运动模式！
                }
            } catch (Exception ignored) {}
        }

        if (modeVal > 0) {
            handleDriveModeSignal(modeVal);
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
        if (line.contains("mModelBatteryVolt =") || line.contains("BatteryVolt")) {
            try {
                int idx = line.indexOf("mModelBatteryVolt =");
                if (idx == -1) idx = line.indexOf("BatteryVolt =");
                if (idx != -1) {
                    String sub = line.substring(idx + (line.contains("mModelBatteryVolt =") ? 19 : 13)).trim();
                    StringBuilder num = new StringBuilder();
                    for (int i = 0; i < sub.length(); i++) {
                        char c = sub.charAt(i);
                        if (Character.isDigit(c)) num.append(c);
                        else if (num.length() > 0) break;
                    }
                    if (num.length() > 0) {
                        int rawVolt = Integer.parseInt(num.toString());
                        // rawVolt e.g. 125 = 12.5V, 138 = 13.8V
                        float volt = (rawVolt > 80 && rawVolt < 250) ? (rawVolt / 10.0f) : (rawVolt / 100.0f);
                        latestBatteryVoltage = volt;
                        SharedPreferences sp = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        sp.edit().putFloat("vehicle_real_battery_volt", volt).commit();
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * 纯物理状态翻转机（State Flip Engine）
     * 彻底废除跨状态时间锁，开门即开，关门毫秒级打断并关好
     */
    private void handleDoorPhysicalState(int fl, int fr, int rl, int rr, int trunk) {
        long now = System.currentTimeMillis();

        // 1. 主驾车门
        if (lastDoorFL == -1) {
            lastDoorFL = fl;
        } else if (fl != lastDoorFL) {
            if (fl == 1) { // 关 -> 开
                if (enableDoorFl && (now - lastTriggerFL > 300)) {
                    lastTriggerFL = now;
                    voicePlayer.play("door_fl.mp3", "主驾车门打开，请注意后方来车");
                }
            } else { // 开 -> 关 (立即打断开门语音并播报已关好)
                if (enableDoorFlClose && (now - lastTriggerFL > 300)) {
                    lastTriggerFL = now;
                    voicePlayer.play("door_fl_close.mp3", "主驾车门已关好");
                }
            }
            lastDoorFL = fl;
        }

        // 2. 副驾车门
        if (lastDoorFR == -1) {
            lastDoorFR = fr;
        } else if (fr != lastDoorFR) {
            if (fr == 1) { // 关 -> 开
                if (enableDoorFr && (now - lastTriggerFR > 300)) {
                    lastTriggerFR = now;
                    voicePlayer.play("door_fr.mp3", "欢迎乘车，请注意安全");
                }
            } else { // 开 -> 关
                if (enableDoorFrClose && (now - lastTriggerFR > 300)) {
                    lastTriggerFR = now;
                    voicePlayer.play("door_fr_close.mp3", "副驾已就坐，请系好安全带");
                }
            }
            lastDoorFR = fr;
        }

        // 3. 左后车门
        if (lastDoorRL == -1) {
            lastDoorRL = rl;
        } else if (rl != lastDoorRL) {
            if (rl == 1) {
                if ((enableDoorRl || enableDoorRear) && (now - lastTriggerRL > 300)) {
                    lastTriggerRL = now;
                    voicePlayer.play("door_rl.mp3", "左后门打开，请注意车外环境");
                }
            } else {
                if (enableDoorRlClose && (now - lastTriggerRL > 300)) {
                    lastTriggerRL = now;
                    voicePlayer.play("door_rl_close.mp3", "左后车门已关好");
                }
            }
            lastDoorRL = rl;
        }

        // 4. 右后车门
        if (lastDoorRR == -1) {
            lastDoorRR = rr;
        } else if (rr != lastDoorRR) {
            if (rr == 1) {
                if ((enableDoorRr || enableDoorRear) && (now - lastTriggerRR > 300)) {
                    lastTriggerRR = now;
                    voicePlayer.play("door_rr.mp3", "右后门打开，请注意上下车安全");
                }
            } else {
                if (enableDoorRrClose && (now - lastTriggerRR > 300)) {
                    lastTriggerRR = now;
                    voicePlayer.play("door_rr_close.mp3", "右后车门已关好");
                }
            }
            lastDoorRR = rr;
        }

        // 5. 电动尾门
        if (lastTrunk == -1) {
            lastTrunk = trunk;
        } else if (trunk != lastTrunk) {
            if (trunk == 1) {
                if (enableTrunkOpen && (now - lastTriggerTrunk > 300)) {
                    lastTriggerTrunk = now;
                    voicePlayer.play("trunk_open.mp3", "后备箱已打开");
                }
            } else {
                if (enableTrunkClose && (now - lastTriggerTrunk > 300)) {
                    lastTriggerTrunk = now;
                    voicePlayer.play("trunk_close.mp3", "后备箱已关闭");
                }
            }
            lastTrunk = trunk;
        }
    }

    /**
     * 处理挡位状态 (智能有人感知状态机：默认P挡静默，手动换出P挡后变量置1激活全量播报，换回P挡播报后置0归位)
     */
    private void handleGearSignal(final int gear) {
        if (lastGearPos == -1) {
            lastGearPos = gear; // 启动首包仅记录基准，坚决不盲目播报
            // 若开机不是处于停泊 P 挡 (5)，则说明车辆已处于行车态，提前激活
            if (gear != 5) {
                isGearVoiceArmed = 1;
            }
            return;
        }

        // 挡位未发生跃变，跳过
        if (gear == lastGearPos) return;

        Log.i(TAG, "Gear changed: " + lastGearPos + " -> " + gear + ", isGearVoiceArmed=" + isGearVoiceArmed);

        // 核心状态机逻辑:
        // 1. 从 P 挡 (5) 换出到其他任意挡位 (D=2, N=3, R=4, S=6/7) -> 证实车内有人主动操作，状态机置 1 激活！
        if (lastGearPos == 5 && gear != 5) {
            isGearVoiceArmed = 1;
            Log.i(TAG, "车主手动换出P挡，挡位语音已激活 (isGearVoiceArmed=1)");
        }

        // 2. 在激活状态下 (isGearVoiceArmed == 1)，按需正常播报各个挡位语音 (彻底移除跨挡1.5s死等拦截)
        if (isGearVoiceArmed == 1) {
            // 挂入 D 挡 (2)
            if (gear == 2) {
                if (enableGearD) {
                    voicePlayer.play("gear_d.mp3", "已挂入前进挡，系好安全带，祝你一路平安");
                }
            }
            // 挂入 R 挡 (4) - 倒车挡专属优化: 延时 150ms 避开 AVM 倒车影像与倒车雷达的初始系统静音瞬态
            else if (gear == 4) {
                if (enableGearR) {
                    mainHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (lastGearPos == 4) {
                                voicePlayer.play("gear_r.mp3", "已挂入倒车挡，请注意观察后方安全");
                            }
                        }
                    }, 150);
                }
            }
            // 挂入 N 挡 (3)
            else if (gear == 3) {
                if (enableGearN) {
                    voicePlayer.play("gear_n.mp3", "已挂入空挡");
                }
            }
            // 挂入 S 挡 (6 或 7)
            else if (gear == 6 || gear == 7) {
                if (enableGearS) {
                    voicePlayer.play("gear_s.mp3", "已挂入运动挡，动力充沛");
                }
            }
            // 挂回 P 挡 (5): 播报一次驻车挡，随后立刻将状态机置 0 归位！
            else if (gear == 5) {
                if (enableGearP) {
                    voicePlayer.play("gear_p.mp3", "已挂入驻车挡");
                }
                isGearVoiceArmed = 0; // 归零！后续再次进入休眠或蓝牙心跳时绝对静默
                Log.i(TAG, "已挂入P挡，状态机归零 (isGearVoiceArmed=0)");
            }
        } else {
            Log.d(TAG, "未激活态(isGearVoiceArmed=0)，跳过挡位播报，防止蓝牙唤醒误报");
        }

        lastGearPos = gear;
    }

    /**
     * 处理驾驶模式切换状态 (智能有人感知状态机：默认智能模式静默，手动切出智能模式后激活全量播报，切回智能模式播报后置0归位)
     * 模式枚举: MODE_COMFORT=1, MODE_SPORT=2, MODE_ECO=3, MODE_SMART=4 (全局解耦，零错位)
     */
    private void handleDriveModeSignal(int mode) {
        if (lastDriveMode == -1) {
            lastDriveMode = mode; // 启动首包仅记录基准，坚决不播报
            if (mode != MODE_SMART) { // 若开机不是默认智能模式，则激活
                isDriveModeVoiceArmed = 1;
            }
            return;
        }
        if (mode == lastDriveMode) return;

        Log.i(TAG, "DriveMode changed: " + lastDriveMode + " -> " + mode + ", isDriveModeVoiceArmed=" + isDriveModeVoiceArmed);

        // 核心状态机逻辑:
        // 1. 从 智能模式 (MODE_SMART) 切换到其他任意模式 -> 证实车主主动按键切换，激活状态机！
        if (lastDriveMode == MODE_SMART && mode != MODE_SMART) {
            isDriveModeVoiceArmed = 1;
            Log.i(TAG, "车主手动切出智能模式，驾驶模式语音已激活 (isDriveModeVoiceArmed=1)");
        }

        // 2. 在激活状态下 (isDriveModeVoiceArmed == 1)，按需播报各个模式
        if (isDriveModeVoiceArmed == 1) {
            switch (mode) {
                case MODE_COMFORT:
                    if (enableModeComfort) {
                        voicePlayer.play("mode_comfort.mp3", "舒适模式");
                    }
                    break;
                case MODE_SPORT:
                    if (enableModeSport) {
                        voicePlayer.play("mode_sport.mp3", "运动模式");
                    }
                    break;
                case MODE_ECO:
                    if (enableModeEco) {
                        voicePlayer.play("mode_eco.mp3", "经济模式");
                    }
                    break;
                case MODE_SMART:
                    // 切回智能模式: 播报一次，随后立即将状态机置 0 归位！
                    if (enableModeSmart) {
                        voicePlayer.play("mode_smart.mp3", "智能模式");
                    }
                    isDriveModeVoiceArmed = 0; // 归零！熄火或休眠开机自动恢复智能模式时绝对静默
                    Log.i(TAG, "已切换为智能模式并播报完成，状态机归零 (isDriveModeVoiceArmed=0)");
                    break;
            }
        } else {
            Log.d(TAG, "未激活态(isDriveModeVoiceArmed=0)，跳过驾驶模式播报，防止唤醒误报");
        }

        lastDriveMode = mode;
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
        if (enableFlameoutVoice && "PEPS_PowerMode".equals(key)) {
            if (val == 0 && lastPowerMode > 0) {
                voicePlayer.play("flameout.mp3", "车辆已熄火，请带好随身物品");
            }
            lastPowerMode = val;
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
        if (btAudioRouter != null) {
            btAudioRouter.stop();
        }
        if (usbMediaManager != null) {
            usbMediaManager.stop();
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
        Log.i(TAG, "VehicleAutomationService stopped");
    }
}
