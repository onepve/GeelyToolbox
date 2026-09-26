<template>
  <div class="flex flex-col space-y-4 shrink-0">
    <!-- 全国实时油价与调价日历 (置顶首位，车主进入系统维护秒查油价，免滑动翻页) -->
    <FeatureCard 
      title="全国实时油价与调价日历"
      desc="监测各省发改委最新最高零售限价，提供调价周期倒计时与加油算账建议。"
      helpTitle="【功能指南】全国实时油价与调价日历"
      helpText="1. 官方限价：&#10;基于国家发改委最新发布的各省成品油最高零售限价，同省同价。&#10;&#10;2. 调价倒计时：&#10;按照国家发改委 10 个工作日调价周期倒计时，提前提供涨跌预期预警。&#10;&#10;3. 省钱算账：&#10;结合缤越 COOL 45L 油箱容积自动折算加满一箱所需成本变动。" helpTip="点击可查看全国 31 省份 92#/95#/98#/柴油价格及调价详情。"
    >
      <div class="w-full flex items-center justify-between p-4 rounded-2xl bg-car-item border border-car-border">
        <div class="flex items-center space-x-4">
          <div class="flex items-center space-x-2">
            <span class="text-[26px]">⛽</span>
            <div class="flex flex-col">
              <div class="flex items-center space-x-2">
                <span class="text-[19px] font-black text-car-text">{{ currentProvinceName }}</span>
                <span class="text-[13px] px-2 py-0.5 rounded bg-car-card text-car-accent font-extrabold border border-car-accent/30">当前地区</span>
              </div>
              <span class="text-[13px] text-car-sub font-bold mt-0.5">发改委限价 · {{ store.oilPrice.nextAdjustment.lastAdjustmentDate }} 生效</span>
            </div>
          </div>

          <div class="h-10 w-px bg-car-border"></div>

          <div class="flex items-center space-x-3">
            <div class="flex flex-col">
              <span class="text-[12.5px] text-car-sub font-bold">92# 汽油</span>
              <span class="text-[20px] font-black text-car-text font-mono leading-none">¥{{ currentOilP92 }}<span class="text-[13px] font-normal text-car-sub">/L</span></span>
            </div>
            <div class="flex flex-col">
              <span class="text-[12.5px] text-car-sub font-bold">95# 汽油</span>
              <span class="text-[20px] font-black text-car-text font-mono leading-none">¥{{ currentOilP95 }}<span class="text-[13px] font-normal text-car-sub">/L</span></span>
            </div>
          </div>

          <div class="h-10 w-px bg-car-border"></div>

          <div class="flex flex-col">
            <div class="flex items-center space-x-1.5">
              <span class="text-[13px] text-rose-500 dark:text-rose-400 font-black">
                下轮预期: {{ store.oilPrice.nextAdjustment ? store.oilPrice.nextAdjustment.predictedLiter + '元/L' : '获取中...' }}
              </span>
              <span class="text-[12px] px-2 py-0.5 rounded-full bg-car-card text-rose-600 dark:text-rose-300 font-extrabold border border-car-border whitespace-nowrap">
                {{ oilDaysText }}
              </span>
            </div>
            <span class="text-[12.5px] text-car-sub font-bold mt-0.5">
              调价日: {{ store.oilPrice.nextAdjustment ? store.oilPrice.nextAdjustment.dateLabel : '同步中...' }}
            </span>
          </div>
        </div>

        <div class="flex items-center space-x-3 shrink-0">
          <button 
            @click="onRefreshOilPrice"
            :disabled="store.oilPrice.isSyncing"
            class="min-h-[56px] px-5 rounded-xl border-2 border-car-border bg-car-item text-car-text font-black text-[16px] cursor-pointer hover:border-car-border-light transition-all shadow-md flex items-center space-x-2 whitespace-nowrap"
          >
            <svg class="w-4 h-4 text-car-sub" :class="{'animate-spin text-car-accent': store.oilPrice.isSyncing}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/>
            </svg>
            <span>{{ store.oilPrice.isSyncing ? '同步中...' : '刷新油价' }}</span>
          </button>

          <button 
            @click="openModal('oilPrice')"
            class="min-h-[56px] px-6 rounded-xl border-2 border-car-accent bg-car-item text-car-accent font-black text-[16.5px] cursor-pointer hover:border-car-accent ring-2 ring-car-accent/20 transition-all shadow-md flex items-center whitespace-nowrap"
          >
            <span>全国详情 ➔</span>
          </button>
        </div>
      </div>
    </FeatureCard>

    <FloatingView />

    <!-- 系统底层维护 4 大核心功能矩阵 (2x2 网格车规左右紧凑排版，按钮上移置右加高加宽统一规格) -->
    <div class="grid grid-cols-2 gap-4">
      <!-- 1. 运行与守护日志 -->
      <div class="rounded-3xl border-2 border-car-border p-5 shadow-2xl transition-all bg-car-card flex items-center justify-between space-x-4">
        <div class="flex-1 min-w-0 flex flex-col space-y-1.5">
          <div class="flex items-center space-x-2.5">
            <span class="text-[20px] font-black text-car-text tracking-wide truncate">运行与守护日志</span>
            <HelpDot @click="openModal('confirm', {
              title: '【功能指南】运行与守护日志',
              desc: '1. 运行记录：\n自动记录车门、挡位、方向盘按键等状态，遇到功能异常时可一键导出给作者排查。\n\n2. 隐私安全保护：\n日志导出时会自动抹除车牌、手机号、行车轨迹等敏感信息，充分保护车主隐私。\n\n3. 不占存储：\n平时仅保存在临时运行内存中，不占车机存储空间，重启后自动释放。',
              tip: '日常无需理会，只有在排查问题时按需导出即可。',
              showCancel: false,
              confirmText: '我知道了'
            })" title="查看运行与守护日志说明" />
          </div>
          <div class="text-[13.5px] text-car-sub font-bold leading-relaxed line-clamp-2">
            实时采集挡位与方控信号，支持一键导出排查
          </div>
        </div>

        <button 
          @click="openLogModal"
          class="w-[185px] h-[58px] rounded-2xl border-2 border-car-border bg-car-item text-car-text hover:border-car-border-light font-black text-[15.5px] cursor-pointer transition-all shadow-sm flex items-center justify-center shrink-0 whitespace-nowrap"
        >
          <span>查看守护日志 ➔</span>
        </button>
      </div>

      <!-- 2. 车机 ADB 交互终端 -->
      <div class="rounded-3xl border-2 border-car-border p-5 shadow-2xl transition-all bg-car-card flex items-center justify-between space-x-4">
        <div class="flex-1 min-w-0 flex flex-col space-y-1.5">
          <div class="flex items-center space-x-2.5">
            <span class="text-[20px] font-black text-car-text tracking-wide truncate">车机 ADB 终端</span>
            <HelpDot @click="showAdbHelp" title="查看 ADB 总开关与失效功能说明" />
          </div>
          <div class="text-[13.5px] text-car-sub font-bold leading-relaxed line-clamp-2">
            本地特权调试交互终端；若遇其他车型无限弹调试授权窗，请关闭 ADB 总开关
          </div>
        </div>

        <div class="flex items-center space-x-2.5 shrink-0">
          <button
            @click="toggleAdbMasterSwitch"
            :class="[
              'min-h-[58px] px-3.5 rounded-2xl border-2 cursor-pointer transition-all shadow-sm flex items-center justify-center space-x-1.5 whitespace-nowrap',
              store.deviceInfo.adb_master_switch !== false
                ? 'border-car-accent bg-car-item text-car-accent'
                : 'border-amber-500/60 bg-car-item text-amber-400 hover:border-amber-400'
            ]"
          >
            <StatusDot size="sm" :color="store.deviceInfo.adb_master_switch !== false ? 'ok' : 'off'" />
            <span class="font-black text-[14px]">
              {{ store.deviceInfo.adb_master_switch !== false ? '总开关已开启' : '总开关已关闭' }}
            </span>
          </button>

          <button 
            @click="openDeepTools"
            class="min-h-[58px] px-4 rounded-2xl border-2 border-car-border bg-car-item text-car-text hover:border-car-accent font-black text-[14.5px] cursor-pointer transition-all shadow-sm flex items-center justify-center whitespace-nowrap"
          >
            <span>打开终端 ➔</span>
          </button>
        </div>
      </div>

      <!-- 3. 整车硬件冷重启控制台 -->
      <div class="rounded-3xl border-2 border-car-border p-5 shadow-2xl transition-all bg-car-card flex items-center justify-between space-x-4">
        <div class="flex-1 min-w-0 flex flex-col space-y-1.5">
          <div class="flex items-center space-x-2.5">
            <span class="text-[20px] font-black text-car-text tracking-wide truncate">整车硬件冷重启</span>
            <HelpDot @click="openModal('confirm', {
              title: '【功能指南】整车硬件冷重启',
              desc: '1. 彻底重启车机：\n像拔插钥匙一样彻底断电重启中控屏幕和底层系统，解决系统卡顿或刚改的配置没生效的问题。\n\n2. 安全提醒：\n行车过程中严禁操作！请在车辆安全停稳、挂入 P 挡后再点击重启。\n\n3. 重启耗时：\n整个过程大约 25~35 秒，期间屏幕暂时黑屏属于正常现象，请耐心等待重新点亮。',
              tip: '车辆停稳挂 P 挡后再点重启，期间黑屏请耐心等待。',
              showCancel: false,
              confirmText: '我知道了'
            })" title="查看整车硬件冷重启说明" />
          </div>
          <div class="text-[13.5px] text-car-sub font-bold leading-relaxed line-clamp-2">
            彻底断电重置 MCU 与系统，安全保护需停稳挂 P 挡
          </div>
        </div>

        <button 
          @click="confirmHardReboot"
          class="w-[185px] h-[58px] rounded-2xl border-2 border-car-border hover:border-rose-500/80 hover:text-rose-400 bg-car-item text-car-text font-black text-[15.5px] cursor-pointer transition-all shadow-sm flex items-center justify-center shrink-0 whitespace-nowrap"
        >
          <span>车机硬件冷重启 ➔</span>
        </button>
      </div>

      <!-- 4. 工具箱开机自启动与后台守护 -->
      <div class="rounded-3xl border-2 border-car-border p-5 shadow-2xl transition-all bg-car-card flex items-center justify-between space-x-4">
        <div class="flex-1 min-w-0 flex flex-col space-y-1.5">
          <div class="flex items-center space-x-2.5">
            <span class="text-[20px] font-black text-car-text tracking-wide truncate">开机自启静默守护</span>
            <HelpDot @click="openModal('confirm', {
              title: '【功能指南】工具箱开机自启动与后台守护',
              desc: '1. 开机后台自启：\n车辆打火通电后，工具箱自动在后台安静启动，让各项自动化联动功能上车就能直接用。\n\n2. 关闭说明：\n关闭后车辆通电将不再自启工具箱，完全不占用后台资源；仅在您桌面主动打开时运行。\n\n3. 推荐常开：\n默认保持常开，确保上车后一切功能运转顺畅。',
              tip: '这是自动化功能的基础开关，可按需开启或关闭。',
              showCancel: false,
              confirmText: '我知道了'
            })" title="查看开机自启守护说明" />
          </div>
          <div class="text-[13.5px] text-car-sub font-bold leading-relaxed line-clamp-2">
            通电静默运行 · 联动项上车即用；关闭不占用后台
          </div>
        </div>

        <button
          @click="toggleAutostart"
          :class="[
            'w-[185px] h-[58px] px-2 rounded-2xl border-2 cursor-pointer transition-all shadow-sm flex items-center justify-center space-x-1.5 shrink-0 whitespace-nowrap',
            store.deviceInfo.autostart
              ? 'border-car-accent bg-car-item text-car-accent'
              : 'border-car-border bg-car-item text-car-text hover:border-car-border-light'
          ]"
        >
          <StatusDot size="sm" :color="store.deviceInfo.autostart ? 'ok' : 'off'" :glow="store.deviceInfo.autostart" :glow-px="store.deviceInfo.autostart ? 6 : 0" />
          <span class="font-black text-[14px]">
            {{ store.deviceInfo.autostart ? '已启用·点击关闭' : '已关闭·点击开启' }}
          </span>
        </button>
      </div>
    </div>

    <!-- 7. 主题外观与昼夜模式 (液态玻璃 4 色 × 白夜三档) -->
    <FeatureCard 
      title="主题外观与昼夜模式"
      desc="四套液态玻璃配色任选，昼夜三档切换；顶栏太阳/月亮按钮可随时一键快捷翻转。"
      helpTitle="【功能指南】主题外观与昼夜模式"
      helpText="1. 外观配色：&#10;提供清晨蓝、琥珀棕、晨曦绿、樱语粉 4 套专属主题，随心更换。&#10;&#10;2. 昼夜模式：&#10;支持根据时间自动切换（白天明亮、晚上柔和不刺眼），也可以强制锁定白天或黑夜。&#10;&#10;3. 自动记忆：&#10;熄火重新通电后依然保持您选好的主题外观。" helpTip="可以点击顶栏的太阳/月亮图标随时快速切换白天/黑夜模式。"
    >
      <div class="w-full flex flex-col space-y-4">
        <!-- 7 个近正方形主题方块（3 昼夜档 + 4 配色），居中等距排布，杜绝扁长拉伸 -->
        <div class="flex flex-wrap justify-center items-stretch -m-2 w-full">
          <button
            v-for="m in MODES"
            :key="m"
            @click="onSetMode(m)"
            :class="[
              'm-2 w-[150px] h-[150px] rounded-3xl border-2 cursor-pointer transition-all flex flex-col items-center justify-center space-y-2.5',
              store.theme.mode === m
                ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-lg'
                : 'bg-car-card border-car-border text-car-text hover:border-car-border-light'
            ]"
          >
            <span class="text-[23px] font-black leading-none whitespace-nowrap">{{ MODE_LABELS[m] }}</span>
            <span class="text-[14px] font-bold text-car-sub leading-none whitespace-nowrap">{{ MODE_SUBS[m] }}</span>
          </button>
          <div class="hidden xl:block w-px self-stretch m-2 bg-car-border" aria-hidden="true"></div>
          <button
            v-for="p in PALETTES"
            :key="p"
            @click="onSetPalette(p)"
            :class="[
              'm-2 w-[150px] h-[150px] rounded-3xl border-2 cursor-pointer transition-all flex flex-col items-center justify-center space-y-2.5',
              store.theme.palette === p
                ? 'bg-car-item border-car-accent text-car-text ring-2 ring-car-accent/30 shadow-lg'
                : 'bg-car-card border-car-border text-car-text hover:border-car-border-light'
            ]"
          >
            <span class="w-11 h-11 rounded-full border border-black/20 shadow-md shrink-0" :style="{ background: PALETTE_DOT[p] }"></span>
            <span class="text-[23px] font-black leading-none whitespace-nowrap">{{ PALETTE_FULL[p] }}</span>
            <span class="text-[14px] font-bold text-car-sub leading-none whitespace-nowrap">{{ PALETTE_LABELS[p] }}色主题</span>
          </button>
        </div>
      </div>
    </FeatureCard>

    <!-- 启动首屏默认视图 -->
    <FeatureCard 
      title="启动首屏默认视图"
      desc="设置缤越助手每次启动时默认呈现的主功能页。配置后立即生效并持久记忆。"
      helpTitle="【功能指南】启动首屏默认视图"
      helpText="1. 智能跟随：&#10;选择「智能跟随」后，每次启动自动恢复到您上次退出时停留的功能页。&#10;&#10;2. 固定主菜单：&#10;也可以固定为方控按键、车身联动、车载语音、精选商城、车载音频、特权安装或系统维护等任意页面。&#10;&#10;3. 立即生效：&#10;点击相应项即刻完成设置并永久记忆，下次打开软件直接进入该页面。" helpTip="建议经常传歌/装软件的车主设置为「特权安装」，常玩车机设为「精选商城」。"
    >
      <div class="w-full">
        <!-- 8 宫格严丝合缝车规对称矩阵 (2行 x 4列，彻底消除缺角空缺) -->
        <div class="w-full flex flex-wrap -m-1">
          <div
            v-for="item in STARTUP_NAV_ITEMS"
            :key="item.id"
            class="p-1 w-1/4"
          >
            <button
              @click="onSelectStartupNav(item.id)"
              :class="[
                'w-full min-h-[64px] p-2 rounded-2xl border-2 cursor-pointer transition-all flex flex-col items-center justify-center space-y-1 shadow-sm',
                store.settings.startup_nav === item.id
                  ? 'bg-car-item border-car-accent text-car-accent ring-2 ring-car-accent/30 shadow-md font-black'
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

    <!-- 底部运维与安装说明 -->
    <div class="bg-car-item border border-car-border rounded-2xl p-5 text-[14.5px] text-car-sub font-bold leading-relaxed space-y-1.5 shadow-sm">
      <div class="text-[16px] text-car-text font-black mb-1 flex items-center">
        车机底层维护铁律与核心原理说明：
      </div>
      <div>• <b>冷重启原理</b>：彻底断电重启 MCU 与 Framework，彻底杜绝开门播报延迟与系统卡顿；</div>
      <div>• <b>应用商店与白名单</b>：原厂应用商店运行会破坏白名单策略导致第三方软件无法安装，必须保持冻结锁定；</div>
      <div>• <b>三权分立架构</b>：桌面胶囊自启、车身语音总开关、方控接管总开关各自独立生效，互不影响与捆绑；</div>
      <div>• <b>ADB 安全边界</b>：深度终端已做系统核心保护，严禁自行卸载系统 Framework 核心组件。</div>
    </div>
  </div>
