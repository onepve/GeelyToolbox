<template>
  <div class="inline-flex items-center space-x-2.5">
    <!-- 中国石化 -->
    <div class="inline-flex items-center space-x-2 px-3 py-1.5 rounded-xl bg-car-card border border-car-border shadow-sm">
      <div class="w-7 h-7 rounded-lg overflow-hidden bg-white flex items-center justify-center p-0.5 shrink-0 border border-car-border/40">
        <img :src="brandSinopec" alt="中国石化" class="w-full h-full object-contain block" />
      </div>
      <span class="text-[14px] font-black text-car-text leading-none select-none">中国石化</span>
    </div>

    <!-- 中国石油 -->
    <div class="inline-flex items-center space-x-2 px-3 py-1.5 rounded-xl bg-car-card border border-car-border shadow-sm">
      <div class="w-7 h-7 rounded-lg overflow-hidden bg-white flex items-center justify-center p-0.5 shrink-0 border border-car-border/40">
        <img :src="brandPetroChina" alt="中国石油" class="w-full h-full object-contain block" />
      </div>
      <span class="text-[14px] font-black text-car-text leading-none select-none">中国石油</span>
    </div>
  </div>
</template>

<script setup>
import brandSinopec from '../assets/brand_sinopec.png';
import brandPetroChina from '../assets/brand_petrochina.png';
</script>
