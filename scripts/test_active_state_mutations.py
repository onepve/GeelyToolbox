#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
激活态门禁变异测试 —— 证明「门禁能拦住回退」，而不是永远 PASS 的摆设。

做法：把 scripts/ 与 web/src/ 复制到临时目录，逐条注入「把合规写法改回整圈」的变异，
      跑门禁，断言它必须 FAIL。任何一条变异仍 PASS = 门禁有漏洞。

跑法：python3 scripts/test_active_state_mutations.py
"""
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GATE_REL = os.path.join("scripts", "check_active_state_contract.py")

SEG_ON = "bg-car-accent-bg text-car-accent border-2 border-transparent"

# (说明, 相对文件, 原串, 变异后串)
MUTATIONS = [
    (
        "皮肤选择卡改回整圈（卡片轨）",
        "web/src/components/ThemeV2Panel.vue",
        "bg-car-item border-car-border border-solid border-l-[5px] border-l-car-accent shadow-md",
        "bg-car-item border-car-accent shadow-lg",
    ),
    (
        "皮肤标签改回整圈（分段轨）",
        "web/src/components/ThemeV2Panel.vue",
        "bg-car-accent-bg border-2 border-transparent text-car-accent",
        "bg-car-accent-bg border-car-accent text-car-accent",
    ),
    (
        "下拉选项卡改回整圈",
        "web/src/components/ActionSelect.vue",
        "bg-car-item border-car-border border-solid border-l-[5px] border-l-car-accent text-car-text shadow-md",
        "bg-car-item border-2 border-car-accent text-car-text shadow-sm",
    ),
    (
        "矩阵磁贴改回整圈",
        "web/src/components/MatrixButton.vue",
        "bg-car-item border-car-border border-solid border-l-[5px] border-l-car-accent text-car-text font-black shadow-md",
        "bg-car-item border-car-accent text-car-text font-black shadow-md",
    ),
    (
        "播放模式双按钮改回整圈（Tab 轨）",
        "web/src/views/LinkView.vue",
        SEG_ON + " shadow-sm",
        "bg-car-card border-car-accent text-car-accent shadow-md",
    ),
    (
        "油品选择改回整圈",
        "web/src/views/FuelView.vue",
        "bg-car-accent-light border-2 border-transparent",
        "bg-car-accent-light border-car-accent",
    ),
    (
        "门开关状态按钮改回整圈",
        "web/src/components/DoorConfigModal.vue",
        SEG_ON + " shadow-sm",
        "bg-car-card border-car-accent text-car-accent shadow-md",
    ),
    (
        "油品四列卡改回整圈",
        "web/src/components/modals/OilPriceModal.vue",
        "bg-car-item border-car-border border-solid border-l-[5px] border-l-car-accent shadow-md",
        "bg-car-card border-car-accent shadow-md",
    ),
    (
        "整卡开关改回整圈（FloatingView）",
        "web/src/views/FloatingView.vue",
        "bg-car-item border-car-border border-solid border-l-[5px] border-l-car-accent shadow-car-accent/10",
        "bg-car-card border-car-accent shadow-car-accent/10",
    ),
    (
        "对象式选中态改回整圈（AppSelectModal）",
        "web/src/components/modals/AppSelectModal.vue",
        "'border-l-[5px] border-l-car-accent': isCurrentSelected(app.pkg)",
        "'border-car-accent bg-car-card': isCurrentSelected(app.pkg)",
    ),
    (
        "对象式选中态改回整圈（BodyView 语音主题包）",
        "web/src/views/BodyView.vue",
        "'bg-car-item border-car-border border-l-[5px] border-l-car-accent'",
        "'bg-car-item border-car-accent'",
    ),
]


def run_gate(cwd):
    p = subprocess.run(
        [sys.executable, os.path.join(cwd, GATE_REL)],
        cwd=cwd, capture_output=True, text=True,
    )
    return p.returncode, p.stdout


def main():
    print("=" * 74)
    print("激活态门禁变异测试：每条变异都必须被拦住")
    print("=" * 74)

    ok = 0
    fail = []

    for desc, rel, old, new in MUTATIONS:
        tmp = tempfile.mkdtemp(prefix="actmut_")
        try:
            dst = os.path.join(tmp, "repo")
            os.makedirs(dst)
            shutil.copytree(os.path.join(ROOT, "scripts"), os.path.join(dst, "scripts"))
            shutil.copytree(os.path.join(ROOT, "web", "src"),
                            os.path.join(dst, "web", "src"))

            target = os.path.join(dst, rel)
            src = open(target, encoding="utf-8").read()
            if old not in src:
                print(f"  [STALE] {desc} —— 变异源串已不存在，用例过期需更新")
                fail.append((desc, "源串过期"))
                continue
            src = src.replace(old, new)
            # 同时抹掉豁免标记，否则会被合法豁免掉
            src = src.replace("active-state:button", "active-state:removed")
            open(target, "w", encoding="utf-8").write(src)

            code, out = run_gate(dst)
            if code == 1 and "FAIL" in out:
                print(f"  [OK]    {desc} —— 被拦住")
                ok += 1
            else:
                print(f"  [HOLE]  {desc} —— 门禁没拦住！exit={code}")
                fail.append((desc, f"exit={code}"))
        finally:
            shutil.rmtree(tmp, ignore_errors=True)

    print("-" * 74)
    print(f"结果：{ok}/{len(MUTATIONS)} 条变异被拦住")
    if fail:
        for d, r in fail:
            print(f"  [FAIL] {d}: {r}")
        return 1
    print("[PASS] 门禁有能力拦住所有回退")
    return 0


if __name__ == "__main__":
    sys.exit(main())
