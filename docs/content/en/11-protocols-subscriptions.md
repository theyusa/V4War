---
title: Protocols & Subscriptions
description: Complete reference of V4War's 28+ supported proxy, QUIC, tunnel, DNS tunnel, and smart outbound protocols.
order: 11
group: routing-protocols
---

# Protocols & Subscriptions

V4War unifies **Sing-box** and **Xray** in a single client, supporting 28+ proxy, tunnel, QUIC, and DNS transport protocols. You can also search and filter all protocols live in the **[Protocols & Lab](/explore)** view.

## Supported protocol matrix

| Category | Protocols & Transports |
|---|---|
| **Proxy & TLS** | `VLESS` (Reality / TLS), `Xray VLESS` (XHTTP / mKCP / XDRIVE), `VMess`, `Trojan`, `Shadowsocks`, `SOCKS5`, `HTTP(S)`, `ShadowTLS v3`, `AnyTLS`, `NaïveProxy`, `TrustTunnel`, `Snell` |
| **QUIC / UDP** | `Hysteria 1/2`, `TUIC v5`, `Juicity`, `Mieru` |
| **VPN Tunnels** | `WireGuard` (incl. WARP), `AmneziaWG` (obfuscated), `MASQUE` (HTTP/3), `SSH`, `OpenVPN`, `OpenConnect` |
| **DNS Tunnels** | `DNSTT / VayDNS`, `MasterDNS / StormDNS`, `XDNS` |
| **Smart Outbounds** | `Direct`, `Chain` (Multi-Hop), `Auto Selector` (Multi-Group & Priority Tiers), `Custom Outbound` (JSON) |

## Subscriptions & import formats

- **Supported Formats** — Standard URI share links, **Sing-box JSON**, **v2rayN**, **Shadowsocks (SIP008)**, **Clash** outbound import, and **Happ** routing import.
- **Custom Subscription SNI** — Override the TLS SNI used when fetching subscription updates on censored networks.
- **Subscription Filters & Update Intervals** — Filter imported nodes by keyword/regex and configure per-group update intervals plus TLS/HTTP version settings.
- **Profile Pinning & Multi-Profile QR** — Pin favorite nodes to the top of groups and export multiple profiles together as QR codes.
- **Deep Links** — Supports `sn://` and `throne://` scheme links for quick one-tap imports.
