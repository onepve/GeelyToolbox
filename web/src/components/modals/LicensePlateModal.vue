<template>
  <ModalWrapper
    :show="show"
    title="爱车配对与车牌快速录入"
    badge="车规大屏专属"
    maxWidthClass="max-w-[1140px]"
    bodyClass="p-4 space-y-2.5"
    zIndexClass="z-[9990]"
    @close="$emit('close')"
  >
    <div class="flex flex-col space-y-2.5">
      <!-- 顶部紧凑卡片：硬件芯片码 + 8格车牌预览 + 绿牌/蓝牌切换 (横向集成，不占垂直高度) -->
      <div class="flex items-center justify-between p-3 rounded-2xl bg-car-card border-2 border-car-border">
        <!-- 左侧：硬件芯片识别码 (单行紧凑) -->
        <div class="flex items-center space-x-2">
          <span class="text-[12px] text-car-sub font-bold whitespace-nowrap">硬件芯片码:</span>
          <span class="text-[14px] font-mono font-black text-car-accent tracking-wide">{{ carId }}</span>
        </div>

        <!-- 中间：8格车牌实时预览 (车规大字体但高度紧凑 50px) -->
        <div class="flex items-center space-x-1.5 px-3 py-1 rounded-xl bg-car-item border border-car-border">
          <div
            v-for="(char, idx) in plateSlots"
            :key="idx"
            @click="onSlotClick(idx)"
            :class="[
              'w-[46px] h-[50px] rounded-xl border-2 flex items-center justify-center font-black transition-all shadow-sm cursor-pointer select-none',
              idx === currentSlotIndex
                ? 'border-car-accent bg-car-card scale-105 shadow-car-accent/20'
                : 'border-car-border bg-car-card/60 hover:border-car-border-light',
              idx === 0 ? 'text-[22px] text-car-accent' : 'text-[24px] font-mono text-car-text',
              isGreenPlate && idx === 7 ? 'text-emerald-400' : ''
            ]"
            :title="idx === 0 ? '点击选择省份简称' : '点击录入字母数字'"
          >
            {{ char || (idx === currentSlotIndex ? '·' : '') }}
          </div>
          <span class="text-[12px] text-car-sub font-bold pl-2 whitespace-nowrap">
            {{ currentSlotIndex + 1 }}/{{ maxSlots }}
          </span>
        </div>

        <!-- 右侧：新能源/蓝牌切换 -->
        <div class="flex items-center space-x-2">
          <button
            type="button"
            @click="isGreenPlate = !isGreenPlate"
            :class="[
              'min-h-[50px] px-4 rounded-xl border-2 font-black text-[13.5px] cursor-pointer transition-all flex items-center justify-center whitespace-nowrap',
              isGreenPlate
                ? 'bg-emerald-500/15 border-emerald-500 text-emerald-400 shadow-sm'
                : 'bg-car-item border-car-border text-car-sub'
            ]"
          >
            {{ isGreenPlate ? '新能源绿牌 (8位)' : '燃油车蓝牌 (7位)' }}
          </button>
        </div>
      </div>

      <!-- 车规大号软键盘区 (控制工具栏 + 全平铺按键) -->
      <div class="p-3.5 rounded-2xl bg-car-card border-2 border-car-border shadow-xl flex flex-col space-y-2">
        <!-- 键盘状态与操作栏 -->
        <div class="flex items-center justify-between pb-1.5 border-b border-car-border/60 px-1">
          <div class="flex items-center space-x-2">
            <span class="text-[14px] font-black text-car-text">
              {{ currentSlotIndex === 0 ? '第一步：请点选车辆所属省份简称' : '第二步：请点选车牌字母与数字' }}
            </span>
          </div>

          <div class="flex items-center space-x-2">
            <!-- 切换省份/字母数字键盘 -->
            <button
              type="button"
              @click="toggleKeyboardMode"
              class="min-h-[50px] px-4 rounded-xl border border-car-border bg-car-item text-car-sub hover:text-car-text text-[13px] font-bold cursor-pointer"
            >
              {{ keyboardMode === 'province' ? '切到字母/数字盘' : '切到省份简称盘' }}
            </button>

            <!-- 回退删除单个字符 -->
            <button
              type="button"
              @click="onBackspace"
              class="min-h-[50px] px-5 rounded-xl border-2 border-rose-500/40 bg-rose-500/10 text-rose-400 font-black text-[14.5px] cursor-pointer hover:bg-rose-500/20 active:scale-95 transition-all"
            >
              ⌫ 退格删除
            </button>

            <!-- 一键清空 -->
            <button
              type="button"
              @click="onClearAll"
              class="min-h-[50px] px-4 rounded-xl border border-car-border bg-car-item text-car-sub text-[13px] font-bold cursor-pointer"
            >
              清空
            </button>
          </div>
        </div>

        <!-- 键盘 1: 省份简称盘 (8列平铺，紧凑大按键) -->
        <div v-if="keyboardMode === 'province'" class="flex flex-wrap -m-0.5">
          <div
            v-for="prov in PROVINCE_KEYS"
            :key="prov"
            class="w-[12.5%] p-0.5"
          >
            <button
              type="button"
              @click="onSelectProvince(prov)"
              class="w-full min-h-[50px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text text-[18px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
            >
              {{ prov }}
            </button>
          </div>
        </div>

        <!-- 键盘 2: 字母与数字键盘 (行间紧凑) -->
        <div v-else class="flex flex-col space-y-1.5">
          <!-- 数字行 0-9 -->
          <div class="flex space-x-1.5">
            <button
              v-for="num in NUMBER_KEYS"
              :key="num"
              type="button"
              @click="onSelectAlphanumeric(num)"
              class="flex-1 min-h-[50px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-amber-400 font-mono text-[20px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
            >
              {{ num }}
            </button>
          </div>

          <!-- 字母行 1 (Q - P) -->
          <div class="flex space-x-1.5">
            <button
              v-for="lt in LETTER_ROW1"
              :key="lt"
              type="button"
              @click="onSelectAlphanumeric(lt)"
              class="flex-1 min-h-[50px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[19px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
            >
              {{ lt }}
            </button>
          </div>

          <!-- 字母行 2 (A - L) -->
          <div class="flex space-x-1.5 px-3">
            <button
              v-for="lt in LETTER_ROW2"
              :key="lt"
              type="button"
              @click="onSelectAlphanumeric(lt)"
              class="flex-1 min-h-[50px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[19px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
            >
              {{ lt }}
            </button>
          </div>

          <!-- 字母行 3 (Z - M) -->
          <div class="flex space-x-1.5 px-6">
            <button
              v-for="lt in LETTER_ROW3"
              :key="lt"
              type="button"
              @click="onSelectAlphanumeric(lt)"
              class="flex-1 min-h-[50px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[19px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
            >
              {{ lt }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部操作按钮 -->
    <template #footer>
      <button
        type="button"
        @click="$emit('close')"
        class="min-h-[50px] px-6 rounded-xl border-2 border-car-border bg-car-card text-car-sub font-black text-[15.5px] hover:text-car-text transition-all cursor-pointer whitespace-nowrap"
      >
        稍后再录
      </button>

      <button
        type="button"
        @click="onConfirmSave"
        :disabled="!isPlateComplete"
        :class="[
          'min-h-[50px] px-8 rounded-xl border-2 font-black text-[16px] transition-all cursor-pointer shadow-lg whitespace-nowrap flex items-center justify-center',
          isPlateComplete
            ? 'border-car-accent bg-car-item text-car-accent hover:opacity-95'
            : 'border-car-border bg-car-card text-car-sub opacity-50 cursor-not-allowed'
        ]"
      >
        ✓ 确认绑定车牌 ({{ assembledPlate || '未填满' }})
      </button>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import ModalWrapper from './ModalWrapper.vue';

