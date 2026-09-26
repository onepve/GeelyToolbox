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
import android.net.ConnectivityManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.bluetooth.BluetoothAdapter;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import app.onepve.geelyconsole.utils.EasMediaBridge;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

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
import app.onepve.geelyconsole.utils.SystemUtils.LogDumpProgressListener;
import app.onepve.geelyconsole.utils.ThemePatcher;
import app.onepve.geelyconsole.utils.VehicleVoicePlayer;
import app.onepve.geelyconsole.utils.SteeringWheelKeyManager;

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

    private static final String TAG = "MainActivity";
    private static final int REQ_CODE_STORAGE = 1001;
    private static final int REQ_CODE_SELECT_APK = 2001;
    private static final int REQ_CODE_SELECT_VOICE = 2002;
    private String currentSelectingVoiceKey = null;

    private WebView webView;
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

    private final BroadcastReceiver networkChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            pushNetworkStatusToWeb();
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentActivity = new WeakReference<>(this);
        AppLogger.init(this);
        hideSystemUI();
        setContentView(R.layout.activity_main);

        initWebView();
        checkAndRequestStoragePermission();
        try {
            registerReceiver(networkChangeReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
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
        // 启动时自动探测并开启白名单，若为真车环境且商店未冻结则自动执行安全冻结保护
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
        }).start();

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

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setBackgroundColor(0xFF0A0D14);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pushDeviceInfoToWeb();
                // 页面加载就绪后，静默异步拉取最新云端 apps.json 并推送前端 (纯云端无本地静态兜底)
                fetchCloudAppsAsync(false);
                if (getIntent() != null && getIntent().hasExtra("eval_js")) {
                    mainHandler.postDelayed(() -> callJs(getIntent().getStringExtra("eval_js")), 300);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new ToolboxBridge(this), "ToolboxBridge");
        webView.loadUrl("file:///android_asset/toolbox_ui.html");
    }

    public void fetchCloudAppsAsync(final boolean showToast) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    java.net.URL url = new java.net.URL("https://dl.onepve.com/GeelyToolbox/apps.json?t=" + System.currentTimeMillis());
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);
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
        }).start();
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

        // 检查系统当前默认 TTS 引擎是否发生变更（移入后台执行，杜绝主线程 IPC 阻塞）
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    VehicleVoicePlayer.getInstance(MainActivity.this).checkAndReloadTtsIfNeeded();
                } catch (Exception ignored) {}
            }
        }, "TtsReloadCheck").start();

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
                    AdbClient.execute(MainActivity.this, "pm grant com.maxmpz.equalizer android.permission.DUMP");
                    AppLogger.i("应用权限", "检测到 Poweramp 均衡器安装，已自动授予 DUMP 高级播放器追踪特权");
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
        if (isFinishing()) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
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
                    obj.put("has_system_settings", SystemUtils.isPackageInstalled(MainActivity.this, "com.android.settings"));
                    obj.put("is_car_device", isCarDevice(MainActivity.this));
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
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
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

                    // 1. 系统核心组件永久硬拦截：无论是否开启安全模式，坚决禁止替换系统设置与核心服务
                    String pkg = null;
                    try {
                        PackageManager pm = getPackageManager();
                        PackageInfo pi = pm.getPackageArchiveInfo(tempApk.getAbsolutePath(), 0);
                        if (pi != null && pi.packageName != null) {
                            pkg = pi.packageName;
                            if (isDangerousSystemPackage(pkg) || isProtectedCriticalPackage(pkg)) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        showToast("【安全拦截】检测到该 APK 为系统核心组件，禁止伪装注入！");
                                    }
                                });
                                return;
                            }
                        }
                    } catch (Exception ignored) {}

                    // 2. 检查卡主题安全防护开关（默认仅限车载导航白名单）
                    android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                    boolean safeMode = prefs.getBoolean("rabbit_safe_mode_enabled", true);
                    if (safeMode && !isNavigationApp(pkg, tempApk.getName())) {
                        final String finalPkg = pkg;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                showToast("【安全保护】当前仅允许导航专车包卡时钟伪装，常规应用请直接在文件管理中安装。");
                            }
                        });
                        return;
                    }

                    final boolean ok = ThemePatcher.packageToRabbitTheme(MainActivity.this, tempApk);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (ok) {
                                if (webView != null) {
                                    webView.evaluateJavascript("showUniversalConfirm({" +
                                            "title: '兔子时钟伪装注入成功！'," +
                                            "desc: '已成功将所选安装包伪装打包至『兔子时钟』屏保主题！<br><br>• <b>第一步</b>：前往车机主题中心应用『兔子时钟』屏保；<br>• <b>第二步</b>：一键软重启车机，开机后直接覆盖安装。'," +
                                            "btnText: '前往车机主题'," +
                                            "onConfirm: function() { callBridge('openRabbitThemeSetting'); }" +
                                            "});", null);
                                } else {
                                    showToast("兔子时钟伪装注入成功！请前往车机主题应用");
                                }
                            } else {
                                showToast("兔子时钟注入失败，请检查机身存储权限");
                            }
                        }
                    });
                } catch (Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "处理所选文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    public void softReboot() {
        Toast.makeText(this, "正在软重启车机系统...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    AdbClient.execute(MainActivity.this, "setprop ctl.restart zygote");
                } catch (Exception ignored) {
                }
            }
        }).start();
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
                } else if ("clean_memory".equals(action)) {
                    int killed = SystemUtils.cleanBackgroundProcesses(MainActivity.this);
                    Toast.makeText(MainActivity.this, "后台已清理，释放运行内存 (关闭非核心后台: " + killed + "个)", Toast.LENGTH_SHORT).show();
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
                if (webView != null) {
                    webView.evaluateJavascript("if(window.onAdbCommandPushedFromPhone) window.onAdbCommandPushedFromPhone(" + JSONObject.quote(command) + ", " + autoExec + ");", null);
                }
                String tip = autoExec ? "收到手机推送的 ADB 指令，已立即自动执行！" : "收到手机推送的 ADB 指令，已自动填入，请核对后执行！";
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
                        prefs.edit().putFloat("vehicle_real_battery_volt", 0.0f).commit();
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
        public void startAutoPilotInject(final String filename) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    File targetFile = null;
                    if (filename != null && !filename.trim().isEmpty()) {
                        File downloadDir = new File(Environment.getExternalStorageDirectory(), "Download");
                        File f = new File(downloadDir, filename.trim());
                        if (f.exists() && f.length() > 0) {
                            targetFile = f;
                        }
                    }

                    if (targetFile == null) {
                        List<File> apks = AutoPilotManager.scanDownloadedApks(MainActivity.this);
                        if (apks.isEmpty()) {
                            if (webView != null) {
                                webView.evaluateJavascript("if(window.hideAutoPilotLoading){window.hideAutoPilotLoading();}", null);
                            }
                            android.content.SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                            boolean expert = prefs.getBoolean("expert_rabbit_theme_enabled", false);
                            String msg = expert ? "未在 Download 目录检测到 APK 安装包，请先在软件中心下载或通过无线快传传输！"
                                                : "未在 Download 目录检测到地图导航类 APK，请先在软件商城下载高德/百度地图！";
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
                            return;
                        } else if (apks.size() == 1) {
                            targetFile = apks.get(0);
                        } else {
                            if (webView != null) {
                                webView.evaluateJavascript("if(window.hideAutoPilotLoading){window.hideAutoPilotLoading();}", null);
                            }
                            try {
                                JSONArray arr = new JSONArray();
                                for (File apk : apks) {
                                    JSONObject item = new JSONObject();
                                    item.put("filename", apk.getName());
                                    item.put("path", apk.getAbsolutePath());
                                    item.put("sizeStr", String.format(java.util.Locale.CHINA, "%.1f MB", apk.length() / (1024.0 * 1024.0)));
                                    arr.put(item);
                                }
                                String script = "if(window.showAutoPilotApkSelector){window.showAutoPilotApkSelector(" + arr.toString() + ");}else if(window.showAmapAutoPilotSelector){window.showAmapAutoPilotSelector(" + arr.toString() + ");}";
                                webView.evaluateJavascript(script, null);
                            } catch (Exception ignored) {}
                            return;
                        }
                    }

                    final File finalTarget = targetFile;
                    android.content.SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                    boolean expert = prefs.getBoolean("expert_rabbit_theme_enabled", false);
                    if (!expert && !isNavigationApp(null, finalTarget.getName())) {
                        if (webView != null) {
                            webView.evaluateJavascript("if(window.hideAutoPilotLoading){window.hideAutoPilotLoading();}", null);
                        }
                        Toast.makeText(context, "【安全拦截】默认仅允许高德、百度等地图软件卡时钟伪装。常规应用严禁卡主题以防死机变砖！如需测试其他应用，请在【设置】中开启专家模式。", Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (webView != null) {
                        webView.evaluateJavascript("if(window.showAutoPilotLoading){window.showAutoPilotLoading('" + finalTarget.getName() + "');}", null);
                    }

                    AutoPilotManager.getInstance().startAutoInject(MainActivity.this, finalTarget, new AutoPilotManager.AutoPilotCallback() {
                        @Override
                        public void onSuccess(final String apkName) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    pushDeviceInfoToWeb();
                                    if (webView != null) {
                                        webView.evaluateJavascript("if(window.onAutoPilotSuccess){window.onAutoPilotSuccess('" + apkName + "');}", null);
                                    }
                                }
                            });
                        }

                        @Override
                        public void onError(final String error) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (webView != null) {
                                        webView.evaluateJavascript("if(window.onAutoPilotError){window.onAutoPilotError('" + error + "');}", null);
                                    }
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
                                File downloadDir = new File(Environment.getExternalStorageDirectory(), "Download");
                                File apkFile = new File(downloadDir, filename);
                                if (!apkFile.exists() || apkFile.length() == 0) {
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(context, "未找到目标文件: " + filename + "，请先下载！", Toast.LENGTH_LONG).show();
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

                                android.content.SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                                boolean expertMode = prefs.getBoolean("expert_rabbit_theme_enabled", false);
                                if (!expertMode && !isNavigationApp(pkg, apkFile.getName())) {
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            showToast("【安全拦截】默认仅允许高德、百度等地图软件卡时钟伪装。常规应用严禁卡主题以防死机变砖！如需测试其他应用，请在【设置】中开启专家模式。");
                                        }
                                    });
                                    return;
                                }

                                final boolean ok = ThemePatcher.packageToRabbitTheme(context, apkFile);
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (ok) {
                                            pushDeviceInfoToWeb();
                                            if (webView != null) {
                                                webView.evaluateJavascript("showUniversalConfirm({" +
                                                        "title: '兔子时钟主题注入成功！'," +
                                                        "desc: '已成功将【" + filename + "】伪装打包至『兔子时钟』屏保主题！<br><br>• <b>第一步</b>：点击【前往车机主题】应用『兔子时钟』屏保；<br>• <b>第二步</b>：点击【一键软重启车机】，开机后白名单自动放行即可覆盖安装。'," +
                                                        "btnText: '前往车机主题'," +
                                                        "onConfirm: function() { callBridge('openRabbitThemeSetting'); }" +
                                                        "});", null);
                                            } else {
                                                showToast("兔子时钟主题注入成功！请前往主题中心应用");
                                            }
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
        public void openTaskManager() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            boolean toggled = false;
                            try {
                                Object service = context.getSystemService("statusbar");
                                Class<?> statusBarManager = Class.forName("android.app.StatusBarManager");
                                java.lang.reflect.Method expand = statusBarManager.getMethod("toggleRecentApps");
                                expand.invoke(service);
                                toggled = true;
                            } catch (Exception ignored) {}

                            if (!toggled) {
                                AdbClient.execute(context, "input keyevent 187");
                            }

                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(context, "已调起任务管理器，请切换到文件管理器完成安装", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }).start();
                }
            });
        }

        @JavascriptInterface
        public boolean setAutostartEnabled(boolean enabled) {
            android.content.SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("autostart_enabled", enabled).commit();
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
                            .commit();
                    pushDeviceInfoToWeb();
                }
            } catch (Exception ignored) {}
            try {
                android.content.SharedPreferences rPrefs = context.getSharedPreferences("rabbit_theme_prefs", Context.MODE_PRIVATE);
                rPrefs.edit().putBoolean("pending_install_after_reboot", false).commit();
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
                        prefs.edit().putBoolean("floating_enabled", enable).commit();
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
        public boolean setFloatingDisplayMode(final String mode) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        prefs.edit().putString("floating_display_mode", mode).commit();
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
        public void softReboot() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.softReboot();
                }
            });
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
                    AdbClient.execute(context, "pm clear " + pkg);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "已清除应用数据与缓存: " + pkg, Toast.LENGTH_SHORT).show();
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
                                showToast("已发送卸载指令: " + pkg);
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
                                                        String script = "if(window.updateToolboxSelfError){window.updateToolboxSelfError('安装包校验失败，文件损坏，请重新下载');}";
                                                        webView.evaluateJavascript(script, null);
                                                        showToast("安装包校验失败，文件损坏，请重新下载");
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
            if (!prefs.getBoolean(SystemUtils.KEY_ADB_MASTER_SWITCH, true)) {
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
                int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
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
        public void toggleWifi(boolean enable) {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wm.setWifiEnabled(enable);
                showToast(enable ? "正在开启车机 Wi-Fi..." : "已关闭车机 Wi-Fi");
            } else {
                AdbClient.execute(MainActivity.this, "svc wifi " + (enable ? "enable" : "disable"));
                showToast(enable ? "已下发指令开启 Wi-Fi" : "已下发指令关闭 Wi-Fi");
            }
        }

        @JavascriptInterface
        public void openWifiSettings() {
            openSystemSettingsPage("android.settings.WIFI_SETTINGS");
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
                        AppLogger.i("系统退出", "车主点击退出，安全退入后台并返回系统桌面，彻底杜绝回弹原厂多媒体");
                        moveTaskToBack(true);
                        Intent home = new Intent(Intent.ACTION_MAIN);
                        home.addCategory(Intent.CATEGORY_HOME);
                        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(home);
                    } catch (Throwable t) {
                        try {
                            moveTaskToBack(true);
                        } catch (Throwable ignored) {}
                    }
                }
            });
        }

        @JavascriptInterface
        public void showToast(String msg) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                }
            });
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
                        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        prefs.edit().remove("custom_voice_" + voiceKey).apply();
                        File voiceDir = new File(getFilesDir(), "custom_voices");
                        File dest = new File(voiceDir, voiceKey);
                        if (dest.exists()) dest.delete();
                        Toast.makeText(MainActivity.this, "已恢复为内置默认语音", Toast.LENGTH_SHORT).show();
                        if (webView != null) {
                            webView.loadUrl("javascript:refreshVehicleAutoUI()");
                        }
                    } catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface
        public boolean setWheelControlStringSetting(final String key, final String value) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        prefs.edit().putString(key, value).commit();
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
                            .edit().putString("voice_item_channel_" + key, v).commit();
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
                    int v = Math.max(-15, Math.min(15, offset));
                    getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE)
                            .edit().putInt("voice_item_offset_" + key, v).commit();
                    AppLogger.i("声音设置", "增益[" + key + "] -> " + v);
                } catch (Exception e) {
                    AppLogger.e("声音设置", "增益更新失败: " + e.getMessage());
                }
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
                        prefs.edit().putInt(key, value).commit();
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
                        prefs.edit().putBoolean(key, enabled).commit();
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
                        prefs.edit().putString(key, value).commit();
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
                        prefs.edit().putInt(key, value).commit();
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
                        prefs.edit().putString(prefKey, action).commit();
                        if ("single".equals(gesture)) {
                            prefs.edit().putString("wheel_action_" + key, action).commit();
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
                        prefs.edit().putString(key, value).commit();
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
                            prefs.edit().putBoolean(key, Boolean.parseBoolean(value)).commit();
                        } else {
                            prefs.edit().putString(key, value).commit();
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
            new Thread(new Runnable() {
                @Override
                public void run() {
                    File downloadDir = SystemUtils.getAppDownloadDir();
                    File zipFile = new File(downloadDir, fileName);
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
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile() && f.getName().toLowerCase().endsWith(".zip")) {
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
                return arr.toString();
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
                        player.play("door_fl_enter.mp3", "车主您好，欢迎回来");
                    } else if ("door_fl_ready".equals(type)) {
                        player.play("door_fl_ready.mp3", "准备启程，请系好安全带");
                    } else if ("door_fl_exit".equals(type)) {
                        player.play("door_fl_exit.mp3", "开门请注意后方来车");
                    } else if ("door_fl_leave".equals(type) || "door_fl_close".equals(type)) {
                        player.play("door_fl_leave.mp3", "车门已关好，请锁车");
                    } else if ("door_fr".equals(type) || "door_fr_enter".equals(type)) {
                        String role = prefs.getString("passenger_voice_role", "female");
                        if ("queen".equals(role)) player.play("door_fr_queen_enter.mp3", "恭迎女王殿下");
                        else if ("female".equals(role) || "original".equals(role) || "standard".equals(role)) player.play("door_fr_enter.mp3", "欢迎乘车");
                        else player.play("door_fr_princess_enter.mp3", "欢迎公主上车");
                    } else if ("door_fr_ready".equals(type) || "door_fr_close".equals(type)) {
                        String role = prefs.getString("passenger_voice_role", "female");
                        if ("queen".equals(role)) player.play("door_fr_queen_ready.mp3", "女王殿下已就座，请系好安全带");
                        else if ("female".equals(role) || "original".equals(role) || "standard".equals(role)) player.play("door_fr_ready.mp3", "车门已关好，请系好安全带");
                        else player.play("door_fr_princess_ready.mp3", "公主请系好安全带");
                    } else if ("door_fr_exit".equals(type)) {
                        String role = prefs.getString("passenger_voice_role", "female");
                        if ("queen".equals(role)) player.play("door_fr_queen_exit.mp3", "女王殿下请慢走，请带好随身贵重物品，注意后方来车");
                        else if ("female".equals(role) || "original".equals(role) || "standard".equals(role)) player.play("door_fr_exit.mp3", "开门请注意后方来车，请带好随身物品");
                        else player.play("door_fr_princess_exit.mp3", "公主请下车，小包包和手机别落下哦，注意后方来车");
                    } else if ("door_fr_leave".equals(type)) {
                        String role = prefs.getString("passenger_voice_role", "female");
                        if ("queen".equals(role)) player.play("door_fr_queen_leave.mp3", "恭送女王殿下，期待下次为您服务");
                        else if ("female".equals(role) || "original".equals(role) || "standard".equals(role)) player.play("door_fr_leave.mp3", "副驾车门已关好，再见，祝您一路顺风");
                        else player.play("door_fr_princess_leave.mp3", "公主再见，今天也要开心哦");
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
                        if (!player.isTtsReady()) {
                            player.ensureTtsReady();
                            Toast.makeText(MainActivity.this, "语音合成引擎正在唤醒连接中，请稍候再试...", Toast.LENGTH_SHORT).show();
                        }
                        player.speakText("吉利车机座舱智能语音联动测试成功");
                    }
                }
            });
        }

        @JavascriptInterface
        public String getTtsEngineInfo() {
            try {
                VehicleVoicePlayer player = VehicleVoicePlayer.getInstance(MainActivity.this);
                // 检测系统首选引擎是否有变化，有变则静默刷新
                player.checkAndReloadTtsIfNeeded();

                boolean ready = player.isTtsReady();
                String activeEngine = player.getActiveTtsEngine();

                String engineLabel = "系统默认语音引擎";
                if (activeEngine != null && !activeEngine.isEmpty() && !"none".equals(activeEngine)) {
                    try {
                        PackageManager pm = getPackageManager();
                        ApplicationInfo ai = pm.getApplicationInfo(activeEngine, 0);
                        CharSequence label = pm.getApplicationLabel(ai);
                        if (label != null && label.length() > 0) {
                            engineLabel = label.toString();
                        }
                    } catch (Exception e) {
                        engineLabel = activeEngine;
                    }
                }

                JSONObject res = new JSONObject();
                res.put("connected", ready);
                res.put("engine", activeEngine);
                if (activeEngine != null && !activeEngine.isEmpty() && !"none".equals(activeEngine)) {
                    res.put("name", engineLabel + " (" + activeEngine + ")");
                } else {
                    res.put("name", engineLabel);
                }

                if (ready) {
                    res.put("status", "已成功直连系统首选语音合成引擎 · 声线就绪");
                } else {
                    res.put("status", "语音合成引擎未就绪，可点击【TTS设置】选择或切换首选引擎");
                }
                return res.toString();
            } catch (Exception e) {
                return "{\"connected\":false,\"name\":\"系统语音引擎\",\"status\":\"TTS状态获取异常\",\"type\":\"native\"}";
            }
        }

        @JavascriptInterface
        public void openTtsSettings() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    // 1. 后台静默保障小爱权限与免CTA拦截
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                SystemUtils.configureXiaoAiTts(MainActivity.this);
                            } catch (Exception ignored) {}
                        }
                    }).start();

                    // 2. 优先调起系统首选语音引擎设置（即车主选择 XCTtsEngine / 系统语音引擎 核心界面）
                    try {
                        Intent sysTts = new Intent("com.android.settings.TTS_SETTINGS");
                        sysTts.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(sysTts);
                        return;
                    } catch (Exception e) {
                        Log.i("MainActivity", "Direct TTS_SETTINGS failed, try privileged fallback: " + e.getMessage());
                    }

                    // 3. 特权 Shell 容灾唤起系统 TTS 设置
                    try {
                        String out = SystemUtils.executePrivileged(MainActivity.this, "am start -a com.android.settings.TTS_SETTINGS");
                        if (out != null && !out.contains("Error") && !out.contains("does not exist")) {
                            return;
                        }
                    } catch (Exception ignored) {}

                    // 4. 小爱专属设置界面备选
                    try {
                        SystemUtils.executePrivileged(MainActivity.this,
                                "am start -n com.xiaomi.mibrain.speech/.tts.TtsSettingsActivity");
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
