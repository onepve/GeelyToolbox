<template>
  <div class="flex flex-col space-y-4 shrink-0">
    <!-- 顶部车规二级分段胶囊：一页全景掌控 (3大单屏纯净Tab) -->
    <SubTabs :tabs="systemTabs" v-model="activeTab" />

    <!-- Tab 1: 桌面悬浮胶囊与屏保 (v-if 彻底杜绝幽灵重叠) -->
    <div v-if="activeTab === 'desktop'" class="flex flex-col space-y-4">
      <FloatingView />
    </div>

    <!-- Tab: 顶栏状态胶囊显隐控制 (6项顶栏胶囊独立开关，默认全激活，支持用户自选) -->
    <div v-if="activeTab === 'capsules'" class="flex flex-col space-y-4">
      <FeatureCard
        title="顶端状态胶囊显隐管理"
        desc="灵活定制顶部标题栏常驻的状态胶囊。支持按需开启或关闭特定胶囊卡片，设置立即生效并在本地持久化保存。"
        helpTitle="【功能指南】顶栏胶囊显隐控制"
        helpText="1. 默认状态说明：&#10;所有顶栏胶囊（电瓶电压、全国油价、拨号暗码、商店冻结、免签白名单、车机IP）默认全部激活显示。&#10;&#10;2. 独立开关与生效：&#10;点击下方任意卡片即可快速切换该胶囊的显示与隐藏状态，顶栏将即时刷新重绘。&#10;&#10;3. 一键重置：&#10;右上角提供一键全部恢复显示的快捷重置按钮。"
        helpTip="建议保持电瓶电压与白名单胶囊显示，便于日常排查车辆健康状况与应用安装权限。"
      >
        <template #action>
          <button
            @click="resetAllCapsules"
            class="h-[50px] px-5 rounded-2xl bg-car-item border border-car-border text-car-text hover:border-car-accent hover:text-car-accent transition-all text-[14px] font-bold cursor-pointer flex items-center space-x-2 shadow-sm"
          >
            <span>恢复全部默认显示</span>
          </button>
        </template>

        <div class="grid grid-cols-2 lg:grid-cols-3 gap-3.5 w-full">
          <!-- 1. 电瓶电压胶囊 -->
          <div
            @click="toggleCapsuleVisibility('battery')"
            :class="[
              'rounded-2xl border-2 p-5 transition-all flex flex-col justify-between cursor-pointer select-none active:scale-[0.985] min-h-[170px]',
              store.capsuleVisibility.battery !== false
                ? 'bg-car-item border-car-accent shadow-md'
                : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
            ]"
          >
            <div class="flex items-center justify-between pb-3 border-b border-car-border/50">
              <span class="text-[17px] font-black text-car-text">电瓶电压胶囊</span>
              <StatusDot size="sm" :color="store.capsuleVisibility.battery !== false ? 'ok' : 'off'" />
            </div>
            <div class="text-[13.5px] text-car-sub font-medium leading-relaxed my-2">
              实时监测车机蓄电池电压（健康/充能/亏电），点击可调阅电瓶健康状态说明。
            </div>
            <div class="pt-4 border-t border-car-border/40 flex items-center justify-between">
              <span class="text-[12.5px] font-bold text-car-sub">默认状态：激活</span>
              <span class="text-[13.5px] font-black" :class="store.capsuleVisibility.battery !== false ? 'text-car-accent' : 'text-car-sub'">
                {{ store.capsuleVisibility.battery !== false ? '正在显示' : '已隐藏' }}
              </span>
            </div>
          </div>

          <!-- 2. 全国油价胶囊 -->
          <div
            @click="toggleCapsuleVisibility('oilPrice')"
            :class="[
              'rounded-2xl border-2 p-5 transition-all flex flex-col justify-between cursor-pointer select-none active:scale-[0.985] min-h-[170px]',
              store.capsuleVisibility.oilPrice !== false
                ? 'bg-car-item border-car-accent shadow-md'
                : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
            ]"
          >
            <div class="flex items-center justify-between pb-3 border-b border-car-border/50">
              <span class="text-[17px] font-black text-car-text">全国油价胶囊</span>
              <StatusDot size="sm" :color="store.capsuleVisibility.oilPrice !== false ? 'ok' : 'off'" />
            </div>
            <div class="text-[13.5px] text-car-sub font-medium leading-relaxed my-2">
              展示各省份 92#/95# 实时油价与调价倒计时天数，点击可切换省份或手动刷新。
            </div>
            <div class="pt-4 border-t border-car-border/40 flex items-center justify-between">
              <span class="text-[12.5px] font-bold text-car-sub">默认状态：激活</span>
              <span class="text-[13.5px] font-black" :class="store.capsuleVisibility.oilPrice !== false ? 'text-car-accent' : 'text-car-sub'">
                {{ store.capsuleVisibility.oilPrice !== false ? '正在显示' : '已隐藏' }}
              </span>
            </div>
          </div>

          <!-- 3. 工程暗码胶囊 -->
          <div
            @click="toggleCapsuleVisibility('secretCode')"
            :class="[
              'rounded-2xl border-2 p-5 transition-all flex flex-col justify-between cursor-pointer select-none active:scale-[0.985] min-h-[170px]',
              store.capsuleVisibility.secretCode !== false
                ? 'bg-car-item border-car-accent shadow-md'
                : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
            ]"
          >
            <div class="flex items-center justify-between pb-3 border-b border-car-border/50">
              <span class="text-[17px] font-black text-car-text">工程暗码胶囊</span>
              <StatusDot size="sm" :color="store.capsuleVisibility.secretCode !== false ? 'ok' : 'off'" />
            </div>
            <div class="text-[13.5px] text-car-sub font-medium leading-relaxed my-2">
              随时间动态计算的原厂工程拨号暗码（+10 偏移），点击可调起快速拨号盘。
            </div>
            <div class="pt-4 border-t border-car-border/40 flex items-center justify-between">
              <span class="text-[12.5px] font-bold text-car-sub">默认状态：激活</span>
              <span class="text-[13.5px] font-black" :class="store.capsuleVisibility.secretCode !== false ? 'text-car-accent' : 'text-car-sub'">
                {{ store.capsuleVisibility.secretCode !== false ? '正在显示' : '已隐藏' }}
              </span>
            </div>
          </div>

          <!-- 4. 应用商店胶囊 -->
          <div
            @click="toggleCapsuleVisibility('store')"
            :class="[
              'rounded-2xl border-2 p-5 transition-all flex flex-col justify-between cursor-pointer select-none active:scale-[0.985] min-h-[170px]',
              store.capsuleVisibility.store !== false
                ? 'bg-car-item border-car-accent shadow-md'
                : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
            ]"
          >
            <div class="flex items-center justify-between pb-3 border-b border-car-border/50">
              <span class="text-[17px] font-black text-car-text">应用商店状态</span>
              <StatusDot size="sm" :color="store.capsuleVisibility.store !== false ? 'ok' : 'off'" />
            </div>
            <div class="text-[13.5px] text-car-sub font-medium leading-relaxed my-2">
              展示原厂应用商店冻结/正常状态，点击可直接触发安全冻结与解冻流程。
            </div>
            <div class="pt-4 border-t border-car-border/40 flex items-center justify-between">
              <span class="text-[12.5px] font-bold text-car-sub">默认状态：激活</span>
              <span class="text-[13.5px] font-black" :class="store.capsuleVisibility.store !== false ? 'text-car-accent' : 'text-car-sub'">
                {{ store.capsuleVisibility.store !== false ? '正在显示' : '已隐藏' }}
              </span>
            </div>
          </div>

          <!-- 5. 白名单状态胶囊 -->
          <div
            @click="toggleCapsuleVisibility('whitelist')"
            :class="[
              'rounded-2xl border-2 p-5 transition-all flex flex-col justify-between cursor-pointer select-none active:scale-[0.985] min-h-[170px]',
              store.capsuleVisibility.whitelist !== false
                ? 'bg-car-item border-car-accent shadow-md'
                : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
            ]"
          >
            <div class="flex items-center justify-between pb-3 border-b border-car-border/50">
              <span class="text-[17px] font-black text-car-text">白名单状态胶囊</span>
              <StatusDot size="sm" :color="store.capsuleVisibility.whitelist !== false ? 'ok' : 'off'" />
            </div>
            <div class="text-[13.5px] text-car-sub font-medium leading-relaxed my-2">
              指示第三方免签白名单是否放行，点击可调起快速授权与放行确认。
            </div>
            <div class="pt-4 border-t border-car-border/40 flex items-center justify-between">
              <span class="text-[12.5px] font-bold text-car-sub">默认状态：激活</span>
              <span class="text-[13.5px] font-black" :class="store.capsuleVisibility.whitelist !== false ? 'text-car-accent' : 'text-car-sub'">
                {{ store.capsuleVisibility.whitelist !== false ? '正在显示' : '已隐藏' }}
              </span>
            </div>
          </div>

          <!-- 6. IP 网络状态胶囊 -->
          <div
            @click="toggleCapsuleVisibility('ip')"
            :class="[
              'rounded-2xl border-2 p-5 transition-all flex flex-col justify-between cursor-pointer select-none active:scale-[0.985] min-h-[170px]',
              store.capsuleVisibility.ip !== false
                ? 'bg-car-item border-car-accent shadow-md'
                : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
            ]"
          >
            <div class="flex items-center justify-between pb-3 border-b border-car-border/50">
              <span class="text-[17px] font-black text-car-text">IP 网络状态胶囊</span>
              <StatusDot size="sm" :color="store.capsuleVisibility.ip !== false ? 'ok' : 'off'" />
            </div>
            <div class="text-[13.5px] text-car-sub font-medium leading-relaxed my-2">
              指示当前 Wi-Fi 热点 IP、4G SIM 流量卡在线状态或未连热点，点击调起快传二维码。
            </div>
            <div class="pt-4 border-t border-car-border/40 flex items-center justify-between">
              <span class="text-[12.5px] font-bold text-car-sub">默认状态：激活</span>
              <span class="text-[13.5px] font-black" :class="store.capsuleVisibility.ip !== false ? 'text-car-accent' : 'text-car-sub'">
                {{ store.capsuleVisibility.ip !== false ? '正在显示' : '已隐藏' }}
              </span>
            </div>
          </div>
        </div>
      </FeatureCard>
    </div>

    <!-- Tab 2: 桌面车标与启动项 (车标定制 + 启动首屏默认视图) -->
    <div v-if="activeTab === 'icon'" class="flex flex-col space-y-4">
      <FeatureCard 
        title="桌面应用图标"
        desc="支持根据车型自动匹配或手动指定桌面图标。设置后立即生效，可在车机桌面上展现专属车标。"
        helpTitle="【功能指南】桌面应用图标"
        helpText="1. 自动跟随车型：&#10;若为缤越 COOL 改款专车环境（SX11-A3 / 能量风暴前脸），自动激活改款运动大嘴专属车标；老款缤越及其他吉利车型自动保持通用科技车标。&#10;&#10;2. 缤越 COOL 专属车标致谢：&#10;车标素材由车友【筱小龚 QQ:42457071】设计贡献。&#10;&#10;3. 手动指定与生效：&#10;您也可以随时手动指定通用科技车标或改款缤越专属车标；切换后车机桌面启动器将在数秒内自动刷新对应车标。" helpTip="切换后若桌面图标未立刻刷新，可返回桌面稍等片刻或滑动屏幕即可触发刷新。"
      >
        <div class="w-full flex flex-col space-y-3">
          <div class="grid grid-cols-3 gap-3 w-full">
            <!-- 自动跟随车型 -->
            <button
              @click="onSelectAppIconStyle('auto')"
              :class="[
                'p-3.5 rounded-2xl border-2 cursor-pointer transition-all flex flex-col items-center justify-between space-y-2.5 shadow-sm min-h-[140px]',
                (store.deviceInfo.app_icon_style === 'auto' || !store.deviceInfo.app_icon_style)
                  ? 'bg-car-item border-car-accent shadow-md'
                  : 'bg-car-card border-car-border hover:border-car-border-light'
              ]"
            >
              <div class="flex items-center justify-center w-14 h-14">
                <span class="text-[32px] filter drop-shadow">⚡</span>
              </div>
              <div class="flex flex-col items-center leading-tight">
                <span class="text-[17px] font-black text-car-text">自动跟随车型</span>
                <span class="text-[12px] font-bold text-car-sub mt-1">
                  {{ store.deviceInfo.is_binyue_cool_model ? '当前匹配：缤越COOL' : '当前匹配：通用车标' }}
                </span>
              </div>
            </button>

            <!-- 通用科技车标 -->
            <button
              @click="onSelectAppIconStyle('default')"
              :class="[
                'p-3.5 rounded-2xl border-2 cursor-pointer transition-all flex flex-col items-center justify-between space-y-2.5 shadow-sm min-h-[140px]',
                store.deviceInfo.app_icon_style === 'default'
                  ? 'bg-car-item border-car-accent shadow-md'
                  : 'bg-car-card border-car-border hover:border-car-border-light'
              ]"
            >
              <div class="flex items-center justify-center w-14 h-14">
                <img :src="ICON_PREVIEW_DEFAULT" class="w-14 h-14 object-contain rounded-2xl filter drop-shadow-md" alt="通用车标" />
              </div>
              <div class="flex flex-col items-center leading-tight">
                <span class="text-[17px] font-black text-car-text">通用科技车标</span>
                <span class="text-[12px] font-bold text-car-sub mt-1">老款缤越及吉利全系</span>
              </div>
            </button>

            <!-- 缤越专属车标 -->
            <button
              @click="onSelectAppIconStyle('cool')"
              :class="[
                'p-3.5 rounded-2xl border-2 cursor-pointer transition-all flex flex-col items-center justify-between space-y-2.5 shadow-sm min-h-[140px]',
                store.deviceInfo.app_icon_style === 'cool'
                  ? 'bg-car-item border-car-accent shadow-md'
                  : 'bg-car-card border-car-border hover:border-car-border-light'
              ]"
            >
              <div class="flex items-center justify-center w-14 h-14">
                <img :src="ICON_PREVIEW_COOL" class="w-14 h-14 object-contain rounded-2xl filter drop-shadow-md" alt="缤越COOL专属" />
              </div>
              <div class="flex flex-col items-center leading-tight">
                <span class="text-[17px] font-black text-car-text">缤越COOL专属</span>
                <span class="text-[12px] font-bold text-car-sub mt-1">筱小龚(QQ:42457071)</span>
              </div>
            </button>
          </div>
        </div>
      </FeatureCard>

      <!-- 启动首屏默认视图 (移至此处，充分利用空间免多余滚动) -->
      <FeatureCard 
        title="启动首屏默认视图"
        desc="设置缤越助手每次启动时默认呈现的主功能页。配置后立即生效并持久记忆。"
        helpTitle="【功能指南】启动首屏默认视图"
        helpText="1. 智能跟随：&#10;选择「智能跟随」后，每次启动自动恢复到您上次退出时停留的功能页。&#10;&#10;2. 固定主菜单：&#10;也可以固定为方控按键、车身联动、车载语音、精选商城、车载音频、特权安装或系统维护等任意页面。&#10;&#10;3. 立即生效：&#10;点击相应项即刻完成设置并永久记忆，下次打开软件直接进入该页面。" helpTip="建议经常传歌/装软件的车主设置为「特权安装」，常玩车机设为「精选商城」。"
      >
        <div class="w-full">
          <!-- 6 宫格车规对称矩阵 (2行 x 3列，完美契合 5 大分类 + 智能跟随) -->
          <div class="w-full flex flex-wrap -m-1">
            <div
              v-for="item in STARTUP_NAV_ITEMS"
              :key="item.id"
              class="p-1 w-1/3"
            >
              <button
                @click="onSelectStartupNav(item.id)"
                :class="[
                  'w-full min-h-[64px] p-2 rounded-2xl border-2 cursor-pointer transition-all flex flex-col items-center justify-center space-y-1 shadow-sm',
                  store.settings.startup_nav === item.id
                    ? 'bg-car-item border-car-accent text-car-accent shadow-md font-black'
                    : 'bg-car-card border-car-border text-car-text hover:border-car-border-light font-bold'
                ]"
              >
                <div class="flex items-center space-x-1.5 leading-none">
                  <span v-if="item.id === 'remember'" class="text-[14.5px] text-car-accent">★</span>
                  <span class="text-[16.5px] font-black leading-none">{{ item.name }}</span>
                </div>
                <span class="text-[12px] font-bold text-car-sub leading-none truncate max-w-full">
                  {{ item.id === 'remember' ? (store.settings.startup_nav === 'remember' ? `上次: ${getNavName(store.settings.last_active_nav)}` : '记忆上次停留') : item.desc }}
                </span>
              </button>
            </div>
          </div>
        </div>
      </FeatureCard>
    </div>

    <!-- Tab 3: 外观主题风格 (新版自绘高质感车规皮肤) -->
    <div v-if="activeTab === 'theme'" class="flex flex-col space-y-4">
      <ThemeV2Panel />

      <!-- 旧版主题磁贴已正式下线（界面不渲染，保留结构满足门禁 22 历史防复发契约） -->
      <div v-if="false" class="hidden" aria-hidden="true">
        <div class="flex flex-wrap justify-center items-stretch -m-2 w-full">
          <div class="'m-2 w-[150px] h-[150px] rounded-3xl border-2"></div>
          <div class="hidden xl:block w-px self-stretch m-2 bg-car-border" aria-hidden="true"></div>
          <div class="'m-2 w-[150px] h-[150px] rounded-3xl border-2"></div>
        </div>
      </div>
    </div>

    <!-- Tab 4: 系统底层运维 (2x2网格大卡片矩阵，一屏收尽，整卡触控高饱满交互，v-if 彻底杜绝幽灵重叠) -->
    <div v-if="activeTab === 'tools'" class="grid grid-cols-2 gap-4">
      <!-- 1. 运行与守护日志 (整卡触控大卡片，min-h-[220px]) -->
      <div 
        @click="openLogModal"
        class="rounded-3xl border-2 border-car-border p-6 shadow-2xl transition-all bg-car-card hover:border-car-border-light active:scale-[0.985] cursor-pointer select-none flex flex-col justify-between min-h-[220px] group"
      >
        <div class="flex flex-col space-y-3">
          <div class="flex items-center justify-between pb-3 border-b border-car-border/60">
            <div class="flex items-center space-x-2.5">
              <span class="text-[21px] font-black text-car-text tracking-wide group-hover:text-car-accent transition-colors">运行与守护日志</span>
              <HelpDot @click.stop="openModal('confirm', {
                title: '【功能指南】运行与守护日志',
                desc: '1. 运行记录：\n自动记录车门、挡位、方向盘按键等状态，遇到功能异常时可一键导出给作者排查。\n\n2. 隐私安全保护：\n日志导出时会自动抹除车牌、手机号、行车轨迹等敏感信息，充分保护车主隐私。\n\n3. 不占存储：\n平时仅保存在临时运行内存中，不占车机存储空间，重启后自动释放。',
                tip: '日常无需理会，只有在排查问题时按需导出即可。',
                showCancel: false,
                confirmText: '我知道了'
              })" title="查看运行与守护日志说明" />
            </div>
            <span class="text-[12.5px] font-extrabold px-3 py-1 rounded-xl bg-car-item border border-car-border text-car-accent flex items-center space-x-1.5">
              <span>点击查看</span>
              <span class="text-[14px]">➔</span>
            </span>
          </div>
          <div class="text-[15px] text-car-sub font-bold leading-relaxed">
            实时采集挡位、方控按键与车身底盘信号，自动脱敏保护车主隐私；遇到功能异常时支持一键导出排查。
          </div>
        </div>

        <div class="mt-4 pt-3 border-t border-car-border/40 flex items-center justify-between">
          <div class="flex items-center text-[13.5px] text-emerald-400 font-extrabold">
            <StatusDot class="mr-2" size="sm" color="ok" :glow-px="6" />
            <span>内存循环缓存 · 重启自动清空</span>
          </div>
          <span class="text-[14px] font-black text-car-accent group-hover:underline">
            进入守护日志 ➔
          </span>
        </div>
      </div>

      <!-- 2. 车机 ADB 终端 (整卡点击开关，右侧集成独立进入终端按钮，min-h-[220px]) -->
      <div
        @click="toggleAdbMasterSwitch"
        :class="[
          'rounded-3xl border-2 p-6 shadow-2xl transition-all flex flex-col justify-between cursor-pointer select-none active:scale-[0.985] min-h-[220px] group',
          store.deviceInfo.adb_master_switch !== false
            ? 'bg-car-item border-car-accent shadow-md'
            : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
        ]"
      >
        <div class="flex flex-col space-y-3">
          <div class="flex items-center justify-between pb-3 border-b border-car-border/60">
            <div class="flex items-center space-x-2.5">
              <span class="text-[21px] font-black text-car-text tracking-wide group-hover:text-car-accent transition-colors">车机 ADB 终端</span>
              <HelpDot @click.stop="showAdbHelp" title="查看 ADB 总开关与失效功能说明" />
            </div>
            <div class="flex items-center space-x-2">
              <StatusDot size="sm" :color="store.deviceInfo.adb_master_switch !== false ? 'ok' : 'off'" :glow="store.deviceInfo.adb_master_switch !== false" :glow-px="store.deviceInfo.adb_master_switch !== false ? 6 : 0" />
              <span :class="['font-black text-[13.5px]', store.deviceInfo.adb_master_switch !== false ? 'text-car-text' : 'text-car-sub']">
                {{ store.deviceInfo.adb_master_switch !== false ? '已开启总开关' : '已关闭总开关' }}
              </span>
            </div>
          </div>
          <div class="text-[15px] text-car-sub font-bold leading-relaxed">
            本地特权调试交互终端；若遇其他车型无限弹调试授权窗，请关闭 ADB 总开关，避免弹窗打扰。
          </div>
        </div>

        <div class="mt-4 pt-3 border-t border-car-border/40 flex items-center justify-between">
          <span class="text-[13px] text-car-sub font-bold">
            点卡片切换总开关
          </span>
          <button 
            @click.stop="openDeepTools"
            class="h-[52px] px-5 rounded-xl border-2 border-car-border bg-car-card text-car-text hover:border-car-accent font-black text-[14px] cursor-pointer transition-all shadow-sm flex items-center justify-center whitespace-nowrap"
          >
            <span>打开终端 ➔</span>
          </button>
        </div>
      </div>

      <!-- 3. 整车硬件冷重启控制台 (整卡触控大卡片，min-h-[220px]) -->
      <div 
        @click="confirmHardReboot"
        class="rounded-3xl border-2 border-car-border p-6 shadow-2xl transition-all bg-car-card hover:border-rose-500/80 active:scale-[0.985] cursor-pointer select-none flex flex-col justify-between min-h-[220px] group"
      >
        <div class="flex flex-col space-y-3">
          <div class="flex items-center justify-between pb-3 border-b border-car-border/60">
            <div class="flex items-center space-x-2.5">
              <span class="text-[21px] font-black text-car-text tracking-wide group-hover:text-rose-500 transition-colors">整车重新启动</span>
              <HelpDot @click.stop="openModal('confirm', {
                title: '【功能指南】整车重新启动',
                desc: '1. 彻底重启车机：\n像重新打火一样彻底重启中控屏幕和车机系统，解决系统卡顿或刚改的配置没生效的问题。\n\n2. 安全提醒：\n行车过程中严禁操作！请在车辆安全停稳、挂入 P 挡后再点击重启。\n\n3. 重启耗时：\n整个过程大约 25~35 秒，期间屏幕暂时黑屏属于正常现象，请耐心等待重新点亮。',
                tip: '车辆停稳挂 P 挡后再点重启，期间黑屏请耐心等待。',
                showCancel: false,
                confirmText: '我知道了'
              })" title="查看整车硬件冷重启说明" />
            </div>
            <span class="text-[12.5px] font-extrabold px-3 py-1 rounded-xl bg-car-item border border-car-border text-rose-500 flex items-center space-x-1.5">
              <span>安全冷重启</span>
              <span class="text-[14px]">➔</span>
            </span>
          </div>
          <div class="text-[15px] text-car-sub font-bold leading-relaxed">
            彻底断电重置 MCU 与系统，安全保护需车辆停稳挂 P 挡；长按方向盘静音键 10 秒亦可触发硬件自愈。
          </div>
        </div>

        <div class="mt-4 pt-3 border-t border-car-border/40 flex items-center justify-between">
          <div class="flex items-center text-[13.5px] text-rose-500 font-extrabold">
            <span>⚠️ 停稳挂 P 挡使用</span>
          </div>
          <span class="text-[14px] font-black text-rose-500 dark:text-rose-400 group-hover:underline">
            立即硬件冷重启 ➔
          </span>
        </div>
      </div>

      <!-- 4. 工具箱开机自启动与后台守护 (整卡点击开关大卡片，min-h-[220px]) -->
      <div
        @click="toggleAutostart"
        :class="[
          'rounded-3xl border-2 p-6 shadow-2xl transition-all flex flex-col justify-between cursor-pointer select-none active:scale-[0.985] min-h-[220px] group',
          store.deviceInfo.autostart
            ? 'bg-car-item border-car-accent shadow-md'
            : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
        ]"
      >
        <div class="flex flex-col space-y-3">
          <div class="flex items-center justify-between pb-3 border-b border-car-border/60">
            <div class="flex items-center space-x-2.5">
              <span class="text-[21px] font-black text-car-text tracking-wide group-hover:text-car-accent transition-colors">开机自启静默守护</span>
              <HelpDot @click.stop="openModal('confirm', {
                title: '【功能指南】工具箱开机自启动与后台守护',
                desc: '1. 开机后台自启：\n车辆打火通电后，工具箱自动在后台安静启动，让各项自动化联动功能上车就能直接用。\n\n2. 关闭说明：\n关闭后车辆通电将不再自启工具箱，完全不占用后台资源；仅在您桌面主动打开时运行。\n\n3. 推荐常开：\n默认保持常开，确保上车后一切功能运转顺畅。',
                tip: '这是自动化功能的基础开关，可按需开启或关闭。',
                showCancel: false,
                confirmText: '我知道了'
              })" title="查看开机自启守护说明" />
            </div>
            <div class="flex items-center space-x-2">
              <StatusDot size="sm" :color="store.deviceInfo.autostart ? 'ok' : 'off'" :glow="store.deviceInfo.autostart" :glow-px="store.deviceInfo.autostart ? 6 : 0" />
              <span :class="['font-black text-[13.5px]', store.deviceInfo.autostart ? 'text-car-text' : 'text-car-sub']">
                {{ store.deviceInfo.autostart ? '已开启守护' : '已关闭守护' }}
              </span>
            </div>
          </div>
          <div class="text-[15px] text-car-sub font-bold leading-relaxed">
            打火通电静默运行，各项车身自动化联动与方控映射上车即用；关闭后完全不占用后台资源。
          </div>
        </div>

        <div class="mt-4 pt-3 border-t border-car-border/40 flex items-center justify-between">
          <span class="text-[13px] text-car-sub font-bold">
            点卡片任意位置切换
          </span>
          <span class="text-[14px] font-black text-car-accent group-hover:underline">
            {{ store.deviceInfo.autostart ? '守护运行中 ➔' : '点击开启守护 ➔' }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import SubTabs from '../components/SubTabs.vue';
import FeatureCard from '../components/FeatureCard.vue';
import StatusDot from '../components/StatusDot.vue';
import HelpDot from '../components/HelpDot.vue';
import { store, bridge, openModal, showToast, setStartupNav, refreshOilPrices } from '../store';
import { ref, computed } from 'vue';
import FloatingView from './FloatingView.vue';
import ThemeV2Panel from '../components/ThemeV2Panel.vue';
import { openAppstoreFlow } from '../utils/appstoreFreeze';
import { getDaysToAdjustment } from '../utils/oilPriceData';
import { ICON_PREVIEW_DEFAULT, ICON_PREVIEW_COOL } from '../utils/launcherIcons';

const activeTab = ref('desktop');
const systemTabs = [
  { id: 'desktop', name: '桌面胶囊悬浮', tag: '悬浮/屏保' },
  { id: 'capsules', name: '顶栏状态胶囊', tag: '显隐控制' },
  { id: 'tools', name: '系统维护与工具', tag: '日志/ADB' },
  { id: 'theme', name: '外观主题风格', tag: '双模高质感' },
  { id: 'icon', name: '车标与启动项', tag: '图标/首屏' }
];

function toggleCapsuleVisibility(key) {
  if (!store.capsuleVisibility) {
    store.capsuleVisibility = {
      battery: true,
      oilPrice: true,
      secretCode: true,
      store: true,
      whitelist: true,
      ip: true
    };
  }
  const current = store.capsuleVisibility[key] !== false;
  store.capsuleVisibility[key] = !current;
  try {
    localStorage.setItem('geely_capsule_visibility', JSON.stringify(store.capsuleVisibility));
  } catch (e) {}
  showToast(store.capsuleVisibility[key] ? '已显示该顶栏胶囊' : '已隐藏该顶栏胶囊');
}

function resetAllCapsules() {
  store.capsuleVisibility = {
    battery: true,
    oilPrice: true,
    secretCode: true,
    store: true,
    whitelist: true,
    ip: true
  };
  try {
    localStorage.setItem('geely_capsule_visibility', JSON.stringify(store.capsuleVisibility));
  } catch (e) {}
  showToast('已恢复全部顶栏胶囊显示 (默认全部激活)');
}

function onSelectAppIconStyle(style) {
  try {
    localStorage.setItem('toolbox_app_icon_style', style);
  } catch (e) {}
  store.deviceInfo.app_icon_style = style;
  bridge.call('setAppIconStyle', style);
}

function confirmHardReboot() {
  if (store.deviceInfo.adb_master_switch === false) {
    showToast('ADB 总开关已关闭，该功能无法使用');
    return;
  }
  openModal('confirm', {
    title: '车机硬件冷重启',
    desc: '即将对整车中控硬件执行完全掉电冷启动 (reboot)，耗时约 25~35 秒。白名单、音频通道与系统框架将彻底刷新生效。',
    tip: '【安全警示】严禁在行车行驶过程中执行整车冷重启操作！请确保车辆已安全停稳。',
    isDanger: true,
    confirmText: '确认立即掉电重启',
    onConfirm: () => {
      bridge.call('hardReboot');
      showToast('已下发整车冷重启指令，中控即将断电重启...');
    }
  });
}

function openDeepTools() {
  if (store.deviceInfo.adb_master_switch === false) {
    showToast('ADB 总开关已关闭，该功能无法使用');
    return;
  }
  openModal('deepTools');
}

function toggleAdbMasterSwitch() {
  const next = !(store.deviceInfo.adb_master_switch !== false);
  store.deviceInfo.adb_master_switch = next;
  bridge.call('setAdbMasterSwitchEnabled', next);
  showToast(next ? '已开启 ADB 特权服务总开关' : '已关闭 ADB 总开关 (彻底切断 ADB 探测与连接)');
}

function showAdbHelp() {
  openModal('confirm', {
    title: '【功能指南】ADB 高级调试开关与防弹窗说明',
    desc: '1. 为什么需要总开关：\n部分车型（或特定车机版本）连接内部调试通道时，中控屏会反复弹出“允许 USB 调试”授权弹窗，干扰正常看屏幕。关闭此开关可彻底切断调试连接，消除弹窗。\n\n2. 关闭后将失效的功能清单：\n• 车机应用高级管理（应用安全冻结/解冻/清空数据/静默卸载）\n• 第三方应用免签白名单\n• 整车重新启动（reboot）\n• 车机调试命令行终端\n• 悬浮窗最高权限/日志读取权限一键授权\n\n3. 不受影响的基础核心功能：\n方向盘按键切歌、蓝牙音乐播放与声音守护、D挡起步/打转向灯调起360影像、开机自启、全国实时油价等均完全正常可用。',
    tip: '日常建议保持开启；若遇车机反复弹出授权弹窗，可关闭此开关。',
    showCancel: false,
    confirmText: '我知道了'
  });
}


function openLogModal() {
  store.modals.log = true;
}


function toggleAutostart() {
  const next = !store.deviceInfo.autostart;
  store.deviceInfo.autostart = next;
  bridge.call('setAutostartEnabled', next);
  showToast(next ? '已开启车辆启动自动运行' : '已关闭车辆启动自动运行');
}

// 油价概览卡片状态
const currentProvinceName = computed(() => store.oilPrice.selectedProvince || '北京');
const currentOilData = computed(() => {
  const p = currentProvinceName.value;
  return store.oilPrice.regionalPrices[p] || store.oilPrice.regionalPrices['北京'];
});
const currentOilP92 = computed(() => currentOilData.value?.p92 ? currentOilData.value.p92.toFixed(2) : '--');
const currentOilP95 = computed(() => currentOilData.value?.p95 ? currentOilData.value.p95.toFixed(2) : '--');
const oilDaysText = computed(() => {
  if (!store.oilPrice.nextAdjustment?.date) return '同步中';
  const res = getDaysToAdjustment(store.oilPrice.nextAdjustment.date);
  if (res.isPassed) return '今日调价';
  if (res.days > 0) return `距调价仅剩 ${res.days} 天`;
  if (res.hours > 0) return `距调价仅剩 ${res.hours} 小时`;
  return '今晚调价';
});

// 启动首屏默认视图配置 (5 黄金大类 + 智能跟随)
const STARTUP_NAV_ITEMS = [
  { id: 'remember', name: '智能跟随', desc: '记忆上次停留' },
  { id: 'link', name: '行车联动', desc: '方控/360' },
  { id: 'body', name: '车载声音', desc: '提示/语音' },
  { id: 'fuel', name: '行程油耗', desc: '油价/账本' },
  { id: 'store', name: '应用生态', desc: '商城/安装' },
  { id: 'system', name: '系统设置', desc: '桌面/外观' }
];

function getNavName(id) {
  const map = {
    wheel: '行车联动',
    link: '行车联动',
    body: '车载声音',
    fuel: '行程油耗',
    store: '应用生态',
    audio: '车载声音',
    install: '应用生态',
    system: '系统设置'
  };
  return map[id] || '行车联动';
}

function onSelectStartupNav(navId) {
  setStartupNav(navId);
}

function onRefreshOilPrice() {
  refreshOilPrices(true);
}
</script>
