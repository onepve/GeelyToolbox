package app.onepve.geelyconsole.utils;

import android.content.SharedPreferences;

/**
 * SharedPreferences 安全类型容错与脏数据自愈工具类 (PrefUtils)
 * 解决车载系统 WebView JSBridge、备份还原与多版本迁移时，布尔值被错误存为 String 或 Int 导致 ClassCastException 的顽疾。
 */
public class PrefUtils {
    /**
     * 获取全应用权威统一的配置存储 (SharedPreferences)
     * 100% 保持与正式版普通存储基线一致，彻底杜绝 DE 存储导致的前后端配置分裂
     */
    public static SharedPreferences getAppPreferences(android.content.Context context) {
        if (context == null) return null;
        try {
            // Android 7.0+ Direct Boot 校验：开机未解锁前严禁直调 CE 存储以防 IllegalStateException 崩溃
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.os.UserManager um = context.getSystemService(android.os.UserManager.class);
                if (um != null && !um.isUserUnlocked()) {
                    return context.createDeviceProtectedStorageContext().getSharedPreferences("toolbox_settings", android.content.Context.MODE_PRIVATE);
                }
            }
            return context.getSharedPreferences("toolbox_settings", android.content.Context.MODE_PRIVATE);
        } catch (IllegalStateException e) {
            // 兜底捕获：若在未解锁时强读抛出异常，降级安全使用 DE 存储
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    return context.createDeviceProtectedStorageContext().getSharedPreferences("toolbox_settings", android.content.Context.MODE_PRIVATE);
                }
            } catch (Throwable ignored) {}
            return null;
        } catch (Throwable t) {
            return null;
        }
    }


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
        // 1. 物理清理所有历史旧版残留的幽灵配置键 (如旧版衍生音效独立 offset/channel)
        cleanupStaleVoicePreferences(prefs);

        // 2. 批量纠偏布尔配置项类型冲突
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

    /**
     * 物理清理历史残留旧版键，消除幽灵增益与声道异常
     */
    public static void cleanupStaleVoicePreferences(SharedPreferences prefs) {
        if (prefs == null) return;
        try {
            SharedPreferences.Editor editor = prefs.edit();
            String[] staleKeys = {
                "voice_item_offset_door_fr_enter",
                "voice_item_offset_door_fr_queen_enter",
                "voice_item_offset_door_fr_princess_enter",
                "voice_item_offset_door_fr_queen_close",
                "voice_item_offset_door_fr_princess_close",
                "voice_item_channel_door_fr_enter",
                "voice_item_channel_door_fr_queen_enter",
                "voice_item_channel_door_fr_princess_enter",
                "voice_item_channel_door_fr_queen_close",
                "voice_item_channel_door_fr_princess_close",
                "voice_item_offset_door_fl_enter",
                "voice_item_offset_door_fl_close_enter",
                "voice_item_channel_door_fl_enter"
            };
            boolean changed = false;
            for (String key : staleKeys) {
                if (prefs.contains(key)) {
                    editor.remove(key);
                    changed = true;
                }
            }
            if (changed) {
                editor.apply();
                AppLogger.i("座舱自动化", "【自愈防护】已成功物理清除历史残留旧版语音增益/声道键，恢复权威主项管控");
            }
        } catch (Exception ignored) {}
    }
}
