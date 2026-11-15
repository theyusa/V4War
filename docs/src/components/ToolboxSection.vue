<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  Activity,
  BarChart3,
  Globe2,
  Network,
  Radar,
  Search,
  Server,
  ShieldAlert,
  Signal,
  Terminal,
  Wifi,
} from 'lucide-vue-next'
import type { Locale } from '../data/content'
import { SITE_COPY, TOOLBOX_ITEMS } from '../data/content'

defineProps<{
  locale: Locale
}>()

const selectedId = ref<string>('stun')
const selectedTool = computed(
  () => TOOLBOX_ITEMS.find((t) => t.id === selectedId.value) ?? TOOLBOX_ITEMS[0],
)

const iconMap: Record<string, unknown> = {
  hotspot: Wifi,
  'dns-leak': ShieldAlert,
  stun: Radar,
  'ip-lookup': Globe2,
  'dns-lookup': Search,
  ping: Activity,
  connectivity: Server,
  interfaces: Network,
  traffic: BarChart3,
  cellular: Signal,
}
</script>

<template>
  <section id="toolbox" class="section">
    <div class="container">
      <div class="section-header">
        <div class="eyebrow">
          <Terminal :size="14" />
          <span>{{ SITE_COPY.toolbox.eyebrow[locale] }}</span>
        </div>
        <h2 class="section-title">{{ SITE_COPY.toolbox.title[locale] }}</h2>
        <p class="section-subtitle">{{ SITE_COPY.toolbox.subtitle[locale] }}</p>
      </div>

      <div class="toolbox-layout">
        <div class="toolbox-grid">
          <button
            v-for="tool in TOOLBOX_ITEMS"
            :key="tool.id"
            type="button"
            class="tool-tile m3-card"
            :class="{ active: selectedId === tool.id }"
            :data-testid="`tool-tile-${tool.id}`"
            @click="selectedId = tool.id"
          >
            <div class="tool-tile-top">
              <div class="squircle" style="width: 38px; height: 38px; border-radius: 12px">
                <component :is="iconMap[tool.id] || Network" :size="18" />
              </div>
              <span class="m3-pill mono" style="font-size: 10.5px; padding: 3px 8px">{{ tool.badge }}</span>
            </div>
            <div class="tool-tile-title">{{ tool.title[locale] }}</div>
          </button>
        </div>

        <aside class="tool-inspector m3-card" data-testid="tool-inspector">
          <div class="inspector-head">
            <span class="m3-pill mono">{{ selectedTool.badge }}</span>
            <h3 class="inspector-title">{{ selectedTool.title[locale] }}</h3>
            <p class="inspector-summary">{{ selectedTool.summary[locale] }}</p>
          </div>

          <div class="inspector-specs">
            <div v-for="spec in selectedTool.specs[locale]" :key="spec" class="spec-row">
              <span class="spec-dot"></span>
              <span>{{ spec }}</span>
            </div>
          </div>

          <div class="terminal-box m3-nested">
            <div class="terminal-bar mono">
              <span class="term-dots"><i></i><i></i><i></i></span>
              <span>{{ selectedTool.sampleOutput.label }}</span>
            </div>
            <div class="terminal-lines mono">
              <div v-for="line in selectedTool.sampleOutput.lines" :key="line" class="terminal-line">
                {{ line }}
              </div>
            </div>
          </div>
        </aside>
      </div>
    </div>
  </section>
</template>

<style scoped>
.toolbox-layout {
  display: grid;
  grid-template-columns: 1.25fr 0.75fr;
  gap: 20px;
  align-items: start;
}

.toolbox-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.tool-tile {
  padding: 16px;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tool-tile:hover {
  border-color: var(--border-strong);
}

.tool-tile.active {
  background: var(--bg-nested);
  border-color: var(--seed-primary);
}

.tool-tile-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.tool-tile-title {
  font-size: 14.5px;
  font-weight: 700;
  color: var(--text-primary);
}

.tool-inspector {
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  position: sticky;
  top: 86px;
}

.inspector-title {
  margin: 12px 0 8px;
  font-size: 21px;
  font-weight: 800;
}

.inspector-summary {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
  color: var(--text-secondary);
}

.inspector-specs {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.spec-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13.5px;
  color: var(--text-primary);
}

.spec-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--seed-accent);
  flex-shrink: 0;
}

.terminal-box {
  overflow: hidden;
}

.terminal-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 12px;
  font-size: 11px;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border-muted);
}

.term-dots {
  display: inline-flex;
  gap: 5px;
}

.term-dots i {
  display: block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--border-strong);
}

.terminal-lines {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-primary);
  overflow-wrap: anywhere;
}

@media (max-width: 960px) {
  .toolbox-layout {
    grid-template-columns: 1fr;
  }

  .tool-inspector {
    position: static;
  }
}

@media (max-width: 560px) {
  .toolbox-grid {
    grid-template-columns: 1fr;
  }
}
</style>
