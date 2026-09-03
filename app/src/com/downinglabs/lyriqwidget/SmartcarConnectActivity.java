package com.downinglabs.lyriqwidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/**
 * Hosts Smartcar Connect in a WebView and intercepts the sc<clientId>://exchange redirect.
 * Intercepting in-app lets the redirect scheme depend on the client ID typed at runtime,
 * which a static manifest intent-filter cannot do. Operates on one specific Vehicle, passed
 * in via EXTRA_VEHICLE_ID, so two vehicles can each connect their own Smartcar app.
 */
public final class SmartcarConnectActivity extends Activity {
    public static final String EXTRA_VEHICLE_ID = "vehicle_id";

    private WebView web;
    private boolean handled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String vehicleId = getIntent().getStringExtra(EXTRA_VEHICLE_ID);
        final Vehicle vehicle = new VehicleStore(this).get(vehicleId);
        final String redirect = SmartcarSource.redirectUri(vehicle.scClientId());

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportMultipleWindows(false);
        // Strip the "; wv)" marker stock WebView adds to its User-Agent — some login/fraud-detection
        // pages (GM's included) specifically distrust embedded WebViews and block them even with
        // fully correct credentials, while trusting a normal-looking Chrome UA.
        s.setUserAgentString(s.getUserAgentString().replace("; wv)", ")"));
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true); // GM's SSO bounces across subdomains; needs this
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return intercept(request.getUrl(), vehicle, redirect);
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return intercept(Uri.parse(url), vehicle, redirect);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                // A server-side 302 to the custom scheme can bypass shouldOverrideUrlLoading; catch it here.
                if (intercept(Uri.parse(url), vehicle, redirect)) view.stopLoading();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame() && intercept(request.getUrl(), vehicle, redirect)) return;
                super.onReceivedError(view, request, error);
            }
        });
        setContentView(web);
        web.loadUrl(SmartcarSource.authorizeUrl(vehicle));
    }

    private boolean intercept(Uri uri, final Vehicle vehicle, String redirect) {
        if (uri == null || handled) return false;
        String url = uri.toString();
        // URL schemes are case-insensitive and Smartcar client IDs can contain capitals.
        if (!url.toLowerCase(java.util.Locale.US).startsWith(redirect.toLowerCase(java.util.Locale.US))) return false;
        handled = true;
        String error = uri.getQueryParameter("error");
        final String code = uri.getQueryParameter("code");
        final String userId = uri.getQueryParameter("user_id");
        if (error != null || code == null) {
            String desc = uri.getQueryParameter("error_description");
            fail("Smartcar denied the connection: " + (desc != null ? desc : error));
            return true;
        }
        Toast.makeText(this, "Finishing connection…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SmartcarSource.completeConnect(vehicle, code, userId);
                    vehicle.setSource(VehicleSource.SMARTCAR);
                    Refresher.refreshVehicle(SmartcarConnectActivity.this, vehicle.id);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setResult(RESULT_OK);
                            finish();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() { fail(e.getMessage()); }
                    });
                }
            }
        }, "smartcar-exchange").start();
        return true;
    }

    private void fail(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Connection failed")
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) { finish(); }
                })
                .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(android.content.DialogInterface d) { finish(); }
                })
                .show();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
