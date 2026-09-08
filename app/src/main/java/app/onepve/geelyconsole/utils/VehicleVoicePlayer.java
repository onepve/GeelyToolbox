package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

/**
 * 车辆语音播报器（支持本地短音频与系统 TTS 引擎）
 * 1. 优先读取用户自定义台词 TTS
 * 2. 其次读取自定义音频文件路径 (MP3/WAV)
 * 3. 其次读取外部放置目录 (/sdcard/Download/语音主题包/ 或 /sdcard/Music/)
 * 4. 其次读取内置资产 assets/audio/xxx.mp3，解压至内部私有目录播放 (防FD关闭异常)
 * 5. 最后兜底调用系统原生 TextToSpeech (对接小爱同学)
 * 6. 默认走车规媒体通道 (STREAM_MUSIC / USAGE_MEDIA)，确保车载功放 100% 出声
 */
public class VehicleVoicePlayer {

    public static AudioAttributes getVoiceAudioAttributes(Context context, String voiceType) {
        String channel = "music";
        try {
            SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            channel = prefs.getString("voice_audio_channel", "music");
        } catch (Exception ignored) {}

        AudioAttributes.Builder builder = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);

        // 倒车挡 (R 挡) 核心保护：车机进入倒车挡时硬件 DSP 会强制将媒体流 (STREAM_MUSIC) 静音或衰减 80%
        // 故倒车播报必须优先走独立防衰减的安全导航引导通道 (USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        boolean isReverse = (voiceType != null && (voiceType.contains("gear_r") || voiceType.contains("reverse") || voiceType.contains("倒车")));

