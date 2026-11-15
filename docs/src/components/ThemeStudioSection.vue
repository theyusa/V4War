<script setup lang="ts">
import { Layers, Palette, Sparkles } from 'lucide-vue-next'
import type { Locale } from '../data/content'
import { SITE_COPY } from '../data/content'
import type { ResolvedThemeTokens, SeedPreset, SurfaceMode } from '../utils/theme'
import { SEED_PRESETS, SURFACE_MODES } from '../utils/theme'

defineProps<{
  locale: Locale
  surfaceMode: SurfaceMode
  activeSeed: SeedPreset
  tokens: ResolvedThemeTokens
}>()

const emit = defineEmits<{
  'update:surfaceMode': [mode: SurfaceMode]
  'update:seed': [seed: SeedPreset]
}>()
</script>

<template>
  <section id="studio" class="section">
    <div class="container">
      <div class="section-header">
        <div class="eyebrow">
          <Palette :size="14" />
          <span>{{ SITE_COPY.studio.eyebrow[locale] }}</span>
        </div>
        <h2 class="section-title">{{ SITE_COPY.studio.title[locale] }}</h2>
        <p class="section-subtitle">{{ SITE_COPY.studio.subtitle[locale] }}</p>
      </div>

      <div class="studio-grid">
        <!-- Surface Mode Picker -->
        <div class="m3-card studio-panel">
          <div class="panel-heading">
            <Layers :size="16" class="panel-icon" />
            <span>{{ SITE_COPY.studio.surfaceLabel[locale] }}</span>
          </div>

          <div class="surface-cards">
            <button
              v-for="mode in SURFACE_MODES"
              :key="mode.id"
              type="button"
              class="surface-option"
              :class="{ active: surfaceMode === mode.id }"
              :data-testid="`surface-btn-${mode.id}`"
              @click="emit('update:surfaceMode', mode.id)"
            >
              <div class="surface-swatch" :data-mode="mode.id"></div>
              <div class="surface-meta">
                <strong>{{ locale === 'tr' ? mode.labelTr : mode.labelEn }}</strong>
                <span>{{ locale === 'tr' ? mode.hintTr : mode.hintEn }}</span>
              </div>
            </button>
          </div>
        </div>

        <!-- Seed Color Picker + Live 3-Tone Inspector -->
        <div class="m3-card studio-panel">
          <div class="panel-heading">
            <Sparkles :size="16" class="panel-icon" />
            <span>{{ SITE_COPY.studio.seedLabel[locale] }}</span>
          </div>

          <div class="seed-grid">
            <button
              v-for="seed in SEED_PRESETS"
              :key="seed.id"
              type="button"
              class="seed-card"
              :class="{ active: activeSeed.id === seed.id }"
              :data-testid="`seed-btn-${seed.id}`"
              @click="emit('update:seed', seed)"
            >
              <span class="seed-circle" :style="{ backgroundColor: seed.primary }"></span>
              <div class="seed-text">
                <strong>{{ locale === 'tr' ? seed.nameTr : seed.nameEn }}</strong>
                <span class="mono">
                  {{ seed.primary }}
                  <template v-if="seed.isAppDefault">
                    · {{ locale === 'tr' ? 'Varsayılan' : 'Default' }}
                  </template>
                </span>
              </div>
            </button>
          </div>

          <div class="tones-inspector m3-nested">
            <div class="tones-label">{{ SITE_COPY.studio.tonesLabel[locale] }}</div>
            <div class="tones-row">
              <div class="tone-chip">
                <span class="tone-preview" :style="{ backgroundColor: tokens.tone1 }"></span>
                <div>
                  <strong>{{ SITE_COPY.studio.tone1Label[locale] }}</strong>
                  <span class="mono">{{ tokens.tone1 }}</span>
                </div>
              </div>
              <div class="tone-chip">
                <span class="tone-preview" :style="{ backgroundColor: tokens.tone2 }"></span>
                <div>
                  <strong>{{ SITE_COPY.studio.tone2Label[locale] }}</strong>
                  <span class="mono">{{ tokens.tone2 }}</span>
                </div>
              </div>
              <div class="tone-chip">
                <span class="tone-preview" :style="{ backgroundColor: tokens.tone3 }"></span>
                <div>
                  <strong>{{ SITE_COPY.studio.tone3Label[locale] }}</strong>
                  <span class="mono">{{ tokens.tone3 }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.studio-grid {
  display: grid;
  grid-template-columns: 0.95fr 1.05fr;
  gap: 20px;
}

.studio-panel {
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--text-secondary);
}

.panel-icon {
  color: var(--seed-accent);
}

.surface-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.surface-option {
  padding: 12px;
  border-radius: 16px;
  border: 1px solid var(--border-muted);
  background: var(--bg-surface-elevated);
  display: flex;
  align-items: center;
  gap: 12px;
  text-align: left;
  color: var(--text-primary);
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.surface-option:hover {
  border-color: var(--border-strong);
}

.surface-option.active {
  background: var(--bg-nested);
  border-color: var(--seed-primary);
}

.surface-swatch {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  flex-shrink: 0;
  border: 1px solid var(--border-subtle);
}

.surface-swatch[data-mode='pure-amoled'] {
  background: #000000;
}

.surface-swatch[data-mode='amoled'] {
  background: linear-gradient(135deg, #000000 35%, var(--seed-tone-2) 100%);
}

.surface-swatch[data-mode='dark'] {
  background: linear-gradient(135deg, var(--seed-tone-1-dark) 35%, var(--seed-tone-2-dark) 100%);
}

.surface-swatch[data-mode='light'] {
  background: linear-gradient(135deg, #f4f5f7 35%, #ffffff 100%);
}

.surface-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.surface-meta strong {
  font-size: 13.5px;
}

.surface-meta span {
  font-size: 11.5px;
  color: var(--text-secondary);
}

.seed-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.seed-card {
  padding: 11px 12px;
  border-radius: 15px;
  border: 1px solid var(--border-muted);
  background: var(--bg-surface-elevated);
  display: flex;
  align-items: center;
  gap: 11px;
  text-align: left;
  color: var(--text-primary);
}

.seed-card:hover {
  border-color: var(--border-strong);
}

.seed-card.active {
  background: var(--bg-nested);
  border-color: var(--seed-primary);
}

.seed-circle {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  flex-shrink: 0;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.16);
}

.seed-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.seed-text strong {
  font-size: 13px;
}

.seed-text span {
  font-size: 11px;
  color: var(--text-muted);
}

.tones-inspector {
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tones-label {
  font-size: 11.5px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--text-secondary);
}

.tones-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.tone-chip {
  display: flex;
  align-items: center;
  gap: 9px;
}

.tone-preview {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  border: 1px solid var(--border-muted);
  flex-shrink: 0;
}

.tone-chip strong {
  display: block;
  font-size: 11.5px;
}

.tone-chip span {
  font-size: 11px;
  color: var(--text-muted);
}

@media (max-width: 960px) {
  .studio-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .surface-cards,
  .seed-grid,
  .tones-row {
    grid-template-columns: 1fr;
  }
}
</style>
