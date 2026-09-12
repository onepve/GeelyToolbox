<template>
  <div class="flex flex-col space-y-6">
    <!-- 核心：方向盘按键方控接管总开关 - 提权置顶首屏 (左右分栏车规黄金磁贴) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-5 min-h-[106px] shadow-xl flex items-center justify-between transition-all">
      <div class="w-[60%] max-w-[60%] flex flex-col space-y-1.5 shrink-0">
        <div class="flex items-center space-x-3">
          <span :class="['w-3.5 h-3.5 rounded-full shadow-md shrink-0', store.vehicleAuto.wheel_master_switch ? 'bg-emerald-500 shadow-[0_0_10px_#10B981]' : 'bg-slate-400']"></span>
          <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">方向盘按键方控接管总开关</span>
          <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-item border-car-border text-car-text inline-flex items-center shrink-0 shadow-sm">
            <span :class="['w-2.5 h-2.5 rounded-full mr-2', store.vehicleAuto.wheel_master_switch ? 'bg-emerald-500 shadow-[0_0_6px_#10B981]' : 'bg-slate-400']"></span>
            {{ store.vehicleAuto.wheel_master_switch ? '方控接管已启用' : '方控已彻底放行 (不干涉)' }}
          </span>
        </div>
        <div class="text-[14.5px] text-car-sub font-bold leading-normal">
          全面接管静音键、Mode键与上一曲/下一曲。支持单击、双击、长按三种手势。
        </div>
      </div>

      <div class="shrink-0 w-[230px]">
        <button
          @click="toggleWheelMasterSwitch"
          :class="[
            'w-full h-[74px] px-4 py-2 rounded-2xl border-2 cursor-pointer transition-all shadow-md flex flex-col items-center justify-center text-center',
            store.vehicleAuto.wheel_master_switch
              ? 'bg-car-item border-car-accent'
              : 'bg-car-card border-car-border hover:border-car-border-light'
          ]"
        >
          <span class="text-[18.5px] font-black text-car-text tracking-wide whitespace-nowrap">
            {{ store.vehicleAuto.wheel_master_switch ? '方控接管已开启' : '方控接管已关闭' }}
          </span>
          <span :class="['text-[12.5px] font-bold mt-1 whitespace-nowrap', store.vehicleAuto.wheel_master_switch ? 'text-car-accent' : 'text-car-sub']">
            {{ store.vehicleAuto.wheel_master_switch ? '点击切换为彻底放行' : '点击开启按键接管' }}
          </span>
        </button>
      </div>
    </div>

    <!-- 车型图解与协议指示区 (缤越 COOL / SX-0017 原厂按键分布) -->
    <div class="bg-car-item border border-car-border rounded-2xl p-5 shadow-sm">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center">
          <span class="w-3 h-3 rounded-full bg-car-accent mr-3 shadow-[0_0_8px_var(--accent-gold)]"></span>
          <div class="flex flex-col">
            <span class="text-[18px] font-black text-car-text">吉利缤越 COOL (IHU516G / E02) 原厂方控图解</span>
            <span class="text-[14px] text-car-sub font-bold mt-0.5">
              {{ activeCluster === 'right' ? '右方向盘多媒体控制区 (SX-0017)：静音、切歌、MODE、返回键全量打通多手势' : '左方向盘智驾控制区 (SX-0016)：智驾领航、LIM限速、⑦号自定义高德往返按键' }}
            </span>
          </div>
        </div>
        <div class="flex items-center space-x-3">
          <div class="flex bg-car-card border-2 border-car-border rounded-2xl p-1.5 items-center">
            <button 
              @click="setCluster('left')"
              :class="[
                'h-[52px] px-5 rounded-xl text-[16px] font-black cursor-pointer transition-all whitespace-nowrap flex items-center',
                activeCluster === 'left' ? 'bg-car-item border-2 border-car-accent text-car-text shadow-md ring-2 ring-car-accent/20' : 'text-car-sub hover:text-car-text'
              ]"
            >
              左方向盘 (SX-0016 智驾区)
            </button>
            <button 
              @click="setCluster('right')"
              :class="[
                'h-[52px] px-5 rounded-xl text-[16px] font-black cursor-pointer transition-all whitespace-nowrap flex items-center',
                activeCluster === 'right' ? 'bg-car-item border-2 border-car-accent text-car-text shadow-md ring-2 ring-car-accent/20' : 'text-car-sub hover:text-car-text'
              ]"
            >
              右方向盘 (SX-0017 多媒体)
            </button>
          </div>
          <button 
            @click="toggleDiagram()"
            class="h-[52px] px-6 rounded-2xl bg-car-card border-2 border-car-border text-car-text font-black text-[16px] cursor-pointer hover:border-car-border-light transition-all shadow-sm whitespace-nowrap flex items-center"
          >
            {{ showDiagram ? '收起图解' : '展开图解' }}
          </button>
        </div>
      </div>

      <!-- 图解内容展示区 -->
      <div v-if="showDiagram" class="mt-4 pt-4 border-t border-car-border/60 flex items-start space-x-6">
        <!-- 原厂方向盘按键高清图示 (沉稳车规底座，绝非刺眼白底) -->
        <div class="p-3 bg-car-item rounded-2xl shadow-md border-2 border-car-border w-[320px] min-w-[320px] max-w-[320px] shrink-0">
          <img 
            :src="activeCluster === 'right' ? wheelGuideImg : wheelLeftGuideImg" 
            :alt="activeCluster === 'right' ? '吉利缤越 COOL 右侧多媒体 SX-0017' : '吉利缤越 COOL 左侧智驾 SX-0016'" 
            class="w-full h-auto rounded-xl object-contain block" 
          />
        </div>

        <!-- 按键编号与原厂定义清单 -->
        <div v-if="activeCluster === 'right'" class="flex-1 min-w-0 flex flex-col space-y-2.5 text-[14.5px] font-bold text-car-sub">
          <div class="flex space-x-3">
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">① 主页/返回键:</span> 返回车机中控主页 / 支持多手势
            </div>
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">② 音量调节键:</span> 上下调音 / 滚轮垂直下按可自定义
            </div>
          </div>
          <div class="flex space-x-3">
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">③ 静音键:</span> 开关静音 (支持多手势自定义)
            </div>
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">④ 向右选择键:</span> 下一个文件 / 下一曲 (支持多手势)
            </div>
          </div>
          <div class="flex space-x-3">
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">⑤ 自定义键:</span> 0x37 硬件按键 / 高德往返自定义
            </div>
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">⑥ MODE键:</span> 音源切换 / 自定义 (支持多手势)
            </div>
          </div>
          <div class="w-full p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
            <span class="text-car-accent font-black mr-1.5">⑦ 向左选择键:</span> 上一个文件 / 上一曲 (支持多手势)
          </div>
        </div>

        <div v-else class="flex-1 min-w-0 flex flex-col space-y-2.5 text-[14.5px] font-bold text-car-sub">
          <div class="flex space-x-3">
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-text font-black mr-1.5">① 智驾领航键:</span> ICC 智能领航 / 自适应巡航
            </div>
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-text font-black mr-1.5">② LIM 限速键:</span> 最高限速设定与开启
            </div>
          </div>
          <div class="flex space-x-3">
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-text font-black mr-1.5">③ 巡航调节滚轮:</span> 设定速度上下微调与确认
            </div>
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-text font-black mr-1.5">④ 跟车距离调节:</span> 多挡跟车安全时距切换
            </div>
          </div>
          <div class="flex space-x-3">
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">⑤ 电话接听键:</span> 原厂电话接听与挂断
            </div>
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">⑥ 语音唤醒键:</span> 原厂语音控制话筒键
            </div>
          </div>
          <div class="w-full p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
            <span class="text-car-accent font-black mr-1.5">⑦ 自定义按键:</span> 高德地图与桌面一键往返 (盲操神器)
          </div>
        </div>
      </div>
    </div>

    <!-- 1. 方向盘方控接管模式 -->
    <FeatureCard 
      title="1. 方向盘方控接管模式 (兼容米小江)"
      desc="彻底屏蔽原厂收音机伴听抢占广播，支持米小江优先协同或控制台全量独立接管。若两个软件都装，选米小江优先互不冲突。"
      helpTitle="【功能指南】方控接管模式、EAS 蓝牙硬件通道与仪表投递"
      helpText="1. 三种接管模式解析：&#10;• 米小江方控优先：放行切歌与 Mode 键由第三方软件处理，工具箱仅补充静音键多手势接管；&#10;• 控制台独立接管 (推荐)：全量接管切歌与按键，并自动选通原车 EAS 蓝牙音频硬件通道；&#10;• 恢复原厂默认：解禁原厂广播接收器，完全不拦截任何按键。&#10;&#10;2. 解决手机蓝牙无声死锁：&#10;在独立接管模式下，手机蓝牙连接车机后，工具箱自动向 EAS 注册并选通 6 号蓝牙物理声道，解决原厂多媒体冻结后手机导航与微信无声的痛点。&#10;&#10;3. 仪表盘与屏保投递双独立开关：&#10;支持独立开关控制是否将歌名、歌手投递给液晶仪表盘与息屏时钟，以及是否向 HUD 推送歌词。默认保持关闭，维持纯净黑底大时钟。"
      helpTip="若同时安装了米小江 CarMedia，请选择【米小江方控优先】；若仅使用工具箱，推荐选择【控制台独立接管】。"
    >
      <div class="grid grid-cols-3 gap-3.5" style="grid-gap: 14px; -webkit-column-gap: 14px;">
        <MatrixButton 
          title="米小江方控优先"
          subtitle="放行切歌与Mode键，工具箱补充静音键多手势接管"
          :active="store.vehicleAuto.wheel_control_mode === 'carmedia_first'"
          @click="setWheelMode('carmedia_first')"
        />
        <MatrixButton 
          title="控制台独立接管 (推荐)"
          subtitle="全量接管切歌与按键，并自动选通原车蓝牙音频硬件通道"
          :active="store.vehicleAuto.wheel_control_mode === 'toolbox_alone' || (!store.vehicleAuto.wheel_control_mode && store.vehicleAuto.wheel_control_mode !== 'factory_default')"
          @click="setWheelMode('toolbox_alone')"
        />
        <MatrixButton 
          title="恢复原厂默认"
          subtitle="解禁原厂广播接收器，完全不拦截任何按键"
          :active="store.vehicleAuto.wheel_control_mode === 'factory_default'"
          @click="setWheelMode('factory_default')"
        />
      </div>

      <!-- 仪表盘与屏保显示扩展 (独立开关·默认关闭) -->
      <div class="mt-5 pt-5 border-t border-car-border/60 flex flex-col space-y-4">
        <div class="flex items-center justify-between p-4 rounded-2xl bg-car-item border-2 border-car-border">
          <div class="flex flex-col space-y-1">
            <span class="text-[17px] font-black text-car-text">投递播放状态至仪表盘与息屏时钟</span>
            <span class="text-[14px] text-car-sub font-bold">开启后向原厂 EAS 广播歌名与歌手，车机息屏后将显示音乐小部件卡片。默认关闭保持极简。</span>
          </div>
          <button 
            @click="toggleClusterPlayback" 
            class="h-[52px] px-5 rounded-xl text-[15.5px] font-black border-2 transition-all whitespace-nowrap cursor-pointer shadow-sm"
            :class="store.vehicleAuto.wheel_push_playback_cluster ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' : 'bg-car-card border-car-border text-car-sub hover:text-car-text'"
          >
            {{ store.vehicleAuto.wheel_push_playback_cluster ? '已开启 (投递状态)' : '已关闭 (默认纯净)' }}
          </button>
        </div>

        <div class="flex items-center justify-between p-4 rounded-2xl bg-car-item border-2 border-car-border">
          <div class="flex flex-col space-y-1">
            <span class="text-[17px] font-black text-car-text">投递当前歌词至原车仪表盘 (HUD)</span>
            <span class="text-[14px] text-car-sub font-bold">开启后向吉利全液晶仪表盘中间卡片推送实时滚动歌词。默认关闭防信息刷屏。</span>
          </div>
          <button 
            @click="toggleClusterLyrics" 
            class="h-[52px] px-5 rounded-xl text-[15.5px] font-black border-2 transition-all whitespace-nowrap cursor-pointer shadow-sm"
            :class="store.vehicleAuto.wheel_push_lyrics_cluster ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' : 'bg-car-card border-car-border text-car-sub hover:text-car-text'"
          >
            {{ store.vehicleAuto.wheel_push_lyrics_cluster ? '已开启 (投递歌词)' : '已关闭 (默认纯净)' }}
          </button>
        </div>
      </div>
    </FeatureCard>

    <!-- 3. 方控按键长按判定时长 (自定义秒数) -->
    <FeatureCard 
      title="2. 方控按键长按判定触发时长 (自定义秒数)"
      desc="自由设定方向盘所有按键长按触发的判定时长 (0.8s ~ 6.0s)。达到该时长立即执行长按动作；长按 10 秒依然是整车硬件看门狗冷重启救砖，互不冲突。"
      helpTitle="【功能指南】长按判定时长与整车硬件看门狗冷重启"
      helpText="1. 自定义判定时长：&#10;自由设定方向盘所有按键长按触发的判定时长（推荐 1.5s ~ 2.0s）。达到该时长立即执行长按自定义动作。&#10;&#10;2. 整车硬件冷重启保护：&#10;方向盘静音键（3号键）长按 10 秒是吉利 MCU 硬件看门狗底层强制断电冷重启，属于硬件级救砖功能。无论你在本卡片如何调整长按时长，10 秒冷重启都绝对不受影响、零冲突！"
      helpTip="建议将长按判定时长设为 1.5 秒或 2.0 秒，手感最为干脆舒适。"
    >
      <template #badge>
        <span class="text-[15px] font-black text-car-accent px-3.5 py-1.5 bg-car-item rounded-xl border border-car-border">
          当前判定: {{ longPressSec }} 秒 ({{ Math.round(longPressSec * 1000) }}ms)
        </span>
      </template>

      <div class="bg-car-item border border-car-border rounded-2xl p-4 flex items-center space-x-4 shadow-sm">
        <span class="text-[14px] text-car-sub font-bold whitespace-nowrap">0.8 秒 (极速触发)</span>
        <input 
          type="range" 
          min="0.8" 
          max="6.0" 
          step="0.1" 
          v-model.number="longPressSec" 
          @input="updateLongPressSec"
          @change="updateLongPressSec"
          class="flex-1 accent-car-accent h-2.5 bg-car-card rounded-lg cursor-pointer"
        />
        <span class="text-[14px] text-car-sub font-bold whitespace-nowrap">6.0 秒 (防误触)</span>
        <div class="flex space-x-2 shrink-0">
          <button 
            v-for="preset in [1.0, 1.5, 2.0, 3.0, 5.0]" 
            :key="preset"
            @click="setLongPressPreset(preset)"
            :class="[
              'px-3 py-1.5 text-[13.5px] font-black rounded-xl border transition-all cursor-pointer shadow-sm',
              longPressSec === preset ? 'bg-car-card border-2 border-car-accent text-car-accent' : 'bg-car-card border border-car-border text-car-sub'
            ]"
          >
            {{ preset }}s
          </button>
        </div>
      </div>
    </FeatureCard>

    <!-- 3. 右方向盘 ① 主页/返回按键多手势映射 -->
    <FeatureCard 
      title="3. 右方向盘 ① 主页/返回按键映射"
      desc="对应右方向盘 ① 号返回/主页键（KeyCode 307）。支持【单击】、【双击】、【长按】自定义。可保留系统原厂返回，或映射为高德导航、360全景或自定义应用。"
    >
      <div class="flex items-center justify-between bg-car-item border border-car-border rounded-2xl p-3 mb-4 shadow-sm">
        <div class="flex items-center space-x-2">
          <button
            v-for="g in gestureList"
            :key="g.id"
            @click="activeGesture.back = g.id"
            :class="[
              'px-4 py-2 rounded-xl text-[14.5px] font-black cursor-pointer transition-all whitespace-nowrap',
              activeGesture.back === g.id
                ? 'bg-car-card border-2 border-car-accent text-car-text shadow-sm'
                : 'bg-car-card border border-car-border text-car-sub hover:border-car-border-light'
            ]"
          >
            {{ g.name }}
          </button>
        </div>
        <div class="text-[13.5px] font-bold text-car-sub">
          当前配置：<span class="text-car-accent">单击[{{ getActionName(getGestureAction('back', 'single')) }}]</span> · 
          <span>双击[{{ getActionName(getGestureAction('back', 'double')) }}]</span> · 
          <span>长按[{{ getActionName(getGestureAction('back', 'long')) }}]</span>
        </div>
      </div>

      <div class="grid grid-cols-5 gap-3.5" style="grid-gap: 14px; -webkit-column-gap: 14px;">
        <MatrixButton 
          title="保持原厂不动"
          subtitle="系统原厂返回"
          :active="getGestureAction('back', activeGesture.back) === 'default'"
          @click="setGestureAction('back', activeGesture.back, 'default')"
        />
        <MatrixButton 
          title="打开高德地图"
          subtitle="一键秒切导航"
          :active="getGestureAction('back', activeGesture.back) === 'open_navi'"
          @click="setGestureAction('back', activeGesture.back, 'open_navi')"
        />
        <MatrixButton 
          title="打开 360 全景"
          subtitle="一键秒看盲区"
          :active="getGestureAction('back', activeGesture.back) === 'open_360'"
          @click="setGestureAction('back', activeGesture.back, 'open_360')"
        />
        <MatrixButton 
          title="音量暂停 / 播放"
          subtitle="媒体暂停或继续"
          :active="getGestureAction('back', activeGesture.back) === 'play_pause'"
          @click="setGestureAction('back', activeGesture.back, 'play_pause')"
        />
        <MatrixButton 
          :title="isCustomApp(getGestureAction('back', activeGesture.back)) ? (getCustomAppName('back_' + activeGesture.back) || '自定义应用') : '自定义打开应用'"
          :subtitle="isCustomApp(getGestureAction('back', activeGesture.back)) ? '点击可重新更换应用' : '自由挑选车机第三方应用'"
          :active="isCustomApp(getGestureAction('back', activeGesture.back))"
          @click="openAppSelectModal('back_' + activeGesture.back)"
        />
      </div>
    </FeatureCard>

    <!-- 4. 右方向盘 ② 音量滚轮按压多手势映射 -->
    <FeatureCard 
      title="4. 右方向盘 ② 音量滚轮按压映射"
      desc="对应右方向盘 ② 号音量滚轮垂直按压（Tasker 黄金键码 0x2d）。支持【单击】、【双击】、【长按】多手势；默认单击控制媒体暂停/播放，亦可秒开 360 或高德。"
    >
      <div class="flex items-center justify-between bg-car-item border border-car-border rounded-2xl p-3 mb-4 shadow-sm">
        <div class="flex items-center space-x-2">
          <button
            v-for="g in gestureList"
            :key="g.id"
            @click="activeGesture.ok = g.id"
            :class="[
              'px-4 py-2 rounded-xl text-[14.5px] font-black cursor-pointer transition-all whitespace-nowrap',
              activeGesture.ok === g.id
                ? 'bg-car-card border-2 border-car-accent text-car-text shadow-sm'
                : 'bg-car-card border border-car-border text-car-sub hover:border-car-border-light'
            ]"
          >
            {{ g.name }}
          </button>
        </div>
        <div class="text-[13.5px] font-bold text-car-sub">
          当前配置：<span class="text-car-accent">单击[{{ getActionName(getGestureAction('ok', 'single')) }}]</span> · 
          <span>双击[{{ getActionName(getGestureAction('ok', 'double')) }}]</span> · 
          <span>长按[{{ getActionName(getGestureAction('ok', 'long')) }}]</span>
        </div>
      </div>

      <div class="grid grid-cols-5 gap-3.5" style="grid-gap: 14px; -webkit-column-gap: 14px;">
        <MatrixButton 
          title="保持原厂不动"
          subtitle="原厂按压确认"
          :active="getGestureAction('ok', activeGesture.ok) === 'default'"
          @click="setGestureAction('ok', activeGesture.ok, 'default')"
        />
        <MatrixButton 
          title="音量暂停 / 播放"
          subtitle="媒体暂停或继续"
          :active="getGestureAction('ok', activeGesture.ok) === 'play_pause'"
          @click="setGestureAction('ok', activeGesture.ok, 'play_pause')"
        />
        <MatrixButton 
          title="打开 360 全景"
          subtitle="一键秒看盲区"
          :active="getGestureAction('ok', activeGesture.ok) === 'open_360'"
          @click="setGestureAction('ok', activeGesture.ok, 'open_360')"
        />
        <MatrixButton 
          title="打开高德地图"
          subtitle="一键秒切导航"
          :active="getGestureAction('ok', activeGesture.ok) === 'open_navi'"
          @click="setGestureAction('ok', activeGesture.ok, 'open_navi')"
        />
        <MatrixButton 
          :title="isCustomApp(getGestureAction('ok', activeGesture.ok)) ? (getCustomAppName('ok_' + activeGesture.ok) || '自定义应用') : '自定义打开应用'"
          :subtitle="isCustomApp(getGestureAction('ok', activeGesture.ok)) ? '点击可重新更换应用' : '自由挑选车机第三方应用'"
          :active="isCustomApp(getGestureAction('ok', activeGesture.ok))"
          @click="openAppSelectModal('ok_' + activeGesture.ok)"
        />
      </div>
    </FeatureCard>

    <!-- 5. 右方向盘 ③ 静音键多手势映射 -->
    <FeatureCard 
      title="5. 右方向盘 ③ 静音按键映射"
      desc="对应右方向盘 ③ 号静音键（KeyCode 300）。支持【单击】、【双击】、【长按 1.5 秒】多手势；长按 10 秒依然是整车硬件冷重启，互不冲突！"
    >
      <div class="flex items-center justify-between bg-car-item border border-car-border rounded-2xl p-3 mb-4 shadow-sm">
        <div class="flex items-center space-x-2">
          <button
            v-for="g in gestureList"
            :key="g.id"
            @click="activeGesture.mute = g.id"
            :class="[
              'px-4 py-2 rounded-xl text-[14.5px] font-black cursor-pointer transition-all whitespace-nowrap',
              activeGesture.mute === g.id
                ? 'bg-car-card border-2 border-car-accent text-car-text shadow-sm'
                : 'bg-car-card border border-car-border text-car-sub hover:border-car-border-light'
            ]"
          >
            {{ g.name }}
          </button>
        </div>
        <div class="text-[13.5px] font-bold text-car-sub">
          当前配置：<span class="text-car-accent">单击[{{ getActionName(getGestureAction('mute', 'single')) }}]</span> · 
          <span>双击[{{ getActionName(getGestureAction('mute', 'double')) }}]</span> · 
          <span>长按[{{ getActionName(getGestureAction('mute', 'long')) }}]</span>
        </div>
      </div>

      <div class="grid grid-cols-5 gap-3.5" style="grid-gap: 14px; -webkit-column-gap: 14px;">
        <MatrixButton 
          title="保持原厂不动"
          subtitle="系统原厂静音/恢复"
          :active="getGestureAction('mute', activeGesture.mute) === 'default'"
          @click="setGestureAction('mute', activeGesture.mute, 'default')"
        />
        <MatrixButton 
          title="打开 360 全景"
          subtitle="一键秒调环视盲区"
          :active="getGestureAction('mute', activeGesture.mute) === 'open_360'"
          @click="setGestureAction('mute', activeGesture.mute, 'open_360')"
        />
        <MatrixButton 
          title="打开高德地图"
          subtitle="一键秒切车载导航"
          :active="getGestureAction('mute', activeGesture.mute) === 'open_navi'"
          @click="setGestureAction('mute', activeGesture.mute, 'open_navi')"
        />
        <MatrixButton 
          title="音量暂停 / 播放"
          subtitle="媒体暂停或继续播放"
          :active="getGestureAction('mute', activeGesture.mute) === 'play_pause'"
          @click="setGestureAction('mute', activeGesture.mute, 'play_pause')"
        />
        <MatrixButton 
          :title="isCustomApp(getGestureAction('mute', activeGesture.mute)) ? (getCustomAppName('mute_' + activeGesture.mute) || '自定义应用') : '自定义打开应用'"
          :subtitle="isCustomApp(getGestureAction('mute', activeGesture.mute)) ? '点击可重新更换应用' : '自由挑选车机第三方应用'"
          :active="isCustomApp(getGestureAction('mute', activeGesture.mute))"
          @click="openAppSelectModal('mute_' + activeGesture.mute)"
        />
      </div>
    </FeatureCard>

    <!-- 6. 右方向盘 ④ 下一曲 / ⑦ 上一曲 切歌按键映射 -->
    <FeatureCard 
      title="6. 右方向盘 ④ 下一曲 / ⑦ 上一曲 切歌按键映射"
      desc="对应右方向盘 ④ 号（向右下一曲 KeyCode 305）与 ⑦ 号（向左上一曲 KeyCode 304）按键。内置官方三重通道调度，完美兼容 QQ音乐车机版、网易云等。"
    >
      <div class="grid grid-cols-2 gap-4">
        <!-- 下一曲 (④ 号键) -->
        <div class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col justify-between shadow-sm">
          <div class="flex items-center justify-between mb-3">
            <span class="text-[19px] font-black text-car-text">④ 下一曲键</span>
            <div class="flex space-x-1.5">
              <button
                v-for="g in gestureList"
                :key="g.id"
                @click="activeGesture.next = g.id"
                :class="[
                  'px-2.5 py-1 rounded-lg text-[13px] font-black cursor-pointer transition-all',
                  activeGesture.next === g.id
                    ? 'bg-car-card border border-car-accent text-car-text shadow-sm'
                    : 'bg-car-card border border-car-border text-car-sub'
                ]"
              >
                {{ g.shortName }}
              </button>
            </div>
          </div>
          <div class="grid grid-cols-3 gap-2.5 mb-2">
            <MatrixButton 
              title="下一曲"
              subtitle="切歌(官方调度)"
              :active="getGestureAction('next', activeGesture.next) === 'next_track' || getGestureAction('next', activeGesture.next) === 'default'"
              @click="setGestureAction('next', activeGesture.next, 'next_track')"
            />
            <MatrixButton 
              title="打开 360"
              subtitle="唤起环视"
              :active="getGestureAction('next', activeGesture.next) === 'open_360'"
              @click="setGestureAction('next', activeGesture.next, 'open_360')"
            />
            <MatrixButton 
              title="播放/暂停"
              subtitle="音乐暂停"
              :active="getGestureAction('next', activeGesture.next) === 'play_pause'"
              @click="setGestureAction('next', activeGesture.next, 'play_pause')"
            />
          </div>
        </div>

        <!-- 上一曲 (⑦ 号键) -->
        <div class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col justify-between shadow-sm">
          <div class="flex items-center justify-between mb-3">
            <span class="text-[19px] font-black text-car-text">⑦ 上一曲键</span>
            <div class="flex space-x-1.5">
              <button
                v-for="g in gestureList"
                :key="g.id"
                @click="activeGesture.prev = g.id"
                :class="[
                  'px-2.5 py-1 rounded-lg text-[13px] font-black cursor-pointer transition-all',
                  activeGesture.prev === g.id
                    ? 'bg-car-card border border-car-accent text-car-text shadow-sm'
                    : 'bg-car-card border border-car-border text-car-sub'
                ]"
              >
                {{ g.shortName }}
              </button>
            </div>
          </div>
          <div class="grid grid-cols-3 gap-2.5 mb-2">
            <MatrixButton 
              title="上一曲"
              subtitle="切歌(官方调度)"
              :active="getGestureAction('prev', activeGesture.prev) === 'prev_track' || getGestureAction('prev', activeGesture.prev) === 'default'"
              @click="setGestureAction('prev', activeGesture.prev, 'prev_track')"
            />
            <MatrixButton 
              title="打开高德"
              subtitle="唤起导航"
              :active="getGestureAction('prev', activeGesture.prev) === 'open_navi'"
              @click="setGestureAction('prev', activeGesture.prev, 'open_navi')"
            />
            <MatrixButton 
              title="播放/暂停"
              subtitle="音乐暂停"
              :active="getGestureAction('prev', activeGesture.prev) === 'play_pause'"
              @click="setGestureAction('prev', activeGesture.prev, 'play_pause')"
            />
          </div>
        </div>
      </div>
    </FeatureCard>

    <!-- 7. 右方向盘 ⑤ 自定义按键多手势映射 (高德地图 ↔ 桌面) -->
    <FeatureCard 
      title="7. 右方向盘 ⑤ 自定义按键映射 (高德 ↔ 桌面)"
      desc="对应右方向盘 ⑤ 号自定义按键（Tasker 黄金键码 0x37）。支持按第 1 下秒开高德地图，按第 2 下最小化退回桌面，实现两端无感盲操往返。"
    >
      <div class="grid grid-cols-2 gap-4">
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text">高德地图 ↔ 桌面一键往返</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">按第 1 下切出高德，按第 2 下最小化高德回桌面</div>
          </div>
          <MatrixButton 
            title="高德/桌面双向往返"
            subtitle="盲操神器 · 无感切换"
            :active="getGestureAction('custom', 'single') === 'open_navi' || getGestureAction('custom', 'single') === 'default'"
            @click="setGestureAction('custom', 'single', 'open_navi')"
          />
        </div>

        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text">360 全景 ↔ 桌面往返</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">按第 1 下呼出 360，按第 2 下退出 360 回桌面</div>
          </div>
          <MatrixButton 
            title="360 全景双向往返"
            subtitle="环视秒调 · 盲区随时看"
            :active="getGestureAction('custom', 'single') === 'open_360'"
            @click="setGestureAction('custom', 'single', 'open_360')"
          />
        </div>
      </div>
    </FeatureCard>

    <!-- 8. 右方向盘 ⑥ MODE 键多手势映射 -->
    <FeatureCard 
      title="8. 右方向盘 ⑥ MODE 按键映射"
      desc="对应右方向盘 ⑥ 号 MODE 模式键（KeyCode 348）。原车用于切换伴听/收音机。默认单击一键秒开 360 全景，同时支持双击与长按个性化定制。"
    >
      <div class="flex items-center justify-between bg-car-item border border-car-border rounded-2xl p-3 mb-4 shadow-sm">
        <div class="flex items-center space-x-2">
          <button
            v-for="g in gestureList"
            :key="g.id"
            @click="activeGesture.mode = g.id"
            :class="[
              'px-4 py-2 rounded-xl text-[14.5px] font-black cursor-pointer transition-all whitespace-nowrap',
              activeGesture.mode === g.id
                ? 'bg-car-card border-2 border-car-accent text-car-text shadow-sm'
                : 'bg-car-card border border-car-border text-car-sub hover:border-car-border-light'
            ]"
          >
            {{ g.name }}
          </button>
        </div>
        <div class="text-[13.5px] font-bold text-car-sub">
          当前配置：<span class="text-car-accent">单击[{{ getActionName(getGestureAction('mode', 'single')) }}]</span> · 
          <span>双击[{{ getActionName(getGestureAction('mode', 'double')) }}]</span> · 
          <span>长按[{{ getActionName(getGestureAction('mode', 'long')) }}]</span>
        </div>
      </div>

      <div class="grid grid-cols-5 gap-3.5" style="grid-gap: 14px; -webkit-column-gap: 14px;">
        <MatrixButton 
          title="保持原厂不动"
          subtitle="走原车音源切换"
          :active="getGestureAction('mode', activeGesture.mode) === 'default'"
          @click="setGestureAction('mode', activeGesture.mode, 'default')"
        />
        <MatrixButton 
          title="打开 360 全景"
          subtitle="一键秒开车身环视"
          :active="getGestureAction('mode', activeGesture.mode) === 'open_360'"
          @click="setGestureAction('mode', activeGesture.mode, 'open_360')"
        />
        <MatrixButton 
          title="打开高德地图"
          subtitle="一键秒切车载导航"
          :active="getGestureAction('mode', activeGesture.mode) === 'open_navi'"
          @click="setGestureAction('mode', activeGesture.mode, 'open_navi')"
        />
        <MatrixButton 
          title="音量暂停 / 播放"
          subtitle="媒体暂停或继续播放"
          :active="getGestureAction('mode', activeGesture.mode) === 'play_pause'"
          @click="setGestureAction('mode', activeGesture.mode, 'play_pause')"
        />
        <MatrixButton 
          :title="isCustomApp(getGestureAction('mode', activeGesture.mode)) ? (getCustomAppName('mode_' + activeGesture.mode) || '自定义应用') : '自定义打开应用'"
          :subtitle="isCustomApp(getGestureAction('mode', activeGesture.mode)) ? '点击可重新更换应用' : '自由挑选车机第三方应用'"
          :active="isCustomApp(getGestureAction('mode', activeGesture.mode))"
          @click="openAppSelectModal('mode_' + activeGesture.mode)"
        />
      </div>
    </FeatureCard>

    <!-- 9. 新手按键能力指引与一键方案配置 -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-6 shadow-2xl mb-5">
      <div class="flex items-center justify-between pb-4 mb-4 border-b border-car-border/60">
        <div class="flex items-center space-x-3">
          <span class="w-3.5 h-3.5 rounded-full bg-car-accent shadow-[0_0_8px_var(--accent-gold)]"></span>
          <span class="text-[20px] font-black text-car-text">新手功能指引 & 常用方案一键配置</span>
        </div>
        <div class="flex items-center space-x-3">
          <button
            @click="applyRecommendedPreset"
            class="h-[52px] px-6 rounded-2xl border-2 border-car-accent bg-car-item text-car-text font-black text-[16px] cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 shadow-md transition-all flex items-center whitespace-nowrap"
          >
            ✨ 一键应用车友黄金方案
          </button>
          <button
            @click="resetAllToFactory"
            class="h-[52px] px-6 rounded-2xl border-2 border-car-border bg-car-item text-car-sub hover:text-car-text font-black text-[16px] cursor-pointer hover:border-car-border-light shadow-sm transition-all flex items-center whitespace-nowrap"
          >
            🔄 一键恢复全车原厂默认
          </button>
        </div>
      </div>

      <!-- 功能介绍与玩法说明 -->
      <div class="grid grid-cols-3 gap-3.5 text-[14px]">
        <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col justify-between shadow-sm">
          <div>
            <div class="font-black text-car-text text-[16px] mb-1.5 flex items-center">
              <span class="text-car-accent mr-2">●</span> 环视 360 全景盲区
            </div>
            <div class="text-car-sub font-bold text-[13.5px] leading-relaxed">
              将原厂闲置的右侧 MODE 键映射为 360 全景，行车路遇窄路盲区随时方向盘盲操秒开，无需低头在中控大屏上找按键。
            </div>
          </div>
          <div class="mt-3 pt-2.5 border-t border-car-border/50 text-[12.5px] text-car-accent font-black">
            推荐键位：右方向盘 ⑥ MODE 键
          </div>
        </div>

        <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col justify-between shadow-sm">
          <div>
            <div class="font-black text-car-text text-[16px] mb-1.5 flex items-center">
              <span class="text-car-accent mr-2">●</span> 高德 ↔ 桌面双向往返
            </div>
            <div class="text-car-sub font-bold text-[13.5px] leading-relaxed">
              将左方向盘 ⑦ 号自定义键映射为往返高德：按第 1 下切出导航，按第 2 下最小化退回桌面，两端无感双向盲操切换。
            </div>
          </div>
          <div class="mt-3 pt-2.5 border-t border-car-border/50 text-[12.5px] text-car-accent font-black">
            推荐键位：左方向盘 ⑦ 自定义键
          </div>
        </div>

        <div class="p-4 rounded-2xl bg-car-item border border-car-border flex flex-col justify-between shadow-sm">
          <div>
            <div class="font-black text-car-text text-[16px] mb-1.5 flex items-center">
              <span class="text-car-accent mr-2">●</span> 音量暂停 / 息屏休眠
            </div>
            <div class="text-car-sub font-bold text-[13.5px] leading-relaxed">
              支持将按键映射为媒体播放暂停或屏幕一键休眠，夜间行车防眩目或接打电话时，方向盘轻点一下即可物理静音关屏。
            </div>
          </div>
          <div class="mt-3 pt-2.5 border-t border-car-border/50 text-[12.5px] text-car-accent font-black">
            推荐键位：右方向盘 ③ 静音键 / ① 返回键
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue';
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';
import { store, bridge, showToast, openModal } from '../store';
import wheelGuideImg from '../assets/steering_wheel_guide.webp';
import wheelLeftGuideImg from '../assets/steering_wheel_left_guide.webp';

