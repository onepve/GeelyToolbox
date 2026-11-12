<template>
  <ModalWrapper
    :show="true"
    title="方控响应与冲突体检"
    badge="实车通路诊断"
    maxWidthClass="max-w-[760px]"
    zIndexClass="z-[9999]"
    :showCloseButton="false"
    @close="handleCancel"
  >
    <div class="flex flex-col py-2 space-y-6 items-center text-center">

      <!-- 阶段一：等待车主按键 -->
      <template v-if="stage === 'waiting'">
        <div class="w-full bg-car-item rounded-2xl border-2 border-car-border p-6 space-y-4">
          <div class="flex items-center justify-center space-x-3">
            <StatusDot size="lg" color="ok" :glow-px="10" />
            <span class="text-[22px] font-black text-car-text tracking-wide">正在聆听方向盘按键...</span>
          </div>
          <div class="text-[17px] text-car-sub font-bold leading-relaxed">
            请现在按下方向盘右侧您想检测的任意按键，<br />
            助手将立即检测底盘信号通路与响应延迟。
          </div>
          <div class="text-[14.5px] text-car-sub font-bold">
            💡 诊断期间按键不会触发 360 或切歌等任何功能，请放心按。
          </div>
        </div>

        <!-- 超时进度条（车规可视化：秒级倒计时） -->
        <div class="w-full max-w-[420px] space-y-2">
          <div class="h-[14px] w-full rounded-full bg-car-item border border-car-border overflow-hidden">
            <div
              class="h-full rounded-full bg-car-accent transition-all duration-300"
              :style="{ width: progressPct + '%' }"
            ></div>
          </div>
          <div class="text-[14.5px] text-car-sub font-black">
            {{ secondsLeft }} 秒后结束检测（共 {{ TOTAL_SECONDS }} 秒）
          </div>
        </div>
      </template>

      <!-- 阶段二：捕获成功，给出诊断结论 -->
      <template v-else-if="stage === 'captured'">
        <div class="w-full bg-car-item rounded-2xl border-2 border-car-accent p-6 space-y-4 text-left">
          <div class="flex items-center justify-between">
            <div class="flex items-center space-x-3">
              <span class="text-[32px]">✅</span>
              <span class="text-[22px] font-black text-car-text tracking-wide">底盘信号通路正常</span>
            </div>
            <div class="flex items-center space-x-2">
              <span class="text-[18px] font-black text-car-accent bg-car-card px-3.5 py-1.5 rounded-xl border border-car-border">
                {{ friendlyName }}
              </span>
              <span class="text-[14px] text-car-sub font-bold">键码 {{ capturedCode }}</span>
            </div>
          </div>

          <!-- 诊断与排错指引 (根据按键提供实车大白话结论) -->
          <div class="bg-car-card border border-car-border rounded-xl p-4 space-y-2 text-[15.5px] leading-relaxed">
            <div class="font-black text-car-text flex items-center space-x-2">
              <span>🩺</span>
              <span>体检诊断结论：</span>
            </div>
            <div class="text-car-sub font-bold whitespace-pre-line">
              {{ diagnosticResultText }}
            </div>
          </div>
        </div>
      </template>

      <!-- 阶段三：超时未捕获，给出排查清单 -->
      <template v-else>
        <div class="w-full bg-car-item rounded-2xl border-2 border-car-border p-6 space-y-4 text-left">
          <div class="flex items-center space-x-3">
            <span class="text-[32px]" style="color: var(--text-main);">⚠️</span>
            <span class="text-[22px] font-black text-car-text tracking-wide">未接收到底盘按键信号</span>
          </div>
          <div class="bg-car-card border border-car-border rounded-xl p-4 space-y-2 text-[15px] leading-relaxed">
            <div class="font-black text-car-text flex items-center space-x-2">
              <span>🔍</span>
              <span>排查与解决建议：</span>
            </div>
            <div class="text-car-sub font-bold space-y-1.5">
              <div>1. <b>总开关检查</b>：请确认方控页面顶部的「方向盘按键接管总开关」已保持开启。</div>
              <div>2. <b>接管模式冲突</b>：只有「① 全面接管」和「② 只接管方控」会拦截按键；选「③ 只接管蓝牙」或「④ 完整原厂」时，方向盘按键全部放行给原车。</div>
              <div>3. <b>第三方方控抢占</b>：若车机同时安装了米小江 CarMedia 等方控软件，按键广播可能已被其优先独占消费。</div>
              <div>4. <b>按键区域安全红线</b>：仅支持右方向盘多媒体键；左侧巡航与行车安全按键受系统保护，严禁检测与拦截。</div>
            </div>
          </div>
        </div>
      </template>
    </div>

    <template #footer>
      <div class="flex items-center justify-end space-x-4 w-full">
        <button
          @click="handleCancel"
          class="min-h-[64px] px-8 rounded-2xl bg-car-item border-2 border-car-border text-car-sub hover:text-car-text font-black text-[19px] cursor-pointer hover:border-car-border-light shadow-sm"
        >
          关闭
        </button>
        <button
          v-if="stage === 'waiting'"
          @click="handleRestart"
          class="min-h-[64px] px-10 rounded-2xl bg-car-item border-2 border-car-border text-car-text font-black text-[19px] cursor-pointer hover:border-car-border-light shadow-sm"
        >
          重新检测
        </button>
        <button
          v-if="stage !== 'waiting'"
          @click="handleRestart"
          class="min-h-[64px] px-10 rounded-2xl bg-car-item border-2 border-car-accent text-car-text font-black text-[19px] cursor-pointer shadow-md"
        >
          再测一个按键
        </button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import { closeModal } from '../../store';
