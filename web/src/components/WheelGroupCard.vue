<template>
  <!--
    ==========================================================================
    WheelGroupCard — 方控按键分组大卡（v1.8.32.17）
    和车身 VoiceGroupCard 同一套视觉语言：一张大卡 = 一个整机部件，
    卡头本身是该部件的总开关，卡内每行是单个按键的动作映射。

    为什么不能直接复用 VoiceGroupCard：
      车身卡内是「开关 + 试听 + 换声音」三件套；
      方控卡内是动作下拉选择器（ActionSelect），且下拉面板是 absolute 定位，
      外层绝不能 overflow-hidden，否则面板会被裁掉点不到 —— 这是本次重点防的坑。
    ==========================================================================
  -->
  <div
    :class="[
      'rounded-3xl border-2 flex flex-col transition-all duration-150',
      masterOn ? 'bg-car-card border-car-accent shadow-md' : 'bg-car-card border-car-border shadow-sm'
    ]"
  >
    <!-- 卡头 = 方控接管总开关 -->
    <div
      @click="onToggle"
      class="flex items-center justify-between px-5 py-4 cursor-pointer select-none active:scale-[0.995] transition-all"
    >
      <div class="flex items-center space-x-2.5 min-w-0">
        <span class="text-[20px] font-black text-car-text tracking-wide whitespace-nowrap">{{ title }}</span>
        <span
          class="px-2.5 py-0.5 text-[12.5px] font-black rounded-full border bg-car-item shrink-0"
          :class="masterOn ? 'border-car-accent text-car-accent' : 'border-car-border text-car-sub'"
        >
          {{ masterOn ? '已接管' : '原车默认' }}
        </span>
      </div>
      <div class="flex items-center space-x-2 shrink-0">
        <StatusDot size="md" :color="masterOn ? 'ok' : 'off'" :glow-px="masterOn ? 8 : 0" />
        <span :class="['text-[16px] font-black', masterOn ? 'text-car-text' : 'text-car-sub']">
          {{ masterOn ? '点此关闭全部' : '点此开启全部' }}
        </span>
      </div>
    </div>

    <!-- 卡身：模式切换胶囊 + 按键行 -->
    <div class="px-5 pb-5 space-y-3">
      <div class="flex items-center justify-between space-x-3">
        <div class="text-[13px] text-car-sub font-bold">把每个按键想干的事，在下面直接选</div>
        <div class="flex items-center space-x-1.5 shrink-0 bg-car-item p-1.5 rounded-2xl border-2 border-car-border shadow-sm">
          <button
            v-for="m in modes"
            :key="m.id"
            type="button"
            @click.stop="emit('setMode', m.id)"
            :class="[
              'px-4 h-[50px] rounded-xl font-black text-[14.5px] transition-all cursor-pointer flex items-center justify-center border-2 select-none',
              m.active ? 'bg-car-card border-car-accent text-car-accent shadow-md' : 'bg-transparent border-transparent text-car-sub hover:text-car-text'
            ]"
          >
            {{ m.name }}
          </button>
        </div>
      </div>

      <slot />
    </div>
  </div>
</template>

<script setup>
import StatusDot from './StatusDot.vue';
import { playTouchFeedback } from '../utils/sound';

const props = defineProps({
  title: { type: String, required: true },
  masterOn: { type: Boolean, default: false },
  modes: { type: Array, default: () => [] }
});

const emit = defineEmits(['toggle', 'setMode']);

function onToggle() {
  playTouchFeedback();
  emit('toggle');
}
</script>
