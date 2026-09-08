<template>
  <ModalWrapper 
    :show="store.modals.settings" 
    title="车机守护与系统设置" 
    badge="车规配置"
    maxWidthClass="max-w-[1040px]"
    @close="closeModal('settings')"
  >
    <!-- 12V 蓄电池电压监控大看板 (大大大) -->
    <div class="bg-car-item border border-car-border rounded-3xl p-6 shadow-sm mb-5">
      <div class="flex items-center justify-between">
        <span class="text-[22px] font-black text-car-text">12V 蓄电池精准电压与健康状态</span>
        <span :class="['text-[15px] px-3.5 py-1.5 rounded-full font-black', batteryStatus.badgeClass]">
          {{ batteryStatus.text }}
        </span>
      </div>
      <div class="flex items-baseline mt-2">
        <span class="text-[54px] font-black text-car-text leading-none mr-4">{{ batteryStatus.voltStr }}</span>
        <span class="text-[20px] text-car-sub font-extrabold">{{ batteryStatus.desc }}</span>
      </div>
    </div>

    <!-- 开机自启动设置 -->
    <div class="bg-car-item border border-car-border rounded-3xl p-6 flex items-center justify-between shadow-sm mb-5">
      <div class="flex-1 pr-6 flex flex-col">
        <span class="text-[22px] font-black text-car-text mb-1">车机点火开机自启动控制台</span>
        <span class="text-[17px] text-car-sub font-bold">点火开机自动于后台启动守护服务与悬浮窗</span>
      </div>
      <button 
        @click="toggleAutostart"
        :class="[
          'min-w-[180px] min-h-[72px] px-8 rounded-2xl border-2 font-black text-[20px] cursor-pointer transition-all shrink-0',
          store.settings.autostart 
            ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-md' 
            : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
        ]"
      >
        {{ store.settings.autostart ? '已开启' : '已关闭 (默认)' }}
      </button>
    </div>

    <!-- 桌面常驻悬浮小胶囊 -->
    <div class="bg-car-item border border-car-border rounded-3xl p-6 shadow-sm mb-5">
      <div class="flex items-center justify-between">
        <div class="flex-1 pr-6 flex flex-col">
          <span class="text-[22px] font-black text-car-text mb-1">桌面常驻双模悬浮小胶囊</span>
          <span class="text-[17px] text-car-sub font-bold">切到文件管理或第三方应用时常驻屏幕边缘，秒级唤醒控制台</span>
        </div>
        <button 
          @click="toggleFloatingPill"
          :class="[
            'min-w-[180px] min-h-[72px] px-8 rounded-2xl border-2 font-black text-[20px] cursor-pointer transition-all shrink-0',
            store.settings.floating_pill 
              ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-md' 
              : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
          ]"
        >
          {{ store.settings.floating_pill ? '已开启' : '已关闭 (默认)' }}
        </button>
      </div>

      <!-- 胶囊模式切换大按键 (高度提升至 66px) -->
      <div v-if="store.settings.floating_pill" class="flex mt-4 pt-4 border-t border-car-border">
        <button 
          @click="setFloatingMode('title')"
          :class="[
            'flex-1 min-h-[66px] mr-3 rounded-2xl border-2 font-black text-[18.5px] cursor-pointer transition-all',
            store.settings.floating_mode === 'title'
              ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/30'
              : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
          ]"
        >
          显示名称模式 (吉利智驾)
        </button>
        <button 
          @click="setFloatingMode('code')"
          :class="[
            'flex-1 min-h-[66px] ml-3 rounded-2xl border-2 font-black text-[18.5px] cursor-pointer transition-all',
            store.settings.floating_mode === 'code'
              ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/30'
              : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
          ]"
        >
          实时暗码模式 (#*XXXXXX)
        </button>
      </div>
    </div>

    <!-- 专家模式：卡主题安装功能安全防护 -->
    <div class="bg-car-item border border-car-border rounded-3xl p-6 flex items-center justify-between shadow-sm mb-5">
      <div class="flex-1 pr-6 flex flex-col">
        <span class="text-[22px] font-black text-car-text mb-1">卡主题安装功能安全防护 (专家模式)</span>
        <span class="text-[17px] text-car-sub font-bold">默认锁定专车导航白名单，防伪装常规软件导致屏保死循环变砖</span>
      </div>
      <button 
        @click="toggleExpertMode"
        :class="[
          'min-w-[180px] min-h-[72px] px-8 rounded-2xl border-2 font-black text-[20px] cursor-pointer transition-all shrink-0',
          store.settings.expert_rabbit 
            ? 'bg-rose-500/15 border-2 border-rose-500 text-rose-500 ring-2 ring-rose-500/20' 
            : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
        ]"
      >
        {{ store.settings.expert_rabbit ? '专家模式 (已解锁)' : '已锁定 (推荐)' }}
      </button>
    </div>

    <!-- 5. 自动检测更新与版本升级管理 -->
    <div class="bg-car-item border border-car-border rounded-3xl p-6 flex items-center justify-between shadow-sm mb-5">
      <div class="flex-1 pr-6 flex flex-col">
        <span class="text-[22px] font-black text-car-text mb-1">开机与前台自动检测更新</span>
        <span class="text-[16px] text-car-sub font-bold">
          开启后，有新版本时将自动弹出更新提示卡片；关闭后彻底静默，仅限手动点击下方【检查版本更新】升级
        </span>
      </div>
      <button 
        @click="toggleAutoCheckUpdate"
        :class="[
          'min-h-[72px] px-6 rounded-2xl border-2 font-black text-[18px] cursor-pointer transition-all min-w-[200px] shadow-sm whitespace-nowrap flex items-center justify-center shrink-0',
          autoCheckUpdateEnabled 
            ? 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-md' 
            : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
        ]"
      >
        {{ autoCheckUpdateEnabled ? '已开启 (推荐)' : '已关闭 (彻底静默)' }}
      </button>
    </div>

    <!-- 6. 软件信息与测试身份管理 (方案B：连击 5 次版本号解锁内测) -->
    <div class="bg-car-item border border-car-border rounded-3xl p-6 shadow-sm mb-5">
      <div class="flex items-center justify-between">
        <div class="flex-1 pr-6 flex flex-col">
          <div class="flex items-center">
            <span class="text-[22px] font-black text-car-text mr-3">软件版本与车机身份</span>
            <span 
              @click="handleVersionClick"
              class="text-[13px] px-3 py-1 rounded-full bg-car-card border border-car-border text-car-accent font-extrabold cursor-pointer hover:border-car-accent select-none mr-3"
              title="连续点击 5 次可解锁测试人员专属特权身份"
            >
              v{{ store.deviceInfo.version || '1.4.0' }}
            </span>
            <span v-if="isTester" class="text-[12px] px-2.5 py-0.5 rounded-full bg-rose-500/20 border border-rose-500 text-rose-400 font-black">
              👑 内测特权身份
            </span>
          </div>
          <span class="text-[15px] text-car-sub font-mono font-bold mt-2">
            车机唯一硬件识别码 (UID): {{ deviceUid }}
          </span>
        </div>

        <!-- 测试人员专属通道切换 -->
        <div v-if="isTester" class="shrink-0 flex items-center">
          <button 
            @click="toggleBetaChannel"
            :class="[
              'h-[56px] px-6 rounded-2xl border-2 font-black text-[17px] cursor-pointer transition-all shadow-sm flex items-center',
              useBetaChannel 
                ? 'bg-rose-500/20 border-rose-500 text-rose-400 ring-2 ring-rose-500/30' 
                : 'bg-car-card border-car-border text-car-sub'
            ]"
          >
            <span class="mr-2">⚡</span>
            <span>{{ useBetaChannel ? '测试通道 (Beta)' : '正式通道 (Release)' }}</span>
          </button>
        </div>
      </div>

      <div v-if="isTester" class="mt-4 pt-4 border-t border-car-border/60 flex items-center justify-between">
        <span class="text-[14.5px] text-amber-300 font-bold flex-1 pr-4">
          你已获得开发者/内测车友专属特权：可接收最新 beta-v* 预发布固件与功能实验包。
        </span>
        <button 
          @click="checkBetaUpdateManually"
          class="h-[48px] px-6 rounded-xl bg-car-card border-2 border-amber-500/60 text-amber-400 font-black text-[15.5px] cursor-pointer hover:bg-amber-500/20 transition-all shrink-0"
        >
          检查测试通道更新
        </button>
      </div>
    </div>

    <!-- 底部操作按钮 (车规大触控 66px) -->
    <template #footer>
      <div class="flex items-center justify-between w-full">
        <button 
          @click="openReward"
          class="min-h-[66px] px-8 rounded-2xl bg-car-item border-2 border-car-border text-car-text font-black text-[19px] cursor-pointer hover:border-car-border-light"
        >
          赞赏支持
        </button>
        <button 
          @click="checkUpdate"
          class="min-h-[66px] px-10 bg-car-item border-2 border-car-accent rounded-2xl text-car-text font-black text-[20.5px] cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 shadow-md"
        >
          检查版本更新
        </button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { ref, computed } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, showToast } from '../../store';

