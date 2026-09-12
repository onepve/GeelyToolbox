<template>
  <div class="flex flex-col space-y-6">
    <!-- 实时车身物理信号探针 (支持折叠·默认收起) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-4 shadow-xl">
      <div class="flex items-center justify-between" :class="isProbeExpanded ? 'pb-3 mb-3 border-b border-car-border/60' : ''">
        <div class="flex items-center space-x-2.5">
          <span class="w-3 h-3 rounded-full bg-emerald-500 animate-pulse shadow-[0_0_8px_#10B981]"></span>
          <span class="text-[18px] font-black text-car-text whitespace-nowrap">车身全域物理信号实时探针</span>
          <!-- 折叠状态下的微型读数胶囊 -->
          <span v-if="!isProbeExpanded" class="text-[13px] px-3 py-1 rounded-xl bg-car-item border border-car-border text-car-accent font-bold whitespace-nowrap">
            {{ doorStatus.gear_name || 'P挡' }} · {{ doorStatus.mode_name || '模式直通' }} · {{ doorStatus.speed !== undefined ? doorStatus.speed : 0 }} km/h
          </span>
        </div>
        <div class="flex items-center space-x-3">
          <span v-if="isProbeExpanded" class="text-[13px] text-car-sub font-bold whitespace-nowrap">
            四门、尾门、换挡、模式与车速信号全量实时监听
          </span>
          <button 
            @click="isProbeExpanded = !isProbeExpanded"
            class="px-4 py-1.5 rounded-xl bg-car-item border border-car-border hover:border-car-accent text-car-text font-bold text-[13.5px] transition-all whitespace-nowrap"
          >
            {{ isProbeExpanded ? '收起探针 ▲' : '展开实时探针 (实车调试) ▼' }}
          </button>
        </div>
      </div>

      <!-- 展开后的探针内容 -->
      <div v-if="isProbeExpanded" class="flex flex-col space-y-3">
        <!-- 第一排：四门与电动尾门 -->
        <div class="grid grid-cols-5 gap-3">
          <!-- 主驾门 -->
          <div 
            :class="[
              'p-3 rounded-2xl border-2 flex flex-col items-center justify-center transition-all',
              doorStatus.fl === 1 
                ? 'bg-emerald-500/20 border-emerald-500 text-emerald-400 ring-2 ring-emerald-500/30 shadow-md' 
                : 'bg-car-item border-car-border text-car-sub'
            ]"
          >
            <span class="text-[13px] font-bold whitespace-nowrap">主驾车门 (FL)</span>
            <span class="text-[16px] font-black mt-1 whitespace-nowrap">
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
            <span class="text-[13px] font-bold whitespace-nowrap">副驾车门 (FR)</span>
            <span class="text-[16px] font-black mt-1 whitespace-nowrap">
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
            <span class="text-[13px] font-bold whitespace-nowrap">左后车门 (RL)</span>
            <span class="text-[16px] font-black mt-1 whitespace-nowrap">
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
            <span class="text-[13px] font-bold whitespace-nowrap">右后车门 (RR)</span>
            <span class="text-[16px] font-black mt-1 whitespace-nowrap">
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
            <span class="text-[13px] font-bold whitespace-nowrap">电动尾门 (Trunk)</span>
            <span class="text-[16px] font-black mt-1 whitespace-nowrap">
              {{ doorStatus.trunk === 1 ? '● 升起打开' : (doorStatus.trunk === 0 ? '○ 已锁止' : '采集中...') }}
            </span>
          </div>
        </div>

        <!-- 第二排：挡位、驾驶模式与实时车速 -->
        <div class="grid grid-cols-3 gap-3">
          <!-- 挡位状态 -->
          <div class="p-3 rounded-2xl bg-car-item border-2 border-car-border flex items-center justify-between">
            <div>
              <div class="text-[13px] font-bold text-car-sub whitespace-nowrap">实时挡位状态 (Gear)</div>
              <div class="text-[17px] font-black text-car-text mt-0.5 whitespace-nowrap">
                {{ doorStatus.gear_name || '采集中...' }}
              </div>
            </div>
            <span class="px-2.5 py-1 text-[12px] font-extrabold rounded-lg bg-car-card border border-car-border text-car-sub whitespace-nowrap">
              实时监听中
            </span>
          </div>

          <!-- 驾驶模式 -->
          <div class="p-3 rounded-2xl bg-car-item border-2 border-car-border flex items-center justify-between">
            <div>
              <div class="text-[13px] font-bold text-car-sub whitespace-nowrap">实时驾驶模式 (DriveMode)</div>
              <div class="text-[17px] font-black text-car-accent mt-0.5 whitespace-nowrap">
                {{ doorStatus.mode_name || '采集中...' }}
              </div>
            </div>
            <span class="px-2.5 py-1 text-[12px] font-extrabold rounded-lg bg-car-card border border-car-border text-car-sub whitespace-nowrap">
              原厂模式直通
            </span>
          </div>

          <!-- 实时车速 -->
          <div class="p-3 rounded-2xl bg-car-item border-2 border-car-border flex items-center justify-between">
            <div>
              <div class="text-[13px] font-bold text-car-sub whitespace-nowrap">实时车速 (Speed)</div>
              <div class="text-[17px] font-black text-car-text mt-0.5 whitespace-nowrap">
                {{ doorStatus.speed !== undefined ? doorStatus.speed + ' km/h' : '采集中...' }}
              </div>
            </div>
            <span class="px-2.5 py-1 text-[12px] font-extrabold rounded-lg bg-car-card border border-car-border text-car-sub whitespace-nowrap">
              ≤30km/h 盲区保护
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 1. 转向灯与 D 挡起步 360 全景联动 -->
    <FeatureCard 
      title="1. 转向灯与 D 挡起步 360 全景盲区联动"
      desc="原厂 360 环视核心状态机毫秒级联动；内置车速 ≤30km/h 安全阈值保护，高速巡航变道自动静默抑制。"
    >
      <div class="grid grid-cols-2 gap-4">
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text whitespace-nowrap">转向灯联动 360 全景</div>
            <div class="text-[14.5px] text-car-sub mt-1 font-bold whitespace-nowrap">打起转向灯秒开盲区画面，回正自动退出</div>
          </div>
          <MatrixButton 
            :title="store.vehicleAuto.vehicle_turn_360_enabled ? '已开启' : '已关闭'"
            :active="store.vehicleAuto.vehicle_turn_360_enabled"
            @click="toggleSetting('vehicle_turn_360_enabled')"
          />
        </div>

        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text whitespace-nowrap">D 挡起步联动 360 全景</div>
            <div class="text-[14.5px] text-car-sub mt-1 font-bold whitespace-nowrap">挂入前进挡秒开全景看盲区，起步后自动退出</div>
          </div>
          <MatrixButton 
            :title="store.vehicleAuto.vehicle_gear_d_360_enabled ? '已开启' : '已关闭'"
            :active="store.vehicleAuto.vehicle_gear_d_360_enabled"
            @click="toggleSetting('vehicle_gear_d_360_enabled')"
          />
        </div>
      </div>
    </FeatureCard>

    <!-- 2. 车速联动与起步多媒体智能自启 (核心新增) -->
    <FeatureCard 
      title="2. 车速联动与起步多媒体智能启播"
      desc="挂 D 挡出发达到设定时速，自动起播自选音乐 App。内置单次行程防抖闭环，等红绿灯不重复触发；若车主已在放歌则静默放行。"
    >
      <template #badge>
        <span class="text-[14.5px] font-black text-car-accent px-3 py-1 bg-car-item rounded-xl border border-car-border whitespace-nowrap">
          当前触发: {{ store.vehicleAuto.vehicle_speed_autoplay_threshold || 20 }} km/h
        </span>
      </template>

      <div class="flex flex-col space-y-4">
        <!-- 总开关与启播模式行 -->
        <div class="flex items-center justify-between p-4 rounded-2xl bg-car-item border border-car-border">
          <div class="flex flex-col space-y-1">
            <div class="flex items-center space-x-2">
              <span class="text-[17px] font-black text-car-title whitespace-nowrap">车速自动启播总开关</span>
              <span class="text-[12px] px-2 py-0.5 rounded bg-car-accent/15 text-car-accent font-bold whitespace-nowrap">单次行程防抖</span>
            </div>
            <span class="text-[13px] text-car-sub font-bold whitespace-nowrap">
              点火出 P 挡起步达到设定车速触发 1 次；停车回 P 挡归零重置。
            </span>
          </div>
          <button 
            @click="toggleSetting('vehicle_speed_autoplay_enabled')" 
            class="px-5 py-2 rounded-xl text-[14px] font-black border transition-colors whitespace-nowrap"
            :class="store.vehicleAuto.vehicle_speed_autoplay_enabled ? 'bg-car-accent/20 border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'"
          >
            {{ store.vehicleAuto.vehicle_speed_autoplay_enabled ? '已开启' : '已关闭 (默认)' }}
          </button>
        </div>

        <!-- 车速预设快捷大磁贴 (4列) -->
        <div class="grid grid-cols-4 gap-3">
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

        <!-- 目标自选音乐 App 与展示形式 -->
        <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3">
          <div class="flex items-center justify-between">
            <div class="flex items-center space-x-2">
              <span class="text-[15px] font-black text-car-title whitespace-nowrap">自启目标音乐软件</span>
              <span class="text-[12px] text-car-sub font-bold whitespace-nowrap">(按当前优先级排序，点击直选主力)</span>
            </div>
            <div class="flex items-center space-x-2">
              <button 
                @click="refreshDetectedApps"
                class="px-3 py-1 rounded-lg bg-car-card border border-car-border text-car-sub text-[13px] font-bold hover:border-car-accent flex items-center space-x-1 whitespace-nowrap"
              >
                <span>🔄 重新扫描已装App</span>
              </button>
              <button 
                @click="openSelectModal('speed_autoplay')"
                class="px-3 py-1 rounded-lg bg-car-card border border-car-border text-car-accent text-[13px] font-bold hover:border-car-accent whitespace-nowrap"
              >
                ⚙️ 排序 / 增减 / 全部应用
              </button>
            </div>
          </div>

          <!-- 动态探测已装音乐软件磁贴 (按优先级排序) -->
          <div class="grid grid-cols-5 gap-2.5">
            <button 
              v-for="(app, idx) in sortedMusicApps" 
              :key="app.pkg"
              @click="setAutoplayApp(app.pkg, app.name)"
              class="p-2.5 rounded-xl border text-left flex flex-col justify-between transition-all"
              :class="store.vehicleAuto.vehicle_speed_autoplay_pkg === app.pkg ? 'bg-car-accent/20 border-car-accent text-car-accent shadow-sm' : 'bg-car-card border-car-border text-car-sub'"
            >
              <div class="flex items-center justify-between">
                <span class="text-[14px] font-black whitespace-nowrap truncate">{{ app.name }}</span>
                <span class="text-[11px] px-1 rounded bg-car-item font-mono font-bold">{{ idx === 0 ? '①' : (idx === 1 ? '②' : (idx === 2 ? '③' : (idx === 3 ? '④' : '⑤'))) }}</span>
              </div>
              <span class="text-[11px] opacity-75 mt-1 whitespace-nowrap">{{ store.vehicleAuto.vehicle_speed_autoplay_pkg === app.pkg ? '● 👑 当前首选' : '○ 点击选为主力' }}</span>
            </button>
          </div>

          <!-- 启播形式：后台静默 vs 前台全屏 -->
          <div class="flex items-center justify-between pt-2 border-t border-car-border/50 text-[13.5px] font-bold text-car-sub">
            <span class="whitespace-nowrap">启播交互形式：</span>
            <div class="flex space-x-2">
              <button 
                @click="setAutoplayFullscreen(false)"
                class="px-3.5 py-1 rounded-lg border text-[13px] whitespace-nowrap"
                :class="!store.vehicleAuto.vehicle_speed_autoplay_fullscreen ? 'bg-car-accent/20 border-car-accent text-car-accent font-black' : 'bg-car-card border-car-border text-car-sub'"
              >
                后台静默放歌 (推荐·保持高德导航不挡视线)
              </button>
              <button 
                @click="setAutoplayFullscreen(true)"
                class="px-3.5 py-1 rounded-lg border text-[13px] whitespace-nowrap"
                :class="store.vehicleAuto.vehicle_speed_autoplay_fullscreen ? 'bg-car-accent/20 border-car-accent text-car-accent font-black' : 'bg-car-card border-car-border text-car-sub'"
              >
                前台全屏大屏
              </button>
            </div>
          </div>
        </div>
      </div>
    </FeatureCard>

    <!-- 3. 行车车速超限安全语音提醒 (新增) -->
    <FeatureCard 
      title="3. 行车车速超限安全语音提醒"
      desc="车速达到设定限速红线并持续超过 3 秒，媒体声道短促温润提醒一次，防城市高架与高速超速罚单。车速回落后自动重新武装。"
    >
      <template #badge>
        <span class="text-[14.5px] font-black text-car-accent px-3 py-1 bg-car-item rounded-xl border border-car-border whitespace-nowrap">
          当前红线: {{ store.vehicleAuto.vehicle_overspeed_threshold || 80 }} km/h
        </span>
      </template>

      <div class="flex flex-col space-y-4">
        <!-- 总开关 -->
        <div class="flex items-center justify-between p-4 rounded-2xl bg-car-item border border-car-border">
          <div class="flex flex-col space-y-1">
            <span class="text-[17px] font-black text-car-title whitespace-nowrap">行车超速播报提醒总开关</span>
            <span class="text-[13px] text-car-sub font-bold whitespace-nowrap">
              点火行车中连续超速 3 秒播报“当前车速已超限，请减速慢行”；低于阈值 5km/h 自动复位。
            </span>
          </div>
          <button 
            @click="toggleSetting('vehicle_overspeed_voice_enabled')" 
            class="px-5 py-2 rounded-xl text-[14px] font-black border transition-colors whitespace-nowrap"
            :class="store.vehicleAuto.vehicle_overspeed_voice_enabled ? 'bg-car-accent/20 border-car-accent text-car-accent' : 'bg-car-card border-car-border text-car-sub'"
          >
            {{ store.vehicleAuto.vehicle_overspeed_voice_enabled ? '已开启' : '已关闭 (默认)' }}
          </button>
        </div>

        <!-- 3 大超速档位大磁贴 -->
        <div class="grid grid-cols-3 gap-3">
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

    <!-- 4. 进出隧道大灯日夜与护眼背光联动 (升级) -->
    <FeatureCard 
      title="4. 进出隧道大灯日夜与护眼背光联动"
      desc="破译原厂大灯信号；白天开大灯（进隧道或天黑）不仅联动高德切深色夜间地图，还可平滑微调中控背光防眩目，出隧道自动恢复。"
    >
      <div class="grid grid-cols-2 gap-4">
        <!-- 子功能 A: 高德日夜地图 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text whitespace-nowrap">大灯联动高德日夜模式</div>
            <div class="text-[14px] text-car-sub mt-1 font-bold whitespace-nowrap">进出隧道与黑夜自动切换高德深色底图</div>
          </div>
          <MatrixButton 
            :title="store.vehicleAuto.vehicle_light_nav_enabled ? '已开启' : '已关闭'"
            :active="store.vehicleAuto.vehicle_light_nav_enabled"
            @click="toggleSetting('vehicle_light_nav_enabled')"
          />
        </div>

        <!-- 子功能 B: 隧道屏幕背光微调 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text whitespace-nowrap">进隧道中控护眼背光微调</div>
            <div class="text-[14px] text-car-sub mt-1 font-bold whitespace-nowrap">开大灯自动压低中控背光至 35%，出隧道恢复</div>
          </div>
          <MatrixButton 
            :title="store.vehicleAuto.vehicle_light_brightness_dim_enabled ? '已开启' : '已关闭'"
            :active="store.vehicleAuto.vehicle_light_brightness_dim_enabled"
            @click="toggleSetting('vehicle_light_brightness_dim_enabled')"
          />
        </div>
      </div>
    </FeatureCard>

    <!-- 5. 停车开门多媒体优雅静音联动 -->
    <FeatureCard 
      title="5. 停车开门多媒体优雅静音联动"
      desc="挂入 P 挡停稳推开前门，毫秒级自动暂停当前多媒体播放，保持下车从容安静。（注：四门安全播报与有人感知由【座舱语音】专属中枢统一接管，零冲突零重复）"
    >
      <div class="grid grid-cols-2 gap-4">
        <!-- P挡开门暂停音乐 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text whitespace-nowrap">P 挡开门自动暂停音乐</div>
            <div class="text-[14px] text-car-sub mt-1 font-bold whitespace-nowrap">挂入驻车 P 挡推开前门，毫秒级暂停当前音乐</div>
          </div>
          <MatrixButton 
            :title="store.vehicleAuto.vehicle_door_pause_music_enabled ? '已开启' : '已关闭'"
            :active="store.vehicleAuto.vehicle_door_pause_music_enabled"
            @click="toggleSetting('vehicle_door_pause_music_enabled')"
          />
        </div>

        <!-- 联动座舱语音指引 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text whitespace-nowrap">座舱车门语音统一归口</div>
            <div class="text-[14px] text-car-sub mt-1 font-bold whitespace-nowrap">四门下车离车提醒与防开门杀已接入【座舱语音】</div>
          </div>
          <button 
            @click="store.currentNav = 'voice'"
            class="h-[46px] px-5 rounded-xl bg-car-card border border-car-border hover:border-car-accent text-car-accent font-black text-[15px] transition-all shadow-sm flex items-center justify-center whitespace-nowrap"
          >
            前往【座舱语音】配置车门台词 ➔
          </button>
        </div>
      </div>
    </FeatureCard>

    <!-- 6. 原厂电动尾门物理串口探针与锁止播报 -->
    <FeatureCard 
      title="6. 原厂电动尾门 (b7) 串口状态与锁止播报"
      desc="原厂电动尾门底层串口独立监听；升起打开安全警示，闭合完全锁止短促语音播报。"
    >
      <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
        <div class="mb-3">
          <div class="text-[19px] font-black text-car-text whitespace-nowrap">原厂电动尾门开闭独立语音播报</div>
          <div class="text-[14.5px] text-car-sub mt-1 font-bold whitespace-nowrap">后备箱抬起升起时提醒，完全闭合锁止时短促播报“后备箱已关好”</div>
        </div>
        <div class="grid grid-cols-2 gap-3.5">
          <MatrixButton 
            title="尾门升起播报"
            :subtitle="store.vehicleAuto.voice_enable_trunk_open ? '已开启' : '已关闭'"
            :active="store.vehicleAuto.voice_enable_trunk_open"
            @click="toggleSetting('voice_enable_trunk_open')"
          />
          <MatrixButton 
            title="尾门锁止播报"
            :subtitle="store.vehicleAuto.voice_enable_trunk_close ? '已开启' : '已关闭'"
            :active="store.vehicleAuto.voice_enable_trunk_close"
            @click="toggleSetting('voice_enable_trunk_close')"
          />
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

const isProbeExpanded = ref(false);

const doorStatus = ref({
  fl: -1,
  fr: -1,
  rl: -1,
  rr: -1,
  trunk: -1,
  gear_name: '采集中...',
  mode_name: '采集中...',
  speed: 0
});

let statusPollTimer = null;

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

function fetchVehicleStatus() {
  try {
    const raw = bridge.call('getDoorStatus');
    if (raw) {
      const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
      doorStatus.value = { ...doorStatus.value, ...data };
    }
  } catch (e) {}
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
  fetchVehicleStatus();
  statusPollTimer = setInterval(fetchVehicleStatus, 800);
  loadSortedMusicApps();
  window.addEventListener('music-order-updated', onMusicOrderChanged);
});

onUnmounted(() => {
  if (statusPollTimer) clearInterval(statusPollTimer);
  window.removeEventListener('music-order-updated', onMusicOrderChanged);
});
</script>
