export interface ApkDownloadItem {
  abi: 'arm64-v8a' | 'armeabi-v7a' | 'x86_64' | 'universal'
  fileName: string
  sizeBytes: number
  sizeLabel: string
  downloadUrl: string
  recommended?: boolean
}

export interface ReleaseInfo {
  tagName: string
  publishedAt: string
  htmlUrl: string
  sha256SumsUrl: string | null
  apks: ApkDownloadItem[]
  isFallback: boolean
}

export interface GitHubAssetPayload {
  name: string
  size: number
  browser_download_url: string
}

export interface GitHubReleasePayload {
  tag_name?: string
  published_at?: string
  html_url?: string
  assets?: GitHubAssetPayload[]
}

export const FALLBACK_RELEASE: ReleaseInfo = {
  tagName: 'v2.1.3',
  publishedAt: '2026-11-15',
  htmlUrl: 'https://github.com/theyusa/V4War/releases/tag/v2.1.3',
  sha256SumsUrl: 'https://github.com/theyusa/V4War/releases/download/v2.1.3/SHA256SUMS',
  isFallback: true,
  apks: [
    {
      abi: 'arm64-v8a',
      fileName: 'V4War-2.1.3-arm64-v8a.apk',
      sizeBytes: 47279623,
      sizeLabel: '45.1 MB',
      downloadUrl: 'https://github.com/theyusa/V4War/releases/download/v2.1.3/V4War-2.1.3-arm64-v8a.apk',
      recommended: true,
    },
    {
      abi: 'armeabi-v7a',
      fileName: 'V4War-2.1.3-armeabi-v7a.apk',
      sizeBytes: 47512239,
      sizeLabel: '45.3 MB',
      downloadUrl: 'https://github.com/theyusa/V4War/releases/download/v2.1.3/V4War-2.1.3-armeabi-v7a.apk',
    },
    {
      abi: 'x86_64',
      fileName: 'V4War-2.1.3-x86_64.apk',
      sizeBytes: 49598421,
      sizeLabel: '47.3 MB',
      downloadUrl: 'https://github.com/theyusa/V4War/releases/download/v2.1.3/V4War-2.1.3-x86_64.apk',
    },
  ],
}

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '—'
  const mb = bytes / (1024 * 1024)
  return `${mb.toFixed(1)} MB`
}

export function detectAbiFromFilename(fileName: string): ApkDownloadItem['abi'] | null {
  if (!fileName.toLowerCase().endsWith('.apk')) return null
  if (fileName.includes('arm64-v8a')) return 'arm64-v8a'
  if (fileName.includes('armeabi-v7a')) return 'armeabi-v7a'
  if (fileName.includes('x86_64')) return 'x86_64'
  if (fileName.includes('universal')) return 'universal'
  return null
}

const ABI_ORDER: Record<ApkDownloadItem['abi'], number> = {
  'arm64-v8a': 0,
  'armeabi-v7a': 1,
  'x86_64': 2,
  universal: 3,
}

export function parseGitHubRelease(payload: GitHubReleasePayload | null | undefined): ReleaseInfo {
  if (!payload || !payload.tag_name || !Array.isArray(payload.assets)) {
    return FALLBACK_RELEASE
  }

  const apks: ApkDownloadItem[] = []
  let sha256SumsUrl: string | null = null

  for (const asset of payload.assets) {
    if (!asset?.name || !asset?.browser_download_url) continue
    if (asset.name === 'SHA256SUMS') {
      sha256SumsUrl = asset.browser_download_url
      continue
    }
    const abi = detectAbiFromFilename(asset.name)
    if (!abi) continue
    apks.push({
      abi,
      fileName: asset.name,
      sizeBytes: Number(asset.size) || 0,
      sizeLabel: formatBytes(Number(asset.size) || 0),
      downloadUrl: asset.browser_download_url,
      recommended: abi === 'arm64-v8a',
    })
  }

  if (apks.length === 0) {
    return FALLBACK_RELEASE
  }

  apks.sort((a, b) => ABI_ORDER[a.abi] - ABI_ORDER[b.abi])

  return {
    tagName: payload.tag_name,
    publishedAt: payload.published_at ? payload.published_at.slice(0, 10) : FALLBACK_RELEASE.publishedAt,
    htmlUrl: payload.html_url || `https://github.com/theyusa/V4War/releases/tag/${payload.tag_name}`,
    sha256SumsUrl,
    apks,
    isFallback: false,
  }
}

export async function fetchLatestRelease(
  fetchImpl: typeof fetch = globalThis.fetch,
): Promise<ReleaseInfo> {
  if (typeof fetchImpl !== 'function') {
    return FALLBACK_RELEASE
  }

  const controller = typeof AbortController !== 'undefined' ? new AbortController() : null
  const timer = controller ? setTimeout(() => controller.abort(), 5000) : null

  try {
    const response = await fetchImpl('https://api.github.com/repos/theyusa/V4War/releases/latest', {
      headers: { Accept: 'application/vnd.github+json' },
      signal: controller?.signal,
    })
    if (!response.ok) {
      return FALLBACK_RELEASE
    }
    const data = (await response.json()) as GitHubReleasePayload
    return parseGitHubRelease(data)
  } catch {
    return FALLBACK_RELEASE
  } finally {
    if (timer) clearTimeout(timer)
  }
}
