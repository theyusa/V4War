export type Locale = 'en' | 'tr'

export interface HotspotBackend {
  id: 'auto' | 'shizuku' | 'root' | 'lan'
  name: Record<Locale, string>
  privilege: Record<Locale, string>
  architecture: string
  summary: Record<Locale, string>
  highlights: Record<Locale, string[]>
}

export interface ToolboxItem {
  id: string
  title: Record<Locale, string>
  badge: string
  summary: Record<Locale, string>
  specs: Record<Locale, string[]>
  sampleOutput: {
    label: string
    lines: string[]
  }
}

export interface ProtocolItem {
  name: string
  category: 'proxy' | 'quic' | 'tunnel' | 'dns' | 'smart'
  badge?: string
}

export interface FaqItem {
  q: Record<Locale, string>
  a: Record<Locale, string>
}

export interface ScreenshotShowcaseItem {
  id: string
  file: string
  title: Record<Locale, string>
  caption: Record<Locale, string>
  docRoute: string
}

export const SCREENSHOT_SHOWCASE: ScreenshotShowcaseItem[] = [
  {
    id: 'home',
    file: 'screenshots/home-screen.webp',
    title: { en: 'Compact Bento Home', tr: 'Kompakt Bento Ana Ekran' },
    caption: {
      en: 'Live speed, CPU/RAM/Connections charts, IP privacy toggle, quick diagnostics & status dock.',
      tr: 'Canlı hız, CPU/RAM/Bağlantı grafikleri, IP gizleme, hızlı tanı testleri ve durum kartı.',
    },
    docRoute: '/docs/overview',
  },
  {
    id: 'appearance',
    file: 'screenshots/appearance-theme-studio.webp',
    title: { en: 'Theme Studio (M3 Expressive)', tr: 'Theme Studio (M3 Expressive)' },
    caption: {
      en: 'Live Preview card, Surface Styles, Monochrome Themes, Custom Palettes & Material Original.',
      tr: 'Canlı Önizleme kartı, Yüzey Stilleri, Monokrom Temalar, Özel Paletler ve Material Original.',
    },
    docRoute: '/docs/appearance-theme-studio',
  },
  {
    id: 'tools',
    file: 'screenshots/tools-network.webp',
    title: { en: 'Network & Tools Suite', tr: 'Ağ ve Araçlar Paketi' },
    caption: {
      en: 'Cellular Radio, Interfaces, IP Lookup, DNS Lookup, Local Ping, Connectivity Test & Traffic.',
      tr: 'Hücresel Radyo, Arayüzler, IP & DNS Sorgulama, Yerel Ping, Bağlantı Testi ve Trafik Analizi.',
    },
    docRoute: '/docs/network-toolbox',
  },
  {
    id: 'hotspot',
    file: 'screenshots/hotspot-share.webp',
    title: { en: 'Hotspot Share (Shizuku TUN)', tr: 'Hotspot Paylaşım (Shizuku TUN)' },
    caption: {
      en: 'Rootless UID 2000 transparent TUN over Wi-Fi, USB RNDIS & Bluetooth with Fail-Closed guard.',
      tr: 'Wi-Fi, USB RNDIS ve Bluetooth üzerinde rootsuz UID 2000 şeffaf TUN ve sızıntı koruması.',
    },
    docRoute: '/docs/hotspot-share',
  },
  {
    id: 'local-proxy',
    file: 'screenshots/hotspot-local-proxy.webp',
    title: { en: 'LAN Proxy & Auto Connect', tr: 'LAN Proxy ve Otomatik Bağlan' },
    caption: {
      en: 'Interactive LAN Proxy Start/Stop, Port & Auth, zero-config Desktop Auto Connect & Share Info.',
      tr: 'Etkileşimli LAN Proxy Başlat/Durdur, Port ve Kimlik Doğrulama, Masaüstü Oto Bağlan ve Paylaşım.',
    },
    docRoute: '/docs/local-proxy-desktop',
  },
]

