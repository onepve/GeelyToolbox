<template>
  <ModalWrapper 
    :show="store.modals.about" 
    title="缤越助手 · 关于" 
    :badge="isBeta ? 'BETA 测试版' : '正式版'"
    maxWidthClass="max-w-[820px]"
    @close="closeModal('about')"
  >
    <div class="flex flex-col space-y-5">
      <!-- 品牌、版本与 5 连击彩蛋区域 -->
      <div class="p-6 rounded-3xl bg-car-item border-2 border-car-border flex flex-col items-center justify-center text-center shadow-md relative">
        <div class="flex items-center space-x-3 mb-2">
          <span class="text-[26px] font-black text-car-text">缤越助手 (GeelyToolbox)</span>
          <span 
            @click="handleVersionClick"
            class="text-[14px] px-3 py-1 rounded-full bg-car-card text-car-text font-black border border-car-border cursor-pointer hover:border-car-border-light transition-all shadow-sm select-none"
          >
            v{{ displayVersion }}
          </span>
          <span 
            v-if="isBeta"
            class="text-[11px] px-2.5 py-0.5 rounded-full bg-amber-500/20 text-amber-400 border border-amber-500/40 font-black tracking-wider shadow-sm flex items-center"
          >
            <StatusDot size="xxs" color="warnSoft" class="mr-1.5" :glow="false" pulse />
            BETA
          </span>
        </div>

        <span class="text-[14.5px] text-car-sub font-bold">
          专为吉利汽车 亿咖通 E02 / IHU516 (缤越 COOL / 缤瑞 / 博越) 深度定制的座舱系统
        </span>

        <!-- 车机硬件唯一识别码 (UID) -->
        <div class="mt-4 pt-3 border-t border-car-border/60 w-full flex items-center justify-between px-2">
          <span class="text-[14px] text-car-sub font-bold">车机硬件唯一识别码 (UID):</span>
          <div class="flex items-center space-x-2">
            <span class="text-[15px] text-car-text font-mono font-black bg-car-card px-3 py-1 rounded-lg border border-car-border select-none" :class="uidVisible ? '' : 'tracking-[0.15em]'">
              {{ uidVisible ? deviceUid : uidMask }}
            </span>
            <button
              @click="uidVisible = !uidVisible"
              class="shrink-0 w-9 h-9 rounded-lg bg-car-card border border-car-border text-car-sub hover:text-car-text hover:border-car-border-light cursor-pointer transition-all flex items-center justify-center select-none"
              :aria-label="uidVisible ? '隐藏识别码' : '显示识别码'"
              :title="uidVisible ? '隐藏识别码' : '显示识别码'"
            >
              <svg v-if="uidVisible" viewBox="0 0 24 24" class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                <circle cx="12" cy="12" r="3"></circle>
              </svg>
              <svg v-else viewBox="0 0 24 24" class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path>
                <line x1="1" y1="1" x2="23" y2="23"></line>
              </svg>
            </button>
          </div>
        </div>

        <!-- 真实屏幕参数采集 (实车分辨率/DPI/视口, 消灭写死魔数; 启动自动采集, 关于页纯展示) -->
        <div class="mt-3 pt-3 border-t border-car-border/60 w-full px-2">
          <div class="flex flex-col items-start space-y-1">
            <span class="text-[15.5px] text-car-sub font-bold">屏幕参数 (实车采集):</span>
            <div class="flex flex-wrap items-center space-x-3 space-y-1 text-[14.5px] font-mono text-car-sub">
              <span>分辨率 <b class="text-car-text">{{ screenInfo.size }}</b></span>
              <span>DPI <b class="text-car-text">{{ screenInfo.dpi }}</b></span>
              <span>应用边界 <b class="text-car-text">{{ screenInfo.appBounds }}</b></span>
            </div>
            <span v-if="screenInfo.wm" class="text-[13px] text-car-sub/80 font-mono truncate max-w-full">{{ screenInfo.wm }}</span>
          </div>
        </div>
      </div>

      <!-- 内测特权卡片 (仅在测试通道激活时展示，切回正式通道自动隐藏) -->
      <div v-if="isTester && useBetaChannel" class="p-5 rounded-2xl bg-car-card border border-car-border flex flex-col space-y-3">
        <div class="flex items-center justify-between">
          <div class="flex flex-col">
            <span class="text-[16.5px] font-black text-car-text flex items-center">
              <span class="mr-2">👑</span> 开发者与内测特权身份
            </span>
            <span class="text-[13.5px] text-car-sub font-bold mt-0.5">
              已激活内测特权：支持在测试通道 (Beta) 与正式通道 (Release) 之间自由切换
            </span>
          </div>

          <!-- 通道切换大按钮 -->
          <button 
            @click="toggleBetaChannel"
            :class="[
              'h-[52px] px-5 rounded-xl border-2 font-black text-[15.5px] cursor-pointer transition-all shadow-sm flex items-center',
              useBetaChannel 
                ? 'bg-amber-500/20 border-amber-500 text-amber-400 ring-2 ring-amber-500/30' 
                : 'bg-car-item border-car-border text-car-sub'
            ]"
          >
            <span>{{ useBetaChannel ? '⚡ 测试通道 (Beta)' : '🚀 正式通道 (Release)' }}</span>
          </button>
        </div>

        <!-- 测试通道说明与独立检查按钮 -->
        <div v-if="useBetaChannel" class="pt-3 border-t border-car-border/60 flex items-center justify-between">
          <span class="text-[13.5px] text-amber-300 font-bold">
            当前处于测试通道：将优先接收前沿功能实验固件包与内测修复。
          </span>
          <button 
            @click="checkBetaUpdateManually"
            class="h-[52px] px-6 rounded-2xl bg-car-item border-2 border-car-accent text-car-text font-black text-[16px] cursor-pointer hover:bg-car-card transition-all shrink-0"
          >
            检查测试通道更新
          </button>
        </div>
      </div>

      <!-- 更新策略与开源信息 -->
      <div class="p-5 rounded-2xl bg-car-card border border-car-border flex items-center justify-between">
        <div class="flex flex-col">
          <span class="text-[16px] font-black text-car-text">开机与前台自动检测更新</span>
          <span class="text-[13.5px] text-car-sub font-bold mt-0.5">关闭后前台将不再自动弹出升级提示，完全由你手动控制</span>
        </div>
        <button 
          @click="toggleAutoCheckUpdate"
          :class="[
            'h-[50px] px-6 rounded-xl border-2 font-black text-[15.5px] cursor-pointer transition-all shadow-sm',
            autoCheckUpdateEnabled 
              ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' 
              : 'bg-car-item border-car-border text-car-sub'
          ]"
        >
          {{ autoCheckUpdateEnabled ? '已开启' : '已关闭' }}
        </button>
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-between w-full">
        <button 
          @click="openReward"
          class="min-h-[60px] px-8 rounded-xl bg-car-item border-2 border-car-border text-car-text font-black text-[17.5px] cursor-pointer hover:border-car-border-light shadow-sm"
        >
          💖 赞赏支持
        </button>

        <div class="flex items-center space-x-3">
          <button 
            @click="forceDownloadCurrent"
            class="min-h-[60px] px-6 bg-car-item border-2 border-car-border rounded-xl text-car-sub hover:text-car-text font-black text-[17px] cursor-pointer hover:border-car-border-light shadow-sm"
          >
            强制重新下载
          </button>
          <button 
            @click="checkUpdate"
            class="min-h-[60px] px-8 bg-car-item border-2 border-car-accent rounded-xl text-car-text font-black text-[18.5px] cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 shadow-md"
          >
            检查版本更新
          </button>
          <button 
            @click="closeModal('about')"
            class="min-h-[60px] px-8 bg-car-card border-2 border-car-border rounded-xl text-car-sub hover:text-car-text font-black text-[17.5px] cursor-pointer hover:border-car-border-light shadow-sm"
          >
            关闭
          </button>
        </div>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import StatusDot from '../StatusDot.vue';
