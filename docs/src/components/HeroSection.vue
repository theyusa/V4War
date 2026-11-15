<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowRight, Download, Github, ShieldCheck, Smartphone, Wifi, Zap } from 'lucide-vue-next'
import type { Locale } from '../data/content'
import { SITE_COPY } from '../data/content'
import type { SeedPreset, SurfaceMode } from '../utils/theme'
import { SEED_PRESETS, SURFACE_MODES } from '../utils/theme'

const props = defineProps<{
  locale: Locale
  versionTag: string
  recommendedDownloadUrl: string
  surfaceMode: SurfaceMode
  activeSeed: SeedPreset
}>()

const emit = defineEmits<{
  'update:surfaceMode': [mode: SurfaceMode]
  'update:seed': [seed: SeedPreset]
}>()

const phoneTab = ref<'live' | 'screenshot'>('live')
const logoUrl = `${import.meta.env.BASE_URL}logo.png`
const lanShotUrl = `${import.meta.env.BASE_URL}lan-sharing.jpg`

const activeSurfaceLabel = computed(() => {
  const found = SURFACE_MODES.find((m) => m.id === props.surfaceMode)
  if (!found) return props.surfaceMode
  return props.locale === 'tr' ? found.labelTr : found.labelEn
})
</script>

<template>
  <section id="top" class="hero section bg-grid-pattern">
    <div class="hero-glow" aria-hidden="true"></div>
    <div class="container hero-grid">
      <div class="hero-copy">
        <div class="hero-badge-row">
          <div class="hero-emblem-wrap">
            <img :src="logoUrl" alt="V4War Emblem" class="hero-emblem" width="46" height="46" />
          </div>
          <div class="eyebrow" style="margin-bottom: 0">
            <ShieldCheck :size="14" />
            <span>{{ SITE_COPY.hero.eyebrow[locale] }}</span>
          </div>
        </div>

        <h1 class="hero-title">
          {{ SITE_COPY.hero.titlePrefix[locale] }}
          <span class="hero-gradient">{{ SITE_COPY.hero.titleHighlight[locale] }}</span>
        </h1>

        <p class="hero-desc">
          {{ SITE_COPY.hero.description[locale] }}
        </p>

        <div class="hero-cta-row">
          <a href="#download" class="m3-btn m3-btn-primary">
            <Download :size="16" />
            <span>{{ SITE_COPY.hero.ctaPrimary[locale] }} ({{ versionTag }})</span>
          </a>

          <a href="#hotspot" class="m3-btn m3-btn-tonal">
            <span>{{ SITE_COPY.hero.ctaSecondary[locale] }}</span>
            <ArrowRight :size="15" />
          </a>

          <a
            href="https://github.com/theyusa/V4War"
            target="_blank"
            rel="noopener noreferrer"
            class="m3-btn m3-btn-tonal"
          >
            <Github :size="16" />
            <span>{{ SITE_COPY.hero.ctaGithub[locale] }}</span>
          </a>
        </div>

        <div class="hero-metrics">
          <div v-for="metric in SITE_COPY.hero.metrics" :key="metric.value" class="metric-card m3-card">
            <div class="metric-value mono">{{ metric.value }}</div>
            <div class="metric-label">{{ metric.label[locale] }}</div>
          </div>
        </div>
      </div>

      <!-- Interactive Material 3 Expressive Phone Showcase -->
      <div class="hero-visual">
        <div class="phone-tabs">
          <button
            type="button"
            class="phone-tab"
            :class="{ active: phoneTab === 'live' }"
            data-testid="phone-tab-live"
            @click="phoneTab = 'live'"
          >
            {{ SITE_COPY.hero.phoneTabLive[locale] }}
          </button>
          <button
            type="button"
            class="phone-tab"
            :class="{ active: phoneTab === 'screenshot' }"
            data-testid="phone-tab-screenshot"
            @click="phoneTab = 'screenshot'"
          >
            {{ SITE_COPY.hero.phoneTabShot[locale] }}
          </button>
        </div>

        <div class="phone-frame m3-card">
          <div class="phone-notch" aria-hidden="true"></div>

          <!-- Tab 1: Interactive V4War Material 3 Expressive Mini-App -->
          <div v-if="phoneTab === 'live'" class="phone-screen" data-testid="phone-live-view">
            <div class="app-topbar">
              <div class="app-brand-mini">
                <span class="app-mini-logo-wrap">
                  <img :src="logoUrl" alt="" width="20" height="20" class="app-mini-logo" />
                </span>
                <span>V4War</span>
              </div>
              <span class="status-chip mono">TUN · 24ms</span>
            </div>

            <div class="app-card m3-nested">
              <div class="app-card-row">
                <div class="squircle" style="width: 36px; height: 36px; border-radius: 12px">
                  <Zap :size="17" />
                </div>
                <div class="app-card-text">
                  <strong>VLESS + Reality · Auto</strong>
                  <span>{{ SITE_COPY.hero.phoneMockup.coreSubtitle[locale] }}</span>
                </div>
                <span
                  class="active-dot"
                  :aria-label="SITE_COPY.hero.phoneMockup.connectedLabel[locale]"
                  :title="SITE_COPY.hero.phoneMockup.connectedLabel[locale]"
                ></span>
              </div>
              <div class="speed-strip mono">
                <span>↑ 2.4 MB/s</span>
                <span>↓ 38.6 MB/s</span>
                <span>Fake-IP</span>
              </div>
            </div>

            <div class="app-card m3-nested">
              <div class="app-card-row">
                <div class="squircle" style="width: 36px; height: 36px; border-radius: 12px">
                  <Wifi :size="17" />
                </div>
                <div class="app-card-text">
                  <strong>{{ SITE_COPY.hero.phoneMockup.hotspotTitle[locale] }}</strong>
                  <span>{{ SITE_COPY.hero.phoneMockup.hotspotSubtitle[locale] }}</span>
                </div>
                <span class="pill-mini mono">UID 2000</span>
              </div>
            </div>

            <div class="mini-studio">
              <div class="mini-studio-title">
                <Smartphone :size="13" />
                <span>Theme Studio · {{ activeSurfaceLabel }}</span>
              </div>
              <div class="mini-surface-row">
                <button
                  v-for="mode in SURFACE_MODES"
                  :key="mode.id"
                  type="button"
                  class="mini-mode-chip"
                  :class="{ active: surfaceMode === mode.id }"
                  @click="emit('update:surfaceMode', mode.id)"
                >
                  {{ locale === 'tr' ? mode.labelTr : mode.labelEn }}
                </button>
              </div>
              <div class="mini-seed-row">
                <button
                  v-for="seed in SEED_PRESETS"
                  :key="seed.id"
                  type="button"
                  class="seed-dot"
                  :class="{ active: activeSeed.id === seed.id }"
                  :style="{ backgroundColor: seed.primary }"
                  :aria-label="locale === 'tr' ? seed.nameTr : seed.nameEn"
                  @click="emit('update:seed', seed)"
                ></button>
              </div>
            </div>

            <a :href="recommendedDownloadUrl" class="m3-btn m3-btn-primary phone-fab">
              <Download :size="15" />
              <span>{{ versionTag }} · arm64-v8a</span>
            </a>
          </div>

          <!-- Tab 2: Real Screenshot from repo -->
          <div v-else class="screenshot-container" data-testid="phone-screenshot-view">
            <div class="screenshot-wrap">
              <img
                :src="lanShotUrl"
                alt="V4War LAN Sharing & Hotspot Gateway Pure AMOLED Interface"
                class="real-screenshot"
              />
            </div>
            <div class="screenshot-caption mono">
              {{ SITE_COPY.hero.phoneMockup.scrollHint[locale] }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.hero {
  padding-top: 56px;
  padding-bottom: 72px;
  overflow: hidden;
}

.hero-glow {
  position: absolute;
  top: -160px;
  left: 50%;
  transform: translateX(-50%);
  width: 820px;
  height: 460px;
  background: radial-gradient(
    ellipse at center,
    color-mix(in srgb, var(--seed-primary) 26%, transparent) 0%,
    transparent 70%
  );
  filter: blur(48px);
  pointer-events: none;
}

.hero-grid {
  position: relative;
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  align-items: center;
  gap: 48px;
}

.hero-badge-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 22px;
  flex-wrap: wrap;
}

