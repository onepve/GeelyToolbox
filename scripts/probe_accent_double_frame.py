#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""点亮态双金框探针：找出「自身是主题强调色边框，且祖先也是强调色边框」的元素。

车主反馈「被点亮状态的那个线条太扎眼」，需要区分：
  A. 该亮的（唯一一处选中指示）
  B. 重复画的（外层卡片金边 + 里面按钮金边，同一条边界画两遍）
"""
import os, json, time, asyncio, subprocess, urllib.request, websockets

ROOT = "/data/projects/GeelyToolbox"
HTML = f"file://{ROOT}/app/src/main/assets/toolbox_ui.html"
PORT = 9335
VIEWS = [("link", "行车联动"), ("body", "车载声音"), ("fuel", "行程油耗"),
         ("store", "应用生态"), ("system", "系统设置")]

JS = r"""(() => {
  const acc = getComputedStyle(document.documentElement).getPropertyValue('--accent-gold').trim();
  const probe = document.createElement('div');
  probe.style.color = acc || '#000';
  document.body.appendChild(probe);
  const accRgb = getComputedStyle(probe).color;
  probe.remove();

  const vis = el => {
    const r = el.getBoundingClientRect();
    if (r.width < 20 || r.height < 14) return false;
    const s = getComputedStyle(el);
    return s.display !== 'none' && s.visibility !== 'hidden' && parseFloat(s.opacity) > 0.05;
  };
  const borderInfo = el => {
    const s = getComputedStyle(el);
    let maxW = 0, isAcc = false;
    for (const d of ['Top','Right','Bottom','Left']) {
      const w = parseFloat(s['border'+d+'Width']) || 0;
      if (w > maxW) maxW = w;
      if (w >= 0.4 && s['border'+d+'Color'] === accRgb) isAcc = true;
    }
    return {maxW, isAcc};
  };
  const cls = el => (el.className || '').toString().replace(/\s+/g, ' ').slice(0, 105);
  const rect = el => { const r = el.getBoundingClientRect(); return Math.round(r.width)+'x'+Math.round(r.height); };

  const lit = [], doubled = [];
  for (const el of [...document.querySelectorAll('main *')].filter(vis)) {
    const me = borderInfo(el);
    if (!me.isAcc) continue;
    lit.push({w: me.maxW, size: rect(el), cls: cls(el)});
    let p = el.parentElement, found = null;
    while (p && p.tagName !== 'MAIN') {
      const pi = borderInfo(p);
      if (pi.isAcc && vis(p)) { found = {w: pi.maxW, size: rect(p), cls: cls(p)}; break; }
      p = p.parentElement;
    }
    if (found) doubled.push({inner: {w: me.maxW, size: rect(el), cls: cls(el)}, outer: found});
  }
  return {accRgb, litCount: lit.length, doubledCount: doubled.length,
          lit: lit.slice(0, 10), doubled: doubled.slice(0, 8)};
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
                print(name, "probe failed:", str(d)[:100]); continue
            print(f"\n===== {name}: 强调色={d['accRgb']} 点亮元素={d['litCount']} 其中双金框={d['doubledCount']}")
            for it in d["doubled"]:
                print(f"   [双框] 内 {it['inner']['w']:.0f}px {it['inner']['size']}  {it['inner']['cls'][:80]}")
                print(f"          外 {it['outer']['w']:.0f}px {it['outer']['size']}  {it['outer']['cls'][:80]}")
            for it in d["lit"]:
                print(f"   [点亮] {it['w']:.0f}px {it['size']}  {it['cls'][:88]}")


if __name__ == "__main__":
    proc = start_edge()
    try:
        asyncio.run(run())
    finally:
        proc.terminate()
        subprocess.run(["pkill", "-f", f"--remote-debugging-port={PORT}"], stderr=subprocess.DEVNULL)