import { store, bridge, closeModal, openModal, showToast } from '../../store';

const autoCheckUpdateEnabled = ref(localStorage.getItem('geely_auto_check_update') !== 'false');
const isTester = ref(localStorage.getItem('geely_tester_unlocked') === 'true');
const useBetaChannel = ref(localStorage.getItem('geely_use_beta_channel') === 'true');
const deviceUid = ref('读取中...');
const uidVisible = ref(false);
const uidMask = computed(() => {
  const chars = deviceUid.value.replace(/\s/g, '');
  return chars.replace(/./g, '●').slice(0, Math.max(6, Math.min(chars.length, 12)));
});

let versionClickCount = 0;
let lastVersionClickTime = 0;

const displayVersion = computed(() => store.deviceInfo.version || '1.6.1');
const isBeta = computed(() => {
  const ver = (store.deviceInfo.version || '').toLowerCase();
  return ver.includes('beta');
});

// 真实屏幕参数：屏幕参数是固定值 —— 每次车机上电 Java 侧只采集一次（2026-09-18 用户定案），
// 弹窗响应式跟进显示 + localStorage 缓存秒显（重启也能立刻显示上次参数），严禁反复轮询
const SCREEN_CACHE_KEY = 'geely_screen_info_cache';
const screenInfo = ref({
  size: '读取中...',
  dpi: '读取中...',
  appBounds: '读取中...',
  wm: ''
});

