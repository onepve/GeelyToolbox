<template>
  <div class="flex flex-col space-y-6">
    <!-- 1. 全局桌面迷你悬浮胶囊 -->
    <FeatureCard 
      title="1. 全局桌面迷你悬浮胶囊 (实时状态与动态暗码)"
      desc="在车机桌面或其他应用上层常驻迷你流光胶囊，支持手指自由拖拽吸附，实时显示软件状态或今日动态工程暗码。"
    >
      <div class="grid grid-cols-2 gap-4">
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text">悬浮微胶囊总开关</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">开启后在全屏最上层常驻微型胶囊，点击秒开工具箱</div>
          </div>
          <MatrixButton 
            :title="store.deviceInfo.floating_enabled ? '已开启' : '已关闭'"
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
                'flex-1 min-h-[58px] rounded-xl font-black text-[15.5px] border-2 cursor-pointer transition-all shadow-sm whitespace-nowrap',
                store.deviceInfo.floating_display_mode === 'name' || (!store.deviceInfo.floating_display_mode)
                  ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' 
                  : 'bg-car-card border-car-border text-car-sub'
              ]"
            >
              显示应用名
            </button>
            <button 
              @click="setFloatingMode('code')"
              :class="[
                'flex-1 min-h-[58px] rounded-xl font-black text-[15.5px] border-2 cursor-pointer transition-all shadow-sm whitespace-nowrap',
                store.deviceInfo.floating_display_mode === 'code'
                  ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' 
                  : 'bg-car-card border-car-border text-car-sub'
              ]"
            >
              显示动态暗码
            </button>
            <button 
              @click="setFloatingMode('battery')"
              :class="[
                'flex-1 min-h-[58px] rounded-xl font-black text-[15.5px] border-2 cursor-pointer transition-all shadow-sm whitespace-nowrap',
                store.deviceInfo.floating_display_mode === 'battery'
                  ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/20' 
                  : 'bg-car-card border-car-border text-car-sub'
              ]"
            >
              显示电瓶电压
            </button>
          </div>
        </div>
      </div>
    </FeatureCard>

    <!-- 2. 今日工程动态暗码大卡片 -->
    <FeatureCard 
      title="2. 今日动态工程暗码 (免剪贴板 · 物理大字号直显)"
      desc="根据车载系统时钟每秒自动计算，直出高亮暗码。车机拨号界面不支持粘贴，请点击按钮打开拨号盘手动按键输入。"
    >
      <div class="grid grid-cols-2 gap-4">
        <!-- 主流+5暗码 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between">
          <div>
            <div class="text-[15px] font-bold text-car-sub">主流新固件 (月份+5 算法)</div>
            <div class="text-[28px] font-black text-car-accent mt-1 tracking-wider">
              {{ store.dynamicCodePlus5 }}
            </div>
            <div class="text-[13px] text-car-sub mt-1">适用于绝大多数 2022~2024 款吉利车机</div>
          </div>
          <button 
            @click="openDialer"
            class="h-[58px] px-6 rounded-2xl bg-car-card border-2 border-car-accent text-car-accent hover:bg-car-item font-black text-[17px] cursor-pointer shadow-md transition-all whitespace-nowrap"
          >
            📞 打开拨号盘
          </button>
        </div>

        <!-- 老固件+10暗码 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between">
          <div>
            <div class="text-[15px] font-bold text-car-sub">早期老版固件 (月份+10 算法)</div>
            <div class="text-[28px] font-black text-car-text mt-1 tracking-wider">
              {{ store.dynamicCode }}
            </div>
            <div class="text-[13px] text-car-sub mt-1">若加 5 暗码提示错误，请尝试此备选暗码</div>
          </div>
          <button 
            @click="openDialer"
            class="h-[58px] px-6 rounded-2xl bg-car-card border-2 border-car-border text-car-text hover:border-car-border-light font-black text-[17px] cursor-pointer shadow-md transition-all whitespace-nowrap"
          >
            📞 打开拨号盘
          </button>
        </div>
      </div>
    </FeatureCard>

    <!-- 3. 工具箱开机自启动与后台常驻 -->
    <FeatureCard 
      title="3. 工具箱开机自启动与后台守护"
      desc="控制车机上电开机后是否在后台静默运行工具箱。独立生效，不影响底层车身硬件监听。"
    >
      <div class="grid grid-cols-2 gap-4">
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text">开机自启静默守护</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">系统完全开机后自动拉起后台服务并展示桌面胶囊</div>
          </div>
          <MatrixButton 
            :title="store.deviceInfo.autostart ? '已开启' : '已关闭'"
            :active="store.deviceInfo.autostart"
            @click="toggleAutostart"
          />
        </div>

        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between">
          <div class="mb-3">
            <div class="text-[19px] font-black text-car-text">自启状态说明</div>
            <div class="text-[15px] text-car-sub mt-1 font-bold">如需保持悬浮窗常驻，建议保持开启</div>
          </div>
          <div class="p-3 bg-car-card border border-car-border rounded-xl text-[14px] text-car-sub font-bold leading-relaxed">
            工具箱采用轻量级前台守护进程，开机自启内存占用低于 15MB，零唤醒锁，对车机性能与蓄电池 0 损耗。
          </div>
        </div>
      </div>
    </FeatureCard>
  </div>
</template>

<script setup>
import { store, bridge, showToast } from '../store';
import FeatureCard from '../components/FeatureCard.vue';
import MatrixButton from '../components/MatrixButton.vue';

function toggleFloatingWindow() {
  const next = !store.deviceInfo.floating_enabled;
  store.deviceInfo.floating_enabled = next;
  bridge.call('toggleFloatingWindow', next);
  showToast('悬浮微胶囊已' + (next ? '开启' : '关闭'));
}

function setFloatingMode(mode) {
  store.deviceInfo.floating_display_mode = mode;
  bridge.call('setFloatingDisplayMode', mode);
  showToast('显示模式已切换');
}

function toggleAutostart() {
  const next = !store.deviceInfo.autostart;
  store.deviceInfo.autostart = next;
  bridge.call('setAutostartEnabled', next);
  showToast('开机自启动已' + (next ? '开启' : '关闭'));
}

function openDialer() {
  bridge.call('openDialer');
}
</script>
