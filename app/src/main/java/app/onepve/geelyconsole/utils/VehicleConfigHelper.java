package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONObject;

import java.util.Map;

/**
 * 车身联动、语音播报与方控按键配置的统一读写与后台广播同步中枢
 */
public final class VehicleConfigHelper {

    public static final String PREF_NAME = "toolbox_settings";
    public static final String ACTION_SETTINGS_CHANGED = "app.onepve.geelyconsole.SETTINGS_CHANGED";

    private VehicleConfigHelper() {}

    /**
     * 写入单一配置项并即时广播通知后台常驻服务生效
     */
    public static void putAndSync(Context context, String key, Object value) {
        if (context == null || key == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value != null) {
            editor.putString(key, value.toString());
        }
        editor.apply();

        Intent intent = new Intent(ACTION_SETTINGS_CHANGED);
        context.sendBroadcast(intent);
    }

    /**
     * 读取全量车身联动、语音播报与方控映射参数并打包为前端响应式 JSON
     */
    public static String getVehicleAutomationSettingsJson(Context context) {
        if (context == null) return "{}";
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        JSONObject obj = new JSONObject();
        try {
            // 核心三大物理独立总开关：语音总开关、方控接管总开关 (自启动由 deviceInfo.autostart 独立管控)
            obj.put("voice_master_switch", prefs.getBoolean("voice_master_switch", true));
            obj.put("wheel_master_switch", prefs.getBoolean("wheel_master_switch", true));

            // 360 联动
            obj.put("vehicle_gear_d_360_enabled", prefs.getBoolean("vehicle_gear_d_360_enabled", true));
            obj.put("vehicle_speed_custom_action_enabled", prefs.getBoolean("vehicle_speed_custom_action_enabled", false));
            obj.put("vehicle_speed_custom_action_threshold", prefs.getInt("vehicle_speed_custom_action_threshold", 40));
            obj.put("vehicle_speed_custom_action_target", prefs.getString("vehicle_speed_custom_action_target", "pkg:com.autonavi.amapauto"));

            // 播报音频输出通道 (music | nav | notification)
            obj.put("voice_audio_channel", prefs.getString("voice_audio_channel", "music"));

            // 四门通用与分门配置 (默认启用独立分门专属详细车门语音)
            obj.put("voice_door_mode_universal", prefs.getBoolean("voice_door_mode_universal", false));
            obj.put("voice_enable_door_universal_open", prefs.getBoolean("voice_enable_door_universal_open", true));
            obj.put("voice_enable_door_universal_close", prefs.getBoolean("voice_enable_door_universal_close", true));

            // 四门与尾门迎宾与关门 (默认全开)
            obj.put("voice_enable_door_fl", prefs.getBoolean("voice_enable_door_fl", true));
            obj.put("voice_enable_door_fl_close", prefs.getBoolean("voice_enable_door_fl_close", true));
            obj.put("voice_enable_door_fr", prefs.getBoolean("voice_enable_door_fr", true));
            obj.put("voice_enable_door_fr_close", prefs.getBoolean("voice_enable_door_fr_close", true));
            obj.put("voice_enable_door_rl", prefs.getBoolean("voice_enable_door_rl", true));
            obj.put("voice_enable_door_rl_close", prefs.getBoolean("voice_enable_door_rl_close", true));
            obj.put("voice_enable_door_rr", prefs.getBoolean("voice_enable_door_rr", true));
            obj.put("voice_enable_door_rr_close", prefs.getBoolean("voice_enable_door_rr_close", true));
            obj.put("voice_enable_door_rear", prefs.getBoolean("voice_enable_door_rear", true));
            obj.put("voice_enable_trunk_open", prefs.getBoolean("voice_enable_trunk_open", true));
            obj.put("voice_enable_trunk_close", prefs.getBoolean("voice_enable_trunk_close", true));

            // 4 大挡位播报 (D/R/P 默认开启，N 挡空挡默认关闭防频报)
            obj.put("voice_enable_gear_d", prefs.getBoolean("voice_enable_gear_d", true));
            obj.put("voice_enable_gear_r", prefs.getBoolean("voice_enable_gear_r", true));
            obj.put("voice_enable_gear_p", prefs.getBoolean("voice_enable_gear_p", true));
            obj.put("voice_enable_gear_n", prefs.getBoolean("voice_enable_gear_n", false));

            // 4 大功能模式播报 (智能/舒适/经济/运动 默认均开启)
            obj.put("voice_enable_mode_smart", prefs.getBoolean("voice_enable_mode_smart", true));
            obj.put("voice_enable_mode_comfort", prefs.getBoolean("voice_enable_mode_comfort", true));
            obj.put("voice_enable_mode_eco", prefs.getBoolean("voice_enable_mode_eco", true));
            obj.put("voice_enable_mode_sport", prefs.getBoolean("voice_enable_mode_sport", true));

            // 默认值：出厂统一默认控制台独立接管模式，短按 Mode 唤起 360
            boolean hasCarMedia = SystemUtils.isPackageInstalled(context, "com.ecarx.carmedia");
            String defaultWheelMode = "toolbox_alone";
            String defaultModeAction = "open_360";

            obj.put("wheel_control_mode", prefs.getString("wheel_control_mode", defaultWheelMode));
            obj.put("wheel_action_mute", prefs.getString("wheel_action_mute", "default"));
            obj.put("wheel_action_mode", prefs.getString("wheel_action_mode", defaultModeAction));
            obj.put("wheel_action_ok", prefs.getString("wheel_action_ok", "default"));
            obj.put("has_carmedia_installed", hasCarMedia);
            obj.put("wheel_push_playback_cluster", prefs.getBoolean("wheel_push_playback_cluster", false));
            obj.put("wheel_push_lyrics_cluster", prefs.getBoolean("wheel_push_lyrics_cluster", false));

            // 车速联动自启与行车安全 (若车机装有 QQ 音乐，默认优先以 QQ 音乐为默认主力并默认开启运行)
            boolean hasQQMusicInstalled = false;
            try {
                context.getPackageManager().getPackageInfo("com.tencent.qqmusiccar", 0);
                hasQQMusicInstalled = true;
            } catch (Exception ignored) {}

            boolean defaultSpeedAutoplayEnabled = hasQQMusicInstalled;
            String defaultSpeedAutoplayPkg = hasQQMusicInstalled ? "com.tencent.qqmusiccar" : "com.android.bluetooth";

            obj.put("vehicle_speed_autoplay_enabled", prefs.getBoolean("vehicle_speed_autoplay_enabled", defaultSpeedAutoplayEnabled));
            obj.put("vehicle_speed_autoplay_threshold", prefs.getInt("vehicle_speed_autoplay_threshold", 20));
            obj.put("vehicle_speed_autoplay_pkg", prefs.getString("vehicle_speed_autoplay_pkg", defaultSpeedAutoplayPkg));
            obj.put("vehicle_speed_autoplay_fullscreen", prefs.getBoolean("vehicle_speed_autoplay_fullscreen", false));
            obj.put("vehicle_door_pause_music_enabled", prefs.getBoolean("vehicle_door_pause_music_enabled", false));
            obj.put("vehicle_rear_door_alert_enabled", prefs.getBoolean("vehicle_rear_door_alert_enabled", false));
            obj.put("preferred_navi_pkg", prefs.getString("preferred_navi_pkg", "com.autonavi.amapauto"));

            // 方控多手势映射 (单击/双击/长按)
            String[] gestureKeys = {"ok", "mute", "mode", "next", "prev", "back", "call", "voice", "home", "custom"};
            String[] gestures = {"single", "double", "long"};
            for (String k : gestureKeys) {
                for (String g : gestures) {
                    String pKey = "wheel_action_" + k + "_" + g;
                    String def = "default";
                    if ("single".equals(g)) {
                        if ("mode".equals(k)) def = "open_360";
                        else if ("next".equals(k)) def = "next_track";
                        else if ("prev".equals(k)) def = "prev_track";
                    }
                    obj.put(pKey, prefs.getString(pKey, prefs.getString("wheel_action_" + k, def)));
                }
            }

            // 兼容历史老 Key 别名
            obj.put("voice_door_fl", prefs.getBoolean("voice_enable_door_fl", true));
            obj.put("voice_door_fl_close", prefs.getBoolean("voice_enable_door_fl_close", true));
            obj.put("voice_door_fr", prefs.getBoolean("voice_enable_door_fr", false));
            obj.put("voice_door_fr_close", prefs.getBoolean("voice_enable_door_fr_close", false));
            obj.put("voice_door_rl", prefs.getBoolean("voice_enable_door_rl", false));
            obj.put("voice_door_rl_close", prefs.getBoolean("voice_enable_door_rl_close", false));
            obj.put("voice_door_rr", prefs.getBoolean("voice_enable_door_rr", false));
            obj.put("voice_door_rr_close", prefs.getBoolean("voice_enable_door_rr_close", false));
            obj.put("voice_door_rear", prefs.getBoolean("voice_enable_door_rear", false));
            obj.put("voice_trunk_open", prefs.getBoolean("voice_enable_trunk_open", false));
            obj.put("voice_trunk_close", prefs.getBoolean("voice_enable_trunk_close", false));
            obj.put("voice_gear_d", prefs.getBoolean("voice_enable_gear_d", true));
            obj.put("voice_gear_r", prefs.getBoolean("voice_enable_gear_r", true));

            obj.put("custom_door_fl", !prefs.getString("custom_voice_door_fl.mp3", "").isEmpty());
            obj.put("custom_door_fl_close", !prefs.getString("custom_voice_door_fl_close.mp3", "").isEmpty());
            obj.put("custom_door_fr", !prefs.getString("custom_voice_door_fr.mp3", "").isEmpty());
            obj.put("custom_door_fr_close", !prefs.getString("custom_voice_door_fr_close.mp3", "").isEmpty());
            obj.put("custom_door_rl", !prefs.getString("custom_voice_door_rl.mp3", "").isEmpty());
            obj.put("custom_door_rl_close", !prefs.getString("custom_voice_door_rl_close.mp3", "").isEmpty());
            obj.put("custom_door_rr", !prefs.getString("custom_voice_door_rr.mp3", "").isEmpty());
            obj.put("custom_door_rr_close", !prefs.getString("custom_voice_door_rr_close.mp3", "").isEmpty());
            obj.put("custom_door_rear", !prefs.getString("custom_voice_door_rl.mp3", "").isEmpty());
            obj.put("custom_trunk_open", !prefs.getString("custom_voice_trunk_open.mp3", "").isEmpty());
            obj.put("custom_trunk_close", !prefs.getString("custom_voice_trunk_close.mp3", "").isEmpty());
            obj.put("custom_gear_d", !prefs.getString("custom_voice_gear_d.mp3", "").isEmpty());
            obj.put("custom_gear_r", !prefs.getString("custom_voice_gear_r.mp3", "").isEmpty());

            obj.put("wheel_long_press_ms", prefs.getInt("wheel_long_press_ms", 1500));

            // 每声效独立声道/增益动态导出（有覆盖才导出：channel_<key> / offset_<key>；未覆盖=前端默认 普通媒体/0）
            for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
                String k = e.getKey();
                if (k.startsWith("voice_item_channel_")) {
                    obj.put(k.substring("voice_item_".length()), e.getValue());
                } else if (k.startsWith("voice_item_offset_")) {
                    obj.put(k.substring("voice_item_".length()), e.getValue());
                }
            }
        } catch (Exception ignored) {}
        return obj.toString();
    }
}
