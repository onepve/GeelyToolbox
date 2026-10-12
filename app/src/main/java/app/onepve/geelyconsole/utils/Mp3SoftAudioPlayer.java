package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

/**
 * 车规级纯 Java MP3 软解播放器 (基于 JLayer 1.0.1 + AudioTrack)
 * 专为被车机厂商（如吉利帝豪L SS13 / IHU521G）裁掉 audio/mpeg 软解组件的车机设计。
 * 零 native SO 依赖，直接在内存中将 MP3 解码为 16-bit PCM，
 * 并通过底层 AudioTrack 吐向功放，彻底跳过脆弱的 mediaserver / NuPlayer。
 */
public class Mp3SoftAudioPlayer {
    private static final String TAG = "Mp3SoftAudioPlayer";
    private static volatile AudioTrack sActiveTrack = null;
    private static final Object sLock = new Object();

    public interface PlayCallback {
        void onStarted();
        void onFinished();
        void onError(String message);
    }

    public static void stopCurrent() {
        synchronized (sLock) {
            if (sActiveTrack != null) {
                try {
                    sActiveTrack.stop();
                } catch (Exception ignored) {}
                try {
                    sActiveTrack.release();
                } catch (Exception ignored) {}
                sActiveTrack = null;
            }
        }
    }

    public static void play(final Context context,
                            final File mp3File,
                            final String voiceType,
                            final AudioAttributes audioAttributes,
                            final AtomicInteger currentSessionId,
                            final int thisSessionId,
                            final PlayCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (currentSessionId != null && currentSessionId.get() != thisSessionId) {
                    if (callback != null) callback.onFinished();
                    return;
                }
                stopCurrent();

                InputStream fis = null;
                BufferedInputStream bis = null;
                Bitstream bitstream = null;
                AudioTrack track = null;

                try {
                    fis = new FileInputStream(mp3File);
                    bis = new BufferedInputStream(fis, 16 * 1024);
                    bitstream = new Bitstream(bis);
                    Decoder decoder = new Decoder();

                    Header header = bitstream.readFrame();
                    if (header == null) {
                        Log.w(TAG, "Empty MP3 header for " + mp3File.getName());
                        if (callback != null) callback.onError("Empty MP3 header");
                        return;
                    }

                    int sampleRate = header.frequency();
                    int channels = (header.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
                    int channelConfig = (channels == 1) ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
                    int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
                    int bufferSize = Math.max(minBufferSize, 16384);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        AudioAttributes attrs = audioAttributes;
                        if (attrs == null) {
                            attrs = new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build();
                        }
                        track = new AudioTrack.Builder()
                                .setAudioAttributes(attrs)
                                .setAudioFormat(new AudioFormat.Builder()
                                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                        .setSampleRate(sampleRate)
                                        .setChannelMask(channelConfig)
                                        .build())
                                .setBufferSizeInBytes(bufferSize)
                                .setTransferMode(AudioTrack.MODE_STREAM)
                                .build();
                    } else {
                        boolean isRev = (voiceType != null && (voiceType.contains("gear_r") || voiceType.contains("reverse")));
                        int streamType = isRev ? AudioManager.STREAM_NOTIFICATION : AudioManager.STREAM_MUSIC;
                        track = new AudioTrack(streamType, sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);
                    }

                    // 确保音量设置满格
                    try {
                        track.setStereoVolume(AudioTrack.getMaxVolume(), AudioTrack.getMaxVolume());
                    } catch (Throwable ignored) {}

                    synchronized (sLock) {
                        if (currentSessionId != null && currentSessionId.get() != thisSessionId) {
                            try { track.release(); } catch (Exception ignored) {}
                            if (callback != null) callback.onFinished();
                            return;
                        }
                        sActiveTrack = track;
                    }

                    track.play();
                    if (callback != null) callback.onStarted();
                    Log.i(TAG, "Mp3SoftAudioPlayer started: " + mp3File.getName() + " (" + sampleRate + "Hz, ch=" + channels + ")");

                    while (header != null) {
                        if (currentSessionId != null && currentSessionId.get() != thisSessionId) {
                            Log.d(TAG, "Session superseded during soft-decoding: " + mp3File.getName());
                            break;
                        }
                        SampleBuffer sampleBuffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                        short[] pcm = sampleBuffer.getBuffer();
                        int pcmLength = sampleBuffer.getBufferLength();
                        if (pcmLength > 0) {
                            track.write(pcm, 0, pcmLength);
                        }
                        bitstream.closeFrame();
                        header = bitstream.readFrame();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Mp3SoftAudioPlayer failed for " + mp3File.getName() + ": " + e.getMessage(), e);
                    if (callback != null) callback.onError(e.getMessage());
                } finally {
                    try {
                        if (bitstream != null) bitstream.close();
                    } catch (Exception ignored) {}
                    try {
                        if (bis != null) bis.close();
                    } catch (Exception ignored) {}
                    try {
                        if (fis != null) fis.close();
                    } catch (Exception ignored) {}

                    if (track != null) {
                        try {
                            if (currentSessionId != null && currentSessionId.get() == thisSessionId) {
                                // 缓冲余量毫秒级自然释放
                                Thread.sleep(60);
                            }
                            track.stop();
                        } catch (Exception ignored) {}
                        try {
                            track.release();
                        } catch (Exception ignored) {}
                    }
                    synchronized (sLock) {
                        if (sActiveTrack == track) {
                            sActiveTrack = null;
                        }
                    }
                    if (callback != null) {
                        callback.onFinished();
                    }
                }
            }
        }, "Mp3SoftPlayerThread").start();
    }
}
