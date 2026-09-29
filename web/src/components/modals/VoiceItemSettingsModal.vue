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
      <!-- 车规双子舱 (左：当前生效音源 / 右：本声效输出通道 · 左右并排极致精简) -->
      <div class="flex space-x-4 items-stretch">
        <!-- 左舱: 当前生效音源 -->
        <div class="flex-1 min-w-0 bg-car-item border border-car-border rounded-2xl p-4 flex flex-col justify-between shadow-sm">
          <div class="flex flex-col space-y-1">
            <div class="flex items-center min-w-0">
              <span class="w-2.5 h-2.5 rounded-full bg-car-accent mr-2.5 shrink-0 shadow-[0_0_8px_var(--accent-gold)]"></span>
              <span class="text-[16px] font-black text-car-text truncate">当前音源：{{ activeVoiceTypeLabel }}</span>
            </div>
            <span class="text-[13px] text-car-sub font-bold truncate pl-5">{{ activeVoiceDesc }}</span>
          </div>

          <!-- 副驾项专属 3 角色切键 -->
          <div v-if="isFrDoorItem" class="flex space-x-2 pt-2.5">
            <button
              v-for="opt in [
                { role: 'female', name: '原车' },
                { role: 'princess', name: '公主' },
                { role: 'queen', name: '女王' }
              ]"
              :key="opt.role"
              @click="selectFrEntityRole(opt.role)"
              :class="[
                'flex-1 h-[50px] rounded-xl border-2 flex items-center justify-center cursor-pointer transition-all',
                isRoleActive(opt.role)
                  ? 'bg-car-card border-car-accent text-car-accent font-black shadow-md'
                  : 'bg-car-card border-car-border text-car-sub hover:text-car-text font-bold'
              ]"
            >
              <span class="text-[13.5px] whitespace-nowrap">{{ opt.name }}</span>
            </button>
          </div>
        </div>

        <!-- 右舱: 本声效输出通道 (独立声道 · 契约保留) -->
        <div class="flex-1 min-w-0 bg-car-item border border-car-border rounded-2xl p-4 flex flex-col justify-between shadow-sm">
          <div class="flex items-center justify-between">
            <div class="flex items-center space-x-2 min-w-0 pr-2">
              <span class="w-2.5 h-2.5 rounded-full bg-car-accent shadow-sm shrink-0"></span>
              <span class="text-[16px] font-black text-car-text whitespace-nowrap">本声效输出通道</span>
            </div>
            <div class="flex space-x-1.5 shrink-0">
              <button
                v-for="ch in channelOptions"
                :key="ch.value"
                @click="setItemChannel(ch.value)"
                class="px-3 h-[50px] rounded-xl border-2 font-black text-[13.5px] cursor-pointer transition-all shadow-sm whitespace-nowrap"
                :class="itemChannel === ch.value ? 'bg-car-card border-car-accent text-car-accent shadow-md' : 'bg-car-card border-car-border text-car-sub hover:text-car-text'"
              >
                {{ ch.label }}
              </button>
            </div>
          </div>
          <div class="text-[12.5px] text-car-sub font-bold truncate pt-2">
            {{ channelHint }}
          </div>
        </div>
      </div>

      <!-- 自定义本地音频文件 (车规免打字点选 · 自动隔离生命周期) -->
      <div class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col shadow-sm space-y-3">
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-2">
            <span class="w-2.5 h-2.5 rounded-full bg-car-accent shadow-sm shrink-0"></span>
            <span class="text-[17px] font-black text-car-text">绑定自定义本地音频</span>
          </div>
          <div class="flex items-center space-x-2">
            <button
              @click="loadDownloadAudioFiles"
              class="h-[50px] px-3.5 rounded-xl bg-car-card border border-car-border text-car-sub hover:text-car-text text-[13.5px] font-bold cursor-pointer transition-all"
            >
              刷新列表
            </button>
            <button
              @click="openModal('qrcode')"
              class="h-[50px] px-3.5 rounded-xl bg-car-card border border-car-accent/60 text-car-accent text-[13.5px] font-bold cursor-pointer transition-all"
            >
              手机扫码快传 ➔
            </button>
          </div>
        </div>

        <!-- 当前已绑定自定义提示条 -->
        <div v-if="customFilePath" class="p-3 rounded-xl bg-car-card border border-car-accent/50 flex items-center justify-between shadow-sm">
          <div class="flex items-center space-x-2 min-w-0 pr-3">
            <span class="text-[13px] text-car-accent font-black shrink-0">已绑定音频:</span>
            <span class="text-[13.5px] text-car-text font-bold truncate">{{ customFileName || customFilePath }}</span>
          </div>
          <div class="flex items-center space-x-2 shrink-0">
            <button
              @click="testCurrentAudio"
              class="h-[50px] px-3.5 rounded-xl bg-car-item border border-car-border text-car-text text-[13.5px] font-black cursor-pointer hover:border-car-border-light shadow-sm"
            >
              试听当前
            </button>
            <button
              @click="resetToDefault"
              class="h-[50px] px-3.5 rounded-xl bg-car-item border border-car-border text-car-sub hover:text-car-accent text-[13.5px] font-bold cursor-pointer shadow-sm"
            >
              解绑还原
            </button>
          </div>
        </div>

        <!-- 下载目录音频列表 (从 /sdcard/Download/车载语音自定义 免打字点选) -->
        <div class="flex flex-col space-y-2">
          <div class="text-[13px] text-car-sub font-bold flex items-center justify-between">
            <span>专属目录素材池 (受白名单保护，清理下载目录不丢失，解绑可找回)：</span>
            <span class="font-mono text-[12px] text-car-sub/80">{{ downloadAudioFiles.length }} 个文件</span>
          </div>

          <!-- 空状态 -->
          <div v-if="downloadAudioFiles.length === 0" class="py-5 px-4 rounded-xl bg-car-card border border-car-border/60 flex flex-col items-center justify-center space-y-2 text-center">
            <span class="text-[13.5px] text-car-sub font-bold">专属受保护目录 (/sdcard/Download/车载语音自定义) 暂无 MP3/WAV 音频</span>
            <span class="text-[12px] text-car-sub/80">点击右上角「手机扫码快传」连接车机 Wi-Fi 秒传，永久受白名单保护不误删</span>
          </div>

          <!-- 列表平铺 -->
          <div v-else class="max-h-[175px] overflow-y-auto space-y-2 pr-1">
            <div
              v-for="f in downloadAudioFiles"
              :key="f.name"
              class="p-2.5 rounded-xl bg-car-card border border-car-border flex items-center justify-between shadow-sm hover:border-car-border-light transition-all"
            >
              <div class="flex flex-col min-w-0 pr-3 space-y-0.5">
                <div class="flex items-center space-x-2">
                  <span class="text-[14px] font-black text-car-text truncate">{{ f.name }}</span>
                  <span v-if="f.dir === '车载语音自定义'" class="px-1.5 py-0.5 rounded bg-car-item border border-car-accent/40 text-car-accent text-[11px] font-bold shrink-0">受保护目录</span>
                </div>
                <span class="text-[12px] text-car-sub font-mono">{{ f.size }} · {{ f.time }}</span>
              </div>
              <div class="flex items-center space-x-2 shrink-0">
                <button
                  @click="testDownloadAudio(f.path)"
                  class="h-[50px] px-3 rounded-xl bg-car-item border border-car-border text-car-text font-bold text-[13.5px] cursor-pointer hover:border-car-border-light shadow-sm"
                >
                  试听
                </button>
                <button
                  @click="importAndBindDownloadAudio(f.name)"
                  class="h-[50px] px-3.5 rounded-xl bg-car-item border-2 border-car-accent text-car-accent font-black text-[13.5px] cursor-pointer hover:border-car-accent shadow-sm"
                >
                  设为本声效
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 快速从已安装语音包中点选混搭 -->
        <div v-if="installedThemes.length > 0" class="flex flex-col space-y-2 pt-4 border-t border-car-border/40">
          <span class="text-[13px] text-car-sub font-bold">或从已导入音效包混搭选择：</span>
          <div class="flex flex-wrap space-x-2">
            <button
              v-for="t in installedThemes"
              :key="t.name"
              @click="selectThemeSound(t.name)"
              class="h-[50px] px-3.5 rounded-xl bg-car-card border border-car-border text-car-text text-[13.5px] font-bold hover:border-car-accent cursor-pointer shadow-sm transition-all"
            >
              {{ t.name }}
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
const customFilePath = ref('');
const customFileName = ref('');
const downloadAudioFiles = ref([]);
const loadingDownload = ref(false);
const installedThemes = ref([]);
const itemChannel = ref('music');

