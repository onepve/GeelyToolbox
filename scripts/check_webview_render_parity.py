#!/usr/bin/env python3
"""车机 WebView 渲染一致性门禁 (ui-webview-render-parity)

为什么需要这个门禁
------------------
同一份产物 app/src/main/assets/toolbox_ui.html，在本地 Edge 上出图「松、透气」，
在车机 Chromium 66 上「挤、矮、字贴边」，导致每次调 UI 都要反复上车实测纠偏。

2026-11 实测复盘出的三类真实差异（本门禁把它们全部钉死）：

1) 竖向被压扁（主因，占差异绝大多数）
   车机 section 是 overflow-y-auto 的纵向 flex 容器，子视图是 flex item。
   Chromium 66 对 flex item 的 min-height:auto 不按内容计算（算作 0），
   内容超高时浏览器选择 flex-shrink 压缩而不是滚动：
     首页大卡 98px → 73px，内容栈 732px → 616px。
   本地新版 Edge 会先撑到自然高再滚动。
   => 断言：section 的任一直接子项不得被压缩（scrollHeight > 高度+1 即失败）
      且首页大卡高度必须等于自然高。

2) 老内核丢弃 preflight 的 :where() 规则
   preflight 用 button,input:where([type=button]){background-color:transparent}
   抹平控件默认灰底。Chromium 66 不认识 :where()（Chrome 88+），整条规则被丢弃，
   于是没显式写 bg-* 的按钮在车机上露出 UA 灰底 rgb(221,221,221)。
   => 断言：产物 CSS 不得出现 :is( / :where(（并列 ui-old-engine-css 门禁）
      且裸 button 计算底色必须是 transparent。

3) flex 容器上的 gap: 无效
   flex gap 需要 Chrome 84+，车机完全忽略 → 间距丢失。
   => 断言：产物里带 gap: 声明的规则，其选择器不得是 flex 工具类。

运行前提
--------
需要一个可用的 Chromium 66 快照（车机同款内核）。默认路径
/data/tmp/chromium66/x/chrome-linux/chrome，可用 CHROME66 环境变量覆盖。
容器不存在时本门禁 SKIP（exit 0）并明确打印原因，不阻塞 CI。

用法
----
    python3 scripts/check_webview_render_parity.py
    CHROME66=/path/to/chrome python3 scripts/check_webview_render_parity.py
"""
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HTML = os.path.join(ROOT, 'app/src/main/assets/toolbox_ui.html')
CHROME66 = os.environ.get('CHROME66', '/data/tmp/chromium66/x/chrome-linux/chrome')

# 车机可用窗口：guard.log 实测 wm size 1920x720，但 appBounds 只有 1760x720
# （右侧 160px 被系统侧栏吃掉）。出图必须按 appBounds，不能按 wm size。
VW, VH = 1760, 720

FAILS = []


def fail(msg):
    FAILS.append(msg)


def free_port():
    s = socket.socket()
    s.bind(('127.0.0.1', 0))
    p = s.getsockname()[1]
    s.close()
    return p


def wait_devtools(port, timeout=25):
    end = time.time() + timeout
    while time.time() < end:
        try:
            with urllib.request.urlopen(f'http://127.0.0.1:{port}/json/list', timeout=2) as r:
                tabs = json.loads(r.read().decode())
                pages = [t for t in tabs if t.get('type') == 'page']
                if pages:
                    return pages[0]['webSocketDebuggerUrl']
        except Exception:
            pass
        time.sleep(0.5)
    return None


def cdp_eval(ws_url, expr):
    """极简 CDP：只用 Runtime.evaluate，避免引入 websockets 依赖时另开分支。"""
    try:
        from websockets.sync.client import connect
    except Exception:
        return None
    with connect(ws_url, max_size=64 * 1024 * 1024) as ws:
        ws.send(json.dumps({'id': 1, 'method': 'Runtime.evaluate',
                            'params': {'expression': expr, 'returnByValue': True}}))
        while True:
            msg = json.loads(ws.recv())
            if msg.get('id') == 1:
                r = msg.get('result', {})
                if 'exceptionDetails' in r:
                    return None
                return r.get('result', {}).get('value')


PROBE = r"""
(() => {
  const out = {vp:[innerWidth,innerHeight], cards:[], compressed:[], bareButtonBg:null,
               flexGapDecls:[], unsupported:{}};
  const sec = document.querySelector('section');
  if (sec) {
    [...sec.children].forEach(ch => {
      const r = ch.getBoundingClientRect();
      if (ch.scrollHeight > r.height + 1) {
        out.compressed.push({cls:(ch.className||'').toString().slice(0,50),
                             h:+r.height.toFixed(1), sh:ch.scrollHeight});
      }
    });
  }
  const cards = [...document.querySelectorAll('main div')].filter(d => {
    const c = (d.className||'').toString();
    return /rounded-3xl/.test(c) && /bg-car-card/.test(c);
  });
  out.cards = cards.slice(0,6).map(d => +d.getBoundingClientRect().height.toFixed(1));
  const b = document.createElement('button'); b.textContent='x';
  document.body.appendChild(b);
  out.bareButtonBg = getComputedStyle(b).backgroundColor;
  b.remove();
  return out;
})()
"""


