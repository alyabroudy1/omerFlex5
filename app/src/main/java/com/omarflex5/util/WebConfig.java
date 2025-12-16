package com.omarflex5.util;

/**
 * Centralized configuration for WebView instances.
 * Ensures consistent User-Agent across Scraper and Sniffer to maximize
 * Cloudflare cookie reuse.
 */
public class WebConfig {

    private static String cachedUserAgent = null;

    /**
     * Returns the system's default User-Agent, ensuring it's Mobile.
     * Use this ensures the UA matches the actual WebView version (e.g. Chrome 142),
     * preventing Cloudflare "Fingerprint Mismatch" detection.
     */
    public static String getUserAgent(android.content.Context context) {
        if (cachedUserAgent == null) {
            try {
                // Get system default
                String systemUa = android.webkit.WebSettings.getDefaultUserAgent(context);

                // Force "Mobile" if missing (to ensure we get mobile sites/challenges)
                if (!systemUa.contains("Mobile")) {
                    systemUa = systemUa.replace("Safari", "Mobile Safari");
                }

                cachedUserAgent = systemUa;
            } catch (Exception e) {
                // Fallback if WebView not ready
                cachedUserAgent = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
            }
        }
        return cachedUserAgent;
    }

    // Common timeout for page loads
    public static final long DEFAULT_TIMEOUT_MS = 30000;

    // Deprecated: Use getUserAgent(context) instead
    public static final String COMMON_USER_AGENT_LEGACY = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
}
