<template>
  <ModalWrapper 
    :show="!!store.modals.appDetail" 
    :title="app?.name || '软件详情'" 
    :badge="app?.badgeText || '应用详情'"
    maxWidthClass="max-w-[1040px]"
    @close="closeModal('appDetail')"
  >
    <div v-if="app" class="flex flex-col space-y-5">
      <!-- 基础元数据徽标栏 -->
      <div class="flex items-center flex-wrap">
        <span class="text-[13.5px] px-3 py-1 rounded-md bg-car-item border border-car-border text-car-text font-mono font-bold mr-2.5 mb-2.5">
          包名: {{ app.package_name }}
        </span>
        <span class="text-[13.5px] px-3 py-1 rounded-md bg-car-item border border-car-border text-car-text font-bold mr-2.5 mb-2.5">
          体积: {{ app.size }}
        </span>
        <span class="text-[13.5px] px-3 py-1 rounded-md bg-car-item border border-car-border text-car-text font-bold mr-2.5 mb-2.5">
          架构: arm64-v8a (纯64位)
        </span>
        <span class="text-[13.5px] px-3 py-1 rounded-md bg-car-item border border-car-border text-car-text font-bold mr-2.5 mb-2.5">
          编译发布: {{ app.updated_at }}
        </span>
      </div>

      <!-- 高德 8.5 专车惯导避坑高危警示 -->
      <div 
        v-if="app.id === 'amap_ae86'" 
        class="bg-amber-500/10 border-2 border-amber-500/40 rounded-2xl p-4 text-amber-500 text-[16px] font-bold leading-relaxed"
      >
        <b>【惯导避坑铁律】</b>高德 8.5 专车版惯导参数默认匹配星瑞 (CMA)；在缤越 COOL (BMA) 等车型上安装后<b>必须进入高德设置将【惯性导航】关闭</b>，关闭后走原生 GPS 即可 100% 稳定流畅不漂移！
      </div>

      <!-- 详细特性说明卡片 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col space-y-2">
        <span class="text-[18px] font-black text-car-text">功能特性与适配说明</span>
        <p class="text-[16.5px] text-car-sub font-semibold leading-relaxed whitespace-pre-wrap">{{ app.desc }}</p>
      </div>

      <!-- 动态全宽下载进度条 (下载中/暂停/完成动态展开) -->
      <div v-if="currentTask" class="bg-car-item border-2 border-car-accent/40 rounded-2xl p-4 flex flex-col space-y-2.5 shadow-md">
        <div class="flex items-center justify-between text-[15px] font-bold text-car-text">
          <div class="flex items-center space-x-2">
            <span :class="['w-2.5 h-2.5 rounded-full', currentTask.status === 'paused' ? 'bg-amber-400' : (currentTask.status === 'completed' ? 'bg-emerald-400' : 'bg-car-accent animate-pulse')]"></span>
            <span>{{ taskStatusTitle }}</span>
          </div>
          <span class="font-mono text-car-accent font-black text-[16px]">{{ currentTask.percent || 0 }}%</span>
        </div>
        <div class="h-3 w-full bg-car-card rounded-full overflow-hidden border border-car-border">
          <div 
            class="h-full transition-all duration-200"
            :class="[currentTask.status === 'paused' ? 'bg-amber-400' : (currentTask.status === 'completed' ? 'bg-emerald-500' : 'bg-car-accent')]"
            :style="{ width: (currentTask.percent || 0) + '%' }"
          ></div>
        </div>
        <div class="flex items-center justify-between pt-0.5">
          <span class="text-[13.5px] text-car-sub font-bold">
            {{ currentTask.status === 'completed' ? '✅ 安装包已下载就绪，可随时点击下方按钮进行安装' : (currentTask.status === 'paused' ? '⏸️ 当前下载已暂停，点击下方按钮可继续或取消' : `⚡ 正在高速下载中 (实时速度: ${currentTask.speed || '正在连接...'})`) }}
          </span>
          <span class="text-[13px] font-mono text-car-accent font-black">
            {{ currentTask.status === 'completed' ? '100%' : `${currentTask.percent || 0}%` }}
          </span>
        </div>
      </div>
    </div>

    <!-- 底部 66px 巨型车规大触控操作栏 (操作按钮全覆盖) -->
    <template #footer>
      <div class="flex items-center justify-between w-full">
        <!-- 专家模式卡主题通道按键 (严密安全限制：仅限高德地图底包，非地图坚决禁止卡主题) -->
        <button 
          v-if="store.settings.expert_rabbit && isMapApp"
          @click="openRabbitGuide"
          class="h-[60px] px-6 bg-amber-500/15 border-2 border-amber-500/50 rounded-2xl text-amber-300 font-black text-[17.5px] cursor-pointer hover:bg-amber-500/25 ring-2 ring-amber-500/20 shadow-md flex items-center"
        >
          <span class="mr-2">⚡</span>
          <span>专家模式: 卡主题安装向导</span>
        </button>
        <div v-else></div>

        <!-- 右侧核心操作按钮流 -->
        <div class="flex items-center space-x-3">
          <!-- 1. 正在下载中：提供大尺寸暂停与取消按钮 -->
          <template v-if="currentTask?.status === 'downloading'">
            <button 
              @click="pauseDownload"
              class="min-h-[66px] px-8 bg-car-item border-2 border-amber-500/60 text-amber-400 hover:border-amber-400 rounded-2xl font-black text-[19px] cursor-pointer shadow-md transition-all flex items-center"
            >
              <span class="mr-2">⏸️</span> 暂停下载
            </button>
            <button 
              @click="cancelDownload"
              class="min-h-[66px] px-8 bg-car-item border-2 border-rose-500/60 text-rose-400 hover:border-rose-400 rounded-2xl font-black text-[19px] cursor-pointer shadow-md transition-all flex items-center"
            >
              <span class="mr-2">🛑</span> 取消下载
            </button>
          </template>

          <!-- 2. 暂停中：提供大尺寸继续与取消按钮 -->
          <template v-else-if="currentTask?.status === 'paused'">
            <button 
              @click="resumeDownload"
              class="min-h-[66px] px-8 bg-car-item border-2 border-car-accent text-car-text hover:border-car-accent rounded-2xl font-black text-[19px] cursor-pointer shadow-md ring-2 ring-car-accent/20 transition-all flex items-center"
            >
              <span class="mr-2">▶️</span> 继续下载
            </button>
            <button 
              @click="cancelDownload"
              class="min-h-[66px] px-8 bg-car-item border-2 border-rose-500/60 text-rose-400 hover:border-rose-400 rounded-2xl font-black text-[19px] cursor-pointer shadow-md transition-all flex items-center"
            >
              <span class="mr-2">🛑</span> 取消下载
            </button>
          </template>

          <!-- 3. 下载完成：提供立即安装应用与重新下载按钮 -->
          <template v-else-if="currentTask?.status === 'completed'">
            <button 
              @click="handleInstallDownloaded"
              class="min-h-[66px] px-10 bg-car-item border-2 border-emerald-500 text-emerald-400 rounded-2xl font-black text-[20px] cursor-pointer hover:border-emerald-400 shadow-lg ring-2 ring-emerald-500/20 transition-all flex items-center"
            >
              <span class="mr-2">✅</span> 立即安装应用
            </button>
            <button 
              @click="handleInstallAction"
              class="min-h-[66px] px-6 bg-car-card border border-car-border text-car-sub hover:text-car-text rounded-2xl font-bold text-[16px] cursor-pointer shadow-sm transition-all"
            >
              🔄 重新下载
            </button>
          </template>

          <!-- 4. 初始未下载：原有的立即下载安装 -->
          <template v-else>
            <button 
              @click="handleInstallAction"
              class="min-h-[66px] px-10 bg-car-item border-2 border-car-accent rounded-2xl text-car-text font-black text-[21px] cursor-pointer hover:border-car-accent shadow-lg ring-2 ring-car-accent/20 transition-all"
            >
              {{ actionButtonText }}
            </button>
          </template>
        </div>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { computed } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, showToast } from '../../store';

