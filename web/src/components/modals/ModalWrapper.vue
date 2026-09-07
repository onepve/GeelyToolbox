<template>
  <transition name="modal-fade">
    <div 
      v-if="show" 
      class="fixed inset-0 z-50 flex items-center justify-center p-6 bg-black/75 backdrop-blur-sm select-none"
      @click.self="handleBackdropClick"
    >
      <div 
        :class="[
          'bg-car-card border border-car-border rounded-3xl shadow-2xl overflow-hidden flex flex-col transition-all duration-200 w-full',
          maxWidthClass || 'max-w-[960px]',
          maxHeightClass || 'max-h-[88vh]'
        ]"
      >
        <!-- 弹窗标题栏 -->
        <div class="h-[68px] px-6 border-b border-car-border flex items-center justify-between shrink-0 bg-car-card">
          <div class="text-[22px] font-black text-car-text tracking-wide flex items-center gap-3">
            <span>{{ title }}</span>
            <span v-if="badge" class="text-[12px] px-2.5 py-0.5 rounded-full border border-car-border bg-car-item text-car-sub font-extrabold">
              {{ badge }}
            </span>
          </div>
          <button 
            @click="close"
            class="h-[40px] px-4 rounded-xl bg-car-item border border-car-border text-car-sub hover:text-car-text font-black text-[15px] cursor-pointer hover:border-car-border-light transition-all"
          >
            关闭
          </button>
        </div>

        <!-- 弹窗主体内容 (独立平滑滚动) -->
        <div class="flex-1 overflow-y-auto p-6 flex flex-col gap-5">
          <slot />
        </div>

        <!-- 底部可选操作栏 -->
        <div v-if="$slots.footer" class="px-6 py-4 border-t border-car-border bg-car-card shrink-0 flex items-center justify-end gap-3">
          <slot name="footer" />
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { playTouchFeedback } from '../../utils/sound';

const props = defineProps({
  show: Boolean,
  title: String,
  badge: String,
  maxWidthClass: String,
  maxHeightClass: String,
  closeOnBackdrop: {
    type: Boolean,
    default: true
  }
});

const emit = defineEmits(['close']);

function close() {
  playTouchFeedback();
  emit('close');
}

function handleBackdropClick() {
  if (props.closeOnBackdrop) {
    close();
  }
}
</script>

<style scoped>
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
  transform: scale(0.97);
}
</style>
