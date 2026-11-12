// 下拉面板同屏互斥（单一真源）
// 【为什么需要这个模块 v1.8.33.16】
// 方控页同屏有 15+ 个下拉：ActionSelect（方向盘 1~4 各 3 个手势）与
// CustomKeyActionSelect（方向盘 5 自定义按键，以及应用生态里的自定义行）。
// 两个组件原先各自实现「点外面就关」：面板挂 body / 绝对定位，
// 各自判断点击是否落在自己 rootEl 内，点别人的触发器时谁也管不到谁，
// 实车与 headless 都稳定复现「同时展开 2~3 个面板、叠在屏幕上」。
// 统一改为模块级单一活跃句柄：任何时刻全屏只允许一个下拉展开，
// 新开一个必然先关掉上一个（跨组件同样生效）。

let activeCloser = null;

// 登记自己为「当前唯一活跃的下拉」，并顺手关掉上一个
export function claimExclusive(closer) {
  if (activeCloser && activeCloser !== closer) {
    try { activeCloser(); } catch (e) {}
  }
  activeCloser = closer;
}

// 自己收起时释放句柄（只有自己还持有时才清，避免误清别人的）
export function releaseExclusive(closer) {
  if (activeCloser === closer) activeCloser = null;
}

// 强制收起当前活跃下拉（切页/切 Tab 时用）
export function closeActive() {
  const c = activeCloser;
  activeCloser = null;
  if (c) { try { c(); } catch (e) {} }
}
