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

    /** 获取方控按键适配器 */
    IVehicleKeyAdapter getKeyAdapter(Context context);

    /** 获取车身传感器/状态适配器（挡位、车速、车门等） */
    IVehicleSensorAdapter getSensorAdapter(Context context);
}
