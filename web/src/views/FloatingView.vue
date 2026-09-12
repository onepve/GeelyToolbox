<template>
  <div class="flex flex-col space-y-6">
    <!-- 1. 全局桌面迷你悬浮胶囊 (整卡一体化开关：整卡即触控大靶区 · 右侧纯文字) -->
    <div 
      @click="toggleFloatingWindow"
      :class="[
        'rounded-3xl border-2 p-6 transition-all flex items-center justify-between cursor-pointer select-none shadow-xl',
        store.deviceInfo.floating_enabled
          ? 'bg-car-card border-car-accent ring-2 ring-car-accent/20'
          : 'bg-car-card border-car-border hover:border-car-border-light'
      ]"
    >
      <div class="flex-1 min-w-0 pr-6 flex flex-col space-y-2">
        <div class="flex items-center space-x-3">
          <span class="text-[23px] font-black text-car-text tracking-wide">1. 全局桌面迷你悬浮胶囊总开关</span>
          <button 
            @click.stop="openFloatingHelp" 
            class="w-8 h-8 rounded-full bg-car-item border-2 border-car-accent text-car-accent font-black text-[16px] flex items-center justify-center cursor-pointer shadow-sm shrink-0"
            title="查看功能指南"
          >?</button>
          <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent">桌面常驻</span>
        </div>
        <div class="text-[15.5px] text-car-sub font-bold leading-relaxed">
          在桌面及全屏应用上层常驻微型流光胶囊，支持手指自由拖拽贴边吸附，实时展示电瓶电压或动态暗码，点击胶囊秒开控制台。轻触整卡直接开/关！
        </div>
      </div>
      <div class="min-w-[240px] shrink-0 flex flex-col items-end justify-center text-right">
        <div class="flex items-center space-x-2.5">
          <span :class="['w-3 h-3 rounded-full', store.deviceInfo.floating_enabled ? 'bg-emerald-500 shadow-[0_0_10px_#10B981]' : 'bg-slate-500']"></span>
          <span :class="['text-[22px] font-black tracking-wide', store.deviceInfo.floating_enabled ? 'text-car-text' : 'text-car-sub']">
            {{ store.deviceInfo.floating_enabled ? '胶囊已常驻' : '胶囊已隐藏' }}
          </span>
        </div>
        <span :class="['text-[13.5px] font-bold mt-1.5', store.deviceInfo.floating_enabled ? 'text-car-accent' : 'text-car-sub']">
          {{ store.deviceInfo.floating_enabled ? '桌面边缘常驻 · 轻触整卡关闭' : '点击整卡开启桌面胶囊' }}
        </span>
      </div>
    </div>

    <!-- 胶囊内容显示模式 (独立大卡片 · 3选1饱满磁贴) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-6 shadow-xl flex flex-col space-y-4">
      <div class="flex items-center justify-between">
        <div class="flex flex-col">
          <span class="text-[20px] font-black text-car-text">胶囊常驻展示模式</span>
          <span class="text-[14.5px] text-car-sub font-bold mt-0.5">选择迷你胶囊上层常驻展示的核心实时数据</span>
        </div>
        <span class="text-[14px] text-car-accent font-black px-3.5 py-1 rounded-xl bg-car-item border border-car-border">
          当前模式: {{ store.deviceInfo.floating_display_mode === 'name' ? '显示应用名' : (store.deviceInfo.floating_display_mode === 'code' ? '显示动态暗码' : '显示电瓶电压 (推荐)') }}
        </span>
      </div>
      <div class="grid grid-cols-3 gap-3.5">
        <button 
          @click="setFloatingMode('battery')"
          :class="[
            'min-h-[68px] p-3 rounded-2xl font-black text-[17px] border-2 cursor-pointer transition-all shadow-sm flex items-center justify-center text-center whitespace-nowrap',
            store.deviceInfo.floating_display_mode === 'battery' || (!store.deviceInfo.floating_display_mode)
              ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' 
              : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
          ]"
        >
          ⚡ 显示电瓶电压 (推荐)
        </button>
        <button 
          @click="setFloatingMode('code')"
          :class="[
            'min-h-[68px] p-3 rounded-2xl font-black text-[17px] border-2 cursor-pointer transition-all shadow-sm flex items-center justify-center text-center whitespace-nowrap',
            store.deviceInfo.floating_display_mode === 'code'
              ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' 
              : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
          ]"
        >
          🔑 显示动态暗码
        </button>
        <button 
          @click="setFloatingMode('name')"
          :class="[
            'min-h-[68px] p-3 rounded-2xl font-black text-[17px] border-2 cursor-pointer transition-all shadow-sm flex items-center justify-center text-center whitespace-nowrap',
            store.deviceInfo.floating_display_mode === 'name'
              ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' 
              : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
          ]"
        >
          📱 显示当前应用名
        </button>
      </div>
    </div>

    <!-- 2. 闲置自动屏保：整卡一体化总开关 (整卡即触控大靶区 · 右侧纯文字) -->
    <div 
      @click="toggleScreensaver"
      :class="[
        'rounded-3xl border-2 p-6 transition-all flex items-center justify-between cursor-pointer select-none shadow-xl',
        ssEnabled
          ? 'bg-car-card border-car-accent ring-2 ring-car-accent/20'
          : 'bg-car-card border-car-border hover:border-car-border-light'
      ]"
    >
      <div class="flex-1 min-w-0 pr-6 flex flex-col space-y-2">
        <div class="flex items-center space-x-3">
          <span class="text-[23px] font-black text-car-text tracking-wide">2. 闲置自动屏保 (息屏休眠总开关)</span>
          <button 
            @click.stop="openScreensaverHelp" 
            class="w-8 h-8 rounded-full bg-car-item border-2 border-car-accent text-car-accent font-black text-[16px] flex items-center justify-center cursor-pointer shadow-sm shrink-0"
            title="查看自动屏保核心原理"
          >?</button>
          <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent inline-flex items-center">
            <span class="w-2.5 h-2.5 rounded-full mr-2" :class="ssStatusDot"></span>{{ ssStatusText }}
          </span>
        </div>
        <div class="text-[15.5px] text-car-sub font-bold leading-relaxed">
          主页面（桌面）闲置达到设定秒数后自动唤起原厂屏幕保护；导航、音乐等应用运行中绝不打扰。关闭总开关后后台计时器彻底销毁，零 CPU 消耗！
        </div>
      </div>
      <div class="min-w-[240px] shrink-0 flex flex-col items-end justify-center text-right">
        <div class="flex items-center space-x-2.5">
          <span :class="['w-3 h-3 rounded-full', ssEnabled ? 'bg-emerald-500 shadow-[0_0_10px_#10B981]' : 'bg-slate-500']"></span>
          <span :class="['text-[22px] font-black tracking-wide', ssEnabled ? 'text-car-text' : 'text-car-sub']">
            {{ ssEnabled ? '自动屏保已开启' : '自动屏保已关闭' }}
          </span>
        </div>
        <span :class="['text-[13.5px] font-bold mt-1.5', ssEnabled ? 'text-car-accent' : 'text-car-sub']">
          {{ ssEnabled ? '闲置达标自动息屏 · 轻触整卡关闭' : '点击整卡开启自动屏保' }}
        </span>
      </div>
    </div>

    <!-- 闲置时长与生效控制卡片 (纯加减时间步进器 · 彻底去除死板预设与永不休眠) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-6 shadow-xl flex flex-col space-y-5">
      <div class="flex items-center justify-between">
        <div class="flex flex-col space-y-1">
          <div class="flex items-center space-x-2.5">
            <span class="text-[20px] font-black text-car-text">闲置时长精准微调</span>
            <span class="px-2.5 py-0.5 text-[12.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent">自由设定</span>
          </div>
          <span class="text-[14.5px] text-car-sub font-bold">
            支持自由设定 3 秒 ~ 180 秒。点击两侧 [-15s] / [-5s] 与 [+5s] [+15s] 步进加减时长，想常亮直接关掉上方总开关即可。
          </span>
        </div>
        <div class="flex items-center space-x-3 shrink-0">
          <button 
            @click="adjustSeconds(-15)"
            class="h-[54px] px-4 rounded-xl bg-car-item border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
            title="减少 15 秒"
          >-15s</button>
          <button 
            @click="adjustSeconds(-5)"
            class="h-[54px] px-4 rounded-xl bg-car-item border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
            title="减少 5 秒"
          >-5s</button>
          <div class="flex items-baseline px-5 py-1.5 bg-car-item rounded-2xl border-2 border-car-accent/60 min-w-[140px] justify-center shadow-inner">
            <span class="text-[32px] font-black text-car-accent font-mono leading-none">{{ ssSeconds }}</span>
            <span class="text-[15px] font-bold text-car-sub ml-1.5 font-mono">秒 ({{ ssSeconds >= 60 ? (ssSeconds / 60).toFixed(1) + '分' : '息屏' }})</span>
          </div>
          <button 
            @click="adjustSeconds(5)"
            class="h-[54px] px-4 rounded-xl bg-car-item border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
            title="增加 5 秒"
          >+5s</button>
          <button 
            @click="adjustSeconds(15)"
            class="h-[54px] px-4 rounded-xl bg-car-item border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
            title="增加 15 秒"
          >+15s</button>
        </div>
      </div>

      <!-- 快捷档位大磁贴 (5 档纯净车规预设) -->
      <div class="grid grid-cols-5 gap-3 pt-2">
        <button
          v-for="preset in ssPresets"
          :key="preset.value"
          @click="setSecondsPreset(preset.value)"
          :class="[
            'h-[56px] rounded-xl border-2 font-black text-[16px] cursor-pointer transition-all whitespace-nowrap shadow-sm flex items-center justify-center',
            ssSeconds === preset.value
              ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20'
              : 'bg-car-item border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'
          ]"
        >
          {{ preset.label }}
        </button>
      </div>

      <!-- 主页面识别权限授权状态 -->
      <div class="pt-3 border-t border-car-border/60 flex items-center justify-between">
        <div class="flex flex-col space-y-0.5">
          <div class="flex items-center space-x-2">
            <span class="w-2.5 h-2.5 rounded-full shrink-0" :class="ssUsageAccess ? 'bg-emerald-400' : 'bg-amber-400'"></span>
            <span class="text-[16px] font-black text-car-text">主页面识别权限（使用情况访问）</span>
            <span class="px-2.5 py-0.5 text-[12px] font-black rounded-full border bg-car-item border-car-border text-car-text">{{ ssUsageAccess ? '已授权' : '未授权' }}</span>
          </div>
          <span class="text-[13.5px] text-car-sub font-bold">用于精准判断当前是否停留在主页面桌面，授权一次永久有效。</span>
        </div>
        <button
          @click="openUsageAccess"
          class="h-[52px] px-6 rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-black text-[15.5px] cursor-pointer shadow-sm whitespace-nowrap"
        >
          {{ ssUsageAccess ? '重新授权' : '去授权 ➔' }}
        </button>
      </div>
    </div>

    <!-- 3. 主页面限制模式 (整卡一体化开关) -->
    <div 
      @click="toggleHomeOnly"
      :class="[
        'rounded-3xl border-2 p-6 transition-all flex items-center justify-between cursor-pointer select-none shadow-xl',
        ssHomeOnly
          ? 'bg-car-card border-car-accent ring-2 ring-car-accent/20'
          : 'bg-car-card border-car-border hover:border-car-border-light'
      ]"
    >
      <div class="flex-1 min-w-0 pr-6 flex flex-col space-y-2">
        <div class="flex items-center space-x-3">
          <span class="text-[23px] font-black text-car-text tracking-wide">3. 主页面限制模式</span>
          <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent">前台避让</span>
        </div>
        <div class="text-[15.5px] text-car-sub font-bold leading-relaxed">
          仅在停留在主页面（桌面）闲置时进入屏保；高德导航、音乐播放等前台应用运行时绝不打扰。轻触整卡切换！
        </div>
      </div>
      <div class="min-w-[240px] shrink-0 flex flex-col items-end justify-center text-right">
        <div class="flex items-center space-x-2.5">
          <span :class="['w-3 h-3 rounded-full', ssHomeOnly ? 'bg-emerald-500 shadow-[0_0_10px_#10B981]' : 'bg-slate-500']"></span>
          <span :class="['text-[22px] font-black tracking-wide', ssHomeOnly ? 'text-car-text' : 'text-car-sub']">
            {{ ssHomeOnly ? '仅主页生效' : '任意界面放开' }}
          </span>
        </div>
        <span :class="['text-[13.5px] font-bold mt-1.5', ssHomeOnly ? 'text-car-accent' : 'text-car-sub']">
          {{ ssHomeOnly ? '导航中绝不打扰 · 点击放开' : '点击限制仅主页面' }}
        </span>
      </div>
    </div>

    <!-- 4. 屏保即时测试 (独立大卡片) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-6 shadow-xl flex items-center justify-between">
      <div class="flex-1 min-w-0 pr-6 flex flex-col space-y-1.5">
        <span class="text-[21px] font-black text-car-text">屏保效果即时测试</span>
        <span class="text-[15px] text-car-sub font-bold">直接下发车载原生屏保指令，无需等待闲置倒计时即可当场验证屏幕保护效果。</span>
      </div>
      <button
        @click="testScreensaver"
        class="min-w-[220px] h-[64px] px-6 rounded-2xl border-2 border-car-accent bg-car-item text-car-text font-black text-[18px] cursor-pointer hover:border-car-accent shadow-md flex items-center justify-center whitespace-nowrap"
      >
        <span>立即测试屏保 ➔</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { store, bridge, showToast, openModal } from '../store';

