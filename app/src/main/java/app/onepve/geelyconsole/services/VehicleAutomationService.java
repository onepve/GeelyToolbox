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
    private boolean enableGearD = false;
    private boolean enableGearR = false;
    private boolean enableGearP = false;
    private boolean enableGearN = false;
    private boolean enableDriveMode = false;
    private boolean enableTurn360 = false;
    private boolean enableLightNav = false;

    // 状态记录与防抖变量
    private int lastDoorFL = -1;
    private int lastDoorFR = -1;
    private int lastDoorRL = -1;
    private int lastDoorRR = -1;
    private int lastTrunk = -1;
    private int lastGearPosition = -1;
    private int lastDriveMode = -1;
    private int lastLightSts = -1;
    private int lastPowerMode = -1;
    private int currentSpeedKmH = 0;
    private boolean is360OpenedByTurn = false;

    private long lastVoiceTimeFL = 0;
    private long lastVoiceTimeFR = 0;
    private long lastVoiceTimeRL = 0;
    private long lastVoiceTimeRR = 0;
    private long lastVoiceTimeTrunk = 0;
    private long lastVoiceTimeGear = 0;

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
            boolean gearD = prefs.getBoolean("voice_enable_gear_d", prefs.getBoolean("voice_enable_gear", false));
            boolean gearR = prefs.getBoolean("voice_enable_gear_r", prefs.getBoolean("voice_enable_gear", false));
            boolean gearP = prefs.getBoolean("voice_enable_gear_p", prefs.getBoolean("voice_enable_gear", false));
            boolean gearN = prefs.getBoolean("voice_enable_gear_n", prefs.getBoolean("voice_enable_gear", false));
            boolean driveMode = prefs.getBoolean("voice_enable_drive_mode", false);
            boolean turn360 = prefs.getBoolean("vehicle_turn_360_enabled", false);
            boolean lightNav = prefs.getBoolean("vehicle_light_nav_enabled", false);
            boolean shouldRun = doorFl || doorFlClose || doorFr || doorFrClose ||
                                doorRl || doorRlClose || doorRr || doorRrClose || doorRear ||
                                trunkOpen || trunkClose || gearD || gearR || gearP || gearN || driveMode || turn360 || lightNav;

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
        startForegroundSafely();

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
        enableGearD = prefs.getBoolean("voice_enable_gear_d", prefs.getBoolean("voice_enable_gear", false));
        enableGearR = prefs.getBoolean("voice_enable_gear_r", prefs.getBoolean("voice_enable_gear", false));
        enableGearP = prefs.getBoolean("voice_enable_gear_p", prefs.getBoolean("voice_enable_gear", false));
        enableGearN = prefs.getBoolean("voice_enable_gear_n", prefs.getBoolean("voice_enable_gear", false));
        enableDriveMode = prefs.getBoolean("voice_enable_drive_mode", false);
        enableTurn360 = prefs.getBoolean("vehicle_turn_360_enabled", false);
        enableLightNav = prefs.getBoolean("vehicle_light_nav_enabled", false);

        boolean anyEnabled = enableDoorFl || enableDoorFlClose || enableDoorFr || enableDoorFrClose ||
                             enableDoorRl || enableDoorRlClose || enableDoorRr || enableDoorRrClose || enableDoorRear ||
                             enableTrunkOpen || enableTrunkClose || enableGearD || enableGearR || enableGearP || enableGearN ||
                             enableDriveMode || enableTurn360 || enableLightNav;

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

                        // 过滤 VehicleDataBuilder、车身 AVM 信号 (ecarx_avm_SocketCommand)、底层串口数据 (SerialControl_v2_0) 与 ECU/MCU 档位/车速标签
                        // -T 1 强制仅从当前最新时刻开始实时监听，坚决不回放环形缓冲区历史旧日志，彻底消除冷启动误报
                        ProcessBuilder pb = new ProcessBuilder("logcat", "-T", "1", "-b", "main", "-b", "system", "-v", "brief", "-s", "VehicleDataBuilder:D", "ecarx_avm_SocketCommand:W", "SerialControl_v2_0:W", "e:D", "ECARX@ECP:D", "ecarx_avm_state:D");
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

        // 3. 解析车辆档位信号: MCULog:GearPosition: x (2=D, 3=N, 4=R, 5=P) 或 VehId=Vehicle_Gear value=0x0x
        if (line.contains("GearPosition:") || line.contains("VehId=Vehicle_Gear value=0x")) {
            int gearVal = -1;
            int idx = line.indexOf("GearPosition:");
            if (idx != -1) {
                try {
                    char c = line.charAt(idx + 13);
                    if (Character.isDigit(c)) {
                        gearVal = Character.getNumericValue(c);
                    }
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
                handleCanSignal("TCU_GearPosition", gearVal);
                return;
            }
        }

        // 4. 解析后备箱/尾门信号 (兼容 MCULog 与 CAN 报文)
        if (line.contains("TRUNK_door_sts") || line.contains("TRUNK_DOOR_STS") ||
            line.contains("TAILGATE_door_sts") || line.contains("TAILGATE_DOOR_STS") ||
            line.contains("BACK_door_sts") || line.contains("BACK_DOOR_STS")) {
            int trunkVal = (line.contains("Open") || line.contains(":0x01") || line.contains(":1")) ? 1 : 0;
            handleCanSignal("BCM_TrunkAjarStatus", trunkVal);
            return;
        }

        // 5. 解析底层 MCU 串口车身报文
        if (line.contains("91 02 01")) {
            try {
                java.util.regex.Matcher sm = SERIAL_DOOR_PATTERN.matcher(line);
                if (sm.find()) {
                    int b6 = Integer.parseInt(sm.group(1).trim(), 16);
                    int b7 = Integer.parseInt(sm.group(2).trim(), 16);
                    // 吉利 E02 真实底盘证实: b7 bit0 (0xF4 时为打开, 0xF5 时为闭合)
                    int trunkVal = ((b7 & 0x01) == 0) ? 1 : 0;
                    handleCanSignal("BCM_TrunkAjarStatus", trunkVal);
                }
            } catch (Exception ignored) {
            }
        }

        // 6. 解析驾驶模式切换信号 (ComfortModule, MCULog, NegativeOneScreen)
        if (line.contains("DM_FUNC_DRIVE_MODE_SELECT value=") || line.contains("MCU Report SwitchMode:") || line.contains("driveModeValue = 5704911")) {
            int modeVal = -1;
            if (line.contains("DM_FUNC_DRIVE_MODE_SELECT value=")) {
                int idx = line.indexOf("DM_FUNC_DRIVE_MODE_SELECT value=");
                try {
                    char c = line.charAt(idx + 32);
                    modeVal = Character.getNumericValue(c);
                } catch (Exception ignored) {}
            } else if (line.contains("driveModeValue = 5704911")) {
                if (line.contains("570491138")) modeVal = 1; // 舒适
                else if (line.contains("570491139")) modeVal = 2; // 运动
                else if (line.contains("570491137")) modeVal = 3; // 经济
                else if (line.contains("570491158")) modeVal = 6; // 智能
            } else if (line.contains("MCU Report SwitchMode:")) {
                int idx = line.indexOf("MCU Report SwitchMode:");
                try {
                    char c = line.charAt(idx + 22);
                    int sm = Character.getNumericValue(c);
                    if (sm == 1) modeVal = 1; // 舒适
                    else if (sm == 0) modeVal = 2; // 运动
                    else if (sm == 3) modeVal = 3; // 经济
                } catch (Exception ignored) {}
            }
            if (modeVal > 0) {
                handleCanSignal("BCM_DriveMode", modeVal);
                return;
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

        // 1. 四门及后备箱上下车迎宾与安全播报（支持每门开关独立控制与开门未完关门抢占式打断）
        // 主驾开门与关门
        if ("BCM_FrontLeftDoorAjarStatus".equals(key)) {
            if (lastDoorFL == -1) {
                lastDoorFL = val; // 启动首包仅记录基准，坚决不播报
                return;
            }
            if (val == 1 && lastDoorFL == 0) {
                if (enableDoorFl) {
                    voicePlayer.play("door_fl.mp3", "主驾车门打开，请注意后方来车");
                }
            } else if (val == 0 && lastDoorFL == 1) {
                if (enableDoorFlClose) {
                    voicePlayer.play("door_fl_close.mp3", "主驾车门已关好");
                }
            }
            lastDoorFL = val;
        }
        // 副驾开门与关门
        else if ("BCM_FrontRightDoorAjarStatus".equals(key)) {
            if (lastDoorFR == -1) {
                lastDoorFR = val; // 启动首包仅记录基准，坚决不播报
                return;
            }
            if (val == 1 && lastDoorFR == 0) {
                if (enableDoorFr) {
                    voicePlayer.play("door_fr.mp3", "欢迎乘车，请注意安全");
                }
            } else if (val == 0 && lastDoorFR == 1) {
                if (enableDoorFrClose) {
                    voicePlayer.play("door_fr_close.mp3", "副驾已就坐，请系好安全带");
                }
            }
            lastDoorFR = val;
        }
        // 左后门开门与关门
        else if ("BCM_RearLeftDoorAjarStatus".equals(key)) {
            if (lastDoorRL == -1) {
                lastDoorRL = val; // 启动首包仅记录基准，坚决不播报
                return;
            }
            if (val == 1 && lastDoorRL == 0) {
                if (enableDoorRl || enableDoorRear) {
                    voicePlayer.play("door_rl.mp3", "左后门打开，请注意车外环境");
                }
            } else if (val == 0 && lastDoorRL == 1) {
                if (enableDoorRlClose) {
                    voicePlayer.play("door_rl_close.mp3", "左后车门已关好");
                }
            }
            lastDoorRL = val;
        }
        // 右后门开门与关门
        else if ("BCM_RearRightDoorAjarStatus".equals(key)) {
            if (lastDoorRR == -1) {
                lastDoorRR = val; // 启动首包仅记录基准，坚决不播报
                return;
            }
            if (val == 1 && lastDoorRR == 0) {
                if (enableDoorRr || enableDoorRear) {
                    voicePlayer.play("door_rr.mp3", "右后门打开，请注意车外环境");
                }
            } else if (val == 0 && lastDoorRR == 1) {
                if (enableDoorRrClose) {
                    voicePlayer.play("door_rr_close.mp3", "右后车门已关好");
                }
            }
            lastDoorRR = val;
        }
        // 后备箱打开与关闭 (抢占式即时打断)
        else if ("BCM_TrunkAjarStatus".equals(key) || "BCM_TailgateAjarStatus".equals(key)) {
            if (lastTrunk == -1) {
                lastTrunk = val; // 启动首包仅记录基准，坚决不播报
                return;
            }
            if (val == 1 && lastTrunk == 0) {
                if (enableTrunkOpen) {
                    voicePlayer.play("trunk_open.mp3", "后备箱已打开");
                }
            } else if (val == 0 && lastTrunk == 1) {
                if (enableTrunkClose) {
                    voicePlayer.play("trunk_close.mp3", "后备箱已关闭");
                }
            }
            lastTrunk = val;
        }
        // 挂挡安全播报 (D/R/P/N 挡位切换，无排队零延迟即时抢占打断)
        else if ("TCU_GearPosition".equals(key)) {
            if (lastGearPosition == -1) {
                lastGearPosition = val; // 启动首包仅记录基准，坚决不盲目播报
                return;
            }
            if (lastGearPosition != val) {
                switch (val) {
                    case 2: // D 挡
                        if (enableGearD) {
                            voicePlayer.play("gear_d.mp3", "前进挡");
                        }
                        break;
                    case 3: // N 挡
                        if (enableGearN) {
                            voicePlayer.play("gear_n.mp3", "空挡");
                        }
                        break;
                    case 4: // R 挡
                        if (enableGearR) {
                            voicePlayer.play("gear_r.mp3", "注意倒车");
                        }
                        break;
                    case 5: // P 挡
                        if (enableGearP) {
                            voicePlayer.play("gear_p.mp3", "已挂入驻车挡");
                        }
                        break;
                }
                lastGearPosition = val;
            }
        }
        // 驾驶模式切换播报 (舒适 / 运动 / 经济 / 智能，快速切换即时抢占打断)
        else if ("BCM_DriveMode".equals(key)) {
            if (lastDriveMode == -1) {
                lastDriveMode = val; // 启动首包仅记录基准
                return;
            }
            if (lastDriveMode != val) {
                if (enableDriveMode) {
                    switch (val) {
                        case 1: // 舒适模式
                            voicePlayer.play("mode_comfort.mp3", "舒适模式");
                            break;
                        case 2: // 运动模式
                            voicePlayer.play("mode_sport.mp3", "运动模式");
                            break;
                        case 3: // 经济模式
                            voicePlayer.play("mode_eco.mp3", "经济模式");
                            break;
                        case 6: // 智能模式
                            voicePlayer.play("mode_smart.mp3", "智能模式");
                            break;
                    }
                }
                lastDriveMode = val;
            }
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
