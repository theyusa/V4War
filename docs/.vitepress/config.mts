import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'V4War',
  description: 'Android Multi-Protocol Proxy Client',
  base: '/V4War/',
  appearance: 'force-dark',
  head: [
    ['link', { rel: 'icon', href: '/V4War/logo.png' }]
  ],
  themeConfig: {
    logo: '/logo.png',
    nav: [
      { text: 'Ana Sayfa', link: '/' },
      { text: 'Kurulum', link: '/guide' },
      { text: 'İndir (Releases)', link: 'https://github.com/theyusa/V4War/releases' }
    ],
    socialLinks: [
      { icon: 'github', link: 'https://github.com/theyusa/V4War' }
    ],
    footer: {
      message: 'Açık Kaynak Android Proxy İstemcisi',
      copyright: 'Copyright © 2026 theyusa'
    }
  }
})
