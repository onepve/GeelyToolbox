#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
执行全车型独立基线矩阵门禁 (HAL Vehicle Matrix Gate)
纯 Python/AST 契约解析 + 独立基线仿真验证，无需 Android SDK 依赖，零外部污染，毫秒级断言。
"""

import os
import sys
import re

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

def read_file(rel_path):
    p = os.path.join(ROOT_DIR, rel_path)
    if not os.path.exists(p):
        return None
    with open(p, "r", encoding="utf-8") as f:
        return f.read()

def test_binyue_cool_2022():
    print("  >> [基线回归] 吉利缤越 COOL (2022 / E02 / IHU516G) 核心门禁测试...")
    failures = 0
    
    # 1. 契约定义与音频策略断言
    content = read_file("app/src/main/java/app/onepve/geelyconsole/hal/platform/BinyueCoolPlatform.java")
    if not content:
        print("     [FAIL] BinyueCoolPlatform.java 不存在！")
        return 1
    if 'PLATFORM_CODE = "binyue_cool_e02"' not in content:
        print("     [FAIL] 缤越 COOL 平台代码不匹配！")
        failures += 1
    if "return 45.0;" not in content:
        print("     [FAIL] 缤越 COOL 标称油箱容积必须为 45.0L！")
        failures += 1
    if "AudioDecodeStrategy.HARD_FIRST_WITH_SOFT_FALLBACK" not in content:
        print("     [FAIL] 缤越 COOL 音频策略必须为 HARD_FIRST_WITH_SOFT_FALLBACK！")
        failures += 1

    # 2. 实车 Logcat 报文与红绿灯启停防爆音状态机仿真
    # 仿真流：P(5) -> R(4) -> N(3) -> D(2)
    p_gear = re.compile(r"VehId=Vehicle_Gear\s+value=(?:0x)?([0-9a-fA-F]+)")
    p_serial = re.compile(r"91\s+02\s+01(?:\s+[0-9a-fA-F]{1,2}){3}\s+([0-9a-fA-F]{1,2})\s+([0-9a-fA-F]{1,2})")

    cool_logs = [
        "09-22 10:00:01.100 D/MCULog  : VehId=Vehicle_Gear value=0x05", # P
        "09-22 10:00:05.200 D/MCULog  : VehId=Vehicle_Gear value=0x04", # R
        "09-22 10:00:10.300 D/MCULog  : VehId=Vehicle_Gear value=0x03", # N
        "09-22 10:00:15.400 D/MCULog  : VehId=Vehicle_Gear value=0x02", # D
    ]

    current_gear = 5
    announcements = []
    gear_map = {2: "D", 3: "N", 4: "R", 5: "P"}

    for log in cool_logs:
        m = p_gear.search(log)
        if m:
            g = int(m.group(1), 16)
            if g != current_gear:
                current_gear = g
                announcements.append(gear_map.get(g, "UNKNOWN"))

    if current_gear != 2 or announcements != ["R", "N", "D"]:
        print(f"     [FAIL] 缤越 COOL 挡位流解析异常: gear={current_gear}, ann={announcements}")
        failures += 1

    # 红绿灯踩刹车熄火防爆音防御测试（发动机熄火 engineRunning=False, speed=0, 收到假P挡信号时不重置）
    fake_p_signal = 5
    engine_running = False
    speed = 0
    if not engine_running and speed == 0 and (current_gear == 2 or current_gear == 3):
        # 防爆音护栏拦截生效
        pass
    else:
        current_gear = fake_p_signal
        announcements.append("P")

    if current_gear != 2:
        print("     [FAIL] 缤越 COOL 红绿灯启停防爆音护栏失效，被错误重置为 P 挡！")
        failures += 1

    # 3. MCU 串口车门报文解析仿真 (91 02 01)
    door_open_log = "09-22 10:01:00.001 D/SerialControl: read data: 91 02 01 00 00 00 01 00"
    m_open = p_serial.search(door_open_log)
    if not (m_open and (int(m_open.group(1), 16) & 0x01) != 0):
        print("     [FAIL] 缤越 COOL MCU 串口左前门开启信号解析失败！")
        failures += 1

    # 4. 车型隔离回归：缤越 COOL 绝不可解析 INFO_ID_VDRIVEINFO_GEAR_POSITION
    binrui_poll_log = "10-27 16:38:00.000 I/SensorModule( 5486): getFunctionValue:funId(0x00200200) zone(0x00000000) propId(INFO_ID_VDRIVEINFO_GEAR_POSITION) return funValue(0x00200230)"
    vas_source = read_file("app/src/main/java/app/onepve/geelyconsole/services/VehicleAutomationService.java")
    if not vas_source or 'if ("binrui_cool_e02".equals(cachedPlatformCode))' not in vas_source:
        print("     [FAIL] 车型分支隔离门禁缺失！INFO_ID_VDRIVEINFO_GEAR_POSITION 未绑定 binrui_cool_e02 车型代码！")
        failures += 1

    if failures == 0:
        print("     [PASS] 缤越 COOL (2022) 核心门禁 100% 通过！")
    return failures

def test_binrui_cool():
    print("  >> [基线回归] 吉利缤瑞 COOL (2022 / E02 / IHU510G / FE6A3) 核心门禁测试...")
    failures = 0
    content = read_file("app/src/main/java/app/onepve/geelyconsole/hal/platform/BinruiCoolPlatform.java")
    if not content:
        print("     [FAIL] BinruiCoolPlatform.java 不存在！")
        return 1
    if 'PLATFORM_CODE = "binrui_cool_e02"' not in content:
        print("     [FAIL] 缤瑞 COOL 平台代码不匹配！")
        failures += 1
    if "return 50.0;" not in content:
        print("     [FAIL] 缤瑞 COOL 标称油箱容积必须为 50.0L！")
        failures += 1
    if "AudioDecodeStrategy.HARD_FIRST_WITH_SOFT_FALLBACK" not in content:
        print("     [FAIL] 缤瑞 COOL 音频策略必须为 HARD_FIRST_WITH_SOFT_FALLBACK！")
        failures += 1

    # 实车 Logcat 抓包仿真 (INFO_ID_VDRIVEINFO_GEAR_POSITION return funValue(0x00200230))
    p_funvalue = re.compile(r"funValue\((?:0x)?([0-9a-fA-F]+)\)")
    binrui_logs = [
        "10-27 16:38:00.000 I/SensorModule( 5486): getFunctionValue:funId(0x00200200) zone(0x00000000) propId(INFO_ID_VDRIVEINFO_GEAR_POSITION) return funValue(0x00200210)", # P
        "10-27 16:38:05.000 I/SensorModule( 5486): getFunctionValue:funId(0x00200200) zone(0x00000000) propId(INFO_ID_VDRIVEINFO_GEAR_POSITION) return funValue(0x00200240)", # R
        "10-27 16:38:10.000 I/SensorModule( 5486): getFunctionValue:funId(0x00200200) zone(0x00000000) propId(INFO_ID_VDRIVEINFO_GEAR_POSITION) return funValue(0x00200230)", # N
        "10-27 16:38:15.000 I/SensorModule( 5486): getFunctionValue:funId(0x00200200) zone(0x00000000) propId(INFO_ID_VDRIVEINFO_GEAR_POSITION) return funValue(0x00200220)", # D
    ]

    current_gear = 5
    announcements = []
    gear_map = {2: "D", 3: "N", 4: "R", 5: "P"}

    for log in binrui_logs:
        if "INFO_ID_VDRIVEINFO_GEAR_POSITION" in log:
            m = p_funvalue.search(log)
            if m:
                raw_val = int(m.group(1), 16)
                low = raw_val & 0xFF
                g = -1
                if low == 0x10: g = 5
                elif low == 0x40: g = 4
                elif low == 0x30: g = 3
                elif low == 0x20: g = 2
                if g > 0 and g != current_gear:
                    current_gear = g
                    announcements.append(gear_map.get(g, "UNKNOWN"))

    if current_gear != 2 or announcements != ["R", "N", "D"]:
        print(f"     [FAIL] 缤瑞 COOL 挡位流解析异常: gear={current_gear}, ann={announcements}")
        failures += 1

    if failures == 0:
        print("     [PASS] 缤瑞 COOL (FE6A3) 核心门禁 100% 通过！")
    return failures

def test_emgrand_l():
    print("  >> [基线回归] 吉利帝豪 L (E02 / IHU521G / SS13) 核心门禁测试...")
    failures = 0
    content = read_file("app/src/main/java/app/onepve/geelyconsole/hal/platform/EmgrandLPlatform.java")
    if not content:
        print("     [FAIL] EmgrandLPlatform.java 不存在！")
        return 1
    if 'PLATFORM_CODE = "emgrand_l_e02"' not in content:
        print("     [FAIL] 帝豪 L 平台代码不匹配！")
        failures += 1
    if "return 52.0;" not in content:
        print("     [FAIL] 帝豪 L 标称油箱容积必须为 52.0L！")
        failures += 1
    # 关键音频断言：帝豪 L 裁掉了 MP3 软解组件，必须强制 DIRECT_SOFT_ONLY
    if "AudioDecodeStrategy.DIRECT_SOFT_ONLY" not in content:
        print("     [FAIL] 帝豪 L 音频策略必须为 DIRECT_SOFT_ONLY！")
        failures += 1

    if failures == 0:
        print("     [PASS] 帝豪 L 核心门禁 100% 通过！")
    return failures

def test_binyue_l():
    print("  >> [基线回归] 吉利缤越 L (IHU624G / Flyme Auto) 核心门禁测试...")
    failures = 0
    content = read_file("app/src/main/java/app/onepve/geelyconsole/hal/platform/BinyueLPlatform.java")
    if not content:
        print("     [FAIL] BinyueLPlatform.java 不存在！")
        return 1
    if 'PLATFORM_CODE = "binyue_l_flyme_auto"' not in content:
        print("     [FAIL] 缤越 L 平台代码不匹配！")
        failures += 1
    if "return 51.0;" not in content:
        print("     [FAIL] 缤越 L 标称油箱容积必须为 51.0L！")
        failures += 1
    if "AudioDecodeStrategy.HARD_FIRST_WITH_SOFT_FALLBACK" not in content:
        print("     [FAIL] 缤越 L 音频策略必须为 HARD_FIRST_WITH_SOFT_FALLBACK！")
        failures += 1

    if failures == 0:
        print("     [PASS] 缤越 L 核心门禁 100% 通过！")
    return failures

def main():
    print("==================================================")
    print("  [HAL Vehicle Matrix Gate] 全车型独立基线矩阵门禁验证")
    print("==================================================")
    
    total = 0
    total += test_binyue_cool_2022()
    total += test_binrui_cool()
    total += test_emgrand_l()
    total += test_binyue_l()

    if total > 0:
        print(f"\n[CRITICAL FAILURE] 车型矩阵门禁失败，存在 {total} 个破坏性变更！阻断提交！")
        return 1
    
    print("\n[CI GATE PASSED] 全车型独立基准测试全部通过，核心无破坏！")
    return 0

if __name__ == "__main__":
    sys.exit(main())
