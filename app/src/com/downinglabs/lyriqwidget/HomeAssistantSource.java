package com.downinglabs.lyriqwidget;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Reads entity states from Home Assistant's REST API (/api/states/<entity_id>). */
public final class HomeAssistantSource implements VehicleSource {
    @Override
    public BatterySnapshot fetch(Vehicle vehicle) throws Exception {
        String base = vehicle.haUrl();
        String token = vehicle.haToken();
        String batteryEntity = vehicle.haEntityBattery();
        if (base.isEmpty() || token.isEmpty() || batteryEntity.isEmpty()) {
            throw new IllegalStateException("Home Assistant URL, token and battery entity are required");
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);

        JSONObject battery = state(base, headers, batteryEntity);
        int percent = (int) Math.round(parseNumber(battery.optString("state")));
        if (percent < 0 || percent > 100) throw new IllegalStateException("Battery entity state is not a percentage: " + battery.optString("state"));

        double rangeMiles = -1;
        if (!vehicle.haEntityRange().isEmpty()) {
            JSONObject range = state(base, headers, vehicle.haEntityRange());
            double v = parseNumber(range.optString("state"));
            if (!Double.isNaN(v)) {
                String unit = range.optJSONObject("attributes") == null ? "" : range.optJSONObject("attributes").optString("unit_of_measurement", "");
                rangeMiles = unit.toLowerCase(Locale.US).startsWith("km") ? v * 0.621371 : v;
            }
        }

        boolean charging = false;
        if (!vehicle.haEntityCharging().isEmpty()) {
            String s = state(base, headers, vehicle.haEntityCharging()).optString("state", "").toLowerCase(Locale.US);
            charging = s.equals("on") || s.equals("true") || s.contains("charging") && !s.contains("not");
        }

        String name = battery.optJSONObject("attributes") == null ? "" : battery.optJSONObject("attributes").optString("friendly_name", "");
        if (name.isEmpty()) name = "Cadillac LYRIQ";
        return new BatterySnapshot(percent, rangeMiles, charging, charging, System.currentTimeMillis(), name, null);
    }

    private static JSONObject state(String base, Map<String, String> headers, String entity) throws Exception {
        Http.Response r = Http.request("GET", base + "/api/states/" + entity, headers, null, null);
        if (r.code == 401 || r.code == 403) throw new IllegalStateException("Home Assistant rejected the token");
        if (r.code == 404) throw new IllegalStateException("Entity not found: " + entity);
        if (!r.ok()) throw new IllegalStateException("Home Assistant HTTP " + r.code);
        return new JSONObject(r.body);
    }

    private static double parseNumber(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return Double.NaN;
        }
    }
}
