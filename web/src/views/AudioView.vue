<template>
  <div class="flex flex-col space-y-4">
    <!-- 1. 整车多媒体音源与智能回退调度 (首选置顶卡片) -->
    <FeatureCard 
      title="多媒体"
      desc="整车默认多媒体音源与智能回退调度中枢，统一管控方向盘切歌与起步车速放歌。"
      helpTitle="【功能指南】整车多媒体与优先级回退"
      helpText="1. 统一调度：&#10;方向盘切歌/播放与起步车速达标放歌，均以此处的排序链为唯一真源。&#10;&#10;2. 依次顺位回退：&#10;当首选音源不可用时（例如首选设为蓝牙，但上车未带手机或手机蓝牙断开），系统将自动按列表顺序顺位唤醒下一个已安装的播放器，避免按键打空或随机调起。&#10;&#10;3. 刷新扫描：&#10;新装了音乐软件后，点击「刷新扫描应用」即可自动收录进列表；点击「调整排序」可自由调整优先级。"
      helpTip="默认首选为手机蓝牙；若手机蓝牙未连接，按方向盘切歌或车速达标将自动顺位拉活已安装的本地音乐。"
    >
      <div class="flex flex-col space-y-4">
        <!-- 顶栏状态与控制 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-4 flex flex-wrap items-center justify-between shadow-sm">
          <div class="flex items-center space-x-3">
            <span class="w-3.5 h-3.5 rounded-full bg-car-accent shadow-[0_0_8px_var(--accent-gold)]"></span>
            <div class="flex items-center space-x-2">
              <span class="text-[17px] font-black text-car-text">当前首选音源：</span>
              <span class="text-[17px] font-black text-car-accent">{{ primaryMediaName }}</span>
              <span class="px-2 py-0.5 text-[11.5px] font-black rounded-full bg-car-card border border-car-border text-car-sub">
                {{ isReordering ? '正在调整排序' : '依次回退调度' }}
              </span>
            </div>
          </div>

          <div class="flex items-center space-x-2.5">
            <button 
              @click="toggleReordering"
              :class="[
                'min-h-[50px] px-4 rounded-xl border-2 font-black text-[14px] cursor-pointer transition-all flex items-center space-x-1.5 shadow-sm',
                isReordering
                  ? 'border-car-accent bg-car-item text-car-accent ring-2 ring-car-accent/20'
                  : 'border-car-border bg-car-card hover:border-car-border-light text-car-text'
              ]"
            >
              <span>{{ isReordering ? '完成排序' : '调整排序' }}</span>
            </button>
            <button 
              @click="rescanMediaApps"
              class="min-h-[50px] px-4 rounded-xl border border-car-border bg-car-card hover:border-car-border-light text-car-text font-black text-[14px] cursor-pointer transition-all flex items-center space-x-1.5 shadow-sm"
              :disabled="mediaScanning"
            >
              <span v-if="mediaScanning">扫描中...</span>
              <span v-else>刷新扫描应用</span>
            </button>
          </div>
        </div>

        <!-- 多媒体应用优先级流列表 -->
        <div class="space-y-2.5">
          <div 
            v-for="(item, index) in mediaAppsList" 
            :key="item.pkg"
            class="p-4 rounded-2xl bg-car-item border transition-all flex items-center justify-between shadow-sm"
            :class="[
              item.pkg === currentPrimaryPkg
                ? 'border-car-accent/80 bg-car-card ring-1 ring-car-accent/30'
                : 'border-car-border hover:border-car-border-light'
            ]"
          >
            <!-- 左侧：序号与应用信息 -->
            <div class="flex items-center space-x-3.5 min-w-0">
              <div 
                class="w-10 h-10 rounded-xl flex items-center justify-center font-black text-[15px] shrink-0 border shadow-sm"
                :class="[
                  index === 0
                    ? 'bg-car-accent text-slate-950 border-car-accent'
                    : 'bg-car-card text-car-sub border-car-border'
                ]"
              >
                No.{{ index + 1 }}
              </div>
              <div class="flex flex-col min-w-0">
                <div class="flex items-center space-x-2">
                  <span class="text-[17px] font-black text-car-text truncate">{{ item.name }}</span>
                  <span 
                    v-if="item.pkg === 'com.android.bluetooth'" 
                    class="px-2 py-0.5 text-[11px] rounded bg-emerald-500/15 border border-emerald-500/40 text-emerald-400 font-bold shrink-0"
                  >
                    手机无线推流
                  </span>
                  <span 
                    v-else 
                    class="px-2 py-0.5 text-[11px] rounded bg-car-card border border-car-border text-car-sub font-bold shrink-0"
                  >
                    车机本地应用
                  </span>
                </div>
                <span class="text-[12.5px] font-mono text-car-sub truncate mt-0.5">{{ item.pkg }}</span>
              </div>
            </div>

            <!-- 右侧操作区 -->
            <div class="flex items-center space-x-2 shrink-0">
              <!-- 排序模式下：上移 / 下移按钮 -->
              <template v-if="isReordering">
                <button 
                  @click="movePriority(index, -1)"
                  :disabled="index === 0"
                  class="h-[50px] px-3.5 rounded-xl border border-car-border bg-car-card text-car-text font-black text-[13px] disabled:opacity-30 disabled:cursor-not-allowed hover:border-car-accent cursor-pointer transition-all shadow-sm"
                >
                  ↑ 上移
                </button>
                <button 
                  @click="movePriority(index, 1)"
                  :disabled="index === mediaAppsList.length - 1"
                  class="h-[50px] px-3.5 rounded-xl border border-car-border bg-car-card text-car-text font-black text-[13px] disabled:opacity-30 disabled:cursor-not-allowed hover:border-car-accent cursor-pointer transition-all shadow-sm"
                >
                  ↓ 下移
                </button>
              </template>

              <!-- 正常模式下：设为首选 / 首选标签 -->
              <template v-else>
                <div 
                  v-if="item.pkg === currentPrimaryPkg" 
                  class="h-[50px] px-4 rounded-xl bg-car-item border border-car-accent text-car-accent font-black text-[13.5px] flex items-center space-x-1 shadow-sm select-none"
                >
                  <span>✓ 默认首选</span>
                </div>
                <button 
                  v-else 
                  @click="setAsPrimary(item)"
                  class="h-[50px] px-4 rounded-xl border border-car-border bg-car-card hover:border-car-accent text-car-sub hover:text-car-text font-black text-[13.5px] cursor-pointer transition-all shadow-sm"
                >
                  设为首选
                </button>
              </template>
            </div>
          </div>
        </div>

        <!-- 底部提示 -->
        <div class="pt-3 border-t border-car-border/50 flex flex-wrap items-center justify-between text-car-sub">
          <div class="text-[13px] font-bold">
            调度策略：车速达标或按方向盘切歌时，依次尝试上述列表中的音源；若手机蓝牙未连接，毫秒级顺位唤醒本地播放器。
          </div>
        </div>
      </div>
    </FeatureCard>

    <!-- 2. 车载蓝牙音频与网络互联 (原卡片向下顺移) -->
    <FeatureCard 
      title="车载音频与网络通道"
      desc="直控车机蓝牙与 Wi-Fi 开关，实时呈现 6 号蓝牙硬件声道仲裁与推流状态，提供一键强制声道选通与发声调试。"
      helpTitle="【功能指南】车载蓝牙音频与网络互联"
      helpText="1. 蓝牙与 Wi-Fi 车规直控：&#10;一键启闭车机蓝牙与 Wi-Fi 无线网络，实时呈现连接设备状态、热点名称与局域网 IP 分配信息。&#10;&#10;2. 原厂硬件无缝联动：&#10;底层已全面固化缤越 COOL 最佳车规音频路由（通话优先与硬件级压音），手机蓝牙音乐与微信语音并发时自动智能避让。"
      helpTip="手机无声时先点「测试发声」确认喇叭，再点「一键强制选通蓝牙声道」。"
    >
      <div class="flex flex-col space-y-4">
        <!-- 上层：蓝牙与 Wi-Fi 硬件连接看板 (双列对称大卡片) -->
        <div class="grid grid-cols-2 gap-4">
          <!-- 蓝牙控制看板 -->
          <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between space-y-3 shadow-sm">
            <div class="flex items-center justify-between">
              <div class="flex items-center space-x-2.5">
                <StatusDot size="md" :color="connStatus.bluetooth_enabled ? 'ok' : 'off'" :glow-px="8" />
                <span class="text-[18px] font-black text-car-text">车机蓝牙</span>
              </div>
              <button 
                @click="toggleBluetooth"
                :class="[
                  'min-h-[50px] px-4 rounded-xl border-2 text-[14px] font-black cursor-pointer transition-all',
                  connStatus.bluetooth_enabled ? 'border-emerald-500/60 bg-emerald-500/15 text-emerald-400' : 'border-car-border bg-car-item text-car-sub'
                ]"
              >
                {{ connStatus.bluetooth_enabled ? '已开启' : '已关闭' }}
              </button>
            </div>
            <div class="text-[14.5px] font-mono text-car-sub truncate">
              设备: {{ connStatus.bluetooth_device_name || '未连接设备' }}
            </div>
            <div class="pt-4 border-t border-car-border/50 flex flex-wrap items-center justify-between space-x-2">
              <span class="text-[12.5px] text-car-sub font-bold">配对与连接管理</span>
              <button 
                @click="openBluetoothSettings"
                class="min-h-[50px] px-4 rounded-xl border border-car-border bg-car-item hover:border-car-border-light text-car-text text-[13.5px] font-bold cursor-pointer"
              >
                打开蓝牙设置 ➔
              </button>
            </div>
          </div>

          <!-- Wi-Fi 控制看板 -->
          <div class="bg-car-item border border-car-border rounded-2xl p-5 flex flex-col justify-between space-y-3 shadow-sm">
            <div class="flex items-center justify-between">
              <div class="flex items-center space-x-2.5">
                <StatusDot size="md" :color="connStatus.wifi_enabled ? 'ok' : 'off'" :glow-px="8" />
                <span class="text-[18px] font-black text-car-text">车机 Wi-Fi</span>
              </div>
              <button 
                @click="toggleWifi"
                :class="[
                  'min-h-[50px] px-4 rounded-xl border-2 text-[14px] font-black cursor-pointer transition-all',
                  connStatus.wifi_enabled ? 'border-emerald-500/60 bg-emerald-500/15 text-emerald-400' : 'border-car-border bg-car-item text-car-sub'
                ]"
              >
                {{ connStatus.wifi_enabled ? '已开启' : '已关闭' }}
              </button>
            </div>
            <div class="text-[14.5px] font-mono text-car-sub truncate">
              热点: {{ connStatus.wifi_ssid || '未连接热点' }} ({{ connStatus.car_ip || '127.0.0.1' }})
            </div>
            <div class="pt-4 border-t border-car-border/50 flex flex-wrap items-center justify-between space-x-2">
              <span class="text-[12.5px] text-car-sub font-bold">热点连接向导</span>
              <button 
                @click="openWifiSettings"
                class="min-h-[50px] px-4 rounded-xl border border-car-border bg-car-item hover:border-car-border-light text-car-text text-[13.5px] font-bold cursor-pointer"
              >
                打开 Wi-Fi 设置 ➔
              </button>
            </div>
          </div>
        </div>

        
      </div>
    </FeatureCard>
    <!-- 2. 车载专属语音主题包与自定义音效 -->
    <FeatureCard 
      title="车载语音主题包"
      helpId="voice_theme_mgr"
      helpTitle="【功能指南】车载专属语音主题包"
      helpText="1. 整套换装：&#10;支持导入车规级 ZIP 语音包，一键整套替换全车播报音色与音效。注意：包内每个音频开头必须自带 ≥280ms 静音（否则车机功放建立通道会吞掉第一个字），详见模板包 README。&#10;&#10;2. 物理隔离：&#10;所有语音主题独立保存在 /sdcard/GeelyPilot/voices/ 专属目录，与下载目录完全隔离，清空 Download 目录绝不影响已导入主题。&#10;&#10;3. 出厂兜底：&#10;未导入任何主题时自动使用出厂官方晓晓原声；导入的主题若有音频缺失项，自动补齐兜底，确保零丢失。&#10;&#10;4. 强制重装原声：&#10;App 升级后如果听到的还是旧声音，点一下「强制重装原声」，就会把车上旧的原声文件全部删掉，用车机里最新版的原声重新覆盖一遍，并自动切回出厂官方原声。"
      helpTip="导入前请确认 ZIP 为车规语音包结构，删除主题会自动恢复出厂原声；升级后声音没换成新的，点「强制重装原声」即可。"
    >
      <div class="flex flex-col space-y-4">
        <!-- 状态与快捷操作顶栏 -->
        <div class="bg-car-item border border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
          <div class="flex items-center space-x-3.5">
            <span class="w-3.5 h-3.5 rounded-full bg-car-accent shadow-[0_0_8px_var(--accent-gold)]"></span>
            <div class="flex flex-col">
              <div class="flex items-center space-x-2">
                <span class="text-[18px] font-black text-car-text">当前整套音效：</span>
                <span class="text-[18px] font-black text-car-accent">{{ activeThemeName ? activeThemeName : '出厂官方原声 (晓晓温婉知性)' }}</span>
                <span class="px-2.5 py-0.5 rounded-full bg-car-item border border-car-border text-car-text text-[12px] font-black inline-flex items-center shadow-sm">
                  <StatusDot class="mr-1.5" size="xs" color="ok" />
                  {{ activeThemeName ? '自定义主题' : '系统默认' }}
                </span>
              </div>
              <span class="text-[14px] text-car-sub font-bold mt-1">
                {{ activeThemeName ? `专属目录: /sdcard/GeelyPilot/voices/${activeThemeName}/` : '吉利智驾出厂原声 · 未包含项自动补齐兜底' }}
              </span>
            </div>
          </div>

          <div class="flex items-center space-x-3 shrink-0">
            <button 
              @click="openModal('voiceThemeImport')"
              class="min-h-[56px] px-6 bg-car-card border-2 border-car-accent text-car-text font-black text-[17px] rounded-xl cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 shadow-md transition-all flex items-center space-x-2"
            >
              <span>导入语音包 (.zip)</span>
            </button>
            <button 
              @click="loadVoiceThemes"
              class="min-h-[56px] px-5 bg-car-card border-2 border-car-border text-car-text font-black text-[16px] rounded-xl cursor-pointer hover:border-car-border-light shadow-sm transition-all"
            >
              刷新列表
            </button>
          </div>
        </div>

        <!-- 主题包列表卡片流 -->
        <div class="flex flex-col space-y-3">
          <!-- 默认出厂主题卡片 -->
          <div class="bg-car-item border-2 border-car-border rounded-2xl p-5 flex items-center justify-between shadow-sm">
            <div class="flex-1 min-w-0 pr-6 flex flex-col space-y-1">
              <div class="flex items-center space-x-2.5">
                <span class="text-[19px] font-black text-car-text">出厂官方原声 (晓晓温婉知性)</span>
                <span class="px-2.5 py-0.5 rounded-md bg-car-card border border-car-border text-car-sub text-[12px] font-black">系统内置</span>
                <span v-if="!activeThemeName" class="px-2.5 py-0.5 rounded-md bg-car-item border border-car-border text-car-text text-[12px] font-black inline-flex items-center shadow-sm"><span class="w-2 h-2 rounded-full mr-1.5 bg-car-accent shadow-[0_0_6px_var(--accent-gold)]"></span>正在生效</span>
              </div>
              <span class="text-[14.5px] text-car-sub font-bold">
                吉利原车温婉知性原声，端庄舒缓温润。零音频丢失，全场景兜底保障。
              </span>
            </div>

            <div class="flex items-center space-x-3 shrink-0">
              <button 
                @click="testThemeVoice('')"
                class="h-[52px] px-6 bg-car-card border-2 border-car-border text-car-text font-black text-[16px] rounded-xl hover:border-car-border-light cursor-pointer shadow-sm transition-all"
              >
                试听样音
              </button>
              <button
                @click="confirmForceRestoreFactory"
                class="h-[52px] px-6 bg-car-card border-2 border-car-border text-car-accent font-black text-[16px] rounded-xl hover:border-car-accent cursor-pointer shadow-sm transition-all"
              >
                强制重装原声
              </button>
              <button
                v-if="activeThemeName"
                @click="applyVoiceTheme('')"
                class="h-[52px] px-6 bg-car-card border-2 border-car-accent text-car-accent font-black text-[16px] rounded-xl hover:border-car-accent ring-2 ring-car-accent/20 cursor-pointer shadow-md transition-all"
              >
                恢复此原声
              </button>
              <div 
                v-else
                class="h-[52px] px-5 bg-emerald-500/15 border-2 border-emerald-500/50 text-emerald-400 font-black text-[15px] rounded-xl flex items-center justify-center space-x-1.5 shadow-sm select-none"
              >
                <StatusDot size="xs" color="okBright" />
                <span>正在生效</span>
              </div>
            </div>
          </div>

          <!-- 用户导入的各语音主题包 -->
          <div 
            v-for="theme in voiceThemes" 
            :key="theme.id"
            class="bg-car-item border-2 rounded-2xl p-5 flex items-center justify-between shadow-sm transition-all"
            :class="activeThemeName === theme.name ? 'border-car-accent ring-2 ring-car-accent/20' : 'border-car-border'"
          >
            <div class="flex-1 min-w-0 pr-6 flex flex-col space-y-1">
              <div class="flex items-center space-x-2.5">
                <span class="text-[19px] font-black text-car-text truncate">{{ theme.name }}</span>
                <span class="px-2.5 py-0.5 rounded-md bg-car-card border border-car-border text-car-accent text-[12px] font-black shrink-0">
                  包含 {{ theme.count }} 个音频
                </span>
                <span v-if="activeThemeName === theme.name" class="px-2.5 py-0.5 rounded-md bg-car-item border border-car-border text-car-text text-[12px] font-black shrink-0 inline-flex items-center shadow-sm"><span class="w-2 h-2 rounded-full mr-1.5 bg-car-accent shadow-[0_0_6px_var(--accent-gold)]"></span>正在整套生效</span>
              </div>
              <span class="text-[14.5px] text-car-sub font-mono font-bold truncate">
                目录: {{ theme.path }}
              </span>
            </div>

            <div class="flex items-center space-x-3 shrink-0">
              <button 
                @click="testThemeVoice(theme.name)"
                class="h-[52px] px-5 bg-car-card border-2 border-car-border text-car-text font-black text-[16px] rounded-xl hover:border-car-border-light cursor-pointer shadow-sm transition-all"
              >
                试听样音
              </button>
              <button 
                v-if="activeThemeName !== theme.name"
                @click="applyVoiceTheme(theme.name)"
                class="h-[52px] px-6 bg-car-card border-2 border-car-accent text-car-text font-black text-[16px] rounded-xl hover:border-car-accent ring-2 ring-car-accent/20 cursor-pointer shadow-md transition-all"
              >
                整套启用
              </button>
              <div 
                v-else
                class="h-[52px] px-5 bg-emerald-500/15 border-2 border-emerald-500/50 text-emerald-400 font-black text-[15px] rounded-xl flex items-center justify-center space-x-1.5 shadow-sm select-none"
              >
                <StatusDot size="xs" color="okBright" />
                <span>正在生效</span>
              </div>
              <button 
                @click="confirmDeleteTheme(theme.name)"
                class="h-[52px] px-4 bg-car-card border-2 border-car-border hover:border-car-border-light text-car-text hover:text-rose-400 font-black text-[15px] rounded-xl cursor-pointer shadow-sm transition-all"
              >
                删除
              </button>
            </div>
          </div>
        </div>
      </div>
    </FeatureCard>

    </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import FeatureCard from '../components/FeatureCard.vue';
