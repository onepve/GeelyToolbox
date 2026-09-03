package app.onepve.geelyconsole.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoPilotManager {

    private static final String TAG = "AutoPilotManager";
    private static AutoPilotManager instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View hudOverlayView;
    private TextView tvStatusTitle;
    private TextView tvStatusDesc;
    private Button btnCancel;

    private Thread workerThread;
    private volatile boolean isRunning = false;
    private volatile boolean isCancelled = false;

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

    public synchronized void startAutoInject(final Context context, final File targetApk) {
        if (isRunning) {
            Toast.makeText(context, "全自动流程已在运行中...", Toast.LENGTH_SHORT).show();
            return;
        }

        if (targetApk == null || !targetApk.exists() || targetApk.length() == 0) {
            Toast.makeText(context, "未找到有效的高德安装包，请先下载！", Toast.LENGTH_SHORT).show();
            return;
        }

        // Record temporary autostart for reboot recovery
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

        // 1. Show Giant HUD Overlay
        showHudOverlay(context, targetApk.getName());

        // 2. Start worker thread
        workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                executeAutoPipeline(context, targetApk);
            }
        });
        workerThread.start();
    }

    public synchronized void cancel(Context context) {
        if (!isRunning) return;
        isCancelled = true;
        isRunning = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
        hideHudOverlay();
        if (context != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, "已手动强制停止全自动安装流程", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void executeAutoPipeline(final Context context, final File targetApk) {
        try {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int screenW = dm.widthPixels > 0 ? dm.widthPixels : 1920;
            int screenH = dm.heightPixels > 0 ? dm.heightPixels : 720;

            // ==================== Step 1: 打包注入兔子时钟 ====================
            updateHud("步骤 1/5: 正在打包伪装至兔子时钟...", "正在将【" + targetApk.getName() + "】写入屏保主题...");
            boolean ok = ThemePatcher.packageToRabbitTheme(context, targetApk);
            if (!ok) {
                abortWithToast(context, "步骤 1 失败: 兔子时钟打包写入失败，请检查机身存储权限！");
                return;
            }
            sleepSafe(1200);
            if (isCancelled) return;

            // ==================== Step 2: 打开车机主题应用 ====================
            updateHud("步骤 2/5: 正在打开车机主题中心...", "正在唤起吉利原厂主题管理器...");
            boolean opened = ThemePatcher.openRabbitThemeSetting(context);
            if (!opened) {
                abortWithToast(context, "步骤 2 失败: 未能启动车机主题应用，请手动在应用列表打开！");
                return;
            }
            sleepSafe(2500);
            if (isCancelled) return;

            // ==================== Step 3: 点击【屏保时钟】分类 ====================
            updateHud("步骤 3/5: 正在进入【屏保时钟】分类...", "定位并点击顶栏【屏保时钟】分类标签...");
            Point tabClockPt = findNodeCenterByText(context, "屏保时钟");
            if (tabClockPt == null) tabClockPt = findNodeCenterByText(context, "屏保");
            if (tabClockPt == null) tabClockPt = findNodeCenterByText(context, "时钟");

            if (tabClockPt != null) {
                clickPoint(context, tabClockPt.x, tabClockPt.y);
            } else {
                clickPoint(context, (int) (screenW * 0.45f), (int) (screenH * 0.16f));
            }
            sleepSafe(1500);
            if (isCancelled) return;

            // ==================== Step 4: 从右向左滑动，选到【兔子时钟】 ====================
            updateHud("步骤 4/5: 正在查找并选中【兔子时钟】...", "在屏保列表中向左滑屏，定位『兔子时钟』...");
            Point rabbitPt = findNodeCenterByText(context, "兔子时钟");
            if (rabbitPt == null) rabbitPt = findNodeCenterByText(context, "兔子");

            if (rabbitPt == null) {
                swipeRightToLeft(context, screenW, screenH);
                sleepSafe(1200);
                rabbitPt = findNodeCenterByText(context, "兔子时钟");
                if (rabbitPt == null) rabbitPt = findNodeCenterByText(context, "兔子");
            }

            if (rabbitPt != null) {
                clickPoint(context, rabbitPt.x, rabbitPt.y);
            } else {
                clickPoint(context, (int) (screenW * 0.50f), (int) (screenH * 0.52f));
            }
            sleepSafe(1200);
            if (isCancelled) return;

            // ==================== Step 5: 点击【应用】并监听反馈 ====================
            updateHud("步骤 5/5: 正在点击【应用】主题...", "提交应用指令，等待系统反馈【应用成功】或【应用失败】...");
            Point applyPt = findNodeCenterByText(context, "应用");
            if (applyPt == null) applyPt = findNodeCenterByText(context, "使用");

            if (applyPt != null) {
                clickPoint(context, applyPt.x, applyPt.y);
            } else {
                clickPoint(context, (int) (screenW * 0.88f), (int) (screenH * 0.89f));
            }

            // 监听反馈（只要出现“应用成功”或“应用失败”，即说明注入和应用已经结束）
            for (int check = 0; check < 8; check++) {
                if (isCancelled) return;
                sleepSafe(500);
                String uiDump = dumpUiHierarchy(context);
                if (uiDump != null && (uiDump.contains("成功") || uiDump.contains("失败") || uiDump.contains("使用中"))) {
                    break;
                }
            }

            if (isCancelled) return;

            // ==================== Step 6: 倒计时并自动软重启车机 ====================
            for (int countdown = 3; countdown >= 1; countdown--) {
                if (isCancelled) return;
                updateHud("🎉 注入与应用已结束！准备重启车机...", "主题应用指令已完成！车机将在 " + countdown + " 秒后自动软重启生效 (点击右侧可停止)...");
                sleepSafe(1000);
            }

            if (isCancelled) return;

            // Trigger soft reboot
            updateHud("正在执行软重启...", "软重启生效中，开机打开工具箱将自动放行白名单！");
            AdbClient.execute(context, "setprop ctl.restart zygote");
            sleepSafe(1000);

        } catch (Exception e) {
            Log.e(TAG, "Auto pipeline exception: " + e.getMessage(), e);
            abortWithToast(context, "全自动流程异常: " + e.getMessage());
        } finally {
            isRunning = false;
            hideHudOverlay();
        }
    }

    private String dumpUiHierarchy(Context context) {
        try {
            AdbClient.AdbResult res = AdbClient.execute(context, "uiautomator dump /data/local/tmp/uidump.xml && cat /data/local/tmp/uidump.xml");
            if (res != null && res.success && res.output != null && !res.output.trim().isEmpty()) {
                return res.output;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Point findNodeCenterByText(Context context, String targetText) {
        try {
            String xml = dumpUiHierarchy(context);
            if (xml == null || xml.isEmpty()) return null;

            Pattern pattern = Pattern.compile("(?:text|content-desc)=\"([^\"]*" + Pattern.quote(targetText) + "[^\"]*)\"[^>]*bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"");
            Matcher m = pattern.matcher(xml);
            if (m.find()) {
                int x1 = Integer.parseInt(m.group(2));
                int y1 = Integer.parseInt(m.group(3));
                int x2 = Integer.parseInt(m.group(4));
                int y2 = Integer.parseInt(m.group(5));
                return new Point((x1 + x2) / 2, (y1 + y2) / 2);
            }
        } catch (Exception e) {
            Log.w(TAG, "findNodeCenterByText err: " + e.getMessage());
        }
        return null;
    }

    private void clickPoint(Context context, int x, int y) {
        try {
            AdbClient.execute(context, "input tap " + x + " " + y);
        } catch (Exception ignored) {}
    }

    private void swipeRightToLeft(Context context, int screenW, int screenH) {
        try {
            int startX = (int) (screenW * 0.80f);
            int endX = (int) (screenW * 0.20f);
            int y = (int) (screenH * 0.55f);
            AdbClient.execute(context, "input swipe " + startX + " " + y + " " + endX + " " + y + " 350");
        } catch (Exception ignored) {}
    }

    private void sleepSafe(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            isCancelled = true;
        }
    }

    private void abortWithToast(final Context context, final String msg) {
        isRunning = false;
        hideHudOverlay();
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateHud(final String title, final String desc) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (tvStatusTitle != null) tvStatusTitle.setText(title);
                if (tvStatusDesc != null) tvStatusDesc.setText(desc);
            }
        });
    }

    /**
     * 展现座舱超大号全景 HUD 悬浮提示（高度 180~220px，字体 26sp，超大红色停止按钮 72px）
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
                    params.y = dp2px(context, 16);

                    // Build HUGE HUD Root Layout
                    LinearLayout root = new LinearLayout(context);
                    root.setOrientation(LinearLayout.HORIZONTAL);
                    root.setGravity(Gravity.CENTER_VERTICAL);
                    root.setPadding(dp2px(context, 32), dp2px(context, 20), dp2px(context, 28), dp2px(context, 20));

                    // Background: 96% opacity dark slate with 3.5px neon cyan stroke
                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(Color.parseColor("#F50A0D14"));
                    bg.setCornerRadius(dp2px(context, 20));
                    bg.setStroke(dp2px(context, 3.5f), Color.parseColor("#00AEB9"));
                    root.setBackground(bg);

                    // Left Giant Robot Icon (44sp)
                    TextView tvIcon = new TextView(context);
                    tvIcon.setText("🤖");
                    tvIcon.setTextSize(44);
                    tvIcon.setPadding(0, 0, dp2px(context, 20), 0);
                    root.addView(tvIcon);

                    // Middle Text Column (Huge, Bold, High Visibility)
                    LinearLayout textBox = new LinearLayout(context);
                    textBox.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                    textBox.setLayoutParams(textLp);

                    tvStatusTitle = new TextView(context);
                    tvStatusTitle.setText("👉 步骤 1/2: 请点击【屏保时钟】➔ 选【兔子时钟】并点应用");
                    tvStatusTitle.setTextSize(26); // Huge 26sp title
                    tvStatusTitle.setTextColor(Color.WHITE);
                    tvStatusTitle.setTypeface(Typeface.DEFAULT_BOLD);
                    textBox.addView(tvStatusTitle);

                    tvStatusDesc = new TextView(context);
                    tvStatusDesc.setText("💡 提示：车机提示成功/失败均已生效！应用后请点右侧【⚡ 立即软重启】");
                    tvStatusDesc.setTextSize(17); // Big 17sp subtitle
                    tvStatusDesc.setTextColor(Color.parseColor("#38BDF8")); // Bright Cyan Accent
                    tvStatusDesc.setTypeface(Typeface.DEFAULT_BOLD);
                    tvStatusDesc.setPadding(0, dp2px(context, 6), 0, 0);
                    textBox.addView(tvStatusDesc);

                    root.addView(textBox);

                    // Right Action Buttons Box
                    LinearLayout btnBox = new LinearLayout(context);
                    btnBox.setOrientation(LinearLayout.HORIZONTAL);
                    btnBox.setGravity(Gravity.CENTER_VERTICAL);
                    btnBox.setPadding(dp2px(context, 16), 0, 0, 0);

                    // 1. Soft Reboot (Primary Action)
                    Button btnSoft = new Button(context);
                    btnSoft.setText("⚡ 我已应用，立即软重启 ➔");
                    btnSoft.setTextSize(18);
                    btnSoft.setTextColor(Color.WHITE);
                    btnSoft.setTypeface(Typeface.DEFAULT_BOLD);
                    btnSoft.setPadding(dp2px(context, 18), dp2px(context, 8), dp2px(context, 18), dp2px(context, 8));
                    GradientDrawable softBg = new GradientDrawable();
                    softBg.setColor(Color.parseColor("#0284C7"));
                    softBg.setCornerRadius(dp2px(context, 12));
                    softBg.setStroke(dp2px(context, 2), Color.parseColor("#38BDF8"));
                    btnSoft.setBackground(softBg);
                    btnSoft.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            hideHudOverlay();
                            Toast.makeText(context, "正在执行 5秒极速软重启...", Toast.LENGTH_SHORT).show();
                            SystemUtils.executePrivileged(context, "setprop ctl.restart zygote");
                        }
                    });
                    LinearLayout.LayoutParams softLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp2px(context, 62));
                    softLp.setMargins(0, 0, dp2px(context, 10), 0);
                    btnSoft.setLayoutParams(softLp);
                    btnBox.addView(btnSoft);

                    // 2. Hard Reboot (Cold Restart Action)
                    Button btnHard = new Button(context);
                    btnHard.setText("🔄 完整硬重启");
                    btnHard.setTextSize(16);
                    btnHard.setTextColor(Color.parseColor("#CBD5E1"));
                    btnHard.setTypeface(Typeface.DEFAULT_BOLD);
                    btnHard.setPadding(dp2px(context, 14), dp2px(context, 8), dp2px(context, 14), dp2px(context, 8));
                    GradientDrawable hardBg = new GradientDrawable();
                    hardBg.setColor(Color.parseColor("#1E293B"));
                    hardBg.setCornerRadius(dp2px(context, 12));
                    hardBg.setStroke(dp2px(context, 1.5f), Color.parseColor("#475569"));
                    btnHard.setBackground(hardBg);
                    btnHard.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            hideHudOverlay();
                            Toast.makeText(context, "正在执行车机硬件冷重启...", Toast.LENGTH_SHORT).show();
                            SystemUtils.executePrivileged(context, "reboot || svc power reboot");
                        }
                    });
                    LinearLayout.LayoutParams hardLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp2px(context, 62));
                    hardLp.setMargins(0, 0, dp2px(context, 10), 0);
                    btnHard.setLayoutParams(hardLp);
                    btnBox.addView(btnHard);

                    // 3. Cancel Button
                    btnCancel = new Button(context);
                    btnCancel.setText("⏹ 退出向导");
                    btnCancel.setTextSize(16);
                    btnCancel.setTextColor(Color.WHITE);
                    btnCancel.setTypeface(Typeface.DEFAULT_BOLD);
                    btnCancel.setPadding(dp2px(context, 14), dp2px(context, 8), dp2px(context, 14), dp2px(context, 8));
                    GradientDrawable cancelBg = new GradientDrawable();
                    cancelBg.setColor(Color.parseColor("#DC2626"));
                    cancelBg.setCornerRadius(dp2px(context, 12));
                    cancelBg.setStroke(dp2px(context, 1.5f), Color.parseColor("#EF4444"));
                    btnCancel.setBackground(cancelBg);
                    btnCancel.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            cancel(context);
                        }
                    });
                    LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp2px(context, 62));
                    btnCancel.setLayoutParams(cancelLp);
                    btnBox.addView(btnCancel);

                    root.addView(btnBox);

                    hudOverlayView = root;
                    windowManager.addView(hudOverlayView, params);

                } catch (Exception e) {
                    Log.e(TAG, "showHudOverlay error: " + e.getMessage());
                }
            }
        });
    }

    private void hideHudOverlay() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (windowManager != null && hudOverlayView != null) {
                        windowManager.removeView(hudOverlayView);
                        hudOverlayView = null;
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    public static List<File> scanDownloadedAmapApks() {
        List<File> list = new ArrayList<>();
        try {
            File downloadDir = new File(Environment.getExternalStorageDirectory(), "Download");
            if (downloadDir.exists() && downloadDir.isDirectory()) {
                File[] files = downloadDir.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        if (!file.isFile() || !file.getName().toLowerCase().endsWith(".apk")) {
                            return false;
                        }
                        String name = file.getName().toLowerCase();
                        return name.contains("amap") || name.contains("高德") || name.contains("automap");
                    }
                });
                if (files != null) {
                    for (File f : files) {
                        if (f.length() > 10 * 1024 * 1024) { // > 10MB
                            list.add(f);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private static int dp2px(Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}
