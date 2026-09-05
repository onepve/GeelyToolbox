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

import app.onepve.geelyconsole.R;
import app.onepve.geelyconsole.utils.AdbClient;
import app.onepve.geelyconsole.utils.AppLogger;
import app.onepve.geelyconsole.utils.SystemUtils;
import app.onepve.geelyconsole.utils.VehicleVoicePlayer;

/**
 * 吉利座舱自动化与智能联动常驻守护服务（替代第三方 TSK / Tasker）
 * 1. 原生监听吉利 E02 底层 VehicleDataBuilder CAN 报文
 * 2. 四门上下车迎宾/提醒（带状态机边沿触发与 4 秒防抖）
 * 3. 转向灯联动 360 全景影像（带车速 ≤30km/h 安全阈值过滤，高速变道坚决不弹 360 遮挡导航）
 * 4. 大灯联动高德日夜模式
 * 5. 熄火下车语音告别
 * 6. 默认全部关闭，车友按需单项开启；全关时自动销毁线程释放资源
 */
public class VehicleAutomationService extends Service {

    private static final String TAG = "VehicleAutomationService";
    private static final String CHANNEL_ID = "geely_toolbox_daemon";
    private static final int NOTIF_ID = 1002;

    public static volatile boolean isRunning = false;

    private boolean enableDoorFl = false;
    private boolean enableDoorFlClose = false;
    private boolean enableDoorFr = false;
    private boolean enableDoorFrClose = false;
    private boolean enableDoorRl = false;
    private boolean enableDoorRlClose = false;
    private boolean enableDoorRr = false;
    private boolean enableDoorRrClose = false;
    private boolean enableDoorRear = false; // 兼容旧版全后门开关
    private boolean enableTrunkOpen = false;
    private boolean enableTrunkClose = false;
    private boolean enableTurn360 = false;
    private boolean enableLightNav = false;
    private boolean enableFlameoutVoice = false;

    // 状态记录与防抖变量
    private int lastDoorFL = 0;
    private int lastDoorFR = 0;
    private int lastDoorRL = 0;
    private int lastDoorRR = 0;
    private int lastTrunk = 0;
    private int lastLightSts = -1;
    private int lastPowerMode = -1;
    private int currentSpeedKmH = 0;
    private boolean is360OpenedByTurn = false;

    private long serviceStartTime = 0;
    private long lastVoiceTimeFL = 0;
    private long lastVoiceTimeFR = 0;
    private long lastVoiceTimeRL = 0;
    private long lastVoiceTimeRR = 0;
    private long lastVoiceTimeTrunk = 0;

    private Thread logcatThread;
    private Process logcatProcess;
    private VehicleVoicePlayer voicePlayer;
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
            boolean turn360 = prefs.getBoolean("vehicle_turn_360_enabled", false);
            boolean lightNav = prefs.getBoolean("vehicle_light_nav_enabled", false);
            boolean flameout = prefs.getBoolean("vehicle_flameout_voice_enabled", false);

