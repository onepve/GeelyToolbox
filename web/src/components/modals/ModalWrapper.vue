<template>
  <transition name="modal-fade">
    <div 
      v-if="show" 
      :class="['fixed top-0 left-0 w-screen h-screen flex items-center justify-center p-6 select-none', zIndexClass || 'z-50']"
      style="position: fixed; top: 0; left: 0; right: 0; bottom: 0; width: 100vw; height: 100vh; background: var(--modal-backdrop, rgba(11, 15, 25, 0.70));"
      @click.self="handleBackdropClick"
    >
      <div 
        :class="[
          'bg-car-card rounded-3xl overflow-hidden flex flex-col transition-all duration-200 w-full shadow-2xl',
          'border border-car-border/80',
          maxWidthClass || 'max-w-[1000px]',
          maxHeightClass || 'max-h-[90vh]'
        ]"
      >
        <!-- 弹窗标题栏 (车规加高 80px) -->
        <div class="h-[80px] px-8 border-b border-car-border flex items-center justify-between shrink-0 bg-car-card">
          <div class="text-[25px] font-black text-car-text tracking-wide flex items-center">
            <span class="mr-3.5">{{ title }}</span>
            <span v-if="badge" class="text-[13.5px] px-3 py-1 rounded-full border border-car-border bg-car-item text-car-sub font-black">
              {{ badge }}
            </span>
          </div>
          <button 
            v-if="showCloseButton !== false"
            @click="close"
            class="h-[56px] px-6 rounded-2xl bg-car-item border-2 border-car-border text-car-sub hover:text-car-text font-black text-[18px] cursor-pointer hover:border-car-border-light transition-all shadow-sm"
          >
            关闭
          </button>
        </div>

        <!-- 弹窗主体内容 (独立平滑滚动，采用 space-y-6 实体隔离彻底杜绝 Android 9 gap 塌陷) -->
        <div class="flex-1 overflow-y-auto p-8 space-y-6">
          <slot />
        </div>

        <!-- 底部可选操作栏 (车规加高 84px) -->
        <div v-if="$slots.footer" class="px-8 py-5 border-t border-car-border bg-car-card shrink-0 flex items-center justify-end space-x-4">
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
  zIndexClass: String,
  showCloseButton: {
    type: Boolean,
    default: true
  },
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
