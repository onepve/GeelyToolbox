<template>
  <div class="flex flex-col space-y-6">
    <!-- 顶端：专车机型协议与流光微胶囊选择器 (支持多机型展示与切换，默认自动感知高亮) -->
    <div class="bg-car-card border-2 border-car-border rounded-3xl p-6 shadow-xl">
      <div class="flex items-center justify-between pb-4 mb-4 border-b border-car-border/60">
        <div class="flex items-center">
          <span class="w-3.5 h-3.5 rounded-full bg-emerald-500 shadow-[0_0_10px_#10B981] mr-3"></span>
          <span class="text-[21px] font-black text-car-text">专车协议与车型适配中枢</span>
        </div>
        <!-- 核心流光高亮微胶囊 -->
        <div class="px-4 py-1.5 rounded-full bg-car-accent-bg border border-car-accent/50 flex items-center shadow-sm">
          <span class="text-[14.5px] text-car-accent-text font-black">
            👑 {{ currentModelSpec.name }} 指定专用协议 (已自动适配)
          </span>
        </div>
      </div>

      <!-- 可选机型微胶囊流 -->
      <div class="flex flex-wrap items-center">
        <button 
          v-for="model in supportedModels"
          :key="model.id"
          @click="selectCarModel(model.id)"
          :class="[
            'px-5 py-2.5 rounded-2xl font-black text-[15.5px] cursor-pointer transition-all mr-3 mb-2 flex items-center',
            selectedModelId === model.id 
              ? 'bg-car-item border-2 border-car-accent text-car-text ring-2 ring-car-accent/25 shadow-md' 
              : 'bg-car-item border border-car-border text-car-sub hover:text-car-text hover:border-car-border-light'
          ]"
        >
          <span v-if="selectedModelId === model.id" class="w-2 h-2 rounded-full bg-car-accent mr-2 shadow-[0_0_6px_var(--accent-gold)]"></span>
          <span>{{ model.label }}</span>
          <span v-if="model.isPrimary" class="ml-2 text-[12px] px-2 py-0.5 rounded bg-car-accent/20 text-car-accent border border-car-accent/40 font-extrabold">指定专用</span>
        </button>
      </div>
      <div class="text-[14px] text-car-sub/90 font-bold mt-2">
        说明：系统基于亿咖通 E02 (IHU516G) 底层 CAN/MCU 总线自动嗅探识别。支持不同吉利车机协议平滑切换。
      </div>
    </div>

    <!-- 1. 挡位安全播报 (前进档、倒车档、驻车档 P、空档 四大标准档位) -->
    <FeatureCard 
      title="1. 挡位安全播报 (前进档 D / 倒车档 R / 驻车档 P / 空档 N)"
      desc="四大标准挡位均已生成专属高品质晓晓知性语音，并支持【声效设置】。内置【有人感知状态机】：开机与蓝牙靠近默认 P 挡绝对静默，换出激活，换回归零。"
    >
      <div class="grid grid-cols-2 gap-4">
        <!-- 前进挡 D -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex flex-col justify-center pr-6 flex-1">
            <div class="flex items-center space-x-3 mb-1.5">
              <span class="text-[22px] font-black text-car-text tracking-wide">前进挡 (D 挡)</span>
              <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-car-accent font-bold">启程安全</span>
            </div>
            <span class="text-[15px] text-car-sub font-bold leading-relaxed">踩刹车挂入前进挡温馨启程播报</span>
          </div>

          <div class="flex space-x-3 shrink-0 items-center">
            <button 
              @click="toggleSetting('voice_enable_gear_d')"
              :class="[
                'w-[124px] h-[120px] rounded-2xl border-2 font-black text-[20px] transition-all flex items-center justify-center select-none cursor-pointer shadow-md',
                store.vehicleAuto.voice_enable_gear_d 
                  ? 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-amber-500/10' 
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              {{ store.vehicleAuto.voice_enable_gear_d ? '开关已开' : '开关已关' }}
            </button>
            <div class="flex flex-col space-y-2.5 w-[130px]">
              <button 
                @click="testVoice('gear_d')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                试听语音
              </button>
              <button 
                @click="openCustomVoice('gear_d', '前进挡 D', 'gear_d.mp3')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-sub hover:text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                声效设置
              </button>
            </div>
          </div>
        </div>

        <!-- 倒车挡 R -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex flex-col justify-center pr-6 flex-1">
            <div class="flex items-center space-x-3 mb-1.5">
              <span class="text-[22px] font-black text-car-text tracking-wide">倒车挡 (R 挡)</span>
              <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-amber-400 font-bold">后方警示</span>
            </div>
            <span class="text-[15px] text-car-sub font-bold leading-relaxed">挂入倒车挡触发后方安全观察警示</span>
          </div>

          <div class="flex space-x-3 shrink-0 items-center">
            <button 
              @click="toggleSetting('voice_enable_gear_r')"
              :class="[
                'w-[124px] h-[120px] rounded-2xl border-2 font-black text-[20px] transition-all flex items-center justify-center select-none cursor-pointer shadow-md',
                store.vehicleAuto.voice_enable_gear_r 
                  ? 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-amber-500/10' 
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              {{ store.vehicleAuto.voice_enable_gear_r ? '开关已开' : '开关已关' }}
            </button>
            <div class="flex flex-col space-y-2.5 w-[130px]">
              <button 
                @click="testVoice('gear_r')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                试听语音
              </button>
              <button 
                @click="openCustomVoice('gear_r', '倒车挡 R')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-sub hover:text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                声效设置
              </button>
            </div>
          </div>
        </div>

        <!-- 驻车挡 P -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex flex-col justify-center pr-6 flex-1">
            <div class="flex items-center space-x-3 mb-1.5">
              <span class="text-[22px] font-black text-car-text tracking-wide">驻车挡 (P 挡)</span>
              <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-emerald-400 font-bold">停泊就绪</span>
            </div>
            <span class="text-[15px] text-car-sub font-bold leading-relaxed">挂回 P 挡播报一次并立即归零状态机</span>
          </div>

          <div class="flex space-x-3 shrink-0 items-center">
            <button 
              @click="toggleSetting('voice_enable_gear_p')"
              :class="[
                'w-[124px] h-[120px] rounded-2xl border-2 font-black text-[20px] transition-all flex items-center justify-center select-none cursor-pointer shadow-md',
                store.vehicleAuto.voice_enable_gear_p 
                  ? 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-amber-500/10' 
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              {{ store.vehicleAuto.voice_enable_gear_p ? '开关已开' : '开关已关' }}
            </button>
            <div class="flex flex-col space-y-2.5 w-[130px]">
              <button 
                @click="testVoice('gear_p')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                试听语音
              </button>
              <button 
                @click="openCustomVoice('gear_p', '驻车挡 P')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-sub hover:text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                声效设置
              </button>
            </div>
          </div>
        </div>

        <!-- 空挡 N -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex flex-col justify-center pr-6 flex-1">
            <div class="flex items-center space-x-3 mb-1.5">
              <span class="text-[22px] font-black text-car-text tracking-wide">空挡 (N 挡)</span>
              <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-car-sub font-bold">临时切空</span>
            </div>
            <span class="text-[15px] text-car-sub font-bold leading-relaxed">等待红绿灯或洗车拖车空挡提醒</span>
          </div>

          <div class="flex space-x-3 shrink-0 items-center">
            <button 
              @click="toggleSetting('voice_enable_gear_n')"
              :class="[
                'w-[124px] h-[120px] rounded-2xl border-2 font-black text-[20px] transition-all flex items-center justify-center select-none cursor-pointer shadow-md',
                store.vehicleAuto.voice_enable_gear_n 
                  ? 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-amber-500/10' 
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              {{ store.vehicleAuto.voice_enable_gear_n ? '开关已开' : '开关已关' }}
            </button>
            <div class="flex flex-col space-y-2.5 w-[130px]">
              <button 
                @click="testVoice('gear_n')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                试听语音
              </button>
              <button 
                @click="openCustomVoice('gear_n', '空挡 N')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-sub hover:text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                声效设置
              </button>
            </div>
          </div>
        </div>
      </div>
    </FeatureCard>

    <!-- 2. 功能模式切换播报 (智能模式 / 舒适模式 / 经济模式 / 运动模式) -->
    <FeatureCard 
      title="2. 功能模式切换播报 (智能模式 / 舒适模式 / 经济模式 / 运动模式)"
      desc="全车 4 大功能模式均已配置专属晓晓温婉知性原声。内置【模式有人感知状态机】：默认智能模式静默，手动切出其他模式激活，切回智能模式播报后置 0 归位，杜绝蓝牙钥匙靠近唤醒误报！"
    >
      <div class="grid grid-cols-2 gap-4">
        <!-- 智能模式 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex flex-col justify-center pr-6 flex-1">
            <div class="flex items-center space-x-3 mb-1.5">
              <span class="text-[22px] font-black text-car-text tracking-wide">智能模式 (Smart)</span>
              <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-car-accent font-bold">默认省心</span>
            </div>
            <span class="text-[15px] text-car-sub font-bold leading-relaxed">吉利默认智能模式，切回播报一次后归零静默</span>
          </div>

          <div class="flex space-x-3 shrink-0 items-center">
            <button 
              @click="toggleSetting('voice_enable_mode_smart')"
              :class="[
                'w-[124px] h-[120px] rounded-2xl border-2 font-black text-[20px] transition-all flex items-center justify-center select-none cursor-pointer shadow-md',
                store.vehicleAuto.voice_enable_mode_smart 
                  ? 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-amber-500/10' 
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              {{ store.vehicleAuto.voice_enable_mode_smart ? '开关已开' : '开关已关' }}
            </button>
            <div class="flex flex-col space-y-2.5 w-[130px]">
              <button 
                @click="testVoice('mode_smart')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                试听模式
              </button>
              <button 
                @click="openCustomVoice('mode_smart', '智能模式')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-sub hover:text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                声效设置
              </button>
            </div>
          </div>
        </div>

        <!-- 舒适模式 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex flex-col justify-center pr-6 flex-1">
            <div class="flex items-center space-x-3 mb-1.5">
              <span class="text-[22px] font-black text-car-text tracking-wide">舒适模式 (Comfort)</span>
              <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-emerald-400 font-bold">平顺温润</span>
            </div>
            <span class="text-[15px] text-car-sub font-bold leading-relaxed">适合日常城市通勤，换挡平顺温润</span>
          </div>

          <div class="flex space-x-3 shrink-0 items-center">
            <button 
              @click="toggleSetting('voice_enable_mode_comfort')"
              :class="[
                'w-[124px] h-[120px] rounded-2xl border-2 font-black text-[20px] transition-all flex items-center justify-center select-none cursor-pointer shadow-md',
                store.vehicleAuto.voice_enable_mode_comfort 
                  ? 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-amber-500/10' 
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              {{ store.vehicleAuto.voice_enable_mode_comfort ? '开关已开' : '开关已关' }}
            </button>
            <div class="flex flex-col space-y-2.5 w-[130px]">
              <button 
                @click="testVoice('mode_comfort')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                试听模式
              </button>
              <button 
                @click="openCustomVoice('mode_comfort', '舒适模式')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-sub hover:text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                声效设置
              </button>
            </div>
          </div>
        </div>

        <!-- 经济模式 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex flex-col justify-center pr-6 flex-1">
            <div class="flex items-center space-x-3 mb-1.5">
              <span class="text-[22px] font-black text-car-text tracking-wide">经济模式 (Eco)</span>
              <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-blue-400 font-bold">低碳节能</span>
            </div>
            <span class="text-[15px] text-car-sub font-bold leading-relaxed">极致节油，长途巡航舒适惬意</span>
          </div>

          <div class="flex space-x-3 shrink-0 items-center">
            <button 
              @click="toggleSetting('voice_enable_mode_eco')"
              :class="[
                'w-[124px] h-[120px] rounded-2xl border-2 font-black text-[20px] transition-all flex items-center justify-center select-none cursor-pointer shadow-md',
                store.vehicleAuto.voice_enable_mode_eco 
                  ? 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-amber-500/10' 
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              {{ store.vehicleAuto.voice_enable_mode_eco ? '开关已开' : '开关已关' }}
            </button>
            <div class="flex flex-col space-y-2.5 w-[130px]">
              <button 
                @click="testVoice('mode_eco')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                试听模式
              </button>
              <button 
                @click="openCustomVoice('mode_eco', '经济模式')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-sub hover:text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                声效设置
              </button>
            </div>
          </div>
        </div>

        <!-- 运动模式 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex flex-col justify-center pr-6 flex-1">
            <div class="flex items-center space-x-3 mb-1.5">
              <span class="text-[22px] font-black text-car-text tracking-wide">运动模式 (Sport)</span>
              <span class="text-[13px] px-2.5 py-0.5 rounded-full bg-car-card border border-car-border text-rose-500 font-bold">动力充沛</span>
            </div>
            <span class="text-[15px] text-car-sub font-bold leading-relaxed">油门激进，动力输出充沛激擎</span>
          </div>

          <div class="flex space-x-3 shrink-0 items-center">
            <button 
              @click="toggleSetting('voice_enable_mode_sport')"
              :class="[
                'w-[124px] h-[120px] rounded-2xl border-2 font-black text-[20px] transition-all flex items-center justify-center select-none cursor-pointer shadow-md',
                store.vehicleAuto.voice_enable_mode_sport 
                  ? 'bg-car-card border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-amber-500/10' 
                  : 'bg-car-card border-car-border text-car-sub hover:border-car-border-light'
              ]"
            >
              {{ store.vehicleAuto.voice_enable_mode_sport ? '开关已开' : '开关已关' }}
            </button>
            <div class="flex flex-col space-y-2.5 w-[130px]">
              <button 
                @click="testVoice('mode_sport')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                试听模式
              </button>
              <button 
                @click="openCustomVoice('mode_sport', '运动模式')"
                class="h-[55px] rounded-xl border-2 border-car-border bg-car-card text-car-sub hover:text-car-text font-black text-[16.5px] flex items-center justify-center cursor-pointer hover:border-car-border-light shadow-sm transition-all"
              >
                声效设置
              </button>
            </div>
          </div>
        </div>
      </div>
    </FeatureCard>

    <!-- 3. 四门迎宾与关门提醒 -->
    <FeatureCard 
      title="3. 四门迎宾与关门提醒 (状态翻转机 · 关门立断)"
      desc="100% 锁定吉利真实 MCU 串口物理报文 (91 02 01 b6)，彻底废除时间防抖锁。开门播报未完突然关门时，毫秒级打断开门语音并无缝切入“车门已关好”。"
    >
      <div class="grid grid-cols-2 gap-4">
        <!-- 主驾 FL -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between shadow-sm">
          <div class="text-[20px] font-black text-car-text mb-3">主驾车门 (FL)</div>
          <div class="grid grid-cols-2 gap-2.5 mb-3">
            <MatrixButton 
              title="开门防杀"
              :subtitle="store.vehicleAuto.voice_enable_door_fl ? '开关已开' : '开关已关'"
              :active="store.vehicleAuto.voice_enable_door_fl"
              @click="toggleSetting('voice_enable_door_fl')"
            />
            <MatrixButton 
              title="关门播报"
              :subtitle="store.vehicleAuto.voice_enable_door_fl_close ? '开关已开' : '开关已关'"
              :active="store.vehicleAuto.voice_enable_door_fl_close"
              @click="toggleSetting('voice_enable_door_fl_close')"
            />
          </div>
          <div class="flex space-x-3">
            <button 
              @click="testVoice('door_fl')"
              class="flex-1 min-h-[58px] bg-car-card border-2 border-car-border rounded-xl text-car-text font-black text-[17px] cursor-pointer hover:border-car-border-light shadow-sm"
            >
              试听开门语音
            </button>
            <button 
              @click="openCustomVoice('door_fl', '主驾车门')"
              class="flex-1 min-h-[58px] bg-car-card border-2 border-car-border text-car-sub hover:text-car-text font-black text-[17px] rounded-xl cursor-pointer hover:border-car-border-light shadow-sm"
            >
              ⚙️ 声效设置
            </button>
          </div>
        </div>

        <!-- 副驾 FR -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between shadow-sm">
          <div class="text-[20px] font-black text-car-text mb-3">副驾车门 (FR)</div>
          <div class="grid grid-cols-2 gap-2.5 mb-3">
            <MatrixButton 
              title="开门迎宾"
              :subtitle="store.vehicleAuto.voice_enable_door_fr ? '开关已开' : '开关已关'"
              :active="store.vehicleAuto.voice_enable_door_fr"
              @click="toggleSetting('voice_enable_door_fr')"
            />
            <MatrixButton 
              title="关门系安全带"
              :subtitle="store.vehicleAuto.voice_enable_door_fr_close ? '开关已开' : '开关已关'"
              :active="store.vehicleAuto.voice_enable_door_fr_close"
              @click="toggleSetting('voice_enable_door_fr_close')"
            />
          </div>
          <div class="flex space-x-3">
            <button 
              @click="testVoice('door_fr')"
              class="flex-1 min-h-[58px] bg-car-card border-2 border-car-border rounded-xl text-car-text font-black text-[17px] cursor-pointer hover:border-car-border-light shadow-sm"
            >
              试听开门语音
            </button>
            <button 
              @click="openCustomVoice('door_fr', '副驾车门')"
              class="flex-1 min-h-[58px] bg-car-card border-2 border-car-border text-car-sub hover:text-car-text font-black text-[17px] rounded-xl cursor-pointer hover:border-car-border-light shadow-sm"
            >
              ⚙️ 声效设置
            </button>
          </div>
        </div>

        <!-- 左后 RL -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between shadow-sm">
          <div class="text-[20px] font-black text-car-text mb-3">左后车门 (RL)</div>
          <div class="grid grid-cols-2 gap-2.5 mb-3">
            <MatrixButton 
              title="开门提醒"
              :subtitle="store.vehicleAuto.voice_enable_door_rl ? '开关已开' : '开关已关'"
              :active="store.vehicleAuto.voice_enable_door_rl"
              @click="toggleSetting('voice_enable_door_rl')"
            />
            <MatrixButton 
              title="关门已关好"
              :subtitle="store.vehicleAuto.voice_enable_door_rl_close ? '开关已开' : '开关已关'"
              :active="store.vehicleAuto.voice_enable_door_rl_close"
              @click="toggleSetting('voice_enable_door_rl_close')"
            />
          </div>
          <div class="flex space-x-3">
            <button 
              @click="testVoice('door_rl')"
              class="flex-1 min-h-[58px] bg-car-card border-2 border-car-border rounded-xl text-car-text font-black text-[17px] cursor-pointer hover:border-car-border-light shadow-sm"
            >
              试听开门语音
            </button>
            <button 
              @click="openCustomVoice('door_rl', '左后车门')"
              class="flex-1 min-h-[58px] bg-car-card border-2 border-car-border text-car-sub hover:text-car-text font-black text-[17px] rounded-xl cursor-pointer hover:border-car-border-light shadow-sm"
            >
              ⚙️ 声效设置
            </button>
          </div>
        </div>

        <!-- 右后 RR -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between shadow-sm">
          <div class="text-[20px] font-black text-car-text mb-3">右后车门 (RR)</div>
          <div class="grid grid-cols-2 gap-2.5 mb-3">
            <MatrixButton 
              title="开门提醒"
              :subtitle="store.vehicleAuto.voice_enable_door_rr ? '开关已开' : '开关已关'"
              :active="store.vehicleAuto.voice_enable_door_rr"
              @click="toggleSetting('voice_enable_door_rr')"
            />
            <MatrixButton 
              title="关门已关好"
              :subtitle="store.vehicleAuto.voice_enable_door_rr_close ? '开关已开' : '开关已关'"
              :active="store.vehicleAuto.voice_enable_door_rr_close"
              @click="toggleSetting('voice_enable_door_rr_close')"
            />
          </div>
          <div class="flex space-x-3">
            <button 
              @click="testVoice('door_rr')"
              class="flex-1 min-h-[58px] bg-car-card border-2 border-car-border rounded-xl text-car-text font-black text-[17px] cursor-pointer hover:border-car-border-light shadow-sm"
            >
              试听开门语音
            </button>
            <button 
              @click="openCustomVoice('door_rr', '右后车门')"
              class="flex-1 min-h-[58px] bg-car-card border-2 border-car-border text-car-sub hover:text-car-text font-black text-[17px] rounded-xl cursor-pointer hover:border-car-border-light shadow-sm"
            >
              ⚙️ 声效设置
            </button>
          </div>
        </div>
      </div>
    </FeatureCard>

    <!-- 4. 电动尾门与转向灯 -->
    <FeatureCard 
      title="4. 电动尾门、转向灯 360 与大灯日夜联动"
      desc="原厂电动尾门物理串口破译闭环；转向灯联动 360 全景内置车速 ≤30km/h 保护；大灯联动高德日夜模式。"
    >
      <div class="grid grid-cols-3 gap-4">
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text">原厂电动尾门 (b7)</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">升起开门提醒与闭合锁止播报</div>
          </div>
          <div class="grid grid-cols-2 gap-2.5">
            <MatrixButton 
              title="开门播报"
              :subtitle="store.vehicleAuto.voice_enable_trunk_open ? '开关已开' : '开关已关'"
              :active="store.vehicleAuto.voice_enable_trunk_open"
              @click="toggleSetting('voice_enable_trunk_open')"
            />
            <MatrixButton 
              title="关门已关"
              :subtitle="store.vehicleAuto.voice_enable_trunk_close ? '开关已开' : '开关已关'"
              :active="store.vehicleAuto.voice_enable_trunk_close"
              @click="toggleSetting('voice_enable_trunk_close')"
            />
          </div>
        </div>

        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text">转向灯联动 360 全景</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">车速 ≤30km/h 允许唤醒盲区</div>
          </div>
          <MatrixButton 
            :title="store.vehicleAuto.vehicle_turn_360_enabled ? '开关已开' : '开关已关'"
            :active="store.vehicleAuto.vehicle_turn_360_enabled"
            @click="toggleSetting('vehicle_turn_360_enabled')"
          />
        </div>

        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text">大灯联动高德日夜模式</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">隧道与黑夜自动切换防刺眼</div>
          </div>
          <MatrixButton 
            :title="store.vehicleAuto.vehicle_light_nav_enabled ? '开关已开' : '开关已关'"
            :active="store.vehicleAuto.vehicle_light_nav_enabled"
            @click="toggleSetting('vehicle_light_nav_enabled')"
          />
        </div>
      </div>
    </FeatureCard>

    <!-- 6. 全局桌面迷你悬浮胶囊 -->
    <FeatureCard 
      title="6. 全局桌面迷你悬浮胶囊 (实时状态与动态暗码)"
      desc="在车机桌面或其他应用上层常驻迷你流光胶囊，支持手指自由拖拽吸附，实时显示软件状态或今日动态工程暗码。"
    >
      <div class="grid grid-cols-2 gap-4">
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text">悬浮微胶囊总开关</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">开启后在全屏最上层常驻微型胶囊</div>
          </div>
          <MatrixButton 
            :title="store.deviceInfo.floating_enabled ? '已开启悬浮胶囊' : '已关闭悬浮胶囊'"
            :active="store.deviceInfo.floating_enabled"
            @click="toggleFloatingWindow"
          />
        </div>

        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text">胶囊内容显示模式</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">切换胶囊内展示的文字信息</div>
          </div>
          <div class="flex space-x-2">
            <button 
              @click="setFloatingMode('name')"
              :class="[
                'flex-1 min-h-[58px] rounded-xl font-black text-[16px] border-2 cursor-pointer transition-all shadow-sm',
                store.deviceInfo.floating_display_mode !== 'code' 
                  ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' 
                  : 'bg-car-card border-car-border text-car-sub'
              ]"
            >
              显示应用名
            </button>
            <button 
              @click="setFloatingMode('code')"
              :class="[
                'flex-1 min-h-[58px] rounded-xl font-black text-[16px] border-2 cursor-pointer transition-all shadow-sm',
                store.deviceInfo.floating_display_mode === 'code' 
                  ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' 
                  : 'bg-car-card border-car-border text-car-sub'
              ]"
            >
              显示动态暗码
            </button>
          </div>
        </div>
      </div>
    </FeatureCard>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';
