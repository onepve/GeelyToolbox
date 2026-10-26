<template>
  <div class="flex flex-col space-y-4">
    <!-- 仅在未指定 initialTab 时作为独立页面展示二级分段胶囊 -->
    <SubTabs v-if="!initialTab" :tabs="installTabs" v-model="activeTab" />

    <!-- Tab 1: 特权安装通道 (双大卡片，一屏收尽，整卡点击，min-h-[260px]) -->
    <div v-if="activeTab === 'channels'" class="grid grid-cols-2 gap-4 items-stretch">
      <!-- 卡片 1：车载原生文件管理 (整卡点击大卡片) -->
      <div 
        @click="openFileManager"
        class="rounded-3xl border-2 border-car-border p-6 shadow-2xl transition-all bg-car-card flex flex-col justify-between cursor-pointer hover:border-car-accent active:scale-[0.99] group min-h-[260px]"
      >
        <div class="flex flex-col space-y-3">
          <div class="flex items-center justify-between pb-3 border-b border-car-border/60">
            <div class="flex items-center space-x-2.5">
              <div class="text-[21px] font-black text-car-text tracking-wide group-hover:text-car-accent transition-colors">
                车载原生文件管理
              </div>
              <HelpDot
                @click.stop="openHelpModal('【功能指南】车载原生文件管理与安全安装规范', '1. 正规安装通道：\n把软件安装包放进车机 Download 文件夹后，点这里的卡片打开车机自带的原生文件管理，在里面点安装，系统就会正常放行。\n\n2. 为什么不用命令行强装：\n用电脑命令行强行静默安装容易导致软件闪退或白屏，通过原生文件管理点击安装才是最稳定正确的姿势。', '传到车机的安装包，统一在原生文件管理里点击安装。')"
                title="查看本功能详细说明与核心原理"
              />
            </div>
            <span class="text-[12.5px] font-extrabold px-3 py-1 rounded-xl bg-car-item border border-car-border text-car-accent flex items-center space-x-1.5">
              <span>点击直接打开</span>
              <span class="text-[14px]">➔</span>
            </span>
          </div>
          <div class="text-[15px] text-car-sub font-bold leading-relaxed">
            请勿使用电脑命令行后台强行安装，易导致应用闪退或白屏。推送 APK 至 <code class="px-1.5 py-0.5 rounded bg-car-item text-car-accent font-mono text-[14px]">/sdcard/Download/</code> 目录，通过原生文件管理点选安装最稳定。
          </div>
        </div>

        <div class="mt-4 pt-3 border-t border-car-border/40 flex items-center justify-between">
          <div class="flex items-center text-[13.5px] text-car-accent font-black">
            <StatusDot class="mr-2" size="sm" color="ok" :glow-px="6" />
            <span>已就绪 · 支持原厂安全安装放行</span>
          </div>
          <button 
            @click.stop="openModal('cleanDownload')"
            class="h-[52px] px-5 bg-car-item border border-car-border hover:border-car-border-light text-car-sub hover:text-car-text font-black text-[14px] rounded-xl cursor-pointer shadow-sm transition-all whitespace-nowrap"
          >
            清理下载目录
          </button>
        </div>
      </div>

      <!-- 卡片 2：局域网无线快传 (整卡点击大卡片) -->
      <div 
        @click="showQrCode"
        class="rounded-3xl border-2 border-car-border p-6 shadow-2xl transition-all bg-car-card flex flex-col justify-between cursor-pointer hover:border-car-accent active:scale-[0.99] group min-h-[260px]"
      >
        <div class="flex flex-col space-y-3">
          <div class="flex items-center justify-between pb-3 border-b border-car-border/60">
            <div class="flex items-center space-x-2.5">
              <div class="text-[21px] font-black text-car-text tracking-wide group-hover:text-car-accent transition-colors">
                局域网无线快传
              </div>
              <HelpDot
                @click.stop="openHelpModal('【功能指南】手机无线局域网极速推包', '1. 手机免插线传包：\n手机连接车机发出的 Wi-Fi 热点后，扫一扫屏幕上的二维码，就能用手机浏览器把安装包秒传到车机上。\n\n2. 传完直接安装：\n传好的安装包会自动存在车机 Download 文件夹，接着点旁边的原生文件管理就能直接安装，免去插拔 U盘或搬电脑的麻烦。', '手机连接车机热点后传输速度最快。')"
                title="查看本功能详细说明与核心原理"
              />
            </div>
            <span class="text-[12.5px] font-extrabold px-3 py-1 rounded-xl bg-car-item border border-car-border text-car-accent flex items-center space-x-1.5">
              <span>点击扫码推包</span>
              <span class="text-[14px]">➔</span>
            </span>
          </div>
          <div class="text-[15px] text-car-sub font-bold leading-relaxed">
            无需繁琐插拔 U 盘或携带电脑，手机连接同一 Wi-Fi 或热点扫码推包，APK 自动保存到车机 Download 目录。
          </div>
        </div>

        <div class="mt-4 pt-3 border-t border-car-border/40 flex items-center justify-between">
          <div class="flex items-center text-[13.5px] text-car-sub font-mono font-bold">
            <span class="whitespace-nowrap min-w-0 truncate">局域网 IP: {{ store.deviceInfo.car_ip ? store.deviceInfo.car_ip + ':8888' : '未连接热点' }}</span>
          </div>
          <span class="text-[13.5px] font-black text-car-accent group-hover:underline">
            打开无线快传码 ➔
          </span>
        </div>
      </div>
    </div>

    <!-- Tab 2: 整车应用高级管理 (左右对称布局 + 整卡可点 + 消除空白) -->
    <div v-if="activeTab === 'manage'" class="grid grid-cols-2 gap-4 items-stretch">
      <!-- 左列：应用高级管理与专家模式卡片 (垂直撑满，min-h-[260px]) -->
      <div class="flex flex-col space-y-4 justify-between min-h-[260px]">
        <!-- 应用高级管理 (整卡点击) -->
        <div 
          @click="openAllApps"
          class="rounded-3xl border-2 border-car-border p-6 shadow-2xl transition-all bg-car-card flex-1 min-w-0 flex flex-col justify-between cursor-pointer hover:border-car-accent active:scale-[0.99] group min-h-[120px]"
        >
          <div class="flex flex-col space-y-3">
            <div class="flex items-center justify-between pb-3 border-b border-car-border/60">
              <div class="flex items-center space-x-2.5">
                <div class="text-[21px] font-black text-car-text tracking-wide group-hover:text-car-accent transition-colors">
                  整车应用高级管理
                </div>
                <HelpDot
                  @click.stop="openHelpModal('【功能指南】整车应用高级管理', '1. 软件分类清晰：\n自动区分车机自带的原厂软件和自己装的第三方软件，防止误删系统核心程序。\n\n2. 冻结释放运存：\n可以快速停用平时不用的预装软件，减少后台占用，让车机更流畅。\n\n3. 一键干净卸载：\n不需要的第三方软件可在此彻底卸载并清理残留缓存。', '安装新版应用前，可先在此卸载旧版本避免冲突。')"
                  title="查看本功能详细说明与核心原理"
                />
              </div>
              <span class="text-[12.5px] font-extrabold px-3 py-1 rounded-xl bg-car-item border border-car-border text-car-accent flex items-center space-x-1.5">
                <span>点击打开</span>
                <span class="text-[14px]">➔</span>
              </span>
            </div>
            <div class="text-[15px] text-car-sub font-bold leading-relaxed">
              内置整车应用分类管理，查看整车已装应用，支持一键卸载残留、冻结预装软件与清理运行缓存。
            </div>
          </div>

          <div class="mt-4 pt-3 border-t border-car-border/40 flex items-center justify-between">
            <div class="flex items-center text-[13.5px] text-emerald-400 font-extrabold">
              <StatusDot class="mr-2" size="sm" color="ok" :glow-px="6" />
              <span>支持系统/用户应用智能分类与状态管控</span>
            </div>
            <span class="text-[14px] font-black text-car-accent group-hover:underline">
              进入管理 ➔
            </span>
          </div>
        </div>

        <!-- 专家模式卡兔子主题安装通道 (整卡点击) -->
        <div 
          v-if="store.isTester || store.settings.expert_rabbit"
          @click="store.settings.expert_rabbit ? openRabbitGuideModal() : confirmUnlockExpert()"
          class="rounded-3xl border-2 border-car-border p-6 shadow-2xl transition-all bg-car-card flex-1 min-w-0 flex flex-col justify-between cursor-pointer hover:border-car-accent active:scale-[0.99] group"
        >
          <div class="flex flex-col space-y-3">
            <div class="flex items-center justify-between pb-3 border-b border-car-border/60">
              <div class="flex items-center space-x-2.5">
                <div class="text-[21px] font-black text-car-text tracking-wide group-hover:text-car-accent transition-colors">
                  卡兔子主题通道
                </div>
                <HelpDot
                  @click.stop="openHelpModal('【功能指南】卡兔子主题通道 (专家模式)', '1. 主题替换高阶通道：\n用于通过原厂时钟屏保等主题方式注入第三方软件的高级功能。\n\n2. 使用建议：\n平时常规安装高德或音乐，优先使用上方的原生文件管理；仅在特殊玩法时按需使用。', '普通车主安装软件使用常规通道即可。')"
                  title="查看本功能详细说明与核心原理"
                />
              </div>
              <div class="bg-car-item border-car-border text-car-text inline-flex items-center space-x-1.5 px-3 py-1 rounded-xl border border-car-border">
                <StatusDot size="sm" :color="store.settings.expert_rabbit ? 'accent' : 'ok'" />
                <span class="text-[13px] font-black text-car-text">
                  {{ store.settings.expert_rabbit ? '专家模式已激活' : '专家模式安全锁定中' }}
                </span>
              </div>
            </div>
            <div class="text-[15px] text-car-sub font-bold leading-relaxed">
              {{ store.settings.expert_rabbit ? '解除地图与音乐安装签名限制，已解锁：支持向原厂时钟屏保注入第三方高德或音乐。' : '解除地图与音乐安装签名限制，用于突破特定机型白名单，普通安装请优先使用特权安装通道。' }}
            </div>
          </div>

          <div class="mt-4 pt-3 border-t border-car-border/40 flex items-center justify-between">
            <span class="text-[13px] text-car-sub font-bold">
              {{ store.settings.expert_rabbit ? '点击直接打开主题替换向导' : '点击按提示安全解锁专家模式' }}
            </span>
            <button 
              @click.stop="confirmUnlockExpert"
              class="h-[52px] px-4 rounded-xl border font-black text-[13.5px] cursor-pointer transition-all shadow-sm flex items-center justify-center whitespace-nowrap"
              :class="store.settings.expert_rabbit ? 'bg-car-item border-car-border text-car-sub hover:text-car-text' : 'bg-car-item border-car-accent text-car-accent'"
            >
              {{ store.settings.expert_rabbit ? '恢复锁定' : '解锁专家模式' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 右列：车载应用安装与白名单说明面板 (左右对称平衡，min-h-[260px]) -->
      <div class="rounded-3xl border-2 border-car-border p-6 shadow-2xl bg-car-card flex flex-col justify-between min-h-[260px]">
        <div class="flex flex-col space-y-4">
          <div class="flex items-center space-x-2.5 pb-3 border-b border-car-border/60">
            <span class="text-car-accent font-black text-[18px]">◆</span>
            <div class="text-[21px] font-black text-car-text tracking-wide">
              车载应用管理规范与白名单须知
            </div>
          </div>

          <div class="space-y-4 text-[15px] text-car-sub font-bold leading-relaxed">
            <div class="bg-car-item border border-car-border rounded-2xl p-4 space-y-1.5">
              <div class="text-car-text font-black text-[16px] flex items-center space-x-2">
                <span class="text-car-accent font-black">【1】</span>
                <span>原厂应用商店与白名单保护</span>
              </div>
              <p class="text-[14px]">
                原厂内置应用商店在后台运行时会主动重置系统安装白名单，从而导致外部安装的第三方应用闪退或失效。建议在系统设置中将其保持冻结状态。
              </p>
            </div>

            <div class="bg-car-item border border-car-border rounded-2xl p-4 space-y-1.5">
              <div class="text-car-text font-black text-[16px] flex items-center space-x-2">
                <span class="text-car-accent font-black">【2】</span>
                <span>原生文件管理器直接点装</span>
              </div>
              <p class="text-[14px]">
                请勿使用电脑 ADB 命令行后台静默强装，强装缺乏系统环境初始化易导致白屏。将 APK 文件推送到 Download 目录后，通过车载原生管理器点选安装最为稳定。
              </p>
            </div>

            <div class="bg-car-item border border-car-border rounded-2xl p-4 space-y-1.5">
              <div class="text-car-text font-black text-[16px] flex items-center space-x-2">
                <span class="text-car-accent font-black">【3】</span>
                <span>安装、冻结与卸载全闭环</span>
              </div>
              <p class="text-[14px]">
                在本模块内即可无缝完成局域网无线传包、特权安全直装、无用预装软件冻结以及旧版本残留卸载，无需依赖任何电脑与外接设备。
              </p>
            </div>
          </div>
        </div>

        <div class="pt-4 mt-2 border-t border-car-border/40 flex items-center justify-between text-[13px] text-car-sub">
          <span>安全环境就绪 · 本地沙盒无联网泄露风险</span>
          <span class="font-mono text-car-accent font-bold">GeelyOS HMI Safe Guard</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import SubTabs from '../components/SubTabs.vue';
import FeatureCard from '../components/FeatureCard.vue';
import StatusDot from '../components/StatusDot.vue';
import HelpDot from '../components/HelpDot.vue';
import { ref } from 'vue';
import { store, bridge, openModal, showToast } from '../store';

const props = defineProps({
  initialTab: { type: String, default: '' }
});

const activeTab = ref(props.initialTab || 'channels');
const installTabs = [
  { id: 'channels', name: '特权安装通道', tag: '文件/快传' },
  { id: 'manage', name: '整车应用管理', tag: '卸载/主题' }
];

function openHelpModal(title, desc, tip) {
  openModal('confirm', {
    title,
    desc,
    tip: tip || '注：本功能带有独立状态防抖与安全保护，可按需随时开关。',
    showCancel: false,
    confirmText: '我知道了'
  });
}

function confirmToggleWhitelist() {
  if (store.deviceInfo.adb_master_switch === false) {
    showToast('ADB 总开关已关闭，该功能无法使用');
    return;
  }
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
  if (store.deviceInfo.adb_master_switch === false) {
    showToast('ADB 总开关已关闭，该功能无法使用');
    return;
  }
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
