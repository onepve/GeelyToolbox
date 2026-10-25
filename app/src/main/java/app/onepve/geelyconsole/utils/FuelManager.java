package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * 燃油管家核心数据中枢 (FuelManager)
 * 物理存储路径: /sdcard/GeelyPilot/fuel/ (与 voices 平级，卸载绝不丢数据)
 * 包含:
 * 1. fuel_config.json: 基础参数、标称容积、常用油品、配对码
 * 2. fuel_records.json: 加油流水与能耗台账
 * 3. 小熊油耗跳枪法 (加满优先) 与阶段油耗分摊统计引擎
 */
public class FuelManager {

    private static final String TAG = "FuelManager";
    private static final String DIR_NAME = "GeelyPilot/fuel";
    private static final String FILE_CONFIG = "fuel_config.json";
    private static final String FILE_RECORDS = "fuel_records.json";

    private static final String FILE_STATE = "fuel_state.json";
    private static final String FILE_VEHICLE_KEY = "vehicle_identity.key";

    // 车机端防卡顿性能与轻量化门禁限制:
    // 最多保留最新 30 条流水记录，超出自动滚动淘汰最旧记录 (全量数据由云端 PWA/D1 承接)
    public static final int MAX_LOCAL_RECORDS = 30;

    private static final Object LOCK = new Object();

