package app.onepve.geelyconsole.utils;

import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import app.onepve.geelyconsole.services.VehicleAutomationService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 吉利方向盘方控与按键拦截管理器
 * 1. 拦截原厂多媒体 MediaKeyReceiver (拔掉原厂收音机/伴听抢占广播的耳朵)
 * 2. 兼容米小江方控 (CarMedia) 与 控制台独立接管模式
 * 3. 按键通道分级铁律：
 *    - 精确通道 (真实物理 Press/Release)：MCU SWC_TRIP、IMS reportKeyToAdaptApi、
 *      AdaptAPI handleMessage、ecarx_core_server cmd_data 状态位 —— 天然支持单击/双击/长按；
 *    - 合成兜底通道 (按下后 80ms 强制抬手)：HAL do nothing / shouldCallback / onAlreadyHome
 *      —— 结构上永远只能表达单击，仅在对应按键被证实没有精确通道时才启用。
 *    任一按键一旦被证实存在精确通道，其合成兜底通道即刻永久失效，彻底根治
 *    双击与长按被合成通道抢占吞成单击的问题。
 * 4. 支持按键手势化：单击 (Single)、双击 (Double)、长按 (Long Press)
 */
public class SteeringWheelKeyManager {

    private static final String TAG = "SteeringWheelKey";
    private static final String PREFS_NAME = "toolbox_settings";

    // 音频目标源常量与记忆态
    private static final int SOURCE_LOCAL = 1;
    private static final int SOURCE_BLUETOOTH = 2;
    private static int sLastActiveAudioSource = SOURCE_LOCAL;

    // 手势常量
    public static final String GESTURE_SINGLE = "single";
    public static final String GESTURE_DOUBLE = "double";
    public static final String GESTURE_LONG = "long";
    public static final int LONG_PRESS_MS = 1500; // 长按判定门限明确锁定 1.5 秒 (远离 10 秒看门狗硬重启)

    // 接管模式
    public static final String MODE_CARMEDIA_FIRST = "carmedia_first"; // 米小江方控优先
    public static final String MODE_TOOLBOX_ALONE = "toolbox_alone";   // 控制台接管
    public static final String MODE_FACTORY_DEFAULT = "factory_default"; // 恢复原厂

    // 按键功能选项
    public static final String ACTION_OPEN_360 = "open_360";
    public static final String ACTION_OPEN_NAVI = "open_navi";
    public static final String ACTION_OPEN_TOOLBOX = "open_toolbox";
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
    public static final int KEY_BACK = 307;     // 键 1/返回: 原厂返回按键
    public static final int KEY_CALL = 287;     // 键 8: 电话接听/挂断按键
    public static final int KEY_VOICE = 286;    // 键 5: 语音话筒按键
    public static final int KEY_WMODE = 348;    // 键 6: MODE 键
    public static final int KEY_CUSTOM = 349;   // 键 1: 自定义菱形键
    public static final int KEY_HOME_ALREADY = 9999; // 桌面二次按 Home 键

