#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""五个主视图 1920x720 真视口截图（UI 复核用）。

用法：
    python3 scripts/shot_views.py                  # 黑夜（出厂默认）
    python3 scripts/shot_views.py day              # 白天
    python3 scripts/shot_views.py day amber_gold   # 白天 + 指定配色
输出：/data/tmp/ui_audit/<view>.png / day_<view>.png
"""
import os, sys, json, time, asyncio, subprocess, urllib.request, base64, websockets

ROOT = "/data/projects/GeelyToolbox"
HTML = f"file://{ROOT}/app/src/main/assets/toolbox_ui.html"
PORT = 9341
OUT = "/data/tmp/ui_audit"
MODE = (sys.argv[1] if len(sys.argv) > 1 else "night").lower()
PALETTE = sys.argv[2] if len(sys.argv) > 2 else "sky_ocean"
THEME_KEY = "toolbox_theme_v2"
VIEWS = [("link", "行车联动"), ("body", "车载声音"), ("fuel", "行程油耗"),
         ("store", "应用生态"), ("system", "系统设置")]


def start_edge():
    subprocess.run(["pkill", "-f", f"--remote-debugging-port={PORT}"], stderr=subprocess.DEVNULL)
    time.sleep(0.5)
    proc = subprocess.Popen(["/usr/bin/microsoft-edge", "--headless=new",
                             f"--remote-debugging-port={PORT}", "--no-sandbox", "--disable-gpu",
                             "--window-size=1920,720", "--hide-scrollbars", HTML],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(40):
        try:
            urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list", timeout=1).read()
            return proc
        except Exception:
            time.sleep(0.4)
    proc.terminate()
    raise RuntimeError("Edge 未就绪")


async def run():
    os.makedirs(OUT, exist_ok=True)
    with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list") as resp:
        tabs = json.loads(resp.read().decode())
    page = [t for t in tabs if t.get("type") == "page"][0]
    async with websockets.connect(page["webSocketDebuggerUrl"], max_size=128*1024*1024) as ws:
        sid = {"v": 0}
        async def call(method, **params):
            sid["v"] += 1
            await ws.send(json.dumps({"id": sid["v"], "method": method, "params": params}))
            while True:
                msg = json.loads(await ws.recv())
                if msg.get("id") == sid["v"]:
                    return msg.get("result", {})
        async def ev(js):
            r = await call("Runtime.evaluate", expression=js, returnByValue=True)
            return r.get("result", {}).get("value")
        # 主题模式由 initTheme() 从 localStorage 读取，改完必须 reload 才会真正切昼夜
        await ev("localStorage.setItem('has_shown_welcome_donate','true'); 'ok'")
        await ev(f"localStorage.setItem('toolbox_theme_v2', JSON.stringify({{palette:'{PALETTE}', mode:'{MODE}'}})); 'ok'")
        await call("Page.reload", ignoreCache=True)
        await asyncio.sleep(2.5)
        real = await ev("document.documentElement.getAttribute('data-mode')")
        pal = await ev("document.documentElement.getAttribute('data-palette')")
        print(f"模式={real} 配色={pal}")
        await ev("(() => { if(window.__VUE_STORE__) window.__VUE_STORE__.modals={}; return 'ok'; })()")
        await asyncio.sleep(0.8)
        prefix = "" if MODE == "night" else (f"{MODE}_" if PALETTE == "sky_ocean" else f"{MODE}_{PALETTE}_")
        for key, name in VIEWS:
            await ev(f"window.__VUE_STORE__.currentNav = '{key}'; 'ok'")
            await asyncio.sleep(1.2)
            shot = await call("Page.captureScreenshot", format="png")
            path = f"{OUT}/{prefix}{key}.png"
            open(path, "wb").write(base64.b64decode(shot["data"]))
            print(path, name, os.path.getsize(path))


if __name__ == "__main__":
    proc = start_edge()
    try:
        asyncio.run(run())
    finally:
        proc.terminate()
        subprocess.run(["pkill", "-f", f"--remote-debugging-port={PORT}"], stderr=subprocess.DEVNULL)
