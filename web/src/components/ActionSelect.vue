<template>
  <div class="relative" ref="rootEl">
    <!-- 触发按钮：车规大磁贴 (80px 高)，显示当前选中动作；compact 时压到 56px 单行 -->
    <button
      @click="toggle"
      class="w-full rounded-2xl border flex items-center justify-between cursor-pointer transition-all shadow-md bg-car-item border-car-border text-car-text hover:border-car-border-light"
      :style="compact
        ? 'min-height:56px; height:56px; padding:8px 12px; box-sizing:border-box;'
        : 'min-height:80px; height:80px; padding:12px 20px; box-sizing:border-box;'"
    >
      <span class="flex flex-col items-start min-w-0">
        <span :class="compact ? 'text-[16px]' : 'text-[19px]'" class="font-black text-car-text leading-tight truncate w-full text-left">{{ displayName }}</span>
        <span v-if="!compact" class="text-[14px] font-bold text-car-sub mt-1 truncate w-full text-left">{{ displaySub }}</span>
      </span>
      <span :class="compact ? 'text-[17px] ml-2' : 'text-[20px] ml-3'" class="text-car-sub font-black shrink-0">▾</span>
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
              ? 'bg-car-item border-car-border border-solid border-l-[5px] border-l-car-accent text-car-text shadow-md'
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
              ? 'bg-car-item border-car-border border-solid border-l-[5px] border-l-car-accent text-car-text shadow-md'
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
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useWheelGesture } from '../composables/useWheelGesture';
import { playTouchFeedback } from '../utils/sound';

const props = defineProps({
  keyName: { type: String, required: true },
  gesture: { type: String, required: true },
  /**
   * 紧凑模式：用于「切歌」这种一格内要塞两个下拉的卡片。
   * 触发按钮从 80px 压到 56px，只留一行主文字（不显示副标题），
   * 下拉面板本身不变（60px 磁贴），所以点开照样好点。
   */
  compact: { type: Boolean, default: false }
});

const open = ref(false);
const rootEl = ref(null);
const panelEl = ref(null);
// 面板用 fixed 定位：absolute 会被右侧滚动容器(overflow-y-auto)裁剪，
// 第一排卡片向上展开时整块顶部被顶栏切掉，车主看不到内容。
// 初始不带 top/bottom：由 toggle() 按方向二选一写入，避免两者同时存在互相打架
const panelStyle = ref({ left: '0px', width: '520px', maxHeight: '360px' });

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

// 【性能铁律 v1.8.33.14】顶栏元素只找一次。
// 以前每次点开面板都要 querySelector('header, .tb-topbar') + getBoundingClientRect()，
// 方控页同时挂着 14 个 ActionSelect，每次点击都要重复这套 DOM 查询与强制布局。
// 顶栏在整个会话里不会换，缓存住即可（onMounted 取一次，取不到就按 0 处理）。
let cachedTopBar = null;
let topBarResolved = false;

function resolveTopBar() {
  if (topBarResolved) return cachedTopBar;
  topBarResolved = true;
  cachedTopBar = document.querySelector('header, .tb-topbar');
  return cachedTopBar;
}

function panelBounds() {
  const rect = rootEl.value?.getBoundingClientRect();
  if (!rect) return null;
  // 实际可用的上下空间 = 视口内、且不被顶栏压住的那一段。
  // 顶栏是 flex 流内元素，这里用它的下沿作为上边界，下边界就是视口底部。
  const topBar = resolveTopBar();
  const topBound = topBar ? topBar.getBoundingClientRect().bottom : 0;
  return {
    rect,
    vw: window.innerWidth,
    vh: window.innerHeight,
    spaceUp: rect.top - topBound - GAP - EDGE,
    spaceDown: window.innerHeight - rect.bottom - GAP - EDGE
  };
}

function toggle() {
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

  // 【一次定位铁律 v1.8.33.14】不再「先写一次样式 → nextTick → 读 offsetHeight → 再写一次」。
  // 那套二次校正会让浏览器在面板刚出现时强制同步布局一次（面板含 8 个磁贴 + shadow，
  // 车机上这一下就是肉眼可见的卡顿与延迟），而且两次 setStyle 之间面板还会闪一下位置。
  // 现在改成：面板用 flex 布局 + maxHeight，方向由 useUp 决定（向上就用 bottom 锚定），
  // 几何上天然保证完整落在可视区内，浏览器内部自己算高度，不需要我们读回来。
  const anchor = useUp
      ? {
          left: Math.round(left) + 'px',
          bottom: Math.round(Math.max(EDGE, vh - rect.top + GAP)) + 'px',
          width: Math.round(width) + 'px',
          maxHeight: Math.round(Math.max(MIN_H, rect.top - GAP - EDGE)) + 'px'
        }
      : {
          left: Math.round(left) + 'px',
          top: Math.round(Math.max(EDGE, rect.bottom + GAP)) + 'px',
          width: Math.round(width) + 'px',
          maxHeight: Math.round(Math.max(MIN_H, Math.min(420, vh - rect.bottom - GAP - EDGE))) + 'px'
        };
  panelStyle.value = anchor;
  open.value = true;
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

// 单例分发入口：未打开时第一行直接返回，14 个实例里最多只有 1 个会继续往下走
function instanceCloser(e) {
  if (!open.value) return;
  if (e === null) { onScrollClose(); return; }
  onDocClick(e);
}

// 【全局监听单例铁律 v1.8.33.14】
// 以前每个 ActionSelect 实例在 onMounted 里各挂 3 个全局监听器：
//   document click(冒泡) + window scroll(capture) + window resize。
// 方控页同屏 14 个实例 => 14 个 click + 14 个 scroll(capture) + 14 个 resize。
// scroll 用 capture 捕获，页面每次滚动/惯性滑动这 14 个回调全部要跑一遍才轮到真正的滚动处理，
// 车机上表现为「滑一下顿一下、点开选择框要先愣一下」。
// 现在改为模块级单例：全局只挂一套监听，内部维护活跃实例集合，事件来时只通知「当前打开的那一个」。
const activeClosers = new Set();

function onGlobalClick(e) {
  for (const c of activeClosers) c(e);
}

function onGlobalScroll() {
  for (const c of activeClosers) c(null);
}

onMounted(() => {
  if (!window.__geelyActionSelectBound) {
    window.__geelyActionSelectBound = true;
    document.addEventListener('click', onGlobalClick);
    window.addEventListener('scroll', onGlobalScroll, true);
    window.addEventListener('resize', onGlobalScroll);
  }
  // 注册实例自己的关闭判定；未打开时它第一行就返回，不做任何工作
  activeClosers.add(instanceCloser);
});

onUnmounted(() => {
  activeClosers.delete(instanceCloser);
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
