---
title: Kurulum ve Güncelleme
description: Cihazınıza uygun V4War APK dosyasını seçin, SHA-256 doğrulaması yapın ve Shizuku sessiz güncelleyiciyi kullanın.
order: 2
group: get-started
---

# Kurulum ve Güncelleme

V4War, derlenmiş ve doğrulanabilir APK paketlerini [GitHub Releases](https://github.com/theyusa/V4War/releases) üzerinden yayınlar.

## Doğru APK mimarisini seçme

Android cihazınızın işlemci mimarisine uygun paketi indirin:

| APK Mimarisi | Hedef Cihazlar |
|---|---|
| **`arm64-v8a`** *(Önerilen)* | Modern Android telefonların, tabletlerin ve güncel Android TV kutularının neredeyse tamamı (2015+) |
| **`armeabi-v7a`** | Eski 32-bit ARM telefonlar ve eski Android TV cihazları |
| **`x86_64`** | Android emülatörleri, Chromebook'lar ve x86_64 tabletler |

> **İpucu:** Cihazınızın mimarisinden emin değilseniz `arm64-v8a` ile başlayın. Uyumsuz bir ABI yüklenmeye çalışıldığında Android kurulum öncesinde güvenli şekilde uyarır.

## İndirme ve SHA-256 doğrulama

Her sürüm, `.apk` dosyalarının yanında `SHA256SUMS` dosyasını da içerir. Kurulumdan önce bütünlüğü doğrulayabilirsiniz:

```bash
sha256sum -c SHA256SUMS --ignore-missing
```

Windows PowerShell üzerinde:

```powershell
Get-FileHash .\V4War-*-arm64-v8a.apk -Algorithm SHA256
```

## Upstream Throne veya NekoBox'tan geçiş

- V4War kendi uygulama imzasını kullanır. Upstream `ThroneForAndroid` veya `NekoBox` sürümünden geçiyorsanız önce `.thrbackup` yedeği alın (**Araçlar → Yedekleme**), ardından V4War'ı kurup yedeği geri yükleyin.
- Hotspot ve Proxy Paylaşımı içindeki parola/kimlik bilgileri, taşınabilir yedekleme sırasında `HotspotBackupSanitizer` tarafından otomatik olarak temizlenir.

## Uygulama içi güncelleyici ve Shizuku sessiz kurulum

V4War, GitHub Releases üzerindeki `v4war-update.json` dosyasını okuyan yerleşik bir güncelleme motoruna sahiptir:

1. **SHA-256 ve İmza Sertifikası Doğrulama** — İndirilen APK, kurulum başlamadan önce hem `SHA-256` özetiyle hem de V4War imza sertifikası piniyle doğrulanır.
2. **Sürüm Notları ve Token Desteği** — `CHANGELOG.md` sürüm notlarını gösterir ve GitHub API hız limitleri için isteğe bağlı token desteği sunar.
3. **Shizuku ile Sessiz Arka Plan Kurulumu** — Shizuku yetkiliyse `UpdateUserService`, sistem paket yükleyici ekranı açmadan güncellemeyi arka planda sessizce kurar.
4. **Obtainium Uyumlu** — `https://github.com/theyusa/V4War` adresini doğrudan Obtainium üzerinden de takip edebilirsiniz.
