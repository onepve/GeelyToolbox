import os
import sys
import json
import hashlib
from datetime import date

def get_apk_meta(apk_path):
    with open(apk_path, 'rb') as f:
        data = f.read()
    md5 = hashlib.md5(data).hexdigest()
    sha256 = hashlib.sha256(data).hexdigest()
    bytes_len = len(data)
    size_mb = f"{bytes_len / (1024 * 1024):.1f} MB"
    return md5, sha256, bytes_len, size_mb

def main():
    apk_path = "output/GeelyConsole.apk"
    if not os.path.exists(apk_path):
        print(f"Error: {apk_path} not found")
        sys.exit(1)

    md5, sha256, bytes_len, size_mb = get_apk_meta(apk_path)
    tag_name = os.environ.get("TAG_NAME", "")
    version_name = os.environ.get("VERSION_NAME", "1.4.0")
    version_code = int(os.environ.get("VERSION_CODE", "7040"))
    today = date.today().isoformat()

    is_beta = tag_name.startswith("beta-")
    print(f">> Executing publish metadata: tag={tag_name}, is_beta={is_beta}, ver={version_name}")

    if is_beta:
        meta = {
            "version": f"{version_name}-beta",
            "version_code": version_code,
            "is_beta": True,
            "min_sdk": 28,
            "target_sdk": 28,
            "package_name": "app.onepve.geelyconsole",
            "size": size_mb,
            "bytes": bytes_len,
            "md5": md5,
            "sha256": sha256,
            "download_url": f"https://dl.onepve.com/GeelyToolbox/GeelyPilot-beta.apk?v={version_name}",
            "changelog": "【测试通道优先体验】\n1. 自动同步内测修复与前沿特性实验\n2. 仅面向已激活测试身份的车友推送，正式车友不受任何影响",
            "release_date": today
        }
        with open("/tmp/version-beta.json", "w", encoding="utf-8") as f:
            json.dump(meta, f, ensure_ascii=False, indent=2)
        print(">> /tmp/version-beta.json generated.")
    else:
        meta = {
            "version": version_name,
            "version_code": version_code,
            "is_beta": False,
            "min_sdk": 28,
            "target_sdk": 28,
            "package_name": "app.onepve.geelyconsole",
            "size": size_mb,
            "bytes": bytes_len,
            "md5": md5,
            "sha256": sha256,
            "download_url": f"https://dl.onepve.com/GeelyToolbox/GeelyToolbox.apk?v={version_name}",
            "changelog": (
                "1. 落地【双轨发布体系】：全面支持【测试通道 (Beta)】与【正式通道 (Release)】，设置关于中支持连击 5 次版本号解锁内测特权，查看车机硬件唯一识别码 (UID)，随心切换双轨通道\n"
                "2. 新增【不再提醒】版本更新静默控制：更新弹窗提供不再提醒按钮，设置中提供【开机与前台自动检测更新】开关与恢复检测\n"
                "3. 落地【有人感知状态机】：默认P挡与智能模式静默，手动换出P挡/切出智能模式置1激活全量播报，换回P挡/智能模式播报一次后置0归位，彻底消除蓝牙靠近与开机唤醒误报连报\n"
                "4. 彻底根治布局错位：移除 flex gap，采用 18px 显式实体 margin-bottom 与 line-height 约束，真车 160DPI 零重叠零遮挡\n"
                "5. 顶栏实装 PPanel 同款 12V 蓄电池电压流光微胶囊 (>=11.8V绿/11.5~11.8V橙/<11.5V红)，直出电压与健康状态\n"
                "6. 实装【专家模式】卡主题安装向导独立组件 (RabbitInstallModal.vue)，向导流程闭环\n"
                "7. 优化车规触控比例：按键高度收敛为 80px 黄金大磁贴，紧凑饱满单行防折字\n"
                "8. 精简应用商城列表：彻底移除外部卡片多余的“立即下载”按钮，收敛至详情大弹窗内\n"
                "9. 拔除冗余软重启按钮，收敛单一权威【整车完整硬件冷重启 (reboot)】\n"
                "10. 修复 U 盘插入自动识别监听默认关闭 Bug，双轨支持标准媒体挂载与物理 USB 设备接入播报"
            ),
            "release_date": today
        }
        with open("/tmp/version.json", "w", encoding="utf-8") as f:
            json.dump(meta, f, ensure_ascii=False, indent=2)
        print(">> /tmp/version.json generated.")

        # 更新 apps.json toolbox 字段
        apps_json_path = "/tmp/apps.json"
        if os.path.exists(apps_json_path):
            with open(apps_json_path, "r", encoding="utf-8") as f:
                apps_data = json.load(f)
            apps_data["version"] = version_name
            apps_data["updated_at"] = today
            tb = apps_data.get("toolbox", {})
            tb.update({
                "version": version_name,
                "version_code": version_code,
                "md5": md5,
                "sha256": sha256,
                "bytes": bytes_len,
                "size": size_mb,
                "download_url": f"https://dl.onepve.com/GeelyToolbox/GeelyToolbox.apk?v={version_name}"
            })
            apps_data["toolbox"] = tb
            with open(apps_json_path, "w", encoding="utf-8") as f:
                json.dump(apps_data, f, ensure_ascii=False, indent=4)
            print(">> /tmp/apps.json updated.")

if __name__ == "__main__":
    main()
