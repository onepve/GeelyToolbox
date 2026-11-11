/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  // 车规铁律：车机是纯触摸屏，没有鼠标。
  // 浏览器在触摸结束（touchend）之后仍会把 hover 状态留在被触摸的元素上（sticky hover），
  // 表现就是「手指按一下，那块就灰了，松手也一直灰着」。
  // Tailwind 3.4 的 hoverOnlyWhenSupported 会把所有 hover: 规则自动包进
  // @media (hover: hover)，触摸设备直接不生效，从根上消灭这个灰块。
  future: {
    hoverOnlyWhenSupported: true,
  },
  theme: {
    extend: {
      colors: {
        'car-bg': 'var(--bg-main)',
        'car-card': 'var(--bg-card)',
        'car-item': 'var(--bg-item)',
        'car-hover': 'var(--bg-item-hover)',
        'car-border': 'var(--border-color)',
        'car-border-light': 'var(--border-light)',
        'car-text': 'var(--text-main)',
        'car-sub': 'var(--text-sub)',
        'car-accent': 'var(--accent-gold)',
        'car-accent-bg': 'var(--accent-gold-bg)',
        'car-accent-text': 'var(--accent-gold-text)',
        'car-accent-sub': 'var(--accent-gold-sub)',
      }
    },
  },
  plugins: [],
}
