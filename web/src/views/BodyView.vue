<template>
  <div class="flex flex-col space-y-6">
    <!-- 核心：全车语音播报总开关 - 提权置顶首屏 (左右分栏车规黄金磁贴 · 完全对齐方控总开关标准) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-5 min-h-[106px] shadow-xl flex items-center justify-between transition-all">
      <div class="w-[60%] max-w-[60%] flex flex-col space-y-1.5 shrink-0">
        <div class="flex items-center space-x-3">
          <StatusDot size="lg" :color="store.vehicleAuto.voice_master_switch ? 'ok' : 'off'" :glow-px="10" class="shadow-md" />
          <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">全车语音播报总开关</span>
          <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-item border-car-border text-car-text inline-flex items-center shrink-0 shadow-sm">
            <StatusDot class="mr-2" size="sm" :color="store.vehicleAuto.voice_master_switch ? 'ok' : 'off'" />
            {{ store.vehicleAuto.voice_master_switch ? '全车播报已启用' : '全车已彻底静音 (全车总闸)' }}
          </span>
        </div>
        <div class="text-[14.5px] text-car-sub font-bold leading-normal">
          一键管控全车车门、尾门、挡位与驾驶模式语音播报。关闭后全车物理静音，独立生效。
        </div>
      </div>

      <div class="shrink-0 w-[230px]">
        <button
          @click="toggleVoiceMasterSwitch"
          :class="[
            'w-full h-[74px] px-4 py-2 rounded-2xl border-2 cursor-pointer transition-all shadow-md flex flex-col items-center justify-center text-center',
            store.vehicleAuto.voice_master_switch
              ? 'bg-car-item border-car-accent'
              : 'bg-car-card border-car-border hover:border-car-border-light'
          ]"
        >
          <span class="text-[18.5px] font-black text-car-text tracking-wide whitespace-nowrap">
            {{ store.vehicleAuto.voice_master_switch ? '全车播报已开启' : '全车已一键静音' }}
          </span>
          <span :class="['text-[12.5px] font-bold mt-1 whitespace-nowrap', store.vehicleAuto.voice_master_switch ? 'text-car-accent' : 'text-car-sub']">
            {{ store.vehicleAuto.voice_master_switch ? '点击切换为全车静音' : '点击开启全车播报' }}
          </span>
        </button>
      </div>
    </div>

    <!-- 2. 实时车身物理信号探针 (支持折叠·默认收起) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-5 shadow-xl">
      <div class="flex items-center justify-between" :class="isProbeExpanded ? 'pb-3 mb-3 border-b border-car-border/60' : ''">
        <div class="flex items-center space-x-3">
          <StatusDot size="md" color="accent" :glow-px="8" pulse />
          <span class="text-[20px] font-black text-car-text whitespace-nowrap">车身全域物理信号实时探针 (实车调试专用)</span>
          <span v-if="!isProbeExpanded" class="text-[14px] px-3.5 py-1 rounded-xl bg-car-item border border-car-border text-car-accent font-bold whitespace-nowrap">
            {{ formatGearName(doorStatus.gear) }} · {{ formatModeName(doorStatus.mode) }} · 五门电平监听中
          </span>
        </div>
        <div class="flex items-center space-x-3">
          <span v-if="isProbeExpanded" class="text-[14px] text-car-sub font-bold whitespace-nowrap">
            底层 MCU 串口 91 02 01、TCU 换挡与驾驶模式广播全量监听
          </span>
          <button 
            @click="isProbeExpanded = !isProbeExpanded"
            class="h-[52px] px-5 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[14.5px] transition-all whitespace-nowrap cursor-pointer shadow-sm"
          >
            {{ isProbeExpanded ? '收起探针 ▲' : '展开实时探针 (实车调试) ▼' }}
          </button>
        </div>
      </div>

      <div v-if="isProbeExpanded" class="flex flex-col space-y-4 pt-1">
        <div class="grid grid-cols-5 gap-3">
          <div :class="['p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all', doorStatus.fl === 1 ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/30 shadow-md' : 'bg-car-item border-car-border text-car-sub']">
            <span class="text-[13px] font-bold">主驾车门 (FL)</span>
            <span class="text-[16px] font-black mt-1">{{ doorStatus.fl === 1 ? '● 物理打开' : (doorStatus.fl === 0 ? '○ 已关好' : '采集中...') }}</span>
          </div>
          <div :class="['p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all', doorStatus.fr === 1 ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/30 shadow-md' : 'bg-car-item border-car-border text-car-sub']">
            <span class="text-[13px] font-bold">副驾车门 (FR)</span>
            <span class="text-[16px] font-black mt-1">{{ doorStatus.fr === 1 ? '● 物理打开' : (doorStatus.fr === 0 ? '○ 已关好' : '采集中...') }}</span>
          </div>
          <div :class="['p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all', doorStatus.rl === 1 ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/30 shadow-md' : 'bg-car-item border-car-border text-car-sub']">
            <span class="text-[13px] font-bold">左后车门 (RL)</span>
            <span class="text-[16px] font-black mt-1">{{ doorStatus.rl === 1 ? '● 物理打开' : (doorStatus.rl === 0 ? '○ 已关好' : '采集中...') }}</span>
          </div>
          <div :class="['p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all', doorStatus.rr === 1 ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/30 shadow-md' : 'bg-car-item border-car-border text-car-sub']">
            <span class="text-[13px] font-bold">右后车门 (RR)</span>
            <span class="text-[16px] font-black mt-1">{{ doorStatus.rr === 1 ? '● 物理打开' : (doorStatus.rr === 0 ? '○ 已关好' : '采集中...') }}</span>
          </div>
          <div :class="['p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all', doorStatus.trunk === 1 ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/30 shadow-md' : 'bg-car-item border-car-border text-car-sub']">
            <span class="text-[13px] font-bold">电动尾门 (Trunk)</span>
            <span class="text-[16px] font-black mt-1">{{ doorStatus.trunk === 1 ? '● 物理开启' : (doorStatus.trunk === 0 ? '○ 锁紧关好' : '采集中...') }}</span>
          </div>
        </div>

        <div class="grid grid-cols-2 gap-3">
          <div class="p-3.5 rounded-2xl border-2 bg-car-item border-car-border flex items-center justify-between px-5">
            <div class="flex flex-col">
              <span class="text-[13.5px] font-bold text-car-sub">实时挡位状态 (Gear)</span>
              <span class="text-[18px] font-black text-car-text mt-0.5">{{ formatGearName(doorStatus.gear) }}</span>
            </div>
            <span class="px-3.5 py-1 rounded-full text-[13.5px] font-black bg-car-card border border-car-border text-car-accent">
              {{ doorStatus.gear === 4 ? 'R 挡 (倒车状态)' : (doorStatus.gear === 2 ? 'D 挡 (前进状态)' : (doorStatus.gear === 5 ? 'P 挡 (驻车停泊)' : '实时监听中')) }}
            </span>
          </div>
          <div class="p-3.5 rounded-2xl border-2 bg-car-item border-car-border flex items-center justify-between px-5">
            <div class="flex flex-col">
              <span class="text-[13.5px] font-bold text-car-sub">实时驾驶模式 (DriveMode)</span>
              <span class="text-[18px] font-black text-car-text mt-0.5">{{ formatModeName(doorStatus.mode) }}</span>
            </div>
            <span class="px-3.5 py-1 rounded-full text-[13.5px] font-black bg-car-card border border-car-border text-car-accent">
              AdaptAPI 9位常量直通
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 3. 车载语音播报计划 (对齐方控 106px 车规标准) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-5 min-h-[106px] shadow-xl flex items-center justify-between transition-all">
      <div class="w-full flex flex-col space-y-1.5">
        <div class="flex items-center space-x-3">
          <StatusDot size="lg" color="accent" :glow-px="10" class="shrink-0" />
          <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">车载语音播报计划</span>
          <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-item border-car-accent/40 text-car-accent inline-flex items-center shrink-0 shadow-sm">
            {{ activeVoiceTaskCount }} / 4 项运行中
          </span>
        </div>
        <div class="text-[14.5px] text-car-sub font-bold leading-normal">
          将繁杂的二十余项播报开关收拢为四大场景计划，固定常驻支持独立开关，点击二级向导可独立试听与配置自定义台词/音效。
        </div>
      </div>
      <div class="flex items-center space-x-3 shrink-0">
        <!-- 核心打通：一键跳转车载音频语音主题包 -->
        <button
          @click="store.currentNav = 'audio'"
          class="h-[60px] px-5 rounded-2xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text hover:text-car-accent font-black text-[15.5px] cursor-pointer shadow-sm flex items-center space-x-2 transition-all"
        >
          <span>整套语音主题包 ➔</span>
        </button>
      </div>
    </div>

    <!-- 语音任务流列表 (车规双列网格 2x2 · 告别单列堆叠面条 · 一屏尽览四大场景) -->
    <div class="grid grid-cols-2 gap-5">
      <!-- 语音任务 1: 挡位安全播报计划 -->
      <PlanCard
        title="1. 换挡有人感知语音计划"
        tag="换挡安全"
        help-size="lg"
        help-text="gear"
        flow-sub="踩刹车挂入 D / R / N 挡，或从行车切回 P 挡驻车"
        flow-main="清晰播报挡位状态，支持自定义台词与音频混搭"
        @help="showGearHelp"
      >
        <template #footer>
          <div class="grid grid-cols-2 gap-3">
            <BaseButton variant="planToggleGrid" :active="isGearVoicePlanActive" @click="toggleAllGearVoice">
              <StatusDot size="sm" :color="isGearVoicePlanActive ? 'accent' : 'sub'" :glow-px="6" />
              <span class="truncate">{{ isGearVoicePlanActive ? '计划运行中' : '计划已暂停' }}</span>
            </BaseButton>
            <BaseButton variant="configCta" @click="openGearConfigModal">
              <span>挡位细分配置</span>
              <span>➔</span>
            </BaseButton>
          </div>
        </template>
      </PlanCard>

      <!-- 语音任务 2: 驾驶模式旋钮播报计划 -->
      <PlanCard
        title="2. 驾驶模式旋钮切换计划"
        tag="旋钮激擎"
        help-size="lg"
        help-text="mode"
        flow-sub="中控模式旋钮转动切换至舒适、经济、运动或智能"
        flow-main="晓晓知性声线发声，点亮对应氛围 (含 160ms 防抖)"
        @help="showModeHelp"
      >
        <template #footer>
          <div class="grid grid-cols-2 gap-3">
            <BaseButton variant="planToggleGrid" :active="isModeVoicePlanActive" @click="toggleAllModeVoice">
              <StatusDot size="sm" :color="isModeVoicePlanActive ? 'accent' : 'sub'" :glow-px="6" />
              <span class="truncate">{{ isModeVoicePlanActive ? '模式计划运行中' : '模式计划已暂停' }}</span>
            </BaseButton>
            <BaseButton variant="configCta" @click="openModeConfigModal">
              <span>模式细分配置</span>
              <span>➔</span>
            </BaseButton>
          </div>
        </template>
      </PlanCard>

      <!-- 语音任务 3: 四门迎宾与关门安全播报计划 -->
      <PlanCard
        title="3. 四门迎宾与关门安全计划"
        tag="五门防抖"
        help-size="lg"
        help-text="door"
        flow-sub="主驾、副驾、后排车门开启或关好 (防抖合并)"
        flow-main="通用「车门已打开/关好」或独立分门，支持 TTS 定制"
        @help="showDoorHelp"
      >
        <template #footer>
          <div class="grid grid-cols-2 gap-3">
            <BaseButton variant="planToggleGrid" :active="isDoorVoicePlanActive" @click="toggleAllDoorVoice">
              <StatusDot size="sm" :color="isDoorVoicePlanActive ? 'accent' : 'sub'" :glow-px="6" />
              <span class="truncate">{{ isDoorVoicePlanActive ? '车门计划运行中' : '车门计划已暂停' }}</span>
            </BaseButton>
            <BaseButton variant="configCta" @click="openDoorConfigModal">
              <span>车门详细配置</span>
              <span>➔</span>
            </BaseButton>
          </div>
        </template>
      </PlanCard>

      <!-- 语音任务 4: 原厂电动尾门安全播报计划 -->
      <PlanCard
        title="4. 原厂电动尾门安全计划"
        tag="尾门防碰"
        help-size="lg"
        help-text="trunk"
        flow-sub="电动尾门按键触发升起，或锁扣电机下落闭锁确认"
        flow-main="播报升起防刮蹭警示与落锁提示，支持专属定制"
        @help="showTrunkHelp"
      >
        <template #footer>
          <div class="grid grid-cols-2 gap-3">
            <BaseButton variant="planToggleGrid" :active="isTrunkVoicePlanActive" @click="toggleAllTrunkVoice">
              <StatusDot size="sm" :color="isTrunkVoicePlanActive ? 'accent' : 'sub'" :glow-px="6" />
              <span class="truncate">{{ isTrunkVoicePlanActive ? '尾门计划运行中' : '尾门计划已暂停' }}</span>
            </BaseButton>
            <BaseButton variant="configCta" @click="openTrunkConfigModal">
              <span>尾门详细配置</span>
              <span>➔</span>
            </BaseButton>
          </div>
        </template>
      </PlanCard>
    </div>

    <!-- 挡位细分配置二级向导 → 独立组件（二级向导） -->
    <GearConfigModal v-if="showGearModal" @close="showGearModal = false" />

    <!-- 驾驶模式细分配置二级向导 → 独立组件（二级向导） -->
    <ModeConfigModal v-if="showModeModal" @close="showModeModal = false" />

    <!-- 四门迎宾与关门安全二级向导 → 独立组件（二级向导） -->
    <DoorConfigModal v-if="showDoorModal" @close="showDoorModal = false" />

    <!-- 原厂电动尾门细分配置二级向导 → 独立组件（二级向导） -->
    <TrunkConfigModal v-if="showTrunkModal" @close="showTrunkModal = false" />

    <!-- 底部独立专区: 车况安全守护语音两项测试卡 (双列等高 · 独立组件防膨胀) -->
    <SafetyGuardVoiceSection />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { store, bridge, showToast, openModal } from '../store';
