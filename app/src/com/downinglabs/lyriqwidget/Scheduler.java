package com.downinglabs.lyriqwidget;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;

import java.util.concurrent.TimeUnit;

/** JobScheduler wiring: one periodic job (checks every vehicle that has a widget) plus an
 *  expedited one-off, either for one specific vehicle (a tap) or a general catch-up. */
public final class Scheduler {
    private static final int JOB_PERIODIC = 1001;
    private static final int JOB_ONCE = 1002;

    private Scheduler() {}

    /** Fires at least as often as the shortest refresh interval among vehicles with a placed widget. */
    public static void schedulePeriodic(Context context) {
        VehicleStore store = new VehicleStore(context);
        int minutes = Integer.MAX_VALUE;
        for (String id : store.vehicleIds()) {
            if (!store.hasAnyWidget(id)) continue;
            minutes = Math.min(minutes, store.get(id).refreshMinutes());
        }
        if (minutes == Integer.MAX_VALUE) minutes = 30;

        JobScheduler js = context.getSystemService(JobScheduler.class);
        long interval = TimeUnit.MINUTES.toMillis(minutes);
        JobInfo job = new JobInfo.Builder(JOB_PERIODIC, new ComponentName(context, RefreshJobService.class))
                .setPeriodic(interval, Math.max(JobInfo.getMinFlexMillis(), interval / 4))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build();
        js.schedule(job);
    }

    public static void cancelPeriodic(Context context) {
        context.getSystemService(JobScheduler.class).cancel(JOB_PERIODIC);
    }

    /** Refreshes one specific vehicle right away (e.g. a widget tap). */
    public static void refreshSoon(Context context, String vehicleId) {
        JobScheduler js = context.getSystemService(JobScheduler.class);
        PersistableBundle extras = new PersistableBundle();
        extras.putString(RefreshJobService.EXTRA_VEHICLE_ID, vehicleId);
        JobInfo job = new JobInfo.Builder(JOB_ONCE, new ComponentName(context, RefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExtras(extras)
                .setExpedited(true)
                .build();
        js.schedule(job);
    }

    /** Catch-up pass: refreshes every due vehicle that has a placed widget. */
    public static void refreshDueSoon(Context context) {
        JobScheduler js = context.getSystemService(JobScheduler.class);
        JobInfo job = new JobInfo.Builder(JOB_ONCE, new ComponentName(context, RefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExpedited(true)
                .build();
        js.schedule(job);
    }
}
