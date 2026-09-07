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
    version_name = os.environ.get("VERSION_NAME", "1.4.2")
    version_code = int(os.environ.get("VERSION_CODE", "7042"))
    today = date.today().isoformat()

    # 如果有精准的 tag_name，以 Tag 中的版本为最高优先级！
    if tag_name:
        clean_tag = tag_name.replace("beta-v", "").replace("v", "").strip()
        if clean_tag:
            version_name = clean_tag
            parts = clean_tag.split(".")
            if len(parts) >= 3:
                try:
                    version_code = 7000 + int(parts[1]) * 10 + int(parts[2])
                except Exception:
                    pass

    is_beta = tag_name.startswith("beta-")
    print(f">> Executing publish metadata: tag={tag_name}, is_beta={is_beta}, ver={version_name}, code={version_code}")

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
                "1. 方向盘方控自适应接管：首次启动自动检测米小江(CarMedia)，已安装默认米小江优先，未安装默认控制台接管\n"
                "2. 按键映射支持自定义打开第三方应用：编号2(滚轮按压)、编号3(静音短按)、编号6(Mode键)均支持自由绑定已安装第三方软件\n"
                "3. 按键默认值精简与规则优化：编号2与编号3默认保持原厂不动，编号3移除无效的音量暂停项，编号6按米小江安装自适应默认项\n"
                "4. 彻底移除 USB 与蓝牙冗余模块：全面交给米小江接管，后台彻底释放 I/O 资源，界面回归纯净克制\n"
                "5. 驾驶模式切换语音错位彻底修复：重构底盘多层协议与 9 位权威常量枚举解耦，彻底拔除运动模式误报智能模式的协议冲突\n"
                "6. 倒车挡 (R 挡) 播报避障防衰减重构：强行提权安全引导通道 (NAVIGATION_GUIDANCE)，追加 +4 级动态增益与 65% 音量兜底"
            ),
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
                "1. 驾驶模式切换语音错位彻底修复：重构底盘多层协议与 9 位权威常量枚举解耦，彻底拔除运动模式误报智能模式的协议冲突\n"
                "2. 倒车挡 (R 挡) 播报避障防衰减重构：强行提权安全引导通道 (NAVIGATION_GUIDANCE)，追加 +4 级动态增益与 65% 音量兜底，雷达压制下依然清晰响亮\n"
                "3. 挡位切换跨挡节流优化：移除 1.5s 跨挡死等拦截，针对 R 挡加入 150ms 避让延时，避开车机 AVM 倒车影像初始静音瞬态\n"
                "4. 赞赏支持弹窗恢复真实官方微信赞赏码：内嵌车规轻量高保真资产，手机扫码秒级识别赞赏支持\n"
                "5. 内测特权身份默认彻底隐藏：出厂状态完全静默不露痕迹，版本号连续点击过程 0 弹窗骚扰，点满 5 次静默开启测试通道\n"
                "6. 车机深度工具箱 ADB 控制台集成【抓 OTA 地址】：二级向导流程询问前置检查，一键调起原厂升级并提取固件下载直链"
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
