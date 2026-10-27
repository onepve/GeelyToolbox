package app.onepve.geelyconsole.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

/**
 * 桌面应用车标动态管理器
 * 支持：
 * 1. "auto"：根据车型底层属性自适应（检测到缤越 COOL 即启用改款大嘴车标，其他车型保持通用科技车标）
 * 2. "default"：强制使用通用极简科技车标
 * 3. "cool"：强制使用缤越 COOL 专属改款车标
 */
public class AppIconManager {
    private static final String TAG = "AppIconManager";
    public static final String PREF_KEY_APP_ICON_STYLE = "app_icon_style"; // "auto", "default", "cool"

    private static final String ALIAS_DEFAULT = "app.onepve.geelyconsole.MainActivity";
    private static final String ALIAS_COOL = "app.onepve.geelyconsole.MainActivityCool";

    /**
     * 判断当前车辆是否为吉利缤越 COOL 改款车型 (专车代号: SX11-A3 / SX11A3 / IHU516G)
     * 注意区分：老款缤越代号通常为 SX11 (GKUI / IHU301G)，而缤越 COOL 改款代号明确为 SX11-A3 或带 COOL 标识。
     */
    public static boolean isBinyueCoolModel() {
        try {
            String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
            String finger = Build.FINGERPRINT != null ? Build.FINGERPRINT.toUpperCase() : "";
            String product = Build.PRODUCT != null ? Build.PRODUCT.toUpperCase() : "";
            String device = Build.DEVICE != null ? Build.DEVICE.toUpperCase() : "";

            // 1. 优先精准匹配缤越 COOL 专车代号 (SX11A3 / SX11-A3 / COOLRAY_COOL / COOL)
            if (model.contains("SX11A3") || model.contains("SX11-A3") || finger.contains("SX11A3") || finger.contains("SX11-A3")
                    || product.contains("SX11A3") || product.contains("SX11-A3") || device.contains("SX11A3") || device.contains("SX11-A3")) {
                return true;
            }
            if (model.contains("COOL") || finger.contains("COOL") || product.contains("COOL")) {
                return true;
            }
            // 2. 反射检测吉利专车工程属性 (ro.ecarx.project 或 ro.product.model)
            Class<?> sp = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method getMethod = sp.getMethod("get", String.class, String.class);
            String pCode = (String) getMethod.invoke(null, "ro.ecarx.project", "");
            if (pCode != null) {
                String up = pCode.toUpperCase();
                if (up.contains("SX11A3") || up.contains("SX11-A3") || (up.contains("SX11") && up.contains("516"))) {
                    return true;
                }
            }
            String vModel = (String) getMethod.invoke(null, "ro.product.model", "");
            if (vModel != null) {
                String uv = vModel.toUpperCase();
                if (uv.contains("SX11A3") || uv.contains("SX11-A3") || uv.contains("COOL")) {
                    return true;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "isBinyueCoolModel check error: " + t.getMessage());
        }
        return false;
    }

    /**
     * 应用当前车标策略 (开机初始化或设置变更时调用)
     */
    public static void applyAppIconStyle(Context context) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            String style = prefs.getString(PREF_KEY_APP_ICON_STYLE, "auto");

            boolean enableCool;
            if ("cool".equals(style)) {
                enableCool = true;
            } else if ("default".equals(style)) {
                enableCool = false;
            } else {
                // "auto" 模式：自动根据车型自适应
                enableCool = isBinyueCoolModel();
            }

            PackageManager pm = context.getPackageManager();
            ComponentName defaultComp = new ComponentName(context, ALIAS_DEFAULT);
            ComponentName coolComp = new ComponentName(context, ALIAS_COOL);

            int targetDefaultState = enableCool ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
            int targetCoolState = enableCool ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

            if (pm.getComponentEnabledSetting(defaultComp) != targetDefaultState) {
                pm.setComponentEnabledSetting(defaultComp, targetDefaultState, PackageManager.DONT_KILL_APP);
                Log.i(TAG, "Switch default icon component state to: " + targetDefaultState);
            }

            if (pm.getComponentEnabledSetting(coolComp) != targetCoolState) {
                pm.setComponentEnabledSetting(coolComp, targetCoolState, PackageManager.DONT_KILL_APP);
                Log.i(TAG, "Switch cool icon component state to: " + targetCoolState);
            }
        } catch (Throwable t) {
            Log.e(TAG, "applyAppIconStyle failed: " + t.getMessage());
        }
    }

    /**
     * 获取当前生效或配置的车标代码
     */
    public static String getAppIconStyle(Context context) {
        if (context == null) return "auto";
        SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
        return prefs.getString(PREF_KEY_APP_ICON_STYLE, "auto");
    }

    /**
     * 设置车标策略并立即生效
     */
    public static void setAppIconStyle(Context context, String style) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_KEY_APP_ICON_STYLE, style).apply();
        applyAppIconStyle(context);
    }
}
