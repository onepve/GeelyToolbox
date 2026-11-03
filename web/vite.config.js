import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { viteSingleFile } from 'vite-plugin-singlefile'
// 主题「单一数据源」：构建期读取，用于生成首帧引导脚本（严禁在别处再抄一份配色表）
import { THEMES, DAY_START, DAY_END, STORAGE_KEY, DEFAULT_PALETTE, DEFAULT_MODE, MODES, PALETTES } from './src/theme/palette.js'

// ---------------------------------------------------------------------------
// 启动闪黑根治：把主题引导脚本内联进 <head>，在 Vue 模块脚本执行前
// （即在车机 WebView 的「首次绘制」之前）就把 4 套配色 × 昼夜的全部 CSS 变量
// 写到 <html> 内联样式上，并设好 data-palette / data-mode / light 类与页面底色。
// 此前主题只在 App.vue 的 onMounted -> initTheme() 时注入，首帧必然先按 :root 的夜间
// 深色默认值渲染，用户看到的就是「软件打开先黑一下，再变成白天模式」。
//
// 铁律：本脚本的取值逻辑必须与 theme/themes.js 的 loadSaved()+resolvedMode() 逐值一致
// （默认档、合法性校验、跟随时间的判定窗口全部取自 palette.js 共享常量），
// 否则「首帧主题」与「挂载后主题」不同，会反向闪动（先白天再跳黑夜）。
// ---------------------------------------------------------------------------
function themeBoot() {
  const boot = `
(function(){try{
var T=${JSON.stringify(THEMES)},K=${JSON.stringify(STORAGE_KEY)},DS=${DAY_START},DE=${DAY_END},
DP=${JSON.stringify(DEFAULT_PALETTE)},DM=${JSON.stringify(DEFAULT_MODE)},
MS=${JSON.stringify(MODES)},PS=${JSON.stringify(PALETTES)};
var P=DP,M=DM;
try{var r=localStorage.getItem(K);if(r){var o=JSON.parse(r);
if(o&&PS.indexOf(o.palette)>=0&&MS.indexOf(o.mode)>=0){P=o.palette;M=o.mode;}}}catch(e){}
var md=(M==='day'||M==='night')?M:(function(){var h=(new Date()).getHours();return (h>=DS&&h<DE)?'day':'night';})();
var v=T[P][md],d=document.documentElement;
for(var k in v){try{d.style.setProperty(k,v[k]);}catch(e){}}
d.setAttribute('data-palette',P);d.setAttribute('data-mode',md);
if(md==='day'){d.classList.add('light');}
d.style.background=v['--bg-main'];
window.__TOOLBOX_BOOT_THEME__={palette:P,mode:md};
}catch(e){}})();
`.trim()

  return {
    name: 'toolbox-theme-boot',
    transformIndexHtml: {
      order: 'post',
      handler(html) {
        if (html.includes('__TOOLBOX_BOOT_THEME__')) return html
        return html.replace('</head>', `    <script>${boot}</script>\n  </head>`)
      }
    }
  }
}

export default defineConfig({
  plugins: [
    vue(),
    themeBoot(),
    viteSingleFile()
  ],
  build: {
    target: 'es2018',
    cssCodeSplit: false,
    assetsInlineLimit: 100000000,
    rollupOptions: {
      output: {
        inlineDynamicImports: true
      }
    }
  }
})
