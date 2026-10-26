import { createApp } from 'vue'
import App from './App.vue'
import { store, openModal, closeModal } from './store'
import { setMode, setPalette, quickToggleDayNight, getDayBrightness, setDayBrightness } from './theme/themes'

window.__APP_STORE__ = store
window.__VUE_STORE__ = store
window.openModal = openModal
window.closeModal = closeModal
window.__TOOLBOX_THEMES__ = { setMode, setPalette, quickToggleDayNight, getDayBrightness, setDayBrightness }
createApp(App).mount('#app')
