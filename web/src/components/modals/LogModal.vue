<template>
  <ModalWrapper 
    :show="store.modals.log" 
    title="运行与守护日志" 
    :badge="autoScroll ? '● 最新置顶轮转 (1.5s)' : '○ 自动置顶已暂停'"
    maxWidthClass="max-w-[1080px]"
    :showCloseButton="false"
    @close="closeModal('log')"
  >
    <div class="flex flex-col space-y-3">
      <!-- 模块日志开关：用户可独立控制高频模块写入，默认全关以避免日志风暴卡死界面 -->
      <div class="bg-car-item border border-car-border rounded-xl p-3 shadow-sm">
        <div class="text-[13.5px] text-car-sub font-bold mb-2 flex items-center justify-between">
          <span>8大核心业务日志采集开关（默认全关，按需开启）</span>
          <div class="flex items-center space-x-2">
            <button
              @click="setAllModules(false)"
              class="text-[12px] px-2.5 py-1 rounded-md bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold cursor-pointer transition-all"
            >
              一键全关
            </button>
            <button
              @click="setAllModules(true)"
              class="text-[12px] px-2.5 py-1 rounded-md bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold cursor-pointer transition-all"
            >
              一键全开
            </button>
          </div>
        </div>
        <div class="flex flex-wrap -mb-2">
          <button
            v-for="item in moduleSwitches"
            :key="item.key"
            @click="toggleModule(item.key)"
            :class="[
              'mr-2 mb-2 px-3 py-1.5 rounded-lg border-2 text-[13px] font-black cursor-pointer transition-all',
              item.enabled
                ? 'bg-emerald-500/15 border-emerald-500/60 text-emerald-400'
                : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
            ]"
          >
            {{ item.enabled ? '●' : '○' }} {{ item.label }}
          </button>
        </div>
      </div>

      <!-- 核心日志控制台输出屏 (独立平滑滚动，全量接入当前主题系统，消除生硬底色) -->
      <pre 
        ref="logContainer"
        class="h-[480px] overflow-y-auto rounded-2xl p-5 font-mono text-[14.5px] font-semibold leading-[1.75] select-text whitespace-pre-wrap shadow-inner transition-colors duration-200"
        :class="[
          store.isNight 
            ? 'bg-car-card border-2 border-car-border text-car-text shadow-black/80' 
            : 'bg-car-item border-2 border-car-border text-car-text shadow-sm'
        ]"
      >{{ logContent || '暂无日志记录...' }}</pre>

      <!-- 全量脱敏日志导出实时进度胶囊 -->
      <div 
        v-if="isExporting || exportFinished" 
        class="mt-3 p-3.5 bg-car-item border-2 rounded-2xl transition-all shadow-md"
        :class="exportFinished ? 'border-emerald-500/60' : 'border-car-accent/70'"
      >
        <div class="flex items-center justify-between text-[14px] font-bold mb-2">
          <span class="flex items-center" :class="exportFinished ? 'text-emerald-400' : 'text-car-accent'">
            <StatusDot v-if="isExporting" class="mr-2.5" size="sm" color="accent" :glow="false" pulse />
            <span v-else class="mr-2">✓</span>
            {{ exportStepText }}
          </span>
          <span class="font-mono font-black" :class="exportFinished ? 'text-emerald-400' : 'text-car-text'">
            {{ exportPercent }}%
          </span>
        </div>
        <div class="w-full h-3 bg-car-card rounded-full overflow-hidden p-0.5 border border-car-border/50">
          <div 
            class="h-full rounded-full transition-all duration-300 ease-out"
            :class="exportFinished ? 'bg-emerald-500 shadow-[0_0_10px_#10B981]' : 'bg-car-accent shadow-[0_0_10px_rgba(245,158,11,0.5)]'"
            :style="{ width: exportPercent + '%' }"
          ></div>
        </div>
        <div v-if="exportFinished && exportFileName" class="mt-2 text-[12.5px] text-car-sub font-mono flex items-center justify-between">
          <span>存储路径: /sdcard/Download/{{ exportFileName }} ({{ exportSizeStr }})</span>
          <span class="text-emerald-400 font-bold">已覆盖唯一样本 · 5重深度脱敏</span>
        </div>
      </div>
    </div>

    <!-- 诊断日志上传确认与结果弹窗 (全量接入车机主题系统，使用 ModalWrapper 标准高实底与主题色) -->
    <ModalWrapper
      :show="showUploadModal"
      title="诊断日志已准备就绪"
      :badge="uploadStatus === 'success' ? '上传成功' : (uploadStatus === 'uploading' ? '正在上传' : (isPausedCountdown ? '倒计时已暂停' : `仅保留30天 · ${uploadCountdown}s`))"
      maxWidthClass="max-w-[1100px]"
      zIndexClass="z-[9999]"
      :showCloseButton="uploadStatus !== 'uploading'"
      :closeOnBackdrop="uploadStatus !== 'uploading'"
      @close="cancelUpload"
    >
      <!-- 阶段 1：确认与安全说明（左右两列排开，单页尽收眼底） -->
      <div 
        v-if="uploadStatus === 'confirm'" 
        class="flex flex-col space-y-3.5 py-1"
        @click="pauseCountdown"
      >
        <div class="flex items-center justify-between bg-car-item rounded-2xl px-5 py-2.5 border border-car-border text-[14px] font-bold font-mono">
          <span>车型平台：<strong class="text-car-accent">{{ logMeta.modelCode || 'SX11A3' }} ({{ logMeta.chip || 'IHU516' }})</strong></span>
          <div class="flex items-center space-x-4">
            <span>文件大小：<strong class="text-emerald-400">{{ logMeta.sizeStr || exportSizeStr || '约 450 KB' }}</strong></span>
            <span v-if="isPausedCountdown" class="text-[12px] font-normal text-amber-500 bg-amber-500/15 px-2.5 py-0.5 rounded-full border border-amber-500/30">
              已停止倒计时，方便您从容阅读
            </span>
          </div>
        </div>

        <!-- 左右并列 2 列栅格，全一屏完整展示杜绝滚动 -->
        <div class="grid grid-cols-2 gap-3.5 text-[13px] leading-relaxed select-none">
          <!-- 左列 1：排查提示 -->
          <div class="p-3.5 rounded-2xl border border-amber-500/40 bg-amber-500/10 text-amber-500 dark:text-amber-300 flex flex-col justify-between">
            <div>
              <div class="font-black text-[14.5px] mb-1 flex items-center">
                <span class="mr-1.5">💡</span>【排查提示：先复现，再抓取】
              </div>
              <p class="text-[12.5px] leading-relaxed">
                如果某个功能异常（如方控不灵、倒车没反应），请务必先将异常操作真实按一遍，确保车机已经记录下当时的按键与信号，这样开发者才能帮您对症下药排查！
              </p>
            </div>
          </div>

          <!-- 右列 1：隐私安全承诺 -->
          <div class="p-3.5 rounded-2xl border border-emerald-500/40 bg-emerald-500/10 text-emerald-600 dark:text-emerald-300 flex flex-col justify-between">
            <div>
              <div class="font-black text-[14.5px] mb-1 flex items-center">
                <span class="mr-1.5">🛡️</span>【隐私安全承诺：不取任何车主个人信息】
              </div>
              <p class="text-[12.5px] leading-relaxed">
                本工具箱在本地已完成 5 重脱敏过滤，日志中：<br>
                • 自动抹除手机号、导航历史记录、家庭/公司等真实位置与轨迹；<br>
                • 隐藏完整车架号（仅留末尾6位用于区分不同车辆，绝不关联个人身份）；<br>
                • 仅记录按键键码、挡位与车身信号，纯用于技术排查，绝无泄露风险。
              </p>
            </div>
          </div>

          <!-- 左列 2：存放规则 -->
          <div class="p-3.5 rounded-2xl border border-sky-500/40 bg-sky-500/10 text-sky-600 dark:text-sky-300 flex flex-col justify-between">
            <div>
              <div class="font-black text-[14.5px] mb-1 flex items-center">
                <span class="mr-1.5">⏳</span>【存放规则：仅保留 30 天，到期全自动销毁】
              </div>
              <p class="text-[12.5px] leading-relaxed">
                • 日志直接传送至开发者专属私有存储库，外界不可见、无法公开下载；<br>
                • 云端仅保留 30 天供技术分析，到期后系统将自动彻底清除，不留任何备份。
              </p>
            </div>
          </div>

          <!-- 右列 2：适配须知 -->
          <div class="p-3.5 rounded-2xl border border-car-border bg-car-item text-car-sub flex flex-col justify-between">
            <div>
              <div class="font-bold text-[14px] mb-1 flex items-center text-car-text">
                <span class="mr-1.5">📢</span>【适配须知：部分车型可能存在物理限制】
              </div>
              <p class="text-[12.5px] leading-relaxed">
                因不同车型的硬件总线和原厂安全协议各不相同，部分功能若受硬件物理隔离，助手可能确实无法实现修复或兼容，敬请各位车友理解。
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- 阶段 2：上传中 -->
      <div v-else-if="uploadStatus === 'uploading'" class="py-14 flex flex-col items-center justify-center text-center space-y-4">
        <div class="w-14 h-14 border-4 border-car-accent/30 border-t-car-accent rounded-full animate-spin"></div>
        <div class="text-[19px] font-black text-car-text">正在安全传输至私有存储库...</div>
        <div class="text-[14px] text-car-sub font-mono">正在流式写入专属隔离桶，请稍候</div>
      </div>

      <!-- 阶段 3：上传成功，展示 8 位纯数字提取码 -->
      <div v-else-if="uploadStatus === 'success'" class="py-4 flex flex-col items-center text-center select-none">
        <div class="w-16 h-16 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center text-3xl font-black mb-3">
          ✓
        </div>
        <h3 class="text-[22px] font-black text-car-text mb-1">诊断日志已安全上传！</h3>
        <p class="text-[14px] text-car-sub mb-4">已归档至私有存储库 · 30天后全自动彻底物理销毁</p>

        <div class="p-6 rounded-2xl bg-car-item border-2 border-emerald-500/50 w-full max-w-[480px] shadow-lg mb-2">
          <div class="text-[13px] font-bold text-car-sub mb-1 uppercase tracking-wider">专属 8 位诊断提取码</div>
          <div class="text-[40px] font-mono font-black text-emerald-400 tracking-[0.2em] my-1">
            {{ extractedCode }}
          </div>
          <div class="text-[13px] text-car-sub mt-2">
            请将上方 8 位数字直接发送给开发者或在交流群反馈
          </div>
        </div>
      </div>

      <!-- 阶段 4：上传失败 -->
      <div v-else-if="uploadStatus === 'error'" class="py-6 flex flex-col items-center text-center">
        <div class="w-16 h-16 rounded-full bg-rose-500/20 text-rose-400 flex items-center justify-center text-3xl font-black mb-3">
          ✕
        </div>
        <h3 class="text-[20px] font-black text-car-text mb-2">上传暂时受阻</h3>
        <p class="text-[14px] text-rose-400 mb-2 max-w-[500px]">{{ uploadErrorMsg }}</p>
      </div>

      <!-- 底部操作按钮插槽 (全量继承主题样式与基线) -->
      <template #footer>
        <template v-if="uploadStatus === 'confirm'">
          <div class="flex items-center justify-between w-full">
            <button 
              @click="cancelUpload"
              class="min-h-[54px] px-6 rounded-2xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-sub hover:text-car-text font-black text-[16px] cursor-pointer transition-all shadow-sm flex items-center"
            >
              <span>放弃上传 {{ isPausedCountdown ? '(已暂停)' : `(${uploadCountdown}s)` }}</span>
            </button>

            <button 
              @click="confirmUpload"
              class="min-h-[54px] px-8 rounded-2xl bg-car-accent hover:opacity-90 text-white font-black text-[16.5px] cursor-pointer transition-all shadow-lg flex items-center"
            >
              <span>立即上传并获取提取码</span>
            </button>
          </div>
        </template>

        <template v-else-if="uploadStatus === 'success'">
          <div class="flex items-center justify-end w-full">
            <button 
              @click="closeUploadModal"
              class="min-h-[54px] px-10 rounded-2xl bg-car-accent text-white font-black text-[16.5px] cursor-pointer hover:opacity-90 shadow-md"
            >
              我已知晓并关闭
            </button>
          </div>
        </template>

        <template v-else-if="uploadStatus === 'error'">
          <div class="flex items-center justify-end space-x-4 w-full">
            <button 
              @click="closeUploadModal"
              class="min-h-[54px] px-6 rounded-2xl bg-car-item border-2 border-car-border text-car-sub font-black text-[15.5px] cursor-pointer hover:border-car-border-light"
            >
              关闭
            </button>
            <button 
              @click="confirmUpload"
              class="min-h-[54px] px-8 rounded-2xl bg-car-accent text-white font-black text-[16px] cursor-pointer"
            >
              重试上传
            </button>
          </div>
        </template>
      </template>
    </ModalWrapper>

    <!-- 底部固定常驻操作栏 (shrink-0 物理吸底，绝不随内容滚动丢失) -->
    <template #footer>
      <div class="flex items-center justify-between w-full">
        <div class="flex items-center space-x-3 shrink-0">
          <button 
            @click="toggleAutoScroll"
            :class="[
              'min-h-[50px] px-5 rounded-xl border-2 font-black text-[15.5px] cursor-pointer transition-all shadow-sm flex items-center',
              autoScroll 
                ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/20' 
                : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            <span class="mr-1.5">{{ autoScroll ? '●' : '○' }}</span>
            {{ autoScroll ? '最新置顶: 开启' : '最新置顶: 暂停' }}
          </button>
          <button 
            @click="manualRefresh"
            class="min-h-[50px] px-5 rounded-xl bg-car-item border-2 border-car-border text-car-text font-black text-[15.5px] cursor-pointer hover:border-car-border-light shadow-sm transition-all flex items-center"
          >
            刷新日志
          </button>
          <button 
            @click="exportFullLogs"
            :disabled="isExporting"
            class="min-h-[50px] px-5 rounded-xl border-2 font-black text-[15.5px] transition-all flex items-center shadow-sm"
            :class="[
              isExporting 
                ? 'bg-car-item border-car-accent text-car-accent opacity-80 cursor-wait' 
                : 'bg-car-item border-car-accent text-car-accent hover:border-car-accent cursor-pointer'
            ]"
          >
            <span>{{ isExporting ? `正在打包 (${exportPercent}%)` : '抓取全量诊断日志 (ZIP)' }}</span>
          </button>
          <button 
            v-if="logMeta.path || exportFileName"
            @click="openUploadConfirm"
            class="min-h-[50px] px-5 rounded-xl bg-gradient-to-r from-sky-500/20 to-indigo-500/20 border-2 border-sky-500/50 text-sky-400 hover:text-sky-300 hover:border-sky-400 font-black text-[15.5px] cursor-pointer shadow-sm transition-all flex items-center"
          >
            <span>☁️ 一键上传分析</span>
          </button>
          <button 
            @click="clearLogs"
            class="min-h-[50px] px-5 rounded-xl bg-car-item border-2 border-car-border hover:border-rose-500/40 text-car-text hover:text-rose-400 font-black text-[15.5px] cursor-pointer shadow-sm transition-all flex items-center"
          >
            清空
          </button>
          <button 
            @click="toggleWifiState"
            class="min-h-[50px] px-4 rounded-xl border-2 font-black text-[15.5px] cursor-pointer transition-all flex items-center shadow-sm"
            :class="isWifiOn 
              ? 'bg-emerald-500/15 border-emerald-500/60 text-emerald-400' 
              : 'bg-car-item border-car-border text-car-sub hover:text-car-text'"
            :title="isWifiOn ? 'Wi-Fi 开启中 (手机可扫码连车机下载日志；排查时可点击关闭以防日志污染)' : '点击开启车机 Wi-Fi (便于手机连接车机局域网下载日志)'"
          >
            <span class="mr-1.5">{{ isWifiOn ? '📶' : '📵' }}</span>
            <span>{{ isWifiOn ? 'Wi-Fi: 已开启' : '打开 Wi-Fi' }}</span>
          </button>
        </div>

        <div class="flex items-center space-x-4">
          <span class="text-[13px] text-car-sub font-mono font-bold hidden md:inline-block">
            {{ autoScroll ? '1.5s 自动置顶最新上报' : '轮转已暂停' }}
          </span>
          <button 
            @click="closeModal('log')"
            class="min-h-[50px] px-8 bg-car-item border-2 border-car-border text-car-text font-black text-[16.5px] rounded-xl cursor-pointer hover:border-car-border-light shadow-sm"
          >
            关闭
          </button>
        </div>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { ref, watch, nextTick, onUnmounted } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import StatusDot from '../StatusDot.vue';
