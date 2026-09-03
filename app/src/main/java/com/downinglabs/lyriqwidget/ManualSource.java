package com.downinglabs.lyriqwidget;

/** Demo source: shows whatever fixed value the user dialed in on the config screen — no
 * network calls at all, zero Smartcar/GM dependency. Useful for a second test vehicle, or
 * just for seeing the widget/car render in different states without a live connection. */
public final class ManualSource implements VehicleSource {
    @Override
    public BatterySnapshot fetch(Vehicle vehicle) {
        return new BatterySnapshot(vehicle.manualPercent(), -1, vehicle.manualCharging(), vehicle.manualCharging(),
                System.currentTimeMillis(), "Cadillac LYRIQ (demo)", null);
    }
}
