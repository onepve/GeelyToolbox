<template>
  <div 
    @click="openTutorial"
    class="p-2 mx-1.5 rounded-2xl bg-car-card border border-car-border flex items-center space-x-2.5 cursor-pointer hover:bg-car-hover transition active:scale-95 group shadow-sm"
    title="扫码或点击查看使用教程"
  >
    <div class="p-1.5 bg-white rounded-xl shadow shrink-0 flex items-center justify-center">
      <canvas ref="qrCanvasRef" class="w-12 h-12 block"></canvas>
    </div>
    <div class="flex-1 min-w-0 pr-1 text-left">
      <div class="text-[12px] font-bold text-car-text tracking-wide group-hover:text-car-primary transition truncate">
        使用教程
      </div>
      <div class="text-[10px] text-car-muted leading-tight mt-0.5 truncate">
        手机扫码直达
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import QRCode from 'qrcode'
import { bridge } from '../store'
import { playTouchFeedback } from '../utils/sound'

const qrCanvasRef = ref(null)
const tutorialUrl = 'https://onepve.com/geely-toolbox/'

onMounted(async () => {
  await nextTick()
  if (qrCanvasRef.value) {
    try {
      await QRCode.toCanvas(qrCanvasRef.value, tutorialUrl, {
        width: 48,
        margin: 0,
        color: {
          dark: '#000000',
          light: '#ffffff'
        },
        errorCorrectionLevel: 'M'
      })
    } catch (e) {
      console.error('Failed to render tutorial QR code:', e)
    }
  }
})

const openTutorial = () => {
  playTouchFeedback()
  if (window.bridge && typeof window.bridge.openBrowser === 'function') {
    bridge.call('openBrowser', tutorialUrl)
  } else {
    window.open(tutorialUrl, '_blank')
  }
}
</script>
