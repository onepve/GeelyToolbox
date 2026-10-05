#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
音频物理通道仲裁与防踩踏专项门禁 (Audio Channel Arbitration & Anti-Regression Gate)
专门锁死车载音频跨模块互斥契约：
1. 蓝牙物理连接零延迟预选通 (Zero-Latency Pre-Routing)
2. 微信短语音推流跃变瞬发开闸 (Instant-Unmute Transition Gate)
3. SCO 蓝牙免提通话通道贯通 (SCO Interlock Gate)
4. 车速自启与本地媒体无损隔离 (Non-Destructive Routing Isolation)
5. 门禁变异自检 (Mutation Self-Test)
"""

import os
import re
import sys

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EAS_BRIDGE_PATH = os.path.join(ROOT_DIR, "app/src/main/java/app/onepve/geelyconsole/utils/EasMediaBridge.java")
VAS_PATH = os.path.join(ROOT_DIR, "app/src/main/java/app/onepve/geelyconsole/services/VehicleAutomationService.java")


def load_file(path):
    if not os.path.exists(path):
        raise FileNotFoundError(f"核心文件缺失: {path}")
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def verify_bluetooth_connection_preroute(code):
    """断言 1: 蓝牙连接 state==2 时必须提前选通物理声道与 EAS 注册"""
    conn_block = re.search(
        r'CONNECTION_STATE_CHANGED["\']\s*\.equals\(action\)(.*?)else\s+if\s*\([^)]*AUDIO_STATE_CHANGED',
        code,
        re.DOTALL
    )
    if not conn_block:
        return False, "未能提取 CONNECTION_STATE_CHANGED 处理块"
    block_text = conn_block.group(1)
    
    # state == 2 块内必须包含预选通调用
    if "state == 2" not in block_text:
        return False, "CONNECTION_STATE_CHANGED 缺少 state == 2 判定"
    if "ensureEasReady()" not in block_text:
        return False, "蓝牙连接建立时缺少 ensureEasReady() 提前预注册调用，会导致短语音推流异步等待死锁"
    if "activateBluetoothChannel()" not in block_text:
        return False, "蓝牙连接建立时缺少 activateBluetoothChannel() 提前物理选通调用"
    if "connectBtMediaBrowser()" not in block_text:
        return False, "蓝牙连接建立时缺少 connectBtMediaBrowser() 底层链路连接调用"
    return True, "蓝牙连接零延迟预选通契约锁死验证通过"


def verify_instant_unmute_transition(code):
    """断言 2: 微信短语音推流跃变即刻选通，严禁单靠粗暴节流拦截连续短语音"""
    audio_block = re.search(
        r'AUDIO_STATE_CHANGED["\']\s*\.equals\(action\)(.*?)else\s+if\s*\([^)]*TRACK_EVENT',
        code,
        re.DOTALL
    )
    if not audio_block:
        return False, "未能提取 AUDIO_STATE_CHANGED 处理块"
    block_text = audio_block.group(1)
    
    if "wasStreaming" not in block_text or "!wasStreaming" not in block_text:
        return False, "AUDIO_STATE_CHANGED 缺少 wasStreaming 跃变判定，无法保障微信连续短语音即点即开闸"
    if "activateBluetoothChannel()" not in block_text:
        return False, "AUDIO_STATE_CHANGED 推流时缺少 activateBluetoothChannel() 选通调用"
    
    # 严禁出现无跃变条件下的 > 4000 粗暴防抖
    if re.search(r'if\s*\(\s*now\s*-\s*lastA2dpWakeTime\s*>\s*4000\s*\)', block_text):
        return False, "存在已废弃的 4000ms 粗暴防抖拦截，将导致微信多条连续短语音哑巴"
    return True, "微信短语音推流跃变瞬发开闸契约锁死验证通过"


def verify_sco_interlock(code):
    """断言 3: SCO 蓝牙免提通道建立时必须联动选通声道"""
    sco_block = re.search(
        r'headsetclient\.profile\.action\.AUDIO_STATE_CHANGED["\']\s*\.equals\(action\)(.*?)(?:}\s*;\s*IntentFilter|\Z)',
        code,
        re.DOTALL
    )
    if not sco_block:
        return False, "未能提取 headsetclient AUDIO_STATE_CHANGED 处理块"
    block_text = sco_block.group(1)
    
    if "state == 2" not in block_text:
        return False, "SCO 状态监听缺少 state == 2 (Connected) 判定"
    if "activateBluetoothChannel()" not in block_text:
        return False, "SCO 免提通话建立时未联动 activateBluetoothChannel()，将导致微信通话/听筒模式无声"
    return True, "SCO 蓝牙免提通话通道贯通契约锁死验证通过"


def verify_audio_routing_isolation(eas_code, vas_code):
    """断言 4: 本地媒体与车速自启严禁永久独占或注销蓝牙接收器"""
    if "a2dpReceiver" not in eas_code:
        return False, "EasMediaBridge 丢失 a2dpReceiver 蓝牙推流守护中枢"
    if "unregisterReceiver(a2dpReceiver)" in eas_code:
        return False, "EasMediaBridge 严禁私自注销 a2dpReceiver，必须全生命周期常驻守护"
    
    # 校验车速自启中本地媒体通道调度不侵入破坏 EAS 全局桥接
    if "switchSourceTypeManually" in vas_code:
        if "EasMediaBridge.getInstance" not in vas_code:
            return False, "VehicleAutomationService 未通过规范的 EasMediaBridge 单一真源进行通道切换"
    return True, "本地媒体与蓝牙通道互斥解耦保护验证通过"


def run_mutation_self_test(code):
    """断言 5: 变异反例自检：人为引入缺陷必须被门禁 100% 捕获并阻断"""
    # 反例 1: 移除 ensureEasReady
    bad_code_1 = code.replace("ensureEasReady();", "// mutated")
    ok, _ = verify_bluetooth_connection_preroute(bad_code_1)
    if ok:
        return False, "变异反例自检失败: 移除 ensureEasReady() 未被断言拦截！"

    # 反例 2: 恢复旧版 4000ms 粗暴节流
    bad_code_2 = re.sub(r'if\s*\(!wasStreaming[^)]*\)', 'if (now - lastA2dpWakeTime > 4000)', code)
    ok, _ = verify_instant_unmute_transition(bad_code_2)
    if ok:
        return False, "变异反例自检失败: 恢复 4000ms 粗暴节流未被断言拦截！"

    # 反例 3: 移除 SCO 联动
    bad_code_3 = code.replace("activateBluetoothChannel();", "// mutated")
    ok, _ = verify_sco_interlock(bad_code_3)
    if ok:
        return False, "变异反例自检失败: 移除 SCO activateBluetoothChannel() 未被断言拦截！"

    return True, "门禁变异反例自检 (Mutation Self-Test) 全部通过"


def main():
    print("=================================================================")
    print(">> 音频物理通道仲裁与防踩踏专项门禁 (Audio Channel Arbitration Gate)")
    print("=================================================================")
    eas_code = load_file(EAS_BRIDGE_PATH)
    vas_code = load_file(VAS_PATH)

    tests = [
        ("断言 1: 蓝牙连接零延迟预选通", lambda: verify_bluetooth_connection_preroute(eas_code)),
        ("断言 2: 微信短语音跃变瞬发开闸", lambda: verify_instant_unmute_transition(eas_code)),
        ("断言 3: SCO 蓝牙免提通道贯通", lambda: verify_sco_interlock(eas_code)),
        ("断言 4: 本地媒体与蓝牙解耦隔离", lambda: verify_audio_routing_isolation(eas_code, vas_code)),
        ("断言 5: 门禁变异反例自检", lambda: run_mutation_self_test(eas_code)),
    ]

    failed = False
    for desc, test_fn in tests:
        ok, msg = test_fn()
        if not ok:
            print(f"[FAIL] ❌ {desc}: {msg}")
            failed = True
        else:
            print(f"[PASS] ✅ {desc}: {msg}")

    if failed:
        print("\n!! [BLOCKED] 音频通道仲裁门禁失败，存在跨模块硬件踩踏或微信语音无声风险，发布阻断！")
        sys.exit(1)

    print("\n[SUCCESS] 全部 5 项音频物理通道仲裁与防踩踏契约均 100% 锁死！")


if __name__ == "__main__":
    main()
