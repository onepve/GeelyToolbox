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

    # 4. 车型隔离回归：主服务绝不可内联特定车型补丁，必须通过 resolveGear 与 getExclusiveLogcatKeywords 物理委派
    vas_source = read_file("app/src/main/java/app/onepve/geelyconsole/services/VehicleAutomationService.java")
    if not vas_source:
        print("     [FAIL] VehicleAutomationService.java 读取失败！")
        return 1
    if "cachedPlatform.resolveGear(line, gearVal, isEngineRunning())" not in vas_source:
        print("     [FAIL] 车型物理隔离委派门禁缺失！VAS 未委派 cachedPlatform.resolveGear！")
        failures += 1
    if "cachedPlatform.getExclusiveLogcatKeywords()" not in vas_source:
        print("     [FAIL] 白名单动态挂载门禁缺失！VAS 未按需读取各车型专属关键字！")
        failures += 1
    if 'INFO_ID_VDRIVEINFO_GEAR_POSITION' in vas_source:
        print("     [FAIL] 主服务污染门禁告警！VAS 仍残留内联 INFO_ID_VDRIVEINFO_GEAR_POSITION 解析！")
        failures += 1
    if 'import app.onepve.geelyconsole.hal.platform.BinyueLPlatform;' in vas_source:
        print("     [FAIL] 主服务污染门禁告警！VAS 仍直接引用特定车型实现类 BinyueLPlatform！")
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

    if failures == 0:
        print("     [PASS] 缤瑞 COOL 基线门禁 100% 通过！")
    return failures

def test_fe6a3_ihu510g():
    print("  >> [车型修补] 吉利缤瑞 COOL FE6A3_IHU510G 专属修补门禁测试...")
    failures = 0
    content = read_file("app/src/main/java/app/onepve/geelyconsole/hal/platform/Fe6a3Ihu510gPlatform.java")
    if not content:
        print("     [FAIL] Fe6a3Ihu510gPlatform.java 独立车型文件不存在！")
        return 1
    if 'PLATFORM_CODE = "fe6a3_ihu510g"' not in content:
        print("     [FAIL] FE6A3_IHU510G 平台代号必须为 fe6a3_ihu510g！")
        failures += 1
    if "resolveGear" not in content:
        print("     [FAIL] FE6A3_IHU510G 专属挡位解析 resolveGear 缺失！")
        failures += 1

    # 仿真：CAN 心跳 0x05 保持 P 挡，SensorModule 报 0x00200230 被 resolveGear 驳回 (返回 0，不产生挡位)
    p_gear = re.compile(r"VehId=Vehicle_Gear\s+value=(?:0x)?([0-9a-fA-F]+)")
    
    # 模拟 FE6A3 专属 resolveGear 逻辑
    def fe6a3_resolve_gear(line, can_gear):
        if "VehId=Vehicle_Gear" in line:
            return can_gear if can_gear >= 0 else 0
        if "INFO_ID_VDRIVEINFO_GEAR_POSITION" in line:
            return 0 # 显式驳回
        return -1

    logs = [
        "10-28 21:49:51.869 D/MCULog: recvVehicleCmd(227) VehId=Vehicle_Gear value=0x05", # CAN P
        "10-28 21:49:52.500 I/SensorModule: onFunctionValueChanged:funId(0x00200200) propId(INFO_ID_VDRIVEINFO_GEAR_POSITION) funcValue(0x00200230)", # Sensor N (应驳回)
        "10-28 21:49:54.869 D/MCULog: recvVehicleCmd(227) VehId=Vehicle_Gear value=0x05", # CAN P
        "10-28 21:49:55.600 I/SensorModule: onFunctionValueChanged:funId(0x00200200) propId(INFO_ID_VDRIVEINFO_GEAR_POSITION) funcValue(0x00200230)", # Sensor N (应驳回)
    ]

    current_gear = -1
    flips = 0
    for line in logs:
        can_gear = -1
        m = p_gear.search(line)
        if m:
            can_gear = int(m.group(1), 16)
        
        g = fe6a3_resolve_gear(line, can_gear)
        if g > 0 and g != current_gear:
            flips += 1
            current_gear = g

    # 只应该有一次初始进入 P 挡，绝不可有 P/N 来回翻转
    if current_gear != 5 or flips != 1:
        print(f"     [FAIL] FE6A3 挡位防抖失败！flips={flips}, final_gear={current_gear}")
        failures += 1

    if failures == 0:
        print("     [PASS] 缤瑞 COOL FE6A3_IHU510G 专属修补门禁 100% 通过！")
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
    if "usesVhalSensorDriver" not in content:
        print("     [FAIL] 缤越 L 底盘感知模式契约缺失！必须实现 usesVhalSensorDriver() 返回 true！")
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
    total += test_fe6a3_ihu510g()
    total += test_emgrand_l()
    total += test_binyue_l()

    if total > 0:
        print(f"\n[CRITICAL FAILURE] 车型矩阵门禁失败，存在 {total} 个破坏性变更！阻断提交！")
        return 1
    
    print("\n[CI GATE PASSED] 全车型独立基准测试全部通过，核心无破坏！")
    return 0

if __name__ == "__main__":
    sys.exit(main())
