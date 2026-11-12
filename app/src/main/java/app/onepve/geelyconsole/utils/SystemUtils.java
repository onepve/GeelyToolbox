package app.onepve.geelyconsole.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SystemUtils {

    private static final String TAG = "SystemUtils";

    public static final String APP_USER_AGENT = "Mozilla/5.0 (Linux; Android 9; IHU516G) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36 GeelyToolbox";

    public static String getAppUserAgent() {
        return APP_USER_AGENT;
    }

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

    public static final String KEY_ADB_MASTER_SWITCH = "adb_master_switch";

    public static boolean isAdbMasterSwitchEnabled(Context ctx) {
        if (ctx == null) return true;
        try {
            android.content.SharedPreferences sp = ctx.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            return sp.getBoolean(KEY_ADB_MASTER_SWITCH, true);
        } catch (Throwable t) {
            return true;
        }
    }

    public static void setAdbMasterSwitchEnabled(Context ctx, boolean enabled) {
        if (ctx == null) return;
        try {
            android.content.SharedPreferences sp = ctx.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            sp.edit().putBoolean(KEY_ADB_MASTER_SWITCH, enabled).apply();
            if (!enabled) {
                AdbClient.closeAllConnections();
            }
        } catch (Throwable ignored) {}
    }

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
        String fallbackHotspotIp = null; // 车机自身热点 (ap0)

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface iface = interfaces.nextElement();
                    try {
                        if (iface.isLoopback()) continue;
                    } catch (Exception ignored) {}

                    String name = iface.getName().toLowerCase();
                    // 彻底过滤车联网 TBox 内部专用网卡与虚拟网卡，防止 192.168.225.xx 误导车主
                    if (name.contains("tbox") || name.startsWith("dummy") || name.contains("sit") || name.contains("p2p")) continue;

                    Enumeration<InetAddress> addresses = iface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                            String host = addr.getHostAddress();
                            if (host == null || host.startsWith("127.")) continue;
                            // 排除车联 TBox 内部私有网段 (192.168.225.x)
                            if (host.startsWith("192.168.225.")) continue;

                            // 1. 优先：Wi-Fi 局域网 / 手机热点客户端 / 有线网络 (wlan0, eth0, rndis)
                            if (name.contains("wlan") || name.contains("rndis") || name.contains("eth")) {
                                status.ip = host;
                                status.ifaceName = name;
                                status.isWifiOrLan = true;
                                status.isCellular = false;
                                status.typeName = name.contains("wlan") ? "Wi-Fi / 手机热点" : "有线/USB 局域网";
                                status.message = "🟢 局域网已就绪，手机连接同一网络后可访问 http://" + host + ":8888";
                                return status;
                            }

                            // 2. 车机自身热点模式 (ap0 / softap)
                            if (name.contains("ap") || name.contains("softap")) {
                                fallbackHotspotIp = host;
                                continue;
                            }

                            // 3. 其它局域网候选
                            if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                                if (status.ip == null) {
                                    status.ip = host;
                                    status.ifaceName = name;
                                    status.isWifiOrLan = true;
                                    status.isCellular = false;
                                    status.typeName = "局域网";
                                    status.message = "🟢 局域网已就绪，手机连接同一网络后可访问 http://" + host + ":8888";
                                }
                            } else {
                                // 4. 蜂窝网络候选
                                if (fallbackCellularIp == null) {
                                    fallbackCellularIp = host;
                                    fallbackCellularIface = name;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        if (status.ip != null) {
            return status;
        }

        // 5. 如果仅检测到车机自身开启了车载热点 (ap0)：
        if (fallbackHotspotIp != null) {
            status.ip = fallbackHotspotIp;
            status.ifaceName = "ap0";
            status.isWifiOrLan = false; // 标为 false，防止误导为可直接反向连入
            status.isCellular = false;
            status.typeName = "车机热点 (仅供上网)";
            status.message = "⚠️ 当前为车机热点模式。受车载系统防火墙隔离保护，手机无法反向访问助手后台；若需使用网页后台或无线快传，请用手机开启热点，并让车机连接手机 Wi-Fi";
            return status;
        }

        // 6. 蜂窝 SIM 流量
        if (fallbackCellularIp != null) {
            status.ip = fallbackCellularIp;
            status.ifaceName = fallbackCellularIface != null ? fallbackCellularIface : "cellular";
            status.isWifiOrLan = false;
            status.isCellular = true;
            status.typeName = "车机内置 SIM 流量";
            status.message = "⚠️ 当前为车机内置 SIM 移动网络 (" + fallbackCellularIp + ")，手机无法跨公网直连！请开启手机热点供车机连接";
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

    // ================= 高频状态查询缓存（车机性能铁律） =================
    // 背景：主界面顶栏每 2.5s、座舱页每 3s 都会查询「白名单/多媒体/商店/其它应用」状态。
    // 旧实现每次都 spawn 一次 `pm list packages -d <pkg>`（车机上一次 0.5~2 秒），
    // 4~5 次串行即让 WebView JS 线程 / 主线程卡死数十秒 —— 这就是「打开工具箱要等好久才能点按钮」的真凶。
    // 现改为：纯 PackageManager API 秒判 + 全量禁用集合后台预热缓存，热路径零进程开销。

    /** 全量禁用包名集合缓存（仅由后台线程预热，热路径只读） */
    private static volatile java.util.Set<String> disabledPkgsCache = null;
    private static volatile long disabledPkgsCacheAt = 0L;
    private static final long DISABLED_PKGS_TTL_MS = 60_000L;
    private static volatile boolean disabledPkgsFetching = false;

    /** 后台预热「全量禁用包名」缓存；绝不阻塞任何调用线程 */
    public static void warmDisabledPackagesCache() {
        boolean stale = disabledPkgsCache == null
                || (System.currentTimeMillis() - disabledPkgsCacheAt) > DISABLED_PKGS_TTL_MS;
        if (!stale || disabledPkgsFetching) return;
        disabledPkgsFetching = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    java.util.Set<String> set = new java.util.HashSet<>();
                    String out = executeShell("pm list packages -d");
                    if (out != null) {
                        for (String line : out.split("\n")) {
                            String s = line.trim();
                            if (s.startsWith("package:")) set.add(s.substring(8).trim());
                        }
                    }
                    disabledPkgsCache = set;
                    disabledPkgsCacheAt = System.currentTimeMillis();
                } catch (Throwable ignored) {
                } finally {
                    disabledPkgsFetching = false;
                }
            }
        }, "SysDisabledPkgsWarm");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 单个应用的启用状态（性能铁律版：热路径 100% 零 shell）
     * 1. PackageManager 纯 API 秒判（用户停用 / 系统停用 / applicationInfo.enabled=false）
     * 2. 已缓存的全量禁用包名集合兜底（缓存缺失时只触发后台预热，本次直接返回，绝不阻塞）
     */
    public static int getAppDetailedState(Context ctx, String pkg) {
        if (ctx == null || pkg == null || pkg.isEmpty()) return APP_STATE_NOT_INSTALLED;
        try {
            PackageInfo pi;
            try {
                pi = ctx.getPackageManager().getPackageInfo(pkg, PackageManager.GET_UNINSTALLED_PACKAGES);
            } catch (PackageManager.NameNotFoundException e) {
                return APP_STATE_NOT_INSTALLED;
            }
            if (pi == null) return APP_STATE_NOT_INSTALLED;

            int setting = ctx.getPackageManager().getApplicationEnabledSetting(pkg);
            if (setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    || setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    || setting == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
                return APP_STATE_DISABLED;
            }
            if (pi.applicationInfo != null && !pi.applicationInfo.enabled) return APP_STATE_DISABLED;

            java.util.Set<String> cached = disabledPkgsCache;
            if (cached != null && cached.contains(pkg)) return APP_STATE_DISABLED;

            warmDisabledPackagesCache();
            return APP_STATE_ENABLED;
        } catch (Throwable e) {
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

    public static boolean isOtaFrozen(Context ctx) {
        // True if ecarx.upgrade is either disabled or not enabled
        return getAppDetailedState(ctx, "ecarx.upgrade") == APP_STATE_DISABLED;
    }

    public static String executePrivileged(Context ctx, String cmd) {
        // 1. Try Localhost ADB TCP (uid 2000 shell privileged access)
        // ⚠️ 用「带缓存」的端口探测：旧实现每次都要对 127.0.0.1/localhost/车机IP 各做一次 1 秒探测，
        //    在 ADB 未开启的车机上等于每次调用先白等 3 秒 —— 常驻服务 3 秒一次轮询时直接卡死主链路。
        try {
            if (isAdbMasterSwitchEnabled(ctx) && AdbClient.isAdbPortOpenCached()) {
                AdbClient.AdbResult adbRes = AdbClient.execute(ctx, cmd);
                if (adbRes != null && adbRes.success && adbRes.output != null) {
                    return adbRes.output;
                }
            }
        } catch (Exception ignored) {
        }

        // 2. 普通 shell 执行兜底
        // ⚠️ 本软件不申请、不使用 root（su）权限：所有功能要么走常规 API，要么走本地 ADB
        //    （uid 2000 shell 特权通道）。历史上此处曾有 su 备用通道，会在无 ADB 的已 root
        //    车机上触发 root 授权弹窗，已按「零 root 申请」要求彻底移除。
        return executeShell(cmd);
    }

    /**
     * 带硬超时的进程执行（返回 null 表示超时 / 失败，绝不阻塞调用线程超过 timeoutMs）
     */
    /**
     * 带硬超时的进程执行（返回 null 表示超时 / 失败，绝不阻塞调用线程超过 timeoutMs）
     */
    public static String execProcess(String arg0, String arg1, String arg2, long timeoutMs) {
        final java.util.concurrent.atomic.AtomicReference<String> out = new java.util.concurrent.atomic.AtomicReference<>(null);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    java.util.List<String> cmdList = new java.util.ArrayList<>();
                    if (arg0 != null) cmdList.add(arg0);
                    if (arg1 != null) cmdList.add(arg1);
                    if (arg2 != null) cmdList.add(arg2);
                    Process p = Runtime.getRuntime().exec(cmdList.toArray(new String[0]));
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()), 1024);
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                        if (sb.length() > 65536) break;
                    }
                    if (!p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        p.destroy();
                        return;
                    }
                    if (p.exitValue() == 0 || sb.length() > 0) {
                        out.set(sb.toString());
                    }
                } catch (Throwable ignored) {
                }
            }
        }, "SysExecTimeout");
        t.setDaemon(true);
        t.start();
        try {
            t.join(timeoutMs + 200L);
        } catch (InterruptedException ignored) {
        }
        return out.get();
    }

    /**
     * 带硬超时的特权命令执行（专供常驻轮询线程使用，超时返回 ""）
     * 用于 dumpsys 等可能因权限/ADB 未开启而长时间无响应的读取，确保轮询 tick 永不卡死。
     */
    public static String executeWithTimeout(Context ctx, String cmd, long timeoutMs) {
        final java.util.concurrent.atomic.AtomicReference<String> out = new java.util.concurrent.atomic.AtomicReference<>("");
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String r = executePrivileged(ctx, cmd);
                    if (r != null) out.set(r);
                } catch (Throwable ignored) {
                }
            }
        }, "SysPrivTimeout");
        t.setDaemon(true);
        t.start();
        try {
            t.join(timeoutMs);
        } catch (InterruptedException ignored) {
        }
        return out.get();
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

    /**
     * 强特权执行通道：必须且只能走 uid 2000 本地 ADB 特权管道！
     * 坚决严禁降级回退到无权限的普通 shell，支持自动重试确保高可靠执行。
     */
    public static String executeStrictPrivileged(Context ctx, String cmd, int maxRetries) {
        if (cmd == null || cmd.trim().isEmpty()) return null;
        if (!isAdbMasterSwitchEnabled(ctx)) return null;
        for (int i = 0; i < maxRetries; i++) {
            try {
                if (AdbClient.isAdbPortOpenCached()) {
                    AdbClient.AdbResult adbRes = AdbClient.execute(ctx, cmd);
                    if (adbRes != null && adbRes.success && adbRes.output != null) {
                        return adbRes.output;
                    }
                }
            } catch (Exception ignored) {}
            if (i < maxRetries - 1) {
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ignored) {}
            }
        }
        return null;
    }

    public static OpResult setPackageEnabled(Context ctx, String pkg, boolean enable) {
        OpResult result;
        if (!isAdbMasterSwitchEnabled(ctx)) {
            result = new OpResult(false, "ADB 总开关已关闭，功能无法使用", "");
            AppLogger.action("应用冻结", (enable ? "解冻: " : "冻结: ") + pkg, false, result.message);
            return result;
        }
        // 关键：立即清空禁用包名内存缓存，强制回读底层真实状态
        disabledPkgsCache = null;
        disabledPkgsCacheAt = 0L;
        try {
            String cmd = enable ? ("pm enable " + pkg + " || pm unhide " + pkg) : ("pm disable-user --user 0 " + pkg + " || pm disable " + pkg + " || pm hide " + pkg);
            // 严禁普通 shell 兜底，必须且只能通过 ADB 特权管道执行，自动重试 3 次！
            String out = executeStrictPrivileged(ctx, cmd, 3);
            if (out == null) {
                result = new OpResult(false, "ADB 特权服务未就绪或未响应，操作未生效，请稍后重试 (请检查本地 5555 ADB 授权)", "");
                AppLogger.action("应用冻结", (enable ? "解冻: " : "冻结: ") + pkg, false, result.message);
                return result;
            }

            if (!enable) {
                try {
                    executeStrictPrivileged(ctx, "am force-stop " + pkg, 2);
                } catch (Throwable ignored) {}
            }
            disabledPkgsCache = null;
            disabledPkgsCacheAt = 0L;
            
            if (out.contains("new state") || out.contains("enabled") || out.contains("disabled") || out.contains("Success") || out.contains("unhidden") || out.contains("hidden") || out.contains("Package " + pkg)) {
                result = new OpResult(true, enable ? "已成功解冻恢复" : "已成功安全冻结", out);
            } else {
                try {
                    Thread.sleep(80);
                } catch (InterruptedException ignored) {}
                disabledPkgsCache = null;
                disabledPkgsCacheAt = 0L;
                int state = getAppDetailedState(ctx, pkg);
                boolean ok = enable ? (state == APP_STATE_ENABLED) : (state == APP_STATE_DISABLED);
                if (ok) {
                    result = new OpResult(true, enable ? "已成功解冻恢复" : "已成功安全冻结", out);
                } else {
                    result = new OpResult(false, "ADB 指令已发送但底层未生效: " + out.trim(), out);
                }
            }
        } catch (Exception e) {
            result = new OpResult(false, "操作异常: " + e.getMessage(), "");
        }
        if ("com.ecarx.multimedia".equals(pkg) && result.success) {
            SteeringWheelKeyManager.updateMultimediaFrozenCache(!enable);
        }
        AppLogger.action("应用冻结", (enable ? "解冻: " : "冻结: ") + pkg, result.success, result.message);
        return result;
    }

    public static void executeReboot(Context ctx) {
        AppLogger.action("系统电源", "触发一键重启车机 (reboot)", true, "整车完整冷启动");
        executePrivileged(ctx, "reboot || svc power reboot");
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
    /** 是否系统预装应用 */
    /**
     * 获取工具箱下载目录：/sdcard/Download/
     * 保持系统原生 Download 目录，与 Android DocumentsUI 官方标准最稳契合，
     * 根治深层子目录导致的各类定制车机文件管理闪退与找不到路径问题。
     */
    public static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.CHINA, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.CHINA, "%.2f MB", bytes / (1024.0 * 1024.0));
    }

    public static long getDirSizeBytes(File dir) {
        if (dir == null || !dir.exists()) return 0;
        if (dir.isFile()) return dir.length();
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                size += (f.isFile() ? f.length() : getDirSizeBytes(f));
            }
        }
        return size;
    }

    public static class CleanDownloadResult {
        public int deletedFiles = 0;
        public long freedBytes = 0;
    }

    public static CleanDownloadResult cleanDownloadDirectory(int mode) {
        CleanDownloadResult result = new CleanDownloadResult();
        try {
            File downloadDir = getAppDownloadDir();
            if (downloadDir == null || !downloadDir.exists()) return result;
            File[] list = downloadDir.listFiles();
            if (list != null) {
                for (File file : list) {
                    String lower = file.getName().toLowerCase();
                    boolean isVoiceProtected = file.isDirectory() && (lower.contains("车载语音自定义") || lower.contains("语音") || lower.contains("voice") || lower.contains("audio") || lower.contains("sound"));
                    if (isVoiceProtected) {
                        // 语音素材专属保护白名单，清理下载目录时永久跳过，防止误删用户自定义音频源素材
                        continue;
                    }
                    if (mode == 1) {
                        result.freedBytes += (file.isFile() ? file.length() : getDirSizeBytes(file));
                        if (deleteRecursiveBool(file)) result.deletedFiles++;
                    } else if (mode == 2) {
                        if (file.isDirectory()) {
                            File[] sub = file.listFiles();
                            if (sub == null || sub.length == 0) {
                                if (file.delete()) result.deletedFiles++;
                            } else {
                                if (lower.contains("voice") || lower.contains("audio") || lower.contains("sound") || lower.contains("语音")) {
                                    continue;
                                }
                                for (File sf : sub) {
                                    if (sf.isFile() && (sf.getName().endsWith(".apk") || sf.getName().endsWith(".zip") || sf.getName().endsWith(".tmp"))) {
                                        result.freedBytes += sf.length();
                                        if (sf.delete()) result.deletedFiles++;
                                    }
                                }
                            }
                        } else if (file.isFile()) {
                            result.freedBytes += file.length();
                            if (file.delete()) result.deletedFiles++;
                        }
                    } else if (mode == 3) {
                        if (file.isFile()) {
                            result.freedBytes += file.length();
                            if (file.delete()) result.deletedFiles++;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static boolean deleteRecursiveBool(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursiveBool(child);
                }
            }
        }
        return fileOrDirectory.delete();
    }

    public static File getAppDownloadDir() {
        File dir = new File(Environment.getExternalStorageDirectory(), "Download");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static void ensureAppDirectories(Context context) {
        try {
            File dir = getAppDownloadDir();
            if (!dir.exists()) dir.mkdirs();
            File customPoolDir = new File(dir, "车载语音自定义");
            if (!customPoolDir.exists()) customPoolDir.mkdirs();

            // 自动平滑迁移历史旧目录 00_车机应用/ 下的文件到 Download/，老版本无缝过渡（纯 Java 极速迁移，不触碰 ADB）
            try {
                File oldDedicatedDir = new File(Environment.getExternalStorageDirectory(), "Download/00_车机应用");
                if (oldDedicatedDir.exists() && oldDedicatedDir.isDirectory()) {
                    File[] oldFiles = oldDedicatedDir.listFiles();
                    if (oldFiles != null) {
                        for (File of : oldFiles) {
                            if (of.isDirectory() && "语音主题包".equals(of.getName())) {
                                // 历史遗留语音主题包迁移至 GeelyPilot/voices/ 物理隔离目录
                                File targetVoiceRoot = new File(Environment.getExternalStorageDirectory(), "GeelyPilot/voices");
                                if (!targetVoiceRoot.exists()) targetVoiceRoot.mkdirs();
                                File[] vfs = of.listFiles();
                                if (vfs != null) {
                                    for (File vf : vfs) {
                                        vf.renameTo(new File(targetVoiceRoot, vf.getName()));
                                    }
                                }
                                of.delete();
                            } else {
                                of.renameTo(new File(dir, of.getName()));
                            }
                        }
                    }
                    deleteRecursive(oldDedicatedDir);
                }
            } catch (Exception ignored) {}

            if (context != null) {
                android.media.MediaScannerConnection.scanFile(context, new String[]{
                        dir.getAbsolutePath()
                }, null, null);
            }
        } catch (Exception ignored) {}
    }

    public static boolean openFileManager(Context context) {
        return openDocumentsUI(context);
    }

    public static boolean openDocumentsUI(Context context) {

        // Strategy 1: 标准原生 Action VIEW_DOWNLOADS (原生系统下载目录，官方标准最稳入口，绝不闪退)
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

        // Strategy 5.5: 常见车载/第三方文件管理器探测启动
        String[] altFileManagers = {
            "com.cxinventor.file.extractor",
            "com.estrongs.android.pop",
            "bin.mt.plus",
            "com.ghisler.android.TotalCommander",
            "com.lonelycatgames.Xplore"
        };
        for (String pkg : altFileManagers) {
            try {
                Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    return true;
                }
            } catch (Exception ignored) {}
        }

        // Strategy 6: Shell am start fallback
        try {
            executeShell("am start -a android.intent.action.VIEW_DOWNLOADS || am start -n com.android.documentsui/.files.FilesActivity");
            return true;
        } catch (Exception ignored) {
        }

        return false;
    }

    public static boolean openSystemSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean openApplicationSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    public static boolean openAccessibilitySettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static int cleanBackgroundProcesses(Context context) {
        int count = 0;
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                List<android.app.ActivityManager.RunningAppProcessInfo> list = am.getRunningAppProcesses();
                if (list != null) {
                    String myPkg = context.getPackageName();
                    for (android.app.ActivityManager.RunningAppProcessInfo info : list) {
                        if (info.pkgList != null) {
                            for (String pkg : info.pkgList) {
                                if (!pkg.equals(myPkg) && !pkg.contains("geely") && !pkg.contains("android") && !pkg.contains("system") && !pkg.contains("ecarx")) {
                                    am.killBackgroundProcesses(pkg);
                                    count++;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return count;
    }

    /** 依次尝试 candidates 里的 pkg/activity，首个启动成功即返回 true（供原厂入口探测共用） */
    private static boolean startFirstAvailable(Context context, String[][] candidates) {
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

    public static boolean openEngineerMode(Context context) {
        return startFirstAvailable(context, new String[][]{
                {"ecarx.debugtools", "ecarx.debugtools.MainActivity"},
                {"com.mediatek.engineermode", "com.mediatek.engineermode.EngineerMode"},
                {"ecarx.debugtools", "ecarx.debugtools.install.InternalInstallActivity"},
                {"com.ecarx.factory", "com.ecarx.factory.MainActivity"}
        });
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

    public static volatile String sOtaCaptureUrl = "";

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
                            sOtaCaptureUrl = url;
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
                    sOtaCaptureUrl = url;
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
                    sOtaCaptureUrl = url;
                    result.details = "从 OTA 服务存储配置中提取";
                    return result;
                }
            }
        } catch (Exception ignored) {
        }

        result.success = false;
        sOtaCaptureUrl = "";
        return result;
    }

    /**
     * 将多个文件打包为 zip
     */
    public static boolean zipFiles(List<File> srcFiles, File destZip) {
        if (srcFiles == null || srcFiles.isEmpty()) return false;
        java.util.zip.ZipOutputStream zos = null;
        try {
            zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(destZip));
            byte[] buffer = new byte[64 * 1024];
            for (File srcFile : srcFiles) {
                if (srcFile == null || !srcFile.exists() || srcFile.length() == 0) continue;
                java.io.FileInputStream fis = null;
                try {
                    java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(srcFile.getName());
                    zos.putNextEntry(zipEntry);
                    fis = new java.io.FileInputStream(srcFile);
                    int length;
                    while ((length = fis.read(buffer)) >= 0) {
                        zos.write(buffer, 0, length);
                    }
                    zos.closeEntry();
                } finally {
                    if (fis != null) {
                        try { fis.close(); } catch (Exception ignored) {}
                    }
                }
            }
            return true;
        } catch (Exception e) {
            Log.e("SystemUtils", "zipFiles error: " + e.getMessage());
            return false;
        } finally {
            if (zos != null) {
                try { zos.close(); } catch (Exception ignored) {}
            }
        }
    }

    public static boolean zipSingleFile(File srcFile, File destZip) {
        if (srcFile == null || !srcFile.exists()) return false;
        java.util.zip.ZipOutputStream zos = null;
        java.io.FileInputStream fis = null;
        try {
            zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(destZip));
            java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(srcFile.getName());
            zos.putNextEntry(zipEntry);
            fis = new java.io.FileInputStream(srcFile);
            byte[] buffer = new byte[64 * 1024];
            int length;
            while ((length = fis.read(buffer)) >= 0) {
                zos.write(buffer, 0, length);
            }
            zos.closeEntry();
            return true;
        } catch (Exception e) {
            Log.e("SystemUtils", "zipSingleFile error: " + e.getMessage());
            return false;
        } finally {
            if (fis != null) {
                try { fis.close(); } catch (Exception ignored) {}
            }
            if (zos != null) {
                try { zos.close(); } catch (Exception ignored) {}
            }
        }
    }

    public interface LogDumpProgressListener {
        void onProgress(int percent, String message);
    }

    private static final java.util.regex.Pattern PATTERN_PHONE = java.util.regex.Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final java.util.regex.Pattern PATTERN_VIN = java.util.regex.Pattern.compile("(?i)\\b([A-HJ-NPR-Z0-9]{4})[A-HJ-NPR-Z0-9]{9}([A-HJ-NPR-Z0-9]{4})\\b");
    private static final java.util.regex.Pattern PATTERN_LATLNG = java.util.regex.Pattern.compile("(?i)LatLng\\{[^}]*\\}");
    // 键名两侧可能带引号（埋点 JSON 写作 "latitude":31.72），旧正则只认 latitude= 因此整段漏掉
    private static final java.util.regex.Pattern PATTERN_GPS_COORD = java.util.regex.Pattern.compile("(?i)([\"']?(?:latitude|longitude|poiLatitude|poiLongitude)[\"']?\\s*[:=]\\s*)([0-9]+\\.[0-9]+)");
    // v1.8.32.13 补齐三处真实漏点（2026-11-12 430884 包实测：45,937 行里 287 行仍带明文坐标/设备号）
    // ① 系统定位原文 Location[gps 31.720748,118.353723 hAcc=...] —— 键名不是 latitude，旧正则完全没命中
    private static final java.util.regex.Pattern PATTERN_LOCATION_OBJ = java.util.regex.Pattern.compile(
            "(?i)(Location\\[(?:gps|network|passive|fused)\\s*)(-?[0-9]+\\.[0-9]+)\\s*,\\s*(-?[0-9]+\\.[0-9]+)");
    // ② MNLD/原生层 lat=31.720748 lng=118.353723 简写 —— 不是 latitude=，旧正则同样漏掉
    private static final java.util.regex.Pattern PATTERN_LATLNG_SHORT = java.util.regex.Pattern.compile(
            "(?i)\\b(lat|lon|lng|latitude|longitude)\\s*[=:]\\s*(-?[0-9]{2,3}\\.[0-9]{3,})");
    // ③ 埋点 JSON 的设备指纹（ecarx_device_id / distinct_id / anonymous_id / $device_id / pcode），
    //    与应用身份强绑定，属于必须抹除的设备标识；旧版只处理了 getXDSN:/deviceID: 两种写法
    private static final java.util.regex.Pattern PATTERN_DEVICE_FINGERPRINT = java.util.regex.Pattern.compile(
            "(?i)(\"?(?:ecarx_device_id|distinct_id|anonymous_id|\\$device_id|device_id|pcode)\"?\\s*[:=]\\s*\"?)([A-Za-z0-9_\\-]{6,})");
    // ④ 车机 IMEI（15 位 86 开头）与埋点设备 UID：430884 包实测 T-Box 串号 868337038618101、
    //    "uid.string":"B0928A74...|0" 全部裸奔。这两项能直接定位到具体一台车，比 VIN 还敏感。
    private static final java.util.regex.Pattern PATTERN_IMEI = java.util.regex.Pattern.compile("\\b86[0-9]{13}\\b");
    private static final char QUOTE = (char) 34;   // 双引号，避开 Java 源码里 \" 的转义地狱
    private static final java.util.regex.Pattern PATTERN_TRACK_UID = java.util.regex.Pattern.compile(
            "(?i)(" + QUOTE + "uid\\.string" + QUOTE + "\\s*:\\s*" + QUOTE + ")[^" + QUOTE + "]+");
    private static final java.util.regex.Pattern PATTERN_MAC = java.util.regex.Pattern.compile(
            "\\b(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\\b");
    private static final java.util.regex.Pattern PATTERN_ADDRESS = java.util.regex.Pattern.compile("(?i)(address|geofullAddress|formatedAddress|poiName)\\s*[:=]\\s*['\"][^'\"]*['\"]");
    private static final java.util.regex.Pattern PATTERN_XDSN = java.util.regex.Pattern.compile("(?i)(getXDSN|deviceID|XDSN)\\s*[:=]\\s*([A-Za-z0-9_]{4})[A-Za-z0-9_]{4,}([A-Za-z0-9_]{4})");

    // 噪音特征过滤：排除 Wi-Fi 扫描、蓝牙 BLE 嗅探、360 逐帧渲染、声卡 20ms 死循环轮询、UI 掉帧、GC、系统压制等纯噪音
    private static final String[] NOISE_PATTERNS = {
        // 1. 无线 Wi-Fi 扫描与驱动心跳
        "wificond", "WifiScanningService", "WifiScanner", "wpa_supplicant",
        "WifiHAL", "WifiConfigStore", "WifiNetworkAgent", "WifiConnectivityManager",
        "WifiProxy-->", "android.net.wifi.SCAN_RESULTS",
        
        // 2. 蓝牙 BLE 广播嗅探 (非车机配对/通话/音频连接)
        "BtGatt.GattService", "bt_btif_scan", "BluetoothLeScanner",
        
        // 3. 360 环视逐帧模型绘制与相机释放 (状态机与触发源完整保留)
        "ecarx_avm_ModelDraw", "LiteCameraFrame",
        
        // 4. 声卡底层死循环轮询与 20ms 级重复音量打印
        "APM_AudioPolicyManager", "abs(mReverseValue -", "setFinalVolume, v:",
        
        // 5. 界面掉帧与底层图形管线
        "Choreographer", "SurfaceFlinger", "OpenGLRenderer", "RenderThread",
        
        // 6. 虚拟机 GC 垃圾回收脉冲
        "concurrent mark sweep GC", "concurrent copying GC", "Explicit concurrent mark sweep",
        
        // 7. 系统压制行与大数据埋点
        // chatty = logd 按进程限流的记账行（expire N lines / identical N lines），
        // 只说明「某个进程刷屏被 limit 掉了」，**不证明缓冲区被覆盖**。
        // 保留它做排查线索：功能失灵时能一眼看出是不是目标进程的日志被限流吃掉。
        // 成本每包几十行、无功能影响，远小于丢失线索的代价。
        "SA.AnalyticsMessages", "SA.TrackTaskManager",

        // 8. 联发科 MTK GPS/GNSS 底层芯片原始时钟与测量数据 (非业务导航，高频硬件灌水)
        "get_gnss_measurement_clock_data", "update_gnss_measurement",
        "mtk_gps_mnl_trigger_mpe", "mtk_gps_sys_gps_mnl_callback", "mnl2hal_gnss_measurements",
        "mtk_gps_sys_nmea_output", "main_event_hdlr", "gps_control_thread",
        "mnld_main_thread", "update_gnss_clock", "send_active_notify",
        "GpsLocationProvider: Location changed", "NativeGnssStatus",

        // 9. 第三方音乐播放器高频 AudioTrack 缓冲心跳 (20ms/次)
        ", streamType=3",

        // 10. WiFi 内核驱动底层 Netlink 等待心跳 (不影响上层 WifiManager 开关与状态监测)
        "nl80211: wait for nl cmd",

        // 11. 预装第三方应用与桌面高频轮询噪音 (输入法、导航心跳、桌面卡片打卡)
        "lyre_logger", "iov_ime_sl", "getStackStackInfosByDisplayId",
        "PermissionUtils", "EcarxAutoFactorGps", "XCNaviService",
        "CarMediaWidget", "BD>SigCommManager", "[AutoMapSDK]MapSDKImpl",
        "Calling a method in the system process without a qualified user",
        "mGpsObserverLocation", "FlowUIHandler] >>>>> Dispatching to Handler",

        // 12. 车机触控滑动事件与系统底层冗余日志
        "BDSMessageConversionUtility", "processPointerEvent handled = true",
        "Found stale backup journal", "Ignoring duplicate uses-permissions"
    };

    // 防误杀白名单 (包含以下车身/业务核心关键字绝对不拦截)
    private static final String[] SAFE_LOG_PATTERNS = {
        "SerialControl", "VehId=", "Vehicle_Gear", "DoorState", "GearState",
        "FL_DOOR", "FR_DOOR", "RL_DOOR", "RR_DOOR", "TRUNK", "SWC",
        "reportKeyToAdaptApi", "shouldCallback", "ECARX_KEY", "CarAudioInfo",
        "Get Speed", "PEPS_PowerMode", "INFO_ID_", "GeelyToolbox", "VehicleAutomationService",
        "VehicleVoicePlayer", "ecarx_avm_state", "AvmServiceManger", "AvmStateManger",
        "camera_state", "turnlight=", "goc", "XCBTPhone", "A2dpSink", "EasMediaBridge"
    };

    public static boolean isNoiseLogLine(String line) {
        if (line == null || line.isEmpty()) return true;
        // 优先白名单放行
        for (int i = 0; i < SAFE_LOG_PATTERNS.length; i++) {
            if (line.contains(SAFE_LOG_PATTERNS[i])) {
                return false;
            }
        }
        // 黑名单噪音判定
        for (int i = 0; i < NOISE_PATTERNS.length; i++) {
            if (line.contains(NOISE_PATTERNS[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * 噪音折叠（v1.8.32.11 新增，替代 isNoiseLogLine 的「整类删除」）。
     *
     * 【车主铁律】脱敏是主目的，去噪只是减体积。噪音里也可能藏着我们需要的东西——
     *   一旦整类删掉，出问题就彻底没线索，那就没法分析问题了。
     * 【做法】每类噪音保留前 {@link #NOISE_SAMPLE_KEEP} 条原文，之后同类只累加计数，
     *   在噪音结束时补一行「同类噪音已折叠 N 条」。真正重复的才折叠，不丢证据。
     *
     * @return 命中返回 true 并累加计数；调用方据此决定是否写这一行（不写时由 endNoiseFold 补汇总行）
     */
    private static final int NOISE_SAMPLE_KEEP = 3;
    private static final java.util.HashMap<String, Integer> noiseKeepCount = new java.util.HashMap<>();
    private static final java.util.HashMap<String, Integer> noiseFoldCount = new java.util.HashMap<>();

    public static boolean foldNoiseLogLine(String line) {
        if (line == null || line.isEmpty()) return false;
        if (!isNoiseLogLine(line)) return false;
        if (line.trim().length() == 0) return false;
        // 找出命中的是哪一类噪音（用于分类计数）
        String kind = null;
        for (int i = 0; i < NOISE_PATTERNS.length; i++) {
            if (line.contains(NOISE_PATTERNS[i])) { kind = NOISE_PATTERNS[i]; break; }
        }
        if (kind == null) kind = "其它噪音";
        int kept = noiseKeepCount.containsKey(kind) ? noiseKeepCount.get(kind) : 0;
        if (kept < NOISE_SAMPLE_KEEP) {
            noiseKeepCount.put(kind, kept + 1);
            return false;   // 保留样本原文
        }
        noiseFoldCount.put(kind, (noiseFoldCount.containsKey(kind) ? noiseFoldCount.get(kind) : 0) + 1);
        return true;        // 折叠（不写这一行）
    }

    /** 噪音折叠汇总行（写进 all.log 末尾，让分析者知道哪些类被折叠了多少条）。 */
    public static String noiseFoldSummary() {
        if (noiseFoldCount.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("---------- 噪音折叠汇总（每类已保留前 ").append(NOISE_SAMPLE_KEEP).append(" 条原文）----------");
        for (String k : noiseFoldCount.keySet()) {
            sb.append('\n').append("[已折叠 ").append(noiseFoldCount.get(k)).append(" 条] ").append(k);
        }
        noiseKeepCount.clear();
        noiseFoldCount.clear();
        return sb.toString();
    }

    /** 经纬度键值脱敏：值必须以 0.000000 覆盖，用 Matcher 循环拼接避免 "$1" 后紧跟数字被误读成 $10。 */
    private static String redactGpsCoord(String text) {
        java.util.regex.Matcher m = PATTERN_GPS_COORD.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(m.group(1) + "0.000000"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String desensitizeLogLine(String line) {
        if (line == null || line.isEmpty()) return line;
        String clean = line;
        if (clean.contains("1") && PATTERN_PHONE.matcher(clean).find()) {
            clean = PATTERN_PHONE.matcher(clean).replaceAll("[REDACTED_PHONE]");
        }
        if (clean.contains("LB3") || clean.contains("vin") || clean.contains("VIN")) {
            clean = PATTERN_VIN.matcher(clean).replaceAll("$1****$2");
        }
        // 定位脱敏：先抹 LatLng{}/Location[gps ..]/latitude=.. 三种完整写法，再兜底抹裸 lat=/lng= 简写
        if (clean.contains("lat") || clean.contains("Lat") || clean.contains("lng") || clean.contains("Lng")
                || clean.contains("lon") || clean.contains("Lon") || clean.contains("Location[")) {
            clean = PATTERN_LATLNG.matcher(clean).replaceAll("LatLng{REDACTED_LOCATION}");
            clean = PATTERN_LOCATION_OBJ.matcher(clean).replaceAll("$1{REDACTED_LOCATION}");
            clean = redactGpsCoord(clean);
            clean = PATTERN_LATLNG_SHORT.matcher(clean).replaceAll("$1=0.000000");
        }
        if (clean.contains("address") || clean.contains("Address") || clean.contains("poiName")) {
            clean = PATTERN_ADDRESS.matcher(clean).replaceAll("$1=\"[REDACTED_ADDRESS]\"");
        }
        // 设备指纹脱敏：埋点里的 ecarx_device_id / distinct_id / $device_id / pcode 与车机身份强绑定，一律抹除
        if (clean.contains("device_id") || clean.contains("deviceID") || clean.contains("distinct_id")
                || clean.contains("anonymous_id") || clean.contains("pcode")
                || clean.contains("XDSN") || clean.contains("xdsn")) {
            clean = PATTERN_DEVICE_FINGERPRINT.matcher(clean).replaceAll("$1[REDACTED_DEVICE_ID]");
            clean = PATTERN_XDSN.matcher(clean).replaceAll("$1:$2****$3");
        }
        // MAC 物理地址脱敏（蓝牙/Wi-Fi 网卡地址可用于设备定位）
        if (clean.contains(":")) {
            clean = PATTERN_MAC.matcher(clean).replaceAll("[REDACTED_MAC]");
        }
        // IMEI / 埋点设备 UID 兜底：不依赖键名，凡是 15 位 86 开头串号一律抹除
        if (clean.contains("86") && clean.length() > 14) {
            clean = PATTERN_IMEI.matcher(clean).replaceAll("[REDACTED_IMEI]");
        }
        if (clean.contains("uid.string")) {
            clean = PATTERN_TRACK_UID.matcher(clean).replaceAll("$1[REDACTED_DEVICE_ID]");
        }
        return clean;
    }

    public static void desensitizeFile(File src, File dest) {
        if (src == null || !src.exists()) return;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(src));
             java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.FileWriter(dest))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // v1.8.32.11：不再「命中噪音就整行丢弃」，改为每类保留前 3 条、其余折叠计数。
                // 脱敏（隐私）与去噪（体积）彻底分开：这里只折叠重复，隐私一律抹成 [REDACTED]。
                if (foldNoiseLogLine(line)) {
                    continue;
                }
                writer.write(desensitizeLogLine(line));
                writer.newLine();
            }
            // 末尾补噪音折叠汇总，让分析者知道哪些类被折叠了多少条（证据可追溯）
            String foldSummary = noiseFoldSummary();
            if (foldSummary.length() > 0) {
                writer.write(foldSummary);
                writer.newLine();
            }
        } catch (Exception ignored) {}
    }

    public static String desensitizeText(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\n");
        for (String l : lines) {
            sb.append(desensitizeLogLine(l)).append("\n");
        }
        return sb.toString();
    }

    public static String getSystemProp(String key, String def) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method m = sp.getMethod("get", String.class, String.class);
            String val = (String) m.invoke(null, key, def);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        } catch (Throwable ignored) {}
        try {
            String val = executeShell("getprop " + key);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        } catch (Throwable ignored) {}
        return def;
    }

    public static String getHardwareChip() {
        String projectInfo = getSystemProp("ro.boot.ecarx_projectinfo", "");
        if (projectInfo.contains("624")) return "IHU624G";
        if (projectInfo.contains("521")) return "IHU521G";
        if (projectInfo.contains("516")) return "IHU516G";
        if (projectInfo.contains("510")) return "IHU510G";
        if (projectInfo.contains("8155")) return "IHU8155";
        
        String model = getCleanDeviceModel().toUpperCase();
        if (model.contains("SX11") || model.contains("516")) return "IHU516G";
        if (model.contains("SS13") || model.contains("521")) return "IHU521G";
        if (model.contains("FE6A") || model.contains("510")) return "IHU510G";
        if (model.contains("624")) return "IHU624G";
        if (model.contains("8155")) return "IHU8155";
        
        String platform = getSystemProp("ro.board.platform", "");
        if (!platform.isEmpty()) {
            String clean = platform.toUpperCase().replaceAll("[^A-Z0-9]", "");
            if ("ECARXP".equals(clean) || "ECARX".equals(clean)) {
                return "IHU516G"; // 亿咖通 E02 核心默认平台代号
            }
            return clean;
        }
        return "IHU516G";
    }

    public static String getVinLast6() {
        return getVinLast6(null);
    }

    public static String getVinLast6(Context context) {
        // 1. 优先从吉利亿咖通车架号系统属性抓取真实 VIN
        String vin = getSystemProp("ro.ecarx.vin", "");
        if (vin.isEmpty() || isDummyVinOrSn(vin)) vin = getSystemProp("persist.sys.vin", "");
        if (vin.isEmpty() || isDummyVinOrSn(vin)) vin = getSystemProp("ro.vin", "");
        if (vin.isEmpty() || isDummyVinOrSn(vin)) vin = getSystemProp("persist.sys.ecarx.vin", "");

        // 2. 尝试通过反射亿咖通 AdaptAPI Device 接口抓取底层真实 VIN
        if (vin.isEmpty() || isDummyVinOrSn(vin)) {
            try {
                Class<?> devClass = Class.forName("com.ecarx.xui.adaptapi.device.Device");
                java.lang.reflect.Method createMethod = devClass.getMethod("create", Context.class);
                Object devObj = createMethod.invoke(null, context);
                if (devObj != null) {
                    java.lang.reflect.Method getVinMethod = devObj.getClass().getMethod("getVin");
                    Object res = getVinMethod.invoke(devObj);
                    if (res instanceof String) {
                        String realVin = (String) res;
                        if (!realVin.isEmpty() && !isDummyVinOrSn(realVin)) {
                            vin = realVin;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 3. 尝试从吉利硬件出厂唯一标识 IHUID 提取
        if (vin.isEmpty() || isDummyVinOrSn(vin)) {
            String ihuid = getSystemProp("ro.ecarx.ihuid", "");
            if (ihuid.isEmpty() || isDummyVinOrSn(ihuid)) {
                ihuid = getSystemProp("persist.sys.ecarx.ihuid", "");
            }
            if (!ihuid.isEmpty() && !isDummyVinOrSn(ihuid)) {
                vin = ihuid;
            }
        }

        // 4. 尝试从芯片通用序列号提取 (严格剔除 0123456789ABCDEF 等芯片出厂通用占位符)
        if (vin.isEmpty() || isDummyVinOrSn(vin)) {
            String sn = getSystemProp("ro.boot.serialno", "");
            if (sn.isEmpty() || isDummyVinOrSn(sn)) sn = getSystemProp("ro.serialno", "");
            if (sn.isEmpty() || isDummyVinOrSn(sn)) {
                try {
                    sn = android.os.Build.SERIAL;
                } catch (Throwable ignored) {}
            }
            if (!sn.isEmpty() && !isDummyVinOrSn(sn)) {
                vin = sn;
            }
        }

        // 5. 格式化提取后 6 位
        String clean = (vin != null) ? vin.replaceAll("[^a-zA-Z0-9]", "") : "";
        if (clean.length() >= 6) {
            return clean.substring(clean.length() - 6).toUpperCase(java.util.Locale.US);
        } else if (clean.length() > 0) {
            return String.format(java.util.Locale.US, "%6s", clean).replace(' ', '0').toUpperCase(java.util.Locale.US);
        }
        return "000000";
    }

    /**
     * 判断是否为车规芯片/Android通用测试占位序列号 (如 0123456789ABCDEF、unknown、000000 等)
     */
    private static boolean isDummyVinOrSn(String val) {
        if (val == null) return true;
        String s = val.trim();
        if (s.isEmpty() || "unknown".equalsIgnoreCase(s) || "null".equalsIgnoreCase(s)) return true;
        String clean = s.replaceAll("[^a-zA-Z0-9]", "").toUpperCase(java.util.Locale.US);
        return clean.contains("0123456789ABCDEF")
                || clean.equals("ABCDEF")
                || clean.endsWith("ABCDEF")
                || clean.equals("0123456789")
                || clean.equals("1234567890")
                || clean.equals("000000");
    }

    public static String getCleanDeviceModel() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method m = sp.getMethod("get", String.class, String.class);
            String model = (String) m.invoke(null, "ro.product.model", "");
            if (model == null || model.trim().isEmpty()) {
                model = executeShell("getprop ro.product.model");
            }
            if (model != null) {
                model = model.trim().replaceAll("[^a-zA-Z0-9_-]", "");
            }
            if (model != null && !model.isEmpty()) {
                return model;
            }
        } catch (Throwable ignored) {}
        return "IHU516G";
    }

    public static JSONObject dumpFullSystemLogcat(Context context) {
        return dumpFullSystemLogcat(context, null);
    }

    public static JSONObject dumpFullSystemLogcat(Context context, LogDumpProgressListener listener) {
        return dumpProtocolVerifyLogcat(context, listener);
    }

    /**
     * 一键采集协议验证日志：全量抓取、5重隐私深度脱敏（抹除手机号/车架号/GPS/地址）并覆盖归档为唯一样本
     */
    public static JSONObject dumpProtocolVerifyLogcat(Context context) {
        return dumpProtocolVerifyLogcat(context, null);
    }

    public static JSONObject dumpProtocolVerifyLogcat(Context context, LogDumpProgressListener listener) {
        JSONObject result = new JSONObject();
        File logFile = null;
        File wheelFile = null;
        File doorFile = null;
        File gearFile = null;
        File powerFile = null;
        File appFile = null;
        File summaryFile = null;
        try {
            ensureAppDirectories(context);
            File downloadDir = getAppDownloadDir();
            File tempDir = new File(downloadDir, "log_verify_" + System.currentTimeMillis());
            if (!tempDir.exists()) tempDir.mkdirs();

            // 1. 抓取全量日志（v1.8.32.11 重写）
            //    【病根】`logcat -d` 只是给 logd 缓冲区拍快照。常驻订阅一开，快照只剩尾部 3 秒。
            //      铁证：同车 1.8.28 及以前 all.log 有 2 万~4.5 万行（52~210 秒），
            //           beta.2 之后全部只剩 700~2000 行（3 秒），引擎盖那类操作整段抓不到。
            //    【解法】主源改为助手自己内存里的全量环形缓冲（读线程逐行抄录、无过滤），
            //       一次抓包能覆盖最近 4~5 分钟；只有环形缓冲不足 5000 行时才退回 logcat -d 拍快照。
            if (listener != null) listener.onProgress(15, "正在汇总整车底层日志...");
            File rawLogFile = new File(tempDir, "raw_all.log");
            int ringLines = 0;
            try {
                ringLines = app.onepve.geelyconsole.services.VehicleAutomationService.rawRingSize();
            } catch (Throwable ignored) {}
            if (ringLines >= 5000) {
                // 主源：内存全量缓冲（流式写盘，不拼巨型字符串）
                try {
                    ringLines = app.onepve.geelyconsole.services.VehicleAutomationService.dumpRawRingToFile(rawLogFile);
                } catch (Throwable ignored) {}
                result.put("log_source", "memory_ring");
                result.put("log_ring_lines", ringLines);
                if (listener != null) listener.onProgress(25, "已汇总内存全量日志 " + ringLines + " 行...");
            } else {
                // 兜底：内存缓冲还没攒够（刚开机/刚装完），退回 logd 快照
                String cmd = "logcat -d -v time > " + rawLogFile.getAbsolutePath();
                executeShell(cmd);
                result.put("log_source", "logcat_snapshot");
                result.put("log_ring_lines", ringLines);
            }

            if (!rawLogFile.exists() || rawLogFile.length() == 0) {
                result.put("success", false);
                result.put("message", "未能生成日志文件，请检查车机存储权限");
                deleteRecursive(tempDir);
                return result;
            }

            // 1.5 流式脱敏处理全量日志（100% 抹杀手机号、车架号、实时GPS经纬度与详细家庭地址、设备ID）
            if (listener != null) listener.onProgress(35, "正在执行多重隐私安全脱敏 (抹除手机号/车架号/GPS/地址)...");
            logFile = new File(tempDir, "all.log");
            desensitizeFile(rawLogFile, logFile);
            rawLogFile.delete(); // 立即物理粉碎原始未脱敏碎片

            // 2. 按关键词分类过滤（切片文件直接继承脱敏后的干净数据）
            if (listener != null) listener.onProgress(55, "正在分类提取方控/换挡/车门/蓝牙专属切片...");
            wheelFile = filterLogByKeywords(logFile, new File(tempDir, "wheel.log"),
                    "方控按键", "HAL_KEY", "HW_KEY_INPUT", "reportKeyToAdaptApi", "shouldCallback",
                    "ECARX_KEY", "方控按键", "方向盘", "steering", "SWC", "按键");

            doorFile = filterLogByKeywords(logFile, new File(tempDir, "door.log"),
                    "DoorStateManager", "车门", "车门已打开", "车门已关好", "车门已关闭", "FL_DOOR", "FR_DOOR", "RL_DOOR", "RR_DOOR",
                    "mModelLFDoor", "mModelRFDoor", "mModelLRDoor", "mModelRRDoor", "TRUNK", "尾门", "后备箱");

            gearFile = filterLogByKeywords(logFile, new File(tempDir, "gear.log"),
                    "GearStateMachine", "挡位状态", "ECARX_GEAR_POSITION", "GEAR_SELECTION", "CURRENT_GEAR",
                    "VehId=Vehicle_Gear", "onVehicleEventGear", "AvmStateManger", "isGearReverse",
                    "换挡", "档位", "gear", "DRIVEINFO", "INFO_ID_VDRIVEINFO_GEAR_POSITION", "驾驶模式", "DriveMode");

            powerFile = filterLogByKeywords(logFile, new File(tempDir, "power.log"),
                    "PEPS_PowerMode", "INFO_ID_VPOWERINFO", "KEY_STATE", "ENGINE_STATE",
                    "AP_POWER_BOOTUP_REASON", "电源状态", "点火", "熄火", "上电", "下电", "battery");

            appFile = filterLogByKeywords(logFile, new File(tempDir, "app.log"),
                    "GeelyToolbox", "VehicleAutomationService",
                    "VehicleVoicePlayer", "系统日志", "HAL探针", "去重");

            File btFile = filterLogByKeywords(logFile, new File(tempDir, "bluetooth.log"),
                    "Bluetooth", "bluetooth", "a2dp", "A2DP", "A2dpSink", "bt_stack", "avdt",
                    "AudioFocus", "requestAudioFocus", "abandonAudioFocus", "EasMediaBridge",
                    "updateCurrentSourceType", "SourceType", "STREAM_BLUETOOTH", "SCO", "HFP", "音频通道", "蓝牙");

            // 2.5 将最新纯内存守护日志一并脱敏归入 guard.log
            File guardFile = new File(tempDir, "guard.log");
            writeTextFile(guardFile, desensitizeText(AppLogger.readRecentLogs(500)));

            // 2.6 关键车身信号轨迹（纯内存记录，不受车机 3 秒缓冲区影响）
            // 车主点"抓日志"这一刻才把内存里的变化轨迹一次性写盘，平时零磁盘写入。
            File signalsFile = new File(tempDir, "signals.log");
            try {
                writeTextFile(signalsFile, desensitizeText(
                        app.onepve.geelyconsole.utils.KeySignalRecorder.exportAll()));
            } catch (Throwable t) {
                signalsFile = null;
            }

            // 3. 生成统计摘要
            if (listener != null) listener.onProgress(75, "正在生成车辆诊断健康体检摘要 (JSON)...");
            summaryFile = new File(tempDir, "summary.json");
            JSONObject summary = buildLogSummary(logFile, wheelFile, doorFile, gearFile, powerFile, appFile);
            summary.put("app_version", app.onepve.geelyconsole.BuildConfig.VERSION_NAME);
            summary.put("app_version_code", app.onepve.geelyconsole.BuildConfig.VERSION_CODE);
            summary.put("device_model", getCleanDeviceModel());
            summary.put("hardware_chip", getHardwareChip());
            String trimName = detectVehicleTrim(context, logFile);
            summary.put("trim_name", trimName);
            try {
                app.onepve.geelyconsole.hal.IVehiclePlatform currentPlat = app.onepve.geelyconsole.hal.VehiclePlatformFactory.getPlatform(context);
                if (currentPlat != null) {
                    summary.put("platform_name", currentPlat.getPlatformName());
                    summary.put("platform_code", currentPlat.getPlatformCode());
                }
            } catch (Throwable ignored) {}
            summary.put("privacy_status", "100%_desensitized_phone_vin_location_redacted");

            // ═══ 抓包可诊断性（2026-11-11 新增）═══
            // 教训：车主连续几次抓到「main 段只有 3 秒」，误以为是缓冲区只有 256KB、
            // 是我们的采集故障。实际解包发现 all.log 第一行就是 "vold (the awakening)
            // firing up"（Android 卷管理冷启动第一句）—— 车机刚开机就点了抓日志，
            // logd 缓冲里本来就只有那几秒的内容，抓到的就是全部。
            // 结论：日志短不一定是故障，可能只是抓早了。把「车机运行多久」写进摘要，
            // 以后拿到包第一眼就能判断这次数据能不能用，不用再解包翻 vold。
            try {
                long bootAgeSec = android.os.SystemClock.elapsedRealtime() / 1000L;
                summary.put("boot_age_seconds", bootAgeSec);
                summary.put("boot_age_text", formatBootAge(bootAgeSec));
                // 小于 120 秒基本必然只有几秒日志，直接给出人话提示，免得白排查一轮
                summary.put("boot_age_hint", bootAgeSec < 120L
                        ? "车机刚开机 " + bootAgeSec + " 秒就抓的包，日志天然很短，不代表采集故障"
                        : "车机已运行 " + formatBootAge(bootAgeSec) + "，日志长度应属正常");
            } catch (Throwable ignored) {}
            // 实际抓到的日志时间跨度（与 boot_age 交叉验证：跨度远小于运行时长才说明被覆盖/限流）
            try {
                summary.put("log_span", measureLogSpan(logFile));
            } catch (Throwable ignored) {}
            // 日志来源（v1.8.32.12）：memory_ring = 内存全量环形缓冲（正常），logcat_snapshot = logd 快照兜底
            //   看这一行就能判断这次抓包到底抓全了没有
            try {
                summary.put("log_source", result.optString("log_source", "unknown"));
                summary.put("log_ring_lines", result.optInt("log_ring_lines", 0));
            } catch (Throwable ignored) {}
            // 关键信号采集体检：各功能变化次数、车门报文收帧数、丢行数（一眼看出是否漏采）
            try {
                summary.put("signal_health", app.onepve.geelyconsole.utils.KeySignalRecorder.getHealthSnapshot()
                        + " | " + app.onepve.geelyconsole.utils.HoodStateManager.getHealthSnapshot());
                summary.put("signal_stats", app.onepve.geelyconsole.utils.KeySignalRecorder.buildStatsJson());
            } catch (Throwable ignored) {}
            // Logcat 通道健康快照：车主发包后仅看这一行即可判断通道是否稳定（建立/断开/兜底次数 + 末次断因）
            try {
                summary.put("logcat_channel", app.onepve.geelyconsole.services.VehicleAutomationService.getLogcatHealthSnapshot());
            } catch (Throwable ignored) {}
            writeTextFile(summaryFile, summary.toString(2));

            // 4. 打包并自动清理历史旧包（实现唯一文件覆盖）
            if (listener != null) listener.onProgress(90, "正在压缩归档为 ZIP 诊断包并清理旧文件...");
            File[] oldLogs = downloadDir.listFiles(new java.io.FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return (name.startsWith("Geely_Log_") || name.endsWith("_log.zip") || (name.contains("_IHU") && name.endsWith(".zip"))) && name.endsWith(".zip");
                }
            });
            if (oldLogs != null) {
                for (File f : oldLogs) {
                    try { f.delete(); } catch (Throwable ignored) {}
                }
            }

            String modelName = getCleanDeviceModel();
            String chipName = getHardwareChip();
            // 规范命名格式：去掉多余的 _log，直接按 [车型]_[平台].zip 命名，如 SX11A3_IHU516G.zip
            String zipFileName = modelName + "_" + chipName + ".zip";
            File zipFile = new File(downloadDir, zipFileName);
            if (zipFile.exists()) zipFile.delete();

            List<File> filesToZip = new ArrayList<>();
            filesToZip.add(logFile);
            if (guardFile.exists() && guardFile.length() > 0) filesToZip.add(guardFile);
            if (btFile.exists() && btFile.length() > 0) filesToZip.add(btFile);
            if (wheelFile.exists() && wheelFile.length() > 0) filesToZip.add(wheelFile);
            if (doorFile.exists() && doorFile.length() > 0) filesToZip.add(doorFile);
            if (gearFile.exists() && gearFile.length() > 0) filesToZip.add(gearFile);
            if (powerFile.exists() && powerFile.length() > 0) filesToZip.add(powerFile);
            if (appFile.exists() && appFile.length() > 0) filesToZip.add(appFile);
            if (signalsFile != null && signalsFile.exists() && signalsFile.length() > 0) filesToZip.add(signalsFile);
            if (summaryFile.exists() && summaryFile.length() > 0) filesToZip.add(summaryFile);

            boolean zipped = zipFiles(filesToZip, zipFile);

            // 5. 清理临时目录，绝不残留散落未压缩原始日志
            deleteRecursive(tempDir);

            if (zipped && zipFile.exists() && zipFile.length() > 0) {
                result.put("success", true);
                result.put("path", zipFile.getAbsolutePath());
                result.put("filename", zipFileName);
                double mb = zipFile.length() / (1024.0 * 1024.0);
                String sizeStr = (mb >= 1.0) ? String.format(java.util.Locale.CHINA, "%.2f MB", mb) : (zipFile.length() / 1024 + " KB");
                result.put("sizeStr", sizeStr);
                result.put("chip", getHardwareChip());
                result.put("modelCode", modelName);
                result.put("vinLast6", getVinLast6(context));
                long lastMod = zipFile.lastModified();
                String capturedTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(new java.util.Date(lastMod > 0 ? lastMod : System.currentTimeMillis()));
                result.put("capturedTime", capturedTime);
                result.put("message", "全量脱敏日志已打包: " + zipFileName);
                if (listener != null) listener.onProgress(100, "✓ 全量脱敏日志已打包完成: " + zipFileName);
            } else {
                result.put("success", false);
                result.put("message", "日志打包失败");
                if (listener != null) listener.onProgress(100, "❌ 日志打包失败");
            }
        } catch (Exception e) {
            try {
                result.put("success", false);
                result.put("message", "采集日志异常: " + e.getMessage());
            } catch (Exception ignored) {}
            if (listener != null) listener.onProgress(100, "❌ 采集异常: " + e.getMessage());
        }
        return result;
    }

    public static JSONObject getExistingDiagnosticLog(Context context) {
        JSONObject res = new JSONObject();
        try {
            ensureAppDirectories(context);
            File downloadDir = getAppDownloadDir();
            if (downloadDir == null || !downloadDir.exists()) {
                res.put("exists", false);
                return res;
            }
            String model = getCleanDeviceModel();
            String chip = getHardwareChip();
            File target = new File(downloadDir, model + "_" + chip + ".zip");
            if (!target.exists()) {
                target = new File(downloadDir, model + "_" + chip + "_log.zip");
            }
            if (!target.exists()) {
                File[] files = downloadDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f != null && f.isFile() && f.getName().startsWith(model) && f.getName().endsWith(".zip")) {
                            target = f;
                            break;
                        }
                    }
                }
            }
            if (target != null && target.exists() && target.length() > 0) {
                res.put("exists", true);
                res.put("path", target.getAbsolutePath());
                res.put("filename", target.getName());
                double mb = target.length() / (1024.0 * 1024.0);
                String sizeStr = (mb >= 1.0) ? String.format(java.util.Locale.CHINA, "%.2f MB", mb) : (target.length() / 1024 + " KB");
                res.put("sizeStr", sizeStr);
                res.put("chip", chip);
                res.put("modelCode", model);
                res.put("vinLast6", getVinLast6(context));
                String capturedTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(new java.util.Date(target.lastModified()));
                res.put("capturedTime", capturedTime);
                return res;
            }
        } catch (Exception ignored) {}
        try {
            res.put("exists", false);
        } catch (Exception ignored) {}
        return res;
    }

    private static File filterLogByKeywords(File src, File dest, String... keywords) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(src));
             java.io.FileWriter writer = new java.io.FileWriter(dest)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String lower = line.toLowerCase();
                for (String kw : keywords) {
                    if (kw != null && lower.contains(kw.toLowerCase())) {
                        writer.write(line);
                        writer.write("\n");
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
        return dest;
    }

    /**
     * 把「车机已运行多少秒」格式化成大白话（1小时23分05秒）。
     * 给 summary.json 的 boot_age_text 用，车主和排查者一眼能读。
     */
    private static String formatBootAge(long sec) {
        if (sec < 60) return sec + "秒";
        long h = sec / 3600, m = (sec % 3600) / 60, s2 = sec % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("小时");
        if (m > 0) sb.append(m).append("分");
        sb.append(s2).append("秒");
        return sb.toString();
    }

    /**
     * 量出这次抓到的日志实际覆盖多长时间（秒，保留一位小数）。
     *
     * 为什么需要它：光有 boot_age 还不够 ——
     *   · 车机开机 2 小时、日志只覆盖 40 秒 → 缓冲被覆盖或限流过（真故障）
     *   · 车机开机 30 秒、日志覆盖 28 秒 → 抓得早而已（数据本来就该这么短）
     * 两个数一对比，就能把「采集故障」和「抓早了」彻底分开，不用再靠猜。
     *
     * 只取 main 段首行与末行的时间戳，不整文件解析，几乎零开销。
     */
    private static double measureLogSpan(File all) {
        if (all == null || !all.exists()) return 0.0;
        String first = null, last = null, minStamp = null, maxStamp = null;
        boolean inMain = false, sawMarker = false;
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US);
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(all))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.contains("beginning of")) {
                    // 只认 main 段，system/crash 段的时间跨度口径不同，混在一起没意义
                    sawMarker = true;
                    inMain = line.trim().endsWith("main");
                    continue;
                }
                if (line.length() < 18) continue;
                if (line.charAt(2) != '-' || line.charAt(5) != ' ') continue;
                String stamp = line.substring(0, 18);
                // 内存环形缓冲 dump 没有 "beginning of" 段标记；同时也可能因多次拼接而局部乱序，
                // 所以除了首末行，再全程跟踪 min/max，避免把「抓早了」误判成「采集故障」。
                if (!sawMarker || inMain) {
                    if (first == null) first = stamp;
                    last = stamp;
                    // MM-dd HH:mm:ss.SSS 定长同格式，字符串比较等价于时间比较
                    if (minStamp == null || stamp.compareTo(minStamp) < 0) minStamp = stamp;
                    if (maxStamp == null || stamp.compareTo(maxStamp) > 0) maxStamp = stamp;
                }
            }
        } catch (Throwable ignored) {}
        if (first == null || last == null) return 0.0;
        try {
            // 优先用 min/max（对乱序免疫）；无乱序时与首末行结果一致
            String s1 = minStamp != null ? minStamp : first;
            String s2 = maxStamp != null ? maxStamp : last;
            java.util.Date d1 = fmt.parse(s1), d2 = fmt.parse(s2);
            double span = (d2.getTime() - d1.getTime()) / 1000.0;
            if (span < 0) span += 86400.0;   // 跨零点
            return Math.round(span * 10.0) / 10.0;
        } catch (Throwable t) {
            return 0.0;
        }
    }

    private static JSONObject buildLogSummary(File all, File wheel, File door, File gear, File power, File app) {
        JSONObject summary = new JSONObject();
        try {
            summary.put("timestamp", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(new java.util.Date()));
            summary.put("allLines", countLines(all));
            summary.put("wheelLines", countLines(wheel));
            summary.put("doorLines", countLines(door));
            summary.put("gearLines", countLines(gear));
            summary.put("powerLines", countLines(power));
            summary.put("appLines", countLines(app));

            JSONObject wheelStats = new JSONObject();
            wheelStats.put("keyDown", countMatches(all, "方控按键", "Down") + countMatches(all, "reportKeyToAdaptApi", "press"));
            wheelStats.put("keyUp", countMatches(all, "方控按键", "Up") + countMatches(all, "reportKeyToAdaptApi", "release"));
            wheelStats.put("dedup", countMatches(all, "重复触发", "去重"));
            summary.put("wheelStats", wheelStats);

            JSONObject doorStats = new JSONObject();
            doorStats.put("opened", countMatches(all, "车门已打开"));
            doorStats.put("closed", countMatches(all, "车门已关闭") + countMatches(all, "车门已关好"));
            doorStats.put("warning", countMatches(all, "车门未关好"));
            summary.put("doorStats", doorStats);
        } catch (Exception ignored) {}
        return summary;
    }

    private static int countLines(File file) {
        if (file == null || !file.exists()) return 0;
        int count = 0;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            while (reader.readLine() != null) count++;
        } catch (Exception ignored) {}
        return count;
    }

    private static int countMatches(File file, String... needles) {
        if (file == null || !file.exists()) return 0;
        int count = 0;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String lower = line.toLowerCase();
                boolean match = true;
                for (String n : needles) {
                    if (n != null && !lower.contains(n.toLowerCase())) {
                        match = false;
                        break;
                    }
                }
                if (match) count++;
            }
        } catch (Exception ignored) {}
        return count;
    }

    private static int countAvailableMatches(File file, String needle) {
        if (file == null || !file.exists() || needle == null) return 0;
        int count = 0;
        String lowerNeedle = needle.toLowerCase();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String lower = line.toLowerCase();
                if (lower.contains(lowerNeedle) && !lower.contains("notavailable") && !lower.contains("status(notavailable)")) {
                    count++;
                }
            }
        } catch (Exception ignored) {}
        return count;
    }

    private static void writeTextFile(File file, String text) {
        try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
            writer.write(text);
        } catch (Exception ignored) {}
    }

    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
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

    /** 白名单属性缓存（车机性能铁律：顶栏每 2.5s 轮询，绝不每次都 spawn getprop 进程） */
    private static volatile Boolean whitelistCache = null;
    private static volatile long whitelistCacheAt = 0L;
    private static final long WHITELIST_TTL_MS = 3000L;

    /** 清除白名单缓存（放行/冻结白名单操作后立即调用，保证状态实时） */
    public static void clearWhitelistCache() {
        whitelistCache = null;
        whitelistCacheAt = 0L;
    }

    public static boolean isApkVerifyWhitelistEnabled() {
        long now = System.currentTimeMillis();
        Boolean cached = whitelistCache;
        if (cached != null && (now - whitelistCacheAt) < WHITELIST_TTL_MS) {
            return cached;
        }
        // 缓存过期时：先返回上一次结果并后台刷新，彻底杜绝 UI / JS 线程被 getprop 阻塞
        if (cached != null) {
            refreshWhitelistAsync();
            return cached;
        }
        boolean ok = readApkVerifyProp();
        whitelistCache = ok;
        whitelistCacheAt = now;
        return ok;
    }

    private static volatile boolean whitelistRefreshing = false;

    /** 后台刷新白名单缓存（非阻塞） */
    public static void refreshWhitelistAsync() {
        if (whitelistRefreshing) return;
        whitelistRefreshing = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean ok = readApkVerifyProp();
                    whitelistCache = ok;
                    whitelistCacheAt = System.currentTimeMillis();
                } catch (Throwable ignored) {
                } finally {
                    whitelistRefreshing = false;
                }
            }
        }, "SysWhitelistRefresh");
        t.setDaemon(true);
        t.start();
    }

    /** 读取 sys.jsbd.apk_verify：优先反射 SystemProperties（零进程），失败才回退 getprop */
    private static boolean readApkVerifyProp() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            try {
                java.lang.reflect.Method m = sp.getMethod("get", String.class, String.class);
                String v = (String) m.invoke(null, "sys.jsbd.apk_verify", "");
                if (v != null && !v.trim().isEmpty()) {
                    return "1".equals(v.trim());
                }
            } catch (Throwable ignored) {
            }
            try {
                java.lang.reflect.Method m2 = sp.getMethod("get", String.class);
                String v2 = (String) m2.invoke(null, "sys.jsbd.apk_verify");
                if (v2 != null && !v2.trim().isEmpty()) {
                    return "1".equals(v2.trim());
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
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
            clearWhitelistCache();
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

    /**
     * 车型档位特征探测（专为缤越 COOL 2022 及亿咖通方案）：
     * 低配：热擎版（9.98万，标配360、小天窗、手动座椅、定速巡航、无电动尾门）
     * 中配：激擎版（10.98万，标配 L2 领航辅助、全景天窗+电动遮阳帘、电动尾门、主驾电动座椅、540°透明底盘）
     * 顶配：Battle版（11.98万，在中配基础上独占 APA 自动泊车、RPA 遥控泊车、循迹倒车、前6驻车雷达/12雷达、BSD盲区监测、原厂内置DVR行车记录仪）
     */
    public static String getVehicleTrimName(Context context) {
        if (context == null) return "";
        try {
            // 0. 支持用户手动覆盖或持久化偏好
            android.content.SharedPreferences sp = PrefUtils.getAppPreferences(context);
            if (sp != null) {
                String manualTrim = sp.getString("custom_vehicle_trim", "");
                if (manualTrim != null && !manualTrim.trim().isEmpty()) {
                    return manualTrim.trim();
                }
            }

            String model = getCleanDeviceModel().toUpperCase(java.util.Locale.US);
            boolean isBinyueCool = model.contains("SX11") || model.contains("COOL");
            if (!isBinyueCool) {
                return "";
            }

            // 如果车主未手动指定，且没有对应日志支撑，严禁凭空瞎猜配置
            // 顶栏徽章仅展示车型（如 缤越COOL、缤越L、帝豪L 等），由车主自行在卡片中选择具体版型
            return "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String detectVehicleTrim(Context context, File allLog) {
        // 0. 支持手动指定覆盖优先
        if (context != null) {
            android.content.SharedPreferences sp = PrefUtils.getAppPreferences(context);
            if (sp != null) {
                String manualTrim = sp.getString("custom_vehicle_trim", "");
                if (manualTrim != null && !manualTrim.trim().isEmpty()) {
                    return manualTrim.trim();
                }
            }
        }

        String baseTrim = getVehicleTrimName(context);
        String model = getCleanDeviceModel().toUpperCase(java.util.Locale.US);
        boolean isBinyueCool = model.contains("SX11") || model.contains("COOL");
        if (!isBinyueCool) {
            return baseTrim;
        }

        if (allLog != null && allLog.exists()) {
            // 若传入的是已打包的 ZIP 诊断归档，直接提取读取 summary.json 中已分析的权威结果
            if (allLog.getName().endsWith(".zip")) {
                try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(allLog)) {
                    java.util.zip.ZipEntry entry = zf.getEntry("summary.json");
                    if (entry != null) {
                        try (java.io.InputStream is = zf.getInputStream(entry);
                             java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"))) {
                            StringBuilder sb = new StringBuilder();
                            String l;
                            while ((l = br.readLine()) != null) sb.append(l);
                            org.json.JSONObject sj = new org.json.JSONObject(sb.toString());
                            String t = sj.optString("trim_name", "");
                            if (t != null && !t.trim().isEmpty()) {
                                return t.trim();
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            } else {
                // 顶配独占硬件特征：APA 自动泊车、循迹倒车、前泊车雷达或盲区监测 (BSD/DOW)
                boolean hasBattleExclusive = countMatches(allLog, "APA_") > 0 
                        || countMatches(allLog, "AutoParking") > 0
                        || countMatches(allLog, "RPA_") > 0
                        || countMatches(allLog, "TRACKING_REVERSE") > 0
                        || countMatches(allLog, "INFO_ID_BLIND_SPOT_MONITORING_SWITCH) return funValue(0x00000001)") > 0;
                if (hasBattleExclusive) {
                    return "Battle";
                }

                // 激擎与 Battle 共有的核心硬件特征：
                // 1. L2 级驾驶辅助（FCS 前向碰撞系统、ALAD 领航辅助、SLIF 交通标识识别）
                // 2. 电动后备箱 (POWERTAILGATE)
                // 3. 全景天窗电动遮阳帘 (INFO_ID_WINDOW_ROLLSHADE，必须剔除 notavailable 不支持状态)
                boolean hasL2Traffic = countMatches(allLog, "ADAS_FCS_ALAD") > 0 
                        || countMatches(allLog, "ADAS_FRS_ALOD") > 0 
                        || countMatches(allLog, "ALAD_SWITCH") > 0 
                        || countMatches(allLog, "INFO_ID_ADAS_FCS_ALAD") > 0;
                boolean hasTailgate = countAvailableMatches(allLog, "POWERTAILGATE") > 0;
                boolean hasRollshade = countAvailableMatches(allLog, "INFO_ID_WINDOW_ROLLSHADE") > 0;

                if (hasL2Traffic || hasTailgate || hasRollshade) {
                    return "激擎";
                }

                // 无 L2、无电动尾门、无电动遮阳帘时，判定为热擎
                return "热擎";
            }
        }

        if ("热擎".equals(baseTrim) || "热擎版".equals(baseTrim)) return "热擎";
        if ("Battle".equals(baseTrim) || "Battle版".equals(baseTrim)) return "Battle";
        return "激擎";
    }

    private static final java.util.Map<String, String> sMd5Cache = new java.util.concurrent.ConcurrentHashMap<>();

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

    public static boolean copyFile(File src, File dst) {
        if (src == null || dst == null || !src.exists()) return false;
        try (java.io.InputStream in = new java.io.FileInputStream(src);
             java.io.OutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[32 * 1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            return true;
        } catch (Exception e) {
            Log.e("SystemUtils", "copyFile failed: " + e.getMessage());
            return false;
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
    private static List<DetailedAppInfo> musicAppsCache = null;
    private static long musicAppsCacheTime = 0L;
    private static List<DetailedAppInfo> naviAppsCache = null;
    private static long naviAppsCacheTime = 0L;
    private static final long MUSIC_APPS_CACHE_TTL = 60_000L;

    public static void clearAppsCache() {
        appsCache = null;
        appsCacheTime = 0;
        disabledPkgsCache = null;
        disabledPkgsCacheAt = 0L;
        musicAppsCache = null;
        musicAppsCacheTime = 0L;
        naviAppsCache = null;
        naviAppsCacheTime = 0L;
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

    /** 动态探测整车已安装的音乐与音频软件 (严禁误判系统框架与非音频应用) */
    public static List<DetailedAppInfo> getInstalledMusicApps(Context context) {
        if (musicAppsCache != null && (System.currentTimeMillis() - musicAppsCacheTime < MUSIC_APPS_CACHE_TTL)) {
            return musicAppsCache;
        }
        List<DetailedAppInfo> musicApps = new ArrayList<>();
        List<DetailedAppInfo> all = getAllInstalledApps(context);
        PackageManager pm = context.getPackageManager();

        // 1. 动态探测系统车规媒体服务 (MediaBrowserService)
        Set<String> mediaBrowserPkgs = new HashSet<>();
        try {
            Intent browserIntent = new Intent("android.media.browse.MediaBrowserService");
            List<ResolveInfo> services = pm.queryIntentServices(browserIntent, 0);
            if (services != null) {
                for (ResolveInfo ri : services) {
                    if (ri.serviceInfo != null && ri.serviceInfo.packageName != null) {
                        mediaBrowserPkgs.add(ri.serviceInfo.packageName.toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. 动态探测物理按键与方控切歌广播 (MEDIA_BUTTON)
        Set<String> mediaButtonPkgs = new HashSet<>();
        try {
            Intent buttonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            List<ResolveInfo> receivers = pm.queryBroadcastReceivers(buttonIntent, 0);
            if (receivers != null) {
                for (ResolveInfo ri : receivers) {
                    if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                        mediaButtonPkgs.add(ri.activityInfo.packageName.toLowerCase(Locale.ROOT));
                    }
                }
            }
        } catch (Throwable ignored) {}

        String[] knownMusicPkgs = {
            "com.luna.music.car", "com.luna.music", "com.qishi.music",
            "com.tencent.qqmusiccar", "com.tencent.qqmusic",
            "com.netease.cloudmusic.car", "com.netease.cloudmusiccar", "com.netease.cloudmusic",
            "cn.kuwo.kwmusiccar", "cn.kuwo.player",
            "com.kugou.androidCar", "com.kugou.android", "com.kugou.player",
            "cn.toside.music.mobile", "com.ecarx.carmedia",
            "com.ximalaya.ting.android", "com.ximalaya.ting.androidcar",
            "com.edog.car", "remix.myplayer", "com.sds.android.ttpod",
            "com.kugou.viper"
        };
        for (DetailedAppInfo app : all) {
            if (!app.enabled) continue;
            String pkgLower = app.packageName.toLowerCase(Locale.ROOT).trim();
            // 核心安全防线：严禁将 Android 核心系统框架、设置、桌面、输入法误判为多媒体
            if ("android".equals(pkgLower) || pkgLower.startsWith("com.android.") ||
                pkgLower.startsWith("com.google.") || pkgLower.contains("launcher") ||
                pkgLower.contains("settings") || pkgLower.contains("inputmethod") ||
                pkgLower.contains("keyboard") || pkgLower.contains("systemui")) {
                continue;
            }

            // 过滤系统标记的游戏应用 (Android 8.0+ CATEGORY_GAME)
            try {
                ApplicationInfo ai = pm.getApplicationInfo(app.packageName, 0);
                if (ai != null && ai.category == ApplicationInfo.CATEGORY_GAME) {
                    continue;
                }
            } catch (Throwable ignored) {}

            boolean matched = false;

            // 准入规则 1: 注册了车规级 MediaBrowserService
            if (mediaBrowserPkgs.contains(pkgLower)) {
                matched = true;
            }

            // 准入规则 2: 知名音乐应用白名单
            if (!matched) {
                for (String kp : knownMusicPkgs) {
                    if (pkgLower.equals(kp) || pkgLower.startsWith(kp + ".")) {
                        matched = true;
                        break;
                    }
                }
            }

            // 准入规则 3: 注册了 MEDIA_BUTTON 物理切歌广播且具有明确音频特征
            if (!matched && mediaButtonPkgs.contains(pkgLower)) {
                String nameLower = app.appName.toLowerCase(Locale.ROOT).trim();
                if (nameLower.contains("音乐") || nameLower.contains("电台") || nameLower.contains("听书") ||
                    nameLower.contains("伴听") || nameLower.contains("洛雪") || nameLower.contains("网易云") ||
                    nameLower.contains("qq音乐") || nameLower.contains("汽水音乐") || nameLower.contains("酷狗") ||
                    nameLower.contains("酷我") || nameLower.contains("喜马拉雅") || nameLower.contains("player") ||
                    nameLower.contains("audio") || nameLower.contains("music")) {
                    matched = true;
                }
            }

            if (matched) {
                musicApps.add(app);
            }
        }
        musicAppsCache = musicApps;
        musicAppsCacheTime = System.currentTimeMillis();
        return musicApps;
    }

    /** 动态探测整车已安装的地图与导航软件 */
    public static List<DetailedAppInfo> getInstalledNaviApps(Context context) {
        if (naviAppsCache != null && (System.currentTimeMillis() - naviAppsCacheTime < MUSIC_APPS_CACHE_TTL)) {
            return naviAppsCache;
        }
        List<DetailedAppInfo> naviApps = new ArrayList<>();
        List<DetailedAppInfo> all = getAllInstalledApps(context);
        String[] knownNaviPkgs = {
            "com.autonavi.amapauto", "com.baidu.BaiduMap.auto", "com.tencent.map.auto",
            "com.autonavi.minimap", "com.baidu.BaiduMap", "com.tencent.map",
            "com.careland", "com.sogou.map.android.automobile"
        };
        for (DetailedAppInfo app : all) {
            if (!app.enabled) continue;
            boolean matched = false;
            String pkgLower = app.packageName.toLowerCase(Locale.ROOT);
            String nameLower = app.appName.toLowerCase(Locale.ROOT);
            for (String kp : knownNaviPkgs) {
                if (pkgLower.contains(kp) || kp.contains(pkgLower)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                if (nameLower.contains("地图") || nameLower.contains("导航") ||
                    nameLower.contains("map") || nameLower.contains("navi") || nameLower.contains("高德") || nameLower.contains("百度")) {
                    matched = true;
                }
            }
            if (matched) {
                naviApps.add(app);
            }
        }
        naviAppsCache = naviApps;
        naviAppsCacheTime = System.currentTimeMillis();
        return naviApps;
    }

    public interface OnUploadProgressListener {
        void onProgress(int percent, long uploadedBytes, long totalBytes, String statusText);
    }

    /**
     * 免密上传诊断日志到 Cloudflare 专属私有存储库（R2）：
     * 1. 文件流命名：芯片_车型_VIN后6位_时间戳_提取码.zip
     *   （提取码由云端统一下发，长度对 App 透明：云端改码长无需再动助手）
     * 2. 本地物理文件保持唯一不改名、不产生垃圾冗余
     * 3. 存储库 100% 纯私有，公网禁止直接访问，30天后全自动彻底销毁
     * 4. 云端准入：非车机设备（芯片代号非 IHU/ECARX 系）自动分流独立目录，不与车机日志混放
     */
    public static JSONObject uploadDiagnosticLog(Context context, String filePath) {
        return uploadDiagnosticLog(context, filePath, null, null);
    }

    public static JSONObject uploadDiagnosticLog(Context context, String filePath, OnUploadProgressListener listener) {
        return uploadDiagnosticLog(context, filePath, null, listener);
    }

    public static JSONObject uploadDiagnosticLog(Context context, String filePath, String customCode, OnUploadProgressListener listener) {
        JSONObject res = new JSONObject();
        if (filePath == null || filePath.trim().isEmpty()) {
            try {
                res.put("success", false);
                res.put("message", "未找到待上传的日志文件路径");
            } catch (Throwable ignored) {}
            return res;
        }
        File file = new File(filePath);
        if (!file.exists() || file.length() == 0) {
            try {
                res.put("success", false);
                res.put("message", "日志文件不存在或内容为空");
            } catch (Throwable ignored) {}
            return res;
        }

        String chip = getHardwareChip();
        String model = getCleanDeviceModel();
        String vin6 = getVinLast6(context);
        String trimName = detectVehicleTrim(context, file);
        String timestamp = new java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US).format(new java.util.Date());
        String safeChip = chip != null ? chip.replaceAll("[^a-zA-Z0-9_-]", "") : "UNKNOWN";
        String safeModel = model != null ? model.replaceAll("[^a-zA-Z0-9_-]", "") : "CAR";
        String safeVin = vin6 != null ? vin6.replaceAll("[^a-zA-Z0-9]", "") : "000000";

        // 优先采用前端本地预生成的6位识别码，确保客户端在上传前后全程统一
        String uploadCode = (customCode != null && customCode.trim().matches("^[0-9]{6}$"))
                ? customCode.trim()
                : String.format(java.util.Locale.US, "%06d", new java.util.Random().nextInt(900000) + 100000);

        boolean allBytesPushed = false;
        final long totalLen = file.length();

        try {
            if (listener != null) listener.onProgress(0, 0, totalLen, "正在建立云端安全链路...");

            java.net.URL url = new java.net.URL("https://log.onepve.com/upload");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            try {
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(25000);
                conn.setReadTimeout(45000);
                conn.setDoOutput(true);
                conn.setDoInput(true);
                conn.setUseCaches(false);

                // 核心：复刻 OkHttp RequestBody 的零内存流式直写机制，彻底杜绝内存溢出与进度倒跳
                conn.setFixedLengthStreamingMode(totalLen);

                conn.setRequestProperty("User-Agent", "GeelyToolbox/" + app.onepve.geelyconsole.BuildConfig.VERSION_NAME + " (Linux; U; Android " + android.os.Build.VERSION.RELEASE + ")");
                conn.setRequestProperty("Content-Type", "application/zip");
                conn.setRequestProperty("Connection", "close");
                conn.setRequestProperty("X-Hardware-Chip", safeChip);
                conn.setRequestProperty("X-Model-Code", safeModel);
                conn.setRequestProperty("X-Vin-Last6", safeVin);
                try {
                    conn.setRequestProperty("X-Vehicle-Trim", java.net.URLEncoder.encode(trimName != null ? trimName : "", "UTF-8"));
                } catch (Throwable ignored) {}
                conn.setRequestProperty("X-Timestamp", timestamp);
                conn.setRequestProperty("X-Upload-Code", uploadCode);

                // 零内存占用轻量流式写出（64KB 缓冲区）
                try (java.io.FileInputStream fis = new java.io.FileInputStream(file);
                     java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] buffer = new byte[64 * 1024];
                    long uploaded = 0;
                    int read;
                    long lastUpdateTime = System.currentTimeMillis();

                    while ((read = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                        uploaded += read;

                        long now = System.currentTimeMillis();
                        if (now - lastUpdateTime >= 250 || uploaded == totalLen) {
                            int pct = totalLen > 0 ? (int) (uploaded * 100 / totalLen) : 0;
                            if (listener != null) {
                                listener.onProgress(pct, uploaded, totalLen, "正在上传日志至云端 (" + pct + "%)...");
                            }
                            lastUpdateTime = now;
                        }
                    }
                    os.flush();
                    allBytesPushed = (uploaded == totalLen);
                }

                int code = conn.getResponseCode();
                java.io.InputStream respStream = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
                String bodyStr = "";
                if (respStream != null) {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(respStream, java.nio.charset.StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        bodyStr = sb.toString();
                    }
                }

                if (code == 200) {
                    if (listener != null) listener.onProgress(100, totalLen, totalLen, "上传成功");
                    JSONObject jsonResp = new JSONObject(bodyStr);
                    res.put("success", jsonResp.optBoolean("success", true));
                    res.put("code", jsonResp.optString("code", uploadCode));
                    res.put("vin6", jsonResp.optString("vin6", safeVin));
                    res.put("filename", jsonResp.optString("filename", ""));
                    res.put("message", jsonResp.optString("message", "上传成功"));
                    return res;
                } else {
                    res.put("success", false);
                    res.put("code", uploadCode);
                    res.put("vin6", safeVin);
                    res.put("message", "服务器响应错误 (" + code + "): " + bodyStr);
                    return res;
                }
            } finally {
                conn.disconnect();
            }
        } catch (Throwable t) {
            // 弱网自愈救赎：如果全部字节已推送完毕，仅因车载网络回执超时断流，轻量向 D1 验证一次是否已入库
            if (allBytesPushed) {
                boolean confirmedOnCloud = verifyLogOnCloud(safeVin, uploadCode);
                try {
                    res.put("success", true);
                    res.put("code", uploadCode);
                    res.put("vin6", safeVin);
                    res.put("isWeakNetworkConfirmed", true);
                    if (confirmedOnCloud) {
                        res.put("message", "上传成功（云端已双重确认归档）");
                    } else {
                        res.put("message", "日志数据已推送完毕（网络回执确认稍慢）");
                    }
                    if (listener != null) listener.onProgress(100, totalLen, totalLen, "日志已送达云端");
                    return res;
                } catch (Throwable ignored) {}
            }

            try {
                res.put("success", false);
                res.put("code", uploadCode);
                res.put("vin6", safeVin);
                if (t instanceof java.net.SocketTimeoutException) {
                    res.put("message", "网络连接超时，请检查车机网络信号后重试");
                } else if (t instanceof javax.net.ssl.SSLException) {
                    res.put("message", "车机安全连接握手受阻，请确认车机网络正常且系统时间正确: " + t.getMessage());
                } else {
                    res.put("message", "网络上传遇到异常: " + t.getMessage());
                }
            } catch (Throwable ignored) {}
        }
        return res;
    }

    /**
     * 向 Cloudflare D1 轻量 API 验证日志是否已入库（超时 4s，防阻塞）
     */
    private static boolean verifyLogOnCloud(String vin6, String code) {
        if (vin6 == null || code == null) return false;
        try {
            java.net.URL url = new java.net.URL("https://log.onepve.com/api/check?vin=" + vin6 + "&code=" + code);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("Connection", "close");
            if (conn.getResponseCode() == 200) {
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    JSONObject json = new JSONObject(sb.toString());
                    return json.optBoolean("exists", false);
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
