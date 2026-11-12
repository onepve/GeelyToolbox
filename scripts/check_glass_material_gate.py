#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
玻璃材质与配色真源门禁（Glass Material & Palette Source Gate）

锁定四个已真实踩过的坑，任何一项回归都会让车机界面视觉直接退化：

1) 老 WebView(Chrome 66) 上 filter:blur 会算错并让色块从屏幕边缘溢出，
   被车主识别成「下方浮出半截的未知窗口」——2026 年整层装饰光斑因此被删除。
   ⇒ 禁止在全局样式与主题层使用 filter: blur()。环境光必须用 radial-gradient。

2) 首帧主题引导脚本若用 background 简写写 <html>，会把 body 的环境光渐变
   整条重置为 none，导致「白天/黑夜切换后光效消失」。
   ⇒ 引导脚本只允许 backgroundColor。

3) 夜景卡片若使用不透明实色（#rrggbb / rgb() 不带 alpha），环境光透不上来，
   卡片会退化成「塑料贴片」——这是 UI 显得廉价的最直接原因。
   ⇒ 所有主题的 --bg-card / --bg-item 必须是 rgba() 且 alpha < 1。

4) 环境光色必须预先算成 rgba 落入 palette.js。
   Chrome 66 无 color-mix()，运行期无法混色，缺 --ambient-1/--ambient-2
   会让氛围层静默失效（不报错、但光完全看不见）。

退出码：0 全绿；1 有违规。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PALETTE = ROOT / "web/src/theme/palette.js"
APP_VUE = ROOT / "web/src/App.vue"
VITE_CFG = ROOT / "web/vite.config.js"
PROD_HTML = ROOT / "app/src/main/assets/toolbox_ui.html"

violations = []


def fail(msg):
    violations.append(msg)


def read(p):
    if not p.exists():
        fail("文件缺失: %s" % p.relative_to(ROOT))
        return ""
    return p.read_text(encoding="utf-8")


def _split_gradients(css):
    """把 CSS 里每个 radial-gradient(...) 切成独立字符串，便于逐层核对。"""
    out = []
    i = 0
    while True:
        start = css.find("radial-gradient(", i)
        if start < 0:
            break
        depth = 0
        for k in range(start + len("radial-gradient"), len(css)):
            if css[k] == "(":
                depth += 1
            elif css[k] == ")":
                depth -= 1
                if depth == 0:
                    out.append(css[start:k + 1])
                    i = k + 1
                    break
        else:
            break
    return out


def _brace_block(src, open_idx):
    """从 '{' 处做括号配对，返回 (块正文, 结束索引)。永不返回未绑定变量。"""
    depth = 0
    for k in range(open_idx, len(src)):
        if src[k] == "{":
            depth += 1
        elif src[k] == "}":
            depth -= 1
            if depth == 0:
                return src[open_idx:k + 1], k
    raise ValueError("括号不配对（配色真源可能已被改坏）")


def strip_comments(src):
    """去掉 JS/CSS 注释，避免把文档里的示例当成真实代码判违规。"""
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    src = re.sub(r"(?m)^\s*//.*$", "", src)
    return src


# ---------------------------------------------------------------------------
# 1) 禁 blur（老 WebView 溢出真凶）
# ---------------------------------------------------------------------------
app_src_raw = read(APP_VUE)
app_src = strip_comments(app_src_raw)
if re.search(r"filter\s*:\s*blur\s*\(", app_src):
    fail(
        "App.vue 出现 filter: blur() —— 老 WebView(Chrome 66) 上会算错并让色块"
        "溢出屏幕边缘（曾被车主误认为浮出半截窗口）。环境光请改用 radial-gradient。"
    )

# tailwind 的 blur-* / backdrop-blur-* 工具类同样受内核限制
for tool in re.findall(r"\b(?:backdrop-)?blur-(?!none)\w+", app_src):
    fail("App.vue 使用了 %s 工具类，老 WebView 不支持或会溢出。" % tool)

# ---------------------------------------------------------------------------
# 2) 首帧引导脚本只能用 backgroundColor，禁止 background 简写
# ---------------------------------------------------------------------------
vite_src = strip_comments(read(VITE_CFG))
if re.search(r"d\.style\.background\s*=", vite_src):
    fail(
        "vite.config.js 首帧引导脚本使用了 d.style.background 简写 —— 会把 body 的"
        "环境光 background-image 重置为 none，导致光效在切换昼夜后消失。"
        "必须使用 d.style.backgroundColor。"
    )

