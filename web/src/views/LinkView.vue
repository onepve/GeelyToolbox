<template>
  <div class="flex flex-col space-y-6">
    <!-- 实时车身物理信号探针 (四门、尾门、挡位、模式 全量直观反映底层电平跃变) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-5 shadow-xl">
      <div class="flex items-center justify-between pb-3 mb-3 border-b border-car-border/60">
        <div class="flex items-center space-x-2.5">
          <span class="w-3 h-3 rounded-full bg-emerald-500 animate-pulse shadow-[0_0_8px_#10B981]"></span>
          <span class="text-[18.5px] font-black text-car-text">车身全域物理信号实时探针 (实车调试专用)</span>
        </div>
        <span class="text-[13.5px] text-car-sub font-bold">
          底层 MCU 串口 91 02 01、TCU 换挡与驾驶模式广播全量监听，动作毫秒级点亮
        </span>
      </div>

      <!-- 第一排：四门与电动尾门 -->
      <div class="grid grid-cols-5 gap-3 mb-3">
        <!-- 主驾门 -->
        <div 
          :class="[
            'p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all',
            doorStatus.fl === 1 
              ? 'bg-emerald-500/20 border-emerald-500 text-emerald-400 ring-2 ring-emerald-500/30 shadow-md' 
              : 'bg-car-item border-car-border text-car-sub'
          ]"
        >
          <span class="text-[13px] font-bold">主驾车门 (FL)</span>
          <span class="text-[16px] font-black mt-1">
            {{ doorStatus.fl === 1 ? '● 物理打开' : (doorStatus.fl === 0 ? '○ 已关好' : '采集中...') }}
          </span>
        </div>

        <!-- 副驾门 -->
        <div 
          :class="[
            'p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all',
            doorStatus.fr === 1 
              ? 'bg-emerald-500/20 border-emerald-500 text-emerald-400 ring-2 ring-emerald-500/30 shadow-md' 
              : 'bg-car-item border-car-border text-car-sub'
          ]"
        >
          <span class="text-[13px] font-bold">副驾车门 (FR)</span>
          <span class="text-[16px] font-black mt-1">
            {{ doorStatus.fr === 1 ? '● 物理打开' : (doorStatus.fr === 0 ? '○ 已关好' : '采集中...') }}
          </span>
        </div>

        <!-- 左后门 -->
        <div 
          :class="[
            'p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all',
            doorStatus.rl === 1 
              ? 'bg-emerald-500/20 border-emerald-500 text-emerald-400 ring-2 ring-emerald-500/30 shadow-md' 
              : 'bg-car-item border-car-border text-car-sub'
          ]"
        >
          <span class="text-[13px] font-bold">左后车门 (RL)</span>
          <span class="text-[16px] font-black mt-1">
            {{ doorStatus.rl === 1 ? '● 物理打开' : (doorStatus.rl === 0 ? '○ 已关好' : '采集中...') }}
          </span>
        </div>

        <!-- 右后门 -->
        <div 
          :class="[
            'p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all',
            doorStatus.rr === 1 
              ? 'bg-emerald-500/20 border-emerald-500 text-emerald-400 ring-2 ring-emerald-500/30 shadow-md' 
              : 'bg-car-item border-car-border text-car-sub'
          ]"
        >
          <span class="text-[13px] font-bold">右后车门 (RR)</span>
          <span class="text-[16px] font-black mt-1">
            {{ doorStatus.rr === 1 ? '● 物理打开' : (doorStatus.rr === 0 ? '○ 已关好' : '采集中...') }}
          </span>
        </div>

        <!-- 电动尾门 -->
        <div 
          :class="[
            'p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all',
            doorStatus.trunk === 1 
              ? 'bg-emerald-500/20 border-emerald-500 text-emerald-400 ring-2 ring-emerald-500/30 shadow-md' 
              : 'bg-car-item border-car-border text-car-sub'
          ]"
        >
          <span class="text-[13px] font-bold">电动尾门 (Trunk)</span>
          <span class="text-[16px] font-black mt-1">
            {{ doorStatus.trunk === 1 ? '● 升起打开' : (doorStatus.trunk === 0 ? '○ 已锁止' : '采集中...') }}
          </span>
        </div>
      </div>

      <!-- 第二排：挡位、驾驶模式与实时车速 -->
      <div class="grid grid-cols-3 gap-3">
        <!-- 挡位状态 -->
        <div class="p-3 rounded-2xl bg-car-item border-2 border-car-border flex items-center justify-between">
          <div>
            <div class="text-[13px] font-bold text-car-sub">实时挡位状态 (Gear)</div>
            <div class="text-[17px] font-black text-car-text mt-0.5">
              {{ doorStatus.gear_name || '采集中...' }}
            </div>
          </div>
          <span class="px-2.5 py-1 text-[12px] font-extrabold rounded-lg bg-car-card border border-car-border text-car-sub">
            实时监听中
          </span>
        </div>

        <!-- 驾驶模式 -->
        <div class="p-3 rounded-2xl bg-car-item border-2 border-car-border flex items-center justify-between">
          <div>
            <div class="text-[13px] font-bold text-car-sub">实时驾驶模式 (DriveMode)</div>
            <div class="text-[17px] font-black text-car-accent mt-0.5">
              {{ doorStatus.mode_name || '采集中...' }}
            </div>
          </div>
          <span class="px-2.5 py-1 text-[12px] font-extrabold rounded-lg bg-car-card border border-car-border text-car-sub">
            AdaptAPI 9位常量直通
          </span>
        </div>

        <!-- 实时车速 -->
        <div class="p-3 rounded-2xl bg-car-item border-2 border-car-border flex items-center justify-between">
          <div>
            <div class="text-[13px] font-bold text-car-sub">实时车速 (Speed)</div>
            <div class="text-[17px] font-black text-car-text mt-0.5">
              {{ doorStatus.speed !== undefined ? doorStatus.speed + ' km/h' : '采集中...' }}
            </div>
          </div>
          <span class="px-2.5 py-1 text-[12px] font-extrabold rounded-lg bg-car-card border border-car-border text-car-sub">
            ≤30km/h 盲区保护
          </span>
        </div>
      </div>
    </div>

    <!-- 1. 转向灯与 D 挡起步 360 全景联动 -->
    <FeatureCard 
      title="1. 转向灯与 D 挡起步 360 全景盲区联动"
      desc="原厂 360 环视核心状态机毫秒级联动；内置车速 ≤30km/h 安全阈值保护，高速巡航变道自动静默抑制。"
    >
      <div class="grid grid-cols-2 gap-4">
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text">转向灯联动 360 全景</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">打起转向灯秒开盲区画面，回正自动退出</div>
          </div>
          <MatrixButton 
            :title="store.vehicleAuto.vehicle_turn_360_enabled ? '已开启' : '已关闭'"
            :active="store.vehicleAuto.vehicle_turn_360_enabled"
            @click="toggleSetting('vehicle_turn_360_enabled')"
          />
        </div>

        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text">D 挡起步联动 360 全景</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">挂入前进挡秒开全景看盲区，起步后自动退出</div>
          </div>
          <MatrixButton 
            :title="store.vehicleAuto.vehicle_gear_d_360_enabled ? '已开启' : '已关闭'"
            :active="store.vehicleAuto.vehicle_gear_d_360_enabled"
            @click="toggleSetting('vehicle_gear_d_360_enabled')"
          />
        </div>
      </div>
    </FeatureCard>

    <!-- 2. 电动尾门与大灯日夜联动 -->
    <FeatureCard 
      title="2. 电动尾门与大灯日夜联动"
      desc="原厂电动尾门物理串口破译闭环；大灯联动高德日夜模式，进出隧道自动切换。"
    >
      <div class="grid grid-cols-2 gap-4">
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text">原厂电动尾门 (b7)</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">升起开门提醒与闭合锁止播报</div>
          </div>
          <div class="grid grid-cols-2 gap-2.5">
            <MatrixButton 
              title="开门播报"
              :subtitle="store.vehicleAuto.voice_enable_trunk_open ? '已开启' : '已关闭'"
              :active="store.vehicleAuto.voice_enable_trunk_open"
              @click="toggleSetting('voice_enable_trunk_open')"
            />
            <MatrixButton 
              title="关门播报"
              :subtitle="store.vehicleAuto.voice_enable_trunk_close ? '已开启' : '已关闭'"
              :active="store.vehicleAuto.voice_enable_trunk_close"
              @click="toggleSetting('voice_enable_trunk_close')"
            />
          </div>
        </div>

        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text">大灯联动高德日夜模式</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">进出隧道与黑夜自动切换高德防刺眼</div>
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
import { ref, onMounted, onUnmounted } from 'vue';
import { store, bridge, showToast } from '../store';
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';

const doorStatus = ref({
  fl: -1,
  fr: -1,
  rl: -1,
  rr: -1,
  trunk: -1,
  gear_name: '采集中...',
  mode_name: '采集中...',
  speed: 0
});

let statusPollTimer = null;

function fetchVehicleStatus() {
  try {
    const raw = bridge.call('getDoorStatus');
    if (raw) {
      const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
      doorStatus.value = { ...doorStatus.value, ...data };
    }
  } catch (e) {}
}

function toggleSetting(key) {
  const next = !store.vehicleAuto[key];
  store.vehicleAuto[key] = next;
  bridge.call('setVehicleAutomationSetting', key, next);
  showToast('设置已更新: ' + (next ? '已开启' : '已关闭'));
}

onMounted(() => {
  fetchVehicleStatus();
  statusPollTimer = setInterval(fetchVehicleStatus, 800);
});

onUnmounted(() => {
  if (statusPollTimer) clearInterval(statusPollTimer);
});
</script>