<template>
  <ModalWrapper 
    :show="store.modals.battery" 
    title="缤越助手 · 12V 蓄电池与充能健康看板" 
    badge="CAN 物理采样"
    maxWidthClass="max-w-[760px]"
    @close="closeModal('battery')"
  >
    <div class="flex flex-col space-y-5">
      <!-- 核心电压大看板卡片 -->
      <div class="p-6 rounded-3xl bg-car-item border-2 border-car-border flex flex-col items-center justify-center text-center shadow-md relative overflow-hidden">
        <div class="flex items-center mb-3">
          <span :class="['text-[15.5px] px-4 py-1.5 rounded-full font-black tracking-wide shadow-sm flex items-center', batteryStatus.badgeClass]">
            <span :class="['w-2 h-2 rounded-full mr-2', batteryStatus.dotClass]"></span>
            {{ batteryStatus.text }}
          </span>
        </div>

        <!-- 大字号电压直出 -->
        <div class="flex items-baseline justify-center my-1">
          <span class="text-[64px] font-black text-car-text leading-none font-mono">{{ batteryStatus.voltStr }}</span>
          <span v-if="batteryStatus.hasVolt" class="text-[24px] text-car-sub font-black ml-2">V</span>
        </div>

        <span class="text-[17px] text-car-sub font-bold mt-2">{{ batteryStatus.desc }}</span>
      </div>

      <!-- 车规级 12V 蓄电池三阶标准说明 (向右流式指引) -->
      <div class="p-5 rounded-2xl bg-car-card border border-car-border text-car-sub font-bold space-y-2 text-[14.5px] leading-relaxed">
        <div class="text-[16px] text-car-text font-black mb-1 flex items-center">
          <span class="mr-2">⚡</span> 车规级蓄电池电压安全参考标准：
        </div>
        <div class="flex items-center text-emerald-400">
          <span class="w-2 h-2 rounded-full bg-emerald-500 mr-2 shrink-0"></span>
          <span><b>≥ 11.8V（健康充沛）</b>：熄火电量充足；启动后发电机供电正常升至 13.5V~14.5V。</span>
        </div>
        <div class="flex items-center text-amber-400">
          <span class="w-2 h-2 rounded-full bg-amber-500 mr-2 shrink-0"></span>
          <span><b>11.5V ~ 11.8V（低电警戒）</b>：剩余电量较低，建议发动车辆运转充能。</span>
        </div>
        <div class="flex items-center text-rose-400">
          <span class="w-2 h-2 rounded-full bg-rose-500 mr-2 shrink-0"></span>
          <span><b>&lt; 11.5V（重度亏电）</b>：面临打不着火风险，请立即关闭大灯及高功耗电器。</span>
        </div>
      </div>

      <!-- 电瓶健康看板一期：静置电压/启动压降/充电平台（Java 侧被动采样，本层只渲染分级结论） -->
      <div class="p-5 rounded-2xl bg-car-item border-2 border-car-border shadow-sm">
        <div class="flex items-center justify-between mb-3">
          <div class="text-[16px] text-car-text font-black flex items-center">
            <span class="mr-2">🔋</span> 健康采样
          </div>
          <div class="flex items-baseline" v-if="health && health.score >= 0">
            <span class="text-[26px] font-black font-mono leading-none"
                  :class="health.score >= 85 ? 'text-emerald-400' : (health.score >= 65 ? 'text-amber-400' : 'text-rose-400')">{{ health.score }}</span>
            <span class="text-[13px] text-car-sub font-bold ml-1.5">分 · {{ health.scoreText }}</span>
          </div>
          <div class="text-[13px] text-car-sub font-bold" v-else>采样积累中</div>
        </div>

        <div class="grid grid-cols-3 gap-3">
          <div v-for="m in healthItems" :key="m.key"
               class="p-3 rounded-xl bg-car-card border border-car-border text-center">
            <div class="text-[13px] text-car-sub font-black mb-1 whitespace-nowrap">{{ m.label }}</div>
            <div class="text-[20px] font-black font-mono leading-none mb-1.5" :class="gradeTextClass(m.grade)">
              <template v-if="m.value !== null">{{ m.value }}<span class="text-[12px] ml-0.5">V</span></template>
              <template v-else>--</template>
            </div>
            <div class="text-[12px] font-bold whitespace-nowrap" :class="gradeTextClass(m.grade)">{{ m.gradeText }}</div>
          </div>
        </div>

        <div class="text-[12px] text-car-sub font-bold mt-3 leading-relaxed opacity-80">
          采样说明：熄火停放满 3 分钟记录静置电压；点火瞬间电压低谷记为启动压降；行车充电后自动记录发电机回充平台。数据长期积累，参考越准。
        </div>
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-between w-full">
        <button 
          @click="refreshVolt"
          class="min-h-[58px] px-8 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[17px] cursor-pointer hover:border-car-border-light shadow-sm"
        >
          刷新实时采样
        </button>
        <button 
          @click="closeModal('battery')"
          class="min-h-[58px] px-10 bg-car-item border-2 border-car-border rounded-xl text-car-text font-black text-[18px] cursor-pointer hover:border-car-border-light shadow-sm"
        >
          关闭
        </button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, showToast } from '../../store';

