<template>
  <button :class="dotClass" :title="title" @click.stop="$emit('click')">?</button>
</template>

<script setup>
// ============================================================================
// HelpDot — 圆形问号帮助钮（全站统一紧凑规格）
// 统一参数：20px × 20px 紧凑规格，字号 12px，轻量精致且防遮挡
// 布局类由父级经 class 透传；点击自带 .stop，不触发父级卡片事件。
// ============================================================================
import { computed } from 'vue';

const props = defineProps({
  size: { type: String, default: 'sm' },
  title: { type: String, default: '查看功能说明' }
});

defineEmits(['click']);

// 统一所有规格为精致小尺寸（原 34px/50px 统一缩小至 20px）
const UNIFIED_STYLE = 'w-[20px] h-[20px] rounded-full border border-car-border/80 bg-car-item text-car-accent hover:border-car-accent font-black text-[12px] flex items-center justify-center cursor-pointer shadow-sm transition-transform active:scale-90 shrink-0';

const dotClass = computed(() => UNIFIED_STYLE);
</script>
