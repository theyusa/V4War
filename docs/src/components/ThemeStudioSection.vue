<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  Check,
  ChevronDown,
  ChevronUp,
  Contrast,
  Eye,
  Layers,
  Palette,
  Plus,
  Send,
  Sparkles,
  X,
} from 'lucide-vue-next'
import type { Locale } from '../data/content'
import { SITE_COPY } from '../data/content'
import type { MonochromeMode, ResolvedThemeTokens, SeedPreset, SurfaceMode } from '../utils/theme'
import {
  blendHex,
  createCustomSeedPreset,
  MONOCHROME_THEMES,
  normalizeHex,
  relativeLuminance,
  SEED_PRESETS,
  SURFACE_MODES,
} from '../utils/theme'

const props = defineProps<{
  locale: Locale
  surfaceMode: SurfaceMode
  monochromeMode: MonochromeMode
  activeSeed: SeedPreset
  tokens: ResolvedThemeTokens
}>()

const emit = defineEmits<{
  'update:surfaceMode': [mode: SurfaceMode]
  'update:monochromeMode': [mode: MonochromeMode]
  'update:seed': [seed: SeedPreset]
}>()

const surfaceExpanded = ref(true)
const paletteExpanded = ref(true)
const showRealComparison = ref(false)
const customDialogOpen = ref(false)

const hexInput = ref(props.activeSeed.primary)
const redChannel = ref(225)
const greenChannel = ref(29)
const blueChannel = ref(36)

const appearanceShotUrl = `${import.meta.env.BASE_URL}screenshots/appearance-theme-studio.webp`

function syncChannelsFromHex(hex: string) {
  const clean = hex.replace('#', '')
  if (clean.length === 6) {
    redChannel.value = parseInt(clean.slice(0, 2), 16) || 0
    greenChannel.value = parseInt(clean.slice(2, 4), 16) || 0
    blueChannel.value = parseInt(clean.slice(4, 6), 16) || 0
  }
}

function rgbToHex(r: number, g: number, b: number): string {
  const clamp = (v: number) => Math.max(0, Math.min(255, Math.round(v)))
  return `#${((1 << 24) + (clamp(r) << 16) + (clamp(g) << 8) + clamp(b)).toString(16).slice(1).toUpperCase()}`
}

watch(
  () => props.activeSeed.primary,
  (val) => {
    hexInput.value = val
    syncChannelsFromHex(val)
  },
  { immediate: true },
)

function openCustomDialog() {
  hexInput.value = props.activeSeed.primary
  syncChannelsFromHex(props.activeSeed.primary)
  customDialogOpen.value = true
}

function onSliderChange() {
  hexInput.value = rgbToHex(redChannel.value, greenChannel.value, blueChannel.value)
}

function onHexInput(val: string) {
  hexInput.value = val
  const norm = normalizeHex(val)
  if (norm) {
    syncChannelsFromHex(norm)
  }
}

function applyCustomColor() {
  const norm = normalizeHex(hexInput.value) ?? rgbToHex(redChannel.value, greenChannel.value, blueChannel.value)
  emit('update:monochromeMode', 'none')
  emit('update:seed', createCustomSeedPreset(norm))
  customDialogOpen.value = false
}

const activeBadgeLabel = computed(() => {
  if (props.monochromeMode !== 'none') {
    const foundMono = MONOCHROME_THEMES.find((m) => m.id === props.monochromeMode)
    if (foundMono) return props.locale === 'tr' ? foundMono.labelTr : foundMono.labelEn
  }
  const foundSurf = SURFACE_MODES.find((m) => m.id === props.surfaceMode)
  if (!foundSurf) return props.surfaceMode
  return props.locale === 'tr' ? foundSurf.labelTr : foundSurf.labelEn
})

const activeSeedDisplay = computed(() => {
  if (props.monochromeMode !== 'none') {
    const foundMono = MONOCHROME_THEMES.find((m) => m.id === props.monochromeMode)
    if (foundMono) return props.locale === 'tr' ? foundMono.labelTr : foundMono.labelEn
  }
  return props.locale === 'tr' ? props.activeSeed.nameTr : props.activeSeed.nameEn
})

