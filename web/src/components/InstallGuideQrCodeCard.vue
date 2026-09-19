<template>
  <div class="bg-car-card border-2 border-car-accent rounded-3xl p-6 shadow-xl flex flex-col space-y-5">
    <!-- 顶部标题与状态标签 -->
    <div class="flex flex-wrap items-center justify-between pb-4 border-b border-car-border">
      <div class="flex items-center space-x-3">
        <span class="w-3.5 h-3.5 rounded-full bg-car-accent shadow-[0_0_10px_var(--accent-gold)]"></span>
        <span class="text-[21px] font-black text-car-text tracking-wide">缤越 COOL 极客安装两步向导</span>
        <span class="px-3 py-1 rounded-full text-[12.5px] font-black bg-car-item border border-car-accent text-car-accent">
          SX11-A3 / E02 / IHU516G 专属调校
        </span>
      </div>
      <div class="text-[13.5px] text-car-sub font-bold">
        经实车路试验证 · 遵循座舱安全规范
      </div>
    </div>

    <!-- 两步向导核心双栏 -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-5">
      <!-- Step 1: 缤越实测免责声明 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between space-y-4">
        <div class="flex flex-col space-y-2">
          <div class="flex items-center justify-between">
            <div class="flex items-center space-x-2">
              <span class="px-2.5 py-0.5 rounded-md bg-car-card border border-car-accent text-car-accent font-black text-[13px]">
                第一步 (Step 1)
              </span>
              <span class="text-[17.5px] font-black text-car-text">缤越实测免责与安全底线</span>
            </div>
            <StatusDot size="sm" :color="disclaimerAgreed ? 'ok' : 'warn'" />
          </div>
          <div class="text-[14.5px] text-car-sub font-bold leading-relaxed">
            本工具专为吉利缤越 COOL (E02 / IHU516G 双联屏) 深度定制，经实车全功能路试。安装第三方 APK 及底层屏保伪装属于极客提权操作，车主请确保在驻车安全状态下操作，严禁在行驶途中调试或冷重启车机。
          </div>
          <div class="p-3 bg-car-card rounded-xl border border-car-border text-[13px] text-car-sub font-mono">
            提示：严禁使用 ADB pm install 强行静默安装，签名校验拦截会导致白屏或闪退；统一遵循原生特权文件管理通道。
          </div>
        </div>

        <div class="pt-4 border-t border-car-border flex items-center justify-between">
          <span class="text-[13.5px] font-bold text-car-sub">
            {{ disclaimerAgreed ? '已阅读并已知晓车规安全条款' : '操作前请先确认免责安全条款' }}
          </span>
          <button 
            @click="toggleDisclaimer"
            class="h-[56px] px-5 rounded-xl border-2 font-black text-[15px] cursor-pointer transition-all shadow-sm"
            :class="disclaimerAgreed ? 'bg-car-card border-car-border text-car-sub hover:text-car-text' : 'bg-car-item border-car-accent text-car-accent hover:bg-car-card'"
          >
            {{ disclaimerAgreed ? '重读安全免责' : '确认知晓免责' }}
          </button>
        </div>
      </div>

      <!-- Step 2: 商店冻结 / 跳过双通道引导 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between space-y-4">
        <div class="flex flex-col space-y-2">
          <div class="flex items-center justify-between">
            <div class="flex items-center space-x-2">
              <span class="px-2.5 py-0.5 rounded-md bg-car-card border border-car-accent text-car-accent font-black text-[13px]">
                第二步 (Step 2)
              </span>
              <span class="text-[17.5px] font-black text-car-text">应用商店防护双通道</span>
            </div>
            <span class="px-2.5 py-0.5 rounded-full text-[12px] font-extrabold border" :class="isStoreFrozen ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-400' : 'bg-amber-500/15 border-amber-500/40 text-amber-400'">
              {{ isStoreFrozen ? '原厂商店已冻结 (白名单已锁)' : '原厂商店运行中' }}
            </span>
          </div>
          <div class="text-[14.5px] text-car-sub font-bold leading-relaxed">
            吉利原厂应用商店会在后台扫描并拦截第三方安装包或强推更新。您可以选择【通道 A: 安全冻结】锁定白名单（推荐），或选择【通道 B: 跳过校验】走原生文件管理直装。
          </div>
          <div class="grid grid-cols-2 gap-3 pt-1">
            <div class="p-3 bg-car-card rounded-xl border border-car-border flex flex-col justify-between">
              <span class="text-[13px] font-black text-car-text">通道 A (推荐)</span>
              <span class="text-[12px] text-car-sub font-bold mt-1">一键冻结商店，彻底阻断签名拦截与偷跑</span>
            </div>
            <div class="p-3 bg-car-card rounded-xl border border-car-border flex flex-col justify-between">
              <span class="text-[13px] font-black text-car-text">通道 B (跳过)</span>
              <span class="text-[12px] text-car-sub font-bold mt-1">保留官方商店，完全避开其校验通道</span>
            </div>
          </div>
        </div>

        <div class="pt-4 border-t border-car-border flex items-center justify-between">
          <span class="text-[13.5px] font-bold text-car-sub">
            当前状态: {{ isStoreFrozen ? '安全通道 A 生效中' : '可选择一键冻结或跳过' }}
          </span>
          <div class="flex items-center space-x-2">
            <button 
              @click="handleStoreAction"
              class="h-[56px] px-4 rounded-xl border-2 font-black text-[15px] cursor-pointer transition-all shadow-sm"
              :class="isStoreFrozen ? 'bg-car-card border-car-border text-car-sub hover:text-car-text' : 'bg-car-item border-car-accent text-car-accent hover:bg-car-card'"
            >
              {{ isStoreFrozen ? '解冻原厂商店' : '通道 A: 一键冻结' }}
            </button>
            <button 
              @click="handleSkipGuidance"
              class="h-[56px] px-4 rounded-xl bg-car-card border border-car-border hover:border-car-border-light text-car-text font-black text-[15px] cursor-pointer transition-all shadow-sm"
            >
              通道 B: 跳过引导
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 3. 手机扫码二维码矢量卡片 -->
    <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-wrap items-center justify-between">
      <div class="flex items-center space-x-5">
        <!-- 二维码矢量画布 (120x120 纯白底座高对比) -->
        <div class="p-2.5 bg-white rounded-2xl shadow-md border-2 border-car-border shrink-0 flex items-center justify-center">
          <canvas ref="qrCanvasRef" width="110" height="110" class="rounded-lg"></canvas>
        </div>
        <div class="flex flex-col space-y-1">
          <div class="flex items-center space-x-2.5">
            <span class="text-[18px] font-black text-car-text">手机无线快传 · 扫码直连</span>
            <span class="px-2.5 py-0.5 rounded-full text-[12px] font-black border" :class="isWifiConnected ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-400' : 'bg-amber-500/15 border-amber-500/40 text-amber-400'">
              {{ isWifiConnected ? 'Wi-Fi 局域网就绪' : '请让手机与车机在同一 Wi-Fi' }}
            </span>
          </div>
          <div class="text-[14.5px] text-car-sub font-bold leading-relaxed">
            手机与车机连接同一 Wi-Fi 或车机热点，使用微信/浏览器扫码秒开推包页面，安装包秒传至 <code class="px-1.5 py-0.5 rounded bg-car-card text-car-accent font-mono text-[13px]">/sdcard/Download/</code>。
          </div>
          <div class="flex items-center space-x-3 text-[13px] font-mono text-car-sub mt-1">
            <span>直连地址: {{ serverUrl }}</span>
            <button 
              @click="copyUrl" 
              class="px-2.5 py-0.5 rounded bg-car-card hover:bg-car-item border border-car-border text-car-text text-[12px] font-bold cursor-pointer"
            >
              复制
            </button>
          </div>
        </div>
      </div>

      <div class="flex items-center space-x-3 shrink-0 mt-3 md:mt-0">
        <button 
          @click="openFullQrModal"
          class="h-[56px] px-6 rounded-2xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[16px] cursor-pointer shadow-sm transition-all whitespace-nowrap"
        >
          放大二维码
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue';
import QRCode from 'qrcode';
import StatusDot from './StatusDot.vue';
import { store, bridge, openModal, showToast } from '../store';

