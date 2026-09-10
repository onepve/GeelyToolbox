<template>
  <ModalWrapper
    :show="store.modals.log"
    title="吉利智驾 · 运行与守护日志"
    :badge="autoScroll ? '● 实时轮转中 (1.5s)' : '⏸️ 自动滚动已暂停'"
    maxWidthClass="max-w-[1080px]"
    :showCloseButton="false"
    @close="closeModal('log')"
  >
    <div class="flex flex-col space-y-3">
      <!-- 顶部轻量信息条 (不占过多垂直空间，防遮挡) -->
      <div class="bg-car-item border border-car-border rounded-xl px-4 py-2.5 flex items-center justify-between shadow-sm">
        <div class="flex items-center space-x-2.5">
          <span class="w-2.5 h-2.5 rounded-full bg-emerald-500 shadow-[0_0_8px_#10B981]"></span>
          <span class="text-[14.5px] font-mono text-car-text font-bold">
            {{ logInfo.path }}
          </span>
        </div>
        <div class="flex items-center space-x-2 text-[13px] text-car-sub font-bold">
          <span>体积: {{ logInfo.size }}</span>
          <span>·</span>
          <span class="text-car-accent font-mono font-bold">保留最新 300 行</span>
        </div>
      </div>

      <!-- 模块日志开关：用户可独立控制高频模块写入，默认关闭以避免日志风暴卡死界面 -->
      <div class="bg-car-item border border-car-border rounded-xl p-3 shadow-sm">
        <div class="text-[13.5px] text-car-sub font-bold mb-2 flex items-center">
          <span class="mr-1.5">🎛️</span>
          模块日志采集开关（默认关闭高频通道，按需开启）
        </div>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="item in moduleSwitches"
            :key="item.key"
            @click="toggleModule(item.key)"
            :class="[
              'px-3 py-1.5 rounded-lg border-2 text-[13px] font-black cursor-pointer transition-all',
              item.enabled
                ? 'bg-emerald-500/15 border-emerald-500/60 text-emerald-400'
                : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
            ]"
          >
            {{ item.enabled ? '●' : '○' }} {{ item.label }}
          </button>
        </div>
      </div>

      <!-- 核心日志控制台输出屏 (固定高度，独立平滑滚动，白天/黑夜双主题护眼自适应) -->
      <pre 
        ref="logContainer"
        class="h-[380px] overflow-y-auto rounded-2xl p-5 font-mono text-[14.5px] font-semibold leading-[1.75] select-text whitespace-pre-wrap shadow-inner transition-colors duration-200"
        :class="[
          store.isNight 
            ? 'bg-[#0B101B] border-2 border-white/15 text-[#E2E8F0] shadow-black/80' 
            : 'bg-[#F8FAFC] border-2 border-[#CBD5E1] text-[#0F172A] shadow-slate-200'
        ]"
      >{{ logContent || '暂无日志记录...' }}</pre>
    </div>

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
            <span class="mr-1.5">{{ autoScroll ? '●' : '⏸️' }}</span>
            {{ autoScroll ? '自动滚动: 开启' : '自动滚动: 暂停' }}
          </button>
          <button 
            @click="manualRefresh"
            class="min-h-[50px] px-5 rounded-xl bg-car-item border-2 border-car-border text-car-text font-black text-[15.5px] cursor-pointer hover:border-car-border-light shadow-sm transition-all flex items-center"
          >
            <span class="mr-1.5">🔄</span> 刷新日志
          </button>
          <button 
            @click="clearLogs"
            class="min-h-[50px] px-5 rounded-xl bg-car-item border-2 border-car-border hover:border-rose-500/40 text-car-text hover:text-rose-400 font-black text-[15.5px] cursor-pointer shadow-sm transition-all flex items-center"
          >
            <span class="mr-1.5 opacity-80">🗑️</span> 清空
          </button>
        </div>

        <div class="flex items-center space-x-4">
          <span class="text-[13px] text-car-sub font-mono font-bold hidden md:inline-block">
            {{ autoScroll ? '1.5s 自动同步最新上报' : '轮转已暂停' }}
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
import { store, bridge, closeModal, showToast, openModal } from '../../store';

const logContent = ref('正在加载中枢运行日志...');
const logInfo = ref({ path: '/sdcard/Download/geely_toolbox.log', size: '0 KB' });
const logContainer = ref(null);
const autoScroll = ref(true);
let pollTimer = null;

const MODULE_LABELS = {
  '方控按键': '方控按键',
  '车门状态': '车门状态',
  '挡位状态': '挡位状态',
  '驾驶模式': '驾驶模式',
  '电源状态': '电源状态',
  'HAL探针': 'HAL探针',
  '系统日志': '系统日志'
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
    if (logs && logs.trim()) {
      logContent.value = logs;
    } else {
      logContent.value = '[系统启动 · 暂未产生异常日志]\n$ 服务正常常驻中...';
    }
  } catch (e) {
    logContent.value = '读取日志失败: ' + e;
  }

  if (autoScroll.value || isManual) {
    nextTick(() => {
      if (logContainer.value) {
        logContainer.value.scrollTop = logContainer.value.scrollHeight;
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
  showToast('已刷新最新运行日志');
}

function toggleAutoScroll() {
  autoScroll.value = !autoScroll.value;
  if (autoScroll.value) {
    nextTick(() => {
      if (logContainer.value) {
        logContainer.value.scrollTop = logContainer.value.scrollHeight;
      }
    });
    showToast('自动滚底已开启');
  } else {
    showToast('自动滚底已暂停');
  }
}

watch(() => store.modals.log, (show) => {
  if (show) {
    startPolling();
  } else {
    stopPolling();
  }
});

onUnmounted(() => {
  stopPolling();
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
