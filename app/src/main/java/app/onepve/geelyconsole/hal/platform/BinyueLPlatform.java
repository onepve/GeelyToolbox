package app.onepve.geelyconsole.hal.platform;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import app.onepve.geelyconsole.hal.AndroidCarClient;
import app.onepve.geelyconsole.hal.IVehicleKeyAdapter;
import app.onepve.geelyconsole.hal.IVehiclePlatform;
import app.onepve.geelyconsole.hal.IVehicleSensorAdapter;
import app.onepve.geelyconsole.services.VehicleAutomationService;
import app.onepve.geelyconsole.utils.AppLogger;
import app.onepve.geelyconsole.utils.SteeringWheelKeyManager;

/**
 * 吉利缤越 L (IHU624G / 魅族 Flyme Auto) 硬件平台适配器
 * 核心特性：
 * 1. Android Automotive OS (AAOS) 原生 VHAL 直通，彻底告别 logcat 正则抓包
 * 2. 独立四门 (DOOR_POS) 与后备箱 (TRUNK_POS) 实时状态感知，支持 voicedoor 语音播报
 * 3. 动力总成 CURRENT_GEAR 挡位监听 (P/R/N/D)
 * 4. BODY_BATTERY_U_BATT_INFO 蓄电池电压实时感知
 * 5. Flyme Auto 专有泊车 AVM 联动
 */
public class BinyueLPlatform implements IVehiclePlatform {
    private static final String TAG = "BinyueLPlatform";
    public static final String PLATFORM_CODE = "binyue_l_flyme_auto";

    private IVehicleKeyAdapter keyAdapter;
    private IVehicleSensorAdapter sensorAdapter;

    @Override
    public String getPlatformCode() {
        return PLATFORM_CODE;
    }

    @Override
    public String getPlatformName() {
        return "吉利缤越 L (IHU624G / Flyme Auto)";
    }

    @Override
    public synchronized IVehicleKeyAdapter getKeyAdapter(Context context) {
        if (keyAdapter == null) {
            keyAdapter = new BinyueLKeyAdapterImpl(context);
        }
        return keyAdapter;
    }

    @Override
    public synchronized IVehicleSensorAdapter getSensorAdapter(Context context) {
        if (sensorAdapter == null) {
            sensorAdapter = new BinyueLSensorAdapterImpl(context);
        }
        return sensorAdapter;
    }

    /**
     * 缤越 L 方控按键适配实现 (标准 MediaKey 接收与 Flyme Auto 按键管理)
     */
    private static class BinyueLKeyAdapterImpl implements IVehicleKeyAdapter {
        private final Context context;
        private SteeringWheelKeyManager keyManager;

