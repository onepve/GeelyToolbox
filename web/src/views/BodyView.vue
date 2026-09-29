<template>
  <div class="flex flex-col space-y-4">
    <!-- 核心：全车语音播报总开关 - 核心功能首屏 (左右分栏车规黄金磁贴 · 完全对齐方控总开关) -->
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

    <!-- 语音提示列表：车规双列网格，一屏尽览四大场景 -->
    <div class="grid grid-cols-2 gap-4">
      <!-- 语音提示 1：换挡安全播报 -->
      <PlanCard
        title="1. 换挡语音提示"
        tag="换挡安全"
        help-size="lg"
        help-text="gear"
        flow-sub="踩刹车挂入 D / R / N 挡，或从行车切回 P 挡驻车"
        flow-main="清晰播报挡位状态，支持自定义音频混搭"
        @help="showGearHelp"
      >
        <template #footer>
          <div class="grid grid-cols-2 gap-3">
            <BaseButton variant="planToggleGrid" :active="isGearVoicePlanActive" @click="toggleAllGearVoice">
              <StatusDot size="sm" :color="isGearVoicePlanActive ? 'accent' : 'sub'" :glow-px="6" />
              <span class="truncate">{{ isGearVoicePlanActive ? '已开启' : '已关闭' }}</span>
            </BaseButton>
            <BaseButton variant="configCta" @click="openGearConfigModal">
              <span>挡位细分配置</span>
              <span>➔</span>
            </BaseButton>
          </div>
        </template>
      </PlanCard>

      <!-- 语音提示 2：驾驶模式播报 -->
      <PlanCard
        title="2. 驾驶模式播报"
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
              <span class="truncate">{{ isModeVoicePlanActive ? '已开启' : '已关闭' }}</span>
            </BaseButton>
            <BaseButton variant="configCta" @click="openModeConfigModal">
              <span>模式细分配置</span>
              <span>➔</span>
            </BaseButton>
          </div>
        </template>
      </PlanCard>

      <!-- 语音提示 3：车门迎宾与关门安全播报 -->
      <PlanCard
        title="3. 车门迎宾与安全提示"
        tag="五门防抖"
        help-size="lg"
        help-text="door"
        flow-sub="主驾、副驾、后排车门开启或关好 (防抖合并)"
        flow-main="通用「车门已打开/关好」或独立分门，支持专属音频定制"
        @help="showDoorHelp"
      >
        <template #footer>
          <div class="grid grid-cols-2 gap-3">
            <BaseButton variant="planToggleGrid" :active="isDoorVoicePlanActive" @click="toggleAllDoorVoice">
              <StatusDot size="sm" :color="isDoorVoicePlanActive ? 'accent' : 'sub'" :glow-px="6" />
              <span class="truncate">{{ isDoorVoicePlanActive ? '已开启' : '已关闭' }}</span>
            </BaseButton>
            <BaseButton variant="configCta" @click="openDoorConfigModal">
              <span>车门详细配置</span>
              <span>➔</span>
            </BaseButton>
          </div>
        </template>
      </PlanCard>

      <!-- 语音提示 4：电动尾门安全播报 -->
      <PlanCard
        title="4. 尾门安全提示"
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
              <span class="truncate">{{ isTrunkVoicePlanActive ? '已开启' : '已关闭' }}</span>
            </BaseButton>
            <BaseButton variant="configCta" @click="openTrunkConfigModal">
              <span>尾门详细配置</span>
              <span>➔</span>
            </BaseButton>
          </div>
        </template>
      </PlanCard>
    </div>

    <!-- 进阶管理: 车载专属语音主题包中枢 (位于四大核心卡片下方) -->
    <FeatureCard 
      id="voice_theme_mgr"
      title="车载专属语音主题包 (整套一键换装)" 
      tag="整套换装"
      icon-name="music"
      help-text="一键整套替换全车 D/R/P 挡位、4大驾驶模式、四门迎宾与电动尾门音效包；也支持在上方四大卡片各自分项中单独混搭覆盖。"
    >
      <div class="flex flex-col space-y-4">
        <!-- 顶部状态栏: 当前生效主题与操作按钮 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-4 flex items-center justify-between shadow-sm">
          <div class="flex items-center space-x-3.5 min-w-0 pr-4">
            <span class="w-3.5 h-3.5 rounded-full bg-car-accent shadow-[0_0_8px_var(--accent-gold)] shrink-0"></span>
            <div class="flex flex-col min-w-0">
              <div class="flex items-center space-x-2">
                <span class="text-[17px] font-black text-car-text shrink-0">当前整套音效：</span>
                <span class="text-[17px] font-black text-car-accent">{{ activeThemeName ? activeThemeName : '出厂官方原声 (晓晓温婉知性)' }}</span>
                <span class="px-2 py-0.5 rounded-full bg-car-card border border-car-border text-car-text text-[11.5px] font-black inline-flex items-center shadow-sm shrink-0">
                  <StatusDot class="mr-1.5" size="xs" color="ok" />
                  {{ activeThemeName ? '自定义主题' : '系统默认' }}
                </span>
              </div>
              <span class="text-[12.5px] text-car-sub font-bold mt-0.5">
                {{ activeThemeName ? `专属目录: /sdcard/GeelyPilot/voices/${activeThemeName}/` : '吉利智驾出厂原声 · 未包含项自动补齐兜底' }}
              </span>
            </div>
          </div>

          <div class="flex items-center space-x-2.5 shrink-0">
            <button 
              @click="openModal('voiceThemeImport')"
              class="min-h-[50px] px-4 bg-car-card border-2 border-car-accent text-car-text font-black text-[15px] rounded-xl cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 shadow-md transition-all flex items-center space-x-1.5 whitespace-nowrap"
            >
              <span>导入语音包 (.zip)</span>
            </button>
            <button 
              @click="openModal('qrCode')"
              class="min-h-[50px] px-3.5 bg-car-card border-2 border-car-border text-car-text font-black text-[14.5px] rounded-xl cursor-pointer hover:border-car-border-light shadow-sm transition-all whitespace-nowrap"
            >
              手机扫码制作
            </button>
            <button 
              @click="loadVoiceThemes"
              class="min-h-[50px] px-3.5 bg-car-card border-2 border-car-border text-car-text font-black text-[14.5px] rounded-xl cursor-pointer hover:border-car-border-light shadow-sm transition-all whitespace-nowrap"
            >
              刷新
            </button>
          </div>
        </div>

        <!-- 主题包列表卡片流 -->
        <div class="flex flex-col space-y-3">
          <!-- 默认出厂主题卡片 -->
          <div class="bg-car-item border-2 border-car-border rounded-2xl p-4 flex items-center justify-between shadow-sm">
            <div class="flex-1 min-w-0 pr-4 flex flex-col space-y-1">
              <div class="flex items-center space-x-2">
                <span class="text-[18px] font-black text-car-text">出厂官方原声 (晓晓温婉知性)</span>
                <span class="px-2 py-0.5 rounded-md bg-car-card border border-car-border text-car-sub text-[11.5px] font-black shrink-0">系统内置</span>
                <span v-if="!activeThemeName" class="px-2.5 py-0.5 rounded-md bg-car-card border border-car-accent/60 text-car-accent text-[12px] font-black inline-flex items-center shadow-sm shrink-0">
                  <span class="w-1.5 h-1.5 rounded-full mr-1.5 bg-car-accent shadow-sm"></span>正在生效
                </span>
              </div>
              <span class="text-[13px] text-car-sub font-bold">
                吉利原车温婉知性原声，端庄舒缓温润。零音频丢失，全场景兜底保障。
              </span>
            </div>

            <div class="flex items-center space-x-2.5 shrink-0">
              <button 
                @click="testThemeVoice('')"
                class="min-h-[50px] w-[100px] bg-car-card border-2 border-car-border text-car-text font-black text-[14.5px] rounded-xl hover:border-car-border-light cursor-pointer shadow-sm transition-all flex items-center justify-center whitespace-nowrap"
              >
                试听样音
              </button>
              <button
                v-if="activeThemeName"
                @click="applyVoiceTheme('')"
                class="min-h-[50px] w-[110px] bg-car-card border-2 border-car-accent text-car-accent font-black text-[14.5px] rounded-xl hover:border-car-accent ring-2 ring-car-accent/20 cursor-pointer shadow-md transition-all flex items-center justify-center whitespace-nowrap"
              >
                恢复原声
              </button>
              <button
                v-else
                @click="confirmForceRestoreFactory"
                class="min-h-[50px] w-[110px] bg-car-card border-2 border-car-border text-car-accent font-black text-[14.5px] rounded-xl hover:border-car-accent cursor-pointer shadow-sm transition-all flex items-center justify-center whitespace-nowrap"
              >
                重装原声
              </button>
            </div>
          </div>

          <!-- 用户导入的各语音主题包 -->
          <div 
            v-for="theme in voiceThemes" 
            :key="theme.id"
            class="bg-car-item border-2 rounded-2xl p-4 flex items-center justify-between shadow-sm transition-all"
            :class="activeThemeName === theme.name ? 'border-car-accent ring-2 ring-car-accent/20' : 'border-car-border'"
          >
            <div class="flex-1 min-w-0 pr-4 flex flex-col space-y-1">
              <div class="flex items-center space-x-2">
                <span class="text-[18px] font-black text-car-text">{{ theme.name }}</span>
                <span v-if="activeThemeName === theme.name" class="px-2.5 py-0.5 rounded-md bg-car-card border border-car-accent/60 text-car-accent text-[12px] font-black inline-flex items-center shadow-sm shrink-0">
                  <span class="w-1.5 h-1.5 rounded-full mr-1.5 bg-car-accent shadow-sm"></span>正在生效
                </span>
                <span v-else class="px-2 py-0.5 rounded-md bg-car-card border border-car-border text-car-sub text-[11.5px] font-black shrink-0">已导入</span>
              </div>
              <span class="text-[13px] text-car-sub font-mono truncate">
                /sdcard/GeelyPilot/voices/{{ theme.name }}/
              </span>
            </div>

            <div class="flex items-center space-x-2.5 shrink-0">
              <button 
                @click="testThemeVoice(theme.name)"
                class="min-h-[50px] w-[100px] bg-car-card border-2 border-car-border text-car-text font-black text-[14.5px] rounded-xl hover:border-car-border-light cursor-pointer shadow-sm transition-all flex items-center justify-center whitespace-nowrap"
              >
                试听样音
              </button>
              <button 
                v-if="activeThemeName !== theme.name"
                @click="applyVoiceTheme(theme.name)"
                class="min-h-[50px] w-[110px] bg-car-card border-2 border-car-accent text-car-text font-black text-[14.5px] rounded-xl hover:border-car-accent ring-2 ring-car-accent/20 cursor-pointer shadow-md transition-all flex items-center justify-center whitespace-nowrap"
              >
                整套启用
              </button>
              <button 
                @click="confirmDeleteTheme(theme.name)"
                class="min-h-[50px] w-[80px] bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text hover:text-rose-400 font-black text-[14.5px] rounded-xl cursor-pointer shadow-sm transition-all flex items-center justify-center whitespace-nowrap"
              >
                删除
              </button>
            </div>
          </div>
        </div>
      </div>
    </FeatureCard>

    <!-- 挡位细分配置二级向导 → 独立组件（二级向导） -->
    <GearConfigModal v-if="showGearModal" @close="showGearModal = false" />

    <!-- 驾驶模式细分配置二级向导 → 独立组件（二级向导） -->
    <ModeConfigModal v-if="showModeModal" @close="showModeModal = false" />

    <!-- 四门迎宾与关门安全二级向导 → 独立组件（二级向导） -->
    <DoorConfigModal v-if="showDoorModal" @close="showDoorModal = false" />

    <!-- 原厂电动尾门细分配置二级向导 → 独立组件（二级向导） -->
    <TrunkConfigModal v-if="showTrunkModal" @close="showTrunkModal = false" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { store, bridge, showToast, openModal } from '../store';
