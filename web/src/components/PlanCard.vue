<template>
  <div class="rounded-3xl border-2 border-car-border hover:border-car-border-light bg-car-card p-6 shadow-xl h-full min-h-[260px] flex flex-col justify-between transition-all duration-200">
    <!-- 头部：编号标题 + 语义标签 + 问号帮助 -->
    <div class="flex items-center justify-between shrink-0 mb-2">
      <div class="flex items-center space-x-3">
        <span class="text-[21px] font-black text-car-text tracking-wide">{{ title }}</span>
        <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent shrink-0">{{ tag }}</span>
        <button
          v-if="helpText"
          @click.stop="$emit('help')"
          :class="helpSize === 'lg' ? HELP_LG : HELP_SM"
          title="查看功能指南"
        >?</button>
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
// PlanCard — 编号计划瓦片共用骨架（BodyView 四大语音计划 + LinkView 联动计划）
// 6 颗瓦片 100% 克隆此骨架，唯一差异为文案、帮助体尺寸与底部按钮（footer 插槽）。
// 像素级还原：类串逐字取自原手搓瓦片。
// ============================================================================
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

const HELP_LG = 'w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-accent hover:border-car-accent font-black text-[18px] flex items-center justify-center cursor-pointer shadow-sm transition-transform active:scale-95 shrink-0';
const HELP_SM = 'w-[34px] h-[34px] rounded-full border-2 border-car-border bg-car-item text-car-accent hover:border-car-accent font-black text-[15px] flex items-center justify-center cursor-pointer shadow-sm transition-transform active:scale-95 shrink-0';
</script>
