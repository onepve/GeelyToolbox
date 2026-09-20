<template>
  <div class="flex flex-col space-y-6">
    <!-- 核心：方向盘按键方控接管总开关 - 提权置顶首屏 (左右分栏车规黄金磁贴) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-5 min-h-[106px] shadow-xl flex items-center justify-between transition-all">
      <div class="w-[60%] max-w-[60%] flex flex-col space-y-1.5 shrink-0">
        <div class="flex items-center space-x-3">
          <StatusDot size="lg" :color="store.vehicleAuto.wheel_master_switch ? 'ok' : 'off'" :glow-px="10" class="shadow-md" />
          <span class="text-[21px] font-black text-car-text tracking-wide whitespace-nowrap">方向盘按键方控接管总开关</span>
          <span class="px-3 py-0.5 text-[13px] font-black rounded-full border bg-car-item border-car-border text-car-text inline-flex items-center shrink-0 shadow-sm">
            <StatusDot class="mr-2" size="sm" :color="store.vehicleAuto.wheel_master_switch ? 'ok' : 'off'" />
            {{ store.vehicleAuto.wheel_master_switch ? '方控接管已启用' : '方控已彻底放行 (不干涉)' }}
          </span>
        </div>
        <div class="text-[14.5px] text-car-sub font-bold leading-normal">
          全面接管静音键、Mode键与上一曲/下一曲。支持单击、双击、长按三种手势。
        </div>
      </div>

      <div class="shrink-0 w-[230px]">
        <BaseButton
          variant="master"
          :active="store.vehicleAuto.wheel_master_switch"
          @click="toggleWheelMasterSwitch"
        >
          <span class="text-[18.5px] font-black text-car-text tracking-wide whitespace-nowrap">
            {{ store.vehicleAuto.wheel_master_switch ? '方控接管已开启' : '方控接管已关闭' }}
          </span>
          <span :class="['text-[12.5px] font-bold mt-1 whitespace-nowrap', store.vehicleAuto.wheel_master_switch ? 'text-car-accent' : 'text-car-sub']">
            {{ store.vehicleAuto.wheel_master_switch ? '点击切换为彻底放行' : '点击开启按键接管' }}
          </span>
        </BaseButton>
      </div>
    </div>

    <!-- 车型图解与协议指示区 (缤越 COOL / SX-0017 原厂按键分布) -->
    <div class="bg-car-item border border-car-border rounded-2xl p-5 shadow-sm">
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center">
          <StatusDot class="mr-3" size="md" color="accent" :glow-px="8" />
          <div class="flex flex-col">
            <span class="text-[18px] font-black text-car-text">吉利缤越 COOL (IHU516G / E02) 原厂方控图解</span>
            <span class="text-[14px] text-car-sub font-bold mt-0.5">
              右方向盘多媒体控制区 (SX-0017)：静音、切歌、MODE、返回键全量打通多手势
            </span>
          </div>
        </div>
        <div class="flex items-center space-x-3">
          <BaseButton variant="chipLg" :active="floatingEnabled" @click="toggleFloating()">
            <StatusDot class="mr-2.5" size="sm" :color="floatingEnabled ? 'ok' : 'off'" />
            {{ floatingEnabled ? '悬浮图解：开' : '悬浮图解：关' }}
          </BaseButton>
          <BaseButton variant="ghost" @click="toggleDiagram()">
            {{ showDiagram ? '收起图解' : '展开图解' }}
          </BaseButton>
        </div>
      </div>

      <!-- 图解内容展示区 -->
      <div v-if="showDiagram" class="mt-4 pt-4 border-t border-car-border/60 flex items-start space-x-6">
        <!-- 原厂方向盘按键高清图示 (沉稳车规底座，绝非刺眼白底) -->
        <div class="p-3 bg-car-item rounded-2xl shadow-md border-2 border-car-border w-[320px] min-w-[320px] max-w-[320px] shrink-0">
          <img 
            :src="wheelGuideImg" 
            alt="吉利缤越 COOL 右侧多媒体 SX-0017" 
            class="guide-img w-full h-auto rounded-xl object-contain block" 
          />
        </div>

        <!-- 按键编号与原厂定义清单 -->
        <div class="flex-1 min-w-0 flex flex-col space-y-2.5 text-[14.5px] font-bold text-car-sub">
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
              <span class="text-car-accent font-black mr-1.5">⑤ 自定义键:</span> 0x37 硬件按键 / 默认原厂 (支持多手势)
            </div>
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">⑥ MODE键:</span> 音源切换 / 自定义 (支持多手势)
            </div>
          </div>
          <div class="w-full p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
            <span class="text-car-accent font-black mr-1.5">⑦ 向左选择键:</span> 上一个文件 / 上一曲 (支持多手势)
          </div>
        </div>
      </div>
    </div>

    <!-- 1. 方向盘方控接管模式 -->
    <FeatureCard 
      title="方向盘方控接管模式 (兼容米小江)"
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
          <BaseButton variant="pill" :active="store.vehicleAuto.wheel_push_playback_cluster" @click="toggleClusterPlayback">
            {{ store.vehicleAuto.wheel_push_playback_cluster ? '已开启 (投递状态)' : '已关闭 (默认纯净)' }}
          </BaseButton>
        </div>

        <div class="flex items-center justify-between p-4 rounded-2xl bg-car-item border-2 border-car-border">
          <div class="flex flex-col space-y-1">
            <span class="text-[17px] font-black text-car-text">投递当前歌词至原车仪表盘 (HUD)</span>
            <span class="text-[14px] text-car-sub font-bold">开启后向吉利全液晶仪表盘中间卡片推送实时滚动歌词。默认关闭防信息刷屏。</span>
          </div>
          <BaseButton variant="pill" :active="store.vehicleAuto.wheel_push_lyrics_cluster" @click="toggleClusterLyrics">
            {{ store.vehicleAuto.wheel_push_lyrics_cluster ? '已开启 (投递歌词)' : '已关闭 (默认纯净)' }}
          </BaseButton>
        </div>
      </div>
    </FeatureCard>

    <!-- 3. 方控按键长按判定时长 (自定义秒数) - 仅 Beta 测试通道展示 -->
    <FeatureCard
      v-if="isBeta"
      title="方控按键长按判定触发时长 (自定义秒数)"
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
          <BaseButton
            v-for="preset in [1.0, 1.5, 2.0, 3.0, 5.0]"
            :key="preset"
            variant="miniChip"
            :active="longPressSec === preset"
            @click="setLongPressPreset(preset)"
          >
            {{ preset }}s
          </BaseButton>
        </div>
      </div>
    </FeatureCard>

    <!-- 3/4/5. 按键功能卡一排两个：节省纵向空间，卡片与字号尺寸全部原样保留 -->
    <div class="grid grid-cols-2 items-start gap-3.5">
      <!-- 3. 右方向盘 ① 主页/返回按键多手势映射 -->
      <WheelGestureCard
        class="!mb-0"
        key-name="back"
        card-title="右方向盘 ① 主页/返回按键映射"
        card-desc="对应右方向盘 ① 号返回/主页键（KeyCode 307）。支持【单击】、【双击】、【长按】自定义。可保留系统原厂返回，或映射为高德导航、360全景或自定义应用。"
      />

      <!-- 4. 右方向盘 ② 音量滚轮按压多手势映射 -->
      <WheelGestureCard
        class="!mb-0"
        key-name="ok"
        card-title="右方向盘 ② 音量滚轮按压映射"
        card-desc="对应右方向盘 ② 号音量滚轮垂直按压（Tasker 黄金键码 0x2d）。支持【单击】、【双击】、【长按】多手势；默认单击控制媒体暂停/播放，亦可秒开 360 或高德。"
      />

      <!-- 5. 右方向盘 ③ 静音键多手势映射 -->
      <WheelGestureCard
        class="!mb-0"
        key-name="mute"
        card-title="右方向盘 ③ 静音按键映射"
        card-desc="对应右方向盘 ③ 号静音键（KeyCode 300）。支持【单击】、【双击】、【长按 1.5 秒】多手势；长按 10 秒依然是整车硬件冷重启，互不冲突！"
      />

      <!-- 6. 右方向盘 ④ 下一曲 / ⑦ 上一曲 切歌按键映射 -->
      <FeatureCard class="!mb-0"
        title="右方向盘 ④ 下一曲 / ⑦ 上一曲 切歌按键映射"
        desc="对应右方向盘 ④ 号（向右下一曲 KeyCode 305）与 ⑦ 号（向左上一曲 KeyCode 304）按键。内置官方三重通道调度，完美兼容 QQ音乐车机版、网易云等。"
        helpTitle="【功能指南】下一曲 / 上一曲切歌按键映射"
        helpText="1. 键位对应：&#10;④ 号键（向右）控制下一曲，⑦ 号键（向左）控制上一曲，均支持单击、双击、长按三种手势自由映射。&#10;&#10;2. 官方三重通道调度：&#10;内置官方切歌三重通道调度机制，完美兼容 QQ音乐车机版、网易云音乐等主流车载音乐应用，切歌稳定不串台。&#10;&#10;3. 动作映射：&#10;可将手势映射为「切歌(官方调度)」「打开 360 全景」或「播放/暂停」，满足不同驾驶场景需求。"
        helpTip="切歌动作建议保持「切歌(官方调度)」，兼容性与稳定性最佳。"
      >
        <div class="grid grid-cols-2 gap-3.5">
          <!-- 上一曲 (⑦ 号键)：与原车「向左」物理方位一致，并排置于左侧 -->
          <div class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col shadow-sm">
            <div class="flex items-center justify-between mb-3">
              <span class="text-[19px] font-black text-car-text whitespace-nowrap">⑦ 上一曲</span>
              <div class="flex flex-wrap space-x-1.5">
                <BaseButton
                  v-for="g in gestureList"
                  :key="g.id"
                  variant="gestureChip"
                  :active="activeGesture.prev === g.id"
                  @click="activeGesture.prev = g.id"
                >
                  {{ g.shortName }}
                </BaseButton>
              </div>
            </div>
            <ActionSelect key-name="prev" :gesture="activeGesture.prev" />
          </div>

          <!-- 下一曲 (④ 号键)：与原车「向右」物理方位一致，并排置于右侧 -->
          <div class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-col shadow-sm">
            <div class="flex items-center justify-between mb-3">
              <span class="text-[19px] font-black text-car-text whitespace-nowrap">④ 下一曲</span>
              <div class="flex flex-wrap space-x-1.5">
                <BaseButton
                  v-for="g in gestureList"
                  :key="g.id"
                  variant="gestureChip"
                  :active="activeGesture.next === g.id"
                  @click="activeGesture.next = g.id"
                >
                  {{ g.shortName }}
                </BaseButton>
              </div>
            </div>
            <ActionSelect key-name="next" :gesture="activeGesture.next" />
          </div>
        </div>
      </FeatureCard>
    </div>

    <!-- 7/8. 按键功能卡一排两个：节省纵向空间，卡片与字号尺寸全部原样保留 -->
    <div class="grid grid-cols-2 items-start gap-3.5">
      <!-- 7. 右方向盘 ⑤ 自定义按键多手势映射 (原厂模式 / 高德 / 360 / 自定义) -->
      <WheelGestureCard
        class="!mb-0"
        key-name="custom"
        card-title="右方向盘 ⑤ 自定义按键映射"
        card-desc="对应右方向盘 ⑤ 号自定义按键（Tasker 黄金键码 0x37）。中高配车型原车中控已具备自定义设置，默认保持原厂模式直通；亦支持多手势选配高德、360或自定义App。"
      />

      <!-- 8. 右方向盘 ⑥ MODE 键多手势映射 -->
      <WheelGestureCard
        class="!mb-0"
        key-name="mode"
        card-title="右方向盘 ⑥ MODE 按键映射"
        card-desc="对应右方向盘 ⑥ 号 MODE 模式键（KeyCode 348）。原车用于切换伴听/收音机。默认单击一键秒开 360 全景，同时支持双击与长按个性化定制。"
      />
    </div>

    <!-- 9. 新手按键能力指引与一键方案配置 -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-6 shadow-2xl mb-5">
      <div class="flex items-center justify-between pb-4 mb-4 border-b border-car-border/60">
        <div class="flex items-center space-x-3">
          <StatusDot size="lg" color="accent" :glow-px="8" />
          <span class="text-[20px] font-black text-car-text">新手功能指引 & 常用方案一键配置</span>
        </div>
        <div class="flex items-center space-x-3">
          <BaseButton variant="cta" @click="applyRecommendedPreset">
            一键应用车友黄金方案
          </BaseButton>
          <BaseButton variant="ghostSoft" @click="resetAllToFactory">
            一键恢复全车原厂默认
          </BaseButton>
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

    <!-- 方向盘图解悬浮对照卡：滚动设键位时默认悬浮于内容区右上角（绝不遮挡下方功能菜单）；
         支持拖放自定义位置并本地记忆，下次同位显示；原地点按隐藏 -->
    <transition name="floating-fade">
      <div
        v-if="showFloating"
        ref="floatingGuideEl"
        @pointerdown="startFloatingDrag"
        class="fixed z-30 cursor-move select-none"
        :style="{ left: floatingPos.x + 'px', top: floatingPos.y + 'px', width: '168px', touchAction: 'none' }"
        title="方向盘图解对照：拖动到任意位置，点一下隐藏"
      >
        <img
          :src="wheelGuideFloatingImg"
          alt="方控图解悬浮对照"
          class="w-full h-auto object-contain block pointer-events-none select-none"
        />
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue';
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';
import BaseButton from '../components/BaseButton.vue';
import StatusDot from '../components/StatusDot.vue';
import WheelGestureCard from '../components/WheelGestureCard.vue';
import ActionSelect from '../components/ActionSelect.vue';
import { store, bridge, showToast, openModal } from '../store';
import { useWheelGesture } from '../composables/useWheelGesture';
import wheelGuideImg from '../assets/steering_wheel_guide.webp';
import wheelGuideFloatingImg from '../assets/steering_wheel_guide_floating.svg';

