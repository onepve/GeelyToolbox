<template>
  <div class="h-screen w-screen flex flex-col bg-car-bg text-car-text font-sans overflow-hidden select-none transition-colors duration-200">
    <!-- 顶部状态栏 -->
    <TopBar />

    <!-- 主体：车规左右分栏 -->
    <main class="flex-1 flex overflow-hidden">
      <!-- 左侧大导航 -->
      <Sidebar />

      <!-- 右侧专属大舞台 -->
      <section class="flex-1 h-full overflow-y-auto p-6 flex flex-col gap-6">
        <WheelView v-if="store.currentNav === 'wheel'" />
        <BodyView v-else-if="store.currentNav === 'body'" />
        <AudioView v-else-if="store.currentNav === 'audio'" />
        <StoreView v-else-if="store.currentNav === 'store'" />
        <InstallView v-else-if="store.currentNav === 'install'" />
        <SystemView v-else-if="store.currentNav === 'system'" />
      </section>
    </main>

    <!-- 7 大 M3 车规级二级模态弹窗 -->
    <SettingsModal />
    <DeepToolsModal />
    <QrCodeModal />
    <DialerModal />
    <AppDetailModal />
    <CustomVoiceTextModal />
    <ConfirmModal />

    <!-- 极简 Toast 提示 -->
    <transition name="fade">
      <div 
        v-if="store.toast.show" 
        class="fixed top-8 left-1/2 -translate-x-1/2 z-50 bg-car-card text-car-accent-text border border-car-accent/60 px-6 py-2.5 rounded-full font-black text-[16.5px] shadow-2xl shadow-black/80 ring-2 ring-car-accent/20"
      >
        {{ store.toast.msg }}
      </div>
    </transition>
  </div>
</template>

<script setup>
import { onMounted } from 'vue';
import TopBar from './components/TopBar.vue';
import Sidebar from './components/Sidebar.vue';
import WheelView from './views/WheelView.vue';
import BodyView from './views/BodyView.vue';
import AudioView from './views/AudioView.vue';
import StoreView from './views/StoreView.vue';
import InstallView from './views/InstallView.vue';
import SystemView from './views/SystemView.vue';

// 7 大 M3 二级模态弹窗组件
import SettingsModal from './components/modals/SettingsModal.vue';
import DeepToolsModal from './components/modals/DeepToolsModal.vue';
import QrCodeModal from './components/modals/QrCodeModal.vue';
import DialerModal from './components/modals/DialerModal.vue';
import AppDetailModal from './components/modals/AppDetailModal.vue';
import CustomVoiceTextModal from './components/modals/CustomVoiceTextModal.vue';
import ConfirmModal from './components/modals/ConfirmModal.vue';

import { store, bridge } from './store';

onMounted(() => {
  // 检查系统日夜模式
  try {
    const isNight = bridge.call('isNightMode');
    if (typeof isNight === 'boolean') {
      store.isNight = isNight;
    }
  } catch (e) {}

  if (!store.isNight) {
    document.documentElement.classList.add('light');
    document.body.classList.add('light');
  } else {
    document.documentElement.classList.remove('light');
    document.body.classList.remove('light');
  }

  // 从 Java 原生拉取初始配置
  try {
    const rawAuto = bridge.call('getVehicleAutomationSettings');
    if (rawAuto) {
      const parsed = JSON.parse(rawAuto);
      Object.assign(store.vehicleAuto, parsed);
    }
  } catch (e) {}

  try {
    const rawDev = bridge.call('getDeviceInfo');
    if (rawDev) {
      const parsed = JSON.parse(rawDev);
      Object.assign(store.deviceInfo, parsed);
    }
  } catch (e) {}
});
</script>

<style>
@tailwind base;
@tailwind components;
@tailwind utilities;

:root {
  /* 默认夜间豪华车规深色模式 (Frosted Glass Dark · M3 微透质感) */
  --bg-main: #090C12;
  --bg-card: rgba(21, 26, 38, 0.72);
  --bg-item: rgba(32, 39, 56, 0.6);
  --bg-item-hover: rgba(45, 55, 78, 0.85);
  --border-color: rgba(255, 255, 255, 0.08);
  --border-light: rgba(255, 255, 255, 0.18);
  --text-main: #FFFFFF;
  --text-sub: #94A3B8;
  --accent-gold: #F59E0B;
}

html.light, body.light {
  /* 日间高对比抗眩光亮色模式 (Daylight) */
  --bg-main: #E2E8F0;          /* 柔和浅冷灰底，车规抗过曝，绝非刺眼纯白 */
  --bg-card: #FFFFFF;          /* 纯白卡片，立体鲜明 */
  --bg-item: #F1F5F9;          /* 次级按键底 */
  --bg-item-hover: #E2E8F0;
  --border-color: #CBD5E1;     /* 清晰车规边框 */
  --border-light: #94A3B8;
  --text-main: #0F172A;        /* 强制深墨黑字，强对比抗强光 */
  --text-sub: #475569;         /* 稳重板岩灰 */
  --accent-gold: #D97706;      /* 日间暖阳金/琥珀橙（更深沉抗反光） */
  --accent-gold-bg: #FEF3C7;   /* 日间暖金底座 */
  --accent-gold-text: #78350F; /* 日间超深金棕墨字，WCAG AAA 顶级对比度 */
  --accent-gold-sub: #92400E;  /* 日间深金棕副字 */
}

body {
  background: radial-gradient(circle at 18% 12%, #182438 0%, #0A0D15 55%, #05070B 100%) no-repeat fixed !important;
}

html.light body, body.light {
  background: #E2E8F0 !important;
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
</style>
