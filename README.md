# V4War

<p align="center">
  <img src="./V.png" alt="V4War logo" width="220" />
</p>

V4War is an Android client focused on internet freedom and censorship resistance.

Its mission is to help users keep control of routing and connectivity under restrictive network conditions.
V4War does not sell network access. It is a technical client for users who manage their own profiles.

Turkce README: **[README.tr.md](./README.tr.md)**

## Alert

In August 2025, Google announced a developer verification model that can centralize control over Android app distribution.
As free software, V4War stands for user autonomy, open ecosystems, and independent software distribution.

If openness matters to you, follow and support: [Keep Android Open](https://keepandroidopen.org/)

## Core Direction

- Freedom-first network tooling
- Censorship resistance and operator-controlled routing
- Android-first reliability and practical control
- Transparent, auditable, community-driven development

## Core Architecture

V4War uses a hybrid core design:

- V2Ray ecosystem compatibility
- sing-box runtime and routing engine

This combines broad profile compatibility with modern routing behavior.

## Feature Overview

- Profile import from links, subscriptions, and files
- Subscription update workflows
- Protocol family support including VLESS, VMess, Trojan, Shadowsocks, and related formats
- Logging and diagnostics for troubleshooting

## Build Guide (Android)

### Requirements

- JDK 21
- Android NDK `29.0.14206865`
- Go version compatible with `buildScript/init/version.sh`

### 1) Clone source

```sh
git clone https://github.com/theyusa/V4War.git --depth=1
cd V4War
```

### 2) Build libcore (Android)

```sh
make libcore_android
```

Output:

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

APK output directory:

```text
androidApp/build/outputs/apk
```

## Project Identity

- App name: `V4War`
- Package name: `tr.theyusa.v4war`
- Developer: `TheYusa`
- Name and trademark rights: `TheYusa`

## License

`GPL-3.0-or-later`

## Acknowledgements

- [SagerNet/sing-box](https://github.com/SagerNet/sing-box)
- [shadowsocks/shadowsocks-android](https://github.com/shadowsocks/shadowsocks-android)
- [SagerNet/SagerNet](https://github.com/SagerNet/SagerNet)
- [XTLS/AnXray](https://github.com/XTLS/AnXray)
- [MatsuriDayo/NekoBoxForAndroid](https://github.com/MatsuriDayo/NekoBoxForAndroid)
- [SagerNet/sing-box-for-android](https://github.com/SagerNet/sing-box-for-android)
- [AntiNeko/CatBoxForAndroid](https://github.com/AntiNeko/CatBoxForAndroid)
- [MetaCubeX/ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid)
- [dyhkwong/Exclave](https://github.com/dyhkwong/Exclave)
- [chen08209/FlClash](https://github.com/chen08209/FlClash)
- [RikkaApps/RikkaX](https://github.com/RikkaApps/RikkaX)
