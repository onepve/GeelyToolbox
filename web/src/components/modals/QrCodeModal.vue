<template>
  <ModalWrapper 
    :show="store.modals.qrCode" 
    title="手机无线快传 (免插线极速推包)" 
    badge="HTTP: 8888"
    maxWidthClass="max-w-[760px]"
    @close="closeModal('qrCode')"
  >
    <div class="flex flex-col items-center text-center py-2">
      <!-- 网络状态胶囊 -->
      <div 
        :class="[
          'px-5 py-2 rounded-full font-black text-[16px] mb-5 shadow-sm',
          isWifiConnected 
            ? 'bg-emerald-500/15 text-emerald-500 border-2 border-emerald-500/40' 
            : 'bg-amber-500/15 text-amber-500 border-2 border-amber-500/40'
        ]"
      >
        {{ isWifiConnected ? `局域网已连接: ${store.deviceInfo.car_ip}` : '提示: 请让手机与车机处于同一热点/Wi-Fi' }}
      </div>

      <!-- 二维码展示区 (220x220 纯白高对比) -->
      <div class="p-5 bg-white rounded-3xl shadow-xl border-4 border-car-border flex flex-col items-center justify-center mb-5">
        <canvas id="qrCodeCanvas" width="200" height="200" class="rounded-xl"></canvas>
        <span class="text-black font-mono font-black text-[16px] mt-3 select-all">
          {{ serverUrl }}
        </span>
      </div>

      <!-- 操作步骤指引 (向右箭头单行流) -->
      <div class="text-[17px] text-car-sub font-bold">
        手机连接同一 Wi-Fi ➔ 微信/相机扫一扫 ➔ 极速推包到车机 Download 目录
      </div>
    </div>
  </ModalWrapper>
</template>

<script setup>
import { computed, watch, nextTick } from 'vue';
import QRCode from 'qrcode';
import ModalWrapper from './ModalWrapper.vue';
import { store, closeModal } from '../../store';

const serverUrl = computed(() => `http://${store.deviceInfo.car_ip || '127.0.0.1'}:8888`);
const isWifiConnected = computed(() => {
  const ip = store.deviceInfo.car_ip || '';
  return ip.startsWith('192.168.') || ip.startsWith('10.') || ip.startsWith('172.');
});

// 监听弹窗打开时渲染真实标准 QR Code 二维码 (手机相机/微信秒扫)
watch(() => store.modals.qrCode, (show) => {
  if (show) {
    nextTick(() => {
      renderQrCode();
    });
  }
});

function renderQrCode() {
  const canvas = document.getElementById('qrCodeCanvas');
  if (!canvas) return;
  QRCode.toCanvas(canvas, serverUrl.value, {
    width: 200,
    margin: 1,
    color: {
      dark: '#000000',
      light: '#FFFFFF'
    }
  }, (error) => {
    if (error) console.error('QR code generation error:', error);
  });
}
</script>