.hero-emblem-wrap {
  width: 56px;
  height: 56px;
  border-radius: 18px;
  border: 1px solid var(--border-subtle);
  background: radial-gradient(circle, rgba(255, 255, 255, 0.14) 0%, rgba(188, 0, 0, 0.16) 62%, var(--bg-surface-elevated) 100%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-glow);
  flex-shrink: 0;
}

.hero-emblem {
  display: block;
  object-fit: contain;
}

.hero-title {
  margin: 0 0 18px;
  font-size: clamp(36px, 5vw, 60px);
  font-weight: 800;
  line-height: 1.06;
  letter-spacing: -0.035em;
}

.hero-gradient {
  display: block;
  background: linear-gradient(115deg, var(--seed-accent) 10%, var(--text-primary) 92%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero-desc {
  margin: 0 0 28px;
  font-size: 16.5px;
  line-height: 1.7;
  color: var(--text-secondary);
  max-width: 600px;
}

.hero-cta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 34px;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  padding: 14px 12px;
  border-radius: 18px;
}

.metric-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--seed-accent);
  margin-bottom: 4px;
}

.metric-label {
  font-size: 12px;
  line-height: 1.35;
  color: var(--text-secondary);
}

.hero-visual {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.phone-tabs {
  display: inline-flex;
  padding: 4px;
  border-radius: 999px;
  background: var(--bg-surface);
  border: 1px solid var(--border-muted);
  gap: 4px;
}

.phone-tab {
  border: none;
  background: transparent;
  color: var(--text-secondary);
  padding: 7px 14px;
  border-radius: 999px;
  font-size: 12.5px;
  font-weight: 600;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.phone-tab.active {
  background: var(--seed-primary);
  color: var(--seed-on-primary);
}

.phone-frame {
  width: 100%;
  max-width: 344px;
  border-radius: 34px;
  padding: 14px;
  border: 1.5px solid var(--border-subtle);
  box-shadow: var(--shadow-glow), var(--shadow-card);
  position: relative;
}

.phone-notch {
  width: 72px;
  height: 6px;
  border-radius: 999px;
  background: var(--border-muted);
  margin: 2px auto 12px;
}

.phone-screen {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.app-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 2px;
}

.app-brand-mini {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 800;
  font-size: 15px;
}

.app-mini-logo-wrap {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 8px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.14) 0%, rgba(188, 0, 0, 0.16) 68%, transparent 100%);
}

