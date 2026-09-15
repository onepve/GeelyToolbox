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

    // 手势状态追踪 (单击/双击/长按 严格4相物理闭环状态机)
    private enum KeyPhase {
        IDLE,                // 空闲态
        DOWN_1,              // 第 1 次物理按下中
        UP_1_WAITING_DOWN_2, // 第 1 次松开完成，等待第 2 次物理按下
        DOWN_2               // 第 2 次物理按下中，等待第 2 次松开
    }

    private static final long DOUBLE_CLICK_WINDOW_MS = 380L; // 双击有效窗口期（380ms，自然贴合人类按键节奏）
    private static final long MIN_CLICK_INTERVAL_MS = 60L;   // 两次物理按下间的最小有效间隔（过滤多源重复日志）
    private static final long MIN_PRESS_DURATION_MS = 35L;   // 单次按压最小有效持续时长（防触点毛刺）

    private final Map<Integer, KeyPhase> keyPhases = new HashMap<>();
    private final Map<Integer, Long> phaseDownTime1 = new HashMap<>();
    private final Map<Integer, Long> phaseUpTime1 = new HashMap<>();
    private final Map<Integer, Long> phaseDownTime2 = new HashMap<>();
    private final Map<Integer, Boolean> isLongPressed = new HashMap<>();
    private final Map<Integer, Runnable> pendingSingleTasks = new HashMap<>();
    private final Map<Integer, Runnable> pendingLongTasks = new HashMap<>();

    // ================= 精确通道登记表 (真实物理 Press/Release 源) =================
    // 铁律：只要某按键被证实存在真实物理 Press/Release 源，它的「合成兜底通道」
    // (HAL do nothing / shouldCallback / cmd_data 无状态位) 即刻永久失效。
    // 合成兜底通道的本质是「按下后 80ms 强制抬手」，结构上永远无法表达双击与长按；
    // 它与精确源并发抢占时还会把真实相位吞掉 (实测被吞掉约 6 成按键)。
    private final Map<Integer, Boolean> preciseChannelProven = new HashMap<>();
    // 合成兜底按下令牌：真实物理按下到达即作废尚未执行的强制抬手，防误吞相位
    private final Map<Integer, Boolean> syntheticPressActive = new HashMap<>();

    /** 该按键是否已被证实存在精确物理通道 (内存缓存 + prefs 持久化，避免长年重启后重学) */
    private boolean isPreciseChannelProven(int keyCode) {
        Boolean cached = preciseChannelProven.get(keyCode);
        if (cached != null) return cached;
        boolean proven = false;
        try {
            proven = prefs.getBoolean("wheel_precise_proven_" + keyCode, false);
        } catch (Throwable ignored) {
        }
        preciseChannelProven.put(keyCode, proven);
        return proven;
    }

    /** 登记一次真实物理事件：作废合成残留相位，并把该键永久升格为精确通道 */
    private void markPreciseEvent(int keyCode) {
        syntheticPressActive.remove(keyCode);
        if (Boolean.TRUE.equals(preciseChannelProven.get(keyCode))) return;
        preciseChannelProven.put(keyCode, true);
        try {
            prefs.edit().putBoolean("wheel_precise_proven_" + keyCode, true).apply();
        } catch (Throwable ignored) {
        }
    }

    /** 合成兜底通道统一闸门：精确通道已证实的按键，坚决不再走任何合成通道 */
    private boolean syntheticFallbackDisabled(int keyCode) {
        return isPreciseChannelProven(keyCode);
    }

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

    /** 合成兜底专用：按下 80ms 后强制抬手 (仅用于完全没有精确源的按键) */
    private void syntheticPressAndAutoRelease(final int keyCode) {
        handleKeyDown(keyCode);
        syntheticPressActive.put(keyCode, Boolean.TRUE);
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (syntheticPressActive.remove(keyCode) != null) {
                    handleKeyUp(keyCode);
                }
            }
        }, 80);
    }

    /**
     * 精确通道抬手补齐：MCU 与原厂核心服务在极速连发时，会把某些真实点击的
     * 「按下 + 抬起」写进同一毫秒 (实车日志实测 36 次按下中有 15 次如此)。
     * 直接抬手会被 MIN_PRESS_DURATION_MS 毛刺门限整次吞掉，故不足最小有效
     * 按压时长时，把抬手补齐到 35ms 再交给 4 相状态机，确保单击/双击/长按不被吞。
     */
    private void preciseRelease(final int keyCode) {
        KeyPhase phase = keyPhases.getOrDefault(keyCode, KeyPhase.IDLE);
        long downAt = System.currentTimeMillis();
        Long recorded = (phase == KeyPhase.DOWN_2) ? phaseDownTime2.get(keyCode) : phaseDownTime1.get(keyCode);
        if (recorded != null) downAt = recorded;
        long held = System.currentTimeMillis() - downAt;
        long pad = MIN_PRESS_DURATION_MS - held;
        if (pad > 0 && pad <= MIN_PRESS_DURATION_MS) {
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    handleKeyUp(keyCode);
                }
            }, pad);
        } else {
            handleKeyUp(keyCode);
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
     * 在日志行中解析方向盘按键 (兼容 press 按下与 release 抬起事件，实现单击/双击/长按)
     * 方控统一固定走本硬件事件直连通道（已下线「HAL 纯协议直连」双轨测试模式）
     */
    public int parseKeyFromLine(String line) {
        if (!wheelMasterCached) {
            return 0; // 方控总开关已关闭，坚决不匹配任何按键
        }
        if (line == null || line.isEmpty()) return 0;

        // 0. 原厂 MCU 精确物理按键事件 (MCULog:SWC_TRIP_Press / SWC_TRIP_Release)
        // ⑤ 自定义键 (HAL 侧编码 0x37) 在 HAL 上是「do nothing」哑键，但 MCU 始终广播完整的
        // Press/Release 物理闭环，实测按住 1.2 秒即真实上报 1.2 秒时长 —— 这是该键双击与
        // 长按的唯一可靠来源 (HAL 合成通道只能表达单击)。
        if (line.contains("SWC_TRIP_Press") || line.contains("SWC_TRIP_Release")) {
            markPreciseEvent(KEY_CUSTOM);
            if (line.contains("SWC_TRIP_Press")) {
                handleKeyDown(KEY_CUSTOM);
            } else {
                preciseRelease(KEY_CUSTOM);
            }
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
                    markPreciseEvent(code);
                    if ("press".equalsIgnoreCase(act)) {
                        handleKeyDown(code);
                    } else if ("release".equalsIgnoreCase(act)) {
                        handleKeyUp(code);
                    }
                    return code;
                } catch (Exception ignored) {}
            }
        }

        // 2. 滚轮下按确认 (OK 键 / shouldCallback 容灾)：无真实抬手事件，仅合成兜底
        // 铁律：严禁匹配 85 (KEYCODE_MEDIA_PLAY_PAUSE)！媒体按键下发后系统会输出 shouldCallback(85)，
        // 若将 85 误判为 OK 键，会导致按一次暂停无限自反馈死循环下发暂停！
        if (line.contains("shouldCallback")) {
            Matcher m2 = OK_KEY_PATTERN.matcher(line);
            if (m2.find()) {
                try {
                    int code = Integer.parseInt(m2.group(1));
                    if (code == KEY_OK || code == 45 || code == 66) {
                        if (!syntheticFallbackDisabled(KEY_OK)) {
                            syntheticPressAndAutoRelease(KEY_OK);
                        }
                        return KEY_OK;
                    }
                } catch (Exception ignored) {}
            }
        }

        // 3. AdaptAPI 物理按键与返回键 (307/300)，携带真实 down=true/false 按下抬起状态
        if (line.contains("handleMessage") && line.contains("keycode=")) {
            try {
                Matcher m3 = ADAPT_KEY_PATTERN.matcher(line);
                if (m3.find()) {
                    boolean isDown = Boolean.parseBoolean(m3.group(1));
                    int code = Integer.parseInt(m3.group(2));
                    markPreciseEvent(code);
                    if (isDown) {
                        handleKeyDown(code);
                    } else {
                        handleKeyUp(code);
                    }
                    return code;
                }
            } catch (Exception ignored) {}
        }

        // 4. 原厂桌面二次按 Home 键 ([JRWidget_SCROLL][onAlreadyHome])：无精确源，仅合成兜底
        if (line.contains("onAlreadyHome")) {
            syntheticPressAndAutoRelease(KEY_HOME_ALREADY);
            return KEY_HOME_ALREADY;
        }

        // 5. DefaultVehicleHal_v2_0: do nothing for this key(0x37 / 0x2d) (HAL 哑键合成兜底)
        // 该通道只能伪造「按下 + 80ms 强制抬手」，仅在对应按键被证实没有精确物理通道时才启用。
        if (line.contains("do nothing for this key") || (line.contains("DefaultVehicleHal") && (line.contains("0x37") || line.contains("0x2d")))) {
            if (line.contains("0x37")) {
                if (syntheticFallbackDisabled(KEY_CUSTOM)) return 0;
                Long lastDown = phaseDownTime1.get(KEY_CUSTOM);
                long now = System.currentTimeMillis();
                if (lastDown == null || (now - lastDown) > 400) {
                    syntheticPressAndAutoRelease(KEY_CUSTOM);
                }
                return KEY_CUSTOM;
            } else if (line.contains("0x2d")) {
                if (syntheticFallbackDisabled(KEY_OK)) return 0;
                Long lastDown = phaseDownTime1.get(KEY_OK);
                long now = System.currentTimeMillis();
                if (lastDown == null || (now - lastDown) > 400) {
                    syntheticPressAndAutoRelease(KEY_OK);
                }
                return KEY_OK;
            }
        }

        // 6. ecarx_core_server 物理硬按键 (cmd_data[0] = 按键状态 0x0按下 / 0x1抬起, cmd_data[1] = 键码)
        // 实测原厂核心服务已随包广播真实按下/抬起状态位，故此处升级为精确通道：
        // ④下一曲 / ⑦上一曲 / ③静音 / ①返回 由此获得完整单击 / 双击 / 长按能力。
        if (line.contains("cmd_data[1]")) {
            try {
                Matcher m = Pattern.compile("cmd_data\\[1\\]\\s*=\\s*(\\d+)").matcher(line);
                if (m.find()) {
                    final int code = Integer.parseInt(m.group(1));
                    if (code == KEY_PREV || code == KEY_NEXT || code == KEY_MUTE || code == KEY_BACK) {
                        Matcher m0 = Pattern.compile("cmd_data\\[0\\]\\s*=\\s*(0[xX][0-9a-fA-F]+|\\d+)").matcher(line);
                        if (m0.find()) {
                            // 精确通道：真实按下 / 抬起物理闭环
                            markPreciseEvent(code);
                            if (parseCmdKeyStateIsPress(m0.group(1))) {
                                handleKeyDown(code);
                            } else {
                                preciseRelease(code);
                            }
                            return code;
                        }
                        // 旧固件无状态位：仅在精确通道未被证实时退化为合成兜底
                        if (!syntheticFallbackDisabled(code)) {
                            Long lastDown = phaseDownTime1.get(code);
                            long now = System.currentTimeMillis();
                            // 若 400ms 内已有硬件 reportKeyToAdaptApi 触发，坚决不重复分发假按键
                            if (lastDown == null || (now - lastDown) > 400) {
                                syntheticPressAndAutoRelease(code);
                            }
                        }
                        return code;
                    }
                }
            } catch (Exception ignored) {}
        }
        return 0;
    }

    /**
     * 按下事件：严格 4 相物理状态机第一相/第三相驱动
     */
    public void handleKeyDown(final int keyCode) {
        if (!wheelMasterCached) return;
        long now = System.currentTimeMillis();
        KeyPhase currentPhase = keyPhases.getOrDefault(keyCode, KeyPhase.IDLE);

        // 如果已处于按下态中 (DOWN_1 或 DOWN_2)，多源并发日志直接防抖忽略
        if (currentPhase == KeyPhase.DOWN_1 || currentPhase == KeyPhase.DOWN_2) {
            return;
        }

        // 阶段 1: 空闲状态下物理按下 -> 确立第 1 次按下
        if (currentPhase == KeyPhase.IDLE) {
            keyPhases.put(keyCode, KeyPhase.DOWN_1);
            phaseDownTime1.put(keyCode, now);
            isLongPressed.put(keyCode, false);

            final String longAction = getGestureAction(keyCode, GESTURE_LONG);
            if (!ACTION_DEFAULT.equals(longAction)) {
                final int lpMs = prefs.getInt("wheel_long_press_ms", LONG_PRESS_MS);
                Runnable lpTask = new Runnable() {
                    @Override
                    public void run() {
                        isLongPressed.put(keyCode, true);
                        keyPhases.put(keyCode, KeyPhase.IDLE);
                        AppLogger.i("方控按键", getKeyName(keyCode) + " -> 触发【长按 " + (lpMs / 1000.0f) + "秒】: " + longAction);
                        executeAction(longAction);
                    }
                };
                pendingLongTasks.put(keyCode, lpTask);
                mainHandler.postDelayed(lpTask, lpMs);
            }
            return;
        }

        // 阶段 3: 处于 UP_1_WAITING_DOWN_2 (第 1 次松开完成，正在等待第 2 次物理按下)
        if (currentPhase == KeyPhase.UP_1_WAITING_DOWN_2) {
            Long up1 = phaseUpTime1.get(keyCode);
            long interval = (up1 != null) ? (now - up1) : 9999L;

            // 物理防抖：如果第二次按下与第一次松开相隔 < 60ms，判定为日志重叠或毛刺杂波，直接滤除
            if (interval < MIN_CLICK_INTERVAL_MS) {
                AppLogger.i("方控按键", getKeyName(keyCode) + " 抬起与再次按下仅隔 " + interval + "ms，判定为信号抖动杂波，已滤除");
                return;
            }

            // 用户真正进行了第 2 次物理按下！立即取消单机定时任务
            Runnable singleTask = pendingSingleTasks.remove(keyCode);
            if (singleTask != null) {
                mainHandler.removeCallbacks(singleTask);
            }

            // 跃迁至第 2 次物理按下中
            keyPhases.put(keyCode, KeyPhase.DOWN_2);
            phaseDownTime2.put(keyCode, now);

            // 800ms 自愈防卡死看门狗
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (keyPhases.get(keyCode) == KeyPhase.DOWN_2) {
                        keyPhases.put(keyCode, KeyPhase.IDLE);
                    }
                }
            }, 800);
        }
    }

    /**
     * 抬起事件：严格 4 相物理状态机第二相/第四相驱动
     */
    public void handleKeyUp(final int keyCode) {
        if (!wheelMasterCached) return;
        long now = System.currentTimeMillis();
        KeyPhase currentPhase = keyPhases.getOrDefault(keyCode, KeyPhase.IDLE);

        // 如果在空闲态或等待按下态收到 KeyUp（无前置物理按下），直接丢弃
        if (currentPhase == KeyPhase.IDLE || currentPhase == KeyPhase.UP_1_WAITING_DOWN_2) {
            return;
        }

        // 取消可能存在的长按定时器
        Runnable lpTask = pendingLongTasks.remove(keyCode);
        if (lpTask != null) {
            mainHandler.removeCallbacks(lpTask);
        }

        // 若长按已触发，松手时静默归位
        if (Boolean.TRUE.equals(isLongPressed.get(keyCode))) {
            isLongPressed.put(keyCode, false);
            keyPhases.put(keyCode, KeyPhase.IDLE);
            return;
        }

        if (MODE_FACTORY_DEFAULT.equals(wheelModeCached)) {
            keyPhases.put(keyCode, KeyPhase.IDLE);
            AppLogger.i("方控按键", "处于[恢复原厂默认]模式，完全放行按键事件给车机原厂总线");
            return;
        }

        final String singleAction = getGestureAction(keyCode, GESTURE_SINGLE);
        final String doubleAction = getGestureAction(keyCode, GESTURE_DOUBLE);

        // 阶段 2: 处理第 1 次松手 (DOWN_1 -> UP_1)
        if (currentPhase == KeyPhase.DOWN_1) {
            Long down1 = phaseDownTime1.get(keyCode);
            long pressDuration = (down1 != null) ? (now - down1) : 100L;
            if (pressDuration < MIN_PRESS_DURATION_MS) {
                // 按压时长过短，判定为接触不良毛刺
                keyPhases.put(keyCode, KeyPhase.IDLE);
                return;
            }

            // 极速 0ms 优化铁律：若未配置双击动作，坚决不等待，0 毫秒立即瞬发执行单击！
            if (ACTION_DEFAULT.equals(doubleAction)) {
                keyPhases.put(keyCode, KeyPhase.IDLE);
                AppLogger.i("方控按键", getKeyName(keyCode) + " -> 触发【单击】(0ms极速): " + singleAction);
                executeAction(singleAction);
                return;
            }

            // 配置了双击：进入 UP_1_WAITING_DOWN_2，启动 380ms 物理双击等待窗口
            keyPhases.put(keyCode, KeyPhase.UP_1_WAITING_DOWN_2);
            phaseUpTime1.put(keyCode, now);

            Runnable singleTask = new Runnable() {
                @Override
                public void run() {
                    // 380ms 窗口期内未等到合法的第 2 次完整按下松开，确认触发【单击】
                    if (keyPhases.get(keyCode) == KeyPhase.UP_1_WAITING_DOWN_2) {
                        keyPhases.put(keyCode, KeyPhase.IDLE);
                        pendingSingleTasks.remove(keyCode);
                        AppLogger.i("方控按键", getKeyName(keyCode) + " -> 触发【单击】: " + singleAction);
                        executeAction(singleAction);
                    }
                }
            };
            pendingSingleTasks.put(keyCode, singleTask);
            mainHandler.postDelayed(singleTask, DOUBLE_CLICK_WINDOW_MS);
            return;
        }

        // 阶段 4: 处理第 2 次松手 (DOWN_2 -> 完成【按下➔松开➔按下➔松开】4 相物理闭环)
        if (currentPhase == KeyPhase.DOWN_2) {
            Long down2 = phaseDownTime2.get(keyCode);
            long pressDuration2 = (down2 != null) ? (now - down2) : 100L;

            if (pressDuration2 < MIN_PRESS_DURATION_MS) {
                // 第 2 次按压过短 (多源重复报文 / 触点毛刺)：绝不允许它白吞车主的真实单击！
                // 回滚至「等待第 2 次按下」相位，并按剩余窗口重新武装单击兜底任务。
                pendingSingleTasks.remove(keyCode);
                keyPhases.put(keyCode, KeyPhase.UP_1_WAITING_DOWN_2);
                Long up1 = phaseUpTime1.get(keyCode);
                long elapsed = (up1 != null) ? (now - up1) : DOUBLE_CLICK_WINDOW_MS;
                long remain = Math.max(0L, DOUBLE_CLICK_WINDOW_MS - elapsed);
                Runnable fallbackSingleTask = new Runnable() {
                    @Override
                    public void run() {
                        if (keyPhases.get(keyCode) == KeyPhase.UP_1_WAITING_DOWN_2) {
                            keyPhases.put(keyCode, KeyPhase.IDLE);
                            pendingSingleTasks.remove(keyCode);
                            AppLogger.i("方控按键", getKeyName(keyCode) + " -> 触发【单击】: " + singleAction);
                            executeAction(singleAction);
                        }
                    }
                };
                pendingSingleTasks.put(keyCode, fallbackSingleTask);
                mainHandler.postDelayed(fallbackSingleTask, remain);
                return;
            }

            keyPhases.put(keyCode, KeyPhase.IDLE);
            pendingSingleTasks.remove(keyCode);

            // 严格满足：【按下1 ➔ 松开1 ➔ 按下2 ➔ 松开2】4 相全流程物理闭环！
            AppLogger.i("方控按键", getKeyName(keyCode) + " -> 触发【双击】(严格4相物理闭环): " + doubleAction);
            executeAction(doubleAction);
        }
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
            Intent intent = new Intent("android.intent.action.SCREEN_OFF");
            context.sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    /**
     * 官方级三重通道媒体按键分发机制 (100% 解决 QQ音乐/网易云 切歌与播放暂停)
     */
    public void sendMediaKeyEventPublic(int keyCode) {
        sendMediaKeyEvent(keyCode);
    }

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

        // 2. MediaSessionManager 传输控制通道 (直接调用当前第三方活跃会话的 skipToNext / skipToPrevious / 播放暂停)
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
                            // 关键保护：跳过车机原厂多媒体与自身，严禁向原厂下发 TransportControls 导致 EAS 回调触发死循环
                            if ("ecarx.xsf.mediacenter".equals(pkg) || "com.ecarx.multimedia".equals(pkg) || context.getPackageName().equals(pkg)) {
                                continue;
                            }
                            if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                                mc.getTransportControls().skipToNext();
                            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
                                mc.getTransportControls().skipToPrevious();
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
