<template>
  <ModalWrapper 
    :show="store.modals.deepTools" 
    title="车机深度工具箱 & ADB 终端" 
    badge="特权调试"
    maxWidthClass="max-w-[1080px]"
    @close="closeModal('deepTools')"
  >
    <!-- 顶部 ADB 状态胶囊 (车规大卡片 · 动态连接动效 · 纯净车规文案) -->
    <div class="flex items-center justify-between bg-car-item border border-car-border rounded-3xl p-5 shadow-sm mb-5">
      <div class="flex items-center space-x-3.5">
        <!-- 连接中动态旋转动效 vs 就绪/未连接圆点 -->
        <div v-if="isCheckingAdb" class="w-8 h-8 rounded-full border-2 border-car-accent border-t-transparent animate-spin shrink-0"></div>
        <StatusDot v-else size="lg" :color="adbStatus.ready ? 'ok' : 'off'" :glow-px="adbStatus.ready ? 10 : 0" />
        
        <div class="flex flex-col">
          <div class="flex items-center space-x-2">
            <span class="text-[19px] font-black text-car-text">{{ adbStatus.title }}</span>
            <span 
              :class="[
                'px-2 py-0.5 text-[11.5px] font-black rounded-full border',
                isCheckingAdb ? 'bg-amber-500/15 border-amber-500/50 text-amber-400 animate-pulse' : (adbStatus.ready ? 'bg-emerald-500/15 border-emerald-500/50 text-emerald-400' : 'bg-car-card border-car-border text-car-sub')
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
          class="min-h-[56px] px-5 rounded-2xl border border-car-border bg-car-card text-car-text hover:border-car-border-light font-black text-[14.5px] shadow-sm transition-all flex items-center justify-center cursor-pointer disabled:opacity-50"
        >
          <span>{{ isCheckingAdb ? '连接中...' : '重新检测 ADB' }}</span>
        </button>
        <button 
          @click="openLogModalFromAdb"
          class="min-h-[56px] px-6 rounded-2xl border-2 border-car-border bg-car-card text-car-text hover:border-car-border-light font-black text-[15.5px] shadow-sm transition-all flex items-center justify-center cursor-pointer"
        >
          <span>系统日志导出 ➔</span>
        </button>
      </div>
    </div>

    <!-- 连接中过渡占位 (避免未就绪时误操作) -->
    <div v-if="isCheckingAdb" class="p-12 rounded-3xl bg-car-item border border-car-border flex flex-col items-center justify-center space-y-4 shadow-sm text-center">
      <div class="w-14 h-14 rounded-full border-4 border-car-accent border-t-transparent animate-spin shadow-[0_0_20px_var(--accent-gold)]"></div>
      <div class="text-[20px] font-black text-car-text">正在建立 ADB 5555 特权安全调试信道...</div>
      <div class="text-[14px] font-bold text-car-sub max-w-md">
        握手成功后将自动解锁全套控制台与常用指令；未就绪前已主动锁定功能按键，避免误操作打空。
      </div>
    </div>

    <!-- 独立终端控制台 (连接成功就绪后展示) -->
    <div v-else class="flex flex-col">
      <div class="flex items-center justify-between mb-3">
        <div class="flex items-center space-x-3">
          <span class="text-[21px] font-black text-car-text">ADB 交互控制台</span>
          <span v-if="hasPushedCmd" class="px-2 py-0.5 rounded-full bg-emerald-500/15 border border-emerald-500/50 text-emerald-400 text-[12px] font-black animate-pulse">
            已填入手机推送指令
          </span>
        </div>
        <div class="flex items-center">
          <button 
            @click="toggleKeyEventCapture"
            :disabled="!adbStatus.ready"
            :class="[
              'min-h-[50px] px-5 mr-3 rounded-xl border-2 font-black text-[15.5px] cursor-pointer shadow-sm flex items-center shrink-0 transition-all disabled:opacity-40 disabled:cursor-not-allowed',
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

      <!-- 命令行输入栏 (68px 车规大高度 · 历史分组与主操作清晰分隔) -->
      <div class="flex items-center space-x-3 mb-4">
        <input 
          v-model="inputCmd"
          type="text"
          placeholder="请输入 Shell / ADB 指令，如: pm list packages"
          :class="[
            'flex-1 h-[68px] border-2 border-car-border rounded-2xl px-5 font-mono text-[18px] outline-none focus:border-car-accent transition-all',
            store.isNight ? 'bg-[var(--term-bg)] text-emerald-400' : 'bg-car-item text-car-text'
          ]"
          @keyup.enter="execCmd"
          @keydown.up.prevent="historyPrev"
          @keydown.down.prevent="historyNext"
        />
        <!-- 历史指令调出按钮组: 上一条 (↑) 与 下一条 (↓) (8px 内聚间距) -->
        <div class="flex items-center space-x-2 shrink-0">
          <button 
            @click="historyPrev"
            title="调出上一条历史指令 (↑)"
            :disabled="cmdHistory.length === 0"
            class="min-h-[68px] h-[68px] min-w-[120px] px-4 bg-car-item border-2 border-car-border hover:border-car-accent rounded-2xl text-car-text font-black text-[15.5px] flex items-center justify-center space-x-1.5 cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed transition-all active:scale-95 shadow-sm"
          >
            <span class="text-[20px] font-black">↑</span>
            <span class="whitespace-nowrap">上一条</span>
          </button>
          <button 
            @click="historyNext"
            title="调出下一条历史指令 (↓)"
            :disabled="cmdHistory.length === 0 || historyIndex === -1"
            class="min-h-[68px] h-[68px] min-w-[120px] px-4 bg-car-item border-2 border-car-border hover:border-car-accent rounded-2xl text-car-text font-black text-[15.5px] flex items-center justify-center space-x-1.5 cursor-pointer disabled:opacity-30 disabled:cursor-not-allowed transition-all active:scale-95 shadow-sm"
          >
            <span class="text-[20px] font-black">↓</span>
            <span class="whitespace-nowrap">下一条</span>
          </button>
        </div>
        <!-- 执行指令主操作按钮 (独立分区留白 ml-2 · 加宽至 150px) -->
        <button 
          @click="execCmd"
          class="min-h-[68px] h-[68px] min-w-[150px] px-7 bg-car-item border-2 border-car-accent rounded-2xl text-car-text font-black text-[19px] cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 shadow-md shrink-0 active:scale-95 ml-2"
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
  title: '正在连接 ADB 5555 调试端口...',
  details: '正在握手 127.0.0.1:5555 本地特权通道，请稍候...',
  privilege: '连接中'
});
const isCheckingAdb = ref(false);
const hasPushedCmd = ref(false);

