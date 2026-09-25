package app.onepve.geelyconsole.utils;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.media.session.MediaController;
import android.content.ComponentName;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import app.onepve.geelyconsole.services.VehicleAutomationService;

import com.ecarx.eas.sdk.ECarXApiClient;
import com.ecarx.eas.sdk.mediacenter.MediaCenterAPI;
import com.ecarx.eas.sdk.mediacenter.MusicClient;
import com.ecarx.eas.sdk.mediacenter.MusicPlaybackInfo;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 吉利 ECARX EAS 多媒体中心轻量桥接器
 * 核心职责：
 * 1. 蓝牙物理声道仲裁 (A2DP Sink 接管)：在【控制台独立接管】模式下，当手机蓝牙连入时主动选通 2 号蓝牙硬件通道并申请音频焦点，
 *    解决冻结原厂多媒体后手机蓝牙/微信语音无声的死锁痛点；在【米小江优先】模式下主动退让，零冲突。
 * 2. 仪表/屏保播放状态投递开关 (wheel_push_playback_cluster，默认 false)：开启时向 EAS 投递歌名歌手，息屏唤起音乐卡片。
 * 3. 仪表盘实时歌词投递开关 (wheel_push_lyrics_cluster，默认 false)：开启时向仪表盘/HUD 投递当前歌词。
 *
 * 音频状态事实边界 (不编造能力)：
 * - A2DP「已连接 (connected)」≠「正在推流 (streaming/active)」：只有底层 AUDIO_STATE_CHANGED
 *   上报 STATE_STARTED 才认为蓝牙音频流真正活跃。手机暂停音乐后连接仍在但推流停止。
 * - 车机侧无法分辨手机端推的是「微信语音」还是「本地音乐」——二者在 A2DP 通道上完全同构，
 *   因此一律按「外部蓝牙音频流」同等保护 (免焦点申请防 AVRCP 反向下发)。
 * - AVRCP 十进制键码事实：68 (0x44) = PLAY，70 (0x46) = PAUSE。原厂协议栈在焦点退让时
 *   反向下发的是 PAUSE (70)，绝非 68。
 */
public class EasMediaBridge {
    private static final String TAG = "EasMediaBridge";
    public static final String PKG_BLUETOOTH = "com.android.bluetooth";
    // 经吉利多媒体源码与实车日志确证：吉利 E02 / SX11-A3 平台 SOURCE_TYPE_BT = 2，SOURCE_TYPE_ONLINE = 6
    public static final int SOURCE_TYPE_BLUETOOTH = 2;

    /** A2DP Sink 音频流状态广播中的真实推流态 (STATE_STARTED) */
    private static final int A2DP_AUDIO_STATE_STARTED = 1;

    private static volatile EasMediaBridge sInstance;
    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private MediaCenterAPI mApi;
    private Object mToken;
    private boolean mRegistered = false;
    private boolean mInitStarted = false;

    private boolean a2dpSinkConnected = false;   // 物理连接态 (connected != active!)
    private volatile boolean a2dpStreaming = false;
    private BroadcastReceiver a2dpReceiver;
    private long lastA2dpWakeTime = 0;
    private volatile long lastBtSourceKeepMs = 0;
    private volatile boolean btFocusHeld = false;
    private AudioManager audioManager;
    private MediaBrowser btMediaBrowser;
    private MediaController btMediaController;
    private volatile boolean btMediaBrowserConnected = false;
    private volatile boolean isDucked = false;
    private volatile boolean voiceCompensationEnabled = true;
    private volatile int voiceCompensationOffset = 3;


