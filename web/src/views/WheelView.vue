<template>
  <div class="flex flex-col space-y-6">
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
      <div v-if="showDiagram" class="mt-4 pt-4 border-t border-car-border/60 flex flex-col md:flex-row items-center gap-6">
        <!-- 原厂方向盘按键高清图示 -->
        <div class="p-3 bg-white rounded-2xl shadow-md border-2 border-car-border max-w-[340px] shrink-0">
          <img :src="wheelGuideImg" alt="吉利缤越 COOL 方向盘多媒体按键说明图 SX-0017" class="w-full h-auto rounded-xl object-contain" />
        </div>

        <!-- 按键编号与原厂定义清单 -->
        <div class="flex-1 grid grid-cols-2 gap-3 text-[14.5px] font-bold text-car-sub">
          <div class="p-2.5 rounded-xl bg-car-card border border-car-border">
            <span class="text-car-text font-black mr-1.5">① 主页键:</span> 返回车机中控主页
          </div>
          <div class="p-2.5 rounded-xl bg-car-card border border-car-border">
            <span class="text-car-accent font-black mr-1.5">② 音量调节键:</span> 调节音量 / 垂直下压
          </div>
          <div class="p-2.5 rounded-xl bg-car-card border border-car-border">
            <span class="text-car-accent font-black mr-1.5">③ 静音键:</span> 开关静音 (长按10s硬件冷重启)
          </div>
          <div class="p-2.5 rounded-xl bg-car-card border border-car-border">
            <span class="text-car-text font-black mr-1.5">④ 向右选择键:</span> 下一个文件 / 下一曲
          </div>
          <div class="p-2.5 rounded-xl bg-car-card border border-car-border">
            <span class="text-car-text font-black mr-1.5">⑤ 模式切换键:</span> 切换主机与组合仪表控制权
          </div>
          <div class="p-2.5 rounded-xl bg-car-card border border-car-border">
            <span class="text-car-accent font-black mr-1.5">⑥ MODE键:</span> 音源切换 / 自定义映射
          </div>
          <div class="p-2.5 rounded-xl bg-car-card border border-car-border col-span-2">
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
      <div class="grid grid-cols-3 gap-3.5">
        <MatrixButton 
          title="米小江方控优先 (推荐)"
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
      <div class="grid grid-cols-4 gap-3.5">
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
      </div>
    </FeatureCard>

    <!-- 3. 独立静音键短按映射 (编号 3) -->
    <FeatureCard 
      title="3. 方向盘独立静音键短按映射 (编号 3)"
      desc="短按本键执行自定义动作（系统已内置防静音干扰补偿）；长按本键 10 秒依然是整车硬件看门狗冷重启救砖，不受任何影响！"
    >
      <div class="grid grid-cols-4 gap-3.5">
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
          title="音量暂停 / 播放"
          subtitle="媒体暂停或继续播放"
          :active="store.vehicleAuto.wheel_action_mute === 'play_pause'"
          @click="setMuteAction('play_pause')"
        />
      </div>
    </FeatureCard>

    <!-- 4. Mode 键短按映射 (编号 6) -->
    <FeatureCard 
      title="4. 方向盘 Mode 键短按映射 (编号 6)"
      desc="原车用于切换伴听/收音机。按下瞬间直接拉起目标功能，4 个选项平铺直选，选中的直接高亮。"
    >
      <div class="grid grid-cols-4 gap-3.5">
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
      </div>
    </FeatureCard>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';
import { store, bridge, showToast } from '../store';
import wheelGuideImg from '../assets/steering_wheel_guide.webp';

const showDiagram = ref(true);

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
