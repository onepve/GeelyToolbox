package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 车规 Automotive HAL 物理按键直通监听器 (CarPropertyKeyMonitor)
 * 通过反射直连 android.car.Car -> CarPropertyManager
 * 监听 HW_KEY_INPUT (678428923) 属性，直通拦截滚轮下按 (硬件码 45 -> KEY_OK 306)
 * 彻底解决 logcat 无 reportKeyToAdaptApi 日志导致 2号按键失灵的问题！
 */
public class CarPropertyKeyMonitor {
    private static final String TAG = "CarPropertyKeyMonitor";
    private static final int HW_KEY_INPUT = 678428923;
    private static final long CONNECT_TIMEOUT_MS = 5000;
    private static final int MAX_RETRIES = 3;
    private int connectRetries = 0;

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Object car;
    private Object carPropertyManager;
    private Object propertyCallback;
    private volatile boolean running = false;
    private volatile boolean connected = false;

    public interface Listener {
        void onKeyDown(int keyCode);
        void onKeyUp(int keyCode);
    }

    public CarPropertyKeyMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    private final Runnable connectTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!connected && running) {
                Log.w(TAG, "CarService connect timeout (5000ms), attempt=" + (connectRetries + 1));
                if (connectRetries < MAX_RETRIES) {
                    connectRetries++;
                    connectCar();
                } else {
                    Log.w(TAG, "CarService connect failed after " + MAX_RETRIES + " retries, fallback to logcat only");
                }
            }
        }
    };

    public synchronized void start() {
        if (running) return;
        running = true;
        connected = false;
        try {
            handler.postDelayed(connectTimeoutRunnable, CONNECT_TIMEOUT_MS);
            connectCar();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to start CarPropertyKeyMonitor: " + t.getMessage());
        }
    }

    private boolean connectCar() {
        try {
            Class<?> carClass = Class.forName("android.car.Car");
            Object connectionListener = createConnectionListener();
            if (connectionListener == null) return false;

            Handler h = new Handler(Looper.getMainLooper());
            try {
                // 尝试签名 1: createCar(Context, CarServiceConnectionListener, Handler)
                Class<?> listenerClass = Class.forName("android.car.Car$CarServiceConnectionListener");
                Method m = carClass.getMethod("createCar", Context.class, listenerClass, Handler.class);
                car = m.invoke(null, context, connectionListener, h);
                Log.i(TAG, "createCar via CarServiceConnectionListener initiated");
                return true;
            } catch (NoSuchMethodException e1) {
                try {
                    // 尝试签名 2: createCar(Context, ServiceConnectionListener, Handler)
                    Class<?> listenerClass2 = Class.forName("android.car.Car$ServiceConnectionListener");
                    Method m2 = carClass.getMethod("createCar", Context.class, listenerClass2, Handler.class);
                    car = m2.invoke(null, context, connectionListener, h);
                    Log.i(TAG, "createCar via ServiceConnectionListener initiated");
                    return true;
                } catch (NoSuchMethodException e2) {
                    try {
                        // 尝试签名 3: createCar(Context) + connect()
                        Method m3 = carClass.getMethod("createCar", Context.class);
                        car = m3.invoke(null, context);
                        carClass.getMethod("connect").invoke(car);
                        Log.i(TAG, "createCar(Context) + connect() initiated");
                        handler.postDelayed(() -> {
                            if (running && !connected) {
                                if (registerPropertyCallback()) {
                                    connected = true;
                                    AppLogger.i("方控总线", "CarProperty 硬件键监听成功连接注册！");
                                }
                            }
                        }, 1500);
                        return true;
                    } catch (Exception e3) {
                        Log.w(TAG, "All createCar signatures failed: " + e3.getMessage());
                        return false;
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "android.car.Car not available on this platform");
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "connectCar exception: " + t.getMessage());
            return false;
        }
    }

    private Object createConnectionListener() {
        String[] possibleInterfaces = {
                "android.car.Car$CarServiceConnectionListener",
                "android.car.Car$ServiceConnectionListener"
        };
        for (String ifaceName : possibleInterfaces) {
            try {
                final Class<?> iface = Class.forName(ifaceName);
                return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        String name = method.getName();
                        if ("onServiceConnected".equals(name)) {
                            Log.i(TAG, "CarService connected (" + iface.getSimpleName() + ")");
                            connectRetries = 0;
                            connected = true;
                            handler.removeCallbacks(connectTimeoutRunnable);
                            if (registerPropertyCallback()) {
                                AppLogger.i("方控总线", "CarProperty 硬件按键 HAL 注册成功 (HW_KEY_INPUT: 678428923)");
                            }
                        } else if ("onServiceDisconnected".equals(name)) {
                            Log.w(TAG, "CarService disconnected");
                            connected = false;
                            if (running && connectRetries < MAX_RETRIES) {
                                connectRetries++;
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (running && !connected) connectCar();
                                    }
                                }, 3000);
                            }
                        }
                        return null;
                    }
                });
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    private boolean registerPropertyCallback() {
        try {
            if (car == null) return false;
            Class<?> carClass = Class.forName("android.car.Car");
            String propertyServiceName = (String) carClass.getField("PROPERTY_SERVICE").get(null);
            carPropertyManager = carClass.getMethod("getCarManager", String.class).invoke(car, propertyServiceName);
            if (carPropertyManager == null) return false;

            Class<?> callbackClass = Class.forName("android.car.CarPropertyManager$CarPropertyEventCallback");
            propertyCallback = Proxy.newProxyInstance(callbackClass.getClassLoader(), new Class<?>[]{callbackClass}, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    String name = method.getName();
                    if ("onChangeEvent".equals(name) && args != null && args.length > 0) {
                        handlePropertyChange(args[0]);
                    }
                    return null;
                }
            });

            try {
                carPropertyManager.getClass().getMethod("registerCallback", callbackClass, Integer.TYPE, Float.TYPE)
                        .invoke(carPropertyManager, propertyCallback, HW_KEY_INPUT, 0.0f);
                return true;
            } catch (NoSuchMethodException e1) {
                try {
                    carPropertyManager.getClass().getMethod("registerCallback", callbackClass, Integer.TYPE)
                            .invoke(carPropertyManager, propertyCallback, HW_KEY_INPUT);
                    return true;
                } catch (Exception e2) {
                    Log.w(TAG, "registerCallback failed: " + e2.getMessage());
                    return false;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "registerPropertyCallback error: " + t.getMessage());
            return false;
        }
    }

    private void handlePropertyChange(Object carPropertyValue) {
        if (carPropertyValue == null) return;
        try {
            Object value = carPropertyValue.getClass().getMethod("getValue").invoke(carPropertyValue);
            if (!(value instanceof int[])) return;
            int[] arr = (int[]) value;
            if (arr.length < 2) return;

            int action = arr[0]; // 0=down, 1=up
            int rawCode = arr[1]; // 45=OK (滚轮下按), 306, etc.

            // 硬件码映射：45 映射为标准的 KEY_OK (306)
            final int keyCode;
            switch (rawCode) {
                case 45: keyCode = SteeringWheelKeyManager.KEY_OK; break;
                case 304: keyCode = SteeringWheelKeyManager.KEY_PREV; break;
                case 305: keyCode = SteeringWheelKeyManager.KEY_NEXT; break;
                case 306: keyCode = SteeringWheelKeyManager.KEY_OK; break;
                case 348: keyCode = SteeringWheelKeyManager.KEY_WMODE; break;
                case 349: keyCode = SteeringWheelKeyManager.KEY_CUSTOM; break;
                default: keyCode = rawCode; break;
            }

            AppLogger.i("方控总线", "CarProperty 收到硬件按键: rawCode=" + rawCode + " -> " + keyCode + ", action=" + (action == 0 ? "DOWN" : "UP"));

            if (listener != null) {
                if (action == 0) {
                    listener.onKeyDown(keyCode);
                } else if (action == 1) {
                    listener.onKeyUp(keyCode);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "handlePropertyChange error: " + t.getMessage());
        }
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        connected = false;
        handler.removeCallbacks(connectTimeoutRunnable);
        try {
            if (carPropertyManager != null && propertyCallback != null) {
                Class<?> callbackClass = Class.forName("android.car.CarPropertyManager$CarPropertyEventCallback");
                carPropertyManager.getClass().getMethod("unregisterCallback", callbackClass)
                        .invoke(carPropertyManager, propertyCallback);
            }
        } catch (Throwable ignored) {}
        car = null;
        carPropertyManager = null;
        propertyCallback = null;
        Log.i(TAG, "CarPropertyKeyMonitor stopped");
    }
}
