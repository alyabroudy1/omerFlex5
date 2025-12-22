package com.omarflex5.data.scraper.policy;

import android.net.Uri;
import android.util.Log;

/**
 * Policy for handling WebView URL redirects.
 * Provides domain-based validation and security checks.
 */
public class RedirectPolicy {

    private static final String TAG = "RedirectPolicy";

    private final String allowedBaseUrl; // e.g., "https://a.asd.homes"
    private final String allowedDomain; // Extracted domain for comparison

    /**
     * Redirect decision enum.
     */
    public enum Decision {
        ALLOW, // Same domain or safe redirect
        BLOCK, // Non-HTTP or dangerous scheme
        ASK_USER // Cross-domain redirect needs confirmation
    }

    /**
     * Create a redirect policy for a specific server.
     *
     * @param baseUrl The server's base URL (e.g., "https://a.asd.homes")
     */
    public RedirectPolicy(String baseUrl) {
        this.allowedBaseUrl = baseUrl;
        this.allowedDomain = extractBaseDomain(baseUrl);
        Log.d(TAG, "Created policy for domain: " + allowedDomain);
    }

    /**
     * Determine if a redirect should be allowed.
     *
     * @param currentUrl Current page URL
     * @param targetUrl  Target redirect URL
     * @return Decision on how to handle the redirect
     */
    public Decision shouldAllowRedirect(String currentUrl, String targetUrl) {
        if (targetUrl == null || targetUrl.isEmpty()) {
            return Decision.BLOCK;
        }

        // 1. Block non-HTTP schemes (intent://, market://, javascript:, etc.)
        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            Log.w(TAG, "BLOCKED non-HTTP scheme: " + truncateUrl(targetUrl));
            return Decision.BLOCK;
        }

        // 2. Check if same domain
        String targetDomain = extractBaseDomain(targetUrl);
        if (targetDomain == null) {
            Log.w(TAG, "Could not extract domain from: " + truncateUrl(targetUrl));
            return Decision.BLOCK;
        }

        // Allow same domain (including subdomains)
        if (isSameDomain(allowedDomain, targetDomain)) {
            Log.d(TAG, "ALLOWED same-domain: " + truncateUrl(targetUrl));
            return Decision.ALLOW;
        }

        // 3. Different domain - ask user
        Log.d(TAG, "ASK_USER cross-domain: " + allowedDomain + " -> " + targetDomain);
        return Decision.ASK_USER;
    }

    /**
     * Check if a URL is safe to load without any policy check.
     * Used for known-safe video/media URLs.
     */
    public static boolean isSafeMediaUrl(String url) {
        if (url == null)
            return false;
        String lower = url.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".m3u8") ||
                lower.endsWith(".mpd") || lower.contains("/hls/") ||
                lower.contains("/dash/") || lower.contains(".mp4?") ||
                lower.contains(".m3u8?");
    }

    /**
     * Extract base domain from URL (handles subdomains).
     * e.g., "https://www.example.com/path" -> "example.com"
     */
    public static String extractBaseDomain(String url) {
        if (url == null)
            return null;

        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null)
                return null;

            // Remove www. prefix
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }

            // Get base domain (last two parts for standard TLDs)
            String[] parts = host.split("\\.");
            if (parts.length >= 2) {
                // Return last 2 parts (e.g., "example.com")
                return parts[parts.length - 2] + "." + parts[parts.length - 1];
            }

            return host;
        } catch (Exception e) {
            Log.e(TAG, "Error extracting domain: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if two domains match (handles subdomains).
     */
    private boolean isSameDomain(String domain1, String domain2) {
        if (domain1 == null || domain2 == null)
            return false;
        return domain1.equalsIgnoreCase(domain2);
    }

    /**
     * Get debug-safe truncated URL.
     */
    private String truncateUrl(String url) {
        if (url == null)
            return "";
        return url.length() > 80 ? url.substring(0, 80) + "..." : url;
    }

    // Getters
    public String getAllowedBaseUrl() {
        return allowedBaseUrl;
    }

    public String getAllowedDomain() {
        return allowedDomain;
    }
}
