<template>
  <div class="flex flex-col space-y-6">
    <!-- 1. 转向灯联动 360 全景盲区 (独立车规整行大卡片) -->
    <FeatureCard 
      title="1. 转向灯联动 360 全景盲区影像"
      desc="原厂 360 环视核心状态机毫秒级联动。打起转向灯秒开盲区画面，方向盘回正自动退出；内置车速 ≤30km/h 安全阈值保护，高速巡航变道自动静默抑制。"
      helpTitle="【功能指南】转向灯联动 360 全景原理与车速安全保护"
      helpText="1. 转向灯联动原理：&#10;实时监听底层原厂 AVM 环视状态机与转向拨杆电平（turnLight=1 左转 / 2 右转 / 0 复位）。打起转向灯毫秒级拉起 360 盲区影像；方向盘回正后自动平滑退出。&#10;&#10;2. 车速 ≤30km/h 抑制保护：&#10;为防止高速公路或城市快速路巡航打转向灯变道时频繁跳出全景画面遮挡高德地图导航，内置了 30km/h 车速硬门槛；时速高于 30km/h 时转向灯联动自动静默。"
      helpTip="建议保持开启，低速转弯与泊车看盲区极度便捷，且绝不影响高速导航。"
    >
      <div class="bg-car-item border-2 border-car-border rounded-2xl p-6 flex items-center justify-between shadow-sm">
        <div class="w-[60%] max-w-[60%] flex flex-col space-y-1.5 shrink-0">
          <div class="flex items-center space-x-3">
            <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">转向灯联动 360 全景盲区</span>
            <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-card border-car-border text-car-accent inline-flex items-center shadow-sm">
              盲区防御
            </span>
          </div>
          <div class="text-[15px] text-car-sub font-bold leading-relaxed">
            打起转向灯秒级唤起 360 全景看盲区，方向盘回正自动退出；时速超过 30km/h 自动静默不挡导航。
          </div>
        </div>
        <div class="shrink-0 w-[230px]">
          <button 
            @click="toggleSetting('vehicle_turn_360_enabled')"
            :class="[
              'w-full h-[78px] px-4 py-2 rounded-2xl border-2 cursor-pointer transition-all shadow-md flex flex-col items-center justify-center text-center',
              store.vehicleAuto.vehicle_turn_360_enabled
                ? 'bg-car-item border-car-accent text-car-text font-black'
                : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
            ]"
          >
            <span class="text-[19px] font-black text-car-text tracking-wide whitespace-nowrap">
              {{ store.vehicleAuto.vehicle_turn_360_enabled ? '转向360已开启' : '转向360已关闭' }}
            </span>
            <span :class="['text-[13px] font-bold mt-1 whitespace-nowrap', store.vehicleAuto.vehicle_turn_360_enabled ? 'text-car-accent' : 'text-car-sub']">
              {{ store.vehicleAuto.vehicle_turn_360_enabled ? '打转向灯秒开盲区' : '点击开启盲区联动' }}
            </span>
          </button>
        </div>
      </div>
    </FeatureCard>

    <!-- 2. D 挡起步联动 360 全景 (独立车规整行大卡片 · 严格单次跃变) -->
    <FeatureCard 
      title="2. 前进 D 挡起步联动 360 全景"
      desc="挂入前进 D 挡瞬间自动唤起 360 全景，起步前环视车身四周障碍物。内置严格单次跃变武装锁，手动退出后绝不循环调起，车速超 15km/h 自动退出。"
      helpTitle="【功能指南】D 挡起步 360 全景与单次跃变防循环调起"
      helpText="1. 起步环视逻辑：&#10;从驻车 P 挡或倒车 R 挡切入前进 D 挡瞬间，自动唤醒 360 盲区画面，便于起步前环视车身四周盲区障碍物，起步车速超过 15km/h 后由系统平滑退出。&#10;&#10;2. 单次跃变武装锁 (彻底杜绝循环唤醒)：&#10;仅在挡位从非 D 挡切入 D 挡的瞬间触发 1 次，随后立即闭锁。在 D 挡等待时哪怕手动点击屏幕退出 360，也绝对不会被心跳信号反复调起；只有切出 D 挡后才会重新武装待命。"
      helpTip="适合侧方位出库或狭窄车位起步场景，根据个人驾驶习惯按需开启。"
    >
      <div class="bg-car-item border-2 border-car-border rounded-2xl p-6 flex items-center justify-between shadow-sm">
        <div class="w-[60%] max-w-[60%] flex flex-col space-y-1.5 shrink-0">
          <div class="flex items-center space-x-3">
            <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">D 挡起步联动 360 全景</span>
            <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-card border-car-border text-car-accent inline-flex items-center shadow-sm">
              单次跃变锁
            </span>
          </div>
          <div class="text-[15px] text-car-sub font-bold leading-relaxed">
            挂入前进 D 挡起步唤醒 360 环视四周；触发一次即锁定，手动退出绝不重复弹出，等红绿灯不打扰。
          </div>
        </div>
        <div class="shrink-0 w-[230px]">
          <button 
            @click="toggleSetting('vehicle_gear_d_360_enabled')"
            :class="[
              'w-full h-[78px] px-4 py-2 rounded-2xl border-2 cursor-pointer transition-all shadow-md flex flex-col items-center justify-center text-center',
              store.vehicleAuto.vehicle_gear_d_360_enabled
                ? 'bg-car-item border-car-accent text-car-text font-black'
                : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
            ]"
          >
            <span class="text-[19px] font-black text-car-text tracking-wide whitespace-nowrap">
              {{ store.vehicleAuto.vehicle_gear_d_360_enabled ? 'D挡360已开启' : 'D挡360已关闭' }}
            </span>
            <span :class="['text-[13px] font-bold mt-1 whitespace-nowrap', store.vehicleAuto.vehicle_gear_d_360_enabled ? 'text-car-accent' : 'text-car-sub']">
              {{ store.vehicleAuto.vehicle_gear_d_360_enabled ? '起步单次唤醒全景' : '点击开启起步全景' }}
            </span>
          </button>
        </div>
      </div>
    </FeatureCard>

    <!-- 3. 车速联动与起步多媒体智能自启 (核心大功能 · 全面拉大) -->
    <FeatureCard 
      title="3. 车速联动与起步多媒体智能启播"
      desc="挂 D 挡出发达到设定时速，自动起播自选音乐 App。内置单次行程防抖闭环，等红绿灯不重复触发；若车主已在放歌则静默放行。"
      helpTitle="【功能指南】车速自启多媒体与单次行程防抖闭环"
      helpText="1. 车速自启逻辑：&#10;车辆点火出库挂入 D 挡起步，车速达到设定阈值（推荐 20 km/h）时，后台自动向目标音乐软件发送播放指令，上车无需再手动翻点屏幕放歌。&#10;&#10;2. 单次行程防抖状态机：&#10;吸取 Tasker 状态机精髓并进行了原生重构。单次行程中达到速度仅触发一次播放指令，随后状态机自动锁定；路上遇到红绿灯、堵车起步绝不重复拉起应用或重复切歌。只有车辆停稳并挂回 P 挡后才会重新复位待命。&#10;&#10;3. 已有播放智能避让：&#10;达到速度时底层会先探查全局 AudioFocus 音频焦点与 MediaSession 状态。若车主在上车前就已经在放歌，系统会静默放行，绝不粗暴打断现有歌曲。"
      helpTip="推荐阈值设为 20 km/h，并选择「后台静默放歌」，可在不打扰高德全屏导航的同时畅听音乐。"
    >
      <template #badge>
        <span class="text-[15px] font-black text-car-accent px-3.5 py-1 bg-car-item rounded-xl border border-car-border whitespace-nowrap">
          当前触发: {{ store.vehicleAuto.vehicle_speed_autoplay_threshold || 20 }} km/h
        </span>
      </template>

      <div class="flex flex-col space-y-5">
        <!-- 总开关大卡片 -->
        <div class="flex items-center justify-between p-6 rounded-2xl bg-car-item border-2 border-car-border shadow-sm">
          <div class="w-[60%] max-w-[60%] flex flex-col space-y-1.5 shrink-0">
            <div class="flex items-center space-x-3">
              <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">车速自动启播总开关</span>
              <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-card border-car-border text-car-accent inline-flex items-center shadow-sm">
                行程防抖
              </span>
            </div>
            <div class="text-[15px] text-car-sub font-bold leading-relaxed">
              点火出 P 挡起步达到设定车速触发 1 次；等红绿灯不重复放歌；停车回 P 挡归零重置。若车主上车已在放歌则静默放行。
            </div>
          </div>
          <div class="shrink-0 w-[230px]">
            <button 
              @click="toggleSetting('vehicle_speed_autoplay_enabled')" 
              :class="[
                'w-full h-[78px] px-4 py-2 rounded-2xl border-2 cursor-pointer transition-all shadow-md flex flex-col items-center justify-center text-center',
                store.vehicleAuto.vehicle_speed_autoplay_enabled
                  ? 'bg-car-item border-car-accent text-car-text font-black'
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              <span class="text-[19px] font-black text-car-text tracking-wide whitespace-nowrap">
                {{ store.vehicleAuto.vehicle_speed_autoplay_enabled ? '车速自启已开启' : '车速自启已关闭' }}
              </span>
              <span :class="['text-[13px] font-bold mt-1 whitespace-nowrap', store.vehicleAuto.vehicle_speed_autoplay_enabled ? 'text-car-accent' : 'text-car-sub']">
                {{ store.vehicleAuto.vehicle_speed_autoplay_enabled ? '起步达到时速自启' : '点击开启自启动' }}
              </span>
            </button>
          </div>
        </div>

        <!-- 车速预设快捷大磁贴 (4列饱满布局) -->
        <div class="grid grid-cols-4 gap-3.5">
          <MatrixButton 
            title="起步即播 (D挡>0)"
            subtitle="挂挡动车立即放歌"
            :active="store.vehicleAuto.vehicle_speed_autoplay_threshold === 1"
            @click="setAutoplaySpeed(1)"
          />
          <MatrixButton 
            title="15 km/h (低速出库)"
            subtitle="地库出位平缓起步"
            :active="store.vehicleAuto.vehicle_speed_autoplay_threshold === 15"
            @click="setAutoplaySpeed(15)"
          />
          <MatrixButton 
            title="20 km/h (黄金推荐)"
            subtitle="驶出小区上路启播"
            :active="store.vehicleAuto.vehicle_speed_autoplay_threshold === 20 || !store.vehicleAuto.vehicle_speed_autoplay_threshold"
            @click="setAutoplaySpeed(20)"
          />
          <MatrixButton 
            title="30 km/h (驶入主路)"
            subtitle="提速巡航自然放歌"
            :active="store.vehicleAuto.vehicle_speed_autoplay_threshold === 30"
            @click="setAutoplaySpeed(30)"
          />
        </div>

        <!-- 目标自选音乐 App 与展示形式 (全面拉伸加大) -->
        <div class="p-6 rounded-2xl bg-car-item border-2 border-car-border flex flex-col space-y-4 shadow-sm">
          <div class="flex items-center justify-between">
            <div class="flex items-center space-x-2.5">
              <span class="text-[18px] font-black text-car-text whitespace-nowrap">自启目标音乐软件</span>
              <span class="text-[14px] text-car-sub font-bold whitespace-nowrap">(按当前优先级排序，点击直选主力)</span>
            </div>
            <div class="flex items-center space-x-3">
              <button 
                @click="refreshDetectedApps"
                class="h-[52px] px-5 rounded-xl bg-car-card border border-car-border text-car-text hover:text-car-text text-[15.5px] font-black hover:border-car-accent flex items-center space-x-1.5 whitespace-nowrap cursor-pointer shadow-sm"
              >
                <span>🔄 重新扫描已装App</span>
              </button>
              <button 
                @click="openSelectModal('speed_autoplay')"
                class="h-[52px] px-5 rounded-xl bg-car-card border border-car-border text-car-accent text-[15.5px] font-black hover:border-car-accent whitespace-nowrap cursor-pointer shadow-sm"
              >
                ⚙️ 排序 / 增减 / 全部应用
              </button>
            </div>
          </div>

          <!-- 动态探测已装音乐软件磁贴 (拉大内边距与字号，彻底拔除 bg-car-accent/ 导致白底) -->
          <div class="grid grid-cols-5 gap-3">
            <button 
              v-for="(app, idx) in sortedMusicApps" 
              :key="app.pkg"
              @click="setAutoplayApp(app.pkg, app.name)"
              class="min-h-[76px] p-3.5 rounded-2xl border-2 text-left flex flex-col justify-between transition-all cursor-pointer shadow-sm"
              :class="store.vehicleAuto.vehicle_speed_autoplay_pkg === app.pkg 
                ? 'bg-car-item border-car-accent text-car-text font-black ring-2 ring-car-accent/20' 
                : 'bg-car-card border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'"
            >
              <div class="flex items-center justify-between">
                <span class="text-[16px] font-black whitespace-nowrap truncate">{{ app.name }}</span>
                <span class="text-[12px] px-1.5 py-0.5 rounded bg-car-item font-mono font-black border border-car-border">{{ idx === 0 ? '①' : (idx === 1 ? '②' : (idx === 2 ? '③' : (idx === 3 ? '④' : '⑤'))) }}</span>
              </div>
              <span :class="['text-[12.5px] mt-1.5 whitespace-nowrap font-bold', store.vehicleAuto.vehicle_speed_autoplay_pkg === app.pkg ? 'text-car-accent' : 'text-car-sub']">
                {{ store.vehicleAuto.vehicle_speed_autoplay_pkg === app.pkg ? '● 👑 当前首选主力' : '○ 点击选为主力' }}
              </span>
            </button>
          </div>

          <!-- 启播交互形式：后台静默 vs 前台全屏 (彻底拉大！字大大大！消除看不清与白底) -->
          <div class="pt-4 border-t border-car-border/60 flex items-center justify-between">
            <div class="flex flex-col">
              <span class="text-[17px] font-black text-car-text whitespace-nowrap">启播交互形式</span>
              <span class="text-[13.5px] text-car-sub font-bold mt-0.5 whitespace-nowrap">推荐后台静默放歌，不挡中控全屏高德地图导航</span>
            </div>
            <div class="flex space-x-3.5">
              <button 
                @click="setAutoplayFullscreen(false)"
                :class="[
                  'h-[58px] px-6 rounded-2xl border-2 text-[17px] font-black whitespace-nowrap cursor-pointer transition-all shadow-md flex items-center space-x-2',
                  !store.vehicleAuto.vehicle_speed_autoplay_fullscreen 
                    ? 'bg-car-item border-car-accent text-car-text font-black ring-2 ring-car-accent/20' 
                    : 'bg-car-card border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'
                ]"
              >
                <span :class="['w-2.5 h-2.5 rounded-full', !store.vehicleAuto.vehicle_speed_autoplay_fullscreen ? 'bg-amber-500 shadow-[0_0_6px_#F59E0B]' : 'bg-slate-400']"></span>
                <span>后台静默放歌 (推荐·保持高德导航)</span>
              </button>
              <button 
                @click="setAutoplayFullscreen(true)"
                :class="[
                  'h-[58px] px-6 rounded-2xl border-2 text-[17px] font-black whitespace-nowrap cursor-pointer transition-all shadow-md flex items-center space-x-2',
                  store.vehicleAuto.vehicle_speed_autoplay_fullscreen 
                    ? 'bg-car-item border-car-accent text-car-text font-black ring-2 ring-car-accent/20' 
                    : 'bg-car-card border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'
                ]"
              >
                <span :class="['w-2.5 h-2.5 rounded-full', store.vehicleAuto.vehicle_speed_autoplay_fullscreen ? 'bg-amber-500 shadow-[0_0_6px_#F59E0B]' : 'bg-slate-400']"></span>
                <span>前台全屏大屏</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </FeatureCard>

    <!-- 4. 行车车速超限安全语音提醒 (独立车规大卡片) -->
    <FeatureCard 
      title="4. 行车车速超限安全语音提醒"
      desc="车速达到设定限速红线并持续超过 3 秒，媒体声道短促温润提醒一次，防城市高架与高速超速罚单。车速回落后自动重新武装。"
      helpTitle="【功能指南】行车超速提醒与自动重新武装机制"
      helpText="1. 判定逻辑：&#10;底层持续监听仪表实时时速。当车速达到你设定的红线（80 / 100 / 120 km/h）且连续超速超过 3 秒时，通过媒体声道进行一次温润知性的晓晓原声播报：“当前车速已超限，请减速慢行”。&#10;&#10;2. 防疲劳重新武装：&#10;为防止超速时语音连续轰炸打扰驾驶，每次播报后会自动锁定；只有当车速充分回落至红线下方 5 km/h（如 80 回落至 75）后，状态机才会重新武装，为下一次超速做好预警。"
      helpTip="城市高架道路推荐设定为 80 km/h，高速巡航推荐设定为 120 km/h。"
    >
      <template #badge>
        <span class="text-[15px] font-black text-car-accent px-3.5 py-1 bg-car-item rounded-xl border border-car-border whitespace-nowrap">
          当前红线: {{ store.vehicleAuto.vehicle_overspeed_threshold || 80 }} km/h
        </span>
      </template>

      <div class="flex flex-col space-y-5">
        <!-- 总开关 -->
        <div class="flex items-center justify-between p-6 rounded-2xl bg-car-item border-2 border-car-border shadow-sm">
          <div class="w-[60%] max-w-[60%] flex flex-col space-y-1.5 shrink-0">
            <div class="flex items-center space-x-3">
              <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">行车超速播报提醒总开关</span>
              <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-card border-car-border text-car-accent inline-flex items-center shadow-sm">安全防御</span>
            </div>
            <div class="text-[15px] text-car-sub font-bold leading-relaxed">
              点火行车中连续超速 3 秒播报“当前车速已超限，请减速慢行”；车速回落 5km/h 自动重新武装，有效防城市高架与高速罚单。
            </div>
          </div>
          <div class="shrink-0 w-[230px]">
            <button 
              @click="toggleSetting('vehicle_overspeed_voice_enabled')" 
              :class="[
                'w-full h-[78px] px-4 py-2 rounded-2xl border-2 cursor-pointer transition-all shadow-md flex flex-col items-center justify-center text-center',
                store.vehicleAuto.vehicle_overspeed_voice_enabled
                  ? 'bg-car-item border-car-accent text-car-text font-black'
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              <span class="text-[19px] font-black text-car-text tracking-wide whitespace-nowrap">
                {{ store.vehicleAuto.vehicle_overspeed_voice_enabled ? '超速提醒已开启' : '超速提醒已关闭' }}
              </span>
              <span :class="['text-[13px] font-bold mt-1 whitespace-nowrap', store.vehicleAuto.vehicle_overspeed_voice_enabled ? 'text-car-accent' : 'text-car-sub']">
                {{ store.vehicleAuto.vehicle_overspeed_voice_enabled ? '超出红线温润播报' : '点击开启安全提醒' }}
              </span>
            </button>
          </div>
        </div>

        <!-- 3 大超速档位大磁贴 -->
        <div class="grid grid-cols-3 gap-3.5">
          <MatrixButton 
            title="80 km/h (城市高架)"
            subtitle="高架快速路防扣分"
            :active="store.vehicleAuto.vehicle_overspeed_threshold === 80 || !store.vehicleAuto.vehicle_overspeed_threshold"
            @click="setOverspeedThreshold(80)"
          />
          <MatrixButton 
            title="100 km/h (近郊大道)"
            subtitle="近郊环线快速路限速"
            :active="store.vehicleAuto.vehicle_overspeed_threshold === 100"
            @click="setOverspeedThreshold(100)"
          />
          <MatrixButton 
            title="120 km/h (高速公路)"
            subtitle="国家高速法定最高限速"
            :active="store.vehicleAuto.vehicle_overspeed_threshold === 120"
            @click="setOverspeedThreshold(120)"
          />
        </div>
      </div>
    </FeatureCard>

    <!-- 5. 进出隧道大灯联动高德日夜模式 (独立车规整行大卡片) -->
    <FeatureCard 
      title="5. 进出隧道大灯联动高德日夜模式"
      desc="破译原厂大灯信号；白天开大灯（进隧道或天黑）毫秒级联动高德切换深色夜间地图，出隧道关大灯恢复浅色模式，告别刺眼白底。"
      helpTitle="【功能指南】大灯联动高德夜间模式原理"
      helpText="1. 破译原厂大灯信号：&#10;实时监听原厂车身大灯电平。白天驾车进出隧道或进出地下车库开大灯时，毫秒级捕获大灯点亮信号。&#10;&#10;2. 高德深色底图联动：&#10;开大灯时向高德地图发送夜间深色模式选通，出隧道关大灯时自动恢复白天浅色地图，告别刺眼白底。"
      helpTip="常走地下车库或隧道的车友强烈推荐开启，进隧道秒变深色夜景地图。"
    >
      <div class="bg-car-item border-2 border-car-border rounded-2xl p-6 flex items-center justify-between shadow-sm">
        <div class="w-[60%] max-w-[60%] flex flex-col space-y-1.5 shrink-0">
          <div class="flex items-center space-x-3">
            <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">大灯联动高德日夜模式</span>
            <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-card border-car-border text-car-accent inline-flex items-center shadow-sm">地图日夜</span>
          </div>
          <div class="text-[15px] text-car-sub font-bold leading-relaxed">
            进出隧道与天黑开大灯自动切换高德深色夜间地图，出隧道关大灯秒级恢复浅色模式。
          </div>
        </div>
        <div class="shrink-0 w-[230px]">
          <button 
            @click="toggleSetting('vehicle_light_nav_enabled')"
            :class="[
              'w-full h-[78px] px-4 py-2 rounded-2xl border-2 cursor-pointer transition-all shadow-md flex flex-col items-center justify-center text-center',
              store.vehicleAuto.vehicle_light_nav_enabled
                ? 'bg-car-item border-car-accent text-car-text font-black'
                : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
            ]"
          >
            <span class="text-[19px] font-black text-car-text tracking-wide whitespace-nowrap">
              {{ store.vehicleAuto.vehicle_light_nav_enabled ? '地图夜间已开启' : '地图夜间已关闭' }}
            </span>
            <span :class="['text-[13px] font-bold mt-1 whitespace-nowrap', store.vehicleAuto.vehicle_light_nav_enabled ? 'text-car-accent' : 'text-car-sub']">
              {{ store.vehicleAuto.vehicle_light_nav_enabled ? '开大灯切夜色地图' : '点击开启深色底图' }}
            </span>
          </button>
        </div>
      </div>
    </FeatureCard>

    <!-- 6. 进隧道中控护眼背光微调 (独立车规整行大卡片) -->
    <FeatureCard 
      title="6. 进隧道中控护眼背光微调"
      desc="白天开大灯进入隧道，自动将车机中控屏幕亮度微调压低至 35%，消除强光骤变眩目；出隧道关大灯秒级恢复原亮度。"
      helpTitle="【功能指南】进出隧道中控背光护眼原理"
      helpText="白天开大灯后自动将车机中控屏幕亮度微调压低至 35%，消除进隧道瞬间强光眩目；出隧道关大灯后秒级恢复你原本的屏幕亮度，柔和过渡不晃眼。"
      helpTip="与大灯高德夜间联动相互独立，可单独开启。"
    >
      <div class="bg-car-item border-2 border-car-border rounded-2xl p-6 flex items-center justify-between shadow-sm">
        <div class="w-[60%] max-w-[60%] flex flex-col space-y-1.5 shrink-0">
          <div class="flex items-center space-x-3">
            <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">进隧道中控护眼背光微调</span>
            <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-card border-car-border text-car-accent inline-flex items-center shadow-sm">防眩目</span>
          </div>
          <div class="text-[15px] text-car-sub font-bold leading-relaxed">
            白天开大灯进隧道自动压低中控背光至 35% 防眩目刺眼，出隧道关大灯秒级恢复原本亮度。
          </div>
        </div>
        <div class="shrink-0 w-[230px]">
          <button 
            @click="toggleSetting('vehicle_light_brightness_dim_enabled')"
            :class="[
              'w-full h-[78px] px-4 py-2 rounded-2xl border-2 cursor-pointer transition-all shadow-md flex flex-col items-center justify-center text-center',
              store.vehicleAuto.vehicle_light_brightness_dim_enabled
                ? 'bg-car-item border-car-accent text-car-text font-black'
                : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
            ]"
          >
            <span class="text-[19px] font-black text-car-text tracking-wide whitespace-nowrap">
              {{ store.vehicleAuto.vehicle_light_brightness_dim_enabled ? '护眼微调已开启' : '护眼微调已关闭' }}
            </span>
            <span :class="['text-[13px] font-bold mt-1 whitespace-nowrap', store.vehicleAuto.vehicle_light_brightness_dim_enabled ? 'text-car-accent' : 'text-car-sub']">
              {{ store.vehicleAuto.vehicle_light_brightness_dim_enabled ? '进隧道平滑压暗' : '点击开启护眼背光' }}
            </span>
          </button>
        </div>
      </div>
    </FeatureCard>

    <!-- 7. 停车开门多媒体优雅静音联动 (独立车规整行大卡片) -->
    <FeatureCard 
      title="7. 停车开门多媒体优雅静音联动"
      desc="挂入 P 挡停稳推开前门，毫秒级自动暂停当前多媒体播放，保持下车从容安静。（注：四门安全播报与有人感知由【座舱语音】专属中枢统一接管，零冲突零重复）"
      helpTitle="【功能指南】P 挡开门音乐暂停与座舱语音职责划分"
      helpText="1. 停稳下车优雅静音：&#10;当车辆挂入驻车 P 挡并推开前门时，底层自动向正在播放的音乐软件发送 MEDIA_PAUSE 暂停指令。到达目的地停稳开门，音乐戛然而止，下车从容安静，杜绝开门大声喧哗。&#10;&#10;2. 职责解耦与零冲突设计：&#10;车门开闭的语音提醒（如“车门已打开”、“请注意后方来车”）全部由【座舱语音】专职中枢管理；【车身联动】只负责物理动作（暂停音乐），彻底根绝两个模块发声打架的问题。"
      helpTip="本功能仅在停稳挂 P 挡后推门生效，行车途中推门（如未关严）不会暂停音乐，确保行车安全。"
    >
      <div class="bg-car-item border-2 border-car-border rounded-2xl p-6 flex items-center justify-between shadow-sm">
        <div class="w-[60%] max-w-[60%] flex flex-col space-y-1.5 shrink-0">
          <div class="flex items-center space-x-3">
            <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">P 挡开门自动暂停音乐</span>
            <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-card border-car-border text-car-accent inline-flex items-center shadow-sm">下车静音</span>
          </div>
          <div class="text-[15px] text-car-sub font-bold leading-relaxed">
            挂入驻车 P 挡停稳推开前门，毫秒级自动暂停当前多媒体播放，下车从容安静，绝不大声扰民。
          </div>
        </div>
        <div class="shrink-0 w-[230px]">
          <button 
            @click="toggleSetting('vehicle_door_pause_music_enabled')"
            :class="[
              'w-full h-[78px] px-4 py-2 rounded-2xl border-2 cursor-pointer transition-all shadow-md flex flex-col items-center justify-center text-center',
              store.vehicleAuto.vehicle_door_pause_music_enabled
                ? 'bg-car-item border-car-accent text-car-text font-black'
                : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
            ]"
          >
            <span class="text-[19px] font-black text-car-text tracking-wide whitespace-nowrap">
              {{ store.vehicleAuto.vehicle_door_pause_music_enabled ? '开门暂停已开启' : '开门暂停已关闭' }}
            </span>
            <span :class="['text-[13px] font-bold mt-1 whitespace-nowrap', store.vehicleAuto.vehicle_door_pause_music_enabled ? 'text-car-accent' : 'text-car-sub']">
              {{ store.vehicleAuto.vehicle_door_pause_music_enabled ? '停稳推门秒暂停' : '点击开启下车静音' }}
            </span>
          </button>
        </div>
      </div>
    </FeatureCard>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { store, bridge, showToast, openModal } from '../store';
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';

