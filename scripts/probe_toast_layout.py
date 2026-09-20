# toast 溢出复现探针：加载 vb13 产物 → 触发 showToast → 量测布局挤压 + 截图
# 证据点：① toast computed position ② Sidebar 是否出滚动条 ③ 主区高度变化 ④ 底部截图
import os, json, time, asyncio, subprocess, urllib.request, base64, websockets

PORT = 9337
HTML = os.environ.get("PROBE_HTML", "/data/hermes-runtime/workspace/unpack/vb13_inspect/assets/toolbox_ui.html")
SHOT = os.environ.get("PROBE_SHOT", "/data/hermes-runtime/workspace/shots/toast_repro_before.png")

def start_edge():
    p = subprocess.Popen([
        "/usr/bin/microsoft-edge", "--headless=new",
        f"--remote-debugging-port={PORT}", "--no-sandbox", "--disable-gpu",
        "--window-size=1920,720", "--hide-scrollbars=false",
        "--user-data-dir=/tmp/edge_toast_repro", "about:blank",
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(30):
        time.sleep(1)
        try:
            urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list", timeout=1).read()
            return p
        except Exception:
            continue
    raise RuntimeError("Edge CDP 未就绪")

async def cdp_send(ws, _id, method, **params):
    await ws.send(json.dumps({"id": _id, "method": method, "params": params}))
    while True:
        raw = json.loads(await ws.recv(), strict=False)
        if raw.get("id") == _id:
            return raw

MEASURE = """(() => {
  const q = s => document.querySelector(s);
  const toast = q('[class*="top-8"]');
  const sidebar = document.querySelector('[class*="w-"][class*="300px"]');
  const main = q('main');
  const r = {
    vw: innerWidth, vh: innerHeight,
    scroll_h: document.documentElement.scrollHeight,
    toast_found: !!toast,
  };
  if (toast) {
    const cs = getComputedStyle(toast);
    const b = toast.getBoundingClientRect();
    r.toast_position = cs.position;
    r.toast_rect = { x: Math.round(b.x), y: Math.round(b.y), w: Math.round(b.width), h: Math.round(b.height) };
    r.toast_text = toast.textContent.trim().slice(0, 30);
  }
  if (sidebar) r.sidebar_scroll = sidebar.scrollHeight > sidebar.clientHeight + 1;
  if (main) { const mb = main.getBoundingClientRect(); r.main_h = Math.round(mb.height); }
  r.body_overflow = document.documentElement.scrollHeight > innerHeight + 1;
  return JSON.stringify(r);
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
            # 基线量测（带异常详情诊断）
            eval_base = await cdp_send(ws, 4, "Runtime.evaluate", expression=MEASURE, returnByValue=True)
            if "value" not in eval_base.get("result", {}).get("result", {}):
                print("MEASURE 异常:", json.dumps(eval_base.get("result", {}).get("exceptionDetails", {}), ensure_ascii=False)[:600])
                base = {}
            else:
                base = json.loads(eval_base["result"]["result"]["value"])
            print("基线:", json.dumps(base, ensure_ascii=False))
            # 触发切皮肤 toast（等同用户点昼夜档/配色卡）
            trig = await cdp_send(ws, 5, "Runtime.evaluate",
                expression="window.__VUE_STORE__.toast.msg='已切换「蓝」玻璃主题'; window.__VUE_STORE__.toast.show=true; 'ok'",
                returnByValue=True)
            print("触发:", trig.get("result", {}).get("result", {}).get("value"), trig.get("result", {}).get("exceptionDetails", {}).get("exception", {}).get("description", ""))
            await asyncio.sleep(0.4)
            # 触发后量测（同样容错）
            eval_after = await cdp_send(ws, 6, "Runtime.evaluate", expression=MEASURE, returnByValue=True)
            if "value" not in eval_after.get("result", {}).get("result", {}):
                print("AFTER 异常:", json.dumps(eval_after.get("result", {}).get("exceptionDetails", {}), ensure_ascii=False)[:400])
                after = {}
            else:
                after = json.loads(eval_after["result"]["result"]["value"])
            print("toast中:", json.dumps(after, ensure_ascii=False))
            # 底部截图
            os.makedirs(os.path.dirname(SHOT), exist_ok=True)
            shot = await cdp_send(ws, 7, "Page.captureScreenshot", format="png")
            open(SHOT, "wb").write(base64.b64decode(shot["result"]["data"]))
            print("截图:", SHOT)
            # 判定
            print()
            print("=== 判定 ===")
            print("toast position:", after.get("toast_position"), "(fixed=正常悬浮 / relative=被覆盖溢出)")
            if after.get("toast_rect"):
                r = after["toast_rect"]
                print(f"toast rect: y={r['y']} h={r['h']} 全宽={r['w']>=1900}")
            print("Sidebar 出滚动条:", base.get("sidebar_scroll"), "→", after.get("sidebar_scroll"))
            print("主区高度:", base.get("main_h"), "→", after.get("main_h"), f"(压矮 {base.get('main_h',0)-after.get('main_h',0)}px)")
            print("页面出现纵向溢出:", base.get("body_overflow"), "→", after.get("body_overflow"))
    finally:
        proc.terminate()

asyncio.run(main())
