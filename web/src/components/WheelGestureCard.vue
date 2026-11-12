<template>
  <FeatureCard
    :title="cardTitle"
    :desc="cardDesc"
    :helpTitle="helpTitle"
    :helpText="helpText"
    :helpTip="helpTip"
    dense
    tighter
  >
    <!-- 仅保留单击映射：直接展示动作下拉选择器，不再展示多余的手势切换胶囊
         compact：一页要装 6 张卡，80px 下拉压到 56px，标题只留一行 -->
    <ActionSelect :key-name="keyName" gesture="single" compact />
  </FeatureCard>
</template>

<script setup>
import FeatureCard from './FeatureCard.vue';
import ActionSelect from './ActionSelect.vue';

defineProps({
  keyName: { type: String, required: true },
  cardTitle: { type: String, required: true },
  cardDesc: { type: String, default: '' },
  helpTitle: { type: String, default: '' },
  helpText: { type: String, default: '' },
  helpTip: { type: String, default: '' }
});
</script>
