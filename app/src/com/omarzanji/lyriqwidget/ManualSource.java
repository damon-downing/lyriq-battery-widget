package com.omarzanji.lyriqwidget;

/** Demo source: shows whatever the user dialed in on the settings screen. */
public final class ManualSource implements VehicleSource {
    @Override
    public BatterySnapshot fetch(Prefs prefs) {
        int percent = prefs.manualPercent();
        // LYRIQ EPA rated range ~ 300-326 mi depending on trim; 314 is a fair single-motor average.
        double range = Math.round(314 * percent / 100.0);
        return new BatterySnapshot(percent, range, prefs.manualCharging(), prefs.manualCharging(),
                System.currentTimeMillis(), "Cadillac LYRIQ (demo)", null);
    }
}
