// Web Audio 原生毫秒级微触感 "嗒" 声
// 性能铁律：异步非阻塞，预置解锁，杜绝阻塞主线程 UI/触控响应与掉帧
let audioCtx = null;
let isUnlocked = false;

function getAudioContext() {
  if (!audioCtx) {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (AudioContextClass) {
      audioCtx = new AudioContextClass();
    }
  }
  return audioCtx;
}

export function playTouchFeedback() {
  // 脱离当前微任务调用栈，让 UI 渲染与响应式状态立即生效，0ms 瞬间反馈
  setTimeout(() => {
    try {
      const ctx = getAudioContext();
      if (!ctx) return;
      if (ctx.state === 'suspended') {
        ctx.resume().catch(() => {});
        return; // 首次恢复异步进行，不阻塞后续流程
      }
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      // 850Hz -> 180Hz 极速衰减 25ms
      const t = ctx.currentTime;
      osc.frequency.setValueAtTime(850, t);
      osc.frequency.exponentialRampToValueAtTime(180, t + 0.025);
      gain.gain.setValueAtTime(0.12, t);
      gain.gain.exponentialRampToValueAtTime(0.001, t + 0.025);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(t);
      osc.stop(t + 0.028);
    } catch (e) {}
  }, 0);
}