# ---------------------------------------------------------------------------
# 3) 卡片色必须半透明（实色 = 塑料贴片）
# ---------------------------------------------------------------------------
pal_raw = read(PALETTE)
pal = strip_comments(pal_raw)

# 取出所有主题块
theme_names = re.findall(r"^  ([a-z_]+): \{$", pal, re.M)
if not theme_names:
    fail("palette.js 未解析到任何主题块，配色真源可能已被改坏。")

TRANSPARENT_OK = re.compile(r"^rgba\(\s*[\d.]+\s*,\s*[\d.]+\s*,\s*[\d.]+\s*,\s*(0?\.\d+|\d+(\.\d+)?)\s*\)$")


def alpha_of(color):
    m = re.match(r"^rgba\(\s*[\d.]+\s*,\s*[\d.]+\s*,\s*[\d.]+\s*,\s*([\d.]+)\s*\)$", color)
    return float(m.group(1)) if m else None


checked_card = 0
for th in theme_names:
    i = pal.find("\n  %s: {" % th)
    if i < 0:
        continue
    blk, _ = _brace_block(pal, pal.index("{", i))

    for var in ("--bg-card", "--bg-item"):
        for m in re.finditer(r"'%s':\s*'([^']*)'" % re.escape(var), blk):
            val = m.group(1).strip()
            checked_card += 1
            if val.startswith("#"):
                fail(
                    "主题 %s 的 %s = %s 是不透明实色 —— 环境光透不上来，卡片会退化成"
                    "塑料贴片。必须改为 rgba(...) 且 alpha < 1。" % (th, var, val)
                )
                continue
            a = alpha_of(val)
            if a is None:
                fail("主题 %s 的 %s = %s 无法解析为 rgba()。" % (th, var, val))
            elif a >= 1.0:
                fail(
                    "主题 %s 的 %s alpha = %.2f，等于不透明 —— 玻璃质感失效。"
                    "要求 alpha < 1（夜景建议 0.42~0.60，白天 0.62~0.90）。" % (th, var, a)
                )

if checked_card == 0:
    fail("未在 palette.js 中检出任何 --bg-card / --bg-item，门禁失去保护对象。")

# ---------------------------------------------------------------------------
# 3b) 白天卡片必须「同色系」，严禁被洗成中性白（发白真凶）
#
#     真机实测（同像素开关卡法）：旧配方 --bg-card 近中性高不透明白 + 卡面白色
#     受光渐变，把主题色饱和度压掉 52%（卡面 S=15.5 vs 同像素背景 S=32.6）。
#     车上看就是「未激活卡片一片死白，跟主题背景完全脱节」。
#
#     业界口径（Material Design 3 surface-container / Apple HIG elevated）：
#     浅色主题的卡片不用纯白，而是同一 hue、明度比背景略低几级的 tinted surface。
#     ⇒ 白天 --bg-card 的通道极差（色相含量）>= 25，且亮度不得高于页面底色。
#     纯白 / 近白卡（极差 9~15）会被这条直接拦住。
# ---------------------------------------------------------------------------
MIN_CHROMA = 25          # 通道极差下限：低于它肉眼就判定为"白"
MAX_LUMA_OVER_BG = 6     # 卡片亮度允许比页面底色亮的余量


def _parse_color(val):
    """把 '#rrggbb' / 'rgba(r,g,b,a)' 统一解析成 (r,g,b,alpha)。"""
    val = val.strip()
    m = re.match(r"^#([0-9a-fA-F]{6})$", val)
    if m:
        h = m.group(1)
        return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 1.0
    m = re.match(r"^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+)\s*)?\)$", val)
    if m:
        return (float(m.group(1)), float(m.group(2)), float(m.group(3)),
                float(m.group(4)) if m.group(4) else 1.0)
    return None


def _luma(r, g, b):
    return 0.299 * r + 0.587 * g + 0.114 * b


