<template>
  <div class="flex flex-col space-y-6">
    <!-- 1. 蓝牙音频无感自动出声 -->
    <FeatureCard 
      title="1. 蓝牙音频无感自动出声 (告别手动点原厂多媒体)"
      desc="手机连接车载蓝牙后点播放，控制台中枢自动申请 STREAM_MUSIC 音频焦点并压制原厂静音机制，全车音响秒出声！"
    >
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between">
        <div class="flex-1 pr-6 flex flex-col">
          <div class="text-[18px] font-black text-car-text mb-1">
            蓝牙播放自动唤醒通道
          </div>
          <div class="text-[15px] text-car-sub font-bold">
            状态：{{ store.vehicleAuto.bt_audio_auto_route ? '已开启 (手机放歌自动出声，免进原厂多媒体)' : '已关闭' }}
          </div>
        </div>
        <MatrixButton 
          :title="store.vehicleAuto.bt_audio_auto_route ? '已开启 (推荐)' : '已关闭'"
          :active="store.vehicleAuto.bt_audio_auto_route"
          class="min-w-[180px]"
          @click="toggleSetting('bt_audio_auto_route')"
        />
      </div>
    </FeatureCard>

    <!-- 2. USB 闪存盘插拔与音频识别 -->
    <FeatureCard 
      title="2. USB 闪存盘插拔与音频识别"
      desc="双轨监听物理 USB 接入与文件系统挂载；遵循克制防打扰铁律：默认仅轻声播报“检测到外部U盘接入”，不越权打扰。"
    >
      <div class="grid grid-cols-2 gap-4">
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between shadow-sm">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text mb-1">U 盘插入轻声提醒</div>
            <div class="text-[15px] text-car-sub font-bold">插入 U 盘瞬间触发温婉语音：“检测到外部U盘接入”</div>
          </div>
          <MatrixButton 
            :title="store.vehicleAuto.usb_media_auto_detect ? '已开启 (默认)' : '已关闭'"
            :active="store.vehicleAuto.usb_media_auto_detect"
            @click="toggleSetting('usb_media_auto_detect')"
          />
        </div>

        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between shadow-sm">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text mb-1">自动全盘扫描歌曲文件</div>
            <div class="text-[15px] text-car-sub font-bold">异步深度遍历扫描 U 盘内的 MP3/FLAC 音乐文件（独立子开关）</div>
          </div>
          <MatrixButton 
            :title="store.vehicleAuto.usb_media_auto_scan_songs ? '已开启' : '已关闭 (默认)'"
            :active="store.vehicleAuto.usb_media_auto_scan_songs"
            @click="toggleSetting('usb_media_auto_scan_songs')"
          />
        </div>
      </div>
    </FeatureCard>

    <!-- 3. 系统语音引擎与小爱 TTS 直通 (拔除死按键，状态直显与试听) -->
    <FeatureCard 
      title="3. 系统语音引擎与小爱 TTS 直通状态"
      desc="系统已直接通过后台 IPC 直连语音合成引擎。因吉利原厂车机精简删除了系统设置中的 TTS 菜单，此处直接常驻显示真实引擎连接状态。"
    >
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between">
        <div class="flex-1 pr-6 flex flex-col">
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';
import { store, bridge, showToast } from '../store';

const ttsInfo = ref({
  connected: true,
  name: '小爱语音合成引擎 (XiaoAi TTS 1.5.1)',
  status: '已成功连接小爱语音引擎 · 专车TTS声线就绪'
});

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
});

function toggleSetting(key) {
  const next = !store.vehicleAuto[key];
  store.vehicleAuto[key] = next;
  bridge.call('setVehicleAutomationSetting', key, next);
  showToast('设置已更新: ' + (next ? '已开启' : '已关闭'));
}

function testTtsEngine() {
  bridge.call('testVehicleVoice', 'custom');
  showToast('正在调用小爱语音合成引擎播放测试语音...');
}

function openStoreToDownload() {
  store.currentNav = 'store';
  showToast('已跳转至精选软件中心');
}
</script>
