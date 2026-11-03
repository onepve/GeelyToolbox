<template>
  <ModalWrapper 
    :show="store.modals.dialer" 
    title="今日车机动态工程暗码" 
    badge="动态对时"
    maxWidthClass="max-w-[820px]"
    @close="closeModal('dialer')"
  >
    <div class="flex flex-col space-y-5">
      <!-- 核心双轨暗码展示 -->
      <div class="flex space-x-4">
        <!-- 主算法 (+10) 缤越COOL首选 -->
        <div class="flex-1 bg-car-item border-2 border-car-accent rounded-2xl p-5 flex flex-col space-y-2 ring-2 ring-car-accent/20">
          <div class="flex items-center justify-between">
            <span class="text-[17px] font-black text-car-text">主算法 (+10 模式)</span>
            <span class="text-[12px] px-2 py-0.5 rounded bg-car-card text-car-accent font-extrabold border border-car-accent">
              缤越COOL 专车推荐
            </span>
          </div>
          <div class="text-[36px] font-black font-mono text-car-text tracking-wider my-1">
            {{ store.dynamicCode }}
          </div>
          <span class="text-[14px] text-car-sub font-bold">吉利缤越COOL 2022 / 银河OS E02 标准算法</span>
        </div>

        <!-- 备用算法 (+5) 早期GKUI -->
        <div class="flex-1 bg-car-item border border-car-border rounded-2xl p-5 flex flex-col space-y-2">
          <div class="flex items-center justify-between">
            <span class="text-[17px] font-black text-car-text">备用算法 (+5 模式)</span>
            <span class="text-[12px] px-2 py-0.5 rounded bg-car-card text-car-sub font-extrabold border border-car-border">
              早期固件兼容
            </span>
          </div>
          <div class="text-[36px] font-black font-mono text-car-text tracking-wider my-1 opacity-80">
            {{ store.dynamicCodePlus5 }}
          </div>
          <span class="text-[14px] text-car-sub font-bold">老款博越/帝豪/早期 GKUI 固件备用</span>
        </div>
      </div>

      <!-- 温馨提示 -->
      <div class="bg-car-item border border-car-border rounded-xl p-4 text-[16px] text-car-sub font-bold leading-relaxed">
        <b>【输入说明】</b>车机拨号盘为硬件按键模拟，<b>不支持剪贴板长按粘贴</b>；请点击下方按钮打开车机电话，在拨号键盘直接手动按键输入上方暗码即可秒进工程模式！
      </div>

      <!-- 操作按钮 -->
      <button 
        @click="openPhoneDialer"
        class="w-full min-h-[66px] bg-car-item border-2 border-car-accent rounded-2xl text-car-text font-black text-[20px] cursor-pointer hover:border-car-accent shadow-lg ring-2 ring-car-accent/20"
      >
        打开车机电话拨号盘
      </button>
    </div>
  </ModalWrapper>
</template>

<script setup>
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, showToast } from '../../store';

function openPhoneDialer() {
  bridge.call('openDialer');
  showToast('正在调起车机电话拨号盘...');
  closeModal('dialer');
}
</script>
