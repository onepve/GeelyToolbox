<template>
  <div class="flex flex-col space-y-4">
    <!-- 1. 实测机型声明 (安全实测认证标识：翠绿圆环徽标与流线说明，彻底告别伪按钮误触) -->
    <div class="bg-car-item border-2 border-car-border rounded-3xl p-6 min-h-[96px] flex items-center shadow-md">
      <div class="w-[52px] h-[52px] rounded-full bg-emerald-500/15 border-2 border-emerald-500 text-emerald-400 flex items-center justify-center mr-6 shrink-0 shadow-sm">
        <svg class="w-7 h-7 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12c0 1.268-.63 2.39-1.593 3.068a3.745 3.745 0 01-1.043 3.296 3.745 3.745 0 01-3.296 1.043A3.745 3.745 0 0112 21c-1.268 0-2.39-.63-3.068-1.593a3.746 3.746 0 01-3.296-1.043 3.745 3.745 0 01-1.043-3.296A3.745 3.745 0 013 12c0-1.268.63-2.39 1.593-3.068a3.745 3.745 0 011.043-3.296 3.746 3.746 0 013.296-1.043A3.746 3.746 0 0112 3c1.268 0 2.39.63 3.068 1.593a3.746 3.746 0 013.296 1.043 3.746 3.746 0 011.043 3.296A3.745 3.745 0 0121 12z" />
        </svg>
      </div>
      <div class="text-[17.5px] text-car-text font-bold leading-[32px]">
        <span class="text-emerald-400 font-black mr-2">【实测机型声明】</span>以下精选车载软件已在 <span class="text-car-accent font-black underline decoration-car-accent/40">吉利缤越COOL 2022 (亿咖通 E02 / IHU516G / Android 9)</span> 专车深度实测调优，其他车型车友请自行评估后安全安装。
      </div>
    </div>

    <!-- 2. 精选车机应用大卡片 (包含内置分类工具头部与双列应用网格，彻底做进卡片内部！) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-6 shadow-md flex flex-col space-y-5">
      <!-- 卡片内置 Header：分类与操作工具栏 -->
      <div class="flex items-center justify-between border-b border-car-border/40 pb-4">
        <div class="text-[21px] font-black text-car-text tracking-wide shrink-0 mr-4">精选车机应用列表</div>
        <div class="flex items-center space-x-3">
          <button 
            v-for="cat in categories" 
            :key="cat.id"
            @click="currentCategory = cat.id"
            :class="[
              'h-[50px] px-5 rounded-xl font-black text-[15.5px] cursor-pointer transition-all whitespace-nowrap shadow-sm flex items-center justify-center',
              currentCategory === cat.id 
                ? 'bg-car-item border-2 border-car-accent text-car-text shadow-md' 
                : 'bg-car-item text-car-sub border-2 border-car-border hover:border-car-border-light hover:text-car-text'
            ]"
          >
            {{ cat.name }}
          </button>
          <button 
            @click="refreshApps"
            class="h-[50px] px-5 rounded-xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[15.5px] cursor-pointer shadow-sm transition-all whitespace-nowrap flex items-center justify-center"
          >
            刷新清单
          </button>
          <button 
            @click="openModal('cleanDownload')"
            class="h-[50px] px-5 rounded-xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[15.5px] cursor-pointer shadow-sm transition-all flex items-center justify-center whitespace-nowrap"
          >
            <span>清理下载目录</span>
          </button>
        </div>
      </div>

      <!-- 卡片内置 Body：应用流 (双列网格 · 强制等高 h-full 对齐基准线) -->
      <div v-if="filteredApps.length > 0" class="grid grid-cols-2 gap-4 items-stretch">
        <div 
          v-for="app in filteredApps" 
          :key="app.id"
          @click="openModal('appDetail', app)"
          class="bg-car-item border-2 border-car-border hover:border-car-accent rounded-2xl p-5 flex flex-col justify-between cursor-pointer transition-all shadow-md group h-full min-h-[190px]"
        >
          <div class="flex-1 min-w-0 flex items-center space-x-4 py-2">
            <div class="flex-1 min-w-0 flex flex-col justify-center space-y-1.5">
              <div class="flex items-center justify-between space-x-3">
                <span class="text-[20px] font-black text-car-text group-hover:text-car-accent transition-colors truncate" :title="app.name">
                  {{ app.name }}
                </span>
                <span v-if="store.downloadProgress[app.id]" class="h-[34px] border px-3.5 rounded-xl bg-car-card border-car-border text-car-text font-black text-[13px] shrink-0 whitespace-nowrap inline-flex items-center shadow-sm">
                  <StatusDot class="mr-2" size="sm" :color="store.downloadProgress[app.id].status === 'paused' ? 'warn' : (store.downloadProgress[app.id].status === 'completed' ? 'ok' : 'info')" :pulse="store.downloadProgress[app.id].status === 'downloading'" />
                  {{ store.downloadProgress[app.id].status === 'paused' ? '已暂停' : (store.downloadProgress[app.id].status === 'completed' ? '已下载' : `下载中 ${store.downloadProgress[app.id].percent || 0}%`) }}
                </span>
                <span v-else class="h-[34px] bg-car-card border-2 border-car-border px-3 rounded-xl text-car-sub font-mono font-black text-[13px] shrink-0 whitespace-nowrap flex items-center justify-center">
                  {{ app.size }}
                </span>
              </div>
              <p class="text-[15px] text-car-sub font-medium leading-relaxed line-clamp-2">{{ getBriefDesc(app) }}</p>
            </div>
          </div>

          <div class="h-[52px] px-5 rounded-2xl bg-car-card border-2 border-car-border group-hover:border-car-accent/60 flex items-center justify-between transition-all mt-auto shrink-0">
            <span class="text-[14.5px] text-car-sub font-bold">点击查看版本详情与适配说明</span>
            <span class="text-[15px] text-car-accent font-black group-hover:translate-x-1 transition-transform flex items-center space-x-1">
              <span>查看详情与安装</span>
              <span>➔</span>
            </span>
          </div>
        </div>
      </div>

      <!-- 纯云端拉取中状态 (彻底去除本地静态兜底，100% 动态云端加载) -->
      <div v-else class="bg-car-item border-2 border-car-border rounded-2xl p-12 flex flex-col items-center justify-center space-y-4 shadow-sm min-h-[220px]">
        <AppSpinner />
        <div class="text-[19px] text-car-text font-black">正在从云端获取最新应用商城清单...</div>
        <div class="text-[15px] text-car-sub font-bold">已直连 dl.onepve.com 专属源，实时拉取已实测车机软件</div>
        <button 
          @click="refreshApps"
          class="mt-3 h-[50px] px-8 rounded-2xl bg-car-card border-2 border-car-accent text-car-text font-black text-[17px] cursor-pointer hover:border-car-accent shadow-md active:scale-95"
        >
          重新拉取云端清单
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import StatusDot from '../components/StatusDot.vue';
import AppSpinner from '../components/AppSpinner.vue';
import { store, openModal } from '../store';

const currentCategory = ref('all');

const categories = [
  { id: 'all', name: '全部' },
  { id: 'navi', name: '车载导航' },
  { id: 'music', name: '音乐应用' },
  { id: 'tool', name: '车机工具' }
];

const filteredApps = computed(() => {
  if (!store.apps || store.apps.length === 0) return [];
  if (currentCategory.value === 'all') return store.apps;
  return store.apps.filter(app => app.category === currentCategory.value);
});

function refreshApps() {
  store.refreshApps();
}

function getBriefDesc(app) {
  if (app.desc) {
    const lines = app.desc.split('\n');
    return lines[0];
  }
  return '吉利车机深度优化版本';
}
</script>
