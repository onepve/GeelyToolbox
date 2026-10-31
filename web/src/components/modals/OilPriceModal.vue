<template>
  <ModalWrapper
    :show="store.modals.oilPrice"
    title="全国实时油价与调价日历"
    badge="国家发改委最高零售限价"
    maxWidthClass="max-w-[880px]"
    @close="closeModal('oilPrice')"
  >
    <div class="flex flex-col space-y-4">
      <!-- 常用省份横向快捷栏 -->
      <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3">
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-2">
            <span class="text-[14.5px] text-car-sub font-black whitespace-nowrap">常用省份:</span>
            <!-- 收藏省份胶囊列表 (space-x-2 避免 flex gap) -->
            <div class="flex items-center space-x-2">
              <button
                v-for="prov in store.oilPrice.favProvinces"
                :key="prov"
                @click="onSelectProvince(prov)"
                :class="[
                  'min-h-[50px] px-4 rounded-xl border-2 font-black text-[15.5px] cursor-pointer transition-all flex items-center shadow-sm',
                  store.oilPrice.selectedProvince === prov
                    ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/30'
                    : 'bg-car-card border-car-border text-car-text hover:border-car-border-light'
                ]"
              >
                <span class="mr-1.5 text-[14px] text-car-accent">★</span>
                <span>{{ prov }}</span>
              </button>
            </div>
          </div>

          <!-- 右侧动作：收藏当前 / 展开更多 / 刷新 -->
          <div class="flex items-center space-x-2">
            <button
              @click="onRefresh"
              :disabled="store.oilPrice.isSyncing"
              class="min-h-[50px] px-3.5 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[14px] cursor-pointer hover:border-car-border-light transition-all flex items-center shadow-sm whitespace-nowrap space-x-1.5"
            >
              <svg class="w-3.5 h-3.5 text-car-sub" :class="{'animate-spin text-car-accent': store.oilPrice.isSyncing}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/>
              </svg>
              <span>{{ store.oilPrice.isSyncing ? '同步中' : '刷新' }}</span>
            </button>
            <button
              @click="onToggleFav"
              class="min-h-[50px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[14.5px] cursor-pointer hover:border-car-border-light transition-all flex items-center shadow-sm whitespace-nowrap"
            >
              <span class="mr-1.5 text-[15px]" :class="isCurrentFav ? 'text-car-accent' : 'text-car-sub'">
                {{ isCurrentFav ? '★ 已收藏' : '☆ 收藏当前' }}
              </span>
            </button>
            <button
              @click="showPicker = !showPicker"
              :class="[
                'min-h-[50px] px-4 rounded-xl border-2 font-black text-[14.5px] cursor-pointer transition-all flex items-center shadow-sm whitespace-nowrap',
                showPicker ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/30' : 'bg-car-card border-car-border text-car-text hover:border-car-border-light'
              ]"
            >
              <span>{{ showPicker ? '收起省份 ▴' : '更多省份 ▾' }}</span>
            </button>
          </div>
        </div>

        <!-- 展开的全国 31 省网格选择面板 -->
        <div v-if="showPicker" class="pt-3 border-t border-car-border">
          <div class="flex flex-wrap -m-1">
            <div
              v-for="prov in PROVINCE_LIST"
              :key="prov"
              class="p-1 w-[14.28%]"
            >
              <button
                @click="onSelectProvince(prov, true)"
                :class="[
                  'w-full min-h-[50px] rounded-xl border-2 font-black text-[15px] cursor-pointer transition-all flex items-center justify-center shadow-sm truncate',
                  store.oilPrice.selectedProvince === prov
                    ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/30'
                    : 'bg-car-card border-car-border text-car-text hover:border-car-border-light'
                ]"
              >
                {{ prov }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 四大标号油价核心矩阵 (4 列并列卡片，点击即可自设为默认油品) -->
      <div v-if="currentPrice" class="flex space-x-3">
        <div
          v-for="item in fuelList"
          :key="item.id"
          @click="onSelectDefaultFuel(item.id)"
          class="flex-1 p-4 rounded-2xl border-2 flex flex-col items-center justify-center text-center shadow-sm relative overflow-hidden cursor-pointer transition-all active:scale-[0.98]"
          :class="[
            defaultFuelType.includes(item.key)
              ? 'bg-car-card border-car-accent shadow-md'
              : 'bg-car-item border-car-border hover:border-car-border-light'
          ]"
        >
          <div class="flex items-center space-x-1.5 mb-1.5">
            <span
              v-if="defaultFuelType.includes(item.key)"
              class="text-[12px] px-2 py-0.5 rounded bg-car-item text-car-accent font-black border border-car-accent"
            >
              我的默认
            </span>
            <span
              v-else-if="item.recTag"
              class="text-[12px] px-2 py-0.5 rounded bg-car-card text-car-accent font-extrabold border border-car-border"
            >
              {{ item.recTag }}
            </span>
            <span
              class="text-[16px] font-black"
              :class="defaultFuelType.includes(item.key) ? 'text-car-accent' : 'text-car-text'"
            >
              {{ item.label }}
            </span>
          </div>

          <div class="flex items-baseline justify-center my-0.5">
            <span
              class="text-[34px] font-black font-mono leading-none"
              :class="defaultFuelType.includes(item.key) ? 'text-car-accent' : 'text-car-text'"
            >
              {{ item.price ? item.price.toFixed(2) : '--' }}
            </span>
            <span class="text-[14px] text-car-sub font-bold ml-1">元/升</span>
          </div>

          <div class="mt-1 text-[13px] text-rose-400 font-black">
            上轮: ↑ {{ item.diff ? item.diff.toFixed(2) : '0.00' }}
          </div>

          <div class="mt-2.5 pt-2 w-full border-t border-car-border/50 text-[12px] font-bold">
            <span v-if="defaultFuelType.includes(item.key)" class="text-car-accent font-black flex items-center justify-center space-x-1">
              <span>●</span>
              <span>默认基准</span>
            </span>
            <span v-else class="text-car-sub hover:text-car-text">
              点击设为默认
            </span>
          </div>
        </div>
      </div>

      <div v-else class="p-8 rounded-2xl bg-car-item border-2 border-car-border flex flex-col items-center justify-center text-center space-y-2">
        <span class="text-[17px] font-black text-car-text">正在从云端获取最新全国油价数据...</span>
        <span class="text-[13px] font-bold text-car-sub">100% 动态直连云端 R2 实时源，严禁本地伪造兜底</span>
      </div>

      <!-- 下轮调价周期与预测算账 -->
      <div v-if="store.oilPrice.nextAdjustment" class="p-4 rounded-2xl bg-car-card border border-car-border flex flex-col space-y-2">
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-2">
            
            <span class="text-[16px] font-black text-car-text">下轮调价窗口:</span>
            <span class="text-[16px] font-black text-car-accent">{{ store.oilPrice.nextAdjustment.dateLabel }}</span>
            <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-item text-rose-600 dark:text-rose-300 font-extrabold border border-car-border">
              仅剩 {{ daysLeft.days }} 天
            </span>
          </div>
          <div 
            class="text-[14px] font-black"
            :class="store.oilPrice.nextAdjustment.trend === 'down' ? 'text-emerald-400' : (store.oilPrice.nextAdjustment.trend === 'up' ? 'text-rose-400' : 'text-car-sub')"
          >
            {{ store.oilPrice.nextAdjustment.trendLabel }} ({{ store.oilPrice.nextAdjustment.predictedTonne }})
          </div>
        </div>

        <div class="text-[14.5px] text-car-sub font-bold flex items-center leading-relaxed">
          <span>
            预测变动幅度：每升预计{{ store.oilPrice.nextAdjustment.trend === 'down' ? '下调' : (store.oilPrice.nextAdjustment.trend === 'up' ? '上调' : '变动') }}约 
            <b :class="store.oilPrice.nextAdjustment.trend === 'down' ? 'text-emerald-400' : (store.oilPrice.nextAdjustment.trend === 'up' ? 'text-rose-400' : 'text-car-text')">
              {{ (store.oilPrice.nextAdjustment.predictedLiter || '').replace(/^[+-]/, '') }} 元
            </b>。
          </span>
        </div>

        <div class="p-3 rounded-xl bg-car-item border border-car-border text-[14px] text-car-text font-bold flex items-center">
          
          <span>{{ store.oilPrice.nextAdjustment.tip }}</span>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-between w-full">
        <div class="flex items-center space-x-3">
          <span class="text-[13.5px] text-car-sub font-bold">
            {{ store.oilPrice.nextAdjustment ? '上轮生效: ' + store.oilPrice.nextAdjustment.lastAdjustmentDate + ' (' + store.oilPrice.selectedProvince + ')' : '正在同步最新调价周期...' }}
          </span>
          <GasStationBrand />
        </div>
        <button
          @click="closeModal('oilPrice')"
          class="min-h-[52px] px-10 bg-car-item border-2 border-car-border rounded-xl text-car-text font-black text-[17px] cursor-pointer hover:border-car-border-light shadow-sm"
        >
          关闭
        </button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { ref, computed } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import GasStationBrand from '../GasStationBrand.vue';
