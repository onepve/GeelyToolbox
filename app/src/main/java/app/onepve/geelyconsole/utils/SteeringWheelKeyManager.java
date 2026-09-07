package app.onepve.geelyconsole.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 吉利方向盘方控与按键拦截管理器
 * 1. 拦截原厂多媒体 MediaKeyReceiver (拔掉原厂收音机/伴听抢占广播的耳朵)
 * 2. 兼容米小江方控 (CarMedia):
 *    - 模式 A: 米小江优先 (放行 304/305 切歌与 348 Mode 键，工具箱仅补充静音键/滚轮自定义)
 *    - 模式 B: 控制台独立接管 (工具箱全面接管 348 Mode键/300 静音键/滚轮按压/切歌)
 *    - 模式 C: 完全恢复原厂 (解禁 MediaKeyReceiver，零拦截)
 * 3. 监听 logcat 物理按键事件: IMS reportKeyToAdaptApi : (\d+) (press|release)
 */
public class SteeringWheelKeyManager {

    private static final String TAG = "SteeringWheelKey";
    private static final String PREFS_NAME = "toolbox_settings";

    // 接管模式
    public static final String MODE_CARMEDIA_FIRST = "carmedia_first"; // 米小江方控优先
    public static final String MODE_TOOLBOX_ALONE = "toolbox_alone";   // 控制台接管
    public static final String MODE_FACTORY_DEFAULT = "factory_default"; // 恢复原厂

    // 按键功能选项
    public static final String ACTION_OPEN_360 = "open_360";
    public static final String ACTION_OPEN_NAVI = "open_navi";
    public static final String ACTION_PLAY_PAUSE = "play_pause";
    public static final String ACTION_MUTE_TOGGLE = "mute_toggle";
    public static final String ACTION_DEFAULT = "default";

    // 键码定义 (SX-0017)
    public static final int KEY_MUTE = 300;     // 键 3: 静音键短按
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

    public SteeringWheelKeyManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 同步并应用原厂 MediaKeyReceiver 拦截状态
     */
    public void syncMediaKeyReceiverState() {
        String mode = getWheelMode();
        boolean shouldBlock = !MODE_FACTORY_DEFAULT.equals(mode);
        try {
            PackageManager pm = context.getPackageManager();
            ComponentName comp = new ComponentName("ecarx.xsf.mediacenter", "ecarx.xsf.mediacenter.MediaKeyReceiver");
            int newState = shouldBlock ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
            pm.setComponentEnabledSetting(comp, newState, PackageManager.DONT_KILL_APP);
            Log.i(TAG, "MediaKeyReceiver enabled setting set to: " + (shouldBlock ? "DISABLED" : "ENABLED"));
        } catch (Exception e) {
            // 尝试通过 Shell 强制执行
            try {
                if (shouldBlock) {
                    AdbClient.execute(context, "pm disable-user --user 0 ecarx.xsf.mediacenter/ecarx.xsf.mediacenter.MediaKeyReceiver");
                } else {
                    AdbClient.execute(context, "pm enable ecarx.xsf.mediacenter/ecarx.xsf.mediacenter.MediaKeyReceiver");
                }
            } catch (Exception ignored) {}
            Log.w(TAG, "MediaKeyReceiver toggle fallback via shell: " + e.getMessage());
        }
    }

    public String getWheelMode() {
        return prefs.getString("wheel_control_mode", MODE_CARMEDIA_FIRST);
    }

