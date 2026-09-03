package com.downinglabs.lyriqwidget;

import android.content.Context;
import android.util.Log;

/** Fetches one vehicle's data from its own source, stores the snapshot, repaints its widgets. */
public final class Refresher {
    private static final String TAG = "LyriqRefresh";

    private Refresher() {}

    /** Blocking; call from a background thread. Returns the snapshot that is now displayed. */
    public static BatterySnapshot refreshVehicle(Context context, String vehicleId) {
        VehicleStore store = new VehicleStore(context);
        Vehicle vehicle = store.get(vehicleId);
        BatterySnapshot result;
        try {
            result = VehicleSource.forVehicle(vehicle).fetch(vehicle);
            vehicle.saveSnapshot(result);
        } catch (Exception e) {
            Log.w(TAG, "refresh failed for " + vehicleId, e);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            vehicle.saveError(msg);
            result = vehicle.snapshot();
        } finally {
            vehicle.setRefreshing(false);
        }
        // Pure repaint — NOT updateForVehicle(), which would re-check staleness and, on a
        // failed fetch, immediately reschedule another attempt (see LyriqWidgetProvider).
        LyriqWidgetProvider.repaintForVehicle(context, vehicleId);
        return result;
    }

    /** Refreshes every vehicle that currently has at least one placed widget and is due. */
    public static void refreshDueVehicles(Context context) {
        VehicleStore store = new VehicleStore(context);
        for (String id : store.vehicleIds()) {
            if (!store.hasAnyWidget(id)) continue; // no point spending a poll on an unused vehicle
            Vehicle v = store.get(id);
            long staleMs = v.refreshMinutes() * 60_000L;
            long updatedAt = v.snapshot().updatedAt;
            if (updatedAt == 0 || System.currentTimeMillis() - updatedAt > staleMs) {
                refreshVehicle(context, id);
            }
        }
    }
}
