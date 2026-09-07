<template>
  <header class="h-[64px] min-h-[64px] bg-car-card border-b border-car-border px-5 flex items-center justify-between z-20 select-none transition-colors">
    <!-- 品牌与版本 -->
    <div class="flex items-center gap-2.5">
      <span class="text-[20px] font-black text-car-text tracking-wide">吉利智能座舱中枢</span>
      <span class="text-[12px] px-2 py-0.5 rounded bg-car-item text-car-text font-extrabold border border-car-border">v1.3.8</span>
    </div>

    <!-- 中部状态指示器 -->
    <div class="flex items-center gap-2">
      <div 
        v-for="pill in statusPills" 
        :key="pill.text"
        @click="pill.onClick"
        class="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-car-item border border-car-border text-[13.5px] font-extrabold text-car-text cursor-pointer hover:border-car-border-light transition-all"
      >
        <span class="w-2.5 h-2.5 rounded-full bg-car-accent shadow-[0_0_6px_var(--accent-gold)]"></span>
        <span>{{ pill.text }}</span>
      </div>
    </div>

    <!-- 右侧全局操作 -->
    <div class="flex items-center gap-2">
      <button 
        @click="openDeepTools"
        class="h-[44px] px-4 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[15.5px] cursor-pointer hover:border-car-border-light transition-all"
      >
        ADB 工具
      </button>
      <button 
        @click="toggleTheme"
        class="h-[44px] px-4 rounded-xl bg-car-accent-bg border border-car-accent text-car-accent-text font-black text-[15.5px] cursor-pointer hover:opacity-90 transition-all"
      >
        日夜模式
      </button>
      <button 
        @click="openFastShare"
        class="h-[44px] px-4 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[15.5px] cursor-pointer hover:border-car-border-light transition-all"
      >
        无线快传
      </button>
      <button 
        @click="openSettings"
        class="h-[44px] px-4 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[15.5px] cursor-pointer hover:border-car-border-light transition-all"
      >
        设置
      </button>
      <button 
        @click="exitApp"
        class="h-[44px] px-4 rounded-xl bg-rose-500/15 border border-rose-500/30 text-rose-500 font-black text-[15.5px] cursor-pointer hover:bg-rose-500/25 transition-all"
      >
        退出
      </button>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue';
import { store, bridge, showToast } from '../store';

const statusPills = computed(() => [
  { text: `暗码(+10): ${store.dynamicCode}`, onClick: () => bridge.call('openDialer') },
  { text: `商店: ${store.deviceInfo.appstore_frozen ? '已冻结' : '未冻结'}`, onClick: () => bridge.call('toggleAppstoreFreeze') },
  { text: `白名单: ${store.deviceInfo.whitelist ? '已放行 1' : '未放行 0'}`, onClick: () => bridge.call('toggleWhitelist') },
  { text: `IP: ${store.deviceInfo.car_ip}`, onClick: () => bridge.call('getQrCodeUrl') }
]);

function openDeepTools() {
  bridge.call('openDeepToolsModal');
  showToast('正在打开 ADB 工具...');
}

function toggleTheme() {
  store.isNight = !store.isNight;
  if (!store.isNight) {
    document.documentElement.classList.add('light');
    document.body.classList.add('light');
  } else {
    document.documentElement.classList.remove('light');
    document.body.classList.remove('light');
  }
  showToast(store.isNight ? '已切换为夜间深色模式' : '已切换为日间亮色模式');
}

function openFastShare() {
  bridge.call('getQrCodeUrl');
  showToast('正在打开无线快传...');
}

function openSettings() {
  bridge.call('openSettingsModal');
  showToast('正在打开系统设置...');
}

function exitApp() {
  bridge.call('minimizeApp');
}
</script>
