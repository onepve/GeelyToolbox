#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
激活态契约门禁 —— 「选中/点亮」不许整圈描边，只有「可点动作」才允许。

## 三轨制（唯一规范，同步写在 App.vue 顶部注释里）

| 轨 | 元素 | 选中态写法 |
|---|---|---|
| 1 可选卡片 | 功能开关卡、皮肤卡、四宫格磁贴、引脚分段 | `bg-car-item border-car-border border-solid border-l-[5px] border-l-car-accent shadow-md`（左侧竖条） |
| 2 分段/页签/小选项 | 播放模式双按钮、油品、三档策略、输出通道 | `bg-car-accent-bg text-car-accent border-2 border-transparent`（淡底填充，**不画边框**） |
| 3 按钮/动作/状态开关 | 开启↔关闭、试听、确认绑定、导出、屏保测试 | 整圈 `border-2 border-car-accent`（可点语言，允许） |

## 为什么轨 2 也不许整圈

用户原话：「tab 也可以不用整圈吧」。一排小方块各自套一圈亮边，
满屏都是圈，就是所谓「满地圈圈」。淡底填充 + 主题色文字同样能一眼看出选中，
而且没有描边叠加，车机 142dpi 下更干净。

## 判据（机器可判，不靠肉眼）

扫所有 `:class` 三元表达式里的**选中分支**：

* 含 `border-l-car-accent` → 轨 1 左侧条，合规；
* 含裸的 `border-car-accent`（不是 hover:/focus: 等变体）→ 整圈染色，
  **必须**在同一行前后 13 行内出现轨 3 豁免标记 `active-state:button`，否则 FAIL；
* 选中分支没有底色抬升、纯边框差异的（ghost 按钮）→ 不算选中态，跳过。

为什么这样够用：
* 轨 1 卡片改成 `border-l-car-accent` 后不含裸 `border-car-accent`，天然不命中；
* 轨 2 分段控件改成 `border-2 border-transparent`，不命中；
* 轨 3 按钮显式带豁免标记，语义清楚；
* 「Tab 忘了改」和「新写的卡片又整圈」都会被拦。

退出码：0 全绿；1 有违规。
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB = os.path.join(ROOT, "web", "src")

LEFT_BAR = "border-l-car-accent"
EXEMPT = "active-state:button"
SKIP_DIRS = {"node_modules", "dist"}

# 变体前缀（hover:/focus:/group-hover: …）= 点击反馈，先剔除再判
VARIANT_ACCENT = re.compile(r"(?:[a-z-]+:)+border-car-accent")

# 严格配对：'选中串' 紧跟 : '未选中串'，中间只允许冒号与空白
PAIR = re.compile(
    r"""(['"])([^'"\n]{0,300}?)\1\s*:\s*(['"])([^'"\n]{0,300}?)\3"""
)


def iter_vue_files():
    for dirpath, dirnames, filenames in os.walk(WEB):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if fn.endswith(".vue"):
                yield os.path.join(dirpath, fn)


def scan(src):
    """返回 [(行号, 说明)]，只列整圈染色的选中态或对象式选中态。"""
    hits = []
    lines = src.split("\n")

    def line_of(pos):
        return src[:pos].count("\n") + 1

    def exempt(pos, ln):
        ctx = "\n".join(lines[max(0, ln - 13):ln + 3])
        return EXEMPT in ctx

    # ---------- 形态一：三元表达式 '选中' : '未选中' ----------
    for m in PAIR.finditer(src):
        active, neg = m.group(2), m.group(4)
        if LEFT_BAR in active:
            continue
        if "border-car-accent" not in VARIANT_ACCENT.sub("", active):
            continue
        # 必须真的是「选中态」：选中分支得有底色在抬升。
        # 纯边框差异（透明 ↔ 边框）是 ghost 按钮，不是选中态。
        if not re.search(r"bg-(car-[a-z-]+|transparent|white)", active):
            continue
        ln = line_of(m.start())
        if exempt(m.start(), ln):
            continue
        hits.append((ln, f"三元选中态整圈描边: {active.strip()[:100]}"))

    # ---------- 形态二：对象式 :class="{ 'border-car-accent': cond }" ----------
    # 这是三元之外最容易漏的写法（BodyView / AppSelectModal 就漏过）。
    for m in re.finditer(r"""(['"])\{([^{}]{0,400}?)\}\1""", src):
        body = m.group(2)
        if LEFT_BAR in body:
            continue
        if "border-car-accent" not in VARIANT_ACCENT.sub("", body):
            continue
        if not re.search(r"bg-(car-[a-z-]+|transparent|white)", body):
            continue
        ln = line_of(m.start())
        if exempt(m.start(), ln):
            continue
        hits.append((ln, f"对象式选中态整圈描边: {{{body.strip()[:90]}}}"))

    return hits


def main():
    print("=" * 74)
    print("激活态契约门禁：选中态不许整圈描边（卡片走左侧条 / 分段走淡底）")
    print("=" * 74)

    total = 0
    bad = 0
    for path in sorted(iter_vue_files()):
        rel = os.path.relpath(path, ROOT)
        src = open(path, encoding="utf-8").read()
        total += 1
        for line_no, desc in scan(src):
            print(f"  [FAIL] {rel}:{line_no}")
            print(f"         {desc}")
            print("         卡片请改: bg-car-item border-car-border border-solid "
                  "border-l-[5px] border-l-car-accent shadow-md")
            print("         分段请改: bg-car-accent-bg text-car-accent "
                  "border-2 border-transparent")
            print("         确属可点按钮/状态开关，请在附近写 "
                  "active-state:button 并说明理由。")
            bad += 1

    print("-" * 74)
    if bad:
        print(f"[FAIL] 共 {bad} 处选中态用了整圈描边（扫描 {total} 个 .vue）")
        return 1

    print(f"[PASS] 选中态全部合规：卡片左侧条 / 分段淡底 / 按钮已显式声明"
          f"（扫描 {total} 个 .vue）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
