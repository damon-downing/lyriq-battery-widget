package com.downinglabs.lyriqwidget;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.PersistableBundle;

/**
 * One job class handles both cases: the periodic job (no "vehicle_id" extra) refreshes every
 * due vehicle that has a placed widget; the expedited one-off (fired with a "vehicle_id"
 * extra, e.g. from a widget tap) refreshes just that vehicle immediately.
 */
public final class RefreshJobService extends JobService {
    static final String EXTRA_VEHICLE_ID = "vehicle_id";

    private volatile Thread worker;

    @Override
    public boolean onStartJob(final JobParameters params) {
        final PersistableBundle extras = params.getExtras();
        final String vehicleId = extras == null ? null : extras.getString(EXTRA_VEHICLE_ID);
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (vehicleId != null && !vehicleId.isEmpty()) {
                        Refresher.refreshVehicle(RefreshJobService.this, vehicleId);
                    } else {
                        Refresher.refreshDueVehicles(RefreshJobService.this);
                    }
                } finally {
                    jobFinished(params, false);
                }
            }
        }, "lyriq-refresh");
        worker.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Thread t = worker;
        if (t != null) t.interrupt();
        return true; // reschedule
    }
}
