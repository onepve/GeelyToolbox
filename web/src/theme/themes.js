// 液态玻璃 4 色 × 白夜主题系统 —— 配色 1:1 来自车主认可的 make_glass.py 效果图
// 模式三档：auto（跟随时间 6:00~17:59 为白天）| day | night
// 配色：blue / brown / green / pink，出厂默认 blue · night

import { store } from '../store';

const DAY_START = 6;   // 06:00 起为白天
const DAY_END = 18;    // 18:00 起为黑夜

// 每组变量与 make_glass.py THEMES 逐值对应：
// bg→--bg-main | glass2→--bg-card | glass→--bg-item | stroke→--border-color
// text/sub→--text-main/--text-sub | accent→--accent-gold | badge→--accent-gold-*
// o1~o4→--orb-1~4 | highlight/toplight→玻璃高光 | shadow→--glass-shadow
const THEMES = {
  blue: {
    label: '蓝',
    swatch: '#2f6bff',
    night: {
      '--bg-main': '#090d18',
      '--bg-panel': '#101728',
      '--bg-card': 'rgba(255,255,255,.13)',
      '--bg-item': 'rgba(255,255,255,.07)',
      '--bg-item-hover': 'rgba(255,255,255,.13)',
      '--border-color': 'rgba(255,255,255,.18)',
      '--border-light': 'rgba(255,255,255,.30)',
      '--text-main': '#eef2f8',
      '--text-sub': '#98a6bd',
      '--accent-gold': '#5b8cff',
      '--accent-gold-bg': 'rgba(91,140,255,.16)',
      '--accent-gold-text': '#a8c2ff',
      '--accent-gold-sub': '#ffd98a',
      '--glass-highlight': 'rgba(255,255,255,.30)',
      '--glass-underline': 'rgba(255,255,255,.05)',
      '--glass-toplight': 'rgba(255,255,255,.62)',
      '--glass-shadow': 'rgba(0,0,0,.55)',
      '--orb-1': '#3b6dff',
      '--orb-2': '#7a4dff',
      '--orb-3': '#1fb6a0',
      '--orb-4': '#3b82f6'
    },
    day: {
      '--bg-main': '#e9eef8',
      '--bg-panel': '#f8fafd',
      '--bg-card': 'rgba(255,255,255,.68)',
      '--bg-item': 'rgba(255,255,255,.46)',
      '--bg-item-hover': 'rgba(255,255,255,.60)',
      '--border-color': 'rgba(66,86,136,.26)',
      '--border-light': 'rgba(66,86,136,.40)',
      '--text-main': '#182238',
      '--text-sub': '#566580',
      '--accent-gold': '#2f6bff',
      '--accent-gold-bg': 'rgba(47,107,255,.14)',
      '--accent-gold-text': '#2f6bff',
      '--accent-gold-sub': '#a9761a',
      '--glass-highlight': 'rgba(255,255,255,.95)',
      '--glass-underline': 'rgba(255,255,255,.38)',
      '--glass-toplight': 'rgba(255,255,255,.95)',
      '--glass-shadow': 'rgba(52,72,122,.30)',
      '--orb-1': '#3b6dff',
      '--orb-2': '#6a5cff',
      '--orb-3': '#1fb6a0',
      '--orb-4': '#58a6ff'
    }
  },
  brown: {
    label: '棕',
    swatch: '#b0792c',
    night: {
      '--bg-main': '#141008',
      '--bg-panel': '#241b0e',
      '--bg-card': 'rgba(255,255,255,.14)',
      '--bg-item': 'rgba(255,255,255,.08)',
      '--bg-item-hover': 'rgba(255,255,255,.14)',
      '--border-color': 'rgba(255,214,160,.24)',
      '--border-light': 'rgba(255,214,160,.38)',
      '--text-main': '#f5ecdb',
      '--text-sub': '#b3a184',
      '--accent-gold': '#d4a04a',
      '--accent-gold-bg': 'rgba(212,160,74,.18)',
      '--accent-gold-text': '#ffd98a',
      '--accent-gold-sub': '#ffd98a',
      '--glass-highlight': 'rgba(255,255,255,.30)',
      '--glass-underline': 'rgba(255,255,255,.05)',
      '--glass-toplight': 'rgba(255,240,200,.60)',
      '--glass-shadow': 'rgba(0,0,0,.55)',
      '--orb-1': '#b07832',
      '--orb-2': '#d4953a',
      '--orb-3': '#8a5a24',
      '--orb-4': '#e8a13c'
    },
    day: {
      '--bg-main': '#f2ece0',
      '--bg-panel': '#fbf7ef',
      '--bg-card': 'rgba(255,255,255,.68)',
      '--bg-item': 'rgba(255,255,255,.48)',
      '--bg-item-hover': 'rgba(255,255,255,.60)',
      '--border-color': 'rgba(126,94,40,.30)',
      '--border-light': 'rgba(126,94,40,.44)',
      '--text-main': '#29210f',
      '--text-sub': '#7a6a4e',
      '--accent-gold': '#b0792c',
      '--accent-gold-bg': 'rgba(176,121,44,.15)',
      '--accent-gold-text': '#b0792c',
      '--accent-gold-sub': '#8a5a18',
      '--glass-highlight': 'rgba(255,255,255,.95)',
      '--glass-underline': 'rgba(255,255,255,.40)',
      '--glass-toplight': 'rgba(255,255,255,.95)',
      '--glass-shadow': 'rgba(120,90,40,.30)',
      '--orb-1': '#c9883a',
      '--orb-2': '#e8a13c',
      '--orb-3': '#b06a2c',
      '--orb-4': '#d4a04a'
    }
  },
  green: {
    label: '绿',
    swatch: '#159a63',
    night: {
      '--bg-main': '#06120c',
      '--bg-panel': '#0b1f15',
      '--bg-card': 'rgba(255,255,255,.13)',
      '--bg-item': 'rgba(255,255,255,.07)',
      '--bg-item-hover': 'rgba(255,255,255,.13)',
      '--border-color': 'rgba(255,255,255,.18)',
      '--border-light': 'rgba(255,255,255,.30)',
      '--text-main': '#e8f5ee',
      '--text-sub': '#8fb3a0',
      '--accent-gold': '#3fbf88',
      '--accent-gold-bg': 'rgba(63,191,136,.16)',
      '--accent-gold-text': '#9fe8c6',
      '--accent-gold-sub': '#ffd98a',
      '--glass-highlight': 'rgba(255,255,255,.30)',
      '--glass-underline': 'rgba(255,255,255,.05)',
      '--glass-toplight': 'rgba(255,255,255,.62)',
      '--glass-shadow': 'rgba(0,0,0,.55)',
      '--orb-1': '#159a63',
      '--orb-2': '#1fb68a',
      '--orb-3': '#0d7a4d',
      '--orb-4': '#34d399'
    },
    day: {
      '--bg-main': '#e8f2ec',
      '--bg-panel': '#f3faf5',
      '--bg-card': 'rgba(255,255,255,.68)',
      '--bg-item': 'rgba(255,255,255,.48)',
      '--bg-item-hover': 'rgba(255,255,255,.60)',
      '--border-color': 'rgba(40,110,80,.28)',
      '--border-light': 'rgba(40,110,80,.42)',
      '--text-main': '#152519',
      '--text-sub': '#567062',
      '--accent-gold': '#159a63',
      '--accent-gold-bg': 'rgba(21,154,99,.15)',
      '--accent-gold-text': '#159a63',
      '--accent-gold-sub': '#8a5a18',
      '--glass-highlight': 'rgba(255,255,255,.95)',
      '--glass-underline': 'rgba(255,255,255,.40)',
      '--glass-toplight': 'rgba(255,255,255,.95)',
      '--glass-shadow': 'rgba(30,100,75,.28)',
      '--orb-1': '#1fb68a',
      '--orb-2': '#34d399',
      '--orb-3': '#159a63',
      '--orb-4': '#7bc86a'
    }
  },
  pink: {
    label: '粉',
    swatch: '#c94a74',
    night: {
      '--bg-main': '#160d12',
      '--bg-panel': '#251319',
      '--bg-card': 'rgba(255,255,255,.13)',
      '--bg-item': 'rgba(255,255,255,.07)',
      '--bg-item-hover': 'rgba(255,255,255,.13)',
      '--border-color': 'rgba(255,255,255,.20)',
      '--border-light': 'rgba(255,255,255,.32)',
      '--text-main': '#f6e9ee',
      '--text-sub': '#c79bad',
      '--accent-gold': '#d46a8c',
      '--accent-gold-bg': 'rgba(212,74,140,.16)',
      '--accent-gold-text': '#f2a8c0',
      '--accent-gold-sub': '#ffd98a',
      '--glass-highlight': 'rgba(255,255,255,.30)',
      '--glass-underline': 'rgba(255,255,255,.05)',
      '--glass-toplight': 'rgba(255,255,255,.62)',
      '--glass-shadow': 'rgba(0,0,0,.55)',
      '--orb-1': '#c94a74',
      '--orb-2': '#d46a8c',
      '--orb-3': '#a84a7a',
      '--orb-4': '#b06bff'
    },
    day: {
      '--bg-main': '#f6e9ee',
      '--bg-panel': '#fdf2f6',
      '--bg-card': 'rgba(255,255,255,.68)',
      '--bg-item': 'rgba(255,255,255,.48)',
      '--bg-item-hover': 'rgba(255,255,255,.60)',
      '--border-color': 'rgba(170,80,120,.28)',
      '--border-light': 'rgba(170,80,120,.42)',
      '--text-main': '#2c1a24',
      '--text-sub': '#7a5a6a',
      '--accent-gold': '#c94a74',
      '--accent-gold-bg': 'rgba(201,74,116,.16)',
      '--accent-gold-text': '#c94a74',
      '--accent-gold-sub': '#8a5a18',
      '--glass-highlight': 'rgba(255,255,255,.95)',
      '--glass-underline': 'rgba(255,255,255,.40)',
      '--glass-toplight': 'rgba(255,255,255,.95)',
      '--glass-shadow': 'rgba(170,80,120,.28)',
      '--orb-1': '#e06a92',
      '--orb-2': '#d46a8c',
      '--orb-3': '#b06bff',
      '--orb-4': '#f291a8'
    }
  }
};

