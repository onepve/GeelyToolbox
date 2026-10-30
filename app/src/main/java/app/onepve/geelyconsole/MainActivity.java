package app.onepve.geelyconsole;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.bluetooth.BluetoothAdapter;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import app.onepve.geelyconsole.utils.EasMediaBridge;
import app.onepve.geelyconsole.utils.NetworkPolicyManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import app.onepve.geelyconsole.hal.IVehiclePlatform;
import app.onepve.geelyconsole.hal.VehiclePlatformFactory;
import app.onepve.geelyconsole.server.WebServer;
import app.onepve.geelyconsole.services.FloatingWindowService;
import app.onepve.geelyconsole.services.VehicleAutomationService;
import app.onepve.geelyconsole.utils.AdbClient;
import app.onepve.geelyconsole.utils.AppLogger;
import app.onepve.geelyconsole.utils.AutoPilotManager;
import app.onepve.geelyconsole.utils.DownloadManager;
import app.onepve.geelyconsole.utils.ForegroundAppDetector;
import app.onepve.geelyconsole.utils.IdleScreensaverManager;
import app.onepve.geelyconsole.utils.VehicleConfigHelper;
import app.onepve.geelyconsole.utils.AppFilterUtils;
import app.onepve.geelyconsole.utils.SystemUtils;
import app.onepve.geelyconsole.utils.SystemUtils.OnUploadProgressListener;
import app.onepve.geelyconsole.utils.SystemUtils.LogDumpProgressListener;
import app.onepve.geelyconsole.utils.ThemePatcher;
import app.onepve.geelyconsole.utils.VehicleVoicePlayer;
import app.onepve.geelyconsole.utils.PrefUtils;
import app.onepve.geelyconsole.utils.SteeringWheelKeyManager;
import app.onepve.geelyconsole.utils.AppIconManager;
import app.onepve.geelyconsole.utils.FuelManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity implements WebServer.WebServerCallback {

    public static volatile boolean isForeground = false;
    public static WeakReference<MainActivity> currentActivity;

    public static void minimizeCurrent() {
        if (currentActivity != null) {
            final MainActivity activity = currentActivity.get();
            if (activity != null && !activity.isFinishing()) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        activity.moveTaskToBack(true);
                    }
                });
            }
        }
    }

    public static void requestDeviceInfoPush() {
        if (currentActivity != null) {
            MainActivity activity = currentActivity.get();
            if (activity != null && !activity.isFinishing()) {
                activity.pushDeviceInfoToWeb();
            }
        }
    }

    private static final String TAG = "MainActivity";
    private static final int REQ_CODE_STORAGE = 1001;
    private static final int REQ_CODE_SELECT_APK = 2001;
    private static final int REQ_CODE_SELECT_VOICE = 2002;
    private static final int REQ_CODE_EXPORT_THEME = 2003;
    private String currentSelectingVoiceKey = null;

    private WebView webView;
    private View splashView;
    private WebServer webServer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isWhitelistEnabled = false;
    private Runnable pendingShowPillRunnable = null;

    private final Runnable statusTicker = new Runnable() {
        @Override
        public void run() {
            pushDeviceInfoToWeb();
            mainHandler.postDelayed(this, 30000);
        }
    };

    private String pendingExportOutputName;
    private String pendingExportStagedPath;

    private final BroadcastReceiver networkChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            pushNetworkStatusToWeb();
            pushNetworkDeviceStatusToWeb();
        }
    };

    private final BroadcastReceiver volumeChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())) {
                try {
                    int streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
                    if (streamType == android.media.AudioManager.STREAM_MUSIC || streamType == -1) {
                        int newVol = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1);
                        if (newVol < 0) {
                            android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
                            if (am != null) newVol = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                        }
                        if (newVol >= 0 && webView != null) {
                            final int finalVol = newVol;
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    webView.evaluateJavascript("if(window.onSystemVolumeChanged){window.onSystemVolumeChanged(" + finalVol + ");}", null);
                                }
                            });
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
    };

    public void pushNetworkStatusToWeb() {
        if (webView == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    SystemUtils.NetStatus net = SystemUtils.getNetworkStatus();
                    JSONObject obj = net.toJson();
                    obj.put("ip", net.ip);
                    obj.put("car_ip", net.ip);
                    obj.put("net_type", net.typeName);
                    obj.put("is_wifi", net.isWifiOrLan);
                    obj.put("is_cellular", net.isCellular);
                    if (webView != null) {
                        webView.evaluateJavascript("if(window.onNetworkChanged) window.onNetworkChanged(" + obj.toString() + ");", null);
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    public void pushNetworkDeviceStatusToWeb() {
        if (webView == null) return;
        NETWORK_WORKER.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONObject obj = NetworkPolicyManager.getFullStatusJson(MainActivity.this);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (webView != null) {
                                webView.evaluateJavascript("if(window.onNetworkDeviceStatusChanged) window.onNetworkDeviceStatusChanged(" + obj.toString() + ");", null);
                            }
                        }
                    });
                } catch (Exception ignored) {}
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentActivity = new WeakReference<>(this);
        AppLogger.init(this);
        PrefUtils.sanitizeDoorPreferences(getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE));
        hideSystemUI();
        setContentView(R.layout.activity_main);
        splashView = findViewById(R.id.splash_view);

        // 异步应用车标策略（自适应车型或保持自定义配置）
        AppIconManager.applyAppIconStyle(this);

        initWebView();
        checkAndRequestStoragePermission();
        try {
            IntentFilter netFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            netFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            netFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            netFilter.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");
            netFilter.addAction("android.net.conn.TETHER_STATE_CHANGED");
            registerReceiver(networkChangeReceiver, netFilter);
        } catch (Exception ignored) {}
        try {
            registerReceiver(volumeChangeReceiver, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
        } catch (Exception ignored) {}
        try {
            IntentFilter pkgFilter = new IntentFilter();
            pkgFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
            pkgFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            pkgFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            pkgFilter.addDataScheme("package");
            registerReceiver(packageChangeReceiver, pkgFilter);
        } catch (Exception ignored) {}
        AppLogger.i("应用启动", "缤越助手界面启动完成");

        // 若是由开机自启静默预热唤起的，立即移入后台，完成渲染准备但不打扰当前屏幕
        if (getIntent() != null && getIntent().getBooleanExtra("silent_boot_preload", false)) {
            AppLogger.i("应用启动", "开机静默预热：已提前绘制就绪，静默退至后台待命");
            if (splashView != null) {
                splashView.setVisibility(View.GONE);
            }
            moveTaskToBack(true);
        } else {
            // 兜底安全防线：冷启动后最长 2.5 秒无论前端是否通知，均平滑解除启动层
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    hideSplashView();
                }
            }, 2500);
        }

        // 启动错峰：包名扫描与白名单探测等重型后台任务延后 20 秒错峰执行，确保前台界面与车门/挡位联动 100% 独占 CPU 与流畅响应
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // 预热「全量禁用包名」缓存（后台一次 pm list packages -d，之后热路径零 shell）
                            SystemUtils.warmDisabledPackagesCache();
                            if (!SystemUtils.isApkVerifyWhitelistEnabled()) {
                                SystemUtils.enableApkVerifyWhitelist(MainActivity.this);
                            }
                            // 启动静默冻结应用商店：仅当用户在「系统维护」里显式开启开关后才执行（默认关闭，
                            // 严禁一启动就自动冻结 —— 是否冻结始终由用户主动决定）
                            android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                            if (prefs.getBoolean("silent_appstore_freeze", false) && isCarDevice(MainActivity.this)) {
                                boolean isAppstoreFrozen = (SystemUtils.getAppDetailedState(MainActivity.this, "com.ecarx.appstore") == SystemUtils.APP_STATE_DISABLED);
                                if (!isAppstoreFrozen && SystemUtils.isPackageInstalled(MainActivity.this, "com.ecarx.appstore")) {
                                    SystemUtils.setPackageEnabled(MainActivity.this, "com.ecarx.appstore", false);
                                    AppLogger.action("应用商店", "启动静默冻结开关已开启，检测到商店未冻结，已自动冻结锁定白名单", true, "已冻结");
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        pushDeviceInfoToWeb();
                    }
                }, "MainActivity_WarmupThread").start();
            }
        }, 20000);

        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    mainHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            hideSystemUI();
                        }
                    }, 800);
                }
            }
        });

        // 1. 默认常驻启动后台无线快传 WebServer (8888 端口，开机即秒连)
        try {
            if (webServer == null) {
                webServer = new WebServer(this, this);
                webServer.start();
            }
        } catch (Exception ignored) {
        }

        // 2. 根据用户设置启动后台悬浮小胶囊守护（默认 false 不显示）
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            boolean floatingEnabled = prefs.getBoolean("floating_enabled", false);
            if (floatingEnabled) {
                FloatingWindowService.ensureServiceStarted(this);
            }
        } catch (Exception ignored) {
        }

        // 3. 根据用户设置启动座舱自动化联动守护（默认全关，按需启动）
        try {
            VehicleAutomationService.syncState(this);
        } catch (Exception ignored) {
        }
    }

    private void initWebView() {
        webView = findViewById(R.id.webview);
        if (webView == null) return;

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setUserAgentString(settings.getUserAgentString() + " GeelyToolbox");

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setBackgroundColor(0xFF0A0D14);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pushDeviceInfoToWeb();
                if (getIntent() != null && getIntent().hasExtra("eval_js")) {
                    mainHandler.postDelayed(() -> callJs(getIntent().getStringExtra("eval_js")), 300);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new ToolboxBridge(this), "ToolboxBridge");
        webView.loadUrl("file:///android_asset/toolbox_ui.html");
    }

    private void hideSplashView() {
        if (splashView != null && splashView.getVisibility() == View.VISIBLE) {
            splashView.animate()
                    .alpha(0f)
                    .setDuration(220)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (splashView != null) {
                                splashView.setVisibility(View.GONE);
                            }
                        }
                    })
                    .start();
        }
    }

    public void fetchCloudAppsAsync(final boolean showToast) {
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    java.net.URL url = new java.net.URL("https://dl.onepve.com/GeelyToolbox/apps.json?t=" + System.currentTimeMillis());
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);
                    conn.setRequestProperty("User-Agent", SystemUtils.getAppUserAgent());
                    final int respCode = conn.getResponseCode();
                    if (respCode == 200) {
                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close();
                        final String jsonContent = sb.toString();

                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (webView != null) {
                                    webView.evaluateJavascript("if(window.applyCloudAppsJson) window.applyCloudAppsJson(" + JSONObject.quote(jsonContent) + "); if(window.onCloudAppsRefreshComplete) window.onCloudAppsRefreshComplete(true, 'ok');", null);
                                }
                                if (showToast) {
                                    Toast.makeText(MainActivity.this, "车载软件列表已刷新至最新", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    } else {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (webView != null) {
                                    webView.evaluateJavascript("if(window.onCloudAppsRefreshComplete) window.onCloudAppsRefreshComplete(false, 'HTTP " + respCode + "');", null);
                                }
                                if (showToast) {
                                    Toast.makeText(MainActivity.this, "无法连接云端软件源", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (webView != null) {
                                webView.evaluateJavascript("if(window.onCloudAppsRefreshComplete) window.onCloudAppsRefreshComplete(false, '" + e.getMessage().replace("'", "\\'") + "');", null);
                            }
                            if (showToast) {
                                Toast.makeText(MainActivity.this, "刷新云端软件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }
        });
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    public void callJs(final String script) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (webView != null) {
                    webView.evaluateJavascript(script, null);
                }
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        hideSystemUI();
        if (intent != null && intent.hasExtra("eval_js")) {
            callJs(intent.getStringExtra("eval_js"));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isForeground = true;
        FloatingWindowService.isMainActivityInForeground = true;
        currentActivity = new WeakReference<>(this);
        hideSystemUI();
        // 前台恢复防抖：延后 600ms 执行状态轮询，避开 Window 渲染与 WebView Surface 重建高峰，消除切回前台的掉帧迟钝感
        mainHandler.removeCallbacks(statusTicker);
        mainHandler.postDelayed(statusTicker, 600);

        // 取消任何待弹出的悬浮窗延迟任务
        if (pendingShowPillRunnable != null) {
            mainHandler.removeCallbacks(pendingShowPillRunnable);
            pendingShowPillRunnable = null;
        }

        // 前台自适应：当控制台处于前台大屏展示时，隐藏悬浮小胶囊，彻底杜绝悬浮窗遮挡顶栏与页面内闪烁
        try {
            Intent hidePill = new Intent(this, FloatingWindowService.class);
            hidePill.setAction(FloatingWindowService.ACTION_HIDE);
            startService(hidePill);
        } catch (Exception ignored) {}
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        try {
            // 消除顶部状态栏黑边（使内容顶格显示到 y=0），同时保留左侧原车系统控制栏（不设置 HIDE_NAVIGATION）
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            View decorView = getWindow().getDecorView();
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(flags);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 关键修复：离开前台时解除前台锁，允许后台悬浮服务在需要时挂载胶囊
        FloatingWindowService.isMainActivityInForeground = false;
        mainHandler.removeCallbacks(statusTicker);
    }

    @Override
    protected void onStop() {
        super.onStop();
        isForeground = false;
        FloatingWindowService.isMainActivityInForeground = false;
        
        // 核心防干扰门禁：绝不在 onStop 瞬间立即挂载悬浮窗！
        // 当用户在车机最左侧呼出原厂菜单时，MainActivity 会短暂触发 onStop。
        // 若立即 addView，顶层 Overlay 窗口会打断原厂系统抽屉导致菜单被顶掉退回为返回键！
        // 增加 1200ms 防抖延时：只有真正退回桌面或切换到外部全屏 App 稳定后，才平滑唤起悬浮胶囊！
        if (pendingShowPillRunnable != null) {
            mainHandler.removeCallbacks(pendingShowPillRunnable);
        }
        pendingShowPillRunnable = new Runnable() {
            // 2026-09-18 用户实测：呼出车机自带菜单浏览数秒后被弹回 = 本防抖到期时菜单仍在最前台，
            // addView 顶层 Overlay 直接把菜单顶掉。修法：挂载前先判定前台归属——
            // ① 原厂系统界面（SystemUI / 非桌面的 ecarx 组件）在前台 → 不挂载，1.5s 后重查（带总量上限）；
            // ② 桌面在前台 → 首次到点先二次确认（下一拍仍为桌面才挂），防菜单收起瞬间的假"桌面"误判；
            // ③ 第三方应用 / 检测不到（无使用情况权限且 dumpsys 熔断）→ 维持旧行为直接挂载。
            private int attempts = 0;
            private boolean homeConfirmPending = false;

            private void repost() {
                attempts++;
                if (attempts <= 10) {
                    mainHandler.postDelayed(this, 1500);
                } else {
                    AppLogger.i("悬浮胶囊", "连续多拍检测到系统界面在前台，本轮放弃自动挂载，等待下次回到前台再触发");
                }
            }

            @Override
            public void run() {
                try {
                    if (isForeground) {
                        return; // 回到工具箱自身前台：不需要胶囊
                    }
                    android.content.SharedPreferences sp = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                    if (!sp.getBoolean("floating_enabled", false)) {
                        return;
                    }
                    String fg = ForegroundAppDetector.getForegroundPackage(MainActivity.this);
                    boolean isHome = !android.text.TextUtils.isEmpty(fg)
                            && ForegroundAppDetector.isHomePackage(MainActivity.this, fg);
                    boolean isSelf = getPackageName().equals(fg);
                    // isSelf：熄屏/浅待机时使用统计常返回「最后使用的自己」——若真在前台则 isForeground 必为 true，
                    // 走不到这里；故视为不可见读数，等待重查，杜绝往锁屏/未知界面上挂胶囊（2026-09-18 模拟器实测发现）
                    boolean isSystemUi = "com.android.systemui".equals(fg);
                    boolean isEcarxNonHome = fg != null && fg.startsWith("com.ecarx.") && !isHome;
                    if (isSelf || isSystemUi || isEcarxNonHome) {
                        // 原厂菜单/抽屉/系统面板仍在最前台：绝不 addView 顶菜单，稍后重查
                        repost();
                        return;
                    }
                    if (isHome && !homeConfirmPending) {
                        // 首拍到桌面：多等一拍确认菜单不是恰好收在中转态
                        homeConfirmPending = true;
                        repost();
                        return;
                    }
                    Intent showPill = new Intent(MainActivity.this, FloatingWindowService.class);
                    showPill.setAction(FloatingWindowService.ACTION_SHOW);
                    startService(showPill);
                    AppLogger.i("悬浮胶囊", "已稳定处于外部桌面或第三方应用，平滑挂载悬浮小胶囊");
                } catch (Exception ignored) {}
            }
        };
        mainHandler.postDelayed(pendingShowPillRunnable, 1200);
    }

    private final BroadcastReceiver packageChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getData() != null) {
                String pkg = intent.getData().getSchemeSpecificPart();
                if ("com.maxmpz.equalizer".equals(pkg)) {
                    AppLogger.i("应用权限", "检测到 Poweramp 均衡器安装");
                }
            }
            if (webView != null) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (webView != null) {
                            webView.evaluateJavascript("if(window.onPackageChanged) window.onPackageChanged();", null);
                        }
                    }
                });
            }
        }
    };

    @Override
    public void onBackPressed() {
        try {
            AppLogger.i("系统导航", "按返回键安全退回桌面，避免任务栈回弹原厂多媒体");
            moveTaskToBack(true);
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
        } catch (Throwable t) {
            super.onBackPressed();
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        try {
            int currentNightMode = newConfig.uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            final boolean isNight = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            AppLogger.i("系统状态", "昼夜模式平滑切换: isNight=" + isNight + "，无感刷新无需重启 Activity");
            if (webView != null) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (webView != null) {
                            webView.evaluateJavascript("if(window.onSystemNightModeChanged) window.onSystemNightModeChanged(" + isNight + ");", null);
                        }
                    }
                });
            }
        } catch (Throwable t) {
            AppLogger.e("系统状态", "onConfigurationChanged 异常", t);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(networkChangeReceiver);
        } catch (Exception ignored) {}
        try {
            unregisterReceiver(volumeChangeReceiver);
        } catch (Exception ignored) {}
        try {
            unregisterReceiver(packageChangeReceiver);
        } catch (Exception ignored) {}
        if (webServer != null) {
            webServer.stop();
        }
    }

    public void showToast(final String msg) {
        showToast(msg, "info");
    }

    public void showToast(final String msg, final String kind) {
        if (isFinishing()) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (webView != null) {
                    try {
                        String k = (kind != null) ? kind : "info";
                        String script = "if(window.showToast){window.showToast(" + org.json.JSONObject.quote(msg) + ",'" + k + "');}";
                        webView.evaluateJavascript(script, null);
                        return;
                    } catch (Exception ignored) {}
                }
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void pushRabbitProgress(final org.json.JSONObject data) {
        if (isFinishing() || webView == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    webView.evaluateJavascript(
                        "if(window.updateRabbitProgress){window.updateRabbitProgress(" + data.toString() + ");}", null);
                } catch (Exception ignored) {}
            }
        });
    }

    private void checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                }, REQ_CODE_STORAGE);
            } else {
                notifyNativePermissionReady();
            }
        } else {
            notifyNativePermissionReady();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CODE_STORAGE) {
            notifyNativePermissionReady();
        }
    }

    private void notifyNativePermissionReady() {
        if (webView != null) {
            webView.post(() -> {
                webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('native-permission-ready'));", null);
            });
        }
    }

    /** 顶栏设备信息推送线程（绝不在主线程做 getprop / 包状态查询，否则整机 UI 卡死数秒） */
    private static final java.util.concurrent.ExecutorService DEVICE_INFO_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    /** 车载网络外设专属异步队列（严格隔离 JSBridge 线程与 UI 主线程，防止底层网络 Shell 阻塞界面） */
    private static final java.util.concurrent.ExecutorService NETWORK_WORKER =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile boolean deviceInfoPushing = false;
    /** 屏幕参数进程级缓存：软件每次启动只真实采集一次，之后推送复用（2026-09-18 定案，修复「屏幕采集」日志无限刷屏） */
    private org.json.JSONObject screenInfoCache = null;

    /**
     * 真实屏幕参数采集（wm size/density + DisplayMetrics 真实值，消灭写死魔数）。
     * 加锁保证「采集 + 落日志」全局恰好一次：首次调用真实执行并记一条「屏幕采集」日志，
     * 之后所有调用方（pushDeviceInfoToWeb / getDeviceInfo）直接复用缓存，不再刷日志。
     */
    private org.json.JSONObject ensureScreenInfoOnce(String ver) {
        synchronized (this) {
            if (screenInfoCache == null) {
                org.json.JSONObject fresh = collectScreenInfo();
                if (fresh != null) {
                    screenInfoCache = fresh;
                    try {
                        AppLogger.i("屏幕采集", "启动首次采集 wm: " + fresh.optString("screen_wm")
                            + " | 实测: " + fresh.optString("screen_real_size")
                            + " | density: " + fresh.optString("screen_density")
                            + " | appBounds: " + fresh.optString("screen_app_bounds")
                            + " | version: " + ver);
                    } catch (Exception ignored) {}
                }
            }
            return screenInfoCache;
        }
    }

    /** 只做一次真实采集（shell + Display 查询），不落日志；失败返回 null 由调用方下次重试 */
    private org.json.JSONObject collectScreenInfo() {
        try {
            org.json.JSONObject s = new org.json.JSONObject();
            String wmSize = SystemUtils.executeShell("wm size");
            String wmDensity = SystemUtils.executeShell("wm density");
            android.graphics.Point realSize = new android.graphics.Point();
            android.view.Display d = ((android.view.WindowManager) getSystemService(android.content.Context.WINDOW_SERVICE)).getDefaultDisplay();
            d.getRealSize(realSize);
            android.graphics.Rect appBounds = new android.graphics.Rect();
            d.getRectSize(appBounds);
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            d.getRealMetrics(dm);
            String sizeStr = "未知";
            if (wmSize != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)x(\\d+)").matcher(wmSize);
                if (m.find()) sizeStr = m.group(1) + "x" + m.group(2);
            }
            s.put("screen_size", sizeStr);
            s.put("screen_real_size", realSize.x + "x" + realSize.y);
            s.put("screen_density", dm.densityDpi + " dpi");
            s.put("screen_density_dpi", dm.densityDpi);
            s.put("screen_app_bounds", appBounds.width() + "x" + appBounds.height());
            s.put("screen_wm", (wmSize == null ? "" : wmSize.trim()) + (wmDensity == null ? "" : " | " + wmDensity.trim()));
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    public void pushDeviceInfoToWeb() {
        if (webView == null) return;
        if (deviceInfoPushing) return; // 避免重复排队堆积
        deviceInfoPushing = true;

        DEVICE_INFO_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                final String script;
                try {
                    SystemUtils.NetStatus net = SystemUtils.getNetworkStatus();
                    JSONObject obj = new JSONObject();
                    obj.put("ip", net.ip);
                    obj.put("net", net.toJson());
                    obj.put("dynamicCode", SystemUtils.calculateDynamicCode());
                    obj.put("dynamicCodePlus5", SystemUtils.calculateDynamicCodePlus5());
                    isWhitelistEnabled = SystemUtils.isApkVerifyWhitelistEnabled();
                    obj.put("whitelist", isWhitelistEnabled);
                    String currentVer = "1.3.8";
                    try {
                        currentVer = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                    } catch (Exception ignored) {}
                    obj.put("version", currentVer);
                    int batteryVolt = 126;
                    try {
                        android.content.Intent batIntent = registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
                        if (batIntent != null) {
                            int v = batIntent.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, -1);
                            if (v > 0) {
                                if (v > 1000) v = v / 100;
                                batteryVolt = v;
                            }
                        }
                    } catch (Exception ignored) {}
                    obj.put("battery_volt", batteryVolt);
                    boolean isMediaFrozen = (SystemUtils.getAppDetailedState(MainActivity.this, "com.ecarx.multimedia") == SystemUtils.APP_STATE_DISABLED) || 
                                           (SystemUtils.getAppDetailedState(MainActivity.this, "com.ecarx.xcmedia") == SystemUtils.APP_STATE_DISABLED);
                    boolean isAppstoreFrozen = (SystemUtils.getAppDetailedState(MainActivity.this, "com.ecarx.appstore") == SystemUtils.APP_STATE_DISABLED);
                    obj.put("multimedia_frozen", isMediaFrozen);
                    obj.put("appstore_frozen", isAppstoreFrozen);
                    android.content.SharedPreferences prefsSilentFreeze = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                    obj.put("silent_appstore_freeze", prefsSilentFreeze.getBoolean("silent_appstore_freeze", false));
                    obj.put("rabbit", ThemePatcher.getRabbitDisguiseInfo(MainActivity.this));
                    obj.put("rabbitPostReboot", ThemePatcher.checkRabbitPostRebootStatus(MainActivity.this));
                    android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                    obj.put("autostart", prefs.getBoolean("autostart_enabled", true));
                    obj.put("floating_enabled", prefs.getBoolean("floating_enabled", false));
                    obj.put("adb_master_switch", prefs.getBoolean(SystemUtils.KEY_ADB_MASTER_SWITCH, true));
                    obj.put("floating_display_mode", prefs.getString("floating_display_mode", "battery"));
                    obj.put("rabbit_safe_mode", prefs.getBoolean("rabbit_safe_mode_enabled", true));
                    obj.put("expert_rabbit_enabled", prefs.getBoolean("expert_rabbit_theme_enabled", false));
                    obj.put("app_icon_style", AppIconManager.getAppIconStyle(MainActivity.this));
                    obj.put("is_binyue_cool_model", AppIconManager.isBinyueCoolModel());
                    obj.put("has_system_settings", SystemUtils.isPackageInstalled(MainActivity.this, "com.android.settings"));
                    obj.put("is_car_device", isCarDevice(MainActivity.this));
                    try {
                        app.onepve.geelyconsole.hal.IVehiclePlatform currentPlat = VehiclePlatformFactory.getPlatform(MainActivity.this);
                        if (currentPlat != null) {
                            obj.put("platform_code", currentPlat.getPlatformCode());
                            obj.put("platform_name", currentPlat.getPlatformName());
                            obj.put("fuel_capacity", currentPlat.getDefaultFuelCapacityLiters());
                        }
                    } catch (Throwable ignored) {}
                    obj.put("fuel_percent", VehicleAutomationService.currentFuelPercent);
                    obj.put("fuel_liters", (double) VehicleAutomationService.currentFuelLiters);
                    obj.put("logPath", AppLogger.getLogFilePath());
                    obj.put("logSize", AppLogger.getLogFileSizeStr());
                    // 真实屏幕参数：每次启动只采一次，之后复用缓存（修「屏幕采集」日志无限刷屏）
                    org.json.JSONObject scr = ensureScreenInfoOnce(currentVer);
                    if (scr != null) {
                        obj.put("screen_size", scr.optString("screen_size"));
                        obj.put("screen_real_size", scr.optString("screen_real_size"));
                        obj.put("screen_density", scr.optString("screen_density"));
                        obj.put("screen_density_dpi", scr.optInt("screen_density_dpi"));
                        obj.put("screen_app_bounds", scr.optString("screen_app_bounds"));
                        obj.put("screen_wm", scr.optString("screen_wm"));
                    }
                    script = "if(window.updateDeviceInfo){window.updateDeviceInfo('" + obj.toString() + "');}";
                } catch (Exception e) {
                    deviceInfoPushing = false;
                    return;
                }
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        deviceInfoPushing = false;
                        try {
                            if (webView != null) {
                                webView.evaluateJavascript(script, null);
                            }
                        } catch (Exception ignored) {}
                    }
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CODE_SELECT_APK && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                handleSelectedApkForRabbit(uri);
            }
        } else if (requestCode == REQ_CODE_EXPORT_THEME && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                handleSelectedApkForExport(uri);
            }
        } else if (requestCode == REQ_CODE_SELECT_VOICE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null && currentSelectingVoiceKey != null) {
                handleSelectedVoiceAudio(uri, currentSelectingVoiceKey);
            }
        }
    }

    private void handleSelectedVoiceAudio(final Uri uri, final String voiceKey) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File voiceDir = new File(getFilesDir(), "custom_voices");
                    if (!voiceDir.exists()) voiceDir.mkdirs();
                    File dest = new File(voiceDir, voiceKey);
                    InputStream is = getContentResolver().openInputStream(uri);
                    if (is != null) {
                        FileOutputStream fos = new FileOutputStream(dest);
                        byte[] buf = new byte[32 * 1024];
                        int r;
                        while ((r = is.read(buf)) != -1) {
                            fos.write(buf, 0, r);
                        }
                        is.close();
                        fos.close();
                    }

                    android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                    prefs.edit().putString("custom_voice_" + voiceKey, dest.getAbsolutePath()).apply();

                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "自定义语音已成功替换！", Toast.LENGTH_SHORT).show();
                            if (webView != null) {
                                webView.loadUrl("javascript:refreshVehicleAutoUI()");
                            }
                        }
                    });
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "读取自定义语音失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void handleSelectedApkForRabbit(final Uri uri) {
        // 打开进度弹窗（inject 模式）
        try {
            org.json.JSONObject init = new org.json.JSONObject();
            init.put("show", true);
            init.put("mode", "inject");
            init.put("step", 0);
            init.put("progress", 0);
            init.put("stepMsg", "正在读取安装包...");
            init.put("done", false);
            init.put("error", false);
            init.put("showNaming", false);
            init.put("showOverwrite", false);
            init.put("themeCenterLaunched", false);
            pushRabbitProgress(init);
        } catch (Exception ignored) {}

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Step 1: 读取 APK
                    updateRabbitStep(1, 10, "正在读取安装包...");
                    File tempApk = new File(getCacheDir(), "selected_inject.apk");
                    InputStream is = getContentResolver().openInputStream(uri);
                    FileOutputStream fos = new FileOutputStream(tempApk);
                    byte[] buf = new byte[64 * 1024];
                    int read;
                    if (is != null) {
                        while ((read = is.read(buf)) != -1) {
                            fos.write(buf, 0, read);
                        }
                        is.close();
                    }
                    fos.close();

                    // Step 2: 安全校验
                    updateRabbitStep(2, 30, "正在执行核心系统防砖安全校验...");
                    final String pkgName;
                    String detectedPkg = null;
                    try {
                        PackageManager pm = getPackageManager();
                        PackageInfo pi = pm.getPackageArchiveInfo(tempApk.getAbsolutePath(), 0);
                        if (pi != null && pi.packageName != null) {
                            detectedPkg = pi.packageName;
                        }
                    } catch (Exception ignored) {}
                    pkgName = detectedPkg;
                    if (pkgName != null && (isDangerousSystemPackage(pkgName) || isProtectedCriticalPackage(pkgName))) {
                        updateRabbitError("【安全拦截】该 APK 为系统核心组件，禁止注入！");
                        return;
                    }

                    // Step 3: 伪装打包
                    updateRabbitStep(3, 55, "正在伪装打包成兔子时钟主题...");
                    // 直接构建临时主题（不走 AutoPilotManager，由本方法控制节奏）
                    final File stagedXtz = ThemePatcher.buildRabbitThemeToTemp(MainActivity.this, tempApk);
                    if (stagedXtz == null || !stagedXtz.exists()) {
                        updateRabbitError("主题包打包失败，请检查机身存储权限！");
                        return;
                    }

                    // Step 4: 完整性校验
                    updateRabbitStep(4, 85, "正在校验主题包完整性...");
                    if (!ThemePatcher.verifyRabbitThemeZip(stagedXtz, tempApk.length())) {
                        stagedXtz.delete();
                        updateRabbitError("主题包完整性校验失败，已自动清理，请重试！");
                        return;
                    }

                    // 完成：等用户点击「前往主题中心」
                    updateRabbitDone("inject", null);

                } catch (final Exception e) {
                    updateRabbitError("处理所选文件失败: " + e.getMessage());
                }
            }
        }).start();
    }

    private void updateRabbitStep(int step, int progress, String msg) {
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("step", step);
            d.put("progress", progress);
            d.put("stepMsg", msg);
            pushRabbitProgress(d);
        } catch (Exception ignored) {}
    }

    private void updateRabbitDone(String mode, String outputName) {
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("step", 4);
            d.put("progress", 100);
            d.put("stepMsg", "全部完成！");
            d.put("done", true);
            if (outputName != null) d.put("outputName", outputName);
            pushRabbitProgress(d);
        } catch (Exception ignored) {}
    }

    private void updateRabbitError(String msg) {
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("error", true);
            d.put("errorMsg", msg);
            d.put("stepMsg", "操作失败");
            pushRabbitProgress(d);
        } catch (Exception ignored) {}
    }

    private void handleSelectedApkForExport(final Uri uri) {
        // 先打开进度弹窗（export 模式，显示命名配置）
        try {
            org.json.JSONObject init = new org.json.JSONObject();
            init.put("show", true);
            init.put("mode", "export");
            init.put("step", 0);
            init.put("progress", 0);
            init.put("stepMsg", "正在读取安装包...");
            init.put("done", false);
            init.put("error", false);
            init.put("showNaming", false);
            init.put("showOverwrite", false);
            init.put("apkLabel", "");
            init.put("apkPkg", "");
            init.put("nameMode", "standard");
            init.put("nameLang", "cn");
            init.put("outputName", "clock.rabbit.xtz");
            pushRabbitProgress(init);
        } catch (Exception ignored) {}

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Step 1: 读取 APK
                    updateRabbitStep(1, 10, "正在读取安装包...");
                    File tempApk = new File(getCacheDir(), "export_source.apk");
                    InputStream is = getContentResolver().openInputStream(uri);
                    FileOutputStream fos = new FileOutputStream(tempApk);
                    byte[] buf = new byte[64 * 1024];
                    int read;
                    if (is != null) {
                        while ((read = is.read(buf)) != -1) {
                            fos.write(buf, 0, read);
                        }
                        is.close();
                    }
                    fos.close();

                    // Step 2: 安全校验 + 解析应用信息
                    updateRabbitStep(2, 30, "正在解析应用信息...");
                    String label = "";
                    String pkg = "";
                    try {
                        PackageManager pm = getPackageManager();
                        PackageInfo pi = pm.getPackageArchiveInfo(tempApk.getAbsolutePath(), 0);
                        if (pi != null && pi.packageName != null) {
                            pkg = pi.packageName;
                            if (isDangerousSystemPackage(pkg) || isProtectedCriticalPackage(pkg)) {
                                updateRabbitError("【安全拦截】该 APK 为系统核心组件，禁止打包！");
                                return;
                            }
                            // 读取中文标签
                            if (pi.applicationInfo != null) {
                                pi.applicationInfo.sourceDir = tempApk.getAbsolutePath();
                                pi.applicationInfo.publicSourceDir = tempApk.getAbsolutePath();
                                CharSequence cs = pi.applicationInfo.loadLabel(pm);
                                if (cs != null) label = cs.toString();
                            }
                        }
                    } catch (Exception ignored) {}

                    // 推送应用信息到前端，展示命名配置面板
                    try {
                        org.json.JSONObject info = new org.json.JSONObject();
                        info.put("apkLabel", label);
                        info.put("apkPkg", pkg);
                        info.put("showNaming", true);
                        info.put("stepMsg", "请选择输出文件名格式");
                        pushRabbitProgress(info);
                    } catch (Exception ignored) {}

                    // 暂存 tempApk 路径，等 confirmRabbitExport 回调继续
                    pendingExportStagedPath = tempApk.getAbsolutePath();

                } catch (final Exception e) {
                    updateRabbitError("处理所选文件失败: " + e.getMessage());
                }
            }
        }).start();
    }

    private void finishExport(File stagedXtz, File outXtz) {
        boolean moved = stagedXtz.renameTo(outXtz);
        if (!moved) {
            try (java.io.FileInputStream fin = new java.io.FileInputStream(stagedXtz);
                 java.io.FileOutputStream fout = new java.io.FileOutputStream(outXtz)) {
                byte[] cbuf = new byte[64 * 1024];
                int cr;
                while ((cr = fin.read(cbuf)) != -1) {
                    fout.write(cbuf, 0, cr);
                }
            } catch (Exception e) {
                updateRabbitError("写入下载目录失败：" + e.getMessage());
                return;
            }
            stagedXtz.delete();
            moved = outXtz.exists() && outXtz.length() > 0;
        }
        if (moved) {
            updateRabbitDone("export", outXtz.getName());
        } else {
            updateRabbitError("主题包写入下载目录失败，请检查存储权限！");
        }
    }

    // WebServer Callbacks
    @Override
    public void onUrlPushed(String url, String fileName) {}
    @Override
    public void onFileUploaded(final File file) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (file.getName().toLowerCase().endsWith(".zip")) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (VehicleVoicePlayer.isVoicePackZip(file)) {
                                String fName = file.getName();
                                int dot = fName.lastIndexOf('.');
                                final String themeName = (dot > 0) ? fName.substring(0, dot) : fName;
                                final int count = VehicleVoicePlayer.extractVoiceZip(file, themeName);
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (count > 0) {
                                            Toast.makeText(MainActivity.this, "🎉 成功导入语音包【" + themeName + "】(" + count + "个音频)，已就绪！", Toast.LENGTH_LONG).show();
                                            callJs("if(window.refreshVoiceThemes) window.refreshVoiceThemes();");
                                        } else {
                                            Toast.makeText(MainActivity.this, "收到快传文件: " + file.getName(), Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                            } else {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this, "收到手机快传文件: " + file.getName(), Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }
                    }).start();
                } else if (file.getName().toLowerCase().endsWith(".apk")) {
                    Toast.makeText(MainActivity.this, "收到安装包: " + file.getName() + " (" + SystemUtils.formatFileSize(file.length()) + ")，已保存至 Download", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, "收到手机快传文件: " + file.getName() + " (" + SystemUtils.formatFileSize(file.length()) + ")", Toast.LENGTH_LONG).show();
                }
            }
        });
    }
    @Override
    public void onActionRequested(final String action) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if ("open_files".equals(action)) {
                    SystemUtils.openFileManager(MainActivity.this);
                    Toast.makeText(MainActivity.this, "已通过手机端远程打开车机文件管理", Toast.LENGTH_SHORT).show();
                } else if ("hard_reboot".equals(action)) {
                    Toast.makeText(MainActivity.this, "收到手机端远程重启指令，正在执行...", Toast.LENGTH_SHORT).show();
                    SystemUtils.executeReboot(MainActivity.this);
                } else if ("open_settings".equals(action)) {
                    if (SystemUtils.openSystemSettings(MainActivity.this)) {
                        Toast.makeText(MainActivity.this, "已调起安卓原生系统设置", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "调起安卓原生设置失败", Toast.LENGTH_SHORT).show();
                    }
                } else if ("open_apps".equals(action)) {
                    if (SystemUtils.openApplicationSettings(MainActivity.this)) {
                        Toast.makeText(MainActivity.this, "已调起应用管理列表", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "调起应用管理列表失败", Toast.LENGTH_SHORT).show();
                    }
                } else if ("open_accessibility".equals(action)) {
                    if (SystemUtils.openAccessibilitySettings(MainActivity.this)) {
                        Toast.makeText(MainActivity.this, "已调起无障碍辅助设置", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "调起无障碍辅助设置失败", Toast.LENGTH_SHORT).show();
                    }
                } else if ("open_dev_settings".equals(action)) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        Toast.makeText(MainActivity.this, "已调起安卓系统开发者选项", Toast.LENGTH_SHORT).show();
                    } catch (Throwable t) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                String cmd = "am start -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS";
                                AdbClient.execute(MainActivity.this, cmd);
                            }
                        }).start();
                        Toast.makeText(MainActivity.this, "已指令调起开发者选项", Toast.LENGTH_SHORT).show();
                    }
                } else if ("open_wireless_settings".equals(action)) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        Toast.makeText(MainActivity.this, "已调起网络与热点设置", Toast.LENGTH_SHORT).show();
                    } catch (Throwable t) {
                        try {
                            Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            Toast.makeText(MainActivity.this, "已调起无线网络设置", Toast.LENGTH_SHORT).show();
                        } catch (Throwable t2) {
                            Toast.makeText(MainActivity.this, "调起网络设置失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else if ("clean_memory".equals(action)) {
                    int killed = SystemUtils.cleanBackgroundProcesses(MainActivity.this);
                    Toast.makeText(MainActivity.this, "后台已清理，释放运行内存 (关闭非核心后台: " + killed + "个)", Toast.LENGTH_SHORT).show();
                } else if ("edit_desktop_widget".equals(action)) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            IVehiclePlatform p = VehiclePlatformFactory.getPlatform(MainActivity.this);
                            if (p != null && "L".equals(p.getPlatformCode())) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this, "缤越L (Flyme Auto) 请直接在桌面长按或双指捏合添加/编辑卡片", Toast.LENGTH_LONG).show();
                                    }
                                });
                                return;
                            }
                            String cmd = "am start -n ecarx.launcher3/ecarx.launcher3.widget.WidgetEditActivity";
                            AdbClient.AdbResult adbRes = AdbClient.execute(MainActivity.this, cmd);
                            if (adbRes == null || (!adbRes.success && (adbRes.output == null || adbRes.output.isEmpty()))) {
                                SystemUtils.executeShell(cmd);
                            }
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "已调起车机桌面卡片编辑界面", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }).start();
                } else if ("freeze_appstore".equals(action)) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            SystemUtils.setPackageEnabled(MainActivity.this, "com.ecarx.appstore", false);
                            SystemUtils.clearAppsCache();
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "已成功冻结吉利应用商店，白名单永久锁定！(๑•̀ㅂ•́)و", Toast.LENGTH_LONG).show();
                                    pushDeviceInfoToWeb();
                                }
                            });
                        }
                    }).start();
                } else if ("unfreeze_appstore".equals(action)) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            SystemUtils.setPackageEnabled(MainActivity.this, "com.ecarx.appstore", true);
                            SystemUtils.clearAppsCache();
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "已解冻恢复吉利应用商店", Toast.LENGTH_LONG).show();
                                    pushDeviceInfoToWeb();
                                }
                            });
                        }
                    }).start();
                } else if ("enable_whitelist".equals(action)) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            boolean ok = SystemUtils.enableApkVerifyWhitelist(MainActivity.this);
                            isWhitelistEnabled = ok;
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (ok) {
                                        Toast.makeText(MainActivity.this, "车机安装白名单已成功放行 (sys.jsbd.apk_verify = 1) (๑•̀ㅂ•́)و", Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(MainActivity.this, "放行白名单执行完毕，请核对状态", Toast.LENGTH_SHORT).show();
                                    }
                                    pushDeviceInfoToWeb();
                                }
                            });
                        }
                    }).start();
                }
            }
        });
    }
    @Override
    public void onAdbCommandPushed(final String command, final boolean autoExec) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (autoExec) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            String cmd = command != null ? command.trim() : "";
                            if (cmd.startsWith("adb shell ")) {
                                cmd = cmd.substring("adb shell ".length()).trim();
                            } else if (cmd.startsWith("adb ")) {
                                cmd = cmd.substring("adb ".length()).trim();
                            }
                            if (!cmd.isEmpty()) {
                                AdbClient.AdbResult adbRes = AdbClient.execute(MainActivity.this, cmd);
                                if (adbRes == null || (!adbRes.success && (adbRes.output == null || adbRes.output.isEmpty()))) {
                                    SystemUtils.executeShell(cmd);
                                }
                            }
                        }
                    }).start();
                }

                if (webView != null) {
                    webView.evaluateJavascript("if(window.onAdbCommandPushedFromPhone) window.onAdbCommandPushedFromPhone(" + JSONObject.quote(command) + ", " + autoExec + ");", null);
                }
                String tip = autoExec ? "收到手机推送的 ADB 指令，已自动执行！" : "收到手机推送的 ADB 指令，已自动填入，请核对后执行！";
                Toast.makeText(MainActivity.this, tip, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // JSBridge
    public class ToolboxBridge {
        private final Context context;

        public ToolboxBridge(Context context) {
            this.context = context;
        }

        // ============================================================================
        // safeCall / safeRun — @JavascriptInterface 方法统一安全包装。
        // 收敛全类几十处雷同的样板 try/catch：String 型方法失败时回退字面量并记日志；
        // void 型方法静默吞异常（保持既有「尽力而为、不打扰」语义）。
        // 仅包裹整方法体的单一 try/catch 场景；带自定义 catch 逻辑的方法保持原样。
        // ============================================================================
        private interface BridgeAction { String run() throws Exception; }

        private interface BridgeTask { void run() throws Exception; }

        private String safeCall(String fallback, BridgeAction action) {
            try {
                return action.run();
            } catch (Exception e) {
                Log.w(TAG, "ToolboxBridge 调用失败，返回兜底: " + fallback, e);
                return fallback;
            }
        }

        private void safeRun(BridgeTask task) {
            try {
                task.run();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void dismissSplash() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    hideSplashView();
                }
            });
        }

        /**
         * 前端主题切换时同步当前主题强调色至原生侧（供桌面小组件等原生组件动态着色）
         */
        @JavascriptInterface
        public void syncTheme(final String accentHex) {
            safeRun(new BridgeTask() {
                @Override
                public void run() {
                    app.onepve.geelyconsole.utils.ThemeColorHelper.setAccentColor(context, accentHex);
                }
            });
        }

        @JavascriptInterface
        public String getDeviceInfo() {
            try {
                SystemUtils.NetStatus net = SystemUtils.getNetworkStatus();
                JSONObject obj = net.toJson();
                obj.put("ip", net.ip);
                obj.put("car_ip", net.ip);
                obj.put("net_type", net.typeName);
                obj.put("is_wifi", net.isWifiOrLan);
                obj.put("is_cellular", net.isCellular);
                obj.put("dynamicCode", SystemUtils.calculateDynamicCode());
                obj.put("dynamicCodePlus5", SystemUtils.calculateDynamicCodePlus5());
                obj.put("whitelist", SystemUtils.isApkVerifyWhitelistEnabled());
                String ver = "1.4.1";
                try {
                    ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                } catch (Exception ignored) {}
                obj.put("version", ver);
                boolean isMediaFrozen = (SystemUtils.getAppDetailedState(MainActivity.this, "com.ecarx.multimedia") == SystemUtils.APP_STATE_DISABLED) || 
                                       (SystemUtils.getAppDetailedState(MainActivity.this, "com.ecarx.xcmedia") == SystemUtils.APP_STATE_DISABLED);
                boolean isAppstoreFrozen = (SystemUtils.getAppDetailedState(MainActivity.this, "com.ecarx.appstore") == SystemUtils.APP_STATE_DISABLED);
                obj.put("multimedia_frozen", isMediaFrozen);
                obj.put("appstore_frozen", isAppstoreFrozen);
                try {
                    app.onepve.geelyconsole.hal.IVehiclePlatform currentPlat = VehiclePlatformFactory.getPlatform(MainActivity.this);
                    if (currentPlat != null) {
                        obj.put("platform_code", currentPlat.getPlatformCode());
                        obj.put("platform_name", currentPlat.getPlatformName());
                        obj.put("fuel_capacity", currentPlat.getDefaultFuelCapacityLiters());
                    }
                } catch (Throwable ignored) {}
                // 真实屏幕参数：每次启动只采一次，之后复用缓存（与 pushDeviceInfoToWeb 同步）
                org.json.JSONObject scr = ensureScreenInfoOnce(ver);
                if (scr != null) {
                    obj.put("screen_size", scr.optString("screen_size"));
                    obj.put("screen_real_size", scr.optString("screen_real_size"));
                    obj.put("screen_density", scr.optString("screen_density"));
                    obj.put("screen_density_dpi", scr.optInt("screen_density_dpi"));
                    obj.put("screen_app_bounds", scr.optString("screen_app_bounds"));
                    obj.put("screen_wm", scr.optString("screen_wm"));
                }
                android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                obj.put("silent_appstore_freeze", prefs.getBoolean("silent_appstore_freeze", false));
                boolean isBeta = ver.toLowerCase().contains("beta");
                obj.put("is_beta", isBeta);
                obj.put("autostart", prefs.getBoolean("autostart_enabled", true));
                obj.put("floating_enabled", prefs.getBoolean("floating_enabled", false));
                obj.put("adb_master_switch", prefs.getBoolean(SystemUtils.KEY_ADB_MASTER_SWITCH, true));
                obj.put("floating_display_mode", prefs.getString("floating_display_mode", "battery"));
                obj.put("expert_rabbit_enabled", prefs.getBoolean("expert_rabbit_theme_enabled", false));
                float batteryVolt = VehicleAutomationService.latestBatteryVoltage;
                if (batteryVolt < 9.0f || batteryVolt > 16.5f) {
                    batteryVolt = prefs.getFloat("vehicle_real_battery_volt", 0.0f);
                }
                if (batteryVolt < 9.0f || batteryVolt > 16.5f) {
                    batteryVolt = 0.0f;
                    try {
                        prefs.edit().putFloat("vehicle_real_battery_volt", 0.0f).apply();
                    } catch (Exception ignored) {}
                }
                obj.put("real_battery_volt", batteryVolt > 0 ? (double)batteryVolt : 0.0);
                obj.put("battery_volt", batteryVolt > 0 ? (int)(batteryVolt * 10) : 0);
                obj.put("door_fl", VehicleAutomationService.currentDoorFL);
                obj.put("door_fr", VehicleAutomationService.currentDoorFR);
                obj.put("door_rl", VehicleAutomationService.currentDoorRL);
                obj.put("door_rr", VehicleAutomationService.currentDoorRR);
                obj.put("door_trunk", VehicleAutomationService.currentTrunk);
                return obj.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public String getDoorStatus() {
            return safeCall("{}", () -> {
                JSONObject obj = new JSONObject();
                obj.put("fl", VehicleAutomationService.currentDoorFL);
                obj.put("fr", VehicleAutomationService.currentDoorFR);
                obj.put("rl", VehicleAutomationService.currentDoorRL);
                obj.put("rr", VehicleAutomationService.currentDoorRR);
                obj.put("trunk", VehicleAutomationService.currentTrunk);
                obj.put("gear", VehicleAutomationService.lastGearPos);
                obj.put("mode", VehicleAutomationService.lastDriveMode);
                return obj.toString();
            });
        }

        @JavascriptInterface
        public String getDynamicCode() {
            return SystemUtils.calculateDynamicCode();
        }

        @JavascriptInterface
        public String getDynamicCodePlus5() {
            return SystemUtils.calculateDynamicCodePlus5();
        }

        @JavascriptInterface
        public String getNetworkStatusJson() {
            return SystemUtils.getNetworkStatus().toJson().toString();
        }

        @JavascriptInterface
        public void saveTerminalLog(final String logText) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        File downloadDir = new File(Environment.getExternalStorageDirectory(), "Download");
                        if (!downloadDir.exists()) {
                            downloadDir.mkdirs();
                        }
                        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                        File logFile = new File(downloadDir, "adb_terminal_" + timeStamp + ".log");
                        try (FileOutputStream fos = new FileOutputStream(logFile)) {
                            fos.write((logText != null ? logText : "").getBytes(StandardCharsets.UTF_8));
                        }
                        Toast.makeText(context, "终端日志已保存至: " + logFile.getAbsolutePath() + " (" + logFile.length() + " 字节)", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(context, "保存日志失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public String getCarIp() {
            String ip = SystemUtils.getCarIpAddress();
            return ip != null ? ip : "127.0.0.1";
        }

        @JavascriptInterface
        public void openDialer() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent intent = new Intent(Intent.ACTION_DIAL);
                        intent.setData(Uri.parse("tel:"));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(context, "调起拨号盘失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void prepareThemeAssets() {
            ThemePatcher.prepareThemeAssetsAsync(MainActivity.this);
        }

        @JavascriptInterface
        public String scanApksForRabbit() {
            return safeCall("[]", () -> {
                List<File> apks = AutoPilotManager.scanDownloadedApks(MainActivity.this);
                JSONArray arr = new JSONArray();
                PackageManager pm = context.getPackageManager();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
                for (File apk : apks) {
                    if (apk == null || !apk.exists() || apk.length() == 0) continue;
                    JSONObject obj = new JSONObject();
                    obj.put("filename", apk.getName());
                    obj.put("path", apk.getAbsolutePath());
                    obj.put("sizeBytes", apk.length());
                    obj.put("sizeStr", String.format(Locale.CHINA, "%.1f MB", apk.length() / (1024.0 * 1024.0)));
                    obj.put("lastModified", apk.lastModified());
                    obj.put("timeStr", sdf.format(new Date(apk.lastModified())));

                    String label = apk.getName();
                    String pkg = "";
                    String verName = "";
                    int verCode = 0;
                    try {
                        PackageInfo pi = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
                        if (pi != null) {
                            pkg = (pi.packageName != null) ? pi.packageName : "";
                            verName = (pi.versionName != null) ? pi.versionName : "";
                            verCode = pi.versionCode;
                            if (pi.applicationInfo != null) {
                                pi.applicationInfo.sourceDir = apk.getAbsolutePath();
                                pi.applicationInfo.publicSourceDir = apk.getAbsolutePath();
                                CharSequence cs = pm.getApplicationLabel(pi.applicationInfo);
                                if (cs != null && cs.length() > 0) {
                                    label = cs.toString();
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    if (label.equals(apk.getName())) {
                        String nameLower = apk.getName().toLowerCase();
                        if (nameLower.contains("automap") || nameLower.contains("amap")) {
                            label = "高德地图车机版";
                        } else if (nameLower.contains("baidu") || nameLower.contains("baidumap")) {
                            label = "百度地图车机版";
                        }
                    }

                    boolean isNavi = isNavigationApp(pkg, apk.getName());
                    obj.put("name", label);
                    obj.put("packageName", pkg);
                    obj.put("versionName", verName);
                    obj.put("versionCode", verCode);
                    obj.put("isNavigation", isNavi);
                    arr.put(obj);
                }
                return arr.toString();
            });
        }

        @JavascriptInterface
        public void startAutoPilotInject(final String filename) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (filename == null || filename.trim().isEmpty()) {
                        Toast.makeText(context, "请先在界面上选择需要注入的安装包！", Toast.LENGTH_LONG).show();
                        return;
                    }

                    File targetFile = null;
                    String cleanName = filename.trim();
                    File directFile = new File(cleanName);
                    if (directFile.exists() && directFile.isFile() && directFile.length() > 0) {
                        targetFile = directFile;
                    } else {
                        // 在全量扫描目录中寻找同名文件
                        List<File> allApks = AutoPilotManager.scanDownloadedApks(MainActivity.this);
                        for (File f : allApks) {
                            if (f != null && (f.getName().equalsIgnoreCase(cleanName) || f.getAbsolutePath().equalsIgnoreCase(cleanName))) {
                                targetFile = f;
                                break;
                            }
                        }
                    }

                    if (targetFile == null) {
                        Toast.makeText(context, "未找到安装包文件: " + cleanName + "，请先下载或传输至机身！", Toast.LENGTH_LONG).show();
                        return;
                    }

                    final File finalTarget = targetFile;
                    AutoPilotManager.getInstance().startAutoInject(MainActivity.this, finalTarget, new AutoPilotManager.AutoPilotCallback() {
                        @Override
                        public void onSuccess(final String apkName) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    pushDeviceInfoToWeb();
                                }
                            });
                        }

                        @Override
                        public void onError(final String error) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    pushDeviceInfoToWeb();
                                }
                            });
                        }
                    });
                }
            });
        }

        @JavascriptInterface
        public void injectApkDirectly(final String filename) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (filename == null || filename.trim().isEmpty()) {
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(context, "请先在界面上选择需要注入的安装包！", Toast.LENGTH_LONG).show();
                                        }
                                    });
                                    return;
                                }

                                File dedicatedDir = SystemUtils.getAppDownloadDir();
                                File legacyDir = new File(Environment.getExternalStorageDirectory(), "Download");
                                File apkFile = null;
                                File f1 = new File(dedicatedDir, filename.trim());
                                File f2 = new File(legacyDir, filename.trim());
                                if (f1.exists() && f1.length() > 0) {
                                    apkFile = f1;
                                } else if (f2.exists() && f2.length() > 0) {
                                    apkFile = f2;
                                }

                                if (apkFile == null) {
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(context, "未找到目标安装包: " + filename.trim() + "，请先下载！", Toast.LENGTH_LONG).show();
                                        }
                                    });
                                    return;
                                }

                                String pkg = null;
                                try {
                                    PackageManager pm = context.getPackageManager();
                                    PackageInfo pi = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
                                    if (pi != null && pi.packageName != null) {
                                        pkg = pi.packageName;
                                        if (isDangerousSystemPackage(pkg) || isProtectedCriticalPackage(pkg)) {
                                            final String dangerousPkg = pkg;
                                            mainHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    showToast("【安全拦截】检测到该 APK 为系统核心组件（" + dangerousPkg + "），禁止伪装注入！");
                                                }
                                            });
                                            return;
                                        }
                                    }
                                } catch (Exception ignored) {}

                                final boolean ok = ThemePatcher.packageToRabbitTheme(context, apkFile);
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (ok) {
                                            pushDeviceInfoToWeb();
                                            showToast("兔子时钟伪装主题注入成功！请打开车机主题中心应用『兔子时钟』，应用后重启车机。");
                                        } else {
                                            showToast("兔子时钟注入失败，请检查机身存储权限");
                                        }
                                    }
                                });
                            } catch (Exception e) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(context, "注入异常: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        }
                    }).start();
                }
            });
        }

        @JavascriptInterface
        public boolean setRabbitSafeModeEnabled(boolean enabled) {
            android.content.SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("rabbit_safe_mode_enabled", enabled).apply();
            pushDeviceInfoToWeb();
            return enabled;
        }

        @JavascriptInterface
        public boolean setExpertRabbitThemeEnabled(boolean enabled) {
            android.content.SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("expert_rabbit_theme_enabled", enabled).apply();
            pushDeviceInfoToWeb();
            return enabled;
        }

        @JavascriptInterface
        public boolean isSystemSettingsInstalled() {
            return SystemUtils.isPackageInstalled(context, "com.android.settings");
        }

        @JavascriptInterface
        public boolean setAutostartEnabled(boolean enabled) {
            android.content.SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("autostart_enabled", enabled).apply();
            pushDeviceInfoToWeb();
            return enabled;
        }

        @JavascriptInterface
        public void onPostRebootHandled() {
            try {
                android.content.SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                if (prefs.getBoolean("temp_autostart_for_rabbit", false)) {
                    boolean savedPref = prefs.getBoolean("saved_user_autostart_pref", false);
                    prefs.edit()
                            .putBoolean("autostart_enabled", savedPref)
                            .putBoolean("temp_autostart_for_rabbit", false)
                            .apply();
                    pushDeviceInfoToWeb();
                }
            } catch (Exception ignored) {}
            try {
                android.content.SharedPreferences rPrefs = context.getSharedPreferences("rabbit_theme_prefs", Context.MODE_PRIVATE);
                rPrefs.edit().putBoolean("pending_install_after_reboot", false).apply();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public String restoreOfficialTheme() {
            try {
                JSONObject res = ThemePatcher.restoreOfficialRabbitTheme(context);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        pushDeviceInfoToWeb();
                    }
                });
                return res.toString();
            } catch (Exception e) {
                return "{\"success\":false,\"message\":\"" + e.getMessage() + "\"}";
            }
        }

        @JavascriptInterface
        public String getRecentLogs(int lines) {
            return AppLogger.readRecentLogs(lines <= 0 ? 300 : lines);
        }

        @JavascriptInterface
        public String getLogContent() {
            return getRecentLogs(300);
        }

        @JavascriptInterface
        public boolean toggleAppstoreFreeze() {
            boolean currentFrozen = (SystemUtils.getAppDetailedState(MainActivity.this, "com.ecarx.appstore") == SystemUtils.APP_STATE_DISABLED);
            return toggleFreezeAppStore(!currentFrozen);
        }

        @JavascriptInterface
        public boolean clearRunLog() {
            return AppLogger.clearLog();
        }

        @JavascriptInterface
        public String getLogInfo() {
            return safeCall("{\"path\":\"/sdcard/Download/geely_toolbox.log\",\"size\":\"0 KB\"}", () -> {
                JSONObject obj = new JSONObject();
                obj.put("path", AppLogger.getLogFilePath());
                obj.put("size", AppLogger.getLogFileSizeStr());
                return obj.toString();
            });
        }

        @JavascriptInterface
        public String getLogModuleSwitches() {
            return AppLogger.getAllModuleSwitchesJson();
        }

        @JavascriptInterface
        public boolean setLogModuleSwitch(String module, boolean enabled) {
            AppLogger.setModuleEnabled(module, enabled);
            return true;
        }

        @JavascriptInterface
        public void selectApkInject() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("*/*");
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        startActivityForResult(Intent.createChooser(intent, "选择要注入的高德或应用 APK"), REQ_CODE_SELECT_APK);
                    } catch (Exception e) {
                        Toast.makeText(context, "打开文件选择器失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void generateRabbitThemeOnly() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("*/*");
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        startActivityForResult(Intent.createChooser(intent, "选择要打包成主题包的 APK"), REQ_CODE_EXPORT_THEME);
                    } catch (Exception e) {
                        Toast.makeText(context, "打开文件选择器失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void confirmRabbitExport(final String outputName) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        File tempApk = new File(pendingExportStagedPath != null ? pendingExportStagedPath : "");
                        if (!tempApk.exists()) {
                            updateRabbitError("安装包缓存已失效，请重新选择！");
                            return;
                        }
                        pendingExportOutputName = outputName;

                        // Step 3: 打包
                        updateRabbitStep(3, 55, "正在伪装打包成兔子时钟主题...");
                        File stagedXtz = ThemePatcher.buildRabbitThemeToTemp(MainActivity.this, tempApk);
                        if (stagedXtz == null || !stagedXtz.exists()) {
                            updateRabbitError("主题包打包失败，请检查机身存储权限！");
                            return;
                        }

                        // Step 4: 校验
                        updateRabbitStep(4, 85, "正在校验主题包完整性...");
                        if (!ThemePatcher.verifyRabbitThemeZip(stagedXtz, tempApk.length())) {
                            stagedXtz.delete();
                            updateRabbitError("主题包完整性校验失败，已自动清理，请重试！");
                            return;
                        }

                        // 检查目标文件是否已存在
                        File downloadDir = new File(android.os.Environment.getExternalStorageDirectory(), "Download");
                        if (!downloadDir.exists()) downloadDir.mkdirs();
                        File outXtz = new File(downloadDir, outputName);
                        if (outXtz.exists()) {
                            // 推送覆盖确认到前端
                            try {
                                org.json.JSONObject d = new org.json.JSONObject();
                                d.put("showOverwrite", true);
                                d.put("outputName", outputName);
                                pushRabbitProgress(d);
                            } catch (Exception ignored) {}
                            // 暂存 stagedXtz 路径，等 confirmRabbitExportOverwrite 回调
                            pendingExportStagedPath = stagedXtz.getAbsolutePath();
                            return;
                        }

                        // 无冲突，直接写入
                        finishExport(stagedXtz, outXtz);

                    } catch (Exception e) {
                        updateRabbitError("导出失败：" + e.getMessage());
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public void confirmRabbitExportOverwrite() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        File stagedXtz = new File(pendingExportStagedPath != null ? pendingExportStagedPath : "");
                        if (!stagedXtz.exists()) {
                            updateRabbitError("临时主题包已失效，请重新操作！");
                            return;
                        }
                        File downloadDir = new File(android.os.Environment.getExternalStorageDirectory(), "Download");
                        File outXtz = new File(downloadDir, pendingExportOutputName != null ? pendingExportOutputName : "clock.rabbit.xtz");
                        if (outXtz.exists()) outXtz.delete();
                        finishExport(stagedXtz, outXtz);
                    } catch (Exception e) {
                        updateRabbitError("覆盖导出失败：" + e.getMessage());
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public void launchThemeCenterForRabbit() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    // 调起主题中心并启动 30 秒延迟替换
                    AutoPilotManager.getInstance().scheduleThemeCenterAndCommit(MainActivity.this);
                }
            });
        }

        @JavascriptInterface
        public void openThemeManager() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    boolean ok = ThemePatcher.openRabbitThemeSetting(context);
                    if (!ok) {
                        Toast.makeText(context, "未找到车机主题应用，请在原生应用列表打开", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        /**
         * 车机内建屏保调用（无需 ADB / root）——原厂固件逆推实证：
         *
         * 下拉快捷栏（QS / 负一屏）里的「屏保」磁贴 = ecarx.settings 的
         * ecarx.settings.vehicle.setting.widget.ScreenSaverWidget
         * （createAttr().setAction("SpecialScreenSaver")，名称资源 R.string.widget_screen_saver）；
         * 其基类 BaseNegativeOneScreenWidget.onReceive() 命中 "SpecialScreenSaver" 后执行：
         *     Intent i = new Intent("android.intent.action.SCREENSAVER");
         *     i.addCategory("android.intent.category.SCREENSAVER");
         *     i.setPackage("com.ecarx.screensaver");
         *     context.startService(i);
         * 目标为 com.ecarx.screensaver/.ScreensaverService（清单中 exported，带
         * action/category = SCREENSAVER 的 intent-filter），即原厂屏保宿主。
         * 本方法 1:1 复刻该链路。
         */
        @JavascriptInterface
        public String startScreenSaver() {
            StringBuilder sb = new StringBuilder();

            // 1. 主路径：原厂同款 —— startService 调起 com.ecarx.screensaver
            try {
                Intent i = new Intent("android.intent.action.SCREENSAVER");
                i.addCategory("android.intent.category.SCREENSAVER");
                i.setPackage("com.ecarx.screensaver");
                context.startService(i);
                AppLogger.action("车机屏保", "调起原厂屏保 (startService SCREENSAVER)", true, "com.ecarx.screensaver");
                return "[OK] startService(SCREENSAVER) -> com.ecarx.screensaver";
            } catch (Throwable e) {
                sb.append("[FAIL] startService: ").append(e.getClass().getSimpleName()).append(' ').append(e.getMessage()).append('\n');
                AppLogger.action("车机屏保", "startService 调起屏保失败", false, String.valueOf(e.getMessage()));
            }

            // 2. 兜底：启动屏保应用主界面
            try {
                Intent i2 = new Intent();
                i2.setComponent(new android.content.ComponentName("com.ecarx.screensaver", "com.ecarx.screensaver.MainActivity"));
                i2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i2);
                AppLogger.action("车机屏保", "调起屏保 (MainActivity)", true, "com.ecarx.screensaver/.MainActivity");
                return sb.append("[OK] 已启动 com.ecarx.screensaver/.MainActivity").toString();
            } catch (Throwable e2) {
                sb.append("[FAIL] MainActivity: ").append(e2.getClass().getSimpleName()).append(' ').append(e2.getMessage());
                AppLogger.action("车机屏保", "屏保 MainActivity 调起失败", false, String.valueOf(e2.getMessage()));
            }

            return sb.toString();
        }

        /**
         * 退出车机屏保：原厂为 launcher 向 com.ecarx.screensaver 发送
         * ecarx.intent.action.QUIT_SCREENSAVER_VIEW（同样以服务/广播形式投递）。
         */
        @JavascriptInterface
        public String stopScreenSaver() {
            StringBuilder sb = new StringBuilder();
            try {
                Intent i = new Intent("ecarx.intent.action.QUIT_SCREENSAVER_VIEW");
                i.setPackage("com.ecarx.screensaver");
                context.startService(i);
                AppLogger.action("车机屏保", "退出原厂屏保 (startService QUIT_SCREENSAVER_VIEW)", true, "com.ecarx.screensaver");
                return "[OK] startService(QUIT_SCREENSAVER_VIEW)";
            } catch (Throwable e) {
                sb.append("[FAIL] startService: ").append(e.getClass().getSimpleName()).append(' ').append(e.getMessage()).append('\n');
            }
            try {
                Intent i2 = new Intent("ecarx.intent.action.QUIT_SCREENSAVER_VIEW");
                i2.setPackage("com.ecarx.screensaver");
                sendBroadcast(i2);
                return sb.append("[OK] 已发送广播 QUIT_SCREENSAVER_VIEW").toString();
            } catch (Throwable e2) {
                sb.append("[FAIL] sendBroadcast: ").append(e2.getClass().getSimpleName()).append(' ').append(e2.getMessage());
            }
            return sb.toString();
        }

        /** 打开系统「屏幕保护」设置页（选择屏保程序），失败则回退主题中心 */
        @JavascriptInterface
        public String openScreenSaverSettings() {
            try {
                Intent i = new Intent("android.settings.DREAM_SETTINGS");
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
                AppLogger.action("车机屏保", "打开屏幕保护设置页", true, "android.settings.DREAM_SETTINGS");
                return "[OK] 已打开屏幕保护设置页 (DREAM_SETTINGS)";
            } catch (Throwable e) {
                try {
                    Intent i2 = new Intent();
                    i2.setComponent(new android.content.ComponentName("com.ecarx.thememanager", "com.ecarx.thememanager.main.MainActivity"));
                    i2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(i2);
                    return "[OK] DREAM_SETTINGS 不可用，已回退主题中心";
                } catch (Throwable e2) {
                    return "[FAIL] 均不可用: " + e2.getMessage();
                }
            }
        }

        /** 枚举车机内屏保/主题/时钟相关组件（供调试面板查看真实入口） */
        @JavascriptInterface
        public String probeScreenSaverComponents() {
            StringBuilder sb = new StringBuilder();
            String[] pkgs = {"com.ecarx.screensaver", "com.ecarx.thememanager", "com.ecarx.theme.service", "com.android.systemui"};
            android.content.pm.PackageManager pm = getPackageManager();
            for (String p : pkgs) {
                try {
                    android.content.pm.PackageInfo pi = pm.getPackageInfo(p, 0);
                    sb.append(p).append("  v").append(pi.versionName).append('\n');
                    Intent launch = pm.getLaunchIntentForPackage(p);
                    sb.append("   launcher: ").append(launch != null ? launch.getComponent().flattenToShortString() : "(无启动入口)").append('\n');
                } catch (Throwable e) {
                    sb.append(p).append("  (未安装)\n");
                }
            }
            // 屏保服务是否声明
            try {
                Intent dream = new Intent("android.intent.action.SCREENSAVER");
                dream.addCategory("android.intent.category.SCREENSAVER");
                java.util.List<android.content.pm.ResolveInfo> list = pm.queryIntentServices(dream, 0);
                sb.append("屏保服务(DreamService): ").append(list.size()).append(" 个\n");
                for (android.content.pm.ResolveInfo ri : list) {
                    sb.append("   ").append(ri.serviceInfo.packageName).append('/').append(ri.serviceInfo.name).append('\n');
                }
            } catch (Throwable e) {
                sb.append("屏保服务查询失败: ").append(e.getMessage()).append('\n');
            }
            return sb.toString();
        }

        // ==================== 闲置自动屏保（主页面闲置 N 秒后自动进入原厂屏保） ====================

        /**
         * 读取闲置自动屏保配置与运行状态。
         * 返回 JSON：enabled / seconds / minSeconds / maxSeconds / home_only /
         *            usage_access / foreground / channel / idle_ms / service_running
         */
        @JavascriptInterface
        public String getScreensaverConfig() {
            return safeCall("{}", () -> {
                JSONObject obj = new JSONObject();
                obj.put("enabled", IdleScreensaverManager.isEnabled(context));
                obj.put("seconds", IdleScreensaverManager.getSeconds(context));
                obj.put("minSeconds", IdleScreensaverManager.MIN_SECONDS);
                obj.put("maxSeconds", IdleScreensaverManager.MAX_SECONDS);
                obj.put("home_only", IdleScreensaverManager.isHomeOnly(context));
                obj.put("policy", IdleScreensaverManager.getPolicy(context));
                obj.put("usage_access", ForegroundAppDetector.isUsageAccessGranted(context));
                obj.put("foreground", ForegroundAppDetector.describe(context));
                obj.put("channel", IdleScreensaverManager.getChannelState());
                obj.put("idle_ms", IdleScreensaverManager.getLastIdleMs());
                obj.put("channel_a_ready", IdleScreensaverManager.isChannelAReady());
                obj.put("idle_raw", IdleScreensaverManager.getLastRawValue());
                obj.put("fail_reason", IdleScreensaverManager.getLastFailReason());
                obj.put("service_running", VehicleAutomationService.isRunning);
                obj.put("screensaver_pkg", ForegroundAppDetector.PKG_SCREENSAVER);
                return obj.toString();
            });
        }

        /**
         * 保存闲置自动屏保配置（支持局部更新，只传需要改的字段）。
         * 入参 JSON 例：{"enabled":true} / {"seconds":25} / {"home_only":false}
         */
        @JavascriptInterface
        public boolean setScreensaverConfig(String json) {
            try {
                JSONObject obj = new JSONObject(json == null ? "{}" : json);
                Boolean enabled = obj.has("enabled") ? obj.getBoolean("enabled") : null;
                Integer seconds = obj.has("seconds") ? obj.getInt("seconds") : null;
                Boolean homeOnly = obj.has("home_only") ? obj.getBoolean("home_only") : null;
                String policy = obj.has("policy") ? obj.getString("policy") : null;

                boolean ok = IdleScreensaverManager.saveConfig(context, enabled, seconds, homeOnly, policy);

                // 立即生效：同步拉起/停止常驻服务，并按其最新配置重启计时器
                VehicleAutomationService.syncState(context);
                if (enabled != null && !enabled) {
                    IdleScreensaverManager.stop();
                }
                AppLogger.action("电源状态", "闲置自动屏保配置已保存", ok,
                        "enabled=" + enabled + " seconds=" + seconds + " home_only=" + homeOnly);
                return ok;
            } catch (Throwable e) {
                AppLogger.action("电源状态", "闲置自动屏保配置保存失败", false, String.valueOf(e.getMessage()));
                return false;
            }
        }

        /** 跳转系统「使用情况访问」授权页（主页面判定所需，授权一次永久有效） */
        @JavascriptInterface
        public void openUsageAccessSettings() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        ForegroundAppDetector.openUsageAccessSettings(context);
                        AppLogger.action("电源状态", "跳转使用情况访问授权页", true, "ACTION_USAGE_ACCESS_SETTINGS");
                    } catch (Throwable e) {
                        AppLogger.action("电源状态", "跳转使用情况访问授权页失败", false, String.valueOf(e.getMessage()));
                    }
                }
            });
        }

        /** 立即触发一次原厂屏保（供调试与验证使用），返回可读结果字符串 */
        @JavascriptInterface
        public String triggerScreenSaverNow() {
            try {
                return IdleScreensaverManager.triggerNow(context);
            } catch (Throwable e) {
                return "[FAIL] " + e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }

        /** 查询当前前台应用与主页面判定结论（排查用） */
        @JavascriptInterface
        public String getForegroundInfo() {
            try {
                return ForegroundAppDetector.describe(context);
            } catch (Throwable e) {
                return "查询失败: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public void hardReboot() {
            if (!SystemUtils.isAdbMasterSwitchEnabled(context)) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showToast("ADB 总开关已关闭，该功能无法使用");
                    }
                });
                return;
            }
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            showToast("正在执行车机完整硬件冷重启 (25~30秒)...");
                            AppLogger.action("系统电源", "触发完整硬件冷重启 (reboot)", true, "整车冷启动");
                            SystemUtils.executePrivileged(context, "reboot || svc power reboot");
                        }
                    }).start();
                }
            });
        }

        @JavascriptInterface
        public boolean toggleFloatingWindow(final boolean enable) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        prefs.edit().putBoolean("floating_enabled", enable).apply();
                        if (enable) {
                            FloatingWindowService.ensureServiceStarted(MainActivity.this);
                            showToast(" 全局悬浮小胶囊已开启 (๑•̀ㅂ•́)و");
                        } else {
                            if (FloatingWindowService.isRunning) {
                                Intent floatIntent = new Intent(MainActivity.this, FloatingWindowService.class);
                                floatIntent.setAction(FloatingWindowService.ACTION_HIDE);
                                stopService(floatIntent);
                            }
                            showToast("全局悬浮小胶囊已关闭");
                        }
                        pushDeviceInfoToWeb();
                    } catch (Exception e) {
                        showToast("设置失败: " + e.getMessage());
                    }
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean setAppIconStyle(final String style) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        AppIconManager.setAppIconStyle(MainActivity.this, style);
                        showToast("桌面车标已切换，稍后将在车机桌面生效");
                        pushDeviceInfoToWeb();
                    } catch (Exception e) {
                        showToast("切换失败: " + e.getMessage());
                    }
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean setFloatingDisplayMode(final String mode) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        prefs.edit().putString("floating_display_mode", mode).apply();
                        boolean floatingEnabled = prefs.getBoolean("floating_enabled", false);
                        if (floatingEnabled && FloatingWindowService.isRunning) {
                            FloatingWindowService.ensureServiceStarted(MainActivity.this);
                        }
                        showToast("悬浮胶囊显示内容已切换");
                        pushDeviceInfoToWeb();
                    } catch (Exception e) {
                        showToast("切换失败: " + e.getMessage());
                    }
                }
            });
            return true;
        }

        @JavascriptInterface
        public void togglePackageFreeze(final String pkg) {
            if (pkg == null || pkg.isEmpty()) return;
            boolean isFrozen = SystemUtils.getAppDetailedState(context, pkg) == SystemUtils.APP_STATE_DISABLED;
            togglePackageFreeze(pkg, !isFrozen);
        }

        @JavascriptInterface
        public void togglePackageFreeze(final String pkg, final boolean freeze) {
            if (!SystemUtils.isAdbMasterSwitchEnabled(context)) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showToast("ADB 总开关已关闭，该功能无法使用");
                        if (webView != null) {
                            webView.evaluateJavascript("if(window.onPackageFreezeFinished) window.onPackageFreezeFinished('" + pkg + "', false, 'ADB 总开关已关闭');", null);
                        }
                    }
                });
                return;
            }
            if (freeze && isProtectedCriticalPackage(pkg)) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showToast("【安全保护】应用【" + pkg + "】为车机底层核心运行组件，禁止冻结！");
                        if (webView != null) {
                            webView.evaluateJavascript("if(window.onPackageFreezeFinished) window.onPackageFreezeFinished('" + pkg + "', false, '核心组件禁止冻结');", null);
                        }
                    }
                });
                return;
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final SystemUtils.OpResult res = SystemUtils.setPackageEnabled(context, pkg, !freeze);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (res != null && res.success) {
                                Toast.makeText(context, (freeze ? "已成功安全冻结: " : "已成功解冻恢复: ") + pkg, Toast.LENGTH_SHORT).show();
                            } else {
                                String msg = (res != null && res.message != null && !res.message.isEmpty()) ? res.message : "ADB 指令未生效";
                                Toast.makeText(context, "操作未生效: " + msg, Toast.LENGTH_LONG).show();
                            }
                            if (webView != null) {
                                boolean isOk = (res != null && res.success);
                                String errMsg = (res != null && res.message != null) ? res.message.replace("'", "\\'") : "";
                                webView.evaluateJavascript("if(window.onPackageFreezeFinished) window.onPackageFreezeFinished('" + pkg + "', " + isOk + ", '" + errMsg + "'); if(window.refreshPackageStates) window.refreshPackageStates();", null);
                            }
                        }
                    });
                }
            }).start();
        }

        @JavascriptInterface
        public void launchApp(String pkg) {
            try {
                Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } else {
                    Toast.makeText(context, "未找到该应用启动入口", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(context, "启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        @JavascriptInterface
        public void clearAppData(final String pkg) {
            if (!SystemUtils.isAdbMasterSwitchEnabled(context)) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showToast("ADB 总开关已关闭，该功能无法使用");
                    }
                });
                return;
            }
            if (isProtectedCriticalPackage(pkg)) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showToast("【安全保护】应用【" + pkg + "】属于车机底层核心组件，禁止清除数据！");
                    }
                });
                return;
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    AdbClient.AdbResult res = AdbClient.execute(context, "pm clear " + pkg);
                    final AdbClient.AdbResult finalRes = res;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (finalRes != null && finalRes.success && finalRes.output != null && finalRes.output.contains("Success")) {
                                Toast.makeText(context, "已清除应用数据与缓存: " + pkg, Toast.LENGTH_SHORT).show();
                            } else {
                                String errMsg = (finalRes != null && finalRes.error != null && !finalRes.error.isEmpty()) ? finalRes.error : "ADB 未连接或执行失败";
                                Toast.makeText(context, "清除数据失败: " + errMsg, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }).start();
        }

        @JavascriptInterface
        public void uninstallApp(final String pkg) {
            if (!SystemUtils.isAdbMasterSwitchEnabled(context)) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showToast("ADB 总开关已关闭，该功能无法使用");
                    }
                });
                return;
            }
            if (isProtectedCriticalPackage(pkg)) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showToast("【安全保护】应用【" + pkg + "】属于车机原厂底层系统组件，禁止卸载！");
                    }
                });
                return;
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    AdbClient.AdbResult res = AdbClient.execute(context, "pm uninstall " + pkg);
                    if (res == null || !res.success || (res.output != null && (res.output.contains("Failure") || res.output.contains("Error")))) {
                        res = AdbClient.execute(context, "pm uninstall -k --user 0 " + pkg);
                    }
                    final AdbClient.AdbResult finalRes = res;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (finalRes != null && finalRes.success && finalRes.output != null && finalRes.output.contains("Success")) {
                                showToast("已成功卸载/停用: " + pkg);
                            } else {
                                String errMsg = (finalRes != null && finalRes.error != null && !finalRes.error.isEmpty()) ? finalRes.error : "ADB 未连接或执行失败";
                                showToast("卸载失败: " + errMsg);
                            }
                        }
                    });
                }
            }).start();
        }

        @JavascriptInterface
        public boolean openSystemUpdate() {
            return SystemUtils.openSystemUpdate(MainActivity.this);
        }

        @JavascriptInterface
        public String extractOta() {
            return safeCall("{\"success\":false,\"url\":\"\"}", () -> {
                SystemUtils.OtaExtractResult res = SystemUtils.extractOtaUrl();
                JSONObject obj = new JSONObject();
                obj.put("success", res != null && res.success);
                obj.put("url", res != null ? res.url : "");
                obj.put("version", res != null ? res.version : "");
                obj.put("details", res != null ? res.details : "");
                return obj.toString();
            });
        }

        @JavascriptInterface
        public boolean openSystemUpgrade() {
            return SystemUtils.openSystemUpdate(context);
        }

        @JavascriptInterface
        public boolean copyToClipboard(final String text) {
            if (text == null) return false;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            ClipData clip = ClipData.newPlainText("GeelyToolbox", text);
                            cm.setPrimaryClip(clip);
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception ignored) {}
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean openBrowser(final String url) {
            if (url == null || url.trim().isEmpty()) return false;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception e) {
                        try {
                            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                ClipData clip = ClipData.newPlainText("GeelyToolbox Tutorial", url.trim());
                                cm.setPrimaryClip(clip);
                                Toast.makeText(context, "未找到可用浏览器，已复制链接到剪贴板", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception ignored) {}
                    }
                }
            });
            return true;
        }

        @JavascriptInterface
        public String getFuelData() {
            return safeCall("{\"config\":{},\"records\":[],\"stats\":{}}", () -> FuelManager.getFullDataJson(MainActivity.this));
        }

        @JavascriptInterface
        public boolean saveFuelConfig(String configJson) {
            try {
                if (configJson == null || configJson.trim().isEmpty()) return false;
                return FuelManager.saveConfig(MainActivity.this, new JSONObject(configJson));
            } catch (Exception e) {
                Log.w(TAG, "saveFuelConfig error", e);
                return false;
            }
        }

        @JavascriptInterface
        public boolean savePlateNumber(String plate) {
            try {
                if (plate == null) return false;
                return FuelManager.setPlateNumber(MainActivity.this, plate);
            } catch (Exception e) {
                Log.w(TAG, "savePlateNumber error", e);
                return false;
            }
        }

        @JavascriptInterface
        public String getPlateNumber() {
            return safeCall("", () -> FuelManager.getPlateNumber(MainActivity.this));
        }

        @JavascriptInterface
        public boolean addFuelRecord(String recordJson) {
            try {
                if (recordJson == null || recordJson.trim().isEmpty()) return false;
                return FuelManager.addRecord(MainActivity.this, new JSONObject(recordJson));
            } catch (Exception e) {
                Log.w(TAG, "addFuelRecord error", e);
                return false;
            }
        }

        @JavascriptInterface
        public boolean updateFuelRecord(String recordJson) {
            try {
                if (recordJson == null || recordJson.trim().isEmpty()) return false;
                return FuelManager.updateRecord(MainActivity.this, new JSONObject(recordJson));
            } catch (Exception e) {
                Log.w(TAG, "updateFuelRecord error", e);
                return false;
            }
        }

        @JavascriptInterface
        public boolean deleteFuelRecord(String recordId) {
            try {
                if (recordId == null || recordId.trim().isEmpty()) return false;
                long id = Long.parseLong(recordId);
                return FuelManager.deleteRecord(MainActivity.this, id);
            } catch (Exception e) {
                Log.w(TAG, "deleteFuelRecord error", e);
                return false;
            }
        }

        @JavascriptInterface
        public boolean clearFuelRecords() {
            return FuelManager.clearRecords(MainActivity.this);
        }

        @JavascriptInterface
        public boolean clearMonthlyRecords(String month) {
            return FuelManager.clearMonthlyRecords(MainActivity.this, month);
        }

        @JavascriptInterface
        public boolean clearYearlyArchive(String year) {
            return FuelManager.clearYearlyArchive(MainActivity.this, year);
        }

        @JavascriptInterface
        public boolean saveMonthlyAdjustment(String month, double totalPaid, double totalLiters) {
            return FuelManager.saveMonthlyAdjustment(MainActivity.this, month, totalPaid, totalLiters);
        }



        @JavascriptInterface
        public String getDeviceUid() {
            try {
                String androidId = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
                if (androidId != null && !androidId.isEmpty()) {
                    return androidId.toUpperCase(Locale.ROOT);
                }
            } catch (Exception ignored) {}
            return "UNKNOWN_DEVICE";
        }

        @JavascriptInterface
        public void forceCheckUpdate(final boolean isBetaChannel) {
            checkUpdateInternal(false, isBetaChannel, true);
        }

        @JavascriptInterface
        public void forceCheckUpdate() {
            checkUpdateInternal(false, false, true);
        }

        @JavascriptInterface
        public void checkBetaUpdate() {
            checkUpdateInternal(false, true, false);
        }

        @JavascriptInterface
        public void checkBetaUpdateSilently() {
            checkUpdateInternal(true, true, false);
        }

        @JavascriptInterface
        public void checkUpdate() {
            checkUpdateInternal(false, false, false);
        }

        @JavascriptInterface
        public void checkUpdateSilently() {
            checkUpdateInternal(true, false, false);
        }

        @JavascriptInterface
        public void checkUpdateSilently(final boolean isBetaChannel) {
            checkUpdateInternal(true, isBetaChannel, false);
        }

        @JavascriptInterface
        public String getNetworkDeviceStatus() {
            return safeCall("{}", () -> NetworkPolicyManager.getFullStatusJson(MainActivity.this).toString());
        }

        @JavascriptInterface
        public boolean setNetworkBootPolicy(final String type, final int policy) {
            NETWORK_WORKER.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        NetworkPolicyManager.setPolicy(MainActivity.this, type, policy);
                        pushNetworkDeviceStatusToWeb();
                    } catch (Throwable ignored) {}
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean setNetworkDeviceDirect(final String type, final boolean enable) {
            NETWORK_WORKER.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        if ("wifi".equalsIgnoreCase(type)) {
                            NetworkPolicyManager.setWifiEnabled(MainActivity.this, enable);
                        } else if ("hotspot".equalsIgnoreCase(type)) {
                            NetworkPolicyManager.setHotspotEnabled(MainActivity.this, enable);
                        } else if ("bluetooth".equalsIgnoreCase(type) || "bt".equalsIgnoreCase(type)) {
                            NetworkPolicyManager.setBluetoothEnabled(MainActivity.this, enable);
                        }
                        pushNetworkDeviceStatusToWeb();
                    } catch (Throwable ignored) {}
                }
            });
            return true;
        }

        private void checkUpdateInternal(final boolean silent) {
            checkUpdateInternal(silent, false, false);
        }

        private void checkUpdateInternal(final boolean silent, final boolean isBetaChannel) {
            checkUpdateInternal(silent, isBetaChannel, false);
        }

        private void checkUpdateInternal(final boolean silent, final boolean isBetaChannel, final boolean forceShow) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String endpoint = isBetaChannel 
                                ? "https://dl.onepve.com/GeelyToolbox/version-beta.json?t=" + System.currentTimeMillis()
                                : "https://dl.onepve.com/GeelyToolbox/version.json?t=" + System.currentTimeMillis();
                        java.net.URL url = new java.net.URL(endpoint);
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(6000);
                        conn.setReadTimeout(6000);
                        conn.setRequestProperty("User-Agent", SystemUtils.getAppUserAgent());
                        if (conn.getResponseCode() == 200) {
                            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) sb.append(line);
                            reader.close();
                            final JSONObject json = new JSONObject(sb.toString());
                            json.put("is_beta", isBetaChannel);
                            final String remoteVer = json.optString("version", "1.0.0");
                            final int remoteCode = json.optInt("version_code", 7000);

                            int currentCode = 7000;
                            try {
                                currentCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                            } catch (Exception ignored) {}

                            final boolean hasNew = remoteCode > currentCode;
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (hasNew || forceShow) {
                                        // 唤起车载精致 H5 更新弹窗 (强制模式下即使同版本也弹窗供重新下载)
                                        String script = "if(window.showToolboxUpdateModal){window.showToolboxUpdateModal(" + json.toString() + ");}";
                                        webView.evaluateJavascript(script, null);
                                    } else if (!silent) {
                                        String channelName = isBetaChannel ? "测试通道" : "正式通道";
                                        showToast(channelName + "当前已是最新版本 v" + remoteVer + " (๑•̀ㅂ•́)و");
                                    }
                                }
                            });
                        } else if (!silent) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    showToast("无法连接更新服务器，请检查车机网络");
                                }
                            });
                        }
                    } catch (Exception e) {
                        if (!silent) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    showToast("检查更新失败: " + e.getMessage());
                                }
                            });
                        }
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public void startToolboxSelfUpdate(final String downloadUrl, final String rawVer) {
            startToolboxSelfUpdate(downloadUrl, rawVer, "");
        }

        @JavascriptInterface
        public void startToolboxSelfUpdate(final String downloadUrl, final String rawVer, final String expectedMd5) {
            // 2026-09-16 统一固定文件名：下载走 .tmp 临时文件，完成后 rename 原子覆盖，
            // 不再按版本号命名导致 Download 目录堆积历史安装包。rawVer 仅为兼容旧 Web 端签名保留。
            final String apkFileName = "GeelyToolbox.apk";
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    // 一次性自清历史版本号命名的残留安装包（GeelyToolbox_v*.apk / GeelyPilot_*.apk）
                    try {
                        java.io.File dlDir = SystemUtils.getAppDownloadDir();
                        java.io.File[] olds = dlDir == null ? null : dlDir.listFiles();
                        if (olds != null) {
                            for (java.io.File f : olds) {
                                String n = f.getName();
                                if (f.isFile() && n.endsWith(".apk")
                                        && (n.startsWith("GeelyToolbox_v") || n.startsWith("GeelyPilot_v") || n.startsWith("GeelyPilot_"))) {
                                    f.delete();
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                    showToast("开始全速下载工具箱新版本...");
                    DownloadManager.startDownload("toolbox_update", downloadUrl, apkFileName, new DownloadManager.DownloadListener() {
                        private int lastReportedProgress = -1;

                        @Override
                        public void onProgress(String id, final int progress, long downloadedBytes, long totalBytes, final String speedStr) {
                            if (progress != lastReportedProgress) {
                                lastReportedProgress = progress;
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        String script = "if(window.updateToolboxSelfProgress){window.updateToolboxSelfProgress(" + progress + ",'" + speedStr + "');}";
                                        webView.evaluateJavascript(script, null);
                                    }
                                });
                            }
                        }

                        @Override
                        public void onSuccess(String id, final File savedFile) {
                            // 后台线程进行 MD5 校验与 I/O 缓冲沉淀，防止下载过快导致系统底层句柄冲突或残包调起失败
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        if (expectedMd5 != null && !expectedMd5.trim().isEmpty()) {
                                            String fileMd5 = SystemUtils.calculateMD5(savedFile);
                                            if (fileMd5 != null && !fileMd5.equalsIgnoreCase(expectedMd5.trim())) {
                                                AppLogger.e("更新下载", "MD5 校验不匹配! 期望=" + expectedMd5 + ", 实际=" + fileMd5);
                                                mainHandler.post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        String detailMsg = "安装包校验失败 (MD5不匹配: 期望 " + expectedMd5.substring(0, Math.min(8, expectedMd5.length())) + "... 实际 " + fileMd5.substring(0, Math.min(8, fileMd5.length())) + "...)，请重试";
                                                        String script = "if(window.updateToolboxSelfError){window.updateToolboxSelfError('" + detailMsg + "');}";
                                                        webView.evaluateJavascript(script, null);
                                                        showToast("安装包校验失败，文件不匹配");
                                                    }
                                                });
                                                return;
                                            }
                                        }
                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                String script = "if(window.updateToolboxSelfVerifying){window.updateToolboxSelfVerifying();}";
                                                webView.evaluateJavascript(script, null);
                                            }
                                        });

                                        // 强制 1.2 秒平滑沉淀延迟，让磁盘完全同步并给用户完整的视觉反馈
                                        Thread.sleep(1200);

                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                String script = "if(window.updateToolboxSelfDone){window.updateToolboxSelfDone();}";
                                                webView.evaluateJavascript(script, null);
                                                showToast("安装包准备就绪，正在调起系统安装通道...");
                                                SystemUtils.installApkViaProvider(MainActivity.this, savedFile);
                                            }
                                        });
                                    } catch (Throwable t) {
                                        AppLogger.e("更新下载", "安装包处理异常: " + t.getMessage());
                                    }
                                }
                            }).start();
                        }

                        @Override
                        public void onError(String id, final String errorMsg) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    String script = "if(window.updateToolboxSelfError){window.updateToolboxSelfError('" + errorMsg + "');}";
                                    webView.evaluateJavascript(script, null);
                                    showToast("下载更新包失败: " + errorMsg);
                                }
                            });
                        }

                        @Override
                        public void onPaused(String id) {}

                        @Override
                        public void onCancelled(String id) {}
                    });
                }
            });
        }

        @JavascriptInterface
        public boolean launchSavedToolboxApk() {
            try {
                java.io.File dlDir = SystemUtils.getAppDownloadDir();
                java.io.File apkFile = new java.io.File(dlDir, "GeelyToolbox.apk");
                if (apkFile.exists() && apkFile.length() > 0) {
                    showToast("正在重新调起系统安装通道...");
                    return SystemUtils.installApkViaProvider(MainActivity.this, apkFile);
                }
            } catch (Throwable ignored) {}
            showToast("未检测到本地安装包，请重新下载");
            return false;
        }

        @JavascriptInterface
        public boolean toggleFreezeAppStore(final boolean freeze) {
            if (!SystemUtils.isAdbMasterSwitchEnabled(MainActivity.this)) {
                showToast("ADB 总开关已关闭，该功能无法使用");
                return false;
            }
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            SystemUtils.OpResult res = SystemUtils.setPackageEnabled(MainActivity.this, "com.ecarx.appstore", !freeze);
                            if (freeze) {
                                showToast("已成功冻结吉利应用商店，白名单永久锁定！(๑•̀ㅂ•́)و");
                            } else {
                                showToast("已解冻恢复吉利应用商店");
                            }
                            SystemUtils.clearAppsCache();
                            pushDeviceInfoToWeb();
                        }
                    }).start();
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean isNightMode() {
            try {
                // 1. 优先通过 UiModeManager 获取当前系统的暗黑夜间状态
                android.app.UiModeManager uiModeManager = (android.app.UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
                if (uiModeManager != null) {
                    int mode = uiModeManager.getNightMode();
                    if (mode == android.app.UiModeManager.MODE_NIGHT_YES) {
                        return true;
                    } else if (mode == android.app.UiModeManager.MODE_NIGHT_NO) {
                        return false;
                    }
                }
                // 2. 读取当前 Activity Configuration 的夜间模式标志
                int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                    return true;
                } else if (nightModeFlags == Configuration.UI_MODE_NIGHT_NO) {
                    return false;
                }
                // 3. 读取 ApplicationContext 的 Configuration 进行双重比对
                int appNightFlags = getApplicationContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                return appNightFlags == Configuration.UI_MODE_NIGHT_YES;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void openFileManager() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    boolean ok = SystemUtils.openDocumentsUI(context);
                    if (!ok) {
                        Toast.makeText(context, "未检测到原生文件管理入口", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public String getDownloadDirStats() {
            return safeCall("{\"count\":0,\"size_mb\":\"0.00\",\"path\":\"/sdcard/Download/\"}", () -> {
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (downloadDir == null || !downloadDir.exists()) {
                    return "{\"count\":0,\"size_mb\":\"0.00\",\"path\":\"/sdcard/Download/\"}";
                }
                File[] files = downloadDir.listFiles();
                int count = 0;
                long totalBytes = 0;
                if (files != null) {
                    for (File f : files) {
                        count++;
                        if (f.isFile()) {
                            totalBytes += f.length();
                        } else if (f.isDirectory()) {
                            totalBytes += getDirSizeBytes(f);
                        }
                    }
                }
                double mb = (double) totalBytes / (1024 * 1024);
                return String.format(Locale.US, "{\"count\":%d,\"size_mb\":\"%.2f\",\"path\":\"/sdcard/Download/\"}", count, mb);
            });
        }

        private long getDirSizeBytes(File dir) {
            long size = 0;
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) size += f.length();
                    else if (f.isDirectory()) size += getDirSizeBytes(f);
                }
            }
            return size;
        }

        @JavascriptInterface
        public void cleanDownloadDirectory(final int mode) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        if (downloadDir == null || !downloadDir.exists()) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(context, "下载目录为空，无需清理", Toast.LENGTH_SHORT).show();
                                }
                            });
                            return;
                        }

                        int deletedFiles = 0;
                        long freedBytes = 0;
                        File[] list = downloadDir.listFiles();
                        if (list != null) {
                            for (File file : list) {
                                if (mode == 1) {
                                    // 模式 1：彻底全量清空
                                    freedBytes += (file.isFile() ? file.length() : getDirSizeBytes(file));
                                    if (deleteRecursiveInternal(file)) deletedFiles++;
                                } else if (mode == 2) {
                                    // 模式 2：智能安全清理 (避开非空目录与音频素材)
                                    if (file.isDirectory()) {
                                        File[] sub = file.listFiles();
                                        if (sub == null || sub.length == 0) {
                                            if (file.delete()) deletedFiles++;
                                        } else {
                                            String lower = file.getName().toLowerCase();
                                            if (lower.contains("voice") || lower.contains("audio") || lower.contains("sound")) {
                                                continue; // 保护
                                            }
                                            for (File sf : sub) {
                                                if (sf.isFile() && (sf.getName().endsWith(".apk") || sf.getName().endsWith(".zip") || sf.getName().endsWith(".tmp"))) {
                                                    freedBytes += sf.length();
                                                    if (sf.delete()) deletedFiles++;
                                                }
                                            }
                                        }
                                    } else if (file.isFile()) {
                                        freedBytes += file.length();
                                        if (file.delete()) deletedFiles++;
                                    }
                                } else if (mode == 3) {
                                    // 模式 3：仅清理散落普通文件 (保留全部文件夹)
                                    if (file.isFile()) {
                                        freedBytes += file.length();
                                        if (file.delete()) deletedFiles++;
                                    }
                                }
                            }
                        }

                        final int finalDeleted = deletedFiles;
                        final double freedMb = (double) freedBytes / (1024 * 1024);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, String.format(Locale.CHINA, "清理完成！共清除 %d 项，释放 %.1f MB 存储空间", finalDeleted, freedMb), Toast.LENGTH_LONG).show();
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to clean download directory: " + e.getMessage());
                    }
                }
            }).start();
        }

        private boolean deleteRecursiveInternal(File fileOrDirectory) {
            if (fileOrDirectory.isDirectory()) {
                File[] children = fileOrDirectory.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteRecursiveInternal(child);
                    }
                }
            }
            return fileOrDirectory.delete();
        }

        @JavascriptInterface
        public boolean setApkVerifyWhitelist(final boolean enable) {
            if (!SystemUtils.isAdbMasterSwitchEnabled(context)) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showToast("ADB 总开关已关闭，该功能无法使用");
                    }
                });
                return false;
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (enable) {
                        SystemUtils.enableApkVerifyWhitelist(context);
                        isWhitelistEnabled = true;
                    } else {
                        SystemUtils.executePrivileged(context, "setprop sys.jsbd.apk_verify 0 && setprop persist.sys.jsbd.apk_verify 0 && setprop sys.geely.apk_verify 0 && setprop persist.sys.geely.apk_verify 0");
                        isWhitelistEnabled = false;
                    }
                    pushDeviceInfoToWeb();
                }
            }).start();
            return true;
        }

        @JavascriptInterface
        public void toggleWhitelist() {
            if (!SystemUtils.isAdbMasterSwitchEnabled(context)) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showToast("ADB 总开关已关闭，该功能无法使用");
                    }
                });
                return;
            }
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            boolean current = SystemUtils.isApkVerifyWhitelistEnabled();
                            if (!current) {
                                SystemUtils.enableApkVerifyWhitelist(context);
                                isWhitelistEnabled = true;
                                showToast("车机安装白名单已成功放行 (sys.jsbd.apk_verify = 1) (๑•̀ㅂ•́)و");
                            } else {
                                SystemUtils.executePrivileged(context, "setprop sys.jsbd.apk_verify 0 && setprop persist.sys.jsbd.apk_verify 0 && setprop sys.geely.apk_verify 0 && setprop persist.sys.geely.apk_verify 0");
                                isWhitelistEnabled = false;
                                showToast("车机安装白名单已关闭 (sys.jsbd.apk_verify = 0)");
                            }
                            pushDeviceInfoToWeb();
                        }
                    }).start();
                }
            });
        }

        @JavascriptInterface
        public boolean isAdbMasterSwitchEnabled() {
            return SystemUtils.isAdbMasterSwitchEnabled(context);
        }

        @JavascriptInterface
        public void setAdbMasterSwitchEnabled(final boolean enabled) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    SystemUtils.setAdbMasterSwitchEnabled(context, enabled);
                    pushDeviceInfoToWeb();
                    if (webView != null) {
                        webView.evaluateJavascript("if(window.onAdbMasterSwitchChanged) window.onAdbMasterSwitchChanged(" + enabled + ");", null);
                    }
                }
            });
        }

        @JavascriptInterface
        public void cleanCache() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                File downloadDir = new File(Environment.getExternalStorageDirectory(), "Download");
                                int cleaned = 0;
                                if (downloadDir.exists() && downloadDir.isDirectory()) {
                                    File[] files = downloadDir.listFiles();
                                    if (files != null) {
                                        for (File f : files) {
                                            if (f.isDirectory() && f.list() != null && f.list().length == 0) {
                                                f.delete();
                                                cleaned++;
                                            }
                                        }
                                    }
                                }
                                final int count = cleaned;
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(context, "已清理 " + count + " 个无效空文件夹", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            } catch (Exception ignored) {
                            }
                        }
                    }).start();
                }
            });
        }

        @JavascriptInterface
        public void cleanAllDownloadFiles() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        File downloadDir = new File(Environment.getExternalStorageDirectory(), "Download");
                        int count = 0;
                        if (downloadDir.exists() && downloadDir.isDirectory()) {
                            File[] files = downloadDir.listFiles();
                            if (files != null) {
                                for (File f : files) {
                                    if (f.isFile() && f.delete()) {
                                        count++;
                                    }
                                }
                            }
                        }
                        final int finalCount = count;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, "已清空下载目录全部文件 (已删除 " + finalCount + " 个文件)", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (Exception e) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, "清空失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public void minimizeApp() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                    if (prefs.getBoolean("floating_enabled", false)) {
                        FloatingWindowService.ensureServiceStarted(MainActivity.this);
                    }
                    moveTaskToBack(true);
                }
            });
        }

        @JavascriptInterface
        public void startWebServer() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (webServer == null) {
                        webServer = new WebServer(context, MainActivity.this);
                    }
                    webServer.start();
                }
            });
        }

        @JavascriptInterface
        public String getAllInstalledApps() {
            return safeCall("[]", () -> {
                SystemUtils.clearAppsCache();
                java.util.List<SystemUtils.DetailedAppInfo> apps = SystemUtils.getAllInstalledApps(MainActivity.this);
                org.json.JSONArray array = new org.json.JSONArray();
                if (apps != null) {
                    for (SystemUtils.DetailedAppInfo info : apps) {
                        JSONObject obj = new JSONObject();
                        obj.put("pkg", info.packageName);
                        obj.put("name", info.appName != null && !info.appName.isEmpty() ? info.appName : info.packageName);
                        obj.put("version", SystemUtils.getInstalledVersionName(MainActivity.this, info.packageName));
                        obj.put("isSystem", info.isSystemApp);
                        obj.put("frozen", !info.enabled);
                        array.put(obj);
                    }
                }
                return array.toString();
            });
        }

        @JavascriptInterface
        public String checkLocalAppStatuses(String jsonArrayStr) {
            return safeCall("{}", () -> {
                org.json.JSONArray arr = new org.json.JSONArray(jsonArrayStr);
                JSONObject result = new JSONObject();
                File downloadDir = new File(Environment.getExternalStorageDirectory(), "Download");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    String id = item.optString("id");
                    String filename = item.optString("filename");
                    String pkg = item.optString("package_name");

                    boolean exists = false;
                    long size = 0;
                    String downloadTime = "";
                    if (filename != null && !filename.isEmpty()) {
                        File f = new File(downloadDir, filename);
                        if (f.exists() && f.length() > 0) {
                            exists = true;
                            size = f.length();
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA);
                            downloadTime = sdf.format(new java.util.Date(f.lastModified()));
                        }
                    }

                    boolean installed = false;
                    String installedVer = "";
                    if (pkg != null && !pkg.isEmpty()) {
                        installedVer = SystemUtils.getInstalledVersionName(context, pkg);
                        installed = (installedVer != null && !installedVer.isEmpty());
                    }

                    JSONObject stat = new JSONObject();
                    stat.put("exists", exists);
                    stat.put("size", size);
                    stat.put("downloadTime", downloadTime);
                    stat.put("installed", installed);
                    stat.put("installedVersion", installedVer);
                    result.put(id, stat);
                }
                return result.toString();
            });
        }

        @JavascriptInterface
        public void downloadApp(final String appId, final String downloadUrl, final String filename) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, "开始下载: " + (filename != null ? filename : appId), Toast.LENGTH_SHORT).show();
                    DownloadManager.startDownload(appId, downloadUrl, filename, new DownloadManager.DownloadListener() {
                        @Override
                        public void onProgress(String id, final int progress, long downloadedBytes, long totalBytes, final String speedStr) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (webView != null) {
                                        webView.evaluateJavascript("if(window.updateDownloadProgress) window.updateDownloadProgress('" + appId + "', " + progress + ", '" + speedStr + "');", null);
                                    }
                                }
                            });
                        }

                        @Override
                        public void onSuccess(String id, final File savedFile) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (webView != null) {
                                        webView.evaluateJavascript("if(window.updateDownloadSuccess) window.updateDownloadSuccess('" + appId + "', '" + savedFile.getName() + "');", null);
                                    }
                                    if (!id.startsWith("amap") && !id.contains("firmware") && !id.contains("rescue")) {
                                        boolean hasSettings = SystemUtils.isPackageInstalled(MainActivity.this, "com.android.settings");
                                        if (hasSettings) {
                                            Toast.makeText(context, "下载完成，正在调起系统安装器...", Toast.LENGTH_SHORT).show();
                                            SystemUtils.installApkViaProvider(MainActivity.this, savedFile);
                                        } else {
                                            Toast.makeText(context, "下载完成: " + savedFile.getName() + "，请通过任务管理或文件管理安装", Toast.LENGTH_LONG).show();
                                            if (webView != null) {
                                                webView.evaluateJavascript("if(window.openManualInstallModalById) window.openManualInstallModalById('" + appId + "');", null);
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "下载完成: " + savedFile.getName() + "\n已保存在 Download 目录", Toast.LENGTH_LONG).show();
                                    }
                                }
                            });
                        }

                        @Override
                        public void onError(String id, final String errorMsg) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (webView != null) {
                                        webView.evaluateJavascript("if(window.updateDownloadError) window.updateDownloadError('" + appId + "', '" + errorMsg.replace("'", "\\'") + "');", null);
                                    }
                                    Toast.makeText(context, "下载失败: " + errorMsg, Toast.LENGTH_LONG).show();
                                }
                            });
                        }

                        @Override
                        public void onPaused(String id) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (webView != null) {
                                        webView.evaluateJavascript("if(window.updateDownloadPaused) window.updateDownloadPaused('" + appId + "');", null);
                                    }
                                    Toast.makeText(context, "已暂停下载", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                        @Override
                        public void onCancelled(String id) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (webView != null) {
                                        webView.evaluateJavascript("if(window.updateDownloadCancelled) window.updateDownloadCancelled('" + appId + "');", null);
                                    }
                                    Toast.makeText(context, "已取消下载", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });
                }
            });
        }

        @JavascriptInterface
        public String dumpSystemLogcat() {
            return dumpFullSystemLogcat();
        }

        @JavascriptInterface
        public String getDiagnosticLogMetadata() {
            JSONObject res = SystemUtils.getExistingDiagnosticLog(MainActivity.this);
            return res != null ? res.toString() : "{\"exists\":false}";
        }

        @JavascriptInterface
        public String dumpFullSystemLogcat() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final JSONObject res = SystemUtils.dumpFullSystemLogcat(MainActivity.this, new LogDumpProgressListener() {
                        @Override
                        public void onProgress(final int percent, final String message) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (webView != null) {
                                        String js = String.format(java.util.Locale.US,
                                            "if (window.onLogDumpProgress) window.onLogDumpProgress(%d, '%s');",
                                            percent, message.replace("'", "\\'"));
                                        webView.evaluateJavascript(js, null);
                                    }
                                }
                            });
                        }
                    });
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            boolean ok = res.optBoolean("success", false);
                            String msg = res.optString("message", "日志打包完成");
                            showToast(ok ? ("✓ " + msg) : ("❌ " + msg));
                            if (webView != null) {
                                webView.evaluateJavascript("if (window.onLogcatDumpFinished) window.onLogcatDumpFinished(" + res.toString() + ");", null);
                            }
                        }
                    });
                }
            }, "AsyncLogcatDump").start();
            return "{\"success\":true,\"message\":\"已启动后台采集\"}";
        }

        @JavascriptInterface
        public String uploadDiagnosticLog(final String filePath) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final JSONObject res = SystemUtils.uploadDiagnosticLog(MainActivity.this, filePath, new OnUploadProgressListener() {
                        @Override
                        public void onProgress(final int percent, final long uploadedBytes, final long totalBytes, final String statusText) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (webView != null) {
                                        long upKb = uploadedBytes / 1024;
                                        long totKb = totalBytes / 1024;
                                        String js = String.format(java.util.Locale.US,
                                                "if (window.onLogUploadProgress) window.onLogUploadProgress(%d, %d, %d, '%s');",
                                                percent, upKb, totKb, statusText.replace("'", "\\'"));
                                        webView.evaluateJavascript(js, null);
                                    }
                                }
                            });
                        }
                    });
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            boolean ok = res.optBoolean("success", false);
                            String code = res.optString("code", "");
                            if (ok) {
                                showToast("✓ 日志上传成功，提取码: " + code);
                            } else {
                                showToast("❌ 日志上传失败: " + res.optString("message", ""));
                            }
                            if (webView != null) {
                                webView.evaluateJavascript("if (window.onLogUploadFinished) window.onLogUploadFinished(" + res.toString() + ");", null);
                            }
                        }
                    });
                }
            }, "AsyncLogUpload").start();
            return "{\"success\":true,\"message\":\"已发起后台上传\"}";
        }

        @JavascriptInterface
        public String exportGuardLogZip() {
            final JSONObject res = AppLogger.exportGuardLogZip(MainActivity.this);
            boolean ok = res.optBoolean("success", false);
            String filename = res.optString("filename", "Geely_Log_Guard.zip");
            showToast(ok ? ("✓ 守护日志已导出: " + filename) : "❌ 导出守护日志失败");
            return res.toString();
        }

        @JavascriptInterface
        public String getConnectivityStatus() {
            JSONObject res = new JSONObject();
            try {
                BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
                boolean btEnabled = (ba != null && ba.isEnabled());
                res.put("bluetooth_enabled", btEnabled);
                boolean btConnected = false;
                String btDeviceName = "未连接设备";
                if (btEnabled && ba != null) {
                    try {
                        int a2dpState = ba.getProfileConnectionState(11); // 11=A2DP_SINK
                        btConnected = (a2dpState == 2);
                        // 性能铁律：getBondedDevices() 是昂贵的蓝牙 binder IPC（车机慢栈上可达数百毫秒），
                        // 而 getConnectivityStatus 由前端每 4 秒同步调用一次、且 JS 线程会被它整段阻塞。
                        // 仅在真的处于已连接态时才去取设备名；未连接（绝大多数时候）直接跳过这次整表 IPC。
                        if (btConnected) {
                            java.util.Set<android.bluetooth.BluetoothDevice> bonded = ba.getBondedDevices();
                            if (bonded != null) {
                                for (android.bluetooth.BluetoothDevice dev : bonded) {
                                    btDeviceName = dev.getName();
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
                res.put("bluetooth_connected", btConnected);
                res.put("bluetooth_device_name", btConnected ? (btDeviceName != null ? btDeviceName : "手机蓝牙") : "未连接设备");

                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                boolean wifiEnabled = (wm != null && wm.isWifiEnabled());
                res.put("wifi_enabled", wifiEnabled);
                String wifiSsid = "未连接热点";
                boolean wifiConnected = false;
                if (wifiEnabled && wm != null) {
                    WifiInfo info = wm.getConnectionInfo();
                    if (info != null && info.getNetworkId() != -1) {
                        String s = info.getSSID();
                        if (s != null && !s.isEmpty() && !"<unknown ssid>".equals(s)) {
                            wifiSsid = s.replace("\"", "");
                            wifiConnected = true;
                        }
                    }
                }
                res.put("wifi_connected", wifiConnected);
                res.put("wifi_ssid", wifiSsid);
                SystemUtils.NetStatus net = SystemUtils.getNetworkStatus();
                res.put("car_ip", (net != null && net.ip != null) ? net.ip : "127.0.0.1");
                res.put("eas_channel_active", EasMediaBridge.getInstance(MainActivity.this).isBluetoothChannelActive());
            } catch (Exception e) {
                try { res.put("error", e.getMessage()); } catch (Exception ignored) {}
            }
            return res.toString();
        }

        @JavascriptInterface
        public void toggleBluetooth(boolean enable) {
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            if (ba != null) {
                if (enable) ba.enable();
                else ba.disable();
                showToast(enable ? "正在开启车机蓝牙..." : "已关闭车机蓝牙");
            } else {
                AdbClient.execute(MainActivity.this, "svc bluetooth " + (enable ? "enable" : "disable"));
                showToast(enable ? "已下发指令开启蓝牙" : "已下发指令关闭蓝牙");
            }
        }

        // ============ 原厂系统设置页启动（蓝牙 / Wi-Fi） ============
        // 车主实测缺陷：「点一下要等好久才出现窗口，而且冒出来还是两个，还得自己挑一个」。
        //   ① 卡顿根因：此前走 AdbClient.execute("am start -a ...")。纯 Java ADB 客户端要先
        //      建 TCP 连接、跑 RSA 握手、再等 shell 命令执行完毕才返回，整段全压在 WebView 的
        //      JS 线程上，点击后界面直接冻结数秒（车机上更明显）。
        //   ② 两个窗口根因：action 不带 component。车机上多个 Activity 都声明了同一 action，
        //      系统于是弹出「选择应用」让车主自己挑，等于点一次要确认两回。
        // 修法：改为 UI 线程直接 startActivity（立刻返回，绝不阻塞 JS 线程），并先用
        //       PackageManager 把候选收敛为唯一的原厂设置组件显式指定；重复点击靠
        //       SINGLE_TOP + 防抖复用同一窗口，杜绝叠出第二个。
        private static final long SETTINGS_LAUNCH_DEBOUNCE_MS = 900L;
        private long settingsLaunchLastAt = 0L;
        private String settingsLaunchLastAction = "";

        private void openSystemSettingsPage(final String action) {
            final long now = System.currentTimeMillis();
            if (action.equals(settingsLaunchLastAction)
                    && (now - settingsLaunchLastAt) < SETTINGS_LAUNCH_DEBOUNCE_MS) {
                return; // 连点防抖：同一入口 900ms 内只认一次
            }
            settingsLaunchLastAction = action;
            settingsLaunchLastAt = now;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent intent = new Intent(action);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        ResolveInfo pick = resolvePreferredSettingsHandler(intent);
                        if (pick != null && pick.activityInfo != null) {
                            intent.setComponent(new ComponentName(
                                    pick.activityInfo.packageName, pick.activityInfo.name));
                        }
                        MainActivity.this.startActivity(intent);
                    } catch (Exception e) {
                        AppLogger.w("系统设置", "直接拉起设置页失败，转 ADB 兜底: " + e.getMessage());
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                AdbClient.execute(MainActivity.this, "am start -a " + action);
                            }
                        }, "settings-adb-fallback").start();
                    }
                }
            });
        }

        /** 把「打开系统设置」的候选 Activity 收敛为唯一一个原厂设置组件。
         *  不收敛时，多个应用都声明同一 action，系统会弹「选择应用」让车主自己挑。 */
        private ResolveInfo resolvePreferredSettingsHandler(Intent intent) {
            try {
                List<ResolveInfo> list = getPackageManager().queryIntentActivities(intent, 0);
                if (list == null || list.isEmpty()) return null;
                if (list.size() == 1) return list.get(0);
                String[] preferPackages = {"com.android.settings", "com.ecarx.settings", "com.ecarx.xctsettings"};
                for (String pkg : preferPackages) {
                    for (ResolveInfo ri : list) {
                        if (ri.activityInfo != null && pkg.equals(ri.activityInfo.packageName)) return ri;
                    }
                }
                String self = getPackageName();
                for (ResolveInfo ri : list) {
                    if (ri.activityInfo != null && !self.equals(ri.activityInfo.packageName)) return ri;
                }
                return list.get(0);
            } catch (Exception e) {
                return null;
            }
        }

        @JavascriptInterface
        public void openBluetoothSettings() {
            openSystemSettingsPage("android.settings.BLUETOOTH_SETTINGS");
        }

        @JavascriptInterface
        public boolean isWifiEnabled() {
            return NetworkPolicyManager.isWifiEnabled(MainActivity.this);
        }

        @JavascriptInterface
        public boolean toggleWifi(boolean enable) {
            boolean ok = NetworkPolicyManager.setWifiEnabled(MainActivity.this, enable);
            showToast(enable ? "正在开启车机 Wi-Fi..." : "已关闭车机 Wi-Fi");
            return ok;
        }

        @JavascriptInterface
        public void openWifiSettings() {
            openSystemSettingsPage("android.settings.WIFI_SETTINGS");
        }

        @JavascriptInterface
        public void openHotspotSettings() {
            // 修复：原厂亿咖通 (ecarx.settings) 无原生 TETHER_SETTINGS，直接拉起整车设置热点页
            openSystemSettingsPage("android.settings.TETHER_SETTINGS");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        AdbClient.execute(MainActivity.this, "am start -n ecarx.settings/ecarx.settings.setting.CarSettingActivity");
                    } catch (Exception ignored) {}
                }
            }, "hotspot-settings-fallback").start();
        }

        @JavascriptInterface
        public String forceActivateBluetoothChannel() {
            EasMediaBridge.getInstance(MainActivity.this).activateBluetoothChannel();
            showToast("已强制选通 2 号蓝牙物理声道");
            return "{\"success\":true}";
        }

        @JavascriptInterface
        public void testBluetoothAudio() {
            VehicleVoicePlayer player = VehicleVoicePlayer.getInstance(MainActivity.this);
            if (player != null) {
                player.play("gear_d.mp3", "蓝牙音频通道测试");
                showToast("正在通过当前音频通道播放测试样音...");
            }
        }

        @JavascriptInterface
        public void playAudioUsageChannel(final int usageChannel) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        app.onepve.geelyconsole.utils.ChannelAudioTester.playChannelBeep(MainActivity.this, usageChannel);
                        showToast("正在向通道 " + usageChannel + " 播放微信测试音...");
                    } catch (Throwable t) {
                        showToast("播放通道 " + usageChannel + " 失败: " + t.getMessage());
                    }
                }
            });
        }

        @JavascriptInterface
        public boolean setPreferredAudioUsageChannel(final int usageChannel) {
            try {
                android.content.SharedPreferences sp = getSharedPreferences("app_config", Context.MODE_PRIVATE);
                sp.edit().putInt("preferred_audio_usage_channel", usageChannel).apply();
                showToast("已将通道 " + usageChannel + " 保存为微信语音首选通道");
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        @JavascriptInterface
        public int getPreferredAudioUsageChannel() {
            try {
                android.content.SharedPreferences sp = getSharedPreferences("app_config", Context.MODE_PRIVATE);
                return sp.getInt("preferred_audio_usage_channel", 2); // 默认 2 (USAGE_VOICE_COMMUNICATION 通话/独立喇叭)
            } catch (Throwable t) {
                return 2;
            }
        }

        @JavascriptInterface
        public boolean switchEcarxSourceType(final int sourceType) {
            try {
                EasMediaBridge.getInstance(MainActivity.this).switchSourceTypeManually(sourceType);
                showToast("已下发底层切源指令: " + sourceType);
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        @JavascriptInterface
        public void refreshCloudApps() {
            fetchCloudAppsAsync(false);
        }

        @JavascriptInterface
        public void refreshCloudAppsManual() {
            fetchCloudAppsAsync(true);
        }

        @JavascriptInterface
        public String getRabbitThemeInfo() {
            return ThemePatcher.getRabbitDisguiseInfo(MainActivity.this).toString();
        }

        @JavascriptInterface
        public void pauseDownload(final String appId) {
            DownloadManager.pauseDownload(appId);
        }

        @JavascriptInterface
        public void cancelDownload(final String appId) {
            DownloadManager.cancelDownload(appId);
        }

        @JavascriptInterface
        public void installDownloadedApk(final String filename) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    File downloadDir = new File(Environment.getExternalStorageDirectory(), "Download");
                    final File apkFile = new File(downloadDir, filename);
                    if (!apkFile.exists() || apkFile.length() == 0) {
                        Toast.makeText(context, "文件不存在，请先下载: " + filename, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 1. 安全拦截：检测到系统设置/核心包名坚决拦截
                    try {
                        PackageManager pm = getPackageManager();
                        PackageInfo pi = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
                        if (pi != null && pi.packageName != null) {
                            String pkg = pi.packageName;
                            if (isDangerousSystemPackage(pkg)) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        showToast("【安全拦截】该 APK 为系统核心组件（" + pkg + "），禁止直接安装！");
                                    }
                                });
                                return;
                            }
                        }
                    } catch (Exception ignored) {}

                    // 2. 直装前置自动确保白名单开启，并调起安全安装器
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            SystemUtils.enableApkVerifyWhitelist(context);
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    boolean launched = SystemUtils.installApkViaProvider(MainActivity.this, apkFile);
                                    AppLogger.action("应用安装", "调起系统安装器: " + filename, launched, launched ? "已调起系统安装界面" : "调起失败，需手动安装");
                                    if (!launched) {
                                        Toast.makeText(context, "调起系统安装器失败，请前往【原生文件管理】手动点击安装", Toast.LENGTH_LONG).show();
                                    }
                                }
                            });
                        }
                    }).start();
                }
            });
        }

        @JavascriptInterface
        public String probeAdbStatus() {
            return AdbClient.probeAdbStatus(MainActivity.this).toString();
        }

        @JavascriptInterface
        public String executeCustomAdbCommand(final String command) {
            if (!SystemUtils.isAdbMasterSwitchEnabled(context)) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showToast("ADB 总开关已关闭，该功能无法使用");
                    }
                });
                JSONObject res = new JSONObject();
                try {
                    res.put("success", false);
                    res.put("output", "ADB 总开关已关闭，该功能无法使用");
                    res.put("error", "ADB_DISABLED");
                    res.put("durationMs", 0);
                } catch (Exception ignored) {}
                return res.toString();
            }
            long startTime = System.currentTimeMillis();
            JSONObject res = new JSONObject();
            try {
                if (command == null || command.trim().isEmpty()) {
                    res.put("success", false);
                    res.put("output", "命令为空");
                    res.put("durationMs", 0);
                    return res.toString();
                }

                String cmd = command.trim();
                AdbClient.AdbResult adbRes = AdbClient.execute(context, cmd);
                long duration = System.currentTimeMillis() - startTime;

                if (adbRes != null && (adbRes.success || !adbRes.output.isEmpty())) {
                    res.put("success", adbRes.success);
                    res.put("output", adbRes.output);
                    res.put("error", adbRes.error);
                    res.put("durationMs", duration);
                } else {
                    // Fallback to Runtime Shell
                    String shOutput = SystemUtils.executeShell(cmd);
                    res.put("success", true);
                    res.put("output", (shOutput != null && !shOutput.isEmpty()) ? shOutput : "[执行完成，无输出返回]");
                    res.put("durationMs", duration);
                }
            } catch (Exception e) {
                try {
                    res.put("success", false);
                    res.put("output", "执行异常: " + e.getMessage());
                    res.put("durationMs", System.currentTimeMillis() - startTime);
                } catch (Exception ignored) {}
            }
            return res.toString();
        }

        private AdbClient.AdbStreamSession keyEventCaptureSession = null;

        @JavascriptInterface
        public boolean startKeyEventCapture() {
            if (!SystemUtils.isAdbMasterSwitchEnabled(context)) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showToast("ADB 总开关已关闭，该功能无法使用");
                    }
                });
                return false;
            }
            try {
                stopKeyEventCapture();
                keyEventCaptureSession = AdbClient.executeStream(context, "getevent -l", new AdbClient.AdbStreamCallback() {
                    @Override
                    public void onLine(final String line) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (webView != null) {
                                    webView.evaluateJavascript("if(window.onKeyEventCaptured) window.onKeyEventCaptured(" + JSONObject.quote(line) + ");", null);
                                }
                            }
                        });
                    }

                    @Override
                    public void onComplete(final boolean success, final String error) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (webView != null) {
                                    webView.evaluateJavascript("if(window.onKeyEventCaptureCompleted) window.onKeyEventCaptureCompleted(" + success + ", " + JSONObject.quote(error != null ? error : "") + ");", null);
                                }
                            }
                        });
                    }
                });
                return keyEventCaptureSession != null;
            } catch (Exception e) {
                Log.e("GeelyConsole", "startKeyEventCapture error: " + e.getMessage(), e);
                return false;
            }
        }

        @JavascriptInterface
        public boolean stopKeyEventCapture() {
            try {
                if (keyEventCaptureSession != null) {
                    keyEventCaptureSession.stop();
                    keyEventCaptureSession = null;
                }
                return true;
            } catch (Exception e) {
                Log.e("GeelyConsole", "stopKeyEventCapture error: " + e.getMessage(), e);
                return false;
            }
        }

        @JavascriptInterface
        public void exitApp() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        AppLogger.i("系统退出", "车主点击退出，安全停止后台服务并彻底杀死自身进程");
                        
                        // 1. 显式停止所有后台守护服务
                        try {
                            stopService(new Intent(MainActivity.this, VehicleAutomationService.class));
                        } catch (Throwable ignored) {}
                        try {
                            stopService(new Intent(MainActivity.this, FloatingWindowService.class));
                        } catch (Throwable ignored) {}

                        // 2. 彻底结束当前 Activity 并移出前台
                        finishAffinity();

                        // 3. 延后 200ms 彻底连根拔起杀死进程，释放所有句柄与内存
                        mainHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    android.os.Process.killProcess(android.os.Process.myPid());
                                    System.exit(0);
                                } catch (Throwable ignored) {}
                            }
                        }, 200L);
                    } catch (Throwable t) {
                        try {
                            finish();
                            android.os.Process.killProcess(android.os.Process.myPid());
                            System.exit(0);
                        } catch (Throwable ignored) {}
                    }
                }
            });
        }

        @JavascriptInterface
        public void showToast(final String msg) {
            MainActivity.this.showToast(msg, "info");
        }

        @JavascriptInterface
        public void showToast(final String msg, final String kind) {
            MainActivity.this.showToast(msg, kind);
        }

        @JavascriptInterface
        public String getVehicleAutomationSettings() {
            return safeCall("{}", () -> VehicleConfigHelper.getVehicleAutomationSettingsJson(MainActivity.this));
        }

        @JavascriptInterface
        public void selectCustomVoice(final String voiceKey) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    currentSelectingVoiceKey = voiceKey;
                    try {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("audio/*");
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        startActivityForResult(Intent.createChooser(intent, "选择自定义音频文件"), REQ_CODE_SELECT_VOICE);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "未能调起系统文件选择器，请直接将 MP3 文件放入 /sdcard/Music/ 目录", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void resetCustomVoice(final String voiceKey) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        String cleanKey = voiceKey != null ? voiceKey.trim().replaceAll("[^a-zA-Z0-9_]", "") : "";
                        SharedPreferences prefs = PrefUtils.getAppPreferences(MainActivity.this);
                        prefs.edit()
                                .remove("custom_voice_" + cleanKey)
                                .remove("custom_voice_" + cleanKey + ".mp3")
                                .remove("custom_voice_name_" + cleanKey)
                                .apply();
                        if ("door_fl".equals(cleanKey) || "door_open".equals(cleanKey)) {
                            prefs.edit()
                                    .remove("custom_voice_door_fl")
                                    .remove("custom_voice_door_fl.mp3")
                                    .remove("custom_voice_door_open")
                                    .remove("custom_voice_door_open.mp3")
                                    .remove("custom_voice_name_door_fl")
                                    .remove("custom_voice_name_door_open")
                                    .apply();
                        }
                        // 物理删除 /sdcard/GeelyPilot/voices/custom/ 下的实体文件，杜绝孤儿垃圾
                        File customDir = new File("/sdcard/GeelyPilot/voices/custom");
                        File customFile = new File(customDir, cleanKey + ".mp3");
                        if (customFile.exists()) {
                            customFile.delete();
                        }
                        if ("door_fl".equals(cleanKey) || "door_open".equals(cleanKey)) {
                            File fl = new File(customDir, "door_fl.mp3");
                            File open = new File(customDir, "door_open.mp3");
                            if (fl.exists()) fl.delete();
                            if (open.exists()) open.delete();
                        }
                        // 兼容清理旧历史目录
                        File voiceDir = new File(getFilesDir(), "custom_voices");
                        File dest = new File(voiceDir, cleanKey);
                        if (dest.exists()) dest.delete();

                        Toast.makeText(MainActivity.this, "已恢复出厂原声并释放存储", Toast.LENGTH_SHORT).show();
                        if (webView != null) {
                            webView.loadUrl("javascript:refreshVehicleAutoUI()");
                        }
                    } catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface
        public boolean clearAllCustomVoices() {
            try {
                File customDir = new File("/sdcard/GeelyPilot/voices/custom");
                if (customDir.exists() && customDir.isDirectory()) {
                    File[] files = customDir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.isFile()) {
                                f.delete();
                            }
                        }
                    }
                }
                SharedPreferences prefs = PrefUtils.getAppPreferences(MainActivity.this);
                SharedPreferences.Editor editor = prefs.edit();
                for (String k : prefs.getAll().keySet()) {
                    if (k.startsWith("custom_voice_")) {
                        editor.remove(k);
                    }
                }
                editor.apply();
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "已清空所有自定义音频并恢复官方原声", Toast.LENGTH_SHORT).show();
                    if (webView != null) {
                        webView.loadUrl("javascript:refreshVehicleAutoUI()");
                    }
                });
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean setWheelControlStringSetting(final String key, final String value) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        prefs.edit().putString(key, value).apply();
                        VehicleAutomationService.syncState(MainActivity.this);
                        AppLogger.i("方控设置", "更新字符设置: " + key + " -> " + value);
                    } catch (Exception e) {
                        AppLogger.e("方控设置", "更新失败: " + e.getMessage());
                    }
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean refreshInstalledApps() {
            SystemUtils.clearAppsCache();
            AppLogger.i("应用管理", "手动清除已装应用缓存，触发全仓重新扫描");
            return true;
        }

        // ═══ 每声效独立声道/增益（音量增益专题定案：单项接管，0=不接管） ═══
        // 契约：voice_item_channel_<key>（music/nav/notification，缺省=music）、voice_item_offset_<key>（-15~15，缺省=0=不接管）
        // 语义：offset≠0 → 单项完全接管（音量=当前流音量+N，倒车防衰减补偿与70%地板停用）；offset=0 → 完全跟随原厂行为
        @JavascriptInterface
        public boolean setVoiceItemChannel(final String key, final String channel) {
            mainHandler.post(() -> {
                try {
                    if (key == null || key.isEmpty()) return;
                    String v = "notification".equals(channel) ? "notification" : ("nav".equals(channel) ? "nav" : "music");
                    getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE)
                            .edit().putString("voice_item_channel_" + key, v).apply();
                    AppLogger.i("声音设置", "声道[" + key + "] -> " + v);
                } catch (Exception e) {
                    AppLogger.e("声音设置", "声道更新失败: " + e.getMessage());
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean setVoiceItemOffset(final String key, final int offset) {
            mainHandler.post(() -> {
                try {
                    if (key == null || key.isEmpty()) return;
                    // v1.7.51: 全量语音已推满车规级标准响度，彻底停用写入增益键；若有旧键顺手清除
                    SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                    if (prefs.contains("voice_item_offset_" + key)) {
                        prefs.edit().remove("voice_item_offset_" + key).apply();
                        AppLogger.i("声音设置", "废弃增益键已物理清除[" + key + "]");
                    }
                } catch (Exception ignored) {}
            });
            return true;
        }

        /** DetailedAppInfo 列表 → JSON 数组（供各 getInstalled*AppsJson 共用，消除样板克隆） */
        private static String appsToJson(List<SystemUtils.DetailedAppInfo> apps) {
            try {
                org.json.JSONArray arr = new org.json.JSONArray();
                for (SystemUtils.DetailedAppInfo a : apps) {
                    org.json.JSONObject o = new org.json.JSONObject();
                    o.put("packageName", a.packageName);
                    o.put("appName", a.appName);
                    o.put("enabled", a.enabled);
                    o.put("isSystem", a.isSystemApp);
                    arr.put(o);
                }
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public String getInstalledMusicAppsJson() {
            return safeCall("[]", () -> {
                return appsToJson(SystemUtils.getInstalledMusicApps(MainActivity.this));
            });
        }

        @JavascriptInterface
        public int getCurrentMusicVolume() {
            try {
                android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    return am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                }
            } catch (Throwable ignored) {}
            return 15;
        }

        @JavascriptInterface
        public String getInstalledNaviAppsJson() {
            return safeCall("[]", () -> {
                return appsToJson(SystemUtils.getInstalledNaviApps(MainActivity.this));
            });
        }

        @JavascriptInterface
        public boolean setVehicleAutomationIntSetting(final String key, final int value) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        prefs.edit().putInt(key, value).apply();
                        VehicleAutomationService.syncState(MainActivity.this);
                        AppLogger.i("座舱自动化", "更新数值设置: " + key + " -> " + value);
                    } catch (Exception e) {
                        AppLogger.e("座舱自动化", "更新设置失败: " + e.getMessage());
                    }
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean setVehicleAutomationSetting(final String key, final boolean enabled) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        prefs.edit().remove(key).putBoolean(key, enabled).apply();
                        VehicleAutomationService.syncState(MainActivity.this);
                        if ("wheel_master_switch".equals(key)) {
                            new SteeringWheelKeyManager(MainActivity.this).syncMediaKeyReceiverState();
                        }
                        AppLogger.i("座舱自动化", "更新设置项: " + key + " -> " + enabled);
                    } catch (Exception e) {
                        AppLogger.e("座舱自动化", "更新设置失败: " + e.getMessage());
                    }
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean setVehicleAutomationSetting(final String key, final String value) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                            prefs.edit().remove(key).putBoolean(key, Boolean.parseBoolean(value)).apply();
                        } else {
                            prefs.edit().remove(key).putString(key, value).apply();
                        }
                        VehicleAutomationService.syncState(MainActivity.this);
                        AppLogger.i("座舱自动化", "更新字符串设置项: " + key + " -> " + value);
                    } catch (Exception e) {
                        AppLogger.e("座舱自动化", "更新字符串设置失败: " + e.getMessage());
                    }
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean setVehicleAutomationSettingInt(final String key, final int value) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        prefs.edit().putInt(key, value).apply();
                        VehicleAutomationService.syncState(MainActivity.this);
                        AppLogger.i("座舱自动化", "更新整数设置项: " + key + " -> " + value);
                    } catch (Exception e) {
                        AppLogger.e("座舱自动化", "更新整数设置失败: " + e.getMessage());
                    }
                }
            });
            return true;
        }

        @JavascriptInterface
        public String getExperimentalVehicleStatus() {
            // 实验通道车辆状态 (供前端显示真实物理状态，绝不编造)
            try {
                org.json.JSONObject obj = new org.json.JSONObject();
                boolean engineRunning = VehicleAutomationService.isEngineRunningForBridge();
                obj.put("engine_running", engineRunning);
                obj.put("gear", VehicleAutomationService.lastGearPos);
                obj.put("speed_kmh", VehicleAutomationService.currentSpeedKmH);
                obj.put("battery_volt", (double) VehicleAutomationService.latestBatteryVoltage);
                obj.put("drive_mode", VehicleAutomationService.lastDriveMode);
                int fuelPct = VehicleAutomationService.currentFuelPercent;
                float fuelLit = VehicleAutomationService.currentFuelLiters;
                obj.put("fuel_percent", fuelPct);
                obj.put("fuel_liters", (double) fuelLit);
                obj.put("has_fuel_sensor", MainActivity.isCarDevice(MainActivity.this) && fuelPct >= 0);
                // 状态描述: 如实汇报，未知就是未知
                StringBuilder sb = new StringBuilder();
                sb.append(engineRunning ? "发动机运行中" : "发动机未确认运行 (可能 KEY ON/ACC)");
                if (VehicleAutomationService.currentSpeedKmH == 0) {
                    sb.append(", 零车速");
                } else {
                    sb.append(", 车速 ").append(VehicleAutomationService.currentSpeedKmH).append("km/h");
                }
                if (VehicleAutomationService.lastGearPos == 5) {
                    sb.append(", P挡");
                } else if (VehicleAutomationService.lastGearPos > 0) {
                    sb.append(", 非P挡");
                } else {
                    sb.append(", 挡位未定");
                }
                obj.put("message", sb.toString());
                obj.put("status", engineRunning ? "ok" : "unknown");
                return obj.toString();
            } catch (Throwable t) {
                try {
                    org.json.JSONObject obj = new org.json.JSONObject();
                    obj.put("status", "error");
                    obj.put("message", "状态读取失败: " + t.getMessage());
                    return obj.toString();
                } catch (Exception ignored) {
                    return "{\"status\":\"error\",\"message\":\"状态读取失败\"}";
                }
            }
        }

        @JavascriptInterface
        public boolean setWheelGestureAction(final String key, final String gesture, final String action) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        String prefKey = "wheel_action_" + key + "_" + gesture;
                        prefs.edit().putString(prefKey, action).apply();
                        if ("single".equals(gesture)) {
                            prefs.edit().putString("wheel_action_" + key, action).apply();
                        }
                        VehicleAutomationService.syncState(MainActivity.this);
                        AppLogger.i("方控设置", "设置按键手势: " + key + " [" + gesture + "] -> " + action);
                        pushDeviceInfoToWeb();
                    } catch (Exception e) {
                        AppLogger.e("方控设置", "设置手势失败: " + e.getMessage());
                    }
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean setVehicleAutomationStringSetting(final String key, final String value) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        prefs.edit().putString(key, value).apply();
                        VehicleAutomationService.syncState(MainActivity.this);
                        AppLogger.i("座舱自动化", "更新字符串设置项: " + key + " -> " + value);
                    } catch (Exception e) {
                        AppLogger.e("座舱自动化", "更新字符串设置失败: " + e.getMessage());
                    }
                }
            });
            return true;
        }

        @JavascriptInterface
        public boolean setSetting(final String key, final String value) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                            prefs.edit().putBoolean(key, Boolean.parseBoolean(value)).apply();
                        } else {
                            prefs.edit().putString(key, value).apply();
                        }
                        if ("autostart".equals(key)) {
                            setAutostartEnabled(Boolean.parseBoolean(value));
                        } else if ("floating_pill".equals(key)) {
                            toggleFloatingWindow(Boolean.parseBoolean(value));
                        } else if ("floating_mode".equals(key)) {
                            setFloatingDisplayMode(value);
                        } else if ("expert_rabbit".equals(key)) {
                            setExpertRabbitThemeEnabled(Boolean.parseBoolean(value));
                        } else if ("adb_master_switch".equals(key) || SystemUtils.KEY_ADB_MASTER_SWITCH.equals(key)) {
                            setAdbMasterSwitchEnabled(Boolean.parseBoolean(value));
                        }
                    } catch (Exception ignored) {}
                }
            });
            return true;
        }

        @JavascriptInterface
        public void testVehicleVoiceText(final String text) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    VehicleVoicePlayer.getInstance(MainActivity.this).speakText(text);
                }
            });
        }

        @JavascriptInterface
        public void playCustomAudioPath(final String filePath) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    VehicleVoicePlayer.getInstance(MainActivity.this).playCustomFile(filePath);
                }
            });
        }

        
        
        
        
        @JavascriptInterface
        public String getVoiceThemesJson() {
            return VehicleVoicePlayer.listInstalledThemesJson(MainActivity.this);
        }

        @JavascriptInterface
        public boolean setActiveVoiceTheme(final String themeName) {
            boolean success = VehicleVoicePlayer.setActiveTheme(MainActivity.this, themeName);
            if (success) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (themeName != null && !themeName.trim().isEmpty()) {
                            showToast("已整套启用语音包【" + themeName + "】");
                            VehicleVoicePlayer.getInstance(MainActivity.this).play("gear_d.mp3", "已启用语音主题 " + themeName);
                        } else {
                            showToast("已恢复出厂默认 (晓晓温婉知性原声)");
                            VehicleVoicePlayer.getInstance(MainActivity.this).play("gear_d.mp3", "已恢复出厂默认语音");
                        }
                    }
                });
            }
            return success;
        }

        @JavascriptInterface
        public boolean deleteVoiceTheme(final String themeName) {
            boolean success = VehicleVoicePlayer.deleteTheme(MainActivity.this, themeName);
            if (success) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showToast("已删除语音包【" + themeName + "】");
                    }
                });
            }
            return success;
        }

        @JavascriptInterface
        public void importVoiceZipFromDownload(final String fileName) {
            importVoiceThemeZip(fileName);
        }

        @JavascriptInterface
        public void importVoiceThemeZip(final String fileName) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    File downloadDir = SystemUtils.getAppDownloadDir();
                    File zipFile = new File(downloadDir, fileName);
                    if (!zipFile.exists()) {
                        File poolZip = new File(new File(downloadDir, "车载语音自定义"), fileName);
                        if (poolZip.exists()) {
                            zipFile = poolZip;
                        }
                    }
                    if (!zipFile.exists()) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                showToast("未找到压缩包: " + fileName);
                            }
                        });
                        return;
                    }
                    String fName = zipFile.getName();
                    int dot = fName.lastIndexOf('.');
                    final String themeName = (dot > 0) ? fName.substring(0, dot) : fName;
                    final int count = VehicleVoicePlayer.extractVoiceZip(zipFile, themeName);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (count > 0) {
                                showToast("成功导入语音包【" + themeName + "】共 " + count + " 个音频！");
                                callJs("if(window.refreshVoiceThemes) window.refreshVoiceThemes();");
                            } else {
                                showToast("解压失败或未找到有效音频文件");
                            }
                        }
                    });
                }
            }).start();
        }

        @JavascriptInterface
        public int forceRestoreFactoryVoice() {
            final int count = VehicleVoicePlayer.forceRestoreFactoryVoice(MainActivity.this);
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (count >= 0) {
                        showToast("已强制重装官方原声，共更新 " + count + " 个音频文件");
                        VehicleVoicePlayer.getInstance(MainActivity.this).play("gear_d.mp3", "官方原声已强制重装完成");
                    } else {
                        showToast("原声重装失败，请稍后重试");
                    }
                    callJs("if(window.refreshVoiceThemes) window.refreshVoiceThemes();");
                }
            });
            return count;
        }

        @JavascriptInterface
        public String scanVoiceZipsInDownload() {
            return safeCall("[]", () -> {
                File downloadDir = SystemUtils.getAppDownloadDir();
                File[] files = downloadDir.listFiles();
                JSONArray arr = new JSONArray();
                java.util.HashSet<String> seen = new java.util.HashSet<>();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile() && f.getName().toLowerCase().endsWith(".zip")) {
                            if (VehicleVoicePlayer.isVoicePackZip(f)) {
                                JSONObject obj = new JSONObject();
                                obj.put("name", f.getName());
                                obj.put("size", SystemUtils.formatFileSize(f.length()));
                                obj.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(f.lastModified())));
                                arr.put(obj);
                                seen.add(f.getName());
                            }
                        }
                    }
                }
                File poolDir = new File(downloadDir, "车载语音自定义");
                if (poolDir.exists()) {
                    File[] poolFiles = poolDir.listFiles();
                    if (poolFiles != null) {
                        for (File f : poolFiles) {
                            if (f.isFile() && f.getName().toLowerCase().endsWith(".zip") && !seen.contains(f.getName())) {
                                if (VehicleVoicePlayer.isVoicePackZip(f)) {
                                    JSONObject obj = new JSONObject();
                                    obj.put("name", f.getName());
                                    obj.put("size", SystemUtils.formatFileSize(f.length()));
                                    obj.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(f.lastModified())));
                                    arr.put(obj);
                                }
                            }
                        }
                    }
                }
                return arr.toString();
            });
        }

        @JavascriptInterface
        public String scanAudioFilesInDownload() {
            return safeCall("[]", () -> {
                File downloadDir = SystemUtils.getAppDownloadDir();
                File poolDir = new File(downloadDir, "车载语音自定义");
                if (!poolDir.exists()) poolDir.mkdirs();
                
                JSONArray arr = new JSONArray();
                java.util.HashSet<String> seenNames = new java.util.HashSet<>();

                // 1. 优先扫描专属受保护目录 /sdcard/Download/车载语音自定义/
                File[] poolFiles = poolDir.listFiles();
                if (poolFiles != null) {
                    for (File f : poolFiles) {
                        if (f.isFile()) {
                            String nameLower = f.getName().toLowerCase();
                            if (nameLower.endsWith(".mp3") || nameLower.endsWith(".wav") || nameLower.endsWith(".ogg")) {
                                JSONObject obj = new JSONObject();
                                obj.put("name", f.getName());
                                obj.put("path", f.getAbsolutePath());
                                obj.put("dir", "车载语音自定义");
                                obj.put("size", SystemUtils.formatFileSize(f.length()));
                                obj.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(f.lastModified())));
                                arr.put(obj);
                                seenNames.add(f.getName());
                            }
                        }
                    }
                }

                // 2. 兼容扫描 /sdcard/Download/ 根目录
                File[] rootFiles = downloadDir.listFiles();
                if (rootFiles != null) {
                    for (File f : rootFiles) {
                        if (f.isFile() && !seenNames.contains(f.getName())) {
                            String nameLower = f.getName().toLowerCase();
                            if (nameLower.endsWith(".mp3") || nameLower.endsWith(".wav") || nameLower.endsWith(".ogg")) {
                                JSONObject obj = new JSONObject();
                                obj.put("name", f.getName());
                                obj.put("path", f.getAbsolutePath());
                                obj.put("dir", "Download");
                                obj.put("size", SystemUtils.formatFileSize(f.length()));
                                obj.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(f.lastModified())));
                                arr.put(obj);
                            }
                        }
                    }
                }
                return arr.toString();
            });
        }

        @JavascriptInterface
        public String importCustomVoiceFile(final String itemKey, final String downloadFileName) {
            return safeCall("{\"success\":false,\"error\":\"unknown\"}", () -> {
                if (itemKey == null || itemKey.trim().isEmpty() || downloadFileName == null || downloadFileName.trim().isEmpty()) {
                    return "{\"success\":false,\"error\":\"参数为空\"}";
                }
                String cleanKey = itemKey.trim().replaceAll("[^a-zA-Z0-9_]", "");
                if (downloadFileName.contains("..") || downloadFileName.contains("/") || downloadFileName.contains("\\")) {
                    return "{\"success\":false,\"error\":\"非法文件名\"}";
                }
                File poolDir = new File(SystemUtils.getAppDownloadDir(), "车载语音自定义");
                File src = new File(poolDir, downloadFileName);
                if (!src.exists() || !src.isFile() || src.length() == 0) {
                    src = new File(SystemUtils.getAppDownloadDir(), downloadFileName);
                }
                if (!src.exists() || !src.isFile() || src.length() == 0) {
                    return "{\"success\":false,\"error\":\"源音频文件不存在\"}";
                }
                File customDir = new File("/sdcard/GeelyPilot/voices/custom");
                if (!customDir.exists()) {
                    customDir.mkdirs();
                }
                File dest = new File(customDir, cleanKey + ".mp3");
                boolean copied = SystemUtils.copyFile(src, dest);
                if (!copied) {
                    return "{\"success\":false,\"error\":\"文件复制失败\"}";
                }
                SharedPreferences prefs = PrefUtils.getAppPreferences(MainActivity.this);
                prefs.edit()
                        .putString("custom_voice_" + cleanKey, dest.getAbsolutePath())
                        .putString("custom_voice_" + cleanKey + ".mp3", dest.getAbsolutePath())
                        .putString("custom_voice_name_" + cleanKey, downloadFileName)
                        .apply();
                if ("door_fl".equals(cleanKey)) {
                    prefs.edit()
                            .putString("custom_voice_door_open", dest.getAbsolutePath())
                            .putString("custom_voice_door_open.mp3", dest.getAbsolutePath())
                            .putString("custom_voice_name_door_open", downloadFileName)
                            .apply();
                } else if ("door_open".equals(cleanKey)) {
                    prefs.edit()
                            .putString("custom_voice_door_fl", dest.getAbsolutePath())
                            .putString("custom_voice_door_fl.mp3", dest.getAbsolutePath())
                            .putString("custom_voice_name_door_fl", downloadFileName)
                            .apply();
                }
                JSONObject res = new JSONObject();
                res.put("success", true);
                res.put("path", dest.getAbsolutePath());
                res.put("originalName", downloadFileName);
                return res.toString();
            });
        }

        @JavascriptInterface
        public void testVehicleVoice(final String type) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                    VehicleVoicePlayer player = VehicleVoicePlayer.getInstance(MainActivity.this);
                    if ("door_open".equals(type)) {
                        player.play("door_open.mp3", "车门已打开");
                    } else if ("door_close".equals(type)) {
                        player.play("door_close.mp3", "车门已关好");
                    } else if ("door".equals(type) || "door_fl".equals(type) || "door_fl_enter".equals(type)) {
                        player.play("door_fl.mp3", "车主您好，欢迎回来");
                    } else if ("door_fl_close".equals(type) || "door_fl_leave".equals(type) || "door_fl_ready".equals(type) || "door_fl_exit".equals(type)) {
                        player.play("door_fl_close.mp3", "主驾车门已关闭");
                    } else if ("door_fr".equals(type) || "door_fr_enter".equals(type)) {
                        player.play("door_fr.mp3", "欢迎乘车");
                    } else if ("door_fr_close".equals(type) || "door_fr_ready".equals(type) || "door_fr_leave".equals(type) || "door_fr_exit".equals(type)) {
                        player.play("door_fr_close.mp3", "副驾车门已关好");
                    } else if ("door_rl".equals(type)) {
                        player.play("door_rl.mp3", "左后车门已打开，请注意车外环境");
                    } else if ("door_rl_close".equals(type)) {
                        player.play("door_rl_close.mp3", "左后车门已关好");
                    } else if ("door_rr".equals(type)) {
                        player.play("door_rr.mp3", "右后车门已打开，请注意车外环境");
                    } else if ("door_rr_close".equals(type)) {
                        player.play("door_rr_close.mp3", "右后车门已关好");
                    } else if ("door_rear".equals(type)) {
                        player.play("door_rl.mp3", "后车门已打开，请注意车外环境");
                    } else if ("trunk_open".equals(type)) {
                        player.play("trunk_open.mp3", "后备箱已打开");
                    } else if ("trunk_close".equals(type)) {
                        player.play("trunk_close.mp3", "后备箱已关闭");
                    } else if ("gear_d".equals(type)) {
                        player.play("gear_d.mp3", "已挂入前进挡，系好安全带，祝你一路平安");
                    } else if ("gear_r".equals(type)) {
                        player.play("gear_r.mp3", "已挂入倒车挡，请注意观察后方安全");
                    } else if ("gear_p".equals(type)) {
                        player.play("gear_p.mp3", "已挂入驻车挡");
                    } else if ("gear_n".equals(type)) {
                        player.play("gear_n.mp3", "已挂入空挡");
                    } else if ("gear_s".equals(type)) {
                        player.play("gear_s.mp3", "已挂入运动挡，动力充沛");
                    } else if ("mode_smart".equals(type)) {
                        player.play("mode_smart.mp3", "智能");
                    } else if ("mode_comfort".equals(type)) {
                        player.play("mode_comfort.mp3", "舒适");
                    } else if ("mode_eco".equals(type)) {
                        player.play("mode_eco.mp3", "经济");
                    } else if ("mode_sport".equals(type)) {
                        player.play("mode_sport.mp3", "运动");
                    } else if ("seatbelt".equals(type)) {
                        player.play("door_fr_close.mp3", "副驾已就坐，请系好安全带");
                    } else {
                        player.play("mode_smart.mp3", "智能");
                    }
                }
            });
        }

        @JavascriptInterface
        public String getTtsEngineInfo() {
            try {
                JSONObject res = new JSONObject();
                res.put("connected", true);
                res.put("engine", "native_mp3");
                res.put("name", "本地原生短音频引擎 (Pure MP3)");
                res.put("status", "已切换为纯净原生短音频架构 · 无系统TTS开销");
                return res.toString();
            } catch (Exception e) {
                return "{\"connected\":true,\"name\":\"本地原生短音频引擎\",\"status\":\"纯净MP3播放模式\",\"type\":\"native\"}";
            }
        }

        @JavascriptInterface
        public void openTtsSettings() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent sysTts = new Intent("com.android.settings.TTS_SETTINGS");
                        sysTts.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(sysTts);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "未找到语音引擎设置界面", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public boolean isCarDevice() {
            return MainActivity.isCarDevice(MainActivity.this);
        }

        @JavascriptInterface
        public boolean isAppstoreFrozen() {
            return SystemUtils.getAppDetailedState(context, "com.ecarx.appstore") == SystemUtils.APP_STATE_DISABLED;
        }

        @JavascriptInterface
        public boolean isPackageFrozen(String pkg) {
            if (pkg == null || pkg.isEmpty()) return false;
            return SystemUtils.getAppDetailedState(context, pkg) == SystemUtils.APP_STATE_DISABLED;
        }

        @JavascriptInterface
        public int getPackageDetailedState(String pkg) {
            if (pkg == null || pkg.isEmpty()) return SystemUtils.APP_STATE_NOT_INSTALLED;
            return SystemUtils.getAppDetailedState(context, pkg);
        }

        @JavascriptInterface
        public boolean isPackageInstalled(String pkg) {
            if (pkg == null || pkg.isEmpty()) return false;
            return SystemUtils.isPackageInstalled(context, pkg);
        }

        @JavascriptInterface
        public String getOilFavProvinces() {
            try {
                android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                if (!prefs.contains("geely_oil_fav_provinces")) {
                    String defaultFavs = "[\"北京\"]";
                    prefs.edit().putString("geely_oil_fav_provinces", defaultFavs).apply();
                    return defaultFavs;
                }
                return prefs.getString("geely_oil_fav_provinces", "[\"北京\"]");
            } catch (Exception e) {
                return "[\"北京\"]";
            }
        }

        @JavascriptInterface
        public void setOilFavProvinces(final String favsJson) {
            if (favsJson == null || favsJson.trim().isEmpty()) return;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        prefs.edit().putString("geely_oil_fav_provinces", favsJson).apply();
                    } catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface
        public String getOilSelectedProvince() {
            try {
                android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                return prefs.getString("geely_oil_selected_province", "北京");
            } catch (Exception e) {
                return "北京";
            }
        }

        @JavascriptInterface
        public void setOilSelectedProvince(final String province) {
            if (province == null || province.trim().isEmpty()) return;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        prefs.edit().putString("geely_oil_selected_province", province).apply();
                    } catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface
        public String getStartupNav() {
            try {
                android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                return prefs.getString("geely_startup_nav", "link");
            } catch (Exception e) {
                return "link";
            }
        }

        @JavascriptInterface
        public void setStartupNav(final String nav) {
            if (nav == null || nav.trim().isEmpty()) return;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        prefs.edit().putString("geely_startup_nav", nav.trim()).apply();
                    } catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface
        public String getLastActiveNav() {
            try {
                android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                return prefs.getString("geely_last_active_nav", "link");
            } catch (Exception e) {
                return "link";
            }
        }

        @JavascriptInterface
        public void setLastActiveNav(final String nav) {
            if (nav == null || nav.trim().isEmpty()) return;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        prefs.edit().putString("geely_last_active_nav", nav.trim()).apply();
                    } catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface
        public String getInstalledLaunchableApps() {
            JSONArray arr = new JSONArray();
            try {
                PackageManager pm = context.getPackageManager();
                Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> activities = pm.queryIntentActivities(mainIntent, 0);
                Set<String> seenPkgs = new HashSet<>();
                if (activities != null) {
                    for (ResolveInfo ri : activities) {
                        if (ri.activityInfo == null || ri.activityInfo.packageName == null) continue;
                        String pkg = ri.activityInfo.packageName;
                        if (seenPkgs.contains(pkg)) continue;
                        seenPkgs.add(pkg);
                        // 2026-09-18 用户要求：方控「自定义打开应用」需能选择缤越助手本体，
                        // 不再过滤自身包名（此前自排除导致「用户已安装」列表看不到自己）

                        JSONObject item = new JSONObject();
                        item.put("pkg", pkg);
                        String label = ri.loadLabel(pm) != null ? ri.loadLabel(pm).toString() : pkg;
                        item.put("name", label);
                        try {
                            PackageInfo pi = pm.getPackageInfo(pkg, 0);
                            item.put("version", pi.versionName != null ? pi.versionName : "");
                            boolean isSys = (pi.applicationInfo != null && (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                            item.put("isSystem", isSys);
                        } catch (Exception ignored) {
                            item.put("version", "");
                            item.put("isSystem", false);
                        }
                        arr.put(item);
                    }
                }
            } catch (Exception ignored) {}
            return arr.toString();
        }

        @JavascriptInterface
        public String getInstalledNavigationApps() {
            return AppFilterUtils.getInstalledNavigationApps(MainActivity.this);
        }
    }

    public static boolean isCarDevice(Context context) {
        return AppFilterUtils.isCarDevice(context);
    }

    public static boolean isNavigationApp(String pkg, String filename) {
        return AppFilterUtils.isNavigationApp(pkg, filename);
    }

    public static boolean isProtectedCriticalPackage(String pkg) {
        return AppFilterUtils.isProtectedCriticalPackage(pkg);
    }

    public static boolean isDangerousSystemPackage(String pkg) {
        return AppFilterUtils.isDangerousSystemPackage(pkg);
    }
}
