package app.onepve.geelyconsole.utils;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.net.NetworkInterface;

/**
 * 车载网络与外设连接策略中枢 (Wi-Fi / 热点 / 蓝牙三合一)
 * 支持手动瞬时控制、开机自启策略（保持现状/默认开/默认关）、以及后台硬件就绪自检探针 (Hardware Ready Probe)。
 */
public class NetworkPolicyManager {

    private static final String PREF_NAME = "toolbox_settings";

    // 自启策略常量：0=保持现状(默认), 1=默认开启, 2=默认关闭
    public static final int POLICY_KEEP = 0;
    public static final int POLICY_DEFAULT_ON = 1;
    public static final int POLICY_DEFAULT_OFF = 2;

    public static final String KEY_POLICY_WIFI = "net_policy_wifi";
    public static final String KEY_POLICY_HOTSPOT = "net_policy_hotspot";
    public static final String KEY_POLICY_BLUETOOTH = "net_policy_bluetooth";

    private static volatile boolean sBootWorkerRunning = false;

    // ──────────────────────────────────────────────────────────────────────────
    // 1. 策略配置读写
    // ──────────────────────────────────────────────────────────────────────────

    public static int getPolicy(Context ctx, String type) {
        if (ctx == null || type == null) return POLICY_KEEP;
        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        switch (type.toLowerCase()) {
            case "wifi":
                return sp.getInt(KEY_POLICY_WIFI, POLICY_KEEP);
            case "hotspot":
                return sp.getInt(KEY_POLICY_HOTSPOT, POLICY_KEEP);
            case "bluetooth":
            case "bt":
                return sp.getInt(KEY_POLICY_BLUETOOTH, POLICY_KEEP);
            default:
                return POLICY_KEEP;
        }
    }

