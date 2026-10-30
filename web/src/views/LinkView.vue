<template>
  <div class="flex flex-col space-y-4">
    <!-- 顶部车规二级分段胶囊：一页全景掌控，消灭长滑 -->
    <SubTabs :tabs="linkTabs" v-model="activeTab" />

    <!-- 0. 方向盘按键接管与独立映射 (v-if 彻底杜绝幽灵重叠，传递 tab 参数消除第三级嵌套) -->
    <div v-if="activeTab === 'wheel_keys' || activeTab === 'wheel_mode'" class="flex flex-col space-y-4">
      <WheelView :initial-tab="activeTab === 'wheel_mode' ? 'mode' : 'keys'" :hide-sub-tabs="true" />
    </div>

    <!-- 1. 360全景联动 (双大卡片，一屏收尽，整卡点击开关) -->
    <div v-if="activeTab === '360'" class="grid grid-cols-2 gap-4 items-stretch">
      <!-- D 挡起步联动 360 -->
      <PlanCard
        title="前进 D 挡起步联动 360"
        tag="起步盲区"
        help-text="gear_d_360"
        flow-sub="从 P 挡或 R 挡切入前进 D 挡起步 (本次起步仅触发一次)"
        flow-main="自动打开 360 扫除起步盲区；车速超 15 km/h 自动退出全景"
        :clickable="true"
        :active="store.vehicleAuto.vehicle_d_gear_360_enabled"
        @toggle="toggleSetting('vehicle_d_gear_360_enabled')"
        @help="showHelp('gear_d_360')"
      >
        <template #footer>
          <div class="flex items-center space-x-2">
            <StatusDot size="sm" :color="store.vehicleAuto.vehicle_d_gear_360_enabled ? 'ok' : 'off'" :glow-px="6" />
            <span :class="['text-[16px] font-black', store.vehicleAuto.vehicle_d_gear_360_enabled ? 'text-car-text' : 'text-car-sub']">
              {{ store.vehicleAuto.vehicle_d_gear_360_enabled ? 'D 挡起步 360 已开启' : 'D 挡起步 360 已关闭' }}
            </span>
          </div>
        </template>
      </PlanCard>

      <!-- 转向灯开启联动 360 全景 (车规标准 PlanCard，双列等高对称，整卡点击开关) -->
      <PlanCard
        title="转向灯开启联动 360"
        tag="变道盲区"
        help-text="turn_signal_360"
        flow-sub="打左/右转向灯时自动唤起 360 全景环视，消除变道盲区"
        :flow-main="store.vehicleAuto.vehicle_turn_signal_360_auto_exit !== false ? '倒车 R 挡避让原厂倒车；转向灯回正复位后自动退出 360 画面' : '倒车 R 挡避让原厂倒车；转向灯回正后持续保持 360 画面'"
        :clickable="true"
        :active="store.vehicleAuto.vehicle_turn_signal_360_enabled"
        @toggle="toggleTurnSignal360"
        @help="showHelp('turn_signal_360')"
      >
        <template #footer>
          <div class="flex items-center justify-between">
            <div class="flex items-center space-x-2">
              <StatusDot size="sm" :color="store.vehicleAuto.vehicle_turn_signal_360_enabled ? 'ok' : 'off'" :glow-px="6" />
              <span :class="['text-[16px] font-black', store.vehicleAuto.vehicle_turn_signal_360_enabled ? 'text-car-text' : 'text-car-sub']">
                {{ store.vehicleAuto.vehicle_turn_signal_360_enabled ? '转向灯联动 360 已开启' : '转向灯联动 360 已关闭' }}
              </span>
            </div>
            <div v-if="store.vehicleAuto.vehicle_turn_signal_360_enabled" class="shrink-0" @click.stop>
              <BaseButton
                variant="dual"
                :active="store.vehicleAuto.vehicle_turn_signal_360_auto_exit !== false"
                @click="toggleTurnSignal360AutoExit"
              >
                <span class="text-[17px] font-black text-car-text tracking-wide whitespace-nowrap">
                  {{ store.vehicleAuto.vehicle_turn_signal_360_auto_exit !== false ? '已开启' : '已关闭' }}
                </span>
                <span :class="['text-[12px] font-bold mt-0.5 whitespace-nowrap', store.vehicleAuto.vehicle_turn_signal_360_auto_exit !== false ? 'text-car-accent' : 'text-car-sub']">
                  {{ store.vehicleAuto.vehicle_turn_signal_360_auto_exit !== false ? '回正自动退出' : '回正持续保持' }}
                </span>
              </BaseButton>
            </div>
          </div>
        </template>
      </PlanCard>
    </div>

    <!-- 2. 车速与启播 (左右双列卡片，一屏收尽，整卡点击开关) -->
    <div v-if="activeTab === 'speed'" class="grid grid-cols-2 gap-4 items-stretch">
      <div
        @click="toggleSetting('vehicle_speed_autoplay_enabled')"
        :class="[
          'rounded-3xl border-2 p-5 shadow-xl h-full flex flex-col space-y-3.5 transition-all duration-200 cursor-pointer select-none active:scale-[0.985]',
          store.vehicleAuto.vehicle_speed_autoplay_enabled
            ? 'bg-car-item border-car-accent shadow-md'
            : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
        ]"
      >
        <div class="flex items-center justify-between shrink-0">
          <div class="flex items-center space-x-2.5">
            <span class="text-[20px] font-black text-car-text tracking-wide">车速达标智能启播音乐</span>
            <span class="px-2.5 py-0.5 text-[12.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent shrink-0">起步自启</span>
            <HelpDot size="sm" @click.stop="showHelp('speed_music')" />
          </div>
          <!-- 右上角：轻量状态指示灯 + 文字（消灭独立大按钮） -->
          <div class="flex items-center space-x-2 shrink-0">
            <StatusDot size="sm" :color="store.vehicleAuto.vehicle_speed_autoplay_enabled ? 'ok' : 'off'" :glow-px="6" />
            <span :class="['text-[15px] font-black', store.vehicleAuto.vehicle_speed_autoplay_enabled ? 'text-car-text' : 'text-car-sub']">
              {{ store.vehicleAuto.vehicle_speed_autoplay_enabled ? '已开启' : '已关闭' }}
            </span>
          </div>
        </div>

        <!-- 紧凑车速步进器 (一体化设计，不再与提示语层叠，阻止事件冒泡) -->
        <div class="flex items-center justify-between bg-car-item border-2 border-car-border rounded-2xl p-2 shrink-0" @click.stop>
          <div class="flex items-center space-x-1.5">
            <button 
              @click.stop="adjustAutoplaySpeed(-5)"
              class="h-[50px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
            >
              -5
            </button>
            <button 
              @click.stop="adjustAutoplaySpeed(-1)"
              class="h-[50px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
            >
              -1
            </button>
          </div>
          <div class="flex items-baseline space-x-1 px-3">
            <span class="text-[13.5px] font-bold text-car-sub mr-1">触发时速</span>
            <span class="text-[28px] font-black text-car-accent tracking-tight font-mono">
              {{ store.vehicleAuto.vehicle_speed_autoplay_threshold || 20 }}
            </span>
            <span class="text-[14px] text-car-sub font-black">km/h</span>
          </div>
          <div class="flex items-center space-x-1.5">
            <button 
              @click.stop="adjustAutoplaySpeed(1)"
              class="h-[50px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
            >
              +1
            </button>
            <button 
              @click.stop="adjustAutoplaySpeed(5)"
              class="h-[50px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
            >
              +5
            </button>
          </div>
        </div>

        <!-- 播放模式切换 (紧凑对称双按钮，阻止事件冒泡) -->
        <div class="grid grid-cols-2 gap-2.5 items-stretch shrink-0" @click.stop>
          <button 
            @click.stop="setAutoplayFullscreen(false)"
            :class="[
              'w-full h-[50px] rounded-xl font-black text-[14.5px] cursor-pointer transition-all border-2 whitespace-nowrap',
              !store.vehicleAuto.vehicle_speed_autoplay_fullscreen
                ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            后台静默播放
          </button>
          <button 
            @click.stop="setAutoplayFullscreen(true)"
            :class="[
              'w-full h-[50px] rounded-xl font-black text-[14.5px] cursor-pointer transition-all border-2 whitespace-nowrap',
              store.vehicleAuto.vehicle_speed_autoplay_fullscreen
                ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            前台全屏播放
          </button>
        </div>
      </div>

      <!-- 车速达标自定义动作与唤起应用 (紧凑车规卡片，高度对齐左侧，整卡点击开关) -->
      <div
        @click="toggleSetting('vehicle_speed_custom_action_enabled')"
        :class="[
          'rounded-3xl border-2 p-5 shadow-xl h-full flex flex-col space-y-3.5 transition-all duration-200 cursor-pointer select-none active:scale-[0.985]',
          store.vehicleAuto.vehicle_speed_custom_action_enabled
            ? 'bg-car-item border-car-accent shadow-md'
            : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
        ]"
      >
        <div class="flex items-center justify-between shrink-0">
          <div class="flex items-center space-x-2.5">
            <span class="text-[20px] font-black text-car-text tracking-wide">车速达标自定义动作</span>
            <span class="px-2.5 py-0.5 text-[12.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent shrink-0">自定动作</span>
            <HelpDot size="sm" @click.stop="showHelp('speed_custom')" />
          </div>
          <!-- 右上角：轻量状态指示灯 + 文字（消灭独立大按钮） -->
          <div class="flex items-center space-x-2 shrink-0">
            <StatusDot size="sm" :color="store.vehicleAuto.vehicle_speed_custom_action_enabled ? 'ok' : 'off'" :glow-px="6" />
            <span :class="['text-[15px] font-black', store.vehicleAuto.vehicle_speed_custom_action_enabled ? 'text-car-text' : 'text-car-sub']">
              {{ store.vehicleAuto.vehicle_speed_custom_action_enabled ? '已开启' : '已关闭' }}
            </span>
          </div>
        </div>

        <!-- 紧凑车速步进器 (阻止事件冒泡) -->
        <div class="flex items-center justify-between bg-car-item border-2 border-car-border rounded-2xl p-2 shrink-0" @click.stop>
          <div class="flex items-center space-x-1.5">
            <button 
              @click.stop="adjustCustomActionSpeed(-5)"
              class="h-[50px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
            >
              -5
            </button>
            <button 
              @click.stop="adjustCustomActionSpeed(-1)"
              class="h-[50px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
            >
              -1
            </button>
          </div>
          <div class="flex items-baseline space-x-1 px-3">
            <span class="text-[13.5px] font-bold text-car-sub mr-1">触发时速</span>
            <span class="text-[28px] font-black text-car-accent tracking-tight font-mono">
              {{ store.vehicleAuto.vehicle_speed_custom_action_threshold || 40 }}
            </span>
            <span class="text-[14px] text-car-sub font-black">km/h</span>
          </div>
          <div class="flex items-center space-x-1.5">
            <button 
              @click.stop="adjustCustomActionSpeed(1)"
              class="h-[50px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
            >
              +1
            </button>
            <button 
              @click.stop="adjustCustomActionSpeed(5)"
              class="h-[50px] px-3.5 rounded-xl bg-car-card border-2 border-car-border hover:border-car-accent text-car-text font-black text-[15px] cursor-pointer active:scale-95 transition-all shadow-sm"
            >
              +5
            </button>
          </div>
        </div>

        <!-- 动作唤起目标选择 (紧凑对称双按钮，高度与左侧完全对齐，阻止事件冒泡) -->
        <div class="grid grid-cols-2 gap-2.5 items-stretch shrink-0" @click.stop>
          <button 
            @click.stop="setCustomActionTarget('pkg:com.autonavi.amapauto')"
            :class="[
              'w-full h-[50px] rounded-xl font-black text-[15px] cursor-pointer transition-all border-2 whitespace-nowrap',
              store.vehicleAuto.vehicle_speed_custom_action_target === 'pkg:com.autonavi.amapauto'
                ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            高德车机地图
          </button>
          <button 
            @click.stop="openSelectModal('speed_custom_action')"
            :class="[
              'w-full h-[50px] rounded-xl font-black text-[15px] cursor-pointer transition-all border-2 whitespace-nowrap truncate px-3',
              store.vehicleAuto.vehicle_speed_custom_action_target?.startsWith('pkg:') && store.vehicleAuto.vehicle_speed_custom_action_target !== 'pkg:com.autonavi.amapauto'
                ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            {{ customActionAppName ? customActionAppName : '自选应用 ➔' }}
          </button>
        </div>
      </div>

      <!-- 停稳推门多媒体暂停 (整卡点击开关) -->
      <PlanCard
        title="停稳推门多媒体暂停"
        tag="下车静音"
        help-text="door_pause"
        flow-sub="挂入驻车 P 挡且推开前排车门（准备下车）"
        flow-main="自动向音乐播放器发送暂停指令，下车优雅安静"
        :clickable="true"
        :active="store.vehicleAuto.vehicle_door_pause_music_enabled"
        @toggle="toggleSetting('vehicle_door_pause_music_enabled')"
        @help="showHelp('door_pause')"
      >
        <template #footer>
          <div class="flex items-center space-x-2">
            <StatusDot size="sm" :color="store.vehicleAuto.vehicle_door_pause_music_enabled ? 'ok' : 'off'" :glow-px="6" />
            <span :class="['text-[16px] font-black', store.vehicleAuto.vehicle_door_pause_music_enabled ? 'text-car-text' : 'text-car-sub']">
              {{ store.vehicleAuto.vehicle_door_pause_music_enabled ? '推门暂停音乐已开启' : '推门暂停音乐已关闭' }}
            </span>
          </div>
        </template>
      </PlanCard>

      <!-- 进隧道大灯联动高德日夜模式 (高德 7.5 经典通道，整卡点击开关) -->
      <PlanCard
        title="进隧道大灯联动高德"
        tag="日夜深浅"
        help-text="light_nav"
        flow-sub="白天行车开启前大灯（驶入地下车库或长隧道）"
        flow-main="秒级切为夜间深色导航；关大灯自动恢复白天浅色"
        :clickable="true"
        :active="store.vehicleAuto.vehicle_headlight_nav_night_enabled"
        @toggle="toggleSetting('vehicle_headlight_nav_night_enabled')"
        @help="showHelp('light_nav')"
      >
        <template #footer>
          <div class="flex items-center space-x-2">
            <StatusDot size="sm" :color="store.vehicleAuto.vehicle_headlight_nav_night_enabled ? 'ok' : 'off'" :glow-px="6" />
            <span :class="['text-[16px] font-black', store.vehicleAuto.vehicle_headlight_nav_night_enabled ? 'text-car-text' : 'text-car-sub']">
              {{ store.vehicleAuto.vehicle_headlight_nav_night_enabled ? '大灯联动高德已开启' : '大灯联动高德已关闭' }}
            </span>
          </div>
        </template>
      </PlanCard>
    </div>

    <!-- 3. 网络与外设连接中枢 (一行三列大卡片，一屏充满，整卡点击开关) -->
    <div v-if="activeTab === 'network'" class="grid grid-cols-3 gap-4 items-stretch">
      <!-- 卡片 1: 车载 Wi-Fi (整卡点击开关) -->
      <div
        @click="toggleDeviceDirect('wifi')"
        :class="[
          'rounded-3xl border-2 p-5 shadow-xl flex flex-col justify-between transition-all duration-200 cursor-pointer select-none active:scale-[0.985]',
          store.networkDevice.wifi_enabled
            ? 'bg-car-item border-car-accent shadow-md'
            : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
        ]"
      >
        <!-- 头部 -->
        <div class="flex items-center justify-between shrink-0 mb-3">
          <div class="flex items-center space-x-2.5">
            <span class="text-[20px] font-black text-car-text tracking-wide">车载 Wi-Fi</span>
            <span class="px-2.5 py-0.5 text-[12.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent shrink-0">无线网络</span>
            <HelpDot size="sm" @click.stop="showHelp('network_policy')" />
          </div>
          <button
            @click.stop="openSettings('wifi')"
            class="h-[50px] px-4 rounded-xl border border-car-border bg-car-item hover:border-car-border-light text-car-text font-black text-[14.5px] cursor-pointer transition-all flex items-center space-x-1.5 shrink-0 shadow-sm"
          >
            <span>系统设置</span>
            <svg class="w-4 h-4 text-car-sub" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 18l6-6-6-6"/>
            </svg>
          </button>
        </div>

        <!-- 中部：状态与开机策略 (阻止冒泡) -->
        <div class="flex-1 min-w-0 flex flex-col justify-center space-y-3 py-2" @click.stop>
          <div class="text-[15.5px] leading-relaxed truncate font-mono">
            <span class="text-car-sub font-bold">网络: </span>
            <span class="text-car-text font-black">
              {{ store.networkDevice.wifi_enabled ? (store.networkDevice.wifi_ssid && store.networkDevice.wifi_ssid !== '未连接外部网络' ? store.networkDevice.wifi_ssid : '未连接热点') : '已关闭 (省电)' }}
            </span>
          </div>

          <!-- 开机策略三选一 -->
          <div class="flex flex-col space-y-1.5 p-3 rounded-2xl bg-car-item border border-car-border">
            <span class="text-[12.5px] font-black text-car-sub">开机自启策略:</span>
            <div class="grid grid-cols-3 gap-1.5">
              <button
                v-for="item in policyOptions"
                :key="'wifi_' + item.value"
                @click.stop="changeBootPolicy('wifi', item.value)"
                :class="[
                  'h-[50px] rounded-xl font-black text-[14px] cursor-pointer transition-all border-2 text-center flex items-center justify-center',
                  store.networkDevice.wifi_policy === item.value
                    ? 'bg-car-card border-car-accent text-car-accent shadow-sm'
                    : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
                ]"
              >
                {{ item.label }}
              </button>
            </div>
          </div>
        </div>

        <!-- 底部标准状态灯 -->
        <div class="pt-4 border-t border-car-border/60 shrink-0 flex items-center space-x-2">
          <StatusDot size="sm" :color="store.networkDevice.wifi_enabled ? 'ok' : 'off'" :glow-px="6" />
          <span :class="['text-[16px] font-black', store.networkDevice.wifi_enabled ? 'text-car-text' : 'text-car-sub']">
            {{ store.networkDevice.wifi_enabled ? 'Wi-Fi 已开启' : 'Wi-Fi 已关闭' }}
          </span>
        </div>
      </div>

      <!-- 卡片 2: 车机热点分享 (整卡点击开关) -->
      <div
        @click="toggleDeviceDirect('hotspot')"
        :class="[
          'rounded-3xl border-2 p-5 shadow-xl flex flex-col justify-between transition-all duration-200 cursor-pointer select-none active:scale-[0.985]',
          store.networkDevice.hotspot_enabled
            ? 'bg-car-item border-car-accent shadow-md'
            : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
        ]"
      >
        <!-- 头部 -->
        <div class="flex items-center justify-between shrink-0 mb-3">
          <div class="flex items-center space-x-2.5">
            <span class="text-[20px] font-black text-car-text tracking-wide">车机热点</span>
            <span class="px-2.5 py-0.5 text-[12.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent shrink-0">网络共享</span>
            <HelpDot size="sm" @click.stop="showHelp('network_policy')" />
          </div>
          <button
            @click.stop="openSettings('hotspot')"
            class="h-[50px] px-4 rounded-xl border border-car-border bg-car-item hover:border-car-border-light text-car-text font-black text-[14.5px] cursor-pointer transition-all flex items-center space-x-1.5 shrink-0 shadow-sm"
          >
            <span>系统设置</span>
            <svg class="w-4 h-4 text-car-sub" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 18l6-6-6-6"/>
            </svg>
          </button>
        </div>

        <!-- 中部：状态与开机策略 (阻止冒泡) -->
        <div class="flex-1 min-w-0 flex flex-col justify-center space-y-3 py-2" @click.stop>
          <div class="text-[15.5px] leading-relaxed truncate font-mono">
            <span class="text-car-sub font-bold">广播: </span>
            <span class="text-car-text font-black">
              {{ store.networkDevice.hotspot_enabled ? (store.networkDevice.hotspot_ssid ? store.networkDevice.hotspot_ssid : '已开启广播') : '已关闭 (未广播)' }}
            </span>
          </div>

          <!-- 开机策略三选一 -->
          <div class="flex flex-col space-y-1.5 p-3 rounded-2xl bg-car-item border border-car-border">
            <span class="text-[12.5px] font-black text-car-sub">开机自启策略:</span>
            <div class="grid grid-cols-3 gap-1.5">
              <button
                v-for="item in policyOptions"
                :key="'ap_' + item.value"
                @click.stop="changeBootPolicy('hotspot', item.value)"
                :class="[
                  'h-[50px] rounded-xl font-black text-[14px] cursor-pointer transition-all border-2 text-center flex items-center justify-center',
                  store.networkDevice.hotspot_policy === item.value
                    ? 'bg-car-card border-car-accent text-car-accent shadow-sm'
                    : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
                ]"
              >
                {{ item.label }}
              </button>
            </div>
          </div>
        </div>

        <!-- 底部标准状态灯 -->
        <div class="pt-4 border-t border-car-border/60 shrink-0 flex items-center space-x-2">
          <StatusDot size="sm" :color="store.networkDevice.hotspot_enabled ? 'ok' : 'off'" :glow-px="6" />
          <span :class="['text-[16px] font-black', store.networkDevice.hotspot_enabled ? 'text-car-text' : 'text-car-sub']">
            {{ store.networkDevice.hotspot_enabled ? '热点分享已开启' : '热点分享已关闭' }}
          </span>
        </div>
      </div>

      <!-- 卡片 3: 车载蓝牙外设 (整卡点击开关) -->
      <div
        @click="toggleDeviceDirect('bluetooth')"
        :class="[
          'rounded-3xl border-2 p-5 shadow-xl flex flex-col justify-between transition-all duration-200 cursor-pointer select-none active:scale-[0.985]',
          store.networkDevice.bluetooth_enabled
            ? 'bg-car-item border-car-accent shadow-md'
            : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
        ]"
      >
        <!-- 头部 -->
        <div class="flex items-center justify-between shrink-0 mb-3">
          <div class="flex items-center space-x-2.5">
            <span class="text-[20px] font-black text-car-text tracking-wide">车载蓝牙</span>
            <span class="px-2.5 py-0.5 text-[12.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent shrink-0">车载外设</span>
            <HelpDot size="sm" @click.stop="showHelp('network_policy')" />
          </div>
          <button
            @click.stop="openSettings('bluetooth')"
            class="h-[50px] px-4 rounded-xl border border-car-border bg-car-item hover:border-car-border-light text-car-text font-black text-[14.5px] cursor-pointer transition-all flex items-center space-x-1.5 shrink-0 shadow-sm"
          >
            <span>系统设置</span>
            <svg class="w-4 h-4 text-car-sub" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 18l6-6-6-6"/>
            </svg>
          </button>
        </div>

        <!-- 中部：状态与开机策略 (阻止冒泡) -->
        <div class="flex-1 min-w-0 flex flex-col justify-center space-y-3 py-2" @click.stop>
          <div class="text-[15.5px] leading-relaxed truncate font-mono">
            <span class="text-car-sub font-bold">设备: </span>
            <span class="text-car-text font-black">
              {{ store.networkDevice.bluetooth_enabled ? (store.networkDevice.bluetooth_device_name && store.networkDevice.bluetooth_device_name !== '未连接设备' ? store.networkDevice.bluetooth_device_name : '未连接设备') : '已关闭 (不打扰)' }}
            </span>
          </div>

          <!-- 开机策略三选一 -->
          <div class="flex flex-col space-y-1.5 p-3 rounded-2xl bg-car-item border border-car-border">
            <span class="text-[12.5px] font-black text-car-sub">开机自启策略:</span>
            <div class="grid grid-cols-3 gap-1.5">
              <button
                v-for="item in policyOptions"
                :key="'bt_' + item.value"
                @click.stop="changeBootPolicy('bluetooth', item.value)"
                :class="[
                  'h-[50px] rounded-xl font-black text-[14px] cursor-pointer transition-all border-2 text-center flex items-center justify-center',
                  store.networkDevice.bluetooth_policy === item.value
                    ? 'bg-car-card border-car-accent text-car-accent shadow-sm'
                    : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
                ]"
              >
                {{ item.label }}
              </button>
            </div>
          </div>
        </div>

        <!-- 底部标准状态灯 -->
        <div class="pt-4 border-t border-car-border/60 shrink-0 flex items-center space-x-2">
          <StatusDot size="sm" :color="store.networkDevice.bluetooth_enabled ? 'ok' : 'off'" :glow-px="6" />
          <span :class="['text-[16px] font-black', store.networkDevice.bluetooth_enabled ? 'text-car-text' : 'text-car-sub']">
            {{ store.networkDevice.bluetooth_enabled ? '蓝牙已开启' : '蓝牙已关闭' }}
          </span>
        </div>
      </div>
    </div>

    <!-- 4. 音乐音源优先级与回退调度已归入「车载声音」大类 -->
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { 
  store, bridge, showToast, openModal, getInstalledMusicAppsCached,
  setNetworkBootPolicy, toggleNetworkDeviceDirect, initNetworkDeviceStatus,
  openDeviceSettings
} from '../store';
import SubTabs from '../components/SubTabs.vue';
import PlanCard from '../components/PlanCard.vue';
import BaseButton from '../components/BaseButton.vue';
import StatusDot from '../components/StatusDot.vue';
import HelpDot from '../components/HelpDot.vue';
import WheelView from './WheelView.vue';