import StatusDot from '../components/StatusDot.vue';
import { store, bridge, openModal, showToast } from '../store';




const ttsInfo = ref({
  connected: true,
  name: '系统默认语音合成引擎',
  status: '已连接系统底层默认语音引擎 · 声线就绪'
});

// ================= 多媒体音源管理与优先级调度 =================
const isReordering = ref(false);
const mediaScanning = ref(false);
const mediaAppsList = ref([
  { name: '手机蓝牙', pkg: 'com.android.bluetooth', isBluetooth: true }
]);

const currentPrimaryPkg = computed(() => {
  return store.vehicleAuto.vehicle_speed_autoplay_pkg || 'com.android.bluetooth';
});

const primaryMediaName = computed(() => {
  const target = mediaAppsList.value.find(item => item.pkg === currentPrimaryPkg.value);
  if (target) return target.name;
  if (currentPrimaryPkg.value === 'com.android.bluetooth') return '手机蓝牙';
  if (currentPrimaryPkg.value === 'com.tencent.qqmusiccar') return 'QQ音乐车机版';
  if (currentPrimaryPkg.value === 'com.netease.cloudmusiccar') return '网易云音乐车机版';
  return currentPrimaryPkg.value || '手机蓝牙';
});

function getSavedMusicOrder() {
  try {
    const raw = localStorage.getItem('preferred_music_apps_order');
    if (raw) return JSON.parse(raw);
  } catch (e) {}
  return [];
}

