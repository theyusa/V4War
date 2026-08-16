module libcore

go 1.26

require (
	github.com/gofrs/uuid/v5 v5.5.1
	github.com/klauspost/compress v1.19.1
	github.com/miekg/dns v1.1.72
	github.com/sagernet/sing v0.9.0-beta.2
	github.com/sagernet/sing-box v1.14.0-alpha.18
	github.com/sagernet/sing-tun v0.8.12-0.20260810140529-d67734281390
	github.com/sagernet/sing-vmess v0.2.8-0.20250909125414-3aed155119a1
	github.com/xchacha20-poly1305/TLS-scribe v0.12.1
	github.com/xchacha20-poly1305/anchor v0.7.1
	github.com/xchacha20-poly1305/anja v0.22.12
	github.com/xchacha20-poly1305/libping v0.10.1
	github.com/xchacha20-poly1305/sing-trusttunnel v0.2.1-0.20260314153016-08d91e19d8c2
	golang.org/x/sync v0.22.0
	golang.org/x/sys v0.47.0
	google.golang.org/protobuf v1.36.11
)

tool (
	github.com/xchacha20-poly1305/anja/cmd/anja
	github.com/xchacha20-poly1305/anja/cmd/anjb
)

// XHTTP transport (type "xhttp") is not in upstream sing-box; use the
// Leadaxe/sing-box-lx fork (upstream v1.14.0 + XHTTP, gated by with_xhttp).
replace github.com/sagernet/sing-box => github.com/Leadaxe/sing-box-lx v1.14.0-lx.26

// replace github.com/sagernet/sing-vmess => github.com/xchacha20-poly1305/sing-vmess v0.2.7-0.20260305142916-7ad18fe0e78b

// cmd
require (
	github.com/oschwald/geoip2-golang v1.11.0
	github.com/oschwald/maxminddb-golang v1.13.1
	github.com/v2fly/v2ray-core/v5 v5.48.0
)

require (
	github.com/adrg/xdg v0.5.3 // indirect
	github.com/ajg/form v1.5.1 // indirect
	github.com/andybalholm/brotli v1.1.0 // indirect
	github.com/caddyserver/certmagic v0.25.3-0.20260421143802-60d9d8b415d6 // indirect
	github.com/caddyserver/zerossl v0.1.5 // indirect
	github.com/database64128/netx-go v0.1.1 // indirect
	github.com/database64128/tfo-go/v2 v2.3.2 // indirect
	github.com/florianl/go-nfqueue/v2 v2.1.0 // indirect
	github.com/fsnotify/fsnotify v1.9.0 // indirect
	github.com/go-chi/chi/v5 v5.2.5 // indirect
	github.com/go-chi/render v1.0.3 // indirect
	github.com/go-ole/go-ole v1.3.0 // indirect
	github.com/gobwas/httphead v0.1.0 // indirect
	github.com/gobwas/pool v0.2.1 // indirect
	github.com/godbus/dbus/v5 v5.2.2 // indirect
	github.com/golang/protobuf v1.5.4 // indirect
	github.com/google/btree v1.1.3 // indirect
	github.com/google/go-cmp v0.7.0 // indirect
	github.com/google/gopacket v1.1.19 // indirect
	github.com/hashicorp/yamux v0.1.2 // indirect
	github.com/huin/goupnp v1.3.0 // indirect
	github.com/jackpal/go-nat-pmp v1.0.2 // indirect
	github.com/jsimonetti/rtnetlink v1.4.1 // indirect
	github.com/klauspost/cpuid/v2 v2.3.0 // indirect
	github.com/koron/go-ssdp v0.0.4 // indirect
	github.com/libdns/acmedns v0.5.0 // indirect
	github.com/libdns/alidns v1.0.6 // indirect
	github.com/libdns/cloudflare v0.2.2 // indirect
	github.com/libdns/libdns v1.1.1 // indirect
	github.com/libp2p/go-nat v1.0.1-0.20250821073202-01afc089f138 // indirect
	github.com/libp2p/go-netroute v0.2.1 // indirect
	github.com/logrusorgru/aurora v2.0.3+incompatible // indirect
	github.com/mdlayher/netlink v1.11.2 // indirect
	github.com/mdlayher/socket v0.6.0 // indirect
	github.com/metacubex/utls v1.8.7 // indirect
	github.com/mholt/acmez/v3 v3.1.6 // indirect
	github.com/quic-go/qpack v0.6.0 // indirect
	github.com/sagernet/bbolt v0.0.0-20231014093535-ea5cb2fe9f0a // indirect
	github.com/sagernet/fswatch v0.1.2 // indirect
	github.com/sagernet/gvisor v0.0.0-20250909151924-850a370d8506 // indirect
	github.com/sagernet/netlink v0.0.0-20240612041022-b9a21c07ac6a // indirect
	github.com/sagernet/nftables v0.3.0-mod.4 // indirect
	github.com/sagernet/quic-go v0.61.0-sing-box-mod.4 // indirect
	github.com/sagernet/sing-mux v0.3.5 // indirect
	github.com/sagernet/sing-quic v0.7.0-beta.2 // indirect
	github.com/sagernet/sing-shadowsocks v0.2.8 // indirect
	github.com/sagernet/sing-shadowsocks2 v0.2.1 // indirect
	github.com/sagernet/sing-usbip v0.0.0-20260616101517-efb91521eddb // indirect
	github.com/sagernet/smux v1.5.50-sing-box-mod.1 // indirect
	github.com/sagernet/ws v0.0.0-20231204124109-acfe8907c854 // indirect
	github.com/vishvananda/netns v0.0.5 // indirect
	github.com/zeebo/blake3 v0.2.4 // indirect
	go.uber.org/multierr v1.11.0 // indirect
	go.uber.org/zap v1.27.1 // indirect
	go.uber.org/zap/exp v0.3.0 // indirect
	go4.org/netipx v0.0.0-20231129151722-fdeea329fbba // indirect
	golang.org/x/crypto v0.54.0 // indirect
	golang.org/x/exp v0.0.0-20260410095643-746e56fc9e2f // indirect
	golang.org/x/mod v0.37.0 // indirect
	golang.org/x/net v0.57.0 // indirect
	golang.org/x/text v0.40.0 // indirect
	golang.org/x/time v0.15.0 // indirect
	golang.org/x/tools v0.47.0 // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20251202230838-ff82c1b0f217 // indirect
	google.golang.org/grpc v1.79.3 // indirect
	lukechampine.com/blake3 v1.4.1 // indirect
)
