#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""边框密度巡检：1920x720 真车视口逐视图截图 + DOM 实测每一处 border 的实际渲染宽度。

为什么不能只数 class：Tailwind 的 border / border-2 / border-b 在旧 WebView 上还可能被
全局样式覆盖，只有 getComputedStyle 的 borderTopWidth 才是车主真正看到的线。本脚本同时
统计「一根卡片里套了几层有边框的框」（洋葱套娃）与「点亮态 border-car-accent 的数量」。
"""
import os, sys, json, time, base64, asyncio, subprocess, urllib.request, websockets

ROOT = "/data/projects/GeelyToolbox"
HTML = f"file://{ROOT}/app/src/main/assets/toolbox_ui.html"
OUT = "/data/tmp/ui_audit"
PORT = 9333
os.makedirs(OUT, exist_ok=True)

VIEWS = [("link", "行车联动"), ("body", "车载声音"), ("fuel", "行程油耗"),
         ("store", "应用生态"), ("system", "系统设置")]

PROBE_JS = r"""(() => {
  const out = {view: null, bordered: 0, thick: 0, accent: 0, maxDepth: 0, depthHist: {}, samples: []};
  const vis = el => {
    const r = el.getBoundingClientRect();
    if (r.width < 30 || r.height < 20) return false;
    const s = getComputedStyle(el);
    return s.display !== 'none' && s.visibility !== 'hidden' && parseFloat(s.opacity) > 0.05;
  };
  const hasBorder = el => {
    const s = getComputedStyle(el);
    const w = ['Top','Right','Bottom','Left'].map(d => parseFloat(s['border'+d+'Width']) || 0);
    return Math.max(...w) > 0.4;
  };
  const isAccent = el => {
    const s = getComputedStyle(el);
    return ['Top','Right','Bottom','Left'].some(d => {
      const c = s['border'+d+'Color'] || '';
      return parseFloat(s['border'+d+'Width']) > 0.4 && /var\(--accent|244, 1|2\d\d, 1[0-9]\d, \d/.test(c);
    });
  };
  const accColor = getComputedStyle(document.documentElement).getPropertyValue('--accent-gold');
  const accRgb = accColor.trim();
  const all = [...document.querySelectorAll('main *')].filter(vis);
  for (const el of all) {
    if (!hasBorder(el)) continue;
    out.bordered++;
    const s = getComputedStyle(el);
    const mw = Math.max(...['Top','Right','Bottom','Left'].map(d => parseFloat(s['border'+d+'Width']) || 0));
    if (mw >= 1.6) out.thick++;
    // 灯亮态：任一方向边框色等于主题强调色
    const lit = ['Top','Right','Bottom','Left'].some(d => {
      const w = parseFloat(s['border'+d+'Width']) || 0;
      if (w < 1.6) return false;
      const c = s['border'+d+'Color'];
      if (!accRgb) return false;
      // 用 canvas 把变量色解析成 rgb 再比
      return c === accRgb;
    });
    if (lit) { out.accent++; if (out.samples.length < 4) out.samples.push((el.className||'').toString().slice(0,90)); }
    // 洋葱深度：从自身往上数还有几层带边框的祖先
    let d = 0, p = el.parentElement;
    while (p && p.tagName !== 'MAIN') {
      if (hasBorder(p) && vis(p)) d++;
      p = p.parentElement;
    }
    out.maxDepth = Math.max(out.maxDepth, d);
    out.depthHist[d] = (out.depthHist[d] || 0) + 1;
  }
  return out;
})()"""


def start_edge():
    subprocess.run(["pkill", "-f", f"--remote-debugging-port={PORT}"], stderr=subprocess.DEVNULL)
    time.sleep(0.5)
    proc = subprocess.Popen([
        "/usr/bin/microsoft-edge", "--headless=new",
        f"--remote-debugging-port={PORT}", "--no-sandbox", "--disable-gpu",
        "--window-size=1920,720", HTML
    ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
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

        async def evaluate(js):
            sid["v"] += 1
            await ws.send(json.dumps({"id": sid["v"], "method": "Runtime.evaluate",
                                      "params": {"expression": js, "returnByValue": True}}))
            res = json.loads(await ws.recv())
            return res.get("result", {}).get("result", {}).get("value")

        async def cmd(method, **params):
            sid["v"] += 1
            await ws.send(json.dumps({"id": sid["v"], "method": method, "params": params}))
            return json.loads(await ws.recv())

        await cmd("Emulation.setDeviceMetricsOverride", width=1920, height=720,
                  deviceScaleFactor=1, mobile=False)
        # 关掉欢迎/赞赏弹窗，避免遮挡与半透明重影
        await evaluate("localStorage.setItem('has_shown_welcome_donate','true'); 'ok'")
        await evaluate("(() => { const s=window.__VUE_STORE__; if(s){ s.modals={}; } return 'ok'; })()")
        await asyncio.sleep(1.0)

        report = []
        for key, name in VIEWS:
            await evaluate(f"window.__VUE_STORE__.currentNav = '{key}'; 'ok'")
            await asyncio.sleep(1.2)
            data = await evaluate(PROBE_JS)
            if isinstance(data, dict):
                data["view"] = name
                report.append(data)
            shot = await cmd("Page.captureScreenshot", format="png")
            png = base64.b64decode(shot["result"]["data"])
            path = os.path.join(OUT, f"{key}.png")
            open(path, "wb").write(png)
            print(f"[{name}] 边框元素={data['bordered']} 粗线(≥1.6px)={data['thick']} "
                  f"点亮态={data['accent']} 最大套娃深度={data['maxDepth']} 图={path}")

        json.dump(report, open(os.path.join(OUT, "report.json"), "w"), ensure_ascii=False, indent=2)
        tot = {k: sum(r[k] for r in report) for k in ("bordered", "thick", "accent")}
        print("\n五视图合计:", tot)
        for r in report:
            print(f"  {r['view']}: 厚度分布={r['depthHist']}")


if __name__ == "__main__":
    proc = start_edge()
    try:
        asyncio.run(run())
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except Exception:
            proc.kill()
        subprocess.run(["pkill", "-f", f"--remote-debugging-port={PORT}"], stderr=subprocess.DEVNULL)
