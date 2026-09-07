<template>
  <button 
    @click="handleClick"
    :class="[
      'min-h-[106px] p-3 rounded-2xl border-2 transition-all flex flex-col items-center justify-center text-center select-none cursor-pointer',
      active 
        ? 'bg-car-accent-bg border-car-accent text-car-accent-text font-black shadow-lg shadow-amber-950/40 ring-2 ring-car-accent/20' 
        : 'bg-car-item border-car-border text-car-text hover:border-car-border-light font-extrabold'
    ]"
  >
    <span class="text-[23px] font-black leading-tight">{{ title }}</span>
    <span 
      v-if="subtitle" 
      :class="[
        'text-[17.5px] font-extrabold mt-1.5 leading-snug',
        active ? 'text-car-accent-sub' : 'text-car-sub'
      ]"
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