        if (isReverse || "nav".equals(channel)) {
            builder.setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
        } else if ("notification".equals(channel)) {
            builder.setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT);
        } else {
            // 默认走媒体主通道，保证车机主功放喇叭 100% 放出温润声音，永不静音！
            builder.setUsage(AudioAttributes.USAGE_MEDIA);
        }
        return builder.build();
    }

    public static AudioAttributes getVoiceAudioAttributes(Context context) {
        return getVoiceAudioAttributes(context, null);
    }

    private static final String TAG = "VehicleVoicePlayer";
    private static VehicleVoicePlayer instance;

    private final Context context;
    private final AudioManager audioManager;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MediaPlayer currentMediaPlayer = null;
    private final Object playerLock = new Object();
    private final java.util.concurrent.atomic.AtomicInteger playSessionId = new java.util.concurrent.atomic.AtomicInteger(0);
    private Object activeFocusRequest = null;
    private int restoreVolumeAfterPlay = -1;
    private int originalStreamType = AudioManager.STREAM_MUSIC;

    private synchronized void applyVolumeOffsetBeforePlay(String voiceType) {
        if (audioManager == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            int offset = prefs.getInt("voice_volume_offset", 0);

            boolean isReverse = (voiceType != null && (voiceType.contains("gear_r") || voiceType.contains("reverse") || voiceType.contains("倒车")));

            // 倒车挡防衰减智能补偿：若处于倒车状态，由于车机系统自动压制背景音量，额外增加 +4 格动态补偿
            if (isReverse) {
                offset += 4;
            }

            int stream = (isReverse || "nav".equals(prefs.getString("voice_audio_channel", "music")))
                    ? AudioManager.STREAM_NOTIFICATION
                    : AudioManager.STREAM_MUSIC;

            int currentVol = audioManager.getStreamVolume(stream);
            int maxVol = audioManager.getStreamMaxVolume(stream);
            int targetVol = Math.max(0, Math.min(maxVol, currentVol + offset));

            // 倒车挡若当前音量过低，强制保障至少 65% 的清晰播报音量
            if (isReverse) {
                int floorVol = (int) (maxVol * 0.65f);
                if (targetVol < floorVol) {
                    targetVol = floorVol;
                }
            }

            if (targetVol != currentVol && restoreVolumeAfterPlay < 0) {
                restoreVolumeAfterPlay = currentVol;
                originalStreamType = stream;
                audioManager.setStreamVolume(stream, targetVol, 0);
                Log.i(TAG, "Applied voice volume offset: " + offset + " (vol: " + currentVol + " -> " + targetVol + ", stream: " + stream + ")");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to apply volume offset: " + e.getMessage());
        }
    }

    private synchronized void applyVolumeOffsetBeforePlay() {
        applyVolumeOffsetBeforePlay(null);
    }

    private synchronized void restoreVolumeAfterPlay() {
        if (audioManager == null || restoreVolumeAfterPlay < 0) return;
        try {
            audioManager.setStreamVolume(originalStreamType, restoreVolumeAfterPlay, 0);
            Log.i(TAG, "Restored vehicle volume to original: " + restoreVolumeAfterPlay);
        } catch (Exception ignored) {
        } finally {
            restoreVolumeAfterPlay = -1;
        }
    }

    private VehicleVoicePlayer(Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        initTts();
        // 预热将内置音频解压到私有目录，确保极速秒播
        extractAssetsAsync();
    }

    public static synchronized VehicleVoicePlayer getInstance(Context context) {
        if (instance == null) {
            instance = new VehicleVoicePlayer(context);
        }
        return instance;
    }

    private void extractAssetsAsync() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File voiceDir = new File(context.getFilesDir(), "voices");
                    if (!voiceDir.exists()) voiceDir.mkdirs();
                    String[] list = context.getAssets().list("audio");
                    if (list != null) {
                        for (String f : list) {
                            File dest = new File(voiceDir, f);
                            if (!dest.exists() || dest.length() == 0) {
                                try (InputStream in = context.getAssets().open("audio/" + f);
                                     FileOutputStream out = new FileOutputStream(dest)) {
                                    byte[] buf = new byte[16 * 1024];
                                    int len;
                                    while ((len = in.read(buf)) > 0) {
                                        out.write(buf, 0, len);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to pre-extract assets: " + e.getMessage());
                }
            }
        }).start();
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
                    } catch (Exception ignored) {}

                    TextToSpeech.OnInitListener listener = new TextToSpeech.OnInitListener() {
                        @Override
                        public void onInit(int status) {
                            if (status == TextToSpeech.SUCCESS && tts != null) {
                                int res = tts.setLanguage(Locale.CHINESE);
                                if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                                    ttsReady = true;
                                    tts.setSpeechRate(1.05f);
                                    Log.i(TAG, "TextToSpeech init ready!");
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

    public void stopCurrentVoice() {
        playSessionId.incrementAndGet();
        synchronized (playerLock) {
            if (currentMediaPlayer != null) {
                try {
                    if (currentMediaPlayer.isPlaying()) {
                        currentMediaPlayer.stop();
                    }
                    currentMediaPlayer.release();
                } catch (Exception ignored) {}
                currentMediaPlayer = null;
            }
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (tts != null && tts.isSpeaking()) {
                        tts.stop();
                    }
                } catch (Exception ignored) {}
            }
        });
        abandonAudioFocus();
        restoreVolumeAfterPlay();
    }

    public void play(String voiceFileName, final String fallbackText) {
        // 核心优先判定：座舱车身语音播报总开关 (voice_master_switch)
        try {
            SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            boolean masterSwitch = prefs.getBoolean("voice_master_switch", true);
            if (!masterSwitch) {
                Log.i(TAG, "Voice master switch is OFF, dropping audio: " + voiceFileName);
                return;
            }
        } catch (Exception ignored) {}

        stopCurrentVoice();

        // 0. 用户自定义台词优先
        try {
            SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            String rawName = voiceFileName.endsWith(".mp3") ? voiceFileName.substring(0, voiceFileName.length() - 4) : voiceFileName;
            String customText = prefs.getString("custom_text_" + voiceFileName, "");
            if (customText == null || customText.trim().isEmpty()) {
                customText = prefs.getString("custom_voice_text_" + rawName, "");
            }
            if (customText == null || customText.trim().isEmpty()) {
                customText = prefs.getString("custom_voice_text_" + voiceFileName, "");
            }
            if (customText != null && !customText.trim().isEmpty()) {
                Log.i(TAG, "Playing custom TTS text: " + customText);
                speakText(customText.trim(), voiceFileName);
                return;
            }
        } catch (Exception ignored) {}

        // 1. 用户指定自定义音频文件路径
        try {
            SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            String customPath = prefs.getString("custom_voice_" + voiceFileName, "");
            if (customPath != null && !customPath.isEmpty()) {
                File customPrefFile = new File(customPath);
                if (customPrefFile.exists() && customPrefFile.length() > 0) {
                    Log.i(TAG, "Playing custom user audio file: " + customPath);
                    playAudioFile(customPrefFile, voiceFileName);
                    return;
                }
            }
        } catch (Exception ignored) {}

        // 2. 外部独立专属座舱语音目录优先 (/sdcard/GeelyPilot/voices/ 物理隔离，永不受 Download 清空影响)
        File pilotVoicesDir = new File(Environment.getExternalStorageDirectory(), "GeelyPilot/voices");
        if (!pilotVoicesDir.exists()) {
            try { pilotVoicesDir.mkdirs(); } catch (Exception ignored) {}
        }
        File customFilePilot = new File(pilotVoicesDir, voiceFileName);
        String activeTheme = "";
        try {
            SharedPreferences sp = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            activeTheme = sp.getString("active_voice_theme", "");
        } catch (Exception ignored) {}
        File customFileThemeAudio = (!activeTheme.isEmpty()) ? new File(pilotVoicesDir, activeTheme + "/audio/" + voiceFileName) : null;
        File customFileTheme = (!activeTheme.isEmpty()) ? new File(pilotVoicesDir, activeTheme + "/" + voiceFileName) : null;

        File customFile0 = new File(SystemUtils.getAppDownloadDir(), "语音主题包/" + voiceFileName);
        File customFile1 = new File(SystemUtils.getAppDownloadDir(), voiceFileName);
        File customFile2 = new File("/sdcard/Music/" + voiceFileName);

        File targetFile = (customFileThemeAudio != null && customFileThemeAudio.exists() && customFileThemeAudio.length() > 0) ? customFileThemeAudio :
                          ((customFileTheme != null && customFileTheme.exists() && customFileTheme.length() > 0) ? customFileTheme :
                          ((customFilePilot.exists() && customFilePilot.length() > 0) ? customFilePilot :
                          ((customFile0.exists() && customFile0.length() > 0) ? customFile0 :
                          ((customFile1.exists() && customFile1.length() > 0) ? customFile1 :
                          ((customFile2.exists() && customFile2.length() > 0) ? customFile2 : null)))));

        if (targetFile != null && targetFile.length() > 0) {
            Log.i(TAG, "Playing external audio file: " + targetFile.getAbsolutePath());
            playAudioFile(targetFile, voiceFileName);
            return;
        }

        // 3. 内置音频资产播放（解压至应用专有目录播放，100% 免疫 FD 异常）
        File localAssetFile = getLocalAssetFile(voiceFileName);
        if (localAssetFile != null && localAssetFile.exists() && localAssetFile.length() > 0) {
            Log.i(TAG, "Playing local asset audio: " + localAssetFile.getAbsolutePath());
            playAudioFile(localAssetFile, voiceFileName);
            return;
        }

        // 4. 兜底调用系统 TTS
        Log.i(TAG, "Fallback speaking TTS: " + fallbackText);
        speakText(fallbackText, voiceFileName);
    }

    private File getLocalAssetFile(String voiceFileName) {
        try {
            File voiceDir = new File(context.getFilesDir(), "voices");
            if (!voiceDir.exists()) voiceDir.mkdirs();
            File target = new File(voiceDir, voiceFileName);
            if (target.exists() && target.length() > 0) {
                return target;
            }

            // 同步从 assets 提取
            try (InputStream in = context.getAssets().open("audio/" + voiceFileName);
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[16 * 1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
            if (target.exists() && target.length() > 0) {
                return target;
            }
        } catch (Exception e) {
            Log.d(TAG, "Asset " + voiceFileName + " not in assets folder: " + e.getMessage());
        }
        return null;
    }

    private void playAudioFile(final File file, final String voiceType) {
        final int sessionId = playSessionId.incrementAndGet();
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (playSessionId.get() != sessionId) return;
                MediaPlayer mp = null;
                try {
                    requestAudioFocus(voiceType);
                    applyVolumeOffsetBeforePlay(voiceType);
                    mp = new MediaPlayer();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mp.setAudioAttributes(getVoiceAudioAttributes(context, voiceType));
                    } else {
                        boolean isRev = (voiceType != null && (voiceType.contains("gear_r") || voiceType.contains("reverse")));
                        mp.setAudioStreamType(isRev ? AudioManager.STREAM_NOTIFICATION : AudioManager.STREAM_MUSIC);
                    }
                    mp.setDataSource(file.getAbsolutePath());
                    mp.prepare();
                    if (playSessionId.get() != sessionId) {
                        try { mp.release(); } catch (Exception ignored) {}
                        abandonAudioFocus();
                        restoreVolumeAfterPlay();
                        return;
                    }
                    mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mediaPlayer) {
                            synchronized (playerLock) {
                                if (currentMediaPlayer == mediaPlayer) currentMediaPlayer = null;
                            }
                            abandonAudioFocus();
                            restoreVolumeAfterPlay();
                            try { mediaPlayer.release(); } catch (Exception ignored) {}
                        }
                    });
                    mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                        @Override
                        public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                            synchronized (playerLock) {
                                if (currentMediaPlayer == mediaPlayer) currentMediaPlayer = null;
                            }
                            abandonAudioFocus();
                            restoreVolumeAfterPlay();
                            try { mediaPlayer.release(); } catch (Exception ignored) {}
                            return true;
                        }
                    });
                    synchronized (playerLock) {
                        currentMediaPlayer = mp;
                    }
                    try {
                        SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                        float speed = prefs.getFloat("voice_playback_speed", 1.0f);
                        if (speed != 1.0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            android.media.PlaybackParams params = mp.getPlaybackParams();
                            params.setSpeed(speed);
                            params.setPitch(1.0f);
                            mp.setPlaybackParams(params);
                        }
                    } catch (Exception ignored) {}
                    mp.start();
                    Log.i(TAG, "MediaPlayer started successfully for " + file.getName());
                } catch (Exception e) {
                    Log.e(TAG, "playAudioFile failed: " + e.getMessage(), e);
                    abandonAudioFocus();
                    restoreVolumeAfterPlay();
                    if (mp != null) {
                        try { mp.release(); } catch (Exception ignored) {}
                    }
                    synchronized (playerLock) {
                        if (currentMediaPlayer == mp) currentMediaPlayer = null;
                    }
                }
            }
        }).start();
    }

    private void playAudioFile(final File file) {
        playAudioFile(file, null);
    }

    public void playCustomFile(final String path) {
        if (path == null || path.trim().isEmpty()) return;
        try {
            File f = new File(path.trim());
            if (f.exists() && f.length() > 0) {
                Log.i(TAG, "Playing explicit custom audio file: " + f.getAbsolutePath());
                playAudioFile(f, f.getName());
            } else {
                speakText("指定自定义音频文件不存在");
            }
        } catch (Exception e) {
            Log.e(TAG, "playCustomFile error: " + e.getMessage());
        }
    }

    public void speakText(final String text, final String voiceType) {
        if (text == null || text.trim().isEmpty()) return;
        // 核心优先判定：座舱车身语音播报总开关 (voice_master_switch)
        try {
            SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            boolean masterSwitch = prefs.getBoolean("voice_master_switch", true);
            if (!masterSwitch) {
                Log.i(TAG, "Voice master switch is OFF, dropping TTS speak: " + text);
                return;
            }
        } catch (Exception ignored) {}

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    requestAudioFocus(voiceType);
                    applyVolumeOffsetBeforePlay(voiceType);
                    if (tts != null && ttsReady) {
                        try {
                            SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                            float speed = prefs.getFloat("voice_playback_speed", 1.0f);
                            tts.setSpeechRate(speed);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                tts.setAudioAttributes(getVoiceAudioAttributes(context, voiceType));
                            }
                        } catch (Exception ignored) {}
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_" + System.currentTimeMillis());
                        } else {
                            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
                        }
                    } else {
                        Log.w(TAG, "TTS engine not ready, queuing speech or retrying");
                    }
                    mainHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            abandonAudioFocus();
                            restoreVolumeAfterPlay();
                        }
                    }, 3000);
                } catch (Exception e) {
                    Log.w(TAG, "speakText error: " + e.getMessage());
                    abandonAudioFocus();
                    restoreVolumeAfterPlay();
                }
            }
        });
    }

    public void speakText(final String text) {
        speakText(text, null);
    }

    private void requestAudioFocus(String voiceType) {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes attrs = getVoiceAudioAttributes(context, voiceType);
                AudioFocusRequest req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(attrs)
                        .build();
                audioManager.requestAudioFocus(req);
                activeFocusRequest = req;
            } else {
                boolean isRev = (voiceType != null && (voiceType.contains("gear_r") || voiceType.contains("reverse")));
                int stream = isRev ? AudioManager.STREAM_NOTIFICATION : AudioManager.STREAM_MUSIC;
                audioManager.requestAudioFocus(null, stream, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            }
        } catch (Exception ignored) {}
    }

    private void requestAudioFocus() {
        requestAudioFocus(null);
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activeFocusRequest instanceof AudioFocusRequest) {
                audioManager.abandonAudioFocusRequest((AudioFocusRequest) activeFocusRequest);
                activeFocusRequest = null;
            } else {
                audioManager.abandonAudioFocus(null);
            }
        } catch (Exception ignored) {}
    }
}
