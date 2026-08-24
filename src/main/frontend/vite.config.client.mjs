import { readdirSync } from 'node:fs'
import { dirname, join, parse } from 'node:path'
import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'

// One entry per page, discovered from src/entries/ — adding a page means
// adding its entry file there, with no config to keep in sync.
const entriesDir = join(dirname(fileURLToPath(import.meta.url)), 'src/entries')
const input = Object.fromEntries(
  readdirSync(entriesDir)
    .filter((file) => file.endsWith('.js'))
    .map((file) => [parse(file).name, join(entriesDir, file)])
)

export default {
  plugins: [vue()],
  build: {
    outDir: '../../../build/frontend/client',
    emptyOutDir: true,
    target: 'es2022',
    rollupOptions: {
      // Shared code (Vue itself) becomes a common chunk.
      input,
      output: {
        // Deterministic entry names — the server template references
        // /assets/<page>.js directly.
        entryFileNames: 'assets/[name].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name][extname]'
      }
    }
  }
}