const activeTab = ref('wheel_keys');
const linkTabs = [
  { id: 'wheel_keys', name: '按键映射手势', tag: '6大按键' },
  { id: 'wheel_mode', name: '接管模式图解', tag: '工作模式' },
  { id: '360', name: '360全景联动', tag: 'D挡/转向灯' },
  { id: 'speed', name: '车速与大灯', tag: '车速/大灯' },
  { id: 'network', name: '网络与外设', tag: 'Wi-Fi/蓝牙' }
];

const hasQQMusic = ref(false);
const hasNeteaseMusic = ref(false);
const customAutoplayAppName = ref('');
const customActionAppName = ref('');

const policyOptions = [
  { label: '保持现状', value: 0 },
  { label: '默认开启', value: 1 },
  { label: '默认关闭', value: 2 }
];

const toggleDeviceDirect = async (type) => {
  const cur = !!store.networkDevice[`${type}_enabled`];
  const next = !cur;
  await toggleNetworkDeviceDirect(type, next);
  const nameMap = { wifi: 'Wi-Fi', hotspot: '热点分享', bluetooth: '蓝牙' };
  if (type === 'hotspot') {
    showToast(next ? '热点指令已下发。供手机上网用；若需访问助手后台，请用手机开热点供车机连接' : '热点分享已关闭');
  } else {
    showToast(`${nameMap[type]} 已切换为 ${next ? '开启' : '关闭'}`);
  }
};

