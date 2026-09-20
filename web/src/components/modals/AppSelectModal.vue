<template>
  <ModalWrapper
    :show="!!store.modals.appSelect"
    :title="`${modalTitle}`"
    badge="应用选择"
    maxWidthClass="max-w-[760px]"
    @close="closeModal('appSelect')"
  >
    <div class="flex flex-col space-y-4">
      <div class="text-[15.5px] font-bold text-car-sub leading-relaxed">
        请选择车机中已安装的应用。选定后，方向盘按下该按键将直接唤醒并打开该第三方应用：
      </div>

      <!-- 快捷搜索框与手动刷新 -->
      <div class="flex items-center space-x-2.5">
        <div class="relative flex-1">
          <input
            type="text"
            v-model="searchQuery"
            placeholder="搜索应用名称或包名..."
            class="w-full h-[52px] px-4 rounded-xl bg-car-item border border-car-border text-car-text placeholder-car-sub font-bold text-[16px] outline-none focus:border-car-accent transition-all"
          />
        </div>
        <button
          v-if="keyTarget === 'speed_autoplay'"
          @click="toggleShowAllApps"
          class="h-[52px] px-4 rounded-xl bg-car-card border border-car-border hover:border-car-accent text-car-accent font-bold text-[14.5px] flex items-center space-x-1.5 whitespace-nowrap shadow-sm cursor-pointer"
        >
          <span>{{ showAllApps ? '仅看音乐分类' : '展开整车所有App' }}</span>
        </button>
        <button
          @click="rescanApps"
          class="h-[52px] px-4 rounded-xl bg-car-card border border-car-border hover:border-car-accent text-car-text font-bold text-[14.5px] flex items-center space-x-1.5 whitespace-nowrap shadow-sm cursor-pointer"
        >
          <span>重新扫描已装App</span>
        </button>
      </div>

      <!-- 分类 Tab：用户已安装(默认) / 系统应用 / 全部（精选音乐/导航清单下自动隐藏） -->
      <div v-if="showFilterTabs" class="grid grid-cols-3 gap-2">
        <button
          v-for="tab in appFilterTabs"
          :key="tab.key"
          @click="appFilter = tab.key"
          :class="[
            'h-[52px] rounded-xl border-2 font-black text-[17px] cursor-pointer transition-all whitespace-nowrap shadow-sm',
            appFilter === tab.key
              ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20 shadow-md'
              : 'bg-car-card border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'
          ]"
        >{{ tab.label }}</button>
      </div>

      <!-- 应用列表容器 -->
      <div class="max-h-[380px] overflow-y-auto space-y-2 pr-1 custom-scroll">
        <div v-if="loading" class="py-12 text-center text-car-sub font-bold text-[16px]">
          正在扫描车机已安装应用列表...
        </div>
        <div v-else-if="filteredApps.length === 0" class="py-12 text-center text-car-sub font-bold text-[16px]">
          未找到匹配的应用
        </div>
        <div
          v-else
          v-for="app in filteredApps"
          :key="app.pkg"
          @click="selectApp(app)"
          class="p-4 rounded-xl bg-car-item border border-car-border hover:border-car-accent flex items-center justify-between cursor-pointer transition-all active:scale-[0.99]"
          :class="{ 'border-car-accent ring-1 ring-car-accent bg-car-card': isCurrentSelected(app.pkg) }"
        >
          <div class="flex flex-col space-y-1">
            <div class="flex items-center space-x-2">
              <span class="text-[17px] font-black text-car-text">{{ app.name }}</span>
              <span v-if="app.isSystem" class="px-1.5 py-0.5 text-[11px] rounded bg-slate-800 text-slate-400 font-bold">系统</span>
            </div>
            <span class="text-[13px] font-mono text-car-sub">{{ app.pkg }}</span>
          </div>

          <div class="flex items-center space-x-2 shrink-0">
            <button
              class="px-5 py-2.5 rounded-xl font-black text-[15px] transition-all shadow-sm shrink-0"
              :class="isCurrentSelected(app.pkg) ? 'bg-car-accent text-slate-950 shadow-md' : 'bg-car-card border border-car-border text-car-text hover:border-car-accent'"
            >
              {{ isCurrentSelected(app.pkg) ? '当前选定' : '选定应用' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </ModalWrapper>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, showToast } from '../../store';

const apps = ref([]);
const loading = ref(false);
const searchQuery = ref('');

const modalData = computed(() => store.modals.appSelect || {});
const keyTarget = computed(() => modalData.value.keyTarget || 'mute'); // 'mute' | 'mode'

const keyTitle = computed(() => {
  if (keyTarget.value === 'speed_autoplay') return '车速自启音乐软件';
  if (keyTarget.value === 'speed_custom_action') return '车速联动打开应用';
  if (keyTarget.value === 'preferred_navi') return '主力导航软件';
  if (keyTarget.value === 'mute') return '右方向盘 ③ 静音键';
  if (keyTarget.value === 'mode') return '右方向盘 ⑥ Mode键';
  if (keyTarget.value === 'custom') return '右方向盘 ⑤ 自定义键';
  return '应用选择';
});

// 弹窗标题：精简短标题（长说明交给下方副文案，杜绝标题换行）
const modalTitle = computed(() => {
  if (keyTarget.value === 'speed_autoplay') return '车速自启音乐软件';
  if (keyTarget.value === 'speed_custom_action') return '车速联动应用';
  if (keyTarget.value === 'preferred_navi') return '主力导航软件';
  return '自定义打开应用';
});

// 全局应用列表缓存（模块级）：首次扫描后常驻内存，6 个入口共享同一份数据，
// 再次打开任意入口秒显不重扫；仅手动点「重新扫描」才清缓存重扫
const _appListCache = { launchable: null, music: null, navi: null };

// 分类筛选：默认只看用户自己安装的应用（列表短、刷新快）
const appFilter = ref('user');

watch(() => store.modals.appSelect, (val) => {
  if (val) {
    appFilter.value = 'user';
    searchQuery.value = '';
    loadApps();
  }
});

const showAllApps = ref(false);

function toggleShowAllApps() {
  showAllApps.value = !showAllApps.value;
  loadApps();
}

function loadApps() {
  loading.value = true;
  try {
    let raw = null;
    if (keyTarget.value === 'speed_autoplay' && !showAllApps.value) {
      raw = _appListCache.music || (_appListCache.music = bridge.call('getInstalledMusicAppsJson'));
    } else if (keyTarget.value === 'preferred_navi' && !showAllApps.value) {
      raw = _appListCache.navi || (_appListCache.navi = bridge.call('getInstalledNaviAppsJson'));
    }
    if (!raw || raw === '[]' || showAllApps.value) {
      raw = _appListCache.launchable || (_appListCache.launchable = bridge.call('getInstalledLaunchableApps'));
    }
    if (raw) {
      let list = typeof raw === 'string' ? JSON.parse(raw) : raw;
      list = list.map(item => ({
        name: item.name || item.appName,
        pkg: item.pkg || item.packageName,
        isSystem: item.isSystem || item.isSystemApp
      }));

      // 如果是音乐自启，追加手机蓝牙选项并按用户偏好排序
      if (keyTarget.value === 'speed_autoplay') {
        if (!list.some(a => a.pkg === 'com.android.bluetooth')) {
          list.push({ name: '手机蓝牙', pkg: 'com.android.bluetooth', isSystem: true });
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
      }
      apps.value = list;
    }
  } catch (e) {
    apps.value = [];
  } finally {
    loading.value = false;
  }
}

function rescanApps() {
  loading.value = true;
  try {
    bridge.call('refreshInstalledApps');
  } catch (e) {}
  showToast('已重新全仓扫描整车已安装应用');
  setTimeout(() => {
    // 仅手动重扫时清除全局缓存，重扫结果回填供所有入口共用
    _appListCache.launchable = null;
    _appListCache.music = null;
    _appListCache.navi = null;
    loadApps();
  }, 350);
}

function getMusicOrder() {
  try {
    const raw = localStorage.getItem('preferred_music_apps_order');
    if (raw) return JSON.parse(raw);
  } catch (e) {}
  return [];
}

function saveMusicOrder(order) {
  localStorage.setItem('preferred_music_apps_order', JSON.stringify(order));
  window.dispatchEvent(new CustomEvent('music-order-updated'));
  loadApps();
}

function moveMusicPriority(pkg, dir) {
  let order = getMusicOrder();
  if (order.length === 0) {
    order = apps.value.map(a => a.pkg);
  }
  const curIdx = order.indexOf(pkg);
  if (curIdx === -1) {
    order.push(pkg);
  }
  const idx = order.indexOf(pkg);
  const targetIdx = idx + dir;
  if (targetIdx >= 0 && targetIdx < order.length) {
    const item = order.splice(idx, 1)[0];
    order.splice(targetIdx, 0, item);
    saveMusicOrder(order);
    showToast('已调整播放优先级顺序');
  }
}

const appFilterTabs = [
  { key: 'user', label: '用户已安装' },
  { key: 'system', label: '系统应用' },
  { key: 'all', label: '全部' }
];

// 精选分类模式（车速自启仅看音乐 / 主力导航列表）列表本身已是精选小清单，不再套用户/系统筛选
const showFilterTabs = computed(() => {
  if (keyTarget.value === 'speed_autoplay' && !showAllApps.value) return false;
  if (keyTarget.value === 'preferred_navi' && !showAllApps.value) return false;
  return true;
});

const filteredApps = computed(() => {
  let list = apps.value;
  if (showFilterTabs.value) {
    if (appFilter.value === 'user') list = list.filter(a => !a.isSystem);
    else if (appFilter.value === 'system') list = list.filter(a => !!a.isSystem);
  }
  const q = searchQuery.value.trim().toLowerCase();
  if (!q) return list;
  return list.filter(a =>
    (a.name && a.name.toLowerCase().includes(q)) ||
    (a.pkg && a.pkg.toLowerCase().includes(q))
  );
});

function isCurrentSelected(pkg) {
  if (keyTarget.value === 'speed_autoplay') {
    return store.vehicleAuto.vehicle_speed_autoplay_pkg === pkg;
  }
  if (keyTarget.value === 'preferred_navi') {
    return (store.vehicleAuto.preferred_navi_pkg || 'com.autonavi.amapauto') === pkg;
  }
  if (keyTarget.value === 'speed_custom_action') {
    return store.vehicleAuto.vehicle_speed_custom_action_target === `pkg:${pkg}`;
  }
  const currentAction = store.vehicleAuto[`wheel_action_${keyTarget.value}`];
  return currentAction === `app:${pkg}`;
}

function selectApp(app) {
  if (keyTarget.value === 'speed_autoplay') {
    store.vehicleAuto.vehicle_speed_autoplay_pkg = app.pkg;
    bridge.call('setWheelControlStringSetting', 'vehicle_speed_autoplay_pkg', app.pkg);
    localStorage.setItem('vehicle_speed_autoplay_app_name', app.name);
    window.dispatchEvent(new CustomEvent('music-order-updated'));
    showToast(`车速自启首选主力已设为: ${app.name}`);
    closeModal('appSelect');
    return;
  }
  if (keyTarget.value === 'preferred_navi') {
    store.vehicleAuto.preferred_navi_pkg = app.pkg;
    bridge.call('setWheelControlStringSetting', 'preferred_navi_pkg', app.pkg);
    localStorage.setItem('preferred_navi_app_name', app.name);
    showToast(`当前主力导航已设为: ${app.name}`);
    closeModal('appSelect');
    return;
  }
  if (keyTarget.value === 'speed_custom_action') {
    const act = `pkg:${app.pkg}`;
    store.vehicleAuto.vehicle_speed_custom_action_target = act;
    bridge.call('setWheelControlStringSetting', 'vehicle_speed_custom_action_target', act);
    localStorage.setItem('vehicle_speed_custom_action_app_name', app.name);
    showToast(`车速达标联动目标已设为: ${app.name}`);
    window.dispatchEvent(new CustomEvent('custom-action-target-updated'));
    closeModal('appSelect');
    return;
  }
  const act = `app:${app.pkg}`;
  // keyTarget 为 <键>_<手势> 全量 id（如 voice_double）；必须写带手势后缀的完整键，
  // 否则双击/长按配置会丢失回落 default（表现为只有单击有效）——与 ActionSelect 下拉
  // 走的 setGestureAction 通道保持一致：写 specific 键 + single 时同步 legacy 键。
  const sep = keyTarget.value.lastIndexOf('_');
  const wKey = sep > 0 ? keyTarget.value.slice(0, sep) : keyTarget.value;
  const gesture = sep > 0 ? keyTarget.value.slice(sep + 1) : 'single';
  const pKey = `wheel_action_${keyTarget.value}`;
  store.vehicleAuto[pKey] = act;
  bridge.call('setWheelGestureAction', wKey, gesture, act);
  // 同时保存友好的应用名称供前端展示
  localStorage.setItem(`${pKey}_app_name`, app.name);
  showToast(`${keyTitle.value} 已映射为打开: ${app.name}`);
  closeModal('appSelect');
}
</script>
