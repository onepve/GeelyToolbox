package app.onepve.geelyconsole.utils;

/**
 * 日志导出脱敏与噪音过滤回归测试 (零 Android 依赖，纯 JVM 快速自测)
 */
public class LogFilterRegressionTest {

    private static int failures = 0;
    private static int passes = 0;

    private static void check(boolean cond, String label) {
        if (cond) {
            passes++;
        } else {
            failures++;
            System.out.println("  [FAIL] " + label);
        }
    }

    public static void main(String[] args) {
        System.out.println("== LogFilterRegressionTest: 日志导出脱敏与噪音过滤回归测试 ==");

        // 1. 测试噪音拦截 (isNoiseLogLine 必须精准拦截)
        check(SystemUtils.isNoiseLogLine("10-22 13:33:22.207 I/wificond(  665): wificond is starting up..."),
                "wificond 启动/扫描日志应被判定为噪音并拦截");
        check(SystemUtils.isNoiseLogLine("10-22 13:36:06.936 D/WifiHAL (  476): WifiRequest::create vendor command"),
                "WifiHAL 驱动日志应被判定为噪音并拦截");
        check(SystemUtils.isNoiseLogLine("10-22 13:36:00.486 D/CarSettingLogs->2500042( 3182): WifiProxy--> onReceive: action = android.net.wifi.SCAN_RESULTS"),
                "WifiProxy 扫描广播应被判定为噪音并拦截");
        check(SystemUtils.isNoiseLogLine("10-22 13:36:18.457 I/APM_AudioPolicyManager(  506): isGearReverse 0"),
                "APM_AudioPolicyManager 声卡高频轮询应被判定为噪音并拦截");
        check(SystemUtils.isNoiseLogLine("10-22 13:36:18.458 E/AudioFlinger(  506): stream 3, setFinalVolume, v: 0.031623, vh: 1.000000, abs(mReverseValue -"),
                "AudioFlinger 毫秒级音量刷新应被判定为噪音并拦截");
        check(SystemUtils.isNoiseLogLine("10-22 13:37:10.217 W/ecarx_avm_ModelDraw(  307): ModelDraw::draw_node start"),
                "ecarx_avm_ModelDraw 360 逐帧渲染应被判定为噪音并拦截");
        check(SystemUtils.isNoiseLogLine("10-22 13:36:52.347 D/LiteCameraFrame(  307): ~LiteCameraFrame:  releaseBuffer ret 0"),
                "LiteCameraFrame 相机 buffer 释放应被判定为噪音并拦截");
        check(SystemUtils.isNoiseLogLine("10-22 13:33:24.261 I/chatty  (  871): uid=1000 system_server identical 396 lines"),
                "chatty 日志压制行应被判定为噪音并拦截");
        check(SystemUtils.isNoiseLogLine("10-22 13:34:00.123 I/art   ( 1234): Explicit concurrent mark sweep GC freed 1024KB"),
                "GC 垃圾回收脉冲应被判定为噪音并拦截");
        check(SystemUtils.isNoiseLogLine("10-22 13:35:00.456 I/Choreographer( 1234): Skipped 45 frames! The application may be doing too much work"),
                "Choreographer 掉帧警告应被判定为噪音并拦截");

        // 2. 测试安全白名单放行 (绝不能误杀车身核心信号)
        check(!SystemUtils.isNoiseLogLine("10-22 13:36:10.888 I/SerialControl_v2_0( 1191): 91 02 01 55 00 00"),
                "SerialControl MCU 车门硬件报文绝对不拦截");
        check(!SystemUtils.isNoiseLogLine("10-22 13:36:10.888 I/SensorModule( 1191): VehId=Vehicle_Gear value=0x02"),
                "VehId=Vehicle_Gear 底盘挡位报文绝对不拦截");
        check(!SystemUtils.isNoiseLogLine("10-22 13:36:10.888 I/CarAudioInfo( 1191): Get Speed = 35"),
                "CarAudioInfo 随速音量车速绝对不拦截");
        check(!SystemUtils.isNoiseLogLine("10-22 13:36:10.888 I/SteeringWheel( 1191): reportKeyToAdaptApi keyCode=4"),
                "reportKeyToAdaptApi 方控按键绝对不拦截");
        check(!SystemUtils.isNoiseLogLine("10-22 13:36:10.888 I/Power( 1191): PEPS_PowerMode value=2"),
                "PEPS_PowerMode 电源状态绝对不拦截");
        check(!SystemUtils.isNoiseLogLine("10-22 13:36:10.888 I/ecarx_avm_state( 1191): camera_state: 1"),
                "ecarx_avm_state 360 状态机切换绝对不拦截");
        check(!SystemUtils.isNoiseLogLine("10-22 13:36:10.888 I/goc( 1191): A2dpSink connected"),
                "goc 蓝牙 A2DP 连接绝对不拦截");

        // 3. 测试敏感隐私脱敏
        String desensPhone = SystemUtils.desensitizeLogLine("User phone is 13812345678 in log");
        check(desensPhone.contains("[REDACTED_PHONE]") && !desensPhone.contains("13812345678"),
                "手机号必须被成功打码");

        String desensVin = SystemUtils.desensitizeLogLine("Vehicle vin is LB3742416N1234567 ready");
        check(desensVin.contains("****") && !desensVin.contains("LB3742416N1234567"),
                "车架号 VIN 必须被成功打码");

        System.out.println("\n测试结果: 通过 " + passes + ", 失败 " + failures);
        if (failures > 0) {
            System.exit(1);
        }
    }
}
