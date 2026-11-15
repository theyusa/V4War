import { copyFileSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const siteBase = process.env.V4WAR_SITE_BASE || '/V4War/'
const normalizedBase = siteBase.endsWith('/') ? siteBase : `${siteBase}/`
const siteOrigin =
  process.env.V4WAR_SITE_ORIGIN ||
  (normalizedBase === '/' ? 'https://v4war.theyusa.tr' : 'https://theyusa.tr')
const siteCanonical = `${siteOrigin}${normalizedBase}`

function siteMetaAndSpaPlugin() {
  return {
    name: 'v4war-site-meta-and-spa',
    transformIndexHtml(html: string) {
      return html
        .replace(/https:\/\/theyusa\.tr\/V4War\//g, siteCanonical)
        .replace(/href="\/V4War\/logo\.png"/g, `href="${normalizedBase}logo.png"`)
    },
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
  base: normalizedBase,
  plugins: [vue(), siteMetaAndSpaPlugin()],
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    sourcemap: false,
  },
})