const qrCanvasRef = ref(null);
const disclaimerAgreed = ref(false);

const serverUrl = computed(() => `http://${store.deviceInfo.car_ip || '127.0.0.1'}:8888`);
const isWifiConnected = computed(() => {
  const ip = store.deviceInfo.car_ip || '';
  return ip.startsWith('192.168.') || ip.startsWith('10.') || ip.startsWith('172.');
});

const isStoreFrozen = computed(() => {
  if (typeof store.deviceInfo?.appstore_frozen === 'boolean') {
    return store.deviceInfo.appstore_frozen;
  }
  try {
    return !!bridge.call('isPackageFrozen', 'com.ecarx.appstore');
  } catch (e) {
    return false;
  }
});

onMounted(() => {
  try {
    disclaimerAgreed.value = localStorage.getItem('geely_disclaimer_agreed') === 'true';
  } catch (e) {}
  renderQr();
});

watch(serverUrl, () => {
  nextTick(() => {
    renderQr();
  });
});

function renderQr() {
  const canvas = qrCanvasRef.value;
  if (!canvas) return;
  QRCode.toCanvas(canvas, serverUrl.value, {
    width: 110,
    margin: 1,
    color: {
      dark: '#000000',
      light: '#FFFFFF'
    }
  }, (error) => {
    if (error) console.error('InstallGuideCard QR error:', error);
  });
}

