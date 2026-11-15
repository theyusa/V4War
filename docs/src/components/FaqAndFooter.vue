<script setup lang="ts">
import { ref } from 'vue'
import { ChevronDown, Github, HelpCircle } from 'lucide-vue-next'
import type { Locale } from '../data/content'
import { FAQS, SITE_COPY } from '../data/content'

defineProps<{
  locale: Locale
}>()

const openIndex = ref<number | null>(0)
const logoUrl = `${import.meta.env.BASE_URL}logo.png`

function toggle(idx: number) {
  openIndex.value = openIndex.value === idx ? null : idx
}
</script>

<template>
  <section id="faq" class="section">
    <div class="container">
      <div class="section-header">
        <div class="eyebrow">
          <HelpCircle :size="14" />
          <span>{{ SITE_COPY.faq.eyebrow[locale] }}</span>
        </div>
        <h2 class="section-title">{{ SITE_COPY.faq.title[locale] }}</h2>
      </div>

      <div class="faq-list">
        <div v-for="(item, idx) in FAQS" :key="item.q.en" class="m3-card faq-item">
          <button
            type="button"
            class="faq-trigger"
            :aria-expanded="openIndex === idx"
            :data-testid="`faq-trigger-${idx}`"
            @click="toggle(idx)"
          >
            <span>{{ item.q[locale] }}</span>
            <ChevronDown :size="18" class="faq-chevron" :class="{ rotated: openIndex === idx }" />
          </button>
          <div v-if="openIndex === idx" class="faq-answer" :data-testid="`faq-answer-${idx}`">
            {{ item.a[locale] }}
          </div>
        </div>
      </div>
    </div>
  </section>

  <footer class="footer">
    <div class="container footer-inner">
      <div class="footer-brand">
        <img :src="logoUrl" alt="V4War Logo" width="28" height="28" class="footer-logo" />
        <div>
          <strong>V4War for Android</strong>
          <p>
            {{
              locale === 'tr'
                ? 'GPL-3.0 Lisanslı Açık Kaynak Proje · Sing-box & Xray Çekirdekli'
                : 'Open-Source under GNU GPL-3.0 · Empowered by Sing-box & Xray'
            }}
          </p>
        </div>
      </div>

      <div class="footer-links">
        <a href="https://github.com/theyusa/V4War" target="_blank" rel="noopener noreferrer" class="m3-pill">
          <Github :size="14" />
          <span>theyusa/V4War</span>
        </a>
        <a href="https://github.com/theyusa/Throne" target="_blank" rel="noopener noreferrer" class="m3-pill">
          <span>theyusa/Throne (Desktop)</span>
        </a>
        <a
          href="https://github.com/throneproj/ThroneForAndroid"
          target="_blank"
          rel="noopener noreferrer"
          class="m3-pill"
        >
          <span>Upstream</span>
        </a>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.faq-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.faq-item {
  overflow: hidden;
}

.faq-trigger {
  width: 100%;
  padding: 18px 20px;
  border: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 15.5px;
  font-weight: 700;
  text-align: left;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.faq-chevron {
  color: var(--seed-accent);
  transition: transform 0.2s ease;
  flex-shrink: 0;
}

.faq-chevron.rotated {
  transform: rotate(180deg);
}

.faq-answer {
  padding: 0 20px 18px;
  font-size: 14px;
  line-height: 1.65;
  color: var(--text-secondary);
}

.footer {
  border-top: 1px solid var(--border-muted);
  padding: 34px 0 46px;
}

.footer-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}

.footer-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.footer-logo {
  border-radius: 8px;
}

.footer-brand p {
  margin: 3px 0 0;
  font-size: 12.5px;
  color: var(--text-muted);
}

.footer-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
