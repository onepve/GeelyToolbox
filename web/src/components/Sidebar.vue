<template>
  <aside
    class="w-[300px] shrink-0 border-r border-car-border flex flex-col p-4 space-y-2.5 select-none transition-colors overflow-y-auto"
    :style="{ background: 'var(--bg-panel)' }"
  >
    <div
      v-for="item in navItems"
      :key="item.id"
      @click="selectNav(item.id)"
      :class="[
        'flex items-center justify-between px-5 min-h-[68px] rounded-2xl border-2 transition-all cursor-pointer font-black text-[21px] tracking-wide',
        store.currentNav === item.id
          ? 'bg-car-item border-car-accent text-car-text shadow-md ring-2 ring-car-accent/30'
          : 'bg-car-item border-transparent text-car-text hover:bg-car-hover'
      ]"
    >
      <span class="truncate">{{ item.name }}</span>
      <span
        :class="[
          'text-[14px] px-2.5 py-1 rounded-lg font-extrabold shrink-0',
          store.currentNav === item.id ? 'border border-car-accent text-car-accent font-black bg-car-card' : 'bg-car-card border border-car-border text-car-sub'
        ]"
      >
        {{ item.badge }}
      </span>
    </div>

    <!-- 左下角官方教程二维码微卡片 -->
    <div class="mt-auto pt-2">
      <SidebarQrCodeCard />
    </div>
  </aside>
</template>

<script setup>
import { store, recordActiveNav } from '../store';
import SidebarQrCodeCard from './SidebarQrCodeCard.vue';
import { playTouchFeedback } from '../utils/sound';

const navItems = [
  { id: 'wheel', name: '方控按键', badge: '核心改装' },
  { id: 'link', name: '车身联动', badge: '360/车速' },
  { id: 'body', name: '车载语音', badge: '语音播报' },
  { id: 'store', name: '精选商城', badge: '专车应用' },
  { id: 'audio', name: '车载音频', badge: '语音/蓝牙' },
  { id: 'install', name: '特权安装', badge: '暗码/快传' },
  { id: 'system', name: '系统维护', badge: '维护/悬浮' }
];

function selectNav(id) {
  playTouchFeedback();
  store.currentNav = id;
  recordActiveNav(id);
}
</script>
