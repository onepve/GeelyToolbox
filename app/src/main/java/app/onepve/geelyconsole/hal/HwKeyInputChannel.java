package app.onepve.geelyconsole.hal;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HW_KEY_INPUT 车规物理按键监听通道（借鉴米小江 CarMedia 1.6.8 CarPropertyKeyMonitor 实现思路）
 *
 * 用途定位（与既有 logcat 方控监听的关系，严格遵守“卡片与按钮门禁铁律”）：
 * 1. 本通道【只做录音学习】，绝不进入日常方控消费链路：
 *    日常按键判定仍 100% 走既有 SteeringWheelKeyManager 的 logcat 事件直连通道，
 *    本类不得在非录制态下调用任何业务回调，防止改变现有方控行为。
 * 2. 通过反射注册 CarPropertyManager 的 HW_KEY_INPUT (678428923) 属性回调，
 *    在车主按下方向盘任意物理键的瞬间，由车机 CarService 主动推送
 *    int[]{action, keyCode, display} —— 免子进程、免轮询、零热路径开销。
 * 3. 仅用于「方控按键学习」弹窗期间捕获未知键码（新车型零代码适配）。
 *
 * 注册失败（车型无此属性 / 权限受限 / CarService 未就绪）时返回 false，
 * 上层自动回退 logcat 抓取方案，不影响任何既有功能。
 */
public class HwKeyInputChannel {
    private static final String TAG = "HwKeyInputChannel";

    /** 吉利/亿咖通车型 CarService 暴露的方控硬按键属性 ID (与米小江 CarMedia 同源实证) */
    public static final int HW_KEY_INPUT_PROP = 678428923;

    // ── 【注册结果可诊断 v1.8.33.20】──
    // 历史坑：注册失败一律被写成「本车 HW_KEY_INPUT 属性不可用」，可实际日志里
    // 抛的是 ClassNotFoundException（本车 ROM 根本没有这个回调类），
    // 把「ROM 没这套 API」误判成「车型不支持该属性」，等于把死路当成可修的路。
    // 现在把失败原因分成固定的几种代号，由上层原样记进内存取证日志。
    public static final int ERR_NONE = 0;              // 未失败
    public static final int ERR_NO_MANAGER = 1;        // CarPropertyManager 拿不到（CarService 未就绪）
    public static final int ERR_CLASS_NOT_FOUND = 2;   // ROM 没有 CarPropertyEventCallback 类 → 本车不可能生效
    public static final int ERR_NO_SUCH_METHOD = 3;    // 两种 registerCallback 签名都不存在 → API 版本不符
    public static final int ERR_SECURITY = 4;          // 权限被拒
    public static final int ERR_PROP_UNSUPPORTED = 5;  // 属性 ID 本车不支持
    public static final int ERR_OTHER = 6;             // 其它

    private volatile int mLastErrorCode = ERR_NONE;
    private volatile String mLastErrorDetail = "";
    private volatile long mRegisteredAtMs = 0L;
    /** HW_KEY_INPUT 回调到达总次数（含未启用捕获时），用来区分「没接上」和「接上了但车机不推」 */
    private final java.util.concurrent.atomic.AtomicLong mEventCount = new java.util.concurrent.atomic.AtomicLong(0L);

    public int getLastErrorCode() {
        return mLastErrorCode;
    }

    public String getLastErrorDetail() {
        return mLastErrorDetail;
    }

    /** 事件计数：注册成功后一直为 0 就说明车机根本不推这个属性，而不是我们没注册上 */
    public long getEventCount() {
        return mEventCount.get();
    }

    /** 给上层写取证日志用的一句话摘要 */
    public String getHealthSummary() {
        if (mRegistered.get()) {
            long upMin = mRegisteredAtMs > 0
                    ? (android.os.SystemClock.elapsedRealtime() - mRegisteredAtMs) / 60000L : 0L;
            return "已注册 " + upMin + " 分钟，收到按键回调 " + mEventCount.get() + " 次";
        }
        return "未注册(" + errorCodeName(mLastErrorCode) + ")" + (mLastErrorDetail.isEmpty() ? "" : "：" + mLastErrorDetail);
    }

    public static String errorCodeName(int code) {
        switch (code) {
            case ERR_NO_MANAGER: return "CarService未就绪";
            case ERR_CLASS_NOT_FOUND: return "ROM无Car回调类";
            case ERR_NO_SUCH_METHOD: return "注册签名不符";
            case ERR_SECURITY: return "权限被拒";
            case ERR_PROP_UNSUPPORTED: return "本车无此属性";
            case ERR_OTHER: return "其它异常";
            default: return "未失败";
        }
    }