const sampleAccent = computed(() => {
  if (props.monochromeMode !== 'none') return '#00B96B'
  return props.activeSeed.primary
})

function modeThumbStyle(id: SurfaceMode) {
  const seed = sampleAccent.value
  if (id === 'pure-amoled') {
    return {
      winBg: '#000000',
      box1Bg: '#000000',
      box2Bg: blendHex('#000000', seed, 0.08),
      stroke: seed,
    }
  }
  if (id === 'amoled') {
    return {
      winBg: '#000000',
      box1Bg: blendHex('#000000', seed, 0.16),
      box2Bg: blendHex('#000000', seed, 0.28),
      stroke: 'rgba(255,255,255,0.16)',
    }
  }
  if (id === 'dark') {
    return {
      winBg: '#141619',
      box1Bg: '#23272C',
      box2Bg: '#2D3238',
      stroke: 'rgba(255,255,255,0.14)',
    }
  }
  if (id === 'light') {
    return {
      winBg: '#F1F5F9',
      box1Bg: '#FFFFFF',
      box2Bg: '#E2E8F0',
      stroke: 'rgba(0,0,0,0.14)',
    }
  }
  return {
    winBg: 'linear-gradient(90deg, #F1F5F9 50%, #141619 50%)',
    box1Bg: '#24282D',
    box2Bg: '#FFFFFF',
    stroke: 'rgba(136,136,136,0.28)',
  }
}

function selectSurface(mode: SurfaceMode) {
  emit('update:monochromeMode', 'none')
  emit('update:surfaceMode', mode)
}

function selectMonochrome(mode: Exclude<MonochromeMode, 'none'>) {
  emit('update:monochromeMode', mode)
}

function selectSeed(seed: SeedPreset) {
  emit('update:monochromeMode', 'none')
  emit('update:seed', seed)
}

function checkColorForBg(hex: string): string {
  return relativeLuminance(hex) > 0.52 ? '#000000' : '#FFFFFF'
}

const liveStatsStyle = computed(() => {
  if (props.monochromeMode === 'none' && props.tokens.effectiveSurface === 'light') {
    const bg = props.tokens.seedPrimary
    return {
      backgroundColor: bg,
      borderColor: 'transparent',
      color: relativeLuminance(bg) > 0.45 ? '#1E293B' : '#FFFFFF',
      dotColor: '#FFFFFF',
    }
  }
  return null
})

const liveFabStyle = computed(() => {
  let bg = props.tokens.seedPrimary
  if (props.monochromeMode === 'black') {
    bg = '#242424'
  } else if (props.monochromeMode === 'white' || props.monochromeMode === 'light-gray') {
    bg = '#1F2937'
  } else if (relativeLuminance(bg) > 0.85) {
    bg = '#242424'
  }
  const color = relativeLuminance(bg) > 0.45 ? '#000000' : '#FFFFFF'
  return { backgroundColor: bg, color }
})
</script>