import { store, bridge, closeModal, showToast, openModal } from '../../store';

const logContent = ref('正在加载运行日志...');
const logInfo = ref({ path: '/sdcard/Download/geely_toolbox.log', size: '0 KB' });
const logContainer = ref(null);
const autoScroll = ref(true);
let pollTimer = null;

const MODULE_LABELS = {
  '车身联动': '车身联动',
  '挡位与模式': '挡位与模式',
  '车门与尾门': '车门与尾门',
  '方控按键': '方控按键',
  '音频与蓝牙': '音频与蓝牙',
  '专车语音': '专车语音',
  '屏保与主题': '屏保与主题',
  '电源与系统': '电源与系统'
};
const moduleSwitches = ref([]);

function loadModuleSwitches() {
  try {
    const raw = bridge.call('getLogModuleSwitches');
    const obj = typeof raw === 'string' ? JSON.parse(raw) : (raw || {});
    moduleSwitches.value = Object.keys(MODULE_LABELS).map(key => ({
      key,
      label: MODULE_LABELS[key],
      enabled: !!obj[key]
    }));
  } catch (e) {
    moduleSwitches.value = Object.keys(MODULE_LABELS).map(key => ({
      key,
      label: MODULE_LABELS[key],
      enabled: false
    }));
  }
}