.app-mini-logo {
  border-radius: 6px;
  object-fit: contain;
}

.status-chip {
  font-size: 11px;
  font-weight: 600;
  padding: 4px 9px;
  border-radius: 999px;
  background: var(--bg-nested);
  border: 1px solid var(--border-subtle);
  color: var(--seed-accent);
}

.app-card {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.app-card-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.app-card-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.app-card-text strong {
  font-size: 13px;
  color: var(--text-primary);
}

.app-card-text span {
  font-size: 11.5px;
  color: var(--text-secondary);
}

.active-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #00d685;
  box-shadow: 0 0 10px #00d685;
}

.speed-strip {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: var(--text-secondary);
  padding-top: 6px;
  border-top: 1px solid var(--border-muted);
}

.pill-mini {
  font-size: 10px;
  padding: 3px 7px;
  border-radius: 999px;
  border: 1px solid var(--border-muted);
  color: var(--text-secondary);
}

.mini-studio {
  padding: 11px 12px;
  border-radius: 16px;
  border: 1px solid var(--border-subtle);
  background: var(--bg-surface-elevated);
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.mini-studio-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.mini-surface-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 5px;
}

.mini-mode-chip {
  padding: 5px 8px;
  border-radius: 10px;
  border: 1px solid var(--border-muted);
  background: transparent;
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 600;
}

.mini-mode-chip.active {
  border-color: var(--seed-primary);
  background: var(--bg-nested);
  color: var(--text-primary);
}

.mini-seed-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 2px;
}

.seed-dot {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 2px solid transparent;
  transition: transform 0.16s ease, border-color 0.16s ease;
}

.seed-dot.active {
  transform: scale(1.16);
  border-color: var(--text-primary);
}

.phone-fab {
  width: 100%;
  margin-top: 2px;
}

.screenshot-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.screenshot-wrap {
  display: block;
  border-radius: 20px;
  overflow-y: auto;
  overflow-x: hidden;
  border: 1px solid var(--border-muted);
  background: #000000;
  height: 432px;
  scrollbar-width: thin;
  scrollbar-color: var(--seed-primary) #0b0b0d;
}

.real-screenshot {
  display: block;
  width: 100%;
  height: auto;
}

.screenshot-caption {
  text-align: center;
  font-size: 11px;
  color: var(--text-muted);
}

@media (max-width: 960px) {
  .hero-grid {
    grid-template-columns: 1fr;
    gap: 36px;
  }

  .hero-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
