<template>
  <div class="flex flex-col gap-5">
    <!-- 卡片 1: 方控接管模式 -->
    <FeatureCard 
      title="1. 方向盘方控接管模式 (兼容米小江)"
      desc="彻底屏蔽原厂收音机伴听抢占广播，支持米小江优先协同或控制台全量自定义。选中的模式直接高亮激活。"
    >
      <div class="grid grid-cols-3 gap-3.5">
        <MatrixButton 
          title="米小江方控优先 (推荐)"
          subtitle="放行切歌与Mode键，工具箱补充静音键改360"
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

    <!-- 卡片 2: Mode 键短按映射 -->
    <FeatureCard 
      title="2. 方向盘 Mode 键短按映射 (编号 6)"
      desc="原车用于切换伴听/收音机。按下瞬间直接拉起目标功能，4 个选项平铺直选，选中的直接高亮。"
    >
      <div class="grid grid-cols-4 gap-3.5">
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
          title="播放 / 暂停音乐"
          subtitle="控制第三方播放器"
          :active="store.vehicleAuto.wheel_action_mode === 'play_pause'"
          @click="setModeAction('play_pause')"
        />
        <MatrixButton 
          title="保持原厂默认"
          subtitle="走原车音源切换"
          :active="store.vehicleAuto.wheel_action_mode === 'default'"
          @click="setModeAction('default')"
        />
      </div>
    </FeatureCard>

    <!-- 卡片 3: 独立静音键短按映射 -->
    <FeatureCard 
      title="3. 方向盘独立静音键短按映射 (编号 3)"
      desc="短按本键执行自定义动作；特别说明：长按本键 10 秒以上依然是整车硬件看门狗冷重启救砖，不受任何影响！"
    >
      <div class="grid grid-cols-4 gap-3.5">
        <MatrixButton 
          title="短按打开 360"
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
          title="播放 / 暂停音乐"
          subtitle="控制第三方播放器"
          :active="store.vehicleAuto.wheel_action_mute === 'play_pause'"
          @click="setMuteAction('play_pause')"
        />
        <MatrixButton 
          title="保持原厂静音"
          subtitle="系统音频静音/恢复"
          :active="store.vehicleAuto.wheel_action_mute === 'mute_toggle'"
          @click="setMuteAction('mute_toggle')"
        />
      </div>
    </FeatureCard>

    <!-- 卡片 4: 滚轮垂直按压 Click 映射 -->
    <FeatureCard 
      title="4. 音量滚轮垂直按压 Click 映射 (编号 2)"
      desc="上下滚动保持原厂线性音量调节（行车安全第一）；垂直向下按压可自由重定向功能。"
    >
      <div class="grid grid-cols-3 gap-3.5">
        <MatrixButton 
          title="原厂默认 (确认/暂停)"
          subtitle="保持原生多媒体操作"
          :active="store.vehicleAuto.wheel_action_ok === 'default'"
          @click="setOkAction('default')"
        />
        <MatrixButton 
          title="按压打开 360"
          subtitle="中轴滚轮秒开全景"
          :active="store.vehicleAuto.wheel_action_ok === 'open_360'"
          @click="setOkAction('open_360')"
        />
        <MatrixButton 
          title="播放 / 暂停音乐"
          subtitle="控制第三方音乐"
          :active="store.vehicleAuto.wheel_action_ok === 'play_pause'"
          @click="setOkAction('play_pause')"
        />
      </div>
    </FeatureCard>
  </div>
</template>

<script setup>
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';
import { store, bridge, showToast } from '../store';

function setWheelMode(mode) {
  store.vehicleAuto.wheel_control_mode = mode;
  bridge.call('setWheelControlStringSetting', 'wheel_control_mode', mode);
  showToast('方控模式已切换: ' + (mode === 'carmedia_first' ? '米小江优先' : (mode === 'toolbox_alone' ? '控制台接管' : '恢复原厂')));
}

function setModeAction(act) {
  store.vehicleAuto.wheel_action_mode = act;
  bridge.call('setWheelControlStringSetting', 'wheel_action_mode', act);
  showToast('Mode 键已映射');
}

function setMuteAction(act) {
  store.vehicleAuto.wheel_action_mute = act;
  bridge.call('setWheelControlStringSetting', 'wheel_action_mute', act);
  showToast('静音短按已映射');
}

function setOkAction(act) {
  store.vehicleAuto.wheel_action_ok = act;
  bridge.call('setWheelControlStringSetting', 'wheel_action_ok', act);
  showToast('滚轮按压已映射');
}
</script>
