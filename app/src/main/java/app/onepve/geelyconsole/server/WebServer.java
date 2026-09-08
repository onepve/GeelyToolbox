package app.onepve.geelyconsole.server;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import app.onepve.geelyconsole.utils.DownloadManager;
import app.onepve.geelyconsole.utils.SystemUtils;
import app.onepve.geelyconsole.utils.ThemePatcher;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class WebServer {

    private static final String TAG = "GeelyWebServer";

    public interface WebServerCallback {
        void onUrlPushed(String url, String fileName);
        void onFileUploaded(File file);
        void onActionRequested(String action);
        void onAdbCommandPushed(String command);
    }

    private static final int PORT = 8888;
    private final Context context;
    private final WebServerCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ServerSocket serverSocket;
    private boolean isRunning = false;

    public WebServer(Context context, WebServerCallback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    public synchronized void start() {
        if (isRunning) return;
        isRunning = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(PORT);
                    while (isRunning && !serverSocket.isClosed()) {
                        try {
                            Socket client = serverSocket.accept();
                            handleClient(client);
                        } catch (Exception e) {
                            if (!isRunning) break;
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    isRunning = false;
                }
            }
        }, "GeelyWebServer").start();
    }

    public synchronized void stop() {
        isRunning = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void handleClient(final Socket socket) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream in = socket.getInputStream();
                    OutputStream out = socket.getOutputStream();

                    // Read request line and headers
                    ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
                    int b;
                    int consecutiveNewlines = 0;
                    while ((b = in.read()) != -1) {
                        headerBuf.write(b);
                        if (b == '\n') {
                            consecutiveNewlines++;
                            if (consecutiveNewlines == 2 || (headerBuf.size() >= 4 && headerBuf.toString().endsWith("\r\n\r\n"))) {
                                break;
                            }
                        } else if (b != '\r') {
                            consecutiveNewlines = 0;
                        }
                    }

                    String headerStr = headerBuf.toString(StandardCharsets.UTF_8.name());
                    String[] lines = headerStr.split("\r\n");
                    if (lines.length == 0 || lines[0].isEmpty()) {
                        socket.close();
                        return;
                    }

                    String[] reqParts = lines[0].split(" ");
                    String method = reqParts[0];
                    String path = reqParts.length > 1 ? reqParts[1] : "/";

                    Map<String, String> headers = new HashMap<>();
                    for (int i = 1; i < lines.length; i++) {
                        int idx = lines[i].indexOf(":");
                        if (idx > 0) {
                            headers.put(lines[i].substring(0, idx).trim().toLowerCase(), lines[i].substring(idx + 1).trim());
                        }
                    }

                    int contentLength = 0;
                    if (headers.containsKey("content-length")) {
                        try {
                            contentLength = Integer.parseInt(headers.get("content-length"));
                        } catch (Exception ignored) {
                        }
                    }

                    if ("/api/status".equals(path) && "GET".equalsIgnoreCase(method)) {
                        handleApiStatus(out);
                    } else if ("/api/list_downloads".equals(path) && "GET".equalsIgnoreCase(method)) {
                        handleApiListDownloads(out);
                    } else if (path.startsWith("/api/download") && "GET".equalsIgnoreCase(method)) {
                        handleApiDownloadFile(path, out);
                    } else if ("/api/push_url".equals(path) && "POST".equalsIgnoreCase(method)) {
                        handleApiPushUrl(in, contentLength, out);
                    } else if ("/api/upload_chunk".equals(path) && "POST".equalsIgnoreCase(method)) {
                        handleApiUploadChunk(in, contentLength, headers, out);
                    } else if ("/api/action".equals(path) && "POST".equalsIgnoreCase(method)) {
                        handleApiAction(in, contentLength, out);
                    } else if ("/api/push_cmd".equals(path) && "POST".equalsIgnoreCase(method)) {
                        handleApiPushCmd(in, contentLength, out);
                    } else if ("/api/get_tts".equals(path)) {
                        handleApiGetTts(out);
                    } else if ("/api/save_tts".equals(path) && "POST".equalsIgnoreCase(method)) {
                        handleApiSaveTts(in, contentLength, out);
                    } else {
                        handleWebPage(out);
                    }

                    out.flush();
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private void handleWebPage(OutputStream out) throws IOException {
        String html = getWebHtml();
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
    }

    private void handleApiStatus(OutputStream out) throws IOException {
        SystemUtils.MemInfo mem = SystemUtils.getMemInfo();
        SystemUtils.StorageInfo storage = SystemUtils.getStorageInfo();
        String dynamicCode = SystemUtils.calculateDynamicCode();
        String dynamicCodePlus5 = SystemUtils.calculateDynamicCodePlus5();
        boolean lockActive = SystemUtils.isPlaceholderLockActive();

        String json = String.format("{\"code\":\"%s\",\"code_plus5\":\"%s\",\"mem\":\"%s\",\"storage\":\"%s\",\"lock\":%b,\"status\":\"ok\"}",
                dynamicCode, dynamicCodePlus5, mem.getSummary(), storage.getSummary(), lockActive);

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
    }

    private void handleApiListDownloads(OutputStream out) throws IOException {
        File downloadDir = SystemUtils.getAppDownloadDir();
        JSONArray array = new JSONArray();
        if (downloadDir.exists() && downloadDir.isDirectory()) {
            File[] files = downloadDir.listFiles();
            if (files != null) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
                for (File f : files) {
                    if (f.isFile()) {
                        JSONObject obj = new JSONObject();
                        try {
                            obj.put("name", f.getName());
                            obj.put("size", f.length());
                            obj.put("sizeFormatted", formatFileSize(f.length()));
                            obj.put("time", f.lastModified());
                            obj.put("timeFormatted", sdf.format(new Date(f.lastModified())));
                            array.put(obj);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }

        JSONObject root = new JSONObject();
        try {
            root.put("status", "ok");
            root.put("files", array);
        } catch (Exception ignored) {
        }

        byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
    }

    private void handleApiDownloadFile(String path, OutputStream out) throws IOException {
        String fileName = "";
        int qIdx = path.indexOf("?file=");
        if (qIdx != -1) {
            fileName = path.substring(qIdx + 6);
        } else if (path.startsWith("/api/download/")) {
            fileName = path.substring("/api/download/".length());
        }

        try {
            fileName = URLDecoder.decode(fileName, "UTF-8");
        } catch (Exception ignored) {
        }

        if (fileName.isEmpty() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            String err = "{\"status\":\"error\",\"message\":\"非法文件名\"}";
            byte[] errBytes = err.getBytes(StandardCharsets.UTF_8);
            String header = "HTTP/1.1 400 Bad Request\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n" +
                    "Content-Length: " + errBytes.length + "\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(errBytes);
            return;
        }

        File downloadDir = SystemUtils.getAppDownloadDir();
        File targetFile = new File(downloadDir, fileName);
        if (!targetFile.exists() || !targetFile.isFile()) {
            String err = "{\"status\":\"error\",\"message\":\"文件不存在\"}";
            byte[] errBytes = err.getBytes(StandardCharsets.UTF_8);
            String header = "HTTP/1.1 404 Not Found\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n" +
                    "Content-Length: " + errBytes.length + "\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(errBytes);
            return;
        }

        long fileLen = targetFile.length();
        String encodedName = URLEncoder.encode(targetFile.getName(), "UTF-8").replace("+", "%20");
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/octet-stream\r\n" +
                "Content-Length: " + fileLen + "\r\n" +
                "Content-Disposition: attachment; filename=\"" + targetFile.getName().replace("\"", "_") + "\"; filename*=UTF-8''" + encodedName + "\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));

        try (FileInputStream fis = new FileInputStream(targetFile)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format(Locale.CHINA, "%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format(Locale.CHINA, "%.1f MB", size / (1024.0 * 1024.0));
        return String.format(Locale.CHINA, "%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    private void handleApiPushUrl(InputStream in, int length, OutputStream out) throws IOException {
        byte[] body = readExactBytes(in, length);
        String bodyStr = new String(body, StandardCharsets.UTF_8);

        String pushUrl = "";
        String fileName = "";
        if (bodyStr.startsWith("{")) {
            // simple json parse
            pushUrl = extractJsonValue(bodyStr, "url");
            fileName = extractJsonValue(bodyStr, "name");
        } else {
            // form-urlencoded
            String[] pairs = bodyStr.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    String k = URLDecoder.decode(kv[0], "UTF-8");
                    String v = URLDecoder.decode(kv[1], "UTF-8");
                    if ("url".equals(k)) pushUrl = v;
                    if ("name".equals(k)) fileName = v;
                }
            }
        }

        final String finalUrl = pushUrl;
        final String finalName = fileName;
        if (!finalUrl.isEmpty()) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onUrlPushed(finalUrl, finalName);
                    }
                }
            });
        }

        String resp = "{\"success\":true,\"message\":\"已向车机推送下载任务\"}";
        sendJsonResponse(out, resp);
    }

    private String getUniqueFileName(File dir, String rawName) {
        if (dir == null || rawName == null) return rawName;
        File file = new File(dir, rawName);
        if (!file.exists()) return rawName;

        String name = rawName;
        String ext = "";
        int dot = rawName.lastIndexOf('.');
        if (dot > 0) {
            name = rawName.substring(0, dot);
            ext = rawName.substring(dot);
        }

        int idx = 1;
        while (file.exists()) {
            String newName = name + "_" + idx + ext;
            file = new File(dir, newName);
            idx++;
        }
        return file.getName();
    }

    private void handleApiUploadChunk(InputStream in, int length, Map<String, String> headers, OutputStream out) throws IOException {
        String fileName = headers.get("x-file-name");
        if (fileName != null) {
            fileName = URLDecoder.decode(fileName, "UTF-8");
        } else {
            fileName = "upload_" + System.currentTimeMillis() + ".apk";
        }

        long chunkOffset = 0;
        if (headers.containsKey("x-chunk-offset")) {
            try {
                chunkOffset = Long.parseLong(headers.get("x-chunk-offset"));
            } catch (Exception ignored) {
            }
        }

        boolean isLastChunk = "true".equalsIgnoreCase(headers.get("x-last-chunk"));

        File downloadDir = SystemUtils.getAppDownloadDir();
        if (!downloadDir.exists()) downloadDir.mkdirs();

        // 仅在分片 offset == 0 且未经过重命名处理时检测同名冲突并自动重命名
        if (chunkOffset == 0 && !"true".equalsIgnoreCase(headers.get("x-name-resolved"))) {
            fileName = getUniqueFileName(downloadDir, fileName);
        }

        final File targetFile = new File(downloadDir, fileName);

        RandomAccessFile raf = new RandomAccessFile(targetFile, "rw");
        raf.seek(chunkOffset);

        byte[] buf = new byte[16 * 1024];
        int remaining = length;
        while (remaining > 0) {
            int toRead = Math.min(buf.length, remaining);
            int read = in.read(buf, 0, toRead);
            if (read == -1) break;
            raf.write(buf, 0, read);
            remaining -= read;
        }
        raf.close();

        if (isLastChunk) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onFileUploaded(targetFile);
                    }
                }
            });
        }

        String resp = "{\"success\":true,\"offset\":" + (chunkOffset + length) + ",\"done\":" + isLastChunk + ",\"savedName\":\"" + fileName + "\"}";
        sendJsonResponse(out, resp);
    }

    private void handleApiGetTts(OutputStream out) throws IOException {
        android.content.SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
        JSONObject root = new JSONObject();
        String[] keys = {
            "door_fl", "door_fl_close", "door_fr", "door_fr_close",
            "door_rl", "door_rl_close", "door_rr", "door_rr_close",
            "trunk_open", "trunk_close",
            "gear_d", "gear_r", "gear_p", "gear_n",
            "mode_smart", "mode_comfort", "mode_eco", "mode_sport",
            "flameout"
        };
        try {
            for (String k : keys) {
                String val = prefs.getString("custom_voice_text_" + k, "");
                if (val.isEmpty()) {
                    val = prefs.getString("custom_text_" + k + ".mp3", "");
                }
                root.put(k, val);
            }
        } catch (Exception ignored) {}
        sendJsonResponse(out, root.toString());
    }

    private void handleApiSaveTts(InputStream in, int length, OutputStream out) throws IOException {
        byte[] body = readExactBytes(in, length);
        String bodyStr = new String(body, StandardCharsets.UTF_8);
        try {
            JSONObject json = new JSONObject(bodyStr);
            android.content.SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            java.util.Iterator<String> it = json.keys();
            while (it.hasNext()) {
                String k = it.next();
                String text = json.optString(k, "").trim();
                editor.putString("custom_voice_text_" + k, text);
                editor.putString("custom_text_" + k + ".mp3", text);
            }
            editor.commit();
            sendJsonResponse(out, "{\"success\":true,\"message\":\"台词已成功保存并实时同步至车机！\"}");
        } catch (Exception e) {
            sendJsonResponse(out, "{\"success\":false,\"message\":\"解析失败: " + e.getMessage() + "\"}");
        }
    }

    private void handleApiAction(InputStream in, int length, OutputStream out) throws IOException {
        byte[] body = readExactBytes(in, length);
        String bodyStr = new String(body, StandardCharsets.UTF_8);
        final String action = extractJsonValue(bodyStr, "action");

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (callback != null) {
                    callback.onActionRequested(action);
                }
            }
        });

        sendJsonResponse(out, "{\"success\":true,\"action\":\"" + action + "\"}");
    }

    private void handleApiPushCmd(InputStream in, int length, OutputStream out) throws IOException {
        byte[] body = readExactBytes(in, length);
        String bodyStr = new String(body, StandardCharsets.UTF_8);

        String cmd = "";
        if (bodyStr.startsWith("{")) {
            cmd = extractJsonValue(bodyStr, "cmd");
        } else {
            String[] pairs = bodyStr.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    String k = URLDecoder.decode(kv[0], "UTF-8");
                    String v = URLDecoder.decode(kv[1], "UTF-8");
                    if ("cmd".equals(k)) cmd = v;
                }
            }
        }

        final String finalCmd = cmd.trim();
        if (!finalCmd.isEmpty()) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onAdbCommandPushed(finalCmd);
                    }
                }
            });
        }

        String resp = "{\"success\":true,\"message\":\"ADB 指令已推送至车机屏幕，请在车机大屏核对后点击执行\"}";
        sendJsonResponse(out, resp);
    }

    private void sendJsonResponse(OutputStream out, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Length: " + bytes.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
    }

    private byte[] readExactBytes(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(data, offset, length - offset);
            if (read == -1) break;
            offset += read;
        }
        return data;
    }

    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int idx = json.indexOf(pattern);
        if (idx != -1) {
            int start = idx + pattern.length();
            int end = json.indexOf("\"", start);
            if (end != -1) {
                return json.substring(start, end);
            }
        }
        return "";
    }

    private String getWebHtml() {
        try (InputStream in = context.getAssets().open("mobile_web.html");
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            Log.w(TAG, "Failed to load mobile_web.html from assets: " + e.getMessage());
            return "<!DOCTYPE html><html><body><h3>吉利车机无线快传</h3><p>资源加载异常，请重启应用重试。</p></body></html>";
        }
    }
}
