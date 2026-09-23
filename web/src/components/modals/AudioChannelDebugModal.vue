<!-- 车载蓝牙音频声道排查与调试中枢 (AudioChannelDebugModal) -->
<template>
  <ModalWrapper
    :show="show"
    title="蓝牙音频排查与声道调试"
    badge="1~13号全声道"
    maxWidthClass="max-w-[960px]"
    zIndexClass="z-[9998]"
    @close="$emit('close')"
  >
    <div class="flex flex-col space-y-4 max-h-[75vh] overflow-y-auto pr-1 select-none">
      <!-- 顶部说明 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-4 flex items-center justify-between text-car-sub text-[13.5px]">
        <div class="flex items-center space-x-2">
          <span class="text-car-accent font-black">诊断指南:</span>
          <span>车机不同声道绑定不同硬件喇叭（如主驾头枕、前排、全车）。点击任意通道即可单独试听，听哪只喇叭出声。</span>
        </div>
        <div class="shrink-0 flex items-center space-x-2">
          <span class="text-car-sub text-[13px]">当前微信默认:</span>
          <span class="px-2.5 py-1 rounded-lg bg-car-card border border-car-accent text-car-accent font-black text-[13px]">
            通道 {{ preferredChannel.toString().padStart(2, '0') }}
          </span>
        </div>
      </div>

      <!-- 快捷工具箱：原外层按钮收纳于此 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-4 flex items-center justify-between shadow-sm">
        <div class="flex flex-col">
          <span class="text-[16px] font-black text-car-text">常用急救工具箱</span>
          <span class="text-[12.5px] text-car-sub">无声急救时先点快速发声，若未开喇叭可一键强制选通原厂蓝牙</span>
        </div>
        <div class="flex items-center space-x-3 shrink-0">
          <button
            @click="testVoiceDefault"
            class="h-[50px] px-5 rounded-xl border border-car-border bg-car-card hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm active:scale-95 transition-all"
          >
            快速测试发声
          </button>
          <button
            @click="forceActivateBt"
            class="h-[50px] px-5 rounded-xl border-2 border-car-accent bg-car-card text-car-accent hover:border-car-accent font-black text-[14px] cursor-pointer shadow-sm active:scale-95 transition-all"
          >
            一键强制激活蓝牙通道
          </button>
        </div>
      </div>

      <!-- 自动全巡检与状态条 -->
      <div class="bg-car-card border border-car-border rounded-2xl p-4 flex items-center justify-between shadow-sm">
        <div class="flex items-center space-x-3">
          <button
            v-if="!isInspecting"
            @click="startInspection"
            class="h-[50px] px-5 rounded-xl bg-car-accent text-car-base hover:opacity-90 font-black text-[14px] cursor-pointer shadow-md active:scale-95 transition-all"
          >
            启动 1~13 号全自动巡检
          </button>
          <button
            v-else
            @click="stopInspection"
            class="h-[50px] px-5 rounded-xl bg-car-item border-2 border-red-500 text-red-400 font-black text-[14px] cursor-pointer shadow-md active:scale-95 transition-all animate-pulse"
          >
            停止巡检
          </button>

          <span class="text-[13.5px] text-car-sub">
            <template v-if="isInspecting">
              <span class="text-car-accent font-black animate-pulse">正在轮询巡检: 通道 {{ currentPlayingChannel }}...</span>
            </template>
            <template v-else>
              自动从 1 到 13 号依次播放微信测试音，坐在车内听哪几个通道有声音
            </template>
          </span>
        </div>

        <!-- 底层切源快捷辅助 -->
        <div class="flex items-center space-x-2 text-[12.5px] text-car-sub">
          <span>底层切源:</span>
          <button
            @click="switchSource(2)"
            class="h-[50px] px-3.5 rounded-xl border border-car-border bg-car-item hover:border-car-accent text-car-text font-bold cursor-pointer"
          >
            2号蓝牙
          </button>
          <button
            @click="switchSource(6)"
            class="h-[50px] px-3.5 rounded-xl border border-car-border bg-car-item hover:border-car-accent text-car-text font-bold cursor-pointer"
          >
            6号EAS
          </button>
          <button
            @click="switchSource(1)"
            class="h-[50px] px-3.5 rounded-xl border border-car-border bg-car-item hover:border-car-accent text-car-text font-bold cursor-pointer"
          >
            1号FM
          </button>
        </div>
      </div>

      <!-- 1~13 车规全声道网格 -->
      <div class="grid grid-cols-4 gap-3">
        <div
          v-for="ch in channelList"
          :key="ch.id"
          @click="selectChannel(ch.id)"
          :class="[
            'p-3.5 rounded-2xl border flex flex-col justify-between space-y-2 cursor-pointer transition-all relative overflow-hidden',
            selectedChannel === ch.id ? 'border-car-accent bg-car-card shadow-md ring-1 ring-car-accent' : 'border-car-border bg-car-item hover:border-car-border-light',
            currentPlayingChannel === ch.id ? 'border-emerald-400 bg-car-card ring-2 ring-emerald-400' : ''
          ]"
        >
          <!-- 默认标记角标 -->
          <div
            v-if="preferredChannel === ch.id"
            class="absolute top-0 right-0 bg-car-accent text-car-base font-black text-[10px] px-2 py-0.5 rounded-bl-lg tracking-wider"
          >
            默认
          </div>

          <div class="flex items-center space-x-2">
            <span class="text-[17px] font-black text-car-accent font-mono">{{ ch.id.toString().padStart(2, '0') }}</span>
            <span class="text-[14.5px] font-black text-car-text truncate">{{ ch.name }}</span>
          </div>

          <div class="text-[11.5px] text-car-sub line-clamp-1">
            {{ ch.desc }}
          </div>

          <div class="flex items-center justify-between pt-1">
            <span class="text-[11px] text-car-sub/80">{{ ch.hardware }}</span>
            <button
              @click.stop="playChannel(ch.id)"
              :class="[
                'h-[50px] px-3.5 rounded-xl font-black text-[12px] cursor-pointer active:scale-95 transition-all shadow-sm',
                currentPlayingChannel === ch.id 
                  ? 'bg-emerald-500 text-white animate-pulse' 
                  : 'bg-car-card border border-car-border hover:border-car-accent text-car-text'
              ]"
            >
              {{ currentPlayingChannel === ch.id ? '发声中' : '试听' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 底部操作与持久化保存 -->
      <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between shadow-sm">
        <div class="flex items-center space-x-2 text-[14px]">
          <span class="text-car-sub">当前选定通道:</span>
          <span class="text-car-accent font-black text-[15px]">
            通道 {{ selectedChannel.toString().padStart(2, '0') }} · {{ getChannelName(selectedChannel) }}
          </span>
        </div>

        <div class="flex items-center space-x-3">
          <button
            @click="resetDefault"
            class="h-[50px] px-5 rounded-xl border border-car-border bg-car-card hover:border-car-border-light text-car-sub font-black text-[13.5px] cursor-pointer"
          >
            恢复推荐默认(通道02)
          </button>
          <button
            @click="savePreferred"
            class="h-[50px] px-6 rounded-xl bg-car-accent text-car-base hover:opacity-90 font-black text-[14px] cursor-pointer shadow-md active:scale-95 transition-all"
          >
            设为微信语音默认通道
          </button>
        </div>
      </div>
    </div>
  </ModalWrapper>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { bridge, showToast } from '../../store';

const props = defineProps({
  show: {
    type: Boolean,
    default: true
  }
});

const emit = defineEmits(['close']);

// 1~13 车规全量声道常量定义（高德同款 AudioAttributes.USAGE 体系）
const channelList = [
  { id: 1, name: '媒体音乐', desc: '全车音乐主放音声道，音质最佳', hardware: '全车喇叭' },
  { id: 2, name: '语音通话', desc: '原厂通话/免提通道，抗静音吞音', hardware: '主驾头枕/独立' },
  { id: 3, name: '通话信令', desc: '电话协议握手与信令提示通道', hardware: '前排喇叭' },
  { id: 4, name: '闹钟警报', desc: '最高优先级声道，功放强制全开', hardware: '全车高音' },
  { id: 5, name: '系统通知', desc: '原厂标准短提示音通道', hardware: '仪表/中控' },
  { id: 6, name: '来电铃声', desc: '原车电话呼入振铃专属通道', hardware: '头枕/前排' },
  { id: 7, name: '通信请求', desc: '车载通信与呼叫对讲请求通道', hardware: '前排喇叭' },
  { id: 8, name: '微信即时', desc: '专为即时通讯语音设计的低延迟通道', hardware: '头枕/中控' },
  { id: 9, name: '延时通知', desc: '后台离线通知与延时消息通道', hardware: '全车提示' },
  { id: 10, name: '日程事件', desc: '车机日历与备忘录事件提醒通道', hardware: '仪表提示' },
  { id: 11, name: '无障碍', desc: '原厂无障碍与视听辅助发音通道', hardware: '主驾喇叭' },
  { id: 12, name: '车载导航', desc: '车规导航核心总线，自动压低音乐', hardware: '高德核心总线' },
  { id: 13, name: '车身提示', desc: '雷达倒车与安全音效独立硬件混音', hardware: '安全混音总线' }
];

const selectedChannel = ref(2);
const preferredChannel = ref(2);
const currentPlayingChannel = ref(0);
const isInspecting = ref(false);
let inspectTimer = null;

function getChannelName(id) {
  const item = channelList.find(c => c.id === id);
  return item ? item.name : '未知通道';
}

function selectChannel(id) {
  selectedChannel.value = id;
}

function playChannel(id) {
  selectedChannel.value = id;
  currentPlayingChannel.value = id;
  bridge.call('playAudioUsageChannel', id);
  setTimeout(() => {
    if (currentPlayingChannel.value === id && !isInspecting.value) {
      currentPlayingChannel.value = 0;
    }
  }, 600);
}

function testVoiceDefault() {
  bridge.call('testBluetoothAudio');
}

function forceActivateBt() {
  bridge.call('forceActivateBluetoothChannel');
}

function switchSource(sourceType) {
  bridge.call('switchEcarxSourceType', sourceType);
}

function startInspection() {
  if (isInspecting.value) return;
  isInspecting.value = true;
  let idx = 0;
  
  function nextStep() {
    if (!isInspecting.value) return;
    if (idx >= channelList.length) {
      stopInspection();
      return;
    }
    const ch = channelList[idx];
    selectedChannel.value = ch.id;
    currentPlayingChannel.value = ch.id;
    bridge.call('playAudioUsageChannel', ch.id);
    idx++;
    inspectTimer = setTimeout(nextStep, 1300);
  }
  
  nextStep();
}

function stopInspection() {
  isInspecting.value = false;
  currentPlayingChannel.value = 0;
  if (inspectTimer) {
    clearTimeout(inspectTimer);
    inspectTimer = null;
  }
}

function savePreferred() {
  preferredChannel.value = selectedChannel.value;
  bridge.call('setPreferredAudioUsageChannel', selectedChannel.value);
}

function resetDefault() {
  selectedChannel.value = 2;
  preferredChannel.value = 2;
  bridge.call('setPreferredAudioUsageChannel', 2);
}

onMounted(() => {
  try {
    const pref = bridge.call('getPreferredAudioUsageChannel');
    if (pref) {
      preferredChannel.value = Number(pref) || 2;
      selectedChannel.value = preferredChannel.value;
    }
  } catch (e) {
    preferredChannel.value = 2;
  }
});

onUnmounted(() => {
  stopInspection();
});
</script>
