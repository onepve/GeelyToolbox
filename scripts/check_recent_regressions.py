#!/usr/bin/env python3
"""Scoped source contracts for recent regressions; no hardware claims.
Every contract is mutation-tested by replacing the protected method/region.
"""
import re
from pathlib import Path
from check_button_focus import has_touch_focus_guard

JAVA = 'app/src/main/java/app/onepve/geelyconsole/'


def clean(source):
    pattern = r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/'
    return re.sub(pattern, lambda m: '' if m[0].startswith(('//', '/*')) else m[0], source, flags=re.S)


def region(source, marker):
    start = source.index(marker)
    opening = source.index('{', start)
    depth = 0
    # Skip braces inside Java/JS strings.
    for token in re.finditer(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|[{}]', source[opening:]):
        if token[0] == '{':
            depth += 1
        elif token[0] == '}':
            depth -= 1
            if depth == 0:
                return source[opening + 1:opening + token.start()]
    raise ValueError('Unclosed region: ' + marker)


# id, path, method/block marker, required regexes, forbidden regexes
CONTRACTS = [
 ('tts-buffer', JAVA+'utils/VehicleVoicePlayer.java', 'public void speakText(final String text, final String voiceType)',
  [r'pendingText\s*=\s*text', r'pendingVoiceType\s*=\s*voiceType', r'pendingTextAt\s*=\s*System.currentTimeMillis\(\)', r'ensureTtsReady\(\)'], []),
 ('tts-ready-flush', JAVA+'utils/VehicleVoicePlayer.java', 'private void initTts()', [r'ttsReady\s*=\s*true', r'flushPendingSpeech\(\)'], []),
 ('tts-expiry', JAVA+'utils/VehicleVoicePlayer.java', 'private void flushPendingSpeech()',
  [r'System.currentTimeMillis\(\)\s*-\s*at\s*>\s*PENDING_TTL_MS', r'pendingText\s*=\s*null',r'speakText\(text,\s*voiceType\)'], []),
 ('tts-retry', JAVA+'utils/VehicleVoicePlayer.java', 'public void ensureTtsReady()',
  [r'mainHandler.post\(', r'now\s*-\s*lastInitAttemptAt\s*<\s*INIT_RETRY_INTERVAL_MS',r'initTts\(\)'], []),
 ('unlock-prewarm', JAVA+'services/VehicleAutomationService.java', 'if (bootReason == 0 || bootReason == 1)', [r'voicePlayer.ensureTtsReady\(\)'], []),
 ('screen-not-poweroff', JAVA+'services/VehicleAutomationService.java', 'private void registerPowerStateReceiver()',
  [r'boolean realPowerOff\s*=[^;]+;', r'else if\s*\(screenOff\)'], [r'boolean realPowerOff\s*=[^;]*ACTION_SCREEN_OFF']),
 ('screen-no-reset', JAVA+'services/VehicleAutomationService.java', 'else if (screenOff)', [], [r'lastPowerMode\s*=',r'resetState\(',r'voicePlayer.play\(']),
 ('engine-state', JAVA+'services/VehicleAutomationService.java', 'public boolean isEngineRunning()',
  [r'if\s*\(lastPowerMode == 0\) return false',r'latestBatteryVoltage >= 13.2f',r'currentSpeedKmH == 0'], []),
 ('idle-clamp', JAVA+'utils/IdleScreensaverManager.java', 'public static int clampSeconds(int v)',
  [r'if\s*\(v == NEVER_SECONDS\) return NEVER_SECONDS',r'MIN_SECONDS',r'MAX_SECONDS'], []),
 ('idle-never-A', JAVA+'utils/IdleScreensaverManager.java', 'private static void doTick()',
  [r'if\s*\(getSeconds\(ctx\) == NEVER_SECONDS\)\s*\{[^}]*return;'], []),
 ('idle-never-B', JAVA+'utils/IdleScreensaverManager.java', 'private static void registerScreenOffReceiver(Context ctx)',
  [r'if\s*\(getSeconds\(context\) == NEVER_SECONDS\) return;'], []),
 ('idle-native-entry', JAVA+'utils/IdleScreensaverManager.java', 'private static synchronized String triggerScreensaver',
  [r'new Intent\(ACTION_SCREENSAVER\)',r'addCategory\(CATEGORY_SCREENSAVER\)',r'setPackage\(PKG_SCREENSAVER\)',r'ctx.startService\(i\)'], [r'Somnambulator',r'startActivity\(']),
 ('idle-manual', JAVA+'utils/IdleScreensaverManager.java', 'public static String triggerNow', [r'triggerScreensaver\('], [r'NEVER_SECONDS']),
 ('idle-timeout', JAVA+'utils/IdleScreensaverManager.java', 'private static long readIdleMs', [r'executeWithTimeout\(',r'3000L'], [r'executePrivileged\(']),
 ('idle-service', JAVA+'services/VehicleAutomationService.java', 'public static void syncState',
  [r'boolean shouldRun\s*=[^;]*IdleScreensaverManager.KEY_ENABLED'], []),
 ('idle-ui-never', 'web/src/views/FloatingView.vue', 'function toggleNever()', [r'saveScreensaverConfig\(\{ seconds: -1 \}\)',r'saveScreensaverConfig\(\{ seconds: 30 \}\)'], []),
 ('theme-install-flag', 'web/src/components/modals/AppDetailModal.vue', 'const isMapApp = computed',
  [r'if\s*\(typeof app.value.need_theme_install === .boolean.\)\s*\{\s*return app.value.need_theme_install;'], [r'category\s*===\s*.navigation.']),
 ('pkg-query-no-shell', JAVA+'utils/SystemUtils.java', 'public static int getAppDetailedState',
  [r'getApplicationEnabledSetting\(',r'disabledPkgsCache',r'warmDisabledPackagesCache\(\)'], [r'executeShell\(',r'Runtime.getRuntime\(',r'new ProcessBuilder']),
 ('whitelist-cache', JAVA+'utils/SystemUtils.java', 'public static boolean isApkVerifyWhitelistEnabled()',
  [r'WHITELIST_TTL_MS',r'refreshWhitelistAsync\(\)'], [r'executeShell\(']),
 ('adb-cached', JAVA+'utils/SystemUtils.java', 'public static String executePrivileged',
  [r'isAdbPortOpenCached\(\)',r'execProcess\("su", "-c", cmd, 2500L\)'], [r'\.waitFor\(\)']),
 ('process-timeout', JAVA+'utils/SystemUtils.java', 'public static String execProcess',
  [r'waitFor\(timeoutMs,',r't.join\(timeoutMs \+ 200L\)'], [r'\.waitFor\(\)']),
 ('foreground-breaker', JAVA+'utils/ForegroundAppDetector.java', 'private static String queryByDumpsys',
  [r'System.currentTimeMillis\(\) < dumpsysPausedUntil',r'dumpsysFailStreak >= 3',r'executeWithTimeout\('], []),
 ('device-info-worker', JAVA+'MainActivity.java', 'public void pushDeviceInfoToWeb()',
  [r'DEVICE_INFO_EXECUTOR.execute\(',r'if\s*\(deviceInfoPushing\) return',r'mainHandler.post\('], []),
 ('wheel-cache', JAVA+'utils/SteeringWheelKeyManager.java', 'public int parseKeyFromLine', [r'wheelMasterCached'], [r'prefs.get',r'getSharedPreferences\(']),
 ('ecarx-no-wrong-key', JAVA+'services/VehicleAutomationService.java', 'private void registerEcarxKeyReceiver()',
  [r'KEY_MUTE',r'KEY_PREV',r'KEY_NEXT'], [r'mappedKey\s*=\s*SteeringWheelKeyManager.KEY_OK']),
 ('gear-reset-quiet', JAVA+'utils/GearStateMachine.java', 'public synchronized void resetState()', [r'if\s*\(changed\)\s*\{\s*AppLogger.i\('], []),
 ('mode-reset-quiet', JAVA+'utils/DriveModeManager.java', 'public synchronized void resetState()', [r'if\s*\(changed\)\s*\{\s*AppLogger.i\('], []),
 ('door-reset-quiet', JAVA+'utils/DoorStateManager.java', 'public synchronized void resetState()', [r'if\s*\(changed\)\s*\{\s*AppLogger.i\('], []),
]


def evaluate(files):
    failures = []
    for ident, path, marker, required, forbidden in CONTRACTS:
        try:
            body = region(clean(files[path]), marker)
            if not all(re.search(p, body, re.S) for p in required) or any(re.search(p, body, re.S) for p in forbidden):
                failures.append(ident)
        except (KeyError, ValueError):
            failures.append(ident)
    for path in ('web/src/App.vue', 'app/src/main/assets/toolbox_ui.html'):
        if not has_touch_focus_guard(files.get(path, '')):
            failures.append('button-focus:' + path)
    all_java = '\n'.join(clean(v) for k,v in files.items() if k.endswith('.java'))
    if re.search(r'\b(?:CarGearHALMonitor|CarPropertyKeyMonitor)\b', all_java):
        failures.append('retired-hal-listeners')
    logger = clean(files.get(JAVA+'utils/AppLogger.java', ''))
    if not re.search(r'if\s*\(lastLogTime.size\(\) > 500\)\s*\{\s*lastLogTime.clear\(\);', logger):
        failures.append('log-dedup-bounded')
    return failures


def load(root):
    root = Path(root)
    paths = {c[1] for c in CONTRACTS} | {'web/src/App.vue', 'app/src/main/assets/toolbox_ui.html', JAVA+'utils/AppLogger.java'}
    paths.update(str(p.relative_to(root)) for p in (root/JAVA).rglob('*.java'))
    return {p:(root/p).read_text() for p in paths if (root/p).exists()}


if __name__ == '__main__':
    import sys
    root = Path(__file__).resolve().parents[1]
    failures = evaluate(load(root))
    for failure in failures:
        print('[FAIL] regression:', failure)
    if not failures:
        print('[PASS] recent regression source contracts')
    sys.exit(bool(failures))
