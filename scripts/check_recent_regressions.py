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
 ('webserver-options-preflight', JAVA+'server/WebServer.java', 'private void handleOptions(OutputStream out)',
  [r'HTTP/1\.1 204 No Content', r'Access-Control-Allow-Origin', r'Access-Control-Allow-Methods'], []),
 ('webserver-incomplete-data-check', JAVA+'server/WebServer.java', 'private void handleApiUploadChunk(InputStream in, int length, Map<String, String> headers, String queryString, OutputStream out)',
  [r'totalWritten\s*<\s*length', r'incomplete_data'], []),
 ('unlock-prewarm', JAVA+'services/VehicleAutomationService.java', 'if (bootReason == 0 || bootReason == 1)', [r'voicePlayer.ensureTtsReady\(\)'], []),
 ('screen-not-poweroff', JAVA+'services/VehicleAutomationService.java', 'private void registerPowerStateReceiver()',
  [r'boolean realPowerOff\s*=[^;]+;', r'else if\s*\(screenOff\)'], [r'boolean realPowerOff\s*=[^;]*ACTION_SCREEN_OFF']),
 ('screen-no-reset', JAVA+'services/VehicleAutomationService.java', 'else if (screenOff)', [], [r'lastPowerMode\s*=',r'resetState\(',r'voicePlayer.play\(']),
 ('engine-state', JAVA+'services/VehicleAutomationService.java', 'public boolean isEngineRunning()',
  [r'if\s*\(lastPowerMode == 0\) return false',r'latestBatteryVoltage >= 13.2f',r'currentSpeedKmH == 0'], []),
 ('media-playing-no-audio-fallback', JAVA+'services/VehicleAutomationService.java', 'public boolean isAnyMediaPlaying()',
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
 # 卡兔子主题高阶通道：必须收纳于【整车应用管理】独立卡片并通过三重安全确认，严禁在商城下载弹窗开设旁路入口
 ('rabbit-theme-entry-isolated', 'web/src/views/InstallView.vue', 'function openRabbitGuideModal',
  [r'store\.modals\.rabbitInstall\s*=\s*true'], []),
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
 ('wheel-media-dispatch', JAVA+'utils/SteeringWheelKeyManager.java', 'private void sendMediaKeyEvent(int keyCode, int targetSource)',
  [r'resolveTargetMediaPackage', r'isIgnoredMediaPackage', r'getInstalledMediaPackages', r'ACTION_MEDIA_BUTTON'], [r'String\[\]\s+targetPkgs\s*=\s*\{\s*"com\.tencent\.qqmusiccar"']),
 ('vehicle-hal-platform', JAVA+'hal/VehiclePlatformFactory.java', 'private static IVehiclePlatform detectPlatform',
  [r'BinyueCoolPlatform', r'GenericAospPlatform', r'SX11'], []),
 ('audio-channel-tester', JAVA+'utils/ChannelAudioTester.java', 'public static void playChannelBeep',
  [r'AudioAttributes\.Builder', r'setUsage', r'AudioTrack'], []),
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
 ('eas-bluetooth-duck-guard', JAVA+'utils/EasMediaBridge.java', 'public synchronized void activateBluetoothChannel',
  [r'requestDuckingFocusWhenFrozen', r'sendKeepSourceBroadcastWhenFrozen', r'updateCurrentSourceType'], [r'am\.abandonAudioFocus', r'dummyListener', r'isAutoWakeSuppressed']),
 ('eas-bluetooth-anti-duck', JAVA+'utils/EasMediaBridge.java', 'public synchronized void requestDuckingFocusWhenFrozen',
  [r'AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK', r'requestAudioFocus'], [r'abandonBluetoothFocus']),
 ('eas-bluetooth-keep-source', JAVA+'utils/EasMediaBridge.java', 'public void sendKeepSourceBroadcastWhenFrozen()',
  [r'ECARX_WIDGET_BLUETOOTH_PLAY', r'com\.ecarx\.multimedia'], []),
 ('cool-drive-mode-smart-lock', JAVA+'utils/VehicleVoicePlayer.java', 'private static String[] getChineseAliases(String baseName)',
  [r'drive_mode_smart', r'智能模式'], [r'雪地模式']),
 ('cool-power-off-0x03-lock', JAVA+'services/VehicleAutomationService.java', 'private int parsePowerStateLine(String line)',
  [r'low == 0x03', r'return 0'], []),
 ('eas-a2dp-stream-resume', JAVA+'utils/EasMediaBridge.java', 'private void registerA2dpReceiver',
  [r'a2dpStreaming', r'activateBluetoothChannel', r'connectBtMediaBrowser'], [r'pauseBluetoothSinkWhenFrozen']),
 ('eas-bluetooth-streaming-guard', JAVA+'utils/EasMediaBridge.java', 'public synchronized void activateBluetoothChannel',
  [r'requestDuckingFocusWhenFrozen', r'sendKeepSourceBroadcastWhenFrozen'], [r'isAutoWakeSuppressed', r'dummyListener']),
 ('eas-bluetooth-voice-bypass-focus', JAVA+'utils/VehicleVoicePlayer.java', 'private void requestAudioFocus',
  [r'isBluetoothChannelActive', r'bypassing requestAudioFocus'], []),
 ('eas-bluetooth-pause-sink', JAVA+'utils/EasMediaBridge.java', 'public void pauseBluetoothSinkWhenFrozen()',
  [r'btMediaController\.getTransportControls\(\)\.pause\(\)', r'mc\.getTransportControls\(\)\.pause\(\)'], []),
 ('wheel-bluetooth-exclusive-dispatch', JAVA+'utils/SteeringWheelKeyManager.java', 'private void sendMediaKeyEvent(int keyCode, int targetSource)',
  [r'com\.android\.bluetooth', r'dispatchedToBt', r'mc\.getTransportControls\(\)'], []),
 ('mode-360-suppress-multimedia', JAVA+'utils/SteeringWheelKeyManager.java', 'public void suppressOriginalMultimedia()',
  [r'am force-stop com\.ecarx\.multimedia'], []),
 ('music-cold-start-service', JAVA+'services/VehicleAutomationService.java', 'public void wakeUpTargetMediaService(final String pkg)',
  [r'tryStartComponentService', r'MediaBrowserService'], []),
 ('speed-autoplay-dynamic-pkg', JAVA+'services/VehicleAutomationService.java', 'private void triggerMusicAutoplay(final String pkg, final boolean fullscreen)',
  [r'wakeUpTargetMediaService', r'ACTION_MEDIA_BUTTON'], []),
 ('speed-autoplay-call-guard', JAVA+'services/VehicleAutomationService.java', 'private void triggerMusicAutoplay(final String pkg, final boolean fullscreen)',
  [r'isInPhoneCall'], []),
]



