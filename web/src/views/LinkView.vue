<template>
  <div class="flex flex-col space-y-5">
    <!-- 顶部计划概览与操作栏 (对齐方控 106px 车规标准) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-5 min-h-[106px] shadow-xl flex items-center justify-between transition-all">
      <div class="w-full flex flex-col space-y-1.5">
        <div class="flex items-center space-x-3">
          <StatusDot size="lg" color="accent" :glow-px="10" class="shrink-0" />
          <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">车身智能联动计划</span>
          <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-item border-car-accent/40 text-car-accent inline-flex items-center shrink-0 shadow-sm">
            {{ activeTaskCount }} / 4 项运行中
          </span>
        </div>
        <div class="text-[14.5px] text-car-sub font-bold leading-normal">
          基于车载 CAN 总线与 MCU 底层传感器事件驱动。四大联动计划固定常驻，支持独立开关与加减微调触发阈值。
        </div>
      </div>
    </div>

    <!-- 计划任务流列表 (车规对称双列网格 · 告别单列8层面条堆叠 · 1920宽屏黄金排布) -->
    <div class="grid grid-cols-2 gap-5">
      <!-- 任务 1: D 挡起步联动 360 -->
      <PlanCard
        title="1. 前进 D 挡起步联动 360"
        tag="单次跃变"
        help-text="gear_d_360"
        flow-sub="从 P 挡或 R 挡切入前进 D 挡起步 (单次跃变锁)"
        flow-main="唤醒 360 扫除起步盲区；车速超 15 km/h 自动还原"
        @help="showHelp('gear_d_360')"
      >
        <template #footer>
          <BaseButton variant="planToggle" :active="store.vehicleAuto.vehicle_d_gear_360_enabled" @click="toggleSetting('vehicle_d_gear_360_enabled')">
            <StatusDot size="sm" :color="store.vehicleAuto.vehicle_d_gear_360_enabled ? 'accent' : 'sub'" :glow-px="6" />
            <span>{{ store.vehicleAuto.vehicle_d_gear_360_enabled ? 'D 挡起步 360 运行中' : 'D 挡起步 360 已暂停' }}</span>
          </BaseButton>
        </template>
      </PlanCard>

      <!-- 任务 2: P 挡开门多媒体优雅静音 -->
      <PlanCard
        title="2. 停稳推门多媒体暂停"
        tag="下车静音"
        help-text="door_pause"
        flow-sub="挂入驻车 P 挡且推开前排车门（准备下车）"
        flow-main="自动向音乐播放器发送暂停指令，下车优雅安静"
        @help="showHelp('door_pause')"
      >
        <template #footer>
          <BaseButton variant="planToggle" :active="store.vehicleAuto.vehicle_door_pause_music_enabled" @click="toggleSetting('vehicle_door_pause_music_enabled')">
            <StatusDot size="sm" :color="store.vehicleAuto.vehicle_door_pause_music_enabled ? 'accent' : 'sub'" :glow-px="6" />
            <span>{{ store.vehicleAuto.vehicle_door_pause_music_enabled ? '推门暂停音乐运行中' : '推门暂停音乐已暂停' }}</span>
          </BaseButton>
        </template>
      </PlanCard>

      <!-- 任务 3: 车速达标智能启播车载音乐 (紧凑车规卡片) -->
      <div
        class="rounded-3xl border-2 border-car-border hover:border-car-border-light bg-car-card p-5 shadow-xl h-full flex flex-col space-y-3.5 transition-all duration-200"
      >
        <div class="flex items-center justify-between shrink-0">
          <div class="flex items-center space-x-2.5">
            <span class="text-[20px] font-black text-car-text tracking-wide">3. 车速达标智能启播音乐</span>
            <span class="px-2.5 py-0.5 text-[12.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent shrink-0">起步自启</span>
            <HelpDot size="sm" @click="showHelp('speed_music')" />
          </div>
          <div class="flex items-center space-x-2 shrink-0">
            <button
              @click="toggleSetting('vehicle_speed_autoplay_enabled')"
              :class="[
                'h-[50px] px-4 rounded-xl font-black text-[15px] cursor-pointer transition-all border-2 flex items-center space-x-2 shrink-0',
                store.vehicleAuto.vehicle_speed_autoplay_enabled
                  ? 'bg-car-item border-car-accent text-car-text shadow-md'
                  : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
              ]"
            >
              <span :class="['w-2.5 h-2.5 rounded-full', store.vehicleAuto.vehicle_speed_autoplay_enabled ? 'bg-car-accent' : 'bg-car-sub']"></span>
              <span>{{ store.vehicleAuto.vehicle_speed_autoplay_enabled ? '运行中' : '已暂停' }}</span>
            </button>
          </div>
        </div>

        <!-- 紧凑车速步进器 (一体化设计，不再与提示语层叠) -->
        <div class="flex items-center justify-between bg-car-item border-2 border-car-border rounded-2xl p-2 shrink-0">
          <div class="flex items-center space-x-1.5">
            <button 
              @click="adjustAutoplaySpeed(-5)"
              class="h-[50px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
            >
              -5
            </button>
            <button 
              @click="adjustAutoplaySpeed(-1)"
              class="h-[50px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
            >
              -1
            </button>
          </div>
          <div class="flex items-baseline space-x-1 px-3">
            <span class="text-[13.5px] font-bold text-car-sub mr-1">触发时速</span>
            <span class="text-[28px] font-black text-car-accent tracking-tight font-mono">
              {{ store.vehicleAuto.vehicle_speed_autoplay_threshold || 20 }}
            </span>
            <span class="text-[14px] text-car-sub font-black">km/h</span>
          </div>
          <div class="flex items-center space-x-1.5">
            <button 
              @click="adjustAutoplaySpeed(1)"
              class="h-[50px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
            >
              +1
            </button>
            <button 
              @click="adjustAutoplaySpeed(5)"
              class="h-[50px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
            >
              +5
            </button>
          </div>
        </div>

        <!-- 播放模式切换 (紧凑对称双按钮) -->
        <div class="grid grid-cols-2 gap-2.5 items-stretch shrink-0">
          <button 
            @click="setAutoplayFullscreen(false)"
            :class="[
              'w-full h-[50px] rounded-xl font-black text-[14.5px] cursor-pointer transition-all border-2 whitespace-nowrap',
              !store.vehicleAuto.vehicle_speed_autoplay_fullscreen
                ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            后台静默放歌 (保持导航)
          </button>
          <button 
            @click="setAutoplayFullscreen(true)"
            :class="[
              'w-full h-[50px] rounded-xl font-black text-[14.5px] cursor-pointer transition-all border-2 whitespace-nowrap',
              store.vehicleAuto.vehicle_speed_autoplay_fullscreen
                ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            前台全屏播放 (沉浸大屏)
          </button>
        </div>

        <!-- 首选音乐应用矩阵 (紧凑网格) -->
        <div class="pt-4 flex flex-col space-y-2 border-t border-car-border/60 min-w-0 flex-1 shrink-0">
          <div class="text-[13.5px] font-bold text-car-sub">首选音乐软件：</div>
          <div class="grid grid-cols-2 gap-2.5 items-stretch">
            <!-- QQ音乐 (未安装则动态隐藏) -->
            <button 
              v-if="hasQQMusic"
              @click="setAutoplayApp('com.tencent.qqmusiccar', 'QQ音乐')"
              :class="[
                'h-[50px] px-3 rounded-xl text-[14.5px] font-black cursor-pointer transition-all border-2 whitespace-nowrap flex items-center justify-center space-x-1.5 shadow-sm',
                store.vehicleAuto.vehicle_speed_autoplay_pkg === 'com.tencent.qqmusiccar'
                  ? 'bg-car-item border-car-accent text-car-accent shadow-md ring-2 ring-car-accent/20'
                  : 'bg-car-card border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'
              ]"
            >
              <span v-if="store.vehicleAuto.vehicle_speed_autoplay_pkg === 'com.tencent.qqmusiccar'" class="px-1.5 py-0.5 text-[10.5px] rounded bg-car-item border border-car-accent text-car-accent font-black">首选</span>
              <span>QQ音乐</span>
            </button>

            <!-- 网易云音乐 (未安装则动态隐藏) -->
            <button 
              v-if="hasNeteaseMusic"
              @click="setAutoplayApp('com.netease.cloudmusiccar', '网易云音乐')"
              :class="[
                'h-[50px] px-3 rounded-xl text-[14.5px] font-black cursor-pointer transition-all border-2 whitespace-nowrap flex items-center justify-center space-x-1.5 shadow-sm',
                store.vehicleAuto.vehicle_speed_autoplay_pkg === 'com.netease.cloudmusiccar'
                  ? 'bg-car-item border-car-accent text-car-accent shadow-md ring-2 ring-car-accent/20'
                  : 'bg-car-card border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'
              ]"
            >
              <span v-if="store.vehicleAuto.vehicle_speed_autoplay_pkg === 'com.netease.cloudmusiccar'" class="px-1.5 py-0.5 text-[10.5px] rounded bg-car-item border border-car-accent text-car-accent font-black">首选</span>
              <span>网易云音乐</span>
            </button>

            <!-- 手机蓝牙 (始终常驻) -->
            <button 
              @click="setAutoplayApp('com.android.bluetooth', '手机蓝牙')"
              :class="[
                'h-[50px] px-3 rounded-xl text-[14.5px] font-black cursor-pointer transition-all border-2 whitespace-nowrap flex items-center justify-center space-x-1.5 shadow-sm',
                store.vehicleAuto.vehicle_speed_autoplay_pkg === 'com.android.bluetooth'
                  ? 'bg-car-item border-car-accent text-car-accent shadow-md ring-2 ring-car-accent/20'
                  : 'bg-car-card border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'
              ]"
            >
              <span v-if="store.vehicleAuto.vehicle_speed_autoplay_pkg === 'com.android.bluetooth'" class="px-1.5 py-0.5 text-[10.5px] rounded bg-car-item border border-car-accent text-car-accent font-black">首选</span>
              <span>手机蓝牙</span>
            </button>

            <!-- 自选车机已装软件 (自选后显示名称并高亮) -->
            <button 
              @click="openSelectModal('speed_autoplay')"
              :class="[
                'h-[50px] px-3 rounded-xl text-[14.5px] font-black cursor-pointer transition-all border-2 whitespace-nowrap flex items-center justify-center space-x-1.5 shadow-sm',
                isCustomAutoplaySelected
                  ? 'bg-car-item border-car-accent text-car-accent shadow-md ring-2 ring-car-accent/20'
                  : 'bg-car-card border-car-border text-car-sub hover:text-car-text hover:border-car-accent'
              ]"
            >
              <span v-if="isCustomAutoplaySelected" class="px-1.5 py-0.5 text-[10.5px] rounded bg-car-item border border-car-accent text-car-accent font-black">自选</span>
              <span class="truncate">{{ customAutoplayAppName || '自选应用 ➔' }}</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 任务 4: 车速达标自定义动作与唤起应用 (紧凑车规卡片，高度对齐左侧) -->
      <div
        class="rounded-3xl border-2 border-car-border hover:border-car-border-light bg-car-card p-5 shadow-xl h-full flex flex-col space-y-3.5 transition-all duration-200"
      >
        <div class="flex items-center justify-between shrink-0">
          <div class="flex items-center space-x-2.5">
            <span class="text-[20px] font-black text-car-text tracking-wide">4. 车速达标自定义动作</span>
            <span class="px-2.5 py-0.5 text-[12.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent shrink-0">自定动作</span>
            <HelpDot size="sm" @click="showHelp('speed_custom')" />
          </div>
          <div class="flex items-center space-x-2 shrink-0">
            <button
              @click="toggleSetting('vehicle_speed_custom_action_enabled')"
              :class="[
                'h-[50px] px-4 rounded-xl font-black text-[15px] cursor-pointer transition-all border-2 flex items-center space-x-2 shrink-0',
                store.vehicleAuto.vehicle_speed_custom_action_enabled
                  ? 'bg-car-item border-car-accent text-car-text shadow-md'
                  : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
              ]"
            >
              <span :class="['w-2.5 h-2.5 rounded-full', store.vehicleAuto.vehicle_speed_custom_action_enabled ? 'bg-car-accent' : 'bg-car-sub']"></span>
              <span>{{ store.vehicleAuto.vehicle_speed_custom_action_enabled ? '运行中' : '已暂停' }}</span>
            </button>
          </div>
        </div>

        <!-- 紧凑车速步进器 -->
        <div class="flex items-center justify-between bg-car-item border-2 border-car-border rounded-2xl p-2 shrink-0">
          <div class="flex items-center space-x-1.5">
            <button 
              @click="adjustCustomActionSpeed(-5)"
              class="h-[50px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
            >
              -5
            </button>
            <button 
              @click="adjustCustomActionSpeed(-1)"
              class="h-[50px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
            >
              -1
            </button>
          </div>
          <div class="flex items-baseline space-x-1 px-3">
            <span class="text-[13.5px] font-bold text-car-sub mr-1">触发时速</span>
            <span class="text-[28px] font-black text-car-accent tracking-tight font-mono">
              {{ store.vehicleAuto.vehicle_speed_custom_action_threshold || 40 }}
            </span>
            <span class="text-[14px] text-car-sub font-black">km/h</span>
          </div>
          <div class="flex items-center space-x-1.5">
            <button 
              @click="adjustCustomActionSpeed(1)"
              class="h-[50px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
            >
              +1
            </button>
            <button 
              @click="adjustCustomActionSpeed(5)"
              class="h-[50px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
            >
              +5
            </button>
          </div>
        </div>

        <!-- 动作唤起目标选择 (紧凑对称双按钮，高度与左侧对称对齐) -->
        <div class="pt-4 flex flex-col space-y-2 border-t border-car-border/60 min-w-0 flex-1 shrink-0">
          <div class="text-[13.5px] font-bold text-car-sub">达标唤起目标应用：</div>
          <div class="grid grid-cols-2 gap-2.5 items-stretch">
            <button 
              @click="setCustomActionTarget('pkg:com.autonavi.amapauto')"
              :class="[
                'w-full h-[50px] rounded-xl font-black text-[15px] cursor-pointer transition-all border-2 whitespace-nowrap',
                store.vehicleAuto.vehicle_speed_custom_action_target === 'pkg:com.autonavi.amapauto'
                  ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                  : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
              ]"
            >
              高德车机地图
            </button>
            <button 
              @click="openSelectModal('speed_custom_action')"
              :class="[
                'w-full h-[50px] rounded-xl font-black text-[15px] cursor-pointer transition-all border-2 whitespace-nowrap truncate px-3',
                store.vehicleAuto.vehicle_speed_custom_action_target?.startsWith('pkg:') && store.vehicleAuto.vehicle_speed_custom_action_target !== 'pkg:com.autonavi.amapauto'
                  ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                  : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
              ]"
            >
              {{ customActionAppName ? customActionAppName : '自选应用 ➔' }}
            </button>
          </div>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { store, bridge, showToast, openModal } from '../store';
