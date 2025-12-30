package com.omarflex5.engine;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;

/**
 * Utility class for migrating existing WebView code to IWebEngine.
 * Provides helper methods to work with both WebView and IWebEngine.
 */
public class WebEngineHelper {

    private static final String TAG = "WebEngineHelper";

    /**
     * Check if the current flavor is using GeckoView.
     */
    public static boolean isGeckoViewFlavor() {
        if (WebEngineFactory.isInitialized()) {
            return WebEngineFactory.getInstance().isGeckoView();
        }
        return false;
    }

    /**
     * Get the engine name ("WebView" or "GeckoView").
     */
    public static String getEngineName() {
        if (WebEngineFactory.isInitialized()) {
            return WebEngineFactory.getInstance().getEngineName();
        }
        return "Unknown";
    }

    /**
     * Create a new IWebEngine instance.
     */
    public static IWebEngine createEngine(Context context) {
        if (!WebEngineFactory.isInitialized()) {
            Log.e(TAG, "WebEngineFactory not initialized! Using fallback.");
            return null;
        }
        return WebEngineFactory.getInstance().create(context);
    }

    /**
     * Attempt to extract the underlying WebView from an IWebEngine.
     * Returns null if the engine is GeckoView-based.
     * This is for compatibility with legacy code during migration.
     */
    public static WebView extractWebView(IWebEngine engine) {
        if (engine == null)
            return null;

        View view = engine.getView();
        if (view instanceof WebView) {
            return (WebView) view;
        }

        // Try reflection as fallback for WebViewEngine
        try {
            java.lang.reflect.Method method = engine.getClass().getMethod("getWebView");
            Object result = method.invoke(engine);
            if (result instanceof WebView) {
                return (WebView) result;
            }
        } catch (Exception e) {
            // Not a WebViewEngine or method doesn't exist
        }

        Log.w(TAG, "Cannot extract WebView from engine type: " + engine.getClass().getSimpleName());
        return null;
    }

    /**
     * Check if an IWebEngine's underlying view is a WebView.
     */
    public static boolean isWebViewBased(IWebEngine engine) {
        return engine != null && engine.getView() instanceof WebView;
    }
}
