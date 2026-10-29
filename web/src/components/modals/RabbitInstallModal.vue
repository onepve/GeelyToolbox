<template>
  <ModalWrapper 
    :show="!!store.modals.rabbitInstall" 
    title="卡兔子主题安装向导" 
    badge="专家模式"
    maxWidthClass="max-w-[880px]"
    @close="handleClose"
  >
    <div class="flex flex-col space-y-4">
      <!-- 核心原理极简卡片 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col space-y-1.5 shadow-sm">
        <div class="text-[17.5px] font-black text-car-text flex items-center justify-between">
          <span>兔子时钟屏保伪装原理</span>
          <span v-if="selectedApk && !wizardActive" class="text-[12.5px] px-2.5 py-0.5 rounded-full bg-car-card text-car-accent border border-car-border font-bold">
            已选定目标应用
          </span>
        </div>
        <p class="text-[14px] text-car-sub leading-relaxed font-bold">
          利用原厂兔子时钟屏保白名单，将选定的 APK 伪装打包注入主题中；应用主题后冷重启车机，即可突破签名限制完成无损覆盖安装。
        </p>
      </div>

      <!-- ================= 阶段二：向导保姆看板 (注入开始后) ================= -->
      <div v-if="wizardActive" class="bg-car-item border-2 border-car-accent rounded-2xl p-5 flex flex-col space-y-4 shadow-lg ring-2 ring-car-accent/20">
        <!-- 顶部状态与倒计时条 -->
        <div class="flex items-center justify-between pb-3 border-b border-car-border/60">
          <div class="flex items-center space-x-3">
            <span v-if="wizardCountdown > 0" class="w-3.5 h-3.5 rounded-full bg-car-accent animate-ping"></span>
            <span v-else class="w-3.5 h-3.5 rounded-full bg-emerald-400"></span>
            <div class="flex flex-col">
              <span class="text-[18px] font-black text-car-text">
                {{ wizardCountdown > 0 ? `向导进行中：正在为【${selectedApk?.name}】注入主题` : `底层已注入就绪：【${selectedApk?.name}】` }}
              </span>
              <span class="text-[13px] font-bold text-car-sub">
                目标文件：{{ selectedApk?.filename }}
              </span>
            </div>
          </div>
          <div class="text-right">
            <span v-if="wizardCountdown > 0" class="text-[15.5px] font-black text-car-accent font-mono">
              {{ wizardCountdown }} 秒 底层替换倒计时
            </span>
            <span v-else class="text-[15.5px] font-black text-emerald-400">
              ✓ 原子替换已完成
            </span>
          </div>
        </div>

        <!-- 四步流程指南卡片 -->
        <div class="space-y-3">
          <!-- 步骤 1 -->
          <div class="p-3.5 rounded-xl bg-car-card border border-car-border flex items-center justify-between">
            <div class="flex items-center space-x-3">
              <span class="w-7 h-7 rounded-lg bg-car-item border border-car-border text-car-accent font-black flex items-center justify-center text-[15px]">1</span>
              <div>
                <div class="text-[15.5px] font-black text-car-text">打开车机主题中心</div>
                <div class="text-[13px] text-car-sub font-bold">系统已自动尝试唤起；若未弹出可手动点击右侧按键打开</div>
              </div>
            </div>
            <button 
              @click="openThemeCenter"
              class="min-h-[50px] px-4 rounded-xl bg-car-item border border-car-border text-car-accent hover:border-car-accent font-black text-[14px] cursor-pointer transition-all flex items-center justify-center whitespace-nowrap"
            >
              打开主题中心 ➔
            </button>
          </div>

          <!-- 步骤 2 -->
          <div class="p-3.5 rounded-xl bg-car-card border border-car-border flex items-center space-x-3">
            <span class="w-7 h-7 rounded-lg bg-car-item border border-car-border text-car-accent font-black flex items-center justify-center text-[15px]">2</span>
            <div>
              <div class="text-[15.5px] font-black text-car-text">在主题中心进入【屏保时钟 ➔ 兔子时钟】</div>
              <div class="text-[13px] text-car-sub font-bold">进入兔子时钟详情页稍作等待（{{ wizardCountdown > 0 ? `等待倒计时归零` : `底层已自动写入` }}，防止原厂自愈）</div>
            </div>
          </div>

          <!-- 步骤 3 -->
          <div class="p-3.5 rounded-xl border flex items-center justify-between"
               :class="wizardCountdown === 0 ? 'bg-[var(--accent-gold-bg)] border-2 border-emerald-400' : 'bg-car-card border-car-border opacity-70'">
            <div class="flex items-center space-x-3">
              <span class="w-7 h-7 rounded-lg bg-car-item border border-car-border text-emerald-400 font-black flex items-center justify-center text-[15px]">3</span>
              <div>
                <div class="text-[15.5px] font-black text-car-text">在主题中心点击【应用】该兔子时钟</div>
                <div class="text-[13px] font-bold" :class="wizardCountdown === 0 ? 'text-emerald-400' : 'text-car-sub'">
                  {{ wizardCountdown === 0 ? '【立即操作】底层替换已就绪！请去主题中心点击右下角【应用】！' : '等待倒计时结束后再去点击应用' }}
                </div>
              </div>
            </div>
            <span v-if="wizardCountdown === 0" class="text-[13px] font-black px-3 py-1 rounded-lg bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
              请去点击应用
            </span>
          </div>

          <!-- 步骤 4 -->
          <div class="p-3.5 rounded-xl bg-car-card border border-car-border flex flex-col space-y-2">
            <div class="flex items-center justify-between">
              <div class="flex items-center space-x-3">
                <span class="w-7 h-7 rounded-lg bg-car-item border border-car-border text-car-accent font-black flex items-center justify-center text-[15px]">4</span>
                <div>
                  <div class="text-[15.5px] font-black text-car-text">应用主题后：冷重启车机 ➔ 打开文件管理器安装覆盖</div>
                  <div class="text-[13px] text-car-sub font-bold">在主题中心点击【应用】后，返回此处执行重启；重启开机后再点击打开文件管理器完成安装</div>
                </div>
              </div>
            </div>
            <!-- 快捷动作条 -->
            <div class="flex space-x-3 pt-1">
              <button 
                @click="rebootVehicle"
                class="flex-1 min-h-[50px] px-4 rounded-xl bg-car-item border border-car-accent text-car-accent font-black text-[15px] cursor-pointer hover:bg-car-card transition-all flex items-center justify-center"
              >
                ① 一键冷重启车机
              </button>
              <button 
                @click="openFileManager"
                class="flex-1 min-h-[50px] px-4 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[15px] cursor-pointer hover:border-car-border-light transition-all flex items-center justify-center"
              >
                ② 打开原生文件管理器点装
              </button>
            </div>
          </div>
        </div>

        <!-- 底部控制按键 -->
        <div class="flex items-center justify-between pt-1">
          <button 
            @click="backToSelector"
            class="min-h-[50px] px-5 rounded-xl bg-car-item border border-car-border text-car-sub hover:text-car-text font-bold text-[14.5px] cursor-pointer hover:border-car-border-light transition-all"
          >
            ➔ 返回重新挑选安装包
          </button>
          <button 
            @click="handleClose"
            class="min-h-[50px] px-6 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[15px] cursor-pointer hover:border-car-border-light transition-all"
          >
            完成并收起向导
          </button>
        </div>
      </div>

      <!-- ================= 阶段一：扫描与挑选安装包 ================= -->
      <template v-else>
        <!-- 正在扫描提示 -->
        <div v-if="isScanning" class="bg-car-item border border-car-border rounded-2xl p-8 flex flex-col items-center justify-center space-y-3">
          <div class="w-8 h-8 border-3 border-car-accent border-t-transparent rounded-full animate-spin"></div>
          <span class="text-[16px] font-bold text-car-text">全盘扫描车机机身、Download 及 U 盘安装包...</span>
        </div>

        <!-- 扫描为空：指引卡片 -->
        <div v-else-if="apkList.length === 0" class="bg-car-item border border-car-border rounded-2xl p-6 flex flex-col space-y-4 shadow-sm">
          <div class="flex items-center space-x-3 text-amber-400">
            <svg class="w-7 h-7 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            <span class="text-[17.5px] font-black text-car-text">未在常用目录检测到 APK 安装包</span>
          </div>
          <p class="text-[14.5px] font-bold text-car-sub leading-relaxed">
            请先在【软件商城】下载高德地图等安装包，或通过 U 盘将 APK 拷贝至车机。您也可以直接点击下方【调起原生文件管理】手动浏览任意路径选择安装包。
          </p>
          <div class="flex space-x-3 pt-1">
            <button 
              @click="goToStore"
              class="flex-1 min-h-[50px] px-4 rounded-xl bg-car-card border border-car-accent text-car-accent font-black text-[15px] cursor-pointer hover:bg-car-item transition-all flex items-center justify-center"
            >
              前往软件商城下载
            </button>
            <button 
              @click="selectApkManual"
              class="flex-1 min-h-[50px] px-4 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[15px] cursor-pointer hover:border-car-border-light transition-all flex items-center justify-center"
            >
              调起文件选择器挑选
            </button>
            <button 
              @click="rescanApks"
              class="min-h-[50px] px-5 rounded-xl bg-car-item border border-car-border text-car-sub font-bold text-[14.5px] cursor-pointer hover:text-car-text transition-all flex items-center justify-center"
            >
              重新扫描
            </button>
          </div>
        </div>

        <!-- 扫描成功：列表单选 -->
        <div v-else class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col space-y-3 shadow-sm">
          <div class="flex items-center justify-between px-1">
            <span class="text-[15.5px] font-black text-car-text">
              请选择要伪装注入的目标安装包（共检测到 {{ apkList.length }} 个）：
            </span>
            <div class="flex items-center space-x-3">
              <button 
                @click="selectApkManual" 
                class="text-[13px] text-car-sub font-bold hover:text-car-text cursor-pointer"
              >
                手动浏览选择 ➔
              </button>
              <button 
                @click="rescanApks" 
                class="text-[13px] text-car-accent font-bold hover:underline cursor-pointer"
              >
                刷新列表
              </button>
            </div>
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
                    <span v-if="item.versionName" class="text-[12px] px-2 py-0.5 rounded bg-car-item border border-car-border text-car-sub font-mono">
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

        <!-- 流程步骤极简指引 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-3.5 flex flex-col space-y-1.5 shadow-sm">
          <div class="text-[14.5px] font-black text-car-text">保姆式标准流程：</div>
          <div class="flex items-center space-x-2 text-[13.5px] font-bold text-car-sub overflow-x-auto py-0.5">
            <span class="px-2.5 py-1 rounded-lg bg-car-card border border-car-border text-car-text">1. 选包启动</span>
            <span>➔</span>
            <span class="px-2.5 py-1 rounded-lg bg-car-card border border-car-border text-car-text">2. 兔子时钟等待20秒</span>
            <span>➔</span>
            <span class="px-2.5 py-1 rounded-lg bg-car-card border border-car-border text-car-accent">3. 主题点应用</span>
            <span>➔</span>
            <span class="px-2.5 py-1 rounded-lg bg-car-card border border-car-border text-car-text">4. 冷重启车机</span>
            <span>➔</span>
            <span class="px-2.5 py-1 rounded-lg bg-car-card border border-car-border text-car-text">5. 原生文件管理点装</span>
          </div>
        </div>

        <!-- 唯一核心执行大按键 (76px 黄金大磁贴) -->
        <div class="pt-1">
          <button 
            @click="startAutoPilot"
            :disabled="!selectedApk"
            class="w-full min-h-[76px] p-4 rounded-2xl flex flex-col items-center justify-center text-center transition-all"
            :class="selectedApk 
              ? 'bg-car-item border-2 border-car-accent cursor-pointer hover:border-car-accent shadow-lg ring-2 ring-car-accent/20' 
              : 'bg-car-item/50 border border-car-border opacity-40 cursor-not-allowed'"
          >
            <span class="text-[20px] font-black text-car-text">一键启动卡兔子向导</span>
            <span class="text-[14px] font-bold text-car-sub mt-1">
              {{ selectedApk ? `将【${selectedApk.name}】伪装注入屏保并自动唤起主题中心` : '请先在上方列表中选定一个 APK 安装包' }}
            </span>
          </button>
        </div>
      </template>
    </div>

    <template #footer>
      <div class="flex items-center justify-between w-full">
        <button 
          @click="openFileManager"
          class="min-h-[50px] px-6 rounded-xl bg-car-item border border-car-border text-car-sub hover:text-car-text font-bold text-[15px] cursor-pointer hover:border-car-border-light transition-all flex items-center justify-center"
        >
          调起原生文件管理
        </button>
        <button 
          @click="handleClose"
          class="min-h-[50px] px-8 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-border-light transition-all flex items-center justify-center"
        >
          {{ wizardActive ? '完成并关闭' : '取消返回' }}
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