// 原厂方控图解：默认折叠，并记忆展开状态
const DIAGRAM_KEY = 'wheel_diagram_expanded';
const showDiagram = ref(localStorage.getItem(DIAGRAM_KEY) === 'true');

function toggleDiagram() {
  showDiagram.value = !showDiagram.value;
  localStorage.setItem(DIAGRAM_KEY, String(showDiagram.value));
}

// 方向盘图解悬浮对照卡：下滑设键位时悬浮显示，回顶自动收起；点按临时隐藏。
// 默认出现在内容区右上角（绝不遮挡自下而上展开的功能菜单），支持拖放自定义位置并本地记忆，下次同位显示
const FLOAT_POS_KEY = 'wheel_floating_guide_pos';
const showFloating = ref(false);

// 悬浮图解总开关：展卡内显式控制悬浮图显示/不显示，持久记忆（默认开，保留老用户既有体验）
const FLOAT_TOGGLE_KEY = 'wheel_floating_guide_enabled';
const floatingEnabled = ref(localStorage.getItem(FLOAT_TOGGLE_KEY) !== 'false');

function toggleFloating() {
  floatingEnabled.value = !floatingEnabled.value;
  localStorage.setItem(FLOAT_TOGGLE_KEY, String(floatingEnabled.value));
  if (!floatingEnabled.value) {
    showFloating.value = false;
  } else {
    const st = document.documentElement.scrollTop || document.body.scrollTop || 0;
    showFloating.value = st > 40;
  }
}

