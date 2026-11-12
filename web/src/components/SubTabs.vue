<template>
  <!-- 分段容器本身不是控件，只是一个底色托盘：不画边框，把唯一一道金边留给真正选中的那一格。
       原来这里是 border-2 灰框包着 border-2 金框，同一条视觉边界画了两层，车机上显得又厚又杂。 -->
  <div class="flex items-center space-x-2.5 p-1.5 rounded-2xl bg-car-item shrink-0 select-none">
    <button
      v-for="tab in tabs"
      :key="tab.id"
      @click="onSelect(tab.id)"
      :class="[
        'h-[50px] px-5 rounded-xl text-[16px] transition-all flex items-center space-x-2 cursor-pointer select-none outline-none focus:outline-none',
        modelValue === tab.id
          ? 'bg-car-card text-car-accent font-black'
          : 'bg-transparent text-car-sub hover:text-car-text font-bold'
      ]"
    >
      <span v-if="tab.badge" class="w-2 h-2 rounded-full" :class="modelValue === tab.id ? 'bg-car-accent' : 'bg-car-sub/60'"></span>
      <span>{{ tab.name }}</span>
      <span
        v-if="tab.tag"
        :class="[
          'text-[12px] px-2 py-0.5 rounded-lg font-black transition-all',
          modelValue === tab.id
            ? 'bg-car-accent-bg text-car-accent'
            : 'bg-car-card/50 text-car-sub/60 opacity-70'
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
