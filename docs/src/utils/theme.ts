export type SurfaceMode = 'pure-amoled' | 'amoled' | 'dark' | 'light'

export interface SeedPreset {
  id: string
  nameEn: string
  nameTr: string
  primary: string
  accent: string
  tone1Dark: string
  tone2Dark: string
  tone1Light: string
  onPrimary: string
  isAppDefault?: boolean
}

/**
 * Curated seed palette matching app/src/main/res/values/colors.xml and values-night/colors.xml
 * plus V4War's brand crimson.
 */
export const SEED_PRESETS: SeedPreset[] = [
  {
    id: 'crimson',
    nameEn: 'V4War Crimson',
    nameTr: 'V4War Kızılı',
    primary: '#E11D24',
    accent: '#FF454B',
    tone1Dark: '#150507',
    tone2Dark: '#240B10',
    tone1Light: '#FFF8F8',
    onPrimary: '#FFFFFF',
  },
  {
    id: 'verdant-mint',
    nameEn: 'Verdant Mint',
    nameTr: 'Zümrüt Nane',
    primary: '#00B96B',
    accent: '#00D685',
    tone1Dark: '#04140D',
    tone2Dark: '#0B2418',
    tone1Light: '#F4FBF7',
    onPrimary: '#04140D',
    isAppDefault: true,
  },
  {
    id: 'blue',
    nameEn: 'Ocean Blue',
    nameTr: 'Okyanus Mavisi',
    primary: '#2196F3',
    accent: '#448AFF',
    tone1Dark: '#050F1E',
    tone2Dark: '#0D1C34',
    tone1Light: '#F4F8FD',
    onPrimary: '#FFFFFF',
  },
  {
    id: 'purple',
    nameEn: 'Royal Purple',
    nameTr: 'Asil Mor',
    primary: '#9C27B0',
    accent: '#E040FB',
    tone1Dark: '#11061C',
    tone2Dark: '#200E32',
    tone1Light: '#F8F5FD',
    onPrimary: '#FFFFFF',
  },
  {
    id: 'cyan',
    nameEn: 'Cyber Cyan',
    nameTr: 'Siber Camgöbeği',
    primary: '#00BCD4',
    accent: '#18FFFF',
    tone1Dark: '#031317',
    tone2Dark: '#082128',
    tone1Light: '#F3F9FA',
    onPrimary: '#031317',
  },
  {
    id: 'amber',
    nameEn: 'Amber Gold',
    nameTr: 'Kehribar Altını',
    primary: '#FFC107',
    accent: '#FFD740',
    tone1Dark: '#151003',
    tone2Dark: '#251B07',
    tone1Light: '#FCF9F2',
    onPrimary: '#151003',
  },
]

export interface SurfaceModeSpec {
  id: SurfaceMode
  labelEn: string
  labelTr: string
  hintEn: string
  hintTr: string
}

export const SURFACE_MODES: SurfaceModeSpec[] = [
  {
    id: 'pure-amoled',
    labelEn: 'Pure AMOLED',
    labelTr: 'Saf AMOLED',
    hintEn: '#000000 + hairline border',
    hintTr: '#000000 + ince çizgi kenarlık',
  },
  {
    id: 'amoled',
    labelEn: 'AMOLED',
    labelTr: 'AMOLED',
    hintEn: 'Tinted seed on #000000',
    hintTr: '#000000 üzerinde tonlu yüzey',
  },
  {
    id: 'dark',
    labelEn: 'Dark',
    labelTr: 'Koyu',
    hintEn: 'Material 3 3-Tone dark',
    hintTr: 'Material 3 3-ton koyu',
  },
  {
    id: 'light',
    labelEn: 'Light',
    labelTr: 'Açık',
    hintEn: 'Clean elevated daylight',
    hintTr: 'Aydınlık yüksek kontrast',
  },
]

function hexToRgb(hex: string): [number, number, number] {
  const clean = hex.replace('#', '')
  const num = parseInt(clean, 16)
  return [(num >> 16) & 255, (num >> 8) & 255, num & 255]
}

