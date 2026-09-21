package app.onepve.geelyconsole.utils;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * VoiceArbiter 全场景仲裁回归测试 (纯 Java，零 Android 依赖，直跑 JVM)。
 *
 * 覆盖：
 *  1. 异步生命周期：准备中占位、过期回调 (stale gen) 不错清新会话、
 *     prepare 失败也要释放通道、TTS 任意 onDone 仅当前代生效、
 *     6s 超时按失败兜底并正常出队。
 *  2. 优先级：P0 阻挡一切低级 (低级绝不打断 P0)、P0 清队列、
 *     同 P0 去重不重启、P1 同级最新覆盖、P1 打断 P2/P3、
 *     P2 单条浅队列 (旧被新替换)、P3 遇忙即丢。
 *  3. 外部流避让：通话中非 P0 全闭嘴、P0 best-effort 穿透、
 *     队列出队时通话开始则丢弃。
 *  4. 停止/销毁：stopAll 清队列 + 作废在途回调。
 *  5. 单调时钟：TTL 判定完全由注入 clock 驱动，免疫墙钟回拨。
 */
public class VoiceArbiterRegressionTest {

    // ---- 测试脚手架 ----

    static class FakeClock implements VoiceArbiter.Clock {
        long now = 100000L;
        @Override public long now() { return now; }
        void advance(long ms) { now += ms; }
    }

    static class FakeEngine implements VoiceArbiter.Engine {
        final List<String> plays = new ArrayList<>();
        int stopCalls = 0;

        @Override public void engineStop() { stopCalls++; }

        @Override public void enginePlay(String voiceFileName, String fallbackText) {
            plays.add((voiceFileName != null ? voiceFileName : "tts") + "|" + (fallbackText != null ? fallbackText : ""));
        }

        String lastPlay() { return plays.get(plays.size() - 1); }
        int playCount() { return plays.size(); }
    }

    static class FakeEnv implements VoiceArbiter.Environment {
        boolean externalActive = false;
        @Override public boolean isExternalAudioActive() { return externalActive; }
    }

    static class RecordingListener implements VoiceArbiter.Listener {
        final List<String> events = new ArrayList<>();
        @Override public void onEvent(String event) { events.add(event); }

        boolean contains(String frag) {
            for (String e : events) if (e.contains(frag)) return true;
            return false;
        }
        int count(String frag) {
            int c = 0;
            for (String e : events) if (e.contains(frag)) c++;
            return c;
        }
    }

    static int failures = 0;
    static int passes = 0;

    static void check(boolean cond, String name) {
        if (cond) { passes++; System.out.println("PASS " + name); }
        else { failures++; System.out.println("FAIL " + name); }
    }

    static VoiceArbiter.Request req(String file, int prio, FakeClock clock) {
        return new VoiceArbiter.Request(file, file == null ? "text" : file, prio, clock.now());
    }

    public static void main(String[] args) {
        test_p0_blocks_all_lower_priorities();
        test_same_p0_dedupe_no_restart();
        test_p1_latest_same_level();
        test_p1_preempts_p2();
        test_p2_single_shallow_queue_latest_wins();
        test_p2_queue_ttl_expiry();
        test_p3_dropped_when_busy();
        test_p3_plays_when_idle();
        test_preparing_state_occupies_slot();
        test_stale_callback_does_not_clear_new_session();
        test_prepare_fail_releases_channel();
        test_tts_ondone_bound_to_generation();
        test_tts_timeout_treated_as_failure_and_dequeues();
        test_stop_all_clears_queue_and_invalidates_inflight();
        test_external_call_blocks_all_but_p0();
        test_dequeue_rechecks_external_state();
        test_monotonic_clock_drives_ttl();
        test_queue_dropped_when_p0_arrives();
        test_dequeue_gap_is_natural_interval();
        test_dedupe_same_request_active();

        System.out.println();
        System.out.println("==== RESULT: pass=" + passes + " fail=" + failures + " ====");
        if (failures > 0) {
            System.exit(1);
        }
    }

    // ---------------- 异步生命周期 ----------------