    /**
     * 在日志行中解析方向盘按键
     * @return 触发的键码，0 为未匹配
     */
    public int parseKeyFromLine(String line) {
        if (line == null || line.isEmpty()) return 0;
        
        // 1. 标准物理按键 (press)
        if (line.contains("reportKeyToAdaptApi")) {
            Matcher m = WHEEL_KEY_PATTERN.matcher(line);
            if (m.find()) {
                if ("press".equalsIgnoreCase(m.group(2))) {
                    try {
                        return Integer.parseInt(m.group(1));
                    } catch (Exception ignored) {}
                }
            }
        }
        
        // 2. 滚轮下按确认 (OK 键)
        if (line.contains("shouldCallback")) {
            Matcher m2 = OK_KEY_PATTERN.matcher(line);
            if (m2.find()) {
                try {
                    int code = Integer.parseInt(m2.group(1));
                    if (code == KEY_OK) return code;
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    /**
     * 分发并执行按键逻辑
     */
    public void handleWheelKey(int keyCode) {
        long now = System.currentTimeMillis();
        // 350ms 防抖，防止连点
        if (keyCode == lastTriggerKey && (now - lastTriggerTime < 350)) {
            return;
        }
        lastTriggerKey = keyCode;
        lastTriggerTime = now;

        String mode = getWheelMode();
        if (MODE_FACTORY_DEFAULT.equals(mode)) {
            // 原厂模式，完全不拦截
            return;
        }

        Log.i(TAG, "Handling wheel key: " + keyCode + " under mode: " + mode);

        // 1. 米小江优先模式 (放行 304, 305, 348 给米小江处理，工具箱仅处理按键 3 静音 与 按键 2 滚轮按压)
        if (MODE_CARMEDIA_FIRST.equals(mode)) {
            if (keyCode == KEY_MUTE) {
                // 静音键短按
                String muteAction = prefs.getString("wheel_action_mute", ACTION_OPEN_360);
                executeAction(muteAction);
                if (!ACTION_DEFAULT.equals(muteAction) && !ACTION_MUTE_TOGGLE.equals(muteAction)) {
                    cancelNativeMute();
                }
            } else if (keyCode == KEY_OK) {
                // 滚轮下按
                String okAction = prefs.getString("wheel_action_ok", ACTION_DEFAULT);
                executeAction(okAction);
            } else if (keyCode == KEY_CUSTOM) {
                // 自定义菱形键
                String customAction = prefs.getString("wheel_action_custom", ACTION_DEFAULT);
                executeAction(customAction);
            }
            // 304/305/348 主动放行给米小江，工具箱绝不争抢
            return;
        }

        // 2. 控制台接管模式 (全量自定义)
        if (MODE_TOOLBOX_ALONE.equals(mode)) {
            if (keyCode == KEY_WMODE) {
                // Mode 键
                String modeAction = prefs.getString("wheel_action_mode", ACTION_OPEN_360);
                executeAction(modeAction);
            } else if (keyCode == KEY_MUTE) {
                // 静音键短按
                String muteAction = prefs.getString("wheel_action_mute", ACTION_OPEN_360);
                executeAction(muteAction);
                if (!ACTION_DEFAULT.equals(muteAction) && !ACTION_MUTE_TOGGLE.equals(muteAction)) {
                    cancelNativeMute();
                }
            } else if (keyCode == KEY_OK) {
                // 滚轮下按
                String okAction = prefs.getString("wheel_action_ok", ACTION_PLAY_PAUSE);
                executeAction(okAction);
            } else if (keyCode == KEY_PREV) {
                // 上一曲 (模拟切歌)
                sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            } else if (keyCode == KEY_NEXT) {
                // 下一曲 (模拟切歌)
                sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_NEXT);
            } else if (keyCode == KEY_CUSTOM) {
                String customAction = prefs.getString("wheel_action_custom", ACTION_OPEN_360);
                executeAction(customAction);
            }
        }
    }

    private void executeAction(String action) {
        if (action == null || ACTION_DEFAULT.equals(action)) return;
        Log.i(TAG, "Executing wheel action: " + action);
        switch (action) {
            case ACTION_OPEN_360:
                open360Camera();
                break;
            case ACTION_OPEN_NAVI:
                openAmapNavi();
                break;
            case ACTION_PLAY_PAUSE:
                sendMediaKeyEvent(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                break;
            case ACTION_MUTE_TOGGLE:
                toggleMute();
                break;
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
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                context.startActivity(intent);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to launch 360: " + e.getMessage());
        }
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

    private void sendMediaKeyEvent(int keyCode) {
        try {
            Intent down = new Intent(Intent.ACTION_MEDIA_BUTTON);
            down.putExtra(Intent.EXTRA_KEY_EVENT, new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode));
            context.sendOrderedBroadcast(down, null);

            Intent up = new Intent(Intent.ACTION_MEDIA_BUTTON);
            up.putExtra(Intent.EXTRA_KEY_EVENT, new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode));
            context.sendOrderedBroadcast(up, null);
        } catch (Exception e) {
            Log.w(TAG, "Failed to send media key event: " + e.getMessage());
        }
    }
}
