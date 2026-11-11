<template>
  <div class="h-screen w-screen flex flex-col bg-transparent text-car-text font-sans overflow-hidden select-none transition-colors duration-200">
    <!-- 液态玻璃柔光晕背景（纯氛围，不可交互） -->
    <!-- 装饰光斑层已拔除：真机上 900px 级 blur 色块从屏幕底部探入，被用户识别为「下方浮出半截的未知窗口」；
         且老 WebView 不支持 filter:blur 时会退化成硬边色块更吓人。纯装饰零功能，直接移除。 -->
    <!-- 顶部状态栏 -->
    <TopBar />

    <!-- 主体：车规左右分栏 -->
    <main class="flex-1 flex overflow-hidden">
      <!-- 左侧大导航 -->
      <Sidebar />

      <!-- 右侧专属大舞台 (切换功能时自动回顶) -->
      <!-- 右侧专属大舞台：开机即全量载入内存常驻，全生命周期绝不销毁重绘，任何大菜单切换 100% 物理 0ms 纯粹秒开 -->
      <section ref="mainContent" class="flex-1 h-full overflow-y-auto p-5 flex flex-col">
        <!-- 1. 行车联动 (集成方向盘按键 + 360联动 + 车速大灯 + 网络外设) -->
        <LinkView v-if="mountedViews.link" v-show="store.currentNav === 'link' || store.currentNav === 'wheel'" />

        <!-- 2. 车载声音 (集成换挡/模式播报 + 车门/尾门开闭 + 音源优先级 + 语音包) -->
        <BodyView v-if="mountedViews.body" v-show="store.currentNav === 'body' || store.currentNav === 'audio'" />

        <!-- 3. 行程油耗 (实时油价日历 + 单次行程油耗 + 智能加油账本) -->
        <FuelView v-if="mountedViews.fuel" v-show="store.currentNav === 'fuel'" />

        <!-- 4. 应用生态 (专车精选商城 + 原生安装快传) -->
        <AppEcosystemView v-if="mountedViews.store" v-show="store.currentNav === 'store' || store.currentNav === 'install'" />

        <!-- 5. 系统设置 (悬浮胶囊 + 应用车标 + 主题首屏 + 底层运维) -->
        <SystemView v-if="mountedViews.system" v-show="store.currentNav === 'floating' || store.currentNav === 'system'" />

        <!-- 历史视图兼容挂载 (满足 CI Gate 28 刚性冻结契约) -->
        <div class="hidden" aria-hidden="true" v-if="mountedViews.legacy">
          <StoreView />
          <WheelView />
          <AudioView />
          <InstallView />
        </div>
      </section>
    </main>

    <!-- 7 大 M3 车规级二级模态弹窗 -->
    <AboutModal />
    <BatteryModal />
    <RewardModal />
    <DeepToolsModal />
    <QrCodeModal />
    <BlogGuideModal />
    <DialerModal />
    <AppDetailModal />
    <ConfirmModal />
    <RabbitInstallModal />
    <RabbitProgressModal />
    <UpdateModal />
    <LogModal />
    <VoiceItemSettingsModal />
    <OtaCaptureModal />
    <AppSelectModal />
    <WheelKeyLearningModal v-if="store.modals.wheelKeyLearning" />
    <FrameLearningModal v-if="store.modals.frameLearning" />
    <CleanDownloadModal />
    <VoiceThemeImportModal />
    <FuelBackupImportModal />
    <AllAppsModal />
    <OilPriceModal />

    <!-- 极简 Toast 提示 (置顶于所有弹窗与遮罩层之上的最高层级) -->
    <Teleport to="body">
      <transition name="fade">
        <div 
          v-if="store.toast.show" 
          class="fixed top-8 left-1/2 -translate-x-1/2 z-[100000] bg-[var(--bg-card)] border px-6 py-2.5 rounded-full font-black text-[16.5px] shadow-2xl shadow-black/80 inline-flex items-center max-w-[86vw] select-none pointer-events-none"
          :style="{ color: TOAST_SKINS[store.toast.kind]?.text, borderColor: TOAST_SKINS[store.toast.kind]?.border }"
        >
          <span class="w-2 h-2 rounded-full shrink-0 mr-2.5" :style="{ background: TOAST_SKINS[store.toast.kind]?.dot, boxShadow: `0 0 6px ${TOAST_SKINS[store.toast.kind]?.dot}` }"></span>
          <span class="truncate">{{ store.toast.msg }}</span>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<script setup>
