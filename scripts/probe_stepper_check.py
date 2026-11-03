# 步进器溢出探针：新旧产物对比 → Link 页步进器按钮是否溢出圆角容器
# 用法：PROBE_HTML=<产物> PROBE_SHOT=<截图> python3 probe_stepper_check.py
import os, json, time, asyncio, subprocess, urllib.request, base64, websockets

PORT = 9339
HTML = os.environ.get("PROBE_HTML", "/data/hermes-runtime/workspace/unpack/vb13_inspect/assets/toolbox_ui.html")
SHOT = os.environ.get("PROBE_SHOT", "/data/hermes-runtime/workspace/shots/stepper_check.png")

def start_edge():
    p = subprocess.Popen([
        "/usr/bin/microsoft-edge", "--headless=new",
        f"--remote-debugging-port={PORT}", "--no-sandbox", "--disable-gpu",
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(30):
        time.sleep(0.5)
        try:
            urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list", timeout=1).read()
            return p
        except Exception:
            pass
    raise RuntimeError("Edge CDP 未就绪")

async def cdp_send(ws, mid, method, **params):
    await ws.send(json.dumps({"id": mid, "method": method, "params": params}))
    while True:
        msg = json.loads(await ws.recv())
        if msg.get("id") == mid:
            return msg

MEASURE_NAV = """(() => { window.__VUE_STORE__.currentNav = 'link'; return 'ok'; })()"""

MEASURE = """(() => {
  const main = document.querySelector('main');
  if (!main) return JSON.stringify({err:'no main'});
  // 步进器容器：含 加减 按钮组的 justify-between 卡片（任务4 车速阈值）
  const btns = [...main.querySelectorAll('button')].filter(b => ['+1','+5','-1','-5'].includes(b.textContent.trim()));
  if (!btns.length) return JSON.stringify({err:'no stepper buttons'});
  const mainRect = main.getBoundingClientRect();
  const out = btns.map(b => {
    const r = b.getBoundingClientRect();
    return { t: b.textContent.trim(), x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height) };
  });
  // 按钮的圆角容器 = 最近的有 rounded + border 的祖先
  const c = btns[0].closest('div[class*="justify-between"]');
  const cr = c ? c.getBoundingClientRect() : null;
  return JSON.stringify({
    main_h: Math.round(mainRect.height),
    container: cr ? { x: Math.round(cr.x), y: Math.round(cr.y), w: Math.round(cr.width), h: Math.round(cr.height) } : null,
    buttons: out,
    any_below_container: cr ? out.some(o => o.y + o.h > cr.y + cr.height + 1) : null,
    any_right_of_container: cr ? out.some(o => o.x + o.w > cr.x + cr.width + 1) : null
  });
})()"""

async def main():
    proc = start_edge()
    try:
        with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list") as r:
            targets = json.loads(r.read())
        ws_url = next(t["webSocketDebuggerUrl"] for t in targets if t.get("type") == "page")
        async with websockets.connect(ws_url, max_size=50*1024*1024) as ws:
            await cdp_send(ws, 1, "Page.enable")
            await cdp_send(ws, 2, "Emulation.setDeviceMetricsOverride", width=1920, height=720, deviceScaleFactor=1, mobile=False)
            await cdp_send(ws, 3, "Page.navigate", url=f"file://{HTML}")
            await asyncio.sleep(3)
            await cdp_send(ws, 4, "Runtime.evaluate", expression=MEASURE_NAV, returnByValue=True)
            await asyncio.sleep(0.8)
            ev = await cdp_send(ws, 5, "Runtime.evaluate", expression=MEASURE, returnByValue=True)
            if "value" not in ev.get("result", {}).get("result", {}):
                print("MEASURE 异常:", json.dumps(ev.get("result", {}).get("exceptionDetails", {}), ensure_ascii=False)[:400])
                return
            data = json.loads(ev["result"]["result"]["value"])
            print(json.dumps(data, ensure_ascii=False, indent=1))
            shot = await cdp_send(ws, 6, "Page.captureScreenshot", format="png")
            os.makedirs(os.path.dirname(SHOT), exist_ok=True)
            open(SHOT, "wb").write(base64.b64decode(shot["result"]["data"]))
            print("截图:", SHOT)
    finally:
        proc.terminate()

asyncio.run(main())
