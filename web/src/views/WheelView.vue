<template>
  <div class="flex flex-col space-y-4">
    <!-- 核心：方向盘按键方控接管总开关 - 核心功能首屏 (左右分栏车规黄金磁贴) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-5 min-h-[106px] shadow-xl flex items-center justify-between transition-all">
      <div class="w-[60%] max-w-[60%] flex flex-col space-y-1.5 shrink-0">
        <div class="flex items-center space-x-3">
          <StatusDot size="lg" :color="store.vehicleAuto.wheel_master_switch ? 'ok' : 'off'" :glow-px="10" class="shadow-md" />
          <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">方向盘按键方控接管总开关</span>
          <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-item border-car-border text-car-text inline-flex items-center shrink-0 shadow-sm">
            <StatusDot class="mr-2" size="sm" :color="store.vehicleAuto.wheel_master_switch ? 'ok' : 'off'" />
            {{ store.vehicleAuto.wheel_master_switch ? '方控接管已启用' : '方控已彻底放行 (不干涉)' }}
          </span>
        </div>
        <div class="text-[14.5px] text-car-sub font-bold leading-normal">
          全面接管静音键、Mode键、切歌键与主页返回键。单击即发，零延迟触发。
        </div>
      </div>

      <div class="shrink-0 w-[230px]">
        <BaseButton
          variant="master"
          :active="store.vehicleAuto.wheel_master_switch"
          @click="toggleWheelMasterSwitch"
        >
          <span class="text-[18.5px] font-black text-car-text tracking-wide whitespace-nowrap">
            {{ store.vehicleAuto.wheel_master_switch ? '方控接管已开启' : '方控接管已关闭' }}
          </span>
          <span :class="['text-[12.5px] font-bold mt-1 whitespace-nowrap', store.vehicleAuto.wheel_master_switch ? 'text-car-accent' : 'text-car-sub']">
            {{ store.vehicleAuto.wheel_master_switch ? '点击切换为彻底放行' : '点击开启按键接管' }}
          </span>
        </BaseButton>
      </div>
    </div>

    <!-- 车型图解与协议指示区 (缤越 COOL / SX-0017 原厂按键分布) -->
    <div class="bg-car-item border border-car-border rounded-2xl p-5 shadow-sm">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center">
          <StatusDot class="mr-3" size="md" color="accent" :glow-px="8" />
          <div class="flex flex-col">
            <span class="text-[18px] font-black text-car-text">吉利缤越 COOL (IHU516G / E02) 原厂方控图解</span>
            <span class="text-[14px] text-car-sub font-bold mt-0.5">
              右方向盘多媒体控制区 (SX-0017)：静音、切歌、MODE、返回键全量打通单击极速接管
            </span>
          </div>
        </div>
        <div class="flex items-center space-x-3">
          <BaseButton variant="chipLg" :active="floatingEnabled" @click="toggleFloating()">
            <StatusDot class="mr-2.5" size="sm" :color="floatingEnabled ? 'ok' : 'off'" />
            {{ floatingEnabled ? '悬浮图解：开' : '悬浮图解：关' }}
          </BaseButton>
          <BaseButton variant="ghost" @click="toggleDiagram()">
            {{ showDiagram ? '收起图解' : '展开图解' }}
          </BaseButton>
        </div>
      </div>

      <!-- 图解内容展示区 -->
      <div v-if="showDiagram" class="mt-4 pt-4 border-t border-car-border/60 flex items-start space-x-6">
        <!-- 原厂方向盘按键高清图示 (沉稳车规底座，绝非刺眼白底) -->
        <div class="p-3 bg-car-item rounded-2xl shadow-md border-2 border-car-border w-[320px] min-w-[320px] max-w-[320px] shrink-0">
          <img 
            :src="wheelGuideImg" 
            alt="吉利缤越 COOL 右侧多媒体 SX-0017" 
            class="guide-img w-full h-auto rounded-xl object-contain block" 
          />
        </div>

        <!-- 按键编号与原厂定义清单 -->
        <div class="flex-1 min-w-0 flex flex-col space-y-2.5 text-[14.5px] font-bold text-car-sub">
          <div class="flex space-x-3">
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">① 主页/返回键:</span> 返回车机中控主页 / 单击自定义
            </div>
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">② 滚轮按压:</span> 媒体播放 / 暂停 (支持自定义)
            </div>
          </div>
          <div class="flex space-x-3">
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">③ 静音键:</span> 开关静音 (支持单击自定义)
            </div>
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">④ 向右选择键:</span> 下一个文件 / 下一曲 (单击自定义)
            </div>
          </div>
          <div class="flex space-x-3">
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">⑤ 自定义键:</span> 0x37 硬件按键 / 默认原厂 (单击自定义)
            </div>
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">⑥ MODE键:</span> 音源切换 / 自定义 (单击自定义)
            </div>
          </div>
          <div class="w-full p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
            <span class="text-car-accent font-black mr-1.5">⑦ 向左选择键:</span> 上一个文件 / 上一曲 (单击自定义)
          </div>
        </div>
      </div>
    </div>

    <!-- 1. 方向盘方控接管模式 -->
    <FeatureCard 
      title="方控按键接管模式"
      desc="彻底屏蔽原厂收音机伴听抢占广播，支持米小江优先协同或控制台全量独立接管。若两个软件都装，选米小江优先互不冲突。"
      helpTitle="【功能指南】方控接管模式、EAS 蓝牙硬件通道与仪表投递"
      helpText="1. 接管模式选择：&#10;• 控制台独立接管（推荐）：方向盘按键直接控制切歌与播放，自动唤醒车载喇叭，体验最流畅。&#10;• 米小江方控优先：如果车机上装了米小江 CarMedia，选这项让它优先处理切歌，两者互不打扰。&#10;• 恢复原厂默认：不拦截方向盘按键，全部交回原车控制。&#10;&#10;2. 蓝牙无声问题解决：&#10;独立接管模式下，手机连车机蓝牙放微信语音或音乐没声音时，会自动帮您接通车载喇叭。&#10;&#10;3. 仪表与屏保显示：&#10;可自由选择是否把当前歌名和歌词推送到仪表盘或屏保上显示。" 
      helpTip="如果同时装了米小江请选「米小江方控优先」；只用本工具箱建议选「控制台独立接管」。"
    >
      <div class="grid grid-cols-3 gap-4" style="grid-gap: 14px; -webkit-column-gap: 14px;">
        <MatrixButton 
          title="米小江方控优先"
          subtitle="放行切歌与Mode键，工具箱补充静音键接管"
          :active="store.vehicleAuto.wheel_control_mode === 'carmedia_first'"
          @click="setWheelMode('carmedia_first')"
        />
        <MatrixButton 
          title="控制台独立接管 (推荐)"
          subtitle="全量接管切歌与按键，并自动选通原车蓝牙音频硬件通道"
          :active="store.vehicleAuto.wheel_control_mode === 'toolbox_alone' || (!store.vehicleAuto.wheel_control_mode && store.vehicleAuto.wheel_control_mode !== 'factory_default')"
          @click="setWheelMode('toolbox_alone')"
        />
        <MatrixButton 
          title="恢复原厂默认"
          subtitle="解禁原厂广播接收器，完全不拦截任何按键"
          :active="store.vehicleAuto.wheel_control_mode === 'factory_default'"
          @click="setWheelMode('factory_default')"
        />
      </div>

      <!-- 仪表盘与屏保显示扩展 (独立开关·默认关闭) -->
      <div class="mt-4 pt-4 border-t border-car-border/60 flex flex-col space-y-4">
        <div class="flex items-center justify-between p-4 rounded-2xl bg-car-item border-2 border-car-border">
          <div class="flex flex-col space-y-1">
            <span class="text-[17px] font-black text-car-text">投递播放状态至仪表盘与息屏时钟</span>
            <span class="text-[14px] text-car-sub font-bold">开启后向原厂 EAS 广播歌名与歌手，车机息屏后将显示音乐小部件卡片。默认关闭保持极简。</span>
          </div>
          <BaseButton variant="pill" :active="store.vehicleAuto.wheel_push_playback_cluster" @click="toggleClusterPlayback">
            {{ store.vehicleAuto.wheel_push_playback_cluster ? '已开启 (投递状态)' : '已关闭 (默认纯净)' }}
          </BaseButton>
        </div>

        <div class="flex items-center justify-between p-4 rounded-2xl bg-car-item border-2 border-car-border">
          <div class="flex flex-col space-y-1">
            <span class="text-[17px] font-black text-car-text">投递当前歌词至原车仪表盘 (HUD)</span>
            <span class="text-[14px] text-car-sub font-bold">开启后向吉利全液晶仪表盘中间卡片推送实时滚动歌词。默认关闭防信息刷屏。</span>
          </div>
          <BaseButton variant="pill" :active="store.vehicleAuto.wheel_push_lyrics_cluster" @click="toggleClusterLyrics">
            {{ store.vehicleAuto.wheel_push_lyrics_cluster ? '已开启 (投递歌词)' : '已关闭 (默认纯净)' }}
          </BaseButton>
        </div>
      </div>
    </FeatureCard>

    

    <!-- 3. 各按键映射配置 (右方向盘多媒体 SX-0017 物理按键逐项配置) -->
    <div class="flex flex-col space-y-4">
      <div class="flex items-center px-2">
        <StatusDot class="mr-3" size="md" color="accent" :glow-px="8" />
        <span class="text-[18px] font-black text-car-text tracking-wide">按键独立映射 (单击动作设定)</span>
        <span class="ml-3 text-[13px] font-bold text-car-sub">即按即响应 · 零延迟触发</span>
      </div>

      <!-- 双列规整网格：一排两卡，工整对齐，共三排 -->
      <div class="grid grid-cols-2 gap-4">
        <!-- ① 右方向盘主页/返回键 -->
        <WheelGestureCard
          class="!mb-0"
          key-name="back"
          card-title="右方向盘 ① 主页/返回按键"
          card-desc="对应右方向盘 ① 号返回/主页键（KeyCode 307）。支持单击自定义。可保留系统原厂返回，或映射为高德导航、360全景等。"
          helpTitle="【功能指南】主页 / 返回按键映射"
          helpText="1. 原厂功能：&#10;原车默认点按返回上一级或主页。&#10;&#10;2. 自定义映射：&#10;可自由绑定为秒开 360 全景、高德地图、打开指定应用或执行控制台快捷动作。&#10;&#10;3. 恢复原厂：&#10;若希望保持吉利系统默认返回逻辑，下拉选择「恢复原厂默认」即可。"
          helpTip="若平时习惯原车返回逻辑，建议保持「恢复原厂默认」。"
        />

        <!-- ② 右方向盘音量滚轮按压 -->
        <WheelGestureCard
          class="!mb-0"
          key-name="ok"
          card-title="右方向盘 ② 音量滚轮按压"
          card-desc="对应右方向盘 ② 号音量滚轮垂直按压（Tasker 黄金键码 0x2d）。默认单击控制媒体暂停/播放，亦可秒开 360 或高德。"
          helpTitle="【功能指南】音量滚轮按压映射"
          helpText="1. 滚轮垂直按压：&#10;右方向盘音量滚轮除了上下滚动调音量外，垂直向下按压也是独立物理按键。&#10;&#10;2. 推荐用途：&#10;默认推荐设为「播放 / 暂停媒体」，开车途中一键暂停极方便；亦可设为 360 全景或高德地图。&#10;&#10;3. 纯净极速：&#10;已剥离复杂长按判断，按压即响应，不影响滚轮正常滚动调音。"
          helpTip="推荐绑定「播放/暂停」，开车切歌听歌更顺手。"
        />

        <!-- ③ 右方向盘静音键 -->
        <WheelGestureCard
          class="!mb-0"
          key-name="mute"
          card-title="右方向盘 ③ 静音按键"
          card-desc="对应右方向盘 ③ 号静音键（KeyCode 300）。支持单击动作自定义；长按 10 秒依然是整车硬件冷重启，互不冲突！"
          helpTitle="【功能指南】静音按键映射与 10 秒冷重启"
          helpText="1. 静音键自定义：&#10;原车点按为全局静音，可改绑为打开 360 全景、高德导航、小爱同学或切歌。&#10;&#10;2. 硬件看门狗救砖：&#10;长按静音键 10 秒是吉利座舱底层的强制硬件冷重启，无论在此如何设置，该应急功能永久有效，安全无忧。"
          helpTip="长按 10 秒强制重启是车机硬件看门狗，任何设置都不会影响它。"
        />

        <!-- ⑥ 右方向盘 MODE 键 -->
        <WheelGestureCard
          class="!mb-0"
          key-name="mode"
          card-title="右方向盘 ⑥ MODE 模式键"
          card-desc="对应右方向盘 ⑥ 号 MODE 模式键（KeyCode 348）。原车切换伴听/收音机。默认单击一键秒开 360 全景，亦可改绑其他功能。"
          helpTitle="【功能指南】MODE 模式按键映射"
          helpText="1. 摆脱原厂收音机：&#10;原车点按 MODE 会强制唤醒伴听或收音机，改绑后可彻底告别讨厌的原厂收音机打扰。&#10;&#10;2. 一键秒开 360：&#10;默认推荐设为打开「360 全景」，倒车、过狭窄路段或侧方停车时一键呼出，非常实用。&#10;&#10;3. 极速响应：&#10;按压立即触发，无任何手势等待延迟。"
          helpTip="绝大多数车友强烈推荐绑定「360 全景影像」，过窄路神器。"
        />

        <!-- ④ & ⑦ 切歌双键：上一曲 / 下一曲 -->
        <FeatureCard
          class="!mb-0"
          title="右方向盘 ④ 下一曲 / ⑦ 上一曲"
          desc="对应右方向盘 ④（下一曲）与 ⑦（上一曲）切歌按键。单击即刻触发，切歌零延迟。"
          helpTitle="【功能指南】下一曲 / 上一曲切歌按键映射"
          helpText="1. 单击极速响应：&#10;方向盘右侧「上一曲 / 下一曲」按键按压即刻响应，零延迟跟手。&#10;&#10;2. 完美适配音乐软件：&#10;深度适配 QQ音乐车机版、网易云音乐、酷狗等，切歌稳定不冲突。&#10;&#10;3. 灵活改绑：&#10;如需由方控开 360 或调用其他功能，也可在此自由改绑。"
          helpTip="切歌动作建议保持「切歌(官方调度)」，切歌最稳定。"
        >
          <div class="grid grid-cols-2 gap-3.5">
            <!-- ⑦ 上一曲 -->
            <div class="bg-car-item border border-car-border rounded-2xl p-3.5 flex flex-col space-y-2.5">
              <div class="flex items-center justify-between">
                <span class="text-[15.5px] font-black text-car-text">⑦ 上一曲 (向左)</span>
                <span class="text-[12px] text-car-accent font-bold">按压即发</span>
              </div>
              <ActionSelect key-name="prev" gesture="single" />
            </div>

            <!-- ④ 下一曲 -->
            <div class="bg-car-item border border-car-border rounded-2xl p-3.5 flex flex-col space-y-2.5">
              <div class="flex items-center justify-between">
                <span class="text-[15.5px] font-black text-car-text">④ 下一曲 (向右)</span>
                <span class="text-[12px] text-car-accent font-bold">按压即发</span>
              </div>
              <ActionSelect key-name="next" gesture="single" />
            </div>
          </div>
        </FeatureCard>

        <!-- ⑤ 左方向盘自定义键 -->
        <WheelGestureCard
          class="!mb-0"
          key-name="custom"
          card-title="左方向盘 ⑤ 自定义按键"
          card-desc="对应左方向盘 ⑤ 号菱形/星号按键（KeyCode 0x37）。吉利原厂车机系统预留按键，单击秒级触发自定义功能。"
          helpTitle="【功能指南】左方向盘自定义按键映射"
          helpText="1. 专属自定义键：&#10;部分吉利车型方向盘左侧配备了 ★ 星号或 ◇ 菱形按键，原车用于快速功能联动。&#10;&#10;2. 全功能接管：&#10;可自由映射为秒开 360 全景、导航、静音、打开常用应用等。&#10;&#10;3. 原厂模式：&#10;如果您的车型该按键由原车仪表专属占用，选择「恢复原厂默认」即可放行。"
          helpTip="推荐设为「360 全景影像」，行车随时查看周边盲区。"
        />
      </div>
    </div>

    <!-- 方向盘图解悬浮对照卡：滚动设键位时默认悬浮于内容区右上角（绝不遮挡下方功能菜单）；
         支持拖放自定义位置并本地记忆，下次同位显示；原地点按隐藏 -->
    <transition name="floating-fade">
      <div
        v-if="showFloating"
        ref="floatingGuideEl"
        @pointerdown="startFloatingDrag"
        class="fixed z-30 cursor-move select-none"
        :style="{ left: floatingPos.x + 'px', top: floatingPos.y + 'px', width: '168px', touchAction: 'none' }"
        title="方向盘图解对照：拖动到任意位置，点一下隐藏"
      >
        <img
          :src="wheelGuideFloatingImg"
          alt="方控图解悬浮对照"
          class="w-full h-auto object-contain block pointer-events-none select-none"
        />
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue';
import { store, bridge, openModal, showToast } from '../store';
import { useWheelGesture } from '../composables/useWheelGesture';
import StatusDot from '../components/StatusDot.vue';
import BaseButton from '../components/BaseButton.vue';
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';
import ActionSelect from '../components/ActionSelect.vue';
import WheelGestureCard from '../components/WheelGestureCard.vue';

