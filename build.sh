#!/usr/bin/env bash
# Builds a signed release APK without Gradle/AGP: aapt2 → javac → dx → zipalign → apksigner.
#
# Inputs (env, all optional):
#   ANDROID_JAR   path to platforms/android-35/android.jar  (default: $ANDROID_HOME or ~/android-sdk)
#   AAPT_ANDROID_JAR  android.jar handed to aapt2 (default: android-33 if present, else ANDROID_JAR)
#   BUILD_TOOLS   dir containing aapt2/dx/zipalign/apksigner (default: Debian's /usr/lib/android-sdk/build-tools/29.0.3,
#                 falling back to $ANDROID_HOME/build-tools/<newest>)
#   KEYSTORE / KS_PASS / KEY_ALIAS   signing config (default: keystore/lyriq-release.jks; a debug key is generated if absent)
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
# No release keystore (fresh clone / CI without secrets)? Generate a throwaway debug key so
# the build still produces an installable APK. It cannot update an app signed with another key.
if [[ ! -f "$KEYSTORE" ]]; then
  KEYSTORE="$ROOT/keystore/debug.jks"; KS_PASS="android"; KEY_ALIAS="debug"
  if [[ ! -f "$KEYSTORE" ]]; then
    mkdir -p "$ROOT/keystore"
    keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias "$KEY_ALIAS" \
      -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=LYRIQ Battery Widget debug" >/dev/null 2>&1
    echo "▸ no release keystore found; generated debug keystore at $KEYSTORE"
  fi
fi

tool() { # prefer the build-tools copy, fall back to PATH; Windows ships these as .bat/.exe
  # (bash's -x doesn't reliably see .bat as executable under MSYS/Git-Bash, so use -f for those)
  if [[ -x "$BUILD_TOOLS/$1" ]]; then echo "$BUILD_TOOLS/$1"; return 0; fi
  for ext in ".bat" ".exe"; do
    if [[ -f "$BUILD_TOOLS/$1$ext" ]]; then echo "$BUILD_TOOLS/$1$ext"; return 0; fi
  done
  command -v "$1" || command -v "$1.bat" || command -v "$1.exe"
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
# javac.exe/d8.bat on Windows are native binaries that don't understand MSYS's /c/... paths;
# convert with cygpath when present (Git Bash ships it), no-op elsewhere.
winpath() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi; }
find "$ROOT/app/src" "$OUT/gen" -name '*.java' | while IFS= read -r f; do winpath "$f"; done > "$OUT/sources.txt"
javac -source 8 -target 8 -encoding UTF-8 -Xlint:-options \
  -bootclasspath "$(winpath "$ANDROID_JAR")" -d "$(winpath "$OUT/classes")" @"$OUT/sources.txt"

echo "▸ dex ($(basename "$DEXER"))"
if [[ "$(basename "$DEXER")" == "d8" || "$(basename "$DEXER")" == "d8.bat" ]]; then
  "$DEXER" --release --min-api 31 --lib "$(winpath "$ANDROID_JAR")" --output "$(winpath "$OUT/dex")" \
    $(find "$OUT/classes" -name '*.class' | while IFS= read -r f; do winpath "$f"; done)
else
  # dx cannot desugar lambdas/method refs; the sources deliberately avoid them.
  if find "$OUT/classes" -name '*.class' -exec javap -c -p {} + | grep -q invokedynamic; then
    echo "invokedynamic found in compiled classes; dx cannot dex lambdas" >&2; exit 1
  fi
  "$DEXER" --dex --min-sdk-version=31 --output="$OUT/dex/classes.dex" "$OUT/classes"
fi

echo "▸ package"
cp "$OUT/app.unsigned.apk" "$OUT/app.withdex.apk"
# `zip` isn't shipped with Git Bash by default; the JDK's `jar` tool updates an existing
# archive the same way (`jar uf` == `zip -u`) and we already require a JDK for javac/keytool.
if command -v zip >/dev/null 2>&1; then
  ( cd "$OUT/dex" && zip -q -u "$OUT/app.withdex.apk" classes.dex )
else
  ( cd "$OUT/dex" && jar uf "$OUT/app.withdex.apk" classes.dex )
fi
"$ZIPALIGN" -f -p 4 "$OUT/app.withdex.apk" "$OUT/app.aligned.apk"

echo "▸ sign"
APK="$OUT/out/lyriq-widget.apk"
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --ks-key-alias "$KEY_ALIAS" --key-pass "pass:$KS_PASS" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out "$APK" "$OUT/app.aligned.apk"
"$APKSIGNER" verify --print-certs "$APK" | head -3
ls -la "$APK"
