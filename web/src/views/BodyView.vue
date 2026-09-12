<template>
  <div class="flex flex-col space-y-6">
    <!-- 1. 座舱车身语音播报总开关 (整卡一体化开关：整卡即触控大靶区 · 右侧纯文字) -->
    <div 
      @click="toggleVoiceMasterSwitch"
      :class="[
        'rounded-3xl border-2 p-6 shadow-xl transition-all duration-200 cursor-pointer flex items-center justify-between',
        store.vehicleAuto.voice_master_switch 
          ? 'bg-car-card border-car-accent shadow-[0_0_15px_rgba(230,168,34,0.12)]' 
          : 'bg-car-card border-car-border hover:border-car-border-light'
      ]"
    >
      <div class="flex-1 min-w-0 pr-6 flex flex-col space-y-2">
        <div class="flex items-center space-x-3">
          <span :class="['w-3.5 h-3.5 rounded-full shadow-md shrink-0', store.vehicleAuto.voice_master_switch ? 'bg-car-accent shadow-[0_0_10px_var(--accent-gold)]' : 'bg-slate-400']"></span>
          <span class="text-[23px] font-black text-car-text tracking-wide whitespace-nowrap">座舱车身语音播报总开关 (全车总闸)</span>
          <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent inline-flex items-center shrink-0">
            {{ store.vehicleAuto.voice_master_switch ? '全车正常发声' : '全车一键静音' }}
          </span>
        </div>
        <span class="text-[15.5px] text-car-sub font-bold">
          一键管控全车车门、挡位与驾驶模式语音播报。关闭后全车物理静音，独立生效。
        </span>
      </div>

      <!-- 右侧纯文字状态呈现 (整卡可点，无多余嵌套小按钮框) -->
      <div class="shrink-0 flex flex-col items-end pr-2">
        <div class="flex items-center space-x-2">
          <span :class="['w-3 h-3 rounded-full', store.vehicleAuto.voice_master_switch ? 'bg-car-accent shadow-[0_0_8px_var(--accent-gold)]' : 'bg-slate-500']"></span>
          <span :class="['text-[20px] font-black tracking-wide', store.vehicleAuto.voice_master_switch ? 'text-car-accent' : 'text-car-sub']">
            {{ store.vehicleAuto.voice_master_switch ? '● 全车播报中' : '○ 全车已静音' }}
          </span>
        </div>
        <span :class="['text-[13.5px] font-bold mt-1.5', store.vehicleAuto.voice_master_switch ? 'text-car-accent' : 'text-car-sub']">
          {{ store.vehicleAuto.voice_master_switch ? '点击整卡一键全车静音' : '轻触整卡开启语音播报' }}
        </span>
      </div>
    </div>

    <!-- 2. 实时车身物理信号探针 (支持折叠·默认收起) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-5 shadow-xl">
      <div class="flex items-center justify-between" :class="isProbeExpanded ? 'pb-3 mb-3 border-b border-car-border/60' : ''">
        <div class="flex items-center space-x-3">
          <span class="w-3 h-3 rounded-full bg-car-accent animate-pulse shadow-[0_0_8px_var(--accent-gold)]"></span>
          <span class="text-[20px] font-black text-car-text whitespace-nowrap">车身全域物理信号实时探针 (实车调试专用)</span>
          <span v-if="!isProbeExpanded" class="text-[14px] px-3.5 py-1 rounded-xl bg-car-item border border-car-border text-car-accent font-bold whitespace-nowrap">
            {{ formatGearName(doorStatus.gear) }} · {{ formatModeName(doorStatus.mode) }} · 五门电平监听中
          </span>
        </div>
        <div class="flex items-center space-x-3">
          <span v-if="isProbeExpanded" class="text-[14px] text-car-sub font-bold whitespace-nowrap">
            底层 MCU 串口 91 02 01、TCU 换挡与驾驶模式广播全量监听
          </span>
          <button 
            @click="isProbeExpanded = !isProbeExpanded"
            class="h-[52px] px-5 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[14.5px] transition-all whitespace-nowrap cursor-pointer shadow-sm"
          >
            {{ isProbeExpanded ? '收起探针 ▲' : '展开实时探针 (实车调试) ▼' }}
          </button>
        </div>
      </div>

      <div v-if="isProbeExpanded" class="flex flex-col space-y-4 pt-1">
        <div class="grid grid-cols-5 gap-3">
          <div :class="['p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all', doorStatus.fl === 1 ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/30 shadow-md' : 'bg-car-item border-car-border text-car-sub']">
            <span class="text-[13px] font-bold">主驾车门 (FL)</span>
            <span class="text-[16px] font-black mt-1">{{ doorStatus.fl === 1 ? '● 物理打开' : (doorStatus.fl === 0 ? '○ 已关好' : '采集中...') }}</span>
          </div>
          <div :class="['p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all', doorStatus.fr === 1 ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/30 shadow-md' : 'bg-car-item border-car-border text-car-sub']">
            <span class="text-[13px] font-bold">副驾车门 (FR)</span>
            <span class="text-[16px] font-black mt-1">{{ doorStatus.fr === 1 ? '● 物理打开' : (doorStatus.fr === 0 ? '○ 已关好' : '采集中...') }}</span>
          </div>
          <div :class="['p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all', doorStatus.rl === 1 ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/30 shadow-md' : 'bg-car-item border-car-border text-car-sub']">
            <span class="text-[13px] font-bold">左后车门 (RL)</span>
            <span class="text-[16px] font-black mt-1">{{ doorStatus.rl === 1 ? '● 物理打开' : (doorStatus.rl === 0 ? '○ 已关好' : '采集中...') }}</span>
          </div>
          <div :class="['p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all', doorStatus.rr === 1 ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/30 shadow-md' : 'bg-car-item border-car-border text-car-sub']">
            <span class="text-[13px] font-bold">右后车门 (RR)</span>
            <span class="text-[16px] font-black mt-1">{{ doorStatus.rr === 1 ? '● 物理打开' : (doorStatus.rr === 0 ? '○ 已关好' : '采集中...') }}</span>
          </div>
          <div :class="['p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all', doorStatus.trunk === 1 ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/30 shadow-md' : 'bg-car-item border-car-border text-car-sub']">
            <span class="text-[13px] font-bold">电动尾门 (Trunk)</span>
            <span class="text-[16px] font-black mt-1">{{ doorStatus.trunk === 1 ? '● 物理开启' : (doorStatus.trunk === 0 ? '○ 锁紧关好' : '采集中...') }}</span>
          </div>
        </div>

        <div class="grid grid-cols-2 gap-3">
          <div class="p-3.5 rounded-2xl border-2 bg-car-item border-car-border flex items-center justify-between px-5">
            <div class="flex flex-col">
              <span class="text-[13.5px] font-bold text-car-sub">实时挡位状态 (Gear)</span>
              <span class="text-[18px] font-black text-car-text mt-0.5">{{ formatGearName(doorStatus.gear) }}</span>
            </div>
            <span class="px-3.5 py-1 rounded-full text-[13.5px] font-black bg-car-card border border-car-border text-car-accent">
              {{ doorStatus.gear === 4 ? 'R 挡 (倒车状态)' : (doorStatus.gear === 2 ? 'D 挡 (前进状态)' : (doorStatus.gear === 5 ? 'P 挡 (驻车停泊)' : '实时监听中')) }}
            </span>
          </div>
          <div class="p-3.5 rounded-2xl border-2 bg-car-item border-car-border flex items-center justify-between px-5">
            <div class="flex flex-col">
              <span class="text-[13.5px] font-bold text-car-sub">实时驾驶模式 (DriveMode)</span>
              <span class="text-[18px] font-black text-car-text mt-0.5">{{ formatModeName(doorStatus.mode) }}</span>
            </div>
            <span class="px-3.5 py-1 rounded-full text-[13.5px] font-black bg-car-card border border-car-border text-car-accent">
              AdaptAPI 9位常量直通
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 3. 语音播报输出通道 (声道配置) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-5 shadow-xl">
      <div class="flex items-center justify-between pb-3 mb-3 border-b border-car-border/60">
        <div class="flex items-center space-x-2.5">
          <span class="text-[20px]">🔊</span>
          <span class="text-[19px] font-black text-car-text">语音播报音频输出通道 (声道配置)</span>
        </div>
        <button
          @click="testCurrentChannelVoice"
          class="h-[52px] px-6 rounded-2xl border-2 border-car-accent bg-car-item text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent shadow-md flex items-center shrink-0"
        >
          <span>试听当前通道</span>
        </button>
      </div>

      <div class="grid grid-cols-3 gap-3">
        <button 
          @click="setAudioChannel('music')"
          :class="[
            'p-4 rounded-2xl border-2 text-left flex flex-col justify-between cursor-pointer transition-all shadow-sm',
            currentAudioChannel === 'music'
              ? 'bg-car-item border-car-accent shadow-md'
              : 'bg-car-item border-car-border hover:border-car-border-light'
          ]"
        >
          <div class="flex items-center justify-between w-full">
            <span class="text-[17px] font-black text-car-text">媒体主声道 (推荐)</span>
            <span v-if="currentAudioChannel === 'music'" class="w-2.5 h-2.5 rounded-full bg-car-accent shadow-[0_0_6px_var(--accent-gold)]"></span>
          </div>
          <span class="text-[13.5px] text-car-sub font-bold mt-2 leading-relaxed">
            走媒体主功放扬声器，听歌时自动降音 60% 混音，播完后平滑恢复
          </span>
        </button>

        <button 
          @click="setAudioChannel('nav')"
          :class="[
            'p-4 rounded-2xl border-2 text-left flex flex-col justify-between cursor-pointer transition-all shadow-sm',
            currentAudioChannel === 'nav'
              ? 'bg-car-item border-car-accent shadow-md'
              : 'bg-car-item border-car-border hover:border-car-border-light'
          ]"
        >
          <div class="flex items-center justify-between w-full">
            <span class="text-[17px] font-black text-car-text">导航引导声道</span>
            <span v-if="currentAudioChannel === 'nav'" class="w-2.5 h-2.5 rounded-full bg-car-accent shadow-[0_0_6px_var(--accent-gold)]"></span>
          </div>
          <span class="text-[13.5px] text-car-sub font-bold mt-2 leading-relaxed">
            与高德地图同级 DSP 混音，抗车身衰减能力最强，清晰度最高
          </span>
        </button>

        <button 
          @click="setAudioChannel('notification')"
          :class="[
            'p-4 rounded-2xl border-2 text-left flex flex-col justify-between cursor-pointer transition-all shadow-sm',
            currentAudioChannel === 'notification'
              ? 'bg-car-item border-car-accent shadow-md'
              : 'bg-car-item border-car-border hover:border-car-border-light'
          ]"
        >
          <div class="flex items-center justify-between w-full">
            <span class="text-[17px] font-black text-car-text">系统通知声道</span>
            <span v-if="currentAudioChannel === 'notification'" class="w-2.5 h-2.5 rounded-full bg-car-accent shadow-[0_0_6px_var(--accent-gold)]"></span>
          </div>
          <span class="text-[13.5px] text-car-sub font-bold mt-2 leading-relaxed">
            走车载系统事件通知流，与原厂系统提示音同级，待机无歌时最佳
          </span>
        </button>
      </div>
    </div>

    <!-- 4. 座舱语音计划任务工作台 (场景工坊) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-6 shadow-xl flex items-center justify-between">
      <div class="flex-1 min-w-0 pr-6 flex flex-col space-y-1">
        <div class="flex items-center space-x-3">
          <span class="w-3 h-3 rounded-full bg-car-accent shadow-[0_0_10px_var(--accent-gold)]"></span>
          <span class="text-[23px] font-black text-car-text tracking-wide">座舱语音播报 · 计划任务工作台</span>
          <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-accent/40 text-car-accent">
            {{ activeVoiceTaskCount }} / {{ visibleVoiceTasks.length }} 项运行中
          </span>
        </div>
        <span class="text-[15.5px] text-car-sub font-bold">
          将繁杂的二十余项播报开关收拢为三大智能计划。不需要的播报计划可一键移除，点击进入二级向导可独立试听与配置台词。
        </span>
      </div>
      <div class="flex items-center space-x-3 shrink-0">
        <button
          v-if="removedVoiceTaskIds.length > 0"
          @click="openAddVoiceModal"
          class="h-[54px] px-6 rounded-2xl bg-car-item border-2 border-car-accent hover:border-car-accent-light text-car-accent font-black text-[16px] cursor-pointer shadow-md flex items-center space-x-2 transition-all"
        >
          <span>+ 添加语音计划</span>
          <span class="px-2 py-0.5 text-[12.5px] bg-car-card rounded-full border border-car-accent/40 text-car-text">
            {{ removedVoiceTaskIds.length }} 项可添加
          </span>
        </button>
        <button
          @click="resetAllVoiceTasks"
          class="h-[54px] px-5 rounded-2xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-sub hover:text-car-text font-black text-[15.5px] cursor-pointer shadow-sm transition-all"
        >
          ↺ 恢复推荐计划
        </button>
      </div>
    </div>

    <!-- 语音任务流列表 -->
    <div class="flex flex-col space-y-5">
      <!-- 语音任务 1: 挡位安全播报计划 -->
      <div 
        v-if="isVoiceTaskVisible('gear_voice')"
        :class="[
          'rounded-3xl border-2 p-6 shadow-xl transition-all duration-200 flex flex-col space-y-4',
          isGearVoicePlanActive 
            ? 'bg-car-card border-car-accent shadow-[0_0_15px_rgba(230,168,34,0.12)]' 
            : 'bg-car-card border-car-border hover:border-car-border-light'
        ]"
      >
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-3">
            <span class="text-[22px] font-black text-car-text tracking-wide">1. 换挡有人感知语音播报计划 (D / R / P / N)</span>
            <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent">换挡安全</span>
            <button 
              @click.stop="showGearHelp"
              class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-accent hover:border-car-accent font-black text-[18px] flex items-center justify-center cursor-pointer shadow-sm transition-transform active:scale-95"
            >
              ?
            </button>
          </div>
          <!-- 右侧操作区 -->
          <div class="flex items-center space-x-3">
            <button
              @click="toggleAllGearVoice"
              :class="[
                'h-[54px] px-6 rounded-2xl font-black text-[16px] cursor-pointer transition-all border-2 flex items-center space-x-2',
                isGearVoicePlanActive
                  ? 'bg-car-item border-car-accent text-car-text shadow-md'
                  : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
              ]"
            >
              <span :class="['w-2.5 h-2.5 rounded-full', isGearVoicePlanActive ? 'bg-car-accent' : 'bg-car-sub']"></span>
              <span>{{ isGearVoicePlanActive ? '换挡计划运行中' : '换挡计划已暂停' }}</span>
            </button>
            <button 
              @click="openGearConfigModal"
              class="h-[54px] px-5 rounded-2xl bg-car-item border-2 border-car-accent hover:border-car-accent text-car-accent font-black text-[15.5px] cursor-pointer shadow-sm transition-all"
            >
              🕹️ 挡位细分配置 ➔
            </button>
            <button 
              @click="removeVoiceTask('gear_voice', '换挡语音计划')"
              class="h-[54px] px-5 rounded-2xl bg-car-item border-2 border-car-border hover:border-red-500/80 text-car-sub hover:text-red-400 font-black text-[15px] cursor-pointer shadow-sm transition-all flex items-center space-x-1.5"
            >
              <span>🗑️</span>
              <span>移除</span>
            </button>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 pt-1">
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-start space-x-3">
            <span class="px-3 py-1 rounded-xl bg-car-card border border-car-border text-car-accent text-[15px] font-black shrink-0">当</span>
            <div class="flex flex-col space-y-1">
              <span class="text-[16px] font-black text-car-text">踩刹车切入前进 D 挡、倒车 R 挡或回驻车 P 挡（有人在座）</span>
              <span class="text-[13.5px] text-car-sub font-bold">内置有人感知状态机：蓝牙靠近通电默认静默，真正起步切挡才激活。</span>
            </div>
          </div>
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-start space-x-3">
            <span class="px-3 py-1 rounded-xl bg-car-card border border-car-accent/40 text-car-accent text-[15px] font-black shrink-0">就</span>
            <div class="flex flex-col space-y-1">
              <span class="text-[16px] font-black text-car-text">晓晓知性女声短促干脆播报对应挡位台词（N 挡空挡默认关防频繁打扰）</span>
              <span class="text-[13.5px] text-car-sub font-bold">以原厂 AVM 环视 + TCU 底层低 4 位为权威源，彻底杜绝虚假播报。</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 语音任务 2: 驾驶模式旋钮播报计划 -->
      <div 
        v-if="isVoiceTaskVisible('mode_voice')"
        :class="[
          'rounded-3xl border-2 p-6 shadow-xl transition-all duration-200 flex flex-col space-y-4',
          isModeVoicePlanActive 
            ? 'bg-car-card border-car-accent shadow-[0_0_15px_rgba(230,168,34,0.12)]' 
            : 'bg-car-card border-car-border hover:border-car-border-light'
        ]"
      >
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-3">
            <span class="text-[22px] font-black text-car-text tracking-wide">2. 驾驶模式旋钮切换播报计划 (4 大驾驶模式)</span>
            <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent">旋钮激擎</span>
            <button 
              @click.stop="showModeHelp"
              class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-accent hover:border-car-accent font-black text-[18px] flex items-center justify-center cursor-pointer shadow-sm transition-transform active:scale-95"
            >
              ?
            </button>
          </div>
          <div class="flex items-center space-x-3">
            <button
              @click="toggleAllModeVoice"
              :class="[
                'h-[54px] px-6 rounded-2xl font-black text-[16px] cursor-pointer transition-all border-2 flex items-center space-x-2',
                isModeVoicePlanActive
                  ? 'bg-car-item border-car-accent text-car-text shadow-md'
                  : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
              ]"
            >
              <span :class="['w-2.5 h-2.5 rounded-full', isModeVoicePlanActive ? 'bg-car-accent' : 'bg-car-sub']"></span>
              <span>{{ isModeVoicePlanActive ? '模式计划运行中' : '模式计划已暂停' }}</span>
            </button>
            <button 
              @click="openModeConfigModal"
              class="h-[54px] px-5 rounded-2xl bg-car-item border-2 border-car-accent hover:border-car-accent text-car-accent font-black text-[15.5px] cursor-pointer shadow-sm transition-all"
            >
              🏎️ 模式细分配置 ➔
            </button>
            <button 
              @click="removeVoiceTask('mode_voice', '驾驶模式计划')"
              class="h-[54px] px-5 rounded-2xl bg-car-item border-2 border-car-border hover:border-red-500/80 text-car-sub hover:text-red-400 font-black text-[15px] cursor-pointer shadow-sm transition-all flex items-center space-x-1.5"
            >
              <span>🗑️</span>
              <span>移除</span>
            </button>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 pt-1">
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-start space-x-3">
            <span class="px-3 py-1 rounded-xl bg-car-card border border-car-border text-car-accent text-[15px] font-black shrink-0">当</span>
            <div class="flex flex-col space-y-1">
              <span class="text-[16px] font-black text-car-text">中控旋钮切入 经济 / 舒适 / 运动 / 智能 模式</span>
              <span class="text-[13.5px] text-car-sub font-bold">内置 160ms 极速防抖滤波，快速旋动旋钮不掐灭音频、不错乱重叠。</span>
            </div>
          </div>
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-start space-x-3">
            <span class="px-3 py-1 rounded-xl bg-car-card border border-car-accent/40 text-car-accent text-[15px] font-black shrink-0">就</span>
            <div class="flex flex-col space-y-1">
              <span class="text-[16px] font-black text-car-text">晓晓知性温润声线发声，点亮对应座舱驾控氛围</span>
              <span class="text-[13.5px] text-car-sub font-bold">点火前 5 秒自动检测音频通路，防止原厂自检音效吞噬播报。</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 语音任务 3: 四门迎宾与关门安全播报计划 -->
      <div 
        v-if="isVoiceTaskVisible('door_voice')"
        :class="[
          'rounded-3xl border-2 p-6 shadow-xl transition-all duration-200 flex flex-col space-y-4',
          isDoorVoicePlanActive 
            ? 'bg-car-card border-car-accent shadow-[0_0_15px_rgba(230,168,34,0.12)]' 
            : 'bg-car-card border-car-border hover:border-car-border-light'
        ]"
      >
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-3">
            <span class="text-[22px] font-black text-car-text tracking-wide">3. 四门迎宾与关门安全播报计划 (五门防抖闭环)</span>
            <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent">五门联动</span>
            <button 
              @click.stop="showDoorHelp"
              class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-accent hover:border-car-accent font-black text-[18px] flex items-center justify-center cursor-pointer shadow-sm transition-transform active:scale-95"
            >
              ?
            </button>
          </div>
          <div class="flex items-center space-x-3">
            <button
              @click="toggleAllDoorVoice"
              :class="[
                'h-[54px] px-6 rounded-2xl font-black text-[16px] cursor-pointer transition-all border-2 flex items-center space-x-2',
                isDoorVoicePlanActive
                  ? 'bg-car-item border-car-accent text-car-text shadow-md'
                  : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
              ]"
            >
              <span :class="['w-2.5 h-2.5 rounded-full', isDoorVoicePlanActive ? 'bg-car-accent' : 'bg-car-sub']"></span>
              <span>{{ isDoorVoicePlanActive ? '车门计划运行中' : '车门计划已暂停' }}</span>
            </button>
            <button 
              @click="openDoorConfigModal"
              class="h-[54px] px-5 rounded-2xl bg-car-item border-2 border-car-accent hover:border-car-accent text-car-accent font-black text-[15.5px] cursor-pointer shadow-sm transition-all"
            >
              🚪 车门独立台词配置 ➔
            </button>
            <button 
              @click="removeVoiceTask('door_voice', '车门语音计划')"
              class="h-[54px] px-5 rounded-2xl bg-car-item border-2 border-car-border hover:border-red-500/80 text-car-sub hover:text-red-400 font-black text-[15px] cursor-pointer shadow-sm transition-all flex items-center space-x-1.5"
            >
              <span>🗑️</span>
              <span>移除</span>
            </button>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 pt-1">
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-start space-x-3">
            <span class="px-3 py-1 rounded-xl bg-car-card border border-car-border text-car-accent text-[15px] font-black shrink-0">当</span>
            <div class="flex flex-col space-y-1">
              <span class="text-[16px] font-black text-car-text">主驾、副驾、后排车门或电动尾门开启/关闭</span>
              <span class="text-[13.5px] text-car-sub font-bold">监听底盘 BCM 门锁总线信号，多门同时动作毫秒级防抖合并。</span>
            </div>
          </div>
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-start space-x-3">
            <span class="px-3 py-1 rounded-xl bg-car-card border border-car-accent/40 text-car-accent text-[15px] font-black shrink-0">就</span>
            <div class="flex flex-col space-y-1">
              <span class="text-[16px] font-black text-car-text">智能播报车门状态（开门统一「车门已打开」，关门统一「车门已关好」）</span>
              <span class="text-[13.5px] text-car-sub font-bold">支持【通用智能车门语音】与【独立分门专属语音】自由切换。</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 弹窗 1: 挡位细分配置二级向导 (GearConfigModal) -->
    <div 
      v-if="showGearModal" 
      class="fixed z-[9998] flex items-center justify-center bg-black/75 p-6"
      style="top:0; left:0; width:100vw; height:100vh;"
    >
      <div class="bg-car-card border-2 border-car-border rounded-3xl p-7 shadow-2xl max-w-[880px] w-full flex flex-col space-y-5 max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between border-b border-car-border pb-4">
          <div class="flex items-center space-x-3">
            <span class="w-3 h-3 rounded-full bg-car-accent"></span>
            <span class="text-[22px] font-black text-car-text">换挡语音详细配置 (四大标准挡位)</span>
          </div>
          <button 
            @click="showGearModal = false"
            class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-sub hover:text-car-text font-black text-[20px] flex items-center justify-center cursor-pointer"
          >
            ✕
          </button>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <!-- D 挡 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[19px] font-black text-car-text">前进挡 (D 挡)</span>
              <span class="text-[13px] text-car-sub font-bold">踩刹车切 D 挡启程播报</span>
            </div>
            <div class="flex items-center space-x-2">
              <button 
                @click="testVoice('gear_d')"
                class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14.5px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="toggleSetting('voice_enable_gear_d')"
                :class="[
                  'h-[52px] px-4 rounded-xl border-2 font-black text-[14.5px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_gear_d ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_gear_d ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>

          <!-- R 挡 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[19px] font-black text-car-text">倒车挡 (R 挡)</span>
              <span class="text-[13px] text-car-sub font-bold">切入倒挡触发后方警示</span>
            </div>
            <div class="flex items-center space-x-2">
              <button 
                @click="testVoice('gear_r')"
                class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14.5px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="toggleSetting('voice_enable_gear_r')"
                :class="[
                  'h-[52px] px-4 rounded-xl border-2 font-black text-[14.5px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_gear_r ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_gear_r ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>

          <!-- P 挡 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[19px] font-black text-car-text">驻车挡 (P 挡)</span>
              <span class="text-[13px] text-car-sub font-bold">挂回 P 挡并立即归零状态机</span>
            </div>
            <div class="flex items-center space-x-2">
              <button 
                @click="testVoice('gear_p')"
                class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14.5px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="toggleSetting('voice_enable_gear_p')"
                :class="[
                  'h-[52px] px-4 rounded-xl border-2 font-black text-[14.5px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_gear_p ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_gear_p ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>

          <!-- N 挡 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[19px] font-black text-car-text">空挡 (N 挡)</span>
              <span class="text-[13px] text-car-sub font-bold">默认关闭防红绿灯打扰</span>
            </div>
            <div class="flex items-center space-x-2">
              <button 
                @click="testVoice('gear_n')"
                class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14.5px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="toggleSetting('voice_enable_gear_n')"
                :class="[
                  'h-[52px] px-4 rounded-xl border-2 font-black text-[14.5px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_gear_n ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_gear_n ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>
        </div>

        <div class="flex justify-end pt-3 border-t border-car-border">
          <button 
            @click="showGearModal = false"
            class="h-[52px] px-8 rounded-xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[16px] cursor-pointer"
          >
            完成配置
          </button>
        </div>
      </div>
    </div>

    <!-- 弹窗 2: 驾驶模式细分配置二级向导 (ModeConfigModal) -->
    <div 
      v-if="showModeModal" 
      class="fixed z-[9998] flex items-center justify-center bg-black/75 p-6"
      style="top:0; left:0; width:100vw; height:100vh;"
    >
      <div class="bg-car-card border-2 border-car-border rounded-3xl p-7 shadow-2xl max-w-[880px] w-full flex flex-col space-y-5 max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between border-b border-car-border pb-4">
          <div class="flex items-center space-x-3">
            <span class="w-3 h-3 rounded-full bg-car-accent"></span>
            <span class="text-[22px] font-black text-car-text">4 大驾驶模式旋钮播报细分配置</span>
          </div>
          <button 
            @click="showModeModal = false"
            class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-sub hover:text-car-text font-black text-[20px] flex items-center justify-center cursor-pointer"
          >
            ✕
          </button>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <!-- 舒适 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[19px] font-black text-car-text">舒适模式 (Comfort)</span>
              <span class="text-[13px] text-car-sub font-bold">换挡平顺温润</span>
            </div>
            <div class="flex items-center space-x-2">
              <button 
                @click="testVoice('mode_comfort')"
                class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14.5px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="toggleSetting('voice_enable_mode_comfort')"
                :class="[
                  'h-[52px] px-4 rounded-xl border-2 font-black text-[14.5px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_mode_comfort ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_mode_comfort ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>

          <!-- 经济 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[19px] font-black text-car-text">经济模式 (Eco)</span>
              <span class="text-[13px] text-car-sub font-bold">极致节油长途巡航</span>
            </div>
            <div class="flex items-center space-x-2">
              <button 
                @click="testVoice('mode_eco')"
                class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14.5px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="toggleSetting('voice_enable_mode_eco')"
                :class="[
                  'h-[52px] px-4 rounded-xl border-2 font-black text-[14.5px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_mode_eco ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_mode_eco ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>

          <!-- 运动 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[19px] font-black text-car-text">运动模式 (Sport)</span>
              <span class="text-[13px] text-car-sub font-bold">油门激进动力充沛</span>
            </div>
            <div class="flex items-center space-x-2">
              <button 
                @click="testVoice('mode_sport')"
                class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14.5px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="toggleSetting('voice_enable_mode_sport')"
                :class="[
                  'h-[52px] px-4 rounded-xl border-2 font-black text-[14.5px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_mode_sport ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_mode_sport ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>

          <!-- 智能 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[19px] font-black text-car-text">智能模式 (Smart)</span>
              <span class="text-[13px] text-car-sub font-bold">动态自适应工况</span>
            </div>
            <div class="flex items-center space-x-2">
              <button 
                @click="testVoice('mode_smart')"
                class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14.5px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="toggleSetting('voice_enable_mode_smart')"
                :class="[
                  'h-[52px] px-4 rounded-xl border-2 font-black text-[14.5px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_mode_smart ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_mode_smart ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>
        </div>

        <div class="flex justify-end pt-3 border-t border-car-border">
          <button 
            @click="showModeModal = false"
            class="h-[52px] px-8 rounded-xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[16px] cursor-pointer"
          >
            完成配置
          </button>
        </div>
      </div>
    </div>

    <!-- 弹窗 3: 四门独立台词与模式配置二级向导 (DoorConfigModal) -->
    <div 
      v-if="showDoorModal" 
      class="fixed z-[9998] flex items-center justify-center bg-black/75 p-6"
      style="top:0; left:0; width:100vw; height:100vh;"
    >
      <div class="bg-car-card border-2 border-car-border rounded-3xl p-7 shadow-2xl max-w-[880px] w-full flex flex-col space-y-5 max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between border-b border-car-border pb-4">
          <div class="flex items-center space-x-3">
            <span class="w-3 h-3 rounded-full bg-car-accent"></span>
            <span class="text-[22px] font-black text-car-text">车门迎宾与关门播报详细配置</span>
          </div>
          <button 
            @click="showDoorModal = false"
            class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-sub hover:text-car-text font-black text-[20px] flex items-center justify-center cursor-pointer"
          >
            ✕
          </button>
        </div>

        <!-- 模式单选 -->
        <div class="flex items-center space-x-3 p-4 bg-car-item rounded-2xl border border-car-border">
          <button 
            @click="setDoorMode(true)"
            :class="[
              'flex-1 h-[52px] rounded-xl font-black text-[15.5px] cursor-pointer transition-all border-2',
              store.vehicleAuto.voice_door_mode_universal
                ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            通用智能车门语音 (推荐 · 合并防抖)
          </button>
          <button 
            @click="setDoorMode(false)"
            :class="[
              'flex-1 h-[52px] rounded-xl font-black text-[15.5px] cursor-pointer transition-all border-2',
              !store.vehicleAuto.voice_door_mode_universal
                ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            独立分门专属台词模式
          </button>
        </div>

        <!-- 分门开关与试听列表 -->
        <div class="grid grid-cols-2 gap-4">
          <!-- 主驾门 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[18px] font-black text-car-text">主驾车门</span>
              <span class="text-[13px] text-car-sub font-bold">开门迎宾 / 关好确认</span>
            </div>
            <div class="flex items-center space-x-2">
              <button 
                @click="testVoice('door_fl')"
                class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14.5px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="toggleSetting('voice_enable_door_fl')"
                :class="[
                  'h-[52px] px-4 rounded-xl border-2 font-black text-[14.5px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_door_fl ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_door_fl ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>

          <!-- 副驾门 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[18px] font-black text-car-text">副驾车门</span>
              <span class="text-[13px] text-car-sub font-bold">副驾上下车安全提示</span>
            </div>
            <div class="flex items-center space-x-2">
              <button 
                @click="testVoice('door_fr')"
                class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14.5px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="toggleSetting('voice_enable_door_fr')"
                :class="[
                  'h-[52px] px-4 rounded-xl border-2 font-black text-[14.5px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_door_fr ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_door_fr ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>

          <!-- 左后门 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[18px] font-black text-car-text">左后车门</span>
              <span class="text-[13px] text-car-sub font-bold">后排乘客开关门</span>
            </div>
            <div class="flex items-center space-x-2">
              <button 
                @click="testVoice('door_rl')"
                class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14.5px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="toggleSetting('voice_enable_door_rl')"
                :class="[
                  'h-[52px] px-4 rounded-xl border-2 font-black text-[14.5px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_door_rl ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_door_rl ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>

          <!-- 右后门 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <span class="text-[18px] font-black text-car-text">右后车门</span>
              <span class="text-[13px] text-car-sub font-bold">后排乘客开关门</span>
            </div>
            <div class="flex items-center space-x-2">
              <button 
                @click="testVoice('door_rr')"
                class="h-[52px] px-4 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14.5px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="toggleSetting('voice_enable_door_rr')"
                :class="[
                  'h-[52px] px-4 rounded-xl border-2 font-black text-[14.5px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_door_rr ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_door_rr ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>
        </div>

        <div class="flex justify-end pt-3 border-t border-car-border">
          <button 
            @click="showDoorModal = false"
            class="h-[52px] px-8 rounded-xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[16px] cursor-pointer"
          >
            完成配置
          </button>
        </div>
      </div>
    </div>

    <!-- 弹窗 4: 添加语音任务至工作台 (AddVoiceModal) -->
    <div 
      v-if="showAddVoiceModal" 
      class="fixed z-[9998] flex items-center justify-center bg-black/75 p-6"
      style="top:0; left:0; width:100vw; height:100vh;"
    >
      <div class="bg-car-card border-2 border-car-border rounded-3xl p-7 shadow-2xl max-w-[800px] w-full flex flex-col space-y-6">
        <div class="flex items-center justify-between border-b border-car-border pb-4">
          <div class="flex items-center space-x-3">
            <span class="w-3 h-3 rounded-full bg-car-accent"></span>
            <span class="text-[22px] font-black text-car-text">添加语音计划至工作台</span>
          </div>
          <button 
            @click="showAddVoiceModal = false"
            class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-sub hover:text-car-text font-black text-[20px] flex items-center justify-center cursor-pointer"
          >
            ✕
          </button>
        </div>

        <div class="flex flex-col space-y-3 max-h-[460px] overflow-y-auto pr-1">
          <div 
            v-for="task in availableVoiceTasks" 
            :key="task.id"
            class="p-4 rounded-2xl bg-car-item border-2 border-car-border hover:border-car-accent flex items-center justify-between transition-all"
          >
            <div class="flex flex-col space-y-1 pr-4">
              <div class="flex items-center space-x-2">
                <span class="text-[18px] font-black text-car-text">{{ task.title }}</span>
                <span class="px-2.5 py-0.5 text-[12px] font-black rounded-full border bg-car-card border-car-border text-car-accent">
                  {{ task.tag }}
                </span>
              </div>
              <span class="text-[14px] text-car-sub font-bold">{{ task.desc }}</span>
            </div>
            <button 
              @click="restoreVoiceTask(task.id)"
              class="h-[52px] px-5 rounded-xl bg-car-card border-2 border-car-accent text-car-text font-black text-[15px] cursor-pointer hover:bg-car-item whitespace-nowrap shadow-sm"
            >
              + 加入列表
            </button>
          </div>
        </div>

        <div class="flex justify-end pt-2">
          <button 
            @click="showAddVoiceModal = false"
            class="h-[52px] px-8 rounded-xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[16px] cursor-pointer"
          >
            完成
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { store, bridge, showToast, openModal } from '../store';

