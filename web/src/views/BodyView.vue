<template>
  <div class="flex flex-col space-y-4">
    <!-- 顶部车规二级分段胶囊：一页全景掌控（按功能小类扁平拆分直达） -->
    <SubTabs :tabs="voiceTabs" v-model="activeTab" />

    <!-- 核心：全车语音播报总开关 (置顶常驻整卡开关大磁贴) -->
    <div
      @click="toggleVoiceMasterSwitch"
      :class="[
        'border-2 rounded-3xl p-5 min-h-[106px] shadow-xl flex items-center justify-between transition-all cursor-pointer select-none active:scale-[0.99]',
        store.vehicleAuto.voice_master_switch
          ? 'bg-car-item border-car-accent shadow-md'
          : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
      ]"
    >
      <div class="w-[65%] max-w-[65%] flex flex-col space-y-1.5 shrink-0">
        <div class="flex items-center space-x-3">
          <StatusDot size="lg" :color="store.vehicleAuto.voice_master_switch ? 'ok' : 'off'" :glow-px="10" class="shadow-md" />
          <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">全车语音播报总开关</span>
        </div>
        <div class="text-[14.5px] text-car-sub font-bold leading-normal">
          一键管控全车车门、尾门、挡位与驾驶模式语音播报。关闭后全车安静不发声，互不影响。
        </div>
      </div>

      <!-- 右侧：轻量化状态大磁贴（消灭外框大按钮） -->
      <div class="flex items-center space-x-3 shrink-0 pr-2">
        <StatusDot size="md" :color="store.vehicleAuto.voice_master_switch ? 'ok' : 'off'" :glow-px="8" />
        <span :class="['text-[20px] font-black tracking-wide', store.vehicleAuto.voice_master_switch ? 'text-car-text' : 'text-car-sub']">
          {{ store.vehicleAuto.voice_master_switch ? '全车播报已开启' : '全车已静音' }}
        </span>
      </div>
    </div>

    <!-- Tab 1: 换挡语音 (v1.8.32.16 合并：原来的 4 张卡合成 1 张双联卡，四个挡位格子并排) -->
    <div v-if="activeTab === 'gear'" class="flex flex-col space-y-4">
      <VoicePairCard
        title="换挡播报"
        tag="D / R / P / N"
        :items="gearItems"
        @toggle="onGearToggle"
        @test="testVoice"
        @custom="onGearCustom"
      />
    </div>

    <!-- Tab 2: 驾驶模式 (v1.8.32.16 合并：4 张卡合成 1 张双联卡) -->
    <div v-if="activeTab === 'mode'" class="flex flex-col space-y-4">
      <VoicePairCard
        title="驾驶模式播报"
        tag="舒适 / 经济 / 运动 / 智能"
        :items="modeItems"
        @toggle="onModeToggle"
        @test="testVoice"
        @custom="onModeCustom"
      />
    </div>

    <!-- Tab 3: 车门语音 (极简合并 / 独立分门 互斥模式中枢) -->
    <div v-if="activeTab === 'door'" class="flex flex-col space-y-4">
      <!-- 置顶：车门模式互斥双选卡片 -->
      <div class="grid grid-cols-2 gap-4 items-stretch">
        <!-- 模式 1: 极简合并播报 (出厂默认推荐) -->
        <div
          @click="setDoorMode(true)"
          :class="[
            'rounded-3xl border-2 p-5 shadow-xl flex flex-col justify-between transition-all duration-200 cursor-pointer select-none active:scale-[0.985]',
            store.vehicleAuto.voice_door_mode_universal === true
              ? 'bg-car-item border-car-accent shadow-md'
              : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
          ]"
        >
          <div class="flex items-center justify-between shrink-0 mb-2">
            <div class="flex items-center space-x-2.5">
              <span class="text-[20px] font-black text-car-text tracking-wide">极简合并播报</span>
              <span class="px-2.5 py-0.5 text-[12.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent shrink-0">出厂默认</span>
            </div>
            <div class="flex items-center space-x-2 shrink-0">
              <StatusDot size="sm" :color="store.vehicleAuto.voice_door_mode_universal === true ? 'ok' : 'off'" :glow-px="6" />
              <span :class="['text-[15px] font-black', store.vehicleAuto.voice_door_mode_universal === true ? 'text-car-text' : 'text-car-sub']">
                {{ store.vehicleAuto.voice_door_mode_universal === true ? '当前生效' : '未选用' }}
              </span>
            </div>
          </div>
          <div class="text-[14px] text-car-sub font-bold leading-relaxed">
            任意车门开启/关好统一播报，不区分具体哪扇门，提示清爽干脆，适合日常代步
          </div>
        </div>

        <!-- 模式 2: 独立分门播报 -->
        <div
          @click="setDoorMode(false)"
          :class="[
            'rounded-3xl border-2 p-5 shadow-xl flex flex-col justify-between transition-all duration-200 cursor-pointer select-none active:scale-[0.985]',
            store.vehicleAuto.voice_door_mode_universal !== true
              ? 'bg-car-item border-car-accent shadow-md'
              : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
          ]"
        >
          <div class="flex items-center justify-between shrink-0 mb-2">
            <div class="flex items-center space-x-2.5">
              <span class="text-[20px] font-black text-car-text tracking-wide">独立分门播报</span>
              <span class="px-2.5 py-0.5 text-[12.5px] font-black rounded-full border bg-car-item border-car-border text-car-sub shrink-0">详细迎宾</span>
            </div>
            <div class="flex items-center space-x-2 shrink-0">
              <StatusDot size="sm" :color="store.vehicleAuto.voice_door_mode_universal !== true ? 'ok' : 'off'" :glow-px="6" />
              <span :class="['text-[15px] font-black', store.vehicleAuto.voice_door_mode_universal !== true ? 'text-car-text' : 'text-car-sub']">
                {{ store.vehicleAuto.voice_door_mode_universal !== true ? '当前生效' : '未选用' }}
              </span>
            </div>
          </div>
          <div class="text-[14px] text-car-sub font-bold leading-relaxed">
            主驾、副驾、左后、右后四门独立识别开门与关门，一扇门一张卡，方位一眼对上
          </div>
        </div>
      </div>

      <!-- 极简合并播报：1 张双联卡 -->
      <div v-if="store.vehicleAuto.voice_door_mode_universal === true">
        <VoicePairCard
          title="全车车门"
          tag="任意车门"
          :items="doorUniversalItems"
          @toggle="onDoorUniversalToggle"
          @test="testVoice"
          @custom="onDoorUniversalCustom"
        />
      </div>

      <!-- 独立分门播报：4 张双联卡，按车内方位摆（左上主驾·右上副驾·左下左后·右下右后） -->
      <div v-else class="grid grid-cols-2 gap-4 items-stretch">
        <VoicePairCard
          v-for="d in doorPairDefs"
          :key="d.pos"
          :title="d.title"
          :tag="d.tag"
          :items="doorItemsOf(d.pos)"
          @toggle="(key) => onDoorToggle(d.pos, key)"
          @test="testVoice"
          @custom="(key) => onDoorCustom(d.pos, key)"
        />
      </div>
    </div>

    <!-- Tab 5: 电动尾门 (v1.8.32.16 合并：开/关合成 1 张双联卡) -->
    <div v-if="activeTab === 'trunk'" class="flex flex-col space-y-4">
      <VoicePairCard
        title="电动尾门"
        tag="后备箱"
        :items="trunkItems"
        @toggle="onTrunkToggle"
        @test="testVoice"
        @custom="onTrunkCustom"
      />
    </div>

    <!-- Tab: 前引擎盖 (v1.8.32.16 合并：开/关合成 1 张双联卡) -->
    <div v-if="activeTab === 'hood'" class="flex flex-col space-y-4">
      <VoicePairCard
        title="前引擎盖"
        tag="掀盖 / 扣盖"
        :items="hoodItems"
        @toggle="onHoodToggle"
        @test="testVoice"
        @custom="onHoodCustom"
      />

      <!-- 报文学习入口：给车主和分析用的实时数值观察窗 -->
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
          class="w-full min-h-[64px] rounded-2xl bg-car-item border-2 border-car-accent text-car-text ring-2 ring-car-accent/25 font-black text-[17px] cursor-pointer shadow-md hover:border-car-border-light transition-all active:scale-[0.99]"
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
import VoicePairCard from '../components/VoicePairCard.vue';
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
// v1.8.32.16 卡片合并：所有双联卡的 items 数据源与事件分发
// 台词预览(live)必须与 Java 端 VehicleVoicePlayer 的实际播报逐字一致
// ============================================================================