        BinyueLKeyAdapterImpl(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public void startListening(Context context, OnVehicleKeyListener listener) {
            Log.i(TAG, "BinyueLKeyAdapter started listening via SteeringWheelKeyManager");
            this.keyManager = new SteeringWheelKeyManager(context);
            this.keyManager.syncMediaKeyReceiverState();
        }

        @Override
        public void stopListening() {
            Log.i(TAG, "BinyueLKeyAdapter stopped listening");
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

    /**
     * 缤越 L 专有车身信号与传感器适配实现 (通过 AndroidCarClient 驱动)
     */
    public static class BinyueLSensorAdapterImpl implements IVehicleSensorAdapter {
        private static final long POLL_INTERVAL_MS = 500;

        private final Context context;
        private AndroidCarClient carClient;
        private OnVehicleSensorListener listener;
        private Handler pollHandler;
        private volatile boolean isRunning = false;

        private int lastRawGear = -1;
        private int lastHalGear = -1;
        private float lastSpeed = -1.0f;
        private int lastTrunkState = -1;

        public BinyueLSensorAdapterImpl(Context context) {
            this.context = context.getApplicationContext();
        }

        /**
         * AAOS VHAL 挡位映射至标准 HAL 格式：
         * AAOS: 1=N, 2=R, 4=P, 8=D
         * HAL 规范: P=0, R=1, N=2, D=3
         */
        private int convertToHalGear(int rawGear) {
            if (rawGear == 1) return 2; // N
            if (rawGear == 2) return 1; // R
            if (rawGear == 4) return 0; // P
            if (rawGear == 8) return 3; // D
            return 0; // 兜底 P
        }

        /**
         * AAOS VHAL 挡位映射至 GeelyToolbox 内部 5 挡位体系：
         * 2=D, 3=N, 4=R, 5=P
         */
        private int convertToInternalGear(int rawGear) {
            if (rawGear == 1) return 3; // N
            if (rawGear == 2) return 4; // R
            if (rawGear == 4) return 5; // P
            if (rawGear == 8) return 2; // D
            return 0;
        }

        @Override
        public void startListening(Context context, OnVehicleSensorListener listener) {
            this.listener = listener;
            this.isRunning = true;
            this.carClient = AndroidCarClient.getInstance();

            AppLogger.i("车身联动", "BinyueLSensorAdapter 正在连接 AAOS VHAL 服务...");
            this.carClient.connect(context, new AndroidCarClient.OnCarConnectListener() {
                @Override
                public void onCarConnected() {
                    AppLogger.i("车身联动", "AAOS VHAL 核心服务连接成功，启动车身状态感知循环");
                    pollHandler = new Handler(Looper.getMainLooper());
                    pollHandler.post(pollRunnable);
                }

                @Override
                public void onCarDisconnected() {
                    AppLogger.w("车身联动", "AAOS VHAL 服务已断开");
                    if (pollHandler != null) {
                        pollHandler.removeCallbacks(pollRunnable);
                        pollHandler = null;
                    }
                }
            });
        }

        private final Runnable pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                pollVehicleState();
                if (pollHandler != null && carClient != null && carClient.isConnected()) {
                    pollHandler.postDelayed(this, POLL_INTERVAL_MS);
                }
            }
        };

        private void pollVehicleState() {
            if (carClient == null || !carClient.isConnected()) return;

            try {
                // 1. 挡位检测 (CURRENT_GEAR)
                int rawGear = carClient.getIntProperty(AndroidCarClient.CURRENT_GEAR, 0, 0);
                if (rawGear > 0 && rawGear != lastRawGear) {
                    lastRawGear = rawGear;
                    int halGear = convertToHalGear(rawGear);
                    int internalGear = convertToInternalGear(rawGear);
                    lastHalGear = halGear;

                    AppLogger.i("车身联动", "Car API 挡位跃变: raw=" + rawGear + " → hal=" + halGear + " (internal=" + internalGear + ")");

                    // 通知标准 HAL 监听器
                    if (listener != null) {
                        listener.onGearChanged(halGear);
                    }

                    // 广播给 VehicleAutomationService 统一处理播报与状态跃变
                    VehicleAutomationService service = VehicleAutomationService.getInstance();
                    if (service != null && internalGear > 0) {
                        service.handleGearSignal(internalGear);
                    }
                }

                // 2. 点火与供电模式 (IGNITION_STATE)
                int ignition = carClient.getIntProperty(AndroidCarClient.IGNITION_STATE, 0, -1);
                if (ignition >= 0) {
                    VehicleAutomationService service = VehicleAutomationService.getInstance();
                    if (service != null) {
                        int powerMode = 2; // ON
                        if (ignition < 2) {
                            powerMode = (ignition == 1) ? 1 : 0; // 0=OFF, 1=ACC
                        }
                        service.lastPowerMode = powerMode;
                    }
                }

                // 3. 小电瓶实时电压 (BODY_BATTERY_U_BATT_INFO)
                int batteryRaw = carClient.getIntProperty(AndroidCarClient.BATTERY_VOLTAGE, 0, 0);
                if (batteryRaw > 0) {
                    VehicleAutomationService.latestBatteryVoltage = batteryRaw / 10.0f;
                }

                // 4. 四门状态采集 (DOOR_POS)
                int doorFl = carClient.getIntProperty(AndroidCarClient.DOOR_POS, AndroidCarClient.AREA_DOOR_ROW_1_LEFT, -1);
                int doorFr = carClient.getIntProperty(AndroidCarClient.DOOR_POS, AndroidCarClient.AREA_DOOR_ROW_1_RIGHT, -1);
                int doorRl = carClient.getIntProperty(AndroidCarClient.DOOR_POS, AndroidCarClient.AREA_DOOR_ROW_2_LEFT, -1);
                int doorRr = carClient.getIntProperty(AndroidCarClient.DOOR_POS, AndroidCarClient.AREA_DOOR_ROW_2_RIGHT, -1);

                VehicleAutomationService service = VehicleAutomationService.getInstance();
                if (service != null && service.getDoorStateManager() != null &&
                        (doorFl >= 0 || doorFr >= 0 || doorRl >= 0 || doorRr >= 0)) {
                    service.getDoorStateManager().updateDoors(
                            doorFl, doorFr, doorRl, doorRr,
                            VehicleAutomationService.voiceMasterSwitch,
                            context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE)
                    );
                }

                // 5. 后备箱尾门采集 (BODY_DOOR_TRUNK_DOOR_POS)
                int trunk = carClient.getIntProperty(AndroidCarClient.TRUNK_POS, 0, -1);
                if (trunk >= 0 && trunk != lastTrunkState) {
                    lastTrunkState = trunk;
                    boolean isOpen = (trunk > 0);
                    AppLogger.i("车身联动", "后备箱尾门状态变更: " + (isOpen ? "开启" : "闭合"));
                    if (listener != null) {
                        listener.onTrunkChanged(isOpen);
                    }
                    if (service != null && service.getTrunkStateManager() != null) {
                        service.getTrunkStateManager().updateTrunk(
                                trunk,
                                VehicleAutomationService.voiceMasterSwitch,
                                context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE)
                        );
                    }
                }

                // 6. 车速采集 (PERF_VEHICLE_SPEED)
                float speed = carClient.getFloatProperty(AndroidCarClient.PERF_VEHICLE_SPEED, 0, 0.0f);
                if (Math.abs(speed - lastSpeed) > 0.2f) {
                    lastSpeed = speed;
                    if (listener != null) {
                        listener.onSpeedChanged(speed);
                    }
                }

            } catch (Throwable t) {
                Log.w(TAG, "pollVehicleState exception: " + t.getMessage());
            }
        }

        @Override
        public void stopListening() {
            this.isRunning = false;
            if (pollHandler != null) {
                pollHandler.removeCallbacks(pollRunnable);
                pollHandler = null;
            }
            this.listener = null;
            AppLogger.i("车身联动", "BinyueLSensorAdapter 已停止监听");
        }

        @Override
        public int getCurrentGear() {
            if (carClient == null || !carClient.isConnected()) return 0;
            return convertToHalGear(carClient.getIntProperty(AndroidCarClient.CURRENT_GEAR, 0, 0));
        }

        @Override
        public float getCurrentSpeed() {
            if (carClient == null || !carClient.isConnected()) return 0.0f;
            return carClient.getFloatProperty(AndroidCarClient.PERF_VEHICLE_SPEED, 0, 0.0f);
        }
    }
}
