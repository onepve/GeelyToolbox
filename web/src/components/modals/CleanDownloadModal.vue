<template>
  <ModalWrapper 
    :show="store.modals.cleanDownload" 
    title="吉利智驾 · 车载下载目录清理中枢" 
    badge="存储空间优化"
    maxWidthClass="max-w-[880px]"
    @close="closeModal('cleanDownload')"
  >
    <div class="flex flex-col space-y-5">
      <!-- 顶部存储路径与当前状态看板 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
        <div class="flex items-center space-x-3.5">
          <span class="w-3.5 h-3.5 rounded-full bg-emerald-500 shadow-[0_0_8px_#10B981]"></span>
          <div class="flex flex-col">
            <span class="text-[17.5px] font-black text-car-text">目标目录: {{ dirStats.path }}</span>
            <span class="text-[14px] text-car-sub font-mono font-bold mt-0.5">当前文件统计: {{ dirStats.count }} 项 · 占用存储约 {{ dirStats.size_mb }} MB</span>
          </div>
        </div>
        <button 
          @click="fetchStats"
          class="h-[46px] px-5 rounded-xl bg-car-card border border-car-border text-car-text font-black text-[15px] cursor-pointer hover:border-car-border-light shadow-sm transition-all"
        >
          刷新状态
        </button>
      </div>

      <!-- 三大清理维度大磁贴 -->
      <div class="flex flex-col space-y-3.5">
        <!-- 选项 1：彻底全量清空 -->
        <div 
          @click="confirmClean(1)"
          class="bg-car-card border-2 border-car-border rounded-2xl p-5 flex items-center justify-between cursor-pointer hover:border-car-border-light hover:bg-car-item transition-all shadow-sm group"
        >
          <div class="flex-1 min-w-0 pr-6 flex flex-col space-y-1">
            <div class="flex items-center space-x-2.5">
              <span class="text-[19px] font-black text-car-text">🔥 彻底全量清空</span>
              <span class="px-2.5 py-0.5 rounded-md bg-amber-500/15 border border-amber-500/30 text-amber-400 text-[12.5px] font-black">强力排障</span>
            </div>
            <span class="text-[14.5px] text-car-sub font-bold leading-relaxed">
              强制清除 Download 目录下的全部文件与所有子文件夹。空间极度告急、历史残留过多时使用，彻底重置为崭新空目录。
            </span>
          </div>
          <button class="h-[52px] px-6 bg-car-card border-2 border-car-border text-car-text font-black text-[16px] rounded-xl group-hover:border-car-border-light shrink-0 transition-all pointer-events-none">
            全量清空
          </button>
        </div>

        <!-- 选项 2：智能安全清理 -->
        <div 
          @click="confirmClean(2)"
          class="bg-car-card border-2 border-car-accent/60 rounded-2xl p-5 flex items-center justify-between cursor-pointer hover:border-car-accent hover:bg-car-item ring-2 ring-car-accent/15 transition-all shadow-md group"
        >
          <div class="flex-1 min-w-0 pr-6 flex flex-col space-y-1">
            <div class="flex items-center space-x-2.5">
              <span class="text-[19px] font-black text-car-text group-hover:text-car-accent">🛡️ 智能安全清理</span>
              <span class="px-2.5 py-0.5 rounded-md bg-car-accent/15 border border-car-accent/30 text-car-accent text-[12.5px] font-black">官方推荐</span>
            </div>
            <span class="text-[14.5px] text-car-sub font-bold leading-relaxed">
              清理已下载的 APK 安装包、临时压缩包与空文件夹；自动识别并保留音频素材与非空目录，安全释放数百兆闪存。
            </span>
          </div>
          <button class="h-[52px] px-6 bg-car-item border-2 border-car-accent text-car-text font-black text-[16px] rounded-xl ring-2 ring-car-accent/20 group-hover:border-car-accent shrink-0 transition-all pointer-events-none">
            安全清理
          </button>
        </div>

        <!-- 选项 3：仅清理散落普通文件 -->
        <div 
          @click="confirmClean(3)"
          class="bg-car-card border-2 border-car-border rounded-2xl p-5 flex items-center justify-between cursor-pointer hover:border-car-border-light hover:bg-car-item transition-all shadow-sm group"
        >
          <div class="flex-1 min-w-0 pr-6 flex flex-col space-y-1">
            <div class="flex items-center space-x-2.5">
              <span class="text-[19px] font-black text-car-text group-hover:text-car-accent">📁 仅清理散落普通文件</span>
              <span class="px-2.5 py-0.5 rounded-md bg-car-item border border-car-border text-car-sub text-[12.5px] font-black">保留所有文件夹</span>
            </div>
            <span class="text-[14.5px] text-car-sub font-bold leading-relaxed">
              只清理 Download 根目录下直接散落的单体文件；对 Download 目录下的所有文件夹及其内部层级完全保留，不触碰任何目录。
            </span>
          </div>
          <button class="h-[52px] px-6 bg-car-card border-2 border-car-border text-car-text font-black text-[16px] rounded-xl group-hover:border-car-border-light shrink-0 transition-all pointer-events-none">
            仅清文件
          </button>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-between w-full">
        <button 
          @click="openFileManager"
          class="h-[52px] px-6 rounded-xl bg-car-item border border-car-border text-car-sub hover:text-car-text font-bold text-[15.5px] cursor-pointer hover:border-car-border-light transition-all"
        >
          📁 查看 Download 目录
        </button>
        <button 
          @click="closeModal('cleanDownload')"
          class="h-[52px] px-8 rounded-xl bg-car-item border-2 border-car-border text-car-text font-black text-[17px] cursor-pointer hover:border-car-border-light shadow-sm"
        >
          关闭
        </button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { ref, watch } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, openModal, showToast } from '../../store';

const dirStats = ref({ count: 0, size_mb: '0.00', path: '/sdcard/Download/' });

watch(() => store.modals.cleanDownload, (show) => {
  if (show) {
    fetchStats();
  }
});

function fetchStats() {
  try {
    const raw = bridge.call('getDownloadDirStats');
    if (raw) {
      dirStats.value = typeof raw === 'string' ? JSON.parse(raw) : raw;
    }
  } catch (e) {}
}

function openFileManager() {
  closeModal('cleanDownload');
  bridge.call('openFileManager');
}

function confirmClean(mode) {
  let title = '确认安全清理';
  let desc = '即将执行智能安全清理：删除旧 APK 安装包与临时缓存，自动保留已解压音频素材与非空目录。';
  let isDanger = false;

  if (mode === 1) {
    title = '【高危确认】彻底全量清空 Download 目录';
    desc = '即将强制清除 /sdcard/Download/ 下的全部文件与所有子文件夹！此操作不可逆，请确认是否继续？';
    isDanger = true;
  } else if (mode === 3) {
    title = '确认仅清理散落文件';
    desc = '即将仅清理 /sdcard/Download/ 根层级下直接散落的普通单体文件，所有子文件夹无论非空均完全保留。';
    isDanger = false;
  }

  openModal('confirm', {
    title,
    desc,
    isDanger,
    confirmText: '确认立即执行清理',
    onConfirm: () => {
      showToast('正在执行下载目录清理...');
      bridge.call('cleanDownloadDirectory', mode);
      setTimeout(() => {
        fetchStats();
      }, 1200);
    }
  });
}
</script>
