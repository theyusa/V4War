---
title: Hızlı Başlangıç
description: Sunucu veya abonelik ekleyin, gecikme testi yapın, yönlendirmeyi seçin ve ilk VPN oturumunuzu başlatın.
order: 3
group: get-started
---

# Hızlı Başlangıç

Sıfır kurulumdan güçlendirilmiş ve çalışır bir VPN bağlantısına iki dakikadan kısa sürede ulaşmak için aşağıdaki adımları izleyin.

## 1. Sunucu veya abonelik ekleme

Ana **Profiller** ekranını açın ve üst çubuktaki **+** düğmesine dokunun:

- **QR Kod Tara** — Tekil sunucu veya çoklu profil QR kodlarını tarayın.
- **Panodan İçe Aktar** — `vless://`, `vmess://`, `trojan://`, `ss://`, `hy2://`, `tuic://`, `wg://`, `sn://` bağlantılarını veya tam **Sing-box JSON** / **Clash outbound** yapılandırmalarını yapıştırın.
- **Abonelik Ekle** — **Çekmece → Gruplar → +** yolunu izleyerek abonelik URL'nizi ekleyin. Standart base64/JSON abonelikleri, **Happ** yönlendirme profilleri, abonelik filtreleri ve **özel abonelik SNI** desteği mevcuttur.
- **Deep Link'ler** — `sn://` veya `throne://` bağlantılarıyla tek dokunuşta içe aktarın.

## 2. Gecikme ve erişilebilirlik testi

1. Gerçek proxy yanıt süresini ölçmek için grup menüsünden **⋮ → Bağlantı Testi** (URL testi) veya **TCP Ping** çalıştırın.
2. Herhangi bir profilde **Servis Kontrolü** çalıştırarak AI, yayın ve sosyal platformlara erişimi ve çıkış ülkesini doğrulayın.
3. İsteğe bağlı: Tek veya birden fazla grup arasında öncelik katmanlarıyla en iyi sunucuyu otomatik seçen bir **Otomatik Seçici (Auto Selector)** profili oluşturun.

## 3. Temel yönlendirmeyi seçme

Kural profillerini yönetmek için **Çekmece → Yönlendirme** ekranını açın:

- Reklam/izleyici engelleme, yerel LAN veya yurt içi trafiği doğrudan (`Direct`) geçirme ya da belirli uygulama/alan adlarını `Proxy` üzerinden yönlendirme kuralları oluşturun.
- Bağlanmadan önce bir uygulamanın, alan adının veya IP'nin hangi kurala düştüğünü görmek için **Rota Yolu Sorgulama (Route Path Lookup)** aracını kullanın.
- Grup ayarlarından her grup için bağımsız **grup bazlı yönlendirme profili** tanımlayabilirsiniz.

## 4. Bağlanma

1. Ana ekranda bir profil kartı seçin (Theme Studio vurgu çubuğuyla işaretlenir).
2. Sağ alttaki **Gönder / Bağlan (FAB)** düğmesine dokunun.
3. İlk çalıştırmada Android VPN izin istemini onaylayın.
4. Tünel açıldığında alt durum çubuğunda anlık yükleme/indirme hızları (`↑ / ↓`) görünür.

> **Güvenlik Notu:** V4War, SNI tabanlı DPI filtrelemelerine karşı dayanıklılık için **TLS Kayıt Parçalama (Fragment)** özelliğini varsayılan olarak açık tutar. İsterseniz **Ayarlar → Ön Ayarlar (Presets)** veya profil düzenleyiciden ince ayar yapabilirsiniz.
