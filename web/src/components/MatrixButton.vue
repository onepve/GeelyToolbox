<template>
  <button 
    @click="handleClick"
    :class="[
      'min-h-[106px] p-3 rounded-2xl border-2 transition-all flex flex-col items-center justify-center text-center select-none cursor-pointer',
      active 
        ? 'bg-[#2A2315] border-[#F59E0B] text-[#FEF3C7] font-black shadow-lg shadow-amber-950/60 ring-2 ring-amber-400/30' 
        : 'bg-[#262A33] border-white/10 text-white/90 hover:border-white/30 font-extrabold'
    ]"
  >
    <span class="text-[23px] font-black leading-tight">{{ title }}</span>
    <span 
      v-if="subtitle" 
      :class="[
        'text-[17.5px] font-extrabold mt-1.5 leading-snug',
        active ? 'text-[#FCD34D]' : 'text-[#94A3B8]'
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