import PlanCard from '../components/PlanCard.vue';
import BaseButton from '../components/BaseButton.vue';
import StatusDot from '../components/StatusDot.vue';
import SafetyGuardVoiceSection from '../components/SafetyGuardVoiceSection.vue';
import GearConfigModal from '../components/GearConfigModal.vue';
import ModeConfigModal from '../components/ModeConfigModal.vue';
import DoorConfigModal from '../components/DoorConfigModal.vue';
import TrunkConfigModal from '../components/TrunkConfigModal.vue';


const showGearModal = ref(false);
const showModeModal = ref(false);
const showDoorModal = ref(false);
const showTrunkModal = ref(false);

const isProbeExpanded = ref(false);
const doorStatus = ref({ fl: -1, fr: -1, rl: -1, rr: -1, trunk: -1, gear: -1, mode: -1 });
let doorPollTimer = null;



const isGearVoicePlanActive = computed(() => {
  return !!(store.vehicleAuto.voice_enable_gear_d || store.vehicleAuto.voice_enable_gear_r || store.vehicleAuto.voice_enable_gear_p);
});

const isModeVoicePlanActive = computed(() => {
  return !!(store.vehicleAuto.voice_enable_mode_comfort || store.vehicleAuto.voice_enable_mode_eco || store.vehicleAuto.voice_enable_mode_sport || store.vehicleAuto.voice_enable_mode_smart);
});

