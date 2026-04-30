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
|-----------|---------|
| `libcore/` | Go core (sing-box based VPN engine) |
| `composeApp/` | Android UI in Kotlin/Compose |
| `androidApp/` | Android shell app |

## Important Code Locations

- **Dashboard Panel**: `composeApp/src/commonMain/kotlin/tr/theyusa/v4war/ui/dashboard/` - Connection viewing, proxy selection
- **CombinedAPI**: `libcore/combinedapi/` - Traffic tracking (independent of Clash API)
- **Go Box Instance**: `libcore/box.go` - Core VPN instance management

## Critical Patterns

### Panel + CombinedAPI Dependency
The dashboard panel (Connections, Proxy Sets) relies on `combinedapi` being registered as ClashServer. When modifying `clash_api` config in `ConfigBuilder.kt`, always include an empty `ClashAPIOptions()` or else CombinedAPI won't be created and connections will be empty.

```kotlin
// ConfigBuilder.kt - ALWAYS include this under experimental:
clash_api = SingBoxOptions.ClashAPIOptions()  // Empty triggers CombinedAPI
```

### Box Include
In `libcore/box_include.go`, the order matters:
```go
// combinedapi must be imported, clashapi is optional for web UI
_ "libcore/combinedapi"
```

### Compose Performance
Use `remember` and `derivedStateOf` to minimize unnecessary recompositions:
```kotlin
val displayData by derivedStateOf {
    // expensive calculation - only recomputes when dependencies change
}
```
- Avoid `LaunchedEffect` with complex calculations inside - precompute in ViewModel
- Use `@Stable` and `@Immutable` data classes for state to help Compose optimize
- Room incremental compilation is active (ksp room.incremental=true)

## Common Issues

| Issue | Fix |
|-------|-----|
| Connections empty | Check ConfigBuilder has `clash_api = ClashAPIOptions()` |
| Panel crashes on open | `box.go` missing nil check on `b.api` - always initialize wrapper |
| App crashes when Clash API disabled | Dashboard handler calls `instance.api.TrafficManager()` without nil check |

## Quick Test

```sh
cd libcore && go build ./...
```

## Git Branches

- `new` - Development branch
- `main` - Stable branch
- `backup-new` - Backup of latest changes before rollback to c360594