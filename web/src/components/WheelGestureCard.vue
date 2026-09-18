<template>
  <FeatureCard :title="cardTitle" :desc="cardDesc" :helpTitle="helpTitle" :helpText="helpText" :helpTip="helpTip">
    <!-- 手势切换 (单击/双击/长按) -->
    <div class="flex flex-wrap items-center space-x-1.5 bg-car-item border border-car-border rounded-2xl p-3 mb-4 shadow-sm">
      <div class="flex flex-wrap items-center space-x-1.5">
        <button
          v-for="g in gestureList"
          :key="g.id"
          @click="activeGesture = g.id"
          :class="[
            'px-5 py-2.5 rounded-xl text-[15.5px] font-black cursor-pointer transition-all whitespace-nowrap',
            activeGesture === g.id
              ? 'bg-car-card border-2 border-car-accent text-car-text shadow-sm'
              : 'bg-car-card border border-car-border text-car-sub hover:border-car-border-light'
          ]"
        >
          {{ g.name }}
        </button>
      </div>
    </div>

    <!-- 当前手势的动作下拉选择器 (统一抽象：原厂/切歌 → 高德 → 360 → 播放暂停 → 自定义) -->
    <ActionSelect :key-name="keyName" :gesture="activeGesture" />
  </FeatureCard>
</template>

<script setup>
import { ref } from 'vue';
import FeatureCard from './FeatureCard.vue';
import ActionSelect from './ActionSelect.vue';
import { useWheelGesture } from '../composables/useWheelGesture';

const props = defineProps({
  keyName: { type: String, required: true },
  cardTitle: { type: String, required: true },
  cardDesc: { type: String, required: true },
  helpTitle: { type: String, default: '' },
  helpText: { type: String, default: '' },
  helpTip: { type: String, default: '' }
});

const activeGesture = ref('single');
const { gestureList } = useWheelGesture();
</script>