import PlanCard from '../components/PlanCard.vue';
import BaseButton from '../components/BaseButton.vue';
import StatusDot from '../components/StatusDot.vue';
import HelpDot from '../components/HelpDot.vue';

const activeTaskCount = computed(() => {
  let count = 0;
  if (store.vehicleAuto.vehicle_d_gear_360_enabled) count++;
  if (store.vehicleAuto.vehicle_speed_autoplay_enabled) count++;
  if (store.vehicleAuto.vehicle_speed_custom_action_enabled) count++;
  if (store.vehicleAuto.vehicle_door_pause_music_enabled) count++;
  return count;
});

const hasQQMusic = ref(false);
const hasNeteaseMusic = ref(false);
const customAutoplayAppName = ref('');
const customActionAppName = ref('');

const isCustomAutoplaySelected = computed(() => {
  const currentPkg = store.vehicleAuto.vehicle_speed_autoplay_pkg;
  if (!currentPkg) return false;
  return currentPkg !== 'com.tencent.qqmusiccar' && 
         currentPkg !== 'com.netease.cloudmusiccar' && 
         currentPkg !== 'com.android.bluetooth';
});

function loadCustomActionAppName() {
  customActionAppName.value = localStorage.getItem('vehicle_speed_custom_action_app_name') || '';
}

