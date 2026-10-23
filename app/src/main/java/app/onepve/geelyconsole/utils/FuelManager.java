package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
                    return new JSONObject(content);
                } catch (Throwable t) {
                    Log.w(TAG, "读取 fuel_config.json 异常，生成默认配置: " + t.getMessage());
                }
            }
            // 默认初始配置
            JSONObject def = new JSONObject();
            try {
                def.put("tank_capacity", 45.0);          // 缤越标称 45L
                def.put("default_fuel_type", "92#");     // 默认 92# 汽油
                def.put("default_province", "浙江");     // 默认发改委省份
                def.put("refuel_threshold_l", 4.0);      // 加油检测跃变阈值 (4.0L)
                def.put("car_id", "COOL_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.US));
                def.put("bind_token", UUID.randomUUID().toString().substring(0, 8));
                def.put("cloud_sync_enabled", false);
                saveConfig(context, def);
            } catch (Throwable ignored) {}
            return def;
        }
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
     * 新增一条加油记录
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
                    updated.put(current.getJSONObject(i));
                }
                boolean ok = saveRecordsInternal(updated);
                if (ok) {
                    AppLogger.i("行程油耗", "成功记录一笔加油台账: " + record.optDouble("liters") + "L (¥" + record.optDouble("actual_paid") + ")");
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
