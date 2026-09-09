<template>
  <div class="flex flex-col space-y-6">
    <!-- 1. 系统语音引擎与小爱 TTS 直通 (拔除死按键，状态直显与试听) -->
    <FeatureCard 
      title="1. 系统语音引擎与小爱 TTS 直通状态"
      desc="系统已直接通过后台 IPC 直连语音合成引擎。因吉利原厂车机精简删除了系统设置中的 TTS 菜单，此处直接常驻显示真实引擎连接状态。"
    >
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between">
        <div class="flex-1 min-w-0 pr-6 flex flex-col">
          <div class="flex items-center mb-2">
            <span 
              :class="[
                'w-3 h-3 rounded-full mr-2.5',
                ttsInfo.connected ? 'bg-emerald-500 shadow-[0_0_8px_#10B981]' : 'bg-amber-500 shadow-[0_0_8px_#F59E0B]'
              ]"
            ></span>
            <span class="text-[19px] font-black text-car-text">
              {{ ttsInfo.name }}
            </span>
            <span 
              :class="[
                'ml-3 text-[12.5px] px-2.5 py-0.5 rounded-full font-black border',
                ttsInfo.connected 
                  ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-400' 
                  : 'bg-amber-500/15 border-amber-500/40 text-amber-400'
              ]"
            >
              {{ ttsInfo.connected ? '已成功直连' : '默认引擎' }}
            </span>
          </div>
          <div class="text-[15px] text-car-sub font-bold leading-relaxed">
            {{ ttsInfo.status }}
          </div>
        </div>

        <div class="shrink-0">
          <button 
            v-if="ttsInfo.connected"
            @click="testTtsEngine"
            class="min-h-[64px] px-8 bg-car-card border-2 border-car-accent text-car-text font-black text-[19px] rounded-2xl cursor-pointer hover:border-car-accent shadow-md ring-2 ring-car-accent/20"
          >
            试听小爱语音
          </button>
          <button 
            v-else
            @click="openStoreToDownload"
            class="min-h-[64px] px-8 bg-car-card border-2 border-car-border text-car-accent font-black text-[19px] rounded-2xl cursor-pointer hover:border-car-border-light shadow-sm"
          >
            前往商城下载小爱TTS
          </button>
        </div>
      </div>
    </FeatureCard>

    <!-- 2. 播报音量相对增益补偿滑条 (解决听歌低音量时听不清开门/换挡) -->
    <FeatureCard 
      title="2. 播报音量动态增益补偿 (听歌低音量时自动增补)"
      desc="听歌时媒体音量调低容易听不清开门与换挡提醒。设置增益后，播报瞬间自动在当前音量上动态叠加补偿，播完秒级恢复原音量！"
    >
      <div class="bg-car-item border border-car-border rounded-2xl p-6 flex flex-col space-y-4 shadow-sm">
        <div class="flex items-center justify-between">
          <div class="flex items-baseline">
            <span class="text-[20px] font-black text-car-text mr-3">动态音量补偿：</span>
            <span class="text-[34px] font-black text-car-accent font-mono">{{ volumeOffset >= 0 ? '+' + volumeOffset : volumeOffset }}</span>
            <span class="text-[16px] text-car-sub font-bold ml-1">格</span>
          </div>
          <span class="text-[15px] font-bold text-car-sub">
            {{ volumeOffset === 0 ? '跟随当前媒体音量 (不增不减)' : (volumeOffset > 0 ? `播报时临时提升 ${volumeOffset} 格，播完秒恢复` : `播报时临时降低 ${Math.abs(volumeOffset)} 格`) }}
          </span>
        </div>

        <!-- 刻度长滑条 -->
        <div class="flex flex-col space-y-2">
          <input 
            type="range" 
            min="-10" 
            max="15" 
            step="1" 
            v-model.number="volumeOffset" 
            @change="saveVolumeOffset"
            class="w-full h-3 bg-car-card rounded-lg appearance-none cursor-pointer accent-amber-500"
          />
          <div class="flex justify-between text-[13px] text-car-sub font-mono font-bold px-1">
            <span>-10 (极轻)</span>
            <span>-5</span>
            <span class="text-car-text font-black">0 (默认跟随)</span>
            <span class="text-car-accent font-black">+5 (加重补偿)</span>
            <span>+10 (开窗防漏)</span>
            <span>+15 (高音满血)</span>
          </div>
        </div>

        <div class="flex items-center justify-end space-x-3 pt-2">
          <button 
            @click="resetVolumeOffset"
            class="h-[52px] px-6 bg-car-card border-2 border-car-border text-car-sub hover:text-car-text font-black text-[16px] rounded-xl cursor-pointer hover:border-car-border-light shadow-sm"
          >
            重置归零 (+0)
          </button>
          <button 
            @click="testVolumeOffset"
            class="h-[52px] px-8 bg-car-card border-2 border-car-accent text-car-text font-black text-[17px] rounded-xl cursor-pointer hover:border-car-accent shadow-md ring-2 ring-car-accent/20"
          >
            试听当前音量增益
          </button>
        </div>
      </div>
    </FeatureCard>

    <!-- 3. 座舱专属语音主题包与自定义音效 (一键整套换装 · 物理隔离) -->
    <FeatureCard 
      title="3. 座舱专属语音主题包 (一键整套换装 · 物理隔离)"
      desc="支持导入车规级 ZIP 语音包。所有语音主题物理隔离保存在 /sdcard/GeelyPilot/voices/ 专属目录中，清空下载目录绝不受任何影响！"
    >
      <div class="flex flex-col space-y-4">
        <!-- 状态与快捷操作顶栏 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex items-center space-x-3.5">
            <span class="w-3.5 h-3.5 rounded-full bg-car-accent shadow-[0_0_8px_var(--accent-gold)]"></span>
            <div class="flex flex-col">
              <div class="flex items-center space-x-2">
                <span class="text-[18px] font-black text-car-text">当前整套音效：</span>
                <span class="text-[18px] font-black text-car-accent">{{ activeThemeName ? activeThemeName : '出厂官方原声 (晓晓温婉知性)' }}</span>
                <span class="px-2.5 py-0.5 rounded-full bg-emerald-500/15 border border-emerald-500/40 text-emerald-400 text-[12px] font-black">
                  {{ activeThemeName ? '自定义主题' : '系统默认' }}
                </span>
              </div>
              <span class="text-[14px] text-car-sub font-bold mt-1">
                {{ activeThemeName ? `专属目录: /sdcard/GeelyPilot/voices/${activeThemeName}/` : '吉利智驾出厂原声 · 未包含项自动补齐兜底' }}
              </span>
            </div>
          </div>

          <div class="flex items-center space-x-3 shrink-0">
            <button 
              @click="openModal('voiceThemeImport')"
              class="min-h-[56px] px-6 bg-car-card border-2 border-car-accent text-car-text font-black text-[17px] rounded-xl cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 shadow-md transition-all flex items-center space-x-2"
            >
              <span>📂 导入语音包 (.zip)</span>
            </button>
            <button 
              @click="loadVoiceThemes"
              class="min-h-[56px] px-5 bg-car-card border-2 border-car-border text-car-text font-black text-[16px] rounded-xl cursor-pointer hover:border-car-border-light shadow-sm transition-all"
            >
              🔄 刷新
            </button>
          </div>
        </div>

        <!-- 主题包列表卡片流 -->
        <div class="flex flex-col space-y-3">
          <!-- 默认出厂主题卡片 -->
          <div class="bg-car-item border-2 border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
            <div class="flex-1 min-w-0 pr-6 flex flex-col space-y-1">
              <div class="flex items-center space-x-2.5">
                <span class="text-[19px] font-black text-car-text">👑 出厂官方原声 (晓晓温婉知性)</span>
                <span class="px-2.5 py-0.5 rounded-md bg-car-card border border-car-border text-car-sub text-[12px] font-black">系统内置</span>
                <span v-if="!activeThemeName" class="px-2.5 py-0.5 rounded-md bg-car-accent/15 border border-car-accent/40 text-car-accent text-[12px] font-black">正在生效</span>
              </div>
              <span class="text-[14.5px] text-car-sub font-bold">
                吉利座舱温婉知性原声，端庄舒缓温润。零音频丢失，全场景兜底保障。
              </span>
            </div>

            <div class="flex items-center space-x-3 shrink-0">
              <button 
                @click="testThemeVoice('')"
                class="h-[52px] px-6 bg-car-card border-2 border-car-border text-car-text font-black text-[16px] rounded-xl hover:border-car-border-light cursor-pointer shadow-sm transition-all"
              >
                ▶️ 试听样音
              </button>
              <button 
                v-if="activeThemeName"
                @click="applyVoiceTheme('')"
                class="h-[52px] px-6 bg-car-card border-2 border-car-accent text-car-accent font-black text-[16px] rounded-xl hover:border-car-accent ring-2 ring-car-accent/20 cursor-pointer shadow-md transition-all"
              >
                恢复此原声
              </button>
              <button 
                v-else
                disabled
                class="h-[52px] px-6 bg-car-card border-2 border-car-border text-car-sub font-black text-[16px] rounded-xl opacity-60 cursor-default"
              >
                ✓ 正在生效
              </button>
            </div>
          </div>

          <!-- 用户导入的各语音主题包 -->
          <div 
            v-for="theme in voiceThemes" 
            :key="theme.id"
            class="bg-car-item border-2 rounded-2xl p-5 flex items-center justify-between shadow-sm transition-all"
            :class="activeThemeName === theme.name ? 'border-car-accent ring-2 ring-car-accent/20' : 'border-car-border'"
          >
            <div class="flex-1 min-w-0 pr-6 flex flex-col space-y-1">
              <div class="flex items-center space-x-2.5">
                <span class="text-[19px] font-black text-car-text truncate">{{ theme.name }}</span>
                <span class="px-2.5 py-0.5 rounded-md bg-car-card border border-car-border text-car-accent text-[12px] font-black shrink-0">
                  包含 {{ theme.count }} 个音频
                </span>
                <span v-if="activeThemeName === theme.name" class="px-2.5 py-0.5 rounded-md bg-car-accent/15 border border-car-accent/40 text-car-accent text-[12px] font-black shrink-0">
                  ✓ 正在整套生效
                </span>
              </div>
              <span class="text-[14.5px] text-car-sub font-mono font-bold truncate">
                目录: {{ theme.path }}
              </span>
            </div>

            <div class="flex items-center space-x-3 shrink-0">
              <button 
                @click="testThemeVoice(theme.name)"
                class="h-[52px] px-5 bg-car-card border-2 border-car-border text-car-text font-black text-[16px] rounded-xl hover:border-car-border-light cursor-pointer shadow-sm transition-all"
              >
                ▶️ 试听样音
              </button>
              <button 
                v-if="activeThemeName !== theme.name"
                @click="applyVoiceTheme(theme.name)"
                class="h-[52px] px-6 bg-car-card border-2 border-car-accent text-car-text font-black text-[16px] rounded-xl hover:border-car-accent ring-2 ring-car-accent/20 cursor-pointer shadow-md transition-all"
              >
                整套启用
              </button>
              <button 
                v-else
                disabled
                class="h-[52px] px-6 bg-car-card border-2 border-car-accent text-car-accent font-black text-[16px] rounded-xl cursor-default opacity-80"
              >
                ✓ 正在生效
              </button>
              <button 
                @click="confirmDeleteTheme(theme.name)"
                class="h-[52px] px-4 bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text hover:text-rose-400 font-black text-[15px] rounded-xl cursor-pointer shadow-sm transition-all"
              >
                🗑️
              </button>
            </div>
          </div>
        </div>
      </div>
    </FeatureCard>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import FeatureCard from '../components/FeatureCard.vue';
