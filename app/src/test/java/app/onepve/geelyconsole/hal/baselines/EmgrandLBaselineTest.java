package app.onepve.geelyconsole.hal.baselines;

import app.onepve.geelyconsole.hal.IVehiclePlatform;
import app.onepve.geelyconsole.hal.platform.EmgrandLPlatform;

/**
 * 吉利帝豪 L (E02 / IHU521G / SS13) 实车基准测试桩
 *
 * 核心契约：
 * 1. 标称油箱容积：52.0L
 * 2. 音频解码策略：DIRECT_SOFT_ONLY（帝豪L定制ROM裁除了audio/mpeg软解组件，必须从第1毫秒起走JLayer纯软解）
 * 3. 车机平台标识：emgrand_l_e02
 */
public class EmgrandLBaselineTest {

    public static int runBaselineChecks() {
        int passes = 0;
        int failures = 0;
        System.out.println("  >> [基线回归] 吉利帝豪 L (E02/IHU521G) 核心门禁测试");

        EmgrandLPlatform platform = new EmgrandLPlatform();
        if (EmgrandLPlatform.PLATFORM_CODE.equals(platform.getPlatformCode())) passes++; else failures++;
        if (platform.getDefaultFuelCapacityLiters() == 52.0) passes++; else failures++;
        
        // 关键断言：帝豪L必须使用纯软解策略
        if (platform.getAudioDecodeStrategy() == IVehiclePlatform.AudioDecodeStrategy.DIRECT_SOFT_ONLY) {
            passes++;
        } else {
            System.err.println("     [FAIL] 帝豪 L 音频解码策略必须为 DIRECT_SOFT_ONLY，以防系统无声！");
            failures++;
        }

        System.out.println("     帝豪 L 基准验证结果: " + passes + " 项通过, " + failures + " 项失败");
        return failures;
    }
}
