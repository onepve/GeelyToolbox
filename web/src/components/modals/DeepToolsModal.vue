<template>
  <ModalWrapper 
    :show="store.modals.deepTools" 
    title="车机深度工具箱 & ADB 终端" 
    badge="特权维护"
    maxWidthClass="max-w-[1080px]"
    @close="closeModal('deepTools')"
  >
    <!-- 顶部 ADB 状态胶囊 (车规大卡片) -->
    <div class="flex items-center justify-between bg-car-item border border-car-border rounded-3xl p-5 shadow-sm mb-5">
      <div class="flex items-center">
        <span class="w-3.5 h-3.5 rounded-full bg-emerald-500 shadow-[0_0_10px_#10B981] mr-3.5"></span>
        <span class="text-[20px] font-black text-car-text">ADB 端口状态：127.0.0.1:5555 (Shell 2000 就绪)</span>
      </div>
      <button 
        @click="dumpLogcat"
        class="min-h-[66px] px-8 rounded-2xl bg-car-card border-2 border-car-border text-car-text font-black text-[18.5px] cursor-pointer hover:border-car-border-light shadow-sm"
      >
        采集车机全量日志 (ZIP)
      </button>
    </div>

    <!-- 核心组件推荐冻结列表 (大卡片 + 66px 按钮) -->
    <div class="mb-5">
      <div class="text-[21px] font-black text-car-text mb-3">原厂核心组件推荐冻结 / 解冻</div>
      <div class="flex space-x-4">
        <!-- 应用商店 -->
        <div class="flex-1 bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between shadow-sm">
          <div class="flex flex-col mb-4">
            <span class="text-[19px] font-black text-car-text mb-1">吉利应用商店</span>
            <span class="text-[14px] text-car-sub font-mono">com.ecarx.appstore</span>
          </div>
          <button 
            @click="toggleFreeze('com.ecarx.appstore')"
            :class="[
              'min-h-[66px] rounded-2xl border-2 font-black text-[17.5px] cursor-pointer transition-all',
              packageStates['com.ecarx.appstore'] 
                ? 'bg-car-card border-emerald-500/60 text-emerald-400' 
                : 'bg-car-card border-amber-500/70 text-car-text hover:border-amber-400'
            ]"
          >
            {{ packageStates['com.ecarx.appstore'] ? '已安全冻结 (白名单锁定)' : '运行中 · 点击安全冻结' }}
          </button>
        </div>

        <!-- 系统 OTA -->
        <div class="flex-1 bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between shadow-sm">
          <div class="flex flex-col mb-4">
            <span class="text-[19px] font-black text-car-text mb-1">原厂 OTA 升级服务</span>
            <span class="text-[14px] text-car-sub font-mono">ecarx.upgrade</span>
          </div>
          <button 
            @click="toggleFreeze('ecarx.upgrade')"
            :class="[
              'min-h-[66px] rounded-2xl border-2 font-black text-[17.5px] cursor-pointer transition-all',
              packageStates['ecarx.upgrade'] 
                ? 'bg-car-card border-emerald-500/60 text-emerald-400' 
                : 'bg-car-card border-car-border text-car-text hover:border-car-border-light'
            ]"
          >
            {{ packageStates['ecarx.upgrade'] ? '已安全冻结 (OTA静默)' : '运行中 · 点击安全冻结' }}
          </button>
        </div>

        <!-- 原厂多媒体伴听 -->
        <div class="flex-1 bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between shadow-sm">
          <div class="flex flex-col mb-4">
            <span class="text-[19px] font-black text-car-text mb-1">原厂多媒体伴听</span>
            <span class="text-[14px] text-car-sub font-mono">com.ecarx.multimedia</span>
          </div>
          <button 
            @click="toggleFreeze('com.ecarx.multimedia')"
            :class="[
              'min-h-[66px] rounded-2xl border-2 font-black text-[17.5px] cursor-pointer transition-all',
              packageStates['com.ecarx.multimedia'] 
                ? 'bg-car-card border-emerald-500/60 text-emerald-400' 
                : 'bg-car-card border-amber-500/70 text-car-text hover:border-amber-400'
            ]"
          >
            {{ packageStates['com.ecarx.multimedia'] ? '已安全冻结 (方控免误弹)' : '运行中 · 点击安全冻结' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 独立终端控制台 (大触控输入 + 大按键) -->
    <div class="flex flex-col">
      <div class="flex items-center justify-between mb-3">
        <span class="text-[21px] font-black text-car-text">ADB 交互控制台</span>
        <div class="flex items-center">
          <button 
            @click="openOtaCapture"
            class="min-h-[50px] px-5 mr-3 rounded-xl bg-amber-500/15 border-2 border-amber-500/50 text-amber-400 hover:border-amber-400 font-black text-[15.5px] cursor-pointer shadow-sm flex items-center shrink-0"
          >
            抓 OTA 地址
          </button>
          <button 
            @click="clearOutput"
            class="min-h-[50px] px-6 mr-3 rounded-xl bg-car-item border border-car-border text-car-sub hover:text-car-text font-bold text-[16px] cursor-pointer hover:border-car-border-light"
          >
            清屏
          </button>
          <button 
            @click="saveLog"
            class="min-h-[50px] px-6 rounded-xl bg-car-item border border-car-border text-car-sub hover:text-car-text font-bold text-[16px] cursor-pointer hover:border-car-border-light"
          >
            保存日志到 Download
          </button>
        </div>
      </div>

      <!-- 快捷指令胶囊 (大号点击区) -->
      <div class="flex flex-wrap mb-3">
        <button 
          v-for="cmd in quickCmds" 
          :key="cmd"
          @click="inputCmd = cmd"
          class="min-h-[46px] px-4 py-2 mr-2 mb-2 rounded-xl bg-car-item border border-car-border text-car-sub hover:text-car-text text-[15px] font-mono cursor-pointer hover:border-car-border-light font-bold"
        >
          {{ cmd }}
        </button>
      </div>

      <!-- 命令行输入栏 (68px 车规大高度) -->
      <div class="flex mb-4">
        <input 
          v-model="inputCmd"
          type="text"
          placeholder="请输入 Shell / ADB 指令，如: pm list packages"
          class="flex-1 h-[68px] bg-[#0A0D12] border-2 border-car-border rounded-2xl px-5 text-emerald-400 font-mono text-[18px] outline-none focus:border-car-accent mr-3"
          @keyup.enter="execCmd"
        />
        <button 
          @click="execCmd"
          class="min-h-[68px] px-10 bg-car-item border-2 border-car-accent rounded-2xl text-car-text font-black text-[20px] cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 shadow-md shrink-0"
        >
          执行指令
        </button>
      </div>

      <!-- 终端输出屏 -->
      <pre class="min-h-[220px] max-h-[300px] overflow-y-auto bg-[#0A0D12] border-2 border-white/10 rounded-2xl p-5 font-mono text-[15px] text-emerald-400 leading-relaxed select-text whitespace-pre-wrap">{{ outputText }}</pre>
    </div>
  </ModalWrapper>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, openModal, showToast } from '../../store';

const packageStates = ref({
  'com.ecarx.appstore': false,
  'ecarx.upgrade': false,
  'com.ecarx.multimedia': false
});

function refreshPackageStates() {
  try {
    packageStates.value['com.ecarx.appstore'] = !!bridge.call('isPackageFrozen', 'com.ecarx.appstore');
    packageStates.value['ecarx.upgrade'] = !!bridge.call('isPackageFrozen', 'ecarx.upgrade');
    packageStates.value['com.ecarx.multimedia'] = !!bridge.call('isPackageFrozen', 'com.ecarx.multimedia');
  } catch (e) {}
}

watch(() => store.modals.deepTools, (show) => {
  if (show) {
    refreshPackageStates();
  }
});

onMounted(() => {
  refreshPackageStates();
});

function openOtaCapture() {
  openModal('otaCapture');
}

const inputCmd = ref('');
const outputText = ref('[ADB Client 127.0.0.1:5555 就绪 · 输入指令并回车执行]\n$ ');

const quickCmds = [
  'getprop ro.product.model',
  'pm list packages -3',
  'dumpsys meminfo',
  'logcat -d -v time | tail -n 20'
];

function execCmd() {
  if (!inputCmd.value.trim()) return;
  const cmd = inputCmd.value.trim();
  outputText.value += `\n$ ${cmd}\n`;
  inputCmd.value = '';
  
  try {
    const res = bridge.call('executeCustomAdbCommand', cmd);
    outputText.value += (res || '(执行完成，无返回输出)') + '\n$ ';
  } catch (e) {
    outputText.value += `执行错误: ${e}\n$ `;
  }
}

function clearOutput() {
  outputText.value = '$ ';
}

function saveLog() {
  bridge.call('saveTerminalLog', outputText.value);
  showToast('终端日志已保存至 /sdcard/Download/');
}

function toggleFreeze(pkg) {
  bridge.call('togglePackageFreeze', pkg);
  showToast('正在下发 ADB 指令更新组件状态...');
  setTimeout(() => {
    refreshPackageStates();
  }, 1200);
}

function dumpLogcat() {
  bridge.call('dumpSystemLogcat');
  showToast('系统日志已导出并压缩至 /sdcard/Download/car_full.zip');
}
</script>