const changeBootPolicy = (type, val) => {
  setNetworkBootPolicy(type, val);
  const nameMap = { wifi: 'Wi-Fi', hotspot: '热点分享', bluetooth: '蓝牙' };
  const polMap = { 0: '保持现状', 1: '默认开启', 2: '默认关闭' };
  showToast(`已设置 ${nameMap[type]} 开机策略为：${polMap[val]}`);
};

let settingsLockUntil = 0;
const openSettings = (type) => {
  const now = Date.now();
  if (now < settingsLockUntil) return;
  settingsLockUntil = now + 900;
  openDeviceSettings(type);
};

const toggleTurnSignal360 = () => {
  const nextVal = !store.vehicleAuto.vehicle_turn_signal_360_enabled;
  store.vehicleAuto.vehicle_turn_signal_360_enabled = nextVal;
  bridge.call('setVehicleAutomationSetting', 'vehicle_turn_signal_360_enabled', nextVal);
  showToast(nextVal ? '转向灯联动 360 已开启' : '转向灯联动 360 已关闭');
};

const toggleTurnSignal360AutoExit = () => {
  const current = store.vehicleAuto.vehicle_turn_signal_360_auto_exit !== false;
  const nextVal = !current;
  store.vehicleAuto.vehicle_turn_signal_360_auto_exit = nextVal;
  bridge.call('setVehicleAutomationSetting', 'vehicle_turn_signal_360_auto_exit', nextVal);
  showToast(nextVal ? '转向灯回正自动退出已开启' : '已关闭自动退出，转向灯回正后保持 360');
};

