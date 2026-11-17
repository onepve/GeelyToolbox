package app.onepve.geelyconsole.server;

import android.content.Context;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import app.onepve.geelyconsole.utils.SystemUtils;
import app.onepve.geelyconsole.utils.PrefUtils;
import app.onepve.geelyconsole.utils.UploadNoticeOverlay;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
        void onAdbCommandPushed(String command, boolean autoExec);
    }

    private static final int PORT = 8888;
    private final Context context;
    // callback 可被前台宿主（界面/服务）热替换；volatile 保证跨线程可见，
    // 实际调用均经 mainHandler.post 切主线程执行
    private volatile WebServerCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ServerSocket serverSocket;
    private boolean isRunning = false;

    /**
     * v1.8.33.24: 快传服务全局单例化，生命周期随进程常驻。
     * 旧实现绑定 MainActivity.onCreate/onDestroy —— 助手一退后台 8888 端口即关闭，
     * 车主感知为「打开助手界面 WiFi 才能连上」。现改为：
     *   - VehicleAutomationService.onCreate 开机即拉起监听（幂等）；
     *   - MainActivity.onCreate 前台接管回调（幂等，仅更新 callback）；
     *   - 退出界面不再 stop，进程存活期间端口始终监听。
     */
    private static volatile WebServer sSharedInstance;
    private static final Object sSharedLock = new Object();

    public static void startShared(Context context, WebServerCallback callback) {
        if (context == null) return;
        synchronized (sSharedLock) {
            WebServer s = sSharedInstance;
            if (s == null) {
                s = new WebServer(context.getApplicationContext(), callback);
                sSharedInstance = s;
                s.start();
                Log.i(TAG, "WebServer shared instance started (port " + PORT + "), lifecycle = process-wide");
            } else if (callback != null) {
                // 已在监听中，仅把回调换成当前前台的宿主（界面/服务），不重启端口
                s.attachCallback(callback);
            }
        }
    }

    private void attachCallback(WebServerCallback callback) {
        if (callback == null) return;
        this.callback = callback;
    }

    private WebServer(Context context, WebServerCallback callback) {
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
                    String fullPath = reqParts.length > 1 ? reqParts[1] : "/";
                    String path = fullPath;
                    String queryString = "";
                    int qIdx = fullPath.indexOf('?');
                    if (qIdx != -1) {
                        path = fullPath.substring(0, qIdx);
                        queryString = fullPath.substring(qIdx + 1);
                    }

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

                    if ("OPTIONS".equalsIgnoreCase(method)) {
                        handleOptions(out);
                    } else if ("/api/status".equals(path) && "GET".equalsIgnoreCase(method)) {
                        handleApiStatus(out);
                    } else if ("/api/list_downloads".equals(path) && "GET".equalsIgnoreCase(method)) {
                        handleApiListDownloads(out);
                    } else if (path.startsWith("/api/download") && "GET".equalsIgnoreCase(method)) {
                        handleApiDownloadFile(fullPath, out);
                    } else if ("/api/push_url".equals(path) && "POST".equalsIgnoreCase(method)) {
                        handleApiPushUrl(in, contentLength, out);
                    } else if ("/api/upload_chunk".equals(path) && "POST".equalsIgnoreCase(method)) {
                        handleApiUploadChunk(in, contentLength, headers, queryString, out);
                    } else if ("/api/clean_download".equals(path) && "POST".equalsIgnoreCase(method)) {
                        handleApiCleanDownload(in, contentLength, out);
                    } else if ("/api/action".equals(path) && "POST".equalsIgnoreCase(method)) {
                        handleApiAction(in, contentLength, out);
                    } else if ("/api/push_cmd".equals(path) && "POST".equalsIgnoreCase(method)) {
                        handleApiPushCmd(in, contentLength, out);
                    } else if ("/api/get_tts".equals(path)) {
                        handleApiGetTts(out);
                    } else if ("/api/save_tts".equals(path) && "POST".equalsIgnoreCase(method)) {
                        handleApiSaveTts(in, contentLength, out);
                    } else if ("/api/voice_template".equals(path) && "GET".equalsIgnoreCase(method)) {
                        handleApiVoiceTemplate(out);
                    } else if ("/api/voice_readme".equals(path) && "GET".equalsIgnoreCase(method)) {
                        handleApiVoiceReadme(out);
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

        app.onepve.geelyconsole.hal.IVehiclePlatform platform = app.onepve.geelyconsole.hal.VehiclePlatformFactory.getPlatform(context);
        String pCode = platform != null ? platform.getPlatformCode() : "COOL";
        String pName = platform != null ? platform.getPlatformName() : "吉利缤越";

        String json = String.format("{\"code\":\"%s\",\"code_plus5\":\"%s\",\"mem\":\"%s\",\"storage\":\"%s\",\"lock\":%b,\"platform_code\":\"%s\",\"platform_name\":\"%s\",\"status\":\"ok\"}",
                dynamicCode, dynamicCodePlus5, mem.getSummary(), storage.getSummary(), lockActive, pCode, pName);

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
                            obj.put("isDir", false);
                            obj.put("size", f.length());
                            obj.put("sizeFormatted", formatFileSize(f.length()));
                            obj.put("time", f.lastModified());
                            obj.put("timeFormatted", sdf.format(new Date(f.lastModified())));
                            array.put(obj);
                        } catch (Exception ignored) {
                        }
                    } else if (f.isDirectory()) {
                        // 手机端选文件夹传上来之后，这里要把文件夹也列出来，
                        // 车主才知道「文件夹本身」确实传到了车机，而不是只有散落文件。
                        JSONObject obj = new JSONObject();
                        try {
                            int[] acc = new int[]{0};
                            long[] bytes = new long[]{0};
                            countDirTree(f, acc, bytes, 0);
                            obj.put("name", f.getName());
                            obj.put("isDir", true);
                            obj.put("fileCount", acc[0]);
                            obj.put("size", bytes[0]);
                            obj.put("sizeFormatted", formatFileSize(bytes[0]));
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

    /**
     * 把「主题包/图片/a.jpg」这类相对路径解析成 Download 下的安全子目录。
     *
     * 防穿越铁律（手机端传什么都可能）：
     *   · 逐段检查，出现「..」「.」「空段」「含冒号或反斜杠」的段直接整段丢弃；
     *   · 全部段都非法就返回 null，调用方退回 Download 根目录，绝不写到别处；
     *   · 最后用规范路径再确认一次仍在 baseDir 之内。
     */
    private File resolveSafeSubDir(File baseDir, String relPath) {
        if (baseDir == null || relPath == null) return null;
        String normalized = relPath.replace('\\', '/');
        String[] rawSegs = normalized.split("/");
        java.util.List<String> safeSegs = new java.util.ArrayList<>();
        for (String seg : rawSegs) {
            if (seg == null) continue;
            String s = seg.trim();
            if (s.isEmpty() || ".".equals(s) || "..".equals(s)) continue;
            if (s.indexOf(':') >= 0) continue;            // 盘符 / 协议前缀
            if (s.indexOf('\u0000') >= 0) continue;
            safeSegs.add(s);
        }
        // 最后一段是文件名，目录部分只用前面的段
        if (safeSegs.size() <= 1) return null;
        safeSegs.remove(safeSegs.size() - 1);

        File dir = baseDir;
        for (String seg : safeSegs) {
            dir = new File(dir, seg);
        }
        try {
            String basePath = baseDir.getCanonicalPath();
            String dirPath = dir.getCanonicalPath();
            if (!dirPath.equals(basePath) && !dirPath.startsWith(basePath + File.separator)) {
                Log.w(TAG, "拒绝越界目录: " + relPath);
                return null;
            }
        } catch (Exception e) {
            return null;
        }
        return dir;
    }

    /** 统计一个目录树里的文件个数与总字节数（限 6 层，防异常深目录拖慢列表） */
    private void countDirTree(File dir, int[] countAcc, long[] sizeAcc, int depth) {
        if (dir == null || depth > 6) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) {
            if (c.isFile()) {
                countAcc[0]++;
                sizeAcc[0] += c.length();
            } else if (c.isDirectory()) {
                countDirTree(c, countAcc, sizeAcc, depth + 1);
            }
        }
    }

    private void handleOptions(OutputStream out) throws IOException {
        String header = "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type, x-file-name, x-chunk-offset, x-last-chunk, x-name-resolved, x-file-size, x-target-dir, x-rel-path, *\r\n" +
                "Access-Control-Max-Age: 86400\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String k = pair.substring(0, idx).trim();
                String v = pair.substring(idx + 1).trim();
                try {
                    v = URLDecoder.decode(v, "UTF-8");
                } catch (Exception ignored) {}
                map.put(k, v);
            }
        }
        return map;
    }

    private void handleApiUploadChunk(InputStream in, int length, Map<String, String> headers, String queryString, OutputStream out) throws IOException {
        Map<String, String> queryParams = parseQueryParams(queryString);

        String fileName = headers.get("x-file-name");
        if (fileName == null || fileName.isEmpty()) {
            fileName = queryParams.get("fileName");
        }
        if (fileName != null && !fileName.isEmpty()) {
            try {
                fileName = URLDecoder.decode(fileName, "UTF-8");
            } catch (Exception ignored) {}
        } else {
            fileName = "upload_" + System.currentTimeMillis();
        }

        // 仅保留文件名本身，严防路径穿越
        fileName = new File(fileName).getName();

        long chunkOffset = 0;
        String offsetStr = headers.get("x-chunk-offset");
        if (offsetStr == null || offsetStr.isEmpty()) {
            offsetStr = queryParams.get("chunkOffset");
        }
        if (offsetStr != null) {
            try {
                chunkOffset = Long.parseLong(offsetStr);
            } catch (Exception ignored) {}
        }

        String lastChunkStr = headers.get("x-last-chunk");
        if (lastChunkStr == null || lastChunkStr.isEmpty()) {
            lastChunkStr = queryParams.get("lastChunk");
        }
        boolean isLastChunk = "true".equalsIgnoreCase(lastChunkStr);

        String nameResolvedStr = headers.get("x-name-resolved");
        if (nameResolvedStr == null || nameResolvedStr.isEmpty()) {
            nameResolvedStr = queryParams.get("nameResolved");
        }
        boolean isNameResolved = "true".equalsIgnoreCase(nameResolvedStr);

        File downloadDir = SystemUtils.getAppDownloadDir();
        String targetSubDir = headers.get("x-target-dir");
        if (targetSubDir == null || targetSubDir.isEmpty()) {
            targetSubDir = queryParams.get("targetDir");
        }
        if ("custom_voice".equalsIgnoreCase(targetSubDir)) {
            downloadDir = new File(downloadDir, "车载语音自定义");
        }

        // 选文件夹上传：前端会带上 webkitRelativePath（形如「主题包/图片/a.jpg」）。
        // 逐段清洗后把子目录建出来，实现「连文件夹本身一起传、保持原目录格式」。
        // 安全铁律：任一段出现 .. / 空段 / 绝对路径前缀一律丢弃整段，绝不越出 Download。
        String relPath = headers.get("x-rel-path");
        if (relPath == null || relPath.isEmpty()) {
            relPath = queryParams.get("relPath");
        }
        if (relPath != null && !relPath.isEmpty()) {
            try {
                relPath = URLDecoder.decode(relPath, "UTF-8");
            } catch (Exception ignored) {}
            File relDir = resolveSafeSubDir(downloadDir, relPath);
            if (relDir != null) {
                fileName = new File(fileName).getName();
                downloadDir = relDir;
            }
        }

        if (!downloadDir.exists()) downloadDir.mkdirs();

        // 仅在分片 offset == 0 且未经过重命名处理时检测同名冲突并自动重命名
        if (chunkOffset == 0 && !isNameResolved) {
            fileName = getUniqueFileName(downloadDir, fileName);
        }

        final File targetFile = new File(downloadDir, fileName);

        RandomAccessFile raf = new RandomAccessFile(targetFile, "rw");
        raf.seek(chunkOffset);

        byte[] buf = new byte[32 * 1024];
        int remaining = length;
        int totalWritten = 0;
        while (remaining > 0) {
            int toRead = Math.min(buf.length, remaining);
            int read = in.read(buf, 0, toRead);
            if (read == -1) break;
            raf.write(buf, 0, read);
            remaining -= read;
            totalWritten += read;
        }
        raf.close();

        if (totalWritten < length) {
            String errResp = "{\"success\":false,\"error\":\"incomplete_data\",\"written\":" + totalWritten + ",\"expected\":" + length + "}";
            sendJsonResponse(out, errResp);
            return;
        }

        if (isLastChunk) {
            // 车机端提示：窗口级悬浮横幅，助手界面在前台/后台都能弹（多选连传按顺序累计）
            try {
                UploadNoticeOverlay.notifyUploaded(context, targetFile);
            } catch (Throwable t) {
                Log.w(TAG, "快传提示横幅调用失败: " + t.getMessage());
            }
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onFileUploaded(targetFile);
                    }
                }
            });
        }

        // 响应体用 JSONObject 生成：文件名/文件夹名可能带引号、反斜杠，手拼 JSON 会直接坏掉
        String resp;
        try {
            JSONObject jo = new JSONObject();
            jo.put("success", true);
            jo.put("offset", chunkOffset + length);
            jo.put("done", isLastChunk);
            jo.put("savedName", fileName);
            resp = jo.toString();
        } catch (Exception e) {
            resp = "{\"success\":true,\"offset\":" + (chunkOffset + length) + ",\"done\":" + isLastChunk + "}";
        }
        sendJsonResponse(out, resp);
    }

    private void handleApiGetTts(OutputStream out) throws IOException {
        android.content.SharedPreferences prefs = PrefUtils.getAppPreferences(context);
        JSONObject root = new JSONObject();
        String[] keys = {
            "door_open", "door_close",
            "door_fl", "door_fl_close", "door_fr", "door_fr_close",
            "door_rl", "door_rl_close", "door_rr", "door_rr_close",
            "trunk_open", "trunk_close",
            "gear_d", "gear_r", "gear_p", "gear_n",
            "mode_smart", "mode_comfort", "mode_eco", "mode_sport"
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
            android.content.SharedPreferences prefs = PrefUtils.getAppPreferences(context);
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

    private void handleApiVoiceTemplate(OutputStream out) throws IOException {
        try {
            InputStream in = context.getAssets().open("voice_template.zip");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            in.close();
            byte[] bytes = baos.toByteArray();
            String header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/zip\r\n" +
                    "Content-Disposition: attachment; filename=\"voice_template.zip\"\r\n" +
                    "Content-Length: " + bytes.length + "\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
        } catch (Exception e) {
            sendJsonResponse(out, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handleApiVoiceReadme(OutputStream out) throws IOException {
        try {
            InputStream in = context.getAssets().open("voice_readme.txt");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            in.close();
            byte[] bytes = baos.toByteArray();
            String header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain; charset=UTF-8\r\n" +
                    "Content-Length: " + bytes.length + "\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n";
            out.write(header.getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
        } catch (Exception e) {
            sendJsonResponse(out, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handleApiCleanDownload(InputStream in, int length, OutputStream out) throws IOException {
        byte[] body = readExactBytes(in, length);
        String bodyStr = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        int mode = 2; // 默认智能安全清理
        try {
            String modeStr = extractJsonValue(bodyStr, "mode");
            if (modeStr != null && !modeStr.isEmpty()) {
                mode = Integer.parseInt(modeStr.trim());
            }
        } catch (Exception ignored) {}

        final int finalMode = mode;
        final SystemUtils.CleanDownloadResult res = SystemUtils.cleanDownloadDirectory(finalMode);
        final double freedMb = (double) res.freedBytes / (1024 * 1024);
        final String freedMbStr = String.format(Locale.CHINA, "%.1f", freedMb);

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (context != null) {
                    Toast.makeText(context, String.format(Locale.CHINA, "手机端远程清理完成！共清除 %d 项，释放 %s MB 存储空间", res.deletedFiles, freedMbStr), Toast.LENGTH_LONG).show();
                }
            }
        });

        String resp = String.format(Locale.CHINA, "{\"success\":true,\"deleted\":%d,\"freed_mb\":\"%s\"}", res.deletedFiles, freedMbStr);
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
        boolean autoExec = false;
        if (bodyStr.startsWith("{")) {
            cmd = extractJsonValue(bodyStr, "cmd");
            String autoStr = extractJsonValue(bodyStr, "auto_exec");
            if ("true".equalsIgnoreCase(autoStr)) {
                autoExec = true;
            }
        } else {
            String[] pairs = bodyStr.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    String k = URLDecoder.decode(kv[0], "UTF-8");
                    String v = URLDecoder.decode(kv[1], "UTF-8");
                    if ("cmd".equals(k)) cmd = v;
                    if ("auto_exec".equals(k) && "true".equalsIgnoreCase(v)) autoExec = true;
                }
            }
        }

        final String finalCmd = cmd.trim();
        final boolean finalAutoExec = autoExec;
        if (!finalCmd.isEmpty()) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (callback != null) {
                        callback.onAdbCommandPushed(finalCmd, finalAutoExec);
                    }
                }
            });
        }

        String msg = finalAutoExec ? "ADB 指令已推送至车机并正在自动执行" : "ADB 指令已推送至车机屏幕，请在车机大屏核对后点击执行";
        String resp = "{\"success\":true,\"message\":\"" + msg + "\"}";
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
            String html = out.toString(StandardCharsets.UTF_8.name());
            // 注入车型平台元数据与 OTA 捕获直链
            app.onepve.geelyconsole.hal.IVehiclePlatform platform = app.onepve.geelyconsole.hal.VehiclePlatformFactory.getPlatform(context);
            String pCode = platform != null ? platform.getPlatformCode() : "COOL";
            String pName = platform != null ? platform.getPlatformName() : "吉利缤越";

            String otaUrl = SystemUtils.sOtaCaptureUrl != null ? SystemUtils.sOtaCaptureUrl : "";
            boolean hasOta = !otaUrl.isEmpty();
            html = html.replace("__PLATFORM_CODE__", pCode)
                       .replace("__PLATFORM_NAME__", pName)
                       .replace("__OTA_CAPTURE_URL__", otaUrl)
                       .replace("__OTA_HAS_URL__", hasOta ? "1" : "0");
            return html;
        } catch (Exception e) {
            Log.w(TAG, "Failed to load mobile_web.html from assets: " + e.getMessage());
            return "<!DOCTYPE html><html><body><h3>吉利车机无线快传</h3><p>资源加载异常，请重启应用重试。</p></body></html>";
        }
    }
}