function setAllModules(enabled) {
  moduleSwitches.value.forEach(item => {
    item.enabled = enabled;
    bridge.call('setLogModuleSwitch', item.key, enabled);
  });
  showToast(`8大业务日志已全部${enabled ? '开启' : '关闭'}`);
  fetchLogs(true);
}

function toggleModule(key) {
  const item = moduleSwitches.value.find(m => m.key === key);
  if (!item) return;
  item.enabled = !item.enabled;
  bridge.call('setLogModuleSwitch', key, item.enabled);
  showToast(`${item.label} 日志已${item.enabled ? '开启' : '关闭'}`);
  fetchLogs(true);
}

function fetchLogs(isManual = false) {
  try {
    const rawInfo = bridge.call('getLogInfo');
    if (rawInfo) {
      logInfo.value = typeof rawInfo === 'string' ? JSON.parse(rawInfo) : rawInfo;
    }
  } catch (e) {}

  try {
    const logs = bridge.call('getRecentLogs', 300);
    let next = '';
    if (logs && logs.trim()) {
      // 倒序排列：最新产生的日志行始终置顶显示在最上方第一行
      const lines = logs.trim().split('\n');
      lines.reverse();
      next = lines.join('\n');
    } else {
      next = '[系统启动 · 暂未产生异常日志]\n$ 服务正常常驻中...';
    }
    // 性能铁律：日志内容未变化时坚决不写回响应式数据，避免 1.5 秒一次无意义重排
    if (next !== logContent.value) {
      logContent.value = next;
    }
  } catch (e) {
    logContent.value = '读取日志失败: ' + e;
  }

  if (autoScroll.value || isManual) {
    nextTick(() => {
      if (logContainer.value) {
        logContainer.value.scrollTop = 0; // 始终保持顶部查看最新日志
      }
    });
  }
}

