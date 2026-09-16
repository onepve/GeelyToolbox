#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""启动闪黑 A/B 取证：
   A = 现产物（<head> 内联了构建期主题引导脚本）
   B = 对照组（同一产物，仅删掉那段引导脚本，还原成修复前的行为）
   两者都剥掉 Vue 模块脚本，只留 <head>（样式 + 引导脚本），并在同源预置 localStorage
   = 白天/晨曦绿。此时画面底色只可能由引导脚本决定，可干净判定「首帧到底是白天还是黑一下」。
"""
import os, json, base64, time, asyncio, subprocess, urllib.request, websockets
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import threading, functools

ASSETS = "/data/projects/GeelyToolbox/app/src/main/assets"
TMP = "/tmp/boot_ab"
MEDIA_DIR = "/home/onepve/hermes-media/screenshots"
PORT_HTTP = 9288
PORT_CDP = 9289
os.makedirs(MEDIA_DIR, exist_ok=True)
os.makedirs(TMP, exist_ok=True)

src = open(os.path.join(ASSETS, "toolbox_ui.html"), encoding="utf-8").read()

# 剥掉所有 <script type="module">...</script>（Vue 应用本体），只留 head
import re
no_app = re.sub(r'<script type="module"[^>]*>.*?</script>', '<!-- app removed for A/B -->', src, flags=re.S)
# 再剥掉引导脚本 = 对照组 B
no_boot = re.sub(r'<script>\(function\(\)\{try\{.*?__TOOLBOX_BOOT_THEME__.*?</script>', '<!-- boot removed -->', no_app, flags=re.S)

assert 'A/B' in no_app, "Vue 模块脚本未找到，剥离规则需更新"
assert '__TOOLBOX_BOOT_THEME__' in no_app, "引导脚本未找到，剥离规则需更新"
assert '__TOOLBOX_BOOT_THEME__' not in no_boot, "对照组仍含引导脚本"
open(f"{TMP}/boot_on.html", "w", encoding="utf-8").write(no_app)
open(f"{TMP}/boot_off.html", "w", encoding="utf-8").write(no_boot)

# 再加一个空壳页，用于在同源写入 localStorage
open(f"{TMP}/set.html", "w", encoding="utf-8").write("<!DOCTYPE html><title>set</title><body>ok</body>")
# 完整产物也放到同源，用于「首帧引导档 vs 挂载后实际档」一致性核验
open(f"{TMP}/toolbox_ui.html", "w", encoding="utf-8").write(src)


def serve():
    h = functools.partial(SimpleHTTPRequestHandler, directory=TMP)
    srv = ThreadingHTTPServer(("127.0.0.1", PORT_HTTP), h)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    return srv


def start_edge(url):
    p = subprocess.Popen([
        "/usr/bin/microsoft-edge", "--headless=new",
        f"--remote-debugging-port={PORT_CDP}", "--no-sandbox", "--disable-gpu",
        "--window-size=1920,720", url
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(40):
        time.sleep(0.5)
        try:
            urllib.request.urlopen(f"http://127.0.0.1:{PORT_CDP}/json/list", timeout=1).read()
            time.sleep(1.2)
            return p
        except Exception:
            continue
    raise RuntimeError("Edge CDP 未就绪")


def pixel(path):
    try:
        from PIL import Image
        im = Image.open(path).convert("RGB")
        return im.getpixel((40, 300)), im.getpixel((960, 360))
    except Exception as e:
        return ("PIL 不可用: %s" % e), None


async def run():
    with urllib.request.urlopen(f"http://127.0.0.1:{PORT_CDP}/json/list") as r:
        tab = [t for t in json.loads(r.read().decode()) if t.get("type") == "page"][0]
    async with websockets.connect(tab["webSocketDebuggerUrl"], max_size=64*1024*1024) as ws:
        _id = {"v": 0}

        async def send(method, **params):
            _id["v"] += 1
            await ws.send(json.dumps({"id": _id["v"], "method": method, "params": params}))
            while True:
                m = json.loads(await ws.recv())
                if m.get("id") == _id["v"]:
                    return m.get("result", {})

        async def ev(js):
            r = await send("Runtime.evaluate", expression=js, returnByValue=True, awaitPromise=True)
            return r.get("result", {}).get("value")

        async def shot(name):
            r = await send("Page.captureScreenshot", format="png")
            p = os.path.join(MEDIA_DIR, name)
            open(p, "wb").write(base64.b64decode(r["data"]))
            return p

        # 同源写入 localStorage：白天 + 晨曦绿
        await send("Page.navigate", url=f"http://127.0.0.1:{PORT_HTTP}/set.html")
        await asyncio.sleep(1.0)
        print("  写入 localStorage:", await ev(
            "localStorage.setItem('toolbox_theme_v1', JSON.stringify({palette:'green',mode:'day'}));"
            "localStorage.getItem('toolbox_theme_v1')"))

        for tag, f in (("A 有引导脚本（修复后）", "boot_on.html"), ("B 无引导脚本（修复前）", "boot_off.html")):
            await send("Page.navigate", url=f"http://127.0.0.1:{PORT_HTTP}/{f}")
            await asyncio.sleep(1.5)
            info = await ev("JSON.stringify({"
                            "var_html: document.documentElement.style.getPropertyValue('--bg-main'),"
                            "mode: document.documentElement.getAttribute('data-mode'),"
                            "palette: document.documentElement.getAttribute('data-palette'),"
                            "cls: document.documentElement.className,"
                            "htmlBg: getComputedStyle(document.documentElement).backgroundColor,"
                            "bodyBg: getComputedStyle(document.body).backgroundColor})")
            d = json.loads(info)
            p = await shot(f"probe_boot_{'on' if tag.startswith('A') else 'off'}.png")
            px = pixel(p)
            print(f"  {tag}")
            print(f"    html 内联 --bg-main = {d['var_html']!r} | data-mode={d['mode']} data-palette={d['palette']} class={d['cls']!r}")
            print(f"    body 计算底色 = {d['bodyBg']} | 截图左上/中心像素 = {px[0]} / {px[1]}")
            print(f"    截图: {p}")

        # ---- 第二段：完整产物（含 Vue 应用）下，首帧引导档 vs 挂载后实际档 必须一致 ----
        print()
        print("### 首帧引导档 vs 挂载后实际档（一致 = 全程无任何闪动）")
        CONSIST = ("JSON.stringify({"
                   "boot: window.__TOOLBOX_BOOT_THEME__ || null,"
                   "mode: document.documentElement.getAttribute('data-mode'),"
                   "palette: document.documentElement.getAttribute('data-palette'),"
                   "bg: document.documentElement.style.getPropertyValue('--bg-main'),"
                   "bodyBg: getComputedStyle(document.body).backgroundColor})")
        cases = [
            ("未存过任何设置（出厂默认）", "null"),
            ("已存：晨曦绿 + 白天", "JSON.stringify({palette:'green',mode:'day'})"),
            ("已存：樱语粉 + 黑夜", "JSON.stringify({palette:'pink',mode:'night'})"),
            ("已存：琥珀棕 + 跟随时间", "JSON.stringify({palette:'brown',mode:'auto'})"),
            ("已存：非法值（应回落出厂默认）", "JSON.stringify({palette:'hacker',mode:'rainbow'})"),
        ]
        ok = True
        for label, expr in cases:
            await send("Page.navigate", url=f"http://127.0.0.1:{PORT_HTTP}/set.html")
            await asyncio.sleep(0.7)
            await ev(f"localStorage.removeItem('toolbox_theme_v1'); if({expr}) localStorage.setItem('toolbox_theme_v1', {expr}); 'ok'")
            await send("Page.navigate", url=f"http://127.0.0.1:{PORT_HTTP}/toolbox_ui.html")
            await asyncio.sleep(1.6)
            d = json.loads(await ev(CONSIST))
            b = d["boot"] or {}
            match = (b.get("mode") == d["mode"]) and (b.get("palette") == d["palette"])
            ok = ok and match
            print(f"  {label:22s} 首帧引导={b.get('palette')}/{b.get('mode')}  挂载后={d['palette']}/{d['mode']}  "
                  f"body底色={d['bodyBg']}  {'✅ 一致' if match else '❌ 不一致（会闪）'}")
        print("  结论:", "全部一致，启动全程零闪动" if ok else "存在不一致，需修正")

        # ---- 第三段：点「关于」时全屏遮罩的实测色（白天档不许再是压暗的深幕布） ----
        print()
        print("### 点「关于」弹窗遮罩实测（白天档）")
        VEIL = ("(async () => {"
                " const sleep = ms => new Promise(r => setTimeout(r, ms));"
                " window.openModal('about');"
                " const t0 = performance.now(); const samples = [];"
                " for (let i = 0; i < 8; i++) {"
                "   const el = document.querySelector('.modal-fade-enter-active, .modal-fade-enter-to, [class*=fixed]');"
                "   samples.push({t: +(performance.now()-t0).toFixed(0), op: el ? getComputedStyle(el).opacity : null,"
                "                 bg: el ? getComputedStyle(el).backgroundColor : null});"
                "   await sleep(30); }"
                " window.closeModal('about');"
                " return JSON.stringify(samples); })()")
        await send("Page.navigate", url=f"http://127.0.0.1:{PORT_HTTP}/set.html")
        await asyncio.sleep(0.6)
        await ev("localStorage.setItem('toolbox_theme_v1', JSON.stringify({palette:'blue',mode:'day'})); 'ok'")
        await send("Page.navigate", url=f"http://127.0.0.1:{PORT_HTTP}/toolbox_ui.html")
        await asyncio.sleep(1.6)
        for s in json.loads(await ev(VEIL)):
            print(f"    t={s['t']:4}ms  遮罩 opacity={s['op']}  background={s['bg']}")
        print("    截图:", await shot("probe_about_veil_day.png"))


if __name__ == "__main__":
    srv = serve()
    proc = start_edge(f"http://127.0.0.1:{PORT_HTTP}/set.html")
    try:
        asyncio.run(run())
    finally:
        proc.terminate()
        srv.shutdown()
