<template>
  <div class="relative" ref="rootEl">
    <!-- 触发按钮：车规大磁贴 (80px 高)，显示当前选中动作 -->
    <button
      @click="toggle"
      class="w-full rounded-2xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-md"
      :class="isCustom
        ? 'bg-car-item border-car-accent text-car-text'
        : 'bg-car-item border-car-border text-car-text hover:border-car-border-light'"
      style="min-height: 80px; height: 80px; padding: 12px 20px; box-sizing: border-box;"
    >
      <span class="flex flex-col items-start min-w-0">
        <span class="text-[19px] font-black text-car-text leading-tight truncate w-full text-left">{{ displayName }}</span>
        <span class="text-[14px] font-bold text-car-sub mt-1 truncate w-full text-left">{{ displaySub }}</span>
      </span>
      <span class="text-[20px] text-car-sub font-black ml-3 shrink-0">▾</span>
    </button>

    <!-- 下拉面板：大磁贴选项列表（浮动弹出：动态方向+高度钳制，永不撑破页面/超出视口）
         铁律：浮层叠在页面内容之上，必须用弹窗实底 --bg-modal(96%)，严禁 --bg-card(玻璃半透 .13~.68)
         —— 半透会让底下页面文字透上来与选项重叠（2026-09-18 用户实测反馈，门禁23 锁定） -->
    <transition name="drop">
      <div
        v-if="open"
        class="absolute z-50 w-full min-w-[280px] overflow-y-auto rounded-2xl border-2 border-car-border bg-[var(--bg-modal)] shadow-2xl p-2 space-y-1.5"
        :class="dropUp ? 'bottom-full mb-2' : 'top-full mt-2'"
        :style="{ maxHeight: maxPanelH + 'px' }"
      >
        <div class="grid grid-cols-3 gap-2">
          <button
            v-for="opt in options"
            :key="opt.action"
            @click="pick(opt.action)"
            class="rounded-xl flex flex-col items-start justify-center cursor-pointer transition-all text-left px-3 py-2"
            :class="currentAction === opt.action
              ? 'bg-car-item border-2 border-car-accent text-car-text'
              : 'bg-car-item border-2 border-car-border text-car-text hover:border-car-border-light'"
            style="min-height: 72px; box-sizing: border-box;"
          >
            <span class="text-[18px] font-black leading-tight truncate w-full">{{ opt.name }}</span>
            <span class="text-[14px] font-bold text-car-sub mt-0.5 truncate w-full">{{ opt.sub }}</span>
          </button>
        </div>

        <!-- 自定义应用入口：双列网格整行 -->
        <button
          @click="pickCustom"
          class="w-full rounded-xl flex items-center justify-between cursor-pointer transition-all text-left"
          :class="isCustom
            ? 'bg-car-item border-2 border-car-accent text-car-text'
            : 'bg-car-item border-2 border-car-border text-car-text hover:border-car-border-light'"
          style="min-height: 64px; padding: 10px 16px; box-sizing: border-box;"
        >
          <span class="flex flex-col min-w-0">
            <span class="text-[18px] font-black">{{ isCustom ? (customName || '自定义应用') : '自定义打开应用' }}</span>
            <span class="text-[13.5px] font-bold text-car-sub mt-0.5">{{ isCustom ? '点击重新更换应用' : '挑选车机第三方应用' }}</span>
          </span>
          <span v-if="isCustom" class="text-[20px] text-car-accent font-black ml-2 shrink-0">✓</span>
        </button>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useWheelGesture } from '../composables/useWheelGesture';
import { playTouchFeedback } from '../utils/sound';

const props = defineProps({
  keyName: { type: String, required: true },
  gesture: { type: String, required: true }
});

const open = ref(false);
const rootEl = ref(null);
const dropUp = ref(false);
const maxPanelH = ref(320);

const { getActionOptions, getGestureAction, setGestureAction, isCustomApp, getCustomAppName, openAppSelectModal, getActionName } = useWheelGesture();

const options = computed(() => getActionOptions(props.keyName));

const currentAction = computed(() => getGestureAction(props.keyName, props.gesture));

const isCustom = computed(() => isCustomApp(currentAction.value));

const customName = computed(() => getCustomAppName(props.keyName + '_' + props.gesture));

const displayName = computed(() => {
  if (isCustom.value) return customName.value || '自定义应用';
  return getActionName(currentAction.value);
});

const displaySub = computed(() => {
  if (isCustom.value) return '已选第三方应用';
  const found = options.value.find(o => o.action === currentAction.value);
  return found ? found.sub : '';
});

function toggle() {
  playTouchFeedback();
  if (!open.value) {
    // 量测按钮在视口中的位置，动态决定弹出方向与最大高度，保证面板完整可见
    const rect = rootEl.value?.getBoundingClientRect();
    if (rect) {
      const spaceUp = rect.top;
      const spaceDown = window.innerHeight - rect.bottom;
      dropUp.value = spaceUp > spaceDown;
      const margin = 16;
      maxPanelH.value = Math.max(200, Math.floor((dropUp.value ? spaceUp : spaceDown) - margin));
    }
  }
  open.value = !open.value;
}

function pick(action) {
  playTouchFeedback();
  setGestureAction(props.keyName, props.gesture, action);
  open.value = false;
}

function pickCustom() {
  open.value = false;
  openAppSelectModal(props.keyName + '_' + props.gesture);
}

function onClickOutside(e) {
  if (rootEl.value && !rootEl.value.contains(e.target)) {
    open.value = false;
  }
}

onMounted(() => document.addEventListener('click', onClickOutside));
onUnmounted(() => document.removeEventListener('click', onClickOutside));
</script>

<style scoped>
.drop-enter-active, .drop-leave-active { transition: opacity 0.15s ease, transform 0.15s ease; }
.drop-enter-from, .drop-leave-to { opacity: 0; transform: translateY(6px); }
</style>
