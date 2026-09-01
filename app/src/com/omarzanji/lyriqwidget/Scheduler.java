package com.omarzanji.lyriqwidget;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import java.util.concurrent.TimeUnit;

/** JobScheduler wiring: one periodic job plus an expedited one-off for taps. */
public final class Scheduler {
    private static final int JOB_PERIODIC = 1001;
    private static final int JOB_ONCE = 1002;

    private Scheduler() {}

    public static void schedulePeriodic(Context context) {
        JobScheduler js = context.getSystemService(JobScheduler.class);
        long interval = TimeUnit.MINUTES.toMillis(new Prefs(context).refreshMinutes());
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

    public static void refreshSoon(Context context) {
        JobScheduler js = context.getSystemService(JobScheduler.class);
        JobInfo job = new JobInfo.Builder(JOB_ONCE, new ComponentName(context, RefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExpedited(true)
                .build();
        js.schedule(job);
    }
}
