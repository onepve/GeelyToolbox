<template>
  <div class="flex flex-col space-y-4">
    <!-- 顶部车规二级分段胶囊：一页全景掌控 (支持外部统一接管隐藏) -->
    <SubTabs v-if="!hideSubTabs" :tabs="wheelTabs" v-model="activeTab" />

    <!-- Tab 1: 按键独立映射 (v-if 彻底杜绝幽灵重叠) -->
    <div v-if="activeTab === 'keys'" class="flex flex-col space-y-4 pb-28">
      
      <!-- 常驻：方控接管总开关 + 模式切换（切换后仍可用，防卡死）
           这张卡本身就是本页的总开关，不再另设外层大卡包住下面的按键卡片。
           点左侧「方向盘按键接管」文字区 = 开/关接管；右侧切换卡片模式。 -->
      <div
        :class="['rounded-3xl border-2 bg-car-card px-5 py-4 flex items-center justify-between transition-all',
                 store.vehicleAuto.wheel_master_switch ? 'border-car-accent shadow-md' : 'border-car-border shadow-sm']"
      >
        <div
          @click="toggleWheelMasterSwitch"
          class="flex items-center space-x-3 min-w-0 cursor-pointer select-none active:scale-[0.995] transition-all"
        >
          <StatusDot size="md" :color="store.vehicleAuto.wheel_master_switch ? 'ok' : 'off'" :glow-px="store.vehicleAuto.wheel_master_switch ? 8 : 0" />
          <span class="text-[19px] font-black text-car-text whitespace-nowrap">方向盘按键接管</span>
          <span
            :class="['text-[13.5px] font-black px-2.5 py-0.5 rounded-lg border shrink-0',
                     store.vehicleAuto.wheel_master_switch ? 'bg-car-item text-car-accent border-car-accent' : 'bg-car-item text-car-sub border-car-border']"
          >
            {{ store.vehicleAuto.wheel_master_switch ? '已接管' : '原车默认' }}
          </span>
          <span class="text-[14px] font-black whitespace-nowrap text-car-sub">
            {{ store.vehicleAuto.wheel_master_switch ? '点此关闭' : '点此开启' }}
          </span>
        </div>
        <div class="flex items-center space-x-1.5 shrink-0 bg-car-item p-1.5 rounded-2xl border-2 border-car-border">
          <button
            v-for="m in wheelModeOptions"
            :key="m.id"
            type="button"
            @click="setMappingMode(m.id)"
            :class="['px-4 h-[50px] rounded-xl font-black text-[14.5px] transition-all cursor-pointer flex items-center justify-center border-2 select-none',
                     m.active ? 'bg-car-card border-car-accent text-car-accent shadow-md' : 'bg-transparent border-transparent text-car-sub hover:text-car-text']"
          >
            {{ m.name }}
          </button>
        </div>
      </div>


      <!-- 模式 A：标准卡片模式 (六大按键网格) -->
      <div v-if="mappingMode === 'preset'" class="flex flex-col space-y-4">
        <!-- 双列规整网格：一排两卡，工整对齐，共三排 -->
        <div class="grid grid-cols-3 gap-3">
        <!-- ① 右方向盘主页/返回键 -->
        <WheelGestureCard
          class="!mb-0"
          key-name="back"
          card-title="右方向盘 ① 主页/返回按键"
          card-desc="右盘 ① 号返回键，单击映射为高德导航、360 全景等。"
          helpTitle="【功能指南】主页 / 返回按键映射"
          helpText="1. 原厂功能：&#10;原车默认点按返回上一级或主页。&#10;&#10;2. 自定义映射：&#10;可自由绑定为秒开 360 全景、高德地图、打开指定应用或执行控制台快捷动作。&#10;&#10;3. 恢复原厂：&#10;若希望保持吉利系统默认返回逻辑，下拉选择「恢复原厂默认」即可。"
          helpTip="若平时习惯原车返回逻辑，建议保持「恢复原厂默认」。"
        />

        <!-- ② 右方向盘音量滚轮按压 -->
        <WheelGestureCard
          class="!mb-0"
          key-name="ok"
          card-title="右方向盘 ② 音量滚轮按压"
          card-desc="右盘 ② 号滚轮垂直向下按压，默认播放/暂停，支持改绑。"
          helpTitle="【功能指南】音量滚轮按压映射"
          helpText="1. 滚轮垂直按压：&#10;右方向盘音量滚轮除了上下滚动调音量外，垂直向下按压也是独立物理按键。&#10;&#10;2. 推荐用途：&#10;默认推荐设为「播放 / 暂停媒体」，开车途中一键暂停极方便；亦可设为 360 全景或高德地图。&#10;&#10;3. 纯净极速：&#10;已剥离复杂长按判断，按压即响应，不影响滚轮正常滚动调音。"
          helpTip="推荐绑定「播放/暂停」，开车切歌听歌更顺手。"
        />

        <!-- ③ 右方向盘静音键 -->
        <WheelGestureCard
          class="!mb-0"
          key-name="mute"
          card-title="右方向盘 ③ 静音按键"
          card-desc="右盘 ③ 号静音键，单击自定义；长按 10 秒仍为原车冷重启。"
          helpTitle="【功能指南】静音按键映射与 10 秒冷重启"
          helpText="1. 静音键自定义：&#10;原车点按为全局静音，可改绑为打开 360 全景、高德导航、小爱同学或切歌。&#10;&#10;2. 强制重启救急：&#10;长按静音键 10 秒是车机自带的强制重启功能，无论在此如何设置，该应急功能始终有效，遇到车机卡死可随时使用。"
          helpTip="长按 10 秒是车机自带的应急重启，任何设置都不会影响它。"
        />

        <!-- ⑥ 右方向盘 MODE 键 -->
        <WheelGestureCard
          class="!mb-0"
          key-name="mode"
          card-title="右方向盘 ⑥ MODE 模式键"
          card-desc="右盘 ⑥ 号 MODE 键，默认一键秒开 360 全景，支持改绑。"
          helpTitle="【功能指南】MODE 模式按键映射"
          helpText="1. 摆脱原厂收音机：&#10;原车点按 MODE 会强制唤醒伴听或收音机，改绑后可彻底告别讨厌的原厂收音机打扰。&#10;&#10;2. 一键秒开 360：&#10;默认推荐设为打开「360 全景」，倒车、过狭窄路段或侧方停车时一键呼出，非常实用。&#10;&#10;3. 极速响应：&#10;按压立即触发，无任何手势等待延迟。"
          helpTip="绝大多数车友强烈推荐绑定「360 全景影像」，过窄路神器。"
        />

        <!-- ④ & ⑦ 切歌双键：上一曲 / 下一曲 -->
        <FeatureCard
          class="!mb-0 col-span-2"
          dense
          title="右方向盘 ④ 下一曲 / ⑦ 上一曲"
          desc="右盘 ④/⑦ 号切歌键，接管后防偶发失效，切歌零延迟。"
          helpTitle="【功能指南】下一曲 / 上一曲切歌按键映射"
          helpText="1. 单击极速响应：&#10;方向盘右侧「上一曲 / 下一曲」按键按压即刻响应，零延迟跟手。&#10;&#10;2. 完美适配音乐软件：&#10;深度适配 QQ音乐车机版、网易云音乐、酷狗等，切歌稳定不冲突。&#10;&#10;3. 灵活改绑：&#10;如需由方控开 360 或调用其他功能，也可在此自由改绑。"
          helpTip="切歌动作建议保持「切歌(官方调度)」，切歌最稳定。"
        >
          <div class="grid grid-cols-2 gap-3.5">
            <!-- ⑦ 上一曲 -->
            <div class="bg-car-item border border-car-border rounded-2xl p-3.5 flex flex-col space-y-2.5">
              <div class="flex items-center justify-between">
                <span class="text-[15.5px] font-black text-car-text">⑦ 上一曲 (向左)</span>
                <span class="text-[12px] text-car-accent font-bold">按压即发</span>
              </div>
              <ActionSelect key-name="prev" gesture="single" />
            </div>

            <!-- ④ 下一曲 -->
            <div class="bg-car-item border border-car-border rounded-2xl p-3.5 flex flex-col space-y-2.5">
              <div class="flex items-center justify-between">
                <span class="text-[15.5px] font-black text-car-text">④ 下一曲 (向右)</span>
                <span class="text-[12px] text-car-accent font-bold">按压即发</span>
              </div>
              <ActionSelect key-name="next" gesture="single" />
            </div>
          </div>
        </FeatureCard>

        <!-- ⑤ 方向盘自定义键 -->
        <WheelGestureCard
          class="!mb-0"
          key-name="custom"
          card-title="方向盘 ⑤ 自定义按键"
          :card-desc="customKeyDesc"
          helpTitle="【功能指南】方向盘自定义按键映射与车型适配"
          helpText="1. 热擎：&#10;原车无 360 与专属交互，该按键在原厂处于闲置状态，是改绑 360 或高德导航的绝佳按键。&#10;&#10;2. 激擎 / Battle：&#10;若原车已被特定交互占用，可选择「恢复原厂默认」放行；若闲置亦可自由映射。&#10;&#10;3. 全功能接管：&#10;可自由映射为秒开 360 全景、导航、静音、打开常用应用等。"
          :helpTip="customKeyTip"
        />

        <!-- 方控响应体检与排错 (通道测试与冲突诊断) -->
        <FeatureCard
          class="!mb-0 col-span-2"
          dense
          title="方控响应与冲突体检"
          desc="按下方向盘任意键，实时检测信号通路与硬件延迟；排查按键无反应与通道冲突。"
          helpTitle="【功能指南】方控响应与冲突体检"
          helpText="1. 体检作用：&#10;按下方向盘上的任意物理按键，助手会立即检测底盘信号通路是否通畅，0ms 读出硬件键码。&#10;&#10;2. 冲突排查：&#10;如果遇到按键没反应、失灵或冲突，点击体检按一下方向盘，即可精准定位是通道未开启、被第三方抢占还是软件未适配。&#10;&#10;3. 安全测试：&#10;体检期间按下的按键仅用于信号检测，不会触发已绑定的切歌、360 等任何功能，测完即恢复。&#10;&#10;4. 安全红线：&#10;仅支持检测右方向盘多媒体控制按键；左侧巡航与驾驶安全按键为原车核心安全件，严禁测试与拦截。"
          helpTip="遇到按键不响应或切歌失效时，先点此体检，按一下看信号是否通畅。"
        >
          <button
            @click="openKeyLearning"
            class="w-full min-h-[72px] rounded-2xl bg-car-item border-2 border-car-accent text-car-text ring-2 ring-car-accent/25 font-black text-[19px] cursor-pointer shadow-md hover:border-car-border-light transition-all active:scale-[0.99]"
          >
            开始方控响应与冲突体检
          </button>
        </FeatureCard>
        </div>
    </div>

    <!-- 模式 B：自定义按键列表模式 (多车型动态学习与独立映射) -->
    <div v-else-if="mappingMode === 'custom_list'" class="flex flex-col space-y-4">
      <!-- 顶部操作条：大白话说明 + 新增学习按钮 -->
      <div class="flex items-center justify-between p-4 rounded-2xl bg-car-card border-2 border-car-border shadow-md">
        <div class="flex flex-col space-y-1">
          <div class="flex items-center space-x-2">
            <span class="text-[18px] font-black text-car-text">动态按键映射表</span>
            <span class="text-[13px] font-black px-2.5 py-0.5 rounded-full bg-car-item text-car-accent border border-car-accent">
              已捕获 {{ customMappingList.length }} 个按键
            </span>
          </div>
          <div class="text-[13.5px] text-car-sub font-bold">
            按下方向盘上的实体按键自动学习，单行直观改绑；0ms 极速响应，各车型独立生效。
          </div>
        </div>

        <button
          @click="openAddKeyLearning"
          class="px-5 h-[52px] rounded-xl bg-car-item border-2 border-car-accent text-car-accent hover:border-car-border-light font-black text-[16px] cursor-pointer shadow-md active:scale-[0.98] transition-all shrink-0 flex items-center justify-center"
        >
          <span>按键学习添加</span>
        </button>
      </div>

      <!-- 空状态卡片：暂无按键引导 -->
      <div
        v-if="customMappingList.length === 0"
        class="flex flex-col items-center justify-center p-12 rounded-3xl bg-car-card border-2 border-dashed border-car-border text-center space-y-3"
      >
        <span class="text-[18px] font-black text-car-text">暂无捕获的按键映射</span>
        <span class="text-[14px] text-car-sub font-bold max-w-[420px]">
          点击右上角「按键学习添加」，然后按下方向盘右侧多媒体区任意按键，即可将其添加到本列表中自由分配动作。
        </span>
        <button
          @click="openAddKeyLearning"
          class="mt-2 px-6 h-[52px] rounded-xl bg-car-accent-bg border-2 border-car-accent text-car-accent font-black text-[16px] cursor-pointer shadow-md hover:opacity-90 active:scale-[0.98] transition-all flex items-center justify-center"
        >
          立即捕获第一个按键
        </button>
      </div>

      <!-- 宽行列表结构：消灭网格嵌套与卡片重叠 -->
      <div v-else class="flex flex-col space-y-3">
        <div
          v-for="(item, idx) in customMappingList"
          :key="item.code"
          class="p-4 rounded-2xl bg-car-card border-2 border-car-border shadow-md flex items-center justify-between space-x-4 transition-all"
        >
          <!-- 左侧：按键键码徽章 + 按键名称 -->
          <div class="flex items-center space-x-3.5 min-w-[200px] shrink-0">
            <span class="px-3 py-1.5 rounded-xl bg-car-item text-car-accent border border-car-accent font-black text-[15px] tracking-wider shrink-0">
              # {{ item.code }}
            </span>
            <div class="flex flex-col">
              <span class="text-[17.5px] font-black text-car-text leading-tight">
                {{ item.name || '新车型专属按键' }}
              </span>
              <span class="text-[12.5px] font-bold text-car-sub mt-0.5">
                底盘硬件物理键
              </span>
            </div>
          </div>

          <!-- 中间：车规双列磁贴动作下拉框 -->
          <div class="flex-1 max-w-[360px]">
            <CustomKeyActionSelect
              :model-value="item.action || 'default'"
              :custom-app-name="item.custom_name || ''"
              :row-id="String(item.code)"
              @change="payload => updateCustomKeyAction(idx, payload.action, payload.customName)"
            />
          </div>

          <!-- 右侧：移除操作 -->
          <div class="shrink-0 pl-2">
            <button
              @click="removeCustomKey(idx)"
              class="px-4 h-[50px] rounded-xl border-2 border-rose-500/40 bg-rose-500/10 text-rose-400 hover:bg-rose-500/20 font-black text-[14.5px] cursor-pointer transition-all active:scale-95 flex items-center justify-center shadow-sm select-none"
            >
              移除
            </button>
          </div>
        </div>
      </div>
    </div>


    </div>

    <!-- Tab 2: 接管模式与图解 (v-if 彻底杜绝幽灵重叠) -->
    <div v-if="activeTab === 'mode'" class="flex flex-col space-y-4">
    <!-- 1. 方向盘方控接管模式 -->
    <FeatureCard 
      title="接管方式"
      desc="按键和蓝牙分开管，四个按钮直接选组合。"
      helpTitle="【功能指南】接管方式怎么选"
      helpText="按键和蓝牙是两件独立的事，分开选就好：&#10;&#10;• ① 全面接管（推荐）：方向盘切歌和蓝牙声音都交给助手。原厂多媒体被冻结、或者你用第三方播放器，选这个。&#10;• ② 只接管方控：只拦方向盘切歌，蓝牙声音全归原车。适合原厂多媒体正常、只想让助手管按键的。&#10;• ③ 只接管蓝牙：方向盘按键全还给原车，助手只负责把蓝牙声音保住。适合原厂按键够用、但蓝牙偶尔没声音的。&#10;• ④ 完整原厂：按键和声音都不碰，助手只在后台待着。&#10;&#10;提醒：选②或④时，如果原厂多媒体被冻结，车机里没人管蓝牙，助手也没接手，蓝牙就会没声音。想让助手保声音，请改选①或③。&#10;&#10;换完选项如果没马上生效，重启一下车机就好。"
      helpTip="想全交给助手选①；只想管声音选③；只想管按键选②；都不要选④。"
    >
      <div class="grid grid-cols-4 gap-3.5" style="grid-gap: 12px; -webkit-column-gap: 12px;">
        <MatrixButton 
          title="① 全面接管"
          subtitle="按键和声音都交给助手"
          :active="isPreset(1)"
          @click="applyPreset(1)"
        />
        <MatrixButton 
          title="② 只接管方控"
          subtitle="只管切歌，声音还给原车"
          :active="isPreset(2)"
          @click="applyPreset(2)"
        />
        <MatrixButton 
          title="③ 只接管蓝牙"
          subtitle="只管声音，按键还给原车"
          :active="isPreset(3)"
          @click="applyPreset(3)"
        />
        <MatrixButton 
          title="④ 完整原厂"
          subtitle="按键和声音全还给原车"
          :active="isPreset(4)"
          @click="applyPreset(4)"
        />
      </div>
    </FeatureCard>

    <div
      @click="toggleFloating()"
      :class="[
        'rounded-3xl border-2 p-5 shadow-xl transition-all duration-200 cursor-pointer select-none active:scale-[0.99]',
        floatingEnabled
          ? 'bg-car-item border-car-accent shadow-md'
          : 'bg-car-card border-car-border hover:border-car-border-light shadow-sm'
      ]"
    >
      <div class="flex items-center justify-between">
        <div class="flex items-center">
          <StatusDot class="mr-3" size="md" color="accent" :glow-px="8" />
          <div class="flex flex-col">
            <span class="text-[18px] font-black text-car-text">吉利缤越 COOL 方向盘多媒体按键图解</span>
            <span class="text-[14px] text-car-sub font-bold mt-0.5">
              方向盘右侧多媒体按键：静音、切歌、MODE、返回键均可一键接管并自定义
            </span>
          </div>
        </div>
        <div class="flex items-center space-x-3 shrink-0">
          <!-- 悬浮状态灯指示（消除大按钮） -->
          <div class="flex items-center space-x-2 mr-1">
            <StatusDot size="sm" :color="floatingEnabled ? 'ok' : 'off'" :glow-px="6" />
            <span :class="['text-[15px] font-black', floatingEnabled ? 'text-car-text' : 'text-car-sub']">
              {{ floatingEnabled ? '悬浮图解已开启' : '悬浮图解已关闭' }}
            </span>
          </div>
          <button
            type="button"
            @click.stop="toggleDiagram()"
            class="h-[50px] px-4 rounded-xl bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer shadow-sm active:scale-95 transition-all"
          >
            {{ showDiagram ? '收起图解' : '展开图解' }}
          </button>
        </div>
      </div>

      <!-- 图解内容展示区 (阻止冒泡防误触) -->
      <div v-if="showDiagram" @click.stop class="mt-4 pt-4 border-t border-car-border/60 flex items-start space-x-6">
        <!-- 原厂方向盘按键高清图示 (沉稳车规底座，绝非刺眼白底) -->
        <div class="p-3 bg-car-item rounded-2xl shadow-md border-2 border-car-border w-[320px] min-w-[320px] max-w-[320px] shrink-0">
          <img 
            :src="wheelGuideImg" 
            alt="吉利缤越 COOL 右侧多媒体按键图解" 
            class="guide-img w-full h-auto rounded-xl object-contain block" 
          />
        </div>

        <!-- 按键编号与原厂定义清单 -->
        <div class="flex-1 min-w-0 flex flex-col space-y-2.5 text-[14.5px] font-bold text-car-sub">
          <div class="flex space-x-3">
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">① 主页/返回键:</span> 返回车机中控主页 / 单击自定义
            </div>
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">② 滚轮按压:</span> 媒体播放 / 暂停 (支持自定义)
            </div>
          </div>
          <div class="flex space-x-3">
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">③ 静音键:</span> 开关静音 (支持单击自定义)
            </div>
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">④ 向右选择键:</span> 下一个文件 / 下一曲 (单击自定义)
            </div>
          </div>
          <div class="flex space-x-3">
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">⑤ 自定义键:</span> 方向盘预留自定义键 (单击自定义)
            </div>
            <div class="flex-1 p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
              <span class="text-car-accent font-black mr-1.5">⑥ MODE键:</span> 音源切换 / 自定义 (单击自定义)
            </div>
          </div>
          <div class="w-full p-3 rounded-xl bg-car-card border border-car-border whitespace-nowrap">
            <span class="text-car-accent font-black mr-1.5">⑦ 向左选择键:</span> 上一个文件 / 上一曲 (单击自定义)
          </div>
        </div>
      </div>
    </div>

    </div>

    <!-- 方向盘图解悬浮对照卡：支持拖放自定义位置并本地记忆，下次同位显示；原地点按隐藏 -->
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
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { store, bridge, openModal, showToast } from '../store';
import { useWheelGesture } from '../composables/useWheelGesture';
import { playTouchFeedback } from '../utils/sound';
import SubTabs from '../components/SubTabs.vue';
import StatusDot from '../components/StatusDot.vue';
import BaseButton from '../components/BaseButton.vue';
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';
import ActionSelect from '../components/ActionSelect.vue';
import CustomKeyActionSelect from '../components/CustomKeyActionSelect.vue';
import WheelGestureCard from '../components/WheelGestureCard.vue';

