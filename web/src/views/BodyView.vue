<template>
  <div class="flex flex-col gap-5">
    <!-- 挡位安全播报 -->
    <FeatureCard 
      title="1. 挡位安全播报 (前进挡 D / 倒车挡 R)"
      desc="踩刹车挂入前进挡温馨启程，挂入倒车挡安全警示；车辆熄火离车后硬件强制处于 P 挡，蓝牙钥匙靠近点亮车机绝对静默零误报。"
    >
      <div class="grid grid-cols-2 gap-4">
        <div class="flex flex-col gap-2">
          <MatrixButton 
            title="前进挡 (D 挡)"
            :subtitle="store.vehicleAuto.voice_enable_gear_d ? '已开启 · 祝你一路平安' : '已关闭'"
            :active="store.vehicleAuto.voice_enable_gear_d"
            @click="toggleSetting('voice_enable_gear_d')"
          />
          <button 
            @click="testVoice('gear_d')"
            class="min-h-[48px] bg-car-item border border-car-border rounded-xl text-car-text font-black text-[16px] cursor-pointer hover:border-car-border-light"
          >
            试听 D 挡播报
          </button>
        </div>

        <div class="flex flex-col gap-2">
          <MatrixButton 
            title="倒车挡 (R 挡)"
            :subtitle="store.vehicleAuto.voice_enable_gear_r ? '已开启 · 注意观察后方' : '已关闭'"
            :active="store.vehicleAuto.voice_enable_gear_r"
            @click="toggleSetting('voice_enable_gear_r')"
          />
          <button 
            @click="testVoice('gear_r')"
            class="min-h-[48px] bg-car-item border border-car-border rounded-xl text-car-text font-black text-[16px] cursor-pointer hover:border-car-border-light"
          >
            试听 R 挡播报
          </button>
        </div>
      </div>
    </FeatureCard>

    <!-- 四门迎宾与关门提醒 -->
    <FeatureCard 
      title="2. 四门迎宾与关门提醒 (状态翻转机 · 关门立断)"
      desc="100% 锁定吉利真实 MCU 串口物理报文 (91 02 01 b6)，彻底废除时间防抖锁。开门播报未完突然关门时，毫秒级打断开门语音并无缝切入“车门已关好”。"
    >
      <div class="grid grid-cols-2 gap-4">
        <!-- 主驾 FL -->
        <div class="bg-car-item border border-car-border rounded-xl p-4 flex flex-col gap-3">
          <div class="text-[19px] font-black text-car-text">主驾车门 (FL)</div>
          <div class="grid grid-cols-2 gap-2">
            <MatrixButton 
              title="开门防杀"
              :subtitle="store.vehicleAuto.voice_enable_door_fl ? '已开启' : '已关闭'"
              :active="store.vehicleAuto.voice_enable_door_fl"
              @click="toggleSetting('voice_enable_door_fl')"
            />
            <MatrixButton 
              title="关门播报"
              :subtitle="store.vehicleAuto.voice_enable_door_fl_close ? '已开启' : '已关闭'"
              :active="store.vehicleAuto.voice_enable_door_fl_close"
              @click="toggleSetting('voice_enable_door_fl_close')"
            />
          </div>
          <button 
            @click="testVoice('door_fl')"
            class="min-h-[44px] bg-car-card border border-car-border rounded-lg text-car-text font-black text-[15px] cursor-pointer hover:border-car-border-light"
          >
            试听开门语音
          </button>
        </div>

        <!-- 副驾 FR -->
        <div class="bg-car-item border border-car-border rounded-xl p-4 flex flex-col gap-3">
          <div class="text-[19px] font-black text-car-text">副驾车门 (FR)</div>
          <div class="grid grid-cols-2 gap-2">
            <MatrixButton 
              title="开门迎宾"
              :subtitle="store.vehicleAuto.voice_enable_door_fr ? '已开启' : '已关闭'"
              :active="store.vehicleAuto.voice_enable_door_fr"
              @click="toggleSetting('voice_enable_door_fr')"
            />
            <MatrixButton 
              title="关门系安全带"
              :subtitle="store.vehicleAuto.voice_enable_door_fr_close ? '已开启' : '已关闭'"
              :active="store.vehicleAuto.voice_enable_door_fr_close"
              @click="toggleSetting('voice_enable_door_fr_close')"
            />
          </div>
          <button 
            @click="testVoice('door_fr')"
            class="min-h-[44px] bg-car-card border border-car-border rounded-lg text-car-text font-black text-[15px] cursor-pointer hover:border-car-border-light"
          >
            试听开门语音
          </button>
        </div>

        <!-- 左后 RL -->
        <div class="bg-car-item border border-car-border rounded-xl p-4 flex flex-col gap-3">
          <div class="text-[19px] font-black text-car-text">左后车门 (RL)</div>
          <div class="grid grid-cols-2 gap-2">
            <MatrixButton 
              title="开门提醒"
              :subtitle="store.vehicleAuto.voice_enable_door_rl ? '已开启' : '已关闭'"
              :active="store.vehicleAuto.voice_enable_door_rl"
              @click="toggleSetting('voice_enable_door_rl')"
            />
            <MatrixButton 
              title="关门已关好"
              :subtitle="store.vehicleAuto.voice_enable_door_rl_close ? '已开启' : '已关闭'"
              :active="store.vehicleAuto.voice_enable_door_rl_close"
              @click="toggleSetting('voice_enable_door_rl_close')"
            />
          </div>
          <button 
            @click="testVoice('door_rl')"
            class="min-h-[44px] bg-car-card border border-car-border rounded-lg text-car-text font-black text-[15px] cursor-pointer hover:border-car-border-light"
          >
            试听开门语音
          </button>
        </div>

        <!-- 右后 RR -->
        <div class="bg-car-item border border-car-border rounded-xl p-4 flex flex-col gap-3">
          <div class="text-[19px] font-black text-car-text">右后车门 (RR)</div>
          <div class="grid grid-cols-2 gap-2">
            <MatrixButton 
              title="开门提醒"
              :subtitle="store.vehicleAuto.voice_enable_door_rr ? '已开启' : '已关闭'"
              :active="store.vehicleAuto.voice_enable_door_rr"
              @click="toggleSetting('voice_enable_door_rr')"
            />
            <MatrixButton 
              title="关门已关好"
              :subtitle="store.vehicleAuto.voice_enable_door_rr_close ? '已开启' : '已关闭'"
              :active="store.vehicleAuto.voice_enable_door_rr_close"
              @click="toggleSetting('voice_enable_door_rr_close')"
            />
          </div>
          <button 
            @click="testVoice('door_rr')"
            class="min-h-[44px] bg-car-card border border-car-border rounded-lg text-car-text font-black text-[15px] cursor-pointer hover:border-car-border-light"
          >
            试听开门语音
          </button>
        </div>
      </div>
    </FeatureCard>

    <!-- 电动尾门与转向灯 -->
    <FeatureCard 
      title="3. 电动尾门、转向灯 360 与大灯日夜联动"
      desc="原厂电动尾门物理串口破译闭环；转向灯联动 360 全景内置车速 ≤30km/h 保护；大灯联动高德日夜模式。"
    >
      <div class="grid grid-cols-3 gap-4">
        <div class="bg-car-item border border-car-border rounded-xl p-4 flex flex-col justify-between gap-3">
          <div>
            <div class="text-[19px] font-black text-car-text">原厂电动尾门 (b7)</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">升起开门提醒与闭合锁止播报</div>
          </div>
          <div class="grid grid-cols-2 gap-2">
            <MatrixButton 
              title="开门播报"
              :subtitle="store.vehicleAuto.voice_enable_trunk_open ? '已开' : '已关'"
              :active="store.vehicleAuto.voice_enable_trunk_open"
              @click="toggleSetting('voice_enable_trunk_open')"
            />
            <MatrixButton 
              title="关门已关"
              :subtitle="store.vehicleAuto.voice_enable_trunk_close ? '已开' : '已关'"
              :active="store.vehicleAuto.voice_enable_trunk_close"
              @click="toggleSetting('voice_enable_trunk_close')"
            />
          </div>
        </div>

        <div class="bg-car-item border border-car-border rounded-xl p-4 flex flex-col justify-between gap-3">
          <div>
            <div class="text-[19px] font-black text-car-text">转向灯联动 360 全景</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">车速 ≤30km/h 允许唤醒盲区</div>
          </div>
          <MatrixButton 
            :title="store.vehicleAuto.vehicle_turn_360_enabled ? '已开启 (安全限速)' : '已关闭'"
            :active="store.vehicleAuto.vehicle_turn_360_enabled"
            @click="toggleSetting('vehicle_turn_360_enabled')"
          />
        </div>

        <div class="bg-car-item border border-car-border rounded-xl p-4 flex flex-col justify-between gap-3">
          <div>
            <div class="text-[19px] font-black text-car-text">大灯联动高德日夜模式</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">隧道与黑夜自动切换防刺眼</div>
          </div>
          <MatrixButton 
            :title="store.vehicleAuto.vehicle_light_nav_enabled ? '已开启' : '已关闭'"
            :active="store.vehicleAuto.vehicle_light_nav_enabled"
            @click="toggleSetting('vehicle_light_nav_enabled')"
          />
        </div>
      </div>
    </FeatureCard>
  </div>
</template>

<script setup>
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';
import { store, bridge, showToast } from '../store';

function toggleSetting(key) {
  const next = !store.vehicleAuto[key];
  store.vehicleAuto[key] = next;
  bridge.call('setVehicleAutomationSetting', key, next);
  showToast('设置已更新: ' + (next ? '已开启' : '已关闭'));
}

function testVoice(type) {
  bridge.call('testVehicleVoice', type);
}
</script>
