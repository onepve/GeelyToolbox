<template>
  <ModalWrapper
    :show="!!store.modals.rabbitInstall"
    title="卡兔子主题安装向导"
    badge="专家模式"
    maxWidthClass="max-w-[760px]"
    @close="handleClose"
  >
    <div class="flex flex-col space-y-4">
      <!-- 原理说明 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-4">
        <div class="text-[16px] font-black text-car-text">兔子时钟屏保伪装原理</div>
        <p class="text-[14px] text-car-sub leading-relaxed font-bold mt-1">
          将您选定的 APK 伪装打包进兔子时钟主题，应用主题后冷重启车机，即可突破签名限制完成安装。
        </p>
      </div>

      <!-- 流程步骤 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col space-y-2">
        <div class="text-[15px] font-black text-car-text">操作流程</div>
        <div class="text-[14px] text-car-sub font-bold leading-relaxed">
          ① 点击下方按钮，在弹出的文件选择器中挑选要安装的 APK<br>
          ② 助手自动伪装注入兔子时钟主题，并校验完整性<br>
          ③ 手动打开主题中心进入【兔子时钟】，30 秒后自动替换，点击【应用】<br>
          ④ 自行重启车机，开机后用原生文件管理器安装该 APK
        </div>
      </div>

      <!-- 主操作：注入 -->
      <button
        @click="startWizard"
        class="w-full min-h-[76px] p-4 rounded-2xl bg-car-item border-2 border-car-accent cursor-pointer hover:border-car-accent shadow-lg ring-2 ring-car-accent/20 flex flex-col items-center justify-center text-center"
      >
        <span class="text-[20px] font-black text-car-text">选择 APK 并注入兔子主题</span>
        <span class="text-[14px] font-bold text-car-sub mt-1">调起系统文件选择器，选中后自动伪装注入本车</span>
      </button>

      <!-- 次操作：仅导出主题包 -->
      <button
        @click="exportThemeOnly"
        class="w-full min-h-[62px] p-4 rounded-2xl bg-car-item border border-car-border cursor-pointer hover:border-car-border-light flex flex-col items-center justify-center text-center transition-all"
      >
        <span class="text-[17px] font-black text-car-text">仅生成主题包（导出到下载目录）</span>
        <span class="text-[13px] font-bold text-car-sub mt-1">生成 .xtz 文件保存至 Download，可直接分享给其他车友</span>
      </button>
    </div>

    <template #footer>
      <div class="flex items-center justify-end w-full">
        <button
          @click="handleClose"
          class="min-h-[50px] px-8 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-border-light transition-all"
        >
          关闭
        </button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, showToast } from '../../store';

function handleClose() {
  closeModal('rabbitInstall');
}

function startWizard() {
  if (store.deviceInfo?.adb_master_switch === false) {
    showToast('ADB 总开关已关闭，该功能无法使用', 'warn');
    return;
  }
  handleClose();
  bridge.call('selectApkInject');
}

function exportThemeOnly() {
  if (store.deviceInfo?.adb_master_switch === false) {
    showToast('ADB 总开关已关闭，该功能无法使用', 'warn');
    return;
  }
  handleClose();
  bridge.call('generateRabbitThemeOnly');
}
</script>