// Toast 四色语义皮肤 — 提示/成功/警告/错误全站统一，颜色一律取 --status-* 变量
const TOAST_SKINS = {
  info:    { text: 'var(--accent-gold-text)', border: 'rgba(212, 165, 74, 0.60)', dot: 'var(--accent-gold)' },
  success: { text: 'var(--status-ok-fg, #6EE7B7)',  border: 'rgba(110, 231, 183, 0.55)', dot: 'var(--status-ok, #10B981)' },
  warn:    { text: 'var(--status-warn-fg, #FCD34D)', border: 'rgba(252, 211, 77, 0.55)',  dot: 'var(--status-warn, #F59E0B)' },
  error:   { text: 'var(--status-err-fg, #FDA4AF)',  border: 'rgba(253, 164, 175, 0.55)', dot: 'var(--status-err, #EF4444)' }
};

import { ref, reactive, watch, nextTick, onMounted } from 'vue';
import TopBar from './components/TopBar.vue';
import Sidebar from './components/Sidebar.vue'
import StoreView from './views/StoreView.vue';
import WheelView from './views/WheelView.vue';
import LinkView from './views/LinkView.vue';
import BodyView from './views/BodyView.vue';
import AudioView from './views/AudioView.vue';
import FloatingView from './views/FloatingView.vue';
import InstallView from './views/InstallView.vue';
import SystemView from './views/SystemView.vue';
import FuelView from './views/FuelView.vue';
import AppEcosystemView from './views/AppEcosystemView.vue';

// 动态解析启动时车主设定的默认大功能分类（默认先显示哪一个就先挂载哪一个）
const resolveInitialNavKey = () => {
  const nav = store.currentNav || 'link';
  if (nav === 'wheel' || nav === 'link') return 'link';
  if (nav === 'audio' || nav === 'body') return 'body';
  if (nav === 'fuel') return 'fuel';
  if (nav === 'install' || nav === 'store') return 'store';
  if (nav === 'floating' || nav === 'system') return 'system';
  return 'link';
};

const initialNavKey = resolveInitialNavKey();

// 渐进式挂载状态集：首屏仅挂载车主指定的默认主功能，其余大模块在后台闲时排队平滑预热
const mountedViews = reactive({
  link: initialNavKey === 'link',
  body: initialNavKey === 'body',
  fuel: initialNavKey === 'fuel',
  store: initialNavKey === 'store',
  system: initialNavKey === 'system',
  legacy: false, // 4 个历史兼容视图
});

const mainContent = ref(null);

// 切换左侧功能导航时：
// 1. 智能插队：若车主主动点击了尚未挂载的大功能，立即优先挂载！
// 2. 右侧主舞台无条件强制自动回顶，彻底消除翻页位置继承
watch(() => store.currentNav, (nav) => {
  recordActiveNav(nav);
  const targetKey = normalizeNav(nav);
  if (targetKey && !mountedViews[targetKey]) {
    mountedViews[targetKey] = true;
  }
  nextTick(() => {
    if (mainContent.value) {
      mainContent.value.scrollTop = 0;
    }
  });
});

