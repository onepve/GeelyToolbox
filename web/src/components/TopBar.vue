<template>
  <header class="h-[64px] min-h-[64px] bg-car-card border-b border-car-border px-5 flex items-center justify-between z-20 select-none transition-colors">
    <!-- 品牌与版本 -->
    <div class="flex items-center">
      <span class="text-[20px] font-black text-car-text tracking-wide mr-2.5">吉利智驾</span>
      <span class="text-[12px] px-2 py-0.5 rounded bg-car-item text-car-text font-extrabold border border-car-border">v{{ displayVersion }}</span>
    </div>

    <!-- 中部状态指示器 (微胶囊流) -->
    <div class="flex items-center space-x-2">
      <div 
        v-for="pill in statusPills" 
        :key="pill.text"
        @click="pill.onClick"
        class="inline-flex items-center px-3 py-1.5 rounded-full bg-car-item border border-car-border text-[13.5px] font-extrabold text-car-text cursor-pointer hover:border-car-border-light transition-all shadow-sm"
      >
        <span :class="['w-2.5 h-2.5 rounded-full mr-2', pill.dotClass || 'bg-car-accent shadow-[0_0_6px_var(--accent-gold)]']"></span>
        <span>{{ pill.text }}</span>
      </div>
    </div>

    <!-- 右侧全局操作 (精简纯净：日夜图标切换 + 设置 + 退出) -->
    <div class="flex items-center space-x-2.5">
      <!-- 日夜模式图标切换按钮 (太阳/月亮纯 SVG 图标，告别生硬文字) -->
      <button 
        @click="toggleTheme"
        :title="store.isNight ? '当前为夜间模式，点击切换为日间高对比' : '当前为日间模式，点击切换为夜间护眼'"
        class="h-[44px] w-[52px] rounded-xl bg-car-item border border-car-border flex items-center justify-center cursor-pointer hover:border-car-border-light transition-all shadow-sm"
      >
        <!-- 夜间深色：展示精致金月亮 -->
        <svg v-if="store.isNight" class="w-5 h-5 text-amber-400" viewBox="0 0 24 24" fill="currentColor" stroke="none">
          <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path>
        </svg>
        <!-- 日间浅色：展示温暖金太阳 -->
        <svg v-else class="w-5 h-5 text-amber-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="5" fill="currentColor" class="text-amber-500"></circle>
          <line x1="12" y1="1" x2="12" y2="3"></line>
          <line x1="12" y1="21" x2="12" y2="23"></line>
          <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line>
          <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line>
          <line x1="1" y1="12" x2="3" y2="12"></line>
          <line x1="21" y1="12" x2="23" y2="12"></line>
          <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line>
          <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line>
        </svg>
      </button>

      <!-- 关于按键 -->
      <button 
        @click="openAbout"
        class="h-[44px] px-4 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[15.5px] cursor-pointer hover:border-car-border-light transition-all shadow-sm"
      >
        关于
      </button>

      <!-- 退出按键 -->
      <button 
        @click="exitApp"
        class="h-[44px] px-4 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[15.5px] cursor-pointer hover:border-car-border-light hover:text-rose-400 transition-all shadow-sm flex items-center"
      >
        <span class="mr-1 text-[13px] opacity-70">✕</span> 退出
      </button>
    </div>
  </header>
</template>

<script setup>
import { computed, onMounted, onUnmounted } from 'vue';
import { store, bridge, openModal, showToast } from '../store';

let topBarTimer = null;

const displayVersion = computed(() => {
  return store.deviceInfo.version || '1.6.1';
});

const isBeta = computed(() => {
  const ver = (store.deviceInfo.version || '').toLowerCase();
  return ver.includes('beta');
});

