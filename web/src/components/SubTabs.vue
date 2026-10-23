<template>
  <div class="flex items-center space-x-2.5 p-1.5 rounded-2xl bg-car-item border-2 border-car-border shrink-0 select-none shadow-sm">
    <button
      v-for="tab in tabs"
      :key="tab.id"
      @click="onSelect(tab.id)"
      :class="[
        'h-[50px] px-5 rounded-xl text-[16px] transition-all flex items-center space-x-2 cursor-pointer border-2 select-none outline-none focus:outline-none',
        modelValue === tab.id
          ? 'bg-car-card border-car-accent text-car-accent font-black shadow-md'
          : 'bg-transparent border-transparent text-car-sub hover:text-car-text font-bold'
      ]"
    >
      <span v-if="tab.badge" class="w-2 h-2 rounded-full" :class="modelValue === tab.id ? 'bg-car-accent' : 'bg-car-sub/60'"></span>
      <span>{{ tab.name }}</span>
      <span
        v-if="tab.tag"
        :class="[
          'text-[12px] px-2 py-0.5 rounded-lg font-black border',
          modelValue === tab.id
            ? 'bg-car-item border-car-accent/40 text-car-accent'
            : 'bg-car-card border-car-border text-car-sub'
        ]"
      >
        {{ tab.tag }}
      </span>
    </button>
  </div>
</template>

<script setup>
import { playTouchFeedback } from '../utils/sound';

const props = defineProps({
  tabs: {
    type: Array,
    required: true
  },
  modelValue: {
    type: String,
    required: true
  }
});

const emit = defineEmits(['update:modelValue']);

function onSelect(id) {
  if (props.modelValue === id) return;
  playTouchFeedback();
  emit('update:modelValue', id);
}
</script>
