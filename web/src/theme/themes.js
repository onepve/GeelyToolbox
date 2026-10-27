// 液态玻璃 4 色 + 曜石金（初代实底主题复活）× 白夜主题系统
// 模式三档：auto（跟随车机原厂昼夜信号）| day | night
// 配色：blue / brown / green / pink / gold，出厂默认 green · night

import { store } from '../store';

import { DAY_START, DAY_END, STORAGE_KEY, THEMES, DEFAULT_PALETTE, DEFAULT_MODE, MODES, PALETTES, V1_PALETTES, V2_PALETTES } from './palette';

// MODES / PALETTES 与默认档统一定义在 palette.js（构建期首帧引导脚本同源共用），此处只做转出。
export { MODES, PALETTES, V1_PALETTES, V2_PALETTES };
export const MODE_LABELS = { auto: '跟随车机', day: '白天', night: '黑夜' };
export const PALETTE_LABELS = {
  blue: '蓝', brown: '棕', green: '绿', pink: '粉', gold: '金',
  sky_ocean: '蓝', aurora_cyber: '霓虹', amber_gold: '金', bamboo_emerald: '绿',
  sakura_pink: '粉', taro_purple: '紫'
};
// 色卡小圆点取色（各配色主色，取自认可效果图）
export const PALETTE_DOT = {
  blue: '#5b8cff', brown: '#d4a04a', green: '#3fbf88', pink: '#d46a8c', gold: '#d99a26',
  sky_ocean: '#2563eb', aurora_cyber: '#06b6d4', amber_gold: '#d97706', bamboo_emerald: '#16a34a',
  sakura_pink: '#f43f5e', taro_purple: '#8b5cf6'
};

export const V2_THEME_INFO = {
  sky_ocean: {
    name: '晴空蓝调 / 深海星曜',
    shortName: '晴空蓝',
    tag: '主力推荐',
    desc: '白天柔光天蓝防眩，夜间黑晶超清冷光',
    dayBg: '#eef3fa',
    nightBg: '#0a0f1d',
    accent: '#2563eb'
  },
  aurora_cyber: {
    name: '极光霓虹 / 幻境星海',
    shortName: '极光霓虹',
    tag: '旗舰创新',
    desc: '白天淡青抗眩强漫射，夜间深沉曜黑青紫流光',
    dayBg: '#f0f9fa',
    nightBg: '#080812',
    accent: '#06b6d4'
  },
  amber_gold: {
    name: '暖阳琥珀 / 曜石流金',
    shortName: '暖阳金',
    tag: '豪华温润',
    desc: '白天暖玉柔光不晃，夜间香槟曜石长途护眼',
    dayBg: '#fbf7ee',
    nightBg: '#14110b',
    accent: '#d97706'
  },
  bamboo_emerald: {
    name: '晨曦竹影 / 翡冷翠境',
    shortName: '翡翠绿',
    tag: '清新舒缓',
    desc: '白天青翠生机漫反射，夜间沉浸墨绿质感',
    dayBg: '#ecfdf5',
    nightBg: '#0a140f',
    accent: '#10b981'
  },
  sakura_pink: {
    name: '落樱暖粉 / 暮樱星河',
    shortName: '落樱粉',
    tag: '甜心柔粉',
    desc: '白天落樱温润漫反射，夜间暮樱黑晶微光',
    dayBg: '#fdf2f4',
    nightBg: '#160c12',
    accent: '#f43f5e'
  },
  taro_purple: {
    name: '香芋紫霞 / 暮霭紫晶',
    shortName: '香芋紫',
    tag: '梦幻紫晶',
    desc: '白天香芋奶霜柔和护眼，夜间紫晶曜石流光溢彩',
    dayBg: '#f6f3fc',
    nightBg: '#110c1c',
    accent: '#8b5cf6'
  }
};

export function getDayBrightness() {
  try {
    const val = localStorage.getItem('toolbox_day_brightness');
    if (val) return parseFloat(val);
  } catch (e) {}
  return 1.0;
}

export function setDayBrightness(val) {
  const clamped = Math.max(0.65, Math.min(1.0, val));
  try {
    localStorage.setItem('toolbox_day_brightness', clamped.toString());
  } catch (e) {}
  applyTheme();
}

export function getNightBrightness() {
  try {
    const val = localStorage.getItem('toolbox_night_brightness');
    if (val) return parseFloat(val);
  } catch (e) {}
  return 1.0;
}

export function setNightBrightness(val) {
  const clamped = Math.max(0.65, Math.min(1.0, val));
  try {
    localStorage.setItem('toolbox_night_brightness', clamped.toString());
  } catch (e) {}
  applyTheme();
}


const OLD_TO_NEW_PALETTE = {
  blue: 'sky_ocean',
  brown: 'amber_gold',
  green: 'bamboo_emerald',
  pink: 'sakura_pink',
  gold: 'amber_gold'
};

