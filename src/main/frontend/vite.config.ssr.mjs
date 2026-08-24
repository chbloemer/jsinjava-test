import vue from '@vitejs/plugin-vue'

export default {
  plugins: [vue()],
  define: {
    'process.env.NODE_ENV': '"production"',
    '__VUE_PROD_DEVTOOLS__': 'false',
    '__VUE_OPTIONS_API__': 'true',
    '__VUE_PROD_HYDRATION_MISMATCH_DETAILS__': 'false'
  },
  build: {
    outDir: '../../../build/frontend/ssr',
    emptyOutDir: true,
    target: 'es2022',
    // Unminified bundle keeps GraalJS stack traces readable during the experiment.
    minify: false,
    lib: {
      entry: 'src/entry-server.js',
      name: 'SSR',
      formats: ['iife'],
      fileName: () => 'server-bundle.iife.js'
    },
    rollupOptions: {
      // Bundle vue + vue/server-renderer inline — GraalJS has no module resolver.
      external: []
    }
  }
}
