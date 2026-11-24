import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import pkg from './package.json' with { type: 'json' }

// The app is served by the same server as the API, under /app/.
export default defineConfig({
  base: '/app/',
  plugins: [react()],
  define: {
    __APP_VERSION__: JSON.stringify(`${pkg.version} (web ${process.env.GITHUB_RUN_NUMBER ?? 'dev'})`),
  },
  build: { outDir: 'dist', sourcemap: false, target: 'es2022' },
  server: { proxy: { '/api': 'http://localhost:8080' } },
  test: { environment: 'jsdom', globals: true, setupFiles: ['./src/test/setup.ts'], css: false },
})
