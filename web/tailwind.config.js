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
        hyper: {
          bg: '#12141A',
          card: '#1C1F26',
          item: '#262A33',
          hover: '#323742',
          border: 'rgba(255, 255, 255, 0.08)',
          txt: '#FFFFFF',
          dim: '#94A3B8'
        }
      }
    },
  },
  plugins: [],
}
