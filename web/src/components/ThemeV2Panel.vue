<template>
  <div class="flex flex-col space-y-4">
    <FeatureCard
      title="车规视觉主题"
      desc="吉利全系车型标准双模外观：白天漫反射温润防眩目，夜间黑晶超清冷光字；支持白天柔光微调。"
      helpTitle="【功能指南】车规双模外观系统"
      helpText="1. 昼夜自适应：&#10;支持跟随车机大灯/时间自动切换，也可随时强制锁定白天高透或黑夜黑晶模式。&#10;&#10;2. 防眩调光：&#10;夜间偏好白色底色的车友，可使用「白天柔光亮度」滑块适度压暗，护眼不刺眼。&#10;&#10;3. 6套质感配色：&#10;包含晴空蓝、极光青、暖阳金、翡翠绿，以及专属女性向落樱粉、香芋紫，纯自绘抗暴晒不掉帧。"
    >
      <div class="w-full flex flex-col space-y-4">
        <!-- 昼夜模式切换三档 + 白天柔光亮度滑动调节（单行紧凑并列，高度更省空间） -->
        <div class="grid grid-cols-2 gap-3.5">
          <div class="flex items-center justify-between p-3 rounded-2xl bg-car-item border border-car-border">
            <div class="flex flex-col">
              <span class="text-[15.5px] font-black text-car-text">昼夜切换逻辑</span>
              <span class="text-[12px] font-medium text-car-sub">跟随信号翻转或锁定</span>
            </div>
            <div class="flex items-center space-x-1.5">
              <button
                v-for="m in MODES"
                :key="m"
                @click="onSetMode(m)"
                :class="[
                  'px-3.5 py-1.5 rounded-lg border text-[13px] font-black cursor-pointer transition-all',
                  store.theme.mode === m
                    ? 'bg-car-accent-bg border-car-accent text-car-accent shadow-sm'
                    : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
                ]"
              >
                {{ MODE_LABELS[m] }}
              </button>
            </div>
          </div>

          <div class="flex items-center justify-between p-3 rounded-2xl bg-car-item border border-car-border">
            <div class="flex flex-col">
              <span class="text-[15.5px] font-black text-car-text">{{ currentBrightnessLabel }} ({{ currentBrightness }}%)</span>
              <span class="text-[12px] font-medium text-car-sub">{{ isNightMode ? '黑夜深色适度压暗防眩目' : '白天白底适度压暗防眩目' }}</span>
            </div>
            <!-- 自定义车规平滑滑块（彻底解决 Android 9 WebView 原生 input[type=range] 无法触摸拖动的问题） -->
            <div class="flex items-center space-x-2">
              <button
                @click="stepBrightness(-5)"
                class="w-7 h-7 rounded-lg bg-car-card border border-car-border flex items-center justify-center text-car-sub hover:text-car-text hover:border-car-accent text-[13px] font-black cursor-pointer active:scale-95 transition-all select-none"
                title="减小亮度"
              >-</button>
              <div
                ref="sliderRef"
                @mousedown="onSliderMouseDown"
                @touchstart.prevent="onSliderTouchStart"
                class="relative w-36 sm:w-44 h-7 flex items-center cursor-pointer select-none py-1"
                style="touch-action: none;"
              >
                <!-- 轨道槽线 -->
                <div class="w-full h-2 rounded-full bg-car-card border border-car-border overflow-hidden">
                  <!-- 激活填充 -->
                  <div
                    class="h-full bg-car-accent transition-none"
                    :style="{ width: `${brightnessPercent}%` }"
                  ></div>
                </div>
                <!-- 拖动圆钮 (车规大触控 Thumb，带边框与阴影，支持平滑抓取) -->
                <div
                  class="absolute top-1/2 -translate-y-1/2 w-5 h-5 rounded-full bg-[#f8fafc] border-2 border-car-accent shadow-md pointer-events-none transition-none"
                  :style="{ left: `calc(${brightnessPercent}% - 10px)` }"
                ></div>
              </div>
              <button
                @click="stepBrightness(5)"
                class="w-7 h-7 rounded-lg bg-car-card border border-car-border flex items-center justify-center text-car-sub hover:text-car-text hover:border-car-accent text-[13px] font-black cursor-pointer active:scale-95 transition-all select-none"
                title="增加亮度"
              >+</button>
            </div>
          </div>
        </div>

        <!-- 6 套新版皮肤矩阵卡片 (2列 x 3行，垂直拉大撑满整屏，方便大拇指点按) -->
        <div class="grid grid-cols-2 gap-3.5">
          <div
            v-for="(info, key) in V2_THEME_INFO"
            :key="key"
            @click="onSetPalette(key)"
            :class="[
              'p-4 rounded-2xl border-2 cursor-pointer transition-all flex flex-col justify-between space-y-2.5 min-h-[96px]',
              store.theme.palette === key
                ? 'bg-car-item border-car-accent shadow-lg'
                : 'bg-car-card border-car-border hover:border-car-border-light'
            ]"
          >
            <div class="flex items-center justify-between">
              <div class="flex items-center space-x-2.5">
                <span class="w-4 h-4 rounded-full border border-black/20 shadow-xs shrink-0" :style="{ background: info.accent }"></span>
                <span class="text-[17px] font-black text-car-text tracking-wide">{{ info.name }}</span>
              </div>
              <span
                :class="[
                  'text-[12px] px-2.5 py-0.5 rounded-lg font-extrabold border',
                  store.theme.palette === key
                    ? 'bg-car-accent-bg border-car-accent text-car-accent'
                    : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.theme.palette === key ? '当前使用中' : info.tag }}
              </span>
            </div>

            <div class="text-[13.5px] font-bold text-car-sub leading-snug">
              {{ info.desc }}
            </div>
          </div>
        </div>
      </div>
    </FeatureCard>
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue';
import { store } from '../store';
import FeatureCard from './FeatureCard.vue';
import {
  MODES, MODE_LABELS,
  V2_THEME_INFO,
  setMode, setPalette, getDayBrightness, setDayBrightness,
  getNightBrightness, setNightBrightness, resolvedMode
} from '../theme/themes';

