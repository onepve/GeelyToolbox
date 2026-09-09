package app.onepve.geelyconsole.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 吉利方向盘方控与按键拦截管理器
 * 1. 拦截原厂多媒体 MediaKeyReceiver (拔掉原厂收音机/伴听抢占广播的耳朵)
 * 2. 兼容米小江方控 (CarMedia) 与 控制台独立接管模式
 * 3. 监听 logcat 物理按键事件: IMS reportKeyToAdaptApi : (\\d+) (press|release)
 * 4. 支持按键手势化：单击 (Single)、双击 (Double)、长按 (Long Press)
 */
public class SteeringWheelKeyManager {

    private static final String TAG = "SteeringWheelKey";
    private static final String PREFS_NAME = "toolbox_settings";

    // 手势常量
    public static final String GESTURE_SINGLE = "single";
    public static final String GESTURE_DOUBLE = "double";
    public static final String GESTURE_LONG = "long";

    // 接管模式
    public static final String MODE_CARMEDIA_FIRST = "carmedia_first"; // 米小江方控优先
    public static final String MODE_TOOLBOX_ALONE = "toolbox_alone";   // 控制台接管
    public static final String MODE_FACTORY_DEFAULT = "factory_default"; // 恢复原厂

    // 按键功能选项
    public static final String ACTION_OPEN_360 = "open_360";
    public static final String ACTION_OPEN_NAVI = "open_navi";
    public static final String ACTION_PLAY_PAUSE = "play_pause";
    public static final String ACTION_NEXT_TRACK = "next_track";
    public static final String ACTION_PREV_TRACK = "prev_track";
    public static final String ACTION_MUTE_TOGGLE = "mute_toggle";
    public static final String ACTION_SCREEN_OFF = "screen_off";
    public static final String ACTION_DEFAULT = "default";

    // 键码定义 (SX-0017)
    public static final int KEY_MUTE = 300;     // 键 3: 静音键短按
    public static final int KEY_VOL_DOWN = 301; // 键 2: 滚轮向下拨动 (音量减)
    public static final int KEY_VOL_UP = 302;   // 键 2: 滚轮向上拨动 (音量加)
    public static final int KEY_PREV = 304;     // 键 7: 上一曲
    public static final int KEY_NEXT = 305;     // 键 4: 下一曲
    public static final int KEY_OK = 306;       // 键 2: 滚轮下按确认
    public static final int KEY_WMODE = 348;    // 键 6: MODE 键
    public static final int KEY_CUSTOM = 349;   // 键 1: 自定义菱形键