function openFloatingHelp() {
  openModal('confirm', {
    title: '【功能指南】桌面悬浮微胶囊机制',
    desc: '1. 浮窗交互：在车机桌面或其他全屏应用上层常驻微型流光胶囊，支持手指自由拖拽并自动贴边吸附，点击胶囊秒开控制台。\n\n2. 三大显示模式：\n• 显示电瓶电压：实时监测整车 12V 蓄电池精准电压；\n• 显示动态暗码：直接展示今日工程模式暗码；\n• 显示应用名：当前最上层运行的软件名称。',
    tip: '建议将显示模式设为「显示电瓶电压」，时刻掌握爱车电瓶健康。',
    showCancel: false,
    confirmText: '我知道了'
  });
}

function openScreensaverHelp() {
  openModal('confirm', {
    title: '【功能指南】闲置自动屏保与前台主页感知',
    desc: '1. 原生 Service 调用：无需通过 ADB 命令强行熄屏，直接调用车载原生屏保服务，纯净稳定。\n\n2. 自由秒数调节：收敛至 3秒 ~ 3分钟（180秒），左右加减步进，想常亮直接关掉上方总开关，彻底停止后台计时器。\n\n3. 主页面保护模式：仅在停留在车机主页面（桌面）闲置时才触发屏保；导航、音乐等应用前台运行时绝不打扰。',
    tip: '需要授予一次「使用情况访问权限」，授权永久生效，仅本机读取，不联网、不上传任何数据。',
    showCancel: false,
    confirmText: '我知道了'
  });
}

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

