# Baigram Android

Baigram Android client, package name `im.baigram`.

## Build

Requirements:

- JDK 17
- Android SDK Platform 35 / Build-Tools 35.0.0
- Android NDK `27.2.12479018`
- CMake `3.10.2`

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :TMessagesProj_App:assembleAfatDebug
```

Output: `TMessagesProj_App/build/outputs/apk/afat/debug/app.apk`

`afat` builds all ABIs: armeabi-v7a / arm64-v8a / x86 / x86_64.

Release builds require `TMessagesProj/config/release.keystore` and the signing values in `gradle.properties`.

## CI

`.github/workflows/android.yml` runs on pushes to `main` or manually, and uploads the APK as a build artifact.
