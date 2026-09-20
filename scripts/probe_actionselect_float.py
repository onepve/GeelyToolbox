#!/usr/bin/env python3
"""验证 ActionSelect 向上浮动面板 + 键字移除：wheel 页截图，用完强杀模拟器（try/finally 必杀）"""
import asyncio, json, os, signal, subprocess, sys, time, base64
import urllib.request
import websockets

HTML = os.environ.get("PROBE_HTML", "/data/projects/GeelyToolbox/web/dist/index.html")
PORT = int(os.environ.get("PROBE_PORT", "9345"))
SHOT = os.environ.get("PROBE_SHOT", "/data/hermes-runtime/workspace/shots/actionselect_float.png")

proc = None

def kill_edge():
    global proc
    if proc:
        try: proc.terminate()
        except Exception: pass
        try: proc.wait(timeout=3)
        except Exception:
            try: proc.kill()
            except Exception: pass
        proc = None
    # 兜底：按 user-data-dir 特征清理本探针的 edge
    subprocess.run(["bash", "-c",
        "ps aux | grep 'edge_actionselect_float' | grep -v grep | awk '{print $2}' | xargs -r kill -9"],
        capture_output=True)

async def cdp_send(ws, mid, method, **params):
    await ws.send(json.dumps({"id": mid, "method": method, "params": params}))
    while True:
        msg = json.loads(await ws.recv())
        if msg.get("id") == mid:
            return msg

async def main():
    global proc
    print("[1] 启动 Edge...", flush=True)
    proc = subprocess.Popen([
        "/usr/bin/microsoft-edge", "--headless=new",
        f"--remote-debugging-port={PORT}", "--no-sandbox", "--disable-gpu",
        "--window-size=1920,720", "--hide-scrollbars=false",
        f"--user-data-dir=/tmp/edge_actionselect_float_{int(time.time())}", "about:blank",
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for i in range(30):
        time.sleep(1)
        try:
            urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list", timeout=1).read()
            print(f"[1] Edge 就绪 ({i+1}s)", flush=True)
            break
        except Exception:
            continue
    else:
        raise RuntimeError("Edge CDP 未就绪")

    targets = json.loads(urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list", timeout=2).read())
    ws_url = next(t["webSocketDebuggerUrl"] for t in targets if t.get("type") == "page")
    print("[2] 连接 WS...", flush=True)
    async with websockets.connect(ws_url, max_size=50*1024*1024) as ws:
        await cdp_send(ws, 1, "Page.enable")
        await cdp_send(ws, 2, "Emulation.setDeviceMetricsOverride", width=1920, height=720, deviceScaleFactor=1, mobile=False)
        print("[3] 加载页面...", flush=True)
        await cdp_send(ws, 3, "Page.navigate", url=f"file://{HTML}")
        await asyncio.sleep(3)
        print("[4] 切 wheel 页...", flush=True)
        r = await cdp_send(ws, 4, "Runtime.evaluate", expression="window.__VUE_STORE__ ? (window.__VUE_STORE__.currentNav='wheel', 'ok') : 'no-store'", returnByValue=True)
        print("    ->", r.get("result", {}).get("result", {}).get("value"), flush=True)
        await asyncio.sleep(1.2)
        print("[5] 点开 ActionSelect...", flush=True)
        r = await cdp_send(ws, 5, "Runtime.evaluate", expression="""
          (() => {
            const btns = [...document.querySelectorAll('main button')].filter(b => [...b.querySelectorAll('span')].some(s => s.textContent.trim() === '▾'));
            if (!btns.length) return 'no-btn';
            btns[0].scrollIntoView({block: 'center'});
            btns[0].click();
            return 'clicked:' + btns[0].textContent.trim().slice(0, 25);
          })()
        """, returnByValue=True)
        print("    ->", r.get("result", {}).get("result", {}).get("value"), flush=True)
        await asyncio.sleep(0.8)
        print("[6] 量测面板方向...", flush=True)
        r = await cdp_send(ws, 6, "Runtime.evaluate", expression="""
          (() => {
            const panels = [...document.querySelectorAll('div.absolute.z-50')].filter(d => d.offsetParent !== null);
            if (!panels.length) return JSON.stringify({panel: false});
            const p = panels[panels.length-1];
            const pr = p.getBoundingClientRect();
            const btn = p.parentElement.querySelector('button');
            const br = btn ? btn.getBoundingClientRect() : null;
            return JSON.stringify({
              panel: true,
              panel_top: Math.round(pr.top), panel_bottom: Math.round(pr.bottom), panel_h: Math.round(pr.height),
              btn_top: br ? Math.round(br.top) : null, btn_bottom: br ? Math.round(br.bottom) : null,
              upward: br ? (pr.bottom <= br.top + 2) : null,
              in_viewport: pr.top >= 0 && pr.bottom <= innerHeight,
              vh: innerHeight,
              options: p.querySelectorAll('button').length
            });
          })()
        """, returnByValue=True)
        meas = r.get("result", {}).get("result", {}).get("value")
        print("    量测:", meas, flush=True)
        print("[7] 校验键字移除...", flush=True)
        r = await cdp_send(ws, 7, "Runtime.evaluate", expression="""
          (() => {
            const txt = document.body.innerText;
            return JSON.stringify({
              has_7: txt.includes('⑦ 上一曲') && !txt.includes('⑦ 上一曲键'),
              has_4: txt.includes('④ 下一曲') && !txt.includes('④ 下一曲键'),
              old_7: txt.includes('⑦ 上一曲键'), old_4: txt.includes('④ 下一曲键')
            });
          })()
        """, returnByValue=True)
        print("    ->", r.get("result", {}).get("result", {}).get("value"), flush=True)
        print("[8] 截图...", flush=True)
        shot = await cdp_send(ws, 8, "Page.captureScreenshot", format="png")
        data = shot.get("result", {}).get("data")
        if data:
            with open(SHOT, "wb") as f:
                f.write(base64.b64decode(data))
            print("    截图:", SHOT, flush=True)
        else:
            print("    截图失败:", json.dumps(shot.get("result", {}))[:200], flush=True)

try:
    asyncio.run(main())
finally:
    kill_edge()
    print("[9] Edge 已强杀清理", flush=True)
