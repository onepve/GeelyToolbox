<template>
  <div class="relative" ref="rootEl">
    <!-- 触发按钮：车规大磁贴 (80px 高)，显示当前选中动作 -->
    <button
      @click="toggle"
      class="w-full rounded-2xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-md bg-car-item border-car-border text-car-text hover:border-car-border-light"
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
        ref="panelEl"
        class="fixed z-[80] overflow-y-auto rounded-2xl border-2 border-car-border bg-[var(--bg-modal)] shadow-2xl p-2.5 space-y-2"
        :style="panelStyle"
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

          <!-- 自定义第三方应用入口：作为标准磁贴单元与左侧对齐（息屏屏保右侧） -->
          <button
            @click="pickCustom"
            class="rounded-xl flex items-center justify-between cursor-pointer transition-all text-left px-3 py-2.5"
            :class="isCustom
              ? 'bg-car-item border-2 border-car-accent text-car-text shadow-sm'
              : 'bg-car-item border-2 border-car-border text-car-text hover:border-car-border-light'"
            style="min-height: 60px; box-sizing: border-box;"
          >
            <span class="flex flex-col min-w-0 pr-1.5">
              <span class="text-[16px] font-black leading-tight text-car-text whitespace-nowrap">{{ isCustom ? (customName || '自定义应用') : '自定义应用' }}</span>
              <span class="text-[12.5px] font-bold text-car-sub mt-0.5 whitespace-nowrap">{{ isCustom ? '点击重新挑选' : '挑选车机应用' }}</span>
            </span>
            <span v-if="isCustom" class="text-[18px] text-car-accent font-black ml-1.5 shrink-0">✓</span>
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue';
import { useWheelGesture } from '../composables/useWheelGesture';
import { playTouchFeedback } from '../utils/sound';

const props = defineProps({
  keyName: { type: String, required: true },
  gesture: { type: String, required: true }
});

const open = ref(false);
const rootEl = ref(null);
const panelEl = ref(null);
// 面板用 fixed 定位：absolute 会被右侧滚动容器(overflow-y-auto)裁剪，
// 第一排卡片向上展开时整块顶部被顶栏切掉，车主看不到内容。
const panelStyle = ref({ left: '0px', top: '0px', width: '520px', maxHeight: '360px' });

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

// 竖直方向的硬边界：不许压到顶栏，也不许漏出屏幕底部
const GAP = 8;    // 面板与触发按钮之间的间距
const EDGE = 10;  // 距可用区上下边缘的最小留白
const MIN_H = 150; // 再小就点不准；只有两侧都放不下时才允许贴边

function panelBounds() {
  const rect = rootEl.value?.getBoundingClientRect();
  if (!rect) return null;
  // 实际可用的上下空间 = 视口内、且不被顶栏压住的那一段。
  // 顶栏是 flex 流内元素，这里用它的下沿作为上边界，下边界就是视口底部。
  const topBar = document.querySelector('header, .tb-topbar');
  const topBound = topBar ? topBar.getBoundingClientRect().bottom : 0;
  return {
    rect,
    vw: window.innerWidth,
    vh: window.innerHeight,
    spaceUp: rect.top - topBound - GAP - EDGE,
    spaceDown: window.innerHeight - rect.bottom - GAP - EDGE
  };
}

async function toggle() {
  playTouchFeedback();
  if (open.value) { open.value = false; return; }

  const b = panelBounds();
  if (!b) { open.value = true; return; }

  const { rect, vw, vh } = b;
  const width = Math.min(520, vw - 2 * EDGE);

  // 先按「哪边空间大」定方向；若该方向连 MIN_H 都放不下就翻到另一边
  let useUp = b.spaceUp > b.spaceDown;
  if (useUp && b.spaceUp < MIN_H && b.spaceDown > b.spaceUp) useUp = false;
  if (!useUp && b.spaceDown < MIN_H && b.spaceUp > b.spaceDown) useUp = true;

  const avail = Math.max(MIN_H, useUp ? b.spaceUp : b.spaceDown);
  let panelH = Math.min(avail, 420);

  // 水平方向夹紧，绝不漏出左/右边
  const left = Math.max(EDGE, Math.min(rect.left, vw - width - EDGE));
  const top = useUp ? rect.top - GAP - panelH : rect.bottom + GAP;

  panelStyle.value = {
    left: Math.round(left) + 'px',
    top: Math.round(Math.max(EDGE, top)) + 'px',
    width: Math.round(width) + 'px',
    maxHeight: Math.round(panelH) + 'px'
  };
  open.value = true;

  // 渲染后按真实高度二次校正：内容比预估更矮时把面板贴回按钮，
  // 更高时再收一次高度，确保任何情况下都完整落在可视区内。
  await nextTick();
  const realH = panelEl.value?.offsetHeight || 0;
  if (!realH) return;
  const finalH = Math.min(realH, panelH);
  const finalTop = useUp
    ? Math.max(EDGE, rect.top - GAP - finalH)
    : Math.min(vh - EDGE - finalH, rect.bottom + GAP);
  panelStyle.value = {
    ...panelStyle.value,
    top: Math.round(finalTop) + 'px',
    maxHeight: Math.round(Math.min(finalH, useUp ? rect.top - GAP - EDGE : vh - EDGE - rect.bottom - GAP)) + 'px'
  };
}

// 页面一滚，fixed 面板就会跟按钮脱节，直接收起
function onScrollClose() {
  if (open.value) open.value = false;
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
  window.addEventListener('scroll', onScrollClose, true);
  window.addEventListener('resize', onScrollClose);
});

onUnmounted(() => {
  document.removeEventListener('click', onDocClick);
  window.removeEventListener('scroll', onScrollClose, true);
  window.removeEventListener('resize', onScrollClose);
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
