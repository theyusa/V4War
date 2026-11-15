import { marked } from 'marked'
import type { Locale } from '../data/content'

export type DocGroupId =
  | 'get-started'
  | 'android'
  | 'hotspot'
  | 'tools-appearance'
  | 'routing-protocols'
  | 'advanced'

export interface DocGroupMeta {
  id: DocGroupId
  label: Record<Locale, string>
}

export interface TocHeading {
  id: string
  text: string
  level: 2 | 3
}

export interface DocPage {
  slug: string
  fileName: string
  locale: Locale
  title: string
  description: string
  order: number
  group: DocGroupId
  markdown: string
  html: string
  headings: TocHeading[]
  plainText: string
  githubEditUrl: string
}

export const DOC_GROUPS: DocGroupMeta[] = [
  { id: 'get-started', label: { en: 'Get Started', tr: 'Başlangıç' } },
  { id: 'android', label: { en: 'Android & System', tr: 'Android ve Sistem' } },
  { id: 'hotspot', label: { en: 'Hotspot & Proxy Share', tr: 'Hotspot ve Proxy Paylaşım' } },
  { id: 'tools-appearance', label: { en: 'Toolbox & Appearance', tr: 'Ağ Araçları ve Görünüm' } },
  { id: 'routing-protocols', label: { en: 'Routing & Protocols', tr: 'Yönlendirme ve Protokoller' } },
  { id: 'advanced', label: { en: 'Backup & Troubleshooting', tr: 'Yedekleme ve Sorun Giderme' } },
]

