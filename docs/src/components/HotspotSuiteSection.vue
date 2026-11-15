<script setup lang="ts">
import { computed, ref } from 'vue'
import { CheckCircle2, Cpu, Laptop, Radio, Share2, Shield, Wifi } from 'lucide-vue-next'
import type { Locale } from '../data/content'
import { HOTSPOT_BACKENDS, SITE_COPY } from '../data/content'

defineProps<{
  locale: Locale
}>()

const activeId = ref<'auto' | 'shizuku' | 'root' | 'lan'>('shizuku')
const activeBackend = computed(
  () => HOTSPOT_BACKENDS.find((b) => b.id === activeId.value) ?? HOTSPOT_BACKENDS[0],
)
</script>

<template>
  <section id="hotspot" class="section">
    <div class="container">
      <div class="section-header">
        <div class="eyebrow">
          <Wifi :size="14" />
          <span>{{ SITE_COPY.hotspot.eyebrow[locale] }}</span>
        </div>
        <h2 class="section-title">{{ SITE_COPY.hotspot.title[locale] }}</h2>
        <p class="section-subtitle">{{ SITE_COPY.hotspot.subtitle[locale] }}</p>
      </div>

      <div class="hotspot-layout">
        <!-- Backend Switcher + Technical Detail Card -->
        <div class="m3-card hotspot-main">
          <div class="backend-tabs" role="tablist" aria-label="Hotspot sharing backends">
            <button
              v-for="backend in HOTSPOT_BACKENDS"
              :key="backend.id"
              type="button"
              role="tab"
              :aria-selected="activeId === backend.id"
              :data-testid="`hotspot-tab-${backend.id}`"
              class="backend-tab"
              :class="{ active: activeId === backend.id }"
              @click="activeId = backend.id"
            >
              <span class="backend-tab-name">{{ backend.name[locale] }}</span>
              <span class="backend-tab-priv">{{ backend.privilege[locale] }}</span>
            </button>
          </div>

          <div class="backend-detail" data-testid="hotspot-detail">
            <div class="backend-header">
              <div>
                <span class="m3-pill mono">{{ activeBackend.architecture }}</span>
                <h3 class="backend-title">{{ activeBackend.name[locale] }}</h3>
              </div>
              <div class="squircle">
                <Shield v-if="activeBackend.id === 'root'" :size="20" />
                <Cpu v-else-if="activeBackend.id === 'shizuku'" :size="20" />
                <Share2 v-else-if="activeBackend.id === 'lan'" :size="20" />
                <Radio v-else :size="20" />
              </div>
            </div>

            <p class="backend-summary">{{ activeBackend.summary[locale] }}</p>

            <ul class="highlight-list">
              <li v-for="item in activeBackend.highlights[locale]" :key="item">
                <CheckCircle2 :size="16" class="check-icon" />
                <span>{{ item }}</span>
              </li>
            </ul>
          </div>
        </div>

        <!-- Multi-Channel & Desktop Pairing Column -->
        <div class="channels-col">
          <div
            v-for="(channel, idx) in SITE_COPY.hotspot.channels"
            :key="channel.title.en"
            class="m3-card m3-card-interactive channel-card"
          >
            <div class="squircle">
              <Radio v-if="idx === 0" :size="20" />
              <Wifi v-else-if="idx === 1" :size="20" />
              <Laptop v-else :size="20" />
            </div>
            <div>
              <h3 class="channel-title">{{ channel.title[locale] }}</h3>
              <p class="channel-desc">{{ channel.desc[locale] }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.hotspot-layout {
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  gap: 20px;
  align-items: stretch;
}

.hotspot-main {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.backend-tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.backend-tab {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  padding: 12px;
  border-radius: 16px;
  border: 1px solid var(--border-muted);
  background: var(--bg-surface-elevated);
  color: var(--text-secondary);
  text-align: left;
  transition: border-color 0.2s ease, background-color 0.2s ease, transform 0.16s ease;
}

.backend-tab:hover {
  border-color: var(--border-strong);
}

.backend-tab.active {
  background: var(--bg-nested);
  border-color: var(--seed-primary);
  color: var(--text-primary);
}

.backend-tab-name {
  font-size: 13.5px;
  font-weight: 700;
}

.backend-tab-priv {
  font-size: 11px;
  color: var(--text-muted);
}

.backend-tab.active .backend-tab-priv {
  color: var(--seed-accent);
}

.backend-detail {
  padding: 22px;
  border-radius: 18px;
  background: var(--bg-nested);
  border: 1px solid var(--border-subtle);
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.backend-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.backend-title {
  margin: 12px 0 0;
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.backend-summary {
  margin: 0;
  font-size: 15px;
  line-height: 1.65;
  color: var(--text-secondary);
}

.highlight-list {
  list-style: none;
  margin: 4px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.highlight-list li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 14px;
  line-height: 1.5;
  color: var(--text-primary);
}

.check-icon {
  color: var(--seed-accent);
  flex-shrink: 0;
  margin-top: 2px;
}

.channels-col {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.channel-card {
  padding: 20px;
  display: flex;
  align-items: flex-start;
  gap: 16px;
  flex: 1;
}

.channel-title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 700;
}

.channel-desc {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--text-secondary);
}

@media (max-width: 960px) {
  .hotspot-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .backend-tabs {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