const STORAGE_VOICE_KEY = 'geely_removed_voice_tasks_v2';
const removedVoiceTaskIds = ref(JSON.parse(localStorage.getItem(STORAGE_VOICE_KEY) || '[]'));

const showAddVoiceModal = ref(false);
const showGearModal = ref(false);
const showModeModal = ref(false);
const showDoorModal = ref(false);

const isProbeExpanded = ref(false);
const doorStatus = ref({ fl: -1, fr: -1, rl: -1, rr: -1, trunk: -1, gear: -1, mode: -1 });
let doorPollTimer = null;

const allVoiceTasks = [
  { id: 'gear_voice', title: '换挡有人感知语音播报计划', tag: '换挡安全', desc: '踩刹车挂入 D/R/P 挡位有人感知播报，N 挡空挡默认关防频繁打扰。' },
  { id: 'mode_voice', title: '驾驶模式旋钮切换播报计划', tag: '旋钮激擎', desc: '中控旋钮切入经济/舒适/运动/智能模式温润发声，带 160ms 防抖。' },
  { id: 'door_voice', title: '四门迎宾与关门安全播报计划', tag: '五门联动', desc: '智能车门合并防抖播报，支持通用智能车门与独立分门模式。' }
];

const visibleVoiceTasks = computed(() => {
  return allVoiceTasks.filter(t => !removedVoiceTaskIds.value.includes(t.id));
});

