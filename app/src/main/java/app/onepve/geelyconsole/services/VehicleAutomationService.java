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
    private boolean enableTurn360 = false;
    private boolean enableLightNav = false;
    private boolean enableFlameoutVoice = false;
    private boolean enableBluetoothRouter = false;
    private boolean enableUsbMedia = false;

    // 状态记录变量 (初始化为 -1，首包仅校准基准绝不误播)
    private int lastDoorFL = -1;
    private int lastDoorFR = -1;
    private int lastDoorRL = -1;
    private int lastDoorRR = -1;
    private int lastTrunk = -1;
    private int lastGearPos = -1;
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
            boolean doorFl = prefs.getBoolean("voice_enable_door_fl", false);
            boolean doorFlClose = prefs.getBoolean("voice_enable_door_fl_close", false);
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
            boolean turn360 = prefs.getBoolean("vehicle_turn_360_enabled", false);
            boolean lightNav = prefs.getBoolean("vehicle_light_nav_enabled", false);
            boolean flameout = prefs.getBoolean("vehicle_flameout_voice_enabled", false);
            boolean btRouter = prefs.getBoolean("bt_audio_auto_route", true);
            boolean usbMedia = prefs.getBoolean("usb_media_auto_detect", false);
            String wheelMode = prefs.getString("wheel_control_mode", SteeringWheelKeyManager.MODE_CARMEDIA_FIRST);
            boolean wheelEnabled = !SteeringWheelKeyManager.MODE_FACTORY_DEFAULT.equals(wheelMode);

            boolean shouldRun = doorFl || doorFlClose || doorFr || doorFrClose ||
                                doorRl || doorRlClose || doorRr || doorRrClose || doorRear ||
                                trunkOpen || trunkClose || gearD || gearR || turn360 ||
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
        enableDoorFl = prefs.getBoolean("voice_enable_door_fl", false);
        enableDoorFlClose = prefs.getBoolean("voice_enable_door_fl_close", false);
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
        enableTurn360 = prefs.getBoolean("vehicle_turn_360_enabled", false);
        enableLightNav = prefs.getBoolean("vehicle_light_nav_enabled", false);
        enableFlameoutVoice = prefs.getBoolean("vehicle_flameout_voice_enabled", false);
        enableBluetoothRouter = prefs.getBoolean("bt_audio_auto_route", true);
        enableUsbMedia = prefs.getBoolean("usb_media_auto_detect", false);

        String wheelMode = prefs.getString("wheel_control_mode", SteeringWheelKeyManager.MODE_CARMEDIA_FIRST);
        boolean wheelEnabled = !SteeringWheelKeyManager.MODE_FACTORY_DEFAULT.equals(wheelMode);

        boolean anyEnabled = enableDoorFl || enableDoorFlClose || enableDoorFr || enableDoorFrClose ||
                             enableDoorRl || enableDoorRlClose || enableDoorRr || enableDoorRrClose || enableDoorRear ||
                             enableTrunkOpen || enableTrunkClose || enableGearD || enableGearR || enableTurn360 ||
                             enableLightNav || enableFlameoutVoice || enableBluetoothRouter || enableUsbMedia || wheelEnabled;

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
                        ProcessBuilder pb = new ProcessBuilder("logcat", "-T", "1", "-b", "main", "-b", "system", "-v", "brief",
                                "-s", "VehicleDataBuilder:D", "SerialControl_v2_0:W", "ECARX@ECP:D", "InputManager:V", "CAR.INPUT:V", "e:D");
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

        // 3. 解析 MCU 物理挡位报文: MCULog:GearPosition: x (2=D, 3=N, 4=R, 5=P)
        if (line.contains("MCULog:GearPosition:")) {
            try {
                int pos = line.indexOf("MCULog:GearPosition:");
                String sub = line.substring(pos + 20).trim();
                int gear = Integer.parseInt(sub.substring(0, 1));
                handleGearSignal(gear);
                return;
            } catch (Exception ignored) {}
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
     * 处理挡位状态 (仅播报 D 前进挡 与 R 倒车挡，P 挡与 N 挡静默，天然免疫蓝牙靠近误播)
     */
    private void handleGearSignal(int gear) {
        long now = System.currentTimeMillis();
        if (lastGearPos == -1) {
            lastGearPos = gear; // 初始校准不播
            return;
        }
        if (gear == lastGearPos) return;

        Log.i(TAG, "Gear changed: " + lastGearPos + " -> " + gear);

        // 挂入 D 挡 (2)
        if (gear == 2) {
            if (enableGearD && (now - lastTriggerGear > 2000)) {
                lastTriggerGear = now;
                voicePlayer.play("gear_d.mp3", "已挂入前进挡，系好安全带，祝你一路平安");
            }
        }
        // 挂入 R 挡 (4)
        else if (gear == 4) {
            if (enableGearR && (now - lastTriggerGear > 2000)) {
                lastTriggerGear = now;
                voicePlayer.play("gear_r.mp3", "已挂入倒车挡，请注意观察后方安全");
            }
        }
        lastGearPos = gear;
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
