package app.onepve.geelyconsole.hal;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 纯反射连接 Android Automotive 原生 Car API (免编译期隐藏 API 强依赖)
 * 适配吉利缤越 L (IHU624G / Flyme Auto)
 */
public class AndroidCarClient {
    private static final String TAG = "AndroidCarClient";
    private static volatile AndroidCarClient sInstance;

    public static final int CURRENT_GEAR = 289408001;        // 0x11400401 (VehicleProperty.CURRENT_GEAR)
    public static final int IGNITION_STATE = 289408009;      // 0x11400409 (VehicleProperty.IGNITION_STATE)
    public static final int PERF_VEHICLE_SPEED = 291504647;  // 0x11600207 (VehicleProperty.PERF_VEHICLE_SPEED)
    public static final int BATTERY_VOLTAGE = 557875206;     // 0x21400426 (BODY_BATTERY_U_BATT_INFO)
    public static final int DOOR_POS = 641736873;           // 0x26400B09 (DOOR_POS)
    public static final int TRUNK_POS = 557850643;          // 0x21400213 (BODY_DOOR_TRUNK_DOOR_POS)

    // 车门 Area ID 掩码
    public static final int AREA_DOOR_ROW_1_LEFT = 1;   // 主驾门
    public static final int AREA_DOOR_ROW_1_RIGHT = 4;  // 副驾门
    public static final int AREA_DOOR_ROW_2_LEFT = 16;  // 左后门
    public static final int AREA_DOOR_ROW_2_RIGHT = 64; // 右后门

    private Object mCar;
    private Object mCarPropertyManager;
    private Method mGetPropertyMethod;
    private Method mGetIntPropertyMethod;
    private Method mGetFloatPropertyMethod;
    private Method mCarDisconnectMethod;
    private volatile boolean mConnected = false;
    private OnCarConnectListener mListener;

    public interface OnCarConnectListener {
        void onCarConnected();
        void onCarDisconnected();
    }

    private AndroidCarClient() {}

    public static AndroidCarClient getInstance() {
        if (sInstance == null) {
            synchronized (AndroidCarClient.class) {
                if (sInstance == null) {
                    sInstance = new AndroidCarClient();
                }
            }
        }
        return sInstance;
    }

    public synchronized void connect(Context context, OnCarConnectListener listener) {
        if (this.mConnected) {
            if (listener != null) listener.onCarConnected();
            return;
        }
        this.mListener = listener;
        final Context appContext = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Class<?> carClass = Class.forName("android.car.Car");
                    Class<?> serviceConnClass = null;
                    try {
                        serviceConnClass = Class.forName("android.car.Car$CarServiceLifecycleListener");
                    } catch (ClassNotFoundException ignored) {}

                    if (serviceConnClass != null) {
                        // Android 11+ 新版 createCar API
                        Object proxyListener = Proxy.newProxyInstance(
                                serviceConnClass.getClassLoader(),
                                new Class<?>[]{serviceConnClass},
                                new InvocationHandler() {
                                    @Override
                                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                        if ("onLifecycleChanged".equals(method.getName()) && args != null && args.length >= 2) {
                                            boolean ready = (Boolean) args[1];
                                            if (ready) {
                                                AndroidCarClient.this.mCar = args[0];
                                                AndroidCarClient.this.initPropertyManager();
                                            } else {
                                                AndroidCarClient.this.handleDisconnected();
                                            }
                                        }
                                        return null;
                                    }
                                }
                        );
                        Method createCarMethod = carClass.getMethod("createCar", Context.class, android.os.Handler.class, Long.TYPE, serviceConnClass);
                        createCarMethod.invoke(null, appContext, null, 0L, proxyListener);
                        return;
                    }

                    // 旧版 ServiceConnection 方式兼容
                    ServiceConnection connection = new ServiceConnection() {
                        @Override
                        public void onServiceConnected(ComponentName name, IBinder service) {
                            AndroidCarClient.this.initPropertyManager();
                        }

                        @Override
                        public void onServiceDisconnected(ComponentName name) {
                            AndroidCarClient.this.handleDisconnected();
                        }
                    };

