---
title: İzinler, Arka Plan ve Android TV
description: V4War'ın arka planda kapanmasını önleyin, Hızlı Ayarlar kutucuklarını, widget'ları ve Android TV modunu kullanın.
order: 4
group: android
---

# İzinler, Arka Plan ve Android TV

Android üreticileri pil ve bildirim izinleri doğru verilmediğinde arka plan VPN servislerini sonlandırabilir.

## Gerekli ve önerilen izinler

1. **VPN İzni** — VPN (TUN) modunu ilk başlattığınızda Android tarafından istenir; sistem trafiğini Sing-box/Xray tüneline almak için zorunludur.
2. **Bildirimler (`POST_NOTIFICATIONS`)** — Android 13+ cihazlarda ön plan VPN servis bildiriminin ve canlı hız göstergesinin görünmesi için gereklidir.
3. **Kısıtlanmamış Pil Kullanımı** — **Android Ayarlar → Uygulamalar → V4War → Pil → Kısıtlanmamış** seçeneğini işaretleyin.
4. **Üretici Arka Plan Kilidi** — MIUI/HyperOS, ColorOS, OneUI veya EMUI cihazlarda Son Kullanılanlar ekranından uygulamayı kilitleyin ve varsa otomatik başlatmayı açın.

## Her Zaman Açık VPN ve Bypass

- **Her Zaman Açık VPN (Always-on VPN)** — Cihaz açıldığında V4War'ın otomatik başlatılması için **Android Ayarlar → Ağ ve İnternet → VPN → V4War** menüsünden etkinleştirin.
- **Uygulamaların VPN'i atlamasına izin ver** — **Ayarlar → Genel** altından açıldığında, sistem bildirim servisleri (ör. FCM) veya yerel bankacılık uygulamaları talep ettiğinde tüneli atlayabilir (*VPN olmadan bağlantıları engelle* açıkken devre dışıdır).

## Widget'lar, Hızlı Ayarlar ve bildirimler

- **Hızlı Ayarlar Kutucukları** — Bildirim panelinize hem VPN aç/kapat kutucuğunu hem de **Hotspot Paylaşım kutucuğunu** (`HotspotShareTileService`) ekleyin.
- **Ana Ekran Widget'ları** — Uygulamayı açmadan bağlantıyı başlatıp durdurmak için ana ekran widget'ı ekleyin.
- **Bildirim Eylemleri** — **Ayarlar → Görünüm** altından bildirim düğmelerini, **Doğrudan hızı göster** ve **Bildirimde grubu göster** seçeneklerini özelleştirin.

## Android TV desteği

V4War yerel **Android TV** desteğiyle gelir:

- Profil kartları, yan çekmece ve ayarlarda tam **D-pad / kumanda** odaklı gezinme.
- Özel Android TV başlatıcı afişi (launcher banner).
- Kamerasız cihazlarda **Pano**, **QR ağ/LAN aktarımı**, **Deep Link** veya **`.thrbackup` geri yükleme** ile kolay profil aktarımı.
