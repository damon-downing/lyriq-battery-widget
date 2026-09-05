package com.downinglabs.lyriqwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.SizeF;
import android.widget.RemoteViews;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Home-screen widget. Each placed instance (appWidgetId) is bound to exactly one Vehicle via
 * VehicleStore, set during the configure flow (VehicleListActivity). Uses the Android 12+
 * responsive RemoteViews API: the launcher picks the largest layout whose declared size fits
 * the cell area the user resized to.
 */
public final class LyriqWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "com.downinglabs.lyriqwidget.REFRESH";
    public static final String EXTRA_WIDGET_ID = AppWidgetManager.EXTRA_APPWIDGET_ID;
    private static final String TAG = "LyriqRefresh";

    private static final SizeF SMALL = new SizeF(110f, 40f);   // any short row: 2x1, 3x1, 4x1
    private static final SizeF MEDIUM = new SizeF(120f, 140f); // 2x2, 3x2 (needs real height for the ring)
    private static final SizeF LARGE = new SizeF(250f, 120f);  // 4x2 and larger

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        Log.i(TAG, "onUpdate: ids=" + Arrays.toString(ids));
        VehicleStore store = new VehicleStore(context);
        for (int id : ids) updateOne(context, manager, store, id);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        Log.i(TAG, "onDeleted: ids=" + Arrays.toString(appWidgetIds));
        VehicleStore store = new VehicleStore(context);
        for (int id : appWidgetIds) store.unbindWidget(id);
    }

    @Override
    public void onEnabled(Context context) {
        Log.i(TAG, "onEnabled");
        Scheduler.schedulePeriodic(context);
        Scheduler.refreshDueSoon(context);
    }

    @Override
    public void onDisabled(Context context) {
        Scheduler.cancelPeriodic(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_REFRESH.equals(intent.getAction())) {
            int widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            VehicleStore store = new VehicleStore(context);
            String vehicleId = widgetId == AppWidgetManager.INVALID_APPWIDGET_ID ? null : store.vehicleIdForWidget(widgetId);
            if (vehicleId != null) {
                store.get(vehicleId).setRefreshing(true);
                updateForVehicle(context, vehicleId);
                Scheduler.refreshSoon(context, vehicleId);
            }
            return;
        }
        super.onReceive(context, intent);
    }

    /** Repaints just the widgets bound to one vehicle, and kicks a refresh if the data is stale.
     *  Only call this from a real widget-lifecycle entry point (onUpdate, onEnabled, a config
     *  save, a widget just getting bound) — NEVER from inside a refresh's own completion
     *  handler. A failed fetch never updates the success timestamp, so if the post-fetch
     *  repaint used this method too, "still stale" would immediately reschedule another
     *  refresh, which fails, which repaints, which reschedules — a tight, silent, self-sustaining
     *  retry loop with nothing to break it. Use repaintForVehicle() there instead. */
    public static void updateForVehicle(Context context, String vehicleId) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        VehicleStore store = new VehicleStore(context);
        for (int id : store.widgetIdsForVehicle(vehicleId)) updateOne(context, manager, store, id);
    }

    /** Pure repaint, no staleness check, no refresh scheduled. Use right after a refresh
     *  completes (success or failure) — the fetch that just ran already answers the "is this
     *  due" question; re-asking it here is what caused the retry-loop bug above. */
    public static void repaintForVehicle(Context context, String vehicleId) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        VehicleStore store = new VehicleStore(context);
        for (int id : store.widgetIdsForVehicle(vehicleId)) paintOne(context, manager, store, id);
    }

    private static void paintOne(Context context, AppWidgetManager manager, VehicleStore store, int widgetId) {
        String vehicleId = store.vehicleIdForWidget(widgetId);
        if (vehicleId == null) return;
        Vehicle vehicle = store.get(vehicleId);
        manager.updateAppWidget(widgetId, build(context, widgetId, vehicle, vehicle.snapshot()));
    }

    private static void updateOne(Context context, AppWidgetManager manager, VehicleStore store, int widgetId) {
        String vehicleId = store.vehicleIdForWidget(widgetId);
        if (vehicleId == null) return;
        paintOne(context, manager, store, widgetId);
        Vehicle vehicle = store.get(vehicleId);
        BatterySnapshot snap = vehicle.snapshot();
        long stale = vehicle.refreshMinutes() * 60_000L;
        boolean isStale = snap.updatedAt == 0 || System.currentTimeMillis() - snap.updatedAt > stale;
        Log.i(TAG, "updateOne: widget=" + widgetId + " vehicle=" + vehicleId
                + " updatedAt=" + snap.updatedAt + " staleAfterMs=" + stale + " stale=" + isStale);
        if (isStale) {
            Scheduler.refreshSoon(context, vehicleId);
        }
    }

    /** Which views a layout contains, so we never touch an id that isn't there. */
    private static final class Spec {
        static final int HERO_NONE = 0, HERO_RING = 1, HERO_CAR = 2;
        final int layout, hero, heroW, heroH, barW;
        final boolean title, footer;
        Spec(int layout, int hero, int heroW, int heroH, int barW, boolean title, boolean footer) {
            this.layout = layout; this.hero = hero; this.heroW = heroW; this.heroH = heroH;
            this.barW = barW; this.title = title; this.footer = footer;
        }
    }

    private static Spec[] specs(String style) {
        switch (style) {
            case Vehicle.STYLE_CAR:
                return new Spec[]{
                        new Spec(R.layout.widget_car_small, Spec.HERO_CAR, 80, 36, 0, false, false),
                        new Spec(R.layout.widget_car_medium, Spec.HERO_CAR, 160, 70, 140, false, false),
                        new Spec(R.layout.widget_car_large, Spec.HERO_CAR, 200, 90, 140, true, true),
                };
            case Vehicle.STYLE_BAR:
                return new Spec[]{
                        new Spec(R.layout.widget_bar_small, Spec.HERO_NONE, 0, 0, 110, false, false),
                        new Spec(R.layout.widget_bar_medium, Spec.HERO_NONE, 0, 0, 120, true, false),
                        new Spec(R.layout.widget_bar_large, Spec.HERO_NONE, 0, 0, 220, true, true),
                };
            default:
                return new Spec[]{
                        new Spec(R.layout.widget_small, Spec.HERO_RING, 34, 34, 0, false, false),
                        new Spec(R.layout.widget_medium, Spec.HERO_RING, 92, 92, 0, false, false),
                        new Spec(R.layout.widget_large, Spec.HERO_RING, 96, 96, 0, true, true),
                };
        }
    }

    private static RemoteViews build(Context context, int widgetId, Vehicle vehicle, BatterySnapshot snap) {
        Spec[] sp = specs(vehicle.widgetStyle());
        Map<SizeF, RemoteViews> views = new HashMap<>();
        views.put(SMALL, layout(context, widgetId, vehicle, snap, sp[0]));
        views.put(MEDIUM, layout(context, widgetId, vehicle, snap, sp[1]));
        views.put(LARGE, layout(context, widgetId, vehicle, snap, sp[2]));
        return new RemoteViews(views);
    }

    private static int dp(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    private static RemoteViews layout(Context context, int widgetId, Vehicle vehicle, BatterySnapshot snap, Spec spec) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), spec.layout);
        if (spec.hero == Spec.HERO_RING) {
            rv.setImageViewBitmap(R.id.gauge, WidgetRenderer.gauge(context, snap, dp(context, spec.heroW)));
        } else if (spec.hero == Spec.HERO_CAR) {
            rv.setImageViewBitmap(R.id.gauge, CarRenderer.car(context, vehicle.carColor(), snap.charging,
                    dp(context, spec.heroW), dp(context, spec.heroH)));
            // Only Car layouts have a lock_icon view (see widget_car_*.xml) — touching this id
            // on Ring/Bar would break them, per the RemoteViews id rule in AGENTS.md.
            rv.setImageViewBitmap(R.id.lock_icon, WidgetRenderer.lockIcon(context, snap.locked, dp(context, 18)));
        }
        if (spec.barW > 0) {
            rv.setImageViewBitmap(R.id.bar, CarRenderer.bar(context, snap, dp(context, spec.barW), dp(context, 8)));
        }
        rv.setTextViewText(R.id.percent, WidgetRenderer.percentText(snap));
        if (spec.title) rv.setTextViewText(R.id.title, WidgetRenderer.title(context, vehicle, snap));
        if (spec.footer) {
            rv.setTextViewText(R.id.subtitle, WidgetRenderer.statusLine(context, snap));
            rv.setTextViewText(R.id.footer, WidgetRenderer.footer(context, vehicle, snap));
        } else {
            String line = vehicle.isRefreshing() ? context.getString(R.string.refreshing)
                    : snap.hasData() ? WidgetRenderer.statusLine(context, snap)
                    : context.getString(R.string.no_data);
            rv.setTextViewText(R.id.subtitle, line);
        }
        rv.setContentDescription(R.id.widget_root, WidgetRenderer.title(context, vehicle, snap) + ", "
                + WidgetRenderer.percentText(snap) + ", " + WidgetRenderer.statusLine(context, snap));

        // widgetId is the PendingIntent request code too, so each placed instance's refresh tap
        // stays distinct from every other instance's (otherwise FLAG_UPDATE_CURRENT would collide them).
        Intent refresh = new Intent(context, LyriqWidgetProvider.class)
                .setAction(ACTION_REFRESH)
                .putExtra(EXTRA_WIDGET_ID, widgetId);
        PendingIntent refreshPi = PendingIntent.getBroadcast(context, widgetId, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // Tapping anywhere on the tile refreshes everything (battery + lock, same /signals
        // call). The old "background opens myCadillac" behavior is on hold — see AGENTS.md —
        // pending the caching investigation; myCadillacIntent() is kept for when it's revisited.
        rv.setOnClickPendingIntent(R.id.widget_root, refreshPi);
        return rv;
    }

    private static final String[] MYCADILLAC_PACKAGES = {"com.gm.cadillac.nomad.ownership", "com.gm.myCadillac"};

    /** Launcher intent for the installed myCadillac app, or null when it isn't installed. */
    private static PendingIntent myCadillacIntent(Context context) {
        for (String pkg : MYCADILLAC_PACKAGES) {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) continue;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            return PendingIntent.getActivity(context, 1, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        return null;
    }
}
