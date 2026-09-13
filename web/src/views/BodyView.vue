<template>
  <div class="flex flex-col space-y-6">
    <!-- 1. 座舱车身语音播报总开关 (整卡一体化开关：整卡即触控大靶区 · 右侧纯文字) -->
    <div 
      @click="toggleVoiceMasterSwitch"
      class="rounded-3xl border-2 border-car-border hover:border-car-border-light bg-car-card p-6 shadow-xl transition-all duration-200 cursor-pointer flex items-center justify-between"
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
          一键管控全车车门、尾门、挡位与驾驶模式语音播报。关闭后全车物理静音，独立生效。
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

    <!-- 3. 座舱语音计划任务工作台 (场景工坊) -->
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
          将繁杂的二十余项播报开关收拢为四大场景计划。不需要的计划可一键移除，点击二级向导可独立试听与配置自定义台词/音效。
        </span>
      </div>
      <div class="flex items-center space-x-3 shrink-0">
        <!-- 核心打通：一键跳转车载音频语音主题包与TTS大厅 -->
        <button
          @click="store.currentNav = 'audio'"
          class="h-[54px] px-6 rounded-2xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text hover:text-car-accent font-black text-[16px] cursor-pointer shadow-sm flex items-center space-x-2 transition-all"
        >
          <span>整套语音主题包 ➔</span>
        </button>
        <button
          @click="openAddVoiceModal"
          class="h-[54px] px-6 rounded-2xl bg-car-item border-2 border-car-accent hover:border-car-accent-light text-car-accent font-black text-[16px] cursor-pointer shadow-md flex items-center space-x-2 transition-all"
        >
          <span>添加语音计划</span>
          <span v-if="removedVoiceTaskIds.length > 0" class="px-2 py-0.5 text-[12.5px] bg-car-card rounded-full border border-car-accent/40 text-car-text">
            {{ removedVoiceTaskIds.length }} 项待添加
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

    <!-- 语音任务流列表 (车规双列网格 2x2 · 告别单列堆叠面条 · 一屏尽览四大场景) -->
    <div class="grid grid-cols-2 gap-5">
      <!-- 语音任务 1: 挡位安全播报计划 -->
      <div 
        v-if="isVoiceTaskVisible('gear_voice')"
        class="rounded-3xl border-2 border-car-border hover:border-car-border-light bg-car-card p-6 shadow-xl flex flex-col justify-between space-y-4 transition-all duration-200"
      >
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-3">
            <span class="text-[21px] font-black text-car-text tracking-wide">1. 换挡有人感知语音计划</span>
            <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent shrink-0">换挡安全</span>
            <button 
              @click.stop="showGearHelp"
              class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-accent hover:border-car-accent font-black text-[18px] flex items-center justify-center cursor-pointer shadow-sm transition-transform active:scale-95 shrink-0"
            >
              ?
            </button>
          </div>
          <button 
            @click="removeVoiceTask('gear_voice', '换挡语音计划')"
            class="h-[50px] px-4 rounded-xl bg-car-item border border-car-border hover:border-red-500/80 text-car-sub hover:text-red-400 font-black text-[14px] cursor-pointer shadow-sm transition-all shrink-0"
            title="从工作台移除"
          >
            移除
          </button>
        </div>

        <!-- 逻辑说明：去套娃化，纯净单行流向，彻底告别嵌套灰框 -->
        <div class="flex flex-col space-y-2 py-1">
          <div class="flex items-start space-x-2.5">
            <span class="px-2.5 py-0.5 rounded-xl bg-car-item border border-car-border text-car-accent text-[13.5px] font-black shrink-0">当</span>
            <span class="text-[15px] font-bold text-car-text leading-relaxed">踩刹车挂入 D / R / N 挡，或从行车切回 P 挡驻车</span>
          </div>
          <div class="flex items-start space-x-2.5">
            <span class="px-2.5 py-0.5 rounded-xl bg-car-item border border-car-accent/40 text-car-accent text-[13.5px] font-black shrink-0">就</span>
            <span class="text-[15px] font-bold text-car-text leading-relaxed">清晰播报挡位状态，支持自定义台词与音频混搭</span>
          </div>
        </div>

        <!-- 底部车规双大按钮：左开关 + 右配置 -->
        <div class="grid grid-cols-2 gap-3 pt-2 border-t border-car-border/60">
          <button
            @click="toggleAllGearVoice"
            :class="[
              'h-[52px] px-4 rounded-2xl font-black text-[16px] cursor-pointer transition-all border-2 flex items-center justify-center space-x-2 shadow-sm',
              isGearVoicePlanActive
                ? 'bg-car-item border-car-accent text-car-text shadow-md'
                : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            <span :class="['w-2.5 h-2.5 rounded-full', isGearVoicePlanActive ? 'bg-car-accent shadow-[0_0_6px_var(--accent-gold)]' : 'bg-car-sub']"></span>
            <span class="truncate">{{ isGearVoicePlanActive ? '计划运行中' : '计划已暂停' }}</span>
          </button>
          <button 
            @click="openGearConfigModal"
            class="h-[52px] px-4 rounded-2xl bg-car-item border-2 border-car-accent hover:border-car-accent text-car-accent font-black text-[16px] cursor-pointer shadow-sm transition-all flex items-center justify-center space-x-1"
          >
            <span>挡位细分配置</span>
            <span>➔</span>
          </button>
        </div>
      </div>

      <!-- 语音任务 2: 驾驶模式旋钮播报计划 -->
      <div 
        v-if="isVoiceTaskVisible('mode_voice')"
        class="rounded-3xl border-2 border-car-border hover:border-car-border-light bg-car-card p-6 shadow-xl flex flex-col justify-between space-y-4 transition-all duration-200"
      >
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-3">
            <span class="text-[21px] font-black text-car-text tracking-wide">2. 驾驶模式旋钮切换计划</span>
            <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent shrink-0">旋钮激擎</span>
            <button 
              @click.stop="showModeHelp"
              class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-accent hover:border-car-accent font-black text-[18px] flex items-center justify-center cursor-pointer shadow-sm transition-transform active:scale-95 shrink-0"
            >
              ?
            </button>
          </div>
          <button 
            @click="removeVoiceTask('mode_voice', '驾驶模式计划')"
            class="h-[50px] px-4 rounded-xl bg-car-item border border-car-border hover:border-red-500/80 text-car-sub hover:text-red-400 font-black text-[14px] cursor-pointer shadow-sm transition-all shrink-0"
            title="从工作台移除"
          >
            移除
          </button>
        </div>

        <div class="flex flex-col space-y-2 py-1">
          <div class="flex items-start space-x-2.5">
            <span class="px-2.5 py-0.5 rounded-xl bg-car-item border border-car-border text-car-accent text-[13.5px] font-black shrink-0">当</span>
            <span class="text-[15px] font-bold text-car-text leading-relaxed">中控模式旋钮转动切换至舒适、经济、运动或智能</span>
          </div>
          <div class="flex items-start space-x-2.5">
            <span class="px-2.5 py-0.5 rounded-xl bg-car-item border border-car-accent/40 text-car-accent text-[13.5px] font-black shrink-0">就</span>
            <span class="text-[15px] font-bold text-car-text leading-relaxed">晓晓知性声线发声，点亮对应氛围 (含 160ms 防抖)</span>
          </div>
        </div>

        <div class="grid grid-cols-2 gap-3 pt-2 border-t border-car-border/60">
          <button
            @click="toggleAllModeVoice"
            :class="[
              'h-[52px] px-4 rounded-2xl font-black text-[16px] cursor-pointer transition-all border-2 flex items-center justify-center space-x-2 shadow-sm',
              isModeVoicePlanActive
                ? 'bg-car-item border-car-accent text-car-text shadow-md'
                : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            <span :class="['w-2.5 h-2.5 rounded-full', isModeVoicePlanActive ? 'bg-car-accent shadow-[0_0_6px_var(--accent-gold)]' : 'bg-car-sub']"></span>
            <span class="truncate">{{ isModeVoicePlanActive ? '模式计划运行中' : '模式计划已暂停' }}</span>
          </button>
          <button 
            @click="openModeConfigModal"
            class="h-[52px] px-4 rounded-2xl bg-car-item border-2 border-car-accent hover:border-car-accent text-car-accent font-black text-[16px] cursor-pointer shadow-sm transition-all flex items-center justify-center space-x-1"
          >
            <span>模式细分配置</span>
            <span>➔</span>
          </button>
        </div>
      </div>

      <!-- 语音任务 3: 四门迎宾与关门安全播报计划 -->
      <div 
        v-if="isVoiceTaskVisible('door_voice')"
        class="rounded-3xl border-2 border-car-border hover:border-car-border-light bg-car-card p-6 shadow-xl flex flex-col justify-between space-y-4 transition-all duration-200"
      >
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-3">
            <span class="text-[21px] font-black text-car-text tracking-wide">3. 四门迎宾与关门安全计划</span>
            <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent shrink-0">五门防抖</span>
            <button 
              @click.stop="showDoorHelp"
              class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-accent hover:border-car-accent font-black text-[18px] flex items-center justify-center cursor-pointer shadow-sm transition-transform active:scale-95 shrink-0"
            >
              ?
            </button>
          </div>
          <button 
            @click="removeVoiceTask('door_voice', '车门语音计划')"
            class="h-[50px] px-4 rounded-xl bg-car-item border border-car-border hover:border-red-500/80 text-car-sub hover:text-red-400 font-black text-[14px] cursor-pointer shadow-sm transition-all shrink-0"
            title="从工作台移除"
          >
            移除
          </button>
        </div>

        <div class="flex flex-col space-y-2 py-1">
          <div class="flex items-start space-x-2.5">
            <span class="px-2.5 py-0.5 rounded-xl bg-car-item border border-car-border text-car-accent text-[13.5px] font-black shrink-0">当</span>
            <span class="text-[15px] font-bold text-car-text leading-relaxed">主驾、副驾、后排车门开启或关好 (防抖合并)</span>
          </div>
          <div class="flex items-start space-x-2.5">
            <span class="px-2.5 py-0.5 rounded-xl bg-car-item border border-car-accent/40 text-car-accent text-[13.5px] font-black shrink-0">就</span>
            <span class="text-[15px] font-bold text-car-text leading-relaxed">通用「车门已打开/关好」或独立分门，支持小爱定制</span>
          </div>
        </div>

        <div class="grid grid-cols-2 gap-3 pt-2 border-t border-car-border/60">
          <button
            @click="toggleAllDoorVoice"
            :class="[
              'h-[52px] px-4 rounded-2xl font-black text-[16px] cursor-pointer transition-all border-2 flex items-center justify-center space-x-2 shadow-sm',
              isDoorVoicePlanActive
                ? 'bg-car-item border-car-accent text-car-text shadow-md'
                : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            <span :class="['w-2.5 h-2.5 rounded-full', isDoorVoicePlanActive ? 'bg-car-accent shadow-[0_0_6px_var(--accent-gold)]' : 'bg-car-sub']"></span>
            <span class="truncate">{{ isDoorVoicePlanActive ? '车门计划运行中' : '车门计划已暂停' }}</span>
          </button>
          <button 
            @click="openDoorConfigModal"
            class="h-[52px] px-4 rounded-2xl bg-car-item border-2 border-car-accent hover:border-car-accent text-car-accent font-black text-[16px] cursor-pointer shadow-sm transition-all flex items-center justify-center space-x-1"
          >
            <span>车门详细配置</span>
            <span>➔</span>
          </button>
        </div>
      </div>

      <!-- 语音任务 4: 原厂电动尾门安全播报计划 -->
      <div 
        v-if="isVoiceTaskVisible('trunk_voice')"
        class="rounded-3xl border-2 border-car-border hover:border-car-border-light bg-car-card p-6 shadow-xl flex flex-col justify-between space-y-4 transition-all duration-200"
      >
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-3">
            <span class="text-[21px] font-black text-car-text tracking-wide">4. 原厂电动尾门安全计划</span>
            <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent shrink-0">尾门防碰</span>
            <button 
              @click.stop="showTrunkHelp"
              class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-accent hover:border-car-accent font-black text-[18px] flex items-center justify-center cursor-pointer shadow-sm transition-transform active:scale-95 shrink-0"
            >
              ?
            </button>
          </div>
          <button 
            @click="removeVoiceTask('trunk_voice', '尾门语音计划')"
            class="h-[50px] px-4 rounded-xl bg-car-item border border-car-border hover:border-red-500/80 text-car-sub hover:text-red-400 font-black text-[14px] cursor-pointer shadow-sm transition-all shrink-0"
            title="从工作台移除"
          >
            移除
          </button>
        </div>

        <div class="flex flex-col space-y-2 py-1">
          <div class="flex items-start space-x-2.5">
            <span class="px-2.5 py-0.5 rounded-xl bg-car-item border border-car-border text-car-accent text-[13.5px] font-black shrink-0">当</span>
            <span class="text-[15px] font-bold text-car-text leading-relaxed">电动尾门按键触发升起，或锁扣电机下落闭锁确认</span>
          </div>
          <div class="flex items-start space-x-2.5">
            <span class="px-2.5 py-0.5 rounded-xl bg-car-item border border-car-accent/40 text-car-accent text-[13.5px] font-black shrink-0">就</span>
            <span class="text-[15px] font-bold text-car-text leading-relaxed">播报升起防刮蹭警示与落锁提示，支持专属定制</span>
          </div>
        </div>

        <div class="grid grid-cols-2 gap-3 pt-2 border-t border-car-border/60">
          <button
            @click="toggleAllTrunkVoice"
            :class="[
              'h-[52px] px-4 rounded-2xl font-black text-[16px] cursor-pointer transition-all border-2 flex items-center justify-center space-x-2 shadow-sm',
              isTrunkVoicePlanActive
                ? 'bg-car-item border-car-accent text-car-text shadow-md'
                : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            <span :class="['w-2.5 h-2.5 rounded-full', isTrunkVoicePlanActive ? 'bg-car-accent shadow-[0_0_6px_var(--accent-gold)]' : 'bg-car-sub']"></span>
            <span class="truncate">{{ isTrunkVoicePlanActive ? '尾门计划运行中' : '尾门计划已暂停' }}</span>
          </button>
          <button 
            @click="openTrunkConfigModal"
            class="h-[52px] px-4 rounded-2xl bg-car-item border-2 border-car-accent hover:border-car-accent text-car-accent font-black text-[16px] cursor-pointer shadow-sm transition-all flex items-center justify-center space-x-1"
          >
            <span>尾门详细配置</span>
            <span>➔</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 弹窗 1: 挡位细分配置二级向导 (GearConfigModal · 全量补齐声效设置) -->
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
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('gear_d')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('gear_d', '前进挡 (D 挡)', 'gear_d.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_gear_d')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
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
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('gear_r')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('gear_r', '倒车挡 (R 挡)', 'gear_r.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_gear_r')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
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
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('gear_p')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('gear_p', '驻车挡 (P 挡)', 'gear_p.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_gear_p')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
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
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('gear_n')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('gear_n', '空挡 (N 挡)', 'gear_n.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_gear_n')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
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

    <!-- 弹窗 2: 驾驶模式细分配置二级向导 (ModeConfigModal · 全量补齐声效设置) -->
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
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('mode_comfort')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('mode_comfort', '舒适模式', 'mode_comfort.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_mode_comfort')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
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
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('mode_eco')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('mode_eco', '经济模式', 'mode_eco.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_mode_eco')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
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
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('mode_sport')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('mode_sport', '运动模式', 'mode_sport.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_mode_sport')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
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
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('mode_smart')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('mode_smart', '智能模式', 'mode_smart.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_mode_smart')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
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

    <!-- 弹窗 3: 四门迎宾与关门安全二级向导 (DoorConfigModal · 通用/分门全量完好) -->
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

        <!-- 模式单选切换 -->
        <div class="flex items-center space-x-3 p-4 bg-car-item rounded-2xl border border-car-border">
          <button 
            @click="setDoorMode(true)"
            :class="[
              'flex-1 h-[52px] rounded-xl font-black text-[15.5px] cursor-pointer transition-all border-2',
              store.vehicleAuto.voice_door_mode_universal !== false
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
              store.vehicleAuto.voice_door_mode_universal === false
                ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            独立分门专属台词模式
          </button>
        </div>

        <!-- 模式 1: 通用智能车门语音 (推荐 · 合并防抖) -->
        <div v-if="store.vehicleAuto.voice_door_mode_universal !== false" class="flex flex-col space-y-3.5">
          <!-- 通用开门提醒 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <div class="flex items-center space-x-2">
                <span class="text-[19px] font-black text-car-text">通用开门提醒</span>
                <span class="px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-car-accent text-[12px] font-bold">场景自适应</span>
              </div>
              <span class="text-[13px] text-car-sub font-bold">登车开门提醒启程，停车下车提醒带好随身物品，行车意外开门紧急报警</span>
            </div>
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('door_open')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('door_open', '通用开门提醒', 'door_open.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_door_universal_open')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_door_universal_open !== false ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_door_universal_open !== false ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>

          <!-- 通用关门提醒 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <div class="flex items-center space-x-2">
                <span class="text-[19px] font-black text-car-text">通用关门提醒</span>
                <span class="px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-emerald-400 text-[12px] font-bold">安全闭合</span>
              </div>
              <span class="text-[13px] text-car-sub font-bold">车门闭合完毕干脆提示“车门已关好”，多门同时动作合并防抖</span>
            </div>
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('door_close')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('door_close', '通用关门提醒', 'door_close.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_door_universal_close')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_door_universal_close !== false ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_door_universal_close !== false ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 模式 2: 独立分门专属台词模式 (4 门开闭完整 8 组独立控制) -->
        <div v-else class="grid grid-cols-2 gap-4">
          <!-- 主驾 FL -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3">
            <div class="flex items-center justify-between border-b border-car-border/50 pb-2">
              <span class="text-[18px] font-black text-car-text">主驾车门 (FL)</span>
              <button 
                @click="openCustomVoice('door_fl', '主驾车门')"
                class="h-[50px] px-3.5 rounded-xl bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[13.5px] cursor-pointer"
              >
                声效设置
              </button>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">开门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="testVoice('door_fl')" class="h-[50px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[13.5px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_fl')" :class="['h-[50px] px-3.5 rounded-lg border-2 font-black text-[13.5px] cursor-pointer', store.vehicleAuto.voice_enable_door_fl ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_fl ? '开启' : '关闭' }}</button>
              </div>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">关门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="testVoice('door_fl_close')" class="h-[50px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[13.5px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_fl_close')" :class="['h-[50px] px-3.5 rounded-lg border-2 font-black text-[13.5px] cursor-pointer', store.vehicleAuto.voice_enable_door_fl_close ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_fl_close ? '开启' : '关闭' }}</button>
              </div>
            </div>
          </div>

          <!-- 副驾 FR -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3">
            <div class="flex items-center justify-between border-b border-car-border/50 pb-2">
              <span class="text-[18px] font-black text-car-text">副驾车门 (FR)</span>
              <button 
                @click="openCustomVoice('door_fr', '副驾车门')"
                class="h-[50px] px-3.5 rounded-xl bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[13.5px] cursor-pointer"
              >
                声效设置
              </button>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">开门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="testVoice('door_fr')" class="h-[50px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[13.5px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_fr')" :class="['h-[50px] px-3.5 rounded-lg border-2 font-black text-[13.5px] cursor-pointer', store.vehicleAuto.voice_enable_door_fr ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_fr ? '开启' : '关闭' }}</button>
              </div>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">关门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="testVoice('door_fr_close')" class="h-[50px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[13.5px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_fr_close')" :class="['h-[50px] px-3.5 rounded-lg border-2 font-black text-[13.5px] cursor-pointer', store.vehicleAuto.voice_enable_door_fr_close ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_fr_close ? '开启' : '关闭' }}</button>
              </div>
            </div>
          </div>

          <!-- 左后 RL -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3">
            <div class="flex items-center justify-between border-b border-car-border/50 pb-2">
              <span class="text-[18px] font-black text-car-text">左后车门 (RL)</span>
              <button 
                @click="openCustomVoice('door_rl', '左后车门')"
                class="h-[50px] px-3.5 rounded-xl bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[13.5px] cursor-pointer"
              >
                声效设置
              </button>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">开门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="testVoice('door_rl')" class="h-[50px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[13.5px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_rl')" :class="['h-[50px] px-3.5 rounded-lg border-2 font-black text-[13.5px] cursor-pointer', store.vehicleAuto.voice_enable_door_rl ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_rl ? '开启' : '关闭' }}</button>
              </div>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">关门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="testVoice('door_rl_close')" class="h-[50px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[13.5px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_rl_close')" :class="['h-[50px] px-3.5 rounded-lg border-2 font-black text-[13.5px] cursor-pointer', store.vehicleAuto.voice_enable_door_rl_close ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_rl_close ? '开启' : '关闭' }}</button>
              </div>
            </div>
          </div>

          <!-- 右后 RR -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3">
            <div class="flex items-center justify-between border-b border-car-border/50 pb-2">
              <span class="text-[18px] font-black text-car-text">右后车门 (RR)</span>
              <button 
                @click="openCustomVoice('door_rr', '右后车门')"
                class="h-[50px] px-3.5 rounded-xl bg-car-card border border-car-border text-car-sub hover:text-car-text font-bold text-[13.5px] cursor-pointer"
              >
                声效设置
              </button>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">开门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="testVoice('door_rr')" class="h-[50px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[13.5px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_rr')" :class="['h-[50px] px-3.5 rounded-lg border-2 font-black text-[13.5px] cursor-pointer', store.vehicleAuto.voice_enable_door_rr ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_rr ? '开启' : '关闭' }}</button>
              </div>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-[14px] text-car-sub font-bold">关门播报</span>
              <div class="flex items-center space-x-2">
                <button @click="testVoice('door_rr_close')" class="h-[50px] px-3.5 rounded-lg bg-car-card border border-car-border text-car-text font-bold text-[13.5px] cursor-pointer">试听</button>
                <button @click="toggleSetting('voice_enable_door_rr_close')" :class="['h-[50px] px-3.5 rounded-lg border-2 font-black text-[13.5px] cursor-pointer', store.vehicleAuto.voice_enable_door_rr_close ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub']">{{ store.vehicleAuto.voice_enable_door_rr_close ? '开启' : '关闭' }}</button>
              </div>
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

    <!-- 弹窗 4: 原厂电动尾门细分配置二级向导 (TrunkConfigModal · 补齐开闭与声效设置) -->
    <div 
      v-if="showTrunkModal" 
      class="fixed z-[9998] flex items-center justify-center bg-black/75 p-6"
      style="top:0; left:0; width:100vw; height:100vh;"
    >
      <div class="bg-car-card border-2 border-car-border rounded-3xl p-7 shadow-2xl max-w-[880px] w-full flex flex-col space-y-5 max-h-[90vh] overflow-y-auto">
        <div class="flex items-center justify-between border-b border-car-border pb-4">
          <div class="flex items-center space-x-3">
            <span class="w-3 h-3 rounded-full bg-car-accent"></span>
            <span class="text-[22px] font-black text-car-text">原厂电动尾门安全播报详细配置</span>
          </div>
          <button 
            @click="showTrunkModal = false"
            class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-sub hover:text-car-text font-black text-[20px] flex items-center justify-center cursor-pointer"
          >
            ✕
          </button>
        </div>

        <div class="flex flex-col space-y-3.5">
          <!-- 尾门升起提醒 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <div class="flex items-center space-x-2">
                <span class="text-[19px] font-black text-car-text">尾门升起提醒</span>
                <span class="px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-car-accent text-[12px] font-bold">防碰防刮</span>
              </div>
              <span class="text-[13px] text-car-sub font-bold">后备箱抬起升起时短促提醒，防止碰擦低矮车库顶梁</span>
            </div>
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('trunk_open')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('trunk_open', '尾门升起提醒', 'trunk_open.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_trunk_open')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_trunk_open ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_trunk_open ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>

          <!-- 尾门完全锁止 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex items-center justify-between">
            <div class="flex flex-col space-y-1">
              <div class="flex items-center space-x-2">
                <span class="text-[19px] font-black text-car-text">尾门完全锁止</span>
                <span class="px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-emerald-400 text-[12px] font-bold">锁闭就绪</span>
              </div>
              <span class="text-[13px] text-car-sub font-bold">后备箱电吸闭合完全锁止时短促播报“后备箱已关好”，关后备箱无需回头确认</span>
            </div>
            <div class="flex items-center space-x-2 shrink-0">
              <button 
                @click="testVoice('trunk_close')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm"
              >
                试听
              </button>
              <button 
                @click="openCustomVoice('trunk_close', '尾门完全锁止', 'trunk_close.mp3')"
                class="h-[52px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-sub hover:text-car-text font-black text-[14px] cursor-pointer shadow-sm transition-all"
              >
                声效设置
              </button>
              <button 
                @click="toggleSetting('voice_enable_trunk_close')"
                :class="[
                  'h-[52px] px-3.5 rounded-xl border-2 font-black text-[14px] cursor-pointer shadow-sm',
                  store.vehicleAuto.voice_enable_trunk_close ? 'bg-car-card border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'
                ]"
              >
                {{ store.vehicleAuto.voice_enable_trunk_close ? '已开启' : '已关闭' }}
              </button>
            </div>
          </div>
        </div>

        <div class="flex justify-end pt-3 border-t border-car-border">
          <button 
            @click="showTrunkModal = false"
            class="h-[52px] px-8 rounded-xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[16px] cursor-pointer"
          >
            完成配置
          </button>
        </div>
      </div>
    </div>

    <!-- 弹窗 5: 添加语音任务至工作台 (AddVoiceModal) -->
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
            v-for="task in allVoiceTasks" 
            :key="task.id"
            class="p-4 rounded-2xl bg-car-item border-2 border-car-border hover:border-car-border-light flex items-center justify-between transition-all"
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
              v-if="removedVoiceTaskIds.includes(task.id)"
              @click="restoreVoiceTask(task.id)"
              class="h-[52px] px-6 rounded-xl bg-car-card border-2 border-car-accent text-car-accent font-black text-[15px] cursor-pointer hover:bg-car-item whitespace-nowrap shadow-sm"
            >
              加入工作台
            </button>
            <div 
              v-else
              class="h-[52px] px-5 rounded-xl bg-car-card border border-car-border text-car-sub font-black text-[14px] flex items-center space-x-1.5 whitespace-nowrap select-none"
            >
              <span class="w-2 h-2 rounded-full bg-car-accent"></span>
              <span>已在工作台中</span>
            </div>
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
const showTrunkModal = ref(false);

const isProbeExpanded = ref(false);
const doorStatus = ref({ fl: -1, fr: -1, rl: -1, rr: -1, trunk: -1, gear: -1, mode: -1 });
let doorPollTimer = null;

const allVoiceTasks = [
  { id: 'gear_voice', title: '换挡有人感知语音播报计划', tag: '换挡安全', desc: '踩刹车挂入 D/R/P 挡位有人感知播报，N 挡空挡默认关防频繁打扰。' },
  { id: 'mode_voice', title: '驾驶模式旋钮切换播报计划', tag: '旋钮激擎', desc: '中控旋钮切入经济/舒适/运动/智能模式温润发声，带 160ms 防抖。' },
  { id: 'door_voice', title: '四门迎宾与关门安全播报计划', tag: '五门联动', desc: '智能车门合并防抖播报，支持通用智能车门与独立分门模式。' },
  { id: 'trunk_voice', title: '原厂电动尾门安全播报计划', tag: '尾门防碰', desc: '后备箱升起防刮顶梁提醒，电吸完全锁止干脆确认“后备箱已关好”。' }
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
  if (store.vehicleAuto.voice_door_mode_universal !== false) {
    return !!(store.vehicleAuto.voice_enable_door_universal_open !== false || store.vehicleAuto.voice_enable_door_universal_close !== false);
  }
  return !!(store.vehicleAuto.voice_enable_door_fl || store.vehicleAuto.voice_enable_door_fr || store.vehicleAuto.voice_enable_door_rl || store.vehicleAuto.voice_enable_door_rr);
});

const isTrunkVoicePlanActive = computed(() => {
  return !!(store.vehicleAuto.voice_enable_trunk_open || store.vehicleAuto.voice_enable_trunk_close);
});

const activeVoiceTaskCount = computed(() => {
  let count = 0;
  if (isVoiceTaskVisible('gear_voice') && isGearVoicePlanActive.value) count++;
  if (isVoiceTaskVisible('mode_voice') && isModeVoicePlanActive.value) count++;
  if (isVoiceTaskVisible('door_voice') && isDoorVoicePlanActive.value) count++;
  if (isVoiceTaskVisible('trunk_voice') && isTrunkVoicePlanActive.value) count++;
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
  store.vehicleAuto.voice_enable_door_universal_open = next;
  store.vehicleAuto.voice_enable_door_universal_close = next;
  store.vehicleAuto.voice_enable_door_fl = next;
  store.vehicleAuto.voice_enable_door_fr = next;
  store.vehicleAuto.voice_enable_door_rl = next;
  store.vehicleAuto.voice_enable_door_rr = next;
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_universal_open', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_universal_close', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_fl', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_fr', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_rl', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_door_rr', next);
  showToast(next ? '车门播报计划已开启' : '车门播报计划已暂停');
}

function toggleAllTrunkVoice() {
  const next = !isTrunkVoicePlanActive.value;
  store.vehicleAuto.voice_enable_trunk_open = next;
  store.vehicleAuto.voice_enable_trunk_close = next;
  bridge.call('setVehicleAutomationSetting', 'voice_enable_trunk_open', next);
  bridge.call('setVehicleAutomationSetting', 'voice_enable_trunk_close', next);
  showToast(next ? '电动尾门播报计划已开启' : '电动尾门播报计划已暂停');
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

function openTrunkConfigModal() {
  showTrunkModal.value = true;
}

function showGearHelp() {
  openModal('confirm', {
    title: '【功能指南】换挡权威源与有人感知状态机',
    desc: '1. 权威判定：以原厂 360 环视 AVM 广播与 TCU 换挡底层低 4 位为权威源，过滤 10 号假挡位。\n\n2. 有人感知：蓝牙钥匙靠近唤醒时保持静默；真正踩刹车切 D/R 挡才激活；回 P 挡播报一次后归零休眠。',
    tip: 'N 挡空挡播报默认关闭，避免红绿灯打扰；支持在细分配置中独立声效设置。',
    showCancel: false,
    confirmText: '我知道了'
  });
}

function showModeHelp() {
  openModal('confirm', {
    title: '【功能指南】4 大驾驶模式旋钮切换',
    desc: '1. 旋钮响应：支持舒适、经济、运动与智能 4 大驾驶模式。\n\n2. 极速防抖：内置 160ms 防抖滤波，防止快速旋转旋钮掐灭音频或重叠发声。',
    tip: '点火前 5 秒自动保护音频通道，防止原厂开机音吞音；支持在细分配置中为每种模式独立定制台词与音效。',
    showCancel: false,
    confirmText: '我知道了'
  });
}

function showDoorHelp() {
  openModal('confirm', {
    title: '【功能指南】五门防抖与通用智能语音',
    desc: '1. 通用智能模式：推荐模式。开门统一播报「车门已打开」，关门统一播报「车门已关好」，多门同时动作合并防抖，不抢音。\n\n2. 独立分门模式：可分别为主驾、副驾、后排播报专属台词。',
    tip: '在二级向导中可随时切换模式，并对各项开闭动作进行个性化声效定制。',
    showCancel: false,
    confirmText: '我知道了'
  });
}

function showTrunkHelp() {
  openModal('confirm', {
    title: '【功能指南】原厂电动尾门独立语音播报',
    desc: '1. 底层独立串口：独立监听 MCU 串口 91 02 01 b7 尾门位图，与四门逻辑完全解耦独立。\n\n2. 升起防刮与锁止确认：后备箱抬起升起时安全警示，防碰擦车库顶梁；电吸完全锁止时短促确认“后备箱已关好”，关后备箱无需回头确认。',
    tip: '升起播报与锁止播报均带独立开关，支持试听与自定义小爱 TTS 台词/音频文件。',
    showCancel: false,
    confirmText: '我知道了'
  });
}

function openCustomVoice(key, title, soundFile) {
  openModal('voiceItemSettings', {
    key,
    title,
    soundFile: soundFile || (key + '.mp3')
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