// 缤越 COOL 专车原厂 SX-0017 多媒体按键图解（深色车规，柔和不刺眼）
import wheelGuideImg from '../assets/steering_wheel_guide.webp';
import wheelGuideFloatingImg from '../assets/steering_wheel_guide_floating.svg';

const showDiagram = ref(false);

function toggleDiagram() {
  showDiagram.value = !showDiagram.value;
}
const FLOAT_TOGGLE_KEY = 'wheel_floating_guide_enabled';
const floatingEnabled = ref(localStorage.getItem(FLOAT_TOGGLE_KEY) !== 'false');
const showFloating = ref(false);

function toggleFloating() {
  floatingEnabled.value = !floatingEnabled.value;
  localStorage.setItem(FLOAT_TOGGLE_KEY, String(floatingEnabled.value));
  if (!floatingEnabled.value) {
    showFloating.value = false;
  } else {
    const st = document.documentElement.scrollTop || document.body.scrollTop || 0;
    showFloating.value = st > 40;
  }
}

const FLOAT_POS_KEY = 'wheel_floating_guide_pos';
const floatingGuideEl = ref(null);
const floatingPos = reactive(loadFloatingPos());

function loadFloatingPos() {
  try {
    const saved = JSON.parse(localStorage.getItem(FLOAT_POS_KEY));
    if (saved && Number.isFinite(saved.x) && Number.isFinite(saved.y)) return saved;
  } catch (e) {}
  return { x: window.innerWidth - 168 - 38, y: 88 };
}

