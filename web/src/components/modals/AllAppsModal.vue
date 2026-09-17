<template>
  <ModalWrapper 
    :show="store.modals.allApps" 
    title="车机全量应用高级管理" 
    badge="特权维护"
    maxWidthClass="max-w-[1100px]"
    @close="closeModal('allApps')"
  >
    <div class="flex flex-col space-y-4">
      <!-- 顶部搜索与统计条 -->
      <div class="flex items-center space-x-3">
        <div class="relative flex-1">
          <input 
            v-model="searchQuery"
            type="text" 
            placeholder="搜索应用名称或包名 (如: 高德, ecarx, 微信, 音乐)..."
            class="w-full min-h-[54px] pl-5 pr-12 rounded-2xl bg-car-item border-2 border-car-border text-car-text text-[16px] font-bold focus:border-car-accent outline-none shadow-sm transition-all"
          />
          <span v-if="searchQuery" @click="searchQuery = ''" class="absolute right-4 top-1/2 -translate-y-1/2 text-car-sub hover:text-car-text text-[18px] cursor-pointer">✕</span>
        </div>
        <button 
          @click="loadApps(true)"
          :disabled="isLoading"
          class="min-h-[54px] px-6 rounded-2xl border-2 border-car-border bg-car-item hover:border-car-border-light text-car-text font-black text-[16px] cursor-pointer shadow-sm transition-all shrink-0 flex items-center space-x-2"
        >
          <span>{{ isLoading ? '正在刷新...' : '刷新列表' }}</span>
        </button>
      </div>

      <!-- 类别与状态双重过滤标签 (space-x-3，杜绝裸 flex gap) -->
      <div class="flex items-center justify-between space-x-3">
        <!-- 分类过滤 -->
        <div class="flex items-center space-x-2">
          <button 
            v-for="t in typeFilters"
            :key="t.key"
            @click="currentType = t.key"
            :class="[
              'min-h-[50px] px-4 rounded-xl border-2 text-[14.5px] font-black cursor-pointer transition-all',
              currentType === t.key
                ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/20 shadow-md'
                : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            {{ t.label }} ({{ getCountByType(t.key) }})
          </button>
        </div>

        <!-- 状态过滤 -->
        <div class="flex items-center space-x-2">
          <button 
            v-for="s in stateFilters"
            :key="s.key"
            @click="currentState = s.key"
            :class="[
              'min-h-[50px] px-4 rounded-xl border-2 text-[14.5px] font-black cursor-pointer transition-all',
              currentState === s.key
                ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/20 shadow-md'
                : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            {{ s.label }} ({{ getCountByState(s.key) }})
          </button>
        </div>
      </div>

      <!-- 应用列表视口 (固定 410px 高度独立滚动) -->
      <div class="h-[410px] overflow-y-auto rounded-2xl border border-car-border bg-car-item p-3 space-y-2">
        <div v-if="filteredApps.length === 0" class="h-full flex flex-col items-center justify-center text-car-sub text-[16px] font-bold space-y-2">
          <span>{{ isLoading ? '正在读取整车已安装应用清单...' : '未找到符合条件的应用' }}</span>
        </div>

        <div 
          v-for="app in filteredApps"
          :key="app.pkg"
          class="bg-car-item/90 hover:bg-car-item border border-car-border rounded-xl p-3.5 flex items-center justify-between shadow-sm transition-all"
        >
          <!-- 左侧信息 (含车规拟物圆角图标) -->
          <div class="flex items-center space-x-3.5 min-w-0 flex-1 pr-4">
            <div class="flex flex-col space-y-1 min-w-0 flex-1">
              <div class="flex items-center space-x-2.5">
                <span class="text-[17.5px] font-black text-car-text truncate">{{ app.name }}</span>
                <span 
                  :class="[
                    'text-[11.5px] px-2 py-0.5 rounded-md font-extrabold shrink-0 border',
                    app.isSystem 
                      ? 'bg-rose-500/15 border-rose-500/30 text-rose-400' 
                      : 'bg-cyan-500/15 border-cyan-500/30 text-cyan-400'
                  ]"
                >
                  {{ app.isSystem ? '原厂系统' : '第三方自装' }}
                </span>
                <span 
                  :class="[
                    'text-[11.5px] px-2 py-0.5 rounded-md font-extrabold shrink-0 border',
                    app.frozen 
                      ? 'bg-amber-500/15 border-amber-500/30 text-amber-400' 
                      : 'bg-emerald-500/15 border-emerald-500/30 text-emerald-400'
                  ]"
                >
                  {{ app.frozen ? '已冻结停用' : '活跃运行中' }}
                </span>
              </div>
              <div class="flex items-center space-x-3 text-[12.5px] font-mono text-car-sub">
                <span class="truncate">{{ app.pkg }}</span>
                <span v-if="app.version">· v{{ app.version }}</span>
              </div>
            </div>
          </div>

          <!-- 右侧操作栏 (车规级大触控按钮 >= 50px) -->
          <div class="flex items-center space-x-2.5 shrink-0">
            <button 
              @click="handleLaunch(app)"
              class="min-h-[50px] px-4 rounded-xl border border-car-border bg-car-card hover:border-car-border-light text-car-text text-[14.5px] font-black cursor-pointer shadow-sm"
            >
              打开
            </button>
            <button 
              @click="handleToggleFreeze(app)"
              :class="[
                'min-h-[50px] px-4 rounded-xl border-2 text-[14.5px] font-black cursor-pointer shadow-sm transition-all',
                app.frozen
                  ? 'border-emerald-500/60 text-emerald-400 bg-car-card hover:border-emerald-400'
                  : 'border-amber-500/60 text-amber-400 bg-car-card hover:border-amber-400'
              ]"
            >
              {{ app.frozen ? '解冻' : '冻结' }}
            </button>
            <button 
              @click="handleClearData(app)"
              class="min-h-[50px] px-4 rounded-xl border border-car-border hover:border-amber-500/50 bg-car-card text-car-sub hover:text-amber-400 text-[14.5px] font-black cursor-pointer shadow-sm"
            >
              清除数据
            </button>
            <button 
              v-if="!app.isSystem"
              @click="handleUninstall(app)"
              class="min-h-[50px] px-4 rounded-xl border border-rose-500/40 hover:border-rose-500 bg-rose-500/10 text-rose-400 text-[14.5px] font-black cursor-pointer shadow-sm"
            >
              卸载
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部操作栏 -->
    <template #footer>
      <div class="flex items-center justify-between w-full">
        <div class="text-[13px] text-car-sub font-bold">
          共收录 {{ allApps.length }} 个已安装组件（已自动对系统底层核心组件施加防误删物理保护）
        </div>
        <button 
          @click="closeModal('allApps')"
          class="min-h-[50px] px-8 bg-car-item border-2 border-car-border text-car-text font-black text-[16.5px] rounded-xl cursor-pointer hover:border-car-border-light shadow-sm"
        >
          关闭
        </button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, openModal, showToast } from '../../store';

