<template>
  <div class="flex flex-col space-y-4">
    <!-- 顶部车规二级分段胶囊：一页全景掌控 (支持外部统一接管隐藏) -->
    <SubTabs v-if="!hideSubTabs" :tabs="wheelTabs" v-model="activeTab" />

    <!-- 核心：方向盘按键方控接管总开关 (置顶常驻整卡开关大磁贴) -->
    <div
      @click="toggleWheelMasterSwitch"
      :class="[
        'border-2 rounded-2xl p-4 min-h-[88px] shadow-xl flex items-center justify-between transition-all cursor-pointer select-none active:scale-[0.99]',
        store.vehicleAuto.wheel_master_switch
          ? 'bg-car-item border-car-accent shadow-md'
          : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
      ]"
    >
      <div class="w-[65%] max-w-[65%] flex flex-col space-y-1.5 shrink-0">
        <div class="flex items-center space-x-3">
          <StatusDot size="lg" :color="store.vehicleAuto.wheel_master_switch ? 'ok' : 'off'" :glow-px="10" class="shadow-md" />
          <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">方向盘按键接管总开关</span>
        </div>
        <div class="text-[14.5px] text-car-sub font-bold leading-normal">
          全面接管静音键、Mode键、切歌键与主页返回键。单击即发，零延迟触发。
        </div>
      </div>

      <!-- 右侧：轻量化状态大磁贴（消灭外框大按钮） -->
      <div class="flex items-center space-x-3 shrink-0 pr-2">
        <StatusDot size="md" :color="store.vehicleAuto.wheel_master_switch ? 'ok' : 'off'" :glow-px="8" />
        <span :class="['text-[20px] font-black tracking-wide', store.vehicleAuto.wheel_master_switch ? 'text-car-text' : 'text-car-sub']">
          {{ store.vehicleAuto.wheel_master_switch ? '按键接管已启用' : '原车默认未接管' }}
        </span>
      </div>
    </div>



    <!-- Tab 1: 按键独立映射 (v-if 彻底杜绝幽灵重叠) -->
    <div v-if="activeTab === 'keys'" class="flex flex-col space-y-4">
    <!-- 各按键映射配置 (右方向盘多媒体 SX-0017 物理按键逐项配置) -->
    <div class="flex flex-col space-y-4">
      <!-- 双列规整网格：一排两卡，工整对齐，共三排 -->
      <div class="grid grid-cols-2 gap-4">
        <!-- ① 右方向盘主页/返回键 -->
        <WheelGestureCard
          class="!mb-0"
          key-name="back"
          card-title="右方向盘 ① 主页/返回按键"
          card-desc="右盘 ① 号返回键，单击映射为高德导航、360 全景等。"
          helpTitle="【功能指南】主页 / 返回按键映射"
          helpText="1. 原厂功能：&#10;原车默认点按返回上一级或主页。&#10;&#10;2. 自定义映射：&#10;可自由绑定为秒开 360 全景、高德地图、打开指定应用或执行控制台快捷动作。&#10;&#10;3. 恢复原厂：&#10;若希望保持吉利系统默认返回逻辑，下拉选择「恢复原厂默认」即可。"
          helpTip="若平时习惯原车返回逻辑，建议保持「恢复原厂默认」。"
        />

        <!-- ② 右方向盘音量滚轮按压 -->
        <WheelGestureCard
          class="!mb-0"
          key-name="ok"
          card-title="右方向盘 ② 音量滚轮按压"
          card-desc="右盘 ② 号滚轮垂直向下按压，默认播放/暂停，支持改绑。"
          helpTitle="【功能指南】音量滚轮按压映射"
          helpText="1. 滚轮垂直按压：&#10;右方向盘音量滚轮除了上下滚动调音量外，垂直向下按压也是独立物理按键。&#10;&#10;2. 推荐用途：&#10;默认推荐设为「播放 / 暂停媒体」，开车途中一键暂停极方便；亦可设为 360 全景或高德地图。&#10;&#10;3. 纯净极速：&#10;已剥离复杂长按判断，按压即响应，不影响滚轮正常滚动调音。"
          helpTip="推荐绑定「播放/暂停」，开车切歌听歌更顺手。"
        />

        <!-- ③ 右方向盘静音键 -->
        <WheelGestureCard
          class="!mb-0"
          key-name="mute"
          card-title="右方向盘 ③ 静音按键"
          card-desc="右盘 ③ 号静音键，单击自定义；长按 10 秒仍为原车冷重启。"
          helpTitle="【功能指南】静音按键映射与 10 秒冷重启"
          helpText="1. 静音键自定义：&#10;原车点按为全局静音，可改绑为打开 360 全景、高德导航、小爱同学或切歌。&#10;&#10;2. 强制重启救急：&#10;长按静音键 10 秒是车机自带的强制重启功能，无论在此如何设置，该应急功能始终有效，遇到车机卡死可随时使用。"
          helpTip="长按 10 秒是车机自带的应急重启，任何设置都不会影响它。"
        />

        <!-- ⑥ 右方向盘 MODE 键 -->
        <WheelGestureCard
          class="!mb-0"
          key-name="mode"
          card-title="右方向盘 ⑥ MODE 模式键"
          card-desc="右盘 ⑥ 号 MODE 键，默认一键秒开 360 全景，支持改绑。"
          helpTitle="【功能指南】MODE 模式按键映射"
          helpText="1. 摆脱原厂收音机：&#10;原车点按 MODE 会强制唤醒伴听或收音机，改绑后可彻底告别讨厌的原厂收音机打扰。&#10;&#10;2. 一键秒开 360：&#10;默认推荐设为打开「360 全景」，倒车、过狭窄路段或侧方停车时一键呼出，非常实用。&#10;&#10;3. 极速响应：&#10;按压立即触发，无任何手势等待延迟。"
          helpTip="绝大多数车友强烈推荐绑定「360 全景影像」，过窄路神器。"
        />

        <!-- ④ & ⑦ 切歌双键：上一曲 / 下一曲 -->
        <FeatureCard
          class="!mb-0"
          title="右方向盘 ④ 下一曲 / ⑦ 上一曲"
          desc="右盘 ④/⑦ 号切歌键，接管后防偶发失效，切歌零延迟。"
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

        <!-- ⑤ 方向盘自定义键 -->
        <WheelGestureCard
          class="!mb-0"
          key-name="custom"
          card-title="方向盘 ⑤ 自定义按键"
          :card-desc="customKeyDesc"
          helpTitle="【功能指南】方向盘自定义按键映射与车型适配"
          helpText="1. 低配（热擎版）：&#10;原车无 360 与专属交互，该按键在原厂处于闲置状态，是低配车友改绑 360 或高德导航的绝佳按键。&#10;&#10;2. 中高配（激擎版/Battle版）：&#10;若原车已被特定交互占用，可选择「恢复原厂默认」放行；若闲置亦可自由映射。&#10;&#10;3. 全功能接管：&#10;可自由映射为秒开 360 全景、导航、静音、打开常用应用等。"
          :helpTip="customKeyTip"
        />
      </div>
    </div>


    </div>

    <!-- Tab 2: 接管模式与图解 (v-if 彻底杜绝幽灵重叠) -->
    <div v-if="activeTab === 'mode'" class="flex flex-col space-y-4">
    <!-- 1. 方向盘方控接管模式 -->
    <FeatureCard 
      title="方控按键接管模式"
      desc="屏蔽原厂伴听抢占广播；若同时安装米小江建议选择协同优先。"
      helpTitle="【功能指南】方控接管模式与仪表显示"
      helpText="1. 接管模式选择：&#10;• 控制台独立接管（推荐）：方向盘按键直接控制切歌与播放，自动唤醒车载喇叭，体验最流畅。&#10;• 米小江方控优先：如果车机上装了米小江 CarMedia，选这项让它优先处理切歌，两者互不打扰。&#10;• 恢复原厂默认：不拦截方向盘按键，全部交回原车控制。&#10;&#10;2. 蓝牙无声问题解决：&#10;独立接管模式下，手机连车机蓝牙放微信语音或音乐没声音时，会自动帮您接通车载喇叭。&#10;&#10;3. 仪表与屏保显示：&#10;可自由选择是否把当前歌名和歌词推送到仪表盘或屏保上显示。" 
      helpTip="如果同时装了米小江请选「米小江方控优先」；只用本工具箱建议选「控制台独立接管」。"
    >
      <div class="grid grid-cols-4 gap-3.5" style="grid-gap: 12px; -webkit-column-gap: 12px;">
        <MatrixButton 
          title="米小江方控优先"
          subtitle="放行切歌与Mode键，补充静音键接管"
          :active="store.vehicleAuto.wheel_control_mode === 'carmedia_first'"
          @click="setWheelMode('carmedia_first')"
        />
        <MatrixButton 
          title="控制台独立接管"
          subtitle="全量接管按键，保持蓝牙声音正常"
          :active="store.vehicleAuto.wheel_control_mode === 'toolbox_alone' || (!store.vehicleAuto.wheel_control_mode && store.vehicleAuto.wheel_control_mode !== 'factory_default' && store.vehicleAuto.wheel_control_mode !== 'bt_only')"
          @click="setWheelMode('toolbox_alone')"
        />
        <MatrixButton 
          title="仅接管蓝牙"
          subtitle="不接管方向盘按键，仅保持蓝牙声音正常"
          :active="store.vehicleAuto.wheel_control_mode === 'bt_only'"
          @click="setWheelMode('bt_only')"
        />
        <MatrixButton 
          title="恢复原厂默认"
          subtitle="恢复原车按键功能，不拦截按键"
          :active="store.vehicleAuto.wheel_control_mode === 'factory_default'"
          @click="setWheelMode('factory_default')"
        />
      </div>
    </FeatureCard>

    <!-- 2. 仪表盘与屏保显示扩展 (独立双列卡片，左右等高对称，整卡点击开关) -->
    <div class="grid grid-cols-2 gap-4 items-stretch">
      <div
        @click="toggleClusterPlayback"
        :class="[
          'flex items-center justify-between p-5 rounded-3xl border-2 cursor-pointer select-none active:scale-[0.985] transition-all shadow-xl',
          store.vehicleAuto.wheel_push_playback_cluster
            ? 'bg-car-item border-car-accent shadow-md'
            : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
        ]"
      >
        <div class="flex flex-col space-y-1 pr-3">
          <span class="text-[17px] font-black text-car-text">息屏时钟显示音乐卡片</span>
          <span class="text-[13px] text-car-sub font-bold">息屏/时钟屏保时展示当前歌名歌手</span>
        </div>
        <div class="flex items-center space-x-2 shrink-0">
          <StatusDot size="sm" :color="store.vehicleAuto.wheel_push_playback_cluster ? 'ok' : 'off'" :glow-px="6" />
          <span :class="['text-[15px] font-black', store.vehicleAuto.wheel_push_playback_cluster ? 'text-car-text' : 'text-car-sub']">
            {{ store.vehicleAuto.wheel_push_playback_cluster ? '已开启' : '已关闭' }}
          </span>
        </div>
      </div>

      <div
        @click="toggleClusterLyrics"
        :class="[
          'flex items-center justify-between p-5 rounded-3xl border-2 cursor-pointer select-none active:scale-[0.985] transition-all shadow-xl',
          store.vehicleAuto.wheel_push_lyrics_cluster
            ? 'bg-car-item border-car-accent shadow-md'
            : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
        ]"
      >
        <div class="flex flex-col space-y-1 pr-3">
          <span class="text-[17px] font-black text-car-text">液晶仪表盘同步滚动歌词</span>
          <span class="text-[13px] text-car-sub font-bold">向仪表盘中间卡片推送实时歌词</span>
        </div>
        <div class="flex items-center space-x-2 shrink-0">
          <StatusDot size="sm" :color="store.vehicleAuto.wheel_push_lyrics_cluster ? 'ok' : 'off'" :glow-px="6" />
          <span :class="['text-[15px] font-black', store.vehicleAuto.wheel_push_lyrics_cluster ? 'text-car-text' : 'text-car-sub']">
            {{ store.vehicleAuto.wheel_push_lyrics_cluster ? '已开启' : '已关闭' }}
          </span>
        </div>
      </div>
    </div>


    <div
      @click="toggleFloating()"
      :class="[
        'rounded-3xl border-2 p-5 shadow-xl transition-all duration-200 cursor-pointer select-none active:scale-[0.99]',
        floatingEnabled
          ? 'bg-car-item border-car-accent shadow-md'
          : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
      ]"
    >
      <div class="flex items-center justify-between">
        <div class="flex items-center">
          <StatusDot class="mr-3" size="md" color="accent" :glow-px="8" />
          <div class="flex flex-col">
            <span class="text-[18px] font-black text-car-text">吉利缤越 COOL 方向盘多媒体按键图解</span>
            <span class="text-[14px] text-car-sub font-bold mt-0.5">
              方向盘右侧多媒体按键：静音、切歌、MODE、返回键均可一键接管并自定义
            </span>
          </div>
        </div>
        <div class="flex items-center space-x-3 shrink-0">
          <!-- 悬浮状态灯指示（消除大按钮） -->
          <div class="flex items-center space-x-2 mr-1">
            <StatusDot size="sm" :color="floatingEnabled ? 'ok' : 'off'" :glow-px="6" />
            <span :class="['text-[15px] font-black', floatingEnabled ? 'text-car-text' : 'text-car-sub']">
              {{ floatingEnabled ? '悬浮图解已开启' : '悬浮图解已关闭' }}
            </span>
          </div>
          <button
            type="button"
            @click.stop="toggleDiagram()"
            class="h-[50px] px-4 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm active:scale-95 transition-all"
          >
            {{ showDiagram ? '收起图解' : '展开图解' }}
          </button>
        </div>
      </div>

      <!-- 图解内容展示区 (阻止冒泡防误触) -->
      <div v-if="showDiagram" @click.stop class="mt-4 pt-4 border-t border-car-border/60 flex items-start space-x-6">
        <!-- 原厂方向盘按键高清图示 (沉稳车规底座，绝非刺眼白底) -->
        <div class="p-3 bg-car-item rounded-2xl shadow-md border-2 border-car-border w-[320px] min-w-[320px] max-w-[320px] shrink-0">
          <img 
            :src="wheelGuideImg" 
            alt="吉利缤越 COOL 右侧多媒体按键图解" 
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
              <span class="text-car-accent font-black mr-1.5">⑤ 自定义键:</span> 方向盘预留自定义键 (单击自定义)
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

    </div>

    <!-- 方向盘图解悬浮对照卡：支持拖放自定义位置并本地记忆，下次同位显示；原地点按隐藏 -->
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
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { store, bridge, openModal, showToast } from '../store';
import { useWheelGesture } from '../composables/useWheelGesture';
import SubTabs from '../components/SubTabs.vue';
import StatusDot from '../components/StatusDot.vue';
import BaseButton from '../components/BaseButton.vue';
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';
import ActionSelect from '../components/ActionSelect.vue';
import WheelGestureCard from '../components/WheelGestureCard.vue';

