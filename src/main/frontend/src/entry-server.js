import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import App from './App.vue'

// Called from Java (GraalJS). Returns a Promise<string> that settles via
// microtasks only, so the JVM side can read the result synchronously.
export function render(initialStateJson) {
  const initialState = JSON.parse(initialStateJson)
  const app = createSSRApp(App, { initialState })
  return renderToString(app)
}
