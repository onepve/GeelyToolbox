<template>
  <div class="bg-car-card border border-car-border rounded-3xl p-6 shadow-xl flex flex-col space-y-5">
    <!-- 顶部状态栏 -->
    <div class="flex flex-wrap items-center justify-between pb-4 border-b border-car-border">
      <div class="flex items-center space-x-3">
        <span class="w-3.5 h-3.5 rounded-full bg-car-accent shadow-[0_0_10px_var(--accent-gold)]"></span>
        <span class="text-[20px] font-black text-car-text tracking-wide">手机无线快传 · 扫码推包</span>
        <span class="px-3 py-1 rounded-full text-[12px] font-black bg-car-item border border-car-accent text-car-accent">
          LAN 直连通道
        </span>
      </div>
      <div class="flex items-center space-x-3">
        <button 
          @click="$emit('open-guide')"
          class="h-[52px] px-4 rounded-xl bg-car-item border border-car-border hover:border-car-accent text-car-accent font-black text-[13.5px] cursor-pointer transition-all flex items-center space-x-1.5"
        >
          <span>📋 极客配置向导 (已就绪·点击复查)</span>
        </button>
      </div>
    </div>

    <!-- 手机扫码二维码矢量卡片 -->
    <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-wrap items-center justify-between">
      <div class="flex items-center space-x-5">
        <!-- 二维码矢量画布 (120x120 纯白底座高对比) -->
        <div class="p-2.5 bg-white rounded-2xl shadow-md border-2 border-car-border shrink-0 flex items-center justify-center">
          <canvas ref="qrCanvasRef" width="110" height="110" class="rounded-lg"></canvas>
        </div>
        <div class="flex flex-col space-y-1">
          <div class="flex items-center space-x-2.5">
            <span class="text-[18px] font-black text-car-text">微信 / 手机浏览器扫码</span>
            <span class="px-2.5 py-0.5 rounded-full text-[12px] font-black border" :class="isWifiConnected ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-400' : 'bg-amber-500/15 border-amber-500/40 text-amber-400'">
              {{ isWifiConnected ? 'Wi-Fi 局域网就绪' : '请连接同一 Wi-Fi 或热点' }}
            </span>
          </div>
          <div class="text-[14px] text-car-sub font-bold leading-relaxed max-w-2xl">
            手机与车机连接同一 Wi-Fi 或车机热点，扫码秒开无线推包页面，安装包将直接传输至 <code class="px-1.5 py-0.5 rounded bg-car-card text-car-accent font-mono text-[13px]">/sdcard/Download/</code>。
          </div>
          <div class="text-[13px] text-car-sub font-mono pt-1">
            局域网推包地址: <span class="text-car-accent font-bold">{{ wifiAddress ? `http://${wifiAddress}:8088` : '正在获取 IP 地址...' }}</span>
          </div>
        </div>
      </div>

      <div class="flex items-center space-x-3">
        <button 
          @click="generateQrCode"
          class="h-[52px] px-5 rounded-xl bg-car-card border border-car-border hover:border-car-border-light text-car-text font-black text-[14.5px] cursor-pointer transition-all"
        >
          刷新二维码
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import QRCode from 'qrcode'
import { bridge } from '../store'

defineEmits<{
  (e: 'open-guide'): void
}>()

const qrCanvasRef = ref<HTMLCanvasElement | null>(null)
const wifiAddress = ref('')
const isWifiConnected = ref(false)

const getWifiIp = async () => {
  try {
    const ip = bridge.call('getCarIp')
    if (ip) {
      wifiAddress.value = ip
      isWifiConnected.value = true
      return ip
    }
  } catch (e) {
    console.warn('Failed to get wifi ip', e)
  }
  wifiAddress.value = '192.168.43.1'
  return '192.168.43.1'
}

const generateQrCode = async () => {
  const ip = await getWifiIp()
  const targetUrl = `http://${ip}:8088/`
  if (qrCanvasRef.value) {
    QRCode.toCanvas(qrCanvasRef.value, targetUrl, {
      width: 110,
      margin: 1,
      color: {
        dark: '#000000',
        light: '#ffffff'
      }
    }, (err) => {
      if (err) console.error('QR code generation error', err)
    })
  }
}

onMounted(() => {
  generateQrCode()
})
</script>
