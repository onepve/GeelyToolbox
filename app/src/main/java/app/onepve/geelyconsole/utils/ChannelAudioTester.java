package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

/**
 * 1~13 号车规全量声道实听测试器
 * 支持 Android 9 (API 28) 车载 HAL AudioAttributes.USAGE 1~13 物理通道探测
 */
public class ChannelAudioTester {
    private static final String TAG = "ChannelAudioTester";
    private static final int SAMPLE_RATE = 44100;
    private static AudioTrack sCurrentTrack = null;
    private static final Object sLock = new Object();

    /**
     * 在指定的 AudioAttributes.USAGE 物理通道上播放一段清脆的测试提示音
     * @param context 上下文
     * @param usageChannel 1 到 13 (对应 USAGE_MEDIA 到 USAGE_ASSISTANCE_SONIFICATION)
     */
    public static void playChannelBeep(Context context, int usageChannel) {
        new Thread(() -> {
            synchronized (sLock) {
                try {
                    stopCurrent();

                    // 生成微信提示风格的双音嘟嘟声 (720Hz 120ms + 间隔 50ms + 960Hz 180ms)
                    byte[] audioData = generateNotificationTone();

                    AudioAttributes attributes = new AudioAttributes.Builder()
                            .setUsage(usageChannel)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .build();

                    AudioFormat format = new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build();

                    int minBufferSize = AudioTrack.getMinBufferSize(
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                    );

                    int bufferSize = Math.max(minBufferSize, audioData.length);

                    sCurrentTrack = new AudioTrack.Builder()
                            .setAudioAttributes(attributes)
                            .setAudioFormat(format)
                            .setBufferSizeInBytes(bufferSize)
                            .setTransferMode(AudioTrack.MODE_STATIC)
                            .build();

                    sCurrentTrack.write(audioData, 0, audioData.length);
                    sCurrentTrack.play();
                    Log.i(TAG, "已在车规声道 USAGE_" + usageChannel + " 上成功发声测试");

                } catch (Throwable t) {
                    Log.e(TAG, "声道 USAGE_" + usageChannel + " 发声测试失败: " + t.getMessage(), t);
                }
            }
        }, "ChannelTesterThread").start();
    }

    public static void stopCurrent() {
        synchronized (sLock) {
            if (sCurrentTrack != null) {
                try {
                    if (sCurrentTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        sCurrentTrack.stop();
                    }
                    sCurrentTrack.release();
                } catch (Throwable ignored) {}
                sCurrentTrack = null;
            }
        }
    }

    /**
     * 合成一段清脆的双频提示音 (类似即时通讯新消息叮咚音)
     */
    private static byte[] generateNotificationTone() {
        int durationMs1 = 110;
        int durationMsSilence = 40;
        int durationMs2 = 160;
        int totalMs = durationMs1 + durationMsSilence + durationMs2;

        int totalSamples = (SAMPLE_RATE * totalMs) / 1000;
        byte[] output = new byte[totalSamples * 2]; // 16-bit mono

        double freq1 = 784.0; // G5
        double freq2 = 1046.5; // C6

        int sampleIdx = 0;

        // 段落 1: 784Hz
        int samples1 = (SAMPLE_RATE * durationMs1) / 1000;
        for (int i = 0; i < samples1; i++, sampleIdx++) {
            double angle = 2.0 * Math.PI * i / (SAMPLE_RATE / freq1);
            // 加平滑包络防破音
            double envelope = Math.sin(Math.PI * i / samples1);
            short sample = (short) (Math.sin(angle) * 28000 * envelope);
            output[sampleIdx * 2] = (byte) (sample & 0xFF);
            output[sampleIdx * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        // 段落 2: 静音间隔
        int samplesSilence = (SAMPLE_RATE * durationMsSilence) / 1000;
        for (int i = 0; i < samplesSilence; i++, sampleIdx++) {
            output[sampleIdx * 2] = 0;
            output[sampleIdx * 2 + 1] = 0;
        }

        // 段落 3: 1046.5Hz
        int samples2 = (SAMPLE_RATE * durationMs2) / 1000;
        for (int i = 0; i < samples2; i++, sampleIdx++) {
            double angle = 2.0 * Math.PI * i / (SAMPLE_RATE / freq2);
            double envelope = Math.sin(Math.PI * i / samples2);
            short sample = (short) (Math.sin(angle) * 28000 * envelope);
            output[sampleIdx * 2] = (byte) (sample & 0xFF);
            output[sampleIdx * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }

        return output;
    }
}
