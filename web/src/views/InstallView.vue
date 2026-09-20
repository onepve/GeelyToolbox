<template>
  <div class="flex flex-col space-y-5">
    <!-- 0. 前置环境安装管控（一左一右双列对称网格：吉利应用商店状态与第三方 APK 放行白名单） -->
    <div class="grid grid-cols-2 gap-5">
      <!-- 左侧：吉利应用商店安全冻结防护 -->
      <FeatureCard 
        class="!mb-0"
        title="吉利应用商店防护"
        :desc="store.deviceInfo.appstore_frozen ? '原厂商店已安全锁定，已彻底阻断第三方安装环境破坏。' : '检测到原厂商店未冻结，会破坏白名单导致软件无法安装！'"
        helpTitle="【功能指南】吉利应用商店防护"
        helpText="1. 为什么要冻结原厂商店：&#10;原厂商店会在后台偷偷重置系统限制，导致刚装好的第三方软件被清空或打不开。&#10;&#10;2. 冻结后的影响：&#10;仅停用原厂自带商城，原车自带的地图、倒车影像、车辆设置等所有功能完全不受影响；需要用原厂商城时可随时一键解冻。&#10;&#10;3. 推荐操作：&#10;平时建议一直保持冻结锁定，安装和使用第三方软件更稳定。" helpTip="建议保持冻结，使用第三方软件更省心。"
      >
        <button
          @click="openAppstoreFlow"
          :class="[
            'w-full min-h-[68px] rounded-2xl border-2 font-black text-[18px] cursor-pointer transition-all shadow-sm flex flex-col items-center justify-center text-center',
            store.deviceInfo.appstore_frozen
              ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20'
              : 'bg-car-item border-amber-500/80 hover:border-amber-400 ring-2 ring-amber-500/20 text-car-text'
          ]"
        >
          <div class="flex items-center space-x-2">
            <StatusDot size="sm" :color="store.deviceInfo.appstore_frozen ? 'ok' : 'warn'" />
            <span class="text-[18px]">{{ store.deviceInfo.appstore_frozen ? '商店状态: 已安全冻结' : '商店状态: 未冻结 (建议处置)' }}</span>
          </div>
          <span class="text-[13px] font-bold mt-1 text-car-sub">
            {{ store.deviceInfo.appstore_frozen ? '已阻断策略重置 · 点击可解冻管理' : '点击立即一键安全冻结商店' }}
          </span>
        </button>
      </FeatureCard>

      <!-- 右侧：第三方 APK 放行白名单 -->
      <FeatureCard 
        class="!mb-0"
        title="第三方 APK 放行白名单"
        desc="注入 sys.jsbd.apk_verify=1 属性，解除系统级安装包签名校验限制。"
        helpTitle="【功能指南】第三方 APK 放行白名单"
        helpText="1. 解除安装限制：&#10;解除车机系统对安装包的签名限制，允许自由安装高德地图、网易云等第三方应用。&#10;&#10;2. 开关说明：&#10;开启后即可随心安装第三方应用；关闭则恢复车机原厂限制。&#10;&#10;3. 搭配建议：&#10;建议始终保持开启，并配合上方「原厂商店冻结」一起使用。" helpTip="安装高德地图、音乐等软件必须保持开启。"
      >
        <button 
          @click="confirmToggleWhitelist"
          :class="[
            'w-full min-h-[68px] rounded-2xl border-2 font-black text-[18px] cursor-pointer transition-all shadow-sm flex flex-col items-center justify-center text-center',
            store.deviceInfo.whitelist 
              ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' 
              : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
          ]"
        >
          <div class="flex items-center space-x-2">
            <StatusDot size="sm" :color="store.deviceInfo.whitelist ? 'ok' : 'warn'" />
            <span class="text-[18px]">{{ store.deviceInfo.whitelist ? '白名单: 已放行 (环境就绪)' : '白名单: 未放行 (已限制)' }}</span>
          </div>
          <span class="text-[13px] font-bold mt-1 text-car-sub">
            {{ store.deviceInfo.whitelist ? '签名校验已解除 · 点击可关闭' : '点击立即放行第三方签名' }}
          </span>
        </button>
      </FeatureCard>
    </div>

    <!-- 1. 原生文件管理特权安装通道与无线快传 (核心通道 2 列对称) -->
    <div class="grid grid-cols-2 gap-5">
      <FeatureCard class="!mb-0" 
        title="车载原生文件管理 (特权安装正解通道)"
        desc="严禁直接通过 ADB 命令行 pm install 强行静默安装，底层安全策略会导致应用无法运行或白屏！"
        helpTitle="【功能指南】车载原生文件管理与特权安装规范"
        helpText="1. 正规安装通道：&#10;把软件安装包放进车机 Download 文件夹后，点这里的按钮打开车机自带的原生文件管理，在里面点安装，系统就会正常放行。&#10;&#10;2. 为什么不用命令行强装：&#10;用电脑命令行强行静默安装容易导致软件闪退或白屏，通过原生文件管理点击安装才是最稳定正确的姿势。" helpTip="传到车机的安装包，统一在原生文件管理里点击安装。"
      >
        <div class="bg-car-item border border-car-border rounded-2xl p-3.5 flex items-center justify-between space-x-4">
          <div class="flex-1 min-w-0 flex flex-col space-y-1">
            <div class="text-[15px] text-car-sub font-bold leading-normal">
              推送 APK 至 <code class="px-1.5 py-0.5 rounded bg-car-card text-car-accent font-mono text-[14px]">/sdcard/Download/</code> 目录，点击进入原生管理器即可直接特权无损直装。
            </div>
            <div class="flex items-center text-[13px] text-emerald-400 font-extrabold">
              <StatusDot class="mr-1.5" size="sm" color="ok" :glow-px="6" />
              <span>已接入原生 PackageInstaller 特权白名单</span>
            </div>
          </div>

          <button 
            @click="openFileManager"
            class="min-w-[210px] h-[52px] px-5 bg-car-card border-2 border-car-accent rounded-xl flex items-center justify-center space-x-2 text-car-text font-black cursor-pointer hover:border-car-accent ring-2 ring-car-accent/25 shadow-md shrink-0 transition-all"
          >
            <span class="text-[17px]">打开文件管理</span>
            <span class="text-[12px] text-car-accent font-extrabold bg-car-item px-2 py-0.5 rounded-md border border-car-accent/40">Download</span>
          </button>
        </div>
      </FeatureCard>

      <FeatureCard class="!mb-0 h-full"
        title="手机局域网无线快传 (免插线极速推包)"
        desc="无需繁琐插拔 U 盘或携带电脑，通过车机内置 HTTP 文件快传服务直接秒传安装包。"
        helpTitle="【功能指南】手机无线局域网极速推包"
        helpText="1. 手机免插线传包：&#10;手机连接车机发出的 Wi-Fi 热点后，扫一扫屏幕上的二维码，就能用手机浏览器把安装包秒传到车机上。&#10;&#10;2. 传完直接安装：&#10;传好的安装包会自动存在车机 Download 文件夹，接着点旁边的原生文件管理就能直接安装，免去插拔 U 盘或搬电脑的麻烦。" helpTip="手机连接车机热点后传输速度最快。"
      >
        <div class="bg-car-item border border-car-border rounded-2xl p-3.5 flex items-center justify-between space-x-4">
          <div class="flex-1 min-w-0 flex flex-col space-y-1">
            <div class="text-[15px] text-car-sub font-bold leading-normal">
              手机连接同一 Wi-Fi 或热点扫码推包，APK 自动保存到车机 Download 目录。
            </div>
            <div class="flex items-center text-[13px] text-car-sub font-mono font-bold">
              <span class="whitespace-nowrap min-w-0 truncate">局域网 IP: {{ store.deviceInfo.car_ip ? store.deviceInfo.car_ip + ':8888' : '未连接热点' }}</span>
            </div>
          </div>

          <button 
            @click="showQrCode"
            class="min-w-[190px] h-[52px] px-4 bg-car-card border-2 border-car-border text-car-text font-black text-[16px] rounded-xl cursor-pointer hover:border-car-border-light shrink-0 shadow-sm transition-all"
          >
            打开无线快传码
          </button>
        </div>
      </FeatureCard>
    </div>

    <!-- 3. 整车应用高级管理与专家通道 (2 列对称卡片) -->
    <div class="grid grid-cols-2 gap-5">
      <!-- 应用高级管理 (原系统维护移至此处，形成安装与卸载管理闭环) -->
      <FeatureCard class="!mb-0"
        title="整车应用高级管理"
        desc="内置整车应用分类管理，支持系统应用与第三方应用分类查看、冻结、卸载与清理数据。"
        helpTitle="【功能指南】整车应用高级管理"
        helpText="1. 软件分类清晰：&#10;自动区分车机自带的原厂软件和自己装的第三方软件，防止误删系统核心程序。&#10;&#10;2. 冻结释放运存：&#10;可以快速停用平时不用的预装软件，减少后台占用，让车机更流畅。&#10;&#10;3. 一键干净卸载：&#10;不需要的第三方软件可在此彻底卸载并清理残留缓存。" helpTip="安装新版应用前，可先在此卸载旧版本避免冲突。"
      >
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-wrap items-center justify-between space-x-3">
          <div class="flex-1 min-w-0 pr-6 flex flex-col">
            <div class="text-[15.5px] text-car-sub font-bold leading-relaxed mb-2">
              查看整车已装应用，支持一键卸载残留、冻结预装软件与清理运行缓存。
            </div>
            <div class="flex items-center text-[13.5px] text-emerald-400 font-extrabold">
              <StatusDot class="mr-2" size="sm" color="ok" :glow-px="8" />
              <span>支持系统/用户应用智能分类与状态管控</span>
            </div>
          </div>

          <button 
            @click="openAllApps"
            class="min-w-[200px] min-h-[72px] px-6 bg-car-card border-2 border-car-accent text-car-text font-black text-[18px] rounded-2xl cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 shrink-0 shadow-md transition-all whitespace-nowrap"
          >
            打开应用高级管理
          </button>
        </div>
      </FeatureCard>

      <!-- 专家模式卡兔子主题安装通道 -->
      <FeatureCard class="!mb-0"
        title="卡兔子主题通道 (专家模式)"
        desc="解除地图与音乐安装签名限制，用于通过原厂时钟屏保注入第三方软件。"
        helpTitle="【功能指南】卡兔子主题通道 (专家模式)"
        helpText="1. 主题替换高阶通道：&#10;用于通过原厂时钟屏保等主题方式注入第三方软件的高级功能。&#10;&#10;2. 使用建议：&#10;平时常规安装高德或音乐，优先使用上方的原生文件管理；仅在特殊玩法时按需使用。" helpTip="普通车主安装软件使用上方常规通道即可。"
      >
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-wrap items-center justify-between space-x-3">
          <div class="flex-1 min-w-0 pr-4 flex flex-col">
            <div class="bg-car-item border-car-border text-car-text inline-flex items-center space-x-2 mb-1 px-3 py-1 rounded-xl border border-car-border">
              <StatusDot size="md" :color="store.settings.expert_rabbit ? 'accent' : 'ok'" />
              <span class="text-[16.5px] font-black text-car-text">
                {{ store.settings.expert_rabbit ? '专家模式已激活 (已解锁)' : '专家模式安全锁定中' }}
              </span>
            </div>
            <div class="text-[14px] text-car-sub font-bold leading-relaxed">
              {{ store.settings.expert_rabbit ? '已解锁：支持向原厂时钟屏保注入第三方高德或音乐' : '用于突破特定机型白名单，普通安装请优先使用左侧正解通道' }}
            </div>
          </div>

          <div class="flex items-center space-x-2 shrink-0">
            <button 
              v-if="store.settings.expert_rabbit"
              @click="openRabbitGuideModal"
              class="h-[64px] px-4 rounded-xl bg-car-card border-2 border-car-accent text-car-accent font-black text-[15px] cursor-pointer hover:border-car-accent transition-all shadow-sm whitespace-nowrap"
            >
              打开向导
            </button>
            <button 
              @click="confirmUnlockExpert"
              :class="[
                'h-[64px] px-4 rounded-xl border-2 font-black text-[15px] cursor-pointer transition-all shadow-sm whitespace-nowrap',
                store.settings.expert_rabbit 
                  ? 'bg-car-card border-car-border text-car-sub hover:text-car-text' 
                  : 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/20'
              ]"
            >
              {{ store.settings.expert_rabbit ? '恢复锁定' : '解锁专家模式' }}
            </button>
          </div>
        </div>
      </FeatureCard>
    </div>

    <!-- 底部运维与安装说明 -->
    <div class="bg-car-item border border-car-border rounded-2xl p-5 text-[14.5px] text-car-sub font-bold leading-relaxed space-y-1.5 shadow-sm">
      <div class="text-[16px] text-car-text font-black mb-1 flex items-center">
        车载应用安装与白名单管理规范说明：
      </div>
      <div>• <b>白名单与应用商店</b>：原厂应用商店运行会重置校验属性导致第三方软件安装失败，必须保持冻结锁定；</div>
      <div>• <b>原生文件管理正解</b>：严禁通过 ADB 命令行 pm install 强装，统一将 APK 放入 Download 目录后由原生文件管理直装；</div>
      <div>• <b>应用全生命周期闭环</b>：在此页面即可完成「环境准备 ➔ 文件传输 ➔ 特权安装 ➔ 卸载与冻结」全套操作。</div>
    </div>
  </div>
