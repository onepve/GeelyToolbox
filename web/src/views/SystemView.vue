<template>
  <div class="flex flex-col space-y-6">
    <!-- 仅在应用商店未冻结时展示的警示横幅 (已冻结时自动隐藏，保持界面清爽) - 布局完全复刻 WheelView 方控总开关黄金分栏 -->
    <div 
      v-if="!store.deviceInfo.appstore_frozen" 
      class="bg-car-card border-2 border-amber-500/70 rounded-3xl p-5 min-h-[106px] shadow-xl flex items-center justify-between transition-all"
    >
      <div class="w-[60%] max-w-[60%] flex flex-col space-y-1.5 shrink-0">
        <div class="flex items-center space-x-3">
          <span class="w-3.5 h-3.5 rounded-full shadow-md shrink-0 bg-amber-400 shadow-[0_0_10px_#F59E0B]"></span>
          <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">检测到吉利应用商店处于未冻结状态</span>
          <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-item border-car-border text-car-text inline-flex items-center shrink-0 shadow-sm"><span class="w-2.5 h-2.5 rounded-full mr-2 bg-amber-400 shadow-[0_0_6px_#F59E0B]"></span>建议处置</span>
        </div>
        <div class="text-[14.5px] text-car-sub font-bold leading-normal">
          原厂商店运行会破坏白名单策略，直接导致第三方软件无法安装，强烈建议立即冻结锁定！
        </div>
      </div>

      <div class="shrink-0 w-[230px]">
        <button
          @click="confirmFreezeStore"
          class="w-full h-[74px] px-4 py-2 rounded-2xl border-2 cursor-pointer transition-all shadow-md flex flex-col items-center justify-center text-center bg-car-item border-amber-500/80 hover:border-amber-400 ring-2 ring-amber-500/20"
        >
          <span class="text-[18.5px] font-black text-car-text tracking-wide whitespace-nowrap">一键安全冻结</span>
          <span class="text-[12.5px] font-bold mt-1 whitespace-nowrap text-amber-400">点击锁定商店防破坏</span>
        </button>
      </div>
    </div>

    <!-- 系统底层维护 6 大核心功能矩阵 (3 列自适应车规排版) -->
    <div class="grid grid-cols-3 gap-4">
      <!-- 1. 整车硬件冷重启控制台 (紧凑饱满) -->
      <FeatureCard 
        title="1. 整车硬件冷重启"
        desc="彻底掉电重置车机 MCU 与安卓核心，白名单与底层服务立即生效。"
      >
        <button 
          @click="confirmHardReboot"
          class="w-full min-h-[72px] rounded-2xl border-2 border-car-border hover:border-rose-500/60 bg-car-item text-car-text font-black text-[18px] cursor-pointer transition-all shadow-sm flex items-center justify-center whitespace-nowrap"
        >
          <span>完整硬件冷重启 (reboot)</span>
        </button>
      </FeatureCard>

      <!-- 2. 车机深度工具箱 & ADB 控制台 (从顶栏移至此处) -->
      <FeatureCard 
        title="2. 车机深度工具箱 & ADB"
        desc="内置本地 ADB Client 2000 端口，提供命令行交互与系统维护。"
      >
        <button 
          @click="openDeepTools"
          class="w-full min-h-[72px] rounded-2xl border-2 border-car-accent bg-car-item text-car-text font-black text-[18px] cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 transition-all shadow-md flex items-center justify-center whitespace-nowrap"
        >
          <span>打开 ADB 交互控制台</span>
        </button>
      </FeatureCard>

      <!-- 3. 安装白名单属性放行 -->
      <FeatureCard 
        title="3. 第三方 APK 放行白名单"
        desc="注入 sys.jsbd.apk_verify=1 属性，解除系统级安装包签名校验限制。"
      >
        <button 
          @click="confirmToggleWhitelist"
          :class="[
            'w-full min-h-[72px] rounded-2xl border-2 font-black text-[18px] cursor-pointer transition-all shadow-sm flex items-center justify-center whitespace-nowrap',
            store.deviceInfo.whitelist 
              ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' 
              : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
          ]"
        >
          <span>{{ store.deviceInfo.whitelist ? '白名单: 已放行' : '白名单: 未放行 (点击开启)' }}</span>
        </button>
      </FeatureCard>

      <!-- 4. 运行与安全审计日志 -->
      <FeatureCard 
        title="4. 运行与守护日志"
        desc="实时采集车门、挡位与方控信号记录，支持独立清空与离线导出。"
      >
        <button 
          @click="openLogModal"
          class="w-full min-h-[72px] rounded-2xl border-2 border-car-border bg-car-item text-car-text font-black text-[18px] cursor-pointer hover:border-car-border-light transition-all shadow-sm flex items-center justify-center whitespace-nowrap"
        >
          <span>查看运行与守护日志</span>
        </button>
      </FeatureCard>

      <!-- 5. 应用商店管理 (带二次校验) -->
      <FeatureCard 
        title="5. 应用商店状态管理"
        desc="未冻结会破坏白名单导致无法安装第三方软件，必须冻结进行锁定。"
      >
        <button 
          @click="confirmToggleAppstore"
          :class="[
            'w-full min-h-[72px] rounded-2xl border-2 font-black text-[18px] cursor-pointer transition-all shadow-sm flex items-center justify-center whitespace-nowrap',
            store.deviceInfo.appstore_frozen 
              ? 'bg-car-item border-emerald-500/60 text-car-text ring-2 ring-emerald-500/20' 
              : 'bg-car-item border-amber-500/70 text-car-text ring-2 ring-amber-500/20'
          ]"
        >
          <span>{{ store.deviceInfo.appstore_frozen ? '商店: 已冻结 (锁定白名单)' : '商店: 未冻结 (点击安全冻结)' }}</span>
        </button>
      </FeatureCard>

      <!-- 6. 工具箱开机自启与桌面胶囊 -->
      <FeatureCard 
        title="6. 工具箱开机自启与桌面胶囊"
        desc="控制开机后是否在后台静默运行工具箱并常驻桌面悬浮胶囊。独立生效，不影响语音与方控。"
      >
        <button 
          @click="toggleAutostart"
          :class="[
            'w-full min-h-[72px] rounded-2xl border-2 font-black text-[18px] cursor-pointer transition-all shadow-sm flex items-center justify-center whitespace-nowrap',
            store.deviceInfo.autostart 
              ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' 
              : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
          ]"
        >
          <span>{{ store.deviceInfo.autostart ? '桌面胶囊自启: 已开启' : '桌面胶囊自启: 已关闭 (开机不弹)' }}</span>
        </button>
      </FeatureCard>
    </div>

    <!-- 7. 闲置自动屏保：主页面闲置 N 秒后自动进入原厂屏保（原生 Service 调用，不依赖 ADB） -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-6 shadow-2xl">
      <!-- 顶部黄金分栏：左详尽说明 + 右 230px 紧凑开关磁贴 -->
      <div class="flex items-center justify-between">
        <div class="w-[60%] max-w-[60%] flex flex-col space-y-1.5 shrink-0">
          <div class="flex items-center space-x-3">
            <span class="w-3.5 h-3.5 rounded-full shadow-md shrink-0 bg-car-accent"></span>
            <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">7. 闲置自动屏保</span>
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
            当前设定: {{ ssSeconds }} 秒
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
            @change="saveSeconds"
            class="flex-1 accent-car-accent h-2.5 bg-car-card rounded-lg cursor-pointer"
          />
          <span class="text-[14px] text-car-sub font-bold whitespace-nowrap">{{ ssMaxSeconds }} 秒 (更从容)</span>
          <div class="flex space-x-2 shrink-0">
            <button
              v-for="preset in ssPresets"
              :key="preset"
              @click="setSecondsPreset(preset)"
              :class="[
                'px-3 py-1.5 text-[13.5px] font-black rounded-xl border transition-all cursor-pointer whitespace-nowrap',
                ssSeconds === preset
                  ? 'bg-car-card border-car-accent text-car-text'
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              {{ preset }}秒
            </button>
          </div>
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

    <!-- 底部运维与避坑指引 -->
    <div class="bg-car-item border border-car-border rounded-2xl p-5 text-[14.5px] text-car-sub font-bold leading-relaxed space-y-1.5 shadow-sm">
      <div class="text-[16px] text-car-text font-black mb-1 flex items-center">
        <span class="mr-2">💡</span> 座舱底层维护铁律与核心原理说明：
      </div>
      <div>• <b>冷重启原理</b>：彻底断电重启 MCU 与 Framework，彻底杜绝开门播报延迟与系统卡顿；</div>
      <div>• <b>应用商店与白名单</b>：原厂应用商店运行会破坏白名单策略导致第三方软件无法安装，必须保持冻结锁定；</div>
      <div>• <b>三权分立架构</b>：桌面胶囊自启、车身语音总开关、方控接管总开关各自独立生效，互不影响与捆绑；</div>
      <div>• <b>ADB 安全边界</b>：深度终端已做系统核心保护，严禁自行卸载系统 Framework 核心组件。</div>
    </div>
  </div>
