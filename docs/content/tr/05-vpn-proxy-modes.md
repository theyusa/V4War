---
title: VPN, Proxy ve DNS Modları
description: VPN (TUN) ve Yalnızca Proxy modları, Uygulama Bazlı Proxy, TUN parametreleri ve DNS mimarisi.
order: 5
group: android
---

# VPN, Proxy ve DNS Modları

V4War, **Ayarlar → Genel → Servis Modu** altında iki ana çalışma modu ile ayrıntılı TUN ve DNS denetimi sunar.

## VPN modu vs Yalnızca Proxy modu

| Mod | Trafiği yakalama biçimi | En uygun kullanım |
|---|---|---|
| **VPN (TUN)** *(Varsayılan)* | Sanal `tun0` arayüzü oluşturur ve sistem trafiğini şeffaf biçimde yönlendirir | Günlük telefon/tablet kullanımı, oyunlar, proxy tanımayan uygulamalar, Hotspot TUN paylaşımı |
| **Yalnızca Proxy** | Android VPN arayüzü açmadan yerel `SOCKS5` / `HTTP` / `Mixed` portları dinler | Başka bir VPN ile zincirleme, Root/ADB proxy akışları veya yalnızca LAN proxy paylaşımı |

## Uygulama Bazlı Proxy (Per-App Proxy)

**Ayarlar → Rota / Uygulamalar** altından **Atla (Bypass)** (seçili uygulamalar VPN dışında kalır) veya **Proxy** (yalnızca seçili uygulamalar VPN kullanır) modunu seçebilirsiniz.

## TUN / VPN ayarları

**Ayarlar → TUN / VPN** altında:

- **TUN Uygulaması** — Cihaz çekirdek uyumluluğuna göre `gVisor`, `System` veya `Mixed` yığını.
- **Strict Route ve Fake-IP** — DNS sorgularını çekirdek içinde (`198.18.0.0/15` Fake-IP havuzu) çözerek ve katı arayüz bağlama uygulayarak DNS/soket sızıntılarını önler.
- **MTU** — Belirtilmeyen WireGuard profillerinde varsayılan `1280` MTU kullanılır; operatör veya hotspot kararlılığı için TUN MTU özelleştirilebilir.

## DNS mimarisi

**Ayarlar → DNS** altında:

- **Uzak DNS ve Doğrudan DNS** — Proxy trafiği ile doğrudan trafik için ayrı çözücüler tanımlayın; daha hızlı yanıt için **birden fazla DNS sunucusunu paralel sorgulama** desteklenir.
- **Protokoller** — `DoH` (DNS-over-HTTPS), `DoT` (DNS-over-TLS), `UDP`, `TCP` ve `Local`.
- **Yönlendirme Profiline Özel DNS** — Her yönlendirme profili kendi Uzak ve Doğrudan DNS sunucularını kullanabilir.
- **Sistem Özel DNS Uyarısı** — Android'in *Özel DNS (DoT)* ayarı açıksa sorguların VPN'i atlamadığından emin olmak için **[10'u 1 Arada Ağ Araç Kutusu](/docs/network-toolbox)** içindeki **DNS Sızıntı ve Fake-IP Testi**'ni çalıştırın.