// 原厂方控图解：默认折叠，并记忆上次的展开/折叠状态与左右侧视图
const DIAGRAM_KEY = 'wheel_diagram_expanded';
const showDiagram = ref(localStorage.getItem(DIAGRAM_KEY) === 'true');
const activeCluster = ref(localStorage.getItem('wheel_active_cluster') || 'right');

function toggleDiagram() {
  showDiagram.value = !showDiagram.value;
  localStorage.setItem(DIAGRAM_KEY, String(showDiagram.value));
}

function setCluster(side) {
  activeCluster.value = side;
  localStorage.setItem('wheel_active_cluster', side);
}
const longPressSec = computed({
  get: () => {
    const ms = store.vehicleAuto.wheel_long_press_ms || 1500;
    return parseFloat((ms / 1000).toFixed(1));
  },
  set: (val) => {
    const ms = Math.round(val * 1000);
    store.vehicleAuto.wheel_long_press_ms = ms;
    bridge.call('setVehicleAutomationIntSetting', 'wheel_long_press_ms', ms);
  }
});

function updateLongPressSec() {
  const ms = Math.round(longPressSec.value * 1000);
  store.vehicleAuto.wheel_long_press_ms = ms;
  bridge.call('setVehicleAutomationIntSetting', 'wheel_long_press_ms', ms);
}

