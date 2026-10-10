<template>
  <Teleport to="body">
    <transition name="modal-backdrop-fade">
    <div 
      v-if="show" 
      :class="['fixed top-0 left-0 w-screen h-screen flex items-center justify-center p-6 select-none', zIndexClass || 'z-50']"
      style="position: fixed; top: 0; left: 0; right: 0; bottom: 0; width: 100vw; height: 100vh; background-color: rgba(0, 0, 0, 0.65);"
      @click.self="handleBackdropClick"
    >
      <div 
        :class="[
          'tb-modal-surface rounded-3xl overflow-hidden flex flex-col w-full shadow-2xl modal-card-anim',
          'border border-car-border/80',
          maxWidthClass || 'max-w-[1000px]',
          maxHeightClass || 'max-h-[90vh]'
        ]"
      >
        <!-- 弹窗标题栏 (车规加高 80px：标题左区独占可自动换行完整显示，徽标+关闭按钮右区成组永不挤压错位) -->
        <div class="min-h-[80px] px-8 py-4 border-b border-car-border flex items-center justify-between shrink-0 tb-modal-surface">
          <div class="flex-1 min-w-0 text-[25px] font-black text-car-text tracking-wide leading-[1.3]">{{ title }}</div>
          <div class="shrink-0 ml-5 flex items-center space-x-3">
            <span v-if="badge" class="text-[13.5px] px-3 py-1.5 rounded-full border border-car-border bg-car-item text-car-sub font-black whitespace-nowrap">
              {{ badge }}
            </span>
            <button 
              v-if="showCloseButton !== false"
              @click="close"
              class="h-[56px] px-6 rounded-2xl bg-car-item border-2 border-car-border text-car-sub hover:text-car-text font-black text-[18px] cursor-pointer hover:border-car-border-light transition-colors shadow-sm"
            >
              关闭
            </button>
          </div>
        </div>

        <!-- 弹窗主体内容 (独立平滑滚动，实体隔离彻底杜绝 Android 9 gap 塌陷与透视穿帮) -->
        <div class="flex-1 overflow-y-auto p-8 space-y-6 tb-modal-surface">
          <slot />
        </div>

        <!-- 底部可选操作栏 (车规加高 84px) -->
        <div v-if="$slots.footer" class="px-8 py-5 border-t border-car-border tb-modal-surface shrink-0 flex items-center justify-end space-x-4">
          <slot name="footer" />
        </div>
      </div>
    </div>
    </transition>
  </Teleport>
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
/* 弹窗专用实底（二级/三级弹窗铁律）：
   弹窗是叠在页面内容之上的独立层，必须用 --bg-modal（96% 高实度）而不是 --bg-card。
   --bg-card 是「贴在实底页面上的玻璃卡」，夜间仅 13% 实度，用在弹窗上会让背后
   页面内容直接透上来，车主观感就是「弹窗过于透明、字看不清」。 */
.tb-modal-surface {
  background: var(--bg-modal, rgba(20, 27, 43, 0.96));
}

/* 动静分离铁律：
   1. 100vw × 100vh 全屏遮罩仅做 opacity 纯透明度渐变，严禁施加 scale 缩放，
      杜绝 1920×720 全屏像素逐帧重采样与 Mali GPU 算力打满；
   2. 弹窗卡片本体容器单独微缩放，硬件合成层极速推进，手感瞬时跟手。 */
.modal-backdrop-fade-enter-active,
.modal-backdrop-fade-leave-active {
  transition: opacity 0.2s cubic-bezier(0.22, 0.61, 0.36, 1);
  will-change: opacity;
}

.modal-backdrop-fade-enter-from,
.modal-backdrop-fade-leave-to {
  opacity: 0;
}

.modal-backdrop-fade-enter-active .modal-card-anim {
  animation: modalCardIn 0.2s cubic-bezier(0.22, 0.61, 0.36, 1) forwards;
}

.modal-backdrop-fade-leave-active .modal-card-anim {
  animation: modalCardOut 0.16s cubic-bezier(0.22, 0.61, 0.36, 1) forwards;
}

@keyframes modalCardIn {
  from {
    opacity: 0;
    transform: scale(0.97) translateZ(0);
  }
  to {
    opacity: 1;
    transform: scale(1) translateZ(0);
  }
}

@keyframes modalCardOut {
  from {
    opacity: 1;
    transform: scale(1) translateZ(0);
  }
  to {
    opacity: 0;
    transform: scale(0.97) translateZ(0);
  }
}
</style>
