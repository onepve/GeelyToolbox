<template>
  <FeatureCard
    :title="cardTitle"
    :desc="cardDesc"
    :helpTitle="helpTitle"
    :helpText="helpText"
    :helpTip="helpTip"
    :active="isKeyActive"
  >
    <!-- 仅保留单击映射：直接展示动作下拉选择器，不再展示多余的手势切换胶囊 -->
    <ActionSelect :key-name="keyName" gesture="single" />
  </FeatureCard>
</template>

<script setup>
import { computed } from 'vue';
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

const { getGestureAction } = useWheelGesture();
const isKeyActive = computed(() => {
  // 原厂放行与默认状态不作为激活高亮，仅当用户自定义绑定了非原厂动作时才高亮激活
  if (props.keyName === 'mode') {
    // MODE 键若为原厂 open_360 或 default，视为默认常规状态
    const currentAction = getGestureAction(props.keyName, 'single');
    return currentAction && currentAction !== 'default' && currentAction !== 'open_360';
  }
  const currentAction = getGestureAction(props.keyName, 'single');
  return currentAction && currentAction !== 'default';
});
</script>
