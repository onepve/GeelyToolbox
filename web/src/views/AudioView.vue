<template>
  <div class="flex flex-col space-y-6">
    <!-- 1. 系统语音合成引擎 (TTS) 直通状态 (支持原厂与第三方TTS自由切换，状态直显与试听) -->
    <FeatureCard 
      title="1. 系统语音合成引擎 (TTS) 直通状态"
      desc="系统直接通过后台 IPC 接入安卓系统语音合成服务。支持车机原厂语音引擎与第三方 TTS 自由选择，点击设置可随心切换。"
      helpTitle="【功能指南】系统语音合成引擎 (TTS) 直通"
      helpText="1. 直连原理：&#10;工具箱通过后台 IPC 直接接入安卓系统级语音合成服务，可实时读取当前引擎连接状态与声线就绪情况。&#10;&#10;2. 引擎自由切换：&#10;支持在车机原厂语音引擎与第三方 TTS 之间自由选择；点击「TTS 设置」调起系统引擎选择，点击「试听语音」立即播放测试样音验证当前引擎发声是否正常。&#10;&#10;3. 缺引擎兜底：&#10;若系统无第三方 TTS 引擎，可点击「前往商城获取语音引擎」一键跳转精选商城下载安装。"
      helpTip="推荐保持原厂晓晓温婉知性声线，端庄舒缓不刺耳。"
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
            <span class="text-[19px] font-black text-car-text truncate">
              {{ ttsInfo.name }}
            </span>
            <span class="ml-3 text-[12.5px] px-2.5 py-0.5 rounded-full font-black border bg-car-item border-car-border text-car-text inline-flex items-center shadow-sm shrink-0">
              <span :class="['w-2 h-2 rounded-full mr-1.5', ttsInfo.connected ? 'bg-sky-500 shadow-[0_0_6px_#0EA5E9]' : 'bg-slate-400']"></span>
              {{ ttsInfo.connected ? '已成功直连' : '默认引擎' }}
            </span>
          </div>
          <div class="text-[15px] text-car-sub font-bold leading-relaxed">
            {{ ttsInfo.status }}
          </div>
        </div>

        <div class="shrink-0 flex items-center space-x-3">
          <button 
            @click="openTtsSettings"
            class="min-h-[64px] px-6 bg-car-card border-2 border-car-border text-car-text font-black text-[18px] rounded-2xl cursor-pointer hover:border-car-border-light shadow-sm"
          >
            TTS 设置
          </button>
          <button 
            v-if="ttsInfo.connected"
            @click="testTtsEngine"
            class="min-h-[64px] px-8 bg-car-card border-2 border-car-accent text-car-text font-black text-[19px] rounded-2xl cursor-pointer hover:border-car-accent shadow-md ring-2 ring-car-accent/20"
          >
            试听语音
          </button>
          <button 
            v-else
            @click="openStoreToDownload"
            class="min-h-[64px] px-8 bg-car-card border-2 border-car-border text-car-accent font-black text-[19px] rounded-2xl cursor-pointer hover:border-car-border-light shadow-sm"
          >
            前往商城获取语音引擎
          </button>
        </div>
      </div>
    </FeatureCard>

    <!-- 2. 播报音量相对增益补偿滑条 (解决听歌低音量时听不清开门/换挡) -->
    <FeatureCard 
      title="2. 播报音量动态增益补偿 (听歌低音量时自动增补)"
      desc="听歌时媒体音量调低容易听不清开门与换挡提醒。设置增益后，播报瞬间自动在当前媒体音量上动态叠加补偿，播完秒级恢复原音量！"
      helpTitle="【功能指南】播报音量动态增益补偿"
      helpText="1. 解决痛点：&#10;听歌时媒体音量调低，开门/换挡等安全播报容易被音乐盖过听不清。&#10;&#10;2. 增益机制：&#10;设置正增益后，每次播报瞬间会在当前媒体音量上临时叠加补偿格数，播报结束秒级恢复原音量，不影响后续听歌。&#10;&#10;3. 纯加减操控：&#10;提供 -2/-1/+1/+2 快速步进与点击中央数值归零，盲调顺手；可随时「试听当前音量增益」即时验证效果。"
      helpTip="建议设为 +2 格，既能清晰听清播报，又不会突兀爆音。"
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

        <!-- 车规纯加减音量补偿控制器 (彻底拔除细长滑条，盲调顺手) -->
        <div class="flex items-center justify-between pt-2">
          <div class="flex items-center space-x-3">
            <button 
              @click="adjustVolumeOffset(-2)"
              class="h-[56px] px-5 rounded-2xl bg-car-card border-2 border-car-border text-car-text font-black text-[17px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
              title="降低 2 格"
            >-2 格</button>
            <button 
              @click="adjustVolumeOffset(-1)"
              class="h-[56px] px-5 rounded-2xl bg-car-card border-2 border-car-border text-car-text font-black text-[17px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
              title="降低 1 格"
            >-1 格</button>
            <div 
              @click="resetVolumeOffset"
              class="flex items-baseline px-6 py-2 bg-car-card rounded-2xl border-2 border-car-accent min-w-[140px] justify-center shadow-inner cursor-pointer hover:bg-car-item active:scale-95 transition-all"
              title="点击重置为 0 格"
            >
              <span class="text-[34px] font-black text-car-accent font-mono leading-none">{{ volumeOffset >= 0 ? '+' + volumeOffset : volumeOffset }}</span>
              <span class="text-[16px] font-bold text-car-sub ml-1.5">格</span>
            </div>
            <button 
              @click="adjustVolumeOffset(1)"
              class="h-[56px] px-5 rounded-2xl bg-car-card border-2 border-car-border text-car-text font-black text-[17px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
              title="增加 1 格"
            >+1 格</button>
            <button 
              @click="adjustVolumeOffset(2)"
              class="h-[56px] px-5 rounded-2xl bg-car-card border-2 border-car-border text-car-text font-black text-[17px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
              title="增加 2 格"
            >+2 格</button>
          </div>

          <div class="flex items-center space-x-3">
            <button 
              @click="resetVolumeOffset"
              class="h-[56px] px-6 bg-car-card border-2 border-car-border text-car-sub hover:text-car-text font-black text-[16px] rounded-2xl cursor-pointer hover:border-car-border-light shadow-sm"
            >
              重置归零 (+0)
            </button>
            <button 
              @click="testVolumeOffset"
              class="h-[56px] px-8 bg-car-card border-2 border-car-accent text-car-text font-black text-[17px] rounded-2xl cursor-pointer hover:border-car-accent shadow-md ring-2 ring-car-accent/20"
            >
              试听当前音量增益
            </button>
          </div>
        </div>
      </div>
    </FeatureCard>

    <!-- 3. 座舱专属语音主题包与自定义音效 (一键整套换装 · 物理隔离) -->
    <FeatureCard 
      title="3. 座舱专属语音主题包 (一键整套换装 · 物理隔离)"
      desc="支持导入车规级 ZIP 语音包。所有语音主题物理隔离保存在 /sdcard/GeelyPilot/voices/ 专属目录中，清空下载目录绝不受任何影响！"
      helpTitle="【功能指南】座舱专属语音主题包"
      helpText="1. 整套换装：&#10;支持导入车规级 ZIP 语音包，一键整套替换全车播报音色与音效。&#10;&#10;2. 物理隔离：&#10;所有语音主题独立保存在 /sdcard/GeelyPilot/voices/ 专属目录，与下载目录完全隔离，清空 Download 目录绝不影响已导入主题。&#10;&#10;3. 出厂兜底：&#10;未导入任何主题时自动使用出厂官方晓晓原声；导入的主题若有音频缺失项，自动补齐兜底，确保零丢失。"
      helpTip="导入前请确认 ZIP 为车规语音包结构，删除主题会自动恢复出厂原声。"
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
                <span class="px-2.5 py-0.5 rounded-full bg-car-item border border-car-border text-car-text text-[12px] font-black inline-flex items-center shadow-sm">
                  <span class="w-2 h-2 rounded-full mr-1.5 bg-emerald-500 shadow-[0_0_6px_#10B981]"></span>
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
              <span>导入语音包 (.zip)</span>
            </button>
            <button 
              @click="loadVoiceThemes"
              class="min-h-[56px] px-5 bg-car-card border-2 border-car-border text-car-text font-black text-[16px] rounded-xl cursor-pointer hover:border-car-border-light shadow-sm transition-all"
            >
              刷新列表
            </button>
          </div>
        </div>

        <!-- 主题包列表卡片流 -->
        <div class="flex flex-col space-y-3">
          <!-- 默认出厂主题卡片 -->
          <div class="bg-car-item border-2 border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
            <div class="flex-1 min-w-0 pr-6 flex flex-col space-y-1">
              <div class="flex items-center space-x-2.5">
                <span class="text-[19px] font-black text-car-text">出厂官方原声 (晓晓温婉知性)</span>
                <span class="px-2.5 py-0.5 rounded-md bg-car-card border border-car-border text-car-sub text-[12px] font-black">系统内置</span>
                <span v-if="!activeThemeName" class="px-2.5 py-0.5 rounded-md bg-car-item border border-car-border text-car-text text-[12px] font-black inline-flex items-center shadow-sm"><span class="w-2 h-2 rounded-full mr-1.5 bg-car-accent shadow-[0_0_6px_var(--accent-gold)]"></span>正在生效</span>
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
                试听样音
              </button>
              <button 
                v-if="activeThemeName"
                @click="applyVoiceTheme('')"
                class="h-[52px] px-6 bg-car-card border-2 border-car-accent text-car-accent font-black text-[16px] rounded-xl hover:border-car-accent ring-2 ring-car-accent/20 cursor-pointer shadow-md transition-all"
              >
                恢复此原声
              </button>
              <div 
                v-else
                class="h-[52px] px-5 bg-emerald-500/15 border-2 border-emerald-500/50 text-emerald-400 font-black text-[15px] rounded-xl flex items-center justify-center space-x-1.5 shadow-sm select-none"
              >
                <span class="w-2 h-2 rounded-full bg-emerald-400 shadow-[0_0_6px_#10B981]"></span>
                <span>正在生效</span>
              </div>
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
                <span v-if="activeThemeName === theme.name" class="px-2.5 py-0.5 rounded-md bg-car-item border border-car-border text-car-text text-[12px] font-black shrink-0 inline-flex items-center shadow-sm"><span class="w-2 h-2 rounded-full mr-1.5 bg-car-accent shadow-[0_0_6px_var(--accent-gold)]"></span>正在整套生效</span>
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
                试听样音
              </button>
              <button 
                v-if="activeThemeName !== theme.name"
                @click="applyVoiceTheme(theme.name)"
                class="h-[52px] px-6 bg-car-card border-2 border-car-accent text-car-text font-black text-[16px] rounded-xl hover:border-car-accent ring-2 ring-car-accent/20 cursor-pointer shadow-md transition-all"
              >
                整套启用
              </button>
              <div 
                v-else
                class="h-[52px] px-5 bg-emerald-500/15 border-2 border-emerald-500/50 text-emerald-400 font-black text-[15px] rounded-xl flex items-center justify-center space-x-1.5 shadow-sm select-none"
              >
                <span class="w-2 h-2 rounded-full bg-emerald-400 shadow-[0_0_6px_#10B981]"></span>
                <span>正在生效</span>
              </div>
              <button 
                @click="confirmDeleteTheme(theme.name)"
                class="h-[52px] px-4 bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text hover:text-rose-400 font-black text-[15px] rounded-xl cursor-pointer shadow-sm transition-all"
              >
                删除
              </button>
            </div>
          </div>
        </div>
      </div>
    </FeatureCard>

    <!-- 4. 车载蓝牙音频与网络互联 -->
    <FeatureCard 
      title="4. 车载蓝牙音频与网络互联 (原厂混音与硬件声道调试)"
      desc="直控车机蓝牙与 Wi-Fi 开关，实时呈现 6 号蓝牙硬件声道仲裁与推流状态，提供一键强制声道选通与发声调试。"
      helpTitle="【功能指南】车载蓝牙音频与网络互联"
      helpText="1. 蓝牙/Wi-Fi 直控：&#10;一键开关车机蓝牙与 Wi-Fi，实时呈现连接设备、热点与局域网 IP。&#10;&#10;2. EAS 6 号物理声道仲裁：&#10;手机蓝牙播放微信语音或音乐无声时，点击「一键强制选通蓝牙声道」可向 EAS 下发切换指令激活喇叭，解决原厂多媒体冻结后的无声痛点。&#10;&#10;3. 发声调试：&#10;「测试发声」立即验证当前蓝牙声道选通是否成功。"
      helpTip="手机无声时先点「测试发声」确认喇叭，再点「一键强制选通蓝牙声道」。"
    >
      <div class="bg-car-item border border-car-border rounded-2xl p-6 flex flex-col space-y-4 shadow-sm">
        <!-- 上层：蓝牙与 Wi-Fi 硬件连接看板 (双列对称大卡片) -->
        <div class="grid grid-cols-2 gap-4">
          <!-- 蓝牙控制看板 -->
          <div class="bg-car-card border border-car-border rounded-2xl p-4 flex flex-col justify-between space-y-3">
            <div class="flex items-center justify-between">
              <div class="flex items-center space-x-2.5">
                <span :class="['w-3 h-3 rounded-full', connStatus.bluetooth_enabled ? 'bg-emerald-500 shadow-[0_0_8px_#10B981]' : 'bg-slate-400']"></span>
                <span class="text-[18px] font-black text-car-text">车机蓝牙</span>
              </div>
              <button 
                @click="toggleBluetooth"
                :class="[
                  'min-h-[50px] px-4 rounded-xl border-2 text-[14px] font-black cursor-pointer transition-all',
                  connStatus.bluetooth_enabled ? 'border-emerald-500/60 bg-emerald-500/15 text-emerald-400' : 'border-car-border bg-car-item text-car-sub'
                ]"
              >
                {{ connStatus.bluetooth_enabled ? '已开启' : '已关闭' }}
              </button>
            </div>
            <div class="text-[14.5px] font-mono text-car-sub truncate">
              设备: {{ connStatus.bluetooth_device_name || '未连接设备' }}
            </div>
            <div class="pt-4 border-t border-car-border/50 flex items-center justify-between">
              <span class="text-[12.5px] text-car-sub font-bold">配对与连接管理</span>
              <button 
                @click="openBluetoothSettings"
                class="min-h-[50px] px-4 rounded-xl border border-car-border bg-car-item hover:border-car-border-light text-car-text text-[13.5px] font-bold cursor-pointer"
              >
                打开蓝牙设置 ➔
              </button>
            </div>
          </div>

          <!-- Wi-Fi 控制看板 -->
          <div class="bg-car-card border border-car-border rounded-2xl p-4 flex flex-col justify-between space-y-3">
            <div class="flex items-center justify-between">
              <div class="flex items-center space-x-2.5">
                <span :class="['w-3 h-3 rounded-full', connStatus.wifi_enabled ? 'bg-emerald-500 shadow-[0_0_8px_#10B981]' : 'bg-slate-400']"></span>
                <span class="text-[18px] font-black text-car-text">车机 Wi-Fi</span>
              </div>
              <button 
                @click="toggleWifi"
                :class="[
                  'min-h-[50px] px-4 rounded-xl border-2 text-[14px] font-black cursor-pointer transition-all',
                  connStatus.wifi_enabled ? 'border-emerald-500/60 bg-emerald-500/15 text-emerald-400' : 'border-car-border bg-car-item text-car-sub'
                ]"
              >
                {{ connStatus.wifi_enabled ? '已开启' : '已关闭' }}
              </button>
            </div>
            <div class="text-[14.5px] font-mono text-car-sub truncate">
              热点: {{ connStatus.wifi_ssid || '未连接热点' }} ({{ connStatus.car_ip || '127.0.0.1' }})
            </div>
            <div class="pt-4 border-t border-car-border/50 flex items-center justify-between">
              <span class="text-[12.5px] text-car-sub font-bold">热点连接向导</span>
              <button 
                @click="openWifiSettings"
                class="min-h-[50px] px-4 rounded-xl border border-car-border bg-car-item hover:border-car-border-light text-car-text text-[13.5px] font-bold cursor-pointer"
              >
                打开 Wi-Fi 设置 ➔
              </button>
            </div>
          </div>
        </div>

        <!-- 下层：EAS 6 号蓝牙物理声道与调试操作条 -->
        <div class="bg-car-card border border-car-border rounded-2xl p-4 flex items-center justify-between">
          <div class="flex flex-col space-y-1 min-w-0 pr-4">
            <div class="flex items-center space-x-2.5">
              <span class="text-[17.5px] font-black text-car-text">EAS 6 号蓝牙物理声道仲裁</span>
              <span 
                :class="[
                  'text-[12px] px-2.5 py-0.5 rounded-md font-black border',
                  connStatus.eas_channel_active
                    ? 'border-emerald-500/50 bg-emerald-500/15 text-emerald-400'
                    : 'border-car-border bg-car-item text-car-sub'
                ]"
              >
                {{ connStatus.eas_channel_active ? '物理声道已选通' : '声道待机中' }}
              </span>
            </div>
            <div class="text-[13px] text-car-sub font-bold">
              手机播放微信语音或音乐不出声时，可点击右侧按钮强制下发 EAS 切换指令激活喇叭
            </div>
          </div>

          <div class="flex items-center space-x-3 shrink-0">
            <button 
              @click="testBluetoothAudio"
              class="min-h-[52px] px-5 rounded-xl border border-car-border bg-car-item hover:border-car-border-light text-car-text font-black text-[15px] cursor-pointer shadow-sm"
            >
              测试发声
            </button>
            <button 
              @click="forceActivateBluetooth"
              class="min-h-[52px] px-6 rounded-xl border-2 border-car-accent bg-car-item text-car-text hover:border-car-accent font-black text-[15.5px] cursor-pointer shadow-md ring-2 ring-car-accent/20"
            >
              一键强制选通蓝牙声道
            </button>
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
  name: '系统默认语音合成引擎',
  status: '已连接系统底层默认语音引擎 · 声线就绪'
});

