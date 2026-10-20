<template>
  <div class="inline-flex items-center space-x-2">
    <!-- 中国石化 -->
    <div class="inline-flex items-center space-x-1.5 px-2.5 py-1 rounded-lg bg-car-card border border-car-border shadow-xs">
      <div class="w-5 h-5 rounded-md overflow-hidden bg-white flex items-center justify-center p-0.5 shrink-0 border border-car-border/30">
        <img :src="brandSinopec" alt="中国石化" class="w-full h-full object-contain block" />
      </div>
      <span class="text-[12px] font-black text-car-text leading-none select-none">中国石化</span>
    </div>

    <!-- 中国石油 -->
    <div class="inline-flex items-center space-x-1.5 px-2.5 py-1 rounded-lg bg-car-card border border-car-border shadow-xs">
      <div class="w-5 h-5 rounded-md overflow-hidden bg-white flex items-center justify-center p-0.5 shrink-0 border border-car-border/30">
        <img :src="brandPetroChina" alt="中国石油" class="w-full h-full object-contain block" />
      </div>
      <span class="text-[12px] font-black text-car-text leading-none select-none">中国石油</span>
    </div>
  </div>
</template>

<script setup>
import brandSinopec from '../assets/brand_sinopec.png';
import brandPetroChina from '../assets/brand_petrochina.png';
</script>