const isCustomAutoplaySelected = computed(() => {
  const currentPkg = store.vehicleAuto.vehicle_speed_autoplay_pkg;
  if (!currentPkg) return false;
  return currentPkg !== 'com.tencent.qqmusiccar' && 
         currentPkg !== 'com.netease.cloudmusiccar' && 
         currentPkg !== 'com.android.bluetooth';
});

const currentPrimaryMusicName = computed(() => {
  const pkg = store.vehicleAuto.vehicle_speed_autoplay_pkg;
  if (!pkg || pkg === 'com.android.bluetooth') return '手机蓝牙 (无线音频)';
  if (pkg === 'com.tencent.qqmusiccar') return 'QQ音乐车机版';
  if (pkg === 'com.netease.cloudmusiccar') return '网易云音乐车机版';
  const saved = localStorage.getItem('vehicle_speed_autoplay_app_name');
  return saved ? `自选应用: ${saved}` : (pkg || '未指定音源');
});

function loadCustomActionAppName() {
  customActionAppName.value = localStorage.getItem('vehicle_speed_custom_action_app_name') || '';
}

function loadCustomAutoplayAppName() {
  const currentPkg = store.vehicleAuto.vehicle_speed_autoplay_pkg;
  if (currentPkg && isCustomAutoplaySelected.value) {
    const savedName = localStorage.getItem('vehicle_speed_autoplay_app_name');
    customAutoplayAppName.value = savedName ? `自选: ${savedName}` : '自选应用 ➔';
  } else {
    customAutoplayAppName.value = '';
  }
}

