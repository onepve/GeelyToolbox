/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
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