function startPolling() {
  stopPolling();
  loadModuleSwitches();
  fetchLogs();
  pollTimer = setInterval(() => {
    fetchLogs();
  }, 1500);
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

function manualRefresh() {
  fetchLogs(true);
  showToast('已刷新最新运行日志 (置顶显示)');
}

const isExporting = ref(false);
const exportPercent = ref(0);
const exportStepText = ref('');
const exportFinished = ref(false);
const exportFileName = ref('');
const exportSizeStr = ref('');

// 云端免密上传与 20s 倒计时相关状态
const logMeta = ref({
  chip: 'IHU516',
  modelCode: 'SX11A3',
  vinLast6: '000000',
  path: '',
  filename: '',
  sizeStr: ''
});
const showUploadModal = ref(false);
const uploadStatus = ref('confirm'); // 'confirm' | 'uploading' | 'success' | 'error'
const uploadCountdown = ref(20);
const isPausedCountdown = ref(false);
let countdownTimer = null;
const extractedCode = ref('');
const uploadErrorMsg = ref('');

function startCountdown() {
  stopCountdown();
  uploadCountdown.value = 20;
  isPausedCountdown.value = false;
  countdownTimer = setInterval(() => {
    if (uploadCountdown.value > 1) {
      uploadCountdown.value--;
    } else {
      stopCountdown();
      cancelUpload(true);
    }
  }, 1000);
}

function pauseCountdown() {
  if (!isPausedCountdown.value && countdownTimer) {
    stopCountdown();
    isPausedCountdown.value = true;
    showToast('💡 倒计时已暂停，您可以从容查阅说明');
  }
}

function stopCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
}

