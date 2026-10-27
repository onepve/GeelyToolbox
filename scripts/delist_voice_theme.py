#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从云端 apps.json 下架语音包（R2 回传 + Cloudflare 刷缓存 + 回读核验）"""
import json, sys, time, urllib.request

sys.path.insert(0, "/home/onepve/.hermes/skills/onepve/geely-store-publish/scripts")
from store_publish import load_env, get_s3_client, purge_cloudflare_cdn, R2_BASE_URL

TARGET_ID = "theme_elegant_zhixing"


def main():
    env = load_env()
    s3 = get_s3_client(env)
    bucket = env.get("R2_DOWNLOADS_BUCKET", "downloads")

    # 1. 下载当前线上 apps.json（带时间戳防缓存）
    url = f"{R2_BASE_URL}apps.json?t={int(time.time())}"
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req) as resp:
        apps_data = json.loads(resp.read().decode("utf-8"))

    apps = apps_data.get("apps", [])
    before = len(apps)
    target = next((a for a in apps if a.get("id") == TARGET_ID), None)
    if not target:
        print(f"!! 未找到 {TARGET_ID}，下架取消")
        sys.exit(1)
    apps[:] = [a for a in apps if a.get("id") != TARGET_ID]
    print(f">> 已移除: {target.get('name')} ({TARGET_ID})，条目 {before} -> {len(apps)}")

    # 2. 本地备份后回传 R2
    backup = f"/tmp/apps_json_backup_{int(time.time())}.json"
    with open(backup, "w", encoding="utf-8") as f:
        json.dump(apps_data, f, ensure_ascii=False, indent=2)
    print(f">> 本地备份: {backup}")

    payload = json.dumps(apps_data, ensure_ascii=False, indent=2).encode("utf-8")
    s3.put_object(Bucket=bucket, Key="GeelyToolbox/apps.json", Body=payload,
                  ContentType="application/json", CacheControl="no-cache")
    print(">> R2 回传完成")

    # 3. CF 刷缓存（apps.json + 语音包试听/封面；APK zip 本体保留在 R2 不删）
    extras = [f"{R2_BASE_URL}VoiceThemes/ElegantZhixing_Preview.mp3",
              f"{R2_BASE_URL}VoiceThemes/ElegantZhixing_Cover.png"]
    ok = purge_cloudflare_cdn(env.get("CLOUDFLARE_CACHE_PURGE_TOKEN") or env.get("CLOUDFLARE_API_TOKEN"),
                              [f"{R2_BASE_URL}apps.json"] + extras)
    print(f">> Cloudflare 刷缓存: {'成功' if ok else '失败(检查 token)'}")

    # 4. 独立回读核验（绕过本地状态，直接请求线上）
    time.sleep(3)
    verify_url = f"{R2_BASE_URL}apps.json?t={int(time.time())}"
    req2 = urllib.request.Request(verify_url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req2) as resp:
        online = json.loads(resp.read().decode("utf-8"))
    ids = [a.get("id") for a in online.get("apps", [])]
    print(f">> 回读核验: 线上 {len(ids)} 条，仍含 {TARGET_ID}: {TARGET_ID in ids}")
    remain = [(a["id"], a.get("name")) for a in online.get("apps", [])]
    print(f">> 线上应用清单: {remain}")
    if TARGET_ID in ids:
        print("!! 下架失败")
        sys.exit(1)
    print("[SUCCESS] 下架闭环完成")


if __name__ == "__main__":
    main()
