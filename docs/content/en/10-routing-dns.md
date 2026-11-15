---
title: Routing, Rule Sets & DNS
description: Build Sing-box and Xray routing rules, use Route Path Lookup, per-group routing, TLS fragmentation, and WARP.
order: 10
group: routing-protocols
---

# Routing, Rule Sets & DNS

V4War provides full rule-based traffic steering powered by Sing-box & Xray rule engines.

## Managing routing profiles

Open **Drawer → Routing** to create, clone, import, or switch routing profiles:

- **Rule Order Matters** — Rules are evaluated top to bottom (`Domain`, `IP CIDR`, `Port`, `Process / Package Name`, `Rule Set`, `GeoIP / Geosite`).
- **Actions** — Route matched traffic to `Proxy`, `Direct`, `Block`, or a specific **Route Proxy Hop / Outbound**.
- **Toggle Rules Individually** — Enable or disable individual rules without deleting them.
- **Route Path Lookup** — Enter any app package, domain, or IP address to trace which exact rule and outbound will handle it.

## Per-group routing & Happ imports

- Open a group's settings to assign a **Per-Group Routing Profile** that overrides the global routing profile whenever you connect to a server in that group.
- Subscriptions providing a **Happ** routing profile can automatically sync into that group's routing profile on update (subscription DNS settings are safely ignored).
- Cloning a remote routing profile creates an independent local copy so subscription updates do not overwrite your edits.

## Anti-censorship presets & TLS fragmentation

- **Default TLS Record Fragmentation** — Enabled by default in V4War to harden fake SNI and CDN-fronting connections against DPI inspection.
- **QUIC Opener** — Assists HTTP/3 protocols (`MASQUE`, `Hysteria2`, `TUIC`) on networks that drop initial QUIC handshakes.
- **Presets (`Settings → Presets`)** — Configure reusable TLS fragment, Multiplex (`TCP Brutal`, `XUDP`), and transport presets across profiles.

## Cloudflare WARP & Chains

- Built-in **Cloudflare WARP** generator supporting **WireGuard**, **AmneziaWG** (with QUIC signature obfuscation), and **MASQUE (HTTP/3)** with TLS fragmentation during registration.
- **Chain Outbounds** — Combine multiple hops (including an **Auto Selector** as a hop) and run **hop-by-hop chain testing**.
