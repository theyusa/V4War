import { describe, expect, it } from 'vitest'
import {
  DOC_GROUPS,
  getDocBySlug,
  getDocPages,
  getPrevNextDocs,
  searchDocs,
} from '../utils/docs'
import { buildHref, parseAppLocation } from '../utils/router'

describe('Markdown Documentation & Multi-Page Router', () => {
  it('loads all 12 EN and 12 TR markdown guides in order with headings and HTML', () => {
    const enPages = getDocPages('en')
    const trPages = getDocPages('tr')

    expect(enPages).toHaveLength(12)
    expect(trPages).toHaveLength(12)
    expect(enPages.map((p) => p.slug)).toEqual(trPages.map((p) => p.slug))

    for (const page of [...enPages, ...trPages]) {
      expect(page.title.length).toBeGreaterThan(3)
      expect(page.headings.length).toBeGreaterThan(0)
      expect(page.html.length).toBeGreaterThan(100)
      expect(page.html).not.toContain('<h1')
      for (const h of page.headings) {
        expect(h.text).not.toContain('&amp;')
        expect(h.text).not.toContain('&#39;')
      }
      expect(DOC_GROUPS.some((g) => g.id === page.group)).toBe(true)
    }

    const hotspotTr = getDocBySlug('tr', 'hotspot-share')
    const statusHeading = hotspotTr.headings.find((h) => h.text.includes('Recovery Status'))
    expect(statusHeading?.text).toBe('Shizuku & Recovery Status paneli')
    expect(statusHeading?.id).toBe('shizuku-recovery-status-paneli')
  })

  it('rewrites relative markdown screenshot paths to public BASE_URL images without raw ../../public', () => {
    const hotspotDoc = getDocBySlug('en', 'hotspot-share')
    expect(hotspotDoc.html).not.toContain('../../public/')
    expect(hotspotDoc.html).toContain('screenshots/hotspot-share.webp')
    expect(hotspotDoc.html).toContain('doc-figure')
  })

  it('computes prev/next step links and filters search queries in both languages', () => {
    const middle = getPrevNextDocs('en', 'hotspot-share')
    expect(middle.prev?.slug).toBe('vpn-proxy-modes')
    expect(middle.next?.slug).toBe('local-proxy-desktop')

    const start = getPrevNextDocs('tr', 'overview')
    expect(start.prev).toBeNull()
    expect(start.next?.slug).toBe('installation')

    const shizukuMatches = searchDocs('tr', 'Shizuku')
    expect(shizukuMatches.some((p) => p.slug === 'installation')).toBe(true)
    expect(shizukuMatches.some((p) => p.slug === 'hotspot-share')).toBe(true)
  })

  it('parses root / routes, subpath overrides, 404 ?p= redirects, and hash fallbacks', () => {
    expect(parseAppLocation('/', '', '', '/').page).toBe('home')
    expect(parseAppLocation('/docs/hotspot-share', '', '', '/')).toEqual({
      page: 'docs',
      docSlug: 'hotspot-share',
      hash: '',
      canonicalPath: '/docs/hotspot-share',
    })
    expect(parseAppLocation('/404.html', '', '?p=/docs/network-toolbox', '/')).toEqual({
      page: 'docs',
      docSlug: 'network-toolbox',
      hash: '',
      canonicalPath: '/docs/network-toolbox',
    })
    expect(parseAppLocation('/', '#/theme-studio', '', '/').page).toBe('theme-studio')
    expect(buildHref('/', '/')).toBe('/')
    expect(buildHref('/docs/overview', '/')).toBe('/docs/overview')

    expect(parseAppLocation('/V4War/', '', '', '/V4War/').page).toBe('home')
    expect(parseAppLocation('/V4War/docs/hotspot-share', '', '', '/V4War/')).toEqual({
      page: 'docs',
      docSlug: 'hotspot-share',
      hash: '',
      canonicalPath: '/docs/hotspot-share',
    })
    expect(parseAppLocation('/V4War/404.html', '', '?p=/docs/network-toolbox', '/V4War/')).toEqual({
      page: 'docs',
      docSlug: 'network-toolbox',
      hash: '',
      canonicalPath: '/docs/network-toolbox',
    })
    expect(buildHref('/docs/overview', '/V4War/')).toBe('/V4War/docs/overview')
  })
})
