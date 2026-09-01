package com.omarzanji.lyriqwidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** Settings screen: pick a data source, connect it, and place the widget. */
public final class MainActivity extends Activity {
    private Prefs prefs;

    private RadioGroup sourceGroup;
    private View sectionSmartcar, sectionHa, sectionManual;
    private EditText scClientId, scClientSecret, haUrl, haToken, haEntityBattery, haEntityRange, haEntityCharging, refreshMinutes;
    private CheckBox scSimulated, manualCharging;
    private TextView scRedirectUri, scStatus, manualLabel, statusPercent, statusLine;
    private SeekBar manualPercent;
    private ImageView statusGauge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        setContentView(R.layout.activity_main);

        sourceGroup = findViewById(R.id.source_group);
        sectionSmartcar = findViewById(R.id.section_smartcar);
        sectionHa = findViewById(R.id.section_ha);
        sectionManual = findViewById(R.id.section_manual);
        scClientId = findViewById(R.id.sc_client_id);
        scClientSecret = findViewById(R.id.sc_client_secret);
        scSimulated = findViewById(R.id.sc_simulated);
        scRedirectUri = findViewById(R.id.sc_redirect_uri);
        scStatus = findViewById(R.id.sc_status);
        haUrl = findViewById(R.id.ha_url);
        haToken = findViewById(R.id.ha_token);
        haEntityBattery = findViewById(R.id.ha_entity_battery);
        haEntityRange = findViewById(R.id.ha_entity_range);
        haEntityCharging = findViewById(R.id.ha_entity_charging);
        manualLabel = findViewById(R.id.manual_label);
        manualPercent = findViewById(R.id.manual_percent);
        manualCharging = findViewById(R.id.manual_charging);
        refreshMinutes = findViewById(R.id.refresh_minutes);
        statusPercent = findViewById(R.id.status_percent);
        statusLine = findViewById(R.id.status_line);
        statusGauge = findViewById(R.id.status_gauge);

        loadIntoForm();

        sourceGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int id) { showSection(id); }
        });
        scClientId.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { updateRedirectUri(); }
        });
        manualPercent.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int p, boolean u) { manualLabel.setText("Battery: " + p + "%"); }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });

        Button save = findViewById(R.id.btn_save);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (saveForm()) {
                    Toast.makeText(MainActivity.this, "Saved", Toast.LENGTH_SHORT).show();
                    refresh();
                }
            }
        });
        findViewById(R.id.btn_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { if (saveForm()) refresh(); }
        });
        findViewById(R.id.btn_add_widget).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { requestPin(); }
        });
        findViewById(R.id.btn_sc_connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { connectSmartcar(); }
        });
        findViewById(R.id.btn_sc_disconnect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.clearSmartcarConnection();
                updateSmartcarStatus();
                Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderStatus(prefs.snapshot());
        updateSmartcarStatus();
    }

    private void loadIntoForm() {
        switch (prefs.source()) {
            case VehicleSource.SMARTCAR: sourceGroup.check(R.id.source_smartcar); break;
            case VehicleSource.HOME_ASSISTANT: sourceGroup.check(R.id.source_ha); break;
            default: sourceGroup.check(R.id.source_manual);
        }
        showSection(sourceGroup.getCheckedRadioButtonId());
        scClientId.setText(prefs.scClientId());
        scClientSecret.setText(prefs.scClientSecret());
        scSimulated.setChecked(prefs.scSimulated());
        updateRedirectUri();
        haUrl.setText(prefs.haUrl());
        haToken.setText(prefs.haToken());
        haEntityBattery.setText(prefs.haEntityBattery());
        haEntityRange.setText(prefs.haEntityRange());
        haEntityCharging.setText(prefs.haEntityCharging());
        manualPercent.setProgress(prefs.manualPercent());
        manualLabel.setText("Battery: " + prefs.manualPercent() + "%");
        manualCharging.setChecked(prefs.manualCharging());
        refreshMinutes.setText(String.valueOf(prefs.refreshMinutes()));
    }

    private void showSection(int checkedId) {
        sectionSmartcar.setVisibility(checkedId == R.id.source_smartcar ? View.VISIBLE : View.GONE);
        sectionHa.setVisibility(checkedId == R.id.source_ha ? View.VISIBLE : View.GONE);
        sectionManual.setVisibility(checkedId == R.id.source_manual ? View.VISIBLE : View.GONE);
    }

    private void updateRedirectUri() {
        String id = scClientId.getText().toString().trim();
        scRedirectUri.setText(id.isEmpty() ? "Redirect URI: (enter client ID)" : "Redirect URI: " + SmartcarSource.redirectUri(id));
    }

    private void updateSmartcarStatus() {
        scStatus.setText(prefs.scConnected()
                ? "Connected" + (prefs.scVehicleId().isEmpty() ? "" : " · vehicle " + prefs.scVehicleId().substring(0, 8) + "…")
                : "Not connected");
    }

    /** Persists the form. Returns false (with a toast) when a required field is missing. */
    private boolean saveForm() {
        int checked = sourceGroup.getCheckedRadioButtonId();
        String source = checked == R.id.source_smartcar ? VehicleSource.SMARTCAR
                : checked == R.id.source_ha ? VehicleSource.HOME_ASSISTANT : VehicleSource.MANUAL;
        prefs.setSmartcarApp(scClientId.getText().toString(), scClientSecret.getText().toString(), scSimulated.isChecked());
        prefs.setHomeAssistant(haUrl.getText().toString(), haToken.getText().toString(),
                haEntityBattery.getText().toString(), haEntityRange.getText().toString(), haEntityCharging.getText().toString());
        prefs.setManual(manualPercent.getProgress(), manualCharging.isChecked());
        int minutes;
        try { minutes = Integer.parseInt(refreshMinutes.getText().toString().trim()); } catch (NumberFormatException e) { minutes = 0; }
        if (minutes < 15) {
            minutes = VehicleSource.SMARTCAR.equals(source) ? 120 : 30;
            refreshMinutes.setText(String.valueOf(minutes));
        }
        prefs.setRefreshMinutes(minutes);
        prefs.setSource(source);
        Scheduler.schedulePeriodic(this);
        return true;
    }

    private void connectSmartcar() {
        if (!saveForm()) return;
        if (prefs.scClientId().isEmpty() || prefs.scClientSecret().isEmpty()) {
            Toast.makeText(this, "Enter the Smartcar client ID and secret first", Toast.LENGTH_LONG).show();
            return;
        }
        sourceGroup.check(R.id.source_smartcar);
        startActivity(new Intent(this, SmartcarConnectActivity.class));
    }

    private void refresh() {
        statusLine.setText("Refreshing…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final BatterySnapshot snap = Refresher.refreshNow(MainActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        renderStatus(snap);
                        if (snap.error != null) Toast.makeText(MainActivity.this, snap.error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "lyriq-manual-refresh").start();
    }

    private void renderStatus(BatterySnapshot snap) {
        int px = Math.round(84 * getResources().getDisplayMetrics().density);
        statusGauge.setImageBitmap(WidgetRenderer.gauge(this, snap, px));
        statusPercent.setText(WidgetRenderer.percentText(snap));
        statusLine.setText(WidgetRenderer.statusLine(this, snap) + "\n" + WidgetRenderer.footer(this, prefs, snap));
    }

    private void requestPin() {
        AppWidgetManager m = getSystemService(AppWidgetManager.class);
        if (m != null && m.isRequestPinAppWidgetSupported()) {
            m.requestPinAppWidget(new ComponentName(this, LyriqWidgetProvider.class), null, null);
        } else {
            Toast.makeText(this, "Long-press the home screen → Widgets → LYRIQ Battery", Toast.LENGTH_LONG).show();
        }
    }
}
