<template>
  <div>
    <!-- 车规标准弹窗 1: 编辑/校准加油台账 -->
    <ModalWrapper
      :show="showEditModal"
      title="编辑与校对加油台账"
      badge="车载数字键盘"
      maxWidthClass="max-w-[1140px]"
      bodyClass="p-3.5 space-y-2.5"
      zIndexClass="z-[9990]"
      @close="$emit('close-edit')"
    >
      <div v-if="editingRecord" class="flex flex-col space-y-2.5">
        <!-- 顶部 4 列大号数值槽位 (触控切换焦点，完全禁止唤出车机系统输入法) -->
        <div class="grid grid-cols-4 gap-2.5">
          <!-- 实付金额 -->
          <div 
            @click="activeField = 'actual_paid'"
            :class="[
              'min-h-[52px] px-3.5 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
              activeField === 'actual_paid'
                ? 'border-car-accent bg-car-accent-light scale-[1.01]'
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
            <span class="text-[11.5px] font-mono font-bold text-car-sub">元</span>
          </div>

          <!-- 加注升数 -->
          <div 
            @click="activeField = 'liters'"
            :class="[
              'min-h-[52px] px-3.5 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
              activeField === 'liters'
                ? 'border-car-accent bg-car-accent-light scale-[1.01]'
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
            <span class="text-[11.5px] font-mono font-bold text-car-sub">L</span>
          </div>

          <!-- 实际单价 -->
          <div 
            @click="activeField = 'price'"
            :class="[
              'min-h-[52px] px-3.5 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
              activeField === 'price'
                ? 'border-car-accent bg-car-accent-light scale-[1.01]'
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
            <span class="text-[11.5px] font-mono font-bold text-car-sub">元/L</span>
          </div>

          <!-- 仪表里程 -->
          <div 
            @click="activeField = 'odo'"
            :class="[
              'min-h-[52px] px-3.5 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
              activeField === 'odo'
                ? 'border-car-accent bg-car-accent-light scale-[1.01]'
                : 'border-car-border bg-car-card hover:border-car-border-light'
            ]"
          >
            <div class="flex flex-col text-left">
              <span class="text-[11.5px] text-car-sub font-bold">仪表总里程</span>
              <div class="flex items-center space-x-1">
                <span class="text-[19px] font-mono font-black" :class="activeField === 'odo' ? 'text-car-accent' : 'text-car-text'">
                  {{ editingRecord.odo !== undefined && editingRecord.odo !== null ? editingRecord.odo : '未打点' }}
                </span>
                <span v-if="activeField === 'odo'" class="w-1.5 h-4 bg-car-accent animate-pulse inline-block"></span>
              </div>
            </div>
            <span class="text-[11.5px] font-mono font-bold text-car-sub">km</span>
          </div>
        </div>

        <!-- 核心交互区：左侧车载数字键盘 + 右侧油品/加满/省份控制 (横向并列) -->
        <div class="grid grid-cols-12 gap-3 items-stretch">
          <!-- 左侧：车载专用大号数字键盘 (5列宽) -->
          <div class="col-span-5 p-2.5 rounded-2xl bg-car-card border-2 border-car-border shadow-lg flex flex-col space-y-1.5">
            <div class="flex items-center justify-between px-1 text-[12px] font-bold text-car-sub pb-1 border-b border-car-border/60">
              <span>当前输入: <b class="text-car-accent text-[13px]">{{ getFieldLabel(activeField) }}</b></span>
              <span class="text-[11px] text-car-sub/70">车规小键盘免输入法</span>
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
                type="button" 
                @click="onQuickAmount(editingRecord, 300, 'edit')" 
                class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item text-amber-400 font-mono text-[13.5px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
              >
                ¥300
              </button>
            </div>
          </div>

          <!-- 右侧：油品标号 + 加满状态 + 省份矩阵 + 快捷备注 (7列宽) -->
          <div class="col-span-7 flex flex-col justify-between space-y-2">
            <!-- 顶排：油品标号与加满状态 -->
            <div class="p-2 rounded-xl bg-car-card border-2 border-car-border flex items-center justify-between">
              <div class="flex items-center space-x-1.5 flex-1">
                <span class="text-[12px] text-car-sub font-bold whitespace-nowrap mr-1">标号:</span>
                <button
                  v-for="ft in ['92#', '95#', '98#', '0#']"
                  :key="ft"
                  type="button"
                  @click="editingRecord.fuel_type = (ft === '0#' ? '0#柴油' : ft); $emit('edit-fuel-type-change')"
                  :class="[
                    'flex-1 min-h-[50px] rounded-xl border-2 font-black text-[13.5px] transition-all flex items-center justify-center cursor-pointer',
                    (editingRecord.fuel_type === ft || (ft === '0#' && editingRecord.fuel_type === '0#柴油'))
                      ? 'bg-car-item border-car-accent text-car-accent shadow-sm'
                      : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
                  ]"
                >
                  {{ ft }}
                </button>
              </div>

              <div class="w-2"></div>

              <button 
                type="button"
                @click="editingRecord.is_full = !editingRecord.is_full" 
                :class="[
                  'min-h-[50px] px-3.5 rounded-xl border-2 font-black text-[13px] transition-all flex items-center justify-center cursor-pointer whitespace-nowrap',
                  editingRecord.is_full
                    ? 'bg-emerald-500/15 border-emerald-500 text-emerald-400'
                    : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ editingRecord.is_full ? '✓ 跳枪加满' : '阶段补油' }}
              </button>
            </div>

            <!-- 中排：全国 31 省市直选平铺矩阵 (紧凑 8 列) -->
            <div class="p-2 rounded-xl bg-car-item border border-car-border flex flex-col space-y-1">
              <div class="flex items-center justify-between text-[12px] text-car-sub font-bold px-0.5">
                <span>加油省份: <b class="text-car-accent text-[13px]">{{ editingRecord.province }}</b></span>
                <span class="text-[10.5px] text-car-sub/70">一键对齐当地实时限价</span>
              </div>
              <div class="flex flex-wrap -m-0.5">
                <div v-for="prov in allProvinceNames" :key="prov" class="w-[12.5%] p-0.5">
                  <button 
                    type="button"
                    @click="editingRecord.province = prov; $emit('edit-province-change')"
                    :class="[
                      'w-full min-h-[50px] rounded-lg border font-black text-[12.5px] cursor-pointer transition-all flex items-center justify-center truncate',
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

            <!-- 底排：免打字组合备注控制台 (支持多选组合、反选取消、一键清空) -->
            <div class="p-2 rounded-xl bg-car-card border-2 border-car-border flex flex-col space-y-1.5">
              <div class="flex items-center justify-between text-[12px] font-bold text-car-sub px-0.5">
                <div class="flex items-center space-x-1.5 truncate">
                  <span class="whitespace-nowrap">组合备注:</span>
                  <span v-if="editingRecord.note" class="text-car-accent font-black truncate max-w-[340px]">
                    {{ editingRecord.note }}
                  </span>
                  <span v-else class="text-car-sub/60">可点下方标签多选组合</span>
                </div>
                <button
                  type="button"
                  @click="onClearNote(editingRecord)"
                  class="min-h-[50px] px-3 rounded-lg border border-car-border bg-car-item text-car-sub hover:text-car-text text-[11.5px] font-bold cursor-pointer whitespace-nowrap"
                >
                  清空备注
                </button>
              </div>

              <!-- 15 个多选标签平铺 -->
              <div class="flex flex-wrap -m-0.5">
                <div v-for="tag in NOTE_TAGS" :key="tag" class="p-0.5">
                  <button 
                    type="button"
                    @click="toggleNoteTag(editingRecord, tag)"
                    :class="[
                      'min-h-[50px] px-2.5 rounded-lg border font-black text-[12px] cursor-pointer transition-all flex items-center justify-center whitespace-nowrap shadow-sm',
                      isTagSelected(editingRecord, tag)
                        ? 'bg-car-item border-car-accent text-car-accent'
                        : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
                    ]"
                  >
                    {{ isTagSelected(editingRecord, tag) ? '✓ ' + tag : '+ ' + tag }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部操作按钮 -->
      <template #footer>
        <button 
          type="button"
          @click="$emit('delete-record')"
          class="min-h-[50px] px-5 rounded-xl border-2 border-rose-500/40 bg-rose-500/10 text-rose-400 font-black text-[15px] hover:bg-rose-500/20 transition-all cursor-pointer whitespace-nowrap mr-auto"
        >
          删除此记录
        </button>

        <button 
          type="button"
          @click="$emit('close-edit')"
          class="min-h-[50px] px-6 rounded-xl border-2 border-car-border bg-car-card text-car-sub font-black text-[15.5px] hover:text-car-text transition-all cursor-pointer whitespace-nowrap"
        >
          取消
        </button>
        <button 
          type="button"
          @click="$emit('save-edit')"
          class="min-h-[50px] px-7 rounded-xl border-2 border-car-accent bg-car-item text-car-accent font-black text-[16px] hover:opacity-90 transition-all cursor-pointer shadow-lg whitespace-nowrap"
        >
          保存修改
        </button>
      </template>
    </ModalWrapper>

    <!-- 车规标准弹窗 2: 手动补录一笔加油台账 -->
    <ModalWrapper
      :show="showAddModal"
      title="手动补录一笔加油台账"
      badge="车载数字键盘"
      maxWidthClass="max-w-[1140px]"
      bodyClass="p-3.5 space-y-2.5"
      zIndexClass="z-[9990]"
      @close="$emit('close-add')"
    >
      <div v-if="newRecord" class="flex flex-col space-y-2.5">
        <!-- 顶部 4 列数值槽位 (触控切换焦点，完全禁止唤出车机系统输入法) -->
        <div class="grid grid-cols-4 gap-2.5">
          <!-- 实付金额 -->
          <div 
            @click="activeField = 'actual_paid'"
            :class="[
              'min-h-[52px] px-3.5 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
              activeField === 'actual_paid'
                ? 'border-car-accent bg-car-accent-light scale-[1.01]'
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
            <span class="text-[11.5px] font-mono font-bold text-car-sub">元</span>
          </div>

          <!-- 加注升数 -->
          <div 
            @click="activeField = 'liters'"
            :class="[
              'min-h-[52px] px-3.5 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
              activeField === 'liters'
                ? 'border-car-accent bg-car-accent-light scale-[1.01]'
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
            <span class="text-[11.5px] font-mono font-bold text-car-sub">L</span>
          </div>

          <!-- 实际单价 -->
          <div 
            @click="activeField = 'price'"
            :class="[
              'min-h-[52px] px-3.5 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
              activeField === 'price'
                ? 'border-car-accent bg-car-accent-light scale-[1.01]'
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
            <span class="text-[11.5px] font-mono font-bold text-car-sub">元/L</span>
          </div>

          <!-- 仪表里程 -->
          <div 
            @click="activeField = 'odo'"
            :class="[
              'min-h-[52px] px-3.5 py-1.5 rounded-xl border-2 flex items-center justify-between cursor-pointer transition-all shadow-sm',
              activeField === 'odo'
                ? 'border-car-accent bg-car-accent-light scale-[1.01]'
                : 'border-car-border bg-car-card hover:border-car-border-light'
            ]"
          >
            <div class="flex flex-col text-left">
              <span class="text-[11.5px] text-car-sub font-bold">仪表总里程</span>
              <div class="flex items-center space-x-1">
                <span class="text-[19px] font-mono font-black" :class="activeField === 'odo' ? 'text-car-accent' : 'text-car-text'">
                  {{ newRecord.odo !== undefined && newRecord.odo !== null ? newRecord.odo : '未打点' }}
                </span>
                <span v-if="activeField === 'odo'" class="w-1.5 h-4 bg-car-accent animate-pulse inline-block"></span>
              </div>
            </div>
            <span class="text-[11.5px] font-mono font-bold text-car-sub">km</span>
          </div>
        </div>

        <!-- 核心交互区：左侧车规数字键盘 + 右侧油品/加满/省份控制 (横向并列) -->
        <div class="grid grid-cols-12 gap-3 items-stretch">
          <!-- 左侧：车载专用大号数字键盘 (5列宽) -->
          <div class="col-span-5 p-2.5 rounded-2xl bg-car-card border-2 border-car-border shadow-lg flex flex-col space-y-1.5">
            <div class="flex items-center justify-between px-1 text-[12px] font-bold text-car-sub pb-1 border-b border-car-border/60">
              <span>当前输入: <b class="text-car-accent text-[13px]">{{ getFieldLabel(activeField) }}</b></span>
              <span class="text-[11px] text-car-sub/70">车规小键盘免输入法</span>
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
                type="button" 
                @click="onQuickAmount(newRecord, 300, 'new')" 
                class="min-h-[50px] rounded-xl border-2 border-car-border bg-car-item text-amber-400 font-mono text-[13.5px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
              >
                ¥300
              </button>
            </div>
          </div>

          <!-- 右侧：油品标号 + 加满状态 + 省份矩阵 + 快捷备注 (7列宽) -->
          <div class="col-span-7 flex flex-col justify-between space-y-2">
            <!-- 顶排：油品标号与加满状态 -->
            <div class="p-2 rounded-xl bg-car-card border-2 border-car-border flex items-center justify-between">
              <div class="flex items-center space-x-1.5 flex-1">
                <span class="text-[12px] text-car-sub font-bold whitespace-nowrap mr-1">标号:</span>
                <button
                  v-for="ft in ['92#', '95#', '98#', '0#']"
                  :key="ft"
                  type="button"
                  @click="newRecord.fuel_type = (ft === '0#' ? '0#柴油' : ft); $emit('new-fuel-type-change')"
                  :class="[
                    'flex-1 min-h-[50px] rounded-xl border-2 font-black text-[13.5px] transition-all flex items-center justify-center cursor-pointer',
                    (newRecord.fuel_type === ft || (ft === '0#' && newRecord.fuel_type === '0#柴油'))
                      ? 'bg-car-item border-car-accent text-car-accent shadow-sm'
                      : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
                  ]"
                >
                  {{ ft }}
                </button>
              </div>

              <div class="w-2"></div>

              <button 
                type="button"
                @click="newRecord.is_full = !newRecord.is_full" 
                :class="[
                  'min-h-[50px] px-3.5 rounded-xl border-2 font-black text-[13px] transition-all flex items-center justify-center cursor-pointer whitespace-nowrap',
                  newRecord.is_full
                    ? 'bg-emerald-500/15 border-emerald-500 text-emerald-400'
                    : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ newRecord.is_full ? '✓ 跳枪加满' : '阶段补油' }}
              </button>
            </div>

            <!-- 中排：全国 31 省市直选平铺矩阵 (紧凑 8 列) -->
            <div class="p-2 rounded-xl bg-car-item border border-car-border flex flex-col space-y-1">
              <div class="flex items-center justify-between text-[12px] text-car-sub font-bold px-0.5">
                <span>加油省份: <b class="text-car-accent text-[13px]">{{ newRecord.province }}</b></span>
                <span class="text-[10.5px] text-car-sub/70">一键对齐当地实时限价</span>
              </div>
              <div class="flex flex-wrap -m-0.5">
                <div v-for="prov in allProvinceNames" :key="prov" class="w-[12.5%] p-0.5">
                  <button 
                    type="button"
                    @click="newRecord.province = prov; $emit('new-province-change')"
                    :class="[
                      'w-full min-h-[50px] rounded-lg border font-black text-[12.5px] cursor-pointer transition-all flex items-center justify-center truncate',
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

            <!-- 底排：免打字组合备注控制台 (支持多选组合、反选取消、一键清空) -->
            <div class="p-2 rounded-xl bg-car-card border-2 border-car-border flex flex-col space-y-1.5">
              <div class="flex items-center justify-between text-[12px] font-bold text-car-sub px-0.5">
                <div class="flex items-center space-x-1.5 truncate">
                  <span class="whitespace-nowrap">组合备注:</span>
                  <span v-if="newRecord.note" class="text-car-accent font-black truncate max-w-[340px]">
                    {{ newRecord.note }}
                  </span>
                  <span v-else class="text-car-sub/60">可点下方标签多选组合</span>
                </div>
                <button
                  type="button"
                  @click="onClearNote(newRecord)"
                  class="min-h-[50px] px-3 rounded-lg border border-car-border bg-car-item text-car-sub hover:text-car-text text-[11.5px] font-bold cursor-pointer whitespace-nowrap"
                >
                  清空备注
                </button>
              </div>

              <!-- 15 个多选标签平铺 -->
              <div class="flex flex-wrap -m-0.5">
                <div v-for="tag in NOTE_TAGS" :key="tag" class="p-0.5">
                  <button 
                    type="button"
                    @click="toggleNoteTag(newRecord, tag)"
                    :class="[
                      'min-h-[50px] px-2.5 rounded-lg border font-black text-[12px] cursor-pointer transition-all flex items-center justify-center whitespace-nowrap shadow-sm',
                      isTagSelected(newRecord, tag)
                        ? 'bg-car-item border-car-accent text-car-accent'
                        : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
                    ]"
                  >
                    {{ isTagSelected(newRecord, tag) ? '✓ ' + tag : '+ ' + tag }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <button 
          type="button"
          @click="$emit('close-add')"
          class="min-h-[50px] px-6 rounded-xl border-2 border-car-border bg-car-card text-car-sub font-black text-[15.5px] hover:text-car-text transition-all cursor-pointer whitespace-nowrap"
        >
          取消
        </button>
        <button 
          type="button"
          @click="$emit('save-new')"
          class="min-h-[50px] px-8 rounded-xl border-2 border-car-accent bg-car-item text-car-accent font-black text-[16px] hover:opacity-90 transition-all cursor-pointer shadow-lg whitespace-nowrap"
        >
          确认记入账本
        </button>
      </template>
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

// 监听弹窗打开，重置聚焦到实付金额
watch(() => props.showAddModal, (val) => {
  if (val) activeField.value = 'actual_paid';
});
watch(() => props.showEditModal, (val) => {
  if (val) activeField.value = 'actual_paid';
});

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
    strVal = '';
  }

  // 小数点去重
  if (char === '.' && strVal.includes('.')) {
    return;
  }

  // 限制长度防溢出
  if (strVal.length >= 8) return;

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

// 快捷预设金额 (如 ¥200, ¥300)
function onQuickAmount(record, amount, mode) {
  if (!record) return;
  record.actual_paid = amount;
  activeField.value = 'actual_paid';
  handleFieldCalculation(record, 'actual_paid', mode);
}

// 联动自动折算
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
  '中石化', '中石油', '中海油', '壳牌', '民营小站', '山姆加油',
  '周五特惠', '会员折扣', '直降促销', '银行满减', '送洗车',
  '日常通勤', '高速服务区', '长途自驾', '加燃油宝'
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
