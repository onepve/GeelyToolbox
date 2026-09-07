<template>
  <ModalWrapper 
    :show="store.modals.deepTools" 
    title="车机深度工具箱 & ADB 终端" 
    badge="特权维护"
    maxWidthClass="max-w-[1080px]"
    @close="closeModal('deepTools')"
  >
    <!-- 顶部 ADB 状态胶囊 -->
    <div class="flex items-center justify-between bg-car-item border border-car-border rounded-2xl p-4">
      <div class="flex items-center gap-3">
        <span class="w-3 h-3 rounded-full bg-emerald-500 shadow-[0_0_8px_#10B981]"></span>
        <span class="text-[17.5px] font-black text-car-text">ADB 端口状态：127.0.0.1:5555 (Shell 2000 就绪)</span>
      </div>
      <button 
        @click="dumpLogcat"
        class="h-[44px] px-5 rounded-xl bg-car-card border border-car-border text-car-text font-black text-[15px] cursor-pointer hover:border-car-border-light"
      >
        采集车机全量日志 (ZIP)
      </button>
    </div>

    <!-- 核心组件推荐冻结列表 -->
    <div class="flex flex-col gap-3">
      <div class="text-[19px] font-black text-car-text">原厂核心组件推荐冻结 / 解冻</div>
      <div class="grid grid-cols-3 gap-3">
        <!-- 应用商店 -->
        <div class="bg-car-item border border-car-border rounded-xl p-4 flex flex-col justify-between gap-3">
          <div class="flex flex-col gap-1">
            <span class="text-[17px] font-black text-car-text">吉利应用商店</span>
            <span class="text-[13px] text-car-sub font-mono">com.ecarx.appstore</span>
          </div>
          <button 
            @click="toggleFreeze('com.ecarx.appstore')"
            :class="[
              'h-[46px] rounded-lg border-2 font-black text-[15px] cursor-pointer transition-all',
              store.deviceInfo.appstore_frozen 
                ? 'bg-car-card border-car-accent text-car-text' 
                : 'bg-rose-500/10 border-rose-500/40 text-rose-500'
            ]"
          >
            {{ store.deviceInfo.appstore_frozen ? '已冻结 (白名单生效)' : '运行中 · 点击一键冻结' }}
          </button>
        </div>

        <!-- 系统 OTA -->
        <div class="bg-car-item border border-car-border rounded-xl p-4 flex flex-col justify-between gap-3">
          <div class="flex flex-col gap-1">
            <span class="text-[17px] font-black text-car-text">原厂 OTA 升级服务</span>
            <span class="text-[13px] text-car-sub font-mono">ecarx.upgrade</span>
          </div>
          <button 
            @click="toggleFreeze('ecarx.upgrade')"
            class="h-[46px] rounded-lg border border-car-border bg-car-card text-car-text font-black text-[15px] cursor-pointer hover:border-car-border-light"
          >
            冻结 / 解冻切换
          </button>
        </div>

        <!-- 原厂多媒体伴听 -->
        <div class="bg-car-item border border-car-border rounded-xl p-4 flex flex-col justify-between gap-3">
          <div class="flex flex-col gap-1">
            <span class="text-[17px] font-black text-car-text">原厂多媒体伴听</span>
            <span class="text-[13px] text-car-sub font-mono">com.ecarx.xcmedia</span>
          </div>
          <button 
            @click="toggleFreeze('com.ecarx.xcmedia')"
            class="h-[46px] rounded-lg border border-car-border bg-car-card text-car-text font-black text-[15px] cursor-pointer hover:border-car-border-light"
          >
            冻结 / 解冻切换
          </button>
        </div>
      </div>
    </div>

    <!-- 独立终端控制台 (黑底极客视口) -->
    <div class="flex flex-col gap-2.5">
      <div class="flex items-center justify-between">
        <span class="text-[19px] font-black text-car-text">ADB 交互控制台</span>
        <div class="flex gap-2">
          <button 
            @click="clearOutput"
            class="h-[36px] px-3.5 rounded-lg bg-car-item border border-car-border text-car-sub hover:text-car-text font-bold text-[13.5px] cursor-pointer"
          >
            清屏
          </button>
          <button 
            @click="saveLog"
            class="h-[36px] px-3.5 rounded-lg bg-car-item border border-car-border text-car-sub hover:text-car-text font-bold text-[13.5px] cursor-pointer"
          >
            保存日志到 Download
          </button>
        </div>
      </div>

      <!-- 快捷指令胶囊 -->
      <div class="flex flex-wrap gap-2">
        <button 
          v-for="cmd in quickCmds" 
          :key="cmd"
          @click="inputCmd = cmd"
          class="px-3 py-1 rounded-md bg-car-item border border-car-border text-car-sub hover:text-car-text text-[13px] font-mono cursor-pointer"
        >
          {{ cmd }}
        </button>
      </div>

      <!-- 命令行输入栏 -->
      <div class="flex gap-2">
        <input 
          v-model="inputCmd"
          type="text"
          placeholder="请输入 Shell / ADB 指令，如: pm list packages"
          class="flex-1 h-[52px] bg-[#0A0D12] border border-car-border rounded-xl px-4 text-emerald-400 font-mono text-[16px] outline-none focus:border-car-accent"
          @keyup.enter="execCmd"
        />
        <button 
          @click="execCmd"
          class="h-[52px] px-8 bg-car-item border-2 border-car-accent rounded-xl text-car-text font-black text-[17px] cursor-pointer hover:border-car-accent"
        >
          执行指令
        </button>
      </div>

      <!-- 黑色终端输出屏 -->
      <pre class="min-h-[220px] max-h-[300px] overflow-y-auto bg-[#0A0D12] border border-white/10 rounded-2xl p-4 font-mono text-[14px] text-emerald-400 leading-relaxed select-text whitespace-pre-wrap">{{ outputText }}</pre>
    </div>
  </ModalWrapper>
</template>

<script setup>
import { ref } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, showToast } from '../../store';

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
  showToast('指令已下发，正在更新组件状态...');
}

function dumpLogcat() {
  bridge.call('dumpSystemLogcat');
  showToast('系统日志已导出并压缩至 /sdcard/Download/car_full.zip');
}
</script>