function loadDownloadAudioFiles() {
  loadingDownload.value = true;
  try {
    const res = bridge.call('scanAudioFilesInDownload');
    downloadAudioFiles.value = JSON.parse(res || '[]');
  } catch (e) {
    console.error('scanAudioFilesInDownload error:', e);
    downloadAudioFiles.value = [];
  } finally {
    loadingDownload.value = false;
  }
}

function testDownloadAudio(path) {
  if (!path) return;
  showToast('正在试听下载目录音频...');
  bridge.call('playCustomAudioPath', path);
}

function importAndBindDownloadAudio(fileName) {
  if (!targetItem.value) return;
  const key = targetItem.value.key;
  showToast(`正在安全导入「${fileName}」...`);
  try {
    const raw = bridge.call('importCustomVoiceFile', key, fileName);
    const res = JSON.parse(raw || '{}');
    if (res.success && res.path) {
      customFilePath.value = res.path;
      customFileName.value = res.originalName || fileName;
      localStorage.setItem(`geely_voice_file_${key}`, res.path);
      localStorage.setItem(`geely_voice_name_${key}`, customFileName.value);
      showToast(`已成功将「${fileName}」绑定为本声效！`);
    } else {
      showToast(`导入绑定失败: ${res.error || '未知原因'}`, 'error');
    }
  } catch (e) {
    showToast(`导入异常: ${e.message}`, 'error');
  }
}

