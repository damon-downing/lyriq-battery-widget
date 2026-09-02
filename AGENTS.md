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

## The car image is a traced-photo shading map

`CarRenderer.car()` composites a single traced-photo shading map from
`app/res/drawable-nodpi/car_shade.png`. The source is a VTracer color-quantized vector trace
of the owner's own car photo, kept as `tools/car_trace_source.svg` (regenerate by tracing a
new photo with VTracer, not by editing pixels). Because the trace is tonal
(grayscale-ish, not flat), a single `PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY)`
reproduces the shading in any paint colour: near-white pixels take the full paint colour,
near-black pixels (tires, glass, grille, shadows) stay dark regardless of colour. This
replaced the earlier Blender-procedural three-layer stack (`car3d_diff/gloss/rest.png`,
`tools/render3d/lyriq_model.py`) — the technique is simpler (one layer, one multiply,
no held-out "rest" pass) and looks more like a real car because it starts from a real
photo instead of hand-modeled geometry.

To regenerate the asset from a new source photo:
1. Isolate the car on a plain near-white background (crop/remove background).
2. Vectorize with VTracer (`vtracer --input photo.png --output trace.svg`) — not potrace;
   potrace collapses everything to one flat silhouette with no shading detail and can't be
   multiply-tinted convincingly.
3. Rasterize the SVG to a PNG ~1100px wide. `cairosvg` needs a native libcairo binary that
   Windows doesn't ship by default; `tools/build_car_shade.py`/`tools/finish_car_shade.py`
   fall back to an Edge headless screenshot of an HTML wrapper (`msedge --headless
   --screenshot=... --window-size=W,H file:///wrapper.html`) instead of fighting that install.
4. Flood-fill the *connected* near-white background region to transparent (alpha=0) — do
   NOT chroma-key by color globally, since on-car highlights can share the background's
   near-white tone; only pixels reachable from the image border without crossing a
   non-background pixel should go transparent.
5. Replace `car_shade.png`; delete the old file if renaming.

`PORT_X`/`PORT_Y` in `CarRenderer` locate the charge port in the image and are currently
**estimated, not verified on a device** — there is no emulator in most agent sandboxes
(see "Verifying a change" above); check the charge-port bolt position against a real
install and adjust if it's off.

## TEMP: demo/manual vehicle source

Restored (Sep 2026) purely to let dual-widget testing happen without a second real Smartcar
account: `ManualSource.java`, `VehicleSource.MANUAL`, the "Demo / manual value" radio button
and `section_manual` in `activity_vehicle_config.xml`, and `Vehicle.manualPercent()` /
`manualCharging()` / `setManual()`. Once dual-widget testing is done, remove all of the
above — grep for "TEMP" to find every touch point. This is NOT the same thing as Smartcar's
own "simulated vehicle" checkbox (`sc_simulated`), which is a real Smartcar API feature that
still requires real credentials and goes through the real Connect OAuth flow.
