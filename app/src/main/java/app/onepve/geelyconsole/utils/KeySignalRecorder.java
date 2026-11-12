package app.onepve.geelyconsole.utils;

import java.util.LinkedList;
import java.util.Map;

/**
 * 关键车身信号变化记录器 (KeySignalRecorder) —— 纯内存，零磁盘写入
 *
 * 【为什么需要它】
 * 2026-11-11 实测取证（诊断包 395816）：车机 logd 的 main 环形缓冲区只有约 256KB
 * （main 段实测 236KB / 3.0 秒 ≈ 79KB/s、723 行/秒），蓝牙电话本 PBAP 同步时更猛，
 * 所以缓冲区实际只装得下最后 3.0 秒的日志。
 * 而车门/尾门报文 91 02 01 固定 3 秒一帧 —— 正好卡在缓冲区边缘：
 * 车主点「抓日志」的瞬间那一帧通常已被后来的日志挤掉，
 * 于是诊断包里 doorLines = 0、doorStats 全 0，看起来像「根本没采集」。
 *
 * 【本类怎么解决】
 * 1. 只记录「状态发生变化」的时刻（同值 1.2 秒内去重），不记每帧心跳，
 *    几百条就能覆盖几十次开关门，内存占用固定；
 * 2. 额外保留原始车门报文抽样（30 秒抽 1 帧、最近 200 帧 ≈ 100 分钟），
 *    用来证明「通道这一百分钟一直在收帧」，不靠推断；
 * 3. 全部存在内存 LinkedList，**一次写盘操作都没有**，绝不磨损车机闪存；
 * 4. 只有车主点【抓日志】时才 exportAll() 生成文本随 ZIP 一次性写出。
 *
 * 【内存账】8 通道 × 260 条 ≈ 2000 条 × 80 字 ≈ 300KB，原始报文抽样 ≈ 40KB。
 * 内存不做抠搜，优先保证「功能所需的信息一条都不丢」。
 *
 * 【与 AppLogger 的区别】AppLogger 受 8 大领域开关管控且默认全关；
 * 本类是穿刺取证通道，始终记录，不占用户可见日志窗口。
 */
public class KeySignalRecorder {

    /** 每类信号最多保留的变化条数（只存变化，不存心跳） */
    private static final int MAX_PER_CHANNEL = 260;

    /** 原始车门报文抽样：保留最近多少帧 */
    private static final int MAX_RAW_FRAMES = 200;

    /** 原始报文抽样间隔：30 秒存 1 帧。200 帧 ≈ 100 分钟覆盖 */
    private static final long RAW_FRAME_SAMPLE_MS = 30000L;

    private static final Map<String, LinkedList<String>> CHANNELS = new java.util.HashMap<>();
    private static final Map<String, String> LAST_VALUE = new java.util.HashMap<>();
    private static final Map<String, Long> LAST_AT = new java.util.HashMap<>();


