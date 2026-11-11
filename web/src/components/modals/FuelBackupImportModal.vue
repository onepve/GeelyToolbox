<template>
  <ModalWrapper
    :show="store.modals.fuelBackupImport"
    title="导入加油台账备份"
    badge="行程油耗"
    maxWidthClass="max-w-[880px]"
    @close="closeModal('fuelBackupImport')"
  >
    <div class="flex flex-col space-y-5">
      <!-- 扫描路径与备份数量 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
        <div class="flex items-center space-x-3.5">
          <span class="w-3.5 h-3.5 rounded-full bg-car-accent"></span>
          <div class="flex flex-col">
            <span class="text-[17.5px] font-black text-car-text">备份位置: /sdcard/Download/</span>
            <span class="text-[14px] text-car-sub font-mono font-bold mt-0.5">
              找到 {{ backupList.length }} 份加油台账备份 · 可用手机快传或 U 盘拷入
            </span>
          </div>
        </div>
        <button
          @click="scanBackups"
          class="h-[52px] px-6 rounded-2xl bg-car-card border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-border-light shadow-sm transition-all shrink-0"
        >
          重新扫描
        </button>
      </div>

      <!-- 备份列表 -->
      <div class="flex flex-col space-y-3">
        <div class="text-[16px] font-black text-car-text flex items-center space-x-2">
          <span>📋 可导入的台账备份</span>
          <span class="text-[13px] text-car-sub font-normal">(点右侧「导入恢复」即可还原)</span>
        </div>

        <div v-if="backupList.length === 0" class="bg-car-card border border-car-border rounded-2xl p-8 flex flex-col items-center justify-center space-y-3 text-center">
          <span class="text-[36px]">📂</span>
          <div class="text-[17px] font-black text-car-text">Download 目录里还没有台账备份</div>
          <div class="text-[14px] text-car-sub font-bold max-w-[560px] leading-relaxed">
            先回到记油页面点一次「导出备份」，或者把手机/电脑上的备份文件传到车机 Download 目录，再回来重新扫描。
          </div>
        </div>

        <div v-else class="flex flex-col space-y-3 max-h-[260px] overflow-y-auto pr-1">
          <div
            v-for="bk in backupList"
            :key="bk.name"
            class="bg-car-card border-2 border-car-border hover:border-car-accent/60 rounded-2xl p-4 flex items-center justify-between shadow-sm transition-all"
          >
            <div class="flex-1 min-w-0 pr-4 flex flex-col space-y-1">
              <div class="flex items-center space-x-2.5">
                <span class="text-[16px] font-black text-car-text truncate">{{ bk.name }}</span>
                <span class="px-2 py-0.5 rounded-md bg-car-item border border-car-accent text-car-accent text-[12px] font-black shrink-0">
                  {{ bk.records }} 笔
                </span>
              </div>
              <span class="text-[13.5px] text-car-sub font-mono font-bold">
                体积: {{ bk.size }} · 时间: {{ bk.time }}
              </span>
            </div>

            <button
              @click="onImport(bk)"
              class="h-[52px] px-5 rounded-2xl bg-car-card border-2 border-car-accent text-car-accent font-black text-[15px] cursor-pointer hover:bg-car-accent hover:text-car-text shadow-sm transition-all shrink-0"
            >
              导入恢复
            </button>
          </div>
        </div>
      </div>

      <!-- 安全说明：让用户放心点 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-4 flex items-start space-x-3">
        <span class="text-[20px] leading-none mt-0.5" style="color: var(--text-main);">🛡️</span>
        <div class="flex flex-col space-y-1">
          <span class="text-[14.5px] font-black text-car-text">导入前会自动给您当前的记录留一份备份</span>
          <span class="text-[13px] text-car-sub font-bold leading-relaxed">
            导入是整体替换：用备份里的台账覆盖当前台账。替换前助手会先把现在的记录自动导出一份放在 Download 里，万一导错还能找回上一份。
          </span>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-end w-full">
        <button
          type="button"
          @click="closeModal('fuelBackupImport')"
          class="min-h-[56px] px-7 rounded-2xl bg-car-item border-2 border-car-border text-car-sub font-black text-[16px] hover:text-car-text transition-all cursor-pointer"
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
import { store, closeModal, openModal, showToast, scanFuelBackups, importFuelBackup } from '../../store';

const backupList = ref([]);

function scanBackups() {
  backupList.value = scanFuelBackups();
}

watch(() => store.modals.fuelBackupImport, (shown) => {
  if (shown) scanBackups();
});

onMounted(() => {
  if (store.modals.fuelBackupImport) scanBackups();
});

function onImport(bk) {
  // 整体替换不可逆，必须二次确认（用户规范：不可逆操作要有明确弹窗）
  openModal('confirm', {
    title: '确认导入这份台账备份？',
    desc: `将用【${bk.name}】里的 ${bk.records} 笔记录替换当前台账。\n导入前助手会自动把当前记录导出成一份备份，放在 Download 目录里，导错了可以找回。`,
    confirmText: '确定导入',
    isDanger: true,
    onConfirm: () => {
      const res = importFuelBackup(bk.name);
      if (res && res.ok) {
        closeModal('fuelBackupImport');
        showToast(`已导入 ${res.records} 笔加油记录`, 'success');
        if (res.safety) {
          setTimeout(() => showToast(`导入前的记录已备份为 ${res.safety}`, 'info'), 1200);
        }
      } else {
        showToast((res && res.msg) || '导入失败，请重试', 'danger');
      }
    }
  });
}
</script>
