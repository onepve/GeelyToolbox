<template>
  <div class="flex flex-col space-y-6">
    <!-- 实测机型声明 (OnePve 极简美学：日夜自适应高对比，告别眩光与日间隐形) -->
    <div class="bg-car-item border-2 border-car-border rounded-2xl p-5 flex items-center shadow-sm">
      <span class="px-3 py-1.5 rounded-lg bg-car-accent/20 border border-car-accent text-car-accent font-black text-[15px] mr-4 shrink-0">
        实测机型声明
      </span>
      <div class="text-[16px] text-car-text font-bold leading-relaxed">
        以下精选软件已在 <span class="text-car-accent font-black underline decoration-car-accent/40">吉利缤越COOL 2022 (亿咖通 E02 / IHU516G / Android 9)</span> 专车实测调优，其他车型请自行评估后安全安装。
      </div>
    </div>

    <!-- 分类过滤与刷新 -->
    <div class="flex items-center justify-between">
      <div class="text-[22px] font-black text-car-text">精选车机应用列表</div>
      <div class="flex space-x-2">
        <button 
          v-for="cat in categories" 
          :key="cat.id"
          @click="currentCategory = cat.id"
          :class="[
            'px-5 py-2.5 rounded-xl font-extrabold text-[16px] cursor-pointer transition-all',
            currentCategory === cat.id 
              ? 'bg-car-item border-2 border-car-accent text-car-text font-black ring-2 ring-car-accent/20' 
              : 'bg-car-item text-car-sub border border-car-border hover:border-car-border-light'
          ]"
        >
          {{ cat.name }}
        </button>
        <button 
          @click="refreshApps"
          class="px-5 py-2.5 rounded-xl bg-car-card border border-car-border text-car-text font-extrabold text-[16px] cursor-pointer hover:border-car-border-light"
        >
          刷新
        </button>
      </div>
    </div>

    <!-- 应用流 (双列网格) -->
    <div class="grid grid-cols-2 gap-4">
      <div 
        v-for="app in filteredApps" 
        :key="app.id"
        @click="openModal('appDetail', app)"
        class="bg-car-card border-2 border-car-border rounded-2xl p-6 flex flex-col justify-between cursor-pointer hover:border-car-border-light transition-all shadow-md group"
      >
        <div class="flex flex-col space-y-2">
          <div class="flex items-center justify-between">
            <span class="text-[21px] font-black text-car-text group-hover:text-car-accent transition-colors">{{ app.name }}</span>
            <span class="text-[13px] bg-car-item border border-car-border px-3 py-1 rounded-md text-car-sub font-mono font-bold">{{ app.size }}</span>
          </div>
          <p class="text-[16px] text-car-sub font-medium leading-relaxed pt-1">{{ app.desc }}</p>
        </div>

        <div class="flex items-center justify-between pt-4 mt-3 border-t border-car-border/60">
          <span class="text-[14px] text-car-sub/80 font-bold">点击查看详细信息与避坑指南</span>
          <span class="text-[14px] text-car-accent font-black group-hover:translate-x-1 transition-transform">查看详情 →</span>
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

function refreshApps() {
  bridge.call('refreshCloudApps');
  showToast('正在从云端拉取最新应用清单...');
}

function handleDownload(app) {
  bridge.call('downloadApp', app.id, app.url, app.filename);
  showToast('已下发下载任务: ' + app.name);
}
</script>
