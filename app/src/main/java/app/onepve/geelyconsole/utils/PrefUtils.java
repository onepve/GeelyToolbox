package app.onepve.geelyconsole.utils;

import android.content.SharedPreferences;

/**
 * SharedPreferences 安全类型容错与脏数据自愈工具类 (PrefUtils)
 * 解决车载系统 WebView JSBridge、备份还原与多版本迁移时，布尔值被错误存为 String 或 Int 导致 ClassCastException 的顽疾。
 */
public class PrefUtils {

    /**
     * 安全读取布尔值：
     * 1. 优先标准读取 getBoolean
     * 2. 若捕获 ClassCastException（如存储为 "true"/"false" 或 1/0），智能解析并自动将底层持久化数据自愈纠正为标准 Boolean
     */
    public static boolean getBoolean(SharedPreferences prefs, String key, boolean defValue) {
        if (prefs == null || key == null) return defValue;
        try {
            return prefs.getBoolean(key, defValue);
        } catch (ClassCastException e) {
            // 类型冲突自愈逻辑
            try {
                String strVal = prefs.getString(key, null);
                if (strVal != null) {
                    boolean val = "true".equalsIgnoreCase(strVal) || "1".equals(strVal);
                    // 自动纠正回标准 Boolean 类型
                    prefs.edit().remove(key).putBoolean(key, val).apply();
                    return val;
                }
            } catch (Exception ignored) {}

            try {
                int intVal = prefs.getInt(key, -1);
                if (intVal != -1) {
                    boolean val = (intVal == 1);
                    prefs.edit().remove(key).putBoolean(key, val).apply();
                    return val;
                }
            } catch (Exception ignored) {}

            return defValue;
        } catch (Exception e) {
            return defValue;
        }
    }

    /**
     * 批量自愈车身与车门相关关键布尔配置项
     * 消除旧版本留存或 JS 误存入 String 导致的 ClassCastException
     */
    public static void sanitizeDoorPreferences(SharedPreferences prefs) {
        if (prefs == null) return;
        String[] boolKeys = {
            "voice_door_mode_universal", "voice_enable_door_universal_open", "voice_enable_door_universal_close",
            "voice_enable_door_fl", "voice_enable_door_fl_close",
            "voice_enable_door_fr", "voice_enable_door_fr_close",
            "voice_enable_door_rl", "voice_enable_door_rl_close",
            "voice_enable_door_rr", "voice_enable_door_rr_close",
            "voice_enable_door_rear",
            "enable_door_fl", "enable_door_fl_close",
            "enable_door_fr", "enable_door_fr_close",
            "enable_door_rl", "enable_door_rl_close",
            "enable_door_rr", "enable_door_rr_close",
            "voice_enable_trunk_open", "voice_enable_trunk_close",
            "enable_trunk_open", "enable_trunk_close"
        };
        for (String k : boolKeys) {
            getBoolean(prefs, k, true);
        }
    }
}
