<template>
  <ModalWrapper
    :show="store.rabbitProgress.show"
    :title="store.rabbitProgress.mode === 'inject' ? '兔子主题注入向导' : '兔子主题包生成'"
    badge="专家模式"
    maxWidthClass="max-w-[760px]"
    @close="handleClose"
  >
    <div class="flex flex-col space-y-4">

      <!-- ── 命名配置面板（仅 export 模式 + 开始前） ── -->
      <template v-if="store.rabbitProgress.showNaming">
        <div class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col space-y-3">
          <div class="text-[15px] font-black text-car-text">应用名称</div>
          <div class="text-[14px] text-car-sub font-bold">
            {{ store.rabbitProgress.apkLabel || '未知应用' }}
            <span v-if="store.rabbitProgress.apkPkg" class="text-[12px] text-car-sub opacity-60 ml-2">{{ store.rabbitProgress.apkPkg }}</span>
          </div>

          <!-- 名称位置 -->
          <div class="text-[14px] font-black text-car-text mt-2">文件名格式</div>
          <div class="flex space-x-2">
            <button
              v-for="opt in nameModeOptions"
              :key="opt.value"
              @click="store.rabbitProgress.nameMode = opt.value; refreshOutputName()"
              :class="[
                'flex-1 py-2 rounded-xl text-[13px] font-black border transition-all cursor-pointer',
                store.rabbitProgress.nameMode === opt.value
                  ? 'bg-car-item border-car-accent text-car-accent'
                  : 'bg-car-item border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >{{ opt.label }}</button>
          </div>

          <!-- 名称语言（仅带应用名时显示） -->
          <template v-if="store.rabbitProgress.nameMode !== 'standard'">
            <div class="text-[14px] font-black text-car-text">应用名语言</div>
            <div class="flex space-x-2">
              <button
                @click="store.rabbitProgress.nameLang = 'cn'; refreshOutputName()"
                :class="[
                  'flex-1 py-2 rounded-xl text-[13px] font-black border transition-all cursor-pointer',
                  store.rabbitProgress.nameLang === 'cn'
                    ? 'bg-car-item border-car-accent text-car-accent'
                    : 'bg-car-item border-car-border text-car-sub hover:border-car-border-light'
                ]"
              >中文名</button>
              <button
                @click="store.rabbitProgress.nameLang = 'pkg'; refreshOutputName()"
                :class="[
                  'flex-1 py-2 rounded-xl text-[13px] font-black border transition-all cursor-pointer',
                  store.rabbitProgress.nameLang === 'pkg'
                    ? 'bg-car-item border-car-accent text-car-accent'
                    : 'bg-car-item border-car-border text-car-sub hover:border-car-border-light'
                ]"
              >包名（英文）</button>
            </div>
          </template>

          <!-- 路径实时预览 -->
          <div class="text-[14px] font-black text-car-text mt-1">输出路径预览</div>
          <div class="bg-car-bg border border-car-border rounded-xl px-3 py-2 text-[13px] font-mono text-car-accent break-all select-none">
            /sdcard/Download/{{ store.rabbitProgress.outputName }}
          </div>

          <button
            @click="confirmNaming"
            class="w-full min-h-[52px] mt-2 rounded-xl bg-car-accent text-white font-black text-[16px] cursor-pointer hover:opacity-90 transition-all"
          >确认，开始打包</button>
        </div>
      </template>

      <!-- ── 进度面板（打包中 / 完成 / 失败） ── -->
      <template v-else>

        <!-- 进度条 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col space-y-3">
          <div class="flex items-center justify-between">
            <span class="text-[14px] font-black text-car-text">{{ store.rabbitProgress.stepMsg }}</span>
            <span class="text-[13px] font-bold text-car-sub">{{ store.rabbitProgress.progress }}%</span>
          </div>
          <!-- 进度轨道 -->
          <div class="w-full h-3 bg-car-bg rounded-full overflow-hidden">
            <div
              class="h-full rounded-full transition-all duration-500"
              :class="store.rabbitProgress.error ? 'bg-red-500' : 'bg-car-accent'"
              :style="{ width: store.rabbitProgress.progress + '%' }"
            ></div>
          </div>
          <!-- 步骤指示 -->
          <div class="flex items-center space-x-1 mt-1">
            <div
              v-for="n in 4"
              :key="n"
              :class="[
                'flex-1 h-1 rounded-full transition-all duration-300',
                store.rabbitProgress.step >= n ? 'bg-car-accent' : 'bg-car-border'
              ]"
            ></div>
          </div>
          <div class="flex justify-between text-[11px] text-car-sub font-bold">
            <span :class="store.rabbitProgress.step >= 1 ? 'text-car-accent' : ''">读取APK</span>
            <span :class="store.rabbitProgress.step >= 2 ? 'text-car-accent' : ''">安全校验</span>
            <span :class="store.rabbitProgress.step >= 3 ? 'text-car-accent' : ''">伪装打包</span>
            <span :class="store.rabbitProgress.step >= 4 ? 'text-car-accent' : ''">完整性校验</span>
          </div>
        </div>

        <!-- 失败提示 -->
        <div v-if="store.rabbitProgress.error" class="bg-red-500/10 border border-red-500/30 rounded-2xl p-4">
          <div class="text-[15px] font-black text-red-400">操作失败</div>
          <p class="text-[14px] text-red-300 font-bold mt-1">{{ store.rabbitProgress.errorMsg }}</p>
        </div>

        <!-- 成功 + inject 模式：操作指引 -->
        <div v-if="store.rabbitProgress.done && !store.rabbitProgress.error && store.rabbitProgress.mode === 'inject'" class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col space-y-2">
          <div class="text-[15px] font-black text-car-accent">主题包已准备就绪！</div>
          <p class="text-[14px] text-car-sub font-bold leading-relaxed">
            点击下方按钮后，助手将自动打开系统主题中心。<br>
            请在 30 秒内手动进入【屏保时钟】➔【兔子时钟】页面，<br>
            倒计时结束后自动替换，再点击【应用】即可生效。
          </p>
          <button
            v-if="!store.rabbitProgress.themeCenterLaunched"
            @click="launchThemeCenter"
            class="w-full min-h-[56px] mt-2 rounded-xl bg-car-accent text-white font-black text-[17px] cursor-pointer hover:opacity-90 transition-all"
          >我准备好了，前往主题中心</button>
          <div v-else class="text-[13px] text-car-sub font-bold text-center py-2">主题中心已打开，请按上方步骤操作</div>
        </div>

        <!-- 成功 + export 模式：导出完成 -->
        <div v-if="store.rabbitProgress.done && !store.rabbitProgress.error && store.rabbitProgress.mode === 'export'" class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col space-y-2">
          <div class="text-[15px] font-black text-car-accent">主题包导出成功！</div>
          <p class="text-[14px] text-car-sub font-bold leading-relaxed">
            文件已保存至下载目录，可直接分享给其他车友使用。<br>
            对方将文件放入 <span class="text-car-text font-mono text-[12px]">/sdcard/XUI/theme/</span> 目录即可在主题中心识别。
          </p>
          <div class="bg-car-bg border border-car-border rounded-xl px-3 py-2 text-[13px] font-mono text-car-accent break-all select-none">
            /sdcard/Download/{{ store.rabbitProgress.outputName }}
          </div>
        </div>

        <!-- 覆盖确认 -->
        <div v-if="store.rabbitProgress.showOverwrite" class="bg-yellow-500/10 border border-yellow-500/30 rounded-2xl p-4 flex flex-col space-y-3">
          <div class="text-[15px] font-black text-yellow-400">文件已存在</div>
          <p class="text-[14px] text-yellow-300 font-bold">下载目录中已有同名主题包，是否覆盖？</p>
          <div class="bg-car-bg border border-car-border rounded-xl px-3 py-2 text-[13px] font-mono text-car-sub break-all">
            /sdcard/Download/{{ store.rabbitProgress.outputName }}
          </div>
          <div class="flex space-x-3">
            <button
              @click="cancelOverwrite"
              class="flex-1 min-h-[50px] rounded-xl bg-car-item border border-car-border text-car-text font-black text-[15px] cursor-pointer hover:border-car-border-light transition-all"
            >取消</button>
            <button
              @click="confirmOverwrite"
              class="flex-1 min-h-[50px] rounded-xl bg-yellow-500 text-white font-black text-[15px] cursor-pointer hover:opacity-90 transition-all"
            >覆盖</button>
          </div>
        </div>

      </template>
    </div>

    <template #footer>
      <div class="flex items-center justify-end w-full">
        <button
          v-if="store.rabbitProgress.done || store.rabbitProgress.error"
          @click="handleClose"
          class="min-h-[50px] px-8 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-border-light transition-all"
        >关闭</button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { computed } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, showToast } from '../../store';

