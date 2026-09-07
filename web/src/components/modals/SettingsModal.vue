<template>
  <ModalWrapper 
    :show="store.modals.settings" 
    title="车机守护与系统设置" 
    badge="M3 车规配置"
    @close="closeModal('settings')"
  >
    <!-- 12V 蓄电池电压监控大看板 -->
    <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col gap-3">
      <div class="flex items-center justify-between">
        <span class="text-[19px] font-black text-car-text">12V 蓄电池精准电压与健康状态</span>
        <span :class="['text-[13px] px-3 py-1 rounded-full font-black', batteryStatus.badgeClass]">
          {{ batteryStatus.text }}
        </span>
      </div>
      <div class="flex items-baseline gap-3">
        <span class="text-[44px] font-black text-car-text leading-none">{{ batteryStatus.voltStr }}</span>
        <span class="text-[18px] text-car-sub font-extrabold">{{ batteryStatus.desc }}</span>
      </div>
    </div>

    <!-- 开机自启动设置 -->
    <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between">
      <div class="flex flex-col gap-1">
        <span class="text-[20px] font-black text-car-text">车机点火开机自启动控制台</span>
        <span class="text-[15.5px] text-car-sub font-bold">点火开机自动于后台启动守护服务与悬浮窗</span>
      </div>
      <button 
        @click="toggleAutostart"
        :class="[
          'min-w-[140px] h-[52px] px-6 rounded-xl border-2 font-black text-[17px] cursor-pointer transition-all',
          store.settings.autostart 
            ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-md' 
            : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
        ]"
      >
        {{ store.settings.autostart ? '已开启' : '已关闭 (默认)' }}
      </button>
    </div>

    <!-- 桌面常驻悬浮小胶囊 -->
    <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col gap-4">
      <div class="flex items-center justify-between">
        <div class="flex flex-col gap-1">
          <span class="text-[20px] font-black text-car-text">桌面常驻双模悬浮小胶囊</span>
          <span class="text-[15.5px] text-car-sub font-bold">切到文件管理或第三方应用时常驻屏幕边缘，秒级唤醒控制台</span>
        </div>
        <button 
          @click="toggleFloatingPill"
          :class="[
            'min-w-[140px] h-[52px] px-6 rounded-xl border-2 font-black text-[17px] cursor-pointer transition-all',
            store.settings.floating_pill 
              ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-md' 
              : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
          ]"
        >
          {{ store.settings.floating_pill ? '已开启' : '已关闭 (默认)' }}
        </button>
      </div>

      <!-- 胶囊模式切换 -->
      <div v-if="store.settings.floating_pill" class="grid grid-cols-2 gap-3 pt-2 border-t border-car-border">
        <button 
          @click="setFloatingMode('title')"
          :class="[
            'h-[50px] rounded-xl border-2 font-black text-[16px] cursor-pointer transition-all',
            store.settings.floating_mode === 'title'
              ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20'
              : 'bg-car-card border-car-border text-car-sub'
          ]"
        >
          显示名称模式 (吉利控制台)
        </button>
        <button 
          @click="setFloatingMode('code')"
          :class="[
            'h-[50px] rounded-xl border-2 font-black text-[16px] cursor-pointer transition-all',
            store.settings.floating_mode === 'code'
              ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20'
              : 'bg-car-card border-car-border text-car-sub'
          ]"
        >
          实时暗码模式 (#*XXXXXX)
        </button>
      </div>
    </div>

    <!-- 专家模式：卡主题安装功能安全防护 -->
    <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between">
      <div class="flex flex-col gap-1">
        <span class="text-[20px] font-black text-car-text">卡主题安装功能安全防护 (专家模式)</span>
        <span class="text-[15.5px] text-car-sub font-bold">默认锁定专车导航白名单，防伪装常规软件导致屏保死循环变砖</span>
      </div>
      <button 
        @click="toggleExpertMode"
        :class="[
          'min-w-[140px] h-[52px] px-6 rounded-xl border-2 font-black text-[17px] cursor-pointer transition-all',
          store.settings.expert_rabbit 
            ? 'bg-rose-500/10 border-rose-500 text-rose-500 ring-2 ring-rose-500/20' 
            : 'bg-car-card border-car-border text-car-sub'
        ]"
      >
        {{ store.settings.expert_rabbit ? '专家模式 (已解锁)' : '已锁定 (推荐)' }}
      </button>
    </div>

    <!-- 底部操作按钮 -->
    <template #footer>
      <div class="flex items-center justify-between w-full">
        <button 
          @click="openReward"
          class="h-[48px] px-6 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-border-light"
        >
          赞赏支持
        </button>
        <div class="flex gap-3">
          <button 
            @click="checkUpdate"
            class="h-[48px] px-6 rounded-xl bg-car-item border-2 border-car-accent text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 shadow-md"
          >
            检查版本更新
          </button>
        </div>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { computed } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, showToast } from '../../store';

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
