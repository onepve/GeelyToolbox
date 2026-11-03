package app.onepve.geelyconsole.hal.baselines;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.onepve.geelyconsole.hal.IVehiclePlatform;
import app.onepve.geelyconsole.hal.platform.BinyueCoolPlatform;

/**
 * 【绝对黄金基准】吉利缤越 COOL (2022 / E02 / IHU516G / SX11-A3) 实车基线测试桩
 *
 * 刚性契约防线：
 * 1. 换挡报文匹配：VehId=Vehicle_Gear value=0x0X 正则解析
 * 2. 状态机迁移：P(5) -> R(4) -> N(3) -> D(2)
 * 3. 红绿灯启停防御：行车等红灯临时熄火（发动机转速为0），坚决不跳回 P 挡爆音
 * 4. MCU 串口车门：91 02 01 物理串口四门及尾门字节解析
 * 5. 标称油箱容积：45.0L
 * 6. 车机音频解码策略：HARD_FIRST_WITH_SOFT_FALLBACK
 */
public class BinyueCool2022BaselineTest {

    private static final Pattern P_VEHICLE_GEAR =
            Pattern.compile("VehId=Vehicle_Gear\\s+value=(?:0x)?([0-9a-fA-F]+)");
    private static final Pattern SERIAL_DOOR_PATTERN =
            Pattern.compile("91\\s+02\\s+01(?:\\s+[0-9a-fA-F]{1,2}){3}\\s+([0-9a-fA-F]{1,2})\\s+([0-9a-fA-F]{1,2})");

    static class FakeCoolGearStateMachine {
        int currentGear = 5; // 出厂默认 P 挡 (5)
        final List<String> announcements = new ArrayList<>();

        void onGearSignal(int targetGear, boolean engineRunning, int speedKmH) {
            // 红绿灯自动启停防御：行车挡等红灯停稳（发动机因启停熄火、车速为0）时，严禁重置为 P 挡
            if (!engineRunning && speedKmH == 0 && (currentGear == 2 || currentGear == 3)) {
                return;
            }

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
        System.out.println("  >> [基线回归] 吉利缤越 COOL (2022) 核心门禁测试");

        // 1. 实车 logcat 挡位与红绿灯启停防爆音防御测试
        FakeCoolGearStateMachine stateMachine = new FakeCoolGearStateMachine();
        String[] coolLogStream = new String[] {
            "09-22 10:00:01.100 D/MCULog  : VehId=Vehicle_Gear value=0x05", // P挡
            "09-22 10:00:05.200 D/MCULog  : VehId=Vehicle_Gear value=0x04", // 挂入 R挡
            "09-22 10:00:10.300 D/MCULog  : VehId=Vehicle_Gear value=0x03", // 挂入 N挡
            "09-22 10:00:15.400 D/MCULog  : VehId=Vehicle_Gear value=0x02", // 挂入 D挡
        };

        for (String line : coolLogStream) {
            Matcher m = P_VEHICLE_GEAR.matcher(line);
            if (m.find()) {
                int gear = Integer.parseInt(m.group(1), 16);
                stateMachine.onGearSignal(gear, true, 0);
            }
        }

        if (stateMachine.currentGear == 2) passes++; else failures++;
        if (stateMachine.announcements.contains("R")) passes++; else failures++;
        if (stateMachine.announcements.contains("N")) passes++; else failures++;
        if (stateMachine.announcements.contains("D")) passes++; else failures++;

        // 模拟红绿灯踩刹车启停场景
        stateMachine.announcements.clear();
        stateMachine.onGearSignal(5, false, 0);
        if (stateMachine.currentGear == 2) passes++; else failures++;
        if (stateMachine.announcements.isEmpty()) passes++; else failures++;

        // 2. MCU 物理串口车门报文流解析测试
        String doorOpenLine = "09-22 10:01:00.001 D/SerialControl: read data: 91 02 01 00 00 00 01 00";
        String doorCloseLine = "09-22 10:01:02.001 D/SerialControl: read data: 91 02 01 00 00 00 00 00";

        Matcher mOpen = SERIAL_DOOR_PATTERN.matcher(doorOpenLine);
        if (mOpen.find() && (Integer.parseInt(mOpen.group(1), 16) & 0x01) != 0) passes++; else failures++;

        Matcher mClose = SERIAL_DOOR_PATTERN.matcher(doorCloseLine);
        if (mClose.find() && (Integer.parseInt(mClose.group(1), 16) & 0x01) == 0) passes++; else failures++;

        // 3. 平台规范契约断言
        BinyueCoolPlatform platform = new BinyueCoolPlatform();
        if (BinyueCoolPlatform.PLATFORM_CODE.equals(platform.getPlatformCode())) passes++; else failures++;
        if (platform.getDefaultFuelCapacityLiters() == 45.0) passes++; else failures++;
        if (platform.getAudioDecodeStrategy() == IVehiclePlatform.AudioDecodeStrategy.HARD_FIRST_WITH_SOFT_FALLBACK) passes++; else failures++;

        System.out.println("     缤越 COOL 基准验证结果: " + passes + " 项通过, " + failures + " 项失败");
        return failures;
    }
}