// 缤越 COOL 专车原厂 SX-0017 多媒体按键图解（深色车规，柔和不刺眼）
import wheelGuideImg from '../assets/steering_wheel_guide.webp';
import wheelGuideFloatingImg from '../assets/steering_wheel_guide_floating.svg';

const props = defineProps({
  initialTab: {
    type: String,
    default: 'keys'
  },
  hideSubTabs: {
    type: Boolean,
    default: false
  }
});

const activeTab = ref(props.initialTab || 'keys');

const mappingMode = computed(() => store.vehicleAuto.wheel_mapping_mode || 'preset');

function setMappingMode(mode) {
  if (store.vehicleAuto.wheel_mapping_mode === mode) return;
  playTouchFeedback();
  store.vehicleAuto.wheel_mapping_mode = mode;
  bridge.call('setVehicleAutomationSetting', 'wheel_mapping_mode', mode);
  showToast(mode === 'custom_list' ? '已切换至「自定义列表模式」' : '已切换至「标准卡片模式」');
}

// 模式 B：自定义按键列表
const customMappingList = computed(() => {
  try {
    const raw = store.vehicleAuto.wheel_custom_mapping_list;
    if (!raw) return [];
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
    return Array.isArray(parsed) ? parsed : [];
  } catch (e) {
    return [];
  }
});

