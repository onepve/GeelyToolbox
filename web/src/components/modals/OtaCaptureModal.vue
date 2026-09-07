<template>
  <ModalWrapper 
    :show="store.modals.otaCapture" 
    title="抓取原厂 OTA 固件地址" 
    badge="固件逆向与提取"
    maxWidthClass="max-w-[800px]"
    @close="handleClose"
  >
    <div class="flex flex-col space-y-5 text-left py-1">
      <!-- 步骤 1：前置条件询问与确认 -->
      <div v-if="step === 1" class="flex flex-col space-y-4">
        <div class="p-5 rounded-2xl bg-car-card border border-car-border flex flex-col space-y-3">
          <div class="flex items-center space-x-3">
            <span class="text-[26px]">⚠️</span>
            <div class="flex flex-col">
              <span class="text-[17px] font-black text-car-text">请确认：车机自带【系统升级】是否已经运行过？</span>
              <span class="text-[13.5px] text-car-sub font-bold mt-0.5">
                原厂固件下载链接仅在官方升级程序发起联网检查更新时，才会实时记录到系统日志中。
              </span>
            </div>
          </div>
        </div>

        <!-- 详细操作指引卡片 (支持日间黑字/夜间白字高对比度) -->
        <div class="p-5 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-2.5 shadow-sm">
          <span class="text-[16px] font-black text-car-accent flex items-center">
            <span class="mr-1.5">💡</span> 标准操作流程向导：
          </span>
          <div class="text-[14.5px] font-bold leading-relaxed space-y-1.5 pl-1 text-car-text">
            <div>1. 点击下方<b>【打开系统升级】</b>进入车机原厂设置界面，点击一次<b>【检查更新】</b>；</div>
            <div>2. 待车机完成联网检索后，返回吉利智驾并点击<b>【已确认运行过 · 立即抓取】</b>；</div>
            <div>3. 软件将自动检索系统日志缓冲区与 OTA 缓存，秒级提取官方完整升级包下载直链。</div>
          </div>
        </div>

        <!-- 操作按钮组 -->
        <div class="flex items-center justify-between pt-2">
          <button 
            @click="openSystemUpgradeApp"
            class="min-h-[58px] px-6 rounded-xl bg-car-item border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-border-light shadow-sm flex items-center"
          >
            <span class="mr-2">⚡</span> 打开系统升级
          </button>
          <button 
            @click="startCapture"
            class="min-h-[58px] px-8 bg-car-item border-2 border-amber-500/80 rounded-xl text-amber-400 font-black text-[17px] cursor-pointer hover:bg-amber-500/20 ring-2 ring-amber-500/30 shadow-md flex items-center"
          >
            已确认运行过 · 立即抓取
          </button>
        </div>
      </div>

      <!-- 步骤 2：抓取中或结果展示 -->
      <div v-else class="flex flex-col space-y-4">
        <!-- 抓取中状态 -->
        <div v-if="loading" class="py-12 flex flex-col items-center justify-center space-y-3">
          <div class="w-10 h-10 border-4 border-amber-400 border-t-transparent rounded-full animate-spin"></div>
          <span class="text-[16.5px] font-black text-car-text">正在检索系统升级日志与缓存目录...</span>
          <span class="text-[13.5px] text-car-sub font-bold">正在匹配 http/https 固件下载直链</span>
        </div>

        <!-- 抓取成功展示直链 -->
        <div v-else-if="captureResult.success && captureResult.url" class="flex flex-col space-y-4">
          <div class="p-4 rounded-2xl bg-emerald-500/15 border-2 border-emerald-500/40 text-emerald-400 flex items-center space-x-3">
            <span class="text-[24px]">🎉</span>
            <div class="flex flex-col">
              <span class="text-[17px] font-black">成功捕获原厂 OTA 固件下载直链！</span>
              <span class="text-[13px] text-emerald-300/80 font-bold mt-0.5">
                {{ captureResult.details || '已从官方升级服务日志中精准提取' }}
              </span>
            </div>
          </div>

          <!-- URL 结果大框 -->
          <div class="flex flex-col space-y-2">
            <span class="text-[14px] text-car-sub font-bold">官方升级包直接下载地址：</span>
            <div class="p-4 rounded-2xl bg-[#0A0D12] border-2 border-white/10 text-emerald-400 font-mono text-[14.5px] leading-relaxed break-all select-all shadow-inner">
              {{ captureResult.url }}
            </div>
          </div>

          <div class="flex items-center justify-between pt-2">
            <button 
              @click="step = 1"
              class="min-h-[54px] px-6 rounded-xl bg-car-item border border-car-border text-car-sub font-bold text-[15.5px] cursor-pointer hover:border-car-border-light"
            >
              返回重新抓取
            </button>
            <button 
              @click="copyUrl(captureResult.url)"
              class="min-h-[54px] px-8 bg-car-item border-2 border-car-accent rounded-xl text-car-text font-black text-[17px] cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 shadow-md flex items-center"
            >
              <span class="mr-2">📋</span> 一键复制下载直链
            </button>
          </div>
        </div>

        <!-- 未检索到直链时的排查说明 -->
        <div v-else class="flex flex-col space-y-4">
          <div class="p-4 rounded-2xl bg-car-item border border-car-border text-car-text flex items-center space-x-3">
            <span class="text-[24px]">🔍</span>
            <div class="flex flex-col">
              <span class="text-[16.5px] font-black">未在当前日志中捕获到有效下载直链</span>
              <span class="text-[13px] text-car-sub font-bold mt-0.5">
                车机可能已是最新版本暂无推包，或日志已被系统滚动覆盖。
              </span>
            </div>
          </div>

          <!-- 日志输出诊断框 -->
          <div class="flex flex-col space-y-1.5">
            <span class="text-[13.5px] text-car-sub font-bold">抓取日志诊断输出：</span>
            <pre class="p-4 max-h-[180px] overflow-y-auto rounded-2xl bg-[#0A0D12] border border-white/10 text-slate-300 font-mono text-[13px] leading-relaxed select-text whitespace-pre-wrap">{{ captureResult.details || '当前日志缓冲区无 ecarx.upgrade / otaservice 联网记录' }}</pre>
          </div>

          <div class="flex items-center justify-between pt-2">
            <button 
              @click="openSystemUpgradeApp"
              class="min-h-[54px] px-6 rounded-xl bg-car-item border border-car-border text-car-text font-bold text-[15.5px] cursor-pointer hover:border-car-border-light"
            >
              再次打开系统升级
            </button>
            <div class="flex space-x-3">
              <button 
                @click="step = 1"
                class="min-h-[54px] px-6 rounded-xl bg-car-item border border-car-border text-car-sub font-bold text-[15.5px] cursor-pointer hover:border-car-border-light"
              >
                返回第一步
              </button>
              <button 
                @click="startCapture"
                class="min-h-[54px] px-6 bg-car-item border-2 border-amber-500/80 rounded-xl text-amber-400 font-black text-[16px] cursor-pointer hover:bg-amber-500/20"
              >
                立即再抓一次
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-end w-full">
        <button 
          @click="handleClose"
          class="min-h-[54px] px-8 bg-car-item border-2 border-car-border rounded-xl text-car-text font-black text-[17px] cursor-pointer hover:border-car-border-light shadow-sm"
        >
          关闭
        </button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { ref } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, showToast } from '../../store';

