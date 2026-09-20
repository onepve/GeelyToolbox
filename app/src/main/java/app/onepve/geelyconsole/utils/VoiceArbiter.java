package app.onepve.geelyconsole.utils;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 车辆座舱语音音频仲裁核心 (全场景音频仲裁引擎)。
 *
 * 设计铁律：
 * 1. 单线程调度：所有仲裁状态变更只发生在主线程 (Looper.getMainLooper())，
 *    外部线程调用一律 post 到主线程串行执行，彻底消灭并发竞态。
 * 2. generation 单调代数：每次 stop/抢占/销毁 generation++；所有异步回调
 *    (MediaPlayer completion / TTS onDone / TTS 超时) 必须携带发起时的
 *    generation，与当前代不匹配直接丢弃 —— 彻底修复「过期回调错清新会话」。
 * 3. 准备占位：play() 入口即占用通道 (state=ACTIVE + currentPriority)，
 *    即便底层 MediaPlayer 还在 prepare()，高优先级抢占也照常生效；
 *    isPlaying() 从不参与仲裁判断 (prepare 中会抛 IllegalStateException)，
 *    ACTIVE 态只由仲裁状态机驱动，与底层播放器物理状态解耦。
 * 4. P0 阻挡一切：P0 安全警报可打断任何当前播放且清空队列；期间所有低级
 *    新请求一律拒绝 (不重启、不排队、不打断 P0)。
 * 5. 去重：与当前活跃语音完全相同的请求 (同名同优先级) 不重启。
 * 6. P1 同级最新：同级 P1 新请求打断当前 P1，只播最新。
 * 7. P2 单条浅队列：仅保留 1 条最新 P2，TTL 3 秒；出队时再次校验新鲜度，
 *    过期/环境变化 (通话中) 则丢弃。
 * 8. P3 遇忙即丢：绝不排队积压。
 * 9. 时序基准：统一单调时钟 (SystemClock.elapsedRealtime / 可注入 Clock)，
 *    免疫系统时间被 NTP/用户回拨。
 * 10. 外部通话/导航流避让由 VoiceEnvironment 谓词注入：仲裁器只信回调结果，
 *     不自行编造能力。
 */
final class VoiceArbiter {

    /** 优先级常量与 VehicleVoicePlayer 保持一致 */
    public static final int PRIORITY_P0_ALARM = 0;
    public static final int PRIORITY_P1_ACTION = 1;
    public static final int PRIORITY_P2_DOOR = 2;
    public static final int PRIORITY_P3_ADVISORY = 3;

    /** 播放请求 (不可变值对象) */
    static final class Request {
        final String voiceFileName;   // 可为 null (纯 TTS 文本)
        final String fallbackText;    // 可为 null (纯音频)
        final int priority;
        final long enqueueAt;        // 单调时钟毫秒 (由 clock 提供)

        Request(String voiceFileName, String fallbackText, int priority, long enqueueAt) {
            this.voiceFileName = voiceFileName;
            this.fallbackText = fallbackText;
            this.priority = priority;
            this.enqueueAt = enqueueAt;
        }

        boolean sameAs(Request other) {
            if (other == null) return false;
            if (this == other) return true;
            boolean sameFile = (this.voiceFileName == null && other.voiceFileName == null)
                    || (this.voiceFileName != null && this.voiceFileName.equals(other.voiceFileName));
            boolean sameText = (this.fallbackText == null && other.fallbackText == null)
                    || (this.fallbackText != null && this.fallbackText.equals(other.fallbackText));
            return sameFile && sameText;
        }
    }

    /** 底层播报通道 (由 VehicleVoicePlayer 实现) */
    interface Engine {
        /** 立即物理停止当前播报 (同步安全，内部自处理线程) */
        void engineStop();

        /** 按解析结果真实启动播报 */
        void enginePlay(String voiceFileName, String fallbackText);
    }

    /** 外部音频环境谓词 (电话/导航流避让) */
    interface Environment {
        boolean isExternalAudioActive(); // 通话或 VoIP 或导航播报流活跃
    }

    /** 可注入时钟 (测试可控) */
    interface Clock {
        long now();
    }

    private enum State { IDLE, PREPARING, ACTIVE }

    /** 仲裁器对外事件，用于回归测试 */
    interface Listener {
        void onEvent(String event);
    }

    private final Engine engine;
    private final Environment environment;
    private final Clock clock;
    private final Listener listener;
    private final Deque<Request> p2Queue = new ArrayDeque<>(2); // 浅队列：最多 1 条

    private State state = State.IDLE;
    private Request current = null;         // 当前已受理 (占位) 的请求
    private Request active = null;          // 已真正开始播放的请求
    private long generation = 0L;           // 单调递增代数
    private boolean externalBlocked = false;

    static final long P2_TTL_MS = 3000L;
    static final long DEQUEUE_GAP_MS = 150L; // 出队自然间隔
    static final long TTS_PLAY_TIMEOUT_MS = 6000L;

    VoiceArbiter(Engine engine, Environment environment, Clock clock, Listener listener) {
        this.engine = engine;
        this.environment = environment;
        this.clock = clock;
        this.listener = listener;
    }

    // ---------------- 状态查询 (仅测试/诊断用) ----------------

    synchronized boolean isBusy() {
        return state != State.IDLE;
    }

    synchronized boolean isCurrent(Request r) {
        return current != null && current.sameAs(r);
    }

    synchronized int getCurrentPriority() {
        return current != null ? current.priority : Integer.MAX_VALUE;
    }

