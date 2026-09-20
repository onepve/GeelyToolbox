#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""方控图解取证：截图「方控按键」页的悬浮图解（新按键排布）与展开图解，供人工核验。"""
import os, json, base64, time, asyncio, subprocess, urllib.request, websockets

MEDIA_DIR = "/home/onepve/hermes-media/screenshots"
os.makedirs(MEDIA_DIR, exist_ok=True)
HTML_FILE = "file:///data/projects/GeelyToolbox/app/src/main/assets/toolbox_ui.html"
PORT = 9278


def start_edge():
    p = subprocess.Popen([
        "/usr/bin/microsoft-edge", "--headless=new",
        f"--remote-debugging-port={PORT}", "--no-sandbox", "--disable-gpu",
        "--user-data-dir=/tmp/edge-profile-wheelprobe",
        "--window-size=1920,720", HTML_FILE
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(40):
        time.sleep(0.5)
        try:
            urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list", timeout=1).read()
            time.sleep(1.5)
            return p
        except Exception:
            continue
    raise RuntimeError("Edge CDP 未就绪")


async def run():
    with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list") as r:
        tabs = json.loads(r.read().decode())
    tab = [t for t in tabs if t.get("type") == "page"][0]
    async with websockets.connect(tab["webSocketDebuggerUrl"], max_size=64 * 1024 * 1024) as ws:
        _id = {"v": 0}

        async def send(method, params=None):
            _id["v"] += 1
            await ws.send(json.dumps({"id": _id["v"], "method": method, "params": params or {}}))
            return json.loads(await ws.recv())

        async def ev(js):
            r = await send("Runtime.evaluate", {"expression": js, "returnByValue": True})
            return r.get("result", {}).get("result", {}).get("value")

        async def shot(name, clip=None, scale=1):
            params = {"format": "png"}
            if clip:
                params["clip"] = {**clip, "scale": scale}
            d = (await send("Page.captureScreenshot", params))["result"]["data"]
            p = os.path.join(MEDIA_DIR, name)
            open(p, "wb").write(base64.b64decode(d))
            return p

        await asyncio.sleep(1.2)
        print(await ev("window.__VUE_STORE__.currentNav = 'wheel'; 'nav=wheel'"))
        await asyncio.sleep(1.0)
        # 悬浮图解需滚动超过 40px 才出现
        await ev("(() => { const el=[...document.querySelectorAll('*')].find(e=>e.scrollHeight>e.clientHeight+80 && e.clientHeight>300); if(!el) return 'no-scroller'; el.scrollTop=220; el.dispatchEvent(new Event('scroll')); return 'scrolled '+el.scrollHeight; })()")
        await asyncio.sleep(1.0)
        info = await ev("""(() => { const img=document.querySelector('img[alt="方控图解悬浮对照"]'); if(!img) return JSON.stringify({found:false});
          const r=img.getBoundingClientRect(); return JSON.stringify({found:true, src:img.getAttribute('src').slice(0,40), x:Math.round(r.x), y:Math.round(r.y), w:Math.round(r.width), h:Math.round(r.height)}); })()""")
        print("悬浮图解:", info)
        d = json.loads(info)
        print("整页截图:", await shot("wheel_floating_full.png"))
        if d.get("found"):
            clip = {"x": max(0, d["x"] - 8), "y": max(0, d["y"] - 8), "width": d["w"] + 16, "height": d["h"] + 16}
            print("悬浮图解特写(3x):", await shot("wheel_floating_zoom.png", clip, 3))

        # 展开图解（大图 + 按键清单）
        await ev("""(() => { const b=[...document.querySelectorAll('button')].find(x=>x.textContent.includes('展开图解')); if(b) b.click(); return b? 'expanded':'not found'; })()""")
        await asyncio.sleep(1.0)
        print("展开图解:", await ev("""(() => { const im=document.querySelector('img[alt*="SX-0017"]'); if(!im) return 'not found'; const r=im.getBoundingClientRect(); return JSON.stringify({x:Math.round(r.x),y:Math.round(r.y),w:Math.round(r.width),h:Math.round(r.height)}); })()"""))
        print("展开图解截图:", await shot("wheel_expanded_full.png"))


if __name__ == "__main__":
    proc = start_edge()
    try:
        asyncio.run(run())
    finally:
        proc.terminate()
