#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
门禁：换挡/开门播报的「静默窗口」不得复发。

背景（车主实测 + 日志 525284 取证）：
  车主感受：「以前最多等 8~10 秒，现在要 10、20、30 秒甚至更长」。
  量出来的真因是三条叠在一起，全部在 logcat 链路上：

  1. 退避表 {2000,5000,15000,30000,60000} 逐步翻倍 —— 断得越多等越久。
     第 3 次断空等 15 秒，第 5 次断空等 60 秒，这期间挡位/车门报文全丢。
  2. 重连后「抽干期」4000ms，历史段里实时行全被 continue 丢掉。
  3. 全量环形缓冲写死常开，读线程每行一次原子自增+取模（洪峰 800 行/秒），
     读端跟不上 → logd 丢该读者队列 → 管道没断但信号整段消失。

规则（任一违反即 FAIL）：
  A. 退避表最大值不得超 6000ms（封顶，禁止翻倍到 15/30/60 秒）。
  B. 抽干期不得超 1500ms。
  C. 环形缓冲必须默认关闭（rawRingEnabled = false），且必须有按需开启入口。
  D. 抓日志流程必须调用按需开启入口，否则点抓日志会拿不到按钮之前的历史。
  E. 语音资产升级必须逐文件比对大小后跳过，禁止无条件全量重写。
  F. 假死看门狗不得超 60 秒。
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SERVICE = os.path.join(ROOT, "app/src/main/java/app/onepve/geelyconsole/services/VehicleAutomationService.java")
PLAYER = os.path.join(ROOT, "app/src/main/java/app/onepve/geelyconsole/utils/VehicleVoicePlayer.java")
SYSUTILS = os.path.join(ROOT, "app/src/main/java/app/onepve/geelyconsole/utils/SystemUtils.java")

FAILS = []


def strip_comments(src):
    src = re.sub(r"//[^\n]*", "", src)
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    return src


def read(path):
    if not os.path.exists(path):
        FAILS.append(f"找不到 {path}")
        return ""
    return strip_comments(open(path, encoding="utf-8").read())


def check_backoff_capped(src):
    m = re.search(r"LOGCAT_BACKOFF_MS\s*=\s*\{([^}]*)\}", src)
    if not m:
        FAILS.append("找不到 LOGCAT_BACKOFF_MS 退避表")
        return
    nums = [int(x.replace("L", "").replace("_", "").strip())
            for x in m.group(1).split(",") if x.strip()]
    if not nums:
        FAILS.append("退避表为空")
        return
    if max(nums) > 6000:
        FAILS.append(
            f"退避表最大值 {max(nums)}ms 超过 6000ms 上限：{nums}\n"
            "  翻倍式退避会让第 3 次断空等 15 秒、第 5 次断空等 60 秒，\n"
            "  这段时间挡位与车门报文全丢，车主感受就是「换挡半天没声音」。\n"
            "  必须封顶（当前应为 {2000,2000,3000,4000,5000}）。"
        )


def check_drain_window(src):
    m = re.search(r"drainDeadlineMs\s*=\s*android\.os\.SystemClock\.elapsedRealtime\(\)\s*\+\s*([0-9_]+)L", src)
    if not m:
        FAILS.append("找不到抽干期 drainDeadlineMs 的定义")
        return
    val = int(m.group(1).replace("_", ""))
    if val > 1500:
        FAILS.append(
            f"抽干期 {val}ms 超 1500ms 上限：\n"
            "  抽干期内实时行会被 continue 丢掉，重连后每多 1 秒就少 1 秒挡位信号。\n"
            "  当前应为 800ms。"
        )


def check_raw_ring_default_off(src):
    if not re.search(r"rawRingEnabled\s*=\s*false", src):
        FAILS.append(
            "环形缓冲 rawRingEnabled 没有默认关闭：\n"
            "  常开的话读线程每行都要做原子自增+取模+数组写（洪峰 800 行/秒），\n"
            "  读端跟不上产出速度 → logd 丢弃该读者队列 → 表现为管道没断但信号整段消失。\n"
            "  平时不抓日志这份缓冲根本没人用，必须默认关闭、按需开启。"
        )
    if "enableRawRingAndBackfill" not in src:
        FAILS.append("缺少 enableRawRingAndBackfill 按需开启入口（且需先补建 logd 历史）")