const connStatus = ref({
  bluetooth_enabled: false,
  bluetooth_connected: false,
  bluetooth_device_name: '未连接设备',
  wifi_enabled: false,
  wifi_connected: false,
  wifi_ssid: '未连接热点',
  car_ip: '127.0.0.1',
  eas_channel_active: false
});

function refreshConnectivity() {
  try {
    const raw = bridge.call('getConnectivityStatus');
    if (raw) {
      connStatus.value = typeof raw === 'string' ? JSON.parse(raw) : raw;
    }
  } catch (e) {}
}

function toggleBluetooth() {
  const next = !connStatus.value.bluetooth_enabled;
  connStatus.value.bluetooth_enabled = next;
  bridge.call('toggleBluetooth', next);
  setTimeout(refreshConnectivity, 1500);
}

function openBluetoothSettings() {
  bridge.call('openBluetoothSettings');
  showToast('正在打开原厂系统蓝牙配对设置...');
}

function toggleWifi() {
  const next = !connStatus.value.wifi_enabled;
  connStatus.value.wifi_enabled = next;
  bridge.call('toggleWifi', next);
  setTimeout(refreshConnectivity, 1500);
}

function openWifiSettings() {
  bridge.call('openWifiSettings');
  showToast('正在打开原厂系统 Wi-Fi 设置...');
}

