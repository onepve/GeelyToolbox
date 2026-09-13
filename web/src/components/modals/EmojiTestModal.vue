<template>
  <div 
    v-if="store.modals.emojiTest"
    class="fixed top-0 left-0 w-screen h-screen z-50 bg-[#0B0F19] flex flex-col p-3 select-none"
    style="position: fixed; top: 0; left: 0; right: 0; bottom: 0; width: 100vw; height: 100vh; background: #0B0F19;"
  >
    <!-- 极简顶栏 (零多余文字，纯数量与大关闭按钮) -->
    <div class="h-[56px] px-3 flex items-center justify-between shrink-0 border-b border-car-border/60 pb-2">
      <div class="flex items-center space-x-3">
        <span class="text-[20px] font-black text-car-text">车机 Emoji 实测</span>
        <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-amber-500/20 text-amber-400 font-mono font-black border border-amber-500/40">
          全量 {{ emojiList.length }} 个 (#001 ~ #{{ String(emojiList.length).padStart(3, '0') }})
        </span>
      </div>

      <button 
        @click="closeModal('emojiTest')"
        class="h-[50px] px-8 rounded-2xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[17.5px] cursor-pointer transition-all shadow-md active:scale-95"
      >
        关闭 ✕
      </button>
    </div>

    <!-- 18列全屏超高密度铺满：纯图标 + 紧凑编号，零中文说明，最大化拍照视野 -->
    <div class="flex-1 overflow-y-auto pt-2.5 pr-1">
      <div style="display: grid; grid-template-columns: repeat(18, minmax(0, 1fr)); gap: 6px;">
        <div 
          v-for="item in emojiList" 
          :key="item.id"
          class="h-[72px] rounded-xl bg-car-card border border-car-border/70 hover:border-car-accent flex flex-col items-center justify-center p-1 relative shadow-sm"
        >
          <!-- 紧凑编号 -->
          <span class="text-[10px] font-mono font-black text-amber-400/90 leading-none mb-1">
            #{{ item.id }}
          </span>
          <!-- 超大字号 Emoji 图标 -->
          <span class="text-[34px] leading-none select-all">
            {{ item.emoji }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { store, closeModal } from '../../store';

// 全场景精选 216 个候选 Emoji 字符 (零文字负担，纯高密平铺)
const rawEmojis = [
  // 车辆、出行与交通
  '🚗', '🚘', '🏎️', '🚙', '🛞', '⛽', '🔋', '🔌', '⚡', '🛣️', '🏁', '🚦', '🚥', '🚨', '🅿️', '🛑', '🎛️', '🚲',
  '🛵', '🚌', '🚚', '🚜', '🚁', '🛸', '🚂', '🚄', '✈️', '🛫', '🛬', '🛳️', '⛵', '⚓', '🚧', '🚏', '🏪', '🏨',
  
  // 座舱、硬件与部件
  '🚪', '🪟', '💺', '🔊', '🔉', '🔈', '🔇', '🔔', '🔕', '💡', '🔦', '🔑', '🗝️', '🧲', '🛡️', '⚙️', '🔧', '🔨',
  '🪛', '🧰', '🔒', '🔓', '🔐', '🔏', '🪞', '🪝', '🧯', '📡', '💾', '🏷️', '📦', '📎', '✂️', '📏', '📐', '📍',

  // 声音、音乐与播报
  '🎵', '🎶', '📻', '🎙️', '🎤', '🎧', '💿', '📼', '📢', '📣', '⏯️', '⏸️', '⏹️', '⏺️', '⏭️', '⏮️', '⏩', '⏪',
  '🔀', '🔁', '🔂', '🗣️', '🎼', '🎻', '🎹', '🥁', '🎸', '🎺', '🎷', '🎙', '🔊', '🔉', '🔇', '🔔', '🔕', '📣',

  // 导航、定位与地图
  '🧭', '🗺️', '📍', '🚩', '🛰️', '🚀', '🎯', '📌', '🏔️', '🏖️', '🏙️', '🌉', '🚇', '⛳', '🏕️', '⛰️', '⛽', '🅿️',

  // 状态、安全与指示
  '✅', '❌', '⭕', '⚠️', '⛔', '🚫', '🔴', '🟢', '🟡', '🔵', '🟣', '⚪', '⚫', '🔘', '💤', '⏰', '⏱️', '⏳',
  '⌛', '📊', '📈', '📉', '💡', '🔌', '🔆', '🔅', '💯', '💢', '♨️', '💠', '🔷', '🔶', '🔺', '🔻', '✨', '🌟',

  // 手势、方向与按键
  '👉', '👈', '👆', '👇', '☝️', '✌️', '🖐️', '✋', '👊', '👍', '👎', '👏', '🤝', '🔍', '🔎', '🔄', '🔃', '➡️',
  '⬅️', '⬆️', '⬇️', '↗️', '↘️', '↙️', '↖️', '↔️', '↕️', '↩️', '↪️', '⤴️', '⤵️', '🔙', '🔚', '🔛', '🔜', '🔝',

  // 界面、系统与工具
  '📱', '💻', '🖥️', '📺', '📷', '📹', '🗑️', '🧹', '📦', '📎', '📝', '📋', '📁', '📂', '📄', '📃', '🔮', '🕹️',
  '🪄', '🪟', '🧲', '📡', '💾', '🗂️', '📊', '📈', '📉', '📌', '🏷️', '🔒', '🔓', '🔑', '⚙️', '🔧', '🔨', '🧰',

  // 天气、氛围与生活
  '☀️', '🌤️', '⛅', '☁️', '🌧️', '⛈️', '❄️', '🌙', '🌡️', '🔥', '💥', '✨', '🌟', '⭐', '👑', '☕', '🥤', '🏆',
  '🥇', '🥈', '🥉', '💖', '🎨', '🌈', '🌪️', '🌫️', '💧', '🫧', '💨', '🍃', '🍂', '🍁', '🪵', '🪨', '🎋', '🎍'
];

// 严格去重并按自增三位数编号 (#001 ~ #NNN)
const uniqueEmojis = Array.from(new Set(rawEmojis));
const emojiList = uniqueEmojis.map((emoji, index) => ({
  id: String(index + 1).padStart(3, '0'),
  emoji
}));
</script>
