import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { pages } from './pages.js'

// Called from Java (GraalJS). Returns a Promise<string> that settles via
// microtasks only, so the JVM side can read the result synchronously.
export function render(pageName, initialStateJson) {
  const Page = pages[pageName]
  if (!Page) {
    throw new Error('Unknown page: ' + pageName)
  }
  const initialState = JSON.parse(initialStateJson)
  const app = createSSRApp(Page, { initialState })
  return renderToString(app)
}

// Lets the Java side validate view names at resolution time.
export const pageNames = Object.keys(pages)
