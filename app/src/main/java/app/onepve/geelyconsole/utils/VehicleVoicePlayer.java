package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.io.File;
import java.util.Locale;

/**
 * 车辆语音播报器（支持本地短音频与系统 TTS 引擎）
 * 1. 优先读取 /sdcard/GeelyToolbox/voice/ 或 /sdcard/Music/ 下的自定义音频 (MP3/WAV)
 * 2. 音频不存在时，自动调用系统原生 TextToSpeech (可对接小爱同学语音包)
 * 3. 播放时申请瞬态音频焦点（AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK），自动压低音乐音量，播完秒恢复
 */
public class VehicleVoicePlayer {
    private static final String TAG = "VehicleVoicePlayer";
    private static VehicleVoicePlayer instance;

    private final Context context;
    private final AudioManager audioManager;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private VehicleVoicePlayer(Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        initTts();
    }

    public static synchronized VehicleVoicePlayer getInstance(Context context) {
        if (instance == null) {
            instance = new VehicleVoicePlayer(context);
        }
        return instance;
    }

    private void initTts() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    String targetEngine = null;
                    try {
                        context.getPackageManager().getPackageInfo("com.xiaomi.mibrain.speech", 0);
                        targetEngine = "com.xiaomi.mibrain.speech";
                    } catch (Exception ignored) {
                    }

                    TextToSpeech.OnInitListener listener = new TextToSpeech.OnInitListener() {
                        @Override
                        public void onInit(int status) {
                            if (status == TextToSpeech.SUCCESS && tts != null) {
                                int res = tts.setLanguage(Locale.CHINESE);
                                if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                                    ttsReady = true;
                                    tts.setSpeechRate(1.1f);
                                }
                            }
                        }
                    };

                    if (targetEngine != null) {
                        tts = new TextToSpeech(context, listener, targetEngine);
                    } else {
                        tts = new TextToSpeech(context, listener);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to init TextToSpeech: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 播放语音（支持 用户自定义文件 优先，内置 Asset 优质音频次之，降级 TTS 播报）
     * @param voiceFileName 比如 "door_fl.mp3", "door_fr.mp3", "flameout.mp3"
     * @param fallbackText  降级文字，比如 "主驾车门打开，请注意后方来车"
     */
    public void play(String voiceFileName, final String fallbackText) {
        // 1. 用户手动设置的自定义文件路径
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            String customPath = prefs.getString("custom_voice_" + voiceFileName, "");
            if (customPath != null && !customPath.isEmpty()) {
                File customPrefFile = new File(customPath);
                if (customPrefFile.exists() && customPrefFile.length() > 0) {
                    playAudioFile(customPrefFile);
                    return;
                }
            }
        } catch (Exception ignored) {}

        // 2. 外部储存固定放置目录 (/sdcard/GeelyToolbox/voice/ 或 /sdcard/Music/)
        File customFile1 = new File("/sdcard/GeelyToolbox/voice/" + voiceFileName);
        File customFile2 = new File("/sdcard/Music/" + voiceFileName);
        File targetFile = customFile1.exists() ? customFile1 : (customFile2.exists() ? customFile2 : null);

        if (targetFile != null && targetFile.length() > 0) {
            playAudioFile(targetFile);
            return;
        }

        // 3. 工具箱内置优质音频资产 (assets/audio/xxx.mp3)
        if (playAssetAudio("audio/" + voiceFileName)) {
            return;
        }

        // 4. 最后兜底：系统 TTS 朗读
        speakText(fallbackText);
    }

    private boolean playAssetAudio(final String assetPath) {
        try {
            final android.content.res.AssetFileDescriptor afd = context.getAssets().openFd(assetPath);
            if (afd == null) return false;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    MediaPlayer mp = null;
                    try {
                        requestAudioFocus();
                        mp = new MediaPlayer();
                        mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                        afd.close();
                        mp.prepare();
                        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mediaPlayer) {
                                abandonAudioFocus();
                                try { mediaPlayer.release(); } catch (Exception ignored) {}
                            }
                        });
                        mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                            @Override
                            public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                                abandonAudioFocus();
                                try { mediaPlayer.release(); } catch (Exception ignored) {}
                                return true;
                            }
                        });
                        mp.start();
                    } catch (Exception e) {
                        abandonAudioFocus();
                        if (mp != null) {
                            try { mp.release(); } catch (Exception ignored) {}
                        }
                    }
                }
            }).start();
            return true;
        } catch (Exception e) {
            Log.d(TAG, "Asset audio not found or failed: " + assetPath);
            return false;
        }
    }

    private void playAudioFile(final File file) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                MediaPlayer mp = null;
                try {
                    requestAudioFocus();
                    mp = new MediaPlayer();
                    mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    mp.setDataSource(file.getAbsolutePath());
                    mp.prepare();
                    mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mediaPlayer) {
                            abandonAudioFocus();
                            try {
                                mediaPlayer.release();
                            } catch (Exception ignored) {}
                        }
                    });
                    mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                        @Override
                        public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                            abandonAudioFocus();
                            try {
                                mediaPlayer.release();
                            } catch (Exception ignored) {}
                            return true;
                        }
                    });
                    mp.start();
                } catch (Exception e) {
                    abandonAudioFocus();
                    if (mp != null) {
                        try { mp.release(); } catch (Exception ignored) {}
                    }
                }
            }
        }).start();
    }

    public void speakText(final String text) {
        if (text == null || text.trim().isEmpty()) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    requestAudioFocus();
                    if (tts != null && ttsReady) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vehicle_tts");
                        } else {
                            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
                        }
                    }
                    // 2.5秒后自动释放音频焦点恢复音乐
                    mainHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            abandonAudioFocus();
                        }
                    }, 2500);
                } catch (Exception ignored) {
                    abandonAudioFocus();
                }
            }
        });
    }

    private void requestAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                AudioFocusRequest req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(attrs)
                        .build();
                audioManager.requestAudioFocus(req);
            } else {
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            }
        } catch (Exception ignored) {}
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        try {
            audioManager.abandonAudioFocus(null);
        } catch (Exception ignored) {}
    }
}
