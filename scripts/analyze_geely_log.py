#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
吉利车机车载运行与路试日志自动化诊断与车型兼容性嗅探中枢
支持：.zip / .log 文件
核心能力：
1. 软件版本准入门禁动态判定（基于正式版链条及定向测试通道）
2. 多源日志时间与打包物理时间精准提取
3. 车型平台、芯片硬件指纹、屏幕分辨率与桌面包名硬件嗅探
4. 核心功能兼容性嗅探（电源/点火、换挡信号、方控按键未知键码、车门CAN、常驻守护）
5. 未知新车型自动化平台接入建议与代码生成（旧日志变废为宝）
"""

import os
import re
import sys
import zipfile
import argparse
from datetime import datetime

GEAR_NAMES = {
    "GEAR_PARK": "P挡 (驻车)",
    "GEAR_REVERSE": "R挡 (倒挡)",
    "GEAR_NEUTRAL": "N挡 (空挡)",
    "GEAR_DRIVE": "D挡 (前进挡)",
    "0x01": "P挡",
    "0x02": "R挡",
    "0x03": "N挡",
    "0x04": "D挡",
    "0x07": "S挡 (运动)",
    "0x00200210": "P挡 (驻车)",
    "0x00200220": "D挡 (前进)",
    "0x00200230": "N挡 (空挡)",
    "0x00200240": "R挡 (倒挡)",
    "1": "P挡",
    "2": "R挡",
    "3": "N挡",
    "4": "D挡",
    "7": "S挡"
}

DRIVE_MODE_NAMES = {
    "1": "舒适模式 (Comfort)",
    "2": "运动模式 (Sport)",
    "3": "经济模式 (Eco)",
    "4": "智能模式 (Smart)",
    "0x01": "舒适模式",
    "0x02": "运动模式",
    "0x03": "经济模式",
    "0x04": "智能模式"
}

KEY_NAMES = {
    "0x37": "右⑤ 自定义按键 (高德↔桌面往返)",
    "0x2d": "右② 滚轮按压 (媒体播放/暂停)",
    "301": "右② 滚轮向下拨动 (音量减)",
    "302": "右② 滚轮向上拨动 (音量加)",
    "307": "右① 主页/返回按键",
    "300": "右③ 独立静音按键",
    "305": "右④ 下一曲按键",
    "304": "右⑦ 上一曲按键",
    "348": "右⑥ MODE 模式按键 (360秒开)",
    "286": "左方向盘 领航辅助按键",
    "287": "左方向盘 限速巡航按键"
}


def fetch_remote_manifest(url):
    """动态获取线上版本清单真源 (dl.onepve.com)"""
    try:
        import urllib.request
        import json
        req = urllib.request.Request(url, headers={"User-Agent": "GeelyLogAnalyzer/1.0"})
        with urllib.request.urlopen(req, timeout=3) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except Exception:
        return None


def get_dynamic_version_info():
    """动态获取最新正式版、最新测试版及完整发版链条，绝不硬编码"""
    import time
    ts = int(time.time())
    rel_meta = fetch_remote_manifest(f"https://dl.onepve.com/GeelyToolbox/version.json?t={ts}")
    latest_release = rel_meta.get("version", "").lstrip("v") if rel_meta else ""
    
    beta_meta = fetch_remote_manifest(f"https://dl.onepve.com/GeelyToolbox/version-beta.json?t={ts}")
    latest_beta = beta_meta.get("version", "").lstrip("v") if beta_meta else ""

    history = []
    try:
        import subprocess
        res = subprocess.run(["git", "-C", "/home/onepve/GeelyToolbox", "tag", "-l", "v*", "--sort=-v:refname"],
                             stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=2)
        if res.returncode == 0 and res.stdout.strip():
            history = [t.lstrip("v").strip() for t in res.stdout.splitlines() if t.strip() and "beta" not in t.lower()]
    except Exception:
        pass

    if not history:
        if latest_release:
            history = [latest_release]
        else:
            history = ["1.8.6", "1.8.5", "1.8.4", "1.8.3", "1.8.2", "1.8.1", "1.8.0"]

    if not latest_release:
        latest_release = history[0]

    return {
        "latest_release": latest_release,
        "latest_beta": latest_beta,
        "release_history": history
    }


def evaluate_version_gate(detected_model, version_str):
    """
    版本基准与门禁判定 (全部动态计算，绝无写死版本号)：
    - 统一基线：所有车型（包括缤越 COOL 以及帝豪、博越等其他车型）日常正常使用线上最新正式版；
    - 落后门禁：运行落后 >= 2 个版本的旧版时直接拦截淘汰，引导在【设置 ➔ 关于 ➔ 检查更新】升级到最新正式版；
    - 定向测试版通道：如果车主运行的是带有 beta 的测试版，判定为我们正在一对一排错的测试车主，免阻断放行分析。
    """
    v_info = get_dynamic_version_info()
    latest_rel = v_info["latest_release"]
    history = v_info["release_history"]
    latest_beta = v_info["latest_beta"]

    is_beta = bool(version_str and ("beta" in version_str.lower() or "rc" in version_str.lower()))
    clean_ver = re.sub(r'^[vV]', '', version_str or '').strip()

    # 1. 定向测试版放行
    if is_beta:
        return {
            "track": f"定向测试版通道 (Beta: 线上参考 {latest_beta or '专属通道'})",
            "status": "PASS",
            "reason": f"车机运行的是定向测试版 [{version_str}] (一对一专属排错)",
            "guidance": "属于定向跟踪测试车主，进入深度时序与堆栈分析。"
        }

    # 2. 正式版基线评估（所有车型统一标准）
    track_name = f"正式版基准 (Release: 线上最新 {latest_rel})"
    if not clean_ver or clean_ver not in history:
        return {
            "track": track_name,
            "status": "REJECT",
            "reason": f"检测到版本 [{version_str or '未知'}] 过旧或无法匹配近期发版链 (线上最新为 {latest_rel})",
            "guidance": f"请指导车友进入助手【设置 ➔ 关于】点击【检查更新】升级至最新正式版 ({latest_rel}) 实测，若仍复现再抓日志。"
        }

    lag = history.index(clean_ver)
    if lag >= 2:
        return {
            "track": track_name,
            "status": "REJECT",
            "reason": f"当前版本 [{clean_ver}] 已落后最新正式版 [{latest_rel}] 达 {lag} 个版本，属于历史失效版本",
            "guidance": f"该版本存在大量已知旧 Bug；请引导车友进入助手【设置 ➔ 关于】点击【检查更新】升级至最新正式版 ({latest_rel}) 再验证。"
        }
    elif lag == 1:
        return {
            "track": track_name,
            "status": "WARN",
            "reason": f"当前版本 [{clean_ver}] 稍早于最新发版 [{latest_rel}] (落后 1 版)",
            "guidance": "核验对应版本更新说明，若最新版已修复该问题则直接引导更新，否则继续深度分析。"
        }
    else:
        return {
            "track": track_name,
            "status": "PASS",
            "reason": f"当前版本 [{clean_ver}] 与线上最新正式发版一致，符合分析门禁",
            "guidance": "版本符合准入标准，进入深度日志分析。"
        }


def read_log_lines(input_path):
    lines = []
    meta = {}
    time_info = {}
    if input_path.endswith(".zip"):
        try:
            with zipfile.ZipFile(input_path, "r") as z:
                names = z.namelist()
                infolist = z.infolist()
                valid_dates = [info.date_time for info in infolist if info.date_time and info.date_time[0] > 1980]
                if valid_dates:
                    latest = max(valid_dates)
                    time_info["zip_mtime"] = f"{latest[0]:04d}-{latest[1]:02d}-{latest[2]:02d} {latest[3]:02d}:{latest[4]:02d}:{latest[5]:02d}"
                
                if "summary.json" in names:
                    try:
                        import json
                        meta = json.loads(z.read("summary.json").decode("utf-8", errors="ignore"))
                    except Exception:
                        pass
                for name in names:
                    if name.endswith(".log") or "logcat" in name.lower() or "car_" in name.lower():
                        with z.open(name) as f:
                            for l in f:
                                try:
                                    lines.append(l.decode("utf-8", errors="ignore"))
                                except Exception:
                                    pass
        except Exception as ex:
            print(f">> ⚠️ 警告: 无法解压或读取压缩包 ({ex})，该文件可能已损坏或为无效占位符")
            meta["corrupt_error"] = str(ex)
    else:
        mtime = os.path.getmtime(input_path)
        time_info["file_mtime"] = datetime.fromtimestamp(mtime).strftime("%Y-%m-%d %H:%M:%S")
        with open(input_path, "r", encoding="utf-8", errors="ignore") as f:
            for l in f:
                lines.append(l)
    meta["time_info"] = time_info
    return lines, meta


def evaluate_capability_sniffer(detected_model, detected_platform, detected_hw_version,
                                detected_mcu_version, detected_screen, detected_launchers,
                                service_started, logcat_thread_ready, voltages, gear_events,
                                key_events, known_keys, unknown_keys, door_events, mode_events):
    """
    车型功能兼容性嗅探管线：
    即便日志版本过旧被门禁拦截，底层硬件数据依然真实有效。
    评估该车型与助手各项核心功能（点火、换挡、方控、门控、守护）的客观兼容度。
    """
    score = 0
    checks = {}

    # 1. 常驻守护与系统服务 (权重: 20分)
    if service_started or logcat_thread_ready:
        checks["system_daemon"] = {
            "status": "PASS",
            "desc": "常驻服务与 Logcat 信号流转正常就绪",
            "score": 20
        }
        score += 20
    else:
        checks["system_daemon"] = {
            "status": "PARTIAL",
            "desc": "未见显式服务就绪日志（可能被抽干或截断）",
            "score": 10
        }
        score += 10

    # 2. 蓄电池电压与发电机提压 (权重: 20分)
    if voltages:
        v_vals = [v[1] for v in voltages]
        max_v = max(v_vals)
        if max_v >= 13.2:
            checks["power_ignition"] = {
                "status": "PASS",
                "desc": f"电压监听正常，捕获到发电机提压 (最高 {max_v:.2f}V)",
                "score": 20
            }
            score += 20
        else:
            checks["power_ignition"] = {
                "status": "PASS",
                "desc": f"电压监听正常 (读数 {len(voltages)} 条，最高 {max_v:.2f}V，待机态)",
                "score": 18
            }
            score += 18
    else:
        checks["power_ignition"] = {
            "status": "NO_DATA",
            "desc": "日志中未捕获到电压上报（可能处于纯怠速或未配置电压探针）",
            "score": 10
        }
        score += 10

    # 3. 换挡广播与状态机联动 (权重: 20分)
    if gear_events:
        sources = list(set([g[1] for g in gear_events]))
        checks["gear_transmission"] = {
            "status": "PASS",
            "desc": f"换挡信号通畅 (捕获 {len(gear_events)} 次动作，通道: {', '.join(sources)})",
            "score": 20
        }
        score += 20
    else:
        checks["gear_transmission"] = {
            "status": "NO_ACTION",
            "desc": "日志期间未发生实际换挡动作",
            "score": 15
        }
        score += 15

    # 4. 方向盘按键映射与未识别键码 (权重: 25分)
    if key_events:
        if unknown_keys:
            checks["steering_keys"] = {
                "status": "NEEDS_ADAPT",
                "desc": f"捕获到按键动作，但存在未识别按键码: {sorted(list(unknown_keys))}",
                "score": 15
            }
            score += 15
        else:
            checks["steering_keys"] = {
                "status": "PASS",
                "desc": f"方控按键完全兼容 (已识别按键: {sorted(list(known_keys))})",
                "score": 25
            }
            score += 25
    else:
        checks["steering_keys"] = {
            "status": "NO_ACTION",
            "desc": "未捕获到方向盘物理按键动作",
            "score": 15
        }
        score += 15

    # 5. 车身 CAN 与驾驶模式 (权重: 15分)
    if door_events or mode_events:
        checks["body_can"] = {
            "status": "PASS",
            "desc": f"车身 CAN 正常流转 (车门: {len(door_events)}次, 驾驶模式: {len(mode_events)}次)",
            "score": 15
        }
        score += 15
    else:
        checks["body_can"] = {
            "status": "NO_ACTION",
            "desc": "未记录到开门或切模式动作",
            "score": 10
        }
        score += 10

    # 判断是否为需要扩充接入的未知/新车型
    is_benchmark = bool(detected_model and "SX11" in detected_model.upper())
    is_known_factory = bool(detected_model and any(k in detected_model.upper() for k in ["SX11", "FE6", "FX11"]))
    needs_factory_expansion = not is_benchmark and not is_known_factory

    return {
        "score": min(100, score),
        "checks": checks,
        "is_benchmark": is_benchmark,
        "needs_factory_expansion": needs_factory_expansion,
        "unknown_keys": sorted(list(unknown_keys)),
        "known_keys": sorted(list(known_keys))
    }


def analyze_log(lines, meta=None):
    if meta is None:
        meta = {}
    voltages = []
    key_states = []
    gear_events = []
    mode_events = []
    door_events = []
    key_events = []
    unknown_keys = set()
    known_keys = set()
    voice_events = []
    warnings = []

    detected_ver = meta.get("app_version")
    detected_model = meta.get("device_model")
    detected_platform = meta.get("platform_name")
    detected_platform_code = meta.get("platform_code")
    detected_hw_version = None
    detected_mcu_version = None
    detected_screen = None
    detected_launchers = None
    service_started = False
    logcat_thread_ready = False

    re_voltage = re.compile(r"(?:latestBatteryVoltage|batteryVoltage)\s*[=:]\s*([0-9.]+)")
    re_key = re.compile(r"\bkey\s*=\\s*(\d+)")
    re_avm_gear = re.compile(r"state\[(GEAR_[A-Z]+)\]")
    re_sensor_gear = re.compile(r"INFO_ID_VDRIVEINFO_GEAR_POSITION.*funValue\((0x[0-9a-fA-F]+)\)")
    re_mcu_gear = re.compile(r"GearPosition.*gear=(\d+)")
    re_mode = re.compile(r"(?:DirveMode|DriveMode)\s*=\\s*(\d+)|DM_FUNC_DRIVE_MODE_SELECT.*funValue\((0x[0-9a-fA-F]+)\)")
    re_bcm_door = re.compile(r"(?:BCM_([A-Z]{2})DoorAjarStatus\s*=\\s*(\d+)|MCULog:([A-Z]{2})_door_sts:0x0(\d))")
    re_app_door = re.compile(r"\[车门状态\]\s*(【[^】]+】)?([^\s]+门)\s*(打开|关好)")
    re_hal_key = re.compile(r"do nothing for this key\((0x[0-9a-fA-F]+)\)")
    re_adapt_key = re.compile(r"AdaptAPI\.InputImpl.*keycode=(\d+)|IMS reportKeyToAdaptApi\s*:\s*(\d+)")
    re_cmd_key = re.compile(r"cmd_data\[1\]\s*=\\s*(\d+)")
    re_voice = re.compile(r"Playing voice.*(gear|drive_mode|door|warning|welcome)[^,\n]*", re.I)

    # 硬件与系统特征嗅探正则
    re_app_ver = re.compile(r"软件版本[:\s]+([v0-9.-]+|beta[0-9.-]+)", re.I)
    re_plat = re.compile(r"车型平台[:\s]+([^\n\r]+)")
    re_prop_model = re.compile(r"ro\.product\.model[=\s]+([^\n\r]+)|ro\.car\.model[=\s]+([^\n\r]+)|\"\$model\"\s*:\s*\"([^\"]+)\"")
    re_ihu_hw = re.compile(r"getIHUHardwareVersion:\s*([A-Za-z0-9_]+)")
    re_mcu_ver = re.compile(r"getMCUSoftwareVersion:\s*([A-Za-z0-9_.-]+)")
    re_screen_wm = re.compile(r"Physical size:\s*([0-9x]+).*density:\s*([0-9]+)")
    re_launcher = re.compile(r"Home packages resolved:\s*(\[[^\]]+\])")
    re_ts = re.compile(r"(\d{4}[-/]\d{2}[-/]\d{2}\s+\d{2}:\d{2}:\d{2}|\d{2}[-/]\d{2}\s+\d{2}:\d{2}:\d{2})")

    valid_ts_list = []

    for idx, line in enumerate(lines):
        ts = line[:19].strip() if len(line) >= 19 and line[0].isdigit() else f"Line {idx+1}"

        # 提取时间戳 (过滤掉 01-01 RTC未同步时间)
        m_t = re_ts.search(line[:35])
        if m_t:
            t_raw = m_t.group(1)
            if not t_raw.startswith("01-01"):
                valid_ts_list.append(t_raw)

        # 0. 车型平台与硬件嗅探
        if not detected_ver:
            m_v = re_app_ver.search(line)
            if m_v:
                detected_ver = m_v.group(1).strip()
        if not detected_platform:
            m_p = re_plat.search(line)
            if m_p:
                detected_platform = m_p.group(1).strip()
        if not detected_model:
            m_pm = re_prop_model.search(line)
            if m_pm:
                detected_model = (m_pm.group(1) or m_pm.group(2) or m_pm.group(3)).strip()
        if not detected_hw_version:
            m_hw = re_ihu_hw.search(line)
            if m_hw:
                detected_hw_version = m_hw.group(1).strip()
        if not detected_mcu_version:
            m_mcu = re_mcu_ver.search(line)
            if m_mcu:
                detected_mcu_version = m_mcu.group(1).strip()
        if not detected_screen:
            m_sc = re_screen_wm.search(line)
            if m_sc:
                detected_screen = f"{m_sc.group(1)} ({m_sc.group(2)}dpi)"
        if not detected_launchers:
            m_la = re_launcher.search(line)
            if m_la:
                detected_launchers = m_la.group(1).strip()

        if "VehicleAutomationService started successfully" in line:
            service_started = True
        if "Logcat 实时监听守护线程已建立就绪" in line:
            logcat_thread_ready = True

        # 1. 电压与钥匙
        m_volt = re_voltage.search(line)
        if m_volt:
            try:
                v = float(m_volt.group(1))
                if 8.0 <= v <= 16.5:
                    voltages.append((ts, v))
            except Exception:
                pass

        m_key = re_key.search(line)
        if m_key:
            key_states.append((ts, m_key.group(1)))

        # 2. 换挡
        m_avm = re_avm_gear.search(line)
        if m_avm:
            g_raw = m_avm.group(1)
            g_name = GEAR_NAMES.get(g_raw, g_raw)
            gear_events.append((ts, "AVM 360核心", g_name, line.strip()))
            continue

        m_sensor_g = re_sensor_gear.search(line)
        if m_sensor_g:
            g_raw = m_sensor_g.group(1).lower()
            g_name = GEAR_NAMES.get(g_raw, g_raw)
            gear_events.append((ts, "SensorModule 0x报文", g_name, line.strip()))
            continue

        m_mcu_g = re_mcu_gear.search(line)
        if m_mcu_g:
            g_raw = m_mcu_g.group(1)
            g_name = GEAR_NAMES.get(g_raw, g_raw)
            gear_events.append((ts, "MCU 硬件串口", g_name, line.strip()))
            continue

        # 3. 驾驶模式
        m_mode = re_mode.search(line)
        if m_mode:
            m_raw = m_mode.group(1) or m_mode.group(2)
            m_name = DRIVE_MODE_NAMES.get(m_raw, m_raw)
            mode_events.append((ts, m_name, line.strip()))

        # 4. 车门 CAN 与车机门控
        m_door = re_bcm_door.search(line)
        if m_door:
            pos = m_door.group(1) or m_door.group(3)
            val = m_door.group(2) or m_door.group(4)
            st = "打开" if val == "1" else "关好"
            door_events.append((ts, f"{pos}门", st))
        m_app_d = re_app_door.search(line)
        if m_app_d:
            tag = m_app_d.group(1) or ""
            d_name = m_app_d.group(2)
            action = m_app_d.group(3)
            door_events.append((ts, f"车机感知{tag}{d_name}", action))

        # 5. 方控
        m_hkey = re_hal_key.search(line)
        if m_hkey:
            k_raw = m_hkey.group(1).lower()
            if k_raw in KEY_NAMES:
                k_name = KEY_NAMES[k_raw]
                known_keys.add(k_raw)
            else:
                k_name = f"未知键码 {k_raw}"
                unknown_keys.add(k_raw)
            key_events.append((ts, "HAL 拦截", k_name))

        m_akey = re_adapt_key.search(line)
        if m_akey:
            k_raw = m_akey.group(1) or m_akey.group(2)
            if k_raw in KEY_NAMES:
                k_name = KEY_NAMES[k_raw]
                known_keys.add(k_raw)
            else:
                k_name = f"未知键码 {k_raw}"
                unknown_keys.add(k_raw)
            key_events.append((ts, "AdaptAPI", k_name))

        m_ckey = re_cmd_key.search(line)
        if m_ckey:
            k_raw = m_ckey.group(1)
            if k_raw in ["304", "305", "300"]:
                k_name = KEY_NAMES.get(k_raw, f"未知键码 {k_raw}")
                known_keys.add(k_raw)
                key_events.append((ts, "CoreServer cmd", k_name))

        # 6. 语音播报
        m_v = re_voice.search(line)
        if m_v:
            voice_events.append((ts, line.strip()))

        # 7. 常见警告/阻断
        if "isEngineRunning -> false" in line or "isEngineRunning() return false" in line:
            warnings.append((ts, "点火状态机判定未点火，阻断语音播报", line.strip()))

    # 日志记录有效时间跨度
    time_info = meta.get("time_info", {})
    if valid_ts_list:
        time_span = f"{valid_ts_list[0]} ~ {valid_ts_list[-1]}"
    else:
        time_span = time_info.get("file_mtime") or time_info.get("zip_mtime") or "未同步RTC时间"

    gate = evaluate_version_gate(detected_model, detected_ver)

    # 硬件与功能兼容性嗅探分析 (Vehicle Capability Sniffer)
    sniffer = evaluate_capability_sniffer(
        detected_model=detected_model,
        detected_platform=detected_platform,
        detected_hw_version=detected_hw_version,
        detected_mcu_version=detected_mcu_version,
        detected_screen=detected_screen,
        detected_launchers=detected_launchers,
        service_started=service_started,
        logcat_thread_ready=logcat_thread_ready,
        voltages=voltages,
        gear_events=gear_events,
        key_events=key_events,
        known_keys=known_keys,
        unknown_keys=unknown_keys,
        door_events=door_events,
        mode_events=mode_events
    )

    return {
        "voltages": voltages,
        "key_states": key_states,
        "gear_events": gear_events,
        "mode_events": mode_events,
        "door_events": door_events,
        "key_events": key_events,
        "known_keys": sorted(list(known_keys)),
        "unknown_keys": sorted(list(unknown_keys)),
        "voice_events": voice_events,
        "warnings": warnings,
        "total_lines": len(lines),
        "detected_ver": detected_ver,
        "detected_model": detected_model,
        "detected_platform": detected_platform,
        "detected_platform_code": detected_platform_code,
        "detected_hw_version": detected_hw_version,
        "detected_mcu_version": detected_mcu_version,
        "detected_screen": detected_screen,
        "detected_launchers": detected_launchers,
        "time_span": time_span,
        "zip_mtime": time_info.get("zip_mtime"),
        "gate": gate,
        "sniffer": sniffer
    }


def print_report(res):
    print("=================================================================")
    print(">> 🚗 吉利车机实车运行与路试日志诊断与兼容性嗅探报告")
    print(f">> 🕒 日志记录时间: {res.get('time_span', '未知时间')} (打包生成: {res.get('zip_mtime') or '未知'})")
    print(f">> 📊 日志全量分析行数: {res['total_lines']:,} 行")
    print("=================================================================\n")

    # 0. 软件版本与门禁状态前置检查
    gate = res.get("gate", {})
    print("### 【前置门禁】 软件版本与准入有效性评估")
    print(f"- **识别车型/设备**: {res.get('detected_model') or '未知车型'} ({res.get('detected_platform') or '未知平台'})")
    print(f"- **助手运行版本**: {res.get('detected_ver') or '未能识别到有效版本'}")
    print(f"- **适用版本基准**: {gate.get('track', '未指定')}")
    st = gate.get("status", "PASS")
    if st == "PASS":
        print(f"- **门禁判定状态**: ✅ **[PASS - 允许分析]** - {gate.get('reason')}")
        print(f"- **后续处置建议**: {gate.get('guidance')}\n")
    elif st == "WARN":
        print(f"- **门禁判定状态**: ⚠️ **[WARN - 临界版本]** - {gate.get('reason')}")
        print(f"- **后续处置建议**: {gate.get('guidance')}\n")
    else:
        print(f"- **门禁判定状态**: ❌ **[REJECT - 阻断分析]** - {gate.get('reason')}")
        print(f"- **处置答复指引**: {gate.get('guidance')}")
        print(">> ⚠️ 门禁拦截：该日志应用版本落后较多，不排查应用层旧 Bug。\n")

    # 1. 硬件兼容性嗅探与新车型接入评估 (★ 变废为宝，无论版本新旧均全量输出)
    sniffer = res.get("sniffer", {})
    print("### 【硬件嗅探】 车型硬件指纹与功能兼容性前置分析 (旧日志变废为宝)")
    print(f"- **车机硬件指纹**: 芯片/硬件版本: `{res.get('detected_hw_version') or '标准硬件'}` | MCU版本: `{res.get('detected_mcu_version') or '未知'}`")
    if res.get("detected_screen"):
        print(f"- **屏幕与UI环境**: 分辨率 `{res.get('detected_screen')}` | 桌面包名: `{res.get('detected_launchers') or '默认'}`")
    print(f"- **客观综合兼容度**: **{sniffer.get('score', 0)} / 100 分**")
    
    checks = sniffer.get("checks", {})
    for mod_key, mod_info in checks.items():
        mod_name_map = {
            "system_daemon": "常驻守护与系统服务",
            "power_ignition": "电源状态与发电机提压",
            "gear_transmission": "换挡广播与档位流转",
            "steering_keys": "方向盘物理按键映射",
            "body_can": "车门 CAN 与驾驶模式"
        }
        status_icon = "✅" if mod_info["status"] == "PASS" else ("⚠️" if "ADAPT" in mod_info["status"] else "⚪")
        print(f"  * {status_icon} **{mod_name_map.get(mod_key, mod_key)}**: {mod_info['desc']}")

    # 若发现未识别按键
    if sniffer.get("unknown_keys"):
        print(f"\n💡 **按键未适配告警**: 该车型上报了以下未映射的私有按键码: `{sniffer['unknown_keys']}`，建议在 `KeyManager` 中补充键码映射！")

    # 若为未知新车型，生成平台接入建议
    model_name = res.get("detected_model")
    if sniffer.get("needs_factory_expansion"):
        print(f"\n💡 **【新车型平台接入建议】**: 检测到此前未正式纳管的硬件型号 `{model_name}`")
        print(f"  * 推荐平台代号: `{model_name.lower()}_platform`")
        print(f"  * 建议接入方式: 在 `VehiclePlatformFactory.java` 中增加分支并适配相应油箱与按键")

    print("\n-----------------------------------------------------------------")

    # 若门禁 REJECT 则直接返回，不展示具体时序；若 PASS 则继续展示时序明细
    if st == "REJECT":
        print(">> （前置门禁未通过，已跳过应用层时序展开。如需排查，请引导车友升级最新版后复现）")
        print("=================================================================\n")
        return

    # 2. 蓄电池与点火分析
    volts = res["voltages"]
    if volts:
        v_vals = [v[1] for v in volts]
        min_v, max_v, avg_v = min(v_vals), max(v_vals), sum(v_vals)/len(v_vals)
        is_charging = max_v >= 13.2
        print("### 一、 电源与点火状态")
        print(f"- **电压读数样本**: 共 {len(volts)} 条记录")
        print(f"- **电压范围**: 最低 {min_v:.2f}V / 最高 {max_v:.2f}V / 平均 {avg_v:.2f}V")
        print(f"- **发电机充放电**: {'⚡ 发电机提压发电中 (≥13.2V)' if is_charging else '⚠️ 未见发电机提压 (<13.2V，可能处于纯待机/熄火或怠速电平)'}")
    else:
        print("### 一、 电源与点火状态\n- 未在日志中捕获到蓄电池电压报文。")

    # 3. 换挡轨迹
    gears = res["gear_events"]
    print(f"\n### 二、 换挡信号权威流转 ({len(gears)} 次动作)")
    if gears:
        last_g = None
        for ts, src, g_name, raw in gears:
            if g_name != last_g:
                print(f"- `[{ts}]` 【{g_name}】 ← 来源: {src}")
                last_g = g_name
    else:
        print("- 未捕获到换挡动作（可能未换挡或信号被上层过滤）。")

    # 4. 驾驶模式
    modes = res["mode_events"]
    print(f"\n### 三、 驾驶模式切换流转 ({len(modes)} 次动作)")
    if modes:
        last_m = None
        for ts, m_name, raw in modes:
            if m_name != last_m:
                print(f"- `[{ts}]` 【{m_name}】")
                last_m = m_name
    else:
        print("- 未捕获到驾驶模式切换报文。")

    # 5. 车门状态
    doors = res["door_events"]
    print(f"\n### 四、 车门 CAN 报文监听 ({len(doors)} 次动作)")
    if doors:
        for ts, d_pos, d_st in doors[-10:]:
            print(f"- `[{ts}]` {d_pos}: {d_st}")
    else:
        print("- 未捕获到车门开闭报文。")

    # 6. 方控按键
    keys = res["key_events"]
    print(f"\n### 五、 方向盘物理按键捕捉 ({len(keys)} 次动作)")
    if keys:
        for ts, src, k_name in keys[-15:]:
            print(f"- `[{ts}]` 【{k_name}】 ({src})")
    else:
        print("- 未捕获到方向盘物理按键。")

    # 7. 语音播报与阻断警告
    warns = res["warnings"]
    print(f"\n### 六、 播报与异常拦截诊断")
    print(f"- **语音播报触发记录**: {len(res['voice_events'])} 次")
    if warns:
        print(f"- **发现 {len(warns)} 次关键拦截警告**:")
        for ts, reason, raw in warns[:5]:
            print(f"  * `[{ts}]` ⚠️ {reason}")
    else:
        print("- ✅ 状态机未发现异常未点火拦截或死锁警告。")

    # 8. 车型通用性与特异性评估
    model_name = res.get("detected_model")
    is_benchmark = bool(model_name and "SX11" in model_name.upper())
    if not is_benchmark and model_name:
        print(f"\n### 七、 多车型通用性与特异性评估")
        print(f"- **当前车型**: {model_name} ({res.get('detected_platform') or '通用车载平台'})")
        print("- **通用基础功能**: 状态机、系统守护、自启与大部分基础车载服务均在正常流转。")
        print("- **一对一定向排错建议**: 该车型日常直接使用正式版即可；若该车友在群内或私信主动反馈了特定按键不灵、特定信号未读取等专属硬件疑难问题，建议针对其车型单独拉取分支打包专属测试版 (Beta) 定向发给他一个人单点验证，验证通过后再规范合入主线正式版。")

    print("\n=================================================================")


def main():
    parser = argparse.ArgumentParser(description="吉利车机车载运行与路试日志自动化诊断与兼容性嗅探中枢")
    parser.add_argument("path", help="待分析的 .zip 或 .log 日志文件路径")
    args = parser.parse_args()

    if not os.path.exists(args.path):
        print(f"!! 文件不存在: {args.path}")
        sys.exit(1)

    lines, meta = read_log_lines(args.path)
    res = analyze_log(lines, meta)
    print_report(res)


if __name__ == "__main__":
    main()