const availableVoiceTasks = computed(() => {
  return allVoiceTasks.filter(t => removedVoiceTaskIds.value.includes(t.id));
});

function isVoiceTaskVisible(id) {
  return !removedVoiceTaskIds.value.includes(id);
}

const isGearVoicePlanActive = computed(() => {
  return !!(store.vehicleAuto.voice_enable_gear_d || store.vehicleAuto.voice_enable_gear_r || store.vehicleAuto.voice_enable_gear_p);
});

const isModeVoicePlanActive = computed(() => {
  return !!(store.vehicleAuto.voice_enable_mode_comfort || store.vehicleAuto.voice_enable_mode_eco || store.vehicleAuto.voice_enable_mode_sport || store.vehicleAuto.voice_enable_mode_smart);
});

const isDoorVoicePlanActive = computed(() => {
  return !!(store.vehicleAuto.voice_enable_door_fl || store.vehicleAuto.voice_enable_door_fr || store.vehicleAuto.voice_enable_door_rl || store.vehicleAuto.voice_enable_door_rr);
});

const activeVoiceTaskCount = computed(() => {
  let count = 0;
  if (isVoiceTaskVisible('gear_voice') && isGearVoicePlanActive.value) count++;
  if (isVoiceTaskVisible('mode_voice') && isModeVoicePlanActive.value) count++;
  if (isVoiceTaskVisible('door_voice') && isDoorVoicePlanActive.value) count++;
  return count;
});