const step = ref(1);
const loading = ref(false);
const captureResult = ref({
  success: false,
  url: '',
  version: '',
  details: ''
});

function handleClose() {
  closeModal('otaCapture');
  setTimeout(() => {
    step.value = 1;
    loading.value = false;
  }, 300);
}

function openSystemUpgradeApp() {
  showToast('正在调起原厂系统升级...');
  try {
    bridge.call('openSystemUpgrade');
  } catch (e) {
    showToast('调起失败，请在车机设置中手动打开系统升级');
  }
}

function startCapture() {
  step.value = 2;
  loading.value = true;
  captureResult.value = { success: false, url: '', version: '', details: '' };

  setTimeout(() => {
    try {
      const resStr = bridge.call('extractOta');
      if (resStr) {
        const parsed = typeof resStr === 'string' ? JSON.parse(resStr) : resStr;
        captureResult.value = parsed;
      } else {
        captureResult.value = {
          success: false,
          url: '',
          details: '车机本地未返回日志结果'
        };
      }
    } catch (e) {
      captureResult.value = {
        success: false,
        url: '',
        details: '提取执行异常: ' + e
      };
    } finally {
      loading.value = false;
    }
  }, 600);
}

function copyUrl(url) {
  if (!url) return;
  try {
    navigator.clipboard.writeText(url);
    showToast('直链已成功复制到剪贴板');
  } catch (e) {
    try {
      bridge.call('copyToClipboard', url);
      showToast('直链已成功复制到剪贴板');
    } catch (err) {
      showToast('复制失败，请长按手动选择');
    }
  }
}
</script>
