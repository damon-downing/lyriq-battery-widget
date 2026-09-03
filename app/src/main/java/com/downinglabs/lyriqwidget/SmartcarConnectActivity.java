package com.downinglabs.lyriqwidget;

import android.app.Activity;
import android.os.Bundle;

import com.smartcar.sdk.SmartcarAuth;
import com.smartcar.sdk.SmartcarCallback;
import com.smartcar.sdk.SmartcarResponse;

/**
 * Launches Smartcar Connect via the real, official Android SDK — proven necessary, not just
 * nicer: GM's login page reliably rejected our own hand-built WebView and Custom Tab attempts
 * (even ones that matched the SDK's parameters, PKCE included) but consistently succeeded
 * through this exact SDK. See AGENTS.md for the full investigation.
 *
 * Redirect is fixed (lyriqwidget://callback, not per-Application-ID) so the Application ID
 * can stay a runtime field — see the SmartcarCodeReceiver entry in AndroidManifest.xml. The
 * SDK's own receiver catches the redirect and hands us back here via this callback; we finish
 * the flow using the same completeConnect()/token logic that already existed before the SDK.
 */
public final class SmartcarConnectActivity extends Activity {
    public static final String EXTRA_VEHICLE_ID = "vehicle_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String vehicleId = getIntent().getStringExtra(EXTRA_VEHICLE_ID);
        final Vehicle vehicle = new VehicleStore(this).get(vehicleId);

        SmartcarAuth smartcarAuth = new SmartcarAuth(
                vehicle.scClientId(),
                SmartcarSource.REDIRECT_URI,
                SmartcarSource.SCOPES.split(" "),
                new SmartcarCallback() {
                    @Override
                    public void handleResponse(final SmartcarResponse response) {
                        finishConnect(vehicle, response);
                    }
                });

        String authUrl = smartcarAuth.authUrlBuilder()
                .setState(vehicle.id)
                .build();
        smartcarAuth.launchAuthFlow(getApplicationContext(), authUrl);
        // Deliberately NOT calling finish() here — this activity (and its callback) needs to
        // stay alive while the user is off in the Custom Tab. It finishes itself once
        // finishConnect() actually runs, whether that's success or failure.
    }

    private void finishConnect(final Vehicle vehicle, final SmartcarResponse response) {
        if (response.getError() != null || response.getCode() == null) {
            finish();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // No getUserId() on this SDK's SmartcarResponse (confirmed by inspecting
                    // the compiled class) — completeConnect() no longer requires one.
                    SmartcarSource.completeConnect(vehicle, response.getCode(), null);
                    vehicle.setSource(VehicleSource.SMARTCAR);
                    Refresher.refreshVehicle(SmartcarConnectActivity.this, vehicle.id);
                } catch (Exception ignored) {
                    // Nothing to show here — surfaced via "Not connected" next time the app opens.
                } finally {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() { finish(); }
                    });
                }
            }
        }, "smartcar-exchange").start();
    }
}

