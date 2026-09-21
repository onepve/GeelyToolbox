package app.onepve.geelyconsole.utils;

/**
 * VoiceGainResolver 纯 Java 回归测试（零 Android 依赖，直跑 JVM）。
 *
 * <p>覆盖真实缺陷场景：播放侧传带后缀文件名（gear_r.mp3），配置端存裸 key（gear_r），
 * 未归一化导致 voice_item_offset_* 查找永久失败、增益从未生效。</p>
 *
 * <p>同时锁死三通道 → 原厂 stream 映射，防止未来又把导航档误并进通知流
 * （历史缺陷：nav/notification 被统一调成 STREAM_NOTIFICATION，实际导航应走私有流 12、
 * 系统提示应走 STREAM_SYSTEM(1)）。</p>
 */
public class VoiceGainResolverRegressionTest {

    static int failures = 0;
    static int passes = 0;

    static void check(boolean cond, String label) {
        if (cond) {
            passes++;
        } else {
            failures++;
            System.out.println("  [FAIL] " + label);
        }
    }

    public static void main(String[] args) {
        System.out.println("== VoiceGainResolver 键名归一化 + 三通道流映射回归 ==");

        // ── A. 键名归一化：后缀剥离 ──
        check("gear_r".equals(VoiceGainResolver.normalizeVoiceKey("gear_r.mp3")),
                "gear_r.mp3 -> gear_r");
        check("gear_r".equals(VoiceGainResolver.normalizeVoiceKey("gear_r.MP3")),
                "gear_r.MP3 (大写) -> gear_r");
        check("gear_r".equals(VoiceGainResolver.normalizeVoiceKey("gear_r")),
                "裸 key gear_r 原样返回");
        check("trunk_open".equals(VoiceGainResolver.normalizeVoiceKey("trunk_open.wav")),
                "trunk_open.wav -> trunk_open");
        check("door_open".equals(VoiceGainResolver.normalizeVoiceKey("door_open.ogg")),
                "door_open.ogg -> door_open");
        check("theme_x".equals(VoiceGainResolver.normalizeVoiceKey("theme_x.m4a")),
                "theme_x.m4a -> theme_x");

        // ── B. 键名归一化：非文件名标识原样透传 ──
        check("tts:已挂入前进挡".equals(VoiceGainResolver.normalizeVoiceKey("tts:已挂入前进挡")),
                "纯 TTS 仲裁键原样返回 (不误剥后缀)");
        check("[[/sdcard/custom.mp3]]".equals(VoiceGainResolver.normalizeVoiceKey("[[/sdcard/custom.mp3]]")),
                "自定义文件路径包裹键不误剥 .mp3");
        check(null == VoiceGainResolver.normalizeVoiceKey(null),
                "null 原样返回 (不 NPE)");
        check("".equals(VoiceGainResolver.normalizeVoiceKey("")),
                "空串原样返回");

        // ── C. 三通道 → 原厂 stream 映射 ──
        check(VoiceGainResolver.resolveStreamForChannel("music") == 3,
                "music -> STREAM_MUSIC(3)");
        check(VoiceGainResolver.resolveStreamForChannel("nav") == 12,
                "nav -> 原厂 STREAM_NAVI(12)，绝不并入通知流");
        check(VoiceGainResolver.resolveStreamForChannel("notification") == 1,
                "notification -> STREAM_SYSTEM(1)，绝不并入 STREAM_NOTIFICATION(5)");

        // ── D. 未知/缺省回落媒体主通道 ──
        check(VoiceGainResolver.resolveStreamForChannel(null) == 3,
                "null channel -> 媒体主通道(3)");
        check(VoiceGainResolver.resolveStreamForChannel("unknown") == 3,
                "未知 channel -> 媒体主通道(3) (不猜测)");

        System.out.println("==== RESULT: pass=" + passes + " fail=" + failures + " ====");
        if (failures > 0) System.exit(1);
    }
}
