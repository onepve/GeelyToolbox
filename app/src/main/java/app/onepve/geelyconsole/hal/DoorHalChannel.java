package app.onepve.geelyconsole.hal;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 车门/尾门车规属性兜底通道 (DoorHalChannel)
 *
 * 背景（2026-11-11 实测取证，日志包 395816）：
 * 车门/尾门状态原本只有一条来源——logcat 管道里的 MCU 串口报文 91 02 01（3 秒一帧）。
 * 一旦这条管道读不到车况（子进程被掐、缓冲区被洪水冲穿、或导出时恰好没抓到帧），
 * 车门状态就冻结在上次值，车主表现为「开了门车机没反应、日志里车门记录 0 条」。
 *
 * 本通道是**条件兜底**，与方控 HW_KEY_INPUT 兜底同一设计原则：
 * 1. 平时绝不注册，主通道（logcat 串口报文）健康时保持彻底静默，避免双来源打架；
 * 2. 只有当主通道连续 15 秒收不到车门报文时才注册，直接订阅车规属性
 *    DOOR_POS(0x26400B09) 与 TRUNK_POS(0x21400213)；
 * 3. 主通道恢复收帧后立刻注销，任何时刻只有一条车门来源；
 * 4. 属性不被本车型支持时 register 返回 false，绝不重试成死循环，上层自行降级。
 *
 * 注意：本类只订阅车规属性回调，**不复用、不复活 CarGearHALMonitor**（门禁禁止）。
 */
public class DoorHalChannel {

    private static final String TAG = "DoorHalChannel";

    /** 门开/关判定阈值：DOOR_POS 各 Area 的取值 0=关，>=1=开（覆盖常见 AOSP 语义） */
    private static final int AREA_FL = AndroidCarClient.AREA_DOOR_ROW_1_LEFT;
    private static final int AREA_FR = AndroidCarClient.AREA_DOOR_ROW_1_RIGHT;
    private static final int AREA_RL = AndroidCarClient.AREA_DOOR_ROW_2_LEFT;
    private static final int AREA_RR = AndroidCarClient.AREA_DOOR_ROW_2_RIGHT;

    public interface OnDoorStateListener {
        /**
         * @param fl/fr/rl/rr 1=开 0=关；-1=该门本次未取到值
         * @param trunk       1=开 0=关 -1=未取到
         */
        void onDoorState(int fl, int fr, int rl, int rr, int trunk);
    }

    private Object mCallbackProxy;
    private Object mRegisteredManager;
    private Method mUnregisterMethod;
    private final AtomicBoolean mRegistered = new AtomicBoolean(false);
    private volatile OnDoorStateListener mListener;

    /** 最近一次取到的四门/尾门值，-1 表示还没读到 */
    private volatile int vFl = -1, vFr = -1, vRl = -1, vRr = -1, vTrunk = -1;

    public boolean isRegistered() {
        return mRegistered.get();
    }

    public void setListener(OnDoorStateListener listener) {
        this.mListener = listener;
    }

    /**
     * 反射注册 DOOR_POS + TRUNK_POS 属性回调。
     * 走与 HwKeyInputChannel 完全相同的签名兼容链（registerCallback 两种签名），
     * 任一属性注册成功即视为兜底可用。
     *
     * @return true=至少一路注册成功
     */
    public synchronized boolean register(Object carPropertyManager) {
        if (mRegistered.get()) return true;
        if (carPropertyManager == null) {
            Log.w(TAG, "register: CarPropertyManager is null");
            return false;
        }
        try {
            Class<?> callbackClass = Class.forName("android.car.CarPropertyManager$CarPropertyEventCallback");
            mCallbackProxy = Proxy.newProxyInstance(
                    callbackClass.getClassLoader(),
                    new Class<?>[]{callbackClass},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            String name = method.getName();
                            if ("onChangeEvent".equals(name) && args != null && args.length >= 1) {
                                handlePropertyChange(args[0]);
                            } else if ("onErrorEvent".equals(name) && args != null && args.length > 0) {
                                Log.w(TAG, "onErrorEvent: " + args[0]);
                            }
                            return null;
                        }
                    }
            );

            Class<?> mgrClass = carPropertyManager.getClass();
            boolean any = false;
            any |= registerOne(carPropertyManager, mgrClass, callbackClass, AndroidCarClient.DOOR_POS);
            any |= registerOne(carPropertyManager, mgrClass, callbackClass, AndroidCarClient.TRUNK_POS);

