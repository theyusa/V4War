# V4War

<p align="center">
  <img src="./V.png" alt="V4War Logo" width="220" />
</p>

**TR**

V4War, internet ozgurlugu icin verilen mucadelenin teknik tarafinda duran bir Android istemcisidir.
Misyonumuz; sansur, trafik manipulasyonu ve baglanti kisitlarina karsi dayanikli bir kullanim deneyimi sunmak,
kullanicinin kendi ag rotasini kendi kosullarina gore yonetebilmesini saglamaktir.

V4War bir hazir VPN saticisi degildir; kendi profil/proxy altyapinizi Android tarafinda yonetmeniz icin gelistirilmis,
teknik odakli bir istemcidir.

## TR - Ozet

- Cekirdek mimari: `V2Ray + sing-box hibrit altyapi`
- Profil/abonelik yonetimi, import ve guncelleme akislari
- Gelismis baglanti protokolleri (VLESS, VMess, Trojan, Shadowsocks vb.)
- Android odakli, hizli ve ozellestirilebilir kullanim

## TR - Derleme

Gereksinimler:

- JDK 21
- Android NDK `29.0.14206865`
- Go surumu: `buildScript/init/version.sh` ile uyumlu

```sh
git clone https://github.com/TheYusa/V4War.git --depth=1
cd V4War
make libcore_android
make assets
./gradlew :composeApp:exportLibraryDefinitions
make apk
```

APK ciktilari:

```text
androidApp/build/outputs/apk
```

---

**EN**

V4War is an Android client built for the technical side of the fight for internet freedom.
Our mission is to provide a censorship-resistant, operator-controlled experience against blocking,
traffic manipulation, and restrictive network conditions.

V4War is not a VPN provider. It is a power-user client that helps you run and manage your own
profiles/proxy infrastructure on Android.

## EN - Overview

- Core architecture: `V2Ray + sing-box hybrid stack`
- Profile/subscription management, import and update workflows
- Modern protocol support (VLESS, VMess, Trojan, Shadowsocks, etc.)
- Android-focused, fast, and customizable UX

## EN - Build

Requirements:

- JDK 21
- Android NDK `29.0.14206865`
- Go version compatible with `buildScript/init/version.sh`

```sh
git clone https://github.com/TheYusa/V4War.git --depth=1
cd V4War
make libcore_android
make assets
./gradlew :composeApp:exportLibraryDefinitions
make apk
```

Output:

```text
androidApp/build/outputs/apk
```

## License

`GPL-3.0-or-later`
