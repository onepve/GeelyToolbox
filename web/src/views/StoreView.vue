<template>
  <div class="flex flex-col gap-5">
    <div class="bg-amber-500/10 border border-amber-500/30 rounded-xl p-4 text-[16px] text-amber-200 font-bold">
      <b>【实测机型声明】</b>以下软件仅在「吉利缤越COOL 2022 (亿咖通 E02 / IHU516G / Android 9)」实测通过，其他车型请自行评估后安装。
    </div>

    <!-- 分类过滤与刷新 -->
    <div class="flex items-center justify-between">
      <div class="text-[22px] font-black text-car-text">精选车机应用列表</div>
      <div class="flex gap-2">
        <button 
          v-for="cat in categories" 
          :key="cat.id"
          @click="currentCategory = cat.id"
          :class="[
            'px-5 py-2.5 rounded-xl font-extrabold text-[16px] cursor-pointer transition-all',
            currentCategory === cat.id ? 'bg-car-item border-2 border-car-accent text-car-text font-black ring-2 ring-car-accent/20' : 'bg-car-item text-car-sub border border-car-border hover:border-car-border-light'
          ]"
        >
          {{ cat.name }}
        </button>
        <button 
          @click="refreshApps"
          class="px-5 py-2.5 rounded-xl bg-white/10 border border-car-border text-car-text font-extrabold text-[16px] cursor-pointer hover:bg-white/20"
        >
          刷新
        </button>
      </div>
    </div>

    <!-- 应用流 -->
    <div class="grid grid-cols-2 gap-4">
      <div 
        v-for="app in filteredApps" 
        :key="app.id"
        @click="openModal('appDetail', app)"
        class="bg-car-card border border-car-border rounded-2xl p-5 flex flex-col justify-between gap-4 cursor-pointer hover:border-car-border-light transition-all"
      >
        <div class="flex flex-col gap-1.5">
          <div class="flex items-center justify-between">
            <span class="text-[20px] font-black text-car-text">{{ app.name }}</span>
            <span class="text-[13px] bg-white/10 px-2.5 py-1 rounded text-car-sub font-bold">{{ app.size }}</span>
          </div>
          <p class="text-[15.5px] text-car-sub font-semibold leading-relaxed">{{ app.desc }}</p>
        </div>

        <button 
          @click.stop="handleDownload(app)"
          class="min-h-[58px] bg-car-item border-2 border-car-accent rounded-xl text-car-text font-black text-[18px] cursor-pointer hover:border-car-accent-light shadow-md ring-2 ring-car-accent/15"
        >
          {{ app.statusText || '立即下载安装' }}
        </button>
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