const autoCheckUpdateEnabled = ref(localStorage.getItem('geely_auto_check_update') !== 'false');

// 测试人员特权身份管理 (方案B：连击 5 次版本号解锁，并可读硬件唯一 UID)
const isTester = ref(localStorage.getItem('geely_tester_unlocked') === 'true');
const useBetaChannel = ref(localStorage.getItem('geely_use_beta_channel') === 'true');
const deviceUid = ref('读取中...');
let versionClickCount = 0;
let lastVersionClickTime = 0;

function handleVersionClick() {
  const now = Date.now();
  if (now - lastVersionClickTime > 2000) {
    versionClickCount = 0;
  }
  lastVersionClickTime = now;
  versionClickCount++;

  if (versionClickCount >= 5) {
    isTester.value = true;
    localStorage.setItem('geely_tester_unlocked', 'true');
    versionClickCount = 0;
    showToast('🎉 已激活开发者与内测特权身份！');
  }
}

function toggleBetaChannel() {
  useBetaChannel.value = !useBetaChannel.value;
  localStorage.setItem('geely_use_beta_channel', String(useBetaChannel.value));
  showToast(useBetaChannel.value ? '已切换至【测试通道 (Beta)】' : '已恢复为【正式通道 (Release)】');
}

function checkBetaUpdateManually() {
  showToast('正在向测试通道检索最新实验固件...');
  try {
    bridge.call('checkBetaUpdate');
  } catch (e) {
    console.error(e);
  }
}

