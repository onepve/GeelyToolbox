<!-- 车载蓝牙音频声道排查与调试中枢 (AudioChannelDebugModal) - 终极车规精致一屏免滚动版 -->
<template>
  <ModalWrapper
    :show="show"
    title="蓝牙音频排查与声道调试"
    badge="1~13号全车规声道"
    maxWidthClass="max-w-[1020px]"
    maxHeightClass="max-h-[96vh]"
    zIndexClass="z-[9998]"
    @close="$emit('close')"
  >
    <div class="flex flex-col space-y-2.5 select-none -mt-3">
      <!-- 1. 顶部多功能综合诊断与自动巡检条 -->
      <div class="bg-car-item border border-car-border rounded-xl px-3.5 py-2 flex items-center justify-between shadow-sm">
        <div class="flex items-center space-x-3 min-w-0">
          <button
            v-if="!isInspecting"
            @click="startInspection"
            class="h-[50px] px-4 rounded-xl bg-car-accent text-car-base hover:opacity-90 font-black text-[12.5px] cursor-pointer shadow-sm active:scale-95 transition-all shrink-0 whitespace-nowrap flex items-center space-x-1"
          >
            <span>▶ 启动1~13号全自动巡检</span>
          </button>
          <button
            v-else
            @click="stopInspection"
            class="h-[50px] px-3.5 rounded-lg bg-car-item border-2 border-red-500 text-red-400 font-black text-[12.5px] cursor-pointer shadow-sm active:scale-95 transition-all shrink-0 animate-pulse whitespace-nowrap"
          >
            ■ 停止巡检
          </button>

          <span class="text-[12px] text-car-sub truncate">
            <template v-if="isInspecting">
              <span class="text-car-accent font-black animate-pulse">正在轮询巡检: 通道 {{ currentPlayingChannel }} ({{ getChannelName(currentPlayingChannel) }})...</span>
            </template>
            <template v-else>
              车机各声道绑定不同硬件喇叭，点击卡片临时试听，播完自动复位蓝牙
            </template>
          </span>
        </div>

        <div class="shrink-0 flex items-center space-x-2 pl-3">
          <span class="text-car-sub text-[12px] whitespace-nowrap">微信默认通道:</span>
          <span class="px-2 py-0.5 rounded-md bg-car-card border border-car-accent text-car-accent font-black text-[12px] font-mono">
            通道 {{ preferredChannel.toString().padStart(2, '0') }}
          </span>
          <div v-if="currentPlayingChannel > 0" class="flex items-center space-x-1 text-[11.5px] text-emerald-400 font-mono font-bold ml-1">
            <span class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-ping"></span>
            <span>发声中:{{ currentPlayingChannel }}</span>
          </div>
        </div>
      </div>

      <!-- 2. 急救发声与底层切源一体化工具条 -->
      <div class="bg-car-item border border-car-border rounded-xl px-3.5 py-1.5 flex items-center justify-between shadow-sm text-[12px]">
        <div class="flex items-center space-x-2 min-w-0">
          <span class="font-black text-car-text shrink-0 whitespace-nowrap">急救排查:</span>
          <button
            @click="testVoiceDefault"
            class="h-[30px] px-3 rounded-lg border border-car-border bg-car-card hover:border-car-border-light text-car-text font-bold text-[11.5px] cursor-pointer shadow-xs active:scale-95 transition-all whitespace-nowrap"
          >
            快速发声测试
          </button>
          <button
            @click="forceActivateBt"
            class="h-[30px] px-3 rounded-lg border border-car-accent bg-car-card text-car-accent hover:bg-emerald-950/30 font-black text-[11.5px] cursor-pointer shadow-xs active:scale-95 transition-all whitespace-nowrap"
          >
            强制选通蓝牙通道
          </button>
        </div>

        <div class="flex items-center space-x-1.5 shrink-0 pl-3 border-l border-car-border/60">
          <span class="text-car-sub font-bold whitespace-nowrap">底层切源:</span>
          <button
            @click="switchSource(2)"
            class="h-[28px] px-2.5 rounded-md border border-car-border bg-car-card hover:border-car-accent text-car-text font-bold text-[11px] cursor-pointer shadow-xs active:scale-95 transition-all whitespace-nowrap"
          >
            2号蓝牙音源
          </button>
          <button
            @click="switchSource(6)"
            class="h-[28px] px-2.5 rounded-md border border-car-border bg-car-card hover:border-car-accent text-car-text font-bold text-[11px] cursor-pointer shadow-xs active:scale-95 transition-all whitespace-nowrap"
          >
            6号在线EAS
          </button>
          <button
            @click="switchSource(1)"
            class="h-[28px] px-2.5 rounded-md border border-car-border bg-car-card hover:border-car-accent text-car-text font-bold text-[11px] cursor-pointer shadow-xs active:scale-95 transition-all whitespace-nowrap"
          >
            1号收音FM
          </button>
        </div>
      </div>

      <!-- 3. 1~12 车规声道 4列等宽对称网格 -->
      <div class="grid grid-cols-4 gap-2">
        <div
          v-for="ch in channelList.slice(0, 12)"
          :key="ch.id"
          @click="selectChannel(ch.id)"
          :class="[
            'p-2.5 rounded-xl border transition-all cursor-pointer relative flex flex-col justify-between shadow-xs',
            selectedChannel === ch.id
              ? 'bg-car-card border-car-accent ring-2 ring-car-accent/30 shadow-md'
              : 'bg-car-item border-car-border hover:border-car-border-light hover:bg-car-hover'
          ]"
        >
          <!-- 默认徽章 (右上角绝对定位) -->
          <div
            v-if="preferredChannel === ch.id"
            class="absolute top-2 right-2 px-1.5 py-0.2 rounded bg-emerald-950/50 border border-car-accent text-car-accent text-[10px] font-black tracking-wider leading-none"
          >
            默认
          </div>

          <!-- 卡片头部：编号与名称 -->
          <div class="flex items-center space-x-1.5 pr-8">
            <span class="text-[12px] font-mono font-black text-car-sub leading-none">
              {{ ch.id.toString().padStart(2, '0') }}
            </span>
            <span class="text-[13px] font-black text-car-text leading-tight truncate">
              {{ ch.name }}
            </span>
          </div>

          <!-- 卡片说明 -->
          <div class="text-[11px] text-car-sub my-1 leading-snug line-clamp-1">
            {{ ch.desc }}
          </div>

          <!-- 卡片底栏：喇叭位置与试听按钮 -->
          <div class="flex items-center justify-between pt-4 border-t border-car-border/40">
            <span class="text-[10.5px] px-1.5 py-0.5 rounded bg-car-base/60 text-car-sub font-mono truncate max-w-[90px]">
              {{ ch.pos }}
            </span>
            <button
              @click.stop="playChannel(ch.id)"
              :class="[
                'h-[26px] px-2.5 rounded-md font-black text-[11px] cursor-pointer shadow-xs active:scale-95 transition-all flex items-center space-x-1 shrink-0',
                currentPlayingChannel === ch.id
                  ? 'bg-emerald-500 text-white animate-pulse'
                  : 'bg-car-base border border-car-border hover:border-car-accent text-car-text'
              ]"
            >
              <span>{{ currentPlayingChannel === ch.id ? '🔊 发声' : '试听' }}</span>
            </button>
          </div>
        </div>

        <!-- 4. 13号车身提示声道：全宽宽幅横幅（横跨 4 列），彻底消除孤立卡片与空缺黑洞 -->
        <div
          v-if="channelList[12]"
          @click="selectChannel(channelList[12].id)"
          :class="[
            'col-span-4 px-3.5 py-2 rounded-xl border transition-all cursor-pointer flex items-center justify-between shadow-xs',
            selectedChannel === channelList[12].id
              ? 'bg-car-card border-car-accent ring-2 ring-car-accent/30 shadow-md'
              : 'bg-car-item border-car-border hover:border-car-border-light hover:bg-car-hover'
          ]"
        >
          <div class="flex items-center space-x-3 min-w-0">
            <div class="flex items-center space-x-1.5 shrink-0">
              <span class="text-[12px] font-mono font-black text-car-sub">13</span>
              <span class="text-[13px] font-black text-car-text">车身提示</span>
              <span class="text-[10.5px] px-2 py-0.5 rounded bg-emerald-950/40 border border-car-accent text-car-accent font-black">
                安全混音总线
              </span>
            </div>
            <span class="text-[11.5px] text-car-sub truncate">
              雷达倒车与安全音效独立硬件混音总线 · 物理直通，不受任何应用媒体音量或静音策略拦截
            </span>
          </div>

          <div class="flex items-center space-x-2 shrink-0 pl-3">
            <div
              v-if="preferredChannel === channelList[12].id"
              class="px-2 py-0.5 rounded bg-emerald-950/50 border border-car-accent text-car-accent text-[11px] font-black"
            >
              当前默认
            </div>
            <button
              @click.stop="playChannel(channelList[12].id)"
              :class="[
                'h-[28px] px-3.5 rounded-lg font-black text-[11.5px] cursor-pointer shadow-xs active:scale-95 transition-all flex items-center space-x-1 shrink-0',
                currentPlayingChannel === channelList[12].id
                  ? 'bg-emerald-500 text-white animate-pulse'
                  : 'bg-car-base border border-car-border hover:border-car-accent text-car-text'
              ]"
            >
              <span>{{ currentPlayingChannel === channelList[12].id ? '🔊 发声中' : '测试发声' }}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 5. 底部选定通道操作与保存栏 -->
      <div class="bg-car-item border border-car-border rounded-xl px-4 py-2 flex items-center justify-between shadow-sm">
        <div class="flex items-center space-x-2 min-w-0">
          <span class="text-[13px] font-bold text-car-sub whitespace-nowrap">当前选定:</span>
          <span class="text-[13.5px] font-black text-car-accent">
            通道 {{ selectedChannel.toString().padStart(2, '0') }} · {{ getChannelName(selectedChannel) }}
          </span>
          <span class="text-[11.5px] text-car-sub/80 truncate">
            (试听仅临时发声，点击右侧确认才会保存)
          </span>
        </div>

        <div class="flex items-center space-x-2.5 shrink-0 pl-3">
          <button
            @click="restoreDefault"
            class="h-[50px] px-4 rounded-xl border border-car-border bg-car-card hover:border-car-border-light text-car-sub hover:text-car-text font-bold text-[12px] cursor-pointer active:scale-95 transition-all whitespace-nowrap shadow-xs"
          >
            恢复推荐默认(通道02)
          </button>
          <button
            @click="applyCurrentSelection"
            class="h-[50px] px-5 rounded-xl bg-car-accent text-car-base hover:opacity-90 font-black text-[12.5px] cursor-pointer active:scale-95 transition-all whitespace-nowrap shadow-sm"
          >
            确认设为微信默认通道
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

