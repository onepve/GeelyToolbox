<template>
  <div class="flex flex-col space-y-5">
    <!-- 1. 原生文件管理特权安装通道 (核心通道) -->
    <FeatureCard 
      title="1. 车载原生文件管理 (特权安装正解通道)"
      desc="严禁直接通过 ADB 命令行 pm install 强行静默安装，底层安全策略会导致应用无法运行或白屏！"
      helpTitle="【功能指南】车载原生文件管理与特权安装规范"
      helpText="1. 为什么严禁 ADB pm install：&#10;吉利车机系统底层有签名校验安全策略，直接通过 ADB 命令行静默安装会导致第三方软件白屏或签名崩溃闪退。&#10;&#10;2. 特权正解通道：&#10;将安装包放入车机 Download 目录后，通过点击本卡片按钮调起车载原生文件管理，在系统级特权应用内点击安装，系统将自动放行安装并正常运行。"
      helpTip="下载或传到车机 Download 目录的 APK，统一通过原生文件管理点击安装。"
    >
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between">
        <div class="flex-1 min-w-0 pr-6 flex flex-col">
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
      helpTitle="【功能指南】手机无线局域网极速推包"
      helpText="1. 免插拔 U 盘原理：&#10;车机内置轻量级 HTTP 局域网传输服务（端口 8888）。手机连接车机 Wi-Fi 热点或同局域网后，扫码即可秒开上传页面。&#10;&#10;2. 自动落盘路径：&#10;手机上传的 APK 会自动保存到车机 /sdcard/Download/ 目录中，随后点击上方 Card 1 原生文件管理即可直接点击安装。"
      helpTip="建议手机连接车机发出的 Wi-Fi 热点，传输速度最快、延迟最低。"
    >
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between">
        <div class="flex-1 min-w-0 pr-6 flex flex-col">
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
      desc="根据车载系统时钟每秒自动计算，直出高亮暗码。车机拨号界面不支持粘贴，请点击按钮打开拨号盘手动按键输入。"
      helpTitle="【功能指南】动态工程暗码算法与拨号盘操作"
      helpText="1. 动态暗码生成机制：&#10;吉利车机工程密码每天根据系统时间动态计算对时。主流新版固件采用「月份+5」算法，早期老固件采用「月份+10」算法。&#10;&#10;2. 拨号盘输入规范：&#10;吉利车机拨号界面原生不支持剪贴板粘贴。请点击右侧「打开拨号盘」按钮调起系统电话，在拨号键盘上照着上方高亮的暗码依次手动输入，即可秒进工程模式。"
      helpTip="进工程模式后，找到「系统信息」或「开发者选项」即可开启无线 ADB。"
    >
      <div class="grid grid-cols-2 gap-4">
        <!-- 主流+5暗码 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between">
          <div>
            <div class="text-[15px] font-bold text-car-sub">主流新固件 (月份+5 算法)</div>
            <div class="text-[28px] font-black text-car-accent mt-1 tracking-wider">
              {{ store.dynamicCodePlus5 }}
            </div>
            <div class="text-[13px] text-car-sub mt-1">适用于绝大多数 2022~2024 款吉利车机</div>
          </div>
          <button 
            @click="openDialer"
            class="h-[58px] px-6 rounded-2xl bg-car-card border-2 border-car-accent text-car-accent hover:bg-car-item font-black text-[17px] cursor-pointer shadow-md transition-all whitespace-nowrap"
          >
            📞 打开拨号盘
          </button>
        </div>

        <!-- 老固件+10暗码 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between">
          <div>
            <div class="text-[15px] font-bold text-car-sub">早期老版固件 (月份+10 算法)</div>
            <div class="text-[28px] font-black text-car-text mt-1 tracking-wider">
              {{ store.dynamicCode }}
            </div>
            <div class="text-[13px] text-car-sub mt-1">若加 5 暗码提示错误，请尝试此备选暗码</div>
          </div>
          <button 
            @click="openDialer"
            class="h-[58px] px-6 rounded-2xl bg-car-card border-2 border-car-border text-car-text hover:border-car-border-light font-black text-[17px] cursor-pointer shadow-md transition-all whitespace-nowrap"
          >
            📞 打开拨号盘
          </button>
        </div>
      </div>
    </FeatureCard>

    <!-- 4. 专家模式与卡主题伪装注入 -->
    <FeatureCard 
      title="4. 专家级伪装与卡主题注入 (专家模式)"
      desc="解除主题防护白名单限制，支持直接将任意第三方应用伪装注入为桌面兔子时钟屏保卡片。"
      helpTitle="【功能指南】卡主题伪装注入与安全规范"
      helpText="1. 核心技术原理：&#10;通过重写吉利原厂桌面兔子时钟屏保卡片（com.ecarx.screensaver）实现无损系统级提权，突破第三方地图签名白名单限制。&#10;&#10;2. 高德免卡兔子升级：&#10;当前高德 7.5 / 8.5 / 9.5 全线已统一使用原厂 AOSP 平台密钥重签，开启白名单后已可直接覆盖安装，无需再卡兔子主题！本专家功能仅留给高阶玩家极客改装使用。"
      helpTip="高德全线已支持免卡兔子直装，普通车友无需开启专家模式。"
    >
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between">
        <div class="flex-1 min-w-0 pr-6 flex flex-col">
          <div class="flex items-center space-x-3 mb-1">
            <span class="text-[18px] font-black text-car-text">伪装屏保注入特权</span>
            <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-item border-car-border text-car-text inline-flex items-center shadow-sm">
              <span :class="['w-2.5 h-2.5 rounded-full mr-2', store.settings.expert_rabbit ? 'bg-car-accent shadow-[0_0_6px_var(--accent-gold)]' : 'bg-emerald-500 shadow-[0_0_6px_#10B981]']"></span>
              {{ store.settings.expert_rabbit ? '专家模式已激活 (已解除限制)' : '安全保护已锁定' }}
            </span>
          </div>
          <div class="text-[15px] text-car-sub font-bold">
            {{ store.settings.expert_rabbit ? '已解除地图白名单限制，精选软件中心与本地应用详情均可直接调起卡主题注入向导' : '默认仅限车载地图导航使用，点击右侧可解锁专家模式体验完整高级功能' }}
          </div>
        </div>

        <button 
          @click="confirmUnlockExpert"
          :class="[
            'min-w-[220px] min-h-[72px] px-6 rounded-2xl border-2 font-black text-[19px] cursor-pointer transition-all shrink-0 shadow-sm',
            store.settings.expert_rabbit 
              ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' 
              : 'bg-car-card border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'
          ]"
        >
          {{ store.settings.expert_rabbit ? '锁定安全保护' : '解锁专家模式' }}
        </button>
      </div>

      <!-- 专家模式专属工具栏 (激活后平铺直出) -->
      <div v-if="store.settings.expert_rabbit" class="mt-4 pt-4 border-t border-car-border/60 flex flex-col space-y-3">
        <div class="flex items-center space-x-2">
          <span class="text-[17px] font-black text-car-accent">⚡ 专家模式专属卡主题注入工具：</span>
          <span class="text-[13.5px] text-car-sub font-bold">突破系统签名保护，半自动保姆式将高德或音乐伪装注入为桌面时钟屏保</span>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <!-- 1. 半自动保姆式卡主题安装向导 -->
          <button 
            @click="openRabbitGuideModal"
            class="min-h-[84px] p-4 rounded-2xl bg-car-item border-2 border-car-accent flex flex-col items-center justify-center text-center cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 shadow-md transition-all"
          >
            <span class="text-[19px] font-black text-car-text">🛠️ 半自动保姆式卡主题向导</span>
            <span class="text-[13.5px] font-bold text-car-sub mt-1">分步向导弹窗引导注入与重启覆盖</span>
          </button>

          <!-- 2. 原生文件管理 -->
          <button 
            @click="openFileManager"
            class="min-h-[84px] p-4 rounded-2xl bg-car-item border-2 border-car-border flex flex-col items-center justify-center text-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
          >
            <span class="text-[19px] font-black text-car-text">📁 调起原生文件管理</span>
            <span class="text-[13.5px] font-bold text-car-sub mt-1">直接浏览车机 Download 目录与安装包</span>
          </button>
        </div>
      </div>
    </FeatureCard>
  </div>
