<template>
  <ModalWrapper
    :show="!!store.modals.confirm"
    :title="confirmData?.title || '操作确认'"
    :badge="confirmData?.isDanger ? '高危操作' : '安全确认'"
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

      <div class="text-[21px] text-car-text font-black leading-relaxed whitespace-pre-wrap">
        {{ confirmData.desc }}
      </div>

      <div v-if="confirmData.tip" class="text-[17px] text-car-sub font-extrabold bg-car-item p-4 rounded-xl border border-car-border">
        {{ confirmData.tip }}
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-end space-x-4 w-full">
        <button 
          @click="closeModal('confirm')"
          class="min-h-[68px] px-10 rounded-2xl bg-car-item border-2 border-car-border text-car-sub hover:text-car-text font-black text-[20px] cursor-pointer hover:border-car-border-light shadow-sm"
        >
          取消
        </button>
        <button 
          @click="handleConfirm"
          :class="[
            'min-h-[68px] px-12 rounded-2xl font-black text-[21px] cursor-pointer transition-all shadow-md',
            confirmData?.isDanger 
              ? 'bg-rose-500/20 border-2 border-rose-500 text-rose-500 hover:bg-rose-500/30' 
              : 'bg-car-item border-2 border-car-accent text-car-text ring-2 ring-car-accent/25'
          ]"
        >
          确认执行
        </button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { computed } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, closeModal } from '../../store';

const confirmData = computed(() => store.modals.confirm);

function handleConfirm() {
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
