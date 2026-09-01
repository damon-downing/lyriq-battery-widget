package com.omarzanji.lyriqwidget;

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
 * which a static manifest intent-filter cannot do.
 */
public final class SmartcarConnectActivity extends Activity {
    private WebView web;
    private boolean handled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Prefs prefs = new Prefs(this);
        final String redirect = SmartcarSource.redirectUri(prefs.scClientId());

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return intercept(request.getUrl(), prefs, redirect);
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return intercept(Uri.parse(url), prefs, redirect);
            }
        });
        setContentView(web);
        web.loadUrl(SmartcarSource.authorizeUrl(prefs));
    }

    private boolean intercept(Uri uri, final Prefs prefs, String redirect) {
        if (uri == null || handled) return false;
        String url = uri.toString();
        // URL schemes are case-insensitive and Smartcar client IDs can contain capitals.
        if (!url.toLowerCase(java.util.Locale.US).startsWith(redirect.toLowerCase(java.util.Locale.US))) return false;
        handled = true;
        String error = uri.getQueryParameter("error");
        final String code = uri.getQueryParameter("code");
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
                    SmartcarSource.exchangeCode(prefs, code);
                    prefs.setSource(VehicleSource.SMARTCAR);
                    Refresher.refreshNow(SmartcarConnectActivity.this);
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