function loadCustomAutoplayAppName() {
  const currentPkg = store.vehicleAuto.vehicle_speed_autoplay_pkg;
  if (currentPkg && isCustomAutoplaySelected.value) {
    const savedName = localStorage.getItem('vehicle_speed_autoplay_app_name');
    customAutoplayAppName.value = savedName ? `自选: ${savedName}` : '自选应用 ➔';
  } else {
    customAutoplayAppName.value = '';
  }
}

function checkInstalledMusicApps() {
  try {
    const raw = bridge.call('getInstalledMusicAppsJson');
    if (raw) {
      const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
      const pkgs = parsed.map(item => item.pkg || item.packageName);
      hasQQMusic.value = pkgs.includes('com.tencent.qqmusiccar');
      hasNeteaseMusic.value = pkgs.includes('com.netease.cloudmusiccar');
    }
  } catch (e) {
    // 降级兜底：默认置 true 供前端调试/兜底
    hasQQMusic.value = true;
    hasNeteaseMusic.value = true;
  }

  // 默认选择逻辑：若检测到安装了 QQ 音乐，默认置为 QQ 音乐且计划任务默认为运行
  if (hasQQMusic.value) {
    if (!store.vehicleAuto.vehicle_speed_autoplay_pkg) {
      store.vehicleAuto.vehicle_speed_autoplay_pkg = 'com.tencent.qqmusiccar';
    }
    if (store.vehicleAuto.vehicle_speed_autoplay_enabled === undefined || store.vehicleAuto.vehicle_speed_autoplay_enabled === null) {
      store.vehicleAuto.vehicle_speed_autoplay_enabled = true;
    }
  } else {
    if (!store.vehicleAuto.vehicle_speed_autoplay_pkg) {
      store.vehicleAuto.vehicle_speed_autoplay_pkg = 'com.android.bluetooth';
    }
  }

  loadCustomAutoplayAppName();
}