function setLongPressPreset(sec) {
  longPressSec.value = sec;
  showToast(`长按判定时长已设定为: ${sec} 秒`);
}

const gestureList = computed(() => [
  { id: 'single', name: '单击', shortName: '单击' },
  { id: 'double', name: '双击', shortName: '双击' },
  { id: 'long', name: `长按 (${longPressSec.value}s)`, shortName: `长按${longPressSec.value}s` }
]);

const activeGesture = reactive({
  back: 'single',
  ok: 'single',
  mute: 'single',
  next: 'single',
  custom: 'single',
  mode: 'single',
  prev: 'single'
});

function getGestureAction(key, gesture) {
  const pKey = `wheel_action_${key}_${gesture}`;
  if (store.vehicleAuto[pKey]) return store.vehicleAuto[pKey];
  if (gesture === 'single') {
    if (store.vehicleAuto[`wheel_action_${key}`]) return store.vehicleAuto[`wheel_action_${key}`];
    if (key === 'mode') return 'open_360';
    if (key === 'custom') return 'open_navi';
    if (key === 'ok') return 'play_pause';
    if (key === 'next') return 'next_track';
    if (key === 'prev') return 'prev_track';
  }
  return 'default';
}

function getActionName(act) {
  const map = {
    default: '原厂默认',
    open_360: '360全景',
    open_navi: '高德地图',
    play_pause: '播放/暂停',
    next_track: '下一曲',
    prev_track: '上一曲',
    mute_toggle: '静音切换',
    screen_off: '息屏休眠'
  };
  if (isCustomApp(act)) return '自定义App';
  return map[act] || act;
}

