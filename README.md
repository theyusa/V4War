# V4War

<p align="center">
  <img src="./V.png" alt="V4War logo" width="220" />
</p>

**Freedom first.**

V4War is an Android client built for the fight for internet freedom.
Its mission is simple: help users keep control over their own network routes under censorship,
traffic manipulation, and restrictive network conditions.

V4War is not a VPN provider and does not sell network access.
It is a technical client for users who run and manage their own profile/proxy infrastructure.

---

Turkce dokuman icin: **[README.tr.md](./README.tr.md)**

## Why V4War

- Censorship resistance and operator-controlled routing
- Fast profile import and subscription update workflows
- Protocol flexibility with a modern hybrid core
- Android-first experience focused on reliability and control

## Core Architecture

V4War uses a **hybrid core architecture**:

- **V2Ray ecosystem compatibility** for broad profile format support
- **sing-box core engine** for modern routing and runtime behavior

This design keeps compatibility and performance in a single Android-focused client.

## Supported Protocol Families

V4War includes support for major protocol families used in modern proxy workflows,
including VLESS, VMess, Trojan, Shadowsocks, SOCKS/HTTP based profiles, and related formats.

## Project Identity

- App name: `V4War`
- Package name: `tr.theyusa.v4war`
- Developer: `TheYusa`
- Name and trademark rights: `TheYusa`

## Build (Android)

### Requirements

- JDK 21
- Android NDK `29.0.14206865`
- Go version compatible with `buildScript/init/version.sh`

### 1) Clone

```sh
git clone https://github.com/theyusa/V4War.git --depth=1
cd V4War
```

### 2) Build libcore for Android

```sh
make libcore_android
```

This generates:

```text
composeApp/libs/libcore.aar
```

### 3) Download geo assets

```sh
make assets
```

### 4) Export OSS license metadata

```sh
./gradlew :composeApp:exportLibraryDefinitions
```

### 5) Build APK

```sh
make apk
```

APK output:

```text
androidApp/build/outputs/apk
```

## Contributing

Please read **[CONTRIBUTING.md](./CONTRIBUTING.md)**.

## License

`GPL-3.0-or-later`
