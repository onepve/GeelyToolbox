<template>
  <ModalWrapper
    :show="true"
    title="车门迎宾与关门播报详细配置"
    badge="车门细分"
    maxWidthClass="max-w-[880px]"
    zIndexClass="z-[9998]"
    @close="$emit('close')"
  >
    <!-- 模式单选切换 -->
        <div class="flex items-center space-x-3 p-4 bg-car-item rounded-2xl border border-car-border">
          <button 
            @click="setDoorMode(true)"
            :class="[
              'flex-1 h-[52px] rounded-xl font-black text-[15.5px] cursor-pointer transition-all border-2',
              store.vehicleAuto.voice_door_mode_universal === true
                ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            极简合并播报
          </button>
          <button 
            @click="setDoorMode(false)"
            :class="[
              'flex-1 h-[52px] rounded-xl font-black text-[15.5px] cursor-pointer transition-all border-2',
              store.vehicleAuto.voice_door_mode_universal !== true
                ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            详细分门播报
          </button>
        </div>

        <!-- 模式 1: 通用智能车门语音 (合并防抖) -->
        <div v-if="store.vehicleAuto.voice_door_mode_universal === true" class="flex flex-col space-y-3.5">
          <!-- 通用开门提醒 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <div class="flex items-center space-x-2">
                <span class="text-[21.5px] font-black text-car-text">通用开门提醒</span>
                <span class="px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-car-accent text-[14px] font-bold">场景自适应</span>
              </div>
              <span class="text-[13px] text-car-sub font-bold">登车开门提醒启程，停车下车提醒带好随身物品，行车意外开门紧急报警</span>
            </div>
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('door_open')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('door_open', '通用开门提醒', 'door_open.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_door_universal_open')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_door_universal_open !== false ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_door_universal_open !== false ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>

          <!-- 通用关门提醒 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <div class="flex items-center space-x-2">
                <span class="text-[21.5px] font-black text-car-text">通用关门提醒</span>
                <span class="px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-emerald-400 text-[14px] font-bold">安全闭合</span>
              </div>
              <span class="text-[13px] text-car-sub font-bold">车门闭合完毕干脆提示“车门已关好”，多门同时动作合并防抖</span>
            </div>
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('door_close')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('door_close', '通用关门提醒', 'door_close.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_door_universal_close')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_door_universal_close !== false ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_door_universal_close !== false ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 模式 2: 独立分门专属台词模式 (主副驾进出4态全量细分 + 后排2态) -->
        <div v-else class="grid grid-cols-2 gap-4">
          <!-- 主驾 FL -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3">
            <div class="flex items-center justify-between border-b border-car-border/50 pb-2">
              <span class="text-[18px] font-black text-car-text">主驾车门 (FL · 进出4态)</span>
            </div>

            <!-- 1. 登车迎宾 -->
            <div class="flex items-center justify-between">
              <span class="text-[16px] text-car-text font-black whitespace-nowrap">登车迎宾</span>
              <div class="flex items-center space-x-2">
                <button @click="openCustomVoice('door_fl_enter', '主驾登车迎宾', 'door_fl_enter.mp3')" class="h-[52px] px-3 rounded-lg bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[13.5px] cursor-pointer">声效设置</button>
                <button @click="testVoice('door_fl_enter')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[15px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_fl_enter')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[15px] cursor-pointer', store.vehicleAuto.voice_enable_door_fl_enter !== false ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_fl_enter !== false ? '开启' : '关闭' }}</button>
              </div>
            </div>

            <!-- 2. 准备启程 -->
            <div class="flex items-center justify-between">
              <span class="text-[16px] text-car-text font-black whitespace-nowrap">准备启程</span>
              <div class="flex items-center space-x-2">
                <button @click="openCustomVoice('door_fl_ready', '主驾准备启程', 'door_fl_ready.mp3')" class="h-[52px] px-3 rounded-lg bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[13.5px] cursor-pointer">声效设置</button>
                <button @click="testVoice('door_fl_ready')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[15px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_fl_ready')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[15px] cursor-pointer', store.vehicleAuto.voice_enable_door_fl_ready !== false ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_fl_ready !== false ? '开启' : '关闭' }}</button>
              </div>
            </div>

            <!-- 3. 下车安全 -->
            <div class="flex items-center justify-between">
              <span class="text-[16px] text-car-text font-black whitespace-nowrap">推门下车</span>
              <div class="flex items-center space-x-2">
                <button @click="openCustomVoice('door_fl_exit', '主驾推门下车', 'door_fl_exit.mp3')" class="h-[52px] px-3 rounded-lg bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[13.5px] cursor-pointer">声效设置</button>
                <button @click="testVoice('door_fl_exit')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[15px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_fl_exit')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[15px] cursor-pointer', store.vehicleAuto.voice_enable_door_fl_exit !== false ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_fl_exit !== false ? '开启' : '关闭' }}</button>
              </div>
            </div>

            <!-- 4. 离车锁车 -->
            <div class="flex items-center justify-between">
              <span class="text-[16px] text-car-text font-black whitespace-nowrap">离车锁车</span>
              <div class="flex items-center space-x-2">
                <button @click="openCustomVoice('door_fl_leave', '主驾离车锁车', 'door_fl_leave.mp3')" class="h-[52px] px-3 rounded-lg bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[13.5px] cursor-pointer">声效设置</button>
                <button @click="testVoice('door_fl_leave')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[15px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_fl_leave')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[15px] cursor-pointer', store.vehicleAuto.voice_enable_door_fl_leave !== false ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_fl_leave !== false ? '开启' : '关闭' }}</button>
              </div>
            </div>
          </div>

          <!-- 副驾 FR -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3">
            <div class="flex items-center justify-between border-b border-car-border/50 pb-2">
              <span class="text-[18px] font-black text-car-text">副驾车门 (FR · 进出4态)</span>
            </div>

            <!-- 1. 登车迎宾 -->
            <div class="flex items-center justify-between">
              <span class="text-[16px] text-car-text font-black whitespace-nowrap">登车迎宾</span>
              <div class="flex items-center space-x-2">
                <button @click="openCustomVoice('door_fr_enter', '副驾登车迎宾', 'door_fr_enter.mp3')" class="h-[52px] px-3 rounded-lg bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[13.5px] cursor-pointer">声效设置</button>
                <button @click="testVoice('door_fr_enter')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[15px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_fr_enter')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[15px] cursor-pointer', store.vehicleAuto.voice_enable_door_fr_enter !== false ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_fr_enter !== false ? '开启' : '关闭' }}</button>
              </div>
            </div>

            <!-- 2. 就座系带 -->
            <div class="flex items-center justify-between">
              <span class="text-[16px] text-car-text font-black whitespace-nowrap">就座系带</span>
              <div class="flex items-center space-x-2">
                <button @click="openCustomVoice('door_fr_ready', '副驾就座系带', 'door_fr_ready.mp3')" class="h-[52px] px-3 rounded-lg bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[13.5px] cursor-pointer">声效设置</button>
                <button @click="testVoice('door_fr_ready')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[15px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_fr_ready')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[15px] cursor-pointer', store.vehicleAuto.voice_enable_door_fr_ready !== false ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_fr_ready !== false ? '开启' : '关闭' }}</button>
              </div>
            </div>

            <!-- 3. 推门下车 -->
            <div class="flex items-center justify-between">
              <span class="text-[16px] text-car-text font-black whitespace-nowrap">推门下车</span>
              <div class="flex items-center space-x-2">
                <button @click="openCustomVoice('door_fr_exit', '副驾推门下车', 'door_fr_exit.mp3')" class="h-[52px] px-3 rounded-lg bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[13.5px] cursor-pointer">声效设置</button>
                <button @click="testVoice('door_fr_exit')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[15px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_fr_exit')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[15px] cursor-pointer', store.vehicleAuto.voice_enable_door_fr_exit !== false ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_fr_exit !== false ? '开启' : '关闭' }}</button>
              </div>
            </div>

            <!-- 4. 离车告别 -->
            <div class="flex items-center justify-between">
              <span class="text-[16px] text-car-text font-black whitespace-nowrap">离车告别</span>
              <div class="flex items-center space-x-2">
                <button @click="openCustomVoice('door_fr_leave', '副驾离车告别', 'door_fr_leave.mp3')" class="h-[52px] px-3 rounded-lg bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[13.5px] cursor-pointer">声效设置</button>
                <button @click="testVoice('door_fr_leave')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[15px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_fr_leave')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[15px] cursor-pointer', store.vehicleAuto.voice_enable_door_fr_leave !== false ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_fr_leave !== false ? '开启' : '关闭' }}</button>
              </div>
            </div>
          </div>

          <!-- 左后 RL -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3">
            <div class="flex items-center justify-between border-b border-car-border/50 pb-2">
              <span class="text-[18px] font-black text-car-text">左后车门 (RL)</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">开门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="openCustomVoice('door_rl', '左后开门播报')" class="h-[52px] px-3 rounded-lg bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[14px] cursor-pointer">声效设置</button>
                <button @click="testVoice('door_rl')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[16px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_rl')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[16px] cursor-pointer', store.vehicleAuto.voice_enable_door_rl ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_rl ? '开启' : '关闭' }}</button>
              </div>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">关门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="openCustomVoice('door_rl_close', '左后关门播报')" class="h-[52px] px-3 rounded-lg bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[14px] cursor-pointer">声效设置</button>
                <button @click="testVoice('door_rl_close')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[16px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_rl_close')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[16px] cursor-pointer', store.vehicleAuto.voice_enable_door_rl_close ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_rl_close ? '开启' : '关闭' }}</button>
              </div>
            </div>
          </div>

          <!-- 右后 RR -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3">
            <div class="flex items-center justify-between border-b border-car-border/50 pb-2">
              <span class="text-[18px] font-black text-car-text">右后车门 (RR)</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">开门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="openCustomVoice('door_rr', '右后开门播报')" class="h-[52px] px-3 rounded-lg bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[14px] cursor-pointer">声效设置</button>
                <button @click="testVoice('door_rr')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[16px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_rr')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[16px] cursor-pointer', store.vehicleAuto.voice_enable_door_rr ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_rr ? '开启' : '关闭' }}</button>
              </div>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">关门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="openCustomVoice('door_rr_close', '右后关门播报')" class="h-[52px] px-3 rounded-lg bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[14px] cursor-pointer">声效设置</button>
                <button @click="testVoice('door_rr_close')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[16px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_rr_close')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[16px] cursor-pointer', store.vehicleAuto.voice_enable_door_rr_close ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_rr_close ? '开启' : '关闭' }}</button>
              </div>
            </div>
          </div>
        </div>

    <template #footer>
      <button 
            @click="$emit('close')"
            class="h-[52px] px-8 rounded-xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[16px] cursor-pointer"
          >
            完成配置
          </button>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { computed } from 'vue';
