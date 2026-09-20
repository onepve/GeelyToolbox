<!-- 原厂电动尾门细分配置二级向导 (TrunkConfigModal · 补齐开闭与声效设置) -->
<template>
  <ModalWrapper
    :show="true"
    title="原厂电动尾门安全播报详细配置"
    badge="尾门细分"
    maxWidthClass="max-w-[880px]"
    zIndexClass="z-[9998]"
    @close="$emit('close')"
  >
    <div class="flex flex-col space-y-3.5">
          <!-- 尾门升起提醒 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <div class="flex items-center space-x-2">
                <span class="text-[21.5px] font-black text-car-text">尾门升起提醒</span>
                <span class="px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-car-accent text-[14px] font-bold">防碰防刮</span>
              </div>
              <span class="text-[13px] text-car-sub font-bold">后备箱抬起升起时短促提醒，防止碰擦低矮车库顶梁</span>
            </div>
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('trunk_open')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('trunk_open', '尾门升起提醒', 'trunk_open.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_trunk_open')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_trunk_open ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_trunk_open ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>

          <!-- 尾门完全锁止 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <div class="flex items-center space-x-2">
                <span class="text-[21.5px] font-black text-car-text">尾门完全锁止</span>
                <span class="px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-emerald-400 text-[14px] font-bold">锁闭就绪</span>
              </div>
              <span class="text-[13px] text-car-sub font-bold">后备箱电吸闭合完全锁止时短促播报“后备箱已关好”，关后备箱无需回头确认</span>
            </div>
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('trunk_close')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('trunk_close', '尾门完全锁止', 'trunk_close.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_trunk_close')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_trunk_close ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_trunk_close ? '已开启' : '已关闭' }}
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
