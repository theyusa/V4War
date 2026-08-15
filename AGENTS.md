# Repository Guidelines

## Project Overview

V4War (`tr.theyusa.v4war`, v1.1.1) is a sing-box-based Android VPN/proxy client.
Hybrid architecture: a Kotlin Multiplatform Compose UI (`composeApp`) + a thin
Android shell (`androidApp`), with the networking engine written in Go (`libcore`,
wrapping sing-box) and bound to Kotlin via the **anja** fork of gomobile.

Protocol scope: VLESS, VMess, Trojan, Shadowsocks, Reality, TLS/uTLS/ALPN, XHTTP,
HTTPUpgrade, gRPC, plus sing-box DNS/routing/TUN/sniffing. WireGuard/OpenVPN/
Naive/OpenConnect/Snell are **explicitly out of scope** for ports.

## Architecture & Data Flow

Two process tiers (same APK):

- **main process** — Compose UI, Room, `DataStore`, WorkManager.
- **`:bg` process** — the Go sing-box instance (in-process via anja bind, not a
  separate binary) plus `VpnService`/`ProxyService`/`TileService`.

Data flow:

```
share link / subscription
  → protocol bean (AbstractBean subclass: StandardV2RayBean, ShadowsocksBean, …)
  → persisted as a Kryo blob column in ProxyEntity (Room)
  → ConfigBuilder.buildConfig() assembles typed SingBoxOptions → sing-box JSON
  → BoxInstance.loadConfig() → Libcore Service.newInstance(config)
  → Service.StartInstance() → Go box.New / box.Start
  → sing-box opens inbounds (mixed/TUN/DNS); in VPN mode it calls back
    AndroidPlatformInterface.openTun() → VpnService.startVpn() returns the TUN fd to Go
```

- UI ↔ `:bg` service talk over an AIDL-style `IServiceControl` binder
  (`SagerConnection`, `IServiceObserver`).
- Connection/speed/group data flows over the Go Unix-socket `Client` API
  (`api.sock`, `vario` wire codec).
- `clash_api = ClashAPIOptions()` is **always** emitted by `ConfigBuilder` so
  sing-box registers `CombinedAPI` as its `ClashServer`; `libcore/box.go` wraps it
  nil-safely (`clashServerWrapper`) — never use an unguarded type assertion.

## Key Directories

| Path | Purpose |
|---|---|
| `libcore/` | Go sing-box core: `box.go`, `service.go`, `format.go`, `ping.go`, `http.go`, `dns.go`, `url.go`, `combinedapi/`, `cmd/` |
| `composeApp/src/commonMain/kotlin/tr/theyusa/v4war/` | Shared Kotlin: `fmt/`, `group/`, `database/`, `bg/`, `ui/`, `di/`, `ktx/`, `repository/`, `libcore/` |
| `composeApp/src/androidMain/kotlin/tr/theyusa/v4war/` | Android impl: `Application.kt`, `ui/MainActivity.kt`, `bg/` (services) |
| `androidApp/` | Android app shell (manifest, signing, flavors) |
| `buildScript/` | Shell build scripts: `init/` (versions, env, CI actions), `lib/` (core/assets/source), `plugin/` |
| `plugin/`, `library/` | Git submodules (native plugin binaries; Compose libs) |
| `.github/workflows/` | CI (APK build, sing-box upgrade check) |

## Development Commands

Build order (full APK):

```sh
make libcore_android   # = ./run lib core --android  → composeApp/libs/libcore.aar
make assets            # = ./run lib assets  → downloads geoip/geosite
./gradlew :composeApp:exportLibraryDefinitions
make apk               # = BUILD_PLUGIN=none ./gradlew androidApp:assembleFossRelease
```

Quick checks (no device needed):

```sh
cd libcore && go build ./...   # compile the Go core
cd libcore && go test ./...    # Go unit tests
```

Other Makefile targets: `apk_debug`, `lint_go`, `fmt_go`, `plugin PLUGIN=…`,
`test` (= `test_gradle` + `test_go`).

The `run` script is a dispatcher: `./run <a> <b> …` execs the first matching
`buildScript/<a>/<b>.sh`. E.g. `./run init action gradle` → `buildScript/init/action/gradle.sh`.

## Code Conventions & Common Patterns

- **Namespace**: current `tr.theyusa.v4war` for all new code; legacy `fr.husi.lib`
  must not be reintroduced.
