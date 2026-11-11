<template>
  <ModalWrapper
    :show="true"
    title="报文学习观察"
    badge="实时数值"
    maxWidthClass="max-w-[900px]"
    zIndexClass="z-[9999]"
    :showCloseButton="false"
    @close="handleClose"
  >
    <div class="flex flex-col py-1 space-y-4">

      <!-- 顶部状态条 -->
      <div class="w-full rounded-2xl bg-car-item border-2 border-car-accent p-4 flex items-center justify-between">
        <div class="flex items-center space-x-3">
          <StatusDot size="md" color="ok" :glow-px="10" pulse />
          <div class="flex flex-col text-left">
            <span class="text-[19px] font-black text-car-text">正在观察车身报文</span>
            <span class="text-[13.5px] text-car-sub font-bold">
              去开关车门、引擎盖、后备箱、挂挡，上面的数值会立刻跟着变
            </span>
          </div>
        </div>
        <div class="flex flex-col items-end">
          <span class="text-[13px] text-car-sub font-bold">已记录变化</span>
          <span class="text-[22px] font-mono font-black text-car-accent">{{ frames.length }} 次</span>
        </div>
      </div>

      <!-- 实时数值面板 -->
      <div class="w-full rounded-2xl bg-car-card border border-car-border p-3">
        <div class="flex items-center justify-between px-1 pb-2">
          <span class="text-[15px] font-black text-car-text">当前数值</span>
          <span class="text-[12.5px] text-car-sub font-bold">每 3 秒刷新一次，操作后稍等再看</span>
        </div>
        <div class="grid grid-cols-2 gap-2">
          <div
            v-for="s in signals"
            :key="s.name"
            class="rounded-xl bg-car-item border border-car-border px-3 py-2.5 flex items-center justify-between"
          >
            <span class="text-[13.5px] text-car-sub font-black">{{ s.name }}</span>
            <div class="flex flex-col items-end">
              <span class="text-[17px] font-mono font-black text-car-accent">{{ s.value }}</span>
              <span class="text-[11.5px] text-car-sub font-bold">{{ s.meaning }}</span>
            </div>
          </div>
        </div>
        <div v-if="signals.length === 0" class="text-center py-6 text-[14px] text-car-sub font-bold">
          还没有收到报文，请先开着这个界面，去开关一次车门
        </div>
      </div>

      <!-- 变化流水 -->
      <div class="w-full rounded-2xl bg-car-card border border-car-border p-3">
        <div class="flex items-center justify-between px-1 pb-2">
          <span class="text-[15px] font-black text-car-text">变化记录</span>
          <span class="text-[12.5px] text-car-sub font-bold">只记有变化的，重复的不刷屏</span>
        </div>
        <div class="max-h-[280px] overflow-y-auto space-y-1.5 pr-1">
          <div
            v-for="f in recentFrames"
            :key="f.seq"
            class="rounded-xl bg-car-item border border-car-border px-3 py-2 flex items-center justify-between"
          >
            <div class="flex items-center space-x-3">
              <span class="text-[11.5px] font-mono text-car-sub font-bold w-[64px]">{{ fmtTime(f.t) }}</span>
              <span class="text-[13px] font-mono text-car-text font-black">{{ f.b6 }}</span>
              <span class="text-[13px] font-mono text-car-text font-black">{{ f.b7 }}</span>
            </div>
            <div class="flex items-center space-x-2 text-[12.5px] font-bold">
              <span class="text-car-sub">{{ f.doors }}</span>
              <span
                class="px-2 py-0.5 rounded-lg border"
                :class="f.hood === '开启'
                  ? 'bg-car-accent-light border-car-accent text-car-accent'
                  : 'bg-car-card border-car-border text-car-sub'"
              >引擎盖{{ f.hood }}</span>
              <span
                class="px-2 py-0.5 rounded-lg border"
                :class="f.trunk === '开启'
                  ? 'bg-car-accent-light border-car-accent text-car-accent'
                  : 'bg-car-card border-car-border text-car-sub'"
              >后备箱{{ f.trunk }}</span>
            </div>
          </div>
          <div v-if="frames.length === 0" class="text-center py-6 text-[14px] text-car-sub font-bold">
            暂无变化记录
          </div>
        </div>
      </div>

      <!-- 说明 -->
      <div class="w-full rounded-xl bg-car-item border border-car-border px-4 py-3 text-[13px] text-car-sub font-bold leading-relaxed">
        这个界面不设倒计时，看够了自己关。它只旁路观察，不会改动车上任何设置，
        也不会影响车门和后备箱的正常播报。
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-between w-full">
        <button
          @click="handleClear"
          class="min-h-[60px] px-6 rounded-2xl bg-car-item border-2 border-car-border text-car-sub hover:text-car-text font-black text-[17px] cursor-pointer hover:border-car-border-light shadow-sm"
        >
          清空记录
        </button>
        <div class="flex items-center space-x-4">
          <button
            @click="handleExport"
            class="min-h-[60px] px-7 rounded-2xl bg-car-item border-2 border-car-border text-car-sub hover:text-car-text font-black text-[17px] cursor-pointer hover:border-car-border-light shadow-sm"
          >
            复制数值
          </button>
          <button
            @click="handleClose"
            class="min-h-[60px] px-9 rounded-2xl bg-car-item border-2 border-car-accent text-car-text ring-2 ring-car-accent/25 font-black text-[18px] cursor-pointer shadow-md"
          >
            关闭
          </button>
        </div>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import { closeModal, showToast } from '../../store';