import {
  store,
  closeModal,
  setOilSelectedProvince,
  toggleOilFavProvince,
  refreshOilPrices,
  setDefaultFuelType,
  showToast
} from '../../store';
import { PROVINCE_LIST, getDaysToAdjustment } from '../../utils/oilPriceData';

const showPicker = ref(false);

const defaultFuelType = computed(() => {
  return store.fuelLedger?.config?.default_fuel_type || '92#';
});

const fuelList = computed(() => {
  if (!currentPrice.value) return [];
  const cur = currentPrice.value;
  return [
    { id: '92#', key: '92', label: '92# 汽油', price: cur.p92, diff: cur.diff92, recTag: '缤越推荐' },
    { id: '95#', key: '95', label: '95# 汽油', price: cur.p95, diff: cur.diff95, recTag: '' },
    { id: '98#', key: '98', label: '98# 汽油', price: cur.p98, diff: cur.diff98, recTag: '' },
    { id: '0# 柴油', key: '0', label: '0# 柴油', price: cur.p0, diff: cur.diff0, recTag: '' }
  ];
});

function onSelectDefaultFuel(type) {
  setDefaultFuelType(type);
  showToast(`已设为默认油品：${type}，全车测算与记账将以此为准`, 'success');
}

const daysLeft = computed(() => {
  if (!store.oilPrice.nextAdjustment || !store.oilPrice.nextAdjustment.date) {
    return { days: 0, hours: 0, isPassed: false };
  }
  return getDaysToAdjustment(store.oilPrice.nextAdjustment.date);
});

const currentPrice = computed(() => {
  const prov = store.oilPrice.selectedProvince || '北京';
  return store.oilPrice.regionalPrices[prov] || store.oilPrice.regionalPrices['北京'] || null;
});

const isCurrentFav = computed(() => {
  const prov = store.oilPrice.selectedProvince || '北京';
  return store.oilPrice.favProvinces.includes(prov);
});

function onSelectProvince(prov, closeDropdown = false) {
  setOilSelectedProvince(prov);
  if (closeDropdown) {
    showPicker.value = false;
  }
}

function onToggleFav() {
  const prov = store.oilPrice.selectedProvince || '北京';
  toggleOilFavProvince(prov);
}

function onRefresh() {
  refreshOilPrices(true);
}
</script>
