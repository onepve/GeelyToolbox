<template>
  <ModalWrapper 
    :show="!!store.modals.voiceItemSettings" 
    :title="`${targetItem?.title || '声效'} · 专车声效个性化设置`" 
    badge="声效配置"
    maxWidthClass="max-w-[880px]"
    @close="closeModal('voiceItemSettings')"
  >
    <div v-if="targetItem" class="flex flex-col space-y-5">
      <!-- 当前生效音源状态大卡片 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
        <div class="flex items-center">
          <span class="w-3 h-3 rounded-full bg-car-accent mr-3 shadow-[0_0_8px_var(--accent-gold)]"></span>
          <div class="flex flex-col">
            <span class="text-[18px] font-black text-car-text">当前播报音源：{{ activeVoiceTypeLabel }}</span>
            <span class="text-[14.5px] text-car-sub font-bold mt-0.5">{{ activeVoiceDesc }}</span>
          </div>
        </div>

        <button 
          @click="testCurrentAudio"
          class="min-h-[54px] px-6 bg-car-card border-2 border-car-accent text-car-text font-black text-[16.5px] rounded-xl cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 shadow-md shrink-0"
        >
          试听生效语音
        </button>
      </div>

      <!-- 1. 自定义 TTS 朗读台词 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col space-y-3 shadow-sm">
        <div class="flex items-center justify-between">
          <div class="text-[18px] font-black text-car-text">1. 自定义 TTS 朗读台词 (小爱语音合成)</div>
          <span class="text-[13px] text-car-sub font-bold">留空则不使用自定义文本</span>
        </div>
        <textarea 
          v-model="customText"
          rows="2"
          placeholder="例如: 已挂入前进挡，系好安全带出发啦！"
          class="w-full bg-[#0A0D12] border-2 border-car-border rounded-xl p-4 text-car-text text-[18px] font-black outline-none focus:border-car-accent resize-none leading-relaxed shadow-inner"
        ></textarea>
        <div class="flex space-x-3">
          <button 
            @click="testTtsText"
            class="flex-1 min-h-[54px] px-6 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[16.5px] cursor-pointer hover:border-car-border-light shadow-sm"
          >
            试听此文字 TTS
          </button>
          <button 
            @click="saveCustomText"
            class="flex-1 min-h-[54px] px-6 rounded-xl bg-car-card border-2 border-car-border text-car-accent font-black text-[16.5px] cursor-pointer hover:border-car-accent shadow-sm"
          >
            保存此台词
          </button>
        </div>
      </div>

      <!-- 2. 自定义本地音频文件 (MP3/WAV) -->
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col space-y-3 shadow-sm">
        <div class="flex items-center justify-between">
          <div class="text-[18px] font-black text-car-text">2. 自定义本地音频文件 (MP3 / WAV)</div>
          <span class="text-[13px] text-car-sub font-bold">优先读取 /sdcard/Music/ 或指定路径</span>
        </div>
        <input 
          v-model="customFilePath"
          type="text"
          placeholder="例如: /sdcard/Music/gear_d.mp3 或 /sdcard/Download/语音主题包/gear_d.mp3"
          class="w-full h-[58px] bg-[#0A0D12] border-2 border-car-border rounded-xl px-4 text-emerald-400 font-mono text-[16px] outline-none focus:border-car-accent shadow-inner"
        />
        <div class="flex space-x-3">
          <button 
            @click="testAudioFile"
            class="flex-1 min-h-[54px] px-6 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[16.5px] cursor-pointer hover:border-car-border-light shadow-sm"
          >
            试听此音频文件
          </button>
          <button 
            @click="saveAudioFilePath"
            class="flex-1 min-h-[54px] px-6 rounded-xl bg-car-card border-2 border-car-border text-car-accent font-black text-[16.5px] cursor-pointer hover:border-car-accent shadow-sm"
          >
            绑定此文件路径
          </button>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-between w-full">
        <button 
          @click="resetToDefault"
          class="h-[56px] px-6 rounded-2xl bg-car-item border-2 border-car-border text-rose-400 hover:text-rose-300 font-black text-[17px] cursor-pointer hover:border-rose-400/50 shadow-sm transition-all"
        >
          恢复出厂默认 (晓晓知性原声)
        </button>
        <button 
          @click="closeModal('voiceItemSettings')"
          class="h-[56px] px-10 bg-car-item border-2 border-car-accent text-car-text font-black text-[18.5px] rounded-2xl cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 shadow-md"
        >
          完成并关闭
        </button>
      </div>
    </template>
  </ModalWrapper>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import ModalWrapper from './ModalWrapper.vue';
import { store, bridge, closeModal, showToast } from '../../store';

const targetItem = computed(() => store.modals.voiceItemSettings);
const customText = ref('');
const customFilePath = ref('');

watch(() => store.modals.voiceItemSettings, (item) => {
  if (item && item.key) {
    customText.value = localStorage.getItem(`geely_voice_text_${item.key}`) || '';
    customFilePath.value = localStorage.getItem(`geely_voice_file_${item.key}`) || '';
  } else {
    customText.value = '';
    customFilePath.value = '';
  }
});

const activeVoiceTypeLabel = computed(() => {
  if (customText.value.trim()) return '自定义台词 TTS';
  if (customFilePath.value.trim()) return '自定义本地音频文件';
  return '出厂默认原声 (晓晓温婉知性原声)';
});

const activeVoiceDesc = computed(() => {
  if (customText.value.trim()) return `台词: “${customText.value.trim()}” (小爱语音合成)`;
  if (customFilePath.value.trim()) return `文件: ${customFilePath.value.trim()}`;
  return '吉利智驾内置官方精调晓晓知性女声';
});

function testCurrentAudio() {
  if (targetItem.value) {
    bridge.call('testVehicleVoice', targetItem.value.key);
  }
}

function testTtsText() {
  if (!customText.value.trim()) {
    showToast('请先输入自定义台词');
    return;
  }
  showToast('正在试听自定义台词...');
  bridge.call('testVehicleVoiceText', customText.value.trim());
}

function saveCustomText() {
  if (!targetItem.value) return;
  const key = targetItem.value.key;
  localStorage.setItem(`geely_voice_text_${key}`, customText.value.trim());
  bridge.call('setVehicleAutomationStringSetting', `custom_voice_text_${key}`, customText.value.trim());
  bridge.call('setVehicleAutomationStringSetting', `custom_text_${targetItem.value.soundFile || key + '.mp3'}`, customText.value.trim());
  showToast('自定义台词已保存生效');
}

function testAudioFile() {
  if (!customFilePath.value.trim()) {
    showToast('请先输入音频文件路径');
    return;
  }
  showToast('正在试听音频文件...');
  bridge.call('playCustomAudioPath', customFilePath.value.trim());
}

function saveAudioFilePath() {
  if (!targetItem.value) return;
  const key = targetItem.value.key;
  localStorage.setItem(`geely_voice_file_${key}`, customFilePath.value.trim());
  bridge.call('setVehicleAutomationStringSetting', `custom_voice_${targetItem.value.soundFile || key + '.mp3'}`, customFilePath.value.trim());
  showToast('自定义音频文件路径已绑定');
}

function resetToDefault() {
  if (!targetItem.value) return;
  const key = targetItem.value.key;
  customText.value = '';
  customFilePath.value = '';
  localStorage.removeItem(`geely_voice_text_${key}`);
  localStorage.removeItem(`geely_voice_file_${key}`);
  bridge.call('resetCustomVoice', key);
  showToast('已恢复为出厂默认晓晓温婉知性原声');
}
</script>
