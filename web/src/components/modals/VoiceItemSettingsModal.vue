<template>
  <ModalWrapper 
    :show="!!store.modals.voiceItemSettings" 
    :title="`${targetItem?.title || '声效'} · 专车声效个性化设置`" 
    badge="声效配置"
    maxWidthClass="max-w-[1020px]"
    zIndexClass="z-[9999]"
    @close="closeModal('voiceItemSettings')"
  >
    <div v-if="targetItem" class="flex flex-col space-y-4">
      <!-- 当前生效音源状态大卡片 -->
      <div class="bg-car-item border border-car-border rounded-2xl p-4 flex items-center justify-between shadow-sm">
        <div class="flex items-center">
          <span class="w-3 h-3 rounded-full bg-car-accent mr-3 shadow-[0_0_8px_var(--accent-gold)]"></span>
          <div class="flex flex-col">
            <span class="text-[17.5px] font-black text-car-text">当前生效音源：{{ activeVoiceTypeLabel }}</span>
            <span class="text-[14px] text-car-sub font-bold mt-0.5">{{ activeVoiceDesc }}</span>
          </div>
        </div>

        <button 
          @click="testCurrentAudio"
          class="min-h-[50px] px-6 bg-car-card border-2 border-car-accent text-car-text font-black text-[16px] rounded-xl cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 shadow-md shrink-0"
        >
          试听生效语音
        </button>
      </div>

      <!-- 本声效输出：独立声道 + 音量增益 (每声效独立管理 · 增益0=原厂音量) -->
      <div class="bg-car-item border-2 border-car-accent/40 rounded-2xl p-4 flex flex-col space-y-2.5 shadow-md">
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-2">
            <span class="w-2.5 h-2.5 rounded-full bg-car-accent shadow-sm"></span>
            <span class="text-[17.5px] font-black text-car-text">本声效输出 (声道 · 音量增益)</span>
          </div>
          <div class="flex space-x-2">
            <button
              v-for="ch in channelOptions"
              :key="ch.value"
              @click="setItemChannel(ch.value)"
              class="px-4 py-2 rounded-xl border-2 font-black text-[14px] cursor-pointer transition-all shadow-sm"
              :class="itemChannel === ch.value ? 'bg-car-accent border-car-accent text-black' : 'bg-car-card border-car-border text-car-sub hover:text-car-text'"
            >
              {{ ch.label }}
            </button>
          </div>
        </div>
        <div class="text-[13.5px] text-car-sub font-bold leading-relaxed">
          {{ channelHint }}
        </div>
        <div class="flex items-center space-x-4 pt-1">
          <span class="text-[13px] text-car-sub font-bold whitespace-nowrap">-15 (接近静音)</span>
          <input
            type="range"
            min="-15"
            max="15"
            step="1"
            v-model.number="itemGain"
            @change="setItemGain"
            @input="setItemGain"
            class="flex-1 accent-car-accent h-2.5 bg-car-card rounded-lg cursor-pointer"
          />
          <span class="text-[13px] text-car-sub font-bold whitespace-nowrap">+15 (最大增益)</span>
          <button
            @click="testCurrentAudio"
            class="px-4 py-2 bg-car-card border-2 border-car-accent text-car-accent hover:text-car-text font-black text-[14.5px] rounded-xl cursor-pointer transition-all shrink-0 shadow-sm"
          >
            试听此音量
          </button>
        </div>
        <div class="flex items-center justify-between pt-0.5">
          <button
            @click="resetItemGain"
            class="px-4 py-1.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent cursor-pointer transition-all shadow-sm"
            title="点击归零"
          >
            <span class="text-[24px] font-black" :class="itemGain === 0 ? 'text-car-sub' : 'text-car-accent'">{{ itemGain > 0 ? '+' + itemGain : itemGain }}</span>
            <span class="text-[13px] font-bold text-car-sub ml-1.5">格 (点击归零)</span>
          </button>
          <span class="text-[13px] font-bold text-car-sub">{{ gainStateText }}</span>
        </div>
      </div>

      <!-- 左右两栏并排 (车规宽屏 1920x720 黄金自适应排版，全要素同屏直出不翻页) -->
      <div class="flex space-x-4">
        <!-- 1. 自定义 TTS 朗读台词 -->
        <div class="flex-1 bg-car-item border border-car-border rounded-2xl p-4 flex flex-col justify-between shadow-sm space-y-3">
          <div class="flex items-center justify-between">
            <div class="text-[17px] font-black text-car-text">1. 自定义台词 (TTS 语音合成)</div>
            <span class="text-[12.5px] text-car-sub font-bold">留空不使用</span>
          </div>
          <input
            v-model="customText"
            type="text"
            placeholder="例如: 已挂入前进挡，系好安全带出发啦！"
            class="w-full h-[54px] bg-car-card border-2 border-car-border rounded-xl px-3 text-car-text text-[15px] font-black outline-none focus:border-car-accent shadow-inner placeholder-car-sub"
          />

          <!-- 快捷台词套用 -->
          <div class="flex flex-col space-y-1.5">
            <span class="text-[13px] text-car-sub font-bold">快捷范例台词（点击一键填入）：</span>
            <div class="flex flex-wrap -mr-2 -mb-2">
              <button
                v-for="item in presetPhrases"
                :key="item.label"
                @click="onSelectPreset(item)"
                class="px-3 py-1.5 mr-2 mb-2 rounded-lg bg-car-card border border-car-border text-car-text text-[13px] font-bold hover:border-car-accent cursor-pointer shadow-sm transition-all"
                :title="item.text"
              >
                {{ item.label }}
              </button>
              <button
                @click="customText = ''"
                class="px-3 py-1.5 mr-2 mb-2 rounded-lg bg-car-card border border-car-border text-car-sub hover:text-car-text hover:border-car-border-light text-[13px] font-bold cursor-pointer shadow-sm transition-all"
              >
                清空台词
              </button>
            </div>
          </div>

          <div class="flex space-x-2.5">
            <button
              @click="testTtsText"
              class="flex-1 min-h-[50px] px-3 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[15.5px] cursor-pointer hover:border-car-border-light shadow-sm"
            >
              试听台词
            </button>
            <button
              @click="saveCustomText"
              class="flex-1 min-h-[50px] px-3 rounded-xl bg-car-card border-2 border-car-border text-car-accent font-black text-[15.5px] cursor-pointer hover:border-car-accent shadow-sm"
            >
              保存台词
            </button>
          </div>
        </div>

        <!-- 2. 自定义本地音频文件 -->
        <div class="flex-1 bg-car-item border border-car-border rounded-2xl p-4 flex flex-col justify-between shadow-sm space-y-3">
          <div class="flex items-center justify-between">
            <div class="text-[17px] font-black text-car-text">2. 自定义本地音频 (MP3/WAV)</div>
            <span class="text-[12.5px] text-car-sub font-bold">/sdcard/Music/</span>
          </div>
          <input
            v-model="customFilePath"
            type="text"
            placeholder="例如: /sdcard/Music/gear_d.mp3"
            class="w-full h-[54px] bg-car-card border-2 border-car-border rounded-xl px-3 text-car-text font-mono text-[14px] outline-none focus:border-car-accent shadow-inner placeholder-car-sub"
          />
          <div class="text-[12px] text-car-sub leading-relaxed">
            🎙️ 规格要求：音频开头请保留 ≥280ms 静音（车机功放建立通道需 0.2~0.3 秒，零静音直录会吞掉第一个字）。ffmpeg 一条命令补齐：<span class="font-mono">ffmpeg -i in.mp3 -af "adelay=280" out.mp3</span>，详见语音包模板 README。
          </div>

          <!-- 快速从已安装语音包中点选混搭 -->
          <div v-if="installedThemes.length > 0" class="flex flex-col space-y-1.5">
            <span class="text-[13px] text-car-sub font-bold">快速从已导入语音包选取此音效：</span>
            <div class="flex flex-wrap space-x-2">
              <button
                v-for="t in installedThemes"
                :key="t.name"
                @click="selectThemeSound(t.name)"
                class="px-3 py-1.5 rounded-lg bg-car-card border border-car-border text-car-text text-[13px] font-bold hover:border-car-accent cursor-pointer mb-1 shadow-sm transition-all"
              >
                {{ t.name }}
              </button>
            </div>
          </div>

          <div class="flex space-x-2.5">
            <button 
              @click="testAudioFile"
              class="flex-1 min-h-[50px] px-3 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[15.5px] cursor-pointer hover:border-car-border-light shadow-sm"
            >
              试听音频文件
            </button>
            <button 
              @click="saveAudioFilePath"
              class="flex-1 min-h-[50px] px-3 rounded-xl bg-car-card border-2 border-car-border text-car-accent font-black text-[15.5px] cursor-pointer hover:border-car-accent shadow-sm"
            >
              绑定文件
            </button>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-between w-full">
        <button 
          @click="resetToDefault"
          class="h-[56px] px-6 rounded-2xl bg-car-item border-2 border-car-border text-car-text hover:text-car-accent font-black text-[17px] cursor-pointer hover:border-car-border-light shadow-sm transition-all flex items-center"
        >
          恢复出厂原声
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
const installedThemes = ref([]);
const itemChannel = ref('music');
const itemGain = ref(0);