// ---------- 换挡播报（4 格并排） ----------
const gearItems = computed(() => [
  { key: 'voice_enable_gear_p', label: '驻车挡 P', line: '已驻车', active: !!store.vehicleAuto.voice_enable_gear_p },
  { key: 'voice_enable_gear_d', label: '前进挡 D', line: '前进挡', active: !!store.vehicleAuto.voice_enable_gear_d },
  { key: 'voice_enable_gear_n', label: '空挡 N', line: '空挡', active: !!store.vehicleAuto.voice_enable_gear_n },
  { key: 'voice_enable_gear_r', label: '倒车挡 R', line: '倒车挡', active: !!store.vehicleAuto.voice_enable_gear_r }
]);
function onGearToggle(key) { toggleSetting(key); }
function onGearCustom(key) {
  const map = {
    voice_enable_gear_p: ['gear_p', '驻车挡 P', 'gear_p.mp3'],
    voice_enable_gear_d: ['gear_d', '前进挡 D', 'gear_d.mp3'],
    voice_enable_gear_n: ['gear_n', '空挡 N', 'gear_n.mp3'],
    voice_enable_gear_r: ['gear_r', '倒车挡 R', 'gear_r.mp3']
  };
  const m = map[key];
  if (m) openCustomVoice(m[0], m[1], m[2]);
}

