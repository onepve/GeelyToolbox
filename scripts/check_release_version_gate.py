#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
发版版本单调性门禁 (Release Version Monotonicity Gate · 防降级双通道)
=====================================================================

背景（2026-11-03 真实事故）：
    已发布正式版 1.8.20 (versionCode 1082099) 后，误打测试版 Tag `beta-v1.8.20.1`，
    按公式算出 versionCode 仅 1082001 < 1082099。车机端判定逻辑为
    `remoteCode > currentCode` 才提示更新，于是测试通道点「检查更新」永远提示
    「已是最新」；即便强装也会被 Android 拒绝 (INSTALL_FAILED_VERSION_DOWNGRADE)。
    根因：原 Gate 17 只校验「build.gradle ↔ Tag 公式一致」，却没校验
    「Tag 算出的号 ↔ 线上已发版本」的单调性，降级包一路绿灯发出。

本门禁补齐这条防线，正式版与测试版分开校验：

    通用硬红线（两通道都查）：
        候选 versionCode 严禁 < 线上正式版 versionCode（车机大盘实际运行的基线）。
        —— 直接抓住本次事故 (1082001 < 1082099 → FAIL)。

    测试版 (beta-v*) 额外：
        候选 < 线上测试版 → 硬失败（发了比在架测试版还老的包，测试通道用户收不到更新）；
        候选 == 线上测试版 → 仅告警放行（同号 re-tag 重发属合法运维场景）。

    正式版 (v*) 额外：
        候选 < 线上正式版 → 硬失败（正式版降级）；
        候选 == 线上正式版 → 仅告警放行（同号 re-tag 重发）；
        候选 <= 线上测试版 → 硬失败（正式版必须超越在架测试版，否则测试版反而更新）。

设计原则：
    * 纯函数 evaluate() 与网络/IO 解耦，--self-test 可离线证明逻辑正确（含本次事故场景）。
    * 线上元数据拉取 fail-closed：网络异常且无显式豁免时拒绝发版，绝不让门禁被 CDN 抖动绕过。
    * 404（线上尚无该通道元数据，如首次发版）视为「无基线」合法放行，不算网络错误。

用法：
    python3 scripts/check_release_version_gate.py --tag beta-v1.8.21.1
    python3 scripts/check_release_version_gate.py --tag v1.8.21
    python3 scripts/check_release_version_gate.py --self-test