    public static void setPolicy(Context ctx, String type, int policy) {
        if (ctx == null || type == null) return;
        if (policy < POLICY_KEEP || policy > POLICY_DEFAULT_OFF) policy = POLICY_KEEP;
        SharedPreferences.Editor ed = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit();
        switch (type.toLowerCase()) {
            case "wifi":
                ed.putInt(KEY_POLICY_WIFI, policy);
                break;
            case "hotspot":
                ed.putInt(KEY_POLICY_HOTSPOT, policy);
                break;
            case "bluetooth":
            case "bt":
                ed.putInt(KEY_POLICY_BLUETOOTH, policy);
                break;
        }
        ed.apply();
        AppLogger.i("网络中枢", "已更新开机连接策略: type=" + type + ", policy=" + policy);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. 硬件当前状态探测
    // ──────────────────────────────────────────────────────────────────────────

    public static boolean isWifiEnabled(Context ctx) {
        if (ctx == null) return false;
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            return wm != null && wm.isWifiEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isBluetoothEnabled() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            return adapter != null && adapter.isEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isHotspotEnabled(Context ctx) {
        if (ctx == null) return false;
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                Method method = wm.getClass().getDeclaredMethod("isWifiApEnabled");
                method.setAccessible(true);
                return (Boolean) method.invoke(wm);
            }
        } catch (Throwable t) {
            try {
                WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    Method method = wm.getClass().getDeclaredMethod("getWifiApState");
                    method.setAccessible(true);
                    int state = (Integer) method.invoke(wm);
                    return state == 13; // 13: WIFI_AP_STATE_ENABLED
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. 硬件瞬时即时控制 (双通道保障)
    // ──────────────────────────────────────────────────────────────────────────

    public static boolean setWifiEnabled(Context ctx, boolean enable) {
        if (ctx == null) return false;
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wm.setWifiEnabled(enable);
                AppLogger.i("网络中枢", "主动切换 Wi-Fi -> " + enable);
                return true;
            }
        } catch (Throwable t) {
            SystemUtils.executePrivileged(ctx, "svc wifi " + (enable ? "enable" : "disable"));
        }
        return isWifiEnabled(ctx);
    }

    public static boolean setBluetoothEnabled(Context ctx, boolean enable) {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) {
                if (enable) adapter.enable();
                else adapter.disable();
                AppLogger.i("网络中枢", "主动切换蓝牙 -> " + enable);
                return true;
            }
        } catch (Throwable t) {
            SystemUtils.executePrivileged(ctx, "service call bluetooth_manager " + (enable ? "6" : "8"));
        }
        return isBluetoothEnabled();
    }

    public static boolean setHotspotEnabled(Context ctx, boolean enable) {
        if (ctx == null) return false;
        AppLogger.i("网络中枢", "主动切换热点分享 -> " + enable);
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (enable) {
                Class<?> callbackClass = Class.forName("android.net.ConnectivityManager$OnStartTetheringCallback");
                Method method = cm.getClass().getDeclaredMethod("startTethering", int.class, boolean.class, callbackClass);
                method.setAccessible(true);
                method.invoke(cm, 0, false, null); // 0: TETHERING_WIFI
                return true;
            } else {
                Method method = cm.getClass().getDeclaredMethod("stopTethering", int.class);
                method.setAccessible(true);
                method.invoke(cm, 0);
                return true;
            }
        } catch (Throwable t) {
            if (enable) {
                String res = SystemUtils.executePrivileged(ctx, "cmd connectivity start-tethering wifi");
                if (res == null || res.contains("Error")) {
                    SystemUtils.executePrivileged(ctx, "svc wifi setWifiApEnabled true");
                }
            } else {
                String res = SystemUtils.executePrivileged(ctx, "cmd connectivity stop-tethering wifi");
                if (res == null || res.contains("Error")) {
                    SystemUtils.executePrivileged(ctx, "svc wifi setWifiApEnabled false");
                }
            }
        }
        return isHotspotEnabled(ctx);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. 全景状态打包为 JSON (供 JSBridge 同步拉取)
    // ──────────────────────────────────────────────────────────────────────────

    public static JSONObject getFullStatusJson(Context ctx) {
        JSONObject obj = new JSONObject();
        try {
            boolean wifiCur = isWifiEnabled(ctx);
            boolean apCur = isHotspotEnabled(ctx);
            boolean btCur = isBluetoothEnabled();

            int wifiPol = getPolicy(ctx, "wifi");
            int apPol = getPolicy(ctx, "hotspot");
            int btPol = getPolicy(ctx, "bluetooth");

            obj.put("wifi_enabled", wifiCur);
            obj.put("hotspot_enabled", apCur);
            obj.put("bluetooth_enabled", btCur);

            obj.put("wifi_policy", wifiPol);
            obj.put("hotspot_policy", apPol);
            obj.put("bluetooth_policy", btPol);
        } catch (Exception ignored) {}
        return obj;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. 开机后台硬件就绪自检探针与错峰执行守护 (Hardware Ready Probe Worker)
    // ──────────────────────────────────────────────────────────────────────────

    public static void runBootPolicyWorkerAsync(final Context context) {
        if (context == null || sBootWorkerRunning) return;
        sBootWorkerRunning = true;

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    AppLogger.i("网络中枢", "开机网络策略守护线程已拉起，开始硬件就绪自检探测...");
                    long startTime = System.currentTimeMillis();
                    long maxTimeoutMs = 25000; // 最长 25 秒安全兜底

                    // 1. 硬件就绪自检循环 (每 500ms 探测一次驱动与物理接口)
                    boolean isHardwareReady = false;
                    while (System.currentTimeMillis() - startTime < maxTimeoutMs) {
                        try {
                            boolean hasWlan = NetworkInterface.getByName("wlan0") != null;
                            boolean hasTbox = NetworkInterface.getByName("tbox0") != null;
                            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
                            boolean hasBtReady = (bt != null && bt.getState() != BluetoothAdapter.STATE_TURNING_ON && bt.getState() != BluetoothAdapter.STATE_TURNING_OFF);

                            // 如果热点需要开启，优先保证 wlan0 和 tbox0 物理就绪
                            int hotspotPolicy = getPolicy(context, "hotspot");
                            if (hotspotPolicy == POLICY_DEFAULT_ON) {
                                if (hasWlan && hasTbox) {
                                    isHardwareReady = true;
                                    break;
                                }
                            } else {
                                if (hasWlan && hasBtReady) {
                                    isHardwareReady = true;
                                    break;
                                }
                            }
                        } catch (Throwable ignored) {}

                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }

                    long elapsed = System.currentTimeMillis() - startTime;
                    AppLogger.i("网络中枢", "硬件就绪探针判定结束: ready=" + isHardwareReady + ", 耗时=" + elapsed + "ms");

                    // 2. 依次按用户策略安全生效 (脱离冷启动脆弱期)
                    int wifiPolicy = getPolicy(context, "wifi");
                    if (wifiPolicy == POLICY_DEFAULT_ON && !isWifiEnabled(context)) {
                        setWifiEnabled(context, true);
                    } else if (wifiPolicy == POLICY_DEFAULT_OFF && isWifiEnabled(context)) {
                        setWifiEnabled(context, false);
                    }

                    int btPolicy = getPolicy(context, "bluetooth");
                    if (btPolicy == POLICY_DEFAULT_ON && !isBluetoothEnabled()) {
                        setBluetoothEnabled(context, true);
                    } else if (btPolicy == POLICY_DEFAULT_OFF && isBluetoothEnabled()) {
                        setBluetoothEnabled(context, false);
                    }

                    int hotspotPolicy = getPolicy(context, "hotspot");
                    if (hotspotPolicy == POLICY_DEFAULT_ON && !isHotspotEnabled(context)) {
                        setHotspotEnabled(context, true);
                    } else if (hotspotPolicy == POLICY_DEFAULT_OFF && isHotspotEnabled(context)) {
                        setHotspotEnabled(context, false);
                    }

                    AppLogger.i("网络中枢", "开机网络与外设连接策略执行完成，线程退出");
                } catch (Throwable t) {
                    AppLogger.w("网络中枢", "开机策略执行异常: " + t.getMessage());
                } finally {
                    sBootWorkerRunning = false;
                }
            }
        }, "NetworkPolicyBootWorker");

        worker.setDaemon(true);
        worker.start();
    }
}
