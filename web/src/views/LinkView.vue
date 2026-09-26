<template>
  <div class="flex flex-col space-y-4">
    <!-- 联动功能列表 -->
    <div class="grid grid-cols-2 gap-4">
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
            <span>{{ store.vehicleAuto.vehicle_d_gear_360_enabled ? 'D 挡起步 360 已开启' : 'D 挡起步 360 已关闭' }}</span>
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
            <span>{{ store.vehicleAuto.vehicle_door_pause_music_enabled ? '推门暂停音乐已开启' : '推门暂停音乐已关闭' }}</span>
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
              <span>{{ store.vehicleAuto.vehicle_speed_autoplay_enabled ? '已开启' : '已关闭' }}</span>
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
            后台静默播放
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
            前台全屏播放
          </button>
        </div>

        <!-- 统一整车默认音源提示条 (详细配置收敛至【车载音频-多媒体】卡片) -->
        <div class="pt-4 flex flex-col space-y-2 border-t border-car-border/60 min-w-0 flex-1 shrink-0">
          <div class="flex items-center justify-between">
            <span class="text-[13.5px] font-bold text-car-sub">整车默认音源：</span>
            <span class="text-[14px] font-black text-car-accent truncate">{{ currentPrimaryMusicName }}</span>
          </div>
          <div class="p-3 rounded-xl bg-car-item border border-car-border/60 flex items-center justify-between shadow-sm">
            <span class="text-[12.5px] text-car-sub font-bold">音源列表与优先级回退已由【车载音频】集中管控</span>
            <span class="text-[12.5px] text-car-accent font-black">统一中枢 ➔</span>
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
              <span>{{ store.vehicleAuto.vehicle_speed_custom_action_enabled ? '已开启' : '已关闭' }}</span>
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

    <!-- 5. 转向灯开启联动 360 - 核心车身联动 (左右分栏车规黄金磁贴，展开自适应充盈大磁贴) -->
    <div
      class="bg-car-card border-2 border-car-border rounded-2xl px-6 shadow-xl flex flex-col justify-between transition-all"
      :class="store.vehicleAuto.vehicle_turn_signal_360_enabled ? 'py-8 min-h-[240px] space-y-6' : 'py-7 min-h-[148px] space-y-0'"
    >
      <div class="flex items-center justify-between">
        <div class="w-[62%] max-w-[62%] flex flex-col space-y-2 shrink-0">
          <div class="flex items-center space-x-3">
            <StatusDot size="lg" :color="store.vehicleAuto.vehicle_turn_signal_360_enabled ? 'ok' : 'off'" :glow-px="10" class="shadow-md" />
            <span class="text-[22px] font-black text-car-text tracking-wide whitespace-nowrap">转向灯开启联动 360 全景</span>
            <span class="px-3 py-1 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-text inline-flex items-center shrink-0 shadow-sm">
              <StatusDot class="mr-2" size="sm" :color="store.vehicleAuto.vehicle_turn_signal_360_enabled ? 'ok' : 'off'" />
              {{ store.vehicleAuto.vehicle_turn_signal_360_enabled ? '转向灯联动已启用' : '转向灯联动已关闭 (不干涉)' }}
            </span>
            <HelpDot size="sm" @click="showHelp('turn_signal_360')" />
          </div>
          <div class="text-[15px] text-car-sub font-bold leading-relaxed">
            打左/右转向灯时自动唤起 360 全景环视，消除变道与侧向盲区（倒车 R 挡避让原厂倒车影像）。
          </div>
        </div>

        <div class="shrink-0 w-[240px]">
          <BaseButton
            variant="master"
            :active="store.vehicleAuto.vehicle_turn_signal_360_enabled"
            @click="toggleTurnSignal360"
          >
            <span class="text-[19px] font-black text-car-text tracking-wide whitespace-nowrap">
              {{ store.vehicleAuto.vehicle_turn_signal_360_enabled ? '转向灯联动已开启' : '转向灯联动已关闭' }}
            </span>
            <span :class="['text-[13px] font-bold mt-1 whitespace-nowrap', store.vehicleAuto.vehicle_turn_signal_360_enabled ? 'text-car-accent' : 'text-car-sub']">
              {{ store.vehicleAuto.vehicle_turn_signal_360_enabled ? '点击关闭转向灯联动' : '点击开启转向灯联动' }}
            </span>
          </BaseButton>
        </div>
      </div>

      <!-- 转向灯复位自动退出 360 子配置 (联动开启时展开，充裕纵深呼吸空间) -->
      <div v-if="store.vehicleAuto.vehicle_turn_signal_360_enabled" class="pt-6 border-t border-car-border/60 flex items-center justify-between">
        <div class="flex items-center space-x-3">
          <StatusDot size="sm" :color="store.vehicleAuto.vehicle_turn_signal_360_auto_exit !== false ? 'ok' : 'off'" />
          <div>
            <div class="text-[17px] font-black text-car-text tracking-wide">转向灯回正复位后自动退出 360</div>
            <div class="text-[13.5px] text-car-sub font-bold mt-1">
              {{ store.vehicleAuto.vehicle_turn_signal_360_auto_exit !== false ? '开启：转向灯打回关闭后自动退出 360 画面还原大屏' : '关闭：转向灯回正后 360 持续保持在屏幕上，不自动退出' }}
            </div>
          </div>
        </div>
        <div class="shrink-0 w-[170px]">
          <BaseButton
            variant="dual"
            :active="store.vehicleAuto.vehicle_turn_signal_360_auto_exit !== false"
            @click="toggleTurnSignal360AutoExit"
          >
            <span class="text-[17px] font-black text-car-text tracking-wide whitespace-nowrap">
              {{ store.vehicleAuto.vehicle_turn_signal_360_auto_exit !== false ? '已开启' : '已关闭' }}
            </span>
            <span :class="['text-[12px] font-bold mt-0.5 whitespace-nowrap', store.vehicleAuto.vehicle_turn_signal_360_auto_exit !== false ? 'text-car-accent' : 'text-car-sub']">
              {{ store.vehicleAuto.vehicle_turn_signal_360_auto_exit !== false ? '回正自动退出' : '回正持续保持' }}
            </span>
          </BaseButton>
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