function loadSaved() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const obj = JSON.parse(raw);
    if (obj) {
      if (OLD_TO_NEW_PALETTE[obj.palette]) {
        obj.palette = OLD_TO_NEW_PALETTE[obj.palette];
      }
      if (PALETTES.includes(obj.palette) && MODES.includes(obj.mode)) return obj;
    }
  } catch (e) { /* WebView 存储不可用时静默降级为默认 */ }
  return null;
}

function save() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ palette: store.theme.palette, mode: store.theme.mode }));
  } catch (e) { /* 同上 */ }
}

export function getCarOrSystemNightStatus() {
  // 1. 优先从车机原生 Bridge 读取实时原厂昼夜信号 (大灯/系统暗黑模式联动)
  try {
    if (typeof window !== 'undefined' && window.ToolboxBridge && typeof window.ToolboxBridge.isNightMode === 'function') {
      const isNight = window.ToolboxBridge.isNightMode();
      if (typeof isNight === 'boolean') {
        return isNight;
      }
    }
  } catch (e) {}

  // 2. 其次读取系统媒体查询（深色模式）
  if (typeof window !== 'undefined' && window.matchMedia) {
    if (window.matchMedia('(prefers-color-scheme: dark)').matches) return true;
    if (window.matchMedia('(prefers-color-scheme: light)').matches) return false;
  }

  // 3. 最后以本地时间兜底（18:00 - 06:00 为夜间）
  const h = new Date().getHours();
  return (h < DAY_START || h >= DAY_END);
}

export function resolvedMode() {
  const mode = store.theme.mode;
  if (mode === 'day' || mode === 'night') return mode;
  // auto 模式：即时毫秒级判定车机原厂昼夜信号，杜绝延迟与历史旧值遮蔽
  return getCarOrSystemNightStatus() ? 'night' : 'day';
}

let applyTimer = null;

export function applyTheme() {
  const palette = store.theme.palette;
  const mode = resolvedMode();
  const vars = THEMES[palette][mode];
  const root = document.documentElement;
  for (const [k, v] of Object.entries(vars)) root.style.setProperty(k, v);
  root.setAttribute('data-palette', palette);
  root.setAttribute('data-mode', mode);
  store.isNight = mode === 'night';
  // 兼容旧 .light 机制（部分组件仍按 html.light 判定）
  if (mode === 'day') {
    root.classList.add('light');
    document.body.classList.add('light');
    const b = getDayBrightness();
    if (b < 0.99) {
      root.style.filter = `brightness(${b})`;
    } else {
      root.style.filter = '';
    }
  } else {
    root.classList.remove('light');
    document.body.classList.remove('light');
    const nb = getNightBrightness();
    if (nb < 0.99) {
      root.style.filter = `brightness(${nb})`;
    } else {
      root.style.filter = '';
    }
  }
}

// 初始化：读持久化 → 应用 → auto 模式感知车机昼夜变化
export function initTheme() {
  const saved = loadSaved();
  store.theme.palette = saved ? saved.palette : DEFAULT_PALETTE;
  store.theme.mode = saved ? saved.mode : DEFAULT_MODE;
  // 首帧若处于 auto 模式，尝试先调取原厂车机 isNightMode 信号
  try {
    if (window.ToolboxBridge && typeof window.ToolboxBridge.isNightMode === 'function') {
      const isNight = window.ToolboxBridge.isNightMode();
      if (typeof isNight === 'boolean') {
        store.isNight = isNight;
      }
    }
  } catch (e) {}
  applyTheme();
  if (applyTimer) clearInterval(applyTimer);
  applyTimer = setInterval(() => {
    if (store.theme.mode === 'auto') {
      try {
        const isNight = getCarOrSystemNightStatus();
        if (typeof isNight === 'boolean' && isNight !== store.isNight) {
          store.isNight = isNight;
          applyTheme();
        }
      } catch (e) {}
    }
  }, 10000);
}

export function setPalette(p) {
  if (!PALETTES.includes(p)) return;
  store.theme.palette = p;
  save();
  applyTheme();
}

export function setMode(mode) {
  if (!MODES.includes(mode)) return;
  store.theme.mode = mode;
  save();
  // 当切到 auto 模式时，立即通过原生 Bridge 抓取当前车机最新昼夜状态，更新 store.isNight
  if (mode === 'auto') {
    store.isNight = getCarOrSystemNightStatus();
  }
  // 无论切到 auto 还是手动模式，立即无延迟重新解析并刷写 DOM 主题
  applyTheme();
}

// 顶栏快捷键：点一下切到相反模式（退出 auto）
export function quickToggleDayNight() {
  store.theme.mode = store.isNight ? 'day' : 'night';
  save();
  applyTheme();
}

export { THEMES };