function checkInstalledMusicApps(force = false) {
  try {
    const list = getInstalledMusicAppsCached(force);
    if (list && list.length > 0) {
      const pkgs = list.map(item => item.pkg || item.packageName);
      hasQQMusic.value = pkgs.includes('com.tencent.qqmusiccar');
      hasNeteaseMusic.value = pkgs.includes('com.netease.cloudmusiccar');
    } else {
      hasQQMusic.value = true;
      hasNeteaseMusic.value = true;
    }
  } catch (e) {
    // 降级兜底：默认置 true 供前端调试/兜底
    hasQQMusic.value = true;
    hasNeteaseMusic.value = true;
  }

  // 默认选择逻辑：若检测到安装了 QQ 音乐，默认置为 QQ 音乐并默认开启自动播放
  if (hasQQMusic.value) {
    if (!store.vehicleAuto.vehicle_speed_autoplay_pkg) {
      store.vehicleAuto.vehicle_speed_autoplay_pkg = 'com.tencent.qqmusiccar';
    }
    if (store.vehicleAuto.vehicle_speed_autoplay_enabled === undefined || store.vehicleAuto.vehicle_speed_autoplay_enabled === null) {
      store.vehicleAuto.vehicle_speed_autoplay_enabled = true;
    }
  } else {
    if (!store.vehicleAuto.vehicle_speed_autoplay_pkg) {
      store.vehicleAuto.vehicle_speed_autoplay_pkg = 'com.android.bluetooth';
    }
  }

  loadCustomAutoplayAppName();
}

