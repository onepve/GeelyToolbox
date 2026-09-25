<template>
  <ModalWrapper 
    :show="store.modals.deepTools" 
    title="车机深度工具箱 & ADB 终端" 
    badge="特权维护"
    maxWidthClass="max-w-[1080px]"
    @close="closeModal('deepTools')"
  >
    <!-- 顶部 ADB 状态胶囊 (车规大卡片 · 真实动态探测无假数据) -->
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

    <!-- 核心组件推荐冻结列表 (大卡片 + 强防呆置灰保护) -->
    <div class="mb-5">
      <div class="flex items-center justify-between mb-3">
        <div class="text-[21px] font-black text-car-text">原厂预装组件推荐冻结 / 解冻 (二次确认保护)</div>
        <button 
          @click="openAllAppsFromAdb"
          class="text-[14.5px] font-black text-car-accent hover:text-amber-400 cursor-pointer flex items-center space-x-1"
        >
          <span>已安装应用管理 ➔</span>
        </button>
      </div>
      <div class="grid grid-cols-4 gap-3.5">
        <!-- 1. 吉利应用商店 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col justify-between shadow-sm">
          <div class="flex flex-col mb-3">
            <span class="text-[17px] font-black text-car-text mb-0.5">吉利应用商店</span>
            <span class="text-[12px] text-car-sub font-mono truncate">com.ecarx.appstore</span>
          </div>
          <button 
            @click="confirmToggleFreeze('com.ecarx.appstore', '吉利应用商店')"
            :disabled="!!freezingPackages['com.ecarx.appstore']"
            :class="[
              'min-h-[58px] px-2 rounded-xl border-2 font-black text-[15px] transition-all',
              freezingPackages['com.ecarx.appstore']
                ? 'bg-car-card border-car-border text-car-sub opacity-50 cursor-not-allowed'
                : (packageStates['com.ecarx.appstore'] 
                    ? 'bg-car-card border-emerald-500/60 text-emerald-400 cursor-pointer' 
                    : 'bg-car-card border-amber-500/70 text-car-text hover:border-amber-400 cursor-pointer')
            ]"
          >
            {{ freezingPackages['com.ecarx.appstore'] 
                ? (packageStates['com.ecarx.appstore'] ? '正在解冻中...' : '正在冻结中...') 
                : (packageStates['com.ecarx.appstore'] ? '已安全冻结 (白名单锁定)' : '运行中 · 点击安全冻结') }}
          </button>
        </div>

        <!-- 2. 原厂多媒体伴听 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col justify-between shadow-sm">
          <div class="flex flex-col mb-3">
            <span class="text-[17px] font-black text-car-text mb-0.5">原厂多媒体伴听</span>
            <span class="text-[12px] text-car-sub font-mono truncate">com.ecarx.multimedia</span>
          </div>
          <button 
            @click="confirmToggleFreeze('com.ecarx.multimedia', '原厂多媒体伴听')"
            :disabled="!!freezingPackages['com.ecarx.multimedia']"
            :class="[
              'min-h-[58px] px-2 rounded-xl border-2 font-black text-[15px] transition-all',
              freezingPackages['com.ecarx.multimedia']
                ? 'bg-car-card border-car-border text-car-sub opacity-50 cursor-not-allowed'
                : (packageStates['com.ecarx.multimedia'] 
                    ? 'bg-car-card border-emerald-500/60 text-emerald-400 cursor-pointer' 
                    : 'bg-car-card border-amber-500/70 text-car-text hover:border-amber-400 cursor-pointer')
            ]"
          >
            {{ freezingPackages['com.ecarx.multimedia'] 
                ? (packageStates['com.ecarx.multimedia'] ? '正在解冻中...' : '正在冻结中...') 
                : (packageStates['com.ecarx.multimedia'] ? '已安全冻结 (方控免误弹)' : '运行中 · 点击安全冻结') }}
          </button>
        </div>

        <!-- 3. 原厂云听车机版 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col justify-between shadow-sm">
          <div class="flex flex-col mb-3">
            <span class="text-[17px] font-black text-car-text mb-0.5">原厂云听车机版</span>
            <span class="text-[12px] text-car-sub font-mono truncate">com.edog.car</span>
          </div>
          <button 
            @click="confirmToggleFreeze('com.edog.car', '原厂云听车机版')"
            :disabled="!!freezingPackages['com.edog.car']"
            :class="[
              'min-h-[58px] px-2 rounded-xl border-2 font-black text-[15px] transition-all',
              freezingPackages['com.edog.car']
                ? 'bg-car-card border-car-border text-car-sub opacity-50 cursor-not-allowed'
                : (packageStates['com.edog.car'] 
                    ? 'bg-car-card border-emerald-500/60 text-emerald-400 cursor-pointer' 
                    : 'bg-car-card border-amber-500/70 text-car-text hover:border-amber-400 cursor-pointer')
            ]"
          >
            {{ freezingPackages['com.edog.car'] 
                ? (packageStates['com.edog.car'] ? '正在解冻中...' : '正在冻结中...') 
                : (packageStates['com.edog.car'] ? '已安全冻结 (省运存36M)' : '运行中 · 点击安全冻结') }}
          </button>
        </div>

        <!-- 4. 火山车娱 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col justify-between shadow-sm">
          <div class="flex flex-col mb-3">
            <span class="text-[17px] font-black text-car-text mb-0.5">火山车娱</span>
            <span class="text-[12px] text-car-sub font-mono truncate">com.bytedance.byteautoservice</span>
          </div>
          <button 
            @click="confirmToggleFreeze('com.bytedance.byteautoservice', '火山车娱')"
            :disabled="!!freezingPackages['com.bytedance.byteautoservice']"
            :class="[
              'min-h-[58px] px-2 rounded-xl border-2 font-black text-[15px] transition-all',
              freezingPackages['com.bytedance.byteautoservice']
                ? 'bg-car-card border-car-border text-car-sub opacity-50 cursor-not-allowed'
                : (packageStates['com.bytedance.byteautoservice'] 
                    ? 'bg-car-card border-emerald-500/60 text-emerald-400 cursor-pointer' 
                    : 'bg-car-card border-amber-500/70 text-car-text hover:border-amber-400 cursor-pointer')
            ]"
          >
            {{ freezingPackages['com.bytedance.byteautoservice'] 
                ? (packageStates['com.bytedance.byteautoservice'] ? '正在解冻中...' : '正在冻结中...') 
                : (packageStates['com.bytedance.byteautoservice'] ? '已安全冻结 (免后台偷跑)' : '运行中 · 点击安全冻结') }}
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
          'min-h-[220px] max-h-[300px] overflow-y-auto border-2 border-car-border rounded-2xl p-5 font-mono text-[15px] leading-relaxed select-text whitespace-pre-wrap shadow-inner transition-all',
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
import { openAppstoreFlow } from '../../utils/appstoreFreeze';