export const SITE_COPY = {
  nav: {
    home: { en: 'Home', tr: 'Ana Sayfa' },
    docs: { en: 'Docs', tr: 'Dokümanlar' },
    studio: { en: 'Theme Studio', tr: 'Theme Studio' },
    explore: { en: 'Explore Lab', tr: 'Özellik Lab' },
    hotspot: { en: 'Hotspot Share', tr: 'Hotspot Paylaşım' },
    toolbox: { en: 'Network Toolbox', tr: 'Ağ Araçları' },
    protocols: { en: 'Protocols', tr: 'Protokoller' },
    download: { en: 'Download', tr: 'İndir' },
  },
  hero: {
    eyebrow: {
      en: 'Sing-box & Xray Core · Open Source GPL-3.0',
      tr: 'Sing-box & Xray Çekirdeği · Açık Kaynak GPL-3.0',
    },
    titlePrefix: {
      en: 'Next-Gen Android',
      tr: 'Yeni Nesil Android',
    },
    titleHighlight: {
      en: 'VPN & Proxy Engine.',
      tr: 'VPN ve Proxy Motoru.',
    },
    description: {
      en: 'Built upon unified Sing-box & Xray cores with a rootless Shizuku TUN Hotspot Share suite, a 10-in-1 Network Diagnostics Toolbox, Material 3 Expressive Pure AMOLED UI, and hardened TLS fragmentation out of the box.',
      tr: 'Birleşik Sing-box ve Xray çekirdekleri üzerinde; rootsuz Shizuku TUN Hotspot paylaşım paketi, 10’u 1 Arada Ağ Tanılama Araç Kutusu, Material 3 Expressive Saf AMOLED arayüzü ve varsayılan TLS kayıt parçalama ile güçlendirildi.',
    },
    ctaPrimary: {
      en: 'Download APK',
      tr: 'APK İndir',
    },
    ctaDocs: {
      en: 'Read Docs',
      tr: 'Dokümanları Oku',
    },
    ctaSecondary: {
      en: 'Open Theme Studio',
      tr: 'Theme Studio Aç',
    },
    ctaGithub: {
      en: 'GitHub',
      tr: 'GitHub',
    },
    metrics: [
      { value: '28+', label: { en: 'Protocols & Transports', tr: 'Protokol ve Aktarım' } },
      { value: '10-in-1', label: { en: 'Diagnostic Toolbox', tr: 'Ağ Tanılama Aracı' } },
      { value: 'UID 2000', label: { en: 'Rootless Shizuku TUN', tr: 'Rootsuz Shizuku TUN' } },
      { value: 'API 24–35', label: { en: 'Android 7.0–15 + TV', tr: 'Android 7.0–15 + TV' } },
    ],
    phoneTabLive: { en: 'Live App UI', tr: 'Canlı Arayüz' },
    phoneTabShot: { en: 'Real Screens', tr: 'Gerçek Ekranlar' },
    phoneMockup: {
      coreSubtitle: { en: 'Sing-box Core · TLS Fragment ON', tr: 'Sing-box Çekirdeği · TLS Fragment AÇIK' },
      connectedLabel: { en: 'Connected', tr: 'Bağlı' },
      hotspotTitle: { en: 'Hotspot Share · Shizuku', tr: 'Hotspot Paylaşım · Shizuku' },
      hotspotSubtitle: { en: 'Wi-Fi + USB RNDIS · 3 clients', tr: 'Wi-Fi + USB RNDIS · 3 istemci' },
      scrollHint: { en: 'Real V4War Pure AMOLED Screens · Metadata Stripped', tr: 'Gerçek V4War Saf AMOLED Ekranları · EXIF Temiz' },
    },
  },
  pillars: {
    eyebrow: { en: 'Focused Architecture', tr: 'Odaklı Mimari' },
    title: {
      en: 'Everything Organized Into Dedicated Guides & Interactive Tools.',
      tr: 'Her Özellik Ayrı Rehberler ve Etkileşimli Araçlarla Düzenlendi.',
    },
    subtitle: {
      en: 'Instead of crowding everything onto one page, explore step-by-step Markdown documentation, test the real Theme Studio replica, or inspect live protocol and diagnostic simulators.',
      tr: 'Her şeyi tek bir sayfaya yığmak yerine adım adım Markdown dokümantasyonunu inceleyin, gerçek Theme Studio kopyasını deneyin veya protokol ve ağ araçları laboratuvarını keşfedin.',
    },
    cards: [
      {
        id: 'docs',
        badge: '12 GUIDES · EN / TR',
        title: { en: 'Step-by-Step Documentation', tr: 'Adım Adım Dokümantasyon' },
        desc: {
          en: 'Throne-style left sidebar with Quick Start, Shizuku TUN Hotspot guides, Network Toolbox manuals, Routing, and Troubleshooting.',
          tr: 'Sol kenar çubuklu, Hızlı Başlangıç, Shizuku TUN Hotspot rehberi, Ağ Araçları, Yönlendirme ve Sorun Giderme dokümanları.',
        },
        cta: { en: 'Browse Docs', tr: 'Dokümanlara Git' },
        route: '/docs/overview',
      },
      {
        id: 'studio',
        badge: '1:1 APP REPLICA',
        title: { en: 'Interactive Theme Studio', tr: 'Etkileşimli Theme Studio' },
        desc: {
          en: 'Test the exact V4War Appearance screen: Live Preview card, Pure AMOLED / AMOLED / Dark / Light / System, Monochrome Themes, and 22+ Seed Colors.',
          tr: 'Gerçek V4War Görünüm ekranını birebir test edin: Canlı Önizleme, Pure AMOLED / AMOLED / Koyu / Açık / Sistem, Monokrom Temalar ve 22+ Tohum Rengi.',
        },
        cta: { en: 'Launch Studio', tr: 'Studio’yu Aç' },
        route: '/theme-studio',
      },
      {
        id: 'explore',
        badge: '10 TOOLS · 28+ PROTOCOLS',
        title: { en: 'Hotspot, Toolbox & Protocols Lab', tr: 'Hotspot, Ağ Araçları ve Protokol Lab' },
        desc: {
          en: 'Compare Auto, Shizuku TUN, Root iptables & LAN Proxy backends, inspect all 10 diagnostic tools, and filter 28+ protocols.',
          tr: 'Auto, Shizuku TUN, Root iptables ve LAN Proxy motorlarını kıyaslayın, 10 ağ aracını ve 28+ protokolü filtreleyin.',
        },
        cta: { en: 'Open Explorer', tr: 'Laboratuvarı Aç' },
        route: '/explore',
      },
      {
        id: 'download',
        badge: 'SHA-256 · SHIZUKU UPDATER',
        title: { en: 'Verified APKs & Source Build', tr: 'Doğrulanmış APK ve Kaynak Derleme' },
        desc: {
          en: 'Grab arm64-v8a, armeabi-v7a, or x86_64 builds synced live from GitHub Releases with SHA256SUMS and Shizuku silent updates.',
          tr: 'GitHub Releases’tan canlı çekilen arm64-v8a, armeabi-v7a ve x86_64 paketlerini SHA256SUMS doğrulamasıyla indirin.',
        },
        cta: { en: 'Get V4War APK', tr: 'APK İndir' },
        route: '/download',
      },
    ],
  },
  gallery: {
    eyebrow: { en: 'Real App Screenshots', tr: 'Gerçek Uygulama Ekranları' },
    title: {
      en: 'Captured Directly From V4War Pure AMOLED.',
      tr: 'Doğrudan V4War Saf AMOLED Arayüzünden.',
    },
    subtitle: {
      en: 'Tap any screen to jump straight to its step-by-step guide in the documentation.',
      tr: 'İlgili adım adım rehbere gitmek için herhangi bir ekrana tıklayın.',
    },
    openGuide: { en: 'Read guide', tr: 'Rehberi oku' },
  },
  hotspot: {
    eyebrow: { en: 'Hotspot & Proxy Share Suite', tr: 'Hotspot ve Proxy Paylaşım Paketi' },
    title: {
      en: 'Share Your VPN Over Wi-Fi, USB & Bluetooth — Root Optional.',
      tr: 'VPN Bağlantınızı Wi-Fi, USB ve Bluetooth Üzerinden Paylaşın — Root Şart Değil.',
    },
    subtitle: {
      en: 'Turn your Android device into a transparent VPN gateway for laptops, phones, and consoles. Adaptive backend selection automatically picks Root, Shizuku TUN, or LAN Proxy.',
      tr: 'Android cihazınızı bilgisayarlar, telefonlar ve konsollar için şeffaf bir VPN ağ geçidine dönüştürün. Uyarlanabilir motor Root, Shizuku TUN veya LAN Proxy arasında otomatik geçiş yapar.',
    },
    channelsTitle: { en: 'Multi-Channel Tethering & Desktop Pairing', tr: 'Çoklu Kanal ve Masaüstü Eşleşmesi' },
    channels: [
      {
        title: { en: 'Wi-Fi, USB RNDIS & Bluetooth PAN', tr: 'Wi-Fi, USB RNDIS ve Bluetooth PAN' },
        desc: {
          en: 'Works across mobile hotspot, USB tethering with RNDIS reset/recovery hardening, and Bluetooth PAN.',
          tr: 'Mobil erişim noktası, RNDIS sıfırlama kurtarmalı USB tethering ve Bluetooth PAN üzerinden çalışır.',
        },
      },
      {
        title: { en: 'Quick Settings Tile & Live Monitor', tr: 'Hızlı Ayarlar Kutucuğu ve Canlı İzleme' },
        desc: {
          en: 'Toggle sharing via HotspotShareTileService and inspect live client throughput via TetherWatchService + Clash API.',
          tr: 'HotspotShareTileService ile bildirim panelinden tek dokunuşla açıp kapatın; bağlı istemcileri ve anlık trafiği görün.',
        },
      },
      {
        title: { en: 'Zero-Config Desktop Pairing (theyusa/Throne)', tr: 'Sıfır Ayarlı Masaüstü Eşleşmesi (theyusa/Throne)' },
        desc: {
          en: 'Connect your Windows/Linux PC to your phone hotspot and enable Hotspot Gateway — no IP or port typing needed.',
          tr: 'Windows/Linux bilgisayarınızda theyusa/Throne kurun, telefonun hotspotuna bağlanın ve Hotspot Ağ Geçidini açın — IP veya port yazmaya gerek yok.',
        },
      },
    ],
  },
  toolbox: {
    eyebrow: { en: '10-in-1 Network Toolbox', tr: '10’u 1 Arada Ağ Araç Kutusu' },
    title: {
      en: 'Built-in Diagnostic Engines Under Tools → Network.',
      tr: 'Araçlar → Ağ Altında On Yerleşik Tanılama Motoru.',
    },
    subtitle: {
      en: 'Verify routing integrity, detect DNS leaks, classify NAT behavior via RFC 5389/5780 STUN, and compare proxy vs direct physical egress in one Material 3 workspace.',
      tr: 'Yönlendirme bütünlüğünü doğrulayın, DNS sızıntılarını tespit edin, RFC 5389/5780 STUN ile NAT tipini ölçün ve proxy ile fiziksel çıkışı tek ekranda kıyaslayın.',
    },
  },
  studio: {
    eyebrow: { en: 'Material 3 Expressive & Theme Studio', tr: 'Material 3 Expressive ve Theme Studio' },
    title: {
      en: 'Appearance & Theme Studio — 1:1 Live Replica.',
      tr: 'Görünüm ve Theme Studio — Birebir Canlı Deneyim.',
    },
    subtitle: {
      en: 'Mirrors V4War’s Settings → Appearance screen card-for-card. Switch Surface Styles, Monochrome Themes, or pick from 22+ Seed Colors & Custom HEX — the entire site updates live.',
      tr: 'V4War’ın Ayarlar → Görünüm ekranını kart kart yansıtır. Yüzey Stilini, Monokrom Temaları veya 22+ Tohum Rengi ile Özel HEX seçimini değiştirin — tüm site anında güncellenir.',
    },
    livePreviewLabel: { en: 'Live Preview', tr: 'Canlı Önizleme' },
    surfaceLabel: { en: 'Surface Style', tr: 'Yüzey Stili' },
    monochromeLabel: { en: 'Monochrome Themes', tr: 'Monokrom Temalar' },
    themeSectionLabel: { en: 'Theme', tr: 'Tema' },
    seedLabel: { en: 'Seed Color', tr: 'Tohum Rengi' },
    customBtnLabel: { en: 'Custom', tr: 'Özel' },
    tonesLabel: { en: 'Live 3-Tone Output', tr: 'Canlı 3 Ton Çıktısı' },
    tone1Label: { en: 'Tone 1 (Canvas)', tr: '1. Ton (Tuval)' },
    tone2Label: { en: 'Tone 2 (Card)', tr: '2. Ton (Kart)' },
    tone3Label: { en: 'Tone 3 (Nested)', tr: '3. Ton (İç Kutu)' },
    compareShotLabel: { en: 'Compare with Real App Screen', tr: 'Gerçek Uygulama Ekranıyla Kıyasla' },
  },
  protocols: {
    eyebrow: { en: 'Protocols & Subscriptions', tr: 'Protokoller ve Abonelikler' },
    title: {
      en: '28+ Protocols & Smart Outbounds in One Unified Core.',
      tr: 'Tek Çekirdekte 28+ Protokol ve Akıllı Çıkışlar.',
    },
    subtitle: {
      en: 'Supports Sing-box JSON, Xray, standard share links, v2rayN, Shadowsocks, Clash outbound import, Happ routing import, custom subscription SNI, and deeplinks.',
      tr: 'Sing-box JSON, Xray, standart paylaşım linkleri, v2rayN, Shadowsocks, Clash çıkış içe aktarma, Happ yönlendirme içe aktarma, özel abonelik SNI ve deeplink desteği.',
    },
    searchPlaceholder: {
      en: 'Filter protocols (e.g. VLESS, Hysteria, DNSTT, WireGuard)...',
      tr: 'Protokol ara (ör. VLESS, Hysteria, DNSTT, WireGuard)...',
    },
    emptyTitle: {
      en: 'No matching protocols found',
      tr: 'Eşleşen protokol bulunamadı',
    },
    emptyReset: {
      en: 'Reset filters',
      tr: 'Filtreleri sıfırla',
    },
  },
  security: {
    eyebrow: { en: 'Core, Security & Smart Updates', tr: 'Çekirdek, Güvenlik ve Akıllı Güncellemeler' },
    title: {
      en: 'Hardened Connectivity & Silent Shizuku Updates.',
      tr: 'Güçlendirilmiş Bağlantı ve Shizuku ile Sessiz Güncelleme.',
    },
    cards: [
      {
        title: { en: 'Default TLS Record Fragmentation', tr: 'Varsayılan TLS Kayıt Parçalama' },
        desc: {
          en: 'Enabled out of the box to harden fake SNI and CDN-fronting connections against DPI inspection.',
          tr: 'Sahte SNI ve CDN fronting senaryolarında DPI engellemelerine karşı dayanıklılık için varsayılan olarak etkindir.',
        },
      },
      {
        title: { en: 'Shizuku Silent Updater & Cert Pinning', tr: 'Shizuku Sessiz Güncelleyici ve İmza Doğrulama' },
        desc: {
          en: 'Reads v4war-update.json with SHA-256 & signing certificate pin verification, installing updates silently via UpdateUserService.',
          tr: 'v4war-update.json üzerinden SHA-256 ve imza sertifikası doğrulaması yapar; Shizuku UpdateUserService ile arka planda sessiz kurulum sağlar.',
        },
      },
      {
        title: { en: 'Zero-Rating Route Proxy Hops & Custom SNI', tr: 'Rota Proxy Atlamaları ve Özel SNI' },
        desc: {
          en: 'Per-group landing/front proxy inheritance on route rules, custom subscription SNI override, and Auto Selector multi-group ranking.',
          tr: 'Rota kurallarında grup iniş/ön proxy devralma, abonelik bazlı özel SNI ezme ve çoklu grup Otomatik Seçici sıralaması.',
        },
      },
      {
        title: { en: 'Sanitized Backups & Daily Upstream Sync', tr: 'Temizlenmiş Yedekler ve Günlük Upstream Sync' },
        desc: {
          en: 'Cross-platform .thrbackup with WebDAV sync and automatic HotspotBackupSanitizer credential stripping, plus daily automated rebase onto upstream.',
          tr: 'WebDAV senkronizasyonlu .thrbackup yedekleri, otomatik gizli veri temizliği (HotspotBackupSanitizer) ve günlük otomatik upstream senkronizasyonu.',
        },
      },
    ],
  },
  download: {
    eyebrow: { en: 'Install V4War', tr: 'V4War Kurulumu' },
    title: {
      en: 'Download Verified APKs Directly From GitHub Releases.',
      tr: 'Doğrulanmış APK Dosyalarını Doğrudan GitHub Releases’tan İndirin.',
    },
    subtitle: {
      en: 'Automatically synced with the latest GitHub release. Pick the build matching your Android CPU architecture (Android 7.0+ / API 24–35).',
      tr: 'En güncel GitHub sürümü ile otomatik senkronize edilir. Android cihazınızın işlemci mimarisine uygun paketi seçin (Android 7.0+ / API 24–35).',
    },
    sourceTitle: { en: 'Build & Test From Source (JDK 17/21 · SDK 35)', tr: 'Kaynak Koddan Derleme ve Test (JDK 17/21 · SDK 35)' },
  },
  faq: {
    eyebrow: { en: 'FAQ & Credits', tr: 'Sıkça Sorulan Sorular ve Teşekkürler' },
    title: { en: 'Quick Answers', tr: 'Hızlı Yanıtlar' },
  },
}

