<template>
    <!-- 弹窗 2: 驾驶模式细分配置二级向导 (ModeConfigModal · 全量补齐声效设置) -->
    <div 
      class="fixed z-[9998] flex items-center justify-center bg-black/75 p-6"
      style="top:0; left:0; width:100vw; height:100vh;"
    >
      <div class="bg-car-card border-2 border-car-border rounded-3xl p-7 shadow-2xl max-w-[880px] w-full flex flex-col space-y-5 max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between border-b border-car-border pb-4">
          <div class="flex items-center space-x-3">
            <span class="w-3 h-3 rounded-full bg-car-accent"></span>
            <span class="text-[22px] font-black text-car-text">4 大驾驶模式旋钮播报细分配置</span>
          </div>
          <button 
            @click="$emit('close')"
            class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-sub hover:text-car-text font-black text-[20px] flex items-center justify-center cursor-pointer"
          >
            ✕
          </button>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <!-- 舒适 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[19px] font-black text-car-text">舒适模式 (Comfort)</span>
              <span class="text-[13px] text-car-sub font-bold">换挡平顺温润</span>
            </div>
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('mode_comfort')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('mode_comfort', '舒适模式', 'mode_comfort.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_mode_comfort')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_mode_comfort ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_mode_comfort ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>

          <!-- 经济 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[19px] font-black text-car-text">经济模式 (Eco)</span>
              <span class="text-[13px] text-car-sub font-bold">极致节油长途巡航</span>
            </div>
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('mode_eco')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('mode_eco', '经济模式', 'mode_eco.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_mode_eco')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_mode_eco ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_mode_eco ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>

          <!-- 运动 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[19px] font-black text-car-text">运动模式 (Sport)</span>
              <span class="text-[13px] text-car-sub font-bold">油门激进动力充沛</span>
            </div>
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('mode_sport')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('mode_sport', '运动模式', 'mode_sport.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_mode_sport')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_mode_sport ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_mode_sport ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>

          <!-- 智能 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[19px] font-black text-car-text">智能模式 (Smart)</span>
              <span class="text-[13px] text-car-sub font-bold">动态自适应工况</span>
            </div>
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('mode_smart')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('mode_smart', '智能模式', 'mode_smart.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_mode_smart')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_mode_smart ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_mode_smart ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>
        </div>

        <div class="flex justify-end pt-3 border-t border-car-border">
          <button 
            @click="$emit('close')"
            class="h-[52px] px-8 rounded-xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[16px] cursor-pointer"
          >
            完成配置
          </button>
        </div>
      </div>
    </div>
</template>

<script setup>
import { store, bridge, showToast, openModal } from '../store';

const emit = defineEmits(['close']);

function toggleSetting(key) {
  const next = !store.vehicleAuto[key];
  store.vehicleAuto[key] = next;
  bridge.call('setVehicleAutomationSetting', key, next);
  showToast('设置已更新: ' + (next ? '已开启' : '已关闭'));
}

function testVoice(type) {
  bridge.call('testVehicleVoice', type);
}

function openCustomVoice(key, title, soundFile) {
  openModal('voiceItemSettings', {
    key,
    title,
    soundFile: soundFile || (key + '.mp3')
  });
}

</script>
