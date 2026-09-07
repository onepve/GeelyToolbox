<template>
  <transition name="modal-fade">
    <div 
      v-if="show" 
      class="fixed inset-0 z-50 flex items-center justify-center p-6 bg-black/80 backdrop-blur-md select-none"
      @click.self="handleBackdropClick"
    >
      <div 
        :class="[
          'bg-car-card border border-car-border rounded-3xl shadow-2xl overflow-hidden flex flex-col transition-all duration-200 w-full',
          maxWidthClass || 'max-w-[1000px]',
          maxHeightClass || 'max-h-[90vh]'
        ]"
      >
        <!-- 弹窗标题栏 (车规加高 80px) -->
        <div class="h-[80px] px-8 border-b border-car-border flex items-center justify-between shrink-0 bg-car-card">
          <div class="text-[25px] font-black text-car-text tracking-wide flex items-center gap-3.5">
            <span>{{ title }}</span>
            <span v-if="badge" class="text-[13.5px] px-3 py-1 rounded-full border border-car-border bg-car-item text-car-sub font-black">
              {{ badge }}
            </span>
          </div>
          <button 
            @click="close"
            class="h-[56px] px-6 rounded-2xl bg-car-item border-2 border-car-border text-car-sub hover:text-car-text font-black text-[18px] cursor-pointer hover:border-car-border-light transition-all shadow-sm"
          >
            关闭
          </button>
        </div>

        <!-- 弹窗主体内容 (独立平滑滚动，内容与按钮大大大) -->
        <div class="flex-1 overflow-y-auto p-8 flex flex-col gap-6">
          <slot />
        </div>

        <!-- 底部可选操作栏 (车规加高 84px) -->
        <div v-if="$slots.footer" class="px-8 py-5 border-t border-car-border bg-car-card shrink-0 flex items-center justify-end gap-4">
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
