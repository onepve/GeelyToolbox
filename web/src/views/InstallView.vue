<template>
  <div class="flex flex-col space-y-5">
    <!-- 1. 原生文件管理特权安装通道 (核心通道) -->
    <div class="grid grid-cols-2 gap-5">
      <FeatureCard class="!mb-0" 
        title="车载原生文件管理 (特权安装正解通道)"
        desc="严禁直接通过 ADB 命令行 pm install 强行静默安装，底层安全策略会导致应用无法运行或白屏！"
        helpTitle="【功能指南】车载原生文件管理与特权安装规范"
        helpText="1. 为什么严禁 ADB pm install：&#10;吉利车机系统底层有签名校验安全策略，直接通过 ADB 命令行静默安装会导致第三方软件白屏或签名崩溃闪退。&#10;&#10;2. 特权正解通道：&#10;将安装包放入车机 Download 目录后，通过点击本卡片按钮调起车载原生文件管理，在系统级特权应用内点击安装，系统将自动放行安装并正常运行。"
        helpTip="下载或传到车机 Download 目录的 APK，统一通过原生文件管理点击安装。"
      >
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-wrap items-center justify-between space-x-3">
          <div class="flex-1 min-w-0 pr-6 flex flex-col">
            <div class="text-[18px] font-black text-car-text mb-2">
              标准无损安装路线：
            </div>
            <div class="text-[15.5px] text-car-sub font-bold leading-relaxed mb-3">
              手机通过无线快传或甲壳虫将 APK 推送至车机 <code class="px-2 py-0.5 rounded bg-car-card text-car-accent font-mono">/sdcard/Download/</code> 目录，点击右侧按钮进入原生文件管理器，直接点击 APK 即可调用系统原生特权打包器无损直装。
            </div>
            <div class="flex items-center text-[13.5px] text-emerald-400 font-extrabold">
              <StatusDot class="mr-2" size="sm" color="ok" :glow-px="8" />
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

      <FeatureCard class="!mb-0 h-full"
        title="手机局域网无线快传 (免插线极速推包)"
        desc="无需繁琐插拔 U 盘或携带电脑，通过车机内置 HTTP 文件快传服务直接秒传安装包。"
        helpTitle="【功能指南】手机无线局域网极速推包"
        helpText="1. 免插拔 U 盘原理：&#10;车机内置轻量级 HTTP 局域网传输服务（端口 8888）。手机连接车机 Wi-Fi 热点或同局域网后，扫码即可秒开上传页面。&#10;&#10;2. 自动落盘路径：&#10;手机上传的 APK 会自动保存到车机 /sdcard/Download/ 目录中，随后点击上方 Card 1 原生文件管理即可直接点击安装。"
        helpTip="建议手机连接车机发出的 Wi-Fi 热点，传输速度最快、延迟最低。"
      >
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-wrap items-center justify-between space-x-3">
          <div class="flex-1 min-w-0 pr-6 flex flex-col">
            <div class="text-[15.5px] text-car-sub font-bold leading-relaxed mb-2">
              只要手机与车机处于同一 Wi-Fi 或车机热点下，扫码即可秒开网页，将手机下载的高德地图、音乐等 APK 秒传至车机 Download 目录。
            </div>
            <div class="flex items-center text-[14px] text-car-sub font-mono font-bold">
              <span class="whitespace-nowrap min-w-0 truncate">当前局域网 IP: {{ store.deviceInfo.car_ip ? store.deviceInfo.car_ip + ':8888' : '未连接热点' }}</span>
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
    </div>

    <!-- 3. 专家模式卡兔子主题安装通道 (紧凑型小卡片) -->
    <div class="bg-car-card border border-car-border rounded-2xl p-4 flex items-center justify-between shadow-sm">
      <div class="flex items-center space-x-3.5 min-w-0 pr-4">
        <StatusDot size="md" :color="store.settings.expert_rabbit ? 'accent' : 'ok'" />
        <div class="flex flex-col min-w-0">
          <div class="flex items-center space-x-2">
            <span class="text-[17px] font-black text-car-text">卡兔子主题通道 (专家模式)</span>
            <span class="px-2.5 py-0.5 text-[12px] font-bold rounded-full border bg-car-item border-car-border text-car-text">
              {{ store.settings.expert_rabbit ? '已激活' : '已锁定' }}
            </span>
          </div>
          <div class="text-[13.5px] text-car-sub font-bold truncate mt-0.5">
            {{ store.settings.expert_rabbit ? '已解锁：支持通过桌面时钟屏保注入第三方高德或音乐' : '解除地图安装限制，用于突破第三方应用签名白名单' }}
          </div>
        </div>
      </div>

      <div class="flex items-center space-x-3 shrink-0">
        <button 
          v-if="store.settings.expert_rabbit"
          @click="openRabbitGuideModal"
          class="h-[52px] px-5 rounded-xl bg-car-item border-2 border-car-accent text-car-accent font-black text-[15.5px] cursor-pointer hover:bg-car-card transition-all shadow-sm"
        >
          打开卡主题向导
        </button>
        <button 
          @click="confirmUnlockExpert"
          :class="[
            'h-[52px] px-5 rounded-xl border-2 font-black text-[15.5px] cursor-pointer transition-all shadow-sm',
            store.settings.expert_rabbit 
              ? 'bg-car-item border-car-border text-car-sub hover:text-car-text' 
              : 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/20'
          ]"
        >
          {{ store.settings.expert_rabbit ? '锁定安全防护' : '解锁专家模式' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import FeatureCard from '../components/FeatureCard.vue';
import StatusDot from '../components/StatusDot.vue';
import { store, bridge, openModal, showToast } from '../store';

function confirmUnlockExpert() {
  if (store.settings.expert_rabbit) {
    store.settings.expert_rabbit = false;
    bridge.call('setSetting', 'expert_rabbit', false);
    showToast('已恢复安全锁定防护');
    return;
  }

  // 提前在后台触发原车主题提取与 R2 静默缓存（在 20s 倒计时期间完成准备）
  try {
    bridge.call('prepareThemeAssets');
  } catch (e) {}

  // 第 1 次确认：高危警告 (10s 倒计时防盲点)
  openModal('confirm', {
    title: '【高危警告】解锁专家模式 (第 1/3 次确认)',
    desc: '【专家模式】解除系统原生安全边界，开放直接向车机底层屏保主题注入任意第三方应用的高级权限。非玩车专业人员误操作可能导致屏保黑屏或组件冲突。是否确认继续？',
    tip: '【安全建议】普通车友使用自带白名单与精选安装即可满足全部日常需求。',
    isDanger: true,
    countdown: 10,
    confirmText: '我已知晓风险，下一步',
    onConfirm: () => {
      // 第 2 次确认：技术原理与行车安全 (5s 倒计时)
      openModal('confirm', {
        title: '【安全确认】卡兔子主题屏保注入规范 (第 2/3 次确认)',
        desc: '卡兔子主题注入通过重写原厂兔子时钟屏保包名（com.ecarx.screensaver）实现无损系统级提权。在执行主题注入或整车重启前，请务必保证车辆安全停稳并挂入 P 挡。严禁在行驶途中操作！',
        tip: '【操作铁律】严禁在行车行驶过程中执行注入或冷重启！',
        isDanger: true,
        countdown: 5,
        confirmText: '确认环境安全，下一步',
        onConfirm: () => {
          // 第 3 次确认：最终特权授权 (5s 倒计时)
          openModal('confirm', {
            title: '【最终授权】正式激活专家模式 (第 3/3 次确认)',
            desc: '确认正式激活专家模式？激活后，下方将立即解锁【半自动保姆式卡兔子主题向导】与【调起原生文件管理】两大高阶工具。',
            tip: '【提示】后续可随时在此处一键恢复安全锁定。',
            isDanger: true,
            countdown: 5,
            confirmText: '确认最终激活',
            onConfirm: () => {
              store.settings.expert_rabbit = true;
              bridge.call('setSetting', 'expert_rabbit', true);
              showToast('专家模式已成功激活！高级注入工具箱已解锁', 'success');
            }
          });
        }
      });
    }
  });
}

function openAutoPilotDirectly() {
  openModal('confirm', {
    title: '全自动免电脑卡屏保注入',
    desc: '系统将自动扫描车机 Download 目录中的高德地图安装包，深度伪装重写进兔子时钟屏保，并自动拉起主题中心引导应用。注入完成后请执行整车冷重启。',
    tip: '请确保车辆已安全停稳且电瓶电量充足。',
    isDanger: false,
    confirmText: '开始全自动注入',
    onConfirm: () => {
      showToast('正在启动免电脑卡屏保全自动注入...');
      bridge.call('startAutoPilotInject', 'AutoMap_9.5.13_FullFeatures_TrafficLight.apk');
    }
  });
}

function openRabbitGuideModal() {
  store.modals.rabbitInstall = {
    name: '高德地图车机版 (默认推荐)',
    filename: 'AutoMap_9.5.13_FullFeatures_TrafficLight.apk'
  };
}

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
