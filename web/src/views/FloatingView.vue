<template>
  <div class="flex flex-col space-y-6">
    <!-- 1. 全局桌面迷你悬浮胶囊 -->
    <FeatureCard 
      title="1. 全局桌面迷你悬浮胶囊 (实时状态与动态暗码)"
      desc="在车机桌面或其他应用上层常驻迷你流光胶囊，支持手指自由拖拽吸附，实时显示软件状态或今日动态工程暗码。"
      helpTitle="【功能指南】桌面悬浮微胶囊运行机制"
      helpText="1. 浮窗交互：&#10;在车机桌面或其他全屏应用上层常驻微型流光胶囊，支持手指自由拖拽并自动贴边吸附，点击胶囊秒开控制台。&#10;&#10;2. 三大显示模式：&#10;• 显示应用名：当前最上层前台运行的软件名称；&#10;• 显示动态暗码：直接在胶囊内展示今日工程模式暗码；&#10;• 显示电瓶电压：实时监测整车 12V 蓄电池电压与发电机发电状态。"
      helpTip="建议将显示模式设为「显示电瓶电压」，可时刻掌握爱车电瓶健康状态，防亏电趴窝。"
    >
      <div class="grid grid-cols-2 gap-4">
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex-1 min-w-0 pr-5 flex flex-col justify-center">
            <div class="text-[19px] font-black text-car-text whitespace-nowrap">悬浮微胶囊总开关</div>
            <div class="text-[13.5px] text-car-sub font-bold mt-1 leading-normal">
              在桌面及全屏应用上层常驻微型流光胶囊，支持自由拖拽吸附，点击秒开工具箱。
            </div>
          </div>
          <button 
            @click="toggleFloatingWindow"
            :class="[
              'w-[170px] h-[74px] px-3 py-2 rounded-2xl border-2 cursor-pointer transition-all shadow-md flex flex-col items-center justify-center text-center shrink-0',
              store.deviceInfo.floating_enabled
                ? 'bg-car-item border-car-accent'
                : 'bg-car-card border-car-border hover:border-car-border-light'
            ]"
          >
            <span class="text-[17.5px] font-black text-car-text tracking-wide whitespace-nowrap">
              {{ store.deviceInfo.floating_enabled ? '胶囊已常驻' : '胶囊已隐藏' }}
            </span>
            <span :class="['text-[12px] font-bold mt-1 whitespace-nowrap', store.deviceInfo.floating_enabled ? 'text-car-accent' : 'text-car-sub']">
              {{ store.deviceInfo.floating_enabled ? '点击关闭胶囊' : '点击开启悬浮' }}
            </span>
          </button>
        </div>

        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text">胶囊内容显示模式</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">切换胶囊内展示的文字信息</div>
          </div>
          <div class="flex space-x-2">
            <button 
              @click="setFloatingMode('name')"
              :class="[
                'flex-1 min-h-[58px] rounded-xl font-black text-[15.5px] border-2 cursor-pointer transition-all shadow-sm whitespace-nowrap',
                store.deviceInfo.floating_display_mode === 'name' || (!store.deviceInfo.floating_display_mode)
                  ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' 
                  : 'bg-car-card border-car-border text-car-sub'
              ]"
            >
              显示应用名
            </button>
            <button 
              @click="setFloatingMode('code')"
              :class="[
                'flex-1 min-h-[58px] rounded-xl font-black text-[15.5px] border-2 cursor-pointer transition-all shadow-sm whitespace-nowrap',
                store.deviceInfo.floating_display_mode === 'code'
                  ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' 
                  : 'bg-car-card border-car-border text-car-sub'
              ]"
            >
              显示动态暗码
            </button>
            <button 
              @click="setFloatingMode('battery')"
              :class="[
                'flex-1 min-h-[58px] rounded-xl font-black text-[15.5px] border-2 cursor-pointer transition-all shadow-sm whitespace-nowrap',
                store.deviceInfo.floating_display_mode === 'battery'
                  ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' 
                  : 'bg-car-card border-car-border text-car-sub'
              ]"
            >
              显示电瓶电压
            </button>
          </div>
        </div>
      </div>
    </FeatureCard>

    <!-- 2. 闲置自动屏保：主页面闲置 N 秒后自动进入原厂屏保（原生 Service 调用，不依赖 ADB） -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-6 shadow-2xl">
      <!-- 顶部黄金分栏：左详尽说明 + 右 230px 紧凑开关磁贴 -->
      <div class="flex items-center justify-between">
        <div class="w-[60%] max-w-[60%] flex flex-col space-y-1.5 shrink-0">
          <div class="flex items-center space-x-3">
            <span class="w-3.5 h-3.5 rounded-full shadow-md shrink-0 bg-car-accent"></span>
            <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">2. 闲置自动屏保 (息屏休眠)</span>
            <button
              @click="openScreensaverHelp"
              class="w-7 h-7 rounded-full bg-car-item border border-car-border text-car-accent hover:border-car-accent hover:bg-car-card font-black text-[15px] flex items-center justify-center cursor-pointer transition-all shadow-sm shrink-0 select-none"
              title="查看自动屏保核心原理"
            >
              ?
            </button>
            <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-item border-car-border text-car-text inline-flex items-center shrink-0 shadow-sm">
              <span class="w-2.5 h-2.5 rounded-full mr-2" :class="ssStatusDot"></span>{{ ssStatusText }}
            </span>
          </div>
          <div class="text-[14.5px] text-car-sub font-bold leading-normal">
            主页面（桌面）闲置达到设定时长后，自动进入原厂屏幕保护。导航、音乐、设置等界面闲置时不会打扰，行车途中同样不误触发。
          </div>
        </div>

        <div class="shrink-0 w-[230px]">
          <button
            @click="toggleScreensaver"
            :class="[
              'w-full h-[74px] px-4 py-2 rounded-2xl border-2 cursor-pointer transition-all shadow-md flex flex-col items-center justify-center text-center',
              ssEnabled
                ? 'bg-car-item border-car-accent'
                : 'bg-car-card border-car-border hover:border-car-border-light'
            ]"
          >
            <span class="text-[18.5px] font-black text-car-text tracking-wide whitespace-nowrap">{{ ssEnabled ? '闲置屏保: 已开启' : '闲置屏保: 已关闭' }}</span>
            <span class="text-[12.5px] font-bold mt-1 whitespace-nowrap" :class="ssEnabled ? 'text-car-accent' : 'text-car-sub'">{{ ssEnabled ? '点击关闭自动屏保' : '点击开启自动屏保' }}</span>
          </button>
        </div>
      </div>

      <!-- 主页面识别权限（使用情况访问）授权状态 -->
      <div class="mt-5 bg-car-item border border-car-border rounded-2xl p-4 flex items-center justify-between">
        <div class="flex flex-col space-y-1">
          <div class="flex items-center space-x-2.5">
            <span class="w-2.5 h-2.5 rounded-full shrink-0" :class="ssUsageAccess ? 'bg-emerald-400' : 'bg-amber-400'"></span>
            <span class="text-[16px] font-black text-car-text whitespace-nowrap">主页面识别权限（使用情况访问）</span>
            <span class="px-2.5 py-0.5 text-[12.5px] font-black rounded-full border bg-car-card border-car-border text-car-text whitespace-nowrap">{{ ssUsageAccess ? '已授权' : '未授权' }}</span>
          </div>
          <div class="text-[13.5px] text-car-sub font-bold leading-normal">
            用于判断当前是否停留在主页面。授权一次永久有效，仅本机读取，不联网、不上传任何数据。
          </div>
        </div>
        <button
          @click="openUsageAccess"
          :class="[
            'shrink-0 min-h-[54px] px-5 rounded-2xl border-2 font-black text-[15.5px] cursor-pointer transition-all shadow-sm whitespace-nowrap',
            ssUsageAccess
              ? 'bg-car-card border-car-border text-car-sub'
              : 'bg-car-item border-car-accent text-car-text'
          ]"
        >
          {{ ssUsageAccess ? '重新授权' : '去授权' }}
        </button>
      </div>

      <!-- 闲置时长：滑块 + 预设磁贴 + 进度条 -->
      <div class="mt-3 bg-car-item border border-car-border rounded-2xl p-4">
        <div class="flex items-center justify-between mb-3">
          <span class="text-[16px] font-black text-car-text whitespace-nowrap">闲置时长</span>
          <span class="text-[15px] font-black text-car-accent px-3.5 py-1.5 bg-car-card rounded-xl border border-car-border whitespace-nowrap">
            当前设定: {{ ssSecondsDisplay }}
          </span>
        </div>

        <div class="flex items-center space-x-4">
          <span class="text-[14px] text-car-sub font-bold whitespace-nowrap">{{ ssMinSeconds }} 秒 (更灵敏)</span>
          <input
            type="range"
            :min="ssMinSeconds"
            :max="ssMaxSeconds"
            step="1"
            v-model.number="ssSeconds"
            :disabled="ssNever"
            @change="saveSeconds"
            class="flex-1 accent-car-accent h-2.5 bg-car-card rounded-lg cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
          />
          <span class="text-[14px] text-car-sub font-bold whitespace-nowrap">{{ ssMaxSeconds }} 秒 (3分钟)</span>
          <div class="flex space-x-2 shrink-0">
            <button
              v-for="preset in ssPresets"
              :key="preset.value"
              @click="setSecondsPreset(preset.value)"
              :class="[
                'px-3 py-1.5 text-[13.5px] font-black rounded-xl border transition-all cursor-pointer whitespace-nowrap',
                ssSeconds === preset.value
                  ? 'bg-car-card border-car-accent text-car-text'
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              {{ preset.label }}
            </button>
          </div>
        </div>

        <!-- 永不自动进入：哨兵值 -1，计时器照常巡检但绝不触发 -->
        <div class="mt-3 flex items-center space-x-3">
          <button
            @click="toggleNever"
            :class="[
              'min-h-[50px] px-5 rounded-xl border-2 font-black text-[14.5px] cursor-pointer transition-all shadow-sm whitespace-nowrap',
              ssNever
                ? 'bg-car-item border-car-accent text-car-text'
                : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
            ]"
          >
            {{ ssNever ? '永不自动进入: 已开启' : '永不自动进入' }}
          </button>
          <span class="text-[13px] text-car-sub font-bold leading-normal">
            开启后屏保永不自动弹出；「立即测试一次屏保」仍可正常手动验证。
          </span>
        </div>

        <!-- 进度条：与滑块实时联动 -->
        <div class="mt-3.5 flex items-center space-x-3">
          <div class="flex-1 h-2.5 rounded-full bg-car-card overflow-hidden">
            <div class="h-full rounded-full bg-car-accent transition-all duration-200" :style="{ width: ssProgressPercent + '%' }"></div>
          </div>
          <span class="text-[13px] text-car-sub font-bold whitespace-nowrap">进度 {{ ssProgressPercent }}%</span>
        </div>
      </div>

      <!-- 生效范围与验证 -->
      <div class="mt-3 grid grid-cols-2 gap-4">
        <div class="bg-car-item border border-car-border rounded-2xl p-4 flex items-center justify-between shadow-sm">
          <div class="flex-1 min-w-0 pr-4 flex flex-col justify-center">
            <div class="text-[17px] font-black text-car-text whitespace-nowrap">主页面限制模式</div>
            <div class="text-[13px] text-car-sub font-bold mt-0.5 leading-normal">
              仅在主页面（桌面）闲置时进入屏保；导航、音乐等应用中绝不打扰。
            </div>
          </div>
          <button
            @click="toggleHomeOnly"
            :class="[
              'w-[160px] h-[64px] px-3 py-1.5 rounded-2xl border-2 font-black cursor-pointer transition-all shadow-md flex flex-col items-center justify-center text-center shrink-0',
              ssHomeOnly
                ? 'bg-car-item border-car-accent text-car-text'
                : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
            ]"
          >
            <span class="text-[16px] font-black text-car-text tracking-wide whitespace-nowrap">
              {{ ssHomeOnly ? '仅主页: 开启' : '任意界面: 放开' }}
            </span>
            <span :class="['text-[11.5px] font-bold mt-0.5 whitespace-nowrap', ssHomeOnly ? 'text-car-accent' : 'text-car-sub']">
              {{ ssHomeOnly ? '应用中不打扰' : '点击限制桌面' }}
            </span>
          </button>
        </div>

        <div class="bg-car-item border border-car-border rounded-2xl p-4 flex items-center justify-between shadow-sm">
          <div class="flex-1 min-w-0 pr-4 flex flex-col justify-center">
            <div class="text-[17px] font-black text-car-text whitespace-nowrap">屏保效果即时验证</div>
            <div class="text-[13px] text-car-sub font-bold mt-0.5 leading-normal">
              下发底层屏保广播指令，无需等待闲置倒计时即可直接验证屏保效果。
            </div>
          </div>
          <button
            @click="testScreensaver"
            class="w-[160px] h-[64px] px-3 py-1.5 rounded-2xl border-2 border-car-border bg-car-card hover:border-car-accent text-car-text font-black cursor-pointer transition-all shadow-md flex flex-col items-center justify-center text-center shrink-0"
          >
            <span class="text-[16px] font-black text-car-text tracking-wide whitespace-nowrap">
              立即测试屏保
            </span>
            <span class="text-[11.5px] font-bold mt-0.5 text-car-accent whitespace-nowrap">
              即刻唤醒屏保 ➔
            </span>
          </button>
        </div>
      </div>

      <!-- 实时诊断信息 -->
      <div class="mt-3 bg-car-card border border-car-border rounded-2xl p-4 text-[13px] text-car-sub font-bold leading-relaxed space-y-1">
        <div>运行通道：<span class="text-car-text">{{ ssChannel }}</span></div>
        <div>当前闲置：<span class="text-car-text">{{ ssIdleDisplay }}</span>（每 3 秒自动检测一次）</div>
        <div>系统原始读数：<span class="text-car-text">{{ ssIdleRaw || '—' }}</span></div>
        <div>前台判定：<span class="text-car-text">{{ ssForeground }}</span></div>
        <div v-if="ssFailReason" class="text-amber-400">通道A提示：{{ ssFailReason }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { store, bridge, showToast, openModal } from '../store';
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';

function openScreensaverHelp() {
  openModal('confirm', {
    title: '【功能指南】闲置自动屏保与前台主页感知',
    desc: '1. 原生 Service 调用：\n无需通过 ADB 命令强行熄屏，直接调用车载原生屏保服务，纯净稳定。\n\n2. 5 档快捷车规时限：\n精准收敛至 3秒 ~ 3分钟（5秒、15秒、30秒、1分钟、3分钟）及永不进入，杜绝原厂过长等待。\n\n3. 主页面保护模式：\n仅在停留在车机主页面（桌面）闲置时才触发屏保；在导航、音乐、微信等应用前台运行时绝不打扰。',
    tip: '需要授予一次「使用情况访问权限」，授权永久生效，仅本机读取，不联网、不上传任何数据。',
    showCancel: false,
    confirmText: '我知道了'
  });
}

// ==================== 1. 桌面悬浮胶囊 ====================
function toggleFloatingWindow() {
  const next = !store.deviceInfo.floating_enabled;
  store.deviceInfo.floating_enabled = next;
  bridge.call('toggleFloatingWindow', next);
  showToast('悬浮微胶囊已' + (next ? '开启' : '关闭'));
}

function setFloatingMode(mode) {
  store.deviceInfo.floating_display_mode = mode;
  bridge.call('setFloatingDisplayMode', mode);
  showToast('显示模式已切换');
}

// ==================== 2. 闲置自动屏保 ====================
const ssEnabled = ref(false);
const ssSeconds = ref(30);
const ssMinSeconds = ref(3);
const ssMaxSeconds = ref(180);
const ssNever = ref(false);
const ssHomeOnly = ref(true);
const ssUsageAccess = ref(false);
const ssChannel = ref('未启动');
const ssIdleMs = ref(-1);
const ssIdleRaw = ref('');
const ssFailReason = ref('');
const ssChannelAReady = ref(false);
const ssForeground = ref('查询中');
const ssPresets = [
  { value: 5, label: '5秒' },
  { value: 15, label: '15秒' },
  { value: 30, label: '30秒' },
  { value: 60, label: '1分钟' },
  { value: 180, label: '3分钟' },
];
let ssTimer = null;

const ssSecondsDisplay = computed(() => ssNever.value ? '永不' : (ssSeconds.value + ' 秒'));

const ssProgressPercent = computed(() => {
  if (ssNever.value) return 0;
  const min = ssMinSeconds.value;
  const max = ssMaxSeconds.value;
  if (max <= min) return 0;
  const v = Math.min(Math.max(ssSeconds.value, min), max);
  return Math.round(((v - min) / (max - min)) * 100);
});

const ssDegraded = computed(() => {
  const c = String(ssChannel.value || '');
  return c.indexOf('失败') >= 0 || c.indexOf('不可用') >= 0;
});

const ssStatusText = computed(() => {
  if (!ssEnabled.value) return '已关闭';
  if (ssChannelAReady.value) return '运行中';
  if (ssDegraded.value) return '已降级熄屏兜底';
  return '启动中';
});

const ssStatusDot = computed(() => {
  if (!ssEnabled.value) return 'bg-car-sub';
  if (ssChannelAReady.value) return 'bg-emerald-400';
  if (ssDegraded.value) return 'bg-rose-400';
  return 'bg-amber-400';
});

const ssIdleDisplay = computed(() => {
  const v = ssIdleMs.value;
  if (v === null || v === undefined || v < 0) return '读取中';
  return (v / 1000).toFixed(1) + ' 秒';
});

function loadScreensaverConfig() {
  try {
    const raw = bridge.call('getScreensaverConfig');
    if (!raw) return;
    const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
    if (typeof data.enabled === 'boolean') ssEnabled.value = data.enabled;
    if (typeof data.seconds === 'number') {
      ssSeconds.value = data.seconds;
      ssNever.value = (data.seconds === -1);
    }
    if (typeof data.minSeconds === 'number') ssMinSeconds.value = data.minSeconds;
    if (typeof data.maxSeconds === 'number') ssMaxSeconds.value = data.maxSeconds;
    if (typeof data.home_only === 'boolean') ssHomeOnly.value = data.home_only;
    if (typeof data.usage_access === 'boolean') ssUsageAccess.value = data.usage_access;
    if (typeof data.channel === 'string') ssChannel.value = data.channel;
    if (typeof data.idle_ms === 'number') ssIdleMs.value = data.idle_ms;
    if (typeof data.idle_raw === 'string') ssIdleRaw.value = data.idle_raw;
    if (typeof data.fail_reason === 'string') ssFailReason.value = data.fail_reason;
    if (typeof data.channel_a_ready === 'boolean') ssChannelAReady.value = data.channel_a_ready;
    if (typeof data.foreground === 'string') ssForeground.value = data.foreground;
  } catch (e) {}
}

function saveScreensaverConfig(patch) {
  try {
    bridge.call('setScreensaverConfig', JSON.stringify(patch));
  } catch (e) {}
}

function toggleScreensaver() {
  const next = !ssEnabled.value;
  ssEnabled.value = next;
  saveScreensaverConfig({ enabled: next });
  if (next) {
    if (!ssUsageAccess.value) {
      showToast('已开启闲置屏保，请先授权「使用情况访问」才能识别主页面');
    } else {
      showToast('已开启闲置屏保：主页面闲置 ' + ssSeconds.value + ' 秒后自动进入屏保');
    }
  } else {
    showToast('已关闭闲置自动屏保');
  }
  setTimeout(loadScreensaverConfig, 400);
}

function saveSeconds() {
  const v = Math.min(Math.max(Math.round(ssSeconds.value), ssMinSeconds.value), ssMaxSeconds.value);
  ssSeconds.value = v;
  ssNever.value = false;
  saveScreensaverConfig({ seconds: v });
  showToast('闲置时长已设为 ' + v + ' 秒');
}

function setSecondsPreset(value) {
  ssSeconds.value = value;
  saveSeconds();
}

function toggleNever() {
  const next = !ssNever.value;
  ssNever.value = next;
  if (next) {
    ssSeconds.value = -1;
    saveScreensaverConfig({ seconds: -1 });
    showToast('已设为永不自动进入屏保');
  } else {
    ssSeconds.value = 30;
    saveScreensaverConfig({ seconds: 30 });
    showToast('已恢复自动屏保（30 秒）');
  }
}

function toggleHomeOnly() {
  const next = !ssHomeOnly.value;
  ssHomeOnly.value = next;
  saveScreensaverConfig({ home_only: next });
  showToast(next ? '已限制为仅在主页面生效' : '已放开为任意界面生效');
}

function openUsageAccess() {
  bridge.call('openUsageAccessSettings');
  showToast('请在系统列表中勾选「吉利控制台」以授予使用情况访问权限');
}

function testScreensaver() {
  const res = bridge.call('triggerScreenSaverNow');
  showToast(res ? String(res) : '已下发屏保调用指令');
  setTimeout(loadScreensaverConfig, 600);
}

onMounted(() => {
  loadScreensaverConfig();
  ssTimer = setInterval(loadScreensaverConfig, 3000);
});

onUnmounted(() => {
  if (ssTimer) {
    clearInterval(ssTimer);
    ssTimer = null;
  }
});
</script>
