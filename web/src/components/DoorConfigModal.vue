<template>
    <!-- 弹窗 3: 四门迎宾与关门安全二级向导 (DoorConfigModal · 通用/分门全量完好) -->
    <div 
      class="fixed z-[9998] flex items-center justify-center p-6" style="top:0; left:0; width:100vw; height:100vh; background: rgba(10, 14, 23, 0.97);"
    >
      <div class="bg-car-card border-2 border-car-border rounded-3xl p-7 shadow-2xl max-w-[880px] w-full flex flex-col space-y-5 max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between border-b border-car-border pb-4">
          <div class="flex items-center space-x-3">
            <span class="w-3 h-3 rounded-full bg-car-accent"></span>
            <span class="text-[22px] font-black text-car-text">车门迎宾与关门播报详细配置</span>
          </div>
          <button 
            @click="$emit('close')"
            class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-sub hover:text-car-text font-black text-[20px] flex items-center justify-center cursor-pointer"
          >
            ✕
          </button>
        </div>

        <!-- 模式单选切换 -->
        <div class="flex items-center space-x-3 p-4 bg-car-item rounded-2xl border border-car-border">
          <button 
            @click="setDoorMode(true)"
            :class="[
              'flex-1 h-[52px] rounded-xl font-black text-[15.5px] cursor-pointer transition-all border-2',
              store.vehicleAuto.voice_door_mode_universal !== false
                ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            通用智能车门语音 (推荐 · 合并防抖)
          </button>
          <button 
            @click="setDoorMode(false)"
            :class="[
              'flex-1 h-[52px] rounded-xl font-black text-[15.5px] cursor-pointer transition-all border-2',
              store.vehicleAuto.voice_door_mode_universal === false
                ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            独立分门专属台词模式
          </button>
        </div>

        <!-- 模式 1: 通用智能车门语音 (推荐 · 合并防抖) -->
        <div v-if="store.vehicleAuto.voice_door_mode_universal !== false" class="flex flex-col space-y-3.5">
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

        <!-- 模式 2: 独立分门专属台词模式 (4 门开闭完整 8 组独立控制) -->
        <div v-else class="grid grid-cols-2 gap-4">
          <!-- 主驾 FL -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3">
            <div class="flex items-center justify-between border-b border-car-border/50 pb-2">
              <span class="text-[18px] font-black text-car-text">主驾车门 (FL)</span>
              <button 
                @click="openCustomVoice('door_fl', '主驾车门')"
                class="h-[50px] px-3.5 rounded-xl bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[13.5px] cursor-pointer"
              >
                声效设置
              </button>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">开门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="testVoice('door_fl')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[16px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_fl')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[16px] cursor-pointer', store.vehicleAuto.voice_enable_door_fl ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_fl ? '开启' : '关闭' }}</button>
              </div>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">关门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="testVoice('door_fl_close')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[16px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_fl_close')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[16px] cursor-pointer', store.vehicleAuto.voice_enable_door_fl_close ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_fl_close ? '开启' : '关闭' }}</button>
              </div>
            </div>
          </div>

          <!-- 副驾 FR -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3">
            <div class="flex items-center justify-between border-b border-car-border/50 pb-2">
              <span class="text-[18px] font-black text-car-text">副驾车门 (FR)</span>
              <button 
                @click="openCustomVoice('door_fr', '副驾车门')"
                class="h-[50px] px-3.5 rounded-xl bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[13.5px] cursor-pointer"
              >
                声效设置
              </button>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">开门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="testVoice('door_fr')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[16px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_fr')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[16px] cursor-pointer', store.vehicleAuto.voice_enable_door_fr ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_fr ? '开启' : '关闭' }}</button>
              </div>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">关门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="testVoice('door_fr_close')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[16px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_fr_close')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[16px] cursor-pointer', store.vehicleAuto.voice_enable_door_fr_close ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_fr_close ? '开启' : '关闭' }}</button>
              </div>
            </div>
          </div>

          <!-- 左后 RL -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3">
            <div class="flex items-center justify-between border-b border-car-border/50 pb-2">
              <span class="text-[18px] font-black text-car-text">左后车门 (RL)</span>
              <button 
                @click="openCustomVoice('door_rl', '左后车门')"
                class="h-[50px] px-3.5 rounded-xl bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[13.5px] cursor-pointer"
              >
                声效设置
              </button>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">开门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="testVoice('door_rl')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[16px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_rl')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[16px] cursor-pointer', store.vehicleAuto.voice_enable_door_rl ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_rl ? '开启' : '关闭' }}</button>
              </div>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">关门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="testVoice('door_rl_close')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[16px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_rl_close')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[16px] cursor-pointer', store.vehicleAuto.voice_enable_door_rl_close ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_rl_close ? '开启' : '关闭' }}</button>
              </div>
            </div>
          </div>

          <!-- 右后 RR -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3">
            <div class="flex items-center justify-between border-b border-car-border/50 pb-2">
              <span class="text-[18px] font-black text-car-text">右后车门 (RR)</span>
              <button 
                @click="openCustomVoice('door_rr', '右后车门')"
                class="h-[50px] px-3.5 rounded-xl bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[13.5px] cursor-pointer"
              >
                声效设置
              </button>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">开门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="testVoice('door_rr')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[16px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_rr')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[16px] cursor-pointer', store.vehicleAuto.voice_enable_door_rr ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_rr ? '开启' : '关闭' }}</button>
              </div>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">关门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="testVoice('door_rr_close')" class="h-[52px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[16px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_rr_close')" :class="['h-[52px] px-3.5 rounded-lg border-2 font-black text-[16px] cursor-pointer', store.vehicleAuto.voice_enable_door_rr_close ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_rr_close ? '开启' : '关闭' }}</button>
              </div>
            </div>
          </div>
        </div>

        <div class="flex justify-end pt-3 border-t border-car-border">
          <button 
            @click="$emit('close')"
            class="h-[52px] px-8 rounded-xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[16px] cursor-pointer"
          >
            完成配置
          </button>
        </div>
      </div>
    </div>
</template>

<script setup>
import { store } from '../store';
import { useConfigModal } from '../composables/useConfigModal';

const emit = defineEmits(['close']);

const { toggleSetting, testVoice, openCustomVoice, setSetting } = useConfigModal();

/** 车门语音双模式切换：key 与 toast 固定，仅布尔值可变 */
function setDoorMode(isUniversal) {
  setSetting(
    'voice_door_mode_universal',
    isUniversal,
    isUniversal ? '已切换为: 通用智能车门语音 (推荐)' : '已切换为: 独立分门明细语音'
  );
}

</script>
