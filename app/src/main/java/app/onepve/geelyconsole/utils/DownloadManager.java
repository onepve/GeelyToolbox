package app.onepve.geelyconsole.utils;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadManager {

    private static final String TAG = "DownloadManager";

    public interface DownloadListener {
        void onProgress(String id, int progress, long downloadedBytes, long totalBytes, String speedStr);
        void onSuccess(String id, File savedFile);
        void onError(String id, String errorMsg);
        void onPaused(String id);
        void onCancelled(String id);
    }

    public static class DownloadTask {
        public final String id;
        public final String url;
        public final String filename;
        public volatile boolean isCancelled = false;
        public volatile boolean isPaused = false;
        public HttpURLConnection conn;
        public InputStream is;
        public FileOutputStream fos;

        public DownloadTask(String id, String url, String filename) {
            this.id = id;
            this.url = url;
            this.filename = filename;
        }
    }

    private static final Map<String, DownloadTask> activeTasks = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void startDownload(final String fileUrl, final String customFileName, final DownloadListener listener) {
        startDownload(customFileName, fileUrl, customFileName, listener);
    }

    public static void startDownload(final String taskId, final String fileUrl, final String customFileName, final DownloadListener listener) {
        final String id = (taskId != null && !taskId.isEmpty()) ? taskId : customFileName;

        // Cancel previous task with same ID if running
        cancelDownload(id);

        final DownloadTask task = new DownloadTask(id, fileUrl, customFileName);
        activeTasks.put(id, task);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                File tempFile = null;
                try {
                    File downloadDir = new File(Environment.getExternalStorageDirectory(), "Download");
                    if (!downloadDir.exists()) {
                        downloadDir.mkdirs();
                    }

                    String fileName = customFileName;
                    if (fileName == null || fileName.isEmpty()) {
                        fileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
                        if (fileName.contains("?")) {
                            fileName = fileName.substring(0, fileName.indexOf('?'));
                        }
                    }
                    if (fileName.isEmpty() || !fileName.contains(".")) {
                        fileName = "app_" + System.currentTimeMillis() + ".apk";
                    }

                    final File targetFile = new File(downloadDir, fileName);
                    tempFile = new File(downloadDir, fileName + ".tmp");

                    if (targetFile.exists()) {
                        targetFile.delete();
                    }
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }

                    String finalUrlStr = fileUrl;
                    if (finalUrlStr != null) {
                        String sep = finalUrlStr.contains("?") ? "&" : "?";
                        finalUrlStr = finalUrlStr + sep + "t=" + System.currentTimeMillis() + "&r=" + (int)(Math.random() * 100000);
                    }

                    URL url = new URL(finalUrlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    task.conn = conn;
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setUseCaches(false);
                    conn.setDefaultUseCaches(false);
                    conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
                    conn.setRequestProperty("Pragma", "no-cache");
                    conn.setRequestProperty("Expires", "0");
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9; IHU516) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36");

                    int responseCode = conn.getResponseCode();
                    if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                        activeTasks.remove(id);
                        notifyError(listener, id, "HTTP 响应错误: " + responseCode);
                        return;
                    }

                    long totalBytes = conn.getContentLengthLong();
                    InputStream is = conn.getInputStream();
                    task.is = is;
                    FileOutputStream fos = new FileOutputStream(tempFile);
                    task.fos = fos;

                    byte[] buffer = new byte[64 * 1024];
                    long downloaded = 0;
                    int read;
                    long lastUpdateTime = System.currentTimeMillis();
                    long bytesSinceLastUpdate = 0;

                    while ((read = is.read(buffer)) != -1) {
                        if (task.isCancelled) {
                            closeQuietly(fos);
                            closeQuietly(is);
                            disconnectQuietly(conn);
                            if (tempFile.exists()) tempFile.delete();
                            activeTasks.remove(id);
                            notifyCancelled(listener, id);
                            return;
                        }

                        if (task.isPaused) {
                            closeQuietly(fos);
                            closeQuietly(is);
                            disconnectQuietly(conn);
                            activeTasks.remove(id);
                            notifyPaused(listener, id);
                            return;
                        }

                        fos.write(buffer, 0, read);
                        downloaded += read;
                        bytesSinceLastUpdate += read;

                        long now = System.currentTimeMillis();
                        if (now - lastUpdateTime >= 300) {
                            double speedKb = (bytesSinceLastUpdate / 1024.0) / ((now - lastUpdateTime) / 1000.0);
                            String speedStr = (speedKb > 1024)
                                    ? String.format(Locale.ROOT, "%.2f MB/s", speedKb / 1024.0)
                                    : String.format(Locale.ROOT, "%.0f KB/s", speedKb);

                            int progress = (totalBytes > 0) ? (int) (downloaded * 100 / totalBytes) : 0;
                            notifyProgress(listener, id, progress, downloaded, totalBytes, speedStr);

                            lastUpdateTime = now;
                            bytesSinceLastUpdate = 0;
                        }
                    }

                    fos.flush();
                    closeQuietly(fos);
                    closeQuietly(is);
                    disconnectQuietly(conn);
                    activeTasks.remove(id);

                    if (targetFile.exists()) {
                        targetFile.delete();
                    }
                    boolean ok = tempFile.renameTo(targetFile);
                    if (ok && targetFile.exists() && targetFile.length() > 0) {
                        notifySuccess(listener, id, targetFile);
                    } else {
                        notifyError(listener, id, "临时文件重命名失败");
                    }
                } catch (Exception e) {
                    activeTasks.remove(id);
                    if (task.isCancelled) {
                        if (tempFile != null && tempFile.exists()) tempFile.delete();
                        notifyCancelled(listener, id);
                    } else if (task.isPaused) {
                        notifyPaused(listener, id);
                    } else {
                        Log.e(TAG, "Download error: " + e.getMessage());
                        notifyError(listener, id, e.getMessage());
                    }
                }
            }
        });
    }

    public static void pauseDownload(String id) {
        if (id == null) return;
        DownloadTask task = activeTasks.get(id);
        if (task != null) {
            task.isPaused = true;
        }
    }

    public static void cancelDownload(String id) {
        if (id == null) return;
        DownloadTask task = activeTasks.get(id);
        if (task != null) {
            task.isCancelled = true;
            try {
                if (task.conn != null) task.conn.disconnect();
            } catch (Exception ignored) {}
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try { closeable.close(); } catch (Exception ignored) {}
        }
    }

    private static void disconnectQuietly(HttpURLConnection conn) {
        if (conn != null) {
            try { conn.disconnect(); } catch (Exception ignored) {}
        }
    }

    private static void notifyProgress(final DownloadListener listener, final String id, final int progress, final long downloaded, final long total, final String speed) {
        if (listener == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onProgress(id, progress, downloaded, total, speed);
            }
        });
    }

    private static void notifySuccess(final DownloadListener listener, final String id, final File file) {
        if (listener == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onSuccess(id, file);
            }
        });
    }

    private static void notifyError(final DownloadListener listener, final String id, final String errorMsg) {
        if (listener == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onError(id, errorMsg);
            }
        });
    }

    private static void notifyPaused(final DownloadListener listener, final String id) {
        if (listener == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onPaused(id);
            }
        });
    }

    private static void notifyCancelled(final DownloadListener listener, final String id) {
        if (listener == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onCancelled(id);
            }
        });
    }
}
