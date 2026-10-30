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
          'px-5 py-2.5 rounded-full font-black text-[16px] mb-4 shadow-sm flex items-center space-x-2',
          isWifiConnected 
            ? 'text-emerald-500 border-2' 
            : 'text-amber-500 border-2'
        ]"
        :style="isWifiConnected ? 'background: rgba(16,185,129,0.15); border-color: rgba(16,185,129,0.4);' : 'background: rgba(245,158,11,0.15); border-color: rgba(245,158,11,0.4);'"
      >
        <StatusDot class="mr-2.5" size="lg" :color="isWifiConnected ? 'ok' : 'warn'" />
        <span v-if="isCarHotspot">⚠️ 车机热点仅供手机上网，受防火墙隔离无法访问后台；请用手机开热点供车机连接</span>
        <span v-else>{{ isWifiConnected ? `局域网已就绪: ${serverUrl}` : '提示: 请用手机开热点供车机连接，或连接同一 Wi-Fi' }}</span>
      </div>

      <!-- 快捷 Wi-Fi 控制工具栏 -->
      <div class="flex items-center space-x-3 mb-4">
        <button 
          type="button"
          @click="toggleWifiState"
          class="min-h-[50px] px-5 rounded-xl border-2 font-black text-[15px] cursor-pointer transition-all flex items-center shadow-sm whitespace-nowrap"
          :class="isWifiOn 
            ? 'bg-emerald-500/15 border-emerald-500/60 text-emerald-400' 
            : 'bg-car-item border-car-border text-car-sub hover:text-car-text'"
        >
          <span class="mr-1.5">{{ isWifiOn ? '📶' : '📵' }}</span>
          <span>{{ isWifiOn ? 'Wi-Fi: 已开启 (点击关闭)' : '一键开启车机 Wi-Fi' }}</span>
        </button>
        <button 
          type="button"
          @click="openWifiSettings"
          class="min-h-[50px] px-4 rounded-xl bg-car-item border-2 border-car-border text-car-text font-black text-[14.5px] cursor-pointer hover:border-car-border-light shadow-sm transition-all flex items-center whitespace-nowrap"
        >
          <span>系统设置 ➔</span>
        </button>
      </div>

      <!-- 二维码展示区 (车规深色语义卡片，独立白底衬垫保证扫码对比度) -->
      <div class="p-5 bg-car-item rounded-3xl border-2 border-car-border flex flex-col items-center justify-center mb-4 shadow-lg">
        <div class="p-3 bg-white rounded-2xl shadow-inner">
          <canvas ref="canvasRef" id="qrCodeCanvas" width="220" height="220" class="rounded-xl"></canvas>
        </div>
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
import { store, bridge, closeModal, showToast } from '../../store';

const canvasRef = ref(null);
const isWifiOn = ref(false);
const netType = ref('');

const isCarHotspot = computed(() => {
  return netType.value.includes('车机热点') || (store.deviceInfo.net_type && store.deviceInfo.net_type.includes('车机热点'));
});

function checkWifiState() {
  try {
    const res = bridge.call('isWifiEnabled');
    if (res !== undefined && res !== null) {
      isWifiOn.value = (res === true || res === 'true');
      return;
    }
    const raw = bridge.call('getConnectivityStatus');
    if (raw) {
      const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
      if (data && data.wifi_enabled !== undefined) {
        isWifiOn.value = !!data.wifi_enabled;
      }
    }
  } catch (e) {}
}

function toggleWifiState() {
  const target = !isWifiOn.value;
  try {
    bridge.call('toggleWifi', target);
    isWifiOn.value = target;
    setTimeout(() => {
      checkWifiState();
      refreshNetworkAndRender();
    }, 800);
    setTimeout(() => {
      checkWifiState();
      refreshNetworkAndRender();
    }, 2000);
  } catch (e) {
    showToast('切换 Wi-Fi 失败: ' + e);
  }
}

function openWifiSettings() {
  try {
    bridge.call('openWifiSettings');
  } catch (e) {
    showToast('打开系统 Wi-Fi 设置失败: ' + e);
  }
}

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
        netType.value = data.net_type || '';
        store.deviceInfo.net_type = data.net_type || '';
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
    checkWifiState();
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
