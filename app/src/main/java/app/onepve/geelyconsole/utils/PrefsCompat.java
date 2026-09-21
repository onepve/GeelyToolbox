package app.onepve.geelyconsole.utils;

import android.content.SharedPreferences;

/**
 * SharedPreferences 类型安全读取工具。
 *
 * 背景 (真实缺陷): 历史版本的「上车预设驾驶模式」目标键
 * vehicle_preset_drive_mode_target 曾被按 boolean 写入 (旧接口只支持布尔)，
 * 而现行代码统一按字符串读取。Android 的 SharedPreferences 对同名键
 * 直接 getString 会抛 ClassCastException —— 一旦车主设备上残留过旧类型的
 * 值，整条座舱自动化链路都会在读取瞬间崩溃。
 *
 * 铁律: 只接受 String 类型；任何非 String 残留 (boolean/int/long/Set) 一律
 * 视为「数据不可信」，静默回落默认值 (保持原厂)，绝不抛出、绝不猜测模式。
 */
public final class PrefsCompat {

    private PrefsCompat() {}

    /**
     * 纯 Java 类型收敛核心 (零 Android 依赖，可直跑 JVM 回归测试)。
     * 仅当存量值确为 String 时返回它，任何其他类型一律回落 def。
     */
    public static String coerce(Object value, String def) {
        return (value instanceof String) ? (String) value : def;
    }

    /**
     * 类型安全的字符串读取。
     *
     * @param prefs 目标 SharedPreferences
     * @param key   键名
     * @param def   键不存在或类型不符时的安全默认值
     * @return 仅当存量值确为 String 时返回它，否则返回 def
     */
    public static String getString(SharedPreferences prefs, String key, String def) {
        if (prefs == null || key == null) return def;
        try {
            return coerce(prefs.getAll().get(key), def);
        } catch (Exception e) {
            return def;
        }
    }
}