const channelOptions = [
  { value: 'music', label: '普通媒体' },
  { value: 'nav', label: '导航引导' },
  { value: 'notification', label: '系统提示' }
];

const channelHint = computed(() => {
  if (itemChannel.value === 'nav') return '导航引导：走导航音量流，可与媒体音量分开调。';
  if (itemChannel.value === 'notification') return '系统提示：走通知音量流（倒车挡慎选，倒车雷达通道与其互斥可能滞后爆音）。';
  return '普通媒体：跟随车机主音量（听歌那条），默认推荐。';
});

const presetPhrases = computed(() => {
  if (!targetItem.value) return [];
  const key = targetItem.value.key || '';

  // 1. 副驾车门 (开/关专属台词)
  if (key === 'door_fr') {
    return [
      { label: '通用标准 (清爽男声)', text: '欢迎乘车', role: 'male' },
      { label: '通用标准 (知性女声)', text: '欢迎乘车', role: 'female' },
      { label: '公主专属 (温润男声)', text: '欢迎公主上车', role: 'princess' },
      { label: '女王专属 (绅士男声)', text: '恭迎女王殿下', role: 'queen' }
    ];
  }
  if (key === 'door_fr_close') {
    return [
      { label: '通用标准 (清爽男声)', text: '车门已关好，请系好安全带', role: 'male' },
      { label: '通用标准 (知性女声)', text: '车门已关好，请系好安全带', role: 'female' },
      { label: '公主专属 (温润男声)', text: '公主请系好安全带', role: 'princess' },
      { label: '女王专属 (绅士男声)', text: '女王殿下已就座，请系好安全带', role: 'queen' }
    ];
  }
  // 2. 主驾车门
  if (key === 'door_fl') {
    return [
      { label: '登车迎宾', text: '车主您好，欢迎回来' },
      { label: '离车提示', text: '请注意后方来车，带好随身物品' },
      { label: '防开门杀', text: '开门请注意后方来车' }
    ];
  }
  if (key === 'door_fl_close') {
    return [
      { label: '准备启程', text: '准备启程，请系好安全带' },
      { label: '离车锁车', text: '车门已关好，请记得锁车' },
      { label: '标准关好', text: '主驾车门已关好' }
    ];
  }
  // 3. 后排车门
  if (key === 'door_rl' || key === 'door_rr') {
    return [
      { label: '防开门杀', text: '后排车门打开，请注意后方来车' },
      { label: '后排开门', text: '后排车门已打开' }
    ];
  }
  if (key === 'door_rl_close' || key === 'door_rr_close') {
    return [
      { label: '后排关好', text: '后排车门已关好' },
      { label: '乘客就位', text: '后排乘客已就位，车门已关好' }
    ];
  }
  // 4. 挡位播报
  if (key === 'gear_d') {
    return [
      { label: '一路平安', text: '已挂入前进挡，系好安全带，祝您一路平安' },
      { label: '注意路况', text: '前进挡已就绪，注意观察周围路况' }
    ];
  }
  if (key === 'gear_r') {
    return [
      { label: '注意后方', text: '已挂入倒车挡，请注意观察后方安全' },
      { label: '倒车防撞', text: '倒车请注意后方行人和障碍物' }
    ];
  }
  if (key === 'gear_p') {
    return [
      { label: '随身物品', text: '已挂入驻车挡，请带好随身物品' },
      { label: '标准驻车', text: '已挂入驻车挡' }
    ];
  }
  // 5. 后备箱
  if (key === 'trunk_open') {
    return [
      { label: '拿取物品', text: '后备箱已打开，请注意拿取物品' },
      { label: '标准打开', text: '后备箱已打开' }
    ];
  }
  if (key === 'trunk_close') {
    return [
      { label: '确认锁好', text: '后备箱已关闭，请确认锁好' },
      { label: '标准关闭', text: '后备箱已关闭' }
    ];
  }
  // 6. 通用开闭
  if (key === 'door_open') {
    return [
      { label: '防开门杀', text: '请注意后方来车，带好随身物品' },
      { label: '车门打开', text: '车门已打开' }
    ];
  }
  if (key === 'door_close') {
    return [
      { label: '关好出发', text: '车门已关好，准备出发' },
      { label: '标准关好', text: '车门已关好' }
    ];
  }
  return [
    { label: '一路平安', text: '祝您一路顺风，平安出行' }
  ];
});

