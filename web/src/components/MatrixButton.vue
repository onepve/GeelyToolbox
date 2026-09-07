<template>
  <button 
    @click="handleClick"
    :class="[
      'min-h-[84px] py-3.5 px-4 rounded-2xl border-2 transition-all flex flex-col items-center justify-center text-center select-none cursor-pointer backdrop-blur-md',
      active 
        ? 'bg-car-item border-car-accent text-car-text font-black shadow-lg shadow-amber-500/10 ring-2 ring-car-accent/30' 
        : 'bg-car-item border-car-border text-car-text hover:border-car-border-light font-extrabold opacity-85 hover:opacity-100'
    ]"
  >
    <span class="text-[21px] font-black leading-tight text-car-text">{{ title }}</span>
    <span 
      v-if="subtitle" 
      class="text-[15.5px] font-extrabold mt-1.5 leading-snug text-car-sub"
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
