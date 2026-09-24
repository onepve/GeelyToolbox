<template>
  <div class="flex flex-col space-y-5">
    <!-- 1. 精选商城总控中枢大卡片 (将实测机型声明、分类筛选与快捷工具 100% 完整封装于单一大卡片内，彻底消除卡片间悬浮断层与重叠) -->
    <div class="bg-car-item border-2 border-car-border rounded-2xl p-6 shadow-md flex flex-col space-y-4">
      <!-- 上半区：安全实测认证标识 + 刷新清单与清理下载目录操作栏 (四周严格对称留白，垂直绝对居中) -->
      <div class="flex items-center justify-between pb-4 border-b border-car-border/50">
        <div class="flex items-center space-x-4 min-w-0 pr-6">
          <!-- 翡翠安全认证徽标 (>=48px 车规大胶囊) -->
          <div class="w-[50px] h-[50px] rounded-xl bg-emerald-500/15 border-2 border-emerald-500/40 flex items-center justify-center shrink-0 shadow-sm">
            <svg class="w-6 h-6 text-emerald-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
              <path d="m9 12 2 2 4-4" />
            </svg>
          </div>
          <div class="flex flex-col min-w-0 justify-center">
            <div class="flex items-center space-x-2.5">
              <span class="text-[19px] font-black text-car-text tracking-wide whitespace-nowrap">实测机型声明</span>
              <span class="px-2.5 py-0.5 rounded-full text-[12.5px] font-black bg-emerald-500/20 text-emerald-400 border border-emerald-500/40 whitespace-nowrap">
                专车真机已适配
              </span>
            </div>
            <div class="text-[14px] text-car-sub font-bold mt-1 leading-snug">
              吉利缤越 COOL (2022款 / E02 / IHU516G / Android 9) 专车深度实测调优，其他车型车友请自行评估后安全安装。
            </div>
          </div>
        </div>

        <!-- 顶部操作按钮组：刷新清单与清理下载目录 (右侧严格保留内边距防贴边) -->
        <div class="flex items-center space-x-3 shrink-0">
          <button 
            @click="refreshApps"
            class="h-[50px] px-5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[15.5px] cursor-pointer shadow-sm transition-all whitespace-nowrap flex items-center justify-center active:scale-95"
          >
            刷新清单
          </button>
          <button 
            @click="openModal('cleanDownload')"
            class="h-[50px] px-5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[15.5px] cursor-pointer shadow-sm transition-all flex items-center justify-center whitespace-nowrap active:scale-95"
          >
            <span>清理下载目录</span>
          </button>
        </div>
      </div>

      <!-- 下半区：应用分类筛选标签栏 (高对比度车规胶囊，四周对称留白) -->
      <div class="flex items-center justify-between pt-1">
        <div class="flex items-center space-x-3 shrink-0 mr-4">
          <StatusDot size="md" color="accent" />
          <span class="text-[18.5px] font-black text-car-text tracking-wide whitespace-nowrap">应用分类</span>
        </div>
        <div class="flex items-center space-x-3">
          <button 
            v-for="cat in categories" 
            :key="cat.id"
            @click="currentCategory = cat.id"
            :class="[
              'h-[50px] px-6 rounded-xl font-black text-[15.5px] cursor-pointer transition-all whitespace-nowrap shadow-sm flex items-center justify-center active:scale-95',
              currentCategory === cat.id 
                ? 'bg-car-card border-2 border-car-accent text-car-accent shadow-md' 
                : 'bg-car-card text-car-text border-2 border-car-border hover:border-car-border-light'
            ]"
          >
            {{ cat.name }}
          </button>
        </div>
      </div>
    </div>

    <!-- 2. 应用列表主体 (直接承接上方总控大卡片，双列应用网格独立沉稳，卡片间再无任何裸露游离按钮) -->
    <div v-if="filteredApps.length > 0" class="grid grid-cols-2 gap-4 items-stretch">
      <div 
        v-for="app in filteredApps" 
        :key="app.id"
        @click="openModal('appDetail', app)"
        class="bg-car-item border-2 border-car-border hover:border-car-accent rounded-2xl p-5 flex flex-col justify-between cursor-pointer transition-all shadow-md group h-full min-h-[190px]"
      >
        <div class="flex items-start space-x-4">
          <div class="w-16 h-16 rounded-2xl bg-car-card border-2 border-car-border flex items-center justify-center shrink-0 shadow-sm overflow-hidden p-1.5 group-hover:border-car-accent transition-colors">
            <img 
              v-if="app.icon" 
              :src="app.icon" 
              :alt="app.name"
              class="w-full h-full object-contain rounded-xl"
              @error="(e) => (e.target.style.display = 'none')"
            />
            <svg v-else class="w-8 h-8 text-car-sub" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="5" y="2" width="14" height="20" rx="2" ry="2"/>
              <line x1="12" y1="18" x2="12.01" y2="18"/>
            </svg>
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex items-center justify-between">
              <h3 class="text-[19px] font-black text-car-text group-hover:text-car-accent transition-colors truncate">
                {{ app.name }}
              </h3>
              <span class="text-[13.5px] font-bold text-car-sub bg-car-card px-2.5 py-0.5 rounded-lg border border-car-border shrink-0 ml-2">
                {{ app.size }}
              </span>
            </div>
            <p class="text-[14.5px] text-car-sub font-bold line-clamp-2 mt-1.5 leading-snug">
              {{ app.desc || app.summary }}
            </p>
          </div>
        </div>

        <div class="mt-4 pt-3.5 border-t border-car-border/40 flex items-center justify-between">
          <div class="flex items-center space-x-2">
            <span class="px-2.5 py-0.5 rounded-md text-[13px] font-bold bg-car-card border border-car-border text-car-sub">
              {{ app.category || '车机软件' }}
            </span>
            <span v-if="app.version" class="text-[13px] font-bold text-car-sub">
              v{{ app.version }}
            </span>
          </div>
          <div class="text-[14.5px] font-black text-car-accent flex items-center space-x-1 group-hover:translate-x-1 transition-transform">
            <span>查看详情与安装</span>
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M9 5l7 7-7 7" />
            </svg>
          </div>
        </div>
      </div>
    </div>

    <!-- 纯云端拉取中状态 -->
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
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { store, bridge, openModal, showToast } from '../store';
import StatusDot from '../components/StatusDot.vue';
import AppSpinner from '../components/AppSpinner.vue';

const currentCategory = ref('all');
const categories = [
  { id: 'all', name: '全部' },
  { id: 'navigation', name: '车载导航' },
  { id: 'music', name: '音乐应用' },
  { id: 'tools', name: '车机工具' }
];

const allApps = computed(() => {
  return store.apps || [];
});

const filteredApps = computed(() => {
  if (currentCategory.value === 'all') return allApps.value;
  return allApps.value.filter(a => a.category === currentCategory.value);
});

onMounted(() => {
  // 进入商城视图时，若未加载或需更新，立即主动从云端异步拉取最新 apps.json
  if (!store.apps || store.apps.length === 0) {
    bridge.call('refreshCloudApps');
  }
});

function getBriefDesc(app) {
  const raw = app?.desc || app?.description || '';
  return raw.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
}

function refreshApps() {
  bridge.call('refreshCloudApps');
  showToast('正在从云端拉取最新应用清单...');
}

function handleDownload(app) {
  const url = app.download_url || app.url;
  bridge.call('downloadApp', app.id, url, app.filename);
  showToast('已下发下载任务: ' + app.name);
}
</script>
