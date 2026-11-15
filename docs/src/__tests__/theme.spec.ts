import { describe, expect, it } from 'vitest'
import {
  createCustomSeedPreset,
  normalizeHex,
  resolveThemeTokens,
  SEED_PRESETS,
} from '../utils/theme'

describe('Theme Studio 3-Tone engine', () => {
  const crimson = SEED_PRESETS[0]
  const mint = SEED_PRESETS.find((s) => s.id === 'verdant-mint')!

  it('exposes Material You plus 22 color seed swatches matching Theme.kt', () => {
    expect(SEED_PRESETS.length).toBeGreaterThanOrEqual(22)
    expect(SEED_PRESETS.some((s) => s.id === 'material-you' && s.isMonet)).toBe(true)
    expect(crimson.id).toBe('crimson')
    expect(mint.isAppDefault).toBe(true)
  })

  it('produces true #000000 canvas and card surface in pure-amoled mode', () => {
    const tokens = resolveThemeTokens('pure-amoled', crimson)
    expect(tokens.tone1).toBe('#000000')
    expect(tokens.tone2).toBe('#000000')
    expect(tokens.tone3).not.toBe('#000000')
    expect(tokens.textPrimary).toBe('#FFFFFF')
  })

  it('blends seed color into card surface for tinted amoled mode', () => {
    const tokens = resolveThemeTokens('amoled', mint)
    expect(tokens.tone1).toBe('#000000')
    expect(tokens.tone2).not.toBe('#000000')
    expect(tokens.seedPrimary).toBe('#00B96B')
  })

  it('uses distinct 3-tone surfaces and high-contrast light ink accents for dark and light modes', () => {
    const dark = resolveThemeTokens('dark', mint)
    expect(dark.tone1).toBe('#04140D')
    expect(dark.tone2).toBe('#0B2418')

    const light = resolveThemeTokens('light', mint)
    expect(light.tone1).toBe('#F4FBF7')
    expect(light.tone2).toBe('#FFFFFF')
    expect(light.tone3).toMatch(/^#[0-9A-F]{6}$/)
    expect(light.textPrimary).toBe('#1C1B1F')
    expect(light.seedAccent).toBe('#007A47')

    const amber = SEED_PRESETS.find((s) => s.id === 'amber')!
    const lightAmber = resolveThemeTokens('light', amber)
    expect(lightAmber.seedAccent).toBe('#946300')
  })

  it('supports monochrome black, white, and light-gray overrides', () => {
    const black = resolveThemeTokens('pure-amoled', crimson, 'black')
    expect(black.canvas).toBe('#000000')
    expect(black.seedPrimary).toBe('#FFFFFF')

    const white = resolveThemeTokens('pure-amoled', crimson, 'white')
    expect(white.effectiveSurface).toBe('light')
    expect(white.canvas).toBe('#FFFFFF')
    expect(white.textPrimary).toBe('#212121')

    const gray = resolveThemeTokens('pure-amoled', crimson, 'light-gray')
    expect(gray.effectiveSurface).toBe('light')
    expect(gray.canvas).toBe('#F5F5F7')
  })

  it('normalizes HEX strings and builds custom seed presets safely', () => {
    expect(normalizeHex('3b82f6')).toBe('#3B82F6')
    expect(normalizeHex('#0fa')).toBe('#00FFAA')
    expect(normalizeHex('invalid')).toBeNull()

    const custom = createCustomSeedPreset('#7C3AED')
    expect(custom.id).toBe('custom')
    expect(custom.isCustom).toBe(true)
    expect(custom.primary).toBe('#7C3AED')
    expect(custom.tone1Dark).toMatch(/^#[0-9A-F]{6}$/)
  })
})
