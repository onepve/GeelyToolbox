<template>
  <ModalWrapper
    :show="show"
    title="爱车配对与车牌快速录入"
    badge="车规大屏专属"
    maxWidthClass="max-w-[960px]"
    zIndexClass="z-[9990]"
    @close="$emit('close')"
  >
    <div class="flex flex-col space-y-5 py-1">
      <!-- 顶部：当前车辆出厂硬件唯一标识卡片 -->
      <div class="flex items-center justify-between p-4 rounded-2xl bg-car-card border-2 border-car-border">
        <div class="flex flex-col">
          <span class="text-[13px] text-car-sub font-bold">原车出厂硬件芯片唯一编码 (只读不可篡改)</span>
          <span class="text-[20px] font-mono font-black text-car-accent tracking-wider mt-0.5">{{ carId }}</span>
        </div>
        <div class="flex items-center space-x-3">
          <!-- 牌照类型切换按钮 (大触控区) -->
          <button
            type="button"
            @click="isGreenPlate = !isGreenPlate"
            :class="[
              'min-h-[52px] px-5 rounded-xl border-2 font-black text-[15px] cursor-pointer transition-all flex items-center justify-center whitespace-nowrap',
              isGreenPlate
                ? 'bg-emerald-500/15 border-emerald-500 text-emerald-400 shadow-md'
                : 'bg-car-item border-car-border text-car-sub'
            ]"
          >
            {{ isGreenPlate ? '新能源绿牌 (8位)' : '燃油车蓝牌 (7位)' }}
          </button>
        </div>
      </div>

      <!-- 车牌展示框（超大字体、高对比车规级车牌预览） -->
      <div class="flex flex-col items-center justify-center p-5 rounded-3xl bg-car-item border-2 border-car-border/80 shadow-inner">
        <div class="flex items-center space-x-2">
          <!-- 逐格展示车牌字符 -->
          <div
            v-for="(char, idx) in plateSlots"
            :key="idx"
            :class="[
              'w-[64px] h-[72px] rounded-2xl border-2 flex items-center justify-center font-black transition-all shadow-md',
              idx === currentSlotIndex
                ? 'border-car-accent bg-car-card scale-105 shadow-car-accent/20'
                : 'border-car-border bg-car-card/60',
              idx === 0 ? 'text-[28px] text-car-accent' : 'text-[32px] font-mono text-car-text',
              isGreenPlate && idx === 7 ? 'text-emerald-400' : ''
            ]"
          >
            {{ char || (idx === currentSlotIndex ? '·' : '') }}
          </div>
        </div>

        <div class="flex items-center space-x-4 mt-3">
          <span class="text-[13.5px] text-car-sub font-bold">
            当前录入：第 <b class="text-car-accent">{{ currentSlotIndex + 1 }}</b> 位 / 共 {{ maxSlots }} 位
          </span>
          <span class="text-[12px] text-car-sub/70">
            (支持车机大屏点选，录完点击保存即可关联硬件)
          </span>
        </div>
      </div>

      <!-- 车规大号软键盘区 -->
      <div class="p-4 rounded-3xl bg-car-card border-2 border-car-border shadow-xl flex flex-col space-y-3">
        <!-- 键盘状态与操作栏 -->
        <div class="flex items-center justify-between pb-2 border-b border-car-border/60 px-1">
          <div class="flex items-center space-x-2">
            <span class="text-[15px] font-black text-car-text">
              {{ currentSlotIndex === 0 ? '第一步：请点选车辆所属省份/直辖市' : '第二步：请点选车牌字母与数字' }}
            </span>
          </div>

          <div class="flex items-center space-x-2">
            <!-- 切换省份/字母数字键盘 -->
            <button
              type="button"
              @click="toggleKeyboardMode"
              class="min-h-[50px] px-4 rounded-xl border border-car-border bg-car-item text-car-sub hover:text-car-text text-[14px] font-bold cursor-pointer"
            >
              {{ keyboardMode === 'province' ? '切到字母/数字盘' : '切到省份简称盘' }}
            </button>

            <!-- 回退删除单个字符 -->
            <button
              type="button"
              @click="onBackspace"
              class="min-h-[50px] px-5 rounded-xl border-2 border-rose-500/40 bg-rose-500/10 text-rose-400 font-black text-[15px] cursor-pointer hover:bg-rose-500/20 active:scale-95 transition-all"
            >
              ⌫ 退格删除
            </button>

            <!-- 一键清空 -->
            <button
              type="button"
              @click="onClearAll"
              class="min-h-[50px] px-4 rounded-xl border border-car-border bg-car-item text-car-sub text-[14px] font-bold cursor-pointer"
            >
              清空
            </button>
          </div>
        </div>

        <!-- 键盘 1: 省份简称盘 (大按键、全平铺) -->
        <div v-if="keyboardMode === 'province'" class="flex flex-wrap -m-1">
          <div
            v-for="prov in PROVINCE_KEYS"
            :key="prov"
            class="w-[12.5%] p-1"
          >
            <button
              type="button"
              @click="onSelectProvince(prov)"
              class="w-full min-h-[56px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text text-[20px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
            >
              {{ prov }}
            </button>
          </div>
        </div>

        <!-- 键盘 2: 字母与数字键盘 (大尺寸车规按键) -->
        <div v-else class="flex flex-col space-y-2">
          <!-- 数字行 0-9 -->
          <div class="flex space-x-2">
            <button
              v-for="num in NUMBER_KEYS"
              :key="num"
              type="button"
              @click="onSelectAlphanumeric(num)"
              class="flex-1 min-h-[54px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-amber-400 font-mono text-[22px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
            >
              {{ num }}
            </button>
          </div>

          <!-- 字母行 1 (Q - P) -->
          <div class="flex space-x-2">
            <button
              v-for="lt in LETTER_ROW1"
              :key="lt"
              type="button"
              @click="onSelectAlphanumeric(lt)"
              class="flex-1 min-h-[54px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[21px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
            >
              {{ lt }}
            </button>
          </div>

          <!-- 字母行 2 (A - L) -->
          <div class="flex space-x-2 px-3">
            <button
              v-for="lt in LETTER_ROW2"
              :key="lt"
              type="button"
              @click="onSelectAlphanumeric(lt)"
              class="flex-1 min-h-[54px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[21px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
            >
              {{ lt }}
            </button>
          </div>

          <!-- 字母行 3 (Z - M + 特殊新能源标识) -->
          <div class="flex space-x-2 px-6">
            <button
              v-for="lt in LETTER_ROW3"
              :key="lt"
              type="button"
              @click="onSelectAlphanumeric(lt)"
              class="flex-1 min-h-[54px] rounded-xl border-2 border-car-border bg-car-item hover:border-car-accent text-car-text font-mono text-[21px] font-black flex items-center justify-center cursor-pointer transition-all shadow-sm"
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
        class="min-h-[54px] px-7 rounded-xl border-2 border-car-border bg-car-card text-car-sub font-black text-[16px] hover:text-car-text transition-all cursor-pointer whitespace-nowrap"
      >
        稍后再录
      </button>

      <button
        type="button"
        @click="onConfirmSave"
        :disabled="!isPlateComplete"
        :class="[
          'min-h-[54px] px-9 rounded-xl border-2 font-black text-[17px] transition-all cursor-pointer shadow-lg whitespace-nowrap flex items-center justify-center',
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
    if (props.initialPlate && props.initialPlate.length >= 7) {
      isGreenPlate.value = props.initialPlate.length >= 8;
      plateChars.value = props.initialPlate.split('').slice(0, maxSlots.value);
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
}
</script>