// 自动屏保状态
const ssEnabled = ref(false);
const ssSeconds = ref(30);
const ssMinSeconds = 3;
const ssMaxSeconds = 180;
const ssHomeOnly = ref(true);
const ssUsageAccess = ref(false);
const ssChannel = ref('未启动');
const ssFailReason = ref('');
const ssChannelAReady = ref(false);
const ssPresets = [
  { value: 5, label: '5 秒 (极速)' },
  { value: 15, label: '15 秒 (推荐)' },
  { value: 30, label: '30 秒 (半分钟)' },
  { value: 60, label: '1 分钟 (中度)' },
  { value: 180, label: '3 分钟 (深度)' },
];
let ssTimer = null;

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
  if (!ssEnabled.value) return 'bg-slate-500';
  if (ssChannelAReady.value) return 'bg-emerald-400';
  if (ssDegraded.value) return 'bg-rose-400';
  return 'bg-amber-400';
});

function toggleScreensaver() {
  const next = !ssEnabled.value;
  ssEnabled.value = next;
  bridge.call('setScreensaverConfig', JSON.stringify({ enabled: next }));
  showToast('自动屏保已' + (next ? '开启' : '关闭'));
  fetchScreensaverState();
}

function adjustSeconds(delta) {
  let next = Math.max(ssMinSeconds, Math.min(ssMaxSeconds, ssSeconds.value + delta));
  ssSeconds.value = next;
  saveSeconds();
}

