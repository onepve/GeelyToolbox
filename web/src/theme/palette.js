// 液态玻璃主题「单一数据源」——纯数据、零依赖。
// 用途：既供运行时 theme/themes.js 使用，也供构建期 vite.config.js 读取，
// 把「首帧主题引导脚本」内联进 index.html <head>，在 Vue 加载前就把 <html> 变量写好，
// 彻底消除「软件打开先黑一下、再变成白天模式」的启动闪黑。
// 铁律：本文件严禁 import 任何模块（store / vue / tailwind），否则构建期无法加载。
export const DAY_START = 6;   // 06:00 起为白天
export const DAY_END = 18;    // 18:00 起为黑夜
export const STORAGE_KEY = 'toolbox_theme_v1';
// 出厂默认档 —— 运行时 initTheme() 与构建期首帧引导脚本必须共用这同一组常量，
// 否则「首帧主题」与「挂载后主题」不一致，会出现反向闪动（先白天再跳黑夜）。
export const DEFAULT_PALETTE = 'blue';
export const DEFAULT_MODE = 'night';
export const MODES = ['auto', 'day', 'night'];
export const PALETTES = ['blue', 'brown', 'green', 'pink'];

export const THEMES = {
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
      '--glass-shadow': 'rgba(0,0,0,.55)'
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
      '--glass-shadow': 'rgba(52,72,122,.30)'
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
      '--glass-shadow': 'rgba(0,0,0,.55)'
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
      '--glass-shadow': 'rgba(120,90,40,.30)'
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
      '--glass-shadow': 'rgba(0,0,0,.55)'
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
      '--glass-shadow': 'rgba(30,100,75,.28)'
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
      '--glass-shadow': 'rgba(0,0,0,.55)'
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
      '--glass-shadow': 'rgba(170,80,120,.28)'
    }
  }
};
