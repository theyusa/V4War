---
title: Permissions, Background & Android TV
description: Keep V4War alive in the background, use Quick Settings tiles, widgets, and Android TV navigation.
order: 4
group: android
---

# Permissions, Background & Android TV

Android OEMs aggressively kill background services unless battery and notification permissions are configured properly.

## Required & recommended permissions

1. **VPN Permission** — Requested by Android the first time you start VPN (TUN) mode. Required to route system traffic through Sing-box/Xray.
2. **Notifications (`POST_NOTIFICATIONS`)** — Required on Android 13+ so the foreground VPN service notification and live speed readout remain visible.
3. **Unrestricted Battery Usage** — Disable battery optimization for V4War in **Android Settings → Apps → V4War → Battery → Unrestricted**.
4. **OEM Autostart / Lock App** — On MIUI/HyperOS, ColorOS, OneUI, or EMUI, pin V4War in Recents and enable background autostart if available.

## Always-on VPN & Bypass

- **Always-on VPN** — Enable in **Android Settings → Network & Internet → VPN → V4War** to let Android start V4War automatically on boot.
- **Allow apps to bypass VPN** — Under **Settings → General**, enable this option if system push services (such as FCM) or banking apps should bypass the tunnel when requested (disabled when Android's *Block connections without VPN* is active).

## Widgets, Quick Settings tiles & notifications

- **Quick Settings Tiles** — Add V4War's VPN toggle tile and **Hotspot Share tile** (`HotspotShareTileService`) to your Android notification shade for one-tap control of VPN and tethering.
- **Home-Screen Widgets** — Place a compact widget to toggle the tunnel and monitor connection state without opening the app.
- **Notification Actions** — Customize notification bar buttons and toggle **Show direct speed** or **Show group in notification** under **Settings → Appearance**.

## Android TV support

V4War ships with native **Android TV** support:

- Full **D-pad / remote** focus navigation across profile cards, drawer menus, and settings.
- Dedicated Android TV launcher banner.
- Import profiles without a camera using **Clipboard**, **QR web/LAN transfer**, **Deep Links**, or **`.thrbackup` restore**.
