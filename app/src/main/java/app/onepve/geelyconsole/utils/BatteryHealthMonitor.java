package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * 电瓶健康看板一期采集中枢（纯被动采样，零轮询、零额外线程、零高频磁盘擦写）。
 *
 * 数据来源：VehicleAutomationService 已有的 12V 电压 CAN 报文回调（mModelBatteryVolt）
 * 与车速/点火判定，仅在电压样本到达时同步喂入，不自己发起任何采集。
 *
 * 一期三项健康指标（用户已确认的 SOH 一期方案）：
 *   1. 静置电压   —— 熄火 + 零车速时的蓄电池开路电压（12.5~12.8V 优 / <12.2V 亏电 / <12.0V 衰减）
 *   2. 启动压降   —— 点火瞬间的电压低谷（≥9.6V 健康 / 9.0~9.6 注意 / <9.0 偏弱）
 *   3. 充电平台   —— 发动机运行时发电机回充电压（13.8~14.6V 正常 / >15 过充风险 / <13.5 充电不足）
 *
 * 铁律：所有阈值收敛在本文件常量区，前端只渲染 Java 算好的分级结论，严禁在 Vue 里写死业务参数。
 * 持久化：toolbox_settings 单 key JSON（battery_health_v1），仅在指标翻转/会话收尾时落盘。
 */
public class BatteryHealthMonitor {

    private static final String TAG = "BatteryHealthMonitor";
    private static final String PREFS = "toolbox_settings";
    private static final String KEY_STATE = "battery_health_v1";

    // ---------------- 车规阈值区（全项目唯一真源，改动只许动这里） ----------------
    private static final float REST_EXCELLENT_MIN = 12.5f;   // 静置 ≥12.5V 优
    private static final float REST_GOOD_MIN      = 12.2f;   // 12.2~12.5 良
    private static final float REST_LOW_MIN       = 12.0f;   // 12.0~12.2 偏低，<12.0 亏电/衰减
    private static final float CRANK_HEALTHY_MIN  = 9.6f;    // 启动压降 ≥9.6V 健康
    private static final float CRANK_WEAK_MIN     = 9.0f;    // 9.0~9.6 注意，<9.0 偏弱
    private static final float CHARGE_NORM_MIN    = 13.8f;   // 充电平台 13.8~14.6 正常
    private static final float CHARGE_NORM_MAX    = 14.6f;
    private static final float CHARGE_OVER_MAX    = 15.0f;   // >15 过充风险
    private static final float CHARGE_UNDER_MAX   = 13.5f;   // <13.5 充电不足（13.5~13.8 尚可）
    private static final float CHARGE_ENTRY_V     = 13.2f;   // 判定"发电机在充"的入门电压（与既有 isEngineRunning 权威判定一致）
    private static final float VOLT_MIN = 9.0f, VOLT_MAX = 16.5f; // 车规有效区间（与全项目校验一致）
    private static final long  CRANK_WINDOW_MS    = 8000L;   // 点火前后 ±8s 内的最低电压记为启动压降
    private static final long  REST_STABLE_MS     = 180000L; // 静置满 3 分钟，静置电压才可信
    private static final long  REST_SAMPLE_TTL_MS = 12L * 3600 * 1000; // 静置样本 12h 过期
    // -------------------------------------------------------------------------

    // 运行态（内存流转；进程被杀后由落盘 JSON 恢复，丢的只是进行中会话）
    private static float sCrankMinV = -1f;        // 本次点火窗口内观测到的最低电压
    private static long  sCrankWindowStart = 0L;
    private static boolean sCrankArmed = true;    // 引擎熄火时重新武装，点火翻转时结算
    private static float sChargeSum = 0f;
    private static int   sChargeCount = 0;
    private static float sChargeMax = -1f;

    private BatteryHealthMonitor() {}