function checkAdbStatus(userTriggered = false) {
  if (store.deviceInfo.adb_master_switch === false) {
    adbStatus.value = {
      ready: false,
      title: 'ADB 服务已停用',
      details: 'ADB 总开关已关闭 (防弹窗保护中)',
      privilege: '已关闭'
    };
    isCheckingAdb.value = false;
    updateTerminalPrompt();
    if (userTriggered) showToast('ADB 总开关已关闭，该功能无法使用');
    return;
  }
  if (isCheckingAdb.value) return;
  isCheckingAdb.value = true;
  adbStatus.value = {
    ready: false,
    title: '正在连接 ADB 5555 调试端口...',
    details: '正在握手 127.0.0.1:5555 本地特权通道，请稍候...',
    privilege: '连接中'
  };
  if (userTriggered) showToast('正在重新探测车机 5555 调试端口...');
  
  // 使用 setTimeout 异步执行，确保弹窗能够以 60fps 瞬间展开，不被底层 Binder 同步阻塞卡顿
  setTimeout(() => {
    try {
      const raw = bridge.call('probeAdbStatus');
      if (raw) {
        const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
        adbStatus.value = {
          ready: !!data.ready,
          title: data.title || (data.ready ? 'ADB 特权终端已就绪' : 'ADB 端口未连接'),
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
  }, 60);
}

// 接收手机闪传推送 ADB 命令接口 (支持自动执行参数 autoExec)
window.onAdbCommandPushedFromPhone = (cmd, autoExec = false) => {
  if (!cmd) return;
  if (!store.modals.deepTools) {
    openModal('deepTools');
  }
  inputCmd.value = cmd;
  hasPushedCmd.value = true;
  if (autoExec) {
    showToast('已接收手机推送指令并立即执行');
    nextTick(() => {
      execCmd();
    });
  } else {
    showToast('已自动填入手机推送的 ADB 指令，核验后点击执行即可');
  }
};

watch(() => store.modals.deepTools, (show) => {
  if (show) {
    if (store.pushedAdbCmd) {
      inputCmd.value = store.pushedAdbCmd;
      hasPushedCmd.value = true;
      const shouldAuto = !!store.pushedAdbAutoExec;
      store.pushedAdbCmd = '';
      store.pushedAdbAutoExec = false;
      if (shouldAuto) {
        showToast('已接收手机指令并立即执行');
        nextTick(() => {
          execCmd();
        });
      } else {
        showToast('已填入手机推送的 ADB 指令');
      }
    }
    checkAdbStatus(false);
  }
});

const isCapturingKeys = ref(false);

function toggleKeyEventCapture() {
  if (isCapturingKeys.value) {
    stopKeyEventCapture();
  } else {
    startKeyEventCapture();
  }
}

function startKeyEventCapture() {
  if (store.deviceInfo.adb_master_switch === false) {
    showToast('ADB 总开关已关闭，该功能无法使用');
    return;
  }
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

// ADB 终端执行历史记录 (持久化至 localStorage，最大保留50条)
const cmdHistory = ref([]);
const historyIndex = ref(-1);

try {
  const saved = localStorage.getItem('geely_adb_cmd_history');
  if (saved) {
    const list = JSON.parse(saved);
    if (Array.isArray(list)) {
      cmdHistory.value = list;
    }
  }
} catch (e) {}

function pushHistory(cmd) {
  if (!cmd || !cmd.trim()) return;
  const trimmed = cmd.trim();
  // 避免与最近一条完全相同
  if (cmdHistory.value.length === 0 || cmdHistory.value[cmdHistory.value.length - 1] !== trimmed) {
    cmdHistory.value.push(trimmed);
    if (cmdHistory.value.length > 50) {
      cmdHistory.value.shift();
    }
    try {
      localStorage.setItem('geely_adb_cmd_history', JSON.stringify(cmdHistory.value));
    } catch (e) {}
  }
  historyIndex.value = -1;
}

function historyPrev() {
  if (cmdHistory.value.length === 0) {
    showToast('暂无历史指令记录');
    return;
  }
  if (historyIndex.value === -1) {
    historyIndex.value = cmdHistory.value.length - 1;
  } else if (historyIndex.value > 0) {
    historyIndex.value--;
  }
  inputCmd.value = cmdHistory.value[historyIndex.value];
}

function historyNext() {
  if (cmdHistory.value.length === 0 || historyIndex.value === -1) {
    return;
  }
  if (historyIndex.value < cmdHistory.value.length - 1) {
    historyIndex.value++;
    inputCmd.value = cmdHistory.value[historyIndex.value];
  } else {
    historyIndex.value = -1;
    inputCmd.value = '';
  }
}

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
  if (store.deviceInfo.adb_master_switch === false) {
    showToast('ADB 总开关已关闭，该功能无法使用');
    return;
  }
  if (!inputCmd.value.trim()) return;
  const cmd = inputCmd.value.trim();
  pushHistory(cmd);
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
