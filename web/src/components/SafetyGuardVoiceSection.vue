<template>
  <div class="flex flex-col space-y-6">
    <!-- 专区标题卡 (106px 车规标准 · 左侧标题说明 + 右侧汇总徽标) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-5 min-h-[106px] shadow-xl flex items-center justify-between transition-all">
      <div class="w-[58%] max-w-[58%] flex flex-col space-y-1.5 shrink-0">
        <div class="flex items-center space-x-3">
          <span :class="['w-3.5 h-3.5 rounded-full shadow-md shrink-0', activeGuardCount > 0 ? 'bg-emerald-500 shadow-[0_0_10px_#10B981]' : 'bg-slate-400']"></span>
          <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">车况安全守护语音 (测试)</span>
          <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-item border-car-border text-car-text inline-flex items-center shrink-0 shadow-sm">
            <span :class="['w-2.5 h-2.5 rounded-full mr-2', activeGuardCount > 0 ? 'bg-emerald-500 shadow-[0_0_6px_#10B981]' : 'bg-slate-400']"></span>
            {{ activeGuardCount }} / 2 项守护运行中
          </span>
        </div>
        <div class="text-[14.5px] text-car-sub font-bold leading-normal">
          两项底盘安全信号守护语音均为测试功能，逐项独立开关。语音仅作辅助提醒，不能替代仪表与警示灯；未知信号不报警。
        </div>
      </div>
      <div class="shrink-0 w-[230px]">
        <button
          @click="showArbiterHelp"
          class="w-full h-[74px] px-4 py-2 rounded-2xl border-2 cursor-pointer transition-all shadow-md flex flex-col items-center justify-center text-center bg-car-item border-car-accent"
        >
          <span class="text-[17.5px] font-black text-car-text tracking-wide whitespace-nowrap">语音协同与仲裁说明</span>
          <span class="text-[12.5px] font-bold mt-1 text-car-accent whitespace-nowrap">通话静默规则 · 点击查看</span>
        </button>
      </div>
    </div>

    <!-- 两项守护双列等高网格 -->
    <div class="grid grid-cols-2 gap-5">
      <!-- 守护项 1: 方向盘未回正 -->
      <div class="rounded-3xl border-2 border-car-border hover:border-car-border-light bg-car-card p-6 shadow-xl h-full min-h-[260px] flex flex-col justify-between transition-all duration-200">
        <div class="flex items-center justify-between shrink-0 mb-2">
          <div class="flex items-center space-x-3">
            <span class="text-[20px] font-black text-car-text tracking-wide">方向盘未回正提醒 (测试)</span>
            <button
              @click.stop="showSteerHelp"
              class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-accent hover:border-car-accent font-black text-[18px] flex items-center justify-center cursor-pointer shadow-sm transition-transform active:scale-95 shrink-0"
            >
              ?
            </button>
          </div>
          <span :class="['px-3 py-1 rounded-full text-[13px] font-black border shrink-0', store.vehicleAuto.voice_enable_steer_angle_guard ? 'bg-emerald-500/10 border-emerald-500/40 text-car-text' : 'bg-car-item border-car-border text-car-sub']">
            {{ store.vehicleAuto.voice_enable_steer_angle_guard ? '● 已开启' : '○ 已关闭' }}
          </span>
        </div>

        <div class="flex-1 min-w-0 flex flex-col justify-center space-y-1.5 py-3 text-[15px] leading-relaxed">
          <div class="text-car-sub font-bold">挂 P 挡解开安全带或推门下车时，方向盘偏离中心较大</div>
          <div class="text-car-text font-bold">温婉提醒回正方向盘，坡道停车请按规范转向车轮</div>
        </div>

        <div class="grid grid-cols-2 gap-3 pt-4 border-t border-car-border/60 mt-auto shrink-0">
          <button
            @click="toggleGuard('voice_enable_steer_angle_guard')"
            :class="[
              'h-[52px] px-4 rounded-2xl font-black text-[16px] cursor-pointer transition-all border-2 flex items-center justify-center space-x-2 shadow-sm',
              store.vehicleAuto.voice_enable_steer_angle_guard
                ? 'bg-car-item border-car-accent text-car-text shadow-md'
                : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            <span :class="['w-2.5 h-2.5 rounded-full', store.vehicleAuto.voice_enable_steer_angle_guard ? 'bg-car-accent shadow-[0_0_6px_var(--accent-gold)]' : 'bg-car-sub']"></span>
            <span class="truncate">{{ store.vehicleAuto.voice_enable_steer_angle_guard ? '守护已开启' : '守护已关闭' }}</span>
          </button>
          <button
            @click="testVoice('steer_angle_guard')"
            class="h-[52px] px-4 rounded-2xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[16px] cursor-pointer shadow-sm transition-all flex items-center justify-center space-x-1"
          >
            <span>试听提醒</span>
            <span>➔</span>
          </button>
        </div>
      </div>

      <!-- 守护项 2: 电子手刹未拉起 -->
      <div class="rounded-3xl border-2 border-car-border hover:border-car-border-light bg-car-card p-6 shadow-xl h-full min-h-[260px] flex flex-col justify-between transition-all duration-200">
        <div class="flex items-center justify-between shrink-0 mb-2">
          <div class="flex items-center space-x-3">
            <span class="text-[20px] font-black text-car-text tracking-wide">电子手刹未拉起预警 (测试)</span>
            <button
              @click.stop="showEpbHelp"
              class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-accent hover:border-car-accent font-black text-[18px] flex items-center justify-center cursor-pointer shadow-sm transition-transform active:scale-95 shrink-0"
            >
              ?
            </button>
          </div>
          <span :class="['px-3 py-1 rounded-full text-[13px] font-black border shrink-0', store.vehicleAuto.voice_enable_epb_guard ? 'bg-emerald-500/10 border-emerald-500/40 text-car-text' : 'bg-car-item border-car-border text-car-sub']">
            {{ store.vehicleAuto.voice_enable_epb_guard ? '● 已开启' : '○ 已关闭' }}
          </span>
        </div>

        <div class="flex-1 min-w-0 flex flex-col justify-center space-y-1.5 py-3 text-[15px] leading-relaxed">
          <div class="text-car-sub font-bold">挂 P 挡推开主驾车门时，检测到电子手刹未拉起</div>
          <div class="text-car-text font-bold">语音提示检查驻车制动，辅助防范溜车隐患</div>
        </div>

        <div class="grid grid-cols-2 gap-3 pt-4 border-t border-car-border/60 mt-auto shrink-0">
          <button
            @click="toggleGuard('voice_enable_epb_guard')"
            :class="[
              'h-[52px] px-4 rounded-2xl font-black text-[16px] cursor-pointer transition-all border-2 flex items-center justify-center space-x-2 shadow-sm',
              store.vehicleAuto.voice_enable_epb_guard
                ? 'bg-car-item border-car-accent text-car-text shadow-md'
                : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            <span :class="['w-2.5 h-2.5 rounded-full', store.vehicleAuto.voice_enable_epb_guard ? 'bg-car-accent shadow-[0_0_6px_var(--accent-gold)]' : 'bg-car-sub']"></span>
            <span class="truncate">{{ store.vehicleAuto.voice_enable_epb_guard ? '守护已开启' : '守护已关闭' }}</span>
          </button>
          <button
            @click="testVoice('epb_guard')"
            class="h-[52px] px-4 rounded-2xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[16px] cursor-pointer shadow-sm transition-all flex items-center justify-center space-x-1"
          >
            <span>试听警报</span>
            <span>➔</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { store, bridge, showToast, openModal } from '../store';

