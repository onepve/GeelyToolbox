package app.onepve.geelyconsole.hal.baselines;

import app.onepve.geelyconsole.hal.IVehiclePlatform;
import app.onepve.geelyconsole.hal.platform.BinyueLPlatform;

/**
 * 吉利缤越 L (IHU624G / Flyme Auto / AAOS) 实车基准测试桩
 *
 * 核心契约：
 * 1. 标称油箱容积：51.0L
 * 2. 架构路线：AAOS VHAL 属性直通驱动
 * 3. 音频解码策略：HARD_FIRST_WITH_SOFT_FALLBACK
 */
public class BinyueLBaselineTest {

    public static int runBaselineChecks() {
        int passes = 0;
        int failures = 0;
        System.out.println("  >> [基线回归] 吉利缤越 L (Flyme Auto) 核心门禁测试");

        BinyueLPlatform platform = new BinyueLPlatform();
        if (BinyueLPlatform.PLATFORM_CODE.equals(platform.getPlatformCode())) passes++; else failures++;
        if (platform.getDefaultFuelCapacityLiters() == 51.0) passes++; else failures++;
        if (platform.getAudioDecodeStrategy() == IVehiclePlatform.AudioDecodeStrategy.HARD_FIRST_WITH_SOFT_FALLBACK) passes++; else failures++;

        System.out.println("     缤越 L 基准验证结果: " + passes + " 项通过, " + failures + " 项失败");
        return failures;
    }
}
