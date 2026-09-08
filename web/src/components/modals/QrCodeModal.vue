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
          'px-5 py-2.5 rounded-full font-black text-[16px] mb-5 shadow-sm flex items-center space-x-2',
          isWifiConnected 
            ? 'bg-emerald-500/15 text-emerald-500 border-2 border-emerald-500/40' 
            : 'bg-amber-500/15 text-amber-500 border-2 border-amber-500/40'
        ]"
      >
        <span class="w-2.5 h-2.5 rounded-full" :class="isWifiConnected ? 'bg-emerald-500 shadow-[0_0_6px_#10B981]' : 'bg-amber-500'"></span>
        <span>{{ isWifiConnected ? `局域网已就绪: ${serverUrl}` : '提示: 请让手机与车机处于同一热点/Wi-Fi' }}</span>
      </div>

      <!-- 二维码展示区 (220x220 纯白高对比) -->
      <div class="p-5 bg-white rounded-3xl shadow-xl border-4 border-car-border flex flex-col items-center justify-center mb-5">
        <canvas ref="canvasRef" id="qrCodeCanvas" width="220" height="220" class="rounded-xl"></canvas>
        <div class="flex items-center space-x-3 mt-3">
          <span class="text-black font-mono font-black text-[16px] select-all">
            {{ serverUrl }}
          </span>
          <button 
            @click="copyUrl"
            class="px-3 py-1 rounded-lg bg-gray-100 hover:bg-gray-200 text-gray-800 text-[13px] font-bold border border-gray-300 cursor-pointer shadow-xs"
          >
            复制
          </button>
        </div>
      </div>

      <!-- 操作步骤指引 (向右箭头单行流) -->
      <div class="text-[17px] text-car-sub font-bold">
        手机连接同一 Wi-Fi ➔ 微信/相机扫一扫 ➔ 极速推包到车机 Download 目录
      </div>
    </div>
  </ModalWrapper>
</template>

<script setup>
import { computed, watch, nextTick, ref } from 'vue';
import QRCode from 'qrcode';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, showToast } from '../../store';

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

function copyUrl() {
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(serverUrl.value);
    showToast('地址已复制到剪贴板');
  } else {
    showToast('快传地址: ' + serverUrl.value);
  }
}
</script>
