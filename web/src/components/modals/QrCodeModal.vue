<template>
  <ModalWrapper 
    :show="store.modals.qrCode" 
    title="手机无线快传 (免插线极速推包)" 
    badge="HTTP: 8888"
    maxWidthClass="max-w-[760px]"
    @close="closeModal('qrCode')"
  >
    <div class="flex flex-col items-center text-center gap-4 py-2">
      <!-- 网络状态胶囊 -->
      <div 
        :class="[
          'px-4 py-1.5 rounded-full font-bold text-[15px]',
          isWifiConnected 
            ? 'bg-emerald-500/15 text-emerald-500 border border-emerald-500/30' 
            : 'bg-amber-500/15 text-amber-500 border border-amber-500/30'
        ]"
      >
        {{ isWifiConnected ? `局域网已连接: ${store.deviceInfo.car_ip}` : '提示: 请让手机与车机处于同一热点/Wi-Fi' }}
      </div>

      <!-- 二维码展示区 (220x220 纯白高对比) -->
      <div class="p-4 bg-white rounded-3xl shadow-xl border-4 border-car-border flex flex-col items-center justify-center">
        <canvas id="qrCodeCanvas" width="200" height="200" class="rounded-xl"></canvas>
        <span class="text-black font-mono font-black text-[15px] mt-2 select-all">
          {{ serverUrl }}
        </span>
      </div>

      <!-- 操作步骤指引 (向右箭头单行流) -->
      <div class="text-[16px] text-car-sub font-bold">
        手机连接同一 Wi-Fi ➔ 微信/相机扫一扫 ➔ 极速推包到车机 Download 目录
      </div>
    </div>
  </ModalWrapper>
</template>

<script setup>
import { computed, watch, nextTick } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, closeModal } from '../../store';

const serverUrl = computed(() => `http://${store.deviceInfo.car_ip || '127.0.0.1'}:8888`);
const isWifiConnected = computed(() => {
  const ip = store.deviceInfo.car_ip || '';
  return ip.startsWith('192.168.') || ip.startsWith('10.') || ip.startsWith('172.');
});

// 监听弹窗打开时绘制轻量离线二维码占位/Canvas
watch(() => store.modals.qrCode, (show) => {
  if (show) {
    nextTick(() => {
      drawSimpleQr();
    });
  }
});

function drawSimpleQr() {
  const canvas = document.getElementById('qrCodeCanvas');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#FFFFFF';
  ctx.fillRect(0, 0, 200, 200);
  
  // 简易车规高对比图案
  ctx.fillStyle = '#000000';
  ctx.fillRect(16, 16, 50, 50);
  ctx.clearRect(26, 26, 30, 30);
  ctx.fillRect(34, 34, 14, 14);

  ctx.fillRect(134, 16, 50, 50);
  ctx.clearRect(144, 26, 30, 30);
  ctx.fillRect(152, 34, 14, 14);

  ctx.fillRect(16, 134, 50, 50);
  ctx.clearRect(26, 144, 30, 30);
  ctx.fillRect(34, 152, 14, 14);

  // 内部微网格点阵
  for (let x = 75; x <= 125; x += 10) {
    for (let y = 75; y <= 125; y += 10) {
      if ((x + y) % 20 === 0) ctx.fillRect(x, y, 8, 8);
    }
  }
}
</script>