day_card_checked = 0
for th in theme_names:
    i = pal.find("\n  %s: {" % th)
    if i < 0:
        continue
    blk, _ = _brace_block(pal, pal.index("{", i))
    mi = blk.find("\n    day: {")
    if mi < 0:
        continue
    mblk, _ = _brace_block(blk, blk.index("{", mi))

    m_bg = re.search(r"'--bg-main':\s*'([^']*)'", mblk)
    m_card = re.search(r"'--bg-card':\s*'([^']*)'", mblk)
    if not m_bg or not m_card:
        fail("主题 %s 的 day 档缺少 --bg-main 或 --bg-card，无法校验卡片色相一致性。" % th)
        continue
    bg_rgba = _parse_color(m_bg.group(1))
    card_rgba = _parse_color(m_card.group(1))
    if bg_rgba is None or card_rgba is None:
        fail("主题 %s 的 day 档 --bg-main/--bg-card 色值无法解析。" % th)
        continue

    day_card_checked += 1
    cr, cg, cb = card_rgba[0], card_rgba[1], card_rgba[2]
    chroma = max(cr, cg, cb) - min(cr, cg, cb)
    if chroma < MIN_CHROMA:
        fail(
            "主题 %s 的 day --bg-card = %s 通道极差仅 %d（要求 >= %d）—— "
            "这张卡已被洗成中性白，叠在带色背景上会「发白」，与主题脱节。"
            "必须改成同色系（同一 hue、明度略低于 %s 的 tinted surface）。"
            % (th, m_card.group(1), chroma, MIN_CHROMA, m_bg.group(1))
        )
    luma_card = _luma(cr, cg, cb)
    luma_bg = _luma(*bg_rgba[:3])
    if luma_card > luma_bg + MAX_LUMA_OVER_BG:
        fail(
            "主题 %s 的 day 卡片亮度 %.1f 高于页面底色 %.1f（允许 +%d）—— "
            "「比背景更亮的白卡」在带色背景上必然发白，且 sRGB 下无法靠调 alpha 挽回。"
            "浅色主题请让卡片明度持平或略低于背景。"
            % (th, luma_card, luma_bg, MAX_LUMA_OVER_BG)
        )

# ---------------------------------------------------------------------------
# 4) 每个主题每档必须有 rgba 形式的 --ambient-1 / --ambient-2
# ---------------------------------------------------------------------------
amb_checked = 0
for th in theme_names:
    i = pal.find("\n  %s: {" % th)
    blk, _ = _brace_block(pal, pal.index("{", i))

    for mode in ("night", "day"):
        mi = blk.find("\n    %s: {" % mode)
        if mi < 0:
            fail("主题 %s 缺少 %s 档。" % (th, mode))
            continue
        mblk, _ = _brace_block(blk, blk.index("{", mi))

        for var in ("--ambient-1", "--ambient-2"):
            m = re.search(r"'%s':\s*'([^']*)'" % var, mblk)
            amb_checked += 1
            if not m:
                fail(
                    "主题 %s 的 %s 档缺少 %s —— Chrome 66 无 color-mix()，"
                    "运行期无法混色，缺失会让环境光层静默失效（不报错但看不见光）。"
                    % (th, mode, var)
                )
                continue
            val = m.group(1).strip()
            if not re.match(r"^rgba\(", val):
                fail(
                    "主题 %s 的 %s(%s) = %s 不是 rgba() —— 必须预先算好带透明度的色值，"
                    "老内核无法在运行期做颜色混合。" % (th, var, mode, val)
                )

# ---------------------------------------------------------------------------
# 5) body 必须真的画出「4 层环境光」
#    只判断「有没有 radial-gradient」是不够的：删掉任意 1~3 层仍然能通过，
#    光效已经残缺但门禁无感。必须逐层核对 4 个定位锚点的完整清单。
# ---------------------------------------------------------------------------
AMBIENT_LAYERS = [
    ("12% 4%",   "--ambient-1"),   # 左上主光源
    ("90% 10%",  "--ambient-2"),   # 右上副光源
    ("58% 104%", "--ambient-1"),   # 底部中央反光
    ("26% 90%",  "--ambient-2"),   # 左下补光
]

# 取出 body 与 html.light body 两条规则，逐一核对四层是否齐备
body_rule = re.search(r"\nbody\s*\{([^}]*)\}", app_src)
light_rule = re.search(r"\nhtml\.light\s+body\s*\{([^}]*)\}", app_src)