const isDoorVoicePlanActive = computed(() => {
  if (store.vehicleAuto.voice_door_mode_universal !== false) {
    return !!(store.vehicleAuto.voice_enable_door_universal_open !== false || store.vehicleAuto.voice_enable_door_universal_close !== false);
  }
  return !!(store.vehicleAuto.voice_enable_door_fl || store.vehicleAuto.voice_enable_door_fr || store.vehicleAuto.voice_enable_door_rl || store.vehicleAuto.voice_enable_door_rr);
});

const isTrunkVoicePlanActive = computed(() => {
  return !!(store.vehicleAuto.voice_enable_trunk_open || store.vehicleAuto.voice_enable_trunk_close);
});

const activeVoiceTaskCount = computed(() => {
  let count = 0;
  if (isGearVoicePlanActive.value) count++;
  if (isModeVoicePlanActive.value) count++;
  if (isDoorVoicePlanActive.value) count++;
  if (isTrunkVoicePlanActive.value) count++;
  return count;
});

function toggleAllGearVoice() {
  const next = !isGearVoicePlanActive.value;
  store.vehicleAuto.voice_enable_gear_d = next;
  store.vehicleAuto.voice_enable_gear_r = next;
  store.vehicleAuto.voice_enable_gear_p = next;
  bridge.call('setVehicleAutomationSetting', 'voice_enable_gear_d', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_gear_r', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_gear_p', next);
  showToast(next ? '换挡播报计划已开启 (D/R/P)' : '换挡播报计划已暂停');
}