    private void markFailure(int code, String detail, Throwable t) {
        mLastErrorCode = code;
        mLastErrorDetail = detail != null ? detail : "";
        Log.w(TAG, "HAL注册失败[" + errorCodeName(code) + "] " + mLastErrorDetail
                + (t != null ? " | " + t.getClass().getSimpleName() : ""));
    }

    public interface OnHwKeyListener {
        /**
         * 捕获到一次物理按键事件
         * @param keyCode 原始物理键码 (arr[1])
         * @param isDown  true=按下(arr[0]==0), false=抬起(arr[0]!=0)
         */
        void onHwKey(int keyCode, boolean isDown);
    }

    private Object mCallbackProxy;          // CarPropertyEventCallback 动态代理
    private Object mRegisteredManager;      // 注册时使用的 CarPropertyManager 引用 (unregister 用)
    private Method mUnregisterMethod;       // unregisterCallback 方法缓存
    private final AtomicBoolean mRegistered = new AtomicBoolean(false);
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private volatile OnHwKeyListener mListener;
    private volatile boolean mCaptureEnabled = false;

    /** 设置监听者与是否启用捕获（false=注册中也不回调，彻底静默） */
    public void setListener(OnHwKeyListener listener, boolean captureEnabled) {
        this.mListener = listener;
        this.mCaptureEnabled = captureEnabled;
    }

    public boolean isRegistered() {
        return mRegistered.get();
    }

    /**
     * 反射注册 HW_KEY_INPUT 属性监听。仅在 AndroidCarClient 已连接成功时调用。
     * @return true=注册成功；false=车型不支持或注册失败（上层回退 logcat）
     */
    public synchronized boolean register(Object carPropertyManager, final Context context) {
        if (mRegistered.get()) return true;
        if (carPropertyManager == null) {
            markFailure(ERR_NO_MANAGER, "CarPropertyManager=null", null);
            return false;
        }
        Class<?> callbackClass;
        try {
            callbackClass = Class.forName("android.car.CarPropertyManager$CarPropertyEventCallback");
        } catch (ClassNotFoundException e) {
            // 本车 ROM 根本没这套 Car API：这是死路，重试一万次也不会通。
            // 上层看到这个代号应当直接放弃该通道，而不是继续当成「可修」去等重试。
            markFailure(ERR_CLASS_NOT_FOUND,
                    "Class.forName(android.car.CarPropertyManager$CarPropertyEventCallback) 抛 ClassNotFoundException，本车 ROM 无此 API",
                    e);
            return false;
        }
        try {

            this.mCallbackProxy = Proxy.newProxyInstance(
                    callbackClass.getClassLoader(),
                    new Class<?>[]{callbackClass},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            String name = method.getName();
                            if ("onChangeEvent".equals(name) && args != null && args.length >= 1) {
                                handlePropertyChange(args[0]);
                            } else if ("onErrorEvent".equals(name)) {
                                Log.w(TAG, "onErrorEvent: " + (args != null && args.length > 0 ? args[0] : ""));
                            }
                            return null;
                        }
                    }
            );

            Class<?> mgrClass = carPropertyManager.getClass();
            // 注册签名兼容链（对齐米小江实车验证过的两种签名）：
            // sig1: registerCallback(callback, propertyId, rate)
            // sig2: registerCallback(callback, propertyId)
            boolean ok = false;
            Method sig1 = null, sig2 = null;
            try {
                sig1 = mgrClass.getMethod("registerCallback", callbackClass, Integer.TYPE, Float.TYPE);
            } catch (NoSuchMethodException ignored) {}
            try {
                sig2 = mgrClass.getMethod("registerCallback", callbackClass, Integer.TYPE);
            } catch (NoSuchMethodException ignored) {}
            if (sig1 == null && sig2 == null) {
                markFailure(ERR_NO_SUCH_METHOD,
                        "CarPropertyManager 两种 registerCallback 签名都不存在（ROM 的 Car API 版本与本机不符）", null);
                return false;
            }
            try {
                if (sig1 != null) {
                    sig1.invoke(carPropertyManager, mCallbackProxy, HW_KEY_INPUT_PROP, 0.0f);
                } else {
                    sig2.invoke(carPropertyManager, mCallbackProxy, HW_KEY_INPUT_PROP);
                }
                ok = true;
            } catch (java.lang.reflect.InvocationTargetException ite) {
                // 真正的原因藏在 target 异常里，以前这里只打 getMessage() 导致什么都看不出来
                Throwable cause = ite.getCause() != null ? ite.getCause() : ite;
                if (cause instanceof SecurityException) {
                    markFailure(ERR_SECURITY, "registerCallback 被权限拒绝：" + cause.getMessage(), cause);
                } else if (cause instanceof IllegalArgumentException) {
                    markFailure(ERR_PROP_UNSUPPORTED,
                            "属性 " + HW_KEY_INPUT_PROP + " 本车 CarService 未声明：" + cause.getMessage(), cause);
                } else {
                    markFailure(ERR_OTHER, "registerCallback 抛 "
                            + cause.getClass().getSimpleName() + "：" + cause.getMessage(), cause);
                }
                return false;
            }

