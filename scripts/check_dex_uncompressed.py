#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
门禁：APK 内 classes.dex 必须 STORED（不压缩）。

为什么必须拦：
  dex 被 deflate 压缩时，省下的只有 ~0.5MB 包体，
  代价是每次冷启动都要把 860KB dex 解压写盘（车机 Android 9 弱 CPU 明显拖慢首屏）。
  ART 对 STORED dex 可直接 mmap，零解压零写盘。

双重检查：
  1. app/build.gradle 里 packaging.dex.useLegacyPackaging 不得为 true
  2. 若线上/本地有 APK 样本，直接读 zip 头确认 classes.dex 的 compress_type 为 0
"""
import glob
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GRADLE = os.path.join(ROOT, "app", "build.gradle")

FAILS = []


def check_gradle():
    if not os.path.exists(GRADLE):
        FAILS.append(f"找不到 {GRADLE}")
        return
    src = open(GRADLE, encoding="utf-8").read()

    # 剥离注释后再匹配，避免注释里出现 true 造成误判
    code = re.sub(r"//[^\n]*", "", src)
    code = re.sub(r"/\*.*?\*/", "", code, flags=re.S)

    m = re.search(r"packaging\s*\{.*?\bdex\s*\{(.*?)\}", code, re.S)
    if not m:
        FAILS.append(
            "app/build.gradle 里找不到 packaging { dex { ... } } 配置块：\n"
            "  必须显式写 useLegacyPackaging = false，否则 AGP 默认行为会压缩 dex"
        )
        return

    body = m.group(1)
    m2 = re.search(r"useLegacyPackaging\s*=\s*(true|false)", body)
    if not m2:
        FAILS.append(
            "packaging.dex 里没有显式 useLegacyPackaging = false：\n"
            "  依赖默认值不可靠，必须写死 false"
        )
        return

    if m2.group(1) == "true":
        FAILS.append(
            "packaging.dex.useLegacyPackaging = true 会让 classes.dex 走 deflate 压缩。\n"
            "  省 ~0.5MB 包体，换来每次冷启动解压 860KB dex 写盘，直接拖慢首屏。\n"
            "  改为 false（STORED，ART 可 mmap）。"
        )


def check_apk_samples():
    """只核验构建产物目录里的 APK（_tmp 下的历史样本不代表当前配置）。"""
    pats = [
        os.path.join(ROOT, "app", "build", "outputs", "apk", "**", "*.apk"),
    ]
    samples = []
    for p in pats:
        samples.extend(glob.glob(p, recursive=True))

    # 只取最新 2 个，避免扫一堆历史包拖慢
    samples = sorted(set(samples), key=lambda x: -os.path.getmtime(x))[:2]

    for apk in samples:
        try:
            with zipfile.ZipFile(apk) as z:
                dexes = [n for n in z.namelist() if re.match(r"classes\d*\.dex$", n)]
                if not dexes:
                    continue
                for n in dexes:
                    ct = z.getinfo(n).compress_type
                    if ct != zipfile.ZIP_STORED:
                        FAILS.append(
                            f"{os.path.basename(apk)} 里 {n} 是压缩存储 (compress_type={ct})，\n"
                            f"  必须为 ZIP_STORED(0)。改 app/build.gradle 的 useLegacyPackaging = false。"
                        )
        except Exception as e:
            print(f"[WARN] 读 {apk} 失败，跳过: {e}")


def main():
    check_gradle()
    check_apk_samples()

    if FAILS:
        print("[FAIL] dex 未压缩门禁未通过：\n")
        for i, f in enumerate(FAILS, 1):
            print(f"{i}. {f}\n")
        return 1

    print("[PASS] classes.dex 保持 STORED，冷启动零解压。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
