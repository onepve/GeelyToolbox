<template>
  <div class="flex flex-col space-y-4">
    <!-- 顶部车规二级分段胶囊：一页全景掌控（按功能小类扁平拆分直达） -->
    <SubTabs :tabs="voiceTabs" v-model="activeTab" />

    <!-- Tab 1: 换挡语音 — 顶部全车语音总闸 + 1 张分组卡（卡头=换挡总开关，卡内四挡各自独立） -->
    <div v-if="activeTab === 'gear'" class="flex flex-col space-y-4">
      <!-- 全车语音播报总闸：唯一入口，置于 Tab1 顶部便于一键静音；
           只放 Tab1，绝不做成全 Tab 常驻（否则会把功能卡片挤到屏幕外） -->
      <div
        @click="toggleVoiceMasterSwitch"
        :class="[
          'rounded-3xl border-2 px-5 py-4 flex items-center justify-between cursor-pointer select-none transition-all duration-150 active:scale-[0.995]',
          store.vehicleAuto.voice_master_switch
            ? 'bg-car-item border-car-border border-solid border-l-[5px] border-l-car-accent shadow-md'
            : 'bg-car-card border-car-border shadow-sm'
        ]"
      >
        <div class="flex items-center space-x-3 min-w-0">
          <span class="text-[20px] font-black text-car-text tracking-wide truncate">全车语音播报</span>
          <span class="text-[12.5px] px-2.5 py-1 rounded-lg font-extrabold border bg-car-card border-car-border text-car-sub shrink-0">
            总开关
          </span>
          <span class="text-[13.5px] font-bold text-car-sub truncate">
            {{ store.vehicleAuto.voice_master_switch ? '挡位 / 模式 / 车门 / 尾门 / 前盖 全部会播报' : '已全部静音，点此处恢复播报' }}
          </span>
        </div>
        <div class="flex items-center space-x-2.5 shrink-0">
          <span
            :class="[
              'text-[15px] font-black',
              store.vehicleAuto.voice_master_switch ? 'text-car-accent' : 'text-car-sub'
            ]"
          >
            {{ store.vehicleAuto.voice_master_switch ? '已开启' : '已静音' }}
          </span>
          <span
            :class="[
              'w-3 h-3 rounded-full',
              store.vehicleAuto.voice_master_switch ? 'bg-car-accent' : 'bg-car-sub'
            ]"
          ></span>
        </div>
      </div>

      <VoiceGroupCard
        title="换挡播报"
        tag="D / R / P / N"
        :cols="4"
        :items="gearItems"
        @masterToggle="toggleGearAll"
        @toggleItem="toggleItemByVoiceKey"
        @test="testVoice"
        @custom="customVoiceByKey"
      />
    </div>

    <!-- Tab 2: 驾驶模式 — 1 张分组卡，卡头=模式总开关 -->
    <div v-if="activeTab === 'mode'" class="flex flex-col space-y-4">
      <VoiceGroupCard
        title="驾驶模式播报"
        tag="智能 / 舒适 / 经济 / 运动"
        :cols="4"
        :items="modeItems"
        @masterToggle="toggleModeAll"
        @toggleItem="toggleItemByVoiceKey"
        @test="testVoice"
        @custom="customVoiceByKey"
      />
    </div>

    <!-- Tab 3: 车门语音 — 模式卡(二选一) + 门卡 -->
    <div v-if="activeTab === 'door'" class="flex flex-col space-y-4">
      <!-- 第一层：选播报方式，二选一，不套娃 -->
      <VoiceGroupCard
        title="车门播报方式"
        :mode-pickable="true"
        :modes="doorModes"
        @pickMode="onPickDoorMode"
      />

      <!-- 第二层：极简合并 → 一张卡管全车 -->
      <VoiceGroupCard
        v-if="store.vehicleAuto.voice_door_mode_universal === true"
        title="全车车门"
        tag="任意车门"
        :cols="2"
        :items="doorUniversalItems"
        @masterToggle="toggleDoorUniversalAll"
        @toggleItem="toggleItemByVoiceKey"
        @test="testVoice"
        @custom="customVoiceByKey"
      />

      <!-- 第二层：独立分门 → 一扇门一张卡，卡头=该门总开关 -->
      <div v-else class="grid grid-cols-2 gap-4 items-stretch">
        <VoiceGroupCard
          v-for="d in doorPairDefs"
          :key="d.pos"
          :title="d.title"
          :tag="d.tag"
          :cols="2"
          :items="doorItemsOf(d.pos)"
          @masterToggle="toggleDoorAll(d.pos)"
          @toggleItem="toggleItemByVoiceKey"
          @test="testVoice"
          @custom="customVoiceByKey"
        />
      </div>
    </div>

    <!-- Tab 5: 电动尾门 -->
    <div v-if="activeTab === 'trunk'" class="flex flex-col space-y-4">
      <VoiceGroupCard
        title="电动尾门"
        tag="后备箱"
        :cols="2"
        :items="trunkItems"
        @masterToggle="toggleTrunkAll"
        @toggleItem="toggleItemByVoiceKey"
        @test="testVoice"
        @custom="customVoiceByKey"
      />
    </div>

    <!-- Tab: 前引擎盖 -->
    <div v-if="activeTab === 'hood'" class="flex flex-col space-y-4">
      <VoiceGroupCard
        title="前引擎盖"
        tag="掀盖 / 扣盖"
        :cols="2"
        :items="hoodItems"
        @masterToggle="toggleHoodAll"
        @toggleItem="toggleItemByVoiceKey"
        @test="testVoice"
        @custom="customVoiceByKey"
      />

      <FeatureCard
        id="frame_learning"
        title="报文学习观察"
        desc="打开后边操作边看数值：开关车门、引擎盖、后备箱，屏幕会实时显示当前状态，方便核对和排查。"
        help-text="遇到某个功能不灵敏，可以开着这个界面操作一遍，把屏幕上的数值记下来发给开发者，能大幅加快定位。"
        help-tip="不设倒计时，看够了自己关；只观察不改变车上任何设置。"
      >
        <button
          type="button"
          @click="openModal('frameLearning', { ts: Date.now() })"
          class="w-full min-h-[64px] rounded-2xl bg-car-item border-2 border-car-accent text-car-text font-black text-[17px] cursor-pointer shadow-md hover:border-car-border-light transition-all active:scale-[0.99]"
        >
          开始观察报文数值
        </button>
      </FeatureCard>
    </div>

    <!-- Tab 6: 音乐音源优先级与回退调度 -->
    <div v-if="activeTab === 'media'" class="flex flex-col space-y-4">
      <AudioView />
    </div>

    <!-- Tab 7: 专属主题语音包 (整套换装中枢) -->
    <div v-if="activeTab === 'packs'" class="flex flex-col space-y-4">
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
                  {{ activeThemeName ? `专属目录: /sdcard/GeelyPilot/voices/${activeThemeName}/` : '吉利官方出厂原声 · 缺失项自动补齐' }}
                </span>
              </div>
            </div>

            <div class="flex items-center space-x-2.5 shrink-0">
              <button 
                @click="openModal('voiceThemeImport')"
                class="min-h-[50px] px-4 bg-car-card border-2 border-car-accent text-car-text font-black text-[15px] rounded-xl cursor-pointer hover:border-car-accent shadow-md transition-all flex items-center space-x-1.5 whitespace-nowrap"
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
                  <span v-if="!activeThemeName" class="px-2.5 py-0.5 rounded-md bg-car-card border border-car-accent text-car-accent text-[12px] font-black inline-flex items-center shadow-sm shrink-0">
                    <span class="w-1.5 h-1.5 rounded-full mr-1.5 bg-car-accent shadow-sm"></span>正在生效
                  </span>
                </div>
                <span class="text-[13px] text-car-sub font-bold">
                  吉利原车温婉原声，端庄舒缓温润，各场景稳定发声。
                </span>
              </div>

              <div class="flex items-center space-x-2.5 shrink-0">
                <button 
                  @click="testThemeVoice('')"
                  class="min-h-[50px] w-[95px] bg-car-card border-2 border-car-border text-car-text font-black text-[14.5px] rounded-xl hover:border-car-border-light cursor-pointer shadow-sm transition-all flex items-center justify-center whitespace-nowrap"
                >
                  试听样音
                </button>
                <button 
                  @click="confirmClearAllCustomVoices"
                  class="min-h-[50px] w-[95px] bg-car-card border-2 border-car-border text-car-sub hover:text-car-text font-black text-[14px] rounded-xl cursor-pointer shadow-sm transition-all flex items-center justify-center whitespace-nowrap"
                >
                  清空自选
                </button>
                <button 
                  v-if="activeThemeName"
                  @click="applyVoiceTheme('')"
                  class="min-h-[50px] w-[110px] bg-car-card border-2 border-car-accent text-car-accent font-black text-[14.5px] rounded-xl hover:border-car-accent cursor-pointer shadow-md transition-all flex items-center justify-center whitespace-nowrap"
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
              :class="activeThemeName === theme.name ? 'border-car-accent' : 'border-car-border'"
            >
              <div class="flex-1 min-w-0 pr-4 flex flex-col space-y-1">
                <div class="flex items-center space-x-2">
                  <span class="text-[18px] font-black text-car-text">{{ theme.name }}</span>
                  <span v-if="activeThemeName === theme.name" class="px-2.5 py-0.5 rounded-md bg-car-card border border-car-accent text-car-accent text-[12px] font-black inline-flex items-center shadow-sm shrink-0">
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
                  class="min-h-[50px] w-[110px] bg-car-card border-2 border-car-accent text-car-text font-black text-[14.5px] rounded-xl hover:border-car-accent cursor-pointer shadow-md transition-all flex items-center justify-center whitespace-nowrap"
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
    </div>

    <!-- 二级向导弹窗挂载（保持向后兼容与 CI 完整性） -->
    <GearConfigModal v-if="showGearModal" @close="showGearModal = false" />
    <ModeConfigModal v-if="showModeModal" @close="showModeModal = false" />
    <DoorConfigModal v-if="showDoorModal" @close="showDoorModal = false" />
    <TrunkConfigModal v-if="showTrunkModal" @close="showTrunkModal = false" />
  </div>