    private static final Pattern WHEEL_KEY_PATTERN =
            Pattern.compile("IMS\\s+reportKeyToAdaptApi\\s*:\\s*(\\d+)\\s+(press|release)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OK_KEY_PATTERN =
            Pattern.compile("shouldCallback:\\s*code\\s*=\\s*(\\d+)\\s+action\\s*=\\s*1", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADAPT_KEY_PATTERN =
            Pattern.compile("handleMessage\\s+down=(true|false).*?keycode=(\\d+)", Pattern.CASE_INSENSITIVE);

    private final Context context;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private long lastTriggerTime = 0;
    private int lastTriggerKey = -1;

    private final Map<Integer, Long> lastKeyTriggerTime = new HashMap<>();
    private static final long KEY_DEBOUNCE_MS = 260L; // 260ms 单键防抖（过滤多源并发日志与物理接触抖动）

    /** cmd_data[0] 按键状态位解析：0x0 = 物理按下，0x1 = 物理抬起 (实车报文实证) */
    private static boolean parseCmdKeyStateIsPress(String raw) {
        try {
            if (raw == null) return false;
            String v = raw.trim().toLowerCase();
            long n = v.startsWith("0x") ? Long.parseLong(v.substring(2), 16) : Long.parseLong(v);
            return n == 0L;
        } catch (Throwable t) {
            return false;
        }
    }

    public SteeringWheelKeyManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 方控统一走原厂按键事件直连通道（logcat reportKeyToAdaptApi / shouldCallback）。
        // 「编号2 音量滚轮垂直下按」是原厂「高德飞屏到仪表盘」功能键，工具箱不再注册任何
        // CarProperty 硬件拦截通道去抢占它（原厂 HAL / CarService 直通实验通道已整体下线）。
        refreshRuntimeCache();
    }

    // ================= 热路径运行态缓存（性能铁律） =================
    // parseKeyFromLine 会被 logcat 全量监听的每一行调用，严禁每行都读 SharedPreferences。
    private volatile boolean wheelMasterCached = true;
    private volatile String wheelModeCached = MODE_CARMEDIA_FIRST;

    /** 刷新热路径缓存（配置变更 / 服务启动时调用一次即可） */
    public void refreshRuntimeCache() {
        try {
            wheelMasterCached = prefs.getBoolean("wheel_master_switch", true);
            wheelModeCached = getWheelMode();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 同步并应用原厂 MediaKeyReceiver 拦截状态
     */
    public void syncMediaKeyReceiverState() {
        refreshRuntimeCache();
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean masterSwitch = wheelMasterCached;
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
            case KEY_BACK: return "back";
            case KEY_CALL: return "call";
            case KEY_VOICE: return "voice";
            case KEY_CUSTOM: return "custom";
            case KEY_HOME_ALREADY: return "home";
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
            if (keyCode == KEY_CUSTOM) return prefs.getString(legacyKey, ACTION_DEFAULT);
            if (keyCode == KEY_OK) return prefs.getString(legacyKey, ACTION_PLAY_PAUSE);
            return prefs.getString(legacyKey, ACTION_DEFAULT);
        }
        return ACTION_DEFAULT;
    }

    /**
     * 在日志行中解析方向盘按键 (单击即发 · 0ms 零延迟极速响应)
     * 方控统一固定走本硬件事件直连通道
     */
    public int parseKeyFromLine(String line) {
        if (!wheelMasterCached) {
            return 0; // 方控总开关已关闭，坚决不匹配任何按键
        }
        if (line == null || line.isEmpty()) return 0;

        // 0. 原厂 MCU 物理按键事件 (MCULog:SWC_TRIP_Press / SWC_TRIP_Release)
        // ⑤ 自定义键 (HAL 侧编码 0x37)
        if (line.contains("SWC_TRIP_Press")) {
            onKeyTriggered(KEY_CUSTOM);
            return KEY_CUSTOM;
        }

        // 1. 标准物理按键 (press / release)
        if (line.contains("reportKeyToAdaptApi")) {
            Matcher m = WHEEL_KEY_PATTERN.matcher(line);
            if (m.find()) {
                try {
                    int code = Integer.parseInt(m.group(1));
                    if (code == 45) code = KEY_OK; // 缤越 COOL IHU516G 滚轮按压硬件码 45 映射为 KEY_OK
                    String act = m.group(2);
                    if ("press".equalsIgnoreCase(act)) {
                        onKeyTriggered(code);
                    }
                    return code;
                } catch (Exception ignored) {}
            }
        }

        // 2. 滚轮下按确认 (OK 键 / shouldCallback 容灾)
        // 铁律：严禁匹配 85 (KEYCODE_MEDIA_PLAY_PAUSE)
        if (line.contains("shouldCallback")) {
            Matcher m2 = OK_KEY_PATTERN.matcher(line);
            if (m2.find()) {
                try {
                    int code = Integer.parseInt(m2.group(1));
                    if (code == KEY_OK || code == 45 || code == 66) {
                        onKeyTriggered(KEY_OK);
                        return KEY_OK;
                    }
                } catch (Exception ignored) {}
            }
        }

        // 3. AdaptAPI 物理按键与返回键 (307/300/348)，携带真实 down=true/false 按下抬起状态
        if (line.contains("handleMessage") && line.contains("keycode=")) {
            try {
                Matcher m3 = ADAPT_KEY_PATTERN.matcher(line);
                if (m3.find()) {
                    boolean isDown = Boolean.parseBoolean(m3.group(1));
                    int code = Integer.parseInt(m3.group(2));
                    if (isDown) {
                        onKeyTriggered(code);
                    }
                    return code;
                }
            } catch (Exception ignored) {}
        }

        // 4. 原厂桌面二次按 Home 键 ([JRWidget_SCROLL][onAlreadyHome])
        if (line.contains("onAlreadyHome")) {
            onKeyTriggered(KEY_HOME_ALREADY);
            return KEY_HOME_ALREADY;
        }

        // 5. DefaultVehicleHal_v2_0: do nothing for this key(0x37 / 0x2d) (HAL 哑键兜底，车友 XML 权威实证)
        if (line.contains("do nothing for this key") || (line.contains("DefaultVehicleHal") && (line.contains("0x37") || line.contains("0x2d")))) {
            if (line.contains("0x37")) {
                onKeyTriggered(KEY_CUSTOM);
                return KEY_CUSTOM;
            } else if (line.contains("0x2d")) {
                onKeyTriggered(KEY_OK);
                return KEY_OK;
            }
        }

        // 6. ecarx_core_server 物理硬按键 (cmd_data[0] = 按键状态 0x0按下 / 0x1抬起, cmd_data[1] = 键码)
        if (line.contains("cmd_data[1]")) {
            try {
                Matcher m = Pattern.compile("cmd_data\\[1\\]\\s*=\\s*(\\d+)").matcher(line);
                if (m.find()) {
                    final int code = Integer.parseInt(m.group(1));
                    if (code == KEY_PREV || code == KEY_NEXT || code == KEY_MUTE || code == KEY_BACK || code == KEY_WMODE) {
                        Matcher m0 = Pattern.compile("cmd_data\\[0\\]\\s*=\\s*(0[xX][0-9a-fA-F]+|\\d+)").matcher(line);
                        if (m0.find()) {
                            if (parseCmdKeyStateIsPress(m0.group(1))) {
                                onKeyTriggered(code);
                            }
                        } else {
                            onKeyTriggered(code);
                        }
                        return code;
                    }
                }
            } catch (Exception ignored) {}
        }
        return 0;
    }

    /**
     * 按键核心触发入口（单击即发 · 0ms 零延迟极速模式）
     */
    public void onKeyTriggered(final int keyCode) {
        if (!wheelMasterCached) return;
        long now = System.currentTimeMillis();
        Long lastTime = lastKeyTriggerTime.get(keyCode);
        if (lastTime != null && (now - lastTime) < KEY_DEBOUNCE_MS) {
            // 同一按键防抖窗口内，过滤硬件触点抖动与多源并发日志重复
            return;
        }
        lastKeyTriggerTime.put(keyCode, now);

        if (MODE_FACTORY_DEFAULT.equals(wheelModeCached)) {
            AppLogger.i("方控按键", "处于[恢复原厂默认]模式，放行按键事件: " + getKeyName(keyCode));
            return;
        }

        final String singleAction = getGestureAction(keyCode, GESTURE_SINGLE);
        if (ACTION_DEFAULT.equals(singleAction)) {
            return;
        }

        AppLogger.i("方控按键", getKeyName(keyCode) + " -> 触发【单击】(0ms极速): " + singleAction);
        executeAction(singleAction);
    }

    /**
     * 按下事件兼容入口
     */
    public void handleKeyDown(final int keyCode) {
        onKeyTriggered(keyCode);
    }

    /**
     * 抬起事件兼容入口
     */
    public void handleKeyUp(final int keyCode) {
        // 单击即发极速模式无需等待抬起
    }

    /**
     * 单次兼容分发入口 (兼容旧调用)
     */
    public void handleWheelKey(int keyCode) {
        onKeyTriggered(keyCode);
    }

    private String getKeyName(int keyCode) {
        switch (keyCode) {
            case KEY_BACK: return "右方向盘 ① 主页/返回键";
            case KEY_OK: return "右方向盘 ② 滚轮垂直按压";
            case KEY_MUTE: return "右方向盘 ③ 静音键";
            case KEY_NEXT: return "右方向盘 ④ 下一曲";
            case KEY_CUSTOM: return "右方向盘 ⑤ 自定义按键";
            case KEY_WMODE: return "右方向盘 ⑥ MODE 键";
            case KEY_PREV: return "右方向盘 ⑦ 上一曲";
            case KEY_VOL_DOWN: return "音量减/滚轮下拨";
            case KEY_VOL_UP: return "音量加/滚轮上拨";
            case KEY_CALL: return "左方向盘 ⑤ 电话按键";
            case KEY_VOICE: return "左方向盘 ⑥ 语音按键";
            case KEY_HOME_ALREADY: return "桌面二次 Home 键";
            default: return "按键(Code:" + keyCode + ")";
        }
    }

    // 同一动作跨通道去重窗口：HAL/logcat/广播可能在短时间内同时到达，只执行一次
    private final Map<String, Long> lastActionTime = new HashMap<>();
    private static final long ACTION_DEDUP_MS = 300;

    // 播放/暂停方向缓存：媒体中心 PlaybackState 广播可能滞后 1~2 秒，
    // 窗口内以上次下发方向推算当前应处状态，杜绝读态滞后导致暂停/播放方向反转
    private volatile long lastToggleActionAt = 0L;
    private volatile boolean lastToggleWasPause = false;
    private static final long TOGGLE_STATE_CACHE_MS = 1200;

    private void executeAction(String action) {
        if (action == null || ACTION_DEFAULT.equals(action)) return;
        long now = System.currentTimeMillis();
        Long last = lastActionTime.get(action);
        if (last != null && (now - last) < ACTION_DEDUP_MS) {
            AppLogger.i("方控按键", "动作 " + action + " " + ACTION_DEDUP_MS + "ms 内重复触发，已去重");
            return;
        }
        lastActionTime.put(action, now);
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
            case ACTION_OPEN_TOOLBOX:
                openToolbox();
                break;
            case ACTION_PLAY_PAUSE:
                // 根治「单击暂停后音乐又自动续播」（2026-09-16 用户真车复现）：
                // 1) 播放判定必须包含原厂多媒体（isAnyMediaPlaying 已修），否则原厂在放时
                //    抑制窗口不武装，原车 EAS 约 1.5 秒后重新仲裁把暂停强行顶回播放；
                // 2) 严禁下发 TOGGLE 型 KEYCODE_MEDIA_PLAY_PAUSE：官方级三重通道会重复注入
                //    （AudioManager 1 次 + 每活跃会话 1 次 + 3 个车机音乐包广播），
                //    toggle 键注入偶数次 = 净效果回原态，且通道间 PlaybackState 异步更新
                //    存在读态竞态会把暂停反向 play 回去。改为状态判定后下发幂等键
                //    （KEYCODE_MEDIA_PAUSE / KEYCODE_MEDIA_PLAY），无论注入几次语义不变。
                // 3) 1.2 秒方向缓存：媒体中心 PlaybackState 广播可能滞后 1~2 秒，
                //    缓存窗口内以上次下发方向推算当前应处状态，杜绝读态滞后导致反向。
                boolean playingNow;
                long toggleNow = System.currentTimeMillis();
                if (toggleNow - lastToggleActionAt < TOGGLE_STATE_CACHE_MS) {
                    playingNow = !lastToggleWasPause; // 上次发 PAUSE → 系统应处暂停态 → 本次视为不在放
                } else {
                    playingNow = isAnyMediaPlaying();
                }
                lastToggleActionAt = toggleNow;
                lastToggleWasPause = playingNow;

                int targetSource = resolveCurrentAudioSource();
                AppLogger.i("方控按键", "play_pause 仲裁目标: targetSource=" + (targetSource == SOURCE_BLUETOOTH ? "BLUETOOTH" : "LOCAL") + ", playingNow=" + playingNow);

                if (playingNow) {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit().putBoolean("user_manually_paused_media", true).apply();
                    // 仅当目标源为手机蓝牙时，才武装 15 秒抑制窗口防反弹；本地音乐绝不抑制蓝牙！
                    if (targetSource == SOURCE_BLUETOOTH) {
                        EasMediaBridge.getInstance(context).suppressAutoWakeAfterUserPause(15000);
                    }
                    sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE, targetSource);
                } else {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit().putBoolean("user_manually_paused_media", false).apply();
                    if (targetSource == SOURCE_BLUETOOTH) {
                        // 核心：用户主动恢复播放时，立即解除暂停抑制窗口，并重新激活选通蓝牙物理通道
                        EasMediaBridge.getInstance(context).clearAutoWakeSuppression();
                        EasMediaBridge.getInstance(context).activateBluetoothChannel();
                    }
                    sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY, targetSource);
                }
                break;
            case ACTION_NEXT_TRACK:
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT, resolveCurrentAudioSource());
                break;
            case ACTION_PREV_TRACK:
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS, resolveCurrentAudioSource());
                break;
            case ACTION_MUTE_TOGGLE:
                toggleMute();
                break;
            case ACTION_SCREEN_OFF:
                turnScreenOff();
                break;
        }
    }

    /**
     * 蓝牙开关是否已打开（蓝牙修复分入口判定：蓝牙关时走纯原厂直发，不武装抑制窗口）。
     */
    private boolean isBluetoothEnabled() {
        try {
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            return ba != null && ba.isEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 判定蓝牙是否处于已连接手机状态（A2DP Sink 已连通）
     */
    private boolean isBluetoothDeviceConnected() {
        try {
            if (EasMediaBridge.getInstance(context).isA2dpSinkConnected()) {
                return true;
            }
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            if (ba != null && ba.isEnabled()) {
                // 11 = BluetoothProfile.A2DP_SINK, 2 = BluetoothProfile.STATE_CONNECTED
                return ba.getProfileConnectionState(11) == 2;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * 获取系统底层当前持有音频焦点的包名（吉利系统权威真源）
     */
    private String getSystemAudioFocusPackage() {
        try {
            String focus = android.provider.Settings.System.getString(context.getContentResolver(), "audio_focus");
            if (focus != null) {
                return focus.trim();
            }
        } catch (Throwable ignored) {}
        return "";
    }

    /**
     * 判定当前活跃音源：SOURCE_BLUETOOTH 还是 SOURCE_LOCAL
     * 覆盖开启蓝牙前、开启蓝牙后、以及多音源共存时的权威真源仲裁
     */
    private int resolveCurrentAudioSource() {
        try {
            String focusPkg = getSystemAudioFocusPackage();
            if ("com.android.bluetooth".equals(focusPkg)) {
                sLastActiveAudioSource = SOURCE_BLUETOOTH;
                return SOURCE_BLUETOOTH;
            }
            if (!focusPkg.isEmpty() && !"app.onepve.geelyconsole".equals(focusPkg)) {
                // 本地应用（如 com.tencent.qqmusiccar、ecarx.xsf.mediacenter 等）明确持有焦点
                sLastActiveAudioSource = SOURCE_LOCAL;
                return SOURCE_LOCAL;
            }

            // 若 focus 为空或自身：通过底层推流与系统音乐状态辅助判定
            if (EasMediaBridge.getInstance(context).isBluetoothChannelActive()) {
                sLastActiveAudioSource = SOURCE_BLUETOOTH;
                return SOURCE_BLUETOOTH;
            }

            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null && am.isMusicActive()) {
                sLastActiveAudioSource = SOURCE_LOCAL;
                return SOURCE_LOCAL;
            }

            // 核心铁律：若蓝牙仅物理连接但并未在推流 (手机未播歌)，绝不能盲目判定为蓝牙切歌源！
            // 彻底杜绝上次蓝牙记忆导致未推流时方控切歌打入黑洞，未推流时恒定回退本地源
        } catch (Throwable ignored) {}
        return SOURCE_LOCAL;
    }

    /**
     * 当前是否有媒体正在播放（用于判定本次 play_pause 究竟会「暂停」还是「恢复」）。
     * 结合吉利系统底层音频焦点、A2DP推流、AudioManager与MediaSession进行权威综合仲裁。
     */
    private boolean isAnyMediaPlaying() {
        try {
            String focusPkg = getSystemAudioFocusPackage();
            if ("com.android.bluetooth".equals(focusPkg)) {
                sLastActiveAudioSource = SOURCE_BLUETOOTH;
                return EasMediaBridge.getInstance(context).isBluetoothChannelActive();
            }
            if (!focusPkg.isEmpty() && !"app.onepve.geelyconsole".equals(focusPkg)) {
                sLastActiveAudioSource = SOURCE_LOCAL;
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (am != null && am.isMusicActive()) {
                    return true;
                }
            }

            if (EasMediaBridge.getInstance(context).isBluetoothChannelActive()) {
                sLastActiveAudioSource = SOURCE_BLUETOOTH;
                return true;
            }

            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null && am.isMusicActive()) {
                sLastActiveAudioSource = SOURCE_LOCAL;
                return true;
            }

            MediaSessionManager msm = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm != null) {
                java.util.List<MediaController> controllers = msm.getActiveSessions(null);
                if (controllers != null) {
                    for (MediaController mc : controllers) {
                        if (mc == null) continue;
                        String pkg = mc.getPackageName();
                        if (context.getPackageName().equals(pkg)) {
                            continue;
                        }
                        android.media.session.PlaybackState st = mc.getPlaybackState();
                        if (st != null && st.getState() == android.media.session.PlaybackState.STATE_PLAYING) {
                            if ("com.android.bluetooth".equals(pkg)) {
                                sLastActiveAudioSource = SOURCE_BLUETOOTH;
                            } else if (!"ecarx.xsf.mediacenter".equals(pkg) && !"com.ecarx.multimedia".equals(pkg)) {
                                sLastActiveAudioSource = SOURCE_LOCAL;
                            }
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void launchCustomApp(String pkg) {
        if (pkg == null || pkg.isEmpty()) return;
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                context.startActivity(intent);
                AppLogger.i("方控按键", "已成功调起自定义应用: " + pkg);
            } else {
                AppLogger.w("方控按键", "未找到应用启动入口: " + pkg);
            }
        } catch (Exception e) {
            AppLogger.e("方控按键", "调起自定义应用失败: " + e.getMessage());
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

    private void openToolbox() {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (intent == null) {
                intent = new Intent(Intent.ACTION_MAIN);
                intent.setComponent(new ComponentName(context.getPackageName(), "app.onepve.geelyconsole.MainActivity"));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            context.startActivity(intent);
            AppLogger.i("方控按键", "已唤醒缤越助手主界面");
            suppressOriginalMultimedia();
        } catch (Throwable t) {
            AppLogger.w("方控按键", "唤醒缤越助手失败: " + t.getMessage());
        }
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
            AppLogger.i("方控按键", "已下发指令秒级唤起 360 全景环视");

            suppressOriginalMultimedia();
        } catch (Exception e) {
            AppLogger.w("方控按键", "唤起 360 失败: " + e.getMessage());
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
                try {
                    // 特权清理原厂多媒体任务栈，杜绝退出 360 环视后原厂多媒体界面冒出
                    SystemUtils.executePrivileged(context, "am force-stop com.ecarx.multimedia");
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    private void openAmapNavi() {
        try {
            String naviPkg = prefs.getString("preferred_navi_pkg", "com.autonavi.amapauto");
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(naviPkg);
            if (intent == null && !"com.autonavi.amapauto".equals(naviPkg)) {
                intent = context.getPackageManager().getLaunchIntentForPackage("com.autonavi.amapauto");
            }
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                context.startActivity(intent);
                AppLogger.i("方控按键", "已唤起当前主力导航: " + (intent.getPackage() != null ? intent.getPackage() : naviPkg));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to launch Navigation: " + e.getMessage());
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
            // 优先采用 Android 原生系统屏保 Somnambulator（车友 XML 123.prj P#29 黄金方案实证）
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(new ComponentName("com.android.systemui", "com.android.systemui.Somnambulator"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(intent);
            AppLogger.i("方控按键", "已唤起 Android 原生屏保息屏 (Somnambulator)");
        } catch (Throwable e) {
            AppLogger.w("方控按键", "调起 Somnambulator 原生屏保失败，尝试回退屏保服务: " + e.getMessage());
            try {
                IdleScreensaverManager.triggerNow(context);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * 媒体按键分发机制 (支持蓝牙通道独占直发与本地播放器分发)
     */
    public void sendMediaKeyEventPublic(int keyCode) {
        int target = resolveCurrentAudioSource();
        sendMediaKeyEvent(keyCode, target);
    }

    private void sendMediaKeyEvent(int keyCode) {
        sendMediaKeyEvent(keyCode, SOURCE_LOCAL);
    }

    private void sendMediaKeyEvent(int keyCode, int targetSource) {
        long now = SystemClock.uptimeMillis();

        // ════════════════════════════════════════════════════════════
        // 分支 A：蓝牙通道独占直发（当目标源为手机蓝牙时）
        // 核心铁律：严禁向 QQ音乐/本地播放器广播媒体键，严禁调用 AudioManager 全局分发，
        // 彻底杜绝本地音乐抢占系统音频焦点导致原生蓝牙协议栈下发 AVRCP PAUSE(70) 秒停手机！
        // ════════════════════════════════════════════════════════════
        if (targetSource == SOURCE_BLUETOOTH) {
            // 核心安全闭环：仅当蓝牙外部音频流真正活跃 (手机端正在推流) 时，才执行蓝牙独占直发
            // 若手机蓝牙仅连接但未在推流 (手机未播歌)，坚决不把按键送入黑洞，平滑回退至本地媒体分发！
            if (EasMediaBridge.getInstance(context).isBluetoothChannelActive()) {
                boolean dispatchedToBt = false;
                try {
                    MediaSessionManager msm = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
                    if (msm != null) {
                        List<MediaController> controllers = msm.getActiveSessions(null);
                        if (controllers != null) {
                            for (MediaController mc : controllers) {
                                if (mc != null && "com.android.bluetooth".equals(mc.getPackageName())) {
                                    if (mc.getTransportControls() != null) {
                                        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                                            mc.getTransportControls().skipToNext();
                                        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                                            mc.getTransportControls().skipToPrevious();
                                        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                                            mc.getTransportControls().pause();
                                        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                                            mc.getTransportControls().play();
                                        } else {
                                            mc.dispatchMediaButtonEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
                                            mc.dispatchMediaButtonEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
                                        }
                                        dispatchedToBt = true;
                                        Log.i(TAG, "Exclusive TransportControls dispatched to com.android.bluetooth (code=" + keyCode + ")");
                                    }
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Exclusive Bluetooth TransportControls error: " + e.getMessage());
                }

                // 若 MediaSession 未命中（极端情况），定向显式广播仅发给 com.android.bluetooth
                if (!dispatchedToBt) {
                    try {
                        Intent down = new Intent(Intent.ACTION_MEDIA_BUTTON);
                        down.setPackage("com.android.bluetooth");
                        down.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
                        context.sendOrderedBroadcast(down, null);

                        Intent up = new Intent(Intent.ACTION_MEDIA_BUTTON);
                        up.setPackage("com.android.bluetooth");
                        up.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
                        context.sendOrderedBroadcast(up, null);
                        Log.i(TAG, "Exclusive directed broadcast sent to com.android.bluetooth (code=" + keyCode + ")");
                    } catch (Exception ignored) {}
                }
                return; // 蓝牙真正推流时独占直发，直接返回！
            } else {
                Log.i(TAG, "Bluetooth connected but not streaming, fallback to local media distribution for keyCode=" + keyCode);
            }
        }

        // ════════════════════════════════════════════════════════════
        // ════════════════════════════════════════════════════════════
        // 分支 B：车机本地播放器 / HiCar / 互联盒子分发
        // 采用动态单点锁定 + MediaSession + 定向显式广播三级精准调度（彻底杜绝多播放器串音）
        // ════════════════════════════════════════════════════════════
        // 1. Android 原生官方推荐通道: AudioManager.dispatchMediaKeyEvent (系统层全局尝试)
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

        // 2. 动态解析本次控制的目标应用（防串音核心真源）
        String targetPkg = resolveTargetMediaPackage();
        Log.i(TAG, "Resolved target media package: " + (targetPkg.isEmpty() ? "NONE" : targetPkg) + " (code=" + keyCode + ")");

        boolean isPlaying = isAnyMediaPlaying();
        boolean isTrackSkip = (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS);

        // 3. MediaSessionManager 传输控制通道 (直接调用目标或活跃会话的 skipToNext / skipToPrevious / 播放暂停)
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
                            String pkg = mc.getPackageName();
                            if (isIgnoredMediaPackage(pkg)) {
                                continue;
                            }
                            // 防串音铁律：若已明确锁定目标应用，只对该应用执行 TransportControls！
                            if (!targetPkg.isEmpty() && !targetPkg.equals(pkg)) {
                                continue;
                            }
                            if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                                mc.getTransportControls().skipToNext();
                                if (!isPlaying) {
                                    mc.getTransportControls().play();
                                }
                            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                                mc.getTransportControls().skipToPrevious();
                                if (!isPlaying) {
                                    mc.getTransportControls().play();
                                }
                            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                                // 幂等暂停：无论状态广播是否滞后，pause() 语义恒定为暂停
                                mc.getTransportControls().pause();
                            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                                // 幂等播放：与 pause() 对称，杜绝 TOGGLE 读态竞态反向
                                mc.getTransportControls().play();
                            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                                android.media.session.PlaybackState state = mc.getPlaybackState();
                                if (state != null && state.getState() == android.media.session.PlaybackState.STATE_PLAYING) {
                                    mc.getTransportControls().pause();
                                } else if (state != null && (state.getState() == android.media.session.PlaybackState.STATE_PAUSED || state.getState() == android.media.session.PlaybackState.STATE_STOPPED)) {
                                    mc.getTransportControls().play();
                                } else {
                                    mc.dispatchMediaButtonEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
                                    mc.dispatchMediaButtonEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
                                }
                            }
                            Log.i(TAG, "TransportControls dispatched to " + pkg);
                            if (!targetPkg.isEmpty()) {
                                break; // 目标已命中，跳出避免多发
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "MediaSession transportControls error: " + e.getMessage());
        }

        // 4. 定向显式广播直达 (针对目标应用或已安装媒体库发送显式 Intent，穿透 Android 9 后台广播限制)
        List<String> broadcastTargets = new ArrayList<>();
        if (!targetPkg.isEmpty()) {
            broadcastTargets.add(targetPkg);
            // 若目标应用当前处于冷态无会话，通过特权通道辅助拉活播放核心，确保按键即时响应
            try {
                VehicleAutomationService vas = VehicleAutomationService.getInstance();
                if (vas != null) {
                    vas.wakeUpTargetMediaService(targetPkg);
                } else if ("com.tencent.qqmusiccar".equals(targetPkg)) {
                    SystemUtils.executePrivileged(context, "am startservice -n com.tencent.qqmusiccar/com.tencent.qqmusicplayerprocess.service.QQPlayerServiceNew");
                }
            } catch (Throwable ignored) {}
        } else {
            // 未锁定具体单一应用时，向整车已安装的媒体应用广播兜底
            broadcastTargets.addAll(getInstalledMediaPackages());
        }

        // 若当前处于未播放状态且车主执行上一首/下一首切歌，必须主动调起播放并起播
        if (!isPlaying && isTrackSkip) {
            for (String pkg : broadcastTargets) {
                try {
                    Intent playDown = new Intent(Intent.ACTION_MEDIA_BUTTON);
                    playDown.setPackage(pkg);
                    playDown.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0));
                    context.sendOrderedBroadcast(playDown, null);

                    Intent playUp = new Intent(Intent.ACTION_MEDIA_BUTTON);
                    playUp.setPackage(pkg);
                    playUp.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0));
                    context.sendOrderedBroadcast(playUp, null);
                } catch (Exception ignored) {}
            }
        }

        for (String pkg : broadcastTargets) {
            try {
                Intent down = new Intent(Intent.ACTION_MEDIA_BUTTON);
                down.setPackage(pkg);
                down.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
                context.sendOrderedBroadcast(down, null);

                Intent up = new Intent(Intent.ACTION_MEDIA_BUTTON);
                up.setPackage(pkg);
                up.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
                context.sendOrderedBroadcast(up, null);
                Log.d(TAG, "Directed media broadcast sent to " + pkg + " (code=" + keyCode + ")");
            } catch (Exception ignored) {}
        }
    }

    /**
     * 静态已知主流互联与媒体播放器包名兜底库（覆盖 HiCar、互联盒子、汽水、酷我、QQ、网易云等）
     */
    public static final String[] KNOWN_MEDIA_PKGS = {
        // 手机互联 / 投屏
        "com.huawei.hicar",              // 华为官方 HiCar 车机端
        "com.huawei.cvd",                // 华为 HiCar 车载协同服务
        "cn.manstep.phonemirrorBox",     // 车连易 / AutoKit 互联盒子
        "cn.manstep.phonemirror",        // AutoKit 系列
        "net.easyconn",                  // 亿连车机端 (EasyConn)
        "com.baidu.carlife",             // 百度 CarLife
        "com.suding.speedplay",          // 速顶 CarPlay / HiCar
        "com.zjinnova.zlink",            // ZLink 互联
        // 主流本地与在线音乐播放器
        "com.luna.music",                // 汽水音乐 (抖音车载/手机版)
        "cn.kuwo.kwmusiccar",            // 酷我音乐车机版
        "cn.kuwo.player",                // 酷我音乐手机版/HD
        "com.tencent.qqmusiccar",        // QQ音乐车机版
        "com.tencent.qqmusic",           // QQ音乐手机版
        "com.netease.cloudmusiccar",     // 网易云音乐车机版
        "com.netease.cloudmusic",        // 网易云音乐手机版
        "com.kugou.androidCar",          // 酷狗音乐车机版
        "com.kugou.player",              // 酷狗音乐手机版
        "com.ximalaya.ting.androidCar",  // 喜马拉雅车机版
        "com.ximalaya.ting.android",     // 喜马拉雅手机版
        "cn.toside.music.mobile",        // 洛雪音乐 (LX Music)
        "com.kyant.music",               // 椒盐音乐 (Salt Player)
        "remix.myplayer"                 // 经典本地播放器
    };

    /**
     * 判断是否为需要排除的系统/原厂或自身包名（严禁打扰或下发播控以免死循环）
     */
    private boolean isIgnoredMediaPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) return true;
        return "ecarx.xsf.mediacenter".equals(pkg)
                || "com.ecarx.multimedia".equals(pkg)
                || "com.android.bluetooth".equals(pkg)
                || context.getPackageName().equals(pkg)
                || "android".equals(pkg)
                || "com.android.systemui".equals(pkg)
                || "com.ecarx.xsf.mediacenter".equals(pkg);
    }

    /**
     * 动态探测整车已安装的声明了媒体控制特性的应用包名（合并系统广播扫描与已知兜底库）
     */
    public List<String> getInstalledMediaPackages() {
        List<String> pkgs = new ArrayList<>();
        try {
            PackageManager pm = context.getPackageManager();
            if (pm != null) {
                // 1. 查询声明接收 ACTION_MEDIA_BUTTON 的广播接收器
                Intent mediaIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
                List<ResolveInfo> receivers = pm.queryBroadcastReceivers(mediaIntent, 0);
                if (receivers != null) {
                    for (ResolveInfo ri : receivers) {
                        if (ri != null && ri.activityInfo != null && ri.activityInfo.packageName != null) {
                            String p = ri.activityInfo.packageName;
                            if (!isIgnoredMediaPackage(p) && !pkgs.contains(p)) {
                                pkgs.add(p);
                            }
                        }
                    }
                }

                // 2. 将静态已知库中已安装的应用合并入列表
                for (String known : KNOWN_MEDIA_PKGS) {
                    if (!pkgs.contains(known)) {
                        try {
                            pm.getPackageInfo(known, 0);
                            pkgs.add(known);
                        } catch (PackageManager.NameNotFoundException ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
        return pkgs;
    }

    /**
     * 权威解析当前媒体播控的目标单一包名（防串音核心）：
     * 1. 优先读取系统底层音频焦点持有者；
     * 2. 其次查询当前正在播放 (STATE_PLAYING) 的 MediaSession；
     * 3. 再次读取上次发声应用的记忆 (last_active_media_pkg)；
     * 4. 再次取任意活跃的 MediaSession；
     * 5. 若均未命中，取整车已安装媒体应用中的首选。
     */
    public String resolveTargetMediaPackage() {
        // 1. 优先读取系统底层音频焦点持有者
        String focusPkg = getSystemAudioFocusPackage();
        if (!focusPkg.isEmpty() && !isIgnoredMediaPackage(focusPkg)) {
            try {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putString("last_active_media_pkg", focusPkg).apply();
            } catch (Throwable ignored) {}
            return focusPkg;
        }

        // 2. 检查是否有活跃且正在播放 (STATE_PLAYING) 的 MediaSession
        try {
            MediaSessionManager msm = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm != null) {
                List<MediaController> controllers = msm.getActiveSessions(null);
                if (controllers != null) {
                    for (MediaController mc : controllers) {
                        if (mc != null && mc.getPlaybackState() != null
                                && mc.getPlaybackState().getState() == android.media.session.PlaybackState.STATE_PLAYING) {
                            String pkg = mc.getPackageName();
                            if (!isIgnoredMediaPackage(pkg)) {
                                try {
                                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                            .edit().putString("last_active_media_pkg", pkg).apply();
                                } catch (Throwable ignored) {}
                                return pkg;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 3. 读取上次发声的记忆应用（若当前仍安装）
        try {
            String lastPkg = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString("last_active_media_pkg", "");
            if (!lastPkg.isEmpty() && !isIgnoredMediaPackage(lastPkg)) {
                try {
                    context.getPackageManager().getPackageInfo(lastPkg, 0);
                    return lastPkg;
                } catch (PackageManager.NameNotFoundException ignored) {}
            }
        } catch (Throwable ignored) {}

        // 3.5 优先读取车主在“车速自启音乐软件”中选定的默认音源应用
        try {
            SharedPreferences sp = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            String configuredPkg = sp.getString("vehicle_speed_autoplay_pkg", null);
            if (configuredPkg != null && !configuredPkg.trim().isEmpty() && !isIgnoredMediaPackage(configuredPkg.trim())) {
                try {
                    context.getPackageManager().getPackageInfo(configuredPkg.trim(), 0);
                    return configuredPkg.trim();
                } catch (PackageManager.NameNotFoundException ignored) {}
            }
        } catch (Throwable ignored) {}

        // 4. 若无记忆，检查是否有任意活跃的非空 MediaSession
        try {
            MediaSessionManager msm = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm != null) {
                List<MediaController> controllers = msm.getActiveSessions(null);
                if (controllers != null) {
                    for (MediaController mc : controllers) {
                        if (mc != null) {
                            String pkg = mc.getPackageName();
                            if (!isIgnoredMediaPackage(pkg)) {
                                return pkg;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 5. 若均未命中，返回已安装列表中的首选媒体应用
        List<String> installed = getInstalledMediaPackages();
        if (!installed.isEmpty()) {
            return installed.get(0);
        }

        return "";
    }
}