function saveCurrentOrder(order) {
  localStorage.setItem('preferred_music_apps_order', JSON.stringify(order));
  try {
    bridge.call('setWheelControlStringSetting', 'preferred_music_apps_order', JSON.stringify(order));
  } catch (e) {}
  window.dispatchEvent(new CustomEvent('music-order-updated'));
}

function loadMediaApps() {
  mediaScanning.value = true;
  try {
    let raw = bridge.call('getInstalledMusicAppsJson');
    if (!raw || raw === '[]') {
      raw = bridge.call('getInstalledLaunchableApps');
    }
    let list = [];
    if (raw) {
      const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
      list = parsed.map(item => ({
        name: item.name || item.appName || item.pkg,
        pkg: item.pkg || item.packageName,
        isBluetooth: false
      })).filter(item => item.pkg !== 'com.android.bluetooth');
    }
    
    // 手机蓝牙常驻候选池
    const all = [{ name: '手机蓝牙', pkg: 'com.android.bluetooth', isBluetooth: true }, ...list];
    
    // 依据用户自定义顺序排序
    const savedOrder = getSavedMusicOrder();
    if (savedOrder && savedOrder.length > 0) {
      all.sort((a, b) => {
        let ia = savedOrder.indexOf(a.pkg);
        let ib = savedOrder.indexOf(b.pkg);
        if (ia === -1) ia = 999;
        if (ib === -1) ib = 999;
        return ia - ib;
      });
    }
    mediaAppsList.value = all;
    
    // 若当前未配置首选，自动同步第一项（默认手机蓝牙）
    if (!store.vehicleAuto.vehicle_speed_autoplay_pkg && all.length > 0) {
      store.vehicleAuto.vehicle_speed_autoplay_pkg = all[0].pkg;
    }
  } catch (e) {
    mediaAppsList.value = [{ name: '手机蓝牙', pkg: 'com.android.bluetooth', isBluetooth: true }];
  } finally {
    mediaScanning.value = false;
  }
}

