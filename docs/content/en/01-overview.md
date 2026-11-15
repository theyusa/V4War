---
title: Overview
description: What V4War for Android is, how it builds on ThroneForAndroid, and what makes it different.
order: 1
group: get-started
---

# Overview

**V4War for Android** is an actively maintained, feature-rich fork of [throneproj/ThroneForAndroid](https://github.com/throneproj/ThroneForAndroid) built upon unified **Sing-box** and **Xray** cores and licensed under **GNU GPL-3.0**.

It combines upstream compatibility and daily upstream synchronization with major Android-specific additions designed for power users, mobile tethering, and network diagnostics.

## What V4War adds on top of upstream

- **Hotspot & Proxy Share Suite** — Share your active VPN tunnel over **Wi-Fi Hotspot**, **USB RNDIS**, or **Bluetooth PAN** using **Auto**, **Shizuku TUN (Rootless UID 2000)**, **Root (`iptables`)**, or **LAN Proxy** (`HTTP` / `SOCKS5`), complete with live client tracking and diagnostic logs.
- **Zero-Config Desktop Pairing** — Pair directly with [theyusa/Throne](https://github.com/theyusa/Throne) on Windows or Linux via **Hotspot Gateway** auto-discovery without typing an IP address or port.
- **10-in-1 Network Toolbox** — Built-in diagnostic engines under **Tools → Network & Tools**, including RFC 5389/5780 STUN NAT discovery, DNS Leak & Fake-IP check, dual-exit IP Lookup, DNS Lookup, Local Ping with live RTT charts, 24+ service Connectivity Test, Live Network Interfaces, Traffic Usage analytics, and Cellular Radio Info.
- **Material 3 Expressive & Theme Studio** — Live miniature preview, 5 surface styles (**Pure AMOLED**, **AMOLED**, **Dark**, **Light**, **System**), Monochrome themes (**Black**, **White**, **Light Gray**), Android 12+ **Material You**, and 21+ curated seed colors plus custom HEX/RGB tuning.
- **Shizuku Silent Updater & Hardened Defaults** — Default **TLS record fragmentation** enabled out of the box, plus SHA-256 & signing-certificate-pinned `v4war-update.json` checks with silent background installation via Shizuku.

## Supported platforms & requirements

| Specification | Value |
|---|---|
| Minimum Android | **Android 7.0 (API 24)** |
| Target SDK | **Android 15 (API 35)** |
| Architectures | `arm64-v8a`, `armeabi-v7a`, `x86_64` |
| Form Factors | Phones, Tablets, **Android TV** (D-pad navigation) |
| Cores | **Sing-box** & **Xray-core** |
| Root Requirement | **Optional** (Shizuku TUN & LAN Proxy work without root) |

## Where to go next

1. Follow **[Installation & Upgrade](/docs/installation)** to pick the right APK and verify checksums.
2. Follow **[Quick Start](/docs/quick-start)** to import your first subscription or profile and connect.
3. Explore **[Hotspot & Proxy Share](/docs/hotspot-share)** to share your VPN to laptops, consoles, or other phones.
