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
            "version": version_name,
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
                "1. 车身全域物理信号探针大扩充：探针不仅展示四门与电动尾门，新增实时挡位(D/R/P/N/S)与实时驾驶模式(舒适/运动/经济/智能)大卡片动态监听\n"
                "2. 驾驶模式按键映射彻底校准：纠正MCU底层SwitchMode对应关系(0=经济, 1=舒适, 2=运动, 6=智能)，彻底解决拨到运动提示经济、拨到智能提示运动的错位\n"
                "3. 换挡与模式激活逻辑彻底智能化：去除盲目isArmed拦截与开门/关门配置双Key不兼容bug，无论新安装还是热启动均可即时秒出声\n"
                "4. 倒车挡音量深度补偿：音频增益物理提纯+6dB，直出倒车安全提醒，彻底战胜车机倒车影像与雷达压音"
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
