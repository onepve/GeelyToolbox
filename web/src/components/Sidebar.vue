<template>
  <aside class="w-[300px] shrink-0 bg-[#1C1F26] border-r border-white/10 flex flex-col p-4 gap-3 select-none">
    <div 
      v-for="item in navItems" 
      :key="item.id"
      @click="selectNav(item.id)"
      :class="[
        'flex items-center justify-between px-5 min-h-[72px] rounded-2xl border-2 transition-all cursor-pointer font-black text-[19px]',
        store.currentNav === item.id 
          ? 'bg-amber-500/10 border-[#F59E0B] text-[#FEF3C7] shadow-md' 
          : 'bg-[#262A33] border-transparent text-white/80 hover:bg-[#323742]'
      ]"
    >
      <span>{{ item.name }}</span>
      <span 
        :class="[
          'text-[13px] px-2.5 py-1 rounded-lg font-extrabold',
          store.currentNav === item.id ? 'bg-[#F59E0B] text-black font-black' : 'bg-white/10 text-[#94A3B8]'
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