export const HOTSPOT_BACKENDS: HotspotBackend[] = [
  {
    id: 'auto',
    name: { en: 'Auto (Adaptive)', tr: 'Otomatik (Uyarlanabilir)' },
    privilege: { en: 'Adaptive Fallback', tr: 'Uyarlanabilir Geçiş' },
    architecture: 'Root → Shizuku → LAN Proxy',
    summary: {
      en: 'Evaluates available system privileges on start and selects the highest-transparency tethering engine without manual intervention.',
      tr: 'Başlatmada sistem yetkilerini kontrol eder ve manuel müdahale gerektirmeden en şeffaf paylaşım motorunu otomatik seçer.',
    },
    highlights: {
      en: [
        'Seamless fallback if Root or Shizuku is temporarily unavailable',
        'Preserves client access policies across Wi-Fi, USB RNDIS & Bluetooth PAN',
        'Integrated into Android Quick Settings tile (HotspotShareTileService)',
      ],
      tr: [
        'Root veya Shizuku erişimi yoksa sorunsuz şekilde yedek moda geçer',
        'Wi-Fi, USB RNDIS ve Bluetooth PAN üzerinde kesintisiz çalışır',
        'Android Hızlı Ayarlar kutucuğu (HotspotShareTileService) ile tek tıkla kontrol',
      ],
    },
  },
  {
    id: 'shizuku',
    name: { en: 'Shizuku TUN', tr: 'Shizuku TUN' },
    privilege: { en: 'Rootless (UID 2000)', tr: 'Rootsuz (UID 2000)' },
    architecture: 'TestNetworkManager + TetheringManager + hev-socks5-tunnel',
    summary: {
      en: 'True transparent system-wide TUN tethering without Root. Connected phones, laptops, or consoles need zero proxy configuration.',
      tr: 'Root gerektirmeden gerçek şeffaf TUN paylaşımı. Bağlanan telefon, bilgisayar veya konsollarda hiçbir proxy ayarı yapmanız gerekmez.',
    },
    highlights: {
      en: [
        'Dual-stack TUN interface with upstream drift watchdog',
        'Automatic ADB 127.0.0.1:5555 watchdog revival & binder-death recovery',
        'Cross-process diagnostic log drawer for UserService & hev-socks5-tunnel',
      ],
      tr: [
        'Çift yığın (dual-stack) TUN arayüzü ve yukarı akış sapma izleyici (watchdog)',
        'ADB 127.0.0.1:5555 otomatik canlandırma ve binder ölüm kurtarma',
        'UserService ve hev-socks5-tunnel olayları için canlı Material 3 log çekmecesi',
      ],
    },
  },
  {
    id: 'root',
    name: { en: 'Root iptables', tr: 'Root iptables' },
    privilege: { en: 'Superuser (libsu)', tr: 'Süper Kullanıcı (libsu)' },
    architecture: 'Custom iptables chains + RootTetherHelper',
    summary: {
      en: 'Kernel-level packet forwarding with idempotent rule deduplication, IPv6 leak prevention, and configurable fail-closed safety.',
      tr: 'Tekilleştirilmiş kural yönetimi, IPv6 sızıntı koruması ve yapılandırılabilir fail-closed güvenliği ile çekirdek düzeyinde yönlendirme.',
    },
    highlights: {
      en: [
        'Idempotent iptables chains prevent duplicate rules on network changes',
        'Configurable fail-closed protection on VPN drop (Block Traffic or Stop Hotspot)',
        'Automatic USB/Wi-Fi/BT tethering bring-up via RootTetherHelper',
      ],
      tr: [
        'Ağ değişikliklerinde kural çakışmasını önleyen tekilleştirilmiş iptables zincirleri',
        'VPN koptuğunda fail-closed koruma (Trafiği Engelle veya Hotspot’u Durdur)',
        'RootTetherHelper ile otomatik Wi-Fi / USB / Bluetooth tether başlatma',
      ],
    },
  },
  {
    id: 'lan',
    name: { en: 'LAN Proxy', tr: 'LAN Proxy' },
    privilege: { en: 'Unprivileged', tr: 'Yetkisiz (Ek İzin Yok)' },
    architecture: 'SOCKS5 / HTTP / Mixed + UoT',
    summary: {
      en: 'Zero-permission local socket sharing with optional credentials, UDP-over-TCP (UoT), one-tap socks5:// share links, and desktop auto-discovery.',
      tr: 'Ek sistem izni gerektirmeyen yerel port paylaşımı; isteğe bağlı parola, UoT desteği, tek tıkla socks5:// linki ve otomatik masaüstü keşfi.',
    },
    highlights: {
      en: [
        'Zero-config pairing with theyusa/Throne desktop Hotspot Gateway',
        'Live client list & bandwidth via Clash API (/connections) + kernel ARP',
        'One-tap QR & socks5:// URI export with credential sanitization in backups',
      ],
      tr: [
        'theyusa/Throne masaüstü Hotspot Ağ Geçidi ile sıfır ayarlı eşleşme',
        'Clash API (/connections) + çekirdek ARP ile canlı istemci ve hız takibi',
        'Tek tıkla QR ve socks5:// dışa aktarma, yedeklerde otomatik parola temizliği',
      ],
    },
  },
]

