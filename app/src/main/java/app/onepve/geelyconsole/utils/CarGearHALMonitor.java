package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 原厂 Automotive HAL 档位直通监听器 (CarGearHALMonitor)
 *
 * 【语音播报 HAL 协议实装】
 * 依托 AOSP 平台系统签名特权，通过纯反射直连 android.car.Car -> CarPropertyManager，
 * 注册原生档位属性 (GEAR_SELECTION / CURRENT_GEAR 及亿咖通 vendor 档位属性位) 的
 * 属性变更回调，把「原厂 HAL 协议」的档位数值直接喂给换挡语音播报状态机，
 * 替代/补强 MCU 串口 logcat 文本正则解析，彻底摆脱文本日志依赖。
 *
 * 【HAL 全量属性探针】
 * 启动时一次性枚举 getPropertyList() 全部已支持属性，把属性 ID / 名称 / 类型 /
 * 访问权限全量写入守护日志（"HAL探针" 模块），并自动命中名称含 gear / shift 的
 * 属性位。用于在实车上定位原厂真正的档位属性 ID，供后续精确锁定。
 *
 * 设计铁律：
 * 1. 全部走反射，编译期不依赖 android.car 隐藏 API，任何机型缺失 android.car 都静默降级；
 * 2. 只注册成功的属性才保留，注册失败逐条捕获异常绝不中断其它候选；
 * 3. 绝不在本类内发声，仅向上回调，语音策略仍由 GearStateMachine 统一裁决。
 */
public class CarGearHALMonitor {

    private static final String TAG = "CarGearHALMonitor";
    private static final String LOG_MODULE = "挡位状态";
    private static final long CONNECT_TIMEOUT_MS = 6000;

    // ===== AOSP 标准车载属性位 (VehiclePropertyIds) =====
    /** 挡杆物理挡位选择 (INT32, 全局区) */
    public static final int PROP_GEAR_SELECTION = 0x11400400; // 289408000
    /** 当前实际啮合挡位 (INT32, 全局区) */
    public static final int PROP_CURRENT_GEAR = 0x11400401;   // 289408001
    /** 电子手刹驻车状态 (BOOLEAN, 全局区) —— 仅作 P 挡旁证探针，不单独触发播报 */
    public static final int PROP_PARKING_BRAKE_ON = 0x1020000E; // 270532622

    // ===== ECARX IHU516 原厂 Vendor 档位属性 (android.car.permission.CAR_VENDOR_EXTENSION) =====
    /** 亿咖通 VDRIVEINFO 档位位置 (INT32) —— 缤越 COOL / IHU516G 原厂真实档位属性 */
    public static final int PROP_ECARX_GEAR_POSITION = 678428909; // 0x287000ED, INFO_ID_VDRIVEINFO_GEAR_POSITION

    // ===== AOSP VehicleGear 位掩码常量 (Android 9 / API 28) =====
    private static final int GEAR_NEUTRAL = 0x0001;
    private static final int GEAR_REVERSE = 0x0002;
    private static final int GEAR_PARK = 0x0004;
    private static final int GEAR_DRIVE = 0x0008;
    private static final int GEAR_1 = 0x0010;
    private static final int GEAR_10 = 0x2000;

    public interface Listener {
        /** 收到 HAL 档位事件，normalizedGear 为 2=D / 3=N / 4=R / 5=P / 6=S，-1 表示未识别 */
        void onHalGear(int normalizedGear, String source);

        /** 原始数值上报（用于实车比对与探针日志，不一定能识别成标准挡位） */
        void onHalGearRaw(String source, int rawValue, int normalizedGear);
    }

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Object car;
    private Object carPropertyManager;
    private Object propertyCallback;

    private volatile boolean running = false;
    private volatile boolean connected = false;

    /** 已成功注册回调的属性位（含来源描述） */
    private final Set<Integer> registeredProps = new LinkedHashSet<>();
    /** 枚举到的属性总数 */
    private volatile int discoveredCount = 0;
    /** 探针摘要（供界面/日志回显） */
    private volatile String probeSummary = "未连接";

    public CarGearHALMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public boolean isConnected() { return connected; }
    public int getDiscoveredCount() { return discoveredCount; }
    public String getProbeSummary() { return probeSummary; }