const sortedMusicApps = ref([]);

function getMusicOrder() {
  try {
    const raw = localStorage.getItem('preferred_music_apps_order');
    if (raw) return JSON.parse(raw);
  } catch (e) {}
  return [];
}

function loadSortedMusicApps() {
  let list = [];
  try {
    const raw = bridge.call('getInstalledMusicAppsJson');
    if (raw) {
      const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
      list = parsed.map(item => ({
        name: item.name || item.appName,
        pkg: item.pkg || item.packageName
      }));
    }
  } catch (e) {}

  if (!list.some(a => a.pkg === 'com.android.bluetooth')) {
    list.push({ name: '手机蓝牙', pkg: 'com.android.bluetooth' });
  }

  // 若探测为空（极简未安装第三方），默认兜底
  if (list.length === 1 && list[0].pkg === 'com.android.bluetooth') {
    list.unshift({ name: '汽水音乐', pkg: 'com.luna.music' });
    list.push({ name: 'QQ音乐', pkg: 'com.tencent.qqmusiccar' });
    list.push({ name: '酷我音乐', pkg: 'cn.kuwo.kwmusiccar' });
  }

  // 按用户排序优先级重排
  const order = getMusicOrder();
  if (order.length > 0) {
    list.sort((a, b) => {
      let ia = order.indexOf(a.pkg);
      let ib = order.indexOf(b.pkg);
      if (ia === -1) ia = 999;
      if (ib === -1) ib = 999;
      return ia - ib;
    });
  }

  // 截取前 5 项平铺展示
  sortedMusicApps.value = list.slice(0, 5);

  // 若当前未选定自启目标，默认锁定第 1 优先级
  if (!store.vehicleAuto.vehicle_speed_autoplay_pkg && sortedMusicApps.value.length > 0) {
    store.vehicleAuto.vehicle_speed_autoplay_pkg = sortedMusicApps.value[0].pkg;
  }
}

