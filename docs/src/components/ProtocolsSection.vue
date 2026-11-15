<script setup lang="ts">
import { computed, ref } from 'vue'
import { Blocks, Search } from 'lucide-vue-next'
import type { Locale, ProtocolItem } from '../data/content'
import { PROTOCOLS, SITE_COPY } from '../data/content'

defineProps<{
  locale: Locale
}>()

type CategoryFilter = 'all' | ProtocolItem['category']

const activeCategory = ref<CategoryFilter>('all')
const query = ref('')

const categories: { id: CategoryFilter; labelEn: string; labelTr: string }[] = [
  { id: 'all', labelEn: 'All (28)', labelTr: 'Tümü (28)' },
  { id: 'proxy', labelEn: 'Proxy & TLS', labelTr: 'Proxy ve TLS' },
  { id: 'quic', labelEn: 'QUIC / UDP', labelTr: 'QUIC / UDP' },
  { id: 'tunnel', labelEn: 'VPN Tunnels', labelTr: 'VPN Tünelleri' },
  { id: 'dns', labelEn: 'DNS Tunnels', labelTr: 'DNS Tünelleri' },
  { id: 'smart', labelEn: 'Smart Outbounds', labelTr: 'Akıllı Çıkışlar' },
]

const filteredProtocols = computed(() => {
  const q = query.value.trim().toLowerCase()
  return PROTOCOLS.filter((item) => {
    const matchesCat = activeCategory.value === 'all' || item.category === activeCategory.value
    const matchesQuery =
      !q ||
      item.name.toLowerCase().includes(q) ||
      (item.badge && item.badge.toLowerCase().includes(q))
    return matchesCat && matchesQuery
  })
})
</script>

<template>
  <section id="protocols" class="section">
    <div class="container">
      <div class="section-header">
        <div class="eyebrow">
          <Blocks :size="14" />
          <span>{{ SITE_COPY.protocols.eyebrow[locale] }}</span>
        </div>
        <h2 class="section-title">{{ SITE_COPY.protocols.title[locale] }}</h2>
        <p class="section-subtitle">{{ SITE_COPY.protocols.subtitle[locale] }}</p>
      </div>

      <div class="protocol-controls">
        <div class="cat-pills">
          <button
            v-for="cat in categories"
            :key="cat.id"
            type="button"
            class="cat-pill"
            :class="{ active: activeCategory === cat.id }"
            :data-testid="`protocol-cat-${cat.id}`"
            @click="activeCategory = cat.id"
          >
            {{ locale === 'tr' ? cat.labelTr : cat.labelEn }}
          </button>
        </div>

        <label class="search-box m3-card">
          <Search :size="16" class="search-icon" />
          <input
            v-model="query"
            type="search"
            class="search-input"
            data-testid="protocol-search"
            :placeholder="SITE_COPY.protocols.searchPlaceholder[locale]"
          />
        </label>
      </div>

      <div class="protocol-grid" data-testid="protocol-grid">
        <div
          v-for="item in filteredProtocols"
          :key="item.name"
          class="protocol-card m3-card m3-card-interactive"
        >
          <span class="protocol-name">{{ item.name }}</span>
          <span v-if="item.badge" class="protocol-badge mono">{{ item.badge }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.protocol-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 22px;
}

.cat-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cat-pill {
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid var(--border-muted);
  background: var(--bg-surface);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  transition: border-color 0.18s ease, background-color 0.18s ease, color 0.18s ease;
}

.cat-pill:hover {
  border-color: var(--border-strong);
  color: var(--text-primary);
}

.cat-pill.active {
  background: var(--seed-primary);
  border-color: var(--seed-primary);
  color: var(--seed-on-primary);
}

.search-box {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  height: 42px;
  border-radius: 999px;
  min-width: 280px;
  flex: 0 1 360px;
}

.search-icon {
  color: var(--text-muted);
  flex-shrink: 0;
}

.search-input {
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-primary);
  width: 100%;
  font-size: 13.5px;
}

.search-input::placeholder {
  color: var(--text-muted);
}

.protocol-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.protocol-card {
  padding: 15px 16px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.protocol-name {
  font-size: 14.5px;
  font-weight: 700;
}

.protocol-badge {
  font-size: 10.5px;
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--bg-nested);
  border: 1px solid var(--border-subtle);
  color: var(--seed-accent);
  white-space: nowrap;
}

@media (max-width: 960px) {
  .protocol-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 520px) {
  .protocol-grid {
    grid-template-columns: 1fr;
  }

  .search-box {
    width: 100%;
    flex: 1 1 100%;
  }
}
</style>
