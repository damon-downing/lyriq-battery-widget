#!/usr/bin/env bash
# Builds a signed release APK without Gradle/AGP: aapt2 → javac → dx → zipalign → apksigner.
#
# Inputs (env, all optional):
#   ANDROID_JAR   path to platforms/android-35/android.jar  (default: $ANDROID_HOME or ~/android-sdk)
#   AAPT_ANDROID_JAR  android.jar handed to aapt2 (default: android-33 if present, else ANDROID_JAR)
#   BUILD_TOOLS   dir containing aapt2/dx/zipalign/apksigner (default: Debian's /usr/lib/android-sdk/build-tools/29.0.3,
#                 falling back to $ANDROID_HOME/build-tools/<newest>)
#   KEYSTORE / KS_PASS / KEY_ALIAS   signing config (default: keystore/lyriq-release.jks, "lyriqwidget", "lyriq")
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
ANDROID_JAR="${ANDROID_JAR:-$SDK/platforms/android-35/android.jar}"
# aapt2 < 2.20 cannot read the compact resource table in API 34+ android.jar; link against 33 when present.
if [[ -z "${AAPT_ANDROID_JAR:-}" ]]; then
  if [[ -f "$SDK/platforms/android-33/android.jar" ]]; then AAPT_ANDROID_JAR="$SDK/platforms/android-33/android.jar"; else AAPT_ANDROID_JAR="$ANDROID_JAR"; fi
fi
if [[ -z "${BUILD_TOOLS:-}" ]]; then
  if [[ -x /usr/lib/android-sdk/build-tools/29.0.3/aapt2 ]]; then
    BUILD_TOOLS=/usr/lib/android-sdk/build-tools/29.0.3
  else
    BUILD_TOOLS="$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)"
  fi
fi
KEYSTORE="${KEYSTORE:-$ROOT/keystore/lyriq-release.jks}"
KS_PASS="${KS_PASS:-lyriqwidget}"
KEY_ALIAS="${KEY_ALIAS:-lyriq}"

tool() { # prefer the build-tools copy, fall back to PATH
  if [[ -x "$BUILD_TOOLS/$1" ]]; then echo "$BUILD_TOOLS/$1"; else command -v "$1"; fi
}
AAPT2="$(tool aapt2)"; ZIPALIGN="$(tool zipalign)"; APKSIGNER="$(tool apksigner)"
DEXER="$(tool d8 || true)"; [[ -n "$DEXER" ]] || DEXER="$(tool dx)"

[[ -f "$ANDROID_JAR" ]] || { echo "android.jar not found at $ANDROID_JAR" >&2; exit 1; }
OUT="$ROOT/build"; rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/out"

echo "▸ aapt2 compile/link"
"$AAPT2" compile --dir "$ROOT/app/res" -o "$OUT/res.zip"
"$AAPT2" link -o "$OUT/app.unsigned.apk" -I "$AAPT_ANDROID_JAR" \
  --manifest "$ROOT/app/AndroidManifest.xml" --java "$OUT/gen" \
  --min-sdk-version 31 --target-sdk-version 35 \
  --auto-add-overlay "$OUT/res.zip"

echo "▸ javac"
find "$ROOT/app/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -encoding UTF-8 -Xlint:-options \
  -bootclasspath "$ANDROID_JAR" -d "$OUT/classes" @"$OUT/sources.txt"

echo "▸ dex ($(basename "$DEXER"))"
if [[ "$(basename "$DEXER")" == "d8" ]]; then
  "$DEXER" --release --min-api 31 --lib "$ANDROID_JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
else
  # dx cannot desugar lambdas/method refs; the sources deliberately avoid them.
  if find "$OUT/classes" -name '*.class' -exec javap -c -p {} + | grep -q invokedynamic; then
    echo "invokedynamic found in compiled classes; dx cannot dex lambdas" >&2; exit 1
  fi
  "$DEXER" --dex --min-sdk-version=31 --output="$OUT/dex/classes.dex" "$OUT/classes"
fi

echo "▸ package"
cp "$OUT/app.unsigned.apk" "$OUT/app.withdex.apk"
( cd "$OUT/dex" && zip -q -u "$OUT/app.withdex.apk" classes.dex )
"$ZIPALIGN" -f -p 4 "$OUT/app.withdex.apk" "$OUT/app.aligned.apk"

echo "▸ sign"
APK="$OUT/out/lyriq-battery-widget.apk"
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias "$KEY_ALIAS" --key-pass "pass:$KS_PASS" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out "$APK" "$OUT/app.aligned.apk"
"$APKSIGNER" verify --print-certs "$APK" | head -3
ls -la "$APK"
