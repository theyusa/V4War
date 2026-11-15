<script setup lang="ts">
import { computed, onMounted, ref, watchEffect } from 'vue'
import NavBar from './components/NavBar.vue'
import HeroSection from './components/HeroSection.vue'
import HotspotSuiteSection from './components/HotspotSuiteSection.vue'
import ToolboxSection from './components/ToolboxSection.vue'
import ThemeStudioSection from './components/ThemeStudioSection.vue'
import ProtocolsSection from './components/ProtocolsSection.vue'
import SecurityAndDownloadSection from './components/SecurityAndDownloadSection.vue'
import FaqAndFooter from './components/FaqAndFooter.vue'
import type { Locale } from './data/content'
import { FALLBACK_RELEASE, fetchLatestRelease, type ReleaseInfo } from './utils/releases'
import {
  applyThemeToDocument,
  resolveThemeTokens,
  SEED_PRESETS,
  SURFACE_MODES,
  type SeedPreset,
  type SurfaceMode,
} from './utils/theme'

function detectInitialLocale(): Locale {
  if (typeof navigator !== 'undefined' && navigator.language?.toLowerCase().startsWith('tr')) {
    return 'tr'
  }
  return 'en'
}

const locale = ref<Locale>(detectInitialLocale())
const surfaceMode = ref<SurfaceMode>('pure-amoled')
const activeSeed = ref<SeedPreset>(SEED_PRESETS[0])
const release = ref<ReleaseInfo>(FALLBACK_RELEASE)

const tokens = computed(() => resolveThemeTokens(surfaceMode.value, activeSeed.value))

watchEffect(() => {
  applyThemeToDocument(surfaceMode.value, activeSeed.value)
  if (typeof document !== 'undefined') {
    document.documentElement.lang = locale.value
  }
})

const recommendedDownloadUrl = computed(() => {
  return (
    release.value.apks.find((a) => a.abi === 'arm64-v8a')?.downloadUrl ??
    release.value.apks[0]?.downloadUrl ??
    release.value.htmlUrl
  )
})

function toggleLocale() {
  locale.value = locale.value === 'tr' ? 'en' : 'tr'
}

function cycleSurfaceMode() {
  const idx = SURFACE_MODES.findIndex((m) => m.id === surfaceMode.value)
  surfaceMode.value = SURFACE_MODES[(idx + 1) % SURFACE_MODES.length].id
}

onMounted(async () => {
  release.value = await fetchLatestRelease()
})
</script>

<template>
  <div class="app-root">
    <NavBar
      :locale="locale"
      :version-tag="release.tagName"
      :surface-mode="surfaceMode"
      @toggle-locale="toggleLocale"
      @cycle-surface="cycleSurfaceMode"
    />

    <main>
      <HeroSection
        :locale="locale"
        :version-tag="release.tagName"
        :recommended-download-url="recommendedDownloadUrl"
        :surface-mode="surfaceMode"
        :active-seed="activeSeed"
        @update:surface-mode="surfaceMode = $event"
        @update:seed="activeSeed = $event"
      />

      <HotspotSuiteSection :locale="locale" />

      <ToolboxSection :locale="locale" />

      <ThemeStudioSection
        :locale="locale"
        :surface-mode="surfaceMode"
        :active-seed="activeSeed"
        :tokens="tokens"
        @update:surface-mode="surfaceMode = $event"
        @update:seed="activeSeed = $event"
      />

      <ProtocolsSection :locale="locale" />

      <SecurityAndDownloadSection :locale="locale" :release="release" />

      <FaqAndFooter :locale="locale" />
    </main>
  </div>
</template>

<style scoped>
.app-root {
  min-height: 100vh;
}
</style>
