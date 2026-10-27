<template>
  <div
    @click="handleClick"
    class="flex items-center justify-between px-5 min-h-[68px] rounded-2xl border-2 border-transparent transition-colors duration-150 cursor-pointer font-black text-[21px] tracking-wide bg-car-item text-car-text hover:bg-car-hover"
    title="点击查看关于与赞助作者信息"
  >
    <div class="flex items-center space-x-2.5 truncate">
      <span class="truncate">关于助手</span>
    </div>
    <span class="text-[14px] px-2.5 py-1 rounded-lg font-extrabold bg-car-card border border-car-border text-car-sub shrink-0 flex items-center space-x-1">
      <span>赞助作者</span>
      <span class="text-[11px]">➔</span>
    </span>
  </div>
</template>

<script setup>
import { openModal } from '../store';
import { playTouchFeedback } from '../utils/sound';

const handleClick = () => {
  playTouchFeedback();
  openModal('about');
};
</script>
