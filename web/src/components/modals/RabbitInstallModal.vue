<template>
  <ModalWrapper 
    :show="!!store.modals.rabbitInstall" 
    :title="`${targetApp?.name || '目标应用'} · 卡主题安装向导`" 
    badge="专家模式"
    maxWidthClass="max-w-[860px]"
    @close="closeModal('rabbitInstall')"
  >
    <div v-if="targetApp" class="flex flex-col space-y-5">
      <!-- 提示卡片 -->
      <div class="bg-amber-500/10 border border-amber-500/30 rounded-2xl p-5 flex flex-col space-y-2">
        <div class="text-[19px] font-black text-amber-400 flex items-center">
          <span class="mr-2">⚠️</span>
          <span>系统签名伪装与覆盖原理</span>
        </div>
        <p class="text-[15.5px] text-amber-200/90 leading-relaxed font-bold">
          吉利原厂自带应用（如高德地图）具备车机系统级签名保护。在【专家模式】下，系统将自动把目标 APK 深度伪装打包注入进车机『兔子时钟』屏保主题包中，应用时钟后重启车机，即可突破签名限制完成无损覆盖安装。
        </p>
      </div>

      <!-- 标准流程向导卡片 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col space-y-3">
        <div class="text-[17px] font-black text-car-text">标准卡主题操作流程：</div>
        <div class="flex items-center space-x-2 text-[15px] font-bold text-car-sub overflow-x-auto py-1">
          <span class="px-3 py-1.5 rounded-lg bg-car-card border border-car-border text-car-text">1. 启动向导</span>
          <span>➔</span>
          <span class="px-3 py-1.5 rounded-lg bg-car-card border border-car-border text-car-text">2. 主题应用兔子时钟</span>
          <span>➔</span>
          <span class="px-3 py-1.5 rounded-lg bg-car-card border border-car-border text-car-text">3. 一键重启车机</span>
          <span>➔</span>
          <span class="px-3 py-1.5 rounded-lg bg-car-card border border-car-border text-car-accent">4. 开机秒弹覆盖</span>
        </div>
      </div>

      <!-- 操作选择大矩阵 (84px 黄金大磁贴) -->
      <div class="flex space-x-4 pt-2">
        <button 
          @click="startAutoPilot"
          class="flex-1 min-h-[84px] p-4 rounded-2xl bg-car-item border-2 border-car-accent flex flex-col items-center justify-center text-center cursor-pointer hover:border-car-accent shadow-lg ring-2 ring-car-accent/20"
        >
          <span class="text-[20px] font-black text-car-text">一键卡兔子向导 (半自动)</span>
          <span class="text-[14.5px] font-bold text-car-sub mt-1">自动打包注入屏保并唤起主题中心</span>
        </button>

        <button 
          @click="reInjectDirectly"
          class="flex-1 min-h-[84px] p-4 rounded-2xl bg-car-item border border-car-border flex flex-col items-center justify-center text-center cursor-pointer hover:border-car-border-light shadow-sm"
        >
          <span class="text-[20px] font-black text-car-text">仅重新注入伪装主题</span>
          <span class="text-[14.5px] font-bold text-car-sub mt-1">不唤起向导，仅重写底层兔子屏保包</span>
        </button>
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-between w-full">
        <button 
          @click="openFileManager"
          class="h-[54px] px-6 rounded-xl bg-car-item border border-car-border text-car-sub hover:text-car-text font-bold text-[16px] cursor-pointer hover:border-car-border-light transition-all"
        >
          📁 调起原生文件管理
        </button>
        <button 
          @click="closeModal('rabbitInstall')"
          class="h-[54px] px-8 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[17px] cursor-pointer hover:border-car-border-light transition-all"
        >
          取消返回
        </button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { computed } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, showToast } from '../../store';

const targetApp = computed(() => {
  if (store.modals.rabbitInstall && typeof store.modals.rabbitInstall === 'object') {
    return store.modals.rabbitInstall;
  }
  if (store.modals.rabbitInstall) {
    return {
      name: '高德地图车机版 (默认)',
      filename: 'AutoMap_9.5.13_FullFeatures_TrafficLight.apk'
    };
  }
  return null;
});

function startAutoPilot() {
  if (!targetApp.value) return;
  closeModal('rabbitInstall');
  showToast(`正在注入兔子时钟屏保: ${targetApp.value.name}...`);
  bridge.call('startAutoPilotInject', targetApp.value.filename || null);
}

function reInjectDirectly() {
  if (!targetApp.value) return;
  closeModal('rabbitInstall');
  showToast(`正在重写伪装主题: ${targetApp.value.name}...`);
  bridge.call('injectApkDirectly', targetApp.value.filename || null);
}

function openFileManager() {
  closeModal('rabbitInstall');
  bridge.call('openFileManager');
}
</script>