function applyScreenInfo(di) {
  if (!di || !(di.screen_size || di.screen_real_size)) return false;
  const next = {
    size: di.screen_size && di.screen_size !== '未知'
      ? di.screen_size
      : (di.screen_real_size || '未知'),
    dpi: di.screen_density || (di.screen_density_dpi ? di.screen_density_dpi + ' dpi' : '未知'),
    appBounds: di.screen_app_bounds || '未知',
    wm: di.screen_wm || ''
  };
  screenInfo.value = next;
  try { localStorage.setItem(SCREEN_CACHE_KEY, JSON.stringify(next)); } catch (e) {}
  return true;
}

applyScreenInfo(store.deviceInfo || {});
if (screenInfo.value.size === '读取中...') {
  // 启动采集尚未到达或失败：先回显上次上电的缓存（参数固定，直接秒显）
  try {
    const cached = JSON.parse(localStorage.getItem(SCREEN_CACHE_KEY) || 'null');
    if (cached && cached.size) screenInfo.value = cached;
  } catch (e) {}
  // 首次运行且无任何缓存：兜底拉取一次（仍遵循「只抓一次」原则）
  if (!localStorage.getItem(SCREEN_CACHE_KEY)) {
    try {
      const raw = bridge.call('getDeviceInfo');
      if (raw) applyScreenInfo(typeof raw === 'string' ? JSON.parse(raw) : raw);
    } catch (e) {}
  }
}
watch(
  () => [store.deviceInfo.screen_size, store.deviceInfo.screen_real_size, store.deviceInfo.screen_density, store.deviceInfo.screen_app_bounds],
  () => { applyScreenInfo(store.deviceInfo || {}); }
);

try {
  const uid = bridge.call('getDeviceUid');
  if (uid) deviceUid.value = uid;
  else deviceUid.value = 'E02_' + (Math.random().toString(16).substring(2, 10).toUpperCase());
} catch (e) {
  deviceUid.value = 'E02_7814B4FF';
}

function handleVersionClick() {
  const now = Date.now();
  if (now - lastVersionClickTime > 2000) {
    versionClickCount = 0;
  }
  lastVersionClickTime = now;
  versionClickCount++;

  if (versionClickCount >= 5) {
    versionClickCount = 0;
    isTester.value = true;
    localStorage.setItem('geely_tester_unlocked', 'true');
    useBetaChannel.value = true;
    localStorage.setItem('geely_use_beta_channel', 'true');
    showToast('👑 恭喜解锁内测特权！已自动开启测试通道', 'success');
  }
}

function toggleBetaChannel() {
  if (useBetaChannel.value) {
    useBetaChannel.value = false;
    isTester.value = false;
    localStorage.setItem('geely_use_beta_channel', 'false');
    localStorage.setItem('geely_tester_unlocked', 'false');
    showToast('已恢复为【正式通道 (Release)】，特权身份已锁定隐藏');
  } else {
    useBetaChannel.value = true;
    localStorage.setItem('geely_use_beta_channel', 'true');
    showToast('已切换至【测试通道 (Beta)】');
  }
}

function checkBetaUpdateManually() {
  showToast('正在向测试通道检索最新实验包...');
  try {
    bridge.call('checkBetaUpdate');
  } catch (e) {}
}

function checkUpdate() {
  showToast('正在检查最新版本...');
  try {
    if (useBetaChannel.value) {
      bridge.call('checkBetaUpdate');
    } else {
      bridge.call('checkUpdate');
    }
  } catch (e) {}
}

function forceDownloadCurrent() {
  showToast('正在获取云端完整安装包...');
  try {
    bridge.call('forceCheckUpdate', useBetaChannel.value);
  } catch (e) {}
}

function toggleAutoCheckUpdate() {
  autoCheckUpdateEnabled.value = !autoCheckUpdateEnabled.value;
  localStorage.setItem('geely_auto_check_update', String(autoCheckUpdateEnabled.value));
  showToast(autoCheckUpdateEnabled.value ? '已开启【自动检测更新】' : '已关闭【自动检测更新】');
}

function openReward() {
  openModal('reward');
}
</script>