const hasQQMusic = ref(false);
const hasNeteaseMusic = ref(false);
const customAutoplayAppName = ref('');
const customActionAppName = ref('');

const toggleTurnSignal360 = () => {
  const nextVal = !store.vehicleAuto.vehicle_turn_signal_360_enabled;
  store.vehicleAuto.vehicle_turn_signal_360_enabled = nextVal;
  bridge.call('setVehicleAutomationSetting', 'vehicle_turn_signal_360_enabled', nextVal);
  showToast(nextVal ? '转向灯联动 360 已开启' : '转向灯联动 360 已关闭');
};

const toggleTurnSignal360AutoExit = () => {
  const current = store.vehicleAuto.vehicle_turn_signal_360_auto_exit !== false;
  const nextVal = !current;
  store.vehicleAuto.vehicle_turn_signal_360_auto_exit = nextVal;
  bridge.call('setVehicleAutomationSetting', 'vehicle_turn_signal_360_auto_exit', nextVal);
  showToast(nextVal ? '转向灯回正自动退出已开启' : '已关闭自动退出，转向灯回正后保持 360');
};

const isCustomAutoplaySelected = computed(() => {
  const currentPkg = store.vehicleAuto.vehicle_speed_autoplay_pkg;
  if (!currentPkg) return false;
  return currentPkg !== 'com.tencent.qqmusiccar' && 
         currentPkg !== 'com.netease.cloudmusiccar' && 
         currentPkg !== 'com.android.bluetooth';
});

const currentPrimaryMusicName = computed(() => {
  const pkg = store.vehicleAuto.vehicle_speed_autoplay_pkg;
  if (!pkg || pkg === 'com.android.bluetooth') return '手机蓝牙 (无线推流)';
  if (pkg === 'com.tencent.qqmusiccar') return 'QQ音乐车机版';
  if (pkg === 'com.netease.cloudmusiccar') return '网易云音乐车机版';
  const saved = localStorage.getItem('vehicle_speed_autoplay_app_name');
  return saved ? `自选应用: ${saved}` : (pkg || '未指定音源');
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

  // 默认选择逻辑：若检测到安装了 QQ 音乐，默认置为 QQ 音乐并默认开启自动播放
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
  showToast('联动设置已更新: ' + (next ? '已开启' : '已关闭'));
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
    },
    turn_signal_360: {
      title: '【功能指南】转向灯开启联动 360',
      desc: '1. 盲区辅助：打左或右转向灯时，自动唤醒 360 全景环视影像，消除后视镜与侧向盲区。\n\n2. 自动退出：转向灯拨杆回正关闭后，360 全景自动退出并还原原屏幕界面。\n\n3. 倒车避让：倒车 R 挡状态下不打扰原厂倒车影像。',
      tip: '适合原厂未配备转向灯联动 360 的车型开启。'
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
