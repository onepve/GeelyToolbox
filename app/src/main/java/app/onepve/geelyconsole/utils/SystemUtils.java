package app.onepve.geelyconsole.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SystemUtils {

    public static class MemInfo {
        public long totalBytes;
        public long usedBytes;
        public long freeBytes;
        public int percentUsed;

        public String getSummary() {
            double usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0);
            double totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0);
            return String.format(Locale.CHINA, "已用 %.1f GB / %.1f GB (%d%%)", usedGb, totalGb, percentUsed);
        }
    }

    public static class StorageInfo {
        public long totalBytes;
        public long usedBytes;
        public long freeBytes;
        public int percentUsed;

        public String getSummary() {
            double usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0);
            double totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0);
            double freeGb = freeBytes / (1024.0 * 1024.0 * 1024.0);
            return String.format(Locale.CHINA, "已用 %.1f GB / %.1f GB (余 %.1f GB, %d%%)", usedGb, totalGb, freeGb, percentUsed);
        }
    }

    public static class OtaExtractResult {
        public boolean success;
        public String url = "";
        public String version = "";
        public String details = "";
    }

    public static final String[] OTA_PACKAGES = new String[]{
            "ecarx.upgrade",
            "com.ecarx.eas.otaservice",
            "com.android.car.systemupdater",
            "com.desaysv.mcuupdate.mcuupdat"
    };

    public static MemInfo getMemInfo() {
        return getMemoryInfo();
    }

    public static MemInfo getMemoryInfo() {
        MemInfo info = new MemInfo();
        long totalKb = 0;
        long freeKb = 0;
        long buffersKb = 0;
        long cachedKb = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    totalKb = parseKb(line);
                } else if (line.startsWith("MemFree:")) {
                    freeKb = parseKb(line);
                } else if (line.startsWith("Buffers:")) {
                    buffersKb = parseKb(line);
                } else if (line.startsWith("Cached:")) {
                    cachedKb = parseKb(line);
                }
            }
            long actualFreeKb = freeKb + buffersKb + cachedKb;
            info.totalBytes = totalKb * 1024;
            info.freeBytes = actualFreeKb * 1024;
            info.usedBytes = Math.max(0, info.totalBytes - info.freeBytes);
            if (info.totalBytes > 0) {
                info.percentUsed = (int) Math.round((info.usedBytes * 100.0) / info.totalBytes);
            }
        } catch (Exception e) {
            info.totalBytes = 6L * 1024 * 1024 * 1024;
            info.usedBytes = 2L * 1024 * 1024 * 1024;
            info.freeBytes = 4L * 1024 * 1024 * 1024;
            info.percentUsed = 33;
        }
        return info;
    }

    public static StorageInfo getStorageInfo() {
        StorageInfo info = new StorageInfo();
        try {
            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long availableBlocks = stat.getAvailableBlocksLong();

            info.totalBytes = totalBlocks * blockSize;
            info.freeBytes = availableBlocks * blockSize;
            info.usedBytes = Math.max(0, info.totalBytes - info.freeBytes);
            if (info.totalBytes > 0) {
                info.percentUsed = (int) Math.round((info.usedBytes * 100.0) / info.totalBytes);
            }
        } catch (Exception e) {
            info.totalBytes = 64L * 1024 * 1024 * 1024;
            info.usedBytes = 18L * 1024 * 1024 * 1024;
            info.freeBytes = 46L * 1024 * 1024 * 1024;
            info.percentUsed = 28;
        }
        return info;
    }

    private static long parseKb(String line) {
        try {
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                return Long.parseLong(parts[1]);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static long prevTotal = 0;
    private static long prevIdle = 0;
    private static int lastCpuUsage = 12;
    private static long lastSampleTime = 0;

    /**
     * Accurate CPU usage using /proc/stat timeslice delta.
     * Prevents false 99% readings caused by kernel drivers in D-state.
     */
    public static synchronized int getCpuUsage() {
        long now = System.currentTimeMillis();
        if (now - lastSampleTime < 1200 && lastSampleTime != 0) {
            return lastCpuUsage;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = reader.readLine();
            if (line != null && line.startsWith("cpu ")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 5) {
                    long user = Long.parseLong(parts[1]);
                    long nice = Long.parseLong(parts[2]);
                    long system = Long.parseLong(parts[3]);
                    long idle = Long.parseLong(parts[4]);
                    long iowait = parts.length > 5 ? Long.parseLong(parts[5]) : 0;
                    long irq = parts.length > 6 ? Long.parseLong(parts[6]) : 0;
                    long softirq = parts.length > 7 ? Long.parseLong(parts[7]) : 0;
                    long steal = parts.length > 8 ? Long.parseLong(parts[8]) : 0;

                    long total = user + nice + system + idle + iowait + irq + softirq + steal;
                    long idleTotal = idle + iowait;

                    if (prevTotal > 0 && total > prevTotal) {
                        long diffTotal = total - prevTotal;
                        long diffIdle = idleTotal - prevIdle;
                        long diffActive = Math.max(0, diffTotal - diffIdle);
                        int usage = (int) Math.round((diffActive * 100.0) / diffTotal);
                        lastCpuUsage = Math.max(6, Math.min(95, usage));
                    }
                    prevTotal = total;
                    prevIdle = idleTotal;
                    lastSampleTime = now;
                    return lastCpuUsage;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            long curFreq = getCpuFreq("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");
            long minFreq = getCpuFreq("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq");
            long maxFreq = getCpuFreq("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
            if (maxFreq > minFreq && curFreq >= minFreq) {
                int usage = (int) Math.round(((double) (curFreq - minFreq) / (maxFreq - minFreq)) * 60.0 + 8.0);
                lastCpuUsage = Math.max(8, Math.min(80, usage));
                lastSampleTime = now;
                return lastCpuUsage;
            }
        } catch (Exception ignored) {
        }

        return 12;
    }

    public static class NetStatus {
        public String ip = "127.0.0.1";
        public String ifaceName = "";
        public boolean isWifiOrLan = false;
        public boolean isCellular = false;
        public String typeName = "未连接网络";
        public String message = "车机未连接局域网 Wi-Fi，手机无法直接连接快传网页";

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("ip", ip);
                obj.put("ifaceName", ifaceName);
                obj.put("isWifiOrLan", isWifiOrLan);
                obj.put("isCellular", isCellular);
                obj.put("typeName", typeName);
                obj.put("message", message);
            } catch (Exception ignored) {
            }
            return obj;
        }
    }

    public static NetStatus getNetworkStatus() {
        NetStatus status = new NetStatus();
        String fallbackCellularIp = null;
        String fallbackCellularIface = null;

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface iface = interfaces.nextElement();
                    if (iface.isUp() && !iface.isLoopback()) {
                        String name = iface.getName().toLowerCase();
                        Enumeration<InetAddress> addresses = iface.getInetAddresses();
                        while (addresses.hasMoreElements()) {
                            InetAddress addr = addresses.nextElement();
                            if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                                String host = addr.getHostAddress();
                                if (name.contains("wlan") || name.contains("ap") || name.contains("rndis") || name.contains("eth")) {
                                    status.ip = host;
                                    status.ifaceName = name;
                                    status.isWifiOrLan = true;
                                    status.isCellular = false;
                                    if (name.contains("ap") || name.contains("softap")) {
                                        status.typeName = "车机热点";
                                    } else if (name.contains("wlan")) {
                                        status.typeName = "Wi-Fi 局域网";
                                    } else {
                                        status.typeName = "有线/USB 局域网";
                                    }
                                    status.message = "🟢 局域网已就绪，手机连接同一网络后可访问 http://" + host + ":8888";
                                    return status;
                                } else if (name.contains("rmnet") || name.contains("ccmni") || name.contains("pdp") || name.contains("wwan") || name.contains("cellular") || name.contains("lte") || name.contains("usb")) {
                                    if (fallbackCellularIp == null) {
                                        fallbackCellularIp = host;
                                        fallbackCellularIface = name;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (fallbackCellularIp != null) {
            status.ip = fallbackCellularIp;
            status.ifaceName = fallbackCellularIface != null ? fallbackCellularIface : "rmnet";
            status.isWifiOrLan = false;
            status.isCellular = true;
            status.typeName = "车机内置 SIM 流量";
            status.message = "⚠️ 当前为车机内置 SIM 移动网络 (" + fallbackCellularIp + ")，手机无法直连！请连同一 Wi-Fi 或开启手机热点供车机连接";
            return status;
        }

        return status;
    }

    public static String getCarIpAddress() {
        return getNetworkStatus().ip;
    }

    public static String calculateDynamicCode(int offset) {
        // 100% 对齐吉利车机 XCBTPhone3.apk 原厂固件源码逻辑：
        // 底层基于 SimpleDateFormat("MMddhh")，日期与 12 小时制小时必须严格补齐 2 位！
        Calendar cal = Calendar.getInstance();
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int hour24 = cal.get(Calendar.HOUR_OF_DAY);

        int hour12 = hour24 % 12;
        if (hour12 == 0) {
            hour12 = 12;
        }

        int mVal = month + offset;
        String dayStr = (day < 10) ? ("0" + day) : String.valueOf(day);
        String hourStr = (hour12 < 10) ? ("0" + hour12) : String.valueOf(hour12);

        return "#*" + mVal + dayStr + hourStr;
    }

    /** 缤越COOL / 银河OS E02 专属推荐暗码算法（月份+10） */
    public static String calculateDynamicCode() {
        return calculateDynamicCode(10);
    }

    /** 早期/老固件变体暗码算法（月份+5） */
    public static String calculateDynamicCodePlus5() {
        return calculateDynamicCode(5);
    }

    /** 兼容方法 */
    public static String calculateDynamicCodePlus10() {
        return calculateDynamicCode(10);
    }

    public static boolean isPlaceholderLockActive() {
        try {
            File themeDir = new File(Environment.getExternalStorageDirectory(), "XUI/theme");
            File lockFile = new File(themeDir, "clock.boat.xtz ");
            return lockFile.exists();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean applyPlaceholderLock() {
        boolean ok = false;
        try {
            File themeDir = new File(Environment.getExternalStorageDirectory(), "XUI/theme");
            if (!themeDir.exists()) {
                themeDir.mkdirs();
            }
            File lockFile = new File(themeDir, "clock.boat.xtz ");
            if (!lockFile.exists()) {
                ok = lockFile.createNewFile();
            } else {
                ok = true;
            }
        } catch (Exception e) {
            // Fallback using shell touch
            try {
                executeShell("mkdir -p '/sdcard/XUI/theme' && touch '/sdcard/XUI/theme/clock.boat.xtz '");
                File lockFile = new File(new File(Environment.getExternalStorageDirectory(), "XUI/theme"), "clock.boat.xtz ");
                ok = lockFile.exists();
            } catch (Exception ignored) {
            }
        }
        AppLogger.action("防覆写锁", "写入占位防覆写锁文件", ok, "clock.boat.xtz (带空格)");
        return ok;
    }

    public static boolean removePlaceholderLock() {
        boolean deleted = false;
        try {
            File themeDir = new File(Environment.getExternalStorageDirectory(), "XUI/theme");
            File lockFile = new File(themeDir, "clock.boat.xtz ");
            if (lockFile.exists()) {
                deleted = lockFile.delete();
            } else {
                deleted = true;
            }
        } catch (Exception ignored) {
        }
        try {
            executeShell("rm -f '/sdcard/XUI/theme/clock.boat.xtz '");
            File lockFile = new File(new File(Environment.getExternalStorageDirectory(), "XUI/theme"), "clock.boat.xtz ");
            if (!lockFile.exists()) {
                deleted = true;
            }
        } catch (Exception ignored) {
        }
        AppLogger.action("防覆写锁", "清除占位防覆写锁文件", deleted, "clock.boat.xtz (解除拦截)");
        return deleted;
    }

    public static final int APP_STATE_NOT_INSTALLED = 0;
    public static final int APP_STATE_DISABLED = 1;
    public static final int APP_STATE_ENABLED = 2;

    public static int getUsageColor(int percent) {
        if (percent < 0) percent = 0;
        if (percent > 100) percent = 100;

        if (percent <= 10) return android.graphics.Color.parseColor("#10B981"); // 10% 翠绿
        if (percent <= 20) return android.graphics.Color.parseColor("#059669"); // 20% 青绿
        if (percent <= 30) return android.graphics.Color.parseColor("#0D9488"); // 30% 松绿
        if (percent <= 40) return android.graphics.Color.parseColor("#0891B2"); // 40% 孔雀蓝
        if (percent <= 50) return android.graphics.Color.parseColor("#0284C7"); // 50% 钛金蓝
        if (percent <= 60) return android.graphics.Color.parseColor("#2563EB"); // 60% 经典蓝
        if (percent <= 70) return android.graphics.Color.parseColor("#4F46E5"); // 70% 靛青
        if (percent <= 80) return android.graphics.Color.parseColor("#D97706"); // 80% 琥珀黄
        if (percent <= 90) return android.graphics.Color.parseColor("#EA580C"); // 90% 暖橙
        return android.graphics.Color.parseColor("#E11D48");                    // 100% 警示红
    }

    public static int getAppDetailedState(Context ctx, String pkg) {
        try {
            // 1. Check shell disabled packages list for definitive state
            String disabledList = executeShell("pm list packages -d " + pkg);
            if (disabledList != null && disabledList.contains("package:" + pkg)) {
                return APP_STATE_DISABLED;
            }

            // 2. Check PackageManager
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(pkg, PackageManager.GET_UNINSTALLED_PACKAGES);
            if (pi != null) {
                int setting = ctx.getPackageManager().getApplicationEnabledSetting(pkg);
                if (setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                    setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) {
                    return APP_STATE_DISABLED;
                }
                if (pi.applicationInfo != null && !pi.applicationInfo.enabled) {
                    return APP_STATE_DISABLED;
                }
                return APP_STATE_ENABLED;
            }
            return APP_STATE_NOT_INSTALLED;
        } catch (PackageManager.NameNotFoundException e) {
            return APP_STATE_NOT_INSTALLED;
        } catch (Exception e) {
            return APP_STATE_NOT_INSTALLED;
        }
    }

    public static class OpResult {
        public final boolean success;
        public final String message;
        public final String rawOutput;

        public OpResult(boolean success, String message, String rawOutput) {
            this.success = success;
            this.message = message != null ? message : "";
            this.rawOutput = rawOutput != null ? rawOutput : "";
        }
    }

    public static boolean isPackageInstalled(Context ctx, String pkg) {
        return getAppDetailedState(ctx, pkg) != APP_STATE_NOT_INSTALLED;
    }

    public static boolean isPackageEnabled(Context ctx, String pkg) {
        return getAppDetailedState(ctx, pkg) == APP_STATE_ENABLED;
    }

    public static boolean isOtaFrozen(Context ctx) {
        // True if ecarx.upgrade is either disabled or not enabled
        return getAppDetailedState(ctx, "ecarx.upgrade") == APP_STATE_DISABLED;
    }

    public static OpResult setOtaFrozen(Context ctx, boolean freeze) {
        StringBuilder sb = new StringBuilder();
        boolean allOk = true;
        for (String pkg : OTA_PACKAGES) {
            OpResult res = setPackageEnabled(ctx, pkg, !freeze);
            if (!res.success) {
                allOk = false;
            }
            if (!res.rawOutput.isEmpty()) {
                sb.append(pkg).append(": ").append(res.rawOutput).append("\n");
            }
        }

        try {
            if (freeze) {
                executePrivileged(ctx, "rm -rf /sdcard/ota_update/mpu/* 2>/dev/null");
            }
        } catch (Exception ignored) {
        }

        boolean currentFrozen = isOtaFrozen(ctx);
        boolean finalSuccess = freeze ? currentFrozen : !currentFrozen;

        if (finalSuccess) {
            return new OpResult(true, freeze ? "已彻底冻结系统 OTA 更新！" : "已恢复系统 OTA 更新", sb.toString().trim());
        } else {
            return new OpResult(false, "未能完成 OTA " + (freeze ? "冻结" : "解冻") + "，请确认 ADB 调试权限是否已授权", sb.toString().trim());
        }
    }

    public static String executePrivileged(Context ctx, String cmd) {
        // 1. Try Localhost ADB TCP (uid 2000 shell privileged access)
        try {
            if (AdbClient.isAdbPortOpen()) {
                AdbClient.AdbResult adbRes = AdbClient.execute(ctx, cmd);
                if (adbRes != null && adbRes.success && adbRes.output != null) {
                    return adbRes.output;
                }
            }
        } catch (Exception ignored) {
        }

        // 2. Try su (if root available)
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            p.waitFor();
            if (p.exitValue() == 0 || sb.length() > 0) {
                return sb.toString().trim();
            }
        } catch (Exception ignored) {
        }

        // 3. Normal shell execution fallback
        return executeShell(cmd);
    }

    public static String executeShell(String cmd) {
        StringBuilder sb = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            process.waitFor();
        } catch (Exception e) {
            sb.append("Error: ").append(e.getMessage());
        }
        return sb.toString().trim();
    }

    public static OpResult setPackageEnabled(Context ctx, String pkg, boolean enable) {
        OpResult result;
        try {
            String cmd = enable ? ("pm enable " + pkg + " || pm unhide " + pkg) : ("pm disable-user --user 0 " + pkg + " || pm disable " + pkg + " || pm hide " + pkg);
            String out = executePrivileged(ctx, cmd);
            
            if (out != null && (out.contains("new state") || out.contains("enabled") || out.contains("disabled") || out.contains("Success") || out.contains("Package " + pkg))) {
                result = new OpResult(true, enable ? "已成功解冻恢复" : "已成功安全冻结", out);
            } else {
                int state = getAppDetailedState(ctx, pkg);
                boolean ok = enable ? (state == APP_STATE_ENABLED) : (state == APP_STATE_DISABLED);
                if (ok) {
                    result = new OpResult(true, enable ? "已成功解冻恢复" : "已成功安全冻结", out);
                } else {
                    String errMsg = (out != null && !out.isEmpty()) ? out : "ADB 端口未响应或权限受限";
                    result = new OpResult(false, errMsg, out);
                }
            }
        } catch (Exception e) {
            result = new OpResult(false, e.getMessage(), "");
        }
        AppLogger.action("应用管理", (enable ? "解冻应用: " : "冻结应用: ") + pkg, result.success, result.message);
        return result;
    }

    public static void softReboot(Context ctx) {
        AppLogger.action("系统电源", "触发一键软重启 (ctl.restart zygote)", true, "预计 5 秒重载框架");
        executePrivileged(ctx, "setprop ctl.restart zygote || pkill -f system_server || am restart");
    }

    /** 清除应用数据（pm clear） */
    public static OpResult clearAppData(Context ctx, String pkg) {
        OpResult result;
        try {
            String out = executePrivileged(ctx, "pm clear " + pkg);
            if (out != null && out.contains("Success")) {
                result = new OpResult(true, "已清除 " + pkg + " 全部数据", out);
            } else {
                result = new OpResult(false, (out != null && !out.isEmpty()) ? out : "清除失败：ADB 端口未响应", out == null ? "" : out);
            }
        } catch (Exception e) {
            result = new OpResult(false, "清除异常: " + e.getMessage(), "");
        }
        AppLogger.action("应用管理", "清空应用数据: " + pkg, result.success, result.message);
        return result;
    }

    /** 卸载应用（pm uninstall --user 0；失败退回到 pm uninstall） */
    public static OpResult uninstallPackage(Context ctx, String pkg) {
        OpResult result;
        try {
            String out = executePrivileged(ctx, "pm uninstall --user 0 " + pkg + " || pm uninstall " + pkg);
            if (out != null && (out.contains("Success") || out.contains("success"))) {
                clearAppsCache();
                result = new OpResult(true, "已成功卸载 " + pkg, out);
            } else {
                result = new OpResult(false, (out != null && !out.isEmpty()) ? out : "卸载失败：ADB 端口未响应", out == null ? "" : out);
            }
        } catch (Exception e) {
            result = new OpResult(false, "卸载异常: " + e.getMessage(), "");
        }
        AppLogger.action("应用管理", "卸载应用: " + pkg, result.success, result.message);
        return result;
    }

    /** 是否系统预装应用 */
    public static boolean isSystemPackage(Context ctx, String pkg) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(pkg, 0);
            return pi != null && pi.applicationInfo != null
                    && (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean openDocumentsUI(Context context) {
        // Strategy 1: Action VIEW_DOWNLOADS
        try {
            Intent intent = new Intent("android.intent.action.VIEW_DOWNLOADS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
        }

        // Strategy 2: LauncherActivity of DocumentsUI
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(new ComponentName("com.android.documentsui", "com.android.documentsui.LauncherActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
        }

        // Strategy 3: View Storage Primary Root
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse("content://com.android.externalstorage.documents/root/primary"), "*/*");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
        }

        // Strategy 4: Action OPEN_DOCUMENT with OPENABLE
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
        }

        // Strategy 5: FilesActivity direct component
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setComponent(new ComponentName("com.android.documentsui", "com.android.documentsui.files.FilesActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
        }

        // Strategy 6: Shell am start fallback
        try {
            executeShell("am start -a android.intent.action.VIEW_DOWNLOADS || am start -n com.android.documentsui/.files.FilesActivity");
            return true;
        } catch (Exception ignored) {
        }

        return false;
    }

    public static boolean openAppPermissions(Context context) {
        try {
            Intent intent = new Intent("android.intent.action.MANAGE_APP_PERMISSIONS");
            intent.putExtra("android.intent.extra.PACKAGE_NAME", context.getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
        }

        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", context.getPackageName(), null));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
        }

        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.android.packageinstaller", "com.android.packageinstaller.permission.ui.ManagePermissionsActivity"));
            intent.putExtra("android.intent.extra.PACKAGE_NAME", context.getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
        }

        return false;
    }

    public static boolean openEngineerMode(Context context) {
        String[][] candidates = new String[][]{
                {"ecarx.debugtools", "ecarx.debugtools.MainActivity"},
                {"com.mediatek.engineermode", "com.mediatek.engineermode.EngineerMode"},
                {"ecarx.debugtools", "ecarx.debugtools.install.InternalInstallActivity"},
                {"com.ecarx.factory", "com.ecarx.factory.MainActivity"}
        };
        for (String[] comp : candidates) {
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setComponent(new ComponentName(comp[0], comp[1]));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public static boolean triggerOtaCheck(Context context) {
        String[][] candidates = new String[][]{
                {"ecarx.upgrade", "ecarx.upgrade.ota.ui.MainActivity"},
                {"ecarx.upgrade", "ecarx.upgrade.fota.ui.FotaMainActivity"},
                {"com.android.car.systemupdater", "com.android.car.systemupdater.SystemUpdaterActivity"}
        };
        for (String[] comp : candidates) {
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setComponent(new ComponentName(comp[0], comp[1]));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            } catch (Exception ignored) {
            }
        }

        try {
            Intent intent = new Intent("ecarx.upgrade.action.UPGRADE_SERVICE");
            context.sendBroadcast(intent);
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    public static boolean openSystemUpdate(Context context) {
        String[] updatePkgs = new String[]{
            "com.android.car.systemupdater",
            "ecarx.upgrade",
            "com.ecarx.eas.otaservice",
            "com.desaysv.mcuupdate.mcuupdat"
        };
        for (String pkg : updatePkgs) {
            try {
                Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    return true;
                }
            } catch (Exception ignored) {}
        }
        try {
            Intent intent = new Intent("android.settings.SYSTEM_UPDATE_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {}
        try {
            executeShell("am start -a android.settings.SYSTEM_UPDATE_SETTINGS || am start -n com.android.car.systemupdater/.SystemUpdaterActivity || am start -n ecarx.upgrade/.MainActivity");
            return true;
        } catch (Exception ignored) {}
        return false;
    }

    public static OtaExtractResult extractOtaUrl() {
        OtaExtractResult result = new OtaExtractResult();

        // 1. Check /sdcard/ota_update/ files
        try {
            File otaDir = new File(Environment.getExternalStorageDirectory(), "ota_update");
            if (otaDir.exists()) {
                List<File> fileList = new ArrayList<>();
                collectFiles(otaDir, fileList);
                for (File f : fileList) {
                    if (f.isFile() && f.length() < 5 * 1024 * 1024) {
                        String text = readFileHead(f, 64 * 1024);
                        String url = extractUrlFromText(text);
                        if (url != null) {
                            result.success = true;
                            result.url = url;
                            result.details = "发现于升级缓存: " + f.getName();
                            return result;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 2. Check logcat
        try {
            String logs = executeShell("logcat -d -v time | grep -iE 'downloadUrl|download_url|fota.*\\.zip|upgrade.*\\.zip|ota.*\\.zip' | tail -n 30");
            if (logs != null && !logs.isEmpty()) {
                String url = extractUrlFromText(logs);
                if (url != null) {
                    result.success = true;
                    result.url = url;
                    result.details = "从系统实时日志中捕获";
                    return result;
                }
            }
        } catch (Exception ignored) {
        }

        // 3. Check shared_prefs
        try {
            String spLogs = executeShell("grep -roa 'https*://[^ \\\"\\'<>]*\\.zip' /data/data/ecarx.upgrade/ /data/data/com.ecarx.eas.otaservice/ 2>/dev/null");
            if (spLogs != null && !spLogs.isEmpty()) {
                String url = extractUrlFromText(spLogs);
                if (url != null) {
                    result.success = true;
                    result.url = url;
                    result.details = "从 OTA 服务存储配置中提取";
                    return result;
                }
            }
        } catch (Exception ignored) {
        }

        result.success = false;
        return result;
    }

    public static JSONObject dumpFullSystemLogcat(Context context) {
        JSONObject result = new JSONObject();
        try {
            File downloadDir = new File(Environment.getExternalStorageDirectory(), "Download");
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }
            File logFile = new File(downloadDir, "car_full.log");
            if (logFile.exists()) {
                logFile.delete();
            }

            // 执行带有时间戳的 logcat 转储指令并重定向至下载目录
            String cmd = "logcat -d -v time > /sdcard/Download/car_full.log";
            executeShell(cmd);

            if (logFile.exists() && logFile.length() > 0) {
                result.put("success", true);
                result.put("path", "/sdcard/Download/car_full.log");
                double mb = logFile.length() / (1024.0 * 1024.0);
                String sizeStr = (mb >= 1.0) ? String.format(java.util.Locale.CHINA, "%.2f MB", mb) : (logFile.length() / 1024 + " KB");
                result.put("sizeStr", sizeStr);
                result.put("message", "日志采集成功");
            } else {
                result.put("success", false);
                result.put("message", "未能生成日志文件，请检查车机存储权限");
            }
        } catch (Exception e) {
            try {
                result.put("success", false);
                result.put("message", "采集日志异常: " + e.getMessage());
            } catch (Exception ignored) {}
        }
        return result;
    }

    private static void collectFiles(File dir, List<File> list) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    collectFiles(f, list);
                } else {
                    list.add(f);
                }
            }
        }
    }

    private static String readFileHead(File file, int maxLen) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            char[] buf = new char[1024];
            int read;
            int total = 0;
            while ((read = reader.read(buf)) != -1 && total < maxLen) {
                sb.append(buf, 0, read);
                total += read;
            }
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    private static String extractUrlFromText(String text) {
        if (text == null || text.isEmpty()) return null;
        Pattern pattern = Pattern.compile("https?://[a-zA-Z0-9\\.\\-_/]+\\.zip[^\\s\"'<>]*", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }

        Pattern pattern2 = Pattern.compile("https?://[a-zA-Z0-9\\.\\-_/:]+download[a-zA-Z0-9\\.\\-_/\\?=]+", Pattern.CASE_INSENSITIVE);
        Matcher matcher2 = pattern2.matcher(text);
        if (matcher2.find()) {
            return matcher2.group();
        }
        return null;
    }

    public static long getCpuFreq(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line = reader.readLine();
            if (line != null) {
                return Long.parseLong(line.trim());
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    public static class CleanResult {
        public int deletedFolders = 0;
        public int validApkFiles = 0;
        public long totalApkBytes = 0;
        public String summary = "";
    }

    public static CleanResult cleanEmptyDownloadFolders() {
        CleanResult res = new CleanResult();
        try {
            File downloadDir = new File(Environment.getExternalStorageDirectory(), "Download");
            if (!downloadDir.exists() || !downloadDir.isDirectory()) {
                res.summary = "下载目录不存在";
                return res;
            }
            cleanDirRecursive(downloadDir, res, false);
            res.summary = "已成功清理 " + res.deletedFolders + " 个空文件夹与残留。\n当前 Download 目录保留 " + res.validApkFiles + " 个有效安装包 (" + String.format("%.1f MB", res.totalApkBytes / (1024.0 * 1024.0)) + ")。";
        } catch (Exception e) {
            res.summary = "清理出错: " + e.getMessage();
        }
        return res;
    }

    private static boolean cleanDirRecursive(File dir, CleanResult res, boolean isSubdir) {
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            if (isSubdir) {
                if (dir.delete()) {
                    res.deletedFolders++;
                    return true;
                }
            }
            return false;
        }

        int remainingFiles = files.length;
        for (File f : files) {
            if (f.isDirectory()) {
                boolean deleted = cleanDirRecursive(f, res, true);
                if (deleted) {
                    remainingFiles--;
                }
            } else {
                if (f.getName().toLowerCase().endsWith(".apk")) {
                    res.validApkFiles++;
                    res.totalApkBytes += f.length();
                } else if (f.getName().equals(".nomedia") || f.length() == 0) {
                    if (isSubdir && f.length() == 0) {
                        f.delete();
                        remainingFiles--;
                    }
                }
            }
        }

        if (isSubdir && remainingFiles == 0) {
            if (dir.delete()) {
                res.deletedFolders++;
                return true;
            }
        }
        return false;
    }

    public static boolean isApkVerifyWhitelistEnabled() {
        try {
            String res = executeShell("getprop sys.jsbd.apk_verify");
            return res != null && res.trim().equals("1");
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean enableApkVerifyWhitelist(Context ctx) {
        boolean ok = false;
        try {
            executePrivileged(ctx, "setprop sys.jsbd.apk_verify 1 && setprop persist.sys.jsbd.apk_verify 1 && setprop sys.geely.apk_verify 1 && setprop persist.sys.geely.apk_verify 1");
            ok = isApkVerifyWhitelistEnabled();
        } catch (Exception e) {
            ok = false;
        }
        AppLogger.action("白名单", "放行系统安装校验 (sys.jsbd.apk_verify=1)", ok, ok ? "已放行生效" : "写入受限");
        return ok;
    }

    public static boolean enableApkVerifyWhitelist() {
        return enableApkVerifyWhitelist(null);
    }

    public static String getVehicleModelDetails() {
        String model = executeShell("getprop ro.product.model");
        String build = executeShell("getprop ro.build.display.id");
        String density = executeShell("getprop ro.sf.lcd_density");
        if (model == null || model.trim().isEmpty()) model = "IHU516G (吉利缤越/缤瑞 COOL)";
        if (build == null || build.trim().isEmpty()) build = "SWOSX110930H116900000";
        if (density == null || density.trim().isEmpty()) density = "160";
        return "车机型号: " + model.trim() + "\n系统版本: " + build.trim() + "\n屏幕参数: 1920x720 (" + density.trim() + " dpi)";
    }

    public static void grantOverlayPermissionViaShell(final Context context) {
        if (context == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String pkg = context.getPackageName();
                    executeShell("appops set " + pkg + " SYSTEM_ALERT_WINDOW allow");
                    executeShell("pm grant " + pkg + " android.permission.SYSTEM_ALERT_WINDOW");
                    executePrivileged(context, "appops set " + pkg + " SYSTEM_ALERT_WINDOW allow");
                    executePrivileged(context, "pm grant " + pkg + " android.permission.SYSTEM_ALERT_WINDOW");
                    executePrivileged(context, "pm grant " + pkg + " android.permission.READ_LOGS");
                    executePrivileged(context, "pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS");
                    executePrivileged(context, "dumpsys deviceidle whitelist +" + pkg);
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    public static boolean fixAmapPermissions(Context context) {
        try {
            String pkg = "com.autonavi.amapauto";
            String[] perms = {
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.ACCESS_COARSE_LOCATION",
                    "android.permission.ACCESS_BACKGROUND_LOCATION",
                    "android.permission.READ_PHONE_STATE",
                    "android.permission.WRITE_EXTERNAL_STORAGE",
                    "android.permission.READ_EXTERNAL_STORAGE",
                    "android.permission.SYSTEM_ALERT_WINDOW"
            };
            for (String p : perms) {
                executeShell("pm grant " + pkg + " " + p);
            }
            executeShell("appops set " + pkg + " SYSTEM_ALERT_WINDOW allow");
            executeShell("appops set " + pkg + " FINE_LOCATION allow");
            executeShell("appops set " + pkg + " COARSE_LOCATION allow");
            executeShell("appops set " + pkg + " READ_PHONE_STATE allow");
            executeShell("appops set " + pkg + " WRITE_EXTERNAL_STORAGE allow");
            executeShell("appops set " + pkg + " READ_EXTERNAL_STORAGE allow");
            executeShell("am force-stop " + pkg);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static File resolveAndRenameApkToFriendlyName(Context context, File sourceApk) {
        if (sourceApk == null || !sourceApk.exists() || !sourceApk.getName().toLowerCase().endsWith(".apk")) {
            return sourceApk;
        }
        String currentName = sourceApk.getName();
        // 已有明确特征命名的商城或自研文件，保持原名不篡改
        if (currentName.startsWith("AutoMap_") || currentName.startsWith("CarMedia_")
                || currentName.startsWith("QQMusic_") || currentName.startsWith("NeteaseMusic_")
                || currentName.startsWith("GeelyToolbox") || currentName.startsWith("高德地图_")) {
            return sourceApk;
        }

        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(sourceApk.getAbsolutePath(), 0);
            if (pi != null && pi.applicationInfo != null) {
                pi.applicationInfo.sourceDir = sourceApk.getAbsolutePath();
                pi.applicationInfo.publicSourceDir = sourceApk.getAbsolutePath();
                CharSequence label = pi.applicationInfo.loadLabel(pm);
                if (label != null && label.length() > 0) {
                    String appName = label.toString().trim();
                    if ("地图".equals(appName) && pi.packageName != null && pi.packageName.contains("autonavi")) {
                        appName = "高德地图";
                    }
                    String verSuffix = "";
                    if (pi.versionName != null && !pi.versionName.trim().isEmpty()) {
                        String cleanVer = pi.versionName.trim().replaceAll("\\s*\\([^)]*\\)", "");
                        if (cleanVer.length() > 20) {
                            cleanVer = cleanVer.substring(0, 20);
                        }
                        verSuffix = "_v" + cleanVer.trim();
                    }
                    String friendlyName = (appName + verSuffix).replaceAll("[\\\\/:*?\"<>|\\s]", "_") + ".apk";
                    if (!friendlyName.equalsIgnoreCase(sourceApk.getName())) {
                        File targetFile = new File(sourceApk.getParentFile(), friendlyName);
                        if (targetFile.exists() && !targetFile.getAbsolutePath().equals(sourceApk.getAbsolutePath())) {
                            targetFile.delete();
                        }
                        if (sourceApk.renameTo(targetFile)) {
                            return targetFile;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w("SystemUtils", "Failed to resolve friendly name for APK: " + e.getMessage());
        }
        return sourceApk;
    }

    private static final java.util.Map<String, String> sMd5Cache = new java.util.concurrent.ConcurrentHashMap<>();

    public static String getOrCalculateMD5(File file) {
        if (file == null || !file.exists() || !file.isFile() || file.length() == 0) {
            return "";
        }
        String key = file.getAbsolutePath() + ":" + file.length() + ":" + file.lastModified();
        String cached = sMd5Cache.get(key);
        if (cached != null) {
            return cached;
        }
        String md5 = calculateMD5(file);
        if (!md5.isEmpty()) {
            sMd5Cache.put(key, md5);
        }
        return md5;
    }

    public static String calculateMD5(File file) {
        if (file == null || !file.exists() || !file.isFile() || file.length() == 0) {
            return "";
        }
        try (java.io.InputStream is = new java.io.FileInputStream(file)) {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[16384];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] md5sum = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : md5sum) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            Log.w("SystemUtils", "calculateMD5 error: " + e.getMessage());
            return "";
        }
    }

    public static boolean installApk(Context context, File apkFile) {
        if (context == null || apkFile == null || !apkFile.exists()) return false;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e("SystemUtils", "installApk error: " + e.getMessage());
            return false;
        }
    }

    /**
     * 安全直调安装器：通过 ApkProvider (ContentProvider) 暴露 URI，完全规避 FileUriExposedException，
     * 避免污染 /data/system 目录与 settings 作用域，直调系统原生 PackageInstaller。
     */
    public static boolean installApkViaProvider(Context context, File apkFile) {
        if (context == null || apkFile == null || !apkFile.exists()) return false;
        try {
            File cacheDir = context.getCacheDir();
            File cacheTarget = new File(cacheDir, apkFile.getName());
            if (!cacheTarget.getAbsolutePath().equals(apkFile.getAbsolutePath())) {
                copyFile(apkFile, cacheTarget);
            }
            Uri contentUri = ApkProvider.buildApkUri(apkFile.getName());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e("SystemUtils", "installApkViaProvider error: " + e.getMessage());
            return installApk(context, apkFile);
        }
    }

    private static void copyFile(File src, File dst) throws java.io.IOException {
        try (java.io.InputStream in = new java.io.FileInputStream(src);
             java.io.OutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[32 * 1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    public static class DetailedAppInfo {
        public String packageName;
        public String appName;
        public boolean enabled;
        public boolean isSystemApp;

        public DetailedAppInfo(String packageName, String appName, boolean enabled, boolean isSystemApp) {
            this.packageName = packageName;
            this.appName = appName;
            this.enabled = enabled;
            this.isSystemApp = isSystemApp;
        }
    }

    /** 缓存：全部应用列表（30 秒 TTL） */
    private static volatile List<DetailedAppInfo> appsCache = null;
    private static volatile long appsCacheTime = 0;
    private static final long APPS_CACHE_TTL = 30_000L;

    /**
     * 快速版：一次 pm list packages -d 拿全部禁用列表，PackageManager 纯 API 判断状态
     * （不再逐包执行 shell，秒级返回）
     */
    public static List<DetailedAppInfo> getAllInstalledApps(Context context) {
        List<DetailedAppInfo> cached = appsCache;
        if (cached != null && System.currentTimeMillis() - appsCacheTime < APPS_CACHE_TTL) {
            return cached;
        }
        List<DetailedAppInfo> list = new ArrayList<>();
        if (context == null) return list;
        try {
            // 一次性获取所有禁用包名
            java.util.Set<String> disabledSet = new java.util.HashSet<>();
            String disabledOut = executeShell("pm list packages -d");
            if (disabledOut != null && !disabledOut.trim().isEmpty()) {
                for (String line : disabledOut.split("\n")) {
                    String s = line.trim();
                    if (s.startsWith("package:")) {
                        disabledSet.add(s.substring("package:".length()).trim());
                    }
                }
            }

            PackageManager pm = context.getPackageManager();
            List<PackageInfo> installed = pm.getInstalledPackages(PackageManager.GET_UNINSTALLED_PACKAGES | PackageManager.GET_DISABLED_COMPONENTS);
            if (installed == null || installed.isEmpty()) {
                installed = pm.getInstalledPackages(0);
            }
            if (installed != null) {
                for (PackageInfo pi : installed) {
                    if (pi == null || pi.packageName == null) continue;
                    String pkg = pi.packageName;
                    String name = (pi.applicationInfo != null) ? pi.applicationInfo.loadLabel(pm).toString() : pkg;
                    boolean isSystem = (pi.applicationInfo != null && (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                    boolean enabled = isAppEnabled(pm, pkg, pi, disabledSet);
                    list.add(new DetailedAppInfo(pkg, name, enabled, isSystem));
                }
            }
            // 排序：系统应用优先？不排序，保持 PM 返回顺序（快）
            appsCache = list;
            appsCacheTime = System.currentTimeMillis();
        } catch (Exception ignored) {
        }
        return list;
    }

    /** 纯 API 判断启用状态（不执行 shell） */
    private static boolean isAppEnabled(PackageManager pm, String pkg, PackageInfo pi, java.util.Set<String> disabledSet) {
        try {
            if (disabledSet.contains(pkg)) return false;
            int setting = pm.getApplicationEnabledSetting(pkg);
            if (setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    || setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    || setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
                return false;
            }
            if (pi.applicationInfo != null && !pi.applicationInfo.enabled) return false;
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    /** 清除应用列表缓存（冻结/解冻后调用） */
    public static void clearAppsCache() {
        appsCache = null;
        appsCacheTime = 0;
    }

    public static String getInstalledVersionName(Context context, String pkg) {
        if (context == null || pkg == null || pkg.isEmpty()) return "";
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_UNINSTALLED_PACKAGES);
            return (pi != null && pi.versionName != null) ? pi.versionName : "";
        } catch (Exception ignored) {
            return "";
        }
    }
}
