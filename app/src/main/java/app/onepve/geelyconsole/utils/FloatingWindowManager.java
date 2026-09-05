package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
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

/**
 * 悬浮窗管理器（吉利工具箱桌面迷你胶囊浮窗）
 * 在车机桌面上显示轻量小胶囊，点击秒回工具箱主界面，支持任意拖拽吸边。
 */
public class FloatingWindowManager {

    private static final String TAG = "FloatingWindowManager";
    private static FloatingWindowManager instance;
    private final Context context;
    private final WindowManager wm;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private View pillView;
    private boolean visible = false;
    private int pillX = -1, pillY = -1;

    private FloatingWindowManager(Context context) {
        this.context = context.getApplicationContext();
        this.wm = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
    }

    public static synchronized FloatingWindowManager getInstance(Context context) {
        if (instance == null) {
            instance = new FloatingWindowManager(context);
        }
        return instance;
    }

    /** 是否已授予悬浮窗权限 */
    public static boolean hasOverlayPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                return Settings.canDrawOverlays(context);
            } catch (Exception e) {
                return true;
            }
        }
        return true;
    }

    public void show() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (visible && pillView != null) return;

                pillView = buildPillView();
                int layoutType;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                } else {
                    layoutType = WindowManager.LayoutParams.TYPE_PHONE;
                }

                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        layoutType,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                );
                lp.gravity = Gravity.TOP | Gravity.START;
                if (pillX == -1 || pillY == -1) {
                    DisplayMetrics dm = context.getResources().getDisplayMetrics();
                    int screenWidth = dm.widthPixels > 0 ? dm.widthPixels : 1920;
                    pillX = Math.max(20, screenWidth - dp2px(540));
                    pillY = dp2px(6);
                }
                lp.x = pillX;
                lp.y = pillY;

                try {
                    wm.addView(pillView, lp);
                    visible = true;
                } catch (Exception e) {
                    Log.w(TAG, "Failed to add overlay view with default type, trying fallback: " + e.getMessage());
                    // 车机系统 fallback 兼容
                    try {
                        lp.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
                        wm.addView(pillView, lp);
                        visible = true;
                    } catch (Exception e2) {
                        try {
                            lp.type = WindowManager.LayoutParams.TYPE_TOAST;
                            wm.addView(pillView, lp);
                            visible = true;
                        } catch (Exception e3) {
                            Log.e(TAG, "All overlay window types failed: " + e3.getMessage());
                            visible = false;
                            pillView = null;
                        }
                    }
                }
            }
        });
    }

    public void hide() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (pillView != null && visible) {
                    try {
                        wm.removeView(pillView);
                    } catch (Exception ignored) {
                    }
                }
                visible = false;
                pillView = null;
            }
        });
    }

    public void toggle() {
        if (visible) {
            hide();
        } else {
            show();
        }
    }

    public boolean isVisible() {
        return visible;
    }

    private View buildPillView() {
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp2px(12), dp2px(6), dp2px(12), dp2px(6));

        // 现代化圆角胶囊背景
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp2px(20));
        bg.setColor(Color.parseColor("#EE0F172A")); // 深邃蓝黑半透
        bg.setStroke(dp2px(1), Color.parseColor("#3B82F6")); // 极客蓝细边框
        pill.setBackground(bg);

        // 图标
        TextView tvIcon = new TextView(context);
        tvIcon.setText("🔧");
        tvIcon.setTextSize(14);
        tvIcon.setPadding(0, 0, dp2px(4), 0);
        pill.addView(tvIcon);

        // 文字
        TextView tvText = new TextView(context);
        tvText.setText("工具箱");
        tvText.setTextColor(Color.parseColor("#F8FAFC"));
        tvText.setTextSize(12);
        tvText.getPaint().setFakeBoldText(true);
        pill.addView(tvText);

        // 状态徽标
        TextView tvStatus = new TextView(context);
        boolean wl = SystemUtils.isApkVerifyWhitelistEnabled();
        tvStatus.setText(wl ? " 🟢" : " 🔴");
        tvStatus.setTextSize(10);
        pill.addView(tvStatus);

        // 点击事件：秒回主界面
        pill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(context, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    context.startActivity(intent);
                } catch (Exception ignored) {
                }
            }
        });

        // 拖拽与触摸事件
        pill.setOnTouchListener(new View.OnTouchListener() {
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
                        return false;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initTouchX);
                        int dy = (int) (event.getRawY() - initTouchY);
                        if (Math.abs(dx) > 6 || Math.abs(dy) > 6) {
                            moved = true;
                            lp.x = initX + dx;
                            lp.y = initY + dy;
                            try {
                                wm.updateViewLayout(v, lp);
                            } catch (Exception ignored) {
                            }
                        }
                        return moved;

                    case MotionEvent.ACTION_UP:
                        pillX = lp.x;
                        pillY = lp.y;
                        return moved;
                }
                return false;
            }
        });

        return pill;
    }

    private int dp2px(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
