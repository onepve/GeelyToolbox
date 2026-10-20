#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""截取座舱联动/座舱语音/方控按键三张完整全景图（放大视口高度，让内容全量渲染后整页截图）。"""
import os, sys, time, json, base64, asyncio, subprocess, urllib.request, websockets

MEDIA_DIR = "/home/onepve/hermes-media/screenshots"
os.makedirs(MEDIA_DIR, exist_ok=True)
HTML_FILE = "file:///data/projects/GeelyToolbox/app/src/main/assets/toolbox_ui.html"
PORT = 9251

def start_edge():
    proc = subprocess.Popen([
        "/usr/bin/microsoft-edge",
        "--headless=new",
        f"--remote-debugging-port={PORT}",
        "--no-sandbox",
        "--disable-gpu",
        "--window-size=1920,3600",
        HTML_FILE
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(2.5)
    return proc

async def run():
    with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list") as resp:
        tabs = json.loads(resp.read().decode())
    page_tab = [t for t in tabs if t.get("type") == "page"][0]
    ws_url = page_tab["webSocketDebuggerUrl"]

    async with websockets.connect(ws_url, max_size=64 * 1024 * 1024) as ws:
        _id = {"v": 0}
        async def evaluate(js):
            _id["v"] += 1
            msg = {"id": _id["v"], "method": "Runtime.evaluate",
                   "params": {"expression": js, "returnByValue": True}}
            await ws.send(json.dumps(msg))
            res = json.loads(await ws.recv())
            return res.get("result", {}).get("result", {}).get("value")

        # 拉高视口，让 100vh 撑开，右侧滚动区内容全量渲染、无内部滚动
        await evaluate("""(() => {
            const store = window.__VUE_STORE__;
            if (!store) return 'no store';
            return 'store ok';
        })()""")

        targets = [("link", "车身联动"), ("body", "座舱语音"), ("wheel", "方控按键")]
        for key, name in targets:
            await evaluate(f"window.__VUE_STORE__.currentNav = '{key}'; 'ok'")
            await asyncio.sleep(0.8)

            # 读内容实际高度，动态设置设备视口为该高度 + 顶栏余量
            height = await evaluate("""
            (() => {
                const sec = document.querySelector('main section.flex-1');
                return sec ? sec.scrollHeight : 0;
            })()
            """)
            target_h = int(height) + 120 if height else 3600

            # 通过设备指标放大视口
            msg = {"id": _id["v"]+100, "method": "Emulation.setDeviceMetricsOverride",
                   "params": {"width": 1920, "height": target_h, "deviceScaleFactor": 1, "mobile": False}}
            await ws.send(json.dumps(msg))
            await ws.recv()
            await asyncio.sleep(0.8)

            msg = {"id": _id["v"]+200, "method": "Page.captureScreenshot",
                   "params": {"format": "png", "captureBeyondViewport": True}}
            await ws.send(json.dumps(msg))
            res = json.loads(await ws.recv())
            img = base64.b64decode(res["result"]["data"])
            path = os.path.join(MEDIA_DIR, f"view_{key}.png")
            with open(path, "wb") as f:
                f.write(img)
            print(f"[{name}] 内容高={height}px -> 视口设={target_h}px -> 保存 {path} ({len(img)} bytes)")

if __name__ == "__main__":
    proc = start_edge()
    try:
        asyncio.run(run())
    finally:
        proc.terminate()
