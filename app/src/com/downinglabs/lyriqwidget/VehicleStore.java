package com.downinglabs.lyriqwidget;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the list of saved vehicles and which placed widget instance (appWidgetId) belongs to
 * which vehicle. One SharedPreferences file backs everything; Vehicle objects are thin views
 * into it scoped by id (see Vehicle.k()).
 */
public final class VehicleStore {
    private static final String FILE = "lyriq_widget";
    private static final String KEY_IDS = "vehicle_ids"; // comma-separated, preserves add order

    private final SharedPreferences sp;

    public VehicleStore(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public List<String> vehicleIds() {
        String raw = sp.getString(KEY_IDS, "");
        List<String> ids = new ArrayList<>();
        if (!raw.isEmpty()) {
            for (String s : raw.split(",")) if (!s.isEmpty()) ids.add(s);
        }
        return ids;
    }

    public boolean isEmpty() { return vehicleIds().isEmpty(); }

    public Vehicle get(String id) { return new Vehicle(sp, id); }

    /** A fresh, in-memory vehicle id that is NOT yet in the saved list — nothing persists until register(). */
    public String newId() { return UUID.randomUUID().toString(); }

    /** Adds id to the saved list. Call once, after the caller has written at least the source field. */
    public void register(String id) {
        List<String> ids = vehicleIds();
        if (ids.contains(id)) return;
        ids.add(id);
        sp.edit().putString(KEY_IDS, String.join(",", ids)).apply();
    }

    /** Removes the vehicle and every "v:<id>:" key it owns, and unbinds any widgets pointing at it. */
    public void delete(String id) {
        List<String> ids = vehicleIds();
        ids.remove(id);
        SharedPreferences.Editor e = sp.edit().putString(KEY_IDS, String.join(",", ids));
        String prefix = "v:" + id + ":";
        for (String key : sp.getAll().keySet()) if (key.startsWith(prefix)) e.remove(key);
        e.apply();
        for (int widgetId : widgetIdsForVehicle(id)) unbindWidget(widgetId);
    }

    // ---- widget instance -> vehicle binding ----

    private static String widgetKey(int appWidgetId) { return "w:" + appWidgetId; }

    public void bindWidget(int appWidgetId, String vehicleId) {
        sp.edit().putString(widgetKey(appWidgetId), vehicleId).apply();
    }

    public void unbindWidget(int appWidgetId) {
        sp.edit().remove(widgetKey(appWidgetId)).apply();
    }

    /** Null when this widget instance hasn't been configured yet (shouldn't normally happen post-place). */
    public String vehicleIdForWidget(int appWidgetId) {
        return sp.getString(widgetKey(appWidgetId), null);
    }

    public List<Integer> widgetIdsForVehicle(String vehicleId) {
        List<Integer> out = new ArrayList<>();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            String key = e.getKey();
            if (key.startsWith("w:") && vehicleId.equals(e.getValue())) {
                try { out.add(Integer.parseInt(key.substring(2))); } catch (NumberFormatException ignored) {}
            }
        }
        return out;
    }

    public boolean hasAnyWidget(String vehicleId) { return !widgetIdsForVehicle(vehicleId).isEmpty(); }
}
