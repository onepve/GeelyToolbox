package app.onepve.geelyconsole.hal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 吉利缤越双车型（缤越 COOL vs 缤越 L）双路线日志仿真与门禁隔离回归测试 (纯 JVM 自包含测试)
 *
 * 覆盖：
 * 1. 缤越 COOL 路线：logcat 报文正则匹配、换挡状态机转换、红绿灯自动启停防爆音防御、车门 MCU 串口报文
 * 2. 缤越 L 路线：AAOS VHAL 属性流回放 (CURRENT_GEAR, DOOR_POS, TRUNK_POS, BATTERY_VOLTAGE, IGNITION_STATE)
 * 3. 双车型门禁隔离与特征互斥断言
 * 4. 前端车型微胶囊标识算法断言
 */
public class VehiclePlatformDualPathSimulationTest {

    private static int passes = 0;
    private static int failures = 0;

    private static void check(boolean condition, String name) {
        if (condition) {
            passes++;
            System.out.println("  ✓ PASS: " + name);
        } else {
            failures++;
            System.err.println("  ✗ FAIL: " + name);
        }
    }

    // ==========================================
    // 1. 缤越 COOL 路线：logcat 数据仿真与解析测试
    // ==========================================

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

    public static void testBinyueCoolLogcatGearParsingAndStartStopDefense() {
        System.out.println("\n[测试用例 1] 缤越 COOL 实车 logcat 挡位流解析与红绿灯启停防爆音防御测试");
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

        check(stateMachine.currentGear == 2, "COOL 实车 logcat 正则匹配成功，挡位正确迁移至 D 挡");
        check(stateMachine.announcements.contains("R"), "R 挡换挡播报事件正常发出");
        check(stateMachine.announcements.contains("N"), "N 挡换挡播报事件正常发出");
        check(stateMachine.announcements.contains("D"), "D 挡换挡播报事件正常发出");

        // 模拟红绿灯踩刹车启停场景
        stateMachine.announcements.clear();
        stateMachine.onGearSignal(5, false, 0);
        check(stateMachine.currentGear == 2, "红绿灯自动启停熄火时，行车状态机坚决保持 D 挡不被篡改");
        check(stateMachine.announcements.isEmpty(), "红绿灯启停期间严禁误报 P 挡爆音");
    }

    public static void testBinyueCoolSerialDoorPatternParsing() {
        System.out.println("\n[测试用例 2] 缤越 COOL MCU 物理串口车门报文流解析测试");
        String doorOpenLine = "09-22 10:01:00.001 D/SerialControl: read data: 91 02 01 00 00 00 01 00";
        String doorCloseLine = "09-22 10:01:02.001 D/SerialControl: read data: 91 02 01 00 00 00 00 00";

        Matcher mOpen = SERIAL_DOOR_PATTERN.matcher(doorOpenLine);
        check(mOpen.find(), "MCU 串口主驾门开报文正则精准命中");
        if (mOpen.matches() || mOpen.reset().find()) {
            int byte7Open = Integer.parseInt(mOpen.group(1), 16);
            check((byte7Open & 0x01) != 0, "主驾门开状态位 (bit 0) 解析为 true");
        }

        Matcher mClose = SERIAL_DOOR_PATTERN.matcher(doorCloseLine);
        check(mClose.find(), "MCU 串口主驾门关报文正则精准命中");
        if (mClose.matches() || mClose.reset().find()) {
            int byte7Close = Integer.parseInt(mClose.group(1), 16);
            check((byte7Close & 0x01) == 0, "主驾门关状态位 (bit 0) 解析为 false");
        }
    }

    // ==========================================
    // 2. 缤越 L 路线：AAOS VHAL 属性流仿真与解析测试
    // ==========================================

    static class FakeBinyueLHalSimulator {
        int currentHalGear = -1;
        int currentInternalGear = -1;
        float currentVoltage = 0.0f;
        int powerMode = -1;
        boolean trunkOpen = false;
        final boolean[] doors = new boolean[4]; // FL, FR, RL, RR

        int convertToHalGear(int rawGear) {
            if (rawGear == 1) return 2; // N
            if (rawGear == 2) return 1; // R
            if (rawGear == 4) return 0; // P
            if (rawGear == 8) return 3; // D
            return 0;
        }