function saveCustomMappingList(list) {
  const jsonStr = JSON.stringify(list);
  store.vehicleAuto.wheel_custom_mapping_list = jsonStr;
  bridge.call('setVehicleAutomationSetting', 'wheel_custom_mapping_list', jsonStr);
}

function updateCustomKeyAction(index, action, customName) {
  const list = [...customMappingList.value];
  if (list[index]) {
    list[index].action = action;
    if (customName !== undefined) {
      list[index].custom_name = customName;
    }
    saveCustomMappingList(list);
  }
}

function removeCustomKey(index) {
  playTouchFeedback();
  const list = [...customMappingList.value];
  const item = list[index];
  openModal('confirm', {
    title: '删除按键映射',
    desc: `是否确认移除「${item.name || '按键'}」的自定义映射？`,
    isDanger: true,
    confirmText: '确认删除',
    onConfirm: () => {
      list.splice(index, 1);
      saveCustomMappingList(list);
      showToast('已移除该按键映射');
    }
  });
}

function openAddKeyLearning() {
  playTouchFeedback();
  openModal('wheelKeyLearning', {
    ts: Date.now(),
    onCaptured: (code, defaultName) => {
      const list = [...customMappingList.value];
      const exists = list.some(item => item.code === code);
      if (exists) {
        showToast(`按键 [${defaultName}] 已在列表中，无需重复添加`);
        return;
      }
      list.push({
        code,
        name: defaultName,
        action: 'default',
        custom_name: ''
      });
      saveCustomMappingList(list);
      showToast(`已成功添加「${defaultName}」`);
    }
  });
}

