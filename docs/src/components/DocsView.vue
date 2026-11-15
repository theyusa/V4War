<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import {
  ArrowLeft,
  ArrowRight,
  BookOpen,
  ChevronRight,
  ExternalLink,
  ListTree,
  Menu,
  Search,
  X,
} from 'lucide-vue-next'
import type { Locale } from '../data/content'
import {
  DOC_GROUPS,
  getDocBySlug,
  getDocPages,
  getPrevNextDocs,
  searchDocs,
} from '../utils/docs'
import { buildHref } from '../utils/router'

const props = defineProps<{
  locale: Locale
  docSlug: string
  hash?: string
}>()

const emit = defineEmits<{
  navigate: [routePath: string]
}>()

const searchQuery = ref('')
const mobileSidebarOpen = ref(false)
const activeHeadingId = ref('')

const allPages = computed(() => getDocPages(props.locale))
const activeDoc = computed(() => getDocBySlug(props.locale, props.docSlug))
const prevNext = computed(() => getPrevNextDocs(props.locale, activeDoc.value.slug))
const filteredPages = computed(() => searchDocs(props.locale, searchQuery.value))

const groupedSidebar = computed(() => {
  const allowedSlugs = new Set(filteredPages.value.map((p) => p.slug))
  return DOC_GROUPS.map((group) => {
    const items = allPages.value.filter(
      (p) => p.group === group.id && allowedSlugs.has(p.slug),
    )
    return {
      id: group.id,
      label: group.label[props.locale],
      items,
    }
  }).filter((g) => g.items.length > 0)
})

const activeGroupLabel = computed(() => {
  const found = DOC_GROUPS.find((g) => g.id === activeDoc.value.group)
  return found ? found.label[props.locale] : 'Docs'
})

function openDoc(slug: string) {
  mobileSidebarOpen.value = false
  emit('navigate', `/docs/${slug}`)
}

