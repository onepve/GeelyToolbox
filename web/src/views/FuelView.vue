<template>
  <div class="flex flex-col space-y-4">
    <!-- 顶部车规二级分段胶囊：一页全景掌控 -->
    <SubTabs :tabs="fuelTabs" v-model="activeTab" />

    <!-- Tab 1: 全国实时油价与调价日历 (单屏全收敛) -->
    <div v-if="activeTab === 'price'" class="flex flex-col space-y-4">
      <FeatureCard 
        title="全国实时油价与调价日历"
        desc="监测各省发改委最新最高零售限价，提供调价周期倒计时与加油算账建议。"
        helpTitle="【功能指南】全国实时油价与调价日历"
        helpText="1. 官方限价：&#10;基于国家发改委最新发布的各省成品油最高零售限价，同省同价。&#10;&#10;2. 调价倒计时：&#10;按照国家发改委 10 个工作日调价周期倒计时，提前提供涨跌预期预警。&#10;&#10;3. 省钱算账：&#10;结合缤越 COOL 45L 油箱容积自动折算加满一箱所需成本变动。" helpTip="点击可查看全国 31 省份 92#/95#/98#/柴油价格及调价详情。"
      >
        <div class="w-full flex items-center justify-between p-4 rounded-2xl bg-car-item border border-car-border">
          <div class="flex items-center space-x-4">
            <div class="flex items-center space-x-2">
              <div class="flex flex-col">
                <div class="flex items-center space-x-2">
                  <span class="text-[19px] font-black text-car-text">{{ currentProvinceName }}</span>
                  <span class="text-[13px] px-2 py-0.5 rounded bg-car-card text-car-accent font-extrabold border border-car-accent/30">当前地区</span>
                </div>
                <span class="text-[13px] text-car-sub font-bold mt-0.5">发改委限价 · {{ store.oilPrice.nextAdjustment.lastAdjustmentDate }} 生效</span>
              </div>
            </div>

            <div class="h-10 w-px bg-car-border"></div>

            <div class="flex items-center space-x-3">
              <div class="flex flex-col">
                <span class="text-[12.5px] text-car-sub font-bold">92# 汽油</span>
                <span class="text-[20px] font-black text-car-text font-mono leading-none">¥{{ currentOilP92 }}<span class="text-[13px] font-normal text-car-sub">/L</span></span>
              </div>
              <div class="flex flex-col">
                <span class="text-[12.5px] text-car-sub font-bold">95# 汽油</span>
                <span class="text-[20px] font-black text-car-text font-mono leading-none">¥{{ currentOilP95 }}<span class="text-[13px] font-normal text-car-sub">/L</span></span>
              </div>
            </div>

            <div class="h-10 w-px bg-car-border"></div>

            <div class="flex flex-col">
              <div class="flex items-center space-x-1.5">
                <span 
                  class="text-[13px] font-black"
                  :class="store.oilPrice.nextAdjustment?.trend === 'down' ? 'text-emerald-500 dark:text-emerald-400' : (store.oilPrice.nextAdjustment?.trend === 'up' ? 'text-rose-500 dark:text-rose-400' : 'text-car-sub')"
                >
                  下轮预期: {{ store.oilPrice.nextAdjustment ? store.oilPrice.nextAdjustment.predictedLiter + '元/L' : '获取中...' }}
                </span>
                <span class="text-[12px] px-2 py-0.5 rounded-full bg-car-card text-rose-600 dark:text-rose-300 font-extrabold border border-car-border whitespace-nowrap">
                  {{ oilDaysText }}
                </span>
              </div>
              <span class="text-[12.5px] text-car-sub font-bold mt-0.5">
                调价日: {{ store.oilPrice.nextAdjustment ? store.oilPrice.nextAdjustment.dateLabel : '同步中...' }}
              </span>
            </div>
          </div>

          <div class="flex items-center space-x-3 shrink-0">
            <button 
              @click="onRefreshOilPrice"
              :disabled="store.oilPrice.isSyncing"
              class="min-h-[56px] px-5 rounded-xl border-2 border-car-border bg-car-item text-car-text font-black text-[16px] cursor-pointer hover:border-car-border-light transition-all shadow-md flex items-center space-x-2 whitespace-nowrap"
            >
              <svg class="w-4 h-4 text-car-sub" :class="{'animate-spin text-car-accent': store.oilPrice.isSyncing}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/>
              </svg>
              <span>{{ store.oilPrice.isSyncing ? '同步中...' : '刷新油价' }}</span>
            </button>

            <button 
              @click="openModal('oilPrice')"
              class="min-h-[56px] px-6 rounded-xl border-2 border-car-accent bg-car-item text-car-accent font-black text-[16.5px] cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 transition-all shadow-md flex items-center whitespace-nowrap"
            >
              <span>全国详情 ➔</span>
            </button>
          </div>
        </div>
      </FeatureCard>

      <!-- 油箱容积与加满成本计算器卡片 -->
      <div class="grid grid-cols-2 gap-4 items-stretch">
        <div class="p-5 rounded-3xl bg-car-card border-2 border-car-border shadow-xl flex flex-col justify-between">
          <div class="flex items-center justify-between mb-3">
            <div class="flex items-center space-x-2">
              <span class="text-[19px] font-black text-car-text">{{ vehicleModelDisplay }} {{ tankCapacity }}L 油箱测算</span>
              <HelpDot size="sm" @click="showTankCalcHelp" />
            </div>
            <span class="px-2.5 py-0.5 text-[12px] font-black rounded-full border bg-car-item border-car-border text-car-accent">官方标称</span>
          </div>

          <!-- 核心双测算：车辆实时加满测算 vs 日常加200元实用换算 -->
          <div class="space-y-3 py-1">
            <!-- 1. 车辆实时动态加满补能计算 -->
            <div class="p-3.5 bg-car-item border border-car-border rounded-2xl flex flex-col space-y-1.5">
              <div class="flex items-center justify-between">
                <div class="flex items-center space-x-2">
                  <span class="text-[14px] text-car-text font-black">当前油箱实际估算：</span>
                  <span v-if="hasFuelSignal" class="px-2 py-0.5 rounded-full bg-car-card border border-car-border text-emerald-400 font-mono text-[12px] font-black">
                    余 {{ currentFuelLiters }}L ({{ currentFuelPercent }}%)
                  </span>
                  <span v-else class="px-2 py-0.5 rounded-full bg-car-card border border-car-border text-car-sub font-bold text-[12px]">
                    底层总线读取中 · 点火即显
                  </span>
                </div>
                <span class="text-[12.5px] text-car-sub font-bold">
                  {{ hasFuelSignal ? `加满缺口: ${fillGapLiters}L` : `标称总容积: ${tankCapacity}L` }}
                </span>
              </div>
              <div class="flex items-center justify-between mt-2 pt-4 border-t border-car-border/40">
                <span class="text-[13px] text-car-sub font-bold">
                  {{ hasFuelSignal ? '加满预估需补：' : '空箱加满整箱预估：' }}
                </span>
                <div class="flex items-baseline space-x-3">
                  <span class="text-[13px] text-car-sub font-bold">92# <span class="text-[20px] font-black text-car-accent font-mono">¥{{ displayCost92 }}</span></span>
                  <span class="text-[13px] text-car-sub font-bold">95# <span class="text-[18px] font-black text-car-text font-mono">¥{{ displayCost95 }}</span></span>
                </div>
              </div>
            </div>

            <!-- 2. 日常加 200 元可加油量测算 -->
            <div class="p-3.5 bg-car-item border border-car-border rounded-2xl flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">日常加 200 元可加：</span>
              <div class="flex items-baseline space-x-3">
                <span class="text-[13px] text-car-sub font-bold">92# <span class="text-[20px] font-black text-emerald-400 font-mono">{{ (currentOilData?.p92 ? 200 / currentOilData.p92 : 26.3).toFixed(1) }}</span> <span class="text-[12px]">L</span></span>
                <span class="text-[13px] text-car-sub font-bold">95# <span class="text-[18px] font-black text-car-text font-mono">{{ (currentOilData?.p95 ? 200 / currentOilData.p95 : 24.6).toFixed(1) }}</span> <span class="text-[12px]">L</span></span>
              </div>
            </div>
          </div>

          <div class="text-[12.5px] text-car-sub font-bold pt-3 border-t border-car-border/50">
            根据 {{ currentProvinceName }} 发改委最新挂牌价换算；车主实际优惠及满减立减以加油机实显为准。
          </div>
        </div>

        <div class="p-5 rounded-3xl bg-car-card border-2 border-car-border shadow-xl flex flex-col justify-between">
          <div class="flex items-center justify-between mb-3">
            <span class="text-[19px] font-black text-car-text">全国调价窗口机制</span>
            <span class="px-2.5 py-0.5 text-[12px] font-black rounded-full border bg-car-item border-car-border text-car-sub">10个工作日</span>
          </div>
          <div class="text-[14px] text-car-sub font-bold leading-relaxed py-1">
            国内成品油调价以 10 个工作日为一个周期，跟踪国际原油加权均价。调价幅度不足 50 元/吨时暂不作调整，纳入下次调价累加或冲抵。
          </div>
          <div class="flex items-center justify-between pt-4 border-t border-car-border/50">
            <span class="text-[13px] text-car-accent font-black">建议：预期上涨提前加满，预期下调晚一天加</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Tab 2: 单次行程油耗账单 (框架就绪) -->
    <div v-if="activeTab === 'trip'" class="flex flex-col space-y-4">
      <div class="grid grid-cols-2 gap-4 items-stretch">
        <div class="p-5 rounded-3xl bg-car-card border-2 border-car-border shadow-xl flex flex-col justify-between">
          <div class="flex items-center justify-between mb-2">
            <span class="text-[20px] font-black text-car-text">上次行程总结 (停稳 P 挡结算)</span>
            <span class="px-2.5 py-0.5 text-[12px] font-black rounded-full border bg-car-item border-car-border text-emerald-400">自动记录</span>
          </div>
          <div class="grid grid-cols-3 gap-2 py-4 text-center bg-car-item border border-car-border rounded-2xl my-2">
            <div class="flex flex-col">
              <span class="text-[13px] text-car-sub font-bold">行驶里程</span>
              <span class="text-[24px] font-black text-car-accent font-mono mt-1">18.5 <span class="text-[12px]">km</span></span>
            </div>
            <div class="flex flex-col border-x border-car-border/60">
              <span class="text-[13px] text-car-sub font-bold">综合油耗</span>
              <span class="text-[24px] font-black text-car-text font-mono mt-1">6.8 <span class="text-[12px]">L/100km</span></span>
            </div>
            <div class="flex flex-col">
              <span class="text-[13px] text-car-sub font-bold">预估油费</span>
              <span class="text-[24px] font-black text-amber-400 font-mono mt-1">¥ 9.8 <span class="text-[12px]">元</span></span>
            </div>
          </div>
          <div class="text-[13px] text-car-sub font-bold">
            起步切 D 挡自动打点总里程 ODO；停稳挂 P 挡结合发改委实时油价静默出账，杜绝语音扰民。
          </div>
        </div>

        <div class="p-5 rounded-3xl bg-car-card border-2 border-car-border shadow-xl flex flex-col justify-between">
          <div class="flex items-center justify-between mb-2">
            <span class="text-[20px] font-black text-car-text">行程计程与出账机制</span>
            <span class="px-2.5 py-0.5 text-[12px] font-black rounded-full border bg-car-item border-car-border text-car-sub">静默出账</span>
          </div>
          <div class="space-y-2 text-[14.5px] text-car-sub font-bold py-2 leading-relaxed">
            <div>• <b>起步计程</b>：踩刹车换入 D 挡起步打点底盘真实里程，原地挪车 (&lt;0.5km) 智能过滤；</div>
            <div>• <b>停稳记账</b>：挂回 P 挡瞬间冻结单次能耗数据，自动持久化至车机内部存储；</div>
            <div>• <b>本地存储</b>：数据写入车机共享存储，零强制网络依赖，应用卸载不丢账。</div>
          </div>
          <div class="pt-4 border-t border-car-border/50 text-[13px] text-car-accent font-black">
            状态：底层总线监听中 · 随时就绪
          </div>
        </div>
      </div>
    </div>

    <!-- Tab 3: 智能加油账本 -->
    <div v-if="activeTab === 'record'" class="flex flex-col space-y-4">
      <div class="grid grid-cols-2 gap-4 items-stretch">
        <div class="p-5 rounded-3xl bg-car-card border-2 border-car-border shadow-xl flex flex-col justify-between">
          <div class="flex items-center justify-between mb-2">
            <span class="text-[20px] font-black text-car-text">自动识别加油记账</span>
            <span class="px-2.5 py-0.5 text-[12px] font-black rounded-full border bg-car-item border-car-border text-car-accent">智能感知</span>
          </div>
          <div class="space-y-2 text-[14.5px] text-car-sub font-bold py-2 leading-relaxed">
            <div>• <b>智能感知</b>：熄火前锁存油箱浮子油量，下次点火若油量突增 &ge;8% 自动判定刚加完油；</div>
            <div>• <b>全自动记账</b>：自动计算加注升数与预估花费，加油开走无需掏手机手动输入；</div>
            <div>• <b>人工灵活微调</b>：支持对加注记录随时手动修改真实实付金额、满减优惠或删除误判。</div>
          </div>
          <div class="p-3 bg-car-item border border-car-border rounded-2xl flex items-center justify-between">
            <span class="text-[14px] text-car-text font-black">最近一次加油推算：</span>
            <span class="text-[16px] font-mono text-car-accent font-black">加注约 +28.5 L · ¥225.0</span>
          </div>
        </div>

        <div class="p-5 rounded-3xl bg-car-card border-2 border-car-border shadow-xl flex flex-col justify-between">
          <div class="flex items-center justify-between mb-2">
            <span class="text-[20px] font-black text-car-text">手机端同步查看</span>
            <span class="px-2.5 py-0.5 text-[12px] font-black rounded-full border bg-car-item border-car-border text-emerald-400">微信扫码</span>
          </div>
          <div class="text-[14px] text-car-sub font-bold leading-relaxed py-2">
            微信直接扫码车机屏幕即可一键绑定爱车，离车后在手机微信中随时翻看历史油耗走势、消费明细与账单流水。
          </div>
          <div class="pt-4 border-t border-car-border/50 flex items-center justify-between">
            <span class="text-[13px] text-car-sub font-bold">微信扫一扫屏幕即可直接绑定爱车</span>
            <button 
              @click="openModal('confirm', { title: '手机扫码配对', desc: '即将在下一阶段开启微信扫码免密直连云同步服务。', confirmText: '我知道了' })"
              class="h-[50px] px-5 bg-car-card border-2 border-car-accent text-car-accent font-black text-[15px] rounded-xl hover:border-car-accent cursor-pointer shadow-md transition-all whitespace-nowrap"
            >
              绑定手机微信 ➔
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { store, openModal, refreshOilPrices } from '../store';
import SubTabs from '../components/SubTabs.vue';
import FeatureCard from '../components/FeatureCard.vue';
import HelpDot from '../components/HelpDot.vue';
import { getDaysToAdjustment } from '../utils/oilPriceData';

