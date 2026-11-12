#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""五个主视图车机真视口截图（UI 复核用）。

默认视口 1760x720，等于车机真实 appBounds（wm size 1920x720 减去右侧 160px 系统侧栏）。
历史教训：曾按 1920x720 出图，卡片更宽松、标签不换行，与车机实拍差别极大，
每次都要上车实测纠偏。出图必须与车机可用窗口一致，否则效果图没有参考价值。

想看车机内核（Chromium 66）真渲染，用 scripts/check_webview_render_parity.py。

用法：
    python3 scripts/shot_views.py                  # 黑夜（出厂默认）
    python3 scripts/shot_views.py day              # 白天
    python3 scripts/shot_views.py day amber_gold   # 白天 + 指定配色
    SHOT_VW=1920 python3 scripts/shot_views.py day # 显式指定视口（仅调试用）
输出：/data/tmp/ui_audit/<view>.png / day_<view>.png
"""
import os, sys, json, time, asyncio, subprocess, urllib.request, base64, websockets

ROOT = "/data/projects/GeelyToolbox"
HTML = f"file://{ROOT}/app/src/main/assets/toolbox_ui.html"
PORT = 9341
OUT = "/data/tmp/ui_audit"
MODE = (sys.argv[1] if len(sys.argv) > 1 else "night").lower()
# 车机真实可用视口：guard.log 实测 wm size 1920x720，但 appBounds=1760x720
# （右侧 160px 被系统侧栏吃掉）。按 1920 出图会让卡片更宽松、不换行，
# 与车机实拍差别极大，因此默认按真实 appBounds 出图。
VW = int(os.environ.get("SHOT_VW", "1760"))
VH = int(os.environ.get("SHOT_VH", "720"))
PALETTE = sys.argv[2] if len(sys.argv) > 2 else "sky_ocean"
THEME_KEY = "toolbox_theme_v2"
VIEWS = [("link", "行车联动"), ("body", "车载声音"), ("fuel", "行程油耗"),
         ("store", "应用生态"), ("system", "系统设置")]


def _find_browser():
    """优先车机同款内核（Chromium 66），找不到才回退本机 Edge/Chrome。

    为什么必须优先 66：本机 Edge 是 120+，flex 布局、:is()/:where()、flex gap、
    background-clip 等行为全都不一样 —— 同一份产物在 Edge 上好看、上车就塌，
    历史上每次出图都要上车纠偏。出图必须与车机同内核，效果图才有参考价值。
    CHROME66 环境变量优先（CI 里由流水线下载快照后注入）。
    """
    for p in [os.environ.get("CHROME66"),
              "/data/tmp/chromium66/x/chrome-linux/chrome",
              "/usr/bin/microsoft-edge", "/usr/bin/google-chrome", "/usr/bin/chromium"]:
        if p and os.path.exists(p):
            return p
    raise RuntimeError("找不到任何可用浏览器（CHROME66 / Chromium66 / Edge / Chrome）")


def _browser_env():
    env = dict(os.environ)
    fc = "/data/tmp/chromium66/fc/fonts.conf"
    if os.path.exists(fc):
        env["FONTCONFIG_FILE"] = fc
    else:
        env.pop("FONTCONFIG_FILE", None)
    return env


def start_edge():
    subprocess.run(["pkill", "-f", f"--remote-debugging-port={PORT}"], stderr=subprocess.DEVNULL)
    time.sleep(0.5)
    browser = _find_browser()
    is66 = "chromium66" in browser or "cr66" in browser
    # 临时目录与 profile 必须落在可写持久盘：Chromium 66 若用默认 profile 会被锁冲突秒退，
    # 若 stdout 指向 DEVNULL 也会秒退（老内核的已知坑，见 audit_full_ui.py）。
    prof = "/data/tmp/shotprof"
    subprocess.run(["rm", "-rf", prof], stderr=subprocess.DEVNULL)
    os.makedirs(OUT, exist_ok=True)
    logf = open("/data/tmp/shot_views_browser.log", "wb")
    args = [browser,
            "--headless" if is66 else "--headless=new",
            f"--remote-debugging-port={PORT}", "--no-sandbox", "--disable-gpu",
            f"--user-data-dir={prof}",
            f"--window-size={VW},{VH}", "--hide-scrollbars", HTML]
    proc = subprocess.Popen(args, stdout=logf, stderr=logf, env=_browser_env())
    print(f"浏览器={browser} 视口={VW}x{VH}")
    for _ in range(40):
        try:
            urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list", timeout=1).read()
            return proc
        except Exception:
            time.sleep(0.4)
    proc.terminate()
    raise RuntimeError("浏览器未就绪，日志尾部：\n"
                       + open("/data/tmp/shot_views_browser.log", errors="replace").read()[-1500:])


async def run():
    os.makedirs(OUT, exist_ok=True)
    with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list") as resp:
        tabs = json.loads(resp.read().decode())
    page = [t for t in tabs if t.get("type") == "page"][0]
    async with websockets.connect(page["webSocketDebuggerUrl"], max_size=128*1024*1024) as ws:
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
        # 主题模式由 initTheme() 从 localStorage 读取，改完必须 reload 才会真正切昼夜
        await ev("localStorage.setItem('has_shown_welcome_donate','true'); 'ok'")
        await ev(f"localStorage.setItem('toolbox_theme_v2', JSON.stringify({{palette:'{PALETTE}', mode:'{MODE}'}})); 'ok'")
        await call("Page.reload", ignoreCache=True)
        await asyncio.sleep(2.5)
        real = await ev("document.documentElement.getAttribute('data-mode')")
        pal = await ev("document.documentElement.getAttribute('data-palette')")
        print(f"模式={real} 配色={pal}")
        await ev("(() => { if(window.__VUE_STORE__) window.__VUE_STORE__.modals={}; return 'ok'; })()")
        await asyncio.sleep(0.8)
        prefix = "" if MODE == "night" else (f"{MODE}_" if PALETTE == "sky_ocean" else f"{MODE}_{PALETTE}_")
        for key, name in VIEWS:
            await ev(f"window.__VUE_STORE__.currentNav = '{key}'; 'ok'")
            await asyncio.sleep(1.2)
            shot = await call("Page.captureScreenshot", format="png")
            path = f"{OUT}/{prefix}{key}.png"
            open(path, "wb").write(base64.b64decode(shot["data"]))
            print(path, name, os.path.getsize(path))


if __name__ == "__main__":
    proc = start_edge()
    try:
        asyncio.run(run())
    finally:
        proc.terminate()
        subprocess.run(["pkill", "-f", f"--remote-debugging-port={PORT}"], stderr=subprocess.DEVNULL)
