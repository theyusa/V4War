---
title: Yönlendirme, Kural Setleri ve DNS
description: Sing-box ve Xray yönlendirme kuralları, Rota Yolu Sorgulama, grup bazlı yönlendirme, TLS parçalama ve WARP.
order: 10
group: routing-protocols
---

# Yönlendirme, Kural Setleri ve DNS

V4War, Sing-box ve Xray kural motorlarıyla çalışan esnek bir trafik yönlendirme sistemi sunar.

## Yönlendirme profillerini yönetme

Kural profilleri oluşturmak, klonlamak, içe aktarmak veya değiştirmek için **Çekmece → Yönlendirme** ekranını açın:

- **Kural Sırası Önemlidir** — Kurallar yukarıdan aşağıya sırayla değerlendirilir (`Domain`, `IP CIDR`, `Port`, `Uygulama Paketi`, `Rule Set`, `GeoIP / Geosite`).
- **Eylemler** — Eşleşen trafiği `Proxy`, `Direct`, `Block` veya belirli bir **Rota Proxy Atlaması / Çıkışı** üzerine yönlendirin.
- **Tekil Kural Anahtarı** — Kuralları silmeden tek tek açıp kapatabilirsiniz.
- **Rota Yolu Sorgulama (Route Path Lookup)** — Herhangi bir uygulama, alan adı veya IP'nin hangi kurala ve çıkışa yönlendirileceğini anında test edin.
- **Kural Setlerini ve Rotaları VPN'den Sonra İndir** — **Ayarlar → Yönlendirme → Kural setleri ve rotaları VPN'den sonra indir** seçeneğini açarak özel `.srs` kural setlerinin ve uzak rotaların bağlanmadan önce yerine VPN tüneli (`proxy`) üzerinden indirilmesini sağlayabilirsiniz (zero-rating veya kısıtlı ağlar için idealdir).

## Grup bazlı yönlendirme ve Happ içe aktarma

- Bir grubun ayarlarından o gruba özel **Grup Bazlı Yönlendirme Profili** atayarak genel yönlendirme seçimini geçersiz kılabilirsiniz.
- **Happ** biçiminde yönlendirme profili sunan abonelikler her güncellemede grubun kendi yönlendirme profilini otomatik güncelleyebilir (abonelikten gelen DNS ayarları güvenlik nedeniyle yok sayılır).
- Uzak bir yönlendirme profilini klonladığınızda yerel bir kopya oluşturulur; böylece abonelik güncellemeleri düzenlemelerinizi silmez.

## Sansür karşıtı ön ayarlar ve TLS parçalama

- **Varsayılan TLS Kayıt Parçalama (Fragment)** — Sahte SNI ve CDN fronting senaryolarında DPI engellemelerine karşı dayanıklılık için varsayılan olarak etkindir.
- **QUIC Opener** — İlk QUIC paketini engelleyen ağlarda HTTP/3 protokollerine (`MASQUE`, `Hysteria2`, `TUIC`) yardımcı olur.
- **Ön Ayarlar (`Ayarlar → Ön Ayarlar`)** — TLS fragment, Multiplex (`TCP Brutal`, `XUDP`) ve taşıma şablonlarını profiller arasında yeniden kullanın.

## Cloudflare WARP ve Zincirler

- **Cloudflare WARP** oluşturucu: kayıt sırasında TLS parçalama desteğiyle **WireGuard**, **AmneziaWG** (QUIC imza gizlemeli) ve **MASQUE (HTTP/3)** profilleri üretir.
- **Zincir (Chain) Çıkışlar** — Birden fazla atlamayı (atlama olarak **Auto Selector** dahil) birleştirin ve **atlama bazlı zincir testi** çalıştırın.
