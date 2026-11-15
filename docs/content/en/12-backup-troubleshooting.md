---
title: Backup, Upstream Sync & Troubleshooting
description: Cross-platform .thrbackup & WebDAV sync, credential sanitization, daily upstream sync, and Android troubleshooting fixes.
order: 12
group: advanced
---

# Backup, Upstream Sync & Troubleshooting

## Cross-platform backups & WebDAV

Open **Tools → Backup** (or **Drawer → Tools → Backup**) to export or restore `.thrbackup` archives:

- **WebDAV Sync** — Shares the same backup list schema with [theyusa/Throne](https://github.com/theyusa/Throne) desktop, displaying device name, platform, app version, and contents across your devices.
- **Automatic Credential Sanitization** — `HotspotBackupSanitizer` automatically strips sensitive Hotspot/Proxy authentication credentials and tokens from portable backups.

## Daily automated upstream sync

V4War stays continuously aligned with [throneproj/ThroneForAndroid](https://github.com/throneproj/ThroneForAndroid) via `.github/workflows/sync-upstream.yml`:

- Runs daily at **03:00 UTC** (plus manual dispatch).
- Rebases V4War commits cleanly onto `upstream/main` and runs `:app:testOssDebugUnitTest` before publishing.

## Troubleshooting checklist

### VPN fails to start or has no traffic
1. Open **Tools → Network → Local Ping** and **IP Lookup** to verify whether the core socket connects.
2. Check **Logs** inside the drawer for TLS handshake, Reality public key, or clock-skew errors.
3. Verify **Settings → DNS** — ensure Remote DNS is reachable through the proxy (avoid unreachable UDP resolvers if your server blocks UDP).

### V4War stops in the background
- Disable battery optimization (**Unrestricted**), lock the app in Recents, and allow **Notifications**.

### Shizuku Hotspot mode stops after screen lock or reboot
- Enable Wireless Debugging or `adb tcpip 5555` so V4War's **Local ADB Auto-Recovery** can re-authorize Shizuku automatically.
- Check **Hotspot & Proxy Share → Logs** to inspect `hev-socks5-tunnel` and `UserService` output.

### DNS leaks or ISP blocks still occur
- Run **Tools → Network → DNS Leak & Fake-IP Check**.
- Disable Android System **Private DNS** (or align it) and enable **Fake-IP** + **Strict Route** under **Settings → TUN / VPN**.
