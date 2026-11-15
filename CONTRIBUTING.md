# Contributing to V4War

Thank you for your interest in contributing to **V4War**!

## Development Setup

1. **Prerequisites**
   - **JDK 17 or 21** (Temurin recommended; `compileOptions` targets Java 17)
   - **Android SDK** (`compileSdk = 35`, `buildToolsVersion = "35.0.1"`, `minSdk = 24`)
   - **Go / ThroneCore AAR** (CI fetches `ThroneCore` automatically via `.github/actions/throne-core`; for local full APK builds, make sure `app/libs/` contains the core AAR)

2. **Run Unit Tests**
   ```bash
   ./gradlew :app:testOssDebugUnitTest
   ```

3. **Build Debug APK**
   ```bash
   ./gradlew :app:assembleOssDebug
   ```

## Guidelines

- **Keep PRs focused:** Separate bug fixes, features, and refactors into clear commits using [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `ci:`, `refactor:`).
- **Write tests:** Add JVM unit tests under `app/src/test/` for new Hotspot Share, Toolbox, or config/routing logic.
- **Localization:** User-facing strings live in `app/src/main/res/values/strings*.xml` and `app/src/main/res/values-tr/strings*.xml`. Keep English and Turkish resources in sync.
- **Security & Privacy:** Never log or export credentials, Clash API secrets, or GitHub tokens. Ensure backup sanitization (`HotspotBackupSanitizer`) covers any newly added sensitive fields.
- **Upstream Attribution:** V4War tracks [throneproj/ThroneForAndroid](https://github.com/throneproj/ThroneForAndroid). Avoid unnecessary churn in upstream-owned files unless needed for fork features.

## License

By contributing, you agree that your contributions will be licensed under the **GNU General Public License v3.0** ([LICENSE](LICENSE)).
