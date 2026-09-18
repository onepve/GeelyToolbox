<template>
    <!-- 弹窗 1: 挡位细分配置二级向导 (GearConfigModal · 全量补齐声效设置) -->
    <div 
      class="fixed z-[9998] flex items-center justify-center p-6" style="top:0; left:0; width:100vw; height:100vh; background: rgba(11, 15, 25, 0.85);"
    >
      <div class="bg-car-card border-2 border-car-border rounded-3xl p-7 shadow-2xl max-w-[880px] w-full flex flex-col space-y-5 max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between border-b border-car-border pb-4">
          <div class="flex items-center space-x-3">
            <span class="w-3 h-3 rounded-full bg-car-accent"></span>
            <span class="text-[22px] font-black text-car-text">换挡语音详细配置 (四大标准挡位)</span>
          </div>
          <button 
            @click="$emit('close')"
            class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-sub hover:text-car-text font-black text-[20px] flex items-center justify-center cursor-pointer"
          >
            ✕
          </button>
        </div>

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
import { store } from '../store';
import { useConfigModal } from '../composables/useConfigModal';

const emit = defineEmits(['close']);

const { toggleSetting, testVoice, openCustomVoice } = useConfigModal();

</script>
