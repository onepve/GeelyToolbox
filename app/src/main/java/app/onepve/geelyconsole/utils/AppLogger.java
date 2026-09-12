package app.onepve.geelyconsole.utils;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 吉利智驾持久化运行日志系统
 * 1. 日志文件保存于 /sdcard/Download/geely_toolbox.log
 * 2. 单文件最大 2MB，超出自动滚动保留最多 3 个历史备份 (geely_toolbox.log.1, .2)
 * 3. 启动及定时清理超过 7 天的历史日志文件
 * 4. 记录每一步操作的具体时间、模块标签、操作是否成功及详细说明
 * 5. 支持在工具箱内快速查看近 500 行日志、实时刷新与一键清空
 */
public class AppLogger {

    private static final String TAG = "GeelyToolbox_Logger";
    private static final String LOG_FILENAME = "geely_toolbox.log";
    private static final long MAX_FILE_SIZE = 512 * 1024; // 512KB，防止日志界面读取卡死
    private static final int MAX_BACKUP_COUNT = 3;
    private static final long MAX_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000; // 7 天
    private static final long LOG_DEDUP_MILLIS = 1000; // 相同模块+消息 1s 内只写一次，抑制日志风暴

    private static final ExecutorService logExecutor = Executors.newSingleThreadExecutor();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA);
    private static final java.util.Map<String, Long> lastLogTime = new java.util.HashMap<>();

    private static android.content.Context appContext = null;
    private static final String PREFS_LOG_SWITCHES = "log_module_switches";
    // 聚焦座舱核心状态，高频/调试通道按需开启避免日志风暴
    private static final java.util.Map<String, Boolean> DEFAULT_MODULE_STATES = new java.util.HashMap<>();
    private static final java.util.List<String> KNOWN_MODULES = java.util.Arrays.asList(
            "电源状态", "挡位状态", "驾驶模式", "车门状态", "语音播报", "方控按键");
    static {
        // 日志按需采集哲学：默认全关，零 I/O 写入开销与零日志风暴。排查问题时车主按需开启对应模块。
        DEFAULT_MODULE_STATES.put("电源状态", false);
        DEFAULT_MODULE_STATES.put("挡位状态", false);
        DEFAULT_MODULE_STATES.put("驾驶模式", false);
        DEFAULT_MODULE_STATES.put("车门状态", false);
        DEFAULT_MODULE_STATES.put("语音播报", false);
        DEFAULT_MODULE_STATES.put("方控按键", false);
    }

    public static void init(android.content.Context ctx) {
        if (ctx != null) appContext = ctx.getApplicationContext();
    }

    public static boolean isModuleEnabled(String module) {
        if (appContext == null) return true; // 未初始化前保持兼容：默认允许写入
        android.content.SharedPreferences prefs = appContext.getSharedPreferences(PREFS_LOG_SWITCHES, android.content.Context.MODE_PRIVATE);
        Boolean def = DEFAULT_MODULE_STATES.get(module);
        return prefs.getBoolean(module, def == null || def);
    }

    public static void setModuleEnabled(String module, boolean enabled) {
        if (appContext == null) return;
        android.content.SharedPreferences prefs = appContext.getSharedPreferences(PREFS_LOG_SWITCHES, android.content.Context.MODE_PRIVATE);
        prefs.edit().putBoolean(module, enabled).apply();
    }

    public static String getAllModuleSwitchesJson() {
        try {
            org.json.JSONObject obj = new org.json.JSONObject();
            for (String m : KNOWN_MODULES) {
                obj.put(m, isModuleEnabled(m));
            }
            return obj.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public static File getLogFile() {
        File downloadDir = SystemUtils.getAppDownloadDir();
        return new File(downloadDir, LOG_FILENAME);
    }

    public static String getLogFilePath() {
        return getLogFile().getAbsolutePath();
    }

    public static void i(String module, String message) {
        writeLog("INFO", module, message, null);
    }

    public static void w(String module, String message) {
        writeLog("WARN", module, message, null);
    }

    public static void e(String module, String message) {
        writeLog("ERROR", module, message, null);
    }

    public static void e(String module, String message, Throwable tr) {
        writeLog("ERROR", module, message, tr);
    }

    /**
     * 核心操作打点记录
     * @param module 业务模块（如：卡兔子、白名单、应用冻结、ADB终端、系统重启等）
     * @param actionName 具体操作名（如：注入高德伪装包、放行白名单、冻结应用商店）
     * @param success 是否成功
     * @param details 详细参数与执行结果
     */
    public static void action(final String module, final String actionName, final boolean success, final String details) {
        String statusStr = success ? "成功 [PASS]" : "失败 [FAIL]";
        String fullMsg = actionName + " -> " + statusStr + (details != null && !details.isEmpty() ? " (" + details + ")" : "");
        writeLog(success ? "INFO" : "ERROR", module, fullMsg, null);
    }

    private static void writeLog(final String level, final String module, final String message, final Throwable tr) {
        final long time = System.currentTimeMillis();

        // 高频模块被用户关闭时：不再写文件，也不再输出 logcat（ERROR 始终保留）
        final boolean fileEnabled = "ERROR".equals(level) || isModuleEnabled(module);

        // 抑制日志风暴：同一模块+消息 1s 内只写一次（异常/ERROR 不受限）
        String dedupKey = level + "|" + module + "|" + message;
        synchronized (lastLogTime) {
            Long last = lastLogTime.get(dedupKey);
            if (last != null && (time - last) < LOG_DEDUP_MILLIS && !"ERROR".equals(level)) {
                return;
            }
            // 防去重表无限膨胀（含变量值的消息会不断产生新 key，长年运行会持续吃内存拖慢全机）
            if (lastLogTime.size() > 500) {
                lastLogTime.clear();
            }
            lastLogTime.put(dedupKey, time);
        }

        // 同时在系统 Logcat 输出（高频模块关闭时仅保留 ERROR；守护线程已过滤本应用标签，避免回环）
        String logcatMsg = "[" + module + "] " + message;
        if ("ERROR".equals(level)) {
            if (tr != null) Log.e(TAG, logcatMsg, tr);
            else Log.e(TAG, logcatMsg);
        } else if (fileEnabled) {
            if ("WARN".equals(level)) {
                Log.w(TAG, logcatMsg);
            } else {
                Log.i(TAG, logcatMsg);
            }
        }

        if (!fileEnabled) return;

        logExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    File logFile = getLogFile();
                    checkAndRotate(logFile);

                    String timeStr;
                    synchronized (dateFormat) {
                        timeStr = dateFormat.format(new Date(time));
                    }

                    StringBuilder sb = new StringBuilder(128);
                    sb.append('[').append(timeStr).append("] ")
                            .append('[').append(level).append("] ")
                            .append('[').append(module).append("] ")
                            .append(message).append('\n');

                    if (tr != null) {
                        StringWriter sw = new StringWriter();
                        PrintWriter pw = new PrintWriter(sw);
                        tr.printStackTrace(pw);
                        sb.append(sw.toString()).append('\n');
                    }

                    FileOutputStream fos = new FileOutputStream(logFile, true);
                    OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
                    osw.write(sb.toString());
                    osw.flush();
                    osw.close();
                    fos.close();
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static void checkAndRotate(File file) {
        if (!file.exists()) return;

        // 1. 检查大小滚动
        if (file.length() >= MAX_FILE_SIZE) {
            File dir = file.getParentFile();
            for (int i = MAX_BACKUP_COUNT - 1; i >= 1; i--) {
                File cur = new File(dir, LOG_FILENAME + "." + i);
                File next = new File(dir, LOG_FILENAME + "." + (i + 1));
                if (cur.exists()) {
                    if (next.exists()) next.delete();
                    cur.renameTo(next);
                }
            }
            File backup1 = new File(dir, LOG_FILENAME + ".1");
            if (backup1.exists()) backup1.delete();
            file.renameTo(backup1);
        }

        // 2. 清理超过 7 天的历史滚动日志
        try {
            File dir = file.getParentFile();
            if (dir != null && dir.exists()) {
                File[] list = dir.listFiles();
                if (list != null) {
                    long now = System.currentTimeMillis();
                    for (File f : list) {
                        String name = f.getName();
                        if (name.startsWith("geely_toolbox.log.") || name.startsWith("car_full.log")) {
                            if (now - f.lastModified() > MAX_RETENTION_MILLIS) {
                                f.delete();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 读取最近的 N 行日志（最多 500 行），供前端界面直观查看
     */
    public static String readRecentLogs(int maxLines) {
        File file = getLogFile();
        if (!file.exists() || file.length() == 0) {
            return "暂无运行日志记录。";
        }

        // 性能优化：先按字节从尾部读取一个窗口，避免每次读完整文件
        List<String> lines = new ArrayList<>();
        try {
            long fileLen = file.length();
            int windowSize = (int) Math.min(fileLen, maxLines * 256L); // 预估每行 256 字节
            long startPos = Math.max(0, fileLen - windowSize);

            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r");
            raf.seek(startPos);
            // 如果不在文件开头，丢弃第一行（可能不完整）
            if (startPos > 0) {
                raf.readLine();
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    new java.io.FileInputStream(raf.getFD()), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
                if (lines.size() > maxLines * 2) {
                    lines = new ArrayList<>(lines.subList(lines.size() - maxLines, lines.size()));
                }
            }
            br.close();
            raf.close();
        } catch (Exception e) {
            return "读取日志失败: " + e.getMessage();
        }

        int start = Math.max(0, lines.size() - maxLines);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < lines.size(); i++) {
            sb.append(lines.get(i)).append('\n');
        }
        return sb.toString();
    }

    /**
     * 清空当前日志文件
     */
    public static boolean clearLog() {
        try {
            File file = getLogFile();
            if (file.exists()) {
                file.delete();
            }
            File dir = file.getParentFile();
            if (dir != null && dir.exists()) {
                for (int i = 1; i <= MAX_BACKUP_COUNT; i++) {
                    File bk = new File(dir, LOG_FILENAME + "." + i);
                    if (bk.exists()) bk.delete();
                }
            }
            i("日志系统", "用户手动清空了历史运行日志");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取日志文件大小友好字符串（如 "128.5 KB"）
     */
    public static String getLogFileSizeStr() {
        File file = getLogFile();
        if (!file.exists()) return "0 KB";
        long len = file.length();
        if (len < 1024) return len + " B";
        if (len < 1024 * 1024) return String.format(Locale.CHINA, "%.1f KB", len / 1024.0);
        return String.format(Locale.CHINA, "%.2f MB", len / (1024.0 * 1024.0));
    }
}