function toggleDisclaimer() {
  openModal('confirm', {
    title: '吉利缤越 COOL 极客安装免责与车规安全规范',
    desc: '【实车测试保障】本工具已经由吉利缤越 COOL (SX11-A3 / E02) 实车路试验证。但安装第三方 APK、自定义系统配置属于极客调优行为。请务必在车辆停稳挂入 P 挡时操作。若您在行车行驶中操作引起的任何意外，开发者概不负责。是否已知晓并确认？',
    tip: '【操作铁律】严禁在行车过程中安装软件或重启车机！',
    isDanger: false,
    confirmText: '我已知晓并遵守车规安全',
    onConfirm: () => {
      disclaimerAgreed.value = true;
      try {
        localStorage.setItem('geely_disclaimer_agreed', 'true');
      } catch (e) {}
      showToast('已确认免责与车规安全条款', 'success');
    }
  });
}

function handleStoreAction() {
  if (isStoreFrozen.value) {
    openModal('confirm', {
      title: '解冻恢复吉利应用商店',
      desc: '解冻原厂应用商店后，官方商店将可能覆盖系统白名单，导致第三方软件无法顺利安装或后台被拦截。',
      tip: '【高危警告】仅在急需使用吉利官方商店时临时开启，平时强烈建议保持冻结！',
      isDanger: true,
      confirmText: '确认解冻',
      onConfirm: () => {
        bridge.call('toggleFreezeAppStore', false);
        store.deviceInfo.appstore_frozen = false;
        showToast('正在解冻恢复吉利应用商店...');
      }
    });
  } else {
    openModal('confirm', {
      title: '冻结吉利应用商店 (通道 A)',
      desc: '冻结吉利原厂应用商店（com.ecarx.appstore）后，系统将彻底锁定第三方软件安装白名单，避免静默拦截与弹窗强退。',
      tip: '【官方推荐】保持原厂应用商店冻结可确保安装体验最稳。随时可解冻。',
      isDanger: false,
      confirmText: '确认安全冻结',
      onConfirm: () => {
        bridge.call('toggleFreezeAppStore', true);
        store.deviceInfo.appstore_frozen = true;
        showToast('已安全冻结应用商店，白名单已锁定', 'success');
      }
    });
  }
}

function handleSkipGuidance() {
  openModal('confirm', {
    title: '通道 B: 跳过应用商店冻结 (保持原样)',
    desc: '若您希望保留吉利原厂应用商店不冻结，请务必严格通过下方【车载原生文件管理】进入 /sdcard/Download/ 目录点击 APK 进行安装。原生文件管理拥有系统特权打包证书，可绕过商店的拦截校验。严禁使用 ADB pm install 强装！',
    tip: '【提示】若后续遇到安装失败或白屏闪退，请随时返回执行通道 A 一键冻结。',
    isDanger: false,
    confirmText: '我已知晓，保持现状',
    onConfirm: () => {
      showToast('已选定通道 B (跳过官方商店，走原生特权直装)');
    }
  });
}

function openFullQrModal() {
  openModal('qrCode');
}

function copyUrl() {
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(serverUrl.value);
    showToast('快传地址已复制');
  } else {
    showToast('快传地址: ' + serverUrl.value);
  }
}
</script>