// 缤越 COOL 专车原厂 SX-0017 多媒体按键图解（深色车规，柔和不刺眼）
import wheelGuideImg from '../assets/steering_wheel_guide.webp';
import wheelGuideFloatingImg from '../assets/steering_wheel_guide_floating.svg';

const props = defineProps({
  initialTab: {
    type: String,
    default: 'keys'
  },
  hideSubTabs: {
    type: Boolean,
    default: false
  }
});

const activeTab = ref(props.initialTab || 'keys');

// 车型档位差异化文案（trim_name 由车机端自动探测推送）
const trimName = computed(() => store.deviceInfo.trim_name || '');
const isLowTrim = computed(() => trimName.value.includes('热擎'));
const isHighTrim = computed(() => trimName.value.includes('Battle'));
const customKeyDesc = computed(() => {
  if (isLowTrim.value) return '⑤ 号自定义键：热擎版原厂闲置按键，可放心改绑常用功能。';
  if (isHighTrim.value) return '⑤ 号自定义键：Battle版原厂有专属交互，可接管也可放行。';
  return '⑤ 号自定义键，原厂预留按键，单击秒级触发。';
});
const customKeyTip = computed(() => {
  if (isLowTrim.value) return '低配推荐设为「360 影像」或「高德导航」，弥补无原生 360 的遗憾。';
  return '推荐设为「360 全景影像」，行车随时查看周边盲区。';
});