    private final AudioManager.OnAudioFocusChangeListener btFocusListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            AppLogger.i("蓝牙音频", "蓝牙 MAY_DUCK 音频焦点状态变更: " + focusChange);
            if (focusChange < 0) {
                btFocusHeld = false;
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                btFocusHeld = true;
            }
        }
    };

    private String currentControlMode = "carmedia_first";
    private boolean pushPlaybackCluster = false;
    private boolean pushLyricsCluster = false;

    private String lastPushedTitle = "";
    private String lastPushedArtist = "";
    private int lastPushedStatus = -1;

    public static EasMediaBridge getInstance(Context context) {
        if (sInstance == null) {
            synchronized (EasMediaBridge.class) {
                if (sInstance == null) {
                    sInstance = new EasMediaBridge(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    private EasMediaBridge(Context context) {
        this.appContext = context;
        try {
            SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            this.voiceCompensationEnabled = prefs.getBoolean("voice_gain_compensation_enabled", true);
            this.voiceCompensationOffset = prefs.getInt("voice_gain_compensation_offset", 3);
        } catch (Throwable ignored) {}
        registerA2dpReceiver();
    }

    /**
     * 同步微信/蓝牙语音音量智能补偿配置
     */
    public synchronized void syncVoiceCompensationConfig(boolean enabled, int offset) {
        this.voiceCompensationEnabled = enabled;
        this.voiceCompensationOffset = Math.max(-10, Math.min(25, offset));
        AppLogger.i("蓝牙音频", "同步微信语音补偿配置: enabled=" + enabled + ", offset=" + this.voiceCompensationOffset);
    }

    /**
     * 同步当前模式与开关
     */
    public synchronized void syncConfig(String mode, boolean pushPlayback, boolean pushLyrics) {
        this.currentControlMode = mode;
        this.pushPlaybackCluster = pushPlayback;
        this.pushLyricsCluster = pushLyrics;

        AppLogger.i("音频通道", "同步 EAS 桥接配置: 模式=" + mode + " 投递状态=" + pushPlayback + " 投递歌词=" + pushLyrics);

        if ("toolbox_alone".equals(mode) || a2dpSinkConnected) {
            // 控制台独立接管或手机蓝牙已连接：确保 EAS 初始化并注册选通蓝牙通道
            ensureEasReady();
            if (a2dpSinkConnected) {
                activateBluetoothChannel();
            }
        } else {
            // 未连接蓝牙且非独立接管：注销 EAS 释放焦点，退让给米小江或原厂多媒体
            releaseEasRegistration();
            abandonBluetoothFocus();
        }
    }

    /**
     * 确保 EAS API 就绪并注册
     */
    private synchronized void ensureEasReady() {
        if (mApi == null) {
            mApi = MediaCenterAPI.get(appContext);
        }
        if (!mInitStarted) {
            mInitStarted = true;
            mApi.init(appContext, new ECarXApiClient.Callback() {
                @Override
                public void onAPIReady(boolean ready) {
                    if (ready) {
                        AppLogger.i("音频通道", "ECARX EAS API 就绪，执行媒体客户端注册...");
                        performRegistration();
                    } else {
                        AppLogger.w("音频通道", "ECARX EAS API 初始化返回失败");
                        mInitStarted = false;
                    }
                }
            });
        } else if (mApi.isReady() && !mRegistered) {
            performRegistration();
        }
    }

    /**
     * 注册 MusicClient 并声明蓝牙通道
     */
    private synchronized void performRegistration() {
        if (mApi == null || !mApi.isReady()) return;
        try {
            if (mRegistered && mToken != null) {
                try {
                    mApi.unregister(mToken);
                } catch (Throwable ignored) {}
                mRegistered = false;
                mToken = null;
            }

            MusicClient client = new MusicClient() {

                // 铁律：EAS 回调（onPlay/onPause/onNext/onPrevious）是蓝牙 AVRCP 状态变化通知，
                // 严禁在此回灌媒体键（sendMediaKeyEvent）。回灌会经 AudioManager -> AVRCP -> EAS 回调
                // 形成自激回环：单次方控按键曾实测触发 5 秒内 837 次媒体键风暴。
                // 方控按键已由 SteeringWheelKeyManager.executeAction 独立处理，此处仅记录状态。
                @Override
                public boolean onPlay() {
                    AppLogger.i("音频通道", "EAS 回调: onPlay（仅记录，不回流媒体键）");
                    return true;
                }

                @Override
                public boolean onPause() {
                    AppLogger.i("音频通道", "EAS 回调: onPause（仅记录，不回流媒体键）");
                    return true;
                }

                @Override
                public boolean onNext() {
                    AppLogger.i("音频通道", "EAS 回调: onNext（仅记录，不回流媒体键）");
                    return true;
                }

                @Override
                public boolean onPrevious() {
                    AppLogger.i("音频通道", "EAS 回调: onPrevious（仅记录，不回流媒体键）");
                    return true;
                }
            };

            mToken = mApi.registerMusic(appContext.getPackageName(), client);
            if (mToken != null) {
                mRegistered = true;
                // 声明支持 2 号蓝牙物理声道并立即宣告选通
                mApi.updateMediaSourceTypeList(mToken, new int[]{SOURCE_TYPE_BLUETOOTH});
                mApi.declareMediaCenterCapability(mToken, new int[]{0, 2, 3});
                mApi.declareSupportCollectTypes(mToken, new int[]{0, 3, 4});
                mApi.updateCurrentSourceType(mToken, SOURCE_TYPE_BLUETOOTH);
                AppLogger.i("音频通道", "EAS 注册成功，已声明并选通 2 号蓝牙物理声道");

                // 若手机蓝牙处于连接或推流状态，仅选通声道，严禁主动下发播放指令
                if (a2dpSinkConnected) {
                    activateBluetoothChannel();
                }
            }
        } catch (Throwable t) {
            AppLogger.e("音频通道", "EAS 注册异常: " + t.getMessage());
        }
    }

    private volatile boolean isExplicitBluetoothPlayRequest = false;

    /**
     * 发送原厂小部件广播保持蓝牙音源 (移植原厂与米小江核心逻辑)
     */
    public void keepXcmediaOnBluetoothSource() {
        if (!isExplicitBluetoothPlayRequest) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBtSourceKeepMs < 4000) {
            return;
        }
        lastBtSourceKeepMs = now;
        try {
            Intent i = new Intent("ecarx.intent.broadcast.action.ECARX_WIDGET_BLUETOOTH_PLAY");
            i.setPackage("com.ecarx.multimedia");
            appContext.sendBroadcast(i);
            AppLogger.i("蓝牙音频", "已下发 ECARX_WIDGET_BLUETOOTH_PLAY 广播保持原厂多媒体在蓝牙音源");
        } catch (Throwable t) {
            AppLogger.w("蓝牙音频", "keepXcmediaOnBluetoothSource 失败: " + t.getMessage());
        } finally {
            isExplicitBluetoothPlayRequest = false;
        }
    }

    /**
     * 申请常驻 MAY_DUCK 闪避音频焦点 (移植米小江车规级核心逻辑)
     * 1. 底层吉利蓝牙堆栈 (A2dpSinkStreamHandler) 永远感知到 audioFocus != 0，绝不会触发 stopFluorideStreaming 与 sendAvrcpPause 掐死微信！
     * 2. MAY_DUCK 告知系统此焦点可与其他媒体混音，本地播放音乐时仅轻微压低音量，手机微信语音、手机导航与本地音乐可完美同时放声！
     */

    /**
     * 严禁第三方应用抢占 AudioFocus (规避系统 AudioPolicy 对 A2DP 蓝牙实施硬件级 70% Ducking 衰减)
     * 底层吉利蓝牙堆栈 (A2dpMediaBrowserService / A2dpSinkStreamHandler) 会自主申请并维持主焦点。
     */
    public synchronized void requestBluetoothFocusIfNeeded() {
        // 彻底杜绝抢占焦点：任何 MAY_DUCK 都会导致系统将蓝牙音乐强制压低为蚊子叫
        if (btFocusHeld) {
            abandonBluetoothFocus();
        }
    }

    /**
     * 释放蓝牙焦点
     */
    public synchronized void abandonBluetoothFocus() {
        if (!btFocusHeld || audioManager == null) {
            return;
        }
        try {
            audioManager.abandonAudioFocus(btFocusListener);
            AppLogger.i("蓝牙音频", "已释放蓝牙残留焦点");
        } catch (Throwable t) {
            AppLogger.w("蓝牙音频", "abandonBluetoothFocus 异常: " + t.getMessage());
        }
        btFocusHeld = false;
    }

    /**
     * 选通原车蓝牙音频硬件通道 (EAS 2 号物理通道)
     * 职责极其纯粹：仅负责打通 DSP 硬件功放的蓝牙声道与解除静音，
     * 严禁在选通时向手机回灌 play() 播歌指令，坚决杜绝把手机微信语音掐断！
     */
    /**
     * 连接系统底层蓝牙 MediaBrowser 服务 (对齐原厂多媒体 XCMedia2 架构)
     * 底层组件: com.android.bluetooth.a2dpsink.mbs.A2dpMediaBrowserService
     */
    private synchronized void connectBtMediaBrowser() {
        if (btMediaBrowser != null && btMediaBrowserConnected) {
            return;
        }
        try {
            if (btMediaBrowser != null) {
                try {
                    btMediaBrowser.disconnect();
                } catch (Throwable ignored) {}
                btMediaBrowser = null;
                btMediaController = null;
                btMediaBrowserConnected = false;
            }
            ComponentName componentName = new ComponentName("com.android.bluetooth", "com.android.bluetooth.a2dpsink.mbs.A2dpMediaBrowserService");
            btMediaBrowser = new MediaBrowser(appContext, componentName, new MediaBrowser.ConnectionCallback() {
                @Override
                public void onConnected() {
                    try {
                        btMediaBrowserConnected = true;
                        MediaSession.Token token = btMediaBrowser.getSessionToken();
                        if (token != null) {
                            btMediaController = new MediaController(appContext, token);
                            AppLogger.i("蓝牙音频", "成功连接 A2dpMediaBrowserService 并获取 MediaController!");
                        }
                    } catch (Throwable t) {
                        AppLogger.w("蓝牙音频", "初始化 MediaController 异常: " + t.getMessage());
                    }
                }

                @Override
                public void onConnectionFailed() {
                    btMediaBrowserConnected = false;
                    AppLogger.w("蓝牙音频", "连接 A2dpMediaBrowserService 失败");
                }
            }, null);
            btMediaBrowser.connect();
            AppLogger.i("蓝牙音频", "发起连接系统蓝牙 A2dpMediaBrowserService...");
        } catch (Throwable t) {
            AppLogger.w("蓝牙音频", "connectBtMediaBrowser 异常: " + t.getMessage());
        }
    }

    private AudioFocusRequest voiceDuckingFocusRequest;
    private final AudioManager.OnAudioFocusChangeListener voiceDuckingListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            AppLogger.i("蓝牙音频", "方案B: voiceDuckingListener 焦点状态变更: " + focusChange);
        }
    };

    /**
     * 方案 B：系统原生 AudioFocus 压低本地音乐 (Ducking)
     * 使用 USAGE_ASSISTANCE_NAVIGATION_GUIDANCE 请求 MAY_DUCK 瞬态焦点：
     * 1. 让正在播放的 QQ 音乐 / 媒体软件接收到 AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK 并自动把自身音量降低到 20%~30%；
     * 2. 严禁改动硬件媒体总音量 (STREAM_MUSIC 保持 13~18)，确保微信声音清晰洪亮；
     * 3. 严禁向音乐软件发送 Play/Pause，避免引起状态机抽搐；
     * 4. 微信播完后释放焦点，QQ 音乐自动恢复 100% 满音量。
     */
    public synchronized void requestDuckingFocusForIncomingVoice() {
        // 彻底杜绝抢占焦点：严禁向系统申请任何 MAY_DUCK，避免系统 AudioPolicy 强行将蓝牙 A2DP 硬件压低 70%
        if (voiceDuckingFocusRequest != null) {
            abandonDuckingFocus();
        }
    }

    public synchronized void abandonDuckingFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && voiceDuckingFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(voiceDuckingFocusRequest);
                voiceDuckingFocusRequest = null;
                AppLogger.i("蓝牙音频", "已释放残留 MAY_DUCK 焦点");
            }
        } catch (Throwable t) {
            AppLogger.w("蓝牙音频", "abandonDuckingFocus 异常: " + t.getMessage());
        }
    }

    /**
     * 保持车载多媒体音量稳定安全 (杜绝起播停播瞬态抽搐篡改)
     */
    public synchronized void duckMediaVolume() {
        try {
            if (audioManager == null) {
                audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
            }
            if (audioManager == null) {
                return;
            }
            int currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            // 保持车主设定的真实硬件媒体音量，仅在底层异常静音(0)时安全兜底
            if (currentVol == 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 12, 0);
            }
        } catch (Throwable t) {
            AppLogger.w("蓝牙音频", "duckMediaVolume 异常: " + t.getMessage());
        }
    }

    /**
     * 恢复媒体背景音量 (极简架构下无需反复横跳，保持平稳)
     */
    public synchronized void restoreMediaVolume() {
        abandonDuckingFocus();
    }

    public synchronized void activateBluetoothChannel() {
        try {
            ensureEasReady();
            if (mApi != null && mRegistered && mToken != null) {
                mApi.updateCurrentSourceType(mToken, SOURCE_TYPE_BLUETOOTH);
                AppLogger.i("蓝牙音频", "已下发 updateCurrentSourceType(2)，原车 2 号蓝牙音频物理通道已选通！");
            }
            // 1. 发送吉利原车系统底层切源广播 (显式指定 com.ecarx.multimedia 杜绝后台拦截) 与保持广播
            try {
                Intent rsrcIntent = new Intent("ecarx.intent.action.ECARX_KEY_RSRC_EVENT");
                rsrcIntent.putExtra("source_type", SOURCE_TYPE_BLUETOOTH);
                rsrcIntent.setPackage("com.ecarx.multimedia");
                appContext.sendBroadcast(rsrcIntent);
            } catch (Throwable ignored) {}

            keepXcmediaOnBluetoothSource();
            connectBtMediaBrowser();

            // 2. 尝试持有 MAY_DUCK 闪避焦点 (若底层蓝牙已接管则自动跳过外部争抢)
            requestBluetoothFocusIfNeeded();

            // 3. 确保系统媒体音量正常
            AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (curVol == 0) {
                    int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(1, maxVol / 2), 0);
                    AppLogger.i("蓝牙音频", "检测到媒体音量为0，已自动恢复适中音量以保障放声");
                }
            }
        } catch (Throwable t) {
            AppLogger.w("蓝牙音频", "选通蓝牙音频物理通道失败: " + t.getMessage());
        }
    }

    /**
     * 主动唤醒手机蓝牙开始播放音乐 (仅在用户主动按方控播放或车速自启明确要放歌时调用)
     */
    public void playBluetoothMusic() {
        clearAutoWakeSuppression();
        isExplicitBluetoothPlayRequest = true;
        activateBluetoothChannel();
        keepXcmediaOnBluetoothSource();
        wakeBluetoothAudioSink();
    }

    /**
     * 唤醒底层 com.android.bluetooth A2DP Sink 链路，使其主动向系统申请 AudioFocus 避免静音
     */
    public void wakeBluetoothAudioSink() {
        try {
            if (btMediaController != null) {
                AppLogger.i("蓝牙音频", "通过 A2dpMediaBrowserService MediaController 下发 play() 解除硬件静音！");
                btMediaController.getTransportControls().play();
                return;
            }
        } catch (Throwable t) {
            AppLogger.w("蓝牙音频", "btMediaController play 异常: " + t.getMessage());
        }
        connectBtMediaBrowser();
    }

    /** 用户主动暂停后的自动唤醒抑制截止时间戳 (ms)，期间严禁任何自动 play() */
    private volatile long autoWakeSuppressUntil = 0L;
    private static final String PREFS_AUTO_WAKE_SUPPRESS = "auto_wake_suppress_until";
    private static final String PREFS_NAME = "toolbox_settings";

    /**
     * 用户主动暂停后开启自动唤醒抑制窗口。
     * 事实依据（真车日志）：原车 EAS 在暂停后约 1.5 秒重新仲裁音频源并回调选通流程，
     * 若无条件下发 play()，会把用户刚按下的暂停强行顶回播放，表现为「暂停后立马又继续播放」。
     *
     * 2026-09-17 修复：抑制窗口落盘 SharedPreferences —— 此前为纯内存态，
     * 应用一重启（日志 18:00:01 应用重启）窗口即归零，A2DP 晚到事件与 EAS 重新仲裁
     * 会把用户暂停顶回播放。落盘后重启不丢，窗口语义完整。
     */
    public synchronized void suppressAutoWakeAfterUserPause(long ms) {
        long until = System.currentTimeMillis() + ms;
        autoWakeSuppressUntil = until;
        try {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putLong(PREFS_AUTO_WAKE_SUPPRESS, until).apply();
        } catch (Throwable ignored) {}
        AppLogger.i("蓝牙音频", "用户主动暂停，已开启 " + (ms / 1000) + " 秒自动唤醒抑制窗口 (已落盘)");
    }

    /**
     * 用户主动恢复播放后，立即解除自动唤醒抑制窗口。
     */
    public synchronized void clearAutoWakeSuppression() {
        autoWakeSuppressUntil = 0L;
        try {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().remove(PREFS_AUTO_WAKE_SUPPRESS).apply();
        } catch (Throwable ignored) {}
        AppLogger.i("蓝牙音频", "用户主动恢复播放，已立即解除自动唤醒抑制窗口");
    }

    /** 当前是否处于「用户主动暂停后的自动唤醒抑制窗口」内（供车身联动等自动 play() 前守卫查询） */
    public synchronized boolean isAutoWakeSuppressed() {
        // 合并内存态与落盘态：启动时从 SharedPreferences 恢复，重启后窗口依然有效
        if (System.currentTimeMillis() < autoWakeSuppressUntil) return true;
        try {
            long persisted = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getLong(PREFS_AUTO_WAKE_SUPPRESS, 0L);
            if (persisted > autoWakeSuppressUntil) autoWakeSuppressUntil = persisted;
            return System.currentTimeMillis() < autoWakeSuppressUntil;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 蓝牙外部音频流是否真正活跃 (正在推流)。
     * 事实边界：仅底层 A2DP Sink AUDIO_STATE_CHANGED 上报 STATE_STARTED 时为 true；
     * 「已连接但未推流」(connected-only，手机暂停了音乐) 返回 false。
     * 车机侧无法、也不试图分辨手机端推的是微信语音还是本地音乐。
     */
    public synchronized boolean isBluetoothChannelActive() {
        return a2dpStreaming;
    }

    /**
     * A2DP Sink 物理连接态 (连接不等于推流)。仅作 UI/诊断显示。
     */
    public synchronized boolean isA2dpSinkConnected() {
        return a2dpSinkConnected;
    }

    /**
     * A2DP Sink 底层推流态 (手机端音频正在播放推流中)
     */
    public boolean isA2dpStreaming() {
        return a2dpStreaming;
    }

    /**
     * 释放 EAS 注册 (退让焦点给米小江或原厂)
     */
    public synchronized void releaseEasRegistration() {
        try {
            if (mApi != null && mRegistered && mToken != null) {
                mApi.unregister(mToken);
                AppLogger.i("音频通道", "已注销 EAS Token，将蓝牙通道仲裁权退让给米小江/原厂");
            }
        } catch (Throwable ignored) {
        } finally {
            mRegistered = false;
            mToken = null;
            lastPushedTitle = "";
            lastPushedArtist = "";
            lastPushedStatus = -1;
        }
    }

    /**
     * 投递播放状态与歌曲信息到仪表盘与屏保 (由 wheel_push_playback_cluster 门控)
     */
    public synchronized void pushPlaybackInfoIfEnabled(String title, String artist, String album, long duration, int status) {
        if (!pushPlaybackCluster) {
            return; // 默认关闭，完全不打扰仪表盘与息屏时钟
        }
        if (!"toolbox_alone".equals(currentControlMode) || mApi == null || !mRegistered || mToken == null) {
            return;
        }
        if (title == null) title = "";
        if (artist == null) artist = "";

        // 去重防高频刷屏
        if (title.equals(lastPushedTitle) && artist.equals(lastPushedArtist) && status == lastPushedStatus) {
            return;
        }
        lastPushedTitle = title;
        lastPushedArtist = artist;
        lastPushedStatus = status;

        try {
            MusicPlaybackInfo info = new MusicPlaybackInfo();
            info.setTitle(title);
            info.setArtist(artist);
            info.setAlbum(album != null ? album : "");
            info.setDuration(duration > 0 ? duration : 0L);
            info.setPlaybackStatus(status == 1 ? 1 : 0);
            info.setSourceType(SOURCE_TYPE_BLUETOOTH);
            info.setPackageName(appContext.getPackageName());
            info.setAppName("缤越助手");
            info.setMediaType("music");
            info.setSupportLoopModeSwitch(true);
            info.setSupportVrCtrlPlayStatus(true);
            info.setUuid(title + "|" + artist);

            mApi.updateMusicPlaybackState(mToken, info);
            AppLogger.i("音频通道", "已向 EAS 投递媒体状态 (仪表/屏保可见): " + title + " - " + artist + " 状态=" + status);
        } catch (Throwable t) {
            AppLogger.w("音频通道", "投递媒体状态异常: " + t.getMessage());
        }
    }

    /**
     * 投递当前歌词到仪表盘/HUD (由 wheel_push_lyrics_cluster 门控)
     */
    public synchronized void pushLyricIfEnabled(String lyric) {
        if (!pushLyricsCluster) {
            return; // 默认关闭，仪表盘不显歌词
        }
        if (!"toolbox_alone".equals(currentControlMode) || mApi == null || !mRegistered || mToken == null) {
            return;
        }
        if (lyric == null || lyric.trim().isEmpty()) {
            return;
        }
        try {
            mApi.updateCurrentLyric(mToken, lyric.trim());
        } catch (Throwable t) {
            AppLogger.w("音频通道", "投递歌词异常: " + t.getMessage());
        }
    }

    /**
     * 监听 A2DP Sink 连接与真实推流状态
     */
    private void registerA2dpReceiver() {
        if (a2dpReceiver != null) return;
        try {
            BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
            if (ba != null) {
                // 11 代表 BluetoothProfile.A2DP_SINK，2 代表 STATE_CONNECTED
                this.a2dpSinkConnected = (ba.getProfileConnectionState(11) == 2);
            }
        } catch (Throwable ignored) {}

        a2dpReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                if ("android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED".equals(action)) {
                    int state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", -1);
                    if (state == 2) {
                        a2dpSinkConnected = true;
                        AppLogger.i("蓝牙音频", "监听到蓝牙已连接 (connected)，选通 2 号物理通道");
                        activateBluetoothChannel();
                    } else if (state == 0) {
                        a2dpSinkConnected = false;
                        a2dpStreaming = false;
                        abandonBluetoothFocus();
                        AppLogger.i("蓝牙音频", "监听到蓝牙已断开，释放焦点");
                    }
                } else if ("android.bluetooth.a2dp-sink.profile.action.AUDIO_STATE_CHANGED".equals(action)) {
                    int state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", -1);
                    boolean streaming = (state == A2DP_AUDIO_STATE_STARTED);
                    if (streaming != a2dpStreaming) {
                        a2dpStreaming = streaming;
                        AppLogger.i("蓝牙音频", "蓝牙推流状态跃变: streaming=" + streaming);
                        if (streaming) {
                            activateBluetoothChannel();
                        }
                    }
                } else if ("android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT".equals(action)) {
                    try {
                        android.media.session.PlaybackState pbState = intent.getParcelableExtra("android.bluetooth.avrcp-controller.profile.extra.PLAYBACK");
                        if (pbState != null) {
                            boolean isPlaying = (pbState.getState() == android.media.session.PlaybackState.STATE_PLAYING);
                            if (isPlaying && !a2dpStreaming) {
                                a2dpStreaming = true;
                                AppLogger.i("蓝牙音频", "监听到 AVRCP 推流起播，选通蓝牙物理声道");
                                activateBluetoothChannel();
                            } else if (!isPlaying && a2dpStreaming) {
                                a2dpStreaming = false;
                                AppLogger.i("蓝牙音频", "监听到 AVRCP 推流停止");
                            }
                        }
                    } catch (Throwable ignored) {}
                } else if ("android.bluetooth.headsetclient.profile.action.AUDIO_STATE_CHANGED".equals(action)) {
                    int state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", -1);
                    if (state == 2) {
                        AppLogger.i("蓝牙音频", "监听到蓝牙免提/麦克风建立 (SCO Connected)");
                    } else if (state == 0) {
                        AppLogger.i("蓝牙音频", "监听到蓝牙免提/麦克风断开 (SCO Disconnected)");
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED");
        filter.addAction("android.bluetooth.a2dp-sink.profile.action.AUDIO_STATE_CHANGED");
        filter.addAction("android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT");
        filter.addAction("android.bluetooth.headsetclient.profile.action.AUDIO_STATE_CHANGED");
        appContext.registerReceiver(a2dpReceiver, filter);
    }
    /**
     * 手动切换底层硬件音源（用于声道排查与调试）
     * @param sourceType 1=RADIO, 2=BT, 3=USB, 6=ONLINE/EAS
     */
    public synchronized void switchSourceTypeManually(int sourceType) {
        try {
            ensureEasReady();
            if (mApi != null && mRegistered && mToken != null) {
                mApi.updateCurrentSourceType(mToken, sourceType);
                AppLogger.i("音频通道", "手动下发 updateCurrentSourceType(" + sourceType + ")");
            }
            try {
                Intent rsrcIntent = new Intent("ecarx.intent.action.ECARX_KEY_RSRC_EVENT");
                rsrcIntent.putExtra("source_type", sourceType);
                appContext.sendBroadcast(rsrcIntent);
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            AppLogger.e("音频通道", "手动切换音源异常: " + t.getMessage());
        }
    }
}