const floatingGuideEl = ref(null);
const floatingPos = reactive(loadFloatingPos());

function loadFloatingPos() {
  try {
    const saved = JSON.parse(localStorage.getItem(FLOAT_POS_KEY));
    if (saved && Number.isFinite(saved.x) && Number.isFinite(saved.y)) return saved;
  } catch (e) {}
  // 默认初始值：内容区右上角（右侧留白 38px：卡片边距 28px + 呼吸间隙 10px），顶部 88px
  return { x: window.innerWidth - 168 - 38, y: 88 };
}

function onWheelScroll(e) {
  // document 级 capture 捕获任意滚动源，读取实际滚动元素的 scrollTop
  const el = e && e.target;
  const st = el && el.scrollTop ? el.scrollTop : (document.documentElement.scrollTop || document.body.scrollTop || 0);
  // 回到顶端收起悬浮卡；下滑则浮现（开关开启时，隐藏后再滚动即重新浮现于记忆位置）
  showFloating.value = floatingEnabled.value && st > 40;
}

// 拖放自定义位置：pointer 事件触摸/鼠标通吃；拖动即位移，松手记忆位置；原地松手（未拖动）视为点按隐藏
function startFloatingDrag(e) {
  if (!e || e.button === 2) return;
  const card = floatingGuideEl.value;
  if (!card) return;
  const w = card.offsetWidth || 168;
  const h = card.offsetHeight || 120;
  const startX = e.clientX;
  const startY = e.clientY;
  const origX = floatingPos.x;
  const origY = floatingPos.y;
  let moved = false;

  // 位置钳制：绝不拖出屏幕可视区
  const clamp = (x, y) => ({
    x: Math.min(Math.max(8, x), window.innerWidth - w - 8),
    y: Math.min(Math.max(8, y), window.innerHeight - h - 8)
  });

  const onMove = (ev) => {
    if (Math.abs(ev.clientX - startX) + Math.abs(ev.clientY - startY) > 6) moved = true;
    const next = clamp(origX + ev.clientX - startX, origY + ev.clientY - startY);
    floatingPos.x = next.x;
    floatingPos.y = next.y;
  };
  const onUp = () => {
    document.removeEventListener('pointermove', onMove);
    document.removeEventListener('pointerup', onUp);
    if (moved) {
      // 拖放结束：记忆位置，下次滚动浮现时仍在同位显示
      localStorage.setItem(FLOAT_POS_KEY, JSON.stringify({ x: floatingPos.x, y: floatingPos.y }));
    } else {
      showFloating.value = false; // 原地点按：临时隐藏
    }
  };
  document.addEventListener('pointermove', onMove);
  document.addEventListener('pointerup', onUp);
}

