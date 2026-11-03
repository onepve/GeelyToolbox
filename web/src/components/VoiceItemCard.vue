<template>
  <div
    @click="handleClick"
    :class="[
      'rounded-3xl border-2 p-5 flex flex-col justify-between transition-all duration-150 cursor-pointer select-none active:scale-[0.985]',
      active
        ? 'bg-car-item border-car-accent shadow-md'
        : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
    ]"
  >
    <!-- 头部：标题与标签 -->
    <div class="flex items-center justify-between mb-2">
      <div class="flex items-center space-x-2.5">
        <span class="text-[20px] font-black text-car-text tracking-wide">{{ title }}</span>
        <span
          v-if="tag"
          class="px-2.5 py-0.5 text-[12.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent shrink-0"
        >
          {{ tag }}
        </span>
      </div>
    </div>

    <!-- 中部：详细描述说明 -->
    <div class="text-[14.5px] text-car-sub font-bold leading-relaxed mb-4">
      {{ desc }}
    </div>

    <!-- 底部操作与状态指示槽 -->
    <div class="pt-3.5 border-t border-car-border flex items-center justify-between mt-auto">
      <!-- 左侧：轻量化状态指示灯（小绿灯+已开启 / 灰灯+已关闭） -->
      <div class="flex items-center space-x-2">
        <StatusDot
          size="sm"
          :color="active ? 'ok' : 'off'"
          :glow-px="active ? 6 : 0"
        />
        <span
          :class="[
            'text-[15px] font-black tracking-wide',
            active ? 'text-car-text' : 'text-car-sub'
          ]"
        >
          {{ active ? '已开启' : '已关闭' }}
        </span>
      </div>

      <!-- 右侧：辅助按钮（试听与声效设置，严格阻止事件冒泡防误触） -->
      <div class="flex items-center space-x-2 shrink-0">
        <button
          type="button"
          @click.stop="handleTest"
          class="h-[50px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[13.5px] cursor-pointer shadow-sm active:scale-95 transition-all"
        >
          试听
        </button>
        <button
          v-if="showCustom !== false"
          type="button"
          @click.stop="handleCustom"
          class="h-[50px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[13.5px] cursor-pointer shadow-sm active:scale-95 transition-all"
        >
          声效设置
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
// ============================================================================
// VoiceItemCard — 车规级扁平化语音单项实体开关卡片
// 整卡即开关热区（菲茨定律高容错），底部原按钮位置收敛为轻量发光状态灯+文字
// ============================================================================
import StatusDot from './StatusDot.vue';
import { playTouchFeedback } from '../utils/sound';

const props = defineProps({
  title: { type: String, required: true },
  desc: { type: String, default: '' },
  tag: { type: String, default: '' },
  active: { type: Boolean, default: false },
  showCustom: { type: Boolean, default: true }
});

const emit = defineEmits(['toggle', 'test', 'custom']);

function handleClick() {
  playTouchFeedback();
  emit('toggle');
}

function handleTest() {
  playTouchFeedback();
  emit('test');
}

function handleCustom() {
  playTouchFeedback();
  emit('custom');
}
</script>
