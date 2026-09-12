package app.onepve.geelyconsole.utils;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
 */
public class EasMediaBridge {
    private static final String TAG = "EasMediaBridge";
    public static final String PKG_BLUETOOTH = "com.android.bluetooth";
    public static final int SOURCE_TYPE_BLUETOOTH = 6;

    private static volatile EasMediaBridge sInstance;
    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private MediaCenterAPI mApi;
    private Object mToken;
    private boolean mRegistered = false;
    private boolean mInitStarted = false;

    private boolean a2dpSinkConnected = false;
    private BroadcastReceiver a2dpReceiver;

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

        if ("toolbox_alone".equals(mode)) {
            // 控制台独立接管：确保 EAS 初始化并注册
            ensureEasReady();
        } else {
            // 米小江优先或原厂默认：注销 EAS 释放焦点，退让给米小江或原厂多媒体
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
                @Override
                public boolean onPlay() {
                    AppLogger.i("音频通道", "EAS 回调: onPlay -> 分发媒体播放键");
                    sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                    return true;
                }

                @Override
                public boolean onPause() {
                    AppLogger.i("音频通道", "EAS 回调: onPause -> 分发媒体暂停键");
                    sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                    return true;
                }

                @Override
                public boolean onNext() {
                    AppLogger.i("音频通道", "EAS 回调: onNext -> 分发下一曲");
                    sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT);
                    return true;
                }

                @Override
                public boolean onPrevious() {
                    AppLogger.i("音频通道", "EAS 回调: onPrevious -> 分发上一曲");
                    sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
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

                // 若手机蓝牙处于连接状态，立即激活蓝牙音频通路
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
        if (!"toolbox_alone".equals(currentControlMode)) {
            return;
        }
        try {
            if (mApi != null && mRegistered && mToken != null) {
                mApi.updateCurrentSourceType(mToken, SOURCE_TYPE_BLUETOOTH);
                AppLogger.i("音频通道", "已下发 updateCurrentSourceType(6)，原车蓝牙音频物理通道已选通！");
            }
            // 申请系统音频焦点，确保功放路由给蓝牙
            AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }
        } catch (Throwable t) {
            AppLogger.w("音频通道", "选通蓝牙音频物理通道失败: " + t.getMessage());
        }
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
            info.setAppName("吉利智驾");
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
     * 监听 A2DP Sink 连接状态
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
                int state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", -1);
                if (state == 2) {
                    a2dpSinkConnected = true;
                    AppLogger.i("音频通道", "监听到蓝牙 A2DP-Sink 已连接，准备选通音频通道");
                    mainHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            activateBluetoothChannel();
                        }
                    }, 1000);
                } else if (state == 0) {
                    a2dpSinkConnected = false;
                    AppLogger.i("音频通道", "监听到蓝牙 A2DP-Sink 已断开");
                }
            }
        };

        IntentFilter filter = new IntentFilter("android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED");
        try {
            appContext.registerReceiver(a2dpReceiver, filter);
        } catch (Throwable ignored) {}
    }

    private void sendMediaKeyEvent(int keyCode) {
        try {
            new SteeringWheelKeyManager(appContext).sendMediaKeyEventPublic(keyCode);
        } catch (Throwable t) {
            Log.w(TAG, "sendMediaKeyEvent error: " + t.getMessage());
        }
    }
}
