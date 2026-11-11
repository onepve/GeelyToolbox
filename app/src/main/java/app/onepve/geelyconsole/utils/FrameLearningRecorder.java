package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HAL / CAN 报文实时学习观察器（FrameLearningRecorder）
 *
 * 用途：车主点「报文学习」后进入观察界面，实时看到每一路信号当前数值，
 *       边操作车门/引擎盖/尾门/挂挡，边在屏幕上一眼确认「哪个字节在动、变成什么」。
 *
 * 与方控学习（WheelKeyLearningModal）的区别：
 *   方控学习 = 抓「按键事件」一次性结论（键码）
 *   报文学习 = 抓「状态帧」的持续数值流，需要实时刷新，且不能有倒计时压力
 *
 * 三条铁律：
 *   1. 只读不写：本类绝不向串口/HAL 发送任何指令，纯旁路监听。
 *   2. 学习期间不干扰业务：不改变任何状态机，也不阻止车门/尾门正常播报。
 *   3. 只快照「变化帧」：3 秒一帧的报文若全部推给前端，WebView 会被 JS 调用打爆；
 *      因此只在某个字节真变了的时候才推，同时限制总条数，保证长时间开着也不卡。
 *
 * 数据来源：VehicleAutomationService 解析 91 02 01 时调用 onSerialFrame()，
 *          报文学习界面打开时才生效（setActive(true)），关闭即停，零常驻开销。
 */
public final class FrameLearningRecorder {
    private static final String TAG = "FrameLearning";

    /** 单次学习最多保留多少条变化帧（防止长时间开着把内存吃满） */
    private static final int MAX_FRAMES = 400;
    /** 前端刷新节流：最快 250ms 推一次，避免一串变化同时到达时打爆 WebView */
    private static final long PUSH_THROTTLE_MS = 250;

    private static final AtomicBoolean active = new AtomicBoolean(false);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 已观察到的信号通道 → 最新值（前端用这个做「实时数值面板」） */
    private static final Map<String, JSONObject> latestSignals = new LinkedHashMap<>();
    /** 完整变化帧流水（带时间戳），供前端下拉查看历史 */
    private static volatile JSONArray frames = new JSONArray();

    private static volatile long lastPushAtMs = 0L;
    private static volatile boolean pushScheduled = false;
    private static volatile long frameSeq = 0L;
    private static volatile String lastRawFrame = "";

    private FrameLearningRecorder() {
    }

    /** 学习界面打开 / 关闭。关闭后所有 onXxx 立刻变成空转，不留常驻开销。 */
    public static void setActive(boolean on) {
        boolean was = active.getAndSet(on);
        if (on && !was) {
            clear();
            AppLogger.diag("报文学习", "开始观察（无限时，操作车门/引擎盖/挡杆即可看到数值变化）");
        } else if (!on && was) {
            AppLogger.diag("报文学习", "结束观察，共记录 " + frames.length() + " 条变化帧");
        }
    }

    public static boolean isActive() {
        return active.get();
    }

    public static void clear() {
        synchronized (latestSignals) {
            latestSignals.clear();
        }
        synchronized (frames) {
            frames.clear();
        }
        frameSeq = 0L;
        lastRawFrame = "";
        lastPushAtMs = 0L;
    }

    /**
     * 车身串口帧入口：由 VehicleAutomationService 在解析 91 02 01 后调用。
     *
     * @param rawFrame 原始帧文本（如 "91 02 01 00 04 00 51 f5"），用于给车主看原貌
     * @param b6       四门位图
     * @param b7       尾门 + 引擎盖位图
     */
    public static void onSerialFrame(String rawFrame, int b6, int b7) {
        if (!active.get()) return;

        // 帧级去重：3 秒一帧，绝大部分帧完全一样，没必要都记
        String sig = String.format("%02x|%02x", b6, b7);
        boolean changed = !sig.equals(lastRawFrame);
        lastRawFrame = sig;

        // 无论变没变，都刷新「实时数值面板」——车主需要看到它在周期性跳动
        putSignal("四门位图 b6", String.format("0x%02x", b6), describeDoors(b6));
        putSignal("尾门+引擎盖 b7", String.format("0x%02x", b7), describeB7(b7));

        if (!changed) {
            schedulePush();
            return;
        }

        frameSeq++;
        JSONObject f = new JSONObject();
        try {
            f.put("seq", frameSeq);
            f.put("t", System.currentTimeMillis());
            f.put("raw", rawFrame == null ? "" : rawFrame);
            f.put("b6", String.format("0x%02x", b6));
            f.put("b7", String.format("0x%02x", b7));
            f.put("doors", describeDoors(b6));
            f.put("hood", ((b7 >> 2) & 0x01) == 1 ? "闭合" : "开启");
            f.put("trunk", (b7 & 0x01) == 1 ? "闭合" : "开启");
        } catch (Exception ignored) {
        }

        synchronized (frames) {
            frames.put(f);
            // 超上限时丢弃最老的，保持滑动窗口
            while (frames.length() > MAX_FRAMES) {
                JSONArray trimmed = new JSONArray();
                for (int i = 1; i < frames.length(); i++) {
                    trimmed.put(frames.opt(i));
                }
                frames = trimmed;
            }
        }
        AppLogger.diag("报文学习", "变化帧 #" + frameSeq + " b6=" + String.format("%02x", b6)
                + " b7=" + String.format("%02x", b7) + " → " + describeDoors(b6)
                + " 引擎盖" + (((b7 >> 2) & 0x01) == 1 ? "闭合" : "开启"));
        schedulePush();
    }

