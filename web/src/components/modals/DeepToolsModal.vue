<template>
  <ModalWrapper 
    :show="store.modals.deepTools" 
    title="车机深度工具箱 & ADB 终端" 
    badge="特权调试"
    maxWidthClass="max-w-[1080px]"
    @close="closeModal('deepTools')"
  >
    <!-- 顶部 ADB 状态胶囊 (车规大卡片 · 真实动态探测 · 纯净车规文案) -->
    <div class="flex items-center justify-between bg-car-item border border-car-border rounded-3xl p-5 shadow-sm mb-5">
      <div class="flex items-center space-x-3.5">
        <StatusDot size="lg" :color="adbStatus.ready ? 'ok' : 'off'" :glow-px="adbStatus.ready ? 10 : 0" />
        <div class="flex flex-col">
          <div class="flex items-center space-x-2">
            <span class="text-[19px] font-black text-car-text">{{ adbStatus.title }}</span>
            <span 
              :class="[
                'px-2 py-0.5 text-[11.5px] font-black rounded-full border',
                adbStatus.ready ? 'bg-emerald-500/15 border-emerald-500/50 text-emerald-400' : 'bg-car-card border-car-border text-car-sub'
              ]"
            >
              {{ adbStatus.privilege }}
            </span>
          </div>
          <span class="text-[13px] font-bold text-car-sub mt-0.5">{{ adbStatus.details }}</span>
        </div>
      </div>
      <div class="flex items-center space-x-3">
        <button 
          @click="checkAdbStatus(true)"
          :disabled="isCheckingAdb"
          class="min-h-[56px] px-5 rounded-2xl border border-car-border bg-car-card text-car-text hover:border-car-border-light font-black text-[14.5px] shadow-sm transition-all flex items-center justify-center cursor-pointer"
        >
          <span>{{ isCheckingAdb ? '探测中...' : '重新检测 ADB' }}</span>
        </button>
        <button 
          @click="openLogModalFromAdb"
          class="min-h-[56px] px-6 rounded-2xl border-2 border-car-border bg-car-card text-car-text hover:border-car-border-light font-black text-[15.5px] shadow-sm transition-all flex items-center justify-center cursor-pointer"
        >
          <span>系统日志导出 ➔</span>
        </button>
      </div>
    </div>

    <!-- 独立终端控制台 (大触控输入 + 大按键 · 纯粹系统交互中枢) -->
    <div class="flex flex-col">
      <div class="flex items-center justify-between mb-3">
        <span class="text-[21px] font-black text-car-text">ADB 交互控制台</span>
        <div class="flex items-center">
          <button 
            @click="toggleKeyEventCapture"
            :class="[
              'min-h-[50px] px-5 mr-3 rounded-xl border-2 font-black text-[15.5px] cursor-pointer shadow-sm flex items-center shrink-0 transition-all',
              isCapturingKeys
                ? 'bg-rose-500/20 border-rose-500 text-rose-400 animate-pulse ring-2 ring-rose-500/30'
                : 'bg-amber-500/15 border-amber-500/50 text-amber-400 hover:border-amber-400'
            ]"
          >
            <span :class="['w-2.5 h-2.5 rounded-full mr-2 shrink-0', isCapturingKeys ? 'bg-rose-500 animate-ping' : 'bg-amber-400']"></span>
            {{ isCapturingKeys ? '停止抓取' : '抓取按键码' }}
          </button>
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

      <!-- 快捷指令胶囊 (中文作用说明，点击自动填入对应英文命令) -->
      <div class="flex flex-wrap mb-3">
        <button 
          v-for="item in quickCmds" 
          :key="item.cmd"
          @click="inputCmd = item.cmd"
          class="min-h-[52px] px-5 py-2.5 mr-2.5 mb-2.5 rounded-2xl bg-car-item border border-car-border text-car-text hover:text-car-text text-[15.5px] cursor-pointer hover:border-car-accent font-bold"
        >
          {{ item.label }}
        </button>
      </div>

      <!-- 命令行输入栏 (68px 车规大高度) -->
      <div class="flex mb-4">
        <input 
          v-model="inputCmd"
          type="text"
          placeholder="请输入 Shell / ADB 指令，如: pm list packages"
          :class="[
            'flex-1 h-[68px] border-2 border-car-border rounded-2xl px-5 font-mono text-[18px] outline-none focus:border-car-accent mr-3 transition-all',
            store.isNight ? 'bg-[var(--term-bg)] text-emerald-400' : 'bg-car-item text-car-text'
          ]"
          @keyup.enter="execCmd"
        />
        <button 
          @click="execCmd"
          class="min-h-[68px] px-10 bg-car-item border-2 border-car-accent rounded-2xl text-car-text font-black text-[20px] cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 shadow-md shrink-0"
        >
          执行指令
        </button>
      </div>

      <!-- 终端输出屏 (最新执行结果始终置顶在最上方第一行) -->
      <pre 
        ref="termContainer"
        :class="[
          'min-h-[260px] max-h-[380px] overflow-y-auto border-2 border-car-border rounded-2xl p-5 font-mono text-[15px] leading-relaxed select-text whitespace-pre-wrap shadow-inner transition-all',
          store.isNight ? 'bg-[var(--term-bg)] text-emerald-400' : 'bg-car-item text-emerald-700'
        ]"
      >{{ outputText }}</pre>
    </div>
  </ModalWrapper>
