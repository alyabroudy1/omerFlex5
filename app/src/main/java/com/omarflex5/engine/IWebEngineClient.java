package com.omarflex5.engine;

import android.graphics.Bitmap;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

/**
 * Unified callback interface for web engine events.
 * Maps to WebViewClient for WebView and NavigationDelegate/ContentDelegate for
 * GeckoView.
 */
public interface IWebEngineClient {

    /**
     * Called when a new page load begins.
     */
    void onPageStarted(IWebEngine engine, String url, Bitmap favicon);

    /**
     * Called when page loading is complete.
     */
    void onPageFinished(IWebEngine engine, String url);

    /**
     * Called when page loading progress changes.
     * 
     * @param progress 0-100
     */
    void onProgressChanged(IWebEngine engine, int progress);

    /**
     * Opportunity to intercept network requests.
     * Return null to allow the request to proceed normally.
     * 
     * @param request The request details
     * @return A WebResourceResponse to serve, or null to proceed normally
     */
    WebResourceResponse shouldInterceptRequest(IWebEngine engine, WebResourceRequest request);

    /**
     * Determine whether to override URL loading.
     * 
     * @param url The URL being loaded
     * @return true to prevent loading, false to allow
     */
    boolean shouldOverrideUrlLoading(IWebEngine engine, String url);

    /**
     * Called when an error occurs during loading.
     */
    void onError(IWebEngine engine, int errorCode, String description, String failingUrl);

    /**
     * Called when the page title changes.
     */
    void onTitleChanged(IWebEngine engine, String title);

    /**
     * Called when a resource is being loaded.
     * Useful for detecting video URLs (m3u8, mp4, etc.).
     * 
     * @param engine The web engine
     * @param url    The resource URL being loaded
     */
    default void onResourceLoaded(IWebEngine engine, String url) {
        // Default no-op. Override to detect video URLs.
    }
}