const dayBrightness = ref(Math.round(getDayBrightness() * 100));
const nightBrightness = ref(Math.round(getNightBrightness() * 100));
const sliderRef = ref(null);

const isNightMode = computed(() => {
  return resolvedMode() === 'night';
});

const currentBrightnessLabel = computed(() => {
  return isNightMode.value ? '黑夜柔光亮度' : '白天柔光亮度';
});

const currentBrightness = computed(() => {
  return isNightMode.value ? nightBrightness.value : dayBrightness.value;
});

const brightnessPercent = computed(() => {
  const val = currentBrightness.value;
  const clamped = Math.max(70, Math.min(100, val));
  return ((clamped - 70) / 30) * 100;
});

function onSetMode(m) {
  setMode(m);
}

function onSetPalette(p) {
  setPalette(p);
}

function stepBrightness(delta) {
  const cur = currentBrightness.value;
  const next = Math.max(70, Math.min(100, cur + delta));
  applyBrightnessValue(next);
}

function applyBrightnessValue(val) {
  if (isNightMode.value) {
    nightBrightness.value = val;
    setNightBrightness(val / 100);
  } else {
    dayBrightness.value = val;
    setDayBrightness(val / 100);
  }
}

function updateBrightnessFromCoord(clientX) {
  if (!sliderRef.value) return;
  const rect = sliderRef.value.getBoundingClientRect();
  if (rect.width <= 0) return;
  const ratio = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
  const rawVal = 70 + ratio * 30;
  const val = Math.round(rawVal);
  if (val !== currentBrightness.value) {
    applyBrightnessValue(val);
  }
}

// 拖拽与触摸事件闭环管理
let isDragging = false;

function onSliderTouchStart(e) {
  isDragging = true;
  if (e.touches && e.touches[0]) {
    updateBrightnessFromCoord(e.touches[0].clientX);
  }
  window.addEventListener('touchmove', onTouchMove, { passive: false });
  window.addEventListener('touchend', onTouchEnd);
  window.addEventListener('touchcancel', onTouchEnd);
}

function onTouchMove(e) {
  if (!isDragging) return;
  // 彻底阻止系统手势截断/垂直滚动劫持，确保横向顺滑拖动
  if (e.cancelable) e.preventDefault();
  if (e.touches && e.touches[0]) {
    updateBrightnessFromCoord(e.touches[0].clientX);
  }
}

function onTouchEnd() {
  if (!isDragging) return;
  isDragging = false;
  window.removeEventListener('touchmove', onTouchMove);
  window.removeEventListener('touchend', onTouchEnd);
  window.removeEventListener('touchcancel', onTouchEnd);
}

function onSliderMouseDown(e) {
  isDragging = true;
  updateBrightnessFromCoord(e.clientX);
  window.addEventListener('mousemove', onMouseMove);
  window.addEventListener('mouseup', onMouseUp);
}

function onMouseMove(e) {
  if (!isDragging) return;
  updateBrightnessFromCoord(e.clientX);
}

function onMouseUp() {
  if (!isDragging) return;
  isDragging = false;
  window.removeEventListener('mousemove', onMouseMove);
  window.removeEventListener('mouseup', onMouseUp);
}

onUnmounted(() => {
  window.removeEventListener('touchmove', onTouchMove);
  window.removeEventListener('touchend', onTouchEnd);
  window.removeEventListener('touchcancel', onTouchEnd);
  window.removeEventListener('mousemove', onMouseMove);
  window.removeEventListener('mouseup', onMouseUp);
});
</script>
