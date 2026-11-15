---
title: Genel Bakış
description: V4War for Android nedir, ThroneForAndroid üzerine neler ekler ve nasıl çalışır?
order: 1
group: get-started
---

# Genel Bakış

**V4War for Android**, [throneproj/ThroneForAndroid](https://github.com/throneproj/ThroneForAndroid) projesinin birleşik **Sing-box** ve **Xray** çekirdekleri üzerinde geliştirilen, **GNU GPL-3.0** lisanslı ve aktif olarak sürdürülen gelişmiş bir çatalıdır (fork).

Günlük otomatik upstream senkronizasyonunun üzerine; mobil ağ paylaşımı, gelişmiş ağ tanılama araçları ve Material 3 Expressive arayüzü ile Android kullanıcıları için uçtan uca bir proxy/VPN deneyimi sunar.

## V4War'ın sunduğu başlıca farklar

- **Hotspot ve Proxy Paylaşım Paketi** — Aktif VPN tünelinizi **Wi-Fi Hotspot**, **USB RNDIS** veya **Bluetooth PAN** üzerinden **Auto**, **Shizuku TUN (Rootsuz UID 2000)**, **Root (`iptables`)** ya da **LAN Proxy** (`HTTP` / `SOCKS5`) ile paylaşın; bağlı cihazları ve tanılama loglarını canlı takip edin.
- **Sıfır Ayarlı Masaüstü Eşleşmesi** — Windows veya Linux üzerindeki [theyusa/Throne](https://github.com/theyusa/Throne) ile **Hotspot Ağ Geçidi** otomatik keşfi sayesinde IP veya port yazmadan doğrudan bağlanın.
- **10'u 1 Arada Ağ Araç Kutusu** — **Araçlar → Ağ ve Araçlar** altında RFC 5389/5780 STUN NAT keşfi, DNS Sızıntı ve Fake-IP testi, çift çıkışlı IP Sorgulama, DNS Lookup, canlı RTT grafikli Yerel Ping, 24+ servis Bağlantı Testi, Canlı Ağ Arayüzleri, Trafik Kullanımı ve Hücresel Radyo Menüsü.
- **Material 3 Expressive ve Theme Studio** — Canlı mini önizleme kartı, 5 yüzey stili (**Pure AMOLED**, **AMOLED**, **Koyu**, **Açık**, **Sistem**), Monokrom temalar (**Siyah**, **Beyaz**, **Açık Gri**), Android 12+ **Material You** ve 21+ özenle seçilmiş tema tohumu + özel HEX/RGB ayarı.
- **Shizuku Sessiz Güncelleyici ve Güçlendirilmiş Varsayılanlar** — Varsayılan olarak etkin **TLS kayıt parçalama (Fragment)** ile birlikte `v4war-update.json` üzerinden SHA-256 ve imza sertifikası doğrulamalı sessiz Shizuku güncellemesi.

## Desteklenen platformlar ve gereksinimler

| Özellik | Değer |
|---|---|
| Minimum Android | **Android 7.0 (API 24)** |
| Hedef SDK | **Android 15 (API 35)** |
| Mimariler | `arm64-v8a`, `armeabi-v7a`, `x86_64` |
| Cihaz Türleri | Telefonlar, Tabletler, **Android TV** (Kumanda / D-pad desteği) |
| Çekirdekler | **Sing-box** & **Xray-core** |
| Root Gereksinimi | **İsteğe Bağlı** (Shizuku TUN ve LAN Proxy rootsuz çalışır) |

## Sonraki adımlar

1. Doğru APK paketini seçmek ve doğrulamak için **[Kurulum ve Güncelleme](/docs/installation)** sayfasına geçin.
2. İlk sunucunuzu veya aboneliğinizi ekleyip bağlanmak için **[Hızlı Başlangıç](/docs/quick-start)** rehberini izleyin.
3. VPN bağlantınızı diğer cihazlarla paylaşmak için **[Hotspot ve Proxy Paylaşım](/docs/hotspot-share)** bölümünü inceleyin.
