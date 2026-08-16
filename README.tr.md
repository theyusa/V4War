# V4War

<p align="center">
  <img src="./V.png" alt="V4War logo" width="220" />
</p>

V4War, internet ozgurlugu ve sansure direnc odakli bir Android istemcisidir.

Misyonu, kisitli ag kosullarinda kullanicinin kendi routing ve baglanti kontrolunu korumasina yardim etmektir.
V4War hazir erisim satan bir servis degildir; kendi profil altyapisini yoneten kullanicilar icin teknik bir istemcidir.

English README: **[README.md](./README.md)**

## Uyari

2025 Agustos doneminde Google, Android dagitim surecinde merkezilesmeyi artiran bir dogrulama modeli duyurdu.
V4War, ozgur yazilim ilkeleri geregi acik ekosistem, kullanici ozerkligi ve bagimsiz dagitim tarafinda durur.

Acikligin korunmasi icin: [Keep Android Open](https://keepandroidopen.org/)

## Ana Yonelim

- Ozgurluk odakli ag araclari
- Sansure dayanikli ve kullanici kontrollu routing
- Android odakli guvenilirlik ve pratik kontrol
- Denetlenebilir, topluluk destekli gelisim yaklasimi

## Cekirdek Mimari

V4War hibrit cekirdek tasarimi kullanir:

- V2Ray ekosistemi uyumlulugu
- sing-box runtime ve routing motoru

Bu yapi, genis profil uyumlulugunu modern routing davranisi ile birlestirir.

## Ozellik Ozeti

- Link, subscription ve dosya tabanli profil importu
- Subscription guncelleme akislari
- VLESS, VMess, Trojan, Shadowsocks ve benzeri protokol aileleri
- Sorun tespiti icin loglama ve tanilama araclari

## Derleme Rehberi (Android)

### Gereksinimler

- JDK 21
- Android NDK `29.0.14206865`
- Go surumu `buildScript/init/version.sh` ile uyumlu olmali

### 1) Kaynagi al

```sh
git clone https://github.com/theyusa/V4War.git --depth=1
cd V4War
```

### 2) libcore (Android) derle

```sh
make libcore_android
```

Cikti:

```text
composeApp/libs/libcore.aar
```

### 3) Geo varliklarini indir

```sh
make assets
```

### 4) OSS lisans metadata olustur

```sh
./gradlew :composeApp:exportLibraryDefinitions
```

### 5) APK derle

```sh
make apk
```

APK cikti dizini:

```text
androidApp/build/outputs/apk
```

## Proje Kimligi

- Uygulama adi: `V4War`
- Paket adi: `tr.theyusa.v4war`
- Gelistirici: `TheYusa`
- Isim ve marka haklari: `TheYusa`

## Katki

Oku: **[CONTRIBUTING.md](./CONTRIBUTING.md)**

## Lisans

`GPL-3.0-or-later`

## Tesekkur

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
