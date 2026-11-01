<template>
  <ModalWrapper
    :show="true"
    title="方控按键学习"
    badge="新车型适配"
    maxWidthClass="max-w-[760px]"
    zIndexClass="z-[9999]"
    :showCloseButton="false"
    @close="handleCancel"
  >
    <div class="flex flex-col py-2 space-y-6 items-center text-center">

      <!-- 阶段一：等待车主按键 -->
      <template v-if="stage === 'waiting'">
        <div class="w-full bg-car-item rounded-2xl border-2 border-car-border p-6 space-y-4">
          <div class="flex items-center justify-center space-x-3">
            <StatusDot size="lg" color="ok" :glow-px="10" />
            <span class="text-[22px] font-black text-car-text tracking-wide">正在聆听方向盘按键...</span>
          </div>
          <div class="text-[17px] text-car-sub font-bold leading-relaxed">
            请现在按下方向盘上您想学习的任意物理按键，<br />
            助手会立即抓取它的真实键码。
          </div>
          <div class="text-[15px] text-car-sub font-bold">
            学习期间该按键不会触发任何已配置的功能，请放心按。
          </div>
        </div>

        <!-- 超时进度条（车规可视化：秒级倒计时） -->
        <div class="w-full max-w-[420px] space-y-2">
          <div class="h-[14px] w-full rounded-full bg-car-item border border-car-border overflow-hidden">
            <div
              class="h-full rounded-full bg-car-accent transition-all duration-300"
              :style="{ width: progressPct + '%' }"
            ></div>
          </div>
          <div class="text-[14.5px] text-car-sub font-black">
            {{ secondsLeft }} 秒后自动结束（共 {{ TOTAL_SECONDS }} 秒）
          </div>
        </div>
      </template>

      <!-- 阶段二：学习成功 -->
      <template v-else-if="stage === 'captured'">
        <div class="w-full bg-car-item rounded-2xl border-2 border-car-accent p-6 space-y-4">
          <div class="flex items-center justify-center space-x-3">
            <span class="text-[38px]">✅</span>
            <span class="text-[22px] font-black text-car-text tracking-wide">已成功识别按键</span>
          </div>
          <div class="flex items-center justify-center space-x-4 pt-1">
            <span class="text-[17px] text-car-sub font-black">识别结果：</span>
            <span class="text-[19px] font-black text-car-text bg-car-card px-4 py-2 rounded-xl border-2 border-car-border">
              {{ friendlyName }}
            </span>
            <span class="text-[15px] text-car-sub font-black">键码 {{ capturedCode }}</span>
          </div>
        </div>
        <div class="text-[15.5px] text-car-sub font-bold leading-relaxed max-w-[560px]">
          {{ capturedHint }}
        </div>
      </template>

      <!-- 阶段三：超时未捕获 -->
      <template v-else>
        <div class="w-full bg-car-item rounded-2xl border-2 border-car-border p-6 space-y-4">
          <div class="flex items-center justify-center space-x-3">
            <span class="text-[34px]">⏰</span>
            <span class="text-[22px] font-black text-car-text tracking-wide">未捕捉到按键</span>
          </div>
          <div class="text-[16.5px] text-car-sub font-bold leading-relaxed">
            20 秒内没有检测到方向盘按键。<br />
            可以重试一次；若始终识别不到，可能当前车型暂不支持，请反馈给作者适配。
          </div>
        </div>
      </template>
    </div>

    <template #footer>
      <div class="flex items-center justify-end space-x-4 w-full">
        <button
          @click="handleCancel"
          class="min-h-[64px] px-8 rounded-2xl bg-car-item border-2 border-car-border text-car-sub hover:text-car-text font-black text-[19px] cursor-pointer hover:border-car-border-light shadow-sm"
        >
          关闭
        </button>
        <button
          v-if="stage === 'waiting'"
          @click="handleRestart"
          class="min-h-[64px] px-10 rounded-2xl bg-car-item border-2 border-car-border text-car-text font-black text-[19px] cursor-pointer hover:border-car-border-light shadow-sm"
        >
          重新开始
        </button>
        <button
          v-if="stage !== 'waiting'"
          @click="handleRestart"
          class="min-h-[64px] px-10 rounded-2xl bg-car-item border-2 border-car-accent text-car-text ring-2 ring-car-accent/25 font-black text-[19px] cursor-pointer shadow-md"
        >
          再学一个按键
        </button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import { closeModal } from '../../store';
import ModalWrapper from './ModalWrapper.vue';
import StatusDot from '../StatusDot.vue';

const emit = defineEmits(['close']);

const TOTAL_SECONDS = 20;
const stage = ref('waiting'); // waiting | captured | timeout
const secondsLeft = ref(TOTAL_SECONDS);
const capturedCode = ref(0);
let countdownTimer = null;

const progressPct = computed(() => Math.max(0, Math.round((secondsLeft.value / TOTAL_SECONDS) * 100)));

// 已知键码 -> 大白话名称（与助手既有键位表一致）
const KEY_NAMES = {
  300: '静音键', 301: '音量减/滚轮下拨', 302: '音量加/滚轮上拨',
  304: '上一曲', 305: '下一曲', 306: '滚轮按压确认', 307: '主页/返回键',
  286: '语音按键', 287: '电话按键', 348: 'MODE 键', 349: '自定义菱形键',
  45: '滚轮按压确认', 66: '滚轮按压确认'
};
const friendlyName = computed(() => KEY_NAMES[capturedCode.value] || '未知新键位');

const capturedHint = computed(() => {
  if (KEY_NAMES[capturedCode.value]) {
    return '这是助手已支持的按键，可直接在「按键映射手势」页为它配置功能。';
  }
  return '这是当前车型的新键位！键码已自动记录，反馈日志给作者后即可在后续版本中适配该按键。';
});

function stopCountdown() {
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null; }
}

function startSession() {
  stage.value = 'waiting';
  secondsLeft.value = TOTAL_SECONDS;
  stopCountdown();
  try { window.ToolboxBridge.stopWheelKeyLearning(); } catch (e) {}
  const ok = (() => {
    try { return window.ToolboxBridge.startWheelKeyLearning(); } catch (e) { return false; }
  })();
  if (!ok) {
    stage.value = 'timeout';
    return;
  }
  countdownTimer = setInterval(() => {
    secondsLeft.value -= 1;
    if (secondsLeft.value <= 0) {
      stopCountdown();
      stage.value = 'timeout';
      try { window.ToolboxBridge.stopWheelKeyLearning(); } catch (e) {}
    }
  }, 1000);
}

function handleRestart() {
  startSession();
}

function handleCancel() {
  stopCountdown();
  try { window.ToolboxBridge.stopWheelKeyLearning(); } catch (e) {}
  closeModal('wheelKeyLearning');
  emit('close');
}

// 原生捕获到按键后回调（由 Java 侧 evaluateJavascript 触发）
window.onWheelKeyLearned = (payload) => {
  try {
    const code = parseInt(payload && payload.keyCode, 10);
    if (!isNaN(code) && code > 0) {
      stopCountdown();
      capturedCode.value = code;
      stage.value = 'captured';
    }
  } catch (e) {}
};

onMounted(() => {
  startSession();
});

onBeforeUnmount(() => {
  stopCountdown();
  delete window.onWheelKeyLearned;
  try { window.ToolboxBridge.stopWheelKeyLearning(); } catch (e) {}
});
</script>
