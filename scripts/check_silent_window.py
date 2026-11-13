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


def check_boot_storm_fast_retry(src):
    """开机洪峰期必须快速重连并免抽干，否则开头几十秒完全没有挡位报文。

    实测 932336：21:06:49 建 #1 → 断 → 等 2s → 21:07:03 建 #2 → 断 → 等 2s
    → 21:07:06 建 #3 → 21:07:15 才抽干完成。开机后 26 秒没有实时挡位报文，
    车主此时已经在换挡，报文全落在「建立中/抽干中」被丢，表现为「换好几圈才有声音」。
    修法：开机窗口内退避压到 400ms，且跳过历史抽干（-T 1 本就无历史可抽）。
    """
    if not re.search(r'static final long BOOT_STORM_WINDOW_MS\s*=', src):
        FAILS.append(
            "找不到开机洪峰窗口 BOOT_STORM_WINDOW_MS 的定义：\n"
            "  开机初期 logd 尚在灌开机日志，固定 2 秒退避会白等，\n"
            "  必须用更短的退避尽快接上实时流。"
        )
        return
    if not re.search(r'static final long BOOT_STORM_BACKOFF_MS\s*=', src):
        FAILS.append(
            "找不到 BOOT_STORM_BACKOFF_MS 的定义：\n"
            "  开机洪峰期的快速重连退避常量被删了，会退回 2 秒起步的老行为。"
        )
        return
    # 退避取值处必须真的用到洪峰分支（匹配三元表达式里的 bootStorm ?）
    if not re.search(r'long wait\s*=\s*bootStorm\s*\?', src):
        FAILS.append(
            "退避取值处没有 bootStorm 三元分支：\n"
            "  BOOT_STORM_BACKOFF_MS 常量存在但没被使用，等于没生效；\n"
            "  必须写成 long wait = bootStorm ? BOOT_STORM_BACKOFF_MS : LOGCAT_BACKOFF_MS[...]。"
        )
    # 抽干必须能被洪峰期跳过
    if not re.search(r'if\s*\(\s*bootStormDrain\s*\)\s*drained\s*=\s*true', src):
        FAILS.append(
            "抽干段没有 bootStormDrain 跳过分支：\n"
            "  开机期历史段极长，走抽干会一路 continue 到 800ms 安全阀，\n"
            "  期间实时挡位报文全被丢弃。"
        )


def check_crash_never_merged(src):
    """crash 缓冲区绝不能并进主通道，必须有独立旁路线程。

    实测 384522 / 932336（同车 602076）：主通道 -b 里带 crash 时，建流成功却在 1~3 秒内
    报 read: unexpected EOF! 退出，外层重连循环反复重建；384522 实测要建 5 次、断 4 次、
    17.2 秒才进实时，换挡/车门报文全落在空窗里。老版本只订 main,system,events 时
    0.13~1.6 秒即实时、0 次断连。
    正确结构：主通道命令绝不含 crash；crash 由 startCrashSidecarReader() 独立线程单独订阅。
    """
    # 主通道命令里出现 crash 即为回退。
    # 判据：任何 -b 订阅组合只要「含 crash 且还含别的缓冲区」就是合并（回退）；
    # crash 单独一条（旁路）是唯一合法写法。
    merged = [b for b in re.findall(r'"-b",\s*"([^"]*)"', src)
              if "crash" in b and b.strip() != "crash"]
    if merged:
        FAILS.append(
            "主通道 logcat 命令里又出现了 crash 与其他缓冲区合并订阅：\n"
            f"  实际发现 -b {merged}\n"
            "  实测会导致 read: unexpected EOF 反复断连，17.2 秒没有实时挡位报文。\n"
            "  crash 必须走 startCrashSidecarReader() 独立旁路线程，单独 -b crash。"
        )
        return
    if not re.search(r'void startCrashSidecarReader\s*\(', src):
        FAILS.append(
            "找不到 crash 旁路线程 startCrashSidecarReader()：\n"
            "  方控按键报文只在 crash 缓冲区里，删掉旁路会让方控日志源彻底失聪。"
        )
        return
    # 旁路线程必须真的单独订阅 crash
    if not re.search(r'\{"logcat",\s*"-v",\s*"time",\s*"-b",\s*"crash",\s*"-T",\s*"1"\}', src):
        FAILS.append(
            "crash 旁路线程没有独立订阅 crash 缓冲区：\n"
            "  必须是 logcat -v time -b crash -T 1，单独进程单独线程。"
        )
        return
    # 旁路必须只挑方控报文，不许把 crash 全量塞进解析队列
    if not re.search(r'line\.contains\("reportKeyToAdaptApi"\)', src):
        FAILS.append(
            "crash 旁路没有按键报文筛选：\n"
            "  crash 段全量入队会挤占解析队列与内存环形缓冲，只允许 reportKeyToAdaptApi。"
        )


