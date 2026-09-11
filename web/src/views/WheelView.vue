<template>
  <div class="flex flex-col space-y-6">
    <!-- 核心：方向盘按键方控接管总开关 - 提权置顶首屏 (左右分栏车规黄金磁贴) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-5 min-h-[106px] shadow-xl flex items-center justify-between transition-all">
      <div class="w-[60%] max-w-[60%] flex flex-col space-y-1.5 shrink-0">
        <div class="flex items-center space-x-3">
          <span :class="['w-3.5 h-3.5 rounded-full shadow-md shrink-0', store.vehicleAuto.wheel_master_switch ? 'bg-emerald-500 shadow-[0_0_10px_#10B981]' : 'bg-slate-400']"></span>
          <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">方向盘按键方控接管总开关</span>
          <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-item border-car-border text-car-text inline-flex items-center shrink-0 shadow-sm">
            <span :class="['w-2.5 h-2.5 rounded-full mr-2', store.vehicleAuto.wheel_master_switch ? 'bg-emerald-500 shadow-[0_0_6px_#10B981]' : 'bg-slate-400']"></span>
            {{ store.vehicleAuto.wheel_master_switch ? '方控接管已启用' : '方控已彻底放行 (不干涉)' }}
          </span>
        </div>
        <div class="text-[14.5px] text-car-sub font-bold leading-normal">
          全面接管静音键、Mode键与上一曲/下一曲。支持单击、双击、长按三种手势。
        </div>
      </div>

      <div class="shrink-0 w-[230px]">
        <button
          @click="toggleWheelMasterSwitch"
          :class="[
            'w-full h-[74px] px-4 py-2 rounded-2xl border-2 cursor-pointer transition-all shadow-md flex flex-col items-center justify-center text-center',
            store.vehicleAuto.wheel_master_switch
              ? 'bg-car-item border-car-accent'
              : 'bg-car-card border-car-border hover:border-car-border-light'
          ]"
        >
          <span class="text-[18.5px] font-black text-car-text tracking-wide whitespace-nowrap">
            {{ store.vehicleAuto.wheel_master_switch ? '方控接管已开启' : '方控接管已关闭' }}
          </span>
          <span :class="['text-[12.5px] font-bold mt-1 whitespace-nowrap', store.vehicleAuto.wheel_master_switch ? 'text-car-accent' : 'text-car-sub']">
            {{ store.vehicleAuto.wheel_master_switch ? '点击切换为彻底放行' : '点击开启按键接管' }}
          </span>
        </button>
      </div>
    </div>

    <!-- 车型图解与协议指示区 (缤越 COOL / SX-0017 原厂按键分布) -->
    <div class="bg-car-item border border-car-border rounded-2xl p-5 shadow-sm">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center">
          <span class="w-3 h-3 rounded-full bg-car-accent mr-3 shadow-[0_0_8px_var(--accent-gold)]"></span>
          <div class="flex flex-col">
            <span class="text-[18px] font-black text-car-text">吉利缤越 COOL (SX-0017 / IHU516G) 原厂方控图解</span>
            <span class="text-[14px] text-car-sub font-bold mt-0.5">左侧多媒体控制区：静音、切歌与Mode键已全量打通；滚轮按压与模式键底层无日志</span>
          </div>
        </div>
        <button 
          @click="toggleDiagram()"
          class="h-[44px] px-5 rounded-xl bg-car-card border border-car-border text-car-sub hover:text-car-text font-black text-[15px] cursor-pointer hover:border-car-border-light transition-all shadow-sm"
        >
          {{ showDiagram ? '收起图解' : '展开图解' }}
        </button>
      </div>

      <!-- 图解内容展示区 -->
      <div v-if="showDiagram" class="mt-4 pt-4 border-t border-car-border/60 flex items-start space-x-6">
        <!-- 原厂方向盘按键高清图示 -->
        <div class="p-3 bg-white rounded-2xl shadow-md border-2 border-car-border w-[320px] min-w-[320px] max-w-[320px] shrink-0">
          <img :src="wheelGuideImg" alt="吉利缤越 COOL 方向盘多媒体按键说明图 SX-0017" class="w-full h-auto rounded-xl object-contain block" />
        </div>

        <!-- 按键编号与原厂定义清单 -->
        <div class="flex-1 min-w-0 flex flex-col space-y-2.5 text-[14.5px] font-bold text-car-sub">
          <div class="flex space-x-3">
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-text font-black mr-1.5">① 主页键:</span> 返回车机中控主页
            </div>
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-text font-black mr-1.5">② 音量调节键:</span> 上下拨动调音量 (按压不可用)
            </div>
          </div>
          <div class="flex space-x-3">
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">③ 静音键:</span> 开关静音 (支持多手势自定义)
            </div>
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">④ 向右选择键:</span> 下一个文件 / 下一曲 (支持多手势)
            </div>
          </div>
          <div class="flex space-x-3">
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-text font-black mr-1.5">⑤ 模式切换键:</span> 驾驶模式切换
            </div>
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">⑥ MODE键:</span> 音源切换 / 自定义 (支持多手势)
            </div>
          </div>
          <div class="w-full p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
            <span class="text-car-accent font-black mr-1.5">⑦ 向左选择键:</span> 上一个文件 / 上一曲 (支持多手势)
          </div>
        </div>
      </div>
    </div>

    <!-- 1. 方向盘方控接管模式 -->
    <FeatureCard 
      title="1. 方向盘方控接管模式 (兼容米小江)"
      desc="彻底屏蔽原厂收音机伴听抢占广播，支持米小江优先协同或控制台全量独立接管。若两个软件都装，选米小江优先互不冲突。"
    >
      <div class="grid grid-cols-3 gap-3.5" style="grid-gap: 14px; -webkit-column-gap: 14px;">
        <MatrixButton 
          title="米小江方控优先"
          subtitle="放行切歌与Mode键，工具箱补充静音键多手势接管"
          :active="store.vehicleAuto.wheel_control_mode === 'carmedia_first'"
          @click="setWheelMode('carmedia_first')"
        />
        <MatrixButton 
          title="控制台独立接管 (推荐)"
          subtitle="工具箱接管切歌与所有按键多手势自定义"
          :active="store.vehicleAuto.wheel_control_mode === 'toolbox_alone' || (!store.vehicleAuto.wheel_control_mode && store.vehicleAuto.wheel_control_mode !== 'factory_default')"
          @click="setWheelMode('toolbox_alone')"
        />
        <MatrixButton 
          title="恢复原厂默认"
          subtitle="解禁原厂广播接收器，完全不拦截任何按键"
          :active="store.vehicleAuto.wheel_control_mode === 'factory_default'"
          @click="setWheelMode('factory_default')"
        />
      </div>
    </FeatureCard>

    <!-- 3. 方控按键长按判定时长 (自定义秒数) -->
    <FeatureCard 
      title="2. 方控按键长按判定触发时长 (自定义秒数)"
      desc="自由设定方向盘所有按键长按触发的判定时长 (0.8s ~ 6.0s)。达到该时长立即执行长按动作；长按 10 秒依然是整车硬件看门狗冷重启救砖，互不冲突。"
    >
      <template #badge>
        <span class="text-[15px] font-black text-car-accent px-3.5 py-1.5 bg-car-item rounded-xl border border-car-border">
          当前判定: {{ longPressSec }} 秒 ({{ Math.round(longPressSec * 1000) }}ms)
        </span>
      </template>

      <div class="bg-car-item border border-car-border rounded-2xl p-4 flex items-center space-x-4 shadow-sm">
        <span class="text-[14px] text-car-sub font-bold whitespace-nowrap">0.8 秒 (极速触发)</span>
        <input 
          type="range" 
          min="0.8" 
          max="6.0" 
          step="0.1" 
          v-model.number="longPressSec" 
          @input="updateLongPressSec"
          @change="updateLongPressSec"
          class="flex-1 accent-car-accent h-2.5 bg-car-card rounded-lg cursor-pointer"
        />
        <span class="text-[14px] text-car-sub font-bold whitespace-nowrap">6.0 秒 (防误触)</span>
        <div class="flex space-x-2 shrink-0">
          <button 
            v-for="preset in [1.0, 1.5, 2.0, 3.0, 5.0]" 
            :key="preset"
            @click="setLongPressPreset(preset)"
            :class="[
              'px-3 py-1.5 text-[13.5px] font-black rounded-xl border transition-all cursor-pointer shadow-sm',
              longPressSec === preset ? 'bg-car-card border-2 border-car-accent text-car-accent' : 'bg-car-card border border-car-border text-car-sub'
            ]"
          >
            {{ preset }}s
          </button>
        </div>
      </div>
    </FeatureCard>

    <!-- 3. 独立静音键多手势映射 (编号 3) -->
    <FeatureCard 
      title="3. 方向盘独立静音键映射 (编号 3)"
      desc="支持【单击】、【双击】、【长按 1.5 秒】多手势；长按 10 秒依然是整车硬件看门狗冷重启救砖，互不冲突！"
    >
      <div class="flex items-center justify-between bg-car-item border border-car-border rounded-2xl p-3 mb-4 shadow-sm">
        <div class="flex items-center space-x-2">
          <button
            v-for="g in gestureList"
            :key="g.id"
            @click="activeGesture.mute = g.id"
            :class="[
              'px-4 py-2 rounded-xl text-[14.5px] font-black cursor-pointer transition-all',
              activeGesture.mute === g.id
                ? 'bg-car-card border-2 border-car-accent text-car-text shadow-sm'
                : 'bg-car-card border border-car-border text-car-sub hover:border-car-border-light'
            ]"
          >
            {{ g.name }}
          </button>
        </div>
        <div class="text-[13.5px] font-bold text-car-sub">
          当前配置：<span class="text-car-accent">单击[{{ getActionName(getGestureAction('mute', 'single')) }}]</span> · 
          <span>双击[{{ getActionName(getGestureAction('mute', 'double')) }}]</span> · 
          <span>长按[{{ getActionName(getGestureAction('mute', 'long')) }}]</span>
        </div>
      </div>

      <div class="grid grid-cols-5 gap-3.5" style="grid-gap: 14px; -webkit-column-gap: 14px;">
        <MatrixButton 
          title="保持原厂不动"
          subtitle="系统原厂静音/恢复"
          :active="getGestureAction('mute', activeGesture.mute) === 'default'"
          @click="setGestureAction('mute', activeGesture.mute, 'default')"
        />
        <MatrixButton 
          title="打开 360 全景"
          subtitle="一键秒调环视盲区"
          :active="getGestureAction('mute', activeGesture.mute) === 'open_360'"
          @click="setGestureAction('mute', activeGesture.mute, 'open_360')"
        />
        <MatrixButton 
          title="打开高德地图"
          subtitle="一键秒切车载导航"
          :active="getGestureAction('mute', activeGesture.mute) === 'open_navi'"
          @click="setGestureAction('mute', activeGesture.mute, 'open_navi')"
        />
        <MatrixButton 
          title="音量暂停 / 播放"
          subtitle="媒体暂停或继续播放"
          :active="getGestureAction('mute', activeGesture.mute) === 'play_pause'"
          @click="setGestureAction('mute', activeGesture.mute, 'play_pause')"
        />
        <MatrixButton 
          :title="isCustomApp(getGestureAction('mute', activeGesture.mute)) ? (getCustomAppName('mute_' + activeGesture.mute) || '自定义应用') : '自定义打开应用'"
          :subtitle="isCustomApp(getGestureAction('mute', activeGesture.mute)) ? '点击可重新更换应用' : '自由挑选车机第三方应用'"
          :active="isCustomApp(getGestureAction('mute', activeGesture.mute))"
          @click="openAppSelectModal('mute_' + activeGesture.mute)"
        />
      </div>
    </FeatureCard>

    <!-- 4. Mode 键多手势映射 (编号 6) -->
    <FeatureCard 
      title="4. 方向盘 Mode 键映射 (编号 6)"
      desc="原车用于切换伴听/收音机。默认单击秒开 360 全景，同时支持双击与长按个性化定制。"
    >
      <div class="flex items-center justify-between bg-car-item border border-car-border rounded-2xl p-3 mb-4 shadow-sm">
        <div class="flex items-center space-x-2">
          <button
            v-for="g in gestureList"
            :key="g.id"
            @click="activeGesture.mode = g.id"
            :class="[
              'px-4 py-2 rounded-xl text-[14.5px] font-black cursor-pointer transition-all',
              activeGesture.mode === g.id
                ? 'bg-car-card border-2 border-car-accent text-car-text shadow-sm'
                : 'bg-car-card border border-car-border text-car-sub hover:border-car-border-light'
            ]"
          >
            {{ g.name }}
          </button>
        </div>
        <div class="text-[13.5px] font-bold text-car-sub">
          当前配置：<span class="text-car-accent">单击[{{ getActionName(getGestureAction('mode', 'single')) }}]</span> · 
          <span>双击[{{ getActionName(getGestureAction('mode', 'double')) }}]</span> · 
          <span>长按[{{ getActionName(getGestureAction('mode', 'long')) }}]</span>
        </div>
      </div>

      <div class="grid grid-cols-5 gap-3.5" style="grid-gap: 14px; -webkit-column-gap: 14px;">
        <MatrixButton 
          title="保持原厂不动"
          subtitle="走原车音源切换"
          :active="getGestureAction('mode', activeGesture.mode) === 'default'"
          @click="setGestureAction('mode', activeGesture.mode, 'default')"
        />
        <MatrixButton 
          title="打开 360 全景"
          subtitle="一键秒开车身环视"
          :active="getGestureAction('mode', activeGesture.mode) === 'open_360'"
          @click="setGestureAction('mode', activeGesture.mode, 'open_360')"
        />
        <MatrixButton 
          title="打开高德地图"
          subtitle="一键秒切车载导航"
          :active="getGestureAction('mode', activeGesture.mode) === 'open_navi'"
          @click="setGestureAction('mode', activeGesture.mode, 'open_navi')"
        />
        <MatrixButton 
          title="音量暂停 / 播放"
          subtitle="媒体暂停或继续播放"
          :active="getGestureAction('mode', activeGesture.mode) === 'play_pause'"
          @click="setGestureAction('mode', activeGesture.mode, 'play_pause')"
        />
        <MatrixButton 
          :title="isCustomApp(getGestureAction('mode', activeGesture.mode)) ? (getCustomAppName('mode_' + activeGesture.mode) || '自定义应用') : '自定义打开应用'"
          :subtitle="isCustomApp(getGestureAction('mode', activeGesture.mode)) ? '点击可重新更换应用' : '自由挑选车机第三方应用'"
          :active="isCustomApp(getGestureAction('mode', activeGesture.mode))"
          @click="openAppSelectModal('mode_' + activeGesture.mode)"
        />
      </div>
    </FeatureCard>

    <!-- 5. 切歌键多手势映射 (编号 4 下一曲 / 编号 7 上一曲) -->
    <FeatureCard 
      title="5. 方向盘切歌键映射 (编号 4 下一曲 / 编号 7 上一曲)"
      desc="内置官方三重通道调度机制，完美兼容 QQ音乐车机版、网易云、酷狗。支持双击/长按扩展自定义。"
    >
      <div class="grid grid-cols-2 gap-4">
        <!-- 下一曲 (编号 4) -->
        <div class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col justify-between shadow-sm">
          <div class="flex items-center justify-between mb-3">
            <span class="text-[19px] font-black text-car-text">④ 下一曲键 (Next Track)</span>
            <div class="flex space-x-1.5">
              <button
                v-for="g in gestureList"
                :key="g.id"
                @click="activeGesture.next = g.id"
                :class="[
                  'px-2.5 py-1 rounded-lg text-[13px] font-black cursor-pointer transition-all',
                  activeGesture.next === g.id
                    ? 'bg-car-card border border-car-accent text-car-text shadow-sm'
                    : 'bg-car-card border border-car-border text-car-sub'
                ]"
              >
                {{ g.shortName }}
              </button>
            </div>
          </div>
          <div class="grid grid-cols-3 gap-2.5 mb-2">
            <MatrixButton 
              title="下一曲"
              subtitle="切歌(官方调度)"
              :active="getGestureAction('next', activeGesture.next) === 'next_track' || getGestureAction('next', activeGesture.next) === 'default'"
              @click="setGestureAction('next', activeGesture.next, 'next_track')"
            />
            <MatrixButton 
              title="打开 360"
              subtitle="唤起环视"
              :active="getGestureAction('next', activeGesture.next) === 'open_360'"
              @click="setGestureAction('next', activeGesture.next, 'open_360')"
            />
            <MatrixButton 
              title="播放/暂停"
              subtitle="音乐暂停"
              :active="getGestureAction('next', activeGesture.next) === 'play_pause'"
              @click="setGestureAction('next', activeGesture.next, 'play_pause')"
            />
          </div>
        </div>

        <!-- 上一曲 (编号 7) -->
        <div class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col justify-between shadow-sm">
          <div class="flex items-center justify-between mb-3">
            <span class="text-[19px] font-black text-car-text">⑦ 上一曲键 (Prev Track)</span>
            <div class="flex space-x-1.5">
              <button
                v-for="g in gestureList"
                :key="g.id"
                @click="activeGesture.prev = g.id"
                :class="[
                  'px-2.5 py-1 rounded-lg text-[13px] font-black cursor-pointer transition-all',
                  activeGesture.prev === g.id
                    ? 'bg-car-card border border-car-accent text-car-text shadow-sm'
                    : 'bg-car-card border border-car-border text-car-sub'
                ]"
              >
                {{ g.shortName }}
              </button>
            </div>
          </div>
          <div class="grid grid-cols-3 gap-2.5 mb-2">
            <MatrixButton 
              title="上一曲"
              subtitle="切歌(官方调度)"
              :active="getGestureAction('prev', activeGesture.prev) === 'prev_track' || getGestureAction('prev', activeGesture.prev) === 'default'"
              @click="setGestureAction('prev', activeGesture.prev, 'prev_track')"
            />
            <MatrixButton 
              title="打开高德"
              subtitle="唤起导航"
              :active="getGestureAction('prev', activeGesture.prev) === 'open_navi'"
              @click="setGestureAction('prev', activeGesture.prev, 'open_navi')"
            />
            <MatrixButton 
              title="播放/暂停"
              subtitle="音乐暂停"
              :active="getGestureAction('prev', activeGesture.prev) === 'play_pause'"
              @click="setGestureAction('prev', activeGesture.prev, 'play_pause')"
            />
          </div>
        </div>
      </div>
    </FeatureCard>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue';
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';
import { store, bridge, showToast, openModal } from '../store';
import wheelGuideImg from '../assets/steering_wheel_guide.webp';

