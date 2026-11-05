#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 v2.0 自绘新皮肤效果图与多配色实车对比图
视口锁定 1920x720，带左侧侧边栏，真实呈现座舱沉浸感
"""
import os, sys, time, json, base64, asyncio, subprocess, urllib.request, websockets
from PIL import Image

MEDIA_DIR = "/home/onepve/hermes-media/screenshots"
os.makedirs(MEDIA_DIR, exist_ok=True)
HTML_FILE = "file:///data/projects/GeelyToolbox/app/src/main/assets/toolbox_ui.html"
PORT = 9277

def start_edge():
    # 杀掉可能残留的端口进程
    subprocess.run(["pkill", "-f", f"remote-debugging-port={PORT}"], stderr=subprocess.DEVNULL)
    time.sleep(0.5)
    proc = subprocess.Popen([
        "/usr/bin/microsoft-edge",
        "--headless=new",
        f"--remote-debugging-port={PORT}",
        "--no-sandbox",
        "--disable-gpu",
        "--window-size=1920,720",
        HTML_FILE
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(40):
        time.sleep(0.4)
        try:
            urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list", timeout=1).read()
            time.sleep(1.0)
            return proc
        except Exception:
            continue
    raise RuntimeError("Edge CDP 启动失败")

async def run():
    with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list") as r:
        tabs = json.loads(r.read().decode())
    tab = [t for t in tabs if t.get("type") == "page"][0]
    
    async with websockets.connect(tab["webSocketDebuggerUrl"], max_size=64*1024*1024) as ws:
        _id = {"v": 0}
        async def evaluate(js):
            _id["v"] += 1
            await ws.send(json.dumps({
                "id": _id["v"],
                "method": "Runtime.evaluate",
                "params": {"expression": js, "returnByValue": True}
            }))
            res = json.loads(await ws.recv())
            return res.get("result", {}).get("result", {}).get("value")

        async def screenshot(path):
            _id["v"] += 1
            await ws.send(json.dumps({
                "id": _id["v"],
                "method": "Page.captureScreenshot",
                "params": {"format": "png"}
            }))
            res = json.loads(await ws.recv())
            data = base64.b64decode(res["result"]["data"])
            with open(path, "wb") as f:
                f.write(data)
            return path

        # 初始化环境：跳过欢迎向导与赞助弹窗，重置状态
        await evaluate("""(() => {
            localStorage.setItem('has_shown_welcome_donate', 'true');
            localStorage.setItem('toolbox_first_launch_done', 'true');
            if (window.__VUE_STORE__) {
                window.__VUE_STORE__.modals = {};
            }
            return 'init ok';
        })()""")
        await asyncio.sleep(0.5)

        # 1. 切换到「系统维护」的「新皮肤(尝鲜)」Tab
        await evaluate("""(() => {
            const store = window.__VUE_STORE__;
            if (store) {
                store.currentNav = 'system';
            }
            return 'nav ok';
        })()""")
        await asyncio.sleep(0.5)

        # 触发切换到 theme tab (外观主题风格)
        await evaluate("""(() => {
            const btns = Array.from(document.querySelectorAll('button'));
            const tabBtn = btns.find(b => (b.textContent || '').includes('外观主题风格'));
            if (tabBtn) {
                tabBtn.click();
                return 'tabBtn clicked: ' + tabBtn.textContent;
            }
            return 'not found';
        })()""")
        await asyncio.sleep(1.0)

        # 截图 1：新皮肤面板（6款配色矩阵）整体
        p1 = f"{MEDIA_DIR}/v2_theme_matrix_panel.png"
        await screenshot(p1)
        print("已截取新皮肤 6 款配色矩阵面板:", p1)

        # 2. 切换到「落樱粉」白天模式 + 截图（在车身联动页看效果）
        await evaluate("""(() => {
            const { setPalette, setMode } = window.__TOOLBOX_THEMES__ || {};
            if (setPalette) setPalette('sakura_pink');
            if (setMode) setMode('day');
            if (window.__VUE_STORE__) window.__VUE_STORE__.currentNav = 'link';
            return 'pink day ok';
        })()""")
        await asyncio.sleep(0.8)
        p_pink_day = f"{MEDIA_DIR}/v2_sakura_pink_day.png"
        await screenshot(p_pink_day)
        print("已截取落樱粉白天:", p_pink_day)

        # 3. 切换到「落樱粉」夜间模式 + 截图
        await evaluate("""(() => {
            const { setMode } = window.__TOOLBOX_THEMES__ || {};
            if (setMode) setMode('night');
            return 'pink night ok';
        })()""")
        await asyncio.sleep(0.8)
        p_pink_night = f"{MEDIA_DIR}/v2_sakura_pink_night.png"
        await screenshot(p_pink_night)
        print("已截取落樱粉夜间:", p_pink_night)

        # 4. 切换到「香芋紫」白天模式 + 截图
        await evaluate("""(() => {
            const { setPalette, setMode } = window.__TOOLBOX_THEMES__ || {};
            if (setPalette) setPalette('taro_purple');
            if (setMode) setMode('day');
            return 'purple day ok';
        })()""")
        await asyncio.sleep(0.8)
        p_purple_day = f"{MEDIA_DIR}/v2_taro_purple_day.png"
        await screenshot(p_purple_day)
        print("已截取香芋紫白天:", p_purple_day)

        # 5. 切换到「香芋紫」夜间模式 + 截图
        await evaluate("""(() => {
            const { setMode } = window.__TOOLBOX_THEMES__ || {};
            if (setMode) setMode('night');
            return 'purple night ok';
        })()""")
        await asyncio.sleep(0.8)
        p_purple_night = f"{MEDIA_DIR}/v2_taro_purple_night.png"
        await screenshot(p_purple_night)
        print("已截取香芋紫夜间:", p_purple_night)

        # 6. 回到新皮肤面板看香芋紫激活态
        await evaluate("""(() => {
            if (window.__VUE_STORE__) window.__VUE_STORE__.currentNav = 'system';
            return 'system ok';
        })()""")
        await asyncio.sleep(0.5)
        await evaluate("""(() => {
            const tabs = Array.from(document.querySelectorAll('button, div')).filter(el => (el.textContent || '').includes('外观主题风格'));
            if (tabs.length > 0) tabs[0].click();
            return 'tab ok';
        })()""")
        await asyncio.sleep(0.5)
        p_panel_purple = f"{MEDIA_DIR}/v2_panel_taro_purple.png"
        await screenshot(p_panel_purple)

        # 拼合 4合1 女生配色对比大图
        img_pd = Image.open(p_pink_day)
        img_pn = Image.open(p_pink_night)
        img_ud = Image.open(p_purple_day)
        img_un = Image.open(p_purple_night)

        W, H = img_pd.size
        # 缩放为半宽
        sw, sh = W // 2, H // 2
        thumb_pd = img_pd.resize((sw, sh), Image.Resampling.LANCZOS)
        thumb_pn = img_pn.resize((sw, sh), Image.Resampling.LANCZOS)
        thumb_ud = img_ud.resize((sw, sh), Image.Resampling.LANCZOS)
        thumb_un = img_un.resize((sw, sh), Image.Resampling.LANCZOS)

        combo = Image.new("RGB", (W, H), (15, 23, 42))
        combo.paste(thumb_pd, (0, 0))
        combo.paste(thumb_pn, (sw, 0))
        combo.paste(thumb_ud, (0, sh))
        combo.paste(thumb_un, (sw, sh))

        combo_path = f"{MEDIA_DIR}/v2_female_themes_compare.png"
        combo.save(combo_path, quality=95)
        print("已生成女生专属新配色 4 合 1 矩阵大图:", combo_path)

if __name__ == "__main__":
    edge = start_edge()
    try:
        asyncio.run(run())
    finally:
        edge.terminate()
        edge.wait()