    /** 由 VehicleAutomationService 在每条有效电压样本处喂入（同步、轻量、零分配热点路径）。 */
    public static synchronized void onVoltageSample(Context ctx, float volt, int speedKmH, boolean engineRunning) {
        if (ctx == null || volt < VOLT_MIN || volt > VOLT_MAX) return;
        long now = System.currentTimeMillis();

        if (engineRunning || speedKmH > 0) {
            // ---- 运行态 ----
            if (volt >= CHARGE_ENTRY_V) {
                sChargeSum += volt;
                sChargeCount++;
                if (volt > sChargeMax) sChargeMax = volt;
            }
            if (sCrankArmed) {
                // 熄火→运行翻转（或运行中首次喂样）：开启点火压降窗口
                sCrankArmed = false;
                sCrankWindowStart = now;
                sCrankMinV = volt;
                finalizeChargeSession(ctx);
            } else if (now - sCrankWindowStart <= CRANK_WINDOW_MS) {
                if (sCrankMinV < 0 || volt < sCrankMinV) sCrankMinV = volt;
            } else if (sCrankMinV > 0) {
                // 点火窗口关闭：结算启动压降
                settleCrank(ctx, sCrankMinV);
                sCrankMinV = -1f;
            }
        } else {
            // ---- 静置态（熄火 + 零车速）----
            if (!sCrankArmed) {
                // 运行→熄火翻转：结算点火窗口（若还开着）与充电会话
                if (sCrankMinV > 0) { settleCrank(ctx, sCrankMinV); sCrankMinV = -1f; }
                finalizeChargeSession(ctx);
                sCrankArmed = true;
            }
            JSONObject st = load(ctx);
            try {
                long restSince = st.optLong("rest_since", 0L);
                long prevRestTs = st.optLong("rest_ts", 0L);
                if (now - prevRestTs > REST_SAMPLE_TTL_MS) restSince = now; // 样本过期：静置计时重开
                if (restSince <= 0) restSince = now;
                st.put("rest_v", volt);
                st.put("rest_ts", now);
                st.put("rest_since", restSince);
                st.put("rest_stable", (now - restSince) >= REST_STABLE_MS);
                saveIfChanged(ctx, st);
            } catch (Exception ignored) {}
        }
    }

    /** 结算启动压降到落盘状态。 */
    private static void settleCrank(Context ctx, float minV) {
        try {
            JSONObject st = load(ctx);
            st.put("crank_min_v", minV);
            st.put("crank_ts", System.currentTimeMillis());
            saveIfChanged(ctx, st);
        } catch (Exception ignored) {}
    }

    /** 充电会话收尾：写平台均值/峰值与时间戳。 */
    private static void finalizeChargeSession(Context ctx) {
        if (sChargeCount <= 0) return;
        try {
            JSONObject st = load(ctx);
            st.put("charge_avg_v", sChargeSum / sChargeCount);
            st.put("charge_max_v", sChargeMax);
            st.put("charge_ts", System.currentTimeMillis());
            saveIfChanged(ctx, st);
            sChargeSum = 0f; sChargeCount = 0; sChargeMax = -1f;
        } catch (Exception ignored) {}
    }

