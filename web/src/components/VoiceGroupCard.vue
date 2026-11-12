<template>
  <!--
    ==========================================================================
    VoiceGroupCard — 语音分组卡（v1.8.32.17）
    一套视觉，两种用途，彻底消灭「卡片套卡片」：

    用法 A · 部件分组（挡位 / 驾驶模式 / 尾门 / 引擎盖）
      1 张大卡 = 该部件的总开关，卡内 N 格并排各管一件事。
      点卡片头 → 该部件全部一起开 / 一起关（后端 voice_master_switch 语义不变，
      由调用方把 masterKey 传成对应的分组开关）。

    用法 B · 模式二选一（车门）
      twoModes 为真时，卡内不是零件格子，而是两张大按钮：极简合并 / 独立分门。
      点哪张就切到哪个模式，当前生效的那张亮起。

    交互契约：
    - 格内按钮全部 @click.stop，绝不误触外层总开关或被外层吞掉
    - 触控高度一律 >= 50px（车规门禁硬性要求）
    ==========================================================================
  -->
  <!-- 大卡只做「容器」，永远不铺整圈金光：金色是珍贵强调色，只分配给卡内真正被选中的那一格。
       原来 anyActive 时整张大卡变金边，内层格子也是金边，同一条边界亮两层，夜里一片金光反而没有焦点。 -->
  <div
    :class="[
      'rounded-3xl border-2 flex flex-col transition-all duration-150 overflow-hidden bg-car-card border-car-border shadow-sm'
    ]"
  >
    <!-- 卡片头 = 总开关（模式二选一时不可点，仅作标题） -->
    <div
      @click="modePickable ? null : onMasterToggle()"
      :class="[
        'flex items-center justify-between px-5 py-4 transition-all duration-150',
        modePickable ? 'cursor-default' : 'cursor-pointer select-none active:scale-[0.995]'
      ]"
    >
      <div class="flex items-center space-x-2.5 min-w-0">
        <span class="text-[20px] font-black text-car-text tracking-wide truncate">{{ title }}</span>
        <span
          v-if="tag"
          class="px-2.5 py-0.5 text-[12.5px] font-black rounded-full bg-car-accent/15 text-car-accent shrink-0"
        >
          {{ tag }}
        </span>
      </div>
      <div class="flex items-center space-x-2 shrink-0">
        <template v-if="!modePickable">
          <StatusDot size="md" :color="anyActive ? 'ok' : 'off'" :glow-px="anyActive ? 8 : 0" />
          <span :class="['text-[16px] font-black', anyActive ? 'text-car-text' : 'text-car-sub']">
            {{ headerText }}
          </span>
        </template>
        <span v-else class="text-[13px] text-car-sub font-bold">二选一</span>
      </div>
    </div>

    <!-- 卡身：模式二选一 -->
    <div v-if="modePickable" class="px-5 pb-5 grid grid-cols-2 gap-3.5 items-stretch">
      <div
        v-for="m in modes"
        :key="m.id"
        @click.stop="emit('pickMode', m.id)"
        :class="[
          'rounded-2xl px-4 py-4 flex flex-col justify-between transition-all duration-150 cursor-pointer select-none active:scale-[0.985]',
          m.active ? 'bg-car-item border-solid border-l-[5px] border-l-car-accent' : 'bg-car-item'
        ]"
      >
        <div class="flex items-center justify-between mb-2">
          <span class="text-[17px] font-black text-car-text">{{ m.name }}</span>
          <div class="flex items-center space-x-1.5 shrink-0">
            <StatusDot size="sm" :color="m.active ? 'ok' : 'off'" :glow-px="m.active ? 6 : 0" />
            <span :class="['text-[13px] font-black', m.active ? 'text-car-text' : 'text-car-sub']">
              {{ m.active ? '当前生效' : '未选用' }}
            </span>
          </div>
        </div>
        <div class="text-[13px] text-car-sub font-bold leading-relaxed">{{ m.desc }}</div>
      </div>
    </div>

    <!-- 卡身：零件格子 -->
    <div v-else class="px-5 pb-5" :class="cols === 1 ? '' : 'grid gap-3'"
         :style="cols === 1 ? '' : `grid-template-columns: repeat(${cols}, minmax(0, 1fr));`">
      <div
        v-for="item in items"
        :key="item.voiceKey"
        :class="[
          'rounded-2xl p-3.5 flex flex-col justify-between transition-all duration-150',
          item.active ? 'bg-car-item border-solid border-l-[4px] border-l-car-accent' : 'bg-car-item opacity-85'
        ]"
      >
        <!-- 格头：名称 + 独立开关小圆点 -->
        <div class="flex items-center justify-between mb-1.5 cursor-pointer select-none active:scale-[0.98]"
             @click.stop="emit('toggleItem', item.voiceKey)">
          <span class="text-[15.5px] font-black text-car-text">{{ item.label }}</span>
          <div class="flex items-center space-x-1.5 shrink-0">
            <StatusDot size="sm" :color="item.active ? 'ok' : 'off'" :glow-px="item.active ? 5 : 0" />
            <span :class="['text-[12.5px] font-black', item.active ? 'text-car-text' : 'text-car-sub']">
              {{ item.active ? '已开启' : '已关闭' }}
            </span>
          </div>
        </div>

        <div class="text-[12.5px] text-car-sub font-bold leading-snug mb-2.5">{{ item.line }}</div>

        <div class="flex items-center space-x-2">
          <button
            type="button"
            @click.stop="emit('test', item.voiceKey)"
            class="flex-1 h-[50px] rounded-xl bg-car-card text-car-text font-black text-[13px] cursor-pointer active:scale-95 transition-all"
          >
            试听
          </button>
          <button
            type="button"
            @click.stop="emit('custom', item.voiceKey)"
            class="flex-1 h-[50px] rounded-xl bg-car-card text-car-sub hover:text-car-text font-black text-[13px] cursor-pointer active:scale-95 transition-all"
          >
            换声音
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// ============================================================================
// VoiceGroupCard — 语音分组卡
// voiceKey：喂给后端 testVehicleVoice 的语音名（gear_p / door_fl …）
// settingKey：可选的独立开关设置名（voice_enable_gear_p …），不传则以 voiceKey 推断
// ============================================================================
import StatusDot from './StatusDot.vue';
import { computed } from 'vue';
import { playTouchFeedback } from '../utils/sound';

const props = defineProps({
  title: { type: String, required: true },
  tag: { type: String, default: '' },
  // items: [{ voiceKey, settingKey, label, line, active }]
  items: { type: Array, default: () => [] },
  cols: { type: Number, default: 4 },
  // 模式二选一（车门专用）
  modes: { type: Array, default: () => [] },
  modePickable: { type: Boolean, default: false }
});

const emit = defineEmits(['masterToggle', 'toggleItem', 'test', 'custom', 'pickMode']);

const anyActive = computed(() => props.items.some((i) => !!i.active));

const headerText = computed(() => {
  const n = props.items.filter((i) => !!i.active).length;
  if (n === 0) return '全部已关闭';
  if (n === props.items.length) return '全部已开启';
  return `${n}/${props.items.length} 已开启`;
});

function onMasterToggle() {
  playTouchFeedback();
  emit('masterToggle');
}
</script>
