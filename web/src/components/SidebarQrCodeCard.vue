<template>
  <div
    @click="handleClick"
    class="h-10 px-3 mx-1.5 rounded-xl bg-car-card border border-car-border flex items-center justify-center cursor-pointer hover:bg-car-hover hover:border-car-accent transition active:scale-95 group shadow-sm text-car-text"
    title="点击查看关于与软件信息"
  >
    <span class="text-[14px] font-black tracking-wide group-hover:text-car-accent transition">关于助手</span>
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
