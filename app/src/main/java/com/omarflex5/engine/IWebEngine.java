package com.omarflex5.engine;

import android.view.View;
import android.webkit.ValueCallback;
import java.util.Map;

/**
 * Unified interface for web engine implementations (WebView, GeckoView).
 * Abstracts the underlying browser engine to allow seamless switching.
 */
public interface IWebEngine {

    /**
     * Get the underlying View to add to the layout hierarchy.
     */
    View getView();

    /**
     * Load a URL.
     */
    void loadUrl(String url);

    /**
     * Load a URL with additional HTTP headers.
     */
    void loadUrl(String url, Map<String, String> headers);

    /**
     * Load raw HTML content.
     */
    void loadHtml(String html, String baseUrl);

    /**
     * Execute JavaScript code and receive the result.
     */
    void evaluateJavascript(String script, ValueCallback<String> callback);

    /**
     * Add a JavaScript interface for native callbacks.
     */
    void addJavascriptInterface(Object object, String name);

    /**
     * Set the client that receives page lifecycle and navigation events.
     */
    void setEngineClient(IWebEngineClient client);

    /**
     * Apply settings to the engine.
     */
    void applySettings(WebEngineSettings settings);

    /**
     * Get the current User-Agent string.
     */
    String getUserAgent();

    /**
     * Get the current URL being displayed.
     */
    String getCurrentUrl();

    /**
     * Check if the engine can go back in history.
     */
    boolean canGoBack();

    /**
     * Go back in browsing history.
     */
    void goBack();

    /**
     * Stop loading the current page.
     */
    void stopLoading();

    /**
     * Reload the current page.
     */
    void reload();

    /**
     * Clean up resources when done.
     */
    void destroy();
}
