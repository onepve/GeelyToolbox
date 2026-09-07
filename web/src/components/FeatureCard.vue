<template>
  <div class="bg-car-card border border-car-border rounded-3xl p-6 flex flex-col shadow-xl backdrop-blur-xl transition-all">
    <div class="flex flex-col mb-4">
      <div class="text-[23px] font-black text-car-text flex items-center gap-2.5">
        {{ title }}
      </div>
      <div v-if="desc" class="text-[16px] text-car-sub font-bold leading-relaxed mt-1.5">
        {{ desc }}
      </div>
    </div>
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