            if (any) {
                try {
                    mUnregisterMethod = mgrClass.getMethod("unregisterCallback", callbackClass);
                    mRegisteredManager = carPropertyManager;
                } catch (NoSuchMethodException ignored) {}
                mRegistered.set(true);
                Log.i(TAG, "车门兜底通道已注册 (DOOR_POS/TRUNK_POS)");
                // 注册成功后立刻主动拉一次当前值，避免车主下一次开门之前界面还是旧状态
                pullSnapshot(carPropertyManager);
            } else {
                Log.w(TAG, "本车型不支持 DOOR_POS/TRUNK_POS 属性回调，车门兜底不可用");
            }
            return any;
        } catch (Throwable t) {
            Log.w(TAG, "register 失败: " + t.getMessage());
            return false;
        }
    }

    private boolean registerOne(Object mgr, Class<?> mgrClass, Class<?> callbackClass, int propId) {
        try {
            Method m = mgrClass.getMethod("registerCallback", callbackClass, Integer.TYPE, Float.TYPE);
            m.invoke(mgr, mCallbackProxy, propId, 0.0f);
            return true;
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable t) {
            Log.w(TAG, "registerCallback(prop=" + propId + ") 失败: " + t.getMessage());
            return false;
        }
        try {
            Method m = mgrClass.getMethod("registerCallback", callbackClass, Integer.TYPE);
            m.invoke(mgr, mCallbackProxy, propId);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "registerCallback2(prop=" + propId + ") 失败: " + t.getMessage());
            return false;
        }
    }

    /** 主动读一次快照（注册成功瞬间补齐状态） */
    private void pullSnapshot(Object mgr) {
        try {
            Class<?> mgrClass = mgr.getClass();
            Method getInt = null;
            try {
                getInt = mgrClass.getMethod("getIntProperty", Integer.TYPE, Integer.TYPE);
            } catch (NoSuchMethodException ignored) {}
            if (getInt == null) return;
            vFl = (Integer) getInt.invoke(mgr, AndroidCarClient.DOOR_POS, AREA_FL);
            vFr = (Integer) getInt.invoke(mgr, AndroidCarClient.DOOR_POS, AREA_FR);
            vRl = (Integer) getInt.invoke(mgr, AndroidCarClient.DOOR_POS, AREA_RL);
            vRr = (Integer) getInt.invoke(mgr, AndroidCarClient.DOOR_POS, AREA_RR);
            vTrunk = (Integer) getInt.invoke(mgr, AndroidCarClient.TRUNK_POS, 0);
            dispatch();
        } catch (Throwable t) {
            Log.w(TAG, "pullSnapshot 失败(不影响回调): " + t.getMessage());
        }
    }

    /**
     * 属性变化回调：只更新变化的那一项，避免一路属性变化把其他门覆盖成未知。
     */
    private void handlePropertyChange(Object carPropertyValue) {
        if (carPropertyValue == null) return;
        try {
            Class<?> cls = carPropertyValue.getClass();
            Object propIdObj = cls.getMethod("getPropertyId").invoke(carPropertyValue);
            Object areaObj = cls.getMethod("getAreaId").invoke(carPropertyValue);
            Object valueObj = cls.getMethod("getValue").invoke(carPropertyValue);
            if (!(propIdObj instanceof Integer) || !(valueObj instanceof Integer)) return;
            int propId = (Integer) propIdObj;
            int area = (areaObj instanceof Integer) ? (Integer) areaObj : 0;
            int value = (Integer) valueObj;
            int open = value == 0 ? 0 : 1; // 0=关，非0=开

            if (propId == AndroidCarClient.DOOR_POS) {
                if (area == AREA_FL || area == 0) vFl = open;
                if (area == AREA_FR) vFr = open;
                if (area == AREA_RL) vRl = open;
                if (area == AREA_RR) vRr = open;
                // 部分车机 DOOR_POS 是位掩码整型，area=0 时按位解析兜底
                if (area == 0) {
                    vFl = ((value & AREA_FL) != 0) ? 1 : vFl;
                    vFr = ((value & AREA_FR) != 0) ? 1 : vFr;
                    vRl = ((value & AREA_RL) != 0) ? 1 : vRl;
                    vRr = ((value & AREA_RR) != 0) ? 1 : vRr;
                }
            } else if (propId == AndroidCarClient.TRUNK_POS) {
                vTrunk = open;
            } else {
                return;
            }
            dispatch();
        } catch (Throwable t) {
            Log.w(TAG, "handlePropertyChange 解析失败: " + t.getMessage());
        }
    }

    private void dispatch() {
        OnDoorStateListener l = mListener;
        if (l == null) return;
        try {
            l.onDoorState(vFl, vFr, vRl, vRr, vTrunk);
        } catch (Throwable t) {
            Log.w(TAG, "回调上层异常: " + t.getMessage());
        }
    }

    /** 注销（RAII 闭环，绝不留僵尸 CarService 回调） */
    public synchronized void unregister() {
        if (!mRegistered.get()) return;
        mRegistered.set(false);
        try {
            if (mUnregisterMethod != null && mRegisteredManager != null && mCallbackProxy != null) {
                mUnregisterMethod.invoke(mRegisteredManager, mCallbackProxy);
            }
        } catch (Throwable t) {
            Log.w(TAG, "unregister 失败: " + t.getMessage());
        }
        mCallbackProxy = null;
        mRegisteredManager = null;
        mUnregisterMethod = null;
        vFl = vFr = vRl = vRr = vTrunk = -1;
        Log.i(TAG, "车门兜底通道已注销（主通道恢复，单一来源）");
    }
}
