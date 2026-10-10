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

    <!-- 下拉面板：双列车规磁贴列表（宽度 520px，双列并排，避免单列纵向撑破屏幕导致显示不全）
         铁律：浮层叠在页面内容之上，必须用弹窗实底 --bg-modal(96%)，严禁 --bg-card -->
    <transition name="drop">
      <div
        v-if="open"
        class="absolute z-50 w-[520px] max-w-[90vw] overflow-y-auto rounded-2xl border-2 border-car-border bg-[var(--bg-modal)] shadow-2xl p-2.5 space-y-2"
        :class="[dropUp ? 'bottom-full mb-2' : 'top-full mt-2', alignRight ? 'right-0' : 'left-0']"
        :style="{ maxHeight: maxPanelH + 'px' }"
      >
        <div class="grid grid-cols-2 gap-2">
          <button
            v-for="opt in options"
            :key="opt.action"
            @click="pick(opt.action)"
            class="rounded-xl flex items-center justify-between cursor-pointer transition-all text-left px-3 py-2.5"
            :class="currentAction === opt.action
              ? 'bg-car-item border-2 border-car-accent text-car-text shadow-sm'
              : 'bg-car-item border-2 border-car-border text-car-text hover:border-car-border-light'"
            style="min-height: 60px; box-sizing: border-box;"
          >
            <span class="flex flex-col min-w-0 pr-1.5">
              <span class="text-[16px] font-black leading-tight text-car-text whitespace-nowrap">{{ opt.name }}</span>
              <span class="text-[12.5px] font-bold text-car-sub mt-0.5 whitespace-nowrap">{{ opt.sub }}</span>
            </span>
            <span v-if="currentAction === opt.action" class="text-[18px] text-car-accent font-black ml-1.5 shrink-0">✓</span>
          </button>

          <!-- 自定义第三方应用入口：占满双列 -->
          <button
            @click="pickCustom"
            class="col-span-2 rounded-xl flex items-center justify-between cursor-pointer transition-all text-left px-3.5 py-2.5"
            :class="isCustom
              ? 'bg-car-item border-2 border-car-accent text-car-text'
              : 'bg-car-item border-2 border-car-border text-car-text hover:border-car-border-light'"
            style="min-height: 60px; box-sizing: border-box;"
          >
            <span class="flex flex-col min-w-0">
              <span class="text-[16px] font-black">{{ isCustom ? (customName || '自定义应用') : '自定义打开应用' }}</span>
              <span class="text-[12.5px] font-bold text-car-sub mt-0.5">{{ isCustom ? '点击重新更换应用' : '挑选车机第三方应用' }}</span>
            </span>
            <span v-if="isCustom" class="text-[18px] text-car-accent font-black ml-2 shrink-0">✓</span>
          </button>
        </div>
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
const alignRight = ref(false);
const maxPanelH = ref(360);

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
    const rect = rootEl.value?.getBoundingClientRect();
    if (rect) {
      const spaceUp = rect.top;
      const spaceDown = window.innerHeight - rect.bottom;
      dropUp.value = spaceUp > spaceDown;
      alignRight.value = (rect.left + 520) > window.innerWidth;
      const margin = 16;
      maxPanelH.value = Math.max(220, Math.floor((dropUp.value ? spaceUp : spaceDown) - margin));
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
  playTouchFeedback();
  open.value = false;
  openAppSelectModal(props.keyName, props.gesture);
}

function onDocClick(e) {
  if (open.value && rootEl.value && !rootEl.value.contains(e.target)) {
    open.value = false;
  }
}

onMounted(() => {
  document.addEventListener('click', onDocClick);
});

onUnmounted(() => {
  document.removeEventListener('click', onDocClick);
});
</script>

<style scoped>
.drop-enter-active, .drop-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.drop-enter-from, .drop-leave-to {
  opacity: 0;
  transform: scaleY(0.95);
}
</style>
