<template>
  <div class="flex flex-col space-y-6">
    <!-- 1. 全局桌面迷你悬浮胶囊 -->
    <FeatureCard 
      title="1. 全局桌面迷你悬浮胶囊 (实时状态与动态暗码)"
      desc="在车机桌面或其他应用上层常驻迷你流光胶囊，支持手指自由拖拽吸附，实时显示软件状态或今日动态工程暗码。"
    >
      <div class="grid grid-cols-2 gap-4">
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text">悬浮微胶囊总开关</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">开启后在全屏最上层常驻微型胶囊，点击秒开工具箱</div>
          </div>
          <MatrixButton 
            :title="store.deviceInfo.floating_enabled ? '已开启' : '已关闭'"
            :active="store.deviceInfo.floating_enabled"
            @click="toggleFloatingWindow"
          />
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
      <div class="mt-3 flex items-center justify-between">
        <button
          @click="toggleHomeOnly"
          :class="[
            'min-h-[54px] px-5 rounded-2xl border-2 font-black text-[15.5px] cursor-pointer transition-all shadow-sm whitespace-nowrap',
            ssHomeOnly
              ? 'bg-car-item border-car-accent text-car-text'
              : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
          ]"
        >
          {{ ssHomeOnly ? '仅在主页面生效: 已开启' : '仅在主页面生效: 已关闭 (任意界面)' }}
        </button>

        <button
          @click="testScreensaver"
          class="shrink-0 min-h-[54px] px-6 rounded-2xl border-2 border-car-border bg-car-item text-car-text font-black text-[15.5px] cursor-pointer hover:border-car-border-light transition-all shadow-sm whitespace-nowrap"
        >
          立即测试一次屏保
        </button>
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

    <!-- 3. 今日工程动态暗码大卡片 -->
    <FeatureCard 
      title="3. 今日动态工程暗码 (免剪贴板 · 物理大字号直显)"
      desc="根据车载系统时钟每秒自动计算，直出高亮暗码。车机拨号界面不支持粘贴，请点击按钮打开拨号盘手动按键输入。"
    >
      <div class="grid grid-cols-2 gap-4">
        <!-- 主流+5暗码 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between">
          <div>
            <div class="text-[15px] font-bold text-car-sub">主流新固件 (月份+5 算法)</div>
            <div class="text-[28px] font-black text-car-accent mt-1 tracking-wider">
              {{ store.dynamicCodePlus5 }}
            </div>
            <div class="text-[13px] text-car-sub mt-1">适用于绝大多数 2022~2024 款吉利车机</div>
          </div>
          <button 
            @click="openDialer"
            class="h-[58px] px-6 rounded-2xl bg-car-card border-2 border-car-accent text-car-accent hover:bg-car-item font-black text-[17px] cursor-pointer shadow-md transition-all whitespace-nowrap"
          >
            📞 打开拨号盘
          </button>
        </div>

        <!-- 老固件+10暗码 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between">
          <div>
            <div class="text-[15px] font-bold text-car-sub">早期老版固件 (月份+10 算法)</div>
            <div class="text-[28px] font-black text-car-text mt-1 tracking-wider">
              {{ store.dynamicCode }}
            </div>
            <div class="text-[13px] text-car-sub mt-1">若加 5 暗码提示错误，请尝试此备选暗码</div>
          </div>
          <button 
            @click="openDialer"
            class="h-[58px] px-6 rounded-2xl bg-car-card border-2 border-car-border text-car-text hover:border-car-border-light font-black text-[17px] cursor-pointer shadow-md transition-all whitespace-nowrap"
          >
            📞 打开拨号盘
          </button>
        </div>
      </div>
    </FeatureCard>

    <!-- 4. 工具箱开机自启动与后台常驻 -->
    <FeatureCard 
      title="4. 工具箱开机自启动与后台守护"
      desc="控制车机上电开机后是否在后台静默运行工具箱。独立生效，不影响底层车身硬件监听。"
    >
      <div class="grid grid-cols-2 gap-4">
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text">开机自启静默守护</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">系统完全开机后自动拉起后台服务并展示桌面胶囊</div>
          </div>
          <MatrixButton 
            :title="store.deviceInfo.autostart ? '已开启' : '已关闭'"
            :active="store.deviceInfo.autostart"
            @click="toggleAutostart"
          />
        </div>

        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text">自启状态说明</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">如需保持悬浮窗常驻，建议保持开启</div>
          </div>
          <div class="p-3 bg-car-card border border-car-border rounded-xl text-[14px] text-car-sub font-bold leading-relaxed">
            工具箱采用轻量级前台守护进程，开机自启内存占用低于 15MB，零唤醒锁，对车机性能与蓄电池 0 损耗。
          </div>
        </div>
      </div>
    </FeatureCard>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { store, bridge, showToast } from '../store';
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';

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

function openDialer() {
  bridge.call('openDialer');
}

function toggleAutostart() {
  const next = !store.deviceInfo.autostart;
  store.deviceInfo.autostart = next;
  bridge.call('setAutostartEnabled', next);
  showToast('开机自启动已' + (next ? '开启' : '关闭'));
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