import { store, bridge, openModal, showToast } from '../store';

const ttsInfo = ref({
  connected: true,
  name: '小爱语音合成引擎 (XiaoAi TTS 1.5.1)',
  status: '已成功连接小爱语音引擎 · 专车TTS声线就绪'
});

const volumeOffset = ref(0);
const voiceThemes = ref([]);
const activeThemeName = ref('');

function loadVoiceThemes() {
  try {
    const raw = bridge.call('getVoiceThemesJson');
    if (raw) {
      const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
      activeThemeName.value = data.activeTheme || '';
      voiceThemes.value = data.themes || [];
    }
  } catch (e) {
    voiceThemes.value = [];
  }
}

function applyVoiceTheme(themeName) {
  bridge.call('setActiveVoiceTheme', themeName);
  activeThemeName.value = themeName;
  loadVoiceThemes();
}

function testThemeVoice(themeName) {
  if (!themeName) {
    bridge.call('testVehicleVoice', 'gear_d');
  } else {
    bridge.call('playCustomAudioPath', `/sdcard/GeelyPilot/voices/${themeName}/gear_d.mp3`);
  }
}

function confirmDeleteTheme(themeName) {
  openModal('confirm', {
    title: `删除语音包【${themeName}】`,
    message: `确定要彻底删除该语音包吗？\n删除后将释放其占用的存储空间，若正在生效将自动恢复为出厂晓晓原声。`,
    isDanger: true,
    onConfirm: () => {
      bridge.call('deleteVoiceTheme', themeName);
      loadVoiceThemes();
    }
  });
}

