<template>
  <div class="flex flex-col space-y-6">
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
      <!-- 任务 2: D 挡起步联动 360 -->
      <PlanCard
        title="2. 前进 D 挡起步联动 360"
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

      <!-- 任务 3: P 挡开门多媒体优雅静音 -->
      <PlanCard
        title="3. 停稳推门多媒体暂停"
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

      <!-- 任务 4: 车速达标智能启播车载音乐 (双列网格 · 内容纵向拉宽填充) -->
      <div
        class="rounded-3xl border-2 border-car-border hover:border-car-border-light bg-car-card p-6 shadow-xl h-full min-h-[260px] flex flex-col space-y-4 transition-all duration-200"
      >
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-3">
            <span class="text-[22px] font-black text-car-text tracking-wide">4. 车速达标智能启播车载音乐</span>
            <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent">起步自启</span>
          </div>
          <div class="flex items-center space-x-3">
            <button
              @click="toggleSetting('vehicle_speed_autoplay_enabled')"
              :class="[
                'h-[54px] px-6 rounded-2xl font-black text-[16px] cursor-pointer transition-all border-2 flex items-center space-x-2',
                store.vehicleAuto.vehicle_speed_autoplay_enabled
                  ? 'bg-car-item border-car-accent text-car-text shadow-md'
                  : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
              ]"
            >
              <span :class="['w-2.5 h-2.5 rounded-full', store.vehicleAuto.vehicle_speed_autoplay_enabled ? 'bg-car-accent' : 'bg-car-sub']"></span>
              <span>{{ store.vehicleAuto.vehicle_speed_autoplay_enabled ? '计划运行中' : '计划已暂停' }}</span>
            </button>
          </div>
        </div>

        <!-- 任务两段流（纵向堆叠拉宽填充，根治「一宽一窄不协调」；shrink-0 防旧内核 WebView66 flex 压缩致步进器穿卡悬浮） -->
        <div class="flex flex-col space-y-3 pt-1 min-w-0 flex-1 shrink-0">
          <!-- 当: 车速控制器 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3 min-w-0 flex-1 shrink-0">
            <div class="flex items-center space-x-3">
              <span class="text-[16px] font-black text-car-text">出 P 挡起步行驶，车速达到设定阈值：</span>
            </div>
            <!-- 纯加减数值步进器 (shrink-0: 车机 WebView66 旧内核 flex 压缩会使按钮穿出卡外悬浮) -->
            <div class="flex items-center justify-between bg-car-card border-2 border-car-border rounded-2xl p-2.5 shrink-0">
              <div class="flex items-center space-x-2">
                <button 
                  @click="adjustAutoplaySpeed(-5)"
                  class="h-[52px] px-4 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[16px] cursor-pointer active:scale-95 transition-all shadow-sm"
                >
                  -5
                </button>
                <button 
                  @click="adjustAutoplaySpeed(-1)"
                  class="h-[52px] px-4 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[16px] cursor-pointer active:scale-95 transition-all shadow-sm"
                >
                  -1
                </button>
              </div>
              <div class="flex items-baseline space-x-1 px-4">
                <span class="text-[32px] font-black text-car-accent tracking-tight font-mono">
                  {{ store.vehicleAuto.vehicle_speed_autoplay_threshold || 20 }}
                </span>
                <span class="text-[15px] text-car-sub font-black">km/h</span>
              </div>
              <div class="flex items-center space-x-2">
                <button 
                  @click="adjustAutoplaySpeed(1)"
                  class="h-[52px] px-4 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[16px] cursor-pointer active:scale-95 transition-all shadow-sm"
                >
                  +1
                </button>
                <button 
                  @click="adjustAutoplaySpeed(5)"
                  class="h-[52px] px-4 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[16px] cursor-pointer active:scale-95 transition-all shadow-sm"
                >
                  +5
                </button>
              </div>
            </div>
          </div>

          <!-- 就: 自动放歌 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3">
            <div class="flex items-center space-x-3">
              <span class="text-[16px] font-black text-car-text">触发主力软件播放（单次行程防抖闭环，等红灯不重复放）：</span>
            </div>
            <!-- 形式与软件选择（两张等宽对称按钮） -->
            <div class="grid grid-cols-2 gap-3 items-stretch">
              <button 
                @click="setAutoplayFullscreen(false)"
                :class="[
                  'w-full h-[52px] rounded-xl font-black text-[15px] cursor-pointer transition-all border-2 whitespace-nowrap',
                  !store.vehicleAuto.vehicle_speed_autoplay_fullscreen
                    ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                    : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
                ]"
              >
                后台静默放歌 (保持高德导航)
              </button>
              <button 
                @click="setAutoplayFullscreen(true)"
                :class="[
                  'w-full h-[52px] rounded-xl font-black text-[15px] cursor-pointer transition-all border-2 whitespace-nowrap',
                  store.vehicleAuto.vehicle_speed_autoplay_fullscreen
                    ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                    : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
                ]"
              >
                前台全屏播放 (沉浸大屏)
              </button>
            </div>
          </div>
        </div>

        <!-- 启播目标音乐应用选择 (支持自动探测、手动强刷扫描、▲/▼ 优先级排序与更多软件选择) -->
        <div class="pt-3 flex flex-col space-y-3 border-t border-car-border/60 min-w-0 flex-1 shrink-0">
          <div class="flex items-center justify-between space-x-4">
            <div class="flex items-center space-x-2 flex-1 min-w-0">
              <span class="text-[15.5px] font-black text-car-text">自启首选主力音乐软件，开机自动探测，点击磁贴即设为首选：</span>
            </div>
            <div class="flex items-center space-x-2.5 shrink-0">
              <button
                @click="refreshDetectedApps"
                class="h-[52px] px-7 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-accent font-black text-[14.5px] cursor-pointer shadow-sm transition-all flex items-center space-x-1.5 whitespace-nowrap shrink-0"
              >

                <span>手动重新扫描</span>
              </button>
              <button
                @click="openSelectModal('speed_autoplay')"
                class="h-[52px] px-7 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[14.5px] cursor-pointer shadow-sm transition-all flex items-center space-x-1.5 whitespace-nowrap shrink-0"
              >

                <span>自定义应用排序 (▲/▼) ➔</span>
              </button>
            </div>
          </div>

          <!-- 常用音乐软件磁贴 (按用户优先级动态排序) -->
          <div class="flex flex-wrap -m-1">
            <button 
              v-for="app in sortedMusicApps" 
              :key="app.pkg"
              @click="setAutoplayApp(app.pkg, app.name)"
              :class="[
                'h-[54px] px-5 rounded-xl text-[15.5px] font-black cursor-pointer transition-all border-2 whitespace-nowrap flex items-center space-x-2 m-1 shadow-sm',
                store.vehicleAuto.vehicle_speed_autoplay_pkg === app.pkg
                  ? 'bg-car-item border-car-accent text-car-accent shadow-md ring-2 ring-car-accent/20'
                  : 'bg-car-card border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'
              ]"
            >
              <span v-if="store.vehicleAuto.vehicle_speed_autoplay_pkg === app.pkg" class="px-2 py-0.5 text-[11px] rounded bg-car-item border border-car-accent text-car-accent font-black mr-1">首选</span>
              <span>{{ app.name }}</span>
            </button>
            <button 
              @click="openSelectModal('speed_autoplay')"
              class="h-[54px] px-5 rounded-xl text-[15.5px] font-black cursor-pointer transition-all border-2 border-car-border bg-car-card hover:border-car-accent text-car-accent whitespace-nowrap flex items-center space-x-2 m-1 shadow-sm"
            >
              
              <span>自选整车已装软件 ➔</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 任务 5: 车速达标自定义动作与唤起应用 (双列网格 · 内容纵向拉宽填充) -->
      <div
        class="rounded-3xl border-2 border-car-border hover:border-car-border-light bg-car-card p-6 shadow-xl h-full min-h-[260px] flex flex-col space-y-4 transition-all duration-200"
      >
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-3">
            <span class="text-[22px] font-black text-car-text tracking-wide">5. 车速达标自定义动作与唤起应用</span>
            <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent">自定动作</span>
          </div>
          <div class="flex items-center space-x-3">
            <button
              @click="toggleSetting('vehicle_speed_custom_action_enabled')"
              :class="[
                'h-[54px] px-6 rounded-2xl font-black text-[16px] cursor-pointer transition-all border-2 flex items-center space-x-2',
                store.vehicleAuto.vehicle_speed_custom_action_enabled
                  ? 'bg-car-item border-car-accent text-car-text shadow-md'
                  : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
              ]"
            >
              <span :class="['w-2.5 h-2.5 rounded-full', store.vehicleAuto.vehicle_speed_custom_action_enabled ? 'bg-car-accent' : 'bg-car-sub']"></span>
              <span>{{ store.vehicleAuto.vehicle_speed_custom_action_enabled ? '计划运行中' : '计划已暂停' }}</span>
            </button>
          </div>
        </div>

        <div class="flex flex-col pt-1 min-w-0 flex-1 shrink-0">
          <!-- 当: 设定速度 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col justify-center space-y-3 min-w-0 flex-1 shrink-0">
            <div class="flex items-center space-x-3">
              <span class="text-[16px] font-black text-car-text">起步加速，当前车速达到设定阈值：</span>
            </div>
            <!-- 纯加减数值步进器 -->
            <div class="flex items-center justify-between bg-car-card border-2 border-car-border rounded-2xl p-2.5 shrink-0">
              <div class="flex items-center space-x-2">
                <button 
                  @click="adjustCustomActionSpeed(-5)"
                  class="h-[52px] px-4 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[16px] cursor-pointer active:scale-95 transition-all shadow-sm"
                >
                  -5
                </button>
                <button 
                  @click="adjustCustomActionSpeed(-1)"
                  class="h-[52px] px-4 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[16px] cursor-pointer active:scale-95 transition-all shadow-sm"
                >
                  -1
                </button>
              </div>
              <div class="flex items-baseline space-x-1 px-4">
                <span class="text-[32px] font-black text-car-accent tracking-tight font-mono">
                  {{ store.vehicleAuto.vehicle_speed_custom_action_threshold || 40 }}
                </span>
                <span class="text-[15px] text-car-sub font-black">km/h</span>
              </div>
              <div class="flex items-center space-x-2">
                <button 
                  @click="adjustCustomActionSpeed(1)"
                  class="h-[52px] px-4 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[16px] cursor-pointer active:scale-95 transition-all shadow-sm"
                >
                  +1
                </button>
                <button 
                  @click="adjustCustomActionSpeed(5)"
                  class="h-[52px] px-4 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[16px] cursor-pointer active:scale-95 transition-all shadow-sm"
                >
                  +5
                </button>
              </div>
            </div>
          </div>

          <!-- 就: 执行动作 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col justify-center space-y-3 min-w-0 flex-1 mt-3 shrink-0">
            <div class="flex items-center space-x-3">
              <span class="text-[16px] font-black text-car-text">自动唤起预选目标（单次行程防抖，回 P 挡重置）：</span>
            </div>
            <!-- 动作选择（两张等宽对称按钮） -->
            <div class="grid grid-cols-2 gap-3 items-stretch">
              <button 
                @click="setCustomActionTarget('pkg:com.autonavi.amapauto')"
                :class="[
                  'w-full h-[68px] rounded-xl font-black text-[19px] cursor-pointer transition-all border-2 whitespace-nowrap',
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
                  'w-full h-[68px] rounded-xl font-black text-[19px] cursor-pointer transition-all border-2 whitespace-nowrap',
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

  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { store, bridge, showToast, openModal } from '../store';
