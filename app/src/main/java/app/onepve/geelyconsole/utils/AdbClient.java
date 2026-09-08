package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;

import javax.crypto.Cipher;

/**
 * Full-featured pure-Java ADB TCP Client with RSA-2048 authentication
 * Connects to localhost adbd daemon (127.0.0.1:5555) with UID 2000 shell privileges.
 */
public class AdbClient {
    private static final String TAG = "AdbClient";
    private static final String ADB_HOST = "127.0.0.1";
    private static final int ADB_PORT = 5555;
    private static final int TIMEOUT_MS = 5000;

    // ADB Protocol Command Constants
    private static final int A_CNXN = 0x4e584e43; // "CNXN"
    private static final int A_AUTH = 0x48545541; // "AUTH"
    private static final int A_OPEN = 0x4e45504f; // "OPEN"
    private static final int A_OKAY = 0x59414b4f; // "OKAY"
    private static final int A_CLSE = 0x45534c43; // "CLSE"
    private static final int A_WRTE = 0x45545257; // "WRTE"

    private static final int ADB_AUTH_TOKEN = 1;
    private static final int ADB_AUTH_SIGNATURE = 2;
    private static final int ADB_AUTH_RSAPUBLICKEY = 3;

    private static final int A_VERSION = 0x01000000;
    private static final int MAX_PAYLOAD = 4096;

    // ASN.1 SHA-1 DigestInfo header (15 bytes)
    private static final byte[] SHA1_DIGEST_INFO = {
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
    };

    public static class AdbResult {
        public final boolean success;
        public final String output;
        public final String error;

        public AdbResult(boolean success, String output, String error) {
            this.success = success;
            this.output = output != null ? output : "";
            this.error = error != null ? error : "";
        }
    }

    private static class AdbMessage {
        public int command;
        public int arg0;
        public int arg1;
        public int dataLength;
        public int dataCheck;
        public int magic;
        public byte[] data;
    }

    public static boolean isAdbPortOpen() {
        return isAdbPortOpen(null);
    }

