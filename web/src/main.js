import { createApp } from 'vue'
import App from './App.vue'
import { store } from './store'

window.__APP_STORE__ = store
createApp(App).mount('#app')
