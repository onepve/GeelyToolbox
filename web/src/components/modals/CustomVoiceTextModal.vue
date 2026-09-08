<template>
  <ModalWrapper 
    :show="!!store.modals.customVoiceText" 
    :title="`自定义语音台词: ${targetInfo?.title || '车身联动'}`" 
    badge="小爱TTS"
    maxWidthClass="max-w-[860px]"
    @close="closeModal('customVoiceText')"
  >
    <div class="flex flex-col space-y-6">
      <div class="text-[17.5px] text-car-sub font-extrabold leading-relaxed">
        请输入你希望车机在触发该动作时朗读的自定义文本，点击试听可实时调用小爱 TTS 引擎预览合成声线。
      </div>

      <!-- 文本输入框 (大字号 20px) -->
      <textarea 
        v-model="customText" 
        rows="4" 
        placeholder="例如: 老公欢迎回家，系好安全带准备出发啦！"
        class="w-full bg-car-card border-2 border-car-border rounded-2xl p-5 text-car-text text-[20px] font-black outline-none focus:border-car-accent resize-none leading-relaxed shadow-inner placeholder-car-sub"
      ></textarea>

      <!-- 操作与试听大按钮行 (66px) -->
      <div class="flex space-x-4">
        <button 
          @click="resetToDefault"
          class="flex-1 min-h-[66px] px-6 rounded-2xl bg-car-item border-2 border-car-border text-car-sub hover:text-car-text font-black text-[18.5px] cursor-pointer hover:border-car-border-light shadow-sm"
        >
          恢复出厂默认 (晓晓知性原声)
        </button>
        <button 
          @click="testTtsVoice"
          class="flex-1 min-h-[66px] px-6 rounded-2xl bg-car-item border-2 border-car-border text-car-text font-black text-[18.5px] cursor-pointer hover:border-car-border-light shadow-sm"
        >
          试听当前文字 TTS
        </button>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end w-full">
        <button 
          @click="saveCustomVoice"
          class="min-h-[72px] px-12 bg-car-item border-2 border-car-accent rounded-2xl text-car-text font-black text-[21px] cursor-pointer hover:border-car-accent shadow-md ring-2 ring-car-accent/20"
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
