package app.onepve.geelyconsole;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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
import android.webkit.ValueCallback;
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
import app.onepve.geelyconsole.utils.DialogHelper;
import app.onepve.geelyconsole.utils.DownloadManager;
import app.onepve.geelyconsole.utils.FloatingWindowManager;
import app.onepve.geelyconsole.utils.SystemUtils;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentActivity = new WeakReference<>(this);
        AppLogger.init(this);
        hideSystemUI();
        setContentView(R.layout.activity_main);

        initWebView();
        checkAndRequestStoragePermission();
        AppLogger.i("应用启动", "吉利工具箱界面启动完成");
        // 启动时自动探测并开启白名单，若为真车环境且商店未冻结则自动执行安全冻结保护
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!SystemUtils.isApkVerifyWhitelistEnabled()) {
                        SystemUtils.enableApkVerifyWhitelist(MainActivity.this);
                    }
                    if (isCarDevice(MainActivity.this)) {
                        boolean isAppstoreFrozen = (SystemUtils.getAppDetailedState(MainActivity.this, "com.ecarx.appstore") == SystemUtils.APP_STATE_DISABLED);
                        if (!isAppstoreFrozen && SystemUtils.isPackageInstalled(MainActivity.this, "com.ecarx.appstore")) {
                            SystemUtils.setPackageEnabled(MainActivity.this, "com.ecarx.appstore", false);
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
                if (getIntent() != null && getIntent().hasExtra("eval_js")) {
                    mainHandler.postDelayed(() -> callJs(getIntent().getStringExtra("eval_js")), 300);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new ToolboxBridge(this), "ToolboxBridge");
        webView.loadUrl("file:///android_asset/toolbox_ui.html");
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
        mainHandler.post(statusTicker);

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
        // 关键修复：坚决禁止在 onPause 中恢复悬浮胶囊！
        // 在应用内部切换页面、打开弹窗或输入焦点变化时均会触发 onPause，在此处恢复胶囊会导致胶囊疯狂闪现！
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
            @Override
            public void run() {
                try {
                    if (!isForeground) {
                        android.content.SharedPreferences sp = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        if (sp.getBoolean("floating_enabled", false)) {
                            Intent showPill = new Intent(MainActivity.this, FloatingWindowService.class);
                            showPill.setAction(FloatingWindowService.ACTION_SHOW);
                            startService(showPill);
                            AppLogger.i("悬浮胶囊", "已稳定处于外部桌面或第三方应用，平滑挂载悬浮小胶囊");
                        }
                    }
                } catch (Exception ignored) {}
            }
        };
        mainHandler.postDelayed(pendingShowPillRunnable, 1200);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
            }
        }
    }

    public void pushDeviceInfoToWeb() {
        if (webView == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
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
                    obj.put("rabbit", ThemePatcher.getRabbitDisguiseInfo(MainActivity.this));
                    obj.put("rabbitPostReboot", ThemePatcher.checkRabbitPostRebootStatus(MainActivity.this));
                    android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                    obj.put("autostart", prefs.getBoolean("autostart_enabled", false));
                    obj.put("floating_enabled", prefs.getBoolean("floating_enabled", false));
                    obj.put("floating_display_mode", prefs.getString("floating_display_mode", "name"));
                    obj.put("rabbit_safe_mode", prefs.getBoolean("rabbit_safe_mode_enabled", true));
                    obj.put("expert_rabbit_enabled", prefs.getBoolean("expert_rabbit_theme_enabled", false));
                    obj.put("has_system_settings", SystemUtils.isPackageInstalled(MainActivity.this, "com.android.settings"));
                    obj.put("is_car_device", isCarDevice(MainActivity.this));
                    obj.put("logPath", AppLogger.getLogFilePath());
                    obj.put("logSize", AppLogger.getLogFileSizeStr());
                    String script = "if(window.updateDeviceInfo){window.updateDeviceInfo('" + obj.toString() + "');}";
                    webView.evaluateJavascript(script, null);
                } catch (Exception ignored) {
                }
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
                } else {
                    Toast.makeText(MainActivity.this, "收到手机快传文件: " + file.getName(), Toast.LENGTH_LONG).show();
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
                }
            }
        });
    }
    @Override
    public void onAdbCommandPushed(final String command) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (webView != null) {
                    webView.evaluateJavascript("if(window.onAdbCommandPushedFromPhone) window.onAdbCommandPushedFromPhone(" + JSONObject.quote(command) + ");", null);
                }
                Toast.makeText(MainActivity.this, "收到手机推送的 ADB 指令，已自动填入，请核对后执行！", Toast.LENGTH_LONG).show();
            }
        });
    }

    // JSBridge
    public class ToolboxBridge {
        private final Context context;

        public ToolboxBridge(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public String getDeviceInfo() {
            try {
                SystemUtils.NetStatus net = SystemUtils.getNetworkStatus();
                JSONObject obj = new JSONObject();
                obj.put("ip", net.ip);
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
                android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                boolean isBeta = ver.toLowerCase().contains("beta");
                obj.put("is_beta", isBeta);
                obj.put("autostart", prefs.getBoolean("autostart_enabled", false));
                obj.put("floating_enabled", prefs.getBoolean("floating_enabled", false));
                obj.put("floating_display_mode", prefs.getString("floating_display_mode", "name"));
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
            try {
                JSONObject obj = new JSONObject();
                obj.put("fl", VehicleAutomationService.currentDoorFL);
                obj.put("fr", VehicleAutomationService.currentDoorFR);
                obj.put("rl", VehicleAutomationService.currentDoorRL);
                obj.put("rr", VehicleAutomationService.currentDoorRR);
                obj.put("trunk", VehicleAutomationService.currentTrunk);
                obj.put("gear", VehicleAutomationService.lastGearPos);
                obj.put("mode", VehicleAutomationService.lastDriveMode);
                return obj.toString();
            } catch (Exception e) {
                return "{}";
            }
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
            try {
                JSONObject obj = new JSONObject();
                obj.put("path", AppLogger.getLogFilePath());
                obj.put("size", AppLogger.getLogFileSizeStr());
                return obj.toString();
            } catch (Exception e) {
                return "{\"path\":\"/sdcard/Download/geely_toolbox.log\",\"size\":\"0 KB\"}";
            }
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

        @JavascriptInterface
        public void hardReboot() {
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
                        if (FloatingWindowService.isRunning) {
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
            if (freeze && isProtectedCriticalPackage(pkg)) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showToast("【安全保护】应用【" + pkg + "】为车机底层核心运行组件，禁止冻结！");
                    }
                });
                return;
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    SystemUtils.setPackageEnabled(context, pkg, !freeze);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, (freeze ? "已安全冻结: " : "已解冻恢复: ") + pkg, Toast.LENGTH_SHORT).show();
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
            try {
                SystemUtils.OtaExtractResult res = SystemUtils.extractOtaUrl();
                JSONObject obj = new JSONObject();
                obj.put("success", res != null && res.success);
                obj.put("url", res != null ? res.url : "");
                obj.put("version", res != null ? res.version : "");
                obj.put("details", res != null ? res.details : "");
                return obj.toString();
            } catch (Exception e) {
                return "{\"success\":false,\"url\":\"\"}";
            }
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
            // 归一化版本号：兼容新旧前端（旧版传 "GeelyPilot_1.7.7.apk"，新版传 "1.7.7"），
            // 统一落盘为干净的 GeelyToolbox_v1.7.7.apk，避免出现 GeelyToolbox_vGeelyPilot_xxx.apk.apk 双重后缀
            String ver = rawVer == null ? "" : rawVer.trim();
            if (ver.startsWith("GeelyToolbox_v")) ver = ver.substring("GeelyToolbox_v".length());
            if (ver.startsWith("GeelyPilot_")) ver = ver.substring("GeelyPilot_".length());
            if (ver.toLowerCase().endsWith(".apk")) ver = ver.substring(0, ver.length() - 4);
            ver = ver.trim();
            if (ver.isEmpty()) ver = "latest";
            final String apkFileName = "GeelyToolbox_v" + ver + ".apk";
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    showToast("开始下载工具箱新版本...");
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
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    String script = "if(window.updateToolboxSelfDone){window.updateToolboxSelfDone();}";
                                    webView.evaluateJavascript(script, null);
                                    showToast("工具箱新版本下载完成，正在通过安全通道调起安装...");
                                    SystemUtils.installApkViaProvider(MainActivity.this, savedFile);
                                }
                            });
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
        public boolean toggleFreezeAppStore(final boolean freeze) {
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
            try {
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
            } catch (Exception e) {
                return "{\"count\":0,\"size_mb\":\"0.00\",\"path\":\"/sdcard/Download/\"}";
            }
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
                    FloatingWindowService.ensureServiceStarted(MainActivity.this);
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
            try {
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
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public String checkLocalAppStatuses(String jsonArrayStr) {
            try {
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
            } catch (Exception e) {
                return "{}";
            }
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
            return SystemUtils.dumpFullSystemLogcat(MainActivity.this).toString();
        }

        @JavascriptInterface
        public void refreshCloudApps() {
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
                                    Toast.makeText(context, "车载软件列表已刷新至最新 (๑•̀ㅂ•́)و", Toast.LENGTH_SHORT).show();
                                }
                            });
                        } else {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (webView != null) {
                                        webView.evaluateJavascript("if(window.onCloudAppsRefreshComplete) window.onCloudAppsRefreshComplete(false, 'HTTP " + respCode + "');", null);
                                    }
                                    Toast.makeText(context, "无法连接云端软件源", Toast.LENGTH_SHORT).show();
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
                                Toast.makeText(context, "刷新云端软件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }).start();
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

        @JavascriptInterface
        public void exitApp() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (FloatingWindowService.isRunning) {
                            Intent floatIntent = new Intent(MainActivity.this, FloatingWindowService.class);
                            floatIntent.setAction(FloatingWindowService.ACTION_HIDE);
                            startService(floatIntent);
                        }
                    } catch (Exception ignored) {}
                    try {
                        finishAffinity();
                    } catch (Exception ignored) {}
                    try {
                        android.os.Process.killProcess(android.os.Process.myPid());
                    } catch (Exception ignored) {}
                    System.exit(0);
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
            try {
                android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                org.json.JSONObject obj = new org.json.JSONObject();
                
                // 核心三大物理独立总开关：语音总开关、方控接管总开关 (自启动由 deviceInfo.autostart 独立管控)
                obj.put("voice_master_switch", prefs.getBoolean("voice_master_switch", true));
                obj.put("wheel_master_switch", prefs.getBoolean("wheel_master_switch", true));

                // 360 与车灯联动
                obj.put("vehicle_turn_360_enabled", prefs.getBoolean("vehicle_turn_360_enabled", false));
                obj.put("vehicle_light_nav_enabled", prefs.getBoolean("vehicle_light_nav_enabled", false));
                obj.put("vehicle_flameout_voice_enabled", prefs.getBoolean("vehicle_flameout_voice_enabled", false));

                // 播报音频输出通道 (music | nav | notification)
                obj.put("voice_audio_channel", prefs.getString("voice_audio_channel", "music"));

                // 四门通用与分门配置 (默认启用通用智能车门语音)
                obj.put("voice_door_mode_universal", prefs.getBoolean("voice_door_mode_universal", true));
                obj.put("voice_enable_door_universal_open", prefs.getBoolean("voice_enable_door_universal_open", true));
                obj.put("voice_enable_door_universal_close", prefs.getBoolean("voice_enable_door_universal_close", true));

                // 四门与尾门迎宾与关门 (默认全开)
                obj.put("voice_enable_door_fl", prefs.getBoolean("voice_enable_door_fl", true));
                obj.put("voice_enable_door_fl_close", prefs.getBoolean("voice_enable_door_fl_close", true));
                obj.put("voice_enable_door_fr", prefs.getBoolean("voice_enable_door_fr", true));
                obj.put("voice_enable_door_fr_close", prefs.getBoolean("voice_enable_door_fr_close", true));
                obj.put("voice_enable_door_rl", prefs.getBoolean("voice_enable_door_rl", true));
                obj.put("voice_enable_door_rl_close", prefs.getBoolean("voice_enable_door_rl_close", true));
                obj.put("voice_enable_door_rr", prefs.getBoolean("voice_enable_door_rr", true));
                obj.put("voice_enable_door_rr_close", prefs.getBoolean("voice_enable_door_rr_close", true));
                obj.put("voice_enable_door_rear", prefs.getBoolean("voice_enable_door_rear", true));
                obj.put("voice_enable_trunk_open", prefs.getBoolean("voice_enable_trunk_open", true));
                obj.put("voice_enable_trunk_close", prefs.getBoolean("voice_enable_trunk_close", true));

                // 4 大挡位播报 (D/R/P/N 默认均开启)
                obj.put("voice_enable_gear_d", prefs.getBoolean("voice_enable_gear_d", true));
                obj.put("voice_enable_gear_r", prefs.getBoolean("voice_enable_gear_r", true));
                obj.put("voice_enable_gear_p", prefs.getBoolean("voice_enable_gear_p", true));
                obj.put("voice_enable_gear_n", prefs.getBoolean("voice_enable_gear_n", true));

                // 4 大功能模式播报 (智能/舒适/经济/运动 默认均开启)
                obj.put("voice_enable_mode_smart", prefs.getBoolean("voice_enable_mode_smart", true));
                obj.put("voice_enable_mode_comfort", prefs.getBoolean("voice_enable_mode_comfort", true));
                obj.put("voice_enable_mode_eco", prefs.getBoolean("voice_enable_mode_eco", true));
                obj.put("voice_enable_mode_sport", prefs.getBoolean("voice_enable_mode_sport", true));

                // 默认值自适应：未设置时根据米小江是否安装智能选定
                boolean hasCarMedia = SystemUtils.isPackageInstalled(MainActivity.this, "com.ecarx.carmedia");
                String defaultWheelMode = hasCarMedia ? "carmedia_first" : "toolbox_alone";
                String defaultModeAction = hasCarMedia ? "default" : "open_360";

                obj.put("wheel_control_mode", prefs.getString("wheel_control_mode", defaultWheelMode));
                obj.put("wheel_action_mute", prefs.getString("wheel_action_mute", "default"));
                obj.put("wheel_action_mode", prefs.getString("wheel_action_mode", defaultModeAction));
                obj.put("wheel_action_ok", prefs.getString("wheel_action_ok", "default"));
                obj.put("has_carmedia_installed", hasCarMedia);

                // 方控多手势映射 (单击/双击/长按)
                String[] gestureKeys = {"ok", "mute", "mode", "next", "prev", "custom"};
                String[] gestures = {"single", "double", "long"};
                for (String k : gestureKeys) {
                    for (String g : gestures) {
                        String pKey = "wheel_action_" + k + "_" + g;
                        String def = "default";
                        if ("single".equals(g)) {
                            if ("mode".equals(k)) def = "open_360";
                            else if ("next".equals(k)) def = "next_track";
                            else if ("prev".equals(k)) def = "prev_track";
                        }
                        obj.put(pKey, prefs.getString(pKey, prefs.getString("wheel_action_" + k, def)));
                    }
                }

                // 兼容历史老 Key 别名
                obj.put("turn_360", prefs.getBoolean("vehicle_turn_360_enabled", false));
                obj.put("light_nav", prefs.getBoolean("vehicle_light_nav_enabled", false));
                obj.put("flameout_voice", prefs.getBoolean("vehicle_flameout_voice_enabled", false));
                obj.put("voice_door_fl", prefs.getBoolean("voice_enable_door_fl", true));
                obj.put("voice_door_fl_close", prefs.getBoolean("voice_enable_door_fl_close", true));
                obj.put("voice_door_fr", prefs.getBoolean("voice_enable_door_fr", false));
                obj.put("voice_door_fr_close", prefs.getBoolean("voice_enable_door_fr_close", false));
                obj.put("voice_door_rl", prefs.getBoolean("voice_enable_door_rl", false));
                obj.put("voice_door_rl_close", prefs.getBoolean("voice_enable_door_rl_close", false));
                obj.put("voice_door_rr", prefs.getBoolean("voice_enable_door_rr", false));
                obj.put("voice_door_rr_close", prefs.getBoolean("voice_enable_door_rr_close", false));
                obj.put("voice_door_rear", prefs.getBoolean("voice_enable_door_rear", false));
                obj.put("voice_trunk_open", prefs.getBoolean("voice_enable_trunk_open", false));
                obj.put("voice_trunk_close", prefs.getBoolean("voice_enable_trunk_close", false));
                obj.put("voice_gear_d", prefs.getBoolean("voice_enable_gear_d", true));
                obj.put("voice_gear_r", prefs.getBoolean("voice_enable_gear_r", true));

                obj.put("custom_door_fl", !prefs.getString("custom_voice_door_fl.mp3", "").isEmpty());
                obj.put("custom_door_fl_close", !prefs.getString("custom_voice_door_fl_close.mp3", "").isEmpty());
                obj.put("custom_door_fr", !prefs.getString("custom_voice_door_fr.mp3", "").isEmpty());
                obj.put("custom_door_fr_close", !prefs.getString("custom_voice_door_fr_close.mp3", "").isEmpty());
                obj.put("custom_door_rl", !prefs.getString("custom_voice_door_rl.mp3", "").isEmpty());
                obj.put("custom_door_rl_close", !prefs.getString("custom_voice_door_rl_close.mp3", "").isEmpty());
                obj.put("custom_door_rr", !prefs.getString("custom_voice_door_rr.mp3", "").isEmpty());
                obj.put("custom_door_rr_close", !prefs.getString("custom_voice_door_rr_close.mp3", "").isEmpty());
                obj.put("custom_door_rear", !prefs.getString("custom_voice_door_rl.mp3", "").isEmpty());
                obj.put("custom_trunk_open", !prefs.getString("custom_voice_trunk_open.mp3", "").isEmpty());
                obj.put("custom_trunk_close", !prefs.getString("custom_voice_trunk_close.mp3", "").isEmpty());
                obj.put("custom_gear_d", !prefs.getString("custom_voice_gear_d.mp3", "").isEmpty());
                obj.put("custom_gear_r", !prefs.getString("custom_voice_gear_r.mp3", "").isEmpty());
                obj.put("custom_flameout", !prefs.getString("custom_voice_flameout.mp3", "").isEmpty());

                obj.put("reverse_volume_boost", prefs.getInt("reverse_volume_boost", 6));
                obj.put("wheel_long_press_ms", prefs.getInt("wheel_long_press_ms", 1500));
                obj.put("vehicle_monitor_engine_mode", prefs.getString("vehicle_monitor_engine_mode", "log_mcu"));
                obj.put("wheel_monitor_engine_mode", prefs.getString("wheel_monitor_engine_mode", "hybrid_dual"));

                return obj.toString();
            } catch (Exception e) {
                return "{}";
            }
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
        public void setVoiceVolumeOffset(final int offset) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        prefs.edit().putInt("voice_volume_offset", offset).commit();
                        showToast("播报音量补偿已设为: " + (offset >= 0 ? "+" + offset : offset) + " 格");
                    } catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface
        public int getVoiceVolumeOffset() {
            try {
                android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                return prefs.getInt("voice_volume_offset", 0);
            } catch (Exception e) {
                return 0;
            }
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
        public String scanVoiceZipsInDownload() {
            try {
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
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public void testVehicleVoice(final String type) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    VehicleVoicePlayer player = VehicleVoicePlayer.getInstance(MainActivity.this);
                    if ("door_open".equals(type)) {
                        player.play("door_open.mp3", "车门已打开");
                    } else if ("door_close".equals(type)) {
                        player.play("door_close.mp3", "车门已关好");
                    } else if ("door".equals(type) || "door_fl".equals(type)) {
                        player.play("door_fl.mp3", "主驾车门已打开，请注意后方来车");
                    } else if ("door_fl_close".equals(type)) {
                        player.play("door_fl_close.mp3", "主驾车门已关好");
                    } else if ("door_fr".equals(type)) {
                        player.play("door_fr.mp3", "副驾车门已打开，请注意安全");
                    } else if ("door_fr_close".equals(type)) {
                        player.play("door_fr_close.mp3", "副驾已就坐，请系好安全带");
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
                        player.play("mode_smart.mp3", "智能模式");
                    } else if ("mode_comfort".equals(type)) {
                        player.play("mode_comfort.mp3", "舒适模式");
                    } else if ("mode_eco".equals(type)) {
                        player.play("mode_eco.mp3", "经济模式");
                    } else if ("mode_sport".equals(type)) {
                        player.play("mode_sport.mp3", "运动模式");
                    } else if ("flameout".equals(type)) {
                        player.play("flameout.mp3", "车辆已熄火，请带好随身物品");
                    } else if ("seatbelt".equals(type)) {
                        player.play("door_fr_close.mp3", "副驾已就坐，请系好安全带");
                    } else {
                        player.speakText("吉利车机座舱智能语音联动测试成功");
                    }
                }
            });
        }

        @JavascriptInterface
        public String getTtsEngineInfo() {
            try {
                getPackageManager().getPackageInfo("com.xiaomi.mibrain.speech", 0);
                return "{\"connected\":true,\"name\":\"小爱语音合成引擎 (XiaoAi TTS 1.5.1)\",\"status\":\"已成功连接小爱语音引擎 · 专车TTS声线就绪\",\"type\":\"xiaoai\"}";
            } catch (Exception e) {
                return "{\"connected\":false,\"name\":\"系统原厂默认引擎\",\"status\":\"未检测到小爱TTS包，当前使用系统底层默认语音引擎\",\"type\":\"native\"}";
            }
        }

        @JavascriptInterface
        public void openTtsSettings() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent xiaoaiIntent = new Intent();
                        xiaoaiIntent.setClassName("com.xiaomi.mibrain.speech", "com.xiaomi.mibrain.speech.tts.TtsSettingsActivity");
                        xiaoaiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(xiaoaiIntent);
                        return;
                    } catch (Exception ignored) {
                    }

                    try {
                        Intent sysTts = new Intent("com.android.settings.TTS_SETTINGS");
                        sysTts.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(sysTts);
                    } catch (Exception e) {
                        Toast.makeText(context, "未找到语音引擎设置界面", Toast.LENGTH_SHORT).show();
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
                        
                        // 过滤掉当前工具箱本体
                        if (context.getPackageName().equals(pkg)) continue;

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
            JSONArray arr = new JSONArray();
            try {
                PackageManager pm = context.getPackageManager();
                List<PackageInfo> installed = pm.getInstalledPackages(0);
                for (PackageInfo pi : installed) {
                    if (pi.packageName == null) continue;
                    String pkg = pi.packageName.toLowerCase();
                    if (isNavigationApp(pkg, null) || pkg.equals("com.autonavi.amapauto")) {
                        JSONObject item = new JSONObject();
                        item.put("pkg", pi.packageName);
                        String label = pi.applicationInfo != null ? pm.getApplicationLabel(pi.applicationInfo).toString() : pi.packageName;
                        item.put("name", label);
                        item.put("version", pi.versionName != null ? pi.versionName : "");
                        boolean isSys = (pi.applicationInfo != null && (pi.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0);
                        item.put("isSystem", isSys);
                        arr.put(item);
                    }
                }
            } catch (Exception ignored) {}
            return arr.toString();
        }
    }

    public static boolean isCarDevice(Context context) {
        String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
        String brand = Build.BRAND != null ? Build.BRAND.toUpperCase() : "";
        String finger = Build.FINGERPRINT != null ? Build.FINGERPRINT.toUpperCase() : "";
        if (model.contains("IHU") || model.contains("E02") || model.contains("GEELY") || model.contains("ECARX")
                || brand.contains("GEELY") || brand.contains("ECARX") || finger.contains("GEELY") || finger.contains("ECARX")) {
            return true;
        }
        if (context != null) {
            PackageManager pm = context.getPackageManager();
            try {
                if (pm.getPackageInfo("com.ecarx.carservice", 0) != null) return true;
            } catch (Exception ignored) {}
            try {
                if (pm.getPackageInfo("com.ecarx.launcher", 0) != null) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    public static boolean isNavigationApp(String pkg, String filename) {
        if (pkg != null) {
            String p = pkg.toLowerCase().trim();
            if (p.contains("autonavi") || p.contains("amap") || p.contains("baidu.nav") || p.contains("baidu.map") || p.contains("cauto") || p.contains("tencent.map") || p.contains("tencent.nav") || p.contains("petalmaps")) {
                return true;
            }
        }
        if (filename != null) {
            String fn = filename.toLowerCase().trim();
            if (fn.contains("amap") || fn.contains("gaode") || fn.contains("高德") || fn.contains("baidu") || fn.contains("百度") || fn.contains("map") || fn.contains("地图") || fn.contains("navi") || fn.contains("tencent") || fn.contains("腾讯")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isProtectedCriticalPackage(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase().trim();
        return p.equals("android")
                || p.equals("com.android.settings")
                || p.equals("com.android.systemui")
                || p.equals("com.android.packageinstaller")
                || p.equals("com.android.permissioncontroller")
                || p.equals("com.android.server.telecom")
                || p.equals("com.android.providers.settings")
                || p.equals("com.android.keyguard")
                || p.equals("com.android.shell")
                || p.equals("app.onepve.geelyconsole")
                || p.contains("inputmethod")
                || p.equals("com.ecarx.carservice")
                || p.equals("com.ecarx.launcher")
                || p.equals("com.ecarx.systemui")
                || p.equals("com.ecarx.service")
                || p.equals("com.ecarx.btphone")
                || p.equals("com.ecarx.carsetting")
                || p.equals("com.ecarx.hvac")
                || p.equals("com.ecarx.sound")
                || p.equals("com.ecarx.policy")
                || p.equals("com.ecarx.ipcamera")
                || p.equals("com.ecarx.dvr");
    }

    public static boolean isDangerousSystemPackage(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase().trim();
        return isProtectedCriticalPackage(p)
                || p.equals("com.android.settings")
                || p.equals("com.android.systemui")
                || (p.startsWith("com.ecarx.") && !p.contains("appstore") && !p.contains("xcmedia") && !p.contains("multimedia") && !p.contains("carmedia") && !p.contains("upgrade"));
    }
}
