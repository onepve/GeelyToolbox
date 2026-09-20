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
      <section ref="mainContent" class="flex-1 h-full overflow-y-auto p-6 flex flex-col space-y-6">
        <KeepAlive :max="8">
          <StoreView v-if="store.currentNav === 'store'" />
          <WheelView v-else-if="store.currentNav === 'wheel'" />
          <LinkView v-else-if="store.currentNav === 'link'" />
          <BodyView v-else-if="store.currentNav === 'body'" />
          <AudioView v-else-if="store.currentNav === 'audio'" />
          <!-- 桌面悬浮已并入系统维护：floating 导航兼容跳转 -->
          <SystemView v-else-if="store.currentNav === 'floating' || store.currentNav === 'system'" />
          <InstallView v-else-if="store.currentNav === 'install'" />
        </KeepAlive>
      </section>
    </main>
    <GeekInstallModal />

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
    <UpdateModal />
    <LogModal />
    <VoiceItemSettingsModal />
    <OtaCaptureModal />
    <AppSelectModal />
    <CleanDownloadModal />
    <VoiceThemeImportModal />
    <AllAppsModal />
    <WelcomeDonateModal />

    <!-- 极简 Toast 提示 -->
    <transition name="fade">
      <div 
        v-if="store.toast.show" 
        class="fixed top-8 left-1/2 -translate-x-1/2 z-50 bg-[var(--bg-card)] border px-6 py-2.5 rounded-full font-black text-[16.5px] shadow-2xl shadow-black/80 inline-flex items-center max-w-[86vw]"
        :style="{ color: TOAST_SKINS[store.toast.kind]?.text, borderColor: TOAST_SKINS[store.toast.kind]?.border }"
      >
        <span class="w-2 h-2 rounded-full shrink-0 mr-2.5" :style="{ background: TOAST_SKINS[store.toast.kind]?.dot, boxShadow: `0 0 6px ${TOAST_SKINS[store.toast.kind]?.dot}` }"></span>
        <span class="truncate">{{ store.toast.msg }}</span>
      </div>
    </transition>
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

import { ref, watch, nextTick, onMounted } from 'vue';
import TopBar from './components/TopBar.vue';
import Sidebar from './components/Sidebar.vue'
import GeekInstallModal from './components/GeekInstallModal.vue';
import StoreView from './views/StoreView.vue';
import WheelView from './views/WheelView.vue';
import LinkView from './views/LinkView.vue';
import BodyView from './views/BodyView.vue';
import AudioView from './views/AudioView.vue';
import FloatingView from './views/FloatingView.vue';
import InstallView from './views/InstallView.vue';
import SystemView from './views/SystemView.vue';

const mainContent = ref(null);

// 切换左侧功能导航时，右侧主舞台无条件强制自动回顶，彻底消除翻页位置继承
watch(() => store.currentNav, () => {
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
import UpdateModal from './components/modals/UpdateModal.vue';
import LogModal from './components/modals/LogModal.vue';
import VoiceItemSettingsModal from './components/modals/VoiceItemSettingsModal.vue';
import OtaCaptureModal from './components/modals/OtaCaptureModal.vue';
import AppSelectModal from './components/modals/AppSelectModal.vue';
import CleanDownloadModal from './components/modals/CleanDownloadModal.vue';
import VoiceThemeImportModal from './components/modals/VoiceThemeImportModal.vue';
import AllAppsModal from './components/modals/AllAppsModal.vue';
import WelcomeDonateModal from './components/modals/WelcomeDonateModal.vue';

import { store, bridge, openModal } from './store';
import { initTheme, quickToggleDayNight } from './theme/themes';

onMounted(() => {
  // 初始化液态玻璃主题（读持久化偏好 → 应用变量 → auto 模式每分钟跟随昼夜）
  try { initTheme(); } catch (e) {}

  // 兜底：JSBridge 可用时以车机系统日夜状态校正 auto 档的实际呈现
  try {
    const isNight = bridge.call('isNightMode');
    if (typeof isNight === 'boolean' && store.theme.mode === 'auto') {
      store.isNight = isNight;
    }
  } catch (e) {}

  // 从 Java 原生拉取初始配置
  try {
    const rawAuto = bridge.call('getVehicleAutomationSettings');
    if (rawAuto) {
      const parsed = JSON.parse(rawAuto);
      Object.assign(store.vehicleAuto, parsed);
    }
  } catch (e) {}

  // 启动即主动从云端异步拉取最新商城应用配置 (纯云端无本地硬编码兜底)
  try {
    bridge.call('refreshCloudApps');
  } catch (e) {}

  // 首次启动检测：等待原生权限就绪后串行拉起作者说明与赞赏弹窗（仅弹一次，持久化到 localStorage）
  try {
    const hasShown = localStorage.getItem('has_shown_welcome_donate');
    if (!hasShown) {
      let triggered = false;
      const triggerWelcome = () => {
        if (triggered) return;
        triggered = true;
        openModal('welcomeDonate');
      };

      // 监听原生层权限授权完毕信号
      window.addEventListener('native-permission-ready', triggerWelcome, { once: true });
      // 容错兜底：若 1800ms 内未收到原生事件（如已永久授权），自动拉起
      setTimeout(triggerWelcome, 1800);
    }
  } catch (e) {}

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
    } catch (e) {}
  };

  window.applyCloudAppsJson = (jsonStr) => {
    try {
      const data = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr;
      if (data && Array.isArray(data.apps)) {
        store.apps = data.apps.map(item => ({
          ...item,
          desc: item.description || item.desc || '',
          description: item.description || item.desc || '',
          url: item.download_url || item.url || '',
          download_url: item.download_url || item.url || ''
        }));
      }
    } catch (e) {}
  };

  window.updateDownloadProgress = (appId, percent, speed) => {
    store.downloadProgress[appId] = { status: 'downloading', percent, speed };
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
    store.downloadProgress[appId] = { status: 'completed', percent: 100, speed: '0 KB/s', savedFileName };
  };

  window.updateDownloadError = (appId, errorMsg) => {
    store.downloadProgress[appId] = { status: 'error', errorMsg, percent: 0 };
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
      setTimeout(() => {
        bridge.call('checkUpdateSilently', isBetaChannel);
      }, 3000);
    }
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
  --bg-panel: #101728;   /* 侧栏/顶栏实底面板，杜绝彩色光晕透垫文字发糊 */
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
  background: var(--bg-main) !important;
}

/* ===== 液态玻璃主题层 ===== */
/* 玻璃卡片反射质感：顶部高光条 + 斜向折射光带（仅大卡片，小按钮不加防杂乱） */
.bg-car-card {
  position: relative;
}
.bg-car-card::before {
  content: '';
  position: absolute;
  left: 8%;
  right: 8%;
  top: 1px;
  height: 5px;
  border-radius: 999px;
  background: linear-gradient(90deg, transparent, var(--glass-toplight), transparent);
  filter: blur(1px);
  opacity: .35;
  pointer-events: none;
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
button:focus,
button:focus-visible {
  outline: none !important;
  box-shadow: none !important;
}
</style>