const activeTab = ref('price');
const fuelTabs = [
  { id: 'price', name: '实时油价日历', tag: '发改委限价' },
  { id: 'trip', name: '单次行程油耗', tag: '行车账单' },
  { id: 'record', name: '智能加油账本', tag: '自动记账' }
];

// 车型名称与油箱容积动态适配
const vehicleModelDisplay = computed(() => {
  if (store.deviceInfo.is_binyue_cool_model) {
    return '吉利缤越 COOL (2022款)';
  }
  const name = store.deviceInfo.platform_name || '';
  if (name.includes('帝豪 L')) return '吉利帝豪 L';
  if (name.includes('缤越 L')) return '吉利缤越 L';
  if (name.includes('缤越')) return '吉利缤越 COOL (2022款)';
  return '吉利缤越 COOL (2022款)';
});

const tankCapacity = computed(() => {
  const name = store.deviceInfo.platform_name || '';
  if (name.includes('帝豪 L')) return 52;
  if (name.includes('缤越 L')) return 51;
  return 45; // 缤越 COOL 2022款 官方标称容积 45L
});

// 实时油位与动态加满计算
const currentFuelPercent = ref(-1);
const hasFuelSignal = computed(() => {
  return currentFuelPercent.value >= 0 && store.deviceInfo.is_car_device;
});