function forceActivateBluetooth() {
  bridge.call('forceActivateBluetoothChannel');
  refreshConnectivity();
}

function testBluetoothAudio() {
  bridge.call('testBluetoothAudio');
}

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
      name: '系统默认语音合成引擎',
      status: '已连接系统底层默认语音引擎 · 声线就绪'
    };
  }

  try {
    const off = bridge.call('getVoiceVolumeOffset');
    if (typeof off === 'number') {
      volumeOffset.value = off;
      store.vehicleAuto.voice_volume_offset = off;
    }
  } catch (e) {}

  loadVoiceThemes();
  window.refreshVoiceThemes = loadVoiceThemes;
  refreshConnectivity();
  setInterval(refreshConnectivity, 4000);
});

function openTtsSettings() {
  bridge.call('openTtsSettings');
  showToast('正在打开系统 TTS 语音引擎设置...');
}

function testTtsEngine() {
  bridge.call('testVehicleVoice', 'custom');
  showToast('正在调用语音合成引擎播放测试语音...');
  setTimeout(() => {
    try {
      const raw = bridge.call('getTtsEngineInfo');
      if (raw) {
        ttsInfo.value = typeof raw === 'string' ? JSON.parse(raw) : raw;
      }
    } catch (e) {}
  }, 1200);
}

function openStoreToDownload() {
  store.currentNav = 'store';
  showToast('已跳转至精选商城');
}

function saveVolumeOffset() {
  store.vehicleAuto.voice_volume_offset = volumeOffset.value;
  bridge.call('setVoiceVolumeOffset', volumeOffset.value);
}

function adjustVolumeOffset(delta) {
  let next = Math.max(-5, Math.min(10, volumeOffset.value + delta));
  volumeOffset.value = next;
  saveVolumeOffset();
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
