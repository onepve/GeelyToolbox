<template>
  <button :class="dotClass" :title="title" @click.stop="$emit('click')">
    <svg class="w-3 h-3 fill-current pointer-events-none" viewBox="0 0 16 16">
      <path d="M5.255 5.786a.237.237 0 0 0 .241.247h.825c.138 0 .248-.113.266-.25.09-.656.54-1.134 1.342-1.134.686 0 1.314.343 1.314 1.168 0 .635-.374.927-.965 1.371-.673.489-1.206 1.06-1.168 1.987l.003.217a.25.25 0 0 0 .25.246h.811a.25.25 0 0 0 .25-.25v-.105c0-.718.273-.927 1.01-1.486.609-.463 1.244-.977 1.244-2.056 0-1.511-1.276-2.241-2.673-2.241-1.267 0-2.655.59-2.75 2.286zm1.557 5.763c0 .533.425.927 1.01.927.609 0 1.028-.394 1.028-.927 0-.552-.42-.94-1.029-.94-.584 0-1.009.388-1.009.94z"/>
    </svg>
  </button>
</template>

<script setup>
// ============================================================================
// HelpDot — 圆形问号帮助钮（全站统一紧凑规格）
// 统一参数：20px × 20px 紧凑规格，内置高精度几何 SVG 图标，脱离字体基线偏下干扰
// 布局类由父级经 class 透传；点击自带 .stop，不触发父级卡片事件。
// ============================================================================
import { computed } from 'vue';

const props = defineProps({
  size: { type: String, default: 'sm' },
  title: { type: String, default: '查看功能说明' }
});

defineEmits(['click']);

// 统一所有规格为精致小尺寸（20px x 20px 绝对正中）
const UNIFIED_STYLE = 'w-[20px] h-[20px] rounded-full border border-car-border/80 bg-car-item text-car-accent hover:border-car-accent flex items-center justify-center cursor-pointer shadow-sm transition-transform active:scale-90 shrink-0 p-0 leading-none';

const dotClass = computed(() => UNIFIED_STYLE);
</script>
