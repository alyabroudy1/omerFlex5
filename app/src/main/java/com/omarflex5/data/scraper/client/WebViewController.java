package com.omarflex5.data.scraper.client;

/**
 * Interface for communicating WebView state changes to the hosting controller
 * (e.g., Activity, Dialog, or Headless Manager).
 * Eliminates the need for View tags or tight coupling.
 */
public interface WebViewController {
    /**
     * Update the visual status text (if applicable).
     */
    void updateStatus(String message);

    /**
     * Update the progress bar (0-100).
     */
    void updateProgress(int progress);

    /**
     * Called when Cloudflare detection logic identifies a challenge.
     */
    void onCloudflareDetected();

    /**
     * Called when a new page load starts.
     */
    void onPageStarted(String url);

    /**
     * Called when valid content (non-CF) is loaded.
     */
    void onPageLoaded(String url);

    /**
     * Called when the page is verified as safe (no Cloudflare challenge) and ready
     * for processing.
     */
    /**
     * Called when valid content (non-CF) is loaded.
     */
    void onContentReady(String url);

    /**
     * Called when a video URL is detected via network interception.
     */
    void onVideoDetected(String url, java.util.Map<String, String> headers);
}
