package libcore

import (
	"context"
	"runtime/debug"
	"time"

	"github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/experimental/deprecated"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	F "github.com/sagernet/sing/common/format"
	"github.com/sagernet/sing/common/observable"
	"github.com/sagernet/sing/service"
	"github.com/sagernet/sing/service/pause"

	"github.com/xchacha20-poly1305/anchor/anchorservice"

	"libcore/combinedapi/trafficcontrol"
	"libcore/protect"
)

type clashServerWrapper struct {
	adapter.ClashServer
}

func (w *clashServerWrapper) QueryStats(name string, isUpload bool) int64 {
	if w.ClashServer == nil {
		return 0
	}
	type statsGetter interface {
		QueryStats(name string, isUpload bool) int64
	}
	if sg, ok := w.ClashServer.(statsGetter); ok {
		return sg.QueryStats(name, isUpload)
	}
	return 0
}

func (w *clashServerWrapper) TrafficManager() *trafficcontrol.Manager {
	if w.ClashServer == nil {
		return nil
	}
	type trafficManagerGetter interface {
		TrafficManager() *trafficcontrol.Manager
	}
	if tm, ok := w.ClashServer.(trafficManagerGetter); ok {
		return tm.TrafficManager()
	}
	return nil
}

func (w *clashServerWrapper) SetMode(mode string) {
	if w.ClashServer == nil {
		return
	}
	type setModeSetter interface {
		SetMode(mode string)
	}
	if sm, ok := w.ClashServer.(setModeSetter); ok {
		sm.SetMode(mode)
	}
}

func (w *clashServerWrapper) ModeList() []string {
	if w.ClashServer == nil {
		return nil
	}
	type modeLister interface {
		ModeList() []string
	}
	if ml, ok := w.ClashServer.(modeLister); ok {
		return ml.ModeList()
	}
	return nil
}

func (w *clashServerWrapper) Mode() string {
	if w.ClashServer == nil {
		return ""
	}
	type moder interface {
		Mode() string
	}
	if m, ok := w.ClashServer.(moder); ok {
		return m.Mode()
	}
	return ""
}

func (w *clashServerWrapper) SetModeUpdateHook(hook *observable.Subscriber[struct{}]) {
	if w.ClashServer == nil {
		return
	}
	type hookSetter interface {
		SetModeUpdateHook(hook *observable.Subscriber[struct{}])
	}
	if hs, ok := w.ClashServer.(hookSetter); ok {
		hs.SetModeUpdateHook(hook)
	}
}

func (w *clashServerWrapper) HistoryStorage() adapter.URLTestHistoryStorage {
	if w.ClashServer == nil {
		return nil
	}
	type historyGetter interface {
		HistoryStorage() adapter.URLTestHistoryStorage
	}
	if hg, ok := w.ClashServer.(historyGetter); ok {
		return hg.HistoryStorage()
	}
	return nil
}

type boxInstance struct {
	ctx    context.Context
	cancel context.CancelFunc
	*box.Box
	forTest bool

	platformInterface PlatformInterface
	protect           *protect.Service
	api               *clashServerWrapper
	anchor            *anchorservice.Anchor

	pauseManager pause.Manager
}

// newBoxInstance creates a new boxInstance.
func newBoxInstance(config string, platformInterface PlatformInterface, forTest bool) (b *boxInstance, err error) {
	defer catchPanic("NewSingBoxInstance", func(panicErr error) { err = panicErr })

	ctx := baseContext(platformInterface)
	options, err := parseConfig(ctx, config)
	if err != nil {
		return nil, err
	}

	ctx, cancel := context.WithCancel(ctx)
	ctx = pause.WithDefaultManager(ctx)
	var platformLogWriter log.PlatformWriter
	interfaceWrapper := &boxPlatformInterfaceWrapper{
		useProcFS: platformInterface.UseProcFS(),
		forTest:   forTest,
	}
	if platformInterface.HasCoreFunction() {
		interfaceWrapper.iif = platformInterface
		service.MustRegister[adapter.PlatformInterface](ctx, interfaceWrapper)
	}

	if !forTest {
		service.MustRegister[deprecated.Manager](ctx, deprecated.NewStderrManager(log.StdLogger()))
		// If set PlatformLogWrapper, box will set something about cache file,
		// which will panic with simple configuration (when URL test).
		platformLogWriter = platformLogWrapper
	}

	boxOption := box.Options{
		Options:           options,
		Context:           ctx,
		PlatformLogWriter: platformLogWriter,
	}

	instance, err := box.New(boxOption)
	if err != nil {
		cancel()
		return nil, E.Cause(err, "create service")
	}

	b = &boxInstance{
		ctx:               ctx,
		Box:               instance,
		forTest:           forTest,
		cancel:            cancel,
		platformInterface: platformInterface,
		pauseManager:      service.FromContext[pause.Manager](ctx),
	}

	if !forTest {
		// Protect
		if C.IsAndroid {
			b.protect, err = protect.New(log.ContextWithNewID(ctx), logFactory.NewLogger("protect"), ProtectPath, func(fd int) error {
				_ = platformInterface.AutoDetectInterfaceControl(int32(fd))
				return nil
			})
			if err != nil {
				log.WarnContext(ctx, "create protect service: ", err)
			}
		}

		// API
		clashApi := service.FromContext[adapter.ClashServer](b.ctx)
		if clashApi != nil {
			b.api = &clashServerWrapper{clashApi}
		} else {
			b.api = &clashServerWrapper{nil}
		}

		// Anchor
		socksPort, dnsPort := sharedPublicPort(options.Inbounds)
		if socksPort > 0 {
			b.anchor, err = b.createAnchor(socksPort, dnsPort)
			if err != nil {
				log.WarnContext(b.ctx, "create anchor: ", err)
			}
		}
	}

	return b, nil
}

func (b *boxInstance) Start() (err error) {
	defer catchPanic("box.Start", func(panicErr error) { err = panicErr })

	err = b.Box.Start()
	if err != nil {
		return err
	}

	if b.protect != nil {
		// Never return error
		_ = b.protect.Start()
	}
	if b.anchor != nil {
		err = b.anchor.Start()
		if err != nil {
			return E.Cause(err, "start anchor service")
		}
	}

	if !b.forTest {
		debug.FreeOSMemory()
	}

	return nil
}

func (b *boxInstance) Close() (err error) {
	return b.CloseTimeout(C.FatalStopTimeout)
}

func (b *boxInstance) CloseTimeout(timeout time.Duration) (err error) {
	defer catchPanic("boxInstance.Close", func(panicErr error) { err = panicErr })

	_ = common.Close(
		common.PtrOrNil(b.protect),
		common.PtrOrNil(b.anchor),
	)

	done := make(chan error, 1)
	start := time.Now()
	go func() {
		defer catchPanic("box.Close", func(panicErr error) { done <- panicErr })
		b.cancel()
		done <- b.Box.Close()
	}()
	select {
	case <-time.After(timeout):
		return E.New("sing-box did not close in time")
	case err = <-done:
		if !b.forTest {
			log.Info("sing-box closed in ", F.Seconds(time.Since(start).Seconds()), " s.")
		}
		return
	}
}

func (b *boxInstance) NeedWIFIState() bool {
	return b.anchor != nil || b.Box.Network().NeedWIFIState()
}

func (b *boxInstance) QueryStats(tag string, isUpload bool) int64 {
	if b.api == nil {
		return 0
	}
	return b.api.QueryStats(tag, isUpload)
}

func (b *boxInstance) historyStorage() adapter.URLTestHistoryStorage {
	if b.api == nil {
		return nil
	}
	return b.api.HistoryStorage()
}
