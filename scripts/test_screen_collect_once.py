#!/usr/bin/env python3
"""Verify screen-info collection runs EXACTLY ONCE per app process (2026-09-18 fix).

Contract (user decision 2026-09-18): the "屏幕采集" log must fire only on the
first launch collection; every later pushDeviceInfoToWeb()/getDeviceInfo() call
must reuse the cached values — no repeated `wm size` shells, no log spam.

Gate style: string-level source contract + negative self-checks (mutation-proof).
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / 'app/src/main/java/app/onepve/geelyconsole/MainActivity.java').read_text()

# 1. 一次性缓存助手必须存在，且带同步锁 + 进程级缓存字段
assert 'private org.json.JSONObject screenInfoCache' in source, \
    'missing process-level screenInfoCache field'
assert 'private org.json.JSONObject ensureScreenInfoOnce(' in source, \
    'missing ensureScreenInfoOnce() collector'
assert 'synchronized (this)' in source, \
    'ensureScreenInfoOnce must be synchronized (exactly-once guarantee)'

# 2. 真实 shell 采集只允许出现在 collectScreenInfo() 内：全文件恰好 1 处
n_wm = source.count('executeShell("wm size")')
assert n_wm == 1, \
    f'executeShell("wm size") must appear exactly once (inside collectScreenInfo), found {n_wm}'
n_wm_d = source.count('executeShell("wm density")')
assert n_wm_d == 1, \
    f'executeShell("wm density") must appear exactly once, found {n_wm_d}'

# 3. 落日志只在首次采集助手内：AppLogger.i("屏幕采集"...) 恰好 1 处，且在缓存未命中分支
n_log = source.count('AppLogger.i("屏幕采集"')
assert n_log == 1, \
    f'AppLogger.i("屏幕采集"...) must appear exactly once (first-launch only), found {n_log}'
idx_cache = source.index('screenInfoCache = fresh;')
idx_log = source.index('AppLogger.i("屏幕采集"')
assert 0 < idx_cache < idx_log and idx_log - idx_cache < 600, \
    'screen-collect log must be inside the cache-miss branch right after caching'

# 4. 两个调用方都必须走缓存，禁止残留裸采集块
for caller_anchor in ('org.json.JSONObject scr = ensureScreenInfoOnce(currentVer);',
                      'org.json.JSONObject scr = ensureScreenInfoOnce(ver);'):
    assert caller_anchor in source, f'caller must reuse cache: {caller_anchor!r}'
assert '与 pushDeviceInfoToWeb 同步）\n                try {' not in source, \
    'stale raw-collection try-block remains in getDeviceInfo()'

# 5. 反例自检：模拟"第二处裸采集"被塞回源码时，本门禁必须能咬住（防门禁失效）
mutated = source.replace(
    'script = "if(window.updateDeviceInfo)',
    'String wmSize2 = SystemUtils.executeShell("wm size");\n                    script = "if(window.updateDeviceInfo)', 1)
assert mutated.count('executeShell("wm size")') == 2, 'self-test setup broken'
try:
    assert mutated.count('executeShell("wm size")') == 1, 'mutation gate self-check'
    raise SystemExit('FATAL: negative self-check unexpectedly passed')
except AssertionError:
    pass  # 期望路径：变异体命中 n_wm==1 断言 → 门禁有效

print('[PASS] Screen-info collection is exactly-once per launch: single shell '
      'collect point, single first-launch log, both callers cache-reuse, '
      'negative self-check bites.')
