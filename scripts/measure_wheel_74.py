#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""量化实测：方控页「6. 右方向盘切歌映射」卡片内两张子卡的等高/溢出检测（Edge headless + CDP）。"""
import os, json, base64, asyncio, subprocess, urllib.request, websockets

MEDIA_DIR = "/home/onepve/hermes-media/screenshots"
HTML_FILE = "file:///data/projects/GeelyToolbox/app/src/main/assets/toolbox_ui.html"
PORT = 9253

def start_edge():
    proc = subprocess.Popen([
        "/usr/bin/microsoft-edge", "--headless=new",
        f"--remote-debugging-port={PORT}", "--no-sandbox", "--disable-gpu",
        "--window-size=1920,720", HTML_FILE
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(2.5)
    return proc

import time

async def run():
    with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list") as resp:
        tabs = json.loads(resp.read().decode())
    page_tab = [t for t in tabs if t.get("type") == "page"][0]
    async with websockets.connect(page_tab["webSocketDebuggerUrl"], max_size=64*1024*1024) as ws:
        _id = {"v": 0}
        async def evaluate(js):
            _id["v"] += 1
            await ws.send(json.dumps({"id": _id["v"], "method": "Runtime.evaluate",
                                      "params": {"expression": js, "returnByValue": True}}))
            res = json.loads(await ws.recv())
            return res.get("result", {}).get("result", {}).get("value")

        await evaluate("window.__VUE_STORE__.currentNav='wheel'; 'ok'")
        await asyncio.sleep(0.8)
        await evaluate("document.querySelector('main section.flex-1').scrollTop = 99999; 'ok'")
        await asyncio.sleep(0.5)

        metrics = await evaluate("""
        (() => {
          try {
            const grids = [...document.querySelectorAll('main div.grid.grid-cols-2')]
              .filter(el => el.textContent.includes('上一曲') && el.textContent.includes('下一曲'));
            // 取最内层（内部不再嵌套 grid）
            const grid = grids.find(el => !el.querySelector('div.grid.grid-cols-2'));
            if (!grid) return JSON.stringify({error: 'grid not found', grids: grids.length});
            const out = [];
            grid.querySelectorAll(':scope > div').forEach(c => {
              const r = c.getBoundingClientRect();
              out.push({ title: (c.querySelector('span')?.textContent || '').trim(),
                         h: Math.round(r.height), top: Math.round(r.top),
                         overflowX: c.scrollWidth - c.clientWidth,
                         overflowY: c.scrollHeight - c.clientHeight });
            });
            const sec = document.querySelector('main section.flex-1');
            return JSON.stringify({ cards: out, pageOverflowX: sec.scrollWidth - sec.clientWidth });
          } catch (e) { return JSON.stringify({error: String(e)}); }
        })()
        """)
        data = json.loads(metrics)
        print("子卡实测：")
        for c in data["cards"]:
            print(f"  {c['title']}  高={c['h']}px  top={c['top']}  横溢出={c['overflowX']}px  纵溢出={c['overflowY']}px")
        if len(data["cards"]) == 2:
            dh = abs(data["cards"][0]["h"] - data["cards"][1]["h"])
            dt = abs(data["cards"][0]["top"] - data["cards"][1]["top"])
            print(f"等高差={dh}px  顶对齐差={dt}px  页面横向溢出={data['pageOverflowX']}px")
            print("结论:", "PASS ✅" if dh <= 2 and dt <= 2 and data["pageOverflowX"] <= 0
                  and all(c["overflowX"] <= 0 for c in data["cards"]) else "FAIL ❌")

        msg = {"id": 900, "method": "Page.captureScreenshot",
               "params": {"format": "png", "captureBeyondViewport": True}}
        await ws.send(json.dumps(msg))
        res = json.loads(await ws.recv())
        img = base64.b64decode(res["result"]["data"])
        path = os.path.join(MEDIA_DIR, "wheel_74_swap.png")
        with open(path, "wb") as f:
            f.write(img)
        print("已保存:", path, f"({len(img)} bytes)")

if __name__ == "__main__":
    proc = start_edge()
    try:
        asyncio.run(run())
    finally:
        proc.terminate()