const gainStateText = computed(() => {
  if (itemGain.value === 0) return '增益 0 = 原厂音量，不增不减';
  if (itemGain.value > 0) return `播报瞬间叠加 +${itemGain.value} 格，播完自动还原`;
  return `播报瞬间压低 ${itemGain.value} 格 (太小可能听不清)`;
});

function setItemChannel(ch) {
  if (!targetItem.value) return;
  itemChannel.value = ch;
  store.vehicleAuto[`channel_${targetItem.value.key}`] = ch;
  bridge.call('setVoiceItemChannel', targetItem.value.key, ch);
  showToast(ch === 'music' ? '已恢复普通媒体声道' : `已切至${ch === 'nav' ? '导航引导' : '系统提示'}声道`);
}

function setItemGain() {
  if (!targetItem.value) return;
  if (typeof itemGain.value !== 'number') itemGain.value = 0;
  itemGain.value = Math.max(-15, Math.min(15, Math.round(itemGain.value)));
  store.vehicleAuto[`offset_${targetItem.value.key}`] = itemGain.value;
  bridge.call('setVoiceItemOffset', targetItem.value.key, itemGain.value);
}

function resetItemGain() {
  itemGain.value = 0;
  setItemGain();
  showToast('增益已归零，恢复原厂音量');
}