def scan_css():
    """静态扫产物 CSS：老内核不支持的写法 + flex gap。"""
    if not os.path.exists(HTML):
        fail(f'产物不存在：{HTML}（先跑 web/pnpm build）')
        return
    s = open(HTML, encoding='utf-8', errors='replace').read()
    css = '\n'.join(re.findall(r'<style[^>]*>(.*?)</style>', s, re.S))
    if not css:
        fail('产物里没有 <style>，无法审查')
        return
    # 1) 选择器级：:is() / :where() 会让整条规则在 Chromium 66 上消失
    for tok, since in ((':is(', 'Chrome 88+'), (':has(', 'Chrome 105+')):
        n = len(re.findall(re.escape(tok), css))
        if n:
            fail(f'产物 CSS 含 {tok}…{n} 处（{since}，车机 Chromium 66 整条规则丢弃）'
                 f'——改用不带该伪类的直白选择器')

    # :where() 单独处理：Tailwind preflight 自带几条 :where() 兜底规则，
    # 引擎不认识就整条丢弃。降级本身无解（preflight 是生成的），
    # 但必须在 App.vue 里用不带 :where 的选择器把关键兜底重新声明一遍，
    # 否则按钮露 UA 灰底、[hidden] 失效 —— 这两个都是真实发生过的车机事故。
    where_rules = []
    for m in re.finditer(r'([^{}]+)\{([^{}]*)\}', css):
        if ':where(' in m.group(1):
            where_rules.append((m.group(1).strip(), m.group(2).strip()))
    fallback_need = {'button-grey': False, 'hidden': False}
    for sel, body in where_rules:
        if 'background-color:transparent' in body and 'button' in sel:
            fallback_need['button-grey'] = True
        if 'display:none' in body and 'hidden' in sel:
            fallback_need['hidden'] = True
    app_vue = open(os.path.join(ROOT, 'web/src/App.vue'), encoding='utf-8', errors='replace').read()
    if fallback_need['button-grey']:
        if not re.search(r'button\s*,\s*\n?\s*input\[type="button"\]', app_vue):
            fail('preflight 用 :where() 抹平控件灰底，车机会丢弃该规则；'
                 'App.vue 缺少不带 :where 的等价声明 —— 车机上按钮会露 UA 灰底 rgb(221,221,221)')
    if fallback_need['hidden']:
        if not re.search(r'\[hidden\]\s*\{\s*\n?\s*display:\s*none', app_vue):
            fail('preflight 的 [hidden]:where(...) 车机会丢弃；'
                 'App.vue 缺少 [hidden]{display:none} 兜底 —— 车机上 hidden 元素不会隐藏')

    # 滚动容器防压缩：section 的子视图必须是 flex-shrink:0
    # 这条必须静态查源码，不能只靠运行时探针——探针跑的是已构建产物，
    # 改了 App.vue 但没重建，运行时看到的还是旧产物，回退会溜过去。
    if not re.search(r'section\s*>\s*\*\s*\{\s*\n?\s*flex-shrink:\s*0', app_vue):
        fail('App.vue 缺少 `section > * { flex-shrink: 0 }` —— 车机 Chromium 66 会把滚动容器里的'
             '子视图压扁而不是滚动（卡片 98px→73px），与本地出图版式完全不同')
    # 2) 属性级
    prop_checks = [
        (r'aspect-ratio\s*:', 'aspect-ratio', 'Chrome 88+'),
        (r'(?<![-\w])inset\s*:', 'inset 简写', 'Chrome 87+'),
        (r'(?<![-\w])(?:min|max|clamp)\(', 'min()/max()/clamp()', 'Chrome 79+'),
        (r'overflow\s*:\s*clip', 'overflow:clip', 'Chrome 90+'),
        (r'color-mix\(', 'color-mix()', 'Chrome 111+'),
        (r'\d(?:dvh|svh|lvh)', 'dvh/svh/lvh', 'Chrome 108+'),
        (r'(?<![-\w])(?:translate|rotate|scale)\s*:\s*[-\d.]', '独立 translate/rotate/scale', 'Chrome 104+'),
    ]
    for pat, label, since in prop_checks:
        n = len(re.findall(pat, css))
        if n:
            fail(f'产物 CSS 含 {label} {n} 处（{since}，车机静默忽略）')
    # 3) flex 容器上的 gap（flex gap 要 Chrome 84+，车机静默失效）
    #    注意：grid 容器的 gap 从 Chrome 57 起就支持，不算问题。
    #    产物 CSS 里 .gap-N 是通用间距类，只有被 flex 容器使用时才失效，
    #    因此这里查源码里「同一 class 属性内既有 flex 又有 gap-*」的真实用法。
    src_root = os.path.join(ROOT, 'web/src')
    flex_gap_hits = []
    for dp, _, fs in os.walk(src_root):
        for fn in fs:
            if not fn.endswith('.vue'):
                continue
            fp = os.path.join(dp, fn)
            txt = open(fp, encoding='utf-8', errors='replace').read()
            for m in re.finditer(r'class="([^"]*)"', txt):
                toks = m.group(1).split()
                if 'flex' not in toks or 'grid' in toks:
                    continue
                gaps = [t for t in toks if re.match(r'gap[xy]?-[0-9.]', t)]
                if gaps:
                    ln = txt[:m.start()].count('\n') + 1
                    flex_gap_hits.append(f'{os.path.relpath(fp, ROOT)}:{ln} {",".join(gaps)}')
    for hit in flex_gap_hits[:8]:
        fail(f'flex 容器上用 gap-*（车机 Chromium 66 不支持 flex gap）：{hit}'
             f' —— 改用 space-x/space-y 或给子元素加 margin')


