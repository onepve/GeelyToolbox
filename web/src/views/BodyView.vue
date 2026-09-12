<template>
  <div class="flex flex-col space-y-6">
    <!-- 核心：座舱车身语音播报总开关 (全车语音发声总闸门) - 提权置顶首屏 (左右分栏车规黄金磁贴) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-5 min-h-[106px] shadow-xl flex items-center justify-between transition-all">
      <div class="w-[60%] max-w-[60%] flex flex-col space-y-1.5 shrink-0">
        <div class="flex items-center space-x-3">
          <span :class="['w-3.5 h-3.5 rounded-full shadow-md shrink-0', store.vehicleAuto.voice_master_switch ? 'bg-emerald-500 shadow-[0_0_10px_#10B981]' : 'bg-slate-400']"></span>
          <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">座舱车身语音播报总开关 (全车总闸)</span>
          <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-item border-car-border text-car-text inline-flex items-center shrink-0 shadow-sm">
            <span :class="['w-2.5 h-2.5 rounded-full mr-2', store.vehicleAuto.voice_master_switch ? 'bg-emerald-500 shadow-[0_0_6px_#10B981]' : 'bg-slate-400']"></span>
            {{ store.vehicleAuto.voice_master_switch ? '全车正常播报' : '全车一键静音中' }}
          </span>
        </div>
        <div class="text-[14.5px] text-car-sub font-bold leading-normal">
          一键管控全车车门、挡位与驾驶模式语音播报。关闭后全车物理静音，独立生效。
        </div>
      </div>

      <div class="shrink-0 w-[230px]">
        <button
          @click="toggleVoiceMasterSwitch"
          :class="[
            'w-full h-[74px] px-4 py-2 rounded-2xl border-2 cursor-pointer transition-all shadow-md flex flex-col items-center justify-center text-center',
            store.vehicleAuto.voice_master_switch
              ? 'bg-car-item border-car-accent'
              : 'bg-car-card border-car-border hover:border-car-border-light'
          ]"
        >
          <span class="text-[18.5px] font-black text-car-text tracking-wide whitespace-nowrap">
            {{ store.vehicleAuto.voice_master_switch ? '车身语音已开启' : '车身语音已关闭' }}
          </span>
          <span :class="['text-[12.5px] font-bold mt-1 whitespace-nowrap', store.vehicleAuto.voice_master_switch ? 'text-car-accent' : 'text-car-sub']">
            {{ store.vehicleAuto.voice_master_switch ? '点击切换为全车静音' : '点击开启语音播报' }}
          </span>
        </button>
      </div>
    </div>

    <!-- 实时车身物理信号探针 (四门、尾门、挡位、模式 全量直观反映底层电平跃变) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-5 shadow-xl">
      <div class="flex items-center justify-between pb-3 mb-3 border-b border-car-border/60">
        <div class="flex items-center space-x-2.5">
          <span class="w-3 h-3 rounded-full bg-emerald-500 animate-pulse shadow-[0_0_8px_#10B981]"></span>
          <span class="text-[18.5px] font-black text-car-text">车身全域物理信号实时探针 (实车调试专用)</span>
        </div>
        <span class="text-[13.5px] text-car-sub font-bold">
          底层 MCU 串口 91 02 01、TCU 换挡与驾驶模式广播全量监听，动作毫秒级点亮
        </span>
      </div>

      <!-- 第一排：四门与电动尾门 -->
      <div class="grid grid-cols-5 gap-3 mb-3">
        <!-- 主驾门 -->
        <div 
          :class="[
            'p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all',
            doorStatus.fl === 1 
              ? 'bg-emerald-500/20 border-emerald-500 text-emerald-400 ring-2 ring-emerald-500/30 shadow-md' 
              : 'bg-car-item border-car-border text-car-sub'
          ]"
        >
          <span class="text-[13px] font-bold">主驾车门 (FL)</span>
          <span class="text-[16px] font-black mt-1">
            {{ doorStatus.fl === 1 ? '● 物理打开' : (doorStatus.fl === 0 ? '○ 已关好' : '采集中...') }}
          </span>
        </div>

        <!-- 副驾门 -->
        <div 
          :class="[
            'p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all',
            doorStatus.fr === 1 
              ? 'bg-emerald-500/20 border-emerald-500 text-emerald-400 ring-2 ring-emerald-500/30 shadow-md' 
              : 'bg-car-item border-car-border text-car-sub'
          ]"
        >
          <span class="text-[13px] font-bold">副驾车门 (FR)</span>
          <span class="text-[16px] font-black mt-1">
            {{ doorStatus.fr === 1 ? '● 物理打开' : (doorStatus.fr === 0 ? '○ 已关好' : '采集中...') }}
          </span>
        </div>

        <!-- 左后门 -->
        <div 
          :class="[
            'p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all',
            doorStatus.rl === 1 
              ? 'bg-emerald-500/20 border-emerald-500 text-emerald-400 ring-2 ring-emerald-500/30 shadow-md' 
              : 'bg-car-item border-car-border text-car-sub'
          ]"
        >
          <span class="text-[13px] font-bold">左后车门 (RL)</span>
          <span class="text-[16px] font-black mt-1">
            {{ doorStatus.rl === 1 ? '● 物理打开' : (doorStatus.rl === 0 ? '○ 已关好' : '采集中...') }}
          </span>
        </div>

        <!-- 右后门 -->
        <div 
          :class="[
            'p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all',
            doorStatus.rr === 1 
              ? 'bg-emerald-500/20 border-emerald-500 text-emerald-400 ring-2 ring-emerald-500/30 shadow-md' 
              : 'bg-car-item border-car-border text-car-sub'
          ]"
        >
          <span class="text-[13px] font-bold">右后车门 (RR)</span>
          <span class="text-[16px] font-black mt-1">
            {{ doorStatus.rr === 1 ? '● 物理打开' : (doorStatus.rr === 0 ? '○ 已关好' : '采集中...') }}
          </span>
        </div>

        <!-- 电动尾门 -->
        <div 
          :class="[
            'p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all',
            doorStatus.trunk === 1 
              ? 'bg-emerald-500/20 border-emerald-500 text-emerald-400 ring-2 ring-emerald-500/30 shadow-md' 
              : 'bg-car-item border-car-border text-car-sub'
          ]"
        >
          <span class="text-[13px] font-bold">电动尾门 (Trunk)</span>
          <span class="text-[16px] font-black mt-1">
            {{ doorStatus.trunk === 1 ? '● 物理打开' : (doorStatus.trunk === 0 ? '○ 已关好' : '采集中...') }}
          </span>
        </div>
      </div>

      <!-- 第二排：实时挡位与驾驶模式探针 -->
      <div class="grid grid-cols-2 gap-3">
        <!-- 实时挡位 -->
        <div 
          :class="[
            'p-3.5 rounded-2xl border-2 flex items-center justify-between px-5 transition-all',
            doorStatus.gear > 0 
              ? 'bg-car-item border-car-accent ring-2 ring-car-accent/20' 
              : 'bg-car-item border-car-border'
          ]"
        >
          <div class="flex flex-col">
            <span class="text-[13.5px] font-bold text-car-sub">实时挡位状态 (Gear)</span>
            <span class="text-[18px] font-black text-car-text mt-0.5">
              {{ formatGearName(doorStatus.gear) }}
            </span>
          </div>
          <span class="px-3.5 py-1 rounded-full text-[13.5px] font-black bg-car-card border border-car-border text-car-accent">
            {{ doorStatus.gear === 4 ? 'R 挡 (倒车状态)' : (doorStatus.gear === 2 ? 'D 挡 (前进状态)' : (doorStatus.gear === 5 ? 'P 挡 (驻车停泊)' : '实时监听中')) }}
          </span>
        </div>

        <!-- 实时驾驶模式 -->
        <div 
          :class="[
            'p-3.5 rounded-2xl border-2 flex items-center justify-between px-5 transition-all',
            doorStatus.mode > 0 
              ? 'bg-car-item border-car-accent ring-2 ring-car-accent/20' 
              : 'bg-car-item border-car-border'
          ]"
        >
          <div class="flex flex-col">
            <span class="text-[13.5px] font-bold text-car-sub">实时驾驶模式 (DriveMode)</span>
            <span class="text-[18px] font-black text-car-text mt-0.5">
              {{ formatModeName(doorStatus.mode) }}
            </span>
          </div>
          <span class="px-3.5 py-1 rounded-full text-[13.5px] font-black bg-car-card border border-car-border text-car-accent">
            AdaptAPI 9位常量直通
          </span>
        </div>
      </div>
    </div>

    <!-- 语音播报音频输出通道与混音抗衰减配置 -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-5 shadow-xl">
      <div class="flex items-center justify-between pb-3 mb-3 border-b border-car-border/60">
        <div class="flex items-center space-x-2.5">
          <span class="text-[20px]">🔊</span>
          <span class="text-[18.5px] font-black text-car-text">语音播报音频输出通道 (声道配置)</span>
        </div>
        <button
          @click="testCurrentChannelVoice"
          class="h-[44px] px-5 rounded-xl border-2 border-car-accent bg-car-item text-car-text font-black text-[15px] cursor-pointer hover:border-car-accent shadow-sm flex items-center shrink-0"
        >
          <span>试听当前通道</span>
        </button>
      </div>

      <div class="grid grid-cols-3 gap-3">
        <!-- 通道 1: 媒体主声道 -->
        <button 
          @click="setAudioChannel('music')"
          :class="[
            'p-4 rounded-2xl border-2 text-left flex flex-col justify-between cursor-pointer transition-all shadow-sm',
            currentAudioChannel === 'music'
              ? 'bg-car-item border-car-accent ring-2 ring-car-accent/25 shadow-md'
              : 'bg-car-item border-car-border hover:border-car-border-light'
          ]"
        >
          <div class="flex items-center justify-between w-full">
            <span class="text-[17px] font-black text-car-text">媒体主声道 (推荐)</span>
            <span v-if="currentAudioChannel === 'music'" class="w-2.5 h-2.5 rounded-full bg-car-accent shadow-[0_0_6px_var(--accent-gold)]"></span>
          </div>
          <span class="text-[13.5px] text-car-sub font-bold mt-2 leading-relaxed">
            走媒体主功放扬声器，听歌时自动将音乐降音 60% 进行温润混音，播完后平滑恢复
          </span>
        </button>

        <!-- 通道 2: 导航引导声道 -->
        <button 
          @click="setAudioChannel('nav')"
          :class="[
            'p-4 rounded-2xl border-2 text-left flex flex-col justify-between cursor-pointer transition-all shadow-sm',
            currentAudioChannel === 'nav'
              ? 'bg-car-item border-car-accent ring-2 ring-car-accent/25 shadow-md'
              : 'bg-car-item border-car-border hover:border-car-border-light'
          ]"
        >
          <div class="flex items-center justify-between w-full">
            <span class="text-[17px] font-black text-car-text">导航引导声道</span>
            <span v-if="currentAudioChannel === 'nav'" class="w-2.5 h-2.5 rounded-full bg-car-accent shadow-[0_0_6px_var(--accent-gold)]"></span>
          </div>
          <span class="text-[13.5px] text-car-sub font-bold mt-2 leading-relaxed">
            与高德地图同级 DSP 混音，抗车身衰减能力最强，即使遇到系统降音也能清晰发声
          </span>
        </button>

        <!-- 通道 3: 系统通知声道 -->
        <button 
          @click="setAudioChannel('notification')"
          :class="[
            'p-4 rounded-2xl border-2 text-left flex flex-col justify-between cursor-pointer transition-all shadow-sm',
            currentAudioChannel === 'notification'
              ? 'bg-car-item border-car-accent ring-2 ring-car-accent/25 shadow-md'
              : 'bg-car-item border-car-border hover:border-car-border-light'
          ]"
        >
          <div class="flex items-center justify-between w-full">
            <span class="text-[17px] font-black text-car-text">系统通知声道</span>
            <span v-if="currentAudioChannel === 'notification'" class="w-2.5 h-2.5 rounded-full bg-car-accent shadow-[0_0_6px_var(--accent-gold)]"></span>
          </div>
          <span class="text-[13.5px] text-car-sub font-bold mt-2 leading-relaxed">
            走车载系统事件通知流，与车机原厂系统提示音同级，适合纯待机不播放音乐场景
          </span>
        </button>
      </div>
    </div>

    <!-- 1. 挡位安全播报 (前进档、倒车档、驻车档 P、空档 四大标准档位) -->
    <FeatureCard 
      title="1. 挡位安全播报 (前进档 D / 倒车档 R / 驻车档 P / 空档 N)"
      desc="四大标准挡位均已生成专属高品质晓晓知性语音，并支持【声效设置】。内置【有人感知状态机】：开机与蓝牙靠近默认 P 挡绝对静默，换出激活，换回归零。"
    >
      <div class="grid grid-cols-2 gap-4">
        <!-- 前进挡 D -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex flex-col justify-center pr-6 flex-1 min-w-0">
            <div class="flex items-center space-x-3 mb-1.5">
              <span class="text-[22px] font-black text-car-text tracking-wide">前进挡 (D 挡)</span>
              <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-car-accent font-bold">启程安全</span>
            </div>
            <span class="text-[15px] text-car-sub font-bold leading-relaxed">踩刹车挂入前进挡温馨启程播报</span>
          </div>

          <div class="flex space-x-3 shrink-0 items-center">
            <button 
              @click="toggleSetting('voice_enable_gear_d')"
              :class="[
                'w-[124px] h-[120px] rounded-2xl border-2 font-black transition-all flex flex-col items-center justify-center select-none cursor-pointer shadow-md',
                store.vehicleAuto.voice_enable_gear_d 
                  ? 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-amber-500/10' 
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              <span class="text-[19.5px] font-black text-car-text tracking-wide mb-1">D挡播报</span>
              <span :class="['text-[14px] font-bold', store.vehicleAuto.voice_enable_gear_d ? 'text-car-accent' : 'text-car-sub']">
                {{ store.vehicleAuto.voice_enable_gear_d ? '已开启' : '已关闭' }}
              </span>
            </button>
            <div class="flex flex-col space-y-2.5 w-[130px]">
              <button 
                @click="testVoice('gear_d')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                试听语音
              </button>
              <button 
                @click="openCustomVoice('gear_d', '前进挡 D', 'gear_d.mp3')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-sub hover:text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                声效设置
              </button>
            </div>
          </div>
        </div>

        <!-- 倒车挡 R -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex flex-col justify-center pr-6 flex-1 min-w-0">
            <div class="flex items-center space-x-3 mb-1.5">
              <span class="text-[22px] font-black text-car-text tracking-wide">倒车挡 (R 挡)</span>
              <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-amber-400 font-bold">后方警示</span>
            </div>
            <span class="text-[15px] text-car-sub font-bold leading-relaxed">挂入倒车挡触发后方安全观察警示</span>
          </div>

          <div class="flex space-x-3 shrink-0 items-center">
            <button 
              @click="toggleSetting('voice_enable_gear_r')"
              :class="[
                'w-[124px] h-[120px] rounded-2xl border-2 font-black transition-all flex flex-col items-center justify-center select-none cursor-pointer shadow-md',
                store.vehicleAuto.voice_enable_gear_r 
                  ? 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-amber-500/10' 
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              <span class="text-[19.5px] font-black text-car-text tracking-wide mb-1">R挡播报</span>
              <span :class="['text-[14px] font-bold', store.vehicleAuto.voice_enable_gear_r ? 'text-car-accent' : 'text-car-sub']">
                {{ store.vehicleAuto.voice_enable_gear_r ? '已开启' : '已关闭' }}
              </span>
            </button>
            <div class="flex flex-col space-y-2.5 w-[130px]">
              <button 
                @click="testVoice('gear_r')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                试听语音
              </button>
              <button 
                @click="openCustomVoice('gear_r', '倒车挡 R')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-sub hover:text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                声效设置
              </button>
            </div>
          </div>
        </div>

        <!-- 驻车挡 P -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex flex-col justify-center pr-6 flex-1 min-w-0">
            <div class="flex items-center space-x-3 mb-1.5">
              <span class="text-[22px] font-black text-car-text tracking-wide">驻车挡 (P 挡)</span>
              <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-emerald-400 font-bold">停泊就绪</span>
            </div>
            <span class="text-[15px] text-car-sub font-bold leading-relaxed">挂回 P 挡播报一次并立即归零状态机</span>
          </div>

          <div class="flex space-x-3 shrink-0 items-center">
            <button 
              @click="toggleSetting('voice_enable_gear_p')"
              :class="[
                'w-[124px] h-[120px] rounded-2xl border-2 font-black transition-all flex flex-col items-center justify-center select-none cursor-pointer shadow-md',
                store.vehicleAuto.voice_enable_gear_p 
                  ? 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-amber-500/10' 
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              <span class="text-[19.5px] font-black text-car-text tracking-wide mb-1">P挡播报</span>
              <span :class="['text-[14px] font-bold', store.vehicleAuto.voice_enable_gear_p ? 'text-car-accent' : 'text-car-sub']">
                {{ store.vehicleAuto.voice_enable_gear_p ? '已开启' : '已关闭' }}
              </span>
            </button>
            <div class="flex flex-col space-y-2.5 w-[130px]">
              <button 
                @click="testVoice('gear_p')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                试听语音
              </button>
              <button 
                @click="openCustomVoice('gear_p', '驻车挡 P')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-sub hover:text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                声效设置
              </button>
            </div>
          </div>
        </div>

        <!-- 空挡 N -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex flex-col justify-center pr-6 flex-1 min-w-0">
            <div class="flex items-center space-x-3 mb-1.5">
              <span class="text-[22px] font-black text-car-text tracking-wide">空挡 (N 挡)</span>
              <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-car-sub font-bold">临时切空</span>
            </div>
            <span class="text-[15px] text-car-sub font-bold leading-relaxed">等待红绿灯或洗车拖车空挡提醒</span>
          </div>

          <div class="flex space-x-3 shrink-0 items-center">
            <button 
              @click="toggleSetting('voice_enable_gear_n')"
              :class="[
                'w-[124px] h-[120px] rounded-2xl border-2 font-black transition-all flex flex-col items-center justify-center select-none cursor-pointer shadow-md',
                store.vehicleAuto.voice_enable_gear_n 
                  ? 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-amber-500/10' 
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              <span class="text-[19.5px] font-black text-car-text tracking-wide mb-1">N挡播报</span>
              <span :class="['text-[14px] font-bold', store.vehicleAuto.voice_enable_gear_n ? 'text-car-accent' : 'text-car-sub']">
                {{ store.vehicleAuto.voice_enable_gear_n ? '已开启' : '已关闭' }}
              </span>
            </button>
            <div class="flex flex-col space-y-2.5 w-[130px]">
              <button 
                @click="testVoice('gear_n')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                试听语音
              </button>
              <button 
                @click="openCustomVoice('gear_n', '空挡 N')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-sub hover:text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                声效设置
              </button>
            </div>
          </div>
        </div>
      </div>
    </FeatureCard>

    <!-- 2. 功能模式切换播报 (智能模式 / 舒适模式 / 经济模式 / 运动模式) -->
    <FeatureCard 
      title="2. 功能模式切换播报 (智能模式 / 舒适模式 / 经济模式 / 运动模式)"
      desc="全车 4 大功能模式均已配置专属晓晓温婉知性原声。内置【模式有人感知状态机】：默认智能模式静默，手动切出其他模式激活，切回智能模式播报后置 0 归位，杜绝蓝牙钥匙靠近唤醒误报！"
    >
      <div class="grid grid-cols-2 gap-4">
        <!-- 智能模式 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex flex-col justify-center pr-6 flex-1 min-w-0">
            <div class="flex items-center space-x-3 mb-1.5">
              <span class="text-[22px] font-black text-car-text tracking-wide">智能模式 (Smart)</span>
              <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-car-accent font-bold">默认省心</span>
            </div>
            <span class="text-[15px] text-car-sub font-bold leading-relaxed">吉利默认智能模式，切回播报一次后归零静默</span>
          </div>

          <div class="flex space-x-3 shrink-0 items-center">
            <button 
              @click="toggleSetting('voice_enable_mode_smart')"
              :class="[
                'w-[124px] h-[120px] rounded-2xl border-2 font-black transition-all flex flex-col items-center justify-center select-none cursor-pointer shadow-md',
                store.vehicleAuto.voice_enable_mode_smart 
                  ? 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-amber-500/10' 
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              <span class="text-[19.5px] font-black text-car-text tracking-wide mb-1">智能播报</span>
              <span :class="['text-[14px] font-bold', store.vehicleAuto.voice_enable_mode_smart ? 'text-car-accent' : 'text-car-sub']">
                {{ store.vehicleAuto.voice_enable_mode_smart ? '已开启' : '已关闭' }}
              </span>
            </button>
            <div class="flex flex-col space-y-2.5 w-[130px]">
              <button 
                @click="testVoice('mode_smart')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                试听模式
              </button>
              <button 
                @click="openCustomVoice('mode_smart', '智能模式')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-sub hover:text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                声效设置
              </button>
            </div>
          </div>
        </div>

        <!-- 舒适模式 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex flex-col justify-center pr-6 flex-1 min-w-0">
            <div class="flex items-center space-x-3 mb-1.5">
              <span class="text-[22px] font-black text-car-text tracking-wide">舒适模式 (Comfort)</span>
              <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-emerald-400 font-bold">平顺温润</span>
            </div>
            <span class="text-[15px] text-car-sub font-bold leading-relaxed">适合日常城市通勤，换挡平顺温润</span>
          </div>

          <div class="flex space-x-3 shrink-0 items-center">
            <button 
              @click="toggleSetting('voice_enable_mode_comfort')"
              :class="[
                'w-[124px] h-[120px] rounded-2xl border-2 font-black transition-all flex flex-col items-center justify-center select-none cursor-pointer shadow-md',
                store.vehicleAuto.voice_enable_mode_comfort 
                  ? 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-amber-500/10' 
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              <span class="text-[19.5px] font-black text-car-text tracking-wide mb-1">舒适播报</span>
              <span :class="['text-[14px] font-bold', store.vehicleAuto.voice_enable_mode_comfort ? 'text-car-accent' : 'text-car-sub']">
                {{ store.vehicleAuto.voice_enable_mode_comfort ? '已开启' : '已关闭' }}
              </span>
            </button>
            <div class="flex flex-col space-y-2.5 w-[130px]">
              <button 
                @click="testVoice('mode_comfort')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                试听模式
              </button>
              <button 
                @click="openCustomVoice('mode_comfort', '舒适模式')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-sub hover:text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                声效设置
              </button>
            </div>
          </div>
        </div>

        <!-- 经济模式 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex flex-col justify-center pr-6 flex-1 min-w-0">
            <div class="flex items-center space-x-3 mb-1.5">
              <span class="text-[22px] font-black text-car-text tracking-wide">经济模式 (Eco)</span>
              <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-blue-400 font-bold">低碳节能</span>
            </div>
            <span class="text-[15px] text-car-sub font-bold leading-relaxed">极致节油，长途巡航舒适惬意</span>
          </div>

          <div class="flex space-x-3 shrink-0 items-center">
            <button 
              @click="toggleSetting('voice_enable_mode_eco')"
              :class="[
                'w-[124px] h-[120px] rounded-2xl border-2 font-black transition-all flex flex-col items-center justify-center select-none cursor-pointer shadow-md',
                store.vehicleAuto.voice_enable_mode_eco 
                  ? 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-amber-500/10' 
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              <span class="text-[19.5px] font-black text-car-text tracking-wide mb-1">经济播报</span>
              <span :class="['text-[14px] font-bold', store.vehicleAuto.voice_enable_mode_eco ? 'text-car-accent' : 'text-car-sub']">
                {{ store.vehicleAuto.voice_enable_mode_eco ? '已开启' : '已关闭' }}
              </span>
            </button>
            <div class="flex flex-col space-y-2.5 w-[130px]">
              <button 
                @click="testVoice('mode_eco')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                试听模式
              </button>
              <button 
                @click="openCustomVoice('mode_eco', '经济模式')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-sub hover:text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                声效设置
              </button>
            </div>
          </div>
        </div>

        <!-- 运动模式 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex flex-col justify-center pr-6 flex-1 min-w-0">
            <div class="flex items-center space-x-3 mb-1.5">
              <span class="text-[22px] font-black text-car-text tracking-wide">运动模式 (Sport)</span>
              <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-car-accent font-bold">动力充沛</span>
            </div>
            <span class="text-[15px] text-car-sub font-bold leading-relaxed">油门激进，动力输出充沛激擎</span>
          </div>

          <div class="flex space-x-3 shrink-0 items-center">
            <button 
              @click="toggleSetting('voice_enable_mode_sport')"
              :class="[
                'w-[124px] h-[120px] rounded-2xl border-2 font-black transition-all flex flex-col items-center justify-center select-none cursor-pointer shadow-md',
                store.vehicleAuto.voice_enable_mode_sport 
                  ? 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-amber-500/10' 
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              <span class="text-[19.5px] font-black text-car-text tracking-wide mb-1">运动播报</span>
              <span :class="['text-[14px] font-bold', store.vehicleAuto.voice_enable_mode_sport ? 'text-car-accent' : 'text-car-sub']">
                {{ store.vehicleAuto.voice_enable_mode_sport ? '已开启' : '已关闭' }}
              </span>
            </button>
            <div class="flex flex-col space-y-2.5 w-[130px]">
              <button 
                @click="testVoice('mode_sport')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                试听模式
              </button>
              <button 
                @click="openCustomVoice('mode_sport', '运动模式')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-sub hover:text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                声效设置
              </button>
            </div>
          </div>
        </div>
      </div>
    </FeatureCard>

    <!-- 3. 四门迎宾与关门提醒 -->
    <FeatureCard 
      title="3. 四门迎宾与关门提醒 (状态翻转机 · 关门立断)"
      desc="100% 锁定吉利真实 MCU 串口物理报文 (91 02 01 b6)，支持【通用智能语音】与【独立分门自定义】双模式无缝切换。"
    >
      <!-- 模式切换选择栏 (车规大胶囊) -->
      <div class="flex items-center justify-between bg-car-item border border-car-border rounded-2xl p-4 mb-4 shadow-sm">
        <div class="flex flex-col">
          <span class="text-[18px] font-black text-car-text">车门语音播报模式</span>
          <span class="text-[14px] text-car-sub font-bold mt-0.5">
            {{ store.vehicleAuto.voice_door_mode_universal !== false ? '当前为通用智能车门语音 (内置上下车感知：登车关门提示就绪，离车开门提醒物品，行车开门危险报警)' : '当前为独立分门语音 (可为各门单独定制专属台词与音效)' }}
          </span>
        </div>
        <div class="flex space-x-2.5 shrink-0">
          <button 
            @click="setDoorMode(true)"
            :class="[
              'px-5 py-2.5 rounded-xl border-2 font-black text-[15px] transition-all cursor-pointer',
              store.vehicleAuto.voice_door_mode_universal !== false 
                ? 'bg-car-card border-car-accent text-car-text shadow-sm' 
                : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
            ]"
          >
            通用智能语音 (推荐)
          </button>
          <button 
            @click="setDoorMode(false)"
            :class="[
              'px-5 py-2.5 rounded-xl border-2 font-black text-[15px] transition-all cursor-pointer',
              store.vehicleAuto.voice_door_mode_universal === false 
                ? 'bg-car-card border-car-accent text-car-text shadow-sm' 
                : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
            ]"
          >
            独立分门语音
          </button>
        </div>
      </div>

      <!-- 模式 1: 通用智能车门语音 (默认首选) -->
      <div v-if="store.vehicleAuto.voice_door_mode_universal !== false" class="grid grid-cols-2 gap-4">
        <!-- 通用车门打开 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex flex-col justify-center pr-6 flex-1 min-w-0">
            <div class="flex items-center space-x-3 mb-1.5">
              <span class="text-[22px] font-black text-car-text tracking-wide">通用开门提醒</span>
              <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-car-accent font-bold">场景自适应</span>
            </div>
            <span class="text-[15px] text-car-sub font-bold leading-relaxed">登车开门提醒，停车下车温馨提醒带好物品，行车门开紧急报警</span>
          </div>
          <div class="flex space-x-3 shrink-0 items-center">
            <button 
              @click="toggleSetting('voice_enable_door_universal_open')"
              :class="[
                'w-[124px] h-[120px] rounded-2xl border-2 font-black transition-all flex flex-col items-center justify-center select-none cursor-pointer shadow-md',
                store.vehicleAuto.voice_enable_door_universal_open !== false 
                  ? 'bg-car-card border-car-accent text-car-text shadow-amber-500/10' 
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              <span class="text-[19.5px] font-black text-car-text tracking-wide mb-1">开门播报</span>
              <span :class="['text-[14px] font-bold', store.vehicleAuto.voice_enable_door_universal_open !== false ? 'text-car-accent' : 'text-car-sub']">
                {{ store.vehicleAuto.voice_enable_door_universal_open !== false ? '已开启' : '已关闭' }}
              </span>
            </button>
            <div class="flex flex-col space-y-2.5 w-[130px]">
              <button 
                @click="testVoice('door_open')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                试听语音
              </button>
              <button 
                @click="openCustomVoice('door_open', '通用开门提醒', 'door_open.mp3')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-sub hover:text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                声效设置
              </button>
            </div>
          </div>
        </div>

        <!-- 通用车门关好 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex flex-col justify-center pr-6 flex-1 min-w-0">
            <div class="flex items-center space-x-3 mb-1.5">
              <span class="text-[22px] font-black text-car-text tracking-wide">通用关门提醒</span>
              <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-emerald-400 font-bold">安全闭合</span>
            </div>
            <span class="text-[15px] text-car-sub font-bold leading-relaxed">车门闭合完毕干脆提示“车门已关好”，多门合并防抖</span>
          </div>
          <div class="flex space-x-3 shrink-0 items-center">
            <button 
              @click="toggleSetting('voice_enable_door_universal_close')"
              :class="[
                'w-[124px] h-[120px] rounded-2xl border-2 font-black transition-all flex flex-col items-center justify-center select-none cursor-pointer shadow-md',
                store.vehicleAuto.voice_enable_door_universal_close !== false 
                  ? 'bg-car-card border-car-accent text-car-text shadow-amber-500/10' 
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              <span class="text-[19.5px] font-black text-car-text tracking-wide mb-1">关门播报</span>
              <span :class="['text-[14px] font-bold', store.vehicleAuto.voice_enable_door_universal_close !== false ? 'text-car-accent' : 'text-car-sub']">
                {{ store.vehicleAuto.voice_enable_door_universal_close !== false ? '已开启' : '已关闭' }}
              </span>
            </button>
            <div class="flex flex-col space-y-2.5 w-[130px]">
              <button 
                @click="testVoice('door_close')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                试听语音
              </button>
              <button 
                @click="openCustomVoice('door_close', '通用关门提醒', 'door_close.mp3')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-sub hover:text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                声效设置
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 模式 2: 独立四门明细语音 (保留给车友自定义) -->
      <div v-else class="grid grid-cols-2 gap-4">
        <!-- 主驾 FL -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between shadow-sm">
          <div class="text-[20px] font-black text-car-text mb-3">主驾车门 (FL)</div>
          <div class="grid grid-cols-2 gap-2.5 mb-3">
            <MatrixButton 
              title="开门播报"
              :subtitle="store.vehicleAuto.voice_enable_door_fl ? '已开启' : '已关闭'"
              :active="store.vehicleAuto.voice_enable_door_fl"
              @click="toggleSetting('voice_enable_door_fl')"
            />
            <MatrixButton 
              title="关门播报"
              :subtitle="store.vehicleAuto.voice_enable_door_fl_close ? '已开启' : '已关闭'"
              :active="store.vehicleAuto.voice_enable_door_fl_close"
              @click="toggleSetting('voice_enable_door_fl_close')"
            />
          </div>
          <div class="flex space-x-3">
            <button 
              @click="testVoice('door_fl')"
              class="flex-1 min-h-[58px] bg-car-card border-2 border-car-border rounded-xl text-car-text font-black text-[17px] cursor-pointer hover:border-car-border-light shadow-sm"
            >
              试听开门语音
            </button>
            <button 
              @click="openCustomVoice('door_fl', '主驾车门')"
              class="flex-1 min-h-[58px] bg-car-card border-2 border-car-border text-car-sub hover:text-car-text font-black text-[17px] rounded-xl cursor-pointer hover:border-car-border-light shadow-sm"
            >
              ⚙️ 声效设置
            </button>
          </div>
        </div>

        <!-- 副驾 FR -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between shadow-sm">
          <div class="text-[20px] font-black text-car-text mb-3">副驾车门 (FR)</div>
          <div class="grid grid-cols-2 gap-2.5 mb-3">
            <MatrixButton 
              title="开门播报"
              :subtitle="store.vehicleAuto.voice_enable_door_fr ? '已开启' : '已关闭'"
              :active="store.vehicleAuto.voice_enable_door_fr"
              @click="toggleSetting('voice_enable_door_fr')"
            />
            <MatrixButton 
              title="关门播报"
              :subtitle="store.vehicleAuto.voice_enable_door_fr_close ? '已开启' : '已关闭'"
              :active="store.vehicleAuto.voice_enable_door_fr_close"
              @click="toggleSetting('voice_enable_door_fr_close')"
            />
          </div>
          <div class="flex space-x-3">
            <button 
              @click="testVoice('door_fr')"
              class="flex-1 min-h-[58px] bg-car-card border-2 border-car-border rounded-xl text-car-text font-black text-[17px] cursor-pointer hover:border-car-border-light shadow-sm"
            >
              试听开门语音
            </button>
            <button 
              @click="openCustomVoice('door_fr', '副驾车门')"
              class="flex-1 min-h-[58px] bg-car-card border-2 border-car-border text-car-sub hover:text-car-text font-black text-[17px] rounded-xl cursor-pointer hover:border-car-border-light shadow-sm"
            >
              ⚙️ 声效设置
            </button>
          </div>
        </div>

        <!-- 左后 RL -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between shadow-sm">
          <div class="text-[20px] font-black text-car-text mb-3">左后车门 (RL)</div>
          <div class="grid grid-cols-2 gap-2.5 mb-3">
            <MatrixButton 
              title="开门播报"
              :subtitle="store.vehicleAuto.voice_enable_door_rl ? '已开启' : '已关闭'"
              :active="store.vehicleAuto.voice_enable_door_rl"
              @click="toggleSetting('voice_enable_door_rl')"
            />
            <MatrixButton 
              title="关门播报"
              :subtitle="store.vehicleAuto.voice_enable_door_rl_close ? '已开启' : '已关闭'"
              :active="store.vehicleAuto.voice_enable_door_rl_close"
              @click="toggleSetting('voice_enable_door_rl_close')"
            />
          </div>
          <div class="flex space-x-3">
            <button 
              @click="testVoice('door_rl')"
              class="flex-1 min-h-[58px] bg-car-card border-2 border-car-border rounded-xl text-car-text font-black text-[17px] cursor-pointer hover:border-car-border-light shadow-sm"
            >
              试听开门语音
            </button>
            <button 
              @click="openCustomVoice('door_rl', '左后车门')"
              class="flex-1 min-h-[58px] bg-car-card border-2 border-car-border text-car-sub hover:text-car-text font-black text-[17px] rounded-xl cursor-pointer hover:border-car-border-light shadow-sm"
            >
              ⚙️ 声效设置
            </button>
          </div>
        </div>

        <!-- 右后 RR -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between shadow-sm">
          <div class="text-[20px] font-black text-car-text mb-3">右后车门 (RR)</div>
          <div class="grid grid-cols-2 gap-2.5 mb-3">
            <MatrixButton 
              title="开门播报"
              :subtitle="store.vehicleAuto.voice_enable_door_rr ? '已开启' : '已关闭'"
              :active="store.vehicleAuto.voice_enable_door_rr"
              @click="toggleSetting('voice_enable_door_rr')"
            />
            <MatrixButton 
              title="关门播报"
              :subtitle="store.vehicleAuto.voice_enable_door_rr_close ? '已开启' : '已关闭'"
              :active="store.vehicleAuto.voice_enable_door_rr_close"
              @click="toggleSetting('voice_enable_door_rr_close')"
            />
          </div>
          <div class="flex space-x-3">
            <button 
              @click="testVoice('door_rr')"
              class="flex-1 min-h-[58px] bg-car-card border-2 border-car-border rounded-xl text-car-text font-black text-[17px] cursor-pointer hover:border-car-border-light shadow-sm"
            >
              试听开门语音
            </button>
            <button 
              @click="openCustomVoice('door_rr', '右后车门')"
              class="flex-1 min-h-[58px] bg-car-card border-2 border-car-border text-car-sub hover:text-car-text font-black text-[17px] rounded-xl cursor-pointer hover:border-car-border-light shadow-sm"
            >
              ⚙️ 声效设置
            </button>
          </div>
        </div>
      </div>
    </FeatureCard>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';