def ui_border_density_contract(files):
    """界面线条密度门禁（用户实车反馈「线条太多、点亮态一圈金边刺眼」后锁死）。

    三组铁律，任一被改回即视为回归：
      1. 全栈禁用 Tailwind ring-*（Chromium 68 多重 box-shadow 会浮错位鬼影）。
      2. 点亮/选中一律只给一道局部标记（左侧竖条或实底胶囊），
         严禁把整张卡片或整个 tab 染成一圈金边。
      3. 卡片内部不再画小方框套娃：托盘/格子用底色分层。
    """
    out = []
    web = [(k, v) for k, v in files.items() if k.startswith('web/src/')]
    all_web = '\n'.join(v for _, v in web)

    # 1) ring-* 全栈清除
    if re.search(r'\bring-(?:inset|offset-\d+|\d+|\[\d+px\])', all_web):
        out.append('ui-no-ring-shadow')

    def of(path):
        return files.get(path, '')

    # 2) 侧边栏大导航：选中用左侧竖条，不得整圈金边
    side = of('web/src/components/Sidebar.vue')
    if 'border-l-car-accent' not in side or 'border-l-[6px]' not in side:
        out.append('ui-nav-selected-left-bar')
    if re.search(r"currentNav === item\.id\s*\?\s*'[^']*border-car-accent", side):
        out.append('ui-nav-selected-left-bar')

    # 3) 顶部分段 tab：托盘不画框，选中用实底胶囊
    sub = of('web/src/components/SubTabs.vue')
    if re.search(r'p-1\.5 rounded-2xl bg-car-item border', sub):
        out.append('ui-subtabs-tray-no-frame')
    if re.search(r"modelValue === tab\.id\s*\?\s*'[^']*border-car-accent", sub):
        out.append('ui-subtabs-selected-no-ring')

    # 4) 语音分组卡：外层大卡永不铺金光，格子选中只给左条
    voice = of('web/src/components/VoiceGroupCard.vue')
    if re.search(r"anyActive\s*\?\s*'bg-car-item border-car-accent", voice):
        out.append('ui-voicegroup-card-no-gold-frame')
    # 同一文件里有两处（零件格子 / 模式二选一）带同样文案的状态类，
    # 必须按宽度锚定，否则改回其中一处仍能被另一处掩盖。
    if not re.search(r"item\.active\s*\?\s*'bg-car-item border-solid border-l-\[4px\] border-l-car-accent'", voice):
        out.append('ui-voicegroup-item-left-bar')
    if not re.search(r"m\.active\s*\?\s*'bg-car-item border-solid border-l-\[5px\] border-l-car-accent'", voice):
        out.append('ui-voicegroup-mode-left-bar')

    # 5) 通用功能卡：激活态不得整圈金边，必须左侧竖条
    feat = of('web/src/components/FeatureCard.vue')
    if re.search(r"active\s*\?\s*'border-car-accent'", feat):
        out.append('ui-featurecard-no-gold-frame')
    if 'w-[4px] bg-car-accent' not in feat:
        out.append('ui-featurecard-no-gold-frame')

    # 5.5) 「车载声音」页二级 Tab 只许 5 个且名称固定（v1.8.33.12 合并后锁死）。
    #      换挡与驾驶模式合并、尾门与引擎盖合并、报文观察收进卡脚，是车主明确确认的拓扑。
    #      有人凭「看起来挤」把 Tab 拆回去，就是又要把车门卡挤到屏幕外，必须拦。
    body = of('web/src/views/BodyView.vue')
    tab_block = re.search(r'const voiceTabs = computed\(\(\) => \[(.*?)\]\);', body, re.S)
    if not tab_block:
        out.append('ui-body-voicetabs-missing')
    else:
        block = tab_block.group(1)
        names = re.findall(r"name:\s*['\"]([^'\"]+)['\"]", block)
        statics = [n for n in names if '{' not in n and '$' not in n]
        ids = re.findall(r"id:\s*'([^']+)'", block)
        if len(ids) != 5:
            out.append('ui-body-voicetabs-count')
        for must in ('换挡与驾驶模式', '尾门与引擎盖'):
            if must not in block:
                out.append('ui-body-voicetabs-merged-name')
        for gone in ("id: 'mode'", "id: 'trunk'", "id: 'hood'"):
            if gone in block:
                out.append('ui-body-voicetabs-split-back')
        # 合并后的目标页签必须有对应面板，否则点了空白
        for need in ("activeTab === 'gear'", "activeTab === 'body'"):
            if need not in body:
                out.append('ui-body-panel-missing')
        # 报文观察必须收进引擎盖卡的卡脚，不得再独立成 FeatureCard 大卡
        if 'name="footer"' not in body and '#footer' not in body:
            out.append('ui-body-frame-learning-not-in-card')

    # 6) 严禁对 var(--x) 主题色写透明度变体（bg-car-card/60、border-car-border/60 等）。
    #    Tailwind 只有在颜色是 rgb 三元组时才能拼出 rgb(var(--x) / .6)；本项目颜色全部是
    #    'var(--bg-card)' 字符串，这类类名在产物里根本没有 CSS 规则，元素直接回退到
    #    preflight 的 #e5e7eb —— 昼夜两档都会冒出一批刺眼的冷灰硬线，且改主题色也永远不跟随。
    #    需要层次就用实体 token（bg-car-item / bg-car-card）或左竖条，不要写斜杠。
    if re.search(r'\b(?:bg|text|border|from|to|via)-car-[a-z-]+/\d+', all_web):
        out.append('ui-no-token-opacity-slash')

    return out


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
    # 方向盘未回正提醒 + EPB 未拉起提醒: 用户判定"不重要且不稳定"，已整体删除 (含状态机/服务/桥接/前端/语音资源)。
    # 严禁任何形式复活 —— 出现即视为回归，不接受注释规避。
    all_web = '\n'.join(v for k, v in files.items() if k.startswith('web/src/'))
    retired_safety_guard_pattern = r'SafetySensorStateMachine|checkSteerAngleOnPark|checkEpbNotEngagedOnPDoorOpen|voice_enable_steer_angle_guard|voice_steer_angle_threshold_deg|voice_enable_epb_guard|steer_angle_guard\.mp3|epb_alarm\.mp3|SafetyGuardVoiceSection'
    if re.search(retired_safety_guard_pattern, all_java) or re.search(retired_safety_guard_pattern, all_web):
        failures.append('retired-safety-guard-card')
    # 商城下载弹窗严禁泄露卡兔子主题通道入口（商城仅负责纯粹下载，卡兔子入口严格收敛于 InstallView 独立卡片与三重确认）
    appdetail = files.get('web/src/components/modals/AppDetailModal.vue', '')
    if re.search(r'canUseRabbitTheme|openRabbitGuide|卡兔子主题安装向导|注入兔子时钟', appdetail):
        failures.append('rabbit-theme-no-store-bypass')
    # ---- 切换流畅度契约（车主反馈「很钝、整个画面需要重新自绘画一样」「虚框一闪」）----
    # 根因：亮度调节把 filter:brightness() 挂在 documentElement 上，等于给整个
    # 1760x670 视口套一层离屏缓冲，任何像素变化都要整屏重新离屏绘制 + 亮度重采样。
    # 实测切主题一次：布局 23 次 / 样式重算 35 次；改成合成层遮罩后降到 3 次，耗时降 2/3。
    themes_js = clean(files.get('web/src/theme/themes.js', ''))
    if re.search(r'documentElement\.style\.filter\s*=|root\.style\.filter\s*=', themes_js):
        failures.append('theme-root-filter-banned')
    if not re.search(r'function\s+applyDim\s*\(', themes_js):
        failures.append('theme-dim-layer-missing')
    # 换肤瞬间必须闸住逐属性过渡：102 张卡片同时补间 box-shadow/border-color，
    # 中间那几帧就是车主说的「很虚的框框，然后突然又消失」。
    if not re.search(r'NO_TRANSITION_CLASS|tb-no-transition', themes_js):
        failures.append('theme-switch-no-transition-gate-missing')
    app_vue = files.get('web/src/App.vue', '')
    if not re.search(r'\.tb-no-transition', app_vue):
        failures.append('theme-switch-no-transition-css-missing')
    # 切页签同样要闸住（整块 v-if 面板重建上百个元素）
    subtabs = files.get('web/src/components/SubTabs.vue', '')
    if not re.search(r'suppressTransitionsBriefly', subtabs):
        failures.append('subtabs-no-transition-gate-missing')
    # 启动分段打点：旧日志只有「界面启动完成」一个总耗时，无法定位卡在哪一段
    main_act = files.get(JAVA+'MainActivity.java', '')
    for seg in ('段1', '段2', '段3', '段4'):
        if seg not in main_act:
            failures.append('boot-segment-timing-missing:' + seg)
            break

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
    if not re.search(r'\.card-title\s*\{[^}]*flex-wrap:\s*wrap', mweb):
        failures.append('mobile-web-card-title-wrap')
    ci_health = files.get('scripts/ci_health_check.py', '')
    if not re.search(r'CHECK_STORE_ASSETS', ci_health):
        failures.append('ci-store-probe-decoupled')
    if re.search(r'failures\.append\(\(url,\s*str\(err\)\)\)\s*\n\s*if failures:\s*\n\s*passed\s*=\s*False', ci_health):
        failures.append('ci-store-probe-non-blocking')
    # 日志脱敏必须覆盖真实世界写法（2026-11-12 430884 包实测 287 行残留明文坐标/设备号）：
    #   Location[gps 31.72,118.35]、lat=/lng= 简写、埋点 JSON 的 "latitude":、ecarx_device_id/distinct_id/pcode、MAC。
    # 只留 latitude=/longitude= 一种写法等于没脱敏 —— 车主隐私一旦上传不可撤回。
    utils_src = files.get(JAVA+'utils/SystemUtils.java', '')
    for pat in (r'PATTERN_LOCATION_OBJ', r'PATTERN_LATLNG_SHORT', r'PATTERN_DEVICE_FINGERPRINT',
                r'PATTERN_MAC', r'PATTERN_IMEI', r'PATTERN_TRACK_UID', r'redactGpsCoord\('):
        if not re.search(pat, utils_src):
            failures.append('log-desensitize-coverage:' + pat)
    # 重新抓取日志时必须立刻作废旧包的「已上传」状态，并禁止在上传按钮上继续点开旧弹窗
    logmodal = files.get('web/src/components/modals/LogModal.vue', '')
    try:
        if not re.search(r'logMeta\.value\.isUploaded\s*=\s*false', region(logmodal, 'function exportFullLogs()')):
            failures.append('log-reexport-clears-uploaded')
    except ValueError:
        failures.append('log-reexport-clears-uploaded')
    if len(re.findall(r'@click="openUploadConfirm"[^>]*:disabled="isExporting"', logmodal, re.S)) < 1:
        failures.append('log-upload-button-disabled-while-exporting')
    # 车身信号轨迹必须是「状态变了才记」：430884 包实测 112 条同值心跳把真实开关门挤出了 260 条上限
    recorder = files.get(JAVA+'utils/KeySignalRecorder.java', '')
    try:
        rec_body = region(recorder, 'public static void record(String channel, String value)')
    except ValueError:
        rec_body = ''
        failures.append('signal-recorder-missing')
    if re.search(r'(now\s*-\s*lastAt)\s*<\s*DEDUP_MS', rec_body):
        failures.append('signal-recorder-time-window-dedup-regressed')
    if not re.search(r'last\.equals\(value\)\)\s*\{\s*\n\s*LAST_AT\.put', rec_body):
        failures.append('signal-recorder-change-only-dedup')
    # 日志跨度必须能兼容无 "beginning of" 段标记的内存环形缓冲 dump，否则 summary 恒为 0 被误判成采集故障
    span_body = ''
    try:
        span_body = region(utils_src, 'private static double measureLogSpan(File all)')
    except ValueError:
        pass
    if not re.search(r'!sawMarker\s*\|\|\s*inMain', span_body) or 'minStamp' not in span_body:
        failures.append('log-span-marker-tolerant')
    failures.extend(log_slice_contract(utils_src))
    failures.extend(ui_border_density_contract(files))
    return failures


