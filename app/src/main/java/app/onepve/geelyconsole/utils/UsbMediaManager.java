package app.onepve.geelyconsole.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 车载 USB 音乐存储与自动识别管理器
 * 1. 监听 Android 系统标准的 USB 盘插拔广播 (ACTION_MEDIA_MOUNTED / USB_DEVICE_ATTACHED)
 * 2. 插入 USB 闪存盘后：
 *    - 毫秒级识别物理 USB 与挂载事件
 *    - 语音温婉提醒：“检测到外部U盘接入”
 *    - 异步快速检索 U 盘内音频资产 (.mp3, .flac, .wav)
 * 3. 遵循隐私与防打扰原则，防抖 3 秒避免连报
 */
public class UsbMediaManager {

    private static final String TAG = "UsbMediaManager";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver usbReceiver;
    private boolean isRegistered = false;
    private VehicleVoicePlayer voicePlayer;
    private long lastUsbNotifyTime = 0;

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
                    if (intent == null) return;
                    String action = intent.getAction();
                    Uri data = intent.getData();
                    String path = (data != null) ? data.getPath() : "";
                    Log.i(TAG, "USB broadcast received: " + action + ", path: " + path);

                    boolean isAttach = Intent.ACTION_MEDIA_MOUNTED.equals(action) ||
                                       "android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(action);
                    boolean isDetach = Intent.ACTION_MEDIA_UNMOUNTED.equals(action) ||
                                       Intent.ACTION_MEDIA_EJECT.equals(action) ||
                                       "android.hardware.usb.action.USB_DEVICE_DETACHED".equals(action);

                    if (isAttach) {
                        onUsbMounted(path);
                    } else if (isDetach) {
                        onUsbEjected(path);
                    }
                }
            };

            // 1. 标准存储挂载 scheme 广播
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            filter.addAction(Intent.ACTION_MEDIA_EJECT);
            filter.addDataScheme("file");
            context.registerReceiver(usbReceiver, filter);

            // 2. 物理 USB 设备硬件接入广播（无 scheme）
            IntentFilter usbFilter = new IntentFilter();
            usbFilter.addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED");
            usbFilter.addAction("android.hardware.usb.action.USB_DEVICE_DETACHED");
            context.registerReceiver(usbReceiver, usbFilter);

            isRegistered = true;
            Log.i(TAG, "UsbMediaManager dual-layer listeners registered successfully");
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
        long now = System.currentTimeMillis();
        if (now - lastUsbNotifyTime < 3000) {
            Log.d(TAG, "USB notify debounced (within 3s)");
            return;
        }
        lastUsbNotifyTime = now;
        Log.i(TAG, "USB Mounted/Attached at: " + mountPath);

        if (voicePlayer != null) {
            voicePlayer.play("usb_in.mp3", "检测到外部U盘接入");
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
                    File root = (mountPath != null && !mountPath.isEmpty()) ? new File(mountPath) : null;
                    if (root == null || !root.exists() || !root.canRead()) {
                        File otg = new File("/storage/usbotg");
                        if (otg.exists() && otg.canRead()) root = otg;
                        else {
                            File mntRw = new File("/mnt/media_rw");
                            if (mntRw.exists() && mntRw.canRead()) {
                                File[] subs = mntRw.listFiles();
                                if (subs != null && subs.length > 0) root = subs[0];
                            }
                        }
                    }

                    if (root == null) return;
                    List<File> audioFiles = new ArrayList<>();
                    scanAudios(root, audioFiles, 0);

                    if (!audioFiles.isEmpty()) {
                        final int count = audioFiles.size();
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (voicePlayer != null) {
                                    voicePlayer.play("usb_scan_done.mp3", "已发现" + count + "首U盘音乐");
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
        long now = System.currentTimeMillis();
        if (now - lastUsbNotifyTime < 3000) return;
        lastUsbNotifyTime = now;
        Log.i(TAG, "USB Ejected/Detached: " + path);
        if (voicePlayer != null) {
            voicePlayer.play("usb_out.mp3", "外部U盘已移除");
        }
    }
}