function jumpToHeading(id: string) {
  activeHeadingId.value = id
  const el = document.getElementById(id)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

function handleArticleClick(e: MouseEvent) {
  const target = e.target as HTMLElement | null
  const anchor = target?.closest('a') as HTMLAnchorElement | null
  if (!anchor) return

  const internalRoute = anchor.getAttribute('data-internal-route')
  if (internalRoute) {
    e.preventDefault()
    emit('navigate', internalRoute)
    return
  }

  const href = anchor.getAttribute('href') || ''
  if (href.startsWith('#')) {
    e.preventDefault()
    jumpToHeading(href.slice(1))
  }
}

watch(
  () => [activeDoc.value.slug, props.hash],
  async () => {
    mobileSidebarOpen.value = false
    await nextTick()
    if (props.hash && props.hash.startsWith('#')) {
      const id = props.hash.slice(1)
      const target = document.getElementById(id)
      if (target) {
        activeHeadingId.value = id
        target.scrollIntoView({ behavior: 'smooth', block: 'start' })
        return
      }
    }
    activeHeadingId.value = activeDoc.value.headings[0]?.id || ''
    if (typeof window !== 'undefined' && typeof window.scrollTo === 'function') {
      window.scrollTo({ top: 0 })
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="docs-shell bg-grid-pattern">
    <!-- Mobile sticky docs bar -->
    <div class="docs-mobile-bar">
      <button
        type="button"
        class="docs-mobile-trigger"
        data-testid="docs-mobile-sidebar-btn"
        @click="mobileSidebarOpen = !mobileSidebarOpen"
      >
        <Menu v-if="!mobileSidebarOpen" :size="16" />
        <X v-else :size="16" />
        <span>{{ locale === 'tr' ? 'Doküman Menüsü' : 'Docs Menu' }}</span>
      </button>
      <div class="docs-mobile-crumb mono">
        <span>{{ String(activeDoc.order).padStart(2, '0') }}</span>
        <ChevronRight :size="13" />
        <span class="truncate">{{ activeDoc.title }}</span>
      </div>
    </div>

    <div class="docs-layout container">
      <!-- Left Step-by-Step Sidebar -->
      <aside
        class="docs-sidebar"
        :class="{ open: mobileSidebarOpen }"
        data-testid="docs-sidebar"
        :aria-label="locale === 'tr' ? 'Dokümantasyon gezintisi' : 'Documentation navigation'"
      >
        <div class="docs-sidebar-inner">
          <div class="docs-sidebar-head">
            <div class="docs-sidebar-title">
              <BookOpen :size="16" class="accent-icon" />
              <span>{{ locale === 'tr' ? 'V4War Rehberleri' : 'V4War Guides' }}</span>
            </div>
            <span class="mono docs-count">{{ allPages.length }}</span>
          </div>

          <div class="docs-search-wrap">
            <Search :size="14" class="docs-search-icon" />
            <input
              v-model="searchQuery"
              type="search"
              data-testid="docs-search-input"
              class="docs-search-input"
              :placeholder="locale === 'tr' ? 'Dokümanlarda ara...' : 'Search docs...'"
              :aria-label="locale === 'tr' ? 'Dokümanlarda ara' : 'Search docs'"
            />
          </div>

          <nav class="docs-nav-groups">
            <div v-for="group in groupedSidebar" :key="group.id" class="docs-group">
              <div class="docs-group-label">{{ group.label }}</div>
              <ul class="docs-group-list">
                <li v-for="page in group.items" :key="page.slug">
                  <a
                    :href="buildHref(`/docs/${page.slug}`)"
                    class="docs-nav-link"
                    :class="{ active: page.slug === activeDoc.slug }"
                    :data-testid="`doc-nav-${page.slug}`"
                    @click.prevent="openDoc(page.slug)"
                  >
                    <span class="docs-step-num mono">{{ String(page.order).padStart(2, '0') }}</span>
                    <span class="docs-nav-title">{{ page.title }}</span>
                  </a>
                </li>
              </ul>
            </div>

            <div v-if="groupedSidebar.length === 0" class="docs-empty-search">
              {{ locale === 'tr' ? 'Sonuç bulunamadı.' : 'No matching guide found.' }}
            </div>
          </nav>
        </div>
      </aside>

      <!-- Center Markdown Article -->
      <main class="docs-main" data-testid="docs-article">
        <div class="docs-breadcrumb mono">
          <span>{{ locale === 'tr' ? 'Dokümanlar' : 'Docs' }}</span>
          <ChevronRight :size="13" />
          <span>{{ activeGroupLabel }}</span>
          <ChevronRight :size="13" />
          <span class="crumb-current">{{ activeDoc.title }}</span>
        </div>

        <header class="docs-article-header">
          <div class="docs-step-pill mono">
            {{ locale === 'tr' ? 'ADIM' : 'STEP' }} {{ String(activeDoc.order).padStart(2, '0') }} /
            {{ String(allPages.length).padStart(2, '0') }}
          </div>
          <h1 class="docs-title">{{ activeDoc.title }}</h1>
          <p v-if="activeDoc.description" class="docs-lead">{{ activeDoc.description }}</p>
        </header>

        <article
          class="doc-prose"
          data-testid="doc-prose"
          v-html="activeDoc.html"
          @click="handleArticleClick"
        ></article>

        <!-- Meta + Edit on GitHub -->
        <div class="docs-meta-bar">
          <span class="mono docs-file-tag">docs/content/{{ locale }}/{{ activeDoc.fileName }}</span>
          <a
            :href="activeDoc.githubEditUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="docs-edit-link"
          >
            <span>{{ locale === 'tr' ? 'GitHub üzerinde düzenle' : 'Edit this page on GitHub' }}</span>
            <ExternalLink :size="13" />
          </a>
        </div>

        <!-- Prev / Next Step Cards -->
        <nav class="docs-pager" :aria-label="locale === 'tr' ? 'Sayfa geçişi' : 'Page navigation'">
          <a
            v-if="prevNext.prev"
            :href="buildHref(`/docs/${prevNext.prev.slug}`)"
            class="docs-pager-card prev"
            data-testid="doc-prev-link"
            @click.prevent="openDoc(prevNext.prev.slug)"
          >
            <span class="pager-dir mono">
              <ArrowLeft :size="13" />
              {{ locale === 'tr' ? 'Önceki Adım' : 'Previous' }}
            </span>
            <span class="pager-title">{{ prevNext.prev.title }}</span>
          </a>
          <div v-else class="docs-pager-spacer"></div>

          <a
            v-if="prevNext.next"
            :href="buildHref(`/docs/${prevNext.next.slug}`)"
            class="docs-pager-card next"
            data-testid="doc-next-link"
            @click.prevent="openDoc(prevNext.next.slug)"
          >
            <span class="pager-dir mono">
              {{ locale === 'tr' ? 'Sonraki Adım' : 'Next' }}
              <ArrowRight :size="13" />
            </span>
            <span class="pager-title">{{ prevNext.next.title }}</span>
          </a>
        </nav>
      </main>

      <!-- Right On-This-Page TOC -->
      <aside
        class="docs-toc"
        data-testid="docs-toc"
        :aria-label="locale === 'tr' ? 'Bu sayfada' : 'On this page'"
      >
        <div class="docs-toc-inner">
          <div class="docs-toc-head">
            <ListTree :size="14" class="accent-icon" />
            <span>{{ locale === 'tr' ? 'Bu Sayfada' : 'On This Page' }}</span>
          </div>
          <ul class="docs-toc-list">
            <li
              v-for="h in activeDoc.headings"
              :key="h.id"
              :class="{ 'level-3': h.level === 3 }"
            >
              <a
                :href="`#${h.id}`"
                class="docs-toc-link"
                :class="{ active: activeHeadingId === h.id }"
                @click.prevent="jumpToHeading(h.id)"
              >
                {{ h.text }}
              </a>
            </li>
          </ul>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.docs-shell {
  padding-top: 90px;
  padding-bottom: 72px;
  min-height: calc(100vh - 120px);
}

.docs-mobile-bar {
  display: none;
}

.docs-layout {
  display: grid;
  grid-template-columns: 272px minmax(0, 1fr) 224px;
  gap: 28px;
  align-items: start;
}

/* Left Sidebar */
.docs-sidebar {
  position: sticky;
  top: 86px;
  max-height: calc(100vh - 106px);
  overflow-y: auto;
  border-radius: 22px;
  background: var(--bg-surface);
  border: 1px solid var(--border-muted);
  padding: 16px;
}

.docs-sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.docs-sidebar-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 0.9rem;
  font-weight: 800;
  color: var(--text-primary);
}

.accent-icon {
  color: var(--seed-accent);
}

.docs-count {
  font-size: 0.72rem;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--bg-pill);
  color: var(--text-secondary);
  border: 1px solid var(--border-muted);
}

.docs-search-wrap {
  position: relative;
  margin-bottom: 14px;
}

.docs-search-icon {
  position: absolute;
  left: 11px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-muted);
  pointer-events: none;
}

.docs-search-input {
  width: 100%;
  padding: 9px 12px 9px 32px;
  border-radius: 12px;
  border: 1px solid var(--border-muted);
  background: var(--bg-nested);
  color: var(--text-primary);
  font-size: 0.82rem;
  outline: none;
}

.docs-search-input:focus {
  border-color: var(--seed-accent);
}

.docs-nav-groups {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.docs-group-label {
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-muted);
  margin-bottom: 6px;
  padding: 0 6px;
}

.docs-group-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.docs-nav-link {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 8px 10px;
  border-radius: 12px;
  font-size: 0.83rem;
  font-weight: 600;
  color: var(--text-secondary);
  border: 1px solid transparent;
  transition: background 140ms ease, color 140ms ease, border-color 140ms ease;
}

.docs-nav-link:hover {
  background: var(--bg-nested);
  color: var(--text-primary);
}

.docs-nav-link.active {
  background: color-mix(in srgb, var(--seed-primary) 16%, var(--bg-nested));
  color: var(--text-primary);
  border-color: var(--border-subtle);
}

.docs-step-num {
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--text-muted);
  min-width: 20px;
}