function setGestureAction(key, gesture, act) {
  const pKey = `wheel_action_${key}_${gesture}`;
  store.vehicleAuto[pKey] = act;
  if (gesture === 'single') {
    store.vehicleAuto[`wheel_action_${key}`] = act;
  }
  bridge.call('setWheelGestureAction', key, gesture, act);
  showToast(`已映射 [${gesture === 'single' ? '单击' : (gesture === 'double' ? '双击' : '长按')}] -> ${getActionName(act)}`);
}

function isCustomApp(actionVal) {
  return typeof actionVal === 'string' && actionVal.startsWith('app:');
}

function getCustomAppName(target) {
  return localStorage.getItem(`wheel_action_${target}_app_name`) || '';
}

function openAppSelectModal(target) {
  openModal('appSelect', { keyTarget: target });
}

function toggleWheelMasterSwitch() {
  const next = !store.vehicleAuto.wheel_master_switch;
  store.vehicleAuto.wheel_master_switch = next;
  bridge.call('setVehicleAutomationSetting', 'wheel_master_switch', next);
  showToast(next ? '方控接管总开关: 已开启 (接管分发)' : '方控接管总开关: 已关闭 (彻底放行)');
}

function setWheelMode(mode) {
  store.vehicleAuto.wheel_control_mode = mode;
  bridge.call('setWheelControlStringSetting', 'wheel_control_mode', mode);
  showToast('方控模式已切换: ' + (mode === 'carmedia_first' ? '米小江优先' : (mode === 'toolbox_alone' ? '控制台独立接管' : '恢复原厂')));
}

