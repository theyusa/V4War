package libcore

import (
	"net"
	"strconv"
	"testing"
	"time"
)

// startTCPServer binds an ephemeral loopback listener and returns its port.
// The returned cleanup closes the listener.
func startTCPServer(t *testing.T) (port int, cleanup func()) {
	t.Helper()
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Skipf("cannot listen on loopback: %v", err)
	}
	port = listener.Addr().(*net.TCPAddr).Port
	return port, func() { _ = listener.Close() }
}

func TestTcpPing_Success(t *testing.T) {
	port, cleanup := startTCPServer(t)
	defer cleanup()

	// The listening socket's kernel backlog completes the handshake without an
	// explicit Accept call, so TcpPing must measure a valid non-negative delay.
	latency, err := TcpPing("127.0.0.1", strconv.Itoa(port), 3000)
	if err != nil {
		t.Fatalf("expected success, got error: %v", err)
	}
	if latency < 0 {
		t.Fatalf("expected non-negative latency, got %d", latency)
	}
}

func TestTcpPing_ConnectionRefused(t *testing.T) {
	// Reserve an ephemeral port, then release it so nothing is listening.
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Skipf("cannot listen on loopback: %v", err)
	}
	port := listener.Addr().(*net.TCPAddr).Port
	_ = listener.Close()

	_, err = TcpPing("127.0.0.1", strconv.Itoa(port), 3000)
	if err == nil {
		t.Fatalf("expected error connecting to closed port %d", port)
	}
}

func TestTcpPing_Timeout(t *testing.T) {
	// 240.0.0.1 is in the reserved 240/4 block and is never routed, so the
	// SYN is dropped and the dial must expire via the context timeout.
	const timeout = 500
	start := time.Now()
	_, err := TcpPing("240.0.0.1", "80", timeout)
	elapsed := time.Since(start)

	if err == nil {
		t.Fatal("expected timeout error dialing a reserved 240/4 address")
	}
	if elapsed < time.Duration(timeout)*time.Millisecond {
		t.Fatalf("TcpPing returned before its timeout: elapsed %v, timeout %v", elapsed, timeout)
	}
	if elapsed > 5*time.Second {
		t.Fatalf("TcpPing exceeded its timeout bound: elapsed %v", elapsed)
	}
}

func TestTcpPing_InvalidHost(t *testing.T) {
	// RFC 2606 reserves .invalid so name resolution must fail.
	_, err := TcpPing("does-not-exist.invalid", "80", 1000)
	if err == nil {
		t.Fatal("expected error resolving an .invalid hostname")
	}
}