function rescanMediaApps() {
  mediaScanning.value = true;
  try {
    bridge.call('refreshInstalledApps');
  } catch (e) {}
  showToast('正在全仓重新扫描整车媒体应用...');
  setTimeout(() => {
    loadMediaApps();
    showToast('媒体应用列表与音源已更新');
  }, 400);
}

function toggleReordering() {
  isReordering.value = !isReordering.value;
  if (!isReordering.value) {
    showToast('多媒体音源优先级已保存生效');
  }
}

function movePriority(index, delta) {
  const targetIndex = index + delta;
  if (targetIndex < 0 || targetIndex >= mediaAppsList.value.length) return;
  const list = [...mediaAppsList.value];
  const temp = list[index];
  list[index] = list[targetIndex];
  list[targetIndex] = temp;
  mediaAppsList.value = list;

  const newOrder = list.map(item => item.pkg);
  saveCurrentOrder(newOrder);

  // 第一项自动同步为整车首选
  if (list.length > 0) {
    const first = list[0];
    store.vehicleAuto.vehicle_speed_autoplay_pkg = first.pkg;
    try {
      bridge.call('setWheelControlStringSetting', 'vehicle_speed_autoplay_pkg', first.pkg);
      localStorage.setItem('vehicle_speed_autoplay_app_name', first.name);
    } catch (e) {}
  }
}

