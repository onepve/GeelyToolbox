#!/usr/bin/env python3
"""卡片级与按钮级原子契约门禁 (Card & Button Contract Gate)

严格落实车主定案铁律：
1. 每一个功能卡片、每一个按钮对应的底层业务逻辑必须全部纳入独立门禁；
2. 修 bug 时严格禁止连带修改其他功能卡片或按钮的代码；
3. 如果修某个 bug 必须调整其他卡片且被门禁拦截，必须向车主详细说明改动理由与破坏性影响并经明确确认授权后方可按范围放开！
"""

import os
import re
import sys

BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
JAVA_BASE = os.path.join(BASE_DIR, "app/src/main/java/app/onepve/geelyconsole")
ASSETS_BASE = os.path.join(BASE_DIR, "app/src/main/assets")
WEB_BASE = os.path.join(BASE_DIR, "web/src")

def clean_code(source: str) -> str:
    pattern = r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/'
    return re.sub(pattern, lambda m: '' if m.group(0).startswith(('//', '/*')) else m.group(0), source, flags=re.S)

def extract_region(source: str, marker: str, is_vue: bool = False) -> str:
    if marker not in source:
        raise ValueError(f"Marker not found: {marker}")
    if is_vue:
        start = source.index(marker)
        left = max(0, start - 600)
        right = min(len(source), start + 2500)
        return source[left:right]
    start = source.index(marker)
    opening = source.find('{', start)
    if opening == -1:
        return source[start:start+10000]
    depth = 0
    for token in re.finditer(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|[{}]', source[opening:]):
        if token.group(0) == '{':
            depth += 1
        elif token.group(0) == '}':
            depth -= 1
            if depth == 0:
                return source[opening + 1:opening + token.start()]
    return source[opening + 1:opening + 10000]

# 卡片级与按钮级全量原子契约定义
# 格式: (卡片名称, 按钮/功能名称, 文件路径, 函数/代码块Marker, 必须包含正则列表, 严禁包含正则列表)
CARD_BUTTON_CONTRACTS = [
    # ==========================================
    # 1. 【车身联动卡片】(Door & Body Linkage)
    # ==========================================
    (
        "车身联动卡片", "主驾车门开关联动 (door_fl)",
        os.path.join(JAVA_BASE, "utils/DoorStateManager.java"),
        "public synchronized void updateDoors",
        [r'handleDoorTransition\("FL"', r'currentFL'],
        []
    ),
    (
        "车身联动卡片", "副驾车门开关联动 (door_fr)",
        os.path.join(JAVA_BASE, "utils/DoorStateManager.java"),
        "public synchronized void updateDoors",
        [r'handleDoorTransition\("FR"', r'currentFR'],
        []
    ),
    (
        "车身联动卡片", "后排车门开关联动 (door_rl / door_rr)",
        os.path.join(JAVA_BASE, "utils/DoorStateManager.java"),
        "public synchronized void updateDoors",
        [r'handleDoorTransition\("RL"', r'handleDoorTransition\("RR"'],
        []
    ),
    (
        "车身联动卡片", "通用车门与独立分门模式仲裁",
        os.path.join(JAVA_BASE, "utils/DoorStateManager.java"),
        "public synchronized void updateDoors",
        [r"universalMode", r"voice_door_mode_universal"],
        []
    ),

    # ==========================================
    # 2. 【挡位安全卡片】(Gear Safety Management)
    # ==========================================
    (
        "挡位安全卡片", "P挡驻车锁止安全联动 (gear_p)",
        os.path.join(JAVA_BASE, "utils/GearStateMachine.java"),
        "public synchronized void updateGear",
        [r"isGearVoiceArmed", r"5", r"gear_p"],
        []
    ),
    (
        "挡位安全卡片", "R挡倒车优先联动 (gear_r)",
        os.path.join(JAVA_BASE, "utils/GearStateMachine.java"),
        "public synchronized void updateGear",
        [r"4", r"gear_r"],
        []
    ),
    (
        "挡位安全卡片", "D挡前进挡联动 (gear_d)",
        os.path.join(JAVA_BASE, "utils/GearStateMachine.java"),
        "public synchronized void updateGear",
        [r"2", r"gear_d"],
        []
    ),
    (
        "挡位安全卡片", "N挡空挡防溜车联动 (gear_n)",
        os.path.join(JAVA_BASE, "utils/GearStateMachine.java"),
        "public synchronized void updateGear",
        [r"3", r"gear_n"],
        []
    ),
    (
        "挡位安全卡片", "换挡防抖与瞬态切歌掐灭保护",
        os.path.join(JAVA_BASE, "utils/GearStateMachine.java"),
        "public synchronized void updateGear",
        [r"GEAR_DEBOUNCE_MS", r"pendingTargetGear"],
        []
    ),

    # ==========================================
    # 3. 【驾驶模式卡片】(4 大专车驾驶模式)
    # ==========================================
    (
        "驾驶模式卡片", "舒适模式 (Comfort) 独立旋钮响应",
        os.path.join(JAVA_BASE, "utils/DriveModeManager.java"),
        "public synchronized void updateDriveMode",
        [r"MODE_COMFORT", r"mode_comfort"],
        []
    ),
    (
        "驾驶模式卡片", "经济模式 (Eco) 独立旋钮响应",
        os.path.join(JAVA_BASE, "utils/DriveModeManager.java"),
        "public synchronized void updateDriveMode",
        [r"MODE_ECO", r"mode_eco"],
        []
    ),
    (
        "驾驶模式卡片", "运动模式 (Sport) 独立旋钮响应",
        os.path.join(JAVA_BASE, "utils/DriveModeManager.java"),
        "public synchronized void updateDriveMode",
        [r"MODE_SPORT", r"mode_sport"],
        []
    ),
    (
        "驾驶模式卡片", "智能模式 (Smart) 专车模式锁定 (严禁雪地/越野)",
        os.path.join(JAVA_BASE, "utils/DriveModeManager.java"),
        "public synchronized void updateDriveMode",
        [r"MODE_SMART", r"mode_smart"],
        [r"雪地模式", r"越野模式", r"泥地模式", r"沙地模式"]
    ),

    # ==========================================
    # 4. 【多媒体接管卡片】(蓝牙音频 / A2DP)
    # ==========================================
    (
        "多媒体接管卡片", "2号蓝牙物理通道硬选通广播 (SOURCE_TYPE_BLUETOOTH=2)",
        os.path.join(JAVA_BASE, "utils/EasMediaBridge.java"),
        "public synchronized void activateBluetoothChannel",
        [r"SOURCE_TYPE_BLUETOOTH", r"ECARX_KEY_RSRC_EVENT", r"updateCurrentSourceType", r"if\s*\(\s*a2dpStreaming\s*\)\s*\{\s*wakeBluetoothAudioSink"],
        [r"isAutoWakeSuppressed", r"dummyListener"]
    ),
    (
        "多媒体接管卡片", "常驻 MAY_DUCK 蓝牙闪避焦点守护 (经车主授权对齐 1.7.47 正式版，保障多媒体冻结后硬件声卡选通)",
        os.path.join(JAVA_BASE, "utils/EasMediaBridge.java"),
        "public synchronized void requestBluetoothFocusIfNeeded",
        [r"AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK", r"requestAudioFocus"],
        [r"abandonBluetoothFocus"]
    ),
    (
        "多媒体接管卡片", "原厂部件蓝牙声道保持广播 (ECARX_WIDGET_BLUETOOTH_PLAY)",
        os.path.join(JAVA_BASE, "utils/EasMediaBridge.java"),
        "public void keepXcmediaOnBluetoothSource",
        [r"ECARX_WIDGET_BLUETOOTH_PLAY", r"com\.ecarx\.multimedia"],
        []
    ),
    (
        "多媒体接管卡片", "微信语音推流防误杀与停流即时掐灭 (A2DP Streaming 保护与 pauseBluetoothAudioSink)",
        os.path.join(JAVA_BASE, "utils/EasMediaBridge.java"),
        "private void registerA2dpReceiver",
        [r"a2dpStreaming", r"activateBluetoothChannel", r"pauseBluetoothAudioSink", r"connectBtMediaBrowser"],
        []
    ),
    (
        "多媒体接管卡片", "底层 A2dpMediaBrowserService 直连通道锁死",
        os.path.join(JAVA_BASE, "utils/EasMediaBridge.java"),
        "private synchronized void connectBtMediaBrowser",
        [r"A2dpMediaBrowserService", r"com\.android\.bluetooth"],
        []
    ),
    (
        "多媒体接管卡片", "国科 GOC 驱动解静音唤醒锁死 (wakeBluetoothAudioSink)",
        os.path.join(JAVA_BASE, "utils/EasMediaBridge.java"),
        "public void wakeBluetoothAudioSink",
        [r"btMediaController\.getTransportControls\(\)\.play\(\)"],
        []
    ),
    (
        "多媒体接管卡片", "推流停止即时掐灭手机排队播放锁死 (pauseBluetoothAudioSink)",
        os.path.join(JAVA_BASE, "utils/EasMediaBridge.java"),
        "public void pauseBluetoothAudioSink",
        [r"btMediaController\.getTransportControls\(\)\.pause\(\)", r"mc\.getTransportControls\(\)\.pause\(\)"],
        []
    ),
    (
        "多媒体接管卡片", "微信音频平滑压低与自动恢复音量锁死 (duckMediaVolume)",
        os.path.join(JAVA_BASE, "utils/EasMediaBridge.java"),
        "public synchronized void duckMediaVolume",
        [r"setStreamVolume", r"STREAM_MUSIC"],
        []
    ),
    (
        "多媒体接管卡片", "微信语音音量智能动态补偿锁死 (syncVoiceCompensationConfig)",
        os.path.join(JAVA_BASE, "utils/EasMediaBridge.java"),
        "public synchronized void syncVoiceCompensationConfig",
        [r"voiceCompensationEnabled", r"voiceCompensationOffset"],
        []
    ),
    (
        "多媒体接管卡片", "微信播放前静音/暂停状态机守卫锁死 (严禁误自启音乐)",
        os.path.join(JAVA_BASE, "services/VehicleAutomationService.java"),
        "public boolean isAnyMediaPlaying",
        [r"com\.android\.bluetooth", r"STATE_PLAYING"],
        []
    ),

    # ==========================================
    # 5. 【多媒体接管卡片】(音乐拉活与安全续播)
    # ==========================================
    (
        "多媒体接管卡片", "冷启动特权服务拉活 (QQ音乐等底层唤醒)",
        os.path.join(JAVA_BASE, "services/VehicleAutomationService.java"),
        "public void wakeUpTargetMediaService",
        [r"tryStartComponentService", r"MediaBrowserService"],
        []
    ),
    (
        "多媒体接管卡片", "微信语音中断后延时安全续播 (防抢占)",
        os.path.join(JAVA_BASE, "services/VehicleAutomationService.java"),
        "private void triggerMusicAutoplay",
        [r"isInPhoneCall", r"wakeUpTargetMediaService"],
        []
    ),

    # ==========================================
    # 6. 【电源管理卡片】(点火与下电熄火)
    # ==========================================
    (
        "电源管理卡片", "吉利缤越COOL钥匙OFF熄火断电判定 (0x00200103)",
        os.path.join(JAVA_BASE, "services/VehicleAutomationService.java"),
        "private int parsePowerStateLine",
        [r"low == 0x03", r"return 0"],
        []
    ),
    (
        "电源管理卡片", "熄火重置状态机 (换挡与模式归零休眠)",
        os.path.join(JAVA_BASE, "services/VehicleAutomationService.java"),
        "private void handleCanSignal",
        [r"PEPS_PowerMode", r"gearStateMachine\.resetState", r"driveModeManager\.resetState"],
        []
    ),

    # ==========================================
    # 7. 【方向盘自定义卡片】(方控按键)
    # ==========================================
    (
        "方向盘自定义卡片", "360影像压制原厂多媒体 (防声音抢占)",
        os.path.join(JAVA_BASE, "utils/SteeringWheelKeyManager.java"),
        "public void suppressOriginalMultimedia",
        [r"am force-stop com\.ecarx\.multimedia"],
        []
    ),
    (
        "方向盘自定义卡片", "首选蓝牙MediaSession独占直发锁死 (方控切歌/播放直通)",
        os.path.join(JAVA_BASE, "utils/SteeringWheelKeyManager.java"),
        "private void sendMediaKeyEvent(int keyCode, int targetSource)",
        [r"com\.android\.bluetooth", r"dispatchedToBt", r"mc\.getTransportControls\(\)"],
        []
    ),

    # ==========================================
    # 8. 【语音包定制卡片】(Theme & Sound)
    # ==========================================
    (
        "语音包定制卡片", "车规级前置 ≥280ms 静音说明规范",
        os.path.join(ASSETS_BASE, "voice_readme.txt"),
        "【核心升级特性】",
        [r"280", r"静音", r"ffmpeg"],
        []
    ),
    (
        "语音包定制卡片", "中英文命名双向对照指南",
        os.path.join(ASSETS_BASE, "voice_readme.txt"),
        "【核心升级特性】",
        [r"gear_p\.mp3", r"mode_smart\.mp3", r"door_fl\.mp3"],
        []
    ),
    (
        "语音包定制卡片", "底盘模式中文别名探测 (智能模式专车锁定)",
        os.path.join(JAVA_BASE, "utils/VehicleVoicePlayer.java"),
        "private static String[] getChineseAliases",
        [r"drive_mode_smart", r"智能模式"],
        [r"雪地模式"]
    ),

    # ==========================================
    # 9. 【车身联动卡片】(转向灯联动 360 与自适应退出)
    # ==========================================
    (
        "车身联动卡片", "转向灯回正自动退出 360 底层状态机锁死",
        os.path.join(JAVA_BASE, "services/VehicleAutomationService.java"),
        "private void handleTurnSignalState",
        [r"vehicle_turn_signal_360_auto_exit", r"close360Camera"],
        []
    ),
    (
        "车身联动卡片", "转向灯回正按键车规级双行架构锁死 (dual 换行排版)",
        os.path.join(WEB_BASE, "views/LinkView.vue"),
        "toggleTurnSignal360AutoExit",
        [r'variant="dual"', r'回正自动退出', r'回正持续保持'],
        []
    ),

    # ==========================================
    # 10. 【系统底座与体验】(昼夜无感适配与避峰引擎)
    # ==========================================
    (
        "系统底座", "昼夜深色模式平滑切换防重建锁死 (uiMode 无感通知)",
        os.path.join(JAVA_BASE, "MainActivity.java"),
        "public void onConfigurationChanged",
        [r"UI_MODE_NIGHT_MASK", r"onSystemNightModeChanged"],
        []
    ),
    (
        "系统底座", "商城更新后台静默与手动点击彻底隔离 (杜绝静默弹窗打扰)",
        os.path.join(JAVA_BASE, "MainActivity.java"),
        "public void refreshCloudApps()",
        [r"fetchCloudAppsAsync\(false\)"],
        [r"fetchCloudAppsAsync\(true\)"]
    ),
    (
        "系统底座", "行车巡航 6 分钟避峰单次静默同步引擎锁死",
        os.path.join(WEB_BASE, "App.vue"),
        "CRUISING_SYNC_DELAY",
        [r"6\s*\*\s*60\s*\*\s*1000", r"refreshCloudApps", r"refreshOilPrices\(false\)"],
        []
    )
]

def main():
    print("=" * 65)
    print(">> 卡片级与按钮级原子契约门禁核验 (Card & Button Contract Gate)")
    print("=" * 65)
    
    failures = []
    
    for card, button, file_path, marker, required_patterns, forbidden_patterns in CARD_BUTTON_CONTRACTS:
        if not os.path.exists(file_path):
            failures.append((card, button, f"文件不存在: {file_path}"))
            continue
            
        with open(file_path, "r", encoding="utf-8") as f:
            content = f.read()
            
        clean = clean_code(content) if file_path.endswith(".java") else content
        
        try:
            target_region = extract_region(clean, marker, is_vue=file_path.endswith(".vue"))
        except Exception as e:
            failures.append((card, button, f"方法签名已被破坏或删除: {e}"))
            continue
            
        for req in required_patterns:
            if not re.search(req, target_region):
                failures.append((card, button, f"缺少关键必选契约逻辑: `{req}`"))
                
        for forb in forbidden_patterns:
            if re.search(forb, target_region):
                failures.append((card, button, f"命中严禁包含的违规逻辑: `{forb}`"))
                
    if failures:
        print(f"\n[FAIL] 🚨 检测到 {len(failures)} 项卡片/按钮独立原子契约被破坏：")
        for i, (card, btn, reason) in enumerate(failures, 1):
            print(f"  {i}. 【{card}】 -> 【{btn}】: {reason}")
        print("\n🚫 门禁阻断原因：修 bug 严禁顺带改坏已验证卡片或按钮的逻辑！")
        print("💡 如确需修改，必须停下来向车主汇报改动理由与破坏性影响，经车主明确授权后方可按范围放开！\n")
        sys.exit(1)
        
    print(f"\n[PASS] ✅ 全部 {len(CARD_BUTTON_CONTRACTS)} 项卡片与按钮独立原子契约均 100% 锁死且校验全绿！")
    print("       (车身联动、挡位安全、4大模式、蓝牙音频、音乐续播、熄火休眠、方控自定义、语音规范)\n")
    sys.exit(0)

if __name__ == "__main__":
    main()