// 车型差异化文案（trim_name 由车机端自动探测推送）
const trimName = computed(() => store.deviceInfo.trim_name || '');
const isLowTrim = computed(() => trimName.value.includes('热擎'));
const isHighTrim = computed(() => trimName.value.includes('Battle'));
const customKeyDesc = computed(() => {
  if (isLowTrim.value) return '⑤ 号自定义键：热擎原厂闲置按键，可放心改绑常用功能。';
  if (isHighTrim.value) return '⑤ 号自定义键：Battle 原厂有专属交互，可接管也可放行。';
  return '⑤ 号自定义键，原厂预留按键，单击秒级触发。';
});
const customKeyTip = computed(() => {
  if (isLowTrim.value) return '热擎推荐设为「360 影像」或「高德导航」，方便行车随时看盲区。';
  return '推荐设为「360 全景影像」，行车随时查看周边盲区。';
});

// 方控组卡内的模式切换（原顶部胶囊搬进卡身，避免双处维护）
const wheelModeOptions = computed(() => [
  { id: 'preset', name: '标准卡片模式', active: mappingMode.value === 'preset' },
  { id: 'custom_list', name: '自定义列表模式', active: mappingMode.value === 'custom_list' }
]);

const wheelTabs = [
  { id: 'keys', name: '按键映射手势', tag: '6大按键' },
  { id: 'mode', name: '接管模式图解', tag: '工作模式' }
];

