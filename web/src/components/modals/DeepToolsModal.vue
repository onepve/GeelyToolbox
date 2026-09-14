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
        :disabled="isDumping"
        :class="[
          'min-h-[66px] px-8 rounded-2xl border-2 font-black text-[18.5px] shadow-sm transition-all flex items-center justify-center',
          isDumping 
            ? 'bg-car-card border-car-accent text-car-accent opacity-80 cursor-wait' 
            : 'bg-car-card border-car-border text-car-text hover:border-car-border-light cursor-pointer'
        ]"
      >
        <span>{{ isDumping ? '⏳ 正在采集打包中...' : '采集车机全量日志 (ZIP)' }}</span>
      </button>
    </div>

    <!-- 核心组件推荐冻结列表 (大卡片 + 66px 按钮) -->
    <div class="mb-5">
      <div class="flex items-center justify-between mb-3">
        <div class="text-[21px] font-black text-car-text">原厂预装组件推荐冻结 / 解冻 (二次确认保护)</div>
        <button 
          @click="openAllAppsFromAdb"
          class="text-[14.5px] font-black text-car-accent hover:text-amber-400 cursor-pointer flex items-center space-x-1"
        >
          <span>查看整车所有已安装软件清单 (卸载/冻结/清数据) ➔</span>
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
            :class="[
              'min-h-[58px] px-2 rounded-xl border-2 font-black text-[15px] cursor-pointer transition-all',
              packageStates['com.ecarx.appstore'] 
                ? 'bg-car-card border-emerald-500/60 text-emerald-400' 
                : 'bg-car-card border-amber-500/70 text-car-text hover:border-amber-400'
            ]"
          >
            {{ packageStates['com.ecarx.appstore'] ? '已安全冻结 (白名单锁定)' : '运行中 · 点击安全冻结' }}
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
            :class="[
              'min-h-[58px] px-2 rounded-xl border-2 font-black text-[15px] cursor-pointer transition-all',
              packageStates['com.ecarx.multimedia'] 
                ? 'bg-car-card border-emerald-500/60 text-emerald-400' 
                : 'bg-car-card border-amber-500/70 text-car-text hover:border-amber-400'
            ]"
          >
            {{ packageStates['com.ecarx.multimedia'] ? '已安全冻结 (方控免误弹)' : '运行中 · 点击安全冻结' }}
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
            :class="[
              'min-h-[58px] px-2 rounded-xl border-2 font-black text-[15px] cursor-pointer transition-all',
              packageStates['com.edog.car'] 
                ? 'bg-car-card border-emerald-500/60 text-emerald-400' 
                : 'bg-car-card border-amber-500/70 text-car-text hover:border-amber-400'
            ]"
          >
            {{ packageStates['com.edog.car'] ? '已安全冻结 (省运存36M)' : '运行中 · 点击安全冻结' }}
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
            :class="[
              'min-h-[58px] px-2 rounded-xl border-2 font-black text-[15px] cursor-pointer transition-all',
              packageStates['com.bytedance.byteautoservice'] 
                ? 'bg-car-card border-emerald-500/60 text-emerald-400' 
                : 'bg-car-card border-amber-500/70 text-car-text hover:border-amber-400'
            ]"
          >
            {{ packageStates['com.bytedance.byteautoservice'] ? '已安全冻结 (免后台偷跑)' : '运行中 · 点击安全冻结' }}
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
          class="min-h-[52px] px-5 py-2.5 mr-2.5 mb-2.5 rounded-2xl bg-car-item border border-car-border text-car-text hover:text-car-text text-[15.5px] font-mono cursor-pointer hover:border-car-accent font-bold"
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
          :class="[
            'flex-1 h-[68px] border-2 border-car-border rounded-2xl px-5 font-mono text-[18px] outline-none focus:border-car-accent mr-3 transition-all',
            store.isNight ? 'bg-[#0A0D12] text-emerald-400' : 'bg-car-item text-car-text'
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
          store.isNight ? 'bg-[#0A0D12] text-emerald-400' : 'bg-car-item text-emerald-700'
        ]"
      >{{ outputText }}</pre>
    </div>
  </ModalWrapper>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, openModal, showToast } from '../../store';