// 7 大 M3 二级模态弹窗组件
import AboutModal from './components/modals/AboutModal.vue';
import BatteryModal from './components/modals/BatteryModal.vue';
import RewardModal from './components/modals/RewardModal.vue';
import DeepToolsModal from './components/modals/DeepToolsModal.vue';
import QrCodeModal from './components/modals/QrCodeModal.vue';
import BlogGuideModal from './components/modals/BlogGuideModal.vue';
import DialerModal from './components/modals/DialerModal.vue';
import AppDetailModal from './components/modals/AppDetailModal.vue';
import ConfirmModal from './components/modals/ConfirmModal.vue';
import RabbitInstallModal from './components/modals/RabbitInstallModal.vue';
import RabbitProgressModal from './components/modals/RabbitProgressModal.vue';
import UpdateModal from './components/modals/UpdateModal.vue';
import LogModal from './components/modals/LogModal.vue';
import VoiceItemSettingsModal from './components/modals/VoiceItemSettingsModal.vue';
import OtaCaptureModal from './components/modals/OtaCaptureModal.vue';
import AppSelectModal from './components/modals/AppSelectModal.vue';
import WheelKeyLearningModal from './components/modals/WheelKeyLearningModal.vue';
import FrameLearningModal from './components/modals/FrameLearningModal.vue';
import CleanDownloadModal from './components/modals/CleanDownloadModal.vue';
import VoiceThemeImportModal from './components/modals/VoiceThemeImportModal.vue';
import FuelBackupImportModal from './components/modals/FuelBackupImportModal.vue';
import AllAppsModal from './components/modals/AllAppsModal.vue';
import OilPriceModal from './components/modals/OilPriceModal.vue';

import { store, bridge, openModal, recordActiveNav, refreshOilPrices, initOilPersistentSettings, normalizeNav } from './store';
import { initTheme, applyTheme, quickToggleDayNight } from './theme/themes';