const currentFuelLiters = computed(() => {
  if (!hasFuelSignal.value) return '--';
  return ((tankCapacity.value * currentFuelPercent.value) / 100).toFixed(1);
});
const fillGapLiters = computed(() => {
  if (!hasFuelSignal.value) return tankCapacity.value;
  return (tankCapacity.value - Number(currentFuelLiters.value)).toFixed(1);
});
const dynamicFillCost92 = computed(() => {
  const p = currentOilData.value?.p92 || 7.55;
  return (p * Number(fillGapLiters.value)).toFixed(1);
});
const dynamicFillCost95 = computed(() => {
  const p = currentOilData.value?.p95 || 8.05;
  return (p * Number(fillGapLiters.value)).toFixed(1);
});

const displayCost92 = computed(() => {
  if (hasFuelSignal.value) return dynamicFillCost92.value;
  const p = currentOilData.value?.p92 || 7.55;
  return (p * tankCapacity.value).toFixed(1);
});

const displayCost95 = computed(() => {
  if (hasFuelSignal.value) return dynamicFillCost95.value;
  const p = currentOilData.value?.p95 || 8.05;
  return (p * tankCapacity.value).toFixed(1);
});

// 油价计算属性
const currentProvinceName = computed(() => store.oilPrice.selectedProvince || '北京');
const currentOilData = computed(() => {
  const p = currentProvinceName.value;
  return store.oilPrice.regionalPrices[p] || store.oilPrice.regionalPrices['北京'];
});
const currentOilP92 = computed(() => currentOilData.value?.p92 ? currentOilData.value.p92.toFixed(2) : '--');
const currentOilP95 = computed(() => currentOilData.value?.p95 ? currentOilData.value.p95.toFixed(2) : '--');
const oilDaysText = computed(() => {
  if (!store.oilPrice.nextAdjustment?.date) return '同步中';
  const res = getDaysToAdjustment(store.oilPrice.nextAdjustment.date);
  if (res.isPassed) return '今日调价';
  if (res.days > 0) return `距调价仅剩 ${res.days} 天`;
  if (res.hours > 0) return `距调价仅剩 ${res.hours} 小时`;
  return '今晚调价';
});

