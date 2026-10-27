#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
吉利智驾 内置音频清单与哈希自动更新工具 (scripts/update_voice_manifest.py)
用于自动维护 app/src/main/assets/audio/voice_version.json
"""
import hashlib
import json
import os
import sys

ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
AUDIO_DIR = os.path.join(ROOT_DIR, "app/src/main/assets/audio")
MANIFEST_PATH = os.path.join(AUDIO_DIR, "voice_version.json")


def generate_manifest():
    if not os.path.exists(AUDIO_DIR):
        print(f"Error: {AUDIO_DIR} does not exist!")
        sys.exit(1)

    existing_ver = 1
    if os.path.exists(MANIFEST_PATH):
        try:
            with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
                d = json.load(f)
                existing_ver = d.get("voice_version", 1)
        except Exception:
            pass

    files = {}
    for fn in sorted(os.listdir(AUDIO_DIR)):
        if fn.endswith(".mp3"):
            fp = os.path.join(AUDIO_DIR, fn)
            sz = os.path.getsize(fp)
            with open(fp, "rb") as f:
                md5 = hashlib.md5(f.read()).hexdigest()
            files[fn] = {"size": sz, "md5": md5}

    # 如果有指定的参数递增版本号
    target_ver = existing_ver
    if len(sys.argv) > 1 and sys.argv[1] == "--bump":
        target_ver += 1

    manifest = {
        "voice_version": target_ver,
        "updated_at": "2026-09-22",
        "description": "吉利智驾车规级内置音频资产版本清单与哈希校验索引",
        "total_files": len(files),
        "files": files,
    }

    with open(MANIFEST_PATH, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)

    print(
        f"[OK] Generated {MANIFEST_PATH} (version {target_ver}, {len(files)} files)"
    )


if __name__ == "__main__":
    generate_manifest()
