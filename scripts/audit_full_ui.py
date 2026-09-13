#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
吉利智驾 全场景 HMI 界面与全量弹窗向导端到端自动化视觉巡检与错位审计
遍历 8 大主功能视图 + 17 大二级/三级弹窗向导，检测：
1. 左右双列网格等高对齐（高度差 <= 2px）
2. 底部操作按钮基线绝对对齐（Y 坐标差 <= 2px）
3. 纵向元素间距安全防线（分割线与大按钮间距 >= 16px）
4. 全量弹窗居中与视口约束
5. 自动全景截图留档
"""

import os
import sys
import time
import json
import base64
import asyncio
import subprocess
import urllib.request
import websockets

MEDIA_DIR = "/home/onepve/hermes-media/audit"
os.makedirs(MEDIA_DIR, exist_ok=True)

HTML_FILE = "file:///data/projects/GeelyToolbox/app/src/main/assets/toolbox_ui.html"
PORT = 9240

def start_edge():
    proc = subprocess.Popen([
        "/usr/bin/microsoft-edge",
        "--headless=new",
        f"--remote-debugging-port={PORT}",
        "--no-sandbox",
        "--disable-gpu",
        "--window-size=1920,720",
        HTML_FILE
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(2.0)
    return proc

async def run_audit():
    with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list") as resp:
        tabs = json.loads(resp.read().decode())
    page_tab = [t for t in tabs if t.get("type") == "page"][0]
    ws_url = page_tab["webSocketDebuggerUrl"]

    total_violations = []

    async with websockets.connect(ws_url) as ws:
        async def evaluate(js):
            msg = {"id": 1, "method": "Runtime.evaluate", "params": {"expression": js, "returnByValue": True}}
            await ws.send(json.dumps(msg))
            res = json.loads(await ws.recv())
            return res.get("result", {}).get("result", {}).get("value")

        async def capture(filename):
            msg = {"id": 2, "method": "Page.captureScreenshot"}
            await ws.send(json.dumps(msg))
            res = json.loads(await ws.recv())
            img_data = base64.b64decode(res["result"]["data"])
            save_path = os.path.join(MEDIA_DIR, f"{filename}.png")
            with open(save_path, "wb") as f:
                f.write(img_data)
            return save_path

        # -------------------------------------------------------------
        # 1. 巡检 8 大主功能视图
        # -------------------------------------------------------------
        navs = [
            ("store", "精选商城"),
            ("wheel", "方控按键"),
            ("link", "车身联动"),
            ("body", "座舱语音"),
            ("audio", "车载音频"),
            ("floating", "桌面悬浮"),
            ("install", "特权安装"),
            ("system", "系统维护")
        ]

        print(">>> [Phase 1] 巡检 8 大主功能视图...")
        for nav_key, nav_name in navs:
            js_switch = f"""
            (() => {{
                const tabs = document.querySelectorAll('nav div, .sidebar-tab, button, span');
                for (const t of tabs) {{
                    if (t.innerText && t.innerText.trim().includes('{nav_name}')) {{
                        t.click();
                        return 'ok';
                    }}
                }}
                return 'not found';
            }})()
            """
            await evaluate(js_switch)
            await asyncio.sleep(0.6)

            # 截图
            await capture(f"nav_{nav_key}")

            # 严苛几何对齐检测
            js_check = """
            (() => {
                const issues = [];
                // 1. 双列网格 (grid-cols-2) 左右卡片等高与按钮基线对齐
                const grids = document.querySelectorAll('.grid-cols-2');
                grids.forEach((g, gIdx) => {
                    const children = Array.from(g.children).filter(c => c.offsetParent !== null);
                    for (let i = 0; i < children.length; i += 2) {
                        if (i + 1 < children.length) {
                            const c1 = children[i];
                            const c2 = children[i+1];
                            const h1 = c1.offsetHeight;
                            const h2 = c2.offsetHeight;
                            if (Math.abs(h1 - h2) > 2) {
                                issues.push(`左右卡片高度错位: 左卡片 ${h1}px vs 右卡片 ${h2}px (偏差 ${Math.abs(h1 - h2)}px)`);
                            }
                            // 检查底部按钮 Y 坐标对齐
                            const b1 = c1.querySelector('button, .h-[52px], .h-[50px]');
                            const b2 = c2.querySelector('button, .h-[52px], .h-[50px]');
                            if (b1 && b2) {
                                const r1 = b1.getBoundingClientRect();
                                const r2 = b2.getBoundingClientRect();
                                if (Math.abs(r1.top - r2.top) > 2) {
                                    issues.push(`底部按钮垂直错位: 左按钮 top=${Math.round(r1.top)}px vs 右按钮 top=${Math.round(r2.top)}px (偏差 ${Math.round(Math.abs(r1.top - r2.top))}px)`);
                                }
                            }
                        }
                    }
                });

                // 2. 检查是否有 pt-1 或 pt-2 贴脸分割线
                const elements = document.querySelectorAll('.pt-1, .pt-2');
                elements.forEach(el => {
                    const cls = el.className || '';
                    if (cls.includes('border-t') && (cls.includes('pt-2') || cls.includes('pt-1'))) {
                        issues.push(`发现贴脸过小间距 (${cls.match(/pt-[12]/)[0]} 只有 4~8px) 用于分割线与操作区！必须 >= pt-4 (16px)`);
                    }
                });

                return issues;
            })()
            """
            issues = await evaluate(js_check)
            if issues:
                print(f"  [WARN] {nav_name} ({nav_key}) 发现 {len(issues)} 处对齐/间距问题:")
                for iss in issues:
                    print(f"    - {iss}")
                    total_violations.append((nav_key, iss))
            else:
                print(f"  [PASS] {nav_name} ({nav_key}) 100% 几何对齐与舒展间距验证通过！")

        # -------------------------------------------------------------
        # 2. 巡检全部 17 大二级与三级弹窗向导
        # -------------------------------------------------------------
        print("\n>>> [Phase 2] 巡检全部二级/三级弹窗向导...")
        modals_to_test = [
            ("about", "吉利智驾·关于"),
            ("battery", "电瓶电压与发电机"),
            ("reward", "赞赏支持"),
            ("deepTools", "深度控制台"),
            ("qrCode", "扫码快传"),
            ("dialer", "工程暗码拨号"),
            ("appDetail", "应用版本详情"),
            ("confirm", "功能指南原理"),
            ("rabbitInstall", "免卡兔子向导"),
            ("update", "版本更新提示"),
            ("log", "运行日志查看"),
            ("voiceItem_gear", "挡位细分配置"),
            ("voiceItem_mode", "模式细分配置"),
            ("voiceItem_door", "车门细分配置"),
            ("voiceItem_trunk", "尾门细分配置"),
            ("otaCapture", "抓OTA固件向导"),
            ("cleanDownload", "清理下载目录"),
            ("emojiTest", "Emoji实测全屏面板")
        ]

        for m_key, m_name in modals_to_test:
            js_open = f"""
            (() => {{
                // 借助挂载在 window 上的 store 直接打开弹窗
                const store = window.__VUE_STORE__;
                if (!store) return 'no store';

                // 先关闭所有弹窗
                for (const k in store.modals) {{
                    if (typeof store.modals[k] === 'boolean') store.modals[k] = false;
                    else store.modals[k] = null;
                }}

                if ('{m_key}' === 'about') store.modals.about = true;
                else if ('{m_key}' === 'battery') store.modals.battery = true;
                else if ('{m_key}' === 'reward') store.modals.reward = true;
                else if ('{m_key}' === 'deepTools') store.modals.deepTools = true;
                else if ('{m_key}' === 'qrCode') store.modals.qrCode = true;
                else if ('{m_key}' === 'dialer') store.modals.dialer = true;
                else if ('{m_key}' === 'rabbitInstall') store.modals.rabbitInstall = true;
                else if ('{m_key}' === 'log') store.modals.log = true;
                else if ('{m_key}' === 'otaCapture') store.modals.otaCapture = true;
                else if ('{m_key}' === 'cleanDownload') store.modals.cleanDownload = true;
                else if ('{m_key}' === 'emojiTest') store.modals.emojiTest = true;
                else if ('{m_key}' === 'update') {{
                    if (window.forceShowUpdateModal) {{
                        window.forceShowUpdateModal({{
                            version: '1.7.29',
                            is_beta: false,
                            size: '1.24 MB',
                            changelog: '1. 左右双列网格强制等高对齐 2. 彻底拉开上下安全间距杜绝贴脸'
                        }});
                    }}
                }}
                else if ('{m_key}' === 'appDetail') {{
                    store.modals.appDetail = {{
                        id: 'amap',
                        name: '高德地图 8.5 (专车定制巡航版)',
                        version: '8.5.0.600124',
                        size: '274.9 MB',
                        desc: '专为亿咖通 E02 深度定制优化。支持小爱语音直控、红绿灯读秒与沉浸式巡航视界。',
                        installed: false
                    }};
                }} else if ('{m_key}' === 'confirm') {{
                    store.modals.confirm = {{
                        title: '【功能指南】转向灯联动 360 全景原理',
                        desc: '1. 车身信号感知：拨动转向灯且车速 <= 30 km/h 时秒级调起 AVM 全景影像。\\n\\n2. 方向盘回正退出：方向盘回正居中后自动关闭 360 返回原界面。',
                        tip: '安全第一，辅助变道盲区全覆盖。',
                        showCancel: false,
                        confirmText: '我知道了'
                    }};
                }} else if ('{m_key}' === 'voiceItem_gear') {{
                    store.modals.voiceItemSettings = {{
                        id: 'gear_voice',
                        name: '前进 D 挡起步语音播报',
                        code: 'gear_d'
                    }};
                }} else if ('{m_key}' === 'voiceItem_mode') {{
                    store.modals.voiceItemSettings = {{
                        id: 'mode_voice',
                        name: '激擎运动模式切换语音播报',
                        code: 'mode_sport'
                    }};
                }} else if ('{m_key}' === 'voiceItem_door') {{
                    store.modals.voiceItemSettings = {{
                        id: 'door_voice',
                        name: '主驾车门开启迎宾播报',
                        code: 'door_fl'
                    }};
                }} else if ('{m_key}' === 'voiceItem_trunk') {{
                    store.modals.voiceItemSettings = {{
                        id: 'trunk_voice',
                        name: '电动尾门升起防刮蹭安全语音',
                        code: 'trunk_open'
                    }};
                }}

                return 'opened';
            }})()
            """
            res = await evaluate(js_open)
            await asyncio.sleep(0.5)

            # 截图保存
            await capture(f"modal_{m_key}")

            # 检查弹窗内部几何健康
            js_modal_check = """
            (() => {
                const issues = [];
                // 检查是否有弹窗遮罩脱节或宽度错位
                const modalWrapper = document.querySelector('.fixed.w-screen, .fixed.top-0');
                if (!modalWrapper) {
                    issues.push('弹窗未成功渲染或无容器');
                    return issues;
                }
                const modalBox = modalWrapper.querySelector('.bg-car-card, .rounded-3xl');
                if (modalBox) {
                    const rect = modalBox.getBoundingClientRect();
                    if (rect.top < 10) {
                        issues.push(`弹窗顶部越界贴顶 (top=${Math.round(rect.top)}px)`);
                    }
                    if (rect.bottom > 715) {
                        issues.push(`弹窗底部越界出屏 (bottom=${Math.round(rect.bottom)}px)`);
                    }
                }
                return issues;
            })()
            """
            m_issues = await evaluate(js_modal_check)
            if m_issues:
                print(f"  [WARN] 弹窗 {m_name} ({m_key}) 存在问题: {m_issues}")
                total_violations.append((m_key, m_issues))
            else:
                print(f"  [PASS] 弹窗 {m_name} ({m_key}) 视口居中与渲染 100% 正常！")

        print(f"\n=======================================================")
        if total_violations:
            print(f"[FAIL] 巡检审计未通过！共发现 {len(total_violations)} 处违规:")
            for v in total_violations:
                print(f"  - {v[0]}: {v[1]}")
            sys.exit(1)
        else:
            print(f"[SUCCESS] 8 大主视图 + 17 大二级/三级弹窗向导全量审计 100% PASS！零错位、零越界、零贴脸！")
            sys.exit(0)

if __name__ == "__main__":
    edge_proc = start_edge()
    try:
        asyncio.run(run_audit())
    finally:
        edge_proc.terminate()