function toggleSetting(key) {
  const next = !store.vehicleAuto[key];
  store.vehicleAuto[key] = next;
  bridge.call('setVehicleAutomationSetting', key, next);
  
  // 双向兼容映射：保证 Java 后端能百分之百接收对应真实 key
  const aliasMap = {
    vehicle_d_gear_360_enabled: 'vehicle_gear_d_360_enabled',
    vehicle_gear_d_360_enabled: 'vehicle_d_gear_360_enabled'
  };
  if (aliasMap[key]) {
    store.vehicleAuto[aliasMap[key]] = next;
    bridge.call('setVehicleAutomationSetting', aliasMap[key], next);
  }
  showToast('联动设置已更新: ' + (next ? '已开启' : '已关闭'));
}

function setAutoplaySpeed(speed) {
  store.vehicleAuto.vehicle_speed_autoplay_threshold = speed;
  bridge.call('setVehicleAutomationIntSetting', 'vehicle_speed_autoplay_threshold', speed);
  showToast(`自动播放车速已设为: ${speed} km/h`);
}

function adjustAutoplaySpeed(delta) {
  let current = store.vehicleAuto.vehicle_speed_autoplay_threshold || 20;
  let next = Math.max(1, Math.min(120, current + delta));
  setAutoplaySpeed(next);
}

