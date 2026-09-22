import { reactive } from 'vue';
import { DEFAULT_NEXT_ADJUSTMENT, REGIONAL_PRICES, syncOilPrices } from '../utils/oilPriceData';

// 安全调用 JSBridge
export const bridge = {
  call(method, ...args) {
    if (window.ToolboxBridge && typeof window.ToolboxBridge[method] === 'function') {
      try {
        return window.ToolboxBridge[method](...args);
      } catch (e) {
        console.error(`JSBridge ${method} error:`, e);
      }
    } else {
      console.warn(`ToolboxBridge.${method} not found in this environment.`);
    }
    return null;
  }
};

// 全局响应式状态
export const store = reactive({
  currentNav: 'wheel',
  isNight: true,
  // 液态玻璃主题：palette=蓝/棕/绿/粉，mode=auto(跟随时间)/day/night
  theme: {
    palette: 'blue',
    mode: 'night'
  },
  toast: { show: false, msg: '', kind: 'info' },
  
  // 顶栏状态
  deviceInfo: {
    whitelist: true,
    multimedia_frozen: false,
    appstore_frozen: false,
    battery_volt: 0,
    car_ip: '127.0.0.1'
  },
  doors: {
    fl: -1,
    fr: -1,
    rl: -1,
    rr: -1,
    trunk: -1
  },
  dynamicCode: '#*190110',
  dynamicCodePlus5: '#*140110',

  // 座舱联动设置
  vehicleAuto: {
    voice_master_switch: true, // 座舱车身语音总开关 (全车总闸，优先判定)
    wheel_master_switch: true, // 方向盘方控接管总开关 (独立生效，放行米小江)
    voice_audio_channel: 'music', // 'music' | 'nav' | 'notification'
    wheel_control_mode: 'toolbox_alone', // 出厂默认控制台独立接管
    wheel_action_mute: 'open_360',
    wheel_action_mode: 'open_360',
    wheel_action_ok: 'default',
    voice_enable_gear_d: true,
    voice_enable_gear_r: true,
    voice_enable_gear_p: true,
    voice_enable_gear_n: false,
    voice_enable_mode_smart: true,
    voice_enable_mode_comfort: true,
    voice_enable_mode_eco: true,
    voice_enable_mode_sport: true,
    voice_enable_door_fl: true,
    voice_enable_door_fl_close: true,
    voice_enable_door_fr: true,
    voice_enable_door_fr_close: true,
    voice_enable_door_rl: true,
    voice_enable_door_rl_close: true,
    voice_enable_door_rr: true,
    voice_enable_door_rr_close: true,
    voice_enable_trunk_open: true,
    voice_enable_trunk_close: true,
    vehicle_gear_d_360_enabled: true,
    vehicle_speed_custom_action_enabled: false,
    vehicle_speed_custom_action_threshold: 40,
    vehicle_speed_custom_action_target: 'pkg' + ':com.autonavi.amapauto'
  },

  // 云端应用与下载
  apps: [],
  downloadProgress: {},

  // 7 大二级弹窗管理
  modals: {
    about: false,
    battery: false,
    deepTools: false,
    qrCode: false,
    blogGuide: false,
    dialer: false,
    appDetail: null,
    rabbitInstall: null,
    confirm: null,
    update: false,
    reward: false,
    welcomeDonate: false,
    log: false,
    voiceItemSettings: null,
    otaCapture: false,
    appSelect: null,
    cleanDownload: false,
    voiceThemeImport: false,
    allApps: false,
    geekInstall: false,
    oilPrice: false
  },
  
  // 全国实时油价与调价窗口状态
  oilPrice: {
    selectedProvince: (() => {
      try {
        return localStorage.getItem('geely_oil_selected_province') || '北京';
      } catch (e) {
        return '北京';
      }
    })(),
    favProvinces: (() => {
      try {
        const raw = localStorage.getItem('geely_oil_fav_provinces');
        if (raw) {
          const list = JSON.parse(raw);
          if (Array.isArray(list) && list.length > 0) {
            const withoutBeijing = list.filter(p => p !== '北京');
            return ['北京', ...withoutBeijing];
          }
        }
      } catch (e) {}
      return ['北京', '浙江', '上海', '江苏'];
    })(),
    nextAdjustment: { ...DEFAULT_NEXT_ADJUSTMENT },
    regionalPrices: { ...REGIONAL_PRICES },
    isSyncing: false,
    lastSyncTime: (() => {
      try {
        return parseInt(localStorage.getItem('geely_oil_last_sync_time') || '0', 10);
      } catch (e) {
        return 0;
      }
    })()
  },

  // 系统设置持久态
  settings: {
    autostart: true,
    floating_pill: false,
    floating_mode: 'battery', // 'name' | 'code' | 'battery'
    expert_rabbit: false,
    silent_appstore_freeze: false,
    startup_nav: (() => {
      try {
        return localStorage.getItem('geely_startup_nav') || 'wheel';
      } catch (e) {
        return 'wheel';
      }
    })(),
    last_active_nav: (() => {
      try {
        return localStorage.getItem('geely_last_active_nav') || 'wheel';
      } catch (e) {
        return 'wheel';
      }
    })()
  }
});