for rule_name, rule in (("body", body_rule), ("html.light body", light_rule)):
    if not rule:
        fail("App.vue 缺少 %s 规则 —— 环境光氛围层已丢失，界面会退回纯色平板。" % rule_name)
        continue
    css = rule.group(1)
    layer_count = css.count("radial-gradient(")
    if layer_count < 4:
        fail(
            "%s 的环境光只有 %d 层 radial-gradient，要求完整 4 层 —— "
            "删减任意一层都会让光效残缺（少了主光源/反光，屏幕一侧会变死板）。"
            % (rule_name, layer_count)
        )
    # 把 CSS 拆成一个个 radial-gradient(...) 调用，逐个核对「该层用的哪个变量」。
    # 不能只查全文有没有出现 --ambient-N：任意一层被写死成固定色时全文仍然命中，
    # 会出现「断链但门禁无感」—— 那层颜色就再也跟不上换肤了。
    for idx, layer in enumerate(_split_gradients(css)):
        m_anchor = re.search(r"at\s+([\d.]+%\s+[\d.]+%)", layer)
        if not m_anchor:
            fail("%s 第 %d 层环境光缺少定位锚点（形如 at 12% 4%%）。" % (rule_name, idx + 1))
            continue
        anchor = re.sub(r"\s+", " ", m_anchor.group(1).strip())
        expect = dict(AMBIENT_LAYERS).get(anchor)
        if expect is None:
            fail(
                "%s 第 %d 层环境光的定位「%s」不在约定的 4 个锚点内 —— "
                "光源布局被改动，可能重新引入边缘溢出或光效偏斜。" % (rule_name, idx + 1, anchor)
            )
            continue
        # 该层必须引用约定变量，且不得写死任何具体颜色值
        if expect not in layer:
            fail(
                "%s 定位在「%s」的那层环境光没有引用 %s —— "
                "该层颜色被写死，换肤时会跟主题脱节（其余层会变，这层不变）。"
                % (rule_name, anchor, expect)
            )
        if re.search(r"rgba?\(|#[0-9a-fA-F]{3,8}", layer):
            fail(
                "%s 定位在「%s」的那层环境光写死了具体色值 —— "
                "必须只用 var(%s)，否则该层不跟随主题换色。" % (rule_name, anchor, expect)
            )

# ---------------------------------------------------------------------------
# 6) 旧版五套实色主题不得复活
# ---------------------------------------------------------------------------
LEGACY = ["blue", "brown", "green", "pink", "gold"]
revived = [t for t in theme_names if t in LEGACY]
if revived:
    fail(
        "已下线的 V1 实色主题被重新加入 palette.js：%s。"
        "这 5 套夜景为不透明实色，是 UI 廉价感的根源，且界面已不再提供选择。"
        % ", ".join(revived)
    )
if "V1_PALETTES" in pal:
    fail("palette.js 重新出现 V1_PALETTES —— 旧主题体系已整体下线，不应复活。")


# ---------------------------------------------------------------------------
# 7) 激活态可见性 —— 点亮与未点亮必须一眼分得清
#
#    实车回归教训：曾把激活态边框色写成 --accent-gold-bg（仅 14%~20% 不透明度），
#    压在深色卡片上与未激活态肉眼无差，用户原话「点亮和不点亮都是一样的显示，
#    导致没办法区分」。这类失效极其隐蔽 —— 代码在、样式也生效，只是看不出来。
#
#    同时明令禁止两类光污染（用户实车否决）：
#    a) 环绕流光/跑马灯：全站卡片同时跑，比常亮更晃眼。
#    b) 挂在卡片本体上的 keyframes：CSS 动画在元素挂载时自动播一次，
#       切 Tab 时整页卡片重新挂载 = 一页卡片全部闪一下。
# ---------------------------------------------------------------------------

