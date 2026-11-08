#!/usr/bin/env python3
"""真机/模拟器 CDP 实测：点「打开蓝牙设置」「打开 Wi-Fi 设置」按钮，
测『点击→系统设置窗口出现』耗时 + 是否弹系统应用选择器（两个窗口/要车主挑）。
用法: python3 probe_settings_launch.py [ws_url]"""
import json, subprocess, sys, time, urllib.request

WS_HTTP = "http://127.0.0.1:19122/json"
FOCUS_CMD = "dumpsys window | grep mCurrentFocus"

def get_ws():
    pages = json.load(urllib.request.urlopen(WS_HTTP, timeout=5))
    for p in pages:
        if "吉利智驾" in p.get("title", ""):
            return p["webSocketDebuggerUrl"]
    raise SystemExit(f"未找到吉利智驾页面: {[p.get('title') for p in pages]}")

def main():
    ws_url = sys.argv[1] if len(sys.argv) > 1 else get_ws()
    try:
        from websocket import create_connection
    except ImportError:
        sys.exit("pip install websocket-client")
    ws = create_connection(ws_url, timeout=30)
    _id = [0]

    def ev(expr, await_promise=False):
        _id[0] += 1
        ws.send(json.dumps({"id": _id[0], "method": "Runtime.evaluate",
                            "params": {"expression": expr, "returnByValue": True,
                                       "awaitPromise": await_promise}}))
        while True:
            m = json.loads(ws.recv())
            if m.get("id") == _id[0]:
                return m.get("result", {}).get("result", {}).get("value")

    def focus():
        return subprocess.run(["adb", "shell", FOCUS_CMD],
                              capture_output=True, text=True, timeout=10).stdout.strip()

    def back_to_app():
        subprocess.run(["adb", "shell", "input", "keyevent", "4"], capture_output=True)
        time.sleep(1.2)

    print("=== 前置：进入「座舱语音」页 ===")
    ev("document.querySelector('[data-nav=\"audio\"], .nav-item:nth-child(3)')?.click(); 1")
    time.sleep(2)

    results = {}
    for label, txt in (("蓝牙设置", "蓝牙设置"), ("WiFi设置", "Wi-Fi 设置")):
        print(f"\n=== 点「打开{label}」 ===")
        f0 = focus()
        t0 = time.time()
        ev(f"""(() => {{
          const els = [...document.querySelectorAll('button, .btn, div, span')]
            .filter(e => e.children.length === 0 && e.textContent.includes('{txt}'));
          (els[els.length - 1] || els[0])?.click(); return 1; }})()""")
        # 轮询等窗口切换（最多 8s，每 200ms 采一次）
        delay = None; seen = []
        while time.time() - t0 < 8.0:
            f1 = focus()
            if f1 != f0 and "geelyconsole" not in f1:
                delay = time.time() - t0
                seen.append(f1)
                break
            time.sleep(0.2)
        time.sleep(1.0)  # 等可能的第二个窗口
        f2 = focus()
        # 若有系统 chooser，通常标题含 ResolveActivity / ChooserActivity
        chooser = subprocess.run(
            ["adb", "shell", "dumpsys activity activities | grep -i -E 'Resolver|Chooser' | head -2"],
            capture_output=True, text=True, timeout=10).stdout.strip()
        results[label] = {"点击→窗口耗时s": round(delay, 2) if delay else "未切出(≤8s)",
                          "落地窗口": f2[:110], "有应用选择器": bool(chooser)}
        print(json.dumps(results[label], ensure_ascii=False, indent=2))
        back_to_app()

    print("\n=== 结论 ===")
    ok = all(isinstance(v["点击→窗口耗时s"], float) and v["点击→窗口耗时s"] < 1.5
             and not v["有应用选择器"] for v in results.values())
    print("PASS ✅ 秒开且无应用选择器" if ok else "FAIL ❌ 仍有卡顿或选择器", json.dumps(results, ensure_ascii=False))

if __name__ == "__main__":
    main()
