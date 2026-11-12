<template>
  <!-- 激活态不再把整圈边框染成金色：金色只留左侧一道 4px 竖条 + 状态点。
       整圈金边会让一屏里同时亮七八个金框，反而看不出哪个是关键。 -->
  <div :class="[
    'rounded-3xl border-2 shadow-2xl transition-all bg-car-card flex flex-col justify-between relative overflow-hidden',
    dense ? 'p-3.5 min-h-0' : 'p-5 min-h-[170px]',
    'border-car-border'
  ]">
    <span
      v-if="active"
      aria-hidden="true"
      class="absolute left-0 top-0 bottom-0 w-[4px] bg-car-accent pointer-events-none"
    ></span>
    <!-- 头部标题与说明：内嵌在卡片顶端 (仅在声明 title 或 header slot 时渲染) -->
    <div v-if="title || $slots.header" :class="['flex flex-col border-b border-car-border', dense ? 'pb-2 mb-2.5' : 'pb-4 mb-4']">
      <div class="flex items-center justify-between mb-1">
        <slot name="header">
          <div class="flex items-center space-x-2.5">
            <StatusDot v-if="isSwitch" size="sm" :color="active ? 'ok' : 'off'" :glow-px="active ? 6 : 0" />
            <div :class="['font-black text-car-text tracking-wide', dense ? 'text-[17px]' : 'text-[21px]']">
              {{ title }}
            </div>
            <!-- 问号说明按钮 (若提供 helpText) -->
            <HelpDot
              v-if="helpText"
              @click="showHelp"
              title="查看本功能详细说明与核心原理"
            />
          </div>
        </slot>
        <slot name="badge" />
      </div>
      <div v-if="(desc || $slots.desc) && !helpText" class="text-[15.5px] text-car-sub font-bold leading-relaxed">
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
import StatusDot from './StatusDot.vue';
import HelpDot from './HelpDot.vue';

const props = defineProps({
  title: String,
  desc: String,
  helpText: String,
  helpTitle: String,
  helpTip: String,
  active: { type: Boolean, default: false },
  isSwitch: { type: Boolean, default: false },
  /**
   * 紧凑模式：用于一屏要塞多张卡的区域（如方向盘按键 7 张卡片）。
   * 只压缩留白与标题字号，绝不缩小内部可点区域（下拉仍 80px 高），
   * 保证车规点按手感不退化。
   */
  dense: { type: Boolean, default: false }
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
