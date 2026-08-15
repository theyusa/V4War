package libcore

import (
	"context"
	"errors"
	"net"
	"net/http"
	"net/http/httptest"
	"testing"

	M "github.com/sagernet/sing/common/metadata"
)

// redirectDialer implements N.Dialer by always dialing a fixed target,
// ignoring the requested destination. It exercises the URL test's request and
// latency measurement path without a real proxy.
type redirectDialer struct {
	target string
}

func (d redirectDialer) DialContext(ctx context.Context, network string, destination M.Socksaddr) (net.Conn, error) {
	return net.Dial("tcp", d.target)
}

func (d redirectDialer) ListenPacket(ctx context.Context, destination M.Socksaddr) (net.PacketConn, error) {
	return nil, errors.New("not supported")
}

// errorDialer always fails the dial, exercising error propagation.
type errorDialer struct{}

func (errorDialer) DialContext(ctx context.Context, network string, destination M.Socksaddr) (net.Conn, error) {
	return nil, errors.New("dial tcp: connect: connection refused")
}

func (errorDialer) ListenPacket(ctx context.Context, destination M.Socksaddr) (net.PacketConn, error) {
	return nil, errors.New("not supported")
}

func TestURLTest_Success(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	latency, err := urlTest(context.Background(), server.URL, redirectDialer{target: server.Listener.Addr().String()}, 0)
	if err != nil {
		t.Fatalf("expected success, got error: %v", err)
	}
	if latency < 0 {
		t.Fatalf("expected non-negative latency, got %d", latency)
	}
}

func TestURLTest_UnifiedDelay(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	}))
	defer server.Close()

	// UnifiedDelay runs the request twice and reports the second latency.
	latency, err := urlTest(context.Background(), server.URL, redirectDialer{target: server.Listener.Addr().String()}, URLTestUnifiedDelay)
	if err != nil {
		t.Fatalf("expected success, got error: %v", err)
	}
	if latency < 0 {
		t.Fatalf("expected non-negative latency, got %d", latency)
	}
}

func TestURLTest_DialError(t *testing.T) {
	_, err := urlTest(context.Background(), "http://example.com/", errorDialer{}, 0)
	if err == nil {
		t.Fatal("expected error when dialer fails")
	}
}

func TestURLTest_CanceledContext(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	_, err := urlTest(ctx, "http://example.com/", redirectDialer{target: "127.0.0.1:1"}, 0)
	if err == nil {
		t.Fatal("expected error from canceled context")
	}
}

func TestURLTest_InvalidLink(t *testing.T) {
	_, err := urlTest(context.Background(), "://not-a-url", errorDialer{}, 0)
	if err == nil {
		t.Fatal("expected error parsing invalid link")
	}
}
