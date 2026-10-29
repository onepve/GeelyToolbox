<template>
  <ModalWrapper 
    :show="!!store.modals.rabbitInstall" 
    title="卡兔子主题伪装安装向导" 
    badge="专家模式"
    maxWidthClass="max-w-[860px]"
    @close="handleClose"
  >
    <div class="flex flex-col space-y-4">
      <!-- 原理卡片 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col space-y-1.5 shadow-sm">
        <div class="text-[18px] font-black text-car-text flex items-center justify-between">
          <span>半自动系统签名伪装与覆盖原理</span>
          <span v-if="selectedApk" class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-card text-car-accent border border-car-border font-bold">
            已就绪
          </span>
        </div>
        <p class="text-[14.5px] text-car-sub leading-relaxed font-bold">
          吉利原厂高德地图具有系统签名保护。在【专家模式】下，系统将把选定的 APK 伪装注入车机『兔子时钟』屏保中，应用后一键重启车机即可无损覆盖安装。
        </p>
      </div>

      <!-- 向导进行中状态展示 -->
      <div v-if="wizardActive" class="bg-car-item border-2 border-car-accent rounded-2xl p-5 flex flex-col space-y-4 shadow-lg ring-2 ring-car-accent/20">
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-3">
            <span class="w-3.5 h-3.5 rounded-full bg-car-accent animate-ping"></span>
            <span class="text-[19px] font-black text-car-text">向导已启动：正在准备伪装主题并唤起系统主题中心</span>
          </div>
          <span class="text-[15px] font-black text-car-accent font-mono">{{ wizardCountdown }}秒 缓冲倒计时</span>
        </div>
        <div class="p-4 rounded-xl bg-car-card border border-car-border text-[15px] font-bold text-car-text space-y-2">
          <div class="flex items-start space-x-2">
            <span class="text-car-accent font-black">①</span>
            <span>已唤起原厂主题中心，请在 20 秒内点击进入【屏保时钟 ➔ 兔子时钟】详情页停留；</span>
          </div>
          <div class="flex items-start space-x-2">
            <span class="text-car-accent font-black">②</span>
            <span>倒计时结束时后台将自动完成覆盖，屏幕顶部会弹出醒目的『就绪提示』；</span>
          </div>
          <div class="flex items-start space-x-2">
            <span class="text-car-accent font-black">③</span>
            <span>看到顶部就绪提示后，立即点击主题中心下方的【应用】按钮，应用后返回助手一键软重启车机。</span>
          </div>
        </div>
        <div class="flex justify-end pt-1">
          <button 
            @click="handleClose"
            class="min-h-[50px] px-6 rounded-xl bg-car-accent text-white font-black text-[16px] cursor-pointer shadow-md hover:brightness-110 transition-all"
          >
            我知道了，收起向导浮窗
          </button>
        </div>
      </div>

      <!-- 常态：扫描与选择区域 -->
      <template v-else>
        <!-- 扫描加载中 -->
        <div v-if="isScanning" class="bg-car-item border border-car-border rounded-2xl p-8 flex flex-col items-center justify-center space-y-3">
          <div class="w-8 h-8 border-3 border-car-accent border-t-transparent rounded-full animate-spin"></div>
          <span class="text-[16px] font-bold text-car-text">正在扫描机身与 /sdcard/Download/ 目录中的安装包...</span>
        </div>

        <!-- 扫描完成：未找到 APK (空状态卡片) -->
        <div v-else-if="apkList.length === 0" class="bg-car-item border border-car-border rounded-2xl p-6 flex flex-col space-y-4 shadow-sm">
          <div class="flex items-center space-x-3 text-amber-400">
            <svg class="w-7 h-7 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            <span class="text-[18px] font-black text-car-text">未在 Download 目录检测到 APK 安装包</span>
          </div>
          <p class="text-[15px] font-bold text-car-sub leading-relaxed">
            伪装注入前必须先指定待安装的 APK。请先在【软件商城】下载高德地图车机版，或通过【无线快传/U盘】将安装包传输至车机 Download 目录。
          </p>
          <div class="flex space-x-3 pt-1">
            <button 
              @click="goToStore"
              class="flex-1 min-h-[50px] px-4 rounded-xl bg-car-card border border-car-accent text-car-accent font-black text-[15.5px] cursor-pointer hover:bg-car-item transition-all flex items-center justify-center"
            >
              前往软件商城下载
            </button>
            <button 
              @click="rescanApks"
              class="min-h-[50px] px-6 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[15.5px] cursor-pointer hover:border-car-border-light transition-all flex items-center justify-center"
            >
              重新扫描
            </button>
          </div>
        </div>

        <!-- 扫描完成：检测到 1 个或多个 APK (选择卡片列表) -->
        <div v-else class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col space-y-3 shadow-sm">
          <div class="flex items-center justify-between px-1">
            <span class="text-[16px] font-black text-car-text">
              请选择要伪装注入的目标应用包（共检测到 {{ apkList.length }} 个）：
            </span>
            <button 
              @click="rescanApks" 
              class="text-[13.5px] text-car-accent font-bold hover:underline cursor-pointer flex items-center space-x-1"
            >
              <span>刷新列表</span>
            </button>
          </div>

          <!-- APK 单选列表 (大磁贴) -->
          <div class="max-h-[220px] overflow-y-auto space-y-2 pr-1 custom-scrollbar">
            <div 
              v-for="item in apkList" 
              :key="item.filename"
              @click="selectApk(item)"
              class="min-h-[64px] p-3 rounded-xl cursor-pointer transition-all flex items-center justify-between"
              :class="selectedApk?.filename === item.filename 
                ? 'border-2 border-car-accent bg-[var(--accent-gold-bg)] shadow-sm' 
                : 'border border-car-border bg-car-card hover:border-car-border-light'"
            >
              <div class="flex items-center space-x-3 min-w-0 flex-1">
                <!-- 单选圈 -->
                <div 
                  class="w-5 h-5 rounded-full border-2 flex items-center justify-center flex-shrink-0 transition-colors"
                  :class="selectedApk?.filename === item.filename ? 'border-car-accent bg-car-card' : 'border-car-border bg-car-item'"
                >
                  <div v-if="selectedApk?.filename === item.filename" class="w-2.5 h-2.5 rounded-full bg-car-accent"></div>
                </div>

                <!-- 应用详情 -->
                <div class="flex flex-col min-w-0 flex-1">
                  <div class="flex items-center space-x-2">
                    <span class="text-[16px] font-black text-car-text truncate">{{ item.name }}</span>
                    <span v-if="item.versionName" class="text-[12.5px] px-2 py-0.5 rounded bg-car-item border border-car-border text-car-sub font-mono">
                      v{{ item.versionName }}
                    </span>
                    <span v-if="item.isNavigation" class="text-[12px] px-2 py-0.5 rounded bg-emerald-500/15 border border-emerald-500/30 text-emerald-400 font-bold">
                      地图推荐
                    </span>
                  </div>
                  <div class="text-[13px] font-bold text-car-sub truncate mt-0.5">
                    {{ item.filename }} · {{ item.sizeStr }} · {{ item.timeStr }}
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 选定结果展示条 -->
          <div class="p-2.5 rounded-xl bg-car-card border border-car-border flex items-center justify-between text-[14px]">
            <span class="font-bold text-car-sub">当前选定：</span>
            <span class="font-black text-car-accent truncate max-w-[500px]">
              {{ selectedApk ? `${selectedApk.name}（${selectedApk.filename}）` : '未选择任何安装包' }}
            </span>
          </div>
        </div>

        <!-- 流程步骤说明 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col space-y-2 shadow-sm">
          <div class="text-[15.5px] font-black text-car-text">半自动保姆式操作流程：</div>
          <div class="flex items-center space-x-2 text-[14px] font-bold text-car-sub overflow-x-auto py-0.5">
            <span class="px-3 py-1.5 rounded-lg bg-car-card border border-car-border text-car-text">1. 选定目标安装包</span>
            <span>➔</span>
            <span class="px-3 py-1.5 rounded-lg bg-car-card border border-car-border text-car-text">2. 启动向导并在兔子时钟停留20秒</span>
            <span>➔</span>
            <span class="px-3 py-1.5 rounded-lg bg-car-card border border-car-border text-car-accent">3. 提示就绪后点应用</span>
            <span>➔</span>
            <span class="px-3 py-1.5 rounded-lg bg-car-card border border-car-border text-car-text">4. 一键软重启车机</span>
          </div>
        </div>

        <!-- 操作选择大矩阵 (84px 黄金大磁贴) -->
        <div class="flex space-x-4 pt-1">
          <button 
            @click="startAutoPilot"
            :disabled="!selectedApk"
            class="flex-1 min-h-[84px] p-4 rounded-2xl flex flex-col items-center justify-center text-center transition-all"
            :class="selectedApk 
              ? 'bg-car-item border-2 border-car-accent cursor-pointer hover:border-car-accent shadow-lg ring-2 ring-car-accent/20' 
              : 'bg-car-item/50 border border-car-border opacity-40 cursor-not-allowed'"
          >
            <span class="text-[19px] font-black text-car-text">一键卡兔子向导 (半自动)</span>
            <span class="text-[13.5px] font-bold text-car-sub mt-1">自动打包选定包、唤起主题中心并在20秒后原子就绪</span>
          </button>

          <button 
            @click="reInjectDirectly"
            :disabled="!selectedApk"
            class="flex-1 min-h-[84px] p-4 rounded-2xl flex flex-col items-center justify-center text-center transition-all"
            :class="selectedApk 
              ? 'bg-car-item border border-car-border cursor-pointer hover:border-car-border-light shadow-sm' 
              : 'bg-car-item/50 border border-car-border opacity-40 cursor-not-allowed'"
          >
            <span class="text-[19px] font-black text-car-text">仅重新注入伪装主题</span>
            <span class="text-[13.5px] font-bold text-car-sub mt-1">不唤起向导，仅将选定包覆盖至兔子时钟屏保</span>
          </button>
        </div>
      </template>
    </div>

    <template #footer>
      <div class="flex items-center justify-between w-full">
        <button 
          @click="openFileManager"
          class="min-h-[50px] px-6 rounded-xl bg-car-item border border-car-border text-car-sub hover:text-car-text font-bold text-[15.5px] cursor-pointer hover:border-car-border-light transition-all flex items-center justify-center"
        >
          调起原生文件管理
        </button>
        <button 
          @click="handleClose"
          class="min-h-[50px] px-8 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[16.5px] cursor-pointer hover:border-car-border-light transition-all flex items-center justify-center"
        >
          取消返回
        </button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { ref, computed, watch, onUnmounted } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, showToast } from '../../store';

