---
title: Quick Start
description: Add servers or subscriptions, test latency, choose routing, and start your first VPN session.
order: 3
group: get-started
---

# Quick Start

Follow these steps to go from a fresh install to a working, hardened VPN connection in under two minutes.

## 1. Add servers or subscriptions

Open the main **Profiles** screen and tap the **+** button in the top bar:

- **Scan QR Code** — Scan a single server or multi-profile QR code.
- **Import from Clipboard** — Paste `vless://`, `vmess://`, `trojan://`, `ss://`, `hy2://`, `tuic://`, `wg://`, `sn://`, or full **Sing-box JSON** / **Clash outbound** configurations.
- **Add Subscription** — Open **Drawer → Groups → +** to add a subscription URL. V4War supports standard base64/JSON subscriptions, **Happ** routing profiles, subscription filters, and **custom subscription SNI** overrides.
- **Deep Links** — Import profiles or subscriptions directly from `sn://` or `throne://` links.

## 2. Test server latency & reachability

1. Tap **⋮ → Connection Test** (URL test) or **TCP Ping** on a group to measure real proxy response times.
2. Use **Service Check** on any profile to verify which AI, streaming, and social services are reachable and which exit IP country is detected.
3. Optional: Create an **Auto Selector** profile to automatically rank and switch across one or multiple groups using priority tiers and real-connection learning.

## 3. Configure basic routing

Open **Drawer → Routing** to manage rule profiles:

- Select a preset or create custom rules blocking ads/trackers, bypassing local LAN/domestic traffic (`Direct`), or forcing specific apps/domains through `Proxy`.
- Use **Route Path Lookup** to test which rule and outbound a given app, domain, or IP will take before connecting.
- Groups can also override the global routing profile with their own **per-group routing profile** (configured in Group Settings).

## 4. Connect

1. Select a profile card on the home screen (highlighted with your Theme Studio seed accent bar).
2. Tap the **Send / Connect FAB** at the bottom right.
3. Accept the Android VPN connection prompt on first launch.
4. Live upload/download speeds (`↑ / ↓`) appear in the bottom stats pill immediately once the tunnel is active.

> **Security Note:** V4War enables **TLS Record Fragmentation** by default to improve resilience against SNI-based DPI filtering. You can fine-tune fragmentation or QUIC Opener behavior in **Settings → Presets** or per profile.
