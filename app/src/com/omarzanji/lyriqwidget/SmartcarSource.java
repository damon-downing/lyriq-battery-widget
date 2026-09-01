package com.omarzanji.lyriqwidget;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Smartcar (https://smartcar.com) — the officially supported way to read a GM/Cadillac
 * vehicle from a third-party app. The owner authorizes via Smartcar Connect with their
 * OnStar login; we then call /battery and /charge for the connected vehicle.
 */
public final class SmartcarSource implements VehicleSource {
    static final String AUTH_URL = "https://connect.smartcar.com/oauth/authorize";
    static final String TOKEN_URL = "https://auth.smartcar.com/oauth/token";
    static final String API = "https://api.smartcar.com/v2.0";
    static final String SCOPES = "read_vehicle_info read_battery read_charge";

    /** Smartcar requires native redirect URIs of the form sc<clientId>://<host>. */
    static String redirectUri(String clientId) {
        return "sc" + clientId.trim() + "://exchange";
    }

    static String authorizeUrl(Prefs prefs) {
        String clientId = prefs.scClientId();
        return AUTH_URL
                + "?response_type=code"
                + "&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(redirectUri(clientId))
                + "&scope=" + enc(SCOPES)
                + "&mode=" + (prefs.scSimulated() ? "simulated" : "live")
                + "&approval_prompt=auto"
                + "&single_select=true";
    }

    private static String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    /** Exchanges the authorization code for tokens and stores them. */
    static void exchangeCode(Prefs prefs, String code) throws Exception {
        String body = "grant_type=authorization_code&code=" + enc(code)
                + "&redirect_uri=" + enc(redirectUri(prefs.scClientId()));
        tokenRequest(prefs, body);
        prefs.setScVehicleId("");
    }

    private static void refreshTokens(Prefs prefs) throws Exception {
        tokenRequest(prefs, "grant_type=refresh_token&refresh_token=" + enc(prefs.scRefreshToken()));
    }

    private static void tokenRequest(Prefs prefs, String body) throws Exception {
        Map<String, String> headers = new HashMap<>();
        String basic = prefs.scClientId() + ":" + prefs.scClientSecret();
        headers.put("Authorization", "Basic " + Base64.encodeToString(basic.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        Http.Response r = Http.request("POST", TOKEN_URL, headers, body, "application/x-www-form-urlencoded");
        if (!r.ok()) throw new IllegalStateException("Smartcar token error (" + r.code + "): " + errorMessage(r.body));
        JSONObject j = new JSONObject(r.body);
        long expiresAt = System.currentTimeMillis() + j.optLong("expires_in", 7200) * 1000L;
        prefs.setScTokens(j.getString("access_token"), j.getString("refresh_token"), expiresAt);
    }

    private static String accessToken(Prefs prefs) throws Exception {
        if (!prefs.scConnected()) throw new IllegalStateException("Not connected — open the app and tap Connect vehicle");
        if (prefs.scAccessToken().isEmpty() || System.currentTimeMillis() > prefs.scExpiresAt() - 60_000L) {
            refreshTokens(prefs);
        }
        return prefs.scAccessToken();
    }

    private static Map<String, String> apiHeaders(String token) {
        Map<String, String> h = new HashMap<>();
        h.put("Authorization", "Bearer " + token);
        h.put("SC-Unit-System", "imperial");
        return h;
    }

    @Override
    public BatterySnapshot fetch(Prefs prefs) throws Exception {
        String token = accessToken(prefs);
        String vehicleId = prefs.scVehicleId();
        if (vehicleId.isEmpty()) {
            Http.Response r = Http.request("GET", API + "/vehicles", apiHeaders(token), null, null);
            if (!r.ok()) throw new IllegalStateException("Smartcar vehicles error (" + r.code + "): " + errorMessage(r.body));
            JSONArray ids = new JSONObject(r.body).getJSONArray("vehicles");
            if (ids.length() == 0) throw new IllegalStateException("No vehicle authorized on this Smartcar connection");
            vehicleId = ids.getString(0);
            prefs.setScVehicleId(vehicleId);
        }

        // One batch request = fewer round trips; each sub-request still counts toward the plan.
        JSONObject batch = new JSONObject().put("requests", new JSONArray()
                .put(new JSONObject().put("path", "/battery"))
                .put(new JSONObject().put("path", "/charge"))
                .put(new JSONObject().put("path", "/")));
        Http.Response r = Http.request("POST", API + "/vehicles/" + vehicleId + "/batch", apiHeaders(token),
                batch.toString(), "application/json");
        if (r.code == 401) {
            prefs.setScTokens("", prefs.scRefreshToken(), 0); // force a refresh next time
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

    private static String errorMessage(String body) {
        try {
            JSONObject j = new JSONObject(body);
            String d = j.optString("description", "");
            if (d.isEmpty()) d = j.optString("error_description", "");
            if (d.isEmpty()) d = j.optString("message", "");
            if (d.isEmpty()) d = j.optString("error", "");
            return d.isEmpty() ? body : d;
        } catch (Exception e) {
            return body == null || body.isEmpty() ? "no details" : body;
        }
    }
}
