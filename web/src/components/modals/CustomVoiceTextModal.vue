<template>
  <ModalWrapper 
    :show="!!store.modals.customVoiceText" 
    :title="`自定义语音台词: ${targetInfo?.title || '车身联动'}`" 
    badge="小爱TTS"
    maxWidthClass="max-w-[820px]"
    @close="closeModal('customVoiceText')"
  >
    <div class="flex flex-col gap-5">
      <div class="text-[16px] text-car-sub font-bold">
        请输入你希望车机在触发该动作时朗读的自定义文本，点击试听可实时预览小爱 TTS 合成音色。
      </div>

      <!-- 文本输入框 -->
      <textarea 
        v-model="customText"
        rows="4"
        placeholder="例如: 老公欢迎回家，系好安全带准备出发啦！"
        class="w-full bg-car-item border-2 border-car-border rounded-2xl p-4 text-car-text text-[18px] font-bold outline-none focus:border-car-accent resize-none leading-relaxed"
      ></textarea>

      <!-- 操作与试听按钮行 -->
      <div class="flex items-center justify-between">
        <button 
          @click="resetToDefault"
          class="h-[50px] px-6 rounded-xl bg-car-item border border-car-border text-car-sub hover:text-car-text font-black text-[16px] cursor-pointer"
        >
          恢复出厂默认 (晓晓知性原声)
        </button>
        <button 
          @click="testTtsVoice"
          class="h-[50px] px-6 rounded-xl bg-car-item border border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-border-light"
        >
          试听当前文字 TTS
        </button>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end w-full">
        <button 
          @click="saveCustomVoice"
          class="min-h-[58px] px-8 bg-car-item border-2 border-car-accent rounded-xl text-car-text font-black text-[18px] cursor-pointer hover:border-car-accent shadow-md ring-2 ring-car-accent/20"
        >
          保存台词并立即生效
        </button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, showToast } from '../../store';

const targetInfo = computed(() => store.modals.customVoiceText);
const customText = ref('');

watch(() => store.modals.customVoiceText, (info) => {
  if (info && info.text) {
    customText.value = info.text;
  } else {
    customText.value = '';
  }
});

function testTtsVoice() {
  if (!customText.value.trim()) {
    showToast('请先输入自定义台词');
    return;
  }
  bridge.call('testVehicleVoice', targetInfo.value?.key, customText.value.trim());
  showToast('正在合成并试听语音...');
}

function resetToDefault() {
  if (!targetInfo.value) return;
  bridge.call('resetCustomVoice', targetInfo.value.key);
  showToast('已恢复出厂内置温婉知性原声');
  closeModal('customVoiceText');
}

function saveCustomVoice() {
  if (!targetInfo.value) return;
  bridge.call('setVehicleAutomationSetting', `custom_voice_text_${targetInfo.value.key}`, customText.value.trim());
  showToast('自定义语音台词已保存生效！');
  closeModal('customVoiceText');
}
</script>