function toggleAllGearVoice() {
  const next = !isGearVoicePlanActive.value;
  store.vehicleAuto.voice_enable_gear_d = next;
  store.vehicleAuto.voice_enable_gear_r = next;
  store.vehicleAuto.voice_enable_gear_p = next;
  bridge.call('setVehicleAutomationSetting', 'voice_enable_gear_d', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_gear_r', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_gear_p', next);
  showToast(next ? '换挡播报计划已开启 (D/R/P)' : '换挡播报计划已暂停');
}

function toggleAllModeVoice() {
  const next = !isModeVoicePlanActive.value;
  store.vehicleAuto.voice_enable_mode_comfort = next;
  store.vehicleAuto.voice_enable_mode_eco = next;
  store.vehicleAuto.voice_enable_mode_sport = next;
  store.vehicleAuto.voice_enable_mode_smart = next;
  bridge.call('setVehicleAutomationSetting', 'voice_enable_mode_comfort', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_mode_eco', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_mode_sport', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_mode_smart', next);
  showToast(next ? '驾驶模式播报计划已开启' : '驾驶模式播报计划已暂停');
}

function toggleAllDoorVoice() {
  const next = !isDoorVoicePlanActive.value;
  store.vehicleAuto.voice_enable_door_fl = next;
  store.vehicleAuto.voice_enable_door_fr = next;
  store.vehicleAuto.voice_enable_door_rl = next;
  store.vehicleAuto.voice_enable_door_rr = next;
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_fl', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_fr', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_rl', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_rr', next);
  showToast(next ? '车门播报计划已开启' : '车门播报计划已暂停');
}

