<script setup lang="ts">
import { ArrowRight, BookOpen, Cpu, Download, Palette, Smartphone, Sparkles } from 'lucide-vue-next'
import type { Locale } from '../data/content'
import { SCREENSHOT_SHOWCASE, SITE_COPY } from '../data/content'
import { buildHref } from '../utils/router'
import type { MonochromeMode, SeedPreset, SurfaceMode } from '../utils/theme'
import HeroSection from './HeroSection.vue'
import FaqAndFooter from './FaqAndFooter.vue'

defineProps<{
  locale: Locale
  versionTag: string
  recommendedDownloadUrl: string
  surfaceMode: SurfaceMode
  monochromeMode: MonochromeMode
  activeSeed: SeedPreset
}>()

const emit = defineEmits<{
  'update:surfaceMode': [mode: SurfaceMode]
  'update:seed': [seed: SeedPreset]
  navigate: [routePath: string]
}>()

const baseUrl = import.meta.env.BASE_URL || '/'

function getPillarIcon(id: string) {
  if (id === 'docs') return BookOpen
  if (id === 'studio') return Palette
  if (id === 'explore') return Cpu
  return Download
}
</script>

<template>
  <div class="home-page">
    <HeroSection
      :locale="locale"
      :version-tag="versionTag"
      :recommended-download-url="recommendedDownloadUrl"
      :surface-mode="surfaceMode"
      :monochrome-mode="monochromeMode"
      :active-seed="activeSeed"
      @update:surface-mode="emit('update:surfaceMode', $event)"
      @update:seed="emit('update:seed', $event)"
      @navigate="emit('navigate', $event)"
    />

    <!-- 4 Modular Hub Cards -->
    <section class="section pillars-section">
      <div class="container">
        <div class="section-header">
          <div class="eyebrow">
            <Sparkles :size="14" />
            <span>{{ SITE_COPY.pillars.eyebrow[locale] }}</span>
          </div>
          <h2 class="section-title">{{ SITE_COPY.pillars.title[locale] }}</h2>
          <p class="section-subtitle">{{ SITE_COPY.pillars.subtitle[locale] }}</p>
        </div>

        <div class="pillars-grid">
          <a
            v-for="card in SITE_COPY.pillars.cards"
            :key="card.id"
            :href="buildHref(card.route)"
            class="m3-card pillar-card"
            :data-testid="`pillar-card-${card.id}`"
            @click.prevent="emit('navigate', card.route)"
          >
            <div class="pillar-top">
              <div class="squircle pillar-icon">
                <component :is="getPillarIcon(card.id)" :size="20" />
              </div>
              <span class="mono pillar-badge">{{ card.badge }}</span>
            </div>
            <h3>{{ card.title[locale] }}</h3>
            <p>{{ card.desc[locale] }}</p>
            <span class="pillar-cta">
              <span>{{ card.cta[locale] }}</span>
              <ArrowRight :size="15" />
            </span>
          </a>
        </div>
      </div>
    </section>

    <!-- Real App Screenshots Showcase -->
    <section class="section gallery-section">
      <div class="container">
        <div class="section-header">
          <div class="eyebrow">
            <Smartphone :size="14" />
            <span>{{ SITE_COPY.gallery.eyebrow[locale] }}</span>
          </div>
          <h2 class="section-title">{{ SITE_COPY.gallery.title[locale] }}</h2>
          <p class="section-subtitle">{{ SITE_COPY.gallery.subtitle[locale] }}</p>
        </div>

        <div class="gallery-grid">
          <a
            v-for="item in SCREENSHOT_SHOWCASE"
            :key="item.id"
            :href="buildHref(item.docRoute)"
            class="m3-card gallery-card"
            :data-testid="`showcase-${item.id}`"
            @click.prevent="emit('navigate', item.docRoute)"
          >
            <div class="gallery-shot-wrap">
              <img
                :src="`${baseUrl}${item.file}`"
                :alt="item.title[locale]"
                loading="lazy"
                width="460"
                height="1024"
                class="gallery-shot"
              />
            </div>
            <div class="gallery-body">
              <h3>{{ item.title[locale] }}</h3>
              <p>{{ item.caption[locale] }}</p>
              <span class="gallery-link">
                <span>{{ SITE_COPY.gallery.openGuide[locale] }}</span>
                <ArrowRight :size="14" />
              </span>
            </div>
          </a>
        </div>
      </div>
    </section>

    <!-- Focused FAQ on Home -->
    <FaqAndFooter :locale="locale" :show-faq="true" :show-footer="false" @navigate="emit('navigate', $event)" />
  </div>
</template>

<style scoped>
.pillars-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.pillar-card {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: transform 160ms ease, border-color 160ms ease;
}

.pillar-card:hover {
  transform: translateY(-2px);
  border-color: var(--seed-accent);
}

.pillar-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pillar-icon {
  background: color-mix(in srgb, var(--seed-primary) 16%, var(--bg-nested));
  color: var(--seed-accent);
  border: 1px solid var(--border-subtle);
}

.pillar-badge {
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--seed-accent);
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--bg-nested);
  border: 1px solid var(--border-muted);
}

.pillar-card h3 {
  font-size: 1.15rem;
  font-weight: 800;
  color: var(--text-primary);
}

.pillar-card p {
  font-size: 0.92rem;
  color: var(--text-secondary);
  line-height: 1.6;
  flex: 1;
}

.pillar-cta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.86rem;
  font-weight: 700;
  color: var(--seed-accent);
  margin-top: 4px;
}

.gallery-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.gallery-card {
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: transform 160ms ease, border-color 160ms ease;
}

.gallery-card:hover {
  transform: translateY(-3px);
  border-color: var(--seed-accent);
}

.gallery-shot-wrap {
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid var(--border-muted);
  background: #000000;
  aspect-ratio: 9 / 19.5;
}

.gallery-shot {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.gallery-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}

.gallery-body h3 {
  font-size: 0.96rem;
  font-weight: 800;
  color: var(--text-primary);
}

.gallery-body p {
  font-size: 0.82rem;
  line-height: 1.5;
  color: var(--text-secondary);
  flex: 1;
}

.gallery-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--seed-accent);
  margin-top: 4px;
}

@media (max-width: 1024px) {
  .gallery-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .pillars-grid,
  .gallery-grid {
    grid-template-columns: 1fr;
  }
}
</style>