const packageStates = ref({
  'com.ecarx.appstore': false,
  'com.ecarx.multimedia': false,
  'com.edog.car': false,
  'com.bytedance.byteautoservice': false
});

const isDumping = ref(false);

function refreshPackageStates() {
  setTimeout(() => {
    try {
      packageStates.value['com.ecarx.appstore'] = !!bridge.call('isPackageFrozen', 'com.ecarx.appstore');
      packageStates.value['com.ecarx.multimedia'] = !!bridge.call('isPackageFrozen', 'com.ecarx.multimedia');
      packageStates.value['com.edog.car'] = !!bridge.call('isPackageFrozen', 'com.edog.car');
      packageStates.value['com.bytedance.byteautoservice'] = !!bridge.call('isPackageFrozen', 'com.bytedance.byteautoservice');
    } catch (e) {}
  }, 100);
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

function openAllAppsFromAdb() {
  closeModal('deepTools');
  store.modals.allApps = true;
}

const termContainer = ref(null);
const inputCmd = ref('');
const INITIAL_PROMPT = '[ADB Client 127.0.0.1:5555 就绪 · 最新输出置顶显示]\n$ ';
const outputText = ref(INITIAL_PROMPT);

const quickCmds = [
  'getprop ro.product.model',
  'pm list packages -3',
  'dumpsys meminfo',
  'logcat -d -v time | tail -n 20'
];

function execCmd() {
  if (!inputCmd.value.trim()) return;
  const cmd = inputCmd.value.trim();
  inputCmd.value = '';

  let out = '';
  try {
    const res = bridge.call('executeCustomAdbCommand', cmd);
    out = (res || '(执行完成，无返回输出)');
  } catch (e) {
    out = `执行错误: ${e}`;
  }

  const now = new Date();
  const timeStr = now.toTimeString().split(' ')[0];
  const newBlock = `$ ${cmd}   [${timeStr}]\n${out.trim()}\n────────────────────────────────────────────────────────────\n`;
  
  if (outputText.value === INITIAL_PROMPT) {
    outputText.value = newBlock + INITIAL_PROMPT;
  } else {
    outputText.value = newBlock + outputText.value;
  }

  nextTick(() => {
    if (termContainer.value) {
      termContainer.value.scrollTop = 0; // 始终保持顶部查看最新输出
    }
  });
}

function clearOutput() {
  outputText.value = INITIAL_PROMPT;
}

function saveLog() {
  bridge.call('saveTerminalLog', outputText.value);
  showToast('终端日志已保存至 /sdcard/Download/');
}

function confirmToggleFreeze(pkg, pkgName) {
  const isFrozen = !!packageStates.value[pkg];
  const actionText = isFrozen ? '解冻恢复' : '安全冻结';
  
  let desc = '';
  let tip = '';
  if (pkg === 'com.ecarx.appstore') {
    desc = isFrozen 
      ? `即将解冻【${pkgName}】(${pkg})。解冻后应用商店将恢复开机自启，可能会在每次开机时强制重置车机安装白名单策略。`
      : `即将安全冻结【${pkgName}】(${pkg})。冻结后可彻底杜绝车机每次开机自动重置白名单，并释放约 100MB 运行内存，保障第三方地图与应用稳定直装。`;
    tip = isFrozen ? '如需安装第三方软件，建议保持应用商店处于冻结状态。' : '如需使用官方应用商店下载原厂应用，可随时在此解冻。';
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
  bridge.call('togglePackageFreeze', pkg);
  showToast('正在下发底层 ADB 指令执行操作...');
  setTimeout(() => {
    refreshPackageStates();
  }, 600);
  setTimeout(() => {
    refreshPackageStates();
  }, 1800);
}

function dumpLogcat() {
  if (isDumping.value) return;
  isDumping.value = true;
  showToast('正在后台采集并打包最近日志，请稍候...');
  try {
    bridge.call('dumpSystemLogcat');
  } catch (e) {}
  setTimeout(() => {
    isDumping.value = false;
  }, 4000);
}

// 接收 Java 异步导出完成回调
window.onLogcatDumpFinished = (res) => {
  isDumping.value = false;
};
</script>
