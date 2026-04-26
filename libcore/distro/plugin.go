package distro

import (
	"libcore/plugin/vless"
	"libcore/plugin/plugindns"

	"github.com/sagernet/sing-box/adapter/outbound"
	"github.com/sagernet/sing-box/dns"
)

func registerPluginsOutbound(registry *outbound.Registry) {
	vless.RegisterOutbound(registry)
}

func registerPluginsDNSTransport(registry *dns.TransportRegistry) {
	plugindns.RegisterTCP(registry)
	plugindns.RegisterTLS(registry)
}