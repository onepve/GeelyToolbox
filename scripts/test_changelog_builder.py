#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
发布更新说明构建器回归测试 (零外部依赖，直跑)。

覆盖核心铁律：
  A. 生成文案的标题版本号必须与传入 Tag 完全一致（杜绝版本与说明错位）；
  B. 手写覆盖文件优先于自动生成，且注释/空行被剔除、自动补序号；
  C. 无覆盖文件时用真实提交主题兜底，并剥掉 feat/fix 等英文前缀；
  D. 极端情况（无提交、无覆盖）也必须给出与本版本一致的说明，绝不冒用历史文案。
"""

import os
import shutil
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from changelog_builder import build_changelog, commit_subjects, previous_tag, read_override  # noqa: E402

passes = 0
failures = 0


def check(cond, label):
    global passes, failures
    if cond:
        passes += 1
    else:
        failures += 1
        print(f"  [FAIL] {label}")


def make_repo(tmp):
    subprocess.run(["git", "init", "-q", tmp], check=True)
    subprocess.run(["git", "-C", tmp, "config", "user.email", "t@example.com"], check=True)
    subprocess.run(["git", "-C", tmp, "config", "user.name", "Tester"], check=True)
    for msg in ["feat(voice): 新增安全守护语音", "fix(ui): 修复卡片等高对齐"]:
        with open(os.path.join(tmp, "f.txt"), "a", encoding="utf-8") as f:
            f.write(msg + "\n")
        subprocess.run(["git", "-C", tmp, "add", "-A"], check=True)
        subprocess.run(["git", "-C", tmp, "commit", "-q", "-m", msg], check=True)
    subprocess.run(["git", "-C", tmp, "tag", "beta-v1.7.33.1"], check=True)
    with open(os.path.join(tmp, "f.txt"), "a", encoding="utf-8") as f:
        f.write("feat(log): 日志收敛统一\n")
    subprocess.run(["git", "-C", tmp, "add", "-A"], check=True)
    subprocess.run(["git", "-C", tmp, "commit", "-q", "-m", "feat(log): 日志收敛统一"], check=True)
    subprocess.run(["git", "-C", tmp, "tag", "beta-v1.7.33.2"], check=True)


def main():
    print("== changelog_builder 回归 ==")
    tmp = tempfile.mkdtemp(prefix="chlog-")
    try:
        make_repo(tmp)

        # A. 标题版本号必须与 Tag 一致
        text = build_changelog("beta-v1.7.33.2", tmp, previous_tag("beta-v1.7.33.2", tmp))
        check("beta-v1.7.33.2" in text, "标题携带本次 Tag 版本号")
        check("beta-v1.7.33.1" not in text, "标题不残留旧版本号 (原缺陷根因)")
        check("【测试通道优先体验" in text, "测试通道标题格式正确")

        # C. 用真实提交兜底，且剥掉英文前缀
        check("日志收敛统一" in text, "包含区间内真实提交主题")
        check("feat(" not in text and "fix(" not in text, "英文 type 前缀已被剥离")
        check("1. " in text, "自动补序号")

        # 区间界定: 上一个 Tag 之前的提交不应出现
        check("安全守护语音" not in text, "上一 Tag 之前的旧提交未被带入")

        # B. 手写覆盖优先
        ov = os.path.join(tmp, "docs", "changelog")
        os.makedirs(ov, exist_ok=True)
        with open(os.path.join(ov, "beta-v1.7.33.2.txt"), "w", encoding="utf-8") as f:
            f.write("# 这是注释\n\n全新手写文案 A\n全新手写文案 B\n")
        text2 = build_changelog("beta-v1.7.33.2", tmp, previous_tag("beta-v1.7.33.2", tmp))
        check("全新手写文案 A" in text2 and "全新手写文案 B" in text2, "手写覆盖文件优先生效")
        check("# 这是注释" not in text2, "覆盖文件注释行被剔除")
        check("日志收敛统一" not in text2, "覆盖生效后不再混入自动内容")
        check("2. 全新手写文案 B" in text2, "覆盖条目已自动编号")

        # D. 覆盖文件里的版本号必须按当前 Tag 生成
        text3 = build_changelog("beta-v1.7.34.1", tmp, "")
        check("beta-v1.7.34.1" in text3, "任意 Tag 均生成与之匹配的标题")
        check("beta-v1.7.33.2" not in text3, "不回落到上一版本文案")

        # 空仓库极端情况
        empty = tempfile.mkdtemp(prefix="chlog-empty-")
        try:
            subprocess.run(["git", "init", "-q", empty], check=True)
            t4 = build_changelog("v1.7.35", empty, "")
            check("v1.7.35" in t4, "空仓库仍生成匹配版本的说明")
            check(len(t4.strip()) > 0, "空仓库不产生空文案")
        finally:
            shutil.rmtree(empty, ignore_errors=True)

        # 纯函数边界: 缺失覆盖文件
        check(read_override("beta-v9.9.9.9", tmp) == [], "缺失覆盖文件返回空列表")
        check(previous_tag("beta-v1.7.33.2", tmp) == "beta-v1.7.33.1", "上一个 Tag 界定正确")
        check(isinstance(commit_subjects("beta-v1.7.33.2", tmp, "beta-v1.7.33.1"), list), "提交主题返回列表")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

    print(f"==== RESULT: pass={passes} fail={failures} ====")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