function toggleClusterPlayback() {
  const next = !store.vehicleAuto.wheel_push_playback_cluster;
  store.vehicleAuto.wheel_push_playback_cluster = next;
  bridge.call('setVehicleAutomationSetting', 'wheel_push_playback_cluster', next);
  showToast(next ? '已开启仪表/屏保播放状态投递' : '已关闭投递，保持仪表盘与屏保纯净');
}

function toggleClusterLyrics() {
  const next = !store.vehicleAuto.wheel_push_lyrics_cluster;
  store.vehicleAuto.wheel_push_lyrics_cluster = next;
  bridge.call('setVehicleAutomationSetting', 'wheel_push_lyrics_cluster', next);
  showToast(next ? '已开启仪表盘歌词投递' : '已关闭歌词投递，保持仪表盘纯净');
}

function applyRecommendedPreset() {
  openModal('confirm', {
    title: '一键应用车友黄金推荐方案',
    desc: '即将应用最受车友喜爱的成熟按键配置：\n• 右方向盘 ① 返回键 ➔ 保持原厂返回\n• 右方向盘 ② 滚轮按压 ➔ 播放 / 暂停多媒体\n• 右方向盘 ③ 静音键 ➔ 保持原厂静音\n• 右方向盘 ④/⑦ 切歌键 ➔ 官方调度切歌\n• 右方向盘 ⑤ 自定义键 ➔ 高德 ↔ 桌面双向往返\n• 右方向盘 ⑥ MODE 键 ➔ 单击秒开 360 全景',
    confirmText: '确认应用方案',
    onConfirm: () => {
      setGestureAction('back', 'single', 'default');
      setGestureAction('ok', 'single', 'play_pause');
      setGestureAction('mute', 'single', 'default');
      setGestureAction('next', 'single', 'next_track');
      setGestureAction('prev', 'single', 'prev_track');
      setGestureAction('custom', 'single', 'open_navi');
      setGestureAction('mode', 'single', 'open_360');
      showToast('已成功应用车友黄金方案！');
    }
  });
}

function resetAllToFactory() {
  openModal('confirm', {
    title: '一键恢复全车原厂默认',
    desc: '将方向盘所有按键手势全部恢复为系统原厂默认动作，工具箱将不拦截任何物理按键。是否确认？',
    isDanger: true,
    confirmText: '确认恢复原厂',
    onConfirm: () => {
      const keys = ['ok', 'mute', 'mode', 'next', 'prev', 'back', 'call', 'voice', 'home', 'custom'];
      const gestures = ['single', 'double', 'long'];
      for (const k of keys) {
        for (const g of gestures) {
          setGestureAction(k, g, 'default');
        }
      }
      showToast('所有方向盘按键已恢复原厂默认直通！');
    }
  });
}
</script>