// 1~13 车规全量声道常量定义（对齐高德车机版 AudioAttributes.USAGE 体系）
const channelList = [
  { id: 1, name: '媒体音乐', desc: '全车音乐主放音声道', pos: '全车喇叭' },
  { id: 2, name: '语音通话', desc: '原厂车载免提电话通道', pos: '主驾头枕/独立' },
  { id: 3, name: '通话信令', desc: '挂断/拨号双音多频音', pos: '前排喇叭' },
  { id: 4, name: '闹钟警报', desc: '高优先级强提醒报警声', pos: '中控主喇叭' },
  { id: 5, name: '系统通知', desc: '微信/QQ标准通知提示音', pos: '前排混音' },
  { id: 6, name: '来电铃声', desc: '电话呼入时振铃声道', pos: '全车振铃' },
  { id: 7, name: '通信请求', desc: 'VOIP网络电话呼叫信令', pos: '主驾前方' },
  { id: 8, name: '微信即时', desc: '专用即时通讯交互音频', pos: '驾驶舱混音' },
  { id: 9, name: '延时通知', desc: '系统低优先级状态告知', pos: '全车混音' },
  { id: 10, name: '日程事件', desc: '日历与车机定时提醒声', pos: '中控喇叭' },
  { id: 11, name: '无障碍', desc: '语音辅助盲操旁白音', pos: '主驾独立' },
  { id: 12, name: '车载导航', desc: '高德/原厂导航播报通道', pos: '主驾左前' },
  { id: 13, name: '车身提示', desc: '雷达倒车与安全音效总线', pos: '安全总线' }
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
  playChannel(preferredChannel.value);
}