// 原厂方控图解：默认折叠，并记忆上次的展开/折叠状态
const DIAGRAM_KEY = 'wheel_diagram_expanded';
const showDiagram = ref(localStorage.getItem(DIAGRAM_KEY) === 'true');

function toggleDiagram() {
  showDiagram.value = !showDiagram.value;
  localStorage.setItem(DIAGRAM_KEY, String(showDiagram.value));
}
const longPressSec = computed({
  get: () => {
    const ms = store.vehicleAuto.wheel_long_press_ms || 1500;
    return parseFloat((ms / 1000).toFixed(1));
  },
  set: (val) => {
    const ms = Math.round(val * 1000);
    store.vehicleAuto.wheel_long_press_ms = ms;
    bridge.call('setVehicleAutomationIntSetting', 'wheel_long_press_ms', ms);
  }
});

function updateLongPressSec() {
  const ms = Math.round(longPressSec.value * 1000);
  store.vehicleAuto.wheel_long_press_ms = ms;
  bridge.call('setVehicleAutomationIntSetting', 'wheel_long_press_ms', ms);
}

function setLongPressPreset(sec) {
  longPressSec.value = sec;
  showToast(`长按判定时长已设定为: ${sec} 秒`);
}

const gestureList = computed(() => [
  { id: 'single', name: '单击 (Single)', shortName: '单击' },
  { id: 'double', name: '双击 (Double)', shortName: '双击' },
  { id: 'long', name: `长按 ${longPressSec.value}s (Long Press)`, shortName: `长按${longPressSec.value}s` }
]);

