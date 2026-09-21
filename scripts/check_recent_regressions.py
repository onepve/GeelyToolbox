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
 ('tts-arbiter-route', JAVA+'utils/VehicleVoicePlayer.java', 'public void speakText(final String text, final String voiceType)',
  [r'play\("tts:"', r'PRIORITY_P2_DOOR'], []),
 ('tts-buffer', JAVA+'utils/VehicleVoicePlayer.java', 'private void speakTextInternal(final String text, final String arbiterKey)',
  [r'pendingText\s*=\s*text', r'pendingVoiceType\s*=\s*arbiterKey', r'pendingTextAt\s*=\s*System.currentTimeMillis\(\)', r'ensureTtsReady\(\)'], []),
 ('tts-ready-flush', JAVA+'utils/VehicleVoicePlayer.java', 'private void initTts()', [r'ttsReady\s*=\s*true', r'flushPendingSpeech\(\)'], []),
 ('tts-expiry', JAVA+'utils/VehicleVoicePlayer.java', 'private void flushPendingSpeech()',
  [r'System.currentTimeMillis\(\)\s*-\s*at\s*>\s*PENDING_TTL_MS', r'pendingText\s*=\s*null',r'speakText\(text,\s*voiceType\)'], []),
 ('webserver-options-preflight', JAVA+'server/WebServer.java', 'private void handleOptions(OutputStream out)',
  [r'HTTP/1\.1 204 No Content', r'Access-Control-Allow-Origin', r'Access-Control-Allow-Methods'], []),
 ('webserver-incomplete-data-check', JAVA+'server/WebServer.java', 'private void handleApiUploadChunk(InputStream in, int length, Map<String, String> headers, String queryString, OutputStream out)',
  [r'totalWritten\s*<\s*length', r'incomplete_data'], []),
 ('tts-retry', JAVA+'utils/VehicleVoicePlayer.java', 'public void ensureTtsReady()',
  [r'mainHandler.post\(', r'now\s*-\s*lastInitAttemptAt\s*<\s*INIT_RETRY_INTERVAL_MS',r'initTts\(\)'], []),
 ('unlock-prewarm', JAVA+'services/VehicleAutomationService.java', 'if (bootReason == 0 || bootReason == 1)', [r'voicePlayer.ensureTtsReady\(\)'], []),
 ('screen-not-poweroff', JAVA+'services/VehicleAutomationService.java', 'private void registerPowerStateReceiver()',
  [r'boolean realPowerOff\s*=[^;]+;', r'else if\s*\(screenOff\)'], [r'boolean realPowerOff\s*=[^;]*ACTION_SCREEN_OFF']),
 ('screen-no-reset', JAVA+'services/VehicleAutomationService.java', 'else if (screenOff)', [], [r'lastPowerMode\s*=',r'resetState\(',r'voicePlayer.play\(']),
 ('engine-state', JAVA+'services/VehicleAutomationService.java', 'public boolean isEngineRunning()',
  [r'if\s*\(lastPowerMode == 0\) return false',r'latestBatteryVoltage >= 13.2f',r'currentSpeedKmH == 0'], []),
 ('media-playing-no-audio-fallback', JAVA+'services/VehicleAutomationService.java', 'private boolean isAnyMediaPlaying()',
  [r'MEDIA_SESSION_SERVICE',r'STATE_PLAYING'], [r'isMusicActive']),
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
 ('idle-ui-adjust', 'web/src/views/FloatingView.vue', 'function adjustSeconds(delta)', [r'ssSeconds\.value\s*\+\s*delta', r'saveSeconds\(\)'], []),
 ('theme-install-flag', 'web/src/components/modals/AppDetailModal.vue', 'const isMapApp = computed',
  [r'if\s*\(typeof app.value.need_theme_install === .boolean.\)\s*\{\s*return app.value.need_theme_install;'], [r'category\s*===\s*.navigation.']),
 ('appselect-gesture-channel', 'web/src/components/modals/AppSelectModal.vue', 'function selectApp(app)',
  [r"setWheelGestureAction", r"lastIndexOf\('_'\)"], [r'setWheelControlStringSetting[^;]*wheel_action_']),
 ('pkg-query-no-shell', JAVA+'utils/SystemUtils.java', 'public static int getAppDetailedState',
  [r'getApplicationEnabledSetting\(',r'disabledPkgsCache',r'warmDisabledPackagesCache\(\)'], [r'executeShell\(',r'Runtime.getRuntime\(',r'new ProcessBuilder']),
 ('whitelist-cache', JAVA+'utils/SystemUtils.java', 'public static boolean isApkVerifyWhitelistEnabled()',
  [r'WHITELIST_TTL_MS',r'refreshWhitelistAsync\(\)'], [r'executeShell\(']),
 ('adb-cached', JAVA+'utils/SystemUtils.java', 'public static String executePrivileged',
  [r'isAdbPortOpenCached\(\)', r'executeShell\(cmd\)'], [r'execProcess\("su"', r'\bsu\b']),
 ('process-timeout', JAVA+'utils/SystemUtils.java', 'public static String execProcess',
  [r'waitFor\(timeoutMs,',r't.join\(timeoutMs \+ 200L\)'], [r'\.waitFor\(\)']),
 ('foreground-breaker', JAVA+'utils/ForegroundAppDetector.java', 'private static String queryByDumpsys',
  [r'System.currentTimeMillis\(\) < dumpsysPausedUntil',r'dumpsysFailStreak >= 3',r'executeWithTimeout\('], []),
 ('device-info-worker', JAVA+'MainActivity.java', 'public void pushDeviceInfoToWeb()',
  [r'DEVICE_INFO_EXECUTOR.execute\(',r'if\s*\(deviceInfoPushing\) return',r'mainHandler.post\('], []),
 ('wheel-cache', JAVA+'utils/SteeringWheelKeyManager.java', 'public int parseKeyFromLine',
  [r'wheelMasterCached', r'0x37', r'0x2d'], [r'prefs.get', r'getSharedPreferences\(', r'syntheticFallbackDisabled']),
 ('wheel-screen-off', JAVA+'utils/SteeringWheelKeyManager.java', 'private void turnScreenOff',
  [r'Somnambulator', r'context.startActivity\(intent\)'], []),
 ('ecarx-no-wrong-key', JAVA+'services/VehicleAutomationService.java', 'private void registerEcarxKeyReceiver',
  [r'KEY_MUTE',r'KEY_PREV',r'KEY_NEXT'], [r'mappedKey\s*=\s*SteeringWheelKeyManager.KEY_OK']),
 ('gear-reset-quiet', JAVA+'utils/GearStateMachine.java', 'public synchronized void resetState()', [r'if\s*\(changed\)\s*\{\s*AppLogger.i\('], []),
 ('mode-reset-quiet', JAVA+'utils/DriveModeManager.java', 'public synchronized void resetState()', [r'if\s*\(changed\)\s*\{\s*AppLogger.i\('], []),
 ('door-reset-quiet', JAVA+'utils/DoorStateManager.java', 'public synchronized void resetState()', [r'if\s*\(changed\)\s*\{\s*AppLogger.i\('], []),
 ('voice-gain-key-normalize', JAVA+'utils/VoiceGainResolver.java', 'public static String normalizeVoiceKey',
  [r'\.mp3', r'\.wav', r'\.ogg', r'endsWith', r'substring'], []),
 ('voice-gain-stream-map', JAVA+'utils/VoiceGainResolver.java', 'public static int resolveStreamForChannel',
  [r'STREAM_NAVI', r'STREAM_SYSTEM', r'STREAM_MUSIC'], [r'STREAM_NOTIFICATION']),
 ('voice-gain-playback-applies-resolver', JAVA+'utils/VehicleVoicePlayer.java', 'private synchronized void applyVolumeOffsetBeforePlay',
  [r'VoiceGainResolver\.normalizeVoiceKey', r'VoiceGainResolver\.resolveStreamForChannel'], [r'STREAM_NOTIFICATION']),
 ('steer-park-snapshot', JAVA+'services/VehicleAutomationService.java', 'private void checkSteerAngleOnPark()',
  [r'PARK_STEER_SNAPSHOT_FRESHNESS_MS', r'checkSteerAngleNotCentered\(now,', r'PRIORITY_P2_DOOR'], [r'checkSteerAngleNotCentered\(now\)']),
 ('steer-user-threshold', JAVA+'services/VehicleAutomationService.java', 'private void checkSteerAngleOnPark()',
  [r'getInt\("voice_steer_angle_threshold_deg"', r'clampSteerAngleThreshold',
   r'checkSteerAngleNotCentered\(now, steerThreshold,', r'PARK_STEER_SNAPSHOT_FRESHNESS_MS'], []),
 ('steer-threshold-bridge', JAVA+'MainActivity.java', 'public boolean setVehicleAutomationIntSetting(final String key, final int value)',
  [r'voice_steer_angle_threshold_deg', r'clampSteerAngleThreshold', r'putInt\(key, normalized\)'], []),
 ('steer-threshold-ui', 'web/src/components/SafetyGuardVoiceSection.vue', 'function adjustSteerThreshold',
  [r'Math\.max\(15, Math\.min\(180, current \+ delta\)\)',
   r'setVehicleAutomationIntSetting.*voice_steer_angle_threshold_deg'], []),
 ('steer-signed-angle', JAVA+'services/VehicleAutomationService.java', 'INFO_ID_VSTEERWHEELINFO_ANGLE_VALUE',
  [r'decodeSignedSteerAngle', r'feedSteerAngleDegrees'], [r'feedSteerAngle\(raw']),
 ('steer-signed-decoder', JAVA+'utils/SafetySensorStateMachine.java', 'public static int decodeSignedSteerAngle',
  [r'0x7FFF', r'0x10000', r'extractLow16'], []),
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
    if not re.search(r'if\s*\(lastLogTime\.size\(\) > 500\)\s*\{\s*lastLogTime\.clear\(\);', logger):
        failures.append('log-dedup-bounded')
    # 发布说明必须按真实版本改动生成，严禁在发布脚本里硬编码固定文案
    # (历史缺陷: 测试通道 changelog 写死远古版本号，每次发版都带出过期更新内容)
    publisher = files.get('scripts/publish_r2.py', '')
    if not re.search(r'build_changelog\(', publisher):
        failures.append('changelog-from-vcs')
    if re.search(r'【测试通道优先体验\s*beta-v', publisher) or re.search(r'【正式版\s*v\d+\.\d+\.\d+】', publisher):
        failures.append('changelog-no-hardcoded-version')
    builder = files.get('scripts/changelog_builder.py', '')
    if not re.search(r'def build_changelog\(', builder) or not re.search(r'def read_override\(', builder):
        failures.append('changelog-builder-contract')
    floating_vue = files.get('web/src/views/FloatingView.vue', '')
    if re.search(r'<HelpDot\s+@click\.stop', floating_vue):
        failures.append('helpdot-no-click-stop')
    if not re.search(r'<HelpDot\s+@click="openFloatingHelp"', floating_vue) or not re.search(r'<HelpDot\s+@click="openScreensaverHelp"', floating_vue):
        failures.append('helpdot-click-bound')
    if re.search(r'立即测试屏保效果', floating_vue) or not re.search(r'<span>屏保测试</span>', floating_vue):
        failures.append('screensaver-test-button-clean')
    mweb = files.get('app/src/main/assets/mobile_web.html', '')
    if re.search(r'accept=[\'"][^\'"]*apk[^\'"]*[\'"]', mweb):
        failures.append('mobile-web-unrestricted-transfer')
    if not re.search(r'retry\s*<\s*3', mweb) or not re.search(r'wakeLock', mweb):
        failures.append('mobile-web-transfer-reliability')
    return failures


def load(root):
    root = Path(root)
    paths = {c[1] for c in CONTRACTS} | {'web/src/App.vue', 'web/src/views/FloatingView.vue', 'app/src/main/assets/toolbox_ui.html', 'app/src/main/assets/mobile_web.html', JAVA+'utils/AppLogger.java', 'scripts/publish_r2.py', 'scripts/changelog_builder.py'}
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
