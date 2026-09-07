<template>
  <div class="flex flex-col space-y-5">
    <!-- 1. 原生文件管理特权安装通道 (核心通道) -->
    <FeatureCard 
      title="1. 车载原生文件管理 (特权安装正解通道)"
      desc="严禁直接通过 ADB 命令行 pm install 强行静默安装，底层安全策略会导致应用无法运行或白屏！"
    >
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between">
        <div class="flex-1 pr-6 flex flex-col">
          <div class="text-[18px] font-black text-car-text mb-2">
            标准无损安装路线：
          </div>
          <div class="text-[15.5px] text-car-sub font-bold leading-relaxed mb-3">
            手机通过无线快传或甲壳虫将 APK 推送至车机 <code class="px-2 py-0.5 rounded bg-car-card text-car-accent font-mono">/sdcard/Download/</code> 目录，点击右侧按钮进入原生文件管理器，直接点击 APK 即可调用系统原生特权打包器无损直装。
          </div>
          <div class="flex items-center text-[13.5px] text-emerald-400 font-extrabold">
            <span class="w-2.5 h-2.5 rounded-full bg-emerald-500 mr-2 shadow-[0_0_8px_#10B981]"></span>
            <span>已接入 Android 9 原生 PackageInstaller 特权证书白名单</span>
          </div>
        </div>

        <button 
          @click="openFileManager"
          class="min-w-[240px] min-h-[76px] px-6 bg-car-card border-2 border-car-accent rounded-2xl flex flex-col items-center justify-center text-car-text font-black cursor-pointer hover:border-car-accent ring-2 ring-car-accent/25 shadow-lg shrink-0 transition-all"
        >
          <span class="text-[20px]">打开原生文件管理</span>
          <span class="text-[13px] text-car-accent mt-0.5 font-extrabold">进入 Download 目录</span>
        </button>
      </div>
    </FeatureCard>

    <!-- 2. 手机无线快传 (8888 端口) -->
    <FeatureCard 
      title="2. 手机局域网无线快传 (免插线极速推包)"
      desc="无需繁琐插拔 U 盘或携带电脑，通过车机内置 HTTP 文件快传服务直接秒传安装包。"
    >
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between">
        <div class="flex-1 pr-6 flex flex-col">
          <div class="text-[15.5px] text-car-sub font-bold leading-relaxed mb-2">
            只要手机与车机处于同一 Wi-Fi 或车机热点下，扫码即可秒开网页，将手机下载的高德地图、音乐等 APK 秒传至车机 Download 目录。
          </div>
          <div class="flex items-center text-[14px] text-car-sub font-mono font-bold">
            <span class="px-2.5 py-0.5 rounded bg-car-card border border-car-border mr-3 text-car-text">HTTP 服务端口: 8888</span>
            <span>当前局域网 IP: {{ store.deviceInfo.car_ip || '未连接热点' }}</span>
          </div>
        </div>

        <button 
          @click="showQrCode"
          class="min-w-[220px] min-h-[72px] px-6 bg-car-card border-2 border-car-border text-car-text font-black text-[19px] rounded-2xl cursor-pointer hover:border-car-border-light shrink-0 shadow-sm transition-all"
        >
          打开无线快传二维码
        </button>
      </div>
    </FeatureCard>

    <!-- 3. 今日动态工程暗码 (+10 / +5) -->
    <FeatureCard 
      title="3. 今日动态工程暗码与 ADB 开启"
      desc="每日按时间对时生成的专车动态工程模式暗码，用于打开车机底层无线 ADB 调试与系统特权。"
    >
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between">
        <div class="flex-1 pr-6 flex flex-col">
          <div class="text-[15.5px] text-car-sub font-bold leading-relaxed mb-2">
            吉利车机工程密码每天根据时间动态计算。点击右侧打开车机原生电话拨号盘，手动在键盘输入今日暗码即可秒进工程菜单开启 ADB。
          </div>
          <div class="flex items-center text-[14px] font-bold">
            <span class="text-car-sub mr-2">今日标准暗码:</span>
            <span class="font-mono text-[17px] text-car-accent font-black mr-4">{{ store.dynamicCode }}</span>
            <span class="text-[13px] text-car-sub font-normal">(早期固件备用: {{ store.dynamicCodePlus5 }})</span>
          </div>
        </div>

        <button 
          @click="openDialer"
          class="min-w-[220px] min-h-[72px] px-6 bg-car-card border-2 border-car-border text-car-text font-black text-[19px] rounded-2xl cursor-pointer hover:border-car-border-light shrink-0 shadow-sm transition-all"
        >
          查看暗码 & 打开电话
        </button>
      </div>
    </FeatureCard>
  </div>
</template>

<script setup>
import FeatureCard from '../components/FeatureCard.vue';
import { store, bridge, openModal, showToast } from '../store';

function openFileManager() {
  bridge.call('openFileManager');
  showToast('正在打开车机原生文件管理...');
}

function showQrCode() {
  openModal('qrCode');
}

function openDialer() {
  openModal('dialer');
}
</script>
