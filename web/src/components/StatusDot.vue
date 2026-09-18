<template>
  <span
    class="rounded-full shrink-0 inline-block"
    :class="[sizeClass, colorClass, pulseClass]"
    :style="glowStyle"
  ></span>
</template>

<script setup>
// ============================================================================
// StatusDot — 发光状态灯微组件（全站唯一状态点实现）
// 语义色 token 经 CSS 变量（--status-*，定义于 App.vue :root），发光随变量自动换肤。
// 布局类（mr-*/animate-pulse/shadow-md 等）由父级经 class 透传，本组件只管形状/颜色/发光。
// ============================================================================
import { computed } from 'vue';

const COLORS = {
  ok:       { bg: 'bg-emerald-500', glowVar: '--status-ok' },
  okBright: { bg: 'bg-emerald-400', glowVar: '--status-ok' },
  warn:     { bg: 'bg-amber-500',   glowVar: '--status-warn' },
  warnSoft: { bg: 'bg-amber-400',   glowVar: '--status-warn' },
  err:      { bg: 'bg-rose-500',    glowVar: '--status-err' },
  info:     { bg: 'bg-sky-500',     glowVar: '--status-info' },
  off:      { bg: 'bg-slate-400',   glowVar: null },
  offDim:   { bg: 'bg-slate-500',   glowVar: null },
  accent:   { bg: 'bg-car-accent',  glowVar: '--accent-gold' },
  sub:      { bg: 'bg-car-sub',     glowVar: null },
  border:   { bg: 'bg-car-border',  glowVar: null }
};

const SIZES = {
  xs: 'w-2 h-2',
  sm: 'w-2.5 h-2.5',
  md: 'w-3 h-3',
  lg: 'w-3.5 h-3.5'
};

// 发光半径随尺寸：与历史手搓值一致（小点 6px / 中点 8px / 大点 10px）
const GLOW_PX = { xs: 6, sm: 6, md: 8, lg: 10 };

const props = defineProps({
  color: { type: String, default: 'ok' },
  size: { type: String, default: 'sm' },
  glow: { type: Boolean, default: true },
  pulse: { type: Boolean, default: false },
  glowPx: { type: Number, default: null }
});

const sizeClass = computed(() => SIZES[props.size] || SIZES.sm);
const colorClass = computed(() => (COLORS[props.color] || COLORS.ok).bg);
const pulseClass = computed(() => (props.pulse ? 'animate-pulse' : ''));

const glowStyle = computed(() => {
  if (!props.glow) return null;
  const c = COLORS[props.color] || COLORS.ok;
  if (!c.glowVar) return null;
  const px = props.glowPx || GLOW_PX[props.size] || 6;
  return { boxShadow: `0 0 ${px}px var(${c.glowVar})` };
});
</script>
