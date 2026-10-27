package app.onepve.geelyconsole.hal;

import android.content.Context;

/**
 * 车载硬件抽象层平台契约（Vehicle HAL Platform Contract）
 * 供缤越 COOL、银河8155、通用 AOSP 车机实现插拔式适配
 */
public interface IVehiclePlatform {
    /** 平台唯一标识代号，如 "binyue_cool_e02", "galaxy_8155", "generic_aosp" */
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
}
