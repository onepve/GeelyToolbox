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


def run_gate():
    r = subprocess.run([sys.executable, str(GATE)],
                       cwd=str(ROOT), capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


# ===== 激活态跑马灯（accent 流光）变异 =====

# 11) 整段流光被删（退回静态常亮圈）
case("环绕流光被整段删除",
     APP,
     "@keyframes accent-run {",
     "@keyframes accent-run-disabled {",
     "accent-run")

# 12) 四条边拆成 4 个独立动画（会互相覆盖 background-position）
case("流光拆成四段覆盖",
     APP,
     "@keyframes accent-run {",
     "@keyframes accent-run {\n  0% { background-position: -60% 0; }\n}\n@keyframes accent-run-x {",
     "少于 2 个")

# 13) 降亮动画被删（光圈常亮刺眼）
case("点亮后不降亮",
     APP,
     "@keyframes accent-dim {",
     "@keyframes accent-dim-removed {",
     "accent-dim")

# 14) 降亮幅度不够：末期仍几乎全亮（改中段没用，末档才是用户长期看到的状态）
case("降亮幅度不足",
     APP,
     "  100%     { opacity: .50; }",
     "  100%     { opacity: .95; }",
     "降得不够")

# 15) 跑马灯挂到卡片本体（文字会跟着闪）
case("流光挂到卡片本体",
     APP,
     ".bg-car-card.border-car-accent,\n.bg-car-item.border-car-accent {\n  animation: accent-ring-in .45s cubic-bezier(.2, .7, .3, 1) 1;\n}",
     ".bg-car-card.border-car-accent,\n.bg-car-item.border-car-accent {\n  animation: accent-ring-in .45s cubic-bezier(.2, .7, .3, 1) 1, accent-run 3.6s linear infinite;\n}",
     "卡片本体")

# 16) 内层槽位也跑跑马灯（圈套圈）
case("内层槽位也跑流光",
     APP,
     ".bg-car-card.border-car-accent::after {",
     ".bg-car-card.border-car-accent::after,\n.bg-car-item.border-car-accent::after {",
     "圈套圈")

# 17) @property 复活（Chrome 85+）
case("@property 复活",
     APP,
     "@keyframes accent-flash {",
     "@property --x { syntax: '<number>'; inherits: false; initial-value: 0; }\n@keyframes accent-flash {",
     "@property")


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
