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
 * <h3>本车故障与修复依据（实车日志 IHU510G_FE6A3_181821_20261104103548 逐帧分析）</h3>
 * <ul>
 *   <li>实车日志铁证：该车机固件中未输出 CAN 总线 {@code VehId=Vehicle_Gear} 心跳。</li>
 *   <li>实车真实挡位跳变唯一通道为 SensorModule 事件：
 *       {@code onFunctionValueChanged:funId(0x00200200) propId(INFO_ID_VDRIVEINFO_GEAR_POSITION) funcValue(0x002002X0)}
 *       其中低字节：
 *       <ul>
 *         <li>0x10 -> P 挡 (GradePlace_status=3, gear=2097680)</li>
 *         <li>0x20 -> R 挡 (GradePlace_status=2, gear=2097696)</li>
 *         <li>0x30 -> N 挡 (GradePlace_status=0, gear=2097712)</li>
 *         <li>0x40 -> D 挡 (GradePlace_status=1, gear=2097728)</li>
 *       </ul>
 *   </li>
 *   <li>高频轮询防抖与假 N 过滤：
 *       系统各后台服务（如 5486）会以约 1 秒周期主动调用 {@code getFunctionValue} 查询，
 *       并输出 {@code return funValue(0x00200230)}（无物理动作的空转轮询）。
 *       因此本车型专属解析策略：
 *       1. 优先只采信真实的跳变事件 {@code onFunctionValueChanged}；
 *       2. 忽略/驳回 getFunctionValue 的高频静态轮询，防止停车态被假 N 报文反复轰炸。
 *   </li>
 * </ul>
 */
public class Fe6a3Ihu510gPlatform extends BinruiCoolPlatform {

    public static final String PLATFORM_CODE = "fe6a3_ihu510g";

    /** 匹配 SensorModule 挡位跳变事件与值 */
    private static final Pattern P_SENSOR_GEAR_VAL = Pattern.compile(
            "(?:funcValue|funValue)\\((?:0x)?([0-9a-fA-F]+)\\)"
    );

    @Override
    public String getPlatformCode() {
        return PLATFORM_CODE;
    }

    @Override
    public String getPlatformName() {
        return "吉利缤瑞 COOL (FE6A3 + IHU510G 专属适配)";
    }

    /**
     * 该车型需要额外挂载进 logcat 监听白名单的专属报文关键字。
     * 确保即使主服务基础白名单变动，510 的挡位跳变事件也能准时进入 parseLogLine。
     */
    @Override
    public String[] getExclusiveLogcatKeywords() {
        return new String[] {
            "info_id_vdriveinfo_gear_position",
            "0x00200200"
        };
    }

    /**
     * FE6A3_IHU510G 专属挡位解析：
     *
     * @param line          原始 logcat 行
     * @param canGear       主服务基准解析结果（若有 CAN 报文命中）
     * @param engineRunning 当前发动机运行状态
     * @return >=0 表示该车型给出最终挡位（2=D, 3=N, 4=R, 5=P）；0 表示已识别但丢弃/防抖；-1 表示非本车挡位报文交还主服务
     */
    @Override
    public int resolveGear(String line, int canGear, boolean engineRunning) {
        if (line == null) return -1;

        // 1. 若后续固件或特定工况下出现 CAN 总线 VehId=Vehicle_Gear，仍然优先作为最高优先级真源
        if (line.contains("VehId=Vehicle_Gear")) {
            return canGear >= 0 ? canGear : 0;
        }

        // 2. SensorModule 挡位信号通道：
        if (line.contains("INFO_ID_VDRIVEINFO_GEAR_POSITION") || line.contains("0x00200200")) {
            // 关键过滤：只采信真正的挡位物理切换事件 onFunctionValueChanged
            // 忽略 getFunctionValue 输出的周期性静态轮询（杜绝驻车 P 态下每秒 return funValue(0x00200230) 引起的假 N 轰炸）
            if (line.contains("getFunctionValue")) {
                return 0; // 显式驳回静态轮询
            }

            // 仅对主动跳变事件 onFunctionValueChanged 进行挡位解析
            if (line.contains("onFunctionValueChanged") || line.contains("funcValue")) {
                try {
                    Matcher m = P_SENSOR_GEAR_VAL.matcher(line);
                    if (m.find()) {
                        long val = Long.parseLong(m.group(1), 16);
                        int lowByte = (int) (val & 0xFF);
                        if (lowByte == 0x30) {
                            return 5; // P 挡 (Park)
                        } else if (lowByte == 0x40) {
                            return 4; // R 挡 (Reverse)
                        } else if (lowByte == 0x10) {
                            return 3; // N 挡 (Neutral)
                        } else if (lowByte == 0x20) {
                            return 2; // D 挡 (Drive)
                        }
                    }
                } catch (Exception ignored) {}
            }
            return 0; // 其他未识别的 Sensor 格式直接安全吞掉
        }

        return -1;
    }
}
