<template>
  <div class="flex flex-col gap-5">
    <div class="bg-amber-500/10 border border-amber-500/30 rounded-xl p-4 text-[16px] text-amber-200 font-bold">
      <b>【实测机型声明】</b>以下软件仅在「吉利缤越COOL 2022 (亿咖通 E02 / IHU516G / Android 9)」实测通过，其他车型请自行评估后安装。
    </div>

    <!-- 分类过滤与刷新 -->
    <div class="flex items-center justify-between">
      <div class="text-[22px] font-black text-white">精选车机应用列表</div>
      <div class="flex gap-2">
        <button 
          v-for="cat in categories" 
          :key="cat.id"
          @click="currentCategory = cat.id"
          :class="[
            'px-5 py-2.5 rounded-xl font-extrabold text-[16px] cursor-pointer transition-all',
            currentCategory === cat.id ? 'bg-white text-black font-black' : 'bg-[#262A33] text-white border border-white/10'
          ]"
        >
          {{ cat.name }}
        </button>
        <button 
          @click="refreshApps"
          class="px-5 py-2.5 rounded-xl bg-white/10 border border-white/20 text-white font-extrabold text-[16px] cursor-pointer hover:bg-white/20"
        >
          刷新
        </button>
      </div>
    </div>

    <!-- 应用流 -->
    <div class="grid grid-cols-2 gap-4">
      <div 
        v-for="app in filteredApps" 
        :key="app.id"
        class="bg-[#1C1F26] border border-white/10 rounded-2xl p-5 flex flex-col justify-between gap-4"
      >
        <div class="flex flex-col gap-1.5">
          <div class="flex items-center justify-between">
            <span class="text-[20px] font-black text-white">{{ app.name }}</span>
            <span class="text-[13px] bg-white/10 px-2.5 py-1 rounded text-[#94A3B8] font-bold">{{ app.size }}</span>
          </div>
          <p class="text-[15.5px] text-[#94A3B8] font-semibold leading-relaxed">{{ app.desc }}</p>
        </div>

        <button 
          @click="handleDownload(app)"
          class="min-h-[58px] bg-white border-2 border-white rounded-xl text-black font-black text-[18px] cursor-pointer hover:bg-white/90"
        >
          {{ app.statusText || '立即下载安装' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { store, bridge, showToast } from '../store';

const currentCategory = ref('all');
const categories = [
  { id: 'all', name: '全部' },
  { id: 'navigation', name: '车载导航' },
  { id: 'music', name: '音乐应用' },
  { id: 'tools', name: '车机工具' }
];

const mockApps = [
  {
    id: 'amap_ae86',
    category: 'navigation',
    name: '高德地图 8.5 (86改·Flyme地图/地库惯导)',
    size: '274.9 MB',
    desc: '纯 64 位原生编译，重构 EAS 5007 可信视觉锚点状态机，地库陀螺仪惯导，三指滑屏秒飞仪表盘。',
    filename: 'AutoMap_E02_DeadReckoning_FlyScreen_AE86.apk'
  },
  {
    id: 'xiaoai_tts',
    category: 'tools',
    name: '系统语音引擎 (小爱TTS·专车专用)',
    size: '29.96 MB',
    desc: '集成小米小爱语音合成引擎，解锁控制台座舱智能联动【改文字 TTS】实时朗读与音色自定义。',
    filename: 'XiaoAi_TTS_Engine_1.5.1.apk'
  },
  {
    id: 'carmedia',
    category: 'music',
    name: 'CarMedia 车机媒体 1.3.3 (稳定推荐版)',
    size: '15.4 MB',
    desc: '米小江出品，吉利车机专属方控媒体中心：方向盘物理按键切歌/播放暂停/自定义应用联动。',
    filename: 'CarMedia_1.3.3.apk'
  },
  {
    id: 'qqmusic_hd',
    category: 'music',
    name: 'QQ音乐车载版 2.5.3.1 (HD)',
    size: '34.2 MB',
    desc: '腾讯官方原版车机客户端，完美适配横屏宽屏车机，无弹窗纯净体验。',
    filename: 'QQMusic_2.5.3.1_HD.apk'
  }
];

const filteredApps = computed(() => {
  if (currentCategory.value === 'all') return mockApps;
  return mockApps.filter(a => a.category === currentCategory.value);
});

function refreshApps() {
  bridge.call('getApps');
  showToast('正在刷新应用列表...');
}

function handleDownload(app) {
  bridge.call('downloadApp', app.id, app.filename);
  showToast('开始下载: ' + app.name);
}
</script>
