package app.onepve.geelyconsole.utils;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 主页面（桌面 Launcher）前台判定器
 *
 * 【为什么要它】
 * 「闲置自动屏保」只允许在主页面生效（导航/音乐/设置等界面闲置时不得打扰用户），
 * 因此需要可靠地回答一个问题：当前屏幕最前面的应用，是不是桌面？
 *
 * 【主页包名不写死】
 * 通过 PackageManager 动态查询所有声明了 {@code android.intent.category.HOME} 的应用，
 * 因此无论原厂 ecarx.launcher3 还是车主自行安装的第三方桌面（如嘟嘟桌面），都能正确识别。
 *
 * 【三级判定，逐级降级，互不依赖】
 *  1. UsageEvents 事件流（最准，需要「使用情况访问」授权一次）
 *  2. UsageStats 最近使用时间（兜底，同上授权）
 *  3. dumpsys activity 解析（无需授权，但需本地 ADB / su，作为最后防线）
 */
public final class ForegroundAppDetector {

    private static final String TAG = "GeelyToolbox_FgDetector";

    /** 车机原厂屏幕保护宿主的包名（用于识别屏保是否已在前台） */
    public static final String PKG_SCREENSAVER = "com.ecarx.screensaver";

    /** 主页包名缓存（声明 CATEGORY_HOME 的应用集合） */
    private static volatile Set<String> homePackagesCache = null;
    /** 主页包名缓存时间戳，避免频繁查询 PackageManager */
    private static volatile long homePackagesCacheAt = 0L;
    private static final long HOME_CACHE_TTL_MS = 5 * 60 * 1000L;

    /** 前台包名解析结果缓存，避免 3 秒轮询时重复做重操作 */
    private static volatile String lastForegroundPkg = null;
    private static volatile long lastForegroundAt = 0L;
    private static final long FG_CACHE_TTL_MS = 1200L;

    private static final Pattern RESUMED_PATTERN =
            Pattern.compile("(?:mResumedActivity|topResumedActivity|ResumedActivity)[^\\n]*?([a-zA-Z0-9_.]+)/(?:[a-zA-Z0-9_.$]+)");

    private ForegroundAppDetector() {
    }

    // ------------------------------------------------------------------
    // 一、主页包名集合（动态查询，绝不写死）
    // ------------------------------------------------------------------

    /**
     * 返回当前系统里所有声明了 CATEGORY_HOME 的桌面应用包名集合。
     * 结果带 5 分钟缓存；查询失败时退化为原厂 ecarx.launcher3，保证功能不会失效。
     */
    public static Set<String> getHomePackages(Context ctx) {
        if (ctx == null) return Collections.singleton("ecarx.launcher3");

        Set<String> cached = homePackagesCache;
        long now = System.currentTimeMillis();
        if (cached != null && !cached.isEmpty() && (now - homePackagesCacheAt) < HOME_CACHE_TTL_MS) {
            return cached;
        }

        Set<String> result = new HashSet<>();
        try {
            PackageManager pm = ctx.getPackageManager();
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> list = pm.queryIntentActivities(homeIntent, 0);
            if (list != null) {
                for (ResolveInfo ri : list) {
                    if (ri != null && ri.activityInfo != null && ri.activityInfo.packageName != null) {
                        // 系统级「选择桌面」选择器本身不是桌面，排除掉
                        if ("android".equals(ri.activityInfo.packageName)) continue;
                        result.add(ri.activityInfo.packageName);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "queryIntentActivities(HOME) failed: " + e.getMessage());
        }

        if (result.isEmpty()) {
            result.add("ecarx.launcher3");
        }

        homePackagesCache = result;
        homePackagesCacheAt = now;
        Log.i(TAG, "Home packages resolved: " + result);
        return result;
    }

    /** 指定包名是否为当前系统的桌面应用 */
    public static boolean isHomePackage(Context ctx, String pkg) {
        if (TextUtils.isEmpty(pkg)) return false;
        return getHomePackages(ctx).contains(pkg);
    }

    // ------------------------------------------------------------------
    // 二、使用情况访问授权状态
    // ------------------------------------------------------------------

    /** 是否已授予「使用情况访问」权限（AppOps 方式判定，兼容 Android 9） */
    public static boolean isUsageAccessGranted(Context ctx) {
        if (ctx == null) return false;
        try {
            AppOpsManager aom = (AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            if (aom == null) return false;
            int mode;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                mode = aom.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(), ctx.getPackageName());
            } else {
                mode = aom.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        Process.myUid(), ctx.getPackageName());
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable e) {
            Log.w(TAG, "isUsageAccessGranted failed: " + e.getMessage());
            return false;
        }
    }

    /** 跳转系统「使用情况访问」授权页，供车主手动勾选本应用 */
    public static void openUsageAccessSettings(Context ctx) {
        if (ctx == null) return;
        try {
            Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Throwable e) {
            try {
                Intent i2 = new Intent(Settings.ACTION_SETTINGS);
                i2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i2);
            } catch (Throwable ignored) {
            }
        }
    }