</template>

<script setup>
import FeatureCard from '../components/FeatureCard.vue';
import StatusDot from '../components/StatusDot.vue';
import { store, bridge, openModal, showToast } from '../store';
import { openAppstoreFlow } from '../utils/appstoreFreeze';



function confirmToggleWhitelist() {
  const next = !store.deviceInfo.whitelist;
  openModal('confirm', {
    title: next ? '开启第三方 APK 放行白名单' : '关闭第三方 APK 放行白名单',
    desc: next 
      ? '系统将写入 sys.jsbd.apk_verify=1 属性，解除系统原生对第三方软件签名的限制。开启后请配合整车冷重启确保生效。'
      : '关闭后车机将恢复出厂严格签名校验限制，已安装的第三方软件可能出现无法打开或签名报错。确认关闭？',
    tip: '日常使用建议始终保持放行状态。',
    isDanger: !next,
    confirmText: next ? '确认开启白名单' : '确认关闭限制',
    onConfirm: () => {
      bridge.call('toggleWhitelist');
      store.deviceInfo.whitelist = next;
      showToast(next ? '第三方白名单已开启' : '第三方白名单已关闭', 'success');
    }
  });
}

function openAllApps() {
  store.modals.allApps = true;
}

function confirmUnlockExpert() {
  if (store.settings.expert_rabbit) {
    store.settings.expert_rabbit = false;
    bridge.call('setSetting', 'expert_rabbit', false);
    showToast('已恢复安全锁定防护');
    return;
  }

  try {
    bridge.call('prepareThemeAssets');
  } catch (e) {}

  openModal('confirm', {
    title: '【高危警告】解锁专家模式 (第 1/3 次确认)',
    desc: '【专家模式】解除系统原生安全边界，开放直接向车机底层屏保主题注入任意第三方应用的高级权限。非玩车专业人员误操作可能导致屏保黑屏或组件冲突。是否确认继续？',
    tip: '【安全建议】普通车友使用自带白名单与精选安装即可满足全部日常需求。',
    isDanger: true,
    countdown: 10,
    confirmText: '我已知晓风险，下一步',
    onConfirm: () => {
      openModal('confirm', {
        title: '【安全确认】卡兔子主题屏保配置规范 (第 2/3 次确认)',
        desc: '卡兔子主题配置通过重写原厂兔子时钟屏保包名（com.ecarx.screensaver）实现车机原生级适配。在执行主题配置或整车重启前，请务必保证车辆安全停稳并挂入 P 挡。严禁在行驶途中操作！',
        tip: '【操作铁律】严禁在行车行驶过程中执行配置或冷重启！',
        isDanger: true,
        countdown: 5,
        confirmText: '确认环境安全，下一步',
        onConfirm: () => {
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
</script>
