import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../App.vue'

describe('V4War Multi-Page App', () => {
  beforeEach(() => {
    const store = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => {
        store.set(k, v)
      },
      removeItem: (k: string) => {
        store.delete(k)
      },
      clear: () => {
        store.clear()
      },
    })
    window.history.replaceState({}, '', '/V4War/')
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          tag_name: 'v2.1.3',
          published_at: '2026-11-15T00:00:00Z',
          html_url: 'https://github.com/theyusa/V4War/releases/tag/v2.1.3',
          assets: [
            {
              name: 'V4War-2.1.3-arm64-v8a.apk',
              size: 47279623,
              browser_download_url:
                'https://github.com/theyusa/V4War/releases/download/v2.1.3/V4War-2.1.3-arm64-v8a.apk',
            },
          ],
        }),
      }),
    )
    Element.prototype.scrollIntoView = vi.fn()
    window.scrollTo = vi.fn()
  })

  it('renders modular Home, toggles EN/TR, switches Hero tabs, and toggles FAQ accordion', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.text()).toContain('V4War')
    expect(wrapper.text()).toContain('Next-Gen Android')
    expect(wrapper.find('[data-testid="pillar-card-docs"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="showcase-appearance"]').exists()).toBe(true)
    // Home stays uncluttered — interactive protocol grid lives on Explore Lab
    expect(wrapper.find('[data-testid="protocol-grid"]').exists()).toBe(false)

    // Hero live / real screenshot switch uses stripped appearance screenshot
    expect(wrapper.find('[data-testid="phone-live-view"]').exists()).toBe(true)
    await wrapper.get('[data-testid="phone-tab-screenshot"]').trigger('click')
    const img = wrapper.get('[data-testid="phone-screenshot-view"] img')
    expect(img.attributes('src')).toContain('screenshots/appearance-theme-studio.webp')

    // FAQ accordion
    expect(wrapper.find('[data-testid="faq-answer-0"]').exists()).toBe(true)
    await wrapper.get('[data-testid="faq-trigger-0"]').trigger('click')
    expect(wrapper.find('[data-testid="faq-answer-0"]').exists()).toBe(false)
    await wrapper.get('[data-testid="faq-trigger-1"]').trigger('click')
    expect(wrapper.find('[data-testid="faq-answer-1"]').exists()).toBe(true)

    // Locale switch
    await wrapper.get('[data-testid="locale-toggle"]').trigger('click')
    expect(wrapper.text()).toContain('Yeni Nesil Android')
    expect(document.documentElement.lang).toBe('tr')
  })

  it('navigates to Docs with step-by-step sidebar, markdown prose, TOC, search, and prev/next', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('[data-testid="nav-link-docs"]').trigger('click')
    expect(wrapper.find('[data-testid="docs-sidebar"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="docs-toc"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="doc-prose"]').text()).toContain('V4War')

    await wrapper.get('[data-testid="doc-nav-hotspot-share"]').trigger('click')
    expect(wrapper.findAll('main')).toHaveLength(1)
    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.get('[data-testid="docs-toc"]').text()).toContain('Shizuku & Recovery Status')
    expect(wrapper.get('[data-testid="docs-toc"]').text()).not.toContain('&amp;')
    expect(wrapper.get('[data-testid="docs-article"]').text()).toContain('Shizuku')
    expect(wrapper.get('[data-testid="doc-prose"] img').attributes('src')).toContain(
      'screenshots/hotspot-share.webp',
    )

    await wrapper.get('[data-testid="doc-next-link"]').trigger('click')
    expect(wrapper.get('[data-testid="docs-article"]').text()).toContain('Proxy')

    await wrapper.get('[data-testid="docs-search-input"]').setValue('Toolbox')
    expect(wrapper.find('[data-testid="doc-nav-network-toolbox"]').exists()).toBe(true)
  })

  it('navigates to Theme Studio 1:1 replica and applies surface, monochrome, seed, and custom HEX', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('[data-testid="nav-link-studio"]').trigger('click')
    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.find('[data-testid="live-preview-canvas"]').exists()).toBe(true)

    await wrapper.get('[data-testid="surface-btn-light"]').trigger('click')
    await wrapper.get('[data-testid="seed-btn-verdant-mint"]').trigger('click')
    expect(document.documentElement.style.getPropertyValue('--seed-primary')).toBe('#00B96B')
    expect(document.documentElement.style.getPropertyValue('--seed-accent')).toBe('#007A47')
    expect(document.documentElement.style.getPropertyValue('--bg-canvas')).toBe('#F4FBF7')

    await wrapper.get('[data-testid="mono-btn-black"]').trigger('click')
    expect(document.documentElement.style.getPropertyValue('--bg-canvas')).toBe('#000000')
    expect(document.documentElement.style.getPropertyValue('--seed-primary')).toBe('#FFFFFF')
    expect(wrapper.get('[data-testid="nav-surface-cycle"]').text()).toContain('Black')

    // Cycling surface from NavBar exits monochrome back to surface mode cleanly
    await wrapper.get('[data-testid="nav-surface-cycle"]').trigger('click')
    expect(document.documentElement.dataset.monochrome).toBe('none')

    await wrapper.get('[data-testid="custom-seed-btn"]').trigger('click')
    await wrapper.get('[data-testid="custom-hex-input"]').setValue('#7C3AED')
    await wrapper.get('[data-testid="custom-color-apply"]').trigger('click')
    expect(document.documentElement.style.getPropertyValue('--seed-primary')).toBe('#7C3AED')
  })

  it('navigates to Explore Lab and tests Hotspot, Toolbox, and Protocols filters + Download view', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('[data-testid="nav-link-explore"]').trigger('click')
    await wrapper.get('[data-testid="hotspot-tab-root"]').trigger('click')
    expect(wrapper.get('[data-testid="hotspot-detail"]').text()).toContain('RootTetherHelper')

    await wrapper.get('[data-testid="tool-tile-dns-leak"]').trigger('click')
    expect(wrapper.get('[data-testid="tool-inspector"]').text()).toContain('198.18.0.0/15')

    await wrapper.get('[data-testid="protocol-cat-dns"]').trigger('click')
    const grid = wrapper.get('[data-testid="protocol-grid"]')
    expect(grid.text()).toContain('DNSTT')
    expect(grid.text()).not.toContain('WireGuard')

    await wrapper.get('[data-testid="protocol-cat-all"]').trigger('click')
    await wrapper.get('[data-testid="protocol-search"]').setValue('nonexistent-xyz')
    expect(grid.findAll('.protocol-card')).toHaveLength(0)
    expect(wrapper.find('[data-testid="protocol-empty"]').exists()).toBe(true)

    await wrapper.get('[data-testid="protocol-reset"]').trigger('click')
    expect(grid.findAll('.protocol-card')).toHaveLength(28)

    await wrapper.get('[data-testid="nav-link-download"]').trigger('click')
    expect(wrapper.findAll('h1')).toHaveLength(1)
    expect(wrapper.find('[data-testid="apk-grid"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="apk-grid"]').text()).toContain('arm64-v8a')
  })
})
