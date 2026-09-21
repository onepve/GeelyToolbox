<template>
  <div class="rounded-3xl border-2 border-car-border hover:border-car-border-light bg-car-card p-6 shadow-xl h-full min-h-[260px] flex flex-col justify-between transition-all duration-200">
    <!-- 头部：编号标题 + 语义标签 + 问号帮助 -->
    <div class="flex items-center justify-between shrink-0 mb-2">
      <div class="flex items-center space-x-3">
        <span class="text-[21px] font-black text-car-text tracking-wide">{{ title }}</span>
        <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent shrink-0">{{ tag }}</span>
        <HelpDot
          v-if="helpText"
          :size="helpSize"
          @click="$emit('help')"
        />
      </div>
    </div>

    <!-- 两段式流向说明 -->
    <div class="flex-1 min-w-0 flex flex-col justify-center space-y-1.5 py-3 text-[15px] leading-relaxed">
      <div class="text-car-sub font-bold">{{ flowSub }}</div>
      <div class="text-car-text font-bold">{{ flowMain }}</div>
    </div>

    <!-- 底部操作区（toggle / 配置按钮由调用方给） -->
    <div class="pt-4 border-t border-car-border/60 mt-auto shrink-0">
      <slot name="footer" />
    </div>
  </div>
</template>

<script setup>
// ============================================================================
// 功能卡片的通用外观与按钮规格
// ============================================================================
import HelpDot from './HelpDot.vue';

defineProps({
  title: String,
  tag: String,
  flowSub: String,
  flowMain: String,
  helpText: String,
  // lg = BodyView 50px 大问号；sm = LinkView 34px 标准问号
  helpSize: { type: String, default: 'sm' }
});

defineEmits(['help']);
</script>