function toggleSetting(key) {
  const next = !store.vehicleAuto[key];
  store.vehicleAuto[key] = next;
  bridge.call('setVehicleAutomationSetting', key, next);
  
  // 双向兼容映射：保证 Java 后端能百分之百接收对应真实 key
  const aliasMap = {
    vehicle_d_gear_360_enabled: 'vehicle_gear_d_360_enabled',
    vehicle_gear_d_360_enabled: 'vehicle_d_gear_360_enabled'
  };
  if (aliasMap[key]) {
    store.vehicleAuto[aliasMap[key]] = next;
    bridge.call('setVehicleAutomationSetting', aliasMap[key], next);
  }
  showToast('计划状态已更新: ' + (next ? '已开启执行' : '已暂停'));
}

function setAutoplaySpeed(speed) {
  store.vehicleAuto.vehicle_speed_autoplay_threshold = speed;
  bridge.call('setVehicleAutomationIntSetting', 'vehicle_speed_autoplay_threshold', speed);
  showToast(`自启车速阈值已设为: ${speed} km/h`);
}

function adjustAutoplaySpeed(delta) {
  let current = store.vehicleAuto.vehicle_speed_autoplay_threshold || 20;
  let next = Math.max(1, Math.min(120, current + delta));
  setAutoplaySpeed(next);
}

