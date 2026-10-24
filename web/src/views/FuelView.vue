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

    <!-- Tab 2: 智能加油账本 (核心高密度真实流水与数据台账) -->
    <div v-if="activeTab === 'record'" class="flex flex-col space-y-4">
      <!-- 顶部高密度仪表盘：核心三指标与快捷操作 -->
      <div class="p-4 rounded-3xl bg-car-card border-2 border-car-border shadow-xl flex items-center justify-between">
        <div class="flex items-center space-x-6">
          <div class="flex flex-col">
            <span class="text-[12.5px] text-car-sub font-bold">加权综合油耗 (跳枪法)</span>
            <div class="flex items-baseline space-x-1.5 mt-0.5">
              <span v-if="store.fuelLedger.stats.is_calculable" class="text-[26px] font-black text-emerald-400 font-mono leading-none">
                {{ store.fuelLedger.stats.avg_consumption }}
              </span>
              <span v-else class="text-[20px] font-black text-car-text leading-none">
                计算中
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
            <span>手机扫码同步</span>
          </button>
        </div>
      </div>

      <!-- 加油记录流水台账列表 (高对比度紧凑列表，拒绝大块空洞卡片) -->
      <div class="p-4 rounded-3xl bg-car-card border-2 border-car-border shadow-xl flex flex-col space-y-3">
        <!-- 月度聚合统计清单 (按月收敛，车机轻量化秒览) -->
        <div v-if="store.fuelLedger.monthly_stats && store.fuelLedger.monthly_stats.length > 0" class="flex flex-col space-y-1.5 pb-2 border-b border-car-border/60">
          <div class="flex items-center justify-between text-[13px] text-car-sub font-bold px-1">
            <span>月度花费汇总清单 (按月聚合)</span>
            <span class="text-car-accent">点击月份可单月过滤 · 车机最多留存 30 笔精细流水</span>
          </div>

          <div class="grid grid-cols-3 gap-2.5">
            <div 
              @click="toggleMonthFilter('')"
              class="px-3.5 py-2 rounded-xl border cursor-pointer transition-all flex items-center justify-between"
              :class="selectedMonth === '' ? 'bg-car-accent-light border-car-accent text-car-accent' : 'bg-car-item border-car-border text-car-text hover:border-car-border-light'"
            >
              <div class="flex flex-col">
                <span class="text-[14px] font-black leading-tight">全部流水</span>
                <span class="text-[11.5px] text-car-sub font-bold mt-0.5">本地共 {{ store.fuelLedger.records.length }} 笔</span>
              </div>
              <span class="text-[13px] font-mono font-black">¥{{ store.fuelLedger.stats.total_spend || '0.0' }}</span>
            </div>

            <div 
              v-for="m in store.fuelLedger.monthly_stats" 
              :key="m.month"
              @click="toggleMonthFilter(m.month)"
              class="px-3.5 py-2 rounded-xl border cursor-pointer transition-all flex items-center justify-between"
              :class="selectedMonth === m.month ? 'bg-car-accent-light border-car-accent text-car-accent' : 'bg-car-item border-car-border text-car-text hover:border-car-border-light'"
            >
              <div class="flex flex-col">
                <div class="flex items-center space-x-1">
                  <span class="text-[14px] font-black leading-tight">{{ m.month }}</span>
                  <span class="text-[11px] px-1.5 py-0.2 rounded bg-car-card border border-car-border text-car-sub font-bold">{{ m.count }}次</span>
                </div>
                <span class="text-[11.5px] text-car-sub font-bold mt-0.5">共加 {{ m.total_liters }} L</span>
              </div>
              <span class="text-[14px] font-mono font-black text-car-accent">¥{{ m.total_paid }}</span>
            </div>
          </div>
        </div>

        <div class="flex items-center justify-between pb-1 text-[13px] text-car-sub font-bold px-1">
          <span>{{ selectedMonth ? `【${selectedMonth}】详细流水 (${filteredRecords.length} 笔)` : `近期加油流水 (${filteredRecords.length} 笔 · 滚动防卡顿)` }}</span>
          <span>点击任一记录可微调金额、油价、省份或删除</span>
        </div>

        <!-- 空状态提示 -->
        <div v-if="!filteredRecords || filteredRecords.length === 0" class="py-12 flex flex-col items-center justify-center space-y-3 text-center">
          <div class="w-12 h-12 rounded-full bg-car-item border border-car-border flex items-center justify-center text-car-sub text-[22px]">
            ⛽
          </div>
          <span class="text-[16px] font-black text-car-text">暂无加油台账记录</span>
          <span class="text-[13px] text-car-sub font-bold max-w-[480px]">
            点火连通底盘总线后，系统会在加油后自动感知记录；跑长途或外地加油也可直接点击右上角【+ 手动记一笔】快速补录。
          </span>
        </div>

        <!-- 紧凑流水列表条目 (高度 58px) -->
        <div 
          v-for="rec in filteredRecords" 
          :key="rec.id"
          @click="openEditRecordModal(rec)"
          class="min-h-[58px] px-4 py-2 rounded-2xl bg-car-item border border-car-border hover:border-car-accent/60 cursor-pointer transition-all flex items-center justify-between shadow-sm"
        >
          <div class="flex items-center space-x-5">
            <!-- 日期与总里程 -->
            <div class="flex flex-col min-w-[120px]">
              <span class="text-[15px] font-black text-car-text font-mono leading-tight">{{ rec.date }}</span>
              <span class="text-[12px] text-car-sub font-bold font-mono">{{ rec.odo ? rec.odo + ' km' : '里程未打点' }}</span>
            </div>

            <div class="h-8 w-px bg-car-border/70"></div>

            <!-- 加油规格与加满标签 -->
            <div class="flex items-center space-x-2">
              <span class="text-[16px] font-black text-car-text font-mono">加 {{ rec.liters }} L</span>
              <span class="text-[12.5px] px-2 py-0.5 rounded font-black border bg-car-card border-car-border text-car-sub">
                {{ rec.fuel_type || '92#' }} {{ rec.is_full ? '跳枪加满' : '阶段补油' }}
              </span>
            </div>

            <div class="h-8 w-px bg-car-border/70"></div>

            <!-- 单价与省份 -->
            <div class="flex flex-col">
              <span class="text-[13.5px] font-bold text-car-sub">
                ¥{{ rec.price ? rec.price.toFixed(2) : '--' }}/L
                <span class="text-[12px] text-car-sub/80">[{{ rec.province || currentProvinceName }}]</span>
              </span>
              <span v-if="rec.note" class="text-[11.5px] text-car-accent font-bold truncate max-w-[140px]">{{ rec.note }}</span>
            </div>
          </div>

          <div class="flex items-center space-x-5">
            <!-- 实付金额 -->
            <div class="flex flex-col text-right">
              <span class="text-[18px] font-black text-car-accent font-mono leading-tight">¥{{ rec.actual_paid ? rec.actual_paid.toFixed(1) : (rec.amount ? rec.amount.toFixed(1) : '0.0') }}</span>
              <span class="text-[11.5px] text-car-sub font-bold">{{ rec.source === 'auto' ? '底盘自动抓取' : '手动录入' }}</span>
            </div>

            <!-- 编辑操作图标 -->
            <div class="flex items-center space-x-2">
              <span class="px-3 py-1.5 rounded-lg bg-car-card border border-car-border text-car-sub text-[13px] font-bold hover:text-car-text">
                编辑/改
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Tab 3: 单次行程油耗账单 (行车打点机制) -->
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
            起步切 D 挡自动打点仪表盘总里程；停稳挂 P 挡结合发改委实时油价静默出账，杜绝语音扰民。
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
            <div>• <b>本地存储</b>：数据写入 /sdcard/GeelyPilot/fuel 共享存储，零强制网络依赖，应用卸载不丢账。</div>
          </div>
          <div class="pt-4 border-t border-car-border/50 text-[13px] text-car-accent font-black">
            状态：底层总线监听中 · 随时就绪
          </div>
        </div>
      </div>
    </div>

    <!-- 车规标准弹窗 1: 编辑/修改单笔加油台账 (接入助手统一 ModalWrapper，彻底告别黑底) -->
    <ModalWrapper
      :show="showEditModal"
      :title="'编辑加油台账 (ID: ' + editingRecord.id + ')'"
      badge="流水微调"
      maxWidthClass="max-w-[840px]"
      zIndexClass="z-[9990]"
      @close="showEditModal = false"
    >
      <div class="flex flex-col space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <!-- 实付金额 (车主最常用) -->
          <div class="flex flex-col space-y-1">
            <span class="text-[13px] text-car-sub font-bold">车主实付金额 (元) *</span>
            <input 
              type="number" 
              step="0.1" 
              v-model.number="editingRecord.actual_paid" 
              @input="onEditPaidChange"
              class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[18px] font-mono font-black focus:border-car-accent outline-none" 
            />
          </div>

          <!-- 加注升数 (L) -->
          <div class="flex flex-col space-y-1">
            <span class="text-[13px] text-car-sub font-bold">加注升数 (L) *</span>
            <input 
              type="number" 
              step="0.01" 
              v-model.number="editingRecord.liters" 
              @input="onEditLitersChange"
              class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[18px] font-mono font-black focus:border-car-accent outline-none" 
            />
          </div>

          <!-- 单价 (元/L) -->
          <div class="flex flex-col space-y-1">
            <span class="text-[13px] text-car-sub font-bold">实际单价 (元/L)</span>
            <input 
              type="number" 
              step="0.01" 
              v-model.number="editingRecord.price" 
              @input="onEditPriceChange"
              class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[18px] font-mono font-black focus:border-car-accent outline-none" 
            />
          </div>

          <!-- 仪表盘当前累计总里程 (大白话) -->
          <div class="flex flex-col space-y-1">
            <span class="text-[13px] text-car-sub font-bold">仪表盘总里程 (公里 km)</span>
            <input 
              type="number" 
              v-model.number="editingRecord.odo" 
              placeholder="仪表盘显示的累计行驶总里程"
              class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[18px] font-mono font-black focus:border-car-accent outline-none" 
            />
          </div>
        </div>

        <!-- 油品与加满状态 -->
        <div class="flex items-center space-x-3">
          <div class="flex flex-col space-y-1 flex-1 min-w-0">
            <span class="text-[13px] text-car-sub font-bold">油品标号</span>
            <div class="flex space-x-2">
              <button
                v-for="ft in ['92#', '95#', '98#', '0#柴油']"
                :key="ft"
                type="button"
                @click="editingRecord.fuel_type = ft; onEditFuelTypeChange()"
                :class="[
                  'flex-1 min-h-[50px] rounded-xl border-2 font-black text-[14.5px] transition-all flex items-center justify-center cursor-pointer',
                  editingRecord.fuel_type === ft
                    ? 'bg-car-item border-car-accent text-car-accent'
                    : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
                ]"
              >
                {{ ft }}
              </button>
            </div>
          </div>

          <div class="flex flex-col space-y-1 flex-1 min-w-0">
            <span class="text-[13px] text-car-sub font-bold">加满状态</span>
            <button 
              type="button"
              @click="editingRecord.is_full = !editingRecord.is_full" 
              :class="[
                'min-h-[50px] px-4 rounded-xl border-2 font-black text-[15px] transition-all flex items-center justify-center cursor-pointer',
                editingRecord.is_full
                  ? 'bg-emerald-500/15 border-emerald-500 text-emerald-400'
                  : 'bg-car-card border-car-border text-car-sub'
              ]"
            >
              {{ editingRecord.is_full ? '✓ 跳枪加满' : '阶段加注 (未满)' }}
            </button>
          </div>
        </div>

        <!-- 平铺全国 31 个省市直选矩阵 (杜绝下拉框空白与多层点击) -->
        <div class="flex flex-col space-y-1.5 p-3 rounded-2xl bg-car-item border border-car-border">
          <div class="flex items-center justify-between text-[13px] text-car-sub font-bold px-1">
            <span>加油省份/直辖市 (平铺点选 · 当前: <b class="text-car-accent">{{ editingRecord.province }}</b>)</span>
            <span class="text-[12px] text-car-sub/80">点击自动对齐当地实时油价</span>
          </div>
          <div class="flex flex-wrap -m-0.5">
            <div 
              v-for="prov in allProvinceNames" 
              :key="prov" 
              class="w-[12.5%] p-0.5"
            >
              <button 
                type="button"
                @click="editingRecord.province = prov; onEditProvinceChange()"
                :class="[
                  'w-full min-h-[50px] rounded-lg border font-black text-[13.5px] cursor-pointer transition-all flex items-center justify-center truncate',
                  editingRecord.province === prov
                    ? 'bg-car-accent border-car-accent text-car-text'
                    : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light hover:text-car-text'
                ]"
              >
                {{ prov }}
              </button>
            </div>
          </div>
        </div>

        <!-- 备注：快捷标签免打字 + 选填输入 -->
        <div class="flex flex-col space-y-1.5">
          <div class="flex items-center justify-between text-[13px] text-car-sub font-bold">
            <span>备注说明 (免打字快捷点击，也支持后续在手机端随时修改)</span>
          </div>
          <div class="flex space-x-2">
            <button 
              v-for="tag in ['中石化优惠', '中石油加注', '周五降价日', '高速服务区', '长途自驾']" 
              :key="tag"
              type="button"
              @click="editingRecord.note = tag"
              class="min-h-[50px] px-3 rounded-lg bg-car-card border border-car-border text-car-sub hover:text-car-accent hover:border-car-accent text-[12.5px] font-bold cursor-pointer"
            >
              + {{ tag }}
            </button>
          </div>
          <input 
            type="text" 
            v-model="editingRecord.note" 
            placeholder="也可手动输入备注，或留空在手机端直接编辑"
            class="h-[48px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[14.5px] font-bold focus:border-car-accent outline-none" 
          />
        </div>
      </div>

      <!-- 底部操作按钮 -->
      <template #footer>
        <button 
          type="button"
          @click="onDeleteCurrentRecord"
          class="h-[52px] px-5 rounded-xl border-2 border-rose-500/40 bg-rose-500/10 text-rose-400 font-black text-[15px] hover:bg-rose-500/20 transition-all cursor-pointer whitespace-nowrap mr-auto"
        >
          删除此记录
        </button>

        <button 
          type="button"
          @click="showEditModal = false"
          class="h-[52px] px-6 rounded-xl border-2 border-car-border bg-car-card text-car-sub font-black text-[15.5px] hover:text-car-text transition-all cursor-pointer whitespace-nowrap"
        >
          取消
        </button>
        <button 
          type="button"
          @click="onSaveEditedRecord"
          class="h-[52px] px-7 rounded-xl border-2 border-car-accent bg-car-item text-car-accent font-black text-[16px] hover:opacity-90 transition-all cursor-pointer shadow-lg whitespace-nowrap"
        >
          保存修改
        </button>
      </template>
    </ModalWrapper>

    <!-- 车规标准弹窗 2: 手动补录一笔加油台账 (接入助手统一 ModalWrapper) -->
    <ModalWrapper
      :show="showAddModal"
      title="手动补录一笔加油台账"
      badge="快速补录"
      maxWidthClass="max-w-[840px]"
      zIndexClass="z-[9990]"
      @close="showAddModal = false"
    >
      <div class="flex flex-col space-y-4">
        <!-- 快捷加注金额预设 -->
        <div class="flex items-center space-x-2">
          <span class="text-[13px] text-car-sub font-bold">快捷金额:</span>
          <button 
            v-for="amt in [100, 200, 300]" 
            :key="amt"
            type="button"
            @click="setQuickAmount(amt)"
            class="min-h-[50px] px-4 py-2 rounded-xl bg-car-card border border-car-border text-car-text font-mono font-black text-[14.5px] hover:border-car-accent flex items-center justify-center cursor-pointer"
          >
            ¥{{ amt }}
          </button>
          <button 
            type="button"
            @click="setQuickFull"
            class="min-h-[50px] px-4 py-2 rounded-xl bg-car-card border border-emerald-400/50 text-emerald-400 font-black text-[14px] hover:border-emerald-400 flex items-center justify-center cursor-pointer"
          >
            加满整箱
          </button>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <!-- 实付金额 -->
          <div class="flex flex-col space-y-1">
            <span class="text-[13px] text-car-sub font-bold">实付金额 (元) *</span>
            <input 
              type="number" 
              step="0.1" 
              v-model.number="newRecord.actual_paid" 
              @input="onNewPaidChange"
              placeholder="如：200"
              class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[18px] font-mono font-black focus:border-car-accent outline-none" 
            />
          </div>

          <!-- 加注升数 (L) -->
          <div class="flex flex-col space-y-1">
            <span class="text-[13px] text-car-sub font-bold">加注升数 (L) *</span>
            <input 
              type="number" 
              step="0.01" 
              v-model.number="newRecord.liters" 
              @input="onNewLitersChange"
              placeholder="如：26.5"
              class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[18px] font-mono font-black focus:border-car-accent outline-none" 
            />
          </div>

          <!-- 单价 (元/L) -->
          <div class="flex flex-col space-y-1">
            <span class="text-[13px] text-car-sub font-bold">实际单价 (元/L)</span>
            <input 
              type="number" 
              step="0.01" 
              v-model.number="newRecord.price" 
              @input="onNewPriceChange"
              class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[18px] font-mono font-black focus:border-car-accent outline-none" 
            />
          </div>

          <!-- 仪表盘当前累计总里程 (大白话) -->
          <div class="flex flex-col space-y-1">
            <span class="text-[13px] text-car-sub font-bold">仪表盘总里程 (公里 km)</span>
            <input 
              type="number" 
              v-model.number="newRecord.odo" 
              placeholder="如：35200"
              class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[18px] font-mono font-black focus:border-car-accent outline-none" 
            />
          </div>
        </div>

        <!-- 油品与加满状态 -->
        <div class="flex items-center space-x-3">
          <div class="flex flex-col space-y-1 flex-1 min-w-0">
            <span class="text-[13px] text-car-sub font-bold">油品标号</span>
            <div class="flex space-x-2">
              <button
                v-for="ft in ['92#', '95#', '98#', '0#柴油']"
                :key="ft"
                type="button"
                @click="newRecord.fuel_type = ft; onNewFuelTypeChange()"
                :class="[
                  'flex-1 min-h-[50px] rounded-xl border-2 font-black text-[14.5px] transition-all flex items-center justify-center cursor-pointer',
                  newRecord.fuel_type === ft
                    ? 'bg-car-item border-car-accent text-car-accent'
                    : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
                ]"
              >
                {{ ft }}
              </button>
            </div>
          </div>

          <div class="flex flex-col space-y-1 flex-1 min-w-0">
            <span class="text-[13px] text-car-sub font-bold">加满状态</span>
            <button 
              type="button"
              @click="newRecord.is_full = !newRecord.is_full" 
              :class="[
                'min-h-[50px] px-4 rounded-xl border-2 font-black text-[15px] transition-all flex items-center justify-center cursor-pointer',
                newRecord.is_full
                  ? 'bg-emerald-500/15 border-emerald-500 text-emerald-400'
                  : 'bg-car-card border-car-border text-car-sub'
              ]"
            >
              {{ newRecord.is_full ? '✓ 跳枪加满' : '阶段加注 (未满)' }}
            </button>
          </div>
        </div>

        <!-- 平铺全国 31 个省市直选矩阵 (杜绝下拉框空白与多层点击) -->
        <div class="flex flex-col space-y-1.5 p-3 rounded-2xl bg-car-item border border-car-border">
          <div class="flex items-center justify-between text-[13px] text-car-sub font-bold px-1">
            <span>加油省份/直辖市 (平铺点选 · 当前: <b class="text-car-accent">{{ newRecord.province }}</b>)</span>
            <span class="text-[12px] text-car-sub/80">点击自动对齐当地实时油价</span>
          </div>
          <div class="flex flex-wrap -m-0.5">
            <div 
              v-for="prov in allProvinceNames" 
              :key="prov" 
              class="w-[12.5%] p-0.5"
            >
              <button 
                type="button"
                @click="newRecord.province = prov; onNewProvinceChange()"
                :class="[
                  'w-full min-h-[50px] rounded-lg border font-black text-[13.5px] cursor-pointer transition-all flex items-center justify-center truncate',
                  newRecord.province === prov
                    ? 'bg-car-accent border-car-accent text-car-text'
                    : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light hover:text-car-text'
                ]"
              >
                {{ prov }}
              </button>
            </div>
          </div>
        </div>

        <!-- 备注：快捷标签免打字 + 选填输入 -->
        <div class="flex flex-col space-y-1.5">
          <div class="flex items-center justify-between text-[13px] text-car-sub font-bold">
            <span>备注说明 (免打字快捷点击，也支持后续在手机端随时修改)</span>
          </div>
          <div class="flex space-x-2">
            <button 
              v-for="tag in ['中石化优惠', '中石油加注', '周五降价日', '高速服务区', '长途自驾']" 
              :key="tag"
              type="button"
              @click="newRecord.note = tag"
              class="min-h-[50px] px-3 rounded-lg bg-car-card border border-car-border text-car-sub hover:text-car-accent hover:border-car-accent text-[12.5px] font-bold cursor-pointer"
            >
              + {{ tag }}
            </button>
          </div>
          <input 
            type="text" 
            v-model="newRecord.note" 
            placeholder="也可手动输入备注，或留空在手机端直接编辑"
            class="h-[48px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[14.5px] font-bold focus:border-car-accent outline-none" 
          />
        </div>
      </div>

      <template #footer>
        <button 
          type="button"
          @click="showAddModal = false"
          class="h-[52px] px-6 rounded-xl border-2 border-car-border bg-car-card text-car-sub font-black text-[15.5px] hover:text-car-text transition-all cursor-pointer whitespace-nowrap"
        >
          取消
        </button>
        <button 
          type="button"
          @click="onSaveNewRecord"
          class="h-[52px] px-8 rounded-xl border-2 border-car-accent bg-car-item text-car-accent font-black text-[16px] hover:opacity-90 transition-all cursor-pointer shadow-lg whitespace-nowrap"
        >
          确认记入账本
        </button>
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
  deleteFuelRecord 
} from '../store';
import SubTabs from '../components/SubTabs.vue';
import FeatureCard from '../components/FeatureCard.vue';
import HelpDot from '../components/HelpDot.vue';
import ModalWrapper from '../components/modals/ModalWrapper.vue';
import { getDaysToAdjustment } from '../utils/oilPriceData';

