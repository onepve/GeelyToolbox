<template>
  <div class="flex flex-col space-y-7 shrink-0">
    <!-- 双列对称：左列【桌面悬浮微胶囊】 + 右列【闲置自动息屏保护】 -->
    <div class="grid grid-cols-2 gap-5 items-stretch">
      <!-- 左列：桌面迷你悬浮胶囊 (强制等高 h-full min-h-[460px]) -->
      <div class="rounded-3xl border-2 border-car-border bg-car-card p-6 shadow-xl h-full min-h-[460px] flex flex-col justify-between transition-all duration-200">
        <!-- 头部标题 + 帮助 -->
        <div class="flex items-center justify-between shrink-0 mb-4">
          <div class="flex items-center space-x-3">
            <span class="text-[22px] font-black text-car-text tracking-wide">桌面迷你悬浮胶囊</span>
            <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent shrink-0">桌面常驻</span>
            <HelpDot @click="openFloatingHelp" title="查看桌面悬浮微胶囊机制" />
          </div>
        </div>

        <!-- 胶囊整卡一体化开关大磁贴 -->
        <div 
          @click="toggleFloatingWindow"
          :class="[
            'p-4 rounded-2xl border-2 cursor-pointer transition-all shadow-sm flex items-center justify-between select-none mb-4',
            store.deviceInfo.floating_enabled
              ? 'bg-car-item border-car-accent ring-2 ring-car-accent/20'
              : 'bg-car-item border-car-border hover:border-car-border-light'
          ]"
        >
          <div class="flex flex-col space-y-0.5">
            <span class="text-[17.5px] font-black text-car-text">悬浮胶囊常驻开关</span>
            <span class="text-[13.5px] text-car-sub font-bold">在桌面及全屏应用上层常驻，支持自由拖拽吸附</span>
          </div>
          <div class="flex items-center space-x-2.5 shrink-0 pl-3">
            <StatusDot size="md" :color="store.deviceInfo.floating_enabled ? 'ok' : 'offDim'" :glow-px="8" />
            <span :class="['text-[18px] font-black', store.deviceInfo.floating_enabled ? 'text-car-text' : 'text-car-sub']">
              {{ store.deviceInfo.floating_enabled ? '胶囊已常驻' : '胶囊已隐藏' }}
            </span>
          </div>
        </div>

        <!-- 中部：3 选 1 纯净车规展示模式 (拔除所有违规 Emoji，加高车规大靶区) -->
        <div class="flex-1 min-w-0 flex flex-col justify-center space-y-3 py-2">
          <div class="flex items-center justify-between">
            <span class="text-[16px] font-black text-car-text">胶囊核心实时数据呈现</span>
            <span class="text-[13px] text-car-accent font-bold px-3 py-0.5 rounded-lg bg-car-item border border-car-border">
              {{ store.deviceInfo.floating_display_mode === 'name' ? '当前：应用名' : (store.deviceInfo.floating_display_mode === 'code' ? '当前：动态暗码' : '当前：电瓶电压') }}
            </span>
          </div>

          <div class="grid grid-cols-3 gap-2.5">
            <button 
              @click="setFloatingMode('battery')"
              :class="[
                'h-[58px] p-2 rounded-xl font-black text-[15.5px] border-2 cursor-pointer transition-all shadow-sm flex items-center justify-center text-center whitespace-nowrap',
                store.deviceInfo.floating_display_mode === 'battery' || (!store.deviceInfo.floating_display_mode)
                  ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20 shadow-md' 
                  : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
              ]"
            >
              显示电瓶电压 (推荐)
            </button>
            <button 
              @click="setFloatingMode('code')"
              :class="[
                'h-[58px] p-2 rounded-xl font-black text-[15.5px] border-2 cursor-pointer transition-all shadow-sm flex items-center justify-center text-center whitespace-nowrap',
                store.deviceInfo.floating_display_mode === 'code'
                  ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20 shadow-md' 
                  : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
              ]"
            >
              显示动态暗码
            </button>
            <button 
              @click="setFloatingMode('name')"
              :class="[
                'h-[58px] p-2 rounded-xl font-black text-[15.5px] border-2 cursor-pointer transition-all shadow-sm flex items-center justify-center text-center whitespace-nowrap',
                store.deviceInfo.floating_display_mode === 'name'
                  ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20 shadow-md' 
                  : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
              ]"
            >
              显示当前应用名
            </button>
          </div>
        </div>

        <!-- 底部提示信息：拉大留白 -->
        <div class="pt-4 border-t border-car-border/60 mt-auto shrink-0 flex items-center justify-between">
          <span class="text-[14px] text-car-sub font-bold">点击胶囊秒开缤越助手控制台</span>
          <span class="text-[13.5px] text-car-accent font-black">支持手指自由拖拽贴边吸附</span>
        </div>
      </div>

      <!-- 右列：闲置自动屏保息屏 (强制等高 h-full min-h-[460px]) -->
      <div class="rounded-3xl border-2 border-car-border bg-car-card p-6 shadow-xl h-full min-h-[460px] flex flex-col justify-between transition-all duration-200">
        <!-- 头部标题 + 帮助 -->
        <div class="flex items-center justify-between shrink-0 mb-4">
          <div class="flex items-center space-x-3">
            <span class="text-[22px] font-black text-car-text tracking-wide">闲置自动屏保</span>
            <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent inline-flex items-center shrink-0">
              <StatusDot class="mr-2" size="sm" :color="ssStatusDot" :glow="false" />{{ ssStatusText }}
            </span>
            <HelpDot @click="openScreensaverHelp" title="查看自动屏保核心原理" />
          </div>
        </div>

        <!-- 屏保整卡一体化开关大磁贴 -->
        <div 
          @click="toggleScreensaver"
          :class="[
            'p-4 rounded-2xl border-2 cursor-pointer transition-all shadow-sm flex items-center justify-between select-none mb-4',
            ssEnabled
              ? 'bg-car-item border-car-accent ring-2 ring-car-accent/20'
              : 'bg-car-item border-car-border hover:border-car-border-light'
          ]"
        >
          <div class="flex flex-col space-y-0.5">
            <span class="text-[17.5px] font-black text-car-text">自动屏保息屏总闸</span>
            <span class="text-[13.5px] text-car-sub font-bold">桌面闲置达标自动息屏唤醒原生屏保，关闭零CPU消耗</span>
          </div>
          <div class="flex items-center space-x-2.5 shrink-0 pl-3">
            <StatusDot size="md" :color="ssEnabled ? 'ok' : 'offDim'" :glow-px="8" />
            <span :class="['text-[18px] font-black', ssEnabled ? 'text-car-text' : 'text-car-sub']">
              {{ ssEnabled ? '自动屏保已开启' : '自动屏保已关闭' }}
            </span>
          </div>
        </div>

        <!-- 中部：纯加减时长控制器 + 5档预设 (彻底拉开安全间距，杜绝贴脸) -->
        <div class="flex-1 min-w-0 flex flex-col justify-center space-y-4 py-2">
          <!-- 纯加减微调器 -->
          <div class="flex items-center justify-between bg-car-item border-2 border-car-border rounded-2xl p-2.5">
            <div class="flex items-center space-x-2">
              <button 
                @click="adjustSeconds(-15)"
                class="h-[50px] px-4 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
              >-15s</button>
              <button 
                @click="adjustSeconds(-5)"
                class="h-[50px] px-4 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
              >-5s</button>
            </div>
            <div class="flex items-baseline space-x-1.5 px-4">
              <span class="text-[30px] font-black text-car-accent font-mono leading-none">{{ ssSeconds }}</span>
              <span class="text-[14px] text-car-sub font-bold font-mono">秒 ({{ ssSeconds >= 60 ? (ssSeconds / 60).toFixed(1) + '分' : '息屏' }})</span>
            </div>
            <div class="flex items-center space-x-2">
              <button 
                @click="adjustSeconds(5)"
                class="h-[50px] px-4 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
              >+5s</button>
              <button 
                @click="adjustSeconds(15)"
                class="h-[50px] px-4 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
              >+15s</button>
            </div>
          </div>

          <!-- 5 档快捷车规预设 (上下间距舒展) -->
          <div class="grid grid-cols-5 gap-2">
            <button
              v-for="preset in ssPresets"
              :key="preset.value"
              @click="setSecondsPreset(preset.value)"
              :class="[
                'h-[50px] rounded-xl border-2 font-black text-[14.5px] cursor-pointer transition-all whitespace-nowrap shadow-sm flex items-center justify-center',
                ssSeconds === preset.value
                  ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20 shadow-sm'
                  : 'bg-car-item border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'
              ]"
            >
              {{ preset.label }}
            </button>
          </div>
        </div>

        <!-- 底部操作栏：3档触发策略单选组 + 屏保测试动作 -->
        <div class="pt-4 border-t border-car-border/60 mt-auto shrink-0 space-y-3">
          <!-- 3 档策略分段选择组 -->
          <div class="grid grid-cols-3 gap-2">
            <button
              v-for="p in ssPolicyOptions"
              :key="p.value"
              @click="setPolicy(p.value)"
              :class="[
                'h-[50px] rounded-xl border-2 font-black text-[14.5px] cursor-pointer transition-all whitespace-nowrap shadow-sm flex items-center justify-center space-x-1.5',
                ssPolicy === p.value
                  ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20'
                  : 'bg-car-item border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'
              ]"
            >
              <StatusDot size="sm" :color="ssPolicy === p.value ? 'okBright' : 'offDim'" :glow="false" />
              <span>{{ p.label }}</span>
            </button>
          </div>

          <!-- 独立测试屏保动作按钮 -->
          <button
            @click="testScreensaver"
            class="w-full h-[52px] px-4 rounded-2xl border-2 border-car-accent bg-car-item text-car-accent hover:border-car-accent active:scale-[0.99] font-black text-[16px] cursor-pointer shadow-md flex items-center justify-center space-x-1.5 transition-all"
          >
            <span>屏保测试</span>
            <span class="text-sm">➔</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { store, bridge, showToast, openModal } from '../store';
