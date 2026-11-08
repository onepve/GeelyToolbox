package app.onepve.geelyconsole.utils;

import android.app.ActivityManager;
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
 * 2. 仪表/屏保音乐卡片不由助手推送：助手全程只做蓝牙通道仲裁，绝不向仪表盘/息屏时钟投递歌名歌手或歌词，
 *    卡片内容一律由车机自带播放器（米小江/酷我等）自行推送，杜绝副屏卡片遮挡导航地图。
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
    // 经吉利官方 EAS SDK (SourceType.java) 源码级确证：SOURCE_TYPE_BT = 2，SOURCE_TYPE_ONLINE = 6
    public static final int SOURCE_TYPE_BLUETOOTH = 2;
    public static final int SOURCE_TYPE_LOCAL = 6;

    /** A2DP Sink 音频流状态广播中的真实推流态 (STATE_STARTED) */
    private static final int A2DP_AUDIO_STATE_STARTED = 1;

    private static volatile EasMediaBridge sInstance;
    private final Context appContext;

    private MediaCenterAPI mApi;
    private Object mToken;
    private boolean mRegistered = false;
    private boolean mInitStarted = false;

    private boolean a2dpSinkConnected = false;   // 物理连接态 (connected != active!)
    private volatile boolean a2dpStreaming = false;
    private BroadcastReceiver a2dpReceiver;
    private long lastA2dpChannelActivateTime = 0;
    private volatile long lastFrozenKeepSourceBroadcastMs = 0;
    private volatile boolean isFrozenFallbackFocusHeld = false;
    private AudioManager audioManager;
    private MediaBrowser btMediaBrowser;
    private MediaController btMediaController;
    private volatile boolean btMediaBrowserConnected = false;
    private volatile boolean voiceCompensationEnabled = true;
    private volatile int voiceCompensationOffset = 3;
    /**
     * 【仅接管方控】模式下的单次放行闸：仅由车主在其他卡片主动触发的显式蓝牙动作开关，
     * 用完即复位，绝不长期放开，防「仅接管方控」被静默穿透成接管蓝牙。
     */
    private volatile boolean explicitBtActionBypass = false;


    private final AudioManager.OnAudioFocusChangeListener frozenFallbackFocusListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            AppLogger.i("蓝牙音频", "【冻结托底】MAY_DUCK 音频焦点状态变更: " + focusChange);
            if (focusChange < 0) {
                isFrozenFallbackFocusHeld = false;
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                isFrozenFallbackFocusHeld = true;
            }
        }
    };

    private String currentControlMode = "carmedia_first";

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
            // 冷启动即按车主已存模式初始化，杜绝【仅接管方控】模式下构造函数先误连蓝牙媒体浏览器
            this.currentControlMode = prefs.getString("wheel_control_mode", "carmedia_first");
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
     * 同步当前接管模式
     * 说明：仪表/息屏音乐卡片开关已整体下线（助手从未真正推送过卡片内容，
     * 开关开着只会误导车主以为能显示，关着又与默认行为无差别），此处不再接收投递参数。
     */
    public synchronized void syncConfig(String mode) {
        this.currentControlMode = mode;

        AppLogger.i("音频通道", "同步 EAS 桥接配置: 模式=" + mode);

        // 【仅接管方控】模式铁律：只拦方向盘按键，蓝牙音频通道 100% 保持原厂调度，
        // 助手绝不注册 EAS、绝不选通蓝牙物理通道、绝不申请闪避焦点（车主明确要求“不接管蓝牙”）。
        if (SteeringWheelKeyManager.MODE_WHEEL_ONLY.equals(mode)) {
            AppLogger.i("音频通道", "当前为【仅接管方控】模式，助手不接管蓝牙音频，释放 EAS 注册与蓝牙焦点，完整交还原厂");
            releaseEasRegistration();
            abandonBluetoothFocus();
            disconnectBtMediaBrowser();
            return;
        }

        // 【恢复原厂默认】铁律：车主明确要求还原原车行为，助手必须彻底撒手。
        // 历史坑：旧判定带 a2dpSinkConnected，手机蓝牙一连上就照样注册 EAS 占住仪表卡片，
        // 名字叫「恢复原厂默认」却偷偷接管，此处显式拦住。
        if (SteeringWheelKeyManager.MODE_FACTORY_DEFAULT.equals(mode)) {
            AppLogger.i("音频通道", "当前为【恢复原厂默认】模式，助手彻底撒手，注销 EAS 并释放蓝牙焦点");
            releaseEasRegistration();
            abandonBluetoothFocus();
            disconnectBtMediaBrowser();
            return;
        }

        if ("toolbox_alone".equals(mode) || "bt_only".equals(mode) || a2dpSinkConnected) {
            // 控制台独立接管、仅接管蓝牙或手机蓝牙已连接：确保 EAS 初始化并注册选通蓝牙通道
            ensureEasReady();
            if (a2dpSinkConnected && a2dpStreaming) {
                activateBluetoothChannel();
            }
        } else {
            // 未连接蓝牙且非独立接管：注销 EAS 释放焦点，退让给米小江或原厂多媒体
            releaseEasRegistration();
            abandonBluetoothFocus();
        }
    }

    /**
     * 当前是否为【仅接管方控】模式：只拦方向盘按键，蓝牙链路一律不接管。
     * 事实边界：该模式下助手不注册 EAS、不选通蓝牙物理通道、不申请闪避焦点、不发保源广播，
     * 蓝牙声音与原厂多媒体调度 100% 保持车主原车行为。
     * 例外：车主在【其他卡片】主动触发的显式蓝牙动作（如车速自启选手机蓝牙、音源测试）不受本模式压制，
     * 由 explicitBtActionBypass 单次放行，保证「方控接管模式」绝不连带干扰其他功能卡片的既有逻辑。
     */
    private boolean isWheelOnlyMode() {
        return !explicitBtActionBypass && SteeringWheelKeyManager.MODE_WHEEL_ONLY.equals(currentControlMode);
    }

    /**
     * 确保 EAS API 就绪并注册
     */
    private synchronized void ensureEasReady() {
        if (isWheelOnlyMode()) {
            AppLogger.i("音频通道", "【仅接管方控】模式，禁止任何 EAS 注册与声道选通，蓝牙链路完整交还原厂");
            return;
        }
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

    private boolean isOriginalMediaActive() {
        // 【仅接管方控】模式：从助手视角一律视为“原厂多媒体正常活跃”，
        // 从而让所有蓝牙介入动作（保源广播、闪避焦点、声卡唤醒 play、排队播放 pause）全部走原厂跳过分支，
        // 助手 100% 不接管蓝牙；车主在其他卡片的显式蓝牙动作由 explicitBtActionBypass 单次放行。
        if (isWheelOnlyMode()) {
            return true;
        }
        try {
            // 1. 原生进程活跃探测（零 ADB，零 shell）：检测 com.ecarx.multimedia 是否存在存活进程
            ActivityManager am = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
                if (procs != null) {
                    for (ActivityManager.RunningAppProcessInfo proc : procs) {
                        if ("com.ecarx.multimedia".equals(proc.processName)) {
                            return true;
                        }
                    }
                }
            }
            // 2. 原生包启用状态探测：若包已安装且未被停用/冻结，同样视为原厂多媒体存在
            return !SteeringWheelKeyManager.isMultimediaFrozenFast(appContext);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 【冻结托底专属】发送原厂小部件广播保持蓝牙音源
     * 注意：此广播附带 _PLAY 意图，严禁在原厂多媒体正常运行时发送，否则原厂多媒体会向手机发送 keyCode:68 唤醒音乐播放！
     */
    public void sendKeepSourceBroadcastWhenFrozen() {
        if (isOriginalMediaActive()) {
            // 原厂多媒体正常运行时，严禁发送带 _PLAY 意图的广播，杜绝原厂向手机误发 keyCode:68 唤醒播放
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastFrozenKeepSourceBroadcastMs < 4000) {
            return;
        }
        lastFrozenKeepSourceBroadcastMs = now;
        try {
            Intent i = new Intent("ecarx.intent.broadcast.action.ECARX_WIDGET_BLUETOOTH_PLAY");
            i.setPackage("com.ecarx.multimedia");
            appContext.sendBroadcast(i);
            AppLogger.i("蓝牙音频", "【冻结托底】已下发 ECARX_WIDGET_BLUETOOTH_PLAY 广播保持原厂多媒体在蓝牙音源");
        } catch (Throwable t) {
            AppLogger.w("蓝牙音频", "sendKeepSourceBroadcastWhenFrozen 失败: " + t.getMessage());
        }
    }

    /**
     * 【冻结托底专属】申请常驻 MAY_DUCK 闪避音频焦点
     * 1. 彻底解决原厂多媒体被冻结后蓝牙无声的物理死穴：
     *    当车主冻结原厂多媒体后，原厂不再向系统申请焦点。若助手不申请焦点，系统 AudioPolicy 会判定
     *    无活动音频源并彻底关闭功放通道 (硬件静音 val:0.000000)！
     * 2. 缤越助手主动申请 STREAM_MUSIC + MAY_DUCK 闪避常驻焦点，告知系统音频就绪，
     *    同时由于是 MAY_DUCK，手机微信语音、手机导航与第三方本地音乐可安全共存，绝不掐断微信！
     * 3. 原厂多媒体存在时直接跳过，杜绝与原厂抢占焦点导致硬件静音。
     */
    public synchronized void requestDuckingFocusWhenFrozen() {
        if (isOriginalMediaActive()) {
            // 原厂多媒体正常运行时，全权交由原厂多媒体调度，助手严禁抢占焦点以防 MT8666 DSP 静音
            return;
        }
        if (isFrozenFallbackFocusHeld) {
            return;
        }
        if (audioManager == null) {
            audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        }
        if (audioManager == null) {
            return;
        }
        try {
            int r = audioManager.requestAudioFocus(frozenFallbackFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            isFrozenFallbackFocusHeld = (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
            AppLogger.i("蓝牙音频", "【冻结托底】已申请常驻 MAY_DUCK 蓝牙闪避焦点 (保障多媒体冻结时功放选通)，result=" + r + " held=" + isFrozenFallbackFocusHeld);
        } catch (Throwable t) {
            AppLogger.w("蓝牙音频", "requestDuckingFocusWhenFrozen 异常: " + t.getMessage());
        }
    }

    public synchronized void abandonBluetoothFocus() {
        if (audioManager == null) {
            audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        }
        if (audioManager == null) {
            return;
        }
        try {
            audioManager.abandonAudioFocus(frozenFallbackFocusListener);
            isFrozenFallbackFocusHeld = false;
            AppLogger.i("蓝牙音频", "【冻结托底】已释放常驻 MAY_DUCK 蓝牙闪避焦点");
        } catch (Throwable t) {
            AppLogger.w("蓝牙音频", "abandonBluetoothFocus 异常: " + t.getMessage());
        }
    }

    /**
     * 断开底层蓝牙 MediaBrowser 连接。
     * 让路态必须调用：助手退出媒体中心后若仍握着 A2dpMediaBrowserService 连接，
     * 会与原厂多媒体争夺底层蓝牙会话，表现为「开了歌词，手机音乐反而被唤醒/声音发飘」。
     */
    private synchronized void disconnectBtMediaBrowser() {
        if (btMediaBrowser == null) {
            btMediaBrowserConnected = false;
            btMediaController = null;
            return;
        }
        try {
            btMediaBrowser.disconnect();
        } catch (Throwable ignored) {
        }
        btMediaBrowser = null;
        btMediaController = null;
        btMediaBrowserConnected = false;
        AppLogger.i("蓝牙音频", "已断开底层 A2DP MediaBrowser 连接，蓝牙会话完整交还原厂");
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
     * 蓝牙语音/微信短语音放音保护：
     * 铁律：彻底杜绝向系统申请任何 USAGE_ASSISTANCE_NAVIGATION_GUIDANCE 或 MAY_DUCK 焦点！
     * 事实依据（吉利车机真车日志）：吉利原厂底层 AudioPolicyManager/AudioFlinger 检测到导航/MAY_DUCK 焦点时，
     * 会在 HAL 硬件层对所有 STREAM_MUSIC (Stream 3) 强制执行全局 doDuck (ampl=0.562342, vlf降至0.003)，
     * 而手机蓝牙 A2DP 语音恰恰挂在 STREAM_MUSIC 通道上，导致微信语音自身被系统误伤腰斩，声音发闷极小。
     * 因此坚决不向系统申请此类压低焦点，若有残留则彻底释放，保障蓝牙音频 100% 满音量无损输出。
     */
    public synchronized void ensureNoDuckingFocus() {
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
        if (isWheelOnlyMode()) {
            AppLogger.i("蓝牙音频", "【仅接管方控】模式，跳过蓝牙物理通道选通与媒体浏览器连接，蓝牙声音仍由原厂调度");
            return;
        }
        try {
            ensureEasReady();
            if (mApi != null && mRegistered && mToken != null) {
                mApi.updateCurrentSourceType(mToken, SOURCE_TYPE_BLUETOOTH);
                AppLogger.i("蓝牙音频", "已下发 updateCurrentSourceType(2)，原车 2 号蓝牙音频物理通道已选通！");
            }

            sendKeepSourceBroadcastWhenFrozen();
            connectBtMediaBrowser();

            // 2. 尝试持有 MAY_DUCK 闪避焦点 (若原厂正常运行则自动跳过外部争抢)
            requestDuckingFocusWhenFrozen();

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
     * 车主显式指令（方控播放键、音源测试、强制选通按钮）下的蓝牙通道选通。
     * 边界：本方法只用于穿透【仅接管方控】模式的被动守护，绝不吞掉车主的显式操作。
     */
    public void activateBluetoothChannelExplicit() {
        explicitBtActionBypass = true;
        try {
            activateBluetoothChannel();
        } finally {
            explicitBtActionBypass = false;
        }
    }

    /**
     * 主动唤醒手机蓝牙开始播放音乐 (仅在用户主动按方控播放或车速自启明确要放歌时调用)
     * 边界：车速自启/音源测试等其他卡片的显式蓝牙动作属车主明确指令，单次放行，不受【仅接管方控】压制。
     */
    public void playBluetoothMusic() {
        clearAutoWakeSuppression();
        explicitBtActionBypass = true;
        try {
            activateBluetoothChannel();
            sendKeepSourceBroadcastWhenFrozen();
            wakeBluetoothSinkWhenFrozen();
        } finally {
            explicitBtActionBypass = false;
        }
    }

    /**
     * 【冻结托底专属】唤醒底层 com.android.bluetooth A2DP Sink 链路，使其主动向系统申请 AudioFocus 避免声卡硬件静音 (val:0.000000)
     * 必须调用 play() 穿透触发系统蓝牙的 informAudioFocusStateNative: 1，解除 GOC a2dp_player.c 的硬件静音锁！
     * 注意：原厂正常活跃时严禁下发 play()，否则会误把 iPhone 唤醒放歌！
     */
    public void wakeBluetoothSinkWhenFrozen() {
        if (isOriginalMediaActive()) {
            // 原厂多媒体正常运行时，全权交由原厂多媒体调度底层蓝牙，严禁下发 play() 误唤醒手机播放
            return;
        }
        requestDuckingFocusWhenFrozen();
        if (isAutoWakeSuppressed()) {
            AppLogger.i("蓝牙音频", "处于用户暂停抑制窗口内，跳过下发 play() (尊重用户暂停意图)");
            return;
        }

        try {
            if (btMediaController != null) {
                AppLogger.i("蓝牙音频", "【冻结托底】通过 A2dpMediaBrowserService MediaController 唤醒底层 A2DP Sink AudioFocus");
                btMediaController.getTransportControls().play();
            } else {
                MediaSessionManager mm = (MediaSessionManager) appContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
                if (mm != null) {
                    List<MediaController> controllers = mm.getActiveSessions(null);
                    if (controllers != null) {
                        for (MediaController mc : controllers) {
                            if ("com.android.bluetooth".equals(mc.getPackageName())) {
                                AppLogger.i("蓝牙音频", "【冻结托底】命中系统蓝牙 MediaSession，下发 play() 唤醒底层 A2DP AudioFocus！");
                                mc.getTransportControls().play();
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            AppLogger.w("蓝牙音频", "【冻结托底】唤醒底层 A2DP AudioFocus 异常: " + t.getMessage());
        }
        connectBtMediaBrowser();
    }

    /**
     * 【冻结托底专属】推流停止或防抢播守护：下发 pause() 掐灭可能排队的手机音乐播放
     */
    public void pauseBluetoothSinkWhenFrozen() {
        if (isOriginalMediaActive()) {
            return;
        }
        try {
            if (btMediaController != null) {
                AppLogger.i("蓝牙音频", "【冻结托底】推流停止，通过 btMediaController 下发 pause() 掐灭手机排队播放");
                btMediaController.getTransportControls().pause();
            } else {
                MediaSessionManager mm = (MediaSessionManager) appContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
                if (mm != null) {
                    List<MediaController> controllers = mm.getActiveSessions(null);
                    if (controllers != null) {
                        for (MediaController mc : controllers) {
                            if ("com.android.bluetooth".equals(mc.getPackageName())) {
                                AppLogger.i("蓝牙音频", "【冻结托底】推流停止，通过系统蓝牙 MediaSession 下发 pause() 掐灭手机排队播放");
                                mc.getTransportControls().pause();
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            AppLogger.w("蓝牙音频", "【冻结托底】pauseBluetoothSinkWhenFrozen 异常: " + t.getMessage());
        }
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
                if (this.a2dpSinkConnected) {
                    if (isOriginalMediaActive()) {
                        AppLogger.i("蓝牙音频", "初始化探测到蓝牙已连接，原厂多媒体正常活跃，放弃接管");
                    } else {
                        AppLogger.i("蓝牙音频", "【冻结托底】初始化探测到蓝牙已连接，就绪底层链路与焦点守护");
                        connectBtMediaBrowser();
                        requestDuckingFocusWhenFrozen();
                    }
                }
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
                        if (isOriginalMediaActive()) {
                            AppLogger.i("蓝牙音频", "监听到蓝牙已连接，原厂多媒体正常活跃，放弃接管，全权由原厂多媒体调度");
                        } else {
                            AppLogger.i("蓝牙音频", "【冻结托底】监听到蓝牙已连接，静默就绪底层链路与焦点守护");
                            connectBtMediaBrowser();
                            requestDuckingFocusWhenFrozen();
                        }
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
                    }
                    if (streaming) {
                        if (!isOriginalMediaActive()) {
                            // 仅在原厂被冻结时，才介入压低焦点与声卡唤醒托底
                            ensureNoDuckingFocus();
                            wakeBluetoothSinkWhenFrozen();
                        }

                        // 选通原车 2 号蓝牙音频物理通道
                        long now = SystemClock.uptimeMillis();
                        if (now - lastA2dpChannelActivateTime > 4000) {
                            lastA2dpChannelActivateTime = now;
                            activateBluetoothChannel();
                        }
                    } else {
                        // 微信推流结束：释放压低焦点，触发 QQ 音乐/本地音乐音量回弹
                        abandonDuckingFocus();
                        AppLogger.i("蓝牙音频", "监听到推流停止，已释放压低焦点并保持通道守护");
                    }
                } else if ("android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT".equals(action)) {
                    try {
                        android.media.session.PlaybackState pbState = intent.getParcelableExtra("android.bluetooth.avrcp-controller.profile.extra.PLAYBACK");
                        if (pbState != null) {
                            boolean isPlaying = (pbState.getState() == android.media.session.PlaybackState.STATE_PLAYING);
                            if (isPlaying && !a2dpStreaming) {
                                a2dpStreaming = true;
                                AppLogger.i("蓝牙音频", "监听到 AVRCP 推流起播，选通声道");
                                if (!isOriginalMediaActive()) {
                                    ensureNoDuckingFocus();
                                    wakeBluetoothSinkWhenFrozen();
                                }
                                activateBluetoothChannel();
                            } else if (!isPlaying && a2dpStreaming) {
                                a2dpStreaming = false;
                                abandonDuckingFocus();
                                AppLogger.i("蓝牙音频", "监听到 AVRCP 推流停止，已释放压低焦点并保持通道守护");
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
     * 选通原车本地/在线音频物理通道 (EAS 6 号物理通道)
     * 当车速自启放歌、或用户方控切歌/播放本地音乐(QQ音乐等)时调用，
     * 确保 DSP 功放选通至车机端本地 DAC 输出通道，杜绝声卡被锁在蓝牙声道导致本地音乐无声。
     */
    public synchronized void activateLocalMediaChannel() {
        try {
            ensureEasReady();
            if (mApi != null && mRegistered && mToken != null) {
                mApi.updateCurrentSourceType(mToken, SOURCE_TYPE_LOCAL);
                AppLogger.i("音频通道", "已下发 updateCurrentSourceType(6)，原车本地多媒体物理通道已选通！");
            }
        } catch (Throwable t) {
            AppLogger.w("音频通道", "选通本地多媒体物理通道失败: " + t.getMessage());
        }
    }
    public synchronized void switchSourceTypeManually(int sourceType) {
        try {
            ensureEasReady();
            if (mApi != null && mRegistered && mToken != null) {
                mApi.updateCurrentSourceType(mToken, sourceType);
                AppLogger.i("音频通道", "手动下发 updateCurrentSourceType(" + sourceType + ")");
            }
        } catch (Throwable t) {
            AppLogger.e("音频通道", "手动切换音源异常: " + t.getMessage());
        }
    }
}