const app = computed(() => store.modals.appDetail);
const currentTask = computed(() => {
  if (!app.value) return null;
  return store.downloadProgress[app.value.id] || null;
});

const taskStatusTitle = computed(() => {
  if (!currentTask.value) return '';
  if (currentTask.value.status === 'paused') return '下载已暂停';
  if (currentTask.value.status === 'completed') return '下载完成 (100%)';
  return `正在极速下载中 (${currentTask.value.speed || '正在连接'})...`;
});

const isMapApp = computed(() => {
  if (!app.value) return false;
  const id = (app.value.id || '').toLowerCase();
  const pkg = (app.value.package_name || '').toLowerCase();
  const name = (app.value.name || '').toLowerCase();
  const category = (app.value.category || '').toLowerCase();
  const filename = (app.value.filename || '').toLowerCase();
  return category === 'navigation' || pkg.includes('autonavi') || filename.startsWith('automap') || name.includes('高德') || name.includes('地图');
});

const actionButtonText = computed(() => {
  if (!app.value) return '立即下载安装';
  return app.value.statusText || '立即下载安装';
});

function handleInstallAction() {
  if (!app.value) return;
  bridge.call('downloadApp', app.value.id, app.value.url, app.value.filename);
  showToast(`已下发下载任务: ${app.value.name}`);
}

function pauseDownload() {
  if (!app.value) return;
  bridge.call('pauseDownload', app.value.id);
  if (store.downloadProgress[app.value.id]) {
    store.downloadProgress[app.value.id].status = 'paused';
  }
  showToast('已下发暂停指令');
}

function resumeDownload() {
  if (!app.value) return;
  bridge.call('downloadApp', app.value.id, app.value.url, app.value.filename);
  if (store.downloadProgress[app.value.id]) {
    store.downloadProgress[app.value.id].status = 'downloading';
  }
  showToast('正在继续下载...');
}

function cancelDownload() {
  if (!app.value) return;
  bridge.call('cancelDownload', app.value.id);
  delete store.downloadProgress[app.value.id];
  showToast('已取消下载任务');
}

function handleInstallDownloaded() {
  if (!app.value) return;
  const filename = currentTask.value?.savedFileName || app.value.filename;
  bridge.call('installDownloadedApk', filename);
  showToast(`正在调起系统安装: ${filename}`);
}

function openRabbitGuide() {
  if (!app.value || !isMapApp.value) {
    showToast('安全保护：非地图类应用严禁使用卡主题方式！');
    return;
  }
  const currentApp = app.value;
  closeModal('appDetail');
  store.modals.rabbitInstall = currentApp;
}
</script>
