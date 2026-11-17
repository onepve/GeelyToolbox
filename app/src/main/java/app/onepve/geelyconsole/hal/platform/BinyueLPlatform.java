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
    public double getDefaultFuelCapacityLiters() {
        return 51.0; // 吉利缤越 L 官方标称容积 51L
    }

    @Override
    public AudioDecodeStrategy getAudioDecodeStrategy() {
        return AudioDecodeStrategy.HARD_FIRST_WITH_SOFT_FALLBACK;
    }

    @Override
    public boolean usesVhalSensorDriver() {
        return true;
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
            // 原厂 MediaKeyReceiver 不可由第三方应用停用（本机实测：无系统权限必然失败、ADB 超时也不生效），
            // 该档位仅刷新热路径缓存，绝不触碰原厂组件。
            if (keyManager != null) {
                keyManager.refreshRuntimeCache();
            }
        }
    }

    /**
     * 缤越 L 专有车身信号与传感器适配实现 (通过 AndroidCarClient 驱动)
     */
    public static class BinyueLSensorAdapterImpl implements IVehicleSensorAdapter {
        private static final long POLL_INTERVAL_MS = 500;
        /** 自愈看门狗周期：1 秒。远小于 15 秒车门静默阈值，故障期顶栏不会空等太久 */
        private static final long POLL_WATCHDOG_INTERVAL_MS = 1000;

        private final Context context;
        private AndroidCarClient carClient;
        private OnVehicleSensorListener listener;
        private Handler pollHandler;
        private Handler pollWatchdog;
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

            // 幂等保护：万一被重复调用，先清掉上一轮的看门狗与轮询，绝不允许两套循环并存
            if (pollWatchdog != null) {
                pollWatchdog.removeCallbacks(pollWatchdogRunnable);
                pollWatchdog = null;
            }
            stopPolling();

            AppLogger.i("车身联动", "BinyueLSensorAdapter 正在连接 AAOS VHAL 服务...");
            this.carClient.connect(context, new AndroidCarClient.OnCarConnectListener() {
                @Override
                public void onCarConnected() {
                    AppLogger.i("车身联动", "AAOS VHAL 核心服务连接成功，启动车身状态感知循环");
                    startPolling("连接回调");
                }

                @Override
                public void onCarDisconnected() {
                    AppLogger.w("车身联动", "AAOS VHAL 服务已断开");
                    stopPolling();
                }
            });

            // ── 【VHAL 感知循环自愈 v1.8.34】──
            // 实车故障：缤越 L 顶栏电瓶恒显示「采集中...」，挡位/车速/车门一起失聪。
            // 根因不在本类：v1.8.32 起 VehicleAutomationService 在服务启动时无条件调用
            // activateWheelHalFallback()（方控 HAL 常开），而 AndroidCarClient 只保存单个
            // mListener —— 方控回调在缤越 L 之后注册，把本类回调覆盖掉了。CarService 异步
            // 连上时只回调最后一个注册者，本类 onCarConnected() 永不触发，500ms 轮询循环
            // 一次都没跑过。v1.8.31 全库只有本类一处 connect()，所以当时正常。
            // 修法：不依赖连接回调一次性点火，改为独立看门狗按「已连上但循环没在跑」自愈，
            // 与回调路径互为冗余（回调先到就走回调，回调被抢走则由本看门狗补启动）。
            // 铁律：绝不动 AndroidCarClient 共享底座（缤越 COOL 方控/车门 HAL 同源），
            // 也绝不动缤越 COOL 基准逻辑 —— 改动全部封闭在本车型文件内。
            this.pollWatchdog = new Handler(Looper.getMainLooper());
            this.pollWatchdog.postDelayed(pollWatchdogRunnable, POLL_WATCHDOG_INTERVAL_MS);
        }

        /** 启动车身状态轮询循环（幂等：已在跑则忽略，绝不叠加多份循环） */
        private void startPolling(String from) {
            if (pollHandler != null) return;
            pollHandler = new Handler(Looper.getMainLooper());
            pollHandler.post(pollRunnable);
            if (!"连接回调".equals(from)) {
                // 走到非回调路径说明连接回调被别的通道抢走了，必须留下可导出证据
                AppLogger.w("车身联动", "VHAL 感知循环自愈启动（触发来源=" + from + "，连接回调未送达本通道）");
            }
        }

        private void stopPolling() {
            if (pollHandler != null) {
                pollHandler.removeCallbacks(pollRunnable);
                pollHandler = null;
            }
        }

        /** 自愈看门狗：每秒判一次「服务已连上但感知循环没在跑」，补启动 */
        private final Runnable pollWatchdogRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                try {
                    if (carClient != null && carClient.isConnected() && pollHandler == null) {
                        startPolling("自愈看门狗");
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "poll watchdog exception: " + t.getMessage());
                }
                if (pollWatchdog != null) {
                    pollWatchdog.postDelayed(this, POLL_WATCHDOG_INTERVAL_MS);
                }
            }
        };

        private final Runnable pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                pollVehicleState();
                if (pollHandler != null && carClient != null && carClient.isConnected()) {
                    pollHandler.postDelayed(this, POLL_INTERVAL_MS);
                } else if (pollHandler != null) {
                    // 【v1.8.34 关键】连接掉线导致本轮不续跑时，必须把句柄置空，
                    // 否则看门狗看到 pollHandler != null 会误判「循环还在跑」而永不接管，
                    // 断线重连后就永久静默 —— 那等于把这次的 bug 换个形态留下来。
                    pollHandler.removeCallbacks(this);
                    pollHandler = null;
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
                    if (service != null) {
                        service.handleSpeedSignal(Math.round(speed));
                    }
                }

            } catch (Throwable t) {
                Log.w(TAG, "pollVehicleState exception: " + t.getMessage());
            }
        }

        @Override
        public void stopListening() {
            this.isRunning = false;
            // RAII 闭环：看门狗与轮询循环必须一起注销，绝不留孤儿 Handler 常驻空转
            if (pollWatchdog != null) {
                pollWatchdog.removeCallbacks(pollWatchdogRunnable);
                pollWatchdog = null;
            }
            stopPolling();
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