    private final Runnable connectTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!connected && running) {
                AppLogger.w(LOG_MODULE, "原厂 HAL CarService 连接超时 (" + CONNECT_TIMEOUT_MS + "ms)，档位自动降级回 MCU 串口报文通道");
            }
        }
    };

    public synchronized void start() {
        if (running) return;
        running = true;
        connected = false;
        AppLogger.i(LOG_MODULE, "原厂 HAL 档位直通监听启动中 (CarPropertyManager 反射连接)...");
        try {
            handler.postDelayed(connectTimeoutRunnable, CONNECT_TIMEOUT_MS);
            connectCar();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to start CarGearHALMonitor: " + t.getMessage());
            AppLogger.w(LOG_MODULE, "原厂 HAL 档位监听启动失败: " + t.getMessage());
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
        registeredProps.clear();
        car = null;
        carPropertyManager = null;
        propertyCallback = null;
        probeSummary = "已停止";
        AppLogger.i(LOG_MODULE, "原厂 HAL 档位直通监听已停止");
    }

    // ==================== CarService 连接 ====================

    private boolean connectCar() {
        try {
            Class<?> carClass = Class.forName("android.car.Car");
        } catch (ClassNotFoundException e) {
            AppLogger.w(LOG_MODULE, "当前系统无 android.car 框架，判定为非车机环境，档位走 MCU 通道");
            return false;
        }
        try {
            Class<?> carClass = Class.forName("android.car.Car");
            Object connectionListener = createConnectionListener();
            if (connectionListener == null) {
                AppLogger.w(LOG_MODULE, "CarService 连接监听器创建失败，档位走 MCU 通道");
                return false;
            }
            Handler h = new Handler(Looper.getMainLooper());

            try {
                Class<?> listenerClass = Class.forName("android.car.Car$CarServiceConnectionListener");
                Method m = carClass.getMethod("createCar", Context.class, listenerClass, Handler.class);
                car = m.invoke(null, context, connectionListener, h);
                AppLogger.i(LOG_MODULE, "createCar 已发起 (CarServiceConnectionListener 签名)");
                return true;
            } catch (NoSuchMethodException e1) {
                try {
                    Class<?> listenerClass2 = Class.forName("android.car.Car$ServiceConnectionListener");
                    Method m2 = carClass.getMethod("createCar", Context.class, listenerClass2, Handler.class);
                    car = m2.invoke(null, context, connectionListener, h);
                    AppLogger.i(LOG_MODULE, "createCar 已发起 (ServiceConnectionListener 签名)");
                    return true;
                } catch (NoSuchMethodException e2) {
                    Method m3 = carClass.getMethod("createCar", Context.class);
                    car = m3.invoke(null, context);
                    carClass.getMethod("connect").invoke(car);
                    AppLogger.i(LOG_MODULE, "createCar(Context) + connect() 已发起");
                    handler.postDelayed(() -> {
                        if (running && !connected) {
                            if (bindPropertyManager()) {
                                connected = true;
                            }
                        }
                    }, 1500);
                    return true;
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "connectCar exception: " + t.getMessage());
            AppLogger.w(LOG_MODULE, "原厂 HAL CarService 连接异常: " + t.getMessage());
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
                            handler.removeCallbacks(connectTimeoutRunnable);
                            if (bindPropertyManager()) {
                                connected = true;
                            }
                        } else if ("onServiceDisconnected".equals(name)) {
                            Log.w(TAG, "CarService disconnected");
                            connected = false;
                            probeSummary = "CarService 已断开";
                            AppLogger.w(LOG_MODULE, "原厂 HAL CarService 连接已断开，档位回退 MCU 通道");
                        }
                        return null;
                    }
                });
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    // ==================== 属性管理器与探针 ====================

    private boolean bindPropertyManager() {
        try {
            if (car == null) return false;
            Class<?> carClass = Class.forName("android.car.Car");
            String propertyServiceName = (String) carClass.getField("PROPERTY_SERVICE").get(null);
            carPropertyManager = carClass.getMethod("getCarManager", String.class).invoke(car, propertyServiceName);
            if (carPropertyManager == null) {
                AppLogger.w(LOG_MODULE, "CarPropertyManager 获取为空，档位走 MCU 通道");
                return false;
            }
            AppLogger.i(LOG_MODULE, "CarPropertyManager 已就绪，开始执行 HAL 属性全量探针");

            buildPropertyCallback();
            enumerateAndRegister();
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "bindPropertyManager error: " + t.getMessage());
            AppLogger.w(LOG_MODULE, "CarPropertyManager 绑定异常: " + t.getMessage());
            return false;
        }
    }

    private void buildPropertyCallback() throws Exception {
        Class<?> callbackClass = Class.forName("android.car.CarPropertyManager$CarPropertyEventCallback");
        propertyCallback = Proxy.newProxyInstance(callbackClass.getClassLoader(), new Class<?>[]{callbackClass}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                if ("onChangeEvent".equals(name) && args != null && args.length > 0) {
                    handlePropertyChange(args[0]);
                } else if ("onErrorEvent".equals(name) && args != null && args.length > 0) {
                    try {
                        Object pid = args[0].getClass().getMethod("getPropertyId").invoke(args[0]);
                        Object code = args[0].getClass().getMethod("getErrorCode").invoke(args[0]);
                        AppLogger.w(LOG_MODULE, "HAL 属性回调错误: propId=" + pid + ", code=" + code);
                    } catch (Throwable ignored) {}
                }
                return null;
            }
        });
    }

    /**
     * HAL 全量属性枚举探针 + 档位候选属性注册
     */
    private void enumerateAndRegister() {
        List<Integer> allIds = new ArrayList<>();
        List<String> hitNames = new ArrayList<>();

        // 1. 全量枚举 getPropertyList()
        try {
            Object listObj = carPropertyManager.getClass().getMethod("getPropertyList").invoke(carPropertyManager);
            if (listObj instanceof List) {
                for (Object item : (List<?>) listObj) {
                    if (item == null) continue;
                    Integer id = extractPropertyId(item);
                    if (id == null) continue;
                    allIds.add(id);
                    String pName = extractPropertyName(item, id);
                    String group = propertyGroupName(id);
                    int typeAndArea = id;
                    AppLogger.i("HAL探针", String.format(Locale.US,
                            "属性位 ID=%d (0x%08X) | 名称=%s | 分组=%s | 类型区=0x%06X",
                            id, id, pName, group, typeAndArea & 0x00FFFFFF));

                    String lower = pName == null ? "" : pName.toLowerCase(Locale.US);
                    if (lower.contains("gear") || lower.contains("shift") || lower.contains("挡") || lower.contains("档")) {
                        hitNames.add(pName + "(" + id + ")");
                        if (!allIds.contains(id)) allIds.add(id);
                    }
                }
            }
        } catch (Throwable t) {
            AppLogger.w("HAL探针", "getPropertyList 枚举失败: " + t.getMessage());
        }
        discoveredCount = allIds.size();

        // 2. 标准 AOSP 档位属性一定注册
        List<Integer> candidates = new ArrayList<>();
        candidates.add(PROP_GEAR_SELECTION);
        candidates.add(PROP_CURRENT_GEAR);
        candidates.add(PROP_ECARX_GEAR_POSITION); // 亿咖通原厂 Vendor 档位，有权限时优先
        // 3. 枚举命中的 gear/shift 属性一并注册（含亿咖通 vendor 档位位）
        for (Integer id : allIds) {
            if (id != null && !candidates.contains(id) && isGearLikeHint(id)) {
                candidates.add(id);
            }
        }

        int okCount = 0;
        for (Integer pid : candidates) {
            if (pid == null) continue;
            if (registerGearCallback(pid)) {
                okCount++;
                registeredProps.add(pid);
            }
        }

        probeSummary = String.format(Locale.US, "枚举 %d 项属性 / 命中档位属性 %d 项 / 成功注册 %d 项",
                discoveredCount, hitNames.size(), okCount);
        // 探针结果只输出一次摘要，避免每秒刷屏
        AppLogger.i("HAL探针", "探针完成 共" + discoveredCount + "个属性 命中:" + hitNames + " 注册:" + okCount);

        // 4. 主动读取一次初始档位值，建立 HAL 基准
        for (Integer pid : registeredProps) {
            readInitialValue(pid);
        }
    }

    /** 从枚举元素中提取属性 ID（兼容 List<Integer> 与 List<CarPropertyConfig>） */
    private Integer extractPropertyId(Object item) {
        if (item instanceof Integer) return (Integer) item;
        try {
            Object id = item.getClass().getMethod("getPropertyId").invoke(item);
            if (id instanceof Integer) return (Integer) id;
        } catch (Throwable ignored) {}
        try {
            Object id = item.getClass().getMethod("getPropertyId").invoke(item);
            if (id instanceof Number) return ((Number) id).intValue();
        } catch (Throwable ignored) {}
        return null;
    }

    /** 从枚举元素中提取属性名称（config.getName() 或 getPropertyInfo 回查） */
    private String extractPropertyName(Object item, int id) {
        try {
            Object name = item.getClass().getMethod("getName").invoke(item);
            if (name instanceof String) return (String) name;
        } catch (Throwable ignored) {}
        try {
            Object info = carPropertyManager.getClass().getMethod("getPropertyInfo", Integer.TYPE).invoke(carPropertyManager, id);
            if (info != null) {
                try {
                    Object name = info.getClass().getMethod("getName").invoke(info);
                    if (name instanceof String) return (String) name;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return "未知名称";
    }

    private String propertyGroupName(int id) {
        int g = id & 0xF0000000;
        if (g == 0x10000000) return "SYSTEM";
        if (g == 0x20000000) return "VENDOR";
        if (g == 0x00000000) return "GLOBAL";
        return "OTHER(0x" + Integer.toHexString(g) + ")";
    }

    /** 无名称信息时的兜底判定：对已知标准档位位及亿咖通 Vendor 档位位返回 true */
    private boolean isGearLikeHint(int id) {
        return id == PROP_GEAR_SELECTION || id == PROP_CURRENT_GEAR || id == PROP_ECARX_GEAR_POSITION;
    }

    private boolean registerGearCallback(final int propertyId) {
        try {
            Class<?> callbackClass = Class.forName("android.car.CarPropertyManager$CarPropertyEventCallback");
            try {
                carPropertyManager.getClass().getMethod("registerCallback", callbackClass, Integer.TYPE, Float.TYPE)
                        .invoke(carPropertyManager, propertyCallback, propertyId, 0.0f);
            } catch (NoSuchMethodException e1) {
                carPropertyManager.getClass().getMethod("registerCallback", callbackClass, Integer.TYPE)
                        .invoke(carPropertyManager, propertyCallback, propertyId);
            }
            AppLogger.i(LOG_MODULE, "HAL 档位属性注册成功: ID=" + propertyId + " (0x" + Integer.toHexString(propertyId) + ")");
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "registerCallback failed for " + propertyId + ": " + t.getMessage());
            // 标准属性注册失败才提示；vendor 候选失败静默（本机型无该属性位属正常）
            if (propertyId == PROP_GEAR_SELECTION || propertyId == PROP_CURRENT_GEAR) {
                AppLogger.w(LOG_MODULE, "标准档位属性注册失败 ID=" + propertyId + ": " + t.getMessage());
            }
            return false;
        }
    }

    private void readInitialValue(final int propertyId) {
        try {
            Object value = null;
            try {
                value = carPropertyManager.getClass().getMethod("getProperty", Integer.TYPE, Integer.TYPE)
                        .invoke(carPropertyManager, propertyId, 0);
            } catch (NoSuchMethodException e1) {
                value = carPropertyManager.getClass().getMethod("getProperty", Integer.TYPE)
                        .invoke(carPropertyManager, propertyId);
            }
            if (value == null) return;
            Object raw = value.getClass().getMethod("getValue").invoke(value);
            if (raw instanceof Integer) {
                int rv = (Integer) raw;
                int norm = normalizeHalGear(rv, propertyId);
                AppLogger.i(LOG_MODULE, "HAL 初始档位基准: " + sourceName(propertyId) + " 原始值=" + rv
                        + " -> 识别=" + gearName(norm));
                dispatch(propertyId, rv, norm, true);
            }
        } catch (Throwable t) {
            Log.w(TAG, "readInitialValue failed for " + propertyId + ": " + t.getMessage());
        }
    }

    private void handlePropertyChange(Object carPropertyValue) {
        if (carPropertyValue == null) return;
        try {
            Object pidObj = carPropertyValue.getClass().getMethod("getPropertyId").invoke(carPropertyValue);
            int pid = pidObj instanceof Integer ? (Integer) pidObj : -1;
            Object raw = carPropertyValue.getClass().getMethod("getValue").invoke(carPropertyValue);
            if (!(raw instanceof Integer)) return;

            // 电子手刹只作 P 挡旁证，不直接派生挡位播报
            if (pid == PROP_PARKING_BRAKE_ON) {
                AppLogger.i(LOG_MODULE, "HAL 电子手刹状态变化: " + raw);
                return;
            }

            int rv = (Integer) raw;
            int norm = normalizeHalGear(rv, pid);
            // 档位事件只在真正变化时由外部状态机记录，这里不再重复写日志
            // AppLogger.i(LOG_MODULE, "HAL 档位事件: " + sourceName(pid) + "=" + rv + "->" + gearName(norm));
            dispatch(pid, rv, norm, false);
        } catch (Throwable t) {
            Log.w(TAG, "handlePropertyChange error: " + t.getMessage());
        }
    }

    private void dispatch(final int propertyId, final int rawValue, final int normalizedGear, final boolean initial) {
        if (listener == null) return;
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!initial) {
                        listener.onHalGearRaw(sourceName(propertyId), rawValue, normalizedGear);
                    }
                    if (normalizedGear > 0) {
                        listener.onHalGear(normalizedGear, sourceName(propertyId));
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "listener dispatch error: " + t.getMessage());
                }
            }
        });
    }

    private String sourceName(int propertyId) {
        if (propertyId == PROP_GEAR_SELECTION) return "GEAR_SELECTION";
        if (propertyId == PROP_CURRENT_GEAR) return "CURRENT_GEAR";
        if (propertyId == PROP_PARKING_BRAKE_ON) return "PARKING_BRAKE_ON";
        if (propertyId == PROP_ECARX_GEAR_POSITION) return "ECARX_GEAR_POSITION";
        return "VENDOR_0x" + Integer.toHexString(propertyId);
    }

    // ==================== 挡位归一化 ====================

    /**
     * 把原厂 HAL 上报的原始挡位数值归一化为播报状态机的标准索引
     * 返回: 2=D 前进挡, 3=N 空挡, 4=R 倒车挡, 5=P 驻车挡, 6=S 运动挡, -1=未识别
     *
     * 两套协议并行判定：
     * A. AOSP 标准位（GEAR_SELECTION / CURRENT_GEAR）按 VehicleGear 位掩码解；
     * B. 亿咖通 vendor 属性沿用 MCU 同一套 0x11~0x16 枚举。
     */
    public static int normalizeHalGear(int raw, int propertyId) {
        boolean aospStandard = (propertyId == PROP_GEAR_SELECTION || propertyId == PROP_CURRENT_GEAR);

        // B. 亿咖通 vendor 枚举 0x11~0x16（17~22），与 MCU 报文同源
        switch (raw) {
            case 17: return 2; // 0x11 D
            case 18: return 3; // 0x12 N
            case 19: return 4; // 0x13 R
            case 20: return 5; // 0x14 P
            case 21: // 0x15 S/M
            case 22: return 6; // 0x16 S/B
            default: break;
        }

        // A. AOSP VehicleGear 位掩码
        if (aospStandard || isAospBitmask(raw)) {
            if (raw == 0) return -1;                       // GEAR_UNKNOWN
            if ((raw & GEAR_PARK) != 0) return 5;          // 驻车挡
            if ((raw & GEAR_REVERSE) != 0) return 4;       // 倒车挡
            if ((raw & GEAR_NEUTRAL) != 0) return 3;       // 空挡
            if ((raw & GEAR_DRIVE) != 0) return 2;         // 前进挡
            if (raw >= GEAR_1 && raw <= GEAR_10) return 2; // GEAR_1~GEAR_10 均属前进
            return -1;
        }

        // C. 直通索引：2/3/4/5/6 与 MCU 文本通道完全一致，直接透传
        if (raw >= 2 && raw <= 7) return raw;

        return -1;
    }

    /** 判定数值是否符合 AOSP 位掩码特征（单一位或若干合法挡位位组合） */
    private static boolean isAospBitmask(int raw) {
        if (raw <= 0) return false;
        // 合法位掩码域: 0x0001 ~ 0x2000 (GEAR_NEUTRAL .. GEAR_10)
        if (raw > 0x2000) return false;
        int validMask = GEAR_NEUTRAL | GEAR_REVERSE | GEAR_PARK | GEAR_DRIVE;
        // 仅仅是这几个基础位之一即为典型位掩码
        if ((raw & ~(validMask)) == 0) return true;
        return false;
    }

    private static String gearName(int gear) {
        switch (gear) {
            case 2: return "D挡(前进)";
            case 3: return "N挡(空挡)";
            case 4: return "R挡(倒车)";
            case 5: return "P挡(驻车)";
            case 6:
            case 7: return "S挡(运动)";
            default: return "未识别";
        }
    }
}