function setCustomActionSpeed(speed) {
  store.vehicleAuto.vehicle_speed_custom_action_threshold = speed;
  bridge.call('setVehicleAutomationIntSetting', 'vehicle_speed_custom_action_threshold', speed);
  showToast(`触发车速已设为: ${speed} km/h`);
}

function adjustCustomActionSpeed(delta) {
  let current = store.vehicleAuto.vehicle_speed_custom_action_threshold || 40;
  let next = Math.max(1, Math.min(120, current + delta));
  setCustomActionSpeed(next);
}

function setCustomActionTarget(target) {
  store.vehicleAuto.vehicle_speed_custom_action_target = target;
  bridge.call('setWheelControlStringSetting', 'vehicle_speed_custom_action_target', target);
  if (target === 'pkg:com.autonavi.amapauto') {
    showToast('车速达标联动目标已设为: 打开高德车机地图');
  }
}

function setAutoplayApp(pkg, name) {
  store.vehicleAuto.vehicle_speed_autoplay_pkg = pkg;
  bridge.call('setWheelControlStringSetting', 'vehicle_speed_autoplay_pkg', pkg);
  if (name) {
    localStorage.setItem('vehicle_speed_autoplay_app_name', name);
  }
  loadCustomAutoplayAppName();
  showToast(`自动播放应用已设为: ${name || pkg}`);
}

function setAutoplayFullscreen(fullscreen) {
  store.vehicleAuto.vehicle_speed_autoplay_fullscreen = fullscreen;
  bridge.call('setVehicleAutomationSetting', 'vehicle_speed_autoplay_fullscreen', fullscreen);
  showToast(fullscreen ? '已设为前台全屏大屏打开' : '已设为后台静默放歌 (保持高德导航)');
}

