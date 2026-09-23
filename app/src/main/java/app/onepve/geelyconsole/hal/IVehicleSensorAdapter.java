package app.onepve.geelyconsole.hal;

import android.content.Context;

/**
 * 车身传感器与状态抽象适配接口（车速、挡位、车门等）
 */
public interface IVehicleSensorAdapter {
    /** 车身传感器统一状态变化监听 */
    interface OnVehicleSensorListener {
        void onGearChanged(int gear);           // P=0, R=1, N=2, D=3
        void onSpeedChanged(float speedKmh);    // 车速
        void onDoorChanged(boolean isOpen, int doorMask); // 门状态
        void onTrunkChanged(boolean isOpen);    // 尾门状态
        void onDriveModeChanged(int mode);      // 驾驶模式
    }

    /** 启动车身信号监听 */
    void startListening(Context context, OnVehicleSensorListener listener);

    /** 停止车身信号监听 */
    void stopListening();

    /** 当前挡位同步查询 */
    int getCurrentGear();

    /** 当前车速同步查询 (km/h) */
    float getCurrentSpeed();
}