    private static final Pattern WHEEL_KEY_PATTERN =
            Pattern.compile("IMS\\s+reportKeyToAdaptApi\\s*:\\s*(\\d+)\\s+(press|release)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OK_KEY_PATTERN =
            Pattern.compile("shouldCallback:\\s*code\\s*=\\s*(\\d+)\\s+action\\s*=\\s*1", Pattern.CASE_INSENSITIVE);

    private final Context context;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private long lastTriggerTime = 0;
    private int lastTriggerKey = -1;

    // 手势状态追踪 (单击/双击/长按)
    private final Map<Integer, Long> keyDownTimes = new HashMap<>();
    private final Map<Integer, Boolean> isLongPressed = new HashMap<>();
    private final Map<Integer, Integer> clickCounts = new HashMap<>();
    private final Map<Integer, Runnable> pendingClickTasks = new HashMap<>();
    private final Map<Integer, Runnable> pendingLongTasks = new HashMap<>();

    public SteeringWheelKeyManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 同步并应用原厂 MediaKeyReceiver 拦截状态
     */
    public void syncMediaKeyReceiverState() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean masterSwitch = prefs.getBoolean("wheel_master_switch", true);
                if (!masterSwitch) {
                    // 方控总开关已关闭：彻底解禁原厂 MediaKeyReceiver，完全不拦截！
                    try {
                        PackageManager pm = context.getPackageManager();
                        ComponentName comp = new ComponentName("ecarx.xsf.mediacenter", "ecarx.xsf.mediacenter.MediaKeyReceiver");
                        pm.setComponentEnabledSetting(comp, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                    } catch (Exception ignored) {}
                    try {
                        AdbClient.execute(context, "pm enable ecarx.xsf.mediacenter/ecarx.xsf.mediacenter.MediaKeyReceiver");
                    } catch (Exception ignored) {}
                    Log.i(TAG, "Wheel master switch is OFF, MediaKeyReceiver restored to ENABLED");
                    return;
                }

                String mode = getWheelMode();
                boolean shouldBlock = !MODE_FACTORY_DEFAULT.equals(mode);
                try {
                    PackageManager pm = context.getPackageManager();
                    ComponentName comp = new ComponentName("ecarx.xsf.mediacenter", "ecarx.xsf.mediacenter.MediaKeyReceiver");
                    int newState = shouldBlock ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                    pm.setComponentEnabledSetting(comp, newState, PackageManager.DONT_KILL_APP);
                } catch (Exception ignored) {}

                // 遵循车规纯净解耦：仅停用广播接收器 MediaKeyReceiver，坚决不破坏/冻结整个多媒体应用包
                try {
                    if (shouldBlock) {
                        AdbClient.execute(context, "pm disable-user --user 0 ecarx.xsf.mediacenter/ecarx.xsf.mediacenter.MediaKeyReceiver");
                        Log.i(TAG, "Successfully disabled MediaKeyReceiver via ADB");
                    } else {
                        AdbClient.execute(context, "pm enable ecarx.xsf.mediacenter/ecarx.xsf.mediacenter.MediaKeyReceiver");
                        Log.i(TAG, "Successfully re-enabled MediaKeyReceiver via ADB");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "syncMediaKeyReceiverState via ADB failed: " + e.getMessage());
                }
            }
        }).start();
    }

    public String getWheelMode() {
        if (!prefs.contains("wheel_control_mode")) {
            boolean hasCarMedia = SystemUtils.isPackageInstalled(context, "com.ecarx.carmedia");
            return hasCarMedia ? MODE_CARMEDIA_FIRST : MODE_TOOLBOX_ALONE;
        }
        return prefs.getString("wheel_control_mode", MODE_CARMEDIA_FIRST);
    }

    public String getKeyPrefix(int keyCode) {
        switch (keyCode) {
            case KEY_OK: return "ok";
            case KEY_MUTE: return "mute";
            case KEY_WMODE: return "mode";
            case KEY_NEXT: return "next";
            case KEY_PREV: return "prev";
            case KEY_CUSTOM: return "custom";
            default: return "key_" + keyCode;
        }
    }

    public String getGestureAction(int keyCode, String gesture) {
        String prefix = getKeyPrefix(keyCode);
        String specificKey = "wheel_action_" + prefix + "_" + gesture;
        if (prefs.contains(specificKey)) {
            return prefs.getString(specificKey, ACTION_DEFAULT);
        }
        // 兼容回退老配置 (单击模式)
        if (GESTURE_SINGLE.equals(gesture)) {
            String legacyKey = "wheel_action_" + prefix;
            if (keyCode == KEY_WMODE) return prefs.getString(legacyKey, ACTION_OPEN_360);
            if (keyCode == KEY_NEXT) return prefs.getString(legacyKey, ACTION_NEXT_TRACK);
            if (keyCode == KEY_PREV) return prefs.getString(legacyKey, ACTION_PREV_TRACK);
            return prefs.getString(legacyKey, ACTION_DEFAULT);
        }
        return ACTION_DEFAULT;
    }

    /**
     * 在日志行中解析方向盘按键 (兼容 press 按下与 release 抬起事件，实现单击/双击/长按)
     */
    public int parseKeyFromLine(String line) {
        if (!prefs.getBoolean("wheel_master_switch", true)) {
            return 0; // 方控总开关已关闭，坚决不匹配任何按键
        }
        if (line == null || line.isEmpty()) return 0;

        // 1. 标准物理按键 (press / release)
        if (line.contains("reportKeyToAdaptApi")) {
            Matcher m = WHEEL_KEY_PATTERN.matcher(line);
            if (m.find()) {
                try {
                    int code = Integer.parseInt(m.group(1));
                    if (code == 45) code = KEY_OK; // 缤越 COOL IHU516G 滚轮按压硬件码 45 映射为 KEY_OK
                    String act = m.group(2);
                    if ("press".equalsIgnoreCase(act)) {
                        handleKeyDown(code);
                    } else if ("release".equalsIgnoreCase(act)) {
                        handleKeyUp(code);
                    }
                    return code;
                } catch (Exception ignored) {}
            }
        }

        // 2. 滚轮下按确认 (OK 键 / shouldCallback 容灾)
        if (line.contains("shouldCallback")) {
            Matcher m2 = OK_KEY_PATTERN.matcher(line);
            if (m2.find()) {
                try {
                    int code = Integer.parseInt(m2.group(1));
                    if (code == KEY_OK || code == 45 || code == 85 || code == 66) {
                        handleKeyDown(KEY_OK);
                        mainHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                handleKeyUp(KEY_OK);
                            }
                        }, 80);
                        return KEY_OK;
                    }
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    /**
     * 按下事件：记录时间并启动 500ms 长按检测定时器
     */
    public void handleKeyDown(final int keyCode) {
        if (!prefs.getBoolean("wheel_master_switch", true)) return;
        long now = System.currentTimeMillis();
        keyDownTimes.put(keyCode, now);
        isLongPressed.put(keyCode, false);

        final String longAction = getGestureAction(keyCode, GESTURE_LONG);
        if (!ACTION_DEFAULT.equals(longAction)) {
            Runnable lpTask = new Runnable() {
                @Override
                public void run() {
                    isLongPressed.put(keyCode, true);
                    AppLogger.i("方控手势", getKeyName(keyCode) + " -> 触发【长按】: " + longAction);
                    executeAction(longAction);
                }
            };
            pendingLongTasks.put(keyCode, lpTask);
            mainHandler.postDelayed(lpTask, 500); // 500ms 触发长按
        }
    }

    /**
     * 抬起事件：取消长按，判定单击或多击 (0ms 极速响应优化)
     */
    public void handleKeyUp(final int keyCode) {
        if (!prefs.getBoolean("wheel_master_switch", true)) return;
        Runnable lpTask = pendingLongTasks.remove(keyCode);
        if (lpTask != null) {
            mainHandler.removeCallbacks(lpTask);
        }

        // 若长按已触发，松手时静默放行
        if (Boolean.TRUE.equals(isLongPressed.get(keyCode))) {
            isLongPressed.put(keyCode, false);
            return;
        }

        String mode = getWheelMode();
        if (MODE_FACTORY_DEFAULT.equals(mode)) {
            AppLogger.i("方控总线", "处于[恢复原厂默认]模式，完全放行按键事件给车机原厂总线");
            return;
        }

        final String doubleAction = getGestureAction(keyCode, GESTURE_DOUBLE);
        final String singleAction = getGestureAction(keyCode, GESTURE_SINGLE);

        // 极速 0ms 优化铁律：若未配置双击动作，坚决不等待 260ms，0 毫秒立即瞬发执行单击！
        if (ACTION_DEFAULT.equals(doubleAction)) {
            AppLogger.i("方控手势", getKeyName(keyCode) + " -> 触发【单击】(0ms极速): " + singleAction);
            executeAction(singleAction);
            return;
        }

        // 配置了双击：进入 260ms 多击判定时间窗口
        int count = clickCounts.getOrDefault(keyCode, 0) + 1;
        clickCounts.put(keyCode, count);

        Runnable oldTask = pendingClickTasks.remove(keyCode);
        if (oldTask != null) {
            mainHandler.removeCallbacks(oldTask);
        }

        Runnable evalTask = new Runnable() {
            @Override
            public void run() {
                int finalCount = clickCounts.getOrDefault(keyCode, 1);
                clickCounts.remove(keyCode);
                pendingClickTasks.remove(keyCode);

                if (finalCount == 1) {
                    AppLogger.i("方控手势", getKeyName(keyCode) + " -> 触发【单击】: " + singleAction);
                    executeAction(singleAction);
                } else if (finalCount >= 2) {
                    AppLogger.i("方控手势", getKeyName(keyCode) + " -> 触发【双击】: " + doubleAction);
                    executeAction(doubleAction);
                }
            }
        };
        pendingClickTasks.put(keyCode, evalTask);
        mainHandler.postDelayed(evalTask, 260);
    }

    /**
     * 单次兼容分发入口 (兼容旧调用)
     */
    public void handleWheelKey(int keyCode) {
        handleKeyDown(keyCode);
        handleKeyUp(keyCode);
    }

    private String getKeyName(int keyCode) {
        switch (keyCode) {
            case KEY_MUTE: return "3号键·静音键";
            case KEY_VOL_DOWN: return "2号键·音量减/滚轮下拨";
            case KEY_VOL_UP: return "2号键·音量加/滚轮上拨";
            case KEY_PREV: return "7号键·上一曲";
            case KEY_NEXT: return "4号键·下一曲";
            case KEY_OK: return "2号键·滚轮垂直按压";
            case KEY_WMODE: return "6号键·Mode音源切换";
            case KEY_CUSTOM: return "1号键·菱形自定义";
            default: return "按键(Code:" + keyCode + ")";
        }
    }

    private void executeAction(String action) {
        if (action == null || ACTION_DEFAULT.equals(action)) return;
        Log.i(TAG, "Executing wheel action: " + action);
        if (action.startsWith("app:")) {
            String pkg = action.substring(4).trim();
            launchCustomApp(pkg);
            return;
        }
        switch (action) {
            case ACTION_OPEN_360:
                open360Camera();
                break;
            case ACTION_OPEN_NAVI:
                openAmapNavi();
                break;
            case ACTION_PLAY_PAUSE:
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                break;
            case ACTION_NEXT_TRACK:
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT);
                break;
            case ACTION_PREV_TRACK:
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                break;
            case ACTION_MUTE_TOGGLE:
                toggleMute();
                break;
            case ACTION_SCREEN_OFF:
                turnScreenOff();
                break;
        }
    }

    private void launchCustomApp(String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                context.startActivity(intent);
                AppLogger.i("方控动作", "已成功调起自定义应用: " + pkg);
            } else {
                AppLogger.w("方控动作", "未找到应用启动入口: " + pkg);
            }
        } catch (Exception e) {
            AppLogger.e("方控动作", "调起自定义应用失败: " + e.getMessage());
        }
    }

    private void cancelNativeMute() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    if (am != null) {
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
                    }
                } catch (Exception ignored) {}
            }
        }, 150);
    }

    private void open360Camera() {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage("ecarx.camera.calibration");
            if (intent == null) {
                intent = new Intent(Intent.ACTION_MAIN);
                intent.setComponent(new ComponentName("ecarx.camera.calibration", "ecarx.camera.calibration.MainActivity"));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(intent);
            AppLogger.i("方控动作", "已下发指令秒级唤起 360 全景环视");

            suppressOriginalMultimedia();
        } catch (Exception e) {
            AppLogger.w("方控动作", "唤起 360 失败: " + e.getMessage());
        }
    }

    public void suppressOriginalMultimedia() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent pauseIntent = new Intent("ecarx.intent.action.STOP");
                    pauseIntent.setPackage("com.ecarx.multimedia");
                    context.sendBroadcast(pauseIntent);
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    private void openAmapNavi() {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage("com.autonavi.amapauto");
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                context.startActivity(intent);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to launch Amap: " + e.getMessage());
        }
    }

    private void toggleMute() {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                boolean muted = am.isStreamMute(AudioManager.STREAM_MUSIC);
                am.setStreamMute(AudioManager.STREAM_MUSIC, !muted);
            }
        } catch (Exception ignored) {}
    }

    private void turnScreenOff() {
        try {
            Intent intent = new Intent("android.intent.action.SCREEN_OFF");
            context.sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    /**
     * 官方级三重通道媒体按键分发机制 (100% 解决 QQ音乐/网易云 切歌与播放暂停)
     */
    private void sendMediaKeyEvent(int keyCode) {
        long now = SystemClock.uptimeMillis();

        // 1. Android 原生官方推荐通道: AudioManager.dispatchMediaKeyEvent
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
                am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
                Log.i(TAG, "Dispatched media key via AudioManager: " + keyCode);
            }
        } catch (Exception e) {
            Log.w(TAG, "AudioManager.dispatchMediaKeyEvent error: " + e.getMessage());
        }

        // 2. MediaSessionManager 传输控制通道 (直接调用当前活跃会话的 skipToNext / skipToPrevious)
        try {
            MediaSessionManager msm = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm != null) {
                List<MediaController> controllers = null;
                try {
                    controllers = msm.getActiveSessions(null);
                } catch (Exception ignored) {}
                if (controllers != null) {
                    for (MediaController mc : controllers) {
                        if (mc != null && mc.getTransportControls() != null) {
                            if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                                mc.getTransportControls().skipToNext();
                            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                                mc.getTransportControls().skipToPrevious();
                            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                                mc.getTransportControls().pause();
                            }
                            Log.i(TAG, "TransportControls dispatched to " + mc.getPackageName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "MediaSession transportControls error: " + e.getMessage());
        }

        // 3. 定向广播直达 (针对 QQ音乐车机版 / 网易云车机版 / 酷狗车机版 发送显式 Intent)
        String[] targetPkgs = {"com.tencent.qqmusiccar", "com.netease.cloudmusiccar", "com.kugou.androidCar"};
        for (String pkg : targetPkgs) {
            try {
                Intent down = new Intent(Intent.ACTION_MEDIA_BUTTON);
                down.setPackage(pkg);
                down.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
                context.sendOrderedBroadcast(down, null);

                Intent up = new Intent(Intent.ACTION_MEDIA_BUTTON);
                up.setPackage(pkg);
                up.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
                context.sendOrderedBroadcast(up, null);
            } catch (Exception ignored) {}
        }
    }
}