function onWheelScroll(e) {
  const el = e && e.target;
  const st = el && el.scrollTop ? el.scrollTop : (document.documentElement.scrollTop || document.body.scrollTop || 0);
  showFloating.value = floatingEnabled.value && st > 40;
}

function startFloatingDrag(e) {
  if (!e || e.button === 2) return;
  const card = floatingGuideEl.value;
  if (!card) return;
  const w = card.offsetWidth || 168;
  const h = card.offsetHeight || 120;
  const startX = e.clientX;
  const startY = e.clientY;
  const origX = floatingPos.x;
  const origY = floatingPos.y;
  let moved = false;

  const clamp = (x, y) => ({
    x: Math.min(Math.max(8, x), window.innerWidth - w - 8),
    y: Math.min(Math.max(8, y), window.innerHeight - h - 8)
  });

  const onMove = (ev) => {
    if (Math.abs(ev.clientX - startX) + Math.abs(ev.clientY - startY) > 6) moved = true;
    const next = clamp(origX + ev.clientX - startX, origY + ev.clientY - startY);
    floatingPos.x = next.x;
    floatingPos.y = next.y;
  };
  const onUp = () => {
    document.removeEventListener('pointermove', onMove);
    document.removeEventListener('pointerup', onUp);
    if (moved) {
      localStorage.setItem(FLOAT_POS_KEY, JSON.stringify({ x: floatingPos.x, y: floatingPos.y }));
    } else {
      showFloating.value = false;
    }
  };
  document.addEventListener('pointermove', onMove);
  document.addEventListener('pointerup', onUp);
}