    /** 其他通道（HAL 属性、挡位、车速）也可以上报，前端统一展示 */
    public static void onSignal(String name, String value, String meaning) {
        if (!active.get()) return;
        putSignal(name, value, meaning);
        schedulePush();
    }

    private static void putSignal(String name, String value, String meaning) {
        JSONObject o = new JSONObject();
        try {
            o.put("name", name);
            o.put("value", value);
            o.put("meaning", meaning);
            o.put("t", System.currentTimeMillis());
        } catch (Exception ignored) {
        }
        synchronized (latestSignals) {
            latestSignals.put(name, o);
        }
    }

    private static String describeDoors(int b6) {
        String[] names = {"主驾", "副驾", "左后", "右后"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            int v = (b6 >> (i * 2)) & 0x03;
            if (sb.length() > 0) sb.append(" ");
            sb.append(names[i]).append(v == 0 ? "开" : "关");
        }
        return sb.toString();
    }

    private static String describeB7(int b7) {
        return "引擎盖" + (((b7 >> 2) & 0x01) == 1 ? "闭合" : "开启")
                + " 尾门" + ((b7 & 0x01) == 1 ? "闭合" : "开启");
    }

    /** 节流推送到 WebView：变化密集时合并成一次，避免 JS 调用风暴 */
    private static void schedulePush() {
        long now = System.currentTimeMillis();
        if (now - lastPushAtMs < PUSH_THROTTLE_MS) {
            if (pushScheduled) return;
            pushScheduled = true;
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    pushScheduled = false;
                    pushNow();
                }
            }, PUSH_THROTTLE_MS - (now - lastPushAtMs));
            return;
        }
        pushNow();
    }

    private static void pushNow() {
        lastPushAtMs = System.currentTimeMillis();
        JSONObject payload = snapshot();
        String js = "window.onFrameLearningUpdate && window.onFrameLearningUpdate(" + payload + ");";
        // 由 MainActivity 注入的桥接器实际执行；这里通过静态回调间接发送
        WebBridge.emit(js);
    }

    /** 一次性全量快照 */
    public static JSONObject snapshot() {
        JSONObject out = new JSONObject();
        try {
            JSONArray sigArr = new JSONArray();
            synchronized (latestSignals) {
                for (JSONObject o : latestSignals.values()) {
                    sigArr.put(o);
                }
            }
            out.put("signals", sigArr);
            JSONArray fArr;
            synchronized (frames) {
                fArr = new JSONArray(frames.toString());
            }
            out.put("frames", fArr);
            out.put("frame_count", frameSeq);
            out.put("active", active.get());
        } catch (Exception ignored) {
        }
        return out;
    }

    /**
     * WebView 桥：MainActivity 在 onCreate 里注册一次，之后本类就能把数据推到页面。
     * 用接口而不是直接依赖 MainActivity，保证本类可以在纯 JVM 环境单测。
     */
    public interface Emitter {
        void emitJs(String js);
    }

    private static volatile Emitter emitter;

    public static void installEmitter(Emitter e) {
        emitter = e;
    }

    /** 内部转发（避免上面 pushNow 直接引用不存在的类） */
    private static final class WebBridge {
        static void emit(String js) {
            Emitter e = emitter;
            if (e == null) return;
            try {
                e.emitJs(js);
            } catch (Throwable ignored) {
            }
        }
    }
}
