package com.downinglabs.lyriqwidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * The app's launcher screen AND the OS widget-placement "configure" target — this list IS the
 * picker. Opening the app never lands on a credential form; that only happens when you
 * explicitly tap + or the pencil on a specific vehicle.
 *
 * Two modes, same screen:
 *  - Normal (opened from the launcher icon): manage vehicles — add, edit, remove.
 *  - Picker (opened by the OS after dragging the widget onto the home screen, carrying
 *    EXTRA_APPWIDGET_ID): tapping a vehicle row binds this new widget instance to it and
 *    finishes immediately; + still adds a new vehicle first, then binds it.
 */
public final class VehicleListActivity extends Activity {
    private static final int REQ_ADD = 100;
    private static final int REQ_EDIT = 101;

    private VehicleStore store;
    private boolean pickerMode;
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    private android.widget.LinearLayout rowsContainer;
    private TextView emptyState, pickerHint;
    private android.widget.Button btnAdd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new VehicleStore(this);

        appWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        pickerMode = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID;

        // Placing a widget with exactly one vehicle configured: just use it, no picker needed.
        if (pickerMode) {
            List<String> ids = store.vehicleIds();
            if (ids.size() == 1) {
                selectForWidget(ids.get(0));
                return;
            }
        }

        setContentView(R.layout.activity_vehicle_list);
        rowsContainer = findViewById(R.id.vehicle_rows);
        emptyState = findViewById(R.id.empty_state);
        pickerHint = findViewById(R.id.picker_hint);
        pickerHint.setVisibility(pickerMode ? View.VISIBLE : View.GONE);

        findViewById(R.id.btn_add_vehicle).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(VehicleListActivity.this, VehicleConfigActivity.class);
                if (pickerMode) i.putExtra(VehicleConfigActivity.EXTRA_APPWIDGET_ID, appWidgetId);
                startActivityForResult(i, REQ_ADD);
            }
        });
        btnAdd = findViewById(R.id.btn_add_vehicle);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void refreshList() {
        rowsContainer.removeAllViews();
        List<String> ids = store.vehicleIds();
        emptyState.setVisibility(ids.isEmpty() ? View.VISIBLE : View.GONE);
        // Single-vehicle app: once one exists, there's nothing left to add outside picker mode.
        btnAdd.setVisibility(!ids.isEmpty() && !pickerMode ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (final String id : ids) {
            final Vehicle vehicle = store.get(id);
            View row = inflater.inflate(R.layout.row_vehicle, rowsContainer, false);
            ((TextView) row.findViewById(R.id.row_name)).setText(vehicle.displayName());
            ((TextView) row.findViewById(R.id.row_source)).setText(
                    pickerMode ? "Tap to assign to this widget" : vehicle.sourceLabel());

            if (pickerMode) {
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) { selectForWidget(id); }
                });
            }
            ((ImageButton) row.findViewById(R.id.row_edit)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(VehicleListActivity.this, VehicleConfigActivity.class)
                            .putExtra(VehicleConfigActivity.EXTRA_VEHICLE_ID, id);
                    startActivityForResult(i, REQ_EDIT);
                }
            });
            ((ImageButton) row.findViewById(R.id.row_delete)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { confirmDelete(id, vehicle.displayName()); }
            });
            rowsContainer.addView(row);
        }
    }

    private void confirmDelete(final String id, String name) {
        new AlertDialog.Builder(this)
                .setTitle("Remove " + name + "?")
                .setMessage("Any widgets showing this vehicle will stop updating until reconfigured.")
                .setPositiveButton("Remove", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        store.delete(id);
                        refreshList();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Picker mode: bind the widget the OS is placing to this vehicle, then hand control back. */
    private void selectForWidget(String vehicleId) {
        store.bindWidget(appWidgetId, vehicleId);
        LyriqWidgetProvider.updateForVehicle(this, vehicleId);
        Scheduler.schedulePeriodic(this);
        finishPickerSuccess();
    }

    private void finishPickerSuccess() {
        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ADD && pickerMode && resultCode == RESULT_OK) {
            // VehicleConfigActivity already bound appWidgetId to the newly-created vehicle.
            finishPickerSuccess();
            return;
        }
        refreshList();
    }
}
