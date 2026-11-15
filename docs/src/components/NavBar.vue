<script setup lang="ts">
import { computed, ref } from 'vue'
import { BookOpen, Download, Globe, Menu, Palette, X } from 'lucide-vue-next'
import type { Locale } from '../data/content'
import { SITE_COPY } from '../data/content'
import type { AppPageName } from '../utils/router'
import { buildHref } from '../utils/router'
import { MONOCHROME_THEMES, SURFACE_MODES, type MonochromeMode, type SurfaceMode } from '../utils/theme'

const props = defineProps<{
  locale: Locale
  versionTag: string
  surfaceMode: SurfaceMode
  monochromeMode: MonochromeMode
  currentPage: AppPageName
}>()

const emit = defineEmits<{
  'toggle-locale': []
  'cycle-surface': []
  navigate: [routePath: string]
}>()

const mobileOpen = ref(false)
const logoUrl = `${import.meta.env.BASE_URL}logo.png`

const activeSurfaceLabel = computed(() => {
  if (props.monochromeMode !== 'none') {
    const foundMono = MONOCHROME_THEMES.find((m) => m.id === props.monochromeMode)
    if (foundMono) return props.locale === 'tr' ? foundMono.labelTr : foundMono.labelEn
  }
  const found = SURFACE_MODES.find((m) => m.id === props.surfaceMode)
  if (!found) return props.surfaceMode
  return props.locale === 'tr' ? found.labelTr : found.labelEn
})

function go(routePath: string) {
  mobileOpen.value = false
  emit('navigate', routePath)
}
</script>

<template>
  <header class="nav-wrapper">
    <nav class="nav-pill" aria-label="Main navigation">
      <a
        :href="buildHref('/')"
        class="nav-brand"
        data-testid="nav-brand"
        @click.prevent="go('/')"
      >
        <span class="nav-logo-badge">
          <img :src="logoUrl" alt="V4War Logo" class="nav-logo" width="28" height="28" />
        </span>
        <span class="nav-name">V4War</span>
        <span class="nav-version mono">{{ versionTag }}</span>
      </a>

      <div class="nav-links">
        <a
          :href="buildHref('/')"
          class="nav-link"
          :class="{ active: currentPage === 'home' }"
          data-testid="nav-link-home"
          @click.prevent="go('/')"
        >
          {{ SITE_COPY.nav.home[locale] }}
        </a>
        <a
          :href="buildHref('/docs/overview')"
          class="nav-link"
          :class="{ active: currentPage === 'docs' }"
          data-testid="nav-link-docs"
          @click.prevent="go('/docs/overview')"
        >
          <BookOpen :size="14" />
          <span>{{ SITE_COPY.nav.docs[locale] }}</span>
        </a>
        <a
          :href="buildHref('/theme-studio')"
          class="nav-link"
          :class="{ active: currentPage === 'theme-studio' }"
          data-testid="nav-link-studio"
          @click.prevent="go('/theme-studio')"
        >
          {{ SITE_COPY.nav.studio[locale] }}
        </a>
        <a
          :href="buildHref('/explore')"
          class="nav-link"
          :class="{ active: currentPage === 'explore' }"
          data-testid="nav-link-explore"
          @click.prevent="go('/explore')"
        >
          {{ SITE_COPY.nav.explore[locale] }}
        </a>
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
          :aria-label="`${SITE_COPY.nav.studio[locale]}: ${activeSurfaceLabel}`"
          :title="`${SITE_COPY.nav.studio[locale]}: ${activeSurfaceLabel}`"
          data-testid="nav-surface-cycle"
          @click="emit('cycle-surface')"
        >
          <Palette :size="15" />
          <span class="hide-sm">{{ activeSurfaceLabel }}</span>
        </button>

        <a
          :href="buildHref('/download')"
          class="m3-btn m3-btn-primary nav-cta"
          data-testid="nav-link-download"
          @click.prevent="go('/download')"
        >
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
      <a :href="buildHref('/')" class="mobile-link" @click.prevent="go('/')">
        {{ SITE_COPY.nav.home[locale] }}
      </a>
      <a :href="buildHref('/docs/overview')" class="mobile-link" @click.prevent="go('/docs/overview')">
        {{ SITE_COPY.nav.docs[locale] }}
      </a>
      <a :href="buildHref('/theme-studio')" class="mobile-link" @click.prevent="go('/theme-studio')">
        {{ SITE_COPY.nav.studio[locale] }}
      </a>
      <a :href="buildHref('/explore')" class="mobile-link" @click.prevent="go('/explore')">
        {{ SITE_COPY.nav.explore[locale] }}
      </a>
      <a :href="buildHref('/download')" class="m3-btn m3-btn-primary" @click.prevent="go('/download')">
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
  max-width: 1200px;
  margin: 0 auto;
  padding: 8px 10px 8px 14px;
  border-radius: 999px;
  background: var(--bg-glass);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  border: 1px solid var(--border-subtle);
  box-shadow: var(--shadow-glass);
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

.nav-logo-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 11px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.14) 0%, rgba(188, 0, 0, 0.18) 65%, transparent 100%);
  border: 1px solid var(--border-subtle);
  flex-shrink: 0;
}

.nav-logo {
  border-radius: 7px;
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
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 13px;
  border-radius: 999px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-secondary);
  transition: color 0.18s ease, background-color 0.18s ease;
}

.nav-link:hover {
  color: var(--text-primary);
  background: var(--bg-pill);
}

.nav-link.active {
  color: var(--text-primary);
  background: var(--bg-nested);
  border: 1px solid var(--border-subtle);
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
  max-width: 1200px;
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

@media (max-width: 1060px) {
  .hide-sm {
    display: none;
  }
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