</template>

<script setup>
import FeatureCard from '../components/FeatureCard.vue';
import StatusDot from '../components/StatusDot.vue';
import HelpDot from '../components/HelpDot.vue';
import { store, bridge, openModal, showToast, setStartupNav, refreshOilPrices } from '../store';
import { ref, computed } from 'vue';
import FloatingView from './FloatingView.vue';
import { MODES, MODE_LABELS, PALETTES, PALETTE_LABELS, PALETTE_DOT, setMode, setPalette } from '../theme/themes';
import { openAppstoreFlow } from '../utils/appstoreFreeze';
import { getDaysToAdjustment } from '../utils/oilPriceData';

// 方块副标题：昼夜档标注时段语义，配色卡标注主题全名
const MODE_SUBS = { auto: '6:00~17:59', day: '常驻白天', night: '常驻黑夜' };
const PALETTE_FULL = { blue: '清晨蓝', brown: '琥珀棕', green: '晨曦绿', pink: '樱语粉', gold: '曜石金' };

function onSetMode(v) {
  setMode(v);
  showToast(`昼夜模式：${MODE_LABELS[v] || v}`);
}

function onSetPalette(v) {
  setPalette(v);
  showToast(`已切换「${PALETTE_LABELS[v] || v}」玻璃主题`);
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
    title: '【功能指南】ADB 特权总开关与防弹窗说明',
    desc: `1. 为什么需要总开关：\n部分车型（或特定车机固件）底层 ADB 鉴权存在异常，连接 5555 端口会导致中控屏无限次、反复弹出“允许 USB 调试”授权弹窗，严重干扰正常行车。关闭此开关可彻底切断 ADB 探测与通信，从根源消除弹窗。\n\n2. ⚠️ 关闭后将失效的功能清单：\n• 车机全量应用高级管理（应用安全冻结/解冻/清空数据/静默卸载）\n• 第三方应用免签白名单（apk_verify 签名放行）\n• 整车硬件冷重启（底层掉电 reboot）\n• 车机 ADB 交互终端（命令行调试与方控按键码抓取）\n• 悬浮窗最高权限/日志读取权限一键授权\n• 倒车/切歌退出时强力终止原厂抢声音频\n\n3. ✅ 不受影响的基础核心功能：\n方控按键监听与切歌、原生蓝牙伴随播放与焦点守护、D挡/低速调起360影像、开机自启动守护、全国实时油价等均完全正常可用。`,
    tip: '日常建议保持开启；若遇车机无限弹窗请关闭此开关。',
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

// 启动首屏默认视图配置 (8 宫格：1 个智能跟随 + 7 个核心业务菜单，严丝合缝对称 2x4)
const STARTUP_NAV_ITEMS = [
  { id: 'remember', name: '智能跟随', desc: '记忆上次停留' },
  { id: 'wheel', name: '方控按键', desc: '核心改装·默认' },
  { id: 'link', name: '车身联动', desc: '360/车速' },
  { id: 'body', name: '车载语音', desc: '语音播报' },
  { id: 'store', name: '精选商城', desc: '专车应用' },
  { id: 'audio', name: '车载音频', desc: '语音/蓝牙' },
  { id: 'install', name: '特权安装', desc: '暗码/快传' },
  { id: 'system', name: '系统维护', desc: '维护/油价' }
];

function getNavName(id) {
  const map = {
    wheel: '方控按键',
    link: '车身联动',
    body: '车载语音',
    store: '精选商城',
    audio: '车载音频',
    install: '特权安装',
    system: '系统维护'
  };
  return map[id] || '方控按键';
}

function onSelectStartupNav(navId) {
  setStartupNav(navId);
}

function onRefreshOilPrice() {
  refreshOilPrices(true);
}
</script>
