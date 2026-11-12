#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""框套框定位（B 档改造施工图）：只报「外层是纯容器（非按钮/非可点）、内层还有可见边框」的组合。

判定：外层元素自身没有 cursor-pointer / 不是 BUTTON / 没有 @click（运行时用 getEventListeners 不可靠，
改用计算样式 cursor 与标签名 + 是否有 role），且内部至少有一个可见边框后代。
按「外层类名」聚合计数，直接给出要改的施工清单。
"""
import os, json, time, asyncio, subprocess, urllib.request, websockets

ROOT = "/data/projects/GeelyToolbox"
HTML = f"file://{ROOT}/app/src/main/assets/toolbox_ui.html"
PORT = 9337
VIEWS = [("link", "行车联动"), ("body", "车载声音"), ("fuel", "行程油耗"),
         ("store", "应用生态"), ("system", "系统设置")]

JS = r"""(() => {
  const vis = el => {
    const r = el.getBoundingClientRect();
    if (r.width < 24 || r.height < 16) return false;
    const s = getComputedStyle(el);
    return s.display !== 'none' && s.visibility !== 'hidden' && parseFloat(s.opacity) > 0.05;
  };
  const isTransparent = c => !c || c === 'transparent' || /rgba\(\s*0,\s*0,\s*0,\s*0\)/.test(c);
  const bw = el => {
    const s = getComputedStyle(el);
    let m = 0;
    for (const d of ['Top','Right','Bottom','Left']) {
      const w = parseFloat(s['border'+d+'Width']) || 0;
      const c = s['border'+d+'Style'] === 'none' ? '' : s['border'+d+'Color'];
      if (w > 0 && !isTransparent(c)) m = Math.max(m, w);
    }
    return m;
  };
  const cls = el => (el.className || '').toString().replace(/\s+/g, ' ').trim().slice(0, 130);
  const out = [];
  for (const el of [...document.querySelectorAll('main *')].filter(vis)) {
    const own = bw(el);
    if (own < 0.4) continue;
    const s = getComputedStyle(el);
    const interactive = el.tagName === 'BUTTON' || s.cursor === 'pointer' || el.tagName === 'INPUT';
    if (interactive) continue;              // 只看纯容器
    const inner = [...el.querySelectorAll('*')].filter(c => bw(c) > 0.4 && vis(c));
    if (!inner.length) continue;
    out.push({own, w: Math.round(el.getBoundingClientRect().width),
              h: Math.round(el.getBoundingClientRect().height),
              innerCount: inner.length, cls: cls(el),
              innerSample: cls(inner[0])});
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
    agg = {}
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
            rows = await ev(JS)
            if not isinstance(rows, list):
                print(name, "probe failed"); continue
            print(f"\n===== {name}: 纯容器套边界 {len(rows)} 处")
            for r in rows:
                k = r["cls"]
                agg.setdefault(k, {"n": 0, "views": set(), "sample": r})
                agg[k]["n"] += 1
                agg[k]["views"].add(name)

    print("\n\n######## 施工清单（按出现次数） ########")
    for k, v in sorted(agg.items(), key=lambda kv: -kv[1]["n"]):
        r = v["sample"]
        print(f"\nx{v['n']}  宽{r['w']} 高{r['h']} 自身边线{r['own']:.0f}px  内层带框子元素{r['innerCount']}个  出现于 {','.join(sorted(v['views']))}")
        print(f"  容器: {k}")
        print(f"  内层样例: {r['innerSample'][:110]}")


if __name__ == "__main__":
    proc = start_edge()
    try:
        asyncio.run(run())
    finally:
        proc.terminate()
        subprocess.run(["pkill", "-f", f"--remote-debugging-port={PORT}"], stderr=subprocess.DEVNULL)