.docs-nav-link.active .docs-step-num {
  color: var(--seed-accent);
}

.docs-nav-title {
  line-height: 1.3;
}

.docs-empty-search {
  font-size: 0.82rem;
  color: var(--text-muted);
  padding: 12px 6px;
}

/* Center Article */
.docs-main {
  min-width: 0;
  border-radius: 26px;
  background: var(--bg-surface);
  border: 1px solid var(--border-muted);
  padding: 28px 34px 32px;
}

.docs-breadcrumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  font-size: 0.74rem;
  color: var(--text-muted);
  margin-bottom: 14px;
}

.crumb-current {
  color: var(--seed-accent);
  font-weight: 700;
}

.docs-article-header {
  padding-bottom: 18px;
  margin-bottom: 22px;
  border-bottom: 1px solid var(--border-muted);
}

.docs-step-pill {
  display: inline-block;
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--seed-accent);
  background: color-mix(in srgb, var(--seed-primary) 14%, var(--bg-nested));
  border: 1px solid var(--border-subtle);
  padding: 3px 10px;
  border-radius: 999px;
  margin-bottom: 10px;
}

.docs-title {
  font-size: clamp(1.65rem, 3vw, 2.3rem);
  font-weight: 800;
  letter-spacing: -0.025em;
  line-height: 1.15;
  color: var(--text-primary);
  margin-bottom: 10px;
}

