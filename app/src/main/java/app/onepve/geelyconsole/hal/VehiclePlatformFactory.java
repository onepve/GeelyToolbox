package app.onepve.geelyconsole.hal;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;

import app.onepve.geelyconsole.hal.platform.BinruiCoolPlatform;
import app.onepve.geelyconsole.hal.platform.BinyueCoolPlatform;
import app.onepve.geelyconsole.hal.platform.BinyueLPlatform;
import app.onepve.geelyconsole.hal.platform.EmgrandLPlatform;
import app.onepve.geelyconsole.hal.platform.Fe6a3Ihu510gPlatform;
import app.onepve.geelyconsole.hal.platform.GenericAospPlatform;

/**
 * 车载硬件平台工厂（自动识别或按需注入平台驱动）
 *
 * <p>车型识别核心准则：双代号联合判定（车型代号 + 车机主机代号）。
 * 例如 FE6A3 + IHU510G 联合命中才确认缤瑞 COOL 专属修补平台；
 * SX11 + IHU516G 联合命中确认缤越 COOL 基准平台；
 * SS13 + IHU521G 联合命中确认帝豪 L 平台。</p>
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

    private static String getSystemProperty(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getMethod("get", String.class, String.class);
            return (String) get.invoke(null, key, "");
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static IVehiclePlatform detectPlatform(Context context) {
        try {
            String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
            String fingerprint = Build.FINGERPRINT != null ? Build.FINGERPRINT.toUpperCase() : "";
            String display = Build.DISPLAY != null ? Build.DISPLAY.toUpperCase() : "";
            String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toUpperCase() : "";

            String projectInfo = getSystemProperty("ro.boot.ecarx_projectinfo").toUpperCase();
            String productName = getSystemProperty("ro.product.name").toUpperCase();
            String productDevice = getSystemProperty("ro.product.device").toUpperCase();

            // 聚合所有硬件特征指纹，确保双代号（车型代号 + 车机代号）联合精确提取
            String hardwareFingerprint = (model + " " + fingerprint + " " + display + " "
                    + manufacturer + " " + projectInfo + " " + productName + " " + productDevice).trim();

            // 1. 缤越 L / IHU624G / 魅族 Flyme Auto 优先判定 (双代号: IHU624 + COOLRAY/BINYUE)
            if (AndroidCarClient.isBinyueLPlatform(context) || hardwareFingerprint.contains("IHU624")
                    || (hardwareFingerprint.contains("624") && (hardwareFingerprint.contains("COOLRAY") || hardwareFingerprint.contains("BINYUE")))) {
                Log.i(TAG, "Detected Geely Binyue L / Coolray L (IHU624G / Flyme Auto) Platform");
                return new BinyueLPlatform();
            }

            // 2. 帝豪 L / 亿咖通 E02 / IHU521G (SS13) 特征检测 (双代号: SS13 + IHU521)
            boolean isEmgrandModel = hardwareFingerprint.contains("SS13") || hardwareFingerprint.contains("EMGRAND");
            boolean isIhu521 = hardwareFingerprint.contains("IHU521") || hardwareFingerprint.contains("521");
            if (isEmgrandModel && isIhu521) {
                Log.i(TAG, "Detected Geely Emgrand L (E02 / IHU521G / SS13) Platform");
                return new EmgrandLPlatform();
            }

            // 3. 缤瑞 COOL (FE6A3 + IHU510G) 专属修补平台 (双代号联合精确命中: FE6A3 + IHU510G)
            boolean isBinruiModel = hardwareFingerprint.contains("FE6A") || hardwareFingerprint.contains("FE-6")
                    || hardwareFingerprint.contains("BINRUI");
            boolean isIhu510 = hardwareFingerprint.contains("IHU510") || hardwareFingerprint.contains("510");
            if (isBinruiModel && isIhu510) {
                Log.i(TAG, "Detected Geely Binrui COOL (FE6A3 + IHU510G) Exclusive Repair Platform");
                return new Fe6a3Ihu510gPlatform();
            }
            if (isBinruiModel) {
                Log.i(TAG, "Detected Geely Binrui COOL Baseline Platform");
                return new BinruiCoolPlatform();
            }

            // 4. 缤越 COOL / 亿咖通 E02 / IHU516G (SX11) 双代号检测 (SX11 + IHU516 或 E02/ECARX 基准)
            boolean isBinyueCoolModel = hardwareFingerprint.contains("SX11") || hardwareFingerprint.contains("COOLRAY");
            boolean isIhu516 = hardwareFingerprint.contains("IHU516") || hardwareFingerprint.contains("516");
            if ((isBinyueCoolModel && isIhu516) || hardwareFingerprint.contains("ECARX") || hardwareFingerprint.contains("GEELY")) {
                Log.i(TAG, "Detected Geely Binyue COOL (E02 / IHU516G) Platform");
                return new BinyueCoolPlatform();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Platform detection fallback due to: " + t.getMessage());
        }

        // 默认兜底为通用 AOSP 车规平台
        Log.i(TAG, "Fallback to Generic AOSP Vehicle Platform");
        return new GenericAospPlatform();
    }
}