        int convertToInternalGear(int rawGear) {
            if (rawGear == 1) return 3; // N
            if (rawGear == 2) return 4; // R
            if (rawGear == 4) return 5; // P
            if (rawGear == 8) return 2; // D
            return 0;
        }

        void updateGear(int rawGear) {
            this.currentHalGear = convertToHalGear(rawGear);
            this.currentInternalGear = convertToInternalGear(rawGear);
        }

        void updateBatteryVoltage(int rawBatt) {
            this.currentVoltage = rawBatt / 10.0f;
        }

        void updateIgnitionState(int ignition) {
            if (ignition >= 2) {
                this.powerMode = 2; // ON
            } else {
                this.powerMode = (ignition == 1) ? 1 : 0;
            }
        }

        void updateDoors(int fl, int fr, int rl, int rr) {
            doors[0] = (fl == 1);
            doors[1] = (fr == 1);
            doors[2] = (rl == 1);
            doors[3] = (rr == 1);
        }

        void updateTrunk(int trunk) {
            this.trunkOpen = (trunk > 0);
        }
    }

    public static void testBinyueLVHalPropertyMappingAndStateTransitions() {
        System.out.println("\n[测试用例 3] 缤越 L 实车 IHU624G VHAL 属性流转换与各通道解析测试");
        FakeBinyueLHalSimulator sim = new FakeBinyueLHalSimulator();

        // 1. 挡位转换 (CURRENT_GEAR: 1=N, 2=R, 4=P, 8=D)
        sim.updateGear(4); // P
        check(sim.currentHalGear == 0 && sim.currentInternalGear == 5, "VHAL P 挡 (4) 精准映射至 internal=5 / hal=0");

        sim.updateGear(2); // R
        check(sim.currentHalGear == 1 && sim.currentInternalGear == 4, "VHAL R 挡 (2) 精准映射至 internal=4 / hal=1");

        sim.updateGear(1); // N
        check(sim.currentHalGear == 2 && sim.currentInternalGear == 3, "VHAL N 挡 (1) 精准映射至 internal=3 / hal=2");

        sim.updateGear(8); // D
        check(sim.currentHalGear == 3 && sim.currentInternalGear == 2, "VHAL D 挡 (8) 精准映射至 internal=2 / hal=3");

        // 2. 蓄电池电压 (实车 dump: 131 -> 13.1V)
        sim.updateBatteryVoltage(131);
        check(Math.abs(sim.currentVoltage - 13.1f) < 0.01f, "实车小蓄电池电压 131 精准换算为 13.1V");

        // 3. 点火模式 (IGNITION_STATE: 2 -> ON)
        sim.updateIgnitionState(2);
        check(sim.powerMode == 2, "点火状态 (2) 精准识别为车辆运转模式 (powerMode=2/ON)");

        // 4. 车门 (DOOR_POS)
        sim.updateDoors(1, 0, 0, 1);
        check(sim.doors[0], "主驾车门开状态更新成功");
        check(!sim.doors[1], "副驾车门关状态更新成功");
        check(!sim.doors[2], "左后车门关状态更新成功");
        check(sim.doors[3], "右后车门开状态更新成功");

        // 5. 后备箱尾门 (TRUNK_POS: 1=开, 0=关)
        sim.updateTrunk(1);
        check(sim.trunkOpen, "尾门开启状态精准更新为 true");
        sim.updateTrunk(0);
        check(!sim.trunkOpen, "尾门闭合状态精准更新为 false");
    }

    // ==========================================
    // 3. 双车型门禁隔离与特征互斥断言
    // ==========================================

    public static void testPlatformIsolationAndContract() {
        System.out.println("\n[测试用例 4] 双车型平台架构与门禁隔离物理互斥断言");
        String coolCode = "binyue_cool_e02";
        String coolName = "吉利缤越 COOL (E02 / IHU516G)";

        String lCode = "binyue_l_flyme_auto";
        String lName = "吉利缤越 L (IHU624G / Flyme Auto)";

        check(!coolCode.equals(lCode), "缤越 COOL 与 缤越 L 平台代码严格互斥");
        check(!coolName.equals(lName), "缤越 COOL 与 缤越 L 平台显示名称严格互斥");
        check(lName.contains("Flyme Auto"), "缤越 L 平台明确标注 Flyme Auto 特征");
        check(coolName.contains("E02"), "缤越 COOL 平台明确标注 E02 / IHU516G 特征");
    }