export const TOOLBOX_ITEMS: ToolboxItem[] = [
  {
    id: 'hotspot',
    title: { en: 'Hotspot & Proxy Share', tr: 'Hotspot ve Proxy Paylaşım' },
    badge: 'TUN / LAN',
    summary: {
      en: 'Unified control center for Auto, Shizuku TUN, Root iptables, and LAN Proxy modes across Wi-Fi, USB RNDIS, and Bluetooth PAN.',
      tr: 'Wi-Fi, USB RNDIS ve Bluetooth PAN üzerinde Auto, Shizuku TUN, Root iptables ve LAN Proxy için birleşik kontrol merkezi.',
    },
    specs: {
      en: ['Auto / Shizuku / Root / LAN switching', 'Live client ARP + Clash API counter', 'Cross-process M3 log drawer'],
      tr: ['Auto / Shizuku / Root / LAN geçişi', 'Canlı istemci ARP + Clash API sayacı', 'Çapraz süreç M3 log çekmecesi'],
    },
    sampleOutput: {
      label: 'TetherWatchService · Active',
      lines: [
        'Backend: Shizuku TUN (UID 2000) · Dual-Stack',
        'Channel: Wi-Fi Hotspot (wlan1) · 10.81.9.235:2080',
        'Clients: 3 connected · ↑ 14.2 MB  ↓ 186.4 MB',
      ],
    },
  },
  {
    id: 'dns-leak',
    title: { en: 'DNS Leak & Fake-IP Check', tr: 'DNS Sızıntı ve Fake-IP Testi' },
    badge: 'PRIVACY',
    summary: {
      en: 'Compares client public egress vs DNS resolver egress, validates Fake-IP / Strict Route isolation, and inspects Android System Private DNS (DoT).',
      tr: 'İstemci genel çıkış IP’sini DNS çözücü çıkışıyla kıyaslar, Fake-IP / Strict Route yalıtımını ve Sistem Özel DNS (DoT) durumunu denetler.',
    },
    specs: {
      en: ['Public IP vs Resolver ASN diff', 'Fake-IP 198.18.0.0/15 range audit', 'Private DNS (DoT) leak detection'],
      tr: ['Genel IP vs Çözücü ASN farkı', 'Fake-IP 198.18.0.0/15 doğrulaması', 'Sistem Özel DNS (DoT) sızıntı tespiti'],
    },
    sampleOutput: {
      label: 'Isolation Audit · Passed',
      lines: [
        'Egress IP: 185.x.x.42 (Frankfurt · AS24940)',
        'DNS Resolver: 185.x.x.42 (No ISP leak)',
        'Fake-IP Pool: Active · Strict Route: Enforced',
      ],
    },
  },
  {
    id: 'stun',
    title: { en: 'STUN NAT Behaviour Discovery', tr: 'STUN NAT Davranış Keşfi' },
    badge: 'RFC 5389/5780',
    summary: {
      en: 'Pure Kotlin RFC 5389 / 5780 NAT classifier with RTT/jitter/loss telemetry, custom server presets, and physical network VPN bypass testing.',
      tr: 'Saf Kotlin RFC 5389 / 5780 NAT sınıflandırıcısı; RTT/jitter/kayıp metrikleri, özel sunucu profilleri ve fiziksel ağ üzerinden VPN bypass testi.',
    },
    specs: {
      en: ['Mapping & Filtering NAT matrix', 'Open / Full-Cone / Symmetric detection', 'Direct physical vs Proxy toggle'],
      tr: ['Mapping & Filtering NAT matrisi', 'Open / Full-Cone / Symmetric tespiti', 'Doğrudan fiziksel ağ vs Proxy seçimi'],
    },
    sampleOutput: {
      label: 'STUN Probe · stun.cloudflare.com',
      lines: [
        'NAT Mapping: Endpoint-Independent (Full-Cone)',
        'NAT Filtering: Address & Port-Dependent',
        'RTT: 38 ms · Jitter: 2.1 ms · Loss: 0.0%',
      ],
    },
  },
  {
    id: 'ip-lookup',
    title: { en: 'IP Lookup & Exit Comparison', tr: 'IP Sorgulama ve Çıkış Kıyaslama' },
    badge: 'DUAL EXIT',
    summary: {
      en: 'Side-by-side Proxy Exit vs Direct Physical Exit inspection plus ASN, ISP, geolocation, timezone, and IP range classification.',
      tr: 'Proxy Çıkışı vs Doğrudan Fiziksel Çıkış yan yana karşılaştırması; ASN, ISS, konum, saat dilimi ve IP aralığı sınıfı analizi.',
    },
    specs: {
      en: ['Proxy vs Direct side-by-side cards', 'Any IP, domain, or URL target', 'ASN, ISP, TZ & Anycast metadata'],
      tr: ['Proxy vs Doğrudan yan yana kartlar', 'IP, alan adı veya URL sorgulama', 'ASN, ISS, saat dilimi ve ağ sınıfı'],
    },
    sampleOutput: {
      label: 'Exit Diff · Proxy vs Physical',
      lines: [
        'Proxy Exit:  185.x.x.42 · DE · Hetzner Online',
        'Direct Exit: 176.x.x.19 · TR · Local Carrier',
        'Delta: Route isolation verified (0 overlap)',
      ],
    },
  },
  {
    id: 'dns-lookup',
    title: { en: 'DNS Lookup', tr: 'DNS Sorgulama' },
    badge: 'UDP / DoH / DoT',
    summary: {
      en: 'Query A, AAAA, CNAME, HTTPS, TXT, MX, NS, and PTR records across Core, System DNS, or Custom resolvers with side-by-side comparison.',
      tr: 'Çekirdek, Sistem DNS veya özel çözücüler (UDP, DoH, DoT) üzerinden A, AAAA, CNAME, HTTPS, TXT, MX, NS ve PTR kayıtlarını kıyaslayın.',
    },
    specs: {
      en: ['8 record types including HTTPS/SVCB', 'Core vs System vs Custom DoH/DoT', 'TTL & response latency breakdown'],
      tr: ['HTTPS/SVCB dahil 8 kayıt tipi', 'Çekirdek vs Sistem vs Özel DoH/DoT', 'TTL ve yanıt süresi ayrımı'],
    },
    sampleOutput: {
      label: 'DNS Query · api.github.com (HTTPS)',
      lines: [
        'Core DoH:   140.82.121.6 (TTL 60s · 24 ms)',
        'System DNS: 140.82.121.6 (TTL 54s · 41 ms)',
        'ALPN: h2, http/1.1 · Status: NOERROR',
      ],
    },
  },
  {
    id: 'ping',
    title: { en: 'Local Ping', tr: 'Yerel Ping' },
    badge: 'TCP / UDP / ICMP',
    summary: {
      en: 'Measure TCP, UDP, and ICMP latency with a live RTT timeline chart, min/avg/max, jitter, packet loss, and physical network bypass toggle.',
      tr: 'Canlı RTT zaman çizelgesi grafiği, min/ort/maks, jitter, paket kaybı ve doğrudan fiziksel ağ anahtarı ile TCP, UDP ve ICMP gecikme ölçümü.',
    },
    specs: {
      en: ['Real-time RTT timeline chart', 'TCP handshake, UDP & ICMP probes', 'Min / Avg / Max / Jitter / Loss'],
      tr: ['Anlık RTT zaman çizelgesi grafiği', 'TCP el sıkışma, UDP ve ICMP testi', 'Min / Ort / Maks / Jitter / Kayıp'],
    },
    sampleOutput: {
      label: 'TCP Ping · 1.1.1.1:443 (10 packets)',
      lines: [
        'Min: 29 ms · Avg: 32.4 ms · Max: 37 ms',
        'Jitter: 1.8 ms · Packet Loss: 0%',
        'Path: Proxy Outbound (VLESS + Reality)',
      ],
    },
  },
  {
    id: 'connectivity',
    title: { en: 'Connectivity Test', tr: 'Bağlantı ve Platform Testi' },
    badge: '24+ SERVICES',
    summary: {
      en: 'One-tap reachability & region probe across AI (ChatGPT, Claude, Gemini, Grok), Streaming (Netflix, YouTube), Social, and Developer platforms.',
      tr: 'Yapay Zeka (ChatGPT, Claude, Gemini, Grok), Yayın (Netflix, YouTube), Sosyal ve Geliştirici platformları için tek dokunuşla erişilebilirlik testi.',
    },
    specs: {
      en: ['AI: ChatGPT, Claude, Gemini, Grok, Perplexity', 'Streaming: Netflix, YouTube, Disney+, Spotify', 'Dev & Social: GitHub, Discord, Telegram, X'],
      tr: ['AI: ChatGPT, Claude, Gemini, Grok, Perplexity', 'Yayın: Netflix, YouTube, Disney+, Spotify', 'Dev & Sosyal: GitHub, Discord, Telegram, X'],
    },
    sampleOutput: {
      label: 'Platform Probe · 24/24 Reachable',
      lines: [
        'AI Suite: ChatGPT 200 (112ms) · Claude 200 (98ms)',
        'Streaming: YouTube 200 (64ms) · Netflix Region: DE',
        'Developer: GitHub 200 (71ms) · Cloudflare 200 (28ms)',
      ],
    },
  },
  {
    id: 'interfaces',
    title: { en: 'Live Network Interfaces', tr: 'Canlı Ağ Arayüzleri' },
    badge: 'NETLINK / DNS',
    summary: {
      en: 'Real-time Active Network & System DNS overview with IPv4/IPv6 addresses, netmasks, MTU, MAC, flags, routes, and tethered interface detection.',
      tr: 'Aktif ağ ve sistem DNS özeti; IPv4/IPv6 adresleri, alt ağ maskeleri, MTU, MAC, bayraklar, yönlendirme tabloları ve tether arayüz tespiti.',
    },
    specs: {
      en: ['tun0 / wlan0 / rndis0 live state', 'MTU, routes, IPv4/IPv6 prefix length', 'Active System DNS & transport caps'],
      tr: ['tun0 / wlan0 / rndis0 anlık durumu', 'MTU, rotalar, IPv4/IPv6 önekleri', 'Aktif Sistem DNS ve taşıyıcı yetenekleri'],
    },
    sampleOutput: {
      label: 'Interfaces · 3 UP',
      lines: [
        'tun0:  UP · MTU 1500 · 172.19.0.1/30',
        'wlan0: UP · MTU 1500 · 192.168.1.44/24',
        'wlan1: UP · Hotspot Gateway · 10.81.9.235/24',
      ],
    },
  },
  {
    id: 'traffic',
    title: { en: 'Traffic Usage Analytics', tr: 'Trafik Kullanım Analizi' },
    badge: 'REALTIME',
    summary: {
      en: 'Live proxy/direct speed, session vs app UID vs device totals, visual traffic bars, and per-profile / per-group usage history.',
      tr: 'Anlık proxy/doğrudan hız, oturum, uygulama UID ve cihaz toplamları, görsel trafik çubukları ve profil/grup bazlı kullanım geçmişi.',
    },
    specs: {
      en: ['Per-App UID traffic breakdown', 'Proxy vs Direct split meters', 'Per-profile & per-group history'],
      tr: ['Uygulama UID bazlı trafik dağılımı', 'Proxy vs Doğrudan ayrık sayaçlar', 'Profil ve grup bazlı geçmiş'],
    },
    sampleOutput: {
      label: 'Session Traffic · Active',
      lines: [
        'Proxy Speed:  ↑ 1.4 MB/s · ↓ 18.6 MB/s',
        'Direct Speed: ↑ 12 KB/s  · ↓ 48 KB/s',
        'Top UID: Browser (1.24 GB) · Telegram (310 MB)',
      ],
    },
  },
  {
    id: 'cellular',
    title: { en: 'Cellular Network Menu', tr: 'Hücresel Ağ Menüsü' },
    badge: 'RADIO INFO',
    summary: {
      en: 'Direct access to Android Radio Info diagnostics (modern & legacy), engineering testing menu, and preferred network type controls (LTE / NR 5G pinning).',
      tr: 'Android Radyo Tanılama ekranlarına (modern ve klasik), test menüsüne ve tercih edilen ağ türü (LTE / NR 5G sabitleme) kontrollerine hızlı erişim.',
    },
    specs: {
      en: ['Android Radio Info launcher', 'LTE / NR 5G preferred band pinning', 'Dual-SIM phone index aware'],
      tr: ['Android Radio Info hızlı açılışı', 'LTE / NR 5G ağ türü sabitleme', 'Çift SIM telefon indeksi uyumlu'],
    },
    sampleOutput: {
      label: 'Radio Diagnostics',
      lines: [
        'Target: com.android.phone.settings.RadioInfo',
        'Modes: NR_ONLY · NR_LTE · LTE_ONLY',
        'Fallback: Legacy PhoneTestingIntent ready',
      ],
    },
  },
]

