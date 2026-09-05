package app.onepve.geelyconsole.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import app.onepve.geelyconsole.MainActivity;
import app.onepve.geelyconsole.services.FloatingWindowService;
import app.onepve.geelyconsole.services.VehicleAutomationService;
import app.onepve.geelyconsole.utils.AppLogger;
import app.onepve.geelyconsole.utils.SystemUtils;
import app.onepve.geelyconsole.utils.ThemePatcher;

/**
 * 车机开机自启守护广播接收器
 * 1. 开机自动自愈：双管齐下清除兔子伪装包与防覆写锁，释放 280MB 空间并恢复原厂自愈
 * 2. 目标应用（高德）安装/更新完成广播时秒级静默清理与官方还原
 * 3. 开机自动放行 sys.jsbd.apk_verify=1 白名单
 * 4. 依据用户自启设置 / 兔子时钟注入后临时自启标志，自动拉起悬浮胶囊或主界面
 * 5. 辅助唤起 CarMedia (com.ecarx.carmedia) 方控服务，确保方向盘切歌 100% 就绪
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "GeelyToolbox_Boot";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        Log.i(TAG, "Received broadcast action: " + action);

        // 场景 A：捕获应用安装/更新广播（高德/专车导航安装完成瞬间，毫秒级自动清理与自愈还原）
        if (Intent.ACTION_PACKAGE_ADDED.equals(action) || Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            try {
                String pkg = intent.getData() != null ? intent.getData().getSchemeSpecificPart() : null;
                AppLogger.action("应用监听", "捕获应用安装/更新广播", true, "包名: " + pkg);
                if ("com.autonavi.amapauto".equals(pkg) || "com.baidu.BaiduMap.auto".equals(pkg)) {
                    Log.i(TAG, "Nav package installed/replaced: " + pkg + ", auto-healing rabbit theme immediately!");
                    AppLogger.action("安装自愈", "检测到目标专车导航安装完成，触发官方原版时钟还原", true, "目标包: " + pkg);
                    ThemePatcher.restoreOfficialRabbitTheme(context);
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed package install cleanup: " + e.getMessage());
                AppLogger.e("安装自愈", "处理安装广播异常: " + e.getMessage());
            }
            return;
        }

        // 场景 B：开机/点火广播 —— 立即在后台静默清除伪装大包与防覆写锁，恢复官方原版时钟与系统自愈机制
        AppLogger.i("开机守护", "收到系统点火/开机广播: " + action);
        try {
            ThemePatcher.restoreDisguiseAndLockSilently(context);
            Log.i(TAG, "Theme boot auto-heal executed: disguise and lock cleared");
        } catch (Exception e) {
            Log.w(TAG, "Theme boot auto-heal error: " + e.getMessage());
            AppLogger.e("开机守护", "开机自愈清理异常: " + e.getMessage());
        }

        // 延时 2 秒等系统核心服务就绪后处理后续引导与服务守护
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                    boolean autostartEnabled = prefs.getBoolean("autostart_enabled", false);
                    boolean tempAutostartForRabbit = prefs.getBoolean("temp_autostart_for_rabbit", false);

                    // 1. 开机自动放行白名单（只要开启自启或属于注入后重启）
                    if (autostartEnabled || tempAutostartForRabbit) {
                        try {
                            SystemUtils.enableApkVerifyWhitelist(context);
                            Log.i(TAG, "Apk verify whitelist auto-enabled on boot");
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to enable whitelist on boot: " + e.getMessage());
                        }

                        // 2. 自动启动桌面悬浮小胶囊
                        try {
                            FloatingWindowService.ensureServiceStarted(context);
                            Log.i(TAG, "FloatingWindowService started on boot");
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to start FloatingWindowService: " + e.getMessage());
                        }
                    }

                    // 同步并启动座舱自动化智能联动（若用户已开启对应开关）
                    try {
                        VehicleAutomationService.syncState(context);
                    } catch (Exception ignored) {
                    }

                    // 3. 如果属于兔子时钟一键注入后的重启：直接全屏拉起工具箱，呈现高德安装就绪弹窗！
                    if (tempAutostartForRabbit) {
                        try {
                            Intent launchMain = new Intent(context, MainActivity.class);
                            launchMain.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                            context.startActivity(launchMain);
                            Log.i(TAG, "MainActivity launched for post-reboot rabbit installation");
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to launch MainActivity on post-reboot: " + e.getMessage());
                        }
                    }

                    // 4. 辅助唤起已安装的方控助手 (CarMedia)
                    try {
                        PackageManager pm = context.getPackageManager();
                        Intent launchCarMedia = pm.getLaunchIntentForPackage("com.ecarx.carmedia");
                        if (launchCarMedia != null) {
                            launchCarMedia.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            Intent carMediaBoot = new Intent("android.intent.action.BOOT_COMPLETED");
                            carMediaBoot.setPackage("com.ecarx.carmedia");
                            context.sendBroadcast(carMediaBoot);
                            Log.i(TAG, "CarMedia boot broadcast sent");
                        }
                    } catch (Exception ignored) {
                    }

                } catch (Exception e) {
                    Log.e(TAG, "BootReceiver handling error: " + e.getMessage(), e);
                }
            }
        }, 2000);
    }
}
