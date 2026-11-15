---
title: Installation & Upgrade
description: Download the right V4War APK, verify SHA-256 checksums, and use the Shizuku Silent Updater.
order: 2
group: get-started
---

# Installation & Upgrade

V4War distributes pre-built, reproducible APKs through [GitHub Releases](https://github.com/theyusa/V4War/releases).

## Choose the right APK

Pick the package matching your device CPU architecture:

| APK Architecture | Target Devices |
|---|---|
| **`arm64-v8a`** *(Recommended)* | Almost all modern Android phones, tablets, and newer Android TV boxes (2015+) |
| **`armeabi-v7a`** | Older 32-bit ARM phones and legacy Android TV sticks |
| **`x86_64`** | Android emulators, Chromebooks, and x86_64 tablets |

> **Tip:** If you are unsure which ABI your phone uses, start with `arm64-v8a`. Android will refuse incompatible ABIs cleanly during package verification.

## Download & verify SHA-256

Every release includes a `SHA256SUMS` file alongside the `.apk` assets. Verify your download before installing:

```bash
sha256sum -c SHA256SUMS --ignore-missing
```

On Windows PowerShell:

```powershell
Get-FileHash .\V4War-*-arm64-v8a.apk -Algorithm SHA256
```

## Upgrading from upstream Throne or NekoBox

- V4War uses its own package signature. If you are migrating from upstream `ThroneForAndroid` or `NekoBox`, export a `.thrbackup` backup first (**Tools → Backup**), install V4War, and restore your backup.
- Credentials inside Hotspot & Proxy Share are automatically stripped from portable backups by `HotspotBackupSanitizer` so sensitive tokens are never leaked across devices.

## In-app updater & Shizuku silent updates

V4War includes a dedicated update engine backed by `v4war-update.json` on GitHub Releases:

1. **Checksum & Certificate Pinning** — Downloaded APKs are verified against both the release `SHA-256` hash and V4War's signing certificate pin before installation begins.
2. **Release Notes & Token Support** — Displays parsed release notes from `CHANGELOG.md` and supports an optional GitHub API token to avoid rate limits.
3. **Shizuku Silent Background Install** — When Shizuku is authorized, `UpdateUserService` installs verified updates in the background without popping up the system package installer prompt.
4. **Obtainium Compatible** — You can also track `https://github.com/theyusa/V4War` directly inside Obtainium.
