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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.json.JSONArray;
import org.json.JSONObject;

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

        // 倒车挡 (R 挡) 核心保护：强制走系统通知通道 (USAGE_NOTIFICATION_EVENT)
        // 彻底免去导航引导流 (USAGE_ASSISTANCE_NAVIGATION_GUIDANCE) 沉重的淡入淡出与系统排队，瞬发且抗倒车衰减！
        boolean isReverse = (voiceType != null && (voiceType.contains("gear_r") || voiceType.contains("reverse") || voiceType.contains("倒车")));

        if (isReverse || "notification".equals(channel)) {
            builder.setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT);
        } else if ("nav".equals(channel)) {
            builder.setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
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
    private Runnable focusReleaseRunnable = null;

    // ---- TTS 冷启动排队补播：点火后引擎未就绪时，缓存最新一条待播，就绪后自动补出（绝不丢首条语音）----
    private volatile String pendingText = null;
    private volatile String pendingVoiceType = null;
    private volatile long pendingTextAt = 0L;
    private static final long PENDING_TTL_MS = 8000L;      // 超出 8 秒的过期台词坚决丢弃，防串音
    private volatile long lastInitAttemptAt = 0L;          // ensureTtsReady 重试节流时间戳
    private static final long INIT_RETRY_INTERVAL_MS = 3000L; // 节流：最短 3 秒重试一次

    private synchronized void applyVolumeOffsetBeforePlay(String voiceType) {
        if (audioManager == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            int offset = prefs.getInt("voice_volume_offset", 0);

            boolean isReverse = (voiceType != null && (voiceType.contains("gear_r") || voiceType.contains("reverse") || voiceType.contains("倒车")));
            String channel = prefs.getString("voice_audio_channel", "music");
            int stream = (isReverse || "nav".equals(channel) || "notification".equals(channel))
                    ? AudioManager.STREAM_NOTIFICATION
                    : AudioManager.STREAM_MUSIC;

            int currentVol = audioManager.getStreamVolume(stream);
            int maxVol = audioManager.getStreamMaxVolume(stream);
            int targetVol = Math.max(0, Math.min(maxVol, currentVol + offset));

            // 倒车挡防衰减智能补偿：通知通道动态补偿 +N 格 (车主可在倒车声效弹窗自由调节，默认+6)
            if (isReverse) {
                int boost = prefs.getInt("reverse_volume_boost", 6);
                int boostedVol = Math.max((int) (maxVol * 0.80f), currentVol + boost);
                targetVol = Math.max(1, Math.min(maxVol, boostedVol));
            }

            if (targetVol != currentVol && restoreVolumeAfterPlay < 0) {
                restoreVolumeAfterPlay = currentVol;
                originalStreamType = stream;
                audioManager.setStreamVolume(stream, targetVol, 0);
                Log.i(TAG, "Applied voice volume offset: " + offset + " (vol: " + currentVol + " -> " + targetVol + ", stream: " + stream + ")");
                AppLogger.i("语音播报", "音量动态补偿生效: 当前=" + currentVol + " -> 目标=" + targetVol + " (" + (offset >= 0 ? "+" + offset : offset) + "格, 通道=" + (stream == AudioManager.STREAM_NOTIFICATION ? "通知/系统" : "媒体") + ")");
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
                                    // 引擎就绪瞬间，立即补播排队中的首条语音（点火即挂挡场景零丢失）
                                    flushPendingSpeech();
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
        if (focusReleaseRunnable != null) {
            mainHandler.removeCallbacks(focusReleaseRunnable);
            focusReleaseRunnable = null;
        }
        synchronized (playerLock) {
            if (currentMediaPlayer != null) {
                try {
                    if (currentMediaPlayer.isPlaying()) {
                        currentMediaPlayer.stop();
                    }
                } catch (Exception ignored) {}
                try {
                    currentMediaPlayer.reset();
                } catch (Exception ignored) {}
                try {
                    currentMediaPlayer.release();
                } catch (Exception ignored) {}
                currentMediaPlayer = null;
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                if (tts != null && tts.isSpeaking()) {
                    tts.stop();
                }
            } catch (Exception ignored) {}
        } else {
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
        }
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
            AppLogger.i("语音播报", "触发播放[主题音频]: " + targetFile.getName() + " (" + fallbackText + ")");
            playAudioFile(targetFile, voiceFileName);
            return;
        }

        // 3. 内置音频资产播放（解压至应用专有目录播放，100% 免疫 FD 异常）
        File localAssetFile = getLocalAssetFile(voiceFileName);
        if (localAssetFile != null && localAssetFile.exists() && localAssetFile.length() > 0) {
            Log.i(TAG, "Playing local asset audio: " + localAssetFile.getAbsolutePath());
            AppLogger.i("语音播报", "触发播放[官方原声]: " + voiceFileName + " (" + fallbackText + ")");
            playAudioFile(localAssetFile, voiceFileName);
            return;
        }

        // 4. 兜底调用系统 TTS
        Log.i(TAG, "Fallback speaking TTS: " + fallbackText);
        AppLogger.i("语音播报", "触发朗读[小爱TTS]: " + fallbackText);
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
                    synchronized (playerLock) {
                        if (playSessionId.get() != sessionId) {
                            try { mp.release(); } catch (Exception ignored) {}
                            abandonAudioFocus();
                            restoreVolumeAfterPlay();
                            return;
                        }
                        currentMediaPlayer = mp;
                    }
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
                        synchronized (playerLock) {
                            if (currentMediaPlayer == mp) currentMediaPlayer = null;
                        }
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
                    if (focusReleaseRunnable != null) {
                        mainHandler.removeCallbacks(focusReleaseRunnable);
                        focusReleaseRunnable = null;
                    }
                    requestAudioFocus(voiceType);
                    applyVolumeOffsetBeforePlay(voiceType);
                    SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
                    if (tts != null && ttsReady) {
                        try {
                            float speed = prefs.getFloat("voice_playback_speed", 1.0f);
                            tts.setSpeechRate(speed);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                tts.setAudioAttributes(getVoiceAudioAttributes(context, voiceType));
                            }
                        } catch (Exception ignored) {}
                        boolean isRev = (voiceType != null && (voiceType.contains("gear_r") || voiceType.contains("reverse") || voiceType.contains("倒车")));
                        String channel = prefs.getString("voice_audio_channel", "music");
                        int streamType = (isRev || "nav".equals(channel) || "notification".equals(channel))
                                ? AudioManager.STREAM_NOTIFICATION
                                : AudioManager.STREAM_MUSIC;
                        android.os.Bundle ttsParams = new android.os.Bundle();
                        ttsParams.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, streamType);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            tts.speak(text, TextToSpeech.QUEUE_FLUSH, ttsParams, "voice_" + System.currentTimeMillis());
                        } else {
                            java.util.HashMap<String, String> map = new java.util.HashMap<>();
                            map.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(streamType));
                            tts.speak(text, TextToSpeech.QUEUE_FLUSH, map);
                        }
                    } else {
                        // 冷启动兜底：TTS 未就绪时缓存最新一条待播台词（覆盖旧缓存），并触发一次重试预热。
                        // 引擎 onInit 成功后会立即 flushPendingSpeech() 补出，绝不丢失点火后首条语音。
                        pendingText = text;
                        pendingVoiceType = voiceType;
                        pendingTextAt = System.currentTimeMillis();
                        ensureTtsReady();
                        Log.w(TAG, "TTS not ready, buffered pending speech (retrying warmup): " + text);
                    }
                    focusReleaseRunnable = new Runnable() {
                        @Override
                        public void run() {
                            abandonAudioFocus();
                            restoreVolumeAfterPlay();
                            focusReleaseRunnable = null;
                        }
                    };
                    mainHandler.postDelayed(focusReleaseRunnable, 2200);
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

    /**
     * TTS 就绪后立即补播排队中的首条语音（点火即挂挡场景零丢失）。
     * 由 onInit 成功回调调用，也在 ensureTtsReady 重试成功后调用。
     */
    private void flushPendingSpeech() {
        final String text = pendingText;
        final String voiceType = pendingVoiceType;
        final long at = pendingTextAt;
        if (text == null || text.trim().isEmpty()) return;
        if (System.currentTimeMillis() - at > PENDING_TTL_MS) {
            // 过期台词（超 8 秒）坚决丢弃，防止点火瞬间排队、半天后串音
            pendingText = null;
            pendingVoiceType = null;
            return;
        }
        pendingText = null;
        pendingVoiceType = null;
        pendingTextAt = 0L;
        if (tts == null || !ttsReady) return;
        Log.i(TAG, "Flushing pending buffered speech: " + text);
        speakText(text, voiceType);
    }

    /**
     * TTS 引擎健康检查与重试预热（QQ 音乐解锁即预载同款）。
     * 解锁/上电广播触发时应主动调用一次，若引擎未就绪则带节流重试 init，
     * 确保点火后第一条语音无需等待引擎冷启动。
     * 该方法必须运行在主线程（TextToSpeech 构造要求），内部已用 mainHandler 包裹。
     */
    public void ensureTtsReady() {
        if (tts != null && ttsReady) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (tts != null && ttsReady) return;
                long now = System.currentTimeMillis();
                // 节流：最短 3 秒重试一次，避免引擎起不来的车机上空转刷屏
                if (now - lastInitAttemptAt < INIT_RETRY_INTERVAL_MS) return;
                lastInitAttemptAt = now;
                try {
                    if (tts != null && !ttsReady) {
                        // 已构造过但未就绪：释放旧实例重新绑定（车机早期小爱引擎未起时的兜底）
                        try { tts.shutdown(); } catch (Throwable ignored) {}
                        tts = null;
                    }
                    initTts();
                    Log.i(TAG, "ensureTtsReady: TTS re-init triggered");
                } catch (Exception e) {
                    Log.w(TAG, "ensureTtsReady re-init failed: " + e.getMessage());
                }
            }
        });
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

    public static File getVoicesRootDir() {
        File dir = new File(Environment.getExternalStorageDirectory(), "GeelyPilot/voices");
        if (!dir.exists()) {
            try { dir.mkdirs(); } catch (Exception ignored) {}
        }
        return dir;
    }

    public static boolean isVoicePackZip(File zipFile) {
        if (zipFile == null || !zipFile.exists() || !zipFile.getName().toLowerCase().endsWith(".zip")) {
            return false;
        }
        ZipInputStream zis = null;
        try {
            zis = new ZipInputStream(new FileInputStream(zipFile));
            ZipEntry entry;
            int audioCount = 0;
            boolean hasReadme = false;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase();
                if (name.endsWith(".mp3") || name.endsWith(".wav")) {
                    audioCount++;
                    if (name.contains("gear_") || name.contains("door_") || name.contains("mode_") || name.contains("trunk_")) {
                        zis.close();
                        return true;
                    }
                } else if (name.endsWith("readme.txt") || name.endsWith("manifest.json")) {
                    hasReadme = true;
                }
                zis.closeEntry();
            }
            return (audioCount >= 2) || (audioCount >= 1 && hasReadme);
        } catch (Exception ignored) {
            return false;
        } finally {
            if (zis != null) {
                try { zis.close(); } catch (Exception ignored) {}
            }
        }
    }

    public static int extractVoiceZip(File zipFile, String themeName) {
        if (zipFile == null || !zipFile.exists()) return -1;
        if (themeName == null || themeName.trim().isEmpty()) {
            String fName = zipFile.getName();
            int dot = fName.lastIndexOf('.');
            themeName = (dot > 0) ? fName.substring(0, dot) : fName;
        }
        File targetDir = new File(getVoicesRootDir(), themeName.trim());
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        ZipInputStream zis = null;
        int count = 0;
        try {
            zis = new ZipInputStream(new FileInputStream(zipFile));
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String fullPath = entry.getName();
                String fileName = new File(fullPath).getName();
                String lower = fileName.toLowerCase();
                if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".txt") || lower.endsWith(".json") || lower.endsWith(".png") || lower.endsWith(".jpg")) {
                    File outFile = new File(targetDir, fileName);
                    FileOutputStream fos = new FileOutputStream(outFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    if (lower.endsWith(".mp3") || lower.endsWith(".wav")) {
                        count++;
                    }
                }
                zis.closeEntry();
            }
            return count;
        } catch (Exception e) {
            Log.e(TAG, "extractVoiceZip error: " + e.getMessage());
            return -1;
        } finally {
            if (zis != null) {
                try { zis.close(); } catch (Exception ignored) {}
            }
        }
    }

    public static String listInstalledThemesJson(Context context) {
        try {
            File root = getVoicesRootDir();
            File[] files = root.listFiles();
            JSONObject result = new JSONObject();
            SharedPreferences sp = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            String active = sp.getString("active_voice_theme", "");
            result.put("activeTheme", active);

            JSONArray list = new JSONArray();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory() && !f.getName().startsWith(".")) {
                        JSONObject t = new JSONObject();
                        t.put("id", f.getName());
                        t.put("name", f.getName());
                        t.put("path", f.getAbsolutePath());
                        File[] audios = f.listFiles();
                        int audioCount = 0;
                        boolean hasPreview = false;
                        JSONArray audioNames = new JSONArray();
                        if (audios != null) {
                            for (File af : audios) {
                                String n = af.getName().toLowerCase();
                                if (n.endsWith(".mp3") || n.endsWith(".wav")) {
                                    audioCount++;
                                    audioNames.put(af.getName());
                                    if (n.equals("preview" + ".mp3") || n.equals("sample" + ".mp3")) {
                                        hasPreview = true;
                                    }
                                }
                            }
                        }
                        t.put("count", audioCount);
                        t.put("hasPreview", hasPreview);
                        t.put("audioFiles", audioNames);
                        list.put(t);
                    }
                }
            }
            result.put("themes", list);
            return result.toString();
        } catch (Exception e) {
            return "{\"activeTheme\":\"\",\"themes\":[]}";
        }
    }

    public static boolean setActiveTheme(Context context, String themeName) {
        try {
            SharedPreferences sp = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            return sp.edit().putString("active_voice_theme", themeName == null ? "" : themeName.trim()).commit();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean deleteTheme(Context context, String themeName) {
        if (themeName == null || themeName.trim().isEmpty()) return false;
        try {
            SharedPreferences sp = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            String active = sp.getString("active_voice_theme", "");
            if (themeName.trim().equals(active)) {
                sp.edit().putString("active_voice_theme", "").commit();
            }
            File dir = new File(getVoicesRootDir(), themeName.trim());
            return deleteRecursive(dir);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean deleteRecursive(File f) {
        if (f == null || !f.exists()) return false;
        if (f.isDirectory()) {
            File[] subs = f.listFiles();
            if (subs != null) {
                for (File s : subs) {
                    deleteRecursive(s);
                }
            }
        }
        return f.delete();
    }
}
