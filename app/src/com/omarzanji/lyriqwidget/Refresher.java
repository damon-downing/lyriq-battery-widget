package com.omarzanji.lyriqwidget;

import android.content.Context;
import android.util.Log;

/** Fetches from the configured source, stores the snapshot, and repaints every widget. */
public final class Refresher {
    private static final String TAG = "LyriqRefresh";

    private Refresher() {}

    /** Blocking; call from a background thread. Returns the snapshot that is now displayed. */
    public static BatterySnapshot refreshNow(Context context) {
        Prefs prefs = new Prefs(context);
        BatterySnapshot result;
        try {
            result = VehicleSource.forPrefs(prefs).fetch(prefs);
            prefs.saveSnapshot(result);
        } catch (Exception e) {
            Log.w(TAG, "refresh failed", e);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            prefs.saveError(msg);
            result = prefs.snapshot();
        } finally {
            prefs.setRefreshing(false);
        }
        LyriqWidgetProvider.updateAll(context);
        return result;
    }
}
