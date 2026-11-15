export type AppPageName = 'home' | 'docs' | 'theme-studio' | 'explore' | 'download'

export interface ParsedRoute {
  page: AppPageName
  docSlug: string
  hash: string
  canonicalPath: string
}

export function normalizeBasePath(baseUrl: string = import.meta.env.BASE_URL || '/'): string {
  if (!baseUrl || baseUrl === '/') return ''
  return baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl
}

export function parseAppLocation(
  pathname: string,
  hash: string = '',
  search: string = '',
  baseUrl: string = import.meta.env.BASE_URL || '/',
): ParsedRoute {
  // Support 404.html SPA redirect fallback (?p=/docs/...)
  if (search) {
    const params = new URLSearchParams(search)
    const redirected = params.get('p')
    if (redirected) {
      return parseAppLocation(redirected, hash, '', baseUrl)
    }
  }

  // Support hash route fallback (#/docs/...)
  if (hash.startsWith('#/')) {
    const [hashPath, subHash] = hash.slice(1).split('#')
    return parseAppLocation(hashPath, subHash ? `#${subHash}` : '', '', '')
  }

  const base = normalizeBasePath(baseUrl)
  let rel = pathname || '/'
  if (base && (rel === base || rel.startsWith(`${base}/`))) {
    rel = rel.slice(base.length) || '/'
  } else if (!base && (rel === '/V4War' || rel.startsWith('/V4War/'))) {
    rel = rel.slice('/V4War'.length) || '/'
  }
  if (!rel.startsWith('/')) rel = `/${rel}`
  rel = rel.replace(/\/+$/, '') || '/'

  if (rel === '/' || rel === '/index.html') {
    return { page: 'home', docSlug: 'overview', hash, canonicalPath: '/' }
  }

  if (rel === '/docs') {
    return { page: 'docs', docSlug: 'overview', hash, canonicalPath: '/docs/overview' }
  }

  if (rel.startsWith('/docs/')) {
    const slug = rel.slice('/docs/'.length).split('/')[0] || 'overview'
    return { page: 'docs', docSlug: slug, hash, canonicalPath: `/docs/${slug}` }
  }

  if (rel === '/theme-studio') {
    return { page: 'theme-studio', docSlug: 'overview', hash, canonicalPath: '/theme-studio' }
  }

  if (rel === '/explore' || rel === '/features' || rel === '/protocols' || rel === '/toolbox') {
    return { page: 'explore', docSlug: 'overview', hash, canonicalPath: '/explore' }
  }

  if (rel === '/download' || rel === '/downloads') {
    return { page: 'download', docSlug: 'overview', hash, canonicalPath: '/download' }
  }

  return { page: 'home', docSlug: 'overview', hash, canonicalPath: '/' }
}

export function buildHref(
  routePath: string,
  baseUrl: string = import.meta.env.BASE_URL || '/',
): string {
  const base = normalizeBasePath(baseUrl)
  const clean = routePath.startsWith('/') ? routePath : `/${routePath}`
  return `${base}${clean}` || '/'
}
