package libcore

import "testing"

func Test_clashServerWrapper_NilSafe(t *testing.T) {
	w := &clashServerWrapper{nil}

	if got := w.QueryStats("tag", false); got != 0 {
		t.Errorf("QueryStats() = %v, want 0", got)
	}
	if got := w.TrafficManager(); got != nil {
		t.Errorf("TrafficManager() = %v, want nil", got)
	}
	if got := w.ModeList(); got != nil {
		t.Errorf("ModeList() = %v, want nil", got)
	}
	if got := w.Mode(); got != "" {
		t.Errorf("Mode() = %q, want empty", got)
	}
	if got := w.HistoryStorage(); got != nil {
		t.Errorf("HistoryStorage() = %v, want nil", got)
	}

	// These must not panic when the wrapped server is nil.
	w.SetMode("rule")
	w.SetModeUpdateHook(nil)
}