<template>
  <section id="studio" class="section">
    <div class="container">
      <div class="section-header studio-top-head">
        <div>
          <div class="eyebrow">
            <Palette :size="14" />
            <span>{{ SITE_COPY.studio.eyebrow[locale] }}</span>
          </div>
          <h1 class="section-title">{{ SITE_COPY.studio.title[locale] }}</h1>
          <p class="section-subtitle">{{ SITE_COPY.studio.subtitle[locale] }}</p>
        </div>

        <button
          type="button"
          class="m3-btn m3-btn-tonal compare-toggle"
          data-testid="toggle-real-comparison"
          @click="showRealComparison = !showRealComparison"
        >
          <Eye :size="16" />
          <span>{{ SITE_COPY.studio.compareShotLabel[locale] }}</span>
        </button>
      </div>

      <div class="studio-workspace" :class="{ 'has-comparison': showRealComparison }">
        <!-- Left: 1:1 V4War Appearance Replica -->
        <div class="appearance-stack">
          <!-- Card 1: Live Preview (Always Open, matching ThemeStudioPreference.kt) -->
          <div class="m3-card studio-box">
            <div class="box-header">
              <div class="squircle box-badge">
                <Contrast :size="19" />
              </div>
              <div class="box-titles">
                <strong>{{ SITE_COPY.studio.livePreviewLabel[locale] }}</strong>
                <span data-testid="studio-active-badge">{{ activeBadgeLabel }}</span>
              </div>
            </div>

            <div class="box-body">
              <div class="live-canvas" data-testid="live-preview-canvas">
                <div class="live-canvas-top">
                  <span class="live-app-name">V4War</span>
                  <span class="live-mode-pill">{{ activeBadgeLabel }}</span>
                </div>

                <div class="live-cards-row">
                  <!-- Selected miniature profile card -->
                  <div class="mini-profile-card selected">
                    <span class="mini-accent-bar"></span>
                    <div class="mini-bars">
                      <span class="mini-bar-primary"></span>
                      <span class="mini-bar-secondary"></span>
                    </div>
                    <span class="mini-latency mono active">38 ms</span>
                  </div>

                  <!-- Unselected miniature profile card -->
                  <div class="mini-profile-card">
                    <div class="mini-bars">
                      <span class="mini-bar-primary muted"></span>
                      <span class="mini-bar-secondary"></span>
                    </div>
                    <span class="mini-latency mono">64 ms</span>
                  </div>
                </div>

                <div class="live-bottom-row">
                  <div
                    class="live-stats-pill mono"
                    :style="
                      liveStatsStyle
                        ? {
                            backgroundColor: liveStatsStyle.backgroundColor,
                            borderColor: liveStatsStyle.borderColor,
                            color: liveStatsStyle.color,
                          }
                        : undefined
                    "
                  >
                    <span
                      class="live-stats-dot"
                      :style="liveStatsStyle ? { backgroundColor: liveStatsStyle.dotColor } : undefined"
                    ></span>
                    <span>↑ 1.8 MB/s &nbsp;•&nbsp; ↓ 24.6 MB/s</span>
                  </div>
                  <div class="live-fab" :style="liveFabStyle" aria-hidden="true">
                    <Send :size="14" />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Card 2: Surface Style + Monochrome Themes -->
          <div class="m3-card studio-box">
            <button
              type="button"
              class="box-header clickable"
              :aria-expanded="surfaceExpanded"
              @click="surfaceExpanded = !surfaceExpanded"
            >
              <div class="squircle box-badge">
                <Layers :size="19" />
              </div>
              <div class="box-titles">
                <strong>{{ SITE_COPY.studio.surfaceLabel[locale] }}</strong>
                <span>{{ activeBadgeLabel }}</span>
              </div>
              <span class="chevron-circle">
                <ChevronUp v-if="surfaceExpanded" :size="16" />
                <ChevronDown v-else :size="16" />
              </span>
            </button>

            <div v-if="surfaceExpanded" class="box-body">
              <div class="mode-strip" role="radiogroup" aria-label="Surface Style">
                <button
                  v-for="mode in SURFACE_MODES"
                  :key="mode.id"
                  type="button"
                  class="mode-thumb-card"
                  :class="{ active: monochromeMode === 'none' && surfaceMode === mode.id }"
                  :data-testid="`surface-btn-${mode.id}`"
                  @click="selectSurface(mode.id)"
                >
                  <div
                    class="mode-thumb-canvas"
                    :style="{
                      background: modeThumbStyle(mode.id).winBg,
                      borderColor: modeThumbStyle(mode.id).stroke,
                    }"
                  >
                    <span
                      class="thumb-box-1"
                      :style="{
                        backgroundColor: modeThumbStyle(mode.id).box1Bg,
                        borderColor: modeThumbStyle(mode.id).stroke,
                      }"
                    ></span>
                    <span
                      class="thumb-box-2"
                      :style="{
                        backgroundColor: modeThumbStyle(mode.id).box2Bg,
                        borderColor: modeThumbStyle(mode.id).stroke,
                      }"
                    ></span>
                    <span class="thumb-dot" :style="{ backgroundColor: sampleAccent }"></span>
                  </div>
                  <span class="mode-thumb-label">{{ locale === 'tr' ? mode.labelTr : mode.labelEn }}</span>
                </button>
              </div>

              <div class="sub-label">{{ SITE_COPY.studio.monochromeLabel[locale] }}</div>
              <div class="mono-row">
                <button
                  v-for="mono in MONOCHROME_THEMES"
                  :key="mono.id"
                  type="button"
                  class="mono-chip"
                  :class="{ active: monochromeMode === mono.id }"
                  :data-testid="`mono-btn-${mono.id}`"
                  @click="selectMonochrome(mono.id)"
                >
                  <span
                    class="mono-dot"
                    :style="{ backgroundColor: mono.dotColor, borderColor: mono.dotBorder }"
                  ></span>
                  <span>{{ locale === 'tr' ? mono.labelTr : mono.labelEn }}</span>
                </button>
              </div>
            </div>
          </div>

          <!-- Theme Category Header -->
          <div class="theme-category-label">{{ SITE_COPY.studio.themeSectionLabel[locale] }}</div>

          <!-- Card 3: Seed Color (6-column swatch grid + Material You + Custom) -->
          <div class="m3-card studio-box">
            <div class="box-header">
              <div class="squircle box-badge">
                <Sparkles :size="19" />
              </div>
              <button
                type="button"
                class="box-titles clickable-inline"
                @click="paletteExpanded = !paletteExpanded"
              >
                <strong>{{ SITE_COPY.studio.seedLabel[locale] }}</strong>
                <span class="active-seed-sub">
                  <span class="active-seed-dot" :style="{ backgroundColor: tokens.seedPrimary }"></span>
                  <span>{{ activeSeedDisplay }}</span>
                </span>
              </button>

              <button
                type="button"
                class="custom-pill-btn"
                :class="{ active: activeSeed.isCustom && monochromeMode === 'none' }"
                data-testid="custom-seed-btn"
                @click="openCustomDialog"
              >
                {{ SITE_COPY.studio.customBtnLabel[locale] }}
              </button>

              <button
                type="button"
                class="chevron-circle"
                :aria-expanded="paletteExpanded"
                aria-label="Toggle color palette"
                @click="paletteExpanded = !paletteExpanded"
              >
                <ChevronUp v-if="paletteExpanded" :size="16" />
                <ChevronDown v-else :size="16" />
              </button>
            </div>

            <div v-if="paletteExpanded" class="box-body">
              <div class="swatch-grid">
                <button
                  v-for="seed in SEED_PRESETS"
                  :key="seed.id"
                  type="button"
                  class="swatch-cell"
                  :class="{
                    active: monochromeMode === 'none' && activeSeed.id === seed.id,
                  }"
                  :title="locale === 'tr' ? seed.nameTr : seed.nameEn"
                  :aria-label="locale === 'tr' ? seed.nameTr : seed.nameEn"
                  :data-testid="`seed-btn-${seed.id}`"
                  @click="selectSeed(seed)"
                >
                  <span
                    v-if="monochromeMode === 'none' && activeSeed.id === seed.id"
                    class="swatch-ring"
                    :style="{ borderColor: seed.primary }"
                  ></span>
                  <span
                    class="swatch-circle"
                    :class="{ 'is-monet': seed.isMonet }"
                    :style="{
                      background: seed.isMonet
                        ? `linear-gradient(135deg, ${seed.primary} 0%, #A9B8E8 100%)`
                        : seed.primary,
                    }"
                  >
                    <Check
                      v-if="monochromeMode === 'none' && activeSeed.id === seed.id"
                      :size="16"
                      :color="checkColorForBg(seed.primary)"
                      :stroke-width="3"
                    />
                  </span>
                </button>

                <!-- Custom '+' Swatch -->
                <button
                  type="button"
                  class="swatch-cell"
                  :class="{ active: monochromeMode === 'none' && activeSeed.isCustom }"
                  :title="SITE_COPY.studio.customBtnLabel[locale]"
                  :aria-label="SITE_COPY.studio.customBtnLabel[locale]"
                  data-testid="seed-btn-custom-plus"
                  @click="openCustomDialog"
                >
                  <span
                    v-if="monochromeMode === 'none' && activeSeed.isCustom"
                    class="swatch-ring"
                    :style="{ borderColor: activeSeed.primary }"
                  ></span>
                  <span
                    class="swatch-circle"
                    :style="{ backgroundColor: activeSeed.isCustom ? activeSeed.primary : '#00D685' }"
                  >
                    <Check
                      v-if="monochromeMode === 'none' && activeSeed.isCustom"
                      :size="16"
                      :color="checkColorForBg(activeSeed.primary)"
                      :stroke-width="3"
                    />
                    <Plus v-else :size="18" color="#04140D" :stroke-width="2.6" />
                  </span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Right: 3-Tone Live Token Inspector (+ optional Real App Screenshot) -->
        <div class="inspector-col">
          <div class="m3-card studio-box inspector-card">
            <div class="box-header">
              <div class="squircle box-badge">
                <Palette :size="19" />
              </div>
              <div class="box-titles">
                <strong>{{ SITE_COPY.studio.tonesLabel[locale] }}</strong>
                <span class="mono">{{ tokens.effectiveSurface }} · {{ tokens.seedPrimary }}</span>
              </div>
            </div>

            <div class="box-body token-list">
              <div class="token-row m3-nested">
                <span class="token-swatch" :style="{ backgroundColor: tokens.tone1 }"></span>
                <div class="token-meta">
                  <strong>{{ SITE_COPY.studio.tone1Label[locale] }}</strong>
                  <span class="mono">{{ tokens.tone1 }}</span>
                </div>
              </div>

              <div class="token-row m3-nested">
                <span class="token-swatch" :style="{ backgroundColor: tokens.tone2 }"></span>
                <div class="token-meta">
                  <strong>{{ SITE_COPY.studio.tone2Label[locale] }}</strong>
                  <span class="mono">{{ tokens.tone2 }}</span>
                </div>
              </div>

              <div class="token-row m3-nested">
                <span class="token-swatch" :style="{ backgroundColor: tokens.tone3 }"></span>
                <div class="token-meta">
                  <strong>{{ SITE_COPY.studio.tone3Label[locale] }}</strong>
                  <span class="mono">{{ tokens.tone3 }}</span>
                </div>
              </div>

              <div class="token-row m3-nested">
                <span class="token-swatch" :style="{ backgroundColor: tokens.seedAccent }"></span>
                <div class="token-meta">
                  <strong>{{ locale === 'tr' ? 'Vurgu Rengi (Accent)' : 'Effective Accent Ink' }}</strong>
                  <span class="mono">{{ tokens.seedAccent }}</span>
                </div>
              </div>
            </div>
          </div>

          <div v-if="showRealComparison" class="m3-card real-shot-card" data-testid="real-appearance-shot">
            <div class="real-shot-head mono">
              <span>V4War · Settings → Appearance</span>
            </div>
            <img
              :src="appearanceShotUrl"
              alt="Real V4War Appearance & Theme Studio Android Screen"
              class="real-shot-img"
            />
          </div>
        </div>
      </div>

      <!-- Custom Color HEX + RGB Modal (matching ColorPickerPreference.showCustomColorDialog) -->
      <div v-if="customDialogOpen" class="modal-scrim" @click.self="customDialogOpen = false">
        <div class="m3-card custom-dialog" role="dialog" aria-modal="true" aria-label="Custom Seed Color">
          <div class="dialog-head">
            <strong>{{ locale === 'tr' ? 'Özel Tohum Rengi' : 'Custom Seed Color' }}</strong>
            <button type="button" class="chevron-circle" @click="customDialogOpen = false">
              <X :size="16" />
            </button>
          </div>

          <div class="dialog-preview m3-nested">
            <span
              class="dialog-dot"
              :style="{ backgroundColor: rgbToHex(redChannel, greenChannel, blueChannel) }"
            ></span>
            <label class="hex-field">
              <span>HEX</span>
              <input
                :value="hexInput"
                type="text"
                maxlength="7"
                class="mono"
                data-testid="custom-hex-input"
                @input="onHexInput(($event.target as HTMLInputElement).value)"
              />
            </label>
          </div>

          <div class="sliders-card m3-nested">
            <label class="slider-row">
              <span class="ch-badge r">R</span>
              <input v-model.number="redChannel" type="range" min="0" max="255" @input="onSliderChange" />
              <span class="mono ch-val">{{ redChannel }}</span>
            </label>
            <label class="slider-row">
              <span class="ch-badge g">G</span>
              <input v-model.number="greenChannel" type="range" min="0" max="255" @input="onSliderChange" />
              <span class="mono ch-val">{{ greenChannel }}</span>
            </label>
            <label class="slider-row">
              <span class="ch-badge b">B</span>
              <input v-model.number="blueChannel" type="range" min="0" max="255" @input="onSliderChange" />
              <span class="mono ch-val">{{ blueChannel }}</span>
            </label>
          </div>

          <div class="dialog-actions">
            <button type="button" class="m3-btn m3-btn-tonal" @click="customDialogOpen = false">
              {{ locale === 'tr' ? 'İptal' : 'Cancel' }}
            </button>
            <button
              type="button"
              class="m3-btn m3-btn-primary"
              data-testid="custom-color-apply"
              @click="applyCustomColor"
            >
              {{ locale === 'tr' ? 'Uygula' : 'Apply' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.studio-top-head {
  max-width: none;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}

.studio-top-head > div {
  max-width: 700px;
}

.studio-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(300px, 0.75fr);
  gap: 20px;
  align-items: start;
}

.appearance-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.studio-box {
  overflow: hidden;
}

.box-header {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border: none;
  background: transparent;
  color: var(--text-primary);
  text-align: left;
}

.box-header.clickable {
  cursor: pointer;
}

.box-badge {
  width: 40px;
  height: 40px;
  border-radius: 14px;
}

.box-titles {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.clickable-inline {
  border: none;
  background: transparent;
  color: inherit;
  padding: 0;
  text-align: left;
  cursor: pointer;
}

.box-titles strong {
  font-size: 15.5px;
  font-weight: 700;
}

.box-titles span {
  font-size: 12.5px;
  color: var(--text-secondary);
}

.active-seed-sub {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.active-seed-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  border: 1px solid var(--border-muted);
  display: inline-block;
}

.chevron-circle {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 1px solid var(--border-subtle);
  background: var(--bg-nested);
  color: var(--text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.custom-pill-btn {
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid var(--border-subtle);
  background: var(--bg-nested);
  color: var(--text-primary);
  font-size: 12px;
  font-weight: 700;
}

.custom-pill-btn.active {
  border-color: var(--seed-primary);
}

.box-body {
  padding: 0 16px 16px;
}

/* Live Preview Card matching ThemeStudioPreference.buildLivePreviewCanvas */
.live-canvas {
  border-radius: 16px;
  background: var(--bg-canvas);
  border: 1px solid var(--border-strong);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.live-canvas-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.live-app-name {
  font-size: 14px;
  font-weight: 800;
  color: var(--text-primary);
}

.live-mode-pill {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  color: var(--seed-accent);
  background: color-mix(in srgb, var(--seed-primary) 16%, transparent);
  border: 1px solid color-mix(in srgb, var(--seed-accent) 45%, transparent);
}

.live-cards-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.mini-profile-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: 12px;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
}

.mini-profile-card.selected {
  background: var(--bg-nested);
  border-color: var(--border-strong);
}

.mini-accent-bar {
  width: 3.5px;
  height: 26px;
  border-radius: 4px;
  background: var(--seed-accent);
  flex-shrink: 0;
}

.mini-bars {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.mini-bar-primary {
  display: block;
  width: 68%;
  height: 7px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--text-primary) 84%, transparent);
}

.mini-bar-primary.muted {
  background: color-mix(in srgb, var(--text-primary) 60%, transparent);
}

.mini-bar-secondary {
  display: block;
  width: 44%;
  height: 5px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--text-primary) 22%, transparent);
}

.mini-latency {
  font-size: 10.5px;
  font-weight: 700;
  color: var(--text-muted);
  white-space: nowrap;
}

.mini-latency.active {
  color: var(--seed-accent);
}

.live-bottom-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.live-stats-pill {
  flex: 1;
  height: 30px;
  padding: 0 12px;
  border-radius: 14px;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: var(--text-primary);
}

.live-stats-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--seed-accent);
  flex-shrink: 0;
}