</template>

<script setup>
import { ref, watch, onMounted, nextTick } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import StatusDot from '../StatusDot.vue';
import { store, bridge, closeModal, openModal, showToast } from '../../store';

// 真实 ADB 状态对象 (绝不硬编码假数据)
const adbStatus = ref({
  ready: false,
  title: 'ADB 端口检测中...',
  details: '正在探测本地 127.0.0.1:5555 与特权状态...',
  privilege: '检测中'
});
const isCheckingAdb = ref(false);

function checkAdbStatus(userTriggered = false) {
  if (isCheckingAdb.value) return;
  isCheckingAdb.value = true;
  if (userTriggered) showToast('正在重新探测车机 5555 调试端口...');
  
  try {
    const raw = bridge.call('probeAdbStatus');
    if (raw) {
      const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
      adbStatus.value = {
        ready: !!data.ready,
        title: data.title || (data.ready ? 'ADB 端口已就绪' : 'ADB 端口未连接'),
        details: data.details || (data.ready ? '已连接 127.0.0.1:5555' : '127.0.0.1:5555 离线/未授权'),
        privilege: data.privilege || (data.ready ? 'Shell 2000' : '未连接')
      };
      if (userTriggered) {
        showToast(adbStatus.value.ready ? 'ADB 探测成功：已就绪' : 'ADB 端口未连接：' + adbStatus.value.details);
      }
    } else {
      adbStatus.value = {
        ready: false,
        title: 'ADB 端口未连接',
        details: '127.0.0.1:5555 离线或模拟器环境无 ADB 端口',
        privilege: '未连接'
      };
    }
  } catch (e) {
    adbStatus.value = {
      ready: false,
      title: 'ADB 探测异常',
      details: '桥接异常或模拟器无直连端口: ' + e,
      privilege: '未连接'
    };
  } finally {
    isCheckingAdb.value = false;
    updateTerminalPrompt();
  }
}

const isCapturingKeys = ref(false);

function toggleKeyEventCapture() {
  if (isCapturingKeys.value) {
    stopKeyEventCapture();
  } else {
    startKeyEventCapture();
  }
}

function startKeyEventCapture() {
  if (!adbStatus.value.ready) {
    showToast('ADB 端口未就绪，无法监听物理按键码');
    return;
  }
  try {
    const success = bridge.call('startKeyEventCapture');
    if (success) {
      isCapturingKeys.value = true;
      const tip = `[ADB 方控按键实时监听已启动 · 请按下方向盘任意按键]\n[说明: 屏幕将实时置顶打印捕获到的 /dev/input 节点与十六进制键值，再次点击按钮可立即停止]\n────────────────────────────────────────────────────────────\n`;
      outputText.value = tip + outputText.value;
      showToast('方控按键监听已开启，请按下方向盘按键');
    } else {
      showToast('启动按键监听失败，请检查 ADB 连接状态');
    }
  } catch (e) {
    showToast(`启动按键监听异常: ${e}`);
  }
}

