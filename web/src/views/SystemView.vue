<template>
  <div class="flex flex-col space-y-6">
    <!-- 仅在应用商店未冻结时展示的警示横幅 (已冻结时自动隐藏，保持界面清爽) -->
    <div 
      v-if="!store.deviceInfo.appstore_frozen" 
      class="bg-car-item border-2 border-amber-500/80 rounded-2xl p-5 flex items-center justify-between shadow-lg ring-1 ring-amber-500/30 relative overflow-hidden"
    >
      <div class="flex items-center space-x-4">
        <div class="w-12 h-12 rounded-xl bg-amber-500/20 border border-amber-500/60 flex items-center justify-center text-[24px] shrink-0">
          ⚠️
        </div>
        <div class="flex flex-col space-y-1">
          <div class="flex items-center space-x-2.5">
            <span class="text-[19px] font-black text-car-text tracking-wide">检测到吉利应用商店处于未冻结状态</span>
            <span class="px-2.5 py-0.5 rounded-full bg-amber-500/20 border border-amber-500 text-amber-400 text-[12.5px] font-black">建议处置</span>
          </div>
          <span class="text-[15px] text-car-text/90 font-bold leading-normal">原厂商店后台可能覆盖白名单策略并静默卸载高德地图等第三方软件，强烈建议立即冻结！</span>
        </div>
      </div>
      <button 
        @click="confirmFreezeStore"
        class="min-h-[58px] px-8 bg-amber-500/20 border-2 border-amber-500 text-car-text font-black text-[18px] rounded-xl cursor-pointer hover:bg-amber-500/30 hover:border-amber-400 ring-2 ring-amber-500/20 shadow-md transition-all shrink-0 ml-4"
      >
        一键安全冻结
      </button>
    </div>

    <!-- 系统底层维护 6 大核心功能矩阵 (3 列自适应车规排版) -->
    <div class="grid grid-cols-3 gap-4">
      <!-- 1. 整车硬件冷重启控制台 (紧凑饱满) -->
      <FeatureCard 
        title="1. 整车硬件冷重启"
        desc="彻底掉电重置车机 MCU 与安卓核心，白名单与底层服务立即生效。"
      >
        <button 
          @click="confirmHardReboot"
          class="w-full min-h-[72px] rounded-2xl border-2 border-rose-500/50 bg-car-item text-rose-400 hover:text-rose-300 font-black text-[18px] cursor-pointer hover:border-rose-400 transition-all shadow-sm flex items-center justify-center whitespace-nowrap"
        >
          <span>完整硬件冷重启 (reboot)</span>
        </button>
      </FeatureCard>

      <!-- 2. 车机深度工具箱 & ADB 控制台 (从顶栏移至此处) -->
      <FeatureCard 
        title="2. 车机深度工具箱 & ADB"
        desc="内置本地 ADB Client 2000 端口，提供命令行交互与固件抓取。"
      >
        <button 
          @click="openDeepTools"
          class="w-full min-h-[72px] rounded-2xl border-2 border-car-accent bg-car-item text-car-text font-black text-[18px] cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 transition-all shadow-md flex items-center justify-center whitespace-nowrap"
        >
          <span>打开 ADB 交互控制台</span>
        </button>
      </FeatureCard>

      <!-- 3. 安装白名单属性放行 -->
      <FeatureCard 
        title="3. 第三方 APK 放行白名单"
        desc="注入 apk_verify=1 属性，解除系统级安装包校验限制。"
      >
        <button 
          @click="confirmToggleWhitelist"
          :class="[
            'w-full min-h-[72px] rounded-2xl border-2 font-black text-[18px] cursor-pointer transition-all shadow-sm flex items-center justify-center whitespace-nowrap',
            store.deviceInfo.whitelist 
              ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' 
              : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
          ]"
        >
          <span>{{ store.deviceInfo.whitelist ? '白名单: 已放行' : '白名单: 未放行 (点击开启)' }}</span>
        </button>
      </FeatureCard>

      <!-- 4. 运行与安全审计日志 -->
      <FeatureCard 
        title="4. 运行与守护日志"
        desc="实时记录开门、挡位与方控触发记录，支持清空与导出。"
      >
        <button 
          @click="openLogModal"
          class="w-full min-h-[72px] rounded-2xl border-2 border-car-border bg-car-item text-car-text font-black text-[18px] cursor-pointer hover:border-car-border-light transition-all shadow-sm flex items-center justify-center whitespace-nowrap"
        >
          <span>查看运行与守护日志</span>
        </button>
      </FeatureCard>

      <!-- 5. 应用商店管理 (带二次校验) -->
      <FeatureCard 
        title="5. 应用商店状态管理"
        desc="防止原厂商店后台静默卸载应用。如需使用商店可在此解冻。"
      >
        <button 
          @click="confirmToggleAppstore"
          :class="[
            'w-full min-h-[72px] rounded-2xl border-2 font-black text-[18px] cursor-pointer transition-all shadow-sm flex items-center justify-center whitespace-nowrap',
            store.deviceInfo.appstore_frozen 
              ? 'bg-car-item border-emerald-500/50 text-emerald-400' 
              : 'bg-rose-500/15 border-rose-500 text-rose-500'
          ]"
        >
          <span>{{ store.deviceInfo.appstore_frozen ? '商店: 已冻结 (安全防删)' : '商店: 未冻结 (点击安全冻结)' }}</span>
        </button>
      </FeatureCard>

      <!-- 6. 车辆启动自动运行 -->
      <FeatureCard 
        title="6. 车辆启动自动运行"
        desc="车机通电开机后自动自启后台服务，门控与方控即刻生效。"
      >
        <button 
          @click="toggleAutostart"
          :class="[
            'w-full min-h-[72px] rounded-2xl border-2 font-black text-[18px] cursor-pointer transition-all shadow-sm flex items-center justify-center whitespace-nowrap',
            store.deviceInfo.autostart 
              ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' 
              : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
          ]"
        >
          <span>{{ store.deviceInfo.autostart ? '自启状态: 已开启自启' : '自启状态: 已关闭自启' }}</span>
        </button>
      </FeatureCard>
    </div>

    <!-- 底部运维与避坑指引 -->
    <div class="bg-car-item border border-car-border rounded-2xl p-5 text-[14.5px] text-car-sub font-bold leading-relaxed space-y-1.5 shadow-sm">
      <div class="text-[16px] text-car-text font-black mb-1 flex items-center">
        <span class="mr-2">💡</span> 座舱底层维护铁律与核心原理说明：
      </div>
      <div>• <b>冷重启原理</b>：彻底断电重启 MCU 与 Framework，彻底杜绝开门播报延迟与系统卡顿；</div>
      <div>• <b>白名单锁定</b>：商店冻结是高德地图防被卸载的关键，日常行车请务必保持冻结状态；</div>
      <div>• <b>ADB 安全边界</b>：深度终端已做系统核心保护，严禁自行卸载系统 Framework 核心组件。</div>
    </div>
  </div>