function setAsPrimary(item) {
  const list = mediaAppsList.value.filter(a => a.pkg !== item.pkg);
  list.unshift(item);
  mediaAppsList.value = list;

  store.vehicleAuto.vehicle_speed_autoplay_pkg = item.pkg;
  try {
    bridge.call('setWheelControlStringSetting', 'vehicle_speed_autoplay_pkg', item.pkg);
    localStorage.setItem('vehicle_speed_autoplay_app_name', item.name);
  } catch (e) {}

  const newOrder = list.map(a => a.pkg);
  saveCurrentOrder(newOrder);
  showToast(`整车首选音源已设为: ${item.name}`);
}

const connStatus = ref({
  bluetooth_enabled: false,
  bluetooth_connected: false,
  bluetooth_device_name: '未连接设备',
  wifi_enabled: false,
  wifi_connected: false,
  wifi_ssid: '未连接热点',
  car_ip: '127.0.0.1',
  eas_channel_active: false
});

function refreshConnectivity() {
  try {
    const raw = bridge.call('getConnectivityStatus');
    if (raw) {
      connStatus.value = typeof raw === 'string' ? JSON.parse(raw) : raw;
    }
  } catch (e) {}
}

function toggleBluetooth() {
  const next = !connStatus.value.bluetooth_enabled;
  connStatus.value.bluetooth_enabled = next;
  bridge.call('toggleBluetooth', next);
  setTimeout(refreshConnectivity, 1500);
}

