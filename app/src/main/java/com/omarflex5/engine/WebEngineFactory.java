package com.omarflex5.engine;

import android.content.Context;

/**
 * Factory for creating IWebEngine instances.
 * The actual implementation is provided by flavor-specific source sets.
 */
public abstract class WebEngineFactory {

    private static WebEngineFactory instance;

    /**
     * Get the singleton factory instance.
     * The concrete implementation is set by flavor-specific code.
     */
    public static WebEngineFactory getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "WebEngineFactory not initialized. Call setInstance() from flavor-specific code.");
        }
        return instance;
    }

    /**
     * Set the factory instance. Called from flavor-specific initialization.
     */
    public static void setInstance(WebEngineFactory factory) {
        instance = factory;
    }

    /**
     * Check if a factory has been set.
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    /**
     * Create a new web engine instance.
     * 
     * @param context The Android context
     * @return A new IWebEngine instance
     */
    public abstract IWebEngine create(Context context);

    /**
     * Get the engine type name (e.g., "WebView", "GeckoView").
     */
    public abstract String getEngineName();

    /**
     * Check if this is the GeckoView implementation.
     */
    public abstract boolean isGeckoView();
}