    synchronized int queueSize() {
        return p2Queue.size();
    }

    synchronized long currentGeneration() {
        return generation;
    }

    // ---------------- 核心 API ----------------

    /**
     * 提交播报请求。返回 true=已受理 (立即播放或入队)。
     * 必须在仲裁线程 (主线程) 调用。
     */
    synchronized boolean submit(Request req) {
        if (req == null) return false;
        emit("submit p" + req.priority + " " + safeName(req));

        // 0. 外部通话/导航流避让：仅 P0 可穿透 (best-effort，不抢通话路由)
        if (environment.isExternalAudioActive()) {
            if (req.priority != PRIORITY_P0_ALARM) {
                emit("drop-external p" + req.priority + " " + safeName(req));
                return false;
            }
            emit("p0-through-external " + safeName(req));
        }

        // 1. 去重：完全相同的请求正在播 (ACTIVE) 或准备 (PREPARING) —— 不重启 (含同 P0 心跳重报)
        if (current != null && current.sameAs(req) && current.priority == req.priority) {
            emit("dedupe-same " + safeName(req));
            return true;
        }

        // 2. P0 一票否决：清队列、杀当前
        if (req.priority == PRIORITY_P0_ALARM) {
            p2Queue.clear();
            if (state != State.IDLE) {
                emit("p0-preempt " + safeName(current));
                stopCurrentLocked("p0");
            }
            beginLocked(req);
            return true;
        }

        // 3. 通道忙判定 (纯仲裁状态，绝不依赖底层 isPlaying)
        boolean busy = (state != State.IDLE);

        if (!busy) {
            beginLocked(req);
            return true;
        }

        // 4. 忙碌仲裁
        if (req.priority < current.priority) {
            // 高优先级抢占
            p2Queue.clear(); // 被打断的旧会话相关排队项全部作废
            emit("preempt p" + req.priority + " over p" + current.priority + " " + safeName(req));
            stopCurrentLocked("preempt");
            beginLocked(req);
            return true;
        }

        if (req.priority == current.priority) {
            if (req.priority == PRIORITY_P1_ACTION) {
                // P1 同级最新：覆盖
                emit("p1-latest " + safeName(req));
                stopCurrentLocked("p1-latest");
                beginLocked(req);
                return true;
            }
            if (req.priority == PRIORITY_P2_DOOR) {
                // P2 单条浅队列：只保留最新一条
                p2Queue.clear();
                p2Queue.addLast(req);
                emit("p2-enqueue " + safeName(req));
                return true;
            }
            // P3 同级遇忙即丢
            emit("drop-busy p3 " + safeName(req));
            return false;
        }

        // 低优先级 vs 高优先级当前：P2 仅在当前为 P1 时入队 (P0 活跃期间不入队)
        if (req.priority == PRIORITY_P2_DOOR && current.priority == PRIORITY_P1_ACTION) {
            p2Queue.clear();
            p2Queue.addLast(req);
            emit("p2-enqueue " + safeName(req));
            return true;
        }
        // P3 / P2 vs P0：遇忙即丢
        emit("drop-busy p" + req.priority + " " + safeName(req));
        return false;
    }

    /** 底层真正开始发声 (MediaPlayer start 或 TTS speak 受理) 回调 */
    synchronized void onEngineStarted(long gen) {
        if (gen != generation) {
            emit("stale-start gen=" + gen);
            return;
        }
        state = State.ACTIVE;
        active = current;
        emit("engine-started " + safeName(current));
    }

    /** 当前播放自然完成 / 出错 / 被引擎终止 —— 尝试出队衔接 */
    synchronized void onEngineFinished(long gen) {
        if (gen != generation) {
            emit("stale-finish gen=" + gen);
            return;
        }
        Request done = current;
        state = State.IDLE;
        active = null;
        current = null;
        emit("engine-finished " + safeName(done));
        tryDequeue();
    }

    /** 出队校验：到点再验 TTL 与外部环境 */
    private synchronized void tryDequeue() {
        Request next = p2Queue.pollFirst();
        if (next == null) return;
        long age = clock.now() - next.enqueueAt;
        if (age > P2_TTL_MS) {
            emit("queue-expired " + safeName(next) + " age=" + age);
            return;
        }
        if (environment.isExternalAudioActive()) {
            emit("queue-drop-external " + safeName(next));
            return;
        }
        emit("dequeue " + safeName(next));
        beginLocked(next);
    }

    /**
     * 立即停止当前播报并清空队列 (换挡跃变第一毫秒掐灭 / 主开关关闭 / 服务销毁)。
     */
    synchronized void stopAll(String reason) {
        p2Queue.clear();
        if (state != State.IDLE) {
            stopCurrentLocked(reason);
        } else {
            generation++; // 空闲时也推进代数，作废在途回调
        }
        state = State.IDLE;
        emit("stop-all " + reason);
    }

    // ---------------- 内部 ----------------

    private void stopCurrentLocked(String reason) {
        generation++;
        engine.engineStop();
        state = State.IDLE;
        active = null;
        current = null;
    }

    private void beginLocked(Request req) {
        generation++;
        current = req;
        state = State.PREPARING;
        emit("begin p" + req.priority + " " + safeName(req));
        engine.enginePlay(req.voiceFileName, req.fallbackText);
    }

    private void emit(String event) {
        if (listener != null) listener.onEvent(event);
    }

    private static String safeName(Request r) {
        if (r == null) return "(null)";
        String n = r.voiceFileName != null ? r.voiceFileName : r.fallbackText;
        return n != null ? n : "(empty)";
    }
}