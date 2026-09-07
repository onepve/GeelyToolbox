<template>
  <div class="h-screen w-screen flex flex-col bg-[#12141A] text-white font-sans overflow-hidden select-none">
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

    <!-- 极简 Toast 提示 -->
    <transition name="fade">
      <div 
        v-if="store.toast.show" 
        class="fixed top-8 left-1/2 -translate-x-1/2 z-50 bg-white text-black px-6 py-2.5 rounded-full font-black text-[16px] shadow-2xl border border-white/20"
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
import { store, bridge } from './store';

onMounted(() => {
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
  background: rgba(255, 255, 255, 0.15);
  border-radius: 4px;
}
</style>
