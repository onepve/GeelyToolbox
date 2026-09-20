<template>
  <div
    @click="handleClick"
    class="h-9 px-3 mx-1.5 rounded-xl bg-car-card border border-car-border flex items-center justify-center space-x-2 cursor-pointer hover:bg-car-hover hover:border-car-accent transition active:scale-95 group shadow-sm text-car-text"
    title="点击查看使用教程"
  >
    <span class="text-sm">📖</span>
    <span class="text-[13px] font-bold tracking-wide group-hover:text-car-accent transition">使用教程</span>
  </div>
</template>

<script setup>
import { openModal } from '../store';
import { playTouchFeedback } from '../utils/sound';

const handleClick = () => {
  playTouchFeedback();
  openModal('blogGuide');
};
</script>