const allApps = ref([]);
const isLoading = ref(false);
const searchQuery = ref('');
const currentType = ref('user');   // 默认点亮'第三方用户软件'，快速响应并减少初次渲染卡顿
const currentState = ref('all');  // 'all' | 'active' | 'frozen'

const typeFilters = [
  { key: 'user', label: '第三方用户软件' },
  { key: 'system', label: '原厂系统组件' },
  { key: 'all', label: '全部类型' }
];

const stateFilters = [
  { key: 'all', label: '全部状态' },
  { key: 'active', label: '活跃运行' },
  { key: 'frozen', label: '已冻结' }
];

function loadApps(showToastMsg = false) {
  isLoading.value = true;
  try {
    const raw = bridge.call('getAllInstalledApps');
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : (raw || []);
    allApps.value = Array.isArray(parsed) ? parsed : [];
    if (showToastMsg) {
      showToast(`已成功刷新整车已安装软件 (${allApps.value.length} 个)`);
    }
  } catch (e) {
    console.error('loadApps error:', e);
  } finally {
    isLoading.value = false;
  }
}

watch(() => store.modals.allApps, (show) => {
  if (show) {
    loadApps(false);
  }
});

onMounted(() => {
  // 监听原生安装、卸载、更新广播，毫秒级自愈刷新
  window.onPackageChanged = () => {
    if (store.modals.allApps) {
      loadApps(false);
    }
  };
});

