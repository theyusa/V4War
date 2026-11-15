---
title: Protokoller ve Abonelikler
description: V4War'ın desteklediği 28+ proxy, QUIC, tünel, DNS tüneli ve akıllı çıkış protokollerinin tam referansı.
order: 11
group: routing-protocols
---

# Protokoller ve Abonelikler

V4War tek uygulamada **Sing-box** ve **Xray** çekirdeklerini birleştirerek 28'den fazla protokol ve akıllı çıkış türünü destekler. Tüm protokolleri **[Protokoller ve Laboratuvar](/explore)** sayfasından canlı arayabilirsiniz.

## Desteklenen protokol matrisi

| Kategori | Protokoller ve Aktarımlar |
|---|---|
| **Proxy & TLS** | `VLESS` (Reality / TLS), `Xray VLESS` (XHTTP / mKCP / XDRIVE), `VMess`, `Trojan`, `Shadowsocks`, `SOCKS5`, `HTTP(S)`, `ShadowTLS v3`, `AnyTLS`, `NaïveProxy`, `TrustTunnel`, `Snell` |
| **QUIC / UDP** | `Hysteria 1/2`, `TUIC v5`, `Juicity`, `Mieru` |
| **VPN Tünelleri** | `WireGuard` (WARP dahil), `AmneziaWG` (gizlemeli), `MASQUE` (HTTP/3), `SSH`, `OpenVPN`, `OpenConnect` |
| **DNS Tünelleri** | `DNSTT / VayDNS`, `MasterDNS / StormDNS`, `XDNS` |
| **Akıllı Çıkışlar** | `Direct`, `Chain` (Çoklu Atlama), `Auto Selector` (Çoklu Grup & Öncelik Katmanları), `Custom Outbound` (JSON) |

## Abonelikler ve içe aktarma biçimleri

- **Desteklenen Biçimler** — Standart URI paylaşım bağlantıları, **Sing-box JSON**, **v2rayN**, **Shadowsocks (SIP008)**, **Clash** çıkış içe aktarma ve **Happ** yönlendirme içe aktarma.
- **Özel Abonelik SNI** — Kısıtlı ağlarda abonelik güncellemelerini çekerken TLS SNI değerini özelleştirin.
- **Abonelik Filtreleri ve Güncelleme Aralığı** — Düğümleri anahtar kelimeyle filtreleyin ve grup bazlı yenileme aralığı ile TLS/HTTP sürümünü belirleyin.
- **Profil Sabitleme ve Çoklu QR** — Sık kullanılan profilleri listenin en üstüne sabitleyin ve birden fazla profili tek QR kod olarak paylaşın.
- **Deep Link'ler** — Tek dokunuşla içe aktarma için `sn://` ve `throne://` şemalarını destekler.
