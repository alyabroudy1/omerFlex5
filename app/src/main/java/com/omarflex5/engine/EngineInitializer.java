package com.omarflex5.engine;

import android.content.Context;

/**
 * Flavor-specific engine initializer.
 * Each flavor provides its own implementation in the flavor source set.
 */
public interface EngineInitializer {
    /**
     * Initialize the web engine factory for this flavor.
     * Called once at application startup.
     */
    void initialize(Context context);
}
