import { reactive } from 'vue';

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
  toast: { show: false, msg: '' },
  
  // 顶栏状态
  deviceInfo: {
    whitelist: true,
    multimedia_frozen: false,
    appstore_frozen: false,
    battery_volt: 126,
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
    wheel_control_mode: 'carmedia_first',
    wheel_action_mute: 'open_360',
    wheel_action_mode: 'open_360',
    wheel_action_ok: 'default',
    voice_enable_gear_d: true,
    voice_enable_gear_r: true,
    voice_enable_gear_p: true,
    voice_enable_gear_n: true,
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
    vehicle_turn_360_enabled: false,
    vehicle_light_nav_enabled: false
  },

  // 云端应用与下载
  apps: [],
  downloadProgress: {},

  // 7 大二级弹窗管理
  modals: {
    settings: false,
    about: false,
    battery: false,
    deepTools: false,
    qrCode: false,
    dialer: false,
    appDetail: null,
    rabbitInstall: null,
    customVoiceText: null,
    confirm: null,
    update: false,
    reward: false,
    log: false,
    voiceItemSettings: null,
    otaCapture: false,
    appSelect: null
  },
  
  // 系统设置持久态
  settings: {
    autostart: false,
    floating_pill: false,
    floating_mode: 'title', // 'title' | 'code'
    expert_rabbit: false
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

export function showToast(msg) {
  store.toast.msg = msg;
  store.toast.show = true;
  setTimeout(() => {
    store.toast.show = false;
  }, 2200);
}