onMounted(() => {
  floatingPos.x = Math.min(Math.max(8, floatingPos.x), Math.max(8, window.innerWidth - 184));
  floatingPos.y = Math.min(Math.max(8, floatingPos.y), Math.max(8, window.innerHeight - 140));
  document.addEventListener('scroll', onWheelScroll, { passive: true, capture: true });
});

onBeforeUnmount(() => {
  document.removeEventListener('scroll', onWheelScroll, { capture: true });
});

const { setGestureAction } = useWheelGesture();

function toggleWheelMasterSwitch() {
  const next = !store.vehicleAuto.wheel_master_switch;
  store.vehicleAuto.wheel_master_switch = next;
  bridge.call('setVehicleAutomationSetting', 'wheel_master_switch', next);
  showToast(next ? '方控接管总开关: 已开启 (接管分发)' : '方控接管总开关: 已关闭 (彻底放行)');
}

function setWheelMode(mode) {
  store.vehicleAuto.wheel_control_mode = mode;
  bridge.call('setWheelControlStringSetting', 'wheel_control_mode', mode);
  showToast('方控模式已切换: ' + (mode === 'carmedia_first' ? '米小江优先' : (mode === 'toolbox_alone' ? '控制台独立接管' : '恢复原厂')));
}

function toggleClusterPlayback() {
  const next = !store.vehicleAuto.wheel_push_playback_cluster;
  store.vehicleAuto.wheel_push_playback_cluster = next;
  bridge.call('setVehicleAutomationSetting', 'wheel_push_playback_cluster', next);
  showToast(next ? '已开启仪表/屏保播放状态投递' : '已关闭投递，保持仪表盘与屏保纯净');
}

