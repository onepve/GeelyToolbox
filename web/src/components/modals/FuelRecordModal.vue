<template>
  <div>
    <!-- 车规标准弹窗 1: 编辑/校准加油台账 -->
    <ModalWrapper
      :show="showEditModal"
      title="编辑与校对加油台账"
      badge="车载工作台"
      maxWidthClass="max-w-[1280px]"
      headerClass="min-h-[64px] px-6 py-2"
      bodyClass="p-4 space-y-3"
      zIndexClass="z-[9990]"
      @close="$emit('close-edit')"
    >
      <template #header-actions>
        <button 
          type="button"
          @click="$emit('delete-record')"
          class="min-h-[50px] px-4 rounded-xl border-2 border-rose-500/40 bg-rose-500/10 text-rose-400 font-black text-[14px] hover:bg-rose-500/20 active:scale-95 transition-all cursor-pointer whitespace-nowrap shadow-sm"
        >
          删除记录
        </button>
        <button 
          type="button"
          @click="$emit('save-edit')"
          class="min-h-[50px] px-6 rounded-xl border-2 border-car-accent bg-car-accent-bg text-car-accent font-black text-[16px] hover:opacity-90 active:scale-95 transition-all cursor-pointer shadow-md whitespace-nowrap"
        >
          保存修改
        </button>
      </template>

      <div v-if="editingRecord" class="flex flex-col space-y-3">
        <!-- 车规三段 Tab 导航栏 (分页收敛，杜绝上下滚动拖拽) -->
        <div class="flex items-center justify-between border-b border-car-border/80 pb-2.5">
          <div class="flex items-center space-x-2">
            <button
              v-for="tab in [
                { id: 'amount', label: '💰 金额与小键盘', desc: '原价/实付/优惠/升数/键盘' },
                { id: 'fuel', label: '⛽ 油品与加油站', desc: `${editingRecord.fuel_type || '92#'} · ${editingRecord.gas_station || '选择油站'}` },
                { id: 'note', label: '📝 实用功能备注', desc: editingRecord.note ? editingRecord.note : '通勤/洗车/发票等' }
              ]"
              :key="tab.id"
              type="button"
              @click="activeModalTab = tab.id"
              :class="[
                'min-h-[50px] px-4 py-1.5 rounded-xl border-2 flex items-center space-x-2 text-left cursor-pointer transition-all shadow-sm select-none',
                activeModalTab === tab.id
                  ? 'border-car-accent bg-car-accent-light text-car-accent'
                  : 'border-car-border bg-car-item text-car-sub hover:text-car-text hover:border-car-border-light'
              ]"
            >
              <div class="flex flex-col">
                <span class="text-[14px] font-black leading-tight">{{ tab.label }}</span>
                <span class="text-[11px] text-car-sub font-bold mt-0.5 max-w-[170px] truncate">{{ tab.desc }}</span>
              </div>
            </button>
          </div>

          <!-- 顶部紧凑公式看板 (实时反馈) -->
          <div class="hidden lg:flex items-center space-x-2 px-3 py-1.5 rounded-xl bg-car-item border border-car-border text-[12.5px] font-mono">
            <span class="text-car-sub font-bold">实际加油:</span>
            <span class="text-car-text font-black">¥{{ getOriginalTotal(editingRecord).toFixed(1) }}</span>
            <span class="text-car-sub font-bold">- 优惠:</span>
            <span class="text-car-accent font-black">¥{{ Number(editingRecord.coupon || 0).toFixed(1) }}</span>
            <span class="text-car-sub font-bold">= 实付:</span>
            <span class="text-car-accent font-black">¥{{ Number(editingRecord.actual_paid || 0).toFixed(1) }}</span>
            <span class="text-car-sub">({{ Number(editingRecord.liters || 0).toFixed(1) }}L)</span>
          </div>
        </div>

        <!-- Tab 1: 💰 金额与小键盘 (横向左右双翼，单屏全景录入) -->
        <div v-show="activeModalTab === 'amount'" class="grid grid-cols-12 gap-3.5 items-stretch">
          <!-- 左侧：6 个数值大槽位 (3+3 触控大格) -->
          <div class="col-span-6 flex flex-col space-y-2.5">
            <div class="flex items-center justify-between text-[12px] font-bold text-car-sub px-1">
              <span>点击槽位选中，在右侧数字小键盘直接输入：</span>
              <span class="text-car-accent">当前激活: {{ getFieldLabel(activeField) }}</span>
            </div>

            <!-- 第一排 3 槽位：实际加油(原价)、优惠减免、车主实付现金 -->
            <div class="grid grid-cols-3 gap-2">
              <!-- 槽位 1: 实际加油金额 (原价) -->
              <div
                @click="activeField = 'total_amount'"
                :class="[
                  'min-h-[56px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'total_amount'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[11px] text-car-sub font-bold">加油原价 (表显)</span>
                  <span class="text-[17px] font-mono font-black text-car-text leading-tight">
                    ¥{{ getOriginalTotal(editingRecord).toFixed(1) }}
                  </span>
                </div>
                <span class="text-[11px] font-bold text-car-sub font-mono">元</span>
              </div>

              <!-- 槽位 2: 优惠券抵扣 / 减免 -->
              <div
                @click="activeField = 'coupon'"
                :class="[
                  'min-h-[56px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'coupon'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[11px] text-car-sub font-bold">优惠立减 (券)</span>
                  <span class="text-[17px] font-mono font-black text-car-accent leading-tight">
                    -¥{{ Number(editingRecord.coupon || 0).toFixed(1) }}
                  </span>
                </div>
                <span class="text-[11px] font-bold text-car-sub font-mono">元</span>
              </div>

              <!-- 槽位 3: 实付现金 (大字高亮) -->
              <div
                @click="activeField = 'actual_paid'"
                :class="[
                  'min-h-[56px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'actual_paid'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[11px] text-car-accent font-bold">车主实付 (现金) *</span>
                  <span class="text-[18px] font-mono font-black text-car-accent leading-tight">
                    ¥{{ Number(editingRecord.actual_paid || 0).toFixed(1) }}
                  </span>
                </div>
                <span class="text-[11px] font-bold text-car-accent font-mono">元</span>
              </div>
            </div>

            <!-- 第二排 3 槽位：会员折扣、加注升数、成交单价 -->
            <div class="grid grid-cols-3 gap-2">
              <!-- 槽位 4: 会员折扣 (折) -->
              <div
                @click="activeField = 'discount'"
                :class="[
                  'min-h-[56px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'discount'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[11px] text-car-sub font-bold">会员折扣 (打折)</span>
                  <span class="text-[17px] font-mono font-black text-car-text leading-tight">
                    {{ (Number(editingRecord.discount || 10) >= 10 || Number(editingRecord.discount || 10) <= 0) ? '无打折' : Number(editingRecord.discount).toFixed(1) + ' 折' }}
                  </span>
                </div>
                <span class="text-[11px] font-bold text-car-sub font-mono">折</span>
              </div>

              <!-- 槽位 5: 加注升数 -->
              <div
                @click="activeField = 'liters'"
                :class="[
                  'min-h-[56px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'liters'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[11px] text-car-sub font-bold">加注升数 *</span>
                  <span class="text-[17px] font-mono font-black text-car-text leading-tight">
                    {{ Number(editingRecord.liters || 0).toFixed(2) }}
                  </span>
                </div>
                <span class="text-[11px] font-bold text-car-sub font-mono">L</span>
              </div>

              <!-- 槽位 6: 成交单价 -->
              <div
                @click="activeField = 'price'"
                :class="[
                  'min-h-[56px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'price'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[11px] text-car-sub font-bold">实际单价</span>
                  <span class="text-[17px] font-mono font-black text-car-text leading-tight">
                    ¥{{ Number(editingRecord.price || 0).toFixed(2) }}
                  </span>
                </div>
                <span class="text-[11px] font-bold text-car-sub font-mono">元/L</span>
              </div>
            </div>

            <!-- 第三排：仪表里程专属行 (带快速带入按键与实车里程刷新) -->
            <div
              @click="activeField = 'odo'"
              :class="[
                'min-h-[58px] px-3.5 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm select-none',
                activeField === 'odo'
                  ? 'border-car-accent bg-car-accent-light'
                  : 'border-car-border bg-car-card hover:border-car-border-light'
              ]"
            >
              <div class="flex items-center space-x-2">
                <span class="text-[15px]">📟</span>
                <span class="text-[12px] text-car-sub font-bold">记录时仪表总里程:</span>
                <span class="text-[17px] font-mono font-black text-car-text">
                  {{ editingRecord.odo ? editingRecord.odo + ' km' : '未记录 (点击小键盘输入)' }}
                </span>
              </div>
              <div class="flex items-center space-x-2">
                <button
                  type="button"
                  @click.stop="$emit('refresh-odo')"
                  class="min-h-[50px] px-3 rounded-lg border border-car-border bg-car-item text-car-sub hover:text-car-text hover:border-car-accent active:scale-95 transition-all text-[12px] font-bold cursor-pointer"
                  title="重新抓取实车仪表总里程"
                >
                  ⟳ 刷新实车
                </button>
                <button
                  type="button"
                  @click.stop="applyCurrentOdo(editingRecord)"
                  class="min-h-[50px] px-3.5 rounded-lg border-2 border-car-accent bg-car-accent-bg text-car-accent text-[12.5px] font-black hover:opacity-90 active:scale-95 transition-all cursor-pointer shadow-sm"
                >
                  带入实车最新里程{{ currentOdo > 0 ? ' (' + Math.round(currentOdo) + 'km)' : '' }}
                </button>
              </div>
            </div>

            <!-- 常用加油金额档位 (纯净标准档位，彻底剔除 250) -->
            <div class="flex flex-col space-y-1 pt-1">
              <div class="flex items-center justify-between px-1 text-[11px] font-bold text-car-sub">
                <span>常用加油金额快捷填入 (原价):</span>
                <span class="text-car-accent">点击自动扣减优惠并推算升数</span>
              </div>
              <div class="grid grid-cols-6 gap-1.5">
                <button
                  v-for="amt in [200, 220, 230, 240, 260, 300]"
                  :key="amt"
                  type="button"
                  @click="onQuickAmountSelect(editingRecord, amt, 'edit')"
                  class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item text-car-text hover:border-car-accent hover:text-car-accent font-mono text-[15px] font-black cursor-pointer transition-all flex items-center justify-center shadow-sm select-none"
                >
                  ¥{{ amt }}
                </button>
              </div>
            </div>

            <!-- 会员常用折扣率快捷选与自定义手动输入 -->
            <div class="flex flex-col space-y-1">
              <div class="flex items-center justify-between px-1 text-[11px] font-bold text-car-sub">
                <span>会员折扣快捷直选 / 手动输入:</span>
                <span class="text-car-sub/80">点击下方快捷项或手动输入任意折扣</span>
              </div>
              <div class="grid grid-cols-8 gap-1">
                <button
                  v-for="d in QUICK_DISCOUNTS"
                  :key="d.val"
                  type="button"
                  @click="onQuickDiscountSelect(editingRecord, d.val, 'edit')"
                  :class="[
                    'min-h-[50px] rounded-lg border-2 font-mono text-[12px] font-bold cursor-pointer transition-all flex items-center justify-center shadow-sm select-none',
                    isQuickDiscountActive(editingRecord, d.val)
                      ? 'border-car-accent bg-car-accent-light text-car-accent font-black ring-1 ring-car-accent/30'
                      : 'border-car-border bg-car-item text-car-text hover:border-car-accent/50 hover:text-car-accent'
                  ]"
                >
                  {{ d.label }}
                </button>
                <button
                  type="button"
                  @click="onSelectCustomDiscount(editingRecord, 'edit')"
                  :class="[
                    'min-h-[50px] rounded-lg border-2 text-[12px] cursor-pointer transition-all flex flex-col items-center justify-center shadow-sm select-none px-1',
                    isCustomDiscountActive(editingRecord)
                      ? 'border-car-accent bg-car-accent-light text-car-accent font-black ring-1 ring-car-accent/30'
                      : (activeField === 'discount'
                          ? 'border-car-accent bg-car-accent-light text-car-accent font-black'
                          : 'border-car-border bg-car-item text-car-text hover:border-car-accent/50 hover:text-car-accent')
                  ]"
                  title="点击在右侧小键盘手动输入任意折扣数字"
                >
                  <span class="font-black">{{ isCustomDiscountActive(editingRecord) ? editingRecord.discount + '折' : '✏️ 手动' }}</span>
                  <span class="text-[9.5px] opacity-75">自定义</span>
                </button>
              </div>
            </div>
          </div>

          <!-- 右侧：车规大号数字软键盘 (4x4 紧凑排列，无重复 200/300 按钮) -->
          <div class="col-span-6 flex flex-col justify-between p-3 rounded-2xl bg-car-card border-2 border-car-border shadow-md">
            <div class="flex items-center justify-between px-1 pb-2 border-b border-car-border/60 text-[12px] font-bold text-car-sub">
              <span>车载软键盘 · 当前修改: <b class="text-car-accent text-[13px]">{{ getFieldLabel(activeField) }}</b></span>
              <span class="text-[11px]">按键高度车规 ≥ 50px</span>
            </div>

            <!-- 4x4 纯净软键盘 -->
            <div class="grid grid-cols-4 gap-2 pt-2">
              <button 
                v-for="k in ['1', '2', '3']" 
                :key="k" 
                type="button" 
                @click="onInputKey(editingRecord, k, 'edit')" 
                class="min-h-[54px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[22px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm active:scale-95"
              >
                {{ k }}
              </button>
              <button 
                type="button" 
                @click="onBackspace(editingRecord, 'edit')" 
                class="min-h-[54px] rounded-xl border-2 border-rose-500/40 bg-rose-500/10 text-rose-400 font-black text-[15px] flex items-center justify-center cursor-pointer hover:bg-rose-500/20 active:scale-95 transition-all shadow-sm"
              >
                ⌫ 退格
              </button>

              <button 
                v-for="k in ['4', '5', '6']" 
                :key="k" 
                type="button" 
                @click="onInputKey(editingRecord, k, 'edit')" 
                class="min-h-[54px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[22px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm active:scale-95"
              >
                {{ k }}
              </button>
              <button 
                type="button" 
                @click="onClearCurrent(editingRecord, 'edit')" 
                class="min-h-[54px] rounded-xl border-2 border-car-border bg-car-item text-car-sub hover:text-car-text text-[14px] font-bold flex items-center justify-center cursor-pointer active:scale-95 transition-all shadow-sm"
              >
                清空
              </button>

              <button 
                v-for="k in ['7', '8', '9']" 
                :key="k" 
                type="button" 
                @click="onInputKey(editingRecord, k, 'edit')" 
                class="min-h-[54px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[22px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm active:scale-95"
              >
                {{ k }}
              </button>
              <button 
                type="button" 
                @click="onNextField" 
                class="min-h-[54px] rounded-xl border-2 border-car-accent/60 bg-car-item text-car-accent text-[14px] font-black flex items-center justify-center cursor-pointer active:scale-95 transition-all shadow-sm"
              >
                下项 ➔
              </button>

              <button 
                type="button" 
                @click="onInputKey(editingRecord, '.', 'edit')" 
                class="min-h-[54px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[24px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm active:scale-95"
              >
                .
              </button>
              <button 
                type="button" 
                @click="onInputKey(editingRecord, '0', 'edit')" 
                class="min-h-[54px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[22px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm active:scale-95"
              >
                0
              </button>
              <button 
                type="button" 
                @click="onInputKey(editingRecord, '00', 'edit')" 
                class="min-h-[54px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[17px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm active:scale-95"
              >
                00
              </button>
              <button 
                type="button" 
                @click="activeModalTab = 'fuel'" 
                class="min-h-[54px] rounded-xl border-2 border-car-accent bg-car-accent-light text-car-accent text-[15px] font-black flex items-center justify-center cursor-pointer active:scale-95 transition-all shadow-md hover:opacity-90"
              >
                油品 ➔
              </button>
            </div>

            <!-- 底部快速指引 -->
            <div class="mt-3 p-2.5 rounded-xl bg-car-item/70 border border-car-border text-[12px] text-car-sub flex items-center justify-between">
              <span>💡 提示：输入加油原价与优惠券后，系统将自动算出实付金额与加注升数。</span>
              <button 
                type="button" 
                @click="$emit('save-edit')" 
                class="px-3.5 py-1.5 rounded-lg border-2 border-car-accent bg-car-accent-light text-car-accent font-black text-[12px] hover:opacity-90 active:scale-95 transition-all cursor-pointer shadow-sm"
              >
                直接保存
              </button>
            </div>
          </div>
        </div>

        <!-- Tab 2: ⛽ 油品与加油站 (精简国营/民营/外资，省份主题色适配夜间) -->
        <div v-show="activeModalTab === 'fuel'" class="grid grid-cols-12 gap-3.5 items-stretch">
          <!-- 左侧：加注标号 + 加注状态 + 精简加油站 -->
          <div class="col-span-6 flex flex-col space-y-3 p-3.5 rounded-2xl bg-car-card border-2 border-car-border shadow-sm">
            <!-- 加注标号 -->
            <div class="flex flex-col space-y-1.5">
              <span class="text-[12px] font-bold text-car-sub px-0.5">加注汽柴油标号:</span>
              <div class="grid grid-cols-4 gap-2">
                <button
                  v-for="f in ['92#', '95#', '98#', '0# 柴油']"
                  :key="f"
                  type="button"
                  @click="onSelectFuelType(editingRecord, f, 'edit')"
                  :class="[
                    'min-h-[50px] rounded-xl border-2 font-mono text-[15px] font-black cursor-pointer transition-all flex items-center justify-center shadow-sm select-none',
                    editingRecord.fuel_type === f
                      ? 'border-car-accent bg-car-accent-light text-car-accent ring-1 ring-car-accent/30'
                      : 'border-car-border bg-car-item text-car-text hover:border-car-accent/50 hover:text-car-accent'
                  ]"
                >
                  {{ f }}
                </button>
              </div>
            </div>

            <!-- 加注状态：跳枪加满 vs 阶段补油 -->
            <div class="flex flex-col space-y-1.5 pt-1">
              <span class="text-[12px] font-bold text-car-sub px-0.5">本次加注工况 (用于计算百公里油耗):</span>
              <div class="grid grid-cols-2 gap-2">
                <div
                  @click="editingRecord.is_full = true"
                  :class="[
                    'min-h-[54px] p-2.5 rounded-xl border-2 cursor-pointer transition-all flex items-center justify-between shadow-sm select-none',
                    editingRecord.is_full
                      ? 'border-car-accent bg-car-accent-light'
                      : 'border-car-border bg-car-item hover:border-car-border-light'
                  ]"
                >
                  <div class="flex flex-col text-left">
                    <span :class="editingRecord.is_full ? 'text-car-accent' : 'text-car-text'" class="text-[14px] font-black">⛽ 跳枪加满</span>
                    <span class="text-[11px] text-car-sub font-bold">精准跳枪基准线</span>
                  </div>
                  <span :class="editingRecord.is_full ? 'border-2 border-car-accent bg-car-accent-light text-car-accent shadow-sm' : 'bg-car-card text-car-sub border border-car-border'" class="text-[11.5px] px-2.5 py-1 rounded-lg font-black">
                    {{ editingRecord.is_full ? '✓ 已标记' : '点此设为' }}
                  </span>
                </div>

                <div
                  @click="editingRecord.is_full = false"
                  :class="[
                    'min-h-[54px] p-2.5 rounded-xl border-2 cursor-pointer transition-all flex items-center justify-between shadow-sm select-none',
                    !editingRecord.is_full
                      ? 'border-car-accent bg-car-accent-light'
                      : 'border-car-border bg-car-item hover:border-car-border-light'
                  ]"
                >
                  <div class="flex flex-col text-left">
                    <span :class="!editingRecord.is_full ? 'text-car-accent' : 'text-car-text'" class="text-[14px] font-black">💧 阶段补油</span>
                    <span class="text-[11px] text-car-sub font-bold">普通日常按金额补充</span>
                  </div>
                  <span :class="!editingRecord.is_full ? 'border-2 border-car-accent bg-car-accent-light text-car-accent shadow-sm' : 'bg-car-card text-car-sub border border-car-border'" class="text-[11.5px] px-2.5 py-1 rounded-lg font-black">
                    {{ !editingRecord.is_full ? '✓ 已标记' : '点此设为' }}
                  </span>
                </div>
              </div>
            </div>

            <!-- 精简实用加油站选择 (去臃肿，保留高频国营/民营) -->
            <div class="flex flex-col space-y-1.5 pt-1">
              <div class="flex items-center justify-between px-0.5 text-[12px] font-bold text-car-sub">
                <span>所属加油站品牌 (已精简实用):</span>
                <span class="text-car-accent">{{ editingRecord.gas_station || '未指定' }}</span>
              </div>
              <div class="grid grid-cols-3 gap-2">
                <button
                  v-for="st in ['中国石化', '中国石油', '中国海油', '民营油站', '壳牌/外资', '其他加油站']"
                  :key="st"
                  type="button"
                  @click="editingRecord.gas_station = st"
                  :class="[
                    'min-h-[50px] rounded-xl border-2 font-bold text-[13.5px] cursor-pointer transition-all flex items-center justify-center shadow-sm select-none',
                    editingRecord.gas_station === st
                      ? 'border-car-accent bg-car-accent-light text-car-accent font-black ring-1 ring-car-accent/30'
                      : 'border-car-border bg-car-item text-car-text hover:border-car-accent/50 hover:text-car-accent'
                  ]"
                >
                  {{ st }}
                </button>
              </div>
            </div>
          </div>

          <!-- 右侧：省份切换与官方最高零售限价对齐 (夜间皮肤文字高对比度适配) -->
          <div class="col-span-6 flex flex-col justify-between p-3.5 rounded-2xl bg-car-card border-2 border-car-border shadow-sm">
            <div class="flex flex-col space-y-2">
              <div class="flex items-center justify-between pb-2 border-b border-car-border/60">
                <div class="flex items-center space-x-2">
                  <span class="text-[13px] font-black text-car-text">加油所在省份:</span>
                  <span class="px-2.5 py-1 rounded-lg border border-car-accent bg-car-accent-light text-car-accent font-black text-[13px]">
                    {{ editingRecord.province || '浙江' }}
                  </span>
                </div>
                <span class="text-[12px] text-car-sub font-bold">发改委限价联动</span>
              </div>

              <!-- 31 省市网格 (文字颜色全量采用 text-car-text，暗黑模式下清晰可见，无黑色看不清字体) -->
              <div class="flex flex-wrap -m-1 pt-1 max-h-[260px] overflow-y-auto">
                <div v-for="prov in allProvinceNames" :key="prov" class="w-[12.5%] p-1">
                  <button
                    type="button"
                    @click="onSelectProvince(editingRecord, prov, 'edit')"
                    :class="[
                      'w-full min-h-[50px] rounded-xl border-2 text-[13px] font-black cursor-pointer transition-all flex items-center justify-center select-none shadow-sm',
                      editingRecord.province === prov
                        ? 'border-car-accent bg-car-accent-light text-car-accent ring-1 ring-car-accent/30'
                        : 'border-car-border bg-car-item text-car-text hover:border-car-accent/50 hover:text-car-accent'
                    ]"
                  >
                    {{ prov }}
                  </button>
                </div>
              </div>
            </div>

            <div class="flex items-center justify-between pt-4 border-t border-car-border/60 text-[12px] text-car-sub">
              <span>选择省份将自动同步当地发改委最高限价至单价槽位。</span>
              <button 
                type="button" 
                @click="activeModalTab = 'note'" 
                class="px-3.5 py-1.5 rounded-xl border border-car-border bg-car-item text-car-accent font-bold hover:border-car-accent"
              >
                前往备注 ➔
              </button>
            </div>
          </div>
        </div>

        <!-- Tab 3: 📝 实用功能备注 (常见都能用上的高频标签) -->
        <div v-show="activeModalTab === 'note'" class="flex flex-col space-y-3 p-4 rounded-2xl bg-car-card border-2 border-car-border shadow-sm">
          <div class="flex items-center justify-between text-[12.5px] font-bold text-car-sub">
            <span>常见车主实用备注标签 (点击快捷添加/取消):</span>
            <button 
              type="button" 
              @click="onClearNote(editingRecord)" 
              class="px-2.5 py-1 rounded-lg border border-car-border text-car-sub hover:text-rose-400 text-[11.5px]"
            >
              清空备注
            </button>
          </div>

          <!-- 精简实用标签库 (3类高频场景，整齐统一接入主题色) -->
          <div class="grid grid-cols-3 gap-3">
            <!-- 分组 1: 出行场景 -->
            <div class="flex flex-col space-y-1.5 p-3 rounded-xl bg-car-item border border-car-border">
              <span class="text-[11.5px] font-black text-car-sub">🚗 出行场景</span>
              <div class="grid grid-cols-1 gap-1.5">
                <button
                  v-for="tag in ['日常通勤', '高速长途', '自驾出游']"
                  :key="tag"
                  type="button"
                  @click="toggleNoteTag(editingRecord, tag)"
                  :class="[
                    'min-h-[50px] rounded-lg border-2 text-[13px] font-bold cursor-pointer transition-all flex items-center justify-center select-none shadow-sm',
                    isTagSelected(editingRecord, tag)
                      ? 'border-car-accent bg-car-accent-light text-car-accent font-black ring-1 ring-car-accent/30'
                      : 'border-car-border bg-car-card text-car-text hover:border-car-accent/50 hover:text-car-accent'
                  ]"
                >
                  {{ isTagSelected(editingRecord, tag) ? '✓ ' + tag : tag }}
                </button>
              </div>
            </div>

            <!-- 分组 2: 优惠形式 -->
            <div class="flex flex-col space-y-1.5 p-3 rounded-xl bg-car-item border border-car-border">
              <span class="text-[11.5px] font-black text-car-sub">🏷️ 优惠形式</span>
              <div class="grid grid-cols-1 gap-1.5">
                <button
                  v-for="tag in ['周五特惠', '会员折扣', '银行立减']"
                  :key="tag"
                  type="button"
                  @click="toggleNoteTag(editingRecord, tag)"
                  :class="[
                    'min-h-[50px] rounded-lg border-2 text-[13px] font-bold cursor-pointer transition-all flex items-center justify-center select-none shadow-sm',
                    isTagSelected(editingRecord, tag)
                      ? 'border-car-accent bg-car-accent-light text-car-accent font-black ring-1 ring-car-accent/30'
                      : 'border-car-border bg-car-card text-car-text hover:border-car-accent/50 hover:text-car-accent'
                  ]"
                >
                  {{ isTagSelected(editingRecord, tag) ? '✓ ' + tag : tag }}
                </button>
              </div>
            </div>

            <!-- 分组 3: 便民服务 -->
            <div class="flex flex-col space-y-1.5 p-3 rounded-xl bg-car-item border border-car-border">
              <span class="text-[11.5px] font-black text-car-sub">🧰 便民服务</span>
              <div class="grid grid-cols-1 gap-1.5">
                <button
                  v-for="tag in ['免费洗车', '加玻璃水', '开具发票']"
                  :key="tag"
                  type="button"
                  @click="toggleNoteTag(editingRecord, tag)"
                  :class="[
                    'min-h-[50px] rounded-lg border-2 text-[13px] font-bold cursor-pointer transition-all flex items-center justify-center select-none shadow-sm',
                    isTagSelected(editingRecord, tag)
                      ? 'border-car-accent bg-car-accent-light text-car-accent font-black ring-1 ring-car-accent/30'
                      : 'border-car-border bg-car-card text-car-text hover:border-car-accent/50 hover:text-car-accent'
                  ]"
                >
                  {{ isTagSelected(editingRecord, tag) ? '✓ ' + tag : tag }}
                </button>
              </div>
            </div>
          </div>

          <!-- 备注预览框 (夜间模式高对比度底座) -->
          <div class="flex items-center space-x-2 p-3 rounded-xl bg-car-item border border-car-border">
            <span class="text-[12.5px] text-car-sub font-bold shrink-0">当前已选备注:</span>
            <div class="flex-1 flex flex-wrap items-center">
              <span 
                v-if="!editingRecord.note || !String(editingRecord.note).trim()" 
                class="text-[12px] text-car-sub/60"
              >
                (暂无备注，点击上方标签即可直接添加)
              </span>
              <span
                v-for="t in String(editingRecord.note || '').trim().split(/\s+/).filter(Boolean)"
                :key="t"
                class="px-2 py-0.5 m-0.5 rounded-md bg-car-accent-light border border-car-accent text-car-accent font-bold text-[11.5px]"
              >
                {{ t }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </ModalWrapper>

    <!-- 车规标准弹窗 2: 手动补录一笔加油台账 (与编辑共享相同的分页架构) -->
    <ModalWrapper
      :show="showAddModal"
      title="手动补录一笔加油台账"
      badge="车载工作台"
      maxWidthClass="max-w-[1280px]"
      headerClass="min-h-[64px] px-6 py-2"
      bodyClass="p-4 space-y-3"
      zIndexClass="z-[9990]"
      @close="$emit('close-add')"
    >
      <template #header-actions>
        <button 
          type="button"
          @click="$emit('close-add')"
          class="min-h-[50px] px-4 rounded-xl border-2 border-car-border bg-car-item text-car-sub font-black text-[14px] hover:text-car-text active:scale-95 transition-all cursor-pointer whitespace-nowrap shadow-sm"
        >
          取消
        </button>
        <button 
          type="button"
          @click="$emit('save-new')"
          class="min-h-[50px] px-6 rounded-xl border-2 border-car-accent bg-car-accent-bg text-car-accent font-black text-[16px] hover:opacity-90 active:scale-95 transition-all cursor-pointer shadow-md whitespace-nowrap"
        >
          确认保存入账
        </button>
      </template>

      <div v-if="newRecord" class="flex flex-col space-y-3">
        <!-- 车规三段 Tab 导航栏 (分页收敛，杜绝上下滚动拖拽) -->
        <div class="flex items-center justify-between border-b border-car-border/80 pb-2.5">
          <div class="flex items-center space-x-2">
            <button
              v-for="tab in [
                { id: 'amount', label: '💰 金额与小键盘', desc: '原价/实付/优惠/升数/键盘' },
                { id: 'fuel', label: '⛽ 油品与加油站', desc: `${newRecord.fuel_type || '92#'} · ${newRecord.gas_station || '选择油站'}` },
                { id: 'note', label: '📝 实用功能备注', desc: newRecord.note ? newRecord.note : '通勤/洗车/发票等' }
              ]"
              :key="tab.id"
              type="button"
              @click="activeModalTab = tab.id"
              :class="[
                'min-h-[50px] px-4 py-1.5 rounded-xl border-2 flex items-center space-x-2 text-left cursor-pointer transition-all shadow-sm select-none',
                activeModalTab === tab.id
                  ? 'border-car-accent bg-car-accent-light text-car-accent'
                  : 'border-car-border bg-car-item text-car-sub hover:text-car-text hover:border-car-border-light'
              ]"
            >
              <div class="flex flex-col">
                <span class="text-[14px] font-black leading-tight">{{ tab.label }}</span>
                <span class="text-[11px] text-car-sub font-bold mt-0.5 max-w-[170px] truncate">{{ tab.desc }}</span>
              </div>
            </button>
          </div>

          <!-- 顶部紧凑公式看板 (实时反馈) -->
          <div class="hidden lg:flex items-center space-x-2 px-3 py-1.5 rounded-xl bg-car-item border border-car-border text-[12.5px] font-mono">
            <span class="text-car-sub font-bold">实际加油:</span>
            <span class="text-car-text font-black">¥{{ getOriginalTotal(newRecord).toFixed(1) }}</span>
            <span class="text-car-sub font-bold">- 优惠:</span>
            <span class="text-car-accent font-black">¥{{ Number(newRecord.coupon || 0).toFixed(1) }}</span>
            <span class="text-car-sub font-bold">= 实付:</span>
            <span class="text-car-accent font-black">¥{{ Number(newRecord.actual_paid || 0).toFixed(1) }}</span>
            <span class="text-car-sub">({{ Number(newRecord.liters || 0).toFixed(1) }}L)</span>
          </div>
        </div>

        <!-- Tab 1: 💰 金额与小键盘 -->
        <div v-show="activeModalTab === 'amount'" class="grid grid-cols-12 gap-3.5 items-stretch">
          <!-- 左侧：6 个数值大槽位 (3+3 触控大格) -->
          <div class="col-span-6 flex flex-col space-y-2.5">
            <div class="flex items-center justify-between text-[12px] font-bold text-car-sub px-1">
              <span>点击槽位选中，在右侧数字小键盘直接输入：</span>
              <span class="text-car-accent">当前激活: {{ getFieldLabel(activeField) }}</span>
            </div>

            <!-- 第一排 3 槽位：实际加油(原价)、优惠减免、车主实付现金 -->
            <div class="grid grid-cols-3 gap-2">
              <div
                @click="activeField = 'total_amount'"
                :class="[
                  'min-h-[56px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'total_amount'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[11px] text-car-sub font-bold">加油原价 (表显)</span>
                  <span class="text-[17px] font-mono font-black text-car-text leading-tight">
                    ¥{{ getOriginalTotal(newRecord).toFixed(1) }}
                  </span>
                </div>
                <span class="text-[11px] font-bold text-car-sub font-mono">元</span>
              </div>

              <div
                @click="activeField = 'coupon'"
                :class="[
                  'min-h-[56px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'coupon'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[11px] text-car-sub font-bold">优惠立减 (券)</span>
                  <span class="text-[17px] font-mono font-black text-car-accent leading-tight">
                    -¥{{ Number(newRecord.coupon || 0).toFixed(1) }}
                  </span>
                </div>
                <span class="text-[11px] font-bold text-car-sub font-mono">元</span>
              </div>

              <div
                @click="activeField = 'actual_paid'"
                :class="[
                  'min-h-[56px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'actual_paid'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[11px] text-car-accent font-bold">车主实付 (现金) *</span>
                  <span class="text-[18px] font-mono font-black text-car-accent leading-tight">
                    ¥{{ Number(newRecord.actual_paid || 0).toFixed(1) }}
                  </span>
                </div>
                <span class="text-[11px] font-bold text-car-accent font-mono">元</span>
              </div>
            </div>

            <!-- 第二排 3 槽位：会员折扣、加注升数、成交单价 -->
            <div class="grid grid-cols-3 gap-2">
              <div
                @click="activeField = 'discount'"
                :class="[
                  'min-h-[56px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'discount'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[11px] text-car-sub font-bold">会员折扣 (打折)</span>
                  <span class="text-[17px] font-mono font-black text-car-text leading-tight">
                    {{ (Number(newRecord.discount || 10) >= 10 || Number(newRecord.discount || 10) <= 0) ? '无打折' : Number(newRecord.discount).toFixed(1) + ' 折' }}
                  </span>
                </div>
                <span class="text-[11px] font-bold text-car-sub font-mono">折</span>
              </div>

              <div
                @click="activeField = 'liters'"
                :class="[
                  'min-h-[56px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'liters'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[11px] text-car-sub font-bold">加注升数 *</span>
                  <span class="text-[17px] font-mono font-black text-car-text leading-tight">
                    {{ Number(newRecord.liters || 0).toFixed(2) }}
                  </span>
                </div>
                <span class="text-[11px] font-bold text-car-sub font-mono">L</span>
              </div>

              <div
                @click="activeField = 'price'"
                :class="[
                  'min-h-[56px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'price'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[11px] text-car-sub font-bold">实际单价</span>
                  <span class="text-[17px] font-mono font-black text-car-text leading-tight">
                    ¥{{ Number(newRecord.price || 0).toFixed(2) }}
                  </span>
                </div>
                <span class="text-[11px] font-bold text-car-sub font-mono">元/L</span>
              </div>
            </div>

            <!-- 第三排：仪表里程专属行 (带快速带入按键与实车里程刷新) -->
            <div
              @click="activeField = 'odo'"
              :class="[
                'min-h-[52px] px-3.5 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm select-none',
                activeField === 'odo'
                  ? 'border-car-accent bg-car-accent-light'
                  : 'border-car-border bg-car-card hover:border-car-border-light'
              ]"
            >
              <div class="flex items-center space-x-2">
                <span class="text-[15px]">📟</span>
                <span class="text-[12px] text-car-sub font-bold">记录时仪表总里程:</span>
                <span class="text-[17px] font-mono font-black text-car-text">
                  {{ newRecord.odo ? newRecord.odo + ' km' : '未记录 (点击小键盘输入)' }}
                </span>
              </div>
              <div class="flex items-center space-x-2">
                <button
                  type="button"
                  @click.stop="$emit('refresh-odo')"
                  class="min-h-[50px] px-3 rounded-lg border border-car-border bg-car-item text-car-sub hover:text-car-text hover:border-car-accent active:scale-95 transition-all text-[12px] font-bold cursor-pointer"
                  title="重新抓取实车仪表总里程"
                >
                  ⟳ 刷新实车
                </button>
                <button
                  type="button"
                  @click.stop="applyCurrentOdo(newRecord)"
                  class="min-h-[50px] px-3.5 rounded-lg border-2 border-car-accent bg-car-accent-bg text-car-accent text-[12.5px] font-black hover:opacity-90 active:scale-95 transition-all cursor-pointer shadow-sm"
                >
                  带入实车最新里程{{ currentOdo > 0 ? ' (' + Math.round(currentOdo) + 'km)' : '' }}
                </button>
              </div>
            </div>

            <!-- 常用加油金额档位 (纯净标准档位，彻底剔除 250) -->
            <div class="flex flex-col space-y-1 pt-1">
              <div class="flex items-center justify-between px-1 text-[11px] font-bold text-car-sub">
                <span>常用加油金额快捷填入 (原价):</span>
                <span class="text-car-accent">点击自动扣减优惠并推算升数</span>
              </div>
              <div class="grid grid-cols-6 gap-1.5">
                <button
                  v-for="amt in [200, 220, 230, 240, 260, 300]"
                  :key="amt"
                  type="button"
                  @click="onQuickAmountSelect(newRecord, amt, 'new')"
                  class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item text-car-text hover:border-car-accent hover:text-car-accent font-mono text-[15px] font-black cursor-pointer transition-all flex items-center justify-center shadow-sm select-none"
                >
                  ¥{{ amt }}
                </button>
              </div>
            </div>

            <!-- 会员常用折扣率快捷选与自定义手动输入 -->
            <div class="flex flex-col space-y-1">
              <div class="flex items-center justify-between px-1 text-[11px] font-bold text-car-sub">
                <span>会员折扣快捷直选 / 手动输入:</span>
                <span class="text-car-sub/80">点击下方快捷项或手动输入任意折扣</span>
              </div>
              <div class="grid grid-cols-8 gap-1">
                <button
                  v-for="d in QUICK_DISCOUNTS"
                  :key="d.val"
                  type="button"
                  @click="onQuickDiscountSelect(newRecord, d.val, 'new')"
                  :class="[
                    'min-h-[50px] rounded-lg border-2 font-mono text-[12px] font-bold cursor-pointer transition-all flex items-center justify-center shadow-sm select-none',
                    isQuickDiscountActive(newRecord, d.val)
                      ? 'border-car-accent bg-car-accent-light text-car-accent font-black ring-1 ring-car-accent/30'
                      : 'border-car-border bg-car-item text-car-text hover:border-car-accent/50 hover:text-car-accent'
                  ]"
                >
                  {{ d.label }}
                </button>
                <button
                  type="button"
                  @click="onSelectCustomDiscount(newRecord, 'new')"
                  :class="[
                    'min-h-[50px] rounded-lg border-2 text-[12px] cursor-pointer transition-all flex flex-col items-center justify-center shadow-sm select-none px-1',
                    isCustomDiscountActive(newRecord)
                      ? 'border-car-accent bg-car-accent-light text-car-accent font-black ring-1 ring-car-accent/30'
                      : (activeField === 'discount'
                          ? 'border-car-accent bg-car-accent-light text-car-accent font-black'
                          : 'border-car-border bg-car-item text-car-text hover:border-car-accent/50 hover:text-car-accent')
                  ]"
                  title="点击在右侧小键盘手动输入任意折扣数字"
                >
                  <span class="font-black">{{ isCustomDiscountActive(newRecord) ? newRecord.discount + '折' : '✏️ 手动' }}</span>
                  <span class="text-[9.5px] opacity-75">自定义</span>
                </button>
              </div>
            </div>
          </div>

          <!-- 右侧：车规大号数字软键盘 (4x4 纯净版，无重复 200/300 按钮) -->
          <div class="col-span-6 flex flex-col justify-between p-3 rounded-2xl bg-car-card border-2 border-car-border shadow-md">
            <div class="flex items-center justify-between px-1 pb-2 border-b border-car-border/60 text-[12px] font-bold text-car-sub">
              <span>车载软键盘 · 当前修改: <b class="text-car-accent text-[13px]">{{ getFieldLabel(activeField) }}</b></span>
              <span class="text-[11px]">按键高度车规 ≥ 50px</span>
            </div>

            <div class="grid grid-cols-4 gap-2 pt-2">
              <button 
                v-for="k in ['1', '2', '3']" 
                :key="k" 
                type="button" 
                @click="onInputKey(newRecord, k, 'new')" 
                class="min-h-[54px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[22px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm active:scale-95"
              >
                {{ k }}
              </button>
              <button 
                type="button" 
                @click="onBackspace(newRecord, 'new')" 
                class="min-h-[54px] rounded-xl border-2 border-rose-500/40 bg-rose-500/10 text-rose-400 font-black text-[15px] flex items-center justify-center cursor-pointer hover:bg-rose-500/20 active:scale-95 transition-all shadow-sm"
              >
                ⌫ 退格
              </button>

              <button 
                v-for="k in ['4', '5', '6']" 
                :key="k" 
                type="button" 
                @click="onInputKey(newRecord, k, 'new')" 
                class="min-h-[54px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[22px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm active:scale-95"
              >
                {{ k }}
              </button>
              <button 
                type="button" 
                @click="onClearCurrent(newRecord, 'new')" 
                class="min-h-[54px] rounded-xl border-2 border-car-border bg-car-item text-car-sub hover:text-car-text text-[14px] font-bold flex items-center justify-center cursor-pointer active:scale-95 transition-all shadow-sm"
              >
                清空
              </button>

              <button 
                v-for="k in ['7', '8', '9']" 
                :key="k" 
                type="button" 
                @click="onInputKey(newRecord, k, 'new')" 
                class="min-h-[54px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[22px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm active:scale-95"
              >
                {{ k }}
              </button>
              <button 
                type="button" 
                @click="onNextField" 
                class="min-h-[54px] rounded-xl border-2 border-car-accent/60 bg-car-item text-car-accent text-[14px] font-black flex items-center justify-center cursor-pointer active:scale-95 transition-all shadow-sm"
              >
                下项 ➔
              </button>

              <button 
                type="button" 
                @click="onInputKey(newRecord, '.', 'new')" 
                class="min-h-[54px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[24px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm active:scale-95"
              >
                .
              </button>
              <button 
                type="button" 
                @click="onInputKey(newRecord, '0', 'new')" 
                class="min-h-[54px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[22px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm active:scale-95"
              >
                0
              </button>
              <button 
                type="button" 
                @click="onInputKey(newRecord, '00', 'new')" 
                class="min-h-[54px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[17px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm active:scale-95"
              >
                00
              </button>
              <button 
                type="button" 
                @click="activeModalTab = 'fuel'" 
                class="min-h-[54px] rounded-xl border-2 border-car-accent bg-car-accent-light text-car-accent text-[15px] font-black flex items-center justify-center cursor-pointer active:scale-95 transition-all shadow-md hover:opacity-90"
              >
                油品 ➔
              </button>
            </div>

            <!-- 底部快速指引 -->
            <div class="mt-3 p-2.5 rounded-xl bg-car-item/70 border border-car-border text-[12px] text-car-sub flex items-center justify-between">
              <span>💡 提示：输入加油原价与优惠券后，系统将自动算出实付金额与加注升数。</span>
              <button 
                type="button" 
                @click="$emit('save-new')" 
                class="px-3.5 py-1.5 rounded-lg border-2 border-car-accent bg-car-accent-light text-car-accent font-black text-[12px] hover:opacity-90 active:scale-95 transition-all cursor-pointer shadow-sm"
              >
                直接入账
              </button>
            </div>
          </div>
        </div>

        <!-- Tab 2: ⛽ 油品与加油站 -->
        <div v-show="activeModalTab === 'fuel'" class="grid grid-cols-12 gap-3.5 items-stretch">
          <div class="col-span-6 flex flex-col space-y-3 p-3.5 rounded-2xl bg-car-card border-2 border-car-border shadow-sm">
            <!-- 加注标号 -->
            <div class="flex flex-col space-y-1.5">
              <span class="text-[12px] font-bold text-car-sub px-0.5">加注汽柴油标号:</span>
              <div class="grid grid-cols-4 gap-2">
                <button
                  v-for="f in ['92#', '95#', '98#', '0# 柴油']"
                  :key="f"
                  type="button"
                  @click="onSelectFuelType(newRecord, f, 'new')"
                  :class="[
                    'min-h-[50px] rounded-xl border-2 font-mono text-[15px] font-black cursor-pointer transition-all flex items-center justify-center shadow-sm select-none',
                    newRecord.fuel_type === f
                      ? 'border-car-accent bg-car-accent-light text-car-accent ring-1 ring-car-accent/30'
                      : 'border-car-border bg-car-item text-car-text hover:border-car-accent/50 hover:text-car-accent'
                  ]"
                >
                  {{ f }}
                </button>
              </div>
            </div>

            <!-- 加注状态 -->
            <div class="flex flex-col space-y-1.5 pt-1">
              <span class="text-[12px] font-bold text-car-sub px-0.5">本次加注工况 (用于计算百公里油耗):</span>
              <div class="grid grid-cols-2 gap-2">
                <div
                  @click="newRecord.is_full = true"
                  :class="[
                    'min-h-[54px] p-2.5 rounded-xl border-2 cursor-pointer transition-all flex items-center justify-between shadow-sm select-none',
                    newRecord.is_full
                      ? 'border-car-accent bg-car-accent-light'
                      : 'border-car-border bg-car-item hover:border-car-border-light'
                  ]"
                >
                  <div class="flex flex-col text-left">
                    <span :class="newRecord.is_full ? 'text-car-accent' : 'text-car-text'" class="text-[14px] font-black">⛽ 跳枪加满</span>
                    <span class="text-[11px] text-car-sub font-bold">精准跳枪基准线</span>
                  </div>
                  <span :class="newRecord.is_full ? 'border-2 border-car-accent bg-car-accent-light text-car-accent shadow-sm' : 'bg-car-card text-car-sub border border-car-border'" class="text-[11.5px] px-2.5 py-1 rounded-lg font-black">
                    {{ newRecord.is_full ? '✓ 已标记' : '点此设为' }}
                  </span>
                </div>

                <div
                  @click="newRecord.is_full = false"
                  :class="[
                    'min-h-[54px] p-2.5 rounded-xl border-2 cursor-pointer transition-all flex items-center justify-between shadow-sm select-none',
                    !newRecord.is_full
                      ? 'border-car-accent bg-car-accent-light'
                      : 'border-car-border bg-car-item hover:border-car-border-light'
                  ]"
                >
                  <div class="flex flex-col text-left">
                    <span :class="!newRecord.is_full ? 'text-car-accent' : 'text-car-text'" class="text-[14px] font-black">💧 阶段补油</span>
                    <span class="text-[11px] text-car-sub font-bold">普通日常按金额补充</span>
                  </div>
                  <span :class="!newRecord.is_full ? 'border-2 border-car-accent bg-car-accent-light text-car-accent shadow-sm' : 'bg-car-card text-car-sub border border-car-border'" class="text-[11.5px] px-2.5 py-1 rounded-lg font-black">
                    {{ !newRecord.is_full ? '✓ 已标记' : '点此设为' }}
                  </span>
                </div>
              </div>
            </div>

            <!-- 精简实用加油站选择 -->
            <div class="flex flex-col space-y-1.5 pt-1">
              <div class="flex items-center justify-between px-0.5 text-[12px] font-bold text-car-sub">
                <span>所属加油站品牌 (已精简实用):</span>
                <span class="text-car-accent">{{ newRecord.gas_station || '未指定' }}</span>
              </div>
              <div class="grid grid-cols-3 gap-2">
                <button
                  v-for="st in ['中国石化', '中国石油', '中国海油', '民营油站', '壳牌/外资', '其他加油站']"
                  :key="st"
                  type="button"
                  @click="newRecord.gas_station = st"
                  :class="[
                    'min-h-[50px] rounded-xl border-2 font-bold text-[13.5px] cursor-pointer transition-all flex items-center justify-center shadow-sm select-none',
                    newRecord.gas_station === st
                      ? 'border-car-accent bg-car-accent-light text-car-accent font-black ring-1 ring-car-accent/30'
                      : 'border-car-border bg-car-item text-car-text hover:border-car-accent/50 hover:text-car-accent'
                  ]"
                >
                  {{ st }}
                </button>
              </div>
            </div>
          </div>

          <!-- 右侧：省份切换与官方最高零售限价对齐 -->
          <div class="col-span-6 flex flex-col justify-between p-3.5 rounded-2xl bg-car-card border-2 border-car-border shadow-sm">
            <div class="flex flex-col space-y-2">
              <div class="flex items-center justify-between pb-2 border-b border-car-border/60">
                <div class="flex items-center space-x-2">
                  <span class="text-[13px] font-black text-car-text">加油所在省份:</span>
                  <span class="px-2.5 py-1 rounded-lg border border-car-accent bg-car-accent-light text-car-accent font-black text-[13px]">
                    {{ newRecord.province || '浙江' }}
                  </span>
                </div>
                <span class="text-[12px] text-car-sub font-bold">发改委限价联动</span>
              </div>

              <!-- 31 省市网格 -->
              <div class="flex flex-wrap -m-1 pt-1 max-h-[260px] overflow-y-auto">
                <div v-for="prov in allProvinceNames" :key="prov" class="w-[12.5%] p-1">
                  <button
                    type="button"
                    @click="onSelectProvince(newRecord, prov, 'new')"
                    :class="[
                      'w-full min-h-[50px] rounded-xl border-2 text-[13px] font-black cursor-pointer transition-all flex items-center justify-center select-none shadow-sm',
                      newRecord.province === prov
                        ? 'border-car-accent bg-car-accent-light text-car-accent ring-1 ring-car-accent/30'
                        : 'border-car-border bg-car-item text-car-text hover:border-car-accent/50 hover:text-car-accent'
                    ]"
                  >
                    {{ prov }}
                  </button>
                </div>
              </div>
            </div>

            <div class="flex items-center justify-between pt-4 border-t border-car-border/60 text-[12px] text-car-sub">
              <span>选择省份将自动同步当地发改委最高限价至单价槽位。</span>
              <button 
                type="button" 
                @click="activeModalTab = 'note'" 
                class="px-3.5 py-1.5 rounded-xl border border-car-border bg-car-item text-car-accent font-bold hover:border-car-accent"
              >
                前往备注 ➔
              </button>
            </div>
          </div>
        </div>

        <!-- Tab 3: 📝 实用功能备注 -->
        <div v-show="activeModalTab === 'note'" class="flex flex-col space-y-3 p-4 rounded-2xl bg-car-card border-2 border-car-border shadow-sm">
          <div class="flex items-center justify-between text-[12.5px] font-bold text-car-sub">
            <span>常见车主实用备注标签 (点击快捷添加/取消):</span>
            <button 
              type="button" 
              @click="onClearNote(newRecord)" 
              class="px-2.5 py-1 rounded-lg border border-car-border text-car-sub hover:text-rose-400 text-[11.5px]"
            >
              清空备注
            </button>
          </div>

          <div class="grid grid-cols-3 gap-3">
            <!-- 分组 1: 出行场景 -->
            <div class="flex flex-col space-y-1.5 p-3 rounded-xl bg-car-item border border-car-border">
              <span class="text-[11.5px] font-black text-car-sub">🚗 出行场景</span>
              <div class="grid grid-cols-1 gap-1.5">
                <button
                  v-for="tag in ['日常通勤', '高速长途', '自驾出游']"
                  :key="tag"
                  type="button"
                  @click="toggleNoteTag(newRecord, tag)"
                  :class="[
                    'min-h-[50px] rounded-lg border-2 text-[13px] font-bold cursor-pointer transition-all flex items-center justify-center select-none shadow-sm',
                    isTagSelected(newRecord, tag)
                      ? 'border-car-accent bg-car-accent-light text-car-accent font-black ring-1 ring-car-accent/30'
                      : 'border-car-border bg-car-card text-car-text hover:border-car-accent/50 hover:text-car-accent'
                  ]"
                >
                  {{ isTagSelected(newRecord, tag) ? '✓ ' + tag : tag }}
                </button>
              </div>
            </div>

            <!-- 分组 2: 优惠形式 -->
            <div class="flex flex-col space-y-1.5 p-3 rounded-xl bg-car-item border border-car-border">
              <span class="text-[11.5px] font-black text-car-sub">🏷️ 优惠形式</span>
              <div class="grid grid-cols-1 gap-1.5">
                <button
                  v-for="tag in ['周五特惠', '会员折扣', '银行立减']"
                  :key="tag"
                  type="button"
                  @click="toggleNoteTag(newRecord, tag)"
                  :class="[
                    'min-h-[50px] rounded-lg border-2 text-[13px] font-bold cursor-pointer transition-all flex items-center justify-center select-none shadow-sm',
                    isTagSelected(newRecord, tag)
                      ? 'border-car-accent bg-car-accent-light text-car-accent font-black ring-1 ring-car-accent/30'
                      : 'border-car-border bg-car-card text-car-text hover:border-car-accent/50 hover:text-car-accent'
                  ]"
                >
                  {{ isTagSelected(newRecord, tag) ? '✓ ' + tag : tag }}
                </button>
              </div>
            </div>

            <!-- 分组 3: 便民服务 -->
            <div class="flex flex-col space-y-1.5 p-3 rounded-xl bg-car-item border border-car-border">
              <span class="text-[11.5px] font-black text-car-sub">🧰 便民服务</span>
              <div class="grid grid-cols-1 gap-1.5">
                <button
                  v-for="tag in ['免费洗车', '加玻璃水', '开具发票']"
                  :key="tag"
                  type="button"
                  @click="toggleNoteTag(newRecord, tag)"
                  :class="[
                    'min-h-[50px] rounded-lg border-2 text-[13px] font-bold cursor-pointer transition-all flex items-center justify-center select-none shadow-sm',
                    isTagSelected(newRecord, tag)
                      ? 'border-car-accent bg-car-accent-light text-car-accent font-black ring-1 ring-car-accent/30'
                      : 'border-car-border bg-car-card text-car-text hover:border-car-accent/50 hover:text-car-accent'
                  ]"
                >
                  {{ isTagSelected(newRecord, tag) ? '✓ ' + tag : tag }}
                </button>
              </div>
            </div>
          </div>

          <div class="flex items-center space-x-2 p-3 rounded-xl bg-car-item border border-car-border">
            <span class="text-[12.5px] text-car-sub font-bold shrink-0">当前已选备注:</span>
            <div class="flex-1 flex flex-wrap items-center">
              <span 
                v-if="!newRecord.note || !String(newRecord.note).trim()" 
                class="text-[12px] text-car-sub/60"
              >
                (暂无备注，点击上方标签即可直接添加)
              </span>
              <span
                v-for="t in String(newRecord.note || '').trim().split(/\s+/).filter(Boolean)"
                :key="t"
                class="px-2 py-0.5 m-0.5 rounded-md bg-car-accent-light border border-car-accent text-car-accent font-bold text-[11.5px]"
              >
                {{ t }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </ModalWrapper>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import ModalWrapper from './ModalWrapper.vue';

const props = defineProps({
  showEditModal: Boolean,
  editingRecord: {
    type: Object,
    default: () => ({})
  },
  showAddModal: Boolean,
  newRecord: {
    type: Object,
    default: () => ({})
  },
  currentOdo: {
    type: [Number, String],
    default: 0
  },
  allProvinceNames: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits([
  'close-edit',
  'save-edit',
  'delete-record',
  'edit-paid-change',
  'edit-liters-change',
  'edit-price-change',
  'edit-fuel-type-change',
  'edit-province-change',
  'close-add',
  'save-new',
  'quick-amount',
  'quick-full',
  'new-paid-change',
  'new-liters-change',
  'new-price-change',
  'new-fuel-type-change',
  'new-province-change',
  'refresh-odo'
]);

// 弹窗内的 3 个分页 Tab: 'amount' | 'fuel' | 'note'
const activeModalTab = ref('amount');

// 当前选中的输入字段: 'total_amount' | 'actual_paid' | 'coupon' | 'discount' | 'liters' | 'price' | 'odo'
const activeField = ref('total_amount');

// 监听弹窗打开，重置聚焦到实际加油金额 (原价)，默认切到第一页
watch(() => props.showEditModal, (val) => {
  if (val) {
    activeModalTab.value = 'amount';
    activeField.value = 'total_amount';
  }
});

watch(() => props.showAddModal, (val) => {
  if (val) {
    activeModalTab.value = 'amount';
    activeField.value = 'total_amount';
    if (props.newRecord && (!props.newRecord.odo || props.newRecord.odo === 0) && props.currentOdo > 0) {
      props.newRecord.odo = Math.round(Number(props.currentOdo));
    }
  }
});

// 计算加油原价总值 (表显实际加油金额)
function getOriginalTotal(record) {
  if (!record) return 0;
  const paid = Number(record.actual_paid || 0);
  const coupon = Number(record.coupon || 0);
  const discount = Number(record.discount || 10);
  
  if (discount > 0 && discount < 10) {
    // 若有折扣，原价 = (实付 + 券) / (discount / 10)
    return parseFloat(((paid + coupon) / (discount / 10.0)).toFixed(1));
  }
  return parseFloat((paid + coupon).toFixed(1));
}

// 标签映射
function getFieldLabel(f) {
  switch (f) {
    case 'total_amount': return '加油原价 (表显)';
    case 'actual_paid': return '车主实付 (现金)';
    case 'coupon': return '优惠立减 (券)';
    case 'discount': return '会员折扣 (折)';
    case 'liters': return '加注升数 (L)';
    case 'price': return '成交单价 (元/L)';
    case 'odo': return '仪表总里程 (km)';
    default: return '数值输入';
  }
}

// 带入最新实车里程
function applyCurrentOdo(record) {
  if (!record) return;
  const odoVal = Math.round(Number(props.currentOdo) || 0);
  if (odoVal > 0) {
    record.odo = odoVal;
  }
}

// 选择油品
function onSelectFuelType(record, type, mode) {
  if (!record) return;
  record.fuel_type = type;
  if (mode === 'edit') emit('edit-fuel-type-change');
  else emit('new-fuel-type-change');
}

// 选择省份
function onSelectProvince(record, prov, mode) {
  if (!record) return;
  record.province = prov;
  if (mode === 'edit') emit('edit-province-change');
  else emit('new-province-change');
}

// 快捷充值档位选择 (加了多少原价油)
function onQuickAmountSelect(record, amt, mode) {
  if (!record) return;
  // 保持当前优惠减免与折扣，重新推导实付与升数
  const coupon = Number(record.coupon || 0);
  const discount = Number(record.discount || 10);
  const price = Number(record.price || 0);

  // 设定原价总额为 amt (如 240 元)
  let paid = amt;
  if (discount > 0 && discount < 10) {
    paid = amt * (discount / 10.0);
  }
  paid = Math.max(0, parseFloat((paid - coupon).toFixed(1)));
  record.actual_paid = paid;

  if (price > 0) {
    record.liters = parseFloat((amt / price).toFixed(2));
  }

  if (mode === 'edit') emit('edit-paid-change');
  else emit('new-paid-change');
}

// 会员快捷折扣列表
const QUICK_DISCOUNTS = [
  { label: '无折扣', val: 10 },
  { label: '98折', val: 9.8 },
  { label: '95折', val: 9.5 },
  { label: '92折', val: 9.2 },
  { label: '9折', val: 9.0 },
  { label: '88折', val: 8.8 },
  { label: '85折', val: 8.5 }
];

// 是否属于某个常用快捷折扣
function isQuickDiscountActive(record, val) {
  if (!record) return false;
  const d = Number(record.discount !== undefined && record.discount !== null ? record.discount : 10);
  return d === val;
}

// 是否当前是自定义折扣 (即用户手动输入的折扣，不在常用列表且有打折)
function isCustomDiscountActive(record) {
  if (!record) return false;
  const d = Number(record.discount);
  if (isNaN(d) || d >= 10 || d <= 0) return false;
  return !QUICK_DISCOUNTS.some(item => item.val === d);
}

// 激活自定义折扣输入
function onSelectCustomDiscount(record, mode) {
  if (!record) return;
  activeField.value = 'discount';
  if (Number(record.discount || 10) >= 10) {
    record.discount = '';
  }
}

// 快捷折扣选择
function onQuickDiscountSelect(record, dVal, mode) {
  if (!record) return;
  record.discount = dVal;
  handleFieldCalculation(record, 'discount', mode);
}

// 软键盘输入键
function onInputKey(record, char, mode) {
  if (!record) return;
  const f = activeField.value;

  if (f === 'discount') {
    let curVal = String(record.discount !== undefined && record.discount !== null ? record.discount : '');
    if (curVal === '10' || curVal === '0') curVal = '';
    if (char === '.' && curVal.includes('.')) return;
    if (curVal.includes('.') && curVal.split('.')[1].length >= 1) return;

    let nextStr = curVal + char;
    let numVal = parseFloat(nextStr) || 0;

    // 智能识别车主输入：如果用户直接按两位整数(如 95、92、88、85、75)，自动识别为 9.5、9.2、8.8、8.5、7.5 折
    if (nextStr.length === 2 && !nextStr.includes('.') && numVal >= 11 && numVal <= 99) {
      nextStr = (numVal / 10).toFixed(1);
      numVal = parseFloat(nextStr);
    }

    if (numVal > 10) {
      nextStr = '10';
      numVal = 10;
    }

    record.discount = nextStr.endsWith('.') ? nextStr : (numVal || nextStr);
    handleFieldCalculation(record, 'discount', mode);
    return;
  }

  if (f === 'total_amount') {
    // 修改原价总额
    let curVal = String(getOriginalTotal(record));
    if (curVal === '0') curVal = '';
    if (char === '.' && curVal.includes('.')) return;
    let nextStr = curVal + char;
    const nextTotal = parseFloat(nextStr) || 0;
    
    // 换算: 保持优惠券与折扣不变，反算实付与升数
    const coupon = Number(record.coupon || 0);
    const discount = Number(record.discount || 10);
    const price = Number(record.price || 0);

    let nextPaid = nextTotal;
    if (discount > 0 && discount < 10) {
      nextPaid = nextTotal * (discount / 10.0);
    }
    nextPaid = Math.max(0, parseFloat((nextPaid - coupon).toFixed(1)));
    record.actual_paid = nextPaid;

    if (price > 0 && nextTotal > 0) {
      record.liters = parseFloat((nextTotal / price).toFixed(2));
    }
    if (mode === 'edit') emit('edit-paid-change');
    else emit('new-paid-change');
    return;
  }

  let curVal = String(record[f] !== undefined ? record[f] : '');
  if (curVal === '0') curVal = '';
  if (char === '.' && curVal.includes('.')) return;
  const nextStr = curVal + char;
  record[f] = f === 'odo' ? parseInt(nextStr, 10) || 0 : (nextStr.endsWith('.') ? nextStr : parseFloat(nextStr) || 0);

  handleFieldCalculation(record, f, mode);
}

// 退格
function onBackspace(record, mode) {
  if (!record) return;
  const f = activeField.value;

  if (f === 'discount') {
    let curVal = String(record.discount !== undefined && record.discount !== null ? record.discount : '');
    if (curVal.length <= 1) {
      record.discount = 10;
    } else {
      let nextStr = curVal.slice(0, -1);
      record.discount = nextStr.endsWith('.') ? nextStr : (parseFloat(nextStr) || 10);
    }
    handleFieldCalculation(record, 'discount', mode);
    return;
  }

  if (f === 'total_amount') {
    let curVal = String(getOriginalTotal(record));
    if (curVal.length <= 1) curVal = '0';
    else curVal = curVal.slice(0, -1);
    const nextTotal = parseFloat(curVal) || 0;

    const coupon = Number(record.coupon || 0);
    const discount = Number(record.discount || 10);
    const price = Number(record.price || 0);

    let nextPaid = nextTotal;
    if (discount > 0 && discount < 10) {
      nextPaid = nextTotal * (discount / 10.0);
    }
    nextPaid = Math.max(0, parseFloat((nextPaid - coupon).toFixed(1)));
    record.actual_paid = nextPaid;

    if (price > 0 && nextTotal > 0) {
      record.liters = parseFloat((nextTotal / price).toFixed(2));
    }
    if (mode === 'edit') emit('edit-paid-change');
    else emit('new-paid-change');
    return;
  }

  let curVal = String(record[f] !== undefined ? record[f] : '');
  if (curVal.length <= 1) {
    record[f] = 0;
  } else {
    const nextStr = curVal.slice(0, -1);
    record[f] = f === 'odo' ? parseInt(nextStr, 10) || 0 : parseFloat(nextStr) || 0;
  }
  handleFieldCalculation(record, f, mode);
}

// 清空当前字段
function onClearCurrent(record, mode) {
  if (!record) return;
  const f = activeField.value;
  if (f === 'total_amount') {
    record.actual_paid = 0;
    record.coupon = 0;
    record.liters = 0;
  } else if (f === 'discount') {
    record[f] = 10;
  } else {
    record[f] = 0;
  }
  handleFieldCalculation(record, f, mode);
}

// 切换到下一个输入字段
function onNextField() {
  const fields = ['total_amount', 'coupon', 'actual_paid', 'discount', 'liters', 'price', 'odo'];
  const idx = fields.indexOf(activeField.value);
  if (idx >= 0 && idx < fields.length - 1) {
    activeField.value = fields[idx + 1];
  } else {
    activeField.value = fields[0];
  }
}

// 核心数值计算联动逻辑 (原价、优惠、实付、升数完全自洽)
function handleFieldCalculation(record, field, mode) {
  const price = parseFloat(record.price) || 0;
  let paid = parseFloat(record.actual_paid) || 0;
  let coupon = parseFloat(record.coupon) || 0;
  let discount = parseFloat(record.discount) || 10;
  let liters = parseFloat(record.liters) || 0;

  if (field === 'discount') {
    // 用户调了折扣率 (如 9.5 折、9.3 折、8.5 折)
    let dVal = parseFloat(record.discount);
    if (!isNaN(dVal) && dVal > 0 && dVal < 10) {
      const discountRatio = dVal / 10.0;
      // 保持当前的原价总值不变，更新实付与立省
      let total = getOriginalTotal(record);
      if (total <= 0 && liters > 0 && price > 0) {
        total = liters * price;
      }
      if (total > 0) {
        paid = parseFloat((total * discountRatio).toFixed(1));
        coupon = Math.max(0, parseFloat((total - paid).toFixed(1)));
        record.actual_paid = paid;
        record.coupon = coupon;
        if (price > 0) {
          record.liters = parseFloat((total / price).toFixed(2));
        }
      }
    } else if (dVal >= 10) {
      // 恢复 10 折无折扣
      record.discount = 10;
      let total = getOriginalTotal(record);
      if (total > 0) {
        record.actual_paid = total;
        record.coupon = 0;
      }
    }
    if (mode === 'edit') emit('edit-paid-change');
    else emit('new-paid-change');
  } else if (field === 'coupon') {
    // 用户改动了优惠立减金额 (例如加了 240，优惠了 20，实付应为 220)
    // 保持原价不变: total = paid + (旧券) 或根据当前原价推导
    let total = getOriginalTotal(record);
    if (total > 0 && total >= coupon) {
      record.actual_paid = parseFloat((total - coupon).toFixed(1));
    }
  } else if (field === 'actual_paid') {
    // 用户改动了实付金额
    const total = paid + coupon;
    if (price > 0 && total > 0) {
      record.liters = parseFloat((total / price).toFixed(2));
    }
  } else if (field === 'liters') {
    // 用户改动了升数: 原价 total = liters * price
    if (price > 0 && liters > 0) {
      const total = parseFloat((liters * price).toFixed(1));
      if (discount > 0 && discount < 10) {
        paid = parseFloat((total * (discount / 10.0)).toFixed(1));
        coupon = Math.max(0, parseFloat((total - paid).toFixed(1)));
        record.actual_paid = paid;
        record.coupon = coupon;
      } else {
        record.actual_paid = Math.max(0, parseFloat((total - coupon).toFixed(1)));
      }
    }
  } else if (field === 'price') {
    // 用户改动了单价
    const total = paid + coupon;
    if (price > 0 && total > 0) {
      record.liters = parseFloat((total / price).toFixed(2));
    }
  }

  if (mode === 'edit') emit('edit-paid-change');
  else emit('new-paid-change');
}

// 实用备注标签相关
function isTagSelected(record, tag) {
  if (!record || !record.note) return false;
  const parts = String(record.note).trim().split(/\s+/);
  return parts.includes(tag);
}

function toggleNoteTag(record, tag) {
  if (!record) return;
  const currentNote = String(record.note || '').trim();
  let parts = currentNote ? currentNote.split(/\s+/) : [];
  if (parts.includes(tag)) {
    parts = parts.filter(t => t !== tag);
  } else {
    parts.push(tag);
  }
  record.note = parts.join(' ');
}

function onClearNote(record) {
  if (!record) return;
  record.note = '';
}
</script>