const channelOptions = [
  { value: 'music', label: '普通媒体' },
  { value: 'nav', label: '导航引导' },
  { value: 'notification', label: '系统提示' }
];

const isFrDoorItem = computed(() => {
  return (targetItem.value?.key || '').startsWith('door_fr');
});

function isRoleActive(role) {
  if (customFilePath.value.trim()) return false;
  const currentRole = store.vehicleAuto.passenger_voice_role || 'female';
  return currentRole === role;
}

function selectFrEntityRole(role) {
  store.vehicleAuto.passenger_voice_role = role;
  bridge.call('setVehicleAutomationSetting', 'passenger_voice_role', role);
  // 清空针对该项的自定义路径覆盖，使出厂实体直接生效
  customFilePath.value = '';
  resetToDefault();
  showToast(`已切回出厂【${role === 'queen' ? '女王语音' : (role === 'female' ? '原车语音' : '公主语音')}】`);
  if (targetItem.value) {
    bridge.call('testVehicleVoice', targetItem.value.key || 'door_fr');
  }
}

const channelHint = computed(() => {
  if (itemChannel.value === 'nav') return '跟随导航音量，与媒体音量独立调节';
  if (itemChannel.value === 'notification') return '跟随系统警报/雷达音量，不受媒体静音影响';
  return '跟随车机主媒体音量 (听歌通道)，默认推荐';
});

function setItemChannel(ch) {
  if (!targetItem.value) return;
  itemChannel.value = ch;
  store.vehicleAuto[`channel_${targetItem.value.key}`] = ch;
  bridge.call('setVoiceItemChannel', targetItem.value.key, ch);
  showToast(ch === 'music' ? '已恢复普通媒体声道' : `已切至${ch === 'nav' ? '导航引导' : '系统提示'}声道`);
}

// v1.7.49: 已全面升级车规级满电平输出，增益接口下线保持空实现防报错与门禁对齐
function setVoiceItemOffset() {}

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
    const exact = th.audioFiles.find(f => f.toLowerCase() === soundFile.toLowerCase());
    if (exact) {
      finalName = exact;
    } else {
      const aliases = VOICE_ALIAS_MAP[baseKey] || [];
      let found = null;
      for (const alias of aliases) {
        found = th.audioFiles.find(f => {
          const base = f.includes('.') ? f.substring(0, f.lastIndexOf('.')) : f;
          return base === alias;
        });
        if (found) break;
      }
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
    customFilePath.value = localStorage.getItem(`geely_voice_file_${item.key}`) || '';
    customFileName.value = localStorage.getItem(`geely_voice_name_${item.key}`) || '';
    itemChannel.value = store.vehicleAuto[`channel_${item.key}`] || 'music';
    loadInstalledThemes();
    loadDownloadAudioFiles();
  } else {
    customFilePath.value = '';
    customFileName.value = '';
    itemChannel.value = 'music';
  }
});

const activeVoiceTypeLabel = computed(() => {
  if (customFilePath.value.trim()) return '自定义本地音频';
  if (isFrDoorItem.value) {
    const role = store.vehicleAuto.passenger_voice_role || 'female';
    if (role === 'queen') return '👑 女王专属 (绅士男声)';
    if (role === 'female') return '🚗 原车官方 (知性女声)';
    return '👸 公主专属 (温润男声)';
  }
  return '出厂官方原声 (晓晓)';
});

const activeVoiceDesc = computed(() => {
  if (customFilePath.value.trim()) {
    return customFileName.value ? `已绑定: ${customFileName.value}` : customFilePath.value.trim();
  }
  if (isFrDoorItem.value) {
    const role = store.vehicleAuto.passenger_voice_role || 'female';
    if (role === 'queen') return '端庄绅士男声 · 专属礼遇';
    if (role === 'female') return '吉利原车内置晓晓知性女声';
    return '温润自然男声 · 专属宠溺音色';
  }
  return '官方精调晓晓知性女声 · 舒缓温润';
});

function testCurrentAudio() {
  if (targetItem.value) {
    bridge.call('testVehicleVoice', targetItem.value.key);
  }
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
  customFilePath.value = '';
  customFileName.value = '';
  localStorage.removeItem(`geely_voice_text_${key}`);
  localStorage.removeItem(`geely_voice_file_${key}`);
  localStorage.removeItem(`geely_voice_name_${key}`);
  bridge.call('resetCustomVoice', key);
  bridge.call('setVehicleAutomationStringSetting', `custom_voice_${targetItem.value.soundFile || key + '.mp3'}`, '');
  bridge.call('setVehicleAutomationStringSetting', `custom_voice_${key}`, '');
  bridge.call('setVehicleAutomationStringSetting', `custom_voice_name_${key}`, '');
  if (key === 'door_fl') {
    bridge.call('setVehicleAutomationStringSetting', 'custom_voice_door_open.mp3', '');
    bridge.call('setVehicleAutomationStringSetting', 'custom_voice_door_open', '');
    bridge.call('setVehicleAutomationStringSetting', 'custom_voice_name_door_open', '');
  }
  showToast('已恢复为出厂默认晓晓温婉知性原声');
}
</script>