function openUploadConfirm() {
  uploadStatus.value = 'confirm';
  showUploadModal.value = true;
  startCountdown();
}

function cancelUpload(isTimeout = false) {
  stopCountdown();
  showUploadModal.value = false;
  isPausedCountdown.value = false;
  if (isTimeout) {
    showToast('倒计时结束：日志已保存在车机，未上传云端');
  } else {
    showToast('已取消上传：日志仅保存在车机内部存储');
  }
}

function closeUploadModal() {
  stopCountdown();
  showUploadModal.value = false;
  uploadStatus.value = 'confirm';
  isPausedCountdown.value = false;
}

function confirmUpload() {
  stopCountdown();
  uploadStatus.value = 'uploading';
  const targetPath = logMeta.value.path || ('/sdcard/Download/' + (exportFileName.value || 'SX11A3_IHU516_log.zip'));
  try {
    bridge.call('uploadDiagnosticLog', targetPath);
  } catch (e) {
    uploadStatus.value = 'error';
    uploadErrorMsg.value = '上传日志失败: ' + e;
  }
}

// 接收底层上传回调
window.onLogUploadFinished = (res) => {
  if (res && res.success) {
    uploadStatus.value = 'success';
    extractedCode.value = res.code || '88888888';
  } else {
    uploadStatus.value = 'error';
    uploadErrorMsg.value = (res && res.message) || '上传遇到未知错误，请检查车机网络连接';
  }
};

