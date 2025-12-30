package com.omarflex5.engine;

import android.content.Context;

/**
 * Factory for creating WebView-based IWebEngine instances.
 * Used in the webview flavor.
 */
public class WebViewEngineFactory extends WebEngineFactory {

    private static boolean initialized = false;

    /**
     * Initialize the factory. Should be called at app startup.
     */
    public static void init() {
        if (!initialized) {
            WebEngineFactory.setInstance(new WebViewEngineFactory());
            initialized = true;
        }
    }

    @Override
    public IWebEngine create(Context context) {
        return new WebViewEngine(context);
    }

    @Override
    public String getEngineName() {
        return "WebView";
    }

    @Override
    public boolean isGeckoView() {
        return false;
    }
}
