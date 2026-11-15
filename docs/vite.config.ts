import { copyFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

function spa404FallbackPlugin() {
  return {
    name: 'v4war-spa-404-fallback',
    closeBundle() {
      const distDir = resolve(__dirname, 'dist')
      const indexFile = resolve(distDir, 'index.html')
      const fallbackFile = resolve(distDir, '404.html')
      if (existsSync(indexFile)) {
        copyFileSync(indexFile, fallbackFile)
      }
    },
  }
}

export default defineConfig({
  base: process.env.V4WAR_SITE_BASE || '/V4War/',
  plugins: [vue(), spa404FallbackPlugin()],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    sourcemap: false,
  },
})
