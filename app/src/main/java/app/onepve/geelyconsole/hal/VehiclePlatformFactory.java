package app.onepve.geelyconsole.hal;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import app.onepve.geelyconsole.hal.platform.BinyueCoolPlatform;
import app.onepve.geelyconsole.hal.platform.GenericAospPlatform;

/**
 * 车载硬件平台工厂（自动识别或按需注入平台驱动）
 */
public class VehiclePlatformFactory {
    private static final String TAG = "VehiclePlatformFactory";
    private static volatile IVehiclePlatform sCurrentPlatform = null;

    /**
     * 自动探测或返回当前车机硬件平台实现
     */
    public static IVehiclePlatform getPlatform(Context context) {
        if (sCurrentPlatform == null) {
            synchronized (VehiclePlatformFactory.class) {
                if (sCurrentPlatform == null) {
                    sCurrentPlatform = detectPlatform(context);
                }
            }
        }
        return sCurrentPlatform;
    }

    /**
     * 允许单元测试或调试时显式指定平台实现
     */
    public static void setPlatformForTesting(IVehiclePlatform platform) {
        sCurrentPlatform = platform;
    }

    private static IVehiclePlatform detectPlatform(Context context) {
        try {
            String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
            String fingerprint = Build.FINGERPRINT != null ? Build.FINGERPRINT.toLowerCase() : "";
            String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase() : "";

            // 1. 缤越 COOL / 亿咖通 E02 / IHU516G 特征检测
            if (model.contains("SX11") || model.contains("IHU516") || fingerprint.contains("ecarx")
                    || manufacturer.contains("ecarx") || fingerprint.contains("geely")) {
                Log.i(TAG, "Detected Geely Binyue COOL (E02 / IHU516G) Platform");
                return new BinyueCoolPlatform();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Platform detection fallback due to: " + t.getMessage());
        }

        // 2. 默认兜底为通用 AOSP 车规平台
        Log.i(TAG, "Fallback to Generic AOSP Vehicle Platform");
        return new GenericAospPlatform();
    }
}
