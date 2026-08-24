import { createSSRApp } from 'vue'
import App from './App.vue'

const initialState = window.__INITIAL_STATE__
createSSRApp(App, { initialState }).mount('#app')