    public static boolean isAdbPortOpen(Context context) {
        java.util.List<String> hosts = new java.util.ArrayList<>();
        hosts.add("127.0.0.1");
        hosts.add("localhost");
        String carIp = SystemUtils.getCarIpAddress();
        if (carIp != null && !carIp.trim().isEmpty() && !hosts.contains(carIp.trim())) {
            hosts.add(carIp.trim());
        }

        for (String host : hosts) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, ADB_PORT), 1000);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public static JSONObject probeAdbStatus(Context context) {
        JSONObject res = new JSONObject();
        try {
            boolean portOpen = isAdbPortOpen(context);
            AdbResult adbRes = execute(context, "id");
            if (adbRes != null && adbRes.success && (adbRes.output.contains("uid=") || adbRes.output.contains("shell") || adbRes.output.contains("root"))) {
                res.put("ready", true);
                res.put("status", "ready");
                res.put("title", "ADB 已就绪");
                res.put("details", "已直连车机 127.0.0.1:5555 (" + adbRes.output.trim() + ")");
                res.put("privilege", adbRes.output.contains("uid=0") ? "ROOT 特权" : "Shell 2000 特权");
                return res;
            }

            // 本地 Runtime Shell 兼容
            String shRes = SystemUtils.executeShell("id");
            if (shRes != null && (shRes.contains("uid=") || shRes.contains("shell") || shRes.contains("app_"))) {
                res.put("ready", true);
                res.put("status", "local_shell");
                res.put("title", "ADB/Shell 已就绪");
                res.put("details", "Runtime 本地环境就绪 (" + shRes.trim() + ")");
                res.put("privilege", "本地执行特权");
                return res;
            }

            res.put("ready", portOpen);
            res.put("status", portOpen ? "port_open" : "offline");
            res.put("title", portOpen ? "ADB 端口已开放" : "ADB 未就绪");
            res.put("details", portOpen ? "5555 端口响应中" : "端口未开放或未授权");
            res.put("privilege", "受限模式");
        } catch (Exception e) {
            try {
                res.put("ready", false);
                res.put("title", "ADB 探测异常");
                res.put("details", e.getMessage());
            } catch (Exception ignored) {}
        }
        return res;
    }

    public static AdbResult execute(Context context, String command) {
        Socket socket = null;
        String connectedHost = "127.0.0.1";
        try {
            java.util.List<String> hosts = new java.util.ArrayList<>();
            hosts.add("127.0.0.1");
            hosts.add("localhost");
            String carIp = SystemUtils.getCarIpAddress();
            if (carIp != null && !carIp.trim().isEmpty() && !hosts.contains(carIp.trim())) {
                hosts.add(carIp.trim());
            }

            Exception lastConnectEx = null;
            for (String host : hosts) {
                try {
                    socket = new Socket();
                    socket.setTcpNoDelay(true);
                    socket.setSoTimeout(TIMEOUT_MS);
                    socket.connect(new InetSocketAddress(host, ADB_PORT), 1500);
                    connectedHost = host;
                    break;
                } catch (Exception e) {
                    lastConnectEx = e;
                    socket = null;
                }
            }

            if (socket == null) {
                return new AdbResult(false, "", "ADB 端口未开放 (5555 连接失败: " + (lastConnectEx != null ? lastConnectEx.getMessage() : "超时") + ")");
            }

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // 1. Send CNXN Handshake
            byte[] cnxnPayload = "host::\0".getBytes(StandardCharsets.UTF_8);
            sendPacket(out, A_CNXN, A_VERSION, MAX_PAYLOAD, cnxnPayload);

            // 2. Read Response (CNXN or AUTH)
            AdbMessage resp = readMessage(in);
            if (resp == null) {
                return new AdbResult(false, "", "车机 ADB 守护进程无响应");
            }

            // Handle AUTH Challenge
            if (resp.command == A_AUTH && resp.arg0 == ADB_AUTH_TOKEN) {
                KeyPair keyPair = getOrCreateAdbKeyPair(context);
                if (keyPair == null) {
                    return new AdbResult(false, "", "无法生成 ADB RSA 密钥对");
                }

                // 2a. Sign token with RSA private key
                byte[] token = resp.data;
                byte[] signature = signToken(keyPair.getPrivate(), token);
                if (signature != null) {
                    sendPacket(out, A_AUTH, ADB_AUTH_SIGNATURE, 0, signature);
                    resp = readMessage(in);
                }

                // 2b. If adbd still requires public key registration
                if (resp != null && resp.command == A_AUTH && resp.arg0 == ADB_AUTH_TOKEN) {
                    byte[] pubKeyPayload = formatAdbPublicKey((RSAPublicKey) keyPair.getPublic());
                    sendPacket(out, A_AUTH, ADB_AUTH_RSAPUBLICKEY, 0, pubKeyPayload);
                    resp = readMessage(in);
                }
            }

            if (resp == null || resp.command != A_CNXN) {
                String cmdHex = (resp != null) ? "0x" + Integer.toHexString(resp.command) : "NULL";
                return new AdbResult(false, "", "ADB 鉴权未通过 (" + cmdHex + ")");
            }

            // 3. Send OPEN command for shell
            int localId = 1;
            String shellCmd = "shell:" + command + "\0";
            byte[] openPayload = shellCmd.getBytes(StandardCharsets.UTF_8);
            sendPacket(out, A_OPEN, localId, 0, openPayload);

            // 4. Read Response Loop
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            int remoteId = 0;

            while (true) {
                AdbMessage msg = readMessage(in);
                if (msg == null) break;

                if (msg.command == A_OKAY) {
                    remoteId = msg.arg0;
                } else if (msg.command == A_WRTE) {
                    if (msg.data != null && msg.data.length > 0) {
                        outputStream.write(msg.data);
                    }
                    sendPacket(out, A_OKAY, localId, remoteId, new byte[0]);
                } else if (msg.command == A_CLSE) {
                    sendPacket(out, A_CLSE, localId, remoteId, new byte[0]);
                    break;
                }
            }

            String output = outputStream.toString("UTF-8").trim();
            return new AdbResult(true, output, "");

        } catch (Exception e) {
            Log.e(TAG, "ADB execution error: " + e.getMessage(), e);
            return new AdbResult(false, "", e.getMessage());
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void sendPacket(OutputStream out, int cmd, int arg0, int arg1, byte[] data) throws Exception {
        int length = (data != null) ? data.length : 0;
        int check = 0;
        if (data != null) {
            for (byte b : data) {
                check += (b & 0xFF);
            }
        }
        int magic = cmd ^ 0xFFFFFFFF;

        ByteBuffer header = ByteBuffer.allocate(24);
        header.order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(cmd);
        header.putInt(arg0);
        header.putInt(arg1);
        header.putInt(length);
        header.putInt(check);
        header.putInt(magic);

        out.write(header.array());
        if (data != null && data.length > 0) {
            out.write(data);
        }
        out.flush();
    }

    private static AdbMessage readMessage(InputStream in) throws Exception {
        byte[] headerBuf = readExact(in, 24);
        if (headerBuf == null) return null;

        ByteBuffer header = ByteBuffer.wrap(headerBuf);
        header.order(ByteOrder.LITTLE_ENDIAN);

        AdbMessage msg = new AdbMessage();
        msg.command = header.getInt();
        msg.arg0 = header.getInt();
        msg.arg1 = header.getInt();
        msg.dataLength = header.getInt();
        msg.dataCheck = header.getInt();
        msg.magic = header.getInt();

        if (msg.dataLength > 0 && msg.dataLength <= (64 * 1024)) {
            msg.data = readExact(in, msg.dataLength);
        } else {
            msg.data = new byte[0];
        }
        return msg;
    }

    private static byte[] readExact(InputStream in, int length) throws Exception {
        byte[] buffer = new byte[length];
        int totalRead = 0;
        while (totalRead < length) {
            int read = in.read(buffer, totalRead, length - totalRead);
            if (read == -1) {
                if (totalRead == 0) return null;
                throw new Exception("Unexpected EOF while reading " + length + " bytes (got " + totalRead + ")");
            }
            totalRead += read;
        }
        return buffer;
    }

    // ==================== ADB Key & Crypto Helpers ====================

    private static KeyPair getOrCreateAdbKeyPair(Context context) {
        try {
            File keyFile = new File(context.getFilesDir(), "adbkey");
            if (keyFile.exists() && keyFile.length() > 0) {
                byte[] encKey = new byte[(int) keyFile.length()];
                try (FileInputStream fis = new FileInputStream(keyFile)) {
                    fis.read(encKey);
                }
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(encKey);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                PrivateKey privKey = kf.generatePrivate(spec);
                if (privKey instanceof RSAPrivateCrtKey) {
                    RSAPrivateCrtKey crt = (RSAPrivateCrtKey) privKey;
                    RSAPublicKeySpec pubSpec = new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent());
                    RSAPublicKey pubKey = (RSAPublicKey) kf.generatePublic(pubSpec);
                    return new KeyPair(pubKey, privKey);
                }
            }

            // Generate new RSA 2048 key
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();

            try (FileOutputStream fos = new FileOutputStream(keyFile)) {
                fos.write(kp.getPrivate().getEncoded());
            }
            return kp;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load/generate ADB key: " + e.getMessage(), e);
            return null;
        }
    }

    private static byte[] signToken(PrivateKey privateKey, byte[] token) {
        try {
            byte[] digestInfo = new byte[SHA1_DIGEST_INFO.length + token.length];
            System.arraycopy(SHA1_DIGEST_INFO, 0, digestInfo, 0, SHA1_DIGEST_INFO.length);
            System.arraycopy(token, 0, digestInfo, SHA1_DIGEST_INFO.length, token.length);

            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, privateKey);
            return cipher.doFinal(digestInfo);
        } catch (Exception e) {
            Log.e(TAG, "Failed to sign token: " + e.getMessage(), e);
            return null;
        }
    }

    private static byte[] formatAdbPublicKey(RSAPublicKey publicKey) {
        try {
            BigInteger n = publicKey.getModulus();
            BigInteger e = publicKey.getPublicExponent();

            int wordCount = 64; // 2048 / 32 = 64 words
            BigInteger r32 = BigInteger.ONE.shiftLeft(32);

            BigInteger n0 = n.remainder(r32);
            BigInteger n0inv = r32.subtract(n0.modInverse(r32)).remainder(r32);

            int[] nWords = new int[wordCount];
            BigInteger tempN = n;
            for (int i = 0; i < wordCount; i++) {
                nWords[i] = tempN.remainder(r32).intValue();
                tempN = tempN.shiftRight(32);
            }

            BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n);
            int[] rrWords = new int[wordCount];
            BigInteger tempRr = rr;
            for (int i = 0; i < wordCount; i++) {
                rrWords[i] = tempRr.remainder(r32).intValue();
                tempRr = tempRr.shiftRight(32);
            }

            ByteBuffer buf = ByteBuffer.allocate(4 + 4 + wordCount * 4 + wordCount * 4 + 4);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.putInt(wordCount);
            buf.putInt(n0inv.intValue());
            for (int w : nWords) buf.putInt(w);
            for (int w : rrWords) buf.putInt(w);
            buf.putInt(e.intValue());

            String b64 = Base64.encodeToString(buf.array(), Base64.NO_WRAP);
            String fullKeyStr = b64 + " onepve@geely\0";
            return fullKeyStr.getBytes(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to format ADB pubkey: " + ex.getMessage(), ex);
            return new byte[0];
        }
    }
}