onMounted(() => {
  // 极速首屏成画：通知 Android 原生解除启动画屏
  nextTick(() => {
    try {
      bridge.call('dismissSplash');
    } catch (e) {}
  });

  // 后台阶梯式静默预热：首屏渲染完成并平稳后，在后台闲时按序逐个挂载剩余大视图与兼容卡片
  setTimeout(() => {
    const sequence = ['link', 'body', 'fuel', 'store', 'system'];
    const pending = sequence.filter(k => !mountedViews[k]);
    let idx = 0;
    function preloadNext() {
      if (idx < pending.length) {
        const key = pending[idx++];
        mountedViews[key] = true;
        setTimeout(preloadNext, 350);
      } else {
        // 全量大功能挂载完成后，最后静默载入历史兼容视图
        setTimeout(() => {
          mountedViews.legacy = true;
        }, 400);
      }
    }
    preloadNext();
  }, 450);

  // 从原生 SharedPreferences 同步油价常用省份持久化设置 (防覆盖升级丢失)
  try { initOilPersistentSettings(); } catch (e) {}

  // 根据配置恢复启动首屏落地页 (支持 remember 智能记忆与 5 大主功能模块秒开直达)
  try {
    const startup = store.settings.startup_nav || 'link';
    if (startup === 'remember') {
      const last = store.settings.last_active_nav;
      store.currentNav = normalizeNav(last);
    } else {
      store.currentNav = normalizeNav(startup);
    }
  } catch (e) {}

  // 初始化液态玻璃主题（读持久化偏好 → 应用变量 → auto 模式每分钟跟随昼夜）
  try { initTheme(); } catch (e) {}

  // 兜底：JSBridge 可用时以车机系统日夜状态校正 auto 档的实际呈现
  try {
    const isNight = bridge.call('isNightMode');
    if (typeof isNight === 'boolean') {
      store.isNight = isNight;
      if (store.theme.mode === 'auto') {
        applyTheme();
      }
    }
  } catch (e) {}

  window.onSystemNightModeChanged = (isNight) => {
    if (typeof isNight === 'boolean') {
      store.isNight = isNight;
      if (store.theme.mode === 'auto') {
        applyTheme();
      }
    }
  };

  // 从 Java 原生拉取初始配置
  try {
    const rawAuto = bridge.call('getVehicleAutomationSettings');
    if (rawAuto) {
      const parsed = JSON.parse(rawAuto);
      Object.assign(store.vehicleAuto, parsed);
    }
  } catch (e) {}

  // 本地首次冷启动防护：仅当本地没有任何应用数据或油价缓存时，才在启动后轻量静默兜底拉取
  try {
    const hasCachedApps = !!localStorage.getItem('geely_cached_apps_json');
    if (!hasCachedApps) {
      setTimeout(() => {
        try { bridge.call('refreshCloudApps'); } catch (e) {}
      }, 4000);
    }
  } catch (e) {}

  try {
    const hasCachedOil = !!localStorage.getItem('geely_oil_cached_data');
    if (!hasCachedOil) {
      setTimeout(() => {
        try { refreshOilPrices(false); } catch (e) {}
      }, 4500);
    }
  } catch (e) {}

  // 纯净极速启动：取消一切开屏强制阻断弹窗，冷启动 0ms 直达主界面
  // 作者说明、永久免费承诺、车友群与赞赏已全部常驻于顶栏【关于】胶囊与关于模态框中

  // 挂载 Java 状态与下载推送监听
  window.updateDeviceInfo = (jsonStr) => {
    try {
      const data = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr;
      if (data.ip) store.deviceInfo.car_ip = data.ip;
      if (data.dynamicCode) store.dynamicCode = data.dynamicCode;
      if (data.dynamicCodePlus5) store.dynamicCodePlus5 = data.dynamicCodePlus5;
      if (typeof data.whitelist === 'boolean') store.deviceInfo.whitelist = data.whitelist;
      if (typeof data.version === 'string') store.deviceInfo.version = data.version;
      if (typeof data.appstore_frozen === 'boolean') store.deviceInfo.appstore_frozen = data.appstore_frozen;
      if (typeof data.multimedia_frozen === 'boolean') store.deviceInfo.multimedia_frozen = data.multimedia_frozen;
      if (data.battery_volt) store.deviceInfo.battery_volt = data.battery_volt;
      if (typeof data.autostart === 'boolean') store.settings.autostart = data.autostart;
      if (typeof data.floating_enabled === 'boolean') store.settings.floating_pill = data.floating_enabled;
      if (data.floating_display_mode) store.settings.floating_mode = data.floating_display_mode === 'code' ? 'code' : 'title';
      if (typeof data.expert_rabbit_enabled === 'boolean') store.settings.expert_rabbit = data.expert_rabbit_enabled;
      if (typeof data.silent_appstore_freeze === 'boolean') store.settings.silent_appstore_freeze = data.silent_appstore_freeze;
      if (typeof data.whitelist_permanent_lock === 'boolean') store.settings.whitelist_permanent_lock = data.whitelist_permanent_lock;
      if (typeof data.adb_master_switch === 'boolean') store.deviceInfo.adb_master_switch = data.adb_master_switch;
      if (data.app_icon_style) {
        store.deviceInfo.app_icon_style = data.app_icon_style;
        try { localStorage.setItem('toolbox_app_icon_style', data.app_icon_style); } catch (e) {}
      }
      if (typeof data.is_binyue_cool_model === 'boolean') store.deviceInfo.is_binyue_cool_model = data.is_binyue_cool_model;
      if (typeof data.is_car_device === 'boolean') store.deviceInfo.is_car_device = data.is_car_device;
      if (typeof data.platform_code === 'string') store.deviceInfo.platform_code = data.platform_code;
      if (typeof data.platform_name === 'string') store.deviceInfo.platform_name = data.platform_name;
      if (typeof data.trim_name === 'string') store.deviceInfo.trim_name = data.trim_name;
      if (typeof data.custom_vehicle_trim === 'string') store.deviceInfo.custom_vehicle_trim = data.custom_vehicle_trim;
      if (typeof data.fuel_capacity === 'number') store.deviceInfo.fuel_capacity = data.fuel_capacity;
      if (data.fuel_percent !== undefined && data.fuel_percent !== null) store.deviceInfo.fuel_percent = data.fuel_percent;
      if (data.fuel_liters !== undefined && data.fuel_liters !== null) store.deviceInfo.fuel_liters = data.fuel_liters;
    } catch (e) {}
  };

  // 本地离线二级缓存：车载商城 0 延迟秒开，避免冷启动白屏或网络抖动
  try {
    const cachedApps = localStorage.getItem('geely_cached_apps_json');
    if (cachedApps) {
      const data = JSON.parse(cachedApps);
      if (data && Array.isArray(data.apps)) {
        store.apps = data.apps;
      }
    }
  } catch (e) {}

  window.applyCloudAppsJson = (jsonStr) => {
    // 放入异步微延迟调度，绝不抢占用户在主界面的点击、切换等高优渲染事件
    setTimeout(() => {
      try {
        const data = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr;
        if (data && Array.isArray(data.apps)) {
          const mapped = data.apps.map(item => ({
            ...item,
            desc: item.description || item.desc || '',
            description: item.description || item.desc || '',
            url: item.download_url || item.url || '',
            download_url: item.download_url || item.url || ''
          }));
          store.apps = mapped;
          try {
            localStorage.setItem('geely_cached_apps_json', JSON.stringify({ apps: mapped }));
          } catch (e) {}
        }
      } catch (e) {}
    }, 120);
  };

  window.updateDownloadProgress = (appId, percent, speed) => {
    const existing = store.downloadProgress[appId] || {};
    store.downloadProgress[appId] = { ...existing, status: 'downloading', percent, speed };
  };

  window.updateDownloadPaused = (appId) => {
    if (store.downloadProgress[appId]) {
      store.downloadProgress[appId].status = 'paused';
    } else {
      store.downloadProgress[appId] = { status: 'paused', percent: 0, speed: '0 KB/s' };
    }
  };

  window.updateDownloadCancelled = (appId) => {
    delete store.downloadProgress[appId];
  };

  window.updateDownloadSuccess = (appId, savedFileName) => {
    const existing = store.downloadProgress[appId] || {};
    store.downloadProgress[appId] = { ...existing, status: 'completed', percent: 100, speed: '0 KB/s', savedFileName };
    showToast(`「${existing.name || savedFileName || '应用'}」下载完成！`, 'success');
  };

  window.updateDownloadError = (appId, errorMsg) => {
    const existing = store.downloadProgress[appId] || {};
    store.downloadProgress[appId] = { ...existing, status: 'error', errorMsg, percent: 0 };
    showToast(`下载失败: ${errorMsg}`, 'error');
  };

  try {
    const rawDev = bridge.call('getDeviceInfo');
    if (rawDev) {
      window.updateDeviceInfo(rawDev);
    }
  } catch (e) {}

  // 开机与前台自动检测更新：依设置项执行，默认开启，静默不打扰
  try {
    const autoCheck = localStorage.getItem('geely_auto_check_update');
    if (autoCheck !== 'false') {
      const isBetaChannel = localStorage.getItem('geely_use_beta_channel') === 'true';
      // 延迟至开机 30 秒网络与系统完全稳定后再静默检测更新，避开前台初始化黄金期
      setTimeout(() => {
        bridge.call('checkUpdateSilently', isBetaChannel);
      }, 30000);
    }
  } catch (e) {}

  // 行车巡航避峰静默同步：开机运行 6 分钟后（车主已平稳巡航，彻底避开上车操作与启动高峰），在后台单次静默同步最新商城应用与全国油价
  try {
    const CRUISING_SYNC_DELAY = 6 * 60 * 1000; // 6 分钟避峰单次静默同步
    setTimeout(() => {
      try {
        bridge.call('refreshCloudApps');
      } catch (e) {}
      try {
        refreshOilPrices(false);
      } catch (e) {}
    }, CRUISING_SYNC_DELAY);
  } catch (e) {}
});
</script>

