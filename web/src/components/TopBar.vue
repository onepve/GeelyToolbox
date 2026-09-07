<template>
  <header class="h-[64px] min-h-[64px] bg-car-card border-b border-car-border px-5 flex items-center justify-between z-20 select-none backdrop-blur-xl transition-colors">
    <!-- 品牌与版本 -->
    <div class="flex items-center gap-2.5">
      <span class="text-[20px] font-black text-car-text tracking-wide">吉利智驾</span>
      <span class="text-[12px] px-2 py-0.5 rounded bg-car-item text-car-text font-extrabold border border-car-border">v{{ store.deviceInfo.version || '1.4.1' }}</span>
    </div>

    <!-- 中部状态指示器 -->
    <div class="flex items-center gap-2">
      <div 
        v-for="pill in statusPills" 
        :key="pill.text"
        @click="pill.onClick"
        class="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-car-item border border-car-border text-[13.5px] font-extrabold text-car-text cursor-pointer hover:border-car-border-light transition-all"
      >
        <span :class="['w-2.5 h-2.5 rounded-full', pill.dotClass || 'bg-car-accent shadow-[0_0_6px_var(--accent-gold)]']"></span>
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
        class="h-[44px] px-4 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[15.5px] cursor-pointer hover:border-car-border-light transition-all"
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
import { store, bridge, openModal, showToast } from '../store';

const statusPills = computed(() => {
  const volt = store.deviceInfo.battery_volt ? (store.deviceInfo.battery_volt / 10).toFixed(1) : '12.6';
  const isCharging = store.deviceInfo.is_charging || (parseFloat(volt) >= 13.4);
  let batteryStatus = '健康充沛';
  let dotColor = 'bg-emerald-500 shadow-[0_0_6px_#10B981]';
  if (isCharging) {
    batteryStatus = '充能中';
    dotColor = 'bg-emerald-500 shadow-[0_0_6px_#10B981]';
  } else if (parseFloat(volt) < 11.5) {
    batteryStatus = '重度亏电';
    dotColor = 'bg-rose-500 shadow-[0_0_6px_#EF4444] animate-pulse';
  } else if (parseFloat(volt) < 11.8) {
    batteryStatus = '低电警戒';
    dotColor = 'bg-amber-500 shadow-[0_0_6px_#F59E0B]';
  }

  return [
    { 
      text: `电瓶: ${volt}V (${batteryStatus})`, 
      dotClass: dotColor,
      onClick: () => openModal('settings') 
    },
    { 
      text: `暗码(+10): ${store.dynamicCode}`, 
      dotClass: 'bg-car-accent shadow-[0_0_6px_var(--accent-gold)]',
      onClick: () => openModal('dialer') 
    },
    { 
      text: `商店: ${store.deviceInfo.appstore_frozen ? '已冻结' : '未冻结'}`, 
      dotClass: store.deviceInfo.appstore_frozen ? 'bg-emerald-500 shadow-[0_0_6px_#10B981]' : 'bg-rose-500 shadow-[0_0_6px_#EF4444]',
      onClick: () => bridge.call('toggleAppstoreFreeze') 
    },
    { 
      text: `白名单: ${store.deviceInfo.whitelist ? '已放行 1' : '未放行 0'}`, 
      dotClass: store.deviceInfo.whitelist ? 'bg-emerald-500 shadow-[0_0_6px_#10B981]' : 'bg-rose-500 shadow-[0_0_6px_#EF4444]',
      onClick: () => bridge.call('toggleWhitelist') 
    },
    { 
      text: `IP: ${store.deviceInfo.car_ip}`, 
      dotClass: 'bg-sky-500 shadow-[0_0_6px_#0EA5E9]',
      onClick: () => openModal('qrCode') 
    }
  ];
});

function openDeepTools() {
  openModal('deepTools');
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
  openModal('qrCode');
}

function openSettings() {
  openModal('settings');
}

function exitApp() {
  bridge.call('minimizeApp');
}
</script>
