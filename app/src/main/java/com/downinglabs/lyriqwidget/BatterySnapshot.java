package com.downinglabs.lyriqwidget;

/** Last known vehicle state, persisted in Prefs so the widget can render offline. */
public final class BatterySnapshot {
    public final int percent;          // -1 when unknown
    public final double rangeMiles;    // <0 when unknown
    public final boolean charging;
    public final boolean pluggedIn;
    public final long updatedAt;       // epoch millis, 0 when never
    public final String vehicleName;
    public final String error;         // null when the last refresh succeeded
    public final Boolean locked;       // null when unknown/not requested, else true/false

    public BatterySnapshot(int percent, double rangeMiles, boolean charging, boolean pluggedIn,
                           long updatedAt, String vehicleName, String error) {
        this(percent, rangeMiles, charging, pluggedIn, updatedAt, vehicleName, error, null);
    }

    public BatterySnapshot(int percent, double rangeMiles, boolean charging, boolean pluggedIn,
                           long updatedAt, String vehicleName, String error, Boolean locked) {
        this.percent = percent;
        this.rangeMiles = rangeMiles;
        this.charging = charging;
        this.pluggedIn = pluggedIn;
        this.updatedAt = updatedAt;
        this.vehicleName = vehicleName;
        this.error = error;
        this.locked = locked;
    }

    public boolean hasData() {
        return percent >= 0;
    }

    public BatterySnapshot withError(String message) {
        return new BatterySnapshot(percent, rangeMiles, charging, pluggedIn, updatedAt, vehicleName, message, locked);
    }
}
