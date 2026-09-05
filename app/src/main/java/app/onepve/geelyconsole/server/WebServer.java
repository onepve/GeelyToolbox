package app.onepve.geelyconsole.server;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

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
        return "<!DOCTYPE html>\n" +
                "<html lang=\"zh-CN\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">\n" +
                "    <title>吉利工具箱 · 手机无线快传</title>\n" +
                "    <style>\n" +
                "        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif; }\n" +
                "        body { background: #ebeff4; color: #1e293b; padding: 16px; display: flex; justify-content: center; }\n" +
                "        .container { width: 100%; max-width: 480px; background: #ffffff; border-radius: 16px; border: 1px solid #d1d8e2; padding: 20px; box-shadow: 0 4px 16px rgba(0,0,0,0.04); }\n" +
                "        .header { text-align: center; margin-bottom: 20px; }\n" +
                "        .title { font-size: 20px; font-weight: bold; color: #0284c7; }\n" +
                "        .subtitle { font-size: 13px; color: #64748b; margin-top: 4px; }\n" +
                "        .card { background: #f8fafc; border-radius: 12px; border: 1px solid #e2e8f0; padding: 14px; margin-bottom: 16px; }\n" +
                "        .card-title { font-size: 14px; font-weight: bold; color: #334155; margin-bottom: 10px; display: flex; align-items: center; justify-content: space-between; }\n" +
                "        .input-group { margin-bottom: 12px; }\n" +
                "        .input-label { font-size: 12px; color: #64748b; margin-bottom: 6px; display: block; }\n" +
                "        input[type=\"text\"], input[type=\"file\"] { width: 100%; padding: 10px 12px; border: 1.5px solid #cbd5e1; border-radius: 8px; font-size: 14px; background: #ffffff; outline: none; }\n" +
                "        input[type=\"text\"]:focus { border-color: #0284c7; }\n" +
                "        .btn { width: 100%; padding: 12px; border: none; border-radius: 8px; font-size: 14px; font-weight: bold; cursor: pointer; text-align: center; white-space: nowrap; transition: 0.2s; }\n" +
                "        .btn-primary { background: #0284c7; color: #ffffff; }\n" +
                "        .btn-primary:active { background: #0369a1; }\n" +
                "        .btn-secondary { background: #ffffff; color: #1e293b; border: 1.5px solid #cbd5e1; margin-top: 8px; }\n" +
                "        .btn-secondary:active { background: #f1f5f9; }\n" +
                "        .progress-bar { width: 100%; height: 8px; background: #e2e8f0; border-radius: 4px; overflow: hidden; margin-top: 10px; display: none; }\n" +
                "        .progress-fill { height: 100%; width: 0%; background: #10b981; transition: width 0.2s; }\n" +
                "        .status-badge { display: inline-block; padding: 4px 8px; border-radius: 6px; font-size: 12px; font-weight: bold; background: #e0f2fe; color: #0284c7; }\n" +
                "        .tip-box { background: #fffbeb; border: 1px solid #fde68a; border-radius: 8px; padding: 10px; font-size: 12px; color: #d97706; line-height: 1.5; margin-top: 12px; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"container\">\n" +
                "        <div class=\"header\">\n" +
                "            <div class=\"title\">吉利车机无线快传</div>\n" +
                "            <div class=\"subtitle\">免车机打字 · 极速直传至 /sdcard/Download/!车机应用/</div>\n" +
                "        </div>\n" +
                "        <div class=\"card\">\n" +
                "            <div class=\"card-title\"><span>🚀 推送下载链接到车机</span><span class=\"status-badge\" id=\"car-status\">车机在线</span></div>\n" +
                "            <div class=\"input-group\">\n" +
                "                <label class=\"input-label\">直接粘贴外部 APK 直链下载地址：</label>\n" +
                "                <input type=\"text\" id=\"push-url\" placeholder=\"https://example.com/app.apk\">\n" +
                "            </div>\n" +
                "            <button class=\"btn btn-primary\" onclick=\"pushUrl()\">一键推送车机下载</button>\n" +
                "        </div>\n" +
                "        <div class=\"card\">\n" +
                "            <div class=\"card-title\"><span>📁 手机本地 APK 秒传到车机</span></div>\n" +
                "            <div class=\"input-group\">\n" +
                "                <label class=\"input-label\">选择手机中的 APK 安装包：</label>\n" +
                "                <input type=\"file\" id=\"file-input\" accept=\".apk,.zip,.xtz\">\n" +
                "            </div>\n" +
                "            <button class=\"btn btn-primary\" onclick=\"uploadFile()\">开始局域网高速传输</button>\n" +
                "            <div class=\"progress-bar\" id=\"progress-bar\"><div class=\"progress-fill\" id=\"progress-fill\"></div></div>\n" +
                "            <div id=\"upload-status\" style=\"font-size:12px; color:#64748b; margin-top:6px; text-align:center;\"></div>\n" +
                "        </div>\n" +
                "        <div class=\"card\">\n" +
                "            <div class=\"card-title\"><span>💻 手机推送 ADB 命令到车机</span></div>\n" +
                "            <div class=\"input-group\">\n" +
                "                <label class=\"input-label\">输入或粘贴 Shell / ADB 指令：</label>\n" +
                "                <textarea id=\"adb-cmd\" rows=\"3\" style=\"width: 100%; padding: 10px 12px; border: 1.5px solid #cbd5e1; border-radius: 8px; font-size: 13.5px; font-family: monospace; outline: none; resize: vertical;\" placeholder=\"例如: pm list packages -3\"></textarea>\n" +
                "            </div>\n" +
                "            <button class=\"btn btn-primary\" onclick=\"pushAdbCmd()\">📡 推送命令到车机屏幕</button>\n" +
                "            <div style=\"font-size: 11.5px; color: #64748b; margin-top: 6px; line-height: 1.45;\">\n" +
                "                🛡️ <b>安全机制</b>：指令仅推送到车机【ADB 终端】输入框中，不会自动静默执行。请在车机大屏上人工核验后点击【▶️ 执行】确认！\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        <div class=\"card\">\n" +
                "            <div class=\"card-title\"><span>📥 车机文件反向导出到手机</span><button class=\"btn btn-secondary\" style=\"width:auto; padding:4px 10px; font-size:12px; margin-top:0;\" onclick=\"loadCarFiles()\">🔄 刷新列表</button></div>\n" +
                "            <div style=\"font-size: 12px; color: #64748b; margin-bottom: 8px;\">点击即可将车机 /sdcard/Download/!车机应用/ 下的日志与 APK 下回到手机：</div>\n" +
                "            <div id=\"car-files-box\" style=\"max-height: 220px; overflow-y: auto; border: 1px solid #e2e8f0; border-radius: 8px; background: #ffffff;\">\n" +
                "                <div style=\"text-align: center; color: #94a3b8; font-size: 12px; padding: 16px;\">正在加载车机文件列表...</div>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        <div class=\"card\">\n" +
                "            <div class=\"card-title\"><span>⚡ 车机远程快捷指令</span></div>\n" +
                "            <button class=\"btn btn-secondary\" onclick=\"sendAction('open_files')\">📁 调起车机原生文件管理</button>\n" +
                "            <button class=\"btn btn-secondary\" onclick=\"sendAction('soft_reboot')\">⚡ 软重启车机</button>\n" +
                "        </div>\n" +
                "        <div class=\"tip-box\">\n" +
                "            🛡️ 安全提示：传输完成后，请在车机端打开原生文件管理直接点击安装，严禁使用 ADB 直装破坏空调界面。\n" +
                "        </div>\n" +
                "    </div>\n" +
                "    <script>\n" +
                "        async function loadCarFiles() {\n" +
                "            const box = document.getElementById('car-files-box');\n" +
                "            box.innerHTML = '<div style=\"text-align: center; color: #94a3b8; font-size: 12px; padding: 16px;\">正在加载车机文件列表...</div>';\n" +
                "            try {\n" +
                "                const res = await fetch('/api/list_downloads');\n" +
                "                const data = await res.json();\n" +
                "                if (!data.files || data.files.length === 0) {\n" +
                "                    box.innerHTML = '<div style=\"text-align: center; color: #94a3b8; font-size: 12px; padding: 16px;\">车机下载目录暂无文件</div>';\n" +
                "                    return;\n" +
                "                }\n" +
                "                let html = '';\n" +
                "                data.files.forEach(f => {\n" +
                "                    const isLog = f.name.endsWith('.log') || f.name.endsWith('.txt');\n" +
                "                    const icon = isLog ? '📋' : (f.name.endsWith('.apk') ? '📦' : '📄');\n" +
                "                    html += `<div style=\"display:flex; align-items:center; justify-content:space-between; padding:8px 10px; border-bottom:1px solid #f1f5f9;\">` +\n" +
                "                            `  <div style=\"overflow:hidden; margin-right:8px;\">` +\n" +
                "                            `    <div style=\"font-size:13px; font-weight:600; color:#1e293b; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;\">${icon} ${f.name}</div>` +\n" +
                "                            `    <div style=\"font-size:11px; color:#64748b;\">${f.sizeFormatted} · ${f.timeFormatted}</div>` +\n" +
                "                            `  </div>` +\n" +
                "                            `  <a href=\"/api/download?file=${encodeURIComponent(f.name)}\" download=\"${f.name}\" style=\"text-decoration:none;\"><button class=\"btn btn-primary\" style=\"width:auto; padding:6px 12px; font-size:12px; white-space:nowrap;\">⬇️ 下载</button></a>` +\n" +
                "                            `</div>`;\n" +
                "                });\n" +
                "                box.innerHTML = html;\n" +
                "            } catch(e) {\n" +
                "                box.innerHTML = '<div style=\"text-align: center; color: #ef4444; font-size: 12px; padding: 16px;\">加载失败: ' + e.message + '</div>';\n" +
                "            }\n" +
                "        }\n" +
                "        loadCarFiles();\n" +
                "        async function pushUrl() {\n" +
                "            const url = document.getElementById('push-url').value.trim();\n" +
                "            if (!url) { alert('请输入下载地址'); return; }\n" +
                "            try {\n" +
                "                const res = await fetch('/api/push_url', { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ url: url }) });\n" +
                "                const data = await res.json();\n" +
                "                alert(data.message || '已成功推送到车机后台下载！');\n" +
                "                document.getElementById('push-url').value = '';\n" +
                "            } catch(e) { alert('推送失败: ' + e.message); }\n" +
                "        }\n" +
                "        async function pushAdbCmd() {\n" +
                "            const cmd = document.getElementById('adb-cmd').value.trim();\n" +
                "            if (!cmd) { alert('请输入 ADB 指令'); return; }\n" +
                "            try {\n" +
                "                const res = await fetch('/api/push_cmd', { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ cmd: cmd }) });\n" +
                "                const data = await res.json();\n" +
                "                alert(data.message || '✅ 指令已推送至车机屏幕待核对！');\n" +
                "                document.getElementById('adb-cmd').value = '';\n" +
                "            } catch(e) { alert('推送失败: ' + e.message); }\n" +
                "        }\n" +
                "        async function uploadFile() {\n" +
                "            const fileInput = document.getElementById('file-input');\n" +
                "            if (!fileInput.files.length) { alert('请先选择文件'); return; }\n" +
                "            const file = fileInput.files[0];\n" +
                "            const chunkSize = 2 * 1024 * 1024; // 2MB chunks\n" +
                "            const totalChunks = Math.ceil(file.size / chunkSize);\n" +
                "            const pBar = document.getElementById('progress-bar');\n" +
                "            const pFill = document.getElementById('progress-fill');\n" +
                "            const pStatus = document.getElementById('upload-status');\n" +
                "            pBar.style.display = 'block';\n" +
                "            let resolvedName = file.name;\n" +
                "            for (let i = 0; i < totalChunks; i++) {\n" +
                "                const start = i * chunkSize;\n" +
                "                const end = Math.min(file.size, start + chunkSize);\n" +
                "                const chunk = file.slice(start, end);\n" +
                "                const isLast = (i === totalChunks - 1);\n" +
                "                pStatus.innerText = `正在传输: ${Math.round((i/totalChunks)*100)}% (${(start/1024/1024).toFixed(1)}MB / ${(file.size/1024/1024).toFixed(1)}MB)`;\n" +
                "                const res = await fetch('/api/upload_chunk', {\n" +
                "                    method: 'POST',\n" +
                "                    headers: {\n" +
                "                        'x-file-name': encodeURIComponent(resolvedName),\n" +
                "                        'x-chunk-offset': start.toString(),\n" +
                "                        'x-name-resolved': (i > 0 ? 'true' : 'false'),\n" +
                "                        'x-last-chunk': isLast ? 'true' : 'false'\n" +
                "                    },\n" +
                "                    body: chunk\n" +
                "                });\n" +
                "                try {\n" +
                "                    const chunkJson = await res.json();\n" +
                "                    if (chunkJson && chunkJson.savedName) resolvedName = chunkJson.savedName;\n" +
                "                } catch(e) {}\n" +
                "                pFill.style.width = Math.round(((i + 1) / totalChunks) * 100) + '%';\n" +
                "            }\n" +
                "            pStatus.innerText = '✅ 传输完成！已保存至车机 /sdcard/Download/!车机应用/' + resolvedName;\n" +
                "        }\n" +
                "        async function sendAction(act) {\n" +
                "            try {\n" +
                "                await fetch('/api/action', { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ action: act }) });\n" +
                "                alert('指令已执行！');\n" +
                "            } catch(e) { alert('执行失败: ' + e.message); }\n" +
                "        }\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
