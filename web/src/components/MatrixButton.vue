<template>
  <button 
    @click="handleClick"
    :class="[
      'rounded-2xl border-2 transition-all flex flex-col items-center justify-center text-center select-none cursor-pointer backdrop-blur-md',
      active 
        ? 'bg-car-item border-car-accent text-car-text font-black shadow-lg shadow-amber-500/10 ring-2 ring-car-accent/30' 
        : 'bg-car-item border-car-border text-car-text hover:border-car-border-light font-extrabold opacity-85 hover:opacity-100'
    ]"
    style="min-height: 80px; height: 80px; max-height: 84px; padding: 10px 14px; box-sizing: border-box; display: flex; flex-direction: column; justify-content: center; align-items: center; overflow: hidden;"
  >
    <span style="font-size: 19px; font-weight: 900; line-height: 1.25; color: var(--text-main); display: block; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">
      {{ title }}
    </span>
    <span 
      v-if="subtitle" 
      style="font-size: 13.5px; font-weight: 800; line-height: 1.3; color: var(--text-sub); margin-top: 4px; display: block; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;"
    >
      {{ subtitle }}
    </span>
  </button>
</template>

<script setup>
import { playTouchFeedback } from '../utils/sound';

const props = defineProps({
  title: String,
  subtitle: String,
  active: Boolean
});

const emit = defineEmits(['click']);

function handleClick(e) {
  playTouchFeedback();
  emit('click', e);
}
</script>