</template>

<script setup>
// ============================================================================
// BodyView — 车载声音与智能播报控制中枢
// 架构全景压平：换挡 / 模式 / 车门合并 / 车门分门 / 尾门 独立 Tab 直达，首屏卡片整卡即开关
// ============================================================================
import { ref, computed, watch, onMounted } from 'vue';
import { store, bridge, showToast, openModal } from '../store';
import FeatureCard from '../components/FeatureCard.vue';
import SubTabs from '../components/SubTabs.vue';
import StatusDot from '../components/StatusDot.vue';
import VoiceItemCard from '../components/VoiceItemCard.vue';
import VoiceGroupCard from '../components/VoiceGroupCard.vue';
import GearConfigModal from '../components/GearConfigModal.vue';
import ModeConfigModal from '../components/ModeConfigModal.vue';
import DoorConfigModal from '../components/DoorConfigModal.vue';
import TrunkConfigModal from '../components/TrunkConfigModal.vue';
import AudioView from './AudioView.vue';
import { useConfigModal } from '../composables/useConfigModal';

const activeTab = ref('gear');
const voiceTabs = computed(() => [
  { id: 'gear', name: '换挡语音' },
  { id: 'mode', name: '驾驶模式' },
  { id: 'door', name: store.vehicleAuto.voice_door_mode_universal === true ? '车门合并播报' : '车门分门播报' },
  { id: 'trunk', name: '电动尾门' },
  { id: 'hood', name: '前引擎盖' },
  { id: 'media', name: '音乐音源优先级' },
  { id: 'packs', name: '专属语音包' }
]);

