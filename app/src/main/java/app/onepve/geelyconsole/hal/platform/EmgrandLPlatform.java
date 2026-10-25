package app.onepve.geelyconsole.hal.platform;

import android.content.Context;
import android.util.Log;

import app.onepve.geelyconsole.hal.IVehicleKeyAdapter;
import app.onepve.geelyconsole.hal.IVehiclePlatform;
import app.onepve.geelyconsole.hal.IVehicleSensorAdapter;
import app.onepve.geelyconsole.utils.GearStateMachine;
import app.onepve.geelyconsole.utils.SteeringWheelKeyManager;

/**
 * 吉利帝豪 L (亿咖通 E02 / IHU521G / SS13) 原厂硬件平台适配器
 */
public class EmgrandLPlatform implements IVehiclePlatform {
    private static final String TAG = "EmgrandLPlatform";
    public static final String PLATFORM_CODE = "emgrand_l_e02";

    private IVehicleKeyAdapter keyAdapter;
    private IVehicleSensorAdapter sensorAdapter;

    @Override
    public String getPlatformCode() {
        return PLATFORM_CODE;
    }

    @Override
    public String getPlatformName() {
        return "吉利帝豪 L (E02 / IHU521G)";
    }

    @Override
    public double getDefaultFuelCapacityLiters() {
        return 52.0; // 吉利帝豪 L 官方标称容积 52L
    }

    @Override
    public synchronized IVehicleKeyAdapter getKeyAdapter(Context context) {
        if (keyAdapter == null) {
            keyAdapter = new EmgrandKeyAdapterImpl(context);
        }
        return keyAdapter;
    }

    @Override
    public synchronized IVehicleSensorAdapter getSensorAdapter(Context context) {
        if (sensorAdapter == null) {
            sensorAdapter = new EmgrandSensorAdapterImpl(context);
        }
        return sensorAdapter;
    }

    /** 帝豪 L 方控按键桥接实现 (与缤越 COOL 同属 E02 架构) */
    private static class EmgrandKeyAdapterImpl implements IVehicleKeyAdapter {
        private final Context context;
        private SteeringWheelKeyManager keyManager;

        EmgrandKeyAdapterImpl(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public void startListening(Context context, OnVehicleKeyListener listener) {
            Log.i(TAG, "EmgrandKeyAdapter started listening via SteeringWheelKeyManager");
            this.keyManager = new SteeringWheelKeyManager(context);
            this.keyManager.syncMediaKeyReceiverState();
        }

        @Override
        public void stopListening() {
            Log.i(TAG, "EmgrandKeyAdapter stopped listening");
            this.keyManager = null;
        }

        @Override
        public boolean supportsHardwareInterception() {
            return true;
        }

        @Override
        public void setInterceptionEnabled(boolean enabled) {
            if (keyManager != null) {
                keyManager.syncMediaKeyReceiverState();
            }
        }
    }

    /** 帝豪 L 车身信号与挡位桥接实现 */
    private static class EmgrandSensorAdapterImpl implements IVehicleSensorAdapter {
        private final Context context;
        private GearStateMachine gearStateMachine;

        EmgrandSensorAdapterImpl(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public void startListening(Context context, final OnVehicleSensorListener listener) {
            Log.i(TAG, "EmgrandSensorAdapter started listening");
            this.gearStateMachine = new GearStateMachine(context, null);
            this.gearStateMachine.setListener(new GearStateMachine.GearStateListener() {
                @Override
                public void onGearChanged(int gear) {
                    if (listener != null) {
                        int halGear = 0;
                        switch (gear) {
                            case 5: halGear = 0; break; // P
                            case 4: halGear = 1; break; // R
                            case 3: halGear = 2; break; // N
                            case 2: halGear = 3; break; // D
                            default: halGear = gear; break;
                        }
                        listener.onGearChanged(halGear);
                    }
                }
            });
        }

        @Override
        public void stopListening() {
            Log.i(TAG, "EmgrandSensorAdapter stopped listening");
            this.gearStateMachine = null;
        }

        @Override
        public int getCurrentGear() {
            if (gearStateMachine == null) return 0;
            int g = gearStateMachine.getGear();
            switch (g) {
                case 5: return 0;
                case 4: return 1;
                case 3: return 2;
                case 2: return 3;
                default: return g;
            }
        }

        @Override
        public float getCurrentSpeed() {
            return app.onepve.geelyconsole.services.VehicleAutomationService.currentSpeedKmH;
        }
    }
}
