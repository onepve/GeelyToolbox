<template>
  <div class="flex flex-col space-y-6">
    <!-- 顶部计划工作台概览与操作栏 (对齐方控 106px 车规标准) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-5 min-h-[106px] shadow-xl flex items-center justify-between transition-all">
      <div class="w-[58%] max-w-[58%] flex flex-col space-y-1.5 shrink-0">
        <div class="flex items-center space-x-3">
          <span class="w-3.5 h-3.5 rounded-full bg-car-accent shadow-[0_0_10px_var(--accent-gold)] shrink-0"></span>
          <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">车身智能联动 · 计划任务工作台</span>
          <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-item border-car-accent/40 text-car-accent inline-flex items-center shrink-0 shadow-sm">
            {{ activeTaskCount }} / {{ visibleTasks.length }} 项运行中
          </span>
        </div>
        <div class="text-[14.5px] text-car-sub font-bold leading-normal">
          基于车载 CAN 总线与 MCU 底层传感器事件驱动。支持独立开关、加减微调触发阈值，不需要的计划可随心一键移除。
        </div>
      </div>
      <div class="flex items-center space-x-3 shrink-0">
        <button
          @click="openAddTaskModal"
          class="h-[60px] px-6 rounded-2xl bg-car-item border-2 border-car-accent hover:border-car-accent-light text-car-accent font-black text-[16px] cursor-pointer shadow-md flex items-center space-x-2 transition-all"
        >
          <span>添加联动计划</span>
          <span v-if="removedTaskIds.length > 0" class="px-2 py-0.5 text-[12px] bg-car-card rounded-full border border-car-accent/40 text-car-text">
            {{ removedTaskIds.length }} 项待添加
          </span>
        </button>
        <button
          @click="resetAllTasks"
          class="h-[60px] px-5 rounded-2xl bg-car-item border-2 border-car-border hover:border-car-border-light text-car-sub hover:text-car-text font-black text-[15px] cursor-pointer shadow-sm transition-all"
        >
          ↺ 恢复推荐计划
        </button>
      </div>
    </div>

    <!-- 计划任务流列表 (车规对称双列网格 · 告别单列8层面条堆叠 · 1920宽屏黄金排布) -->
    <div class="grid grid-cols-2 gap-5">
      <!-- 任务 2: D 挡起步联动 360 -->
      <div 
        v-if="isTaskVisible('d_360')"
        class="rounded-3xl border-2 border-car-border hover:border-car-border-light bg-car-card p-6 shadow-xl h-full min-h-[260px] flex flex-col justify-between transition-all duration-200"
      >
        <div class="flex items-center justify-between shrink-0 mb-2">
          <div class="flex items-center space-x-3">
            <span class="text-[21px] font-black text-car-text tracking-wide">2. 前进 D 挡起步联动 360</span>
            <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent shrink-0">单次跃变</span>
            <button 
              @click.stop="showHelp('gear_d_360')"
              class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-accent hover:border-car-accent font-black text-[18px] flex items-center justify-center cursor-pointer shadow-sm transition-transform active:scale-95 shrink-0"
              title="查看功能指南"
            >?</button>
          </div>
          <button 
            @click="removeTask('d_360', 'D 挡起步联动 360')"
            class="h-[50px] px-4 rounded-xl bg-car-item border border-car-border hover:border-red-500/80 text-car-sub hover:text-red-400 font-black text-[14px] cursor-pointer shadow-sm transition-all shrink-0"
            title="从工作台移除"
          >
            移除
          </button>
        </div>

        <div class="flex-1 min-w-0 flex flex-col justify-center space-y-1.5 py-3 text-[15px] leading-relaxed">
          <div class="text-car-sub font-bold">从 P 挡或 R 挡切入前进 D 挡起步 (单次跃变锁)</div>
          <div class="text-car-text font-bold">唤醒 360 扫除起步盲区；车速超 15 km/h 自动还原</div>
        </div>

        <div class="pt-4 border-t border-car-border/60 mt-auto shrink-0">
          <button
            @click="toggleSetting('vehicle_d_gear_360_enabled')"
            :class="[
              'w-full h-[52px] px-6 rounded-2xl font-black text-[16px] cursor-pointer transition-all border-2 flex items-center justify-center space-x-2 shadow-sm',
              store.vehicleAuto.vehicle_d_gear_360_enabled
                ? 'bg-car-item border-car-accent text-car-text shadow-md'
                : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            <span :class="['w-2.5 h-2.5 rounded-full', store.vehicleAuto.vehicle_d_gear_360_enabled ? 'bg-car-accent shadow-[0_0_6px_var(--accent-gold)]' : 'bg-car-sub']"></span>
            <span>{{ store.vehicleAuto.vehicle_d_gear_360_enabled ? 'D 挡起步 360 运行中' : 'D 挡起步 360 已暂停' }}</span>
          </button>
        </div>
      </div>

      <!-- 任务 3: 车速达标智能启播车载音乐 (整宽大卡片 · 纯加减微调控制器) -->
      <div 
        v-if="isTaskVisible('speed_music')"
        class="col-span-2 rounded-3xl border-2 border-car-border hover:border-car-border-light bg-car-card p-6 shadow-xl flex flex-col space-y-4 transition-all duration-200"
      >
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-3">
            <span class="text-[22px] font-black text-car-text tracking-wide">3. 车速达标智能启播车载音乐</span>
            <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent">起步自启</span>
          </div>
          <div class="flex items-center space-x-3">
            <button
              @click="toggleSetting('vehicle_speed_autoplay_enabled')"
              :class="[
                'h-[54px] px-6 rounded-2xl font-black text-[16px] cursor-pointer transition-all border-2 flex items-center space-x-2',
                store.vehicleAuto.vehicle_speed_autoplay_enabled
                  ? 'bg-car-item border-car-accent text-car-text shadow-md'
                  : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
              ]"
            >
              <span :class="['w-2.5 h-2.5 rounded-full', store.vehicleAuto.vehicle_speed_autoplay_enabled ? 'bg-car-accent' : 'bg-car-sub']"></span>
              <span>{{ store.vehicleAuto.vehicle_speed_autoplay_enabled ? '计划运行中' : '计划已暂停' }}</span>
            </button>
            <button 
              @click="removeTask('speed_music', '车速启播音乐')"
              class="h-[54px] px-5 rounded-2xl bg-car-item border-2 border-car-border hover:border-red-500/80 text-car-sub hover:text-red-400 font-black text-[15px] cursor-pointer shadow-sm transition-all flex items-center space-x-1.5"
            >
              
              <span>移除</span>
            </button>
          </div>
        </div>

        <!-- 任务两段流 -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 pt-1">
          <!-- 当: 车速控制器 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3">
            <div class="flex items-center space-x-3">
              <span class="text-[16px] font-black text-car-text">出 P 挡起步行驶，车速达到设定阈值：</span>
            </div>
            <!-- 纯加减数值步进器 -->
            <div class="flex items-center justify-between bg-car-card border-2 border-car-border rounded-2xl p-2.5">
              <div class="flex items-center space-x-2">
                <button 
                  @click="adjustAutoplaySpeed(-5)"
                  class="h-[52px] px-4 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[16px] cursor-pointer active:scale-95 transition-all shadow-sm"
                >
                  -5
                </button>
                <button 
                  @click="adjustAutoplaySpeed(-1)"
                  class="h-[52px] px-4 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[16px] cursor-pointer active:scale-95 transition-all shadow-sm"
                >
                  -1
                </button>
              </div>
              <div class="flex items-baseline space-x-1 px-4">
                <span class="text-[32px] font-black text-car-accent tracking-tight font-mono">
                  {{ store.vehicleAuto.vehicle_speed_autoplay_threshold || 20 }}
                </span>
                <span class="text-[15px] text-car-sub font-black">km/h</span>
              </div>
              <div class="flex items-center space-x-2">
                <button 
                  @click="adjustAutoplaySpeed(1)"
                  class="h-[52px] px-4 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[16px] cursor-pointer active:scale-95 transition-all shadow-sm"
                >
                  +1
                </button>
                <button 
                  @click="adjustAutoplaySpeed(5)"
                  class="h-[52px] px-4 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[16px] cursor-pointer active:scale-95 transition-all shadow-sm"
                >
                  +5
                </button>
              </div>
            </div>
          </div>

          <!-- 就: 自动放歌 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3">
            <div class="flex items-center space-x-3">
              <span class="text-[16px] font-black text-car-text">触发主力软件播放（单次行程防抖闭环，等红灯不重复放）：</span>
            </div>
            <!-- 形式与软件选择 -->
            <div class="flex items-center space-x-3">
              <button 
                @click="setAutoplayFullscreen(false)"
                :class="[
                  'flex-1 h-[52px] rounded-xl font-black text-[15px] cursor-pointer transition-all border-2 whitespace-nowrap',
                  !store.vehicleAuto.vehicle_speed_autoplay_fullscreen
                    ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                    : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
                ]"
              >
                后台静默放歌 (保持高德导航)
              </button>
              <button 
                @click="setAutoplayFullscreen(true)"
                :class="[
                  'h-[52px] px-4 rounded-xl font-black text-[15px] cursor-pointer transition-all border-2 whitespace-nowrap',
                  store.vehicleAuto.vehicle_speed_autoplay_fullscreen
                    ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                    : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
                ]"
              >
                前台全屏
              </button>
            </div>
          </div>
        </div>

        <!-- 启播目标音乐应用选择 (支持自动探测、手动强刷扫描、▲/▼ 优先级排序与更多软件选择) -->
        <div class="pt-3 flex flex-col space-y-3 border-t border-car-border/60">
          <div class="flex items-center justify-between">
            <div class="flex items-center space-x-2">
              <span class="text-[15.5px] font-black text-car-text">自启首选主力音乐软件：</span>
              <span class="text-[13.5px] text-car-sub font-bold">（开机自动探测，点击任意磁贴直接设为首选主力）</span>
            </div>
            <div class="flex items-center space-x-2.5">
              <button 
                @click="refreshDetectedApps" 
                class="h-[52px] px-4 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-accent font-black text-[14.5px] cursor-pointer shadow-sm transition-all flex items-center space-x-1.5"
              >
                
                <span>手动重新扫描</span>
              </button>
              <button 
                @click="openSelectModal('speed_autoplay')"
                class="h-[52px] px-4 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[14.5px] cursor-pointer shadow-sm transition-all flex items-center space-x-1.5"
              >
                
                <span>自定义应用排序 (▲/▼) ➔</span>
              </button>
            </div>
          </div>

          <!-- 常用音乐软件磁贴 (按用户优先级动态排序) -->
          <div class="flex flex-wrap -m-1">
            <button 
              v-for="app in sortedMusicApps" 
              :key="app.pkg"
              @click="setAutoplayApp(app.pkg, app.name)"
              :class="[
                'h-[54px] px-5 rounded-xl text-[15.5px] font-black cursor-pointer transition-all border-2 whitespace-nowrap flex items-center space-x-2 m-1 shadow-sm',
                store.vehicleAuto.vehicle_speed_autoplay_pkg === app.pkg
                  ? 'bg-car-item border-car-accent text-car-accent shadow-md ring-2 ring-car-accent/20'
                  : 'bg-car-card border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'
              ]"
            >
              <span v-if="store.vehicleAuto.vehicle_speed_autoplay_pkg === app.pkg" class="px-2 py-0.5 text-[11px] rounded bg-car-item border border-car-accent text-car-accent font-black mr-1">首选</span>
              <span>{{ app.name }}</span>
            </button>
            <button 
              @click="openSelectModal('speed_autoplay')"
              class="h-[54px] px-5 rounded-xl text-[15.5px] font-black cursor-pointer transition-all border-2 border-car-border bg-car-card hover:border-car-accent text-car-accent whitespace-nowrap flex items-center space-x-2 m-1 shadow-sm"
            >
              
              <span>自选整车已装软件 ➔</span>
            </button>
          </div>
        </div>
      </div>

      <!-- 任务 4: 车速达标自定义动作与唤起应用 (整宽大卡片 · 纯加减微调控制器) -->
      <div 
        v-if="isTaskVisible('speed_action')"
        class="col-span-2 rounded-3xl border-2 border-car-border hover:border-car-border-light bg-car-card p-6 shadow-xl flex flex-col space-y-4 transition-all duration-200"
      >
        <div class="flex items-center justify-between">
          <div class="flex items-center space-x-3">
            <span class="text-[22px] font-black text-car-text tracking-wide">4. 车速达标自定义动作与唤起应用</span>
            <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent">自定动作</span>
          </div>
          <div class="flex items-center space-x-3">
            <button
              @click="toggleSetting('vehicle_speed_custom_action_enabled')"
              :class="[
                'h-[54px] px-6 rounded-2xl font-black text-[16px] cursor-pointer transition-all border-2 flex items-center space-x-2',
                store.vehicleAuto.vehicle_speed_custom_action_enabled
                  ? 'bg-car-item border-car-accent text-car-text shadow-md'
                  : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
              ]"
            >
              <span :class="['w-2.5 h-2.5 rounded-full', store.vehicleAuto.vehicle_speed_custom_action_enabled ? 'bg-car-accent' : 'bg-car-sub']"></span>
              <span>{{ store.vehicleAuto.vehicle_speed_custom_action_enabled ? '计划运行中' : '计划已暂停' }}</span>
            </button>
            <button 
              @click="removeTask('speed_action', '车速自定义动作')"
              class="h-[54px] px-5 rounded-2xl bg-car-item border-2 border-car-border hover:border-red-500/80 text-car-sub hover:text-red-400 font-black text-[15px] cursor-pointer shadow-sm transition-all flex items-center space-x-1.5"
            >
              
              <span>移除</span>
            </button>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 pt-1">
          <!-- 当: 设定速度 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3">
            <div class="flex items-center space-x-3">
              <span class="text-[16px] font-black text-car-text">起步加速，当前车速达到设定阈值：</span>
            </div>
            <!-- 纯加减数值步进器 -->
            <div class="flex items-center justify-between bg-car-card border-2 border-car-border rounded-2xl p-2.5">
              <div class="flex items-center space-x-2">
                <button 
                  @click="adjustCustomActionSpeed(-5)"
                  class="h-[52px] px-4 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[16px] cursor-pointer active:scale-95 transition-all shadow-sm"
                >
                  -5
                </button>
                <button 
                  @click="adjustCustomActionSpeed(-1)"
                  class="h-[52px] px-4 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[16px] cursor-pointer active:scale-95 transition-all shadow-sm"
                >
                  -1
                </button>
              </div>
              <div class="flex items-baseline space-x-1 px-4">
                <span class="text-[32px] font-black text-car-accent tracking-tight font-mono">
                  {{ store.vehicleAuto.vehicle_speed_custom_action_threshold || 40 }}
                </span>
                <span class="text-[15px] text-car-sub font-black">km/h</span>
              </div>
              <div class="flex items-center space-x-2">
                <button 
                  @click="adjustCustomActionSpeed(1)"
                  class="h-[52px] px-4 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[16px] cursor-pointer active:scale-95 transition-all shadow-sm"
                >
                  +1
                </button>
                <button 
                  @click="adjustCustomActionSpeed(5)"
                  class="h-[52px] px-4 rounded-xl bg-car-item border-2 border-car-border hover:border-car-accent text-car-text font-black text-[16px] cursor-pointer active:scale-95 transition-all shadow-sm"
                >
                  +5
                </button>
              </div>
            </div>
          </div>

          <!-- 就: 执行动作 -->
          <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col space-y-3">
            <div class="flex items-center space-x-3">
              <span class="text-[16px] font-black text-car-text">自动唤起预选目标（单次行程防抖，回 P 挡重置）：</span>
            </div>
            <!-- 动作选择：默认高德车机地图，或自选整车任意第三方软件 -->
            <div class="flex items-center space-x-3">
              <button 
                @click="setCustomActionTarget('pkg:com.autonavi.amapauto')"
                :class="[
                  'flex-1 h-[52px] rounded-xl font-black text-[15px] cursor-pointer transition-all border-2 whitespace-nowrap',
                  store.vehicleAuto.vehicle_speed_custom_action_target === 'pkg:com.autonavi.amapauto'
                    ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                    : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
                ]"
              >
                高德车机地图
              </button>
              <button 
                @click="openSelectModal('speed_custom_action')"
                :class="[
                  'h-[52px] px-4 rounded-xl font-black text-[15px] cursor-pointer transition-all border-2 whitespace-nowrap',
                  store.vehicleAuto.vehicle_speed_custom_action_target?.startsWith('pkg:') && store.vehicleAuto.vehicle_speed_custom_action_target !== 'pkg:com.autonavi.amapauto'
                    ? 'bg-car-card border-car-accent text-car-accent shadow-md'
                    : 'bg-car-card border-car-border text-car-sub hover:text-car-text'
                ]"
              >
                {{ customActionAppName ? customActionAppName : '自选应用 ➔' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 任务 5: P 挡开门多媒体优雅静音 -->
      <div 
        v-if="isTaskVisible('door_pause')"
        class="rounded-3xl border-2 border-car-border hover:border-car-border-light bg-car-card p-6 shadow-xl h-full min-h-[260px] flex flex-col justify-between transition-all duration-200"
      >
        <div class="flex items-center justify-between shrink-0 mb-2">
          <div class="flex items-center space-x-3">
            <span class="text-[21px] font-black text-car-text tracking-wide">5. 停稳推门多媒体暂停</span>
            <span class="px-3 py-0.5 text-[13.5px] font-black rounded-full border bg-car-item border-car-border text-car-accent shrink-0">下车静音</span>
            <button 
              @click.stop="showHelp('door_pause')"
              class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-accent hover:border-car-accent font-black text-[18px] flex items-center justify-center cursor-pointer shadow-sm transition-transform active:scale-95 shrink-0"
              title="查看功能指南"
            >?</button>
          </div>
          <button 
            @click="removeTask('door_pause', '推门暂停音乐')"
            class="h-[50px] px-4 rounded-xl bg-car-item border border-car-border hover:border-red-500/80 text-car-sub hover:text-red-400 font-black text-[14px] cursor-pointer shadow-sm transition-all shrink-0"
            title="从工作台移除"
          >
            移除
          </button>
        </div>

        <div class="flex-1 min-w-0 flex flex-col justify-center space-y-1.5 py-3 text-[15px] leading-relaxed">
          <div class="text-car-sub font-bold">挂入驻车 P 挡且推开前排车门（准备下车）</div>
          <div class="text-car-text font-bold">自动向音乐播放器发送暂停指令，下车优雅安静</div>
        </div>

        <div class="pt-4 border-t border-car-border/60 mt-auto shrink-0">
          <button
            @click="toggleSetting('vehicle_door_pause_music_enabled')"
            :class="[
              'w-full h-[52px] px-6 rounded-2xl font-black text-[16px] cursor-pointer transition-all border-2 flex items-center justify-center space-x-2 shadow-sm',
              store.vehicleAuto.vehicle_door_pause_music_enabled
                ? 'bg-car-item border-car-accent text-car-text shadow-md'
                : 'bg-car-item border-car-border text-car-sub hover:text-car-text'
            ]"
          >
            <span :class="['w-2.5 h-2.5 rounded-full', store.vehicleAuto.vehicle_door_pause_music_enabled ? 'bg-car-accent shadow-[0_0_6px_var(--accent-gold)]' : 'bg-car-sub']"></span>
            <span>{{ store.vehicleAuto.vehicle_door_pause_music_enabled ? '推门暂停音乐运行中' : '推门暂停音乐已暂停' }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 添加计划任务弹窗 (AddTaskModal) -->
    <div 
      v-if="showAddModal" 
      class="fixed z-[9998] flex items-center justify-center bg-black/75 p-6"
      style="top:0; left:0; width:100vw; height:100vh;"
    >
      <div class="bg-car-card border-2 border-car-border rounded-3xl p-7 shadow-2xl max-w-[800px] w-full flex flex-col space-y-6">
        <div class="flex items-center justify-between border-b border-car-border pb-4">
          <div class="flex items-center space-x-3">
            <span class="w-3 h-3 rounded-full bg-car-accent"></span>
            <span class="text-[22px] font-black text-car-text">添加联动计划至工作台</span>
          </div>
          <button 
            @click="showAddModal = false"
            class="w-[50px] h-[50px] rounded-full border-2 border-car-border bg-car-item text-car-sub hover:text-car-text font-black text-[20px] flex items-center justify-center cursor-pointer"
          >
            ✕
          </button>
        </div>

        <div class="flex flex-col space-y-3 max-h-[460px] overflow-y-auto pr-1">
          <div 
            v-for="task in allTasks" 
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
              v-if="removedTaskIds.includes(task.id)"
              @click="restoreTask(task.id)"
              class="h-[52px] px-6 rounded-xl bg-car-card border-2 border-car-accent text-car-accent font-black text-[15px] cursor-pointer hover:bg-car-item whitespace-nowrap shadow-sm"
            >
              加入工作台
            </button>
            <div 
              v-else
              class="h-[52px] px-5 rounded-xl bg-emerald-500/10 border border-emerald-500/40 text-emerald-400 font-black text-[14px] flex items-center space-x-1.5 whitespace-nowrap select-none shadow-sm"
            >
              <span class="w-2 h-2 rounded-full bg-emerald-400 shadow-[0_0_6px_#10B981]"></span>
              <span>已在工作台中</span>
            </div>
          </div>
        </div>

        <div class="flex justify-end pt-2">
          <button 
            @click="showAddModal = false"
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

const STORAGE_REMOVED_KEY = 'geely_removed_link_tasks_v2';
const removedTaskIds = ref(JSON.parse(localStorage.getItem(STORAGE_REMOVED_KEY) || '[]'));
const showAddModal = ref(false);

const allTasks = [
  { id: 'd_360', title: '前进 D 挡起步联动 360 全景', tag: '起步环视', desc: '从 P/R 挂入 D 挡起步唤醒 360 环视四周；单次跃变锁防反复弹出。' },
  { id: 'speed_music', title: '车速达标智能启播车载音乐', tag: '起步自启', desc: '车速达到设定阈值后后台自动启播自选音乐，支持纯加减自由微调速度。' },
  { id: 'speed_action', title: '车速达标自定义动作与唤起应用', tag: '自定动作', desc: '车速达标后自动打开高德或整车已安装的任意第三方软件。' },
  { id: 'door_pause', title: '停稳推门多媒体自动暂停', tag: '下车静音', desc: '挂 P 挡推开车门秒级暂停音乐，下车安静从容。' }
];

const visibleTasks = computed(() => {
  return allTasks.filter(t => !removedTaskIds.value.includes(t.id));
});

const availablePresetTasks = computed(() => {
  return allTasks.filter(t => removedTaskIds.value.includes(t.id));
});

function isTaskVisible(id) {
  return !removedTaskIds.value.includes(id);
}

const activeTaskCount = computed(() => {
  let count = 0;
  if (isTaskVisible('d_360') && store.vehicleAuto.vehicle_d_gear_360_enabled) count++;
  if (isTaskVisible('speed_music') && store.vehicleAuto.vehicle_speed_autoplay_enabled) count++;
  if (isTaskVisible('speed_action') && store.vehicleAuto.vehicle_speed_custom_action_enabled) count++;
  if (isTaskVisible('door_pause') && store.vehicleAuto.vehicle_door_pause_music_enabled) count++;
  return count;
});

function removeTask(id, name) {
  if (!removedTaskIds.value.includes(id)) {
    removedTaskIds.value.push(id);
    localStorage.setItem(STORAGE_REMOVED_KEY, JSON.stringify(removedTaskIds.value));
    showToast(`已从工作台移除联动计划: ${name}（随时可在顶部添加回来）`);
  }
}

function restoreTask(id) {
  removedTaskIds.value = removedTaskIds.value.filter(item => item !== id);
  localStorage.setItem(STORAGE_REMOVED_KEY, JSON.stringify(removedTaskIds.value));
  showToast('已成功添加计划至工作台');
}

function resetAllTasks() {
  removedTaskIds.value = [];
  localStorage.setItem(STORAGE_REMOVED_KEY, JSON.stringify([]));
  showToast('已恢复全量推荐车身联动计划');
}

function openAddTaskModal() {
  showAddModal.value = true;
}

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

  const currentPkg = store.vehicleAuto.vehicle_speed_autoplay_pkg;
  const currentName = localStorage.getItem('vehicle_speed_autoplay_app_name');
  if (currentPkg && !list.some(a => a.pkg === currentPkg)) {
    list.unshift({ name: currentName || '自选应用', pkg: currentPkg });
  }

  sortedMusicApps.value = list;

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
  
  // 双向兼容映射：保证 Java 后端能百分之百接收对应真实 key
  const aliasMap = {
    vehicle_d_gear_360_enabled: 'vehicle_gear_d_360_enabled',
    vehicle_gear_d_360_enabled: 'vehicle_d_gear_360_enabled'
  };
  if (aliasMap[key]) {
    store.vehicleAuto[aliasMap[key]] = next;
    bridge.call('setVehicleAutomationSetting', aliasMap[key], next);
  }
  showToast('计划状态已更新: ' + (next ? '已开启执行' : '已暂停'));
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
  if (target === 'pkg:com.autonavi.amapauto') {
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

function openSelectModal(target) {
  openModal('appSelect', { keyTarget: target });
}

function showHelp(key) {
  const helpData = {
    gear_d_360: {
      title: '【功能指南】前进 D 挡起步 360 全景',
      desc: '1. 单次跃变锁：从 P 挡或 R 挡切入前进 D 挡瞬间自动唤醒 360 环视四周；触发一次即锁定，手动退出绝不重复弹出。\n\n2. 自动退出：起步车速超 15km/h 自动退出全景，保持导航视线。',
      tip: '切出 D 挡离开后才会重新武装待命。'
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
  if (store.vehicleAuto.vehicle_gear_d_360_enabled !== undefined) {
    store.vehicleAuto.vehicle_d_gear_360_enabled = store.vehicleAuto.vehicle_gear_d_360_enabled;
  }
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