function setSecondsPreset(val) {
  ssSeconds.value = val;
  saveSeconds();
}

function saveSeconds() {
  bridge.call('setScreensaverConfig', JSON.stringify({ seconds: ssSeconds.value }));
  showToast(`屏保闲置时长已设为: ${ssSeconds.value} 秒`);
}

function toggleHomeOnly() {
  const next = !ssHomeOnly.value;
  ssHomeOnly.value = next;
  bridge.call('setScreensaverConfig', JSON.stringify({ home_only: next }));
  showToast('主页面限制模式: ' + (next ? '已开启' : '已放开'));
}

function openUsageAccess() {
  bridge.call('openUsageAccessSettings');
  showToast('正在打开权限设置...');
}

function testScreensaver() {
  bridge.call('triggerScreenSaverNow');
  showToast('正在唤起车载屏幕保护...');
}

function fetchScreensaverState() {
  try {
    const raw = bridge.call('getScreensaverConfig');
    if (raw) {
      const cfg = typeof raw === 'string' ? JSON.parse(raw) : raw;
      ssEnabled.value = !!cfg.enabled;
      if (typeof cfg.seconds === 'number' && cfg.seconds >= ssMinSeconds) {
        ssSeconds.value = cfg.seconds;
      }
      ssHomeOnly.value = cfg.home_only !== false;
      ssUsageAccess.value = !!cfg.usage_access;
      ssChannel.value = cfg.channel || '未启动';
      ssChannelAReady.value = !!cfg.channel_a_ready;
      ssFailReason.value = cfg.fail_reason || '';
    }
  } catch (e) {}
}

onMounted(() => {
  fetchScreensaverState();
  ssTimer = setInterval(fetchScreensaverState, 3000);
});

onUnmounted(() => {
  if (ssTimer) clearInterval(ssTimer);
});
</script>