function setCustomActionSpeed(speed) {
  store.vehicleAuto.vehicle_speed_custom_action_threshold = speed;
  bridge.call('setVehicleAutomationIntSetting', 'vehicle_speed_custom_action_threshold', speed);
  showToast(`联动触发时速已设为: ${speed} km/h`);
}

function adjustCustomActionSpeed(delta) {
  let current = store.vehicleAuto.vehicle_speed_custom_action_threshold || 40;
  let next = Math.max(1, Math.min(120, current + delta));
  setCustomActionSpeed(next);
}

function setCustomActionTarget(target) {
  store.vehicleAuto.vehicle_speed_custom_action_target = target;
  bridge.call('setWheelControlStringSetting', 'vehicle_speed_custom_action_target', target);
  if (target === 'pkg:com.autonavi.amapauto') {
    showToast('车速达标联动目标已设为: 打开高德车机地图');
  }
}

function setAutoplayApp(pkg, name) {
  store.vehicleAuto.vehicle_speed_autoplay_pkg = pkg;
  bridge.call('setWheelControlStringSetting', 'vehicle_speed_autoplay_pkg', pkg);
  if (name) {
    localStorage.setItem('vehicle_speed_autoplay_app_name', name);
  }
  loadCustomAutoplayAppName();
  showToast(`自启首选主力已设为: ${name || pkg}`);
}