// 自动读取 UID
try {
  const uid = bridge.call('getDeviceUid');
  if (uid) deviceUid.value = uid;
  else deviceUid.value = 'E02_' + (Math.random().toString(16).substring(2, 10).toUpperCase());
} catch (e) {
  deviceUid.value = 'E02_7814B4FF';
}

function toggleAutoCheckUpdate() {
  autoCheckUpdateEnabled.value = !autoCheckUpdateEnabled.value;
  localStorage.setItem('geely_auto_check_update', String(autoCheckUpdateEnabled.value));
  showToast(autoCheckUpdateEnabled.value ? '已开启【自动检测更新】' : '已关闭【自动检测更新】，不会再弹出更新提示');
}

const batteryStatus = computed(() => {
  const v = store.batteryVoltage;
  if (!v) {
    return {
      voltStr: '-- V',
      text: '实时采集',
      desc: '等待系统传感器上报...',
      badgeClass: 'bg-car-card border border-car-border text-car-sub'
    };
  }
  const voltStr = `${v.toFixed(1)} V`;
  if (v >= 11.8) {
    return {
      voltStr,
      text: '充沛充能 (健康)',
      desc: '发电机运行良好，电压充沛无打火风险',
      badgeClass: 'bg-emerald-500/20 border border-emerald-500 text-emerald-400'
    };
  } else if (v >= 11.5) {
    return {
      voltStr,
      text: '低电警戒 (建议启动)',
      desc: '蓄电池电量偏低，建议尽快发动车辆充能',
      badgeClass: 'bg-amber-500/20 border border-amber-500 text-amber-400'
    };
  } else {
    return {
      voltStr,
      text: '严重亏电 (面临无法点火)',
      desc: '电瓶严重亏电！请立即关闭大灯及功放，准备搭电或点火',
      badgeClass: 'bg-rose-500/20 border border-rose-500 text-rose-400'
    };
  }
});

function toggleAutostart() {
  store.settings.autostart = !store.settings.autostart;
  bridge.call('setSetting', 'autostart', store.settings.autostart);
  showToast(store.settings.autostart ? '已开启点火自启' : '已关闭点火自启');
}

function toggleFloatingPill() {
  store.settings.floating_pill = !store.settings.floating_pill;
  bridge.call('setSetting', 'floating_pill', store.settings.floating_pill);
  showToast(store.settings.floating_pill ? '已开启悬浮胶囊' : '已关闭悬浮胶囊');
}

function setFloatingMode(mode) {
  store.settings.floating_mode = mode;
  bridge.call('setSetting', 'floating_mode', mode);
  showToast(mode === 'title' ? '已设为名称模式' : '已设为暗码模式');
}

function toggleExpertMode() {
  store.settings.expert_rabbit = !store.settings.expert_rabbit;
  bridge.call('setSetting', 'expert_rabbit', store.settings.expert_rabbit);
  showToast(store.settings.expert_rabbit ? '⚡ 专家模式已激活 (请谨慎操作)' : '已锁定安全防护');
}

function checkUpdate() {
  showToast('正在检查最新版本...');
  bridge.call('checkUpdate');
}

function openReward() {
  closeModal('settings');
  store.modals.qrCode = true;
}
</script>
