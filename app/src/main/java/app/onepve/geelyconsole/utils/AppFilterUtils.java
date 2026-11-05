package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * 车载应用分类、安全过滤与导航包识别通用工具模块
 */
public final class AppFilterUtils {

    private AppFilterUtils() {}

    /**
     * 判断当前设备是否为吉利/亿咖通原生车机环境
     */
    public static boolean isCarDevice(Context context) {
        String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
        String brand = Build.BRAND != null ? Build.BRAND.toUpperCase() : "";
        String finger = Build.FINGERPRINT != null ? Build.FINGERPRINT.toUpperCase() : "";
        if (model.contains("IHU") || model.contains("E02") || model.contains("GEELY") || model.contains("ECARX")
                || brand.contains("GEELY") || brand.contains("ECARX") || finger.contains("GEELY") || finger.contains("ECARX")) {
            return true;
        }
        if (context != null) {
            PackageManager pm = context.getPackageManager();
            try {
                if (pm.getPackageInfo("com.ecarx.carservice", 0) != null) return true;
            } catch (Exception ignored) {}
            try {
                if (pm.getPackageInfo("com.ecarx.launcher", 0) != null) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * 判断指定包名或文件名是否为车载导航类应用
     */
    public static boolean isNavigationApp(String pkg, String filename) {
        if (pkg != null) {
            String p = pkg.toLowerCase().trim();
            if (p.contains("autonavi") || p.contains("amap") || p.contains("baidu.nav")
                    || p.contains("baidu.map") || p.contains("cauto") || p.contains("tencent.map")
                    || p.contains("tencent.nav") || p.contains("petalmaps")) {
                return true;
            }
        }
        if (filename != null) {
            String fn = filename.toLowerCase().trim();
            if (fn.contains("amap") || fn.contains("gaode") || fn.contains("高德")
                    || fn.contains("baidu") || fn.contains("百度") || fn.contains("map")
                    || fn.contains("地图") || fn.contains("navi") || fn.contains("tencent") || fn.contains("腾讯")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 严禁冻结/卸载的系统级核心生命线包名
     */
    public static boolean isProtectedCriticalPackage(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase().trim();
        return p.equals("android")
                || p.equals("com.android.settings")
                || p.equals("com.android.systemui")
                || p.equals("com.android.packageinstaller")
                || p.equals("com.android.permissioncontroller")
                || p.equals("com.android.server.telecom")
                || p.equals("com.android.providers.settings")
                || p.equals("com.android.keyguard")
                || p.equals("com.android.shell")
                || p.equals("app.onepve.geelyconsole")
                || p.contains("inputmethod")
                || p.equals("com.ecarx.carservice")
                || p.equals("com.ecarx.launcher")
                || p.equals("com.ecarx.systemui")
                || p.equals("com.ecarx.service")
                || p.equals("com.ecarx.btphone")
                || p.equals("com.ecarx.carsetting")
                || p.equals("com.ecarx.hvac")
                || p.equals("com.ecarx.sound")
                || p.equals("com.ecarx.policy")
                || p.equals("com.ecarx.ipcamera")
                || p.equals("com.ecarx.dvr");
    }

    public static boolean isDangerousSystemPackage(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase().trim();
        return isProtectedCriticalPackage(p)
                || p.equals("com.android.settings")
                || p.equals("com.android.systemui")
                || (p.startsWith("com.ecarx.") && !p.contains("appstore") && !p.contains("xcmedia") && !p.contains("multimedia") && !p.contains("carmedia") && !p.contains("upgrade"));
    }

    /**
     * 获取系统中已安装的全部车载导航应用列表（JSON 格式）
     */
    public static String getInstalledNavigationApps(Context context) {
        JSONArray arr = new JSONArray();
        if (context == null) return arr.toString();
        try {
            PackageManager pm = context.getPackageManager();
            List<PackageInfo> installed = pm.getInstalledPackages(0);
            for (PackageInfo pi : installed) {
                if (pi.packageName == null) continue;
                String pkg = pi.packageName.toLowerCase();
                if (isNavigationApp(pkg, null) || pkg.equals("com.autonavi.amapauto")) {
                    JSONObject item = new JSONObject();
                    item.put("pkg", pi.packageName);
                    String label = pi.applicationInfo != null ? pm.getApplicationLabel(pi.applicationInfo).toString() : pi.packageName;
                    item.put("name", label);
                    item.put("version", pi.versionName != null ? pi.versionName : "");
                    boolean isSys = (pi.applicationInfo != null && (pi.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0);
                    item.put("isSystem", isSys);
                    arr.put(item);
                }
            }
        } catch (Exception ignored) {}
        return arr.toString();
    }
}
