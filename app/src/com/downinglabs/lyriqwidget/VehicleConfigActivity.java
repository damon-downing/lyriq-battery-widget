package com.downinglabs.lyriqwidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
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
import android.widget.TextView;
import android.widget.Toast;

/**
 * Add/edit screen for exactly one vehicle. Reached only from VehicleListActivity — never the
 * launcher, and never opens with a saved secret already on screen unless the user chose to
 * edit that specific vehicle. Save always returns to the list (or, when placing a new widget,
 * binds the widget to this vehicle and returns straight to the OS placement flow).
 */
public final class VehicleConfigActivity extends Activity {
    public static final String EXTRA_VEHICLE_ID = "vehicle_id";
    public static final String EXTRA_APPWIDGET_ID = AppWidgetManager.EXTRA_APPWIDGET_ID;

    private VehicleStore store;
    private Vehicle vehicle;
    private boolean registered;
    private int pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    private RadioGroup sourceGroup, styleGroup;
    private View sectionCarColor;
    private android.widget.LinearLayout colorRow;
    private EditText carColorHex;
    private TextView colorName;
    private int selectedColor;
    private View sectionSmartcar, sectionHa, sectionManual;
    private EditText scClientId, scTokenClientId, scClientSecret, haUrl, haToken, haEntityBattery, haEntityRange, haEntityCharging, refreshMinutes, nickname;
    private CheckBox scSimulated, manualCharging;
    private TextView scRedirectUri, scStatus, statusPercent, statusLine, manualLabel;
    private android.widget.SeekBar manualPercent;
    private ImageView statusGauge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new VehicleStore(this);