- **Go↔Kotlin bridge**: anja/gomobile regenerates `tr.theyusa.v4war.libcore.*`
  (`Libcore`, `URL`, `Client`, `Service`, `HTTPClient`). Any new exported Go
  method requires re-running `./run lib core --android` to refresh the binding.
- **DI**: Koin — `initV4WarKoin(repository)`. In `commonMain` prefer interface +
  DI over `expect/actual`; reserve expect/actual for platform stubs
  (`IServiceControl`, `BoxServiceFactory`).
- **State**: Room (`SagerDatabase`, `ProxyEntity`, `RuleEntity`, `ProfileManager`)
  + `DataStore` (preferences singleton with delegated properties) + Kotlin
  Flow/StateFlow; Compose reads via `collectAsStateWithLifecycle`.
- **Beans**: protocol beans extend `AbstractBean` and serialize via
  `KryoConverters`. Serialize/deserialize `write*`/`read*` **order is load-bearing**
  — when adding a field, bump the serialized version and read it under a
  `version >= N` guard.
- **Config generation**: `ConfigBuilder.buildConfig()` → typed `SingBoxOptions` →
  JSON. Preserve these invariants: `clash_api = ClashAPIOptions()`; TLS `insecure`
  only via `effectiveAllowInsecure(bean.allowInsecure)` (`fmt/TLS.kt`);
  `combinedapi` imported via `libcore/box_include.go` (`_ "libcore/combinedapi"`).

## Important Files

- Entry points: `androidApp/src/main/AndroidManifest.xml`,
  `composeApp/src/androidMain/.../Application.kt`, `ui/MainActivity.kt`,
  `bg/BaseService.kt`, `bg/VpnService.kt`.
- Config: `fmt/ConfigBuilder.kt`, `fmt/SingBoxOptions.kt`, `fmt/TLS.kt`,
  `fmt/v2ray/V2RayFmt.kt`, `fmt/UniversalFmt.kt`.
- Go core: `libcore/box.go`, `service.go`, `format.go`, `combinedapi/combinedapi.go`.
- Data: `database/SagerDatabase.kt`, `ProxyEntity.kt`, `DataStore.kt`, `ProfileManager.kt`.
- Build pins: `buildScript/init/version.sh`, `libcore/build.sh`, `libcore/go.mod`,
  `gradle/libs.versions.toml`, `v4war.properties`.

## Runtime/Tooling Preferences

- **Go** 1.26.1 (`buildScript/init/version.sh GO_VERSION`; `go.mod` `go 1.26`).
- **JDK** 21 (temurin). **Gradle** 9.4.1 (wrapper).
- **Android**: compileSdk 36, minSdk 24, targetSdk 36, buildTools 36.1.0,
  NDK 29.0.14206865. ABI: arm64-v8a, armeabi-v7a, x86_64, x86.
- **sing-box** v1.14.0-alpha.18 (pinned in `libcore/go.mod`). Build tags:
  `with_gvisor, with_quic, with_utls, with_clash_api`. Do **not** upgrade the core
  without re-applying the clash_api + nil-safe `ClashServer` + `route.sniff`
  migration analysis.
- **Kotlin/Compose**: Kotlin 2.3.20, AGP 9.1.0, Compose Multiplatform 1.10.3,
  Material3 1.11.0-alpha05. Package manager: Gradle (Go modules for `libcore`).

## Testing & QA

- **Go**: standard `testing` only (no testify). Run `cd libcore && go test ./...`
  or `make test_go` (`go test -v -count=1 ./...`).
- **Kotlin**: `commonTest` uses `kotlin.test` + JUnit Jupiter (`junit-bom` 6.0.3) +
  `kotlinx-coroutines-test`. **Known infra gap**: the module applies
  `com.android.kotlin.multiplatform.library` with only an `android {}` target and
  no `withHostTest`/`withDeviceTest`, so `commonTest` is orphaned — there is no
  runnable Gradle test task (`make test_gradle` / `:composeApp:allTests` is stale).
  Kotlin tests currently cannot be executed; verify Kotlin changes by careful
  review or in CI after a build.
- **Conventions**: `@BeforeTest` does `initV4WarKoin(FakeRepository())` +
  `DataStore.configurationStore.reset()` + `SagerDatabase.*Dao.reset()`;
  `@AfterTest` calls `stopKoin()`; suspend tests use `runBlocking`; reset global
  state (e.g. `DataStore.globalAllowInsecure`) in `finally`.
- **CI**: `android-manual-build.yml` (dispatch APK) and
  `android-latest-singbox-build.yml` (weekly sing-box check). No unit-test job in
  the current workflows.
