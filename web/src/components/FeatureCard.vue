<template>
  <div class="rounded-3xl border-2 border-car-border p-6 shadow-2xl transition-all bg-car-card mb-5">
    <!-- 头部标题与说明：内嵌在卡片顶端 -->
    <div class="flex flex-col pb-4 mb-4 border-b border-car-border/60">
      <div class="flex items-center justify-between mb-1">
        <slot name="header">
          <div class="flex items-center space-x-2.5">
            <div class="text-[21px] font-black text-car-text tracking-wide">
              {{ title }}
            </div>
            <!-- 问号说明按钮 (若提供 helpText) -->
            <button
              v-if="helpText"
              @click="showHelp"
              class="w-7 h-7 rounded-full bg-car-item border border-car-border text-car-accent hover:border-car-accent hover:bg-car-card font-black text-[15px] flex items-center justify-center cursor-pointer transition-all shadow-sm shrink-0 select-none"
              title="查看本功能详细说明与核心原理"
            >
              ?
            </button>
          </div>
        </slot>
        <slot name="badge" />
      </div>
      <div v-if="desc || $slots.desc" class="text-[15.5px] text-car-sub font-bold leading-relaxed">
        <slot name="desc">{{ desc }}</slot>
      </div>
    </div>

    <!-- 内部操作内容区 -->
    <div class="w-full">
      <slot />
    </div>
  </div>
</template>

<script setup>
import { openModal } from '../store';

const props = defineProps({
  title: String,
  desc: String,
  helpText: String,
  helpTitle: String,
  helpTip: String
});

function showHelp() {
  openModal('confirm', {
    title: props.helpTitle || `【功能指南】${props.title}`,
    desc: props.helpText,
    tip: props.helpTip || '注：本功能带有独立状态防抖与安全保护，可按需随时开关。',
    showCancel: false,
    confirmText: '我知道了'
  });
}
</script>
