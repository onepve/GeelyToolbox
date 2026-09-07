package app.onepve.geelyconsole.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

/**
 * 蓝牙 A2DP 音频自动接管路由
 * 1. 监听车载蓝牙 A2DP-Sink 状态 (连接/推流)
 * 2. 手机端点播放时，自动申请 STREAM_MUSIC 瞬态音频焦点，打通车机底层声音通道
 * 3. 彻底免除“必须手动打开原厂多媒体点击蓝牙音乐才有声音”的繁琐限制
 */
public class BluetoothAudioRouter {

    private static final String TAG = "BluetoothAudioRouter";
    private static final String ACTION_A2DP_SINK_STATE = "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED";
    private static final String ACTION_A2DP_PLAYING_STATE = "android.bluetooth.a2dp-sink.profile.action.PLAYING_STATE_CHANGED";

    private final Context context;
    private final AudioManager audioManager;
    private BroadcastReceiver btReceiver;
    private boolean isRegistered = false;
    private AudioFocusRequest focusRequest;

    public BluetoothAudioRouter(Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
    }

    public synchronized void start() {
        if (isRegistered) return;
        try {
            btReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    String action = intent.getAction();
                    if (ACTION_A2DP_SINK_STATE.equals(action)) {
                        int state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", -1);
                        Log.i(TAG, "A2DP Sink connection state changed: " + state);
                        if (state == 2) { // STATE_CONNECTED
                            requestBluetoothAudioFocus();
                        }
                    } else if (ACTION_A2DP_PLAYING_STATE.equals(action) || "android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED".equals(action)) {
                        int state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", -1);
                        Log.i(TAG, "A2DP Playing state changed: " + state);
                        if (state == 10 || state == 1) { // 正在播放
                            requestBluetoothAudioFocus();
                        }
                    }
                }
            };

            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_A2DP_SINK_STATE);
            filter.addAction(ACTION_A2DP_PLAYING_STATE);
            filter.addAction("android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED");
            filter.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
            context.registerReceiver(btReceiver, filter);
            isRegistered = true;
            Log.i(TAG, "BluetoothAudioRouter registered successfully");
        } catch (Exception e) {
            Log.w(TAG, "Failed to register BluetoothAudioRouter: " + e.getMessage());
        }
    }

    public synchronized void stop() {
        if (!isRegistered) return;
        if (btReceiver != null) {
            try {
                context.unregisterReceiver(btReceiver);
            } catch (Exception ignored) {}
            btReceiver = null;
        }
        abandonBluetoothAudioFocus();
        isRegistered = false;
        Log.i(TAG, "BluetoothAudioRouter stopped");
    }

    private void requestBluetoothAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (focusRequest == null) {
                    AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build();
                    focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            .setAudioAttributes(playbackAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                                @Override
                                public void onAudioFocusChange(int focusChange) {
                                    Log.d(TAG, "Bluetooth AudioFocus change: " + focusChange);
                                }
                            })
                            .build();
                }
                audioManager.requestAudioFocus(focusRequest);
            } else {
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            }
            Log.i(TAG, "Requested AudioFocus for Bluetooth streaming successfully");
        } catch (Exception e) {
            Log.w(TAG, "Failed to request audio focus: " + e.getMessage());
        }
    }

    private void abandonBluetoothAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                audioManager.abandonAudioFocusRequest(focusRequest);
            } else {
                audioManager.abandonAudioFocus(null);
            }
        } catch (Exception ignored) {}
    }
}
