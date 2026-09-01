# Setup guide

Ten minutes, no Android Studio, no Google Play. You need an Android 12+ phone, a Smartcar
developer account (free) **or** a Home Assistant instance that already knows your car.

## 1. Install the app

1. On the phone, download the newest `lyriq-battery-widget-v*.apk` from
   [`releases/`](../releases) (or the assets on the latest GitHub release).
2. Open it. Android asks to allow installs from that browser or Files app the first time;
   allow it and tap Install.
3. Open **LYRIQ Battery**. Pick **Demo / manual value**, tap **Save settings**, then
   **Add widget**. A widget with a fake 72% appears on your home screen, so you can place
   and resize it before any accounts are involved. Long-press it to resize; every size
   from 2×1 up works.

## 2. Connect your car through Smartcar

Smartcar is the officially supported way for third-party apps to read GM (and 40 other
brands') cars. You log in once with your manufacturer account and Smartcar hands the app
battery, range and charging state. The free plan covers one live vehicle and roughly 500
API calls per vehicle per month, which is why the app defaults to a two-hour background
refresh; tapping the widget refreshes on demand.

### 2a. Create a Smartcar application

1. Go to https://dashboard.smartcar.com and sign up.
2. The dashboard creates a default application. Open it and go to **Configuration**.

### 2b. Copy three values into the app

| Dashboard tab | Value | App field |
| --- | --- | --- |
| Application details | **Application ID** (a UUID) | *Application ID* |
| API credentials | **Client ID** (`client_…`) | *Client ID (client_…)* |
| API credentials | **Client secret** (click *Create secret*; it is shown once) | *Client secret* |

### 2c. Register the redirect URI

On the **Application details** tab, click **Add new redirect URI** and paste the string
the app shows under the credentials, which is `sc` + your Application ID + `://exchange`,
for example:

```
sc12345678-90ab-cdef-1234-567890abcdef://exchange
```

Save. Smartcar only accepts this `sc<id>://` form for native apps, so the redirect never
touches a server: the app catches it itself.

### 2d. Check scopes and connect

1. **Vehicle access** tab: keep at least *Vehicle info*, *Battery* and *Charge* enabled.
2. Back in the app: select **Smartcar**, set **Refresh** to 120 minutes or more, tap
   **Save settings**, then **Connect vehicle**.
3. Smartcar Connect opens inside the app. Pick your brand, sign in with the manufacturer
   account (for Cadillac that's your myCadillac / OnStar login), complete any MFA, choose
   the car, tap Allow.
4. The app returns showing **Connected (V3)** and refreshes. Your real charge replaces the
   demo value on the widget within a few seconds.

Want to try it before involving your car? Tick **Use a Smartcar simulated vehicle**, create
a simulated car under **Simulator** in the dashboard, connect, then untick and reconnect.

### If something fails

The app shows Smartcar's own error text. The common ones:

- **`Invalid parameter client_id: client_…` on the Connect page** — the `client_…` value
  went into the Application ID field. The first field wants the UUID.
- **Redirect URI error on the Connect page** — step 2c was skipped or has a typo.
- **`Smartcar token error (401)` after approving** — the `client_…` Client ID is empty, so
  the app fell back to Smartcar's legacy flow, which new dashboards no longer support.
- **`Smartcar API-credentials error (401)`** — the secret is wrong. Create a new one.
- **`reports no connected vehicles yet`** — the approval went to a different Smartcar app
  or user; reconnect and check *Vehicles* in the dashboard.

## 3. Alternative: Home Assistant

If Home Assistant already tracks your car (for example through
[onstar2mqtt](https://github.com/BigThunderSR/onstar2mqtt)), the widget can read its
entities directly.

1. **Base URL** reachable from the phone: `https://ha.example.com` or
   `http://homeassistant.local:8123`. Plain HTTP is allowed.
2. **Token**: Home Assistant profile, Security, *Create long-lived access token*.
3. **Entities**: battery percent (required), range (optional; kilometres are converted
   using the entity's unit), charging (optional; `on`, `true`, or any state containing
   "charging" counts as charging).
4. Select **Home Assistant**, Save, **Refresh now**. A 15 to 30 minute refresh is fine here.

## 4. Make it yours

- **Widget style**: Ring (default), Car, or Bar. The Car style draws a side-profile LYRIQ
  in your paint; pick one of the nine factory colors or type a hex.
- **Tap behaviour**: tapping the widget opens myCadillac when it is installed; tapping the
  ring, car or bar refreshes. Without myCadillac the whole widget refreshes.
- **Settings from the home screen**: long-press the widget and choose the settings gear.
- **Privacy**: credentials and tokens stay in the app's private storage on the phone. The
  app talks only to Smartcar (or your Home Assistant) and has no analytics.

## 5. Updating

Install a newer APK over the old one; settings and the Smartcar connection are kept. If
Android says the app can't be installed, the new APK was signed with a different key
(for example a build you made yourself): uninstall first, then install and reconnect.
