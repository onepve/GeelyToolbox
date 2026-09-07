<template>
  <ModalWrapper 
    :show="!!store.modals.confirm" 
    :title="confirmData?.title || '操作确认'" 
    :badge="confirmData?.isDanger ? '高危注意' : '二次确认'"
    maxWidthClass="max-w-[720px]"
    @close="closeModal('confirm')"
  >
    <div v-if="confirmData" class="flex flex-col gap-4">
      <div 
        v-if="confirmData.isDanger"
        class="bg-rose-500/10 border border-rose-500/30 rounded-xl p-4 text-rose-500 text-[16px] font-bold"
      >
        <b>【高危操作警告】</b>该操作涉及整车底层硬件或系统服务，请确保已充分知晓操作影响！
      </div>

      <div class="text-[18px] text-car-text font-bold leading-relaxed whitespace-pre-wrap">
        {{ confirmData.desc }}
      </div>

      <div v-if="confirmData.tip" class="text-[15px] text-car-sub font-semibold">
        {{ confirmData.tip }}
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-end gap-3 w-full">
        <button 
          @click="closeModal('confirm')"
          class="min-h-[54px] px-8 rounded-xl bg-car-item border border-car-border text-car-sub hover:text-car-text font-black text-[17px] cursor-pointer"
        >
          取消
        </button>
        <button 
          @click="handleConfirm"
          :class="[
            'min-h-[54px] px-8 rounded-xl font-black text-[17px] cursor-pointer transition-all',
            confirmData?.isDanger 
              ? 'bg-rose-500/15 border-2 border-rose-500 text-rose-500 hover:bg-rose-500/25' 
              : 'bg-car-item border-2 border-car-accent text-car-text ring-2 ring-car-accent/20'
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
  if (confirmData.value && typeof confirmData.value.onConfirm === 'function') {
    confirmData.value.onConfirm();
  }
  closeModal('confirm');
}
</script>
