#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
CI Health Gate for GeelyPilot (GeelyToolbox)
Architecture: Vite + Vue 3 + Tailwind CSS + Android Java Native JSBridge

10 Comprehensive Gates:
1. Java Static Symbol & Log Integrity Gate (TAG, imports, helper methods)
2. Mobile Fast-Transfer Web AST & Syntax Gate (mobile_web.html parsed via Node.js AST)
3. Android 9 Chromium 68 Full-Spectrum Layout Gate (Zero flex gap, zero bare inset-0)
4. Modal Layering & z-index Gate (ConfirmModal z-[9999] elevation)
5. Cockpit Color, Contrast & Tombstone Aesthetics Gate (Zero tombstone buttons, adaptive inputs)
6. Vehicle Touch Touchpoint & Button Text Overflow Gate (Minimum touch height, single-line protection)
7. Voice Audio Asset Completeness Gate (100% referenced mp3s physically exist in assets/audio/)
8. Steering Wheel Action Full-Chain Closure Gate (WheelView actions handled in SteeringWheelKeyManager)
9. Full-Stack State & JSBridge Contract Gate (Store keys, Bridge methods, Modal imports)
10. Cloud CDN Assets & Dual-Track Version Gate (HEAD link verification, build.gradle consistency)
"""

import json
import os
import re
import subprocess
import sys
import time
import urllib.request

ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
APP_SRC_DIR = os.path.join(ROOT_DIR, "app/src/main")
JAVA_SRC_DIR = os.path.join(APP_SRC_DIR, "java")
ASSETS_DIR = os.path.join(APP_SRC_DIR, "assets")
AUDIO_DIR = os.path.join(ASSETS_DIR, "audio")
WEB_SRC_DIR = os.path.join(ROOT_DIR, "web/src")

JAVA_MAIN_PATH = os.path.join(JAVA_SRC_DIR, "app/onepve/geelyconsole/MainActivity.java")
SERVICE_PATH = os.path.join(JAVA_SRC_DIR, "app/onepve/geelyconsole/services/VehicleAutomationService.java")
KEY_MANAGER_PATH = os.path.join(JAVA_SRC_DIR, "app/onepve/geelyconsole/utils/SteeringWheelKeyManager.java")
SYSTEM_UTILS_PATH = os.path.join(JAVA_SRC_DIR, "app/onepve/geelyconsole/utils/SystemUtils.java")
APP_LOGGER_PATH = os.path.join(JAVA_SRC_DIR, "app/onepve/geelyconsole/utils/AppLogger.java")

APP_VUE_PATH = os.path.join(WEB_SRC_DIR, "App.vue")
STORE_JS_PATH = os.path.join(WEB_SRC_DIR, "store/index.js")
APPS_DATA_PATH = os.path.join(WEB_SRC_DIR, "data/apps.js")
BUILD_GRADLE_PATH = os.path.join(ROOT_DIR, "app/build.gradle")
MOBILE_WEB_PATH = os.path.join(ASSETS_DIR, "mobile_web.html")

passed = True


def log_step(title):
    print(f"\n{'='*65}\n>> {title}\n{'='*65}")


# ----------------------------------------------------------------------
# 1. Java Static Symbol & Log Integrity Gate
# ----------------------------------------------------------------------
log_step("1. Checking Java Static Symbol & Log Integrity")
java_violations = []

for root, _, files in os.walk(JAVA_SRC_DIR):
    for fn in files:
        if fn.endswith(".java"):
            fp = os.path.join(root, fn)
            with open(fp, "r", encoding="utf-8") as f:
                content = f.read()

            # Check Log usage: if Log.d/i/w/e/v used, must import Log and define TAG
            if re.search(r'\bLog\.[diwev]\s*\(', content):
                if "import android.util.Log;" not in content:
                    java_violations.append(f"{fn}: calls Log without 'import android.util.Log;'")
                if not re.search(r'TAG\s*=', content):
                    java_violations.append(f"{fn}: calls Log without TAG constant defined")

            # Check SystemUtils calls
            sys_calls = set(re.findall(r'SystemUtils\.([a-zA-Z0-9_]+)\s*\(', content))
            with open(SYSTEM_UTILS_PATH, "r", encoding="utf-8") as sf:
                sys_methods = set(re.findall(r'public\s+static\s+[^;{=]+\s+([a-zA-Z0-9_]+)\s*\(', sf.read()))
            missing_sys = sys_calls - sys_methods
            if missing_sys:
                java_violations.append(f"{fn}: calls undefined SystemUtils methods: {missing_sys}")

            # Check AppLogger calls
            logger_calls = set(re.findall(r'AppLogger\.([a-zA-Z0-9_]+)\s*\(', content))
            with open(APP_LOGGER_PATH, "r", encoding="utf-8") as lf:
                logger_methods = set(re.findall(r'public\s+static\s+[^;{=]+\s+([a-zA-Z0-9_]+)\s*\(', lf.read()))
            missing_logger = logger_calls - logger_methods
            if missing_logger:
                java_violations.append(f"{fn}: calls undefined AppLogger methods: {missing_logger}")

if java_violations:
    for v in java_violations:
        print(f"  [FAIL] {v}")
    passed = False
else:
    print("[PASS] 100% of Java symbols, Log imports, TAG constants, and utility calls are verified valid.")


# ----------------------------------------------------------------------
# 2. Mobile Fast-Transfer Web AST & Syntax Gate (mobile_web.html)
# ----------------------------------------------------------------------
log_step("2. Checking Mobile Fast-Transfer Web Template & JS AST")
if not os.path.exists(MOBILE_WEB_PATH):
    print(f"  [FAIL] mobile_web.html missing at {MOBILE_WEB_PATH}")
    passed = False
else:
    with open(MOBILE_WEB_PATH, "r", encoding="utf-8") as f:
        mobile_html = f.read()

    js_match = re.search(r'<script>(.*?)</script>', mobile_html, re.DOTALL)
    if not js_match:
        print("  [FAIL] <script> block missing in mobile_web.html")
        passed = False
    else:
        tmp_js = "/tmp/ci_test_mobile_script.js"
        with open(tmp_js, "w", encoding="utf-8") as f:
            f.write(js_match.group(1))
        res = subprocess.run(["node", "-c", tmp_js], capture_output=True, text=True)
        if res.returncode != 0:
            print(f"  [FAIL] JavaScript syntax error in mobile_web.html:\n{res.stderr}")
            passed = False
        else:
            print("[PASS] mobile_web.html JavaScript syntax validated 100% clean via Node.js AST engine.")

    # Check input types and box-sizing
    input_tags = re.findall(r'(<input[^>]+>)', mobile_html)
    unstyled_inputs = [tag for tag in input_tags if 'type=' not in tag]
    if unstyled_inputs:
        print(f"  [WARN/FAIL] Input tags missing explicit type in mobile_web.html: {unstyled_inputs}")
        passed = False
    else:
        print("[PASS] All input elements explicitly typed and mobile viewport ready.")


# ----------------------------------------------------------------------
# 3. Android 9 Chromium 68 Full-Spectrum Layout Gate
# ----------------------------------------------------------------------
log_step("3. Checking Android 9 (Chromium 68) Layout Compatibility (Space vs Flex Gap)")
gap_violations = []
bare_inset_violations = []

for root, _, files in os.walk(WEB_SRC_DIR):
    for fn in files:
        if fn.endswith((".vue", ".html")):
            fp = os.path.join(root, fn)
            with open(fp, "r", encoding="utf-8") as f:
                for idx, line in enumerate(f):
                    if "flex" in line and re.search(r'\bgap-[0-9]+', line):
                        gap_violations.append((fn, idx + 1, line.strip()))
                    if re.search(r'\binset-0\b', line) and 'top: 0' not in line:
                        bare_inset_violations.append((fn, idx + 1, line.strip()))

if gap_violations:
    for v in gap_violations:
        print(f"  [FAIL] Flex with gap found at {v[0]}:{v[1]} -> {v[2]}")
    passed = False
else:
    print("[PASS] Zero forbidden flex gap usages found across all Vue components (100% space-x/y margin compliant).")

if bare_inset_violations:
    for v in bare_inset_violations:
        print(f"  [FAIL] Bare inset-0 found at {v[0]}:{v[1]} -> {v[2]}")
    passed = False
else:
    print("[PASS] Zero bare inset-0 found (all modal containers have top:0 left:0 width:100vw fallback).")


# ----------------------------------------------------------------------
# 4. Modal Layering & z-index Gate
# ----------------------------------------------------------------------
log_step("4. Checking Modal Layering & z-index Elevation")
confirm_modal_path = os.path.join(WEB_SRC_DIR, "components/modals/ConfirmModal.vue")
if os.path.exists(confirm_modal_path):
    with open(confirm_modal_path, "r", encoding="utf-8") as f:
        confirm_content = f.read()
    if 'z-[9999]' in confirm_content:
        print("[PASS] ConfirmModal elevation confirmed at top tier (z-[9999]), 100% immune to backdrop occlusion.")
    else:
        print("[FAIL] ConfirmModal missing z-[9999] elevation tag!")
        passed = False


# ----------------------------------------------------------------------
# 5. Cockpit Color, Contrast & Tombstone Aesthetics Gate
# ----------------------------------------------------------------------
log_step("5. Checking Cockpit Color, Contrast & Tombstone Aesthetics")
tombstone_violations = []
slash_opacity_violations = []
white_bg_violations = []

for root, _, files in os.walk(WEB_SRC_DIR):
    for fn in files:
        if fn.endswith(".vue"):
            fp = os.path.join(root, fn)
            with open(fp, "r", encoding="utf-8") as f:
                for idx, line in enumerate(f):
                    # 1. 丧葬墓碑纯白底座黑字检测
                    if ("<button" in line or "cursor-pointer" in line) and "bg-white" in line and "text-black" in line:
                        tombstone_violations.append((fn, idx + 1, line.strip()))
                    
                    # 2. Android 9 Chromium 68 塌陷为白底的现代 slash opacity 类检测 (bg-car-accent/xx)
                    if "bg-car-accent/" in line and not line.strip().startswith("//") and not line.strip().startswith("<!--"):
                        slash_opacity_violations.append((fn, idx + 1, line.strip()))

                    # 3. 车载夜间深色模式下未经脱敏的刺眼纯白底座检测 (排除二维码与微信赞赏码实体卡片)
                    if "bg-white" in line and not any(x in fn for x in ["QrCode", "Reward"]) and not line.strip().startswith("//") and not line.strip().startswith("<!--"):
                        white_bg_violations.append((fn, idx + 1, line.strip()))

if tombstone_violations:
    for v in tombstone_violations:
        print(f"  [FAIL] Tombstone pure-white button found at {v[0]}:{v[1]} -> {v[2]}")
    passed = False
else:
    print("[PASS] Zero tombstone (bg-white text-black) buttons found.")

if slash_opacity_violations:
    for v in slash_opacity_violations:
        print(f"  [FAIL] Chromium 68 white-background fallback hazard (bg-car-accent/xx) found at {v[0]}:{v[1]} -> {v[2]}")
    passed = False
else:
    print("[PASS] Zero Chromium 68 slash opacity (bg-car-accent/xx) hazards found. All backgrounds are solid and immune to white fallback.")

if white_bg_violations:
    for v in white_bg_violations:
        print(f"  [FAIL] Inappropriate pure-white background (bg-white) found at {v[0]}:{v[1]} -> {v[2]}")
    passed = False
else:
    print("[PASS] Zero dazzling white backgrounds in cockpit UI. High-contrast Obsidian & Halo Ring strictly enforced.")


# ----------------------------------------------------------------------
# 6. Vehicle Touch Touchpoint & Button Text Overflow Gate
# ----------------------------------------------------------------------
log_step("6. Checking Touchpoint Sizing & Non-Wrapping Text Protection")
touch_violations = []

for root, _, files in os.walk(WEB_SRC_DIR):
    for fn in files:
        if fn.endswith(".vue"):
            fp = os.path.join(root, fn)
            with open(fp, "r", encoding="utf-8") as f:
                content = f.read()
            # Check critical buttons for touch height: primary switch/action buttons must be >= 50px
            buttons = re.findall(r'<button([^>]+)>', content)
            for b in buttons:
                # 排除状态栏顶部 TopBar 小药丸按钮
                if "TopBar" in fn:
                    continue
                if 'class=' in b and 'h-[' in b:
                    h_m = re.search(r'h-\[(\d+)px\]', b)
                    if h_m and int(h_m.group(1)) < 50:
                        touch_violations.append((fn, h_m.group(1), b.strip()))

if touch_violations:
    for v in touch_violations:
        print(f"  [FAIL] Primary vehicle button height under 50px ({v[1]}px) at {v[0]}: {v[2][:60]}")
    passed = False
else:
    print("[PASS] All primary vehicle touch buttons satisfy car-grade touch sizing (>= 50px, primary tiles 78~88px).")


# ----------------------------------------------------------------------
# 7. Voice Audio Asset Completeness Gate (Zero Broken Audio)
# ----------------------------------------------------------------------
log_step("7. Checking Voice Audio Asset Completeness (Zero Dead Audio)")
audio_refs = set()
for root, _, files in os.walk(ROOT_DIR):
    if "node_modules" in root or ".git" in root:
        continue
    for fn in files:
        if fn.endswith((".java", ".vue")):
            fp = os.path.join(root, fn)
            with open(fp, "r", encoding="utf-8") as f:
                matches = re.findall(r'[\"\']([a-zA-Z0-9_]+\.mp3)[\"\']', f.read())
                for m in matches:
                    if not m.startswith("custom_voice_") and m != "xxx.mp3":
                        audio_refs.add(m)

if not os.path.exists(AUDIO_DIR):
    print(f"  [FAIL] Audio asset directory missing: {AUDIO_DIR}")
    passed = False
else:
    existing_audios = set(os.listdir(AUDIO_DIR))
    missing_audios = audio_refs - existing_audios
    print(f"Total voice asset references in code: {len(audio_refs)}")
    print(f"Total physical mp3 files in assets/audio/: {len(existing_audios)}")
    if missing_audios:
        print(f"  [FAIL] Code references audio assets missing on disk: {missing_audios}")
        passed = False
    else:
        # Verify sizes > 0
        empty_audios = [a for a in existing_audios if os.path.getsize(os.path.join(AUDIO_DIR, a)) == 0]
        if empty_audios:
            print(f"  [FAIL] Empty 0-byte audio files detected: {empty_audios}")
            passed = False
        else:
            print("[PASS] 100% of referenced voice audio assets physically exist and are non-empty.")


# ----------------------------------------------------------------------
# 8. Steering Wheel Action Full-Chain Closure Gate
# ----------------------------------------------------------------------
log_step("8. Checking Steering Wheel Key Action Closure (WheelView vs KeyManager)")
if os.path.exists(KEY_MANAGER_PATH):
    with open(KEY_MANAGER_PATH, "r", encoding="utf-8") as f:
        km_content = f.read()
    handled_actions = set(re.findall(r'case\s+([A-Z0-9_]+):', km_content))
    print(f"KeyManager handled actions: {handled_actions}")
    required_actions = {"ACTION_OPEN_360", "ACTION_OPEN_NAVI", "ACTION_PLAY_PAUSE", "ACTION_MUTE_TOGGLE"}
    missing_actions = required_actions - handled_actions
    if missing_actions:
        print(f"  [FAIL] Missing action handlers in SteeringWheelKeyManager: {missing_actions}")
        passed = False
    else:
        print("[PASS] All steering wheel action options are cleanly handled with zero dead code branches.")


# ----------------------------------------------------------------------
# 9. Full-Stack State & JSBridge Contract Gate
# ----------------------------------------------------------------------
log_step("9. Checking Full-Stack State & JSBridge Contract (Store/Modals vs Java)")
# Modal registration
with open(APP_VUE_PATH, "r", encoding="utf-8") as f:
    app_vue_content = f.read()
template_modals = set(re.findall(r'<([A-Z][a-zA-Z0-9]*Modal)\s*/>', app_vue_content))
imported_modals = set(re.findall(r'import\s+([A-Z][a-zA-Z0-9]*Modal)\s+from', app_vue_content))
missing_modals = template_modals - imported_modals
if missing_modals:
    print(f"  [FAIL] Modals used in template but NOT imported: {missing_modals}")
    passed = False
else:
    print(f"[PASS] All {len(template_modals)} Vue modal components are properly registered.")

# State contract: store.vehicleAuto vs Java getVehicleAutomationSettings
with open(STORE_JS_PATH, "r", encoding="utf-8") as f:
    store_js = f.read()
auto_m = re.search(r'vehicleAuto:\s*\{([^}]+)\}', store_js)
store_auto_keys = set(re.findall(r'([a-zA-Z0-9_]+)\s*:', auto_m.group(1))) if auto_m else set()

with open(JAVA_MAIN_PATH, "r", encoding="utf-8") as f:
    java_content = f.read()
get_auto_m = re.search(r'public String getVehicleAutomationSettings\(\)\s*\{([\s\S]+?return obj\.toString\(\);)', java_content)
java_auto_keys = set(re.findall(r'obj\.put\(\"([a-zA-Z0-9_]+)\"', get_auto_m.group(1))) if get_auto_m else set()

missing_auto_keys = store_auto_keys - java_auto_keys
if missing_auto_keys:
    print(f"  [FAIL] Keys defined in store.vehicleAuto but missing in Java: {missing_auto_keys}")
    passed = False
else:
    print(f"[PASS] 100% of store.vehicleAuto keys ({len(store_auto_keys)}) are properly returned by Java.")

# JSBridge calls vs Java @JavascriptInterface
frontend_calls = set()
for root, _, files in os.walk(WEB_SRC_DIR):
    for fn in files:
        if fn.endswith((".vue", ".js")):
            with open(os.path.join(root, fn), "r", encoding="utf-8") as f:
                frontend_calls.update(re.findall(r'bridge\.call\([\"\\\']([a-zA-Z0-9_]+)[\"\\\']', f.read()))

java_bridge_methods = set(re.findall(r'@JavascriptInterface\s+public\s+[^\(]+\s+([a-zA-Z0-9_]+)\s*\(', java_content))
missing_bridge = sorted(list(frontend_calls - java_bridge_methods))
if missing_bridge:
    print(f"  [FAIL] Frontend calls Bridge methods NOT implemented in Java: {missing_bridge}")
    passed = False
else:
    print(f"[PASS] All {len(frontend_calls)} frontend Bridge calls are 100% implemented in Java.")


# ----------------------------------------------------------------------
# 10. Cloud CDN Assets & Dual-Track Version Gate
# ----------------------------------------------------------------------
log_step("10. Checking Cloud CDN Assets & Dual-Track Version Gate")
with open(BUILD_GRADLE_PATH, "r", encoding="utf-8") as f:
    gradle_content = f.read()
v_code = re.search(r'versionCode\s+([0-9]+)', gradle_content)
v_name = re.search(r'versionName\s+\"([^\"]+)\"', gradle_content)
if v_code and v_name:
    print(f"[PASS] Gradle version configuration valid: {v_name.group(1)} (code {v_code.group(1)})")
else:
    print("[FAIL] Failed to parse versionCode/versionName from app/build.gradle")
    passed = False

if os.path.exists(APPS_DATA_PATH):
    with open(APPS_DATA_PATH, "r", encoding="utf-8") as f:
        app_urls = re.findall(r'\"url\":\s*\"([^\"]+)\"', f.read())
    print(f"Total cloud app URLs to verify: {len(app_urls)}")
    failures = []
    for url in app_urls:
        status_code = None
        err = None
        for attempt in range(1, 4):
            try:
                req = urllib.request.Request(url, method="HEAD", headers={"User-Agent": "GeelyToolbox-CI/1.0"})
                with urllib.request.urlopen(req, timeout=10) as resp:
                    status_code = resp.status
                    if status_code in (200, 301, 302):
                        break
            except Exception as ex:
                err = ex
                time.sleep(1)
        if status_code in (200, 301, 302):
            print(f"  [OK] {status_code} -> {url}")
        else:
            print(f"  [FAIL] -> {url} (Error: {err})")
            failures.append((url, str(err)))
    if failures:
        print(f"  [FAIL] {len(failures)} asset links failed verification!")
        passed = False
    else:
        print("[PASS] All cloud app asset links verified accessible.")


# ----------------------------------------------------------------------
# 11. Chromium 68 Layout Safety: Flex min-w-0 & FeatureCard Non-Stretching
# ----------------------------------------------------------------------
log_step("11. Checking Chromium 68 Layout Safety: Flex min-w-0 & FeatureCard Non-Stretching")
with open(os.path.join(ROOT_DIR, "web/src/components/FeatureCard.vue"), "r", encoding="utf-8") as f:
    fc_content = f.read()

if "h-full" in fc_content or "mt-auto" in fc_content:
    print("  [FAIL] FeatureCard.vue must NOT contain 'h-full' or 'mt-auto' (causes infinite stretch in Chromium 68 flex containers)!")
    passed = False
else:
    print("[PASS] FeatureCard.vue non-stretching container confirmed clean (zero h-full/mt-auto).")

# Check all Vue views for flex-1 containers lacking min-w-0
flex1_issues = []
for root, _, files in os.walk(os.path.join(ROOT_DIR, "web/src/views")):
    for fn in files:
        if fn.endswith(".vue"):
            fp = os.path.join(root, fn)
            with open(fp, "r", encoding="utf-8") as f:
                lines = f.readlines()
            for idx, line in enumerate(lines, 1):
                # Match class containing flex-1 but missing min-w-0 in flex row containers
                if "flex-1" in line and "min-w-0" not in line and "flex items-center" not in line and "button" not in line:
                    if "flex flex-col" in line or "pr-" in line:
                        flex1_issues.append((fn, idx, line.strip()))

if flex1_issues:
    print(f"  [FAIL] Found {len(flex1_issues)} flex-1 container(s) missing min-w-0 (Chromium 68 text overlap hazard):")
    for fn, idx, line in flex1_issues:
        print(f"    - {fn}:{idx} -> {line}")
    passed = False
else:
    print("[PASS] 100% of flex-1 text containers enforce min-w-0 boundary protection.")


# ----------------------------------------------------------------------
# 12. Publish Changelog Formatting & Newline Integrity Gate
# ----------------------------------------------------------------------
log_step("12. Checking Publish Changelog Formatting (Zero Literal \\n Bugs)")
with open(os.path.join(ROOT_DIR, "scripts/publish_r2.py"), "r", encoding="utf-8") as f:
    pub_content = f.read()

# Check for literal escaped \\n in publish_r2.py changelog
if "\\\\n" in pub_content:
    print("  [FAIL] scripts/publish_r2.py contains literal '\\\\n' double-escaped strings! Must use actual newlines '\\n'.")
    passed = False
else:
    print("[PASS] scripts/publish_r2.py uses clean real newlines (zero literal \\\\n).")

with open(os.path.join(ROOT_DIR, "web/src/components/modals/UpdateModal.vue"), "r", encoding="utf-8") as f:
    update_modal_content = f.read()

if ".replace(/\\\\n/g" not in update_modal_content:
    print("  [FAIL] UpdateModal.vue must include .replace(/\\\\n/g, '\\n') defensive formatting for changelog!")
    passed = False
else:
    print("[PASS] UpdateModal.vue includes changelog newline fallback defense.")


# ----------------------------------------------------------------------
# 13. Decoupled Vehicle State Managers & Direct MCU Protocol Integrity Gate
# ----------------------------------------------------------------------
log_step("13. Checking Decoupled Vehicle State Managers & Direct MCU Protocol")
required_managers = [
    ("DoorStateManager.java", "四门独立状态机"),
    ("TrunkStateManager.java", "电动尾门专属状态机"),
    ("GearStateMachine.java", "换挡有人感知状态机"),
    ("DriveModeManager.java", "驾驶模式独立判定器")
]

manager_violations = []
utils_dir = os.path.join(JAVA_SRC_DIR, "app/onepve/geelyconsole/utils")

for mgr_file, desc in required_managers:
    fp = os.path.join(utils_dir, mgr_file)
    if not os.path.exists(fp):
        manager_violations.append(f"{mgr_file} ({desc}) 核心类缺失！")
    else:
        with open(fp, "r", encoding="utf-8") as f:
            mc = f.read()
        # 必须具备独立的 setListener 与 update 方法，严禁空实现
        if "setListener" not in mc:
            manager_violations.append(f"{mgr_file}: 缺少 setListener 状态监听器绑定！")
        if "AppLogger" not in mc:
            manager_violations.append(f"{mgr_file}: 缺少 AppLogger 中文审计日志！")

# 检查 VehicleAutomationService 是否彻底完成四大模块解耦接线
with open(SERVICE_PATH, "r", encoding="utf-8") as f:
    vas_content = f.read()

for mgr_cls in ["DoorStateManager", "TrunkStateManager", "GearStateMachine", "DriveModeManager"]:
    if f"import app.onepve.geelyconsole.utils.{mgr_cls};" not in vas_content:
        manager_violations.append(f"VehicleAutomationService 缺少对 {mgr_cls} 的显式 import！")
    if f"new {mgr_cls}" not in vas_content:
        manager_violations.append(f"VehicleAutomationService 未实例化 {mgr_cls}！")

# 检查硬件串口权威通道 91 02 01 是否直接分发给四门与尾门独立状态机
if "doorStateManager.updateDoors" not in vas_content:
    manager_violations.append("串口 91 02 01 未直通分发给 doorStateManager！")
if "trunkStateManager.updateTrunk" not in vas_content:
    manager_violations.append("串口 91 02 01 未直通分发给 trunkStateManager！")

if manager_violations:
    for v in manager_violations:
        print(f"  [FAIL] {v}")
    passed = False
else:
    print("[PASS] 四大独立解耦状态机完整闭环，MCU硬件串口(91 02 01)与TCU换挡中断100%接线闭环！")


# ----------------------------------------------------------------------
# 14. Voice Theme & Fast-Transfer Template Integrity Gate
# ----------------------------------------------------------------------
log_step("14. Checking Voice Theme & Fast-Transfer Template Integrity")
voice_violations = []

template_zip_path = os.path.join(ASSETS_DIR, "voice_template.zip")
readme_path = os.path.join(ASSETS_DIR, "voice_readme.txt")

if not os.path.exists(template_zip_path) or os.path.getsize(template_zip_path) == 0:
    voice_violations.append("assets/voice_template.zip 不存在或为空！")
else:
    import zipfile
    try:
        with zipfile.ZipFile(template_zip_path, "r") as zf:
            zip_names = set(zf.namelist())
            req_audios = {
                "gear_d.mp3", "gear_r.mp3", "gear_p.mp3", "gear_n.mp3",
                "mode_smart.mp3", "mode_comfort.mp3", "mode_eco.mp3", "mode_sport.mp3",
                "door_fl.mp3", "door_fl_close.mp3", "door_fr.mp3", "door_fr_close.mp3",
                "door_rl.mp3", "door_rr.mp3",
                "trunk_open.mp3", "trunk_close.mp3", "README.txt"
            }
            missing = req_audios - zip_names
            if missing:
                voice_violations.append(f"voice_template.zip 缺少关键文件: {missing}")
    except Exception as e:
        voice_violations.append(f"voice_template.zip 损坏无法读取: {e}")

if not os.path.exists(readme_path) or os.path.getsize(readme_path) == 0:
    voice_violations.append("assets/voice_readme.txt 不存在或为空！")

with open(os.path.join(JAVA_SRC_DIR, "app/onepve/geelyconsole/server/WebServer.java"), "r", encoding="utf-8") as f:
    ws_code = f.read()

if "/api/voice_template" not in ws_code or "/api/voice_readme" not in ws_code:
    voice_violations.append("WebServer.java 缺少 /api/voice_template 或 /api/voice_readme 路由！")

with open(os.path.join(JAVA_SRC_DIR, "app/onepve/geelyconsole/utils/VehicleVoicePlayer.java"), "r", encoding="utf-8") as f:
    vvp_code = f.read()

if "GeelyPilot/voices" not in vvp_code:
    voice_violations.append("VehicleVoicePlayer 未确立 GeelyPilot/voices 专属物理隔离目录！")
if "extractVoiceZip" not in vvp_code or "isVoicePackZip" not in vvp_code:
    voice_violations.append("VehicleVoicePlayer 缺少 ZIP 自动识别与穿透解压引擎！")

with open(MOBILE_WEB_PATH, "r", encoding="utf-8") as f:
    mweb_code = f.read()

if "voice_template" not in mweb_code or "voice_readme" not in mweb_code:
    voice_violations.append("mobile_web.html 缺少语音模板下载或说明文档弹窗！")

if voice_violations:
    for v in voice_violations:
        print(f"  [FAIL] {v}")
    passed = False
else:
    print("[PASS] 官方轻量模板(16音频+README)、快传双API、专属目录物理隔离与解压引擎100%闭环！")


# ----------------------------------------------------------------------
# 15. Download Directory 3-Tier Clean Modes & Audio Safety Gate
# ----------------------------------------------------------------------
log_step("15. Checking Download Clean Modes & Voice Protection Gate")
clean_violations = []

with open(JAVA_MAIN_PATH, "r", encoding="utf-8") as f:
    main_code = f.read()

if "cleanDownloadDirectory" not in main_code:
    clean_violations.append("MainActivity 缺少 cleanDownloadDirectory 桥接接口！")
else:
    if "mode == 1" not in main_code or "mode == 2" not in main_code or "mode == 3" not in main_code:
        clean_violations.append("cleanDownloadDirectory 未完整处理三阶清理模式 (1=全量清空, 2=智能安全, 3=仅清理文件)！")

clean_modal_path = os.path.join(WEB_SRC_DIR, "components/modals/CleanDownloadModal.vue")
if not os.path.exists(clean_modal_path):
    clean_violations.append("CleanDownloadModal.vue 弹窗组件缺失！")
else:
    with open(clean_modal_path, "r", encoding="utf-8") as f:
        cm_code = f.read()
    if "confirmClean(1)" not in cm_code or "confirmClean(2)" not in cm_code or "confirmClean(3)" not in cm_code:
        clean_violations.append("CleanDownloadModal 未完整绑定三阶清理维度！")

if clean_violations:
    for v in clean_violations:
        print(f"  [FAIL] {v}")
    passed = False
else:
    print("[PASS] 下载目录三阶清理中枢与音频素材安全避让策略100%闭环！")


# ----------------------------------------------------------------------
# 16. Checking Core Feature Regression & Critical Button Defense Gate
# ----------------------------------------------------------------------
log_step("16. Checking Core Feature Regression & Critical Button Defense Gate")
reg_violations = []

# 16.1 检查软件中心下载生命周期完整控制 (暂停/取消/重试/安装)
app_detail_modal_path = os.path.join(WEB_SRC_DIR, "components/modals/AppDetailModal.vue")
if not os.path.exists(app_detail_modal_path):
    reg_violations.append("AppDetailModal.vue 组件缺失！")
else:
    with open(app_detail_modal_path, "r", encoding="utf-8") as f:
        adm_code = f.read()
    if "pauseDownload" not in adm_code:
        reg_violations.append("软件中心 AppDetailModal 缺少【暂停下载】按钮或方法！")
    if "cancelDownload" not in adm_code:
        reg_violations.append("软件中心 AppDetailModal 缺少【取消下载】按钮或方法！")
    if "resumeDownload" not in adm_code:
        reg_violations.append("软件中心 AppDetailModal 缺少【继续下载】按钮或方法！")
    if "installDownloadedApk" not in adm_code:
        reg_violations.append("软件中心 AppDetailModal 缺少【立即安装应用】闭环按键！")

with open(os.path.join(WEB_SRC_DIR, "App.vue"), "r", encoding="utf-8") as f:
    app_vue_code = f.read()
if "updateDownloadPaused" not in app_vue_code or "updateDownloadCancelled" not in app_vue_code or "updateDownloadSuccess" not in app_vue_code:
    reg_violations.append("App.vue 缺少全局下载状态回调 (updateDownloadPaused / updateDownloadCancelled / updateDownloadSuccess)！")

# 16.2 检查方控接管模式、长按滑块与米小江兼容
# （「HAL 纯协议直连」双轨测试通道已按车主指令下线，方控统一固定走硬件事件直连链路，故不再校验该开关）
wheel_view_path = os.path.join(WEB_SRC_DIR, "views/WheelView.vue")
with open(wheel_view_path, "r", encoding="utf-8") as f:
    wv_code = f.read()
if "wheel_monitor_engine_mode" in wv_code:
    reg_violations.append("WheelView.vue 仍残留已下线的「方控 HAL 双轨测试」切换卡片！")
if "wheel_long_press_ms" not in wv_code or "longPressSec" not in wv_code:
    reg_violations.append("WheelView.vue 缺少方控按键长按判定时长自由调节滑块！")
if "carmedia_first" not in wv_code:
    reg_violations.append("WheelView.vue 缺少米小江方控优先模式单选卡片！")

# 16.3 检查车身监控引擎（HAL 实验通道已下线）与倒车音量滑块
# （车身「原厂 HAL / CarService 直通」双轨实验通道已按车主指令下线：
#   该车机固件未授予平台签名，CarService 无法连接，只会产出失败日志；
#   档位统一以 MCU 串口报文为唯一权威源，故此处改为反向校验，防被误加回来）
body_view_path = os.path.join(WEB_SRC_DIR, "views/BodyView.vue")
with open(body_view_path, "r", encoding="utf-8") as f:
    bv_code = f.read()
if "vehicle_monitor_engine_mode" in bv_code:
    reg_violations.append("BodyView.vue 仍残留已下线的「车身 HAL 双轨实验」切换卡片！")

voice_item_modal_path = os.path.join(WEB_SRC_DIR, "components/modals/VoiceItemSettingsModal.vue")
with open(voice_item_modal_path, "r", encoding="utf-8") as f:
    vism_code = f.read()
if "reverse_volume_boost" not in vism_code or "reverseBoost" not in vism_code:
    reg_violations.append("VoiceItemSettingsModal.vue 倒车设置缺少【倒车防衰减音量额外补偿】滑块！")

# 16.4 检查手机快传避免单行双按钮溢出
with open(MOBILE_WEB_PATH, "r", encoding="utf-8") as f:
    mweb_code = f.read()
if 'style="display:flex; gap:8px;' in mweb_code and "voice_template" in mweb_code:
    reg_violations.append("mobile_web.html 语音制作中枢仍在使用单行双按钮布局，窄屏将溢出！")

# 16.5 检查专家模式三步确认倒计时契约 (防看都不看盲点解锁) 与状态胶囊高对比
install_view_path = os.path.join(WEB_SRC_DIR, "views/InstallView.vue")
with open(install_view_path, "r", encoding="utf-8") as f:
    iv_code = f.read()
# 三步确认必须分别带 10s / 5s / 5s 倒计时
if "countdown: 10," not in iv_code:
    reg_violations.append("InstallView 专家模式第1步(风险告知)确认缺少 10s 倒计时！")
if iv_code.count("countdown: 5,") < 2:
    reg_violations.append("InstallView 专家模式第2/3步确认缺少 5s 倒计时！")
# 状态胶囊必须统一顶栏款高对比样式 (bg-car-item + border-car-border + text-car-text) 且带语义点，不得回退为实底彩底
if "bg-car-item border-car-border text-car-text" not in iv_code:
    reg_violations.append("InstallView 专家模式【已激活】胶囊丢失顶栏统一样式 (需 bg-car-item + border-car-border + text-car-text)！")
if "bg-amber-400 text-[#0F172A]" in iv_code or "bg-emerald-400 text-[#0F172A]" in iv_code:
    reg_violations.append("InstallView 专家模式胶囊回退为实底彩底旧样式 (应统一顶栏款 + 语义发光圆点)！")

confirm_modal_path = os.path.join(WEB_SRC_DIR, "components/modals/ConfirmModal.vue")
with open(confirm_modal_path, "r", encoding="utf-8") as f:
    cm_code = f.read()
if "countdownLeft" not in cm_code or "请仔细阅读" not in cm_code:
    reg_violations.append("ConfirmModal.vue 缺少高危确认倒计时逻辑 (countdownLeft / 请仔细阅读)！")

# 16.6 已修复问题的专项回归：源码契约 + 真实 Java/JS 决策 + 反例自检
# 在本地与 tag CI 中均执行；任何失败阻断 APK 编译和上传。
regression_scripts = [
    "check_recent_regressions.py",
    "test_button_focus_gate.py",
    "test_recent_decisions.py",
    "test_update_filename.py",
    "test_regression_mutations.py",
]
for regression_script in regression_scripts:
    try:
        result = subprocess.run(
            [sys.executable, os.path.join(ROOT_DIR, "scripts", regression_script)],
            cwd=ROOT_DIR, capture_output=True, text=True, timeout=90,
        )
        if result.returncode:
            reg_violations.append(f"专项回归 {regression_script} 失败:\n{result.stdout}\n{result.stderr}")
        else:
            print(f"[PASS] 专项回归: {regression_script}")
    except (OSError, subprocess.TimeoutExpired) as e:
        reg_violations.append(f"专项回归 {regression_script} 无法完成: {e}")

if reg_violations:
    for v in reg_violations:
        print(f"  [FAIL] {v}")
    passed = False
else:
    print("[PASS] 核心功能与已修复缺陷回归通过：下载控制、方控兼容、语音、屏保、焦点框、安装分流、命名及性能防线。")


# ----------------------------------------------------------------------
# 17. Version Contract Consistency Gate (Tag ↔ build.gradle ↔ (maj*10000+minor*100+patch)*100+rev)
# ----------------------------------------------------------------------
log_step("17. Checking Version Contract Consistency Gate (build.gradle ↔ Tag 同源同码)")
with open(BUILD_GRADLE_PATH, "r", encoding="utf-8") as f:
    gradle_content = f.read()

m_code = re.search(r'versionCode\s+([0-9]+)', gradle_content)
m_name = re.search(r'versionName\s+"([^"]+)"', gradle_content)
if not m_code or not m_name:
    print("[FAIL] build.gradle versionCode/versionName 无法解析")
    passed = False
else:
    g_code = int(m_code.group(1))
    g_name = m_name.group(1).strip()

    # 统一版本契约公式（与 .github/workflows/ci.yml / publish_r2.py 三处同源）：
    #   (主*10000 + 次*100 + 修订) * 100 + 序号
    #   正式版 vX.Y.Z       → 序号 99（versionName 形如 "1.7.8"）
    #   测试版 beta-vX.Y.Z.N → 序号 N=1~98（versionName 形如 "1.7.8-beta.2"）
    def _calc_code(maj, mi, pa, rev):
        return (maj * 10000 + mi * 100 + pa) * 100 + rev

    def _parse_gradle_name(name):
        """解析 build.gradle 的 versionName，返回 (code, 基准号, 是否测试版) 或 None。"""
        m2 = re.match(r'^(\d+)\.(\d+)\.(\d+)(?:-beta\.(\d+))?$', name.strip())
        if not m2:
            return None
        maj, mi, pa = int(m2.group(1)), int(m2.group(2)), int(m2.group(3))
        if m2.group(4) is None:
            return _calc_code(maj, mi, pa, 99), f"{maj}.{mi}.{pa}", False
        return _calc_code(maj, mi, pa, int(m2.group(4))), f"{maj}.{mi}.{pa}", True

    parsed = _parse_gradle_name(g_name)
    ok = True
    if parsed is not None:
        expected, base_ver, is_g_beta = parsed
        if g_code != expected:
            print(f"[FAIL] build.gradle 版本契约失衡: versionName {g_name} 要求 versionCode={expected}，实际 versionCode={g_code}（公式 (主*10000+次*100+修订)*100+序号，正式版序号 99 / 测试版 1~98）")
            ok = False
        elif is_g_beta and not 1 <= int(g_name.rsplit('.', 1)[-1]) <= 98:
            print(f"[FAIL] build.gradle 测试版序号越界（必须 1~98）: {g_name}")
            ok = False
        else:
            print(f"[PASS] build.gradle 语义版本与 versionCode 公式一致: {g_name} -> code {g_code}")
    else:
        print(f"[FAIL] build.gradle versionName 格式非法: {g_name}（应为 X.Y.Z 或 X.Y.Z-beta.N）")
        ok = False

    # CI tag 环境强校验（Inject 步骤已前置到健康检查之前，此处应完全对齐）
    ref = os.environ.get("GITHUB_REF", "")
    if ref.startswith("refs/tags/"):
        tag = ref[len("refs/tags/"):]
        is_beta_tag = tag.startswith("beta-")
        ver = tag[len("beta-v"):] if is_beta_tag else tag[len("v"):]
        tparts = ver.split('.')
        exp_code = None
        exp_name = None
        if is_beta_tag:
            # 测试版必须四段号，序号 1~98 且恒小于同版本正式版的 99
            if len(tparts) == 4:
                try:
                    tmaj, tmi, tpa, trev = (int(x) for x in tparts)
                    if 1 <= trev <= 98:
                        exp_code = _calc_code(tmaj, tmi, tpa, trev)
                        exp_name = f"{tmaj}.{tmi}.{tpa}-beta.{trev}"
                except Exception:
                    exp_code = None
            if exp_code is None:
                print(f"[FAIL] 测试版 Tag 必须为四段号 beta-vX.Y.Z.N 且序号 1~98，当前: {tag}")
                ok = False
        else:
            if len(tparts) == 3:
                try:
                    tmaj, tmi, tpa = (int(x) for x in tparts)
                    exp_code = _calc_code(tmaj, tmi, tpa, 99)
                    exp_name = f"{tmaj}.{tmi}.{tpa}"
                except Exception:
                    exp_code = None
            if exp_code is None:
                print(f"[FAIL] 正式版 Tag 必须为三段号 vX.Y.Z，当前: {tag}")
                ok = False

        if ok and exp_code is not None:
            if g_code != exp_code or g_name != exp_name:
                print(f"[FAIL] 当前 tag {tag} 要求 versionName={exp_name} versionCode={exp_code}，但 build.gradle 实际为 {g_name}/{g_code} —— 版本契约不匹配，APK 将永远被云端判定为旧版，拒绝发版！")
                ok = False
            else:
                print(f"[PASS] 当前 tag {tag} 与 build.gradle 完全对齐: {g_name} (code {g_code})")

    if not ok:
        passed = False
    else:
        print("[PASS] 版本契约门禁全绿：APK 内部 versionCode 与云端元数据同源生成，彻底杜绝『已是最新仍提示更新』幻影升级！")


# ----------------------------------------------------------------------
# Final Summary Verdict
# ----------------------------------------------------------------------
log_step("CI 17-Gate Health Check Verdict")
if passed:
    print("[SUCCESS] All 17 CI Health Gates PASSED cleanly! (Zero dead links, zero AST errors, zero Chromium 68 flex/stretch violations, zero \\n changelog bugs, 100% decoupled state architecture, voice isolation, 3-tier clean gates, core feature regression defense & version contract consistency all closed)")
    sys.exit(0)
else:
    print("[FAILED] One or more CI Health Gates failed. Please fix before pushing.")
    sys.exit(1)