// ---------- 驾驶模式（4 格并排） ----------
const modeItems = computed(() => [
  { key: 'voice_enable_mode_comfort', label: '舒适模式', line: '舒适模式', active: !!store.vehicleAuto.voice_enable_mode_comfort },
  { key: 'voice_enable_mode_eco', label: '经济模式', line: '经济模式', active: !!store.vehicleAuto.voice_enable_mode_eco },
  { key: 'voice_enable_mode_sport', label: '运动模式', line: '运动模式', active: !!store.vehicleAuto.voice_enable_mode_sport },
  { key: 'voice_enable_mode_smart', label: '智能模式', line: '智能模式', active: !!store.vehicleAuto.voice_enable_mode_smart }
]);
function onModeToggle(key) { toggleSetting(key); }
function onModeCustom(key) {
  const map = {
    voice_enable_mode_comfort: ['mode_comfort', '舒适模式', 'mode_comfort.mp3'],
    voice_enable_mode_eco: ['mode_eco', '经济模式', 'mode_eco.mp3'],
    voice_enable_mode_sport: ['mode_sport', '运动模式', 'mode_sport.mp3'],
    voice_enable_mode_smart: ['mode_smart', '智能模式', 'mode_smart.mp3']
  };
  const m = map[key];
  if (m) openCustomVoice(m[0], m[1], m[2]);
}

// ---------- 车门：极简合并（1 张双联卡） ----------
const doorUniversalItems = computed(() => [
  { key: 'voice_enable_door_universal_open', label: '开门播报', line: '车门已打开', active: store.vehicleAuto.voice_enable_door_universal_open !== false },
  { key: 'voice_enable_door_universal_close', label: '关门播报', line: '车门已关闭', active: store.vehicleAuto.voice_enable_door_universal_close !== false }
]);
function onDoorUniversalToggle(key) { toggleSetting(key); }
function onDoorUniversalCustom(key) {
  const isOpen = key === 'voice_enable_door_universal_open';
  openCustomVoice(isOpen ? 'door_open' : 'door_close', isOpen ? '全车开门播报' : '全车关门播报', isOpen ? 'door_open.mp3' : 'door_close.mp3');
}