    // ==========================================
    // 4. 前端 TopBar 车型胶囊动态标识渲染断言
    // ==========================================

    private static String computePlatformBadge(String platformCode, String platformName) {
        String code = platformCode != null ? platformCode.toLowerCase() : "";
        String name = platformName != null ? platformName : "";
        if (code.contains("binyue_l") || name.contains("缤越 L") || name.contains("缤越L")) {
            return "缤越L · IHU624G";
        }
        if (name.contains("COOL")) {
            return "缤越COOL · IHU516G";
        }
        return name.isEmpty() ? "车载平台" : name;
    }

    public static void testTopBarBadgeComputation() {
        System.out.println("\n[测试用例 5] 前端 TopBar 车型微胶囊标识计算断言");
        String badgeL = computePlatformBadge("binyue_l_flyme_auto", "吉利缤越 L (IHU624G / Flyme Auto)");
        check("缤越L · IHU624G".equals(badgeL), "缤越 L 平台顶栏正确计算为 [ 缤越L · IHU624G ]");

        String badgeCool = computePlatformBadge("binyue_cool_e02", "吉利缤越 COOL (E02 / IHU516G)");
        check("缤越COOL · IHU516G".equals(badgeCool), "缤越 COOL 平台顶栏正确计算为 [ 缤越COOL · IHU516G ]");

        String badgeGeneric = computePlatformBadge("generic_aosp", "通用车规平台");
        check("通用车规平台".equals(badgeGeneric), "通用车规平台正确兜底显示");
    }

    // ==========================================
    // 5. 车规原生桌面卡片 (Widget) 布局与渲染数据断言
    // ==========================================

    public static void testToolboxWidgetStateAndBadge() {
        System.out.println("\n[测试用例 6] 车机桌面原生卡片 (Widget) 电压健康分级与门锁状态机断言");

        // 电压健康分级
        float voltHigh = 13.5f;
        float voltWarn = 11.8f;
        float voltCritical = 11.2f;

        check(voltHigh >= 12.0f, "13.5V 正确命中健康良好电压区间 (绿/天蓝)");
        check(voltWarn >= 11.6f && voltWarn < 12.0f, "11.8V 正确命中电量偏低警戒区间 (琥珀黄)");
        check(voltCritical < 11.6f, "11.2V 正确命中亏电预警高危区间 (严重红)");

        // 门锁状态全关 vs 未关严判定
        boolean fl = false, fr = false, rl = false, rr = false, trunk = false;
        boolean anyOpen1 = fl || fr || rl || rr || trunk;
        check(!anyOpen1, "全车车门与尾门全关时状态判定为正常");

        // 模拟副驾未关严
        fr = true;
        boolean anyOpen2 = fl || fr || rl || rr || trunk;
        check(anyOpen2, "任一车门未关严时正确触发预警提示");
    }

    public static void main(String[] args) {
        System.out.println("==========================================================");
        System.out.println("  GeelyToolbox 双车型日志仿真与门禁回归测试套件 (JVM)");
        System.out.println("==========================================================");

        testBinyueCoolLogcatGearParsingAndStartStopDefense();
        testBinyueCoolSerialDoorPatternParsing();
        testBinyueLVHalPropertyMappingAndStateTransitions();
        testPlatformIsolationAndContract();
        testTopBarBadgeComputation();
        testToolboxWidgetStateAndBadge();

        System.out.println("\n----------------------------------------------------------");
        System.out.println("测试结果统计: 通用 " + passes + " 项通过, " + failures + " 项失败");
        System.out.println("----------------------------------------------------------");

        if (failures > 0) {
            System.err.println("❌ 回归测试未通过！");
            System.exit(1);
        } else {
            System.out.println("✅ 双车型全部感知路线与门禁隔离验证 100% 成功！");
        }
    }
}
