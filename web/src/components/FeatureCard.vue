<template>
  <div class="bg-car-card border border-car-border rounded-3xl p-6 flex flex-col shadow-xl backdrop-blur-xl transition-all">
    <!-- 标题容器：显式内联设置 margin-bottom 与 line-height，杜绝任何浏览器内核继承折叠 -->
    <div style="margin-bottom: 18px; display: block; width: 100%;">
      <div style="font-size: 22px; font-weight: 900; line-height: 1.4; color: var(--txt-color); margin-bottom: 6px;">
        {{ title }}
      </div>
      <div v-if="desc" style="font-size: 15.5px; font-weight: 700; line-height: 1.5; color: var(--sub-color);">
        {{ desc }}
      </div>
    </div>

    <!-- 按键/内容插槽容器：显式内联设置顶部防侵入安全区 -->
    <div style="display: block; width: 100%; position: relative; clear: both;">
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
