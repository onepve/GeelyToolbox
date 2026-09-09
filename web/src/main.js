import { createApp } from 'vue'
import App from './App.vue'
import { store, openModal, closeModal } from './store'

window.__APP_STORE__ = store
window.openModal = openModal
window.closeModal = closeModal
createApp(App).mount('#app')