import { store, bridge, openModal, showToast } from '../store';

const selectedModelId = ref(localStorage.getItem('geely_vehicle_model') || 'binyue_cool');

const supportedModels = [
  { id: 'binyue_cool', label: '吉利缤越 COOL (E02 / IHU516G)', name: '吉利缤越 COOL', isPrimary: true },
  { id: 'binrui_cool', label: '吉利缤瑞 COOL (E02 架构)', name: '吉利缤瑞 COOL', isPrimary: false },
  { id: 'boyue_e02', label: '吉利博越 / 豪越 (E02 SWOS)', name: '吉利博越/豪越', isPrimary: false },
  { id: 'dihao_swos', label: '吉利帝豪 (Android 9 SWOS)', name: '吉利帝豪', isPrimary: false }
];

const currentModelSpec = computed(() => {
  return supportedModels.find(m => m.id === selectedModelId.value) || supportedModels[0];
});

function selectCarModel(id) {
  selectedModelId.value = id;
  localStorage.setItem('geely_vehicle_model', id);
  bridge.call('setVehicleAutomationStringSetting', 'vehicle_target_model', id);
  showToast(`已切换至【${currentModelSpec.value.name}】专车适配协议`);
}

function toggleSetting(key) {
  const next = !store.vehicleAuto[key];
  store.vehicleAuto[key] = next;
  bridge.call('setVehicleAutomationSetting', key, next);
  showToast('设置已更新: ' + (next ? '已开启' : '已关闭'));
}

function testVoice(type) {
  bridge.call('testVehicleVoice', type);
}

function openCustomVoice(key, title, soundFile) {
  openModal('voiceItemSettings', {
    key,
    title,
    soundFile: soundFile || (key + '.mp3')
  });
}

function toggleFloatingWindow() {
  const next = !store.deviceInfo.floating_enabled;
  store.deviceInfo.floating_enabled = next;
  bridge.call('toggleFloatingWindow', next);
}

function setFloatingMode(mode) {
  store.deviceInfo.floating_display_mode = mode;
  bridge.call('setFloatingDisplayMode', mode);
}
</script>
