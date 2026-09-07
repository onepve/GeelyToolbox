<template>
  <ModalWrapper 
    :show="store.modals.reward" 
    title="赞赏支持 · 纯净座舱生态开源" 
    badge="开发者致谢"
    maxWidthClass="max-w-[700px]"
    @close="closeModal('reward')"
  >
    <div class="flex flex-col items-center text-center py-2 space-y-4">
      <div class="text-[16px] text-car-sub font-bold">
        感谢你对《吉利智驾 (GeelyPilot)》独立座舱生态与开源改装的支持与喜爱！
      </div>

      <!-- 赞赏卡片展示区 -->
      <div class="p-6 bg-white rounded-3xl shadow-xl border-4 border-car-border flex flex-col items-center justify-center">
        <!-- 纯净赞赏 SVG 矢量图标与致谢徽标 -->
        <div class="w-48 h-48 rounded-2xl bg-[#0F172A] p-4 flex flex-col items-center justify-center text-center shadow-inner">
          <span class="text-[44px] mb-2">☕</span>
          <span class="text-amber-400 font-black text-[17px]">为开发者请一杯咖啡</span>
          <span class="text-slate-400 text-[13px] font-bold mt-1">感谢一路同行的吉利车友</span>
        </div>
        <div class="text-black font-black text-[17px] mt-4 flex items-center">
          <span class="mr-1.5">💖</span> 微信 / 支付宝 / QQ 交流群：564654011
        </div>
      </div>

      <!-- 提示语 -->
      <div class="text-[14.5px] text-car-sub font-bold leading-relaxed px-4">
        本软件由车友利用业余时间纯手工打造，永久承诺 0 广告、0 商业捆绑、100% 免费开源，你的每一次鼓励都是我们持续演进座舱的动力！
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-center w-full">
        <button 
          @click="closeModal('reward')"
          class="min-h-[58px] px-12 bg-car-item border-2 border-car-border rounded-xl text-car-text font-black text-[18px] cursor-pointer hover:border-car-border-light shadow-sm"
        >
          关闭
        </button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import ModalWrapper from './ModalWrapper.vue';
import { store, closeModal } from '../../store';
</script>
