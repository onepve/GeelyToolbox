<template>
  <div class="flex flex-col space-y-6 shrink-0">
    <!-- 全国实时油价与调价日历 (置顶首位，车主进入系统维护秒查油价，免滑动翻页) -->
    <FeatureCard 
      title="全国实时油价与调价日历"
      desc="监测各省发改委最新最高零售限价，提供调价周期倒计时与加油算账建议。"
      helpTitle="【功能指南】全国实时油价与调价日历"
      helpText="1. 官方限价：&#10;基于国家发改委最新发布的各省成品油最高零售限价，同省同价。&#10;&#10;2. 调价倒计时：&#10;按照国家发改委 10 个工作日调价周期倒计时，提前提供涨跌预期预警。&#10;&#10;3. 省钱算账：&#10;结合缤越 COOL 45L 油箱容积自动折算加满一箱所需成本变动。" helpTip="点击可查看全国 31 省份 92#/95#/98#/柴油价格及调价详情。"
    >
      <div class="w-full flex items-center justify-between p-4 rounded-2xl bg-car-item border border-car-border">
        <div class="flex items-center space-x-4">
          <div class="flex items-center space-x-2">
            <span class="text-[26px]">⛽</span>
            <div class="flex flex-col">
              <div class="flex items-center space-x-2">
                <span class="text-[19px] font-black text-car-text">{{ currentProvinceName }}</span>
                <span class="text-[13px] px-2 py-0.5 rounded bg-car-card text-amber-300 font-extrabold border border-car-border">当前地区</span>
              </div>
              <span class="text-[13px] text-car-sub font-bold mt-0.5">发改委限价 · {{ store.oilPrice.nextAdjustment.lastAdjustmentDate }} 生效</span>
            </div>
          </div>

          <div class="h-10 w-px bg-car-border"></div>

          <div class="flex items-center space-x-3">
            <div class="flex flex-col">
              <span class="text-[12.5px] text-car-sub font-bold">92# 汽油</span>
              <span class="text-[20px] font-black text-car-text font-mono leading-none">¥{{ currentOilP92 }}<span class="text-[13px] font-normal text-car-sub">/L</span></span>
            </div>
            <div class="flex flex-col">
              <span class="text-[12.5px] text-car-sub font-bold">95# 汽油</span>
              <span class="text-[20px] font-black text-car-text font-mono leading-none">¥{{ currentOilP95 }}<span class="text-[13px] font-normal text-car-sub">/L</span></span>
            </div>
          </div>

          <div class="h-10 w-px bg-car-border"></div>

          <div class="flex flex-col">
            <div class="flex items-center space-x-1.5">
              <span class="text-[13px] text-rose-400 font-black">下轮预期: {{ store.oilPrice.nextAdjustment.predictedLiter }}元/L</span>
              <span class="text-[12px] px-2 py-0.5 rounded-full bg-car-card text-rose-300 font-extrabold border border-car-border">
                距调价仅剩 {{ oilDaysLeft }} 天
              </span>
            </div>
            <span class="text-[12.5px] text-car-sub font-bold mt-0.5">调价日: {{ store.oilPrice.nextAdjustment.dateLabel }}</span>
          </div>
        </div>

        <div class="flex items-center space-x-3 shrink-0">
          <button 
            @click="onRefreshOilPrice"
            :disabled="store.oilPrice.isSyncing"
            class="min-h-[56px] px-5 rounded-xl border-2 border-car-border bg-car-item text-car-text font-black text-[16px] cursor-pointer hover:border-car-border-light transition-all shadow-md flex items-center space-x-2 whitespace-nowrap"
          >
            <svg class="w-4 h-4 text-car-sub" :class="{'animate-spin text-car-accent': store.oilPrice.isSyncing}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/>
            </svg>
            <span>{{ store.oilPrice.isSyncing ? '同步中...' : '刷新油价' }}</span>
          </button>

          <button 
            @click="openModal('oilPrice')"
            class="min-h-[56px] px-6 rounded-xl border-2 border-car-accent bg-car-item text-car-accent font-black text-[16.5px] cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 transition-all shadow-md flex items-center whitespace-nowrap"
          >
            <span>全国详情 ➔</span>
          </button>
        </div>
      </div>
    </FeatureCard>

    <FloatingView />

    <!-- 系统底层维护 4 大核心功能矩阵 (2x2 网格车规对称排版) -->
    <div class="grid grid-cols-2 gap-4">
      <!-- 1. 运行与守护日志 -->
      <FeatureCard 
        title="运行与守护日志"
        desc="实时采集车门、挡位与方控信号记录，支持独立清空与一键全量导出。"
        helpTitle="【功能指南】运行与守护日志"
        helpText="1. 运行记录：&#10;自动记录车门、挡位、方向盘按键等状态，遇到功能异常时可一键导出给作者排查。&#10;&#10;2. 隐私安全保护：&#10;日志导出时会自动抹除车牌、手机号、行车轨迹等敏感信息，充分保护车主隐私。&#10;&#10;3. 不占存储：&#10;平时仅保存在临时运行内存中，不占车机存储空间，重启后自动释放。" helpTip="日常无需理会，只有在排查问题时按需导出即可。"
      >
        <button 
          @click="openLogModal"
          class="w-full min-h-[72px] rounded-2xl border-2 border-car-border bg-car-item text-car-text font-black text-[18px] cursor-pointer hover:border-car-border-light transition-all shadow-sm flex items-center justify-center whitespace-nowrap"
        >
          <span>查看与管理守护日志</span>
        </button>
      </FeatureCard>

      <!-- 2. 车机 ADB 交互终端 (原应用高级管理已移至应用安装) -->
      <FeatureCard 
        title="车机 ADB 交互终端"
        desc="本地 ADB 交互终端，支持执行系统 Shell 指令，用于进阶调试与底层诊断。"
        helpTitle="【功能指南】车机 ADB 交互终端"
        helpText="1. 调试终端：&#10;为高级玩车用户提供的系统命令行窗口，方便输入指令调试系统。&#10;&#10;2. 核心防护：&#10;已开启系统级安全保护，防止误删车机核心系统组件。&#10;&#10;3. 应用管理：&#10;普通软件的安装、卸载和冻结，建议直接在「应用安装」页面操作。" helpTip="仅建议熟悉安卓命令行的进阶车主使用。"
      >
        <button 
          @click="openDeepTools"
          class="w-full min-h-[72px] rounded-2xl border-2 border-car-accent bg-car-item text-car-text font-black text-[18px] cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 transition-all shadow-md flex items-center justify-center whitespace-nowrap"
        >
          <span>打开 ADB 交互终端</span>
        </button>
      </FeatureCard>

      <!-- 3. 整车硬件冷重启控制台 (紧凑饱满) -->
      <FeatureCard 
        title="整车硬件冷重启"
        desc="彻底掉电重置车机 MCU 与安卓核心，白名单与底层服务立即生效。"
        helpTitle="【功能指南】整车硬件冷重启"
        helpText="1. 彻底重启车机：&#10;像拔插钥匙一样彻底断电重启中控屏幕和底层系统，解决系统卡顿或刚改的配置没生效的问题。&#10;&#10;2. 安全提醒：&#10;行车过程中严禁操作！请在车辆安全停稳、挂入 P 挡后再点击重启。&#10;&#10;3. 重启耗时：&#10;整个过程大约 25~35 秒，期间屏幕暂时黑屏属于正常现象，请耐心等待重新点亮。" helpTip="车辆停稳挂 P 挡后再点重启，期间黑屏请耐心等待。"
      >
        <button 
          @click="confirmHardReboot"
          class="w-full min-h-[72px] rounded-2xl border-2 border-car-border hover:border-rose-500/60 bg-car-item text-car-text font-black text-[18px] cursor-pointer transition-all shadow-sm flex items-center justify-center whitespace-nowrap"
        >
          <span>完整硬件冷重启 (reboot)</span>
        </button>
      </FeatureCard>

      <!-- 4. 工具箱开机自启动与后台守护 -->
      <FeatureCard 
        title="工具箱开机自启动与后台守护"
        desc="控制车机上电开机后在后台静默运行工具箱。出厂默认常驻开启，各联动项由各自开关独立管控。"
        helpTitle="【功能指南】工具箱开机自启动与后台守护"
        helpText="1. 开机后台自启：&#10;车辆打火通电后，工具箱自动在后台安静启动，让各项自动化联动功能上车就能直接用。&#10;&#10;2. 独立控制：&#10;开机自启开启后，各个联动功能（如 D 挡 360、开门关门播报等）依然由各自的开关独立决定是否工作。&#10;&#10;3. 推荐常开：&#10;默认保持常开，确保上车后一切功能运转顺畅。" helpTip="这是自动化功能的基础开关，建议保持开启。"
      >
        <div class="w-full min-h-[72px] rounded-2xl border-2 border-car-accent bg-car-item text-car-accent font-black text-[18px] shadow-sm flex items-center justify-center space-x-2">
          <StatusDot size="md" color="ok" :glow-px="8" />
          <span>开机自启静默守护: 已默认常驻启用</span>
        </div>
      </FeatureCard>
    </div>

    <!-- 7. 主题外观与昼夜模式 (液态玻璃 4 色 × 白夜三档) -->
    <FeatureCard 
      title="主题外观与昼夜模式"
      desc="四套液态玻璃配色任选，昼夜三档切换；顶栏太阳/月亮按钮可随时一键快捷翻转。"
      helpTitle="【功能指南】主题外观与昼夜模式"
      helpText="1. 外观配色：&#10;提供清晨蓝、琥珀棕、晨曦绿、樱语粉 4 套专属主题，随心更换。&#10;&#10;2. 昼夜模式：&#10;支持根据时间自动切换（白天明亮、晚上柔和不刺眼），也可以强制锁定白天或黑夜。&#10;&#10;3. 自动记忆：&#10;熄火重新通电后依然保持您选好的主题外观。" helpTip="可以点击顶栏的太阳/月亮图标随时快速切换白天/黑夜模式。"
    >
      <div class="w-full flex flex-col space-y-4">
        <!-- 7 个近正方形主题方块（3 昼夜档 + 4 配色），居中等距排布，杜绝扁长拉伸 -->
        <div class="flex flex-wrap justify-center items-stretch -m-2 w-full">
          <button
            v-for="m in MODES"
            :key="m"
            @click="onSetMode(m)"
            :class="[
              'm-2 w-[150px] h-[150px] rounded-3xl border-2 cursor-pointer transition-all flex flex-col items-center justify-center space-y-2.5',
              store.theme.mode === m
                ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-lg'
                : 'bg-car-card border-car-border text-car-text hover:border-car-border-light'
            ]"
          >
            <span class="text-[23px] font-black leading-none whitespace-nowrap">{{ MODE_LABELS[m] }}</span>
            <span class="text-[14px] font-bold text-car-sub leading-none whitespace-nowrap">{{ MODE_SUBS[m] }}</span>
          </button>
          <div class="hidden xl:block w-px self-stretch m-2 bg-car-border" aria-hidden="true"></div>
          <button
            v-for="p in PALETTES"
            :key="p"
            @click="onSetPalette(p)"
            :class="[
              'm-2 w-[150px] h-[150px] rounded-3xl border-2 cursor-pointer transition-all flex flex-col items-center justify-center space-y-2.5',
              store.theme.palette === p
                ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-lg'
                : 'bg-car-card border-car-border text-car-text hover:border-car-border-light'
            ]"
          >
            <span class="w-11 h-11 rounded-full border border-black/20 shadow-md shrink-0" :style="{ background: PALETTE_DOT[p] }"></span>
            <span class="text-[23px] font-black leading-none whitespace-nowrap">{{ PALETTE_FULL[p] }}</span>
            <span class="text-[14px] font-bold text-car-sub leading-none whitespace-nowrap">{{ PALETTE_LABELS[p] }}色主题</span>
          </button>
        </div>
      </div>
    </FeatureCard>

    <!-- 启动首屏默认视图 -->
    <FeatureCard 
      title="启动首屏默认视图"
      desc="设置缤越助手每次启动时默认呈现的主功能页。配置后立即生效并持久记忆。"
      helpTitle="【功能指南】启动首屏默认视图"
      helpText="1. 智能跟随：&#10;选择「记忆上次退出时页面」后，每次启动自动恢复到您上次停留的功能页。&#10;&#10;2. 固定主菜单：&#10;也可以固定为方控按键、车身联动、车载语音、精选商城、车载音频、特权安装或系统维护等任意页面。&#10;&#10;3. 立即生效：&#10;点击相应项即刻完成设置并永久记忆，下次打开软件直接进入该页面。" helpTip="建议经常传歌/装软件的车主设置为「特权安装」，常玩车机设为「精选商城」。"
    >
      <div class="w-full flex flex-col space-y-3 overflow-hidden">
        <!-- 智能跟随选项 -->
        <button
          @click="onSelectStartupNav('remember')"
          :class="[
            'w-full min-h-[58px] px-6 rounded-2xl border-2 cursor-pointer transition-all flex items-center justify-between shadow-sm',
            store.settings.startup_nav === 'remember'
              ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/30 shadow-md font-black'
              : 'bg-car-card border-car-border text-car-text hover:border-car-border-light font-bold'
          ]"
        >
          <div class="flex items-center space-x-2.5">
            <span class="text-[17px] text-amber-400">★</span>
            <span class="text-[17px]">记忆上次退出时页面 (智能跟随)</span>
          </div>
          <span class="text-[13.5px] text-car-sub">上次停留: {{ getNavName(store.settings.last_active_nav) }}</span>
        </button>

        <!-- 7 个固定主菜单网格 -->
        <div class="w-full flex flex-wrap">
          <div
            v-for="item in STARTUP_NAV_ITEMS"
            :key="item.id"
            class="p-1 w-1/4"
          >
            <button
              @click="onSelectStartupNav(item.id)"
              :class="[
                'w-full min-h-[62px] rounded-2xl border-2 cursor-pointer transition-all flex flex-col items-center justify-center space-y-1 shadow-sm',
                store.settings.startup_nav === item.id
                  ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-md'
                  : 'bg-car-card border-car-border text-car-text hover:border-car-border-light'
              ]"
            >
              <span class="text-[16.5px] font-black leading-none">{{ item.name }}</span>
              <span class="text-[12.5px] font-bold text-car-sub leading-none">{{ item.desc }}</span>
            </button>
          </div>
        </div>
      </div>
    </FeatureCard>

    <!-- 底部运维与安装说明 -->
    <div class="bg-car-item border border-car-border rounded-2xl p-5 text-[14.5px] text-car-sub font-bold leading-relaxed space-y-1.5 shadow-sm">
      <div class="text-[16px] text-car-text font-black mb-1 flex items-center">
        车机底层维护铁律与核心原理说明：
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
import StatusDot from '../components/StatusDot.vue';
import { store, bridge, openModal, showToast, setStartupNav, refreshOilPrices } from '../store';
import { ref, computed } from 'vue';
import FloatingView from './FloatingView.vue';
import { MODES, MODE_LABELS, PALETTES, PALETTE_LABELS, PALETTE_DOT, setMode, setPalette } from '../theme/themes';
import { openAppstoreFlow } from '../utils/appstoreFreeze';
import { getDaysToAdjustment } from '../utils/oilPriceData';

