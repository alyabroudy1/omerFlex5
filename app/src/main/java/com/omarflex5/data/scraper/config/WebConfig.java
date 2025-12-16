package com.omarflex5.data.scraper.config;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.omarflex5.data.sniffer.config.SnifferConfig;

/**
 * Centralized configuration for all WebViews in the app.
 * Ensures consistent User-Agent, Cookie settings, and Caching behavior.
 */
public class WebConfig {

    @SuppressLint("SetJavaScriptEnabled")
    public static void configure(WebView webView) {
        if (webView == null)
            return;

        Context context = webView.getContext();
        WebSettings settings = webView.getSettings();

        // 1. Core Settings
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false); // Consistent with Scraper
        settings.setMediaPlaybackRequiresUserGesture(false); // Helpful for video sniffing

        // 2. User Agent (Dynamic)
        // Use the common util if available, otherwise fallback to SnifferConfig
        String userAgent = com.omarflex5.util.WebConfig.getUserAgent(context);
        settings.setUserAgentString(userAgent);

        // 3. Network & Content
        settings.setBlockNetworkImage(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // 4. Caching (Fresh content for Scraper/Sniffer)
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // 5. Hardware Acceleration (Critical for Turnstile)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 6. Cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // 7. Debugging (Optional, can be toggled)
        WebView.setWebContentsDebuggingEnabled(true);
    }
}
