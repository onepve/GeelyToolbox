#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""套娃定位探针：找出「外层卡片已有边框，内部又有独立边框」的组合，列出具体类名与尺寸。

只输出定位证据，不改代码。
"""
import os, json, time, asyncio, subprocess, urllib.request, websockets

ROOT = "/data/projects/GeelyToolbox"
HTML = f"file://{ROOT}/app/src/main/assets/toolbox_ui.html"
PORT = 9334
VIEWS = [("link", "行车联动"), ("body", "车载声音"), ("fuel", "行程油耗"),
         ("store", "应用生态"), ("system", "系统设置")]

JS = r"""(() => {
  const vis = el => {
    const r = el.getBoundingClientRect();
    if (r.width < 30 || r.height < 18) return false;
    const s = getComputedStyle(el);
    return s.display !== 'none' && s.visibility !== 'hidden' && parseFloat(s.opacity) > 0.05;
  };
  const bw = el => {
    const s = getComputedStyle(el);
    return Math.max(...['Top','Right','Bottom','Left'].map(d => parseFloat(s['border'+d+'Width']) || 0));
  };
  const w = el => Math.round(el.getBoundingClientRect().width);
  const h = el => Math.round(el.getBoundingClientRect().height);
  const cls = el => (el.className || '').toString().replace(/\s+/g, ' ').slice(0, 110);
  const rows = [];
  for (const el of [...document.querySelectorAll('main *')].filter(vis)) {
    const own = bw(el);
    if (own < 0.4) continue;
    let p = el.parentElement, depth = 0, outer = null;
    while (p && p.tagName !== 'MAIN') {
      if (bw(p) > 0.4 && vis(p)) { depth++; if (!outer) outer = p; }
      p = p.parentElement;
    }
    if (depth === 0) continue;
    rows.push({depth, own, w: w(el), h: h(el), cls: cls(el), outer: cls(outer), tag: el.tagName});
  }
  // 按「自身边框粗细」分组统计，看有多少是 2px 重线
  const stat = {};
  for (const r of rows) {
    const k = r.own >= 1.6 ? '2px' : '1px';
    stat[k] = (stat[k] || 0) + 1;
  }
  return {nestedRows: rows, stat, totalNested: rows.length};
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
            d = await ev(JS)
            if not isinstance(d, dict):
                print(name, "probe failed"); continue
            print(f"\n===== {name} 套娃元素 {d['totalNested']} 个 {d['stat']}")
            seen = {}
            for r in d["nestedRows"]:
                k = (r["cls"][:60], r["own"])
                seen.setdefault(k, []).append(r)
            for k, v in sorted(seen.items(), key=lambda kv: -len(kv[1]))[:6]:
                r = v[0]
                print(f"  x{len(v):2d} own={r['own']:.0f}px {r['w']}x{r['h']} depth={r['depth']}")
                print(f"       内: {r['cls'][:100]}")
                print(f"       外: {r['outer'][:100]}")


if __name__ == "__main__":
    proc = start_edge()
    try:
        asyncio.run(run())
    finally:
        proc.terminate()
        subprocess.run(["pkill", "-f", f"--remote-debugging-port={PORT}"], stderr=subprocess.DEVNULL)
