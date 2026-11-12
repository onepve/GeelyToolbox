<template>
  <!--
    ==========================================================================
    VoicePairCard — 双联语音卡片（v1.8.32.16）
    把「同一部件 / 同一挡位」的开与关两张卡合成一张，内部左右并排两格。
    背景动机：原来主驾开、主驾关是两张独立卡，描述除开关二字外完全重复，
    8 张车卡首屏根本看不完。合并后一扇门一张卡，一眼对得上号。

    交互契约（与 VoiceItemCard 完全一致，不改变车主操作手感）：
    - 点击某个半格 → 切换该半格的语音开关（对应原来整卡点击）
    - 半格内「试听」→ 播该半格音频
    - 半格内「声效设置」→ 打开该半格的自定义音频
    - 严格 @click.stop 阻止冒泡，绝不误触另一格
    ==========================================================================
  -->
  <div
    :class="[
      'rounded-3xl border-2 p-5 flex flex-col transition-all duration-150',
      anyActive
        ? 'bg-car-item border-car-border border-solid border-l-[5px] border-l-car-accent shadow-md'
        : 'bg-car-card border-car-border shadow-sm'
    ]"
  >
    <!-- 卡片头：部件名 + 整体状态 -->
    <div class="flex items-center justify-between mb-3">
      <div class="flex items-center space-x-2.5">
        <span class="text-[20px] font-black text-car-text tracking-wide">{{ title }}</span>
        <span
          v-if="tag"
          class="px-2.5 py-0.5 text-[12.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent shrink-0"
        >
          {{ tag }}
        </span>
      </div>
      <div class="flex items-center space-x-2 shrink-0">
        <StatusDot size="sm" :color="anyActive ? 'ok' : 'off'" :glow-px="anyActive ? 6 : 0" />
        <span :class="['text-[15px] font-black', anyActive ? 'text-car-text' : 'text-car-sub']">
          {{ overallText }}
        </span>
      </div>
    </div>

    <!-- 两格：左 = 开 / 右 = 关 -->
    <div class="grid grid-cols-2 gap-3 items-stretch">
      <div
        v-for="(item, idx) in items"
        :key="item.key"
        @click.stop="onToggle(item, idx)"
        :class="[
          'rounded-2xl border-2 p-3.5 flex flex-col justify-between transition-all duration-150 cursor-pointer select-none active:scale-[0.985]',
          item.active
            ? 'bg-car-item border-car-border border-solid border-l-[5px] border-l-car-accent'
            : 'bg-car-card border-car-border opacity-80 hover:border-car-border-light'
        ]"
      >
        <!-- 半格标题 + 小状态灯 -->
        <div class="flex items-center justify-between mb-1.5">
          <span class="text-[15.5px] font-black text-car-text">{{ item.label }}</span>
          <div class="flex items-center space-x-1.5 shrink-0">
            <StatusDot size="sm" :color="item.active ? 'ok' : 'off'" :glow-px="item.active ? 5 : 0" />
            <span :class="['text-[12.5px] font-black', item.active ? 'text-car-text' : 'text-car-sub']">
              {{ item.active ? '已开启' : '已关闭' }}
            </span>
          </div>
        </div>

        <!-- 台词预览（一眼看到说的是什么） -->
        <div class="text-[12.5px] text-car-sub font-bold leading-snug mb-2.5">
          {{ item.line }}
        </div>

        <!-- 半格按钮 -->
        <div class="flex items-center space-x-2">
          <button
            type="button"
            @click.stop="onTest(item)"
            class="flex-1 h-[50px] rounded-xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[13px] cursor-pointer shadow-sm active:scale-95 transition-all"
          >
            试听
          </button>
          <button
            v-if="showCustom !== false"
            type="button"
            @click.stop="onCustom(item)"
            class="flex-1 h-[50px] rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[13px] cursor-pointer shadow-sm active:scale-95 transition-all"
          >
            换声音
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// ============================================================================
// VoicePairCard — 双联语音卡片
// 一卡两格，共用卡片头；每格独立开关 / 试听 / 换声音，互不干扰
// ============================================================================
import StatusDot from './StatusDot.vue';
import { computed } from 'vue';
import { playTouchFeedback } from '../utils/sound';

const props = defineProps({
  title: { type: String, required: true },
  tag: { type: String, default: '' },
  // items: [{ key, label, line, active }]
  items: { type: Array, required: true },
  showCustom: { type: Boolean, default: true }
});

const emit = defineEmits(['toggle', 'test', 'custom']);

const anyActive = computed(() => props.items.some((i) => !!i.active));

// 卡片头的整体状态文案：两格都开 / 都关 / 只开一半
const overallText = computed(() => {
  const n = props.items.filter((i) => !!i.active).length;
  if (n === 0) return '均已关闭';
  if (n === props.items.length) return '已开启';
  return '部分开启';
});

function onToggle(item, idx) {
  playTouchFeedback();
  emit('toggle', item.key, idx);
}

function onTest(item) {
  playTouchFeedback();
  emit('test', item.key);
}

function onCustom(item) {
  playTouchFeedback();
  emit('custom', item.key);
}
</script>
