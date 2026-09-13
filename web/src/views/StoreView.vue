<template>
  <div class="flex flex-col space-y-7">
    <!-- 实测机型声明 (OnePve 极简美学：上下舒展充盈、车规大胶囊徽标、告别压窄逼仄) -->
    <div class="bg-car-item border-2 border-car-border rounded-3xl p-6 flex items-center shadow-md">
      <div class="h-[48px] px-5 rounded-2xl bg-car-card border-2 border-car-accent text-car-accent font-black text-[16px] mr-5 shrink-0 shadow-sm flex items-center justify-center">
        实测机型声明
      </div>
      <div class="text-[17px] text-car-text font-bold leading-relaxed">
        以下精选车载软件已在 <span class="text-car-accent font-black underline decoration-car-accent/40">吉利缤越COOL 2022 (亿咖通 E02 / IHU516G / Android 9)</span> 专车深度实测调优，其他车型车友请自行评估后安全安装。
      </div>
    </div>

    <!-- 分类过滤与工具菜单 (车规标准大按钮：统一 h-[52px]、大号触控靶区、纯净无Emoji) -->
    <div class="flex items-center justify-between pt-1">
      <div class="text-[23px] font-black text-car-text tracking-wide">精选车机应用列表</div>
      <div class="flex items-center space-x-3">
        <button 
          v-for="cat in categories" 
          :key="cat.id"
          @click="currentCategory = cat.id"
          :class="[
            'h-[52px] px-6 rounded-2xl font-black text-[16.5px] cursor-pointer transition-all whitespace-nowrap shadow-sm flex items-center justify-center',
            currentCategory === cat.id 
              ? 'bg-car-item border-2 border-car-accent text-car-text shadow-md' 
              : 'bg-car-item text-car-sub border-2 border-car-border hover:border-car-border-light hover:text-car-text'
          ]"
        >
          {{ cat.name }}
        </button>
        <button 
          @click="refreshApps"
          class="h-[52px] px-6 rounded-2xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[16.5px] cursor-pointer shadow-sm transition-all whitespace-nowrap flex items-center justify-center"
        >
          刷新清单
        </button>
        <button 
          @click="openModal('cleanDownload')"
          class="h-[52px] px-6 rounded-2xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[16.5px] cursor-pointer shadow-sm transition-all flex items-center justify-center whitespace-nowrap"
        >
          <span>清理下载目录</span>
        </button>
      </div>
    </div>

    <!-- 应用流 (双列网格 · 强制等高 h-full 对齐基准线) -->
    <div class="grid grid-cols-2 gap-5 items-stretch">
      <div 
        v-for="app in filteredApps" 
        :key="app.id"
        @click="openModal('appDetail', app)"
        class="bg-car-card border-2 border-car-border hover:border-car-accent rounded-3xl p-6 flex flex-col justify-between cursor-pointer transition-all shadow-md group h-full min-h-[190px]"
      >
        <div class="flex-1 min-w-0 flex flex-col justify-center space-y-2 py-1">
          <div class="flex items-center justify-between space-x-3">
            <span class="text-[21px] font-black text-car-text group-hover:text-car-accent transition-colors flex-1 min-w-0 truncate" :title="app.name">
              {{ app.name }}
            </span>
            <span v-if="store.downloadProgress[app.id]" class="h-[36px] border px-4 rounded-xl bg-car-item border-car-border text-car-text font-black text-[13.5px] shrink-0 whitespace-nowrap inline-flex items-center shadow-sm">
              <span :class="['w-2.5 h-2.5 rounded-full mr-2', store.downloadProgress[app.id].status === 'paused' ? 'bg-amber-400 shadow-[0_0_6px_#F59E0B]' : (store.downloadProgress[app.id].status === 'completed' ? 'bg-emerald-500 shadow-[0_0_6px_#10B981]' : 'bg-sky-500 shadow-[0_0_6px_#0EA5E9] animate-pulse')]"></span>
              {{ store.downloadProgress[app.id].status === 'paused' ? '已暂停' : (store.downloadProgress[app.id].status === 'completed' ? '已下载' : `下载中 ${store.downloadProgress[app.id].percent || 0}%`) }}
            </span>
            <span v-else class="h-[36px] bg-car-item border-2 border-car-border px-3.5 rounded-xl text-car-sub font-mono font-black text-[13.5px] shrink-0 whitespace-nowrap flex items-center justify-center">
              {{ app.size }}
            </span>
          </div>
          <p class="text-[16px] text-car-sub font-medium leading-relaxed line-clamp-2">{{ getBriefDesc(app) }}</p>
        </div>

        <div class="h-[52px] px-5 rounded-2xl bg-car-item border-2 border-car-border group-hover:border-car-accent/60 flex items-center justify-between transition-all mt-auto shrink-0">
          <span class="text-[14.5px] text-car-sub font-bold">点击查看版本详情与避坑实测</span>
          <span class="text-[15px] text-car-accent font-black group-hover:translate-x-1 transition-transform flex items-center space-x-1">
            <span>查看详情与安装</span>
            <span>➔</span>
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { store, bridge, openModal, showToast } from '../store';
import { CLOUD_APPS } from '../data/apps';

const currentCategory = ref('all');
const categories = [
  { id: 'all', name: '全部' },
  { id: 'navigation', name: '车载导航' },
  { id: 'music', name: '音乐应用' },
  { id: 'tools', name: '车机工具' }
];

const allApps = computed(() => {
  return store.apps.length > 0 ? store.apps : CLOUD_APPS;
});

const filteredApps = computed(() => {
  if (currentCategory.value === 'all') return allApps.value;
  return allApps.value.filter(a => a.category === currentCategory.value);
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