// ---------- 车门：独立分门（4 张双联卡，按车内方位摆） ----------
const doorPairDefs = [
  { pos: 'fl', title: '主驾驶门', tag: '左前' },
  { pos: 'fr', title: '副驾驶门', tag: '右前' },
  { pos: 'rl', title: '左后侧门', tag: '左后' },
  { pos: 'rr', title: '右后侧门', tag: '右后' }
];
const DOOR_META = {
  fl: { name: '主驾', openKey: 'voice_enable_door_fl', closeKey: 'voice_enable_door_fl_close', openLine: '主驾车门已打开', closeLine: '主驾车门已关闭' },
  fr: { name: '副驾', openKey: 'voice_enable_door_fr', closeKey: 'voice_enable_door_fr_close', openLine: '副驾车门已打开', closeLine: '副驾车门已关闭' },
  rl: { name: '左后', openKey: 'voice_enable_door_rl', closeKey: 'voice_enable_door_rl_close', openLine: '左后侧门已打开', closeLine: '左后侧门已关闭' },
  rr: { name: '右后', openKey: 'voice_enable_door_rr', closeKey: 'voice_enable_door_rr_close', openLine: '右后侧门已打开', closeLine: '右后侧门已关闭' }
};
function doorItemsOf(pos) {
  const m = DOOR_META[pos];
  return [
    { key: m.openKey, label: '开门', line: m.openLine, active: store.vehicleAuto[m.openKey] !== false },
    { key: m.closeKey, label: '关门', line: m.closeLine, active: store.vehicleAuto[m.closeKey] !== false }
  ];
}
function onDoorToggle(pos, key) { toggleSetting(key); }
function onDoorCustom(pos, key) {
  const m = DOOR_META[pos];
  const isOpen = key === m.openKey;
  const slug = isOpen ? `door_${pos}` : `door_${pos}_close`;
  const label = `${m.name}车门${isOpen ? '打开' : '关闭'}`;
  openCustomVoice(slug, label, `${slug}.mp3`);
}

// ---------- 电动尾门（1 张双联卡） ----------
const trunkItems = computed(() => [
  { key: 'voice_enable_trunk_open', label: '尾门打开', line: '后备箱已打开', active: !!store.vehicleAuto.voice_enable_trunk_open },
  { key: 'voice_enable_trunk_close', label: '尾门关闭', line: '后备箱已关闭', active: !!store.vehicleAuto.voice_enable_trunk_close }
]);
function onTrunkToggle(key) { toggleSetting(key); }
function onTrunkCustom(key) {
  const isOpen = key === 'voice_enable_trunk_open';
  openCustomVoice(isOpen ? 'trunk_open' : 'trunk_close', isOpen ? '尾门打开' : '尾门关闭', isOpen ? 'trunk_open.mp3' : 'trunk_close.mp3');
}

// ---------- 前引擎盖（1 张双联卡） ----------
const hoodItems = computed(() => [
  { key: 'voice_enable_hood_open', label: '引擎盖打开', line: '引擎盖已打开', active: !!store.vehicleAuto.voice_enable_hood_open },
  { key: 'voice_enable_hood_close', label: '引擎盖关闭', line: '引擎盖已关闭', active: !!store.vehicleAuto.voice_enable_hood_close }
]);
function onHoodToggle(key) { toggleSetting(key); }
function onHoodCustom(key) {
  const isOpen = key === 'voice_enable_hood_open';
  openCustomVoice(isOpen ? 'hood_open' : 'hood_close', isOpen ? '引擎盖打开' : '引擎盖关闭', isOpen ? 'hood_open.mp3' : 'hood_close.mp3');
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