watch(() => props.initialTab, (v) => {
  if (v) activeTab.value = v;
});

const showDiagram = ref(true);

function toggleDiagram() {
  playTouchFeedback();
  showDiagram.value = !showDiagram.value;
}
const FLOAT_TOGGLE_KEY = 'wheel_floating_guide_enabled';
const floatingEnabled = ref(localStorage.getItem(FLOAT_TOGGLE_KEY) !== 'false');
const showFloating = ref(false);

function toggleFloating() {
  playTouchFeedback();
  floatingEnabled.value = !floatingEnabled.value;
  localStorage.setItem(FLOAT_TOGGLE_KEY, String(floatingEnabled.value));
  if (!floatingEnabled.value) {
    showFloating.value = false;
  } else {
    const st = document.documentElement.scrollTop || document.body.scrollTop || 0;
    showFloating.value = st > 40;
  }
}

const FLOAT_POS_KEY = 'wheel_floating_guide_pos';
const floatingGuideEl = ref(null);
const floatingPos = reactive(loadFloatingPos());

function loadFloatingPos() {
  try {
    const saved = JSON.parse(localStorage.getItem(FLOAT_POS_KEY));
    if (saved && Number.isFinite(saved.x) && Number.isFinite(saved.y)) return saved;
  } catch (e) {}
  return { x: window.innerWidth - 168 - 38, y: 88 };
}

