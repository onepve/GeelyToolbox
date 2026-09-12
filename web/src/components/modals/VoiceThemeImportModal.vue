<template>
  <ModalWrapper 
    :show="store.modals.voiceThemeImport" 
    title="吉利智驾 · 导入车载语音包 (.zip)" 
    badge="座舱音频主题"
    maxWidthClass="max-w-[880px]"
    @close="closeModal('voiceThemeImport')"
  >
    <div class="flex flex-col space-y-5">
      <!-- 顶部提示与目录看板 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
        <div class="flex items-center space-x-3.5">
          <span class="w-3.5 h-3.5 rounded-full bg-car-accent shadow-[0_0_8px_var(--accent-gold)]"></span>
          <div class="flex flex-col">
            <span class="text-[17.5px] font-black text-car-text">扫描路径: /sdcard/Download/</span>
            <span class="text-[14px] text-car-sub font-mono font-bold mt-0.5">
              已检测到 {{ zipList.length }} 个待导入语音包 ZIP · 解压后归位至 /sdcard/GeelyPilot/voices/
            </span>
          </div>
        </div>
        <button 
          @click="scanZips"
          class="h-[46px] px-5 rounded-xl bg-car-card border border-car-border text-car-text font-black text-[15px] cursor-pointer hover:border-car-border-light shadow-sm transition-all shrink-0"
        >
          🔄 重新扫描
        </button>
      </div>

      <!-- 扫描到的 ZIP 列表 -->
      <div class="flex flex-col space-y-3">
        <div class="text-[16px] font-black text-car-text flex items-center space-x-2">
          <span>📦 待导入语音压缩包</span>
          <span class="text-[13px] text-car-sub font-normal">(点击即可一键解压并部署到专属目录)</span>
        </div>

        <div v-if="zipList.length === 0" class="bg-car-card border border-car-border rounded-2xl p-8 flex flex-col items-center justify-center space-y-3 text-center">
          <span class="text-[36px]">📂</span>
          <div class="text-[17px] font-black text-car-text">Download 目录下暂无可用语音 ZIP 包</div>
          <div class="text-[14px] text-car-sub font-bold max-w-[560px] leading-relaxed">
            你可以通过【手机无线快传】直接将制作好的语音包发送到车机，或者将 U 盘中的 ZIP 考至 Download 目录后重新扫描。
          </div>
        </div>

        <div v-else class="flex flex-col space-y-3 max-h-[260px] overflow-y-auto pr-1">
          <div 
            v-for="zip in zipList" 
            :key="zip.name"
            class="bg-car-card border-2 border-car-border hover:border-car-accent/60 rounded-2xl p-4 flex items-center justify-between shadow-sm transition-all"
          >
            <div class="flex-1 min-w-0 pr-4 flex flex-col space-y-1">
              <div class="flex items-center space-x-2.5">
                <span class="text-[18px] font-black text-car-text truncate">{{ zip.name }}</span>
                <span class="px-2 py-0.5 rounded-md bg-car-accent/15 border border-car-accent/30 text-car-accent text-[12px] font-black shrink-0">ZIP 语音包</span>
              </div>
              <span class="text-[13.5px] text-car-sub font-mono font-bold">
                体积: {{ zip.size }} · 时间: {{ zip.time }}
              </span>
            </div>

            <button 
              @click="importZip(zip.name)"
              class="h-[48px] px-6 bg-car-item border-2 border-car-accent text-car-text font-black text-[16px] rounded-xl hover:border-car-accent ring-2 ring-car-accent/20 cursor-pointer shadow-md shrink-0 transition-all"
            >
              📥 一键解压导入
            </button>
          </div>
        </div>
      </div>

      <!-- 手机快传极简制作向导卡片 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
        <div class="flex-1 min-w-0 pr-4 flex flex-col space-y-1">
          <div class="flex items-center space-x-2">
            <span class="text-[17px] font-black text-car-text">📱 手机无线制作与快传向导</span>
            <span class="text-[12px] px-2 py-0.5 rounded bg-amber-500/15 text-amber-400 font-black border border-amber-500/30">免电脑</span>
          </div>
          <span class="text-[14px] text-car-sub font-bold leading-relaxed">
            手机扫码连接快传页面，可直接下载官方空模板与 README 制作指南；制作完成后秒传至车机，全自动解包归位！
          </span>
        </div>

        <button 
          @click="openQrCodeModal"
          class="h-[48px] px-6 bg-car-card border-2 border-car-border text-car-accent font-black text-[15.5px] rounded-xl hover:border-car-border-light cursor-pointer shadow-sm shrink-0 transition-all"
        >
          📡 打开快传二维码
        </button>
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-between w-full">
        <span class="text-[14px] text-car-sub font-bold">
          物理隔离保障：语音包解压后锁定在 /sdcard/GeelyPilot/voices/，清空下载目录永不丢失
        </span>
        <button 
          @click="closeModal('voiceThemeImport')"
          class="h-[52px] px-8 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[16.5px] cursor-pointer hover:border-car-border-light shadow-sm"
        >
          关闭
        </button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, openModal, showToast } from '../../store';

const zipList = ref([]);

function scanZips() {
  try {
    const raw = bridge.call('scanVoiceZipsInDownload');
    if (raw) {
      zipList.value = JSON.parse(raw);
    } else {
      zipList.value = [];
    }
  } catch (e) {
    zipList.value = [];
  }
}

watch(() => store.modals.voiceThemeImport, (shown) => {
  if (shown) {
    scanZips();
  }
});

onMounted(() => {
  if (store.modals.voiceThemeImport) {
    scanZips();
  }
});

function importZip(fileName) {
  showToast(`正在解压导入【${fileName}】...`);
  bridge.call('importVoiceZipFromDownload', fileName);
  setTimeout(() => {
    closeModal('voiceThemeImport');
  }, 1000);
}

function openQrCodeModal() {
  closeModal('voiceThemeImport');
  openModal('qrCode');
}
</script>