function stopKeyEventCapture() {
  try {
    bridge.call('stopKeyEventCapture');
  } catch (e) {}
  isCapturingKeys.value = false;
  const tip = `[ADB 方控按键监听已主动停止]\n────────────────────────────────────────────────────────────\n`;
  outputText.value = tip + outputText.value;
  showToast('方控按键监听已停止');
}

// 接收 Java 异步流式按键事件回调
window.onKeyEventCaptured = (line) => {
  if (!line || !line.trim()) return;
  const now = new Date();
  const timeStr = now.toTimeString().split(' ')[0] + '.' + String(now.getMilliseconds()).padStart(3, '0');
  const logLine = `[${timeStr}] ${line.trim()}\n`;
  outputText.value = logLine + outputText.value;
};

window.onKeyEventCaptureCompleted = (success, error) => {
  isCapturingKeys.value = false;
};

watch(() => store.modals.deepTools, (show) => {
  if (show) {
    checkAdbStatus(false);
  } else if (isCapturingKeys.value) {
    stopKeyEventCapture();
  }
});

onMounted(() => {
  if (store.modals.deepTools) {
    checkAdbStatus(false);
  }
});

function openOtaCapture() {
  openModal('otaCapture');
}

function openLogModalFromAdb() {
  closeModal('deepTools');
  openModal('log');
}

const termContainer = ref(null);
const inputCmd = ref('');
const outputText = ref('');

function updateTerminalPrompt() {
  if (adbStatus.value.ready) {
    outputText.value = `[ADB Client 127.0.0.1:5555 就绪 (${adbStatus.value.privilege}) · 最新输出置顶显示]\n$ `;
  } else {
    outputText.value = `[ADB 端口未连接 · 127.0.0.1:5555 离线或模拟器环境无端口 · 特权指令受限]\n$ `;
  }
}

const quickCmds = [
  { label: '开启白名单', cmd: 'adb shell setprop sys.jsbd.apk_verify 1' },
  { label: '查看车机型号', cmd: 'getprop ro.product.model' },
  { label: '列出全部已装应用', cmd: 'pm list packages -3' },
  { label: '查看内存占用', cmd: 'dumpsys meminfo' },
  { label: '查看最近20行日志', cmd: 'logcat -d -v time | tail -n 20' }
];

function execCmd() {
  if (!inputCmd.value.trim()) return;
  const cmd = inputCmd.value.trim();
  inputCmd.value = '';

  let actualCmd = cmd;
  if (actualCmd.startsWith('adb shell ')) {
    actualCmd = actualCmd.substring('adb shell '.length).trim();
  } else if (actualCmd.startsWith('adb ')) {
    actualCmd = actualCmd.substring('adb '.length).trim();
  }

  let out = '';
  try {
    const res = bridge.call('executeCustomAdbCommand', actualCmd);
    out = (res || '(执行完成，无返回输出)');
  } catch (e) {
    out = `执行错误: ${e}`;
  }

  const now = new Date();
  const timeStr = now.toTimeString().split(' ')[0];
  const newBlock = `$ ${cmd}   [${timeStr}]\n${out.trim()}\n────────────────────────────────────────────────────────────\n`;
  
  outputText.value = newBlock + outputText.value;

  nextTick(() => {
    if (termContainer.value) {
      termContainer.value.scrollTop = 0;
    }
  });
}

function clearOutput() {
  updateTerminalPrompt();
}

function saveLog() {
  bridge.call('saveTerminalLog', outputText.value);
  showToast('终端日志已保存至 /sdcard/Download/');
}
</script>
