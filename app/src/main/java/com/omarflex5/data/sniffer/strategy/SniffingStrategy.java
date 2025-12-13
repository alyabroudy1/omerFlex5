package com.omarflex5.data.sniffer.strategy;

import android.webkit.WebResourceRequest;
import android.webkit.WebView;

import java.util.Map;

/**
 * Strategy interface for WebView sniffing behaviors.
 * Each implementation defines how to detect and extract specific content types.
 */
public interface SniffingStrategy {

    /**
     * Called when a page finishes loading.
     * Use this to inject JavaScript, analyze HTML, or trigger detection logic.
     *
     * @param view The WebView instance
     * @param url  The URL that finished loading
     */
    void onPageFinished(WebView view, String url);

    /**
     * Determines if a resource request should be intercepted/processed.
     * Return true to allow normal loading, false to block.
     *
     * @param url     The resource URL
     * @param request Full request details (headers, method)
     * @return true to allow, false to block
     */
    boolean shouldLoadResource(String url, WebResourceRequest request);

    /**
     * Determines if a navigation should be overridden.
     *
     * @param url The target URL
     * @return true to block navigation, false to allow
     */
    boolean shouldOverrideUrlLoading(String url);

    /**
     * Returns custom JavaScript to inject on page load.
     * Return null if no custom script is needed.
     */
    String getCustomScript();

    /**
     * Called when a potential video URL is detected.
     * Strategy can validate and notify the callback.
     *
     * @param url     The detected URL
     * @param headers Associated headers
     * @return true if this is a valid detection, false to continue searching
     */
    boolean onPotentialVideoDetected(String url, Map<String, String> headers);

    /**
     * Called when Cloudflare or WAF is detected.
     * Strategy can decide how to handle it.
     *
     * @param type The detected WAF type (e.g., "cloudflare", "ddos-guard")
     */
    void onWafDetected(String type);

    /**
     * Returns the strategy name for logging/status.
     */
    String getName();
}
