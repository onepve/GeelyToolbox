package app.onepve.geelyconsole.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import app.onepve.geelyconsole.MainActivity;
import app.onepve.geelyconsole.R;
import app.onepve.geelyconsole.hal.IVehiclePlatform;
import app.onepve.geelyconsole.hal.VehiclePlatformFactory;
import app.onepve.geelyconsole.utils.SystemUtils;
import app.onepve.geelyconsole.utils.ThemeColorHelper;

/**
 * 缤越助手车机原生桌面卡片 (AppWidgetProvider 3x1)
 *
 * 遵循原车 GalaxyOS / Flyme Auto 车规卡槽交互规范：
 * 1. 纯原生 RemoteViews，零常驻开销，局部按需跃变刷新；
 * 2. 蓄电池电压 + 动力模式 + 五门微缩图示状态；
 * 3. 快捷动作：点击直达 360 AVM、点击复制今日动态工程码唤醒拨号盘、点击标题直达主页。
 */
public class ToolboxWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "ToolboxWidget";

    public static final String ACTION_TRIGGER_360 = "app.onepve.geelyconsole.widget.ACTION_TRIGGER_360";
    public static final String ACTION_OPEN_ENGINEER = "app.onepve.geelyconsole.widget.ACTION_OPEN_ENGINEER";

    // 内存中缓存的最新状态 (车身服务跳变时驱动)
    private static volatile float sLastVolt = 13.1f;
    private static volatile String sLastGear = "P";
    private static volatile boolean sDoorFl = false;
    private static volatile boolean sDoorFr = false;
    private static volatile boolean sDoorRl = false;
    private static volatile boolean sDoorRr = false;
    private static volatile boolean sTrunkOpen = false;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidgetView(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (action == null) return;

        if (ACTION_TRIGGER_360.equals(action)) {
            trigger360Camera(context);
        } else if (ACTION_OPEN_ENGINEER.equals(action)) {
            openEngineerMode(context);
        }
    }

    /**
     * 调起 360 AVM 全景影像
     */
    private void trigger360Camera(Context context) {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage("ecarx.camera.calibration");
            if (intent == null) {
                intent = new Intent(Intent.ACTION_MAIN);
                intent.setComponent(new ComponentName("ecarx.camera.calibration", "ecarx.camera.calibration.MainActivity"));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "Failed to launch 360 camera: " + e.getMessage());
            Toast.makeText(context, "唤起 360 失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 调起工程模式并自动复制今日计算暗号
     */
    private void openEngineerMode(Context context) {
        try {
            String code = SystemUtils.calculateDynamicCode();
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("DynamicCode", code));
            }

            Intent dialIntent = new Intent(Intent.ACTION_DIAL);
            dialIntent.setData(Uri.parse("tel:" + Uri.encode(code)));
            dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(dialIntent);

            Toast.makeText(context, "今日暗号已复制: " + code, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.w(TAG, "Failed to open engineer mode: " + e.getMessage());
            Toast.makeText(context, "调起工程模式失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 车身感知服务调用：更新状态并局部推送桌面卡片
     */
    public static void updateVehicleState(Context context, float volt, String gear,
                                          boolean fl, boolean fr, boolean rl, boolean rr, boolean trunk) {
        if (context == null) return;
        sLastVolt = volt;
        if (gear != null && !gear.isEmpty()) {
            sLastGear = gear;
        }
        sDoorFl = fl;
        sDoorFr = fr;
        sDoorRl = rl;
        sDoorRr = rr;
        sTrunkOpen = trunk;

        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName thisWidget = new ComponentName(context, ToolboxWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(thisWidget);
        if (ids != null && ids.length > 0) {
            for (int id : ids) {
                updateWidgetView(context, manager, id);
            }
        }
    }

    private static void updateWidgetView(Context context, AppWidgetManager manager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_toolbox_3x1);

        // 1. 车型徽章计算 (统一车规标准：[ 车型名称 · 车机芯片 ]，颜色动态接入助手全局主题色)
        IVehiclePlatform platform = VehiclePlatformFactory.getPlatform(context);
        String pCode = platform != null ? platform.getPlatformCode() : "";
        String pName = platform != null ? platform.getPlatformName() : "";
        String badgeText;
        if (pCode.contains("binyue_l") || pName.contains("缤越 L") || pName.contains("缤越L")) {
            badgeText = "[ 缤越L · IHU624G ]";
        } else if (pCode.contains("fe6a3") || pName.contains("缤瑞") || pCode.contains("binrui")) {
            badgeText = "[ 缤瑞COOL · IHU510G ]";
        } else if (pName.contains("帝豪") || pCode.contains("emgrand")) {
            badgeText = "[ 帝豪L · IHU521G ]";
        } else if (pName.contains("COOL") || "binyue_cool_e02".equals(pCode)) {
            badgeText = "[ 缤越COOL · IHU516G ]";
        } else {
            // 预注册车型显示层：未知车机走公开资料车型代号映射（宁缺勿猜，未命中回落通用座舱）
            String modelCode = SystemUtils.getCleanDeviceModel();
            String displayName = VehiclePlatformFactory.resolveModelDisplayName(modelCode);
            badgeText = "未知车型".equals(displayName) ? "[ 车规智能座舱 ]" : "[ " + displayName + " ]";
        }
        views.setTextViewText(R.id.widget_platform_badge, badgeText);
        // 徽章颜色 100% 动态接入助手全局主题色（ThemeColorHelper 同步自前端主题体系）
        views.setTextColor(R.id.widget_platform_badge, ThemeColorHelper.getAccentColor(context));

        // 2. 蓄电池电压与健康
        float volt = sLastVolt > 0 ? sLastVolt : 13.1f;
        views.setTextViewText(R.id.widget_battery_volt, String.format("%.1fV", volt));
        if (volt >= 12.0f) {
            views.setTextColor(R.id.widget_battery_volt, 0xFF38BDF8); // 正常浅天蓝
            views.setTextViewText(R.id.widget_battery_status, "健康良好 · " + sLastGear + "挡");
            views.setTextColor(R.id.widget_battery_status, 0xFF4ADE80);
        } else if (volt >= 11.6f) {
            views.setTextColor(R.id.widget_battery_volt, 0xFFF59E0B); // 警戒琥珀黄
            views.setTextViewText(R.id.widget_battery_status, "电量偏低 · " + sLastGear + "挡");
            views.setTextColor(R.id.widget_battery_status, 0xFFF59E0B);
        } else {
            views.setTextColor(R.id.widget_battery_volt, 0xFFEF4444); // 严重红色
            views.setTextViewText(R.id.widget_battery_status, "亏电预警 · " + sLastGear + "挡");
            views.setTextColor(R.id.widget_battery_status, 0xFFEF4444);
        }

        // 3. 车门状态点示 (红=开, 绿=关)
        views.setImageViewResource(R.id.widget_door_fl, sDoorFl ? R.drawable.widget_door_dot_open : R.drawable.widget_door_dot_closed);
        views.setImageViewResource(R.id.widget_door_fr, sDoorFr ? R.drawable.widget_door_dot_open : R.drawable.widget_door_dot_closed);
        views.setImageViewResource(R.id.widget_door_rl, sDoorRl ? R.drawable.widget_door_dot_open : R.drawable.widget_door_dot_closed);
        views.setImageViewResource(R.id.widget_door_rr, sDoorRr ? R.drawable.widget_door_dot_open : R.drawable.widget_door_dot_closed);
        views.setImageViewResource(R.id.widget_door_trunk, sTrunkOpen ? R.drawable.widget_door_dot_open : R.drawable.widget_door_dot_closed);

        boolean anyDoorOpen = sDoorFl || sDoorFr || sDoorRl || sDoorRr || sTrunkOpen;
        views.setTextViewText(R.id.widget_doors_title, anyDoorOpen ? "门未关严 ⚠" : "全车门锁 正常");
        views.setTextColor(R.id.widget_doors_title, anyDoorOpen ? 0xFFEF4444 : 0xFF94A3B8);

        // 4. 今日工程暗号
        String dynCode = SystemUtils.calculateDynamicCode();
        views.setTextViewText(R.id.widget_btn_engineer, "⚡ 暗号 " + dynCode);

        // 5. 今日油价摘要显示
        String oilSummary = app.onepve.geelyconsole.utils.OilPriceHelper.getCachedOilSummary(context);
        views.setTextViewText(R.id.widget_oil_price, oilSummary);

        // 6. 点击事件绑定
        // 5.1 点击标题打开主程序
        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent mainPi = PendingIntent.getActivity(context, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));
        views.setOnClickPendingIntent(R.id.widget_title, mainPi);
        views.setOnClickPendingIntent(R.id.widget_platform_badge, mainPi);

        // 5.2 点击开启 360
        Intent avmIntent = new Intent(context, ToolboxWidgetProvider.class);
        avmIntent.setAction(ACTION_TRIGGER_360);
        PendingIntent avmPi = PendingIntent.getBroadcast(context, 101, avmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));
        views.setOnClickPendingIntent(R.id.widget_btn_360, avmPi);

        // 5.3 点击今日工程暗号
        Intent engIntent = new Intent(context, ToolboxWidgetProvider.class);
        engIntent.setAction(ACTION_OPEN_ENGINEER);
        PendingIntent engPi = PendingIntent.getBroadcast(context, 102, engIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));
        views.setOnClickPendingIntent(R.id.widget_btn_engineer, engPi);

        manager.updateAppWidget(appWidgetId, views);
    }
}
