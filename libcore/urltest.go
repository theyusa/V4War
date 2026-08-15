package libcore

import (
	"cmp"
	"context"
	"crypto/tls"
	"net"
	"net/http"
	"net/url"
	"time"

	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/common/ntp"
)

// URL test measurement options.
const (
	// urlTestUnifiedDelay repeats the request on the established connection and
	// reports the warm round-trip latency instead of the cold first-request
	// latency. This is the same "real delay" strategy used by v2rayN/NekoBox
	// and removes the instability caused by a cold TLS session or proxy
	// negotiation on the very first request.
	urlTestUnifiedDelay uint8 = 1 << iota
	// urlTestIgnoreHandshakeTime restarts the latency clock after the
	// transport handshake (TLS/WebSocket/HTTPUpgrade) so that only the HTTP
	// round-trip is measured, matching sing-box's historical behavior.
	// When unset, latency covers DNS + TCP + handshake + HTTP.
	urlTestIgnoreHandshakeTime
)

// runURLTest probes `link` through `detour` and returns the latency in
// milliseconds. It is a drop-in replacement for
// github.com/sagernet/sing-box/common/urltest.URLTest with two behavioral
// fixes:
//
//  1. The clock starts before DialContext and is NOT reset after the dial, so
//     the reported latency represents the complete profile connection
//     (DNS + TCP + handshake + HTTP), not just the handshake+HTTP tail.
//  2. urlTestUnifiedDelay repeats the request on the already-established
//     connection and reports the warm latency, which is stable and matches
//     what steady-state traffic experiences.
func runURLTest(ctx context.Context, link string, detour N.Dialer, options uint8) (t uint16, err error) {
	link = cmp.Or(link, "https://www.gstatic.com/generate_204")
	linkURL, err := url.Parse(link)
	if err != nil {
		return
	}
	hostname := linkURL.Hostname()
	port := linkURL.Port()
	if port == "" {
		switch linkURL.Scheme {
		case "http":
			port = "80"
		case "https":
			port = "443"
		}
	}

	start := time.Now()
	instance, err := detour.DialContext(ctx, N.NetworkTCP, M.ParseSocksaddrHostPortStr(hostname, port))
	if err != nil {
		return
	}
	defer instance.Close()
	if options&urlTestIgnoreHandshakeTime != 0 && N.NeedHandshakeForWrite(instance) {
		start = time.Now()
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodHead, link, nil)
	if err != nil {
		return
	}
	client := http.Client{
		Transport: &http.Transport{
			DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
				return instance, nil
			},
			TLSClientConfig: &tls.Config{
				Time:    ntp.TimeFuncFromContext(ctx),
				RootCAs: adapter.RootPoolFromContext(ctx),
			},
		},
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			return http.ErrUseLastResponse
		},
		Timeout: C.TCPTimeout,
	}
	defer client.CloseIdleConnections()
	times := 1
	if options&urlTestUnifiedDelay != 0 {
		times++
	}
	for range times {
		var resp *http.Response
		resp, err = client.Do(req)
		if err != nil {
			return
		}
		t = uint16(time.Since(start) / time.Millisecond)
		_ = resp.Body.Close()
	}
	return
}
