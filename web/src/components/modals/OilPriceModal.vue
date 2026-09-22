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
                <span class="mr-1.5 text-[14px] text-amber-400">★</span>
                <span>{{ prov }}</span>
              </button>
            </div>
          </div>

          <!-- 右侧动作：收藏当前 / 展开更多 -->
          <div class="flex items-center space-x-2">
            <button
              @click="onToggleFav"
              class="min-h-[50px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[14.5px] cursor-pointer hover:border-car-border-light transition-all flex items-center shadow-sm whitespace-nowrap"
            >
              <span class="mr-1.5 text-[15px]" :class="isCurrentFav ? 'text-amber-400' : 'text-car-sub'">
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

      <!-- 四大标号油价核心矩阵 (4 列并列卡片) -->
      <div class="flex space-x-3">
        <!-- 92# 汽油 -->
        <div class="flex-1 p-4 rounded-2xl bg-car-item border-2 border-car-border flex flex-col items-center justify-center text-center shadow-sm relative overflow-hidden">
          <div class="flex items-center space-x-1.5 mb-1.5">
            <span class="text-[12.5px] px-2 py-0.5 rounded bg-car-card text-amber-300 font-extrabold border border-car-border">缤越推荐</span>
            <span class="text-[16px] font-black text-car-text">92# 汽油</span>
          </div>
          <div class="flex items-baseline justify-center my-0.5">
            <span class="text-[34px] font-black text-car-text font-mono leading-none">{{ currentPrice.p92.toFixed(2) }}</span>
            <span class="text-[14px] text-car-sub font-bold ml-1">元/升</span>
          </div>
          <div class="mt-1 text-[13px] text-rose-400 font-black">
            上轮: ↑ {{ currentPrice.diff92.toFixed(2) }}
          </div>
        </div>

        <!-- 95# 汽油 -->
        <div class="flex-1 p-4 rounded-2xl bg-car-item border-2 border-car-border flex flex-col items-center justify-center text-center shadow-sm relative overflow-hidden">
          <div class="flex items-center space-x-1.5 mb-1.5">
            <span class="text-[16px] font-black text-car-text">95# 汽油</span>
          </div>
          <div class="flex items-baseline justify-center my-0.5">
            <span class="text-[34px] font-black text-car-text font-mono leading-none">{{ currentPrice.p95.toFixed(2) }}</span>
            <span class="text-[14px] text-car-sub font-bold ml-1">元/升</span>
          </div>
          <div class="mt-1 text-[13px] text-rose-400 font-black">
            上轮: ↑ {{ currentPrice.diff95.toFixed(2) }}
          </div>
        </div>

        <!-- 98# 汽油 -->
        <div class="flex-1 p-4 rounded-2xl bg-car-item border-2 border-car-border flex flex-col items-center justify-center text-center shadow-sm relative overflow-hidden">
          <div class="flex items-center space-x-1.5 mb-1.5">
            <span class="text-[16px] font-black text-car-text">98# 汽油</span>
          </div>
          <div class="flex items-baseline justify-center my-0.5">
            <span class="text-[34px] font-black text-car-text font-mono leading-none">{{ currentPrice.p98.toFixed(2) }}</span>
            <span class="text-[14px] text-car-sub font-bold ml-1">元/升</span>
          </div>
          <div class="mt-1 text-[13px] text-rose-400 font-black">
            上轮: ↑ {{ currentPrice.diff98.toFixed(2) }}
          </div>
        </div>

        <!-- 0# 柴油 -->
        <div class="flex-1 p-4 rounded-2xl bg-car-item border-2 border-car-border flex flex-col items-center justify-center text-center shadow-sm relative overflow-hidden">
          <div class="flex items-center space-x-1.5 mb-1.5">
            <span class="text-[16px] font-black text-car-text">0# 柴油</span>
          </div>
          <div class="flex items-baseline justify-center my-0.5">
            <span class="text-[34px] font-black text-car-text font-mono leading-none">{{ currentPrice.p0.toFixed(2) }}</span>
            <span class="text-[14px] text-car-sub font-bold ml-1">元/升</span>
          </div>
          <div class="mt-1 text-[13px] text-rose-400 font-black">
            上轮: ↑ {{ currentPrice.diff0.toFixed(2) }}
          </div>
        </div>
      </div>

      <!-- 下轮调价周期与预测算账 -->
      <div class="p-4 rounded-2xl bg-car-card border border-car-border flex flex-col space-y-2">
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-2">
            <span class="text-[18px]">📅</span>
            <span class="text-[16px] font-black text-car-text">下轮调价窗口:</span>
            <span class="text-[16px] font-black text-amber-400">{{ store.oilPrice.nextAdjustment.dateLabel }}</span>
            <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-item text-rose-300 font-extrabold border border-car-border">
              仅剩 {{ daysLeft }} 天
            </span>
          </div>
          <div class="text-[14px] font-black text-rose-400">
            {{ store.oilPrice.nextAdjustment.trendLabel }} ({{ store.oilPrice.nextAdjustment.predictedTonne }})
          </div>
        </div>

        <div class="text-[14.5px] text-car-sub font-bold flex items-center leading-relaxed">
          <span class="mr-1.5">📈</span>
          <span>预测变动幅度：每升预计上调约 <b class="text-rose-400">{{ store.oilPrice.nextAdjustment.predictedLiter }} 元</b>。</span>
        </div>

        <div class="p-3 rounded-xl bg-car-item border border-car-border text-[14px] text-car-text font-bold flex items-center">
          <span class="mr-2 text-[16px]">💡</span>
          <span>{{ store.oilPrice.nextAdjustment.tip }}</span>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-between w-full">
        <span class="text-[13.5px] text-car-sub font-bold">
          上轮生效: {{ store.oilPrice.nextAdjustment.lastAdjustmentDate }} (当前地区: {{ store.oilPrice.selectedProvince }})
        </span>
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
import {
  store,
  closeModal,
  setOilSelectedProvince,
  toggleOilFavProvince
} from '../../store';
import { PROVINCE_LIST, getDaysToAdjustment } from '../../utils/oilPriceData';

const showPicker = ref(false);

const daysLeft = computed(() => {
  return getDaysToAdjustment(store.oilPrice.nextAdjustment.date);
});

const currentPrice = computed(() => {
  const prov = store.oilPrice.selectedProvince || '浙江';
  return store.oilPrice.regionalPrices[prov] || store.oilPrice.regionalPrices['浙江'];
});

const isCurrentFav = computed(() => {
  const prov = store.oilPrice.selectedProvince || '浙江';
  return store.oilPrice.favProvinces.includes(prov);
});

function onSelectProvince(prov, closeDropdown = false) {
  setOilSelectedProvince(prov);
  if (closeDropdown) {
    showPicker.value = false;
  }
}

function onToggleFav() {
  const prov = store.oilPrice.selectedProvince || '浙江';
  toggleOilFavProvince(prov);
}
</script>