.docs-lead {
  font-size: 1rem;
  color: var(--text-secondary);
  line-height: 1.6;
}

/* Rendered Markdown prose */
.doc-prose {
  font-size: 0.96rem;
  line-height: 1.72;
  color: var(--text-secondary);
}

.doc-prose :deep(h1:first-child) {
  display: none;
}

.doc-prose :deep(h2) {
  font-size: 1.35rem;
  font-weight: 800;
  letter-spacing: -0.015em;
  color: var(--text-primary);
  margin: 30px 0 12px;
  padding-top: 8px;
  border-top: 1px solid var(--border-muted);
  scroll-margin-top: 96px;
}

.doc-prose :deep(h3) {
  font-size: 1.08rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 22px 0 8px;
  scroll-margin-top: 96px;
}

.doc-prose :deep(p) {
  margin-bottom: 14px;
}

.doc-prose :deep(ul),
.doc-prose :deep(ol) {
  margin: 0 0 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.doc-prose :deep(strong) {
  color: var(--text-primary);
  font-weight: 700;
}

.doc-prose :deep(a) {
  color: var(--seed-accent);
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.doc-prose :deep(code) {
  font-family: var(--font-mono);
  font-size: 0.84em;
  background: var(--bg-nested);
  border: 1px solid var(--border-muted);
  color: var(--text-primary);
  padding: 2px 6px;
  border-radius: 6px;
}

.doc-prose :deep(.doc-code-wrap) {
  margin: 16px 0 18px;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid var(--border-muted);
  background: var(--bg-nested);
}

.doc-prose :deep(.doc-code-bar) {
  padding: 7px 14px;
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border-muted);
  background: var(--bg-surface-elevated);
}

.doc-prose :deep(.doc-pre) {
  padding: 14px 16px;
  overflow-x: auto;
  font-size: 0.82rem;
  line-height: 1.55;
  margin: 0;
}

.doc-prose :deep(.doc-pre code) {
  background: transparent;
  border: none;
  padding: 0;
}

.doc-prose :deep(blockquote) {
  margin: 16px 0;
  padding: 12px 16px;
  border-left: 3px solid var(--seed-accent);
  background: var(--bg-nested);
  border-radius: 0 14px 14px 0;
  color: var(--text-primary);
}

.doc-prose :deep(blockquote p:last-child) {
  margin-bottom: 0;
}

.doc-prose :deep(.doc-table-wrap) {
  overflow-x: auto;
  margin: 16px 0 20px;
  border-radius: 14px;
  border: 1px solid var(--border-muted);
}

.doc-prose :deep(table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.86rem;
}

.doc-prose :deep(th) {
  text-align: left;
  padding: 10px 14px;
  background: var(--bg-nested);
  color: var(--text-primary);
  font-weight: 700;
  border-bottom: 1px solid var(--border-muted);
}

.doc-prose :deep(td) {
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-muted);
  vertical-align: top;
}

