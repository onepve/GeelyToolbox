#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
门禁：语音资产升级检测不得占用启动与首次播报时段。

背景（实测教训）：
  过去 VehicleVoicePlayer 构造时就 extractAssetsAsync()，
  等于「点火开机第一刻」就去读 assets、写私有目录（25 个文件 404KB）。
  而刚上车那几十秒正是迎宾播报、首次换挡播报要求「开口即响」的时刻，
  此时抢 CPU 与磁盘 IO，直接把第一声播报拖慢（实测首次 D 挡播报迟 4 秒以上）。

规则：
  1. VehicleVoicePlayer 构造函数内不得出现 extractAssetsAsync / syncBuiltinAssets
     等同步动作（必须交给 onIdleSyncVoice 空闲调度）。
  2. VehicleAutomationService 必须存在空闲调度入口 startIdleVoiceSync()，
     且必须挂载在 onCreate 里。
  3. syncBuiltinAssets 的自动触发必须先过版本戳（code / voice_version）比对，
     未变更时不得遍历文件、不得读内容。
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PLAYER = os.path.join(ROOT, "app/src/main/java/app/onepve/geelyconsole/utils/VehicleVoicePlayer.java")
SERVICE = os.path.join(ROOT, "app/src/main/java/app/onepve/geelyconsole/services/VehicleAutomationService.java")

FAILS = []


def strip_comments(src):
    src = re.sub(r"//[^\n]*", "", src)
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    return src


def check_ctor_not_blocking():
    if not os.path.exists(PLAYER):
        FAILS.append(f"找不到 {PLAYER}")
        return
    src = strip_comments(open(PLAYER, encoding="utf-8").read())

    m = re.search(r"private\s+VehicleVoicePlayer\s*\(\s*Context\s+\w+\s*\)\s*\{(.*?)\n\s*\}", src, re.S)
    if not m:
        FAILS.append("VehicleVoicePlayer 构造方法解析失败，请人工确认")
        return

    body = m.group(1)
    for banned in ("extractAssetsAsync", "syncBuiltinAssets", "forceRestoreFactoryVoice"):
        if banned in body:
            FAILS.append(
                f"VehicleVoicePlayer 构造函数里调用了 {banned}()：\n"
                "  这会在点火开机第一刻就去读 assets / 写私有目录，\n"
                "  与迎宾播报、首次换挡播报抢 CPU 和磁盘，直接拖慢第一声。\n"
                "  改法：构造函数里什么都不做，交给 onIdleSyncVoice() 在空闲时执行。"
            )


def check_idle_scheduler_wired():
    if not os.path.exists(SERVICE):
        FAILS.append(f"找不到 {SERVICE}")
        return
    src = strip_comments(open(SERVICE, encoding="utf-8").read())

    if "startIdleVoiceSync" not in src:
        FAILS.append(
            "VehicleAutomationService 里找不到 startIdleVoiceSync 空闲调度入口：\n"
            "  语音资产核对必须延迟到空闲时机，不能一开机就做。"
        )
        return

    if not re.search(r"private\s+void\s+startIdleVoiceSync\s*\(", src):
        FAILS.append("startIdleVoiceSync 不是无参私有方法，形态被改动，请人工复核")

    if not re.search(r"^\s*startIdleVoiceSync\(\)\s*;", src, re.M):
        FAILS.append("startIdleVoiceSync() 没有被调用，空闲调度实际未挂载")

    # 必须存在开机最小等待时长，防止有人把等待压成 0
    m = re.search(r"IDLE_VOICE_MIN_UPTIME_MS\s*=\s*([0-9_]+)L", src)
    if not m:
        FAILS.append("找不到 IDLE_VOICE_MIN_UPTIME_MS，缺少开机静默期保护")
    else:
        val = int(m.group(1).replace("_", ""))
        if val < 60_000:
            FAILS.append(
                f"IDLE_VOICE_MIN_UPTIME_MS = {val}ms，不足 60 秒：\n"
                "  迎宾与首次换挡播报都发生在上车最初这段时间，静默期太短等于没保护。"
            )


def check_version_shortcircuit():
    src = strip_comments(open(PLAYER, encoding="utf-8").read())
    body = src[src.find("public static int syncBuiltinAssets"):]
    if not body:
        FAILS.append("找不到 syncBuiltinAssets 实现")
        return
    body = body[:4000]

    if "versionChanged" not in body:
        FAILS.append(
            "syncBuiltinAssets 里找不到 versionChanged 版本戳比对：\n"
            "  版本没变时必须 0 毫秒放行，不能每次都遍历文件。"
        )
    else:
        # 必须真的拿 code/lastCode 去比，不能写成恒真，否则等于每次都全量覆盖
        m = re.search(r"versionChanged\s*=\s*([^;]+);", body)
        expr = m.group(1) if m else ""
        if not re.search(r"code\s*!=\s*lastCode|lastCode\s*!=\s*code", expr):
            FAILS.append(
                f"versionChanged 的判据不成立：versionChanged = {expr.strip()}\n"
                "  必须使用 「code != lastCode」 这类真实版本号比对。\n"
                "  写成 true 或写成不含 lastCode 的表达式，等于每次启动都全量覆盖 25 个语音文件。"
            )
        if not re.search(r"targetVoiceVer\s*!=\s*lastVoiceVer|lastVoiceVer\s*!=\s*targetVoiceVer", expr):
            FAILS.append(
                f"versionChanged 少了语音清单版本比对：versionChanged = {expr.strip()}\n"
                "  必须同时比对 voice_version（targetVoiceVer != lastVoiceVer）。"
            )

    if "lastCode" not in body and "PREF_LAST_ASSET_EXTRACTED_CODE" not in body:
        FAILS.append(
            "syncBuiltinAssets 里找不到本地版本号记录（lastCode / PREF_LAST_ASSET_EXTRACTED_CODE）：\n"
            "  每次启动都要重新判定，失去「升级一次覆盖一次」的本地认证。"
        )


def main():
    check_ctor_not_blocking()
    check_idle_scheduler_wired()
    check_version_shortcircuit()

    if FAILS:
        print("[FAIL] 语音资产空闲检测门禁未通过：\n")
        for i, f in enumerate(FAILS, 1):
            print(f"{i}. {f}\n")
        return 1

    print("[PASS] 语音资产核对已延迟到空闲时机，构造期零 IO，版本戳短路生效。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
