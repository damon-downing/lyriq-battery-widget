# LYRIQ Battery Widget — agent guide

Read this before touching the repo. It is short on purpose; the skills under
`.claude/skills/` carry the step-by-step procedures.

## What this is

A framework-only Android app (Java, minSdk 31, targetSdk 35, **no Gradle, no AndroidX**)
whose single job is a resizable home-screen widget showing an EV's state of charge.
Built for the Cadillac LYRIQ, but any Smartcar-supported EV or any Home Assistant
entity works.

## Layout

```
app/AndroidManifest.xml                 permissions, <queries> for myCadillac, widget receiver, job service
app/res/xml/widget_info.xml             widget metadata (sizes, resize, configure activity)
app/res/layout/widget_{small,medium,large}.xml        Ring style
app/res/layout/widget_car_{small,medium,large}.xml    Car style
app/res/layout/widget_bar_{small,medium,large}.xml    Bar style
app/res/layout/activity_main.xml        settings screen
app/res/values{,-night}/colors.xml      Material You palette (system_accent1 / system_neutral1)
app/src/com/omarzanji/lyriqwidget/
  LyriqWidgetProvider.java   AppWidgetProvider; RemoteViews(Map<SizeF,RemoteViews>) responsive sizing; per-style Spec table
  WidgetRenderer.java        ring gauge bitmap + text lines
  CarRenderer.java           LYRIQ side-profile bitmap (paint color) + battery bar bitmap
  VehicleSource.java         interface + factory (smartcar | ha | manual)
  SmartcarSource.java        Smartcar Connect + API V3 (iam token, /v3/connections, /v3/vehicles/{id}/signals) with V2 fallback
  SmartcarConnectActivity    WebView hosting Connect; intercepts sc<AppID>://exchange (code + user_id)
  HomeAssistantSource.java   GET /api/states/<entity>
  ManualSource.java          demo values
  Refresher.java             fetch → Prefs snapshot → repaint widgets
  RefreshJobService/Scheduler JobScheduler periodic + expedited one-off
  Prefs.java                 SharedPreferences wrapper (settings, tokens, snapshot)
  MainActivity.java          settings UI; also the widget's reconfigure activity
build.sh                     aapt2 → javac → dx/d8 → zipalign → apksigner
.github/workflows/build.yml  CI build + release on v* tags
docs/SETUP.md                end-user setup guide (Smartcar, Home Assistant)
```

## Hard rules

- **No lambdas / method references / Java 9+ syntax in `app/src`.** The Debian `dx`
  used locally cannot desugar `invokedynamic`; `build.sh` fails the build if it finds
  one. Use anonymous inner classes. (CI uses `d8`, which would accept them — don't rely
  on that.)
- **Never touch a RemoteViews id that isn't in that layout.** A single bad id makes the
  launcher render "Problem loading widget". The `Spec` table in `LyriqWidgetProvider`
  declares which ids each layout has; extend it when adding layouts.
- **Every widget size must fit its declared `SizeF`.** `SMALL` (110×40 dp) is any single
  row, `MEDIUM` (120×140) is 2×2/3×2, `LARGE` (250×120) is 4×2 and up. Vertical layouts
  go in MEDIUM only.
- **Keep credentials on-device.** Tokens and secrets live in private SharedPreferences.
  Never log them, never add analytics.
- **Smartcar quota.** Free plan ≈ 500 calls/vehicle/month. Default refresh is 120 min on
  Smartcar; don't lower it silently.
- **Signing.** `keystore/` is gitignored. `build.sh` generates a throwaway debug keystore
  when none is present. Releases from the maintainer are signed with a private key
  supplied via the `ANDROID_KEYSTORE_BASE64` / `ANDROID_KEYSTORE_PASSWORD` CI secrets.
- **Version bumps.** Bump `versionCode` and `versionName` in `AndroidManifest.xml` for
  every user-facing change; Android refuses to install a same-or-lower `versionCode`
  over an existing install.

## Verifying a change

1. `ANDROID_HOME=~/android-sdk ./build.sh` must succeed (see `.claude/skills/build-apk`).
2. `aapt dump badging build/out/lyriq-battery-widget.apk` shows the expected version.
3. Install on a device (`adb install -r build/out/lyriq-battery-widget.apk`) and check
   every style at 2×1, 2×2, 4×2. There is no emulator in most agent sandboxes; say so
   in the report instead of claiming a visual check you did not do.

## Skills

- `.claude/skills/setup-lyriq-widget` — walk a user through Smartcar/Home Assistant setup and install.
- `.claude/skills/build-apk` — reproduce the Gradle-free build in a fresh environment.
- `.claude/skills/add-data-source` — add a new `VehicleSource` (another API or car brand).