function loadInstalledThemes() {
  try {
    const raw = bridge.call('getVoiceThemesJson');
    if (raw) {
      const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
      installedThemes.value = data.themes || [];
    }
  } catch (e) {
    installedThemes.value = [];
  }
}

const VOICE_ALIAS_MAP = {
    door_fl: ['主驾车门开启', '主驾开门', '主驾驶开门', '主驾门开', '通用开门', '开门', '车门开启'],
    door_fl_close: ['主驾开门关闭', '主驾车门关闭', '主驾关门', '主驾驶关门', '关门', '车门关闭'],
    door_fr: ['副驾车门开启', '副驾开门', '副驾驶开门', '副驾门开'],
    door_fr_close: ['副驾车门关闭', '副驾关门', '副驾驶关门'],
    door_rl: ['左后车门开启', '左后开门', '左后门开'],
    door_rl_close: ['左后车门关闭', '左后关门'],
    door_rr: ['右后车门开启', '右后开门', '右后门开'],
    door_rr_close: ['右后车门关闭', '右后关门'],
    door_open: ['主驾车门开启', '开门', '车门开启', '车门打开', '主驾开门'],
    door_close: ['主驾车门关闭', '主驾开门关闭', '关门', '车门关闭'],
    gear_p: ['P挡', '动力已锁止【P】', '挂入P挡', '驻车挡'],
    gear_d: ['D挡', '前进挡【D】', '挂入D挡', '前进挡'],
    gear_r: ['R挡', '倒车挡注意安全【R】', '挂入R挡', '倒车挡', '倒挡'],
    gear_n: ['N挡', '当前空挡，注意溜车【N】', '挂入N挡', '空挡'],
    mode_comfort: ['舒适模式', '舒适'],
    mode_sport: ['运动模式', '运动'],
    mode_eco: ['经济模式', '经济'],
    mode_smart: ['智能模式', '智能'],
    start: ['车辆已启动系统自检正常【启动】', '启动', '点火', '欢迎乘坐量子号飞船【启动】'],
    stop: ['车辆已熄火下次再见【熄火】', '熄火', '下电'],
    trunk_open: ['后备箱开启', '尾门开启'],
    trunk_close: ['后备箱关闭', '尾门关闭']
  };

  function selectThemeSound(themeName) {
    if (!targetItem.value) return;
    const th = installedThemes.value.find(t => t.name === themeName);
    const baseKey = targetItem.value.key;
    const soundFile = targetItem.value.soundFile || (baseKey + '.mp3');
    let finalName = soundFile;

    if (th && th.audioFiles && th.audioFiles.length > 0) {
      // 1. 尝试英文原名
      const exact = th.audioFiles.find(f => f.toLowerCase() === soundFile.toLowerCase());
      if (exact) {
        finalName = exact;
      } else {
        // 2. 尝试别名字典精准匹配
        const aliases = VOICE_ALIAS_MAP[baseKey] || [];
        let found = null;
        for (const alias of aliases) {
          found = th.audioFiles.find(f => {
            const base = f.includes('.') ? f.substring(0, f.lastIndexOf('.')) : f;
            return base === alias;
          });
          if (found) break;
        }
        // 3. 尝试模糊包含别名
        if (!found) {
          for (const alias of aliases) {
            found = th.audioFiles.find(f => {
              const base = f.includes('.') ? f.substring(0, f.lastIndexOf('.')) : f;
              return base.includes(alias);
            });
            if (found) break;
          }
        }
        if (found) finalName = found;
      }
    }

    customFilePath.value = `/sdcard/GeelyPilot/voices/${themeName}/${finalName}`;
    saveAudioFilePath();
    showToast(`已快捷绑定【${themeName}】的 ${finalName}`);
  }

  watch(() => store.modals.voiceItemSettings, (item) => {
  if (item && item.key) {
    customText.value = localStorage.getItem(`geely_voice_text_${item.key}`) || '';
    customFilePath.value = localStorage.getItem(`geely_voice_file_${item.key}`) || '';
    itemChannel.value = store.vehicleAuto[`channel_${item.key}`] || 'music';
    itemGain.value = store.vehicleAuto[`offset_${item.key}`] !== undefined ? Number(store.vehicleAuto[`offset_${item.key}`]) : 0;
    loadInstalledThemes();
  } else {
    customText.value = '';
    customFilePath.value = '';
    itemChannel.value = 'music';
    itemGain.value = 0;
  }
});