退出码：0 = 放行；1 = 拦截发版；2 = 无法核验（fail-closed）。
"""

import argparse
import json
import os
import socket
import sys
import time
import urllib.error
import urllib.request

R2_BASE = "https://dl.onepve.com/GeelyToolbox/"
OFFICIAL_META = "version.json"
BETA_META = "version-beta.json"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

# 显式豁免开关：仅在 CDN 确证故障、需紧急发版时人工置 1（默认 fail-closed）
NET_FALLBACK_ENV = "ALLOW_RELEASE_GATE_NET_FALLBACK"


# ----------------------------------------------------------------------
# IPv4-only 猴子补丁：宿主机若有本地 IPv6 但无公网 IPv6 路由，urllib 请求带
# AAAA 记录的域名（dl.onepve.com）会卡死在 IPv6 握手超时 60s（curl 不受影响）。
# ----------------------------------------------------------------------
_orig_getaddrinfo = socket.getaddrinfo


def _ipv4_only_getaddrinfo(*args, **kwargs):
    return [r for r in _orig_getaddrinfo(*args, **kwargs) if r[0] == socket.AF_INET]


socket.getaddrinfo = _ipv4_only_getaddrinfo


# ----------------------------------------------------------------------
# Tag 解析与 versionCode 推导（与 .github/workflows/ci.yml 注入公式 100% 同源）
# 公式：(MAJ*10000 + MIN*100 + PATCH) * 100 + REV ；正式版 REV=99，测试版 REV=1~98
# ----------------------------------------------------------------------
def derive(tag):
    """从 Tag 名推导 (versionName, versionCode, is_beta)。非法 Tag 抛 ValueError。"""
    tag = tag.strip()
    if not tag:
        raise ValueError("Tag 名为空")

    is_beta = tag.startswith("beta-")
    if is_beta:
        ver = tag[len("beta-v"):]
        parts = ver.split(".")
        if len(parts) != 4:
            raise ValueError(
                f"测试版 Tag 必须为四段号 beta-vX.Y.Z.N（如 beta-v1.8.21.1），当前: {tag}"
            )
        try:
            maj, mino, patch, rev = (int(p) for p in parts)
        except ValueError:
            raise ValueError(f"测试版 Tag 号段含非数字: {tag}")
        if not (1 <= rev <= 98):
            raise ValueError(f"测试序号必须为 1~98，当前: {rev}（Tag {tag}）")
        vname = f"{maj}.{mino}.{patch}-beta.{rev}"
    else:
        if not tag.startswith("v"):
            raise ValueError(f"正式版 Tag 必须以 v 开头（如 v1.8.21），当前: {tag}")
        ver = tag[1:]
        parts = ver.split(".")
        if len(parts) != 3:
            raise ValueError(f"正式版 Tag 必须为三段号 vX.Y.Z（如 v1.8.21），当前: {tag}")
        try:
            maj, mino, patch = (int(p) for p in parts)
        except ValueError:
            raise ValueError(f"正式版 Tag 号段含非数字: {tag}")
        rev = 99
        vname = f"{maj}.{mino}.{patch}"

    vcode = (maj * 10000 + mino * 100 + patch) * 100 + rev
    return vname, vcode, is_beta


# ----------------------------------------------------------------------
# 纯函数门禁逻辑（可离线单测，与网络解耦）
# ----------------------------------------------------------------------
def evaluate(cand_code, cand_is_beta, official_code, beta_code):
    """
    返回 (ok: bool, hard: list[str], warn: list[str])。
      official_code / beta_code 为线上已发 versionCode，None 表示该通道线上尚无基线。
      ok=False 时必须拦截发版。
    """
    hard, warn = [], []

    # —— 通用硬红线：候选严禁低于线上正式版基线（车机大盘实际运行版本）——
    if official_code is not None:
        if cand_code < official_code:
            hard.append(
                f"降级拦截：候选 versionCode {cand_code} < 线上正式版 {official_code}"
                f"（车机将判定『已是最新』收不到更新，且 Android 拒绝降级覆盖安装）"
            )
        elif cand_code == official_code:
            warn.append(
                f"候选 versionCode {cand_code} == 线上正式版 {official_code}"
                f"（同号重发，仅 re-tag 场景合法；如为迭代发版请递进号段）"
            )

    if cand_is_beta:
        # —— 测试版额外：严禁低于在架测试版（测试通道用户会收不到更新）——
        if beta_code is not None:
            if cand_code < beta_code:
                hard.append(
                    f"测试版回退拦截：候选 {cand_code} < 线上测试版 {beta_code}"
                    f"（发了比在架测试版还老的包）"
                )
            elif cand_code == beta_code:
                warn.append(
                    f"候选 {cand_code} == 线上测试版 {beta_code}"
                    f"（同号 re-tag 重发，放行但请确认是否应递增 REV）"
                )
    else:
        # —— 正式版额外：必须严格超越在架测试版，否则测试版反而比正式版新 ——
        if beta_code is not None and cand_code <= beta_code:
            hard.append(
                f"正式版未超越在架测试版：候选 {cand_code} <= 线上测试版 {beta_code}"
                f"（正式版必须是全局最高号）"
            )

    return (len(hard) == 0), hard, warn


# ----------------------------------------------------------------------
# 线上元数据拉取（fail-closed）
# ----------------------------------------------------------------------
def fetch_online_code(meta_name, retries=3, timeout=15):
    """
    拉取线上 version(_-beta).json 的 version_code。
      返回 int（有基线）或 None（404 首次发版，合法无基线）。
      网络/解析异常 → 抛 RuntimeError（由 main 决定 fail-closed）。
    """
    url = f"{R2_BASE}{meta_name}?t={int(time.time())}"
    last_err = None
    for attempt in range(1, retries + 1):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                raw = resp.read().decode("utf-8")
            data = json.loads(raw)
            code = data.get("version_code")
            if code is None:
                raise RuntimeError(f"{meta_name} 缺少 version_code 字段")
            return int(code)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                # 线上尚无该通道元数据（如首次发版）→ 无基线，合法放行
                return None
            last_err = e
        except Exception as e:  # noqa: BLE001 - 网络/JSON 异常统一重试
            last_err = e
        if attempt < retries:
            time.sleep(2 * attempt)  # 退避 2s / 4s
    raise RuntimeError(f"拉取 {meta_name} 失败（已重试 {retries} 次）：{last_err}")


def main():
    ap = argparse.ArgumentParser(description="发版版本单调性门禁（防降级双通道）")
    ap.add_argument("--tag", help="候选发版 Tag（如 beta-v1.8.21.1 或 v1.8.21）")
    ap.add_argument("--self-test", action="store_true", help="离线自测门禁逻辑（含本次事故场景）")
    args = ap.parse_args()

    if args.self_test:
        return run_self_test()

    if not args.tag:
        # 兼容 CI：从 GITHUB_REF 取 Tag
        ref = os.environ.get("GITHUB_REF", "")
        if ref.startswith("refs/tags/"):
            args.tag = ref[len("refs/tags/"):]
    if not args.tag:
        print("!! [FAIL] 未提供 --tag，且 GITHUB_REF 非 Tag 引用，无法核验。")
        return 2

    # 1. 推导候选版本
    try:
        vname, vcode, is_beta = derive(args.tag)
    except ValueError as e:
        print(f"!! [FAIL] Tag 解析失败：{e}")
        return 1

    channel = "测试通道 (Beta)" if is_beta else "正式通道 (Release)"
    print("=" * 60)
    print(">> 发版版本单调性门禁 (Anti-Downgrade · 双通道)")
    print("=" * 60)
    print(f"  候选 Tag        : {args.tag}")
    print(f"  候选通道        : {channel}")
    print(f"  候选 versionName: {vname}")
    print(f"  候选 versionCode: {vcode}")

    # 2. 拉取线上基线（fail-closed）
    try:
        official_code = fetch_online_code(OFFICIAL_META)
        beta_code = fetch_online_code(BETA_META)
    except RuntimeError as e:
        allow = os.environ.get(NET_FALLBACK_ENV, "").strip() in ("1", "true", "yes")
        print(f"!! [ERROR] 无法核验线上版本：{e}")
        if allow:
            print(f">> [WARN] 已设置 {NET_FALLBACK_ENV}=1，紧急豁免放行（请人工确认号段递进）。")
            return 0
        print(
            ">> fail-closed：线上基线不可读时拒绝发版，绝不让门禁被 CDN 抖动绕过。\n"
            f"   如确证 CDN 故障需紧急发版，可置环境变量 {NET_FALLBACK_ENV}=1 人工豁免。"
        )
        return 2

    print(f"  线上正式版 code : {official_code if official_code is not None else '（无基线）'}")
    print(f"  线上测试版 code : {beta_code if beta_code is not None else '（无基线）'}")
    print("-" * 60)

    # 3. 评估
    ok, hard, warn = evaluate(vcode, is_beta, official_code, beta_code)
    for w in warn:
        print(f"  [WARN] {w}")
    if ok:
        print("  [PASS] 版本单调性校验通过：候选号严格高于线上基线，车机可正常检测更新。")
        print("=" * 60)
        return 0
    for h in hard:
        print(f"  [FAIL] {h}")
    print("-" * 60)
    print(">> 发版已阻断。修复指引：")
    if is_beta:
        print("   测试版必须递进到『下一主干版本』号段（如正式 1.8.20 ➔ beta-v1.8.21.1），")
        print("   使 versionCode 严格大于线上正式版；严禁沿用当前正式版号打测试 Tag。")
    else:
        print("   正式版 versionCode 必须严格大于线上正式版与在架测试版；请递进号段后重打 Tag。")
    print("   取号请用：python3 ~/.hermes/skills/onepve/geely-release-ops/scripts/tag_release.py --channel beta --dry-run")
    print("=" * 60)
    return 1


# ----------------------------------------------------------------------
# 离线自测：用纯函数 evaluate() 复现并证明能抓住本次事故
# ----------------------------------------------------------------------
def run_self_test():
    print("=" * 60)
    print(">> 门禁逻辑离线自测 (--self-test)")
    print("=" * 60)

    # 先验证 derive() 公式与 CI 注入完全同源
    derive_cases = [
        ("beta-v1.8.20.1", "1.8.20-beta.1", 1082001, True),   # 本次事故错号
        ("beta-v1.8.21.1", "1.8.21-beta.1", 1082101, True),   # 本次事故修复号
        ("v1.8.20", "1.8.20", 1082099, False),
        ("v1.8.21", "1.8.21", 1082199, False),
    ]
    for tag, exp_name, exp_code, exp_beta in derive_cases:
        n, c, b = derive(tag)
        assert (n, c, b) == (exp_name, exp_code, exp_beta), f"derive({tag}) 公式漂移: {(n, c, b)}"
    print(f"  [PASS] derive() 公式与 CI 注入同源（{len(derive_cases)} 例）")

    # evaluate() 场景：(候选code, 是否beta, 线上正式, 线上测试, 期望ok, 备注)
    cases = [
        # ★ 本次真实事故：beta-v1.8.20.1(1082001) vs 线上正式 1082099 → 必须拦截
        (1082001, True, 1082099, None, False, "事故复现：测试版低于正式版基线"),
        # ★ 修复号：beta-v1.8.21.1(1082101) vs 线上正式 1082099 → 放行
        (1082101, True, 1082099, None, True, "修复号：测试版高于正式版基线"),
        # 测试版迭代：1082102 > 在架测试 1082101 且 > 正式 1082099 → 放行
        (1082102, True, 1082099, 1082101, True, "测试版正常递增"),
        # 测试版回退：1082101 < 在架测试 1082102 → 拦截
        (1082101, True, 1082099, 1082102, False, "测试版回退到更老号"),
        # 测试版同号 re-tag：1082101 == 在架测试 1082101 → 告警放行
        (1082101, True, 1082099, 1082101, True, "测试版同号 re-tag（告警放行）"),
        # 正式版降级：1082099 < 线上正式 1082199 → 拦截
        (1082099, False, 1082199, None, False, "正式版降级"),
        # 正式版同号 re-tag：1082099 == 线上正式 1082099 → 告警放行
        (1082099, False, 1082099, None, True, "正式版同号 re-tag（告警放行）"),
        # 正式版正常递增且超越在架测试：1082199 > 正式1082099 且 > 测试1082101 → 放行
        (1082199, False, 1082099, 1082101, True, "正式版正常递增超越测试版"),
        # 正式版未超越在架测试版：1082101 <= 测试1082101 → 拦截
        (1082101, False, 1082099, 1082101, False, "正式版未超越在架测试版"),
        # 首次发版无任何线上基线 → 放行
        (1082099, False, None, None, True, "首次发版无基线"),
    ]
    for code, is_beta, off, bet, exp_ok, note in cases:
        ok, hard, warn = evaluate(code, is_beta, off, bet)
        assert ok == exp_ok, f"场景『{note}』判定错误: 期望 ok={exp_ok}, 实际 ok={ok}, hard={hard}"
        flag = "放行" if ok else "拦截"
        print(f"  [PASS] {flag} · {note} (cand={code}, off={off}, beta={bet})")

    print("-" * 60)
    print(f"  [SUCCESS] 全部 {len(derive_cases) + len(cases)} 项自测通过，门禁逻辑正确（含事故复现）。")
    print("=" * 60)
    return 0


if __name__ == "__main__":
    sys.exit(main())