// 原厂系统设置页入口：Java 侧已改为 UI 线程直启（不再卡 JS 线程）+ 显式指定唯一组件
// （消除系统「应用选择器」）+ 900ms 连点防抖。此处再加一道前端闸门：
// 旧版正是「点了没反应 → 车主再点一次 → 两个窗口叠出来」，闸门期间给即时反馈，
// 让车主不必、也无法重复点击。
let settingsLaunchLockUntil = 0;
function launchSystemSettingsPage(kind) {
  const now = Date.now();
  if (now < settingsLaunchLockUntil) return;   // 闸门内重复点击直接吞掉
  settingsLaunchLockUntil = now + 900;
  bridge.call(kind === 'bluetooth' ? 'openBluetoothSettings' : 'openWifiSettings');
  showToast(kind === 'bluetooth' ? '正在打开原厂系统蓝牙设置...' : '正在打开原厂系统 Wi-Fi 设置...');
}

function openBluetoothSettings() {
  launchSystemSettingsPage('bluetooth');
}

function toggleWifi() {
  const next = !connStatus.value.wifi_enabled;
  connStatus.value.wifi_enabled = next;
  bridge.call('toggleWifi', next);
  setTimeout(refreshConnectivity, 1500);
}

function openWifiSettings() {
  launchSystemSettingsPage('wifi');
}

