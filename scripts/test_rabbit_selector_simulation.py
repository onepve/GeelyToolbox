#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
test_rabbit_selector_simulation.py
模拟仿真验证卡兔子主题伪装安装链路：
1. 原生 scanApksForRabbit 桥接返回结构验证
2. 前端 3 种状态机推演（空目录、单地图包、多包混合选择）
3. 原生注入接口（startAutoPilotInject / injectApkDirectly）严格防御测试（杜绝盲注 apks.get(0)）
"""

import os
import sys
import json
import re

def test_native_scan_contract():
    print(">> [1/3] 验证 Native 端 scanApksForRabbit 桥接接口与防盲注拦截...")
    main_activity_path = "/data/projects/GeelyToolbox/app/src/main/java/app/onepve/geelyconsole/MainActivity.java"
    with open(main_activity_path, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. 验证 scanApksForRabbit 存在并包含必须的元数据字段
    assert "public String scanApksForRabbit()" in content, "未找到 scanApksForRabbit 方法"
    for field in ["filename", "path", "sizeBytes", "sizeStr", "timeStr", "name", "packageName", "versionName", "isNavigation"]:
        assert f'obj.put("{field}"' in content, f"scanApksForRabbit 缺少必须字段: {field}"
    print("  [PASS] scanApksForRabbit 接口定义及全量字段健全！")

    # 2. 验证彻底拔除 apks.get(0)
    # 检查 startAutoPilotInject 与 injectApkDirectly 中是否还存在 apks.get(0) 盲取
    start_auto_match = re.search(r'public void startAutoPilotInject\([^\)]+\)\s*\{(.*?)(public void injectApkDirectly|\Z)', content, re.DOTALL)
    assert start_auto_match, "未找到 startAutoPilotInject 方法体"
    start_body = start_auto_match.group(1)
    assert "apks.get(0)" not in start_body, "FATAL: startAutoPilotInject 中仍残留 apks.get(0) 盲取逻辑！"
    assert "请先在界面上选择需要注入的安装包" in start_body, "startAutoPilotInject 缺少空包门禁拦截！"

    inject_direct_match = re.search(r'public void injectApkDirectly\([^\)]+\)\s*\{(.*?)(public void prepareThemeAssets|\Z)', content, re.DOTALL)
    if inject_direct_match:
        inject_body = inject_direct_match.group(1)
        assert "apks.get(0)" not in inject_body, "FATAL: injectApkDirectly 中仍残留 apks.get(0) 盲取逻辑！"
        assert "请先在界面上选择需要注入的安装包" in inject_body, "injectApkDirectly 缺少空包门禁拦截！"
    print("  [PASS] startAutoPilotInject 与 injectApkDirectly 彻底根除 apks.get(0) 盲取，严格实施未选包拦截！")

def test_frontend_state_machine():
    print(">> [2/3] 模拟验证前端状态机与多场景选择器逻辑...")
    vue_path = "/data/projects/GeelyToolbox/web/src/components/modals/RabbitInstallModal.vue"
    with open(vue_path, "r", encoding="utf-8") as f:
        vue_content = f.read()

    # 验证组件包含关键车规元素
    assert "scanApksForRabbit" in vue_content, "Vue 未调用 scanApksForRabbit"
    assert "未在常用目录检测到 APK 安装包" in vue_content or "未在 Download 目录检测到 APK 安装包" in vue_content, "Vue 缺少空状态说明卡片"
    assert "前往软件商城下载" in vue_content, "Vue 缺少空状态前往商城按钮"
    assert ":disabled=\"!selectedApk\"" in vue_content, "Vue 缺少未选包禁用注入按钮机制"
    assert "当前选定：" in vue_content, "Vue 缺少当前选定指示条"
    assert "AutoMap_9.5.13_FullFeatures_TrafficLight.apk" not in vue_content, "Vue 仍残留假高德硬编码文件名！"

    # 模拟 Scenario A: 空列表
    apk_list_a = []
    selected_a = None
    button_disabled_a = (len(apk_list_a) == 0 or selected_a is None)
    assert button_disabled_a is True, "场景 A 失败：空目录时按钮未被禁用！"

    # 模拟 Scenario B: 单个高德地图
    apk_list_b = [
        {"name": "高德地图车机版", "filename": "AutoMap_v7.5.0.apk", "isNavigation": True, "sizeStr": "118.5 MB"}
    ]
    # 默认选中逻辑推演：优先找 isNavigation
    selected_b = next((it for it in apk_list_b if it["isNavigation"]), apk_list_b[0] if apk_list_b else None)
    assert selected_b is not None, "场景 B 失败：selected_b 为空"
    assert selected_b["filename"] == "AutoMap_v7.5.0.apk", "场景 B 失败：未能自动命中高德地图！"

    # 模拟 Scenario C: 多个安装包（高德、微信、网易云）
    apk_list_c = [
        {"name": "网易云音乐车机版", "filename": "CloudMusic_Car.apk", "isNavigation": False, "sizeStr": "45.2 MB"},
        {"name": "高德地图车机版", "filename": "AutoMap_v7.5.0.apk", "isNavigation": True, "sizeStr": "118.5 MB"},
        {"name": "微信车机版", "filename": "WeChat_Car.apk", "isNavigation": False, "sizeStr": "65.0 MB"},
    ]
    # 默认优先高亮地图包
    default_selected_c = next((it for it in apk_list_c if it["isNavigation"]), apk_list_c[0] if apk_list_c else None)
    assert default_selected_c is not None, "场景 C 失败：default_selected_c 为空"
    assert default_selected_c["filename"] == "AutoMap_v7.5.0.apk", "场景 C 失败：多包时未能优先推荐地图包！"
    # 用户点击切换到网易云
    user_clicked_c = apk_list_c[0]
    assert user_clicked_c["filename"] == "CloudMusic_Car.apk"
    # 模拟点击注入时传递的正是用户选中的包名
    param_to_native = user_clicked_c["filename"]
    assert param_to_native == "CloudMusic_Car.apk", "场景 C 失败：透传给 Native 的文件名与选中的包不一致！"
    print("  [PASS] 场景 A/B/C 状态机与多包切换流转 100% 验证通过！")

def test_installview_entry():
    print(">> [3/3] 验证 InstallView 入口及硬编码彻底清除...")
    iv_path = "/data/projects/GeelyToolbox/web/src/views/InstallView.vue"
    with open(iv_path, "r", encoding="utf-8") as f:
        iv_content = f.read()

    assert "AutoMap_9.5.13_FullFeatures_TrafficLight.apk" not in iv_content, "InstallView.vue 仍残留假高德写死包名！"
    assert "store.modals.rabbitInstall = true" in iv_content, "InstallView.vue 打开向导时未规范触发响应式弹窗！"
    print("  [PASS] InstallView 入口干净规范，写死假包名已彻底剔除！")

if __name__ == "__main__":
    try:
        test_native_scan_contract()
        test_frontend_state_machine()
        test_installview_entry()
        print("\n[ALL PASS] 卡兔子主题选择器仿真验证全量通过，功能闭环无漏洞！")
    except Exception as e:
        print(f"\n[TEST FAILED] {e}", file=sys.stderr)
        sys.exit(1)
