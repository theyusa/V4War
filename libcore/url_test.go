package libcore

import "testing"

func Test_ParseUrl(t *testing.T) {
	type args struct {
		rawURL string
	}
	tests := []struct {
		name    string
		args    args
		isWant  func(u URL) bool
		wantErr bool
	}{
		{
			name: "no port",
			args: args{
				rawURL: "hysteria2://ganggang@icecreamsogood/",
			},
			isWant: func(u URL) bool {
				if u == nil {
					return false
				}
				if u.GetScheme() != "hysteria2" {
					return false
				}
				if u.GetUsername() != "ganggang" {
					return false
				}
				if u.GetHost() != "icecreamsogood" {
					return false
				}
				return true
			},
		},
		{
			name: "single port",
			args: args{
				rawURL: "hysteria2://yesyes@icecreamsogood:8888/",
			},
			isWant: func(u URL) bool {
				if u == nil {
					return false
				}
				if u.GetScheme() != "hysteria2" {
					return false
				}
				if u.GetUsername() != "yesyes" {
					return false
				}
				if u.GetHost() != "icecreamsogood" {
					return false
				}
				if u.GetPorts() != "8888" {
					return false
				}
				return true
			},
		},
		{
			name: "multi port",
			args: args{
				rawURL: "hysteria2://darkness@laplus.org:8888,9999,11111/",
			},
			isWant: func(u URL) bool {
				if u == nil {
					return false
				}
				if u.GetScheme() != "hysteria2" {
					return false
				}
				if u.GetUsername() != "darkness" {
					return false
				}
				if u.GetHost() != "laplus.org" {
					return false
				}
				if u.GetPorts() != "8888,9999,11111" {
					return false
				}
				return true
			},
		},
		{
			name: "range port",
			args: args{
				rawURL: "hysteria2://darkness@laplus.org:8888-9999/",
			},
			isWant: func(u URL) bool {
				if u == nil {
					return false
				}
				if u.GetScheme() != "hysteria2" {
					return false
				}
				if u.GetUsername() != "darkness" {
					return false
				}
				if u.GetHost() != "laplus.org" {
					return false
				}
				if u.GetPorts() != "8888-9999" {
					return false
				}
				return true
			},
		},
		{
			name: "both",
			args: args{
				rawURL: "hysteria2://gawr:gura@atlantis.moe:443,7788-8899,10010/",
			},
			isWant: func(u URL) bool {
				if u == nil {
					return false
				}
				if u.GetScheme() != "hysteria2" {
					return false
				}
				if u.GetUsername() != "gawr" {
					return false
				}
				if u.GetPassword() != "gura" {
					return false
				}
				if u.GetHost() != "atlantis.moe" {
					return false
				}
				if u.GetPorts() != "443,7788-8899,10010" {
					return false
				}
				return true
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := ParseURL(tt.args.rawURL)
			if (err != nil) != tt.wantErr {
				t.Errorf("Parse() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !tt.isWant(got) {
				t.Errorf("Failed to parse, got: %s", got.GetString())
			}
		})
	}
}

func Test_SetHostAndPorts(t *testing.T) {
	// SetHost must not add a trailing colon when the URL has no port.
	u, err := ParseURL("https://example.com/path")
	if err != nil {
		t.Fatal(err)
	}
	u.SetHost("new.example.com")
	if got := u.GetFullHost(); got != "new.example.com" {
		t.Errorf("SetHost(no port) = %q, want %q", got, "new.example.com")
	}

	// SetHost must preserve an existing port.
	u2, err := ParseURL("https://example.com:443/path")
	if err != nil {
		t.Fatal(err)
	}
	u2.SetHost("new.example.com")
	if got := u2.GetFullHost(); got != "new.example.com:443" {
		t.Errorf("SetHost(with port) = %q, want %q", got, "new.example.com:443")
	}

	// SetPorts must not produce a leading colon when the URL has no port.
	u3, err := ParseURL("https://example.com/path")
	if err != nil {
		t.Fatal(err)
	}
	u3.SetPorts("8443")
	if got := u3.GetFullHost(); got != "example.com:8443" {
		t.Errorf("SetPorts(no port) = %q, want %q", got, "example.com:8443")
	}

	// SetPorts must replace an existing port.
	u4, err := ParseURL("https://example.com:443/path")
	if err != nil {
		t.Fatal(err)
	}
	u4.SetPorts("8443")
	if got := u4.GetFullHost(); got != "example.com:8443" {
		t.Errorf("SetPorts(with port) = %q, want %q", got, "example.com:8443")
	}
}