function removeVoiceTask(id, name) {
  if (!removedVoiceTaskIds.value.includes(id)) {
    removedVoiceTaskIds.value.push(id);
    localStorage.setItem(STORAGE_VOICE_KEY, JSON.stringify(removedVoiceTaskIds.value));
    showToast(`已从工作台移除语音计划: ${name}`);
  }
}

function restoreVoiceTask(id) {
  removedVoiceTaskIds.value = removedVoiceTaskIds.value.filter(item => item !== id);
  localStorage.setItem(STORAGE_VOICE_KEY, JSON.stringify(removedVoiceTaskIds.value));
  showToast('已成功添加语音计划至工作台');
}

function resetAllVoiceTasks() {
  removedVoiceTaskIds.value = [];
  localStorage.setItem(STORAGE_VOICE_KEY, JSON.stringify([]));
  showToast('已恢复全量推荐座舱语音计划');
}

function openAddVoiceModal() {
  showAddVoiceModal.value = true;
}

function openGearConfigModal() {
  showGearModal.value = true;
}

function openModeConfigModal() {
  showModeModal.value = true;
}

function openDoorConfigModal() {
  showDoorModal.value = true;
}

function showGearHelp() {
  openModal('confirm', {
    title: '【功能指南】换挡权威源与有人感知状态机',
    desc: '1. 权威判定：以原厂 360 环视 AVM 广播与 TCU 换挡底层低 4 位为权威源，过滤 10 号假挡位。\n\n2. 有人感知：蓝牙钥匙靠近唤醒时保持静默；真正踩刹车切 D/R 挡才激活；回 P 挡播报一次后归零休眠。',
    tip: 'N 挡空挡播报默认关闭，避免红绿灯打扰。',
    showCancel: false,
    confirmText: '我知道了'
  });
}

