package app.onepve.geelyconsole.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.onepve.geelyconsole.MainActivity;
import app.onepve.geelyconsole.utils.SystemUtils;

/**
 * 悬浮窗后台守护服务（常驻桌面迷你胶囊浮窗）
 * 1. 真正的 Service 进程守护，切换桌面/导航/音乐永不丢失
 * 2. 自适应车机白天/黑夜模式动态变色
 * 3. 动态暗码随身显：点击动态码后浮窗秒变「🔑 暗码: #*MMDDhh」，25秒后自动恢复
 * 4. 支持单击秒回工具箱、双击快速隐藏、任意拖拽吸边
 */
public class FloatingWindowService extends Service {

    private static final String TAG = "FloatingWindowService";
    private static final String CHANNEL_ID = "geely_toolbox_daemon";
    private static final int NOTIF_ID = 1001;

    public static final String ACTION_TOGGLE = "action_toggle";
    public static final String ACTION_SHOW = "action_show";
    public static final String ACTION_HIDE = "action_hide";
    public static final String ACTION_SHOW_CODE = "action_show_code";
    public static final String EXTRA_CODE = "extra_code";

    public static boolean isRunning = false;

    private WindowManager wm;
    private View pillView;
    private TextView tvIcon;
    private TextView tvText;
    private TextView tvStatus;
    private LinearLayout layoutPillContainer;

    private boolean visible = false;
    private int pillX = -1, pillY = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String currentDynamicCode = null;
    private final Runnable revertCodeRunnable = new Runnable() {
        @Override
        public void run() {
            currentDynamicCode = null;
            updatePillContent();
        }
    };

    public static void ensureServiceStarted(Context context) {
        if (context == null) return;
        try {
            SystemUtils.grantOverlayPermissionViaShell(context);
            Intent intent = new Intent(context, FloatingWindowService.class);
            intent.setAction(ACTION_SHOW);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start FloatingWindowService: " + e.getMessage());
        }
    }

