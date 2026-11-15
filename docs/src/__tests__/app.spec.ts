import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../App.vue'

describe('V4War Landing App', () => {
  beforeEach(() => {
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
              browser_download_url: 'https://github.com/theyusa/V4War/releases/download/v2.1.3/V4War-2.1.3-arm64-v8a.apk',
            },
          ],
        }),
      }),
    )
    Element.prototype.scrollIntoView = vi.fn()
  })

  it('renders hero, switches locale between EN and TR, and updates UI copy', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.text()).toContain('V4War')
    expect(wrapper.text()).toContain('Next-Gen Android')

    await wrapper.get('[data-testid="locale-toggle"]').trigger('click')
    expect(wrapper.text()).toContain('Yeni Nesil Android')
    expect(wrapper.text()).toContain('Hotspot Paylaşım')
    expect(wrapper.text()).toContain('TLS Fragment AÇIK')
    expect(wrapper.text()).toContain('1. Ton (Tuval)')
    expect(document.documentElement.lang).toBe('tr')

    await wrapper.get('[data-testid="locale-toggle"]').trigger('click')
    expect(wrapper.text()).toContain('Next-Gen Android')
    expect(document.documentElement.lang).toBe('en')
  })

  it('switches hero phone tabs between live studio and real LAN screenshot', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.find('[data-testid="phone-live-view"]').exists()).toBe(true)
    await wrapper.get('[data-testid="phone-tab-screenshot"]').trigger('click')
    expect(wrapper.find('[data-testid="phone-screenshot-view"]').exists()).toBe(true)
    const img = wrapper.get('[data-testid="phone-screenshot-view"] img')
    expect(img.attributes('src')).toContain('lan-sharing.jpg')
  })

  it('switches hotspot backends and toolbox inspector items', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('[data-testid="hotspot-tab-root"]').trigger('click')
    expect(wrapper.get('[data-testid="hotspot-detail"]').text()).toContain('RootTetherHelper')

    await wrapper.get('[data-testid="tool-tile-dns-leak"]').trigger('click')
    expect(wrapper.get('[data-testid="tool-inspector"]').text()).toContain('198.18.0.0/15')
  })

  it('filters protocols by category and search query including empty state and reset', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('[data-testid="protocol-cat-dns"]').trigger('click')
    const grid = wrapper.get('[data-testid="protocol-grid"]')
    expect(grid.text()).toContain('DNSTT')
    expect(grid.text()).not.toContain('WireGuard')

    await wrapper.get('[data-testid="protocol-cat-all"]').trigger('click')
    await wrapper.get('[data-testid="protocol-search"]').setValue('amnezia')
    expect(grid.text()).toContain('AmneziaWG')
    expect(grid.text()).not.toContain('VLESS')

    await wrapper.get('[data-testid="protocol-search"]').setValue('nonexistent-xyz')
    expect(grid.findAll('.protocol-card')).toHaveLength(0)
    expect(wrapper.find('[data-testid="protocol-empty"]').exists()).toBe(true)

    await wrapper.get('[data-testid="protocol-reset"]').trigger('click')
    expect(grid.findAll('.protocol-card')).toHaveLength(28)
  })

  it('updates theme tokens via studio & navbar cycle and toggles FAQ accordion items', async () => {
    const wrapper = mount(App)
    await flushPromises()

    await wrapper.get('[data-testid="nav-surface-cycle"]').trigger('click')
    expect(document.documentElement.dataset.surface).toBe('amoled')

    await wrapper.get('[data-testid="surface-btn-light"]').trigger('click')
    await wrapper.get('[data-testid="seed-btn-verdant-mint"]').trigger('click')
    expect(document.documentElement.style.getPropertyValue('--seed-primary')).toBe('#00B96B')
    expect(document.documentElement.style.getPropertyValue('--seed-accent')).toBe('#007A47')
    expect(document.documentElement.style.getPropertyValue('--bg-canvas')).toBe('#F4FBF7')

    expect(wrapper.find('[data-testid="faq-answer-0"]').exists()).toBe(true)
    await wrapper.get('[data-testid="faq-trigger-0"]').trigger('click')
    expect(wrapper.find('[data-testid="faq-answer-0"]').exists()).toBe(false)
    await wrapper.get('[data-testid="faq-trigger-1"]').trigger('click')
    expect(wrapper.find('[data-testid="faq-answer-1"]').exists()).toBe(true)
  })
})
