<template>
  <div class="rounded-3xl border-2 border-car-border p-6 shadow-2xl backdrop-blur-xl transition-all bg-car-card" style="margin-bottom: 20px;">
    <!-- 头部标题与说明：内嵌在卡片顶端 -->
    <div class="flex flex-col gap-1.5 pb-4 mb-4 border-b border-car-border/60">
      <div class="text-[21px] font-black text-car-text tracking-wide flex items-center gap-2.5">
        {{ title }}
      </div>
      <div v-if="desc" class="text-[15px] text-car-sub font-bold leading-relaxed">
        {{ desc }}
      </div>
    </div>

    <!-- 内部操作内容区 -->
    <div class="w-full">
      <slot />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: String,
  desc: String
});
</script>
