#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""弹窗实底取证：在真实车机视口 1920x720 下打开二级/三级弹窗，
读取弹窗面板的实际生效底色实度，并截图供人工核验文字可读性。

背景：弹窗曾沿用 --bg-card（夜间仅 13% 实度），导致背后页面内容透上来、正文读不清。
修复后弹窗统一使用 --bg-modal（96% 高实度）。本脚本用于回归自证。
"""
import os, json, base64, time, asyncio, subprocess, urllib.request, websockets

MEDIA_DIR = "/home/onepve/hermes-media/screenshots"
os.makedirs(MEDIA_DIR, exist_ok=True)
HTML_FILE = "file:///data/projects/GeelyToolbox/app/src/main/assets/toolbox_ui.html"
PORT = 9277


def start_edge():
    p = subprocess.Popen([
        "/usr/bin/microsoft-edge", "--headless=new",
        f"--remote-debugging-port={PORT}", "--no-sandbox", "--disable-gpu",
        "--user-data-dir=/tmp/edge-profile-modalprobe",
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
  const cs = (el, p) => el ? getComputedStyle(el).getPropertyValue(p).trim() : '(none)';
  const bgc = el => el ? getComputedStyle(el).backgroundColor : '(none)';
  const panel = document.querySelector('.tb-modal-surface');
  const back = panel ? panel.closest('div[style*="modal-backdrop"]') : null;
  const r = panel ? panel.getBoundingClientRect() : null;
  const alpha = s => { const m = /rgba?\(([^)]+)\)/.exec(s || ''); if (!m) return null;
    const p = m[1].split(',').map(x => x.trim()); return p.length === 4 ? parseFloat(p[3]) : 1; };
  return JSON.stringify({
    htmlInline: document.documentElement.style.getPropertyValue('--bg-modal') || '(empty)',
    htmlComputed: cs(document.documentElement, '--bg-modal'),
    panelBg: bgc(panel),
    panelAlpha: alpha(bgc(panel)),
    surfaceCount: document.querySelectorAll('.tb-modal-surface').length,
    backdropBg: bgc(back),
    panelSize: r ? [Math.round(r.width), Math.round(r.height)] : null,
    title: panel ? (panel.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 26) : ''
  });
})()
"""


async def run():
    with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list") as r:
        tabs = json.loads(r.read().decode())
    tab = [t for t in tabs if t.get("type") == "page"][0]
    async with websockets.connect(tab["webSocketDebuggerUrl"], max_size=64 * 1024 * 1024) as ws:
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

        click = """(txt) => { const b=[...document.querySelectorAll('button')].find(x=>x.textContent.replace(/\\s+/g,'').includes(txt)); if(!b) return 'NOT FOUND: '+txt; b.click(); return 'clicked '+txt; }"""

        await asyncio.sleep(1.2)
        # 进入系统维护页（主题区所在页）
        await ev("window.__VUE_STORE__.currentNav = 'system'; 'ok'")
        await asyncio.sleep(0.8)

        async def scenario(mode_txt, pal_txt, level):
            await ev(f"({click})('{mode_txt}')"); await asyncio.sleep(0.5)
            await ev(f"({click})('{pal_txt}')"); await asyncio.sleep(0.6)
            if level == 2:
                await ev("window.__VUE_STORE__.modals.about = true; 'ok'")
            else:
                # 三级弹窗：先开二级「关于」，再叠一层最高层级的确认弹窗（z-[9999]）
                await ev("window.__VUE_STORE__.modals.about = true; 'ok'")
                await asyncio.sleep(0.4)
                await ev("""window.__VUE_STORE__.modals.confirm = {title:'三级弹窗实底核验', desc:'这一层文字必须清晰可读，背后页面内容不得透上来干扰阅读。', badge:'核验样例', tip:'弹窗专用实底 --bg-modal 已生效'}; 'ok'""")
            await asyncio.sleep(0.7)
            d = json.loads(await ev(PROBE))
            tag = f"{mode_txt}-{pal_txt}-{'二级' if level == 2 else '三级'}"
            p = await shot(f"modal_{'L2' if level==2 else 'L3'}_{'night' if mode_txt=='黑夜' else 'day'}_{pal_txt}.png")
            print(f"[{tag}] 面板底色={d['panelBg']} 实度={d['panelAlpha']} | 实底元素数={d['surfaceCount']} | 遮罩={d['backdropBg']} | 尺寸={d['panelSize']} | 标题='{d['title']}'")
            print(f"    html 内联 --bg-modal={d['htmlInline']} | 计算值={d['htmlComputed']}")
            print(f"    截图: {p}")
            await ev("window.__VUE_STORE__.modals.confirm = null; window.__VUE_STORE__.modals.about = false; 'ok'")
            await asyncio.sleep(0.4)

        print("### 黑夜 · 二级/三级弹窗")
        for pal in ["清晨蓝", "琥珀棕", "晨曦绿", "樱语粉"]:
            await scenario("黑夜", pal, 3 if pal == "清晨蓝" else 2)

        print()
        print("### 白天 · 二级/三级弹窗")
        await scenario("白天", "清晨蓝", 3)
        await scenario("白天", "樱语粉", 2)


if __name__ == "__main__":
    proc = start_edge()
    try:
        asyncio.run(run())
    finally:
        proc.terminate()
