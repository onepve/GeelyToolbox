package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 手机快传到达提示横幅（车机端弹窗）
 *
 * 为什么要有它：车主在手机上点完上传，人盯着的是手机屏，车机那边以前只在
 * MainActivity 处于前台时弹一条小 Toast —— 多数时候车机停在桌面或别的地图界面，
 * 车主回来一看「啥也没有」，误以为没传上去。这里改成窗口级悬浮横幅：
 *   · 不依赖助手界面是否在前台，进程活着就能弹；
 *   · 一次多选连传多个文件时，同一块横幅按顺序累计已收到的文件，不会刷屏；
 *   · 强调色统一取 ThemeColorHelper（跟随车主选的主题），昼夜底色跟随系统深浅模式；
 *   · 6 秒自动收起，轻触立即关闭。
 *
 * 线程：所有窗口操作必须在主线程，外部任意线程调用均可（内部 post 到主线程）。
 */
public final class UploadNoticeOverlay {

    private static final String TAG = "UploadNotice";
    private static final long AUTO_HIDE_MS = 6000L;
    private static final int MAX_LINES = 4;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static WindowManager windowManager;
    private static View bannerView;
    private static TextView tvDetail;
    private static LinearLayout container;
    private static boolean attached = false;

    private static final List<String> received = new ArrayList<>();
    private static long batchBytes = 0;
    private static long lastArriveMs = 0L;
    /** 两批之间的静默间隔：超过这个时长再来的文件视为新一批，重新累计，不把上一次的记录混进来 */
    private static final long BATCH_GAP_MS = 30000L;

    private static final Runnable HIDE_TASK = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    private UploadNoticeOverlay() {
    }

    /** 每收到一个完整文件（分片的最后一片落盘后）调用一次 */
    public static void notifyUploaded(final Context context, final File file) {
        if (context == null || file == null) return;
        final Context appCtx = context.getApplicationContext();
        if (appCtx == null) return;
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                try {
                    appendFile(file);
                    showBanner(appCtx);
                } catch (Throwable t) {
                    Log.w(TAG, "展示快传提示横幅失败: " + t.getMessage());
                }
            }
        });
    }

    private static void appendFile(File file) {
        long now = System.currentTimeMillis();
        // 上一批已结束（横幅收起超过静默间隔）→ 重新开一批，避免把上次的文件名混进这次的提示
        if (lastArriveMs > 0L && now - lastArriveMs > BATCH_GAP_MS) {
            received.clear();
            batchBytes = 0;
        }
        lastArriveMs = now;

        // 选文件夹上传时文件落在 Download/子目录里，这里显示相对 Download 的路径，
        // 车主才能确认「文件夹结构真的保留了」，而不是只看到一个光文件名。
        String name = file.getName();
        try {
            String base = SystemUtils.getAppDownloadDir().getCanonicalPath();
            String full = file.getCanonicalPath();
            if (full.startsWith(base + File.separator)) {
                name = full.substring(base.length() + 1).replace(File.separatorChar, '/');
            }
        } catch (Throwable ignored) {
        }
        String one = name + "（" + SystemUtils.formatFileSize(file.length()) + "）";
        if (received.size() >= MAX_LINES) {
            received.remove(0);
        }
        received.add(one);
        batchBytes += file.length();
    }

    private static String buildDetailText() {
        int n = received.size();
        if (n == 0) return "";
        if (n == 1) {
            return "已存入车机 Download 目录\n" + received.get(0);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("已按顺序接收 ").append(n).append(" 个文件，合计 ")
                .append(SystemUtils.formatFileSize(batchBytes)).append('\n');
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append('\n');
            sb.append(i + 1).append(". ").append(received.get(i));
        }
        return sb.toString();
    }

    private static boolean isNightMode(Context ctx) {
        int flags = ctx.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return flags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static void showBanner(Context ctx) {
        if (bannerView == null) {
            bannerView = buildBanner(ctx);
        }
        if (tvDetail != null) {
            tvDetail.setText(buildDetailText());
        }
        if (attached) {
            MAIN.removeCallbacks(HIDE_TASK);
            MAIN.postDelayed(HIDE_TASK, AUTO_HIDE_MS);
            return;
        }
        if (windowManager == null) {
            windowManager = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        }
        if (windowManager == null) {
            Log.w(TAG, "WindowManager 不可用，快传提示退化为系统 Toast");
            return;
        }

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        lp.y = Math.round((dm.density > 0 ? 16f : 16f) * dm.density);

        int[] candidateTypes;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            candidateTypes = new int[]{
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.TYPE_PHONE
            };
        } else {
            candidateTypes = new int[]{
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.TYPE_PHONE
            };
        }

        for (int type : candidateTypes) {
            lp.type = type;
            try {
                windowManager.addView(bannerView, lp);
                attached = true;
                MAIN.removeCallbacks(HIDE_TASK);
                MAIN.postDelayed(HIDE_TASK, AUTO_HIDE_MS);
                return;
            } catch (Throwable t) {
                Log.w(TAG, "悬浮横幅挂载失败 (type " + type + "): " + t.getMessage());
            }
        }
        Log.e(TAG, "所有悬浮层类型均挂载失败，快传提示退化为系统提示条");
        try {
            String text = received.isEmpty() ? "手机快传完成" : ("手机快传完成：" + received.get(received.size() - 1));
            android.widget.Toast.makeText(ctx, text, android.widget.Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
        }
    }

    private static View buildBanner(final Context ctx) {
        boolean night = isNightMode(ctx);
        int accent = ThemeColorHelper.getAccentColor(ctx);

        container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        int padH = dp(ctx, 22);
        int padV = dp(ctx, 16);
        container.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(ctx, 18));
        bg.setColor(night ? 0xEE0F172A : 0xF7FFFFFF);
        bg.setStroke(dp(ctx, 2), accent);
        container.setBackground(bg);
        container.setElevation(dp(ctx, 8));

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("📥 手机快传完成");
        tvTitle.setTextSize(19);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(night ? 0xFFF8FAFC : 0xFF0F172A);
        container.addView(tvTitle);

        tvDetail = new TextView(ctx);
        tvDetail.setTextSize(14);
        tvDetail.setLineSpacing(dp(ctx, 4), 1f);
        tvDetail.setTextColor(night ? 0xFFCBD5E1 : 0xFF334155);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailLp.topMargin = dp(ctx, 8);
        tvDetail.setLayoutParams(detailLp);
        container.addView(tvDetail);

        TextView tvHint = new TextView(ctx);
        tvHint.setText("轻触关闭 · 约 6 秒后自动收起");
        tvHint.setTextSize(11);
        tvHint.setTextColor(accent);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(ctx, 8);
        tvHint.setLayoutParams(hintLp);
        container.addView(tvHint);

        container.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });
        return container;
    }

    /** 收起横幅并清空本批累计（下一批上传重新计数） */
    public static void hide() {
        MAIN.removeCallbacks(HIDE_TASK);
        if (bannerView != null && attached && windowManager != null) {
            try {
                windowManager.removeViewImmediate(bannerView);
            } catch (Throwable ignored) {
            }
        }
        attached = false;
        bannerView = null;
        tvDetail = null;
        container = null;
        received.clear();
        batchBytes = 0;
    }

    private static int dp(Context ctx, int value) {
        float density = ctx.getResources().getDisplayMetrics().density;
        if (density <= 0f) density = 1f;
        return Math.round(value * density);
    }
}