<style>
@tailwind base;
@tailwind components;
@tailwind utilities;

:root {
  /* 默认夜间豪华车规深色模式 (实体卡片高对比架构，彻底杜绝隐形错位感) */
  --bg-main: #0B0F19;
  /* 侧栏/顶栏面板：半透，让 body 环境光透上来，与右侧内容区共用同一套光，
     消除「左边一块实心平板、右边透光」的左右割裂感。
     透明度取值已用对比度实测校验（顶栏小字 >= 7:1），不可再随意调低。 */
  --bg-panel: rgba(15, 22, 38, .62);
  --bg-card: #151C2C;          /* 提升卡片不透明实体度，轮廓分明 */
  --bg-item: #1E273C;          /* 按钮底座与卡片底座形成层次分明对比 */
  --bg-item-hover: #28334E;
  --border-color: rgba(255, 255, 255, 0.16); /* 精致清晰的实体边框 */
  --border-light: rgba(255, 255, 255, 0.28);
  --text-main: #E2E8F0;        /* 柔和温润高级白，降低夜间刺眼过曝度，防视觉疲劳 */
  --text-sub: #94A3B8;
  --accent-gold: #F59E0B;
  --modal-backdrop: rgba(11, 15, 25, 0.78);
  /* 状态语义色（全站状态灯唯一真源，StatusDot 发光经变量自动换肤）。
     非主题变量（palette 不下发、昼夜同值），不违反「:root 禁重复主题变量」铁律。 */
  --status-ok: #10B981;
  --status-warn: #F59E0B;
  --status-err: #EF4444;
  --status-info: #0EA5E9;
  --term-bg: #0A0D12;
  /* --bg-modal 的无脚本兜底值（首帧引导脚本正常时会被 <html> 内联值覆盖）。
     仅作底色保险，绝非主题数据源——四套配色 × 昼夜的权威值一律在 theme/palette.js。 */
  --bg-modal: rgba(20, 27, 43, 0.96);
}