const wheelTabs = [
  { id: 'keys', name: '按键映射手势', tag: '6大按键' },
  { id: 'mode', name: '接管模式与图解', tag: '工作模式' }
];

watch(() => props.initialTab, (v) => {
  if (v) activeTab.value = v;
});

const showDiagram = ref(true);

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
  showToast(next ? '按键接管总开关: 已开启' : '按键接管总开关: 已关闭 (原车控制)');
}

function setWheelMode(mode) {
  store.vehicleAuto.wheel_control_mode = mode;
  bridge.call('setWheelControlStringSetting', 'wheel_control_mode', mode);
  const modeNames = {
    carmedia_first: '米小江优先',
    toolbox_alone: '控制台独立接管',
    bt_only: '仅接管蓝牙(不改按键)',
    factory_default: '恢复原厂'
  };
  showToast('方控模式已切换: ' + (modeNames[mode] || mode));
}

function toggleClusterPlayback() {
  const next = !store.vehicleAuto.wheel_push_playback_cluster;
  if (next) {
    openModal('confirm', {
      title: '提示',
      desc: '开启息屏音乐卡片需要将音乐通道交由车机自带播放器自行处理，助手的后台蓝牙保活功能将自动让位。您的方向盘按键设置仍会保持不变。是否确认开启？',
      confirmText: '确认开启',
      onConfirm: () => {
        store.vehicleAuto.wheel_push_playback_cluster = true;
        bridge.call('setVehicleAutomationSetting', 'wheel_push_playback_cluster', true);
        showToast('已开启屏保音乐卡片');
      }
    });
    return;
  }

  store.vehicleAuto.wheel_push_playback_cluster = false;
  bridge.call('setVehicleAutomationSetting', 'wheel_push_playback_cluster', false);
  showToast('已关闭屏保音乐卡片');
}

