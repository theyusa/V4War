<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watchEffect } from 'vue'
import type { Locale } from './data/content'
import type { MonochromeMode, SeedPreset, SurfaceMode } from './utils/theme'
import {
  applyThemeToDocument,
  createCustomSeedPreset,
  normalizeHex,
  resolveThemeTokens,
  SEED_PRESETS,
  SURFACE_MODES,
} from './utils/theme'
import { FALLBACK_RELEASE, fetchLatestRelease } from './utils/releases'
import type { ReleaseInfo } from './utils/releases'
import { buildHref, parseAppLocation } from './utils/router'
import type { ParsedRoute } from './utils/router'
import NavBar from './components/NavBar.vue'
import HomeView from './components/HomeView.vue'
import DocsView from './components/DocsView.vue'
import ThemeStudioSection from './components/ThemeStudioSection.vue'
import ExploreView from './components/ExploreView.vue'
import SecurityAndDownloadSection from './components/SecurityAndDownloadSection.vue'
import FaqAndFooter from './components/FaqAndFooter.vue'

function detectInitialLocale(): Locale {
  if (typeof window === 'undefined') return 'en'
  try {
    const saved = window.localStorage.getItem('v4war-locale')
    if (saved === 'tr' || saved === 'en') return saved
  } catch {
    // ignore storage errors
  }
  return navigator.language?.toLowerCase().startsWith('tr') ? 'tr' : 'en'
}

function detectInitialSurface(): SurfaceMode {
  if (typeof window === 'undefined') return 'pure-amoled'
  try {
    const saved = window.localStorage.getItem('v4war-surface') as SurfaceMode | null
    if (saved && SURFACE_MODES.some((s) => s.id === saved)) return saved
  } catch {
    // ignore
  }
  return 'pure-amoled'
}

function detectInitialMonochrome(): MonochromeMode {
  if (typeof window === 'undefined') return 'none'
  try {
    const saved = window.localStorage.getItem('v4war-monochrome') as MonochromeMode | null
    if (saved === 'black' || saved === 'white' || saved === 'light-gray' || saved === 'none') {
      return saved
    }
  } catch {
    // ignore
  }
  return 'none'
}

function detectInitialSeed(): SeedPreset {
  if (typeof window === 'undefined') return SEED_PRESETS[0]
  try {
    const savedId = window.localStorage.getItem('v4war-seed')
    if (savedId === 'custom') {
      const savedHex = window.localStorage.getItem('v4war-custom-seed')
      if (savedHex && normalizeHex(savedHex)) {
        return createCustomSeedPreset(savedHex)
      }
    }
    const found = SEED_PRESETS.find((s) => s.id === savedId)
    if (found) return found
  } catch {
    // ignore
  }
  return SEED_PRESETS[0]
}

function readCurrentRoute(): ParsedRoute {
  if (typeof window === 'undefined') {
    return { page: 'home', docSlug: 'overview', hash: '', canonicalPath: '/' }
  }
  return parseAppLocation(window.location.pathname, window.location.hash, window.location.search)
}

const locale = ref<Locale>(detectInitialLocale())
const surfaceMode = ref<SurfaceMode>(detectInitialSurface())
const monochromeMode = ref<MonochromeMode>(detectInitialMonochrome())
const activeSeed = ref<SeedPreset>(detectInitialSeed())
const release = ref<ReleaseInfo>(FALLBACK_RELEASE)
const route = ref<ParsedRoute>(readCurrentRoute())

const tokens = computed(() =>
  resolveThemeTokens(surfaceMode.value, activeSeed.value, monochromeMode.value),
)

const recommendedDownloadUrl = computed(
  () =>
    release.value.apks.find((a) => a.recommended)?.downloadUrl ??
    release.value.apks[0]?.downloadUrl ??
    release.value.htmlUrl,
)

watchEffect(() => {
  applyThemeToDocument(surfaceMode.value, activeSeed.value, monochromeMode.value)
  if (typeof document !== 'undefined') {
    document.documentElement.lang = locale.value
  }
})

