export type SurfaceMode = 'pure-amoled' | 'amoled' | 'dark' | 'light' | 'system'
export type MonochromeMode = 'none' | 'black' | 'white' | 'light-gray'

export interface SeedPreset {
  id: string
  nameEn: string
  nameTr: string
  primary: string
  accent: string
  accentLight: string
  tone1Dark: string
  tone2Dark: string
  tone1Light: string
  onPrimary: string
  isAppDefault?: boolean
  isMonet?: boolean
  isCustom?: boolean
}

export interface MonochromeSpec {
  id: Exclude<MonochromeMode, 'none'>
  labelEn: string
  labelTr: string
  dotColor: string
  dotBorder: string
}

export const MONOCHROME_THEMES: MonochromeSpec[] = [
  {
    id: 'black',
    labelEn: 'Black',
    labelTr: 'Siyah',
    dotColor: '#000000',
    dotBorder: '#64748B',
  },
  {
    id: 'white',
    labelEn: 'White',
    labelTr: 'Beyaz',
    dotColor: '#FFFFFF',
    dotBorder: '#CBD5E1',
  },
  {
    id: 'light-gray',
    labelEn: 'Light Gray',
    labelTr: 'Açık Gri',
    dotColor: '#E5E7EB',
    dotBorder: '#94A3B8',
  },
]

function hexToRgb(hex: string): [number, number, number] {
  const clean = hex.replace('#', '').padEnd(6, '0').slice(0, 6)
  const num = parseInt(clean, 16)
  if (Number.isNaN(num)) return [0, 185, 107]
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
  return `#${((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1).toUpperCase()}`
}

export function relativeLuminance(hex: string): number {
  const [r8, g8, b8] = hexToRgb(hex)
  const toLinear = (c: number) => {
    const s = c / 255
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4
  }
  return 0.2126 * toLinear(r8) + 0.7152 * toLinear(g8) + 0.0722 * toLinear(b8)
}

