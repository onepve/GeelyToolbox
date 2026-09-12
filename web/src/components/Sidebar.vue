<template>
  <aside class="w-[300px] shrink-0 bg-car-card border-r border-car-border flex flex-col p-4 space-y-2 select-none backdrop-blur-xl transition-colors overflow-y-auto">
    <div 
      v-for="item in navItems" 
      :key="item.id"
      @click="selectNav(item.id)"
      :class="[
        'flex items-center justify-between px-5 min-h-[64px] rounded-2xl border-2 transition-all cursor-pointer font-black text-[18px]',
        store.currentNav === item.id 
          ? 'bg-car-item border-car-accent text-car-text shadow-md ring-2 ring-car-accent/30' 
          : 'bg-car-item border-transparent text-car-sub hover:bg-car-hover hover:text-car-text'
      ]"
    >
      <span>{{ item.name }}</span>
      <span 
        :class="[
          'text-[12.5px] px-2.5 py-1 rounded-lg font-extrabold',
          store.currentNav === item.id ? 'border border-car-accent text-car-accent font-black bg-car-card' : 'bg-car-card border border-car-border text-car-sub'
        ]"
      >
        {{ item.badge }}
      </span>
    </div>
  </aside>
</template>

<script setup>
import { store } from '../store';
import { playTouchFeedback } from '../utils/sound';

const navItems = [
  { id: 'store', name: '精选商城', badge: '专车应用' },
  { id: 'wheel', name: '方控按键', badge: '核心改装' },
  { id: 'link', name: '车身联动', badge: '盲区/大灯' },
  { id: 'body', name: '座舱语音', badge: '门挡播报' },
  { id: 'audio', name: '车载音频', badge: 'TTS/增益' },
  { id: 'floating', name: '桌面悬浮', badge: '流光胶囊' },
  { id: 'install', name: '特权安装', badge: '原生互联' },
  { id: 'system', name: '系统维护', badge: 'ADB/重启' }
];

function selectNav(id) {
  playTouchFeedback();
  store.currentNav = id;
}
</script>
