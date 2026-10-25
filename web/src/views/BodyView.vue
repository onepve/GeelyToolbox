<template>
  <div class="flex flex-col space-y-4">
    <!-- 顶部车规二级分段胶囊：一页全景掌控（按功能小类扁平拆分直达） -->
    <SubTabs :tabs="voiceTabs" v-model="activeTab" />

    <!-- 核心：全车语音播报总开关 (置顶常驻) -->
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

    <!-- Tab 1: 换挡语音 (D / R / P / N 4 大实体开关卡片平铺首屏) -->
    <div v-if="activeTab === 'gear'" class="flex flex-col space-y-4">
      <div class="grid grid-cols-2 gap-4 items-stretch">
        <VoiceItemCard
          title="前进挡 (D 挡)"
          tag="D 挡"
          desc="踩刹车挂入前进 D 挡触发启程语音播报"
          :active="!!store.vehicleAuto.voice_enable_gear_d"
          @toggle="toggleSetting('voice_enable_gear_d')"
          @test="testVoice('gear_d')"
          @custom="openCustomVoice('gear_d', '前进挡 (D 挡)', 'gear_d.mp3')"
        />
        <VoiceItemCard
          title="倒车挡 (R 挡)"
          tag="R 挡"
          desc="挂入倒车 R 挡触发倒车警示与后方安全提醒"
          :active="!!store.vehicleAuto.voice_enable_gear_r"
          @toggle="toggleSetting('voice_enable_gear_r')"
          @test="testVoice('gear_r')"
          @custom="openCustomVoice('gear_r', '倒车挡 (R 挡)', 'gear_r.mp3')"
        />
        <VoiceItemCard
          title="驻车挡 (P 挡)"
          tag="P 挡"
          desc="挂回驻车 P 挡播报，并立即安全归零行程状态机"
          :active="!!store.vehicleAuto.voice_enable_gear_p"
          @toggle="toggleSetting('voice_enable_gear_p')"
          @test="testVoice('gear_p')"
          @custom="openCustomVoice('gear_p', '驻车挡 (P 挡)', 'gear_p.mp3')"
        />
        <VoiceItemCard
          title="空挡 (N 挡)"
          tag="N 挡"
          desc="挂入空挡 N 挡防溜车提醒，默认关闭防红绿灯打扰"
          :active="!!store.vehicleAuto.voice_enable_gear_n"
          @toggle="toggleSetting('voice_enable_gear_n')"
          @test="testVoice('gear_n')"
          @custom="openCustomVoice('gear_n', '空挡 (N 挡)', 'gear_n.mp3')"
        />
      </div>
    </div>

    <!-- Tab 2: 驾驶模式 (舒适 / 经济 / 运动 / 智能 4 大实体开关卡片平铺首屏) -->
    <div v-if="activeTab === 'mode'" class="flex flex-col space-y-4">
      <div class="grid grid-cols-2 gap-4 items-stretch">
        <VoiceItemCard
          title="舒适模式 (Comfort)"
          tag="舒适"
          desc="中控旋钮切入舒适模式，换挡平顺温润知性播报"
          :active="!!store.vehicleAuto.voice_enable_mode_comfort"
          @toggle="toggleSetting('voice_enable_mode_comfort')"
          @test="testVoice('mode_comfort')"
          @custom="openCustomVoice('mode_comfort', '舒适模式', 'mode_comfort.mp3')"
        />
        <VoiceItemCard
          title="经济模式 (Eco)"
          tag="经济"
          desc="中控旋钮切入经济模式，极致节油长途巡航播报"
          :active="!!store.vehicleAuto.voice_enable_mode_eco"
          @toggle="toggleSetting('voice_enable_mode_eco')"
          @test="testVoice('mode_eco')"
          @custom="openCustomVoice('mode_eco', '经济模式', 'mode_eco.mp3')"
        />
        <VoiceItemCard
          title="运动模式 (Sport)"
          tag="运动"
          desc="中控旋钮切入运动模式，激活动感声浪与热血播报"
          :active="!!store.vehicleAuto.voice_enable_mode_sport"
          @toggle="toggleSetting('voice_enable_mode_sport')"
          @test="testVoice('mode_sport')"
          @custom="openCustomVoice('mode_sport', '运动模式', 'mode_sport.mp3')"
        />
        <VoiceItemCard
          title="智能模式 (Smart)"
          tag="智能"
          desc="中控旋钮切入智能模式，自适应工况智能调度播报"
          :active="!!store.vehicleAuto.voice_enable_mode_smart"
          @toggle="toggleSetting('voice_enable_mode_smart')"
          @test="testVoice('mode_smart')"
          @custom="openCustomVoice('mode_smart', '智能模式', 'mode_smart.mp3')"
        />
      </div>
    </div>

    <!-- Tab 3: 车门合并播报 (极简模式：通用开门 + 通用关门，全平铺卡片零按钮) -->
    <div v-if="activeTab === 'door_universal'" class="flex flex-col space-y-4">
      <div class="grid grid-cols-2 gap-4 items-stretch">
        <VoiceItemCard
          title="通用开门提醒"
          tag="开门提示"
          desc="任意车门开启触发下车防落物品与行车防误开报警"
          :active="store.vehicleAuto.voice_enable_door_universal_open !== false"
          @toggle="toggleSetting('voice_enable_door_universal_open')"
          @test="testVoice('door_open')"
          @custom="openCustomVoice('door_open', '通用开门提醒', 'door_open.mp3')"
        />
        <VoiceItemCard
          title="通用关门好提示"
          tag="关门提示"
          desc="全车车门关好就位确认，告别反复回头观望"
          :active="store.vehicleAuto.voice_enable_door_universal_close !== false"
          @toggle="toggleSetting('voice_enable_door_universal_close')"
          @test="testVoice('door_close')"
          @custom="openCustomVoice('door_close', '通用关门好提示', 'door_close.mp3')"
        />
      </div>
    </div>

    <!-- Tab 4: 车门分门播报 (四门独立迎宾：主驾 / 副驾 / 左后 / 右后，全平铺卡片零按钮) -->
    <div v-if="activeTab === 'door_detail'" class="flex flex-col space-y-4">
      <div class="grid grid-cols-2 gap-4 items-stretch">
        <VoiceItemCard
          title="主驾驶门迎宾"
          tag="FL 主驾"
          desc="主驾驶门专属登车迎宾与启程提示"
          :active="!!store.vehicleAuto.voice_enable_door_fl"
          @toggle="toggleSetting('voice_enable_door_fl')"
          @test="testVoice('door_fl')"
          @custom="openCustomVoice('door_fl', '主驾驶车门', 'door_fl.mp3')"
        />
        <VoiceItemCard
          title="副驾驶门迎宾"
          tag="FR 副驾"
          desc="副驾驶乘客登车温馨提示与关门确认"
          :active="!!store.vehicleAuto.voice_enable_door_fr"
          @toggle="toggleSetting('voice_enable_door_fr')"
          @test="testVoice('door_fr')"
          @custom="openCustomVoice('door_fr', '副驾驶车门', 'door_fr.mp3')"
        />
        <VoiceItemCard
          title="左后排车门迎宾"
          tag="RL 左后"
          desc="左后排尊贵乘客登车提示与下车防开门碰擦"
          :active="!!store.vehicleAuto.voice_enable_door_rl"
          @toggle="toggleSetting('voice_enable_door_rl')"
          @test="testVoice('door_rl')"
          @custom="openCustomVoice('door_rl', '左后排车门', 'door_rl.mp3')"
        />
        <VoiceItemCard
          title="右后排车门迎宾"
          tag="RR 右后"
          desc="右后排乘客登车提示与下车关门确认"
          :active="!!store.vehicleAuto.voice_enable_door_rr"
          @toggle="toggleSetting('voice_enable_door_rr')"
          @test="testVoice('door_rr')"
          @custom="openCustomVoice('door_rr', '右后排车门', 'door_rr.mp3')"
        />
      </div>
    </div>

    <!-- Tab 5: 电动尾门 (开启升起 + 闭锁关好 2 大实体开关卡片平铺首屏) -->
    <div v-if="activeTab === 'trunk'" class="flex flex-col space-y-4">
      <div class="grid grid-cols-2 gap-4 items-stretch">
        <VoiceItemCard
          title="尾门升起提醒"
          tag="开门提示"
          desc="尾门按键触发升起短促提醒，防止碰擦低矮车库顶梁"
          :active="!!store.vehicleAuto.voice_enable_trunk_open"
          @toggle="toggleSetting('voice_enable_trunk_open')"
          @test="testVoice('trunk_open')"
          @custom="openCustomVoice('trunk_open', '尾门升起提醒', 'trunk_open.mp3')"
        />
        <VoiceItemCard
          title="尾门完全锁止"
          tag="关门提示"
          desc="电吸闭合锁止时播报确认，无需回头确认关门"
          :active="!!store.vehicleAuto.voice_enable_trunk_close"
          @toggle="toggleSetting('voice_enable_trunk_close')"
          @test="testVoice('trunk_close')"
          @custom="openCustomVoice('trunk_close', '尾门完全锁止', 'trunk_close.mp3')"
        />
      </div>
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
                  {{ activeThemeName ? `专属目录: /sdcard/GeelyPilot/voices/${activeThemeName}/` : '吉利智驾出厂原声 · 未包含项自动补齐兜底' }}
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
                  吉利原车温婉知性原声，端庄舒缓温润。零音频丢失，全场景兜底保障。
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
import GearConfigModal from '../components/GearConfigModal.vue';
import ModeConfigModal from '../components/ModeConfigModal.vue';
import DoorConfigModal from '../components/DoorConfigModal.vue';
import TrunkConfigModal from '../components/TrunkConfigModal.vue';
import AudioView from './AudioView.vue';
import { useConfigModal } from '../composables/useConfigModal';

