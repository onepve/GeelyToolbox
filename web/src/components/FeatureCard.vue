<template>
  <div class="bg-car-card border border-car-border rounded-3xl p-6 flex flex-col gap-4 shadow-xl backdrop-blur-xl transition-all">
    <div class="flex flex-col gap-1.5">
      <div class="text-[24px] font-black text-car-text flex items-center gap-2.5">
        {{ title }}
      </div>
      <div v-if="desc" class="text-[17.5px] text-car-sub font-bold leading-relaxed">
        {{ desc }}
      </div>
    </div>
    <div class="mt-1">
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
