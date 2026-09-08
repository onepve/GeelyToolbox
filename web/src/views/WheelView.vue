<template>
  <div class="flex flex-col space-y-6">
    <!-- 核心：方向盘按键方控接管总开关 - 提权置顶首屏 (完整卡片闭合包裹) -->
    <FeatureCard>
      <template #header>
        <div class="flex items-center justify-between w-full">
          <div class="flex items-center space-x-3">
            <span :class="['w-3.5 h-3.5 rounded-full shadow-md shrink-0', store.vehicleAuto.wheel_master_switch ? 'bg-emerald-500 shadow-[0_0_10px_#10B981]' : 'bg-rose-500 shadow-[0_0_10px_#F43F5E]']"></span>
            <span class="text-[21px] font-black text-car-text tracking-wide">方向盘按键方控接管总开关</span>
          </div>
          <span :class="['px-3.5 py-1 text-[13.5px] font-black rounded-full border shrink-0', store.vehicleAuto.wheel_master_switch ? 'bg-emerald-500/15 text-emerald-400 border-emerald-500/40' : 'bg-rose-500/15 text-rose-400 border-rose-500/40']">
            {{ store.vehicleAuto.wheel_master_switch ? '方控接管已启用' : '方控已彻底放行 (不干涉)' }}
          </span>
        </div>
      </template>
      <template #desc>
        独立控制是否接管滚轮下按、静音键与 Mode 键。如开启米小江且完全不想让工具箱参与方控，可直接关闭此项，彻底放行给原厂与米小江。
      </template>

      <button
        @click="toggleWheelMasterSwitch"
        :class="[
          'w-full min-h-[72px] rounded-2xl border-2 font-black text-[18.5px] cursor-pointer transition-all shadow-sm flex items-center justify-center whitespace-nowrap',
          store.vehicleAuto.wheel_master_switch
            ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/25'
            : 'bg-rose-500/15 border-rose-500 text-rose-400 hover:bg-rose-500/25'
        ]"
      >
        <span>{{ store.vehicleAuto.wheel_master_switch ? '方控接管总开关: 已开启 (按键接管分发中)' : '方控接管总开关: 已关闭 (彻底放行不干涉)' }}</span>
      </button>
    </FeatureCard>

    <!-- 车型图解与协议指示区 (缤越 COOL / SX-0017 原厂按键分布) -->
    <div class="bg-car-item border border-car-border rounded-2xl p-5 shadow-sm">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center">
          <span class="w-3 h-3 rounded-full bg-car-accent mr-3 shadow-[0_0_8px_var(--accent-gold)]"></span>
          <div class="flex flex-col">
            <span class="text-[18px] font-black text-car-text">吉利缤越 COOL (SX-0017 / IHU516G) 原厂方控图解</span>
            <span class="text-[14px] text-car-sub font-bold mt-0.5">左侧多媒体控制区：滚轮与实体按键已完整映射适配</span>
          </div>
        </div>
        <button 
          @click="showDiagram = !showDiagram"
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

        <!-- 按键编号与原厂定义清单 (使用 space-y 与 space-x 实体隔离带，彻底杜绝低版本内核 gap 塌陷错位) -->
        <div class="flex-1 min-w-0 flex flex-col space-y-2.5 text-[14.5px] font-bold text-car-sub">
          <div class="flex space-x-3">
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border">
              <span class="text-car-text font-black mr-1.5">① 主页键:</span> 返回车机中控主页
            </div>
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border">
              <span class="text-car-accent font-black mr-1.5">② 音量调节键:</span> 调节音量 / 垂直下压
            </div>
          </div>
          <div class="flex space-x-3">
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border">
              <span class="text-car-accent font-black mr-1.5">③ 静音键:</span> 开关静音 (长按10s硬件冷重启)
            </div>
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border">
              <span class="text-car-accent font-black mr-1.5">④ 向右选择键:</span> 下一个文件 / 下一曲
            </div>
          </div>
          <div class="flex space-x-3">
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border">
              <span class="text-car-text font-black mr-1.5">⑤ 模式切换键:</span> 驾驶模式切换 (原厂未配置)
            </div>
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border">
              <span class="text-car-accent font-black mr-1.5">⑥ MODE键:</span> 音源切换 / 自定义映射
            </div>
          </div>
          <div class="w-full p-3 rounded-xl bg-car-card border border-car-border">
            <span class="text-car-text font-black mr-1.5">⑦ 向左选择键:</span> 上一个文件 / 上一曲
          </div>
        </div>
      </div>
    </div>

    <!-- 1. 方向盘方控接管模式 -->
    <FeatureCard 
      title="1. 方向盘方控接管模式 (兼容米小江)"
      desc="彻底屏蔽原厂收音机伴听抢占广播，支持米小江优先协同或控制台全量自定义。"
    >
      <div class="grid grid-cols-3 gap-3.5" style="grid-gap: 14px; -webkit-column-gap: 14px;">
        <MatrixButton 
          title="米小江方控优先"
          subtitle="放行切歌与Mode键，工具箱补充静音与滚轮下按"
          :active="store.vehicleAuto.wheel_control_mode === 'carmedia_first'"
          @click="setWheelMode('carmedia_first')"
        />
        <MatrixButton 
          title="控制台独立接管"
          subtitle="工具箱接管切歌与所有按键自定义分发"
          :active="store.vehicleAuto.wheel_control_mode === 'toolbox_alone'"
          @click="setWheelMode('toolbox_alone')"
        />
        <MatrixButton 
          title="恢复原厂默认"
          subtitle="解禁原厂组件，完全不拦截任何按键"
          :active="store.vehicleAuto.wheel_control_mode === 'factory_default'"
          @click="setWheelMode('factory_default')"
        />
      </div>
    </FeatureCard>

    <!-- 2. 音量调节键按压映射 (编号 2) -->
    <FeatureCard 
      title="2. 方向盘音量调节键按压映射 (编号 2 滚轮按压)"
      desc="中央音量滚轮除了上下拨动调节音量外，垂直向下按压可触发自定义动作。默认保持原厂不动。"
    >
      <div class="grid grid-cols-5 gap-3.5" style="grid-gap: 14px; -webkit-column-gap: 14px;">
        <MatrixButton 
          title="保持原厂不动"
          subtitle="原厂默认不动作"
          :active="store.vehicleAuto.wheel_action_ok === 'default'"
          @click="setOkAction('default')"
        />
        <MatrixButton 
          title="打开 360 全景"
          subtitle="一键秒开车身环视"
          :active="store.vehicleAuto.wheel_action_ok === 'open_360'"
          @click="setOkAction('open_360')"
        />
        <MatrixButton 
          title="打开高德地图"
          subtitle="一键秒切车载导航"
          :active="store.vehicleAuto.wheel_action_ok === 'open_navi'"
          @click="setOkAction('open_navi')"
        />
        <MatrixButton 
          title="音量暂停 / 播放"
          subtitle="媒体暂停或继续播放"
          :active="store.vehicleAuto.wheel_action_ok === 'play_pause'"
          @click="setOkAction('play_pause')"
        />
        <MatrixButton 
          :title="isCustomApp(store.vehicleAuto.wheel_action_ok) ? (getCustomAppName('ok') || '自定义应用') : '自定义打开应用'"
          :subtitle="isCustomApp(store.vehicleAuto.wheel_action_ok) ? '点击可重新更换应用' : '自由挑选车机第三方应用'"
          :active="isCustomApp(store.vehicleAuto.wheel_action_ok)"
          @click="openAppSelectModal('ok')"
        />
      </div>
    </FeatureCard>

    <!-- 3. 独立静音键短按映射 (编号 3) -->
    <FeatureCard 
      title="3. 方向盘独立静音键短按映射 (编号 3)"
      desc="短按本键执行自定义动作（系统已内置防静音干扰补偿）；长按本键 10 秒依然是整车硬件看门狗冷重启救砖，不受任何影响！"
    >
      <div class="grid grid-cols-4 gap-3.5" style="grid-gap: 14px; -webkit-column-gap: 14px;">
        <MatrixButton 
          title="保持原厂不动"
          subtitle="系统原厂静音/恢复"
          :active="store.vehicleAuto.wheel_action_mute === 'default'"
          @click="setMuteAction('default')"
        />
        <MatrixButton 
          title="打开 360 全景"
          subtitle="一键秒调环视盲区"
          :active="store.vehicleAuto.wheel_action_mute === 'open_360'"
          @click="setMuteAction('open_360')"
        />
        <MatrixButton 
          title="打开高德地图"
          subtitle="一键秒切车载导航"
          :active="store.vehicleAuto.wheel_action_mute === 'open_navi'"
          @click="setMuteAction('open_navi')"
        />
        <MatrixButton 
          :title="isCustomApp(store.vehicleAuto.wheel_action_mute) ? (getCustomAppName('mute') || '自定义应用') : '自定义打开应用'"
          :subtitle="isCustomApp(store.vehicleAuto.wheel_action_mute) ? '点击可重新更换应用' : '自由挑选车机第三方应用'"
          :active="isCustomApp(store.vehicleAuto.wheel_action_mute)"
          @click="openAppSelectModal('mute')"
        />
      </div>
    </FeatureCard>

    <!-- 4. Mode 键短按映射 (编号 6) -->
    <FeatureCard 
      title="4. 方向盘 Mode 键短按映射 (编号 6)"
      desc="原车用于切换伴听/收音机。按下瞬间直接拉起目标功能，平铺直选，选中的直接高亮。"
    >
      <div class="grid grid-cols-5 gap-3.5" style="grid-gap: 14px; -webkit-column-gap: 14px;">
        <MatrixButton 
          title="保持原厂不动"
          subtitle="走原车音源切换"
          :active="store.vehicleAuto.wheel_action_mode === 'default'"
          @click="setModeAction('default')"
        />
        <MatrixButton 
          title="打开 360 全景"
          subtitle="一键秒开车身环视"
          :active="store.vehicleAuto.wheel_action_mode === 'open_360'"
          @click="setModeAction('open_360')"
        />
        <MatrixButton 
          title="打开高德地图"
          subtitle="一键秒切车载导航"
          :active="store.vehicleAuto.wheel_action_mode === 'open_navi'"
          @click="setModeAction('open_navi')"
        />
        <MatrixButton 
          title="音量暂停 / 播放"
          subtitle="媒体暂停或继续播放"
          :active="store.vehicleAuto.wheel_action_mode === 'play_pause'"
          @click="setModeAction('play_pause')"
        />
        <MatrixButton 
          :title="isCustomApp(store.vehicleAuto.wheel_action_mode) ? (getCustomAppName('mode') || '自定义应用') : '自定义打开应用'"
          :subtitle="isCustomApp(store.vehicleAuto.wheel_action_mode) ? '点击可重新更换应用' : '自由挑选车机第三方应用'"
          :active="isCustomApp(store.vehicleAuto.wheel_action_mode)"
          @click="openAppSelectModal('mode')"
        />
      </div>
    </FeatureCard>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';
import { store, bridge, showToast, openModal } from '../store';
import wheelGuideImg from '../assets/steering_wheel_guide.webp';

const showDiagram = ref(true);

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
  showToast('方控模式已切换: ' + (mode === 'carmedia_first' ? '米小江优先' : (mode === 'toolbox_alone' ? '控制台接管' : '恢复原厂')));
}

function setOkAction(act) {
  store.vehicleAuto.wheel_action_ok = act;
  bridge.call('setWheelControlStringSetting', 'wheel_action_ok', act);
  showToast('滚轮按压已映射');
}

function setMuteAction(act) {
  store.vehicleAuto.wheel_action_mute = act;
  bridge.call('setWheelControlStringSetting', 'wheel_action_mute', act);
  showToast('静音短按已映射');
}

function setModeAction(act) {
  store.vehicleAuto.wheel_action_mode = act;
  bridge.call('setWheelControlStringSetting', 'wheel_action_mode', act);
  showToast('Mode 键已映射');
}
</script>
