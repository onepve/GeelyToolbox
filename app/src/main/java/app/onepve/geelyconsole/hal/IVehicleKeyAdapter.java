package app.onepve.geelyconsole.hal;

import android.content.Context;

/**
 * 方控按键抽象适配接口
 */
public interface IVehicleKeyAdapter {
    /** 按键事件通用统一监听回调 */
    interface OnVehicleKeyListener {
        /**
         * 捕获到原始车规方控按键
         * @param rawKeyCode 原始物理键码
         * @param action 按键动作 (0=DOWN, 1=UP)
         * @param rawPayload 平台专有原始负载或标识字符串
         */
        void onKeyCaptured(int rawKeyCode, int action, String rawPayload);
    }

    /** 启动按键监听 */
    void startListening(Context context, OnVehicleKeyListener listener);

    /** 停止按键监听并释放资源 */
    void stopListening();

    /** 是否支持底层硬件拦截（免原厂并发） */
    boolean supportsHardwareInterception();

    /** 设置是否拦截原厂默认按键消费 */
    void setInterceptionEnabled(boolean enabled);
}
