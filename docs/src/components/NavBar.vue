<script setup lang="ts">
import { ref } from 'vue'
import { Download, Globe, Menu, Palette, X } from 'lucide-vue-next'
import type { Locale } from '../data/content'
import { SITE_COPY } from '../data/content'

defineProps<{
  locale: Locale
  versionTag: string
}>()

const emit = defineEmits<{
  'toggle-locale': []
  'scroll-studio': []
}>()

const mobileOpen = ref(false)
const logoUrl = `${import.meta.env.BASE_URL}logo.png`

function closeMobile() {
  mobileOpen.value = false
}
</script>

<template>
  <header class="nav-wrapper">
    <nav class="nav-pill" aria-label="Main navigation">
      <a href="#top" class="nav-brand">
        <img :src="logoUrl" alt="V4War Logo" class="nav-logo" width="32" height="32" />
        <span class="nav-name">V4War</span>
        <span class="nav-version mono">{{ versionTag }}</span>
      </a>

      <div class="nav-links">
        <a href="#hotspot" class="nav-link">{{ SITE_COPY.nav.hotspot[locale] }}</a>
        <a href="#toolbox" class="nav-link">{{ SITE_COPY.nav.toolbox[locale] }}</a>
        <a href="#studio" class="nav-link">{{ SITE_COPY.nav.studio[locale] }}</a>
        <a href="#protocols" class="nav-link">{{ SITE_COPY.nav.protocols[locale] }}</a>
      </div>

      <div class="nav-actions">
        <button
          type="button"
          class="icon-pill"
          :aria-label="locale === 'tr' ? 'Switch to English' : 'Türkçeye geç'"
          data-testid="locale-toggle"
          @click="emit('toggle-locale')"
        >
          <Globe :size="15" />
          <span>{{ locale.toUpperCase() }}</span>
        </button>

        <button
          type="button"
          class="icon-pill hide-xs"
          :aria-label="SITE_COPY.nav.studio[locale]"
          @click="emit('scroll-studio')"
        >
          <Palette :size="15" />
        </button>

        <a href="#download" class="m3-btn m3-btn-primary nav-cta">
          <Download :size="15" />
          <span>{{ SITE_COPY.nav.download[locale] }}</span>
        </a>

        <button
          type="button"
          class="icon-pill mobile-menu-btn"
          :aria-expanded="mobileOpen"
          aria-label="Toggle menu"
          @click="mobileOpen = !mobileOpen"
        >
          <X v-if="mobileOpen" :size="18" />
          <Menu v-else :size="18" />
        </button>
      </div>
    </nav>

    <div v-if="mobileOpen" class="mobile-drawer m3-card">
      <a href="#hotspot" class="mobile-link" @click="closeMobile">{{ SITE_COPY.nav.hotspot[locale] }}</a>
      <a href="#toolbox" class="mobile-link" @click="closeMobile">{{ SITE_COPY.nav.toolbox[locale] }}</a>
      <a href="#studio" class="mobile-link" @click="closeMobile">{{ SITE_COPY.nav.studio[locale] }}</a>
      <a href="#protocols" class="mobile-link" @click="closeMobile">{{ SITE_COPY.nav.protocols[locale] }}</a>
      <a href="#download" class="m3-btn m3-btn-primary" @click="closeMobile">
        <Download :size="15" />
        <span>{{ SITE_COPY.nav.download[locale] }}</span>
      </a>
    </div>
  </header>
</template>

<style scoped>
.nav-wrapper {
  position: sticky;
  top: 14px;
  z-index: 50;
  padding: 0 24px;
  pointer-events: none;
}

.nav-pill {
  max-width: 1160px;
  margin: 0 auto;
  padding: 8px 10px 8px 14px;
  border-radius: 999px;
  background: var(--bg-glass);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  border: 1px solid var(--border-subtle);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.42);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  pointer-events: auto;
}

.nav-brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-weight: 800;
  font-size: 17px;
  letter-spacing: -0.02em;
}

.nav-logo {
  border-radius: 10px;
  object-fit: contain;
}

.nav-version {
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--bg-nested);
  border: 1px solid var(--border-subtle);
  color: var(--seed-accent);
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-link {
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--text-secondary);
  transition: color 0.18s ease, background-color 0.18s ease;
}

.nav-link:hover {
  color: var(--text-primary);
  background: var(--bg-pill);
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 12px;
  border-radius: 999px;
  background: var(--bg-pill);
  border: 1px solid var(--border-muted);
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 600;
  transition: border-color 0.18s ease, background-color 0.18s ease;
}

.icon-pill:hover {
  border-color: var(--border-strong);
  background: var(--bg-nested);
}

.nav-cta {
  padding: 9px 16px;
  font-size: 13px;
}

.mobile-menu-btn {
  display: none;
  padding: 0 10px;
}

.mobile-drawer {
  max-width: 1160px;
  margin: 10px auto 0;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: auto;
  background: var(--bg-glass);
  backdrop-filter: blur(18px);
}

.mobile-link {
  padding: 10px 12px;
  border-radius: 12px;
  font-weight: 600;
  color: var(--text-secondary);
}

.mobile-link:hover {
  background: var(--bg-nested);
  color: var(--text-primary);
}

@media (max-width: 900px) {
  .nav-links {
    display: none;
  }

  .mobile-menu-btn {
    display: inline-flex;
  }
}

@media (max-width: 560px) {
  .nav-wrapper {
    padding: 0 14px;
  }

  .nav-cta {
    display: none;
  }

  .hide-xs {
    display: none;
  }
}
</style>