export function normalizeHex(input: string): string | null {
  const clean = input.trim().replace(/^#/, '')
  if (/^[0-9a-fA-F]{6}$/.test(clean)) {
    return `#${clean.toUpperCase()}`
  }
  if (/^[0-9a-fA-F]{3}$/.test(clean)) {
    const expanded = clean
      .split('')
      .map((c) => c + c)
      .join('')
    return `#${expanded.toUpperCase()}`
  }
  return null
}

export function createCustomSeedPreset(hexInput: string): SeedPreset {
  const hex = normalizeHex(hexInput) ?? '#E11D24'
  const lum = relativeLuminance(hex)
  const accent = lum < 0.25 ? blendHex(hex, '#FFFFFF', 0.22) : hex
  const accentLight = lum > 0.35 ? blendHex(hex, '#000000', 0.42) : hex
  return {
    id: 'custom',
    nameEn: hex,
    nameTr: hex,
    primary: hex,
    accent,
    accentLight,
    tone1Dark: blendHex('#000000', hex, 0.08),
    tone2Dark: blendHex('#000000', hex, 0.14),
    tone1Light: blendHex('#FFFFFF', hex, 0.05),
    onPrimary: lum > 0.45 ? '#111827' : '#FFFFFF',
    isCustom: true,
  }
}

/**
 * Curated seed palette matching ColorPickerPreference.kt & app/src/main/res/values/colors.xml,
 * keeping V4War Crimson first as brand showcase alongside Verdant Mint (app default).
 */
export const SEED_PRESETS: SeedPreset[] = [
  {
    id: 'crimson',
    nameEn: 'V4War Crimson',
    nameTr: 'V4War Kızılı',
    primary: '#E11D24',
    accent: '#FF454B',
    accentLight: '#B91C1C',
    tone1Dark: '#150507',
    tone2Dark: '#240B10',
    tone1Light: '#FFF8F8',
    onPrimary: '#FFFFFF',
  },
  {
    id: 'material-you',
    nameEn: 'Material You',
    nameTr: 'Material You',
    primary: '#3664E8',
    accent: '#6B8EFF',
    accentLight: '#244AC2',
    tone1Dark: '#060A18',
    tone2Dark: '#101830',
    tone1Light: '#F4F7FE',
    onPrimary: '#FFFFFF',
    isMonet: true,
  },
  {
    id: 'verdant-mint',
    nameEn: 'Verdant Mint',
    nameTr: 'Nane Yeşili',
    primary: '#00B96B',
    accent: '#00D685',
    accentLight: '#007A47',
    tone1Dark: '#04140D',
    tone2Dark: '#0B2418',
    tone1Light: '#F4FBF7',
    onPrimary: '#04140D',
    isAppDefault: true,
  },
  {
    id: 'blue',
    nameEn: 'Classic Blue',
    nameTr: 'Klasik Mavi',
    primary: '#2196F3',
    accent: '#448AFF',
    accentLight: '#1565C0',
    tone1Dark: '#050F1E',
    tone2Dark: '#0D1C34',
    tone1Light: '#F4F8FD',
    onPrimary: '#FFFFFF',
  },
  {
    id: 'indigo',
    nameEn: 'Indigo',
    nameTr: 'İndigo',
    primary: '#3F51B5',
    accent: '#536DFE',
    accentLight: '#303F9F',
    tone1Dark: '#07091C',
    tone2Dark: '#121634',
    tone1Light: '#F4F5FB',
    onPrimary: '#FFFFFF',
  },
  {
    id: 'light-blue',
    nameEn: 'Sky Blue',
    nameTr: 'Gök Mavisi',
    primary: '#03A9F4',
    accent: '#40C4FF',
    accentLight: '#0277BD',
    tone1Dark: '#04121E',
    tone2Dark: '#0B1F34',
    tone1Light: '#F3F8FB',
    onPrimary: '#04121E',
  },
  {
    id: 'cyan',
    nameEn: 'Cyan',
    nameTr: 'Turkuaz',
    primary: '#00BCD4',
    accent: '#18FFFF',
    accentLight: '#007C8F',
    tone1Dark: '#031317',
    tone2Dark: '#082128',
    tone1Light: '#F3F9FA',
    onPrimary: '#031317',
  },
  {
    id: 'teal',
    nameEn: 'Teal',
    nameTr: 'Su Yeşili',
    primary: '#009688',
    accent: '#64FFDA',
    accentLight: '#00695C',
    tone1Dark: '#031414',
    tone2Dark: '#082324',
    tone1Light: '#F3F9F9',
    onPrimary: '#FFFFFF',
  },
  {
    id: 'green',
    nameEn: 'Forest Green',
    nameTr: 'Orman Yeşili',
    primary: '#4CAF50',
    accent: '#69F0AE',
    accentLight: '#2E7D32',
    tone1Dark: '#051406',
    tone2Dark: '#0D2511',
    tone1Light: '#F4FAF4',
    onPrimary: '#051406',
  },
  {
    id: 'light-green',
    nameEn: 'Light Green',
    nameTr: 'Açık Yeşil',
    primary: '#8BC34A',
    accent: '#B2FF59',
    accentLight: '#558B2F',
    tone1Dark: '#061505',
    tone2Dark: '#10260E',
    tone1Light: '#F5FAF4',
    onPrimary: '#061505',
  },
  {
    id: 'lime',
    nameEn: 'Lime',
    nameTr: 'Limon Yeşili',
    primary: '#CDDC39',
    accent: '#EEFF41',
    accentLight: '#7E8700',
    tone1Dark: '#0B1503',
    tone2Dark: '#172608',
    tone1Light: '#F7FAF3',
    onPrimary: '#0B1503',
  },
  {
    id: 'yellow',
    nameEn: 'Yellow',
    nameTr: 'Sarı',
    primary: '#FFEB3B',
    accent: '#FFFF00',
    accentLight: '#8E7900',
    tone1Dark: '#151203',
    tone2Dark: '#252007',
    tone1Light: '#FCF9F2',
    onPrimary: '#151203',
  },
  {
    id: 'amber',
    nameEn: 'Amber',
    nameTr: 'Kehribar',
    primary: '#FFC107',
    accent: '#FFD740',
    accentLight: '#946300',
    tone1Dark: '#151003',
    tone2Dark: '#251B07',
    tone1Light: '#FCF9F2',
    onPrimary: '#151003',
  },
  {
    id: 'orange',
    nameEn: 'Vivid Orange',
    nameTr: 'Canlı Turuncu',
    primary: '#FF9800',
    accent: '#FFAB40',
    accentLight: '#C66900',
    tone1Dark: '#160B04',
    tone2Dark: '#261408',
    tone1Light: '#FDF7F3',
    onPrimary: '#160B04',
  },
  {
    id: 'deep-orange',
    nameEn: 'Coral Orange',
    nameTr: 'Mercan',
    primary: '#FF5722',
    accent: '#FF6E40',
    accentLight: '#BF360C',
    tone1Dark: '#180803',
    tone2Dark: '#281006',
    tone1Light: '#FDF6F3',
    onPrimary: '#FFFFFF',
  },
  {
    id: 'red',
    nameEn: 'Flame Red',
    nameTr: 'Alev Kırmızısı',
    primary: '#F44336',
    accent: '#FF5252',
    accentLight: '#C62828',
    tone1Dark: '#150507',
    tone2Dark: '#240B10',
    tone1Light: '#FFF8F8',
    onPrimary: '#FFFFFF',
  },
  {
    id: 'pink-ssr',
    nameEn: 'Sakura Pink',
    nameTr: 'Kiraz Çiçeği',
    primary: '#FB7299',
    accent: '#FF8FB0',
    accentLight: '#B8335B',
    tone1Dark: '#170512',
    tone2Dark: '#290C20',
    tone1Light: '#FDF5F8',
    onPrimary: '#170512',
  },
  {
    id: 'pink',
    nameEn: 'Neon Pink',
    nameTr: 'Neon Pembe',
    primary: '#E91E63',
    accent: '#FF4081',
    accentLight: '#AD1457',
    tone1Dark: '#160510',
    tone2Dark: '#270B1D',
    tone1Light: '#FDF5F8',
    onPrimary: '#FFFFFF',
  },
  {
    id: 'purple',
    nameEn: 'Purple',
    nameTr: 'Lavanta Moru',
    primary: '#9C27B0',
    accent: '#E040FB',
    accentLight: '#7B1FA2',
    tone1Dark: '#11061C',
    tone2Dark: '#200E32',
    tone1Light: '#F8F5FD',
    onPrimary: '#FFFFFF',
  },
  {
    id: 'deep-purple',
    nameEn: 'Deep Purple',
    nameTr: 'Koyu Mor',
    primary: '#673AB7',
    accent: '#7C4DFF',
    accentLight: '#512DA8',
    tone1Dark: '#0E0519',
    tone2Dark: '#1A0B2C',
    tone1Light: '#F7F4FD',
    onPrimary: '#FFFFFF',
  },
  {
    id: 'brown',
    nameEn: 'Mocha Brown',
    nameTr: 'Mocha Kahve',
    primary: '#795548',
    accent: '#A1887F',
    accentLight: '#5D4037',
    tone1Dark: '#140B07',
    tone2Dark: '#24160E',
    tone1Light: '#FAF7F5',
    onPrimary: '#FFFFFF',
  },
  {
    id: 'blue-grey',
    nameEn: 'Blue Grey',
    nameTr: 'Mavi Gri',
    primary: '#607D8B',
    accent: '#90A4AE',
    accentLight: '#455A64',
    tone1Dark: '#0A1116',
    tone2Dark: '#142028',
    tone1Light: '#F5F7F8',
    onPrimary: '#FFFFFF',
  },
  {
    id: 'grey',
    nameEn: 'Graphite Grey',
    nameTr: 'Antrasit Gri',
    primary: '#9E9E9E',
    accent: '#BDBDBD',
    accentLight: '#616161',
    tone1Dark: '#121212',
    tone2Dark: '#1E1E1E',
    tone1Light: '#F5F5F7',
    onPrimary: '#121212',
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
  {
    id: 'system',
    labelEn: 'System',
    labelTr: 'Sistem',
    hintEn: 'Follows OS day / night',
    hintTr: 'Cihaz temasını izler',
  },
]

export interface ResolvedThemeTokens {
  effectiveSurface: Exclude<SurfaceMode, 'system'>
  monochrome: MonochromeMode
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
  shadowGlow: string
  shadowCard: string
  shadowGlass: string
  seedPrimary: string
  seedAccent: string
  seedOnPrimary: string
  tone1: string
  tone2: string
  tone3: string
  tone1Dark: string
  tone2Dark: string
}

export function resolveSystemPrefersDark(): boolean {
  if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches
  }
  return true
}

/**
 * Mirrors V4War Theme.kt (resolveCardBackground, resolveNestedBoxBackground, resolveCardStrokeColor)
 * including Monochrome Themes (Black / White / Light Gray) and System mode.
 */
export function resolveThemeTokens(
  surface: SurfaceMode,
  seed: SeedPreset,
  monochrome: MonochromeMode = 'none',
  systemPrefersDark: boolean = resolveSystemPrefersDark(),
): ResolvedThemeTokens {
  if (monochrome === 'black') {
    return {
      effectiveSurface: 'pure-amoled',
      monochrome,
      canvas: '#000000',
      surface: '#000000',
      surfaceElevated: '#0A0A0A',
      nested: '#141414',
      glass: 'rgba(0, 0, 0, 0.86)',
      pill: 'rgba(255, 255, 255, 0.08)',
      textPrimary: '#FFFFFF',
      textSecondary: '#CAC4D0',
      textMuted: '#8E8A96',
      borderSubtle: 'rgba(255, 255, 255, 0.22)',
      borderMuted: 'rgba(255, 255, 255, 0.14)',
      borderStrong: 'rgba(255, 255, 255, 0.85)',
      shadowGlow: '0 0 60px rgba(255, 255, 255, 0.08)',
      shadowCard: '0 10px 34px rgba(0, 0, 0, 0.65)',
      shadowGlass: '0 12px 32px rgba(0, 0, 0, 0.55)',
      seedPrimary: '#FFFFFF',
      seedAccent: '#FFFFFF',
      seedOnPrimary: '#000000',
      tone1: '#000000',
      tone2: '#000000',
      tone3: '#141414',
      tone1Dark: '#000000',
      tone2Dark: '#141414',
    }
  }

  if (monochrome === 'white') {
    return {
      effectiveSurface: 'light',
      monochrome,
      canvas: '#FFFFFF',
      surface: '#FFFFFF',
      surfaceElevated: '#FAFAFA',
      nested: '#F5F5F5',
      glass: 'rgba(255, 255, 255, 0.92)',
      pill: 'rgba(0, 0, 0, 0.05)',
      textPrimary: '#212121',
      textSecondary: '#424242',
      textMuted: '#757575',
      borderSubtle: 'rgba(33, 33, 33, 0.18)',
      borderMuted: 'rgba(0, 0, 0, 0.12)',
      borderStrong: 'rgba(33, 33, 33, 0.65)',
      shadowGlow: '0 14px 40px rgba(0, 0, 0, 0.06)',
      shadowCard: '0 8px 24px rgba(15, 23, 42, 0.07)',
      shadowGlass: '0 8px 24px rgba(15, 23, 42, 0.08)',
      seedPrimary: '#212121',
      seedAccent: '#2563EB',
      seedOnPrimary: '#FFFFFF',
      tone1: '#FFFFFF',
      tone2: '#FFFFFF',
      tone3: '#F5F5F5',
      tone1Dark: seed.tone1Dark,
      tone2Dark: seed.tone2Dark,
    }
  }

  if (monochrome === 'light-gray') {
    return {
      effectiveSurface: 'light',
      monochrome,
      canvas: '#F5F5F7',
      surface: '#FFFFFF',
      surfaceElevated: '#FFFFFF',
      nested: '#ECEEF2',
      glass: 'rgba(245, 245, 247, 0.90)',
      pill: 'rgba(31, 41, 55, 0.06)',
      textPrimary: '#1F2937',
      textSecondary: '#4B5563',
      textMuted: '#6B7280',
      borderSubtle: 'rgba(71, 85, 105, 0.24)',
      borderMuted: 'rgba(31, 41, 55, 0.14)',
      borderStrong: 'rgba(31, 41, 55, 0.65)',
      shadowGlow: '0 14px 40px rgba(31, 41, 55, 0.08)',
      shadowCard: '0 8px 24px rgba(15, 23, 42, 0.07)',
      shadowGlass: '0 8px 24px rgba(15, 23, 42, 0.08)',
      seedPrimary: '#1F2937',
      seedAccent: '#475569',
      seedOnPrimary: '#FFFFFF',
      tone1: '#F5F5F7',
      tone2: '#FFFFFF',
      tone3: '#ECEEF2',
      tone1Dark: seed.tone1Dark,
      tone2Dark: seed.tone2Dark,
    }
  }

  const effectiveSurface: Exclude<SurfaceMode, 'system'> =
    surface === 'system' ? (systemPrefersDark ? 'dark' : 'light') : surface

  if (effectiveSurface === 'light') {
    const tone1 = seed.tone1Light
    const tone2 = '#FFFFFF'
    const tone3 = blendHex('#FFFFFF', seed.primary, 0.08)
    return {
      effectiveSurface,
      monochrome: 'none',
      canvas: tone1,
      surface: tone2,
      surfaceElevated: '#FFFFFF',
      nested: tone3,
      glass: 'rgba(255, 255, 255, 0.88)',
      pill: 'rgba(28, 27, 31, 0.05)',
      textPrimary: '#1C1B1F',
      textSecondary: '#49454F',
      textMuted: '#6E6973',
      borderSubtle: rgba(seed.accentLight, 0.30),
      borderMuted: 'rgba(28, 27, 31, 0.14)',
      borderStrong: rgba(seed.accentLight, 0.72),
      shadowGlow: `0 18px 54px ${rgba(seed.accentLight, 0.14)}`,
      shadowCard: '0 10px 28px rgba(15, 23, 42, 0.08)',
      shadowGlass: '0 10px 28px rgba(15, 23, 42, 0.10)',
      seedPrimary: seed.primary,
      seedAccent: seed.accentLight,
      seedOnPrimary: seed.onPrimary,
      tone1,
      tone2,
      tone3,
      tone1Dark: seed.tone1Dark,
      tone2Dark: seed.tone2Dark,
    }
  }

  if (effectiveSurface === 'dark') {
    const tone1 = seed.tone1Dark
    const tone2 = seed.tone2Dark
    const tone3 = blendHex(tone2, seed.primary, 0.12)
    return {
      effectiveSurface,
      monochrome: 'none',
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
      shadowGlow: `0 0 80px ${rgba(seed.primary, 0.22)}`,
      shadowCard: '0 12px 34px rgba(0, 0, 0, 0.45)',
      shadowGlass: '0 12px 32px rgba(0, 0, 0, 0.42)',
      seedPrimary: seed.primary,
      seedAccent: seed.accent,
      seedOnPrimary: seed.onPrimary,
      tone1,
      tone2,
      tone3,
      tone1Dark: seed.tone1Dark,
      tone2Dark: seed.tone2Dark,
    }
  }

  if (effectiveSurface === 'amoled') {
    const tone1 = '#000000'
    const tone2 = blendHex('#000000', seed.primary, 0.11)
    const tone3 = blendHex(tone2, seed.primary, 0.12)
    return {
      effectiveSurface,
      monochrome: 'none',
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
      shadowGlow: `0 0 80px ${rgba(seed.primary, 0.22)}`,
      shadowCard: '0 10px 34px rgba(0, 0, 0, 0.55)',
      shadowGlass: '0 12px 32px rgba(0, 0, 0, 0.48)',
      seedPrimary: seed.primary,
      seedAccent: seed.accent,
      seedOnPrimary: seed.onPrimary,
      tone1,
      tone2,
      tone3,
      tone1Dark: seed.tone1Dark,
      tone2Dark: seed.tone2Dark,
    }
  }

  // pure-amoled
  const tone1 = '#000000'
  const tone2 = '#000000'
  const tone3 = blendHex('#000000', seed.primary, 0.08)
  return {
    effectiveSurface: 'pure-amoled',
    monochrome: 'none',
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
    shadowGlow: `0 0 80px ${rgba(seed.primary, 0.22)}`,
    shadowCard: '0 10px 34px rgba(0, 0, 0, 0.55)',
    shadowGlass: '0 12px 32px rgba(0, 0, 0, 0.48)',
    seedPrimary: seed.primary,
    seedAccent: seed.accent,
    seedOnPrimary: seed.onPrimary,
    tone1,
    tone2,
    tone3,
    tone1Dark: seed.tone1Dark,
    tone2Dark: seed.tone2Dark,
  }
}

export function applyThemeToDocument(
  surface: SurfaceMode,
  seed: SeedPreset,
  monochrome: MonochromeMode = 'none',
): ResolvedThemeTokens {
  const tokens = resolveThemeTokens(surface, seed, monochrome)
  if (typeof document !== 'undefined') {
    const root = document.documentElement
    root.dataset.surface = tokens.effectiveSurface
    root.dataset.surfaceSelection = surface
    root.dataset.monochrome = monochrome
    root.style.setProperty('--seed-primary', tokens.seedPrimary)
    root.style.setProperty('--seed-accent', tokens.seedAccent)
    root.style.setProperty('--seed-tone-1', tokens.tone1)
    root.style.setProperty('--seed-tone-2', tokens.tone2)
    root.style.setProperty('--seed-tone-1-dark', tokens.tone1Dark)
    root.style.setProperty('--seed-tone-2-dark', tokens.tone2Dark)
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

    root.style.setProperty('--shadow-glow', tokens.shadowGlow)
    root.style.setProperty('--shadow-card', tokens.shadowCard)
    root.style.setProperty('--shadow-glass', tokens.shadowGlass)

    const metaTheme = document.querySelector('meta[name="theme-color"]')
    if (metaTheme) {
      metaTheme.setAttribute('content', tokens.canvas)
    }
  }
  return tokens
}