function setAutoplayFullscreen(fullscreen) {
  store.vehicleAuto.vehicle_speed_autoplay_fullscreen = fullscreen;
  bridge.call('setVehicleAutomationSetting', 'vehicle_speed_autoplay_fullscreen', fullscreen);
  showToast(fullscreen ? '已设为前台全屏大屏打开' : '已设为后台静默放歌 (保持高德导航)');
}

function openSelectModal(target) {
  openModal('appSelect', { keyTarget: target });
}

function showHelp(key) {
  const helpData = {
    gear_d_360: {
      title: '【功能指南】前进 D 挡起步 360 全景',
      desc: '1. 单次跃变锁：从 P 挡或 R 挡切入前进 D 挡瞬间自动唤醒 360 环视四周；触发一次即锁定，手动退出绝不重复弹出。\n\n2. 自动退出：起步车速超 15km/h 自动退出全景，保持导航视线。',
      tip: '切出 D 挡离开后才会重新武装待命。'
    },
    door_pause: {
      title: '【功能指南】P 挡开门音乐暂停',
      desc: '挂入驻车 P 挡推开前门，毫秒级自动向播放器发送暂停指令，下车从容安静。（车门安全播报由【车载语音】专职管理，零冲突零重复）',
      tip: '仅在挂 P 挡停稳推门生效，行车中推门不误触。'
    },
    speed_music: {
      title: '【功能指南】车速达标智能启播音乐',
      desc: '1. 智能起步判断：挂入 D 挡起步行驶，实时车速突破设定阈值瞬间自动触发首选主力音乐软件播放。\n\n2. 单次行程防抖：单次行驶过程中仅触发一次，中途红绿灯停车或减速再次起步绝不重复触发。\n\n3. 自动待命重置：车辆停稳重新挂入 P 挡后，自动重置状态，等待下一次起步。',
      tip: '支持后台静默播放（不抢导航视线）或前台全屏大屏沉浸播放。'
    },
    speed_custom: {
      title: '【功能指南】车速达标自定义动作',
      desc: '1. 车速阈值唤起：行驶车速达到设定阈值时，自动唤醒高德车机地图或自选软件，无需行车中分心操作屏幕。\n\n2. 行程防抖机制：单次行驶中仅触发一次，避免频繁弹窗；挂入 P 挡停稳后自动重置待命。',
      tip: '适合将阈值设为 40~50 km/h，出地库上主路巡航时自动切回大屏导航。'
    }
  };
  const item = helpData[key];
  if (item) {
    openModal('confirm', {
      title: item.title,
      desc: item.desc,
      tip: item.tip,
      showCancel: false,
      confirmText: '我知道了'
    });
  }
}

onMounted(() => {
  if (store.vehicleAuto.vehicle_gear_d_360_enabled !== undefined) {
    store.vehicleAuto.vehicle_d_gear_360_enabled = store.vehicleAuto.vehicle_gear_d_360_enabled;
  }
  checkInstalledMusicApps();
  loadCustomActionAppName();
  window.addEventListener('music-order-updated', checkInstalledMusicApps);
  window.addEventListener('custom-action-target-updated', loadCustomActionAppName);
});

onUnmounted(() => {
  window.removeEventListener('music-order-updated', checkInstalledMusicApps);
  window.removeEventListener('custom-action-target-updated', loadCustomActionAppName);
});
</script>
