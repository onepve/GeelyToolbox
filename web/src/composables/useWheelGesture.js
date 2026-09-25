import { computed } from 'vue';
import { store, bridge, showToast, openModal } from '../store';

// 方控按键手势映射共享逻辑（WheelView 与 WheelGestureCard 共用，单一真源）
// 动作统一定义表：下拉选择器、名称展示、切歌键三方共用，杜绝手写数组顺序混乱
const ACTION_DEFS = {
  default: { name: '保持原厂默认', sub: '不拦截原厂动作' },
  next_track: { name: '下一曲', sub: '切歌 (官方调度)' },
  prev_track: { name: '上一曲', sub: '切歌 (官方调度)' },
  open_navi: { name: '打开高德地图', sub: '一键秒切导航' },
  open_360: { name: '打开 360 全景', sub: '一键秒看盲区' },
  open_toolbox: { name: '唤醒缤越助手', sub: '一键呼出控制台' },
  play_pause: { name: '播放 / 暂停', sub: '媒体暂停或继续' },
  mute_toggle: { name: '静音切换', sub: '音乐声道静音/恢复' },
  screen_off: { name: '息屏屏保', sub: '唤起内部息屏屏保' }
};

// 各键下拉选项（统一顺序：原厂/切歌 → 高德 → 360 → 播放暂停 → 静音 → 息屏，彻底消除顺序不一致）
function getActionOptions(keyName) {
  const tail = [
    { action: 'open_navi', ...ACTION_DEFS.open_navi },
    { action: 'open_360', ...ACTION_DEFS.open_360 },
    { action: 'open_toolbox', ...ACTION_DEFS.open_toolbox },
    { action: 'play_pause', ...ACTION_DEFS.play_pause },
    { action: 'mute_toggle', ...ACTION_DEFS.mute_toggle },
    { action: 'screen_off', ...ACTION_DEFS.screen_off }
  ];
  if (keyName === 'next') return [{ action: 'next_track', ...ACTION_DEFS.next_track }, ...tail];
  if (keyName === 'prev') return [{ action: 'prev_track', ...ACTION_DEFS.prev_track }, ...tail];
  return [{ action: 'default', ...ACTION_DEFS.default }, ...tail];
}

export function useWheelGesture() {
  const isBeta = computed(() => {
    // 与安装包版本号彻底脱钩：仅当在「关于」连续点击版本号 11 次解锁内测特权后才展示双击/长按
    const isTesterUnlocked = localStorage.getItem('geely_tester_unlocked') === 'true';
    const useBetaChannel = localStorage.getItem('geely_use_beta_channel') === 'true';
    return isTesterUnlocked || useBetaChannel;
  });

  const gestureList = computed(() => {
    const list = [
      { id: 'single', name: '单击', shortName: '单击' }
    ];
    if (isBeta.value) {
      list.push({ id: 'double', name: '双击', shortName: '双击' });
      const sec = parseFloat(((store.vehicleAuto.wheel_long_press_ms || 1500) / 1000).toFixed(1));
      list.push({ id: 'long', name: `长按 (${sec}s)`, shortName: `长按${sec}s` });
    }
    return list;
  });

  function isCustomApp(actionVal) {
    return typeof actionVal === 'string' && actionVal.startsWith('app:');
  }

  function getCustomAppName(target) {
    return localStorage.getItem(`wheel_action_${target}_app_name`) || '';
  }

  function openAppSelectModal(target) {
    openModal('appSelect', { keyTarget: target });
  }

  function getActionName(act) {
    const map = {
      default: '原厂默认',
      open_360: '360全景',
      open_navi: '高德地图',
      open_toolbox: '唤醒助手',
      play_pause: '播放/暂停',
      next_track: '下一曲',
      prev_track: '上一曲',
      mute_toggle: '静音切换',
      screen_off: '息屏屏保'
    };
    if (isCustomApp(act)) return '自定义App';
    return map[act] || act;
  }

  function getGestureAction(key, gesture) {
    const pKey = `wheel_action_${key}_${gesture}`;
    if (store.vehicleAuto[pKey]) return store.vehicleAuto[pKey];
    if (gesture === 'single') {
      if (store.vehicleAuto[`wheel_action_${key}`]) return store.vehicleAuto[`wheel_action_${key}`];
      if (key === 'mode') return 'open_360';
      if (key === 'custom') return 'default';
      if (key === 'ok') return 'play_pause';
      if (key === 'next') return 'next_track';
      if (key === 'prev') return 'prev_track';
    }
    return 'default';
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

  return {
    gestureList,
    isCustomApp,
    getCustomAppName,
    openAppSelectModal,
    getActionName,
    getGestureAction,
    setGestureAction,
    getActionOptions,
    ACTION_DEFS
  };
}