export const PROTOCOLS: ProtocolItem[] = [
  { name: 'VLESS', category: 'proxy', badge: 'Reality / TLS' },
  { name: 'Xray VLESS', category: 'proxy', badge: 'Xray Core' },
  { name: 'VMess', category: 'proxy' },
  { name: 'Trojan', category: 'proxy' },
  { name: 'Shadowsocks', category: 'proxy' },
  { name: 'SOCKS5', category: 'proxy' },
  { name: 'HTTP(S)', category: 'proxy' },
  { name: 'ShadowTLS', category: 'proxy', badge: 'v3' },
  { name: 'AnyTLS', category: 'proxy' },
  { name: 'NaïveProxy', category: 'proxy' },
  { name: 'TrustTunnel', category: 'proxy' },
  { name: 'Hysteria 1/2', category: 'quic', badge: 'QUIC' },
  { name: 'TUIC', category: 'quic', badge: 'v5' },
  { name: 'Juicity', category: 'quic' },
  { name: 'Mieru', category: 'quic' },
  { name: 'Snell', category: 'proxy' },
  { name: 'WireGuard', category: 'tunnel', badge: 'WARP' },
  { name: 'AmneziaWG', category: 'tunnel', badge: 'Obfuscated' },
  { name: 'MASQUE', category: 'tunnel', badge: 'HTTP/3' },
  { name: 'SSH', category: 'tunnel' },
  { name: 'OpenVPN', category: 'tunnel' },
  { name: 'OpenConnect', category: 'tunnel' },
  { name: 'DNSTT / VayDNS', category: 'dns', badge: 'DNS Tunnel' },
  { name: 'MasterDNS', category: 'dns', badge: 'DoH / DoT' },
  { name: 'Direct', category: 'smart' },
  { name: 'Chain', category: 'smart', badge: 'Multi-Hop' },
  { name: 'Auto Selector', category: 'smart', badge: 'Multi-Group' },
  { name: 'Custom Outbound', category: 'smart', badge: 'JSON' },
]

