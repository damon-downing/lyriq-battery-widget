# LYRIQ Battery Widget

A small, dependency-free Android app whose only job is to put my Cadillac LYRIQ's
state of charge on the Pixel home screen. Built for a Pixel 10 Pro XL (Android 16),
works on any Android 12+ device.

<p>
<b>Widget</b>: Material You colors, resizable from 2×1 to full width. Short rows show a
compact ring + percent + range; 2×2 / 3×2 show a big ring; 4×2 and larger show the
vehicle name, percent, range, charge state and "Updated N min ago". Tapping the widget opens the
myCadillac app when it is installed; tapping the ring refreshes (the whole widget refreshes
when myCadillac is absent).
</p>

## How the car's battery gets to the phone (research notes)

- **Inside the car / Android Auto.** The LYRIQ runs Android Automotive OS with Google
  built-in. Google Maps there reads the pack directly from the vehicle HAL through the
  Car API: `VehiclePropertyIds.EV_BATTERY_LEVEL` (Wh) divided by
  `INFO_EV_BATTERY_CAPACITY`, guarded by `Car.PERMISSION_ENERGY`. On a phone running
  Android Auto (projection) the head unit streams the same sensor data to the phone and
  Maps uses it for range-aware routing. Neither path is exposed to third-party phone
  apps, and Google Maps has no public "current SoC" API.
- **Phone apps therefore need a cloud path.** GM's own myCadillac app talks to the
  OnStar/GM Digital API. That API is undocumented, requires TOTP MFA, and the
  reverse-engineered clients (OnStarJS2 / onstar2mqtt) break every time GM changes it.
- **Smartcar** is the sanctioned route: an OAuth "Connect" flow where you log in with
  your OnStar account, then a REST API returns state of charge, range and charge state
  (V3 signals `tractionbattery-stateofcharge`, `tractionbattery-range`,
  `charge-detailedchargingstatus`; V2 `/battery` + `/charge`). Cadillac is a supported brand;
  the free plan covers 1 live vehicle and ~500 calls/vehicle/month, so this app defaults
  to a 2-hour background refresh plus on-demand taps.
- **Home Assistant** is the second supported source, for anyone already running
  onstar2mqtt (or any integration) — the widget reads entity states via HA's REST API.

## Data sources

| Source | What you need | Notes |
| --- | --- | --- |
| Smartcar (API V3, verified working with the LYRIQ) | Free app at dashboard.smartcar.com. Paste the **Application ID** (Configuration → Application details), the **Client ID** (`client_…`) and **Client secret** (API credentials tab). Register the redirect URI the app shows: `sc<ApplicationID>://exchange` | Connect uses the Application ID; the `client_…` credentials mint a 1-hour app token at `iam.smartcar.com`; the vehicle is resolved via `/v3/connections` and read from `/v3/vehicles/{id}/signals` with the `sc-user-id` from the Connect redirect. Legacy V2 credentials still work as a fallback. |
| Home Assistant | Base URL, long-lived token, battery entity id (plus optional range / charging entities) | Works with `sensor.<car>_ev_battery_level`, `sensor.<car>_ev_range`, `binary_sensor.<car>_ev_plug_state` from onstar2mqtt. |
| Demo / manual | nothing | Lets you place and resize the widget immediately. |

Credentials live only in the app's private SharedPreferences on the phone.

## Install on the phone

1. Download `lyriq-battery-widget.apk` (see `releases/` in this repo or the Build APK
   workflow artifact) to the Pixel and open it; allow installs from your browser/Files
   app when prompted.
2. Open **LYRIQ Battery**, pick a data source, save, and tap **Add widget** (or
   long-press the home screen → Widgets → LYRIQ Battery).
3. Resize by long-pressing the widget; every size from 2×1 up is supported.

## Build

No Gradle, no AGP, no AndroidX — the whole app is framework Java, so it builds with
`aapt2`, `javac`, `dx`/`d8`, `zipalign` and `apksigner`:

```sh
# Ubuntu: apt-get install aapt apksigner zipalign dalvik-exchange android-sdk-build-tools zip
# plus an android.jar for API 35 at $ANDROID_HOME/platforms/android-35/ (and API 33 for old aapt2)
ANDROID_HOME=~/android-sdk ./build.sh
# → build/out/lyriq-battery-widget.apk
```

`.github/workflows/build.yml` does the same on GitHub Actions with the runner's SDK and
attaches the APK to any `v*` tag as a release.

The release keystore (`keystore/lyriq-release.jks`, password `lyriqwidget`, alias
`lyriq`) is committed so future builds keep the same signature and can update the
installed app in place. This repo is private; rotate it if that changes.

## Layout of the code

```
app/AndroidManifest.xml            permissions, widget receiver, job service
app/res/xml/widget_info.xml        widget metadata (resize, min sizes, preview)
app/res/layout/widget_*.xml        small / medium / large layouts
app/src/.../LyriqWidgetProvider    AppWidgetProvider; RemoteViews(Map<SizeF, RemoteViews>) responsive sizing
app/src/.../WidgetRenderer         ring gauge bitmap + text formatting
app/src/.../SmartcarSource         OAuth token exchange/refresh + /batch fetch
app/src/.../SmartcarConnectActivity WebView that hosts Smartcar Connect and catches the redirect
app/src/.../HomeAssistantSource    REST /api/states reader
app/src/.../RefreshJobService      JobScheduler periodic + expedited refresh
app/src/.../MainActivity           settings screen
build.sh                           the whole build pipeline
```