export function rgba(hex: string, alpha: number): string {
  const [r, g, b] = hexToRgb(hex)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

export function blendHex(baseHex: string, tintHex: string, ratio: number): string {
  const [r1, g1, b1] = hexToRgb(baseHex)
  const [r2, g2, b2] = hexToRgb(tintHex)
  const r = Math.round(r1 + (r2 - r1) * ratio)
  const g = Math.round(g1 + (g2 - g1) * ratio)
  const b = Math.round(b1 + (b2 - b1) * ratio)
  return `#${((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1)}`
}

export interface ResolvedThemeTokens {
  canvas: string
  surface: string
  surfaceElevated: string
  nested: string
  glass: string
  pill: string
  textPrimary: string
  textSecondary: string
  textMuted: string
  borderSubtle: string
  borderMuted: string
  borderStrong: string
  seedPrimary: string
  seedAccent: string
  seedOnPrimary: string
  tone1: string
  tone2: string
  tone3: string
}

/**
 * Mirrors V4War Theme.kt (resolveCardBackground, resolveNestedBoxBackground, resolveCardStrokeColor).
 */
export function resolveThemeTokens(surface: SurfaceMode, seed: SeedPreset): ResolvedThemeTokens {
  if (surface === 'light') {
    const tone1 = seed.tone1Light
    const tone2 = '#FFFFFF'
    const tone3 = blendHex('#FFFFFF', seed.primary, 0.08)
    return {
      canvas: tone1,
      surface: tone2,
      surfaceElevated: '#FFFFFF',
      nested: tone3,
      glass: 'rgba(255, 255, 255, 0.84)',
      pill: 'rgba(28, 27, 31, 0.05)',
      textPrimary: '#1C1B1F',
      textSecondary: '#49454F',
      textMuted: '#79747E',
      borderSubtle: rgba(seed.primary, 0.24),
      borderMuted: 'rgba(28, 27, 31, 0.12)',
      borderStrong: rgba(seed.primary, 0.65),
      seedPrimary: seed.primary,
      seedAccent: seed.primary,
      seedOnPrimary: seed.onPrimary,
      tone1,
      tone2,
      tone3,
    }
  }

  if (surface === 'dark') {
    const tone1 = seed.tone1Dark
    const tone2 = seed.tone2Dark
    const tone3 = blendHex(tone2, seed.primary, 0.12)
    return {
      canvas: tone1,
      surface: tone2,
      surfaceElevated: blendHex(tone2, '#FFFFFF', 0.03),
      nested: tone3,
      glass: rgba(tone1, 0.84),
      pill: 'rgba(255, 255, 255, 0.06)',
      textPrimary: '#F4F4F6',
      textSecondary: '#CAC4D0',
      textMuted: '#938F99',
      borderSubtle: rgba(seed.primary, 0.28),
      borderMuted: 'rgba(255, 255, 255, 0.12)',
      borderStrong: rgba(seed.accent, 0.72),
      seedPrimary: seed.primary,
      seedAccent: seed.accent,
      seedOnPrimary: seed.onPrimary,
      tone1,
      tone2,
      tone3,
    }
  }

  if (surface === 'amoled') {
    const tone1 = '#000000'
    const tone2 = blendHex('#000000', seed.primary, 0.11)
    const tone3 = blendHex(tone2, seed.primary, 0.12)
    return {
      canvas: tone1,
      surface: tone2,
      surfaceElevated: blendHex(tone2, '#FFFFFF', 0.02),
      nested: tone3,
      glass: 'rgba(0, 0, 0, 0.84)',
      pill: 'rgba(255, 255, 255, 0.06)',
      textPrimary: '#FFFFFF',
      textSecondary: '#CAC4D0',
      textMuted: '#8E8A96',
      borderSubtle: rgba(seed.primary, 0.34),
      borderMuted: 'rgba(255, 255, 255, 0.14)',
      borderStrong: rgba(seed.accent, 0.8),
      seedPrimary: seed.primary,
      seedAccent: seed.accent,
      seedOnPrimary: seed.onPrimary,
      tone1,
      tone2,
      tone3,
    }
  }

  // pure-amoled
  const tone1 = '#000000'
  const tone2 = '#000000'
  const tone3 = blendHex('#000000', seed.primary, 0.08)
  return {
    canvas: tone1,
    surface: tone2,
    surfaceElevated: '#070709',
    nested: tone3,
    glass: 'rgba(0, 0, 0, 0.82)',
    pill: 'rgba(255, 255, 255, 0.05)',
    textPrimary: '#FFFFFF',
    textSecondary: '#CAC4D0',
    textMuted: '#8E8A96',
    borderSubtle: rgba(seed.accent, 0.36),
    borderMuted: 'rgba(255, 255, 255, 0.13)',
    borderStrong: rgba(seed.accent, 0.85),
    seedPrimary: seed.primary,
    seedAccent: seed.accent,
    seedOnPrimary: seed.onPrimary,
    tone1,
    tone2,
    tone3,
  }
}

export function applyThemeToDocument(surface: SurfaceMode, seed: SeedPreset): ResolvedThemeTokens {
  const tokens = resolveThemeTokens(surface, seed)
  if (typeof document !== 'undefined') {
    const root = document.documentElement
    root.style.setProperty('--seed-primary', tokens.seedPrimary)
    root.style.setProperty('--seed-accent', tokens.seedAccent)
    root.style.setProperty('--seed-tone-1', tokens.tone1)
    root.style.setProperty('--seed-tone-2', tokens.tone2)
    root.style.setProperty('--seed-on-primary', tokens.seedOnPrimary)

    root.style.setProperty('--bg-canvas', tokens.canvas)
    root.style.setProperty('--bg-surface', tokens.surface)
    root.style.setProperty('--bg-surface-elevated', tokens.surfaceElevated)
    root.style.setProperty('--bg-nested', tokens.nested)
    root.style.setProperty('--bg-glass', tokens.glass)
    root.style.setProperty('--bg-pill', tokens.pill)

    root.style.setProperty('--text-primary', tokens.textPrimary)
    root.style.setProperty('--text-secondary', tokens.textSecondary)
    root.style.setProperty('--text-muted', tokens.textMuted)

    root.style.setProperty('--border-subtle', tokens.borderSubtle)
    root.style.setProperty('--border-muted', tokens.borderMuted)
    root.style.setProperty('--border-strong', tokens.borderStrong)

    const metaTheme = document.querySelector('meta[name="theme-color"]')
    if (metaTheme) {
      metaTheme.setAttribute('content', tokens.canvas)
    }
  }
  return tokens
}