.doc-prose :deep(tr:last-child td) {
  border-bottom: none;
}

.doc-prose :deep(.doc-figure) {
  margin: 20px 0 24px;
  padding: 14px;
  border-radius: 20px;
  background: var(--bg-nested);
  border: 1px solid var(--border-muted);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.doc-prose :deep(.doc-figure img) {
  max-width: 310px;
  width: 100%;
  height: auto;
  border-radius: 16px;
  border: 1px solid var(--border-muted);
  box-shadow: var(--shadow-card);
}

.doc-prose :deep(.doc-figure figcaption) {
  font-size: 0.78rem;
  color: var(--text-muted);
  text-align: center;
}

.docs-meta-bar {
  margin-top: 28px;
  padding-top: 16px;
  border-top: 1px solid var(--border-muted);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.docs-file-tag {
  font-size: 0.74rem;
  color: var(--text-muted);
}

.docs-edit-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--seed-accent);
}

.docs-pager {
  margin-top: 18px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.docs-pager-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 16px;
  background: var(--bg-nested);
  border: 1px solid var(--border-muted);
  transition: border-color 140ms ease, transform 140ms ease;
}

.docs-pager-card:hover {
  border-color: var(--seed-accent);
  transform: translateY(-1px);
}

.docs-pager-card.next {
  align-items: flex-end;
  text-align: right;
}

.pager-dir {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--text-muted);
}

.pager-title {
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--text-primary);
}

/* Right TOC */
.docs-toc {
  position: sticky;
  top: 86px;
  max-height: calc(100vh - 106px);
  overflow-y: auto;
  border-radius: 20px;
  background: var(--bg-surface);
  border: 1px solid var(--border-muted);
  padding: 16px;
}

.docs-toc-head {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 0.78rem;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-primary);
  margin-bottom: 10px;
}

.docs-toc-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.docs-toc-list li.level-3 {
  padding-left: 12px;
}

.docs-toc-link {
  display: block;
  padding: 4px 8px;
  border-radius: 8px;
  font-size: 0.78rem;
  line-height: 1.35;
  color: var(--text-muted);
  border-left: 2px solid transparent;
  transition: color 140ms ease, border-color 140ms ease;
}

.docs-toc-link:hover,
.docs-toc-link.active {
  color: var(--seed-accent);
  border-left-color: var(--seed-accent);
}

@media (max-width: 1120px) {
  .docs-layout {
    grid-template-columns: 256px minmax(0, 1fr);
  }
  .docs-toc {
    display: none;
  }
}

@media (max-width: 860px) {
  .docs-mobile-bar {
    position: sticky;
    top: 72px;
    z-index: 35;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin: 0 16px 14px;
    padding: 9px 14px;
    border-radius: 16px;
    background: var(--bg-glass);
    backdrop-filter: blur(18px);
    border: 1px solid var(--border-muted);
  }
  .docs-mobile-trigger {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    padding: 6px 11px;
    border-radius: 10px;
    border: 1px solid var(--border-muted);
    background: var(--bg-nested);
    color: var(--text-primary);
    font-size: 0.8rem;
    font-weight: 700;
    cursor: pointer;
    flex-shrink: 0;
  }
  .docs-mobile-crumb {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 0.74rem;
    color: var(--text-secondary);
    min-width: 0;
  }
  .truncate {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .docs-layout {
    grid-template-columns: 1fr;
  }
  .docs-sidebar {
    display: none;
    position: static;
    max-height: none;
  }
  .docs-sidebar.open {
    display: block;
  }
  .docs-main {
    padding: 20px 18px 24px;
  }
  .docs-pager {
    grid-template-columns: 1fr;
  }
}
</style>
