// ============================================================================
// useConfigModal — 车辆自动化配置弹窗共用逻辑（Door/Gear/Mode/Trunk 四弹窗统一入口）
// 抽取自四份 100% 克隆的 script 段：开关切换 / 语音试听 / 自定义声效 / 直设参数。
// 模板层 @click 调用形态保持不变，仅 script 收敛到本文件。
// ============================================================================
import { store, bridge, showToast, openModal } from '../store';

export function useConfigModal() {

  /** 布尔开关切换：同步 store + 通知原生 + toast 反馈 */
  function toggleSetting(key) {
    const next = !store.vehicleAuto[key];
    store.vehicleAuto[key] = next;
    bridge.call('setVehicleAutomationSetting', key, next);
    showToast('设置已更新: ' + (next ? '已开启' : '已关闭'));
  }

  /** 非布尔参数直设（如车门语音模式），toast 文案由调用方给定 */
  function setSetting(key, value, toast) {
    store.vehicleAuto[key] = value;
    bridge.call('setVehicleAutomationSetting', key, value);
    if (toast) showToast(toast);
  }

  /** 按语音类型即时试听当前生效的播报音频 */
  function testVoice(type) {
    bridge.call('testVehicleVoice', type);
  }

  /** 打开单项声效设置弹窗（未显式给音频文件时默认 key.mp3） */
  function openCustomVoice(key, title, soundFile) {
    openModal('voiceItemSettings', {
      key,
      title,
      soundFile: soundFile || (key + '.mp3')
    });
  }

  return { toggleSetting, setSetting, testVoice, openCustomVoice };
}