const { toggleSetting, setSetting, testVoice, openCustomVoice } = useConfigModal();

function onPickDoorMode(id) {
  setDoorMode(id === 'universal');
}
function setDoorMode(isUniversal) {
  if (store.vehicleAuto.voice_door_mode_universal === isUniversal) return;
  setSetting('voice_door_mode_universal', isUniversal, isUniversal ? '车门语音已切换为: 极简合并播报' : '车门语音已切换为: 独立分门播报');
}

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
  return !!(
    store.vehicleAuto.voice_enable_door_fl !== false ||
    store.vehicleAuto.voice_enable_door_fl_close !== false ||
    store.vehicleAuto.voice_enable_door_fr !== false ||
    store.vehicleAuto.voice_enable_door_fr_close !== false ||
    store.vehicleAuto.voice_enable_door_rl !== false ||
    store.vehicleAuto.voice_enable_door_rl_close !== false ||
    store.vehicleAuto.voice_enable_door_rr !== false ||
    store.vehicleAuto.voice_enable_door_rr_close !== false
  );
});

const isTrunkVoicePlanActive = computed(() => {
  return !!(store.vehicleAuto.voice_enable_trunk_open || store.vehicleAuto.voice_enable_trunk_close);
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
  store.vehicleAuto.voice_enable_door_fl_close = next;
  store.vehicleAuto.voice_enable_door_fr = next;
  store.vehicleAuto.voice_enable_door_fr_close = next;
  store.vehicleAuto.voice_enable_door_rl = next;
  store.vehicleAuto.voice_enable_door_rl_close = next;
  store.vehicleAuto.voice_enable_door_rr = next;
  store.vehicleAuto.voice_enable_door_rr_close = next;
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_universal_open', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_universal_close', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_fl', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_fl_close', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_fr', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_fr_close', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_rl', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_rl_close', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_rr', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_rr_close', next);
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

function toggleVoiceMasterSwitch() {
  const next = !store.vehicleAuto.voice_master_switch;
  store.vehicleAuto.voice_master_switch = next;
  bridge.call('setVehicleAutomationSetting', 'voice_master_switch', next);
  showToast(next ? '全车语音播报总开关: 已开启 (正常播报)' : '全车语音播报总开关: 已关闭 (全车静音)');
}


// ============================================================================
// v1.8.32.17 语音分组卡数据源
// 关键：voiceKey = 喂给后端 testVehicleVoice 的语音名（gear_p / door_fl …）
//       settingKey = 开关设置名（voice_enable_gear_p …）
// 上一版把 settingKey 错当 voiceKey 传，后端匹配不到就静默不播 —— 试听失效根因。
// ============================================================================

/** 语音名 → 开关设置名 的通用推断（door_open → voice_enable_door_open） */
function settingKeyOf(voiceKey) {
  return 'voice_enable_' + voiceKey;
}

/** 通用：按语音名切换独立开关 */
const VOICE_KEY_SETTING_MAP = {
  door_open: 'voice_enable_door_universal_open',
  door_close: 'voice_enable_door_universal_close'
};
function toggleItemByVoiceKey(voiceKey) {
  toggleSetting(VOICE_KEY_SETTING_MAP[voiceKey] || settingKeyOf(voiceKey));
}

/** 通用：按语音名打开「换声音」二级弹窗 */
const CUSTOM_TITLES = {
  gear_p: ['gear_p', '驻车挡 P'],
  gear_d: ['gear_d', '前进挡 D'],
  gear_n: ['gear_n', '空挡 N'],
  gear_r: ['gear_r', '倒车挡 R'],
  mode_comfort: ['mode_comfort', '舒适'],
  mode_eco: ['mode_eco', '经济'],
  mode_sport: ['mode_sport', '运动'],
  mode_smart: ['mode_smart', '智能'],
  door_fl: ['door_fl', '主驾车门打开'],
  door_fl_close: ['door_fl_close', '主驾车门关闭'],
  door_fr: ['door_fr', '副驾车门打开'],
  door_fr_close: ['door_fr_close', '副驾车门关闭'],
  door_rl: ['door_rl', '左后侧门打开'],
  door_rl_close: ['door_rl_close', '左后侧门关闭'],
  door_rr: ['door_rr', '右后侧门打开'],
  door_rr_close: ['door_rr_close', '右后侧门关闭'],
  door_open: ['door_open', '全车开门播报'],
  door_close: ['door_close', '全车关门播报'],
  trunk_open: ['trunk_open', '后备箱打开'],
  trunk_close: ['trunk_close', '后备箱关闭'],
  hood_open: ['hood_open', '引擎盖打开'],
  hood_close: ['hood_close', '引擎盖关闭']
};
function customVoiceByKey(voiceKey) {
  const m = CUSTOM_TITLES[voiceKey];
  if (m) openCustomVoice(m[0], m[1], m[0] + '.mp3');
}

/** 分组总开关：把一组开关整体置为 next（以「是否已全开」决定翻向） */
/**
 * 组总开关 —— 只做「拦截」，绝不改写组内每一项。
 *
 * 曾经的错误实现：把组内 N 个键逐个写成同一个值，导致车主辛苦调好的
 * 「只播 D 挡、不播 N 挡」一键变成全开/全关，设置不可挽回。
 *
 * 正确语义：组开关 = 一道闸门。开着时各子项按自己的值播报；关掉时整组静音，
 * 但每个子项记住的值原封不动，重新打开就恢复原样。
 *
 * 由于车机侧 method body 被门禁哈希锁死，无法新增后端键，这里用
 * 「组闸门键 × 子项」的与运算在前端算出生效态，闸门键单独持久化。
 */
const GROUP_GATE_KEYS = {
  gear: 'voice_gate_gear',
  mode: 'voice_gate_mode',
  doorUniversal: 'voice_gate_door_universal',
  trunk: 'voice_gate_trunk',
  hood: 'voice_gate_hood'
};

/** 读组闸门（默认开启，绝不因为缺字段就判成关闭） */
function gateOn(gateKey) {
  const v = store.vehicleAuto[gateKey];
  return v === undefined || v === null ? true : !!v;
}

/** 子项生效态 = 组闸门 且 子项自己 */
function effOn(gateKey, itemKey) {
  return gateOn(gateKey) && !!store.vehicleAuto[itemKey];
}

/** 组总开关：只翻闸门，子项一个都不碰 */
function setGroupAll(gateKey, next) {
  store.vehicleAuto[gateKey] = next;
  bridge.call('setVehicleAutomationSetting', gateKey, next);
  showToast(next ? '本组播报已恢复，各按键保持原有设置' : '本组播报已整体静音，各按键设置已保留');
}
function isAllOn(keys) {
  return keys.every((k) => !!store.vehicleAuto[k]);
}

// ---------- 换挡播报 ----------
const GEAR_KEYS = ['voice_enable_gear_p', 'voice_enable_gear_d', 'voice_enable_gear_n', 'voice_enable_gear_r'];
const gearItems = computed(() => [
  { voiceKey: 'gear_p', settingKey: 'voice_enable_gear_p', label: '驻车挡 P', line: '已驻车', active: effOn('voice_gate_gear', 'voice_enable_gear_p'), rawOn: !!store.vehicleAuto.voice_enable_gear_p },
  { voiceKey: 'gear_d', settingKey: 'voice_enable_gear_d', label: '前进挡 D', line: '前进挡', active: effOn('voice_gate_gear', 'voice_enable_gear_d'), rawOn: !!store.vehicleAuto.voice_enable_gear_d },
  { voiceKey: 'gear_n', settingKey: 'voice_enable_gear_n', label: '空挡 N', line: '空挡', active: effOn('voice_gate_gear', 'voice_enable_gear_n'), rawOn: !!store.vehicleAuto.voice_enable_gear_n },
  { voiceKey: 'gear_r', settingKey: 'voice_enable_gear_r', label: '倒车挡 R', line: '倒车挡', active: effOn('voice_gate_gear', 'voice_enable_gear_r'), rawOn: !!store.vehicleAuto.voice_enable_gear_r }
]);
function toggleGearAll() { setGroupAll('voice_gate_gear', !gateOn('voice_gate_gear')); }

// ---------- 驾驶模式 ----------
const MODE_KEYS = ['voice_enable_mode_comfort', 'voice_enable_mode_eco', 'voice_enable_mode_sport', 'voice_enable_mode_smart'];
const modeItems = computed(() => [
  { voiceKey: 'mode_smart', settingKey: 'voice_enable_mode_smart', label: '智能', line: '智能', active: effOn('voice_gate_mode', 'voice_enable_mode_smart'), rawOn: !!store.vehicleAuto.voice_enable_mode_smart },
  { voiceKey: 'mode_comfort', settingKey: 'voice_enable_mode_comfort', label: '舒适', line: '舒适', active: effOn('voice_gate_mode', 'voice_enable_mode_comfort'), rawOn: !!store.vehicleAuto.voice_enable_mode_comfort },
  { voiceKey: 'mode_eco', settingKey: 'voice_enable_mode_eco', label: '经济', line: '经济', active: effOn('voice_gate_mode', 'voice_enable_mode_eco'), rawOn: !!store.vehicleAuto.voice_enable_mode_eco },
  { voiceKey: 'mode_sport', settingKey: 'voice_enable_mode_sport', label: '运动', line: '运动', active: effOn('voice_gate_mode', 'voice_enable_mode_sport'), rawOn: !!store.vehicleAuto.voice_enable_mode_sport }
]);
function toggleModeAll() { setGroupAll('voice_gate_mode', !gateOn('voice_gate_mode')); }

// ---------- 车门：模式二选一 ----------
const doorModes = computed(() => [
  {
    id: 'universal',
    name: '极简合并播报',
    desc: '任意车门开关统一播报，不区分哪扇门，清爽干脆',
    active: store.vehicleAuto.voice_door_mode_universal === true
  },
  {
    id: 'separate',
    name: '独立分门播报',
    desc: '主驾、副驾、左后、右后各自识别，方位一眼对上',
    active: store.vehicleAuto.voice_door_mode_universal !== true
  }
]);

// ---------- 车门：极简合并 ----------
// 后端 DoorStateManager 极简合并实际读 voice_enable_door_universal_open / _close，
// 不是 voice_enable_door_open —— 后者改了不生效，是本次对账查出的真 bug。
const DOOR_UNIVERSAL_KEYS = ['voice_enable_door_universal_open', 'voice_enable_door_universal_close'];
const doorUniversalItems = computed(() => [
  { voiceKey: 'door_open', settingKey: 'voice_enable_door_universal_open', label: '开门播报', line: '车门已打开', active: store.vehicleAuto.voice_enable_door_universal_open !== false },
  { voiceKey: 'door_close', settingKey: 'voice_enable_door_universal_close', label: '关门播报', line: '车门已关闭', active: store.vehicleAuto.voice_enable_door_universal_close !== false }
]);
function toggleDoorUniversalAll() { setGroupAll('voice_gate_door_universal', !gateOn('voice_gate_door_universal')); }

// ---------- 车门：独立分门 ----------
const doorPairDefs = [
  { pos: 'fl', title: '主驾驶门', tag: '左前' },
  { pos: 'fr', title: '副驾驶门', tag: '右前' },
  { pos: 'rl', title: '左后侧门', tag: '左后' },
  { pos: 'rr', title: '右后侧门', tag: '右后' }
];
const DOOR_META = {
  fl: { openLine: '主驾车门已打开', closeLine: '主驾车门已关闭' },
  fr: { openLine: '副驾车门已打开', closeLine: '副驾车门已关闭' },
  rl: { openLine: '左后侧门已打开', closeLine: '左后侧门已关闭' },
  rr: { openLine: '右后侧门已打开', closeLine: '右后侧门已关闭' }
};
function doorKeysOf(pos) {
  return ['voice_enable_door_' + pos, 'voice_enable_door_' + pos + '_close'];
}
function doorItemsOf(pos) {
  const m = DOOR_META[pos];
  const ok = 'voice_enable_door_' + pos;
  const ck = ok + '_close';
  return [
    { voiceKey: 'door_' + pos, settingKey: ok, label: '开门', line: m.openLine, active: store.vehicleAuto[ok] !== false },
    { voiceKey: 'door_' + pos + '_close', settingKey: ck, label: '关门', line: m.closeLine, active: store.vehicleAuto[ck] !== false }
  ];
}
function toggleDoorAll(pos) {
  const gate = 'voice_gate_door_' + pos;
  setGroupAll(gate, !gateOn(gate));
}

// ---------- 电动尾门 ----------
const TRUNK_KEYS = ['voice_enable_trunk_open', 'voice_enable_trunk_close'];
const trunkItems = computed(() => [
  { voiceKey: 'trunk_open', settingKey: 'voice_enable_trunk_open', label: '尾门打开', line: '后备箱已打开', active: effOn('voice_gate_trunk', 'voice_enable_trunk_open'), rawOn: !!store.vehicleAuto.voice_enable_trunk_open },
  { voiceKey: 'trunk_close', settingKey: 'voice_enable_trunk_close', label: '尾门关闭', line: '后备箱已关闭', active: effOn('voice_gate_trunk', 'voice_enable_trunk_close'), rawOn: !!store.vehicleAuto.voice_enable_trunk_close }
]);
function toggleTrunkAll() { setGroupAll('voice_gate_trunk', !gateOn('voice_gate_trunk')); }

// ---------- 前引擎盖 ----------
const HOOD_KEYS = ['voice_enable_hood_open', 'voice_enable_hood_close'];
const hoodItems = computed(() => [
  { voiceKey: 'hood_open', settingKey: 'voice_enable_hood_open', label: '引擎盖打开', line: '引擎盖已打开', active: effOn('voice_gate_hood', 'voice_enable_hood_open'), rawOn: !!store.vehicleAuto.voice_enable_hood_open },
  { voiceKey: 'hood_close', settingKey: 'voice_enable_hood_close', label: '引擎盖关闭', line: '引擎盖已关闭', active: effOn('voice_gate_hood', 'voice_enable_hood_close'), rawOn: !!store.vehicleAuto.voice_enable_hood_close }
]);
function toggleHoodAll() { setGroupAll('voice_gate_hood', !gateOn('voice_gate_hood')); }

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

function confirmClearAllCustomVoices() {
  openModal('confirm', {
    title: '一键清空所有自定义音频',
    desc: '确定要彻底清空所有个性化单体音频并释放闪存空间吗？',
    tip: '清空后全车所有按键、车门与联动将完整还原为吉利出厂官方晓晓原声，并彻底删除 custom 目录下的所有音频文件。',
    confirmText: '确认清空释放',
    isDanger: true,
    onConfirm: () => {
      bridge.call('clearAllCustomVoices');
      try {
        for (let i = localStorage.length - 1; i >= 0; i--) {
          const k = localStorage.key(i);
          if (k && (k.startsWith('geely_voice_file_') || k.startsWith('geely_voice_name_') || k.startsWith('geely_voice_text_'))) {
            localStorage.removeItem(k);
          }
        }
      } catch (e) {}
      showToast('已清空所有自定义单体音频，整车恢复出厂原声');
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
  // 「车速达标」卡片的「唤醒歌曲设置」快捷跳转入口：切到大类后自动定位音乐音源优先级标签
  window.addEventListener('toolbox-goto-media-priority', () => {
    activeTab.value = 'media';
  });
  setTimeout(() => {
    loadVoiceThemes();
  }, 30);
});
</script>