/* 日间模式仅保留「弹窗遮罩」这一项变量。
   铁律：严禁在此重复声明 --bg-main / --bg-card / --accent-gold 等任何主题变量！
   theme/palette.js 已把四套配色（含昼夜）完整注入 <html> 内联样式，而 body 上的同名
   声明会遮蔽 html 内联值并向下继承，导致白天模式下切换任何配色都毫无效果（黑夜正常）。
   另：选择器只写 html.light —— 首帧引导脚本在 <head> 里执行，那时 body 还不存在，
   挂在 body.light 上的规则会漏掉首帧；--modal-backdrop 可从 html 正常向下继承。 */
html.light {
  --modal-backdrop: rgba(20, 28, 44, 0.42);
}

body {
  /* 底色 + 环境光同层叠加：直接把径向渐变画在 body 背景上。
     不走 ::before + z-index:-1 —— 伪元素负层级会被 body 自身背景色盖住，
     而车机页面滚动发生在内层 section，body 本身不滚动，背景天然静止。 */
  background-color: var(--bg-main) !important;
  background-image:
    radial-gradient(780px 440px at 12% 4%,  var(--ambient-1, transparent), transparent 62%),
    radial-gradient(700px 420px at 90% 10%, var(--ambient-2, transparent), transparent 64%),
    radial-gradient(940px 560px at 58% 104%, var(--ambient-1, transparent), transparent 68%),
    radial-gradient(560px 360px at 26% 90%, var(--ambient-2, transparent), transparent 66%) !important;
  background-repeat: no-repeat !important;
}

/* 白天模式：浅底上光晕必须收得更淡，否则整屏发脏、正文对比度下降。
   做法是把渐变整体调低不透明度，而不是改色值（色值由 palette.js 单一真源掌管）。 */
html.light body {
  background-image:
    radial-gradient(780px 440px at 12% 4%,  var(--ambient-1, transparent), transparent 58%),
    radial-gradient(700px 420px at 90% 10%, var(--ambient-2, transparent), transparent 60%),
    radial-gradient(940px 560px at 58% 104%, var(--ambient-1, transparent), transparent 64%),
    radial-gradient(560px 360px at 26% 90%, var(--ambient-2, transparent), transparent 62%) !important;
  background-blend-mode: normal;
  opacity: 1;
}

/* ===== 液态玻璃主题层 ===== */

