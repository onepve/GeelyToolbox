<template>
  <ModalWrapper 
    :show="store.modals.reward" 
    title="赞赏支持 · 纯净座舱生态开源" 
    badge="开发者致谢"
    maxWidthClass="max-w-[620px]"
    @close="closeModal('reward')"
  >
    <div class="flex flex-col items-center text-center space-y-3">
      <div class="text-[15.5px] text-car-sub font-bold">
        感谢你对《吉利智驾 (GeelyToolbox)》独立座舱生态与开源改装的支持与喜爱！
      </div>

      <!-- 赞赏卡片展示区 -->
      <div class="p-4 bg-white rounded-3xl shadow-xl border-4 border-car-border flex flex-col items-center justify-center">
        <!-- 微信赞赏码实体图片 -->
        <img 
          :src="rewardQrImg" 
          alt="微信赞赏码" 
          class="w-48 h-48 object-contain rounded-xl shadow-sm"
        />
        <div class="text-slate-900 font-black text-[17px] mt-2.5 flex items-center">
          <span class="mr-1.5 text-rose-500">💖</span> 微信扫码赞赏支持 · 迷失
        </div>
        <div class="text-slate-500 font-bold text-[13px] mt-0.5">
          车友交流 QQ 群：564654011
        </div>
      </div>

      <!-- 提示语 -->
      <div class="text-[14px] text-car-sub font-bold leading-relaxed px-2">
        本软件由车友利用业余时间纯手工打造，永久承诺 0 广告、0 商业捆绑、100% 免费开源，你的每一次鼓励都是我们持续演进座舱的动力！
      </div>
    </div>
  </ModalWrapper>
</template>

<script setup>
import ModalWrapper from './ModalWrapper.vue';
import { store, closeModal } from '../../store';
import rewardQrImg from '../../assets/img_reward_code.webp';
</script>
