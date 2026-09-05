#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Geely Store App Lifecycle & OLD Archive Manager
吉利车机商城应用全生命周期管理与 OLD 归档轮换工具

核心规范：
1. 商城软件更新时，严禁直接删除旧包；
2. 自动将当前旧版本归档至 R2 `GeelyToolbox/OLD/` 目录；
3. 每款应用在 OLD/ 下严格保留最多 2 份历史版本供故障秒级回退；
4. 超过 2 份历史备份时，自动按时间戳滚动清除最旧的一份；
5. 自动解析 APK 元数据并更新 apps.json；
6. 自动调用 Cloudflare API 全网 Purge CDN 缓存。
"""

import os
import sys
import json
import time
import datetime
import urllib.request
import argparse
import boto3
from pathlib import Path

# 导入本地 apk 解析工具
sys.path.insert(0, '/root/.hermes/scripts')
try:
    from apk_parser import parse_apk
except ImportError:
    parse_apk = None

def get_env():
    env = {}
    env_file = '/root/.hermes/onepve/.env'
    if os.path.exists(env_file):
        with open(env_file, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#') and '=' in line:
                    k, v = line.split('=', 1)
                    env[k.strip()] = v.strip().strip('"').strip("'")
    return env

def get_s3_client(env):
    acc = env.get('R2_DOWNLOADS_ACCOUNT_ID') or env.get('CLOUDFLARE_ACCOUNT_ID')
    ak = env.get('R2_DOWNLOADS_ACCESS_KEY_ID')
    sk = env.get('R2_DOWNLOADS_SECRET_ACCESS_KEY')
    endpoint = f"https://{acc}.r2.cloudflarestorage.com"
    return boto3.client(
        's3',
        endpoint_url=endpoint,
        aws_access_key_id=ak,
        aws_secret_access_key=sk,
        region_name='auto'
    )

def purge_cf_cache(env, urls):
    token = env.get('CLOUDFLARE_API_TOKEN', '').strip()
    zone_id = '11e721ee98e46e59da3e50fe90eef7ce'
    if not token or not urls:
        return
    try:
        req = urllib.request.Request(
            f"https://api.cloudflare.com/client/v4/zones/{zone_id}/purge_cache",
            data=json.dumps({"files": urls}).encode('utf-8'),
            headers={'Authorization': f"Bearer {token}", 'Content-Type': 'application/json'},
            method='POST'
        )
        with urllib.request.urlopen(req) as resp:
            res = json.loads(resp.read().decode())
            print(f"  [CDN] Cloudflare Purge Cache: {res.get('success')} ({len(urls)} URLs)")
    except Exception as e:
        print(f"  [WARN] Cloudflare Purge Cache failed: {e}")

def archive_and_rotate_old(s3, bucket, prefix, app_filename, app_id, old_version):
    """
    将现有旧包移入 OLD/，且该应用最多保留 2 份历史版本，超量自动滚动删除
    """
    old_source_key = f"{prefix}{app_filename}"
    
    # 1. 检查线上是否存在当前版本
    try:
        s3.head_object(Bucket=bucket, Key=old_source_key)
        exists = True
    except Exception:
        exists = False
        
    if exists:
        timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        stem = Path(app_filename).stem
        ext = Path(app_filename).suffix
        safe_ver = str(old_version).replace(' ', '_').replace('/', '_') if old_version else "prev"
        old_dest_key = f"{prefix}OLD/{stem}_{safe_ver}_{timestamp}{ext}"
        
        print(f"  [ARCHIVE] 正在将现有版本归档至 OLD/ 目录: {old_dest_key}")
        s3.copy_object(
            Bucket=bucket,
            CopySource={'Bucket': bucket, 'Key': old_source_key},
            Key=old_dest_key
        )
    else:
        print(f"  [INFO] 线上未检测到历史同名文件，跳过归档步骤")

    # 2. 扫描并轮换清理：该 app 在 OLD/ 下最多存 2 份
    stem = Path(app_filename).stem
    old_prefix = f"{prefix}OLD/{stem}_"
    try:
        res = s3.list_objects_v2(Bucket=bucket, Prefix=old_prefix)
        backups = res.get('Contents', [])
        # 按 LastModified 升序排序（最旧的在前面）
        backups.sort(key=lambda x: x['LastModified'])
        
        max_keep = 2
        if len(backups) > max_keep:
            to_delete = backups[:len(backups) - max_keep]
            print(f"  [ROTATE] 检测到 {stem} 在 OLD/ 共有 {len(backups)} 份备份，最多保留 {max_keep} 份，开始清理最旧备份:")
            for item in to_delete:
                del_key = item['Key']
                print(f"    - 删除超量旧快照: {del_key}")
                s3.delete_object(Bucket=bucket, Key=del_key)
        else:
            print(f"  [ROTATE] 当前 {stem} 在 OLD/ 下保留 {len(backups)} 份历史版本 (配额 ≤ {max_keep} 份)")
    except Exception as e:
        print(f"  [WARN] 扫描 OLD 目录轮换失败: {e}")

def update_store_app(new_apk_path, target_app_id=None, custom_filename=None, custom_name=None, custom_desc=None):
    if not os.path.isfile(new_apk_path):
        print(f"[ERROR] APK 文件不存在: {new_apk_path}")
        return False
        
    env = get_env()
    s3 = get_s3_client(env)
    bucket = "downloads"
    prefix = "GeelyToolbox/"
    
    print(f"\n==================================================")
    print(f">> 开始商城应用升级与 OLD 归档流")
    print(f"==================================================")
    
    # 1. 解析新 APK 元数据
    apk_meta = parse_apk(new_apk_path) if parse_apk else {}
    file_bytes = os.path.getsize(new_apk_path)
    file_size_mb = f"{file_bytes / (1024 * 1024):.2f} MB"
    
    # 计算哈希
    import hashlib
    md5_hash = hashlib.md5()
    sha256_hash = hashlib.sha256()
    with open(new_apk_path, 'rb') as f:
        while chunk := f.read(65536):
            md5_hash.update(chunk)
            sha256_hash.update(chunk)
    md5_str = md5_hash.hexdigest()
    sha256_str = sha256_hash.hexdigest()
    
    pkg_name = apk_meta.get('package_name', '')
    ver_name = apk_meta.get('version_name', '')
    ver_code = apk_meta.get('version_code', 0)
    
    print(f"  [APK] 解析完成: 包名={pkg_name}, 版本={ver_name}({ver_code}), 大小={file_size_mb}")
    
    # 2. 从 R2 下载最新的 apps.json
    try:
        resp = s3.get_object(Bucket=bucket, Key=f"{prefix}apps.json")
        apps_data = json.loads(resp['Body'].read().decode('utf-8'))
    except Exception as e:
        print(f"[ERROR] 读取 apps.json 失败: {e}")
        return False
        
    # 3. 寻找匹配的 app 条目
    app_entry = None
    if target_app_id:
        for a in apps_data.get('apps', []):
            if a.get('id') == target_app_id:
                app_entry = a
                break
    if not app_entry and pkg_name:
        for a in apps_data.get('apps', []):
            if a.get('package_name') == pkg_name:
                app_entry = a
                break
                
    today = datetime.date.today().strftime("%Y-%m-%d")
    
    if app_entry:
        app_filename = custom_filename or app_entry.get('filename') or os.path.basename(new_apk_path)
        old_version = app_entry.get('version', '')
        print(f"  [MATCH] 匹配到现役应用: ID={app_entry.get('id')}, 原版本={old_version}")
        
        # 4. 执行旧版本归档与轮换（最多留 2 份）
        archive_and_rotate_old(s3, bucket, prefix, app_filename, app_entry.get('id'), old_version)
        
        # 5. 更新条目信息
        app_entry['filename'] = app_filename
        app_entry['download_url'] = f"https://dl.onepve.com/{prefix}{app_filename}"
        app_entry['version'] = str(ver_name) if ver_name else app_entry.get('version')
        app_entry['version_code'] = int(ver_code) if ver_code else app_entry.get('version_code', 0)
        app_entry['size'] = file_size_mb
        app_entry['bytes'] = file_bytes
        app_entry['md5'] = md5_str
        app_entry['sha256'] = sha256_str
        app_entry['updated_at'] = today
        if custom_name:
            app_entry['name'] = custom_name
        if custom_desc:
            app_entry['description'] = custom_desc
    else:
        app_filename = custom_filename or os.path.basename(new_apk_path)
        new_id = target_app_id or (pkg_name.split('.')[-1] if pkg_name else "custom_app")
        print(f"  [NEW] 未在现役清单中匹配，创建新应用条目: ID={new_id}")
        app_entry = {
            "id": new_id,
            "name": custom_name or (apk_meta.get('app_name') or new_id),
            "package_name": pkg_name,
            "version": str(ver_name) or "1.0.0",
            "version_code": int(ver_code) or 1,
            "filename": app_filename,
            "download_url": f"https://dl.onepve.com/{prefix}{app_filename}",
            "size": file_size_mb,
            "bytes": file_bytes,
            "md5": md5_str,
            "sha256": sha256_str,
            "badge": "官方上架",
            "category": "tool",
            "architecture": "universal",
            "need_steering_helper": False,
            "need_freeze_original": False,
            "need_theme_install": False,
            "description": custom_desc or "吉利车机适配软件",
            "updated_at": today,
            "compile_date": today
        }
        apps_data.setdefault('apps', []).append(app_entry)
        
    apps_data['apps_count'] = len(apps_data['apps'])
    apps_data['total_apps'] = len(apps_data['apps'])
    apps_data['total'] = len(apps_data['apps']) + 1
    apps_data['updated_at'] = today

    # 6. 上传新 APK
    target_s3_key = f"{prefix}{app_filename}"
    print(f"  [UPLOAD] 正在上传新版 APK 到: {target_s3_key}")
    s3.upload_file(new_apk_path, bucket, target_s3_key)
    print(f"  [OK] 新版 APK 上传成功: {target_s3_key}")

    # 7. 上传最新 apps.json
    apps_json_bytes = json.dumps(apps_data, ensure_ascii=False, indent=2).encode('utf-8')
    s3.put_object(
        Bucket=bucket,
        Key=f"{prefix}apps.json",
        Body=apps_json_bytes,
        ContentType='application/json; charset=utf-8'
    )
    print(f"  [OK] apps.json 元数据更新并同步 R2 成功 (共 {len(apps_data['apps'])} 款应用)")

    # 8. 强刷 CDN
    urls_to_purge = [
        f"https://dl.onepve.com/{prefix}apps.json",
        f"https://dl.onepve.com/{prefix}{app_filename}"
    ]
    purge_cf_cache(env, urls_to_purge)
    
    print(f"==================================================")
    print(f"✅ 商城应用升级成功！旧版本已安全归档至 OLD/（最多留 2 份）")
    print(f"==================================================\n")
    return True

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description="Geely Store App OLD Archive & Update Tool")
    parser.add_argument("apk", help="新 APK 文件路径")
    parser.add_argument("--id", help="应用 ID (可选)")
    parser.add_argument("--filename", help="线上固定的文件名 (可选)")
    parser.add_argument("--name", help="应用显示名称 (可选)")
    parser.add_argument("--desc", help="应用简介 (可选)")
    
    args = parser.parse_args()
    update_store_app(
        args.apk,
        target_app_id=args.id,
        custom_filename=args.filename,
        custom_name=args.name,
        custom_desc=args.desc
    )
