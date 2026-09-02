package com.downinglabs.lyriqwidget;

/**
 * TEMP demo source: shows whatever fixed value the user dialed in on the config screen, no
 * network calls at all. Restored so you can stand up a second test vehicle without needing a
 * second real Smartcar account while proving out the two-widget flow. Remove once that's done
 * (see AGENTS.md) — delete this file, the MANUAL constant in VehicleSource, and the manual
 * radio/section in VehicleConfigActivity + its layout.
 */
public final class ManualSource implements VehicleSource {
    @Override
    public BatterySnapshot fetch(Vehicle vehicle) {
        return new BatterySnapshot(vehicle.manualPercent(), -1, vehicle.manualCharging(), vehicle.manualCharging(),
                System.currentTimeMillis(), "Cadillac LYRIQ (demo)", null);
    }
}