export const FAQS: FaqItem[] = [
  {
    q: {
      en: 'How do I share my VPN without Root?',
      tr: 'Root olmadan VPN bağlantımı nasıl paylaşırım?',
    },
    a: {
      en: 'Open Tools → Network → Hotspot & Proxy Share. Select Shizuku mode for system-wide transparent TUN tethering (works over Wi-Fi Hotspot, USB RNDIS, and Bluetooth PAN with zero proxy config on client devices), or choose LAN Proxy to share a SOCKS5/HTTP link.',
      tr: 'Araçlar → Ağ → Hotspot ve Proxy Paylaşımı ekranını açın. Bağlanan cihazlarda hiçbir proxy ayarı yapmadan sistem geneli şeffaf TUN paylaşımı için Shizuku modunu (Wi-Fi Hotspot, USB RNDIS ve Bluetooth destekli), ya da SOCKS5/HTTP linki paylaşmak için LAN Proxy modunu seçin.',
    },
  },
  {
    q: {
      en: 'How do I connect my PC without typing an IP or Port?',
      tr: 'Bilgisayarımla IP veya Port girmeden nasıl VPN paylaşırım?',
    },
    a: {
      en: 'Install theyusa/Throne on Windows or Linux, connect to your phone’s Wi-Fi hotspot, and turn on Settings → Basic Settings → Hotspot Gateway. Desktop discovers the phone gateway automatically.',
      tr: 'Windows veya Linux bilgisayarınıza theyusa/Throne kurun, telefonunuzun hotspot ağına bağlanın ve masaüstü ayarlarından Settings → Basic Settings → Hotspot Ağ Geçidi seçeneğini açın. Bilgisayar otomatik olarak bağlanır.',
    },
  },
  {
    q: {
      en: 'Which APK architecture should I download?',
      tr: 'Cihazım için hangi APK mimarisini indirmeliyim?',
    },
    a: {
      en: 'Almost all modern Android phones and tablets (2015+) use arm64-v8a. Older 32-bit ARM devices use armeabi-v7a, while emulators and x86_64 tablets use x86_64.',
      tr: 'Modern Android telefon ve tabletlerin neredeyse tamamı (2015+) arm64-v8a kullanır. Eski 32-bit ARM cihazlar için armeabi-v7a, emülatörler ve x86_64 sistemler için x86_64 sürümünü seçin.',
    },
  },
  {
    q: {
      en: 'How are in-app updates verified and installed?',
      tr: 'Uygulama içi güncellemeler nasıl doğrulanır ve kurulur?',
    },
    a: {
      en: 'V4War checks v4war-update.json on GitHub Releases, verifies SHA-256 checksums and signing certificate pins, and can install updates silently in the background via Shizuku (or via Obtainium).',
      tr: 'V4War, GitHub Releases üzerindeki v4war-update.json dosyasını okur, SHA-256 ve imza sertifikasını doğrular ve Shizuku üzerinden arka planda sessiz kurulum (veya Obtainium takibi) sunar.',
    },
  },
]
