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
        helpText="1. 官方限价：&#10;基于国家发改委最新发布的各省成品油最高零售限价，同省同价。&#10;&#10;2. 调价倒计时：&#10;按照国家发改委 10 个工作日调价周期倒计时，提前提供涨跌预期预警。&#10;&#10;3. 省钱算账：&#10;结合当前车型标称油箱容积自动折算加满一箱所需成本变动。" helpTip="点击可查看全国 31 省份 92#/95#/98#/柴油价格及调价详情。"
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
              class="min-h-[56px] px-6 rounded-xl border-2 border-car-accent bg-car-item text-car-accent font-black text-[16.5px] cursor-pointer hover:border-car-accent transition-all shadow-md flex items-center whitespace-nowrap"
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
                    正在连接车辆 · 点火即显示
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

    <!-- Tab 2: 智能加油账本 (核心高密度真实流水与数据台账) -->
    <div v-if="activeTab === 'record'" class="flex flex-col space-y-4">
      <!-- 顶部高密度仪表盘：核心三指标与快捷操作 -->
      <div class="p-4 rounded-3xl bg-car-card border-2 border-car-border shadow-xl flex items-center justify-between">
        <div class="flex items-center space-x-6">
          <div class="flex flex-col">
            <span class="text-[12.5px] text-car-sub font-bold">综合百公里真实油耗</span>
            <div class="flex items-baseline space-x-1.5 mt-0.5">
              <span v-if="store.fuelLedger.stats.is_calculable" class="text-[26px] font-black text-emerald-400 font-mono leading-none">
                {{ store.fuelLedger.stats.avg_consumption }}
              </span>
              <span v-else class="text-[20px] font-black text-car-text leading-none">
                暂未出账
              </span>
              <span class="text-[13px] text-car-sub font-bold">
                {{ store.fuelLedger.stats.is_calculable ? 'L/100km' : `(${store.fuelLedger.stats.status_desc})` }}
              </span>
            </div>
          </div>

          <div class="h-10 w-px bg-car-border"></div>

          <div class="flex flex-col">
            <span class="text-[12.5px] text-car-sub font-bold">每公里平均花费</span>
            <div class="flex items-baseline space-x-1 mt-0.5">
              <span v-if="store.fuelLedger.stats.is_calculable" class="text-[22px] font-black text-amber-400 font-mono leading-none">
                ¥{{ store.fuelLedger.stats.cost_per_km }}
              </span>
              <span v-else class="text-[20px] font-black text-car-sub font-mono leading-none">--</span>
              <span class="text-[13px] text-car-sub font-bold">/km</span>
            </div>
          </div>

          <div class="h-10 w-px bg-car-border"></div>

          <div class="flex flex-col">
            <span class="text-[12.5px] text-car-sub font-bold">累计加油总支出 / 升数</span>
            <div class="flex items-baseline space-x-2 mt-0.5">
              <span class="text-[22px] font-black text-car-accent font-mono leading-none">¥{{ store.fuelLedger.stats.total_spend || '0.0' }}</span>
              <span class="text-[13px] text-car-sub font-bold">共 {{ store.fuelLedger.stats.total_liters || '0.0' }} L</span>
            </div>
          </div>
        </div>

        <div class="flex items-center space-x-3 shrink-0">
          <!-- 快速车牌卡片 / 录入入口 (大触控、高辨识度) -->
          <button 
            @click="showPlateModal = true"
            class="min-h-[52px] px-4 rounded-xl border-2 border-car-border bg-car-item text-car-text font-black text-[15px] cursor-pointer hover:border-car-accent transition-all shadow-md flex items-center space-x-2 whitespace-nowrap"
          >
            <span class="text-[12px] px-2 py-0.5 rounded bg-car-card border border-car-border text-car-sub">车牌</span>
            <span class="font-mono font-black text-car-accent">{{ store.fuelLedger.config.plate_number || '点此录入车牌' }}</span>
          </button>

          <button 
            @click="openAddRecordModal"
            class="min-h-[52px] px-5 rounded-xl border-2 border-car-accent bg-car-item text-car-accent font-black text-[15.5px] cursor-pointer hover:border-car-accent transition-all shadow-md flex items-center space-x-2 whitespace-nowrap"
          >
            <span>+ 手动记一笔</span>
          </button>

          <button 
            @click="showCloudPairModal"
            class="min-h-[52px] px-4 rounded-xl border-2 border-car-border bg-car-item text-car-sub font-black text-[14.5px] cursor-pointer hover:text-car-text hover:border-car-border-light transition-all shadow-md flex items-center space-x-1.5 whitespace-nowrap"
          >
            <span>手机扫码绑定</span>
          </button>
        </div>
      </div>

      <!-- 加油记录流水台账列表 (高对比度紧凑列表，拒绝大块空洞卡片) -->
      <div class="p-4 rounded-3xl bg-car-card border-2 border-car-border shadow-xl flex flex-col space-y-3">
        <!-- 顶栏：记账年份切换与状态栏 -->
        <div class="flex items-center justify-between pb-2 border-b border-car-border/60">
          <div class="flex items-center space-x-2">
            <span class="text-[13px] text-car-sub font-bold">台账年份:</span>
            <div class="flex items-center space-x-1.5">
              <div 
                v-for="yItem in availableYears" 
                :key="yItem.year"
                @click="onYearChange(yItem.year)"
                class="h-[36px] px-3.5 rounded-xl border cursor-pointer transition-all flex items-center justify-center font-black text-[13px] select-none shadow-sm"
                :class="(activeYear === yItem.year || (activeYear === '' && yItem.year === currentYear)) 
                  ? 'bg-car-accent-light border-car-accent text-car-accent' 
                  : 'bg-car-item border-car-border text-car-sub hover:text-car-text'"
              >
                {{ yItem.label }}
              </div>
            </div>
          </div>

          <div class="text-[12px] text-car-sub font-bold text-right hidden sm:block">
            <span v-if="!isPastYearView">本年支持查看详细记录与单月筛选 · 跨年后自动归档汇总</span>
            <span v-else class="text-car-accent">往年已汇总归档 · 仅展示年度总览</span>
          </div>
        </div>

        <!-- 往年年度归档总账视图 (无需琐碎流水小票，直接呈现年度宏观总账与月度收敛分布) -->
        <div v-if="isPastYearView && selectedPastYearData" class="flex flex-col space-y-3">
          <!-- 往年年度宏观总览卡片 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-accent/50 shadow-md flex items-center justify-between">
            <div class="flex items-center space-x-6">
              <div class="flex flex-col">
                <span class="text-[12px] text-car-sub font-bold">{{ selectedPastYearData.year }} 年度总支出</span>
                <span class="text-[22px] font-mono font-black text-car-accent">¥{{ selectedPastYearData.total_paid }}</span>
              </div>
              <div class="h-8 w-px bg-car-border/80"></div>
              <div class="flex flex-col">
                <span class="text-[12px] text-car-sub font-bold">年度加油总量</span>
                <span class="text-[20px] font-mono font-black text-car-text">{{ selectedPastYearData.total_liters }} <span class="text-[14px]">L</span></span>
              </div>
              <div class="h-8 w-px bg-car-border/80"></div>
              <div class="flex flex-col">
                <span class="text-[12px] text-car-sub font-bold">加油频次</span>
                <span class="text-[18px] font-mono font-black text-car-text">{{ selectedPastYearData.count }} <span class="text-[13px] text-car-sub">次 (加满 {{ selectedPastYearData.full_count || 0 }} 次)</span></span>
              </div>
              <div v-if="selectedPastYearData.distance > 0" class="h-8 w-px bg-car-border/80"></div>
              <div v-if="selectedPastYearData.distance > 0" class="flex flex-col">
                <span class="text-[12px] text-car-sub font-bold">年度里程跨度</span>
                <span class="text-[18px] font-mono font-black text-car-text">{{ selectedPastYearData.distance }} <span class="text-[13px] text-car-sub">km</span></span>
              </div>
            </div>

            <!-- 清除该年归档按钮 -->
            <button 
              @click="onClearPastYear(selectedPastYearData.year)"
              class="h-[50px] min-h-[50px] px-4 rounded-xl border-2 border-rose-500/40 bg-rose-500/10 text-rose-400 hover:bg-rose-500/20 text-[13px] font-bold cursor-pointer transition-colors whitespace-nowrap shadow-sm"
            >
              清空{{ selectedPastYearData.year }}年归档
            </button>
          </div>

          <!-- 往年月度收敛分布网格 -->
          <div class="flex flex-col space-y-2">
            <div class="flex items-center justify-between text-[13px] text-car-sub font-bold px-1">
              <span>{{ selectedPastYearData.year }} 年各月份用油分布 (已月度汇总)</span>
              <span>历史年份不保留零碎小票，只留完整月度台账</span>
            </div>

            <div v-if="selectedPastYearData.months && selectedPastYearData.months.length > 0" class="grid grid-cols-4 gap-2.5">
              <div 
                v-for="m in selectedPastYearData.months" 
                :key="m.month"
                class="p-3 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-1 shadow-sm"
              >
                <div class="flex items-center justify-between">
                  <span class="text-[14px] font-black text-car-text font-mono">{{ m.month_short || m.month }}</span>
                  <span class="text-[11px] px-1.5 py-0.5 rounded bg-car-card border border-car-border text-car-sub font-bold">{{ m.count }}次加油</span>
                </div>
                <div class="flex items-baseline justify-between pt-1">
                  <span class="text-[12px] text-car-sub font-bold">加油量: <span class="text-car-text font-mono font-black">{{ m.liters }}</span> L</span>
                  <span class="text-[15px] font-mono font-black text-car-accent">¥{{ m.paid }}</span>
                </div>
              </div>
            </div>

            <div v-else class="py-8 text-center text-car-sub text-[13px] font-bold bg-car-item rounded-2xl border border-car-border">
              该年度暂无细分月度汇总数据
            </div>
          </div>
        </div>

        <!-- 本年度常规详细流水视图 -->
        <template v-else>
        <!-- 月度聚合统计清单 (按月收敛，车机轻量化秒览) -->
        <div v-if="store.fuelLedger.monthly_stats && store.fuelLedger.monthly_stats.length > 0" class="flex flex-col space-y-1.5 pb-2 border-b border-car-border/60">
          <div class="flex items-center justify-between text-[13px] text-car-sub font-bold px-1">
            <span>月度花费汇总清单 (按月汇总)</span>
            <span class="text-car-accent">单击筛选 · 双击管理/校准本月</span>
          </div>

          <div class="flex flex-wrap -m-1">
            <div class="p-1">
              <div 
                @click="toggleMonthFilter('')"
                class="h-[50px] px-3.5 rounded-xl border cursor-pointer transition-all flex items-center space-x-2.5 shadow-sm"
                :class="selectedMonth === '' ? 'bg-car-accent-light border-car-accent text-car-accent' : 'bg-car-item border-car-border text-car-text hover:border-car-border-light'"
              >
                <div class="flex flex-col">
                  <span class="text-[13px] font-black leading-tight whitespace-nowrap">全部汇总</span>
                  <span class="text-[11px] text-car-sub font-bold whitespace-nowrap">共 {{ store.fuelLedger.records.length }} 笔</span>
                </div>
                <span class="text-[13.5px] font-mono font-black text-car-accent whitespace-nowrap">¥{{ store.fuelLedger.stats.total_spend || '0.0' }}</span>
              </div>
            </div>

            <div 
              v-for="m in store.fuelLedger.monthly_stats" 
              :key="m.month"
              class="p-1"
            >
              <div 
                @click="onMonthClick(m.month)"
                @dblclick="openMonthManageModal(m)"
                class="h-[50px] px-3.5 rounded-xl border cursor-pointer transition-all flex items-center space-x-3 shadow-sm select-none"
                :class="selectedMonth === m.month ? 'bg-car-accent-light border-car-accent text-car-accent' : 'bg-car-item border-car-border text-car-text hover:border-car-border-light'"
                :title="'单击筛选，双击管理/校准【' + m.month + '】账单'"
              >
                <div class="flex flex-col text-left">
                  <div class="flex items-center space-x-1">
                    <span class="text-[13px] font-black leading-tight whitespace-nowrap">{{ m.month }}</span>
                    <span class="text-[10.5px] px-1 rounded bg-car-card border border-car-border text-car-sub font-bold whitespace-nowrap">{{ m.count }}次</span>
                  </div>
                  <span class="text-[11px] text-car-sub font-bold whitespace-nowrap">共 {{ m.total_liters }} L</span>
                </div>
                <div class="flex flex-col text-right">
                  <span class="text-[14px] font-mono font-black text-car-accent whitespace-nowrap">¥{{ m.total_paid }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="flex items-center justify-between pb-1 text-[13px] text-car-sub font-bold px-1">
          <div class="flex items-center space-x-2">
            <span>{{ selectedMonth ? `【${selectedMonth}】详细流水 (${filteredRecords.length} 笔)` : `近期加油流水 (${filteredRecords.length} 笔 · 双列卡片)` }}</span>
            <!-- 未选单月时：清空全部 -->
            <span 
              v-if="!selectedMonth && store.fuelLedger.records && store.fuelLedger.records.length > 0"
              @click="clearAllRecords"
              class="px-2 py-0.5 rounded-lg border border-rose-500/30 bg-rose-500/10 text-rose-400 hover:bg-rose-500/20 text-[11px] font-bold cursor-pointer transition-colors"
            >
              清空全部记录
            </span>
            <!-- 选中单月时：智能联动为清空本月 (带笔数) -->
            <span 
              v-if="selectedMonth && filteredRecords.length > 0"
              @click="clearCurrentMonthRecords"
              class="px-2 py-0.5 rounded-lg border border-rose-500/40 bg-rose-500/15 text-rose-400 hover:bg-rose-500/25 text-[11px] font-bold cursor-pointer transition-colors"
            >
              清空【{{ selectedMonth }}】流水 ({{ filteredRecords.length }} 笔)
            </span>
            <!-- 选中单月时：提供快捷管理校准入口 -->
            <span 
              v-if="selectedMonth"
              @click="openSelectedMonthManage"
              class="px-2 py-0.5 rounded-lg border border-car-border bg-car-item text-car-sub hover:text-car-text text-[11px] font-bold cursor-pointer transition-colors"
            >
              校准本月账单
            </span>
          </div>
          <span>点击卡片微调或删除 · 双击月份胶囊管理月度</span>
        </div>

        <!-- 空状态提示 -->
        <div v-if="!filteredRecords || filteredRecords.length === 0" class="py-12 flex flex-col items-center justify-center space-y-3 text-center">
          <div class="w-12 h-12 rounded-full bg-car-item border border-car-border flex items-center justify-center text-car-sub">
            <span class="font-black text-car-accent text-[18px]">油</span>
          </div>
          <span class="text-[16px] font-black text-car-text">暂无加油台账记录</span>
          <span class="text-[13px] text-car-sub font-bold max-w-[480px]">
            启动点火后，系统会在每次加油后自动记录；平时手动加油也可直接点击右上角【+ 手动记一笔】补录。
          </span>
        </div>

        <!-- 双列流水卡片列表 (左右两张卡片并排，高密度，消灭单行拉长留白；限制高度内滚动，彻底杜绝穿底越界) -->
        <div v-else class="max-h-[380px] overflow-y-auto p-1 pb-4">
          <div class="grid grid-cols-2 gap-2.5">
            <div 
              v-for="rec in filteredRecords" 
              :key="rec.id"
              @click="openEditRecordModal(rec)"
              class="min-h-[58px] px-3.5 py-2.5 rounded-2xl bg-car-item border border-car-border hover:border-car-accent/60 cursor-pointer transition-all flex items-center justify-between shadow-sm"
            >
              <div class="flex items-center space-x-3 flex-1 min-w-0 mr-3">
                <!-- 日期与总里程 -->
                <div class="flex flex-col min-w-[95px] shrink-0">
                  <span class="text-[13.5px] font-black text-car-text font-mono leading-tight whitespace-nowrap">{{ rec.date }}</span>
                  <span class="text-[11.5px] text-car-sub font-bold font-mono whitespace-nowrap">{{ rec.odo ? rec.odo + ' km' : '未记里程' }}</span>
                </div>

                <div class="h-7 w-px bg-car-border/70 shrink-0"></div>

                <!-- 加油规格与加满标签 -->
                <div class="flex flex-col shrink-0">
                  <span class="text-[15px] font-black text-car-text font-mono leading-tight whitespace-nowrap">加 {{ rec.liters }} L</span>
                  <div class="flex items-center space-x-1 mt-0.5">
                    <span 
                      :class="rec.gas_station === '智能自动感知' ? 'border-amber-500/50 text-amber-400 bg-amber-500/10' : 'bg-car-card border-car-border text-car-sub'"
                      class="text-[10.5px] px-1.5 py-0.2 rounded font-black border whitespace-nowrap"
                    >
                      {{ rec.gas_station === '智能自动感知' ? '⚡自动' : (rec.fuel_type || '92#') + (rec.is_full ? '·满' : '') }}
                    </span>
                    <span class="text-[11.5px] text-car-sub font-mono font-bold whitespace-nowrap">¥{{ rec.price ? rec.price.toFixed(2) : '--' }}</span>
                  </div>
                </div>

                <!-- 中间备注标签展示区 (有备注才展示，支持自动折行并列展示，车规标准 margin 间距) -->
                <div v-if="rec.note && String(rec.note).trim()" class="flex-1 flex flex-wrap items-center min-w-0 pl-1">
                  <span 
                    v-for="(tag, tIdx) in String(rec.note).trim().split(/\s+/).filter(Boolean)" 
                    :key="tIdx"
                    class="text-[10px] px-1.5 py-0.5 m-0.5 rounded-md bg-car-card/90 border border-car-accent/40 text-car-accent font-bold whitespace-nowrap"
                  >
                    {{ tag }}
                  </span>
                </div>
              </div>

              <!-- 右侧：实付金额与编辑入口 -->
              <div class="flex items-center space-x-3 shrink-0">
                <div class="flex flex-col text-right">
                  <span class="text-[17px] font-black text-car-accent font-mono leading-tight whitespace-nowrap">¥{{ rec.actual_paid ? rec.actual_paid.toFixed(1) : (rec.amount ? rec.amount.toFixed(1) : '0.0') }}</span>
                  <div class="flex items-center justify-end space-x-1">
                    <span v-if="Number(rec.coupon || 0) > 0" class="text-[10px] px-1 py-0.2 rounded bg-amber-500/15 border border-amber-500/40 text-amber-400 font-bold whitespace-nowrap">
                      省¥{{ Number(rec.coupon).toFixed(1) }}
                    </span>
                    <span class="text-[11px] text-car-sub font-bold whitespace-nowrap">{{ rec.gas_station === '智能自动感知' ? '自动' : '手动' }}</span>
                  </div>
                </div>

                <span class="px-2.5 py-1.5 rounded-lg bg-car-card border border-car-border text-car-sub text-[12px] font-bold hover:text-car-text whitespace-nowrap">
                  改
                </span>
              </div>
            </div>
          </div>
        </div>
        </template>
      </div>
    </div>

    <!-- 车规标准弹窗: 加油台账编辑与手动补录 (组件化独立，一页式全功能无滚动) -->
    <FuelRecordModal
      :showEditModal="showEditModal"
      :editingRecord="editingRecord"
      :showAddModal="showAddModal"
      :newRecord="newRecord"
      :currentOdo="store.fuelLedger.current_odo || 0"
      :allProvinceNames="allProvinceNames"
      @refresh-odo="store.fetchFuelLedgerData()"
      @close-edit="showEditModal = false"
      @save-edit="onSaveEditedRecord"
      @delete-record="onDeleteCurrentRecord"
      @edit-paid-change="onEditPaidChange"
      @edit-liters-change="onEditLitersChange"
      @edit-price-change="onEditPriceChange"
      @edit-fuel-type-change="onEditFuelTypeChange"
      @edit-province-change="onEditProvinceChange"
      @close-add="showAddModal = false"
      @save-new="onSaveNewRecord"
      @quick-amount="setQuickAmount"
      @quick-full="setQuickFull"
      @new-paid-change="onNewPaidChange"
      @new-liters-change="onNewLitersChange"
      @new-price-change="onNewPriceChange"
      @new-fuel-type-change="onNewFuelTypeChange"
      @new-province-change="onNewProvinceChange"
    />

    <!-- 车规大屏专属车牌录入弹窗 -->
    <LicensePlateModal
      :show="showPlateModal"
      :carId="store.fuelLedger.config.car_id || ''"
      :initialPlate="store.fuelLedger.config.plate_number || ''"
      @close="showPlateModal = false"
      @confirm="onPlateConfirmed"
    />

    <!-- 车规标准弹窗: 单月台账管理与校准 -->
    <ModalWrapper
      :show="showMonthModal"
      :title="`【${activeManagingMonth?.month || ''}】月度账单管理与校准`"
      badge="月度台账中枢"
      maxWidthClass="max-w-[820px]"
      zIndexClass="z-[9990]"
      @close="showMonthModal = false"
    >
      <div v-if="activeManagingMonth" class="flex flex-col space-y-4 py-1">
        <div class="grid grid-cols-3 gap-3">
          <div class="p-3.5 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-1 text-center">
            <span class="text-[13px] text-car-sub font-bold">本月总花费</span>
            <span class="text-[22px] font-mono font-black text-car-accent">¥{{ activeManagingMonth.total_paid }}</span>
          </div>
          <div class="p-3.5 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-1 text-center">
            <span class="text-[13px] text-car-sub font-bold">本月加注升数</span>
            <span class="text-[22px] font-mono font-black text-car-text">{{ activeManagingMonth.total_liters }} L</span>
          </div>
          <div class="p-3.5 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-1 text-center">
            <span class="text-[13px] text-car-sub font-bold">本月加油笔数</span>
            <span class="text-[22px] font-mono font-black text-car-text">{{ activeManagingMonth.count }} 次</span>
          </div>
        </div>

        <div class="text-[13.5px] text-car-sub font-bold bg-car-item/70 p-3 rounded-xl border border-car-border leading-relaxed">
          说明：本月数据由当前本地明细与历史汇总合并生成。若本月存在漏记的发票或想统一校准，可直接修改下方实付总金额与总升数；亦可仅清空本月的所有记录，其他月份不受任何影响。
        </div>

        <!-- 手动校准输入区 -->
        <div class="flex flex-col space-y-2 p-3.5 rounded-2xl bg-car-item border border-car-border">
          <span class="text-[14.5px] font-black text-car-text">手动校准当月总额 (可选)：</span>
          <div class="grid grid-cols-2 gap-3">
            <div class="flex flex-col space-y-1">
              <span class="text-[12px] text-car-sub font-bold">校准总花费 (元)</span>
              <input 
                v-model.number="adjustPaidInput" 
                type="number" 
                step="0.1" 
                class="h-[48px] px-3 rounded-xl bg-car-card border border-car-border text-car-text text-[18px] font-mono font-black focus:border-car-accent outline-none"
              />
            </div>
            <div class="flex flex-col space-y-1">
              <span class="text-[12px] text-car-sub font-bold">校准总升数 (L)</span>
              <input 
                v-model.number="adjustLitersInput" 
                type="number" 
                step="0.1" 
                class="h-[48px] px-3 rounded-xl bg-car-card border border-car-border text-car-text text-[18px] font-mono font-black focus:border-car-accent outline-none"
              />
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="flex items-center justify-between w-full">
          <button 
            type="button"
            @click="onConfirmClearMonth"
            class="min-h-[56px] px-5 rounded-2xl border-2 border-rose-500/40 bg-rose-500/10 text-rose-400 font-black text-[16px] hover:bg-rose-500/20 active:scale-95 transition-all cursor-pointer"
          >
            清空本月台账
          </button>
          <div class="flex items-center space-x-3">
            <button 
              type="button"
              @click="showMonthModal = false"
              class="min-h-[56px] px-6 rounded-2xl bg-car-item border-2 border-car-border text-car-sub font-black text-[16px] hover:text-car-text active:scale-95 transition-all cursor-pointer"
            >
              取消
            </button>
            <button 
              type="button"
              @click="onSaveMonthAdjust"
              class="min-h-[56px] px-7 rounded-2xl border-2 border-car-accent bg-car-item text-car-accent font-black text-[16px] hover:opacity-90 active:scale-95 transition-all cursor-pointer shadow-md"
            >
              保存校准
            </button>
          </div>
        </div>
      </template>
    </ModalWrapper>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { 
  store, 
  openModal, 
  refreshOilPrices, 
  loadFuelData, 
  addFuelRecord, 
  updateFuelRecord, 
  deleteFuelRecord,
  clearFuelRecords,
  clearMonthlyRecords,
  clearYearlyArchive,
  saveMonthlyAdjustment
} from '../store';
import SubTabs from '../components/SubTabs.vue';
import FeatureCard from '../components/FeatureCard.vue';
import HelpDot from '../components/HelpDot.vue';
import ModalWrapper from '../components/modals/ModalWrapper.vue';
import LicensePlateModal from '../components/modals/LicensePlateModal.vue';
import FuelRecordModal from '../components/modals/FuelRecordModal.vue';
import { getDaysToAdjustment } from '../utils/oilPriceData';

const activeTab = ref('price'); // 默认优先展示实时油价日历，符合日常高频查看需求
const fuelTabs = [
  { id: 'price', name: '实时油价日历', tag: '发改委限价' },
  { id: 'record', name: '智能加油账本', tag: '数据台账' }
];

// 月度聚合与单月过滤状态
const selectedMonth = ref('');
let clickTimer = null;

// 年度切换状态 (默认为空表示当前本年)
const activeYear = ref('');
const currentYear = computed(() => store.fuelLedger.current_year || new Date().getFullYear().toString());

const availableYears = computed(() => {
  const list = [{ year: currentYear.value, label: `${currentYear.value}年 (本年)` }];
  if (store.fuelLedger.yearly_stats && store.fuelLedger.yearly_stats.length > 0) {
    store.fuelLedger.yearly_stats.forEach(y => {
      if (y.year !== currentYear.value) {
        list.push({ year: y.year, label: `${y.year}年 (归档)` });
      }
    });
  }
  return list;
});

const isPastYearView = computed(() => {
  return activeYear.value !== '' && activeYear.value !== currentYear.value;
});

const selectedPastYearData = computed(() => {
  if (!isPastYearView.value) return null;
  return store.fuelLedger.yearly_stats?.find(y => y.year === activeYear.value) || null;
});

function onYearChange(yr) {
  activeYear.value = yr;
  selectedMonth.value = '';
}

function onClearPastYear(year) {
  openModal('confirm', {
    title: `确认清空【${year}年度】历史归档？`,
    desc: `清空后【${year}年】的历史年度总账与月度分布将被彻底抹除，不可恢复。`,
    confirmText: '确定清空',
    isDanger: true,
    onConfirm: () => {
      clearYearlyArchive(year);
      activeYear.value = '';
    }
  });
}

// 单击筛选 (防抖防止与双击冲突)
function onMonthClick(month) {
  if (clickTimer) {
    clearTimeout(clickTimer);
    clickTimer = null;
    return;
  }
  clickTimer = setTimeout(() => {
    toggleMonthFilter(month);
    clickTimer = null;
  }, 250);
}

function toggleMonthFilter(month) {
  if (selectedMonth.value === month) {
    selectedMonth.value = '';
  } else {
    selectedMonth.value = month;
  }
}

// 单月管理与校准弹窗状态
const showMonthModal = ref(false);
const activeManagingMonth = ref(null);
const adjustPaidInput = ref(0);
const adjustLitersInput = ref(0);

function openMonthManageModal(m) {
  if (clickTimer) {
    clearTimeout(clickTimer);
    clickTimer = null;
  }
  activeManagingMonth.value = m;
  adjustPaidInput.value = m.total_paid || 0;
  adjustLitersInput.value = m.total_liters || 0;
  showMonthModal.value = true;
}

function openSelectedMonthManage() {
  if (!selectedMonth.value) return;
  const m = (store.fuelLedger.monthly_stats || []).find(item => item.month === selectedMonth.value);
  if (m) {
    openMonthManageModal(m);
  } else {
    openMonthManageModal({ month: selectedMonth.value, total_paid: 0, total_liters: 0, count: 0 });
  }
}

function onSaveMonthAdjust() {
  if (!activeManagingMonth.value) return;
  const mKey = activeManagingMonth.value.month;
  saveMonthlyAdjustment(mKey, adjustPaidInput.value, adjustLitersInput.value);
  showMonthModal.value = false;
}

function onConfirmClearMonth() {
  if (!activeManagingMonth.value) return;
  const mKey = activeManagingMonth.value.month;
  openModal('confirm', {
    title: `确认清空【${mKey}】月度台账？`,
    desc: `清空后该月份的历史流水明细及汇总基底将被彻底抹除，其他月份台账完全不受影响。`,
    confirmText: '确定清空本月',
    isDanger: true,
    onConfirm: () => {
      clearMonthlyRecords(mKey);
      showMonthModal.value = false;
      if (selectedMonth.value === mKey) {
        selectedMonth.value = '';
      }
    }
  });
}

function clearCurrentMonthRecords() {
  if (!selectedMonth.value) return;
  const mKey = selectedMonth.value;
  openModal('confirm', {
    title: `确认清空【${mKey}】加油流水？`,
    desc: `清空后【${mKey}】月份的全部加油记录将被抹除，其他月份台账完全不受影响。`,
    confirmText: '确定清空本月',
    isDanger: true,
    onConfirm: () => {
      clearMonthlyRecords(mKey);
      selectedMonth.value = '';
    }
  });
}

const filteredRecords = computed(() => {
  const all = store.fuelLedger.records || [];
  if (!selectedMonth.value) return all;
  return all.filter(r => (r.date || '').startsWith(selectedMonth.value));
});

// 全国所有省份名称列表 (支持跑长途异地加油单价)
const allProvinceNames = [
  '北京', '天津', '河北', '山西', '内蒙古', '辽宁', '吉林', '黑龙江',
  '上海', '江苏', '浙江', '安徽', '福建', '江西', '山东', '河南',
  '湖北', '湖南', '广东', '广西', '海南', '重庆', '四川', '贵州',
  '云南', '西藏', '陕西', '甘肃', '青海', '宁夏', '新疆'
];

onMounted(() => {
  loadFuelData();
});

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
  if (typeof store.deviceInfo.fuel_capacity === 'number' && store.deviceInfo.fuel_capacity > 0) {
    return store.deviceInfo.fuel_capacity;
  }
  const name = store.deviceInfo.platform_name || '';
  if (name.includes('帝豪 L') || name.includes('帝豪')) return 52;
  if (name.includes('缤越 L')) return 51;
  return 45; // 缤越 COOL 2022款 官方标称容积 45L
});