            if (ok) {
                // 缓存 unregister 方法，停止时彻底注销，不留僵尸回调
                try {
                    mUnregisterMethod = mgrClass.getMethod("unregisterCallback", callbackClass);
                    mRegisteredManager = carPropertyManager;
                } catch (NoSuchMethodException ignored) {}
                mRegistered.set(true);
                mLastErrorCode = ERR_NONE;
                mLastErrorDetail = "";
                mRegisteredAtMs = android.os.SystemClock.elapsedRealtime();
                Log.i(TAG, "HW_KEY_INPUT property callback registered (prop=" + HW_KEY_INPUT_PROP + ")");
            }
            return ok;
        } catch (Throwable t) {
            markFailure(ERR_OTHER, "registerPropertyCallback 未知异常：" + t.getMessage(), t);
            return false;
        }
    }

    /**
     * HW_KEY_INPUT 报文解析：value = int[]{action(0按下/其他抬起), keyCode, display}
     */
    private void handlePropertyChange(Object carPropertyValue) {
        // 事件计数必须在 mCaptureEnabled 判断之前：即使当前不是录制学习态，
        // 「车机到底推不推这个属性」也必须留痕，否则无法区分「没接上」和「接上了但车机不推」。
        mEventCount.incrementAndGet();
        if (!mCaptureEnabled) return; // 门禁：非录制学习态，捕获通道彻底静默
        if (carPropertyValue == null) return;
        try {
            Object value = carPropertyValue.getClass().getMethod("getValue").invoke(carPropertyValue);
            if (!(value instanceof int[])) {
                Log.w(TAG, "unexpected value type: " + (value != null ? value.getClass().getName() : "null"));
                return;
            }
            int[] arr = (int[]) value;
            if (arr.length < 2) {
                Log.w(TAG, "int[] length < 2, length=" + arr.length);
                return;
            }
            int action = arr[0];
            int keyCode = arr[1];
            boolean isDown = (action == 0);
            Log.d(TAG, "HW_KEY_INPUT key " + (isDown ? "DOWN" : "UP") + ": code=" + keyCode);
            final OnHwKeyListener l = mListener;
            if (l != null) {
                mMain.post(new Runnable() {
                    @Override
                    public void run() {
                        OnHwKeyListener cur = mListener;
                        if (cur != null && mCaptureEnabled) {
                            try {
                                cur.onHwKey(keyCode, isDown);
                            } catch (Throwable t) {
                                Log.w(TAG, "onHwKey callback error: " + t.getMessage());
                            }
                        }
                    }
                });
            }
        } catch (Throwable t) {
            Log.w(TAG, "handlePropertyChange error: " + t.getMessage());
        }
    }

    /**
     * 停止监听并彻底注销回调（RAII 闭环：杜绝残留回调吃 CarService 句柄）
     */
    public synchronized void unregister() {
        mCaptureEnabled = false;
        mListener = null;
        if (!mRegistered.getAndSet(false)) return;
        try {
            if (mUnregisterMethod != null && mRegisteredManager != null && mCallbackProxy != null) {
                mUnregisterMethod.invoke(mRegisteredManager, mCallbackProxy);
                Log.i(TAG, "HW_KEY_INPUT property callback unregistered");
            }
        } catch (Throwable t) {
            Log.w(TAG, "unregisterCallback error: " + t.getMessage());
        }
        mCallbackProxy = null;
        mRegisteredManager = null;
        mUnregisterMethod = null;
    }
}
