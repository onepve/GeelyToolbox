<template>
  <div class="flex flex-col space-y-6">
    <!-- 1. 转向灯联动 360 全景盲区 (整卡一体化：整卡即触控大靶区 · 右侧纯文字无多余按钮框) -->
    <div 
      @click="toggleSetting('vehicle_turn_360_enabled')"
      :class="[
        'rounded-3xl border-2 p-6 transition-all flex items-center justify-between cursor-pointer select-none shadow-xl',
        store.vehicleAuto.vehicle_turn_360_enabled
          ? 'bg-car-card border-car-accent ring-2 ring-car-accent/20'
          : 'bg-car-card border-car-border hover:border-car-border-light'
      ]"
    >
      <div class="flex-1 min-w-0 pr-6 flex flex-col space-y-2">
        <div class="flex items-center space-x-3">
          <span class="text-[23px] font-black text-car-text tracking-wide">1. 转向灯联动 360 全景盲区影像</span>
          <button 
            @click.stop="showHelp('turn_360')" 
            class="w-8 h-8 rounded-full bg-car-item border-2 border-car-accent text-car-accent font-black text-[16px] flex items-center justify-center cursor-pointer shadow-sm shrink-0"
            title="查看功能指南"
          >?</button>
          <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent">盲区防御</span>
        </div>
        <div class="text-[15.5px] text-car-sub font-bold leading-relaxed">
          实时监听底层原厂 AVM 环视状态机与转向拨杆电平。打起转向灯秒级唤起 360 盲区影像，方向盘回正自动退出；时速超过 30km/h 变道自动静默抑制不挡导航。
        </div>
      </div>
      <!-- 右侧：纯文字状态呈现，彻底去除多余的按钮矩形小框！ -->
      <div class="min-w-[240px] shrink-0 flex flex-col items-end justify-center text-right">
        <div class="flex items-center space-x-2.5">
          <span :class="['w-3 h-3 rounded-full', store.vehicleAuto.vehicle_turn_360_enabled ? 'bg-emerald-500 shadow-[0_0_10px_#10B981]' : 'bg-slate-500']"></span>
          <span :class="['text-[22px] font-black tracking-wide', store.vehicleAuto.vehicle_turn_360_enabled ? 'text-car-text' : 'text-car-sub']">
            {{ store.vehicleAuto.vehicle_turn_360_enabled ? '360联动已开启' : '360联动已关闭' }}
          </span>
        </div>
        <span :class="['text-[13.5px] font-bold mt-1.5', store.vehicleAuto.vehicle_turn_360_enabled ? 'text-car-accent' : 'text-car-sub']">
          {{ store.vehicleAuto.vehicle_turn_360_enabled ? '打转向灯秒开 · 点击整卡关闭' : '点击整卡开启盲区联动' }}
        </span>
      </div>
    </div>

    <!-- 2. 前进 D 挡起步联动 360 全景 (整卡一体化 · 严格单次跃变锁) -->
    <div 
      @click="toggleSetting('vehicle_gear_d_360_enabled')"
      :class="[
        'rounded-3xl border-2 p-6 transition-all flex items-center justify-between cursor-pointer select-none shadow-xl',
        store.vehicleAuto.vehicle_gear_d_360_enabled
          ? 'bg-car-card border-car-accent ring-2 ring-car-accent/20'
          : 'bg-car-card border-car-border hover:border-car-border-light'
      ]"
    >
      <div class="flex-1 min-w-0 pr-6 flex flex-col space-y-2">
        <div class="flex items-center space-x-3">
          <span class="text-[23px] font-black text-car-text tracking-wide">2. 前进 D 挡起步联动 360 全景</span>
          <button 
            @click.stop="showHelp('gear_d_360')" 
            class="w-8 h-8 rounded-full bg-car-item border-2 border-car-accent text-car-accent font-black text-[16px] flex items-center justify-center cursor-pointer shadow-sm shrink-0"
            title="查看功能指南"
          >?</button>
          <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent">单次跃变锁</span>
        </div>
        <div class="text-[15.5px] text-car-sub font-bold leading-relaxed">
          挂入前进 D 挡起步瞬间唤醒 360 全景环视车身四周障碍物。内置严格单次跃变武装锁，手动退出后绝不反复循环调起，车速超 15km/h 自动退出。
        </div>
      </div>
      <div class="min-w-[240px] shrink-0 flex flex-col items-end justify-center text-right">
        <div class="flex items-center space-x-2.5">
          <span :class="['w-3 h-3 rounded-full', store.vehicleAuto.vehicle_gear_d_360_enabled ? 'bg-emerald-500 shadow-[0_0_10px_#10B981]' : 'bg-slate-500']"></span>
          <span :class="['text-[22px] font-black tracking-wide', store.vehicleAuto.vehicle_gear_d_360_enabled ? 'text-car-text' : 'text-car-sub']">
            {{ store.vehicleAuto.vehicle_gear_d_360_enabled ? 'D挡360已开启' : 'D挡360已关闭' }}
          </span>
        </div>
        <span :class="['text-[13.5px] font-bold mt-1.5', store.vehicleAuto.vehicle_gear_d_360_enabled ? 'text-car-accent' : 'text-car-sub']">
          {{ store.vehicleAuto.vehicle_gear_d_360_enabled ? '起步单次唤醒 · 点击整卡关闭' : '点击整卡开启起步全景' }}
        </span>
      </div>
    </div>

    <!-- 3. 车速联动与起步多媒体智能启播 (复合功能 · 纯加减数值控制 · 无死板预设按钮) -->
    <FeatureCard 
      title="3. 车速联动与起步多媒体智能启播"
      desc="挂 D 挡出发达到设定时速，后台自动起播自选音乐 App。内置单次行程防抖闭环，等红绿灯不重复放歌；若车主上车已在放歌则静默放行。"
      helpTitle="【功能指南】车速自启多媒体与单次行程防抖闭环"
      helpText="1. 车速自启逻辑：&#10;车辆点火出库挂入 D 挡起步，车速达到你加减设定的目标时速时，后台自动向目标音乐软件发送播放指令，上车无需再手动翻点屏幕放歌。&#10;&#10;2. 单次行程防抖状态机：&#10;吸取 Tasker 状态机精髓并进行了原生重构。单次行程中达到速度仅触发一次播放指令，随后状态机自动锁定；路上遇到红绿灯、堵车起步绝不重复拉起应用或重复切歌。只有车辆停稳并挂回 P 挡后才会重新复位待命。&#10;&#10;3. 已有播放智能避让：&#10;达到速度时底层会先探查全局 AudioFocus 音频焦点与 MediaSession 状态。若车主在上车前就已经在放歌，系统会静默放行，绝不粗暴打断现有歌曲。"
      helpTip="时速完全支持加减微调自由设定，推荐设为 20 km/h 并选择「后台静默放歌」。"
    >
      <template #badge>
        <span class="text-[15px] font-black text-car-accent px-3.5 py-1 bg-car-item rounded-xl border border-car-border whitespace-nowrap">
          当前时速: {{ store.vehicleAuto.vehicle_speed_autoplay_threshold || 20 }} km/h
        </span>
      </template>

      <div class="flex flex-col space-y-5">
        <!-- 总开关长条行 -->
        <div 
          @click="toggleSetting('vehicle_speed_autoplay_enabled')"
          class="flex items-center justify-between p-6 rounded-2xl bg-car-item border-2 border-car-border shadow-sm cursor-pointer hover:border-car-border-light transition-all"
        >
          <div class="w-[60%] max-w-[60%] flex flex-col space-y-1.5 shrink-0">
            <div class="flex items-center space-x-3">
              <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">车速自动启播总开关</span>
              <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-card border-car-border text-car-accent inline-flex items-center shadow-sm">
                行程防抖
              </span>
            </div>
            <div class="text-[15px] text-car-sub font-bold leading-relaxed">
              点火出 P 挡起步达到设定车速触发 1 次；等红绿灯不重复放歌；停车回 P 挡归零重置。
            </div>
          </div>
          <div class="shrink-0 w-[230px]">
            <div 
              :class="[
                'w-full h-[78px] px-4 py-2 rounded-2xl border-2 transition-all shadow-md flex flex-col items-center justify-center text-center',
                store.vehicleAuto.vehicle_speed_autoplay_enabled
                  ? 'bg-car-item border-car-accent text-car-text font-black'
                  : 'bg-car-card border-car-border text-car-sub'
              ]"
            >
              <span class="text-[19px] font-black text-car-text tracking-wide whitespace-nowrap">
                {{ store.vehicleAuto.vehicle_speed_autoplay_enabled ? '车速自启已开启' : '车速自启已关闭' }}
              </span>
              <span :class="['text-[13px] font-bold mt-1 whitespace-nowrap', store.vehicleAuto.vehicle_speed_autoplay_enabled ? 'text-car-accent' : 'text-car-sub']">
                {{ store.vehicleAuto.vehicle_speed_autoplay_enabled ? '达到时速自启 · 点击关闭' : '点击开启自动启播' }}
              </span>
            </div>
          </div>
        </div>

        <!-- 纯加减数值车速控制器 (彻底删除死板固定按钮，自由设定 1~120 km/h) -->
        <div class="p-5 rounded-2xl bg-car-item border-2 border-car-border flex items-center justify-between shadow-sm">
          <div class="flex flex-col space-y-1">
            <div class="flex items-center space-x-2.5">
              <span class="text-[18px] font-black text-car-text">触发时速数值微调</span>
              <span class="px-2.5 py-0.5 text-[12.5px] font-black rounded-full border bg-car-card border-car-border text-car-accent">自由设定</span>
            </div>
            <span class="text-[14px] text-car-sub font-bold leading-normal">
              支持设定 1 ~ 120 km/h。点击两侧 [-5] / [-1] 与 [+1] / [+5] 加减自选专属起播时速。
            </span>
          </div>
          <div class="flex items-center space-x-3 shrink-0">
            <button 
              @click="adjustAutoplaySpeed(-5)"
              class="h-[54px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
              title="减少 5 km/h"
            >-5</button>
            <button 
              @click="adjustAutoplaySpeed(-1)"
              class="h-[54px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
              title="减少 1 km/h"
            >-1</button>
            <div class="flex items-baseline px-5 py-1.5 bg-car-card rounded-2xl border-2 border-car-accent/60 min-w-[130px] justify-center shadow-inner">
              <span class="text-[32px] font-black text-car-accent font-mono leading-none">{{ store.vehicleAuto.vehicle_speed_autoplay_threshold || 20 }}</span>
              <span class="text-[15px] font-bold text-car-sub ml-1.5 font-mono">km/h</span>
            </div>
            <button 
              @click="adjustAutoplaySpeed(1)"
              class="h-[54px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
              title="增加 1 km/h"
            >+1</button>
            <button 
              @click="adjustAutoplaySpeed(5)"
              class="h-[54px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
              title="增加 5 km/h"
            >+5</button>
          </div>
        </div>

        <!-- 目标自选音乐 App 与展示形式 -->
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

          <!-- 启播交互形式 -->
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

    <!-- 4. 车速达标自定义动作与唤起软件 (新增功能二：纯加减车速微调 + 自选动作与软件) -->
    <FeatureCard 
      title="4. 车速达标自定义动作与唤起应用"
      desc="行驶中车速达到设定时速，后台自动执行自定义动作（唤起 360 全景盲区、打开高德地图或启动整车已安装软件）。内置单次行程防抖闭环，等红绿灯不重复调起。"
      helpTitle="【功能指南】车速达标自定义动作原理"
      helpText="1. 运行机理：&#10;从驻车 P 挡出发，当车速首次达到你加减设定的目标时速时，系统毫秒级触发一次目标动作（如唤起 360 全景看盲区或打开常用导航），随后状态机立即锁定。&#10;&#10;2. 行程防抖保障：&#10;等红绿灯堵车起步绝不重复触发打扰，停车回 P 挡后自动复位待命。"
      helpTip="时速完全由车主自由加减调节（不设死板固定按钮），支持随心绑定 360 全景或整车任意已装软件。"
    >
      <template #badge>
        <span class="text-[15px] font-black text-car-accent px-3.5 py-1 bg-car-item rounded-xl border border-car-border whitespace-nowrap">
          当前时速: {{ store.vehicleAuto.vehicle_speed_custom_action_threshold || 40 }} km/h
        </span>
      </template>

      <div class="flex flex-col space-y-5">
        <!-- 总开关长条行 -->
        <div 
          @click="toggleSetting('vehicle_speed_custom_action_enabled')"
          class="flex items-center justify-between p-6 rounded-2xl bg-car-item border-2 border-car-border shadow-sm cursor-pointer hover:border-car-border-light transition-all"
        >
          <div class="w-[60%] max-w-[60%] flex flex-col space-y-1.5 shrink-0">
            <div class="flex items-center space-x-3">
              <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">车速自定义动作总开关</span>
              <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-card border-car-border text-car-accent inline-flex items-center shadow-sm">
                行程防抖
              </span>
            </div>
            <div class="text-[15px] text-car-sub font-bold leading-relaxed">
              出 P 挡起步达到设定车速触发 1 次；停车回 P 挡复位。支持唤起 360 全景或整车任意应用。
            </div>
          </div>
          <div class="shrink-0 w-[230px]">
            <div 
              :class="[
                'w-full h-[78px] px-4 py-2 rounded-2xl border-2 transition-all shadow-md flex flex-col items-center justify-center text-center',
                store.vehicleAuto.vehicle_speed_custom_action_enabled
                  ? 'bg-car-item border-car-accent text-car-text font-black'
                  : 'bg-car-card border-car-border text-car-sub'
              ]"
            >
              <span class="text-[19px] font-black text-car-text tracking-wide whitespace-nowrap">
                {{ store.vehicleAuto.vehicle_speed_custom_action_enabled ? '车速动作已开启' : '车速动作已关闭' }}
              </span>
              <span :class="['text-[13px] font-bold mt-1 whitespace-nowrap', store.vehicleAuto.vehicle_speed_custom_action_enabled ? 'text-car-accent' : 'text-car-sub']">
                {{ store.vehicleAuto.vehicle_speed_custom_action_enabled ? '达标自动执行 · 点击关闭' : '点击开启自定义联动' }}
              </span>
            </div>
          </div>
        </div>

        <!-- 纯加减数值车速控制器 (无固定死板按钮，自由调节 1~120 km/h) -->
        <div class="p-5 rounded-2xl bg-car-item border-2 border-car-border flex items-center justify-between shadow-sm">
          <div class="flex flex-col space-y-1">
            <div class="flex items-center space-x-2.5">
              <span class="text-[18px] font-black text-car-text">触发车速数值微调</span>
              <span class="px-2.5 py-0.5 text-[12.5px] font-black rounded-full border bg-car-card border-car-border text-car-accent">自由可调</span>
            </div>
            <span class="text-[14px] text-car-sub font-bold leading-normal">
              自由设定 1 ~ 120 km/h。点击两侧 [-5] / [-1] 与 [+1] / [+5] 步进加减自定义速度。
            </span>
          </div>
          <div class="flex items-center space-x-3 shrink-0">
            <button 
              @click="adjustCustomActionSpeed(-5)"
              class="h-[54px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
              title="减少 5 km/h"
            >-5</button>
            <button 
              @click="adjustCustomActionSpeed(-1)"
              class="h-[54px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
              title="减少 1 km/h"
            >-1</button>
            <div class="flex items-baseline px-5 py-1.5 bg-car-card rounded-2xl border-2 border-car-accent/60 min-w-[130px] justify-center shadow-inner">
              <span class="text-[32px] font-black text-car-accent font-mono leading-none">{{ store.vehicleAuto.vehicle_speed_custom_action_threshold || 40 }}</span>
              <span class="text-[15px] font-bold text-car-sub ml-1.5 font-mono">km/h</span>
            </div>
            <button 
              @click="adjustCustomActionSpeed(1)"
              class="h-[54px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
              title="增加 1 km/h"
            >+1</button>
            <button 
              @click="adjustCustomActionSpeed(5)"
              class="h-[54px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
              title="增加 5 km/h"
            >+5</button>
          </div>
        </div>

        <!-- 目标联动动作/软件选择器 -->
        <div class="p-6 rounded-2xl bg-car-item border-2 border-car-border flex flex-col space-y-4 shadow-sm">
          <div class="flex items-center justify-between">
            <div class="flex flex-col">
              <span class="text-[18px] font-black text-car-text">车速达标联动目标</span>
              <span class="text-[14px] text-car-sub font-bold mt-0.5">选择达到时速后自动执行的动作或唤起的应用</span>
            </div>
            <button 
              @click="openSelectModal('speed_custom_action')"
              class="h-[52px] px-5 rounded-xl bg-car-card border border-car-border text-car-accent text-[15.5px] font-black hover:border-car-accent whitespace-nowrap cursor-pointer shadow-sm"
            >
              ⚙️ 从整车已装应用中自选 ➔
            </button>
          </div>
          <div class="grid grid-cols-3 gap-3.5">
            <button 
              @click="setCustomActionTarget('action_360')"
              :class="[
                'min-h-[74px] p-4 rounded-2xl border-2 text-left flex flex-col justify-between cursor-pointer transition-all shadow-sm',
                store.vehicleAuto.vehicle_speed_custom_action_target === 'action_360' || (!store.vehicleAuto.vehicle_speed_custom_action_target)
                  ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20'
                  : 'bg-car-card border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'
              ]"
            >
              <span class="text-[17px] font-black whitespace-nowrap">🎥 唤起 360 全景盲区</span>
              <span :class="['text-[12.5px] mt-1 font-bold', store.vehicleAuto.vehicle_speed_custom_action_target === 'action_360' || (!store.vehicleAuto.vehicle_speed_custom_action_target) ? 'text-car-accent' : 'text-car-sub']">
                {{ store.vehicleAuto.vehicle_speed_custom_action_target === 'action_360' || (!store.vehicleAuto.vehicle_speed_custom_action_target) ? '● 当前选定' : '○ 点击选定' }}
              </span>
            </button>
            <button 
              @click="setCustomActionTarget('pkg:com.autonavi.amapauto')"
              :class="[
                'min-h-[74px] p-4 rounded-2xl border-2 text-left flex flex-col justify-between cursor-pointer transition-all shadow-sm',
                store.vehicleAuto.vehicle_speed_custom_action_target === 'pkg:com.autonavi.amapauto'
                  ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20'
                  : 'bg-car-card border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'
              ]"
            >
              <span class="text-[17px] font-black whitespace-nowrap">🗺️ 打开高德车机地图</span>
              <span :class="['text-[12.5px] mt-1 font-bold', store.vehicleAuto.vehicle_speed_custom_action_target === 'pkg:com.autonavi.amapauto' ? 'text-car-accent' : 'text-car-sub']">
                {{ store.vehicleAuto.vehicle_speed_custom_action_target === 'pkg:com.autonavi.amapauto' ? '● 当前选定' : '○ 点击选定' }}
              </span>
            </button>
            <button 
              @click="openSelectModal('speed_custom_action')"
              :class="[
                'min-h-[74px] p-4 rounded-2xl border-2 text-left flex flex-col justify-between cursor-pointer transition-all shadow-sm',
                store.vehicleAuto.vehicle_speed_custom_action_target && store.vehicleAuto.vehicle_speed_custom_action_target !== 'action_360' && store.vehicleAuto.vehicle_speed_custom_action_target !== 'pkg:com.autonavi.amapauto'
                  ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20'
                  : 'bg-car-card border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'
              ]"
            >
              <span class="text-[17px] font-black whitespace-nowrap truncate">📱 {{ customActionAppName || '自选其他应用' }}</span>
              <span :class="['text-[12.5px] mt-1 font-bold', store.vehicleAuto.vehicle_speed_custom_action_target && store.vehicleAuto.vehicle_speed_custom_action_target !== 'action_360' && store.vehicleAuto.vehicle_speed_custom_action_target !== 'pkg:com.autonavi.amapauto' ? 'text-car-accent' : 'text-car-sub']">
                {{ store.vehicleAuto.vehicle_speed_custom_action_target && store.vehicleAuto.vehicle_speed_custom_action_target !== 'action_360' && store.vehicleAuto.vehicle_speed_custom_action_target !== 'pkg:com.autonavi.amapauto' ? '● 正在生效' : '○ 点击挑选' }}
              </span>
            </button>
          </div>
        </div>
      </div>
    </FeatureCard>

    <!-- 5. 行车车速超限安全语音提醒 (纯加减数值控制 · 彻底删除死板预设按钮) -->
    <FeatureCard 
      title="5. 行车车速超限安全语音提醒"
      desc="车速达到设定限速红线并持续超过 3 秒，媒体声道短促温润提醒一次，防城市高架与高速超速罚单。车速回落后自动重新武装。"
      helpTitle="【功能指南】行车超速提醒与自动重新武装机制"
      helpText="1. 判定逻辑：&#10;底层持续监听仪表实时时速。当车速达到你加减设定的红线且连续超速超过 3 秒时，通过媒体声道进行一次温润知性的晓晓原声播报：“当前车速已超限，请减速慢行”。&#10;&#10;2. 防疲劳重新武装：&#10;为防止超速时语音连续轰炸打扰驾驶，每次播报后会自动锁定；只有当车速充分回落至红线下方 5 km/h（如 80 回落至 75）后，状态机才会重新武装，为下一次超速做好预警。"
      helpTip="时速完全支持加减微调自由设定，城市高架推荐设定为 80 km/h，高速巡航推荐设定为 120 km/h。"
    >
      <template #badge>
        <span class="text-[15px] font-black text-car-accent px-3.5 py-1 bg-car-item rounded-xl border border-car-border whitespace-nowrap">
          当前红线: {{ store.vehicleAuto.vehicle_overspeed_threshold || 80 }} km/h
        </span>
      </template>

      <div class="flex flex-col space-y-5">
        <!-- 总开关长条行 -->
        <div 
          @click="toggleSetting('vehicle_overspeed_voice_enabled')"
          class="flex items-center justify-between p-6 rounded-2xl bg-car-item border-2 border-car-border shadow-sm cursor-pointer hover:border-car-border-light transition-all"
        >
          <div class="w-[60%] max-w-[60%] flex flex-col space-y-1.5 shrink-0">
            <div class="flex items-center space-x-3">
              <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">行车超速播报提醒总开关</span>
              <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-card border-car-border text-car-accent inline-flex items-center shadow-sm">安全防御</span>
            </div>
            <div class="text-[15px] text-car-sub font-bold leading-relaxed">
              点火行车中连续超速 3 秒播报“当前车速已超限，请减速慢行”；车速回落 5km/h 自动重新武装。
            </div>
          </div>
          <div class="shrink-0 w-[230px]">
            <div 
              :class="[
                'w-full h-[78px] px-4 py-2 rounded-2xl border-2 transition-all shadow-md flex flex-col items-center justify-center text-center',
                store.vehicleAuto.vehicle_overspeed_voice_enabled
                  ? 'bg-car-item border-car-accent text-car-text font-black'
                  : 'bg-car-card border-car-border text-car-sub'
              ]"
            >
              <span class="text-[19px] font-black text-car-text tracking-wide whitespace-nowrap">
                {{ store.vehicleAuto.vehicle_overspeed_voice_enabled ? '超速提醒已开启' : '超速提醒已关闭' }}
              </span>
              <span :class="['text-[13px] font-bold mt-1 whitespace-nowrap', store.vehicleAuto.vehicle_overspeed_voice_enabled ? 'text-car-accent' : 'text-car-sub']">
                {{ store.vehicleAuto.vehicle_overspeed_voice_enabled ? '超出红线温润播报 · 点击关闭' : '点击开启安全提醒' }}
              </span>
            </div>
          </div>
        </div>

        <!-- 纯加减超速红线微调控制器 (彻底删除死板固定按钮) -->
        <div class="p-5 rounded-2xl bg-car-item border-2 border-car-border flex items-center justify-between shadow-sm">
          <div class="flex flex-col space-y-1">
            <div class="flex items-center space-x-2.5">
              <span class="text-[18px] font-black text-car-text">超速红线数值微调</span>
              <span class="px-2.5 py-0.5 text-[12.5px] font-black rounded-full border bg-car-card border-car-border text-car-accent">自由可调</span>
            </div>
            <span class="text-[14px] text-car-sub font-bold leading-normal">
              支持设定 30 ~ 150 km/h。点击两侧 [-10] / [-5] 与 [+5] / [+10] 加减微调超速警示红线。
            </span>
          </div>
          <div class="flex items-center space-x-3 shrink-0">
            <button 
              @click="adjustOverspeedThreshold(-10)"
              class="h-[54px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
              title="减少 10 km/h"
            >-10</button>
            <button 
              @click="adjustOverspeedThreshold(-5)"
              class="h-[54px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
              title="减少 5 km/h"
            >-5</button>
            <div class="flex items-baseline px-5 py-1.5 bg-car-card rounded-2xl border-2 border-car-accent/60 min-w-[130px] justify-center shadow-inner">
              <span class="text-[32px] font-black text-car-accent font-mono leading-none">{{ store.vehicleAuto.vehicle_overspeed_threshold || 80 }}</span>
              <span class="text-[15px] font-bold text-car-sub ml-1.5 font-mono">km/h</span>
            </div>
            <button 
              @click="adjustOverspeedThreshold(5)"
              class="h-[54px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
              title="增加 5 km/h"
            >+5</button>
            <button 
              @click="adjustOverspeedThreshold(10)"
              class="h-[54px] px-4 rounded-xl bg-car-card border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent shadow-sm flex items-center justify-center active:scale-95"
              title="增加 10 km/h"
            >+10</button>
          </div>
        </div>
      </div>
    </FeatureCard>

    <!-- 6. 进出隧道大灯联动高德日夜模式 (整卡一体化：右侧纯文字无按钮框) -->
    <div 
      @click="toggleSetting('vehicle_light_nav_enabled')"
      :class="[
        'rounded-3xl border-2 p-6 transition-all flex items-center justify-between cursor-pointer select-none shadow-xl',
        store.vehicleAuto.vehicle_light_nav_enabled
          ? 'bg-car-card border-car-accent ring-2 ring-car-accent/20'
          : 'bg-car-card border-car-border hover:border-car-border-light'
      ]"
    >
      <div class="flex-1 min-w-0 pr-6 flex flex-col space-y-2">
        <div class="flex items-center space-x-3">
          <span class="text-[23px] font-black text-car-text tracking-wide">6. 进出隧道大灯联动高德日夜模式</span>
          <button 
            @click.stop="showHelp('light_nav')" 
            class="w-8 h-8 rounded-full bg-car-item border-2 border-car-accent text-car-accent font-black text-[16px] flex items-center justify-center cursor-pointer shadow-sm shrink-0"
            title="查看功能指南"
          >?</button>
          <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent">地图日夜</span>
        </div>
        <div class="text-[15.5px] text-car-sub font-bold leading-relaxed">
          实时监听原厂车身大灯电平。白天开大灯（进隧道或天黑）毫秒级联动高德切换深色夜间地图，出隧道关大灯恢复浅色模式，告别刺眼白底。
        </div>
      </div>
      <div class="min-w-[240px] shrink-0 flex flex-col items-end justify-center text-right">
        <div class="flex items-center space-x-2.5">
          <span :class="['w-3 h-3 rounded-full', store.vehicleAuto.vehicle_light_nav_enabled ? 'bg-emerald-500 shadow-[0_0_10px_#10B981]' : 'bg-slate-500']"></span>
          <span :class="['text-[22px] font-black tracking-wide', store.vehicleAuto.vehicle_light_nav_enabled ? 'text-car-text' : 'text-car-sub']">
            {{ store.vehicleAuto.vehicle_light_nav_enabled ? '地图夜间已开启' : '地图夜间已关闭' }}
          </span>
        </div>
        <span :class="['text-[13.5px] font-bold mt-1.5', store.vehicleAuto.vehicle_light_nav_enabled ? 'text-car-accent' : 'text-car-sub']">
          {{ store.vehicleAuto.vehicle_light_nav_enabled ? '开大灯切夜色 · 点击整卡关闭' : '点击整卡开启深色底图' }}
        </span>
      </div>
    </div>

    <!-- 7. 进隧道中控护眼背光微调 (整卡一体化：右侧纯文字无按钮框) -->
    <div 
      @click="toggleSetting('vehicle_light_brightness_dim_enabled')"
      :class="[
        'rounded-3xl border-2 p-6 transition-all flex items-center justify-between cursor-pointer select-none shadow-xl',
        store.vehicleAuto.vehicle_light_brightness_dim_enabled
          ? 'bg-car-card border-car-accent ring-2 ring-car-accent/20'
          : 'bg-car-card border-car-border hover:border-car-border-light'
      ]"
    >
      <div class="flex-1 min-w-0 pr-6 flex flex-col space-y-2">
        <div class="flex items-center space-x-3">
          <span class="text-[23px] font-black text-car-text tracking-wide">7. 进隧道中控护眼背光微调</span>
          <button 
            @click.stop="showHelp('light_dim')" 
            class="w-8 h-8 rounded-full bg-car-item border-2 border-car-accent text-car-accent font-black text-[16px] flex items-center justify-center cursor-pointer shadow-sm shrink-0"
            title="查看功能指南"
          >?</button>
          <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent">防眩目</span>
        </div>
        <div class="text-[15.5px] text-car-sub font-bold leading-relaxed">
          白天开大灯进隧道自动将中控背光微调压低至 35%，消除强光骤变刺眼；出隧道关大灯秒级恢复原亮度，柔和过渡不眩目。
        </div>
      </div>
      <div class="min-w-[240px] shrink-0 flex flex-col items-end justify-center text-right">
        <div class="flex items-center space-x-2.5">
          <span :class="['w-3 h-3 rounded-full', store.vehicleAuto.vehicle_light_brightness_dim_enabled ? 'bg-emerald-500 shadow-[0_0_10px_#10B981]' : 'bg-slate-500']"></span>
          <span :class="['text-[22px] font-black tracking-wide', store.vehicleAuto.vehicle_light_brightness_dim_enabled ? 'text-car-text' : 'text-car-sub']">
            {{ store.vehicleAuto.vehicle_light_brightness_dim_enabled ? '护眼微调已开启' : '护眼微调已关闭' }}
          </span>
        </div>
        <span :class="['text-[13.5px] font-bold mt-1.5', store.vehicleAuto.vehicle_light_brightness_dim_enabled ? 'text-car-accent' : 'text-car-sub']">
          {{ store.vehicleAuto.vehicle_light_brightness_dim_enabled ? '进隧道平滑压暗 · 点击整卡关闭' : '点击整卡开启护眼背光' }}
        </span>
      </div>
    </div>

    <!-- 8. 停车开门多媒体优雅静音联动 (整卡一体化：右侧纯文字无按钮框) -->
    <div 
      @click="toggleSetting('vehicle_door_pause_music_enabled')"
      :class="[
        'rounded-3xl border-2 p-6 transition-all flex items-center justify-between cursor-pointer select-none shadow-xl',
        store.vehicleAuto.vehicle_door_pause_music_enabled
          ? 'bg-car-card border-car-accent ring-2 ring-car-accent/20'
          : 'bg-car-card border-car-border hover:border-car-border-light'
      ]"
    >
      <div class="flex-1 min-w-0 pr-6 flex flex-col space-y-2">
        <div class="flex items-center space-x-3">
          <span class="text-[23px] font-black text-car-text tracking-wide">8. 停车开门多媒体优雅静音联动</span>
          <button 
            @click.stop="showHelp('door_pause')" 
            class="w-8 h-8 rounded-full bg-car-item border-2 border-car-accent text-car-accent font-black text-[16px] flex items-center justify-center cursor-pointer shadow-sm shrink-0"
            title="查看功能指南"
          >?</button>
          <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent">下车静音</span>
        </div>
        <div class="text-[15.5px] text-car-sub font-bold leading-relaxed">
          挂入驻车 P 挡停稳推开前门，毫秒级自动向播放器发送暂停指令，下车从容安静，绝不大声喧哗。（车门安全播报由【座舱语音】统一专职管理，职责解耦零冲突）
        </div>
      </div>
      <div class="min-w-[240px] shrink-0 flex flex-col items-end justify-center text-right">
        <div class="flex items-center space-x-2.5">
          <span :class="['w-3 h-3 rounded-full', store.vehicleAuto.vehicle_door_pause_music_enabled ? 'bg-emerald-500 shadow-[0_0_10px_#10B981]' : 'bg-slate-500']"></span>
          <span :class="['text-[22px] font-black tracking-wide', store.vehicleAuto.vehicle_door_pause_music_enabled ? 'text-car-text' : 'text-car-sub']">
            {{ store.vehicleAuto.vehicle_door_pause_music_enabled ? '开门暂停已开启' : '开门暂停已关闭' }}
          </span>
        </div>
        <span :class="['text-[13.5px] font-bold mt-1.5', store.vehicleAuto.vehicle_door_pause_music_enabled ? 'text-car-accent' : 'text-car-sub']">
          {{ store.vehicleAuto.vehicle_door_pause_music_enabled ? '停稳推门秒暂停 · 点击整卡关闭' : '点击整卡开启下车静音' }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { store, bridge, showToast, openModal } from '../store';
import FeatureCard from '../components/FeatureCard.vue';

const sortedMusicApps = ref([]);
const customActionAppName = ref('');

function loadCustomActionAppName() {
  customActionAppName.value = localStorage.getItem('vehicle_speed_custom_action_app_name') || '';
}

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

  if (list.length === 1 && list[0].pkg === 'com.android.bluetooth') {
    list.unshift({ name: '汽水音乐', pkg: 'com.luna.music' });
    list.push({ name: 'QQ音乐', pkg: 'com.tencent.qqmusiccar' });
    list.push({ name: '酷我音乐', pkg: 'cn.kuwo.kwmusiccar' });
  }

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

  sortedMusicApps.value = list.slice(0, 5);

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

function adjustAutoplaySpeed(delta) {
  let current = store.vehicleAuto.vehicle_speed_autoplay_threshold || 20;
  let next = Math.max(1, Math.min(120, current + delta));
  setAutoplaySpeed(next);
}

function setCustomActionSpeed(speed) {
  store.vehicleAuto.vehicle_speed_custom_action_threshold = speed;
  bridge.call('setVehicleAutomationIntSetting', 'vehicle_speed_custom_action_threshold', speed);
  showToast(`联动触发时速已设为: ${speed} km/h`);
}

function adjustCustomActionSpeed(delta) {
  let current = store.vehicleAuto.vehicle_speed_custom_action_threshold || 40;
  let next = Math.max(1, Math.min(120, current + delta));
  setCustomActionSpeed(next);
}

function setCustomActionTarget(target) {
  store.vehicleAuto.vehicle_speed_custom_action_target = target;
  bridge.call('setWheelControlStringSetting', 'vehicle_speed_custom_action_target', target);
  if (target === 'action_360') {
    showToast('车速达标联动目标已设为: 唤起 360 全景盲区');
  } else if (target === 'pkg:com.autonavi.amapauto') {
    showToast('车速达标联动目标已设为: 打开高德车机地图');
  }
}

function setAutoplayApp(pkg, name) {
  store.vehicleAuto.vehicle_speed_autoplay_pkg = pkg;
  bridge.call('setWheelControlStringSetting', 'vehicle_speed_autoplay_pkg', pkg);
  localStorage.setItem('vehicle_speed_autoplay_app_name', name);
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

function adjustOverspeedThreshold(delta) {
  let current = store.vehicleAuto.vehicle_overspeed_threshold || 80;
  let next = Math.max(30, Math.min(150, current + delta));
  setOverspeedThreshold(next);
}

function openSelectModal(target) {
  openModal('appSelect', { keyTarget: target });
}

function showHelp(key) {
  const helpData = {
    turn_360: {
      title: '【功能指南】转向灯联动 360 全景盲区',
      desc: '1. 联动原理：监听底层原厂 AVM 环视状态机与转向拨杆电平。打起转向灯秒级唤起 360 盲区影像；方向盘回正后自动平滑退出。\n\n2. 安全限速：时速高于 30km/h 时自动静默，防止高速变道遮挡地图导航。',
      tip: '整张卡片任意位置一拍即合开/关。'
    },
    gear_d_360: {
      title: '【功能指南】前进 D 挡起步 360 全景',
      desc: '1. 单次跃变锁：从 P 挡或 R 挡切入前进 D 挡瞬间自动唤醒 360 环视四周；触发一次即锁定，手动退出绝不重复弹出。\n\n2. 自动退出：起步车速超 15km/h 自动退出全景，保持导航视线。',
      tip: '切出 D 挡离开后才会重新武装待命。'
    },
    light_nav: {
      title: '【功能指南】大灯联动高德日夜模式',
      desc: '白天开大灯（进地下车库或隧道）秒级向高德发送夜间模式指令，出隧道关大灯自动恢复白天浅色地图，告别刺眼白底。',
      tip: '常走隧道的车友建议保持开启。'
    },
    light_dim: {
      title: '【功能指南】进隧道中控护眼背光微调',
      desc: '白天开大灯后自动将车机中控屏幕亮度微调压低至 35%，消除进隧道瞬间强光眩目；出隧道关大灯秒级恢复原亮度。',
      tip: '可与高德夜间联动独立开关。'
    },
    door_pause: {
      title: '【功能指南】P 挡开门音乐暂停',
      desc: '挂入驻车 P 挡推开前门，毫秒级自动向播放器发送暂停指令，下车从容安静。（车门安全播报由【座舱语音】专职管理，零冲突零重复）',
      tip: '仅在挂 P 挡停稳推门生效，行车中推门不误触。'
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
  loadSortedMusicApps();
  loadCustomActionAppName();
  window.addEventListener('music-order-updated', loadSortedMusicApps);
  window.addEventListener('custom-action-target-updated', loadCustomActionAppName);
});

onUnmounted(() => {
  window.removeEventListener('music-order-updated', loadSortedMusicApps);
  window.removeEventListener('custom-action-target-updated', loadCustomActionAppName);
});
</script>
