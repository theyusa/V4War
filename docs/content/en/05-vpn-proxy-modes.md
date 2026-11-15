---
title: VPN, Proxy & DNS Modes
description: Understand VPN (TUN) vs Proxy-Only mode, Per-App Proxy, TUN parameters, and DNS configuration.
order: 5
group: android
---

# VPN, Proxy & DNS Modes

V4War supports two primary service modes under **Settings → General → Service Mode**, plus granular DNS and per-app controls.

## VPN mode vs Proxy-only mode

| Mode | How traffic is captured | Best for |
|---|---|---|
| **VPN (TUN)** *(Default)* | Creates an Android virtual `tun0` interface and captures device traffic transparently | Daily phone/tablet usage, games, apps that ignore system proxy, Hotspot TUN sharing |
| **Proxy Only** | Opens local `SOCKS5` / `HTTP` / `Mixed` inbound sockets without creating an Android VPN interface | Chaining alongside another VPN app, Root/ADB proxy workflows, or LAN-only proxy sharing |

## Per-App Proxy

Under **Settings → Route / Apps**, choose **Bypass** (only selected apps skip VPN) or **Proxy** (only selected apps use VPN). Useful for excluding local banking apps, streaming boxes, or LAN tools.

## TUN / VPN settings

Under **Settings → TUN / VPN**:

- **TUN Implementation** — Uses `gVisor`, `System`, or `Mixed` stack depending on device kernel compatibility.
- **Strict Route & Fake-IP** — Prevents DNS and non-TUN socket leaks by routing DNS queries through the core (`198.18.0.0/15` Fake-IP pool) and enforcing strict interface binding.
- **MTU** — Default WireGuard MTU is `1280` when unspecified; TUN MTU can be tuned for carrier/hotspot stability.

## DNS architecture

Under **Settings → DNS**:

- **Remote DNS & Direct DNS** — Separate resolvers for proxied vs direct traffic; supports **multiple DNS servers queried in parallel** for faster resolution.
- **Protocols** — `DoH` (DNS-over-HTTPS), `DoT` (DNS-over-TLS), `UDP`, `TCP`, and `Local`.
- **Per-Profile Routing DNS** — Individual routing profiles can override global Remote and Direct DNS servers.
- **System Private DNS Warning** — If Android's *Private DNS (DoT)* is set to a custom hostname, verify isolation using **[DNS Leak & Fake-IP Check](/docs/network-toolbox)** so queries do not bypass your proxy.
