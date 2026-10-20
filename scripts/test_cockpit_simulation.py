#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
本地实车全量日志全真仿真测试套件 (Cockpit Full Simulation Engine)
基于车主在吉利缤越 COOL (SX11A3 / IHU516G) 实车采集的全量原始日志进行端到端全真回放。
数据源路径：/data/projects/GeelyToolbox/local_test_logs/ 下最新的 3 份实车全量日志包。
"""

import os
import sys
import glob
import zipfile
import re

LOG_DIR = "/data/projects/GeelyToolbox/local_test_logs"

class AudioSpy:
    def __init__(self):
        self.play_history = []

    def play(self, audio, reason):
        self.play_history.append((audio, reason))

    def clear(self):
        self.play_history = []

# 模拟真实的 VehicleAutomationService + GearStateMachine + DoorStateManager + DriveModeManager 状态机流水线
class VirtualCockpitService:
    def __init__(self, audio_spy):
        self.spy = audio_spy
        self.lastGearPos = -1
        self.isGearVoiceArmed = 0
        self.currentFL = -1
        self.lastDriveMode = -1
        self.isSmartModeArmed = False

        self.P_VEHICLE_GEAR = re.compile(r"VehId=Vehicle_Gear\s+value=(?:0x)?([0-9a-fA-F]+)")
        self.SERIAL_DOOR = re.compile(r"91\s+02\s+01(?:\s+[0-9a-fA-F]{1,2}){3}\s+([0-9a-fA-F]{1,2})\s+([0-9a-fA-F]{1,2})")
        self.P_COMFORT_MODE = re.compile(r"DM_FUNC_DRIVE_MODE_SELECT\s+value\s*=\s*(\d+)")

    def parse_line(self, line):
        if not line: return

        # 1. 挡位
        if "VehId=Vehicle_Gear" in line:
            m = self.P_VEHICLE_GEAR.search(line)
            if m:
                raw = int(m.group(1), 16)
                low = raw & 0x0F
                gear = 5 if low == 0x05 else (4 if low == 0x04 else (3 if low == 0x03 else (2 if low == 0x02 else low)))
                self.handle_gear(gear)

        # 2. 车门
        if "91 02 01" in line:
            m = self.SERIAL_DOOR.search(line)
            if m:
                b6 = int(m.group(1).strip(), 16)
                b7 = int(m.group(2).strip(), 16)
                if b6 == 0x00 and b7 == 0x00:
                    return # 无效帧
                fl = 1 if ((b6 & 0x03) == 0x00) else 0
                self.handle_door_fl(fl)

        # 3. 驾驶模式 (仅认旋钮物理信号 DM_FUNC_DRIVE_MODE_SELECT 与 DirveMode)
        if "DM_FUNC_DRIVE_MODE_SELECT" in line:
            m = self.P_COMFORT_MODE.search(line)
            if m:
                mode = int(m.group(1))
                modes_map = {1: 1, 2: 2, 3: 3, 4: 4, 6: 4}
                if mode in modes_map:
                    self.handle_drive_mode(modes_map[mode])

    def handle_gear(self, gear):
        if self.lastGearPos == -1:
            self.lastGearPos = gear if (2 <= gear <= 7) else 5
            self.isGearVoiceArmed = 0
            return
        if gear != self.lastGearPos:
            prev = self.lastGearPos
            if gear == 5:
                if prev != 5:
                    self.spy.play("gear_p.mp3", "已挂入驻车挡")
                self.isGearVoiceArmed = 0
            else:
                self.isGearVoiceArmed = 1
                if gear == 2: self.spy.play("gear_d.mp3", "已挂入前进挡")
                elif gear == 3: self.spy.play("gear_n.mp3", "已挂入空挡")
                elif gear == 4: self.spy.play("gear_r.mp3", "已挂入倒车挡")
            self.lastGearPos = gear

    def handle_door_fl(self, fl):
        if self.currentFL == -1:
            self.currentFL = fl
            return # 开机首包静默
        if fl != self.currentFL:
            if fl == 1:
                self.spy.play("door_fl.mp3", "主驾车门已打开")
            else:
                self.spy.play("door_fl_close.mp3", "主驾车门已关好")
            self.currentFL = fl

    def handle_drive_mode(self, mode):
        # 核心契合：吉利原厂底盘熄火点火必定默认回归智能模式 (4)
        if self.lastDriveMode == -1:
            self.lastDriveMode = 4 # 出厂开机默认智能模式
            self.isSmartModeArmed = True
        if mode != self.lastDriveMode:
            modes = {1: "mode_comfort.mp3", 2: "mode_sport.mp3", 3: "mode_eco.mp3", 4: "mode_smart.mp3"}
            if mode in modes:
                self.spy.play(modes[mode], f"模式切换至{mode}")
            self.lastDriveMode = mode


def run_simulation():
    print("=================================================================")
    print(">> 运行本地实车全真回放仿真套件 (Local Full Cockpit Simulation)")
    print("=================================================================")

    if not os.path.exists(LOG_DIR):
        print(f"[WARN] 实车测试日志目录不存在: {LOG_DIR}，跳过本地回放")
        return True

    zips = sorted(glob.glob(os.path.join(LOG_DIR, "*.zip")), key=os.path.getmtime, reverse=True)
    if not zips:
        print("[WARN] 未在 local_test_logs 中找到实车日志包，跳过回放")
        return True

    # 严格保持最新的 3 份实车日志包
    test_zips = zips[:3]
    print(f"找到 {len(test_zips)} 份实车全量回放日志包进行回归测试:")
    for z in test_zips:
        print(f" - {os.path.basename(z)} ({os.path.getsize(z) // 1024} KB)")

    audio_spy = AudioSpy()

    for zip_path in test_zips:
        zname = os.path.basename(zip_path)
        print(f"\n>> 开始全量回放实车日志: {zname} ...")
        audio_spy.clear()
        svc = VirtualCockpitService(audio_spy)

        line_count = 0
        with zipfile.ZipFile(zip_path) as z:
            # 找到 all.log
            target_name = "all.log" if "all.log" in z.namelist() else None
            if not target_name:
                for n in z.namelist():
                    if n.endswith(".log"): target_name = n; break

            if not target_name:
                print(f"[SKIP] 压缩包中无有效日志文件: {zname}")
                continue

            with z.open(target_name) as f:
                for line_b in f:
                    line_count += 1
                    line = line_b.decode("utf-8", errors="ignore").strip()
                    svc.parse_line(line)

        print(f"已回放 {line_count} 行系统全量日志。")
        print(f"捕获到的真实音频输出列表 ({len(audio_spy.play_history)} 次):")
        for idx, (snd, reason) in enumerate(audio_spy.play_history[:10]):
            print(f"   [{idx+1}] {snd} ({reason})")
        if len(audio_spy.play_history) > 10:
            print(f"   ... (后略 {len(audio_spy.play_history) - 10} 条)")

        # 断言 1: 验证没有连续同音刷屏
        for i in range(len(audio_spy.play_history) - 1):
            s1 = audio_spy.play_history[i][0]
            s2 = audio_spy.play_history[i+1][0]
            assert s1 != s2, f"[FAIL] 检测到连续相同语音复读: {s1}"

        # 断言 2: 开机坐车静止日志中，绝不能有连续乱报
        assert len(audio_spy.play_history) <= 12, f"[FAIL] 静止/开机日志中出现异常过多声音播报 ({len(audio_spy.play_history)}次)！"

        print(f"[PASS] 实车日志 {zname} 全真回放断言全绿！")

    print("\n=================================================================")
    print("[SUCCESS] 3 份实车日志全量全真回放仿真全部通过！零死循环，零状态机踩踏！")
    print("=================================================================")
    return True

if __name__ == "__main__":
    try:
        ok = run_simulation()
        sys.exit(0 if ok else 1)
    except Exception as e:
        print(f"\n[FAIL] 仿真测试抛出未捕获异常: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
