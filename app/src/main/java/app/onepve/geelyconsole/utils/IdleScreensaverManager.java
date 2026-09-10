package app.onepve.geelyconsole.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 闲置自动屏保管理器（主页面闲置 X 秒后自动进入原厂屏保）
 *
 * 【为什么需要它】
 * 原厂车机只在「下拉快捷栏」里提供一个手动「屏保」磁贴，没有「闲置 N 秒自动进入」的选项，
 * 而原厂车辆 API 的屏保时间也只有 5 / 10 / 永不三个固定档且需要特权权限。因此本类自建计时器。
 *
 * 【屏保调用链路（固件逆推实证，非猜测）】
 * ecarx.settings 的下拉磁贴 ScreenSaverWidget（action = "SpecialScreenSaver"）实际执行：
 *     Intent i = new Intent("android.intent.action.SCREENSAVER");
 *     i.addCategory("android.intent.category.SCREENSAVER");
 *     i.setPackage("com.ecarx.screensaver");
 *     context.startService(i);
 * 目标为 com.ecarx.screensaver/.ScreensaverService。本类 1:1 复刻该链路。
 *
 * 【闲置检测 · 双通道，互不依赖】
 *  通道 A（主）：每 3 秒读取 dumpsys power 的 mLastUserActivityTime，精确得到闲置毫秒数，
 *               到点即进屏保（不黑屏、无闪烁）。
 *  通道 B（备）：监听系统 ACTION_SCREEN_OFF。若通道 A 因权限/命令不可用而失效，
 *               车机自身休眠熄屏的瞬间也会兜底触发屏保。此通道不修改任何系统设置。
 *
 * 【生效条件】
 *  默认仅在「主页面（桌面）」闲置时触发；导航/音乐/设置等界面闲置绝不打扰。
 *  主页面通过 ForegroundAppDetector 动态识别（不写死原厂桌面包名，兼容第三方桌面）。
 *
 * 【功耗说明】
 *  每 3 秒一次 dumpsys power（输出仅数行）与嘟嘟桌面的「主页无操作」检测同频，
 *  仅在开关开启且服务常驻时运行；关闭开关后本类完全停止，零轮询、零功耗。
 */
public final class IdleScreensaverManager {

    private static final String TAG = "GeelyToolbox_IdleSS";

    /** 日志模块（沿用「电源状态」通道，语义最贴合且默认开启） */
    private static final String LOG_MODULE = "电源状态";

    // ---------------- 配置项 ----------------
    public static final String PREFS = "toolbox_settings";
    public static final String KEY_ENABLED = "screensaver_idle_enabled";
    public static final String KEY_SECONDS = "screensaver_idle_seconds";
    public static final String KEY_HOME_ONLY = "screensaver_home_only";

    public static final int MIN_SECONDS = 10;
    public static final int MAX_SECONDS = 30;
    public static final int DEFAULT_SECONDS = 20;

    // ---------------- 屏保链路常量 ----------------
    public static final String ACTION_SCREENSAVER = "android.intent.action.SCREENSAVER";
    public static final String CATEGORY_SCREENSAVER = "android.intent.category.SCREENSAVER";
    public static final String PKG_SCREENSAVER = "com.ecarx.screensaver";

    // ---------------- 计时参数 ----------------
    /** 轮询间隔：与嘟嘟桌面「主页无操作」的 3 秒检测同频 */
    private static final long TICK_MS = 3000L;
    /** 闲置低于该值即认为用户已重新活动，重新武装计时器 */
    private static final long REARM_IDLE_MS = 3000L;
    /** 触发后最短冷却，防止屏保被立刻重复拉起的抖动 */
    private static final long COOLDOWN_MS = 8000L;

    private static final Pattern P_ACTIVITY =
            Pattern.compile("mLastUserActivityTime=([^\\r\\n]+)");
    private static final Pattern P_WAKE =
            Pattern.compile("mWakefulness=([A-Za-z]+)");
    private static final Pattern P_TIME_FULL = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?$");
    private static final Pattern P_TIME_SHORT = Pattern.compile(
            "^(\\d{2})-(\\d{2}) \\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?$");
    /** 真实 AOSP 打印形态：「107514142 (506236 ms ago)」——括号内即是权威闲置毫秒数 */
    private static final Pattern P_DELTA_AGO =
            Pattern.compile("(-?\\d+)\\s*ms\\s*ago");
    private static final Pattern P_LEAD_NUM =
            Pattern.compile("^(-?\\d{5,})");
    private static final Pattern P_DURATION_TOKEN =
            Pattern.compile("(\\d+)\\s*(d|h|m|s|ms)\\b");