const activeGesture = reactive({
  mute: 'single',
  mode: 'single',
  next: 'single',
  prev: 'single'
});

function getGestureAction(key, gesture) {
  const pKey = `wheel_action_${key}_${gesture}`;
  if (store.vehicleAuto[pKey]) return store.vehicleAuto[pKey];
  if (gesture === 'single') {
    if (store.vehicleAuto[`wheel_action_${key}`]) return store.vehicleAuto[`wheel_action_${key}`];
    if (key === 'mode') return 'open_360';
    if (key === 'next') return 'next_track';
    if (key === 'prev') return 'prev_track';
  }
  return 'default';
}

function getActionName(act) {
  const map = {
    default: '原厂默认',
    open_360: '360全景',
    open_navi: '高德地图',
    play_pause: '播放/暂停',
    next_track: '下一曲',
    prev_track: '上一曲',
    mute_toggle: '静音切换',
    screen_off: '息屏休眠'
  };
  if (isCustomApp(act)) return '自定义App';
  return map[act] || act;
}

function setGestureAction(key, gesture, act) {
  const pKey = `wheel_action_${key}_${gesture}`;
  store.vehicleAuto[pKey] = act;
  if (gesture === 'single') {
    store.vehicleAuto[`wheel_action_${key}`] = act;
  }
  bridge.call('setWheelGestureAction', key, gesture, act);
  showToast(`已映射 [${gesture === 'single' ? '单击' : (gesture === 'double' ? '双击' : '长按')}] -> ${getActionName(act)}`);
}

function isCustomApp(actionVal) {
  return typeof actionVal === 'string' && actionVal.startsWith('app:');
}

function getCustomAppName(target) {
  return localStorage.getItem(`wheel_action_${target}_app_name`) || '';
}

function openAppSelectModal(target) {
  openModal('appSelect', { keyTarget: target });
}

function toggleWheelMasterSwitch() {
  const next = !store.vehicleAuto.wheel_master_switch;
  store.vehicleAuto.wheel_master_switch = next;
  bridge.call('setVehicleAutomationSetting', 'wheel_master_switch', next);
  showToast(next ? '方控接管总开关: 已开启 (接管分发)' : '方控接管总开关: 已关闭 (彻底放行)');
}

function setWheelMode(mode) {
  store.vehicleAuto.wheel_control_mode = mode;
  bridge.call('setWheelControlStringSetting', 'wheel_control_mode', mode);
  showToast('方控模式已切换: ' + (mode === 'carmedia_first' ? '米小江优先' : (mode === 'toolbox_alone' ? '控制台独立接管' : '恢复原厂')));
}

</script>
