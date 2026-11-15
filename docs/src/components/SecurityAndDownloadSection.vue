<script setup lang="ts">
import { ref } from 'vue'
import { Check, Copy, Download, ExternalLink, FileCheck2, Lock, RefreshCw, Route, Shield } from 'lucide-vue-next'
import type { Locale } from '../data/content'
import { SITE_COPY } from '../data/content'
import type { ReleaseInfo } from '../utils/releases'

defineProps<{
  locale: Locale
  release: ReleaseInfo
}>()

const copied = ref(false)
const buildCommands = `./gradlew :app:testOssDebugUnitTest\n./gradlew :app:assembleOssDebug`

async function copyCommands() {
  try {
    await navigator.clipboard.writeText(buildCommands)
    copied.value = true
    setTimeout(() => {
      copied.value = false
    }, 1800)
  } catch {
    copied.value = false
  }
}

function abiSubtitle(abi: string, locale: Locale): string {
  if (abi === 'arm64-v8a') {
    return locale === 'tr' ? 'Modern telefon ve tabletler (2015+)' : 'Most modern Android phones & tablets'
  }
  if (abi === 'armeabi-v7a') {
    return locale === 'tr' ? 'Eski 32-bit ARM cihazlar' : 'Older 32-bit ARM devices'
  }
  if (abi === 'x86_64') {
    return locale === 'tr' ? 'Emülatörler ve x86_64 Android sistemler' : 'Emulators & x86_64 Android systems'
  }
  return locale === 'tr' ? 'Evrensel paket' : 'Universal package'
}
</script>

<template>
  <!-- Download & Build Section (primary focus on /download) -->
  <section id="download" class="section">
    <div class="container">
      <div class="m3-card download-shell">
        <div class="download-head">
          <div>
            <div class="eyebrow">
              <Download :size="14" />
              <span>{{ SITE_COPY.download.eyebrow[locale] }} · {{ release.tagName }}</span>
            </div>
            <h1 class="section-title">{{ SITE_COPY.download.title[locale] }}</h1>
            <p class="section-subtitle">{{ SITE_COPY.download.subtitle[locale] }}</p>
          </div>

          <div class="release-meta-pills">
            <span class="m3-pill mono">{{ release.tagName }}</span>
            <span class="m3-pill mono">{{ release.publishedAt }}</span>
            <a
              v-if="release.sha256SumsUrl"
              :href="release.sha256SumsUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="m3-pill mono"
            >
              SHA256SUMS ↗
            </a>
          </div>
        </div>

        <div class="apk-grid" data-testid="apk-grid">
          <a
            v-for="apk in release.apks"
            :key="apk.fileName"
            :href="apk.downloadUrl"
            class="apk-card m3-nested"
            :class="{ recommended: apk.recommended }"
          >
            <div class="apk-top">
              <span class="apk-abi mono">{{ apk.abi }}</span>
              <span v-if="apk.recommended" class="apk-badge">
                {{ locale === 'tr' ? 'Önerilen' : 'Recommended' }}
              </span>
            </div>
            <div class="apk-sub">{{ abiSubtitle(apk.abi, locale) }}</div>
            <div class="apk-bottom mono">
              <span>{{ apk.sizeLabel }}</span>
              <span class="apk-dl">
                <Download :size="14" />
                APK
              </span>
            </div>
          </a>
        </div>

        <div class="source-build m3-nested">
          <div class="source-head">
            <strong>{{ SITE_COPY.download.sourceTitle[locale] }}</strong>
            <div class="source-actions">
              <button type="button" class="copy-btn mono" @click="copyCommands">
                <Check v-if="copied" :size="14" />
                <Copy v-else :size="14" />
                <span>{{ copied ? (locale === 'tr' ? 'Kopyalandı' : 'Copied') : (locale === 'tr' ? 'Kopyala' : 'Copy') }}</span>
              </button>
              <a
                :href="release.htmlUrl"
                target="_blank"
                rel="noopener noreferrer"
                class="copy-btn mono"
              >
                <ExternalLink :size="14" />
                <span>Releases</span>
              </a>
            </div>
          </div>
          <pre class="mono code-block"><code>{{ buildCommands }}</code></pre>
        </div>
      </div>
    </div>
  </section>

  <!-- Security & Core Verification Section -->
  <section class="section security-section">
    <div class="container">
      <div class="section-header">
        <div class="eyebrow">
          <Lock :size="14" />
          <span>{{ SITE_COPY.security.eyebrow[locale] }}</span>
        </div>
        <h2 class="section-title">{{ SITE_COPY.security.title[locale] }}</h2>
      </div>

      <div class="security-grid">
        <div
          v-for="(card, idx) in SITE_COPY.security.cards"
          :key="card.title.en"
          class="m3-card m3-card-interactive security-card"
        >
          <div class="squircle">
            <Shield v-if="idx === 0" :size="20" />
            <RefreshCw v-else-if="idx === 1" :size="20" />
            <Route v-else-if="idx === 2" :size="20" />
            <FileCheck2 v-else :size="20" />
          </div>
          <h3 class="sec-title">{{ card.title[locale] }}</h3>
          <p class="sec-desc">{{ card.desc[locale] }}</p>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.security-section {
  padding-top: 24px;
}

.security-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.security-card {
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sec-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
}

.sec-desc {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
  color: var(--text-secondary);
}

.download-shell {
  padding: 32px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  box-shadow: var(--shadow-glow);
}

.download-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}

.release-meta-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.apk-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.apk-card {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border: 1px solid var(--border-muted);
  transition: border-color 0.2s ease, transform 0.18s ease;
}

.apk-card:hover {
  border-color: var(--border-strong);
  transform: translateY(-2px);
}

.apk-card.recommended {
  border-color: var(--seed-primary);
}

.apk-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.apk-abi {
  font-size: 15px;
  font-weight: 700;
}

.apk-badge {
  font-size: 11px;
  font-weight: 700;
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--seed-primary);
  color: var(--seed-on-primary);
}

.apk-sub {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.45;
  flex: 1;
}

.apk-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12.5px;
  color: var(--text-muted);
  padding-top: 8px;
  border-top: 1px solid var(--border-muted);
}

.apk-dl {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--seed-accent);
  font-weight: 700;
}

.source-build {
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.source-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 13.5px;
}

.source-actions {
  display: flex;
  gap: 8px;
}

.copy-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 11px;
  border-radius: 999px;
  background: var(--bg-surface);
  border: 1px solid var(--border-muted);
  color: var(--text-primary);
  font-size: 12px;
}

.copy-btn:hover {
  border-color: var(--border-strong);
}

.code-block {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--text-secondary);
  overflow-x: auto;
}

@media (max-width: 900px) {
  .security-grid,
  .apk-grid {
    grid-template-columns: 1fr;
  }

  .download-shell {
    padding: 20px;
  }
}
</style>
