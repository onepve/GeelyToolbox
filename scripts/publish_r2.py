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
            "download_url": f"https://dl.onepve.com/GeelyToolbox/GeelyPilot-beta.apk?v={version_name}",
            "changelog": (
                "【测试通道优先体验】\n"
                "1. Tasker实车规则全面对齐：彻底校准DirveMode=2运动/DirveMode=3经济误报Bug，实装getDrivingMode权威挡位\n"
                "2. 根治挡位错位与循环播报：纠正0x14(R挡)误报驻车挡、0x12(D挡)误报空挡Bug，物理挡位精确按低4位解析\n"
                "3. 补齐系统级通道动态补音：彻底打通系统通知声道与音量动态增益调节联动\n"
                "4. 毫秒级熄火感知：纳入onAccOff燃油服务熄火广播，下电复位更加干脆利落\n"
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
            "download_url": f"https://dl.onepve.com/GeelyToolbox/GeelyPilot.apk?v={version_name}",
            "changelog": (
                "本次同步更新：\n"
                "1. 根除VehId=Vehicle_Gear正则错配：精准匹配value数值，彻底消除P挡跳跃为[挡位(10)]的致命死循环\n"
                "2. 驾驶模式160ms防抖保护：彻底解决旋钮极速旋转导致9ms内连续打断音频掐灭声音问题，到位后干脆秒出\n"
                "3. 守护日志与终端双控倒序置顶：最新日志与指令返回结果始终高居最上方第一行，视口平稳舒适0晃眼\n"
                "4. 模块采集开关精简实装：彻底下线死开关HAL探针与系统日志，新增【语音播报】开关(默认开启)，播报透明可查"
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
