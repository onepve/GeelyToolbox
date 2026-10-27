package app.onepve.geelyconsole.hal;

import app.onepve.geelyconsole.hal.baselines.BinyueCool2022BaselineTest;
import app.onepve.geelyconsole.hal.baselines.BinyueLBaselineTest;
import app.onepve.geelyconsole.hal.baselines.EmgrandLBaselineTest;

/**
 * CI 车型矩阵总门禁验证入口
 * 每次执行 CI 健康自检时，全量回归各车型的独立实车基线
 */
public class VehiclePlatformMatrixTest {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  [HAL CI Matrix] 正在执行全车型独立基线矩阵门禁验证");
        System.out.println("==================================================");

        int totalFailures = 0;

        // 1. 缤越 COOL 2022 核心黄金基线回归
        totalFailures += BinyueCool2022BaselineTest.runBaselineChecks();

        // 2. 帝豪 L 核心基线回归
        totalFailures += EmgrandLBaselineTest.runBaselineChecks();

        // 3. 缤越 L 核心基线回归
        totalFailures += BinyueLBaselineTest.runBaselineChecks();

        if (totalFailures > 0) {
            System.err.println("\n[CI GATE CRITICAL] 车型矩阵门禁失败，存在 " + totalFailures + " 个基准未通过！阻断交付！");
            System.exit(1);
        } else {
            System.out.println("\n[CI GATE PASSED] 全车型独立基准测试全部通过，缤越 COOL 核心无退化！");
        }
    }
}