function exportFullLogs() {
  if (isExporting.value) return;
  isExporting.value = true;
  exportPercent.value = 5;
  exportStepText.value = '正在准备全量采集与脱敏环境...';
  exportFinished.value = false;
  exportFileName.value = '';
  exportSizeStr.value = '';
  try {
    bridge.call('dumpFullSystemLogcat');
  } catch (e) {
    showToast('发起全量日志采集失败: ' + e);
    isExporting.value = false;
  }
}

// 接收全局进度与完成回调
window.onLogDumpProgress = (percent, message) => {
  isExporting.value = true;
  exportPercent.value = percent;
  exportStepText.value = message;
};

window.onLogcatDumpFinished = (res) => {
  isExporting.value = false;
  exportFinished.value = true;
  exportPercent.value = 100;
  if (res && res.success) {
    exportFileName.value = res.filename || 'SX11A3_IHU516_log.zip';
    exportSizeStr.value = res.sizeStr || '';
    exportStepText.value = '✓ ' + (res.message || '全量脱敏日志打包完成');
    
    // 更新元数据
    logMeta.value = {
      chip: res.chip || 'IHU516',
      modelCode: res.modelCode || 'SX11A3',
      vinLast6: res.vinLast6 || '000000',
      path: res.path || ('/sdcard/Download/' + exportFileName.value),
      filename: exportFileName.value,
      sizeStr: exportSizeStr.value
    };

    // 延迟 400ms 弹出润色后的上传确认与 20s 倒计时弹窗
    setTimeout(() => {
      openUploadConfirm();
    }, 400);
  } else {
    exportStepText.value = '❌ ' + ((res && res.message) || '日志打包失败');
  }
  setTimeout(() => {
    exportFinished.value = false;
  }, 6000);
};

function toggleAutoScroll() {
  autoScroll.value = !autoScroll.value;
  if (autoScroll.value) {
    nextTick(() => {
      if (logContainer.value) {
        logContainer.value.scrollTop = 0;
      }
    });
    showToast('最新日志自动置顶已开启');
  } else {
    showToast('最新日志自动置顶已暂停');
  }
}

const isWifiOn = ref(false);

function checkWifiState() {
  try {
    const res = bridge.call('isWifiEnabled');
    if (res !== undefined && res !== null) {
      isWifiOn.value = (res === true || res === 'true');
      return;
    }
    const raw = bridge.call('getConnectivityStatus');
    if (raw) {
      const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
      if (data && data.wifi_enabled !== undefined) {
        isWifiOn.value = !!data.wifi_enabled;
      }
    }
  } catch (e) {}
}

function toggleWifiState() {
  const target = !isWifiOn.value;
  try {
    bridge.call('toggleWifi', target);
    isWifiOn.value = target;
    setTimeout(checkWifiState, 600);
    setTimeout(checkWifiState, 1500);
  } catch (e) {
    showToast('切换 Wi-Fi 失败: ' + e);
  }
}

watch(() => store.modals.log, (show) => {
  if (show) {
    checkWifiState();
    startPolling();
  } else {
    stopPolling();
  }
});

onUnmounted(() => {
  stopPolling();
  stopCountdown();
});

function clearLogs() {
  openModal('confirm', {
    title: '清空运行日志',
    desc: '即将彻底清空 /sdcard/Download/geely_toolbox.log 当前全部日志记录。清空后不影响后续新日志写入。是否确认清空？',
    isDanger: false,
    onConfirm: () => {
      bridge.call('clearRunLog');
      logContent.value = '[日志已清空]';
      showToast('运行与守护日志已清空');
    }
  });
}
</script>
