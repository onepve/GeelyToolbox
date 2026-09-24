<template>
  <div class="flex flex-col space-y-6">
    <!-- 1. 精选商城总控中枢大卡片 (工业级车规工整布局：p-7 px-8 豪华呼吸留白，水平中轴绝对对齐，彻底告别贴边与悬浮) -->
    <div class="bg-car-item border-2 border-car-border rounded-2xl p-7 px-8 shadow-xl block">
      <!-- 1.1 上半区：实测机型声明与全域快捷工具 (垂直绝对居中，留白对称) -->
      <div class="flex items-center justify-between min-h-[58px]">
        <!-- 左侧：安全实测认证标识与说明 -->
        <div class="flex items-center space-x-4 flex-1 min-w-0 pr-6">
          <div class="w-[52px] h-[52px] rounded-full border-2 border-emerald-500/40 bg-emerald-500/10 flex items-center justify-center shrink-0 shadow-inner">
            <svg class="w-7 h-7 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
            </svg>
          </div>
          <div class="flex flex-col space-y-1">
            <div class="flex items-center space-x-2.5">
              <span class="text-[20px] font-black text-car-text tracking-wide whitespace-nowrap">实测机型声明</span>
              <span class="px-2.5 py-0.5 text-[12.5px] font-black rounded-full border bg-emerald-500/20 border-emerald-500/40 text-emerald-300 inline-flex items-center shrink-0">
                专车真机已适配
              </span>
            </div>
            <div class="text-[14.5px] text-car-sub font-bold leading-normal">
              吉利缤越 COOL (2022款 / E02 / IHU516G / Android 9) 专车深度实测调优，其他车型车友请自行评估后安全安装。
            </div>
          </div>
        </div>

        <!-- 右侧：全域操作按钮 (h-[50px] 车规大按键) -->
        <div class="flex items-center space-x-3 shrink-0">
          <button 
            @click="refreshApps"
            class="h-[50px] px-6 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[15.5px] cursor-pointer shadow-md transition-all whitespace-nowrap flex items-center justify-center active:scale-95"
          >
            刷新清单
          </button>
          <button 
            @click="openModal('cleanDownload')"
            class="h-[50px] px-6 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[15.5px] cursor-pointer shadow-md transition-all flex items-center justify-center whitespace-nowrap active:scale-95"
          >
            <span>清理下载目录</span>
          </button>
        </div>
      </div>

      <!-- 1.2 中轴分割线 (沉稳车规分界) -->
      <div class="border-b border-car-border/60 my-5"></div>

      <!-- 1.3 下半区：应用分类控制栏 (基线严格对齐，安全呼吸留白充足) -->
      <div class="flex items-center justify-between min-h-[50px]">
        <div class="flex items-center space-x-2.5 h-[50px] shrink-0">
          <StatusDot size="md" color="accent" />
          <span class="text-[19px] font-black text-car-text tracking-wide">应用分类</span>
          <span class="text-[13.5px] text-car-sub font-bold ml-1">（点击标签极速筛选）</span>
        </div>
        <div class="flex items-center space-x-3 shrink-0">
          <button 
            v-for="cat in categories" 
            :key="cat.id"
            @click="currentCategory = cat.id"
            :class="[
              'h-[50px] px-6 rounded-xl font-black text-[15.5px] cursor-pointer transition-all whitespace-nowrap shadow-md flex items-center justify-center active:scale-95',
              currentCategory === cat.id 
                ? 'bg-car-card border-2 border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-lg' 
                : 'bg-car-card text-car-text/80 border-2 border-car-border hover:border-car-border-light hover:text-car-text'
            ]"
          >
            {{ cat.name }}
          </button>
        </div>
      </div>
    </div>

    <!-- 2. 下方精选车机应用网格列表 (双列车规卡片排布) -->
    <div v-if="filteredApps.length > 0" class="grid grid-cols-2 gap-4 items-stretch">
      <div 
        v-for="app in filteredApps" 
        :key="app.id"
        @click="openModal('appDetail', app)"
        class="bg-car-item border-2 border-car-border hover:border-car-accent rounded-2xl p-5 flex flex-col justify-between cursor-pointer transition-all shadow-md group h-full min-h-[190px]"
      >
        <div class="flex items-start space-x-4">
          <!-- 应用图标 -->
          <div class="w-16 h-16 rounded-2xl bg-car-card border border-car-border p-2 shrink-0 flex items-center justify-center shadow-inner overflow-hidden">
            <img 
              v-if="app.icon" 
              :src="app.icon" 
              :alt="app.name" 
              class="w-full h-full object-contain rounded-xl"
              @error="(e) => e.target.style.display = 'none'"
            />
            <svg v-else class="w-8 h-8 text-car-sub" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="5" y="2" width="14" height="20" rx="2" ry="2"/>
              <line x1="12" y1="18" x2="12.01" y2="18"/>
            </svg>
          </div>

          <!-- 应用基本信息 -->
          <div class="flex-1 min-w-0">
            <div class="flex items-center justify-between">
              <div class="text-[19px] font-black text-car-text truncate group-hover:text-car-accent transition-colors">
                {{ app.name }}
              </div>
              <span class="text-[13px] font-bold text-car-sub bg-car-card px-2 py-0.5 rounded border border-car-border shrink-0 ml-2">
                {{ app.size || '未知大小' }}
              </span>
            </div>
            <div class="text-[14px] text-car-sub line-clamp-2 mt-1.5 font-bold leading-relaxed">
              {{ getBriefDesc(app) }}
            </div>
          </div>
        </div>

        <!-- 卡片底栏信息 -->
        <div class="mt-4 pt-3 border-t border-car-border/40 flex items-center justify-between">
          <div class="flex items-center space-x-2">
            <span class="text-[12.5px] px-2 py-0.5 rounded bg-car-card border border-car-border text-car-sub font-black">
              {{ app.category || 'tools' }}
            </span>
            <span v-if="app.version" class="text-[12.5px] text-car-sub font-bold">
              v{{ app.version }}
            </span>
          </div>

          <div class="text-[14.5px] font-black text-car-accent group-hover:translate-x-1 transition-transform flex items-center">
            查看详情与安装
            <span class="ml-1 text-[17px]">→</span>
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