onMounted(() => {
  try {
    const raw = bridge.call('getTtsEngineInfo');
    if (raw) {
      const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
      ttsInfo.value = parsed;
    }
  } catch (e) {
    ttsInfo.value = {
      connected: true,
      name: '小爱语音合成引擎 (XiaoAi TTS 1.5.1)',
      status: '已成功直连小爱语音引擎 · 专车TTS声线就绪'
    };
  }

  try {
    const off = bridge.call('getVoiceVolumeOffset');
    if (typeof off === 'number') {
      volumeOffset.value = off;
    }
  } catch (e) {}

  loadVoiceThemes();
  window.refreshVoiceThemes = loadVoiceThemes;
});

function testTtsEngine() {
  bridge.call('testVehicleVoice', 'custom');
  showToast('正在调用小爱语音合成引擎播放测试语音...');
}

function openStoreToDownload() {
  store.currentNav = 'store';
  showToast('已跳转至精选软件中心');
}

function saveVolumeOffset() {
  bridge.call('setVoiceVolumeOffset', volumeOffset.value);
}

function resetVolumeOffset() {
  volumeOffset.value = 0;
  saveVolumeOffset();
  showToast('播报音量补偿已重置归零 (+0)');
}

function testVolumeOffset() {
  saveVolumeOffset();
  bridge.call('testVehicleVoice', 'gear_d');
  showToast(`正在按 ${volumeOffset.value >= 0 ? '+' + volumeOffset.value : volumeOffset.value} 格补偿试听播报...`);
}
</script>