const nameModeOptions = [
  { value: 'standard', label: '标准名' },
  { value: 'left', label: '应用名在左' },
  { value: 'right', label: '应用名在右' },
];

function refreshOutputName() {
  const p = store.rabbitProgress;
  if (p.nameMode === 'standard') {
    p.outputName = 'clock.rabbit.xtz';
    return;
  }
  const name = p.nameLang === 'cn' ? (p.apkLabel || '未知应用') : (p.apkPkg || 'unknown');
  // 去掉文件名非法字符
  const safe = name.replace(/[\/\\:*?"<>|]/g, '_');
  if (p.nameMode === 'left') {
    p.outputName = safe + '_clock.rabbit.xtz';
  } else {
    p.outputName = 'clock.rabbit_' + safe + '.xtz';
  }
}

function confirmNaming() {
  refreshOutputName();
  store.rabbitProgress.showNaming = false;
  store.rabbitProgress.step = 0;
  store.rabbitProgress.progress = 0;
  bridge.call('confirmRabbitExport', store.rabbitProgress.outputName);
}

function confirmOverwrite() {
  store.rabbitProgress.showOverwrite = false;
  bridge.call('confirmRabbitExportOverwrite');
}

function cancelOverwrite() {
  store.rabbitProgress.showOverwrite = false;
  store.rabbitProgress.done = false;
  store.rabbitProgress.error = true;
  store.rabbitProgress.errorMsg = '用户取消覆盖，主题包未导出。';
}

function launchThemeCenter() {
  store.rabbitProgress.themeCenterLaunched = true;
  bridge.call('launchThemeCenterForRabbit');
}

function handleClose() {
  store.rabbitProgress.show = false;
  // 重置状态
  setTimeout(() => {
    Object.assign(store.rabbitProgress, {
      show: false, mode: 'export', step: 0, stepMsg: '', progress: 0,
      done: false, error: false, errorMsg: '', apkLabel: '', apkPkg: '',
      nameMode: 'standard', nameLang: 'cn', outputName: '',
      showNaming: false, showOverwrite: false, themeCenterLaunched: false
    });
  }, 300);
}
</script>
