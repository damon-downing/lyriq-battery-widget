---
name: build-apk
description: Build, sign and verify the LYRIQ Battery Widget APK without Gradle or Android Studio, including bootstrapping aapt2/dx/zipalign/apksigner and an android.jar in a fresh Linux sandbox where Google's download hosts may be blocked. Use when asked to build, rebuild, bump the version, or produce a release of this app.
---

# Build the APK

`build.sh` is the whole pipeline: `aapt2 compile/link`, `javac -source 8`, `dx` or `d8`,
`zip`, `zipalign`, `apksigner`. It needs an `android.jar` and the build tools; nothing else.

## Fast path (Android SDK already installed)

```sh
ANDROID_HOME=$ANDROID_HOME BUILD_TOOLS=$ANDROID_HOME/build-tools/35.0.0 ./build.sh
# -> build/out/lyriq-battery-widget.apk
```

## Sandbox path (no SDK, dl.google.com blocked)

1. Tools from apt (Debian/Ubuntu):
   `apt-get install -y aapt apksigner zipalign dalvik-exchange android-sdk-build-tools zip`
   gives `/usr/lib/android-sdk/build-tools/29.0.3/{aapt2,dx,zipalign,apksigner}`.
   `build.sh` finds this directory automatically.
2. `android.jar` from the Sable mirror (git works even when raw downloads do not):
   ```sh
   git clone --depth 1 --filter=blob:none --sparse https://github.com/Sable/android-platforms.git /tmp/ap
   (cd /tmp/ap && git sparse-checkout set android-35 android-33)
   mkdir -p ~/android-sdk/platforms/android-35 ~/android-sdk/platforms/android-33
   cp /tmp/ap/android-35/android.jar ~/android-sdk/platforms/android-35/
   cp /tmp/ap/android-33/android.jar ~/android-sdk/platforms/android-33/
   ```
   The API 33 jar matters: aapt2 2.19 (the apt build) cannot parse the compact resource
   table in API 34+ jars, so `build.sh` links resources against 33 and compiles Java
   against 35. Set `AAPT_ANDROID_JAR` to override.
3. `ANDROID_HOME=~/android-sdk ./build.sh`.

## Signing

- No keystore present: `build.sh` generates `keystore/debug.jks` (gitignored) so the build
  never blocks. Such APKs cannot update an install signed with a different key.
- Maintainer releases: set `KEYSTORE`, `KS_PASS`, `KEY_ALIAS` env vars, or in CI the
  `ANDROID_KEYSTORE_BASE64` and `ANDROID_KEYSTORE_PASSWORD` secrets (see
  `.github/workflows/build.yml`).

## Before you call it done

- Bump `android:versionCode` and `android:versionName` in `app/AndroidManifest.xml`.
- `aapt dump badging build/out/lyriq-battery-widget.apk | grep -E "package|sdkVersion"`.
- `apksigner verify --print-certs build/out/lyriq-battery-widget.apk`.
- The dx path refuses `invokedynamic`: no lambdas or method refs in `app/src`.
- Copy the APK to `releases/lyriq-battery-widget-v<version>.apk` for a release commit.