    // ------------------------------------------------------------------
    // 三、前台包名获取（三级降级）
    // ------------------------------------------------------------------

    /** 获取当前前台应用包名；优先使用情况统计，失败则退到 dumpsys。可能返回 null。 */
    public static String getForegroundPackage(Context ctx) {
        if (ctx == null) return null;

        long now = System.currentTimeMillis();
        String cached = lastForegroundPkg;
        if (cached != null && (now - lastForegroundAt) < FG_CACHE_TTL_MS) {
            return cached;
        }

        String pkg = null;

        // 1. 使用情况事件流（最准确，能区分「刚切到哪个应用」）
        if (isUsageAccessGranted(ctx)) {
            pkg = queryByEvents(ctx);
            // 2. 事件流为空（长时间停在同一界面不会再产生事件）时退到使用统计
            if (TextUtils.isEmpty(pkg)) {
                pkg = queryByStats(ctx);
            }
        }

        // 3. 最后防线：dumpsys activity（需本地 ADB / su）
        if (TextUtils.isEmpty(pkg)) {
            pkg = queryByDumpsys(ctx);
        }

        if (!TextUtils.isEmpty(pkg)) {
            lastForegroundPkg = pkg;
            lastForegroundAt = now;
        }
        return pkg;
    }

    private static String queryByEvents(Context ctx) {
        UsageEvents events = null;
        try {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;
            long now = System.currentTimeMillis();
            events = usm.queryEvents(now - 120000L, now);
            if (events == null) return null;

            String best = null;
            long bestTs = -1L;
            UsageEvents.Event ev = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(ev);
                // Event.MOVE_TO_FOREGROUND == ACTIVITY_RESUMED == 1（API 28/29 同值）
                if (ev.getEventType() == 1 && ev.getTimeStamp() >= bestTs) {
                    bestTs = ev.getTimeStamp();
                    best = ev.getPackageName();
                }
            }
            return (bestTs > 0) ? best : null;
        } catch (Throwable e) {
            Log.w(TAG, "queryByEvents failed: " + e.getMessage());
            return null;
        }
    }

    private static String queryByStats(Context ctx) {
        try {
            UsageStatsManager usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;
            long now = System.currentTimeMillis();
            List<UsageStats> stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, now - 12 * 3600 * 1000L, now);
            if (stats == null || stats.isEmpty()) return null;

            UsageStats best = null;
            for (UsageStats s : stats) {
                if (s == null) continue;
                if (best == null || s.getLastTimeUsed() > best.getLastTimeUsed()) {
                    best = s;
                }
            }
            return (best != null) ? best.getPackageName() : null;
        } catch (Throwable e) {
            Log.w(TAG, "queryByStats failed: " + e.getMessage());
            return null;
        }
    }

    private static String queryByDumpsys(Context ctx) {
        try {
            String out = SystemUtils.executePrivileged(ctx,
                    "dumpsys activity activities | grep -E \"mResumedActivity|topResumedActivity\"");
            return parseDumpsysActivity(out);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 从 dumpsys activity 输出解析前台包名（独立出来便于单元测试） */
    public static String parseDumpsysActivity(String out) {
        if (TextUtils.isEmpty(out)) return null;
        try {
            Matcher m = RESUMED_PATTERN.matcher(out);
            if (m.find()) {
                return m.group(1);
            }
            // 退化解析：直接取形如 com.xxx.yyy/ 之前的包名
            Matcher m2 = Pattern.compile("([a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]+){1,})/").matcher(out);
            if (m2.find()) {
                return m2.group(1);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 四、语义化便捷判定
    // ------------------------------------------------------------------

    /** 当前前台是否为主页面（任意一个桌面应用） */
    public static boolean isHomeForeground(Context ctx) {
        String pkg = getForegroundPackage(ctx);
        if (TextUtils.isEmpty(pkg)) return false;
        return isHomePackage(ctx, pkg);
    }

    /** 车机原厂屏保是否已在前台（说明屏保已亮起，用于防重复触发） */
    public static boolean isScreenSaverForeground(Context ctx) {
        String pkg = getForegroundPackage(ctx);
        return PKG_SCREENSAVER.equals(pkg);
    }

    /** 供调试面板展示：当前前台应用 + 判定结论 */
    public static String describe(Context ctx) {
        try {
            String pkg = getForegroundPackage(ctx);
            List<String> homes = new ArrayList<>(getHomePackages(ctx));
            Collections.sort(homes);
            boolean granted = isUsageAccessGranted(ctx);
            return "前台应用=" + (pkg == null ? "(读取失败)" : pkg)
                    + " | 是否主页面=" + (pkg != null && homes.contains(pkg))
                    + " | 使用情况访问=" + (granted ? "已授权" : "未授权")
                    + " | 桌面列表=" + homes;
        } catch (Throwable e) {
            return "描述失败: " + e.getMessage();
        }
    }
}