</template>

<script setup>
import FeatureCard from '../components/FeatureCard.vue';
import { store, bridge, openModal, showToast } from '../store';

function confirmUnlockExpert() {
  if (store.settings.expert_rabbit) {
    store.settings.expert_rabbit = false;
    bridge.call('setSetting', 'expert_rabbit', false);
    showToast('已恢复安全锁定防护');
    return;
  }

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
        title: '【安全确认】卡主题屏保注入规范 (第 2/3 次确认)',
        desc: '卡主题注入通过重写原厂兔子时钟屏保包名（com.ecarx.screensaver）实现无损系统级提权。在执行主题注入或整车重启前，请务必保证车辆安全停稳并挂入 P 挡。严禁在行驶途中操作！',
        tip: '【操作铁律】严禁在行车行驶过程中执行注入或冷重启！',
        isDanger: true,
        countdown: 5,
        confirmText: '确认环境安全，下一步',
        onConfirm: () => {
          // 第 3 次确认：最终特权授权 (5s 倒计时)
          openModal('confirm', {
            title: '【最终授权】正式激活专家模式 (第 3/3 次确认)',
            desc: '确认正式激活专家模式？激活后，下方将立即解锁【🛠️ 半自动保姆式卡主题向导】与【📁 调起原生文件管理】两大高阶工具。',
            tip: '【提示】后续可随时在此处一键恢复安全锁定。',
            isDanger: true,
            countdown: 5,
            confirmText: '确认最终激活',
            onConfirm: () => {
              store.settings.expert_rabbit = true;
              bridge.call('setSetting', 'expert_rabbit', true);
              showToast('⚡ 专家模式已成功激活！高级注入工具箱已解锁');
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