function toggleAllModeVoice() {
  const next = !isModeVoicePlanActive.value;
  store.vehicleAuto.voice_enable_mode_comfort = next;
  store.vehicleAuto.voice_enable_mode_eco = next;
  store.vehicleAuto.voice_enable_mode_sport = next;
  store.vehicleAuto.voice_enable_mode_smart = next;
  bridge.call('setVehicleAutomationSetting', 'voice_enable_mode_comfort', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_mode_eco', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_mode_sport', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_mode_smart', next);
  showToast(next ? '驾驶模式播报计划已开启' : '驾驶模式播报计划已暂停');
}

function toggleAllDoorVoice() {
  const next = !isDoorVoicePlanActive.value;
  store.vehicleAuto.voice_enable_door_universal_open = next;
  store.vehicleAuto.voice_enable_door_universal_close = next;
  store.vehicleAuto.voice_enable_door_fl = next;
  store.vehicleAuto.voice_enable_door_fr = next;
  store.vehicleAuto.voice_enable_door_rl = next;
  store.vehicleAuto.voice_enable_door_rr = next;
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_universal_open', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_universal_close', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_fl', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_fr', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_rl', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_rr', next);
  showToast(next ? '车门播报计划已开启' : '车门播报计划已暂停');
}

function toggleAllTrunkVoice() {
  const next = !isTrunkVoicePlanActive.value;
  store.vehicleAuto.voice_enable_trunk_open = next;
  store.vehicleAuto.voice_enable_trunk_close = next;
  bridge.call('setVehicleAutomationSetting', 'voice_enable_trunk_open', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_trunk_close', next);
  showToast(next ? '电动尾门播报计划已开启' : '电动尾门播报计划已暂停');
}


function openGearConfigModal() {
  showGearModal.value = true;
}

function openModeConfigModal() {
  showModeModal.value = true;
}

function openDoorConfigModal() {
  showDoorModal.value = true;
}

function openTrunkConfigModal() {
  showTrunkModal.value = true;
}

function showGearHelp() {
  openModal('confirm', {
    title: '【功能指南】换挡权威源与有人感知状态机',
    desc: '1. 权威判定：以原厂 360 环视 AVM 广播与 TCU 换挡底层低 4 位为权威源，过滤 10 号假挡位。\n\n2. 有人感知：蓝牙钥匙靠近唤醒时保持静默；真正踩刹车切 D/R 挡才激活；回 P 挡播报一次后归零休眠。',
    tip: 'N 挡空挡播报默认关闭，避免红绿灯打扰；支持在细分配置中独立声效设置。',
    showCancel: false,
    confirmText: '我知道了'
  });
}

function showModeHelp() {
  openModal('confirm', {
    title: '【功能指南】4 大驾驶模式旋钮切换',
    desc: '1. 旋钮响应：支持舒适、经济、运动与智能 4 大驾驶模式。\n\n2. 极速防抖：内置 160ms 防抖滤波，防止快速旋转旋钮掐灭音频或重叠发声。',
    tip: '点火前 5 秒自动保护音频通道，防止原厂开机音吞音；支持在细分配置中为每种模式独立定制台词与音效。',
    showCancel: false,
    confirmText: '我知道了'
  });
}

function showDoorHelp() {
  openModal('confirm', {
    title: '【功能指南】五门防抖与通用智能语音',
    desc: '1. 通用智能模式：推荐模式。开门统一播报「车门已打开」，关门统一播报「车门已关好」，多门同时动作合并防抖，不抢音。\n\n2. 独立分门模式：可分别为主驾、副驾、后排播报专属台词。',
    tip: '在二级向导中可随时切换模式，并对各项开闭动作进行个性化声效定制。',
    showCancel: false,
    confirmText: '我知道了'
  });
}

function showTrunkHelp() {
  openModal('confirm', {
    title: '【功能指南】原厂电动尾门独立语音播报',
    desc: '1. 底层独立串口：独立监听 MCU 串口 91 02 01 b7 尾门位图，与四门逻辑完全解耦独立。\n\n2. 升起防刮与锁止确认：后备箱抬起升起时安全警示，防碰擦车库顶梁；电吸完全锁止时短促确认“后备箱已关好”，关后备箱无需回头确认。',
    tip: '升起播报与锁止播报均带独立开关，支持试听与自定义 TTS 台词/音频文件。',
    showCancel: false,
    confirmText: '我知道了'
  });
}


function formatGearName(gear) {
  switch (gear) {
    case 2: return '前进挡 (D 挡)';
    case 4: return '倒车挡 (R 挡)';
    case 5: return '驻车挡 (P 挡)';
    case 1: return '空挡 (N 挡)';
    default: return '采集中...';
  }
}

function formatModeName(mode) {
  switch (mode) {
    case 1: return '舒适模式 (Comfort)';
    case 2: return '运动模式 (Sport)';
    case 3: return '经济模式 (Eco)';
    case 4: return '智能模式 (Smart)';
    default: return '采集中...';
  }
}

function fetchDoorStatus() {
  try {
    const raw = bridge.call('getDoorStatus');
    if (raw) {
      const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
      if (data && typeof data === 'object') {
        doorStatus.value = {
          fl: Number(data.fl ?? -1),
          fr: Number(data.fr ?? -1),
          rl: Number(data.rl ?? -1),
          rr: Number(data.rr ?? -1),
          trunk: Number(data.trunk ?? -1),
          gear: Number(data.gear ?? -1),
          mode: Number(data.mode ?? -1)
        };
      }
    }
  } catch (e) {}
}



function toggleVoiceMasterSwitch() {
  const next = !store.vehicleAuto.voice_master_switch;
  store.vehicleAuto.voice_master_switch = next;
  bridge.call('setVehicleAutomationSetting', 'voice_master_switch', next);
  showToast(next ? '全车语音播报总开关: 已开启 (正常播报)' : '全车语音播报总开关: 已关闭 (全车静音)');
}


onMounted(() => {
  fetchDoorStatus();
  doorPollTimer = setInterval(fetchDoorStatus, 800);
});

onUnmounted(() => {
  if (doorPollTimer) clearInterval(doorPollTimer);
});
</script>
