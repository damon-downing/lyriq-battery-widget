package com.downinglabs.lyriqwidget;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Smartcar (https://smartcar.com) — the officially supported way to read a GM/Cadillac
 * vehicle from a third-party app. Two generations of credentials exist:
 *
 *  - V3 (current dashboards): an Application ID (UUID) used as the Connect client_id, plus
 *    "API credentials" (client_... id + secret) that mint a 1-hour application token from
 *    iam.smartcar.com. Connect appends a user_id to the redirect; vehicle reads go to
 *    vehicle.api.smartcar.com/v3 with an "sc-user-id" header.
 *  - V2 (legacy credentials): the classic authorization-code exchange at auth.smartcar.com
 *    with refresh tokens, and api.smartcar.com/v2.0 endpoints.
 *
 * We use V3 whenever a client_... id is configured and the redirect carried a user_id,
 * otherwise fall back to V2. Every credential/token lives on the specific Vehicle passed in,
 * so two vehicles can each be connected to their own Smartcar app independently.
 */
public final class SmartcarSource implements VehicleSource {
    static final String AUTH_URL = "https://connect.smartcar.com/oauth/authorize";
    static final String TOKEN_URL_V2 = "https://auth.smartcar.com/oauth/token";
    static final String TOKEN_URL_V3 = "https://iam.smartcar.com/oauth2/token";
    static final String API_V2 = "https://api.smartcar.com/v2.0";
    static final String API_V3 = "https://vehicle.api.smartcar.com/v3";
    static final String SCOPES = "read_vehicle_info read_battery read_charge";

    /** Smartcar requires native redirect URIs of the form sc<clientId>://<host>. */
    static String redirectUri(String clientId) {
        return "sc" + clientId.trim() + "://exchange";
    }

    static String authorizeUrl(Vehicle vehicle) {
        String clientId = vehicle.scClientId();
        return AUTH_URL
                + "?response_type=code"
                + "&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUri(clientId))
                + "&scope=" + enc(SCOPES)
                + "&mode=" + (vehicle.scSimulated() ? "simulated" : "live")
                + "&approval_prompt=auto"
                + "&single_select=true";
    }

    private static String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    // ------------------------------------------------------------------ connect

    /** Completes the Connect flow after the redirect. userId may be null on legacy apps.
     *  Only persists "connected" state (scUserId/scApiVersion) once a real vehicle is
     *  confirmed — marking a vehicle connected before that leaves it in a limbo state where
     *  every future refresh keeps hitting Smartcar's /connections endpoint looking for a car
     *  that was never actually found, which is exactly what caused the retry-loop API spike. */
    static void completeConnect(Vehicle vehicle, String code, String userId) throws Exception {
        boolean v3 = !vehicle.scTokenClientId().isEmpty() && userId != null && !userId.isEmpty();
        if (v3) {
            String token = appToken(vehicle);
            String foundVehicleId = findVehicleV3(vehicle, token, userId); // throws if none found — nothing persisted yet
            vehicle.setScUserId(userId);
            vehicle.setScApiVersion("v3");
            vehicle.setScVehicleId(foundVehicleId);
        } else {
            vehicle.setScApiVersion("v2");
            exchangeCodeV2(vehicle, code);
            vehicle.setScVehicleId("");
        }
    }

    // ------------------------------------------------------------------ V3

    /** Client-credentials application token (1 hour). Cached on the vehicle until near expiry. */
    private static String appToken(Vehicle vehicle) throws Exception {
        if (!vehicle.scAccessToken().isEmpty() && System.currentTimeMillis() < vehicle.scExpiresAt() - 60_000L) {
            return vehicle.scAccessToken();
        }
        JSONObject body = new JSONObject()
                .put("grant_type", "client_credentials")
                .put("client_id", vehicle.scTokenClientId())
                .put("client_secret", vehicle.scClientSecret());
        Http.Response r = Http.request("POST", TOKEN_URL_V3, null, body.toString(), "application/json");
        if (!r.ok()) {
            // Some deployments want form encoding instead of JSON; try once more.
            String form = "grant_type=client_credentials&client_id=" + enc(vehicle.scTokenClientId())
                    + "&client_secret=" + enc(vehicle.scClientSecret());
            Http.Response r2 = Http.request("POST", TOKEN_URL_V3, null, form, "application/x-www-form-urlencoded");
            if (!r2.ok()) throw new IllegalStateException("Smartcar API-credentials error (" + r.code + "): " + errorMessage(r.body));
            r = r2;
        }
        JSONObject j = new JSONObject(r.body);
        long expiresAt = System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000L;
        vehicle.setScTokens(j.getString("access_token"), "", expiresAt);
        return j.getString("access_token");
    }

    private static Map<String, String> v3Headers(String token, String userId) {
        Map<String, String> h = new HashMap<>();
        h.put("Authorization", "Bearer " + token);
        if (userId != null && !userId.isEmpty()) h.put("sc-user-id", userId);
        return h;
    }

    private static String findVehicleV3(Vehicle vehicle, String token, String userId) throws Exception {
        Http.Response r = Http.request("GET", API_V3 + "/connections", v3Headers(token, userId), null, null);
        if (!r.ok()) throw new IllegalStateException("Smartcar connections error (" + r.code + "): " + errorMessage(r.body));
        JSONArray data = new JSONObject(r.body).optJSONArray("data");
        String first = null;
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                JSONObject c = data.getJSONObject(i);
                String vehicleId = c.optString("vehicle_id", "");
                String uid = c.optString("user_id", "");
                JSONObject rel = c.optJSONObject("relationships");
                if (rel != null) {
                    JSONObject v = rel.optJSONObject("vehicle"), u = rel.optJSONObject("user");
                    if (v != null && v.optJSONObject("data") != null) vehicleId = v.optJSONObject("data").optString("id", vehicleId);
                    if (u != null && u.optJSONObject("data") != null) uid = u.optJSONObject("data").optString("id", uid);
                }
                if (vehicleId.isEmpty()) continue;
                if (first == null) first = vehicleId;
                if (uid.equals(userId)) return vehicleId; // prefer the vehicle this user just connected
            }
        }
        if (first == null) throw new IllegalStateException("Smartcar reports no connected vehicles yet");
        return first;
    }

    private static BatterySnapshot fetchV3(Vehicle vehicle) throws Exception {
        String token = appToken(vehicle);
        String userId = vehicle.scUserId();
        String vehicleId = vehicle.scVehicleId();
        if (vehicleId.isEmpty()) {
            vehicleId = findVehicleV3(vehicle, token, userId);
            vehicle.setScVehicleId(vehicleId);
        }
        Http.Response r = Http.request("GET", API_V3 + "/vehicles/" + vehicleId + "/signals", v3Headers(token, userId), null, null);
        if (r.code == 401) {
            vehicle.setScTokens("", "", 0);
            throw new IllegalStateException("Smartcar token expired — refresh again");
        }
        if (!r.ok()) throw new IllegalStateException("Smartcar signals error (" + r.code + "): " + errorMessage(r.body));

        int percent = -1;
        double rangeMiles = -1;
        boolean charging = false, plugged = false;
        long socAt = 0, chargeAt = 0, plugAt = 0;
        JSONObject root = new JSONObject(r.body);
        JSONArray data = root.optJSONArray("data");
        if (data == null && root.optJSONObject("data") != null) data = new JSONArray().put(root.optJSONObject("data"));
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                JSONObject a = item.optJSONObject("attributes");
                if (a == null) a = item;
                String name = (a.optString("name", "") + " " + a.optString("code", "") + " " + item.optString("id", "")).toLowerCase(Locale.US);
                JSONObject status = a.optJSONObject("status");
                if (status != null && "ERROR".equalsIgnoreCase(status.optString("value", ""))) continue;
                JSONObject body = a.optJSONObject("body");
                if (body == null) continue;
                String unit = a.optString("unit", "").toLowerCase(Locale.US);
                long at = oemUpdatedAt(item);
                if (name.contains("tractionbattery-stateofcharge")) {
                    double v = body.optDouble("value", -1);
                    if (v >= 0) percent = (int) Math.round(v <= 1.0 && !unit.equals("percent") ? v * 100 : v);
                    if (percent > 100 && v <= 1.0) percent = (int) Math.round(v * 100);
                    socAt = at;
                } else if (name.contains("tractionbattery-range")) {
                    double v = body.optDouble("value", -1);
                    if (v >= 0) rangeMiles = unit.startsWith("mi") ? v : v * 0.621371;
                } else if (name.contains("charge-detailedchargingstatus") || name.contains("charge-chargingstatus")) {
                    String s = body.optString("value", "").toUpperCase(Locale.US);
                    charging = s.contains("CHARGING") && !s.contains("NOT") && !s.contains("DIS") && !s.contains("FULLY");
                    if (s.contains("PLUGGED") || s.contains("CONNECTED")) plugged = true;
                    chargeAt = at;
                } else if (name.contains("charge-ischargingcableconnected") || name.contains("charge-ispluggedin")) {
                    plugged = body.optBoolean("value", false) || "true".equalsIgnoreCase(body.optString("value", ""));
                    plugAt = at;
                }
            }
        }
        if (percent < 0) throw new IllegalStateException("Smartcar returned no state-of-charge signal");
        // Smartcar V3 serves cached values (about once a day per vehicle unless it is subscribed
        // to a webhook), and GM's plug/charge status is documented to hold stale values. Time-stamp
        // the snapshot with the car's own report time, and only claim a plug state that is recent.
        long now = System.currentTimeMillis();
        long dataAt = socAt > 0 ? socAt : now;
        boolean chargeFresh = chargeAt > 0 && now - chargeAt < FRESH_MS;
        boolean plugFresh = plugAt > 0 && now - plugAt < FRESH_MS;
        charging = charging && chargeFresh;
        plugged = (plugged && (plugFresh || chargeFresh)) || charging;
        android.util.Log.i("LyriqRefresh", "v3: soc=" + percent + " range=" + rangeMiles + " charging=" + charging
                + " plugged=" + plugged + " socAge=" + (now - dataAt) / 60000 + "min plugAge=" + (plugAt > 0 ? (now - plugAt) / 60000 : -1) + "min");
        return new BatterySnapshot(percent, rangeMiles, charging, plugged, dataAt, "Cadillac LYRIQ", null);
    }

    /** Plug and charge status older than this are not shown; the OEM only refreshes them on events. */
    private static final long FRESH_MS = 90L * 60 * 1000;

    /** item.meta.oemUpdatedAt as epoch millis (ISO-8601 or a millisecond number), 0 when absent. */
    private static long oemUpdatedAt(JSONObject item) {
        JSONObject meta = item.optJSONObject("meta");
        if (meta == null) return 0;
        Object v = meta.opt("oemUpdatedAt");
        if (v == null) v = meta.opt("retrievedAt");
        if (v instanceof Number) return ((Number) v).longValue();
        if (v == null) return 0;
        try {
            return java.time.Instant.parse(v.toString()).toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ V2 (legacy)

    private static void exchangeCodeV2(Vehicle vehicle, String code) throws Exception {
        String body = "grant_type=authorization_code&code=" + enc(code)
                + "&redirect_uri=" + enc(redirectUri(vehicle.scClientId()));
        tokenRequestV2(vehicle, body);
    }

    private static void tokenRequestV2(Vehicle vehicle, String body) throws Exception {
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        if (!vehicle.scTokenClientId().isEmpty()) ids.add(vehicle.scTokenClientId());
        ids.add(vehicle.scClientId());
        Http.Response last = null;
        for (String id : ids) {
            Map<String, String> headers = new HashMap<>();
            String basic = id + ":" + vehicle.scClientSecret();
            headers.put("Authorization", "Basic " + Base64.encodeToString(basic.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
            last = Http.request("POST", TOKEN_URL_V2, headers, body, "application/x-www-form-urlencoded");
            if (last.ok() || (last.code != 401 && last.code != 403)) break;
        }
        if (last == null || !last.ok()) {
            throw new IllegalStateException("Smartcar token error (" + last.code + "): " + errorMessage(last.body)
                    + " — if your dashboard shows a client_… ID under API credentials, enter it in the app to use the V3 flow.");
        }
        JSONObject j = new JSONObject(last.body);
        long expiresAt = System.currentTimeMillis() + j.optLong("expires_in", 7200) * 1000L;
        vehicle.setScTokens(j.getString("access_token"), j.getString("refresh_token"), expiresAt);
    }

    private static String accessTokenV2(Vehicle vehicle) throws Exception {
        if (vehicle.scRefreshToken().isEmpty()) throw new IllegalStateException("Not connected — open the app and tap Connect vehicle");
        if (vehicle.scAccessToken().isEmpty() || System.currentTimeMillis() > vehicle.scExpiresAt() - 60_000L) {
            tokenRequestV2(vehicle, "grant_type=refresh_token&refresh_token=" + enc(vehicle.scRefreshToken()));
        }
        return vehicle.scAccessToken();
    }

    private static Map<String, String> v2Headers(String token) {
        Map<String, String> h = new HashMap<>();
        h.put("Authorization", "Bearer " + token);
        h.put("SC-Unit-System", "imperial");
        return h;
    }

    private static BatterySnapshot fetchV2(Vehicle vehicle) throws Exception {
        String token = accessTokenV2(vehicle);
        String vehicleId = vehicle.scVehicleId();
        if (vehicleId.isEmpty()) {
            Http.Response r = Http.request("GET", API_V2 + "/vehicles", v2Headers(token), null, null);
            if (!r.ok()) throw new IllegalStateException("Smartcar vehicles error (" + r.code + "): " + errorMessage(r.body));
            JSONArray ids = new JSONObject(r.body).getJSONArray("vehicles");
            if (ids.length() == 0) throw new IllegalStateException("No vehicle authorized on this Smartcar connection");
            vehicleId = ids.getString(0);
            vehicle.setScVehicleId(vehicleId);
        }
        JSONObject batch = new JSONObject().put("requests", new JSONArray()
                .put(new JSONObject().put("path", "/battery"))
                .put(new JSONObject().put("path", "/charge"))
                .put(new JSONObject().put("path", "/")));
        Http.Response r = Http.request("POST", API_V2 + "/vehicles/" + vehicleId + "/batch", v2Headers(token),
                batch.toString(), "application/json");
        if (r.code == 401) {
            vehicle.setScTokens("", vehicle.scRefreshToken(), 0);
            throw new IllegalStateException("Smartcar session expired — refresh again");
        }
        if (!r.ok()) throw new IllegalStateException("Smartcar batch error (" + r.code + "): " + errorMessage(r.body));

        int percent = -1;
        double range = -1;
        boolean charging = false, plugged = false;
        String name = "Cadillac LYRIQ";
        JSONArray responses = new JSONObject(r.body).getJSONArray("responses");
        for (int i = 0; i < responses.length(); i++) {
            JSONObject item = responses.getJSONObject(i);
            String path = item.optString("path");
            JSONObject b = item.optJSONObject("body");
            if (b == null) continue;
            if (item.optInt("code", 200) >= 400) {
                if (path.equals("/battery")) throw new IllegalStateException("Smartcar /battery: " + errorMessage(b.toString()));
                continue;
            }
            switch (path) {
                case "/battery":
                    percent = (int) Math.round(b.optDouble("percentRemaining", -1) * 100);
                    range = b.optDouble("range", -1);
                    break;
                case "/charge":
                    plugged = b.optBoolean("isPluggedIn", false);
                    charging = "CHARGING".equalsIgnoreCase(b.optString("state", ""));
                    break;
                case "/":
                    String make = b.optString("make", ""), model = b.optString("model", "");
                    if (!model.isEmpty()) name = (make + " " + model).trim();
                    break;
            }
        }
        if (percent < 0) throw new IllegalStateException("Smartcar returned no battery reading");
        return new BatterySnapshot(percent, range, charging, plugged, System.currentTimeMillis(), name, null);
    }

    // ------------------------------------------------------------------ entry point

    @Override
    public BatterySnapshot fetch(Vehicle vehicle) throws Exception {
        if (!vehicle.scConnected()) throw new IllegalStateException("Not connected — open the app and tap Connect vehicle");
        return "v3".equals(vehicle.scApiVersion()) ? fetchV3(vehicle) : fetchV2(vehicle);
    }

    private static String errorMessage(String body) {
        try {
            JSONObject j = new JSONObject(body);
            String d = j.optString("description", "");
            if (d.isEmpty()) d = j.optString("error_description", "");
            if (d.isEmpty()) d = j.optString("message", "");
            if (d.isEmpty()) d = j.optString("detail", "");
            if (d.isEmpty()) d = j.optString("error", "");
            return d.isEmpty() ? body : d;
        } catch (Exception e) {
            return body == null || body.isEmpty() ? "no details" : body;
        }
    }
}
