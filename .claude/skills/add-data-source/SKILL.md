---
name: add-data-source
description: Add a new vehicle data source (another car API, brand cloud, MQTT bridge, or local server) to the LYRIQ Battery Widget by implementing VehicleSource, wiring Prefs, the settings UI, and the source picker. Use when asked to support a different car, API, or backend for the widget.
---

# Add a data source

A source is one class that turns settings into a `BatterySnapshot`. Everything else
(widgets, scheduling, error display) is shared.

1. **Implement `VehicleSource`** in `app/src/com/omarzanji/lyriqwidget/<Name>Source.java`:
   - `fetch(Prefs)` runs on a background thread; use `Http.request(...)` and `org.json`.
   - Return `new BatterySnapshot(percent, rangeMiles /* -1 if unknown */, charging, pluggedIn,
     System.currentTimeMillis(), vehicleName, null)`.
   - Throw `IllegalStateException` with a **user-readable** message on failure; it is shown
     verbatim in the widget footer and the settings card.
   - No lambdas (dx). No new dependencies (there is no dependency system).
2. **Prefs**: add getters/setters for the new settings in `Prefs.java` (string keys, trimmed).
3. **Factory**: add a constant and a `case` in `VehicleSource.forPrefs`.
4. **Settings UI**: add a `RadioButton` to `source_group` and a `LinearLayout` section in
   `activity_main.xml`; wire `showSection`, `loadIntoForm`, and `saveForm` in
   `MainActivity.java` (follow the Home Assistant section as the template).
5. **Docs**: describe the setup in `docs/SETUP.md` and the table in `README.md`; extend the
   `setup-lyriq-widget` skill's troubleshooting map.
6. **Rate limits**: if the API is metered, choose a sensible default in
   `Prefs.refreshMinutes()` for that source.
7. Build with the `build-apk` skill and bump the version.
