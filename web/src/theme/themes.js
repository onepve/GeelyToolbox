// 液态玻璃 4 色 + 曜石金（初代实底主题复活）× 白夜主题系统
// 模式三档：auto（跟随时间 6:00~17:59 为白天）| day | night
// 配色：blue / brown / green / pink / gold，出厂默认 green · night

import { store } from '../store';

import { DAY_START, DAY_END, STORAGE_KEY, THEMES, DEFAULT_PALETTE, DEFAULT_MODE, MODES, PALETTES } from './palette';

// 每组变量与 make_glass.py THEMES 逐值对应：
// bg→--bg-main | glass2→--bg-card | glass→--bg-item | stroke→--border-color
// text/sub→--text-main/--text-sub | accent→--accent-gold | badge→--accent-gold-*
// highlight/toplight→玻璃高光 | shadow→--glass-shadow

// MODES / PALETTES 与默认档统一定义在 palette.js（构建期首帧引导脚本同源共用），此处只做转出。
export { MODES, PALETTES };
export const MODE_LABELS = { auto: '跟随时间', day: '白天', night: '黑夜' };
export const PALETTE_LABELS = { blue: '蓝', brown: '棕', green: '绿', pink: '粉', gold: '金' };
// 色卡小圆点取色（各配色主色，取自认可效果图）
export const PALETTE_DOT = { blue: '#5b8cff', brown: '#d4a04a', green: '#3fbf88', pink: '#d46a8c', gold: '#d99a26' };


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
  store.theme.palette = saved ? saved.palette : DEFAULT_PALETTE;
  store.theme.mode = saved ? saved.mode : DEFAULT_MODE;
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