onMounted(() => {
  // 视口兜底：记忆位置或默认值超界（如分辨率/朝向变化）时拉回可视区内
  floatingPos.x = Math.min(Math.max(8, floatingPos.x), Math.max(8, window.innerWidth - 184));
  floatingPos.y = Math.min(Math.max(8, floatingPos.y), Math.max(8, window.innerHeight - 140));
  document.addEventListener('scroll', onWheelScroll, { passive: true, capture: true });
});

onBeforeUnmount(() => {
  document.removeEventListener('scroll', onWheelScroll, { capture: true });
});
const isBeta = computed(() => {
  // 与安装包版本号脱钩：仅由连点 11 次激活的内测特权决定显示/隐藏
  const isTesterUnlocked = localStorage.getItem('geely_tester_unlocked') === 'true';
  const useBetaChannel = localStorage.getItem('geely_use_beta_channel') === 'true';
  return isTesterUnlocked || useBetaChannel;
});

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

const { gestureList, getActionName, getGestureAction, setGestureAction } = useWheelGesture();

const activeGesture = reactive({
  next: 'single',
  prev: 'single'
});







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
      showToast('已成功应用车友黄金方案！', 'success');
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

<style scoped>
/* 方向盘图解统一调暗：降一档亮度，柔和进车规深色主题，兼防白色编号圈在夜间刺眼 */
.guide-img {
  filter: brightness(0.86) contrast(1.02);
}

.floating-fade-enter-active,
.floating-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.floating-fade-enter-from,
.floating-fade-leave-to {
  opacity: 0;
  transform: translateY(12px);
}
</style>
