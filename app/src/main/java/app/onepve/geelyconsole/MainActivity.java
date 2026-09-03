package app.onepve.geelyconsole;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
        hideSystemUI();
        setContentView(R.layout.activity_main);

        initWebView();
        checkAndRequestStoragePermission();
        SystemUtils.applyPlaceholderLock();
        AppLogger.i("应用启动", "吉利工具箱界面启动完成");
        // 启动时自动探测并开启白名单，确保打开应用即处于放行状态
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!SystemUtils.isApkVerifyWhitelistEnabled()) {
                        SystemUtils.enableApkVerifyWhitelist(MainActivity.this);
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
            }
        });

        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new ToolboxBridge(this), "ToolboxBridge");
        webView.loadUrl("file:///android_asset/toolbox_ui.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        isForeground = true;
        currentActivity = new WeakReference<>(this);
        hideSystemUI();
        mainHandler.post(statusTicker);
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
        isForeground = false;
        mainHandler.removeCallbacks(statusTicker);
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            // 切到桌面或后台时，如果开启了悬浮胶囊，强力确保胶囊可见挂载
            android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            if (prefs.getBoolean("floating_enabled", false)) {
                FloatingWindowService.ensureServiceStarted(this);
            }
        } catch (Exception ignored) {
        }
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
                    obj.put("version", "1.1.8");
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
                    obj.put("rabbit_safe_mode", prefs.getBoolean("rabbit_safe_mode_enabled", true));
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
    public void onFileUploaded(File file) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, "收到手机快传文件: " + file.getName(), Toast.LENGTH_LONG).show();
            }
        });
    }
    @Override
    public void onActionRequested(String action) {}
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
        public String getDynamicCode() {
            return SystemUtils.calculateDynamicCode();
        }

        @JavascriptInterface
        public String getDynamicCodePlus5() {
            return SystemUtils.calculateDynamicCodePlus5();
        }

        @JavascriptInterface
        public String getDynamicCodePlus10() {
            return SystemUtils.calculateDynamicCode();
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
        public void copyToClipboard(String text) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("code", text);
                        cm.setPrimaryClip(clip);
                        Toast.makeText(context, "已复制: " + text, Toast.LENGTH_SHORT).show();
                    } catch (Exception ignored) {
                    }
                }
            });
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
                        List<File> apks = AutoPilotManager.scanDownloadedAmapApks();
                        if (apks.isEmpty()) {
                            Toast.makeText(context, "未检测到已下载的高德地图，请先在右侧软件中心选择下载！", Toast.LENGTH_LONG).show();
                            return;
                        } else if (apks.size() == 1) {
                            targetFile = apks.get(0);
                        } else {
                            try {
                                JSONArray arr = new JSONArray();
                                for (File apk : apks) {
                                    JSONObject item = new JSONObject();
                                    item.put("filename", apk.getName());
                                    item.put("path", apk.getAbsolutePath());
                                    item.put("sizeStr", String.format(java.util.Locale.CHINA, "%.1f MB", apk.length() / (1024.0 * 1024.0)));
                                    arr.put(item);
                                }
                                String script = "if(window.showAmapAutoPilotSelector){window.showAmapAutoPilotSelector(" + arr.toString() + ");}";
                                webView.evaluateJavascript(script, null);
                            } catch (Exception ignored) {}
                            return;
                        }
                    }

                    AutoPilotManager.getInstance().startAutoInject(MainActivity.this, targetFile);
                }
            });
        }

        @JavascriptInterface
        public void cancelAutoPilot() {
            AutoPilotManager.getInstance().cancel(context);
        }

        @JavascriptInterface
        public String getDownloadedAmapListJson() {
            try {
                List<File> apks = AutoPilotManager.scanDownloadedAmapApks();
                JSONArray arr = new JSONArray();
                for (File apk : apks) {
                    JSONObject item = new JSONObject();
                    item.put("filename", apk.getName());
                    item.put("path", apk.getAbsolutePath());
                    item.put("sizeStr", String.format(java.util.Locale.CHINA, "%.1f MB", apk.length() / (1024.0 * 1024.0)));
                    arr.put(item);
                }
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            }
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
                                boolean safeMode = prefs.getBoolean("rabbit_safe_mode_enabled", true);
                                if (safeMode && !isNavigationApp(pkg, apkFile.getName())) {
                                    final String finalPkg = pkg;
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            showToast("【安全保护】当前仅允许车载导航类专车包使用时钟伪装。常规应用请直接在应用列表中安装。");
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
        public String checkRabbitPostReboot() {
            JSONObject res = ThemePatcher.checkRabbitPostRebootStatus(context);
            return res.toString();
        }

        @JavascriptInterface
        public boolean isRabbitSafeModeEnabled() {
            android.content.SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            return prefs.getBoolean("rabbit_safe_mode_enabled", true);
        }

        @JavascriptInterface
        public boolean setRabbitSafeModeEnabled(boolean enabled) {
            android.content.SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("rabbit_safe_mode_enabled", enabled).apply();
            pushDeviceInfoToWeb();
            return enabled;
        }

        @JavascriptInterface
        public boolean isAutostartEnabled() {
            android.content.SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            return prefs.getBoolean("autostart_enabled", false);
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
        public void softReboot() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.softReboot();
                }
            });
        }

        @JavascriptInterface
        public void uninstallAmap() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    AdbClient.execute(context, "pm uninstall com.autonavi.amapauto");
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            showToast("已发送卸载高德指令 (com.autonavi.amapauto)");
                        }
                    });
                }
            }).start();
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
                    AdbClient.execute(context, "pm uninstall " + pkg);
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            showToast("已发送卸载指令: " + pkg);
                        }
                    });
                }
            }).start();
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
        public void checkUpdate() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        java.net.URL url = new java.net.URL("https://dl.onepve.com/GeelyToolbox/version.json?t=" + System.currentTimeMillis());
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
                                    if (hasNew) {
                                        // 唤起车载精致 H5 更新弹窗
                                        String script = "if(window.showToolboxUpdateModal){window.showToolboxUpdateModal(" + json.toString() + ");}";
                                        webView.evaluateJavascript(script, null);
                                    } else {
                                        showToast("当前已是最新版本 v" + remoteVer + " (๑•̀ㅂ•́)و");
                                    }
                                }
                            });
                        } else {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    showToast("无法连接更新服务器，请检查车机网络");
                                }
                            });
                        }
                    } catch (Exception e) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                showToast("检查更新失败: " + e.getMessage());
                            }
                        });
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public void startToolboxSelfUpdate(final String downloadUrl, final String remoteVer) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    showToast("开始下载工具箱新版本...");
                    DownloadManager.startDownload("toolbox_update", downloadUrl, "GeelyToolbox_v" + remoteVer + ".apk", new DownloadManager.DownloadListener() {
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
        public boolean wakeBluetoothAudio() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            boolean ok = SystemUtils.wakeBluetoothAudioChannel(MainActivity.this);
                            if (ok) {
                                showToast(" 蓝牙音频通道选通信号已发送 (๑•̀ㅂ•́)و");
                            } else {
                                showToast("蓝牙音频通道选通信号已发送");
                            }
                        }
                    }).start();
                }
            });
            return true;
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
        public boolean toggleFreezeMultimedia(final boolean freeze) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            SystemUtils.OpResult res1 = SystemUtils.setPackageEnabled(MainActivity.this, "com.ecarx.multimedia", !freeze);
                            SystemUtils.OpResult res2 = SystemUtils.setPackageEnabled(MainActivity.this, "com.ecarx.xcmedia", !freeze);
                            boolean ok = (res1 != null && res1.success) || (res2 != null && res2.success);
                            if (freeze) {
                                showToast(" 已安全冻结原厂多媒体伴听，释放后台运存 (๑•̀ㅂ•́)و");
                            } else {
                                showToast(" 已解冻恢复原厂多媒体伴听");
                            }
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
        public void openPermissions() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + context.getPackageName()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } catch (Exception e) {
                        try {
                            Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
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
        public void showAllApps() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, "已装应用列表可直接在车机原生应用抽屉查看", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void toggleFloatingCapsule() {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    FloatingWindowService.ensureServiceStarted(MainActivity.this);
                    Toast.makeText(context, "已唤起桌面悬浮小胶囊", Toast.LENGTH_SHORT).show();
                }
            });
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
                                        Toast.makeText(context, "下载完成，正在调起原生安装器...", Toast.LENGTH_SHORT).show();
                                        SystemUtils.installApkViaProvider(MainActivity.this, savedFile);
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
                    finishAndRemoveTask();
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
                obj.put("turn_360", prefs.getBoolean("vehicle_turn_360_enabled", false));
                obj.put("light_nav", prefs.getBoolean("vehicle_light_nav_enabled", false));
                obj.put("flameout_voice", prefs.getBoolean("vehicle_flameout_voice_enabled", false));

                obj.put("voice_door_fl", prefs.getBoolean("voice_enable_door_fl", false));
                obj.put("voice_door_fr", prefs.getBoolean("voice_enable_door_fr", false));
                obj.put("voice_door_fr_close", prefs.getBoolean("voice_enable_door_fr_close", false));
                obj.put("voice_door_rear", prefs.getBoolean("voice_enable_door_rear", false));

                obj.put("custom_door_fl", !prefs.getString("custom_voice_door_fl.mp3", "").isEmpty());
                obj.put("custom_door_fr", !prefs.getString("custom_voice_door_fr.mp3", "").isEmpty());
                obj.put("custom_door_fr_close", !prefs.getString("custom_voice_door_fr_close.mp3", "").isEmpty());
                obj.put("custom_door_rear", !prefs.getString("custom_voice_door_rl.mp3", "").isEmpty());
                obj.put("custom_flameout", !prefs.getString("custom_voice_flameout.mp3", "").isEmpty());

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
        public boolean setVehicleAutomationSetting(final String key, final boolean enabled) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.content.SharedPreferences prefs = getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        prefs.edit().putBoolean(key, enabled).apply();
                        VehicleAutomationService.syncState(MainActivity.this);
                        AppLogger.i("座舱自动化", "更新设置项: " + key + " -> " + enabled);
                    } catch (Exception e) {
                        AppLogger.e("座舱自动化", "更新设置失败: " + e.getMessage());
                    }
                }
            });
            return true;
        }

        @JavascriptInterface
        public void testVehicleVoice(final String type) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    VehicleVoicePlayer player = VehicleVoicePlayer.getInstance(MainActivity.this);
                    if ("door".equals(type)) {
                        player.play("door_fl.mp3", "主驾车门已打开，请注意后方来车");
                    } else if ("door_fr".equals(type)) {
                        player.play("door_fr.mp3", "副驾车门已打开，请注意安全");
                    } else if ("door_rear".equals(type)) {
                        player.play("door_rl.mp3", "后车门已打开，请注意车外环境");
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
                        Toast.makeText(context, "未找到小爱语音引擎或系统TTS设置", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
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