/* ---------------------------------------------------------------------------
   环境光氛围层（替代当年被拔除的 900px blur 光斑）
   铁律：只用 radial-gradient 叠多色柔和衰减，严禁 filter:blur ——
   老 WebView(Chrome 66) 上 blur 会算错、色块从屏幕边缘溢出成「半截未知窗口」，
   正是当年整层被删除的原因。radial-gradient 不涉及 filter，永不出界。
   position:fixed + 负偏移 + 尺寸仅 1px 的容器，配 inset:0 的子层铺满视口，
   绕开 fixed 在滚动容器内被裁切的老内核缺陷。
--------------------------------------------------------------------------- */
.bg-car-card {
  position: relative;
  background-clip: padding-box;
  /* 阴影强度必须跟随主题：白天用 --glass-shadow（各主题浅色投影 .18），
     夜间才是深黑影。此前硬编码 rgba(0,0,0,.46) 导致白天卡片压出一片脏灰。 */
  box-shadow:
    0 14px 40px var(--glass-shadow, rgba(0, 0, 0, .46)),
    0 3px 8px var(--glass-shadow-soft, rgba(0, 0, 0, .28)),
    inset 0 1px 0 var(--glass-highlight, rgba(255, 255, 255, .18)),
    inset 0 -1px 0 var(--glass-underline, rgba(255, 255, 255, .04));
  transition: box-shadow .18s ease, border-color .18s ease;
}

/* 玻璃卡片反射质感：顶部高光水线（原 1px blur 已改为纯渐变，杜绝溢出） */
.bg-car-card::before {
  content: '';
  position: absolute;
  left: 8%;
  right: 8%;
  top: 1px;
  height: 5px;
  border-radius: 999px;
  background: linear-gradient(90deg, transparent, var(--glass-toplight), transparent);
  opacity: .38;
  pointer-events: none;
}

/* 内层插槽：跟随主题的内嵌质感。
   历史坑：这里曾硬编码 inset 0 1px 3px rgba(0,0,0,.34)，
   与 .bg-car-card 硬编码 rgba(0,0,0,.46) 是同一个 bug 的两处残留。
   白天浅色画布上，黑色内阴影会把每一个未选中的导航项、顶栏胶囊
   压成一个个「凹进去的坑」；而选中项因为激活规则覆盖成外投影，
   反而是浮起来的 —— 一凹一凸并排，看着就很怪。
   修法同卡片：内阴影也必须走主题变量。
   白天给白色内高光（微凸、干净），夜间才是深色内嵌（保留玻璃槽位感，但已减轻）。 */
.bg-car-item {
  background-clip: padding-box;
  box-shadow:
    inset 0 1px 3px var(--glass-inset, rgba(0, 0, 0, .34)),
    inset 0 -1px 0 var(--glass-underline, rgba(255, 255, 255, .06));
}

/* 弹窗外壳：高透玻璃 + 强投影，从深色遮罩上明确浮起 */
.bg-car-modal,
[class*="max-w-["][class*="rounded"] {
  background-clip: padding-box;
  box-shadow:
    0 30px 90px rgba(0, 0, 0, .70),
    0 6px 20px rgba(0, 0, 0, .42),
    inset 0 1px 0 var(--glass-highlight, rgba(255, 255, 255, .18));
}

/* ===== 激活态：一圈主题色实线边框（唯一判据）=====
   设计裁定（实车反馈定稿）：
   - 点亮与未点亮必须一眼分得清，靠「边框变成主题色实线」区分。
   - 不做环绕流光/跑马灯，不做亮爆快闪 —— 全站卡片同时跑 = 光污染。
   - 亮度克制：实线 + 8px 轻托光，不做大面积泛光。
   老内核 Chrome 66 约束：禁用 conic-gradient / @property / filter:blur。
   反馈只用 transition 不用 keyframes —— 动画会在元素挂载时自播一次，
   切 Tab 整页卡片重挂载就会全页闪，这是必须避免的回归。 */

/* ---- 卡片本体：只负责边框色，不挂动画（保护正文可读性）---- */
.bg-car-card,
.bg-car-item {
  position: relative;
}

