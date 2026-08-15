package libcore

import (
	"context"
	"net"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	N "github.com/sagernet/sing/common/network"
)

func TestRunURLTestSuccess(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	latency, err := runURLTest(ctx, srv.URL, N.SystemDialer, urlTestUnifiedDelay)
	if err != nil {
		t.Fatalf("runURLTest failed: %v", err)
	}
	if latency == 0 {
		t.Fatal("expected non-zero latency")
	}
}

func TestRunURLTestConnectionRefused(t *testing.T) {
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	addr := l.Addr().String()
	_ = l.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if _, err = runURLTest(ctx, "http://"+addr+"/", N.SystemDialer, 0); err == nil {
		t.Fatal("expected connection refused error")
	}
}

func TestRunURLTestTimeout(t *testing.T) {
	// A listener that never accepts keeps the connect in the backlog and never
	// responds to the HTTP request, so the test must fail on the context deadline.
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer l.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 200*time.Millisecond)
	defer cancel()

	if _, err = runURLTest(ctx, "http://"+l.Addr().String()+"/", N.SystemDialer, 0); err == nil {
		t.Fatal("expected timeout error")
	}
}

func TestRunURLTestInvalidURL(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if _, err := runURLTest(ctx, "://not-a-url", N.SystemDialer, 0); err == nil {
		t.Fatal("expected invalid URL error")
	}
}