const props = defineProps({
  show: {
    type: Boolean,
    default: false
  },
  carId: {
    type: String,
    default: ''
  },
  initialPlate: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['close', 'confirm']);

// 车牌字符数组
const plateChars = ref([]);
const isGreenPlate = ref(false);
const keyboardMode = ref('province'); // 'province' | 'alpha'

// 全国 31 个省市自治区简称
const PROVINCE_KEYS = [
  '浙', '粤', '京', '沪', '苏', '鲁', '豫', '川',
  '冀', '晋', '蒙', '辽', '吉', '黑', '皖', '闽',
  '赣', '鄂', '湘', '桂', '琼', '渝', '贵', '云',
  '藏', '陕', '甘', '青', '宁', '新', '临', '港'
];

// 数字按键
const NUMBER_KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'];

// 车牌排除了容易混淆的 I 和 O
const LETTER_ROW1 = ['Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'P'];
const LETTER_ROW2 = ['A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'];
const LETTER_ROW3 = ['Z', 'X', 'C', 'V', 'B', 'N', 'M'];

const maxSlots = computed(() => (isGreenPlate.value ? 8 : 7));

// 保证槽位长度一致
const plateSlots = computed(() => {
  const slots = [];
  for (let i = 0; i < maxSlots.value; i++) {
    slots.push(plateChars.value[i] || '');
  }
  return slots;
});

const currentSlotIndex = computed(() => {
  const len = plateChars.value.length;
  return len >= maxSlots.value ? maxSlots.value - 1 : len;
});

const assembledPlate = computed(() => plateChars.value.join(''));

const isPlateComplete = computed(() => plateChars.value.length === maxSlots.value);

// 监听弹窗打开，初始化车牌数据
watch(() => props.show, (newVal) => {
  if (newVal) {
    let raw = props.initialPlate ? String(props.initialPlate).trim() : '';
    if (!raw) {
      try {
        raw = localStorage.getItem('fuel_plate_number') || '';
      } catch (e) {}
    }
    const clean = raw.replace(/[·\s\-_]/g, '').toUpperCase();
    if (clean.length >= 7) {
      isGreenPlate.value = clean.length >= 8;
      plateChars.value = clean.split('').slice(0, isGreenPlate.value ? 8 : 7);
      keyboardMode.value = 'alpha';
    } else {
      plateChars.value = [];
      keyboardMode.value = 'province';
    }
  }
});

// 监听绿牌切换，截断多余字符
watch(isGreenPlate, (isGreen) => {
  const max = isGreen ? 8 : 7;
  if (plateChars.value.length > max) {
    plateChars.value = plateChars.value.slice(0, max);
  }
});

function toggleKeyboardMode() {
  keyboardMode.value = keyboardMode.value === 'province' ? 'alpha' : 'province';
}

function onSlotClick(idx) {
  if (idx === 0) {
    keyboardMode.value = 'province';
  } else {
    keyboardMode.value = 'alpha';
  }
}

function onSelectProvince(prov) {
  if (plateChars.value.length === 0) {
    plateChars.value.push(prov);
  } else {
    plateChars.value[0] = prov;
  }
  // 选完首字省份，自动切到字母数字大键盘
  keyboardMode.value = 'alpha';
}

function onSelectAlphanumeric(char) {
  if (plateChars.value.length < maxSlots.value) {
    plateChars.value.push(char);
  }
}

function onBackspace() {
  if (plateChars.value.length > 0) {
    plateChars.value.pop();
    if (plateChars.value.length === 0) {
      keyboardMode.value = 'province';
    }
  }
}

function onClearAll() {
  plateChars.value = [];
  keyboardMode.value = 'province';
}

function onConfirmSave() {
  if (!isPlateComplete.value) return;
  emit('confirm', assembledPlate.value);
  emit('close');
}
</script>