    public static void showDynamicCode(Context context, String code) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, FloatingWindowService.class);
            intent.setAction(ACTION_SHOW_CODE);
            intent.putExtra(EXTRA_CODE, code);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForegroundSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "吉利控制台后台守护", NotificationManager.IMPORTANCE_MIN);
                    channel.setDescription("保障悬浮小胶囊常驻运行");
                    channel.enableLights(false);
                    channel.enableVibration(false);
                    channel.setSound(null, null);
                    nm.createNotificationChannel(channel);
                }
                Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                        .setContentTitle("吉利控制台守护中")
                        .setContentText("全局悬浮胶囊常驻运行")
                        .setSmallIcon(app.onepve.geelyconsole.R.mipmap.ic_launcher);
                startForeground(NOTIF_ID, builder.build());
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        startForegroundSafely();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        SystemUtils.grantOverlayPermissionViaShell(this);
        showPill();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundSafely();
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_HIDE.equals(action)) {
                hidePill();
            } else if (ACTION_SHOW.equals(action)) {
                showPill();
            } else if (ACTION_TOGGLE.equals(action)) {
                if (visible) hidePill(); else showPill();
            } else if (ACTION_SHOW_CODE.equals(action)) {
                currentDynamicCode = intent.getStringExtra(EXTRA_CODE);
                showPill();
                updatePillContent();
                handler.removeCallbacks(revertCodeRunnable);
                handler.postDelayed(revertCodeRunnable, 25000); // 25秒后自动恢复正常文字
            }
        } else {
            showPill();
        }
        return START_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 系统日夜模式切换时，动态更新胶囊主题色彩
        if (pillView != null && visible) {
            applyThemeColors();
        }
    }

    private boolean isNightMode() {
        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyThemeColors() {
        if (layoutPillContainer == null || tvText == null) return;
        boolean night = isNightMode();

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp2px(22));

        if (night) {
            // 夜晚深蓝黑半透
            bg.setColor(Color.parseColor("#EE0F172A"));
            bg.setStroke(dp2px(1.5f), Color.parseColor("#3B82F6"));
            tvText.setTextColor(Color.parseColor("#F8FAFC"));
        } else {
            // 白天纯净浅灰白
            bg.setColor(Color.parseColor("#F5F8FAFC"));
            bg.setStroke(dp2px(1.5f), Color.parseColor("#2563EB"));
            tvText.setTextColor(Color.parseColor("#0F172A"));
        }
        layoutPillContainer.setBackground(bg);
    }

    private void updatePillContent() {
        if (tvIcon == null || tvText == null || tvStatus == null) return;
        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
        String displayMode = prefs.getString("floating_display_mode", "name"); // 默认 "name" 为吉利工具箱，"code" 为动态暗码

        if ("code".equals(displayMode)) {
            String code10 = SystemUtils.calculateDynamicCode();
            String code5 = SystemUtils.calculateDynamicCodePlus5();
            tvIcon.setText("🔑");
            tvText.setText("暗码(+10): " + code10 + "  |  暗码(+5): " + code5);
            tvStatus.setText("");
        } else {
            tvIcon.setText("🔧");
            tvText.setText("吉利工具箱");
            tvStatus.setText("");
        }
    }

    private void showPill() {
        if (visible && pillView != null && pillView.isAttachedToWindow()) {
            updatePillContent();
            applyThemeColors();
            return;
        }

        // 如果之前残留了未挂载或状态不一致的 view，先稳健清理
        if (pillView != null) {
            try {
                wm.removeViewImmediate(pillView);
            } catch (Exception ignored) {
            }
            pillView = null;
            visible = false;
        }

        // 确保悬浮窗权限放行
        SystemUtils.grantOverlayPermissionViaShell(this);

        pillView = buildPillView();
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.START;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenWidth = dm.widthPixels > 0 ? dm.widthPixels : 1920;
        int screenHeight = dm.heightPixels > 0 ? dm.heightPixels : 720;

        if (pillX == -1 || pillY == -1) {
            android.content.SharedPreferences sp = getSharedPreferences("floating_pill_prefs", Context.MODE_PRIVATE);
            int savedX = sp.getInt("pill_x", -1);
            int savedY = sp.getInt("pill_y", -1);
            if (savedX >= 0 && savedY >= 0 && savedX < screenWidth && savedY < screenHeight) {
                pillX = savedX;
                pillY = savedY;
            } else {
                // 默认初始位置：右上角安全区域 (屏幕右边缘往内 140dp，顶部 12dp)
                pillX = Math.max(20, screenWidth - dp2px(140));
                pillY = dp2px(12);
            }
        } else {
            // 确保坐标在当前屏幕范围内防移出屏幕
            pillX = Math.max(0, Math.min(pillX, screenWidth - dp2px(80)));
            pillY = Math.max(0, Math.min(pillY, screenHeight - dp2px(40)));
        }

        lp.x = pillX;
        lp.y = pillY;

        // 多层级 WindowManager 布局类型逐级 fallback，确保在吉利定制 Android 9 桌面 100% 成功挂载
        int[] candidateTypes;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            candidateTypes = new int[]{
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.TYPE_TOAST
            };
        } else {
            candidateTypes = new int[]{
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.TYPE_TOAST
            };
        }

        boolean added = false;
        for (int type : candidateTypes) {
            lp.type = type;
            try {
                wm.addView(pillView, lp);
                visible = true;
                added = true;
                break;
            } catch (Exception e) {
                Log.w(TAG, "Failed overlay with type " + type + ": " + e.getMessage());
            }
        }

        if (!added) {
            Log.e(TAG, "All candidate overlay types failed to add pillView");
            visible = false;
            pillView = null;
        }
    }

    private void hidePill() {
        if (pillView != null && visible) {
            try {
                wm.removeView(pillView);
            } catch (Exception ignored) {
            }
        }
        visible = false;
        pillView = null;
    }

    private View buildPillView() {
        layoutPillContainer = new LinearLayout(this);
        layoutPillContainer.setOrientation(LinearLayout.HORIZONTAL);
        layoutPillContainer.setGravity(Gravity.CENTER_VERTICAL);
        layoutPillContainer.setPadding(dp2px(14), dp2px(8), dp2px(14), dp2px(8));

        tvIcon = new TextView(this);
        tvIcon.setText("🔧");
        tvIcon.setTextSize(15);
        tvIcon.setPadding(0, 0, dp2px(6), 0);
        layoutPillContainer.addView(tvIcon);

        tvText = new TextView(this);
        tvText.setText("吉利工具箱");
        tvText.setTextSize(13);
        tvText.getPaint().setFakeBoldText(true);
        layoutPillContainer.addView(tvText);

        tvStatus = new TextView(this);
        tvStatus.setTextSize(11);
        layoutPillContainer.addView(tvStatus);

        updatePillContent();
        applyThemeColors();

                // 触摸、拖拽与点击交互（彻底移除双击隐藏，0延迟秒开工具箱，拖拽松手自动靠边吸附）
        layoutPillContainer.setOnTouchListener(new View.OnTouchListener() {
            private int initX, initY;
            private float initTouchX, initTouchY;
            private boolean moved = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                WindowManager.LayoutParams lp = (WindowManager.LayoutParams) v.getLayoutParams();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = lp.x;
                        initY = lp.y;
                        initTouchX = event.getRawX();
                        initTouchY = event.getRawY();
                        moved = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initTouchX);
                        int dy = (int) (event.getRawY() - initTouchY);
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                            moved = true;
                            lp.x = initX + dx;
                            lp.y = initY + dy;
                            try {
                                wm.updateViewLayout(v, lp);
                            } catch (Exception ignored) {
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            if (MainActivity.isForeground) {
                                // 🌟 工具箱当前在前台 ➔ 点击/双击胶囊 = 一键最小化退到后台（露出正在操作的文件管理器/任务管理器）！
                                MainActivity.minimizeCurrent();
                            } else {
                                // 🌟 工具箱当前在后台 ➔ 点击/双击胶囊 = 0延迟秒将工具箱拉到前台！
                                try {
                                    Intent intent = new Intent(FloatingWindowService.this, MainActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                                    startActivity(intent);
                                } catch (Exception ignored) {
                                }
                            }
                        } else {
                            // 🌟 拖拽松手：保持自由拖拽放置（并限制在屏幕可视范围内），实时记忆坐标
                            try {
                                DisplayMetrics dm = getResources().getDisplayMetrics();
                                int screenWidth = dm.widthPixels > 0 ? dm.widthPixels : 1920;
                                int screenHeight = dm.heightPixels > 0 ? dm.heightPixels : 720;
                                int pillWidth = (v.getWidth() > 0) ? v.getWidth() : dp2px(110);
                                int pillHeight = (v.getHeight() > 0) ? v.getHeight() : dp2px(36);

                                int clampX = Math.max(0, Math.min(lp.x, screenWidth - pillWidth));
                                int clampY = Math.max(0, Math.min(lp.y, screenHeight - pillHeight));

                                lp.x = clampX;
                                lp.y = clampY;
                                pillX = clampX;
                                pillY = clampY;
                                wm.updateViewLayout(v, lp);

                                // 实时持久化坐标，下次启动/退出后依然在原位
                                android.content.SharedPreferences sp = getSharedPreferences("floating_pill_prefs", Context.MODE_PRIVATE);
                                sp.edit().putInt("pill_x", pillX).putInt("pill_y", pillY).apply();
                            } catch (Exception ignored) {
                            }
                        }
                        return true;
                }
                return false;
            }
        });

        return layoutPillContainer;
    }

    private int dp2px(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
        hidePill();
    }
}