function forceActivateBt() {
  bridge.call('forceActivateBluetoothChannel');
  showToast('已下发 EAS 6号蓝牙物理声道强制选通指令');
}

function switchSource(src) {
  bridge.call('switchEcarxSourceType', src);
  showToast(`已向底层下发切换到 ${src} 号音源`);
}

function applyCurrentSelection() {
  bridge.call('setPreferredAudioUsageChannel', selectedChannel.value);
  preferredChannel.value = selectedChannel.value;
  showToast(`已将微信语音默认通道保存为：通道 ${selectedChannel.value} (${getChannelName(selectedChannel.value)})`);
}

function restoreDefault() {
  selectedChannel.value = 2;
  bridge.call('setPreferredAudioUsageChannel', 2);
  preferredChannel.value = 2;
  showToast('已恢复推荐默认：通道 02 (语音通话)');
}

function startInspection() {
  if (isInspecting.value) return;
  isInspecting.value = true;
  let idx = 0;
  
  function next() {
    if (!isInspecting.value) return;
    if (idx >= channelList.length) {
      stopInspection();
      showToast('🎉 1~13号全车规声道自动巡检完成！');
      return;
    }
    const ch = channelList[idx];
    selectedChannel.value = ch.id;
    currentPlayingChannel.value = ch.id;
    bridge.call('playAudioUsageChannel', ch.id);
    
    idx++;
    inspectTimer = setTimeout(next, 900);
  }
  
  next();
}

function stopInspection() {
  isInspecting.value = false;
  if (inspectTimer) {
    clearTimeout(inspectTimer);
    inspectTimer = null;
  }
  currentPlayingChannel.value = 0;
}

onMounted(() => {
  try {
    const saved = bridge.call('getPreferredAudioUsageChannel');
    if (saved && !isNaN(parseInt(saved))) {
      preferredChannel.value = parseInt(saved);
      selectedChannel.value = parseInt(saved);
    }
  } catch (e) {}
});

onUnmounted(() => {
  stopInspection();
});
</script>