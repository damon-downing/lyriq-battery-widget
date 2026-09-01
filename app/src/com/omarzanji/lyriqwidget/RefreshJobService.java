package com.omarzanji.lyriqwidget;

import android.app.job.JobParameters;
import android.app.job.JobService;

public final class RefreshJobService extends JobService {
    private volatile Thread worker;

    @Override
    public boolean onStartJob(final JobParameters params) {
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Refresher.refreshNow(RefreshJobService.this);
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
