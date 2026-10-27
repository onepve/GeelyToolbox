package app.onepve.geelyconsole.hal.baselines;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.onepve.geelyconsole.hal.IVehiclePlatform;
import app.onepve.geelyconsole.hal.platform.BinruiCoolPlatform;

/**
 * 吉利缤瑞 COOL (2022 / E02 / IHU510G / FE6A3) 实车基准测试桩
 *
 * 核心契约：
 * 1. 换挡报文匹配：INFO_ID_VDRIVEINFO_GEAR_POSITION return funValue(0x002002X0)
 * 2. 状态机迁移：P(0x10->5) -> R(0x40->4) -> N(0x30->3) -> D(0x20->2)
 * 3. 标称油箱容积：50.0L
 * 4. 车机音频解码策略：HARD_FIRST_WITH_SOFT_FALLBACK
 * 5. 车机平台标识：binrui_cool_e02
 */
public class BinruiCoolBaselineTest {

    private static final Pattern P_FUNVALUE =
            Pattern.compile("funValue\\((?:0x)?([0-9a-fA-F]+)\\)");

    static class FakeBinruiGearStateMachine {
        int currentGear = 5; // P 挡
        final List<String> announcements = new ArrayList<>();

        void onGearSignal(int targetGear) {
            if (targetGear != currentGear && targetGear > 0) {
                currentGear = targetGear;
                switch (targetGear) {
                    case 2: announcements.add("D"); break;
                    case 3: announcements.add("N"); break;
                    case 4: announcements.add("R"); break;
                    case 5: announcements.add("P"); break;
                }
            }
        }
    }

    public static int runBaselineChecks() {
        int passes = 0;
        int failures = 0;
        System.out.println("  >> [基线回归] 吉利缤瑞 COOL (E02 / IHU510G / FE6A3) 核心门禁测试");

        // 1. 实车 Logcat 抓包换挡回放测试 (P -> R -> N -> D)
        FakeBinruiGearStateMachine stateMachine = new FakeBinruiGearStateMachine();
        String[] realBinruiLogStream = new String[] {
            "10-27 16:38:00.000 I/SensorModule( 5486): getFunctionValue:funId(0x00200200) zone(0x00000000) propId(INFO_ID_VDRIVEINFO_GEAR_POSITION) return funValue(0x00200210)", // P
            "10-27 16:38:05.000 I/SensorModule( 5486): getFunctionValue:funId(0x00200200) zone(0x00000000) propId(INFO_ID_VDRIVEINFO_GEAR_POSITION) return funValue(0x00200240)", // R
            "10-27 16:38:10.000 I/SensorModule( 5486): getFunctionValue:funId(0x00200200) zone(0x00000000) propId(INFO_ID_VDRIVEINFO_GEAR_POSITION) return funValue(0x00200230)", // N (实车日志 46346890 真实报文)
            "10-27 16:38:15.000 I/SensorModule( 5486): getFunctionValue:funId(0x00200200) zone(0x00000000) propId(INFO_ID_VDRIVEINFO_GEAR_POSITION) return funValue(0x00200220)", // D
        };

        for (String line : realBinruiLogStream) {
            if (line.contains("INFO_ID_VDRIVEINFO_GEAR_POSITION")) {
                Matcher m = P_FUNVALUE.matcher(line);
                if (m.find()) {
                    long rawVal = Long.parseLong(m.group(1), 16);
                    int low = (int) (rawVal & 0xFF);
                    int gearVal = -1;
                    if (low == 0x10) gearVal = 5;      // P
                    else if (low == 0x40) gearVal = 4; // R
                    else if (low == 0x30) gearVal = 3; // N
                    else if (low == 0x20) gearVal = 2; // D
                    if (gearVal > 0) {
                        stateMachine.onGearSignal(gearVal);
                    }
                }
            }
        }

        if (stateMachine.currentGear == 2) passes++; else failures++;
        if (stateMachine.announcements.contains("R")) passes++; else failures++;
        if (stateMachine.announcements.contains("N")) passes++; else failures++;
        if (stateMachine.announcements.contains("D")) passes++; else failures++;

        // 2. 平台规范契约断言
        BinruiCoolPlatform platform = new BinruiCoolPlatform();
        if (BinruiCoolPlatform.PLATFORM_CODE.equals(platform.getPlatformCode())) passes++; else failures++;
        if (platform.getDefaultFuelCapacityLiters() == 50.0) passes++; else failures++;
        if (platform.getAudioDecodeStrategy() == IVehiclePlatform.AudioDecodeStrategy.HARD_FIRST_WITH_SOFT_FALLBACK) passes++; else failures++;

        System.out.println("     缤瑞 COOL 基准验证结果: " + passes + " 项通过, " + failures + " 项失败");
        return failures;
    }
}
