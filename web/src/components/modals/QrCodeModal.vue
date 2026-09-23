<template>
  <ModalWrapper 
    :show="store.modals.qrCode" 
    title="局域网无线快传" 
    badge="HTTP: 8888"
    maxWidthClass="max-w-[760px]"
    @close="closeModal('qrCode')"
  >
    <div class="flex flex-col items-center text-center py-2">
      <!-- 网络状态胶囊 -->
      <div 
        :class="[
          'px-5 py-2.5 rounded-full font-black text-[16px] mb-5 shadow-sm flex items-center space-x-2',
          isWifiConnected 
            ? 'text-emerald-500 border-2' 
            : 'text-amber-500 border-2'
        ]"
        :style="isWifiConnected ? 'background: rgba(16,185,129,0.15); border-color: rgba(16,185,129,0.4);' : 'background: rgba(245,158,11,0.15); border-color: rgba(245,158,11,0.4);'"
      >
        <StatusDot class="mr-2.5" size="lg" :color="isWifiConnected ? 'ok' : 'warn'" />
        <span>{{ isWifiConnected ? `局域网已就绪: ${serverUrl}` : '提示: 请让手机与车机处于同一热点/Wi-Fi' }}</span>
      </div>

      <!-- 二维码展示区 (护眼柔和对比度，已移除车机冗余复制网址) -->
      <div class="p-4 bg-slate-200 rounded-3xl shadow-xl border-4 border-car-border flex flex-col items-center justify-center mb-5">
        <canvas ref="canvasRef" id="qrCodeCanvas" width="220" height="220" class="rounded-xl opacity-90"></canvas>
      </div>

      <!-- 操作步骤指引 (向右箭头单行流) -->
      <div class="text-[17px] text-car-sub font-bold">
        📱 手机连接同一 Wi-Fi ➔ 微信/相机扫一扫 ➔ 极速推包到车机 Download 目录
      </div>
    </div>
  </ModalWrapper>
</template>

<script setup>
import { computed, watch, nextTick, ref } from 'vue';
import QRCode from 'qrcode';
import ModalWrapper from './ModalWrapper.vue';
import StatusDot from '../StatusDot.vue';
import { store, bridge, closeModal } from '../../store';

const canvasRef = ref(null);

const serverUrl = computed(() => `http://${store.deviceInfo.car_ip || '127.0.0.1'}:8888`);
const isWifiConnected = computed(() => {
  const ip = store.deviceInfo.car_ip || '';
  return ip.startsWith('192.168.') || ip.startsWith('10.') || ip.startsWith('172.');
});

function refreshNetworkAndRender() {
  try {
    const raw = bridge.call('getDeviceInfo');
    if (raw) {
      const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
      if (data && data.ip) {
        store.deviceInfo.car_ip = data.ip;
      }
    }
  } catch (e) {}
  nextTick(() => {
    renderQrCode();
    setTimeout(renderQrCode, 150);
  });
}

// 监听弹窗打开时拉取最新局域网 IP 并渲染标准 QR Code
watch(() => store.modals.qrCode, (show) => {
  if (show) {
    refreshNetworkAndRender();
  }
});

watch(serverUrl, () => {
  if (store.modals.qrCode) {
    nextTick(() => {
      renderQrCode();
    });
  }
});

function renderQrCode() {
  const canvas = canvasRef.value || document.getElementById('qrCodeCanvas');
  if (!canvas) return;
  QRCode.toCanvas(canvas, serverUrl.value, {
    width: 220,
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
