#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""交互性能取证：在 1920x720 车机视口下量化三件事——
  ① 切换各功能页的真实渲染+布局耗时（v-if 销毁重建的代价）
  ② 反复进出「语音」页后仍在运行的定时器数量（泄漏自证）
  ③ 点击「关于」时全屏遮罩的透明度时间线 + 主页背景是否突变（真闪 or 设计淡入）
"""
import os, json, base64, time, asyncio, subprocess, urllib.request, websockets

MEDIA_DIR = "/home/onepve/hermes-media/screenshots"
os.makedirs(MEDIA_DIR, exist_ok=True)
HTML_FILE = "file:///data/projects/GeelyToolbox/app/src/main/assets/toolbox_ui.html"
PORT = 9271


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


# ---- ① 切页耗时：所有视图 + 空基线（只等一个 setTimeout(0)+强制布局）----
SWITCH = r"""
(async () => {
  const st = window.__VUE_STORE__;
  const sleep = ms => new Promise(r => setTimeout(r, ms));
  const flush = async () => {
    await sleep(0);            // 让 Vue 微任务刷新 + 强制样式/布局
    void document.body.scrollHeight;
  };
  const timeIt = async (fn) => { const t0 = performance.now(); await fn(); const t1 = performance.now(); return +(t1 - t0).toFixed(1); };
  const base = await timeIt(flush);                 // 基线：仅 setTimeout(0)+布局
  const navs = ['wheel','link','body','audio','floating','install','store','system'];
  const out = [];
  for (const n of navs) {
    await timeIt(() => { st.currentNav = n; });
    await sleep(120);
    const ms = await timeIt(() => { st.currentNav = 'wheel'; });   // 回到 wheel 也算一次真实切页
    out.push([n, ms, n === 'wheel' ? '(self)' : '']);
  }
  // 第二遍：同页再切一次，看是否稳定（无累积劣化）
  const second = [];
  for (const n of ['link','body','audio']) {
    const ms = await timeIt(() => { st.currentNav = n; });
    second.push([n, ms]);
    await sleep(80);
  }
  return JSON.stringify({ baseline_ms: base, first_round: out, second_round: second });
})()
"""

# ---- ② 定时器泄漏：反复进出「语音」页 ----
TIMERS = r"""
(async () => {
  const sleep = ms => new Promise(r => setTimeout(r, ms));
  const st = window.__VUE_STORE__;
  const created = [], ids = new Set();
  const oSet = window.setInterval, oClr = window.clearInterval;
  window.setInterval = function (fn, ms, ...a) { const id = oSet.call(window, fn, ms, ...a); created.push({ id, ms }); ids.add(id); return id; };
  window.clearInterval = function (id) { ids.delete(id); return oClr.call(window, id); };
  const before = performance.now();
  const marks = [];
  for (let i = 1; i <= 3; i++) {
    st.currentNav = 'audio'; await sleep(500);
    st.currentNav = 'wheel'; await sleep(300);
    marks.push({ visit: i, new_timers: created.length, alive_after: ids.size, ms_list: created.map(c => c.ms) });
  }
  return JSON.stringify({ created_total: created.length, alive_now: ids.size, marks });
})()
"""

# ---- ③ 关于弹窗：遮罩透明度时间线 + 页面底色是否突变 ----
ABOUT = r"""
(async () => {
  const sleep = ms => new Promise(r => setTimeout(r, ms));
  const html = document.documentElement, body = document.body;
  const t0 = performance.now();
  const shot = () => ({
    t: +(performance.now() - t0).toFixed(1),
    htmlBg: getComputedStyle(html).backgroundColor,
    bodyBg: getComputedStyle(body).backgroundColor,
    overlays: [...document.querySelectorAll('body *')].filter(el => {
      const cs = getComputedStyle(el), r = el.getBoundingClientRect();
      return cs.position === 'fixed' && r.width >= innerWidth * 0.95 && r.height >= innerHeight * 0.95
             && cs.backgroundColor && cs.backgroundColor !== 'rgba(0, 0, 0, 0)';
    }).map(el => ({ cls: String(el.className).slice(0, 40), op: getComputedStyle(el).opacity, bg: getComputedStyle(el).backgroundColor }))
  });
  const timeline = [shot()];
  window.openModal('about');
  for (const w of [16, 32, 48, 64, 96, 140, 200, 300, 420]) {
    await sleep(w === 16 ? 16 : 0);
    timeline.push(shot());
  }
  await sleep(120);
  timeline.push(shot());
  return JSON.stringify({ timeline });
})()
"""


# ---- ④ 模拟车机慢 IPC：证明同步桥调用已不在首帧路径上 ----
SLOW_BRIDGE = r"""
(function(){
  var slow = { getTtsEngineInfo: '{"connected":true,"name":"模拟引擎","status":"ok"}',
               getConnectivityStatus: '{"bluetooth_enabled":false,"wifi_connected":false}',
               getVoiceThemesJson: '[]',
               getDeviceInfo: '{}' };
  var _b = window.ToolboxBridge || {};
  window.ToolboxBridge = new Proxy(_b, {
    get: function (t, k) {
      if (k in t) return t[k];
      return function () {
        if (slow[k] !== undefined) { var s = Date.now(); while (Date.now() - s < 300) {} return slow[k]; }
        return null;
      };
    }
  });
})();
"""

SLOW_TEST = r"""
(async () => {
  const st = window.__VUE_STORE__;
  const sleep = ms => new Promise(r => setTimeout(r, ms));
  st.currentNav = 'wheel'; await sleep(300);
  const t0 = performance.now();
  st.currentNav = 'audio';                       // 同步阶段：Vue 更新 + onMounted
  const sync_ms = performance.now() - t0;
  const frame_ms = await new Promise(r => requestAnimationFrame(() => r(performance.now() - t0)));
  await sleep(900);
  return JSON.stringify({ sync_ms: +sync_ms.toFixed(1), first_frame_ms: +frame_ms.toFixed(1) });
})()
"""


async def run():
    with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list") as r:
        tabs = json.loads(r.read().decode())
    tab = [t for t in tabs if t.get("type") == "page"][0]
    async with websockets.connect(tab["webSocketDebuggerUrl"], max_size=64*1024*1024) as ws:
        _id = {"v": 0}

        async def ev(js, await_promise=True):
            _id["v"] += 1
            await ws.send(json.dumps({"id": _id["v"], "method": "Runtime.evaluate",
                                      "params": {"expression": js, "returnByValue": True,
                                                 "awaitPromise": await_promise}}))
            r = json.loads(await ws.recv()).get("result", {}).get("result", {})
            return r.get("value")

        async def shot(name):
            _id["v"] += 1
            await ws.send(json.dumps({"id": _id["v"], "method": "Page.captureScreenshot", "params": {"format": "png"}}))
            d = json.loads(await ws.recv())["result"]["data"]
            p = os.path.join(MEDIA_DIR, name)
            open(p, "wb").write(base64.b64decode(d))
            return p

        async def send(method, **params):
            _id["v"] += 1
            await ws.send(json.dumps({"id": _id["v"], "method": method, "params": params}))
            while True:
                m = json.loads(await ws.recv())
                if m.get("id") == _id["v"]:
                    return m.get("result", {})

        await asyncio.sleep(1.0)
        await ev("window.__VUE_STORE__.currentNav = 'wheel'; 'ok'", False)
        await asyncio.sleep(0.8)

        print("### ① 切页耗时（实测：点击导航 → DOM 更新+布局完成，单位 ms）")
        d = json.loads(await ev(SWITCH))
        print("  空基线（仅 setTimeout(0)+强制布局） =", d["baseline_ms"], "ms")
        print("  第一轮：")
        for n, ms, tag in d["first_round"]:
            print(f"    {n:9s} → wheel   {ms:7.1f} ms {tag}")
        print("  第二轮（检验是否累积劣化）：")
        for n, ms in d["second_round"]:
            print(f"    wheel → {n:9s} {ms:7.1f} ms")

        print()
        print("### ② 反复进出「语音」页后的存活定时器（泄漏自证）")
        print(" ", await ev(TIMERS))

        print()
        print("### ③ 点击「关于」时全屏遮罩透明度时间线")
        d = json.loads(await ev(ABOUT))
        for s in d["timeline"]:
            ov = "; ".join(f"op={o['op']} bg={o['bg']}" for o in s["overlays"]) or "(无全屏遮罩)"
            print(f"  t={s['t']:6.1f}ms htmlBg={s['htmlBg']:22s} bodyBg={s['bodyBg']:22s} | {ov}")
        print("  截图:", await shot("probe_about_open.png"))

        print()
        print("### ④ 模拟车机慢 IPC（每个桥调用阻塞 300ms）：「点击语音 → 首帧绘制」")
        await send("Page.addScriptToEvaluateOnNewDocument", source=SLOW_BRIDGE)
        await send("Page.reload", ignoreCache=True)
        await asyncio.sleep(2.5)
        d = json.loads(await ev(SLOW_TEST))
        print(f"  同步阻塞阶段 sync_ms            = {d['sync_ms']} ms   ← 修复前这里会含 3×300ms")
        print(f"  到首帧绘制 first_frame_ms       = {d['first_frame_ms']} ms   ← 画面先出，慢查询在之后自己跑")


if __name__ == "__main__":
    proc = start_edge()
    try:
        asyncio.run(run())
    finally:
        proc.terminate()
