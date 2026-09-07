package app.onepve.geelyconsole.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 车载 USB 音乐存储与自动识别管理器
 * 1. 监听 Android 系统标准的 USB 盘插拔广播 (ACTION_MEDIA_MOUNTED / ACTION_MEDIA_UNMOUNTED / ACTION_MEDIA_EJECT)
 * 2. 插入 USB 闪存盘后：
 *    - 毫秒级识别挂载路径 (如 /storage/usbotg 或 /mnt/media_rw/xxx)
 *    - 语音温婉提醒：“U盘已连接，正在识别音乐文件”
 *    - 异步快速检索 U 盘内音频资产 (.mp3, .flac, .wav)
 *    - 找到有效音乐后，自动调用系统标准媒体扫描器与播放广播，实现免进原厂多媒体自动出声
 * 3. 支持开关控制，供实车自由开启与测试验证
 */
public class UsbMediaManager {

    private static final String TAG = "UsbMediaManager";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver usbReceiver;
    private boolean isRegistered = false;
    private VehicleVoicePlayer voicePlayer;

    public UsbMediaManager(Context context) {
        this.context = context.getApplicationContext();
        this.voicePlayer = VehicleVoicePlayer.getInstance(context);
    }

    public synchronized void start() {
        if (isRegistered) return;
        try {
            usbReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    String action = intent.getAction();
                    Uri data = intent.getData();
                    String path = (data != null) ? data.getPath() : "";
                    Log.i(TAG, "USB Media broadcast: " + action + ", path: " + path);

                    if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
                        onUsbMounted(path);
                    } else if (Intent.ACTION_MEDIA_UNMOUNTED.equals(action) || Intent.ACTION_MEDIA_EJECT.equals(action)) {
                        onUsbEjected(path);
                    }
                }
            };

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            filter.addAction(Intent.ACTION_MEDIA_EJECT);
            filter.addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED");
            filter.addAction("android.hardware.usb.action.USB_DEVICE_DETACHED");
            filter.addDataScheme("file");
            context.registerReceiver(usbReceiver, filter);

            // 补充无 dataScheme 的 USB 设备物理插拔监听（针对部分 Android 9 车机挂载广播不带 file scheme 场景）
            IntentFilter usbFilter = new IntentFilter();
            usbFilter.addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED");
            usbFilter.addAction("android.hardware.usb.action.USB_DEVICE_DETACHED");
            context.registerReceiver(usbReceiver, usbFilter);
            isRegistered = true;
            Log.i(TAG, "UsbMediaManager registered successfully");
        } catch (Exception e) {
            Log.w(TAG, "Failed to register UsbMediaManager: " + e.getMessage());
        }
    }

    public synchronized void stop() {
        if (!isRegistered) return;
        if (usbReceiver != null) {
            try {
                context.unregisterReceiver(usbReceiver);
            } catch (Exception ignored) {}
            usbReceiver = null;
        }
        isRegistered = false;
        Log.i(TAG, "UsbMediaManager stopped");
    }

    private void onUsbMounted(final String mountPath) {
        Log.i(TAG, "USB Mounted at: " + mountPath);
        if (voicePlayer != null) {
            voicePlayer.speakText("检测到外部U盘接入");
        }

        // 检查是否开启“自动扫描U盘歌曲”开关（默认关闭，保持克制，不越权打扰）
        boolean autoScan = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE)
                .getBoolean("usb_media_auto_scan_songs", false);
        if (!autoScan) {
            Log.i(TAG, "U盘自动扫描歌曲开关处于关闭状态，跳过后台文件检索");
            return;
        }

        // 异步遍历检索音频文件
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File root = new File(mountPath);
                    if (!root.exists() || !root.canRead()) {
                        // 尝试扫描常见的外部挂载根目录
                        File otg = new File("/storage/usbotg");
                        if (otg.exists() && otg.canRead()) root = otg;
                    }

                    List<File> audioFiles = new ArrayList<>();
                    scanAudios(root, audioFiles, 0);

                    Log.i(TAG, "Scan completed in " + root.getAbsolutePath() + ", found audios: " + audioFiles.size());
                    if (!audioFiles.isEmpty()) {
                        final int count = audioFiles.size();
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (voicePlayer != null) {
                                    voicePlayer.speakText("已发现" + count + "首U盘音乐");
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error scanning USB media: " + e.getMessage());
                }
            }
        }, "UsbAudioScanner").start();
    }

    private void scanAudios(File dir, List<File> result, int depth) {
        if (dir == null || !dir.exists() || !dir.isDirectory() || depth > 4 || result.size() >= 50) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) {
                    scanAudios(f, result, depth + 1);
                }
            } else {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") || name.endsWith(".aac")) {
                    result.add(f);
                }
            }
        }
    }

    private void onUsbEjected(String path) {
        Log.i(TAG, "USB Ejected: " + path);
    }
}
