<template>
  <div class="flex flex-col gap-5">
    <!-- 原生文件管理特权安装 -->
    <FeatureCard 
      title="1. 车载特权安装通道 (防白屏唯二正解)"
      desc="严禁直接 ADB 命令行 pm install 安装！请通过甲壳虫推送到车机 /sdcard/Download/ 目录后，点击下方大按钮打开车机自带原生文件管理特权安装。"
    >
      <button 
        @click="openFileManager"
        class="w-full min-h-[80px] bg-car-item border-2 border-car-accent rounded-2xl p-4 flex items-center justify-center gap-4 text-car-text font-black text-[22px] cursor-pointer hover:border-car-accent shadow-lg ring-2 ring-car-accent/20"
      >
        <span>打开车机自带原生文件管理</span>
        <span class="bg-car-card border border-car-accent text-car-accent text-[14px] px-3 py-1 rounded-md font-extrabold">进入 Download 目录直接安装</span>
      </button>
    </FeatureCard>

    <div class="grid grid-cols-2 gap-4">
      <!-- 手机无线快传 (8888 端口) -->
      <FeatureCard 
        title="2. 手机无线快传 (8888 端口)"
        desc="控制台中枢常驻局域网 HTTP 传输服务，手机无需插线，扫码秒开极速推包。"
      >
        <button 
          @click="showQrCode"
          class="w-full min-h-[72px] bg-car-item border border-car-border text-car-text font-black text-[20px] rounded-xl cursor-pointer hover:border-car-border-light"
        >
          展示无线快传二维码
        </button>
      </FeatureCard>

      <!-- 今日动态工程暗码 -->
      <FeatureCard 
        title="3. 今日动态工程暗码 (+10 / +5)"
        desc="进入工程模式开启 WiFi ADB，屏幕左上角直读 IP 填入甲壳虫秒配对。"
      >
        <button 
          @click="openDialer"
          class="w-full min-h-[72px] bg-car-item border border-car-border text-car-text font-black text-[20px] rounded-xl cursor-pointer hover:border-car-border-light"
        >
          调起车机电话拨号盘
        </button>
      </FeatureCard>
    </div>
  </div>
</template>

<script setup>
import FeatureCard from '../components/FeatureCard.vue';
import { bridge, showToast } from '../store';

function openFileManager() {
  bridge.call('openFileManager');
  showToast('正在打开车机原生文件管理...');
}

function showQrCode() {
  bridge.call('getQrCodeUrl');
  showToast('正在展示快传二维码...');
}

function openDialer() {
  bridge.call('openDialer');
  showToast('正在调起车机拨号盘...');
}
</script>
