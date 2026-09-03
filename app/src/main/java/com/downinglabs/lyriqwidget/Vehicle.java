package com.downinglabs.lyriqwidget;

import android.content.SharedPreferences;

import java.util.Locale;

/**
 * One saved car: its own data source (Smartcar or Home Assistant), its own credentials,
 * its own widget style/color, its own refresh interval, its own cached snapshot. Every
 * field lives in the shared SharedPreferences file under a "v:<id>:" prefix — same flat-key
 * style Prefs used when there was only one vehicle, just parameterized per vehicle now.
 */
public final class Vehicle {
    public final String id;
    private final SharedPreferences sp;

    Vehicle(SharedPreferences sp, String id) {
        this.sp = sp;
        this.id = id;
    }

    private String k(String key) {
        return "v:" + id + ":" + key;
    }

    // ---- identity ----
    public String nickname() { return sp.getString(k("nickname"), ""); }
    public void setNickname(String n) { sp.edit().putString(k("nickname"), n.trim()).apply(); }

    public String source() { return sp.getString(k("source"), VehicleSource.SMARTCAR); }
    public void setSource(String s) { sp.edit().putString(k("source"), s).apply(); }

    public int refreshMinutes() {
        int def = VehicleSource.SMARTCAR.equals(source()) ? 120 : 30;
        return Math.max(15, sp.getInt(k("refresh_minutes"), def));
    }
    public void setRefreshMinutes(int m) { sp.edit().putInt(k("refresh_minutes"), Math.max(15, m)).apply(); }

    // ---- widget look ----
    public static final String STYLE_RING = "ring", STYLE_CAR = "car", STYLE_BAR = "bar";
    public String widgetStyle() { return sp.getString(k("widget_style"), STYLE_CAR); }
    public void setWidgetStyle(String s) { sp.edit().putString(k("widget_style"), s).apply(); }
    public int carColor() { return sp.getInt(k("car_color"), CarRenderer.PAINT_COLORS[5]); }
    public void setCarColor(int argb) { sp.edit().putInt(k("car_color"), argb).apply(); }

    public boolean isRefreshing() { return sp.getBoolean(k("refreshing"), false); }
    public void setRefreshing(boolean b) { sp.edit().putBoolean(k("refreshing"), b).apply(); }

    // ---- manual / demo ----
    public int manualPercent() { return sp.getInt(k("manual_percent"), 72); }
    public boolean manualCharging() { return sp.getBoolean(k("manual_charging"), false); }
    public void setManual(int percent, boolean charging) {
        sp.edit().putInt(k("manual_percent"), percent).putBoolean(k("manual_charging"), charging).apply();
    }

    // ---- smartcar ----
    public String scClientId() { return sp.getString(k("sc_client_id"), ""); }
    public String scClientSecret() { return sp.getString(k("sc_client_secret"), ""); }
    public String scTokenClientId() { return sp.getString(k("sc_token_client_id"), ""); }
    public void setSmartcarApp(String id2, String tokenClientId, String secret) {
        sp.edit().putString(k("sc_client_id"), id2.trim())
                .putString(k("sc_token_client_id"), tokenClientId.trim())
                .putString(k("sc_client_secret"), secret.trim()).apply();
    }
    public String scAccessToken() { return sp.getString(k("sc_access"), ""); }
    public String scRefreshToken() { return sp.getString(k("sc_refresh"), ""); }
    public long scExpiresAt() { return sp.getLong(k("sc_expires"), 0); }
    public String scVehicleId() { return sp.getString(k("sc_vehicle"), ""); }
    public void setScTokens(String access, String refresh, long expiresAt) {
        sp.edit().putString(k("sc_access"), access).putString(k("sc_refresh"), refresh).putLong(k("sc_expires"), expiresAt).apply();
    }
    public void setScVehicleId(String id2) { sp.edit().putString(k("sc_vehicle"), id2).apply(); }
    public String scUserId() { return sp.getString(k("sc_user_id"), ""); }
    public void setScUserId(String id2) { sp.edit().putString(k("sc_user_id"), id2).apply(); }
    public String scApiVersion() { return sp.getString(k("sc_api_version"), "v2"); }
    public void setScApiVersion(String v) { sp.edit().putString(k("sc_api_version"), v).apply(); }
    public boolean scConnected() {
        // V3 used to check scUserId — but this SDK version never provides one (see
        // SmartcarSource.completeConnect()), so that check could never pass even on a fully
        // successful connection. scVehicleId is the reliable signal: it's only ever set once
        // a real vehicle is actually found.
        return "v3".equals(scApiVersion()) ? !scVehicleId().isEmpty() : !scRefreshToken().isEmpty();
    }
    public void clearSmartcarConnection() {
        sp.edit().remove(k("sc_access")).remove(k("sc_refresh")).remove(k("sc_expires")).remove(k("sc_vehicle"))
                .remove(k("sc_user_id")).remove(k("sc_api_version")).apply();
    }

    // ---- snapshot ----
    public BatterySnapshot snapshot() {
        return new BatterySnapshot(
                sp.getInt(k("snap_percent"), -1),
                Double.longBitsToDouble(sp.getLong(k("snap_range"), Double.doubleToLongBits(-1))),
                sp.getBoolean(k("snap_charging"), false),
                sp.getBoolean(k("snap_plugged"), false),
                sp.getLong(k("snap_updated"), 0),
                sp.getString(k("snap_name"), ""),
                sp.getString(k("snap_error"), null));
    }

    public void saveSnapshot(BatterySnapshot s) {
        SharedPreferences.Editor e = sp.edit()
                .putInt(k("snap_percent"), s.percent)
                .putLong(k("snap_range"), Double.doubleToLongBits(s.rangeMiles))
                .putBoolean(k("snap_charging"), s.charging)
                .putBoolean(k("snap_plugged"), s.pluggedIn)
                .putLong(k("snap_updated"), s.updatedAt)
                .putString(k("snap_name"), s.vehicleName == null ? "" : s.vehicleName);
        if (s.error == null) e.remove(k("snap_error")); else e.putString(k("snap_error"), s.error);
        e.apply();
    }

    public void saveError(String message) {
        sp.edit().putString(k("snap_error"), message).apply();
    }

    /** What shows in the vehicle list: the nickname, or the last known car name, or a generic label. */
    public String displayName() {
        String n = nickname();
        if (!n.isEmpty()) return n;
        String snapName = snapshot().vehicleName;
        if (snapName != null && !snapName.isEmpty()) return snapName;
        return "Smartcar vehicle";
    }

    public String sourceLabel() {
        if (VehicleSource.MANUAL.equals(source())) return "Demo (no live data)";
        return String.format(Locale.US, "Smartcar%s", scConnected() ? "" : " (not connected)");
    }
}
