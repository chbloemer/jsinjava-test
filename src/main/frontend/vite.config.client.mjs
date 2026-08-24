import vue from '@vitejs/plugin-vue'

export default {
  plugins: [vue()],
  build: {
    outDir: '../../../build/frontend/client',
    emptyOutDir: true,
    target: 'es2022',
    rollupOptions: {
      input: 'src/entry-client.js',
      output: {
        entryFileNames: 'assets/app.js',
        assetFileNames: 'assets/[name][extname]'
      }
    }
  }
}