</template>

<script setup>
import FeatureCard from '../components/FeatureCard.vue';
import { store, bridge, openModal, showToast } from '../store';

function confirmHardReboot() {
  openModal('confirm', {
    title: '整车完整硬件冷重启 (reboot)',
    desc: '即将对整车中控硬件执行完全掉电冷启动 (reboot)，耗时约 25~35 秒。白名单、音频通道与系统框架将彻底刷新生效。',
    tip: '【安全警示】严禁在行车行驶过程中执行整车冷重启操作！请确保车辆已安全停稳。',
    isDanger: true,
    confirmText: '确认立即掉电重启',
    onConfirm: () => {
      bridge.call('hardReboot');
      showToast('已下发整车冷重启指令，中控即将断电重启...');
    }
  });
}

function openDeepTools() {
  openModal('deepTools');
}

function openLogModal() {
  store.modals.log = true;
}

function confirmFreezeStore() {
  openModal('confirm', {
    title: '冻结吉利应用商店',
    desc: '冻结吉利原厂应用商店后，将永久锁定第三方软件安装白名单，彻底防止高德地图等应用被后台静默卸载。',
    tip: '提示：后续可随时在此处解冻恢复。',
    isDanger: false,
    onConfirm: () => {
      bridge.call('toggleFreezeAppStore', true);
      showToast('正在执行应用商店安全冻结...');
    }
  });
}

function confirmToggleAppstore() {
  const isFrozen = store.deviceInfo.appstore_frozen;
  if (isFrozen) {
    // 当前已冻结，解冻需要二次警告确认
    openModal('confirm', {
      title: '解冻恢复吉利应用商店',
      desc: '解冻吉利应用商店后，原厂商店可能会在后台执行扫描并静默卸载高德地图 8.5 等第三方应用。是否确认解冻？',
      tip: '【警告】解冻仅建议在需要从官方商店安装官方应用时临时使用，安装完毕建议立即重新冻结。',
      isDanger: true,
      confirmText: '确认解冻',
      onConfirm: () => {
        bridge.call('toggleFreezeAppStore', false);
        showToast('正在解冻恢复吉利应用商店...');
      }
    });
  } else {
    // 当前未冻结，引导冻结
    confirmFreezeStore();
  }
}

function confirmToggleWhitelist() {
  const next = !store.deviceInfo.whitelist;
  openModal('confirm', {
    title: next ? '开启第三方 APK 放行白名单' : '关闭第三方 APK 放行白名单',
    desc: next 
      ? '即将注入 sys.jsbd.apk_verify=1 属性，解除车机原生 PackageInstaller 的签名校验限制，允许自由安装第三方软件。'
      : '关闭白名单后，安装第三方 APK 将恢复系统原生限制并可能报解析包失败。',
    tip: '建议始终保持开启状态。',
    isDanger: !next,
    onConfirm: () => {
      bridge.call('toggleWhitelist');
      showToast(next ? '正在开启第三方白名单...' : '正在关闭第三方白名单...');
    }
  });
}

function toggleAutostart() {
  const next = !store.deviceInfo.autostart;
  store.deviceInfo.autostart = next;
  bridge.call('setAutostartEnabled', next);
  showToast(next ? '已开启车辆启动自动运行' : '已关闭车辆启动自动运行');
}
</script>