import ModalWrapper from './modals/ModalWrapper.vue';
import { store } from '../store';
import { useConfigModal } from '../composables/useConfigModal';

const emit = defineEmits(['close']);

const { toggleSetting, testVoice, openCustomVoice, setSetting } = useConfigModal();

const currentPassengerRole = computed(() => {
  return store.vehicleAuto.passenger_voice_role || 'female';
});

const passengerPhrases = computed(() => {
  const role = currentPassengerRole.value;
  if (role === 'queen') {
    return {
      enter: '恭迎女王殿下',
      ready: '女王殿下请系好安全带',
      exit: '女王殿下请慢走，带好贵重物品',
      leave: '恭送女王殿下'
    };
  } else if (role === 'female' || role === 'original' || role === 'standard') {
    return {
      enter: '欢迎乘车 (知性原车女声)',
      ready: '请系好安全带',
      exit: '开门请注意后方来车',
      leave: '车门已关好'
    };
  } else {
    return {
      enter: '欢迎公主上车 (温润男声)',
      ready: '公主请系好安全带',
      exit: '公主请下车，小包包和手机别落下哦',
      leave: '公主再见，今天也要开心哦'
    };
  }
});

function switchPassengerRole(roleId, roleName) {
  setSetting('passenger_voice_role', roleId, '已切换副驾语音为: ' + roleName);
  testVoice('door_fr_enter');
}

/** 车门语音双模式切换：key 与 toast 固定，仅布尔值可变 */
function setDoorMode(isUniversal) {
  setSetting(
    'voice_door_mode_universal',
    isUniversal,
    isUniversal ? '已切换为: 通用智能车门语音 (推荐)' : '已切换为: 独立分门明细语音'
  );
}

</script>
