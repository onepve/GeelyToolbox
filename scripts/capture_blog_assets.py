#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成缤越助手 10 张核心博客长截图（白天日间模式、版本号 1.7.50、状态栏饱满、像素级精确裁切零空白）
"""
import os, sys, time, json, base64, asyncio, subprocess, urllib.request, websockets

OUT_DIR = "/home/onepve/hermes-media/blog-screenshots"
os.makedirs(OUT_DIR, exist_ok=True)
TOOLBOX_HTML = "file:///data/projects/GeelyToolbox/app/src/main/assets/toolbox_ui.html"
MOBILE_HTML = "file:///data/projects/GeelyToolbox/app/src/main/assets/mobile_web.html"
PORT = 9298

# 获取在线 apps.json
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
        "--window-size=1920,4000",
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

        # 1. 切换到白天高对比模式并注入真实状态栏与版本号
        res_setup = await evaluate("""(() => {
            const btns = document.querySelectorAll('header button');
            if (btns.length > 0 && window.__VUE_STORE__.isNight) {
                btns[0].click(); // 切换为日间模式
            }
            const store = window.__VUE_STORE__;
            if (store) {
                if (!store.deviceInfo) store.deviceInfo = {};
                store.deviceInfo.version = '1.7.50';
                store.deviceInfo.appstore_frozen = true;
                store.deviceInfo.wifi_ap = true;
                store.batteryVoltage = '12.6V';
                store.oilPrice = '92# 7.85';
                if (store.modals) {
                    store.modals.welcomeDonate = false;
                }
            }
            localStorage.setItem('has_shown_welcome_donate', 'true');
            return {
                bg: getComputedStyle(document.body).backgroundColor,
                mode: store ? store.theme.mode : null,
                isNight: store ? store.isNight : null
            };
        })()""")
        print("Toolbox Day Mode Setup:", res_setup)
        await asyncio.sleep(0.8)

        # 2. 注入云端应用列表
        if apps_json_str:
            escaped = json.dumps(apps_json_str)
            await evaluate(f"window.applyCloudAppsJson({escaped});")
            await asyncio.sleep(1.0)

        # 3. 截取 7 大主功能长图
        views = [
            ("wheel", "preview_wheel_full.png"),
            ("link", "preview_link_full.png"),
            ("body", "preview_body_full.png"),
            ("store", "preview_store_full.png"),
            ("audio", "preview_audio_full.png"),
            ("install", "preview_install_full.png"),
            ("system", "preview_system_full.png")
        ]

        for nav_key, filename in views:
            await evaluate(f"""(() => {{
                window.__VUE_STORE__.currentNav = '{nav_key}';
            }})()""")
            await asyncio.sleep(0.5)

            # 探测当前激活视图的真实 bottom
            mb = await evaluate("""(() => {
                const sec = document.querySelector('main section.flex-1');
                if (!sec) return 1600;
                const activeView = Array.from(sec.children).find(el => el.offsetParent !== null && getComputedStyle(el).display !== 'none');
                if (!activeView) return 1600;
                let maxB = 0;
                const els = Array.from(activeView.querySelectorAll('*'));
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
            
            calc_h = int(mb) + 32 if mb else 1600
            print(f"Target {filename}: calculated height = {calc_h}")

            msg_id += 1
            msg = {"id": msg_id, "method": "Page.captureScreenshot",
                   "params": {
                       "format": "png",
                       "clip": {"x": 0, "y": 0, "width": 1920, "height": calc_h, "scale": 1}
                   }}
            await ws.send(json.dumps(msg))
            res = json.loads(await ws.recv())
            img_bytes = base64.b64decode(res["result"]["data"])
            out_path = os.path.join(OUT_DIR, filename)
            with open(out_path, "wb") as f:
                f.write(img_bytes)
            print(f"Saved {filename} ({1920}x{calc_h})")

        # 4. 截取 2 大弹窗（1920x720 视口）
        modals = [
            ("oilPrice", "preview_oil_price.png"),
            ("about", "preview_about.png")
        ]
        for m_key, filename in modals:
            await evaluate(f"window.openModal('{m_key}');")
            await asyncio.sleep(0.6)
            msg_id += 1
            msg = {"id": msg_id, "method": "Page.captureScreenshot",
                   "params": {
                       "format": "png",
                       "clip": {"x": 0, "y": 0, "width": 1920, "height": 720, "scale": 1}
                   }}
            await ws.send(json.dumps(msg))
            res = json.loads(await ws.recv())
            img_bytes = base64.b64decode(res["result"]["data"])
            out_path = os.path.join(OUT_DIR, filename)
            with open(out_path, "wb") as f:
                f.write(img_bytes)
            print(f"Saved modal {filename} (1920x720)")
            await evaluate(f"window.closeModal('{m_key}');")
            await asyncio.sleep(0.3)

        await ws.close()
    finally:
        proc.terminate()

async def capture_mobile():
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
                    <div style="display:flex; justify-content:space-between; align-items:center; padding:10px; border-bottom:1px solid #f1f5f9;">
                        <div>
                            <div style="font-weight:600; font-size:13px; color:#1e293b;">AutoNavi_8.5_Custom.apk</div>
                            <div style="font-size:11px; color:#64748b;">85.2 MB · 刚刚更新</div>
                        </div>
                        <button class="btn btn-secondary" style="width:auto; padding:4px 8px; font-size:11px;">下载</button>
                    </div>
                    <div style="display:flex; justify-content:space-between; align-items:center; padding:10px;">
                        <div>
                            <div style="font-weight:600; font-size:13px; color:#1e293b;">welcome_voice.mp3</div>
                            <div style="font-size:11px; color:#64748b;">380 KB · 10分钟前</div>
                        </div>
                        <button class="btn btn-secondary" style="width:auto; padding:4px 8px; font-size:11px;">下载</button>
                    </div>
                `;
            }
            return 'mock injected';
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
        print(f"Mobile target height: {calc_h}")

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
        print(f"Saved preview_mobile_transfer.png ({width}x{calc_h})")

        await ws.close()
    finally:
        proc.terminate()

async def main():
    print("=== Capturing 9 Views/Modals ===")
    await capture_toolbox()
    print("=== Capturing Mobile ===")
    await capture_mobile()
    print("=== All 10 screenshots generated successfully! ===")

if __name__ == "__main__":
    asyncio.run(main())
