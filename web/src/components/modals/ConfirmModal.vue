<template>
  <ModalWrapper
    :show="!!store.modals.confirm"
    :title="confirmData?.title || '操作确认'"
    :badge="confirmData?.isDanger ? '高危操作' : (confirmData?.badge || '功能指南')"
    maxWidthClass="max-w-[860px]"
    zIndexClass="z-[9999]"
    @close="handleCancel"
  >
    <div v-if="confirmData" class="flex flex-col py-2 space-y-5">
      <div 
        v-if="confirmData.isDanger"
        class="bg-rose-500/15 border-2 border-rose-500/40 rounded-2xl p-5 text-rose-500 text-[18px] font-black"
      >
        <b>【高危操作警告】</b>该操作涉及整车底层硬件或系统核心，请确保已充分知晓实际影响！
      </div>

      <div class="text-[18px] text-car-text font-bold leading-relaxed whitespace-pre-wrap">
        {{ confirmData.desc }}
      </div>

      <div v-if="confirmData.tip" class="text-[15.5px] text-car-sub font-bold bg-car-item p-4 rounded-xl border border-car-border leading-relaxed">
        💡 {{ confirmData.tip }}
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-end space-x-4 w-full">
        <button 
          v-if="confirmData?.showCancel !== false"
          @click="closeModal('confirm')"
          class="min-h-[64px] px-8 rounded-2xl bg-car-item border-2 border-car-border text-car-sub hover:text-car-text font-black text-[19px] cursor-pointer hover:border-car-border-light shadow-sm"
        >
          取消
        </button>
        <button 
          @click="handleConfirm"
          :disabled="countdownLeft > 0"
          :class="[
            'min-h-[64px] px-10 rounded-2xl font-black text-[19px] transition-all shadow-md',
            countdownLeft > 0
              ? 'bg-car-item border-2 border-car-border text-car-sub opacity-60 cursor-not-allowed'
              : (confirmData?.isDanger 
                  ? 'bg-rose-500/20 border-2 border-rose-500 text-rose-500 hover:bg-rose-500/30 cursor-pointer'
                  : 'bg-car-item border-2 border-car-accent text-car-text ring-2 ring-car-accent/25 cursor-pointer')
          ]"
        >
          {{ countdownLeft > 0 ? `⏳ 请仔细阅读 (${countdownLeft}s)` : (confirmData?.confirmText || (confirmData?.showCancel === false ? '我知道了' : '确认执行')) }}
        </button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { computed, ref, watch, onUnmounted } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, closeModal } from '../../store';

const confirmData = computed(() => store.modals.confirm);

// ---- 高危确认倒计时 (countdown>0 时确认键禁用置灰直至归零，防看都不看盲点) ----
const countdownLeft = ref(0);
let countdownTimer = null;

function stopCountdown() {
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null; }
  countdownLeft.value = 0;
}

function startCountdown() {
  stopCountdown();
  const secs = confirmData.value?.countdown || 0;
  if (secs > 0) {
    countdownLeft.value = secs;
    countdownTimer = setInterval(() => {
      countdownLeft.value -= 1;
      if (countdownLeft.value <= 0) stopCountdown();
    }, 1000);
  }
}

// 每次弹窗内容(重新)打开都重置倒计时；关闭即清理
watch(() => store.modals.confirm, (nv) => {
  if (nv) startCountdown(); else stopCountdown();
}, { immediate: true });

onUnmounted(stopCountdown);

function handleConfirm() {
  // 倒计时未归零时按钮已禁用，此处双重保险
  if (countdownLeft.value > 0) return;
  const cb = confirmData.value?.onConfirm;
  // 关键顺序：必须先关闭当前弹窗再执行回调！
  // 否则嵌套确认（onConfirm 内再次 openModal('confirm')）会立刻被随后的 closeModal 误杀，
  // 表现为"点确认后第二步/第三步内容消失"。
  closeModal('confirm');
  if (typeof cb === 'function') cb();
}

function handleCancel() {
  const cb = confirmData.value?.onCancel;
  closeModal('confirm');
  if (typeof cb === 'function') cb();
}
</script>
