#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
缤越助手全新 v1.7.92 博客全套实车运行图景自动生成脚本 (V2 完善版)
- 全面覆盖 5 大主菜单 + 核心 SubTabs + 重点弹窗 + 手机无线快传页
- 100% 遵循白天高对比日间车规模式
- 模拟原车实车全量 CAN 日志与硬件参数：12.6V 电瓶、1920x720 160DPI、全国油价、加油账本、专车云端应用
- 像素级精准裁剪：弹窗精准抓取卡片本体，彻底剔除外围半透明黑色遮罩与大黑边
- 主页面长图动态探测最后一个卡片元素的真实 bottom + 28px 紧凑收尾，绝无底部多余空白
"""

import os, sys, time, json, base64, asyncio, subprocess, urllib.request, websockets

OUT_DIR = "/data/projects/fuwari-blog-preview/public/att/geely"
os.makedirs(OUT_DIR, exist_ok=True)

TOOLBOX_HTML = "file:///data/projects/GeelyToolbox/app/src/main/assets/toolbox_ui.html"
MOBILE_HTML = "file:///data/projects/GeelyToolbox/app/src/main/assets/mobile_web.html"
PORT = 9298

# 获取在线 apps.json 真实专车应用
apps_json_str = "{}"
try:
    req = urllib.request.Request("https://dl.onepve.com/GeelyToolbox/apps.json", headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req, timeout=5) as r:
        apps_json_str = r.read().decode('utf-8')
except Exception as e:
    print("Fetch apps.json error:", e)

def start_edge(url):
    subprocess.run(["pkill", "-9", "-f", f"--remote-debugging-port={PORT}"], stderr=subprocess.DEVNULL)
    time.sleep(0.5)
    proc = subprocess.Popen([
        "/usr/bin/microsoft-edge",
        "--headless=new",
        f"--remote-debugging-port={PORT}",
        "--no-sandbox",
        "--disable-gpu",
        "--hide-scrollbars",
        "--window-size=1920,2000",
        url
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(2.5)
    return proc

async def get_ws():
    with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list") as resp:
        tabs = json.loads(resp.read().decode())
    page_tab = [t for t in tabs if t.get("type") == "page"][0]
    ws_url = page_tab["webSocketDebuggerUrl"]
    return await websockets.connect(ws_url, max_size=64 * 1024 * 1024)

# 真实原车模拟数据注入 JS
INJECT_MOCK_DATA_JS = """(() => {
    localStorage.setItem('has_shown_welcome_donate', 'true');
    localStorage.setItem('toolbox_first_launch_done', 'true');
    localStorage.setItem('geely_tester_unlocked', 'false');

    const store = window.__VUE_STORE__;
    if (!store) return 'store not found';

    // 1. 强制激活白天模式 + 晴空蓝调 (sky_ocean) 车规主题
    if (window.__TOOLBOX_THEMES__) {
        if (window.__TOOLBOX_THEMES__.setPalette) window.__TOOLBOX_THEMES__.setPalette('sky_ocean');
        if (window.__TOOLBOX_THEMES__.setMode) window.__TOOLBOX_THEMES__.setMode('day');
    }
    store.isNight = false;
    if (!store.theme) store.theme = {};
    store.theme.mode = 'day';
    store.theme.palette = 'sky_ocean';
    document.documentElement.classList.remove('dark');
    document.documentElement.classList.add('light');

    // 2. 注入车机硬件真实参数（绝无“读取中...”）
    store.batteryVoltage = 12.6;
    if (!store.deviceInfo) store.deviceInfo = {};
    Object.assign(store.deviceInfo, {
        version: '1.7.93',
        whitelist: true,
        multimedia_frozen: false,
        appstore_frozen: true,
        battery_volt: 126,
        real_battery_volt: 12.6,
        car_ip: '192.168.43.1',
        adb_master_switch: true,
        platform_code: 'IHU516G',
        platform_name: '吉利缤越 COOL (SX11A3)',
        is_car_device: true,
        fuel_percent: 76,
        fuel_liters: 34.2,
        screen_size: '1920x720',
        screen_real_size: '1920x720',
        screen_density: '160 dpi',
        screen_density_dpi: 160,
        screen_app_bounds: '1920x720 (无虚拟按键占位)',
        screen_wm: 'Physical size: 1920x720 · Density: 160',
        memory_usage: '2.1G / 6.0G (充足)'
    });

    // 缓存屏幕参数让 AboutModal 秒显
    try {
        localStorage.setItem('geely_toolbox_screen_info', JSON.stringify({
            size: '1920x720',
            dpi: '160 dpi',
            appBounds: '1920x720 (无虚拟按键占位)',
            wm: 'Physical size: 1920x720'
        }));
    } catch(e){}

    // 3. 注入网络与外设连接策略
    if (!store.networkDevice) store.networkDevice = {};
    Object.assign(store.networkDevice, {
        wifi_enabled: true,
        hotspot_enabled: true,
        bluetooth_enabled: true,
        wifi_policy: 1, // 默认开启
        hotspot_policy: 1, // 默认开启
        bluetooth_policy: 1,
        wifi_ssid: 'Geely-Binyue-5G',
        bluetooth_device_name: 'OnePlus 12 (已连接)'
    });

    // 4. 注入行程与加油台账真实模拟记录
    if (!store.fuelLedger) store.fuelLedger = {};
    store.fuelLedger.isLoaded = true;
    store.fuelLedger.current_odo = 18560;
    store.fuelLedger.current_year = '2026';
    store.fuelLedger.config = {
        plate_number: '浙A·8B666',
        car_id: 'SX11A3_COOL'
    };
    store.fuelLedger.stats = {
        is_calculable: true,
        avg_consumption: '6.82',
        cost_per_km: '0.53',
        total_spend: '1,280.00',
        total_liters: '163.0',
        status_desc: '数据饱满'
    };
    store.fuelLedger.monthly_stats = [
        { month: '2026-10', total_spend: 580.0, total_liters: 73.8, avg_consumption: '6.75', count: 2 },
        { month: '2026-09', total_spend: 700.0, total_liters: 89.2, avg_consumption: '6.88', count: 2 }
    ];
    store.fuelLedger.records = [
        {
            id: 'rec_01',
            date: '2026-10-22',
            brand: '中国石化',
            oilType: '92#',
            price: 7.85,
            liters: 42.0,
            amount: 329.7,
            odo: 18560,
            isFull: true,
            note: '下班顺路加满'
        },
        {
            id: 'rec_02',
            date: '2026-10-08',
            brand: '中国石油',
            oilType: '92#',
            price: 7.82,
            liters: 31.8,
            amount: 248.6,
            odo: 17945,
            isFull: true,
            note: '国庆返程加油'
        },
        {
            id: 'rec_03',
            date: '2026-09-24',
            brand: '壳牌',
            oilType: '92#',
            price: 7.85,
            liters: 45.0,
            amount: 353.2,
            odo: 17320,
            isFull: true,
            note: '长途出发前加满'
        }
    ];

    // 5. 注入油价信息与全国省份价格（消除“获取中...”）
    if (!store.oilPrice) store.oilPrice = {};
    store.oilPrice.selectedProvince = '浙江';
    store.oilPrice.favProvinces = ['浙江', '上海', '江苏', '北京'];
    if (!store.oilPrice.regionalPrices) store.oilPrice.regionalPrices = {};
    store.oilPrice.regionalPrices['浙江'] = { p92: 7.85, p95: 8.35, p98: 9.15, p0: 7.52 };
    store.oilPrice.regionalPrices['北京'] = { p92: 7.88, p95: 8.39, p98: 9.37, p0: 7.59 };
    store.oilPrice.regionalPrices['上海'] = { p92: 7.82, p95: 8.32, p98: 9.32, p0: 7.50 };
    store.oilPrice.regionalPrices['江苏'] = { p92: 7.83, p95: 8.33, p98: 9.33, p0: 7.51 };
    store.oilPrice.nextAdjustment = {
        date: '2026-10-29',
        lastAdjustmentDate: '10月11日',
        trend: 'down',
        desc: '预计每吨下调 120 元 (折合每升降约 0.10 元)',
        countdownDays: 3
    };

    // 6. 重置所有弹窗为关闭状态（保留 keys）
    if (store.modals) {
        for (const k in store.modals) {
            store.modals[k] = false;
        }
    }

    return 'mock injected successfully';
})()"""

async def capture_toolbox():
    proc = start_edge(TOOLBOX_HTML)
    try:
        ws = await get_ws()
        msg_id = 0
        async def evaluate(js):
            nonlocal msg_id
            msg_id += 1
            msg = {"id": msg_id, "method": "Runtime.evaluate",
                   "params": {"expression": js, "returnByValue": True}}
            await ws.send(json.dumps(msg))
            res = json.loads(await ws.recv())
            return res.get("result", {}).get("result", {}).get("value")

        async def capture_rect(clip, filename):
            nonlocal msg_id
            msg_id += 1
            msg = {"id": msg_id, "method": "Page.captureScreenshot",
                   "params": {
                       "format": "png",
                       "clip": {
                           "x": float(clip["x"]),
                           "y": float(clip["y"]),
                           "width": float(clip["width"]),
                           "height": float(clip["height"]),
                           "scale": 1
                       }
                   }}
            await ws.send(json.dumps(msg))
            res = json.loads(await ws.recv())
            data = base64.b64decode(res["result"]["data"])
            out_path = os.path.join(OUT_DIR, filename)
            with open(out_path, "wb") as f:
                f.write(data)
            print(f"[OK] Saved {filename} ({int(clip['width'])}x{int(clip['height'])})")
            return out_path

        # 注入 mock 真实车机环境
        res = await evaluate(INJECT_MOCK_DATA_JS)
        print("Mock Data Setup:", res)
        await asyncio.sleep(0.8)

        # 注入真实云端商城应用列表
        if apps_json_str:
            escaped = json.dumps(apps_json_str)
            await evaluate(f"if (window.applyCloudAppsJson) window.applyCloudAppsJson({escaped});")
            await asyncio.sleep(0.8)

        # -------------------------------------------------------------
        # 1. 主页面长截图：探测右侧真实卡片底部，紧凑裁剪绝无多余空白
        # -------------------------------------------------------------
        async def capture_active_view(nav_id, sub_tab_text, filename):
            await evaluate(f"""(() => {{
                const store = window.__VUE_STORE__;
                if (!store) return;
                store.currentNav = '{nav_id}';
                if ('{sub_tab_text}') {{
                    const btns = Array.from(document.querySelectorAll('button, div[role="tab"]'));
                    const targetBtn = btns.find(b => (b.textContent || '').includes('{sub_tab_text}'));
                    if (targetBtn) targetBtn.click();
                }}
            }})()""")
            await asyncio.sleep(0.6)

            # 精准探测右侧卡片以及左侧边栏的实际 bottom，杜绝 h-screen 放大
            mb = await evaluate("""(() => {
                const sec = document.querySelector('main section.flex-1');
                if (!sec) return 800;
                const activeView = Array.from(sec.children).find(el => el.offsetParent !== null && getComputedStyle(el).display !== 'none');
                if (!activeView) return 800;
                
                // 找到真正展示内容的那些卡片
                const cards = Array.from(activeView.querySelectorAll('div.rounded-2xl, div.rounded-3xl, div.border, p, table, h2, h3'));
                let maxB = 0;
                for (const el of cards) {
                    if (el.offsetParent !== null) {
                        const r = el.getBoundingClientRect();
                        // 过滤掉视口级容器
                        if (r.height < 1500 && r.width > 60 && r.bottom > maxB) {
                            maxB = r.bottom;
                        }
                    }
                }
                // 保证至少包含左侧导航栏底部的关于二维码卡片
                const aside = document.querySelector('aside');
                if (aside) {
                    const asideBottom = aside.getBoundingClientRect().bottom;
                    if (asideBottom > maxB && asideBottom < 1200) {
                        maxB = asideBottom;
                    }
                }
                return Math.round(maxB);
            })()""")

            calc_h = int(mb) + 24 if mb else 720
            if calc_h < 720: calc_h = 720
            # 宽 1920，高 calc_h 紧凑收尾
            await capture_rect({"x": 0, "y": 0, "width": 1920, "height": calc_h}, filename)

        print("\n--- 正在截取五大主导航核心功能长图（紧凑无下半部多余空白） ---")

        # 1.1 行车联动：方控按键映射手势
        await capture_active_view("link", "按键映射", "preview_wheel_full.png")
        await capture_active_view("link", "按键映射", "preview_link_full.png")

        # 1.2 行车联动：360全景联动
        await capture_active_view("link", "360全景", "preview_link_360.png")

        # 1.3 行车联动：车速与大灯
        await capture_active_view("link", "车速与大灯", "preview_link_speed.png")

        # 1.4 行车联动：网络与外设
        await capture_active_view("link", "网络与外设", "preview_link_network.png")

        # 2.1 车载声音：换挡语音播报
        await capture_active_view("body", "换挡语音", "preview_body_full.png")
        await capture_active_view("body", "换挡语音", "shot-03-body.png")

        # 2.2 车载声音：驾驶模式
        await capture_active_view("body", "驾驶模式", "preview_body_mode.png")

        # 2.3 车载声音：车门开闭播报
        await capture_active_view("body", "车门", "preview_body_door.png")

        # 2.4 车载声音：音乐音源优先级与 MAY_DUCK 功放守护
        await capture_active_view("body", "音乐音源", "preview_audio_full.png")

        # 2.5 车载声音：专属语音包
        await capture_active_view("body", "专属语音包", "preview_body_packs.png")

        # 3.1 行程油耗：全国实时油价日历与调价预警
        await capture_active_view("fuel", "实时油价日历", "preview_fuel_full.png")

        # 3.2 行程油耗：智能加油账本台账与百公里油耗分析
        await capture_active_view("fuel", "智能加油账本", "preview_fuel_record.png")

        # 4.1 应用生态：专车精选商城
        await capture_active_view("store", "精选商城", "preview_store_full.png")
        await capture_active_view("store", "精选商城", "shot-04-store.png")

        # 4.2 应用生态：特权安装通道
        await capture_active_view("store", "特权安装", "preview_install_full.png")

        # 4.3 应用生态：整车应用管理
        await capture_active_view("store", "整车应用管理", "preview_all_apps.png")

        # 5.1 系统设置：桌面胶囊悬浮（悬浮胶囊车速球、原厂时钟屏保）
        await capture_active_view("system", "桌面胶囊悬浮", "preview_system_desktop.png")

        # 5.2 系统设置：外观主题风格（6款全新自绘高质感皮肤矩阵与车规亮度滑块）
        await capture_active_view("system", "外观主题风格", "preview_system_full.png")
        await capture_active_view("system", "外观主题风格", "shot-07-system.png")

        # 5.3 系统设置：底层运维与自检
        await capture_active_view("system", "底层运维", "preview_system_ops.png")

        # -------------------------------------------------------------
        # 2. 重点模态卡片截图：精准裁切卡片本身，彻底消除遮罩大黑边
        # -------------------------------------------------------------
        print("\n--- 正在截取精美弹窗卡片（彻底剔除外围黑边） ---")

        async def capture_modal_card(modal_name, filename):
            # 打开弹窗并将外层 backdrop 背景设为全透明
            await evaluate(f"""(() => {{
                const store = window.__VUE_STORE__;
                if (!store) return;
                if (!store.modals) store.modals = {{}};
                store.modals['{modal_name}'] = true;
            }})()""")
            await asyncio.sleep(0.7)

            # 把背景遮罩变透明并探测卡片真实坐标
            rect = await evaluate("""(() => {
                // 寻找弹窗遮罩层
                const backdrops = Array.from(document.querySelectorAll('div')).filter(el => {
                    const s = window.getComputedStyle(el);
                    return s.position === 'fixed' && (s.zIndex >= 40 || s.backgroundColor.includes('rgba(0, 0, 0'));
                });
                for (const b of backdrops) {
                    b.style.backgroundColor = 'transparent';
                }

                // 找到卡片本体
                const card = document.querySelector('.tb-modal-surface.rounded-3xl') || 
                             document.querySelector('div[class*="rounded-3xl"].shadow-2xl') ||
                             document.querySelector('.tb-modal-surface');
                if (!card) return null;
                const r = card.getBoundingClientRect();
                return {
                    x: Math.round(r.left),
                    y: Math.round(r.top),
                    width: Math.round(r.width),
                    height: Math.round(r.height)
                };
            })()""")

            if rect and rect["width"] > 100 and rect["height"] > 100:
                print(f"[Modal Found] {modal_name} rect: {rect}")
                # 紧密贴合卡片边缘，不漏外边
                clip = {
                    "x": rect["x"],
                    "y": rect["y"],
                    "width": rect["width"],
                    "height": rect["height"]
                }
                await capture_rect(clip, filename)
            else:
                print(f"[WARN] Modal {modal_name} rect not found, fallback to center")
                await capture_rect({"x": 460, "y": 60, "width": 1000, "height": 600}, filename)

            # 关闭弹窗
            await evaluate(f"""(() => {{
                const store = window.__VUE_STORE__;
                if (store && store.modals) store.modals['{modal_name}'] = false;
            }})()""")
            await asyncio.sleep(0.3)

        # 2.1 关于车机参数与更新设置（1920x720 160DPI、内存占用，无读取中）
        await capture_modal_card("about", "preview_about.png")

        # 2.2 12V 蓄电池健康看板（12.6V 大字号健康充沛）
        await capture_modal_card("battery", "preview_vehicle_health.png")

        # 2.3 全国油价详情弹窗
        await capture_modal_card("oilPrice", "preview_oil_price.png")

        await ws.close()
    finally:
        proc.terminate()

async def capture_mobile():
    print("\n--- 正在截取手机无线快传长图 ---")
    proc = start_edge(MOBILE_HTML)
    try:
        ws = await get_ws()
        msg_id = 0
        async def evaluate(js):
            nonlocal msg_id
            msg_id += 1
            msg = {"id": msg_id, "method": "Runtime.evaluate",
                   "params": {"expression": js, "returnByValue": True}}
            await ws.send(json.dumps(msg))
            res = json.loads(await ws.recv())
            return res.get("result", {}).get("result", {}).get("value")

        width = 860
        target_h = 2400

        msg_id += 1
        msg = {"id": msg_id, "method": "Emulation.setDeviceMetricsOverride",
               "params": {"width": width, "height": target_h, "deviceScaleFactor": 1, "mobile": True}}
        await ws.send(json.dumps(msg))
        await ws.recv()
        await asyncio.sleep(0.8)

        # 注入 mock 示例文件并填充卡片
        await evaluate("""(() => {
            const box = document.getElementById('car-files-box');
            if (box) {
                box.innerHTML = `
                    <div style="display:flex; justify-content:space-between; align-items:center; padding:12px; border-bottom:1px solid #e2e8f0; background:rgba(255,255,255,0.8); border-radius:12px; margin-bottom:8px;">
                        <div>
                            <div style="font-weight:800; font-size:14.5px; color:#0f172a;">AutoNavi_Car_v8.5_LaneLevel.apk</div>
                            <div style="font-size:12px; color:#64748b; margin-top:2px;">88.5 MB · 高德地图车机版 (车道级导航专版)</div>
                        </div>
                        <button style="border:none; background:#2563eb; color:#fff; padding:6px 14px; border-radius:8px; font-weight:700; font-size:12.5px;">下载到手机</button>
                    </div>
                    <div style="display:flex; justify-content:space-between; align-items:center; padding:12px; border-bottom:1px solid #e2e8f0; background:rgba(255,255,255,0.8); border-radius:12px; margin-bottom:8px;">
                        <div>
                            <div style="font-weight:800; font-size:14.5px; color:#0f172a;">Paimon_Voice_Pack_v2.zip</div>
                            <div style="font-size:12px; color:#64748b; margin-top:2px;">4.2 MB · 原神派蒙车载语音提醒包</div>
                        </div>
                        <button style="border:none; background:#2563eb; color:#fff; padding:6px 14px; border-radius:8px; font-weight:700; font-size:12.5px;">下载到手机</button>
                    </div>
                `;
            }
            return 'mobile mock ok';
        })()""")
        await asyncio.sleep(0.5)

        # 探测 mobile 页面真实 bottom
        mb = await evaluate("""(() => {
            const els = Array.from(document.querySelectorAll('body *'));
            let maxB = 0;
            for (const el of els) {
                if (el.offsetParent !== null) {
                    const r = el.getBoundingClientRect();
                    if (r.width > 0 && r.height > 0 && r.bottom > maxB) {
                        maxB = r.bottom;
                    }
                }
            }
            return Math.round(maxB);
        })()""")
        calc_h = int(mb) + 28 if mb else 1380

        msg_id += 1
        msg = {"id": msg_id, "method": "Page.captureScreenshot",
               "params": {
                   "format": "png",
                   "clip": {"x": 0, "y": 0, "width": width, "height": calc_h, "scale": 1}
               }}
        await ws.send(json.dumps(msg))
        res = json.loads(await ws.recv())
        img_bytes = base64.b64decode(res["result"]["data"])
        out_path = os.path.join(OUT_DIR, "preview_mobile_transfer.png")
        with open(out_path, "wb") as f:
            f.write(img_bytes)
        print(f"[OK] Saved preview_mobile_transfer.png ({width}x{calc_h})")

        await ws.close()
    finally:
        proc.terminate()

async def main():
    print("==================================================")
    print(">> 开始全自动化截取全新 v1.7.92 博客全套实车运行图景 (V2)")
    print("==================================================")
    await capture_toolbox()
    await capture_mobile()
    print("==================================================")
    print(">> 全部截图生成完毕！")
    print("==================================================")

if __name__ == "__main__":
    asyncio.run(main())
