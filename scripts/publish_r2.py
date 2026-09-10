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
                "【重要 · 本版必读】本次已更换为吉利原厂官方签名，安装方式与以往不同：\n"
                "① 先点【立即下载升级】，把新版安装包下载到车机 Download 目录\n"
                "② 再到系统「设置 ➔ 应用」中卸载旧版工具箱（旧签名的包无法直接覆盖安装，会提示“应用未安装”）\n"
                "③ 打开车机「文件管理 ➔ Download」，安装 GeelyToolbox_v1.7.7.apk 即可\n"
                "迁移一次到位后，往后所有新版本都能在工具箱内一键自动升级，不必再卸载。\n"
                "\n"
                "本次同步更新：\n"
                "1. 方控按键多手势全面实装：单击 / 双击 / 长按自由映射，长按时长 0.8~6 秒无级可调\n"
                "2. 车身语音新增有人感知状态机：上下车、开关车门自适应播报，防抖合并不重复\n"
                "3. 倒车音量补偿与方控接管参数全部滑块化，支持即时试听\n"
                "4. 原生 HAL 档位直通实装：换挡播报直接走原厂 CAN 总线，D / R / P / N 识别更稳更准\n"
                "5. 软件中心恢复【暂停 / 取消下载 / 重试】车规大按键\n"
                "6. 专家模式三步确认加倒计时，防误触更安心\n"
                "7. 修复熄火后蓝牙循环播报、QQ 音乐切歌、倒车声道均衡，以及悬浮胶囊顶掉原厂左侧菜单抽屉等问题\n"
                "8. 守护日志昼夜高对比护眼配色，全站提示胶囊统一风格"
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
