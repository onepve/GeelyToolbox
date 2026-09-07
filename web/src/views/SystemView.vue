<template>
  <div class="flex flex-col gap-5">
    <!-- 车身安全重启控制台 -->
    <FeatureCard 
      title="1. 车身安全重启控制台"
      desc="软重启用于快速重载车机系统服务（5秒生效）；完整硬重启用于整车硬件冷启动重置底层。"
    >
      <div class="grid grid-cols-2 gap-4">
        <MatrixButton 
          title="一键软重启 (5秒)"
          subtitle="重启 Zygote 服务层，白名单立即锁死生效"
          class="border-emerald-500/40 text-emerald-400"
          @click="softReboot"
        />
        <MatrixButton 
          title="完整硬件冷重启 (reboot)"
          subtitle="整机底层掉电冷启，100% 硬件重载"
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
          class="w-full min-h-[106px] bg-car-item border border-car-border text-car-text font-black text-[20px] rounded-2xl cursor-pointer hover:border-car-border-light"
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

function softReboot() {
  openModal('confirm', {
    title: '一键软重启车机 (5秒)',
    desc: '即将重载 Android 系统服务与白名单进程，不重启整机 Linux 内核与整车电源，耗时约 5~8 秒。',
    tip: '适用场景：卡兔子时钟伪装后激活白名单、消除临时系统界面卡顿。',
    isDanger: false,
    onConfirm: () => {
      bridge.call('softReboot');
      showToast('已下发软重启指令，请稍候...');
    }
  });
}

function hardReboot() {
  openModal('confirm', {
    title: '整车完整硬件冷重启 (reboot)',
    desc: '即将对整车中控硬件执行完全掉电冷启动 (reboot)，耗时约 25~35 秒。',
    tip: '高危提示：正在行车时请勿执行整车冷重启！',
    isDanger: true,
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
