// Package pluginoption provides options for hooked protocols.
package pluginoption

import (
	C "github.com/sagernet/sing-box/constant"
)

func ProxyDisplayName(proxyType string) string {
	return C.ProxyDisplayName(proxyType)
}