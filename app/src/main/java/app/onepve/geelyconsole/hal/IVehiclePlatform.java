package app.onepve.geelyconsole.hal;

import android.content.Context;

/**
 * 车载硬件抽象层平台契约（Vehicle HAL Platform Contract）
 * 供缤越 COOL、银河8155、通用 AOSP 车机实现插拔式适配
 */
public interface IVehiclePlatform {
    /** 平台唯一标识代号，如 "binyue_cool_e02", "fe6a3_ihu510g", "generic_aosp" */
    String getPlatformCode();

    /** 平台友好显示名称 */
    String getPlatformName();

    /** 获取当前车型的官方标称油箱容积 (Liters) */
    double getDefaultFuelCapacityLiters();

    /** 音频解码策略：用于区分健康车型（系统硬解优先）与裁剪ROM车型（必须纯软解防静音） */
    enum AudioDecodeStrategy {
        /** 正常车机：系统 MediaPlayer 硬解优先，软解为异常兜底 (缤越 COOL, 缤越 L 等) */
        HARD_FIRST_WITH_SOFT_FALLBACK,
        /** 裁剪 ROM：车机系统裁剪裁除了 audio/mpeg 软解组件，从第 1 毫秒起直走纯 Java JLayer 软解 (帝豪 L 等) */
        DIRECT_SOFT_ONLY,
        /** 纯硬解 */
        HARD_ONLY
    }

    /** 获取当前车型的车载语音音频解码策略 */
    AudioDecodeStrategy getAudioDecodeStrategy();

    /** 获取方控按键适配器 */
    IVehicleKeyAdapter getKeyAdapter(Context context);

    /** 获取车身传感器/状态适配器（挡位、车速、车门等） */
    IVehicleSensorAdapter getSensorAdapter(Context context);

    // ==================== 底盘感知驱动模式（独立文件物理隔离） ====================
    /**
     * 车型车身感知驱动模式：
     * true = 优先走原厂 VHAL 底盘属性感知驱动（如缤越 L IHU624G / Flyme Auto，免 logcat）；
     * false = 走 logcat 实时流监听（缤越 COOL 2022 基准及绝大多数 E02 车型）。
     */
    default boolean usesVhalSensorDriver() {
        return false;
    }

    // ==================== 车型专属报文关键字（独立文件物理隔离） ====================
    /**
     * 该车型需要额外挂载进 logcat 白名单的专属报文关键字（小写）。
     * 主服务只挂载缤越 COOL 2022 基准关键字；各车型的专属关键字由各自 Java 文件声明，
     * 避免某车型的轮询报文串入其它车型的解析路径。
     */
    default String[] getExclusiveLogcatKeywords() {
        return new String[0];
    }

    // ==================== 挡位解析隔离门禁（独立文件物理隔离） ====================
    /**
     * 车型专属挡位解析钩子。
     *
     * 拓扑铁律：主服务（VehicleAutomationService）只保留缤越 COOL 2022 纯净基准；
     * 各车型需要修补的挡位解析逻辑，物理下沉到各自独立的 Java 文件（Fe6a3Ihu510gPlatform 等），
     * 主服务 parseLogLine 只负责识别 + 委派调用，绝不在主文件内联特定车型的 if 补丁。
     *
     * @param line          原始 logcat 行
     * @param canGear       主服务基准逻辑（CAN VehId=Vehicle_Gear）已解析出的挡位
     *                      （2=D 3=N 4=R 5=P），未命中 CAN 报文则传 -1
     * @param engineRunning 当前发动机运行状态（供车型自行做通电门控）
     * @return >=0 表示该车型完全接管并给出最终挡位；
     *         -1 表示本车型未接管，主服务继续走缤越 COOL 2022 基准逻辑（基准=CAN only，不解析 Sensor）
     */
    default int resolveGear(String line, int canGear, boolean engineRunning) {
        return -1;
    }
}