        String vehicleId = getIntent().getStringExtra(EXTRA_VEHICLE_ID);
        registered = vehicleId != null;
        vehicle = store.get(registered ? vehicleId : store.newId());
        pendingWidgetId = getIntent().getIntExtra(EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        setContentView(R.layout.activity_vehicle_config);

        nickname = findViewById(R.id.nickname);
        sourceGroup = findViewById(R.id.source_group);
        styleGroup = findViewById(R.id.style_group);
        sectionCarColor = findViewById(R.id.section_car_color);
        colorRow = findViewById(R.id.color_row);
        carColorHex = findViewById(R.id.car_color_hex);
        colorName = findViewById(R.id.color_name);
        sectionSmartcar = findViewById(R.id.section_smartcar);
        sectionHa = findViewById(R.id.section_ha);
        sectionManual = findViewById(R.id.section_manual);
        scClientId = findViewById(R.id.sc_client_id);
        scTokenClientId = findViewById(R.id.sc_token_client_id);
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

        styleGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int id) {
                sectionCarColor.setVisibility(id == R.id.style_car ? View.VISIBLE : View.GONE);
                renderStatus(vehicle.snapshot());
            }
        });
        carColorHex.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                Integer c = parseHex(s.toString());
                if (c != null && c != selectedColor) { selectedColor = c; buildSwatches(); renderStatus(vehicle.snapshot()); }
            }
        });
        sourceGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int id) { showSection(id); }
        });
        scClientId.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { updateRedirectUri(); }
        });
        manualPercent.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar sb, int p, boolean u) { manualLabel.setText("Battery: " + p + "%"); }
            public void onStartTrackingTouch(android.widget.SeekBar sb) {}
            public void onStopTrackingTouch(android.widget.SeekBar sb) {}
        });

        Button save = findViewById(R.id.btn_save);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { saveAndReturn(); }
        });
        findViewById(R.id.btn_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { saveForm(); refresh(); }
        });
        findViewById(R.id.btn_sc_connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { connectSmartcar(); }
        });
        findViewById(R.id.btn_sc_disconnect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vehicle.clearSmartcarConnection();
                updateSmartcarStatus();
                Toast.makeText(VehicleConfigActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderStatus(vehicle.snapshot());
        updateSmartcarStatus();
    }

    private void loadIntoForm() {
        nickname.setText(vehicle.nickname());
        switch (vehicle.source()) {
            case VehicleSource.HOME_ASSISTANT: sourceGroup.check(R.id.source_ha); break;
            case VehicleSource.MANUAL: sourceGroup.check(R.id.source_manual); break;
            default: sourceGroup.check(R.id.source_smartcar);
        }
        showSection(sourceGroup.getCheckedRadioButtonId());
        switch (vehicle.widgetStyle()) {
            case Vehicle.STYLE_CAR: styleGroup.check(R.id.style_car); break;
            case Vehicle.STYLE_BAR: styleGroup.check(R.id.style_bar); break;
            default: styleGroup.check(R.id.style_ring);
        }
        sectionCarColor.setVisibility(vehicle.widgetStyle().equals(Vehicle.STYLE_CAR) ? View.VISIBLE : View.GONE);
        selectedColor = vehicle.carColor();
        carColorHex.setText(String.format(java.util.Locale.US, "#%06X", selectedColor & 0xFFFFFF));
        buildSwatches();
        scClientId.setText(vehicle.scClientId());
        scTokenClientId.setText(vehicle.scTokenClientId());
        scClientSecret.setText(vehicle.scClientSecret());
        scSimulated.setChecked(vehicle.scSimulated());
        updateRedirectUri();
        haUrl.setText(vehicle.haUrl());
        haToken.setText(vehicle.haToken());
        haEntityBattery.setText(vehicle.haEntityBattery());
        haEntityRange.setText(vehicle.haEntityRange());
        haEntityCharging.setText(vehicle.haEntityCharging());
        manualPercent.setProgress(vehicle.manualPercent());
        manualLabel.setText("Battery: " + vehicle.manualPercent() + "%");
        manualCharging.setChecked(vehicle.manualCharging());
        refreshMinutes.setText(String.valueOf(vehicle.refreshMinutes()));
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
        if (!vehicle.scConnected()) { scStatus.setText("Not connected"); return; }
        String v = vehicle.scVehicleId();
        scStatus.setText("Connected (" + vehicle.scApiVersion().toUpperCase(java.util.Locale.US) + ")"
                + (v.isEmpty() ? "" : " · vehicle " + v.substring(0, Math.min(8, v.length())) + "…"));
    }

    /** Persists the form onto {@link #vehicle} and registers it in the store if it's new. */
    private void saveForm() {
        int checked = sourceGroup.getCheckedRadioButtonId();
        String source = checked == R.id.source_ha ? VehicleSource.HOME_ASSISTANT
                : checked == R.id.source_manual ? VehicleSource.MANUAL : VehicleSource.SMARTCAR;
        vehicle.setNickname(nickname.getText().toString());
        vehicle.setSmartcarApp(scClientId.getText().toString(), scTokenClientId.getText().toString(), scClientSecret.getText().toString(), scSimulated.isChecked());
        vehicle.setHomeAssistant(haUrl.getText().toString(), haToken.getText().toString(),
                haEntityBattery.getText().toString(), haEntityRange.getText().toString(), haEntityCharging.getText().toString());
        vehicle.setManual(manualPercent.getProgress(), manualCharging.isChecked());
        int minutes;
        try { minutes = Integer.parseInt(refreshMinutes.getText().toString().trim()); } catch (NumberFormatException e) { minutes = 0; }
        if (minutes < 15) {
            minutes = VehicleSource.SMARTCAR.equals(source) ? 120 : 30;
            refreshMinutes.setText(String.valueOf(minutes));
        }
        vehicle.setRefreshMinutes(minutes);
        vehicle.setSource(source);
        int styleId = styleGroup.getCheckedRadioButtonId();
        vehicle.setWidgetStyle(styleId == R.id.style_car ? Vehicle.STYLE_CAR : styleId == R.id.style_bar ? Vehicle.STYLE_BAR : Vehicle.STYLE_RING);
        Integer hex = parseHex(carColorHex.getText().toString());
        vehicle.setCarColor(hex != null ? hex : selectedColor);
        if (!registered) { store.register(vehicle.id); registered = true; }
        Scheduler.schedulePeriodic(this);
    }

    /** Save button: persist, then either bind the in-progress widget placement or go back to the list. */
    private void saveAndReturn() {
        saveForm();
        if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            store.bindWidget(pendingWidgetId, vehicle.id);
            LyriqWidgetProvider.updateForVehicle(this, vehicle.id);
            setResult(RESULT_OK);
        } else {
            LyriqWidgetProvider.updateForVehicle(this, vehicle.id);
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private void connectSmartcar() {
        saveForm();
        if (vehicle.scClientId().isEmpty() || vehicle.scClientSecret().isEmpty()) {
            Toast.makeText(this, "Enter the Smartcar client ID and secret first", Toast.LENGTH_LONG).show();
            return;
        }
        sourceGroup.check(R.id.source_smartcar);
        startActivity(new Intent(this, SmartcarConnectActivity.class)
                .putExtra(SmartcarConnectActivity.EXTRA_VEHICLE_ID, vehicle.id));
    }

    private void refresh() {
        statusLine.setText("Refreshing…");
        final String id = vehicle.id;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final BatterySnapshot snap = Refresher.refreshVehicle(VehicleConfigActivity.this, id);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        renderStatus(snap);
                        if (snap.error != null) Toast.makeText(VehicleConfigActivity.this, snap.error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "lyriq-manual-refresh").start();
    }

    private String currentStyle() {
        int id = styleGroup.getCheckedRadioButtonId();
        return id == R.id.style_car ? Vehicle.STYLE_CAR : id == R.id.style_bar ? Vehicle.STYLE_BAR : Vehicle.STYLE_RING;
    }

    private static Integer parseHex(String text) {
        String t = text.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() != 6) return null;
        try { return 0xFF000000 | Integer.parseInt(t, 16); } catch (NumberFormatException e) { return null; }
    }

    private void buildSwatches() {
        colorRow.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        int size = Math.round(44 * d), gap = Math.round(10 * d);
        String name = "Custom";
        for (int i = 0; i < CarRenderer.PAINT_COLORS.length; i++) {
            final int color = CarRenderer.PAINT_COLORS[i];
            final String paint = CarRenderer.PAINT_NAMES[i];
            boolean selected = color == selectedColor;
            if (selected) name = paint;
            android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
            dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dot.setColor(color);
            dot.setStroke(Math.round((selected ? 3 : 1) * d), selected ? getColor(R.color.gauge_fill) : getColor(R.color.widget_text_secondary));
            View v = new View(this);
            v.setBackground(dot);
            v.setContentDescription(paint);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(gap);
            v.setLayoutParams(lp);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    selectedColor = color;
                    carColorHex.setText(String.format(java.util.Locale.US, "#%06X", color & 0xFFFFFF));
                    buildSwatches();
                    renderStatus(vehicle.snapshot());
                }
            });
            colorRow.addView(v);
        }
        colorName.setText(name);
    }

    private void renderStatus(BatterySnapshot snap) {
        float d = getResources().getDisplayMetrics().density;
        if (Vehicle.STYLE_CAR.equals(currentStyle())) {
            statusGauge.setImageBitmap(CarRenderer.car(this, selectedColor, snap.charging, Math.round(130 * d), Math.round(50 * d)));
        } else {
            statusGauge.setImageBitmap(WidgetRenderer.gauge(this, snap, Math.round(84 * d)));
        }
        statusPercent.setText(WidgetRenderer.percentText(snap));
        statusLine.setText(WidgetRenderer.statusLine(this, snap) + "\n" + WidgetRenderer.footer(this, vehicle, snap));
    }
}
