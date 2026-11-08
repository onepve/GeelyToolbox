package app.onepve.geelyconsole.utils;

/**
 * 语音播报「单项声道 / 增益」解析器（纯 Java，零 Android 依赖，可直接 JVM 回归）。
 *
 * <p>三通道 → 原厂音量流映射，依据 IHU516G (SX11-A3 / E02) 原厂固件 CarAudioService 实测：</p>
 * <pre>
 *   music        → USAGE_MEDIA(1)                        → group 0 MUSIC  → stream 3  (STREAM_MUSIC)
 *   nav          → USAGE_ASSISTANCE_NAVIGATION_GUIDANCE(12) → group 2 NAVI   → stream 12 (原厂 STREAM_NAVI)
 *   notification → USAGE_NOTIFICATION_EVENT(10)          → group 4 SYSTEM → stream 1  (STREAM_SYSTEM)
 * </pre>
 *
 * <p>原厂 audioUseDynamicRouting=false（legacy 分支），CarAudioService.setGroupVolume 直接转发
 * AudioManager.setStreamVolume(STREAM_TYPES[groupId])，STREAM_TYPES = {3, 0, 12, 9, 1, 16, 18, 19}。
 * 因此导航档绝不等于通知流：导航 → 私有流 12，系统提示 → STREAM_SYSTEM(1)，媒体 → STREAM_MUSIC(3)。</p>
 */
public final class VoiceGainResolver {

    /** 原厂私有扩展流号（AOSP 无此常量；IHU516G AudioSystem 已注册 STREAM_NAVI）。 */
    public static final int STREAM_NAVI = 12;

    /** 媒体主通道，对应 {@code android.media.AudioManager.STREAM_MUSIC}。 */
    public static final int STREAM_MUSIC = 3;

    /** 系统提示通道，对应 {@code android.media.AudioManager.STREAM_SYSTEM}。 */
    public static final int STREAM_SYSTEM = 1;

    private VoiceGainResolver() {
    }

    /**
     * 归一化声效键：把播放侧传入的文件名（如 {@code gear_r.mp3}）还原成配置裸 key（{@code gear_r}）。
     *
     * <p>前端持久化键为 {@code voice_item_channel_<key>} / {@code voice_item_offset_<key>}，
     * 其中 key 为裸名（无后缀）；播放侧却传带后缀文件名，导致查找永久错位、增益从未生效。</p>
     *
     * <p>对纯 TTS 仲裁键（{@code tts:...}）与自定义文件路径（{@code [[...]]}）原样返回——
     * 它们不是声效裸 key，本就不应命中单项配置。</p>
     *
     * @param voiceType 播放侧声效标识（文件名或仲裁键）
     * @return 归一化后的裸 key；null / 空串原样返回
     */
    public static String normalizeVoiceKey(String voiceType) {
        if (voiceType == null || voiceType.isEmpty()) {
            return voiceType;
        }
        String lower = voiceType.toLowerCase();
        // 仅剥离已知音频后缀；与 aaptOptions noCompress (mp3/wav/ogg) 及常见自定义音频对齐。
        for (String ext : new String[]{".mp3", ".wav", ".ogg", ".m4a"}) {
            if (lower.endsWith(ext)) {
                return voiceType.substring(0, voiceType.length() - ext.length());
            }
        }
        return voiceType;
    }

    /**
     * 三通道 → 原厂音量流号。
     *
     * @param channel 通道名（music / nav / notification）
     * @return 原厂 stream 号；未知值一律回落媒体主通道（不猜测）
     */
    public static int resolveStreamForChannel(String channel) {
        if ("nav".equals(channel)) {
            return STREAM_NAVI;
        }
        if ("notification".equals(channel)) {
            return STREAM_SYSTEM;
        }
        return STREAM_MUSIC;
    }
}