const activeTab = ref(store.vehicleAuto.voice_door_mode_universal === true ? 'door_universal' : 'gear');
const voiceTabs = [
  { id: 'gear', name: '换挡语音' },
  { id: 'mode', name: '驾驶模式' },
  { id: 'door_universal', name: '车门合并播报' },
  { id: 'door_detail', name: '车门分门播报' },
  { id: 'trunk', name: '电动尾门' },
  { id: 'media', name: '音乐音源优先级' },
  { id: 'packs', name: '专属语音包' }
];

const { toggleSetting, setSetting, testVoice, openCustomVoice } = useConfigModal();

function setDoorMode(isUniversal) {
  setSetting('voice_door_mode_universal', isUniversal, isUniversal ? '车门语音已切换为: 极简合并播报' : '车门语音已切换为: 独立分门播报');
}

// 监听 Tab 切换：切入车门合并播报或分门播报时，底层状态自动同步切换，告别奇怪按钮
watch(activeTab, (newTab) => {
  if (newTab === 'door_universal') {
    if (store.vehicleAuto.voice_door_mode_universal !== true) {
      setDoorMode(true);
    }
  } else if (newTab === 'door_detail') {
    if (store.vehicleAuto.voice_door_mode_universal === true) {
      setDoorMode(false);
    }
  }
});

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

function confirmClearAllCustomVoices() {
  openModal('confirm', {
    title: '一键清空所有自定义音频',
    desc: '确定要彻底清空所有个性化单体音频并释放闪存空间吗？',
    tip: '清空后全车所有按键、车门与联动将完整还原为吉利出厂官方晓晓原声，并物理删除 custom 目录下的所有音频文件。',
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
  setTimeout(() => {
    loadVoiceThemes();
  }, 30);
});
</script>
