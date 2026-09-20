package app.onepve.geelyconsole.model;

public class AppItem {
    public String id;
    public String name;
    public String packageName;
    public String versionName;
    public long versionCode;
    public String filename;
    public String sizeStr;
    public long bytes;
    public String md5;
    public String sha256;
    public String downloadUrl;
    public String description;
    public String badgeText;
    public String updatedAt;
    public boolean needSteeringHelper; // 是否需要方控助手
    public boolean needFreezeOriginal;
    public boolean isSystemCore;
    public boolean needThemeInstall;  // 是否需要卡主题安装（地图类）

    public AppItem() {
    }

    public AppItem(String id, String name, String packageName, String versionName, String sizeStr, String downloadUrl, String description, String badgeText, boolean needSteeringHelper) {
        this.id = id;
        this.name = name;
        this.packageName = packageName;
        this.versionName = versionName;
        this.sizeStr = sizeStr;
        this.downloadUrl = downloadUrl;
        this.description = description;
        this.badgeText = badgeText;
        this.needSteeringHelper = needSteeringHelper;
    }
}
