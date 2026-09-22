<template>
  <header class="h-[64px] min-h-[64px] border-b border-car-border px-5 flex items-center justify-between z-20 select-none transition-colors" :style="{ background: 'var(--bg-panel)' }">
    <!-- 品牌与版本 -->
    <div class="flex items-center">
      <span class="text-[20px] font-black text-car-text tracking-wide mr-2.5">缤越助手</span>
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
        <StatusDot class="mr-2" size="sm" :color="pill.dot ? pill.dot.color : 'accent'" :pulse="pill.dot ? !!pill.dot.pulse : false" />
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
        @click="openModal('about')"
        class="h-[44px] px-4 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[15.5px] cursor-pointer hover:border-car-border-light transition-all shadow-sm whitespace-nowrap"
      >
        关于
      </button>

      <!-- 退出按键 -->
      <button 
        @click="exitApp"
        class="h-[44px] px-4 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[15.5px] cursor-pointer hover:border-car-border-light hover:text-rose-400 transition-all shadow-sm flex items-center whitespace-nowrap"
      >
        <span class="mr-1 text-[13px] opacity-70">✕</span>退出
      </button>
    </div>
  </header>
</template>

<script setup>
import { computed, onMounted, onUnmounted } from 'vue';
import { store, bridge, openModal, showToast } from '../store';
import { getDaysToAdjustment } from '../utils/oilPriceData';
import { quickToggleDayNight } from '../theme/themes';
import { openAppstoreFlow } from '../utils/appstoreFreeze';
import StatusDot from './StatusDot.vue';

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
  let dot = { color: 'warn', pulse: true };

  // 车规 12V 蓄电池有效区间校验 (严格限制在 9.0V ~ 16.5V，低于 9V 坚决视为未就绪，杜绝 4.2V 等假数据污染)
  if (v && v >= 9.0 && v <= 16.5) {
    const voltStr = v.toFixed(1);
    const isCharging = v >= 13.4;
    let batteryStatus = '健康充沛';
    dot = { color: 'ok', pulse: false };
    if (isCharging) {
      batteryStatus = '充能中';
      dot = { color: 'ok', pulse: true };
    } else if (v < 11.5) {
      batteryStatus = '重度亏电';
      dot = { color: 'err', pulse: true };
    } else if (v < 11.8) {
      batteryStatus = '低电警戒';
      dot = { color: 'warn', pulse: false };
    }
    batteryText = `电瓶: ${voltStr}V (${batteryStatus})`;
  }

  return [
    { 
      text: batteryText, 
      dot,
      onClick: () => openModal('battery') 
    },
    {
      text: (() => {
        const prov = store.oilPrice.selectedProvince || '北京';
        const p = store.oilPrice.regionalPrices[prov] || store.oilPrice.regionalPrices['北京'];
        const p92 = (p && p.p92) ? p.p92.toFixed(2) : '8.26';
        const days = getDaysToAdjustment(store.oilPrice.nextAdjustment.date);
        return `油价: ${prov} 92# ¥${p92} (${days}天后调价)`;
      })(),
      dot: { color: 'accent' },
      onClick: () => openModal('oilPrice')
    },
    { 
      text: `暗码(+10): ${store.dynamicCode}`, 
      dot: { color: 'accent' },
      onClick: () => openModal('dialer') 
    },
    { 
      text: `商店: ${store.deviceInfo.appstore_frozen ? '已冻结' : '未冻结'}`, 
      dot: { color: store.deviceInfo.appstore_frozen ? 'ok' : 'warn' },
      onClick: () => handleStoreCapsuleClick() 
    },
    { 
      text: (() => {
        const ip = store.deviceInfo.car_ip || store.deviceInfo.ip || '';
        const isWifi = store.deviceInfo.is_wifi || (ip && (ip.startsWith('192.168.') || ip.startsWith('10.') || ip.startsWith('172.')));
        const isCellular = store.deviceInfo.is_cellular || (ip && !ip.startsWith('127.') && !isWifi);
        if (isWifi && ip && !ip.startsWith('127.')) return `IP: ${ip}`;
        if (isCellular) return 'SIM: 流量在线';
        return '网络: 未连热点';
      })(), 
      dotClass: (() => {
        const ip = store.deviceInfo.car_ip || store.deviceInfo.ip || '';
        const isWifi = store.deviceInfo.is_wifi || (ip && (ip.startsWith('192.168.') || ip.startsWith('10.') || ip.startsWith('172.')));
        const isCellular = store.deviceInfo.is_cellular || (ip && !ip.startsWith('127.') && !isWifi);
        if (isWifi && ip && !ip.startsWith('127.')) return { color: 'ok' };
        if (isCellular) return { color: 'info' };
        return { color: 'off' };
      })(),
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
      if (parsed.ip) {
        store.deviceInfo.ip = parsed.ip;
        store.deviceInfo.car_ip = parsed.ip;
      }
      if (parsed.real_battery_volt && parsed.real_battery_volt >= 9.0 && parsed.real_battery_volt <= 16.5) {
        store.batteryVoltage = parsed.real_battery_volt;
      }
    }
  } catch (e) {}
}

onMounted(() => {
  autoPollDeviceInfo();
  // 实时网络切换主动广播回调：连接/断开热点毫秒级响应
  window.onNetworkChanged = (net) => {
    if (net) {
      if (net.ip) {
        store.deviceInfo.ip = net.ip;
        store.deviceInfo.car_ip = net.ip;
      }
      if (typeof net.isWifiOrLan === 'boolean') store.deviceInfo.is_wifi = net.isWifiOrLan;
      if (typeof net.isCellular === 'boolean') store.deviceInfo.is_cellular = net.isCellular;
      if (net.typeName) store.deviceInfo.net_type = net.typeName;
    }
  };
  // 4 秒自适应心跳：Java 侧已对白名单/包状态做零 shell 缓存，轮询本身不再卡顿
  topBarTimer = setInterval(autoPollDeviceInfo, 4000);
});

onUnmounted(() => {
  if (topBarTimer) {
    clearInterval(topBarTimer);
    topBarTimer = null;
  }
});

function handleStoreCapsuleClick() {
  // 顶部胶囊仅显示状态，点击统一走共享主控（与系统维护/ADB 冻结卡同源同文案）
  openAppstoreFlow();
}

function toggleTheme() {
  quickToggleDayNight();
  showToast(store.isNight ? '已切换为夜间护眼模式' : '已切换为日间高对比模式');
}

function openAbout() {
  openModal('about');
}

function exitApp() {
  bridge.call('exitApp');
}
</script>
