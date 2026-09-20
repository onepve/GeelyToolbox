<template>
  <ModalWrapper 
    :show="store.modals.blogGuide" 
    title="缤越玩车避坑指南 · 官方图文教程" 
    badge="onepve.com"
    maxWidthClass="max-w-[720px]"
    @close="closeModal('blogGuide')"
  >
    <div class="flex flex-col items-center text-center py-2 space-y-5">
      <!-- 顶部提示说明 -->
      <div class="text-[15.5px] text-car-sub font-bold leading-relaxed px-4">
        车机屏幕不便长篇阅读，推荐使用手机扫码查阅图文详解。<br/>
        包含缤越 COOL 双联屏原厂白名单解密、高德车机版飞屏与方控按键定制全攻略。
      </div>

      <!-- 二维码高对比展示卡片 -->
      <div class="p-5 bg-white rounded-3xl shadow-xl border-4 border-car-border flex flex-col items-center justify-center">
        <canvas ref="canvasRef" width="220" height="220" class="rounded-xl"></canvas>
        <div class="flex items-center space-x-3 mt-3">
          <span class="text-black font-mono font-bold text-[14px] select-none">
            {{ blogUrl }}
          </span>
          <button 
            @click="copyUrl"
            class="px-3 py-1 rounded-lg bg-gray-100 hover:bg-gray-200 text-gray-800 text-[13px] font-bold border border-gray-300 cursor-pointer shadow-xs"
          >
            复制
          </button>
        </div>
      </div>

      <!-- 操作流程提示 -->
      <div class="text-[16px] text-car-text font-black">
        📱 打开手机微信 / 浏览器扫一扫 ➔ 边看教程边在车机操作
      </div>
    </div>
  </ModalWrapper>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue';
import QRCode from 'qrcode';
import ModalWrapper from './ModalWrapper.vue';
import { store, closeModal, showToast } from '../../store';

const canvasRef = ref(null);
const blogUrl = 'https://onepve.com/geely-toolbox/';

function renderQrCode() {
  if (!canvasRef.value) return;
  QRCode.toCanvas(canvasRef.value, blogUrl, {
    width: 220,
    margin: 1,
    color: {
      dark: '#000000',
      light: '#FFFFFF'
    }
  }, (err) => {
    if (err) console.error('QR code render error:', err);
  });
}

watch(() => store.modals.blogGuide, (show) => {
  if (show) {
    nextTick(() => {
      renderQrCode();
      setTimeout(renderQrCode, 150);
    });
  }
});

function copyUrl() {
  navigator.clipboard.writeText(blogUrl).then(() => {
    showToast('教程直达链接已复制');
  }).catch(() => {
    showToast(blogUrl);
  });
}
</script>
