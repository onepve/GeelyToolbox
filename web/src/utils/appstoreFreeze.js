// 应用商店冻结/解冻 统一主控（全应用唯一实现）
// 所有入口（顶部胶囊 / 系统维护两卡 / ADB 深度工具箱冻结卡）一律调用此处，
// 文案与二次确认只维护这一份 —— 冻结文案以系统维护「一键安全冻结」描述为基准。
import { store, bridge, openModal, showToast } from '../store';

const STORE_PKG = 'com.ecarx.appstore';

export function isAppstoreFrozen() {
  if (typeof store.deviceInfo?.appstore_frozen === 'boolean') {
    return store.deviceInfo.appstore_frozen;
  }
  try {
    return !!bridge.call('isPackageFrozen', STORE_PKG);
  } catch (e) {
    return false;
  }
}

function execFreeze(freeze, onDone) {
  bridge.call('toggleFreezeAppStore', freeze);
  showToast(freeze ? '正在执行应用商店安全冻结...' : '正在解冻恢复吉利应用商店...');
  if (typeof onDone === 'function') {
    setTimeout(onDone, 600);
    setTimeout(onDone, 1800);
  }
}

// 统一冻结流程：已冻结 → 状态提示；未冻结 → 二次确认后执行
export function openAppstoreFreezeFlow(onDone) {
  if (store.deviceInfo?.adb_master_switch === false) {
    showToast('ADB 总开关已关闭，该功能无法使用');
    return;
  }
  if (isAppstoreFrozen()) {
    showToast('吉利应用商店当前处于安全冻结状态，白名单已锁定 (๑•̀ㅂ•́)و');
    return;
  }
  openModal('confirm', {
    title: '冻结吉利应用商店 (锁定白名单)',
    desc: '冻结吉利原厂应用商店后，将永久锁定第三方软件安装白名单，确保第三方应用能够顺利安装并正常运行。',
    tip: '【强烈建议】请始终保持应用商店冻结状态。后续需要时可随时在此解冻恢复。',
    isDanger: false,
    confirmText: '确认安全冻结',
    cancelText: '取消',
    onConfirm: () => execFreeze(true, onDone)
  });
}

// 统一解冻流程：高危二次警告后执行
export function openAppstoreUnfreezeFlow(onDone) {
  if (store.deviceInfo?.adb_master_switch === false) {
    showToast('ADB 总开关已关闭，该功能无法使用');
    return;
  }
  openModal('confirm', {
    title: '解冻恢复吉利应用商店',
    desc: '解冻原厂应用商店后，商店将破坏系统白名单策略，直接导致第三方软件无法安装，且已有软件可能被后台静默拦截。',
    tip: '【高危警告】解冻后将直接影响白名单导致无法安装第三方软件！仅在急需使用官方商店时临时开启。',
    isDanger: true,
    confirmText: '确认解冻',
    cancelText: '取消',
    onConfirm: () => execFreeze(false, onDone)
  });
}

// 统一双向入口：按当前状态自动分流（冻结/解冻）
export function openAppstoreFlow(onDone) {
  if (store.deviceInfo?.adb_master_switch === false) {
    showToast('ADB 总开关已关闭，该功能无法使用');
    return;
  }
  if (isAppstoreFrozen()) {
    openAppstoreUnfreezeFlow(onDone);
  } else {
    openAppstoreFreezeFlow(onDone);
  }
}
