<template>
  <ModalWrapper
    :show="store.modals.welcomeDonate"
    title="欢迎体验缤越助手 · 作者说明"
    badge="车主互助"
    max-width-class="max-w-[1080px]"
    max-height-class="max-h-[92vh]"
    :show-close-button="false"
    :close-on-backdrop="false"
    @close="handleFinish"
  >
    <div class="flex flex-col space-y-3">
      <!-- 左右车规双栏布局 (紧凑车规单屏自适应，杜绝 720P 产生滚动条) -->
      <div class="grid grid-cols-1 md:grid-cols-12 gap-4 items-stretch">
        <!-- 左侧：两大核心说明 (精简通透版，彻底无滚动条) -->
        <div class="md:col-span-7 flex flex-col justify-around py-2 space-y-4">
          <!-- 说明 1：纯净免费承诺 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col">
            <div class="flex items-center space-x-2 text-[17px] font-black text-car-text">
              <span class="text-emerald-400">🛡️</span>
              <span>永久承诺 · 纯净免费</span>
            </div>
            <div class="text-[14px] text-car-sub font-bold leading-relaxed mt-2">
              本软件由迷失开发制作，吉利缤越cool 2022款 专车打造，其他车型仅供测试。<br>
              <span class="text-car-text font-black">完全免费使用，承诺 0 广告、0 诱导充值、0 功能锁死。</span>
            </div>
          </div>

          <!-- 说明 2：安全与免责底线 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col">
            <div class="flex items-center space-x-2 text-[17px] font-black text-car-text">
              <span class="text-amber-400">⚠️</span>
              <span>安全与免责底线</span>
            </div>
            <div class="text-[14px] text-car-sub font-bold leading-relaxed mt-2">
              各项功能直接与底层车辆协议联动。请务必在<span class="text-car-accent font-black">安全停车（P 挡）</span>状态下进行配置；行车途中严禁分心操作屏幕。
            </div>
          </div>
        </div>

        <!-- 右侧：直接复用已有赞赏码 (占 5 栏) -->
        <div class="md:col-span-5 p-4 bg-white rounded-3xl shadow-xl border-4 border-car-border flex flex-col items-center justify-center text-center">
          <img 
            :src="rewardQrImg" 
            alt="微信赞赏码" 
            class="w-44 h-44 object-contain rounded-2xl shadow-sm"
          />
          <div class="text-slate-900 font-black text-[16.5px] mt-2 flex items-center">
            <span class="mr-1 text-rose-500">💖</span> 微信扫码赞赏支持 · 迷失
          </div>
          <div class="text-slate-500 font-bold text-[13px] mt-0.5">
            吉利车友交流 QQ 群：564654011
          </div>
        </div>
      </div>

      <!-- 底部操作与提示栏 -->
      <div class="pt-4 border-t border-car-border/60 flex items-center justify-between">
        <div class="text-[13px] text-car-sub font-bold flex items-center">
          <span class="w-2 h-2 rounded-full bg-car-accent mr-2"></span>
          <span>本说明仅在首次启动时展示，后续可在【关于】页面随时查看赞赏与群号</span>
        </div>

        <button 
          @click="handleFinish"
          :disabled="countdown > 0"
          :class="[
            'min-h-[50px] px-7 font-black text-[16.5px] rounded-2xl transition-all whitespace-nowrap flex-shrink-0 flex items-center justify-center',
            countdown > 0 
              ? 'bg-car-item text-car-sub/50 border border-car-border cursor-not-allowed opacity-70' 
              : 'bg-car-accent hover:brightness-110 text-black cursor-pointer shadow-lg shadow-car-accent/25'
          ]"
        >
          <span v-if="countdown > 0">请仔细阅读说明 ({{ countdown }}s)</span>
          <span v-else>我已知晓，开启体验</span>
        </button>
      </div>
    </div>
  </ModalWrapper>
</template>

<script setup>
import { ref, watch, onUnmounted } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, closeModal } from '../../store';
import rewardQrImg from '../../assets/img_reward_code.webp';

const countdown = ref(10);
let timer = null;

function startTimer() {
  stopTimer();
  countdown.value = 10;
  timer = setInterval(() => {
    if (countdown.value > 0) {
      countdown.value--;
    } else {
      stopTimer();
    }
  }, 1000);
}

function stopTimer() {
  if (timer) {
    clearInterval(timer);
    timer = null;
  }
}

watch(() => store.modals.welcomeDonate, (show) => {
  if (show) {
    startTimer();
  } else {
    stopTimer();
  }
});

onUnmounted(() => {
  stopTimer();
});

function handleFinish() {
  if (countdown.value > 0) return;
  try {
    localStorage.setItem('has_shown_welcome_donate', 'true');
  } catch (e) {}
  closeModal('welcomeDonate');
  // 顺畅串行衔接：通知初始化向导可以安全展示，避免层级重叠
  try {
    openModal('geekInstall');
    window.dispatchEvent(new CustomEvent('welcome-donate-completed'));
  } catch (e) {}
}
</script>
