<template>
  <div class="flex flex-col gap-5">
    <!-- 蓝牙音频自动接管 -->
    <FeatureCard 
      title="1. 蓝牙音频无感自动出声 (告别手动点原厂多媒体)"
      desc="手机连接车载蓝牙后点播放，控制台中枢自动申请 STREAM_MUSIC 音频焦点并压制原厂静音机制，全车音响秒出声！"
    >
      <div class="flex items-center justify-between">
        <div class="text-[17px] text-[#94A3B8] font-bold">
          状态：{{ store.vehicleAuto.bt_audio_auto_route ? '已开启 (手机放歌自动出声)' : '已关闭' }}
        </div>
        <MatrixButton 
          :title="store.vehicleAuto.bt_audio_auto_route ? '已开启 (推荐)' : '已关闭'"
          :active="store.vehicleAuto.bt_audio_auto_route"
          class="min-w-[180px]"
          @click="toggleSetting('bt_audio_auto_route')"
        />
      </div>
    </FeatureCard>

    <!-- USB 闪存盘插拔与音频识别 -->
    <FeatureCard 
      title="2. USB 闪存盘插拔与音频识别 (实验性功能·实车测试)"
      desc="遵循克制与隐私原则：默认仅轻声播报 U 盘接入；曲目全盘扫描做成独立开关，默认关闭，绝不强行越权搜歌。"
    >
      <div class="grid grid-cols-2 gap-4">
        <div class="bg-[#262A33] border border-white/10 rounded-xl p-4 flex flex-col justify-between gap-3">
          <div>
            <div class="text-[19px] font-black text-white">U 盘插入轻声提醒</div>
            <div class="text-[15px] text-[#94A3B8] mt-1 font-bold">插入 U 盘瞬间触发温婉语音：“检测到外部U盘接入”</div>
          </div>
          <MatrixButton 
            :title="store.vehicleAuto.usb_media_auto_detect ? '已开启 (默认)' : '已关闭'"
            :active="store.vehicleAuto.usb_media_auto_detect"
            @click="toggleSetting('usb_media_auto_detect')"
          />
        </div>

        <div class="bg-[#262A33] border border-white/10 rounded-xl p-4 flex flex-col justify-between gap-3">
          <div>
            <div class="text-[19px] font-black text-white">自动全盘扫描歌曲文件</div>
            <div class="text-[15px] text-[#94A3B8] mt-1 font-bold">异步深度遍历扫描 U 盘内的 MP3/FLAC 音乐文件（实验性）</div>
          </div>
          <MatrixButton 
            :title="store.vehicleAuto.usb_media_auto_scan_songs ? '已开启' : '已关闭 (默认)'"
            :active="store.vehicleAuto.usb_media_auto_scan_songs"
            @click="toggleSetting('usb_media_auto_scan_songs')"
          />
        </div>
      </div>
    </FeatureCard>

    <!-- 系统语音引擎与小爱 TTS 直通 -->
    <FeatureCard 
      title="3. 系统语音引擎与小爱 TTS 直通"
      desc="已上架《系统语音引擎 (小爱TTS·专车专用)》，支持进入小爱设置界面微调语速、音调或下载离线个性语音包。"
    >
      <div class="flex items-center justify-end">
        <button 
          @click="openTtsSettings"
          class="min-h-[64px] px-8 bg-[#2A2315] border-2 border-[#F59E0B] text-[#FEF3C7] font-black text-[20px] rounded-xl cursor-pointer hover:bg-[#382E1C] shadow-md shadow-amber-950/50"
        >
          进入小爱设置
        </button>
      </div>
    </FeatureCard>
  </div>
</template>

<script setup>
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';
import { store, bridge, showToast } from '../store';

function toggleSetting(key) {
  const next = !store.vehicleAuto[key];
  store.vehicleAuto[key] = next;
  bridge.call('setVehicleAutomationSetting', key, next);
  showToast('设置已更新: ' + (next ? '已开启' : '已关闭'));
}

function openTtsSettings() {
  bridge.call('openTtsSettings');
  showToast('正在调起小爱语音设置...');
}
</script>
