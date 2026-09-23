package app.onepve.geelyconsole.hal.platform;

import android.content.Context;
import android.util.Log;

import app.onepve.geelyconsole.hal.IVehicleKeyAdapter;
import app.onepve.geelyconsole.hal.IVehiclePlatform;
import app.onepve.geelyconsole.hal.IVehicleSensorAdapter;

/**
 * 通用 AOSP / 标准安卓车机硬件平台实现（跨机型扩容基础模板）
 */
public class GenericAospPlatform implements IVehiclePlatform {
    private static final String TAG = "GenericAospPlatform";
    public static final String PLATFORM_CODE = "generic_aosp";

    private IVehicleKeyAdapter keyAdapter;
    private IVehicleSensorAdapter sensorAdapter;

    @Override
    public String getPlatformCode() {
        return PLATFORM_CODE;
    }

    @Override
    public String getPlatformName() {
        return "通用 AOSP 车载硬件平台";
    }

    @Override
    public synchronized IVehicleKeyAdapter getKeyAdapter(Context context) {
        if (keyAdapter == null) {
            keyAdapter = new GenericKeyAdapterImpl();
        }
        return keyAdapter;
    }

    @Override
    public synchronized IVehicleSensorAdapter getSensorAdapter(Context context) {
        if (sensorAdapter == null) {
            sensorAdapter = new GenericSensorAdapterImpl();
        }
        return sensorAdapter;
    }

    private static class GenericKeyAdapterImpl implements IVehicleKeyAdapter {
        @Override
        public void startListening(Context context, OnVehicleKeyListener listener) {
            Log.i(TAG, "GenericKeyAdapter initialized");
        }

        @Override
        public void stopListening() {
            Log.i(TAG, "GenericKeyAdapter stopped");
        }

        @Override
        public boolean supportsHardwareInterception() {
            return false;
        }

        @Override
        public void setInterceptionEnabled(boolean enabled) {
            // 通用平台无原厂抢占机制
        }
    }

    private static class GenericSensorAdapterImpl implements IVehicleSensorAdapter {
        @Override
        public void startListening(Context context, OnVehicleSensorListener listener) {
            Log.i(TAG, "GenericSensorAdapter initialized");
        }

        @Override
        public void stopListening() {
            Log.i(TAG, "GenericSensorAdapter stopped");
        }

        @Override
        public int getCurrentGear() {
            return 0; // P
        }

        @Override
        public float getCurrentSpeed() {
            return 0.0f;
        }
    }
}