    // ---------------- 运行态 ----------------
    private static HandlerThread workerThread;
    private static Handler worker;
    private static volatile boolean running = false;
    /** 是否处于「可触发」状态；触发一次后需等用户重新活动才重新武装 */
    private static volatile boolean armed = true;
    private static volatile long lastTriggerAt = 0L;
    private static volatile long lastIdleMs = -1L;
    private static volatile long lastReadOkAt = 0L;
    private static volatile int readFailStreak = 0;
    private static volatile String channelState = "未启动";
    /** 通道 A 最近一次读取的原始输出与解析到的原始值（诊断用，前端直接展示，免车主去猜） */
    private static volatile String lastRawDump = "";
    private static volatile String lastRawValue = "";
    /** 通道 A 是否曾经读取成功过（用于区分「从未成功」与「偶发失败」） */
    private static volatile boolean channelAReady = false;
    private static volatile String lastFailReason = "";
    private static BroadcastReceiver screenOffReceiver;
    private static Context appCtx;

    private IdleScreensaverManager() {
    }

    // ==================================================================
    // 生命周期
    // ==================================================================

    /** 由常驻守护服务调用；内部自行判定开关，关闭时不做任何事 */
    public static synchronized void start(Context ctx) {
        if (ctx == null) return;
        appCtx = ctx.getApplicationContext();
        if (running) {
            // 已在运行：仅刷新配置（开关可能刚被打开/调整）
            return;
        }
        if (!isEnabled(appCtx)) {
            channelState = "已关闭";
            return;
        }
        running = true;
        armed = true;
        try {
            workerThread = new HandlerThread("ToolboxIdleSS");
            workerThread.start();
            worker = new Handler(workerThread.getLooper());
            worker.postDelayed(tick, TICK_MS);
            registerScreenOffReceiver(appCtx);
            channelState = "运行中(通道A轮询)";
            AppLogger.i(LOG_MODULE, "闲置自动屏保已启动：闲置 " + getSeconds(appCtx) + " 秒且"
                    + (isHomeOnly(appCtx) ? "位于主页面" : "任意界面") + "时自动进入原厂屏保");
        } catch (Throwable e) {
            running = false;
            channelState = "启动失败: " + e.getMessage();
            AppLogger.e(LOG_MODULE, "闲置自动屏保启动失败: " + e.getMessage());
        }
    }

