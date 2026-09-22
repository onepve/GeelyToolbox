package app.onepve.geelyconsole.utils;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import com.ecarx.eas.sdk.ECarXApiClient;
import com.ecarx.eas.sdk.mediacenter.MediaCenterAPI;
import com.ecarx.eas.sdk.mediacenter.MusicClient;
import com.ecarx.eas.sdk.mediacenter.MusicPlaybackInfo;

import java.util.List;

/**
 * 吉利 ECARX EAS 多媒体中心轻量桥接器
 * 核心职责：
 * 1. 蓝牙物理声道仲裁 (A2DP Sink 接管)：在【控制台独立接管】模式下，当手机蓝牙连入时主动选通 6 号蓝牙硬件通道并申请音频焦点，
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
    public static final int SOURCE_TYPE_BLUETOOTH = 6;

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
    private volatile boolean a2dpStreaming = false; // 底层真实推流态 (AUDIO_STATE_CHANGED STATE_STARTED)
    private BroadcastReceiver a2dpReceiver;
    private long lastA2dpWakeTime = 0;

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
        registerA2dpReceiver();
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
        } else {
            // 未连接蓝牙且非独立接管：注销 EAS 释放焦点，退让给米小江或原厂多媒体
            releaseEasRegistration();
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
                // 声明支持 6 号蓝牙物理声道
                mApi.updateMediaSourceTypeList(mToken, new int[]{SOURCE_TYPE_BLUETOOTH});
                mApi.declareMediaCenterCapability(mToken, new int[]{0, 2, 3});
                mApi.declareSupportCollectTypes(mToken, new int[]{0, 3, 4});
                AppLogger.i("音频通道", "EAS 注册成功，已声明蓝牙音频能力 (SourceType=6)");

                // 若手机蓝牙处于推流状态，立即激活蓝牙音频通路
                if (a2dpSinkConnected) {
                    activateBluetoothChannel();
                }
            }
        } catch (Throwable t) {
            AppLogger.e("音频通道", "EAS 注册异常: " + t.getMessage());
        }
    }

    /**
     * 选通原车蓝牙音频硬件通道并申请焦点
     */
    public synchronized void activateBluetoothChannel() {
        // 2026-09-17 修复：抑制窗口内严禁通道选通。
        // 真车日志 17:59:20.900：用户刚暂停（17:59:19.465）后 updateCurrentSourceType(6)
        // 仍被下发，选通动作会触发原车 EAS 重新仲裁音频源，把用户暂停顶回播放。
        // 仅跳过 play() 唤醒不够，通道选通本身也要在抑制窗口内拦截。
        if (isAutoWakeSuppressed()) {
            AppLogger.i("蓝牙音频", "处于用户暂停抑制窗口内，跳过蓝牙通道选通 (尊重用户暂停意图)");
            return;
        }
        try {
            ensureEasReady();
            if (mApi != null && mRegistered && mToken != null) {
                mApi.updateCurrentSourceType(mToken, SOURCE_TYPE_BLUETOOTH);
                AppLogger.i("蓝牙音频", "已下发 updateCurrentSourceType(6)，原车蓝牙音频物理通道已选通！");
            }
            // 关键：通知底层 com.android.bluetooth A2dpSink 激活音频焦点，解除 MT8666 音量为 0 与被动暂停限制
            wakeBluetoothAudioSink();

            // 申请系统音频焦点，采用车规闪避属性，与高德导航及车身播报混音共存
            AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            }
        } catch (Throwable t) {
            AppLogger.w("蓝牙音频", "选通蓝牙音频物理通道失败: " + t.getMessage());
        }
    }

    /**
     * 唤醒底层 com.android.bluetooth A2DP Sink 链路，使其主动向系统申请 AudioFocus 避免静音
     */
    public void wakeBluetoothAudioSink() {
        try {
            MediaSessionManager mm = (MediaSessionManager) appContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (mm != null) {
                List<MediaController> controllers = mm.getActiveSessions(null);
                if (controllers != null) {
                    for (MediaController mc : controllers) {
                        String pkg = mc.getPackageName();
                        if ("com.android.bluetooth".equals(pkg)) {
                            // 铁律：用户刚主动暂停 → 严禁自动 play() 顶回播放
                            if (System.currentTimeMillis() < autoWakeSuppressUntil) {
                                AppLogger.i("蓝牙音频", "处于用户暂停抑制窗口内，跳过 play() 唤醒（尊重用户暂停意图）");
                                return;
                            }
                            AppLogger.i("蓝牙音频", "命中蓝牙 MediaSession，下发 play() 唤醒底层 A2DP AudioFocus！");
                            mc.getTransportControls().play();
                            return;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            AppLogger.w("蓝牙音频", "通过 MediaSession 唤醒蓝牙音频焦点异常: " + t.getMessage());
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
                        AppLogger.i("蓝牙音频", "监听到蓝牙 A2DP-Sink 已连接 (connected)，准备选通音频通道并唤醒链路");
                        mainHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                activateBluetoothChannel();
                            }
                        }, 1000);
                    } else if (state == 0) {
                        a2dpSinkConnected = false;
                        a2dpStreaming = false; // 断开必然不再推流
                        AppLogger.i("蓝牙音频", "监听到蓝牙 A2DP-Sink 已断开，推流态同步复位");
                    }
                } else if ("android.bluetooth.a2dp-sink.profile.action.AUDIO_STATE_CHANGED".equals(action)) {
                    // 真实推流状态：只有 STATE_STARTED 才认为蓝牙音频流活跃 (connected != streaming)
                    int state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", -1);
                    boolean streaming = (state == A2DP_AUDIO_STATE_STARTED);
                    if (streaming != a2dpStreaming) {
                        a2dpStreaming = streaming;
                        AppLogger.i("蓝牙音频", "蓝牙 A2DP 推流状态跃变: streaming=" + streaming
                                + " (connected=" + a2dpSinkConnected + ")");
                    }
                    // 当手机端点开微信语音或音乐开始推流瞬间，毫秒级唤醒 A2DP Sink AudioFocus 与选通通道，杜绝无声与被动暂停
                    if (streaming) {
                        long now = System.currentTimeMillis();
                        if (now - lastA2dpWakeTime > 4000) {
                            lastA2dpWakeTime = now;
                            activateBluetoothChannel();
                        }
                    }
                }
                // 已移除 AVRCP TRACK_EVENT 触发的通道选通：
                // 该事件在「暂停 / 切歌 / 播放」时都会下发，据此无条件唤醒会在用户按下暂停
                // 后立刻把播放顶回来。真正的「手机端开始推流」已由上面 AUDIO_STATE_CHANGED
                // 的 STATE_STARTED (streaming=true) 分支覆盖，无需重复触发。
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED");
        filter.addAction("android.bluetooth.a2dp-sink.profile.action.AUDIO_STATE_CHANGED");
        try {
            appContext.registerReceiver(a2dpReceiver, filter);
        } catch (Throwable ignored) {}
    }
}