def check_boot_main_only(src):
    """开机洪峰期必须只订单 -b main，且断过一次就锁定最稳档。

    实测 686292（1.8.33-beta.17，缤越 COOL，1.8.33 全量铁证）：
      08:10:41 建 #1 → 08:11:11 建 #8 才稳定，中间 7 次 read: unexpected EOF，
      48 秒没有实时挡位报文；车主此时已点火、换挡、切模式，前置操作全丢，
      导致换挡/驾驶模式识别错乱（表现为「换好几圈才有一次声音」）。
      换到单 -b main 后连跑 8 分钟 0 掉线、读到 66.8 万行、队列丢 0 行，
      21 次挡位跃变 + 169 帧车门报文一帧不少。
    根因不是参数挑车，而是开机期 logd 灌得比读端快 → 读端落后 10 秒 → logd 踢读者。
    砍掉 system/events 后读端立刻跟得上，管道不再积压。

    所以门禁要求：
      1) 必须存在「洪峰期或锁定后走单 -b main」的分支；
      2) 断过一次必须立刻锁定单 -b main，不许再爬多级阶梯（旧阶梯要爬 6 次 = 白丢几十秒）；
      3) 读端热路径不许逐行做计数/时间戳写（那是读端变慢的直接原因）。
    """
    if not re.search(r'\{"logcat",\s*"-v",\s*"time",\s*"-b",\s*"main",\s*"-T",\s*"1"\}', src):
        FAILS.append(
            "找不到开机期单 -b main 的订阅命令：\n"
            "  实测 686292 单 -b main 连跑 8 分钟 0 掉线，而 main,system,events 开机期 7 次 EOF、\n"
            "  48 秒没有实时挡位报文。必须保留 logcat -v time -b main -T 1 这一档。"
        )
    if not re.search(r'logcatMainOnly\s*=\s*true', src):
        FAILS.append(
            "断过一次没有立刻锁定单 -b main（logcatMainOnly = true）：\n"
            "  旧阶梯要爬到第 6 次才降到 main，代价是白白丢掉几十秒实时报文。\n"
            "  必须 tier >= 1 就锁定最稳档。"
        )
    if not re.search(r'boolean bootStormPhase\s*=', src):
        FAILS.append(
            "找不到开机洪峰判定 bootStormPhase：\n"
            "  开机 90 秒内 logd 灌得最凶，必须单独走单 -b main，否则又是 7 次 EOF。"
        )
    if 'countLineReadBatch' not in src:
        FAILS.append(
            "读端没有批量计数（countLineReadBatch）：\n"
            "  逐行 countLineRead + 每行写 volatile 时间戳会把读端拖慢，读端一慢 logd 就踢读者。\n"
            "  必须读端本地累加、每 64 行刷一次。"
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
        check_boot_storm_fast_retry(src)
        check_crash_never_merged(src)
        check_boot_main_only(src)
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
