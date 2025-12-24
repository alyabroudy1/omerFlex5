package com.omarflex5.data.scraper.util;

/**
 * Utility class for detecting Cloudflare protection in HTTP responses.
 */
public class CfDetector {

    /**
     * Check if the HTTP response indicates Cloudflare protection.
     *
     * @param statusCode HTTP status code (typically 403 or 503 for CF)
     * @param body       Response body content
     * @return true if Cloudflare challenge is detected
     */
    public static boolean isCloudflareResponse(int statusCode, String body) {
        // Cloudflare typically returns 403 or 503
        if (statusCode != 403 && statusCode != 503) {
            return false;
        }

        if (body == null || body.isEmpty()) {
            return false;
        }

        // Check for common Cloudflare challenge indicators
        return body.contains("Just a moment") ||
                body.contains("Cloudflare") ||
                body.contains("Checking your browser") ||
                body.contains("cf-browser-verification") ||
                body.contains("__cf_chl_tk") ||
                body.contains("cf_clearance") ||
                body.contains("challenge-form") ||
                body.contains("_cf_chl_opt") ||
                body.contains("turnstile-wrapper");
    }

    /**
     * Check if HTML content contains Cloudflare challenge page.
     * Use this for content already loaded in WebView.
     * Includes Turnstile widget detection for modern CF challenges.
     *
     * @param html Page HTML content
     * @return true if page appears to be a CF challenge
     */
    public static boolean isCloudflareContent(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }

        // Classic CF challenge indicators
        if (html.contains("Just a moment") ||
                html.contains("Checking your browser") ||
                html.contains("cf-browser-verification")) {
            return true;
        }

        // Turnstile-specific indicators (modern CF challenges)
        if (html.contains("cf-turnstile") || // Turnstile widget class
                html.contains("challenges.cloudflare") || // Turnstile iframe src
                html.contains("challenge-form") || // CF challenge form ID
                html.contains("_cf_chl_opt") || // CF challenge config object
                html.contains("turnstile-wrapper") || // Turnstile container
                html.contains("cf-spinner") || // CF loading spinner
                html.contains("__cf_chl_tk") || // CF token
                html.contains("data-cf-settings")) { // CF settings attribute
            return true;
        }

        return false;
    }

    /**
     * Check if page title indicates Cloudflare challenge.
     *
     * @param title Document title
     * @return true if title indicates CF
     */
    public static boolean isCloudflareTitleIndicator(String title) {
        if (title == null) {
            return false;
        }

        String lowerTitle = title.toLowerCase();
        return lowerTitle.contains("just a moment") ||
                lowerTitle.contains("cloudflare") ||
                lowerTitle.contains("checking your browser") ||
                lowerTitle.contains("attention required");
    }
}