function showModeHelp() {
  openModal('confirm', {
    title: '【功能指南】4 大驾驶模式旋钮切换',
    desc: '1. 旋钮响应：支持舒适、经济、运动与智能 4 大驾驶模式。\n\n2. 极速防抖：内置 160ms 防抖滤波，防止快速旋转旋钮掐灭音频或重叠发声。',
    tip: '点火前 5 秒自动保护音频通道，防止原厂开机音吞音。',
    showCancel: false,
    confirmText: '我知道了'
  });
}

function showDoorHelp() {
  openModal('confirm', {
    title: '【功能指南】五门防抖与通用智能语音',
    desc: '1. 通用智能模式：推荐模式。开门统一播报「车门已打开」，关门统一播报「车门已关好」，多门同时动作合并防抖，不抢音。\n\n2. 独立分门模式：可分别为主驾、副驾、后排播报专属台词。',
    tip: '在二级向导中可一键切换模式。',
    showCancel: false,
    confirmText: '我知道了'
  });
}

function formatGearName(gear) {
  switch (gear) {
    case 2: return '前进挡 (D 挡)';
    case 4: return '倒车挡 (R 挡)';
    case 5: return '驻车挡 (P 挡)';
    case 1: return '空挡 (N 挡)';
    default: return '采集中...';
  }
}