    /** 停止轮询与广播监听（关闭开关或服务销毁时调用） */
    public static synchronized void stop() {
        running = false;
        armed = true;
        try {
            if (worker != null) {
                worker.removeCallbacksAndMessages(null);
                worker = null;
            }
            if (workerThread != null) {
                workerThread.quit();
                workerThread = null;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (screenOffReceiver != null && appCtx != null) {
                appCtx.unregisterReceiver(screenOffReceiver);
            }
        } catch (Throwable ignored) {
        }
        screenOffReceiver = null;
        channelState = "已停止";
    }

    /** 配置变更后由桥接层调用：按新配置重启或停止 */
    public static synchronized void restart(Context ctx) {
        stop();
        if (ctx != null) {
            appCtx = ctx.getApplicationContext();
            start(appCtx);
        }
    }

    /** 通道 B：系统熄屏兜底（不修改任何系统设置，纯粹借用系统自身的休眠事件） */
    private static void registerScreenOffReceiver(Context ctx) {
        try {
            if (screenOffReceiver != null) return;
            screenOffReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        if (!Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) return;
                        if (!isEnabled(context)) return;
                        if (isHomeOnly(context)
                                && !ForegroundAppDetector.isHomeForeground(context)) {
                            return;
                        }
                        channelState = "触发中(通道B熄屏兜底)";
                        triggerScreensaver(context, "系统熄屏兜底");
                    } catch (Throwable e) {
                        Log.w(TAG, "screenOff trigger failed: " + e.getMessage());
                    }
                }
            };
            ctx.registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        } catch (Throwable e) {
            Log.w(TAG, "registerScreenOffReceiver failed: " + e.getMessage());
        }
    }

    // ==================================================================
    // 轮询主循环（通道 A）
    // ==================================================================

    private static final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            try {
                doTick();
            } catch (Throwable e) {
                Log.w(TAG, "tick error: " + e.getMessage());
            } finally {
                if (running && worker != null) {
                    worker.postDelayed(this, TICK_MS);
                }
            }
        }
    };

    private static void doTick() {
        Context ctx = appCtx;
        if (ctx == null) return;
        if (!isEnabled(ctx)) {
            channelState = "已关闭";
            return;
        }

        long idle = readIdleMs(ctx);
        if (idle < 0) {
            readFailStreak++;
            channelAReady = false;
            lastFailReason = TextUtils.isEmpty(lastRawDump)
                    ? "系统未返回闲置时间（dumpsys power 无输出：需在车机上开启 ADB 调试授权）"
                    : ("已读到系统内容但无法解析，原始值=" + lastRawValue);
            if (readFailStreak == 3) {
                channelState = "通道A读数失败，已启用通道B熄屏兜底";
                AppLogger.w(LOG_MODULE, "闲置时间读取失败（通道A不可用）→ " + lastFailReason
                        + "；已自动切换为系统熄屏兜底（通道B）");
            } else if (readFailStreak == 4) {
                channelState = "通道A不可用(已降级通道B)";
            } else if (readFailStreak > 4 && readFailStreak % 20 == 0) {
                AppLogger.w(LOG_MODULE, "闲置时间仍读取失败，已连续 " + readFailStreak + " 次，持续使用通道B兜底");
            }
            return;
        }
        if (!channelAReady) {
            channelAReady = true;
            AppLogger.i(LOG_MODULE, "闲置时间通道A已连通（原始值=" + lastRawValue + "），精确计时应答生效");
        }
        if (readFailStreak > 3) {
            AppLogger.i(LOG_MODULE, "闲置时间读取已恢复，重新启用通道A精确触发");
        }
        readFailStreak = 0;
        lastFailReason = "";
        lastIdleMs = idle;
        lastReadOkAt = System.currentTimeMillis();

        long threshold = getSeconds(ctx) * 1000L;

        // 1) 用户重新活动 -> 重新武装
        if (!armed) {
            if (idle < REARM_IDLE_MS) {
                armed = true;
                channelState = "运行中(通道A轮询)";
            } else {
                return;
            }
        }

        // 2) 冷却期内不触发
        if (System.currentTimeMillis() - lastTriggerAt < COOLDOWN_MS) {
            return;
        }

        // 3) 未达阈值
        if (idle < threshold) {
            return;
        }

        // 4) 屏保已经在显示 -> 视为已消耗，等用户重新活动
        if (ForegroundAppDetector.isScreenSaverForeground(ctx)) {
            armed = false;
            channelState = "运行中(屏保显示中)";
            return;
        }

        // 5) 生效条件：仅主页面
        if (isHomeOnly(ctx) && !ForegroundAppDetector.isHomeForeground(ctx)) {
            channelState = "运行中(当前不在主页面，不计时)";
            return;
        }

        triggerScreensaver(ctx, "主页面闲置 " + (idle / 1000) + " 秒");
    }

    // ==================================================================
    // 触发
    // ==================================================================

    /** 立即触发一次（供前端「立即测试」按钮使用），返回可读结果 */
    public static String triggerNow(Context ctx) {
        if (ctx == null) return "[FAIL] 上下文为空";
        return triggerScreensaver(ctx, "手动测试");
    }

    private static synchronized String triggerScreensaver(Context ctx, String reason) {
        lastTriggerAt = System.currentTimeMillis();
        armed = false;
        try {
            Intent i = new Intent(ACTION_SCREENSAVER);
            i.addCategory(CATEGORY_SCREENSAVER);
            i.setPackage(PKG_SCREENSAVER);
            ctx.startService(i);
            AppLogger.action(LOG_MODULE, "自动进入原厂屏保", true, reason + " -> startService(SCREENSAVER)");
            channelState = "已触发(" + reason + ")";
            return "[OK] 已调起原厂屏保（" + reason + "）";
        } catch (Throwable e) {
            AppLogger.action(LOG_MODULE, "自动进入原厂屏保失败", false,
                    reason + " -> " + e.getClass().getSimpleName() + " " + e.getMessage());
            channelState = "触发失败: " + e.getMessage();
            return "[FAIL] " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    // ==================================================================
    // 闲置时间读取与解析（通道 A）
    // ==================================================================

    /**
     * 读取系统「最后一次用户交互」距现在经过的毫秒数。
     * 读取或解析失败返回 -1（调用方据此判定通道 A 不可用并降级）。
     */
    private static long readIdleMs(Context ctx) {
        String dump = null;
        try {
            // 硬超时 3 秒：dumpsys 在未开启 ADB/无 root 的车机上可能长时间无响应，
            // 旧实现会让计时线程永久卡死（表现为「开了屏保但 10 秒后毫无反应」）
            dump = SystemUtils.executeWithTimeout(ctx,
                    "dumpsys power | grep -E \"mLastUserActivityTime|mWakefulness=\"", 3000L);
        } catch (Throwable e) {
            Log.w(TAG, "dumpsys power failed: " + e.getMessage());
            return -1L;
        }
        lastRawDump = (dump == null) ? "" : dump.trim();
        if (TextUtils.isEmpty(dump)) {
            return -1L;
        }

        // 息屏/休眠状态下无需触发（屏保是亮屏态的展示）
        try {
            Matcher mw = P_WAKE.matcher(dump);
            if (mw.find()) {
                String wake = mw.group(1);
                if ("Asleep".equalsIgnoreCase(wake) || "Dozing".equalsIgnoreCase(wake)) {
                    return 0L; // 视为刚活动，避免误触发
                }
            }
        } catch (Throwable ignored) {
        }

        return parseIdleMs(dump, System.currentTimeMillis(), SystemClock.uptimeMillis());
    }

    /**
     * 解析 dumpsys power 中的 mLastUserActivityTime 并换算闲置毫秒。
     *
     * 车机 ROM 实测存在多种打印形态，全部兼容（按可靠性从高到低）：
     *  a) 真实 AOSP 标准形态：{@code mLastUserActivityTime=107514142 (506236 ms ago)}
     *     —— 括号内即为权威闲置毫秒，直接采用；
     *  b) 纯 uptime 数字：{@code mLastUserActivityTime=107514142}（用 uptime 相减）；
     *  c) 墙钟时间串：{@code yyyy-MM-dd HH:mm:ss[.SSS]}（用墙钟相减）；
     *  d) 未来式时长串：{@code +0h 3m 12s 345ms}（按持续时长直接当闲置）；
     *  e) 短日期串：{@code MM-dd HH:mm:ss.SSS}（TimeUtils.formatUptime 渲染形态）。
     *
     * 任何解析异常、或换算结果离谱（> 12 小时 / 负值）一律判定为不可用返回 -1，
     * 由调用方降级，坚决杜绝「1970 年时间戳」被当成 56 年闲置而瞬间误触发。
     */
    static long parseIdleMs(String dump, long nowWallMs, long nowUptimeMs) {
        if (TextUtils.isEmpty(dump)) return -1L;
        String value = null;
        Matcher m = P_ACTIVITY.matcher(dump);
        while (m.find()) {
            // 同一份 dump 可能打印多次，取最后一次（最新）
            value = m.group(1);
        }
        if (value == null) return -1L;
        value = value.trim();
        if (value.isEmpty()) return -1L;
        lastRawValue = value;

        // a) 标准形态：「107514142 (506236 ms ago)」——括号内为权威闲置毫秒
        Matcher mDelta = P_DELTA_AGO.matcher(value);
        if (mDelta.find()) {
            try {
                return saneIdle(Long.parseLong(mDelta.group(1)));
            } catch (Throwable ignored) {
            }
        }

        // b) 纯 uptime 数字（或数字 + 其它尾缀，取开头数字）
        Matcher mNum = P_LEAD_NUM.matcher(value);
        if (mNum.find()) {
            try {
                long v = Long.parseLong(mNum.group(1));
                // 典型 uptime 量级（毫秒）才按 uptime 换算，防止把年份误当 uptime
                if (v >= 0 && v < 100000000000L) {
                    return saneIdle(nowUptimeMs - v);
                }
            } catch (Throwable ignored) {
            }
        }

        // c) 完整墙钟时间串
        if (P_TIME_FULL.matcher(value).matches()) {
            String[] patterns = {"yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss"};
            for (String p : patterns) {
                try {
                    SimpleDateFormat fmt = new SimpleDateFormat(p, Locale.CHINA);
                    Date d = fmt.parse(value);
                    if (d != null) {
                        return saneIdle(nowWallMs - d.getTime());
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        // d) 未来式时长串：「+0h 3m 12s 345ms」
        long dur = parseDurationMs(value);
        if (dur >= 0) {
            return saneIdle(dur);
        }

        // e) 短日期串：「MM-dd HH:mm:ss.SSS」（按当年同月同日解析）
        Matcher ms = P_TIME_SHORT.matcher(value);
        if (ms.matches()) {
            try {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                String parsed = String.format(Locale.CHINA, "%04d-%s",
                        cal.get(java.util.Calendar.YEAR), value);
                String[] patterns = {"yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss"};
                for (String p : patterns) {
                    try {
                        Date d = new SimpleDateFormat(p, Locale.CHINA).parse(parsed);
                        if (d != null) {
                            return saneIdle(nowWallMs - d.getTime());
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return -1L;
    }

    /** 闲置读数合理性校验：负值或超过 12 小时一律判为解析失败（防 1970 时间戳误触发） */
    private static long saneIdle(long idle) {
        if (idle < 0) return 0L;
        if (idle > 12L * 60 * 60 * 1000) return -1L;
        return idle;
    }

    /** 解析「3h 12m 5s 400ms」这类时长串为毫秒；无任何时长 token 返回 -1 */
    private static long parseDurationMs(String value) {
        if (TextUtils.isEmpty(value)) return -1L;
        Matcher m = P_DURATION_TOKEN.matcher(value);
        long total = 0L;
        boolean found = false;
        while (m.find()) {
            try {
                long n = Long.parseLong(m.group(1));
                String unit = m.group(2);
                if ("d".equals(unit)) total += n * 86400000L;
                else if ("h".equals(unit)) total += n * 3600000L;
                else if ("m".equals(unit)) total += n * 60000L;
                else if ("s".equals(unit)) total += n * 1000L;
                else total += n; // ms
                found = true;
            } catch (Throwable ignored) {
            }
        }
        return found ? total : -1L;
    }

    // ==================================================================
    // 配置读写
    // ==================================================================

    public static boolean isEnabled(Context ctx) {
        if (ctx == null) return false;
        try {
            return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_ENABLED, false);
        } catch (Throwable e) {
            return false;
        }
    }

    public static int getSeconds(Context ctx) {
        if (ctx == null) return DEFAULT_SECONDS;
        try {
            int v = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getInt(KEY_SECONDS, DEFAULT_SECONDS);
            return clampSeconds(v);
        } catch (Throwable e) {
            return DEFAULT_SECONDS;
        }
    }

    public static boolean isHomeOnly(Context ctx) {
        if (ctx == null) return true;
        try {
            return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_HOME_ONLY, true);
        } catch (Throwable e) {
            return true;
        }
    }

    public static int clampSeconds(int v) {
        if (v < MIN_SECONDS) return MIN_SECONDS;
        if (v > MAX_SECONDS) return MAX_SECONDS;
        return v;
    }

    /** 保存配置（同步 commit 落盘，防掉电丢失） */
    public static boolean saveConfig(Context ctx, Boolean enabled, Integer seconds, Boolean homeOnly) {
        if (ctx == null) return false;
        try {
            SharedPreferences.Editor ed = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
            if (enabled != null) ed.putBoolean(KEY_ENABLED, enabled);
            if (seconds != null) ed.putInt(KEY_SECONDS, clampSeconds(seconds));
            if (homeOnly != null) ed.putBoolean(KEY_HOME_ONLY, homeOnly);
            return ed.commit();
        } catch (Throwable e) {
            AppLogger.e(LOG_MODULE, "保存闲置屏保配置失败: " + e.getMessage());
            return false;
        }
    }

    // ==================================================================
    // 状态输出（供前端展示与排查）
    // ==================================================================

    public static String getChannelState() {
        return channelState;
    }

    public static long getLastIdleMs() {
        return lastIdleMs;
    }

    public static long getLastReadOkAt() {
        return lastReadOkAt;
    }

    /** 通道 A 最近一次原始输出（诊断展示用） */
    public static String getLastRawDump() {
        return lastRawDump;
    }

    /** 通道 A 最近一次解析到的原始值（诊断展示用） */
    public static String getLastRawValue() {
        return lastRawValue;
    }

    /** 通道 A 是否曾经读取成功过 */
    public static boolean isChannelAReady() {
        return channelAReady;
    }

    /** 通道 A 最近一次失败原因（诊断展示用） */
    public static String getLastFailReason() {
        return lastFailReason;
    }
}
