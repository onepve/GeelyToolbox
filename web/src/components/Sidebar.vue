<template>
  <aside class="w-[300px] shrink-0 bg-car-card border-r border-car-border flex flex-col p-4 gap-3 select-none transition-colors">
    <div 
      v-for="item in navItems" 
      :key="item.id"
      @click="selectNav(item.id)"
      :class="[
        'flex items-center justify-between px-5 min-h-[72px] rounded-2xl border-2 transition-all cursor-pointer font-black text-[19px]',
        store.currentNav === item.id 
          ? 'bg-car-accent-bg border-car-accent text-car-accent-text shadow-md ring-2 ring-car-accent/20' 
          : 'bg-car-item border-transparent text-car-sub hover:bg-car-hover hover:text-car-text'
      ]"
    >
      <span>{{ item.name }}</span>
      <span 
        :class="[
          'text-[13px] px-2.5 py-1 rounded-lg font-extrabold',
          store.currentNav === item.id ? 'bg-car-accent text-black font-black' : 'bg-car-card border border-car-border text-car-sub'
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
  { id: 'wheel', name: '方向盘与方控', badge: '核心改装' },
  { id: 'body', name: '车身智能联动', badge: '门控/挡位' },
  { id: 'audio', name: '车载音频与USB', badge: '蓝牙免点' },
  { id: 'store', name: '精选软件中心', badge: '专车高德' },
  { id: 'install', name: '特权安装互联', badge: '原生文件' },
  { id: 'system', name: '系统底层维护', badge: 'ADB/重启' }
];

function selectNav(id) {
  playTouchFeedback();
  store.currentNav = id;
}
</script>