function toggleClusterLyrics() {
  const next = !store.vehicleAuto.wheel_push_lyrics_cluster;
  if (next) {
    openModal('confirm', {
      title: '提示',
      desc: '开启仪表盘歌词需要将音乐通道交由车机自带播放器自行处理，助手的后台蓝牙保活功能将自动让位。您的方向盘按键设置仍会保持不变。是否确认开启？',
      confirmText: '确认开启',
      onConfirm: () => {
        store.vehicleAuto.wheel_push_lyrics_cluster = true;
        bridge.call('setVehicleAutomationSetting', 'wheel_push_lyrics_cluster', true);
        showToast('已开启仪表盘歌词推送');
      }
    });
    return;
  }

  store.vehicleAuto.wheel_push_lyrics_cluster = false;
  bridge.call('setVehicleAutomationSetting', 'wheel_push_lyrics_cluster', false);
  showToast('已关闭仪表盘歌词推送');
}

function resetAllToFactory() {
  openModal('confirm', {
    title: '恢复原车默认设置',
    desc: '将方向盘所有按键全部恢复为原车默认动作，不再拦截任何按键。是否确认？',
    isDanger: true,
    confirmText: '确认恢复',
    onConfirm: () => {
      const keys = ['ok', 'mute', 'mode', 'next', 'prev', 'back', 'call', 'voice', 'home', 'custom'];
      const gestures = ['single', 'double', 'long'];
      for (const k of keys) {
        for (const g of gestures) {
          setGestureAction(k, g, 'default');
        }
      }
      showToast('所有方向盘按键已恢复原车默认！');
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