.live-fab {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--seed-primary);
  color: var(--seed-on-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* Surface Mode Horizontal Thumbnail Strip */
.mode-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.mode-thumb-card {
  padding: 7px 7px 8px;
  border-radius: 15px;
  border: 1px solid var(--border-subtle);
  background: var(--bg-nested);
  color: var(--text-secondary);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.mode-thumb-card.active {
  border: 1.5px solid var(--seed-accent);
  background: color-mix(in srgb, var(--seed-primary) 13%, var(--bg-surface));
  color: var(--text-primary);
}

.mode-thumb-card.active .mode-thumb-label {
  font-weight: 700;
}

.mode-thumb-canvas {
  position: relative;
  width: 100%;
  height: 52px;
  border-radius: 10px;
  border: 1px solid;
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow: hidden;
}

.thumb-box-1,
.thumb-box-2 {
  display: block;
  width: 100%;
  border-radius: 5px;
  border: 1px solid;
}

.thumb-box-1 {
  height: 16px;
}

.thumb-box-2 {
  height: 14px;
}

.thumb-dot {
  position: absolute;
  right: 5px;
  bottom: 5px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.mode-thumb-label {
  font-size: 11.5px;
  text-align: center;
  line-height: 1.2;
}

.sub-label {
  margin: 14px 2px 8px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
}

.mono-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.mono-chip {
  padding: 10px 10px;
  border-radius: 14px;
  border: 1px solid var(--border-subtle);
  background: var(--bg-nested);
  color: var(--text-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  font-size: 12.5px;
}

.mono-chip.active {
  border-width: 1.5px;
  border-color: var(--seed-accent);
  font-weight: 700;
}

.mono-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 1px solid;
  flex-shrink: 0;
}

.theme-category-label {
  padding: 4px 6px 0;
  font-size: 13px;
  font-weight: 700;
  color: var(--seed-accent);
}

/* 6-Column Swatch Grid matching ColorPickerPreference.kt */
.swatch-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px 6px;
  padding-top: 4px;
}

.swatch-cell {
  position: relative;
  width: 44px;
  height: 44px;
  margin: 0 auto;
  border: none;
  background: transparent;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.swatch-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 2px solid;
}

.swatch-circle {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.16);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: width 0.14s ease, height 0.14s ease;
}

.swatch-cell.active .swatch-circle {
  width: 33px;
  height: 33px;
}

/* Inspector & Real App Screenshot */
.inspector-col {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.token-list {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.token-row {
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.token-swatch {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  border: 1px solid var(--border-muted);
  flex-shrink: 0;
}

.token-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.token-meta strong {
  font-size: 12.5px;
}

.token-meta span {
  font-size: 11.5px;
  color: var(--text-muted);
}

.real-shot-card {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.real-shot-head {
  font-size: 11.5px;
  color: var(--text-secondary);
  text-align: center;
}

.real-shot-img {
  display: block;
  width: 100%;
  max-width: 310px;
  margin: 0 auto;
  border-radius: 18px;
  border: 1px solid var(--border-muted);
}

/* Custom HEX Dialog */
.modal-scrim {
  position: fixed;
  inset: 0;
  z-index: 90;
  background: rgba(0, 0, 0, 0.68);
  backdrop-filter: blur(6px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.custom-dialog {
  width: 100%;
  max-width: 400px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  box-shadow: var(--shadow-card);
}

.dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 17px;
}

.dialog-preview {
  padding: 12px;
  display: flex;
  align-items: center;
  gap: 14px;
}

.dialog-dot {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: 1.5px solid var(--border-strong);
  flex-shrink: 0;
}

.hex-field {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 11px;
  font-weight: 700;
  color: var(--text-secondary);
}

.hex-field input {
  width: 100%;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid var(--border-subtle);
  background: var(--bg-surface);
  color: var(--text-primary);
  font-size: 14px;
}

.sliders-card {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.slider-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.slider-row input[type='range'] {
  flex: 1;
}

.ch-badge {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11.5px;
  font-weight: 800;
}

.ch-badge.r {
  background: rgba(239, 68, 68, 0.2);
  color: #ef4444;
}

.ch-badge.g {
  background: rgba(34, 197, 94, 0.2);
  color: #22c55e;
}

.ch-badge.b {
  background: rgba(59, 130, 246, 0.2);
  color: #3b82f6;
}

.ch-val {
  width: 30px;
  text-align: right;
  font-size: 12px;
  color: var(--text-secondary);
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 960px) {
  .studio-workspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .mode-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .mono-row {
    grid-template-columns: 1fr;
  }

  .swatch-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
</style>
