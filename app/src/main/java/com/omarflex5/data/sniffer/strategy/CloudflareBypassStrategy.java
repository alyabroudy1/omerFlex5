package com.omarflex5.data.sniffer.strategy;

import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;

import com.omarflex5.data.sniffer.callback.SnifferCallback;

import java.util.HashMap;
import java.util.Map;

/**
 * Strategy for bypassing Cloudflare and extracting clearance cookies.
 * Uses DOM element detection (NOT document title) for robust detection.
 */
public class CloudflareBypassStrategy implements SniffingStrategy {

    private static final String TAG = "CloudflareBypass";

    private final SnifferCallback callback;
    private boolean bypassComplete = false;
    private int checkCount = 0;
    private static final int MAX_CHECKS = 30; // 30 seconds max

    public CloudflareBypassStrategy(SnifferCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        if (bypassComplete)
            return;
        callback.onProgress("Checking for security...");

        // Inject robust CF detection script
        String detectionScript = buildCloudflareDetectionScript();
        view.evaluateJavascript(detectionScript, null);
    }

    @Override
    public boolean shouldLoadResource(String url, WebResourceRequest request) {
        // Allow all resources during CF bypass
        return true;
    }

    @Override
    public boolean shouldOverrideUrlLoading(String url) {
        // Allow all redirects during CF bypass
        return false;
    }

    @Override
    public String getCustomScript() {
        return null;
    }

    @Override
    public boolean onPotentialVideoDetected(String url, Map<String, String> headers) {
        // Not looking for video in this strategy
        return false;
    }

    @Override
    public void onWafDetected(String type) {
        callback.onProgress("🛡️ " + type + " detected, solving...");
    }

    @Override
    public String getName() {
        return "CloudflareBypass";
    }

    @Override
    public SnifferCallback getCallback() {
        return callback;
    }

    /**
     * Called from JS when CF status is determined.
     */
    public void onCloudflareCheckResult(WebView view, boolean isCloudflare, String url) {
        checkCount++;

        if (isCloudflare) {
            callback.onProgress("🛡️ Cloudflare detected, waiting... (" + checkCount + ")");

            if (checkCount < MAX_CHECKS) {
                // Schedule re-check in 1 second
                view.postDelayed(() -> {
                    if (!bypassComplete) {
                        view.evaluateJavascript(buildCloudflareDetectionScript(), null);
                    }
                }, 1000);
            } else {
                callback.onProgress("⏱️ Timeout waiting for Cloudflare");
                callback.onTimeout();
            }
        } else {
            // CF passed or not present
            bypassComplete = true;
            callback.onProgress("✅ Security check passed!");

            // Extract cookies
            String cookieString = CookieManager.getInstance().getCookie(url);
            Map<String, String> cookies = extractCookies(cookieString);

            if (cookies.containsKey("cf_clearance")) {
                callback.onProgress("🍪 Clearance cookie obtained!");
            }

            // Extract HTML
            view.evaluateJavascript(
                    "(function() { return document.documentElement.outerHTML; })();",
                    html -> {
                        if (html != null && !html.equals("null")) {
                            String unescaped = unescapeJsString(html);
                            callback.onHtmlExtracted(unescaped, cookies);
                            callback.onCloudflareBypassComplete(cookies);
                        }
                    });
        }
    }

    private String buildCloudflareDetectionScript() {
        // Robust CF detection using DOM elements (NOT title)
        return "javascript:(function() {" +
                "   try {" +
                "       var cfForm = document.getElementById('challenge-form');" +
                "       var cfSpinner = document.getElementById('cf-spinner');" +
                "       var cfError = document.getElementById('challenge-error-text');" +
                "       var turnstile = document.getElementById('turnstile-wrapper');" +
                "       var bodyHtml = document.body ? document.body.innerHTML : '';" +
                "       var hasMarker1 = bodyHtml.indexOf('Enable JavaScript and cookies to continue') !== -1;" +
                "       var hasMarker2 = bodyHtml.indexOf('Checking your browser before accessing') !== -1;" +
                "       var hasMarker3 = bodyHtml.indexOf('cf-browser-verification') !== -1;" +
                "       var hasMarker4 = bodyHtml.indexOf('Just a moment') !== -1;" +
                "       var isCloudflare = !!(cfForm || cfSpinner || cfError || turnstile || " +
                "                            hasMarker1 || hasMarker2 || hasMarker3 || hasMarker4);" +
                "       console.log('[CF] Detection result:', isCloudflare);" +
                "       window.SnifferAndroid.onCloudflareStatus(isCloudflare);" +
                "   } catch(e) { console.error('[CF] Error:', e); }" +
                "})();";
    }

    private Map<String, String> extractCookies(String cookieString) {
        Map<String, String> cookies = new HashMap<>();
        if (cookieString == null || cookieString.isEmpty())
            return cookies;

        String[] parts = cookieString.split(";");
        for (String part : parts) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                cookies.put(kv[0].trim(), kv[1].trim());
            }
        }
        return cookies;
    }

    private String unescapeJsString(String jsString) {
        if (jsString == null)
            return null;
        if (jsString.startsWith("\"") && jsString.endsWith("\"")) {
            jsString = jsString.substring(1, jsString.length() - 1);
        }
        return jsString.replace("\\u003C", "<")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }
}
