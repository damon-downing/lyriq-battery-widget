package com.omarzanji.lyriqwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.SizeF;
import android.widget.RemoteViews;

import java.util.HashMap;
import java.util.Map;

/**
 * Home-screen widget. Uses the Android 12+ responsive RemoteViews API: the launcher picks
 * the largest layout whose declared size fits the cell area the user resized to.
 */
public final class LyriqWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "com.omarzanji.lyriqwidget.REFRESH";

    private static final SizeF SMALL = new SizeF(110f, 40f);   // any short row: 2x1, 3x1, 4x1
    private static final SizeF MEDIUM = new SizeF(120f, 140f); // 2x2, 3x2 (needs real height for the ring)
    private static final SizeF LARGE = new SizeF(250f, 120f);  // 4x2 and larger

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        Prefs prefs = new Prefs(context);
        BatterySnapshot snap = prefs.snapshot();
        for (int id : ids) manager.updateAppWidget(id, build(context, prefs, snap));
        // Data older than the refresh interval? Fetch in the background.
        long stale = prefs.refreshMinutes() * 60_000L;
        if (snap.updatedAt == 0 || System.currentTimeMillis() - snap.updatedAt > stale) Scheduler.refreshSoon(context);
    }

    @Override
    public void onEnabled(Context context) {
        Scheduler.schedulePeriodic(context);
        Scheduler.refreshSoon(context);
    }

    @Override
    public void onDisabled(Context context) {
        Scheduler.cancelPeriodic(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_REFRESH.equals(intent.getAction())) {
            Prefs prefs = new Prefs(context);
            prefs.setRefreshing(true);
            updateAll(context);
            Scheduler.refreshSoon(context);
            return;
        }
        super.onReceive(context, intent);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, LyriqWidgetProvider.class));
        if (ids.length == 0) return;
        Prefs prefs = new Prefs(context);
        BatterySnapshot snap = prefs.snapshot();
        for (int id : ids) manager.updateAppWidget(id, build(context, prefs, snap));
    }

    private static RemoteViews build(Context context, Prefs prefs, BatterySnapshot snap) {
        Map<SizeF, RemoteViews> views = new HashMap<>();
        views.put(SMALL, layout(context, prefs, snap, R.layout.widget_small, 34));
        views.put(MEDIUM, layout(context, prefs, snap, R.layout.widget_medium, 92));
        views.put(LARGE, layout(context, prefs, snap, R.layout.widget_large, 96));
        return new RemoteViews(views);
    }

    private static RemoteViews layout(Context context, Prefs prefs, BatterySnapshot snap, int layoutId, int gaugeDp) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), layoutId);
        int px = Math.round(gaugeDp * context.getResources().getDisplayMetrics().density);
        rv.setImageViewBitmap(R.id.gauge, WidgetRenderer.gauge(context, snap, px));
        rv.setTextViewText(R.id.percent, WidgetRenderer.percentText(snap));
        if (layoutId == R.layout.widget_large) {
            rv.setTextViewText(R.id.title, WidgetRenderer.title(context, snap));
            rv.setTextViewText(R.id.subtitle, WidgetRenderer.statusLine(context, snap));
            rv.setTextViewText(R.id.footer, WidgetRenderer.footer(context, prefs, snap));
        } else {
            String line = prefs.isRefreshing() ? context.getString(R.string.refreshing)
                    : snap.hasData() ? WidgetRenderer.statusLine(context, snap)
                    : context.getString(R.string.no_data);
            rv.setTextViewText(R.id.subtitle, line);
        }
        rv.setContentDescription(R.id.widget_root, WidgetRenderer.title(context, snap) + ", "
                + WidgetRenderer.percentText(snap) + ", " + WidgetRenderer.statusLine(context, snap));

        Intent refresh = new Intent(context, LyriqWidgetProvider.class).setAction(ACTION_REFRESH);
        PendingIntent refreshPi = PendingIntent.getBroadcast(context, 0, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent openPi = myCadillacIntent(context);
        if (openPi != null) {
            // Widget body opens myCadillac; the ring stays a refresh button.
            rv.setOnClickPendingIntent(R.id.widget_root, openPi);
            rv.setOnClickPendingIntent(R.id.gauge, refreshPi);
        } else {
            rv.setOnClickPendingIntent(R.id.widget_root, refreshPi);
        }
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
