#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
玻璃材质门禁的变异测试：故意把每个已修复的 bug 重新注入，
必须全部被拦下。任何一条「注入后仍然 PASS」都说明门禁是纸糊的。
"""
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GATE = ROOT / "scripts/check_glass_material_gate.py"
PALETTE = ROOT / "web/src/theme/palette.js"
APP = ROOT / "web/src/App.vue"
VITE = ROOT / "web/vite.config.js"

CASES = []


def case(name, path, old, new, expect_keyword):
    CASES.append((name, path, old, new, expect_keyword))


# 1) 实色卡片复活（塑料贴片真凶）
case("实色卡片复活",
     PALETTE,
     "'--bg-card': 'rgba(22,32,50,.58)'",
     "'--bg-card': '#162032'",
     "不透明实色")

# 2) 卡片 alpha = 1（等同于不透明）
case("卡片 alpha=1",
     PALETTE,
     "'--bg-card': 'rgba(22,32,50,.58)'",
     "'--bg-card': 'rgba(22,32,50,1)'",
     "等于不透明")

# 3) 环境光缺失
case("环境光变量被删",
     PALETTE,
     "      '--ambient-1': 'rgba(56,189,248,0.30)',\n",
     "",
     "缺少 --ambient-1")

# 4) 环境光写成实色（老内核无法混色）
case("环境光写成实色",
     PALETTE,
     "'--ambient-1': 'rgba(56,189,248,0.30)'",
     "'--ambient-1': '#38bdf8'",
     "不是 rgba()")

# 5) 首帧脚本用 background 简写（光效被抹掉）
case("引导脚本用 background 简写",
     VITE,
     "d.style.backgroundColor=v['--bg-main'];",
     "d.style.background=v['--bg-main'];",
     "background 简写")

# 6) filter:blur 复活（溢出真凶）
case("filter:blur 复活",
     APP,
     "  opacity: .38;\n  pointer-events: none;",
     "  opacity: .38;\n  filter: blur(1px);\n  pointer-events: none;",
     "filter: blur()")

# 7) 环境光整层被删（只剩底色）
case("环境光整层被删除",
     APP,
     "    radial-gradient(780px 440px at 12% 4%,  var(--ambient-1, transparent), transparent 62%),",
     "    linear-gradient(90deg, transparent, transparent),",
     "4 层")

# 7b) 只删掉一层（残缺光效，最容易被忽略的回归）
case("环境光少了一层",
     APP,
     "    radial-gradient(560px 360px at 26% 90%, var(--ambient-2, transparent), transparent 66%) !important;",
     "    linear-gradient(90deg, transparent, transparent) !important;",
     "4 层")

# 7c) 环境光引用与配色真源断开
case("环境光变量断链",
     APP,
     "radial-gradient(780px 440px at 12% 4%,  var(--ambient-1, transparent), transparent 62%),",
     "radial-gradient(780px 440px at 12% 4%,  rgba(56,189,248,.2), transparent 62%),",
     "--ambient-1")

# 8) V1 实色主题复活
case("V1 主题复活",
     PALETTE,
     "export const THEMES = {",
     "export const THEMES = {\n  blue: {\n    label: '蓝',\n    night: { '--bg-card': '#162032' },\n    day: { '--bg-card': '#ffffff' }\n  },",
     "已下线的 V1 实色主题")

# 9) 白天卡片被洗回中性白（车机「未激活卡片发白」真凶）
#    旧配方 rgba(240,246,255,.90) 通道极差只有 15，肉眼就是白的。
case("白天卡片洗成中性白",
     PALETTE,
     "'--bg-card': 'rgba(212,223,244,.92)'",
     "'--bg-card': 'rgba(240,246,255,.90)'",
     "通道极差")

# 10) 白天卡片比背景更亮（白卡压在带色背景上必然发白）
case("白天卡片比背景更亮",
     PALETTE,
     "'--bg-card': 'rgba(212,223,244,.92)'",
     "'--bg-card': 'rgba(250,252,255,.92)'",
     "高于页面底色")


def run_gate():
    r = subprocess.run([sys.executable, str(GATE)],
                       cwd=str(ROOT), capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


# ===== 激活态可见性变异 =====

# 11) 激活态边框退回半透明 bg 色（实车「点亮和不点亮一样」的真凶）
case("激活态边框退回半透明色",
     APP,
     "  /* 白天同理：边框必须是主题色实线才看得出来（原来 14% 不透明度等于没有） */\n  border-color: var(--accent-gold) !important;",
     "  /* 白天同理：边框必须是主题色实线才看得出来（原来 14% 不透明度等于没有） */\n  border-color: var(--accent-gold-bg) !important;",
     "肉眼无差")

# 12) 夜间激活态整段规则被删（点亮后完全没有边框）
case("夜间激活态规则被删",
     APP,
     "border-color: var(--accent-gold) !important;",
     "border-color: var(--border-color) !important;",
     "没有任何可见边框")

# 13) 环绕流光复活
case("环绕流光复活",
     APP,
     "/* 未激活态：中性边框",
     "@keyframes accent-run { 0% { background-position: 0 0; } }\n/* 未激活态：中性边框",
     "光污染")

# 14) 降亮动画复活
case("降亮动画残留",
     APP,
     "/* 未激活态：中性边框",
     "@keyframes accent-dim { 0% { opacity: 1 } }\n/* 未激活态：中性边框",
     "光污染")

# 15) 切 Tab 整页闪：keyframes 挂回卡片本体
case("卡片本体挂 animation（切Tab全页闪）",
     APP,
     ".bg-car-card,\n.bg-car-item {\n  transition:",
     ".bg-car-card.border-car-accent,\n.bg-car-item.border-car-accent {\n  animation: x .4s 1;\n}\n.bg-car-card,\n.bg-car-item {\n  transition:",
     "全页闪")

# 16) 卡片过渡误加 opacity（文字忽明忽暗）
case("卡片过渡加了 opacity",
     APP,
     "transition: box-shadow .30s ease-out, border-color .30s ease-out;",
     "transition: box-shadow .30s ease-out, border-color .30s ease-out, opacity .3s;",
     "文字跟着忽明忽暗")

# 17) 白天弹窗回到纯白（车主实拍里那块最大的白）
case("白天弹窗回纯白",
     PALETTE,
     "'--bg-modal': 'rgba(248,240,225,1)'",
     "'--bg-modal': 'rgba(255,255,255,1)'",
     "纯白 --bg-modal")

# 18) 弹窗改成半透（背后列表文字会串上来）
case("弹窗改成半透",
     PALETTE,
     "'--bg-modal': 'rgba(248,240,225,1)'",
     "'--bg-modal': 'rgba(248,240,225,0.72)'",
     "不透明度")

# 19) 组件里写死 bg-white（不跟主题的死白块）
case("组件写死 bg-white",
     ROOT / "web/src/components/modals/LogModal.vue",
     "bg-[var(--bg-modal)]",
     "bg-white",
     "写死 bg-white")

# 20) preflight 边框兜底被删（框套框双层描边复发）
case("preflight 边框兜底被删",
     APP,
     "  border-color: var(--border-color);\n}",
     "  border-color: #e5e7eb;\n}",
     "框套框")

def main():
    print("=" * 70)
    print(">> 玻璃材质门禁 · 变异测试（每个 bug 重新注入，必须被拦下）")
    print("=" * 70)

    rc, out = run_gate()
    if rc != 0:
        print("[FAIL] 基线未通过，无法进行变异测试：")
        print(out)
        return 1
    print("[PASS] 基线全绿，开始注入变异\n")

    results = []
    for name, path, old, new, keyword in CASES:
        backup = path.read_text(encoding="utf-8")
        if old not in backup:
            results.append((name, "SKIP", "锚点未找到: %r" % old[:40]))
            continue
        try:
            path.write_text(backup.replace(old, new, 1), encoding="utf-8")
            rc, out = run_gate()
            caught = rc != 0
            has_kw = keyword in out
            if caught and has_kw:
                results.append((name, "PASS", "已拦下，命中关键词「%s」" % keyword))
            elif caught:
                results.append((name, "WARN", "已拦下但未命中关键词「%s」" % keyword))
            else:
                results.append((name, "FAIL", "注入后门禁仍然通过 —— 防线是纸糊的！"))
        finally:
            path.write_text(backup, encoding="utf-8")

    print("-" * 70)
    bad = 0
    for name, st, detail in results:
        print("  [%s] %-22s %s" % (st, name, detail))
        if st in ("FAIL", "SKIP"):
            bad += 1
    print("-" * 70)

    # 收尾：确认已还原且全绿
    rc, out = run_gate()
    if rc != 0:
        print("[FAIL] 还原后基线不绿，文件可能被污染：")
        print(out)
        return 1
    print("[PASS] 全部文件已还原，基线重新全绿。")

    if bad:
        print("\n!! 变异测试有 %d 项未达标。" % bad)
        return 1
    print("\n[PASS] 变异测试 %d/%d 全部通过：每个已修复的坑都会被门禁拦住。"
          % (len(results), len(CASES)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