// 方块副标题：昼夜档标注时段语义，配色卡标注主题全名
const MODE_SUBS = { auto: '6:00~17:59', day: '常驻白天', night: '常驻黑夜' };
const PALETTE_FULL = { blue: '清晨蓝', brown: '琥珀棕', green: '晨曦绿', pink: '樱语粉', gold: '曜石金' };

function onSetMode(v) {
  setMode(v);
  showToast(`昼夜模式：${MODE_LABELS[v] || v}`);
}

function onSetPalette(v) {
  setPalette(v);
  showToast(`已切换「${PALETTE_LABELS[v] || v}」玻璃主题`);
}

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


function toggleAutostart() {
  const next = !store.deviceInfo.autostart;
  store.deviceInfo.autostart = next;
  bridge.call('setAutostartEnabled', next);
  showToast(next ? '已开启车辆启动自动运行' : '已关闭车辆启动自动运行');
}

// 油价概览卡片状态
const currentProvinceName = computed(() => store.oilPrice.selectedProvince || '浙江');
const currentOilData = computed(() => {
  const p = currentProvinceName.value;
  return store.oilPrice.regionalPrices[p] || store.oilPrice.regionalPrices['浙江'];
});
const currentOilP92 = computed(() => currentOilData.value?.p92 ? currentOilData.value.p92.toFixed(2) : '8.26');
const currentOilP95 = computed(() => currentOilData.value?.p95 ? currentOilData.value.p95.toFixed(2) : '8.79');
const oilDaysLeft = computed(() => getDaysToAdjustment(store.oilPrice.nextAdjustment.date));

// 启动首屏默认视图配置
const STARTUP_NAV_ITEMS = [
  { id: 'wheel', name: '方控按键', desc: '核心改装 (默认)' },
  { id: 'link', name: '车身联动', desc: '360/车速' },
  { id: 'body', name: '车载语音', desc: '语音播报' },
  { id: 'store', name: '精选商城', desc: '专车应用' },
  { id: 'audio', name: '车载音频', desc: '语音/蓝牙' },
  { id: 'install', name: '特权安装', desc: '暗码/快传' },
  { id: 'system', name: '系统维护', desc: '维护/油价' }
];

function getNavName(id) {
  const map = {
    wheel: '方控按键',
    link: '车身联动',
    body: '车载语音',
    store: '精选商城',
    audio: '车载音频',
    install: '特权安装',
    system: '系统维护'
  };
  return map[id] || '方控按键';
}

function onSelectStartupNav(navId) {
  setStartupNav(navId);
}

function onRefreshOilPrice() {
  refreshOilPrices(true);
}
</script>
