package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 1~13 号车规全量声道实听测试器
 * 支持 Android 9 (API 28) 车载 HAL AudioAttributes.USAGE 1~13 物理通道探测
 * 
 * 铁律保障：
 * 1. 单次临时试听：播放完 310ms 微信测试音后，后台毫秒级执行 stop() 与 release()，杜绝 AudioTrack 挂死 DSP 通道；
 * 2. 播后自动恢复：试听结束后自动选通原厂蓝牙通道与 MAY_DUCK 闪避焦点，保障微信与媒体音频瞬间复位；
 * 3. 绝不擅自篡改用户首选配置：仅供实听排查，不修改 preferred_audio_usage_channel。
 */
public class ChannelAudioTester {
    private static final String TAG = "ChannelAudioTester";
    private static final int SAMPLE_RATE = 44100;
    private static AudioTrack sCurrentTrack = null;
    private static final Object sLock = new Object();
    private static final ScheduledExecutorService sScheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * 在指定的 AudioAttributes.USAGE 物理通道上播放一段清脆的测试提示音
     * 播放完毕后自动释放 AudioTrack 并平滑复位蓝牙/微信工作声道
     * @param context 上下文
     * @param usageChannel 1 到 13 (对应 USAGE_MEDIA 到 USAGE_ASSISTANCE_SONIFICATION)
     */
    public static void playChannelBeep(final Context context, final int usageChannel) {
        new Thread(() -> {
            synchronized (sLock) {
                try {
                    stopCurrent();

                    // 生成微信提示风格的双音嘟嘟声 (784Hz 110ms + 间隔 40ms + 1046Hz 160ms，总时长 310ms)
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

                    // 400ms 后提示音彻底放完，自动关闭 AudioTrack 并恢复蓝牙工作声道，绝不占死硬件路由
                    sScheduler.schedule(() -> {
                        synchronized (sLock) {
                            stopCurrent();
                        }
                        try {
                            if (context != null) {
                                EasMediaBridge.getInstance(context).activateBluetoothChannel();
                                Log.i(TAG, "测试发声结束，已自动复位蓝牙声道与 MAY_DUCK 焦点守护");
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "复位蓝牙声道异常: " + t.getMessage());
                        }
                    }, 400, TimeUnit.MILLISECONDS);

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
     * 合成一段清脆的双频提示音 (微信新消息同款高辨识度叮咚音)
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