import { store, bridge, openModal, showToast } from '../store';

const selectedModelId = ref(localStorage.getItem('geely_vehicle_model') || 'binyue_cool');
const doorStatus = ref({ fl: -1, fr: -1, rl: -1, rr: -1, trunk: -1, gear: -1, mode: -1 });
let doorPollTimer = null;

function formatGearName(gear) {
  switch (gear) {
    case 2: return 'D 挡 (前进挡)';
    case 3: return 'N 挡 (空挡)';
    case 4: return 'R 挡 (倒车挡)';
    case 5: return 'P 挡 (驻车挡)';
    case 6:
    case 7: return 'S 挡 (运动挡)';
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

onMounted(() => {
  fetchDoorStatus();
  doorPollTimer = setInterval(fetchDoorStatus, 800);
});

onUnmounted(() => {
  if (doorPollTimer) clearInterval(doorPollTimer);
});

const supportedModels = [
  { id: 'binyue_cool', label: '吉利缤越 COOL (E02 / IHU516G)', name: '吉利缤越 COOL', isPrimary: true },
  { id: 'binrui_cool', label: '吉利缤瑞 COOL (E02 架构)', name: '吉利缤瑞 COOL', isPrimary: false },
  { id: 'boyue_e02', label: '吉利博越 / 豪越 (E02 SWOS)', name: '吉利博越/豪越', isPrimary: false },
  { id: 'dihao_swos', label: '吉利帝豪 (Android 9 SWOS)', name: '吉利帝豪', isPrimary: false }
];

const currentModelSpec = computed(() => {
  return supportedModels.find(m => m.id === selectedModelId.value) || supportedModels[0];
});

function setDoorMode(isUniversal) {
  store.vehicleAuto.voice_door_mode_universal = isUniversal;
  bridge.call('setVehicleAutomationSetting', 'voice_door_mode_universal', isUniversal);
  showToast(isUniversal ? '已切换为: 通用智能车门语音 (推荐)' : '已切换为: 独立分门明细语音');
}

function selectCarModel(id) {
  selectedModelId.value = id;
  localStorage.setItem('geely_vehicle_model', id);
  bridge.call('setVehicleAutomationStringSetting', 'vehicle_target_model', id);
  showToast(`已切换至【${currentModelSpec.value.name}】专车适配协议`);
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

function openCustomVoice(key, title, soundFile) {
  openModal('voiceItemSettings', {
    key,
    title,
    soundFile: soundFile || (key + '.mp3')
  });
}
</script>