export function openModal(modalName, payload = null) {
  if (modalName in store.modals) {
    store.modals[modalName] = payload !== null ? payload : true;
  }
}

export function closeModal(modalName) {
  if (modalName in store.modals) {
    store.modals[modalName] = typeof store.modals[modalName] === 'boolean' ? false : null;
  }
}

export function showToast(msg, kind = 'info') {
  store.toast.msg = msg;
  store.toast.kind = kind;
  store.toast.show = true;
  clearTimeout(showToast._t);
  showToast._t = setTimeout(() => {
    store.toast.show = false;
  }, 2200);
}

export function setOilSelectedProvince(prov) {
  if (!prov) return;
  store.oilPrice.selectedProvince = prov;
  try {
    localStorage.setItem('geely_oil_selected_province', prov);
  } catch (e) {}
}

export function toggleOilFavProvince(prov) {
  if (!prov) return;
  const list = [...store.oilPrice.favProvinces];
  const idx = list.indexOf(prov);
  if (idx >= 0) {
    if (list.length <= 1) {
      showToast('至少保留 1 个常用省份', 'warn');
      return;
    }
    list.splice(idx, 1);
    showToast(`已将 ${prov} 移出常用省份`, 'info');
  } else {
    if (list.length >= 4) {
      showToast('常用省份最多收藏 4 个', 'warn');
      return;
    }
    list.push(prov);
    showToast(`已将 ${prov} 设为常用省份`, 'success');
  }
  store.oilPrice.favProvinces = list;
  try {
    localStorage.setItem('geely_oil_fav_provinces', JSON.stringify(list));
  } catch (e) {}
}

export function setStartupNav(navId) {
  store.settings.startup_nav = navId;
  try {
    localStorage.setItem('geely_startup_nav', navId);
  } catch (e) {}
  showToast('已更新启动首屏落地页', 'success');
}

export function recordActiveNav(navId) {
  if (!navId || navId === 'floating') return;
  store.settings.last_active_nav = navId;
  try {
    localStorage.setItem('geely_last_active_nav', navId);
  } catch (e) {}
}

export async function refreshOilPrices(force = false) {
  if (store.oilPrice.isSyncing) return;
  store.oilPrice.isSyncing = true;
  try {
    const res = await syncOilPrices(force);
    if (res.success && res.data) {
      if (res.data.regionalPrices) {
        Object.assign(store.oilPrice.regionalPrices, res.data.regionalPrices);
      }
      if (res.data.nextAdjustment) {
        Object.assign(store.oilPrice.nextAdjustment, res.data.nextAdjustment);
      }
      store.oilPrice.lastSyncTime = Date.now();
      if (force) {
        showToast('已同步最新全国油价与调价预测', 'success');
      }
    } else if (force) {
      showToast('网络离线，当前呈现本地权威发改委限价', 'info');
    }
  } catch (e) {
    if (force) showToast('同步请求超时，已保留现有数据', 'warn');
  } finally {
    store.oilPrice.isSyncing = false;
  }
}
