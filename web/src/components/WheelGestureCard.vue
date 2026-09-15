<template>
  <FeatureCard :title="cardTitle" :desc="cardDesc">
    <div class="flex items-center justify-between bg-car-item border border-car-border rounded-2xl p-3 mb-4 shadow-sm">
      <div class="flex items-center space-x-2">
        <button
          v-for="g in gestureList"
          :key="g.id"
          @click="activeGesture = g.id"
          :class="[
            'px-4 py-2 rounded-xl text-[14.5px] font-black cursor-pointer transition-all whitespace-nowrap',
            activeGesture === g.id
              ? 'bg-car-card border-2 border-car-accent text-car-text shadow-sm'
              : 'bg-car-card border border-car-border text-car-sub hover:border-car-border-light'
          ]"
        >
          {{ g.name }}
        </button>
      </div>
      <div class="text-[13.5px] font-bold text-car-sub">
        当前配置：<span class="text-car-accent">单击[{{ getActionName(getGestureAction(keyName, 'single')) }}]</span> ·
        <span>双击[{{ getActionName(getGestureAction(keyName, 'double')) }}]</span> ·
        <span>长按[{{ getActionName(getGestureAction(keyName, 'long')) }}]</span>
      </div>
    </div>

    <div class="grid grid-cols-5 gap-3.5" style="grid-gap: 14px; -webkit-column-gap: 14px;">
        <MatrixButton
          v-for="btn in actions"
          :key="btn.action"
          :title="btn.title"
          :subtitle="btn.subtitle"
          :active="getGestureAction(keyName, activeGesture) === btn.action"
          @click="setGestureAction(keyName, activeGesture, btn.action)"
        />
      <MatrixButton
        :title="isCustomApp(getGestureAction(keyName, activeGesture)) ? (getCustomAppName(keyName + '_' + activeGesture) || '自定义应用') : '自定义打开应用'"
        :subtitle="isCustomApp(getGestureAction(keyName, activeGesture)) ? '点击可重新更换应用' : '自由挑选车机第三方应用'"
        :active="isCustomApp(getGestureAction(keyName, activeGesture))"
        @click="openAppSelectModal(keyName + '_' + activeGesture)"
      />
    </div>
  </FeatureCard>
</template>

<script setup>
import { ref } from 'vue';
import FeatureCard from './FeatureCard.vue';
import MatrixButton from './MatrixButton.vue';
import { useWheelGesture } from '../composables/useWheelGesture';

const props = defineProps({
  keyName: { type: String, required: true },
  cardTitle: { type: String, required: true },
  cardDesc: { type: String, required: true },
  actions: { type: Array, required: true }
});

const activeGesture = ref('single');
const { gestureList, isCustomApp, getCustomAppName, openAppSelectModal, getActionName, getGestureAction, setGestureAction } = useWheelGesture();
</script>