const rawEnDocs = import.meta.glob('../../content/en/*.md', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>

const rawTrDocs = import.meta.glob('../../content/tr/*.md', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>

function slugifyHeading(text: string): string {
  return text
    .toLowerCase()
    .replace(/<[^>]+>/g, '')
    .replace(/[^\p{L}\p{N}\s-]/gu, '')
    .trim()
    .replace(/\s+/g, '-')
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function parseFrontmatter(raw: string): {
  meta: Record<string, string>
  body: string
} {
  const normalized = raw.replace(/\r\n/g, '\n')
  if (!normalized.startsWith('---\n')) {
    return { meta: {}, body: normalized }
  }
  const endIdx = normalized.indexOf('\n---\n', 4)
  if (endIdx === -1) {
    return { meta: {}, body: normalized }
  }
  const frontBlock = normalized.slice(4, endIdx)
  const body = normalized.slice(endIdx + 5)
  const meta: Record<string, string> = {}
  for (const line of frontBlock.split('\n')) {
    const colon = line.indexOf(':')
    if (colon === -1) continue
    const key = line.slice(0, colon).trim()
    const val = line.slice(colon + 1).trim().replace(/^["']|["']$/g, '')
    meta[key] = val
  }
  return { meta, body }
}

function resolveDocMediaSrc(href: string): string {
  const baseUrl = import.meta.env.BASE_URL || '/'
  const normalizedBase = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`
  const cleaned = href
    .replace(/^\.\.\/\.\.\/public\//, '')
    .replace(/^\.\//, '')
    .replace(/^\//, '')
  return `${normalizedBase}${cleaned}`
}

function renderMarkdown(markdown: string): { html: string; headings: TocHeading[] } {
  const headings: TocHeading[] = []
  const seenIds = new Map<string, number>()

  const renderer = new marked.Renderer()

  renderer.heading = ({ tokens, depth }) => {
    const innerHtml = marked.Parser.parseInline(tokens, { renderer })
    const plain = innerHtml.replace(/<[^>]+>/g, '').trim()
    let id = slugifyHeading(plain) || `section-${headings.length + 1}`
    const count = seenIds.get(id) ?? 0
    seenIds.set(id, count + 1)
    if (count > 0) id = `${id}-${count}`

    if (depth === 2 || depth === 3) {
      headings.push({ id, text: plain, level: depth })
    }
    return `<h${depth} id="${id}" class="doc-h${depth}"><a href="#${id}" class="doc-anchor" aria-hidden="true">#</a>${innerHtml}</h${depth}>\n`
  }

  renderer.image = ({ href, text, title }) => {
    const resolvedSrc = resolveDocMediaSrc(href || '')
    const alt = escapeHtml(text || '')
    const caption = escapeHtml(title || text || '')
    return `<figure class="doc-figure"><div class="doc-figure-frame"><img src="${resolvedSrc}" alt="${alt}" loading="lazy" class="doc-screenshot" /></div>${caption ? `<figcaption class="doc-figcaption">${caption}</figcaption>` : ''}</figure>`
  }

  renderer.link = ({ href, tokens, title }) => {
    const text = marked.Parser.parseInline(tokens, { renderer })
    const rawHref = href || '#'
    const titleAttr = title ? ` title="${escapeHtml(title)}"` : ''

    if (
      rawHref.startsWith('/docs/') ||
      rawHref === '/docs' ||
      rawHref === '/theme-studio' ||
      rawHref === '/explore' ||
      rawHref === '/download' ||
      rawHref === '/'
    ) {
      const base = (import.meta.env.BASE_URL || '/').replace(/\/$/, '')
      const fullHref = `${base}${rawHref}`
      return `<a href="${fullHref}" data-internal-route="${escapeHtml(rawHref)}"${titleAttr}>${text}</a>`
    }

    if (rawHref.startsWith('http://') || rawHref.startsWith('https://')) {
      return `<a href="${escapeHtml(rawHref)}" target="_blank" rel="noopener noreferrer"${titleAttr}>${text}</a>`
    }

    return `<a href="${escapeHtml(rawHref)}"${titleAttr}>${text}</a>`
  }

  renderer.table = (token) => {
    const headerCells = token.header
      .map((cell) => {
        const content = marked.Parser.parseInline(cell.tokens, { renderer })
        return `<th>${content}</th>`
      })
      .join('')

    const bodyRows = token.rows
      .map((row) => {
        const cells = row
          .map((cell) => {
            const content = marked.Parser.parseInline(cell.tokens, { renderer })
            return `<td>${content}</td>`
          })
          .join('')
        return `<tr>${cells}</tr>`
      })
      .join('')

    return `<div class="doc-table-wrap"><table><thead><tr>${headerCells}</tr></thead><tbody>${bodyRows}</tbody></table></div>`
  }

  renderer.code = ({ text, lang }) => {
    const language = escapeHtml(lang || 'text')
    const escaped = escapeHtml(text)
    return `<div class="doc-code-wrap"><div class="doc-code-bar mono"><span>${language}</span></div><pre class="doc-pre mono"><code>${escaped}</code></pre></div>`
  }

  const html = marked.parse(markdown, { renderer, gfm: true, breaks: false }) as string
  return { html, headings }
}

function buildDocCollection(rawMap: Record<string, string>, locale: Locale): DocPage[] {
  const pages: DocPage[] = []
  for (const [path, raw] of Object.entries(rawMap)) {
    const fileName = path.split('/').pop() || ''
    const slug = fileName.replace(/^\d+-/, '').replace(/\.md$/i, '')
    const { meta, body } = parseFrontmatter(raw)
    const { html, headings } = renderMarkdown(body)
    const plainText = body
      .replace(/```[\s\S]*?```/g, ' ')
      .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
      .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
      .replace(/[#>*`|_-]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim()

    pages.push({
      slug,
      fileName,
      locale,
      title: meta.title || slug,
      description: meta.description || '',
      order: Number(meta.order) || 99,
      group: (meta.group as DocGroupId) || 'get-started',
      markdown: body,
      html,
      headings,
      plainText,
      githubEditUrl: `https://github.com/theyusa/V4War/edit/main/docs/content/${locale}/${fileName}`,
    })
  }
  return pages.sort((a, b) => a.order - b.order)
}

const DOCS_BY_LOCALE: Record<Locale, DocPage[]> = {
  en: buildDocCollection(rawEnDocs, 'en'),
  tr: buildDocCollection(rawTrDocs, 'tr'),
}

export function getDocPages(locale: Locale): DocPage[] {
  return DOCS_BY_LOCALE[locale]
}

export function getDocBySlug(locale: Locale, slug?: string): DocPage {
  const pages = DOCS_BY_LOCALE[locale]
  if (!slug) return pages[0]
  return pages.find((p) => p.slug === slug) ?? pages[0]
}

export function getPrevNextDocs(locale: Locale, slug: string): {
  prev: DocPage | null
  next: DocPage | null
} {
  const pages = DOCS_BY_LOCALE[locale]
  const idx = pages.findIndex((p) => p.slug === slug)
  if (idx === -1) {
    return { prev: null, next: pages[1] ?? null }
  }
  return {
    prev: idx > 0 ? pages[idx - 1] : null,
    next: idx < pages.length - 1 ? pages[idx + 1] : null,
  }
}

export function searchDocs(locale: Locale, query: string): DocPage[] {
  const q = query.trim().toLowerCase()
  const pages = DOCS_BY_LOCALE[locale]
  if (!q) return pages
  return pages.filter(
    (p) =>
      p.title.toLowerCase().includes(q) ||
      p.description.toLowerCase().includes(q) ||
      p.plainText.toLowerCase().includes(q),
  )
}
