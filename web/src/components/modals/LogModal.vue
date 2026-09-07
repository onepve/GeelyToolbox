<template>
  <ModalWrapper
    :show="store.modals.log"
    title="吉利智驾 · 运行与守护日志"
    badge="自动轮转"
    maxWidthClass="max-w-[1080px]"
    @close="closeModal('log')"
  >
    <div class="flex flex-col space-y-4">
      <!-- 顶部状态与工具栏 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
        <div class="flex items-center">
          <span class="w-3 h-3 rounded-full bg-emerald-500 mr-3 shadow-[0_0_8px_#10B981]"></span>
          <div class="flex flex-col">
            <span class="text-[17px] font-black text-car-text">日志存储路径: {{ logInfo.path }}</span>
            <span class="text-[14px] text-car-sub font-mono font-bold mt-0.5">当前文件体积: {{ logInfo.size }} · 自动保留最新 300 行</span>
          </div>
        </div>

        <div class="flex items-center space-x-3">
          <button 
            @click="fetchLogs"
            class="h-[52px] px-6 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-border-light shadow-sm transition-all"
          >
            刷新日志
          </button>
          <button 
            @click="clearLogs"
            class="h-[52px] px-6 rounded-xl bg-car-card border-2 border-car-border text-car-sub hover:text-car-text font-black text-[16px] cursor-pointer hover:border-car-border-light shadow-sm transition-all"
          >
            清空日志
          </button>
        </div>
      </div>

      <!-- 核心日志控制台输出屏 -->
      <pre 
        ref="logContainer"
        class="min-h-[300px] max-h-[440px] overflow-y-auto bg-[#0A0D12] border-2 border-white/10 rounded-2xl p-5 font-mono text-[14.5px] text-emerald-400 leading-relaxed select-text whitespace-pre-wrap shadow-inner"
      >{{ logContent || '暂无日志记录...' }}</pre>
    </div>

    <template #footer>
      <div class="flex items-center justify-between w-full">
        <span class="text-[14px] text-car-sub font-bold">
          提示：守护服务自动监控车门、挡位、方控与 U 盘事件，所有状态切换均在此留下安全审计日志。
        </span>
        <button 
          @click="closeModal('log')"
          class="h-[56px] px-8 bg-car-item border-2 border-car-border text-car-text font-black text-[18px] rounded-2xl cursor-pointer hover:border-car-border-light shadow-sm"
        >
          关闭
        </button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, showToast, openModal } from '../../store';

const logContent = ref('正在加载中枢运行日志...');
const logInfo = ref({ path: '/sdcard/Download/geely_toolbox.log', size: '0 KB' });
const logContainer = ref(null);

watch(() => store.modals.log, (show) => {
  if (show) {
    fetchLogs();
  }
});

function fetchLogs() {
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

  nextTick(() => {
    if (logContainer.value) {
      logContainer.value.scrollTop = logContainer.value.scrollHeight;
    }
  });
}

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