function refreshDetectedApps() {
  try {
    bridge.call('refreshInstalledApps');
  } catch (e) {}
  loadSortedMusicApps();
  showToast('已重新扫描整车已安装音乐软件并刷新排序');
}

function toggleSetting(key) {
  const next = !store.vehicleAuto[key];
  store.vehicleAuto[key] = next;
  bridge.call('setVehicleAutomationSetting', key, next);
  showToast('设置已更新: ' + (next ? '已开启' : '已关闭'));
}

function setAutoplaySpeed(speed) {
  store.vehicleAuto.vehicle_speed_autoplay_threshold = speed;
  bridge.call('setVehicleAutomationIntSetting', 'vehicle_speed_autoplay_threshold', speed);
  showToast(`自启车速阈值已设为: ${speed} km/h`);
}

function setAutoplayApp(pkg, name) {
  store.vehicleAuto.vehicle_speed_autoplay_pkg = pkg;
  bridge.call('setWheelControlStringSetting', 'vehicle_speed_autoplay_pkg', pkg);
  localStorage.setItem('vehicle_speed_autoplay_app_name', name);
  // 点击选为主力时，自动将它置顶为第 1 优先级
  let order = getMusicOrder();
  order = order.filter(p => p !== pkg);
  order.unshift(pkg);
  localStorage.setItem('preferred_music_apps_order', JSON.stringify(order));
  loadSortedMusicApps();
  showToast(`自启首选主力已设为: ${name}`);
}

function setAutoplayFullscreen(fullscreen) {
  store.vehicleAuto.vehicle_speed_autoplay_fullscreen = fullscreen;
  bridge.call('setVehicleAutomationSetting', 'vehicle_speed_autoplay_fullscreen', fullscreen);
  showToast(fullscreen ? '已设为前台全屏大屏打开' : '已设为后台静默放歌 (保持高德导航)');
}

function setOverspeedThreshold(speed) {
  store.vehicleAuto.vehicle_overspeed_threshold = speed;
  bridge.call('setVehicleAutomationIntSetting', 'vehicle_overspeed_threshold', speed);
  showToast(`超速红线已设为: ${speed} km/h`);
}

function openSelectModal(target) {
  openModal('appSelect', { keyTarget: target });
}

function onMusicOrderChanged() {
  loadSortedMusicApps();
}

onMounted(() => {
  loadSortedMusicApps();
  window.addEventListener('music-order-updated', onMusicOrderChanged);
});

onUnmounted(() => {
  window.removeEventListener('music-order-updated', onMusicOrderChanged);
});
</script>
