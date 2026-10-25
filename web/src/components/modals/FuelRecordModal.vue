<template>
  <div>
    <!-- 车规标准弹窗 1: 编辑/修改单笔加油台账 -->
    <ModalWrapper
      :show="showEditModal"
      :title="'编辑加油台账 (ID: ' + editingRecord.id + ')'"
      badge="流水微调"
      maxWidthClass="max-w-[1120px]"
      bodyClass="p-4 space-y-3"
      zIndexClass="z-[9990]"
      @close="$emit('close-edit')"
    >
      <div class="flex flex-col space-y-3">
        <!-- 核心数字录入 (4列横向一字排开，输入框长度适中精致，彻底告别臃肿过长) -->
        <div class="grid grid-cols-4 gap-3">
          <!-- 实付金额 (车主最常用) -->
          <div class="flex flex-col space-y-1">
            <div class="flex items-center justify-between text-[13px] text-car-sub font-bold px-0.5">
              <span>实付金额 *</span>
              <span class="text-[11px] font-mono text-car-sub/70">元</span>
            </div>
            <input 
              type="number" 
              step="0.1" 
              v-model.number="editingRecord.actual_paid" 
              @input="$emit('edit-paid-change')"
              placeholder="如 200"
              class="h-[46px] px-3.5 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[17px] font-mono font-black focus:border-car-accent outline-none" 
            />
          </div>

          <!-- 加注升数 (L) -->
          <div class="flex flex-col space-y-1">
            <div class="flex items-center justify-between text-[13px] text-car-sub font-bold px-0.5">
              <span>加注升数 *</span>
              <span class="text-[11px] font-mono text-car-sub/70">L</span>
            </div>
            <input 
              type="number" 
              step="0.01" 
              v-model.number="editingRecord.liters" 
              @input="$emit('edit-liters-change')"
              placeholder="如 26.5"
              class="h-[46px] px-3.5 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[17px] font-mono font-black focus:border-car-accent outline-none" 
            />
          </div>

          <!-- 单价 (元/L) -->
          <div class="flex flex-col space-y-1">
            <div class="flex items-center justify-between text-[13px] text-car-sub font-bold px-0.5">
              <span>实际单价</span>
              <span class="text-[11px] font-mono text-car-sub/70">元/L</span>
            </div>
            <input 
              type="number" 
              step="0.01" 
              v-model.number="editingRecord.price" 
              @input="$emit('edit-price-change')"
              placeholder="如 7.55"
              class="h-[46px] px-3.5 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[17px] font-mono font-black focus:border-car-accent outline-none" 
            />
          </div>

          <!-- 仪表盘里程 (km) -->
          <div class="flex flex-col space-y-1">
            <div class="flex items-center justify-between text-[13px] text-car-sub font-bold px-0.5">
              <span>仪表盘总里程</span>
              <span class="text-[11px] font-mono text-car-sub/70">km</span>
            </div>
            <input 
              type="number" 
              v-model.number="editingRecord.odo" 
              placeholder="如 35200"
              class="h-[46px] px-3.5 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[17px] font-mono font-black focus:border-car-accent outline-none" 
            />
          </div>
        </div>

        <!-- 左右并排双栏区域 (充分利用宽屏横向空间，消灭左右留白与上下滚动) -->
        <div class="grid grid-cols-12 gap-3.5 items-stretch">
          <!-- 左栏 (5列宽：油品标号 + 加满状态 + 快捷备注) -->
          <div class="col-span-5 flex flex-col justify-between space-y-2.5">
            <!-- 油品与加满状态 -->
            <div class="flex flex-col space-y-1.5">
              <span class="text-[13px] text-car-sub font-bold">油品标号与加满状态</span>
              <div class="flex space-x-2">
                <div class="flex space-x-1.5 flex-1">
                  <button
                    v-for="ft in ['92#', '95#', '98#', '0#柴油']"
                    :key="ft"
                    type="button"
                    @click="editingRecord.fuel_type = ft; $emit('edit-fuel-type-change')"
                    :class="[
                      'flex-1 min-h-[50px] rounded-xl border-2 font-black text-[13px] transition-all flex items-center justify-center cursor-pointer',
                      editingRecord.fuel_type === ft
                        ? 'bg-car-item border-car-accent text-car-accent'
                        : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
                    ]"
                  >
                    {{ ft === '0#柴油' ? '0#' : ft }}
                  </button>
                </div>
                <button 
                  type="button"
                  @click="editingRecord.is_full = !editingRecord.is_full" 
                  :class="[
                    'min-h-[50px] px-3 rounded-xl border-2 font-black text-[13px] transition-all flex items-center justify-center cursor-pointer whitespace-nowrap',
                    editingRecord.is_full
                      ? 'bg-emerald-500/15 border-emerald-500 text-emerald-400'
                      : 'bg-car-card border-car-border text-car-sub'
                  ]"
                >
                  {{ editingRecord.is_full ? '✓ 跳枪加满' : '阶段未满' }}
                </button>
              </div>
            </div>

            <!-- 备注说明：免打字标签 + 输入框 -->
            <div class="flex flex-col space-y-1.5">
              <span class="text-[13px] text-car-sub font-bold">备注说明 (免打字快捷点击)</span>
              <div class="flex flex-wrap -m-1">
                <div v-for="tag in ['中石化优惠', '中石油加注', '周五特惠', '高速服务区', '长途自驾']" :key="tag" class="p-1">
                  <button 
                    type="button"
                    @click="editingRecord.note = tag"
                    class="min-h-[50px] px-3 rounded-xl bg-car-card border border-car-border text-car-sub hover:text-car-accent hover:border-car-accent text-[12px] font-bold cursor-pointer transition-all"
                  >
                    + {{ tag }}
                  </button>
                </div>
              </div>
              <input 
                type="text" 
                v-model="editingRecord.note" 
                placeholder="也可手动输入备注，或留空在手机端直接编辑"
                class="h-[44px] px-3 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[13.5px] font-bold focus:border-car-accent outline-none" 
              />
            </div>
          </div>

          <!-- 右栏 (7列宽：平铺全国 31 个省市直选矩阵，8列紧凑网格) -->
          <div class="col-span-7 p-3 rounded-2xl bg-car-item border border-car-border flex flex-col justify-between">
            <div class="flex items-center justify-between text-[13px] text-car-sub font-bold mb-1 px-0.5">
              <span>加油省份/直辖市 (当前: <b class="text-car-accent text-[14px]">{{ editingRecord.province }}</b>)</span>
              <span class="text-[11.5px] text-car-sub/70">一键对齐当地实时限价</span>
            </div>
            <div class="flex flex-wrap -m-0.5">
              <div v-for="prov in allProvinceNames" :key="prov" class="w-[12.5%] p-0.5">
                <button 
                  type="button"
                  @click="editingRecord.province = prov; $emit('edit-province-change')"
                  :class="[
                    'w-full min-h-[50px] rounded-lg border font-black text-[13px] cursor-pointer transition-all flex items-center justify-center truncate',
                    editingRecord.province === prov
                      ? 'bg-car-accent border-car-accent text-car-text shadow-sm'
                      : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light hover:text-car-text'
                  ]"
                >
                  {{ prov }}
                </button>
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
      badge="快速补录"
      maxWidthClass="max-w-[1120px]"
      bodyClass="p-4 space-y-3"
      zIndexClass="z-[9990]"
      @close="$emit('close-add')"
    >
      <div class="flex flex-col space-y-3">
        <!-- 快捷加注预设 + 核心数字录入 (4列横排) -->
        <div class="flex flex-col space-y-1.5">
          <div class="flex items-center justify-between px-0.5">
            <span class="text-[13px] text-car-sub font-bold">常用快捷加注金额 (点击自动折算升数):</span>
            <div class="flex items-center space-x-2">
              <button 
                v-for="amt in [100, 200, 300]" 
                :key="amt"
                type="button"
                @click="$emit('quick-amount', amt)"
                class="min-h-[50px] px-4 rounded-xl bg-car-card border border-car-border text-car-text font-mono font-black text-[13.5px] hover:border-car-accent flex items-center justify-center cursor-pointer transition-all"
              >
                ¥{{ amt }}
              </button>
              <button 
                type="button"
                @click="$emit('quick-full')"
                class="min-h-[50px] px-4 rounded-xl bg-car-card border border-emerald-400/50 text-emerald-400 font-black text-[13.5px] hover:border-emerald-400 flex items-center justify-center cursor-pointer transition-all"
              >
                加满整箱
              </button>
            </div>
          </div>

          <div class="grid grid-cols-4 gap-3">
            <!-- 实付金额 -->
            <div class="flex flex-col space-y-1">
              <div class="flex items-center justify-between text-[13px] text-car-sub font-bold px-0.5">
                <span>实付金额 *</span>
                <span class="text-[11px] font-mono text-car-sub/70">元</span>
              </div>
              <input 
                type="number" 
                step="0.1" 
                v-model.number="newRecord.actual_paid" 
                @input="$emit('new-paid-change')"
                placeholder="如 200"
                class="h-[46px] px-3.5 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[17px] font-mono font-black focus:border-car-accent outline-none" 
              />
            </div>

            <!-- 加注升数 -->
            <div class="flex flex-col space-y-1">
              <div class="flex items-center justify-between text-[13px] text-car-sub font-bold px-0.5">
                <span>加注升数 *</span>
                <span class="text-[11px] font-mono text-car-sub/70">L</span>
              </div>
              <input 
                type="number" 
                step="0.01" 
                v-model.number="newRecord.liters" 
                @input="$emit('new-liters-change')"
                placeholder="如 26.5"
                class="h-[46px] px-3.5 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[17px] font-mono font-black focus:border-car-accent outline-none" 
              />
            </div>

            <!-- 单价 -->
            <div class="flex flex-col space-y-1">
              <div class="flex items-center justify-between text-[13px] text-car-sub font-bold px-0.5">
                <span>实际单价</span>
                <span class="text-[11px] font-mono text-car-sub/70">元/L</span>
              </div>
              <input 
                type="number" 
                step="0.01" 
                v-model.number="newRecord.price" 
                @input="$emit('new-price-change')"
                placeholder="如 7.55"
                class="h-[46px] px-3.5 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[17px] font-mono font-black focus:border-car-accent outline-none" 
              />
            </div>

            <!-- 仪表盘里程 -->
            <div class="flex flex-col space-y-1">
              <div class="flex items-center justify-between text-[13px] text-car-sub font-bold px-0.5">
                <span>仪表盘总里程</span>
                <span class="text-[11px] font-mono text-car-sub/70">km</span>
              </div>
              <input 
                type="number" 
                v-model.number="newRecord.odo" 
                placeholder="如 35200"
                class="h-[46px] px-3.5 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[17px] font-mono font-black focus:border-car-accent outline-none" 
              />
            </div>
          </div>
        </div>

        <!-- 左右并排双栏区域 (横向充分利用，告别上下拖动) -->
        <div class="grid grid-cols-12 gap-3.5 items-stretch">
          <!-- 左栏 (5列宽：油品标号 + 加满状态 + 快捷备注) -->
          <div class="col-span-5 flex flex-col justify-between space-y-2.5">
            <!-- 油品与加满状态 -->
            <div class="flex flex-col space-y-1.5">
              <span class="text-[13px] text-car-sub font-bold">油品标号与加满状态</span>
              <div class="flex space-x-2">
                <div class="flex space-x-1.5 flex-1">
                  <button
                    v-for="ft in ['92#', '95#', '98#', '0#柴油']"
                    :key="ft"
                    type="button"
                    @click="newRecord.fuel_type = ft; $emit('new-fuel-type-change')"
                    :class="[
                      'flex-1 min-h-[50px] rounded-xl border-2 font-black text-[13px] transition-all flex items-center justify-center cursor-pointer',
                      newRecord.fuel_type === ft
                        ? 'bg-car-item border-car-accent text-car-accent'
                        : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
                    ]"
                  >
                    {{ ft === '0#柴油' ? '0#' : ft }}
                  </button>
                </div>
                <button 
                  type="button"
                  @click="newRecord.is_full = !newRecord.is_full" 
                  :class="[
                    'min-h-[50px] px-3 rounded-xl border-2 font-black text-[13px] transition-all flex items-center justify-center cursor-pointer whitespace-nowrap',
                    newRecord.is_full
                      ? 'bg-emerald-500/15 border-emerald-500 text-emerald-400'
                      : 'bg-car-card border-car-border text-car-sub'
                  ]"
                >
                  {{ newRecord.is_full ? '✓ 跳枪加满' : '阶段未满' }}
                </button>
              </div>
            </div>

            <!-- 备注说明：免打字标签 + 输入框 -->
            <div class="flex flex-col space-y-1.5">
              <span class="text-[13px] text-car-sub font-bold">备注说明 (免打字快捷点击)</span>
              <div class="flex flex-wrap -m-1">
                <div v-for="tag in ['中石化优惠', '中石油加注', '周五特惠', '高速服务区', '长途自驾']" :key="tag" class="p-1">
                  <button 
                    type="button"
                    @click="newRecord.note = tag"
                    class="min-h-[50px] px-3 rounded-xl bg-car-card border border-car-border text-car-sub hover:text-car-accent hover:border-car-accent text-[12px] font-bold cursor-pointer transition-all"
                  >
                    + {{ tag }}
                  </button>
                </div>
              </div>
              <input 
                type="text" 
                v-model="newRecord.note" 
                placeholder="也可手动输入备注，或留空在手机端直接编辑"
                class="h-[44px] px-3 rounded-xl bg-car-card border-2 border-car-border text-car-text text-[13.5px] font-bold focus:border-car-accent outline-none" 
              />
            </div>
          </div>

          <!-- 右栏 (7列宽：平铺全国 31 个省市直选矩阵，8列紧凑网格) -->
          <div class="col-span-7 p-3 rounded-2xl bg-car-item border border-car-border flex flex-col justify-between">
            <div class="flex items-center justify-between text-[13px] text-car-sub font-bold mb-1 px-0.5">
              <span>加油省份/直辖市 (当前: <b class="text-car-accent text-[14px]">{{ newRecord.province }}</b>)</span>
              <span class="text-[11.5px] text-car-sub/70">一键对齐当地实时限价</span>
            </div>
            <div class="flex flex-wrap -m-0.5">
              <div v-for="prov in allProvinceNames" :key="prov" class="w-[12.5%] p-0.5">
                <button 
                  type="button"
                  @click="newRecord.province = prov; $emit('new-province-change')"
                  :class="[
                    'w-full min-h-[50px] rounded-lg border font-black text-[13px] cursor-pointer transition-all flex items-center justify-center truncate',
                    newRecord.province === prov
                      ? 'bg-car-accent border-car-accent text-car-text shadow-sm'
                      : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light hover:text-car-text'
                  ]"
                >
                  {{ prov }}
                </button>
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
import ModalWrapper from './ModalWrapper.vue';

defineProps({
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

defineEmits([
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
</script>