const activeVoiceTypeLabel = computed(() => {
  if (customText.value.trim()) return '自定义台词 TTS';
  if (customFilePath.value.trim()) return '自定义本地音频文件';
  return '出厂默认原声 (晓晓温婉知性原声)';
});

const activeVoiceDesc = computed(() => {
  if (customText.value.trim()) return `台词: “${customText.value.trim()}” (TTS 语音合成)`;
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
    showToast('请先输入自定义台词', 'warn');
    return;
  }
  showToast('正在试听自定义台词...');
  bridge.call('testVehicleVoiceText', customText.value.trim());
}

function onSelectPreset(item) {
  customText.value = item.text || '';
  if (item.role) {
    bridge.call('setVehicleAutomationStringSetting', 'passenger_voice_role', item.role);
    localStorage.setItem('geely_passenger_voice_role', item.role);
    showToast(`已应用副驾专属角色：${item.label}`);
  }
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
    showToast('请先输入音频文件路径', 'warn');
    return;
  }
  showToast('正在试听音频文件...');
  bridge.call('playCustomAudioPath', customFilePath.value.trim());
}

function saveAudioFilePath() {
  if (!targetItem.value) return;
  const key = targetItem.value.key;
  const path = customFilePath.value.trim();
  localStorage.setItem(`geely_voice_file_${key}`, path);
  const soundFile = targetItem.value.soundFile || (key + '.mp3');
  // 双键兼容：带 .mp3 与不带后缀双写，保证底层所有查找逻辑均可直接命中
  bridge.call('setVehicleAutomationStringSetting', `custom_voice_${soundFile}`, path);
  bridge.call('setVehicleAutomationStringSetting', `custom_voice_${key}`, path);
  // 车门模式互通兜底：极简模式下触发 door_open，分门模式触发 door_fl，双向联动绑定
  if (key === 'door_fl') {
    bridge.call('setVehicleAutomationStringSetting', 'custom_voice_door_open.mp3', path);
    bridge.call('setVehicleAutomationStringSetting', 'custom_voice_door_open', path);
  } else if (key === 'door_open') {
    bridge.call('setVehicleAutomationStringSetting', 'custom_voice_door_fl.mp3', path);
    bridge.call('setVehicleAutomationStringSetting', 'custom_voice_door_fl', path);
  }
  showToast('自定义音频文件已成功绑定并生效');
}

function resetToDefault() {
  if (!targetItem.value) return;
  const key = targetItem.value.key;
  customText.value = '';
  customFilePath.value = '';
  localStorage.removeItem(`geely_voice_text_${key}`);
  localStorage.removeItem(`geely_voice_file_${key}`);
  bridge.call('resetCustomVoice', key);
  bridge.call('setVehicleAutomationStringSetting', `custom_voice_${targetItem.value.soundFile || key + '.mp3'}`, '');
  bridge.call('setVehicleAutomationStringSetting', `custom_voice_${key}`, '');
  if (key === 'door_fl') {
    bridge.call('setVehicleAutomationStringSetting', 'custom_voice_door_open.mp3', '');
    bridge.call('setVehicleAutomationStringSetting', 'custom_voice_door_open', '');
  }
  showToast('已恢复为出厂默认晓晓温婉知性原声');
}
</script>