const activeTab = ref('record'); // 默认展示智能加油账本
const fuelTabs = [
  { id: 'record', name: '智能加油账本', tag: '数据台账' },
  { id: 'price', name: '实时油价日历', tag: '发改委限价' },
  { id: 'trip', name: '单次行程油耗', tag: '行车账单' }
];

// 月度聚合与单月过滤状态
const selectedMonth = ref('');

function toggleMonthFilter(month) {
  if (selectedMonth.value === month) {
    selectedMonth.value = '';
  } else {
    selectedMonth.value = month;
  }
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
  const name = store.deviceInfo.platform_name || '';
  if (name.includes('帝豪 L')) return 52;
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

// ----------------------------------------------------
// 编辑台账逻辑 (内聚弹窗)
// ----------------------------------------------------
const showEditModal = ref(false);
const editingRecord = ref({});

function openEditRecordModal(rec) {
  editingRecord.value = JSON.parse(JSON.stringify(rec));
  if (!editingRecord.value.province) {
    editingRecord.value.province = currentProvinceName.value;
  }
  showEditModal.value = true;
}

function onEditProvinceChange() {
  const prov = editingRecord.value.province;
  const pData = store.oilPrice.regionalPrices[prov];
  if (pData) {
    const fuelType = editingRecord.value.fuel_type || '92#';
    if (fuelType.includes('95')) editingRecord.value.price = pData.p95 || 8.05;
    else if (fuelType.includes('98')) editingRecord.value.price = pData.p98 || 9.10;
    else if (fuelType.includes('柴油') || fuelType.includes('0#')) editingRecord.value.price = pData.p0 || 7.20;
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
  if (editingRecord.value.price > 0 && editingRecord.value.actual_paid > 0) {
    editingRecord.value.liters = Math.round((editingRecord.value.actual_paid / editingRecord.value.price) * 100) / 100;
  }
}

function onEditLitersChange() {
  if (editingRecord.value.price > 0 && editingRecord.value.liters > 0) {
    editingRecord.value.actual_paid = Math.round(editingRecord.value.liters * editingRecord.value.price * 10) / 10;
  }
}

function onEditPriceChange() {
  if (editingRecord.value.liters > 0 && editingRecord.value.price > 0) {
    editingRecord.value.actual_paid = Math.round(editingRecord.value.liters * editingRecord.value.price * 10) / 10;
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
  newRecord.value = {
    actual_paid: initialPaid,
    liters: Math.round((initialPaid / p92) * 100) / 100,
    price: p92,
    province: currentProvinceName.value,
    fuel_type: '92#',
    is_full: false,
    odo: 0,
    note: '',
    source: 'manual'
  };
  showAddModal.value = true;
}

function setQuickAmount(amt) {
  newRecord.value.actual_paid = amt;
  newRecord.value.is_full = false;
  if (newRecord.value.price > 0) {
    newRecord.value.liters = Math.round((amt / newRecord.value.price) * 100) / 100;
  }
}

function setQuickFull() {
  newRecord.value.is_full = true;
  const gap = Number(fillGapLiters.value) || 35.0;
  newRecord.value.liters = gap;
  if (newRecord.value.price > 0) {
    newRecord.value.actual_paid = Math.round(gap * newRecord.value.price * 10) / 10;
  }
}

function onNewProvinceChange() {
  const prov = newRecord.value.province;
  const pData = store.oilPrice.regionalPrices[prov];
  if (pData) {
    const fuelType = newRecord.value.fuel_type || '92#';
    if (fuelType.includes('95')) newRecord.value.price = pData.p95 || 8.05;
    else if (fuelType.includes('98')) newRecord.value.price = pData.p98 || 9.10;
    else if (fuelType.includes('柴油') || fuelType.includes('0#')) newRecord.value.price = pData.p0 || 7.20;
    else newRecord.value.price = pData.p92 || 7.55;

    if (newRecord.value.actual_paid > 0 && newRecord.value.price > 0) {
      newRecord.value.liters = Math.round((newRecord.value.actual_paid / newRecord.value.price) * 100) / 100;
    }
  }
}

function onNewFuelTypeChange() {
  onNewProvinceChange();
}

function onNewPaidChange() {
  if (newRecord.value.price > 0 && newRecord.value.actual_paid > 0) {
    newRecord.value.liters = Math.round((newRecord.value.actual_paid / newRecord.value.price) * 100) / 100;
  }
}

function onNewLitersChange() {
  if (newRecord.value.price > 0 && newRecord.value.liters > 0) {
    newRecord.value.actual_paid = Math.round(newRecord.value.liters * newRecord.value.price * 10) / 10;
  }
}

function onNewPriceChange() {
  if (newRecord.value.actual_paid > 0 && newRecord.value.price > 0) {
    newRecord.value.liters = Math.round((newRecord.value.actual_paid / newRecord.value.price) * 100) / 100;
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

function showCloudPairModal() {
  const carId = store.fuelLedger.config.car_id || 'COOL_CAR';
  openModal('confirm', {
    title: '【手机云同步】爱车配对码',
    desc: `您的爱车专属配对码为：\n【 ${carId} 】\n\n手机端（微信或浏览器）打开专属页面后输入此配对码，即可离车查看实时油耗折线报表与语音智能修改。`,
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
