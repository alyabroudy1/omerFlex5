package com.omarflex5.data.sniffer.callback;

import java.util.Map;

/**
 * Callback interface for sniffer results.
 */
public interface SnifferCallback {

    /**
     * Called when a video URL is successfully detected.
     *
     * @param videoUrl The detected video URL
     * @param headers  Headers to use for playback (User-Agent, Referer, Cookie)
     */
    void onVideoFound(String videoUrl, Map<String, String> headers);

    /**
     * Called when HTML content is extracted (for CF bypass or scraping).
     *
     * @param html    The page HTML
     * @param cookies Extracted cookies
     */
    void onHtmlExtracted(String html, Map<String, String> cookies);

    /**
     * Called when Cloudflare bypass completes successfully.
     *
     * @param cookies The CF clearance cookies
     */
    void onCloudflareBypassComplete(Map<String, String> cookies);

    /**
     * Called when Cloudflare protection is detected active.
     */
    void onCloudflareDetected();

    /**
     * Called on progress updates (for status bar).
     *
     * @param message Status message
     */
    void onProgress(String message);

    /**
     * Called on error.
     *
     * @param message Error message
     */
    void onError(String message);

    /**
     * Called on timeout.
     */
    void onTimeout();
}