function backToSelector() {
  clearTimer();
  wizardActive.value = false;
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
  showToast('正在重新扫描全盘安装包...');
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

  // 3. 默认选中第一项（全盘按修改时间倒序排列，第一项就是最新下载的包）
  selectedApk.value = apkList.value[0];
}

function selectApkManual() {
  showToast('正在调起系统文件选择器...');
  bridge.call('selectApkInject');
}

function startAutoPilot() {
  if (!selectedApk.value) {
    showToast('请先在列表中选择需要注入的安装包！', 'warn');
    return;
  }
  if (store.deviceInfo?.adb_master_switch === false) {
    showToast('ADB 总开关已关闭，该功能无法使用', 'warn');
    return;
  }

  const targetFilename = selectedApk.value.filename;
  // 切换为向导看板模式，开启 20 秒倒计时
  wizardActive.value = true;
  wizardCountdown.value = 20;
  clearTimer();
  countdownTimer = setInterval(() => {
    if (wizardCountdown.value > 1) {
      wizardCountdown.value--;
    } else {
      wizardCountdown.value = 0;
      clearTimer();
      showToast('【就绪】底层替换已完成！请在主题中心点击【应用】！', 'success');
    }
  }, 1000);

  showToast(`向导已启动：正在准备【${selectedApk.value.name}】并唤起主题中心...`);
  bridge.call('startAutoPilotInject', targetFilename);
}

function openThemeCenter() {
  showToast('正在打开车机主题中心...');
  bridge.call('openThemeManager');
}

function rebootVehicle() {
  openModal('confirm', {
    title: '【确认重启】执行车机完整硬件冷重启',
    desc: '请确保您已经在主题中心点击了【应用】兔子时钟！重启后伪装时钟即生效。是否确认立即重启？',
    tip: '【操作规范】请确保车辆停稳挂入 P 挡，重启过程约需 25~30 秒。',
    isDanger: true,
    confirmText: '确认立即冷重启',
    onConfirm: () => {
      showToast('正在执行车机冷重启...');
      bridge.call('hardReboot');
    }
  });
}

function openFileManager() {
  bridge.call('openFileManager');
  showToast('正在打开原生文件管理器...');
}

function goToStore() {
  handleClose();
  store.currentNav = 'store';
}
</script>
