<template>
  <div class="flex flex-col space-y-4">
    <FeatureCard
      title="v2.0 自绘高质感新皮肤 (尝鲜测试)"
      desc="全新米小江自绘质感：白天多层漫反射柔光防眩，夜间黑晶超清冷光字；独立双轨尝鲜，不影响经典主题。"
      helpTitle="【功能指南】v2.0 自绘新质感皮肤"
      helpText="1. 独立双轨：&#10;新版皮肤采用高动态光感与黑晶底色，与经典原版并存，供车友提前实车盲测。&#10;&#10;2. 防眩调光：&#10;若夜间偏好白天白色主题，可使用下方的「白天柔光亮度」滑块适度压暗，护眼不刺眼。&#10;&#10;3. 一键还原：&#10;随时可点击右上角按钮无缝切回经典原厂皮肤。"
    >
      <template #badge>
        <button
          @click="resetToClassicTheme"
          class="px-3 py-1.5 rounded-xl bg-car-item border border-car-border text-car-sub hover:text-car-text hover:border-car-accent text-[12.5px] font-black cursor-pointer transition-all"
          title="一键切回出厂经典晨曦绿"
        >
          切回经典原版 ↺
        </button>
      </template>

      <div class="w-full flex flex-col space-y-3">
        <!-- 昼夜模式切换三档 + 白天柔光亮度滑动调节（单行紧凑并列，高度更省空间） -->
        <div class="grid grid-cols-2 gap-3">
          <div class="flex items-center justify-between p-2.5 rounded-2xl bg-car-item border border-car-border">
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
                    ? 'bg-car-accent text-white border-car-accent shadow-md'
                    : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
                ]"
              >
                {{ MODE_LABELS[m] }}
              </button>
            </div>
          </div>

          <div class="flex items-center justify-between p-2.5 rounded-2xl bg-car-item border border-car-border">
            <div class="flex flex-col">
              <span class="text-[15.5px] font-black text-car-text">白天柔光亮度 ({{ dayBrightness }}%)</span>
              <span class="text-[12px] font-medium text-car-sub">夜驾白底适度压暗防眩目</span>
            </div>
            <div class="flex items-center space-x-2 w-48">
              <span class="text-[11px] text-car-sub font-bold">70%</span>
              <input
                type="range"
                min="70"
                max="100"
                step="5"
                :value="dayBrightness"
                @input="onBrightnessChange"
                class="w-full accent-car-accent cursor-pointer h-1.5"
              />
              <span class="text-[11px] text-car-sub font-bold">100%</span>
            </div>
          </div>
        </div>

        <!-- 4 套新版皮肤矩阵卡片 (2x2) -->
        <div class="grid grid-cols-2 gap-3.5">
          <div
            v-for="(info, key) in V2_THEME_INFO"
            :key="key"
            @click="onSetPalette(key)"
            :class="[
              'p-4 rounded-2xl border-2 cursor-pointer transition-all flex flex-col justify-between space-y-3',
              store.theme.palette === key
                ? 'bg-car-item border-car-accent shadow-lg'
                : 'bg-car-card border-car-border hover:border-car-border-light'
            ]"
          >
            <div class="flex items-center justify-between">
              <div class="flex items-center space-x-2.5">
                <span class="w-4 h-4 rounded-full border border-black/20 shadow-xs shrink-0" :style="{ background: info.accent }"></span>
                <span class="text-[17.5px] font-black text-car-text">{{ info.name }}</span>
              </div>
              <span
                :class="[
                  'text-[12px] px-2.5 py-0.5 rounded-md font-extrabold border',
                  store.theme.palette === key
                    ? 'bg-car-accent text-white border-car-accent'
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
import { ref } from 'vue';
import { store, showToast } from '../store';
import FeatureCard from './FeatureCard.vue';
import {
  MODES, MODE_LABELS,
  V2_THEME_INFO,
  setMode, setPalette, getDayBrightness, setDayBrightness
} from '../theme/themes';

const dayBrightness = ref(Math.round(getDayBrightness() * 100));

function onSetMode(m) {
  setMode(m);
}

function onSetPalette(p) {
  setPalette(p);
}

function onBrightnessChange(e) {
  const val = parseInt(e.target.value, 10);
  dayBrightness.value = val;
  setDayBrightness(val / 100);
}

function resetToClassicTheme() {
  setPalette('green');
  showToast('已切回经典晨曦绿主题');
}
</script>
