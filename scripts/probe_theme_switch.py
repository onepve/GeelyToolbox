#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""主题切换取证：在真实车机视口 1920x720 下点击昼夜/配色按钮，读取 html/body 上 CSS 变量的实际生效值，
并枚举所有「从视口底部冒出的长条元素」，定位『下方露出半截的未知窗口』。"""
import os, json, base64, time, asyncio, subprocess, urllib.request, websockets

MEDIA_DIR = "/home/onepve/hermes-media/screenshots"
os.makedirs(MEDIA_DIR, exist_ok=True)
HTML_FILE = "file:///data/projects/GeelyToolbox/app/src/main/assets/toolbox_ui.html"
PORT = 9263

def start_edge():
    p = subprocess.Popen([
        "/usr/bin/microsoft-edge", "--headless=new",
        f"--remote-debugging-port={PORT}", "--no-sandbox", "--disable-gpu",
        "--window-size=1920,720", HTML_FILE
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(40):
        time.sleep(0.5)
        try:
            urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list", timeout=1).read()
            time.sleep(1.5)
            return p
        except Exception:
            continue
    raise RuntimeError("Edge CDP 未就绪")

PROBE = r"""
(() => {
  const H = window.innerHeight, W = window.innerWidth;
  const cs = (el, p) => el ? getComputedStyle(el).getPropertyValue(p).trim() : '(none)';
  const html = document.documentElement, body = document.body;
  // 1) 变量三层取值：html inline / html 计算 / body 计算（body 优先，被子元素继承）
  const vars = {};
  ['--bg-main','--bg-card','--accent-gold','--orb-1','--text-main'].forEach(v => {
    vars[v] = {
      html_inline: html.style.getPropertyValue(v) || '(empty)',
      html_computed: cs(html, v),
      body_computed: cs(body, v)
    };
  });
  // 2) 底部「半截长条」候选：宽度>屏幕 1/3 的元素，其底部超出视口或顶部在视口下 55% 之后
  const suspects = [];
  document.querySelectorAll('body *').forEach(el => {
    const r = el.getBoundingClientRect();
    if (r.width < W / 3 || r.height < 20) return;
    const st = getComputedStyle(el);
    const emerges = (r.bottom > H + 2) && (r.top < H) && (r.top > H * 0.5);
    const isBgOrb = el.matches('.bg-orbs i') || (el.closest && el.closest('.bg-orbs'));
    if (emerges || isBgOrb) {
      suspects.push({
        tag: el.tagName, cls: (el.className && el.className.baseVal !== undefined ? el.className.baseVal : String(el.className)).slice(0, 90),
        top: Math.round(r.top), left: Math.round(r.left), w: Math.round(r.width), h: Math.round(r.height),
        position: st.position, filter: st.filter, opacity: st.opacity, bg: st.backgroundColor, z: st.zIndex,
        text: (el.textContent || '').trim().slice(0, 40)
      });
    }
  });
  return JSON.stringify({
    viewport: [W, H], dataMode: html.getAttribute('data-mode'), dataPalette: html.getAttribute('data-palette'),
    htmlClass: html.className, bodyClass: body.className,
    bodyBg: getComputedStyle(body).backgroundColor,
    vars, suspects: suspects.slice(0, 25), suspectCount: suspects.length
  }, null, 1);
})()
"""

async def run():
    with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list") as r:
        tabs = json.loads(r.read().decode())
    tab = [t for t in tabs if t.get("type") == "page"][0]
    async with websockets.connect(tab["webSocketDebuggerUrl"], max_size=64*1024*1024) as ws:
        _id = {"v": 0}
        async def ev(js):
            _id["v"] += 1
            await ws.send(json.dumps({"id": _id["v"], "method": "Runtime.evaluate",
                                      "params": {"expression": js, "returnByValue": True}}))
            return json.loads(await ws.recv()).get("result", {}).get("result", {}).get("value")
        async def shot(name):
            _id["v"] += 1
            await ws.send(json.dumps({"id": _id["v"], "method": "Page.captureScreenshot", "params": {"format": "png"}}))
            d = json.loads(await ws.recv())["result"]["data"]
            p = os.path.join(MEDIA_DIR, name)
            open(p, "wb").write(base64.b64decode(d))
            return p

        await asyncio.sleep(1.0)
        # 进入系统维护页（主题区所在页）
        await ev("window.__VUE_STORE__.currentNav = 'system'; 'ok'")
        await asyncio.sleep(0.8)
        # 点击某个按钮（按文字精确匹配）
        click = """(txt) => { const b=[...document.querySelectorAll('button')].find(x=>x.textContent.replace(/\\s+/g,'').includes(txt)); if(!b) return 'NOT FOUND: '+txt; b.click(); return 'clicked '+txt; }"""
        print("### 场景1：白天 + 逐个配色")
        print(await ev(f"({click})('白天')")); await asyncio.sleep(0.5)
        for pal in ["清晨蓝", "琥珀棕", "晨曦绿", "樱语粉"]:
            print(await ev(f"({click})('{pal}')")); await asyncio.sleep(0.6)
            info = await ev(PROBE)
            d = json.loads(info)
            v = d["vars"]
            print(f"  {pal}: html_inline bg-main={v['--bg-main']['html_inline']} | body计算 bg-main={v['--bg-main']['body_computed']} | bodyBg={d['bodyBg']} | bodyClass='{d['bodyClass']}'")
        print("  截图:", await shot("probe_day_pink.png"))
        print()
        print("### 场景2：黑夜 + 逐个配色")
        print(await ev(f"({click})('黑夜')")); await asyncio.sleep(0.6)
        for pal in ["清晨蓝", "琥珀棕", "晨曦绿", "樱语粉"]:
            print(await ev(f"({click})('{pal}')")); await asyncio.sleep(0.6)
            d = json.loads(await ev(PROBE))
            v = d["vars"]
            print(f"  {pal}: html_inline bg-main={v['--bg-main']['html_inline']} | body计算 bg-main={v['--bg-main']['body_computed']} | bodyBg={d['bodyBg']}")
        print("  截图:", await shot("probe_night_pink.png"))
        print()
        print("### 底部「半截长条」元素清单（当前状态）")
        d = json.loads(await ev(PROBE))
        print("viewport", d["viewport"], "suspects:", d["suspectCount"])
        for s in d["suspects"]:
            print(f"  <{s['tag']}> top={s['top']} left={s['left']} {s['w']}x{s['h']} pos={s['position']} filter={s['filter']} op={s['opacity']} z={s['z']} bg={s['bg']} text='{s['text']}' cls='{s['cls']}'")

if __name__ == "__main__":
    proc = start_edge()
    try:
        asyncio.run(run())
    finally:
        proc.terminate()
