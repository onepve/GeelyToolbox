import { computed } from 'vue';
import { store, bridge, showToast, openModal } from '../store';

// 方控按键手势映射共享逻辑（WheelView 与 WheelGestureCard 共用，单一真源）
export function useWheelGesture() {
  const gestureList = computed(() => {
    const sec = parseFloat(((store.vehicleAuto.wheel_long_press_ms || 1500) / 1000).toFixed(1));
    return [
      { id: 'single', name: '单击', shortName: '单击' },
      { id: 'double', name: '双击', shortName: '双击' },
      { id: 'long', name: `长按 (${sec}s)`, shortName: `长按${sec}s` }
    ];
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
      play_pause: '播放/暂停',
      next_track: '下一曲',
      prev_track: '上一曲',
      mute_toggle: '静音切换',
      screen_off: '息屏休眠'
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

  return { gestureList, isCustomApp, getCustomAppName, openAppSelectModal, getActionName, getGestureAction, setGestureAction };
}