// 实时油位与动态加满计算
const currentFuelPercent = computed(() => {
  if (typeof store.deviceInfo.fuel_percent === 'number' && store.deviceInfo.fuel_percent >= 0) {
    return store.deviceInfo.fuel_percent;
  }
  return -1;
});
const hasFuelSignal = computed(() => {
  return currentFuelPercent.value >= 0;
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

// ----------------------------------------------------
// 编辑台账逻辑 (内聚弹窗)
// ----------------------------------------------------
const showEditModal = ref(false);
const showPlateModal = ref(false);
const editingRecord = ref({});

function openEditRecordModal(rec) {
  editingRecord.value = JSON.parse(JSON.stringify(rec));
  if (!editingRecord.value.province) {
    editingRecord.value.province = currentProvinceName.value;
  }
  showEditModal.value = true;
}

function clearAllRecords() {
  openModal('confirm', {
    title: '确认清空全部加油台账？',
    desc: '清空后所有历史加油流水与统计将被彻底抹除，恢复为空白台账，不可恢复。',
    confirmText: '确定清空',
    isDanger: true,
    onConfirm: () => {
      clearFuelRecords();
    }
  });
}

function onEditProvinceChange() {
  const prov = editingRecord.value.province;
  const pData = store.oilPrice.regionalPrices[prov];
  if (pData) {
    const fuelType = editingRecord.value.fuel_type || '92#';
    if (fuelType.includes('95')) editingRecord.value.price = pData.p95 || 8.05;
    else if (fuelType.includes('98')) editingRecord.value.price = pData.p98 || 9.10;
    else editingRecord.value.price = pData.p92 || 7.55;

    // 智能联动实付金额
    if (editingRecord.value.liters > 0) {
      editingRecord.value.actual_paid = Math.round(editingRecord.value.liters * editingRecord.value.price * 10) / 10;
    }
  }
}

function onEditFuelTypeChange() {
  onEditProvinceChange();
}

function onEditPaidChange() {
  const coupon = Number(editingRecord.value.coupon) || 0;
  const total = (Number(editingRecord.value.actual_paid) || 0) + coupon;
  if (editingRecord.value.price > 0 && total > 0) {
    editingRecord.value.liters = Math.round((total / editingRecord.value.price) * 100) / 100;
  }
}

function onEditLitersChange() {
  const coupon = Number(editingRecord.value.coupon) || 0;
  if (editingRecord.value.price > 0 && editingRecord.value.liters > 0) {
    const total = Math.round(editingRecord.value.liters * editingRecord.value.price * 10) / 10;
    editingRecord.value.actual_paid = Math.max(0, Math.round((total - coupon) * 10) / 10);
  }
}

function onEditPriceChange() {
  const coupon = Number(editingRecord.value.coupon) || 0;
  const total = (Number(editingRecord.value.actual_paid) || 0) + coupon;
  if (editingRecord.value.price > 0 && total > 0) {
    editingRecord.value.liters = Math.round((total / editingRecord.value.price) * 100) / 100;
  }
}

function onSaveEditedRecord() {
  if (!editingRecord.value.actual_paid || !editingRecord.value.liters) {
    openModal('confirm', {
      title: '参数未填完整',
      desc: '请确保实付金额与加注升数均大于 0。',
      showCancel: false,
      confirmText: '好的'
    });
    return;
  }
  updateFuelRecord(editingRecord.value);
  showEditModal.value = false;
}

function onDeleteCurrentRecord() {
  openModal('confirm', {
    title: '确认删除此记录？',
    desc: '删除后此笔加油台账将从车机中彻底移除，不可恢复。',
    confirmText: '确定删除',
    isDanger: true,
    onConfirm: () => {
      deleteFuelRecord(editingRecord.value.id);
      showEditModal.value = false;
    }
  });
}

// ----------------------------------------------------
// 新增台账逻辑 (内聚弹窗)
// ----------------------------------------------------
const showAddModal = ref(false);
const newRecord = ref({
  actual_paid: 200,
  coupon: 0,
  liters: 26.5,
  price: 7.55,
  province: '北京',
  fuel_type: '92#',
  is_full: false,
  odo: 0,
  note: '',
  source: 'manual'
});

function openAddRecordModal() {
  const p92 = currentOilData.value?.p92 || 7.55;
  const initialPaid = 200;
  const odoVal = Math.round(Number(store.fuelLedger.current_odo) || 0);
  newRecord.value = {
    actual_paid: initialPaid,
    coupon: 0,
    liters: Math.round((initialPaid / p92) * 100) / 100,
    price: p92,
    province: currentProvinceName.value,
    fuel_type: '92#',
    is_full: false,
    odo: odoVal > 0 ? odoVal : 0,
    note: '',
    source: 'manual'
  };
  showAddModal.value = true;
}

function setQuickAmount(amt) {
  newRecord.value.actual_paid = amt;
  newRecord.value.is_full = false;
  if (newRecord.value.price > 0) {
    const total = amt + (Number(newRecord.value.coupon) || 0);
    newRecord.value.liters = Math.round((total / newRecord.value.price) * 100) / 100;
  }
}

function setQuickFull() {
  newRecord.value.is_full = true;
  const gap = Number(fillGapLiters.value) || 35.0;
  newRecord.value.liters = gap;
  if (newRecord.value.price > 0) {
    const total = Math.round(gap * newRecord.value.price * 10) / 10;
    const coupon = Number(newRecord.value.coupon) || 0;
    newRecord.value.actual_paid = Math.max(0, Math.round((total - coupon) * 10) / 10);
  }
}

function onNewProvinceChange() {
  const prov = newRecord.value.province;
  const pData = store.oilPrice.regionalPrices[prov];
  if (pData) {
    const fuelType = newRecord.value.fuel_type || '92#';
    if (fuelType.includes('95')) newRecord.value.price = pData.p95 || 8.05;
    else if (fuelType.includes('98')) newRecord.value.price = pData.p98 || 9.10;
    else newRecord.value.price = pData.p92 || 7.55;

    if (newRecord.value.price > 0) {
      const total = (Number(newRecord.value.actual_paid) || 0) + (Number(newRecord.value.coupon) || 0);
      if (total > 0) {
        newRecord.value.liters = Math.round((total / newRecord.value.price) * 100) / 100;
      }
    }
  }
}

function onNewFuelTypeChange() {
  onNewProvinceChange();
}

function onNewPaidChange() {
  const coupon = Number(newRecord.value.coupon) || 0;
  const total = (Number(newRecord.value.actual_paid) || 0) + coupon;
  if (newRecord.value.price > 0 && total > 0) {
    newRecord.value.liters = Math.round((total / newRecord.value.price) * 100) / 100;
  }
}

function onNewLitersChange() {
  const coupon = Number(newRecord.value.coupon) || 0;
  if (newRecord.value.price > 0 && newRecord.value.liters > 0) {
    const total = Math.round(newRecord.value.liters * newRecord.value.price * 10) / 10;
    newRecord.value.actual_paid = Math.max(0, Math.round((total - coupon) * 10) / 10);
  }
}

function onNewPriceChange() {
  const coupon = Number(newRecord.value.coupon) || 0;
  const total = (Number(newRecord.value.actual_paid) || 0) + coupon;
  if (newRecord.value.price > 0 && total > 0) {
    newRecord.value.liters = Math.round((total / newRecord.value.price) * 100) / 100;
  }
}

function onSaveNewRecord() {
  if (!newRecord.value.actual_paid || !newRecord.value.liters) {
    openModal('confirm', {
      title: '参数未填完整',
      desc: '请确保实付金额与加注升数均大于 0。',
      showCancel: false,
      confirmText: '好的'
    });
    return;
  }
  addFuelRecord(newRecord.value);
  showAddModal.value = false;
}

function onPlateConfirmed(newPlate) {
  if (store.fuelLedger.config) {
    store.fuelLedger.config.plate_number = newPlate;
    saveFuelConfig(store.fuelLedger.config);
  }
  // 本地 localStorage 兜底秒级持久化，杜绝重开助手时桥接异步偶发为空
  try {
    localStorage.setItem('fuel_plate_number', newPlate);
  } catch (e) {}
  showPlateModal.value = false;
  setTimeout(() => {
    openModal('confirm', {
      title: '车牌绑定成功',
      desc: `已成功将车牌【 ${newPlate} 】绑定至当前出厂硬件芯片 (${store.fuelLedger.config.car_id || 'COOL'})。后续手机端扫码即可同步该车。`,
      showCancel: false,
      confirmText: '好的'
    });
  }, 100);
}

function showCloudPairModal() {
  const carId = store.fuelLedger.config.car_id || 'COOL_CAR';
  const plate = store.fuelLedger.config.plate_number || '未绑定车牌';
  openModal('confirm', {
    title: '【手机云同步】爱车专属配对',
    desc: `当前车辆识别身份：\n・出厂硬件唯一码：【 ${carId} 】\n・当前车牌号：【 ${plate} 】\n\n手机端打开专属车库页面后，扫码或输入此出厂码即可一键认领该车进您的车库。`,
    showCancel: false,
    confirmText: '我知道了'
  });
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
    descText = `1. 实车动态加满补能公式（当前生效）：\n【公式】：(油箱总容积 - 剩余升数) × 当日油价\n・实车油量：剩余约 ${remPercent}% (约 ${remLiters} L)，距离加满还差 ${gap} L\n・92# 补满需：${gap}L × ¥${p92}/L = ¥${dyn92} 元\n・95# 补满需：${gap}L × ¥${p95}/L = ¥${dyn95} 元\n\n2. 日常加 200 元可加油量公式：\n【公式】：200元 ÷ 当日油价\n・92# 加200元：200 ÷ ¥${p92} = 约 ${lit200_92} 升\n・95# 加200元：200 ÷ ¥${p95} = 约 ${lit200_95} 升\n\n3. 车型标称容积说明：\n・当前匹配车型：${vehicleModelName.value} (官方油箱容积 ${cap}L)\n・其他适配车型：吉利帝豪 L (52L)、吉利缤越 L (51L)、缤越 COOL (45L)`;
  } else {
    descText = '1. 动态加满说明：\n当前车辆尚未启动或正在读取油量，暂按【整箱加满】展示；车辆点火后，会自动按【当前实际油量】算出还需要补多少升。\n\n2. 空箱整箱基准金额公式：\n【公式】：油箱总容积 × 当日油价\n・92# 整箱：' + cap + 'L × ¥' + p92 + '/L = ¥' + cost92 + ' 元\n・95# 整箱：' + cap + 'L × ¥' + p95 + '/L = ¥' + cost95 + ' 元\n\n3. 日常加 200 元可加油量公式：\n【公式】：200元 ÷ 当日油价\n・92# 加200元：200 ÷ ¥' + p92 + ' = 约 ' + lit200_92 + ' 升\n・95# 加200元：200 ÷ ¥' + p95 + ' = 约 ' + lit200_95 + ' 升\n\n4. 适配车型标准油箱：\n・当前匹配车型：' + vehicleModelName.value + ' (官方油箱容积 ' + cap + 'L)\n・其他适配车型：吉利帝豪 L (52L)、吉利缤越 L (51L)、缤越 COOL (45L)';
  }

  openModal('confirm', {
    title: '【使用指南】油箱加注与花费估算',
    desc: descText,
    tip: '所有油价均以当前所选省份发改委官方最高限价核算。',
    showCancel: false,
    confirmText: '我知道了'
  });
}
</script>