import FeatureCard from '../components/FeatureCard.vue';
import PlanCard from '../components/PlanCard.vue';
import BaseButton from '../components/BaseButton.vue';
import StatusDot from '../components/StatusDot.vue';
import GearConfigModal from '../components/GearConfigModal.vue';
import ModeConfigModal from '../components/ModeConfigModal.vue';
import DoorConfigModal from '../components/DoorConfigModal.vue';
import TrunkConfigModal from '../components/TrunkConfigModal.vue';


const showGearModal = ref(false);
const showModeModal = ref(false);
const showDoorModal = ref(false);
const showTrunkModal = ref(false);

const isGearVoicePlanActive = computed(() => {
  return !!(store.vehicleAuto.voice_enable_gear_d || store.vehicleAuto.voice_enable_gear_r || store.vehicleAuto.voice_enable_gear_p);
});

const isModeVoicePlanActive = computed(() => {
  return !!(store.vehicleAuto.voice_enable_mode_comfort || store.vehicleAuto.voice_enable_mode_eco || store.vehicleAuto.voice_enable_mode_sport || store.vehicleAuto.voice_enable_mode_smart);
});

const isDoorVoicePlanActive = computed(() => {
  if (store.vehicleAuto.voice_door_mode_universal === true) {
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
  showToast(next ? '换挡播报已开启 (D/R/P)' : '换挡播报已关闭');
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
  showToast(next ? '驾驶模式播报已开启' : '驾驶模式播报已关闭');
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
  showToast(next ? '车门播报已开启' : '车门播报已关闭');
}

function toggleAllTrunkVoice() {
  const next = !isTrunkVoicePlanActive.value;
  store.vehicleAuto.voice_enable_trunk_open = next;
  store.vehicleAuto.voice_enable_trunk_close = next;
  bridge.call('setVehicleAutomationSetting', 'voice_enable_trunk_open', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_trunk_close', next);
  showToast(next ? '电动尾门播报已开启' : '电动尾门播报已关闭');
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
    tip: '点火前 5 秒自动保护音频通道，防止原厂开机音吞音；支持在细分配置中为每种模式独立定制专属音效。',
    showCancel: false,
    confirmText: '我知道了'
  });
}

function showDoorHelp() {
  openModal('confirm', {
    title: '【功能指南】五门防抖与通用智能语音',
    desc: '1. 通用智能模式：推荐模式。开门统一播报「车门已打开」，关门统一播报「车门已关好」，多门同时动作合并防抖，不抢音。\n\n2. 独立分门模式：可分别为主驾、副驾、后排播报专属语音。',
    tip: '在二级向导中可随时切换模式，并对各项开闭动作进行个性化声效定制。',
    showCancel: false,
    confirmText: '我知道了'
  });
}

function showTrunkHelp() {
  openModal('confirm', {
    title: '【功能指南】原厂电动尾门独立语音播报',
    desc: '1. 底层独立串口：独立监听 MCU 串口 91 02 01 b7 尾门位图，与四门逻辑完全解耦独立。\n\n2. 升起防刮与锁止确认：后备箱抬起升起时安全警示，防碰擦车库顶梁；电吸完全锁止时短促确认“后备箱已关好”，关后备箱无需回头确认。',
    tip: '升起播报与锁止播报均带独立开关，支持试听与自定义音频文件。',
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

function toggleVoiceMasterSwitch() {
  const next = !store.vehicleAuto.voice_master_switch;
  store.vehicleAuto.voice_master_switch = next;
  bridge.call('setVehicleAutomationSetting', 'voice_master_switch', next);
  showToast(next ? '全车语音播报总开关: 已开启 (正常播报)' : '全车语音播报总开关: 已关闭 (全车静音)');
}

const voiceThemes = ref([]);
const activeThemeName = ref('');

function loadVoiceThemes() {
  try {
    const raw = bridge.call('getVoiceThemesJson');
    if (raw) {
      const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
      activeThemeName.value = data.activeTheme || '';
      voiceThemes.value = data.themes || [];
    }
  } catch (e) {
    voiceThemes.value = [];
  }
}

function applyVoiceTheme(themeName) {
  bridge.call('setActiveVoiceTheme', themeName);
  activeThemeName.value = themeName;
  loadVoiceThemes();
}

function confirmForceRestoreFactory() {
  openModal('confirm', {
    title: '强制重装官方原声',
    desc: '会把车上旧的原声文件全部删掉，用车机里最新版的原声重新覆盖一遍，并自动切回出厂官方原声。',
    tip: '正在使用的自定义语音包会被取消生效，但文件不会被删除，随时可以再整套启用。',
    confirmText: '立即重装原声',
    onConfirm: () => {
      const count = bridge.call('forceRestoreFactoryVoice');
      if (typeof count === 'number' && count >= 0) {
        showToast(`已强制重装原声，更新 ${count} 个音频文件`);
      } else {
        showToast('原声重装失败，请稍后重试', 'error');
      }
      loadVoiceThemes();
    }
  });
}

function testThemeVoice(themeName) {
  if (!themeName) {
    bridge.call('testVehicleVoice', 'gear_d');
  } else {
    bridge.call('playCustomAudioPath', `/sdcard/GeelyPilot/voices/${themeName}/gear_d.mp3`);
  }
}

function confirmDeleteTheme(themeName) {
  openModal('confirm', {
    title: `删除语音包【${themeName}】`,
    message: `确定要彻底删除该语音包吗？\n删除后将释放其占用的存储空间，若正在生效将自动恢复为出厂晓晓原声。`,
    isDanger: true,
    onConfirm: () => {
      bridge.call('deleteVoiceTheme', themeName);
      loadVoiceThemes();
    }
  });
}

onMounted(() => {
  window.refreshVoiceThemes = loadVoiceThemes;
  setTimeout(() => {
    loadVoiceThemes();
  }, 30);
});
</script>
