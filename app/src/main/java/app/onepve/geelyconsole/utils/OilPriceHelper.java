package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 车规油价同步与解析辅助类 (OilPriceHelper)
 * 直连 R2 (dl.onepve.com/GeelyToolbox/oil-price.json)，支持本地持久化与卡片组件动态读取。
 */
public class OilPriceHelper {

    private static final String TAG = "OilPriceHelper";
    private static final String PREFS_NAME = "oil_price_cache";
    private static final String KEY_OIL_SUMMARY = "key_oil_summary";
    private static final String KEY_LAST_SYNC = "key_last_sync_timestamp";

    public interface OnOilPriceSyncedListener {
        void onSynced(String summary);
    }

    /**
     * 获取用户收藏或选中的基准省份
     */
    public static String getFavoriteProvince(Context context) {
        if (context == null) return "北京";
        try {
            SharedPreferences prefs = context.getSharedPreferences("toolbox_settings", Context.MODE_PRIVATE);
            String favsJson = prefs.getString("geely_oil_fav_provinces", null);
            if (favsJson == null) {
                prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
                favsJson = prefs.getString("geely_oil_fav_provinces", null);
            }
            if (favsJson != null && !favsJson.trim().isEmpty()) {
                JSONArray arr = new JSONArray(favsJson);
                if (arr.length() > 0) {
                    String fav = arr.optString(0);
                    if (fav != null && !fav.trim().isEmpty()) {
                        return fav;
                    }
                }
            }
            return prefs.getString("geely_oil_selected_province", "北京");
        } catch (Exception ignored) {
            return "北京";
        }
    }

    /**
     * 获取指定标号（92#/95#/98#/0#）当前收藏省份的油价单价
     */
    public static double getFavoriteOilPrice(Context context, String fuelType) {
        if (context == null) return 7.55;
        try {
            SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String rawJson = sp.getString("key_oil_raw_json", null);
            if (rawJson == null) return 7.55;

            JSONObject root = new JSONObject(rawJson);
            JSONObject regional = root.optJSONObject("regionalPrices");
            if (regional == null) regional = root.optJSONObject("prices");
            if (regional == null) return 7.55;

            String favProv = getFavoriteProvince(context);
            JSONObject pObj = regional.optJSONObject(favProv);
            if (pObj == null) pObj = regional.optJSONObject("北京");
            if (pObj == null && regional.keys().hasNext()) {
                pObj = regional.getJSONObject(regional.keys().next());
            }

            if (pObj != null) {
                if (fuelType != null && fuelType.contains("95")) {
                    return pObj.optDouble("p95", pObj.optDouble("95h", 8.05));
                } else if (fuelType != null && fuelType.contains("98")) {
                    return pObj.optDouble("p98", pObj.optDouble("98h", 9.25));
                } else if (fuelType != null && (fuelType.contains("0#") || fuelType.contains("柴"))) {
                    return pObj.optDouble("p0", pObj.optDouble("0h", 7.20));
                } else {
                    return pObj.optDouble("p92", pObj.optDouble("92h", 7.55));
                }
            }
        } catch (Exception ignored) {}
        return 7.55;
    }

    /**
     * 获取缓存的最新油价摘要 (格式: "92# 7.82元 (-0.12)")
     */
    public static String getCachedOilSummary(Context context) {
        if (context == null) return "今日油价关注中";
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_OIL_SUMMARY, "92# 油价同步中");
    }

    /**
     * 异步拉取最新油价并更新本地缓存
     */
    public static void fetchOilPricesAsync(final Context context, final OnOilPriceSyncedListener listener) {
        if (context == null) return;
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://dl.onepve.com/GeelyToolbox/oil-price.json?t=" + System.currentTimeMillis());
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "GeelyToolbox/1.0");

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject root = new JSONObject(sb.toString());
                    String summary = "92# 油价关注中";

                    JSONObject regional = root.optJSONObject("regionalPrices");
                    if (regional == null) regional = root.optJSONObject("prices");

                    if (regional != null) {
                        String favProv = getFavoriteProvince(context);
                        JSONObject defaultProv = regional.optJSONObject(favProv);
                        if (defaultProv == null) defaultProv = regional.optJSONObject("北京");
                        if (defaultProv == null && regional.keys().hasNext()) {
                            defaultProv = regional.getJSONObject(regional.keys().next());
                        }

                        if (defaultProv != null) {
                            double p92 = defaultProv.optDouble("p92", defaultProv.optDouble("92h", 0.0));
                            if (p92 > 0) {
                                summary = String.format("92# %.2f元", p92);
                            }
                        }
                    }

                    if (root.has("next_adjustment")) {
                        JSONObject nextAdj = root.getJSONObject("next_adjustment");
                        if (nextAdj.has("predicted_change")) {
                            double change = nextAdj.optDouble("predicted_change", 0.0);
                            if (Math.abs(change) >= 0.01) {
                                String trend = change > 0 ? "📈" : "📉";
                                summary += String.format(" (%+.2f %s)", change, trend);
                            }
                        }
                    }

                    SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    sp.edit()
                            .putString(KEY_OIL_SUMMARY, summary)
                            .putString("key_oil_raw_json", sb.toString())
                            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                            .apply();

                    final String finalSummary = summary;
                    if (listener != null) {
                        new Handler(Looper.getMainLooper()).post(() -> listener.onSynced(finalSummary));
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to sync oil price: " + e.getMessage());
            } finally {
                if (conn != null) {
                    try {
                        conn.disconnect();
                    } catch (Exception ignored) {}
                }
            }
        }).start();
    }
}