                    try {
                        Method createCar = carClass.getMethod("createCar", Context.class, ServiceConnection.class);
                        AndroidCarClient.this.mCar = createCar.invoke(null, appContext, connection);
                        Method connectMethod = carClass.getMethod("connect");
                        connectMethod.invoke(AndroidCarClient.this.mCar);
                    } catch (NoSuchMethodException e) {
                        Constructor<?> constructor = carClass.getConstructor(Context.class, ServiceConnection.class, android.os.Handler.class);
                        AndroidCarClient.this.mCar = constructor.newInstance(appContext, connection, null);
                        Method connectMethod = carClass.getMethod("connect");
                        connectMethod.invoke(AndroidCarClient.this.mCar);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Car API connection failed: " + t.getMessage());
                }
            }
        }, "CarClientConnect").start();
    }

    /* package */ synchronized void initPropertyManager() {
        try {
            if (this.mCar == null) return;
            Class<?> carClass = this.mCar.getClass();
            Method getCarManager = carClass.getMethod("getCarManager", String.class);
            this.mCarPropertyManager = getCarManager.invoke(this.mCar, "property");
            if (this.mCarPropertyManager != null) {
                Class<?> mgrClass = this.mCarPropertyManager.getClass();
                try {
                    this.mGetIntPropertyMethod = mgrClass.getMethod("getIntProperty", Integer.TYPE, Integer.TYPE);
                } catch (NoSuchMethodException ignored) {}
                try {
                    this.mGetFloatPropertyMethod = mgrClass.getMethod("getFloatProperty", Integer.TYPE, Integer.TYPE);
                } catch (NoSuchMethodException ignored) {}
                try {
                    this.mGetPropertyMethod = mgrClass.getMethod("getProperty", Integer.TYPE, Integer.TYPE);
                } catch (NoSuchMethodException ignored) {}
                this.mConnected = true;
                Log.i(TAG, "CarPropertyManager successfully initialized");
                if (this.mListener != null) {
                    this.mListener.onCarConnected();
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to init PropertyManager: " + t.getMessage());
        }
    }

    /* package */ synchronized void handleDisconnected() {
        this.mConnected = false;
        this.mCarPropertyManager = null;
        if (this.mListener != null) {
            this.mListener.onCarDisconnected();
        }
    }

    public boolean isConnected() {
        return this.mConnected && this.mCarPropertyManager != null;
    }

    public int getIntProperty(int propId, int areaId, int defaultValue) {
        if (!isConnected()) return defaultValue;
        try {
            if (this.mGetIntPropertyMethod != null) {
                return (Integer) this.mGetIntPropertyMethod.invoke(this.mCarPropertyManager, propId, areaId);
            }
            if (this.mGetPropertyMethod != null) {
                Object carPropValue = this.mGetPropertyMethod.invoke(this.mCarPropertyManager, propId, areaId);
                if (carPropValue != null) {
                    Method getValue = carPropValue.getClass().getMethod("getValue");
                    Object val = getValue.invoke(carPropValue);
                    if (val instanceof Integer) {
                        return (Integer) val;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return defaultValue;
    }

    public float getFloatProperty(int propId, int areaId, float defaultValue) {
        if (!isConnected()) return defaultValue;
        try {
            if (this.mGetFloatPropertyMethod != null) {
                return (Float) this.mGetFloatPropertyMethod.invoke(this.mCarPropertyManager, propId, areaId);
            }
            if (this.mGetPropertyMethod != null) {
                Object carPropValue = this.mGetPropertyMethod.invoke(this.mCarPropertyManager, propId, areaId);
                if (carPropValue != null) {
                    Method getValue = carPropValue.getClass().getMethod("getValue");
                    Object val = getValue.invoke(carPropValue);
                    if (val instanceof Float) {
                        return (Float) val;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return defaultValue;
    }

    public synchronized void disconnect() {
        try {
            if (this.mCar != null && this.mCarDisconnectMethod != null) {
                this.mCarDisconnectMethod.invoke(this.mCar);
            }
        } catch (Throwable ignored) {}
        this.mConnected = false;
        this.mCar = null;
        this.mCarPropertyManager = null;
    }

    public static boolean isBinyueLPlatform(Context context) {
        try {
            String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
            if (model.contains("IHU624") || model.contains("COOLRAY L") || model.contains("BINYUE L")) {
                return true;
            }
            // 检查 Flyme Auto 专有包
            if (context != null) {
                try {
                    context.getPackageManager().getPackageInfo("com.flyme.auto", 0);
                    return true;
                } catch (Exception ignored) {}
                try {
                    context.getPackageManager().getPackageInfo("com.flyme.auto.service", 0);
                    return true;
                } catch (Exception ignored) {}
            }
            // 检查系统属性
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getMethod("get", String.class, String.class);
            String projectInfo = (String) get.invoke(null, "ro.boot.ecarx_projectinfo", "");
            if (projectInfo != null && projectInfo.contains("624")) {
                return true;
            }
            String platform = (String) get.invoke(null, "ro.ecarx.version.platform", "");
            if (platform != null && platform.toLowerCase().contains("flyme")) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean triggerAvm(Context context) {
        if (context == null) return false;
        try {
            Intent intent = new Intent("com.jsbd.avm.OPEN");
            intent.setPackage("com.ecarx.parking");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Direct intent triggerAvm failed: " + t.getMessage());
            try {
                Intent serviceIntent = new Intent();
                serviceIntent.setComponent(new ComponentName("com.ecarx.parking", "com.ecarx.parking.AvmEnterService"));
                context.startService(serviceIntent);
                return true;
            } catch (Throwable t2) {
                Log.w(TAG, "Service triggerAvm fallback failed: " + t2.getMessage());
                return false;
            }
        }
    }
}
