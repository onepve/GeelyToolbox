package app.onepve.geelyconsole.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import app.onepve.geelyconsole.MainActivity;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * 兔子时钟半自动向导管理器 (Co-Pilot HUD)
 * 负责后台打包伪装注入、前台半自动悬浮向导挂起与重启车机触发。
 * 全程 100% 纯文字与 0 Emoji，支持无死锁常驻悬浮向导。
 */
public class AutoPilotManager {

    private static final String TAG = "AutoPilotManager";
    private static AutoPilotManager instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View hudOverlayView;

    private Thread workerThread;
    private Runnable delayedCommitRunnable;
    private Runnable countdownRunnable;
    private volatile boolean isRunning = false;
    private volatile boolean isCancelled = false;

    public interface AutoPilotCallback {
        void onSuccess(String apkName);
        void onError(String error);
    }

    public static synchronized AutoPilotManager getInstance() {
        if (instance == null) {
            instance = new AutoPilotManager();
        }
        return instance;
    }

    private AutoPilotManager() {}

    public boolean isRunning() {
        return isRunning;
    }

    public static void ensureOverlayPermission(Context context) {
        if (context == null) return;
        // 遵循系统权限规范：悬浮窗权限由车主通过系统设置界面授权，不再后台静默调用 ADB
    }

    public synchronized void startAutoInject(final Context context, final File targetApk) {
        startAutoInject(context, targetApk, null);
    }

    public synchronized void startAutoInject(final Context context, final File targetApk, final AutoPilotCallback callback) {
        if (isRunning) {
            Toast.makeText(context, "半自动向导已在运行中...", Toast.LENGTH_SHORT).show();
            return;
        }

        if (targetApk == null || !targetApk.exists() || targetApk.length() == 0) {
            Toast.makeText(context, "未找到有效的 APK 安装包，请先下载！", Toast.LENGTH_SHORT).show();
            if (callback != null) callback.onError("未找到有效的 APK 安装包");
            return;
        }

        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            boolean currentPref = prefs.getBoolean("autostart_enabled", false);
            prefs.edit()
                    .putBoolean("saved_user_autostart_pref", currentPref)
                    .putBoolean("temp_autostart_for_rabbit", true)
                    .apply();
        } catch (Exception ignored) {}

        isRunning = true;
        isCancelled = false;

        // 确保悬浮窗权限
        ensureOverlayPermission(context);

