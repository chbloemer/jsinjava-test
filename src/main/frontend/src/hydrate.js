import { createSSRApp } from 'vue'

// Shared client bootstrap: hydrate the server-rendered markup with the page
// component, using the state the server inlined into the page.
export function hydrate(Page) {
  const initialState = window.__INITIAL_STATE__
  if (!initialState) {
    throw new Error('window.__INITIAL_STATE__ missing — server template broken?')
  }
  createSSRApp(Page, { initialState }).mount('#app')
}
