<template>
  <aside
    class="w-[300px] shrink-0 flex flex-col p-4 space-y-2.5 select-none transition-colors overflow-y-auto"
  >
    <div
      v-for="item in navItems"
      :key="item.id"
      @click="selectNav(item.id)"
      :class="[
        'flex items-center justify-between px-5 min-h-[68px] rounded-2xl nav-item cursor-pointer font-black text-[21px] tracking-wide',
        store.currentNav === item.id
          ? 'bg-car-item border-solid border-l-[6px] border-l-car-accent text-car-text shadow-md'
          : 'bg-car-item text-car-text shadow-nav-rest'
      ]"
    >
      <span class="truncate">{{ item.name }}</span>
      <span
        :class="[
          'text-[14px] px-2.5 py-1 rounded-lg font-extrabold shrink-0 nav-badge',
          store.currentNav === item.id
            ? 'text-car-accent font-black bg-car-accent-bg'
            : 'bg-car-card text-car-sub opacity-75'
        ]"
      >
        {{ item.badge }}
      </span>
    </div>

    <!-- 左下角关于助手菜单卡片：完全看齐主菜单统一车规设计 -->
    <div class="mt-auto pt-3 border-t border-car-border">
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

<style scoped>
/* 车规交互铁律：点下去必须「即亮」。
   以前这里是 transition-colors duration-150 + 全局 box-shadow .18s，
   点一个新大区要等 150ms 边框才亮完、阴影再拖 180ms，
   观感就是「慢慢切过去、按钮不干脆」。
   现在边框与文字颜色 0 过渡（瞬间切换），只给投影留 90ms 微收，
   既保住质感的柔和，又不拖反应速度。 */
.nav-item {
  transition: box-shadow 90ms ease-out;
}
.nav-badge {
  transition: box-shadow 90ms ease-out, background-color 0ms, border-color 0ms, color 0ms;
}
</style>
