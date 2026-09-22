<template>
  <div class="inline-flex items-center space-x-2 px-2.5 py-1 rounded-xl bg-car-card border border-car-border shadow-inner">
    <span class="text-[12px] font-bold text-car-sub whitespace-nowrap">限价直适:</span>
    
    <!-- 中国石油 -->
    <div class="flex items-center space-x-1.5 whitespace-nowrap">
      <svg class="w-4 h-4 shrink-0 rounded-full shadow-sm" viewBox="0 0 100 100" fill="none">
        <circle cx="50" cy="50" r="48" fill="#D92D20" />
        <!-- 宝石花黄芯与太阳花瓣 -->
        <circle cx="50" cy="50" r="28" fill="#FDB022" />
        <path d="M50 12 L56 38 L82 34 L62 50 L78 72 L50 60 L22 72 L38 50 L18 34 L44 38 Z" fill="#FEF08A" />
        <circle cx="50" cy="50" r="14" fill="#D92D20" />
      </svg>
      <span class="text-[12.5px] font-black text-car-text tracking-tight">中国石油</span>
    </div>

    <div class="h-3 w-px bg-car-border"></div>

    <!-- 中国石化 -->
    <div class="flex items-center space-x-1.5 whitespace-nowrap">
      <svg class="w-4 h-4 shrink-0 rounded-full shadow-sm" viewBox="0 0 100 100" fill="none">
        <circle cx="50" cy="50" r="48" fill="#C92A2A" />
        <!-- 朝阳火炬与环状标志 -->
        <circle cx="50" cy="50" r="38" fill="#FFFFFF" />
        <path d="M50 18 C32 18 18 32 18 50 C18 68 32 82 50 82 C68 82 82 68 82 50 C82 32 68 18 50 18 Z" fill="#D92D20" />
        <path d="M50 25 L58 45 L50 40 L42 45 Z" fill="#FFFFFF" />
        <rect x="47" y="42" width="6" height="28" rx="2" fill="#FFFFFF" />
        <circle cx="50" cy="50" r="8" fill="#D92D20" />
      </svg>
      <span class="text-[12.5px] font-black text-car-text tracking-tight">中国石化</span>
    </div>
  </div>
</template>

<script setup>
// 中国石油与中国石化官方标准配色徽标组件
</script>
