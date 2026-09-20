<template>
  <button :class="dotClass" :title="title" @click.stop="$emit('click')">?</button>
</template>

<script setup>
// ============================================================================
// HelpDot — 圆形问号帮助钮（全站唯一实现）
// sm = 34px 标准档（卡片标题行）；lg = 50px 大档（PlanCard 语音计划瓦片）。
// 布局类由父级经 class 透传；点击自带 .stop，不触发卡片级事件。
// ============================================================================
import { computed } from 'vue';

const props = defineProps({
  size: { type: String, default: 'sm' },
  title: { type: String, default: '查看功能指南' }
});

defineEmits(['click']);

const STYLES = {
  sm: 'w-[34px] h-[34px] rounded-full border-2 border-car-border bg-car-item text-car-accent hover:border-car-accent font-black text-[15px] flex items-center justify-center cursor-pointer shadow-sm transition-transform active:scale-95 shrink-0',
  lg: 'w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-accent hover:border-car-accent font-black text-[18px] flex items-center justify-center cursor-pointer shadow-sm transition-transform active:scale-95 shrink-0'
};

const dotClass = computed(() => STYLES[props.size] || STYLES.sm);
</script>
