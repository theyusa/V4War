//go:build with_xhttp

package libcore

import "testing"

// Test_CheckConfig_XHTTP verifies that a sing-box config using the V2Ray
// "xhttp" transport is accepted by the pinned sing-box-lx core when built with
// the with_xhttp tag. This guards the load-bearing contract that V4War's
// generated XHTTP config (type "xhttp", host/path/mode/headers) parses and
// constructs a box. If with_xhttp is dropped from build.sh TAGS, or the
// Leadaxe/sing-box-lx fork changes the transport registration, this test
// fails with "unknown transport type: xhttp".
func Test_CheckConfig_XHTTP(t *testing.T) {
	const config = `{
  "outbounds": [
    {
      "type": "vless",
      "tag": "proxy",
      "server": "example.com",
      "server_port": 443,
      "uuid": "0f9d4c7e-6e5b-4a1d-9c3f-2b8a7d1e4c9a",
      "tls": {
        "enabled": true,
        "server_name": "example.com"
      },
      "transport": {
        "type": "xhttp",
        "host": "example.com",
        "path": "/xhttp",
        "mode": "auto"
      }
    }
  ]
}`
	if err := CheckConfig(config); err != nil {
		t.Fatalf("XHTTP config rejected by core: %v", err)
	}
}
