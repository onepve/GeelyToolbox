<template>
  <div class="bg-car-card border-2 border-car-border rounded-3xl p-5 min-h-[106px] shadow-xl flex items-center justify-between transition-all">
    <div class="w-[52%] max-w-[52%] flex flex-col space-y-1.5 shrink-0">
      <div class="flex items-center space-x-3">
        <span :class="['w-3.5 h-3.5 rounded-full shadow-md shrink-0', enabled ? 'bg-emerald-500 shadow-[0_0_10px_#10B981]' : 'bg-slate-400']"></span>
        <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">上车预设驾驶模式 (测试)</span>
      </div>
      <div class="text-[14.5px] text-car-sub font-bold leading-normal">
        点火成功且主驾就位后延时下发预设模式切换，保持原厂默认智能模式起步 (默认关闭)。
      </div>
    </div>
    <div class="shrink-0 w-[230px]">
      <button
        @click="toggleEnabled"
        :class="[
          'w-full h-[74px] px-4 py-2 rounded-2xl border-2 cursor-pointer transition-all shadow-md flex flex-col items-center justify-center text-center',
          enabled
            ? 'bg-car-item border-car-accent'
            : 'bg-car-card border-car-border hover:border-car-border-light'
        ]"
      >
        <span class="text-[18.5px] font-black text-car-text tracking-wide whitespace-nowrap">
          {{ enabled ? '预设已启用' : '预设已关闭' }}
        </span>
        <span :class="['text-[12.5px] font-bold mt-1 whitespace-nowrap', enabled ? 'text-car-accent' : 'text-car-sub']">
          {{ enabled ? modeName(target) + ' · 点击关闭' : '点击启用预设' }}
        </span>
      </button>
    </div>
  </div>

  <!-- 模式选择与实测区 (启用后展开) -->
  <div v-if="enabled" class="rounded-3xl border-2 border-car-border bg-car-card p-6 shadow-xl flex flex-col space-y-5">
    <div class="flex items-center justify-between">
      <div class="flex items-center space-x-3">
        <span class="w-3 h-3 rounded-full bg-car-accent shadow-[0_0_8px_var(--accent-gold)]"></span>
        <span class="text-[20px] font-black text-car-text whitespace-nowrap">目标模式与实车软切验证 (测试)</span>
        <button
          @click.stop="showHelp"
          class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-accent hover:border-car-accent font-black text-[18px] flex items-center justify-center cursor-pointer shadow-sm transition-transform active:scale-95 shrink-0"
        >
          ?
        </button>
      </div>
      <span class="px-3.5 py-1.5 rounded-full text-[14px] font-black bg-car-item border border-car-border text-car-accent whitespace-nowrap shrink-0">
        当前预设: {{ modeName(target) }}
      </span>
    </div>

    <!-- 五档模式单选 (default 在首位保持原厂) -->
    <div class="grid grid-cols-5 gap-3">
      <button
        v-for="mode in modes"
        :key="mode.value"
        @click="selectMode(mode.value)"
        :class="[
          'h-[52px] rounded-xl font-black text-[15.5px] cursor-pointer transition-all border-2 whitespace-nowrap flex items-center justify-center',
          target === mode.value
            ? 'bg-car-item border-car-accent text-car-text shadow-md'
            : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
        ]"
      >
        {{ mode.label }}
      </button>
    </div>

    <!-- 实测按钮 -->
    <button
      @click="confirmTest"
      class="h-[52px] px-6 rounded-2xl bg-car-item border-2 border-car-accent hover:border-car-accent text-car-accent font-black text-[16px] cursor-pointer shadow-md transition-all w-fit whitespace-nowrap"
    >
      手动实测切换 {{ modeName(target) }} ➔
    </button>

    <!-- 结果回显 (真实透传 backend JSON, 绝不伪造) -->
    <div v-if="lastResult" :class="['p-4 rounded-2xl border-2 flex flex-col space-y-1', resultBorderClass]">
      <div class="flex items-center space-x-2">
        <span :class="['w-2.5 h-2.5 rounded-full shrink-0', resultDotClass]"></span>
        <span class="text-[15.5px] font-black" :class="resultTextClass">实测返回: {{ resultLabel }}</span>
      </div>
      <div class="text-[14px] font-bold text-car-sub leading-relaxed break-all">{{ lastResult.message }}</div>
    </div>

    <!-- 车辆状态 (backend getExperimentalVehicleStatus) -->
    <div v-if="vehicleStatusText" class="p-4 rounded-2xl border border-car-border bg-car-item flex items-center justify-between">
      <span class="text-[14.5px] font-bold text-car-sub whitespace-nowrap">实验通道车辆状态</span>
      <span class="text-[14.5px] font-black text-car-text truncate ml-4">{{ vehicleStatusText }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { store, bridge, showToast, openModal } from '../store';

const modes = [
  { value: 'default', label: '保持原厂' },
  { value: 'smart', label: '智能' },
  { value: 'comfort', label: '舒适' },
  { value: 'eco', label: '经济' },
  { value: 'sport', label: '运动' }
];

const lastResult = ref(null);
const vehicleStatusText = ref('');
let statusTimer = null;

const enabled = computed(() => !!store.vehicleAuto.vehicle_preset_drive_mode_enabled);
const target = computed(() => store.vehicleAuto.vehicle_preset_drive_mode_target || 'default');

function modeName(value) {
  const found = modes.find(m => m.value === value);
  return found ? found.label : '保持原厂';
}

function toggleEnabled() {
  const next = !store.vehicleAuto.vehicle_preset_drive_mode_enabled;
  store.vehicleAuto.vehicle_preset_drive_mode_enabled = next;
  bridge.call('setVehicleAutomationSetting', 'vehicle_preset_drive_mode_enabled', next);
  showToast(next ? '上车预设驾驶模式已启用 (测试)' : '上车预设驾驶模式已关闭');
  if (next) {
    fetchVehicleStatus();
  }
}

function selectMode(value) {
  store.vehicleAuto.vehicle_preset_drive_mode_target = value;
  // target 为字符串，须走 setVehicleAutomationStringSetting 通道 (Java 端 putString)
  bridge.call('setVehicleAutomationStringSetting', 'vehicle_preset_drive_mode_target', value);
  showToast('预设目标模式: ' + modeName(value));
}

function confirmTest() {
  openModal('confirm', {
    title: '确认执行实测切换',
    desc: '请确认车辆已停稳并挂入 P 挡，发动机怠速或点火就绪状态，再执行手动实测切换。\n\n实测仅向下发一次切换请求，结果以仪表盘模式图标是否跟随跃变为准。',
    showCancel: true,
    confirmText: '已停稳 · 立即实测',
    cancelText: '取消',
    onConfirm: () => {
      runTest();
    }
  });
}

function runTest() {
  if (target.value === 'default') {
    lastResult.value = { status: 'unchanged', message: '保持原厂不下发切换指令。请选择具体模式后实测。' };
    return;
  }
  const raw = bridge.call('testSwitchDriveMode', target.value);
  if (typeof raw === 'boolean') {
    lastResult.value = { status: raw ? 'accepted' : 'unsupported', message: raw ? '当前原生接口仅确认请求已受理，不提供切换结果；请观察实车仪表，不代表已切换成功。' : '原生接口未接受请求，请核对车型与固件支持情况。' };
    return;
  }
  // backend 约定返回 JSON 字符串 {status, message}；未知/缺失如实显示，绝不伪造成功
  if (raw === null || raw === undefined || raw === '') {
    lastResult.value = { status: 'unavailable', message: '当前环境未提供 testSwitchDriveMode 通道 (桥接方法不存在或未返回)，无法实测。' };
    return;
  }
  let parsed = null;
  try {
    parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
  } catch (e) {
    parsed = null;
  }
  if (parsed && typeof parsed === 'object' && parsed.status) {
    lastResult.value = { status: String(parsed.status), message: String(parsed.message || '(backend 未附带回执消息)') };
  } else {
    lastResult.value = { status: 'unparseable', message: '返回内容无法解析为 {status, message}: ' + String(raw) };
  }
}

const resultLabel = computed(() => {
  const s = lastResult.value ? lastResult.value.status : '';
  if (s === 'sent') return '已请求下发 · 待仪表确认';
  if (s === 'unsupported') return '车型或固件不支持';
  if (s === 'blocked') return '安全条件未满足 · 已拦截';
  if (s === 'error') return '底层执行出错';
  if (s === 'success') return '原生回执成功 · 请核对仪表';
  if (s === 'accepted') return '请求已受理 · 结果未确认';
  if (s === 'unchanged') return '保持原厂 · 未执行切换';
  return '通道未就绪 / 返回异常';
});

const resultBorderClass = computed(() => {
  const s = lastResult.value ? lastResult.value.status : '';
  if (s === 'sent') return 'border-sky-500/50 bg-sky-500/10';
  if (s === 'unsupported' || s === 'blocked' || s === 'error' || s === 'unavailable' || s === 'unparseable') return 'border-red-500/50 bg-red-500/10';
  if (s === 'success') return 'border-emerald-500/50 bg-emerald-500/10';
  return 'border-car-border bg-car-item';
});

const resultDotClass = computed(() => {
  const s = lastResult.value ? lastResult.value.status : '';
  if (s === 'sent') return 'bg-sky-400 shadow-[0_0_6px_#38BDF8]';
  if (s === 'unsupported' || s === 'blocked' || s === 'error' || s === 'unavailable' || s === 'unparseable') return 'bg-red-400 shadow-[0_0_6px_#F87171]';
  if (s === 'success') return 'bg-emerald-400 shadow-[0_0_6px_#34D399]';
  return 'bg-car-sub';
});

const resultTextClass = 'text-car-text';

function fetchVehicleStatus() {
  const raw = bridge.call('getExperimentalVehicleStatus');
  if (raw === null || raw === undefined || raw === '') {
    vehicleStatusText.value = '通道未提供 (getExperimentalVehicleStatus)';
    return;
  }
  try {
    const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
    if (data && typeof data === 'object') {
      const parts = [];
      if (typeof data.gear === 'number') parts.push('挡位 ' + data.gear);
      if (typeof data.speed_kmh === 'number') parts.push('车速 ' + data.speed_kmh + ' km/h');
      if (typeof data.engine_running === 'boolean') parts.push(data.engine_running ? '已点火' : '未点火');
      vehicleStatusText.value = parts.length > 0 ? parts.join(' · ') : JSON.stringify(data);
    } else {
      vehicleStatusText.value = String(data);
    }
  } catch (e) {
    vehicleStatusText.value = '状态解析失败';
  }
}

function showHelp() {
  openModal('confirm', {
    title: '【测试功能指南】上车预设驾驶模式',
    desc: '1. 原理：点火成功且主驾就位后延时约 2.5 秒 (避开马达压降与仪表自检) 下发预设模式切换请求，默认保持原厂智能模式。\n\n2. 实测方法：停稳挂 P 挡后点击手动实测，观察仪表盘模式图标与转速表红区是否跟随跃变，即可确认底层网关是否放行软切换。\n\n3. 真实回执：实测结果仅显示底层真实返回 (已请求待仪表确认 / 不支持 / 被拦截 / 出错)，工具箱绝不伪造切换成功。',
    tip: '测试阶段功能，默认关闭保持原厂；待实车验证通过后转正。',
    showCancel: false,
    confirmText: '我知道了'
  });
}

onMounted(() => {
  if (enabled.value) fetchVehicleStatus();
  statusTimer = setInterval(() => {
    if (enabled.value) fetchVehicleStatus();
  }, 3000);
});
onUnmounted(() => {
  if (statusTimer) clearInterval(statusTimer);
});
</script>
