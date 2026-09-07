<template>
  <div class="flex flex-col space-y-6">
    <!-- 车身安全重启控制台 -->
    <FeatureCard 
      title="1. 整车安全重启控制台"
      desc="完整硬件冷重启用于整车硬件与 Android 核心底层彻底重置，白名单与底层服务立即生效。"
    >
      <div class="w-full">
        <MatrixButton 
          title="完整硬件冷重启 (reboot)"
          subtitle="整机底层掉电冷启，100% 硬件重载生效"
          class="border-rose-500/40 text-rose-400"
          @click="hardReboot"
        />
      </div>
    </FeatureCard>

    <div class="grid grid-cols-3 gap-4">
      <!-- 商店核心冻结 -->
      <FeatureCard 
        title="2. 商店核心冻结"
        desc="停用原厂商店防止覆盖白名单"
      >
        <MatrixButton 
          :title="store.deviceInfo.appstore_frozen ? '商店: 已冻结 (安全)' : '商店: 未冻结 (警告)'"
          :active="store.deviceInfo.appstore_frozen"
          @click="toggleAppstoreFreeze"
        />
      </FeatureCard>

      <!-- 安装白名单属性 -->
      <FeatureCard 
        title="3. 安装白名单属性"
        desc="注入 sys.jsbd.apk_verify=1"
      >
        <MatrixButton 
          :title="store.deviceInfo.whitelist ? '白名单: 已放行 1' : '白名单: 未放行 0'"
          :active="store.deviceInfo.whitelist"
          @click="toggleWhitelist"
        />
      </FeatureCard>

      <!-- 中枢运行日志 -->
      <FeatureCard 
        title="4. 中枢运行日志"
        desc="存储于 Download 目录自动轮转"
      >
        <button 
          @click="openLogModal"
          class="w-full min-h-[84px] bg-car-item border border-car-border text-car-text font-black text-[19px] rounded-2xl cursor-pointer hover:border-car-border-light shadow-sm"
        >
          查看运行日志
        </button>
      </FeatureCard>
    </div>
  </div>
</template>

<script setup>
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';
import { store, bridge, openModal, showToast } from '../store';

function hardReboot() {
  openModal('confirm', {
    title: '整车完整硬件冷重启 (reboot)',
    desc: '即将对整车中控硬件执行完全掉电冷启动 (reboot)，耗时约 25~35 秒。白名单与系统框架将彻底生效。',
    tip: '高危提示：正在行车时请勿执行整车冷重启！',
    isDanger: true,
    confirmText: '确认立即重启',
    onConfirm: () => {
      bridge.call('hardReboot');
      showToast('已下发整车冷重启指令，中控即将断电重启...');
    }
  });
}

function toggleAppstoreFreeze() {
  bridge.call('toggleAppstoreFreeze');
  showToast('正在切换商店冻结状态...');
}

function toggleWhitelist() {
  bridge.call('toggleWhitelist');
  showToast('正在切换白名单状态...');
}

function openLogModal() {
  bridge.call('getLogContent');
  showToast('正在加载运行日志...');
}
</script>