import ModalWrapper from './ModalWrapper.vue';
import StatusDot from '../StatusDot.vue';

const emit = defineEmits(['close']);

const signals = ref([]);
const frames = ref([]);
const active = ref(false);

// 只展示最近 30 条，多了滚动查看即可，不必把 DOM 撑爆
const recentFrames = computed(() => frames.value.slice().reverse().slice(0, 30));

function fmtTime(ts) {
  try {
    const d = new Date(ts);
    const p = (n) => String(n).padStart(2, '0');
    return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
  } catch (e) {
    return '--:--:--';
  }
}

function applySnapshot(snap) {
  if (!snap) return;
  if (Array.isArray(snap.signals)) signals.value = snap.signals;
  if (Array.isArray(snap.frames)) frames.value = snap.frames;
}

// 原生侧每次发现字节变化就会推一次（已做 250ms 节流，不会打爆页面）
window.onFrameLearningUpdate = (snap) => {
  try {
    applySnapshot(snap);
  } catch (e) {}
};

function startSession() {
  try {
    const ok = window.ToolboxBridge.startFrameLearning();
    active.value = !!ok;
    if (!ok) {
      showToast('观察通道没打开，请稍后重试', 'danger');
      return;
    }
    // 立刻拉一次，避免开界面后空白等 3 秒
    pullSnapshot();
  } catch (e) {
    active.value = false;
  }
}

function pullSnapshot() {
  try {
    const raw = window.ToolboxBridge.getFrameLearningSnapshot();
    if (raw) applySnapshot(JSON.parse(raw));
  } catch (e) {}
}

function handleClear() {
  try {
    window.ToolboxBridge.clearFrameLearning();
  } catch (e) {}
  signals.value = [];
  frames.value = [];
  showToast('记录已清空', 'success');
}

function handleExport() {
  try {
    const lines = [];
    lines.push('=== 车身报文数值快照 ===');
    signals.value.forEach(s => lines.push(`${s.name} = ${s.value}  (${s.meaning})`));
    lines.push('');
    lines.push('=== 变化记录 ===');
    frames.value.slice().reverse().forEach(f => {
      lines.push(`${fmtTime(f.t)}  ${f.b6} ${f.b7}  ${f.doors}  引擎盖${f.hood}  后备箱${f.trunk}`);
    });
    const text = lines.join('\n');
    // 车机 WebView 没有剪贴板权限，退化为把文本塞进一个临时 textarea 再选中
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    document.execCommand('copy');
    document.body.removeChild(ta);
    showToast('数值已复制，可直接粘给我', 'success');
  } catch (e) {
    showToast('复制失败', 'danger');
  }
}

function handleClose() {
  try {
    window.ToolboxBridge.stopFrameLearning();
  } catch (e) {}
  active.value = false;
  delete window.onFrameLearningUpdate;
  closeModal('frameLearning');
  emit('close');
}

onMounted(() => {
  startSession();
});

onBeforeUnmount(() => {
  try {
    window.ToolboxBridge.stopFrameLearning();
  } catch (e) {}
  delete window.onFrameLearningUpdate;
});
</script>
