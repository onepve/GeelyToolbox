package app.onepve.geelyconsole.hal.platform;

import android.content.Context;
import android.util.Log;

import app.onepve.geelyconsole.hal.IVehicleKeyAdapter;
import app.onepve.geelyconsole.hal.IVehiclePlatform;
import app.onepve.geelyconsole.hal.IVehicleSensorAdapter;
import app.onepve.geelyconsole.utils.GearStateMachine;
import app.onepve.geelyconsole.utils.SteeringWheelKeyManager;

/**
 * 吉利缤越 COOL (亿咖通 E02 / IHU516G) 原厂硬件平台适配器
 */
public class BinyueCoolPlatform implements IVehiclePlatform {
    private static final String TAG = "BinyueCoolPlatform";
    public static final String PLATFORM_CODE = "binyue_cool_e02";

    private IVehicleKeyAdapter keyAdapter;
    private IVehicleSensorAdapter sensorAdapter;

    @Override
    public String getPlatformCode() {
        return PLATFORM_CODE;
    }

    @Override
    public String getPlatformName() {
        return "吉利缤越 COOL (E02 / IHU516G)";
    }

    @Override
    public synchronized IVehicleKeyAdapter getKeyAdapter(Context context) {
        if (keyAdapter == null) {
            keyAdapter = new BinyueKeyAdapterImpl(context);
        }
        return keyAdapter;
    }

    @Override
    public synchronized IVehicleSensorAdapter getSensorAdapter(Context context) {
        if (sensorAdapter == null) {
            sensorAdapter = new BinyueSensorAdapterImpl(context);
        }
        return sensorAdapter;
    }

    /** 缤越 COOL 专有方控按键桥接实现 */
    private static class BinyueKeyAdapterImpl implements IVehicleKeyAdapter {
        private final Context context;
        private SteeringWheelKeyManager keyManager;

        BinyueKeyAdapterImpl(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public void startListening(Context context, OnVehicleKeyListener listener) {
            Log.i(TAG, "BinyueKeyAdapter started listening via SteeringWheelKeyManager");
            this.keyManager = new SteeringWheelKeyManager(context);
            this.keyManager.syncMediaKeyReceiverState();
        }

        @Override
        public void stopListening() {
            Log.i(TAG, "BinyueKeyAdapter stopped listening");
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

    /** 缤越 COOL 专有车身信号与挡位桥接实现 */
    private static class BinyueSensorAdapterImpl implements IVehicleSensorAdapter {
        private final Context context;
        private GearStateMachine gearStateMachine;

        BinyueSensorAdapterImpl(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public void startListening(Context context, final OnVehicleSensorListener listener) {
            Log.i(TAG, "BinyueSensorAdapter started listening");
            this.gearStateMachine = new GearStateMachine(context, null);
            this.gearStateMachine.setListener(new GearStateMachine.GearStateListener() {
                @Override
                public void onGearChanged(int gear) {
                    if (listener != null) {
                        // 统一 HAL 挡位映射: 0=P, 1=R, 2=N, 3=D
                        int halGear = 0;
                        if (gear == 4) halGear = 1;      // R
                        else if (gear == 3) halGear = 2; // N
                        else if (gear == 2) halGear = 3; // D
                        else if (gear == 5) halGear = 0; // P
                        listener.onGearChanged(halGear);
                    }
                }
            });
        }

        @Override
        public void stopListening() {
            Log.i(TAG, "BinyueSensorAdapter stopped listening");
            this.gearStateMachine = null;
        }

        @Override
        public int getCurrentGear() {
            if (gearStateMachine != null) {
                int g = gearStateMachine.getGear();
                if (g == 4) return 1; // R
                if (g == 3) return 2; // N
                if (g == 2) return 3; // D
                if (g == 5) return 0; // P
            }
            return 0;
        }

        @Override
        public float getCurrentSpeed() {
            return 0.0f;
        }
    }
}