</template>

<script setup>
import FeatureCard from '../components/FeatureCard.vue';
import { store, bridge, openModal, showToast } from '../store';
import { ref, computed, onMounted, onUnmounted } from 'vue';

function confirmHardReboot() {
  openModal('confirm', {
    title: '整车完整硬件冷重启 (reboot)',
    desc: '即将对整车中控硬件执行完全掉电冷启动 (reboot)，耗时约 25~35 秒。白名单、音频通道与系统框架将彻底刷新生效。',
    tip: '【安全警示】严禁在行车行驶过程中执行整车冷重启操作！请确保车辆已安全停稳。',
    isDanger: true,
    confirmText: '确认立即掉电重启',
    onConfirm: () => {
      bridge.call('hardReboot');
      showToast('已下发整车冷重启指令，中控即将断电重启...');
    }
  });
}

function openDeepTools() {
  openModal('deepTools');
}

function openLogModal() {
  store.modals.log = true;
}

function confirmFreezeStore() {
  openModal('confirm', {
    title: '冻结吉利应用商店 (锁定白名单)',
    desc: '冻结吉利原厂应用商店后，将永久锁定第三方软件安装白名单，确保第三方应用能够顺利安装并正常运行。',
    tip: '【强烈建议】请始终保持应用商店冻结状态。后续需要时可随时在此解冻恢复。',
    isDanger: false,
    onConfirm: () => {
      bridge.call('toggleFreezeAppStore', true);
      showToast('正在执行应用商店安全冻结...');
    }
  });
}

