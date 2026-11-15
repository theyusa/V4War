import { describe, expect, it } from 'vitest'
import { resolveThemeTokens, SEED_PRESETS } from '../utils/theme'

describe('Theme Studio 3-Tone engine', () => {
  const crimson = SEED_PRESETS[0]
  const mint = SEED_PRESETS.find((s) => s.id === 'verdant-mint')!

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
})
