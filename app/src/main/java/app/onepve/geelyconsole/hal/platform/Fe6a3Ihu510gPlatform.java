package app.onepve.geelyconsole.hal.platform;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 吉利缤瑞 COOL FE6A3 + IHU510G 双代号精确识别车型的专属修补平台。
 *
 * <p>物理隔离铁律：本文件只承载 FE6A3_IHU510G 这一台车的专项修复逻辑。
 * 主服务（VehicleAutomationService）保留缤越 COOL 2022 纯净基准（CAN only），
 * 本车型通过工厂双代号联合识别命中后，由主服务委派调用本类的 resolveGear 接管挡位解析。
 * 其它车型（缤越 COOL、帝豪 L 等）不会加载本文件逻辑，物理上零污染。</p>
 *
 * <h3>本车故障与修复依据（实车日志 FE6A3_IHU510G.zip 逐帧分析）</h3>
 * <ul>
 *   <li>该车型车机上同时存在两条挡位信号通道：
 *       <ul>
 *         <li>CAN 总线心跳 {@code ecarx_avm_state: recvVehicleCmd(227) VehId=Vehicle_Gear value=0x0X}，
 *             每 3 秒周期上报，P 挡停车时也持续心跳，是全时段权威真源；</li>
 *         <li>SensorModule {@code INFO_ID_VDRIVEINFO_GEAR_POSITION} 报文，开机初期各进程注册时广播
 *             funcValue(0x00000001)（无效值），随后各进程以各自周期（最快约 1.04 秒）高频
 *             getFunctionValue 轮询并输出 return funValue(0x00200230)。</li>
 *       </ul></li>
 *   <li>实车 P 挡停车场景：CAN 心跳恒为 0x05(P)，而 Sensor 通道反复报 0x00200230(N)。
 *       两通道交替喂入 GearStateMachine，且 3 秒心跳间隔大于 120ms 防抖窗，
 *       防抖无法吞掉这种双源交替，导致 P/N 每 1~3 秒交替播报。</li>
 *   <li>修复：本车型挡位唯一真源锁定 CAN 总线心跳，Sensor 通道报文在本文件内
 *       显式驳回（不参与挡位状态机）。</li>
 * </ul>
 */
public class Fe6a3Ihu510gPlatform extends BinruiCoolPlatform {

    public static final String PLATFORM_CODE = "fe6a3_ihu510g";

    /** SensorModule GEAR_POSITION 报文的 funValue 值提取（十六进制） */
    private static final Pattern P_FUNVALUE = Pattern.compile("funValue\\((?:0x)?([0-9a-fA-F]+)\\)");

    @Override
    public String getPlatformCode() {
        return PLATFORM_CODE;
    }

    @Override
    public String getPlatformName() {
        return "吉利缤瑞 COOL (FE6A3 + IHU510G 专属适配)";
    }

    /**
     * FE6A3_IHU510G 专属挡位解析：
     * <ul>
     *   <li>CAN 报文已由主服务基准解析（canGear >= 0）→ 直接采用，与基准一致；</li>
     *   <li>SensorModule GEAR_POSITION 报文 → 通电门控保留（熄火不播报），
     *       但信号值恒定为 P 挡锚点输出已被否决——该车真实行车时 CAN 会持续上报 D/N/R，
     *       因此 Sensor 报文一律驳回（返回 0 表示"已处理但不产生挡位"由主服务跳过）；
     *       实现上采用：仅当报文为开机无效值 0x00000001 或熄火态时直接驳回，
     *       其余 Sensor 挡位值一律不采信。</li>
     * </ul>
     * 返回 -1 = 非挡位相关报文，交还主服务继续后续解析。
     */
    @Override
    public int resolveGear(String line, int canGear, boolean engineRunning) {
        if (line == null) return -1;

        // CAN 总线挡位报文：唯一真源，直接沿用主服务基准解析结果
        if (line.contains("VehId=Vehicle_Gear")) {
            return canGear >= 0 ? canGear : 0;
        }

        // SensorModule 挡位报文：本车型双通道冲突源，一律驳回，绝不喂入状态机
        if (line.contains("INFO_ID_VDRIVEINFO_GEAR_POSITION")) {
            return 0;
        }

        return -1;
    }
}
