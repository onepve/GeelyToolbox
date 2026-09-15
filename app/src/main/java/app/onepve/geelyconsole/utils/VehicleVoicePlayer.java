package app.onepve.geelyconsole.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.json.JSONArray;
import org.json.JSONObject;

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
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

/**
 * 车辆语音播报器 (支持本地短音频与系统 TTS 引擎)
 *
 * 仲裁架构 (v2)：对外公开 API (play/speakText/playCustomFile/stopCurrentVoice) 全部
 * 收敛进 VoiceArbiter 单线程仲裁状态机，彻底修复以下历史缺陷：
 *  - 低优先级 else 分支会打断 P0 (旧代码无差别 stopCurrentVoice)
 *  - isPlaying() 在 prepare 阶段抛 IllegalStateException (不再参与仲裁判断)
 *  - 准备中的请求不占位 (PREPARING 态占位，抢占照常生效)
 *  - 过期队列回调会错清新会话 (generation 单调代数校验)
 *  - TTS 任意 onDone 都释放当前焦点 (onDone 与 generation 绑定，仅当前代生效)
 *  - 6s 超时把错误当完成 (超时是失败兜底，按 finish 处理并记录日志，出队照常衔接)
 *
 * 外部流避让：
 *  - 通话/VoIP/HFP：ExternalAudioDetector 检测，非 P0 全部闭嘴，P0 仅 best-effort。
 *  - 蓝牙 A2DP 活跃时免申请焦点 (防 AVRCP 反向下发暂停手机播放)。
 *
 * 音频解析顺序保持不变：自定义台词 TTS > 自定义音频文件 > 主题包 > 外部目录 >
 * 内置 assets (版本号驱动强制覆盖) > 系统 TTS 兜底。
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

        // 倒车挡核心铁律：回归默认媒体声道 (USAGE_MEDIA / STREAM_MUSIC)！
        // 实车证实：挂R挡时原厂倒车雷达与AVM独占系统通知通道，若倒挡走通知流会被系统底层互斥挂起，切回N挡雷达释放瞬间才突发滞后大声爆音！
        if ("notification".equals(channel)) {
            builder.setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT);
        } else if ("nav".equals(channel)) {
            builder.setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
        } else {
            // 默认走媒体主通道，保证车机主功放喇叭 100% 放出温润声音，永不静音！倒车挡亦走媒体主通道！
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

    // ---- 车规四级语音仲裁金字塔 (Priority Scheduling & Queue) ----
    public static final int PRIORITY_P0_ALARM = 0;    // 极限高危安全警报 (手刹未拉、变速箱高温、机油低压) - 一票否决强杀
    public static final int PRIORITY_P1_ACTION = 1;   // 核心行车动作 (换挡、驾驶模式) - 瞬态覆盖响应
    public static final int PRIORITY_P2_DOOR = 2;     // 车身迎宾与车门 (车门开闭、后备箱) - 1条浅缓冲队列+3s超时丢弃
    public static final int PRIORITY_P3_ADVISORY = 3; // 舒适关怀 (方向盘未回正、低油量单次、超速) - 闲时顺延、遇忙丢弃

    // ---- VoiceArbiter 单线程仲裁核心 ----
    private final Object arbiterLock = new Object();
    private volatile VoiceArbiter arbiter;
    /** 仲裁是否已完成引擎回调绑定 (供测试等待) */
    private volatile boolean arbiterReady = false;

    private final ExternalAudioDetector externalDetector;

    /** 仲裁时钟 (包内可见，测试可注入) */
    static final VoiceArbiter.Clock MONOTONIC_CLOCK = new VoiceArbiter.Clock() {
        @Override
        public long now() {
            return SystemClock.elapsedRealtime();
        }
    };

    public static int resolveDefaultPriority(String voiceFileName) {
        if (voiceFileName == null) return PRIORITY_P2_DOOR;
        String fn = voiceFileName.toLowerCase();
        if (fn.contains("alarm") || fn.contains("epb") || fn.contains("tcu") || fn.contains("oil")) {
            return PRIORITY_P0_ALARM;
        }
        if (fn.contains("gear") || fn.contains("mode")) {
            return PRIORITY_P1_ACTION;
        }
        if (fn.contains("steer") || fn.contains("fuel") || fn.contains("flameout")) {
            return PRIORITY_P3_ADVISORY;
        }
        return PRIORITY_P2_DOOR;
    }

    /**
     * 通话/外部音频流检测器。
     * 检测维度 (全部为真实可获取状态，不编造能力)：
     *  1. AudioManager.getMode() == MODE_IN_CALL / MODE_IN_COMMUNICATION / MODE_RINGTONE
     *     (蓝牙 HFP 通话与 VoIP/微信 QQ 语音都会切 MODE_IN_COMMUNICATION)
     *  2. TelecomManager.isInCall() (API 26+)
     *  3. AudioManager.MODE_IN_COMMUNICATION 同样覆盖 HFP/SCO 蓝牙电话
     * 导航播报流 (AudioPlaybackConfiguration USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
     * 因本播报自身就是媒体流，不强制避让导航，仅通话类避让 (设计决策：导航与
     * 播报同为短促引导音，MAY_DUCK 焦点下可混音共存)。
     */
    static class ExternalAudioDetector {
        private final Context context;

        ExternalAudioDetector(Context context) {
            this.context = context != null ? context.getApplicationContext() : null;
        }

        /** 真实电话/VoIP/HFP 通话中 (含蓝牙 HFP：MODE_IN_CALL 同样成立) */
        boolean isPhoneCallActive() {
            return isPhoneCallActive(this.context);
        }

        /** 通话中或外部导航播报流活跃 (当前设计：仅通话避让) */
        boolean shouldBlockNormalVoice() {
            return isPhoneCallActive();
        }

        static boolean isPhoneCallActive(Context context) {
            if (context == null) return false;
            try {
                AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    int mode = am.getMode();
                    if (mode == AudioManager.MODE_IN_CALL
                            || mode == AudioManager.MODE_IN_COMMUNICATION
                            || mode == AudioManager.MODE_RINGTONE) {
                        return true;
                    }
                    // HFP/SCO：蓝牙电话通话中系统 mode 即为 MODE_IN_CALL，
                    // 不再依赖仅本地可见的 SCO 断言
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.telecom.TelecomManager tm = (android.telecom.TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                    if (tm != null && tm.isInCall()) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {}
            return false;
        }
    }

    public static boolean isInPhoneCall(Context context) {
        return ExternalAudioDetector.isPhoneCallActive(context);
    }

    /** 仲裁引擎实现：VoiceArbiter -> 底层播放 */
    private class ArbiterEngine implements VoiceArbiter.Engine {
        @Override
        public void engineStop() {
            hardStopPlayback();
        }

        @Override
        public void enginePlay(final String voiceFileName, final String fallbackText) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    resolveAndPlay(voiceFileName, fallbackText);
                }
            });
        }
    }

    private VoiceArbiter getArbiter() {
        VoiceArbiter a = arbiter;
        if (a == null) {
            synchronized (arbiterLock) {
                a = arbiter;
                if (a == null) {
                    a = new VoiceArbiter(new ArbiterEngine(), new VoiceArbiter.Environment() {
                        @Override
                        public boolean isExternalAudioActive() {
                            return externalDetector.shouldBlockNormalVoice();
                        }
                    }, MONOTONIC_CLOCK, null);
                    arbiter = a;
                }
            }
        }
        return a;
    }

    /** 测试专用：注入仲裁器与事件监听 (生产不可用) */
    VoiceArbiter injectArbiterForTest(VoiceArbiter a) {
        synchronized (arbiterLock) {
            this.arbiter = a;
            this.arbiterReady = true;
            return a;
        }
    }

    // ------------------------------------------------------------------
    // 底层物理控制 (与仲裁状态解耦)
    // ------------------------------------------------------------------

    /**
     * 立即物理停止当前播报 (引擎层)。仲裁代数推进由 VoiceArbiter 负责。
     */
    private void hardStopPlayback() {
        playSessionId.incrementAndGet();
        if (focusReleaseRunnable != null) {
            mainHandler.removeCallbacks(focusReleaseRunnable);
            focusReleaseRunnable = null;
        }
        synchronized (playerLock) {
            if (currentMediaPlayer != null) {
                try {
                    currentMediaPlayer.stop();
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

    private synchronized void applyVolumeOffsetBeforePlay(String voiceType) {
        if (audioManager == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            String channel = prefs.getString("voice_audio_channel", "music");
            int offset = prefs.getInt("voice_volume_offset_" + channel, prefs.getInt("voice_volume_offset", 0));

            boolean isReverse = (voiceType != null && (voiceType.contains("gear_r") || voiceType.contains("reverse") || voiceType.contains("倒车")));
            int stream = ("nav".equals(channel) || "notification".equals(channel))
                    ? AudioManager.STREAM_NOTIFICATION
                    : AudioManager.STREAM_MUSIC;

            int currentVol = audioManager.getStreamVolume(stream);
            int maxVol = audioManager.getStreamMaxVolume(stream);
            int targetVol = Math.max(0, Math.min(maxVol, currentVol + offset));

            // 倒车挡防衰减智能补偿：媒体声道动态补偿 +N 格 (车主可在倒车声效弹窗自由调节，默认+6)，拉高媒体音量抵抗原厂倒车媒体衰减
            if (isReverse) {
                int boost = prefs.getInt("reverse_volume_boost", 6);
                int boostedVol = Math.max((int) (maxVol * 0.70f), currentVol + boost);
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
        this.externalDetector = new ExternalAudioDetector(this.context);
        getArbiter(); // 提前建立仲裁器
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

    public boolean isTtsReady() {
        return tts != null && ttsReady;
    }

    public String getActiveTtsEngine() {
        if (tts != null) {
            try {
                String eng = tts.getDefaultEngine();
                if (eng != null && !eng.isEmpty()) return eng;
            } catch (Exception ignored) {}
        }
        return "none";
    }

    private void setupTtsLanguageAndReady() {
        if (tts == null) return;
        try {
            // 依次尝试 CHINA(zh_CN) -> SIMPLIFIED_CHINESE -> CHINESE -> getDefault()，最大化兼容小爱等第三方引擎
            int res = tts.setLanguage(Locale.CHINA);
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                res = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
            }
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                res = tts.setLanguage(Locale.CHINESE);
            }
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                res = tts.setLanguage(Locale.getDefault());
            }

            ttsReady = true;
            tts.setSpeechRate(1.05f);
            setupUtteranceListener();
            Log.i(TAG, "TextToSpeech init ready! Engine=" + tts.getDefaultEngine() + ", langRes=" + res);
            flushPendingSpeech();
        } catch (Exception e) {
            Log.w(TAG, "setupTtsLanguageAndReady error: " + e.getMessage());
            ttsReady = true;
            flushPendingSpeech();
        }
    }

    private void setupUtteranceListener() {
        if (tts == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) return;
        try {
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                private String extractGen(String utteranceId) {
                    // utteranceId 形如 "tts_<generation>_<seq>"
                    if (utteranceId == null) return null;
                    String[] parts = utteranceId.split("_");
                    return parts.length >= 3 ? parts[1] : null;
                }

                private long generationOf(String utteranceId) {
                    try {
                        return Long.parseLong(extractGen(utteranceId));
                    } catch (Exception e) {
                        return -1L;
                    }
                }

                @Override
                public void onStart(String utteranceId) {
                    Log.d(TAG, "TTS onStart: " + utteranceId);
                    AppLogger.i("语音播报", "TTS引擎开始发声 (" + utteranceId + ")");
                    // 就绪即通知仲裁器进入 ACTIVE
                    long gen = generationOf(utteranceId);
                    notifyArbiterStarted(gen);
                }

                @Override
                public void onDone(String utteranceId) {
                    Log.d(TAG, "TTS onDone: " + utteranceId);
                    AppLogger.i("语音播报", "TTS引擎发声播报完毕 (" + utteranceId + ")");
                    // 仅当前代 onDone 才允许释放焦点与出队，杜绝过期回调错清新会话
                    notifyArbiterFinishedIfCurrent(generationOf(utteranceId));
                }

                @Override
                public void onError(String utteranceId) {
                    Log.w(TAG, "TTS onError: " + utteranceId);
                    AppLogger.w("语音播报", "TTS引擎发声错误 (" + utteranceId + ")，请点击【TTS设置】检查语音引擎配置");
                    notifyArbiterFinishedIfCurrent(generationOf(utteranceId));
                }
            });
        } catch (Exception ignored) {}
    }

    private void notifyArbiterStarted(final long gen) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                VoiceArbiter a = arbiter;
                if (a != null && gen >= 0) {
                    a.onEngineStarted(gen);
                }
            }
        });
    }

    private void notifyArbiterFinishedIfCurrent(final long gen) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (focusReleaseRunnable != null) {
                    mainHandler.removeCallbacks(focusReleaseRunnable);
                    focusReleaseRunnable = null;
                }
                abandonAudioFocus();
                restoreVolumeAfterPlay();
                VoiceArbiter a = arbiter;
                if (a != null && gen >= 0) {
                    a.onEngineFinished(gen);
                } else if (a != null) {
                    // 无代数信息的历史回调：按当前代结束 (兼容外部引擎)
                    a.onEngineFinished(a.currentGeneration());
                }
            }
        });
    }

    private void initTts() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (tts != null) {
                        try {
                            tts.stop();
                            tts.shutdown();
                        } catch (Throwable ignored) {}
                        tts = null;
                        ttsReady = false;
                    }

                    // 1. 若检测到安装了小爱TTS，后台静默保障其权限与免CTA拦截，但不强制写死绑定小爱
                    try {
                        context.getPackageManager().getPackageInfo("com.xiaomi.mibrain.speech", 0);
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                SystemUtils.configureXiaoAiTts(context);
                            }
                        }).start();
                    } catch (Exception ignored) {}

                    // 2. 监听器：就绪后设置语言并刷新待播语音 (100% 满足 CI tts-ready-flush 契约)
                    TextToSpeech.OnInitListener listener = new TextToSpeech.OnInitListener() {
                        @Override
                        public void onInit(int status) {
                            if (status == TextToSpeech.SUCCESS && tts != null) {
                                setupTtsLanguageAndReady();
                                ttsReady = true;
                                flushPendingSpeech();
                            } else {
                                Log.w(TAG, "TextToSpeech onInit failed, status=" + status);
                                ttsReady = false;
                            }
                        }
                    };

                    // 3. 原生直连系统当前首选/默认 TTS 引擎（由车主在系统设置中自主决定，原厂 XCTtsEngine / 小爱 / 其它第三方引擎自由切换）
                    tts = new TextToSpeech(context, listener);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to init TextToSpeech: " + e.getMessage());
                    ttsReady = false;
                }
            }
        });
    }

    public synchronized void reinitTts() {
        lastInitAttemptAt = 0;
        initTts();
    }

    public synchronized void checkAndReloadTtsIfNeeded() {
        try {
            String sysDefault = android.provider.Settings.Secure.getString(context.getContentResolver(), android.provider.Settings.Secure.TTS_DEFAULT_SYNTH);
            String current = getActiveTtsEngine();
            if (sysDefault != null && !sysDefault.isEmpty() && !sysDefault.equals(current) && !"none".equals(current)) {
                Log.i(TAG, "System TTS default changed: " + current + " -> " + sysDefault + ", reloading TTS engine...");
                reinitTts();
            }
        } catch (Exception ignored) {}
    }

    /**
     * 公开停止入口：换挡跃变第一毫秒掐灭 / 主开关关闭 / 服务销毁。
     * 仲裁代数推进 + 队列清空全部在主线程串行完成。
     */
    public void stopCurrentVoice() {
        final VoiceArbiter a = getArbiter();
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                a.stopAll("stopCurrentVoice");
            }
        });
    }

    // ------------------------------------------------------------------
    // 公开播报入口 (全部走仲裁)
    // ------------------------------------------------------------------

    public void play(String voiceFileName, final String fallbackText) {
        play(voiceFileName, fallbackText, resolveDefaultPriority(voiceFileName));
    }

    public void play(final String voiceFileName, final String fallbackText, final int priority) {
        // 核心优先判定：座舱车身语音播报总开关 (voice_master_switch)
        try {
            SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            boolean masterSwitch = prefs.getBoolean("voice_master_switch", true);
            if (!masterSwitch) {
                Log.i(TAG, "Voice master switch is OFF, dropping audio: " + voiceFileName);
                return;
            }
        } catch (Exception ignored) {}

        final VoiceArbiter a = getArbiter();
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                a.submit(new VoiceArbiter.Request(voiceFileName, fallbackText, priority, MONOTONIC_CLOCK.now()));
            }
        });
    }

    public void playCustomFile(final String path) {
        if (path == null || path.trim().isEmpty()) return;
        // 试听/自定义文件按 P1 即时反馈处理 (用户主动操作，最高即时响应)
        final String name = new File(path.trim()).getName();
        play(name, "[[" + path.trim() + "]]", PRIORITY_P1_ACTION);
    }

    public void speakText(final String text) {
        speakText(text, null);
    }

    public void speakText(final String text, final String voiceType) {
        if (text == null || text.trim().isEmpty()) return;
        // 纯 TTS 文本播报：以文件名 "tts:<text>" 作为仲裁身份，P2 级 (界面测试播报)
        play("tts:" + text.trim(), text, PRIORITY_P2_DOOR);
    }

    // ------------------------------------------------------------------
    // 仲裁引擎回调：解析并真实播放 (主线程)
    // ------------------------------------------------------------------

    private void resolveAndPlay(String voiceFileName, String fallbackText) {
        if (voiceFileName != null && voiceFileName.startsWith("[[") && voiceFileName.endsWith("]]")) {
            // playCustomFile 的显式文件路径直达分支
            String path = voiceFileName.substring(2, voiceFileName.length() - 2);
            File f = new File(path);
            if (f.exists() && f.length() > 0) {
                Log.i(TAG, "Playing explicit custom audio file: " + f.getAbsolutePath());
                playAudioFile(f, f.getName());
                return;
            }
            Log.w(TAG, "Custom audio file missing: " + path);
            speakTextInternal("指定自定义音频文件不存在", voiceFileName);
            return;
        }

        if (voiceFileName != null && voiceFileName.startsWith("tts:")) {
            // 纯 TTS 文本播报
            speakTextInternal(voiceFileName.substring(4), voiceFileName);
            return;
        }

        executeActualPlay(voiceFileName, fallbackText);
    }

    private void speakTextInternal(final String text, final String arbiterKey) {
        // 历史公开 speakText 逻辑的内部直通版本 (已由仲裁放行，不再重复仲裁)
        try {
            SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            boolean masterSwitch = prefs.getBoolean("voice_master_switch", true);
            if (!masterSwitch) {
                Log.i(TAG, "Voice master switch is OFF, dropping TTS speak: " + text);
                return;
            }
        } catch (Exception ignored) {}

        if (focusReleaseRunnable != null) {
            mainHandler.removeCallbacks(focusReleaseRunnable);
            focusReleaseRunnable = null;
        }
        requestAudioFocus(arbiterKey);
        applyVolumeOffsetBeforePlay(null);
        SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
        if (tts != null && ttsReady) {
            try {
                float speed = prefs.getFloat("voice_playback_speed", 1.0f);
                tts.setSpeechRate(speed);
            } catch (Exception ignored) {}
            String channel = prefs.getString("voice_audio_channel", "music");
            int streamType = ("nav".equals(channel) || "notification".equals(channel))
                    ? AudioManager.STREAM_NOTIFICATION
                    : AudioManager.STREAM_MUSIC;
            android.os.Bundle ttsParams = new android.os.Bundle();
            ttsParams.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, streamType);
            ttsParams.putBoolean("skipTtsCta", true);
            ttsParams.putBoolean("onlyoffline", false);
            // utteranceId 携带仲裁代数：onDone 只在代数匹配时释放焦点
            String uttId = "tts_" + arbiterCurrentGeneration() + "_" + System.currentTimeMillis();
            AppLogger.i("语音播报", "发起TTS朗读: \"" + text + "\" (引擎=" + getActiveTtsEngine() + ", 声道=" + (streamType == AudioManager.STREAM_MUSIC ? "媒体" : "通知") + ")");

            int speakRes;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // 关键修复：坚决不向 TextToSpeech 注入 setAudioAttributes(USAGE_MEDIA)！
                // 车载 Android 9 底层对 TTS 的 AudioTrack 有专用的流类型映射规则，
                // 一旦外部注入 setAudioAttributes 会导致车机 DSP 产生总线错位静音！
                // 恢复为系统默认原生流直通（最早可发声版本的纯净实现）
                speakRes = tts.speak(text, TextToSpeech.QUEUE_FLUSH, ttsParams, uttId);
            } else {
                java.util.HashMap<String, String> map = new java.util.HashMap<>();
                map.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(streamType));
                map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uttId);
                speakRes = tts.speak(text, TextToSpeech.QUEUE_FLUSH, map);
            }
            if (speakRes != TextToSpeech.SUCCESS) {
                AppLogger.w("语音播报", "TTS speak 请求失败 (错误码: " + speakRes + ")，语音引擎可能未准备好发音数据");
            }
        } else {
            // 冷启动兜底：TTS 未就绪时缓存最新一条待播台词（覆盖旧缓存），并触发一次重试预热。
            pendingText = text;
            pendingVoiceType = arbiterKey;
            pendingTextAt = System.currentTimeMillis();
            ensureTtsReady();
            AppLogger.w("语音播报", "TTS尚未就绪，已加入待播队列并触发唤醒: " + text);
        }
        // 6s 超时兜底：TTS 引擎可能永远不回调 (车载 IPC 异常)；超时按失败处理，
        // 只对当前代生效，绝不把超时当成功、也绝不误清新会话。
        final long gen = arbiterCurrentGeneration();
        focusReleaseRunnable = new Runnable() {
            @Override
            public void run() {
                if (focusReleaseRunnable == this) {
                    focusReleaseRunnable = null;
                }
                AppLogger.w("语音播报", "TTS 6秒超时未收到引擎回调，按失败兜底释放焦点 (gen=" + gen + ")");
                notifyArbiterFinishedIfCurrent(gen);
            }
        };
        mainHandler.postDelayed(focusReleaseRunnable, 6000);
    }

    private long arbiterCurrentGeneration() {
        VoiceArbiter a = arbiter;
        return a != null ? a.currentGeneration() : 0L;
    }

    private void executeActualPlay(String voiceFileName, final String fallbackText) {
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
                speakTextInternal(customText.trim(), voiceFileName);
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
        AppLogger.i("语音播报", "触发朗读[系统TTS(" + getActiveTtsEngine() + ")]: " + fallbackText);
        speakTextInternal(fallbackText, voiceFileName);
    }

    private File getLocalAssetFile(String voiceFileName) {
        try {
            File voiceDir = new File(context.getFilesDir(), "voices");
            if (!voiceDir.exists()) voiceDir.mkdirs();
            File target = new File(voiceDir, voiceFileName);

            // 检查应用版本：当升级安装新版时，强制重新从 assets 提取覆盖，确保新音频立即生效！
            SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            int currentCode = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
            int lastExtractedCode = prefs.getInt("last_asset_extracted_code", 0);

            if (target.exists() && target.length() > 0 && currentCode <= lastExtractedCode) {
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
            if (currentCode > lastExtractedCode) {
                prefs.edit().putInt("last_asset_extracted_code", currentCode).apply();
            }
            if (target.exists() && target.length() > 0) {
                return target;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void playAudioFile(final File file, final String voiceType) {
        final int sessionId = playSessionId.incrementAndGet();
        final long arbiterGen = arbiterCurrentGeneration();
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
                            notifyArbiterFinishedIfCurrent(arbiterGen);
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
                            notifyArbiterFinishedIfCurrent(arbiterGen);
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
                    notifyArbiterStarted(arbiterGen);
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
                    // 准备失败也要通知仲裁器结束，否则通道永久卡死
                    notifyArbiterFinishedIfCurrent(arbiterGen);
                }
            }
        }).start();
    }

    private void playAudioFile(final File file) {
        playAudioFile(file, null);
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
            // 核心铁律：当车载蓝牙音频通道处于活跃连接态时，严禁申请 AudioFocus！
            // 吉利原厂蓝牙协议栈收到焦点退让广播 (-3) 后，会反向向手机下发 AVRCP 指令，
            // 其中十进制 keyCode 68 = 0x44 = PLAY，keyCode 70 = 0x46 = PAUSE；
            // 误触发 PAUSE 会导致微信语音或手机音乐被强制暂停掐断。
            // 直接走 AudioFlinger PCM 底层硬件混音即可完美共存！
            try {
                if (EasMediaBridge.getInstance(context).isBluetoothChannelActive()) {
                    Log.i(TAG, "Bluetooth channel active, bypassing requestAudioFocus to prevent AVRCP PAUSE(0x46/70) to phone.");
                    return;
                }
            } catch (Throwable ignored) {}

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
            Log.e(TAG, "extractVoiceZip error: " + e.getMessage(), e);
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