function openSelectModal(target) {
  openModal('appSelect', { keyTarget: target });
}

function showHelp(key) {
  const helpData = {
    gear_d_360: {
      title: '【功能指南】前进 D 挡起步 360 全景',
      desc: '1. 智能单次触发：从 P 挡或 R 挡切入前进 D 挡瞬间自动打开 360 环视四周；本次起步仅触发一次，手动退出后绝不反复弹出。\n\n2. 自动退出：起步车速超过 15km/h 自动退出全景，保持导航视线。',
      tip: '换出 D 挡（如重新挂入 P 挡）后才会进入下一次待命。'
    },
    door_pause: {
      title: '【功能指南】P 挡开门音乐暂停',
      desc: '挂入驻车 P 挡推开前门，毫秒级自动向播放器发送暂停指令，下车从容安静。（车门安全播报由【车载语音】专职管理，零冲突零重复）',
      tip: '仅在挂 P 挡停稳推门生效，行车中推门不误触。'
    },
    speed_music: {
      title: '【功能指南】车速达标智能启播音乐',
      desc: '1. 智能起步判断：挂入 D 挡起步行驶，车速达到设定速度瞬间自动播放选择的音乐软件。\n\n2. 单次行程防抖：单次行驶过程中仅触发一次，中途等红绿灯停下或减速再次起步绝不重复触发。\n\n3. 自动重置状态：车辆停稳重新挂入 P 挡后，自动重置状态，等待下一次起步。\n\n4. 播放说明：车机刚打火开机且音乐软件从未打开过时，部分三方音乐软件需要联网加载账号信息，需要几秒时间；若平时软件已经在后台，达到设定时速即可瞬间恢复播放。',
      tip: '支持后台静默播放（不抢导航视线）或前台全屏大屏沉浸播放。刚开机时稍等片刻联网，平时在后台秒播。'
    },
    speed_custom: {
      title: '【功能指南】车速达标自定义动作',
      desc: '车速达到设定数值后，自动打开高德车机地图或自选的任意第三方应用。单次行程仅触发一次。',
      tip: '常用导航建议设为「高德车机地图」。'
    },
    light_nav: {
      title: '【功能指南】进隧道大灯联动高德',
      desc: '1. 自动深色护眼：\n白天开大灯（进地下车库或长隧道）时，毫秒级向高德地图发送夜间模式指令，自动切换为深色黑底，告别刺眼白屏。\n\n2. 出隧道自动恢复：\n出隧道或离开地库关闭大灯后，自动恢复白天清爽浅色地图。\n\n3. 版本适配说明：\n本功能针对高德地图 7.5 经典版深度适配生效。',
      tip: '使用高德 7.5 经典版的车友推荐开启。'
    },
    turn_signal_360: {
      title: '【功能指南】转向灯开启联动 360',
      desc: '1. 盲区辅助：打左或右转向灯时，自动唤醒 360 全景环视影像，消除后视镜与侧向盲区。\n\n2. 自动退出：转向灯拨杆回正关闭后，360 全景自动退出并还原原屏幕界面。\n\n3. 倒车避让：倒车 R 挡状态下不打扰原厂倒车影像。',
      tip: '适合原厂未配备转向灯联动 360 的车型开启。'
    },
    network_policy: {
      title: '【功能指南】车载网络与外设连接中枢',
      desc: '1. 快速控制：随时手动点击开关，状态实时同步更新。\n\n2. 开机策略：支持选择保持现状、默认开启或默认关闭。例如设置开机默认打开热点，手机上车即可无感自动连入车机网络；设置开机默认关闭 Wi-Fi，可避免车机在后台频繁扫描周围热点带来的卡顿。\n\n3. 智能就绪启动：车机开机后会在后台等待网络硬件准备完毕，随后平稳开启，流畅不卡顿。',
      tip: '支持 Wi-Fi、热点与蓝牙三通道独立策略管理。'
    }
  };
  const item = helpData[key];
  if (item) {
    openModal('confirm', {
      title: item.title,
      desc: item.desc,
      tip: item.tip,
      showCancel: false,
      confirmText: '我知道了'
    });
  }
}

onMounted(() => {
  if (store.vehicleAuto.vehicle_gear_d_360_enabled !== undefined) {
    store.vehicleAuto.vehicle_d_gear_360_enabled = store.vehicleAuto.vehicle_gear_d_360_enabled;
  }
  initNetworkDeviceStatus();
  checkInstalledMusicApps();
  loadCustomActionAppName();
  window.addEventListener('music-order-updated', checkInstalledMusicApps);
  window.addEventListener('custom-action-target-updated', loadCustomActionAppName);
});

onUnmounted(() => {
  window.removeEventListener('music-order-updated', checkInstalledMusicApps);
  window.removeEventListener('custom-action-target-updated', loadCustomActionAppName);
});
</script>