function forceActivateBluetooth() {
  bridge.call('forceActivateBluetoothChannel');
  refreshConnectivity();
}

function testBluetoothAudio() {
  bridge.call('testBluetoothAudio');
}

const voiceThemes = ref([]);
const activeThemeName = ref('');

function loadVoiceThemes() {
  try {
    const raw = bridge.call('getVoiceThemesJson');
    if (raw) {
      const data = typeof raw === 'string' ? JSON.parse(raw) : raw;
      activeThemeName.value = data.activeTheme || '';
      voiceThemes.value = data.themes || [];
    }
  } catch (e) {
    voiceThemes.value = [];
  }
}

function applyVoiceTheme(themeName) {
  bridge.call('setActiveVoiceTheme', themeName);
  activeThemeName.value = themeName;
  loadVoiceThemes();
}

// 强制重装官方原声：App 升级后若仍听到旧原声，一键清掉车上旧文件、用新版原声全量覆盖
function confirmForceRestoreFactory() {
  openModal('confirm', {
    title: '强制重装官方原声',
    desc: '会把车上旧的原声文件全部删掉，用车机里最新版的原声重新覆盖一遍，并自动切回出厂官方原声。',
    tip: '正在使用的自定义语音包会被取消生效，但文件不会被删除，随时可以再整套启用。',
    confirmText: '立即重装原声',
    onConfirm: () => {
      const count = bridge.call('forceRestoreFactoryVoice');
      if (typeof count === 'number' && count >= 0) {
        showToast(`已强制重装原声，更新 ${count} 个音频文件`);
      } else {
        showToast('原声重装失败，请稍后重试', 'error');
      }
      loadVoiceThemes();
    }
  });
}

