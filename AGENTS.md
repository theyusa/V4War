# AGENTS.md - V4War Developer Instructions

## Build Commands (in order)

```sh
# 1. Build Go core to Android AAR
make libcore_android

# 2. Download geo assets
make assets

# 3. Export OSS license metadata
./gradlew :composeApp:exportLibraryDefinitions

# 4. Build APK
make apk
```

## Namespace Convention
- **Current**: `tr.theyusa.v4war` (all new code)
- **Old**: `fr.husi.lib` (legacy - avoid)
- When adding new files, use `tr.theyusa.v4war` package prefix

## Key Directories

| Directory | Purpose |
|----------|---------|
| `libcore/` | Go core (sing-box based VPN engine) |
| `composeApp/` | Android UI in Kotlin/Compose |
| `androidApp/` | Android shell app |

## Important Code Locations

- **Dashboard Panel**: `composeApp/.../ui/dashboard/` - Connection viewing, proxy selection
- **CombinedAPI**: `libcore/combinedapi/` - Traffic tracking (independent of Clash API)
- **Go Box Instance**: `libcore/box.go` - Core VPN instance management

## Critical Patterns

### Panel + CombinedAPI Dependency
The dashboard panel relies on `combinedapi` being registered as ClashServer.

```kotlin
// ConfigBuilder.kt - ALWAYS include:
clash_api = SingBoxOptions.ClashAPIOptions()  // Empty triggers CombinedAPI
```

### Box Include (libcore/box_include.go)
```go
_ "libcore/combinedapi"  // Must be imported
```

### Clean Architecture
Avoid platform-specific code leaking into common layer:
```kotlin
// ❌ Avoid: expect/actual
expect fun getPlatformName(): String

// ✅ Prefer: Interface + DI
interface PlatformProvider {
    fun getPlatformName(): String
}

class MyViewModel(private val platform: PlatformProvider) : ViewModel()
```

### Koin DI Pattern
V4War uses Koin for dependency injection:
```kotlin
// androidMain
single<PlatformProvider> { AndroidPlatformProvider() }

// commonMain
class MyViewModel(private val platform: PlatformProvider) : ViewModel()
```

### Compose Performance
- Use `remember` and `derivedStateOf` for expensive calculations
- Use `@Stable` and `@Immutable` data classes
- Room incremental: `ksp.arg("room.incremental", "true")`

## Common Issues

| Issue | Fix |
|-------|-----|
| Connections empty | Check ConfigBuilder has `clash_api = ClashAPIOptions()` |
| Panel crashes | `box.go` - always initialize wrapper, nil check `b.api` |
| Crash when Clash API disabled | Handler needs nil check before `TrafficManager()` |

## Quick Test

```sh
cd libcore && go build ./...
```

## Git Branches

- `new` - Development branch
- `main` - Stable branch
- `backup-new` - Backup of recent changes