function onWheelScroll(e) {
  const el = e && e.target;
  const st = el && el.scrollTop ? el.scrollTop : (document.documentElement.scrollTop || document.body.scrollTop || 0);
  showFloating.value = floatingEnabled.value && st > 40;
}

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
      localStorage.setItem(FLOAT_POS_KEY, JSON.stringify({ x: floatingPos.x, y: floatingPos.y }));
    } else {
      showFloating.value = false;
    }
  };
  document.addEventListener('pointermove', onMove);
  document.addEventListener('pointerup', onUp);
}

onMounted(() => {
  floatingPos.x = Math.min(Math.max(8, floatingPos.x), Math.max(8, window.innerWidth - 184));
  floatingPos.y = Math.min(Math.max(8, floatingPos.y), Math.max(8, window.innerHeight - 140));
  document.addEventListener('scroll', onWheelScroll, { passive: true, capture: true });
});

onBeforeUnmount(() => {
  document.removeEventListener('scroll', onWheelScroll, { capture: true });
});

const { setGestureAction } = useWheelGesture();

function openKeyLearning() {
  playTouchFeedback();
  openModal('wheelKeyLearning', { ts: Date.now() });
}

function toggleWheelMasterSwitch() {
  playTouchFeedback();
  const next = !store.vehicleAuto.wheel_master_switch;
  store.vehicleAuto.wheel_master_switch = next;
  bridge.call('setVehicleAutomationSetting', 'wheel_master_switch', next);
  showToast(next ? '按键接管总开关: 已开启' : '按键接管总开关: 已关闭 (原车控制)');
}