# 7.1 激活态边框必须是主题色实线，不得用半透明 bg 色当边框
for sel_m in re.finditer(
    r"html(?:\.dark|\.light|:not\(\.light\))\s+\.bg-car-(?:card|item)\.border-car-accent[^{]*\{([^}]*)\}",
    app_src,
):
    block = sel_m.group(1)
    bc = re.search(r"border-color\s*:\s*([^;!]+)", block)
    if bc and "accent-gold-bg" in bc.group(1):
        fail(
            "激活态边框用了 --accent-gold-bg（半透明底色）—— 压在卡片上与未激活态"
            "肉眼无差，用户实车反馈「点亮和不点亮一样」。必须用 --accent-gold 实色。"
        )

# 7.2 夜间与白天激活态都必须写出 --accent-gold 实线边框（防止整段规则被删）
for scope, label in ((r"html\.dark|html:not\(\.light\)", "夜间"),
                     (r"html\.light", "白天")):
    found = False
    for m in re.finditer(
        r"((?:html\.dark|html:not\(\.light\)|html\.light)\s+"
        r"\.bg-car-(?:card|item)\.border-car-accent[^{]*)\{([^}]*)\}",
        app_src,
    ):
        sel, block = m.group(1), m.group(2)
        if not re.search(scope, sel):
            continue
        if re.search(r"border-color\s*:\s*var\(--accent-gold\)\s*!important", block):
            found = True
            break
    if not found:
        fail(
            "%s激活态缺少 `border-color: var(--accent-gold) !important` —— "
            "卡片点亮后没有任何可见边框，用户无法区分开关状态。" % label
        )

# 7.3 严禁环绕流光/跑马灯回归
for banned in ("accent-run", "accent-dim", "@keyframes accent-flash"):
    if banned in app_src:
        fail(
            "App.vue 重新出现 %s —— 环绕流光已按实车反馈整体下线"
            "（全站卡片同时跑 = 光污染）。请勿复活。" % banned
        )

# 7.4 严禁把 keyframes 挂在卡片本体上（切 Tab 会整页闪）
if re.search(r"\.bg-car-(?:card|item)\.border-car-accent\s*,\s*\n"
             r"\.bg-car-(?:card|item)\.border-car-accent\s*\{\s*\n?\s*animation\s*:",
             app_src):
    fail(
        "卡片激活态挂了 animation —— CSS 动画在元素挂载时自动播一次，"
        "切 Tab 整页卡片重挂载会全页闪一下（用户明确否决的光污染）。"
        "反馈只能用 transition。"
    )

# 7.5 卡片的过渡只能动 box-shadow / border-color，不得动 opacity
for tr in re.finditer(
    r"\.bg-car-(?:card|item)\s*,\s*\n?\.bg-car-(?:card|item)\s*\{([^}]*)\}",
    app_src,
):
    if re.search(r"transition[^;]*opacity", tr.group(1)):
        fail(
            "卡片 transition 里出现 opacity —— 会让整张卡片（含正文文字）一起变淡，"
            "文字跟着忽明忽暗。过渡只允许 box-shadow / border-color。"
        )

# 7.6 禁用 conic-gradient：Chrome 69+ 才支持，车机是 Chrome 66，
#     用了不会报错，只会整层流光静默消失。
for m in re.finditer(r"conic-gradient", app_src):
    fail(
        "App.vue 使用了 conic-gradient —— 车机 WebView 是 Chrome 66（需 69+），"
        "该属性会被静默忽略，流光完全不显示。请用 4 条 linear-gradient 拼边。"
    )
    break

# 7.7 禁用 @property：Chrome 85+，同样静默失效
if "@property" in app_src:
    fail("@property 需要 Chrome 85+，车机为 Chrome 66，自定义属性无法注册动画。")


# ---------------------------------------------------------------------------
# 8) 车机内核「通透配方」契约（Chromium 66 实测结论）
#
#    背景：车主长期反馈「效果图好看、上车没有通透感」。
#    真因是车机 WebView 为 Chrome 66，backdrop-filter 完全不存在 ——
#    网上 glassmorphism 教程 100% 依赖它，搬过来必然一场空。
#    唯一可行路线是「受光渐变 + 内高光 + 分层投影 + 噪点颗粒」，
#    且白天/夜间必须分开写（白天卡比背景亮 = 发白割裂；夜间卡比背景暗 = 糊成一片）。
#    下面把这条配方锁死，防止日后被"简化"回去。
# ---------------------------------------------------------------------------