        // 启动后台线程执行注入与向导挂起
        workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                executeSemiAutoPipeline(context, targetApk, callback);
            }
        });
        workerThread.start();
    }

    /**
     * 供 MainActivity 的进度弹窗按钮调起：
     * 临时主题包已由 MainActivity 提前打包好（clock.rabbit.xtz.tmp），
     * 此方法只负责：调起主题中心 + 30秒后提交替换。
     */
    public synchronized void scheduleThemeCenterAndCommit(final Context context) {
        if (isRunning) {
            Toast.makeText(context, "半自动向导已在运行中...", Toast.LENGTH_SHORT).show();
            return;
        }
        isRunning = true;
        isCancelled = false;
        ensureOverlayPermission(context);

        final File stagedXtz = new File(
            android.os.Environment.getExternalStorageDirectory(), "XUI/theme/clock.rabbit.xtz.tmp");
        if (!stagedXtz.exists() || stagedXtz.length() < 10 * 1024) {
            isRunning = false;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, "临时主题包不存在，请重新操作！", Toast.LENGTH_LONG).show();
                }
            });
            return;
        }

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isCancelled) { isRunning = false; return; }
                boolean opened = ThemePatcher.openRabbitThemeSetting(context);
                if (!opened) {
                    Toast.makeText(context, "【向导已启动】请在 30 秒内打开主题中心，进入【屏保时钟 ➔ 兔子时钟】等待...", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(context, "【向导已启动】请在 30 秒内进入【屏保时钟 ➔ 兔子时钟】等待...", Toast.LENGTH_LONG).show();
                }

                delayedCommitRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (isCancelled) { isRunning = false; return; }
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                executeDelayedCommit(context, stagedXtz, null);
                            }
                        }).start();
                    }
                };
                mainHandler.postDelayed(delayedCommitRunnable, 30000);
            }
        });
    }

    public synchronized void cancel(Context context) {
        isCancelled = true;
        isRunning = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
        if (delayedCommitRunnable != null) {
            mainHandler.removeCallbacks(delayedCommitRunnable);
            delayedCommitRunnable = null;
        }
        if (countdownRunnable != null) {
            mainHandler.removeCallbacks(countdownRunnable);
            countdownRunnable = null;
        }
        hideHudOverlay();
        if (context != null) {
            try {
                android.content.SharedPreferences prefs = context.getSharedPreferences("rabbit_theme_prefs", Context.MODE_PRIVATE);
                prefs.edit().putBoolean("pending_install_after_reboot", false).apply();
            } catch (Exception ignored) {}

            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, "已退出半自动安装向导", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void executeSemiAutoPipeline(final Context context, final File targetApk, final AutoPilotCallback callback) {
        try {
            // Step 1: 先在后台准备打包临时伪装文件，不立即覆盖目标，避开原厂主题管理器启动自愈覆写
            final File stagedXtz = ThemePatcher.buildRabbitThemeToTemp(context, targetApk);
            if (stagedXtz == null || !stagedXtz.exists()) {
                isRunning = false;
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, "兔子时钟打包写入失败，请检查机身存储权限！", Toast.LENGTH_LONG).show();
                        if (callback != null) callback.onError("兔子时钟打包写入失败");
                    }
                });
                return;
            }

            if (isCancelled) {
                if (stagedXtz.exists()) stagedXtz.delete();
                isRunning = false;
                return;
            }

            // Step 2: 调起原厂主题中心，并给予车主 20 秒进入兔子时钟详情页的时间
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (isCancelled) {
                        if (stagedXtz.exists()) stagedXtz.delete();
                        isRunning = false;
                        return;
                    }

                    boolean opened = ThemePatcher.openRabbitThemeSetting(context);
                    if (!opened) {
                        Toast.makeText(context, "【向导已启动】请在 30 秒内打开主题中心，进入【屏保时钟 ➔ 兔子时钟】等待...", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(context, "【向导已启动】已打开主题中心，请在 30 秒内点击【屏保时钟】进入【兔子时钟】等待...", Toast.LENGTH_LONG).show();
                    }

                    // 启动 30 秒延迟覆盖定时器
                    delayedCommitRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (isCancelled) {
                                if (stagedXtz.exists()) stagedXtz.delete();
                                isRunning = false;
                                return;
                            }

                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    final boolean ok = ThemePatcher.commitStagedRabbitTheme(context, targetApk, stagedXtz);
                                    isRunning = false;
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (ok) {
                                                Toast.makeText(context, "【注入就绪】兔子主题已成功覆盖！请立即点击屏幕下方的【应用】，应用后返回助手重启车机！", Toast.LENGTH_LONG).show();
                                                if (callback != null) {
                                                    callback.onSuccess(targetApk.getName());
                                                }
                                            } else {
                                                Toast.makeText(context, "兔子主题覆盖写入失败，请检查机身存储权限！", Toast.LENGTH_LONG).show();
                                                if (callback != null) {
                                                    callback.onError("覆盖失败");
                                                }
                                            }
                                        }
                                    });
                                }
                            }).start();
                        }
                    };
                    mainHandler.postDelayed(delayedCommitRunnable, 30000);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "SemiAuto pipeline error: " + e.getMessage(), e);
            isRunning = false;
            final String errMsg = e.getMessage();
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, "向导流程异常: " + errMsg, Toast.LENGTH_LONG).show();
                    if (callback != null) callback.onError("流程异常: " + errMsg);
                }
            });
        }
    }

    /**
     * 展现座舱超大号全景 HUD 悬浮提示横条
     * 100% 纯文字与 0 Emoji，右侧常驻大号实体重启按键
     */
    @SuppressLint("RtlHardcoded")
    private void showHudOverlay(final Context context, final String apkName) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                    if (windowManager == null) return;

                    if (hudOverlayView != null) {
                        try {
                            windowManager.removeView(hudOverlayView);
                        } catch (Exception ignored) {}
                    }

                    int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE;

                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            layoutType,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                            PixelFormat.TRANSLUCENT
                    );
                    params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                    params.y = dp2px(context, 12);

                    // Build HUGE HUD Root Layout
                    LinearLayout root = new LinearLayout(context);
                    root.setOrientation(LinearLayout.HORIZONTAL);
                    root.setGravity(Gravity.CENTER_VERTICAL);
                    root.setPadding(dp2px(context, 24), dp2px(context, 14), dp2px(context, 24), dp2px(context, 14));

                    // Background: Dark Slate with Teal/Cyan Border
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(Color.parseColor("#F50B1120"));
                    bg.setCornerRadius(dp2px(context, 16));
                    bg.setStroke(dp2px(context, 3), Color.parseColor("#00AEB9"));
                    root.setBackground(bg);

                    // Left Tag: 純文字 [向导] 胶囊
                    TextView tvBadge = new TextView(context);
                    tvBadge.setText("向导");
                    tvBadge.setTextSize(16);
                    tvBadge.setTextColor(Color.parseColor("#00AEB9"));
                    tvBadge.setTypeface(Typeface.DEFAULT_BOLD);
                    tvBadge.setPadding(dp2px(context, 12), dp2px(context, 6), dp2px(context, 12), dp2px(context, 6));
                    GradientDrawable badgeBg = new GradientDrawable();
                    badgeBg.setColor(Color.parseColor("#1A00AEB9"));
                    badgeBg.setCornerRadius(dp2px(context, 8));
                    badgeBg.setStroke(dp2px(context, 1.5f), Color.parseColor("#00AEB9"));
                    tvBadge.setBackground(badgeBg);
                    root.addView(tvBadge);

                    // Middle Text Column (Huge, Bold, High Contrast)
                    LinearLayout textBox = new LinearLayout(context);
                    textBox.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                    textLp.setMargins(dp2px(context, 18), 0, dp2px(context, 16), 0);
                    textBox.setLayoutParams(textLp);

                    TextView tvTitle = new TextView(context);
                    tvTitle.setText("步骤 1/2: 请在下方点击【屏保时钟】-> 选【兔子时钟】并点击【应用】");
                    tvTitle.setTextSize(22);
                    tvTitle.setTextColor(Color.WHITE);
                    tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
                    textBox.addView(tvTitle);

                    TextView tvDesc = new TextView(context);
                    tvDesc.setText("说明：车机提示“应用成功”或“应用失败”均已生效！应用后请点击右侧【立即重启】");
                    tvDesc.setTextSize(15);
                    tvDesc.setTextColor(Color.parseColor("#38BDF8"));
                    tvDesc.setTypeface(Typeface.DEFAULT_BOLD);
                    tvDesc.setPadding(0, dp2px(context, 4), 0, 0);
                    textBox.addView(tvDesc);

                    root.addView(textBox);

                    // Right Action Buttons Box
                    LinearLayout btnBox = new LinearLayout(context);
                    btnBox.setOrientation(LinearLayout.HORIZONTAL);
                    btnBox.setGravity(Gravity.CENTER_VERTICAL);

                    // 1. Soft Reboot (Primary Action Button)
                    Button btnSoft = new Button(context);
                    btnSoft.setText("我已应用，立即重启车机");
                    btnSoft.setTextSize(17);
                    btnSoft.setTextColor(Color.WHITE);
                    btnSoft.setTypeface(Typeface.DEFAULT_BOLD);
                    btnSoft.setPadding(dp2px(context, 20), dp2px(context, 6), dp2px(context, 20), dp2px(context, 6));
                    GradientDrawable softBg = new GradientDrawable();
                    softBg.setColor(Color.parseColor("#0284C7"));
                    softBg.setCornerRadius(dp2px(context, 10));
                    softBg.setStroke(dp2px(context, 2), Color.parseColor("#38BDF8"));
                    btnSoft.setBackground(softBg);
                    btnSoft.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            hideHudOverlay();
                            isRunning = false;
                            Toast.makeText(context, "正在执行一键重启车机...", Toast.LENGTH_SHORT).show();
                            SystemUtils.executePrivileged(context, "reboot || svc power reboot");
                        }
                    });
                    LinearLayout.LayoutParams softLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp2px(context, 56));
                    softLp.setMargins(0, 0, dp2px(context, 10), 0);
                    btnSoft.setLayoutParams(softLp);
                    btnBox.addView(btnSoft);

                    // 2. Hard Reboot (Cold Restart Button)
                    Button btnHard = new Button(context);
                    btnHard.setText("完整硬重启");
                    btnHard.setTextSize(15);
                    btnHard.setTextColor(Color.parseColor("#CBD5E1"));
                    btnHard.setTypeface(Typeface.DEFAULT_BOLD);
                    btnHard.setPadding(dp2px(context, 14), dp2px(context, 6), dp2px(context, 14), dp2px(context, 6));
                    GradientDrawable hardBg = new GradientDrawable();
                    hardBg.setColor(Color.parseColor("#1E293B"));
                    hardBg.setCornerRadius(dp2px(context, 10));
                    hardBg.setStroke(dp2px(context, 1.5f), Color.parseColor("#475569"));
                    btnHard.setBackground(hardBg);
                    btnHard.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            hideHudOverlay();
                            isRunning = false;
                            Toast.makeText(context, "正在执行车机硬件冷重启...", Toast.LENGTH_SHORT).show();
                            SystemUtils.executePrivileged(context, "reboot || svc power reboot");
                        }
                    });
                    LinearLayout.LayoutParams hardLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp2px(context, 56));
                    hardLp.setMargins(0, 0, dp2px(context, 10), 0);
                    btnHard.setLayoutParams(hardLp);
                    btnBox.addView(btnHard);

                    // 3. Cancel Button
                    Button btnCancel = new Button(context);
                    btnCancel.setText("退出向导");
                    btnCancel.setTextSize(15);
                    btnCancel.setTextColor(Color.WHITE);
                    btnCancel.setTypeface(Typeface.DEFAULT_BOLD);
                    btnCancel.setPadding(dp2px(context, 14), dp2px(context, 6), dp2px(context, 14), dp2px(context, 6));
                    GradientDrawable cancelBg = new GradientDrawable();
                    cancelBg.setColor(Color.parseColor("#DC2626"));
                    cancelBg.setCornerRadius(dp2px(context, 10));
                    cancelBg.setStroke(dp2px(context, 1.5f), Color.parseColor("#EF4444"));
                    btnCancel.setBackground(cancelBg);
                    btnCancel.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            cancel(context);
                        }
                    });
                    LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp2px(context, 56));
                    btnCancel.setLayoutParams(cancelLp);
                    btnBox.addView(btnCancel);

                    root.addView(btnBox);
                    hudOverlayView = root;

                    // Fallback for Window Types
                    int[] types;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        types = new int[]{
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                                WindowManager.LayoutParams.TYPE_PHONE
                        };
                    } else {
                        types = new int[]{
                                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                                WindowManager.LayoutParams.TYPE_PHONE
                        };
                    }

                    boolean added = false;
                    for (int type : types) {
                        params.type = type;
                        try {
                            windowManager.addView(hudOverlayView, params);
                            added = true;
                            break;
                        } catch (Exception e) {
                            Log.w(TAG, "HUD addView failed with type " + type + ": " + e.getMessage());
                        }
                    }

                    if (!added) {
                        Log.e(TAG, "All window types failed to add HUD!");
                    }

                } catch (Exception e) {
                    Log.e(TAG, "showHudOverlay error: " + e.getMessage());
                }
            }
        });
    }

    public void hideHudOverlay() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (windowManager != null && hudOverlayView != null) {
                        windowManager.removeView(hudOverlayView);
                        hudOverlayView = null;
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    /**
     * 全盘广度扫描车机 APK 安装包：涵盖 Download 目录、子目录、机身根目录及外接 U 盘，不做应用类型过滤
     */
    public static List<File> scanDownloadedApks(Context context) {
        List<File> list = new ArrayList<>();
        try {
            List<File> searchDirs = new ArrayList<>();
            File sdcard = Environment.getExternalStorageDirectory();
            if (sdcard != null && sdcard.exists()) {
                searchDirs.add(sdcard); // 机身根目录 (/sdcard)
                searchDirs.add(new File(sdcard, "Download"));
                searchDirs.add(new File(sdcard, "Downloads"));
                searchDirs.add(new File(sdcard, "Download/00_车机应用"));
                searchDirs.add(new File(sdcard, "Download/!车机应用"));
                searchDirs.add(new File(sdcard, "Download/browser"));
                searchDirs.add(new File(sdcard, "Download/Browser"));
                searchDirs.add(new File(sdcard, "browser/download"));
                searchDirs.add(new File(sdcard, "Browser/Download"));
                searchDirs.add(new File(sdcard, "UCDownloads"));
            }

            File appDl = SystemUtils.getAppDownloadDir();
            if (appDl != null && !searchDirs.contains(appDl)) {
                searchDirs.add(appDl);
            }

            if (context != null) {
                try {
                    File extFiles = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (extFiles != null && extFiles.exists() && !searchDirs.contains(extFiles)) {
                        searchDirs.add(extFiles);
                    }
                } catch (Exception ignored) {}
            }

            // 扫描外置 U 盘路径 (/storage/xxx, /mnt/media_rw/xxx)
            try {
                File storageRoot = new File("/storage");
                if (storageRoot.exists() && storageRoot.isDirectory()) {
                    File[] mounts = storageRoot.listFiles();
                    if (mounts != null) {
                        for (File m : mounts) {
                            if (m.isDirectory() && !m.getName().equalsIgnoreCase("emulated") && !m.getName().equalsIgnoreCase("self")) {
                                searchDirs.add(m);
                                File uDl = new File(m, "Download");
                                if (uDl.exists()) searchDirs.add(uDl);
                            }
                        }
                    }
                }
                File mntRoot = new File("/mnt/media_rw");
                if (mntRoot.exists() && mntRoot.isDirectory()) {
                    File[] mounts = mntRoot.listFiles();
                    if (mounts != null) {
                        for (File m : mounts) {
                            if (m.isDirectory()) searchDirs.add(m);
                        }
                    }
                }
            } catch (Exception ignored) {}

            FileFilter apkFilter = new FileFilter() {
                @Override
                public boolean accept(File file) {
                    if (file == null || !file.isFile()) return false;
                    String name = file.getName().toLowerCase();
                    return name.endsWith(".apk");
                }
            };

            FileFilter dirFilter = new FileFilter() {
                @Override
                public boolean accept(File file) {
                    if (file == null || !file.isDirectory()) return false;
                    String name = file.getName();
                    return !name.startsWith(".") && !name.equalsIgnoreCase("Android");
                }
            };

            java.util.Set<String> scannedDirPaths = new java.util.HashSet<>();
            List<File> allDirsToScan = new ArrayList<>();
            for (File d : searchDirs) {
                if (d != null && d.exists() && d.isDirectory() && scannedDirPaths.add(d.getAbsolutePath())) {
                    allDirsToScan.add(d);
                    // 仅对 Download 目录探测一级子目录
                    if (d.getName().equalsIgnoreCase("Download") || d.getName().equalsIgnoreCase("Downloads")) {
                        File[] subs = d.listFiles(dirFilter);
                        if (subs != null) {
                            for (File s : subs) {
                                if (scannedDirPaths.add(s.getAbsolutePath())) {
                                    allDirsToScan.add(s);
                                }
                            }
                        }
                    }
                }
            }

            java.util.Set<String> addedNames = new java.util.HashSet<>();
            for (File d : allDirsToScan) {
                try {
                    File[] files = d.listFiles(apkFilter);
                    if (files != null) {
                        for (File f : files) {
                            // 只要是有效 APK (大于 1 KB 且未重复)，全量纳入备选
                            if (f.length() > 1024 && !addedNames.contains(f.getName())) {
                                list.add(f);
                                addedNames.add(f.getName());
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 按最后修改时间倒序排列：刚下载/传进车机的文件排在最前面
            java.util.Collections.sort(list, new java.util.Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    return Long.compare(f2.lastModified(), f1.lastModified());
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "scanDownloadedApks error: " + e.getMessage(), e);
        }
        return list;
    }

    public static List<File> scanDownloadedApks() {
        return scanDownloadedApks(null);
    }

    public static List<File> scanDownloadedAmapApks() {
        return scanDownloadedApks();
    }

    private static int dp2px(Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}
