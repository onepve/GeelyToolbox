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
 * 吉利工具箱持久化运行日志系统
 * 1. 日志文件保存于 /sdcard/Download/geely_toolbox.log
 * 2. 单文件最大 2MB，超出自动滚动保留最多 3 个历史备份 (geely_toolbox.log.1, .2)
 * 3. 启动及定时清理超过 7 天的历史日志文件
 * 4. 记录每一步操作的具体时间、模块标签、操作是否成功及详细说明
 * 5. 支持在工具箱内快速查看近 500 行日志、实时刷新与一键清空
 */
public class AppLogger {

    private static final String TAG = "GeelyToolbox_Logger";
    private static final String LOG_FILENAME = "geely_toolbox.log";
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB
    private static final int MAX_BACKUP_COUNT = 3;
    private static final long MAX_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000; // 7 天

    private static final ExecutorService logExecutor = Executors.newSingleThreadExecutor();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA);

    public static File getLogFile() {
        File downloadDir = new File(Environment.getExternalStorageDirectory(), "Download");
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
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
        // 同时在系统 Logcat 输出
        String logcatMsg = "[" + module + "] " + message;
        if ("ERROR".equals(level)) {
            if (tr != null) Log.e(TAG, logcatMsg, tr);
            else Log.e(TAG, logcatMsg);
        } else if ("WARN".equals(level)) {
            Log.w(TAG, logcatMsg);
        } else {
            Log.i(TAG, logcatMsg);
        }

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

        List<String> lines = new ArrayList<>();
        try {
            FileInputStream fis = new FileInputStream(file);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
                if (lines.size() > maxLines * 2) {
                    lines = new ArrayList<>(lines.subList(lines.size() - maxLines, lines.size()));
                }
            }
            br.close();
            fis.close();
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
