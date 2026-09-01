package com.omarzanji.lyriqwidget;

import android.content.Context;
import android.content.SharedPreferences;

/** Thin wrapper over the app's private SharedPreferences. */
public final class Prefs {
    private static final String FILE = "lyriq_widget";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---- general ----
    public String source() { return sp.getString("source", VehicleSource.MANUAL); }
    public void setSource(String s) { sp.edit().putString("source", s).apply(); }

    public int refreshMinutes() {
        int def = VehicleSource.SMARTCAR.equals(source()) ? 120 : 30;
        return Math.max(15, sp.getInt("refresh_minutes", def));
    }
    public boolean hasRefreshMinutes() { return sp.contains("refresh_minutes"); }
    public void setRefreshMinutes(int m) { sp.edit().putInt("refresh_minutes", Math.max(15, m)).apply(); }

    public boolean isRefreshing() { return sp.getBoolean("refreshing", false); }
    public void setRefreshing(boolean b) { sp.edit().putBoolean("refreshing", b).apply(); }

    // ---- manual / demo ----
    public int manualPercent() { return sp.getInt("manual_percent", 72); }
    public boolean manualCharging() { return sp.getBoolean("manual_charging", false); }
    public void setManual(int percent, boolean charging) {
        sp.edit().putInt("manual_percent", percent).putBoolean("manual_charging", charging).apply();
    }

    // ---- smartcar ----
    public String scClientId() { return sp.getString("sc_client_id", ""); }
    public String scClientSecret() { return sp.getString("sc_client_secret", ""); }
    public boolean scSimulated() { return sp.getBoolean("sc_simulated", false); }
    /** Optional "client_..." ID from the API credentials tab; used with the secret at the token endpoint. */
    public String scTokenClientId() { return sp.getString("sc_token_client_id", ""); }
    public void setSmartcarApp(String id, String tokenClientId, String secret, boolean simulated) {
        sp.edit().putString("sc_client_id", id.trim())
                .putString("sc_token_client_id", tokenClientId.trim())
                .putString("sc_client_secret", secret.trim())
                .putBoolean("sc_simulated", simulated).apply();
    }
    public String scAccessToken() { return sp.getString("sc_access", ""); }
    public String scRefreshToken() { return sp.getString("sc_refresh", ""); }
    public long scExpiresAt() { return sp.getLong("sc_expires", 0); }
    public String scVehicleId() { return sp.getString("sc_vehicle", ""); }
    public void setScTokens(String access, String refresh, long expiresAt) {
        sp.edit().putString("sc_access", access).putString("sc_refresh", refresh).putLong("sc_expires", expiresAt).apply();
    }
    public void setScVehicleId(String id) { sp.edit().putString("sc_vehicle", id).apply(); }
    public boolean scConnected() { return !scRefreshToken().isEmpty(); }
    public void clearSmartcarConnection() {
        sp.edit().remove("sc_access").remove("sc_refresh").remove("sc_expires").remove("sc_vehicle").apply();
    }

    // ---- home assistant ----
    public String haUrl() { return sp.getString("ha_url", ""); }
    public String haToken() { return sp.getString("ha_token", ""); }
    public String haEntityBattery() { return sp.getString("ha_entity_battery", ""); }
    public String haEntityRange() { return sp.getString("ha_entity_range", ""); }
    public String haEntityCharging() { return sp.getString("ha_entity_charging", ""); }
    public void setHomeAssistant(String url, String token, String battery, String range, String charging) {
        sp.edit().putString("ha_url", url.trim().replaceAll("/+$", ""))
                .putString("ha_token", token.trim())
                .putString("ha_entity_battery", battery.trim())
                .putString("ha_entity_range", range.trim())
                .putString("ha_entity_charging", charging.trim()).apply();
    }

    // ---- snapshot ----
    public BatterySnapshot snapshot() {
        return new BatterySnapshot(
                sp.getInt("snap_percent", -1),
                Double.longBitsToDouble(sp.getLong("snap_range", Double.doubleToLongBits(-1))),
                sp.getBoolean("snap_charging", false),
                sp.getBoolean("snap_plugged", false),
                sp.getLong("snap_updated", 0),
                sp.getString("snap_name", ""),
                sp.getString("snap_error", null));
    }

    public void saveSnapshot(BatterySnapshot s) {
        SharedPreferences.Editor e = sp.edit()
                .putInt("snap_percent", s.percent)
                .putLong("snap_range", Double.doubleToLongBits(s.rangeMiles))
                .putBoolean("snap_charging", s.charging)
                .putBoolean("snap_plugged", s.pluggedIn)
                .putLong("snap_updated", s.updatedAt)
                .putString("snap_name", s.vehicleName == null ? "" : s.vehicleName);
        if (s.error == null) e.remove("snap_error"); else e.putString("snap_error", s.error);
        e.apply();
    }

    public void saveError(String message) {
        sp.edit().putString("snap_error", message).apply();
    }
}