function testThemeVoice(themeName) {
  if (!themeName) {
    bridge.call('testVehicleVoice', 'gear_d');
  } else {
    bridge.call('playCustomAudioPath', `/sdcard/GeelyPilot/voices/${themeName}/gear_d.mp3`);
  }
}

function confirmDeleteTheme(themeName) {
  openModal('confirm', {
    title: `删除语音包【${themeName}】`,
    message: `确定要彻底删除该语音包吗？\n删除后将释放其占用的存储空间，若正在生效将自动恢复为出厂晓晓原声。`,
    isDanger: true,
    onConfirm: () => {
      bridge.call('deleteVoiceTheme', themeName);
      loadVoiceThemes();
    }
  });
}

// 切页首帧优先：桥调用一律延后到首帧绘制之后执行。
// getTtsEngineInfo（内部 checkAndReloadTtsIfNeeded 要读系统首选 TTS 引擎）与
// getConnectivityStatus（内部 5+ 次蓝牙/WiFi binder IPC，含昂贵的 getBondedDevices）
// 都是同步 JSBridge，写在 onMounted 里会阻塞「本页首次绘制」，
// 车主观感就是「点一下语音，要等一小会才切过去」。延后一帧后画面先出、数据随后到。
function afterFirstPaint(fn) {
  if (typeof requestAnimationFrame === 'function') {
    requestAnimationFrame(() => setTimeout(fn, 0));
  } else {
    setTimeout(fn, 0);
  }
}

// 轮询句柄必须持有：本页由 App.vue 的 v-if 销毁重建，若不清理，
// 每进一次「语音」页就永久多留一条 4 秒同步跨端轮询（越用越卡的实测铁证）。
let connectivityTimer = null;

onMounted(() => {
  afterFirstPaint(() => {
    loadMediaApps();
    loadVoiceThemes();
    window.refreshVoiceThemes = loadVoiceThemes;
    refreshConnectivity();
    if (connectivityTimer) clearInterval(connectivityTimer);
    connectivityTimer = setInterval(refreshConnectivity, 4000);
  });
});

onBeforeUnmount(() => {
  if (connectivityTimer) {
    clearInterval(connectivityTimer);
    connectivityTimer = null;
  }
});
</script>
