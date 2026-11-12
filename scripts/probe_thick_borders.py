#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""重线清单：列出渲染宽度 ≥2px 的可见边框元素，按类名聚合，用于逐项收窄。

用法：python3 scripts/probe_thick_borders.py
"""
import json, time, asyncio, subprocess, collections, urllib.request, websockets

ROOT = "/data/projects/GeelyToolbox"
HTML = f"file://{ROOT}/app/src/main/assets/toolbox_ui.html"
PORT = 9342
VIEWS = [("link", "行车联动"), ("body", "车载声音"), ("fuel", "行程油耗"),
         ("store", "应用生态"), ("system", "系统设置")]

JS = r"""(() => {
  const vis = el => {
    const r = el.getBoundingClientRect();
    if (r.width < 20 || r.height < 14) return false;
    const s = getComputedStyle(el);
    return s.display !== 'none' && parseFloat(s.opacity) > 0.05;
  };
  const bw = el => {
    const s = getComputedStyle(el);
    let m = 0;
    for (const d of ['Top','Right','Bottom','Left']) {
      const w = parseFloat(s['border'+d+'Width']) || 0;
      const c = s['border'+d+'Color'];
      if (w > 0 && c && c !== 'transparent' && !/rgba\(\s*0,\s*0,\s*0,\s*0\)/.test(c)) m = Math.max(m, w);
    }
    return m;
  };
  const out = [];
  for (const el of [...document.querySelectorAll('main *')].filter(vis)) {
    const o = bw(el);
    if (o < 1.6) continue;
    const s = getComputedStyle(el);
    out.push({
      o, w: Math.round(el.getBoundingClientRect().width),
      h: Math.round(el.getBoundingClientRect().height),
      clickable: el.tagName === 'BUTTON' || s.cursor === 'pointer',
      cls: (el.className || '').toString().replace(/\s+/g, ' ').slice(0, 155)
    });
  }
  return out;
})()"""


def start_edge():
    subprocess.run(["pkill", "-f", f"--remote-debugging-port={PORT}"], stderr=subprocess.DEVNULL)
    time.sleep(0.5)
    proc = subprocess.Popen(["/usr/bin/microsoft-edge", "--headless=new",
                             f"--remote-debugging-port={PORT}", "--no-sandbox", "--disable-gpu",
                             "--window-size=1920,720", HTML],
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
    with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list") as resp:
        tabs = json.loads(resp.read().decode())
    page = [t for t in tabs if t.get("type") == "page"][0]
    agg, views = collections.Counter(), {}
    async with websockets.connect(page["webSocketDebuggerUrl"], max_size=64*1024*1024) as ws:
        sid = {"v": 0}
        async def ev(js):
            sid["v"] += 1
            await ws.send(json.dumps({"id": sid["v"], "method": "Runtime.evaluate",
                                      "params": {"expression": js, "returnByValue": True}}))
            return json.loads(await ws.recv()).get("result", {}).get("result", {}).get("value")
        await ev("localStorage.setItem('has_shown_welcome_donate','true'); 'ok'")
        await ev("(() => { if(window.__VUE_STORE__) window.__VUE_STORE__.modals={}; return 'ok'; })()")
        await asyncio.sleep(0.8)
        for key, name in VIEWS:
            await ev(f"window.__VUE_STORE__.currentNav = '{key}'; 'ok'")
            await asyncio.sleep(1.1)
            rows = await ev(JS) or []
            print(f"{name}: 重线 {len(rows)} 处")
            for r in rows:
                k = r["cls"]
                agg[k] += 1
                views.setdefault(k, (name, r))
    print(f"\n合计重线 {sum(agg.values())} 处 / {len(agg)} 种\n")
    for k, c in agg.most_common():
        name, r = views[k]
        tag = "可点" if r["clickable"] else "静态"
        print(f"x{c:<2} {r['w']}x{r['h']} {r['o']:.0f}px {tag} @{name}\n    {k}")


if __name__ == "__main__":
    proc = start_edge()
    try:
        asyncio.run(run())
    finally:
        proc.terminate()
        subprocess.run(["pkill", "-f", f"--remote-debugging-port={PORT}"], stderr=subprocess.DEVNULL)