// 真实 ADB 状态对象 (绝不硬编码假数据)
const adbStatus = ref({
  ready: false,
  title: 'ADB 端口检测中...',
  details: '正在探测本地 127.0.0.1:5555 与特权特权...',
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

// 预装组件状态与强防呆集合
const packageStates = ref({
  'com.ecarx.appstore': false,
  'com.ecarx.multimedia': false,
  'com.edog.car': false,
  'com.bytedance.byteautoservice': false
});
const freezingPackages = ref({}); // 记录正在执行防呆变灰的包名集合

const isDumping = ref(false);
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

// 刷新预装应用冻结状态
function refreshPackageStates() {
  try {
    packageStates.value['com.ecarx.appstore'] = !!bridge.call('isPackageFrozen', 'com.ecarx.appstore');
    packageStates.value['com.ecarx.multimedia'] = !!bridge.call('isPackageFrozen', 'com.ecarx.multimedia');
    packageStates.value['com.edog.car'] = !!bridge.call('isPackageFrozen', 'com.edog.car');
    packageStates.value['com.bytedance.byteautoservice'] = !!bridge.call('isPackageFrozen', 'com.bytedance.byteautoservice');
  } catch (e) {}
}

// 导出至 window 供 Java 回调
window.refreshPackageStates = refreshPackageStates;
window.onPackageFreezeFinished = (pkg, success, errorMsg) => {
  if (pkg && freezingPackages.value[pkg]) {
    freezingPackages.value[pkg] = false;
  }
  refreshPackageStates();
};

watch(() => store.modals.deepTools, (show) => {
  if (show) {
    checkAdbStatus(false);
    refreshPackageStates();
  } else if (isCapturingKeys.value) {
    // 离开弹窗: 自动终止按键抓取, 防后台驻留
    stopKeyEventCapture();
  }
});

onMounted(() => {
  if (store.modals.deepTools) {
    checkAdbStatus(false);
    refreshPackageStates();
  }
});

function openOtaCapture() {
  openModal('otaCapture');
}

function openAllAppsFromAdb() {
  closeModal('deepTools');
  store.modals.allApps = true;
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
      termContainer.value.scrollTop = 0; // 始终保持顶部查看最新输出
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

function confirmToggleFreeze(pkg, pkgName) {
  // 防呆：如果正在处理中，绝不重复触发
  if (freezingPackages.value[pkg]) {
    showToast('该应用正在执行操作中，请勿重复点击');
    return;
  }

  const isFrozen = !!packageStates.value[pkg];
  const actionText = isFrozen ? '解冻恢复' : '安全冻结';
  
  let desc = '';
  let tip = '';
  if (pkg === 'com.ecarx.appstore') {
    openAppstoreFlow(() => {
      refreshPackageStates();
    });
    return;
  } else if (pkg === 'ecarx.upgrade') {
    desc = isFrozen
      ? `即将解冻【${pkgName}】(${pkg})。解冻后车机将恢复接收吉利官方 OTA 系统固件推送。`
      : `即将安全冻结【${pkgName}】(${pkg})。冻结后将彻底静默官方 OTA 自动静默下载与强制弹窗更新，防止系统升级后被覆盖丢失已装软件。`;
    tip = isFrozen ? '解冻后建议留意系统更新提示。' : '冻结不影响车机日常联网、导航与多媒体功能。';
  } else if (pkg === 'com.ecarx.multimedia') {
    desc = isFrozen
      ? `即将解冻【${pkgName}】(${pkg})。解冻后原车伴听与多媒体中心将恢复开机自启。`
      : `即将安全冻结【${pkgName}】(${pkg})。冻结后将彻底屏蔽原厂伴听开机自启与广告电台抢占，方向盘切歌与方控将纯净交给第三方音乐。`;
    tip = isFrozen ? '解冻后原厂多媒体将重新接管媒体按键。' : '冻结原厂多媒体不影响车载蓝牙电话接打与配对。';
  } else {
    desc = `即将执行【${actionText}】组件【${pkgName}】(${pkg})，是否确认继续？`;
    tip = '请确保了解该组件的系统功能，非必要请勿随意变更。';
  }

  openModal('confirm', {
    title: `【高危操作确认】${actionText} ${pkgName}`,
    desc: desc,
    tip: tip,
    isDanger: !isFrozen,
    confirmText: `确认${actionText}`,
    cancelText: '取消',
    onConfirm: () => {
      toggleFreeze(pkg);
    }
  });
}

function toggleFreeze(pkg) {
  // 立即进入防呆置灰锁定
  freezingPackages.value[pkg] = true;
  showToast('正在下发底层 ADB 指令执行操作，按钮已置灰防误触...');

  try {
    bridge.call('togglePackageFreeze', pkg);
  } catch (e) {
    freezingPackages.value[pkg] = false;
    showToast('下发指令异常: ' + e);
    return;
  }

  // 兜底超时安全机制：如果 6 秒内底层没有回调完成，自动释放置灰并刷新状态，防止死锁
  setTimeout(() => {
    if (freezingPackages.value[pkg]) {
      freezingPackages.value[pkg] = false;
      refreshPackageStates();
    }
  }, 6000);
}
</script>