function getCountByType(type) {
  if (type === 'all') return allApps.value.length;
  if (type === 'user') return allApps.value.filter(a => !a.isSystem).length;
  if (type === 'system') return allApps.value.filter(a => a.isSystem).length;
  return 0;
}

function getCountByState(state) {
  if (state === 'all') return allApps.value.length;
  if (state === 'active') return allApps.value.filter(a => !a.frozen).length;
  if (state === 'frozen') return allApps.value.filter(a => a.frozen).length;
  return 0;
}

const filteredApps = computed(() => {
  const q = searchQuery.value.trim().toLowerCase();
  return allApps.value.filter(app => {
    // 搜索过滤
    if (q) {
      const matchName = (app.name || '').toLowerCase().includes(q);
      const matchPkg = (app.pkg || '').toLowerCase().includes(q);
      if (!matchName && !matchPkg) return false;
    }
    // 类型过滤
    if (currentType.value === 'user' && app.isSystem) return false;
    if (currentType.value === 'system' && !app.isSystem) return false;
    // 状态过滤
    if (currentState.value === 'active' && app.frozen) return false;
    if (currentState.value === 'frozen' && !app.frozen) return false;
    return true;
  });
});

function handleLaunch(app) {
  try {
    bridge.call('launchApp', app.pkg);
    showToast(`正在启动 ${app.name}...`);
  } catch (e) {}
}

function handleToggleFreeze(app) {
  const nextFreeze = !app.frozen;
  openModal('confirm', {
    title: nextFreeze ? `冻结应用：${app.name}` : `解冻应用：${app.name}`,
    desc: nextFreeze
      ? `冻结后应用将彻底停止运行、不再自启偷跑并从系统桌面隐藏，随时可在此解冻恢复。`
      : `解冻后应用将恢复正常可用状态并重新出现在桌面。`,
    isDanger: nextFreeze,
    onConfirm: () => {
      bridge.call('togglePackageFreeze', app.pkg, nextFreeze);
      app.frozen = nextFreeze;
      showToast(nextFreeze ? `已冻结: ${app.name}` : `已解冻: ${app.name}`);
      setTimeout(() => loadApps(false), 600);
    }
  });
}

function handleClearData(app) {
  openModal('confirm', {
    title: `清除数据：${app.name}`,
    desc: `即将清空【${app.name}】(${app.pkg}) 的全部本地缓存与用户数据，相当于应用刚安装状态。此操作不可逆，是否继续？`,
    isDanger: true,
    onConfirm: () => {
      bridge.call('clearAppData', app.pkg);
      showToast(`已下发清除 ${app.name} 数据指令`);
      setTimeout(() => loadApps(false), 800);
    }
  });
}

function handleUninstall(app) {
  openModal('confirm', {
    title: `卸载应用：${app.name}`,
    desc: `确认彻底从车机卸载第三方应用【${app.name}】(${app.pkg}) 吗？卸载后所有本地配置将被清空。`,
    isDanger: true,
    onConfirm: () => {
      bridge.call('uninstallApp', app.pkg);
      showToast(`正在卸载 ${app.name}...`);
      setTimeout(() => {
        loadApps(false);
      }, 1500);
    }
  });
}
</script>