function toggleClusterLyrics() {
  const next = !store.vehicleAuto.wheel_push_lyrics_cluster;
  store.vehicleAuto.wheel_push_lyrics_cluster = next;
  bridge.call('setVehicleAutomationSetting', 'wheel_push_lyrics_cluster', next);
  showToast(next ? '已开启仪表盘歌词投递' : '已关闭歌词投递，保持仪表盘纯净');
}

function resetAllToFactory() {
  openModal('confirm', {
    title: '一键恢复全车原厂默认',
    desc: '将方向盘所有按键全部恢复为系统原厂默认动作，工具箱将不拦截任何物理按键。是否确认？',
    isDanger: true,
    confirmText: '确认恢复原厂',
    onConfirm: () => {
      const keys = ['ok', 'mute', 'mode', 'next', 'prev', 'back', 'call', 'voice', 'home', 'custom'];
      const gestures = ['single', 'double', 'long'];
      for (const k of keys) {
        for (const g of gestures) {
          setGestureAction(k, g, 'default');
        }
      }
      showToast('所有方向盘按键已恢复原厂默认直通！');
    }
  });
}
</script>

<style scoped>
/* 方向盘图解统一调暗：降一档亮度，柔和进车规深色主题，兼防白色编号圈在夜间刺眼 */
.guide-img {
  filter: brightness(0.86) contrast(1.02);
}

.floating-fade-enter-active,
.floating-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.floating-fade-enter-from,
.floating-fade-leave-to {
  opacity: 0;
  transform: translateY(12px);
}
</style>