    static void test_preparing_state_occupies_slot() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        RecordingListener l = new RecordingListener();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, l);

        VoiceArbiter.Request p1 = req("gear_d.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock);
        check(a.submit(p1), "prepare-occupy: submit accepted");
        // 未回调 onEngineStarted —— 通道必须已被占位 (busy)
        check(a.isBusy(), "prepare-occupy: channel busy before engine-started callback");
        // P2 到来必须排队而不是并行直插
        VoiceArbiter.Request p2 = req("door_open.mp3", VoiceArbiter.PRIORITY_P2_DOOR, clock);
        check(a.submit(p2), "prepare-occupy: p2 enqueued");
        check(a.queueSize() == 1, "prepare-occupy: p2 waits in queue");
        // 高优先级在准备阶段也照常抢占
        VoiceArbiter.Request p0 = req("gear_park_alarm.mp3", VoiceArbiter.PRIORITY_P0_ALARM, clock);
        check(a.submit(p0), "prepare-occupy: p0 preempts preparing");
        check(a.getCurrentPriority() == VoiceArbiter.PRIORITY_P0_ALARM,
                "prepare-occupy: current is p0");
        check(a.queueSize() == 0, "prepare-occupy: queue cleared by p0");
        // 被抢占请求的迟到 started 回调 (旧代) 必须被丢弃
        long staleGen = a.currentGeneration() - 1;
        a.onEngineStarted(staleGen);
        check(!l.contains("engine-started") || l.count("stale-start") >= 1,
                "prepare-occupy: stale started dropped");
    }

    static void test_stale_callback_does_not_clear_new_session() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, null);

        VoiceArbiter.Request first = req("gear_d.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock);
        a.submit(first);
        long gen1 = a.currentGeneration();

        // P0 抢占 -> generation 推进
        VoiceArbiter.Request second = req("gear_park_alarm.mp3", VoiceArbiter.PRIORITY_P0_ALARM, clock);
        a.submit(second);
        long gen2 = a.currentGeneration();
        check(gen2 > gen1, "stale: generation advanced on preempt");

        // 旧会话 (gen1) 的完成回调迟到大摇大摆到达：绝不能清掉 P0 新会话
        a.onEngineFinished(gen1);
        check(a.isBusy(), "stale: new session survives old finish callback");
        check(a.getCurrentPriority() == VoiceArbiter.PRIORITY_P0_ALARM,
                "stale: p0 still current after stale finish");

        // 新会话正常回调 -> 通道释放
        a.onEngineFinished(gen2);
        check(!a.isBusy(), "stale: correct gen finish releases channel");
    }

    static void test_prepare_fail_releases_channel() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, null);

        VoiceArbiter.Request r = req("mode_sport.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock);
        a.submit(r);
        long gen = a.currentGeneration();
        // 引擎 resolve 失败 (文件缺失等) -> onEngineFinished 同代回调
        a.onEngineFinished(gen);
        check(!a.isBusy(), "prepare-fail: channel released after engine failure");
        // 后续请求可正常受理，通道未卡死
        VoiceArbiter.Request next = req("gear_d.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock);
        check(a.submit(next), "prepare-fail: next request accepted after failure");
    }

    static void test_tts_ondone_bound_to_generation() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, null);

        VoiceArbiter.Request t1 = new VoiceArbiter.Request("tts:第一句", "第一句", VoiceArbiter.PRIORITY_P1_ACTION, clock.now());
        a.submit(t1);
        long gen1 = a.currentGeneration();
        a.onEngineStarted(gen1);

        // P1 同级最新：旧 TTS 被打断，generation 推进
        VoiceArbiter.Request t2 = new VoiceArbiter.Request("tts:第二句", "第二句", VoiceArbiter.PRIORITY_P1_ACTION, clock.now());
        a.submit(t2);
        long gen2 = a.currentGeneration();
        check(gen2 > gen1, "tts-ondone: generation advanced on p1-latest");

        // 被打断的第一句 TTS 引擎现在才回调 onDone (旧代码：任意 onDone 都释放焦点)
        a.onEngineFinished(gen1);
        check(a.isBusy(), "tts-ondone: stale onDone does not release new session");
        check(a.getCurrentPriority() == VoiceArbiter.PRIORITY_P1_ACTION, "tts-ondone: session still p1 t2");

        a.onEngineFinished(gen2);
        check(!a.isBusy(), "tts-ondone: current gen onDone releases channel");
    }

    static void test_tts_timeout_treated_as_failure_and_dequeues() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        RecordingListener l = new RecordingListener();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, l);

        // P1 TTS 播报 + P2 在队列里等待
        VoiceArbiter.Request p1 = new VoiceArbiter.Request("tts:驾驶模式已切换", "驾驶模式已切换", VoiceArbiter.PRIORITY_P1_ACTION, clock.now());
        a.submit(p1);
        long gen1 = a.currentGeneration();
        VoiceArbiter.Request p2 = req("door_open.mp3", VoiceArbiter.PRIORITY_P2_DOOR, clock);
        a.submit(p2);

        // 6 秒超时 (引擎无任何回调)：超时按失败兜底 —— 通道释放且队列照常衔接出队
        clock.advance(VoiceArbiter.TTS_PLAY_TIMEOUT_MS + 1);
        a.onEngineFinished(gen1); // 模拟超时 runnable 触发 (VehicleVoicePlayer 调 onEngineFinished(gen))

        check(!a.isBusy() || a.getCurrentPriority() == VoiceArbiter.PRIORITY_P2_DOOR,
                "tts-timeout: channel moves on (queue dequeued or idle), not stuck on dead p1");
        check(engine.plays.size() >= 1, "tts-timeout: engine actually played something");
    }

    static void test_dequeue_gap_is_natural_interval() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, null);

        VoiceArbiter.Request p1 = req("gear_p.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock);
        a.submit(p1);
        long gen1 = a.currentGeneration();
        VoiceArbiter.Request p2 = req("door_open.mp3", VoiceArbiter.PRIORITY_P2_DOOR, clock);
        a.submit(p2);

        // 出队时才校验 TTL：入队后 3s 内完成 P1，P2 应出队播放
        clock.advance(500);
        a.onEngineFinished(gen1);
        check(engine.plays.size() == 2, "dequeue-gap: queued p2 dequeued after p1 finished");
        check(engine.plays.get(1).startsWith("door_open.mp3"), "dequeue-gap: dequeued content correct");
    }

    // ---------------- 优先级仲裁 ----------------

    static void test_p0_blocks_all_lower_priorities() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        RecordingListener l = new RecordingListener();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, l);

        VoiceArbiter.Request p0 = req("gear_park_alarm.mp3", VoiceArbiter.PRIORITY_P0_ALARM, clock);
        a.submit(p0);
        long gen0 = a.currentGeneration();
        a.onEngineStarted(gen0); // P0 正在播

        // 低优先级全部拒绝：绝不打断 P0 (旧代码 else 分支会 stopCurrentVoice 打断！)
        VoiceArbiter.Request p1 = req("gear_d.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock);
        VoiceArbiter.Request p2 = req("door_open.mp3", VoiceArbiter.PRIORITY_P2_DOOR, clock);
        VoiceArbiter.Request p3 = req("flameout.mp3", VoiceArbiter.PRIORITY_P3_ADVISORY, clock);

        a.submit(p1);
        a.submit(p2);
        a.submit(p3);

        check(a.getCurrentPriority() == VoiceArbiter.PRIORITY_P0_ALARM,
                "p0-blocks: current still p0 after lower submissions");
        check(engine.stopCalls == 0, "p0-blocks: engine never stopped by lower priority (旧bug: 低级else打断P0)");
        check(engine.playCount() == 1, "p0-blocks: no new play started by lower priority");
        check(a.queueSize() == 0, "p0-blocks: queue stays empty while p0 active");
    }

    static void test_same_p0_dedupe_no_restart() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, null);

        VoiceArbiter.Request p0 = req("tcu_alarm.mp3", VoiceArbiter.PRIORITY_P0_ALARM, clock);
        a.submit(p0);
        long gen = a.currentGeneration();
        a.onEngineStarted(gen);

        int playsBefore = engine.playCount();
        int stopsBefore = engine.stopCalls;

        // 完全相同的 P0 重报 (CAN 心跳重复)：去重，不重启
        VoiceArbiter.Request dup = req("tcu_alarm.mp3", VoiceArbiter.PRIORITY_P0_ALARM, clock);
        a.submit(dup);

        check(engine.playCount() == playsBefore, "p0-dedupe: same p0 not restarted");
        check(engine.stopCalls == stopsBefore, "p0-dedupe: current p0 not stopped");
        check(a.getCurrentPriority() == VoiceArbiter.PRIORITY_P0_ALARM, "p0-dedupe: still p0");
    }

    static void test_dedupe_same_request_active() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, null);

        VoiceArbiter.Request p1 = req("gear_d.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock);
        a.submit(p1);
        int plays = engine.playCount();
        a.submit(req("gear_d.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock));
        check(engine.playCount() == plays, "dedupe-active: identical active request not restarted");
    }

    static void test_p1_latest_same_level() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, null);

        a.submit(req("gear_r.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock));
        long gen = a.currentGeneration();
        a.onEngineStarted(gen);
        // R -> N 快切：同级 P1 只播最新
        a.submit(req("gear_n.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock));

        check(engine.plays.size() == 2, "p1-latest: new p1 played");
        check(engine.plays.get(1).startsWith("gear_n.mp3"), "p1-latest: latest content wins");
        check(a.getCurrentPriority() == VoiceArbiter.PRIORITY_P1_ACTION, "p1-latest: still p1");
    }

    static void test_p1_preempts_p2() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, null);

        a.submit(req("door_open.mp3", VoiceArbiter.PRIORITY_P2_DOOR, clock));
        a.onEngineStarted(a.currentGeneration());
        a.submit(req("gear_d.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock));

        check(engine.plays.size() == 2, "p1-preempt-p2: p1 played");
        check(engine.plays.get(1).startsWith("gear_d.mp3"), "p1-preempt-p2: p1 content correct");
        check(engine.stopCalls >= 1, "p1-preempt-p2: old p2 stopped");
    }

    static void test_p2_single_shallow_queue_latest_wins() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, null);

        a.submit(req("gear_p.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock));
        a.onEngineStarted(a.currentGeneration());
        a.submit(req("door_fl_open.mp3", VoiceArbiter.PRIORITY_P2_DOOR, clock));
        check(a.queueSize() == 1, "p2-queue: first p2 queued");
        // 第二条 P2 替换第一条 (单条浅队列)
        a.submit(req("door_fr_open.mp3", VoiceArbiter.PRIORITY_P2_DOOR, clock));
        check(a.queueSize() == 1, "p2-queue: still single slot (shallow)");
        // P1 完成后出队的必须是最新那条
        long gen = a.currentGeneration();
        a.onEngineFinished(gen);
        check(engine.plays.size() == 2, "p2-queue: dequeued after p1 done");
        check(engine.plays.get(1).startsWith("door_fr_open.mp3"), "p2-queue: latest p2 wins slot");
    }

    static void test_p2_queue_ttl_expiry() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        RecordingListener l = new RecordingListener();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, l);

        a.submit(req("gear_p.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock));
        a.onEngineStarted(a.currentGeneration());
        a.submit(req("door_open.mp3", VoiceArbiter.PRIORITY_P2_DOOR, clock));

        // P1 迟迟不结束，P2 在队列里超过 TTL 3 秒
        clock.advance(VoiceArbiter.P2_TTL_MS + 100);
        long gen = a.currentGeneration();
        a.onEngineFinished(gen);

        check(engine.plays.size() == 1, "p2-ttl: expired queued p2 dropped, not played");
        check(l.contains("queue-expired"), "p2-ttl: expiry event emitted");
    }

    static void test_monotonic_clock_drives_ttl() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        RecordingListener l = new RecordingListener();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, l);

        a.submit(req("gear_p.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock));
        a.onEngineStarted(a.currentGeneration());
        a.submit(req("door_open.mp3", VoiceArbiter.PRIORITY_P2_DOOR, clock));

        // 2.9 秒 (TTL 内)：应正常出队
        clock.advance(VoiceArbiter.P2_TTL_MS - 100);
        long gen = a.currentGeneration();
        a.onEngineFinished(gen);
        check(engine.plays.size() == 2, "monotonic: within-ttl p2 dequeued");
    }

    static void test_p3_dropped_when_busy() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, null);

        a.submit(req("gear_d.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock));
        a.onEngineStarted(a.currentGeneration());
        boolean accepted = a.submit(req("low_fuel.mp3", VoiceArbiter.PRIORITY_P3_ADVISORY, clock));
        check(!accepted, "p3-busy: p3 dropped when busy");
        check(engine.plays.size() == 1, "p3-busy: p3 not played");
        check(a.queueSize() == 0, "p3-busy: p3 never queued");
    }

    static void test_p3_plays_when_idle() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, null);

        check(a.submit(req("low_fuel.mp3", VoiceArbiter.PRIORITY_P3_ADVISORY, clock)), "p3-idle: accepted when idle");
        check(engine.plays.size() == 1, "p3-idle: played");
    }

    // ---------------- 停止/销毁 ----------------

    static void test_stop_all_clears_queue_and_invalidates_inflight() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, null);

        a.submit(req("gear_d.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock));
        long gen = a.currentGeneration();
        a.onEngineStarted(gen);
        a.submit(req("door_open.mp3", VoiceArbiter.PRIORITY_P2_DOOR, clock));

        a.stopAll("gear-change");

        check(a.queueSize() == 0, "stop-all: queue cleared");
        check(!a.isBusy(), "stop-all: channel idle");
        check(engine.stopCalls >= 1, "stop-all: engine stopped");

        // stopAll 后旧代回调不得复活通道
        a.onEngineFinished(gen);
        check(!a.isBusy(), "stop-all: stale finish after stop does not resurrect");

        // 停止后新请求正常受理
        check(a.submit(req("mode_eco.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock)), "stop-all: new request accepted after stop");
    }

    static void test_queue_dropped_when_p0_arrives() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, null);

        a.submit(req("gear_d.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock));
        a.onEngineStarted(a.currentGeneration());
        a.submit(req("door_open.mp3", VoiceArbiter.PRIORITY_P2_DOOR, clock));
        check(a.queueSize() == 1, "p0-clear-queue: p2 queued");

        a.submit(req("oil_alarm.mp3", VoiceArbiter.PRIORITY_P0_ALARM, clock));
        check(a.queueSize() == 0, "p0-clear-queue: p0 cleared queue");
        check(a.getCurrentPriority() == VoiceArbiter.PRIORITY_P0_ALARM, "p0-clear-queue: p0 current");

        // P1 结束后出队：队列已空，不得播放旧 P2
        a.onEngineFinished(a.currentGeneration());
        check(engine.plays.stream().noneMatch(p -> p.startsWith("door_open.mp3")) || engine.plays.size() <= 2,
                "p0-clear-queue: stale p2 never played");
    }

    // ---------------- 外部流避让 ----------------

    static void test_external_call_blocks_all_but_p0() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, null);

        env.externalActive = true; // 通话/VoIP 中

        boolean p1 = a.submit(req("gear_d.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock));
        boolean p2 = a.submit(req("door_open.mp3", VoiceArbiter.PRIORITY_P2_DOOR, clock));
        boolean p3 = a.submit(req("low_fuel.mp3", VoiceArbiter.PRIORITY_P3_ADVISORY, clock));

        check(!p1 && !p2 && !p3, "external: p1/p2/p3 all dropped during call");
        check(engine.plays.size() == 0, "external: nothing played during call");
        check(!a.isBusy(), "external: channel untouched during call");

        // P0 best-effort 穿透 (已允许通道内尽力播报，不强制抢通话路由)
        boolean p0 = a.submit(req("gear_park_alarm.mp3", VoiceArbiter.PRIORITY_P0_ALARM, clock));
        check(p0, "external: p0 best-effort passes through");
        check(engine.plays.size() == 1, "external: p0 played best-effort");
    }

    static void test_dequeue_rechecks_external_state() {
        FakeClock clock = new FakeClock();
        FakeEngine engine = new FakeEngine();
        FakeEnv env = new FakeEnv();
        RecordingListener l = new RecordingListener();
        VoiceArbiter a = new VoiceArbiter(engine, env, clock, l);

        a.submit(req("gear_p.mp3", VoiceArbiter.PRIORITY_P1_ACTION, clock));
        a.onEngineStarted(a.currentGeneration());
        a.submit(req("door_open.mp3", VoiceArbiter.PRIORITY_P2_DOOR, clock));

        // 出队瞬间车主接起电话：到出队再校验环境，丢弃
        env.externalActive = true;
        long gen = a.currentGeneration();
        a.onEngineFinished(gen);

        check(engine.plays.size() == 1, "dequeue-recheck: queued voice dropped when call started");
        check(l.contains("queue-drop-external"), "dequeue-recheck: drop event emitted");
        check(!a.isBusy(), "dequeue-recheck: channel stays idle");
    }
}