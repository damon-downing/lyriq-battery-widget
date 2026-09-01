---
name: setup-lyriq-widget
description: Walk a user through installing the LYRIQ Battery Widget APK on Android and connecting it to their car through Smartcar (API V3) or Home Assistant. Use when someone asks how to set up, connect, configure, or troubleshoot the widget, its Smartcar credentials, redirect URI, or refresh interval.
---

# Set up the LYRIQ Battery Widget

You are guiding a person, not running commands on their phone. Ask for screenshots when
something fails; the app shows Smartcar's own error text in its dialogs.

## 1. Install the APK

1. Send them the latest APK: the newest file under `releases/` in this repo, or the
   asset on the latest GitHub release. Build one with the `build-apk` skill if needed.
2. On the phone: open the APK from the browser or Files app, allow "install unknown apps"
   for that app if prompted, install. Updates install over the old version as long as the
   `versionCode` is higher **and** the signing key is the same; a different key requires
   uninstalling first.
3. Open **LYRIQ Battery**. Choose **Demo / manual value**, tap **Save settings**, then
   **Add widget** (or long-press the home screen, Widgets, LYRIQ Battery). This proves
   the widget renders before any credentials are involved.

## 2. Smartcar (recommended; works for any Smartcar-supported EV)

Smartcar's current dashboard issues **API V3** credentials. The app needs three values and
one redirect URI. Walk through them in this order.

1. **Create the app**: https://dashboard.smartcar.com, sign up (free), the default
   application is fine. Free plan: 1 live vehicle, about 500 API calls per vehicle per month.
2. **Application ID**: Configuration, *Application details* tab, copy **Application ID**
   (a UUID). Paste into the app's first Smartcar field.
3. **Redirect URI**: still on *Application details*, click **Add new redirect URI** and add
   exactly `sc<ApplicationID>://exchange` (lowercase `sc` prefix, the UUID, then
   `://exchange`). The app prints this string once the Application ID is typed in.
4. **API credentials**: Configuration, *API credentials* tab, copy the **Client ID**
   (`client_…`) and create/copy a **Client secret** (shown once). Paste both into the app.
5. **Vehicle access** tab: make sure Battery, Charge and Vehicle info scopes are enabled
   (`read_battery`, `read_charge`, `read_vehicle_info`).
6. In the app: select **Smartcar**, set refresh to **120** minutes or more, **Save
   settings**, tap **Connect vehicle**. Choose the brand, sign in with the manufacturer
   account (myCadillac / OnStar for GM cars), approve. The app returns with "Connected (V3)".
7. Optional dry run: tick "simulated vehicle", create a simulated car under *Simulator* in
   the dashboard, connect; untick and reconnect for the real car.

### Troubleshooting map

| Symptom | Cause | Fix |
| --- | --- | --- |
| Connect page: `400 Invalid parameter client_id: client_…` | The `client_…` ID was put in the Application ID field | Use the UUID Application ID for the first field |
| Connect page: redirect URI error | URI not registered or mistyped | Register `sc<ApplicationID>://exchange` exactly |
| "Smartcar token error (401)" after approving | Legacy V2 exchange ran because the `client_…` field is empty | Fill the `client_…` Client ID; the app then uses V3 |
| "Smartcar API-credentials error (401)" | Wrong or stale client secret | Regenerate the secret in *API credentials*, paste the new one |
| "Smartcar reports no connected vehicles yet" | Connect was approved for a different user/app | Reconnect; check the dashboard's *Vehicles* page |
| "returned no state-of-charge signal" | Vehicle lacks battery scope or brand does not expose it | Check *Vehicle access* scopes; try the battery endpoint from the dashboard |
| Widget shows "Couldn't refresh · …" | Last background refresh failed; message is Smartcar's | Tap the ring/car to retry; read the message |

## 3. Home Assistant (for onstar2mqtt users or any integration)

1. Base URL reachable from the phone (for example `https://ha.example.com` or
   `http://homeassistant.local:8123`; cleartext HTTP is allowed by the app).
2. Profile, Security, **Create long-lived access token**; paste it.
3. Entity ids: battery percent (required, for example `sensor.lyriq_ev_battery_level`),
   range (optional; km auto-converted to miles by unit), charging (optional; `on`/`off`,
   `true`, or a state containing "charging").
4. Select **Home Assistant**, refresh 15 to 30 min is fine, Save, Refresh now.

## 4. Widget styles and paint

Settings, **Widget style**: Ring (default), Car (side-profile in the chosen paint;
nine LYRIQ factory colors or a hex), Bar. Long-press the widget and pick the settings
gear to reopen this screen. Tapping the widget opens myCadillac if installed; tapping the
ring, car or bar refreshes.
