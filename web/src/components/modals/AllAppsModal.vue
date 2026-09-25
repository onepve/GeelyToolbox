<template>
  <ModalWrapper 
    :show="store.modals.allApps" 
    title="车机全量应用高级管理" 
    badge="特权维护"
    maxWidthClass="max-w-[1100px]"
    @close="closeModal('allApps')"
  >
    <div class="flex flex-col space-y-4">
      <!-- 顶部主模式切换 Tab 胶囊 (车规大触控 · 分割清晰无干扰) -->
      <div class="flex items-center justify-between border-b border-car-border pb-3">
        <div class="flex items-center space-x-3">
          <button 
            @click="activeTab = 'all'"
            :class="[
              'min-h-[52px] px-5 rounded-2xl font-black text-[16px] cursor-pointer transition-all border-2 flex items-center space-x-2',
              activeTab === 'all'
                ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/20 shadow-md'
                : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            <span>全部已安装应用</span>
            <span class="px-2 py-0.5 text-[12px] font-bold rounded-full bg-car-card border border-car-border text-car-sub">
              {{ allApps.length }}
            </span>
          </button>

          <button 
            @click="activeTab = 'recommended'"
            :class="[
              'min-h-[52px] px-5 rounded-2xl font-black text-[16px] cursor-pointer transition-all border-2 flex items-center space-x-2',
              activeTab === 'recommended'
                ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/20 shadow-md'
                : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            <span>原厂推荐冻结</span>
            <span class="px-2 py-0.5 text-[12px] font-bold rounded-full bg-car-card border border-car-border text-car-sub">
              4 款专属
            </span>
          </button>
        </div>

        <div v-if="activeTab === 'all'" class="text-[13px] text-car-sub font-bold">
          默认优先展示用户自装软件 · 支持按名称/包名全局检索
        </div>
        <div v-else class="text-[13px] text-car-sub font-bold">
          低频调优操作 · 彻底切断开机自启偷跑 · 支持一键安全解冻
        </div>
      </div>

      <!-- ==================== TAB 1: 全部已安装应用 ==================== -->
      <div v-if="activeTab === 'all'" class="flex flex-col space-y-4">
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

        <!-- 类别与状态双重过滤标签 -->
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

        <!-- 应用列表视口 (固定高度独立滚动) -->
        <div class="h-[400px] overflow-y-auto rounded-2xl border border-car-border bg-car-item p-3 space-y-2">
          <div v-if="filteredApps.length === 0" class="h-full flex flex-col items-center justify-center text-car-sub text-[16px] font-bold space-y-2">
            <span>{{ isLoading ? '正在读取整车已安装应用清单...' : '未找到符合条件的应用' }}</span>
          </div>

          <div 
            v-for="app in filteredApps" 
            :key="app.pkg"
            class="bg-car-item/90 hover:bg-car-item border border-car-border rounded-xl p-3.5 flex items-center justify-between shadow-sm transition-all"
          >
            <!-- 左侧信息 -->
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

            <!-- 右侧操作栏 -->
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

      <!-- ==================== TAB 2: 原厂推荐冻结 ==================== -->
      <div v-else class="flex flex-col space-y-4">
        <div class="bg-car-item border border-car-border rounded-2xl p-4 flex items-center justify-between text-car-sub">
          <div class="text-[14px] font-bold text-car-text">
            ⚡ 原厂预装组件冻结专区：针对吉利车机开机后台偷跑、抢占蓝牙与多媒体焦点的 4 款核心组件。
          </div>
          <button 
            @click="refreshPresetStates"
            class="min-h-[50px] px-4 rounded-xl border border-car-border bg-car-card text-car-text hover:border-car-border-light text-[13.5px] font-black cursor-pointer shadow-sm"
          >
            刷新状态
          </button>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <!-- 1. 吉利应用商店 -->
          <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between space-y-3 shadow-sm">
            <div class="flex flex-col">
              <div class="flex items-center justify-between mb-1">
                <span class="text-[18px] font-black text-car-text">吉利应用商店</span>
                <span :class="['text-[12px] px-2 py-0.5 rounded font-extrabold border', presetStates['com.ecarx.appstore'] ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-400' : 'bg-car-card border-car-border text-car-sub']">
                  {{ presetStates['com.ecarx.appstore'] ? '已安全冻结' : '活跃运行中' }}
                </span>
              </div>
              <span class="text-[12px] text-car-sub font-mono truncate mb-2">com.ecarx.appstore</span>
              <span class="text-[13px] text-car-sub font-bold leading-relaxed">
                冻结后车机白名单将刚性常驻开启，不再被原厂商店自动关闭，防止已装软件闪退。
              </span>
            </div>
            <button 
              @click="confirmPresetFreeze('com.ecarx.appstore', '吉利应用商店')"
              :disabled="!!freezingPackages['com.ecarx.appstore']"
              :class="[
                'min-h-[58px] px-4 rounded-xl border-2 font-black text-[16px] transition-all',
                freezingPackages['com.ecarx.appstore']
                  ? 'bg-car-card border-car-border text-car-sub opacity-50 cursor-not-allowed'
                  : (presetStates['com.ecarx.appstore'] 
                      ? 'bg-car-card border-emerald-500/60 text-emerald-400 hover:border-emerald-400 cursor-pointer' 
                      : 'bg-car-card border-amber-500/70 text-car-text hover:border-amber-400 cursor-pointer')
              ]"
            >
              {{ freezingPackages['com.ecarx.appstore'] 
                  ? (presetStates['com.ecarx.appstore'] ? '正在解冻恢复中...' : '正在安全冻结中...') 
                  : (presetStates['com.ecarx.appstore'] ? '已安全冻结 · 点击解冻恢复' : '运行中 · 点击安全冻结') }}
            </button>
          </div>

          <!-- 2. 原厂多媒体伴听 -->
          <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between space-y-3 shadow-sm">
            <div class="flex flex-col">
              <div class="flex items-center justify-between mb-1">
                <span class="text-[18px] font-black text-car-text">原厂多媒体伴听</span>
                <span :class="['text-[12px] px-2 py-0.5 rounded font-extrabold border', presetStates['com.ecarx.multimedia'] ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-400' : 'bg-car-card border-car-border text-car-sub']">
                  {{ presetStates['com.ecarx.multimedia'] ? '已安全冻结' : '活跃运行中' }}
                </span>
              </div>
              <span class="text-[12px] text-car-sub font-mono truncate mb-2">com.ecarx.multimedia</span>
              <span class="text-[13px] text-car-sub font-bold leading-relaxed">
                冻结后彻底屏蔽原厂广播与广告电台抢占，方向盘切歌键纯净交给第三方音乐播放器。
              </span>
            </div>
            <button 
              @click="confirmPresetFreeze('com.ecarx.multimedia', '原厂多媒体伴听')"
              :disabled="!!freezingPackages['com.ecarx.multimedia']"
              :class="[
                'min-h-[58px] px-4 rounded-xl border-2 font-black text-[16px] transition-all',
                freezingPackages['com.ecarx.multimedia']
                  ? 'bg-car-card border-car-border text-car-sub opacity-50 cursor-not-allowed'
                  : (presetStates['com.ecarx.multimedia'] 
                      ? 'bg-car-card border-emerald-500/60 text-emerald-400 hover:border-emerald-400 cursor-pointer' 
                      : 'bg-car-card border-amber-500/70 text-car-text hover:border-amber-400 cursor-pointer')
              ]"
            >
              {{ freezingPackages['com.ecarx.multimedia'] 
                  ? (presetStates['com.ecarx.multimedia'] ? '正在解冻恢复中...' : '正在安全冻结中...') 
                  : (presetStates['com.ecarx.multimedia'] ? '已安全冻结 · 点击解冻恢复' : '运行中 · 点击安全冻结') }}
            </button>
          </div>

          <!-- 3. 原厂云听车机版 -->
          <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between space-y-3 shadow-sm">
            <div class="flex flex-col">
              <div class="flex items-center justify-between mb-1">
                <span class="text-[18px] font-black text-car-text">原厂云听车机版</span>
                <span :class="['text-[12px] px-2 py-0.5 rounded font-extrabold border', presetStates['com.edog.car'] ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-400' : 'bg-car-card border-car-border text-car-sub']">
                  {{ presetStates['com.edog.car'] ? '已安全冻结' : '活跃运行中' }}
                </span>
              </div>
              <span class="text-[12px] text-car-sub font-mono truncate mb-2">com.edog.car</span>
              <span class="text-[13px] text-car-sub font-bold leading-relaxed">
                原厂捆绑网络音频，日常使用率极低。冻结后可为车机释放约 36MB 宝贵运行内存。
              </span>
            </div>
            <button 
              @click="confirmPresetFreeze('com.edog.car', '原厂云听车机版')"
              :disabled="!!freezingPackages['com.edog.car']"
              :class="[
                'min-h-[58px] px-4 rounded-xl border-2 font-black text-[16px] transition-all',
                freezingPackages['com.edog.car']
                  ? 'bg-car-card border-car-border text-car-sub opacity-50 cursor-not-allowed'
                  : (presetStates['com.edog.car'] 
                      ? 'bg-car-card border-emerald-500/60 text-emerald-400 hover:border-emerald-400 cursor-pointer' 
                      : 'bg-car-card border-amber-500/70 text-car-text hover:border-amber-400 cursor-pointer')
              ]"
            >
              {{ freezingPackages['com.edog.car'] 
                  ? (presetStates['com.edog.car'] ? '正在解冻恢复中...' : '正在安全冻结中...') 
                  : (presetStates['com.edog.car'] ? '已安全冻结 · 点击解冻恢复' : '运行中 · 点击安全冻结') }}
            </button>
          </div>

          <!-- 4. 火山车娱 -->
          <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between space-y-3 shadow-sm">
            <div class="flex flex-col">
              <div class="flex items-center justify-between mb-1">
                <span class="text-[18px] font-black text-car-text">火山车娱</span>
                <span :class="['text-[12px] px-2 py-0.5 rounded font-extrabold border', presetStates['com.bytedance.byteautoservice'] ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-400' : 'bg-car-card border-car-border text-car-sub']">
                  {{ presetStates['com.bytedance.byteautoservice'] ? '已安全冻结' : '活跃运行中' }}
                </span>
              </div>
              <span class="text-[12px] text-car-sub font-mono truncate mb-2">com.bytedance.byteautoservice</span>
              <span class="text-[13px] text-car-sub font-bold leading-relaxed">
                原厂短视频后台服务。冻结后彻底防止开机自动加载视频流，节约整车热点与流量。
              </span>
            </div>
            <button 
              @click="confirmPresetFreeze('com.bytedance.byteautoservice', '火山车娱')"
              :disabled="!!freezingPackages['com.bytedance.byteautoservice']"
              :class="[
                'min-h-[58px] px-4 rounded-xl border-2 font-black text-[16px] transition-all',
                freezingPackages['com.bytedance.byteautoservice']
                  ? 'bg-car-card border-car-border text-car-sub opacity-50 cursor-not-allowed'
                  : (presetStates['com.bytedance.byteautoservice'] 
                      ? 'bg-car-card border-emerald-500/60 text-emerald-400 hover:border-emerald-400 cursor-pointer' 
                      : 'bg-car-card border-amber-500/70 text-car-text hover:border-amber-400 cursor-pointer')
              ]"
            >
              {{ freezingPackages['com.bytedance.byteautoservice'] 
                  ? (presetStates['com.bytedance.byteautoservice'] ? '正在解冻恢复中...' : '正在安全冻结中...') 
                  : (presetStates['com.bytedance.byteautoservice'] ? '已安全冻结 · 点击解冻恢复' : '运行中 · 点击安全冻结') }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部操作栏 -->
    <template #footer>
      <div class="flex items-center justify-between w-full">
        <div class="text-[13px] text-car-sub font-bold">
          <span v-if="activeTab === 'all'">共收录 {{ allApps.length }} 个已安装组件（已自动对系统核心库施加物理安全保护）</span>
          <span v-else>原厂 4 款核心组件已全量受 ADB 权限保护，支持即时冻结与无缝解冻恢复</span>
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
import { openAppstoreFlow } from '../../utils/appstoreFreeze';

const activeTab = ref('all'); // 'all' | 'recommended'
const allApps = ref([]);
const isLoading = ref(false);
const searchQuery = ref('');
const currentType = ref('user');   // 默认点亮'第三方用户软件'
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

// 原厂 4 款预装推荐冻结状态
const presetStates = ref({
  'com.ecarx.appstore': false,
  'com.ecarx.multimedia': false,
  'com.edog.car': false,
  'com.bytedance.byteautoservice': false
});
const freezingPackages = ref({}); // 防呆置灰防狂点

function refreshPresetStates() {
  try {
    presetStates.value['com.ecarx.appstore'] = !!bridge.call('isPackageFrozen', 'com.ecarx.appstore');
    presetStates.value['com.ecarx.multimedia'] = !!bridge.call('isPackageFrozen', 'com.ecarx.multimedia');
    presetStates.value['com.edog.car'] = !!bridge.call('isPackageFrozen', 'com.edog.car');
    presetStates.value['com.bytedance.byteautoservice'] = !!bridge.call('isPackageFrozen', 'com.bytedance.byteautoservice');
  } catch (e) {}
}

window.refreshPackageStates = () => {
  refreshPresetStates();
  if (store.modals.allApps) {
    loadApps(false);
  }
};

window.onPackageFreezeFinished = (pkg, success, errorMsg) => {
  if (pkg && freezingPackages.value[pkg]) {
    freezingPackages.value[pkg] = false;
  }
  refreshPresetStates();
  if (store.modals.allApps) {
    loadApps(false);
  }
};

function confirmPresetFreeze(pkg, pkgName) {
  if (freezingPackages.value[pkg]) {
    showToast('该应用正在执行操作中，请勿重复点击');
    return;
  }

  const isFrozen = !!presetStates.value[pkg];
  const actionText = isFrozen ? '解冻恢复' : '安全冻结';
  
  if (pkg === 'com.ecarx.appstore') {
    openAppstoreFlow(() => {
      refreshPresetStates();
      loadApps(false);
    });
    return;
  }

  openModal('confirm', {
    title: `【高危操作确认】${actionText} ${pkgName}`,
    desc: isFrozen
      ? `即将解冻恢复【${pkgName}】(${pkg})，恢复后将重新允许自启与运行。`
      : `即将安全冻结【${pkgName}】(${pkg})，冻结后彻底停止运行并禁止开机自启。`,
    isDanger: !isFrozen,
    confirmText: `确认${actionText}`,
    cancelText: '取消',
    onConfirm: () => {
      executePresetToggle(pkg);
    }
  });
}

function executePresetToggle(pkg) {
  freezingPackages.value[pkg] = true;
  showToast('正在下发底层 ADB 冻结指令，请稍候...');

  try {
    bridge.call('togglePackageFreeze', pkg);
  } catch (e) {
    freezingPackages.value[pkg] = false;
    showToast('指令下发失败: ' + e);
    return;
  }

  // 6 秒超时保护
  setTimeout(() => {
    if (freezingPackages.value[pkg]) {
      freezingPackages.value[pkg] = false;
      refreshPresetStates();
      loadApps(false);
    }
  }, 6000);
}

function loadApps(showToastMsg = false) {
  isLoading.value = true;
  try {
    const raw = bridge.call('getAllInstalledApps');
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : (raw || []);
    allApps.value = Array.isArray(parsed) ? parsed : [];
    if (showToastMsg) {
      showToast(`已成功刷新整车已安装软件 (${allApps.value.length} 个)`, 'success');
    }
  } catch (e) {
    console.error('loadApps error:', e);
  } finally {
    isLoading.value = false;
  }
}

watch(() => store.modals.allApps, (show) => {
  if (show) {
    activeTab.value = 'all'; // 每次打开默认在全部已安装应用
    loadApps(false);
    refreshPresetStates();
  }
});

onMounted(() => {
  window.onPackageChanged = () => {
    if (store.modals.allApps) {
      loadApps(false);
      refreshPresetStates();
    }
  };
  if (store.modals.allApps) {
    loadApps(false);
    refreshPresetStates();
  }
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
    if (q) {
      const matchName = (app.name || '').toLowerCase().includes(q);
      const matchPkg = (app.pkg || '').toLowerCase().includes(q);
      if (!matchName && !matchPkg) return false;
    }
    if (currentType.value === 'user' && app.isSystem) return false;
    if (currentType.value === 'system' && !app.isSystem) return false;
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
