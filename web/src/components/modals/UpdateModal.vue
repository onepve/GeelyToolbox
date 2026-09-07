<template>
  <ModalWrapper 
    :show="showModal" 
    :title="updateInfo?.is_beta ? '【测试通道】检测到实验新版' : '检测到新版本发布'" 
    :badge="updateInfo?.is_beta ? 'Beta测试' : 'OTA升级'"
    maxWidthClass="max-w-[760px]"
    @close="closeUpdate"
  >
    <div v-if="updateInfo" class="flex flex-col space-y-4">
      <!-- 版本徽标卡片 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
        <div class="flex items-center">
          <span class="text-[22px] font-black text-car-text mr-3">吉利智驾</span>
          <span 
            :class="[
              'text-[14px] px-3 py-1 rounded-full font-extrabold border',
              updateInfo.is_beta 
                ? 'bg-rose-500/20 border-rose-500 text-rose-400' 
                : 'bg-car-accent/20 border-car-accent text-car-accent'
            ]"
          >
            v{{ updateInfo.version }}
          </span>
        </div>
        <span class="text-[14px] font-mono text-car-sub font-bold">体积: {{ updateInfo.size || '2.3 MB' }}</span>
      </div>

      <!-- 下载进度条卡片 (下载中动态展开，实时展现速度与百分比) -->
      <div v-if="isDownloading" class="bg-car-item border-2 border-car-accent/40 rounded-2xl p-5 flex flex-col space-y-3 shadow-md">
        <div class="flex items-center justify-between text-[16px] font-black text-car-text">
          <span class="flex items-center">
            <span class="w-2.5 h-2.5 rounded-full bg-car-accent mr-2 animate-pulse shadow-[0_0_8px_var(--accent-gold)]"></span>
            <span>正在全速下载升级包 ({{ downloadSpeed || '连接中...' }})...</span>
          </span>
          <span class="font-mono text-[18px] text-car-accent font-black">{{ downloadPercent }}%</span>
        </div>
        <div class="h-4 w-full bg-car-card rounded-full overflow-hidden border border-car-border p-0.5">
          <div 
            class="h-full bg-car-accent rounded-full transition-all duration-200"
            :style="{ width: Math.max(downloadPercent, 3) + '%' }"
          ></div>
        </div>
      </div>

      <!-- 错误提示 -->
      <div v-if="downloadError" class="bg-rose-500/15 border border-rose-500 text-rose-400 p-4 rounded-xl font-bold text-[15px]">
        下载中断：{{ downloadError }}，请检查车机网络后重试。
      </div>

      <!-- 更新日志卡片 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col space-y-2 shadow-sm">
        <div class="text-[17px] font-black text-car-text">更新内容：</div>
        <pre class="text-[15px] font-semibold text-car-sub leading-relaxed whitespace-pre-wrap font-sans">{{ updateInfo.changelog || '全能座舱体验升级与稳定性优化。' }}</pre>
      </div>

      <!-- 提示 -->
      <div class="text-[14px] text-car-sub/80 font-bold px-1">
        提示：点击【不再提醒】后，前台将不再自动弹出升级提示。后续可随时进入【设置 ➔ 关于】手动检查更新或恢复自动检测。
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-between w-full">
        <button 
          v-if="!isDownloading"
          @click="ignoreThisVersion"
          class="h-[54px] px-6 rounded-xl bg-car-item border border-car-border text-car-sub hover:text-car-text font-bold text-[16px] cursor-pointer hover:border-car-border-light transition-all"
        >
          不再提醒
        </button>
        <div v-else></div>

        <div class="flex items-center space-x-3">
          <button 
            @click="closeUpdate"
            class="h-[54px] px-6 rounded-xl bg-car-item border border-car-border text-car-text font-bold text-[16px] cursor-pointer hover:border-car-border-light transition-all"
          >
            {{ isDownloading ? '后台下载' : '稍后再说' }}
          </button>
          <button 
            v-if="!isDownloading"
            @click="startUpgrade"
            class="h-[54px] px-8 rounded-xl bg-car-item border-2 border-car-accent text-car-text font-black text-[18px] cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 shadow-md transition-all"
          >
            立即下载升级
          </button>
          <button 
            v-else
            disabled
            class="h-[54px] px-8 rounded-xl bg-car-item border border-car-border text-car-accent font-black text-[17px] opacity-80"
          >
            正在下载 {{ downloadPercent }}%...
          </button>
        </div>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, showToast } from '../../store';

const showModal = ref(false);
const updateInfo = ref(null);
const isDownloading = ref(false);
const downloadPercent = ref(0);
const downloadSpeed = ref('');
const downloadError = ref('');

onMounted(() => {
  window.showToolboxUpdateModal = function(info) {
    if (!info) return;
    updateInfo.value = info;

    // 检查用户是否勾选了“不再自动提示更新”
    const ignoredVer = localStorage.getItem('geely_ignored_update_version');
    const autoCheck = localStorage.getItem('geely_auto_check_update');
    
    // 如果显式关闭了自动检测更新，且本次是前台静默探针触发，则不弹窗
    if (autoCheck === 'false') {
      console.log('用户已在设置中关闭自动检测更新，静默跳过弹窗');
      return;
    }
    // 如果当前版本被用户标记为“不再提醒”，跳过弹窗
    if (ignoredVer === String(info.version)) {
      console.log(`版本 v${info.version} 已被用户设置为不再提醒，跳过弹窗`);
      return;
    }

    showModal.value = true;
  };

  // 供手动检查更新强制唤起
  window.forceShowUpdateModal = function(info) {
    updateInfo.value = info;
    showModal.value = true;
  };

  // 接收原生下载进度广播
  window.updateToolboxSelfProgress = function(progress, speedStr) {
    isDownloading.value = true;
    downloadPercent.value = progress;
    downloadSpeed.value = speedStr;
    downloadError.value = '';
  };

  window.updateToolboxSelfDone = function() {
    isDownloading.value = false;
    downloadPercent.value = 100;
    showToast('升级包下载完成，正在唤起安全安装通道...');
    setTimeout(() => {
      showModal.value = false;
    }, 1500);
  };

  window.updateToolboxSelfError = function(errMsg) {
    isDownloading.value = false;
    downloadError.value = errMsg || '下载异常';
  };
});

function closeUpdate() {
  showModal.value = false;
}

function ignoreThisVersion() {
  if (updateInfo.value && updateInfo.value.version) {
    localStorage.setItem('geely_ignored_update_version', String(updateInfo.value.version));
  }
  showModal.value = false;
  showToast('已设置不再提示本次更新，后续可在【设置】中手动升级');
}

function startUpgrade() {
  if (!updateInfo.value) return;
  isDownloading.value = true;
  downloadPercent.value = 2;
  downloadSpeed.value = '连接服务器...';
  downloadError.value = '';
  showToast('正在调起全速下载升级包...');
  bridge.call('startToolboxSelfUpdate', updateInfo.value.download_url, `GeelyPilot_${updateInfo.value.version}.apk`);
}
</script>
