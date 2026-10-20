# 联动复现探针：Link 页 + toast 触发 → 验证步进器按钮溢出是否为 toast 挤压连带症状
# 用法：PROBE_HTML=<产物> PROBE_SHOT=<截图> python3 probe_link_toast_combo.py
import os, json, asyncio, subprocess, time, urllib.request, base64, websockets

PORT = 9341
HTML = os.environ.get("PROBE_HTML", "/data/hermes-runtime/workspace/unpack/vb13_inspect/assets/toolbox_ui.html")
SHOT = os.environ.get("PROBE_SHOT", "/data/hermes-runtime/workspace/shots/link_combo.png")

def start_edge():
    p = subprocess.Popen([
        "/usr/bin/microsoft-edge", "--headless=new",
        f"--remote-debugging-port={PORT}", "--no-sandbox", "--disable-gpu",
        "--window-size=1920,720", "--hide-scrollbars=false",
        f"--user-data-dir=/tmp/edge_link_combo_{PORT}", "about:blank",
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
  const main = q('main');
  const r = { vh: innerHeight, scroll_h: document.documentElement.scrollHeight };
  if (main) { const b = main.getBoundingClientRect(); r.main_h = Math.round(b.height); r.main_bottom = Math.round(b.bottom); }
  // 任务4/5 步进器：justify-between + bg-car-card + rounded-2xl + p-2.5 容器（L128/L283）
  const steppers = [...document.querySelectorAll('main div.flex.justify-between.bg-car-card')];
  const c4 = steppers[0], c5s = steppers[1];
  if (c4) {
    const cb = c4.getBoundingClientRect();
    r.c4 = { y: Math.round(cb.y), h: Math.round(cb.height), bottom: Math.round(cb.bottom) };
    r.c4_btns = [...c4.querySelectorAll('button')].map(b => {
      const bb = b.getBoundingClientRect();
      return { t: b.textContent.trim(), y: Math.round(bb.y), bottom: Math.round(bb.bottom) };
    });
    // 父卡片（bg-car-item rounded-2xl 含该步进器）
    const card = c4.closest('.rounded-3xl');
    if (card) { const cb2 = card.getBoundingClientRect(); r.c4_card_bottom = Math.round(cb2.bottom); r.c4_card_h = Math.round(cb2.height); }
  }
  // 任务5 形式按钮（h-52 w-full 组）
  const c5 = [...q('main').querySelectorAll('button')].filter(b => b.className.includes('h-[52px]') && b.className.includes('w-full'));
  r.c5_btns = c5.slice(0, 2).map(b => {
    const bb = b.getBoundingClientRect();
    return { t: b.textContent.trim().slice(0, 8), y: Math.round(bb.y), bottom: Math.round(bb.bottom) };
  });
  if (c5s) {
    const sb = c5s.getBoundingClientRect();
    r.c5s = { y: Math.round(sb.y), h: Math.round(sb.height), bottom: Math.round(sb.bottom) };
    r.c5s_btns = [...c5s.querySelectorAll('button')].map(b => {
      const bb = b.getBoundingClientRect();
      return { t: b.textContent.trim(), y: Math.round(bb.y), bottom: Math.round(bb.bottom) };
    });
  }
  const toast = q('[class*="top-8"]');
  if (toast) {
    r.toast_pos = getComputedStyle(toast).position;
    const tb = toast.getBoundingClientRect();
    r.toast_rect = { y: Math.round(tb.y), h: Math.round(tb.height), w: Math.round(tb.width) };
  }
  return JSON.stringify(r);
})()"""

def judge(tag, d):
    print(f"--- {tag} ---")
    print(f"main_h={d.get('main_h')} scroll_h={d.get('scroll_h')} (vh={d.get('vh')})")
    for key, name in (('c4', '任务4'), ('c5s', '任务5')):
        c = d.get(key)
        if c:
            out = [b for b in d.get(key + '_btns', []) if b['bottom'] > c['bottom'] + 1]
            print(f"{name}容器 bottom={c['bottom']} 按钮 bottom 超出: {[(b['t'], b['bottom']) for b in out] or '无'}")
    if d.get('c4_card_bottom'):
        print(f"任务4卡片 bottom={d['c4_card_bottom']} h={d.get('c4_card_h')} vs 步进器 bottom={d.get('c4', {}).get('bottom')} 溢出: {d['c4_card_bottom'] < d.get('c4', {}).get('bottom', 0)}")
    if d.get('toast_pos'):
        print(f"toast: pos={d['toast_pos']} rect={d.get('toast_rect')}")

async def main():
    proc = start_edge()
    try:
        with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list") as r:
            targets = json.loads(r.read())
        ws_url = next(t["webSocketDebuggerUrl"] for t in targets if t.get("type") == "page")
        async with websockets.connect(ws_url, max_size=50*1024*1024) as ws:
            await cdp_send(ws, 1, "Page.enable")
            await cdp_send(ws, 2, "Emulation.setDeviceMetricsOverride", width=int(os.environ.get("PROBE_W", 1920)), height=int(os.environ.get("PROBE_H", 720)), deviceScaleFactor=1, mobile=False)
            await cdp_send(ws, 3, "Page.navigate", url=f"file://{HTML}")
            await asyncio.sleep(3)
            await cdp_send(ws, 4, "Runtime.evaluate",
                expression="window.__VUE_STORE__.currentNav='link'; 'ok'", returnByValue=True)
            await asyncio.sleep(1)
            ev0 = await cdp_send(ws, 5, "Runtime.evaluate", expression=MEASURE, returnByValue=True)
            base = json.loads(ev0["result"]["result"]["value"]) if "value" in ev0.get("result", {}).get("result", {}) else {}
            judge("基线（无 toast）", base)
            await cdp_send(ws, 6, "Runtime.evaluate",
                expression="window.__VUE_STORE__.toast.msg='已保存'; window.__VUE_STORE__.toast.show=true; 'ok'",
                returnByValue=True)
            await asyncio.sleep(0.4)
            ev1 = await cdp_send(ws, 7, "Runtime.evaluate", expression=MEASURE, returnByValue=True)
            after = json.loads(ev1["result"]["result"]["value"]) if "value" in ev1.get("result", {}).get("result", {}) else {}
            judge("toast 显示中", after)
            os.makedirs(os.path.dirname(SHOT), exist_ok=True)
            shot = await cdp_send(ws, 8, "Page.captureScreenshot", format="png")
            open(SHOT, "wb").write(base64.b64decode(shot["result"]["data"]))
            print("截图:", SHOT)
            print()
            print("=== 结论 ===")
            b4 = [b for b in (after.get('c4_btns') or []) if after.get('c4') and b['bottom'] > after['c4']['bottom'] + 1]
            print("toast 压缩导致任务4按钮溢出容器:", bool(b4), [b['t'] for b in b4])
            b5 = [b for b in (after.get('c5s_btns') or []) if after.get('c5s') and b['bottom'] > after['c5s']['bottom'] + 1]
            print("toast 压缩导致任务5按钮溢出容器:", bool(b5), [b['t'] for b in b5])
    finally:
        proc.terminate()

asyncio.run(main())
