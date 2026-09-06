package app.onepve.geelyconsole.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import app.onepve.geelyconsole.R;
import app.onepve.geelyconsole.utils.AdbClient;
import app.onepve.geelyconsole.utils.AppLogger;
import app.onepve.geelyconsole.utils.SystemUtils;
import app.onepve.geelyconsole.utils.VehicleVoicePlayer;

/**
 * 吉利座舱自动化与智能联动常驻守护服务（替代第三方 TSK / Tasker）
 * 1. 专车专用协议架构：默认自适应检测，首选吉利缤越 COOL (2022款 · 1.5TD 激擎版 / SX11-A3 / IHU516G)
 * 2. 5组核心车身门控：四门（主驾/副驾/左后/右后）开闭 + 原厂后备箱/电动尾门开闭
 * 3. 彻底下线换挡与驾驶模式语音（根除熄火开机乱播报）
 * 4. 彻底拔除 AVM 摄像头假门信号（切断转向灯与门控的任何冲突）
 * 5. 启动加 -T 1 与 3.5 秒基准静默期，历史旧日志 0 触发
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
    private boolean enableDoorRear = false;
    private boolean enableTrunkOpen = false;
    private boolean enableTrunkClose = false;
    private String selectedVehicleModel = "auto";
    private boolean enableTurn360 = false;
    private boolean enableLightNav = false;

    // 状态记录与防抖变量
    private int lastDoorFL = -1;
    private int lastDoorFR = -1;
    private int lastDoorRL = -1;
    private int lastDoorRR = -1;
    private int lastTrunk = -1;
    private int lastLightSts = -1;
    private int currentSpeedKmH = 0;
    private boolean is360OpenedByTurn = false;

    private long lastVoiceTimeFL = 0;
    private long lastVoiceTimeFR = 0;
    private long lastVoiceTimeRL = 0;
    private long lastVoiceTimeRR = 0;
    private long lastVoiceTimeTrunk = 0;

    // 启动时间戳（用于冷启动 3.5 秒基准静默期，防读旧缓存乱报）
    private long serviceStartTime = 0;

    private Thread logcatThread;
    private Process logcatProcess;
    private VehicleVoicePlayer voicePlayer;

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
            boolean shouldRun = doorFl || doorFlClose || doorFr || doorFrClose ||
                                doorRl || doorRlClose || doorRr || doorRrClose || doorRear ||
                                trunkOpen || trunkClose || turn360 || lightNav;

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

        reloadSettings();
        startLogcatReader();
        AppLogger.i("座舱自动化", "座舱自动化常驻服务启动成功 (车型协议: " + selectedVehicleModel + ")");
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
        selectedVehicleModel = prefs.getString("vehicle_model_selection", "auto");
        enableTurn360 = prefs.getBoolean("vehicle_turn_360_enabled", false);
        enableLightNav = prefs.getBoolean("vehicle_light_nav_enabled", false);

    }

    public static String detectVehicleModel() {
        try {
            String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
            String device = Build.DEVICE != null ? Build.DEVICE.toUpperCase() : "";
            String display = Build.DISPLAY != null ? Build.DISPLAY.toUpperCase() : "";
            String vehicleType = "";
            String mcuType = "";
            try {
                Class<?> sp = Class.forName("android.os.SystemProperties");
                java.lang.reflect.Method get = sp.getMethod("get", String.class, String.class);
                vehicleType = (String) get.invoke(null, "persist.sys.ecarx.vehicleType", "");
                mcuType = (String) get.invoke(null, "sys.bicv.mcu_type", "");
            } catch (Exception ignored) {}
            vehicleType = vehicleType != null ? vehicleType.toUpperCase() : "";
            mcuType = mcuType != null ? mcuType.toUpperCase() : "";

            if (model.contains("SX11") || device.contains("SX11") || display.contains("SX11") ||
                model.contains("IHU516") || device.contains("IHU516") || vehicleType.contains("SX11") ||
                vehicleType.contains("516") || mcuType.contains("516")) {
                return "cool";
            }
            if (model.contains("FS11") || display.contains("FS11") || vehicleType.contains("FS11")) {
                return "xingrui";
            }
            if (model.contains("FX11") || display.contains("FX11") || vehicleType.contains("FX11")) {
                return "boyuel";
            }
        } catch (Exception ignored) {}
        return "cool";
    }

    private void startForegroundSafely() {
        try {
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
                        .setContentTitle("吉利控制台 · 座舱智能联动")
                        .setContentText("车门与车身安全守护运行中")
                        .setSmallIcon(R.mipmap.ic_launcher);
                startForeground(NOTIF_ID, builder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "startForegroundSafely error: " + e.getMessage());
        }
    }

    private static final Pattern SERIAL_DOOR_PATTERN = Pattern.compile(
        "91\s+02\s+01\s+00\s+04\s+00\s+([0-9a-fA-F]{2})\s+([0-9a-fA-F]{2})", Pattern.CASE_INSENSITIVE
    );

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

                        // -T 1 强制仅从当前最新时刻开始实时监听，坚决不回放环形缓冲区历史旧日志，彻底消除冷启动误报
                        ProcessBuilder pb = new ProcessBuilder("logcat", "-T", "1", "-b", "main", "-b", "system", "-v", "brief");
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

        // 2. 原厂主驾车门物理事件 (ECarXPowerManagerService 绝对真车硬信号，全车型通用)
        if (line.contains("EVENT_DRIVER_DOOR_OPEN")) {
            handleCanSignal("BCM_FrontLeftDoorAjarStatus", 1);
            return;
        }
        if (line.contains("EVENT_DRIVER_DOOR_CLOSE")) {
            handleCanSignal("BCM_FrontLeftDoorAjarStatus", 0);
            return;
        }

        // 3. 原厂 core_server 门控状态
        if (line.contains("vehicledata----callbacks---mModelLFDoor =")) {
            if (line.contains("mModelLFDoor = 0")) {
                handleCanSignal("BCM_FrontLeftDoorAjarStatus", 1);
            } else if (line.contains("mModelLFDoor = 1")) {
                handleCanSignal("BCM_FrontLeftDoorAjarStatus", 0);
            }
            return;
        }

        // 4. 原厂 MCU 车身报文 (吉利缤越 COOL SX11-A3 专车专用协议: 91 02 01)
        // 彻底剔除 ecarx_avm_SocketCommand，绝不与转向灯和盲区影像产生交集
        if (line.contains("91 02 01") && (line.contains("SerialControl_v2_0") || line.contains("VehicleEmulator_v2_0") || line.contains("mcu->mpu"))) {
            try {
                java.util.regex.Matcher sm = SERIAL_DOOR_PATTERN.matcher(line);
                if (sm.find()) {
                    int b6 = Integer.parseInt(sm.group(1).trim(), 16);
                    int b7 = Integer.parseInt(sm.group(2).trim(), 16);

                    // 尾门状态 (b7): 0xF4 (bit0=0) 开启/抬起中; 0xF5 (bit0=1) 关好/电吸锁紧
                    int trunkVal = ((b7 & 0x01) == 0) ? 1 : 0;
                    handleCanSignal("BCM_TrunkAjarStatus", trunkVal);

                    // 四门物理位图 (b6): 0x55 全关, 0x54 主驾开, 0x51 副驾开, 0x45 左后开, 0x15 右后开
                    int fl = ((b6 & 0x01) == 0) ? 1 : 0;
                    int fr = (((b6 >> 2) & 0x01) == 0) ? 1 : 0;
                    int rl = (((b6 >> 4) & 0x01) == 0) ? 1 : 0;
                    int rr = (((b6 >> 6) & 0x01) == 0) ? 1 : 0;

                    handleCanSignal("BCM_FrontLeftDoorAjarStatus", fl);
                    handleCanSignal("BCM_FrontRightDoorAjarStatus", fr);
                    handleCanSignal("BCM_RearLeftDoorAjarStatus", rl);
                    handleCanSignal("BCM_RearRightDoorAjarStatus", rr);
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        // 5. 原厂 MCULog 车门状态 (MCULog:FL_DOOR_STS Open / Close 等)
        if (line.contains("_DOOR_STS") && line.contains("MCULog")) {
            boolean isOpen = line.contains("Open");
            int val = isOpen ? 1 : 0;
            if (line.contains("FL_DOOR_STS")) {
                handleCanSignal("BCM_FrontLeftDoorAjarStatus", val);
            } else if (line.contains("FR_DOOR_STS")) {
                handleCanSignal("BCM_FrontRightDoorAjarStatus", val);
            } else if (line.contains("RL_DOOR_STS")) {
                handleCanSignal("BCM_RearLeftDoorAjarStatus", val);
            } else if (line.contains("RR_DOOR_STS")) {
                handleCanSignal("BCM_RearRightDoorAjarStatus", val);
            } else if (line.contains("TRUNK_DOOR_STS") || line.contains("TAILGATE_DOOR_STS")) {
                handleCanSignal("BCM_TrunkAjarStatus", val);
            }
            return;
        }

        // 6. 解析转向灯信号联动 360 全景影像 (带低速安全阈值 <=30km/h)
        if (enableTurn360 && (line.contains("TurnLight") || line.contains("turn_indicator") || line.contains("TCM_Req_TurnIndicationAct"))) {
            int turnVal = 0;
            if (line.contains("value=0x1") || line.contains("value=0x2") || line.contains(":1") || line.contains(":2") || line.contains("TurnIndicationAct:1") || line.contains("TurnIndicationAct:2")) {
                turnVal = 1;
            }
            if (turnVal == 1 && currentSpeedKmH <= 30 && !is360OpenedByTurn) {
                open360Camera();
                is360OpenedByTurn = true;
            } else if (turnVal == 0 && is360OpenedByTurn) {
                close360Camera();
                is360OpenedByTurn = false;
            }
            return;
        }

        // 7. 解析大灯信号联动高德日夜模式
        if (enableLightNav && (line.contains("LightSts") || line.contains("HeadlightStatus") || line.contains("BCM_LowBeamStatus"))) {
            int lightVal = (line.contains("value=0x1") || line.contains(":1") || line.contains("ON") || line.contains("On")) ? 1 : 0;
            if (lightVal != lastLightSts) {
                lastLightSts = lightVal;
                sendAmapDayNightMode(lightVal == 1 ? 2 : 1);
            }
        }
    }

    private void handleCanSignal(String key, int val) {
        long now = System.currentTimeMillis();

        // 启动前 3.5 秒基准静默期：仅静默记录各门物理基准状态，坚决不播放任何语音，彻底杜绝冷启动误报
        if (now - serviceStartTime < 3500) {
            if ("BCM_FrontLeftDoorAjarStatus".equals(key)) lastDoorFL = val;
            else if ("BCM_FrontRightDoorAjarStatus".equals(key)) lastDoorFR = val;
            else if ("BCM_RearLeftDoorAjarStatus".equals(key)) lastDoorRL = val;
            else if ("BCM_RearRightDoorAjarStatus".equals(key)) lastDoorRR = val;
            else if ("BCM_TrunkAjarStatus".equals(key)) lastTrunk = val;
            return;
        }

        // 主驾开门与关门 (行车车速 > 3km/h 强制静默抑制)
        if ("BCM_FrontLeftDoorAjarStatus".equals(key)) {
            if (currentSpeedKmH > 3) { lastDoorFL = val; return; }
            if (lastDoorFL == -1) {
                lastDoorFL = val;
                return;
            }
            if (val == 1 && lastDoorFL == 0) {
                if (enableDoorFl && (now - lastVoiceTimeFL > 2000)) {
                    lastVoiceTimeFL = now;
                    AppLogger.i("座舱自动化", "触发主驾开门语音播报");
                    voicePlayer.play("door_fl.mp3", "主驾车门打开，请注意后方来车");
                }
            } else if (val == 0 && lastDoorFL == 1) {
                if (enableDoorFlClose && (now - lastVoiceTimeFL > 2000)) {
                    lastVoiceTimeFL = now;
                    AppLogger.i("座舱自动化", "触发主驾关门语音播报");
                    voicePlayer.play("door_fl_close.mp3", "主驾车门已关好");
                }
            }
            lastDoorFL = val;
        }
        // 副驾开门与关门
        else if ("BCM_FrontRightDoorAjarStatus".equals(key)) {
            if (currentSpeedKmH > 3) { lastDoorFR = val; return; }
            if (lastDoorFR == -1) {
                lastDoorFR = val;
                return;
            }
            if (val == 1 && lastDoorFR == 0) {
                if (enableDoorFr && (now - lastVoiceTimeFR > 2000)) {
                    lastVoiceTimeFR = now;
                    AppLogger.i("座舱自动化", "触发副驾开门语音播报");
                    voicePlayer.play("door_fr.mp3", "欢迎乘车，请注意安全");
                }
            } else if (val == 0 && lastDoorFR == 1) {
                if (enableDoorFrClose && (now - lastVoiceTimeFR > 2000)) {
                    lastVoiceTimeFR = now;
                    AppLogger.i("座舱自动化", "触发副驾关门语音播报");
                    voicePlayer.play("door_fr_close.mp3", "副驾已就坐，请系好安全带");
                }
            }
            lastDoorFR = val;
        }
        // 左后门开门与关门
        else if ("BCM_RearLeftDoorAjarStatus".equals(key)) {
            if (currentSpeedKmH > 3) { lastDoorRL = val; return; }
            if (lastDoorRL == -1) {
                lastDoorRL = val;
                return;
            }
            if (val == 1 && lastDoorRL == 0) {
                if ((enableDoorRl || enableDoorRear) && (now - lastVoiceTimeRL > 2500)) {
                    lastVoiceTimeRL = now;
                    AppLogger.i("座舱自动化", "触发左后门开门语音播报");
                    voicePlayer.play("door_rl.mp3", "左后门打开，请注意车外环境");
                }
            } else if (val == 0 && lastDoorRL == 1) {
                if (enableDoorRlClose && (now - lastVoiceTimeRL > 2500)) {
                    lastVoiceTimeRL = now;
                    AppLogger.i("座舱自动化", "触发左后门关门语音播报");
                    voicePlayer.play("door_rl_close.mp3", "左后车门已关好");
                }
            }
            lastDoorRL = val;
        }
        // 右后门开门与关门
        else if ("BCM_RearRightDoorAjarStatus".equals(key)) {
            if (currentSpeedKmH > 3) { lastDoorRR = val; return; }
            if (lastDoorRR == -1) {
                lastDoorRR = val;
                return;
            }
            if (val == 1 && lastDoorRR == 0) {
                if ((enableDoorRr || enableDoorRear) && (now - lastVoiceTimeRR > 2500)) {
                    lastVoiceTimeRR = now;
                    AppLogger.i("座舱自动化", "触发右后门开门语音播报");
                    voicePlayer.play("door_rr.mp3", "右后门打开，请注意车外环境");
                }
            } else if (val == 0 && lastDoorRR == 1) {
                if (enableDoorRrClose && (now - lastVoiceTimeRR > 2500)) {
                    lastVoiceTimeRR = now;
                    AppLogger.i("座舱自动化", "触发右后门关门语音播报");
                    voicePlayer.play("door_rr_close.mp3", "右后车门已关好");
                }
            }
            lastDoorRR = val;
        }
        // 后备箱/电动尾门开门与关门
        else if ("BCM_TrunkAjarStatus".equals(key)) {
            if (lastTrunk == -1) {
                lastTrunk = val;
                return;
            }
            if (val == 1 && lastTrunk == 0) {
                if (enableTrunkOpen && (now - lastVoiceTimeTrunk > 3000)) {
                    lastVoiceTimeTrunk = now;
                    AppLogger.i("座舱自动化", "触发后备箱/电动尾门开启语音播报");
                    voicePlayer.play("trunk_open.mp3", "后备箱已打开，请注意后方障碍物");
                }
            } else if (val == 0 && lastTrunk == 1) {
                if (enableTrunkClose && (now - lastVoiceTimeTrunk > 3000)) {
                    lastVoiceTimeTrunk = now;
                    AppLogger.i("座舱自动化", "触发后备箱/电动尾门已关好语音播报");
                    voicePlayer.play("trunk_close.mp3", "后备箱已锁好");
                }
            }
            lastTrunk = val;
        }
    }

    private void open360Camera() {
        try {
            Intent intent = new Intent();
            intent.setClassName("com.ecarx.avm", "com.ecarx.avm.MainActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    private void close360Camera() {
        try {
            Intent intent = new Intent("com.ecarx.action.AVM_CLOSE");
            sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    private void sendAmapDayNightMode(int mode) {
        try {
            Intent intent = new Intent("AUTONAVI_STANDARD_BROADCAST_RECV");
            intent.putExtra("KEY_TYPE", 10049);
            intent.putExtra("EXTRA_DAY_NIGHT_MODE", mode);
            sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (logcatProcess != null) {
            try {
                logcatProcess.destroy();
            } catch (Exception ignored) {}
        }
        if (logcatThread != null) {
            logcatThread.interrupt();
        }
        if (voicePlayer != null) {
            voicePlayer.stopCurrentVoice();
        }
        AppLogger.i("座舱自动化", "座舱自动化服务已停止运行");
    }
}