function confirmToggleAppstore() {
  const isFrozen = store.deviceInfo.appstore_frozen;
  if (isFrozen) {
    // 当前已冻结，解冻需要二次警告确认
    openModal('confirm', {
      title: '解冻恢复吉利应用商店',
      desc: '解冻原厂应用商店后，商店将破坏系统白名单策略，直接导致第三方软件无法安装，且已有软件可能被后台静默拦截。是否确认解冻？',
      tip: '【高危警告】解冻后将直接影响白名单导致无法安装第三方软件！仅在急需使用官方商店时临时开启。',
      isDanger: true,
      confirmText: '确认解冻',
      onConfirm: () => {
        bridge.call('toggleFreezeAppStore', false);
        showToast('正在解冻恢复吉利应用商店...');
      }
    });
  } else {
    // 当前未冻结，引导冻结
    confirmFreezeStore();
  }
}

function confirmToggleWhitelist() {
  const next = !store.deviceInfo.whitelist;
  openModal('confirm', {
    title: next ? '开启第三方 APK 放行白名单' : '关闭第三方 APK 放行白名单',
    desc: next 
      ? '即将注入 sys.jsbd.apk_verify=1 属性，解除车机原生 PackageInstaller 的签名校验限制，允许自由安装第三方软件。'
      : '关闭白名单后，安装第三方 APK 将恢复系统原生限制并可能报解析包失败。',
    tip: '建议始终保持开启状态。',
    isDanger: !next,
    onConfirm: () => {
      bridge.call('toggleWhitelist');
      showToast(next ? '正在开启第三方白名单...' : '正在关闭第三方白名单...');
    }
  });
}

function toggleAutostart() {
  const next = !store.deviceInfo.autostart;
  store.deviceInfo.autostart = next;
  bridge.call('setAutostartEnabled', next);
  showToast(next ? '已开启车辆启动自动运行' : '已关闭车辆启动自动运行');
}

// ==================== 7. 闲置自动屏保 ====================
const ssEnabled = ref(false);
const ssSeconds = ref(20);
const ssMinSeconds = ref(10);
const ssMaxSeconds = ref(30);
const ssHomeOnly = ref(true);
const ssUsageAccess = ref(false);
const ssChannel = ref('未启动');
const ssIdleMs = ref(-1);
const ssIdleRaw = ref('');
const ssFailReason = ref('');
const ssChannelAReady = ref(false);
const ssForeground = ref('查询中');
const ssPresets = [10, 15, 20, 30];
let ssTimer = null;

// 进度条：按当前设定值在最小~最大区间内的占比实时联动
const ssProgressPercent = computed(() => {
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
    if (typeof data.seconds === 'number') ssSeconds.value = data.seconds;
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
  saveScreensaverConfig({ seconds: v });
  showToast('闲置时长已设为 ' + v + ' 秒');
}

function setSecondsPreset(preset) {
  ssSeconds.value = preset;
  saveSeconds();
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
  // 每 3 秒刷新一次运行状态与闲置读数（与 Java 侧检测同频）
  ssTimer = setInterval(loadScreensaverConfig, 3000);
});

onUnmounted(() => {
  if (ssTimer) {
    clearInterval(ssTimer);
    ssTimer = null;
  }
});
</script>