    private static final java.text.SimpleDateFormat FMT =
            new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.CHINA);

    // ── 通道名（固定几个，方便导出后肉眼扫）──
    public static final String CH_DOOR = "车门";
    public static final String CH_TRUNK = "尾门";
    public static final String CH_GEAR = "挡位";
    public static final String CH_FUEL = "油量";
    public static final String CH_SPEED = "车速";
    public static final String CH_POWER = "电源";
    public static final String CH_WHEEL = "方控";
    public static final String CH_CHANNEL = "通道";
    /** 引擎盖（v1.8.32.12 新增）：91 02 01 的 b7 字节 bit2，1=闭合 0=开启 */
    public static final String CH_HOOD = "引擎盖";

    // ── 车门报文收帧统计（判断「管道到底通不通」的唯一硬指标）──
    private static volatile long firstDoorFrameAtMs = 0L;
    private static volatile long lastDoorFrameAtMs = 0L;
    private static volatile long lastDoorFrameCount = 0L;
    private static volatile String lastDoorFrameRaw = "";

    // ── 原始报文抽样环形队列 ──
    private static final LinkedList<String> RAW_FRAMES = new LinkedList<>();
    private static volatile long lastRawSampleAtMs = 0L;

    // ── 通道健康统计（供 CH_CHANNEL 心跳与 summary.json 使用）──
    private static volatile long linesRead = 0L;      // 从管道读到的总行数
    private static volatile long linesMatched = 0L;   // 命中白名单的行数
    private static volatile long linesDropped = 0L;   // 队列满被主动丢弃的行数
    private static volatile long chunkGaps = 0L;      // 检测到的行间空档次数（>500ms）

    private static String nowStr(long ms) {
        synchronized (FMT) {
            return FMT.format(new java.util.Date(ms));
        }
    }

    /**
     * 记录一次状态变化。**只有状态真的变了才落盘**，同值心跳一律丢弃。
     *
     * 为什么不是「同值 1.2 秒内去重」：车门/尾门报文固定 3 秒一帧，1.2 秒窗口对它们等于没有，
     * 430884 包实测有 112 条一模一样的「主驾=关 副驾=关 左后=关 右后=关」、112 条「尾门=关」、
     * 106 条「挡位=D挡」，真正的开关门变化反而被这些心跳挤到 260 条上限之外被丢掉。
     * 帧还活着的证据由 markDoorFrame 的原始报文抽样与统计行负责，不靠这里重复记同值。
     *
     * @param channel 通道名（用本类常量）
     * @param value   当前状态的可读描述，如 "主驾=开 副驾=关 左后=关 右后=关"
     */
    public static void record(String channel, String value) {
        if (channel == null || value == null) return;
        long now = System.currentTimeMillis();
        synchronized (CHANNELS) {
            String last = LAST_VALUE.get(channel);
            if (last != null && last.equals(value)) {
                LAST_AT.put(channel, now);
                return;   // 状态没变，不占缓冲
            }
            LAST_VALUE.put(channel, value);
            LAST_AT.put(channel, now);

            LinkedList<String> buf = CHANNELS.get(channel);
            if (buf == null) {
                buf = new LinkedList<>();
                CHANNELS.put(channel, buf);
            }
            if (buf.size() >= MAX_PER_CHANNEL) {
                buf.removeFirst();
            }
            buf.addLast("[" + nowStr(now) + "] [" + channel + "] " + value);
        }
    }

    /**
     * 车门/尾门报文的**每一帧**都打点（判断通道是否真的活着），
     * 并按 30 秒抽 1 帧把原始报文存进环形队列（最近 200 帧 ≈ 100 分钟）。
     * 这是「通道一直在收帧」的自证材料，不靠推断。
     * 车门报文固定 3 秒一帧，超过 15 秒没帧就说明主通道已读不到车况。
     */
    public static void markDoorFrame(String rawLine) {
        long now = System.currentTimeMillis();
        lastDoorFrameAtMs = now;
        lastDoorFrameCount++;
        if (firstDoorFrameAtMs == 0L) firstDoorFrameAtMs = now;
        if (rawLine == null || rawLine.length() == 0) return;
        lastDoorFrameRaw = rawLine.length() > 120 ? rawLine.substring(0, 120) : rawLine;
        synchronized (RAW_FRAMES) {
            if (now - lastRawSampleAtMs >= RAW_FRAME_SAMPLE_MS) {
                lastRawSampleAtMs = now;
                if (RAW_FRAMES.size() >= MAX_RAW_FRAMES) RAW_FRAMES.removeFirst();
                RAW_FRAMES.addLast("[" + nowStr(now) + "] " + (rawLine.length() > 200 ? rawLine.substring(0, 200) : rawLine));
            }
        }
    }

    /** 管道每读到一行就计数（算「命中率」、判断是否被洪水淹没） */
    public static void countLineRead(boolean matched) {
        linesRead++;
        if (matched) linesMatched++;
    }

    /** 解析队列满主动丢弃的行数 */
    public static void countLineDropped() {
        linesDropped++;
    }

    /** 检测到行间空档（>500ms 无日志）时计数，用于识别「读端被卡住」 */
    public static void countChunkGap() {
        chunkGaps++;
    }

    /** 距上次收到车门报文的毫秒数；-1 表示本次开机还没收到过 */
    public static long getDoorFrameSilenceMs() {
        long t = lastDoorFrameAtMs;
        if (t <= 0) return -1L;
        return System.currentTimeMillis() - t;
    }

    public static long getDoorFrameCount() {
        return lastDoorFrameCount;
    }

    public static String getLastDoorFrameRaw() {
        return lastDoorFrameRaw;
    }

    /**
     * 通道心跳：每 60 秒由服务调用一次，把当时的吞吐与丢行情况记一条到 CH_CHANNEL。
     * 车主这次遇到的正是「通道没断，但信号整段消失」，只有这种心跳才查得出「丢行」。
     */
    public static void recordChannelHeartbeat() {
        long read = linesRead, matched = linesMatched, dropped = linesDropped, gaps = chunkGaps;
        long silence = getDoorFrameSilenceMs();
        StringBuilder sb = new StringBuilder(160);
        sb.append("读到").append(read).append("行 命中").append(matched).append(" 丢").append(dropped);
        if (read > 0) {
            sb.append(" 命中率").append(String.format(java.util.Locale.US, "%.1f", matched * 100.0 / read)).append('%');
        }
        sb.append(" 空档").append(gaps).append("次");
        if (silence < 0) {
            sb.append(" 车门帧=未收到");
        } else {
            sb.append(" 车门帧=").append(lastDoorFrameCount).append("帧/距今").append(silence / 1000L).append("秒");
        }
        record(CH_CHANNEL, sb.toString());
    }

    /** 通道事件（建立/断开/兜底接管/兜底收回/静默超时）统一从这里记 */
    public static void recordChannelEvent(String event) {
        if (event == null || event.length() == 0) return;
        record(CH_CHANNEL, "【事件】" + event);
    }

    /**
     * 导出为可读文本（随诊断包 signals.log 一并带回）
     */
    public static String exportAll() {
        StringBuilder sb = new StringBuilder(16384);
        sb.append("=== 关键车身信号变化轨迹（纯内存记录，只记变化，不记心跳） ===\n");
        sb.append("说明：车机 main 日志缓冲区实测仅约 3 秒容量，导出时真实车况会被冲掉；\n");
        sb.append("      本文件是助手在内存里持续记录的轨迹，不受缓冲区大小影响。\n\n");

        long silence = getDoorFrameSilenceMs();
        sb.append("--- 车门报文收帧统计 ---\n");
        sb.append("累计收帧: ").append(lastDoorFrameCount).append(" 帧\n");
        if (firstDoorFrameAtMs > 0) {
            sb.append("首次收帧: ").append(nowStr(firstDoorFrameAtMs)).append('\n');
        }
        if (silence < 0) {
            sb.append("最后收帧: 本次开机尚未收到过任何车门报文（主通道可能完全读不到车况）\n");
        } else {
            sb.append("最后收帧: 距今 ").append(silence / 1000L).append(" 秒");
            if (silence > 15000L) sb.append("（超过 15 秒，属异常）");
            sb.append('\n');
        }
        if (lastDoorFrameRaw != null && lastDoorFrameRaw.length() > 0) {
            sb.append("最后一帧原文: ").append(lastDoorFrameRaw).append('\n');
        }

        sb.append("\n--- 管道吞吐统计 ---\n");
        sb.append("读到 ").append(linesRead).append(" 行，命中白名单 ").append(linesMatched).append(" 行");
        if (linesRead > 0) {
            sb.append("（命中率 ").append(String.format(java.util.Locale.US, "%.1f", linesMatched * 100.0 / linesRead)).append("%）");
        }
        sb.append('\n');
        sb.append("解析队列满丢弃 ").append(linesDropped).append(" 行；检测到行间空档 ").append(chunkGaps).append(" 次\n");

        sb.append("\n--- 原始车门报文抽样（30 秒 1 帧，最近 ").append(MAX_RAW_FRAMES).append(" 帧）---\n");
        synchronized (RAW_FRAMES) {
            if (RAW_FRAMES.isEmpty()) {
                sb.append("（本次开机尚未收到车门报文）\n");
            } else {
                for (String s : RAW_FRAMES) sb.append(s).append('\n');
            }
        }

        sb.append("\n--- 各功能状态变化轨迹 ---\n");
        synchronized (CHANNELS) {
            boolean any = false;
            for (Map.Entry<String, LinkedList<String>> e : CHANNELS.entrySet()) {
                LinkedList<String> buf = e.getValue();
                if (buf == null || buf.isEmpty()) continue;
                any = true;
                sb.append("--- ").append(e.getKey()).append(" (").append(buf.size()).append(" 条变化) ---\n");
                for (String line : buf) sb.append(line).append('\n');
                sb.append('\n');
            }
            if (!any) {
                sb.append("（本次开机至今没有采集到任何车身状态变化）\n");
            }
        }
        return sb.toString();
    }

    /** 供 summary.json 用的一行健康摘要 */
    public static String getHealthSnapshot() {
        long silence = getDoorFrameSilenceMs();
        String s;
        if (silence < 0) {
            s = "车门报文=本次开机未收到";
        } else {
            s = "车门报文=累计" + lastDoorFrameCount + "帧 最后距今" + (silence / 1000L) + "秒";
            if (silence > 15000L) s += "(偏久,可能主通道读不到车况)";
        }
        s += " 读到" + linesRead + "行 命中" + linesMatched + " 丢" + linesDropped;
        return s;
    }

    /**
     * 供 summary.json 的 signal_stats 字段（一眼看出各功能采集了多少次变化）
     */
    public static org.json.JSONObject buildStatsJson() {
        org.json.JSONObject obj = new org.json.JSONObject();
        try {
            long silence = getDoorFrameSilenceMs();
            obj.put("door_frames", lastDoorFrameCount);
            obj.put("door_silence_sec", silence < 0 ? -1 : silence / 1000L);
            obj.put("lines_read", linesRead);
            obj.put("lines_matched", linesMatched);
            obj.put("lines_dropped", linesDropped);
            obj.put("chunk_gaps", chunkGaps);
            if (lastDoorFrameRaw != null && lastDoorFrameRaw.length() > 0) {
                obj.put("last_door_frame", lastDoorFrameRaw);
            }
            org.json.JSONObject counts = new org.json.JSONObject();
            synchronized (CHANNELS) {
                for (Map.Entry<String, LinkedList<String>> e : CHANNELS.entrySet()) {
                    LinkedList<String> buf = e.getValue();
                    counts.put(e.getKey(), buf == null ? 0 : buf.size());
                }
            }
            obj.put("channel_changes", counts);
        } catch (Throwable ignored) {}
        return obj;
    }

    /** 清空（换车主或排查完手动清理时用） */
    public static void clear() {
        synchronized (CHANNELS) {
            CHANNELS.clear();
            LAST_VALUE.clear();
            LAST_AT.clear();
        }
        synchronized (RAW_FRAMES) {
            RAW_FRAMES.clear();
        }
        firstDoorFrameAtMs = 0L;
        lastDoorFrameAtMs = 0L;
        lastDoorFrameCount = 0L;
        lastDoorFrameRaw = "";
        lastRawSampleAtMs = 0L;
        linesRead = 0L;
        linesMatched = 0L;
        linesDropped = 0L;
        chunkGaps = 0L;
    }
}
