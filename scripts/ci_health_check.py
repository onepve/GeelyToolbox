#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
CI Health Checker for GeelyToolbox
Validates:
1. DOM Event Handlers (onclick / onchange / oninput) vs defined JS functions
2. JSBridge Contract (HTML ToolboxBridge calls vs Java @JavascriptInterface exports)
3. Cloud Apps & Firmware Asset Links (HEAD verification with retries)
"""

import json
import os
import re
import sys
import time
import urllib.request

ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
HTML_PATH = os.path.join(ROOT_DIR, "app/src/main/assets/toolbox_ui.html")
JAVA_PATH = os.path.join(ROOT_DIR, "app/src/main/java/app/onepve/geelyconsole/MainActivity.java")

passed = True


def log_step(title):
    print(f"\n{'='*60}\n>> {title}\n{'='*60}")


# ----------------------------------------------------------------------
# 1. DOM Event Handlers Check
# ----------------------------------------------------------------------
log_step("1. Checking HTML DOM Event Handlers & Defined Functions")
if not os.path.exists(HTML_PATH):
    print(f"[FAIL] HTML file not found at: {HTML_PATH}")
    sys.exit(1)

with open(HTML_PATH, "r", encoding="utf-8") as f:
    html_content = f.read()

# Match event attributes like onclick="funcName(...)"
event_matches = re.findall(r'on(?:click|change|input)=["\']([^"\']+)["\']', html_content)
called_funcs = set()
for expr in event_matches:
    # Match standard function invocation
    funcs = re.findall(r'([a-zA-Z0-9_]+)\s*\(', expr)
    for fn in funcs:
        # Ignore common JS built-ins
        if fn in {"stopPropagation", "preventDefault", "alert", "confirm", "parseInt", "parseFloat", "String"}:
            continue
        called_funcs.add(fn)

defined_funcs = set(re.findall(r'function\s+([a-zA-Z0-9_]+)\s*\(', html_content))
missing_funcs = [fn for fn in sorted(called_funcs) if fn not in defined_funcs]

print(f"Total DOM event handlers inspected: {len(event_matches)}")
print(f"Unique functions invoked from DOM: {len(called_funcs)}")
print(f"Unique functions defined in HTML: {len(defined_funcs)}")

if missing_funcs:
    print(f"[FAIL] Missing JS function definitions for DOM handlers: {missing_funcs}")
    passed = False
else:
    print("[PASS] All DOM event handlers have matching JS function definitions.")


# ----------------------------------------------------------------------
# 2. JSBridge Contract Check
# ----------------------------------------------------------------------
log_step("2. Checking JSBridge Contract (HTML vs Java Interface)")
if not os.path.exists(JAVA_PATH):
    print(f"[FAIL] Java file not found at: {JAVA_PATH}")
    sys.exit(1)

with open(JAVA_PATH, "r", encoding="utf-8") as f:
    java_content = f.read()

# Match frontend Bridge calls
bridge_calls = set(re.findall(r'ToolboxBridge\.([a-zA-Z0-9_]+)\s*\(', html_content))
bridge_calls.update(re.findall(r'callBridge\(["\']([a-zA-Z0-9_]+)["\']', html_content))

# Match Java @JavascriptInterface methods
java_methods = set(
    re.findall(r'@JavascriptInterface\s+public\s+[^\(]+\s+([a-zA-Z0-9_]+)\s*\(', java_content)
)

missing_in_java = sorted(list(bridge_calls - java_methods))
orphans_in_java = sorted(list(java_methods - bridge_calls))

print(f"Bridge methods called by frontend: {len(bridge_calls)}")
print(f"Java @JavascriptInterface methods exported: {len(java_methods)}")

if missing_in_java:
    print(f"[FAIL] Frontend calls Bridge methods NOT found in Java: {missing_in_java}")
    passed = False
else:
    print("[PASS] 100% of frontend Bridge calls are implemented in Java.")

if orphans_in_java:
    print(f"[INFO] Exported Java methods currently unused in frontend: {orphans_in_java}")


# ----------------------------------------------------------------------
# 3. Cloud Apps & Firmware Asset Links Check
# ----------------------------------------------------------------------
log_step("3. Checking Cloud Apps & Firmware Asset Links (HEAD verification)")
match = re.search(r'var\s+CLOUD_APPS\s*=\s*(\[.*?\]);', html_content, re.DOTALL)
if not match:
    print("[FAIL] CLOUD_APPS array not found in HTML!")
    passed = False
else:
    try:
        cloud_apps = json.loads(match.group(1))
    except Exception as e:
        print(f"[FAIL] Failed to parse CLOUD_APPS JSON: {e}")
        cloud_apps = []
        passed = False

    print(f"Total cloud apps to verify: {len(cloud_apps)}")
    link_failures = []

    for app in cloud_apps:
        name = app.get("name", "Unknown")
        filename = app.get("filename", "")
        url = app.get("url") or f"https://dl.onepve.com/GeelyToolbox/apps/{filename}"

        status_code = None
        last_error = None

        # Retry up to 3 times to avoid transient network jitter in GitHub CI runners
        for attempt in range(1, 4):
            try:
                req = urllib.request.Request(
                    url,
                    method="HEAD",
                    headers={"User-Agent": "GeelyToolbox-CI/1.0 (Mozilla/5.0)"}
                )
                with urllib.request.urlopen(req, timeout=10) as resp:
                    status_code = resp.status
                    if status_code in (200, 302, 301):
                        break
            except Exception as ex:
                last_error = ex
                time.sleep(1)

        if status_code in (200, 302, 301):
            print(f"  [OK] {status_code} - {name[:30]:<30} -> {url}")
        else:
            print(f"  [FAIL] {name[:30]:<30} -> {url} (Error: {last_error})")
            link_failures.append((name, url, str(last_error)))

    if link_failures:
        print(f"\n[FAIL] {len(link_failures)} asset links failed verification!")
        passed = False
    else:
        print("\n[PASS] All cloud app & firmware asset links verified accessible.")


# ----------------------------------------------------------------------
# Final Result
# ----------------------------------------------------------------------
log_step("CI Health Check Summary")
if passed:
    print("[SUCCESS] All CI health gate checks passed cleanly! (0 dead links, 0 contract violations)")
    sys.exit(0)
else:
    print("[FAILED] One or more CI health checks failed. Please review errors above.")
    sys.exit(1)