const isScanning = ref(false);
const apkList = ref([]);
const selectedApk = ref(null);
const wizardActive = ref(false);
const wizardCountdown = ref(20);
let countdownTimer = null;

// 监听弹窗打开时自动执行扫描
watch(() => store.modals.rabbitInstall, (val) => {
  if (val) {
    wizardActive.value = false;
    clearTimer();
    scanDownloadedApks();
  } else {
    clearTimer();
    wizardActive.value = false;
  }
}, { immediate: true });

function clearTimer() {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
}

onUnmounted(() => {
  clearTimer();
});

function handleClose() {
  clearTimer();
  wizardActive.value = false;
  closeModal('rabbitInstall');
}

function selectApk(item) {
  selectedApk.value = item;
}

function scanDownloadedApks() {
  isScanning.value = true;
  apkList.value = [];
  selectedApk.value = null;

  try {
    let raw = null;
    if (window.$native && typeof window.$native.scanApksForRabbit === 'function') {
      raw = window.$native.scanApksForRabbit();
    } else if (window.$native && typeof window.$native.scanDownloadedApks === 'function') {
      raw = window.$native.scanDownloadedApks();
    }

    if (raw && typeof raw === 'string') {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        apkList.value = parsed.map(it => ({
          name: it.name || it.filename || '未知应用',
          filename: it.filename || '',
          path: it.path || '',
          sizeStr: it.sizeStr || (it.sizeBytes ? `${(it.sizeBytes / (1024 * 1024)).toFixed(1)} MB` : '0 MB'),
          timeStr: it.timeStr || '',
          isNavigation: !!it.isNavigation,
          versionName: it.versionName || '',
          packageName: it.packageName || ''
        }));
      }
    }
  } catch (e) {
    console.error('扫描 APK 列表异常:', e);
  } finally {
    isScanning.value = false;
    determineDefaultSelection();
  }
}