function formatModeName(mode) {
  switch (mode) {
    case 1: return '舒适模式 (Comfort)';
    case 2: return '运动模式 (Sport)';
    case 3: return '经济模式 (Eco)';
    case 4: return '智能模式 (Smart)';
    default: return '采集中...';
  }
}

function fetchDoorStatus() {
  try {
    const raw = bridge.call('getDoorStatus');
    if (raw) {
      const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
      if (data && typeof data === 'object') {
        doorStatus.value = {
          fl: Number(data.fl ?? -1),
          fr: Number(data.fr ?? -1),
          rl: Number(data.rl ?? -1),
          rr: Number(data.rr ?? -1),
          trunk: Number(data.trunk ?? -1),
          gear: Number(data.gear ?? -1),
          mode: Number(data.mode ?? -1)
        };
      }
    }
  } catch (e) {}
}

const currentAudioChannel = computed(() => store.vehicleAuto.voice_audio_channel || 'music');

function setAudioChannel(channel) {
  store.vehicleAuto.voice_audio_channel = channel;
  bridge.call('setVehicleAutomationStringSetting', 'voice_audio_channel', channel);
  const labels = {
    music: '媒体主声道 (听歌自动压音混音)',
    nav: '导航引导声道 (抗衰减强力混音)',
    notification: '系统通知声道 (事件提示音)'
  };
  showToast('已切换至: ' + (labels[channel] || channel));
}