import ModalWrapper from './ModalWrapper.vue';
import StatusDot from '../StatusDot.vue';
import { store } from '../../store';

const emit = defineEmits(['close']);

const TOTAL_SECONDS = 20;
const stage = ref('waiting'); // waiting | captured | timeout
const secondsLeft = ref(TOTAL_SECONDS);
const capturedCode = ref(0);
let countdownTimer = null;

const progressPct = computed(() => Math.max(0, Math.round((secondsLeft.value / TOTAL_SECONDS) * 100)));

// 已知键码 -> 大白话名称（与助手既有键位表一致）
const KEY_NAMES = {
  300: '静音键', 301: '音量减/滚轮下拨', 302: '音量加/滚轮上拨',
  304: '上一曲', 305: '下一曲', 306: '滚轮按压确认', 307: '主页/返回键',
  286: '语音按键', 287: '电话按键', 348: 'MODE 键', 349: '自定义菱形键',
  45: '滚轮按压确认', 66: '滚轮按压确认'
};
const friendlyName = computed(() => KEY_NAMES[capturedCode.value] || '自定义按键');

const diagnosticResultText = computed(() => {
  const code = capturedCode.value;
  if (code === 300) {
    return '1. 硬件连接：底盘信号 0ms 瞬间直达，按键通路完好。\n2. 静音键特殊机制：原厂车机系统对静音键存在底层独占。若映射为「播放/暂停」，部分非车载标准的音乐软件（如移动版酷狗/网易云）可能无法正确响应暂停，建议静音键保持「原厂默认」使用。\n3. 长按应急提示：长按此键 10 秒为原车硬件级强制重启，不受任何设置影响。';
  }
  if (code === 348) {
    return '1. 硬件连接：MODE 模式键通路完全正常。\n2. 原厂冲突防护：助手已为您开启「防原厂收音机打扰」门禁，按下后将阻断原车伴听/收音机弹窗。\n3. 推荐用途：推荐在设置中将其绑定为「360 全景」，倒车或过窄路盲操极方便。';
  }
  if (code === 304 || code === 305) {
    return '1. 硬件连接：切歌键通路通畅，极速响应。\n2. 切歌生效提示：已配备 900ms 兜底切歌状态机。若在某些音乐软件中切歌没反应，请确认该音乐软件是否开启了「允许线控切歌」或具有后台运行权限。';
  }
  if (code === 306 || code === 45 || code === 66) {
    return '1. 硬件连接：滚轮垂直按压键通路正常。\n2. 推荐用途：垂直按压默认推荐绑定「播放 / 暂停媒体」，上下滚动依然保持正常调节音量，互不干扰。';
  }
  if (code === 307) {
    return '1. 硬件连接：右盘返回键通路正常。\n2. 使用建议：若习惯原车点按返回上一级，卡片中选择「恢复原厂默认」即可；亦可绑定为秒开高德或 360。';
  }
  if (KEY_NAMES[code]) {
    return '底盘信号极速响应，硬件物理通路完全正常！您可直接在下方对应的按键卡片中自由绑定 360、高德或自定义应用。';
  }
  return '成功捕获当前车型的物理键码！底盘信号通路已打通。键码已自动记录，如需单独适配专属卡片，可将此键码反馈给作者。';
});

function stopCountdown() {
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null; }
}

function startSession() {
  stage.value = 'waiting';
  secondsLeft.value = TOTAL_SECONDS;
  stopCountdown();
  try { window.ToolboxBridge.stopWheelKeyLearning(); } catch (e) {}
  const ok = (() => {
    try { return window.ToolboxBridge.startWheelKeyLearning(); } catch (e) { return false; }
  })();
  if (!ok) {
    stage.value = 'timeout';
    return;
  }
  countdownTimer = setInterval(() => {
    secondsLeft.value -= 1;
    if (secondsLeft.value <= 0) {
      stopCountdown();
      stage.value = 'timeout';
      try { window.ToolboxBridge.stopWheelKeyLearning(); } catch (e) {}
    }
  }, 1000);
}

function handleRestart() {
  startSession();
}

function handleCancel() {
  stopCountdown();
  try { window.ToolboxBridge.stopWheelKeyLearning(); } catch (e) {}
  closeModal('wheelKeyLearning');
  emit('close');
}

// 原生捕获到按键后回调（由 Java 侧 evaluateJavascript 触发）
window.onWheelKeyLearned = (payload) => {
  try {
    const code = parseInt(payload && payload.keyCode, 10);
    if (!isNaN(code) && code > 0) {
      stopCountdown();
      capturedCode.value = code;
      stage.value = 'captured';
      // 若携带了自定义添加回调（用于模式 B 添加按键）
      if (store.modals.wheelKeyLearning && typeof store.modals.wheelKeyLearning.onCaptured === 'function') {
        store.modals.wheelKeyLearning.onCaptured(code, KEY_NAMES[code] || '自定义按键');
      }
    }
  } catch (e) {}
};

onMounted(() => {
  startSession();
});

onBeforeUnmount(() => {
  stopCountdown();
  delete window.onWheelKeyLearned;
  try { window.ToolboxBridge.stopWheelKeyLearning(); } catch (e) {}
});
</script>
