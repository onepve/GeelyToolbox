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
        'flex items-center justify-between px-5 min-h-[68px] rounded-2xl border-2 transition-colors duration-150 cursor-pointer font-black text-[21px] tracking-wide',
        store.currentNav === item.id
          ? 'bg-car-item border-car-accent text-car-text shadow-md'
          : 'bg-car-item border-transparent text-car-text hover:bg-car-hover'
      ]"
    >
      <span class="truncate">{{ item.name }}</span>
      <span
        :class="[
          'text-[14px] px-2.5 py-1 rounded-lg font-extrabold shrink-0 transition-all',
          store.currentNav === item.id
            ? 'border-2 border-car-accent text-car-accent font-black bg-car-card shadow-sm'
            : 'bg-car-card/60 border border-car-border/60 text-car-sub/70 opacity-75'
        ]"
      >
        {{ item.badge }}
      </span>
    </div>

    <!-- 左下角关于助手菜单卡片：完全看齐主菜单统一车规设计 -->
    <div class="mt-auto pt-3 border-t border-car-border/50">
      <SidebarQrCodeCard />
    </div>
  </aside>
</template>

<script setup>
import { store, recordActiveNav } from '../store';
import SidebarQrCodeCard from './SidebarQrCodeCard.vue';
import { playTouchFeedback } from '../utils/sound';

const navItems = [
  { id: 'link', name: '行车联动', badge: '方控/360' },
  { id: 'body', name: '车载声音', badge: '提示/语音' },
  { id: 'fuel', name: '行程油耗', badge: '油价/账本' },
  { id: 'store', name: '应用生态', badge: '商城/安装' },
  { id: 'system', name: '系统设置', badge: '桌面/外观' }
];

function selectNav(id) {
  if (store.currentNav === id) return;
  playTouchFeedback();
  store.currentNav = id;
  recordActiveNav(id);
}
</script>