function onRefreshOilPrice() {
  refreshOilPrices(true);
}

function showTankCalcHelp() {
  const p92 = currentOilData.value?.p92 || 7.55;
  const p95 = currentOilData.value?.p95 || 8.05;
  const cap = tankCapacity.value;
  const cost92 = (p92 * cap).toFixed(1);
  const cost95 = (p95 * cap).toFixed(1);
  const lit200_92 = (200 / p92).toFixed(1);
  const lit200_95 = (200 / p95).toFixed(1);

  let descText = '';
  if (hasFuelSignal.value) {
    const remLiters = currentFuelLiters.value;
    const remPercent = currentFuelPercent.value;
    const gap = fillGapLiters.value;
    const dyn92 = dynamicFillCost92.value;
    const dyn95 = dynamicFillCost95.value;
    descText = `1. 实车动态加满补能公式（当前生效）：\n【公式】：(油箱总容积 - 剩余升数) × 当日油价\n・实车油量：剩余约 ${remPercent}% (约 ${remLiters} L)，距离加满还差 ${gap} L\n・92# 补满需：${gap}L × ¥${p92}/L = ¥${dyn92} 元\n・95# 补满需：${gap}L × ¥${p95}/L = ¥${dyn95} 元\n\n2. 日常加 200 元可加油量公式：\n【公式】：200元 ÷ 当日油价\n・92# 加200元：200 ÷ ¥${p92} = 约 ${lit200_92} 升\n・95# 加200元：200 ÷ ¥${p95} = 约 ${lit200_95} 升\n\n3. 车型标称容积说明：\n・缤越 COOL (2022款)：官方油箱容积 45L\n・吉利帝豪 L：官方油箱容积 52L\n・吉利缤越 L：官方油箱容积 51L`;
  } else {
    descText = `1. 动态加满机制说明：\n当前处于模拟器或尚未读取到底盘燃油信号状态，界面展示【整箱加满基准】；真车点火连通底盘 CAN 总线后，将自动秒级切换为【实车动态补满计算】。\n\n2. 空箱整箱基准金额公式：\n【公式】：油箱标称总容积 × 当日油价\n・92# 整箱：${cap}L × ¥${p92}/L = ¥${cost92} 元\n・95# 整箱：${cap}L × ¥${p95}/L = ¥${cost95} 元\n\n3. 日常加 200 元可加油量公式：\n【公式】：200元 ÷ 当日油价\n・92# 加200元：200 ÷ ¥${p92} = 约 ${lit200_92} 升\n・95# 加200元：200 ÷ ¥${p95} = 约 ${lit200_95} 升\n\n4. 车型标称容积自适应：\n・缤越 COOL (2022款)：45L\n・吉利帝豪 L：52L\n・吉利缤越 L：51L`;
  }

  openModal('confirm', {
    title: '【计算公式】油箱加注与成本核算逻辑',
    desc: descText,
    tip: '所有油价均以当前所选省份发改委官方最高限价核算。',
    showCancel: false,
    confirmText: '我知道了'
  });
}
</script>