def log_slice_keywords(src, var):
    """取出某一路切片 filterLogByKeywords 调用的实际关键词（剔除注释）。"""
    m = re.search(var + r'\s*=\s*filterLogByKeywords\([^;]*?\);', src, re.S)
    if not m:
        return None
    body = re.sub(r'//[^\n]*', '', m.group(0))
    return re.findall(r'"([^"]*)"', body[body.index('),') + 2:])


def log_slice_contract(utils_src):
    """切片关键词必须「真值零漏召回 + 不掺噪音」，用 430884 真实包校准过的口径。

    切片是车主自己看的第一现场，漏召回比多几行更致命；但宽词（裸 gear / SCO）会把
    雷达音频、auditd 的 scontext 一起灌进来，让真正的开关门/换挡被淹掉。
    """
    out = []
    rules = {
        # var: (必须包含, 必须不含, 缺失标签)
        'wheelFile': (['onKeyEvent', 'keycode', 'keyCode', 'EcarxVolumeKeyPolicy', 'XCInputService',
                       'AdaptAPI.InputImpl'], [], 'log-slice-wheel-chain'),
        'doorFile': (['91 02 01'], [], 'log-slice-door-serial-frame'),
        'gearFile': (['mGearState', 'gear_position', 'GEAR_'], ['"gear"', '"DRIVEINFO"', '"AvmStateManger"'],
                     'log-slice-gear-precise'),
        'btFile': (['BluetoothSCO', 'SCO_AUDIO_STATE'], ['"SCO"', '"HFP"'], 'log-slice-bt-sco-precise'),
    }
    for var, (need, forbid, label) in rules.items():
        kws = log_slice_keywords(utils_src, var)
        if kws is None:
            out.append(label + ':missing')
            continue
        joined = '|'.join(kws)
        for token in need:
            if token not in kws:
                # GEAR_ 这类前缀锚点用子串判定
                if token.endswith('_') and any(t.startswith(token) for t in kws):
                    continue
                out.append(label + ':need-' + token)
        for token in forbid:
            bare = token.strip('"')
            if bare in kws:
                out.append(label + ':bare-' + bare)
    return out


def load(root):
    root = Path(root)
    paths = {c[1] for c in CONTRACTS} | {'web/src/App.vue', 'web/src/views/FloatingView.vue', 'app/src/main/assets/toolbox_ui.html', 'app/src/main/assets/mobile_web.html', JAVA+'utils/AppLogger.java', 'scripts/publish_r2.py', 'scripts/changelog_builder.py', 'scripts/ci_health_check.py'}
    paths.update(str(p.relative_to(root)) for p in (root/JAVA).rglob('*.java'))
    paths.update(str(p.relative_to(root)) for p in (root/'web/src').rglob('*.vue'))
    paths.update(str(p.relative_to(root)) for p in (root/'web/src').rglob('*.js'))
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
