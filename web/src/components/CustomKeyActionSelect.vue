<template>
  <div class="relative" ref="rootEl">
    <!-- 触发按钮：车规大磁贴 (68px 高)，显示当前选中动作 -->
    <button
      @click="toggle"
      class="w-full rounded-2xl border flex items-center justify-between cursor-pointer transition-all shadow-md bg-car-item border-car-border text-car-text hover:border-car-border-light active:scale-[0.99]"
      style="min-height: 68px; height: 68px; padding: 10px 18px; box-sizing: border-box;"
    >
      <span class="flex flex-col items-start min-w-0 pr-2">
        <span class="text-[17.5px] font-black text-car-text leading-tight truncate w-full text-left">{{ displayName }}</span>
        <span class="text-[13px] font-bold text-car-sub mt-1 truncate w-full text-left">{{ displaySub }}</span>
      </span>
      <span class="text-[18px] text-car-sub font-black shrink-0">▾</span>
    </button>

    <!-- 下拉面板：车规双列磁贴浮层 (完全接入主题实底 --bg-modal) -->
      <div
        v-if="open"
        class="absolute z-50 w-[480px] max-w-[90vw] overflow-y-auto rounded-2xl border-2 border-car-border bg-[var(--bg-modal)] shadow-lg p-2.5"
        :class="[dropUp ? 'bottom-full mb-2' : 'top-full mt-2', alignRight ? 'right-0' : 'left-0']"
        :style="{ maxHeight: maxPanelH + 'px' }"
      >
        <div class="grid grid-cols-2 gap-2">
          <button
            v-for="opt in options"
            :key="opt.action"
            @click="pick(opt.action)"
            class="rounded-xl flex items-center justify-between cursor-pointer transition-all text-left px-3 py-2.5"
            :class="modelValue === opt.action
              ? 'bg-car-item border-car-border border-solid border-l-[5px] border-l-car-accent text-car-text shadow-md'
              : 'bg-car-item border-2 border-car-border text-car-text hover:border-car-border-light'"
            style="min-height: 56px; box-sizing: border-box;"
          >
            <span class="flex flex-col min-w-0 pr-1.5">
              <span class="text-[15.5px] font-black leading-tight text-car-text whitespace-nowrap">{{ opt.name }}</span>
              <span class="text-[12px] font-bold text-car-sub mt-0.5 whitespace-nowrap">{{ opt.sub }}</span>
            </span>
            <span v-if="modelValue === opt.action" class="text-[17px] text-car-accent font-black ml-1.5 shrink-0">✓</span>
          </button>

          <!-- 自定义应用 -->
          <button
            @click="pickCustom"
            class="rounded-xl flex items-center justify-between cursor-pointer transition-all text-left px-3 py-2.5"
            :class="isCustom
              ? 'bg-car-item border-car-border border-solid border-l-[5px] border-l-car-accent text-car-text shadow-md'
              : 'bg-car-item border-2 border-car-border text-car-text hover:border-car-border-light'"
            style="min-height: 56px; box-sizing: border-box;"
          >
            <span class="flex flex-col min-w-0 pr-1.5">
              <span class="text-[15.5px] font-black leading-tight text-car-text whitespace-nowrap">{{ isCustom ? (customName || '自定义应用') : '自定义应用' }}</span>
              <span class="text-[12px] font-bold text-car-sub mt-0.5 whitespace-nowrap">{{ isCustom ? '重新挑选应用' : '挑选车机应用' }}</span>
            </span>
            <span v-if="isCustom" class="text-[17px] text-car-accent font-black ml-1.5 shrink-0">✓</span>
          </button>
        </div>
      </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useWheelGesture } from '../composables/useWheelGesture';
import { playTouchFeedback } from '../utils/sound';
import { openModal } from '../store';
import { claimExclusive, releaseExclusive } from '../utils/dropdownMutex';

const props = defineProps({
  modelValue: { type: String, default: 'default' },
  customAppName: { type: String, default: '' },
  rowId: { type: String, default: '' }
});

const emit = defineEmits(['update:modelValue', 'update:customAppName', 'change']);

const open = ref(false);
const rootEl = ref(null);
const dropUp = ref(false);
const alignRight = ref(false);
const maxPanelH = ref(340);

const { getActionOptions, isCustomApp, ACTION_DEFS } = useWheelGesture();
const options = computed(() => getActionOptions('custom'));

const isCustom = computed(() => isCustomApp(props.modelValue));
const customName = computed(() => props.customAppName);

const currentDef = computed(() => {
  if (isCustom.value) {
    return { name: customName.value || '自定义应用', sub: '一键拉起前台置顶' };
  }
  return ACTION_DEFS[props.modelValue] || { name: '恢复原车默认', sub: '完全放行交给原车' };
});

const displayName = computed(() => currentDef.value.name);
const displaySub = computed(() => currentDef.value.sub);

function toggle() {
  playTouchFeedback();
  if (!open.value) {
    updatePosition();
    // 与 ActionSelect 共用同一把互斥锁：整屏同时只允许一个下拉展开
    claimExclusive(closeFromPeer);
  }
  open.value = !open.value;
}

// 让别的下拉（含 ActionSelect）能主动关掉我，收起后释放活跃句柄
function closeFromPeer() {
  open.value = false;
  releaseExclusive(closeFromPeer);
}

function updatePosition() {
  if (!rootEl.value) return;
  const rect = rootEl.value.getBoundingClientRect();
  const spaceBelow = window.innerHeight - rect.bottom;
  const spaceAbove = rect.top;
  dropUp.value = spaceBelow < 320 && spaceAbove > spaceBelow;
  maxPanelH.value = Math.min(340, Math.max(180, (dropUp.value ? spaceAbove : spaceBelow) - 16));
  alignRight.value = rect.left > window.innerWidth / 2;
}

function pick(actionKey) {
  playTouchFeedback();
  open.value = false;
  releaseExclusive(closeFromPeer);
  emit('update:modelValue', actionKey);
  emit('change', { action: actionKey, customName: '' });
}

function pickCustom() {
  playTouchFeedback();
  open.value = false;
  releaseExclusive(closeFromPeer);
  openModal('appSelect', {
    keyTarget: 'custom_row_' + props.rowId,
    onSelect: (pkg, appName) => {
      const act = 'app:' + pkg;
      emit('update:modelValue', act);
      emit('update:customAppName', appName || pkg);
      emit('change', { action: act, customName: appName || pkg });
    }
  });
}

function onDocClick(e) {
  if (open.value && rootEl.value && !rootEl.value.contains(e.target)) {
    open.value = false;
    releaseExclusive(closeFromPeer);
  }
}

onMounted(() => document.addEventListener('click', onDocClick));
onUnmounted(() => document.removeEventListener('click', onDocClick));
</script>

<style scoped>
/* 同 ActionSelect：去掉淡入/位移动画，点开即显，杜绝车机上的中间帧掉帧 */
</style>