const statusPills = computed(() => {
  let v = null;
  if (store.batteryVoltage && store.batteryVoltage > 0) {
    v = store.batteryVoltage;
  } else if (store.deviceInfo.real_battery_volt && store.deviceInfo.real_battery_volt > 0) {
    v = store.deviceInfo.real_battery_volt;
  } else if (store.deviceInfo.battery_volt && store.deviceInfo.battery_volt > 0) {
    v = store.deviceInfo.battery_volt / 10.0;
  }

  let batteryText = '电瓶: 采集中...';
  let dotColor = 'bg-amber-500 shadow-[0_0_6px_#F59E0B] animate-pulse';

  // 车规 12V 蓄电池有效区间校验 (严格限制在 9.0V ~ 16.5V，低于 9V 坚决视为未就绪，杜绝 4.2V 等假数据污染)
  if (v && v >= 9.0 && v <= 16.5) {
    const voltStr = v.toFixed(1);
    const isCharging = v >= 13.4;
    let batteryStatus = '健康充沛';
    dotColor = 'bg-emerald-500 shadow-[0_0_6px_#10B981]';
    if (isCharging) {
      batteryStatus = '充能中';
      dotColor = 'bg-emerald-500 shadow-[0_0_6px_#10B981] animate-pulse';
    } else if (v < 11.5) {
      batteryStatus = '重度亏电';
      dotColor = 'bg-rose-500 shadow-[0_0_6px_#EF4444] animate-pulse';
    } else if (v < 11.8) {
      batteryStatus = '低电警戒';
      dotColor = 'bg-amber-500 shadow-[0_0_6px_#F59E0B]';
    }
    batteryText = `电瓶: ${voltStr}V (${batteryStatus})`;
  }

  return [
    { 
      text: batteryText, 
      dotClass: dotColor,
      onClick: () => openModal('battery') 
    },
    { 
      text: `暗码(+10): ${store.dynamicCode}`, 
      dotClass: 'bg-car-accent shadow-[0_0_6px_var(--accent-gold)]',
      onClick: () => openModal('dialer') 
    },
    { 
      text: `商店: ${store.deviceInfo.appstore_frozen ? '已冻结' : '未冻结'}`, 
      dotClass: store.deviceInfo.appstore_frozen ? 'bg-emerald-500 shadow-[0_0_6px_#10B981]' : 'bg-amber-500 shadow-[0_0_6px_#F59E0B]',
      onClick: () => handleStoreCapsuleClick() 
    },
    { 
      text: `IP: ${store.deviceInfo.car_ip || '127.0.0.1'}`, 
      dotClass: 'bg-sky-500 shadow-[0_0_6px_#0EA5E9]',
      onClick: () => openModal('qrCode') 
    }
  ];
});

function autoPollDeviceInfo() {
  try {
    const raw = bridge.call('getDeviceInfo');
    if (raw) {
      const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
      Object.assign(store.deviceInfo, parsed);
      if (parsed.real_battery_volt && parsed.real_battery_volt >= 9.0 && parsed.real_battery_volt <= 16.5) {
        store.batteryVoltage = parsed.real_battery_volt;
      }
    }
  } catch (e) {}
}

onMounted(() => {
  autoPollDeviceInfo();
  topBarTimer = setInterval(autoPollDeviceInfo, 2500); // 2.5 秒自适应心跳，电瓶与IP变化秒级同步
});

onUnmounted(() => {
  if (topBarTimer) {
    clearInterval(topBarTimer);
    topBarTimer = null;
  }
});

function handleStoreCapsuleClick() {
  if (store.deviceInfo.appstore_frozen) {
    showToast('吉利应用商店当前处于安全冻结状态，白名单已锁定 (๑•̀ㅂ•́)و');
  } else {
    openModal('confirm', {
      title: '一键冻结吉利应用商店',
      desc: '冻结吉利原厂应用商店后，可永久锁定第三方软件白名单，彻底防止高德地图等应用被后台静默卸载。是否立即执行？',
      isDanger: false,
      tip: '提示：后续可随时在系统维护中解冻恢复。',
      onConfirm: () => {
        bridge.call('toggleFreezeAppStore', true);
        showToast('正在执行应用商店安全冻结...');
      }
    });
  }
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
  showToast(store.isNight ? '已切换为夜间护眼模式' : '已切换为日间高对比模式');
}

function openAbout() {
  openModal('about');
}

function exitApp() {
  bridge.call('exitApp');
}
</script>