import StatusDot from '../components/StatusDot.vue';
import HelpDot from '../components/HelpDot.vue';

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
const ssPolicy = ref('home');
const ssPolicyOptions = [
  { value: 'all', label: '全局生效' },
  { value: 'home', label: '仅主页' },
  { value: 'avoid_navi', label: '避让导航' }
];
const ssUsageAccess = ref(false);
const ssChannel = ref('未启动');
const ssFailReason = ref('');
const ssChannelAReady = ref(false);
const ssPresets = [
  { value: 5, label: '5 秒 (极速)' },
  { value: 15, label: '15 秒 (推荐)' },
  { value: 30, label: '30 秒 (半分)' },
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
  if (!ssEnabled.value) return 'offDim';
  if (ssChannelAReady.value) return 'okBright';
  if (ssDegraded.value) return 'err';
  return 'warnSoft';
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

function setPolicy(p) {
  ssPolicy.value = p;
  ssHomeOnly.value = (p === 'home');
  bridge.call('setScreensaverConfig', JSON.stringify({ policy: p, home_only: p === 'home' }));
  const label = ssPolicyOptions.find(opt => opt.value === p)?.label || p;
  showToast(`屏保策略已设为: ${label}`);
}

function toggleHomeOnly() {
  setPolicy(ssPolicy.value === 'home' ? 'all' : 'home');
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
      if (cfg.policy) {
        ssPolicy.value = cfg.policy;
      } else {
        ssPolicy.value = cfg.home_only !== false ? 'home' : 'all';
      }
      ssHomeOnly.value = ssPolicy.value === 'home';
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