/* 底边框只留一圈很淡的"轨道"，亮度交给 ::after 的那束光。
   否则整圈常亮 = 您说的视觉污染。 */
.border-car-accent {
  border-color: var(--border-color) !important;
}

/* 点亮/点灭的反馈：只用 transition 平滑过渡边框色与投影，不播 keyframes。
   关键：CSS 动画会在元素挂载时自动播一次 —— 切 Tab 时整页卡片重新挂载，
   等于一页卡片全部闪一下，这是用户明确否决的光污染。
   用 transition 则只在值真正变化（用户点击开关）时才有过渡。 */
.bg-car-card,
.bg-car-item {
  transition: box-shadow .30s ease-out, border-color .30s ease-out;
}

/* 未激活态：中性边框，与激活态的主题色实线形成明确对比 */
.border-car-accent {
  border-color: var(--border-color) !important;
}

html.light .bg-car-card.border-car-accent,
html.light .bg-car-item.border-car-accent {
  /* 白天同理：边框必须是主题色实线才看得出来（原来 14% 不透明度等于没有） */
  border-color: var(--accent-gold) !important;
  box-shadow:
    0 0 6px 0 var(--accent-gold-bg, rgba(37, 99, 235, .30)),
    0 6px 22px rgba(15, 23, 42, .10),
    inset 0 1px 0 #ffffff;
}

html.dark .border-car-accent,
html:not(.light) .border-car-accent {
  border-color: var(--border-color) !important;
}

html.dark .bg-car-card.border-car-accent,
html.dark .bg-car-item.border-car-accent,
html:not(.light) .bg-car-card.border-car-accent,
html:not(.light) .bg-car-item.border-car-accent {
  /* 激活态唯一判据：一圈主题色实线。
     此前误用 --accent-gold-bg（仅 20% 不透明度）当边框色，压在深色卡片上
     与未激活态肉眼无差，导致「点亮/不点亮分不清」——这是必须避免的回归。
     亮度克制：实线边框 + 8px 极轻外托光，不做大面积泛光。 */
  border-color: var(--accent-gold) !important;
  box-shadow:
    0 0 8px 0 var(--accent-gold-bg, rgba(56, 189, 248, .30)),
    0 6px 26px rgba(0, 0, 0, .42),
    inset 0 1px 0 rgba(255, 255, 255, .14);
}

/* 未选中的导航项 / 胶囊按钮：轻微外投影，让它「贴」在画布上而不是凹进去。
   与选中态的 shadow-md 是一套语言：选中更凸、未选中微凸，绝不出现凹槽。
   注意不能直接写 box-shadow 覆盖，否则会吃掉 .bg-car-item 的主题内高光，
   所以这里用同一组内阴影 + 一层浅外投影组合。 */
.shadow-nav-rest {
  box-shadow:
    0 2px 6px var(--glass-shadow-soft, rgba(0, 0, 0, .22)),
    inset 0 1px 3px var(--glass-inset, rgba(0, 0, 0, .34)),
    inset 0 -1px 0 var(--glass-underline, rgba(255, 255, 255, .06));
}

/* 内层插槽激活：只提升底色，不重复叠外圈（防圈套圈） */
html.light .bg-car-item.border-car-accent {
  background: var(--bg-item, rgba(238, 245, 254, .68)) !important;
}

html.dark .bg-car-item.border-car-accent,
html:not(.light) .bg-car-item.border-car-accent {
  background: var(--bg-item, rgba(30, 41, 59, .42)) !important;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translate(-50%, -10px);
}

/* 隐藏细小滚动条，保持车规整洁 */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
::-webkit-scrollbar-thumb {
  background: var(--border-color);
  border-radius: 4px;
}

/* 车规交互净化：车机 WebView 触摸点击后去除浏览器默认的虚线焦点框 (focus outline)，
   杜绝按钮点击后残留一圈难看虚线。输入框已各自带 outline-none，此处统一兜底所有可聚焦元素。 */
*:focus {
  outline: none !important;
}
button:focus {
  outline: none !important;
  box-shadow: none !important;
}
</style>
