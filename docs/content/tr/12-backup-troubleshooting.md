---
title: Yedekleme, Upstream Sync ve Sorun Giderme
description: Çapraz platform .thrbackup ve WebDAV senkronizasyonu, gizli veri temizliği, günlük upstream senkronizasyonu ve çözümler.
order: 12
group: advanced
---

# Yedekleme, Upstream Sync ve Sorun Giderme

## Çapraz platform yedekleme ve WebDAV

`.thrbackup` arşivlerini dışa aktarmak veya geri yüklemek için **Araçlar → Yedekleme** ekranını açın:

- **WebDAV Senkronizasyonu** — [theyusa/Throne](https://github.com/theyusa/Throne) masaüstü ile aynı yedek şemasını paylaşır; tüm cihazlarınızdaki yedekleri cihaz adı, platform ve sürüm bilgisiyle listeler.
- **Otomatik Gizli Veri Temizliği** — `HotspotBackupSanitizer`, taşınabilir yedeklerde Hotspot/Proxy kimlik bilgilerini ve hassas anahtarları otomatik olarak temizler.

## Günlük otomatik upstream senkronizasyonu

V4War, `.github/workflows/sync-upstream.yml` iş akışıyla [throneproj/ThroneForAndroid](https://github.com/throneproj/ThroneForAndroid) ile sürekli güncel kalır:

- Her gün **03:00 UTC** saatinde (veya manuel tetiklemeyle) çalışır.
- Fork commit'lerini `upstream/main` üzerine rebase eder ve yayınlamadan önce `:app:testOssDebugUnitTest` testlerini doğrular.

## Sorun giderme kontrol listesi

### VPN başlamıyor veya trafik geçmiyor
1. Çekirdeğin soket açabildiğini görmek için **Araçlar → Ağ → Local Ping** ve **IP Lookup** çalıştırın.
2. TLS el sıkışma, Reality açık anahtar veya saat senkronizasyonu hataları için çekmecedeki **Loglar** ekranına bakın.
3. **Ayarlar → DNS** bölümünü kontrol edin — Uzak DNS'in proxy üzerinden erişilebilir olduğundan emin olun.

### V4War arka planda kapanıyor
- Pil optimizasyonunu **Kısıtlanmamış** yapın, uygulamayı Son Kullanılanlar ekranında kilitleyin ve **Bildirim** iznini açık tutun.

### Shizuku Hotspot ekran kilidinden veya yeniden başlatmadan sonra duruyor
- V4War'ın **Yerel ADB Otomatik Kurtarma** mekanizmasının Shizuku'yu otomatik canlandırabilmesi için Kablosuz Hata Ayıklama veya `adb tcpip 5555` açık olmalıdır.
- `hev-socks5-tunnel` ve `UserService` günlükleri için **Hotspot ve Proxy Paylaşımı → Logs** sekmesini inceleyin.

### DNS sızıntısı devam ediyor
- **Araçlar → Ağ → DNS Sızıntı ve Fake-IP Testi**'ni çalıştırın.
- Android sistem **Özel DNS** ayarını kapatın (veya uyumlu hale getirin) ve **Ayarlar → TUN / VPN** altında **Fake-IP** + **Strict Route** seçeneklerini etkinleştirin.
