<template>
  <div>
    <!-- 车规标准弹窗 1: 编辑/校准加油台账 -->
    <ModalWrapper
      :show="showEditModal"
      title="编辑与校对加油台账"
      badge="车载工作台"
      maxWidthClass="max-w-[1260px]"
      headerClass="min-h-[64px] px-6 py-2"
      bodyClass="p-3.5 space-y-2"
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
          class="min-h-[50px] px-6 rounded-xl border-2 border-car-accent bg-car-item text-car-accent font-black text-[16px] hover:opacity-90 active:scale-95 transition-all cursor-pointer shadow-md whitespace-nowrap"
        >
          保存修改
        </button>
      </template>

      <div v-if="editingRecord" class="flex flex-col space-y-2">
        <!-- 左右双翼工作台 (横向展开，消除垂直滚动条) -->
        <div class="grid grid-cols-12 gap-3.5 items-stretch">
          
          <!-- 左翼：数值槽位 + 大号数字软键盘 + 填补空白的加满/补油卡片 (占 6 列) -->
          <div class="col-span-6 flex flex-col space-y-2">
            <!-- 4 个数值槽位 (2x2 网格，触控直选，禁止唤出系统软键盘) -->
            <div class="grid grid-cols-2 gap-2">
              <!-- 实付金额 -->
              <div 
                @click="activeField = 'actual_paid'"
                :class="[
                  'min-h-[52px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'actual_paid'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[11.5px] text-car-sub font-bold">实付金额 *</span>
                  <div class="flex items-center space-x-1">
                    <span class="text-[19px] font-mono font-black" :class="activeField === 'actual_paid' ? 'text-car-accent' : 'text-car-text'">
                      {{ editingRecord.actual_paid !== undefined && editingRecord.actual_paid !== null ? editingRecord.actual_paid : '0.0' }}
                    </span>
                    <span v-if="activeField === 'actual_paid'" class="w-1.5 h-4 bg-car-accent animate-pulse inline-block"></span>
                  </div>
                </div>
                <span class="text-[12px] font-mono font-bold text-car-sub">元</span>
              </div>

              <!-- 加注升数 -->
              <div 
                @click="activeField = 'liters'"
                :class="[
                  'min-h-[52px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'liters'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[11.5px] text-car-sub font-bold">加注升数 *</span>
                  <div class="flex items-center space-x-1">
                    <span class="text-[19px] font-mono font-black" :class="activeField === 'liters' ? 'text-car-accent' : 'text-car-text'">
                      {{ editingRecord.liters !== undefined && editingRecord.liters !== null ? editingRecord.liters : '0.0' }}
                    </span>
                    <span v-if="activeField === 'liters'" class="w-1.5 h-4 bg-car-accent animate-pulse inline-block"></span>
                  </div>
                </div>
                <span class="text-[12px] font-mono font-bold text-car-sub">L</span>
              </div>

              <!-- 实际单价 -->
              <div 
                @click="activeField = 'price'"
                :class="[
                  'min-h-[52px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'price'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[11.5px] text-car-sub font-bold">实际单价</span>
                  <div class="flex items-center space-x-1">
                    <span class="text-[19px] font-mono font-black" :class="activeField === 'price' ? 'text-car-accent' : 'text-car-text'">
                      {{ editingRecord.price !== undefined && editingRecord.price !== null ? editingRecord.price : '0.00' }}
                    </span>
                    <span v-if="activeField === 'price'" class="w-1.5 h-4 bg-car-accent animate-pulse inline-block"></span>
                  </div>
                </div>
                <span class="text-[12px] font-mono font-bold text-car-sub">元/L</span>
              </div>

              <!-- 仪表总里程 -->
              <div 
                @click="activeField = 'odo'"
                :class="[
                  'min-h-[52px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'odo'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <div class="flex items-center space-x-1.5">
                    <span class="text-[11.5px] text-car-sub font-bold">仪表总里程</span>
                    <span 
                      v-if="currentOdo > 0"
                      @click.stop="applyCurrentOdo(editingRecord)"
                      class="px-1.5 py-0.5 rounded-md bg-car-item border border-car-accent text-[10.5px] text-car-accent font-black hover:bg-car-accent hover:text-car-text cursor-pointer transition-all shadow-sm"
                      title="点击填入车机采集的实时里程"
                    >
                      实车 {{ Math.round(currentOdo) }} km ⤶
                    </span>
                    <span 
                      v-else
                      @click.stop="$emit('refresh-odo')"
                      class="px-1.5 py-0.5 rounded-md bg-car-item border border-car-border text-[10.5px] text-car-sub font-bold hover:border-car-accent hover:text-car-text cursor-pointer transition-all shadow-sm"
                      title="点击向车机请求同步实时里程"
                    >
                      待同步 ⟳
                    </span>
                  </div>
                  <div class="flex items-center space-x-1">
                    <span class="text-[19px] font-mono font-black" :class="activeField === 'odo' ? 'text-car-accent' : 'text-car-text'">
                      {{ editingRecord.odo !== undefined && editingRecord.odo !== null && editingRecord.odo > 0 ? editingRecord.odo : (currentOdo > 0 ? Math.round(currentOdo) : '未记录') }}
                    </span>
                    <span v-if="activeField === 'odo'" class="w-1.5 h-4 bg-car-accent animate-pulse inline-block"></span>
                  </div>
                </div>
                <span class="text-[12px] font-mono font-bold text-car-sub">km</span>
              </div>
            </div>

            <!-- 车载大号数字键盘面板 -->
            <div class="p-2.5 rounded-2xl bg-car-card border-2 border-car-border shadow-md flex flex-col space-y-1.5">
              <div class="flex items-center justify-between px-1 text-[12px] font-bold text-car-sub pb-1 border-b border-car-border/60">
                <span>当前录入: <b class="text-car-accent text-[13px]">{{ getFieldLabel(activeField) }}</b></span>
                <span class="text-[11px] text-car-sub/70">内置软键盘 · 免调车机输入法</span>
              </div>

              <!-- 4x4 紧凑数字键阵 -->
              <div class="grid grid-cols-4 gap-1.5">
                <button 
                  v-for="k in ['1', '2', '3']" 
                  :key="k" 
                  type="button" 
                  @click="onInputKey(editingRecord, k, 'edit')" 
                  class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[20px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  {{ k }}
                </button>
                <button 
                  type="button" 
                  @click="onBackspace(editingRecord, 'edit')" 
                  class="min-h-[50px] rounded-xl border-2 border-rose-500/40 bg-rose-500/10 text-rose-400 font-black text-[14px] flex items-center justify-center cursor-pointer hover:bg-rose-500/20 active:scale-95 transition-all shadow-sm"
                >
                  ⌫ 退格
                </button>

                <button 
                  v-for="k in ['4', '5', '6']" 
                  :key="k" 
                  type="button" 
                  @click="onInputKey(editingRecord, k, 'edit')" 
                  class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[20px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  {{ k }}
                </button>
                <button 
                  type="button" 
                  @click="onClearCurrent(editingRecord, 'edit')" 
                  class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item text-car-sub hover:text-car-text text-[13.5px] font-bold flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  清空
                </button>

                <button 
                  v-for="k in ['7', '8', '9']" 
                  :key="k" 
                  type="button" 
                  @click="onInputKey(editingRecord, k, 'edit')" 
                  class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[20px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  {{ k }}
                </button>
                <button 
                  v-if="activeField === 'odo'"
                  type="button" 
                  @click="currentOdo > 0 ? applyCurrentOdo(editingRecord) : onInputKey(editingRecord, '00', 'edit')" 
                  class="min-h-[50px] rounded-xl border-2 border-car-accent bg-car-item text-car-accent font-mono text-[13px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  {{ currentOdo > 0 ? '带入实车' : '00' }}
                </button>
                <button 
                  v-else
                  type="button" 
                  @click="onQuickAmount(editingRecord, 200, 'edit')" 
                  class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item text-amber-400 font-mono text-[13.5px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  ¥200
                </button>

                <button 
                  type="button" 
                  @click="onInputKey(editingRecord, '.', 'edit')" 
                  class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[22px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  .
                </button>
                <button 
                  type="button" 
                  @click="onInputKey(editingRecord, '0', 'edit')" 
                  class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[20px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  0
                </button>
                <button 
                  type="button" 
                  @click="onNextField" 
                  class="min-h-[50px] rounded-xl border-2 border-car-accent/40 bg-car-item text-car-accent text-[13.5px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  下项 ➔
                </button>
                <button 
                  v-if="activeField === 'odo'"
                  type="button" 
                  @click="currentOdo > 0 ? applyCurrentOdo(editingRecord) : onInputKey(editingRecord, '000', 'edit')" 
                  class="min-h-[50px] rounded-xl border-2 border-emerald-500/50 bg-emerald-500/10 text-emerald-400 font-mono text-[11.5px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  {{ currentOdo > 0 ? Math.round(currentOdo) + 'km' : '000' }}
                </button>
                <button 
                  v-else
                  type="button" 
                  @click="onQuickAmount(editingRecord, 300, 'edit')" 
                  class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item text-amber-400 font-mono text-[13.5px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  ¥300
                </button>
              </div>
            </div>

            <!-- 填补键盘下方多余空白：【跳枪加满】与【阶段补油】带状态胶囊双卡片 -->
            <div class="grid grid-cols-2 gap-2">
              <!-- 跳枪加满卡片 -->
              <div
                @click="editingRecord.is_full = true"
                :class="[
                  'min-h-[52px] px-3.5 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  editingRecord.is_full
                    ? 'border-emerald-500 bg-emerald-500/10'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[13px] font-black" :class="editingRecord.is_full ? 'text-emerald-400' : 'text-car-text'">
                    跳枪加满
                  </span>
                  <span class="text-[10.5px] text-car-sub font-bold">精准核算百公里油耗</span>
                </div>
                <!-- 专属状态小胶囊 -->
                <span 
                  :class="[
                    'px-2.5 py-1 rounded-full text-[11.5px] font-black whitespace-nowrap transition-all shadow-sm',
                    editingRecord.is_full
                      ? 'bg-emerald-500 text-white'
                      : 'border border-car-border bg-car-item text-car-sub/60'
                  ]"
                >
                  {{ editingRecord.is_full ? '● 已加满' : '○ 未激活' }}
                </span>
              </div>

              <!-- 阶段补油卡片 -->
              <div
                @click="editingRecord.is_full = false"
                :class="[
                  'min-h-[52px] px-3.5 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  !editingRecord.is_full
                    ? 'border-amber-500 bg-amber-500/10'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[13px] font-black" :class="!editingRecord.is_full ? 'text-amber-400' : 'text-car-text'">
                    阶段补油
                  </span>
                  <span class="text-[10.5px] text-car-sub font-bold">临时补充 · 不计油耗</span>
                </div>
                <!-- 专属状态小胶囊 -->
                <span 
                  :class="[
                    'px-2.5 py-1 rounded-full text-[11.5px] font-black whitespace-nowrap transition-all shadow-sm',
                    !editingRecord.is_full
                      ? 'bg-amber-500 text-slate-900'
                      : 'border border-car-border bg-car-item text-car-sub/60'
                  ]"
                >
                  {{ !editingRecord.is_full ? '● 补油中' : '○ 未激活' }}
                </span>
              </div>
            </div>
          </div>

          <!-- 右翼：油品标号 + 快捷省份折叠 + 免打字多选组合备注工作台 (占 6 列) -->
          <div class="col-span-6 flex flex-col space-y-2">
            
            <!-- 油品标号 4 选 1 -->
            <div class="p-2 rounded-xl bg-car-card border-2 border-car-border flex items-center justify-between shadow-sm">
              <span class="text-[12px] text-car-sub font-bold whitespace-nowrap mr-2">油品标号:</span>
              <div class="grid grid-cols-4 gap-1.5 w-[75%]">
                <button
                  v-for="ft in ['92#', '95#', '98#', '0#']"
                  :key="ft"
                  type="button"
                  @click="editingRecord.fuel_type = (ft === '0#' ? '0#柴油' : ft); $emit('edit-fuel-type-change')"
                  :class="[
                    'min-h-[50px] rounded-xl border-2 font-black text-[13.5px] transition-all flex items-center justify-center cursor-pointer shadow-sm',
                    (editingRecord.fuel_type === ft || (ft === '0#' && editingRecord.fuel_type === '0#柴油'))
                      ? 'bg-car-item border-car-accent text-car-accent'
                      : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
                  ]"
                >
                  {{ ft }}
                </button>
              </div>
            </div>

            <!-- 省份快捷联动 (默认单行，可折叠切换，杜绝挤占空间) -->
            <div class="p-2 rounded-xl bg-car-card border-2 border-car-border flex flex-col space-y-1.5 shadow-sm">
              <div class="flex items-center justify-between text-[12px] font-bold px-0.5">
                <div class="flex items-center space-x-1.5">
                  <span class="text-car-sub whitespace-nowrap">加油省份:</span>
                  <span class="px-2 py-0.5 rounded-lg bg-car-accent-light border border-car-accent text-car-accent font-black text-[12.5px]">
                    {{ editingRecord.province || '浙江' }}
                  </span>
                  <span class="text-[11px] text-car-sub/70">限价自动对齐</span>
                </div>
                <button
                  type="button"
                  @click="showEditProvincePicker = !showEditProvincePicker"
                  class="min-h-[50px] px-3.5 rounded-lg border border-car-border bg-car-item text-car-sub hover:text-car-text text-[12px] font-bold cursor-pointer whitespace-nowrap"
                >
                  {{ showEditProvincePicker ? '收起省份 ▲' : '切换省份 ▾' }}
                </button>
              </div>

              <!-- 折叠展开的 31 省市网格 -->
              <div v-if="showEditProvincePicker" class="flex flex-wrap -m-0.5 pt-4 border-t border-car-border/60">
                <div v-for="prov in allProvinceNames" :key="prov" class="w-[12.5%] p-0.5">
                  <button 
                    type="button"
                    @click="onSelectProvince(editingRecord, prov, 'edit')"
                    :class="[
                      'w-full min-h-[50px] rounded-lg border font-black text-[12px] cursor-pointer transition-all flex items-center justify-center truncate',
                      editingRecord.province === prov
                        ? 'bg-car-accent border-car-accent text-car-text shadow-sm'
                        : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
                    ]"
                  >
                    {{ prov }}
                  </button>
                </div>
              </div>
            </div>

            <!-- 免打字组合备注工作台 (多选组合、反选剔除、一键清空) -->
            <div class="p-2.5 rounded-2xl bg-car-card border-2 border-car-border flex flex-col space-y-2 flex-1 shadow-md">
              <div class="flex items-center justify-between text-[12px] font-bold text-car-sub px-0.5 border-b border-car-border/60 pb-1.5">
                <div class="flex items-center space-x-1.5 truncate">
                  <span class="whitespace-nowrap">组合备注:</span>
                  <span v-if="editingRecord.note" class="text-car-accent font-black truncate max-w-[360px] text-[13px]">
                    {{ editingRecord.note }}
                  </span>
                  <span v-else class="text-car-sub/60">可点击下方标签多选自由组合</span>
                </div>
                <button
                  type="button"
                  @click="onClearNote(editingRecord)"
                  class="min-h-[50px] px-3.5 rounded-xl border border-rose-500/40 bg-rose-500/10 text-rose-400 hover:bg-rose-500/20 text-[12px] font-black cursor-pointer whitespace-nowrap transition-all shadow-sm"
                >
                  清空备注
                </button>
              </div>

              <!-- 15 组车规级高频常用标签矩阵 (3 列 x 5 行) -->
              <div class="grid grid-cols-3 gap-1.5">
                <button 
                  v-for="tag in NOTE_TAGS" 
                  :key="tag" 
                  type="button"
                  @click="toggleNoteTag(editingRecord, tag)"
                  :class="[
                    'min-h-[50px] px-2 rounded-xl border font-black text-[12.5px] cursor-pointer transition-all flex items-center justify-center whitespace-nowrap shadow-sm',
                    isTagSelected(editingRecord, tag)
                      ? 'bg-car-item border-2 border-car-accent text-car-accent'
                      : 'bg-car-card border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'
                  ]"
                >
                  {{ isTagSelected(editingRecord, tag) ? '✓ ' + tag : '+ ' + tag }}
                </button>
              </div>
            </div>

          </div>
        </div>
      </div>
    </ModalWrapper>

    <!-- 车规标准弹窗 2: 手动补录一笔加油台账 -->
    <ModalWrapper
      :show="showAddModal"
      title="手动补录一笔加油台账"
      badge="车载工作台"
      maxWidthClass="max-w-[1260px]"
      headerClass="min-h-[64px] px-6 py-2"
      bodyClass="p-3.5 space-y-2"
      zIndexClass="z-[9990]"
      @close="$emit('close-add')"
    >
      <template #header-actions>
        <button 
          type="button"
          @click="$emit('save-new')"
          class="min-h-[50px] px-6 rounded-xl border-2 border-car-accent bg-car-item text-car-accent font-black text-[16px] hover:opacity-90 active:scale-95 transition-all cursor-pointer shadow-md whitespace-nowrap"
        >
          确认记入账本
        </button>
      </template>

      <div v-if="newRecord" class="flex flex-col space-y-2">
        <!-- 左右双翼工作台 (横向展开，消除垂直滚动条) -->
        <div class="grid grid-cols-12 gap-3.5 items-stretch">
          
          <!-- 左翼：数值槽位 + 大号数字软键盘 + 填补空白的加满/补油卡片 (占 6 列) -->
          <div class="col-span-6 flex flex-col space-y-2">
            <!-- 4 个数值槽位 (2x2 网格，触控直选，禁止唤出系统软键盘) -->
            <div class="grid grid-cols-2 gap-2">
              <!-- 实付金额 -->
              <div 
                @click="activeField = 'actual_paid'"
                :class="[
                  'min-h-[52px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'actual_paid'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[11.5px] text-car-sub font-bold">实付金额 *</span>
                  <div class="flex items-center space-x-1">
                    <span class="text-[19px] font-mono font-black" :class="activeField === 'actual_paid' ? 'text-car-accent' : 'text-car-text'">
                      {{ newRecord.actual_paid !== undefined && newRecord.actual_paid !== null ? newRecord.actual_paid : '0.0' }}
                    </span>
                    <span v-if="activeField === 'actual_paid'" class="w-1.5 h-4 bg-car-accent animate-pulse inline-block"></span>
                  </div>
                </div>
                <span class="text-[12px] font-mono font-bold text-car-sub">元</span>
              </div>

              <!-- 加注升数 -->
              <div 
                @click="activeField = 'liters'"
                :class="[
                  'min-h-[52px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'liters'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[11.5px] text-car-sub font-bold">加注升数 *</span>
                  <div class="flex items-center space-x-1">
                    <span class="text-[19px] font-mono font-black" :class="activeField === 'liters' ? 'text-car-accent' : 'text-car-text'">
                      {{ newRecord.liters !== undefined && newRecord.liters !== null ? newRecord.liters : '0.0' }}
                    </span>
                    <span v-if="activeField === 'liters'" class="w-1.5 h-4 bg-car-accent animate-pulse inline-block"></span>
                  </div>
                </div>
                <span class="text-[12px] font-mono font-bold text-car-sub">L</span>
              </div>

              <!-- 实际单价 -->
              <div 
                @click="activeField = 'price'"
                :class="[
                  'min-h-[52px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'price'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[11.5px] text-car-sub font-bold">实际单价</span>
                  <div class="flex items-center space-x-1">
                    <span class="text-[19px] font-mono font-black" :class="activeField === 'price' ? 'text-car-accent' : 'text-car-text'">
                      {{ newRecord.price !== undefined && newRecord.price !== null ? newRecord.price : '0.00' }}
                    </span>
                    <span v-if="activeField === 'price'" class="w-1.5 h-4 bg-car-accent animate-pulse inline-block"></span>
                  </div>
                </div>
                <span class="text-[12px] font-mono font-bold text-car-sub">元/L</span>
              </div>

              <!-- 仪表总里程 -->
              <div 
                @click="activeField = 'odo'"
                :class="[
                  'min-h-[52px] px-3 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  activeField === 'odo'
                    ? 'border-car-accent bg-car-accent-light'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <div class="flex items-center space-x-1.5">
                    <span class="text-[11.5px] text-car-sub font-bold">仪表总里程</span>
                    <span 
                      v-if="currentOdo > 0"
                      @click.stop="applyCurrentOdo(newRecord)"
                      class="px-1.5 py-0.5 rounded-md bg-car-item border border-car-accent text-[10.5px] text-car-accent font-black hover:bg-car-accent hover:text-car-text cursor-pointer transition-all shadow-sm"
                      title="点击填入车机采集的实时里程"
                    >
                      实车 {{ Math.round(currentOdo) }} km ⤶
                    </span>
                    <span 
                      v-else
                      @click.stop="$emit('refresh-odo')"
                      class="px-1.5 py-0.5 rounded-md bg-car-item border border-car-border text-[10.5px] text-car-sub font-bold hover:border-car-accent hover:text-car-text cursor-pointer transition-all shadow-sm"
                      title="点击向车机请求同步实时里程"
                    >
                      待同步 ⟳
                    </span>
                  </div>
                  <div class="flex items-center space-x-1">
                    <span class="text-[19px] font-mono font-black" :class="activeField === 'odo' ? 'text-car-accent' : 'text-car-text'">
                      {{ newRecord.odo !== undefined && newRecord.odo !== null && newRecord.odo > 0 ? newRecord.odo : (currentOdo > 0 ? Math.round(currentOdo) : '未记录') }}
                    </span>
                    <span v-if="activeField === 'odo'" class="w-1.5 h-4 bg-car-accent animate-pulse inline-block"></span>
                  </div>
                </div>
                <span class="text-[12px] font-mono font-bold text-car-sub">km</span>
              </div>
            </div>

            <!-- 车载大号数字键盘面板 -->
            <div class="p-2.5 rounded-2xl bg-car-card border-2 border-car-border shadow-md flex flex-col space-y-1.5">
              <div class="flex items-center justify-between px-1 text-[12px] font-bold text-car-sub pb-1 border-b border-car-border/60">
                <span>当前录入: <b class="text-car-accent text-[13px]">{{ getFieldLabel(activeField) }}</b></span>
                <span class="text-[11px] text-car-sub/70">内置软键盘 · 免调车机输入法</span>
              </div>

              <!-- 4x4 紧凑数字键阵 -->
              <div class="grid grid-cols-4 gap-1.5">
                <button 
                  v-for="k in ['1', '2', '3']" 
                  :key="k" 
                  type="button" 
                  @click="onInputKey(newRecord, k, 'new')" 
                  class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[20px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  {{ k }}
                </button>
                <button 
                  type="button" 
                  @click="onBackspace(newRecord, 'new')" 
                  class="min-h-[50px] rounded-xl border-2 border-rose-500/40 bg-rose-500/10 text-rose-400 font-black text-[14px] flex items-center justify-center cursor-pointer hover:bg-rose-500/20 active:scale-95 transition-all shadow-sm"
                >
                  ⌫ 退格
                </button>

                <button 
                  v-for="k in ['4', '5', '6']" 
                  :key="k" 
                  type="button" 
                  @click="onInputKey(newRecord, k, 'new')" 
                  class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[20px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  {{ k }}
                </button>
                <button 
                  type="button" 
                  @click="onClearCurrent(newRecord, 'new')" 
                  class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item text-car-sub hover:text-car-text text-[13.5px] font-bold flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  清空
                </button>

                <button 
                  v-for="k in ['7', '8', '9']" 
                  :key="k" 
                  type="button" 
                  @click="onInputKey(newRecord, k, 'new')" 
                  class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[20px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  {{ k }}
                </button>
                <button 
                  v-if="activeField === 'odo'"
                  type="button" 
                  @click="currentOdo > 0 ? applyCurrentOdo(newRecord) : onInputKey(newRecord, '00', 'new')" 
                  class="min-h-[50px] rounded-xl border-2 border-car-accent bg-car-item text-car-accent font-mono text-[13px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  {{ currentOdo > 0 ? '带入实车' : '00' }}
                </button>
                <button 
                  v-else
                  type="button" 
                  @click="onQuickAmount(newRecord, 200, 'new')" 
                  class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item text-amber-400 font-mono text-[13.5px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  ¥200
                </button>

                <button 
                  type="button" 
                  @click="onInputKey(newRecord, '.', 'new')" 
                  class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[22px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  .
                </button>
                <button 
                  type="button" 
                  @click="onInputKey(newRecord, '0', 'new')" 
                  class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[20px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  0
                </button>
                <button 
                  type="button" 
                  @click="onNextField" 
                  class="min-h-[50px] rounded-xl border-2 border-car-accent/40 bg-car-item text-car-accent text-[13.5px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  下项 ➔
                </button>
                <button 
                  v-if="activeField === 'odo'"
                  type="button" 
                  @click="currentOdo > 0 ? applyCurrentOdo(newRecord) : onInputKey(newRecord, '000', 'new')" 
                  class="min-h-[50px] rounded-xl border-2 border-emerald-500/50 bg-emerald-500/10 text-emerald-400 font-mono text-[11.5px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  {{ currentOdo > 0 ? Math.round(currentOdo) + 'km' : '000' }}
                </button>
                <button 
                  v-else
                  type="button" 
                  @click="onQuickAmount(newRecord, 300, 'new')" 
                  class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item text-amber-400 font-mono text-[13.5px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
                >
                  ¥300
                </button>
              </div>
            </div>

            <!-- 填补键盘下方多余空白：【跳枪加满】与【阶段补油】带状态胶囊双卡片 -->
            <div class="grid grid-cols-2 gap-2">
              <!-- 跳枪加满卡片 -->
              <div
                @click="newRecord.is_full = true"
                :class="[
                  'min-h-[52px] px-3.5 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  newRecord.is_full
                    ? 'border-emerald-500 bg-emerald-500/10'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[13px] font-black" :class="newRecord.is_full ? 'text-emerald-400' : 'text-car-text'">
                    跳枪加满
                  </span>
                  <span class="text-[10.5px] text-car-sub font-bold">精准核算百公里油耗</span>
                </div>
                <!-- 专属状态小胶囊 -->
                <span 
                  :class="[
                    'px-2.5 py-1 rounded-full text-[11.5px] font-black whitespace-nowrap transition-all shadow-sm',
                    newRecord.is_full
                      ? 'bg-emerald-500 text-white'
                      : 'border border-car-border bg-car-item text-car-sub/60'
                  ]"
                >
                  {{ newRecord.is_full ? '● 已加满' : '○ 未激活' }}
                </span>
              </div>

              <!-- 阶段补油卡片 -->
              <div
                @click="newRecord.is_full = false"
                :class="[
                  'min-h-[52px] px-3.5 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
                  !newRecord.is_full
                    ? 'border-amber-500 bg-amber-500/10'
                    : 'border-car-border bg-car-card hover:border-car-border-light'
                ]"
              >
                <div class="flex flex-col text-left">
                  <span class="text-[13px] font-black" :class="!newRecord.is_full ? 'text-amber-400' : 'text-car-text'">
                    阶段补油
                  </span>
                  <span class="text-[10.5px] text-car-sub font-bold">临时补充 · 不计油耗</span>
                </div>
                <!-- 专属状态小胶囊 -->
                <span 
                  :class="[
                    'px-2.5 py-1 rounded-full text-[11.5px] font-black whitespace-nowrap transition-all shadow-sm',
                    !newRecord.is_full
                      ? 'bg-amber-500 text-slate-900'
                      : 'border border-car-border bg-car-item text-car-sub/60'
                  ]"
                >
                  {{ !newRecord.is_full ? '● 补油中' : '○ 未激活' }}
                </span>
              </div>
            </div>
          </div>

          <!-- 右翼：油品标号 + 快捷省份折叠 + 免打字多选组合备注工作台 (占 6 列) -->
          <div class="col-span-6 flex flex-col space-y-2">
            
            <!-- 油品标号 4 选 1 -->
            <div class="p-2 rounded-xl bg-car-card border-2 border-car-border flex items-center justify-between shadow-sm">
              <span class="text-[12px] text-car-sub font-bold whitespace-nowrap mr-2">油品标号:</span>
              <div class="grid grid-cols-4 gap-1.5 w-[75%]">
                <button
                  v-for="ft in ['92#', '95#', '98#', '0#']"
                  :key="ft"
                  type="button"
                  @click="newRecord.fuel_type = (ft === '0#' ? '0#柴油' : ft); $emit('new-fuel-type-change')"
                  :class="[
                    'min-h-[50px] rounded-xl border-2 font-black text-[13.5px] transition-all flex items-center justify-center cursor-pointer shadow-sm',
                    (newRecord.fuel_type === ft || (ft === '0#' && newRecord.fuel_type === '0#柴油'))
                      ? 'bg-car-item border-car-accent text-car-accent'
                      : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
                  ]"
                >
                  {{ ft }}
                </button>
              </div>
            </div>

            <!-- 省份快捷联动 (默认单行，可折叠切换，杜绝挤占空间) -->
            <div class="p-2 rounded-xl bg-car-card border-2 border-car-border flex flex-col space-y-1.5 shadow-sm">
              <div class="flex items-center justify-between text-[12px] font-bold px-0.5">
                <div class="flex items-center space-x-1.5">
                  <span class="text-car-sub whitespace-nowrap">加油省份:</span>
                  <span class="px-2 py-0.5 rounded-lg bg-car-accent-light border border-car-accent text-car-accent font-black text-[12.5px]">
                    {{ newRecord.province || '浙江' }}
                  </span>
                  <span class="text-[11px] text-car-sub/70">限价自动对齐</span>
                </div>
                <button
                  type="button"
                  @click="showAddProvincePicker = !showAddProvincePicker"
                  class="min-h-[50px] px-3.5 rounded-lg border border-car-border bg-car-item text-car-sub hover:text-car-text text-[12px] font-bold cursor-pointer whitespace-nowrap"
                >
                  {{ showAddProvincePicker ? '收起省份 ▲' : '切换省份 ▾' }}
                </button>
              </div>

              <!-- 折叠展开的 31 省市网格 -->
              <div v-if="showAddProvincePicker" class="flex flex-wrap -m-0.5 pt-4 border-t border-car-border/60">
                <div v-for="prov in allProvinceNames" :key="prov" class="w-[12.5%] p-0.5">
                  <button 
                    type="button"
                    @click="onSelectProvince(newRecord, prov, 'new')"
                    :class="[
                      'w-full min-h-[50px] rounded-lg border font-black text-[12px] cursor-pointer transition-all flex items-center justify-center truncate',
                      newRecord.province === prov
                        ? 'bg-car-accent border-car-accent text-car-text shadow-sm'
                        : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
                    ]"
                  >
                    {{ prov }}
                  </button>
                </div>
              </div>
            </div>

            <!-- 免打字组合备注工作台 (多选组合、反选剔除、一键清空) -->
            <div class="p-2.5 rounded-2xl bg-car-card border-2 border-car-border flex flex-col space-y-2 flex-1 shadow-md">
              <div class="flex items-center justify-between text-[12px] font-bold text-car-sub px-0.5 border-b border-car-border/60 pb-1.5">
                <div class="flex items-center space-x-1.5 truncate">
                  <span class="whitespace-nowrap">组合备注:</span>
                  <span v-if="newRecord.note" class="text-car-accent font-black truncate max-w-[360px] text-[13px]">
                    {{ newRecord.note }}
                  </span>
                  <span v-else class="text-car-sub/60">可点击下方标签多选自由组合</span>
                </div>
                <button
                  type="button"
                  @click="onClearNote(newRecord)"
                  class="min-h-[50px] px-3.5 rounded-xl border border-rose-500/40 bg-rose-500/10 text-rose-400 hover:bg-rose-500/20 text-[12px] font-black cursor-pointer whitespace-nowrap transition-all shadow-sm"
                >
                  清空备注
                </button>
              </div>

              <!-- 15 组车规级高频常用标签矩阵 (3 列 x 5 行) -->
              <div class="grid grid-cols-3 gap-1.5">
                <button 
                  v-for="tag in NOTE_TAGS" 
                  :key="tag" 
                  type="button"
                  @click="toggleNoteTag(newRecord, tag)"
                  :class="[
                    'min-h-[50px] px-2 rounded-xl border font-black text-[12.5px] cursor-pointer transition-all flex items-center justify-center whitespace-nowrap shadow-sm',
                    isTagSelected(newRecord, tag)
                      ? 'bg-car-item border-2 border-car-accent text-car-accent'
                      : 'bg-car-card border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'
                  ]"
                >
                  {{ isTagSelected(newRecord, tag) ? '✓ ' + tag : '+ ' + tag }}
                </button>
              </div>
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
  'new-province-change'
]);

// 当前选中的输入字段: 'actual_paid' | 'liters' | 'price' | 'odo'
const activeField = ref('actual_paid');

// 省份折叠开关状态
const showEditProvincePicker = ref(false);
const showAddProvincePicker = ref(false);

// 监听弹窗打开，重置聚焦到实付金额，并尝试为新记录带入实车最新总里程
watch(() => props.showAddModal, (val) => {
  if (val) {
    activeField.value = 'actual_paid';
    showAddProvincePicker.value = false;
    if (props.newRecord && (!props.newRecord.odo || props.newRecord.odo === 0) && props.currentOdo > 0) {
      props.newRecord.odo = Math.round(Number(props.currentOdo));
    }
  }
});
watch(() => props.showEditModal, (val) => {
  if (val) {
    activeField.value = 'actual_paid';
    showEditProvincePicker.value = false;
  }
});

// 一键填入/同步车机采集到的当前真实总里程
function applyCurrentOdo(record) {
  if (!record || !props.currentOdo) return;
  record.odo = Math.round(Number(props.currentOdo));
  activeField.value = 'odo';
}

function getFieldLabel(f) {
  switch (f) {
    case 'actual_paid': return '实付金额 (元)';
    case 'liters': return '加注升数 (L)';
    case 'price': return '实际单价 (元/L)';
    case 'odo': return '仪表总里程 (km)';
    default: return '实付金额';
  }
}

// 切换到下一个输入字段
function onNextField() {
  const fields = ['actual_paid', 'liters', 'price', 'odo'];
  const curIdx = fields.indexOf(activeField.value);
  const nextIdx = (curIdx + 1) % fields.length;
  activeField.value = fields[nextIdx];
}

// 车载小键盘输入数字或小数点
function onInputKey(record, char, mode) {
  if (!record) return;
  const field = activeField.value;
  let curVal = record[field];
  let strVal = (curVal !== undefined && curVal !== null) ? String(curVal) : '';

  // 如果原本是 0 且输入的不是小数点，直接替换
  if (strVal === '0' && char !== '.') {
    if (char === '00' || char === '000') return;
    strVal = '';
  }

  // 小数点去重
  if (char === '.' && strVal.includes('.')) {
    return;
  }

  // 限制长度防溢出
  if (strVal.length + char.length > 8) return;

  strVal += char;
  
  if (field === 'odo') {
    record.odo = parseInt(strVal, 10) || 0;
  } else {
    const num = parseFloat(strVal);
    record[field] = isNaN(num) ? 0 : (strVal.endsWith('.') ? strVal : num);
  }

  handleFieldCalculation(record, field, mode);
}

// ⌫ 退格删除一个字符
function onBackspace(record, mode) {
  if (!record) return;
  const field = activeField.value;
  let strVal = String(record[field] || '');
  if (strVal.length > 0) {
    strVal = strVal.slice(0, -1);
    if (strVal === '' || strVal === '-') {
      record[field] = 0;
    } else {
      if (field === 'odo') {
        record.odo = parseInt(strVal, 10) || 0;
      } else {
        const num = parseFloat(strVal);
        record[field] = isNaN(num) ? 0 : num;
      }
    }
    handleFieldCalculation(record, field, mode);
  }
}

// C 清空当前选定字段
function onClearCurrent(record, mode) {
  if (!record) return;
  const field = activeField.value;
  record[field] = 0;
  handleFieldCalculation(record, field, mode);
}

// 快捷金额 (¥200, ¥300)
function onQuickAmount(record, amount, mode) {
  if (!record) return;
  record.actual_paid = amount;
  activeField.value = 'actual_paid';
  handleFieldCalculation(record, 'actual_paid', mode);
}

// 省份选择事件
function onSelectProvince(record, prov, mode) {
  if (!record) return;
  record.province = prov;
  if (mode === 'edit') {
    emit('edit-province-change');
    showEditProvincePicker.value = false;
  } else {
    emit('new-province-change');
    showAddProvincePicker.value = false;
  }
}

// 触发联动重算
function handleFieldCalculation(record, field, mode) {
  const price = parseFloat(record.price) || 0;
  if (field === 'actual_paid') {
    const paid = parseFloat(record.actual_paid) || 0;
    if (price > 0 && paid > 0) {
      record.liters = parseFloat((paid / price).toFixed(2));
    }
    if (mode === 'edit') emit('edit-paid-change');
    else emit('new-paid-change');
  } else if (field === 'liters') {
    const liters = parseFloat(record.liters) || 0;
    if (price > 0 && liters > 0) {
      record.actual_paid = parseFloat((liters * price).toFixed(1));
    }
    if (mode === 'edit') emit('edit-liters-change');
    else emit('new-liters-change');
  } else if (field === 'price') {
    const paid = parseFloat(record.actual_paid) || 0;
    if (price > 0 && paid > 0) {
      record.liters = parseFloat((paid / price).toFixed(2));
    }
    if (mode === 'edit') emit('edit-price-change');
    else emit('new-price-change');
  }
}

// 常用免打字多选组合备注库 (涵盖小油站、品牌、优惠、场景)
const NOTE_TAGS = [
  '中石化', '中石油', '中海油',
  '壳牌', '民营小站', '山姆加油',
  '周五特惠', '会员折扣', '直降促销',
  '银行满减', '送洗车', '加燃油宝',
  '日常通勤', '高速服务区', '长途自驾'
];

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