function rescanApks() {
  showToast('正在重新扫描安装包...');
  scanDownloadedApks();
}

function determineDefaultSelection() {
  if (apkList.value.length === 0) {
    selectedApk.value = null;
    return;
  }

  // 1. 若从应用商城详情页指定了 app 对象打开，优先匹配
  const modalPayload = store.modals.rabbitInstall;
  if (modalPayload && typeof modalPayload === 'object') {
    const targetFilename = (modalPayload.filename || '').toLowerCase();
    const targetName = (modalPayload.name || '').toLowerCase();
    const matched = apkList.value.find(it => {
      const fn = (it.filename || '').toLowerCase();
      const n = (it.name || '').toLowerCase();
      return (targetFilename && fn === targetFilename) || (targetName && n.includes(targetName));
    });
    if (matched) {
      selectedApk.value = matched;
      return;
    }
  }

  // 2. 优先匹配地图应用
  const mapApk = apkList.value.find(it => it.isNavigation);
  if (mapApk) {
    selectedApk.value = mapApk;
    return;
  }

  // 3. 默认选中第一项
  selectedApk.value = apkList.value[0];
}

function checkSafety(apk) {
  if (!apk) {
    showToast('请先在列表中选择需要伪装注入的安装包！', 'warn');
    return false;
  }
  if (store.deviceInfo?.adb_master_switch === false) {
    showToast('ADB 总开关已关闭，该功能无法使用', 'warn');
    return false;
  }
  // 检查是否非地图应用且未开启专家模式
  if (!apk.isNavigation && !store.settings.expert_rabbit) {
    showToast('【安全拦截】默认仅允许高德、百度等地图软件伪装。常规应用请在设置中开启专家模式！', 'warn');
    return false;
  }
  return true;
}

function startAutoPilot() {
  if (!checkSafety(selectedApk.value)) return;

  const targetFilename = selectedApk.value.filename;
  // 按照规范：不立即关闭弹窗，展示倒计时与引导
  wizardActive.value = true;
  wizardCountdown.value = 20;
  clearTimer();
  countdownTimer = setInterval(() => {
    if (wizardCountdown.value > 1) {
      wizardCountdown.value--;
    } else {
      clearTimer();
    }
  }, 1000);

  showToast(`向导已启动：正在准备【${selectedApk.value.name}】并唤起主题中心...`);
  bridge.call('startAutoPilotInject', targetFilename);
}

function reInjectDirectly() {
  if (!checkSafety(selectedApk.value)) return;

  const targetFilename = selectedApk.value.filename;
  showToast(`正在注入【${selectedApk.value.name}】伪装主题...`);
  bridge.call('injectApkDirectly', targetFilename);
}

function openFileManager() {
  handleClose();
  bridge.call('openFileManager');
}

function goToStore() {
  handleClose();
  store.currentNav = 'store';
}
</script>
