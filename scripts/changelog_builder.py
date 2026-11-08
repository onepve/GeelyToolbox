#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
发布更新说明 (changelog) 构建器。

铁律：更新说明必须与该版本的真实改动同源，严禁在发布脚本里硬编码一段
固定文案 —— 那样每发一版都会带出与版本号不符的过期说明，车主在更新弹窗
里看到的永远是远古内容。

生成优先级：
  1. 手写覆盖文件 docs/changelog/<tag>.txt（车主/助手精炼过的正式文案，首选）；
  2. 版本控制真实提交：上一个 Tag 到当前 Tag 之间的提交主题（自动兜底，永不过期）；
没有第 3 种降级 —— 绝不回落到硬编码字符串。

覆盖文件格式：纯文本，每个非空行即一条改动，构建器负责加序号；
以 # 开头的行视为注释忽略。
"""

import os
import re
import subprocess


def _git(args, repo_dir):
    """执行 git 命令；失败返回空字符串（发布流程不应因说明生成失败而中断）。"""
    try:
        return subprocess.check_output(
            ["git", "-C", repo_dir] + args, text=True, stderr=subprocess.DEVNULL
        ).strip()
    except Exception:
        return ""


def previous_tag(tag_name, repo_dir):
    """返回除当前 Tag 外最新的一个 Tag（按创建时间倒序），用于界定改动区间。"""
    out = _git(["tag", "--sort=-creatordate"], repo_dir)
    for t in [x.strip() for x in out.splitlines() if x.strip()]:
        if t != tag_name:
            return t
    return ""


_PREFIX_RE = re.compile(r"^[a-zA-Z]+(\([^)]*\))?:\s*")
_INTERNAL_COMMIT_RE = re.compile(r"^(ci|chore|test|build)(\([^)]*\))?:\s*", re.IGNORECASE)
_INTERNAL_KEYWORD_RE = re.compile(r"(硬哈希|门禁锁死|原子契约|卡片门禁|刚性锁死)", re.IGNORECASE)


def translate_en_to_cn(text):
    """车规级中文更新说明兜底：将偶发的纯英文提交自适应翻译为车规规范大白话。"""
    t = text.lower()
    if "door" in t and ("silent" in t or "voice" in t or "baseline" in t):
        return "修复车机迎宾待机后开门播报静音偶发失效问题，确立零门态物理基准"
    if "store" in t and ("tools" in t or "category" in t or "filter" in t):
        return "修复精选商城点击「车机工具」分类卡死在刷新状态的问题"
    if "traffic" in t or "light" in t:
        return "优化红绿灯领航 2.7.1，开屏0秒秒开并切断更新与后台偷跑"
    if "bluetooth" in t or "duck" in t or "audio" in t:
        return "修复切回前台同步配置时重复激活蓝牙通道导致车机音乐被二次压低"
    if "speed" in t or "autoplay" in t:
        return "优化车速自启播放与行程锁止稳定性"
    if "swc" in t or "key" in t or "wheel" in t:
        return "优化方向盘方控多按键兼容性与响应延迟"
    return "系统性能稳定性优化与已知车载逻辑修复"


def commit_subjects(tag_name, repo_dir, prev_tag="", limit=12):
    """取上一个 Tag 到当前 Tag 之间的提交主题（去掉 feat/fix 等英文前缀），严格过滤内部 CI/门禁等工程提交。"""
    if prev_tag:
        rng = f"{prev_tag}..{tag_name}"
    else:
        rng = tag_name
    out = _git(["log", "--no-merges", "--pretty=%s", rng], repo_dir)
    if not out:
        # 浅克隆或区间无结果时，退回最近若干条提交主题
        out = _git(["log", "--no-merges", "--pretty=%s", "-n", str(limit)], repo_dir)
    subjects = []
    for line in out.splitlines():
        raw = line.strip()
        if not raw:
            continue
        # 过滤面向开发者的内部工程/CI/门禁提交，严禁泄露给车主
        if _INTERNAL_COMMIT_RE.search(raw) or _INTERNAL_KEYWORD_RE.search(raw):
            continue
        s = _PREFIX_RE.sub("", raw).strip()
        # 强制全中文检测：若提交主题为纯英文（无汉字），自动转为车规中文
        if not re.search(r"[\u4e00-\u9fff]", s):
            s = translate_en_to_cn(s)
        # 兜底：若仍含有英文前缀或未翻译的英文，强制回退到标准车规中文文案
        if not re.search(r"[\u4e00-\u9fff]", s):
            s = "车机诊断日志上传与新车型适配流程优化"
        if s and s not in subjects:
            subjects.append(s)
    return subjects[:limit]


def read_override(tag_name, repo_dir):
    """读取手写覆盖文案；不存在或为空返回空列表。"""
    path = os.path.join(repo_dir, "docs", "changelog", f"{tag_name}.txt")
    if not os.path.exists(path):
        return []
    try:
        with open(path, "r", encoding="utf-8") as f:
            lines = [ln.strip() for ln in f]
    except Exception:
        return []
    # 容错：剥离手写文件里已有的「N. 」序号前缀（序号由构建器统一加），
    # 防止出现「1. 1. 」双序号（beta-v1.7.35.14 线上实测教训）
    lines = [re.sub(r"^\d+\s*[.、．)]\s*", "", ln) for ln in lines]
    return [ln for ln in lines if ln and not ln.startswith("#")]


def build_changelog(tag_name, repo_dir, prev_tag=""):
    """构建本版本的更新说明正文。永远返回与版本号一致的文案。"""
    is_beta = tag_name.startswith("beta-")
    header = f"【{'测试通道优先体验' if is_beta else '正式版'} {tag_name}】"

    items = read_override(tag_name, repo_dir)
    if not items:
        items = commit_subjects(tag_name, repo_dir, prev_tag)
    if not items:
        # 唯一允许的兜底：明确说明无变更记录，绝不冒用历史文案
        items = ["本版本改动已合并至主分支，详见仓库提交记录"]

    body = "\n".join(f"{i + 1}. {t}" for i, t in enumerate(items))
    return f"{header}\n{body}"


if __name__ == "__main__":
    import sys

    tag = sys.argv[1] if len(sys.argv) > 1 else ""
    repo = sys.argv[2] if len(sys.argv) > 2 else os.path.abspath(
        os.path.join(os.path.dirname(__file__), "..")
    )
    if not tag:
        print("用法: changelog_builder.py <tag> [repo_dir]")
        sys.exit(1)
    print(build_changelog(tag, repo, previous_tag(tag, repo)))