import PlanCard from '../components/PlanCard.vue';
import BaseButton from '../components/BaseButton.vue';
import StatusDot from '../components/StatusDot.vue';





const activeTaskCount = computed(() => {
  let count = 0;
  if (store.vehicleAuto.vehicle_d_gear_360_enabled) count++;
  if (store.vehicleAuto.vehicle_speed_autoplay_enabled) count++;
  if (store.vehicleAuto.vehicle_speed_custom_action_enabled) count++;
  if (store.vehicleAuto.vehicle_door_pause_music_enabled) count++;
  return count;
});


const sortedMusicApps = ref([]);
const customActionAppName = ref('');

function loadCustomActionAppName() {
  customActionAppName.value = localStorage.getItem('vehicle_speed_custom_action_app_name') || '';
}

function getMusicOrder() {
  try {
    const raw = localStorage.getItem('preferred_music_apps_order');
    if (raw) return JSON.parse(raw);
  } catch (e) {}
  return [];
}

function loadSortedMusicApps() {
  let list = [];
  try {
    const raw = bridge.call('getInstalledMusicAppsJson');
    if (raw) {
      const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
      list = parsed.map(item => ({
        name: item.name || item.appName,
        pkg: item.pkg || item.packageName
      }));
    }
  } catch (e) {}

  if (!list.some(a => a.pkg === 'com.android.bluetooth')) {
    list.push({ name: '手机蓝牙', pkg: 'com.android.bluetooth' });
  }

  if (list.length === 1 && list[0].pkg === 'com.android.bluetooth') {
    list.unshift({ name: '汽水音乐', pkg: 'com.luna.music' });
    list.push({ name: 'QQ音乐', pkg: 'com.tencent.qqmusiccar' });
    list.push({ name: '酷我音乐', pkg: 'cn.kuwo.kwmusiccar' });
  }

  const order = getMusicOrder();
  if (order.length > 0) {
    list.sort((a, b) => {
      let ia = order.indexOf(a.pkg);
      let ib = order.indexOf(b.pkg);
      if (ia === -1) ia = 999;
      if (ib === -1) ib = 999;
      return ia - ib;
    });
  }

  const currentPkg = store.vehicleAuto.vehicle_speed_autoplay_pkg;
  const currentName = localStorage.getItem('vehicle_speed_autoplay_app_name');
  if (currentPkg && !list.some(a => a.pkg === currentPkg)) {
    list.unshift({ name: currentName || '自选应用', pkg: currentPkg });
  }

  sortedMusicApps.value = list;

  if (!store.vehicleAuto.vehicle_speed_autoplay_pkg && sortedMusicApps.value.length > 0) {
    store.vehicleAuto.vehicle_speed_autoplay_pkg = sortedMusicApps.value[0].pkg;
  }
}

function refreshDetectedApps() {
  try {
    bridge.call('refreshInstalledApps');
  } catch (e) {}
  loadSortedMusicApps();
  showToast('已重新扫描整车已安装音乐软件并刷新排序');
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
  localStorage.setItem('vehicle_speed_autoplay_app_name', name);
  let order = getMusicOrder();
  order = order.filter(p => p !== pkg);
  order.unshift(pkg);
  localStorage.setItem('preferred_music_apps_order', JSON.stringify(order));
  loadSortedMusicApps();
  showToast(`自启首选主力已设为: ${name}`);
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
  loadSortedMusicApps();
  loadCustomActionAppName();
  window.addEventListener('music-order-updated', loadSortedMusicApps);
  window.addEventListener('custom-action-target-updated', loadCustomActionAppName);
});

onUnmounted(() => {
  window.removeEventListener('music-order-updated', loadSortedMusicApps);
  window.removeEventListener('custom-action-target-updated', loadCustomActionAppName);
});
</script>