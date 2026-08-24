import { createSSRApp } from 'vue'
import App from './App.vue'

const initialState = window.__INITIAL_STATE__
if (!initialState) {
  throw new Error('window.__INITIAL_STATE__ missing — server template broken?')
}
createSSRApp(App, { initialState }).mount('#app')