    /**
     * 供 JSBridge 输出的完整健康看板 JSON（分级结论全部在此算好，前端零业务参数）。
     * 结构：{rest:{v,ts,stable,grade,gradeText}, crank:{v,ts,grade,gradeText},
     *        charge:{avg,max,ts,grade,gradeText}, score, scoreText, updated_at}
     * grade: 0=待采样 1=优/健康 2=良/正常 3=注意 4=风险
     */
    public static String buildHealthJson(Context ctx) {
        JSONObject out = new JSONObject();
        try {
            JSONObject st = load(ctx);
            long now = System.currentTimeMillis();

            // 1) 静置电压
            JSONObject rest = new JSONObject();
            float restV = (float) st.optDouble("rest_v", -1);
            long restTs = st.optLong("rest_ts", 0);
            boolean restStable = st.optBoolean("rest_stable", false)
                    && restTs > 0 && (now - restTs) <= REST_SAMPLE_TTL_MS;
            if (restV < VOLT_MIN || !restStable) {
                rest.put("grade", 0);
                rest.put("gradeText", restV < VOLT_MIN ? "待采样" : "静置不足3分钟");
                if (restV >= VOLT_MIN) rest.put("v", restV);
            } else {
                rest.put("v", restV);
                int g;
                String t;
                if (restV >= REST_EXCELLENT_MIN) { g = 1; t = "优"; }
                else if (restV >= REST_GOOD_MIN)  { g = 2; t = "良好"; }
                else if (restV >= REST_LOW_MIN)   { g = 3; t = "偏低"; }
                                else              { g = 4; t = "亏电/衰减"; }
                rest.put("grade", g); rest.put("gradeText", t);
            }
            rest.put("ts", restTs);
            out.put("rest", rest);

            // 2) 启动压降
            JSONObject crank = new JSONObject();
            float crankV = (float) st.optDouble("crank_min_v", -1);
            long crankTs = st.optLong("crank_ts", 0);
            if (crankV < VOLT_MIN || (now - crankTs) > REST_SAMPLE_TTL_MS) {
                crank.put("grade", 0); crank.put("gradeText", "待点火采样");
            } else {
                crank.put("v", crankV);
                int g; String t;
                if (crankV >= CRANK_HEALTHY_MIN) { g = 1; t = "健康"; }
                else if (crankV >= CRANK_WEAK_MIN){ g = 3; t = "注意"; }
                else                              { g = 4; t = "偏弱"; }
                crank.put("grade", g); crank.put("gradeText", t);
            }
            crank.put("ts", crankTs);
            out.put("crank", crank);

            // 3) 充电平台
            JSONObject charge = new JSONObject();
            float chargeAvg = (float) st.optDouble("charge_avg_v", -1);
            float chargeMax = (float) st.optDouble("charge_max_v", -1);
            long chargeTs = st.optLong("charge_ts", 0);
            if (chargeAvg < CHARGE_ENTRY_V || (now - chargeTs) > REST_SAMPLE_TTL_MS) {
                charge.put("grade", 0); charge.put("gradeText", "待行驶采样");
            } else {
                charge.put("avg", chargeAvg);
                charge.put("max", chargeMax);
                int g; String t;
                if (chargeMax > CHARGE_OVER_MAX)                  { g = 4; t = "过充风险"; }
                else if (chargeAvg >= CHARGE_NORM_MIN && chargeAvg <= CHARGE_NORM_MAX) { g = 1; t = "正常"; }
                else if (chargeAvg > CHARGE_UNDER_MAX)            { g = 2; t = "尚可"; }
                else                                              { g = 3; t = "充电不足"; }
                charge.put("grade", g); charge.put("gradeText", t);
            }
            charge.put("ts", chargeTs);
            out.put("charge", charge);

            // 4) 综合健康分（仅对已采样子项计权：静置40 + 压降35 + 充电25）
            int sum = 0, wsum = 0;
            int[][] items = {
                {gradeToScore(rest.optInt("grade", 0), restStable), 40},
                {gradeToScore(crank.optInt("grade", 0), crank.optInt("grade", 0) > 0), 35},
                {gradeToScore(charge.optInt("grade", 0), charge.optInt("grade", 0) > 0), 25}
            };
            for (int[] it : items) {
                if (it[1] > 0 && it[0] >= 0) { sum += it[0] * it[1]; wsum += it[1]; }
            }
            if (wsum > 0) {
                int score = Math.round(sum * 100f / (wsum * 100f));
                out.put("score", score);
                out.put("scoreText", score >= 85 ? "健康" : (score >= 65 ? "良好" : (score >= 45 ? "注意养护" : "建议检修")));
            } else {
                out.put("score", -1);
                out.put("scoreText", "采样积累中");
            }
            out.put("updated_at", now);
        } catch (Exception e) {
            try { out.put("error", e.getMessage()); } catch (Exception ignored) {}
        }
        return out.toString();
    }

    /** 子项等级→百分制（0=待采样返回 -1 不计权；1..4 → 100/85/60/30）。 */
    private static int gradeToScore(int grade, boolean sampled) {
        if (!sampled || grade <= 0) return -1;
        switch (grade) {
            case 1: return 100;
            case 2: return 85;
            case 3: return 60;
            default: return 30;
        }
    }

    private static JSONObject load(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return new JSONObject(sp.getString(KEY_STATE, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** 仅在关键字段变化时落盘，避免每次样本都擦写 flash。 */
    private static void saveIfChanged(Context ctx, JSONObject st) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String next = st.toString();
            if (!next.equals(sp.getString(KEY_STATE, null))) {
                sp.edit().putString(KEY_STATE, next).apply();
            }
        } catch (Exception ignored) {}
    }
}
