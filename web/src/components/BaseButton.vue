<template>
  <button :class="btnClass" @click="handleClick"><slot /></button>
</template>

<script setup>
// ============================================================================
// BaseButton — 行内按钮统一底座（皮肤 variant 体系 + 全站唯一触感音效接线点）
// 每组 variant 的类串 = 从原手搓按钮逐像素提取，active 态切换不改变像素形态。
// MatrixButton（80px 固定瓦片）保持独立实现，两者共同构成按钮皮肤体系。
// ============================================================================
import { computed } from 'vue';
import { playTouchFeedback } from '../utils/sound';

// 基础骨架（每 variant 的静态部分）
const BASE = {
  // 方控总开关大按钮（74px，标题+副标题纵排）
  master: 'w-full h-[74px] px-4 py-2 rounded-2xl border-2 cursor-pointer transition-all shadow-md flex flex-col items-center justify-center text-center',
  // 图解区 chip 开关（52px，可含 StatusDot）
  chipLg: 'h-[52px] px-5 rounded-2xl border-2 cursor-pointer transition-all shadow-sm whitespace-nowrap flex items-center font-black text-[16px]',
  // 设置行右侧状态胶囊（52px 圆角 xl）
  pill: 'h-[52px] px-5 rounded-xl text-[15.5px] font-black border-2 transition-all whitespace-nowrap cursor-pointer shadow-sm',
  // 中性展开/收起按钮
  ghost: 'h-[52px] px-6 rounded-2xl bg-car-card border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-border-light transition-all shadow-sm whitespace-nowrap flex items-center',
  // 预设小 chip（长按秒数等）
  miniChip: 'px-3 py-1.5 text-[13.5px] font-black rounded-xl border transition-all cursor-pointer shadow-sm',
  // 手势选择 chip（px-3.5）
  gestureChip: 'px-3.5 py-2 rounded-xl text-[15px] font-black cursor-pointer transition-all whitespace-nowrap',
  // 手势选择 chip 大号（WheelGestureCard 顶部切换器，px-5 py-2.5）
  gestureChipLg: 'px-5 py-2.5 rounded-xl text-[15.5px] font-black cursor-pointer transition-all whitespace-nowrap',
  // 金色强调 CTA
  cta: 'h-[52px] px-6 rounded-2xl border-2 border-car-accent bg-car-item text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 shadow-md transition-all flex items-center whitespace-nowrap',
  // 柔和次级按钮（恢复默认）
  ghostSoft: 'h-[52px] px-6 rounded-2xl border-2 border-car-border bg-car-item text-car-sub hover:text-car-text font-black text-[16px] cursor-pointer hover:border-car-border-light shadow-sm transition-all flex items-center whitespace-nowrap',
  // 计划瓦片全宽 toggle（LinkView）
  planToggle: 'w-full h-[52px] px-6 rounded-2xl font-black text-[16px] cursor-pointer transition-all border-2 flex items-center justify-center space-x-2 shadow-sm',
  // 计划瓦片半宽 toggle（BodyView 双按钮 grid）
  planToggleGrid: 'h-[52px] px-4 rounded-2xl font-black text-[16px] cursor-pointer transition-all border-2 flex items-center justify-center space-x-2 shadow-sm',
  // 计划瓦片「细分配置」金色入口
  configCta: 'h-[52px] px-4 rounded-2xl bg-car-item border-2 border-car-accent hover:border-car-accent text-car-accent font-black text-[16px] cursor-pointer shadow-sm transition-all flex items-center justify-center space-x-1'
};

// active=true 的补充分（与基础串拼接）
const ACTIVE = {
  master: 'bg-car-item border-car-accent',
  chipLg: 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/20',
  pill: 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20',
  miniChip: 'bg-car-card border-2 border-car-accent text-car-accent',
  gestureChip: 'bg-car-card border-2 border-car-accent text-car-text shadow-sm',
  gestureChipLg: 'bg-car-card border-2 border-car-accent text-car-text shadow-sm',
  planToggle: 'bg-car-item border-car-accent text-car-text shadow-md',
  planToggleGrid: 'bg-car-item border-car-accent text-car-text shadow-md'
};

// active=false 的补充分
const INACTIVE = {
  master: 'bg-car-card border-car-border hover:border-car-border-light',
  chipLg: 'bg-car-card border-car-border text-car-sub',
  pill: 'bg-car-card border-car-border text-car-sub hover:text-car-text',
  miniChip: 'bg-car-card border border-car-border text-car-sub',
  gestureChip: 'bg-car-card border border-car-border text-car-sub',
  gestureChipLg: 'bg-car-card border border-car-border text-car-sub hover:border-car-border-light',
  planToggle: 'bg-car-item border-car-border text-car-sub hover:text-car-text',
  planToggleGrid: 'bg-car-item border-car-border text-car-sub hover:text-car-text'
};

const props = defineProps({
  variant: { type: String, default: 'ghost' },
  active: { type: Boolean, default: false }
});

const emit = defineEmits(['click']);

const btnClass = computed(() => [
  BASE[props.variant] || BASE.ghost,
  props.active
    ? (ACTIVE[props.variant] || '')
    : (INACTIVE[props.variant] || '')
]);

function handleClick(e) {
  playTouchFeedback();
  emit('click', e);
}
</script>
