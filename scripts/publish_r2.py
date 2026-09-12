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
    env_version_name = os.environ.get("VERSION_NAME", "").strip()
    env_version_code = os.environ.get("VERSION_CODE", "").strip()
    version_name = env_version_name or "0.0.0"
    version_code = int(env_version_code) if env_version_code.isdigit() else 0
    today = date.today().isoformat()

    # 版本号唯一权威来源：Tag（与 .github/workflows/ci.yml 的注入公式 100% 同源）
    # 公式：(主*10000 + 次*100 + 修订) * 100 + 序号
    #   正式版：vX.Y.Z    → 序号固定 99（如 v1.7.8  → 1070899）
    #   测试版：beta-vX.Y.Z.N → 序号 N=1~98（如 beta-v1.7.8.2 → 1070802）
    # 该公式彻底消除旧公式 7000+次*10+修订 的撞码缺陷（1.7.10 与 1.8.0 曾同为 7080）
    if tag_name:
        is_beta_tag = tag_name.startswith("beta-")
        clean_tag = tag_name[len("beta-v"):] if is_beta_tag else tag_name.lstrip("v").strip()
        parts = clean_tag.split(".")
        if len(parts) >= 3:
            try:
                maj, mi, pa = int(parts[0]), int(parts[1]), int(parts[2])
                rev = int(parts[3]) if (is_beta_tag and len(parts) >= 4) else (99 if not is_beta_tag else None)
                if rev is not None:
                    derived_code = (maj * 10000 + mi * 100 + pa) * 100 + rev
                    derived_name = f"{maj}.{mi}.{pa}-beta.{rev}" if is_beta_tag else f"{maj}.{mi}.{pa}"
                    # 与 CI 注入进 build.gradle 的值交叉校验，两者必须完全一致
                    if env_version_code.isdigit() and int(env_version_code) != derived_code:
                        print(f"!! [FAIL] 版本号契约失衡：CI 注入 versionCode={env_version_code}，"
                              f"按 Tag {tag_name} 推导应为 {derived_code}。拒绝发布以杜绝幻影升级。")
                        sys.exit(1)
                    version_code = derived_code
                    version_name = derived_name
            except Exception as e:
                print(f">> Warn: 版本号推导失败，回退使用环境变量值: {e}")

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
            "download_url": f"https://dl.onepve.com/GeelyToolbox/GeelyToolbox-beta.apk?v={version_name}",
            "changelog": (
                "【测试通道优先体验】\n"
                "1. 闲置自动屏保归位【桌面悬浮】：息屏时长精准收敛至3秒~3分钟，提供5档车规快捷预设与永不进入\n"
                "2. 方控按键全面去十六进制工程化：重构为左/右方向盘+序号直观命名，新增新手功能指引与一键方案配置\n"
                "3. 座舱核心默认值全面优化：转向灯360默认开启、开机自启守护默认开启、悬浮胶囊默认显示电瓶电压、N挡空挡默认关闭\n"
                "4. 守护日志采集策略全面收紧：6大通道默认全关零I/O开销，按需开启彻底根治日志刷屏与性能损耗\n"
                "5. 英文品牌名全链条收敛定案：统一收敛为GeelyToolbox，彻底告别GeelyPilot混淆，安装包与仓库浑然一体\n"
                "6. 智能语音能力指南与避坑说明：新增有人感知状态机与车门自适应场景说明卡片\n"
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
                "本次正式版核心修复与重大重构升级：\n"
                "1. 全新 8 大 4 字极简座舱架构：精选商城排第 1 位，模块职责纯粹清晰，单行防折字更美观\n"
                "2. 转向灯与起步联动 360 盲区神器：转向灯联动 360 全景（车速≤30km/h 安全抑制保护，回正退出，默认开启），支持 D 挡起步联动\n"
                "3. 闲置自动屏保归位【桌面悬浮】：息屏时长精准收敛至 3 秒~3 分钟车规级，提供 5 档快捷预设与永不进入，调起原生原厂屏保\n"
                "4. 方控按键全面去十六进制工程化：重构为左/右方向盘+序号直观命名，新增新手功能指引与一键方案配置（车友黄金方案/一键恢复原厂）\n"
                "5. 座舱核心默认值全面优化：转向灯 360 默认开启、开机自启守护默认开启、悬浮胶囊默认显示实时电瓶电压、N 挡空挡默认关闭\n"
                "6. 守护日志采集策略全面收紧：6 大通道默认全关零 I/O 开销，按需开启彻底根治日志刷屏与性能损耗\n"
                "7. 英文品牌名全链条收敛定案：统一收敛为 GeelyToolbox，安装包、仓库与分发浑然一体，彻底告别混淆\n"
                "8. 点火物理铁壁与换挡武装状态机：发电机发电电压（≥13.2V）权威判定点火，换挡 0ms 强占打断，倒挡回归媒体声道防雷达死锁\n"
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
                "download_url": f"https://dl.onepve.com/GeelyToolbox/GeelyPilot.apk?v={version_name}"
            })
            apps_data["toolbox"] = tb
            with open(apps_json_path, "w", encoding="utf-8") as f:
                json.dump(apps_data, f, ensure_ascii=False, indent=4)
            print(">> /tmp/apps.json updated.")

if __name__ == "__main__":
    main()