            boolean shouldRun = doorFl || doorFlClose || doorFr || doorFrClose ||
                                doorRl || doorRlClose || doorRr || doorRrClose || doorRear ||
                                trunkOpen || trunkClose || turn360 || lightNav || flameout;

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
        serviceStartTime = System.currentTimeMillis();
        voicePlayer = VehicleVoicePlayer.getInstance(this);
        startForegroundSafely();
        registerPowerReceiver();
        reloadSettings();
        startLogcatReader();
        AppLogger.i("座舱自动化", "座舱自动化服务启动成功");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundSafely();
        reloadSettings();
        return START_STICKY;
    }

    private void reloadSettings() {
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
        enableTurn360 = prefs.getBoolean("vehicle_turn_360_enabled", false);
        enableLightNav = prefs.getBoolean("vehicle_light_nav_enabled", false);
        enableFlameoutVoice = prefs.getBoolean("vehicle_flameout_voice_enabled", false);

        boolean anyEnabled = enableDoorFl || enableDoorFlClose || enableDoorFr || enableDoorFrClose ||
                             enableDoorRl || enableDoorRlClose || enableDoorRr || enableDoorRrClose || enableDoorRear ||
                             enableTrunkOpen || enableTrunkClose || enableTurn360 || enableLightNav || enableFlameoutVoice;

        if (!anyEnabled) {
            stopSelf();
        }
    }

    private void startForegroundSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "吉利控制台后台守护", NotificationManager.IMPORTANCE_MIN);
                channel.setDescription("保障座舱自动化与悬浮胶囊常驻运行");
                channel.enableLights(false);
                channel.enableVibration(false);
                channel.setSound(null, null);
                nm.createNotificationChannel(channel);
            }
            Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("座舱自动化运行中")
                    .setContentText("监听车辆信号与智能联动")
                    .setSmallIcon(R.mipmap.ic_launcher);
            startForeground(NOTIF_ID, builder.build());
        }
    }

    private void registerPowerReceiver() {
        if (powerReceiver != null) return;
        powerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                String action = intent.getAction();
                if (Intent.ACTION_SHUTDOWN.equals(action)
                        || "android.intent.action.QUICKBOOT_POWEROFF".equals(action)
                        || "com.ecarx.intent.action.ECARX_SHUTDOWN".equals(action)
                        || Intent.ACTION_SCREEN_OFF.equals(action)) {
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
        } catch (Exception ignored) {
        }
    }

    private static final java.util.regex.Pattern CAN_PATTERN =
            java.util.regex.Pattern.compile("key\\s*=\\s*([^,\\s]+).*?data\\s*=\\s*(-?\\d+)");
    private static final java.util.regex.Pattern DOOR_DIRECT_PATTERN =
            java.util.regex.Pattern.compile("fl\\s*=\\s*(\\d+).*?fr\\s*=\\s*(\\d+).*?rl\\s*=\\s*(\\d+).*?rr\\s*=\\s*(\\d+)");
    private static final java.util.regex.Pattern SERIAL_DOOR_PATTERN =
            java.util.regex.Pattern.compile("91\\s+02\\s+01\\s+[0-9a-fA-F]{2}\\s+[0-9a-fA-F]{2}\\s+[0-9a-fA-F]{2}\\s+([0-9a-fA-F]{2})\\s+([0-9a-fA-F]{2})");

    private void startLogcatReader() {
        if (logcatThread != null && logcatThread.isAlive()) return;

        logcatThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRunning) {
                    try {
                        // 确保具备底层系统日志读取权限
                        try {
                            AdbClient.execute(VehicleAutomationService.this, "pm grant " + getPackageName() + " android.permission.READ_LOGS");
                        } catch (Exception ignored) {}

                        // 启动实时监听前清空旧缓冲区，确保收到的每一条都是最新车辆真实事件
                        try {
                            AdbClient.execute(VehicleAutomationService.this, "logcat -c");
                        } catch (Exception ignored) {}
                        ProcessBuilder pb = new ProcessBuilder("logcat", "-b", "main", "-b", "system", "-v", "brief", "-s", "VehicleDataBuilder:D", "ecarx_avm_SocketCommand:W", "SerialControl_v2_0:W", "e:D");
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

        // 1. 解析车辆实时车速
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
                } catch (Exception ignored) {
                }
            }
            return;
        }

        // 2. 解析直出式四门信号: fl = 0, fr = 1, rl = 0, rr = 0 (吉利车身 AVM 控制总线)
        if (line.contains("fl =") && line.contains("fr =")) {
            try {
                java.util.regex.Matcher dm = DOOR_DIRECT_PATTERN.matcher(line);
                if (dm.find()) {
                    int fl = Integer.parseInt(dm.group(1).trim());
                    int fr = Integer.parseInt(dm.group(2).trim());
                    int rl = Integer.parseInt(dm.group(3).trim());
                    int rr = Integer.parseInt(dm.group(4).trim());
                    handleCanSignal("BCM_FrontLeftDoorAjarStatus", fl);
                    handleCanSignal("BCM_FrontRightDoorAjarStatus", fr);
                    handleCanSignal("BCM_RearLeftDoorAjarStatus", rl);
                    handleCanSignal("BCM_RearRightDoorAjarStatus", rr);
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        // 3. 解析底层 MCU 串口车身报文: 91 02 01 ... byte6 / byte7 包含四门与后备箱状态
        if (line.contains("91 02 01")) {
            try {
                java.util.regex.Matcher sm = SERIAL_DOOR_PATTERN.matcher(line);
                if (sm.find()) {
                    int b6 = Integer.parseInt(sm.group(1).trim(), 16);
                    int b7 = Integer.parseInt(sm.group(2).trim(), 16);
                    // 提取后备箱 (bit0 of byte7 或 b6/b7 特征位)
                    // 当 b7 低4位变化或者有门/尾箱触发时识别
                    int trunkVal = ((b7 & 0x01) == 0) ? 1 : 0; // 0x51 时为门开，尾箱通常挂载在辅助控制位
                    // 若有明确 trunk 状态变更则分发
                    // handleCanSignal("BCM_TrunkAjarStatus", trunkVal);
                }
            } catch (Exception ignored) {
            }
        }

        // 4. 解析 CAN 数据: parseCanData: key = ..., data = ... (支持灵活正则匹配)
        if (!line.contains("parseCanData")) return;

        try {
            java.util.regex.Matcher m = CAN_PATTERN.matcher(line);
            if (m.find()) {
                String key = m.group(1).trim();
                int val = Integer.parseInt(m.group(2).trim());
                handleCanSignal(key, val);
            }
        } catch (Exception ignored) {
        }
    }

    private void handleCanSignal(String key, int val) {
        long now = System.currentTimeMillis();

        // 启动前 800ms 仅静默同步当前基准状态，不发声
        if (now - serviceStartTime < 800) {
            if ("BCM_FrontLeftDoorAjarStatus".equals(key)) lastDoorFL = val;
            else if ("BCM_FrontRightDoorAjarStatus".equals(key)) lastDoorFR = val;
            else if ("BCM_RearLeftDoorAjarStatus".equals(key)) lastDoorRL = val;
            else if ("BCM_RearRightDoorAjarStatus".equals(key)) lastDoorRR = val;
            else if ("BCM_TrunkAjarStatus".equals(key) || "BCM_TailgateAjarStatus".equals(key)) lastTrunk = val;
            return;
        }

        // 1. 四门及后备箱上下车迎宾与安全播报（支持每门开关独立控制与开门未完关门抢占式打断）
        // 主驾开门与关门
        if ("BCM_FrontLeftDoorAjarStatus".equals(key)) {
            if (val == 1 && lastDoorFL != 1) {
                if (enableDoorFl && (now - lastVoiceTimeFL > 1200)) {
                    lastVoiceTimeFL = now;
                    voicePlayer.play("door_fl.mp3", "主驾车门打开，请注意后方来车");
                }
            } else if (val == 0 && lastDoorFL != 0) {
                if (enableDoorFlClose && (now - lastVoiceTimeFL > 800)) {
                    lastVoiceTimeFL = now;
                    voicePlayer.play("door_fl_close.mp3", "主驾车门已关好");
                }
            }
            lastDoorFL = val;
        }
        // 副驾开门与关门
        else if ("BCM_FrontRightDoorAjarStatus".equals(key)) {
            if (val == 1 && lastDoorFR != 1) {
                if (enableDoorFr && (now - lastVoiceTimeFR > 1200)) {
                    lastVoiceTimeFR = now;
                    voicePlayer.play("door_fr.mp3", "欢迎乘车，请注意安全");
                }
            } else if (val == 0 && lastDoorFR != 0) {
                if (enableDoorFrClose && (now - lastVoiceTimeFR > 800)) {
                    lastVoiceTimeFR = now;
                    voicePlayer.play("door_fr_close.mp3", "副驾已就坐，请系好安全带");
                }
            }
            lastDoorFR = val;
        }
        // 左后门开门与关门
        else if ("BCM_RearLeftDoorAjarStatus".equals(key)) {
            if (val == 1 && lastDoorRL != 1) {
                if ((enableDoorRl || enableDoorRear) && (now - lastVoiceTimeRL > 1200)) {
                    lastVoiceTimeRL = now;
                    voicePlayer.play("door_rl.mp3", "左后门打开，请注意车外环境");
                }
            } else if (val == 0 && lastDoorRL != 0) {
                if (enableDoorRlClose && (now - lastVoiceTimeRL > 800)) {
                    lastVoiceTimeRL = now;
                    voicePlayer.play("door_rl_close.mp3", "左后车门已关好");
                }
            }
            lastDoorRL = val;
        }
        // 右后门开门与关门
        else if ("BCM_RearRightDoorAjarStatus".equals(key)) {
            if (val == 1 && lastDoorRR != 1) {
                if ((enableDoorRr || enableDoorRear) && (now - lastVoiceTimeRR > 1200)) {
                    lastVoiceTimeRR = now;
                    voicePlayer.play("door_rr.mp3", "右后门打开，请注意车外环境");
                }
            } else if (val == 0 && lastDoorRR != 0) {
                if (enableDoorRrClose && (now - lastVoiceTimeRR > 800)) {
                    lastVoiceTimeRR = now;
                    voicePlayer.play("door_rr_close.mp3", "右后车门已关好");
                }
            }
            lastDoorRR = val;
        }
        // 后备箱打开与关闭
        else if ("BCM_TrunkAjarStatus".equals(key) || "BCM_TailgateAjarStatus".equals(key)) {
            if (val == 1 && lastTrunk != 1) {
                if (enableTrunkOpen && (now - lastVoiceTimeTrunk > 1200)) {
                    lastVoiceTimeTrunk = now;
                    voicePlayer.play("trunk_open.mp3", "后备箱已打开");
                }
            } else if (val == 0 && lastTrunk != 0) {
                if (enableTrunkClose && (now - lastVoiceTimeTrunk > 800)) {
                    lastVoiceTimeTrunk = now;
                    voicePlayer.play("trunk_close.mp3", "后备箱已关闭");
                }
            }
            lastTrunk = val;
        }

        // 2. 转向灯联动 360 全景影像 (严格车速过滤)
        if (enableTurn360 && "TCM_Req_TurnIndicationAct".equals(key)) {
            if (val == 1 || val == 2) { // 1 左转, 2 右转
                // 只有当车速 <= 30km/h 允许调起 360，高速变道坚决不遮挡导航
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

        // 3. 大灯联动高德日夜模式
        if (enableLightNav && "BCM_PositionLightSts".equals(key)) {
            if (lastLightSts == -1) {
                lastLightSts = val;
                return;
            }
            if (val == 1 && lastLightSts == 0) {
                // 开启大灯 -> 高德切换黑夜模式 (2)
                sendAmapDayNightMode(2);
                lastLightSts = 1;
            } else if (val == 0 && lastLightSts == 1) {
                // 关闭大灯 -> 高德恢复自动/日间模式 (0)
                sendAmapDayNightMode(0);
                lastLightSts = 0;
            }
        }

        // 4. 电源模式/熄火检测
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
            } catch (Exception ignored) {
            }
            powerReceiver = null;
        }
        if (logcatProcess != null) {
            try {
                logcatProcess.destroy();
            } catch (Exception ignored) {
            }
            logcatProcess = null;
        }
        AppLogger.i("座舱自动化", "座舱自动化服务已停止");
    }
}