let voltTimer = null;
const health = ref(null);

// 电瓶健康一期三项（Java 侧 BatteryHealthMonitor 已算好分级，本层纯渲染）
const healthItems = computed(() => {
  const h = health.value || {};
  const rest = h.rest || {}, crank = h.crank || {}, charge = h.charge || {};
  return [
    { key: 'rest', label: '静置电压', value: rest.v != null ? rest.v.toFixed(1) : null, grade: rest.grade || 0, gradeText: rest.gradeText || '待采样' },
    { key: 'crank', label: '启动压降', value: crank.v != null ? crank.v.toFixed(1) : null, grade: crank.grade || 0, gradeText: crank.gradeText || '待点火采样' },
    { key: 'charge', label: '充电平台', value: charge.avg != null ? (Math.round(charge.avg * 10) / 10).toFixed(1) : null, grade: charge.grade || 0, gradeText: charge.gradeText || '待行驶采样' }
  ];
});

function gradeTextClass(g) {
  if (g === 1 || g === 2) return 'text-emerald-400';
  if (g === 3) return 'text-amber-400';
  if (g === 4) return 'text-rose-400';
  return 'text-car-sub';
}

function fetchHealth() {
  try {
    const raw = bridge.call('getBatteryHealth');
    if (raw) {
      health.value = typeof raw === 'string' ? JSON.parse(raw) : raw;
    }
  } catch (e) {}
}

const batteryStatus = computed(() => {
  let v = null;
  if (store.batteryVoltage && store.batteryVoltage > 0) {
    v = store.batteryVoltage;
  } else if (store.deviceInfo.real_battery_volt && store.deviceInfo.real_battery_volt > 0) {
    v = store.deviceInfo.real_battery_volt;
  } else if (store.deviceInfo.battery_volt && store.deviceInfo.battery_volt > 0) {
    v = store.deviceInfo.battery_volt / 10.0;
  }

  // 车规安全区间校验：必须在 9.0V ~ 16.5V 范围内，低于 9V 坚决视为未就绪，彻底杜绝 4.2V 假报警
  if (!v || v < 9.0 || v > 16.5) {
    return {
      hasVolt: false,
      voltStr: '--',
      text: '传感器采集中...',
      desc: '正在监听车身 CAN 总线物理电压报文，请稍候...',
      badgeClass: 'bg-car-item border border-car-border text-car-text',
      dotClass: 'bg-amber-400 animate-pulse shadow-[0_0_6px_#F59E0B]'
    };
  }

  const voltStr = v.toFixed(1);
  const isCharging = v >= 13.4;

  if (isCharging) {
    return {
      hasVolt: true,
      voltStr,
      text: '发电机充能中',
      desc: '发动机已启动，车载发电机正在为蓄电池持续回充',
      badgeClass: 'bg-car-item border border-car-border text-car-text',
      dotClass: 'bg-emerald-500 animate-pulse shadow-[0_0_6px_#10B981]'
    };
  } else if (v >= 11.8) {
    return {
      hasVolt: true,
      voltStr,
      text: '健康充沛',
      desc: '12V 蓄电池状态健康，剩余电量充裕',
      badgeClass: 'bg-car-item border border-car-border text-car-text',
      dotClass: 'bg-emerald-500 shadow-[0_0_6px_#10B981]'
    };
  } else if (v >= 11.5) {
    return {
      hasVolt: true,
      voltStr,
      text: '低电警戒 (建议启动)',
      desc: '蓄电池电量偏低，建议尽快发动车辆充能',
      badgeClass: 'bg-car-item border border-car-border text-car-text',
      dotClass: 'bg-amber-400 shadow-[0_0_6px_#F59E0B]'
    };
  } else {
    return {
      hasVolt: true,
      voltStr,
      text: '严重亏电 (面临无法点火)',
      desc: '电瓶严重亏电！请立即关闭高功耗电器，准备启动充能',
      badgeClass: 'bg-car-item border border-car-border text-car-text',
      dotClass: 'bg-rose-500 animate-pulse shadow-[0_0_6px_#EF4444]'
    };
  }
});

function fetchVoltSilently() {
  try {
    const raw = bridge.call('getDeviceInfo');
    if (raw) {
      const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
      Object.assign(store.deviceInfo, parsed);
      if (parsed.real_battery_volt && parsed.real_battery_volt >= 9.0 && parsed.real_battery_volt <= 16.5) {
        store.batteryVoltage = parsed.real_battery_volt;
      }
    }
  } catch (e) {}
}

function refreshVolt() {
  fetchVoltSilently();
  fetchHealth();
  showToast('已刷新蓄电池电压检测');
}

onMounted(() => {
  fetchVoltSilently();
  fetchHealth();
  voltTimer = setInterval(fetchVoltSilently, 2000); // 打开弹窗期间每 2 秒自适应实时刷新
});

onUnmounted(() => {
  if (voltTimer) {
    clearInterval(voltTimer);
    voltTimer = null;
  }
});
</script>
