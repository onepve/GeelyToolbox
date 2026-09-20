#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""探针：车身联动 360/车速 页 · 卡片4 与卡片5 等高核验（1920x720 车机分辨率）"""
import json, os, time, subprocess, urllib.request, signal, sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIST = os.path.join(REPO, "web", "dist", "index.html")
PORT = 9351
SHOT = os.path.join(REPO, "scripts", "shots", "linkcard_heights.png")
UDIR = "/tmp/.edge-probe-linkcard"

def kill_edge():
    subprocess.run(["pkill", "-f", "remote-debugging-port=%d" % PORT], capture_output=True)
    time.sleep(1.5)
    subprocess.run(["pkill", "-9", "-f", "remote-debugging-port=%d" % PORT], capture_output=True)
    subprocess.run(["rm", "-rf", UDIR], capture_output=True)

def cdp_send(ws, mid, method, params=None):
    import base64
    msg = json.dumps({"id": mid, "method": method, "params": params or {}})
    ws.send(msg)
    while True:
        raw = ws.recv()
        data = json.loads(raw)
        if data.get("id") == mid:
            return data.get("result", {})

def main():
    kill_edge()
    ws = None
    try:
        proc = subprocess.Popen([
            "microsoft-edge", "--headless=new",
            "--remote-debugging-port=%d" % PORT,
            "--user-data-dir=%s" % UDIR,
            "--no-sandbox", "--disable-gpu",
            "--window-size=1920,720", "about:blank",
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        deadline = time.time() + 20
        target = None
        while time.time() < deadline:
            try:
                tabs = json.loads(urllib.request.urlopen(
                    "http://127.0.0.1:%d/json/list" % PORT, timeout=2).read())
                pages = [t for t in tabs if t.get("type") == "page"]
                if pages:
                    target = pages[0]
                    break
            except Exception:
                time.sleep(0.4)
        if not target:
            print(json.dumps({"error": "edge devtools not ready"}))
            return 1
        from websockets.sync.client import connect
        ws = connect(target["webSocketDebuggerUrl"], max_size=50 * 1024 * 1024)
        mid = [0]

        def send(method, params=None):
            mid[0] += 1
            return cdp_send(ws, mid[0], method, params)

        send("Emulation.setDeviceMetricsOverride",
             {"width": 1920, "height": 720, "deviceScaleFactor": 1, "mobile": False})
        send("Page.enable")
        send("Page.navigate", {"url": "file://" + DIST})
        time.sleep(2.5)
        # 切到 车身联动 页
        send("Runtime.evaluate", {"expression":
             "window.__VUE_STORE__ && (window.__VUE_STORE__.currentNav = 'link'); 'ok'",
             "returnByValue": True})
        time.sleep(1.2)
        # 量测卡片4/5
        js = r"""
(() => {
  function findCard(txt) {
    const els = Array.from(document.querySelectorAll('div.rounded-3xl'));
    for (const el of els) {
      if (el.textContent.includes(txt)) {
        // 只取最外层卡片（自身 class 含 bg-car-card 且直接是 grid 子元素）
        let c = el;
        while (c && c.parentElement && !c.parentElement.classList.contains('grid')) c = c.parentElement;
        if (c) return c;
      }
    }
    return null;
  }
  function rect(el) {
    if (!el) return null;
    const r = el.getBoundingClientRect();
    return {top: Math.round(r.top), bottom: Math.round(r.bottom),
            height: Math.round(r.height), width: Math.round(r.width)};
  }
  const c4 = findCard('4. 车速达标智能启播车载音乐');
  const c5 = findCard('5. 车速达标自定义动作与唤起应用');
  const grid = c4 && c4.parentElement;
  return JSON.stringify({
    card4: rect(c4), card5: rect(c5),
    grid: rect(grid),
    scrollH: document.documentElement.scrollHeight,
    innerH: window.innerHeight,
    equal: c4 && c5 && Math.abs(c4.getBoundingClientRect().height - c5.getBoundingClientRect().height) <= 2
  });
})()
"""
        res = send("Runtime.evaluate", {"expression": js, "returnByValue": True})
        val = res.get("result", {}).get("value", "{}")
        print("MEASURE:", val)
        # 截图存档
        os.makedirs(os.path.dirname(SHOT), exist_ok=True)
        import base64 as b64
        shot = send("Page.captureScreenshot", {"format": "png"})
        with open(SHOT, "wb") as f:
            f.write(b64.b64decode(shot["data"]))
        print("SHOT:", SHOT)
        return 0
    finally:
        try:
            if ws:
                ws.close()
        except Exception:
            pass
        kill_edge()
        left = subprocess.run(["pgrep", "-fc", "remote-debugging-port=%d" % PORT],
                              capture_output=True, text=True).stdout.strip()
        print("EDGE_LEFT:", left)

if __name__ == "__main__":
    sys.exit(main() or 0)