const activeGuardCount = computed(() => {
  let count = 0;
  if (store.vehicleAuto.voice_enable_steer_angle_guard) count++;
  if (store.vehicleAuto.voice_enable_epb_guard) count++;
  return count;
});

function toggleGuard(key) {
  const next = !store.vehicleAuto[key];
  store.vehicleAuto[key] = next;
  bridge.call('setVehicleAutomationSetting', key, next);
  showToast('守护设置已更新: ' + (next ? '已开启' : '已关闭'));
}

function testVoice(type) {
  // 使用原生守护语音试听分支；不模拟车辆传感器触发。
  bridge.call('testVehicleVoice', type);
}

function showSteerHelp() {
  openModal('confirm', {
    title: '【测试功能指南】方向盘未回正提醒',
    desc: '1. 触发条件：挂入 P 挡解开安全带或推开主驾车门准备下车时，方向盘偏离中心角度较大则温婉提醒回正。\n\n2. 克制表述：偶尔停放时方向盘未完全回正属正常现象，不必然伤车；坡道停车请按驾驶规范转向车轮并拉起手刹。\n\n3. 辅助定位：本提醒仅作下车前辅助提示，不替代仪表与警示灯；未知信号不报警。',
    tip: '测试阶段功能，待实车逐项验证通过后转正。',
    showCancel: false,
    confirmText: '我知道了'
  });
}

function showEpbHelp() {
  openModal('confirm', {
    title: '【测试功能指南】电子手刹未拉起预警',
    desc: '1. 触发条件：挂 P 挡推开主驾车门瞬间，检测到电子手刹未拉起时语音警报。\n\n2. 防溜车定位：纯语音提醒不遮挡视线，防止坡道溜车隐患。\n\n3. 辅助定位：仅作辅助提醒，不替代仪表与警示灯；未知信号不报警。',
    tip: '测试阶段功能，待实车逐项验证通过后转正。',
    showCancel: false,
    confirmText: '我知道了'
  });
}

function showArbiterHelp() {
  openModal('confirm', {
    title: '【语音协同与音频仲裁说明】',
    desc: '通话进行中：普通座舱语音保持静默避让，仅高危守护语音可插播。\n\n蓝牙媒体：微信语音、手机音乐等蓝牙媒体场景下，系统可能无法逐条识别当前播放内容；工具箱播报与蓝牙媒体走底层混音共存。\n\n混音承诺边界：不承诺固定压低比率或绝对混音效果，实际闪避与恢复由原厂音频仲裁决定。',
    tip: '安全提醒仅辅助不替代仪表，未知信号不报警。',
    showCancel: false,
    confirmText: '我知道了'
  });
}
</script>
