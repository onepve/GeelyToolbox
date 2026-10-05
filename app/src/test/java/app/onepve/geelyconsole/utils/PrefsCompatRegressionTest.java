package app.onepve.geelyconsole.utils;

/**
 * PrefsCompat 类型收敛 纯 Java 回归测试 (零 Android 依赖，直跑 JVM)。
 *
 * 覆盖真实缺陷场景: 旧版本把 vehicle_preset_drive_mode_target 按 boolean 写入，
 * 现行代码按字符串读取会抛 ClassCastException。必须保证：
 *  A. 存量 boolean (true/false) 一律回落默认值，绝不抛异常、绝不误判为模式；
 *  B. 其他非字符串残留 (Integer/Long/Set) 同样回落默认值；
 *  C. 合法的 String 原样透传 (含 "default" 与五档模式名)；
 *  D. 键缺失 (null) 回落默认值；空字符串不做猜测，原样返回。
 */
public class PrefsCompatRegressionTest {

    static int failures = 0;
    static int passes = 0;

    static void check(boolean cond, String label) {
        if (cond) { passes++; }
        else { failures++; System.out.println("  [FAIL] " + label); }
    }

    public static void main(String[] args) {
        System.out.println("== PrefsCompat 类型收敛回归 ==");

        // A. 存量 boolean 残留 (真实故障场景) -> 回落默认
        check("default".equals(PrefsCompat.coerce(Boolean.FALSE, "default")),
                "boolean(false) 残留 -> default (不抛异常)");
        check("default".equals(PrefsCompat.coerce(Boolean.TRUE, "default")),
                "boolean(true) 残留 -> default (不抛异常)");
        check("default".equals(PrefsCompat.coerce(Boolean.FALSE, "default")),
                "boolean 残留不误判成 eco/sport 等模式");

        // B. 其他非字符串残留 -> 回落默认
        check("default".equals(PrefsCompat.coerce(Integer.valueOf(3), "default")),
                "Integer 残留 -> default");
        check("default".equals(PrefsCompat.coerce(Long.valueOf(9L), "default")),
                "Long 残留 -> default");
        check("default".equals(PrefsCompat.coerce(new java.util.HashSet<String>(), "default")),
                "Set 残留 -> default");

        // C. 合法 String 原样透传
        check("sport".equals(PrefsCompat.coerce("sport", "default")),
                "String 'sport' 原样透传");
        check("default".equals(PrefsCompat.coerce("default", "default")),
                "String 'default' 原样透传");
        check("comfort".equals(PrefsCompat.coerce("comfort", "default")),
                "String 'comfort' 原样透传");

        // D. 缺失 / 边界
        check("default".equals(PrefsCompat.coerce(null, "default")),
                "键缺失 (null) -> default");
        check("".equals(PrefsCompat.coerce("", "default")),
                "空字符串原样返回 (不猜测模式)");
        check(PrefsCompat.getString(null, "k", "default").equals("default"),
                "prefs 为 null -> default (不 NPE)");
        check(PrefsCompat.getString(null, null, "default").equals("default"),
                "key 为 null -> default (不 NPE)");

        System.out.println("==== RESULT: pass=" + passes + " fail=" + failures + " ====");
        if (failures > 0) System.exit(1);
    }
}
