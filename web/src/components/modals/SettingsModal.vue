<template>
  <ModalWrapper 
    :show="store.modals.settings" 
    title="车机守护与系统设置" 
    badge="车规配置"
    maxWidthClass="max-w-[1040px]"
    @close="closeModal('settings')"
  >
    <!-- 12V 蓄电池电压监控大看板 (大大大) -->
    <div class="bg-car-item border border-car-border rounded-3xl p-6 flex flex-col gap-3.5 shadow-sm">
      <div class="flex items-center justify-between">
        <span class="text-[22px] font-black text-car-text">12V 蓄电池精准电压与健康状态</span>
        <span :class="['text-[15px] px-3.5 py-1.5 rounded-full font-black', batteryStatus.badgeClass]">
          {{ batteryStatus.text }}
        </span>
      </div>
      <div class="flex items-baseline gap-4 mt-1">
        <span class="text-[54px] font-black text-car-text leading-none">{{ batteryStatus.voltStr }}</span>
        <span class="text-[20px] text-car-sub font-extrabold">{{ batteryStatus.desc }}</span>
      </div>
    </div>

    <!-- 开机自启动设置 -->
    <div class="bg-car-item border border-car-border rounded-3xl p-6 flex items-center justify-between gap-6 shadow-sm">
      <div class="flex flex-col gap-1.5">
        <span class="text-[22px] font-black text-car-text">车机点火开机自启动控制台</span>
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
    <div class="bg-car-item border border-car-border rounded-3xl p-6 flex flex-col gap-5 shadow-sm">
      <div class="flex items-center justify-between gap-6">
        <div class="flex flex-col gap-1.5">
          <span class="text-[22px] font-black text-car-text">桌面常驻双模悬浮小胶囊</span>
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
      <div v-if="store.settings.floating_pill" class="grid grid-cols-2 gap-4 pt-3 border-t border-car-border">
        <button 
          @click="setFloatingMode('title')"
          :class="[
            'min-h-[66px] rounded-2xl border-2 font-black text-[18.5px] cursor-pointer transition-all',
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
            'min-h-[66px] rounded-2xl border-2 font-black text-[18.5px] cursor-pointer transition-all',
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
    <div class="bg-car-item border border-car-border rounded-3xl p-6 flex items-center justify-between gap-6 shadow-sm">
      <div class="flex flex-col gap-1.5">
        <span class="text-[22px] font-black text-car-text">卡主题安装功能安全防护 (专家模式)</span>
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
    <div class="bg-car-item border border-car-border rounded-3xl p-6 flex items-center justify-between gap-6 shadow-sm">
      <div class="flex flex-col gap-1.5">
        <span class="text-[22px] font-black text-car-text">开机与前台自动检测更新</span>
        <span class="text-[16px] text-car-sub font-bold">
          开启后，有新版本时将自动弹出更新提示卡片；关闭后彻底静默，仅限手动点击下方【检查版本更新】升级
        </span>
      </div>
      <button 
        @click="toggleAutoCheckUpdate"
        :class="[
          'min-h-[72px] px-6 rounded-2xl border-2 font-black text-[18px] cursor-pointer transition-all min-w-[200px] shadow-sm whitespace-nowrap flex items-center justify-center',
          autoCheckUpdateEnabled
            ? 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-md' 
            : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
        ]"
      >
        {{ autoCheckUpdateEnabled ? '已开启 (推荐)' : '已关闭 (彻底静默)' }}
      </button>
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
        <div class="flex gap-4">
          <button 
            @click="checkUpdate"
            class="min-h-[66px] px-10 bg-car-item border-2 border-car-accent rounded-2xl text-car-text font-black text-[20.5px] cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 shadow-md"
          >
            检查版本更新
          </button>
        </div>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { ref, computed } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, showToast } from '../../store';

const autoCheckUpdateEnabled = ref(localStorage.getItem('geely_auto_check_update') !== 'false');

function toggleAutoCheckUpdate() {
  autoCheckUpdateEnabled.value = !autoCheckUpdateEnabled.value;
  localStorage.setItem('geely_auto_check_update', String(autoCheckUpdateEnabled.value));
  if (autoCheckUpdateEnabled.value) {
    // 恢复自动更新时，同时清空之前标记的“不再提醒版本号”，让最新版本能够重新被检测
    localStorage.removeItem('geely_ignored_update_version');
    showToast('已恢复自动检测更新，后续新版本将正常提醒');
  } else {
    showToast('已关闭自动检测更新，前台将不再主动弹窗');
  }
}

const batteryStatus = computed(() => {
  const volt = (store.deviceInfo.battery_volt || 126) / 10;
  const voltStr = volt.toFixed(1) + ' V';
  if (volt >= 11.8) {
    return {
      voltStr,
      text: '健康充沛 · 正常点火',
      desc: volt >= 13.2 ? '发电机正在正常充电充能' : '电量充沛，随时可安全启动',
      badgeClass: 'bg-emerald-500/15 border border-emerald-500/30 text-emerald-500'
    };
  } else if (volt >= 11.5) {
    return {
      voltStr,
      text: '低电警戒 · 建议启动',
      desc: '电量低于 20%，建议点火启动发动机为电瓶充能',
      badgeClass: 'bg-amber-500/15 border border-amber-500/30 text-amber-500'
    };
  } else {
    return {
      voltStr,
      text: '重度亏电 · 面临趴窝',
      desc: '电压过低，极易导致起动机打不着火，请尽快搭电',
      badgeClass: 'bg-rose-500/15 border border-rose-500/30 text-rose-500'
    };
  }
});

function toggleAutostart() {
  const next = !store.settings.autostart;
  store.settings.autostart = next;
  bridge.call('setAutostartEnabled', next);
  showToast(next ? '已开启开机自启' : '已关闭开机自启');
}

function toggleFloatingPill() {
  const next = !store.settings.floating_pill;
  store.settings.floating_pill = next;
  bridge.call('toggleFloatingWindow', next);
  showToast(next ? '已开启悬浮小胶囊' : '已关闭悬浮胶囊');
}

function setFloatingMode(mode) {
  store.settings.floating_mode = mode;
  bridge.call('setFloatingDisplayMode', mode);
  showToast('悬浮窗模式已切换');
}

function toggleExpertMode() {
  const next = !store.settings.expert_rabbit;
  store.settings.expert_rabbit = next;
  bridge.call('setExpertRabbitThemeEnabled', next);
  showToast(next ? '已切换为专家模式 (风险自负)' : '已锁定安全模式 (仅限专车导航)');
}

function openReward() {
  store.modals.reward = true;
}

function checkUpdate() {
  bridge.call('checkUpdate');
  showToast('正在向云端检查更新...');
}
</script>