    /**
     * 获取持久化目录: /sdcard/GeelyPilot/fuel
     */
    public static File getFuelDir() {
        File dir = new File(Environment.getExternalStorageDirectory(), DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * 读取燃油配置 (若无则自动生成初始默认配置)
     */
    public static JSONObject getConfig(Context context) {
        synchronized (LOCK) {
            File configFile = new File(getFuelDir(), FILE_CONFIG);
            if (configFile.exists() && configFile.length() > 0) {
                try {
                    String content = readFileContent(configFile);
                    JSONObject cfg = new JSONObject(content);
                    // 确保爱车识别码与出厂硬件身份同步对齐 (防重置/刷机防漂移)
                    String persistentCarId = getHardwareCarIdentity(context);
                    if (!persistentCarId.isEmpty() && (!cfg.has("car_id") || cfg.optString("car_id", "").startsWith("COOL_"))) {
                        cfg.put("car_id", persistentCarId);
                        saveConfig(context, cfg);
                    }
                    return cfg;
                } catch (Throwable t) {
                    Log.w(TAG, "读取 fuel_config.json 异常，生成默认配置: " + t.getMessage());
                }
            }
            // 默认初始配置
            JSONObject def = new JSONObject();
            try {
                double defCap = app.onepve.geelyconsole.hal.VehiclePlatformFactory.getPlatform(context).getDefaultFuelCapacityLiters();
                def.put("tank_capacity", defCap);        // 按照当前实车底层 HAL 自动识别车型标称容积
                def.put("default_fuel_type", "92#");     // 默认 92# 汽油
                def.put("default_province", "浙江");     // 默认发改委省份
                def.put("refuel_threshold_l", 4.0);      // 加油检测跃变阈值 (4.0L)
                def.put("car_id", getHardwareCarIdentity(context)); // 原车出厂芯片/总线硬件唯一指纹
                def.put("plate_number", "");             // 车牌号 (支持车规软键盘录入)
                def.put("bind_token", UUID.randomUUID().toString().substring(0, 8));
                def.put("cloud_sync_enabled", false);
                saveConfig(context, def);
            } catch (Throwable ignored) {}
            return def;
        }
    }

    /**
     * 获取原车不可变硬件身份指纹 (防 Root、防刷机双清漂移)
     * 优先级:
     * 1. 外部共享存储已持久化烙印 (vehicle_identity.key)
     * 2. 原车出厂 SoC 芯片序列号 (ro.boot.serialno / ro.serialno / Build.SERIAL)
     * 3. 亿咖通专车硬件标识 (ro.boot.ecarx_projectinfo / XDSN)
     * 4. Android 硬件唯一 ID (Settings.Secure.ANDROID_ID)
     */
    public static String getHardwareCarIdentity(Context context) {
        // 1. 检查 /sdcard/GeelyPilot/fuel/vehicle_identity.key 物理持久烙印
        try {
            File keyFile = new File(getFuelDir(), FILE_VEHICLE_KEY);
            if (keyFile.exists() && keyFile.length() > 0) {
                String savedKey = readFileContent(keyFile).trim();
                if (!savedKey.isEmpty() && !isKnownDummyId(savedKey)) {
                    return savedKey;
                }
            }
        } catch (Throwable ignored) {}

        String identity = "";

        // 2. 读取原车出厂硬件芯片序列号 (不受刷系统、改 system 分区影响)
        identity = getSystemProperty("ro.boot.serialno");
        if (identity.isEmpty() || isKnownDummyId(identity)) {
            identity = getSystemProperty("ro.serialno");
        }
        if (identity.isEmpty() || isKnownDummyId(identity)) {
            try {
                if (Build.SERIAL != null && !Build.SERIAL.isEmpty() && !isKnownDummyId(Build.SERIAL)) {
                    identity = Build.SERIAL;
                }
            } catch (Throwable ignored) {}
        }

        // 3. 读取吉利亿咖通车规硬件标识 (优先从 IHUID / DeviceService 中抓取真正唯一的出厂码)
        if (identity.isEmpty() || isKnownDummyId(identity)) {
            String ihuid = getSystemProperty("ro.ecarx.ihuid");
            if (ihuid.isEmpty() || isKnownDummyId(ihuid)) {
                ihuid = getSystemProperty("persist.sys.ecarx.ihuid");
            }
            if (!ihuid.isEmpty() && !isKnownDummyId(ihuid)) {
                identity = "ECARX_" + ihuid;
            }
        }
        if (identity.isEmpty() || isKnownDummyId(identity)) {
            String ecarxInfo = getSystemProperty("ro.boot.ecarx_projectinfo");
            if (!ecarxInfo.isEmpty() && !isKnownDummyId(ecarxInfo)) {
                identity = "ECARX_" + ecarxInfo.replace(" ", "_");
            }
        }

        // 4. 回退至硬件级 ANDROID_ID
        if (identity.isEmpty() || isKnownDummyId(identity)) {
            try {
                if (context != null) {
                    String aid = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                    if (aid != null && !aid.trim().isEmpty() && !isKnownDummyId(aid)) {
                        identity = aid.toUpperCase(Locale.US);
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 5. 兜底随机码 (基于 AndroidId / UUID)
        if (identity.isEmpty() || isKnownDummyId(identity)) {
            identity = "GEELY_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.US);
        }

        // 规范化前缀标识
        String finalId = identity.replaceAll("[^A-Za-z0-9_\\-]", "");
        if (!finalId.startsWith("GEELY_") && !finalId.startsWith("ECARX_")) {
            finalId = "GEELY_" + finalId;
        }

        // 写入外部共享持久文件做物理烙印，即使后续被双清也能跨环境恢复识别
        try {
            File keyFile = new File(getFuelDir(), FILE_VEHICLE_KEY);
            writeFileContent(keyFile, finalId);
        } catch (Throwable ignored) {}

        return finalId;
    }

    /**
     * 判断是否为车规芯片/Android通用占位测试序列号 (如 0123456789ABCDEF、unknown 等)
     */
    private static boolean isKnownDummyId(String val) {
        if (val == null) return true;
        String s = val.trim();
        if (s.isEmpty() || "unknown".equalsIgnoreCase(s) || "null".equalsIgnoreCase(s)) return true;
        String clean = s.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.US);
        return clean.startsWith("0123456789ABCDE") || clean.equals("0123456789") || clean.equals("1234567890");
    }

    /**
     * 安全反射读取系统属性
     */
    private static String getSystemProperty(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method m = sp.getMethod("get", String.class, String.class);
            String val = (String) m.invoke(null, key, "");
            return val != null ? val.trim() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * 读取断电前保存的最后液位快照 (fuel_state.json)
     */
    public static JSONObject getLastFuelState(Context context) {
        synchronized (LOCK) {
            try {
                File stateFile = new File(getFuelDir(), FILE_STATE);
                if (stateFile.exists() && stateFile.length() > 0) {
                    String content = readFileContent(stateFile);
                    return new JSONObject(content);
                }
            } catch (Throwable t) {
                Log.w(TAG, "读取 fuel_state.json 异常: " + t.getMessage());
            }
            return new JSONObject();
        }
    }

    /**
     * 保持更新液位与总里程瞬态状态 (在熄火断电或行车打点时落盘)
     */
    public static void saveLastFuelState(Context context, double oilLiters, double odoKm) {
        synchronized (LOCK) {
            try {
                JSONObject state = new JSONObject();
                state.put("oil", oilLiters);
                state.put("odo", odoKm);
                state.put("timestamp", System.currentTimeMillis());
                File stateFile = new File(getFuelDir(), FILE_STATE);
                writeFileContent(stateFile, state.toString(2));
            } catch (Throwable t) {
                Log.w(TAG, "保存 fuel_state.json 异常: " + t.getMessage());
            }
        }
    }

    /**
     * 主动从 logcat 缓存中极速反查最近一次打印的仪表总里程报文
     */
    public static double fetchLatestOdoFromLogcat() {
        try {
            String out = SystemUtils.executeShell("logcat -d -v time | grep -iE 'INFO_ID_IPKINFO_TOTAL_ODO|0x00100700' | tail -n 5");
            if (out != null && !out.isEmpty()) {
                String[] lines = out.split("\n");
                for (int i = lines.length - 1; i >= 0; i--) {
                    String line = lines[i];
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:INFO_ID_IPKINFO_TOTAL_ODO|0x00100700).*?(?:(?:funValue|funcValue)\\((?:0x)?([0-9a-fA-F]+)\\)|[:=]\\s*([0-9.]+))", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(line);
                    if (m.find()) {
                        String hexStr = m.group(1);
                        String decStr = m.group(2);
                        double odo = 0.0;
                        if (hexStr != null && !hexStr.isEmpty()) {
                            long raw = Long.parseLong(hexStr, 16);
                            odo = raw / 10.0;
                        } else if (decStr != null && !decStr.isEmpty()) {
                            odo = Double.parseDouble(decStr);
                        }
                        if (odo > 0 && odo < 2000000) {
                            return Math.round(odo * 10.0) / 10.0;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "fetchLatestOdoFromLogcat error: " + t.getMessage());
        }
        return -1.0;
    }

    /**
     * 保存燃油配置
     */
    public static boolean saveConfig(Context context, JSONObject config) {
        synchronized (LOCK) {
            try {
                File configFile = new File(getFuelDir(), FILE_CONFIG);
                writeFileContent(configFile, config.toString(2));
                AppLogger.i("行程油耗", "已持久化更新 fuel_config.json 至共享存储");
                return true;
            } catch (Throwable t) {
                Log.e(TAG, "保存 fuel_config.json 失败: " + t.getMessage());
                return false;
            }
        }
    }

    /**
     * 获取所有加油历史记录 (降序排列，最新记录排最前)
     */
    public static JSONArray getRecords(Context context) {
        synchronized (LOCK) {
            File recordsFile = new File(getFuelDir(), FILE_RECORDS);
            if (recordsFile.exists() && recordsFile.length() > 0) {
                try {
                    String content = readFileContent(recordsFile);
                    return new JSONArray(content);
                } catch (Throwable t) {
                    Log.w(TAG, "读取 fuel_records.json 异常: " + t.getMessage());
                }
            }
            return new JSONArray();
        }
    }

    /**
     * 保存所有记录
     */
    private static boolean saveRecordsInternal(JSONArray records) {
        try {
            File recordsFile = new File(getFuelDir(), FILE_RECORDS);
            writeFileContent(recordsFile, records.toString(2));
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "持久化 fuel_records.json 失败: " + t.getMessage());
            return false;
        }
    }

    /**
     * 新增一条加油记录 (自动执行 MAX_LOCAL_RECORDS = 30 轻量化滑动窗口滚动淘汰)
     */
    public static boolean addRecord(Context context, JSONObject record) {
        synchronized (LOCK) {
            try {
                if (!record.has("id") || record.optLong("id", 0) <= 0) {
                    record.put("id", System.currentTimeMillis());
                }
                if (!record.has("date")) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                    record.put("date", sdf.format(new Date()));
                }
                JSONArray current = getRecords(context);
                JSONArray updated = new JSONArray();
                // 新记录插在最前
                updated.put(record);
                for (int i = 0; i < current.length(); i++) {
                    // 车机本地滑动窗口截断淘汰：只保留最新 30 条，多余的最旧记录从车机内存/存储淘汰
                    if (updated.length() >= MAX_LOCAL_RECORDS) {
                        break;
                    }
                    updated.put(current.getJSONObject(i));
                }
                boolean ok = saveRecordsInternal(updated);
                if (ok) {
                    AppLogger.i("行程油耗", "成功记录一笔加油台账: " + record.optDouble("liters") + "L (¥" + record.optDouble("actual_paid") + ")，当前本地保留: " + updated.length() + "/" + MAX_LOCAL_RECORDS);
                }
                return ok;
            } catch (Throwable t) {
                Log.e(TAG, "新增加油台账失败: " + t.getMessage());
                return false;
            }
        }
    }

    /**
     * 更新修改一条加油记录
     */
    public static boolean updateRecord(Context context, JSONObject updatedRecord) {
        synchronized (LOCK) {
            try {
                long targetId = updatedRecord.optLong("id", 0);
                if (targetId <= 0) return false;

                JSONArray current = getRecords(context);
                JSONArray updated = new JSONArray();
                boolean found = false;

                for (int i = 0; i < current.length(); i++) {
                    JSONObject item = current.getJSONObject(i);
                    if (item.optLong("id", 0) == targetId) {
                        updated.put(updatedRecord);
                        found = true;
                    } else {
                        updated.put(item);
                    }
                }
                if (!found) return false;
                boolean ok = saveRecordsInternal(updated);
                if (ok) {
                    AppLogger.i("行程油耗", "成功编辑修改加油台账: ID=" + targetId);
                }
                return ok;
            } catch (Throwable t) {
                Log.e(TAG, "更新加油台账失败: " + t.getMessage());
                return false;
            }
        }
    }

    /**
     * 删除指定 ID 的加油记录
     */
    public static boolean deleteRecord(Context context, long recordId) {
        synchronized (LOCK) {
            try {
                JSONArray current = getRecords(context);
                JSONArray updated = new JSONArray();
                boolean found = false;

                for (int i = 0; i < current.length(); i++) {
                    JSONObject item = current.getJSONObject(i);
                    if (item.optLong("id", 0) == recordId) {
                        found = true;
                    } else {
                        updated.put(item);
                    }
                }
                if (!found) return false;
                boolean ok = saveRecordsInternal(updated);
                if (ok) {
                    AppLogger.i("行程油耗", "已删除加油记录: ID=" + recordId);
                }
                return ok;
            } catch (Throwable t) {
                Log.e(TAG, "删除加油记录失败: " + t.getMessage());
                return false;
            }
        }
    }

    /**
     * 清空全部加油记录
     */
    public static boolean clearRecords(Context context) {
        synchronized (LOCK) {
            boolean ok = saveRecordsInternal(new JSONArray());
            if (ok) {
                AppLogger.i("行程油耗", "已清空所有加油流水台账");
            }
            return ok;
        }
    }

    /**
     * 单次行程出账功能已按车规极简原则废弃：
     * 单次行程（里程、油耗、均速）完全交由原厂液晶仪表原生呈现；
     * 燃油管家聚焦跨周期加注宏观台账，彻底拔除单次频繁刷写 flash 磁盘。
     */
    public static JSONArray getTrips(Context context) {
        return new JSONArray();
    }

    public static JSONObject getLastTrip(Context context) {
        return null;
    }



    /**
     * 小熊油耗跳枪法与阶段油耗算法统计引擎
     */
    public static JSONObject calculateStats(JSONArray records) {
        JSONObject stats = new JSONObject();
        try {
            int len = records.length();
            double totalPaid = 0.0;
            double totalLiters = 0.0;
            int fullCount = 0;

            for (int i = 0; i < len; i++) {
                JSONObject r = records.getJSONObject(i);
                double paid = r.optDouble("actual_paid", r.optDouble("amount", 0.0));
                double l = r.optDouble("liters", 0.0);
                totalPaid += paid;
                totalLiters += l;
                if (r.optBoolean("is_full", false)) {
                    fullCount++;
                }
            }

            stats.put("total_records", len);
            stats.put("total_spend", Math.round(totalPaid * 100.0) / 100.0);
            stats.put("total_liters", Math.round(totalLiters * 10.0) / 10.0);
            stats.put("full_count", fullCount);

            // 小熊油耗跳枪法核算：需按时间升序(旧->新)重排计算
            if (len >= 2 && fullCount >= 2) {
                // 将记录按 odo 排序 (从小到大)
                java.util.List<JSONObject> list = new java.util.ArrayList<>();
                for (int i = 0; i < len; i++) {
                    list.add(records.getJSONObject(i));
                }
                list.sort((a, b) -> Double.compare(a.optDouble("odo", 0.0), b.optDouble("odo", 0.0)));

                double sumWeightedConsumption = 0.0;
                double sumDistance = 0.0;

                // 寻找连续加满或阶段加满区间
                int lastFullIdx = -1;
                for (int i = 0; i < list.size(); i++) {
                    JSONObject r = list.get(i);
                    if (r.optBoolean("is_full", false)) {
                        if (lastFullIdx != -1) {
                            double startOdo = list.get(lastFullIdx).optDouble("odo", 0.0);
                            double endOdo = r.optDouble("odo", 0.0);
                            double dist = endOdo - startOdo;

                            if (dist > 5.0) { // 过滤异常零里程
                                // 统计两区间之间的耗油量 (包含本次加满的加油量)
                                double consumedLiters = 0.0;
                                for (int k = lastFullIdx + 1; k <= i; k++) {
                                    consumedLiters += list.get(k).optDouble("liters", 0.0);
                                }
                                double segConsumption = (consumedLiters / dist) * 100.0;
                                sumWeightedConsumption += segConsumption * dist;
                                sumDistance += dist;
                            }
                        }
                        lastFullIdx = i;
                    }
                }

                if (sumDistance > 10.0) {
                    double avgConsumption = sumWeightedConsumption / sumDistance;
                    stats.put("is_calculable", true);
                    stats.put("avg_consumption", Math.round(avgConsumption * 100.0) / 100.0);
                    stats.put("total_distance", Math.round(sumDistance * 10.0) / 10.0);
                    double costPerKm = (totalPaid / sumDistance);
                    stats.put("cost_per_km", Math.round(costPerKm * 100.0) / 100.0);
                    stats.put("status_desc", "加权综合油耗 (跳枪法)");
                } else {
                    stats.put("is_calculable", false);
                    stats.put("avg_consumption", 0.0);
                    stats.put("cost_per_km", 0.0);
                    stats.put("status_desc", "加满间距过短，继续行驶中");
                }
            } else {
                stats.put("is_calculable", false);
                stats.put("avg_consumption", 0.0);
                stats.put("cost_per_km", 0.0);
                if (fullCount < 1) {
                    stats.put("status_desc", "需至少2次加满自动核算");
                } else {
                    stats.put("status_desc", "需再加满1次即可出账");
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "统计油耗异常: " + t.getMessage());
        }
        return stats;
    }

    /**
     * 按月份聚合统计加油与花费清单 (轻量秒算，车机零负担)
     */
    public static JSONArray calculateMonthlyStats(JSONArray records) {
        JSONArray monthlyList = new JSONArray();
        try {
            int len = records.length();
            if (len == 0) return monthlyList;

            // 使用有序 Map 按月份逆序聚合 (最新月份排在前)
            java.util.Map<String, JSONObject> map = new java.util.LinkedHashMap<>();

            for (int i = 0; i < len; i++) {
                JSONObject r = records.getJSONObject(i);
                String dateStr = r.optString("date", "");
                String monthKey = "近期";
                if (dateStr.length() >= 7) {
                    monthKey = dateStr.substring(0, 7); // "2026-10"
                }

                JSONObject mObj = map.get(monthKey);
                if (mObj == null) {
                    mObj = new JSONObject();
                    mObj.put("month", monthKey);
                    mObj.put("count", 0);
                    mObj.put("total_paid", 0.0);
                    mObj.put("total_liters", 0.0);
                    map.put(monthKey, mObj);
                }

                int c = mObj.optInt("count", 0) + 1;
                double p = mObj.optDouble("total_paid", 0.0) + r.optDouble("actual_paid", r.optDouble("amount", 0.0));
                double l = mObj.optDouble("total_liters", 0.0) + r.optDouble("liters", 0.0);

                mObj.put("count", c);
                mObj.put("total_paid", Math.round(p * 100.0) / 100.0);
                mObj.put("total_liters", Math.round(l * 10.0) / 10.0);
            }

            for (JSONObject m : map.values()) {
                monthlyList.put(m);
            }
        } catch (Throwable t) {
            Log.w(TAG, "聚合月度账单异常: " + t.getMessage());
        }
        return monthlyList;
    }

    /**
     * 组装前台所需完整数据 (包含配置、流水记录列表、统计数据)
     */
    public static String getFullDataJson(Context context) {
        try {
            JSONObject root = new JSONObject();
            JSONObject config = getConfig(context);
            JSONArray records = getRecords(context);
            JSONObject stats = calculateStats(records);

            root.put("config", config);
            root.put("records", records);
            root.put("stats", stats);
            root.put("monthly_stats", calculateMonthlyStats(records));

            double curOdo = app.onepve.geelyconsole.services.VehicleAutomationService.currentTotalOdoKm;
            if (curOdo <= 0) {
                // 主动从车端 logcat 历史缓存中速抓一次最近的真实里程
                curOdo = fetchLatestOdoFromLogcat();
                if (curOdo > 0) {
                    app.onepve.geelyconsole.services.VehicleAutomationService.currentTotalOdoKm = curOdo;
                }
            }
            if (curOdo <= 0) {
                curOdo = getLastFuelState(context).optDouble("odo", 0.0);
            }
            if (curOdo <= 0 && records != null) {
                for (int i = 0; i < records.length(); i++) {
                    JSONObject r = records.optJSONObject(i);
                    if (r != null) {
                        double recOdo = r.optDouble("odo", 0.0);
                        if (recOdo > curOdo) {
                            curOdo = recOdo;
                        }
                    }
                }
            }
            root.put("current_odo", curOdo > 0 ? Math.round(curOdo * 10.0) / 10.0 : 0.0);

            JSONArray trips = getTrips(context);
            root.put("trips", trips);
            root.put("last_trip", trips.length() > 0 ? trips.getJSONObject(0) : JSONObject.NULL);

            return root.toString();
        } catch (Throwable t) {
            Log.e(TAG, "组装燃油管家完整数据失败: " + t.getMessage());
            return "{\"config\":{},\"records\":[],\"stats\":{}}";
        }
    }

    /**
     * 辅助工具: 读取文件内容
     */
    private static String readFileContent(File f) throws Exception {
        FileInputStream fis = new FileInputStream(f);
        BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line).append("\n");
        }
        br.close();
        fis.close();
        return sb.toString().trim();
    }

    /**
     * 辅助工具: 写入文件内容 (覆盖写)
     */
    private static void writeFileContent(File f, String content) throws Exception {
        FileOutputStream fos = new FileOutputStream(f, false);
        OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
        osw.write(content);
        osw.flush();
        fos.getFD().sync();
        osw.close();
        fos.close();
    }
}