/**
 * 一键套用预设组合。四个按钮 = 方控维度 × 蓝牙维度 的 2×2 全组合。
 *   preset 1: 方控接管 + 蓝牙接管  （全面接管，推荐）
 *   preset 2: 方控接管 + 蓝牙原厂  （只接管方控）
 *   preset 3: 方控原厂 + 蓝牙接管  （只接管蓝牙）
 *   preset 4: 方控原厂 + 蓝牙原厂  （完整原厂）
 */
const PRESET_TABLE = {
  1: { wheel: 'takeover', media: 'takeover', name: '全面接管' },
  2: { wheel: 'takeover', media: 'factory',  name: '只接管方控' },
  3: { wheel: 'factory',  media: 'takeover', name: '只接管蓝牙' },
  4: { wheel: 'factory',  media: 'factory',  name: '完整原厂' },
};

function isPreset(n) {
  const p = PRESET_TABLE[n];
  if (!p) return false;
  const curWheel = store.vehicleAuto.wheel_control_mode || 'takeover';
  const curMedia = store.vehicleAuto.media_control_mode || 'takeover';
  return curWheel === p.wheel && curMedia === p.media;
}

function applyPreset(n) {
  const p = PRESET_TABLE[n];
  if (!p) return;
  playTouchFeedback();

  // 只在点按钮这一下查一次原厂多媒体状态：纯本地权限判定，零 ADB、零轮询。
  // 检测结果只用于给车主一句提示，绝不拦截切换。
  let mmState = 'installed';
  try {
    mmState = bridge.call('getMultimediaState') || 'installed';
  } catch (e) {
    mmState = 'installed';
  }

  store.vehicleAuto.wheel_control_mode = p.wheel;
  store.vehicleAuto.media_control_mode = p.media;
  bridge.call('setWheelControlStringSetting', 'wheel_control_mode', p.wheel);
  bridge.call('setWheelControlStringSetting', 'media_control_mode', p.media);

  // 胶囊提示：只讲真实后果，绝不拦截切换；文案 ≤20 字，2 秒内看完。
  let tip = '';
  if (p.media === 'factory') {
    if (mmState === 'frozen' || mmState === 'absent') {
      // 车主明确选了不接管蓝牙，这时确实可能没声音，如实告知，但不替他改主意
      tip = '原厂多媒体已冻结，蓝牙可能没声音';
    }
  } else {
    if (mmState === 'frozen' || mmState === 'absent') {
      tip = '助手已接管，蓝牙声音保住了';
    } else {
      tip = '助手接管蓝牙，声音按助手走';
    }
  }

  showToast(tip ? (p.name + '：' + tip) : ('已切换：' + p.name));
}

function resetAllToFactory() {
  openModal('confirm', {
    title: '恢复原车默认设置',
    desc: '将方向盘所有按键全部恢复为原车默认动作，不再拦截任何按键。是否确认？',
    isDanger: true,
    confirmText: '确认恢复',
    onConfirm: () => {
      const keys = ['ok', 'mute', 'mode', 'next', 'prev', 'back', 'call', 'voice', 'home', 'custom'];
      const gestures = ['single', 'double', 'long'];
      for (const k of keys) {
        for (const g of gestures) {
          setGestureAction(k, g, 'default');
        }
      }
      showToast('所有方向盘按键已恢复原车默认！');
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
