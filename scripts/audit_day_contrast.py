#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""昼夜两档的「可辨识度」实测：卡片与底色差、文字最低对比度、选中态标记。

用法：
    python3 scripts/audit_day_contrast.py          # 黑夜
    python3 scripts/audit_day_contrast.py day      # 白天
判定口径：
  · 卡片/底座色差 ΔL* 用 WCAG 相对亮度差（(L1+0.05)/(L2+0.05)），< 1.08 视为「糊成一片」
  · 正文对比度 < 4.5（大字 < 3.0）视为不达标
"""
import sys, json, time, asyncio, subprocess, urllib.request, websockets

ROOT = "/data/projects/GeelyToolbox"
HTML = f"file://{ROOT}/app/src/main/assets/toolbox_ui.html"
PORT = 9348
MODE = (sys.argv[1] if len(sys.argv) > 1 else "night").lower()
PALETTE = sys.argv[2] if len(sys.argv) > 2 else "sky_ocean"
THEME_KEY = "toolbox_theme_v2"
VIEWS = [("link", "行车联动"), ("body", "车载声音"), ("fuel", "行程油耗"),
         ("store", "应用生态"), ("system", "系统设置")]

JS = r"""(() => {
  const parse = c => {
    const m = c.match(/rgba?\(([^)]+)\)/);
    if (!m) return null;
    const p = m[1].split(',').map(x => parseFloat(x));
    return {r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1};
  };
  const lum = c => {
    const f = v => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); };
    return 0.2126 * f(c.r) + 0.7152 * f(c.g) + 0.0722 * f(c.b);
  };
  const blend = (fg, bg) => ({
    r: fg.r * fg.a + bg.r * (1 - fg.a),
    g: fg.g * fg.a + bg.g * (1 - fg.a),
    b: fg.b * fg.a + bg.b * (1 - fg.a), a: 1
  });
  const ratio = (a, b) => {
    const la = lum(a), lb = lum(b);
    return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
  };
  let pageBgCache = null;
  // 从元素自身向上叠到不透明背景
  const effBg = el => {
    let cur = el, stack = [];
    while (cur) {
      const c = parse(getComputedStyle(cur).backgroundColor);
      if (c && c.a > 0.01) { stack.push(c); if (c.a >= 0.99) break; }
      cur = cur.parentElement;
    }
    let bg = pageBgCache || {r: 255, g: 255, b: 255, a: 1};
    for (let i = stack.length - 1; i >= 0; i--) bg = blend(stack[i], bg);
    return bg;
  };
  const vis = el => {
    const r = el.getBoundingClientRect();
    if (r.width < 24 || r.height < 16) return false;
    const s = getComputedStyle(el);
    return s.display !== 'none' && s.visibility !== 'hidden' && parseFloat(s.opacity) > 0.05;
  };

  // 1) 卡片与页面底色差
  // 页面底色的真源是主题变量 --bg-main（main/body 多为透明，取 computed background 会误得纯白，
  // 从而把「白卡 vs 浅蓝底」误判成「白卡 vs 白底糊在一起」）。
  const rootStyle = getComputedStyle(document.documentElement);
  const mainVar = rootStyle.getPropertyValue('--bg-main').trim();
  let pageBg = mainVar ? parse(mainVar) : null;
  if (!pageBg) {
    const page = parse(getComputedStyle(document.body).backgroundColor)
              || parse(getComputedStyle(document.documentElement).backgroundColor)
              || {r: 255, g: 255, b: 255, a: 1};
    pageBg = page.a >= 0.99 ? page : blend(page, {r: 255, g: 255, b: 255, a: 1});
  }
  pageBgCache = pageBg;
  let flat = 0, cards = 0;
  const flatCls = [];
  for (const el of [...document.querySelectorAll('main *')].filter(vis)) {
    const cls = (el.className || '').toString();
    if (!/bg-car-(card|item|base)/.test(cls)) continue;
    // 自身不铺色的（如未选中导航项）不算卡片，别把「透明=融进底色」当缺陷
    const own = parse(getComputedStyle(el).backgroundColor);
    if (!own || own.a < 0.02) continue;
    cards++;
    const bg = effBg(el);
    // 只跟「它直接铺在什么上」比：卡片比页面底，底座比卡片。
    // 拿小块跟页面底比会把「白色底座落在白卡上」误判成糊成一片。
    const host = el.parentElement ? effBg(el.parentElement) : pageBg;
    if (ratio(bg, host) < 1.04) {
      flat++;
      if (flatCls.length < 12) flatCls.push(cls.replace(/\s+/g, ' ').slice(0, 90));
    }
  }

  // 2) 正文对比度最低点
  const walker = document.createTreeWalker(document.querySelector('main') || document.body, NodeFilter.SHOW_TEXT);
  let worst = 99, worstTxt = '', low = 0, samples = 0;
  while (walker.nextNode()) {
    const t = walker.currentNode;
    const txt = (t.textContent || '').trim();
    if (txt.length < 2) continue;
    const el = t.parentElement;
    if (!el || !vis(el)) continue;
    const s = getComputedStyle(el);
    const fg = parse(s.color);
    if (!fg) continue;
    const bg = effBg(el);
    const cr = ratio(blend(fg, bg), bg);
    samples++;
    if (cr < 4.5) low++;
    if (cr < worst) { worst = cr; worstTxt = txt.slice(0, 16); }
  }

  // 3) 选中/点亮态标记（左侧竖条）
  const marks = [];
  const rootCS = getComputedStyle(document.documentElement);
  for (const el of [...document.querySelectorAll('main *')].filter(vis)) {
    const s = getComputedStyle(el);
    const lw = parseFloat(s.borderLeftWidth) || 0;
    // animate-spin 的加载圈四边宽度相等，不是选中标记，排除
    if (/animate-spin/.test((el.className || '').toString())) continue;
    if (lw >= 3 && s.borderLeftColor && s.borderLeftColor !== 'transparent') {
      const c = parse(s.borderLeftColor);
      const bg = effBg(el);
      marks.push({w: lw, c: s.borderLeftColor, ratio: c ? +ratio(blend(c, bg), bg).toFixed(2) : 0});
    }
  }
  return {pageBg: `${Math.round(pageBg.r)},${Math.round(pageBg.g)},${Math.round(pageBg.b)}`,
          cards, flat, flatCls,
          textSamples: samples, lowContrast: low,
          worst: +worst.toFixed(2), worstTxt,
          marks: marks.length, markMin: marks.length ? Math.min(...marks.map(m => m.ratio)) : null,
          accent: rootCS.getPropertyValue('--accent-gold').trim()};
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
        await ev(f"localStorage.setItem('{THEME_KEY}', JSON.stringify({{palette:'{PALETTE}', mode:'{MODE}'}})); 'ok'")
        await call("Page.reload", ignoreCache=True)
        await asyncio.sleep(2.5)
        print("模式=", await ev("document.documentElement.getAttribute('data-mode')"))
        await ev("(() => { if(window.__VUE_STORE__) window.__VUE_STORE__.modals={}; return 'ok'; })()")
        await asyncio.sleep(0.8)
        for key, name in VIEWS:
            await ev(f"window.__VUE_STORE__.currentNav = '{key}'; 'ok'")
            await asyncio.sleep(1.1)
            d = await ev(JS)
            if not isinstance(d, dict):
                print(name, "probe failed"); continue
            print(f"{name}: 页面底=rgb({d['pageBg']}) 强调色={d['accent']} "
                  f"卡片块={d['cards']} 与底色几乎无差={d['flat']} "
                  f"文字样本={d['textSamples']} 低于4.5={d['lowContrast']} "
                  f"最低={d['worst']}({d['worstTxt']}) "
                  f"左侧竖条={d['marks']}处 最低反差={d['markMin']}")
            if d["flatCls"]:
                for c in d["flatCls"]:
                    print("    平淡块:", c)


if __name__ == "__main__":
    proc = start_edge()
    try:
        asyncio.run(run())
    finally:
        proc.terminate()
        subprocess.run(["pkill", "-f", f"--remote-debugging-port={PORT}"], stderr=subprocess.DEVNULL)