function testCurrentChannelVoice() {
  bridge.call('testVehicleVoice', 'door_fl');
  showToast('正在通过当前选择声道试听播报...');
}

function setDoorMode(isUniversal) {
  store.vehicleAuto.voice_door_mode_universal = isUniversal;
  bridge.call('setVehicleAutomationSetting', 'voice_door_mode_universal', isUniversal);
  showToast(isUniversal ? '已切换为: 通用智能车门语音 (推荐)' : '已切换为: 独立分门明细语音');
}

function toggleSetting(key) {
  const next = !store.vehicleAuto[key];
  store.vehicleAuto[key] = next;
  bridge.call('setVehicleAutomationSetting', key, next);
  showToast('设置已更新: ' + (next ? '已开启' : '已关闭'));
}

function toggleVoiceMasterSwitch() {
  const next = !store.vehicleAuto.voice_master_switch;
  store.vehicleAuto.voice_master_switch = next;
  bridge.call('setVehicleAutomationSetting', 'voice_master_switch', next);
  showToast(next ? '座舱车身语音总开关: 已开启 (正常播报)' : '座舱车身语音总开关: 已关闭 (全车静音)');
}

function testVoice(type) {
  bridge.call('testVehicleVoice', type);
}

onMounted(() => {
  fetchDoorStatus();
  doorPollTimer = setInterval(fetchDoorStatus, 800);
});

onUnmounted(() => {
  if (doorPollTimer) clearInterval(doorPollTimer);
});
</script>