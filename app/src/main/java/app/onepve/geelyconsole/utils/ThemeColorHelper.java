package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/**
 * 原生侧主题色桥接工具
 *
 * 严格对接助手完整主题体系 (web/src/theme/palette.js)，
 * 供原生组件（桌面小组件、悬浮窗等）动态读取当前车主选中的主题色。
 * 主题变更时由前端 setPalette() / setMode() 通过 ToolboxBridge.syncTheme 同步。
 */
public class ThemeColorHelper {
    private static final String PREFS = "toolbox_settings";
    private static final String KEY_ACCENT = "theme_accent_color";

    /** 出厂默认主题：晴空蓝 (sky_ocean) 夜间档 accent-gold = #58c4ff */
    public static final int DEFAULT_ACCENT = 0xFF58C4FF;

    /** 获取当前主题的强调色 (accent-gold)，供原生控件动态着色 */
    public static int getAccentColor(Context context) {
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String hex = sp.getString(KEY_ACCENT, null);
            if (hex != null && hex.startsWith("#")) {
                return Color.parseColor(hex);
            }
        } catch (Throwable ignored) {}
        return DEFAULT_ACCENT;
    }

    /** 由前端主题切换时同步写入当前强调色 hex（含 # 前缀） */
    public static void setAccentColor(Context context, String hex) {
        try {
            if (hex == null || !hex.startsWith("#")) return;
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_ACCENT, hex).apply();
        } catch (Throwable ignored) {}
    }
}
