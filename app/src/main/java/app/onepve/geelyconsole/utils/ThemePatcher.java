package app.onepve.geelyconsole.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ThemePatcher {

    private static final String DESCRIPTION_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<XUI-Theme>\n" +
            "    <title>兔子时钟</title>\n" +
            "    <providerName>吉利</providerName>\n" +
            "    <introduce>官方推荐</introduce>\n" +
            "    <price>0</price>\n" +
            "    <version>5</version>\n" +
            "    <uiVersion>1</uiVersion>\n" +
            "</XUI-Theme>";

    private static final String R2_PREVIEW_URL = "https://dl.onepve.com/GeelyToolbox/preview1.png";

    // 最小有效 1x1 占位 PNG (67 字节)，确保极端缺图时 zip 结构 100% 完备无异常
    private static final byte[] MINIMAL_PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
            (byte) 0x89, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x44, 0x41,
            0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
            0x42, 0x60, (byte) 0x82
    };

    public static void prepareThemeAssetsAsync(final Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    getOrExtractPreviewBytes(context);
                } catch (Exception ignored) {}
            }
        }).start();
    }

    public static byte[] getOrExtractPreviewBytes(Context context) {
        File pilotThemeDir = new File(Environment.getExternalStorageDirectory(), "GeelyPilot/theme");
        if (!pilotThemeDir.exists()) {
            pilotThemeDir.mkdirs();
        }
        File cachedPreview = new File(pilotThemeDir, "preview1.png");

        // 1. 优先读取持久缓存目录 /sdcard/GeelyPilot/theme/preview1.png
        if (cachedPreview.exists() && cachedPreview.length() > 1024) {
            try (FileInputStream fis = new FileInputStream(cachedPreview)) {
                byte[] data = new byte[(int) cachedPreview.length()];
                int read = fis.read(data);
                if (read == data.length) return data;
            } catch (Exception ignored) {}
        }

        // 2. 从车机原厂出厂主题包 /sdcard/XUI/theme/clock.rabbit.xtz 中动态提取 (当其为原厂包而非伪装包时)
        File themeDir = new File(Environment.getExternalStorageDirectory(), "XUI/theme");
        File officialXtz = new File(themeDir, "clock.rabbit.xtz");
        if (officialXtz.exists() && officialXtz.length() > 1024 && officialXtz.length() < 10 * 1024 * 1024) {
            byte[] extracted = extractPreviewFromZip(officialXtz, cachedPreview);
            if (extracted != null) return extracted;
        }

        // 3. 检索系统原厂预置固件目录 (/system/etc/theme/ 或 /system/media/theme/ 等)
        String[] sysPaths = {
            "/system/etc/theme/clock.rabbit.xtz",
            "/system/media/theme/clock.rabbit.xtz",
            "/product/etc/theme/clock.rabbit.xtz",
            "/vendor/etc/theme/clock.rabbit.xtz"
        };
        for (String sp : sysPaths) {
            File sysFile = new File(sp);
            if (sysFile.exists() && sysFile.length() > 1024 && sysFile.length() < 10 * 1024 * 1024) {
                byte[] extracted = extractPreviewFromZip(sysFile, cachedPreview);
                if (extracted != null) return extracted;
            }
        }

        // 4. 从 Cloudflare R2 CDN 静默下载（点击专家模式 20s 倒计时期间后台异步完成）
        try {
            java.net.URL url = new java.net.URL(R2_PREVIEW_URL);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(8000);
            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(cachedPreview)) {
                    byte[] buf = new byte[64 * 1024];
                    int r;
                    while ((r = is.read(buf)) != -1) {
                        fos.write(buf, 0, r);
                    }
                }
                if (cachedPreview.exists() && cachedPreview.length() > 1024) {
                    AppLogger.action("兔子主题", "从 R2 云端静默拉取官方壁纸完成", true, "大小: " + cachedPreview.length() + " 字节");
                    try (FileInputStream fis = new FileInputStream(cachedPreview)) {
                        byte[] data = new byte[(int) cachedPreview.length()];
                        int read = fis.read(data);
                        if (read == data.length) return data;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            AppLogger.action("兔子主题", "R2 预览壁纸下载跳过或异常", false, e.getMessage());
        }

        // 5. 兜底最小有效 1x1 PNG，确保时钟包结构 100% 完备
        return MINIMAL_PNG_BYTES;
    }

    private static byte[] extractPreviewFromZip(File zipFile, File destFile) {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zipFile)) {
            ZipEntry entry = zf.getEntry("preview/preview1.png");
            if (entry != null) {
                try (InputStream is = zf.getInputStream(entry);
                     FileOutputStream fos = new FileOutputStream(destFile)) {
                    byte[] buf = new byte[64 * 1024];
                    int r;
                    while ((r = is.read(buf)) != -1) {
                        fos.write(buf, 0, r);
                    }
                }
                if (destFile.exists() && destFile.length() > 0) {
                    AppLogger.action("兔子主题", "成功从原车出厂主题提取 preview1.png 并持久化", true, "来源: " + zipFile.getAbsolutePath());
                    try (FileInputStream fis = new FileInputStream(destFile)) {
                        byte[] data = new byte[(int) destFile.length()];
                        int read = fis.read(data);
                        if (read == data.length) return data;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static boolean packageToRabbitTheme(File sourceApk) {
        return packageToRabbitTheme(null, sourceApk);
    }

    public static boolean packageToRabbitTheme(Context context, File sourceApk) {
        if (sourceApk == null || !sourceApk.exists()) {
            return false;
        }

        // 1. Ensure placeholder lock is applied first
        SystemUtils.applyPlaceholderLock();

        File themeDir = new File(Environment.getExternalStorageDirectory(), "XUI/theme");
        if (!themeDir.exists()) {
            themeDir.mkdirs();
        }

        File targetXtz = new File(themeDir, "clock.rabbit.xtz");
        File tempXtz = new File(themeDir, "clock.rabbit.xtz.tmp");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempXtz))) {
            // 1. description.xml
            ZipEntry descEntry = new ZipEntry("description.xml");
            zos.putNextEntry(descEntry);
            zos.write(DESCRIPTION_XML.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 2. preview/ directory and preview1.png
            ZipEntry previewDir = new ZipEntry("preview/");
            zos.putNextEntry(previewDir);
            zos.closeEntry();

            byte[] previewBytes = getOrExtractPreviewBytes(context);
            if (previewBytes != null && previewBytes.length > 0) {
                ZipEntry previewFileEntry = new ZipEntry("preview/preview1.png");
                zos.putNextEntry(previewFileEntry);
                zos.write(previewBytes);
                zos.closeEntry();
            }

            // 3. wallpaper directories
            ZipEntry wpDir1 = new ZipEntry("wallpaper/");
            zos.putNextEntry(wpDir1);
            zos.closeEntry();
            ZipEntry wpDir2 = new ZipEntry("wallpaper/csd/");
            zos.putNextEntry(wpDir2);
            zos.closeEntry();
            ZipEntry wpDir3 = new ZipEntry("wallpaper/psd/");
            zos.putNextEntry(wpDir3);
            zos.closeEntry();

            // 4. xcscreensaver.apk (the disguised target APK)
            ZipEntry apkEntry = new ZipEntry("xcscreensaver.apk");
            zos.putNextEntry(apkEntry);
            byte[] buf = new byte[64 * 1024];
            int read;
            try (FileInputStream fis = new FileInputStream(sourceApk)) {
                while ((read = fis.read(buf)) != -1) {
                    zos.write(buf, 0, read);
                }
            }
            zos.closeEntry();

            zos.finish();

            if (targetXtz.exists()) {
                targetXtz.delete();
            }
            boolean renamed = tempXtz.renameTo(targetXtz);

            // Re-assert placeholder lock
            SystemUtils.applyPlaceholderLock();

            if (renamed && context != null) {
                try {
                    android.content.SharedPreferences prefs = context.getSharedPreferences("rabbit_theme_prefs", Context.MODE_PRIVATE);
                    prefs.edit()
                            .putLong("last_injected_time", System.currentTimeMillis())
                            .putString("last_injected_filename", sourceApk.getName())
                            .putString("last_injected_path", sourceApk.getAbsolutePath())
                            .putBoolean("pending_install_after_reboot", true)
                            .apply();
                } catch (Exception ignored) {
                }
            }

            if (renamed) {
                AppLogger.action("兔子主题", "注入伪装包", true, "文件: " + sourceApk.getName() + ", 大小: " + String.format(java.util.Locale.CHINA, "%.1f MB", targetXtz.length() / (1024.0 * 1024.0)));
            } else {
                AppLogger.action("兔子主题", "注入伪装包", false, "临时文件重命名失败");
            }

            return renamed;
        } catch (Exception e) {
            AppLogger.action("兔子主题", "注入伪装包", false, "异常: " + e.getMessage());
            if (tempXtz.exists()) {
                tempXtz.delete();
            }
            return false;
        }
    }

    public static boolean openRabbitThemeSetting(Context context) {
        if (context == null) return false;

        // 1. 首选：直接启动吉利【主题】主界面（包含我的主题、我的壁纸、屏保时钟）
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(new ComponentName("com.ecarx.thememanager", "com.ecarx.thememanager.main.MainActivity"));
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(intent);
            AppLogger.action("车机主题", "拉起原厂主题中心", true, "Component: com.ecarx.thememanager");
            return true;
        } catch (Exception ignored) {
        }

        // 2. 次选：通过 PackageManager 获取主题应用启动入口
        try {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage("com.ecarx.thememanager");
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launch);
                return true;
            }
        } catch (Exception ignored) {
        }

        // 3. 备选：通过原厂 Action 调起主题
        try {
            Intent intent = new Intent("com.ecarx.intent.action.theme");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception ignored) {
        }

        // 4. 备选：通过本地 ADB 特权指令启动
        try {
            AdbClient.AdbResult res = AdbClient.execute(context, "am start -n com.ecarx.thememanager/com.ecarx.thememanager.main.MainActivity");
            if (res != null && res.success) {
                return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    public static JSONObject checkRabbitPostRebootStatus(Context context) {
        JSONObject obj = new JSONObject();
        if (context == null) return obj;
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("rabbit_theme_prefs", Context.MODE_PRIVATE);
            boolean pending = prefs.getBoolean("pending_install_after_reboot", false);

            if (pending) {
                // 1. Auto-enable whitelist if not enabled
                boolean isWhitelistEnabled = SystemUtils.isApkVerifyWhitelistEnabled();
                boolean whitelistAutoEnabled = false;
                if (!isWhitelistEnabled) {
                    SystemUtils.enableApkVerifyWhitelist(context);
                    whitelistAutoEnabled = true;
                }

                // 2. Check if app store is frozen/disabled
                boolean appstoreDisabled = (SystemUtils.getAppDetailedState(context, "com.ecarx.appstore") == SystemUtils.APP_STATE_DISABLED);

                obj.put("hasPendingReboot", true);
                obj.put("disguiseCleaned", true);
                obj.put("whitelistAutoEnabled", whitelistAutoEnabled);
                obj.put("appstoreDisabled", appstoreDisabled);
                obj.put("filename", prefs.getString("last_injected_filename", "高德地图.apk"));
                obj.put("injectedTime", prefs.getLong("last_injected_time", 0));
                return obj;
            } else {
                obj.put("hasPendingReboot", false);
            }
        } catch (Exception e) {
            try {
                obj.put("hasPendingReboot", false);
            } catch (Exception ignored) {}
        }
        return obj;
    }

    public static JSONObject restoreOfficialRabbitTheme(Context context) {
        JSONObject res = new JSONObject();
        long freedBytes = 0;
        try {
            File themeDir = new File(Environment.getExternalStorageDirectory(), "XUI/theme");
            File targetXtz = new File(themeDir, "clock.rabbit.xtz");
            if (targetXtz.exists()) {
                freedBytes += targetXtz.length();
                targetXtz.delete();
            }
            if (themeDir.exists() && themeDir.isDirectory()) {
                File[] files = themeDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String name = f.getName();
                        if (name.startsWith("clock.rabbit.") && (name.endsWith(".tmp") || name.endsWith(".bak"))) {
                            freedBytes += f.length();
                            f.delete();
                        }
                    }
                }
            }
            SystemUtils.executeShell("rm -f '/sdcard/XUI/theme/clock.rabbit.xtz'");
            boolean lockRemoved = SystemUtils.removePlaceholderLock();

            if (context != null) {
                android.content.SharedPreferences prefs = context.getSharedPreferences("rabbit_theme_prefs", Context.MODE_PRIVATE);
                prefs.edit()
                        .putBoolean("pending_install_after_reboot", false)
                        .remove("last_injected_time")
                        .remove("last_injected_filename")
                        .remove("last_injected_path")
                        .apply();
            }

            res.put("success", true);
            res.put("freedBytes", freedBytes);
            res.put("freedMb", String.format(java.util.Locale.CHINA, "%.1f", freedBytes / (1024.0 * 1024.0)));
            res.put("lockRemoved", lockRemoved);
            res.put("message", "已清除伪装包与防覆写锁，恢复官方原版时钟，释放 " + String.format(java.util.Locale.CHINA, "%.1f MB", freedBytes / (1024.0 * 1024.0)));
            AppLogger.action("兔子主题", "还原官方原版时钟", true, "释放: " + String.format(java.util.Locale.CHINA, "%.1f MB", freedBytes / (1024.0 * 1024.0)) + ", 锁已解除: " + lockRemoved);
        } catch (Exception e) {
            try {
                res.put("success", false);
                res.put("message", "还原失败: " + e.getMessage());
            } catch (Exception ignored) {}
            AppLogger.action("兔子主题", "还原官方原版时钟", false, "异常: " + e.getMessage());
        }
        return res;
    }

    public static JSONObject restoreDisguiseAndLockSilently(Context context) {
        JSONObject res = new JSONObject();
        long freedBytes = 0;
        try {
            File themeDir = new File(Environment.getExternalStorageDirectory(), "XUI/theme");
            File targetXtz = new File(themeDir, "clock.rabbit.xtz");
            if (targetXtz.exists()) {
                freedBytes += targetXtz.length();
                targetXtz.delete();
            }
            if (themeDir.exists() && themeDir.isDirectory()) {
                File[] files = themeDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String name = f.getName();
                        if (name.startsWith("clock.rabbit.") && (name.endsWith(".tmp") || name.endsWith(".bak"))) {
                            freedBytes += f.length();
                            f.delete();
                        }
                    }
                }
            }
            SystemUtils.executeShell("rm -f '/sdcard/XUI/theme/clock.rabbit.xtz'");
            boolean lockRemoved = SystemUtils.removePlaceholderLock();

            res.put("success", true);
            res.put("freedBytes", freedBytes);
            res.put("lockRemoved", lockRemoved);
            if (freedBytes > 0 || lockRemoved) {
                AppLogger.action("开机自愈", "静默清除伪装与锁文件", true, "释放空间: " + String.format(java.util.Locale.CHINA, "%.1f MB", freedBytes / (1024.0 * 1024.0)) + ", 锁已解除: " + lockRemoved);
            }
        } catch (Exception e) {
            try {
                res.put("success", false);
            } catch (Exception ignored) {}
        }
        return res;
    }

    public static JSONObject getRabbitDisguiseInfo(Context context) {
        JSONObject obj = new JSONObject();
        try {
            File themeDir = new File(Environment.getExternalStorageDirectory(), "XUI/theme");
            File targetXtz = new File(themeDir, "clock.rabbit.xtz");
            boolean lockActive = SystemUtils.isPlaceholderLockActive();
            obj.put("lockActive", lockActive);

            boolean hasDisguise = targetXtz.exists() && targetXtz.length() > 0;
            if (hasDisguise) {
                obj.put("exists", true);
                obj.put("sizeBytes", targetXtz.length());
                obj.put("sizeStr", String.format(java.util.Locale.CHINA, "%.1f MB", targetXtz.length() / (1024.0 * 1024.0)));
                long lastMod = targetXtz.lastModified();
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA);
                obj.put("timeStr", sdf.format(new java.util.Date(lastMod)));
                obj.put("disguisedPkg", "com.autonavi.amapauto");
            } else if (lockActive) {
                obj.put("exists", true);
                obj.put("sizeBytes", 0);
                obj.put("sizeStr", "0 MB (锁残留)");
                obj.put("timeStr", "防覆写锁存在");
                obj.put("disguisedPkg", "防覆写锁激活");
            } else {
                obj.put("exists", false);
                obj.put("sizeStr", "0 MB");
                obj.put("timeStr", "未伪装");
                obj.put("disguisedPkg", "官方原生屏保");
            }
        } catch (Exception e) {
            try {
                obj.put("exists", false);
                obj.put("timeStr", "未伪装");
            } catch (Exception ignored) {}
        }
        return obj;
    }
}
