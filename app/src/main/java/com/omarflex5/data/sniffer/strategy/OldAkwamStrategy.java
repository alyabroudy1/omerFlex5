package com.omarflex5.data.sniffer.strategy;

import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;

import com.omarflex5.data.sniffer.callback.SnifferCallback;

import java.util.Map;

/**
 * Server-specific strategy for Old Akwam (ak.sv, akwam.cc).
 * Handles:
 * 1. Hash fragment restoration (lost during Android navigation).
 * 2. Polling for download button after server's 5-second timer.
 * 3. Auto-navigation of intermediate download pages.
 */
public class OldAkwamStrategy implements SniffingStrategy {

    private static final String TAG = "OldAkwamStrategy";

    private final SnifferCallback callback;
    private boolean videoFound = false;

    public OldAkwamStrategy(SnifferCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        if (videoFound)
            return;
        callback.onProgress("OldAkwam: Checking page...");

        // Inject the hash restoration + polling script
        view.evaluateJavascript("javascript:" + getCustomScript(), null);
    }

    @Override
    public boolean shouldLoadResource(String url, WebResourceRequest request) {
        if (videoFound)
            return true;

        // Detect video pattern in resource requests
        if (url != null && (url.contains(".mp4") || url.contains(".m3u8") || url.contains(".mkv"))) {
            if (!isAdUrl(url)) {
                Log.d(TAG, "Video resource detected: " + url);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean shouldOverrideUrlLoading(String url) {
        // Block non-HTTP schemes (ads, intents)
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Log.d(TAG, "Blocked non-HTTP: " + url);
            return true;
        }
        return false;
    }

    @Override
    public String getCustomScript() {
        // Hash restoration + download button polling for Old Akwam
        return "try {" +
                "    /* FIX: Restore Hash if missing (Android navigation strips it) */" +
                "    if (!location.hash) {" +
                "        var links = document.querySelectorAll('a[ng-href], a[href*=\"#\"]');" +
                "        for (var i = 0; i < links.length; i++) {" +
                "            var href = links[i].getAttribute('ng-href') || links[i].getAttribute('href');" +
                "            if (href && href.indexOf('#') !== -1) {" +
                "                var recoveredHash = href.substring(href.indexOf('#'));" +
                "                console.log('[OldAkwam] 🔧 Restoring hash: ' + recoveredHash);" +
                "                location.hash = recoveredHash;" +
                "                break;" +
                "            }" +
                "        }" +
                "    }" +
                "    " +
                "    /* UX: Auto-scroll to timer/button ONCE */" +
                "    /* UX: Auto-scroll to timer/button ONCE */" +
                "    if (!window.hasScrolledToTimer) {" +
                "        var target = document.querySelector('.download-timer') || " +
                "                     document.querySelector('.download_button') || " +
                "                     document.querySelector('.timing_counter') || " +
                "                     document.querySelector('.timer') || " +
                "                     document.querySelector('#timer');" +
                "        " +
                "        /* Priority 2: Fallback to hash target if no specific timer found */" +
                "        if (!target && location.hash) {" +
                "             try { target = document.querySelector(location.hash); } catch(e){}" +
                "        }" +
                "        " +
                "        if (target) {" +
                "            console.log('[OldAkwam] 📜 Scrolling to: ' + (target.id || target.className));" +
                "            /* Add small delay to ensure layout is stable */" +
                "            setTimeout(function() { " +
                "               target.scrollIntoView({behavior: 'smooth', block: 'center'}); " +
                "            }, 500);" +
                "            window.hasScrolledToTimer = true;" +
                "        } else {" +
                "            /* Debug: Log that we couldn't find a scroll target yet */" +
                "        }" +
                "    }" +
                "    " +
                "    /* Poll for download button (appears after 5s timer) */" +
                "    var btn = document.querySelector('.download_button');" +
                "    if (btn && btn.href) { " +
                "       console.log('[OldAkwam] ✓ Found .download_button: ' + btn.href); " +
                "       window.SnifferAndroid.onVideoDetected(btn.href); " +
                "       return; " +
                "    }" +
                "    " +
                "    /* Auto-navigate intermediate pages */" +
                "    var capsule = document.querySelector('.unauth_capsule a');" +
                "    if (capsule) { " +
                "       var href = capsule.getAttribute('ng-href') || capsule.href; " +
                "       if (href && href.indexOf('/download/') !== -1) { " +
                "           console.log('[OldAkwam] → Auto-navigating: ' + href); " +
                "           window.location.href = href; " +
                "           return; " +
                "       }" +
                "    }" +
                "    " +
                "    console.log('[OldAkwam] ⏳ Waiting... (Title: ' + document.title + ')');" +
                "} catch(e) { console.log('[OldAkwam] ❌ Error: ' + e.message); }";
    }

    @Override
    public boolean onPotentialVideoDetected(String url, Map<String, String> headers) {
        if (videoFound || isAdUrl(url))
            return false;

        // Validate it's a real video URL
        if (url.contains("/dl/") || url.contains(".mp4") || url.contains(".mkv") || url.contains(".m3u8")) {
            videoFound = true;
            callback.onProgress("🎬 Video found: " + truncate(url));
            callback.onVideoFound(url, headers);
            return true;
        }
        return false;
    }

    @Override
    public void onWafDetected(String type) {
        callback.onProgress("🛡️ Security detected: " + type);
    }

    @Override
    public String getName() {
        return "OldAkwam";
    }

    private boolean isAdUrl(String url) {
        if (url == null)
            return false;
        String lower = url.toLowerCase();
        return lower.contains("beacon.min.js") || lower.contains("googleads") ||
                lower.contains("doubleclick") || lower.contains("analytics") ||
                lower.length() < 50;
    }

    private String truncate(String url) {
        if (url == null)
            return "";
        return url.length() > 40 ? url.substring(0, 40) + "..." : url;
    }
}