function toggleLocale() {
  locale.value = locale.value === 'en' ? 'tr' : 'en'
  try {
    window.localStorage.setItem('v4war-locale', locale.value)
  } catch {
    // ignore
  }
}

function setSurface(mode: SurfaceMode) {
  surfaceMode.value = mode
  if (mode === 'light' || mode === 'system') {
    monochromeMode.value = 'none'
  }
  try {
    window.localStorage.setItem('v4war-surface', mode)
    window.localStorage.setItem('v4war-monochrome', monochromeMode.value)
  } catch {
    // ignore
  }
}

function setMonochrome(mode: MonochromeMode) {
  monochromeMode.value = mode
  try {
    window.localStorage.setItem('v4war-monochrome', mode)
  } catch {
    // ignore
  }
}

function cycleSurface() {
  const order: SurfaceMode[] = ['pure-amoled', 'amoled', 'dark', 'light', 'system']
  const idx = order.indexOf(surfaceMode.value)
  setSurface(order[(idx + 1) % order.length])
}

function setSeed(seed: SeedPreset) {
  activeSeed.value = seed
  monochromeMode.value = 'none'
  try {
    window.localStorage.setItem('v4war-seed', seed.id)
    window.localStorage.setItem('v4war-monochrome', 'none')
    if (seed.id === 'custom') {
      window.localStorage.setItem('v4war-custom-seed', seed.primary)
    }
  } catch {
    // ignore
  }
}

function navigate(routePath: string) {
  const [pathPart, hashPart] = routePath.split('#')
  const hash = hashPart ? `#${hashPart}` : ''
  const parsed = parseAppLocation(pathPart || '/', hash, '', '')
  route.value = parsed

  if (typeof window !== 'undefined') {
    const targetUrl = `${buildHref(parsed.canonicalPath)}${hash}`
    if (`${window.location.pathname}${window.location.hash}` !== targetUrl) {
      window.history.pushState({ route: parsed.canonicalPath }, '', targetUrl)
    }
    if (!hash && typeof window.scrollTo === 'function') {
      window.scrollTo({ top: 0 })
    }
  }
}

function handlePopState() {
  route.value = readCurrentRoute()
}

onMounted(async () => {
  window.addEventListener('popstate', handlePopState)
  const fetched = await fetchLatestRelease()
  if (fetched) {
    release.value = fetched
  }
})

onUnmounted(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('popstate', handlePopState)
  }
})
</script>

<template>
  <div class="app-shell">
    <NavBar
      :locale="locale"
      :version-tag="release.tagName"
      :surface-mode="surfaceMode"
      :current-page="route.page"
      @toggle-locale="toggleLocale"
      @cycle-surface="cycleSurface"
      @navigate="navigate"
    />

    <main>
      <HomeView
        v-if="route.page === 'home'"
        :locale="locale"
        :version-tag="release.tagName"
        :recommended-download-url="recommendedDownloadUrl"
        :surface-mode="surfaceMode"
        :active-seed="activeSeed"
        @update:surface-mode="setSurface"
        @update:seed="setSeed"
        @navigate="navigate"
      />

      <DocsView
        v-else-if="route.page === 'docs'"
        :locale="locale"
        :doc-slug="route.docSlug"
        :hash="route.hash"
        @navigate="navigate"
      />

      <div v-else-if="route.page === 'theme-studio'" class="subpage-offset">
        <ThemeStudioSection
          :locale="locale"
          :surface-mode="surfaceMode"
          :monochrome-mode="monochromeMode"
          :active-seed="activeSeed"
          :tokens="tokens"
          @update:surface-mode="setSurface"
          @update:monochrome-mode="setMonochrome"
          @update:seed="setSeed"
        />
      </div>

      <ExploreView
        v-else-if="route.page === 'explore'"
        :locale="locale"
        @navigate="navigate"
      />

      <div v-else-if="route.page === 'download'" class="subpage-offset">
        <SecurityAndDownloadSection :locale="locale" :release="release" />
      </div>
    </main>

    <FaqAndFooter :locale="locale" :show-faq="false" :show-footer="true" @navigate="navigate" />
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-shell > main {
  flex: 1;
}

.subpage-offset {
  padding-top: 72px;
}
</style>
