#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""可见边框密度复核：只统计「颜色不透明」的边框，避免 border-transparent 虚增。

输出每个视图：
  · 可见带边框元素数 / 其中 ≥1.6px 的重线数
  · 套娃元素数（自身有可见边框，且祖先也有可见边框）
  · 点亮态（= 主题强调色）数量，以及「内层金框 + 外层金框」同时存在的双金框处数
  · ring-* 实测渲染（box-shadow 层数），用于核实旧内核鬼影风险
"""
import os, sys, json, time, asyncio, subprocess, urllib.request, websockets

ROOT = "/data/projects/GeelyToolbox"
HTML = f"file://{ROOT}/app/src/main/assets/toolbox_ui.html"
PORT = 9336
MODE = (sys.argv[1] if len(sys.argv) > 1 else "night").lower()
PALETTE = sys.argv[2] if len(sys.argv) > 2 else "sky_ocean"
VIEWS = [("link", "行车联动"), ("body", "车载声音"), ("fuel", "行程油耗"),
         ("store", "应用生态"), ("system", "系统设置")]

JS = r"""(() => {
  const rootCS = getComputedStyle(document.documentElement);
  const accName = rootCS.getPropertyValue('--accent-gold').trim();
  const probe = document.createElement('div');
  probe.style.color = accName || '#000';
  document.body.appendChild(probe);
  const accRgb = getComputedStyle(probe).color;
  probe.remove();

  const vis = el => {
    const r = el.getBoundingClientRect();
    if (r.width < 24 || r.height < 16) return false;
    const s = getComputedStyle(el);
    return s.display !== 'none' && s.visibility !== 'hidden' && parseFloat(s.opacity) > 0.05;
  };
  const isTransparent = c => !c || c === 'transparent' || /rgba\(\s*0,\s*0,\s*0,\s*0\)/.test(c);
  const info = el => {
    const s = getComputedStyle(el);
    let maxW = 0, accent = false;
    let shadowLayers = 0;
    const bs = s.boxShadow || 'none';
    if (bs !== 'none') shadowLayers = bs.split(/,(?![^()]*\))/).length;
    const side = {};
    for (const d of ['Top','Right','Bottom','Left']) {
      const w = parseFloat(s['border'+d+'Width']) || 0;
      const c = s['border'+d+'Style'] === 'none' ? '' : s['border'+d+'Color'];
      const on = w > 0 && !isTransparent(c);
      side[d] = on;
      if (on) {
        if (w > maxW) maxW = w;
        if (c === accRgb) accent = true;
      }
    }
    // 只有成对边（左右 / 上下 / 四边）才构成「矩形轮廓」；
    // 单边（如 border-b 分割线、border-l 指示条）按设计属于分隔与强调，不算画框。
    const pairs = side.Left && side.Right;
    const tb = side.Top && side.Bottom;
    const visible = pairs || tb;
    return {maxW, visible, accent, shadowLayers};
  };
  const cls = el => (el.className || '').toString().replace(/\s+/g, ' ').slice(0, 95);

  const res = {accRgb, visible: 0, thick: 0, nested: 0, nestedThick: 0, accent: 0,
               doubleGold: 0, maxDepth: 0, shadow2: 0, ringish: 0, topCls: []};
  const all = [...document.querySelectorAll('main *')].filter(vis);
  for (const el of all) {
    const me = info(el);
    if (me.shadowLayers >= 2) res.shadow2++;
    if (!me.visible) continue;
    res.visible++;
    if (me.maxW >= 1.6) res.thick++;
    if (me.accent) res.accent++;
    let p = el.parentElement, depth = 0, goldAnc = false;
    while (p && p.tagName !== 'MAIN') {
      const pi = info(p);
      if (pi.visible && vis(p)) { depth++; if (pi.accent) goldAnc = true; }
      p = p.parentElement;
    }
    if (depth > 0) { res.nested++; if (me.maxW >= 1.6) res.nestedThick++; }
    if (depth > res.maxDepth) res.maxDepth = depth;
    if (me.accent && goldAnc) {
      res.doubleGold++;
      if (res.topCls.length < 3) res.topCls.push(cls(el));
    }
  }
  return res;
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
        await ev("localStorage.setItem('has_shown_welcome_donate','true'); 'ok'")
        await ev(f"localStorage.setItem('toolbox_theme_v2', JSON.stringify({{palette:'{PALETTE}', mode:'{MODE}'}})); 'ok'")
        await call("Page.reload", ignoreCache=True)
        await asyncio.sleep(2.5)
        print("模式=", await ev("document.documentElement.getAttribute('data-mode')"))
        await ev("(() => { if(window.__VUE_STORE__) window.__VUE_STORE__.modals={}; return 'ok'; })()")
        await asyncio.sleep(0.8)
        agg = {}
        for key, name in VIEWS:
            await ev(f"window.__VUE_STORE__.currentNav = '{key}'; 'ok'")
            await asyncio.sleep(1.1)
            d = await ev(JS)
            if not isinstance(d, dict):
                print(name, "probe failed"); continue
            print(f"{name}: 可见边框={d['visible']:3d} 重线(≥2px)={d['thick']:3d} "
                  f"套娃={d['nested']:3d}(其中重线{d['nestedThick']:2d}) "
                  f"点亮={d['accent']:2d} 双金框={d['doubleGold']:2d} 最大层深={d['maxDepth']} "
                  f"多层阴影元素={d['shadow2']:3d}")
            for k in ("visible","thick","nested","nestedThick","accent","doubleGold","shadow2"):
                agg[k] = agg.get(k, 0) + d[k]
        print("\n合计:", agg)


if __name__ == "__main__":
    proc = start_edge()
    try:
        asyncio.run(run())
    finally:
        proc.terminate()
        subprocess.run(["pkill", "-f", f"--remote-debugging-port={PORT}"], stderr=subprocess.DEVNULL)
