package app.onepve.geelyconsole.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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

                    if (root.has("prices")) {
                        JSONObject prices = root.getJSONObject("prices");
                        // 优先尝试读取北京或首个省份作为全国基准参考
                        JSONObject defaultProv = prices.optJSONObject("北京");
                        if (defaultProv == null && prices.keys().hasNext()) {
                            defaultProv = prices.getJSONObject(prices.keys().next());
                        }

                        if (defaultProv != null && defaultProv.has("92h")) {
                            double p92 = defaultProv.getDouble("92h");
                            summary = String.format("92# %.2f元", p92);
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
