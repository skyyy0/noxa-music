# NOXA

Premium native music streaming player untuk Android, ditenagai API musik publik
(Deezer, Audius, YouTube via instance Piped/Invidious, LRCLIB) dengan audio yang
benar-benar diputar, bukan simulasi.

* **Package**: `com.noxa.music` (namespace `com.example`)
* **Stack**: Kotlin, Jetpack Compose (Material 3), Media3/ExoPlayer, Room, Retrofit +
  Moshi, Firebase (Auth, Firestore, AI), KSP
* **Min / target SDK**: 24 / 36 (`compileSdk` 36.1)

Dokumentasi konfigurasi, sumber musik, dan perilaku aplikasi ada di
[`ENVIRONMENT.md`](ENVIRONMENT.md).

## Prasyarat

| Kebutuhan | Versi |
| --- | --- |
| JDK | 17 |
| Android SDK Platform | API 36 (build-tools + platform-tools) |
| Gradle | pakai wrapper yang disertakan (`./gradlew`) |

Tidak ada server, database, atau layanan berbayar yang wajib. Semua kunci API
**opsional** — aplikasi tetap berjalan penuh tanpa konfigurasi apa pun.

## Setup

1. Arahkan Gradle ke Android SDK (file ini tidak di-commit):

   ```bash
   echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
   ```

2. (Opsional) Salin konfigurasi environment. Kosongkan juga tidak masalah:

   ```bash
   cp .env.example .env
   ```

   `.env` dibaca oleh Secrets Gradle Plugin lalu diekspos sebagai `BuildConfig`.

3. (Opsional) Firebase: `app/google-services.json` sudah ada di repo. Untuk build
   dari sumber lain, pulihkan dari secret base64:

   ```bash
   printf '%s' "$GOOGLE_SERVICES_JSON_BASE64" | base64 --decode > app/google-services.json
   ```

   Tanpa file itu build tetap sukses (plugin berjalan mode `WARN`); hanya
   Firebase Auth / Firestore yang tidak aktif.

## Build & test

```bash
chmod +x gradlew            # hanya jika bit executable hilang
./gradlew assembleDebug     # APK debug  -> app/build/outputs/apk/debug/
./gradlew assembleRelease   # APK release -> app/build/outputs/apk/release/
./gradlew testDebugUnitTest # unit test (Robolectric)
```

Release di-sign dengan keystore dari `KEYSTORE_PATH` / `CM_KEYSTORE_PATH` bila
tersedia; kalau tidak ada keystore, APK release dibiarkan tidak ter-sign dan
build tetap lulus.

## CI

[`codemagic.yaml`](codemagic.yaml) menyediakan dua workflow: `noxa-android-debug`
(tanpa setup) dan `noxa-android-release` (butuh keystore `noxa_keystore` di
Codemagic serta variabel secret opsional `GOOGLE_SERVICES_JSON_BASE64`).

## Struktur singkat

```
app/src/main/java/com/example/
  data/     # remote API, auth, penyimpanan lokal, backend
  ui/       # Compose screens, komponen, view model
  ...
app/src/test/            # unit test Robolectric
app/src/androidTest/     # instrumentation test
```