# 8.1 卡片必须有受光渐变（background-image 纵向提亮），否则退回纯色平面
card_rule = re.search(r"\.bg-car-card\s*\{([^}]*)\}", app_src)
if not card_rule:
    fail("App.vue 找不到 .bg-car-card 规则 —— 卡片质感真源丢失。")
else:
    if "background-image" not in card_rule.group(1):
        fail(
            "卡片 .bg-car-card 缺少受光渐变 background-image —— "
            "车机无 backdrop-filter，去掉这层卡片会退回纯色平面，通透感彻底消失。"
        )

# 8.2 昼夜必须各有独立配方（不能一套参数走天下）
for scope, label in ((r"html\.light\s+\.bg-car-card", "白天"),
                     (r"html:not\(\.light\)\s+\.bg-car-card", "夜间")):
    if not re.search(scope + r"\s*\{", app_src):
        fail(
            "App.vue 缺少 %s 专属的 .bg-car-card 规则 —— "
            "昼夜必须分档：白天卡比背景亮会发白割裂，夜间卡比背景暗会糊成一片。" % label
        )

# 8.3 噪点层必须存在，且必须 pointer-events:none（否则挡住卡片点击）
if "feTurbulence" not in app_src:
    fail(
        "App.vue 丢失了 feTurbulence 噪点层 —— 这是 Chrome 66 上最接近真玻璃的"
        "唯一手段（backdrop-filter 不可用），删掉卡片会退回塑料贴片。"
    )
after_rule = re.search(r"\.bg-car-card::after\s*\{([^}]*)\}", app_src)
if not after_rule:
    fail("App.vue 找不到 .bg-car-card::after 噪点层规则。")
else:
    body = after_rule.group(1)
    if "pointer-events" not in body or "none" not in body:
        fail(
            "噪点层 .bg-car-card::after 缺少 pointer-events:none —— "
            "会盖住整张卡片拦截点击，卡片上的按钮全部点不动。"
        )
    # 严禁用 left/top/right/bottom 四连：Vite(esbuild) 会把它压成 inset:0，
    # 而 inset 简写要 Chrome 87+，车机 66 静默忽略 → 噪点层缩成 0 尺寸，
    # 表现是"代码在、样式没了"，且本地 Edge 上完全正常（已真实踩到）。
    if re.search(r"right\s*:\s*0", body) and re.search(r"bottom\s*:\s*0", body):
        fail(
            "噪点层用了 left/top/right/bottom 四连 —— 构建时会被压缩成 "
            "`inset:0`（Chrome 87+），车机 66 静默忽略后噪点层缩成 0 尺寸。"
            "必须改用 width:100% / height:100%。"
        )

# 8.4 产物里不得残留 inset 简写（构建后的兜底复核）
prod = read(PROD_HTML)
if prod and re.search(r"(?<![-\w])inset\s*:", prod):
    fail(
        "构建产物里出现 inset 简写（Chrome 87+，车机 66 静默忽略）。"
        "常见来源：源码写了 left/top/right/bottom 被压缩器自动合并，"
        "或直接写了 inset:0。必须改用 left/top + width/height。"
    )



# ---------------------------------------------------------------------------
def main():
    print("=" * 70)
    print(">> 玻璃材质与配色真源门禁 (Glass Material & Palette Source Gate)")
    print("=" * 70)
    print("检查项: blur 禁用 / 引导脚本简写 / 卡片半透明 / 环境光 rgba / 旧主题不复活 / 跑马灯契约")
    print("统计: 主题 %d 套，卡片色 %d 项，环境光 %d 项"
          % (len(theme_names), checked_card, amb_checked))
    print("-" * 70)

    if violations:
        for v in violations:
            print("  [FAIL] " + v)
        print("-" * 70)
        print("!! 玻璃材质门禁未通过，共 %d 项违规。" % len(violations))
        return 1

    print("  [PASS] 卡片全部为半透玻璃色（无实色贴片）")
    print("  [PASS] 环境光为预计算 rgba（老内核可渲染）")
    print("  [PASS] 全局无 filter:blur（不会溢出屏幕边缘）")
    print("  [PASS] 首帧引导脚本未用 background 简写")
    print("  [PASS] 已下线 V1 实色主题未复活")
    print("-" * 70)
    print("[PASS] 玻璃材质与配色真源门禁全绿。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
