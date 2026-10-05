<!-- 挡位细分配置二级向导 (GearConfigModal · 全量补齐声效设置) -->
<template>
  <ModalWrapper
    :show="true"
    title="换挡语音配置"
    badge="挡位细分"
    maxWidthClass="max-w-[880px]"
    zIndexClass="z-[9998]"
    @close="$emit('close')"
  >
    <div class="grid grid-cols-2 gap-4">
          <!-- D 挡 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[21.5px] font-black text-car-text">前进挡 (D 挡)</span>
              <span class="text-[15.5px] text-car-sub font-bold">踩刹车切 D 挡启程播报</span>
            </div>
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('gear_d')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('gear_d', '前进挡 (D 挡)', 'gear_d.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_gear_d')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_gear_d ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_gear_d ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>

          <!-- R 挡 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[21.5px] font-black text-car-text">倒车挡 (R 挡)</span>
              <span class="text-[15.5px] text-car-sub font-bold">切入倒挡触发后方警示</span>
            </div>
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('gear_r')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('gear_r', '倒车挡 (R 挡)', 'gear_r.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_gear_r')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_gear_r ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_gear_r ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>

          <!-- P 挡 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[21.5px] font-black text-car-text">驻车挡 (P 挡)</span>
              <span class="text-[15.5px] text-car-sub font-bold">挂回 P 挡并立即归零状态机</span>
            </div>
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('gear_p')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('gear_p', '驻车挡 (P 挡)', 'gear_p.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_gear_p')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_gear_p ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_gear_p ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>

          <!-- N 挡 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[21.5px] font-black text-car-text">空挡 (N 挡)</span>
              <span class="text-[15.5px] text-car-sub font-bold">默认关闭防红绿灯打扰</span>
            </div>
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('gear_n')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('gear_n', '空挡 (N 挡)', 'gear_n.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_gear_n')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_gear_n ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_gear_n ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>
        </div>

    <template #footer>
      <button 
            @click="$emit('close')"
            class="h-[52px] px-8 rounded-xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[16px] cursor-pointer"
          >
            完成配置
          </button>
    </template>
  </ModalWrapper>
</template>

<script setup>
import ModalWrapper from './modals/ModalWrapper.vue';
import { store } from '../store';
import { useConfigModal } from '../composables/useConfigModal';

const emit = defineEmits(['close']);

const { toggleSetting, testVoice, openCustomVoice } = useConfigModal();

</script>