def runtime_probe():
    if not os.path.exists(CHROME66):
        return 'SKIP'
    if not os.path.exists(HTML):
        return 'SKIP'
    port = free_port()
    env = dict(os.environ)
    # 本地自带字体映射（中文渲染需要）；CI 上没有该文件就不设，用系统字体
    fc = env.get('FONTCONFIG_FILE') or '/data/tmp/chromium66/fc/fonts.conf'
    if os.path.exists(fc):
        env['FONTCONFIG_FILE'] = fc
    else:
        env.pop('FONTCONFIG_FILE', None)
    proc = subprocess.Popen(
        [CHROME66, '--headless', '--disable-gpu', '--no-sandbox',
         f'--remote-debugging-port={port}', f'--window-size={VW},{VH}',
         '--hide-scrollbars', 'file://' + HTML],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, env=env,
    )
    try:
        ws = wait_devtools(port)
        if not ws:
            fail('Chromium 66 起来了但 DevTools 没响应')
            return 'FAIL'
        # 进 day 模式 + 首页，等渲染稳定
        cdp_eval(ws, "localStorage.setItem('has_shown_welcome_donate','true');1")
        cdp_eval(ws, "localStorage.setItem('toolbox_theme_v2',"
                     "JSON.stringify({palette:'sky_ocean',mode:'day'}));1")
        cdp_eval(ws, 'location.reload();1')
        time.sleep(3)
        cdp_eval(ws, "(()=>{window.__VUE_STORE__.modals={};"
                     "window.__VUE_STORE__.currentNav='link';return 1})()")
        time.sleep(1.5)
        r = cdp_eval(ws, PROBE)
        if not r:
            fail('Chromium 66 探针执行失败（页面未挂载？）')
            return 'FAIL'
        # 断言 1：视口必须是车机 appBounds
        w, h = r['vp']
        if (w, h) != (VW, VH):
            fail(f'探针视口 {w}x{h} ≠ 车机 appBounds {VW}x{VH}')
        # 断言 2：不得有子项被压缩
        if r['compressed']:
            detail = '; '.join(f"{c['cls'][:26]} {c['h']}px(内容{c['sh']}px)" for c in r['compressed'][:3])
            fail(f'滚动容器里的子视图被 flex-shrink 压扁（车机压缩、本地撑开，版式必然不一致）：{detail}'
                 f' —— 修法：section > * {{ flex-shrink: 0 }}')
        # 断言 3：首页大卡必须是自然高（>=90px，压缩后会掉到 73px）
        if r['cards'] and r['cards'][0] < 90:
            fail(f"首页大卡高度 {r['cards'][0]}px < 90px，疑似被压缩（自然高应约 98px）")
        # 断言 4：裸按钮不得是 UA 灰底（preflight :where() 失效的活证据）
        bg = (r['bareButtonBg'] or '').replace(' ', '')
        if bg in ('rgb(221,221,221)', 'rgb(239,239,239)', 'rgb(192,192,192)'):
            fail(f'裸 button 底色 {bg} = 浏览器 UA 默认灰底，说明 preflight 的 :where() 规则'
                 f'在车机被丢弃了 —— 必须在 App.vue 里用不带 :where 的选择器重新声明')
        return 'PASS'
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=8)
        except Exception:
            proc.kill()


def main():
    scan_css()
    state = runtime_probe()
    if state == 'SKIP':
        print(f'[SKIP] 未找到车机内核 Chromium 66 快照：{CHROME66}')
        print('       设置 CHROME66=/path/to/chrome 后重跑才能真正比对；静态扫描已执行。')
    print()
    if FAILS:
        print(f'❌ ui-webview-render-parity 失败 {len(FAILS)} 项：')
        for i, f in enumerate(FAILS, 1):
            print(f'  {i}. {f}')
        print('\n这些差异在本地 Edge 上完全看不出来，只有上车才暴露。')
        sys.exit(1)
    print('✅ ui-webview-render-parity 通过：产物与车机 Chromium 66 渲染基线一致')


if __name__ == '__main__':
    main()
