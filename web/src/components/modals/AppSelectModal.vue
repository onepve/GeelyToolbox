<template>
  <ModalWrapper
    :show="!!store.modals.appSelect"
    :title="`选择按键自定义打开应用 (${keyTitle})`"
    badge="方控映射"
    maxWidthClass="max-w-[760px]"
    @close="closeModal('appSelect')"
  >
    <div class="flex flex-col space-y-4">
      <div class="text-[15.5px] font-bold text-car-sub leading-relaxed">
        请选择车机中已安装的应用。选定后，方向盘按下该按键将直接唤醒并打开该第三方应用：
      </div>

      <!-- 快捷搜索框 -->
      <div class="relative">
        <input
          type="text"
          v-model="searchQuery"
          placeholder="搜索应用名称或包名..."
          class="w-full h-12 px-4 rounded-xl bg-car-item border border-car-border text-car-text placeholder-car-sub font-bold text-[16px] outline-none focus:border-car-accent transition-all"
        />
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

          <button
            class="px-5 py-2.5 rounded-xl font-black text-[15px] transition-all shadow-sm shrink-0"
            :class="isCurrentSelected(app.pkg) ? 'bg-car-accent text-slate-950 shadow-md' : 'bg-car-card border border-car-border text-car-text hover:border-car-accent'"
          >
            {{ isCurrentSelected(app.pkg) ? '当前选定' : '选定应用' }}
          </button>
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
const keyTarget = computed(() => modalData.value.keyTarget || 'ok'); // 'ok' | 'mute' | 'mode'

const keyTitle = computed(() => {
  if (keyTarget.value === 'ok') return '编号 2 滚轮按压';
  if (keyTarget.value === 'mute') return '编号 3 静音短按';
  if (keyTarget.value === 'mode') return '编号 6 Mode键';
  return '方向盘按键';
});

watch(() => store.modals.appSelect, (val) => {
  if (val) {
    loadApps();
  }
});

function loadApps() {
  loading.value = true;
  try {
    const raw = bridge.call('getInstalledLaunchableApps');
    if (raw) {
      apps.value = typeof raw === 'string' ? JSON.parse(raw) : raw;
    }
  } catch (e) {
    apps.value = [];
  } finally {
    loading.value = false;
  }
}

const filteredApps = computed(() => {
  if (!searchQuery.value.trim()) return apps.value;
  const q = searchQuery.value.trim().toLowerCase();
  return apps.value.filter(a => 
    (a.name && a.name.toLowerCase().includes(q)) || 
    (a.pkg && a.pkg.toLowerCase().includes(q))
  );
});

function isCurrentSelected(pkg) {
  const currentAction = store.vehicleAuto[`wheel_action_${keyTarget.value}`];
  return currentAction === `app:${pkg}`;
}

function selectApp(app) {
  const act = `app:${app.pkg}`;
  store.vehicleAuto[`wheel_action_${keyTarget.value}`] = act;
  bridge.call('setWheelControlStringSetting', `wheel_action_${keyTarget.value}`, act);
  // 同时保存友好的应用名称供前端展示
  localStorage.setItem(`wheel_action_${keyTarget.value}_app_name`, app.name);
  showToast(`${keyTitle.value} 已映射为打开: ${app.name}`);
  closeModal('appSelect');
}
</script>
