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
        /** 归一化后的四门状态，仅用于变化取证，避免同一读数反复刷诊断日志 */
        private int lastNormFl = -1, lastNormFr = -1, lastNormRl = -1, lastNormRr = -1;

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
                // 【v1.8.35 根因修复】本车（缤越 L / IHU624G）关门稳定态读数是 2，不是 0。
                // 车机自身 VehicleModules 适配层日志全程 374 帧均为 properValue=0 → adapterValue=2。
                // 旧逻辑只认「1=开、其余当关」，行车时又把四门强制写成 0；一踩刹车车速归零，
                // 2 != 0 立刻被当成「该门从 0 跳到 2」的假跃变，四门同轮齐触发，连播四句关门音。
                // 修法：在本车型层先把原始值归一化到状态机约定的 0/1 契约，行车基准与静止读数一致，
                // 从此车速归零不再产生任何假跃变。
                int rawFl = carClient.getIntProperty(AndroidCarClient.DOOR_POS, AndroidCarClient.AREA_DOOR_ROW_1_LEFT, -1);
                int rawFr = carClient.getIntProperty(AndroidCarClient.DOOR_POS, AndroidCarClient.AREA_DOOR_ROW_1_RIGHT, -1);
                int rawRl = carClient.getIntProperty(AndroidCarClient.DOOR_POS, AndroidCarClient.AREA_DOOR_ROW_2_LEFT, -1);
                int rawRr = carClient.getIntProperty(AndroidCarClient.DOOR_POS, AndroidCarClient.AREA_DOOR_ROW_2_RIGHT, -1);

                int doorFl = normalizeDoorValue(rawFl);
                int doorFr = normalizeDoorValue(rawFr);
                int doorRl = normalizeDoorValue(rawRl);
                int doorRr = normalizeDoorValue(rawRr);

                logDoorReadingOnce(rawFl, rawFr, rawRl, rawRr, doorFl, doorFr, doorRl, doorRr);

                VehicleAutomationService service = VehicleAutomationService.getInstance();
                if (service != null && service.getDoorStateManager() != null &&
                        (doorFl >= 0 || doorFr >= 0 || doorRl >= 0 || doorRr >= 0)) {
                    // 【v1.8.35 防误播铁律】同一轮里三扇及以上车门齐变，物理上不可能，
                    // 判定为一次数据异常：只更新本层基准，这一轮绝不交下游做播报。
                    if (isSimultaneousDoorJump(doorFl, doorFr, doorRl, doorRr)) {
                        rememberDoorReading(doorFl, doorFr, doorRl, doorRr);
                        AppLogger.diag("车身联动单门", "多门同轮齐变，判定数据异常，本轮仅重建基准不播报");
                    } else {
                        rememberDoorReading(doorFl, doorFr, doorRl, doorRr);
                        service.getDoorStateManager().updateDoors(
                                doorFl, doorFr, doorRl, doorRr,
                                VehicleAutomationService.voiceMasterSwitch,
                                context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE)
                        );
                    }
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

        /**
         * 【v1.8.35 根因修复】本车型 DOOR_POS 原始值归一化
         *
         * 实车取证（识别码 305151，缤越 L / IHU624G / Flyme Auto）：
         * 车机自身 VehicleModules 适配层在整个日志窗口内打印 374 帧，
         * 无一例外都是 properValue = 0 → adapterValue = 2，
         * 而这段时间车辆一直在正常行驶、四门全程紧闭。
         *
         * 结论：本车「关门」的稳定读数就是 2，车机把 AOSP 的 0 平移成了自己的档位码。
         * 旧代码只按「1=开、其余都当关」处理，看着像能用，实际埋了一个大坑：
         * 行车时 DoorStateManager 把四门强制记为 0，一踩刹车车速归零，
         * 真实读数 2 与基准 0 不等 → 被判成「该门刚发生一次跃变」→ 四门同轮齐报。
         *
         * 归一化契约（与 DoorStateManager 内部 0/1 语义严格对齐）：
         *   1        → 1 开
         *   0 或 2   → 0 关   （2 是本车关门稳定值）
         *   其余/负数 → -1 未知，调用方保持原状态，绝不参与跃变判定
         * 铁律：未知值一律不许硬塞成 0，否则又回到「拿未知当关门」的老坑。
         */
        private int normalizeDoorValue(int raw) {
            if (raw == 1) return 1;              // 开
            if (raw == 0 || raw == 2) return 0;  // 关（2 = 本车关门稳定读数）
            return -1;                            // 未知：不参与判定
        }

        /** 四门读数变化取证：只在读数发生实质变化时打一条，绝不刷屏 */
        private void logDoorReadingOnce(int rawFl, int rawFr, int rawRl, int rawRr,
                                        int fl, int fr, int rl, int rr) {
            if (fl == lastNormFl && fr == lastNormFr && rl == lastNormRl && rr == lastNormRr) return;
            AppLogger.diag("车身联动单门",
                    "四门读数: 原始[" + rawFl + "," + rawFr + "," + rawRl + "," + rawRr + "]"
                            + " → 归一化[" + fl + "," + fr + "," + rl + "," + rr + "]"
                            + "（1=开 0=关 -1=未知）");
            lastNormFl = fl;
            lastNormFr = fr;
            lastNormRl = rl;
            lastNormRr = rr;
        }

        /** 记录本轮读数，作为下一轮判定跃变的基准 */
        private void rememberDoorReading(int fl, int fr, int rl, int rr) {
            if (fl >= 0) lastNormFl = fl;
            if (fr >= 0) lastNormFr = fr;
            if (rl >= 0) lastNormRl = rl;
            if (rr >= 0) lastNormRr = rr;
        }

        /**
         * 多门同轮齐变识别：物理上四扇门不可能在同一次 500ms 轮询里一起开关。
         * 三扇及以上同时变化即判定为数据异常（通道抖动、适配层瞬时错值），
         * 这一轮只重建基准、不交下游播报，避免连环误播。
         */
        private boolean isSimultaneousDoorJump(int fl, int fr, int rl, int rr) {
            int jumped = 0;
            if (fl >= 0 && lastNormFl >= 0 && fl != lastNormFl) jumped++;
            if (fr >= 0 && lastNormFr >= 0 && fr != lastNormFr) jumped++;
            if (rl >= 0 && lastNormRl >= 0 && rl != lastNormRl) jumped++;
            if (rr >= 0 && lastNormRr >= 0 && rr != lastNormRr) jumped++;
            return jumped >= 3;
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