export const PALETTES = ['blue', 'brown', 'green', 'pink'];
export const MODES = ['auto', 'day', 'night'];
export const MODE_LABELS = { auto: '跟随时间', day: '白天', night: '黑夜' };
export const PALETTE_LABELS = { blue: '蓝', brown: '棕', green: '绿', pink: '粉' };
// 色卡小圆点取色（各配色主色，取自认可效果图）
export const PALETTE_DOT = { blue: '#5b8cff', brown: '#d4a04a', green: '#3fbf88', pink: '#d46a8c' };

const STORAGE_KEY = 'toolbox_theme_v1';

function loadSaved() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const obj = JSON.parse(raw);
    if (obj && PALETTES.includes(obj.palette) && MODES.includes(obj.mode)) return obj;
  } catch (e) { /* WebView 存储不可用时静默降级为默认 */ }
  return null;
}

function save() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ palette: store.theme.palette, mode: store.theme.mode }));
  } catch (e) { /* 同上 */ }
}

export function resolvedMode() {
  const mode = store.theme.mode;
  if (mode === 'day' || mode === 'night') return mode;
  const h = new Date().getHours();
  return (h >= DAY_START && h < DAY_END) ? 'day' : 'night';
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
  } else {
    root.classList.remove('light');
    document.body.classList.remove('light');
  }
}

// 初始化：读持久化 → 应用 → auto 模式每分钟自动跟随昼夜
export function initTheme() {
  const saved = loadSaved();
  store.theme.palette = saved ? saved.palette : 'blue';
  store.theme.mode = saved ? saved.mode : 'night';
  applyTheme();
  if (applyTimer) clearInterval(applyTimer);
  applyTimer = setInterval(() => {
    if (store.theme.mode === 'auto') applyTheme();
  }, 60000);
}

export function setPalette(p) {
  if (!PALETTES.includes(p)) return;
  store.theme.palette = p;
  save();
  applyTheme();
}

export function setMode(m) {
  if (!MODES.includes(m)) return;
  store.theme.mode = m;
  save();
  applyTheme();
}

// 顶栏快捷键：点一下切到相反模式（退出 auto）
export function quickToggleDayNight() {
  store.theme.mode = store.isNight ? 'day' : 'night';
  save();
  applyTheme();
}

export { THEMES };
