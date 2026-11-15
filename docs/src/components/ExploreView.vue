<script setup lang="ts">
import { ref } from 'vue'
import { Activity, BookOpen, Cpu, Share2, Wrench } from 'lucide-vue-next'
import type { Locale } from '../data/content'
import { buildHref } from '../utils/router'
import HotspotSuiteSection from './HotspotSuiteSection.vue'
import ToolboxSection from './ToolboxSection.vue'
import ProtocolsSection from './ProtocolsSection.vue'

defineProps<{
  locale: Locale
}>()

const emit = defineEmits<{
  navigate: [routePath: string]
}>()

type LabTab = 'all' | 'hotspot' | 'toolbox' | 'protocols'
const activeTab = ref<LabTab>('all')
</script>

<template>
  <div class="explore-page bg-grid-pattern">
    <section class="explore-banner">
      <div class="container explore-banner-inner">
        <div>
          <div class="eyebrow">
            <Cpu :size="14" />
            <span>{{ locale === 'tr' ? 'İnteraktif Özellik Laboratuvarı' : 'Interactive Feature Lab' }}</span>
          </div>
          <h1 class="explore-title">
            {{
              locale === 'tr'
                ? 'Hotspot Motorları, Ağ Araçları & Protokol Matrisi'
                : 'Hotspot Backends, Network Toolbox & Protocol Matrix'
            }}
          </h1>
          <p class="explore-sub">
            {{
              locale === 'tr'
                ? 'Tüm teknik simülatörleri tek yerde deneyin ve ilgili adım adım dokümanlara tek tıkla geçin.'
                : 'Test all technical simulators in one dedicated workspace and jump directly to matching documentation guides.'
            }}
          </p>
        </div>

        <div class="explore-tabs-row">
          <button
            type="button"
            class="lab-tab"
            :class="{ active: activeTab === 'all' }"
            @click="activeTab = 'all'"
          >
            {{ locale === 'tr' ? 'Tümü' : 'All Modules' }}
          </button>
          <button
            type="button"
            class="lab-tab"
            :class="{ active: activeTab === 'hotspot' }"
            @click="activeTab = 'hotspot'"
          >
            <Share2 :size="14" />
            <span>Hotspot</span>
          </button>
          <button
            type="button"
            class="lab-tab"
            :class="{ active: activeTab === 'toolbox' }"
            @click="activeTab = 'toolbox'"
          >
            <Wrench :size="14" />
            <span>{{ locale === 'tr' ? 'Ağ Araçları' : 'Toolbox' }}</span>
          </button>
          <button
            type="button"
            class="lab-tab"
            :class="{ active: activeTab === 'protocols' }"
            @click="activeTab = 'protocols'"
          >
            <Activity :size="14" />
            <span>{{ locale === 'tr' ? 'Protokoller' : 'Protocols' }}</span>
          </button>

          <a
            :href="buildHref('/docs/hotspot-share')"
            class="m3-pill doc-jump-pill"
            @click.prevent="emit('navigate', '/docs/hotspot-share')"
          >
            <BookOpen :size="14" />
            <span>{{ locale === 'tr' ? 'Hotspot Rehberi' : 'Hotspot Docs' }}</span>
          </a>
        </div>
      </div>
    </section>

    <HotspotSuiteSection v-if="activeTab === 'all' || activeTab === 'hotspot'" :locale="locale" />
    <ToolboxSection v-if="activeTab === 'all' || activeTab === 'toolbox'" :locale="locale" />
    <ProtocolsSection v-if="activeTab === 'all' || activeTab === 'protocols'" :locale="locale" />
  </div>
</template>

<style scoped>
.explore-page {
  padding-top: 24px;
}

.explore-page :deep(.section) {
  padding: 44px 0;
}

.explore-banner {
  padding: 16px 0 8px;
}

.explore-banner-inner {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.explore-title {
  font-size: clamp(1.75rem, 3.4vw, 2.5rem);
  font-weight: 800;
  letter-spacing: -0.025em;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.explore-sub {
  font-size: 0.98rem;
  color: var(--text-secondary);
  max-width: 720px;
}

.explore-tabs-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.lab-tab {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 9px 15px;
  border-radius: 999px;
  border: 1px solid var(--border-muted);
  background: var(--bg-surface);
  color: var(--text-secondary);
  font-size: 0.84rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 140ms ease;
}

.lab-tab:hover {
  color: var(--text-primary);
  border-color: var(--seed-accent);
}

.lab-tab.active {
  background: var(--seed-primary);
  color: var(--seed-on-primary);
  border-color: var(--seed-primary);
}

.doc-jump-pill {
  margin-left: auto;
  color: var(--seed-accent);
  border-color: var(--border-subtle);
}

@media (max-width: 680px) {
  .doc-jump-pill {
    margin-left: 0;
  }
}
</style>
