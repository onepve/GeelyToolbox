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
                "1. 彻底根治错位与弹窗未居中：升级绝对视口渲染，彻底解决低版本内核 flex gap 塌陷与 inset-0 黑背景问题\n"
                "2. 实装【播报音量动态增益补偿滑条】：解决听歌时媒体音量低听不清语音的痛点，支持 -10~+15 格动态增补，播完秒恢复\n"
                "3. 统一规范 4 大标准挡位 (D/R/P/N) 与 4 大功能模式 (智能/舒适/经济/运动) 全量播报与独立开关\n"
                "4. 实装专属【⚙️ 声效个性化设置】二级弹窗：每个动作均支持自定义 TTS 台词朗读、自定义本地音频文件与一键恢复出厂默认\n"
                "5. 优化【有人感知状态机】与毫秒级抢占打断：挂挡快切随换随播不吞音，新挡位毫秒级切入打断旧声音\n"
                "6. 强化设置持久化记忆：所有车规开关升级为 commit 同步强刷磁盘，彻底杜绝冷重启掉电丢失配置\n"
                "7. 专车协议与车型流光微胶囊：顶栏常驻点亮缤越 COOL 指定专用协议，并支持多机型平滑切换\n"
                "8. 方向盘方控移除冗余编号 2，底层实装编号 3 自动解静音补偿，按键不再误掐断音乐\n"
                "9. 顶栏精简优化：日夜模式升级为太阳/月亮纯 SVG 图标，移除重复快传按钮，ADB 移入系统底层维护形成 6 大黄金卡片\n"
                "10. 修复 USB 闪存盘双轨硬件接入监听，右上角退出按钮升级为内核级杀进程彻底退出"
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