def check_capture_uses_enable(src_sys):
    if not src_sys:
        return
    if "enableRawRingAndBackfill" not in src_sys:
        FAILS.append(
            "抓日志流程没有调用 enableRawRingAndBackfill：\n"
            "  缓冲默认关闭后，若不在这里开启并补建历史，\n"
            "  车主点「抓日志」之前刚做的换挡/开门操作会整段丢失。"
        )


def check_voice_skip_by_length(src_player):
    if not src_player:
        return
    body = src_player
    idx = body.find("public static int syncBuiltinAssets")
    if idx < 0:
        FAILS.append("找不到 syncBuiltinAssets")
        return
    body = body[idx:idx + 6000]

    if "openFd" not in body:
        FAILS.append(
            "syncBuiltinAssets 没有用 openFd().getLength() 取 APK 内原始长度：\n"
            "  没有期望长度就无法判断本地文件是否已是最新。"
        )
    if not re.search(r"dest\.length\(\)\s*==\s*expected", body):
        FAILS.append(
            "syncBuiltinAssets 没有「dest.length() == expected 就跳过」的判断：\n"
            "  这等于版本一变就无条件全量重写 25 个文件 404KB，正是升级后首次启动卡顿的来源。"
        )
    if "skipped" not in body:
        FAILS.append("syncBuiltinAssets 没有统计跳过数量（skipped），无法确认跳过逻辑真的生效")


def check_watchdog(src):
    m = re.search(r"LOGCAT_SILENCE_WATCHDOG_MS\s*=\s*([0-9_]+)L", src)
    if not m:
        FAILS.append("找不到 LOGCAT_SILENCE_WATCHDOG_MS 假死看门狗")
        return
    val = int(m.group(1).replace("_", ""))
    if val > 60000:
        FAILS.append(
            f"假死看门狗 {val}ms 超 60 秒上限：\n"
            "  通道悄悄死掉后要等这么久才发现，期间挡位/车门全丢。\n"
            "  当前应为 20000ms。"
        )


def check_tail_seek_is_one(src):
    """订阅必须用 -T 1，只收实时、不回放历史。

    历史背景：曾用 -T 200，但车机日志量 723 行/秒，200 行只等于 0.28 秒，
    盖不住重连空窗，却让读端一建流就连续消费 200 行；OS 管道 64KB 一满，
    logd 就停止投递并开始丢该读者队列的行，反过来加剧丢报文。
    """
    hits = re.findall(r'"-T",\s*"([0-9]+)"', src)
    if not hits:
        FAILS.append(
            "找不到 logcat 订阅的 -T 参数：\n"
            "  必须显式写 -T 1，只从缓冲区最后一行起跟随。"
        )
        return
    bad = [h for h in hits if h != "1"]
    if bad:
        FAILS.append(
            f"logcat 订阅 -T 值必须为 1，实际发现 {bad}：\n"
            "  -T 200 会先吞 200 行历史，车机 723 行/秒下只够 0.28 秒，\n"
            "  既补不回断连空窗，又让 OS 管道塞满导致 logd 丢本读者的行。"
        )


def main():
    src = read(SERVICE)
    player = read(PLAYER)
    sysutils = read(SYSUTILS)

    if src:
        check_backoff_capped(src)
        check_drain_window(src)
        check_raw_ring_default_off(src)
        check_watchdog(src)
        check_tail_seek_is_one(src)
    check_capture_uses_enable(sysutils)
    check_voice_skip_by_length(player)

    if FAILS:
        print("[FAIL] 静默窗口门禁未通过：\n")
        for i, f in enumerate(FAILS, 1):
            print(f"{i}. {f}\n")
        return 1

    print("[PASS] 退避封顶 5s、抽干 0.8s、-T 1 只收实时、缓冲按需开启、语音按长度跳过，静默窗口最坏 5.8s。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
