<template>
  <div
    @click="handleClick"
    class="flex items-center justify-between px-5 min-h-[64px] rounded-2xl border-2 transition-all duration-150 cursor-pointer font-black text-[20px] tracking-wide bg-car-item border-car-border/70 hover:border-car-border text-car-text hover:bg-car-hover shadow-sm active:scale-[0.98]"
    title="点击查看关于与软件信息"
  >
    <div class="flex items-center space-x-2.5 truncate">
      <span class="truncate">关于助手</span>
    </div>
    <span class="text-[13px] px-2.5 py-1 rounded-lg font-black bg-car-card border border-car-border text-car-sub shrink-0">
      v{{ store.deviceInfo.version || '1.7.82' }}
    </span>
  </div>
</template>

<script setup>
import { store, openModal } from '../store';
import { playTouchFeedback } from '../utils/sound';

const handleClick = () => {
  playTouchFeedback();
  openModal('about');
};
</script>
