import { describe, expect, it, vi } from 'vitest'
import {
  detectAbiFromFilename,
  FALLBACK_RELEASE,
  fetchLatestRelease,
  formatBytes,
  parseGitHubRelease,
} from '../utils/releases'

describe('releases utilities', () => {
  it('formats byte sizes cleanly and handles invalid numbers', () => {
    expect(formatBytes(47279623)).toBe('45.1 MB')
    expect(formatBytes(0)).toBe('—')
    expect(formatBytes(-10)).toBe('—')
    expect(formatBytes(Number.NaN)).toBe('—')
  })

  it('detects supported Android ABIs from APK filenames only', () => {
    expect(detectAbiFromFilename('V4War-2.1.3-arm64-v8a.apk')).toBe('arm64-v8a')
    expect(detectAbiFromFilename('V4War-2.1.3-armeabi-v7a.apk')).toBe('armeabi-v7a')
    expect(detectAbiFromFilename('V4War-2.1.3-x86_64.apk')).toBe('x86_64')
    expect(detectAbiFromFilename('V4War-2.1.3-universal.apk')).toBe('universal')
    expect(detectAbiFromFilename('v4war-update.json')).toBeNull()
    expect(detectAbiFromFilename('SHA256SUMS')).toBeNull()
  })

  it('parses and sorts GitHub release assets while extracting SHA256SUMS', () => {
    const parsed = parseGitHubRelease({
      tag_name: 'v2.2.0',
      published_at: '2026-12-01T10:00:00Z',
      html_url: 'https://github.com/theyusa/V4War/releases/tag/v2.2.0',
      assets: [
        {
          name: 'V4War-2.2.0-x86_64.apk',
          size: 50000000,
          browser_download_url: 'https://example.com/x86_64.apk',
        },
        {
          name: 'v4war-update.json',
          size: 800,
          browser_download_url: 'https://example.com/update.json',
        },
        {
          name: 'SHA256SUMS',
          size: 275,
          browser_download_url: 'https://example.com/SHA256SUMS',
        },
        {
          name: 'V4War-2.2.0-arm64-v8a.apk',
          size: 48000000,
          browser_download_url: 'https://example.com/arm64.apk',
        },
      ],
    })

    expect(parsed.isFallback).toBe(false)
    expect(parsed.tagName).toBe('v2.2.0')
    expect(parsed.publishedAt).toBe('2026-12-01')
    expect(parsed.sha256SumsUrl).toBe('https://example.com/SHA256SUMS')
    expect(parsed.apks.map((a) => a.abi)).toEqual(['arm64-v8a', 'x86_64'])
    expect(parsed.apks[0].recommended).toBe(true)
  })

  it('falls back safely on missing or empty release payloads', () => {
    expect(parseGitHubRelease(null)).toEqual(FALLBACK_RELEASE)
    expect(parseGitHubRelease({ tag_name: 'v2.2.0', assets: [] })).toEqual(FALLBACK_RELEASE)
  })

  it('fetches live release or returns fallback when fetch fails or returns non-200', async () => {
    const badFetch = vi.fn().mockResolvedValue({ ok: false })
    expect(await fetchLatestRelease(badFetch as unknown as typeof fetch)).toEqual(FALLBACK_RELEASE)

    const throwingFetch = vi.fn().mockRejectedValue(new Error('network down'))
    expect(await fetchLatestRelease(throwingFetch as unknown as typeof fetch)).toEqual(FALLBACK_RELEASE)

    const goodFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        tag_name: 'v2.1.4',
        published_at: '2026-11-20T00:00:00Z',
        assets: [
          {
            name: 'V4War-2.1.4-arm64-v8a.apk',
            size: 47000000,
            browser_download_url: 'https://example.com/V4War-2.1.4-arm64-v8a.apk',
          },
        ],
      }),
    })
    const live = await fetchLatestRelease(goodFetch as unknown as typeof fetch)
    expect(live.isFallback).toBe(false)
    expect(live.tagName).toBe('v2.1.4')
  })
})
